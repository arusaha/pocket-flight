package dev.pocketflight;

import org.junit.Test;
import static org.junit.Assert.*;

public class HomeStatusTest {
    @Test public void ordinaryMonitoringUsesApprovedCopy() {
        HomeStatus status = HomeStatus.from(true, false, true, true, "Ready · lock your phone and put it in your pocket");
        assertEquals("Monitoring", status.title());
        assertEquals("Waiting for your phone to be locked and pocketed", status.detail());
        assertEquals("Stop monitoring", status.button());
    }
    @Test public void stoppedStateDoesNotShowStaleServiceStatus() {
        assertEquals(new HomeStatus("Paused", "Automation is off", "Start monitoring"),
                HomeStatus.from(false, false, true, true, "Ready · lock your phone"));
    }
    @Test public void failedRestorationCannotLookPaused() {
        HomeStatus status = HomeStatus.from(false, true, false, false, "Restore pending");
        assertEquals("Restore needed", status.title());
        assertTrue(status.detail().contains("Quick Settings"));
        assertEquals("Retry restoring connectivity", status.button());
    }
    @Test public void ownedModeKeepsRecoveryVisibleAfterStopping() {
        assertEquals("Restoring connectivity", HomeStatus.from(false, true, true, true, "Paused").title());
    }
    @Test public void missingShizukuCannotLookReady() {
        assertEquals("Setup needed", HomeStatus.from(true, false, false, false, "Ready").title());
        assertEquals("Connecting", HomeStatus.from(true, false, false, true, "Ready").title());
    }
    @Test public void sensorAndCommandErrorsRemainVisible() {
        for (String error : new String[]{"No usable pocket sensors — automation paused", "Action incomplete: Shizuku connection lost", "Could not save recovery state · staying online"}) {
            HomeStatus status = HomeStatus.from(true, false, true, true, error);
            assertEquals("Needs attention", status.title()); assertEquals(error, status.detail());
        }
    }
    @Test public void manualAirplaneModeIsDescribedAccurately() {
        assertEquals("Manual airplane mode", HomeStatus.from(true, false, true, true, "Manual airplane mode · left unchanged").title());
    }
    @Test public void exitCountdownIsVisibleWithoutClaimingRestorationHasStarted() {
        assertEquals(new HomeStatus("Out of pocket", "Restoring connectivity in 1:42", "Stop monitoring"),
                HomeStatus.from(true, true, true, true, "Out of pocket · restoring in 1:42"));
        assertEquals("Restore needed", HomeStatus.from(true, true, false, false, "Out of pocket · restoring in 1:42").title());
    }
}
