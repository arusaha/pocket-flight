package dev.pocketflight;

import org.junit.Test;
import static org.junit.Assert.*;

public class ConnectionsTest {
    @Test public void androidQuotedNameMatchesEnteredName() {
        assertEquals("Home Wi-Fi", Connections.normalizeSsid("\"Home Wi-Fi\""));
    }
    @Test public void unknownNameNeverBecomesAnExceptionMatch() {
        assertEquals("", Connections.normalizeSsid("<unknown ssid>"));
        assertEquals("", Connections.normalizeSsid(null));
    }
    @Test public void namesRetainCaseAndMeaningfulWhitespace() {
        assertEquals(" My Home ", Connections.normalizeSsid("\" My Home \""));
        assertNotEquals(Connections.normalizeSsid("home"), Connections.normalizeSsid("HOME"));
    }
    @Test public void hexadecimalNameIsPreserved() {
        assertEquals("0x1234", Connections.normalizeSsid("0x1234"));
    }
}
