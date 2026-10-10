package dev.pocketflight;

import org.junit.Test;
import static org.junit.Assert.*;

public class PocketReportTest {
    @Test public void reportSurvivesUnlockAndIncludesLatestReadings() {
        PocketReport r = new PocketReport(); r.begin(1000, "Selected sensors");
        r.sample(1000, "5 cm", "Locked", false);
        assertFalse(r.sample(1500, "5 cm", "Locked", false));
        assertTrue(r.sample(1600, "5 cm", "Unlocked", true));
        r.end(); assertFalse(r.active()); assertTrue(r.text().contains("Unlocked"));
        assertTrue(r.text().contains("Selected sensors"));
    }
    @Test public void longSessionKeepsBoundedRecentSamplesAndInventory() {
        PocketReport r = new PocketReport(); r.begin(0, "Selected sensors");
        for (int i = 0; i < 1000; i++) r.sample(i * 2000L, "reading", "sample " + i, false);
        assertTrue(r.text().contains("Selected sensors")); assertTrue(r.text().contains("sample 999"));
        assertFalse(r.text().contains("sample 100\n")); assertTrue(r.text().length() < 5000);
    }
}
