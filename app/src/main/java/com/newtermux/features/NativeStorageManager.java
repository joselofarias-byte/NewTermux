package com.newtermux.features;

import android.content.Context;
import android.os.Environment;
import android.os.StatFs;
import android.os.SystemClock;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructStat;

import com.termux.shared.termux.TermuxConstants;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Native storage inventory for NewTermux.
 *
 * The scanner never follows symlinks, deduplicates hard links by (dev,inode),
 * and uses mutually-exclusive roots so the UI does not count the same bytes
 * twice when, for example, a model directory lives inside HOME.
 */
public final class NativeStorageManager {

    public static final class Item {
        public final String id;
        public final String label;
        public final String path;
        public final long bytes;
        public final boolean selectable;
        public final boolean restorable;
        public final String detail;

        public Item(String id, String label, String path, long bytes,
                    boolean selectable, boolean restorable, String detail) {
            this.id = id;
            this.label = label;
            this.path = path;
            this.bytes = bytes;
            this.selectable = selectable;
            this.restorable = restorable;
            this.detail = detail;
        }
    }

    public static final class Snapshot {
        public final List<Item> items;
        public final long totalBytes;
        public final long freeBytes;
        public final long measuredBytes;

        public Snapshot(List<Item> items, long totalBytes, long freeBytes, long measuredBytes) {
            this.items = Collections.unmodifiableList(items);
            this.totalBytes = totalBytes;
            this.freeBytes = freeBytes;
            this.measuredBytes = measuredBytes;
        }
    }

    public interface Progress {
        void onProgress(String phase, int completed, int total);
    }

    private NativeStorageManager() {}

    public static Snapshot scan(Context context) {
        return scan(context, null);
    }

    public static Snapshot scan(Context context, Progress progress) {
        File home = TermuxConstants.TERMUX_HOME_DIR;
        File prefix = TermuxConstants.TERMUX_PREFIX_DIR;
        File prootBase = new File(prefix, "var/lib/proot-distro");

        List<File> modelRoots = existingUnique(
            new File(home, "llm-models"),
            new File(home, "models"),
            new File(home, ".ollama/models"),
            new File(home, ".cache/huggingface/hub"),
            new File(home, ".cache/llama.cpp")
        );

        List<File> backupRoots = existingUnique(
            new File(home, "tbm_backups"),
            new File(home, ".tbm/backups"),
            new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "NewTermux/Backups")
        );

        List<File> cacheRoots = existingUnique(
            context.getCacheDir(),
            new File(home, ".cache"),
            new File(prefix, "tmp")
        );

        Set<String> homeExcludes = canonicalSet(modelRoots);
        addAll(homeExcludes, backupRoots);
        // HOME cache is presented separately. Never exclude app cache because it is not inside HOME.
        addIfInside(homeExcludes, new File(home, ".cache"), home);

        Set<String> prefixExcludes = new LinkedHashSet<>();
        addIfInside(prefixExcludes, prootBase, prefix);
        addIfInside(prefixExcludes, new File(prefix, "tmp"), prefix);

        List<Item> items = new ArrayList<>();
        Set<String> globalSeen = new HashSet<>();
        List<ProotRoot> proots = discoverProots(prootBase);
        final int totalPhases = 6 + proots.size();
        int phase = 0;

        report(progress, "Calculando HOME", phase, totalPhases);
        long homeBytes = sizeTree(home, homeExcludes, globalSeen, progress, "Calculando HOME", phase, totalPhases);
        items.add(new Item(
            "home", "HOME", home.getAbsolutePath(), homeBytes,
            true, true, "Archivos personales y proyectos; modelos, cachés y respaldos se muestran aparte."
        ));
        report(progress, "HOME listo", ++phase, totalPhases);

        report(progress, "Calculando paquetes / PREFIX", phase, totalPhases);
        long prefixBytes = sizeTree(prefix, prefixExcludes, globalSeen, progress, "Calculando paquetes / PREFIX", phase, totalPhases);
        items.add(new Item(
            "prefix", "Paquetes / PREFIX", prefix.getAbsolutePath(), prefixBytes,
            true, false, "Paquetes y herramientas de NewTermux. Restauración requiere compatibilidad exacta."
        ));
        report(progress, "PREFIX listo", ++phase, totalPhases);

        for (ProotRoot p : proots) {
            report(progress, "Calculando PRoot · " + p.name, phase, totalPhases);
            long bytes = sizeTree(p.root, Collections.emptySet(), globalSeen, progress, "Calculando PRoot · " + p.name, phase, totalPhases);
            items.add(new Item(
                "proot:" + p.name, "PRoot · " + p.name, p.root.getAbsolutePath(), bytes,
                true, true, "Contenedor completo administrado por proot-distro (rootfs y metadata)."
            ));
            report(progress, "PRoot · " + p.name + " listo", ++phase, totalPhases);
        }

        report(progress, "Calculando modelos LLM", phase, totalPhases);
        long modelBytes = sizeRoots(modelRoots, Collections.emptySet(), globalSeen, progress, "Calculando modelos LLM", phase, totalPhases);
        if (modelBytes > 0 || !modelRoots.isEmpty()) {
            items.add(new Item(
                "models", "Modelos LLM", joinPaths(modelRoots), modelBytes,
                true, true, "Modelos detectados en rutas conocidas de HOME."
            ));
        }
        report(progress, "Modelos listos", ++phase, totalPhases);

        // Cache roots may contain model roots; exclude those so models are never counted twice.
        report(progress, "Calculando cachés y temporales", phase, totalPhases);
        long cacheBytes = sizeRoots(cacheRoots, canonicalSet(modelRoots), globalSeen, progress, "Calculando cachés y temporales", phase, totalPhases);
        items.add(new Item(
            "cache", "Cachés y temporales", joinPaths(cacheRoots), cacheBytes,
            false, false, "Contenido regenerable. No se incluye en respaldos."
        ));
        report(progress, "Cachés listas", ++phase, totalPhases);

        report(progress, "Calculando respaldos", phase, totalPhases);
        long backupBytes = sizeRoots(backupRoots, Collections.emptySet(), globalSeen, progress, "Calculando respaldos", phase, totalPhases);
        items.add(new Item(
            "backups", "Respaldos", joinPaths(backupRoots), backupBytes,
            false, false, "Los respaldos existentes nunca se incluyen dentro de otro respaldo."
        ));
        report(progress, "Respaldos listos", ++phase, totalPhases);

        File sharedLogs = new File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "NewTermux"
        );
        report(progress, "Calculando salidas y registros", phase, totalPhases);
        Set<String> logExcludes = canonicalSet(backupRoots);
        long outputBytes = sizeTree(sharedLogs, logExcludes, globalSeen, progress, "Calculando salidas y registros", phase, totalPhases);
        if (outputBytes > 0) {
            items.add(new Item(
                "outputs", "Salidas y registros", sharedLogs.getAbsolutePath(), outputBytes,
                false, false, "Archivos exportados y registros comprimidos de NewTermux."
            ));
        }
        report(progress, "Inventario terminado", ++phase, totalPhases);

        StatFs stat = new StatFs(context.getFilesDir().getAbsolutePath());
        long total = stat.getTotalBytes();
        long free = stat.getAvailableBytes();
        long measured = 0;
        for (Item item : items) measured += Math.max(0, item.bytes);

        return new Snapshot(items, total, free, measured);
    }

    private static final class ProotRoot {
        final String name;
        final File root;
        ProotRoot(String name, File root) {
            this.name = name;
            this.root = root;
        }
    }

    private static List<ProotRoot> discoverProots(File base) {
        List<ProotRoot> out = new ArrayList<>();
        Set<String> names = new HashSet<>();

        File containers = new File(base, "containers");
        File[] current = safeList(containers);
        if (current != null) {
            for (File c : current) {
                File rootfs = new File(c, "rootfs");
                if (isDirectoryNoFollow(c) && isDirectoryNoFollow(rootfs) && names.add(c.getName())) {
                    // Keep the complete container so proot-distro metadata beside rootfs is
                    // measured, backed up and restored together with the filesystem.
                    out.add(new ProotRoot(c.getName(), c));
                }
            }
        }

        File legacy = new File(base, "installed-rootfs");
        File[] old = safeList(legacy);
        if (old != null) {
            for (File root : old) {
                if (isDirectoryNoFollow(root) && names.add(root.getName())) {
                    out.add(new ProotRoot(root.getName(), root));
                }
            }
        }

        out.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
        return out;
    }

    private static final class ScanProgressState {
        long visited;
        long bytes;
        long lastReportMs;
    }

    private static long sizeRoots(List<File> roots, Set<String> excludes, Set<String> seen,
                                  Progress progress, String phase, int completed, int totalPhases) {
        ScanProgressState state = new ScanProgressState();
        long total = 0;
        for (File root : roots) {
            total += sizeTreeInternal(root, excludes, seen, progress, phase, completed, totalPhases, state);
        }
        return total;
    }

    private static long sizeTree(File root, Set<String> excludes, Set<String> seen,
                                 Progress progress, String phase, int completed, int totalPhases) {
        return sizeTreeInternal(root, excludes, seen, progress, phase, completed, totalPhases,
            new ScanProgressState());
    }

    private static long sizeTreeInternal(File root, Set<String> excludes, Set<String> seen,
                                         Progress progress, String phase, int completed, int totalPhases,
                                         ScanProgressState state) {
        if (root == null || !root.exists()) return 0;

        long sum = 0;
        ArrayDeque<File> pending = new ArrayDeque<>();
        pending.push(root);

        while (!pending.isEmpty()) {
            File current = pending.pop();
            state.visited++;
            String path = current.getAbsolutePath();
            if (isExcluded(path, excludes)) {
                maybeReportTreeProgress(progress, phase, completed, totalPhases, state);
                continue;
            }

            StructStat st;
            try {
                st = Os.lstat(path);
            } catch (Exception e) {
                maybeReportTreeProgress(progress, phase, completed, totalPhases, state);
                continue;
            }

            // Never follow symlinks. This also keeps ~/storage from exploding the scan.
            if (OsConstants.S_ISLNK(st.st_mode)) {
                maybeReportTreeProgress(progress, phase, completed, totalPhases, state);
                continue;
            }

            String inode = st.st_dev + ":" + st.st_ino;
            if (!seen.add(inode)) {
                maybeReportTreeProgress(progress, phase, completed, totalPhases, state);
                continue;
            }

            if (OsConstants.S_ISREG(st.st_mode)) {
                long bytes = Math.max(0, st.st_size);
                sum += bytes;
                state.bytes += bytes;
                maybeReportTreeProgress(progress, phase, completed, totalPhases, state);
                continue;
            }
            if (!OsConstants.S_ISDIR(st.st_mode)) {
                maybeReportTreeProgress(progress, phase, completed, totalPhases, state);
                continue;
            }

            File[] children = safeList(current);
            if (children != null) {
                for (File child : children) pending.push(child);
            }
            maybeReportTreeProgress(progress, phase, completed, totalPhases, state);
        }

        return sum;
    }

    private static void maybeReportTreeProgress(Progress progress, String phase, int completed,
                                                int totalPhases, ScanProgressState state) {
        if (progress == null) return;
        long now = SystemClock.elapsedRealtime();
        if (state.lastReportMs != 0 && now - state.lastReportMs < 750) return;
        state.lastReportMs = now;
        String detail = String.format(
            Locale.getDefault(),
            "%s · %,d elementos · %s",
            phase,
            state.visited,
            formatBytes(state.bytes)
        );
        report(progress, detail, completed, totalPhases);
    }

    private static boolean isExcluded(String path, Set<String> excludes) {
        for (String ex : excludes) {
            if (path.equals(ex) || path.startsWith(ex + File.separator)) return true;
        }
        return false;
    }

    private static List<File> existingUnique(File... candidates) {
        List<File> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (File file : candidates) {
            if (file == null || !file.exists()) continue;
            String c = canonical(file);
            if (c != null && seen.add(c)) out.add(file);
        }
        return out;
    }

    private static Set<String> canonicalSet(List<File> files) {
        Set<String> out = new LinkedHashSet<>();
        addAll(out, files);
        return out;
    }

    private static void addAll(Set<String> target, List<File> files) {
        for (File file : files) {
            String c = canonical(file);
            if (c != null) target.add(c);
        }
    }

    private static void addIfInside(Set<String> target, File candidate, File parent) {
        if (candidate == null || !candidate.exists()) return;
        String c = canonical(candidate);
        String p = canonical(parent);
        if (c != null && p != null && (c.equals(p) || c.startsWith(p + File.separator))) {
            target.add(c);
        }
    }

    /**
     * Scanner paths deliberately use absolute paths instead of getCanonicalPath().
     * Calling getCanonicalPath() for every entry performs extra filesystem resolution
     * and made large HOME/PRoot inventories take minutes on-device. Symlink safety is
     * enforced separately with lstat(), and hard links are deduplicated by inode.
     */
    private static String canonical(File file) {
        return file == null ? null : file.getAbsolutePath();
    }

    private static void report(Progress progress, String phase, int completed, int total) {
        if (progress != null) progress.onProgress(phase, completed, total);
    }

    private static boolean isDirectoryNoFollow(File file) {
        try {
            StructStat st = Os.lstat(file.getAbsolutePath());
            return OsConstants.S_ISDIR(st.st_mode);
        } catch (Exception e) {
            return false;
        }
    }

    private static File[] safeList(File dir) {
        try {
            return dir.listFiles();
        } catch (SecurityException e) {
            return null;
        }
    }

    private static String joinPaths(List<File> files) {
        if (files.isEmpty()) return "";
        StringBuilder b = new StringBuilder();
        for (File file : files) {
            if (b.length() > 0) b.append("\n");
            b.append(file.getAbsolutePath());
        }
        return b.toString();
    }

    public static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        final String[] units = {"KB", "MB", "GB", "TB"};
        double value = bytes;
        int unit = -1;
        do {
            value /= 1024.0;
            unit++;
        } while (value >= 1024 && unit < units.length - 1);
        return String.format(Locale.getDefault(), value >= 100 ? "%.0f %s" : value >= 10 ? "%.1f %s" : "%.2f %s",
            value, units[unit]);
    }
}
