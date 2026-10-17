package dev.pocketflight;

import android.app.*;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.hardware.*;
import android.net.*;
import android.net.wifi.WifiManager;
import android.media.AudioManager;
import android.os.*;
import android.provider.Settings;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import static dev.pocketflight.FlightPolicy.ExceptionState.*;

public class PocketService extends Service implements SensorEventListener {
    public static final String PAUSE = "dev.pocketflight.PAUSE";
    private static final String CHANNEL = "pocket_monitor";
    private static final int NOTIFICATION = 1;
    private static final long CONNECTION_RECHECK_MS = 60000;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private AppPrefs prefs;
    private ShizukuBridge bridge;
    private SensorManager sensors;
    private Sensor proximity, light, orientation;
    private boolean lightRegistered, orientationRegistered;
    private boolean sensorsActive, sensorsNeedRefresh = true, bluetoothChecking;
    private FlightPolicy.ExceptionState bluetoothState = UNKNOWN;
    private long bluetoothCheckedAt = -1;
    private int connectionGeneration;
    private final PocketDetector detector = new PocketDetector();
    private final PocketExitTimer exitTimer = new PocketExitTimer();
    private final PocketReport report = new PocketReport();
    private PowerManager power;
    private KeyguardManager keyguard;
    private PowerManager.WakeLock shortWake, fallbackWake;
    private ConnectivityManager connectivity;
    private boolean covered, sensorRegistered, busy, suppressed, lastLocked, recovering;
    private volatile boolean destroyed;
    private long retryAt, lastEvaluationAt, sensorEpoch;
    private volatile int generation;
    private String status = "Starting…";
    private final Runnable tick = this::evaluate;
    private final SharedPreferences.OnSharedPreferenceChangeListener preferences = (p, key) -> {
        if ("enabled".equals(key) || "wifi".equals(key) || "bluetooth".equals(key) || "delay_seconds".equals(key)
                || "pocket_assist".equals(key) || "dark_lux".equals(key)
                || "restore_on_exit".equals(key) || "exit_minutes".equals(key)) {
            generation++;
            retryAt = 0;
            detector.resetTimer();
            exitTimer.reset();
            if ("pocket_assist".equals(key)) sensorsNeedRefresh = true;
            if ("bluetooth".equals(key)) invalidateBluetooth();
            evaluate();
        }
    };
    private final BroadcastReceiver events = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent intent) {
            generation++;
            retryAt = 0;
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action) || BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)
                    || BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) invalidateBluetooth();
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                // Re-acquire defaults after folding and demand fresh post-screen-off readings.
                // Defer registration until connection exceptions have been checked.
                sensorsNeedRefresh = true;
                if (sensorsActive) {
                    report.begin(SystemClock.elapsedRealtime(), sensorInventory());
                    prefs.event("Screen off · pocket diagnostic started");
                }
            } else if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
                detector.resetTimer();
            }
            evaluate();
        }
    };
    private final ConnectivityManager.NetworkCallback networks = new ConnectivityManager.NetworkCallback() {
        private void change() { main.post(() -> { generation++; retryAt = 0; evaluate(); }); }
        @Override public void onAvailable(Network n) { change(); }
        @Override public void onLost(Network n) { change(); }
        @Override public void onCapabilitiesChanged(Network n, NetworkCapabilities c) { change(); }
    };

    @Override public void onCreate() {
        super.onCreate();
        prefs = new AppPrefs(this);
        recovering = prefs.owned();
        power = getSystemService(PowerManager.class);
        keyguard = getSystemService(KeyguardManager.class);
        lastLocked = keyguard.isDeviceLocked();
        shortWake = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PocketFlight:transition");
        shortWake.setReferenceCounted(false);
        fallbackWake = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PocketFlight:nonWakeSensor");
        fallbackWake.setReferenceCounted(false);
        NotificationManager notifications = getSystemService(NotificationManager.class);
        notifications.createNotificationChannel(new NotificationChannel(CHANNEL, "Pocket monitoring", NotificationManager.IMPORTANCE_LOW));
        if (Build.VERSION.SDK_INT >= 34) startForeground(NOTIFICATION, notification(status), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else startForeground(NOTIFICATION, notification(status));
        sensors = getSystemService(SensorManager.class);
        IntentFilter filter = new IntentFilter();
        for (String action : new String[]{Intent.ACTION_SCREEN_OFF, Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT,
                Intent.ACTION_AIRPLANE_MODE_CHANGED, BluetoothDevice.ACTION_ACL_CONNECTED, BluetoothDevice.ACTION_ACL_DISCONNECTED,
                BluetoothAdapter.ACTION_STATE_CHANGED, WifiManager.WIFI_STATE_CHANGED_ACTION, WifiManager.NETWORK_STATE_CHANGED_ACTION}) filter.addAction(action);
        // Bluetooth originates in a privileged non-system UID. Protected system actions only.
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(events, filter, Context.RECEIVER_EXPORTED);
        else registerReceiver(events, filter);
        connectivity = getSystemService(ConnectivityManager.class);
        connectivity.registerNetworkCallback(new NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(), networks);
        prefs.data.registerOnSharedPreferenceChangeListener(preferences);
        bridge = new ShizukuBridge(this, () -> main.post(() -> { invalidateBluetooth(); retryAt = 0; evaluate(); }));
        prefs.event("Monitor started");
        main.post(tick);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && PAUSE.equals(intent.getAction())) prefs.enabled(false);
        evaluate();
        return prefs.enabled() || prefs.owned() ? START_STICKY : START_NOT_STICKY;
    }

    public static boolean airplane(Context context) {
        return Settings.Global.getInt(context.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 0) != 0;
    }
    private void wakeBriefly() { if (!destroyed) shortWake.acquire(25000); }
    private void releaseBriefWake() { if (shortWake.isHeld()) shortWake.release(); }
    private void manageWake(boolean airplane) {
        boolean nonWake = sensorRegistered && !proximity.isWakeUpSensor();
        boolean assistNonWake = prefs.pocketAssist() && lightRegistered && orientationRegistered
                && (!light.isWakeUpSensor() || !orientation.isWakeUpSensor());
        boolean watchExit = airplane && prefs.owned() && prefs.restoreOnExit();
        boolean countingExit = watchExit && exitTimer.running()
                && !exitTimer.due(SystemClock.elapsedRealtime(), prefs.exitMillis());
        // Non-wake sensors must keep delivering while offline too. Wake-up sensors only
        // need a CPU lock during the exit countdown so the deadline runs with the screen off.
        boolean needed = prefs.enabled() && !power.isInteractive()
                && (((nonWake || assistNonWake) && (!airplane || watchExit)) || countingExit);
        if (needed) fallbackWake.acquire(600000);
        else if (fallbackWake.isHeld()) fallbackWake.release();
    }

    private Sensor preferredSensor(int type) {
        Sensor result = sensors.getDefaultSensor(type, true);
        return result == null ? sensors.getDefaultSensor(type) : result;
    }
    private void configureSensors() {
        sensors.unregisterListener(this);
        sensorsActive = true;
        sensorsNeedRefresh = false;
        sensorEpoch = SystemClock.elapsedRealtime();
        detector.reset(); exitTimer.reset(); covered = false; generation++;
        proximity = preferredSensor(Sensor.TYPE_PROXIMITY);
        sensorRegistered = proximity != null && sensors.registerListener(this, proximity, SensorManager.SENSOR_DELAY_NORMAL);
        lightRegistered = orientationRegistered = false;
        light = orientation = null;
        if (prefs.pocketAssist()) {
            light = preferredSensor(Sensor.TYPE_LIGHT);
            orientation = preferredSensor(Sensor.TYPE_GRAVITY);
            if (orientation == null) orientation = preferredSensor(Sensor.TYPE_ACCELEROMETER);
            lightRegistered = light != null && sensors.registerListener(this, light, 200000);
            orientationRegistered = orientation != null && sensors.registerListener(this, orientation, 200000);
        }
    }
    private void suspendSensors() {
        if (sensorsActive) {
            sensors.unregisterListener(this);
            sensorsActive = false;
            sensorRegistered = lightRegistered = orientationRegistered = false;
            detector.reset(); exitTimer.reset(); covered = false; generation++;
            if (report.active()) {
                report.sample(SystemClock.elapsedRealtime(), detector.readings(), "Pocket sensors suspended", true);
                prefs.data.edit().putString("pocket_report", report.text()).apply();
                report.end();
            }
        }
        if (!busy) releaseBriefWake();
        if (fallbackWake.isHeld()) fallbackWake.release();
    }
    private void resumeSensors() {
        if (sensorsActive && !sensorsNeedRefresh) return;
        configureSensors();
        if (!power.isInteractive()) {
            report.begin(SystemClock.elapsedRealtime(), sensorInventory());
            wakeBriefly();
        }
    }

    private void invalidateBluetooth() {
        connectionGeneration++;
        generation++; // Cancel an enable queued before the connection change.
        bluetoothState = UNKNOWN;
        bluetoothCheckedAt = -1;
    }
    private void cacheBluetooth(int result) {
        bluetoothState = result == 1 ? CONNECTED : result == 0 ? CLEAR : UNKNOWN;
        bluetoothCheckedAt = SystemClock.elapsedRealtime();
    }
    private FlightPolicy.ExceptionState bluetoothException(long now) {
        if (prefs.bluetooth().isEmpty()) return CLEAR;
        if (!bridge.ready()) return UNKNOWN;
        if (!bluetoothChecking && (bluetoothCheckedAt < 0 || now - bluetoothCheckedAt >= CONNECTION_RECHECK_MS)) {
            bluetoothChecking = true;
            bluetoothState = UNKNOWN;
            int token = connectionGeneration;
            String[] addresses = prefs.bluetooth().toArray(new String[0]);
            IFlightBridge remote = bridge.remote();
            // No sensor or CPU wake lock for a connection-only check. ACL broadcasts
            // provide prompt changes; the idle timer is only a non-waking fallback.
            worker.execute(() -> {
                int result;
                try { result = remote == null ? -1 : remote.connectedBluetooth(addresses); }
                catch (Exception e) { result = -1; }
                int connected = result;
                main.post(() -> {
                    bluetoothChecking = false;
                    if (destroyed) return;
                    if (token == connectionGeneration) cacheBluetooth(connected);
                    evaluate();
                });
            });
        }
        return bluetoothState;
    }
    private String describe(Sensor sensor, boolean registered) {
        return sensor == null ? "not available" : sensor.getName() + " · "
                + (registered ? "registered" : "registration failed") + " · "
                + (sensor.isWakeUpSensor() ? "wake-up" : "non-wake");
    }
    private String sensorInventory() {
        return "Proximity: " + describe(proximity, sensorRegistered) + "\nLight: " + describe(light, lightRegistered)
                + "\nOrientation: " + describe(orientation, orientationRegistered)
                + "\nPocket assist: " + (prefs.pocketAssist() ? "on, ≤ " + prefs.darkLux() + " lux" : "off");
    }

    private void recordSession(long now, boolean locked, boolean screenOff) {
        if (!report.active()) return;
        boolean finished = !locked && !screenOff;
        String flags = "locked=" + locked + ", screenOff=" + screenOff + ", candidate=" + covered
                + ", exitCountdown=" + exitTimer.running();
        if (report.sample(now, detector.readings(), flags + " · " + reportStatus(status), finished))
            prefs.data.edit().putString("pocket_report", report.text()).apply();
        if (finished) {
            report.end();
            prefs.event("Pocket diagnostic saved · tap Last pocket report");
        }
    }
    private String reportStatus(String value) {
        return value.startsWith("Exception: ") ? "Wi-Fi exception connected" : value;
    }

    @Override public void onConfigurationChanged(android.content.res.Configuration configuration) {
        super.onConfigurationChanged(configuration);
        sensorsNeedRefresh = true;
        evaluate();
    }

    @Override public void onSensorChanged(SensorEvent event) {
        long now = SystemClock.elapsedRealtime();
        // Ignore events queued before the screen-off re-registration or from an old display sensor.
        if (!sensorsActive || event.timestamp / 1000000L < sensorEpoch) return;
        boolean previous = covered;
        if (event.sensor == proximity) detector.proximity(event.values[0], proximity.getMaximumRange());
        else if (event.sensor == light) detector.light(event.values[0]);
        else if (event.sensor == orientation) detector.orientation(event.values[0], event.values[1], event.values[2], now);
        else return;
        covered = detector.candidate(now, prefs.pocketAssist() && lightRegistered && orientationRegistered, prefs.darkLux());
        if (covered != previous) { generation++; if (covered && !power.isInteractive()) wakeBriefly(); }
        boolean exitChanged = updateExitTimer(now, airplane(this), keyguard.isDeviceLocked());
        if (event.sensor != orientation || covered != previous || exitChanged || now - lastEvaluationAt >= 1000) evaluate();
    }
    @Override public void onAccuracyChanged(Sensor s, int accuracy) {}

    private boolean updateExitTimer(long now, boolean airplane, boolean locked) {
        long previous = exitTimer.startedAt();
        boolean assist = prefs.pocketAssist();
        boolean usable = (!assist || (lightRegistered && orientationRegistered))
                && (sensorRegistered || assist);
        boolean armed = prefs.enabled() && prefs.owned() && airplane && locked && prefs.restoreOnExit();
        exitTimer.observe(now, armed, usable && detector.outside(now, sensorRegistered, assist, prefs.darkLux()));
        boolean changed = previous != exitTimer.startedAt();
        if (changed) generation++; // Also invalidates an exit restore queued before re-pocketing.
        return changed;
    }

    private void evaluate() {
        if (destroyed || prefs == null || bridge == null) return;
        try { evaluateState(); }
        finally {
            main.removeCallbacks(tick);
            // Handler delays do not wake a sleeping CPU. Standby has no sensor loop,
            // wake lock, wake-up alarm or Bluetooth scan; broadcasts resume it promptly.
            if (!destroyed && (prefs.enabled() || prefs.owned())) main.postDelayed(tick,
                    sensorsActive || busy || prefs.owned() ? 2000 : CONNECTION_RECHECK_MS);
        }
    }
    private void evaluateState() {
        boolean locked = keyguard.isDeviceLocked();
        boolean screenOff = !power.isInteractive();
        boolean airplane = airplane(this);
        long now = SystemClock.elapsedRealtime();
        lastEvaluationAt = now;
        if (locked != lastLocked) { generation++; lastLocked = locked; }
        if (!locked) suppressed = false;
        if (!locked || !screenOff) detector.resetTimer();
        bridge.connect();
        Connections.Wifi wifi = Connections.wifi(this, prefs.wifi(), true);
        boolean restoring = recovering || !prefs.enabled() || (prefs.owned() && !locked);
        FlightPolicy.ExceptionState bluetooth = wifi.state() == CONNECTED || restoring ? CLEAR : bluetoothException(now);
        FlightPolicy.ExceptionState connection = wifi.state() == CONNECTED || bluetooth == CONNECTED ? CONNECTED
                : wifi.state() == UNKNOWN || bluetooth == UNKNOWN ? UNKNOWN : CLEAR;
        boolean watchExit = prefs.owned() && airplane;
        if (!prefs.enabled() || connection == CONNECTED || (connection == UNKNOWN && !watchExit)) {
            suspendSensors();
            prefs.data.edit().putString("sensor", "Pocket sensors paused while checking or connected to an exception").apply();
        } else {
            resumeSensors();
            boolean previous = covered;
            covered = detector.candidate(now, prefs.pocketAssist() && lightRegistered && orientationRegistered, prefs.darkLux());
            if (previous != covered) generation++;
            updateExitTimer(now, airplane, locked);
            prefs.data.edit().putString("sensor", detector.reason() + "\n" + detector.readings()).apply();
            recordSession(now, locked, screenOff);
            manageWake(airplane);
        }
        if (busy) return;
        // Serialize recovery behind any command still executing in the privileged process.
        if (recovering) {
            if (bridge.ready() && SystemClock.elapsedRealtime() >= retryAt) recover();
            else publish("Recovery pending — start Shizuku, or restore in Quick Settings");
            return;
        }
        if (prefs.owned() && !airplane) {
            prefs.owned(false);
            suppressed = locked;
            prefs.event("Airplane mode is off; respecting manual changes until unlock");
        }
        if (!prefs.enabled() && !prefs.owned()) {
            publish("Paused"); stopSelf(); return;
        }
        // A returning exception restores our own airplane mode before suspending
        // pocket-exit sensing, so a locked phone cannot be stranded offline at home.
        if (prefs.owned() && (!prefs.enabled() || !locked || connection == CONNECTED)) {
            if (bridge.ready() && SystemClock.elapsedRealtime() >= retryAt) changeMode(false);
            else publish("Restore pending — start Shizuku, or turn off airplane mode in Quick Settings");
            return;
        }
        if (airplane && !prefs.owned()) {
            releaseBriefWake(); publish("Manual airplane mode · left unchanged"); return;
        }
        if (connection != CLEAR && !prefs.owned()) {
            releaseBriefWake();
            if (connection == CONNECTED) publish(wifi.state() == CONNECTED
                    ? "Standby · Wi-Fi exception connected · pocket sensors paused"
                    : "Standby · Bluetooth exception connected · pocket sensors paused");
            else publish("Checking exceptions · pocket sensors paused · " + (wifi.state() == UNKNOWN
                    ? wifi.description() : bluetoothChecking ? "Checking Bluetooth connection" : "Bluetooth state unavailable — check Shizuku"));
            return;
        }
        if (exitTimer.due(now, prefs.exitMillis())) {
            if (bridge.ready() && now >= retryAt) changeMode(false, true);
            else publish("Restore pending — start Shizuku, or turn off airplane mode in Quick Settings");
            return;
        }
        if (!sensorRegistered && !(prefs.pocketAssist() && lightRegistered && orientationRegistered)) {
            publish("No usable pocket sensors — automation paused"); return;
        }
        if (!bridge.ready()) { releaseBriefWake(); publish("Waiting for Shizuku — open it, start it and grant access"); return; }
        if (airplane) {
            releaseBriefWake();
            if (exitTimer.running()) {
                long seconds = (exitTimer.remaining(now, prefs.exitMillis()) + 999) / 1000;
                publish(String.format(java.util.Locale.US, "Out of pocket · restoring in %d:%02d", seconds / 60, seconds % 60));
            } else publish(prefs.owned() ? "Pocket detected · airplane mode on" : "Manual airplane mode · left unchanged");
            return;
        }
        if (!locked || !screenOff) { publish("Ready · lock your phone and put it in your pocket"); return; }
        if (suppressed) { publish("Manual override · waiting for your next unlock"); return; }
        if (callActive()) { releaseBriefWake(); publish("Call in progress · staying online"); return; }
        if (!covered) { releaseBriefWake(); publish("Locked · " + detector.reason()); return; }
        if (wifi.state() != FlightPolicy.ExceptionState.CLEAR) { releaseBriefWake(); publish(wifi.description()); return; }
        long duration = detector.duration(now);
        FlightPolicy.State state = new FlightPolicy.State(prefs.enabled(), locked, screenOff, covered, duration,
                prefs.delayMillis(), airplane, prefs.owned(), bridge.ready(), suppressed, wifi.state());
        if (FlightPolicy.decide(state) != FlightPolicy.Action.ENABLE) {
            publish("Pocket check · waiting " + Math.max(1, (prefs.delayMillis() - duration + 999) / 1000) + " seconds");
            return;
        }
        if (SystemClock.elapsedRealtime() >= retryAt) checkBluetooth();
    }

    private void checkBluetooth() {
        busy = true;
        int token = generation;
        int connectionToken = connectionGeneration;
        String[] addresses = prefs.bluetooth().toArray(new String[0]);
        IFlightBridge remote = bridge.remote();
        wakeBriefly();
        worker.execute(() -> {
            int result;
            try { result = remote == null ? -1 : remote.connectedBluetooth(addresses); }
            catch (Exception e) { result = -1; }
            int connected = result;
            main.post(() -> {
                busy = false;
                if (destroyed) return;
                if (connectionToken == connectionGeneration) cacheBluetooth(connected);
                if (token != generation) { evaluate(); return; }
                retryAt = SystemClock.elapsedRealtime() + 5000;
                if (connected != 0) { evaluate(); return; }
                // Re-read every condition after the asynchronous Bluetooth query.
                Connections.Wifi wifi = Connections.wifi(this, prefs.wifi(), true);
                long now = SystemClock.elapsedRealtime();
                boolean currentCovered = detector.candidate(now, prefs.pocketAssist() && lightRegistered && orientationRegistered, prefs.darkLux());
                FlightPolicy.State current = new FlightPolicy.State(prefs.enabled(), keyguard.isDeviceLocked(),
                        !power.isInteractive(), currentCovered, detector.duration(now),
                        prefs.delayMillis(), airplane(this), prefs.owned(), bridge.ready(), suppressed, wifi.state());
                if (FlightPolicy.decide(current) == FlightPolicy.Action.ENABLE && !callActive()) changeMode(true);
                else evaluate();
            });
        });
    }

    private boolean callActive() { return getSystemService(AudioManager.class).getMode() != AudioManager.MODE_NORMAL; }

    private void recover() {
        busy = true;
        IFlightBridge remote = bridge.remote();
        worker.execute(() -> {
            String result;
            try { result = remote == null ? "Unavailable" : remote.probe(); }
            catch (Exception e) { result = "Unavailable"; }
            String read = result;
            main.post(() -> {
                busy = false;
                if (destroyed) return;
                if (read.startsWith("OK: ")) {
                    if (read.equals("OK: disabled")) prefs.owned(false);
                    recovering = false;
                    evaluate();
                } else { retryAt = SystemClock.elapsedRealtime() + 10000; publish("Recovery pending · reconnect Shizuku"); }
            });
        });
    }

    private void changeMode(boolean enabled) {
        changeMode(enabled, false);
    }
    private void changeMode(boolean enabled, boolean afterExit) {
        if (enabled && !prefs.owned(true)) { publish("Could not save recovery state · staying online"); return; }
        if (enabled) exitTimer.reset();
        busy = true;
        wakeBriefly();
        publish(enabled ? "Turning airplane mode on…" : "Restoring connectivity…");
        IFlightBridge remote = bridge.remote();
        int token = generation;
        worker.execute(() -> {
            String result;
            try {
                // Unlock or pause can happen between the main-thread decision and dispatch.
                if (enabled && (destroyed || token != generation || !prefs.enabled() || !keyguard.isDeviceLocked() || power.isInteractive() || callActive())) result = "Cancelled";
                else if (afterExit && prefs.enabled() && keyguard.isDeviceLocked()
                        && (destroyed || token != generation || !prefs.restoreOnExit())) result = "Cancelled";
                else result = remote == null ? "Shizuku disconnected" : remote.setAirplaneMode(enabled);
            } catch (Exception e) { result = "Shizuku connection lost"; }
            String outcome = result;
            main.post(() -> {
                busy = false;
                if (destroyed) return; // Durable ownership journal is retained for recovery.
                boolean actual = airplane(this);
                if (!actual) prefs.owned(false);
                if ("Cancelled".equals(outcome)) { retryAt = 0; evaluate(); return; }
                if ("OK".equals(outcome) && actual == enabled) {
                    if (!enabled) { exitTimer.reset(); detector.resetTimer(); }
                    prefs.event(enabled ? "Pocket detected → airplane mode on" : afterExit
                            ? "Out of pocket → airplane mode off" : "Connectivity restored → airplane mode off");
                    retryAt = 0;
                    evaluate(); // Compensates for an unlock that raced with the enable command.
                } else {
                    retryAt = SystemClock.elapsedRealtime() + 10000;
                    prefs.event(outcome);
                    publish("Action incomplete: " + outcome + (actual ? " · use Quick Settings if needed" : ""));
                    if (enabled && actual && (!prefs.enabled() || !keyguard.isDeviceLocked())) { retryAt = 0; evaluate(); }
                }
            });
        });
    }

    private Notification notification(String text) {
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent pause = PendingIntent.getService(this, 1, new Intent(this, PocketService.class).setAction(PAUSE), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_flight)
                .setContentTitle(getString(R.string.app_name)).setContentText(text).setStyle(new Notification.BigTextStyle().bigText(text))
                .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
                .addAction(new Notification.Action.Builder(null, "Pause & restore", pause).build()).build();
    }
    private void publish(String next) {
        if (next.equals(status)) return;
        status = next;
        prefs.data.edit().putString("status", next).apply();
        if (report.sample(SystemClock.elapsedRealtime(), detector.readings(), reportStatus(next), true))
            prefs.data.edit().putString("pocket_report", report.text()).apply();
        getSystemService(NotificationManager.class).notify(NOTIFICATION, notification(next));
    }
    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public void onDestroy() {
        destroyed = true;
        main.removeCallbacksAndMessages(null);
        if (sensors != null) sensors.unregisterListener(this);
        try { unregisterReceiver(events); } catch (RuntimeException ignored) { }
        try { if (connectivity != null) connectivity.unregisterNetworkCallback(networks); } catch (RuntimeException ignored) { }
        if (prefs != null) {
            if (report.active()) {
                report.sample(SystemClock.elapsedRealtime(), detector.readings(), "Monitor stopped", true);
                prefs.data.edit().putString("pocket_report", report.text()).apply();
            }
            prefs.data.unregisterOnSharedPreferenceChangeListener(preferences);
            prefs.data.edit().putString("status", prefs.owned() ? "Monitor stopped · restoration pending" : "Monitor stopped").apply();
        }
        if (bridge != null) bridge.close();
        if (shortWake != null && shortWake.isHeld()) shortWake.release();
        if (fallbackWake != null && fallbackWake.isHeld()) fallbackWake.release();
        worker.shutdown();
        super.onDestroy();
    }
}
