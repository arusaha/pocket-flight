package dev.pocketflight;

import org.junit.Test;
import static org.junit.Assert.*;

public class PocketDetectorTest {
    private PocketDetector detector() { PocketDetector d = new PocketDetector(); d.proximity(5, 5); return d; }
    @Test public void handCoverageStillWorksWithoutAssist() {
        PocketDetector d = detector(); d.proximity(0, 5);
        assertTrue(d.candidate(0, false, 10));
        assertTrue(d.candidate(5000, false, 10)); assertEquals(5000, d.duration(5000));
    }
    @Test public void pocketCanTriggerDespiteClearProximity() {
        PocketDetector d = detector(); d.light(2); d.orientation(0, 9.8f, 0, 0);
        assertTrue(d.candidate(0, true, 10));
        d.orientation(0, 9.8f, 0, 5000);
        assertTrue(d.candidate(5000, true, 10)); assertEquals(5000, d.duration(5000));
        assertEquals("Pocket assist: dark + tilted", d.reason());
    }
    @Test public void assistIsOptIn() {
        PocketDetector d = detector(); d.light(0); d.orientation(0, 9.8f, 0, 0);
        assertFalse(d.candidate(0, false, 10));
    }
    @Test public void darknessAloneOnFlatTableNeverTriggersAssist() {
        for (float z : new float[]{9.8f, -9.8f}) {
            PocketDetector d = detector(); d.light(0); d.orientation(0, 0, z, 0);
            assertFalse(d.candidate(0, true, 10));
        }
    }
    @Test public void uprightPhoneInLightDoesNotTrigger() {
        PocketDetector d = detector(); d.light(20); d.orientation(0, 9.8f, 0, 0);
        assertFalse(d.candidate(0, true, 10));
    }
    @Test public void missingReadingsCannotTriggerFallback() {
        PocketDetector d = detector(); assertFalse(d.candidate(0, true, 10));
        d.light(0); assertFalse(d.candidate(0, true, 10));
        d.reset(); d.orientation(0, 9.8f, 0, 0); assertFalse(d.candidate(0, true, 10));
    }
    @Test public void staleOrientationCannotTrigger() {
        PocketDetector d = detector(); d.light(0); d.orientation(0, 9.8f, 0, 0);
        assertFalse(d.candidate(2501, true, 10));
    }
    @Test public void unchangedOnChangeProximityReadingRemainsValid() {
        PocketDetector d = detector(); d.proximity(0, 5);
        assertTrue(d.candidate(0, false, 10)); assertTrue(d.candidate(60000, false, 10));
    }
    @Test public void briefFlickerPausesButDoesNotEraseProgress() {
        PocketDetector d = detector(); d.proximity(0, 5); d.candidate(0, false, 10);
        d.proximity(5, 5); assertFalse(d.candidate(4000, false, 10)); assertEquals(0, d.duration(4000));
        d.proximity(0, 5); assertTrue(d.candidate(4500, false, 10));
        assertEquals(4000, d.duration(4500)); assertEquals(5000, d.duration(5500));
    }
    @Test public void longClearGapRestartsConfirmation() {
        PocketDetector d = detector(); d.proximity(0, 5); d.candidate(0, false, 10);
        d.proximity(5, 5); d.candidate(4000, false, 10);
        d.proximity(0, 5); d.candidate(4900, false, 10); assertEquals(0, d.duration(4900));
    }
    @Test public void resetDiscardsReadingsFromPreviousDisplaySession() {
        PocketDetector d = detector(); d.proximity(0, 5); d.light(0); d.orientation(0, 9.8f, 0, 0);
        d.candidate(0, true, 10); d.reset(); assertFalse(d.candidate(5000, true, 10)); assertEquals(0, d.duration(5000));
    }
    @Test public void timerResetPreventsScreenOnTimeCountingTowardLockDelay() {
        PocketDetector d = detector(); d.proximity(0, 5); d.candidate(0, false, 10); d.resetTimer();
        d.candidate(10000, false, 10); assertEquals(0, d.duration(10000));
    }
    @Test public void invalidValuesDoNotTrigger() {
        PocketDetector d = detector(); d.proximity(-1, 5); assertFalse(d.candidate(0, false, 10));
        d.proximity(Float.NaN, 5); d.light(-1); d.orientation(0, 9.8f, 0, 0);
        assertFalse(d.candidate(0, true, 10));
        d.light(0); d.orientation(0, 0, 0, 0); assertFalse(d.candidate(0, true, 10));
    }
    @Test public void configuredLightThresholdIsRespected() {
        PocketDetector d = detector(); d.light(15); d.orientation(0, 9.8f, 0, 0);
        assertFalse(d.candidate(0, true, 10)); assertTrue(d.candidate(0, true, 20));
    }
    @Test public void outsideNeedsValidFarProximityWhenAvailable() {
        PocketDetector d = new PocketDetector();
        assertFalse(d.outside(0, true, false, 10));
        d.proximity(-1, 5); assertFalse(d.outside(0, true, false, 10));
        d.proximity(5, 0); assertFalse(d.outside(0, true, false, 10));
        d.proximity(0, 5); assertFalse(d.outside(0, true, false, 10));
        d.proximity(5, 5); assertTrue(d.outside(0, true, false, 10));
        assertTrue(d.outside(120000, true, false, 10)); // On-change readings remain valid.
        assertFalse(d.outside(120000, false, false, 10));
    }
    @Test public void stalePocketAssistIsNotEvidenceOfLeavingPocket() {
        PocketDetector d = detector(); d.light(0); d.orientation(0, 9.8f, 0, 0);
        assertFalse(d.outside(0, true, true, 10));
        assertFalse(d.candidate(3000, true, 10));
        assertFalse(d.outside(3000, true, true, 10));
        d.reset(); assertFalse(d.outside(3000, true, true, 10));
    }
    @Test public void clearProximityPlusLightOrFreshFlatnessConfirmsExit() {
        PocketDetector d = detector(); d.light(20);
        assertTrue(d.outside(0, true, true, 10));
        d.proximity(0, 5); assertFalse(d.outside(0, true, true, 10));
        d.proximity(5, 5); d.light(0); d.orientation(0, 0, 9.8f, 0);
        assertTrue(d.outside(0, true, true, 10));
        assertFalse(d.outside(2501, true, true, 10));
    }
    @Test public void assistOnlyPhonesCanConfirmExitWithoutProximity() {
        PocketDetector d = new PocketDetector(); d.light(20);
        assertTrue(d.outside(0, false, true, 10));
        assertFalse(d.outside(0, true, true, 10)); // A registered but unread proximity sensor is unknown.
    }
    @Test public void fallbackStillNeedsScreenOffAndLockAndClearExceptions() {
        PocketDetector d = detector(); d.light(0); d.orientation(0, 9.8f, 0, 0); d.candidate(0, true, 10);
        d.orientation(0, 9.8f, 0, 5000); boolean candidate = d.candidate(5000, true, 10);
        for (boolean locked : new boolean[]{false, true}) {
            for (boolean screenOff : new boolean[]{false, true}) {
                for (FlightPolicy.ExceptionState exception : FlightPolicy.ExceptionState.values()) {
                    var state = new FlightPolicy.State(true, locked, screenOff, candidate, d.duration(5000), 5000,
                            false, false, true, false, exception);
                    boolean allowed = locked && screenOff && exception == FlightPolicy.ExceptionState.CLEAR;
                    assertEquals(allowed ? FlightPolicy.Action.ENABLE : FlightPolicy.Action.WAIT, FlightPolicy.decide(state));
                }
            }
        }
    }
}
