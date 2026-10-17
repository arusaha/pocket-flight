package dev.pocketflight;

/** Presentation only; service errors and restoration remain visible on the minimal home screen. */
public record HomeStatus(String title, String detail, String button) {
    public static HomeStatus from(boolean enabled, boolean owned, boolean ready, boolean authorized, String serviceStatus) {
        String current = serviceStatus == null ? "" : serviceStatus;
        String button = enabled ? "Stop monitoring" : owned ? "Retry restoring connectivity" : "Start monitoring";
        if (owned && !ready) return new HomeStatus("Restore needed", "Start Shizuku, or turn off airplane mode in Quick Settings", button);
        if (enabled && owned && current.startsWith("Out of pocket · restoring in "))
            return new HomeStatus("Out of pocket", "Restoring connectivity in " + current.substring("Out of pocket · restoring in ".length()), button);
        if (owned) return new HomeStatus("Restoring connectivity", "Turning off airplane mode enabled by Pocket Flight", button);
        if (!enabled) return new HomeStatus("Paused", "Automation is off", button);
        if (!ready) return new HomeStatus(authorized ? "Connecting" : "Setup needed",
                authorized ? "Connecting to Shizuku" : "Open Settings → Shizuku to connect and grant access", button);
        if (current.startsWith("No usable") || current.startsWith("Action incomplete")
                || current.startsWith("Could not") || current.startsWith("Recovery pending"))
            return new HomeStatus("Needs attention", current, button);
        if (current.startsWith("Manual airplane mode")) return new HomeStatus("Manual airplane mode", "Your manual setting is left unchanged", button);
        if (current.startsWith("Standby · ")) return new HomeStatus("Standby",
                "Connected to a saved exception · pocket sensors paused", button);
        if (current.startsWith("Checking exceptions · ")) return new HomeStatus("Checking connections",
                current.substring("Checking exceptions · ".length()), button);
        return new HomeStatus("Monitoring", "Waiting for your phone to be locked and pocketed", button);
    }
}
