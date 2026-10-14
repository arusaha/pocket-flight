package dev.pocketflight;

/** Requires continuous positive outside evidence; an unobserved gap starts a fresh interval. */
public final class PocketExitTimer {
    private static final long MAX_OBSERVATION_GAP_MS = 10000;
    private long startedAt = -1, observedAt = -1;

    public void observe(long now, boolean armed, boolean outside) {
        if (!armed || !outside) { reset(); return; }
        if (observedAt < 0 || now < observedAt || now - observedAt > MAX_OBSERVATION_GAP_MS) startedAt = now;
        observedAt = now;
    }
    public void reset() { startedAt = observedAt = -1; }
    public long startedAt() { return startedAt; }
    public boolean running() { return startedAt >= 0; }
    public long remaining(long now, long delayMillis) {
        return Math.max(0, delayMillis - (running() ? Math.max(0, now - startedAt) : 0));
    }
    public boolean due(long now, long delayMillis) {
        return running() && now >= observedAt && now - observedAt <= MAX_OBSERVATION_GAP_MS
                && remaining(now, delayMillis) == 0;
    }
}
