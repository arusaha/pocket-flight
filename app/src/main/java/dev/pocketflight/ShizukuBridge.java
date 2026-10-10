package dev.pocketflight;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.IBinder;
import rikka.shizuku.Shizuku;

public final class ShizukuBridge implements AutoCloseable {
    private volatile IFlightBridge remote;
    private boolean binding;
    private boolean closed;
    private final Runnable changed;
    private final Shizuku.UserServiceArgs args;
    private final Shizuku.OnBinderReceivedListener received = this::connect;
    private final Shizuku.OnBinderDeadListener died = () -> {
        remote = null; binding = false; changed();
    };
    private final Shizuku.OnRequestPermissionResultListener permission = (code, result) -> connect();
    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            if (closed) return;
            remote = IFlightBridge.Stub.asInterface(binder); binding = false; changed();
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            remote = null; binding = false; changed();
        }
    };

    public ShizukuBridge(Context context, Runnable changed) {
        this.changed = changed;
        args = new Shizuku.UserServiceArgs(new ComponentName(context, FlightBridgeService.class))
                .daemon(false).processNameSuffix("flight_bridge").debuggable(BuildConfig.DEBUG)
                .version(BuildConfig.VERSION_CODE);
        Shizuku.addBinderReceivedListenerSticky(received);
        Shizuku.addBinderDeadListener(died);
        Shizuku.addRequestPermissionResultListener(permission);
        connect();
    }
    private void changed() { if (!closed) changed.run(); }
    public static boolean authorized() {
        try { return Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED; }
        catch (RuntimeException e) { return false; }
    }
    public void connect() {
        if (closed || ready() || binding || !authorized()) return;
        try { binding = true; Shizuku.bindUserService(args, connection); }
        catch (RuntimeException e) { binding = false; }
    }
    public boolean ready() { return remote != null && remote.asBinder().isBinderAlive() && authorized(); }
    public IFlightBridge remote() { return remote; }
    @Override public void close() {
        closed = true;
        Shizuku.removeBinderReceivedListener(received);
        Shizuku.removeBinderDeadListener(died);
        Shizuku.removeRequestPermissionResultListener(permission);
        try { if (authorized()) Shizuku.unbindUserService(args, connection, false); }
        catch (RuntimeException ignored) { }
        remote = null;
    }
}
