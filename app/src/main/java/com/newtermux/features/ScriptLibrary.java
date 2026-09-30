package com.newtermux.features;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.text.InputType;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.termux.shared.termux.TermuxConstants;
import com.termux.terminal.TerminalSession;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

/**
 * On-device script library.
 *
 * Scripts and folders live under HOME so TBM backs them up. Visual metadata is
 * stored beside them in .colors.json and contains no credentials.
 */
public final class ScriptLibrary {
    private ScriptLibrary() {}

    private static final String META_FILE = ".colors.json";
    private static final int DEFAULT_SCRIPT_COLOR = 0xFF9BC7AE;
    private static final int DEFAULT_FOLDER_COLOR = 0xFFB8A7D9;

    private static File directory() {
        return new File(TermuxConstants.TERMUX_HOME_DIR, ".newtermux/scripts");
    }

    private static File metadataFile() {
        return new File(directory(), META_FILE);
    }

    public static void show(Context context, TerminalSession session) {
        File root = directory();
        if (!root.isDirectory() && !root.mkdirs()) {
            Toast.makeText(context, "No se pudo crear la carpeta de scripts", Toast.LENGTH_LONG).show();
            return;
        }
        showDirectory(context, session, root);
    }

    private static void showDirectory(Context context, TerminalSession session, File current) {
        File root = directory();
        File[] dirs = current.listFiles(File::isDirectory);
        File[] scripts = current.listFiles((d, name) ->
            name.matches("[A-Za-z0-9_-]{1,64}\\.sh") && new File(d, name).isFile());

        if (dirs == null) dirs = new File[0];
        if (scripts == null) scripts = new File[0];

        Arrays.sort(dirs, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        Arrays.sort(scripts, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));

        boolean atRoot = sameFile(root, current);
        List<CharSequence> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();

        if (!atRoot) {
            labels.add("← Volver");
            File parent = current.getParentFile();
            actions.add(() -> showDirectory(context, session, parent == null ? root : parent));
        }

        labels.add("+ Crear carpeta");
        actions.add(() -> createFolder(context, session, current));

        labels.add("+ Crear script");
        actions.add(() -> edit(context, session, current, null));

        if (!atRoot) {
            labels.add(coloredLabel("●  Carpeta actual: " + current.getName(), getFolderColor(current)));
            actions.add(() -> folderOptions(context, session, current));
        }

        for (File dir : dirs) {
            labels.add(coloredLabel("●  📁 " + dir.getName(), getFolderColor(dir)));
            actions.add(() -> showDirectory(context, session, dir));
        }

        for (File file : scripts) {
            labels.add(coloredLabel("●  " + file.getName(), getScriptColor(file)));
            actions.add(() -> options(context, session, file));
        }

        String title = atRoot ? "Mis scripts" : "Mis scripts · " + relativePath(current);
        new AlertDialog.Builder(context)
            .setTitle(title)
            .setItems(labels.toArray(new CharSequence[0]), (dialog, which) -> {
                if (which >= 0 && which < actions.size()) actions.get(which).run();
            })
            .setNegativeButton("Cerrar", null)
            .show();
    }

    private static CharSequence coloredLabel(String text, int color) {
        SpannableString value = new SpannableString(text);
        int markerEnd = Math.min(1, value.length());
        if (markerEnd > 0) {
            value.setSpan(new ForegroundColorSpan(color), 0, markerEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return value;
    }

    private static void createFolder(Context context, TerminalSession session, File parent) {
        EditText input = new EditText(context);
        input.setHint("Nombre de carpeta");
        input.setSingleLine(true);
        int pad = (int) (20 * context.getResources().getDisplayMetrics().density);
        LinearLayout box = new LinearLayout(context);
        box.setPadding(pad, 0, pad, 0);
        box.addView(input);

        AlertDialog dialog = new AlertDialog.Builder(context)
            .setTitle("Nueva carpeta")
            .setView(box)
            .setPositiveButton("Crear", null)
            .setNegativeButton("Cancelar", null)
            .create();

        dialog.setOnShowListener(ignored ->
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String name = input.getText().toString().trim();
                if (!validName(name)) {
                    input.setError("Usá entre 1 y 64 letras, números, - o _");
                    return;
                }
                File target = new File(parent, name);
                if (target.exists()) {
                    input.setError("Ese nombre ya existe");
                    return;
                }
                if (!target.mkdir()) {
                    Toast.makeText(context, "No se pudo crear la carpeta", Toast.LENGTH_LONG).show();
                    return;
                }
                dialog.dismiss();
                showDirectory(context, session, parent);
            })
        );
        dialog.show();
    }

    private static void folderOptions(Context context, TerminalSession session, File folder) {
        new AlertDialog.Builder(context)
            .setTitle(folder.getName())
            .setItems(new String[]{"Cambiar color", "Renombrar carpeta", "Eliminar carpeta vacía"}, (dialog, which) -> {
                if (which == 0) {
                    new ColorPickerDialog(context)
                        .setInitialColor(getFolderColor(folder))
                        .setOnColorSelectedListener(color -> {
                            setColor("dir:" + relativePath(folder), color);
                            showDirectory(context, session, folder);
                        })
                        .show();
                } else if (which == 1) {
                    renameFolder(context, session, folder);
                } else {
                    File[] children = folder.listFiles();
                    if (children != null && children.length > 0) {
                        Toast.makeText(context, "La carpeta no está vacía", Toast.LENGTH_LONG).show();
                        return;
                    }
                    File parent = folder.getParentFile();
                    new AlertDialog.Builder(context)
                        .setTitle("¿Eliminar " + folder.getName() + "?")
                        .setPositiveButton("Eliminar", (d, w) -> {
                            if (!folder.delete()) {
                                Toast.makeText(context, "No se pudo eliminar", Toast.LENGTH_LONG).show();
                            } else {
                                removeColor("dir:" + relativePath(folder));
                                showDirectory(context, session, parent == null ? directory() : parent);
                            }
                        })
                        .setNegativeButton("Cancelar", null)
                        .show();
                }
            })
            .setNegativeButton("Volver", (d, w) -> showDirectory(context, session, folder))
            .show();
    }

    private static void renameFolder(Context context, TerminalSession session, File folder) {
        EditText input = new EditText(context);
        input.setSingleLine(true);
        input.setText(folder.getName());
        input.setSelectAllOnFocus(true);
        int pad = (int) (20 * context.getResources().getDisplayMetrics().density);
        LinearLayout box = new LinearLayout(context);
        box.setPadding(pad, 0, pad, 0);
        box.addView(input);

        AlertDialog dialog = new AlertDialog.Builder(context)
            .setTitle("Renombrar carpeta")
            .setView(box)
            .setPositiveButton("Guardar", null)
            .setNegativeButton("Cancelar", null)
            .create();

        dialog.setOnShowListener(ignored ->
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String name = input.getText().toString().trim();
                if (!validName(name)) {
                    input.setError("Usá entre 1 y 64 letras, números, - o _");
                    return;
                }
                File parent = folder.getParentFile();
                if (parent == null) return;
                File target = new File(parent, name);
                if (!sameFile(folder, target) && target.exists()) {
                    input.setError("Ese nombre ya existe");
                    return;
                }
                String oldRel = relativePath(folder);
                if (!sameFile(folder, target) && !folder.renameTo(target)) {
                    Toast.makeText(context, "No se pudo renombrar", Toast.LENGTH_LONG).show();
                    return;
                }
                remapMetadataPrefix(oldRel, relativePath(target));
                dialog.dismiss();
                showDirectory(context, session, target);
            })
        );
        dialog.show();
    }

    private static void options(Context context, TerminalSession session, File file) {
        new AlertDialog.Builder(context)
            .setTitle(file.getName())
            .setItems(new String[]{"Ejecutar", "Editar", "Cambiar color", "Mover a…", "Eliminar"}, (dialog, which) -> {
                if (which == 0) {
                    if (session == null) {
                        Toast.makeText(context, "Abrí una sesión primero", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    session.write("bash \"$HOME/.newtermux/scripts/" + relativePath(file) + "\"\n");
                } else if (which == 1) {
                    edit(context, session, file.getParentFile(), file);
                } else if (which == 2) {
                    new ColorPickerDialog(context)
                        .setInitialColor(getScriptColor(file))
                        .setOnColorSelectedListener(color -> {
                            setColor("file:" + relativePath(file), color);
                            showDirectory(context, session, file.getParentFile());
                        })
                        .show();
                } else if (which == 3) {
                    chooseMoveTarget(context, session, file);
                } else {
                    new AlertDialog.Builder(context)
                        .setTitle("Eliminar " + file.getName() + "?")
                        .setPositiveButton("Eliminar", (d, w) -> {
                            File parent = file.getParentFile();
                            String key = "file:" + relativePath(file);
                            if (!file.delete()) {
                                Toast.makeText(context, "No se pudo eliminar", Toast.LENGTH_SHORT).show();
                            } else {
                                removeColor(key);
                                showDirectory(context, session, parent == null ? directory() : parent);
                            }
                        })
                        .setNegativeButton("Cancelar", null)
                        .show();
                }
            })
            .setNegativeButton("Volver", (d, w) -> showDirectory(context, session, file.getParentFile()))
            .show();
    }

    private static void chooseMoveTarget(Context context, TerminalSession session, File file) {
        List<File> folders = new ArrayList<>();
        folders.add(directory());
        collectFolders(directory(), folders);

        CharSequence[] names = new CharSequence[folders.size()];
        for (int i = 0; i < folders.size(); i++) {
            File f = folders.get(i);
            names[i] = sameFile(f, directory()) ? "Raíz" : relativePath(f);
        }

        new AlertDialog.Builder(context)
            .setTitle("Mover " + file.getName() + " a…")
            .setItems(names, (dialog, which) -> {
                File targetDir = folders.get(which);
                File target = new File(targetDir, file.getName());
                if (sameFile(target, file)) {
                    showDirectory(context, session, file.getParentFile());
                    return;
                }
                if (target.exists()) {
                    Toast.makeText(context, "Ya existe un script con ese nombre", Toast.LENGTH_LONG).show();
                    return;
                }
                String oldKey = "file:" + relativePath(file);
                Integer oldColor = findColor(oldKey);
                if (!file.renameTo(target)) {
                    Toast.makeText(context, "No se pudo mover", Toast.LENGTH_LONG).show();
                    return;
                }
                removeColor(oldKey);
                if (oldColor != null) setColor("file:" + relativePath(target), oldColor);
                showDirectory(context, session, targetDir);
            })
            .setNegativeButton("Cancelar", null)
            .show();
    }

    private static void collectFolders(File parent, List<File> out) {
        File[] children = parent.listFiles(File::isDirectory);
        if (children == null) return;
        Arrays.sort(children, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        for (File child : children) {
            out.add(child);
            collectFolders(child, out);
        }
    }

    private static void edit(Context context, TerminalSession session, File currentDir, File existing) {
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
            try {
                body.setText(new String(Files.readAllBytes(existing.toPath()), StandardCharsets.UTF_8));
            } catch (Exception e) {
                Toast.makeText(context, "No se pudo leer el script", Toast.LENGTH_SHORT).show();
                return;
            }
        }
        box.addView(body);

        AlertDialog dialog = new AlertDialog.Builder(context)
            .setTitle(existing == null ? "Nuevo script" : "Editar script")
            .setView(box)
            .setPositiveButton("Guardar", null)
            .setNegativeButton("Cancelar", null)
            .create();

        dialog.setOnShowListener(ignored ->
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String label = name.getText().toString().trim();
                if (!validName(label)) {
                    name.setError("Usá entre 1 y 64 letras, números, - o _");
                    return;
                }

                File target = new File(currentDir, label + ".sh");
                if (target.exists() && (existing == null || !sameFile(target, existing))) {
                    name.setError("Ese nombre ya existe");
                    return;
                }

                String oldKey = existing == null ? null : "file:" + relativePath(existing);
                Integer oldColor = oldKey == null ? null : findColor(oldKey);

                try (FileOutputStream out = new FileOutputStream(target)) {
                    out.write(body.getText().toString().getBytes(StandardCharsets.UTF_8));
                    out.flush();

                    if (existing != null && !sameFile(existing, target) && !existing.delete()) {
                        Toast.makeText(context, "El nombre anterior sigue presente", Toast.LENGTH_SHORT).show();
                    } else if (oldKey != null && !sameFile(existing, target)) {
                        removeColor(oldKey);
                        if (oldColor != null) setColor("file:" + relativePath(target), oldColor);
                    }

                    dialog.dismiss();
                    showDirectory(context, session, currentDir);
                } catch (Exception e) {
                    Toast.makeText(context, "Error al guardar: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            })
        );
        dialog.show();
    }

    private static boolean validName(String value) {
        return value != null && value.matches("[A-Za-z0-9_-]{1,64}");
    }

    private static String relativePath(File file) {
        try {
            String rel = directory().toPath().relativize(file.toPath()).toString();
            return rel.replace(File.separatorChar, '/');
        } catch (Exception e) {
            return file.getName();
        }
    }

    private static boolean sameFile(File a, File b) {
        if (a == null || b == null) return false;
        try {
            return a.getCanonicalFile().equals(b.getCanonicalFile());
        } catch (Exception e) {
            return a.equals(b);
        }
    }

    private static int getScriptColor(File file) {
        Integer color = findColor("file:" + relativePath(file));
        return color == null ? DEFAULT_SCRIPT_COLOR : color;
    }

    private static int getFolderColor(File folder) {
        Integer color = findColor("dir:" + relativePath(folder));
        return color == null ? DEFAULT_FOLDER_COLOR : color;
    }

    private static synchronized Integer findColor(String key) {
        try {
            JSONObject object = readMetadata();
            if (!object.has(key)) return null;
            return object.getInt(key);
        } catch (Exception e) {
            return null;
        }
    }

    private static synchronized void setColor(String key, int color) {
        try {
            JSONObject object = readMetadata();
            object.put(key, color);
            writeMetadata(object);
        } catch (Exception ignored) {
        }
    }

    private static synchronized void removeColor(String key) {
        try {
            JSONObject object = readMetadata();
            object.remove(key);
            writeMetadata(object);
        } catch (Exception ignored) {
        }
    }

    private static synchronized void remapMetadataPrefix(String oldRel, String newRel) {
        try {
            JSONObject source = readMetadata();
            JSONObject target = new JSONObject();
            Iterator<String> keys = source.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                String mapped = key;
                if (key.equals("dir:" + oldRel)) {
                    mapped = "dir:" + newRel;
                } else if (key.startsWith("dir:" + oldRel + "/")) {
                    mapped = "dir:" + newRel + key.substring(("dir:" + oldRel).length());
                } else if (key.startsWith("file:" + oldRel + "/")) {
                    mapped = "file:" + newRel + key.substring(("file:" + oldRel).length());
                }
                target.put(mapped, source.getInt(key));
            }
            writeMetadata(target);
        } catch (Exception ignored) {
        }
    }

    private static JSONObject readMetadata() {
        File file = metadataFile();
        if (!file.isFile()) return new JSONObject();
        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            return new JSONObject(new String(bytes, StandardCharsets.UTF_8));
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    private static void writeMetadata(JSONObject object) {
        File root = directory();
        if (!root.isDirectory()) root.mkdirs();
        try (FileOutputStream out = new FileOutputStream(metadataFile(), false)) {
            out.write(object.toString(2).getBytes(StandardCharsets.UTF_8));
            out.flush();
        } catch (Exception ignored) {
        }
    }
}
