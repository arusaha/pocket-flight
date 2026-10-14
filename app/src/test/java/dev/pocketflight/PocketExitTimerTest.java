package dev.pocketflight;

import org.junit.Test;
import static org.junit.Assert.*;

public class PocketExitTimerTest {
    private void observeThrough(PocketExitTimer timer, long from, long to) {
        for (long now = from; now <= to; now += 2000) timer.observe(now, true, true);
    }
    @Test public void fullTwoMinutesRequired() {
        PocketExitTimer timer = new PocketExitTimer(); observeThrough(timer, 0, 118000);
        timer.observe(119999, true, true); assertFalse(timer.due(119999, 120000));
        timer.observe(120000, true, true); assertTrue(timer.due(120000, 120000));
    }
    @Test public void returningToPocketOrUnknownReadingsRestartsWholeInterval() {
        PocketExitTimer timer = new PocketExitTimer(); observeThrough(timer, 0, 118000);
        timer.observe(119000, true, false); assertFalse(timer.running());
        observeThrough(timer, 120000, 238000); assertFalse(timer.due(238000, 120000));
        timer.observe(240000, true, true); assertTrue(timer.due(240000, 120000));
    }
    @Test public void unownedDisabledOrOfflineMonitoringEndedDisarmsTimer() {
        PocketExitTimer timer = new PocketExitTimer(); observeThrough(timer, 0, 120000);
        timer.observe(120000, false, true); assertFalse(timer.running()); assertFalse(timer.due(120000, 120000));
    }
    @Test public void cpuSleepOrClockRollbackCannotCountAsObservedOutsideTime() {
        PocketExitTimer timer = new PocketExitTimer(); timer.observe(0, true, true);
        timer.observe(120000, true, true); assertEquals(120000, timer.remaining(120000, 120000));
        timer.observe(1000, true, true); assertEquals(120000, timer.remaining(1000, 120000));
    }
    @Test public void delayIsConfigurableAndResetNeedsFreshFullInterval() {
        PocketExitTimer timer = new PocketExitTimer(); observeThrough(timer, 0, 60000);
        assertTrue(timer.due(60000, 60000)); assertFalse(timer.due(60000, 180000));
        timer.reset(); timer.observe(60000, true, true); assertEquals(180000, timer.remaining(60000, 180000));
    }
}
