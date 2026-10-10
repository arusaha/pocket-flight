package dev.pocketflight;

import java.util.ArrayDeque;
import java.util.Locale;

/** Bounded record of the most recent screen-off session, without network/device identifiers. */
public final class PocketReport {
    private final ArrayDeque<String> lines = new ArrayDeque<>();
    private long beganAt = -1, lastAt = -1;
    public boolean active() { return beganAt >= 0; }
    public void begin(long now, String sensors) {
        lines.clear(); beganAt = now; lastAt = -1;
        lines.add("Locked-session diagnostic\n" + sensors);
    }
    public boolean sample(long now, String readings, String state, boolean force) {
        if (!active() || (!force && lastAt >= 0 && now - lastAt < 2000)) return false;
        lastAt = now;
        lines.add(String.format(Locale.US, "+%.1fs  %s\n%s", (now - beganAt) / 1000d, state, readings));
        // Retain the sensor inventory and the most recent samples.
        if (lines.size() > 42) { String header = lines.removeFirst(); lines.removeFirst(); lines.addFirst(header); }
        return true;
    }
    public String text() { return String.join("\n\n", lines); }
    public void end() { beganAt = -1; }
}
