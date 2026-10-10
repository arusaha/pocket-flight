package dev.pocketflight;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.os.Binder;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/** Runs only inside Shizuku's shell/root process. No arbitrary command interface. */
public class FlightBridgeService extends IFlightBridge.Stub {
    public FlightBridgeService() {}

    private String command(String... args) throws Exception {
        Process process = new ProcessBuilder(args).redirectErrorStream(true).start();
        try {
            if (!process.waitFor(6, TimeUnit.SECONDS)) throw new IllegalStateException("System command timed out");
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int count;
            while ((count = process.getInputStream().read(buffer)) != -1) bytes.write(buffer, 0, count);
            String output = bytes.toString(StandardCharsets.UTF_8.name()).trim();
            if (process.exitValue() != 0) throw new IllegalStateException("Android rejected command: " + output);
            return output;
        } finally { process.destroy(); }
    }

    @Override public synchronized String probe() {
        try {
            String result = command("/system/bin/cmd", "connectivity", "airplane-mode");
            return result.equals("enabled") || result.equals("disabled") ? "OK: " + result : "Unavailable: " + result;
        } catch (Exception e) { return "Unavailable: " + e.getMessage(); }
    }

    @Override public synchronized String setAirplaneMode(boolean enabled) {
        try {
            command("/system/bin/cmd", "connectivity", "airplane-mode", enabled ? "enable" : "disable");
            String expected = enabled ? "enabled" : "disabled";
            for (int i = 0; i < 8; i++) {
                if (command("/system/bin/cmd", "connectivity", "airplane-mode").equals(expected)) return "OK";
                Thread.sleep(150);
            }
            return "Android did not confirm airplane mode " + expected;
        } catch (Exception e) { return e.getClass().getSimpleName() + ": " + e.getMessage(); }
    }

    @Override public int connectedBluetooth(String[] addresses) {
        if (addresses == null || addresses.length == 0) return 0;
        long identity = Binder.clearCallingIdentity();
        try {
            // Hidden API is allowed in a Shizuku UserService. This checks ACL state,
            // including already-connected watches, cars and BLE devices at startup.
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            if (adapter == null) return 0;
            int state = adapter.getState();
            if (state == BluetoothAdapter.STATE_OFF) return 0;
            if (state != BluetoothAdapter.STATE_ON) return -1;
            var method = BluetoothDevice.class.getMethod("isConnected");
            for (String address : addresses) {
                if (!BluetoothAdapter.checkBluetoothAddress(address)) return -1;
                if (Boolean.TRUE.equals(method.invoke(adapter.getRemoteDevice(address)))) return 1;
            }
            return 0;
        } catch (Exception | LinkageError e) { return -1; }
        finally { Binder.restoreCallingIdentity(identity); }
    }

    @Override public void destroy() { System.exit(0); }
}
