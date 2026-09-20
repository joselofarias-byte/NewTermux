package com.newtermux.features;

import org.junit.Assert;
import org.junit.Test;

public class SessionStatusTextTest {

    @Test
    public void disconnectedHidesSessionDetails() {
        Assert.assertEquals("Service disconnected",
            SessionStatusText.format(false, true, true, true, true));
        Assert.assertTrue(SessionStatusText.shouldOfferReconnect(false));
        Assert.assertFalse(SessionStatusText.shouldOfferRestart(false, true, false));
    }

    @Test
    public void connectedRunningSession() {
        Assert.assertEquals("Service connected · session running",
            SessionStatusText.format(true, true, true, false, false));
        Assert.assertFalse(SessionStatusText.shouldOfferRestart(true, true, true));
        Assert.assertFalse(SessionStatusText.shouldOfferReconnect(true));
    }

    @Test
    public void exitedFailsafeWithWakeLock() {
        Assert.assertEquals("Service connected · session exited · failsafe · wake lock",
            SessionStatusText.format(true, true, false, true, true));
        Assert.assertTrue(SessionStatusText.shouldOfferRestart(true, true, false));
    }

    @Test
    public void connectedWithNoSession() {
        Assert.assertEquals("Service connected · no session",
            SessionStatusText.format(true, false, false, false, false));
        Assert.assertFalse(SessionStatusText.shouldOfferRestart(true, false, false));
    }
}
