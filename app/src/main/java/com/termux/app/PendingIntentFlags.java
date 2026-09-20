package com.termux.app;

import android.app.PendingIntent;
import android.os.Build;

/**
 * PendingIntent flag helper for notification actions owned by this app.
 *
 * Reimplemented from Android 12+ guidance (and the same FLAG_IMMUTABLE pattern
 * used by Acode's TerminalService). Not copied from Acode sources.
 *
 * minSdk is 21; FLAG_IMMUTABLE exists from API 23.
 */
public final class PendingIntentFlags {

    private PendingIntentFlags() {}

    /**
     * {@link PendingIntent#FLAG_UPDATE_CURRENT} plus {@link PendingIntent#FLAG_IMMUTABLE}
     * when the platform supports it.
     */
    public static int immutableUpdateCurrent() {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return flags;
    }
}
