package com.newtermux.features;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructStat;

import com.termux.BuildConfig;
import com.termux.shared.termux.TermuxConstants;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * NewTermux-native backup format.
 *
 * .ntbackup is a standard ZIP container with:
 *   META-INF/header.json       small manifest written first for instant inspection
 *   data/...                   selected payload
 *   META-INF/metadata.jsonl    scalable per-entry metadata and SHA-256
 *   META-INF/tree.sha256       SHA-256 over metadata.jsonl
 *
 * It is deliberately independent from TBM.
 */
public final class NativeBackupManager {
    private static final String FORMAT = "newtermux-native-backup-v1";
    private static final String HEADER_ENTRY = "META-INF/header.json";
    private static final String METADATA_ENTRY = "META-INF/metadata.jsonl";
    private static final String TREE_HASH_ENTRY = "META-INF/tree.sha256";
    private static final long MIN_HEADROOM = 128L * 1024L * 1024L;

    public interface Progress {
        void onProgress(String phase, long doneBytes, long totalBytes);
    }

    /** Cooperative cancellation for long native backup operations. */
    public interface Cancellation {
        boolean isCancelled();
    }

    public static final class BackupResult {
        public final File file;
        public final File sha256File;
        public final long sourceBytes;
        public final long archiveBytes;
        public final String sha256;

        BackupResult(File file, File sha256File, long sourceBytes, long archiveBytes, String sha256) {
            this.file = file;
            this.sha256File = sha256File;
            this.sourceBytes = sourceBytes;
            this.archiveBytes = archiveBytes;
            this.sha256 = sha256;
        }
    }

    public static final class ComponentInfo {
        public final String id;
        public final String label;
        public final long bytes;
        public final boolean restorable;

        ComponentInfo(String id, String label, long bytes, boolean restorable) {
            this.id = id;
            this.label = label;
            this.bytes = bytes;
            this.restorable = restorable;
        }
    }

    public static final class BackupInfo {
        public final String format;
        public final String createdAt;
        public final String appVersion;
        public final String packageVariant;
        public final String abi;
        public final List<ComponentInfo> components;

        BackupInfo(String format, String createdAt, String appVersion,
                   String packageVariant, String abi, List<ComponentInfo> components) {
            this.format = format;
            this.createdAt = createdAt;
            this.appVersion = appVersion;
            this.packageVariant = packageVariant;
            this.abi = abi;
            this.components = Collections.unmodifiableList(components);
        }
    }

    private NativeBackupManager() {}

    public static boolean hasPendingRestore(Context context) {
        return new File(pendingDir(context), "READY").isFile();
    }

    public static void discardPendingRestore(Context context) {
        deleteTree(pendingDir(context));
    }

    public static BackupResult createBackup(
            Context context,
            List<NativeStorageManager.Item> selectedItems,
            Progress progress) throws Exception {
        return createBackup(context, selectedItems, progress, null);
    }

    public static BackupResult createBackup(
            Context context,
            List<NativeStorageManager.Item> selectedItems,
            Progress progress,
            Cancellation cancellation) throws Exception {
        if (selectedItems == null || selectedItems.isEmpty()) {
            throw new IllegalArgumentException("Seleccioná al menos un componente");
        }
        throwIfCancelled(cancellation);

        long totalBytes = 0;
        for (NativeStorageManager.Item item : selectedItems) {
            if (!item.selectable) continue;
            totalBytes += Math.max(0, item.bytes);
        }
        if (totalBytes <= 0) {
            throw new IllegalArgumentException("Los componentes seleccionados están vacíos");
        }

        File outDir = new File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "NewTermux/Backups");
        if (!outDir.isDirectory() && !outDir.mkdirs()) {
            throw new IllegalStateException("No se pudo crear Descargas/NewTermux/Backups");
        }

        StatFs stat = new StatFs(outDir.getAbsolutePath());
        if (stat.getAvailableBytes() < totalBytes + MIN_HEADROOM) {
            throw new IllegalStateException(
                "No hay espacio libre suficiente para crear el respaldo con margen de seguridad");
        }

        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.ROOT).format(new Date());
        File out = new File(outDir, "NewTermux-" + stamp + "-" + java.util.UUID.randomUUID() + ".ntbackup");
        File partial = new File(out.getAbsolutePath() + ".partial");
        File tmpMeta = File.createTempFile("newtermux-backup-", ".jsonl", context.getCacheDir());

        JSONObject header = buildHeader(context, selectedItems);
        MessageDigest treeDigest = MessageDigest.getInstance("SHA-256");
        long[] done = {0L};

        try (ZipOutputStream zip = new ZipOutputStream(
                 new BufferedOutputStream(new FileOutputStream(partial), 1024 * 1024));
             BufferedWriter metadata = new BufferedWriter(
                 new OutputStreamWriter(new FileOutputStream(tmpMeta), StandardCharsets.UTF_8),
                 256 * 1024)) {

            zip.setLevel(1);
            writeTextEntry(zip, HEADER_ENTRY, header.toString());

            for (NativeStorageManager.Item item : selectedItems) {
                throwIfCancelled(cancellation);
                if (!item.selectable) continue;
                List<File> roots = splitRoots(item.path);
                Set<String> excludes = excludesFor(item.id);
                for (int rootIndex = 0; rootIndex < roots.size(); rootIndex++) {
                    File root = roots.get(rootIndex);
                    if (!root.exists()) continue;
                    backupTree(zip, metadata, treeDigest, item.id, rootIndex, root, root,
                        excludes, done, totalBytes, progress, cancellation);
                }
            }

            throwIfCancelled(cancellation);
            metadata.flush();
            addFileEntry(zip, METADATA_ENTRY, tmpMeta);
            writeTextEntry(zip, TREE_HASH_ENTRY, hex(treeDigest.digest()) + "\n");
        } catch (Exception e) {
            partial.delete();
            throw e;
        } finally {
            tmpMeta.delete();
        }

        throwIfCancelled(cancellation);
        if (!partial.renameTo(out)) {
            partial.delete();
            throw new IllegalStateException("No se pudo publicar el respaldo terminado");
        }

        File sidecar = new File(out.getAbsolutePath() + ".sha256");
        try {
            if (progress != null) progress.onProgress("Verificando respaldo", totalBytes, totalBytes);
            String archiveHash = sha256File(out, cancellation);
            throwIfCancelled(cancellation);
            try (FileOutputStream fos = new FileOutputStream(sidecar)) {
                fos.write((archiveHash + "  " + out.getName() + "\n").getBytes(StandardCharsets.UTF_8));
            }

            if (progress != null) progress.onProgress("Respaldo terminado", totalBytes, totalBytes);
            return new BackupResult(out, sidecar, totalBytes, out.length(), archiveHash);
        } catch (Exception e) {
            sidecar.delete();
            out.delete();
            throw e;
        }
    }

    public static BackupInfo inspect(Context context, Uri uri) throws Exception {
        try (InputStream raw = context.getContentResolver().openInputStream(uri)) {
            if (raw == null) throw new IllegalStateException("No se pudo abrir el respaldo");
            try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(raw, 256 * 1024))) {
                ZipEntry entry;
                int inspected = 0;
                while ((entry = zip.getNextEntry()) != null && inspected++ < 8) {
                    if (HEADER_ENTRY.equals(entry.getName())) {
                        String text = readSmallText(zip, 1024 * 1024);
                        return parseHeader(new JSONObject(text));
                    }
                    drain(zip);
                    zip.closeEntry();
                }
            }
        }
        throw new IllegalArgumentException("No es un respaldo nativo de NewTermux");
    }

    /**
     * Extracts and verifies selected restorable components into app-private staging.
     * The live HOME/PRoot is not touched here. applyPendingRestore() performs the
     * idempotent publish on the next process start.
     */
    public static void prepareRestore(
            Context context,
            Uri uri,
            Set<String> selected,
            Progress progress) throws Exception {
        if (selected == null || selected.isEmpty()) {
            throw new IllegalArgumentException("Seleccioná al menos un componente");
        }

        File pending = pendingDir(context);
        deleteTree(pending);
        if (!pending.mkdirs()) throw new IllegalStateException("No se pudo crear staging de restauración");

        File selectedMetadata = new File(pending, "metadata-selected.jsonl");
        JSONObject header = null;
        Map<String, String> extractedHashes = new HashMap<>();
        Set<String> seenMetadataFiles = new HashSet<>();
        MessageDigest treeDigest = MessageDigest.getInstance("SHA-256");
        String expectedTreeHash = null;
        long done = 0;
        long selectedBytes = 0;
        BackupArchivePolicy policy = new BackupArchivePolicy(Math.max(0,
            new StatFs(context.getNoBackupFilesDir().getAbsolutePath()).getAvailableBytes() - MIN_HEADROOM));
        Map<String, List<File>> targets = null;
        Map<String, String> componentsByToken = new HashMap<>();

        try (InputStream raw = context.getContentResolver().openInputStream(uri);
             BufferedWriter selectedMeta = new BufferedWriter(
                 new OutputStreamWriter(new FileOutputStream(selectedMetadata), StandardCharsets.UTF_8),
                 256 * 1024)) {
            if (raw == null) throw new IllegalStateException("No se pudo abrir el respaldo");

            try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(raw, 1024 * 1024))) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    String name = entry.getName();
                    policy.entry(name);

                    if (HEADER_ENTRY.equals(name)) {
                        header = new JSONObject(readSmallText(zip, 1024 * 1024));
                        validateHeaderForRestore(context, header, selected);
                        targets = resolveSelectedTargets(header, selected);
                        JSONArray components = header.getJSONArray("components");
                        for (int i = 0; i < components.length(); i++) {
                            String id = components.getJSONObject(i).getString("id");
                            componentsByToken.put(BackupArchivePolicy.componentToken(id), id);
                        }
                        selectedBytes = selectedBytes(header, selected);
                        ensureRestoreSpace(context, selectedBytes);
                    } else if (name.startsWith("data/")) {
                        if (header == null)
                            throw new IllegalArgumentException("header.json debe ser la primera entrada del respaldo");
                        String component = componentsByToken.get(componentFromDataEntry(name));
                        if (selected.contains(component)) {
                            File target = safeStagePath(pending, name);
                            if (entry.isDirectory()) {
                                if (!target.isDirectory() && !target.mkdirs())
                                    throw new IllegalStateException("No se pudo crear staging: " + name);
                            } else {
                                File parent = target.getParentFile();
                                if (parent != null && !parent.isDirectory() && !parent.mkdirs())
                                    throw new IllegalStateException("No se pudo crear staging: " + parent);
                                MessageDigest fileDigest = MessageDigest.getInstance("SHA-256");
                                try (FileOutputStream fos = new FileOutputStream(target)) {
                                    byte[] buf = new byte[1024 * 1024];
                                    int n;
                                    while ((n = zip.read(buf)) != -1) {
                                        policy.payload(n);
                                        fos.write(buf, 0, n);
                                        fileDigest.update(buf, 0, n);
                                        done += n;
                                        if (progress != null)
                                            progress.onProgress("Preparando restauración", done, selectedBytes);
                                    }
                                }
                                extractedHashes.put(name, hex(fileDigest.digest()));
                            }
                        } else {
                            drain(zip);
                        }
                    } else if (METADATA_ENTRY.equals(name)) {
                        if (header == null)
                            throw new IllegalArgumentException("header.json debe preceder la metadata");
                        BufferedReader reader = new BufferedReader(
                            new InputStreamReader(zip, StandardCharsets.UTF_8), 256 * 1024);
                        String line;
                        while ((line = readMetadataLine(reader)) != null) {
                            byte[] canonical = (line + "\n").getBytes(StandardCharsets.UTF_8);
                            treeDigest.update(canonical);

                            JSONObject meta = new JSONObject(line);
                            String component = meta.getString("component");
                            if (!selected.contains(component)) continue;

                            String type = meta.getString("type");
                            policy.destination(component, meta.getInt("root"), meta.getString("path"),
                                type, meta.optString("zipEntry", ""));
                            resolveMetadataTarget(targets, meta); // reject unsafe destinations before READY
                            if ("file".equals(type)) {
                                String zipEntry = meta.getString("zipEntry");
                                String actual = extractedHashes.get(zipEntry);
                                if (actual == null || !actual.equalsIgnoreCase(meta.getString("sha256"))) {
                                    throw new IllegalStateException(
                                        "Falló la verificación SHA-256 de " + meta.optString("path"));
                                }
                                seenMetadataFiles.add(zipEntry);
                            }
                            selectedMeta.write(line);
                            selectedMeta.newLine();
                        }
                    } else if (TREE_HASH_ENTRY.equals(name)) {
                        expectedTreeHash = readSmallText(zip, 4096).trim();
                    } else {
                        drain(zip);
                    }
                    zip.closeEntry();
                }
            }

            selectedMeta.flush();

            if (header == null) throw new IllegalArgumentException("Falta header.json");
            if (expectedTreeHash == null || expectedTreeHash.isEmpty())
                throw new IllegalArgumentException("Falta tree.sha256");

            String actualTree = hex(treeDigest.digest());
            if (!actualTree.equalsIgnoreCase(expectedTreeHash))
                throw new IllegalStateException("La metadata del respaldo no supera SHA-256");

            for (String zipEntry : extractedHashes.keySet()) {
                if (!seenMetadataFiles.contains(zipEntry))
                    throw new IllegalStateException("Archivo sin metadata verificada: " + zipEntry);
            }

            JSONObject plan = new JSONObject();
            plan.put("format", FORMAT);
            plan.put("header", header);
            JSONArray ids = new JSONArray();
            for (String id : selected) ids.put(id);
            plan.put("selected", ids);
            plan.put("preparedAt", System.currentTimeMillis());
            writeUtf8(new File(pending, "plan.json"), plan.toString());
            writeUtf8(new File(pending, "READY"), "1\n");
        } catch (Exception e) {
            deleteTree(pending);
            throw e;
        }

        if (progress != null) progress.onProgress("Restauración verificada", selectedBytes, selectedBytes);
    }

    /**
     * Called early from Application.onCreate(). HOME is restored as an idempotent
     * merge (never deletes unrelated current files). PRoot and model roots are
     * reconstructed from the verified staging payload. PREFIX restore remains
     * intentionally blocked in v1.
     */
    public static void applyPendingRestore(Context context) {
        File pending = pendingDir(context);
        File ready = new File(pending, "READY");
        if (!ready.isFile()) return;

        Map<File, File> rollbacks = new LinkedHashMap<>();
        try {
            JSONObject plan = new JSONObject(readUtf8(new File(pending, "plan.json")));
            JSONObject header = plan.getJSONObject("header");
            Set<String> selected = jsonStringSet(plan.getJSONArray("selected"));

            Map<String, List<File>> targets = resolveSelectedTargets(header, selected);
            File metadataFile = new File(pending, "metadata-selected.jsonl");

            // Isolated roots are swapped transactionally: keep the previous root beside
            // the destination until the whole restore succeeds. HOME remains merge-only.
            for (String id : selected) {
                if ("prefix".equals(id)) {
                    throw new IllegalStateException("Restauración automática de PREFIX no habilitada");
                }
                if (id.startsWith("proot:") || "models".equals(id)) {
                    List<File> roots = targets.get(id);
                    if (roots != null) {
                        for (File root : roots) {
                            File parent = root.getParentFile();
                            if (parent == null)
                                throw new IllegalStateException("Destino sin carpeta padre: " + root);
                            if (!parent.isDirectory() && !parent.mkdirs())
                                throw new IllegalStateException("No se pudo preparar " + parent);

                            File rollback = new File(parent, "." + root.getName() + ".newtermux-before-restore");

                            // A rollback left by a hard process death is authoritative: keep it.
                            // Remove only the partial destination and reuse the preserved original.
                            if (existsNoFollow(rollback)) {
                                deleteTree(root);
                            } else if (existsNoFollow(root)) {
                                if (!root.renameTo(rollback))
                                    throw new IllegalStateException("No se pudo preservar " + root);
                            }

                            // Track even a newly-created destination: on failure it must be
                            // removed instead of leaving a partial PRoot/model tree behind.
                            rollbacks.put(root, rollback);
                            if (!root.isDirectory() && !root.mkdirs())
                                throw new IllegalStateException("No se pudo preparar " + root);
                        }
                    }
                }
            }

            // Directories first.
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(new FileInputStream(metadataFile), StandardCharsets.UTF_8),
                    256 * 1024)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    JSONObject meta = new JSONObject(line);
                    if (!"dir".equals(meta.getString("type"))) continue;
                    File target = resolveMetadataTarget(targets, meta);
                    if (!target.isDirectory() && !target.mkdirs())
                        throw new IllegalStateException("No se pudo crear " + target);
                    // Keep directories writable until their children are published.
                }
            }

            // Files and symlinks.
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(new FileInputStream(metadataFile), StandardCharsets.UTF_8),
                    256 * 1024)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    JSONObject meta = new JSONObject(line);
                    String type = meta.getString("type");
                    if ("dir".equals(type)) continue;

                    File target = resolveMetadataTarget(targets, meta);
                    File parent = target.getParentFile();
                    if (parent != null && !parent.isDirectory() && !parent.mkdirs())
                        throw new IllegalStateException("No se pudo crear " + parent);

                    if ("file".equals(type)) {
                        File staged = safeStagePath(pending, meta.getString("zipEntry"));
                        copyFile(staged, target);
                        applyModeAndTime(target, meta, false);
                    } else if ("symlink".equals(type)) {
                        File tmp = File.createTempFile(".newtermux-restore-link-", ".tmp", parent);
                        if (!tmp.delete()) throw new IllegalStateException("No se pudo preparar enlace temporal");
                        Os.symlink(meta.getString("linkTarget"), tmp.getAbsolutePath());
                        try {
                            if (isDirectoryNoFollow(target)) deleteTree(target);
                            Os.rename(tmp.getAbsolutePath(), target.getAbsolutePath());
                        } catch (Exception e) {
                            deleteTree(tmp);
                            throw e;
                        }
                    }
                }
            }

            // Apply directory modes only after all payload is published. Otherwise a
            // read-only directory in the archive prevents its own children restoring.
            List<JSONObject> directories = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    new FileInputStream(metadataFile), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    JSONObject meta = new JSONObject(line);
                    if ("dir".equals(meta.getString("type"))) directories.add(meta);
                }
            }
            // Native v1 writes parents before children; finalize in reverse order
            // so restrictive parent modes cannot prevent finalizing a child.
            Collections.reverse(directories);
            for (JSONObject meta : directories)
                applyModeAndTime(resolveMetadataTarget(targets, meta), meta, false);
            for (File rollback : rollbacks.values()) deleteTree(rollback);
            deleteTree(pending);
        } catch (Exception e) {
            // Put isolated roots back if publishing failed. HOME writes use atomic file
            // replacement and may already contain safe merged entries; staging stays intact.
            List<Map.Entry<File, File>> entries = new ArrayList<>(rollbacks.entrySet());
            Collections.reverse(entries);
            for (Map.Entry<File, File> entry : entries) {
                try {
                    deleteTree(entry.getKey());
                    if (existsNoFollow(entry.getValue()) && !entry.getValue().renameTo(entry.getKey()))
                        throw new IllegalStateException("No se pudo revertir " + entry.getKey());
                } catch (Exception ignored) {}
            }
            try {
                writeUtf8(new File(pending, "LAST_ERROR.txt"),
                    new Date().toString() + "\n" + e.toString() + "\n");
            } catch (Exception ignored) {}
        }
    }

    private static JSONObject buildHeader(Context context, List<NativeStorageManager.Item> items) throws Exception {
        JSONObject header = new JSONObject();
        header.put("format", FORMAT);
        header.put("createdAt", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.ROOT).format(new Date()));
        header.put("packageName", context.getPackageName());
        header.put("appVersion", BuildConfig.VERSION_NAME);
        header.put("packageVariant", BuildConfig.TERMUX_PACKAGE_VARIANT);
        header.put("abi", Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0] : "unknown");
        header.put("androidSdk", Build.VERSION.SDK_INT);

        JSONArray components = new JSONArray();
        for (NativeStorageManager.Item item : items) {
            if (!item.selectable) continue;
            JSONObject c = new JSONObject();
            c.put("id", item.id);
            c.put("label", item.label);
            c.put("bytes", item.bytes);
            c.put("restorable", item.restorable);
            JSONArray roots = new JSONArray();
            for (File root : splitRoots(item.path)) roots.put(logicalRoot(root));
            c.put("roots", roots);
            components.put(c);
        }
        header.put("components", components);
        return header;
    }

    private static BackupInfo parseHeader(JSONObject header) throws Exception {
        String format = header.optString("format");
        if (!FORMAT.equals(format)) throw new IllegalArgumentException("Formato no compatible");

        List<ComponentInfo> components = new ArrayList<>();
        JSONArray arr = header.getJSONArray("components");
        for (int i = 0; i < arr.length(); i++) {
            JSONObject c = arr.getJSONObject(i);
            components.add(new ComponentInfo(
                c.getString("id"),
                c.optString("label", c.getString("id")),
                c.optLong("bytes", 0),
                c.optBoolean("restorable", false)
            ));
        }
        return new BackupInfo(
            format,
            header.optString("createdAt"),
            header.optString("appVersion"),
            header.optString("packageVariant"),
            header.optString("abi"),
            components
        );
    }

    private static void validateHeaderForRestore(Context context, JSONObject header, Set<String> selected) throws Exception {
        BackupInfo info = parseHeader(header);
        if (!context.getPackageName().equals(header.optString("packageName")))
            throw new IllegalArgumentException("El respaldo pertenece a otra identidad de aplicación");

        Set<String> available = new HashSet<>();
        Set<String> tokens = new HashSet<>();
        JSONArray components = header.getJSONArray("components");
        for (int i = 0; i < components.length(); i++) {
            JSONObject c = components.getJSONObject(i);
            String id = c.getString("id");
            if (!available.add(id) || !tokens.add(BackupArchivePolicy.componentToken(id)))
                throw new IllegalArgumentException("Componente duplicado o ambiguo: " + id);
            if (c.optLong("bytes", 0) < 0)
                throw new IllegalArgumentException("Tamaño de componente inválido");
            if (selected.contains(id) && !c.optBoolean("restorable", false))
                throw new IllegalArgumentException(id + " está disponible para respaldo pero no para restauración");
        }
        if (!available.containsAll(selected))
            throw new IllegalArgumentException("La selección contiene componentes que no existen en el respaldo");

        if (selected.contains("prefix")) {
            if (!BuildConfig.VERSION_NAME.equals(info.appVersion)
                    || !BuildConfig.TERMUX_PACKAGE_VARIANT.equals(info.packageVariant)
                    || (Build.SUPPORTED_ABIS.length > 0 && !Build.SUPPORTED_ABIS[0].equals(info.abi))) {
                throw new IllegalArgumentException("PREFIX requiere misma versión, variante y arquitectura");
            }
            throw new IllegalArgumentException("Restauración de PREFIX aún no está habilitada");
        }

        resolveSelectedTargets(header, selected); // validates logical roots before extracting.
    }

    private static Map<String, List<File>> resolveSelectedTargets(JSONObject header, Set<String> selected) throws Exception {
        Map<String, List<File>> out = new LinkedHashMap<>();
        JSONArray components = header.getJSONArray("components");
        Set<String> resolvedPaths = new HashSet<>();
        for (int i = 0; i < components.length(); i++) {
            JSONObject c = components.getJSONObject(i);
            String id = c.getString("id");
            if (!selected.contains(id)) continue;

            JSONArray roots = c.getJSONArray("roots");
            List<File> resolved = new ArrayList<>();
            for (int r = 0; r < roots.length(); r++) {
                File root = resolveLogicalRoot(roots.getJSONObject(r));
                validateTargetForComponent(id, root);
                if (!resolvedPaths.add(root.getCanonicalPath()))
                    throw new IllegalArgumentException("Raíz de restauración duplicada");
                resolved.add(root);
            }
            if (resolved.isEmpty())
                throw new IllegalArgumentException("Componente sin raíces: " + id);
            out.put(id, resolved);
        }
        return out;
    }

    private static JSONObject logicalRoot(File root) throws Exception {
        String path = root.getCanonicalPath();
        String home = TermuxConstants.TERMUX_HOME_DIR.getCanonicalPath();
        String prefix = TermuxConstants.TERMUX_PREFIX_DIR.getCanonicalPath();

        JSONObject o = new JSONObject();
        if (path.equals(home) || path.startsWith(home + File.separator)) {
            o.put("scope", "home");
            o.put("relative", relativeTo(home, path));
        } else if (path.equals(prefix) || path.startsWith(prefix + File.separator)) {
            o.put("scope", "prefix");
            o.put("relative", relativeTo(prefix, path));
        } else {
            throw new IllegalArgumentException("Ruta fuera de HOME/PREFIX: " + path);
        }
        return o;
    }

    private static File resolveLogicalRoot(JSONObject root) throws Exception {
        String scope = root.getString("scope");
        String rel = root.optString("relative", "");
        BackupArchivePolicy.logicalPath(rel, true);
        File base;
        if ("home".equals(scope)) base = TermuxConstants.TERMUX_HOME_DIR;
        else if ("prefix".equals(scope)) base = TermuxConstants.TERMUX_PREFIX_DIR;
        else throw new IllegalArgumentException("Scope inválido: " + scope);

        File resolved = rel.isEmpty() ? base : new File(base, rel);
        ensureInside(resolved, base);
        return resolved;
    }

    private static void validateTargetForComponent(String id, File root) throws Exception {
        if ("home".equals(id)) {
            if (!root.getCanonicalPath().equals(TermuxConstants.TERMUX_HOME_DIR.getCanonicalPath()))
                throw new IllegalArgumentException("Raíz HOME inválida");
            return;
        }
        if ("prefix".equals(id)) {
            if (!root.getCanonicalPath().equals(TermuxConstants.TERMUX_PREFIX_DIR.getCanonicalPath()))
                throw new IllegalArgumentException("Raíz PREFIX inválida");
            return;
        }
        if ("models".equals(id)) {
            ensureInside(root, TermuxConstants.TERMUX_HOME_DIR);
            String rel = relativeTo(TermuxConstants.TERMUX_HOME_DIR.getCanonicalPath(), root.getCanonicalPath());
            Set<String> allowed = new HashSet<>();
            Collections.addAll(allowed,
                "llm-models", "models", ".ollama/models", ".cache/huggingface/hub", ".cache/llama.cpp");
            if (!allowed.contains(rel.replace(File.separatorChar, '/')))
                throw new IllegalArgumentException("Ruta de modelos no reconocida: " + rel);
            return;
        }
        if (id.startsWith("proot:")) {
            File base = new File(TermuxConstants.TERMUX_PREFIX_DIR, "var/lib/proot-distro");
            ensureInside(root, base);
            String rel = relativeTo(base.getCanonicalPath(), root.getCanonicalPath());
            String[] parts = rel.split("/", -1);
            String name = id.substring("proot:".length());
            if (parts.length != 2 || !("containers".equals(parts[0])
                    || "installed-rootfs".equals(parts[0])) || !parts[1].equals(name) || name.isEmpty())
                throw new IllegalArgumentException("Raíz PRoot inválida: " + rel);
            return;
        }
        throw new IllegalArgumentException("Componente no restaurable: " + id);
    }

    private static long selectedBytes(JSONObject header, Set<String> selected) throws Exception {
        long total = 0;
        JSONArray components = header.getJSONArray("components");
        for (int i = 0; i < components.length(); i++) {
            JSONObject c = components.getJSONObject(i);
            if (selected.contains(c.getString("id"))) {
                long bytes = c.optLong("bytes", 0);
                if (bytes < 0 || bytes > Long.MAX_VALUE - MIN_HEADROOM - total)
                    throw new IllegalArgumentException("Tamaño de respaldo inválido");
                total += bytes;
            }
        }
        return total;
    }

    private static void backupTree(
            ZipOutputStream zip,
            BufferedWriter metadata,
            MessageDigest treeDigest,
            String component,
            int rootIndex,
            File root,
            File file,
            Set<String> excludes,
            long[] done,
            long total,
            Progress progress,
            Cancellation cancellation) throws Exception {
        throwIfCancelled(cancellation);
        String filePath = file.getAbsolutePath();
        StructStat st = Os.lstat(filePath);
        String rel = relativeTo(root.getAbsolutePath(), filePath).replace(File.separatorChar, '/');

        if (OsConstants.S_ISLNK(st.st_mode)) {
            JSONObject meta = baseMeta(component, rootIndex, rel, "symlink", st);
            meta.put("linkTarget", Os.readlink(file.getAbsolutePath()));
            writeMetadata(metadata, treeDigest, meta);
            return;
        }

        String canonical = file.getCanonicalPath();
        if (isExcluded(canonical, excludes)) return;

        if (OsConstants.S_ISDIR(st.st_mode)) {
            JSONObject meta = baseMeta(component, rootIndex, rel, "dir", st);
            writeMetadata(metadata, treeDigest, meta);
            File[] children = file.listFiles();
            if (children == null) return;
            for (File child : children) {
                backupTree(zip, metadata, treeDigest, component, rootIndex, root, child,
                    excludes, done, total, progress, cancellation);
            }
            return;
        }

        if (!OsConstants.S_ISREG(st.st_mode)) {
            JSONObject meta = baseMeta(component, rootIndex, rel, "skipped-special", st);
            writeMetadata(metadata, treeDigest, meta);
            return;
        }

        String zipName = dataEntryName(component, rootIndex, rel);
        ZipEntry ze = new ZipEntry(zipName);
        ze.setTime(Math.max(0, st.st_mtime * 1000L));
        zip.putNextEntry(ze);

        MessageDigest fileDigest = MessageDigest.getInstance("SHA-256");
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buf = new byte[1024 * 1024];
            int n;
            while ((n = fis.read(buf)) != -1) {
                throwIfCancelled(cancellation);
                zip.write(buf, 0, n);
                fileDigest.update(buf, 0, n);
                done[0] += n;
                if (progress != null) progress.onProgress("Respaldando " + component, done[0], total);
            }
        }
        zip.closeEntry();

        JSONObject meta = baseMeta(component, rootIndex, rel, "file", st);
        meta.put("size", st.st_size);
        meta.put("sha256", hex(fileDigest.digest()));
        meta.put("zipEntry", zipName);
        writeMetadata(metadata, treeDigest, meta);
    }

    private static JSONObject baseMeta(
            String component, int rootIndex, String path, String type, StructStat st) throws Exception {
        JSONObject o = new JSONObject();
        o.put("component", component);
        o.put("root", rootIndex);
        o.put("path", path);
        o.put("type", type);
        o.put("mode", st.st_mode & 07777);
        o.put("mtime", st.st_mtime * 1000L);
        return o;
    }

    private static void writeMetadata(
            BufferedWriter writer, MessageDigest digest, JSONObject metadata) throws Exception {
        String line = metadata.toString();
        writer.write(line);
        writer.newLine();
        digest.update((line + "\n").getBytes(StandardCharsets.UTF_8));
    }

    private static Set<String> excludesFor(String id) {
        Set<String> excludes = new HashSet<>();
        try {
            if ("home".equals(id)) {
                File home = TermuxConstants.TERMUX_HOME_DIR;
                addCanonical(excludes, new File(home, "llm-models"));
                addCanonical(excludes, new File(home, "models"));
                addCanonical(excludes, new File(home, ".ollama/models"));
                addCanonical(excludes, new File(home, ".cache"));
                addCanonical(excludes, new File(home, "tbm_backups"));
                addCanonical(excludes, new File(home, ".tbm/backups"));
                // ~/storage is normally a symlink and is already skipped by lstat.
            } else if ("prefix".equals(id)) {
                File prefix = TermuxConstants.TERMUX_PREFIX_DIR;
                addCanonical(excludes, new File(prefix, "var/lib/proot-distro"));
                addCanonical(excludes, new File(prefix, "tmp"));
            }
        } catch (Exception ignored) {}
        return excludes;
    }

    private static void addCanonical(Set<String> out, File file) throws Exception {
        if (file.exists()) out.add(file.getCanonicalPath());
    }

    private static boolean isExcluded(String path, Set<String> excludes) {
        for (String ex : excludes)
            if (path.equals(ex) || path.startsWith(ex + File.separator)) return true;
        return false;
    }

    private static List<File> splitRoots(String raw) {
        List<File> roots = new ArrayList<>();
        if (raw == null || raw.isEmpty()) return roots;
        for (String line : raw.split("\\n")) {
            if (!line.trim().isEmpty()) roots.add(new File(line.trim()));
        }
        return roots;
    }

    private static String dataEntryName(String component, int root, String rel) {
        return BackupArchivePolicy.dataEntryName(component, root, rel);
    }

    private static String componentFromDataEntry(String name) {
        int slash = name.indexOf('/', 5);
        if (slash < 0) throw new IllegalArgumentException("Entrada de datos inválida");
        return name.substring(5, slash);
    }

    private static File safeStagePath(File pending, String zipName) throws Exception {
        File target = new File(pending, zipName);
        ensureInside(target, pending);
        return target;
    }

    private static File resolveMetadataTarget(
            Map<String, List<File>> targets, JSONObject meta) throws Exception {
        String component = meta.getString("component");
        int root = meta.getInt("root");
        List<File> roots = targets.get(component);
        if (roots == null || root < 0 || root >= roots.size())
            throw new IllegalArgumentException("Raíz inválida para " + component);
        File base = roots.get(root);
        String rel = meta.optString("path", "");
        BackupArchivePolicy.logicalPath(rel, true);
        File target = rel.isEmpty() ? base : new File(base, rel);
        ensureInside(target, base);
        return target;
    }

    private static void applyModeAndTime(File file, JSONObject meta, boolean symlink) {
        if (!symlink) {
            try { Os.chmod(file.getAbsolutePath(), meta.optInt("mode", 0600)); } catch (Exception ignored) {}
            long time = meta.optLong("mtime", 0);
            if (time > 0) file.setLastModified(time);
        }
    }

    private static void ensureInside(File child, File base) throws Exception {
        String c = child.getCanonicalPath();
        String b = base.getCanonicalPath();
        if (!c.equals(b) && !c.startsWith(b + File.separator))
            throw new SecurityException("Ruta fuera del destino permitido: " + c);
    }

    private static String relativeTo(String base, String child) {
        if (child.equals(base)) return "";
        if (child.startsWith(base + File.separator)) return child.substring(base.length() + 1);
        throw new IllegalArgumentException("Ruta fuera de raíz");
    }

    private static void writeTextEntry(ZipOutputStream zip, String name, String text) throws Exception {
        ZipEntry entry = new ZipEntry(name);
        zip.putNextEntry(entry);
        zip.write(text.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static void addFileEntry(ZipOutputStream zip, String name, File file) throws Exception {
        ZipEntry entry = new ZipEntry(name);
        zip.putNextEntry(entry);
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buf = new byte[256 * 1024];
            int n;
            while ((n = fis.read(buf)) != -1) zip.write(buf, 0, n);
        }
        zip.closeEntry();
    }

    private static String readSmallText(InputStream in, int maxBytes) throws Exception {
        byte[] buf = new byte[8192];
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int n;
        while ((n = in.read(buf)) != -1) {
            if (out.size() + n > maxBytes) throw new IllegalArgumentException("Metadata demasiado grande");
            out.write(buf, 0, n);
        }
        return out.toString(StandardCharsets.UTF_8.name());
    }

    private static String readMetadataLine(BufferedReader reader) throws Exception {
        StringBuilder line = new StringBuilder();
        int c;
        while ((c = reader.read()) != -1) {
            if (c == '\n') return line.toString();
            if (line.length() >= 1024 * 1024)
                throw new IllegalArgumentException("Registro de metadata demasiado grande");
            line.append((char) c);
        }
        return line.length() == 0 ? null : line.toString();
    }

    private static void drain(InputStream in) throws Exception {
        byte[] buf = new byte[256 * 1024];
        while (in.read(buf) != -1) {}
    }

    private static String sha256File(File file) throws Exception {
        return sha256File(file, null);
    }

    private static String sha256File(File file, Cancellation cancellation) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] buf = new byte[1024 * 1024];
            int n;
            while ((n = in.read(buf)) != -1) {
                throwIfCancelled(cancellation);
                digest.update(buf, 0, n);
            }
        }
        throwIfCancelled(cancellation);
        return hex(digest.digest());
    }

    private static void throwIfCancelled(Cancellation cancellation) {
        if (cancellation != null && cancellation.isCancelled())
            throw new CancellationException("Respaldo cancelado");
    }

    private static String hex(byte[] bytes) {
        StringBuilder b = new StringBuilder(bytes.length * 2);
        for (byte v : bytes) b.append(String.format(Locale.ROOT, "%02x", v & 0xff));
        return b.toString();
    }

    private static Set<String> jsonStringSet(JSONArray array) throws Exception {
        Set<String> out = new HashSet<>();
        for (int i = 0; i < array.length(); i++) out.add(array.getString(i));
        return out;
    }

    private static File pendingDir(Context context) {
        return new File(context.getNoBackupFilesDir(), "native-restore-pending");
    }

    private static void ensureRestoreSpace(Context context, long selectedBytes) {
        StatFs stat = new StatFs(context.getNoBackupFilesDir().getAbsolutePath());
        long needed = Math.max(0, selectedBytes) + MIN_HEADROOM;
        if (stat.getAvailableBytes() < needed)
            throw new IllegalStateException(
                "No hay espacio interno suficiente para preparar la restauración");
    }

    private static boolean existsNoFollow(File file) {
        if (file == null) return false;
        try {
            Os.lstat(file.getAbsolutePath());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isDirectoryNoFollow(File file) {
        if (file == null) return false;
        try {
            return OsConstants.S_ISDIR(Os.lstat(file.getAbsolutePath()).st_mode);
        } catch (Exception e) {
            return false;
        }
    }

    private static void copyFile(File source, File target) throws Exception {
        // A fixed name may already be a HOME symlink. Create a fresh sibling so
        // opening the temporary file cannot follow that link and overwrite its target.
        File tmp = File.createTempFile(".newtermux-restore-", ".tmp", target.getParentFile());
        try {
            try (FileInputStream in = new FileInputStream(source);
                 FileOutputStream out = new FileOutputStream(tmp)) {
                byte[] buf = new byte[1024 * 1024];
                int n;
                while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
                out.getFD().sync();
            }
            Os.rename(tmp.getAbsolutePath(), target.getAbsolutePath());
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo publicar " + target, e);
        } finally {
            tmp.delete();
        }
    }

    private static void deleteTree(File file) {
        if (file == null) return;
        StructStat st;
        try {
            st = Os.lstat(file.getAbsolutePath());
        } catch (Exception e) {
            return;
        }
        if (OsConstants.S_ISLNK(st.st_mode) || !OsConstants.S_ISDIR(st.st_mode)) {
            file.delete();
            return;
        }
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteTree(child);
        file.delete();
    }

    private static void writeUtf8(File file, String text) throws Exception {
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs())
            throw new IllegalStateException("No se pudo crear " + parent);
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
            out.getFD().sync();
        }
    }

    private static String readUtf8(File file) throws Exception {
        try (FileInputStream in = new FileInputStream(file)) {
            return readSmallText(in, 8 * 1024 * 1024);
        }
    }
}
