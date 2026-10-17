package dev.pocketflight;

import android.app.KeyguardManager;
import android.app.AlarmManager;
import android.content.Context;
import android.content.Intent;
import android.content.BroadcastReceiver;
import android.bluetooth.BluetoothDevice;
import android.net.ConnectivityManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorManager;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowSensor;
import org.robolectric.shadows.ShadowNetwork;
import org.robolectric.shadows.ShadowAlarmManager;
import org.robolectric.shadows.ShadowSystemClock;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, shadows = {PocketServiceTest.TestBridge.class, PocketServiceTest.TestConnections.class,
        PocketServiceTest.TestAlarms.class}, instrumentedPackages = "dev.pocketflight")
public class PocketServiceTest {
    private ServiceController<PocketService> controller;
    private PocketService service;
    private AppPrefs prefs;
    private Sensor proximity;
    private QueuedWorker worker = new QueuedWorker();
    private final List<Boolean> commands = new ArrayList<>();
    private int bluetoothResult, bluetoothQueries;

    @Implements(AlarmManager.class)
    public static class TestAlarms extends ShadowAlarmManager {
        static boolean fail;
        @Implementation @Override protected void setAndAllowWhileIdle(int type, long when, android.app.PendingIntent operation) {
            if (fail) throw new SecurityException("Simulated scheduling failure");
            super.setAndAllowWhileIdle(type, when, operation);
        }
    }

    @Implements(value = Connections.class, isInAndroidSdk = false)
    public static class TestConnections {
        static FlightPolicy.ExceptionState wifi = FlightPolicy.ExceptionState.CLEAR;
        static int reads;
        @Implementation protected static Connections.Wifi wifi(Context context, Set<String> exemptions, boolean background) {
            reads++;
            return new Connections.Wifi(exemptions.isEmpty() ? FlightPolicy.ExceptionState.CLEAR : wifi,
                    wifi == FlightPolicy.ExceptionState.CONNECTED ? "Exception: Home" : "Wi-Fi connection check", "Home");
        }
    }

    @Implements(value = ShizukuBridge.class, isInAndroidSdk = false)
    public static class TestBridge {
        static boolean available;
        static IFlightBridge remote;
        static Runnable changed;
        @Implementation protected void __constructor__(Context context, Runnable callback) { changed = callback; }
        @Implementation protected void connect() {}
        @Implementation protected boolean ready() { return available; }
        @Implementation protected IFlightBridge remote() { return remote; }
        @Implementation protected void close() {}
    }
    private static class QueuedWorker extends AbstractExecutorService {
        final ArrayDeque<Runnable> pending = new ArrayDeque<>();
        boolean stopped;
        @Override public void execute(Runnable command) { pending.add(command); }
        @Override public void shutdown() { stopped = true; }
        @Override public List<Runnable> shutdownNow() { stopped = true; return new ArrayList<>(pending); }
        @Override public boolean isShutdown() { return stopped; }
        @Override public boolean isTerminated() { return stopped && pending.isEmpty(); }
        @Override public boolean awaitTermination(long timeout, TimeUnit unit) { return isTerminated(); }
        void runNext() { assertFalse("Expected a queued radio command", pending.isEmpty()); pending.remove().run(); }
    }
    @Before public void open() {
        TestBridge.available = true;
        TestAlarms.fail = false;
        // Existing regression cases explicitly exercise continuous exit detection.
        new AppPrefs(RuntimeEnvironment.getApplication()).data.edit().putBoolean("hybrid_exit", false).commit();
        ShadowAlarmManager.setAutoSchedule(false);
        TestConnections.wifi = FlightPolicy.ExceptionState.CLEAR; TestConnections.reads = 0;
        proximity = ShadowSensor.newInstance(Sensor.TYPE_PROXIMITY);
        ReflectionHelpers.setField(proximity, "mMaxRange", 5f);
        ReflectionHelpers.setField(proximity, "mFlags", 1); // Wake-up on-change proximity.
        Shadows.shadowOf(RuntimeEnvironment.getApplication().getSystemService(SensorManager.class)).addSensor(proximity);
        controller = Robolectric.buildService(PocketService.class); service = controller.get();
        ExecutorService original = ReflectionHelpers.getField(service, "worker"); original.shutdown();
        ReflectionHelpers.setField(service, "worker", worker);
        controller.create();
        prefs = new AppPrefs(service);
        Shadows.shadowOf(service.getSystemService(KeyguardManager.class)).setIsDeviceLocked(true);
        Shadows.shadowOf(service.getSystemService(PowerManager.class)).setIsInteractive(false);
        Settings.Global.putInt(service.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 1);
        prefs.owned(true); prefs.enabled(true);
        TestBridge.remote = new IFlightBridge.Stub() {
            @Override public String setAirplaneMode(boolean enabled) {
                commands.add(enabled);
                Settings.Global.putInt(service.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, enabled ? 1 : 0);
                return "OK";
            }
            @Override public int connectedBluetooth(String[] addresses) {
                if (addresses.length == 0) return 0;
                bluetoothQueries++; return bluetoothResult;
            }
            @Override public String probe() { return PocketService.airplane(service) ? "OK: enabled" : "OK: disabled"; }
            @Override public void destroy() {}
        };
        service.onStartCommand(null, 0, 0);
        sample(0);
    }
    @After public void close() { controller.destroy(); TestBridge.remote = null; }
    private void sample(float distance) {
        sampleSensor(proximity, distance);
    }
    private void sampleSensor(Sensor sensor, float... values) {
        SensorEvent event = ReflectionHelpers.callConstructor(SensorEvent.class, ReflectionHelpers.ClassParameter.from(int.class, 3));
        event.sensor = sensor; System.arraycopy(values, 0, event.values, 0, values.length);
        event.timestamp = SystemClock.elapsedRealtimeNanos();
        service.onSensorChanged(event);
    }
    private void advance(long seconds) { Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(seconds)); }
    private void finishCommand() { worker.runNext(); Shadows.shadowOf(Looper.getMainLooper()).idle(); }
    private PowerManager.WakeLock monitoringWake() { return ReflectionHelpers.getField(service, "fallbackWake"); }
    private PowerManager.WakeLock transitionWake() { return ReflectionHelpers.getField(service, "shortWake"); }
    private boolean listening() { return Shadows.shadowOf(service.getSystemService(SensorManager.class)).hasListener(service); }
    private ShadowAlarmManager alarmManager() { return Shadows.shadowOf(service.getSystemService(AlarmManager.class)); }
    private String status() { return prefs.data.getString("status", ""); }
    private void enableHybrid() {
        prefs.data.edit().putBoolean("hybrid_exit", true).commit();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }
    private void restart() {
        controller.destroy(); worker = new QueuedWorker();
        controller = Robolectric.buildService(PocketService.class); service = controller.get();
        ExecutorService original = ReflectionHelpers.getField(service, "worker"); original.shutdown();
        ReflectionHelpers.setField(service, "worker", worker);
        controller.create();
        service.onStartCommand(null, 0, 0); Shadows.shadowOf(Looper.getMainLooper()).idle();
    }
    private void online() {
        prefs.owned(false);
        Settings.Global.putInt(service.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 0);
        service.onStartCommand(null, 0, 0);
    }
    private void broadcast(String action) {
        BroadcastReceiver receiver = ReflectionHelpers.getField(service, "events");
        receiver.onReceive(service, new Intent(action));
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }
    private void wifiChanged(FlightPolicy.ExceptionState state) {
        TestConnections.wifi = state;
        ConnectivityManager.NetworkCallback callback = ReflectionHelpers.getField(service, "networks");
        if (state == FlightPolicy.ExceptionState.CLEAR) callback.onLost(ShadowNetwork.newInstance(42));
        else callback.onAvailable(ShadowNetwork.newInstance(42));
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }

    @Test public void restoresAfterTwoMinutesOutsideWhileStillLockedAndReleasesCountdownWake() {
        assertTrue(proximity.isWakeUpSensor()); assertFalse(monitoringWake().isHeld());
        sample(5); assertTrue(monitoringWake().isHeld());
        advance(119); assertTrue(worker.pending.isEmpty()); assertTrue(PocketService.airplane(service));
        advance(3); finishCommand();
        assertEquals(List.of(false), commands); assertFalse(PocketService.airplane(service)); assertFalse(prefs.owned());
        assertFalse(monitoringWake().isHeld());
        assertTrue(prefs.data.getString("history", "").contains("Out of pocket"));
    }
    @Test public void repocketingResetsCountdownAndCancelsAlreadyQueuedRestore() {
        sample(5); advance(100); sample(0); advance(40); assertTrue(worker.pending.isEmpty());
        sample(5); advance(122); assertEquals(1, worker.pending.size());
        sample(0); finishCommand();
        assertTrue(commands.isEmpty()); assertTrue(PocketService.airplane(service)); assertTrue(prefs.owned());
        assertFalse(monitoringWake().isHeld());
    }
    @Test public void manualAirplaneModeAndDisabledOptionDoNotRestore() {
        prefs.owned(false); sample(5); advance(122);
        assertTrue(worker.pending.isEmpty()); assertFalse(monitoringWake().isHeld());
        prefs.owned(true); prefs.data.edit().putBoolean("restore_on_exit", false).commit();
        sample(5); advance(122); assertTrue(worker.pending.isEmpty());
    }
    @Test public void unreadSensorsNeverBeginCountdownAndNonWakeMonitoringContinuesOffline() {
        ReflectionHelpers.setField(proximity, "mFlags", 0);
        sample(Float.NaN); advance(122);
        assertTrue(worker.pending.isEmpty()); assertTrue(monitoringWake().isHeld());
        prefs.data.edit().putBoolean("restore_on_exit", false).commit();
        advance(2); assertFalse(monitoringWake().isHeld());
    }
    @Test public void changingDelayStartsNewIntervalAndLossOfShizukuKeepsOwnership() {
        sample(5); advance(100);
        prefs.data.edit().putInt("exit_minutes", 1).commit(); advance(58); assertTrue(worker.pending.isEmpty());
        TestBridge.available = false; advance(4); assertTrue(worker.pending.isEmpty()); assertTrue(prefs.owned());
        TestBridge.available = true; service.onStartCommand(null, 0, 0); finishCommand();
        assertFalse(prefs.owned()); assertEquals(List.of(false), commands);
    }
    @Test public void unlockRestoresImmediatelyEvenWithoutOutsideEvidence() {
        Shadows.shadowOf(service.getSystemService(KeyguardManager.class)).setIsDeviceLocked(false);
        service.onStartCommand(null, 0, 0); finishCommand();
        assertEquals(List.of(false), commands); assertFalse(prefs.owned());
    }
    @Test public void selectedWifiSuspendsEverySensorAndWakeLockWithoutWaitingForPocket() {
        online(); sample(5);
        ReflectionHelpers.setField(proximity, "mFlags", 0);
        Sensor light = ShadowSensor.newInstance(Sensor.TYPE_LIGHT);
        Sensor gravity = ShadowSensor.newInstance(Sensor.TYPE_GRAVITY);
        var manager = Shadows.shadowOf(service.getSystemService(SensorManager.class));
        manager.addSensor(light); manager.addSensor(gravity);
        prefs.data.edit().putBoolean("pocket_assist", true).commit();
        broadcast(Intent.ACTION_SCREEN_OFF);
        assertTrue(manager.hasListener(service, proximity));
        assertTrue(manager.hasListener(service, light)); assertTrue(manager.hasListener(service, gravity));
        assertTrue(monitoringWake().isHeld());
        prefs.wifi(Set.of("Home")); wifiChanged(FlightPolicy.ExceptionState.CONNECTED);
        assertFalse(listening()); assertFalse(monitoringWake().isHeld()); assertFalse(transitionWake().isHeld());
        assertTrue(prefs.data.getString("status", "").startsWith("Standby · Wi-Fi"));
        broadcast(Intent.ACTION_SCREEN_OFF);
        service.onConfigurationChanged(service.getResources().getConfiguration());
        sample(0); // An old queued sensor event cannot restart detection or acquire a lock.
        assertFalse(listening()); assertFalse(transitionWake().isHeld()); assertFalse(monitoringWake().isHeld());
        int reads = TestConnections.reads;
        advance(59); assertEquals(reads, TestConnections.reads); assertTrue(worker.pending.isEmpty());
        advance(1); assertEquals(reads + 1, TestConnections.reads); assertFalse(listening());
    }
    @Test public void knownWifiAtStartupDoesNotRegisterSensorsEvenWhenShizukuUnavailable() {
        online(); prefs.wifi(Set.of("Home")); TestConnections.wifi = FlightPolicy.ExceptionState.CONNECTED;
        TestBridge.available = false; restart();
        assertFalse(listening()); assertFalse(monitoringWake().isHeld()); assertFalse(transitionWake().isHeld());
        assertTrue(prefs.data.getString("status", "").startsWith("Standby · Wi-Fi"));
    }
    @Test public void disconnectRequiresFreshReadingsAndFullEntryDelay() {
        online(); prefs.wifi(Set.of("Home")); sample(0); advance(4);
        wifiChanged(FlightPolicy.ExceptionState.CONNECTED); advance(20);
        wifiChanged(FlightPolicy.ExceptionState.CLEAR);
        assertTrue(listening()); advance(8); assertTrue(worker.pending.isEmpty());
        sample(0); advance(4); assertTrue(worker.pending.isEmpty());
        advance(2); finishCommand(); finishCommand();
        assertEquals(List.of(true), commands); assertTrue(PocketService.airplane(service));
    }
    @Test public void bluetoothAlreadyConnectedIsCheckedBeforeSensorsAndUsesIdleRechecks() {
        online(); bluetoothResult = 1; prefs.bluetooth(Set.of("AA:BB:CC:DD:EE:FF"));
        restart();
        assertFalse(listening()); finishCommand();
        assertFalse(listening()); assertFalse(monitoringWake().isHeld()); assertFalse(transitionWake().isHeld());
        assertTrue(prefs.data.getString("status", "").startsWith("Standby · Bluetooth"));
        assertEquals(1, bluetoothQueries); advance(59); assertTrue(worker.pending.isEmpty());
        advance(1); finishCommand(); assertEquals(2, bluetoothQueries);
        bluetoothResult = 0; broadcast(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        assertFalse(listening()); finishCommand(); assertTrue(listening());
    }
    @Test public void eitherConnectionKeepsStandbyUntilBothHaveDisconnected() {
        online(); prefs.wifi(Set.of("Home")); bluetoothResult = 1;
        wifiChanged(FlightPolicy.ExceptionState.CONNECTED);
        prefs.bluetooth(Set.of("AA:BB:CC:DD:EE:FF")); broadcast(BluetoothDevice.ACTION_ACL_CONNECTED);
        assertFalse(listening()); assertTrue(worker.pending.isEmpty()); // Wi-Fi alone is sufficient.
        wifiChanged(FlightPolicy.ExceptionState.CLEAR); finishCommand();
        assertFalse(listening());
        wifiChanged(FlightPolicy.ExceptionState.CONNECTED);
        bluetoothResult = 0; broadcast(BluetoothDevice.ACTION_ACL_DISCONNECTED); assertFalse(listening());
        wifiChanged(FlightPolicy.ExceptionState.CLEAR); finishCommand();
        assertTrue(listening()); assertTrue(commands.isEmpty());
    }
    @Test public void connectionChangeInvalidatesAnEnableAlreadyQueuedToTheWorker() {
        online(); prefs.wifi(Set.of("Home")); sample(0); advance(6); finishCommand();
        assertEquals(1, worker.pending.size()); assertTrue(prefs.owned());
        wifiChanged(FlightPolicy.ExceptionState.CONNECTED); finishCommand();
        assertTrue(commands.isEmpty()); assertFalse(prefs.owned()); assertFalse(listening());
        assertFalse(transitionWake().isHeld());
        assertTrue(prefs.data.getString("status", "").startsWith("Standby"));
    }
    @Test public void staleBluetoothReplyCannotResumeSensorsAfterAConnectionEvent() {
        online(); prefs.bluetooth(Set.of("AA:BB:CC:DD:EE:FF"));
        service.onStartCommand(null, 0, 0); Shadows.shadowOf(Looper.getMainLooper()).idle();
        worker.runNext(); // A clear reply has been posted but not delivered to the main thread.
        bluetoothResult = 1; broadcast(BluetoothDevice.ACTION_ACL_CONNECTED);
        assertFalse(listening()); assertEquals(1, worker.pending.size());
        finishCommand(); assertFalse(listening()); assertEquals(2, bluetoothQueries);
    }
    @Test public void reconnectRestoresOwnedAirplaneModeBeforeStandbyButLeavesManualModeAlone() {
        prefs.wifi(Set.of("Home")); wifiChanged(FlightPolicy.ExceptionState.CONNECTED);
        assertFalse(listening()); finishCommand();
        assertEquals(List.of(false), commands); assertFalse(prefs.owned()); assertFalse(monitoringWake().isHeld());
        assertFalse(transitionWake().isHeld()); assertFalse(listening());
        Settings.Global.putInt(service.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 1);
        broadcast(Intent.ACTION_AIRPLANE_MODE_CHANGED); advance(122);
        assertTrue(worker.pending.isEmpty()); assertTrue(PocketService.airplane(service));
        assertTrue(prefs.data.getString("status", "").startsWith("Manual airplane mode"));
    }
    @Test public void unknownConnectionParksEntryButKeepsOwnedPocketExitAndUnlockRestoration() {
        prefs.wifi(Set.of("Home")); wifiChanged(FlightPolicy.ExceptionState.UNKNOWN);
        assertTrue(listening()); sample(5); advance(122); finishCommand();
        assertEquals(List.of(false), commands); assertFalse(listening());
        assertTrue(prefs.data.getString("status", "").startsWith("Checking exceptions"));
        Settings.Global.putInt(service.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 1); prefs.owned(true);
        Shadows.shadowOf(service.getSystemService(KeyguardManager.class)).setIsDeviceLocked(false);
        broadcast(Intent.ACTION_USER_PRESENT); finishCommand(); assertFalse(prefs.owned());
    }
    @Test public void shizukuReconnectRefreshesBluetoothAndPairedOnlyDeviceDoesNotBlockMonitoring() {
        online(); prefs.bluetooth(Set.of("AA:BB:CC:DD:EE:FF")); bluetoothResult = -1;
        service.onStartCommand(null, 0, 0); Shadows.shadowOf(Looper.getMainLooper()).idle(); finishCommand();
        assertFalse(listening()); assertTrue(commands.isEmpty());
        bluetoothResult = 0; TestBridge.changed.run(); Shadows.shadowOf(Looper.getMainLooper()).idle();
        finishCommand(); assertTrue(listening());
        sample(0); advance(6); finishCommand(); finishCommand();
        assertEquals(List.of(true), commands);
    }
    @Test public void hybridUsesFiveSecondChecksAndSleepsWithoutSensorOrCpuLocks() {
        enableHybrid(); sample(0);
        assertTrue(listening()); assertTrue(monitoringWake().isHeld()); assertTrue(status().startsWith("Hybrid check"));
        advance(5);
        assertFalse(listening()); assertFalse(monitoringWake().isHeld()); assertFalse(transitionWake().isHeld());
        assertTrue(status().startsWith("Hybrid rest"));
        var alarm = alarmManager().peekNextScheduledAlarm(); assertNotNull(alarm);
        assertEquals(AlarmManager.ELAPSED_REALTIME_WAKEUP, alarm.type); assertTrue(alarm.allowWhileIdle);
        assertEquals(0, alarm.interval); assertEquals(SystemClock.elapsedRealtime() + 60000, alarm.triggerAtTime);
        sample(5); // A sensor event queued before suspension must not start a countdown.
        int reads = TestConnections.reads;
        advance(59); assertEquals(reads, TestConnections.reads); assertTrue(worker.pending.isEmpty());
        broadcast(Intent.ACTION_SCREEN_OFF); service.onConfigurationChanged(service.getResources().getConfiguration());
        assertFalse(listening()); assertFalse(monitoringWake().isHeld());
        advance(1); assertTrue(listening()); assertTrue(monitoringWake().isHeld());
        assertNull(alarmManager().peekNextScheduledAlarm());
        advance(5); assertFalse(listening()); assertTrue(worker.pending.isEmpty());
    }
    @Test public void delayedHybridAlarmNeedsFreshEvidenceThenTheEntireContinuousInterval() {
        enableHybrid(); sample(0); advance(5);
        var alarm = alarmManager().peekNextScheduledAlarm();
        ShadowSystemClock.simulateDeepSleep(Duration.ofMinutes(12));
        alarmManager().fireAlarm(alarm); Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertTrue(listening()); assertTrue(worker.pending.isEmpty()); assertTrue(status().startsWith("Hybrid check"));
        sample(5); assertTrue(status().startsWith("Out of pocket")); assertTrue(monitoringWake().isHeld());
        assertNull(alarmManager().peekNextScheduledAlarm());
        advance(119); assertTrue(listening()); assertTrue(worker.pending.isEmpty());
        advance(3); finishCommand();
        assertEquals(List.of(false), commands); assertFalse(prefs.owned()); assertFalse(monitoringWake().isHeld());
        assertNull(alarmManager().peekNextScheduledAlarm());
    }
    @Test public void hybridRepocketingCancelsQueuedRestoreAndReturnsToRest() {
        enableHybrid(); sample(5); advance(122); assertEquals(1, worker.pending.size());
        sample(0); finishCommand();
        assertTrue(PocketService.airplane(service)); assertTrue(prefs.owned()); assertTrue(commands.isEmpty());
        assertFalse(listening()); assertFalse(monitoringWake().isHeld()); assertFalse(transitionWake().isHeld());
        assertTrue(status().startsWith("Hybrid rest")); assertNotNull(alarmManager().peekNextScheduledAlarm());
        advance(60); sample(5); advance(119); assertTrue(worker.pending.isEmpty());
        advance(3); finishCommand(); assertEquals(List.of(false), commands);
    }
    @Test public void hybridAssistRequiresFreshOutsideEvidenceAndStaleTiltEndsConfirmation() {
        Sensor light = ShadowSensor.newInstance(Sensor.TYPE_LIGHT);
        Sensor gravity = ShadowSensor.newInstance(Sensor.TYPE_GRAVITY);
        var sensors = Shadows.shadowOf(service.getSystemService(SensorManager.class));
        sensors.addSensor(light); sensors.addSensor(gravity);
        prefs.data.edit().putBoolean("pocket_assist", true).commit(); enableHybrid();
        sample(5); sampleSensor(light, 0); sampleSensor(gravity, 9.8f, 0, 0);
        advance(5); assertFalse(listening()); assertTrue(worker.pending.isEmpty());
        advance(60); sample(5); sampleSensor(light, 0); sampleSensor(gravity, 0, 0, 9.8f);
        assertTrue(status().startsWith("Out of pocket"));
        advance(4); assertFalse(listening()); assertTrue(status().startsWith("Hybrid rest"));
        advance(60); sampleSensor(light, 100); sampleSensor(gravity, 0, 0, 9.8f);
        assertTrue(status().startsWith("Hybrid check")); // Still missing fresh proximity.
        sample(5); assertTrue(status().startsWith("Out of pocket"));
        advance(10); assertTrue(listening()); // Brightness is sufficient even after tilt ages.
        assertTrue(worker.pending.isEmpty());
    }
    @Test public void hybridUnlockAndPauseDoNotWaitForTheNextCheck() {
        enableHybrid(); sample(0); advance(5);
        Shadows.shadowOf(service.getSystemService(KeyguardManager.class)).setIsDeviceLocked(false);
        broadcast(Intent.ACTION_USER_PRESENT); finishCommand();
        assertFalse(prefs.owned()); assertNull(alarmManager().peekNextScheduledAlarm());
        Shadows.shadowOf(service.getSystemService(KeyguardManager.class)).setIsDeviceLocked(true);
        Settings.Global.putInt(service.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 1); prefs.owned(true);
        service.onStartCommand(null, 0, 0); sample(0); advance(5); assertFalse(listening());
        service.onStartCommand(new Intent(PocketService.PAUSE), 0, 0); finishCommand();
        assertEquals(List.of(false, false), commands); assertFalse(prefs.enabled()); assertFalse(prefs.owned());
        assertNull(alarmManager().peekNextScheduledAlarm()); assertFalse(monitoringWake().isHeld());
    }
    @Test public void connectionReturnInterruptsHybridRestAndCancelsItsAlarm() {
        prefs.wifi(Set.of("Home")); enableHybrid(); sample(0); advance(5);
        wifiChanged(FlightPolicy.ExceptionState.CONNECTED); finishCommand();
        assertEquals(List.of(false), commands); assertFalse(prefs.owned()); assertFalse(listening());
        assertNull(alarmManager().peekNextScheduledAlarm()); assertTrue(status().startsWith("Standby"));
    }
    @Test public void disablingHybridResumesContinuousSensingAndDisablingExitCancelsItsAlarm() {
        enableHybrid(); sample(0); advance(5); assertNotNull(alarmManager().peekNextScheduledAlarm());
        prefs.data.edit().putBoolean("hybrid_exit", false).commit(); Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertTrue(listening()); assertNull(alarmManager().peekNextScheduledAlarm());
        sample(0); advance(65); assertTrue(listening());
        enableHybrid(); sample(0); advance(5);
        prefs.data.edit().putBoolean("restore_on_exit", false).commit(); Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertNull(alarmManager().peekNextScheduledAlarm()); assertFalse(monitoringWake().isHeld());
        sample(5); advance(122); assertTrue(worker.pending.isEmpty()); assertTrue(PocketService.airplane(service));
    }
    @Test public void manualAirplaneModeNeverStartsHybridChecks() {
        prefs.owned(false); enableHybrid(); sample(5); advance(122);
        assertNull(alarmManager().peekNextScheduledAlarm()); assertTrue(worker.pending.isEmpty());
        assertTrue(PocketService.airplane(service)); assertTrue(status().startsWith("Manual airplane mode"));
    }
    @Test public void hybridRestartAndSettingChangesNeverReuseAConfirmationInterval() {
        enableHybrid(); sample(5); advance(100);
        restart(); finishCommand(); // Reconcile durable ownership before starting fresh sensing.
        assertTrue(status().startsWith("Hybrid check")); sample(5);
        advance(100); assertTrue(worker.pending.isEmpty());
        prefs.data.edit().putInt("exit_minutes", 1).commit(); Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertTrue(status().startsWith("Hybrid check")); sample(5);
        advance(59); assertTrue(worker.pending.isEmpty()); advance(3); finishCommand();
        assertEquals(List.of(false), commands);
    }
    @Test public void hybridSchedulingFailureFallsBackToContinuousSensing() {
        TestAlarms.fail = true; enableHybrid(); sample(0); advance(5);
        assertTrue(listening()); assertNull(alarmManager().peekNextScheduledAlarm());
        assertTrue(status().startsWith("Hybrid unavailable"));
        sample(5); advance(122); finishCommand(); assertEquals(List.of(false), commands);
    }
    @Test public void hybridDestructionCancelsAlarmAndAllWakeLocks() {
        enableHybrid(); sample(0); advance(5);
        assertNotNull(alarmManager().peekNextScheduledAlarm());
        service.onDestroy();
        assertNull(alarmManager().peekNextScheduledAlarm()); assertFalse(listening());
        assertFalse(monitoringWake().isHeld()); assertFalse(transitionWake().isHeld());
    }
    @Test @Config(sdk = 30) public void android11HybridAlarmCanResumeThePrivateReceiver() {
        enableHybrid(); sample(0); advance(5);
        var alarm = alarmManager().peekNextScheduledAlarm(); assertNotNull(alarm);
        ShadowSystemClock.simulateDeepSleep(Duration.ofMinutes(2));
        alarmManager().fireAlarm(alarm); Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertTrue(listening()); assertTrue(status().startsWith("Hybrid check"));
        sample(5); advance(122); finishCommand(); assertEquals(List.of(false), commands);
    }
}
