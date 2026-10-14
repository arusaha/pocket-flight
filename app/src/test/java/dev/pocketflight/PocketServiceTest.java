package dev.pocketflight;

import android.app.KeyguardManager;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
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
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowSensor;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, shadows = PocketServiceTest.TestBridge.class, instrumentedPackages = "dev.pocketflight")
public class PocketServiceTest {
    private ServiceController<PocketService> controller;
    private PocketService service;
    private AppPrefs prefs;
    private Sensor proximity;
    private final QueuedWorker worker = new QueuedWorker();
    private final List<Boolean> commands = new ArrayList<>();

    @Implements(value = ShizukuBridge.class, isInAndroidSdk = false)
    public static class TestBridge {
        static boolean available;
        static IFlightBridge remote;
        @Implementation protected void __constructor__(Context context, Runnable changed) {}
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
        controller = Robolectric.buildService(PocketService.class).create(); service = controller.get();
        ExecutorService original = ReflectionHelpers.getField(service, "worker"); original.shutdown();
        ReflectionHelpers.setField(service, "worker", worker);
        prefs = new AppPrefs(service);
        Shadows.shadowOf(service.getSystemService(KeyguardManager.class)).setIsDeviceLocked(true);
        Shadows.shadowOf(service.getSystemService(PowerManager.class)).setIsInteractive(false);
        proximity = ShadowSensor.newInstance(Sensor.TYPE_PROXIMITY);
        ReflectionHelpers.setField(proximity, "mMaxRange", 5f);
        ReflectionHelpers.setField(proximity, "mFlags", 1); // Wake-up on-change proximity.
        ReflectionHelpers.setField(service, "proximity", proximity);
        ReflectionHelpers.setField(service, "sensorRegistered", true);
        Settings.Global.putInt(service.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 1);
        prefs.owned(true); prefs.enabled(true);
        TestBridge.remote = new IFlightBridge.Stub() {
            @Override public String setAirplaneMode(boolean enabled) {
                commands.add(enabled);
                Settings.Global.putInt(service.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, enabled ? 1 : 0);
                return "OK";
            }
            @Override public int connectedBluetooth(String[] addresses) { return 0; }
            @Override public String probe() { return PocketService.airplane(service) ? "OK: enabled" : "OK: disabled"; }
            @Override public void destroy() {}
        };
        sample(0);
    }
    @After public void close() { controller.destroy(); TestBridge.remote = null; }
    private void sample(float distance) {
        SensorEvent event = ReflectionHelpers.callConstructor(SensorEvent.class, ReflectionHelpers.ClassParameter.from(int.class, 3));
        event.sensor = proximity; event.values[0] = distance; event.timestamp = SystemClock.elapsedRealtimeNanos();
        service.onSensorChanged(event);
    }
    private void advance(long seconds) { Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(seconds)); }
    private void finishCommand() { worker.runNext(); Shadows.shadowOf(Looper.getMainLooper()).idle(); }
    private PowerManager.WakeLock monitoringWake() { return ReflectionHelpers.getField(service, "fallbackWake"); }

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
}
