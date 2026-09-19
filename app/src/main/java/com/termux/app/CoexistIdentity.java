package com.termux.app;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.TermuxConstants;

/**
 * Guards package / PREFIX identity so a coexist debug build cannot read or
 * delete Termux Play ({@code com.termux}) private data.
 */
public final class CoexistIdentity {

    public static final String TERMUX_PLAY_PACKAGE = "com.termux";
    public static final String DEBUG_COEXIST_PACKAGE = "com.newtermux.dev";
    public static final String DEMO_PACKAGE = "com.termux.demo";

    private CoexistIdentity() {}

    public static boolean matchesBuildConstants(@NonNull Context context) {
        return context.getPackageName().equals(TermuxConstants.TERMUX_PACKAGE_NAME);
    }

    public static boolean prefixMatchesPackage(@NonNull Context context) {
        return TermuxConstants.TERMUX_INTERNAL_PRIVATE_APP_DATA_DIR_PATH
            .equals("/data/data/" + context.getPackageName());
    }

    /**
     * True when this process is not Termux Play but {@link TermuxConstants}
     * still points at Play's {@code /data/data/com.termux/} tree.
     */
    public static boolean wouldTouchPlayData(@NonNull Context context) {
        if (TERMUX_PLAY_PACKAGE.equals(context.getPackageName())) {
            return false;
        }
        return TermuxConstants.TERMUX_PREFIX_DIR_PATH.startsWith("/data/data/" + TERMUX_PLAY_PACKAGE + "/");
    }

    @Nullable
    public static String mismatchReason(@NonNull Context context) {
        if (!matchesBuildConstants(context)) {
            return "applicationId " + context.getPackageName()
                + " != TermuxConstants.TERMUX_PACKAGE_NAME "
                + TermuxConstants.TERMUX_PACKAGE_NAME;
        }
        if (!prefixMatchesPackage(context)) {
            return "PREFIX " + TermuxConstants.TERMUX_PREFIX_DIR_PATH
                + " does not belong to " + context.getPackageName();
        }
        if (wouldTouchPlayData(context)) {
            return "refusing to use Termux Play PREFIX while running as "
                + context.getPackageName();
        }
        return null;
    }
}
