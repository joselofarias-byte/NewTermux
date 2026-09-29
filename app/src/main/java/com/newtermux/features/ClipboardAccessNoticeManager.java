package com.newtermux.features;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;

import com.termux.shared.logger.Logger;

/**
 * Applies NewTermux's preferred clipboard access notification policy.
 *
 * Android 12+ can show a transient overlay whenever an app reads the clipboard.
 * On small screens that overlay can cover NewTermux's compact extra-key controls.
 *
 * The secure setting is global for the current Android user. Clipboard reads and
 * paste remain enabled; only the system access notification is disabled.
 */
public final class ClipboardAccessNoticeManager {

    private static final String LOG_TAG = "ClipboardAccessNotice";
    private static final String SETTING = "clipboard_show_access_notifications";

    private ClipboardAccessNoticeManager() {}

    /**
     * Best-effort and idempotent. This method must never break app startup.
     *
     * @return true if the desired value is already active or was applied.
     */
    public static boolean apply(Context context) {
        if (context == null) return false;

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return true;
        }

        try {
            int current = Settings.Secure.getInt(
                context.getContentResolver(), SETTING, 1);
            if (current == 0) {
                Logger.logDebug(LOG_TAG, "Clipboard access notification already disabled");
                return true;
            }

            if (context.checkCallingOrSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS)
                    != PackageManager.PERMISSION_GRANTED) {
                Logger.logWarn(LOG_TAG,
                    "WRITE_SECURE_SETTINGS not granted; clipboard access notification unchanged");
                return false;
            }

            if (!Settings.Secure.putInt(context.getContentResolver(), SETTING, 0)) {
                Logger.logWarn(LOG_TAG, "Settings.Secure.putInt returned false");
                return false;
            }

            int verified = Settings.Secure.getInt(
                context.getContentResolver(), SETTING, 1);
            if (verified != 0) {
                Logger.logWarn(LOG_TAG,
                    "Clipboard access notification setting did not persist");
                return false;
            }

            Logger.logInfo(LOG_TAG, "Clipboard access notification disabled");
            return true;
        } catch (SecurityException e) {
            Logger.logWarn(LOG_TAG,
                "Permission denied while disabling clipboard access notification: " + e.getMessage());
            return false;
        } catch (Exception e) {
            Logger.logWarn(LOG_TAG,
                "Could not apply clipboard access notification setting: " + e.getMessage());
            return false;
        }
    }
}
