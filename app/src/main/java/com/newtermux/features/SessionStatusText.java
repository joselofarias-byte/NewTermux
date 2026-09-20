package com.newtermux.features;

/**
 * Pure-Java formatter for the session/service status strip.
 * Kept free of Android types so unit tests can cover the copy without a device.
 */
public final class SessionStatusText {

    private SessionStatusText() {}

    /**
     * Build a compact status line for the toolbar.
     *
     * @param serviceConnected {@code true} if {@code TermuxService} is bound
     * @param sessionPresent   {@code true} if a terminal session is attached
     * @param sessionRunning   {@code true} if that session's process is alive
     * @param failsafe         {@code true} if the session is a failsafe shell
     * @param wakeLockHeld     {@code true} if the service holds a wake lock
     */
    public static String format(boolean serviceConnected,
                                boolean sessionPresent,
                                boolean sessionRunning,
                                boolean failsafe,
                                boolean wakeLockHeld) {
        if (!serviceConnected) {
            return "Service disconnected";
        }

        StringBuilder status = new StringBuilder("Service connected");
        if (!sessionPresent) {
            status.append(" · no session");
        } else if (sessionRunning) {
            status.append(" · session running");
        } else {
            status.append(" · session exited");
        }
        if (failsafe) {
            status.append(" · failsafe");
        }
        if (wakeLockHeld) {
            status.append(" · wake lock");
        }
        return status.toString();
    }

    /** Whether Restart should be emphasized (session missing or process dead). */
    public static boolean shouldOfferRestart(boolean serviceConnected, boolean sessionPresent, boolean sessionRunning) {
        return serviceConnected && sessionPresent && !sessionRunning;
    }

    /** Whether Reconnect should be emphasized (UI lost the service bind). */
    public static boolean shouldOfferReconnect(boolean serviceConnected) {
        return !serviceConnected;
    }
}
