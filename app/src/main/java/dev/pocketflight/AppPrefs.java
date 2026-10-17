package dev.pocketflight;

import android.content.Context;
import android.content.SharedPreferences;
import java.text.DateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

public final class AppPrefs {
    public final SharedPreferences data;
    public AppPrefs(Context context) { data = context.getSharedPreferences("pocket_flight", Context.MODE_PRIVATE); }
    public boolean enabled() { return data.getBoolean("enabled", false); }
    public void enabled(boolean value) { data.edit().putBoolean("enabled", value).commit(); }
    public boolean owned() { return data.getBoolean("owned", false); }
    // Synchronous journal before a radio-changing command, including process-death recovery.
    public boolean owned(boolean value) { return data.edit().putBoolean("owned", value).commit(); }
    public long delayMillis() { return data.getInt("delay_seconds", 5) * 1000L; }
    public boolean pocketAssist() { return data.getBoolean("pocket_assist", false); }
    public boolean restoreOnExit() { return data.getBoolean("restore_on_exit", true); }
    public boolean hybridExit() { return data.getBoolean("hybrid_exit", true); }
    public int exitMinutes() { return Math.max(1, Math.min(30, data.getInt("exit_minutes", 2))); }
    public long exitMillis() { return exitMinutes() * 60000L; }
    public float darkLux() { return Math.max(1, Math.min(50, data.getInt("dark_lux", 10))); }
    public Set<String> wifi() { return new HashSet<>(data.getStringSet("wifi", Set.of())); }
    public Set<String> bluetooth() { return new HashSet<>(data.getStringSet("bluetooth", Set.of())); }
    public void wifi(Set<String> values) { data.edit().putStringSet("wifi", new HashSet<>(values)).commit(); }
    public void bluetooth(Set<String> values) { data.edit().putStringSet("bluetooth", new HashSet<>(values)).commit(); }
    public void event(String message) {
        String line = DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date()) + "  " + message;
        String history = line + "\n" + data.getString("history", "");
        String[] lines = history.split("\n");
        StringBuilder kept = new StringBuilder();
        for (int i = 0; i < Math.min(30, lines.length); i++) kept.append(lines[i]).append('\n');
        data.edit().putString("history", kept.toString()).apply();
    }
}
