package com.newtermux.features;

import android.app.AlertDialog;
import android.content.Context;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.termux.shared.termux.TermuxConstants;
import com.termux.terminal.TerminalSession;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Comparator;

/** Small on-device library. Files live in HOME so TBM includes them with the user's scripts. */
public final class ScriptLibrary {
    private ScriptLibrary() {}

    private static File directory() {
        return new File(TermuxConstants.TERMUX_HOME_DIR, ".newtermux/scripts");
    }

    public static void show(Context context, TerminalSession session) {
        File dir = directory();
        if (!dir.isDirectory() && !dir.mkdirs()) {
            Toast.makeText(context, "No se pudo crear la carpeta de scripts", Toast.LENGTH_LONG).show();
            return;
        }
        File[] scripts = dir.listFiles((d, name) -> name.matches("[A-Za-z0-9_-]{1,64}\\.sh") && new File(d, name).isFile());
        if (scripts == null) scripts = new File[0];
        Arrays.sort(scripts, Comparator.comparing(File::getName));
        File[] files = scripts;
        String[] names = new String[files.length + 1];
        names[0] = "+ Crear script";
        for (int i = 0; i < files.length; i++) names[i + 1] = files[i].getName();
        new AlertDialog.Builder(context).setTitle("Mis scripts")
            .setItems(names, (dialog, which) -> {
                if (which == 0) edit(context, session, null);
                else options(context, session, files[which - 1]);
            }).setNegativeButton("Cerrar", null).show();
    }

    private static void options(Context context, TerminalSession session, File file) {
        new AlertDialog.Builder(context).setTitle(file.getName())
            .setItems(new String[]{"Ejecutar", "Editar", "Eliminar"}, (dialog, which) -> {
                if (which == 0) {
                    if (session == null) {
                        Toast.makeText(context, "Abrí una sesión primero", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    // File names are restricted below to ASCII letters, digits, dashes and underscores.
                    session.write("bash \"$HOME/.newtermux/scripts/" + file.getName() + "\"\n");
                } else if (which == 1) edit(context, session, file);
                else new AlertDialog.Builder(context).setTitle("Eliminar " + file.getName() + "?")
                    .setPositiveButton("Eliminar", (d, w) -> {
                        if (!file.delete()) Toast.makeText(context, "No se pudo eliminar", Toast.LENGTH_SHORT).show();
                        show(context, session);
                    }).setNegativeButton("Cancelar", null).show();
            }).setNegativeButton("Volver", (d, w) -> show(context, session)).show();
    }

    private static void edit(Context context, TerminalSession session, File existing) {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * context.getResources().getDisplayMetrics().density);
        box.setPadding(pad, 0, pad, 0);
        EditText name = new EditText(context);
        name.setHint("Nombre (letras, números, - y _)");
        name.setSingleLine(true);
        if (existing != null) name.setText(existing.getName().replaceFirst("\\.sh$", ""));
        box.addView(name);
        EditText body = new EditText(context);
        body.setHint("#!/data/data/com.termux/files/usr/bin/bash\n# Comandos...");
        body.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        body.setMinLines(8);
        if (existing != null) {
            try { body.setText(new String(Files.readAllBytes(existing.toPath()), StandardCharsets.UTF_8)); }
            catch (Exception e) { Toast.makeText(context, "No se pudo leer el script", Toast.LENGTH_SHORT).show(); return; }
        }
        box.addView(body);
        AlertDialog dlg = new AlertDialog.Builder(context).setTitle(existing == null ? "Nuevo script" : "Editar script")
            .setView(box).setPositiveButton("Guardar", null).setNegativeButton("Cancelar", null).create();
        dlg.setOnShowListener(ignored -> dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String label = name.getText().toString().trim();
            if (!label.matches("[A-Za-z0-9_-]{1,64}")) {
                name.setError("Usá entre 1 y 64 letras, números, - o _"); return;
            }
            File target = new File(directory(), label + ".sh");
            if (target.exists() && (existing == null || !target.equals(existing))) {
                name.setError("Ese nombre ya existe"); return;
            }
            try (FileOutputStream out = new FileOutputStream(target)) {
                out.write(body.getText().toString().getBytes(StandardCharsets.UTF_8));
                out.flush();
                if (existing != null && !existing.equals(target) && !existing.delete())
                    Toast.makeText(context, "El nombre anterior sigue presente", Toast.LENGTH_SHORT).show();
                dlg.dismiss();
                show(context, session);
            } catch (Exception e) {
                Toast.makeText(context, "Error al guardar: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }));
        dlg.show();
    }
}
