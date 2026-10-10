package dev.pocketflight;

/** Sensor interpretation, independent of Android so timing and fallback rules can be tested. */
public final class PocketDetector {
    public static final long FLICKER_GRACE_MS = 800;
    private float proximity = Float.NaN, range = Float.NaN, lux = Float.NaN, verticalZ = Float.NaN;
    private long orientationAt = -1, startedAt = -1, clearAt = -1;
    private String reason = "Waiting for sensor readings";

    public void reset() {
        proximity = range = lux = verticalZ = Float.NaN;
        orientationAt = startedAt = clearAt = -1;
        reason = "Waiting for fresh sensor readings";
    }
    public void proximity(float value, float maximum) { proximity = value; range = maximum; }
    public void light(float value) { lux = value; }
    public void orientation(float x, float y, float z, long now) {
        double magnitude = Math.sqrt(x * x + y * y + z * z);
        // Reject free-fall, violent motion and invalid samples.
        verticalZ = magnitude >= 7 && magnitude <= 12.5 ? (float) (Math.abs(z) / magnitude) : Float.NaN;
        orientationAt = now;
    }
    public boolean near() {
        return Float.isFinite(proximity) && proximity >= 0 && Float.isFinite(range) && range > 0
                && proximity < Math.min(range, 5f);
    }
    public boolean candidate(long now, boolean assist, float darkLux) {
        boolean dark = Float.isFinite(lux) && lux >= 0 && lux <= darkLux;
        boolean tilted = Float.isFinite(verticalZ) && verticalZ < 0.82f
                && orientationAt >= 0 && now >= orientationAt && now - orientationAt <= 2500;
        boolean detected = near() || (assist && dark && tilted);
        if (near()) reason = "Proximity covered";
        else if (!assist) reason = Float.isNaN(proximity) ? "Waiting for proximity reading" : "Proximity clear";
        else if (!Float.isFinite(lux) || lux < 0) reason = "Waiting for light reading";
        else if (!dark) reason = "Too bright for pocket assist";
        else if (!tilted) reason = "Dark; waiting for a tilted phone / fresh orientation";
        else reason = "Pocket assist: dark + tilted";

        if (detected) {
            if (startedAt < 0 || (clearAt >= 0 && now - clearAt > FLICKER_GRACE_MS)) startedAt = now;
            else if (clearAt >= 0) startedAt += now - clearAt; // Gaps never count toward the confirmation delay.
            clearAt = -1;
        } else if (startedAt >= 0) {
            if (clearAt < 0) clearAt = now;
            if (now - clearAt > FLICKER_GRACE_MS) startedAt = -1;
        }
        return detected;
    }
    public long duration(long now) { return startedAt < 0 || clearAt >= 0 ? 0 : Math.max(0, now - startedAt); }
    public void resetTimer() { startedAt = clearAt = -1; }
    public String reason() { return reason; }
    public String readings() {
        return "Proximity: " + number(proximity) + " / " + number(range) + " cm"
                + " · light: " + number(lux) + " lux · tilt: "
                + (Float.isFinite(verticalZ) ? Math.round(Math.toDegrees(Math.acos(Math.min(1, verticalZ)))) + "°" : "waiting");
    }
    private static String number(float value) { return Float.isFinite(value) ? String.format(java.util.Locale.US, "%.1f", value) : "waiting"; }
}
