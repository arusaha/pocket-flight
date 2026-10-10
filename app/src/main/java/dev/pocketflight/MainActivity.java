package dev.pocketflight;

import android.Manifest;
import android.app.*;
import android.bluetooth.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import rikka.shizuku.Shizuku;

public class MainActivity extends Activity {
    private static final int INK = Color.rgb(23, 43, 38), GREEN = Color.rgb(21, 104, 92);
    private static final int MUTED = Color.rgb(88, 105, 98), BG = Color.rgb(244, 245, 239);
    private AppPrefs prefs;
    private ShizukuBridge bridge;
    private LinearLayout column;
    private TextView status, access, wifiSummary, btSummary, sensorStatus, history;
    private Button toggle;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private AlertDialog sensorDialog;
    private boolean resumed;
    private final Runnable refreshTick = new Runnable() {
        @Override public void run() { refresh(); if (resumed) main.postDelayed(this, 1500); }
    };

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new AppPrefs(this);
        buildUi();
        bridge = new ShizukuBridge(this, () -> main.post(this::refresh));
    }
    private int dp(int size) { return Math.round(size * getResources().getDisplayMetrics().density); }
    private GradientDrawable background(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color); drawable.setCornerRadius(dp(radius)); return drawable;
    }
    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value); view.setTextSize(size); view.setTextColor(color);
        view.setLineSpacing(dp(3), 1f);
        if (bold) view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.bottomMargin = dp(10); view.setLayoutParams(p);
        return view;
    }
    private Button button(String label, boolean primary, Runnable action) {
        Button b = new Button(this);
        b.setText(label); b.setTextSize(15); b.setAllCaps(false);
        b.setTextColor(primary ? Color.WHITE : GREEN);
        b.setBackground(background(primary ? GREEN : Color.rgb(231, 238, 229), 16));
        b.setPadding(dp(16), dp(12), dp(16), dp(12)); b.setMinHeight(dp(52));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = dp(6); p.bottomMargin = dp(6); b.setLayoutParams(p);
        b.setOnClickListener(v -> action.run()); return b;
    }
    private LinearLayout card(String title, String description) {
        LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(20), dp(20), dp(20), dp(16)); card.setBackground(background(Color.WHITE, 24));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.bottomMargin = dp(16); card.setLayoutParams(p);
        if (title != null) card.addView(text(title, 20, INK, true));
        if (description != null) card.addView(text(description, 14, MUTED, false));
        column.addView(card); return card;
    }
    private void buildUi() {
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(BG);
        FrameLayout frame = new FrameLayout(this);
        column = new LinearLayout(this); column.setOrientation(LinearLayout.VERTICAL); column.setPadding(dp(20), dp(28), dp(20), dp(30));
        FrameLayout.LayoutParams layout = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        frame.addView(column, layout); scroll.addView(frame);
        scroll.setOnApplyWindowInsetsListener((v, insets) -> {
            var bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom); return insets;
        });
        frame.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            int width = Math.min(r - l, dp(760));
            if (column.getLayoutParams().width != width) { column.getLayoutParams().width = width; column.requestLayout(); }
        });
        setContentView(scroll);
        TextView eyebrow = text("PERSONAL AUTOMATION", 11, GREEN, true); eyebrow.setLetterSpacing(0.15f); column.addView(eyebrow);
        column.addView(text("Pocket Flight", 38, INK, true));
        column.addView(text("Disconnect in your pocket.\nReconnect when you unlock.", 17, MUTED, false));
        LinearLayout hero = card(null, null); hero.setBackground(background(Color.rgb(221, 235, 218), 24));
        hero.addView(text("YOUR POCKET, YOUR RULES", 11, GREEN, true));
        status = text("Paused", 22, INK, true); hero.addView(status);
        hero.addView(text("Screen off + phone locked + pocket detected. Airplane mode stays on until you unlock.", 14, MUTED, false));
        toggle = button("Start monitoring", true, this::toggleMonitoring); hero.addView(toggle);

        LinearLayout setup = card("01  ·  Connect Shizuku", "Required to switch airplane mode on your Z Fold8. Start Shizuku again after each reboot.");
        access = text("Checking access…", 14, GREEN, true); setup.addView(access);
        setup.addView(button("Connect / grant Shizuku access", true, this::authorize));
        setup.addView(button("Read-only connection check", false, this::diagnostics));
        setup.addView(button("Shizuku setup guide ↗", false, () -> open(new Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/guide/setup/")))));

        LinearLayout exceptions = card("02  ·  Stay connected here", "Any selected Wi-Fi network OR Bluetooth device prevents airplane mode while connected. Saved or paired alone does not count.");
        exceptions.addView(text("WI-FI NETWORKS", 11, GREEN, true));
        wifiSummary = text("None selected", 15, INK, false); exceptions.addView(wifiSummary);
        exceptions.addView(button("Choose Wi-Fi exceptions", false, this::wifiDialog));
        exceptions.addView(text("BLUETOOTH DEVICES", 11, GREEN, true));
        btSummary = text("None selected", 15, INK, false); exceptions.addView(btSummary);
        exceptions.addView(button("Choose paired devices", false, this::bluetoothDialog));
        exceptions.addView(text("If a selected exception cannot be checked, the app stays online.", 13, MUTED, false));

        LinearLayout detection = card("03  ·  Pocket detection", "Proximity can detect a hand but miss loose or dark fabric. Try Pocket assist if the folded phone is missed in your pocket.");
        sensorStatus = text("Sensor not checked", 14, INK, false); detection.addView(sensorStatus);
        detection.addView(button("Test proximity sensor", false, this::sensorDiagnostic));
        Switch assist = new Switch(this);
        assist.setText("Pocket assist · darkness + tilt"); assist.setTextSize(15); assist.setTextColor(INK);
        assist.setMinHeight(dp(56)); assist.setChecked(prefs.pocketAssist()); detection.addView(assist);
        detection.addView(text("Adds a fallback when proximity stays clear: low light and a phone tilted at least 35° from flat, held for the confirmation delay. A tilted phone in a dark room can also trigger. This mode can use more battery while locked.", 13, MUTED, false));
        TextView lightLabel = text("Pocket assist light limit: " + Math.round(prefs.darkLux()) + " lux", 14, INK, true); detection.addView(lightLabel);
        SeekBar darkness = new SeekBar(this); darkness.setMax(49); darkness.setProgress(Math.round(prefs.darkLux()) - 1);
        darkness.setContentDescription("Maximum light level for Pocket assist, 1 to 50 lux"); darkness.setEnabled(prefs.pocketAssist()); detection.addView(darkness);
        assist.setOnCheckedChangeListener((button, enabled) -> {
            prefs.data.edit().putBoolean("pocket_assist", enabled).apply(); darkness.setEnabled(enabled);
            if (enabled) toast("Pocket assist enabled. Start monitoring, lock and pocket the phone, then check Last pocket report.");
        });
        darkness.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int p, boolean user) { lightLabel.setText("Pocket assist light limit: " + (p + 1) + " lux"); }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) { prefs.data.edit().putInt("dark_lux", bar.getProgress() + 1).apply(); }
        });
        detection.addView(button("Last pocket report", false, this::showPocketReport));
        detection.addView(text("While monitoring, lock the folded phone, put it in your pocket for 15 seconds, then unlock. The report saves the readings and any reason the app stayed online.", 13, MUTED, false));
        TextView delayLabel = text("Confirmation delay: " + prefs.delayMillis() / 1000 + " seconds", 14, INK, true); detection.addView(delayLabel);
        SeekBar delay = new SeekBar(this); delay.setMax(13); delay.setProgress((int) prefs.delayMillis() / 1000 - 2);
        delay.setContentDescription("Pocket confirmation delay, 2 to 15 seconds"); detection.addView(delay);
        delay.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int p, boolean user) { delayLabel.setText("Confirmation delay: " + (p + 2) + " seconds"); }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) { prefs.data.edit().putInt("delay_seconds", bar.getProgress() + 2).commit(); }
        });

        LinearLayout reliability = card("04  ·  Permissions & reliability", "Wi-Fi names require precise location and “Allow all the time”, plus the phone’s Location switch. No GPS coordinates are requested or stored.");
        reliability.addView(button("Allow location, Bluetooth & notifications", false, this::requestPermissions));
        reliability.addView(button("App settings · location & battery", false, this::appSettings));
        reliability.addView(text("Samsung: set Battery to Unrestricted and add Pocket Flight and Shizuku to Never sleeping apps. Enable “Lock instantly with Side button” in Secure lock settings.", 14, MUTED, false));
        reliability.addView(text("Monitoring has a persistent notification. If your sensor cannot wake the phone, the app keeps the CPU awake while checking with the screen off; this uses more battery.", 13, MUTED, false));

        LinearLayout activity = card("Recent activity", null); history = text("No activity yet", 13, MUTED, false); activity.addView(history);
        column.addView(text("On-device only · No account · No analytics\nManually enabled airplane mode is left unchanged.\nAirplane mode interrupts cellular calls and mobile data. If Shizuku stops while offline, restore connectivity in Quick Settings.", 12, MUTED, false));
    }

    private void refresh() {
        if (isFinishing() || status == null) return;
        status.setText(prefs.data.getString("status", "Paused"));
        toggle.setText(prefs.enabled() ? "Pause & restore connectivity" : prefs.owned() ? "Retry restoring connectivity" : "Start monitoring");
        access.setText(bridge != null && bridge.ready() ? "✓ Shizuku connected" : ShizukuBridge.authorized() ? "Starting connection…" : "Setup needed · Shizuku access");
        Set<String> wifi = prefs.wifi();
        wifiSummary.setText(wifi.isEmpty() ? "No Wi-Fi exceptions selected" : String.join("\n", new TreeSet<>(wifi)));
        Set<String> bt = prefs.bluetooth();
        ArrayList<String> labels = new ArrayList<>();
        for (String address : new TreeSet<>(bt)) labels.add(prefs.data.getString("device_" + address, address));
        btSummary.setText(labels.isEmpty() ? "No Bluetooth exceptions selected" : String.join("\n", labels));
        sensorStatus.setText(prefs.data.getString("sensor", "Use the sensor test before your first pocket test"));
        history.setText(prefs.data.getString("history", "No activity yet"));
    }
    private void toggleMonitoring() {
        if (prefs.enabled() || prefs.owned()) {
            prefs.enabled(false);
            startMonitor(PocketService.PAUSE);
        } else {
            if (!ShizukuBridge.authorized()) { authorize(); return; }
            SensorManager available = getSystemService(SensorManager.class);
            boolean hasAssist = prefs.pocketAssist() && available.getDefaultSensor(Sensor.TYPE_LIGHT) != null
                    && (available.getDefaultSensor(Sensor.TYPE_GRAVITY) != null || available.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null);
            if (available.getDefaultSensor(Sensor.TYPE_PROXIMITY) == null && !hasAssist) {
                message("Sensor unavailable", "Android does not expose usable pocket sensors. Try the cover screen, or enable Pocket assist if light and orientation sensors are available."); return;
            }
            prefs.enabled(true); startMonitor(null);
        }
        refresh();
    }
    private void startMonitor(String action) {
        try { startForegroundService(new Intent(this, PocketService.class).setAction(action)); }
        catch (RuntimeException e) { prefs.enabled(false); message("Could not start", e.getMessage()); }
    }
    private void authorize() {
        try {
            if (!Shizuku.pingBinder()) {
                Intent launch = getPackageManager().getLaunchIntentForPackage("moe.shizuku.privileged.api");
                if (launch != null) open(launch);
                else open(new Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/")));
                return;
            }
            if (!ShizukuBridge.authorized()) Shizuku.requestPermission(40);
            else { bridge.connect(); toast("Shizuku permission is granted"); }
        } catch (RuntimeException e) { message("Shizuku unavailable", "Start Shizuku, then return here and grant Pocket Flight access. " + e.getMessage()); }
    }
    private void requestPermissions() {
        ArrayList<String> missing = new ArrayList<>();
        for (String p : new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION})
            if (!Connections.granted(this, p)) missing.add(p);
        if (Build.VERSION.SDK_INT >= 31 && !Connections.granted(this, Manifest.permission.BLUETOOTH_CONNECT)) missing.add(Manifest.permission.BLUETOOTH_CONNECT);
        if (Build.VERSION.SDK_INT >= 33 && !Connections.granted(this, Manifest.permission.POST_NOTIFICATIONS)) missing.add(Manifest.permission.POST_NOTIFICATIONS);
        if (!missing.isEmpty()) requestPermissions(missing.toArray(new String[0]), 10);
        else locationBackgroundInfo();
    }
    private void locationBackgroundInfo() {
        if (!Connections.granted(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
            new AlertDialog.Builder(this).setTitle("Wi-Fi exceptions while locked")
                    .setMessage("To read your connected Wi-Fi name while the phone is locked, choose Permissions → Location → Allow all the time, and enable precise location. Keep the phone’s Location switch on. Without access, a configured Wi-Fi exception makes automation stay online whenever its connection cannot be identified.")
                    .setPositiveButton("Open app settings", (d, w) -> appSettings()).setNegativeButton("Later", null).show();
        } else toast("Permissions granted. Keep the phone’s Location switch on for Wi-Fi names.");
    }
    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request == 11 && (Build.VERSION.SDK_INT < 31 || Connections.granted(this, Manifest.permission.BLUETOOTH_CONNECT))) bluetoothDialog();
        if (request == 10 && Connections.granted(this, Manifest.permission.ACCESS_FINE_LOCATION)) locationBackgroundInfo();
        refresh();
    }
    private void wifiDialog() {
        LinearLayout body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL); body.setPadding(dp(24), dp(8), dp(24), 0);
        body.addView(text("Enter exact network names, one per line. Names are case-sensitive. Clear the list to remove all Wi-Fi exceptions.", 14, MUTED, false));
        EditText input = new EditText(this); input.setMinLines(3); input.setGravity(Gravity.TOP);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setHint("Home Wi-Fi\nOffice Wi-Fi"); input.setText(String.join("\n", new TreeSet<>(prefs.wifi()))); body.addView(input);
        body.addView(button("Add currently connected Wi-Fi", false, () -> {
            Connections.Wifi current = Connections.wifi(this, Set.of(), false);
            if (current.ssid().isEmpty()) { message("Wi-Fi name unavailable", current.description() + ". You can type the network name or grant location permission first."); return; }
            Set<String> names = wifiLines(input.getText().toString()); names.add(current.ssid()); input.setText(String.join("\n", names));
        }));
        new AlertDialog.Builder(this).setTitle("Wi-Fi exceptions").setView(body)
                .setPositiveButton("Save", (d, w) -> { prefs.wifi(wifiLines(input.getText().toString())); refresh(); })
                .setNegativeButton("Cancel", null).show();
    }
    private Set<String> wifiLines(String raw) {
        Set<String> values = new LinkedHashSet<>();
        for (String line : raw.split("\n")) if (!line.isEmpty()) values.add(line);
        return values;
    }
    @SuppressWarnings("MissingPermission")
    private void bluetoothDialog() {
        if (Build.VERSION.SDK_INT >= 31 && !Connections.granted(this, Manifest.permission.BLUETOOTH_CONNECT)) {
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, 11); return;
        }
        try {
            BluetoothManager manager = getSystemService(BluetoothManager.class);
            BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
            if (adapter == null) { message("Bluetooth unavailable", "This device does not expose a Bluetooth adapter."); return; }
            TreeMap<String, String> devices = new TreeMap<>();
            for (String address : prefs.bluetooth()) devices.put(address, prefs.data.getString("device_" + address, address));
            for (BluetoothDevice device : adapter.getBondedDevices()) {
                String name = device.getName();
                devices.put(device.getAddress(), (name == null ? "Unnamed device" : name) + "\n" + device.getAddress());
            }
            if (devices.isEmpty()) { message("No paired devices", "Pair your car, headphones or watch in Android Bluetooth settings, then return here."); return; }
            String[] addresses = devices.keySet().toArray(new String[0]);
            String[] labels = devices.values().toArray(new String[0]);
            Set<String> selected = prefs.bluetooth(); boolean[] checked = new boolean[addresses.length];
            for (int i = 0; i < addresses.length; i++) checked[i] = selected.contains(addresses[i]);
            new AlertDialog.Builder(this).setTitle("Stay online when connected to…")
                    .setMultiChoiceItems(labels, checked, (d, which, on) -> { if (on) selected.add(addresses[which]); else selected.remove(addresses[which]); })
                    .setPositiveButton("Save", (d, w) -> {
                        prefs.bluetooth(selected);
                        var edit = prefs.data.edit(); for (int i = 0; i < addresses.length; i++) edit.putString("device_" + addresses[i], labels[i]); edit.apply(); refresh();
                    }).setNegativeButton("Cancel", null).show();
        } catch (RuntimeException e) { message("Bluetooth unavailable", "Enable Bluetooth and grant Nearby devices access, then try again."); }
    }
    private void sensorDiagnostic() {
        SensorManager manager = getSystemService(SensorManager.class);
        Sensor sensor = manager.getDefaultSensor(Sensor.TYPE_PROXIMITY, true);
        if (sensor == null) sensor = manager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        if (sensor == null) { message("No sensor found", "Try the other display. Pocket detection requires an Android proximity sensor."); return; }
        Sensor chosen = sensor;
        TextView result = text("Waiting for a reading…", 18, INK, true); result.setPadding(dp(24), dp(16), dp(24), dp(20));
        SensorEventListener listener = new SensorEventListener() {
            @Override public void onSensorChanged(SensorEvent event) {
                boolean covered = event.values[0] < Math.min(chosen.getMaximumRange(), 5f);
                result.setText((covered ? "COVERED" : "CLEAR") + "  ·  " + event.values[0] + " cm\n\n" + chosen.getName()
                        + "\n" + (chosen.isWakeUpSensor() ? "Wake-up sensor available" : "Non-wake sensor: monitoring needs extra battery")
                        + "\n\nCover and uncover the sensor near the top of the active display. Repeat with the phone folded and unfolded. This test does not change airplane mode.");
            }
            @Override public void onAccuracyChanged(Sensor s, int accuracy) {}
        };
        sensorDialog = new AlertDialog.Builder(this).setTitle("Live proximity test").setView(result).setPositiveButton("Done", null).create();
        sensorDialog.setOnDismissListener(d -> manager.unregisterListener(listener)); sensorDialog.show();
        if (!manager.registerListener(listener, chosen, SensorManager.SENSOR_DELAY_NORMAL)) result.setText("Android could not start this sensor.");
    }
    private void showPocketReport() {
        String report = prefs.data.getString("pocket_report", "No pocket report yet. Start monitoring, lock the phone, pocket it for 15 seconds, then unlock and return here.");
        TextView body = text(report, 13, INK, false); body.setTextIsSelectable(true); body.setPadding(dp(20), dp(12), dp(20), dp(12));
        ScrollView scroll = new ScrollView(this); scroll.addView(body);
        new AlertDialog.Builder(this).setTitle("Last pocket report · v" + BuildConfig.VERSION_NAME).setView(scroll)
                .setPositiveButton("Done", null).setNeutralButton("Copy report", (d, w) -> {
                    getSystemService(android.content.ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("Pocket Flight report", report));
                    toast("Report copied");
                }).show();
    }
    private void diagnostics() {
        if (bridge == null || !bridge.ready()) { authorize(); return; }
        IFlightBridge remote = bridge.remote(); String[] devices = prefs.bluetooth().toArray(new String[0]);
        Connections.Wifi wifi = Connections.wifi(this, prefs.wifi(), true);
        toast("Checking Shizuku and connection exceptions…");
        worker.execute(() -> {
            String output;
            try {
                int bt = remote.connectedBluetooth(devices);
                output = "Airplane command: " + remote.probe() + "\n\nWi-Fi: " + wifi.description()
                        + "\n\nBluetooth exception: " + (bt == 1 ? "connected" : bt == 0 ? "none connected" : "unavailable; automation will stay online")
                        + "\n\nThis read-only check does not toggle airplane mode. Test lock → pocket → unlock next to verify your phone’s behavior.";
            } catch (Exception e) { output = "Shizuku connection failed. Start Shizuku and try again."; }
            String report = output;
            main.post(() -> { if (!isFinishing() && !isDestroyed()) message("Connection check", report); });
        });
    }
    private void appSettings() { open(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()))); }
    private void open(Intent intent) { try { startActivity(intent); } catch (RuntimeException e) { toast("No app available to open this screen"); } }
    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
    private void message(String title, String message) { new AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("OK", null).show(); }
    @Override public void onResume() {
        super.onResume(); resumed = true;
        if (prefs.enabled() || prefs.owned()) startMonitor(null);
        main.post(refreshTick);
    }
    @Override public void onPause() { resumed = false; main.removeCallbacks(refreshTick); super.onPause(); }
    @Override public void onDestroy() {
        if (sensorDialog != null) sensorDialog.dismiss();
        if (bridge != null) bridge.close();
        worker.shutdown(); main.removeCallbacksAndMessages(null); super.onDestroy();
    }
}
