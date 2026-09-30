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

    public static void runAiHarnessInstaller(Context context, TerminalSession session, String action) {
        if (session == null) {
            Toast.makeText(context, "Abrí una sesión primero", Toast.LENGTH_SHORT).show();
            return;
        }
        if (action == null || !action.matches("prepare|antigravity|codex|opencode|all|status")) {
            Toast.makeText(context, "Acción de instalación no válida", Toast.LENGTH_LONG).show();
            return;
        }

        File dir = new File(TermuxConstants.TERMUX_HOME_DIR, ".newtermux/installers");
        if (!dir.isDirectory() && !dir.mkdirs()) {
            Toast.makeText(context, "No se pudo crear ~/.newtermux/installers", Toast.LENGTH_LONG).show();
            return;
        }

        File target = new File(dir, AI_HARNESS_FILENAME);
        try (InputStream in = context.getAssets().open(AI_HARNESS_ASSET);
             FileOutputStream out = new FileOutputStream(target, false)) {
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = in.read(buffer)) >= 0) {
                if (read > 0) out.write(buffer, 0, read);
            }
            out.flush();
            target.setReadable(true, true);
            target.setExecutable(true, true);
        } catch (Exception e) {
            Toast.makeText(context, "No se pudo preparar el instalador: " + e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }

        session.write("bash \"$HOME/.newtermux/installers/" + AI_HARNESS_FILENAME + "\" " + action + "\n");
    }
}
