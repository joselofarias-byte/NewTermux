package com.newtermux.features;

import android.content.Context;
import android.widget.Toast;

import com.termux.shared.termux.TermuxConstants;
import com.termux.terminal.TerminalSession;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * Materializes installer scripts bundled with the APK into HOME and runs them in
 * the current terminal session. Bundled scripts contain no credentials.
 */
public final class BundledInstallerLibrary {
    private BundledInstallerLibrary() {}

    private static final String AI_HARNESS_ASSET =
        "newtermux/installers/ai-harnesses.sh";
    private static final String AI_HARNESS_FILENAME =
        "ai-harnesses.sh";
    private static final String NINE_ROUTER_ASSET =
        "newtermux/installers/9router-go.sh";
    private static final String NINE_ROUTER_FILENAME =
        "9router-go.sh";
    private static final String ONE_TOUCH_ASSET =
        "newtermux/installers/one-touch-ai-stack.sh";
    private static final String ONE_TOUCH_FILENAME =
        "one-touch-ai-stack.sh";

    private static File installerDirectory(Context context) {
        File dir = new File(TermuxConstants.TERMUX_HOME_DIR, ".newtermux/installers");
        if (!dir.isDirectory() && !dir.mkdirs()) {
            Toast.makeText(context, "No se pudo crear ~/.newtermux/installers", Toast.LENGTH_LONG).show();
            return null;
        }
        return dir;
    }

    private static File materialize(Context context, String asset, String filename) {
        File dir = installerDirectory(context);
        if (dir == null) return null;

        File target = new File(dir, filename);
        try (InputStream in = context.getAssets().open(asset);
             FileOutputStream out = new FileOutputStream(target, false)) {
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = in.read(buffer)) >= 0) {
                if (read > 0) out.write(buffer, 0, read);
            }
            out.flush();
            target.setReadable(true, true);
            target.setExecutable(true, true);
            return target;
        } catch (Exception e) {
            Toast.makeText(context, "No se pudo preparar " + filename + ": " + e.getMessage(),
                Toast.LENGTH_LONG).show();
            return null;
        }
    }

    public static void runOneTouchAiStack(Context context, TerminalSession session) {
        if (session == null) {
            Toast.makeText(context, "Abrí una sesión primero", Toast.LENGTH_SHORT).show();
            return;
        }

        if (materialize(context, NINE_ROUTER_ASSET, NINE_ROUTER_FILENAME) == null) return;
        if (materialize(context, AI_HARNESS_ASSET, AI_HARNESS_FILENAME) == null) return;
        if (materialize(context, ONE_TOUCH_ASSET, ONE_TOUCH_FILENAME) == null) return;

        session.write("bash \"$HOME/.newtermux/installers/" + ONE_TOUCH_FILENAME + "\"\n");
    }

    public static void runNineRouterInstaller(Context context, TerminalSession session, String action) {
        if (session == null) {
            Toast.makeText(context, "Abrí una sesión primero", Toast.LENGTH_SHORT).show();
            return;
        }
        if (action == null || !action.matches("install|start|status|stop")) {
            Toast.makeText(context, "Acción de 9router-go no válida", Toast.LENGTH_LONG).show();
            return;
        }

        if (materialize(context, NINE_ROUTER_ASSET, NINE_ROUTER_FILENAME) == null) return;
        session.write("bash \"$HOME/.newtermux/installers/" + NINE_ROUTER_FILENAME + "\" " + action + "\n");
    }

    public static void runAiHarnessInstaller(Context context, TerminalSession session, String action) {
        if (session == null) {
            Toast.makeText(context, "Abrí una sesión primero", Toast.LENGTH_SHORT).show();
            return;
        }
        if (action == null || !action.matches("prepare|antigravity|codex|opencode|opencode-router|all|status")) {
            Toast.makeText(context, "Acción de instalación no válida", Toast.LENGTH_LONG).show();
            return;
        }

        if (materialize(context, AI_HARNESS_ASSET, AI_HARNESS_FILENAME) == null) return;
        session.write("bash \"$HOME/.newtermux/installers/" + AI_HARNESS_FILENAME + "\" " + action + "\n");
    }
}
