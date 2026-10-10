package dev.pocketflight;

import org.junit.Test;
import static org.junit.Assert.*;
import static dev.pocketflight.FlightPolicy.*;

public class FlightPolicyTest {
    private State ready() { return new State(true, true, true, true, 5000, 5000, false, false, true, false, ExceptionState.CLEAR); }
    @Test public void coveredLockedScreenOffEntersAfterDelay() { assertEquals(Action.ENABLE, decide(ready())); }
    @Test public void briefCoverageDoesNotTrigger() {
        assertEquals(Action.WAIT, decide(new State(true,true,true,true,4999,5000,false,false,true,false,ExceptionState.CLEAR)));
    }
    @Test public void screenOffWithoutLockDoesNotTrigger() {
        assertEquals(Action.WAIT, decide(new State(true,false,true,true,6000,5000,false,false,true,false,ExceptionState.CLEAR)));
    }
    @Test public void lockScreenWithDisplayOnDoesNotTrigger() {
        assertEquals(Action.WAIT, decide(new State(true,true,false,true,6000,5000,false,false,true,false,ExceptionState.CLEAR)));
    }
    @Test public void clearSensorDoesNotTrigger() {
        assertEquals(Action.WAIT, decide(new State(true,true,true,false,6000,5000,false,false,true,false,ExceptionState.CLEAR)));
    }
    @Test public void wifiOrBluetoothConnectedBlocksEntry() {
        for (ExceptionState other : ExceptionState.values()) {
            assertEquals(ExceptionState.CONNECTED, combine(ExceptionState.CONNECTED, other));
            assertEquals(ExceptionState.CONNECTED, combine(other, ExceptionState.CONNECTED));
        }
        assertEquals(Action.WAIT, decide(new State(true,true,true,true,6000,5000,false,false,true,false,ExceptionState.CONNECTED)));
    }
    @Test public void permissionLossOrUnknownExceptionBlocksEntry() {
        assertEquals(ExceptionState.UNKNOWN, combine(ExceptionState.CLEAR, ExceptionState.UNKNOWN));
        assertEquals(ExceptionState.UNKNOWN, combine(ExceptionState.UNKNOWN, ExceptionState.CLEAR));
        assertEquals(Action.WAIT, decide(new State(true,true,true,true,6000,5000,false,false,true,false,ExceptionState.UNKNOWN)));
    }
    @Test public void unlockRestoresEvenIfExceptionsUnreadable() {
        assertEquals(Action.RESTORE, decide(new State(true,false,false,false,0,5000,true,true,true,false,ExceptionState.UNKNOWN)));
    }
    @Test public void pauseWhileLockedRestores() {
        assertEquals(Action.RESTORE, decide(new State(false,true,true,true,6000,5000,true,true,true,false,ExceptionState.CONNECTED)));
    }
    @Test public void manualAirplaneModeSurvivesUnlock() {
        assertEquals(Action.WAIT, decide(new State(true,false,false,false,0,5000,true,false,true,false,ExceptionState.CLEAR)));
    }
    @Test public void takingOutOfPocketWhileLockedWaitsForUnlock() {
        assertEquals(Action.WAIT, decide(new State(true,true,true,false,0,5000,true,true,true,false,ExceptionState.CLEAR)));
    }
    @Test public void manualOffSuppressesReentryUntilUnlock() {
        assertEquals(Action.WAIT, decide(new State(true,true,true,true,6000,5000,false,false,true,true,ExceptionState.CLEAR)));
    }
    @Test public void missingBridgeNeverClaimsToRestore() {
        assertEquals(Action.WAIT, decide(new State(true,false,false,false,0,5000,true,true,false,false,ExceptionState.CLEAR)));
    }
    @Test public void missingBridgeBlocksEntry() {
        assertEquals(Action.WAIT, decide(new State(true,true,true,true,6000,5000,false,false,false,false,ExceptionState.CLEAR)));
    }
    @Test public void pausedMonitorNeverEnables() {
        assertEquals(Action.WAIT, decide(new State(false,true,true,true,6000,5000,false,false,true,false,ExceptionState.CLEAR)));
    }
}
