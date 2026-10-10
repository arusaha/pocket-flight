package dev.pocketflight;

/** Pure policy. UNKNOWN exceptions always prevent entry; restoration has priority. */
public final class FlightPolicy {
    public enum ExceptionState { CLEAR, CONNECTED, UNKNOWN }
    public enum Action { WAIT, ENABLE, RESTORE }
    public record State(boolean enabled, boolean locked, boolean screenOff,
                        boolean covered, long coveredMillis, long delayMillis,
                        boolean airplaneOn, boolean owned, boolean bridgeReady,
                        boolean suppressed, ExceptionState exceptionState) {}

    public static Action decide(State s) {
        if (s.owned && (!s.enabled || !s.locked)) {
            return s.airplaneOn && s.bridgeReady ? Action.RESTORE : Action.WAIT;
        }
        if (!s.enabled || !s.locked || !s.screenOff || !s.covered || s.suppressed
                || s.airplaneOn || s.owned || !s.bridgeReady
                || s.coveredMillis < s.delayMillis
                || s.exceptionState != ExceptionState.CLEAR) return Action.WAIT;
        return Action.ENABLE;
    }

    public static ExceptionState combine(ExceptionState wifi, ExceptionState bluetooth) {
        if (wifi == ExceptionState.CONNECTED || bluetooth == ExceptionState.CONNECTED)
            return ExceptionState.CONNECTED;
        if (wifi == ExceptionState.UNKNOWN || bluetooth == ExceptionState.UNKNOWN)
            return ExceptionState.UNKNOWN;
        return ExceptionState.CLEAR;
    }
}
