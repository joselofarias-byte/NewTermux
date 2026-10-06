package com.termux.app;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.PopupWindow;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;

import com.termux.BuildConfig;
import com.termux.R;
import com.termux.app.api.file.FileReceiverActivity;
import com.termux.app.terminal.TermuxActivityRootView;
import com.termux.app.terminal.TermuxTerminalSessionActivityClient;
import com.termux.app.terminal.io.TermuxTerminalExtraKeys;
import com.termux.shared.activities.ReportActivity;
import com.termux.shared.activity.ActivityUtils;
import com.termux.shared.activity.media.AppCompatActivityUtils;
import com.termux.shared.data.IntentUtils;
import com.termux.shared.android.PermissionUtils;
import com.termux.shared.data.DataUtils;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.TermuxConstants.TERMUX_APP.TERMUX_ACTIVITY;
import com.termux.app.activities.HelpActivity;
import com.termux.app.activities.SettingsActivity;
import com.termux.shared.termux.crash.TermuxCrashUtils;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.app.terminal.TermuxSessionsListViewController;
import com.termux.app.terminal.io.TerminalToolbarViewPager;
import com.termux.app.terminal.TermuxTerminalViewClient;
import com.termux.shared.termux.extrakeys.ExtraKeysView;
import com.termux.shared.termux.interact.TextInputDialogUtils;
import com.termux.shared.interact.ShareUtils;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxUtils;
import com.termux.shared.termux.settings.properties.TermuxAppSharedProperties;
import com.termux.shared.termux.theme.TermuxThemeUtils;
import com.termux.shared.theme.NightMode;
import com.termux.shared.view.ViewUtils;
import com.termux.terminal.TerminalSession;
import com.termux.terminal.TerminalSessionClient;
import com.termux.view.TerminalView;
import com.termux.view.TerminalViewClient;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.viewpager.widget.ViewPager;

import android.Manifest;
import android.content.pm.PackageManager;

import com.newtermux.features.AutoCorrectHandler;
import com.newtermux.features.NewTermuxSettings;
import com.newtermux.features.NewTermuxTheme;
import com.newtermux.features.SpeechInputManager;
import com.newtermux.features.TerminalTaskMonitor;
import com.newtermux.features.TerminalTextExport;
import com.termux.app.terminal.MiniTerminalPipView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

/**
 * A terminal emulator activity.
 * <p/>
 * See
 * <ul>
 * <li>http://www.mongrel-phones.com.au/default/how_to_make_a_local_service_and_bind_to_it_in_android</li>
 * <li>https://code.google.com/p/android/issues/detail?id=6426</li>
 * </ul>
 * about memory leaks.
 */
public final class TermuxActivity extends AppCompatActivity implements ServiceConnection {

    /**
     * The connection to the {@link TermuxService}. Requested in {@link #onCreate(Bundle)} with a call to
     * {@link #bindService(Intent, ServiceConnection, int)}, and obtained and stored in
     * {@link #onServiceConnected(ComponentName, IBinder)}.
     */
    TermuxService mTermuxService;

    /**
     * The {@link TerminalView} shown in  {@link TermuxActivity} that displays the terminal.
     */
    TerminalView mTerminalView;

    /**
     *  The {@link TerminalViewClient} interface implementation to allow for communication between
     *  {@link TerminalView} and {@link TermuxActivity}.
     */
    TermuxTerminalViewClient mTermuxTerminalViewClient;

    /**
     *  The {@link TerminalSessionClient} interface implementation to allow for communication between
     *  {@link TerminalSession} and {@link TermuxActivity}.
     */
    TermuxTerminalSessionActivityClient mTermuxTerminalSessionActivityClient;

    /**
     * Termux app shared preferences manager.
     */
    private TermuxAppSharedPreferences mPreferences;

    /**
     * Termux app SharedProperties loaded from termux.properties
     */
    private TermuxAppSharedProperties mProperties;

    /**
     * The root view of the {@link TermuxActivity}.
     */
    TermuxActivityRootView mTermuxActivityRootView;

    /**
     * The space at the bottom of {@link @mTermuxActivityRootView} of the {@link TermuxActivity}.
     */
    View mTermuxActivityBottomSpaceView;

    /**
     * The terminal extra keys view.
     */
    ExtraKeysView mExtraKeysView;

    /**
     * The client for the {@link #mExtraKeysView}.
     */
    TermuxTerminalExtraKeys mTermuxTerminalExtraKeys;

    /** Whether extra keys were placed in the right drawer at activity creation time. */
    private boolean mExtraKeysInDrawerModeAtCreation = false;

    /**
     * The termux sessions list controller.
     */
    TermuxSessionsListViewController mTermuxSessionListViewController;

    /**
     * The {@link TermuxActivity} broadcast receiver for various things like terminal style configuration changes.
     */
    private final BroadcastReceiver mTermuxActivityBroadcastReceiver = new TermuxActivityBroadcastReceiver();

    /**
     * The last toast shown, used cancel current toast before showing new in {@link #showToast(String, boolean)}.
     */
    Toast mLastToast;

    /**
     * If between onResume() and onStop(). Note that only one session is in the foreground of the terminal view at the
     * time, so if the session causing a change is not in the foreground it should probably be treated as background.
     */
    private boolean mIsVisible;

    /**
     * If onResume() was called after onCreate().
     */
    private boolean mIsOnResumeAfterOnCreate = false;

    /**
     * If activity was restarted like due to call to {@link #recreate()} after receiving
     * {@link TERMUX_ACTIVITY#ACTION_RELOAD_STYLE}, system dark night mode was changed or activity
     * was killed by android.
     */
    private boolean mIsActivityRecreated = false;

    /**
     * The {@link TermuxActivity} is in an invalid state and must not be run.
     */
    private boolean mIsInvalidState;

    private int mNavBarHeight;

    private float mTerminalToolbarDefaultHeight;


    // NewTermux features
    private SpeechInputManager mSpeechInputManager;
    private AutoCorrectHandler mAutoCorrectHandler;
    private com.newtermux.features.PackageManagerMenu mPackageManagerMenu;
    private View mAutocorrectBar;
    private TextView mAutocorrectText;
    private String mPendingCorrection;
    private String mPendingOriginal;
    private ImageButton mBtnSTT;
    private LinearLayout mSessionPipContainer;
    private PopupWindow mSessionNoticePopup;
    private final Handler mSessionNoticeHandler = new Handler(Looper.getMainLooper());
    private final Runnable mDismissSessionNotice = () -> dismissSessionNotice();

    // Compact long-task monitor shown between the toolbar and session previews.
    private View mTaskStatusPanel;
    private TextView mTaskStatusDot;
    private TextView mTaskStatusTitle;
    private TextView mTaskStatusDetail;
    private TextView mTaskStatusElapsed;
    private ProgressBar mTaskStatusProgress;
    private final Handler mTaskMonitorHandler = new Handler(Looper.getMainLooper());
    private boolean mTaskMonitorRefreshPending;
    private final Runnable mTaskMonitorTicker = new Runnable() {
        @Override
        public void run() {
            refreshTaskMonitor();
            if (mIsVisible) mTaskMonitorHandler.postDelayed(this, 1000L);
        }
    };

    private static final int REQUEST_RECORD_AUDIO = 201;

    // SAF launchers for Export Screen and Make Script
    private ActivityResultLauncher<String> mScreenExportSaver;
    private ActivityResultLauncher<String> mScriptSaver;
    private final List<String> mPendingScriptLines = new ArrayList<>();

    private static final int CONTEXT_MENU_SELECT_URL_ID = 0;
    private static final int CONTEXT_MENU_SHARE_TRANSCRIPT_ID = 1;
    private static final int CONTEXT_MENU_SHARE_SELECTED_TEXT = 10;
    private static final int CONTEXT_MENU_AUTOFILL_USERNAME = 11;
    private static final int CONTEXT_MENU_AUTOFILL_PASSWORD = 2;
    private static final int CONTEXT_MENU_RESET_TERMINAL_ID = 3;
    private static final int CONTEXT_MENU_KILL_PROCESS_ID = 4;
    private static final int CONTEXT_MENU_STYLING_ID = 5;
    private static final int CONTEXT_MENU_TOGGLE_KEEP_SCREEN_ON = 6;
    private static final int CONTEXT_MENU_HELP_ID = 7;
    private static final int CONTEXT_MENU_SETTINGS_ID = 8;
    private static final int CONTEXT_MENU_REPORT_ID = 9;
    private static final int CONTEXT_MENU_HOME_ID = 12;
    private static final int CONTEXT_MENU_CLEAR_ID = 13;
    private static final int CONTEXT_MENU_SCROLL_BOTTOM_ID = 14;
    private static final int CONTEXT_MENU_SAVE_TRANSCRIPT_TXT_ID = 15;
    private static final int CONTEXT_MENU_SAVE_SELECTED_TXT_ID = 16;
    private static final int CONTEXT_MENU_COPY_TRANSCRIPT_ID = 17;
    private static final int CONTEXT_MENU_SHARE_TRANSCRIPT_TXT_ID = 18;
    // Parent-only submenu id. Must never collide with actionable menu ids.
    private static final int CONTEXT_MENU_OUTPUT_TOOLS_ID = 19;

    private static final String ARG_TERMINAL_TOOLBAR_TEXT_INPUT = "terminal_toolbar_text_input";
    private static final String ARG_ACTIVITY_RECREATED = "activity_recreated";

    private static final String LOG_TAG = "TermuxActivity";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Logger.logDebug(LOG_TAG, "onCreate");
        mIsOnResumeAfterOnCreate = true;

        if (savedInstanceState != null)
            mIsActivityRecreated = savedInstanceState.getBoolean(ARG_ACTIVITY_RECREATED, false);

        // Delete ReportInfo serialized object files from cache older than 14 days
        ReportActivity.deleteReportInfoFilesOlderThanXDays(this, 14, false);

        // Load Termux app SharedProperties from disk
        mProperties = TermuxAppSharedProperties.getProperties();
        reloadProperties();

        setActivityTheme();

        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_termux);

        // Load termux shared preferences
        // This will also fail if TermuxConstants.TERMUX_PACKAGE_NAME does not equal applicationId
        mPreferences = TermuxAppSharedPreferences.build(this, true);
        if (mPreferences == null) {
            // An AlertDialog should have shown to kill the app, so we don't continue running activity code
            mIsInvalidState = true;
            return;
        }

        setMargins();

        mTermuxActivityRootView = findViewById(R.id.activity_termux_root_view);
        mTermuxActivityRootView.setActivity(this);
        mTermuxActivityBottomSpaceView = findViewById(R.id.activity_termux_bottom_space_view);
        mTermuxActivityRootView.setOnApplyWindowInsetsListener(new TermuxActivityRootView.WindowInsetsListener());

        View content = findViewById(android.R.id.content);
        content.setOnApplyWindowInsetsListener((v, insets) -> {
            mNavBarHeight = insets.getSystemWindowInsetBottom();
            return insets;
        });

        if (mProperties.isUsingFullScreen()) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }

        setTermuxTerminalViewAndClients();

        setTerminalToolbarView(savedInstanceState);

        mScreenExportSaver = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("text/plain"),
            uri -> {
                if (uri == null) return;
                TerminalSession session = getCurrentSession();
                if (session == null) return;
                String text = session.getEmulator().getScreen().getTranscriptText();
                new Thread(() -> {
                    try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                        if (os != null) os.write(text.getBytes());
                        runOnUiThread(() -> Toast.makeText(this, getString(R.string.msg_screen_exported), Toast.LENGTH_SHORT).show());
                    } catch (Exception e) {
                        runOnUiThread(() -> Toast.makeText(this, getString(R.string.msg_export_failed, e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()), Toast.LENGTH_LONG).show());
                    }
                }).start();
            });

        mScriptSaver = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("text/x-shellscript"),
            uri -> {
                if (uri == null || mPendingScriptLines.isEmpty()) return;
                List<String> lines = new ArrayList<>(mPendingScriptLines);
                mPendingScriptLines.clear();
                new Thread(() -> {
                    try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                        if (os == null) return;
                        os.write("#!/bin/zsh\n".getBytes());
                        for (String line : lines) {
                            os.write((line + "\n").getBytes());
                        }
                        runOnUiThread(() -> Toast.makeText(this, getString(R.string.msg_script_saved), Toast.LENGTH_SHORT).show());
                    } catch (Exception e) {
                        runOnUiThread(() -> Toast.makeText(this, getString(R.string.msg_save_failed, e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()), Toast.LENGTH_LONG).show());
                    }
                }).start();
            });

        setupDrawerCommandButtons();

        setupNewTermuxFeatures();

        registerForContextMenu(mTerminalView);

        FileReceiverActivity.updateFileReceiverActivityComponentsState(this);

        try {
            // Start the {@link TermuxService} and make it run regardless of who is bound to it
            Intent serviceIntent = new Intent(this, TermuxService.class);
            startService(serviceIntent);

            // Attempt to bind to the service, this will call the {@link #onServiceConnected(ComponentName, IBinder)}
            // callback if it succeeds.
            if (!bindService(serviceIntent, this, 0))
                throw new RuntimeException("bindService() failed");
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG,"TermuxActivity failed to start TermuxService", e);
            Logger.showToast(this,
                getString(e.getMessage() != null && e.getMessage().contains("app is in background") ?
                    R.string.error_termux_service_start_failed_bg : R.string.error_termux_service_start_failed_general),
                true);
            mIsInvalidState = true;
            return;
        }

        // Send the {@link TermuxConstants#BROADCAST_TERMUX_OPENED} broadcast to notify apps that Termux
        // app has been opened.
        TermuxUtils.sendTermuxOpenedBroadcast(this);
    }

    @Override
    public void onStart() {
        super.onStart();

        Logger.logDebug(LOG_TAG, "onStart");

        if (mIsInvalidState) return;

        mIsVisible = true;

        if (mTermuxTerminalSessionActivityClient != null)
            mTermuxTerminalSessionActivityClient.onStart();

        if (mTermuxTerminalViewClient != null)
            mTermuxTerminalViewClient.onStart();

        if (mPreferences.isTerminalMarginAdjustmentEnabled())
            addTermuxActivityRootViewGlobalLayoutListener();

        registerTermuxActivityBroadcastReceiver();

        // NOTE: the keep-alive wake lock is intentionally NOT released here anymore. It is now tied
        // to the session lifecycle in TermuxService (held while any session is alive), not to the
        // Activity lifecycle — releasing it on every foreground transition is what made it flap.
    }

    @Override
    public void onResume() {
        super.onResume();

        Logger.logVerbose(LOG_TAG, "onResume");

        if (mIsInvalidState) return;

        // Re-apply extra keys settings when returning from Settings
        boolean currentDrawerMode = com.newtermux.features.NewTermuxSettings.isExtraKeysInDrawer(this);
        if (currentDrawerMode != mExtraKeysInDrawerModeAtCreation) {
            recreate();
            return;
        }
        if (!currentDrawerMode) {
            ViewPager vp = getTerminalToolbarViewPager();
            if (vp != null) {
                vp.setVisibility(com.newtermux.features.NewTermuxSettings.isExtraKeysVisible(this)
                    ? View.VISIBLE : View.GONE);
                scheduleTerminalGeometryRefresh();
            }
        }

        if (mTermuxTerminalSessionActivityClient != null)
            mTermuxTerminalSessionActivityClient.onResume();

        if (mTermuxTerminalViewClient != null)
            mTermuxTerminalViewClient.onResume();

        // Check if a crash happened on last run of the app or if a plugin crashed and show a
        // notification with the crash details if it did
        TermuxCrashUtils.notifyAppCrashFromCrashLogFile(this, LOG_TAG);

        mIsOnResumeAfterOnCreate = false;
        applyAccentColor();
        applyFeatureSettings();
        applyOrientationLayoutPolicy();
        startTaskMonitorTicker();
        if (mTermuxTerminalSessionActivityClient != null)
            mTermuxTerminalSessionActivityClient.checkForFontAndColors();

        // One-time nudge to allowlist NewTermux from battery optimization / Doze, so the keep-alive
        // foreground service actually survives backgrounding. Gated: only shows if keep-alive is on,
        // we are not already exempt, and we have not asked before.
        maybePromptBatteryOptimization();

        // Run any command injected by Settings (e.g. "pkg install zsh\n")
        String pendingCmd = com.newtermux.features.NewTermuxSettings.getPendingCommand(this);
        if (pendingCmd != null) {
            com.newtermux.features.NewTermuxSettings.clearPendingCommand(this);
            TerminalSession session = getCurrentSession();
            if (session != null) {
                byte[] bytes = pendingCmd.getBytes();
                session.write(bytes, 0, bytes.length);
            }
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull android.content.res.Configuration newConfig) {
        // TermuxActivity handles orientation itself via configChanges, so Android does not
        // reinflate the layout on rotation. Capture IME state before applying the new
        // compact landscape policy, then restore it after the terminal has usable space.
        boolean restoreIme = isSoftKeyboardVisible();
        super.onConfigurationChanged(newConfig);

        setMargins();
        applyOrientationLayoutPolicy();
        scheduleTerminalGeometryRefresh();

        if (restoreIme && mTerminalView != null) {
            mTerminalView.postDelayed(() ->
                com.termux.shared.view.KeyboardUtils.showSoftKeyboard(this, mTerminalView), 180L);
        }
    }

    /**
     * Show a one-time dialog asking the user to allowlist NewTermux from battery optimization /
     * Doze. Without the exemption, Android can still tear the process down in the background even
     * with a foreground service + wake lock. Gated so it is unobtrusive: only when keep-alive is
     * enabled, we are not already exempt, and we have not prompted before.
     */
    private void maybePromptBatteryOptimization() {
        if (!com.newtermux.features.NewTermuxSettings.isKeepAliveInBackground(this)) return;
        if (com.newtermux.features.NewTermuxSettings.wasBatteryOptPrompted(this)) return;
        if (PermissionUtils.checkIfBatteryOptimizationsDisabled(this)) return;

        // Mark as prompted up front so this only ever appears once, regardless of the user's choice.
        com.newtermux.features.NewTermuxSettings.setBatteryOptPrompted(this, true);

        new AlertDialog.Builder(this)
            .setTitle(R.string.battery_opt_prompt_title)
            .setMessage(R.string.battery_opt_prompt_message)
            .setPositiveButton(R.string.battery_opt_prompt_allow, (d, w) -> {
                try {
                    PermissionUtils.requestDisableBatteryOptimizations(this);
                } catch (Exception e) {
                    Logger.logError(LOG_TAG, "Failed to request battery optimization exemption: " + e);
                }
            })
            .setNegativeButton(R.string.battery_opt_prompt_later, null)
            .show();
    }

    private void applyAccentColor() {
        int color = NewTermuxTheme.getAccentColor(this);

        // AC toggle button
        android.widget.TextView btnAC = findViewById(R.id.btn_autocorrect_toggle);
        if (btnAC != null) {
            boolean acOn = mTerminalView != null && mTerminalView.isKeyboardSuggestionsEnabled();
            btnAC.setTextColor(acOn ? color : getResources().getColor(R.color.nt_on_surface, getTheme()));
        }

        // STT button (idle state)
        if (mBtnSTT != null) mBtnSTT.setColorFilter(color);

        // Settings cog button
        ImageButton btnSettings = findViewById(R.id.btn_settings);
        if (btnSettings != null) btnSettings.setColorFilter(color);

        // Session chips
        updateSessionTabs();

        // Drawer command buttons
        setupDrawerCommandButtons();
    }

    private void applyFeatureSettings() {
        setVisible(R.id.btn_autocorrect_toggle, NewTermuxSettings.isShowAcButton(this));
        setVisible(R.id.btn_stt, NewTermuxSettings.isShowSttButton(this));
        setVisible(R.id.btn_packages_menu, NewTermuxSettings.isShowPackagesButton(this));
        setVisible(R.id.btn_clear_terminal, NewTermuxSettings.isShowClearButton(this));
        // Session previews are useful in portrait but consume most of the HONOR 200 height
        // in landscape, especially while the software keyboard is visible.
        setVisible(R.id.session_tabs_scroll,
            NewTermuxSettings.isSessionTabsEnabled(this) && !isLandscapeMode());
        // Autocorrect initial enabled state
        if (mAutoCorrectHandler != null)
            mAutoCorrectHandler.setEnabled(NewTermuxSettings.isAutocorrectEnabled(this));
    }

    private boolean isLandscapeMode() {
        return getResources().getConfiguration().orientation
            == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
    }

    private boolean isSoftKeyboardVisible() {
        if (mTerminalView == null) return false;
        androidx.core.view.WindowInsetsCompat insets =
            androidx.core.view.ViewCompat.getRootWindowInsets(mTerminalView);
        return insets != null
            && insets.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime());
    }

    /**
     * Keep the terminal usable on short landscape displays.
     *
     * The activity opts into handling orientation changes itself. Without an explicit
     * policy, the 80dp session previews plus multi-row extra keys can consume almost
     * the entire landscape height before TerminalView is measured.
     */
    private void applyOrientationLayoutPolicy() {
        final boolean landscape = isLandscapeMode();

        setVisible(R.id.session_tabs_scroll,
            NewTermuxSettings.isSessionTabsEnabled(this) && !landscape);

        if (!mExtraKeysInDrawerModeAtCreation) {
            ViewPager terminalToolbarViewPager = getTerminalToolbarViewPager();
            if (terminalToolbarViewPager != null) {
                boolean showExtraKeys =
                    NewTermuxSettings.isExtraKeysVisible(this) && !landscape;
                terminalToolbarViewPager.setVisibility(
                    showExtraKeys ? View.VISIBLE : View.GONE);
            }
        }

        // The long-task card can cost another ~58dp. Keep status available in portrait,
        // but prioritize a readable terminal viewport in landscape.
        if (landscape) {
            if (mTaskStatusPanel != null) mTaskStatusPanel.setVisibility(View.GONE);
        } else {
            refreshTaskMonitor();
        }

        scheduleTerminalGeometryRefresh();
    }

    private void setVisible(int id, boolean visible) {
        View v = findViewById(id);
        if (v != null) {
            int targetVisibility = visible ? View.VISIBLE : View.GONE;
            if (v.getVisibility() != targetVisibility) {
                v.setVisibility(targetVisibility);
                if (id == R.id.session_tabs_scroll)
                    scheduleTerminalGeometryRefresh();
            }
        }
    }

    private void scheduleTerminalGeometryRefresh() {
        if (mTermuxTerminalViewClient != null) {
            mTermuxTerminalViewClient.scheduleTerminalGeometryRefresh();
        } else if (mTerminalView != null) {
            mTerminalView.post(() -> {
                mTerminalView.requestLayout();
                mTerminalView.updateSize();
                mTerminalView.invalidate();
            });
        }
    }

    @Override
    protected void onStop() {
        super.onStop();

        Logger.logDebug(LOG_TAG, "onStop");

        if (mIsInvalidState) return;

        mIsVisible = false;
        mTaskMonitorHandler.removeCallbacks(mTaskMonitorTicker);
        mSessionNoticeHandler.removeCallbacks(mDismissSessionNotice);
        dismissSessionNotice();

        if (mTermuxTerminalSessionActivityClient != null)
            mTermuxTerminalSessionActivityClient.onStop();

        if (mTermuxTerminalViewClient != null)
            mTermuxTerminalViewClient.onStop();

        removeTermuxActivityRootViewGlobalLayoutListener();

        unregisterTermuxActivityBroadcastReceiver();
        getDrawer().closeDrawers();

        // Snapshot the current tab list to disk as we go to background, so that if the process is
        // torn down while a game is in the foreground, the next launch can rehydrate the tabs. The
        // keep-alive wake lock is already held continuously by the service (session-driven).
        if (mTermuxService != null)
            SessionStatePersister.save(this, mTermuxService.getTermuxSessions());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        Logger.logDebug(LOG_TAG, "onDestroy");

        if (mIsInvalidState) return;

        mTaskMonitorHandler.removeCallbacksAndMessages(null);
        mSessionNoticeHandler.removeCallbacksAndMessages(null);
        dismissSessionNotice();
        if (mSpeechInputManager != null) { mSpeechInputManager.destroy(); mSpeechInputManager = null; }
        if (mAutoCorrectHandler != null) { mAutoCorrectHandler.destroy(); mAutoCorrectHandler = null; }

        if (mTermuxService != null) {
            // Do not leave service and session clients with references to activity.
            mTermuxService.unsetTermuxTerminalSessionClient();
            mTermuxService = null;
        }

        try {
            unbindService(this);
        } catch (Exception e) {
            // ignore.
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle savedInstanceState) {
        Logger.logVerbose(LOG_TAG, "onSaveInstanceState");

        super.onSaveInstanceState(savedInstanceState);
        saveTerminalToolbarTextInput(savedInstanceState);
        savedInstanceState.putBoolean(ARG_ACTIVITY_RECREATED, true);
    }





    /**
     * Part of the {@link ServiceConnection} interface. The service is bound with
     * {@link #bindService(Intent, ServiceConnection, int)} in {@link #onCreate(Bundle)} which will cause a call to this
     * callback method.
     */
    @Override
    public void onServiceConnected(ComponentName componentName, IBinder service) {
        Logger.logDebug(LOG_TAG, "onServiceConnected");

        mTermuxService = ((TermuxService.LocalBinder) service).service;

        final Intent intent = getIntent();
        setIntent(null);

        if (mTermuxService.isTermuxSessionsEmpty()) {
            if (mIsVisible) {
                TermuxInstaller.setupBootstrapIfNeeded(TermuxActivity.this, () -> {
                    if (mTermuxService == null) return; // Activity might have been destroyed.
                    try {
                        boolean launchFailsafe = false;
                        if (intent != null && intent.getExtras() != null) {
                            launchFailsafe = intent.getExtras().getBoolean(TERMUX_ACTIVITY.EXTRA_FAILSAFE_SESSION, false);
                        }
                        mTermuxTerminalSessionActivityClient.addNewSession(launchFailsafe, null);
                    } catch (WindowManager.BadTokenException e) {
                        // Activity finished - ignore.
                    }
                });
            } else {
                // The service connected while not in foreground - just bail out.
                finishActivityIfNotFinishing();
            }
        } else {
            // If termux was started from launcher "New session" shortcut and activity is recreated,
            // then the original intent will be re-delivered, resulting in a new session being re-added
            // each time.
            if (!mIsActivityRecreated && intent != null && Intent.ACTION_RUN.equals(intent.getAction())) {
                // Android 7.1 app shortcut from res/xml/shortcuts.xml.
                boolean isFailSafe = intent.getBooleanExtra(TERMUX_ACTIVITY.EXTRA_FAILSAFE_SESSION, false);
                mTermuxTerminalSessionActivityClient.addNewSession(isFailSafe, null);
            } else {
                mTermuxTerminalSessionActivityClient.setCurrentSession(mTermuxTerminalSessionActivityClient.getCurrentStoredSessionOrLast());
            }
        }

        // Update the {@link TerminalSession} and {@link TerminalEmulator} clients.
        mTermuxService.setTermuxTerminalSessionClient(mTermuxTerminalSessionActivityClient);

        // Now that the service is connected and sessions exist, populate the session chips.
        updateSessionTabs();

        // Ensure zsh plugins + shell are set up for existing installs that skipped first-run.
        new Thread(() -> TermuxInstaller.installZshPlugins(this)).start();

        // Request storage permission on every launch; no-op if already granted.
        // Demo build: skip entirely — demo has no real filesystem, setupStorageSymlinks
        // would try to access /data/data/com.termux/ which the demo package cannot reach.
        if (!BuildConfig.IS_DEMO) requestStoragePermission(false);
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        Logger.logDebug(LOG_TAG, "onServiceDisconnected");

        // Respect being stopped from the {@link TermuxService} notification action.
        finishActivityIfNotFinishing();
    }






    private void reloadProperties() {
        mProperties.loadTermuxPropertiesFromDisk();

        if (mTermuxTerminalViewClient != null)
            mTermuxTerminalViewClient.onReloadProperties();
    }



    private void setActivityTheme() {
        // Update NightMode.APP_NIGHT_MODE
        TermuxThemeUtils.setAppNightMode(mProperties.getNightMode());

        // Set activity night mode. If NightMode.SYSTEM is set, then android will automatically
        // trigger recreation of activity when uiMode/dark mode configuration is changed so that
        // day or night theme takes affect.
        AppCompatActivityUtils.setNightMode(this, NightMode.getAppNightMode().getName(), true);
    }

    private void setMargins() {
        RelativeLayout relativeLayout = findViewById(R.id.activity_termux_root_relative_layout);
        int marginHorizontal = mProperties.getTerminalMarginHorizontal();
        int marginVertical = mProperties.getTerminalMarginVertical();
        ViewUtils.setLayoutMarginsInDp(relativeLayout, marginHorizontal, marginVertical, marginHorizontal, marginVertical);
    }



    public void addTermuxActivityRootViewGlobalLayoutListener() {
        getTermuxActivityRootView().getViewTreeObserver().addOnGlobalLayoutListener(getTermuxActivityRootView());
    }

    public void removeTermuxActivityRootViewGlobalLayoutListener() {
        if (getTermuxActivityRootView() != null)
            getTermuxActivityRootView().getViewTreeObserver().removeOnGlobalLayoutListener(getTermuxActivityRootView());
    }



    private void setTermuxTerminalViewAndClients() {
        // Set termux terminal view and session clients
        mTermuxTerminalSessionActivityClient = new TermuxTerminalSessionActivityClient(this);
        mTermuxTerminalViewClient = new TermuxTerminalViewClient(this, mTermuxTerminalSessionActivityClient);

        // Set termux terminal view
        mTerminalView = findViewById(R.id.terminal_view);
        mTerminalView.setTerminalViewClient(mTermuxTerminalViewClient);

        if (mTermuxTerminalViewClient != null)
            mTermuxTerminalViewClient.onCreate();

        if (mTermuxTerminalSessionActivityClient != null)
            mTermuxTerminalSessionActivityClient.onCreate();
    }




    private void setTerminalToolbarView(Bundle savedInstanceState) {
        mTermuxTerminalExtraKeys = new TermuxTerminalExtraKeys(this, mTerminalView,
            mTermuxTerminalViewClient, mTermuxTerminalSessionActivityClient);

        final ViewPager terminalToolbarViewPager = getTerminalToolbarViewPager();

        ViewGroup.LayoutParams layoutParams = terminalToolbarViewPager.getLayoutParams();
        mTerminalToolbarDefaultHeight = layoutParams.height;

        boolean inDrawer = com.newtermux.features.NewTermuxSettings.isExtraKeysInDrawer(this);
        mExtraKeysInDrawerModeAtCreation = inDrawer;
        getDrawer().setDrawerLockMode(
            inDrawer ? DrawerLayout.LOCK_MODE_UNLOCKED : DrawerLayout.LOCK_MODE_LOCKED_CLOSED,
            Gravity.END);

        if (inDrawer) {
            // Route extra keys into the right drawer instead of the bottom ViewPager
            ExtraKeysView rightEkv = (ExtraKeysView) findViewById(R.id.right_drawer_extra_keys);
            if (rightEkv != null) {
                rightEkv.setExtraKeysViewClient(mTermuxTerminalExtraKeys);
                rightEkv.setButtonTextAllCaps(getProperties().shouldExtraKeysTextBeAllCaps());
                mExtraKeysView = rightEkv;
                rightEkv.reload(mTermuxTerminalExtraKeys.getExtraKeysInfo(), mTerminalToolbarDefaultHeight);
                // GridLayout buttons use height=0 + FILL — the view needs a fixed height to expand into
                if (mTermuxTerminalExtraKeys.getExtraKeysInfo() != null) {
                    int rowCount = mTermuxTerminalExtraKeys.getExtraKeysInfo().getMatrix().length;
                    int totalHeight = Math.round(mTerminalToolbarDefaultHeight * rowCount
                        * mProperties.getTerminalToolbarHeightScaleFactor());
                    ViewGroup.LayoutParams ekParams = rightEkv.getLayoutParams();
                    ekParams.height = totalHeight;
                    rightEkv.setLayoutParams(ekParams);
                }
            }
            // ViewPager stays GONE; no adapter set in drawer mode
        } else {
            if (com.newtermux.features.NewTermuxSettings.isExtraKeysVisible(this)) {
                terminalToolbarViewPager.setVisibility(View.VISIBLE);
            }
            setTerminalToolbarHeight();

            String savedTextInput = null;
            if (savedInstanceState != null)
                savedTextInput = savedInstanceState.getString(ARG_TERMINAL_TOOLBAR_TEXT_INPUT);

            terminalToolbarViewPager.setAdapter(new TerminalToolbarViewPager.PageAdapter(this, savedTextInput));
            terminalToolbarViewPager.addOnPageChangeListener(new TerminalToolbarViewPager.OnPageChangeListener(this, terminalToolbarViewPager));
        }
    }

    private void setTerminalToolbarHeight() {
        final ViewPager terminalToolbarViewPager = getTerminalToolbarViewPager();
        if (terminalToolbarViewPager == null) return;

        ViewGroup.LayoutParams layoutParams = terminalToolbarViewPager.getLayoutParams();
        layoutParams.height = Math.round(mTerminalToolbarDefaultHeight *
            (mTermuxTerminalExtraKeys.getExtraKeysInfo() == null ? 0 : mTermuxTerminalExtraKeys.getExtraKeysInfo().getMatrix().length) *
            mProperties.getTerminalToolbarHeightScaleFactor());
        terminalToolbarViewPager.setLayoutParams(layoutParams);
    }

    public void toggleTerminalToolbar() {
        if (mExtraKeysInDrawerModeAtCreation) return; // in drawer mode — use right drawer swipe instead
        final ViewPager terminalToolbarViewPager = getTerminalToolbarViewPager();
        if (terminalToolbarViewPager == null) return;

        final boolean showNow = mPreferences.toogleShowTerminalToolbar();
        Logger.showToast(this, (showNow ? getString(R.string.msg_enabling_terminal_toolbar) : getString(R.string.msg_disabling_terminal_toolbar)), true);
        terminalToolbarViewPager.setVisibility(showNow ? View.VISIBLE : View.GONE);
        scheduleTerminalGeometryRefresh();
        if (showNow && isTerminalToolbarTextInputViewSelected()) {
            // Focus the text input view if just revealed.
            findViewById(R.id.terminal_toolbar_text_input).requestFocus();
        }
    }

    private void saveTerminalToolbarTextInput(Bundle savedInstanceState) {
        if (savedInstanceState == null) return;

        final EditText textInputView = findViewById(R.id.terminal_toolbar_text_input);
        if (textInputView != null) {
            String textInput = textInputView.getText().toString();
            if (!textInput.isEmpty()) savedInstanceState.putString(ARG_TERMINAL_TOOLBAR_TEXT_INPUT, textInput);
        }
    }



    // -----------------------------------------------------------------------------------------
    // NewTermux Features: Speech-to-Text, Root Toggle, AutoCorrect
    // -----------------------------------------------------------------------------------------

    private void setupNewTermuxFeatures() {
        // Initialize managers
        mSpeechInputManager = new SpeechInputManager(this);
        mAutoCorrectHandler = new AutoCorrectHandler(this);
        mPackageManagerMenu = new com.newtermux.features.PackageManagerMenu(this);

        // Autocorrect UI
        mAutocorrectBar = findViewById(R.id.autocorrect_bar);
        mAutocorrectText = findViewById(R.id.autocorrect_text);
        View btnApply = findViewById(R.id.autocorrect_apply_button);
        View btnClose = findViewById(R.id.autocorrect_close_button);
        if (btnApply != null) btnApply.setOnClickListener(v -> applyAutocorrect());
        if (btnClose != null) btnClose.setOnClickListener(v -> hideAutocorrectBar());

        // Wire toolbar buttons
        View appTitle = findViewById(R.id.tv_app_title);
        if (appTitle != null) {
            appTitle.setOnClickListener(v -> {
                DrawerLayout drawer = getDrawer();
                if (drawer == null) return;
                if (drawer.isDrawerOpen(Gravity.START)) drawer.closeDrawer(Gravity.START);
                else drawer.openDrawer(Gravity.START);
            });
        }

        mBtnSTT = findViewById(R.id.btn_stt);
        if (mBtnSTT != null) {
            mBtnSTT.setOnClickListener(v -> onSTTButtonClicked());
            if (!SpeechInputManager.isAvailable(this)) {
                mBtnSTT.setAlpha(0.4f);
            }
        }

        View btnCopyVisible = findViewById(R.id.btn_copy_visible);
        if (btnCopyVisible != null) {
            // Primary action: copy everything in the terminal transcript in one tap.
            btnCopyVisible.setOnClickListener(v -> copyFullTerminalTranscript());
            // Secondary gesture retained for the rarer "visible screen only" case.
            btnCopyVisible.setOnLongClickListener(v -> {
                copyVisibleTerminalOutput();
                return true;
            });
        }

        View btnPasteEnter = findViewById(R.id.btn_paste_enter);
        if (btnPasteEnter != null) {
            btnPasteEnter.setOnClickListener(v -> {
                if (mTermuxTerminalExtraKeys != null)
                    mTermuxTerminalExtraKeys.onTerminalExtraKeyButtonClick(null, "PASTE_ENTER", false, false, false, false);
            });
            btnPasteEnter.setOnLongClickListener(v -> {
                if (mTermuxTerminalExtraKeys != null)
                    mTermuxTerminalExtraKeys.onTerminalExtraKeyButtonClick(null, "PASTE", false, false, false, false);
                return true;
            });
        }

        View btnPackages = findViewById(R.id.btn_packages_menu);
        if (btnPackages != null) {
            btnPackages.setOnClickListener(v -> mPackageManagerMenu.show(v));
            btnPackages.setOnLongClickListener(v -> {
                startActivity(new Intent(this, com.termux.app.activities.PackageManagerActivity.class));
                return true;
            });
        }

        View btnClear = findViewById(R.id.btn_clear_terminal);
        if (btnClear != null) {
            btnClear.setOnClickListener(v -> {
                TerminalSession session = getCurrentSession();
                if (session != null) {
                    session.write("clear\n");
                }
            });
        }

        com.newtermux.features.ControlAppearanceDialog.applyToolbarColors(
            this, btnClear, btnCopyVisible, btnPasteEnter);

        View btnMore = findViewById(R.id.btn_more_actions);
        if (btnMore != null) {
            btnMore.setOnClickListener(this::showMoreActionsMenu);
        }

        ImageButton btnSettings = findViewById(R.id.btn_settings);
        if (btnSettings != null) {
            btnSettings.setOnClickListener(v -> {
                ActivityUtils.startActivity(this, new Intent(this, SettingsActivity.class));
            });
        }

        android.widget.TextView btnAC = findViewById(R.id.btn_autocorrect_toggle);
        if (btnAC != null) {
            // Apply saved AC state
            boolean acEnabled = NewTermuxSettings.isKeyboardSuggestionsEnabled(this);
            mTerminalView.setKeyboardSuggestionsEnabled(acEnabled);
            if (mAutoCorrectHandler != null) mAutoCorrectHandler.setEnabled(acEnabled);

            btnAC.setOnClickListener(v -> {
                boolean nowEnabled = !mTerminalView.isKeyboardSuggestionsEnabled();
                mTerminalView.setKeyboardSuggestionsEnabled(nowEnabled);
                if (mAutoCorrectHandler != null) mAutoCorrectHandler.setEnabled(nowEnabled);
                NewTermuxSettings.setKeyboardSuggestions(this, nowEnabled);
                int color = getResources().getColor(
                    nowEnabled ? R.color.nt_primary : R.color.nt_on_surface, getTheme());
                btnAC.setTextColor(color);
            });
        }

        ImageButton btnNewSession = findViewById(R.id.btn_new_session);
        if (btnNewSession != null) {
            btnNewSession.setOnClickListener(v -> {
                if (mTermuxTerminalSessionActivityClient != null) {
                    mTermuxTerminalSessionActivityClient.addNewSession(false, null);
                }
            });
        }

        // Session pip row
        mSessionPipContainer = findViewById(R.id.session_pip_container);
        updateSessionTabs();

        // Long-running task summary. It remains hidden for ordinary interactive shell use.
        mTaskStatusPanel = findViewById(R.id.task_status_panel);
        mTaskStatusDot = findViewById(R.id.task_status_dot);
        mTaskStatusTitle = findViewById(R.id.task_status_title);
        mTaskStatusDetail = findViewById(R.id.task_status_detail);
        mTaskStatusElapsed = findViewById(R.id.task_status_elapsed);
        mTaskStatusProgress = findViewById(R.id.task_status_progress);
        if (mTaskStatusPanel != null) {
            mTaskStatusPanel.setOnClickListener(v -> showTaskMonitorDetails());
        }
        refreshTaskMonitor();

        // STT result callback
        mSpeechInputManager.setCallback(new SpeechInputManager.SpeechCallback() {
            @Override
            public void onResult(String text) {
                runOnUiThread(() -> insertSpeechTextToTerminal(text));
            }
            @Override
            public void onError(String error) {
                runOnUiThread(() -> showToast(error, false));
            }
            @Override
            public void onListeningStarted() {
                runOnUiThread(() -> updateSTTButtonState(true));
            }
            @Override
            public void onListeningStopped() {
                runOnUiThread(() -> updateSTTButtonState(false));
            }
        });
    }

    private void copyVisibleTerminalOutput() {
        if (mTerminalView == null) return;
        String text = mTerminalView.getVisibleText();
        if (DataUtils.isNullOrEmpty(text)) return;
        ShareUtils.copyTextToClipboard(this, text, getString(R.string.msg_visible_screen_copied));
    }

    private void copyFullTerminalTranscript() {
        TerminalSession session = getCurrentSession();
        if (session == null || session.getEmulator() == null) return;
        String text = session.getEmulator().getScreen().getTranscriptText();
        if (DataUtils.isNullOrEmpty(text)) return;
        ShareUtils.copyTextToClipboard(this, text, getString(R.string.msg_full_transcript_copied));
    }

    private void showMoreActionsMenu(View anchor) {
        List<String> items = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();

        // NewTermux is used primarily as an AI coding environment, so the
        // primary launch path must not be buried behind setup submenus.
        final String preferredOpenCode = findPreferredOpenCodeCommand();
        items.add(preferredOpenCode != null ? "OpenCode · Abrir" : "OpenCode · Instalar");
        actions.add(() -> {
            if (preferredOpenCode != null) {
                launchDetectedEnvironment("OpenCode", preferredOpenCode);
            } else {
                showOpenCodeSetupDialog();
            }
        });

        items.add("9router-go · router local  ›");
        actions.add(this::showRouterInstaller);

        items.add("Ahorro de tokens · RTK  ›");
        actions.add(this::showTokenSaverMenu);

        // Hidden toolbar favorites automatically move into More.
        if (!NewTermuxSettings.isShowPackagesButton(this)) {
            items.add(getString(R.string.newtermux_toolbar_packages));
            actions.add(() -> {
                if (mPackageManagerMenu != null) mPackageManagerMenu.show(anchor);
            });
        }
        if (!NewTermuxSettings.isShowClearButton(this)) {
            items.add(getString(R.string.newtermux_toolbar_clear));
            actions.add(() -> {
                TerminalSession session = getCurrentSession();
                if (session != null) session.write("clear\n");
            });
        }
        if (!NewTermuxSettings.isShowSttButton(this)) {
            items.add(getString(R.string.newtermux_toolbar_speech));
            actions.add(this::onSTTButtonClicked);
        }
        if (!NewTermuxSettings.isShowAcButton(this)) {
            boolean acEnabled = mTerminalView != null && mTerminalView.isKeyboardSuggestionsEnabled();
            items.add(getString(acEnabled ? R.string.more_autocorrect_on : R.string.more_autocorrect_off));
            actions.add(this::toggleToolbarAutocorrect);
        }

        // Permanent secondary actions.
        items.add(getString(R.string.more_save_txt));
        actions.add(() -> saveTerminalTextAsTxt(false, false));

        items.add(getString(R.string.more_paste));
        actions.add(() -> {
            if (mTermuxTerminalExtraKeys != null)
                mTermuxTerminalExtraKeys.onTerminalExtraKeyButtonClick(null, "PASTE", false, false, false, false);
        });

        items.add(getString(R.string.more_home));
        actions.add(() -> {
            if (mTermuxTerminalExtraKeys != null)
                mTermuxTerminalExtraKeys.onTerminalExtraKeyButtonClick(null, "HOME", false, false, false, false);
        });

        items.add(getString(R.string.more_latest));
        actions.add(() -> {
            if (mTerminalView != null) mTerminalView.scrollToBottom();
        });

        boolean imeVisible = isSoftKeyboardVisible();
        items.add(getString(imeVisible ? R.string.more_keyboard_hide : R.string.more_keyboard_show));
        actions.add(() -> {
            if (mTermuxTerminalViewClient != null)
                mTermuxTerminalViewClient.onToggleSoftKeyboardRequest();
        });

        items.add(getString(R.string.more_files));
        actions.add(() -> startActivity(new Intent(this, com.termux.app.activities.FileManagerActivity.class)));

        items.add("Mis scripts");
        actions.add(() -> com.newtermux.features.ScriptLibrary.show(this, getCurrentSession()));

        items.add("Personalizar controles");
        actions.add(() -> com.newtermux.features.ControlAppearanceDialog.show(this, this::recreate));

        items.add("TBM · respaldo y restauración  ›");
        actions.add(this::showTbmMenu);

        items.add("Entornos y herramientas  ›");
        actions.add(this::showDetectedEnvironments);

        items.add("Instalar componentes y PRoot");
        actions.add(this::showComponentInstaller);

        boolean promptClockEnabled = new java.io.File(
            com.termux.shared.termux.TermuxConstants.TERMUX_HOME_DIR,
            ".newtermux/prompt-clock.enabled").isFile();
        items.add("Hora en prompt · " + (promptClockEnabled ? "Sí" : "No"));
        actions.add(() -> {
            TerminalSession session = getCurrentSession();
            if (session == null) {
                showToast("Abrí una sesión primero", false);
                return;
            }
            com.newtermux.features.BundledInstallerLibrary.runPromptClockInstaller(
                this, session, promptClockEnabled ? "disable" : "enable");
        });

        items.add("Guardar salidas largas · "
            + (NewTermuxSettings.isAutoSaveOutputEnabled(this) ? "Sí" : "No"));
        actions.add(this::configureAutoOutput);

        com.newtermux.features.NtPopupMenu.showAsDropDown(
            this,
            anchor,
            null,
            items.toArray(new String[0]),
            idx -> {
                if (idx >= 0 && idx < actions.size()) actions.get(idx).run();
            }
        );
    }

    /**
     * Discover installed development environments at the moment the menu is opened.
     *
     * No manual registry is required: NewTermux scans the native prefix/home plus
     * installed proot-distro root filesystems and only offers launchable entries.
     */
    private void showDetectedEnvironments() {
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();

        File dataDir = new File(getApplicationInfo().dataDir);
        File prefixDir = new File(dataDir, "files/usr");
        File homeDir = new File(dataDir, "files/home");
        File prootDistro = firstExistingFile(
            new File(prefixDir, "bin/proot-distro"),
            new File(prefixDir, "bin/proot-distro.sh"));

        String[][] toolSpecs = {
            {"opencode", "OpenCode"},
            {"agy", "Antigravity (agy)"},
            {"claude", "Claude Code"},
            {"gemini", "Gemini CLI"},
            {"codex", "Codex"},
            {"aider", "Aider"},
            {"9router-go", "9router-go"}
        };

        // Native NewTermux tools.
        for (String[] spec : toolSpecs) {
            File tool = findHostTool(prefixDir, homeDir, spec[0]);
            if (tool != null) {
                String command = shellQuote(tool.getAbsolutePath());
                labels.add(spec[1] + " · NewTermux");
                actions.add(() -> launchDetectedEnvironment(spec[1], command));
            }
        }

        // Every installed proot-distro environment is discovered from its rootfs directory.
        File rootfsBase = new File(prefixDir, "var/lib/proot-distro/installed-rootfs");
        File[] distros = rootfsBase.listFiles(File::isDirectory);
        if (prootDistro != null && distros != null) {
            Arrays.sort(distros, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
            for (File rootfs : distros) {
                final String alias = rootfs.getName();
                final String distroLabel = friendlyEnvironmentName(alias);

                String shellCommand = shellQuote(prootDistro.getAbsolutePath())
                    + " login " + shellQuote(alias);
                labels.add(distroLabel + " · shell");
                actions.add(() -> launchDetectedEnvironment(distroLabel, shellCommand));

                for (String[] spec : toolSpecs) {
                    String insidePath = findDistroTool(rootfs, spec[0]);
                    if (insidePath == null) continue;

                    String inner = "exec " + shellQuote(insidePath);
                    String command = shellQuote(prootDistro.getAbsolutePath())
                        + " login " + shellQuote(alias)
                        + " -- sh -lc " + shellQuote(inner);
                    labels.add(distroLabel + " · " + spec[1]);
                    actions.add(() -> launchDetectedEnvironment(
                        distroLabel + " · " + spec[1], command));
                }
            }
        }

        // Always keep installation/setup reachable from the same place.
        labels.add("Instalar o agregar componentes…");
        actions.add(this::showComponentInstaller);

        new AlertDialog.Builder(this)
            .setTitle("Entornos detectados")
            .setItems(labels.toArray(new String[0]), (dialog, which) -> {
                if (which >= 0 && which < actions.size()) actions.get(which).run();
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private String findPreferredOpenCodeCommand() {
        File dataDir = new File(getApplicationInfo().dataDir);
        File prefixDir = new File(dataDir, "files/usr");
        File homeDir = new File(dataDir, "files/home");
        File prootDistro = firstExistingFile(
            new File(prefixDir, "bin/proot-distro"),
            new File(prefixDir, "bin/proot-distro.sh"));

        // Prefer Debian because that is the environment prepared by NewTermux's
        // AI harness installer and where OpenCode is configured for 9router-go.
        if (prootDistro != null) {
            File rootfsBase = new File(prefixDir, "var/lib/proot-distro/installed-rootfs");
            File debian = new File(rootfsBase, "debian");
            if (debian.isDirectory()) {
                String insidePath = findDistroTool(debian, "opencode");
                if (insidePath != null) {
                    String inner = "exec " + shellQuote(insidePath);
                    return shellQuote(prootDistro.getAbsolutePath())
                        + " login debian -- sh -lc " + shellQuote(inner);
                }
            }

            File[] distros = rootfsBase.listFiles(File::isDirectory);
            if (distros != null) {
                Arrays.sort(distros, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
                for (File rootfs : distros) {
                    if ("debian".equalsIgnoreCase(rootfs.getName())) continue;
                    String insidePath = findDistroTool(rootfs, "opencode");
                    if (insidePath == null) continue;
                    String inner = "exec " + shellQuote(insidePath);
                    return shellQuote(prootDistro.getAbsolutePath())
                        + " login " + shellQuote(rootfs.getName())
                        + " -- sh -lc " + shellQuote(inner);
                }
            }
        }

        File hostTool = findHostTool(prefixDir, homeDir, "opencode");
        return hostTool == null ? null : shellQuote(hostTool.getAbsolutePath());
    }

    private void showOpenCodeSetupDialog() {
        new AlertDialog.Builder(this)
            .setTitle("OpenCode")
            .setMessage(
                "OpenCode no está instalado en un entorno detectado. "
                + "NewTermux puede instalarlo dentro de Debian PRoot.")
            .setPositiveButton("Instalar", (dialog, which) -> {
                TerminalSession session = getCurrentSession();
                if (session == null) {
                    showToast("Abrí una sesión primero", false);
                    return;
                }
                com.newtermux.features.BundledInstallerLibrary.runAiHarnessInstaller(
                    this, session, "opencode");
            })
            .setNeutralButton("Preparar IA completo", (dialog, which) -> {
                TerminalSession session = getCurrentSession();
                if (session == null) {
                    showToast("Abrí una sesión primero", false);
                    return;
                }
                com.newtermux.features.BundledInstallerLibrary.runOneTouchAiStack(
                    this, session);
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private File findHostTool(File prefixDir, File homeDir, String command) {
        File direct = firstExistingFile(
            new File(prefixDir, "bin/" + command),
            new File(homeDir, ".local/bin/" + command),
            new File(homeDir, ".opencode/bin/" + command),
            new File(homeDir, "bin/" + command));
        if (direct != null) return direct;

        // OpenCode's installer may expose the executable as opencode2.
        if ("opencode".equals(command)) {
            return firstExistingFile(
                new File(homeDir, ".opencode/bin/opencode2"),
                new File(homeDir, ".local/bin/opencode2"));
        }
        return null;
    }

    private File firstExistingFile(File... files) {
        if (files == null) return null;
        for (File file : files) {
            if (file != null && file.exists() && file.isFile()) return file;
        }
        return null;
    }

    private String findDistroTool(File rootfs, String command) {
        String[] candidates = {
            "root/.local/bin/" + command,
            "root/.opencode/bin/" + command,
            "root/bin/" + command,
            "usr/local/bin/" + command,
            "usr/bin/" + command,
            "bin/" + command
        };
        for (String relative : candidates) {
            File candidate = new File(rootfs, relative);
            if (candidate.exists() && candidate.isFile()) return "/" + relative;
        }

        // Our OpenCode installer uses ~/.opencode/bin and may name the binary
        // opencode2, so treat it as the same launchable tool.
        if ("opencode".equals(command)) {
            String[] aliases = {
                "root/.opencode/bin/opencode2",
                "root/.local/bin/opencode2",
                "usr/local/bin/opencode2",
                "usr/bin/opencode2"
            };
            for (String relative : aliases) {
                File candidate = new File(rootfs, relative);
                if (candidate.exists() && candidate.isFile()) return "/" + relative;
            }
        }
        return null;
    }

    private String friendlyEnvironmentName(String alias) {
        if (alias == null || alias.isEmpty()) return "PRoot";
        String lower = alias.toLowerCase(java.util.Locale.ROOT);
        if ("debian".equals(lower)) return "Debian";
        if ("ubuntu".equals(lower)) return "Ubuntu";
        if ("archlinux".equals(lower) || "arch".equals(lower)) return "Arch Linux";
        if ("alpine".equals(lower)) return "Alpine";
        if ("fedora".equals(lower)) return "Fedora";
        return Character.toUpperCase(alias.charAt(0)) + alias.substring(1);
    }

    private void launchDetectedEnvironment(String sessionName, String command) {
        if (mTermuxTerminalSessionActivityClient == null) {
            showToast("No hay servicio de terminal disponible", false);
            return;
        }

        mTermuxTerminalSessionActivityClient.addNewSession(false, sessionName);
        TerminalSession session = getCurrentSession();
        if (session == null) {
            showToast("No se pudo crear la sesión", false);
            return;
        }

        String line = command + "\n";
        session.write(line.getBytes(), 0, line.length());
    }

    private String shellQuote(String value) {
        if (value == null) return "''";
        return "'" + value.replace("'", "'\\''") + "'";
    }

    private void showComponentInstaller() {
        String[] labels = {
            "PRoot y distribuciones",
            "Debian en PRoot",
            "Git y SSH",
            "Python",
            "Node.js",
            "Go",
            "Herramientas de compilación",
            "Harness IA  ›"
        };
        String[] commands = {
            "pkg install proot-distro",
            "proot-distro install debian",
            "pkg install git openssh",
            "pkg install python",
            "pkg install nodejs",
            "pkg install golang",
            "pkg install clang make cmake",
            null
        };

        new AlertDialog.Builder(this)
            .setTitle("Instalar componentes")
            .setItems(labels, (dialog, index) -> {
                if (index == labels.length - 1) {
                    showHarnessInstaller();
                    return;
                }

                String command = commands[index];
                new AlertDialog.Builder(this)
                    .setTitle(labels[index])
                    .setMessage("Ejecutar en la sesión actual:\n" + command)
                    .setPositiveButton("Ejecutar", (d, w) -> {
                        TerminalSession session = getCurrentSession();
                        if (session != null) session.write(command + "\n");
                        else showToast("Abrí una sesión primero", false);
                    })
                    .setNegativeButton("Cancelar", null)
                    .show();
            })
            .setNegativeButton("Cerrar", null)
            .show();
    }

    private void showHarnessInstaller() {
        String[] labels = {
            "Diagnóstico / reparar entorno",
            "Un toque · preparar NewTermux",
            "9router-go · router local  ›",
            "Preparar Debian",
            "Antigravity CLI",
            "Codex CLI",
            "OpenCode",
            "Instalar los tres + GitHub CLI",
            "Verificar instalados"
        };
        String[] actions = {
            "doctor",
            "one-touch",
            null,
            "prepare",
            "antigravity",
            "codex",
            "opencode",
            "all",
            "status"
        };

        new AlertDialog.Builder(this)
            .setTitle("Harness IA")
            .setItems(labels, (dialog, index) -> {
                if (index == 0) {
                    new AlertDialog.Builder(this)
                        .setTitle("Diagnóstico / reparar entorno")
                        .setMessage(
                            "Comprueba el prefijo de NewTermux y repara automáticamente inconsistencias "
                            + "de paquetes base como curl/libcurl/libngtcp2, Git y dpkg. Si el entorno "
                            + "está sano no modifica nada.")
                        .setPositiveButton("Diagnosticar y reparar", (d, w) -> {
                            TerminalSession session = getCurrentSession();
                            if (session == null) {
                                showToast("Abrí una sesión primero", false);
                                return;
                            }
                            com.newtermux.features.BundledInstallerLibrary.runEnvironmentDoctor(
                                this, session, "repair");
                        })
                        .setNegativeButton("Cancelar", null)
                        .show();
                    return;
                }
                if (index == 1) {
                    new AlertDialog.Builder(this)
                        .setTitle("Un toque · preparar NewTermux")
                        .setMessage(
                            "Primero diagnostica y autorrepara el entorno. Después instala/actualiza "
                            + "9router-go, lo inicia, prepara Debian PRoot, instala/actualiza Antigravity, "
                            + "Codex, OpenCode y GitHub CLI, y configura OpenCode para usar la ruta "
                            + "coding-auto cuando el router la publique (coding-best-free como fallback). "
                            + "También comprueba si TBM ya tiene una release estable habilitada para NewTermux.\n\n"
                            + "TBM sólo se instala si pasó su gate explícito; mientras tanto se omite "
                            + "sin fallar. No inicia sesión ni importa credenciales de proveedores.")
                        .setPositiveButton("Ejecutar", (d, w) -> {
                            TerminalSession session = getCurrentSession();
                            if (session == null) {
                                showToast("Abrí una sesión primero", false);
                                return;
                            }
                            com.newtermux.features.BundledInstallerLibrary.runOneTouchAiStack(
                                this, session);
                        })
                        .setNegativeButton("Cancelar", null)
                        .show();
                    return;
                }
                if (index == 2) {
                    showRouterInstaller();
                    return;
                }

                new AlertDialog.Builder(this)
                    .setTitle(labels[index])
                    .setMessage(
                        "Se ejecutará dentro de Debian PRoot usando el instalador incluido con NewTermux. "
                        + "Las credenciales no se incluyen ni se guardan por NewTermux.\n\n"
                        + "Acción: " + actions[index])
                    .setPositiveButton("Ejecutar", (d, w) -> {
                        TerminalSession session = getCurrentSession();
                        if (session == null) {
                            showToast("Abrí una sesión primero", false);
                            return;
                        }
                        com.newtermux.features.BundledInstallerLibrary.runAiHarnessInstaller(
                            this, session, actions[index]);
                    })
                    .setNegativeButton("Cancelar", null)
                    .show();
            })
            .setNegativeButton("Volver", (d, w) -> showComponentInstaller())
            .show();
    }

    private void showRouterInstaller() {
        String[] labels = {
            "Instalar / actualizar 9router-go",
            "Iniciar 9router-go",
            "Estado de 9router-go",
            "Detener 9router-go",
            "Ahorro de tokens  ›"
        };
        String[] actions = {
            "install",
            "start",
            "status",
            "stop"
        };

        new AlertDialog.Builder(this)
            .setTitle("9router-go · router local")
            .setItems(labels, (dialog, index) -> {
                if (index == labels.length - 1) {
                    showTokenSaverMenu();
                    return;
                }

                new AlertDialog.Builder(this)
                    .setTitle(labels[index])
                    .setMessage(
                        "Nuestro fork joselofarias-byte/9router-go se administra en una copia separada de NewTermux.\n"
                        + "Puerto local predeterminado: 20130\n"
                        + "Rutas de continuidad: coding-auto / coding-best-free / free-best / free\n\n"
                        + "Acción: " + actions[index])
                    .setPositiveButton("Ejecutar", (d, w) -> runNineRouterMenuAction(actions[index]))
                    .setNegativeButton("Cancelar", null)
                    .show();
            })
            .setNegativeButton("Volver", (d, w) -> showHarnessInstaller())
            .show();
    }

    private void showTokenSaverMenu() {
        String[] labels = {
            "Estado actual",
            "Predeterminado · RTK",
            "Medio · RTK + respuestas breves",
            "Máximo · RTK + breves + código mínimo",
            "Desactivado"
        };
        String[] actions = {
            "saver-status",
            "saver-safe",
            "saver-medium",
            "saver-max",
            "saver-off"
        };
        String[] details = {
            "Muestra el perfil guardado y qué capas están activas.",
            "Sólo RTK. Comprime resultados de herramientas grandes sin pedirle al modelo que cambie su estilo de respuesta. Es la opción recomendada.",
            "RTK + Caveman. Además de comprimir resultados de herramientas, pide respuestas más breves. Puede cambiar el estilo del modelo.",
            "RTK + Caveman + Ponytail. Máximo ahorro: respuestas breves y preferencia por código mínimo/YAGNI. Útil cuando querés exprimir cuota, pero puede ser demasiado agresivo para diseño o explicación detallada.",
            "Desactiva RTK, Caveman y Ponytail. 9router-go sigue funcionando como router normal."
        };

        new AlertDialog.Builder(this)
            .setTitle("Ahorro de tokens · 9router-go")
            .setMessage(
                "El perfil se guarda en NewTermux y se aplica a los clientes que pasan por 9router-go. "
                + "Si el router fue iniciado por NewTermux, se reinicia automáticamente al cambiar el perfil.")
            .setItems(labels, (dialog, index) -> {
                if (index == 0) {
                    runNineRouterMenuAction(actions[index]);
                    return;
                }

                new AlertDialog.Builder(this)
                    .setTitle(labels[index])
                    .setMessage(details[index])
                    .setPositiveButton("Aplicar", (d, w) -> runNineRouterMenuAction(actions[index]))
                    .setNegativeButton("Cancelar", null)
                    .show();
            })
            .setNegativeButton("Volver", (d, w) -> showRouterInstaller())
            .show();
    }

    private void runNineRouterMenuAction(String action) {
        TerminalSession session = getCurrentSession();
        if (session == null && mTermuxTerminalSessionActivityClient != null) {
            mTermuxTerminalSessionActivityClient.addNewSession(false, "9router-go");
            session = getCurrentSession();
        }
        if (session == null) {
            showToast("No se pudo abrir una sesión para 9router-go", false);
            return;
        }

        com.newtermux.features.BundledInstallerLibrary.runNineRouterInstaller(
            this, session, action);
    }

    private void showTbmMenu() {
        String[] labels = {
            "Estado y gate de integración",
            "Instalar / actualizar TBM validado",
            "Abrir panel TBM",
            "Verificar instalación"
        };
        String[] actions = {
            "status",
            "install",
            "panel",
            "verify"
        };

        new AlertDialog.Builder(this)
            .setTitle("TBM · respaldo y restauración")
            .setItems(labels, (dialog, index) ->
                new AlertDialog.Builder(this)
                    .setTitle(labels[index])
                    .setMessage(
                        "NewTermux sólo instala TBM desde una release estable del repositorio "
                        + "joselofarias-byte/TBM-Recovery-Master que declare NEWTERMUX_READY=1. "
                        + "Las prereleases actuales no pasan este gate.\n\n"
                        + "Backup y restore nunca se ejecutan automáticamente; se eligen dentro del panel TBM.")
                    .setPositiveButton("Ejecutar", (d, w) -> {
                        TerminalSession session = getCurrentSession();
                        if (session == null) {
                            showToast("Abrí una sesión primero", false);
                            return;
                        }
                        com.newtermux.features.BundledInstallerLibrary.runTbmInstaller(
                            this, session, actions[index]);
                    })
                    .setNegativeButton("Cancelar", null)
                    .show()
            )
            .setNegativeButton("Cerrar", null)
            .show();
    }

    private void configureAutoOutput() {
        boolean enabled = NewTermuxSettings.isAutoSaveOutputEnabled(this);
        String message = enabled
            ? "Ahora está activado. Las salidas largas se guardan comprimidas en Descargas/NewTermux como .log.gz."
            : "Las salidas largas pueden guardarse automáticamente, comprimidas, en Descargas/NewTermux como .log.gz.";

        new AlertDialog.Builder(this)
            .setTitle("Guardar salidas largas")
            .setMessage(message)
            .setItems(new String[]{"Guardar", "No guardar"}, (dialog, which) -> {
                boolean save = which == 0;
                NewTermuxSettings.setAutoSaveOutputEnabled(this, save);
                showToast(save
                    ? "Guardado automático activado"
                    : "Guardado automático desactivado", false);
            })
            .setNegativeButton("Cancelar", null)
            .show();
    }

    private void toggleToolbarAutocorrect() {
        if (mTerminalView == null) return;
        boolean enabled = !mTerminalView.isKeyboardSuggestionsEnabled();
        mTerminalView.setKeyboardSuggestionsEnabled(enabled);
        if (mAutoCorrectHandler != null) mAutoCorrectHandler.setEnabled(enabled);
        NewTermuxSettings.setKeyboardSuggestions(this, enabled);

        TextView ac = findViewById(R.id.btn_autocorrect_toggle);
        if (ac != null) {
            int color = getResources().getColor(
                enabled ? R.color.nt_primary : R.color.nt_on_surface, getTheme());
            ac.setTextColor(color);
        }
    }

    /** Update the horizontal session pip row with live mini terminal previews. */
    public void updateSessionTabs() {
        if (mSessionPipContainer == null || mTermuxService == null) return;

        mSessionPipContainer.removeAllViews();
        java.util.List<com.termux.shared.termux.shell.command.runner.terminal.TermuxSession> sessions = mTermuxService.getTermuxSessions();
        TerminalSession currentSession = getCurrentSession();

        float density   = getResources().getDisplayMetrics().density;
        int pipWidthPx  = (int) (density * 86);
        int pipHeightPx = (int) (density * 58);
        int marginPx    = (int) (density * 4);
        int labelGapPx  = (int) (density * 2);

        for (int i = 0; i < sessions.size(); i++) {
            com.termux.shared.termux.shell.command.runner.terminal.TermuxSession termuxSession = sessions.get(i);
            TerminalSession session = termuxSession.getTerminalSession();

            // Vertical wrapper: name label on top, live pip below
            LinearLayout wrapper = new LinearLayout(this);
            wrapper.setOrientation(LinearLayout.VERTICAL);
            wrapper.setGravity(Gravity.CENTER_HORIZONTAL);
            LinearLayout.LayoutParams wrapperLp = new LinearLayout.LayoutParams(pipWidthPx, LinearLayout.LayoutParams.WRAP_CONTENT);
            wrapperLp.setMarginEnd(marginPx);
            wrapper.setLayoutParams(wrapperLp);
            wrapper.setTag(session);

            // Session name label
            android.widget.TextView nameLabel = new android.widget.TextView(this);
            String displayName = (session.mSessionName != null && !session.mSessionName.isEmpty())
                    ? session.mSessionName : "#" + (i + 1);
            nameLabel.setText(displayName);
            nameLabel.setTextSize(10f);
            nameLabel.setTextColor(0xFFAAAAAA);
            nameLabel.setMaxLines(1);
            nameLabel.setEllipsize(android.text.TextUtils.TruncateAt.END);
            nameLabel.setGravity(Gravity.CENTER_HORIZONTAL);
            LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            nameLp.bottomMargin = labelGapPx;
            nameLabel.setLayoutParams(nameLp);
            wrapper.addView(nameLabel);

            // Live pip preview
            MiniTerminalPipView pip = new MiniTerminalPipView(this);
            pip.setSession(session);
            pip.setActive(session == currentSession);
            pip.setLayoutParams(new LinearLayout.LayoutParams(pipWidthPx, pipHeightPx));
            wrapper.addView(pip);

            wrapper.setOnClickListener(v -> {
                if (mTermuxTerminalSessionActivityClient != null) {
                    // setCurrentSession() already rebuilds the miniature row before showing
                    // the notice. Rebuilding it again here detached the popup anchor and
                    // made notices for #1/#2/#3 jump to the upper-left corner.
                    mTermuxTerminalSessionActivityClient.setCurrentSession(session);
                }
            });

            wrapper.setOnLongClickListener(v -> {
                showSessionPopupMenu(v, session);
                return true;
            });

            mSessionPipContainer.addView(wrapper);
        }
    }

    /**
     * Show a compact transient notice anchored to the session miniature that caused it.
     * This keeps "[N]" session-change notices away from the main toolbar controls.
     */
    public void showSessionNotice(TerminalSession session, String text, boolean longDuration) {
        if (text == null || text.isEmpty()) return;

        mSessionNoticeHandler.removeCallbacks(mDismissSessionNotice);
        dismissSessionNotice();

        // The row may have just been rebuilt by setCurrentSession(). Wait until Android has
        // measured and positioned the new miniature before calculating popup coordinates.
        if (mSessionPipContainer == null) {
            showToast(text, longDuration);
            return;
        }

        mSessionPipContainer.post(() -> showSessionNoticeAnchored(session, text, longDuration, 0));
    }

    private void showSessionNoticeAnchored(TerminalSession session, String text,
                                           boolean longDuration, int attempt) {
        if (!mIsVisible) return;

        View anchor = findSessionNoticeAnchor(session);
        if (anchor == null || !anchor.isShown() || anchor.getWidth() <= 0 || anchor.getHeight() <= 0) {
            if (mSessionPipContainer != null && attempt < 4) {
                mSessionNoticeHandler.postDelayed(
                    () -> showSessionNoticeAnchored(session, text, longDuration, attempt + 1),
                    32L);
                return;
            }
            // Last-resort fallback only. Normal session switches should never reach this path.
            showToast(text, longDuration);
            return;
        }

        float density = getResources().getDisplayMetrics().density;
        int horizontalPadding = Math.round(12 * density);
        int verticalPadding = Math.round(7 * density);
        int edgeMargin = Math.round(8 * density);
        int gap = Math.round(6 * density);

        TextView label = new TextView(this);
        label.setText(text);
        label.setTextColor(androidx.core.content.ContextCompat.getColor(
            this, com.termux.R.color.nt_on_surface));
        label.setTextSize(13f);
        label.setGravity(Gravity.CENTER);
        label.setMaxLines(2);
        label.setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding);
        label.setBackgroundResource(com.termux.R.drawable.bg_popup_menu);

        View decor = getWindow().getDecorView();
        int screenWidth = Math.max(decor.getWidth(), getResources().getDisplayMetrics().widthPixels);
        int maxWidth = Math.max(1, screenWidth - 2 * edgeMargin);
        label.setMaxWidth(Math.min(maxWidth, Math.round(280 * density)));
        label.measure(
            View.MeasureSpec.makeMeasureSpec(maxWidth, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));

        int[] anchorLocation = new int[2];
        int[] decorLocation = new int[2];
        anchor.getLocationOnScreen(anchorLocation);
        decor.getLocationOnScreen(decorLocation);

        int popupWidth = Math.max(1, label.getMeasuredWidth());
        int desiredLeft = anchorLocation[0] + (anchor.getWidth() - popupWidth) / 2;
        int clampedLeft = Math.max(edgeMargin,
            Math.min(desiredLeft, screenWidth - edgeMargin - popupWidth));

        int popupX = clampedLeft - decorLocation[0];
        int popupY = anchorLocation[1] + anchor.getHeight() + gap - decorLocation[1];

        PopupWindow popup = new PopupWindow(
            label,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            false);
        popup.setClippingEnabled(true);
        popup.setOutsideTouchable(false);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP)
            popup.setElevation(8 * density);

        try {
            // Absolute window placement is deliberate: unlike showAsDropDown(), it remains
            // stable even if the HorizontalScrollView relayouts after a session switch.
            popup.showAtLocation(decor, Gravity.TOP | Gravity.START, popupX, popupY);
            mSessionNoticePopup = popup;
            mSessionNoticeHandler.postDelayed(
                mDismissSessionNotice,
                longDuration ? 2800L : 1600L);
        } catch (WindowManager.BadTokenException e) {
            showToast(text, longDuration);
        }
    }

    private View findSessionNoticeAnchor(TerminalSession session) {
        if (session == null || mSessionPipContainer == null) return null;
        for (int i = 0; i < mSessionPipContainer.getChildCount(); i++) {
            View child = mSessionPipContainer.getChildAt(i);
            if (child != null && child.getTag() == session) return child;
        }
        return null;
    }

    private void dismissSessionNotice() {
        if (mSessionNoticePopup != null) {
            try {
                mSessionNoticePopup.dismiss();
            } catch (Exception ignored) {
            }
            mSessionNoticePopup = null;
        }
    }

    private void showSessionPopupMenu(View anchor, TerminalSession session) {
        // Session-scoped actions live on the miniature itself so cleanup does not get
        // buried in a global menu.
        com.newtermux.features.NtPopupMenu.showAsDropDown(this, anchor, null,
            new String[]{
                getString(R.string.action_rename),
                getString(R.string.action_close),
                getString(R.string.action_close_others),
                getString(R.string.action_close_finished_others),
                getString(R.string.action_close_all)
            }, idx -> {
                switch (idx) {
                    case 0:
                        if (mTermuxTerminalSessionActivityClient != null)
                            mTermuxTerminalSessionActivityClient.renameSession(session);
                        break;
                    case 1:
                        confirmCloseSession(session);
                        break;
                    case 2:
                        confirmCloseOtherSessions(session);
                        break;
                    case 3:
                        closeFinishedOtherSessions(session);
                        break;
                    case 4:
                        confirmCloseAllSessions();
                        break;
                    default:
                        break;
                }
            });
    }

    private void confirmCloseAllSessions() {
        if (mTermuxService == null) return;
        new AlertDialog.Builder(this)
            .setTitle(R.string.action_close_all)
            .setMessage(R.string.msg_close_all_sessions)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.action_close_all, (dialog, which) -> {
                // Stop the service using its normal session/process cleanup path.
                Intent stop = new Intent(this, TermuxService.class);
                stop.setAction(TermuxConstants.TERMUX_APP.TERMUX_SERVICE.ACTION_STOP_SERVICE);
                startService(stop);
            })
            .show();
    }

    private void confirmCloseOtherSessions(TerminalSession keepSession) {
        if (keepSession == null || mTermuxService == null) return;

        List<com.termux.shared.termux.shell.command.runner.terminal.TermuxSession> snapshot =
            new ArrayList<>(mTermuxService.getTermuxSessions());
        int count = 0;
        for (com.termux.shared.termux.shell.command.runner.terminal.TermuxSession item : snapshot) {
            TerminalSession terminal = item == null ? null : item.getTerminalSession();
            if (terminal != null && terminal != keepSession) count++;
        }

        if (count == 0) {
            showToast(getString(R.string.msg_no_other_sessions), false);
            return;
        }

        final int closeCount = count;
        new AlertDialog.Builder(this)
            .setTitle(R.string.title_close_other_sessions)
            .setMessage(getResources().getQuantityString(
                R.plurals.msg_close_other_sessions, closeCount, closeCount))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.action_close_others, (dialog, which) -> {
                List<com.termux.shared.termux.shell.command.runner.terminal.TermuxSession> current =
                    new ArrayList<>(mTermuxService.getTermuxSessions());
                for (com.termux.shared.termux.shell.command.runner.terminal.TermuxSession item : current) {
                    TerminalSession terminal = item == null ? null : item.getTerminalSession();
                    if (terminal == null || terminal == keepSession) continue;
                    terminal.finishIfRunning();
                    if (mTermuxTerminalSessionActivityClient != null)
                        mTermuxTerminalSessionActivityClient.removeFinishedSession(terminal);
                }
                if (mTermuxTerminalSessionActivityClient != null)
                    mTermuxTerminalSessionActivityClient.setCurrentSession(keepSession);
                updateSessionTabs();
            })
            .show();
    }

    private void closeFinishedOtherSessions(TerminalSession keepSession) {
        if (keepSession == null || mTermuxService == null ||
            mTermuxTerminalSessionActivityClient == null) return;

        List<com.termux.shared.termux.shell.command.runner.terminal.TermuxSession> snapshot =
            new ArrayList<>(mTermuxService.getTermuxSessions());
        int removed = 0;
        for (com.termux.shared.termux.shell.command.runner.terminal.TermuxSession item : snapshot) {
            TerminalSession terminal = item == null ? null : item.getTerminalSession();
            if (terminal == null || terminal == keepSession || terminal.isRunning()) continue;
            mTermuxTerminalSessionActivityClient.removeFinishedSession(terminal);
            removed++;
        }

        mTermuxTerminalSessionActivityClient.setCurrentSession(keepSession);
        updateSessionTabs();
        showToast(removed == 0
            ? getString(R.string.msg_no_finished_sessions)
            : getResources().getQuantityString(R.plurals.msg_finished_sessions_closed, removed, removed),
            false);
    }

    private void confirmCloseSession(TerminalSession session) {
        if (session == null) return;

        new AlertDialog.Builder(this)
            .setTitle(R.string.title_confirm_close_session)
            .setMessage(R.string.msg_confirm_close_session)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.action_close_session, (dialog, which) -> {
                session.finishIfRunning();
                if (mTermuxTerminalSessionActivityClient != null)
                    mTermuxTerminalSessionActivityClient.removeFinishedSession(session);
            })
            .show();
    }

    /** Coalesce raw PTY bursts into at most four status redraws per second. */
    public void scheduleTaskMonitorRefresh(TerminalSession session) {
        if (session == null || session != getCurrentSession() || mTaskMonitorRefreshPending) return;
        mTaskMonitorRefreshPending = true;
        mTaskMonitorHandler.postDelayed(() -> {
            mTaskMonitorRefreshPending = false;
            refreshTaskMonitor();
        }, 250L);
    }

    private void startTaskMonitorTicker() {
        mTaskMonitorHandler.removeCallbacks(mTaskMonitorTicker);
        refreshTaskMonitor();
        if (mIsVisible) mTaskMonitorHandler.postDelayed(mTaskMonitorTicker, 1000L);
    }

    public void refreshTaskMonitor() {
        if (mTaskStatusPanel == null) return;
        if (isLandscapeMode()) {
            mTaskStatusPanel.setVisibility(View.GONE);
            return;
        }
        TerminalSession session = getCurrentSession();
        TerminalTaskMonitor.Snapshot snapshot = TerminalTaskMonitor.snapshot(session, true);
        long now = System.currentTimeMillis();

        if (snapshot == null || !snapshot.shouldDisplay(now)) {
            mTaskStatusPanel.setVisibility(View.GONE);
            return;
        }

        mTaskStatusPanel.setVisibility(View.VISIBLE);

        String health = taskHealthLabel(snapshot);
        if (mTaskStatusTitle != null)
            mTaskStatusTitle.setText(snapshot.title + " · " + health);

        StringBuilder detail = new StringBuilder(snapshot.phase);
        if (!DataUtils.isNullOrEmpty(snapshot.item)) detail.append(" · ").append(snapshot.item);
        if (snapshot.completedItems > 0) {
            detail.append(" · ").append(snapshot.completedItems);
            if (snapshot.totalItems > 0) detail.append("/").append(snapshot.totalItems);
            detail.append(" paquetes");
        }
        if (mTaskStatusDetail != null) mTaskStatusDetail.setText(detail.toString());

        if (mTaskStatusElapsed != null)
            mTaskStatusElapsed.setText(TerminalTaskMonitor.formatDuration(snapshot.elapsedMs(now)));

        if (mTaskStatusProgress != null) {
            if (snapshot.percent >= 0) {
                mTaskStatusProgress.setIndeterminate(false);
                mTaskStatusProgress.setProgress(snapshot.percent);
            } else {
                mTaskStatusProgress.setIndeterminate(snapshot.health == TerminalTaskMonitor.Health.ACTIVE);
                if (!mTaskStatusProgress.isIndeterminate()) mTaskStatusProgress.setProgress(0);
            }
        }

        if (mTaskStatusDot != null) {
            int colorRes;
            switch (snapshot.health) {
                case ACTIVE:
                case FINISHED:
                    colorRes = android.R.color.holo_green_light;
                    break;
                case WARNING:
                    colorRes = android.R.color.holo_red_light;
                    break;
                case QUIET:
                case SLOW:
                    colorRes = android.R.color.holo_orange_light;
                    break;
                case STALLED:
                case FAILED:
                default:
                    colorRes = android.R.color.holo_red_light;
                    break;
            }
            mTaskStatusDot.setTextColor(ContextCompat.getColor(this, colorRes));
        }
    }

    private String taskHealthLabel(TerminalTaskMonitor.Snapshot snapshot) {
        switch (snapshot.health) {
            case ACTIVE:
                return snapshot.outputAgeMs(System.currentTimeMillis()) > 10_000L
                    ? "trabajando sin salida" : "trabajando";
            case WARNING:
                return "error reciente";
            case QUIET:
                return "sin salida reciente";
            case SLOW:
                return "lento";
            case STALLED:
                return "posible bloqueo";
            case FINISHED:
                return "terminado";
            case FAILED:
            default:
                return "falló";
        }
    }

    private void showTaskMonitorDetails() {
        TerminalTaskMonitor.Snapshot snapshot =
            TerminalTaskMonitor.snapshot(getCurrentSession(), true);
        if (snapshot == null) return;

        long now = System.currentTimeMillis();
        String progress = snapshot.percent >= 0 ? snapshot.percent + "%" : "sin porcentaje fiable";
        String lastOutput = snapshot.outputAgeMs(now) == Long.MAX_VALUE
            ? "sin datos"
            : "hace " + TerminalTaskMonitor.formatDuration(snapshot.outputAgeMs(now));
        String process = DataUtils.isNullOrEmpty(snapshot.processName)
            ? "no disponible" : snapshot.processName;
        String free = TerminalTaskMonitor.formatBytes(getFilesDir().getFreeSpace());

        StringBuilder message = new StringBuilder();
        message.append("Estado: ").append(taskHealthLabel(snapshot)).append("\n");
        message.append("Fase: ").append(snapshot.phase).append("\n");
        if (!DataUtils.isNullOrEmpty(snapshot.item))
            message.append("Elemento actual: ").append(snapshot.item).append("\n");
        message.append("Progreso: ").append(progress);
        if (snapshot.completedItems > 0) {
            message.append(" · ").append(snapshot.completedItems);
            if (snapshot.totalItems > 0) message.append("/").append(snapshot.totalItems);
            message.append(" paquetes");
        }
        message.append("\n");
        message.append("Tiempo: ")
            .append(TerminalTaskMonitor.formatDuration(snapshot.elapsedMs(now))).append("\n");
        message.append("Última salida: ").append(lastOutput).append("\n");
        message.append("Proceso: ").append(process).append("\n");
        if (snapshot.rootPid > 0) message.append("PID raíz: ").append(snapshot.rootPid).append("\n");
        if (snapshot.processCount > 0)
            message.append("Procesos observados: ").append(snapshot.processCount).append("\n");
        message.append("CPU/I/O: ")
            .append(snapshot.processActive ? "con actividad" : "sin cambio en la última muestra").append("\n");
        if (snapshot.rssBytes > 0)
            message.append("RAM de procesos: ")
                .append(TerminalTaskMonitor.formatBytes(snapshot.rssBytes)).append("\n");
        if (snapshot.writeBytes > 0)
            message.append("Escritura acumulada: ")
                .append(TerminalTaskMonitor.formatBytes(snapshot.writeBytes)).append("\n");
        message.append("Salida recibida: ")
            .append(TerminalTaskMonitor.formatBytes(snapshot.bytes))
            .append(" · ").append(snapshot.lines).append(" líneas\n");
        message.append("Espacio libre: ").append(free);
        if (!DataUtils.isNullOrEmpty(snapshot.warningLine))
            message.append("\n\nError reciente detectado:\n").append(snapshot.warningLine);
        if (!DataUtils.isNullOrEmpty(snapshot.lastLine))
            message.append("\n\nÚltima línea:\n").append(snapshot.lastLine);

        new AlertDialog.Builder(this)
            .setTitle(snapshot.title)
            .setMessage(message.toString())
            .setPositiveButton(android.R.string.ok, null)
            .show();
    }


    /**
     * Notify the pip for a specific session to redraw (called from onTextChanged).
     * Only updates the matching pip without rebuilding the whole row.
     */
    public void notifyPipUpdate(TerminalSession session) {
        if (mSessionPipContainer == null) return;
        for (int i = 0; i < mSessionPipContainer.getChildCount(); i++) {
            android.view.View child = mSessionPipContainer.getChildAt(i);
            if (child instanceof ViewGroup) {
                ViewGroup wrapper = (ViewGroup) child;
                for (int j = 0; j < wrapper.getChildCount(); j++) {
                    android.view.View inner = wrapper.getChildAt(j);
                    if (inner instanceof MiniTerminalPipView) {
                        MiniTerminalPipView pip = (MiniTerminalPipView) inner;
                        if (pip.getSession() == session) {
                            pip.notifyUpdate();
                            return;
                        }
                    }
                }
            }
        }
    }

    public void onSTTButtonClicked() {
        if (mSpeechInputManager == null) return;
        if (mSpeechInputManager.isListening()) {
            mSpeechInputManager.stopListening();
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO);
        } else {
            mSpeechInputManager.startListening();
        }
    }

    public void checkForAutocorrect(String text) {
        if (mAutoCorrectHandler == null) return;
        if (text == null || text.trim().isEmpty()) {
            hideAutocorrectBar();
            return;
        }
        String corrected = mAutoCorrectHandler.getCommandCorrection(text);
        if (corrected != null && !corrected.equals(text)) {
            mPendingCorrection = corrected;
            mPendingOriginal = text;
            runOnUiThread(() -> {
                if (mAutocorrectBar != null && mAutocorrectText != null) {
                    mAutocorrectText.setText(getString(R.string.autocorrect_suggestion, corrected));
                    mAutocorrectBar.setVisibility(View.VISIBLE);
                }
            });
        } else {
            // If it's a small word or we are done typing, maybe keep it.
            // For now, only hide if no correction found
            if (text.length() > 3) hideAutocorrectBar();
        }
    }

    private void applyAutocorrect() {
        if (mPendingCorrection == null || mPendingOriginal == null) return;
        TerminalSession session = getCurrentSession();
        if (session != null) {
            // Send backspaces to delete the original word and the trailing space
            StringBuilder backspaces = new StringBuilder();
            for (int i = 0; i < mPendingOriginal.length() + 1; i++) {
                backspaces.append('\177'); // DEL character for backspace
            }
            byte[] bsBytes = backspaces.toString().getBytes();
            session.write(bsBytes, 0, bsBytes.length);

            // Send the corrected command
            byte[] bytes = mPendingCorrection.getBytes();
            session.write(bytes, 0, bytes.length);
        }
        hideAutocorrectBar();
    }

    private void hideAutocorrectBar() {
        mPendingCorrection = null;
        runOnUiThread(() -> {
            if (mAutocorrectBar != null) mAutocorrectBar.setVisibility(View.GONE);
        });
    }

    private void updateSTTButtonState(boolean isListening) {
        if (mBtnSTT == null) return;
        mBtnSTT.setImageResource(isListening ? R.drawable.ic_mic_off : R.drawable.ic_mic);
        int color = getResources().getColor(
            isListening ? R.color.nt_stt_listening : R.color.nt_stt_idle, getTheme());
        mBtnSTT.setColorFilter(color);
    }

    private void insertSpeechTextToTerminal(String text) {
        if (text == null || text.isEmpty()) return;
        // Check autocorrect for known command typos
        String corrected = mAutoCorrectHandler != null
            ? mAutoCorrectHandler.getCommandCorrection(text) : null;
        String toInsert = corrected != null ? corrected : text;
        // Write to the active terminal session
        TerminalSession session = getCurrentSession();
        if (session != null) {
            byte[] bytes = toInsert.getBytes();
            session.write(bytes, 0, bytes.length);
        }
    }

    // -----------------------------------------------------------------------------------------

    private static final String DRAWER_PREFS = "newtermux_drawer_buttons";
    private static final String[] DRAWER_BTN_DEFAULT_NAMES = {"Gemini Yolo", "Claude", "", "", ""};
    private static final String[] DRAWER_BTN_DEFAULT_CMDS  = {
        "gemini --yolo",
        "claude --dangerously-skip-permissions",
        "", "", ""
    };

    private void setupDrawerCommandButtons() {
        SharedPreferences prefs = getSharedPreferences(DRAWER_PREFS, MODE_PRIVATE);
        int count = prefs.getInt("btn_count", DRAWER_BTN_DEFAULT_NAMES.length);

        LinearLayout container = findViewById(R.id.drawer_cmd_container);
        if (container == null) return;
        container.removeAllViews();

        int accentColor = NewTermuxTheme.getAccentColor(this);
        android.content.res.ColorStateList accentCsl =
            android.content.res.ColorStateList.valueOf(accentColor);

        int marginBtm = Math.round(6 * getResources().getDisplayMetrics().density);

        // --- Utility buttons (always at top, unless toggled off in Settings) ---
        boolean showExportScript = NewTermuxSettings.isShowDrawerExportScript(this);
        boolean showPkgUpdate    = NewTermuxSettings.isShowDrawerPkgUpdate(this);
        boolean anyUtility = showExportScript || showPkgUpdate;

        if (showExportScript) {
            MaterialButton exportBtn = new MaterialButton(this,
                null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = marginBtm;
            exportBtn.setLayoutParams(lp);
            exportBtn.setStrokeColor(accentCsl);
            exportBtn.setTextColor(accentColor);
            exportBtn.setText(R.string.drawer_export_screen);
            exportBtn.setOnClickListener(v -> {
                getDrawer().closeDrawers();
                mScreenExportSaver.launch("screen.txt");
            });
            container.addView(exportBtn);

            MaterialButton scriptBtn = new MaterialButton(this,
                null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
            LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp2.bottomMargin = marginBtm;
            scriptBtn.setLayoutParams(lp2);
            scriptBtn.setStrokeColor(accentCsl);
            scriptBtn.setTextColor(accentColor);
            scriptBtn.setText(R.string.drawer_make_script);
            scriptBtn.setOnClickListener(v -> {
                getDrawer().closeDrawers();
                showMakeScriptDialog();
            });
            container.addView(scriptBtn);
        }

        if (showPkgUpdate) {
            MaterialButton pkgBtn = new MaterialButton(this,
                null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = marginBtm;
            pkgBtn.setLayoutParams(lp);
            pkgBtn.setStrokeColor(accentCsl);
            pkgBtn.setTextColor(accentColor);
            pkgBtn.setText(R.string.drawer_pkg_update);
            pkgBtn.setOnClickListener(v -> {
                getDrawer().closeDrawers();
                TerminalSession s = getCurrentSession();
                if (s != null) {
                    byte[] cmdBytes = "pkg update -y\n".getBytes();
                    s.write(cmdBytes, 0, cmdBytes.length);
                }
            });
            container.addView(pkgBtn);
        }

        // Divider between utility and custom buttons
        if (anyUtility) {
            android.view.View divider = new android.view.View(this);
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Math.round(1 * getResources().getDisplayMetrics().density));
            dlp.topMargin = Math.round(2 * getResources().getDisplayMetrics().density);
            dlp.bottomMargin = marginBtm;
            divider.setLayoutParams(dlp);
            divider.setBackgroundColor((accentColor & 0x00FFFFFF) | 0x55000000); // 33% alpha accent
            container.addView(divider);
        }

        // --- Custom command buttons ---
        if (!NewTermuxSettings.isShowDrawerCmdButtons(this)) return;
        for (int i = 0; i < count; i++) {
            final int idx = i;
            String defName = idx < DRAWER_BTN_DEFAULT_NAMES.length ? DRAWER_BTN_DEFAULT_NAMES[idx] : "";
            String defCmd  = idx < DRAWER_BTN_DEFAULT_CMDS.length  ? DRAWER_BTN_DEFAULT_CMDS[idx]  : "";
            String name = prefs.getString("btn_" + (i + 1) + "_name", defName);
            String cmd  = prefs.getString("btn_" + (i + 1) + "_cmd",  defCmd);

            MaterialButton btn = new MaterialButton(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = marginBtm;
            btn.setLayoutParams(lp);
            btn.setBackgroundTintList(accentCsl);

            boolean isUnset = name.isEmpty() && cmd.isEmpty();
            btn.setText(isUnset ? getString(R.string.drawer_long_press_to_set) : (name.isEmpty() ? getString(R.string.drawer_button_number, i + 1) : name));
            if (isUnset) btn.setAlpha(0.5f);

            btn.setOnClickListener(v -> {
                String c = prefs.getString("btn_" + (idx + 1) + "_cmd",
                    idx < DRAWER_BTN_DEFAULT_CMDS.length ? DRAWER_BTN_DEFAULT_CMDS[idx] : "");
                String n = prefs.getString("btn_" + (idx + 1) + "_name",
                    idx < DRAWER_BTN_DEFAULT_NAMES.length ? DRAWER_BTN_DEFAULT_NAMES[idx] : "");
                String label = n.isEmpty() ? getString(R.string.drawer_button_number, idx + 1) : n;
                if (mTermuxTerminalSessionActivityClient != null) {
                    mTermuxTerminalSessionActivityClient.addNewSession(false, label);
                    if (!c.isEmpty()) {
                        final String finalCmd = c + "\n";
                        mTerminalView.post(() -> {
                            TerminalSession s = getCurrentSession();
                            if (s != null) {
                                byte[] finalBytes = finalCmd.getBytes();
                                s.write(finalBytes, 0, finalBytes.length);
                            }
                        });
                    }
                }
            });
            btn.setOnLongClickListener(v -> {
                showEditDrawerButtonDialog(idx);
                return true;
            });
            container.addView(btn);
        }

        MaterialButton editShortcutsBtn = new MaterialButton(this,
            null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        LinearLayout.LayoutParams editLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        editLp.bottomMargin = marginBtm;
        editShortcutsBtn.setLayoutParams(editLp);
        editShortcutsBtn.setStrokeColor(accentCsl);
        editShortcutsBtn.setTextColor(accentColor);
        editShortcutsBtn.setText(R.string.drawer_edit_shortcuts);
        editShortcutsBtn.setOnClickListener(v -> showDrawerShortcutEditor());
        container.addView(editShortcutsBtn);

        // +/- controls — apply accent stroke/text color
        MaterialButton addBtn    = findViewById(R.id.drawer_btn_add);
        MaterialButton removeBtn = findViewById(R.id.drawer_btn_remove);
        if (addBtn != null) {
            addBtn.setStrokeColor(accentCsl);
            addBtn.setTextColor(accentColor);
            addBtn.setOnClickListener(v -> {
                int cur = prefs.getInt("btn_count", DRAWER_BTN_DEFAULT_NAMES.length);
                if (cur < 10) {
                    prefs.edit().putInt("btn_count", cur + 1).apply();
                    setupDrawerCommandButtons();
                }
            });
        }
        if (removeBtn != null) {
            removeBtn.setStrokeColor(accentCsl);
            removeBtn.setTextColor(accentColor);
            removeBtn.setOnClickListener(v -> {
                int cur = prefs.getInt("btn_count", DRAWER_BTN_DEFAULT_NAMES.length);
                if (cur > 1) {
                    // Clear saved values for the slot being removed so it starts fresh if re-added
                    prefs.edit()
                        .remove("btn_" + cur + "_name")
                        .remove("btn_" + cur + "_cmd")
                        .putInt("btn_count", cur - 1)
                        .apply();
                    setupDrawerCommandButtons();
                }
            });
        }
    }

    private void showDrawerShortcutEditor() {
        SharedPreferences prefs = getSharedPreferences(DRAWER_PREFS, MODE_PRIVATE);
        int count = prefs.getInt("btn_count", DRAWER_BTN_DEFAULT_NAMES.length);
        String[] labels = new String[count];

        for (int i = 0; i < count; i++) {
            String defName = i < DRAWER_BTN_DEFAULT_NAMES.length ? DRAWER_BTN_DEFAULT_NAMES[i] : "";
            String name = prefs.getString("btn_" + (i + 1) + "_name", defName);
            labels[i] = name == null || name.trim().isEmpty()
                ? getString(R.string.drawer_button_number, i + 1)
                : name;
        }

        new AlertDialog.Builder(this)
            .setTitle(R.string.drawer_edit_shortcuts)
            .setItems(labels, (dialog, which) -> showEditDrawerButtonDialog(which))
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void showEditDrawerButtonDialog(int idx) {
        SharedPreferences prefs = getSharedPreferences(DRAWER_PREFS, MODE_PRIVATE);
        String defName = idx < DRAWER_BTN_DEFAULT_NAMES.length ? DRAWER_BTN_DEFAULT_NAMES[idx] : "";
        String defCmd  = idx < DRAWER_BTN_DEFAULT_CMDS.length  ? DRAWER_BTN_DEFAULT_CMDS[idx]  : "";
        String currentName = prefs.getString("btn_" + (idx + 1) + "_name", defName);
        String currentCmd  = prefs.getString("btn_" + (idx + 1) + "_cmd",  defCmd);

        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        int pad = Math.round(16 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, 0);

        EditText nameField = new EditText(this);
        nameField.setHint(R.string.drawer_button_label_hint);
        nameField.setText(currentName);
        layout.addView(nameField);

        EditText cmdField = new EditText(this);
        cmdField.setHint(R.string.drawer_command_hint);
        cmdField.setText(currentCmd);
        layout.addView(cmdField);

        new AlertDialog.Builder(this)
            .setTitle(getString(R.string.drawer_edit_button_title, idx + 1))
            .setView(layout)
            .setPositiveButton(R.string.action_save, (d, w) -> {
                String newName = nameField.getText().toString().trim();
                String newCmd  = cmdField.getText().toString().trim();
                prefs.edit()
                    .putString("btn_" + (idx + 1) + "_name", newName)
                    .putString("btn_" + (idx + 1) + "_cmd",  newCmd)
                    .apply();
                setupDrawerCommandButtons();
            })
            .setNeutralButton(R.string.action_reset, (d, w) -> {
                prefs.edit()
                    .remove("btn_" + (idx + 1) + "_name")
                    .remove("btn_" + (idx + 1) + "_cmd")
                    .apply();
                setupDrawerCommandButtons();
            })
            .setNegativeButton(R.string.action_cancel, null)
            .show();
    }





    private void showMakeScriptDialog() {
        new Thread(() -> {
            // Try zsh_history first, fall back to bash_history
            File histFile = new File(TermuxConstants.TERMUX_HOME_DIR_PATH, ".zsh_history");
            if (!histFile.exists()) histFile = new File(TermuxConstants.TERMUX_HOME_DIR_PATH, ".bash_history");
            if (!histFile.exists()) {
                runOnUiThread(() -> Toast.makeText(this, getString(R.string.msg_no_history_file), Toast.LENGTH_SHORT).show());
                return;
            }

            // Parse history: deduplicate, newest-first, cap at 100
            LinkedHashMap<String, String> seen = new LinkedHashMap<>();
            try (BufferedReader br = new BufferedReader(new FileReader(histFile))) {
                String line;
                while ((line = br.readLine()) != null) {
                    // Strip zsh extended format: ": timestamp:elapsed;command"
                    if (line.startsWith(": ") && line.contains(";")) {
                        line = line.substring(line.indexOf(';') + 1);
                    }
                    line = line.trim();
                    if (!line.isEmpty()) seen.put(line, line);
                }
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, getString(R.string.msg_history_read_failed), Toast.LENGTH_SHORT).show());
                return;
            }

            // Reverse so newest entries appear first, cap at 100
            List<String> entries = new ArrayList<>(seen.values());
            java.util.Collections.reverse(entries);
            if (entries.size() > 100) entries = entries.subList(0, 100);

            final List<String> finalEntries = entries;
            final boolean[] checked = new boolean[finalEntries.size()];

            runOnUiThread(() -> {
                CharSequence[] items = finalEntries.toArray(new CharSequence[0]);
                new AlertDialog.Builder(this)
                    .setTitle(R.string.title_make_script)
                    .setMultiChoiceItems(items, checked, (d, which, isChecked) -> checked[which] = isChecked)
                    .setPositiveButton(R.string.action_save_script, (d, w) -> {
                        // Collect in chronological order (reverse of display order)
                        List<String> selected = new ArrayList<>();
                        for (int i = checked.length - 1; i >= 0; i--) {
                            if (checked[i]) selected.add(finalEntries.get(i));
                        }
                        if (selected.isEmpty()) {
                            Toast.makeText(this, getString(R.string.msg_no_commands_selected), Toast.LENGTH_SHORT).show();
                            return;
                        }
                        mPendingScriptLines.clear();
                        mPendingScriptLines.addAll(selected);
                        mScriptSaver.launch("script.sh");
                    })
                    .setNegativeButton(R.string.action_cancel, null)
                    .show();
            });
        }).start();
    }

    @SuppressLint("RtlHardcoded")
    @Override
    public void onBackPressed() {
        DrawerLayout drawer = getDrawer();
        if (drawer.isDrawerOpen(Gravity.LEFT) || drawer.isDrawerOpen(Gravity.RIGHT)) {
            drawer.closeDrawers();
        } else {
            finishActivityIfNotFinishing();
        }
    }

    public void finishActivityIfNotFinishing() {
        // prevent duplicate calls to finish() if called from multiple places
        if (!TermuxActivity.this.isFinishing()) {
            finish();
        }
    }

    /** Show a toast and dismiss the last one if still visible. */
    public void showToast(String text, boolean longDuration) {
        if (text == null || text.isEmpty()) return;
        if (mLastToast != null) mLastToast.cancel();
        mLastToast = Toast.makeText(TermuxActivity.this, text, longDuration ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT);
        mLastToast.setGravity(Gravity.TOP, 0, 0);
        // Bannerlator-style outlined banner for the top-of-screen session-activity notice.
        float density = getResources().getDisplayMetrics().density;
        android.widget.TextView tv = new android.widget.TextView(this);
        tv.setText(text);
        tv.setTextColor(androidx.core.content.ContextCompat.getColor(this, com.termux.R.color.nt_on_surface));
        tv.setTextSize(14f);
        int ph = (int) (20 * density), pv = (int) (12 * density);
        tv.setPadding(ph, pv, ph, pv);
        tv.setBackgroundResource(com.termux.R.drawable.bg_popup_menu);
        mLastToast.setView(tv);
        mLastToast.show();
    }



    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        TerminalSession currentSession = getCurrentSession();
        if (currentSession == null) return;

        boolean hasSelection = !DataUtils.isNullOrEmpty(mTerminalView.getStoredSelectedText());

        SubMenu outputMenu = menu.addSubMenu(Menu.NONE, CONTEXT_MENU_OUTPUT_TOOLS_ID, Menu.NONE, R.string.action_output_tools);
        outputMenu.add(Menu.NONE, CONTEXT_MENU_SAVE_TRANSCRIPT_TXT_ID, Menu.NONE, R.string.action_save_transcript_txt);
        if (hasSelection)
            outputMenu.add(Menu.NONE, CONTEXT_MENU_SAVE_SELECTED_TXT_ID, Menu.NONE, R.string.action_save_selected_txt);
        outputMenu.add(Menu.NONE, CONTEXT_MENU_SHARE_TRANSCRIPT_TXT_ID, Menu.NONE, R.string.action_share_transcript_txt);
        outputMenu.add(Menu.NONE, CONTEXT_MENU_COPY_TRANSCRIPT_ID, Menu.NONE, R.string.action_copy_transcript);

        menu.add(Menu.NONE, CONTEXT_MENU_SCROLL_BOTTOM_ID, Menu.NONE, R.string.action_scroll_bottom);
        menu.add(Menu.NONE, CONTEXT_MENU_HOME_ID, Menu.NONE, R.string.action_cursor_home);
        menu.add(Menu.NONE, CONTEXT_MENU_CLEAR_ID, Menu.NONE, R.string.action_clear_screen);
        menu.add(Menu.NONE, CONTEXT_MENU_SELECT_URL_ID, Menu.NONE, R.string.action_select_url);
        menu.add(Menu.NONE, CONTEXT_MENU_SHARE_TRANSCRIPT_ID, Menu.NONE, R.string.action_share_transcript);
        if (hasSelection)
            menu.add(Menu.NONE, CONTEXT_MENU_SHARE_SELECTED_TEXT, Menu.NONE, R.string.action_share_selected_text);
        menu.add(Menu.NONE, CONTEXT_MENU_RESET_TERMINAL_ID, Menu.NONE, R.string.action_reset_terminal);
        menu.add(Menu.NONE, CONTEXT_MENU_TOGGLE_KEEP_SCREEN_ON, Menu.NONE, R.string.action_toggle_keep_screen_on).setCheckable(true).setChecked(mPreferences.shouldKeepScreenOn());
        menu.add(Menu.NONE, CONTEXT_MENU_SETTINGS_ID, Menu.NONE, R.string.action_open_settings);
        menu.add(Menu.NONE, CONTEXT_MENU_HELP_ID, Menu.NONE, R.string.action_open_help);
        menu.add(Menu.NONE, CONTEXT_MENU_KILL_PROCESS_ID, Menu.NONE,
            getResources().getString(R.string.action_kill_process, getCurrentSession().getPid()))
            .setEnabled(currentSession.isRunning());
    }

    /** Hook system menu to show context menu instead. */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        mTerminalView.showContextMenu();
        return false;
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        TerminalSession session = getCurrentSession();

        switch (item.getItemId()) {
            case CONTEXT_MENU_OUTPUT_TOOLS_ID:
                // The parent exists only to open the submenu. Do not route it
                // through any terminal action if Android reports it selected.
                return true;
            case CONTEXT_MENU_SAVE_TRANSCRIPT_TXT_ID:
                saveTerminalTextAsTxt(false, false);
                return true;
            case CONTEXT_MENU_SAVE_SELECTED_TXT_ID:
                saveTerminalTextAsTxt(true, false);
                return true;
            case CONTEXT_MENU_SHARE_TRANSCRIPT_TXT_ID:
                saveTerminalTextAsTxt(false, true);
                return true;
            case CONTEXT_MENU_COPY_TRANSCRIPT_ID:
                if (session != null && session.getEmulator() != null) {
                    String transcript = session.getEmulator().getScreen().getTranscriptText();
                    ShareUtils.copyTextToClipboard(this, transcript, getString(R.string.msg_transcript_copied));
                }
                return true;
            case CONTEXT_MENU_SCROLL_BOTTOM_ID:
                if (mTerminalView != null) mTerminalView.scrollToBottom();
                return true;
            case CONTEXT_MENU_HOME_ID:
                if (mTermuxTerminalExtraKeys != null)
                    mTermuxTerminalExtraKeys.onTerminalExtraKeyButtonClick(null, "HOME", false, false, false, false);
                return true;
            case CONTEXT_MENU_CLEAR_ID:
                if (session != null) session.write("clear\n");
                return true;
            case CONTEXT_MENU_SELECT_URL_ID:
                mTermuxTerminalViewClient.showUrlSelection();
                return true;
            case CONTEXT_MENU_SHARE_TRANSCRIPT_ID:
                mTermuxTerminalViewClient.shareSessionTranscript();
                return true;
            case CONTEXT_MENU_SHARE_SELECTED_TEXT:
                mTermuxTerminalViewClient.shareSelectedText();
                return true;
            case CONTEXT_MENU_AUTOFILL_USERNAME:
                mTerminalView.requestAutoFillUsername();
                return true;
            case CONTEXT_MENU_AUTOFILL_PASSWORD:
                mTerminalView.requestAutoFillPassword();
                return true;
            case CONTEXT_MENU_RESET_TERMINAL_ID:
                onResetTerminalSession(session);
                return true;
            case CONTEXT_MENU_KILL_PROCESS_ID:
                showKillSessionDialog(session);
                return true;
            case CONTEXT_MENU_STYLING_ID:
                showStylingDialog();
                return true;
            case CONTEXT_MENU_TOGGLE_KEEP_SCREEN_ON:
                toggleKeepScreenOn();
                return true;
            case CONTEXT_MENU_HELP_ID:
                ActivityUtils.startActivity(this, new Intent(this, HelpActivity.class));
                return true;
            case CONTEXT_MENU_SETTINGS_ID:
                ActivityUtils.startActivity(this, new Intent(this, SettingsActivity.class));
                return true;
            case CONTEXT_MENU_REPORT_ID:
                mTermuxTerminalViewClient.reportIssueFromTranscript();
                return true;
            default:
                return super.onContextItemSelected(item);
        }
    }

    @Override
    public void onContextMenuClosed(Menu menu) {
        super.onContextMenuClosed(menu);
        // onContextMenuClosed() is triggered twice if back button is pressed to dismiss instead of tap for some reason
        mTerminalView.onContextMenuClosed(menu);
    }

    private void saveTerminalTextAsTxt(boolean selectedOnly, boolean shareAfterSave) {
        TerminalSession session = getCurrentSession();
        if (session == null || session.getEmulator() == null) return;

        String text = selectedOnly
            ? mTerminalView.getStoredSelectedText()
            : session.getEmulator().getScreen().getTranscriptText();
        if (DataUtils.isNullOrEmpty(text)) return;

        String prefix = selectedOnly ? "NewTermux-seleccion" : "NewTermux-terminal";
        String fileName = TerminalTextExport.buildFileName(prefix);

        TerminalTextExport.saveToDownloads(this, text, fileName, new TerminalTextExport.Callback() {
            @Override
            public void onSuccess(@Nullable Uri uri, @NonNull String displayName) {
                if (shareAfterSave && uri != null) {
                    try {
                        TerminalTextExport.shareTextFile(
                            TermuxActivity.this,
                            uri,
                            displayName,
                            getString(R.string.title_share_txt)
                        );
                    } catch (Exception e) {
                        showToast(getString(R.string.msg_txt_share_failed,
                            e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()), true);
                    }
                } else {
                    Snackbar snackbar = Snackbar.make(
                        mTerminalView,
                        getString(R.string.msg_txt_saved, displayName),
                        Snackbar.LENGTH_LONG
                    );
                    if (uri != null) {
                        snackbar.setAction(R.string.action_open_saved_file, v -> {
                            try {
                                TerminalTextExport.openText(TermuxActivity.this, uri);
                            } catch (Exception e) {
                                showToast(getString(R.string.msg_txt_open_failed,
                                    e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()), true);
                            }
                        });
                    }
                    snackbar.show();
                }
            }

            @Override
            public void onError(@NonNull Exception error) {
                showToast(getString(R.string.msg_txt_save_failed,
                    error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage()), true);
            }
        });
    }

    private void showKillSessionDialog(TerminalSession session) {
        if (session == null) return;

        final AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setIcon(android.R.drawable.ic_dialog_alert);
        b.setMessage(R.string.title_confirm_kill_process);
        b.setPositiveButton(android.R.string.yes, (dialog, id) -> {
            dialog.dismiss();
            session.finishIfRunning();
        });
        b.setNegativeButton(android.R.string.no, null);
        b.show();
    }

    private void onResetTerminalSession(TerminalSession session) {
        if (session != null) {
            session.reset();
            showToast(getResources().getString(R.string.msg_terminal_reset), true);

            if (mTermuxTerminalSessionActivityClient != null)
                mTermuxTerminalSessionActivityClient.onResetTerminalSession();
        }
    }

    private void showStylingDialog() {
        Intent stylingIntent = new Intent();
        stylingIntent.setClassName(TermuxConstants.TERMUX_STYLING_PACKAGE_NAME, TermuxConstants.TERMUX_STYLING_APP.TERMUX_STYLING_ACTIVITY_NAME);
        try {
            startActivity(stylingIntent);
        } catch (ActivityNotFoundException | IllegalArgumentException e) {
            // The startActivity() call is not documented to throw IllegalArgumentException.
            // However, crash reporting shows that it sometimes does, so catch it here.
            new AlertDialog.Builder(this).setMessage(getString(R.string.error_styling_not_installed))
                .setPositiveButton(R.string.action_styling_install,
                    (dialog, which) -> ActivityUtils.startActivity(this, new Intent(Intent.ACTION_VIEW, Uri.parse(TermuxConstants.TERMUX_STYLING_FDROID_PACKAGE_URL))))
                .setNegativeButton(android.R.string.cancel, null).show();
        }
    }
    private void toggleKeepScreenOn() {
        if (mTerminalView.getKeepScreenOn()) {
            mTerminalView.setKeepScreenOn(false);
            mPreferences.setKeepScreenOn(false);
        } else {
            mTerminalView.setKeepScreenOn(true);
            mPreferences.setKeepScreenOn(true);
        }
    }



    /**
     * For processes to access primary external storage (/sdcard, /storage/emulated/0, ~/storage/shared),
     * termux needs to be granted legacy WRITE_EXTERNAL_STORAGE or MANAGE_EXTERNAL_STORAGE permissions
     * if targeting targetSdkVersion 30 (android 11) and running on sdk 30 (android 11) and higher.
     */
    public void requestStoragePermission(boolean isPermissionCallback) {
        new Thread() {
            @Override
            public void run() {
                // Do not ask for permission again
                int requestCode = isPermissionCallback ? -1 : PermissionUtils.REQUEST_GRANT_STORAGE_PERMISSION;

                // If permission is granted, then also setup storage symlinks.
                if(PermissionUtils.checkAndRequestLegacyOrManageExternalStoragePermission(
                    TermuxActivity.this, requestCode, !isPermissionCallback)) {
                    if (isPermissionCallback)
                        Logger.logInfoAndShowToast(TermuxActivity.this, LOG_TAG,
                            getString(com.termux.shared.R.string.msg_storage_permission_granted_on_request));

                    TermuxInstaller.setupStorageSymlinks(TermuxActivity.this);
                } else {
                    if (isPermissionCallback)
                        Logger.logInfoAndShowToast(TermuxActivity.this, LOG_TAG,
                            getString(com.termux.shared.R.string.msg_storage_permission_not_granted_on_request));
                }
            }
        }.start();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Logger.logVerbose(LOG_TAG, "onActivityResult: requestCode: " + requestCode + ", resultCode: "  + resultCode + ", data: "  + IntentUtils.getIntentString(data));
        if (!BuildConfig.IS_DEMO && requestCode == PermissionUtils.REQUEST_GRANT_STORAGE_PERMISSION) {
            requestStoragePermission(true);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        Logger.logVerbose(LOG_TAG, "onRequestPermissionsResult: requestCode: " + requestCode + ", permissions: "  + Arrays.toString(permissions) + ", grantResults: "  + Arrays.toString(grantResults));
        if (!BuildConfig.IS_DEMO && requestCode == PermissionUtils.REQUEST_GRANT_STORAGE_PERMISSION) {
            requestStoragePermission(true);
        } else if (requestCode == REQUEST_RECORD_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (mSpeechInputManager != null) mSpeechInputManager.startListening();
            } else {
                showToast(getString(R.string.msg_microphone_permission_required), false);
            }
        }
    }



    public int getNavBarHeight() {
        return mNavBarHeight;
    }

    public TermuxActivityRootView getTermuxActivityRootView() {
        return mTermuxActivityRootView;
    }

    public View getTermuxActivityBottomSpaceView() {
        return mTermuxActivityBottomSpaceView;
    }

    public ExtraKeysView getExtraKeysView() {
        return mExtraKeysView;
    }

    public TermuxTerminalExtraKeys getTermuxTerminalExtraKeys() {
        return mTermuxTerminalExtraKeys;
    }

    public void setExtraKeysView(ExtraKeysView extraKeysView) {
        mExtraKeysView = extraKeysView;
    }

    public DrawerLayout getDrawer() {
        return (DrawerLayout) findViewById(R.id.drawer_layout);
    }


    public ViewPager getTerminalToolbarViewPager() {
        return (ViewPager) findViewById(R.id.terminal_toolbar_view_pager);
    }

    public float getTerminalToolbarDefaultHeight() {
        return mTerminalToolbarDefaultHeight;
    }

    public boolean isTerminalViewSelected() {
        return getTerminalToolbarViewPager().getCurrentItem() == 0;
    }

    public boolean isTerminalToolbarTextInputViewSelected() {
        return getTerminalToolbarViewPager().getCurrentItem() == 1;
    }


    public void termuxSessionListNotifyUpdated() {
        if (mTermuxSessionListViewController != null)
            mTermuxSessionListViewController.notifyDataSetChanged();
        updateSessionTabs();
    }

    public boolean isVisible() {
        return mIsVisible;
    }

    public boolean isOnResumeAfterOnCreate() {
        return mIsOnResumeAfterOnCreate;
    }

    public boolean isActivityRecreated() {
        return mIsActivityRecreated;
    }



    public TermuxService getTermuxService() {
        return mTermuxService;
    }

    public TerminalView getTerminalView() {
        return mTerminalView;
    }

    public TermuxTerminalViewClient getTermuxTerminalViewClient() {
        return mTermuxTerminalViewClient;
    }

    public TermuxTerminalSessionActivityClient getTermuxTerminalSessionClient() {
        return mTermuxTerminalSessionActivityClient;
    }

    @Nullable
    public TerminalSession getCurrentSession() {
        if (mTerminalView != null)
            return mTerminalView.getCurrentSession();
        else
            return null;
    }

    public TermuxAppSharedPreferences getPreferences() {
        return mPreferences;
    }

    public TermuxAppSharedProperties getProperties() {
        return mProperties;
    }




    public static void updateTermuxActivityStyling(Context context, boolean recreateActivity) {
        // Make sure that terminal styling is always applied.
        Intent stylingIntent = new Intent(TERMUX_ACTIVITY.ACTION_RELOAD_STYLE);
        stylingIntent.putExtra(TERMUX_ACTIVITY.EXTRA_RECREATE_ACTIVITY, recreateActivity);
        context.sendBroadcast(stylingIntent);
    }

    private void registerTermuxActivityBroadcastReceiver() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(TERMUX_ACTIVITY.ACTION_NOTIFY_APP_CRASH);
        intentFilter.addAction(TERMUX_ACTIVITY.ACTION_RELOAD_STYLE);
        intentFilter.addAction(TERMUX_ACTIVITY.ACTION_REQUEST_PERMISSIONS);

        registerReceiver(mTermuxActivityBroadcastReceiver, intentFilter);
    }

    private void unregisterTermuxActivityBroadcastReceiver() {
        unregisterReceiver(mTermuxActivityBroadcastReceiver);
    }

    private void fixTermuxActivityBroadcastReceiverIntent(Intent intent) {
        if (intent == null) return;

        String extraReloadStyle = intent.getStringExtra(TERMUX_ACTIVITY.EXTRA_RELOAD_STYLE);
        if ("storage".equals(extraReloadStyle)) {
            intent.removeExtra(TERMUX_ACTIVITY.EXTRA_RELOAD_STYLE);
            intent.setAction(TERMUX_ACTIVITY.ACTION_REQUEST_PERMISSIONS);
        }
    }

    class TermuxActivityBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) return;

            if (mIsVisible) {
                fixTermuxActivityBroadcastReceiverIntent(intent);

                switch (intent.getAction()) {
                    case TERMUX_ACTIVITY.ACTION_NOTIFY_APP_CRASH:
                        Logger.logDebug(LOG_TAG, "Received intent to notify app crash");
                        TermuxCrashUtils.notifyAppCrashFromCrashLogFile(context, LOG_TAG);
                        return;
                    case TERMUX_ACTIVITY.ACTION_RELOAD_STYLE:
                        Logger.logDebug(LOG_TAG, "Received intent to reload styling");
                        reloadActivityStyling(intent.getBooleanExtra(TERMUX_ACTIVITY.EXTRA_RECREATE_ACTIVITY, true));
                        return;
                    case TERMUX_ACTIVITY.ACTION_REQUEST_PERMISSIONS:
                        Logger.logDebug(LOG_TAG, "Received intent to request storage permissions");
                        requestStoragePermission(false);
                        return;
                    default:
                }
            }
        }
    }

    private void reloadActivityStyling(boolean recreateActivity) {
        if (mProperties != null) {
            reloadProperties();

            if (mExtraKeysView != null) {
                mExtraKeysView.setButtonTextAllCaps(mProperties.shouldExtraKeysTextBeAllCaps());
                mExtraKeysView.reload(mTermuxTerminalExtraKeys.getExtraKeysInfo(), mTerminalToolbarDefaultHeight);
            }

            // Update NightMode.APP_NIGHT_MODE
            TermuxThemeUtils.setAppNightMode(mProperties.getNightMode());
        }

        setMargins();
        setTerminalToolbarHeight();

        FileReceiverActivity.updateFileReceiverActivityComponentsState(this);

        if (mTermuxTerminalSessionActivityClient != null)
            mTermuxTerminalSessionActivityClient.onReloadActivityStyling();

        if (mTermuxTerminalViewClient != null)
            mTermuxTerminalViewClient.onReloadActivityStyling();

        // To change the activity and drawer theme, activity needs to be recreated.
        // It will destroy the activity, including all stored variables and views, and onCreate()
        // will be called again. Extra keys input text, terminal sessions and transcripts will be preserved.
        if (recreateActivity) {
            Logger.logDebug(LOG_TAG, "Recreating activity");
            TermuxActivity.this.recreate();
        }
    }



    public static void startTermuxActivity(@NonNull final Context context) {
        ActivityUtils.startActivity(context, newInstance(context));
    }

    public static Intent newInstance(@NonNull final Context context) {
        Intent intent = new Intent(context, TermuxActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }

}
