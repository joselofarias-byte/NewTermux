package com.termux.app.terminal.io;

import android.annotation.SuppressLint;
import android.view.Gravity;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.drawerlayout.widget.DrawerLayout;

import com.termux.app.TermuxActivity;
import com.termux.app.terminal.TermuxTerminalSessionActivityClient;
import com.termux.app.terminal.TermuxTerminalViewClient;
import com.termux.shared.logger.Logger;
import com.termux.shared.interact.ShareUtils;
import com.termux.shared.termux.extrakeys.ExtraKeyButton;
import com.termux.shared.termux.extrakeys.ExtraKeysConstants;
import com.termux.shared.termux.extrakeys.ExtraKeysInfo;
import com.termux.shared.termux.settings.properties.TermuxPropertyConstants;
import com.termux.shared.termux.settings.properties.TermuxSharedProperties;
import com.termux.shared.termux.terminal.io.TerminalExtraKeys;
import com.termux.view.TerminalView;

import org.json.JSONException;

public class TermuxTerminalExtraKeys extends TerminalExtraKeys {

    private ExtraKeysInfo mExtraKeysInfo;

    final TermuxActivity mActivity;
    final TermuxTerminalViewClient mTermuxTerminalViewClient;
    final TermuxTerminalSessionActivityClient mTermuxTerminalSessionActivityClient;

    private static final String LOG_TAG = "TermuxTerminalExtraKeys";

    private static final String[][] LEGACY_TBM_COMPACT_LAYOUT = new String[][] {
        {"ESC", "TAB", "UP", "DOWN", "ENTER"},
        {"HOME", "END", "LEFT", "RIGHT", "PASTE"}
    };

    private static final String[][] PREVIOUS_NEWTERMUX_COMPACT_LAYOUT = new String[][] {
        {"ESC", "TAB", "y", "n", "PASTE", "ENTER"},
        {"HOME", "END", "LEFT", "RIGHT", "UP", "DOWN"}
    };

    public TermuxTerminalExtraKeys(TermuxActivity activity, @NonNull TerminalView terminalView,
                                   TermuxTerminalViewClient termuxTerminalViewClient,
                                   TermuxTerminalSessionActivityClient termuxTerminalSessionActivityClient) {
        super(terminalView);

        mActivity = activity;
        mTermuxTerminalViewClient = termuxTerminalViewClient;
        mTermuxTerminalSessionActivityClient = termuxTerminalSessionActivityClient;

        setExtraKeys();
    }


    /**
     * Set the terminal extra keys and style.
     */
    private void setExtraKeys() {
        mExtraKeysInfo = null;

        try {
            // The mMap stores the extra key and style string values while loading properties
            // Check {@link #getExtraKeysInternalPropertyValueFromValue(String)} and
            // {@link #getExtraKeysStyleInternalPropertyValueFromValue(String)}
            String extrakeys = (String) mActivity.getProperties().getInternalPropertyValue(TermuxPropertyConstants.KEY_EXTRA_KEYS, true);
            String extraKeysStyle = (String) mActivity.getProperties().getInternalPropertyValue(TermuxPropertyConstants.KEY_EXTRA_KEYS_STYLE, true);

            ExtraKeysConstants.ExtraKeyDisplayMap extraKeyDisplayMap = ExtraKeysInfo.getCharDisplayMapForStyle(extraKeysStyle);
            if (ExtraKeysConstants.EXTRA_KEY_DISPLAY_MAPS.DEFAULT_CHAR_DISPLAY.equals(extraKeyDisplayMap) && !TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS_STYLE.equals(extraKeysStyle)) {
                Logger.logError(TermuxSharedProperties.LOG_TAG, "The style \"" + extraKeysStyle + "\" for the key \"" + TermuxPropertyConstants.KEY_EXTRA_KEYS_STYLE + "\" is invalid. Using default style instead.");
                extraKeysStyle = TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS_STYLE;
            }

            mExtraKeysInfo = new ExtraKeysInfo(extrakeys, extraKeysStyle, ExtraKeysConstants.CONTROL_CHARS_ALIASES);

            // Existing HONOR 200 installs can already have the previous 5x2 toolbar persisted
            // in termux.properties. Upgrade only that exact NewTermux layout at runtime so
            // unrelated custom extra-key layouts remain untouched.
            if (shouldUpgradeNewTermuxCompactLayout(mExtraKeysInfo)) {
                mExtraKeysInfo = new ExtraKeysInfo(
                    TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS,
                    extraKeysStyle,
                    ExtraKeysConstants.CONTROL_CHARS_ALIASES
                );
            }
        } catch (JSONException e) {
            Logger.showToast(mActivity, "Could not load and set the \"" + TermuxPropertyConstants.KEY_EXTRA_KEYS + "\" property from the properties file: " + e.toString(), true);
            Logger.logStackTraceWithMessage(LOG_TAG, "Could not load and set the \"" + TermuxPropertyConstants.KEY_EXTRA_KEYS + "\" property from the properties file: ", e);

            try {
                mExtraKeysInfo = new ExtraKeysInfo(TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS, TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS_STYLE, ExtraKeysConstants.CONTROL_CHARS_ALIASES);
            } catch (JSONException e2) {
                Logger.showToast(mActivity, "Can't create default extra keys",true);
                Logger.logStackTraceWithMessage(LOG_TAG, "Could create default extra keys: ", e);
                mExtraKeysInfo = null;
            }
        }
    }

    private static boolean shouldUpgradeNewTermuxCompactLayout(ExtraKeysInfo extraKeysInfo) {
        if (extraKeysInfo == null) return false;

        ExtraKeyButton[][] buttons = extraKeysInfo.getMatrix();
        return matchesLayout(buttons, LEGACY_TBM_COMPACT_LAYOUT)
            || matchesLayout(buttons, PREVIOUS_NEWTERMUX_COMPACT_LAYOUT);
    }

    private static boolean matchesLayout(ExtraKeyButton[][] buttons, String[][] expected) {
        if (buttons.length != expected.length) return false;

        for (int row = 0; row < expected.length; row++) {
            if (buttons[row].length != expected[row].length) return false;
            for (int col = 0; col < expected[row].length; col++) {
                if (!expected[row][col].equals(buttons[row][col].getKey()))
                    return false;
            }
        }

        return true;
    }

    public ExtraKeysInfo getExtraKeysInfo() {
        return mExtraKeysInfo;
    }

    @SuppressLint("RtlHardcoded")
    @Override
    public void onTerminalExtraKeyButtonClick(View view, String key, boolean ctrlDown, boolean altDown, boolean shiftDown, boolean fnDown) {
        // NewTermux compact controls: H and C are actions, not literal text.
        // H -> HOME key. C/CLEAR -> clear the current terminal screen.
        if ("H".equals(key)) {
            super.onTerminalExtraKeyButtonClick(view, "HOME", ctrlDown, altDown, shiftDown, fnDown);
        } else if ("C".equals(key) || "CLEAR".equals(key)) {
            com.termux.terminal.TerminalSession session =
                mTermuxTerminalViewClient.getActivity().getCurrentSession();
            if (session != null) session.write("clear\n");
        } else if ("KEYBOARD".equals(key)) {
            if(mTermuxTerminalViewClient != null)
                mTermuxTerminalViewClient.onToggleSoftKeyboardRequest();
        } else if ("DRAWER".equals(key)) {
            DrawerLayout drawerLayout = mTermuxTerminalViewClient.getActivity().getDrawer();
            if (drawerLayout.isDrawerOpen(Gravity.LEFT))
                drawerLayout.closeDrawer(Gravity.LEFT);
            else
                drawerLayout.openDrawer(Gravity.LEFT);
        } else if ("PASTE_ENTER".equals(key)) {
            TerminalView terminalView = mTermuxTerminalViewClient.getActivity().getTerminalView();
            com.termux.terminal.TerminalSession session =
                mTermuxTerminalViewClient.getActivity().getCurrentSession();
            String text = ShareUtils.getTextStringFromClipboardIfSet(mActivity, true);
            if (text != null && terminalView != null && terminalView.mEmulator != null && session != null) {
                terminalView.mEmulator.paste(text);
                super.onTerminalExtraKeyButtonClick(view, "ENTER", false, false, false, false);
            }
        } else if ("PASTE".equals(key)) {
            if(mTermuxTerminalSessionActivityClient != null)
                mTermuxTerminalSessionActivityClient.onPasteTextFromClipboard(null);
        }  else if ("SCROLL".equals(key)) {
            TerminalView terminalView = mTermuxTerminalViewClient.getActivity().getTerminalView();
            if (terminalView != null && terminalView.mEmulator != null)
                terminalView.mEmulator.toggleAutoScrollDisabled();
        } else {
            super.onTerminalExtraKeyButtonClick(view, key, ctrlDown, altDown, shiftDown, fnDown);
        }
    }

}
