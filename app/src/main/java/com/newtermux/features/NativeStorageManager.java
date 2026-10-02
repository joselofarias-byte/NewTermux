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
        /** Logical payload bytes, used for backup planning and metadata. */
        public final long bytes;
        /** Blocks actually allocated on disk, used for storage accounting UI. */
        public final long allocatedBytes;
        public final boolean selectable;
        public final boolean restorable;
        public final String detail;

        public Item(String id, String label, String path, long bytes, long allocatedBytes,
                    boolean selectable, boolean restorable, String detail) {
            this.id = id;
            this.label = label;
            this.path = path;
            this.bytes = bytes;
            this.allocatedBytes = allocatedBytes;
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

        File downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File sharedStorage = Environment.getExternalStorageDirectory();

        List<File> backupRoots = existingUnique(
            new File(home, "tbm_backups"),
            new File(home, ".tbm/backups"),
            new File(downloads, "NewTermux/Backups"),
            new File(downloads, "tbm_backups"),
            new File(sharedStorage, "Download-Folders/tbm_backups")
        );

        // TBM keeps large recovery workspaces under ~/.tbm. The entire workspace is
        // separate from active HOME, then split into useful categories below.
        File tbmRoot = new File(home, ".tbm");
        File tbmTmpRoot = new File(tbmRoot, "tmp");
        File tbmCutoverSourceRoot = new File(tbmRoot, "cutover-source");
        List<File> tbmStageRoots = discoverTbmStageRoots(home);

        List<File> cacheRoots = existingUnique(
            context.getCacheDir(),
            new File(home, ".cache"),
            new File(prefix, "tmp")
        );

        Set<String> homeExcludes = canonicalSet(modelRoots);
        addAll(homeExcludes, backupRoots);
        // Exclude ALL of ~/.tbm from active HOME. Individual TBM categories are measured below.
        addIfInside(homeExcludes, tbmRoot, home);
        // HOME cache is presented separately. Never exclude app cache because it is not inside HOME.
        addIfInside(homeExcludes, new File(home, ".cache"), home);

        Set<String> tbmOtherExcludes = new LinkedHashSet<>();
        addIfInside(tbmOtherExcludes, tbmTmpRoot, tbmRoot);
        addIfInside(tbmOtherExcludes, tbmCutoverSourceRoot, tbmRoot);
        for (File stageRoot : tbmStageRoots) addIfInside(tbmOtherExcludes, stageRoot, tbmRoot);
        for (File backupRoot : backupRoots) addIfInside(tbmOtherExcludes, backupRoot, tbmRoot);

        Set<String> prefixExcludes = new LinkedHashSet<>();
        addIfInside(prefixExcludes, prootBase, prefix);
        addIfInside(prefixExcludes, new File(prefix, "tmp"), prefix);

        List<Item> items = new ArrayList<>();
        Set<String> globalSeen = new HashSet<>();
        List<ProotRoot> proots = discoverProots(prootBase);
        final int totalPhases = 10 + proots.size();
        int phase = 0;

        report(progress, "Calculando HOME", phase, totalPhases);
        Usage homeUsage = sizeTree(home, homeExcludes, globalSeen, progress, "Calculando HOME", phase, totalPhases);
        items.add(new Item(
            "home", "HOME", home.getAbsolutePath(), homeUsage.logicalBytes, homeUsage.allocatedBytes,
            true, true, "HOME activo; modelos, cachés, respaldos y todo el espacio de trabajo TBM se muestran aparte."
        ));
        report(progress, "HOME listo", ++phase, totalPhases);

        report(progress, "Calculando paquetes / PREFIX", phase, totalPhases);
        Usage prefixUsage = sizeTree(prefix, prefixExcludes, globalSeen, progress, "Calculando paquetes / PREFIX", phase, totalPhases);
        items.add(new Item(
            "prefix", "Paquetes / PREFIX", prefix.getAbsolutePath(), prefixUsage.logicalBytes, prefixUsage.allocatedBytes,
            true, false, "Paquetes y herramientas de NewTermux. Restauración requiere compatibilidad exacta."
        ));
        report(progress, "PREFIX listo", ++phase, totalPhases);

        for (ProotRoot p : proots) {
            report(progress, "Calculando PRoot · " + p.name, phase, totalPhases);
            Usage usage = sizeTree(p.root, Collections.emptySet(), globalSeen, progress, "Calculando PRoot · " + p.name, phase, totalPhases);
            items.add(new Item(
                "proot:" + p.name, "PRoot · " + p.name, p.root.getAbsolutePath(), usage.logicalBytes, usage.allocatedBytes,
                true, true, "Contenedor completo administrado por proot-distro (rootfs y metadata)."
            ));
            report(progress, "PRoot · " + p.name + " listo", ++phase, totalPhases);
        }

        report(progress, "Calculando modelos LLM", phase, totalPhases);
        Usage modelUsage = sizeRoots(modelRoots, Collections.emptySet(), globalSeen, progress, "Calculando modelos LLM", phase, totalPhases);
        if (modelUsage.logicalBytes > 0 || modelUsage.allocatedBytes > 0 || !modelRoots.isEmpty()) {
            items.add(new Item(
                "models", "Modelos LLM", joinPaths(modelRoots), modelUsage.logicalBytes, modelUsage.allocatedBytes,
                true, true, "Modelos activos detectados en rutas conocidas de HOME."
            ));
        }
        report(progress, "Modelos listos", ++phase, totalPhases);

        // Cache roots may contain model roots; exclude those so models are never counted twice.
        report(progress, "Calculando cachés y temporales", phase, totalPhases);
        Usage cacheUsage = sizeRoots(cacheRoots, canonicalSet(modelRoots), globalSeen, progress, "Calculando cachés y temporales", phase, totalPhases);
        items.add(new Item(
            "cache", "Cachés y temporales", joinPaths(cacheRoots), cacheUsage.logicalBytes, cacheUsage.allocatedBytes,
            false, false, "Contenido regenerable. No se incluye en respaldos."
        ));
        report(progress, "Cachés listas", ++phase, totalPhases);

        report(progress, "Calculando temporales de TBM", phase, totalPhases);
        Usage tbmTmpUsage = sizeTree(tbmTmpRoot, Collections.emptySet(), globalSeen, progress,
            "Calculando temporales de TBM", phase, totalPhases);
        if (tbmTmpUsage.logicalBytes > 0 || tbmTmpUsage.allocatedBytes > 0 || tbmTmpRoot.exists()) {
            items.add(new Item(
                "tbm-tmp", "TBM · temporales / probes", tbmTmpRoot.getAbsolutePath(),
                tbmTmpUsage.logicalBytes, tbmTmpUsage.allocatedBytes,
                false, false, "Área temporal de probes, resume y staging transitorio de TBM. No forma parte del HOME activo."
            ));
        }
        report(progress, "Temporales de TBM listos", ++phase, totalPhases);

        report(progress, "Calculando fuente cutover de TBM", phase, totalPhases);
        Usage tbmCutoverUsage = sizeTree(tbmCutoverSourceRoot, Collections.emptySet(), globalSeen, progress,
            "Calculando fuente cutover de TBM", phase, totalPhases);
        if (tbmCutoverUsage.logicalBytes > 0 || tbmCutoverUsage.allocatedBytes > 0 || tbmCutoverSourceRoot.exists()) {
            items.add(new Item(
                "tbm-cutover-source", "TBM · fuente cutover", tbmCutoverSourceRoot.getAbsolutePath(),
                tbmCutoverUsage.logicalBytes, tbmCutoverUsage.allocatedBytes,
                false, false, "Archivo/fuente de migración y manifest de TBM. Se conserva separado del HOME activo."
            ));
        }
        report(progress, "Fuente cutover de TBM lista", ++phase, totalPhases);

        report(progress, "Calculando staging de TBM", phase, totalPhases);
        Usage tbmStageUsage = sizeRoots(tbmStageRoots, Collections.emptySet(), globalSeen, progress,
            "Calculando staging de TBM", phase, totalPhases);
        if (tbmStageUsage.logicalBytes > 0 || tbmStageUsage.allocatedBytes > 0 || !tbmStageRoots.isEmpty()) {
            items.add(new Item(
                "tbm-staging", "TBM · staging / recuperación", joinPaths(tbmStageRoots),
                tbmStageUsage.logicalBytes, tbmStageUsage.allocatedBytes,
                false, false, "Copias históricas de cutover/restauración. Se separan de HOME y no entran al respaldo nativo."
            ));
        }
        report(progress, "Staging de TBM listo", ++phase, totalPhases);

        report(progress, "Calculando metadatos de TBM", phase, totalPhases);
        Usage tbmOtherUsage = sizeTree(tbmRoot, tbmOtherExcludes, globalSeen, progress,
            "Calculando metadatos de TBM", phase, totalPhases);
        if (tbmOtherUsage.logicalBytes > 0 || tbmOtherUsage.allocatedBytes > 0 || tbmRoot.exists()) {
            items.add(new Item(
                "tbm-other", "TBM · metadatos / otros", tbmRoot.getAbsolutePath(),
                tbmOtherUsage.logicalBytes, tbmOtherUsage.allocatedBytes,
                false, false, "Estado, rollback, binarios y metadatos TBM fuera de temporales, cutover, staging y respaldos."
            ));
        }
        report(progress, "Metadatos de TBM listos", ++phase, totalPhases);

        report(progress, "Calculando respaldos", phase, totalPhases);
        Usage backupUsage = sizeRoots(backupRoots, Collections.emptySet(), globalSeen, progress, "Calculando respaldos", phase, totalPhases);
        items.add(new Item(
            "backups", "Respaldos", joinPaths(backupRoots), backupUsage.logicalBytes, backupUsage.allocatedBytes,
            false, false, "Incluye respaldos NewTermux/TBM detectados; nunca se incluyen dentro de otro respaldo."
        ));
        report(progress, "Respaldos listos", ++phase, totalPhases);

        File sharedLogs = new File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "NewTermux"
        );
        report(progress, "Calculando salidas y registros", phase, totalPhases);
        Set<String> logExcludes = canonicalSet(backupRoots);
        Usage outputUsage = sizeTree(sharedLogs, logExcludes, globalSeen, progress, "Calculando salidas y registros", phase, totalPhases);
        if (outputUsage.logicalBytes > 0 || outputUsage.allocatedBytes > 0) {
            items.add(new Item(
                "outputs", "Salidas y registros", sharedLogs.getAbsolutePath(),
                outputUsage.logicalBytes, outputUsage.allocatedBytes,
                false, false, "Archivos exportados y registros comprimidos de NewTermux."
            ));
        }
        report(progress, "Inventario terminado", ++phase, totalPhases);

        StatFs stat = new StatFs(context.getFilesDir().getAbsolutePath());
        long total = stat.getTotalBytes();
        long free = stat.getAvailableBytes();
        long measured = 0;
        for (Item item : items) measured += Math.max(0, item.allocatedBytes);

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

    private static List<File> discoverTbmStageRoots(File home) {
        File tbm = new File(home, ".tbm");
        File[] children = safeList(tbm);
        if (children == null) return Collections.emptyList();

        List<File> out = new ArrayList<>();
        for (File child : children) {
            String name = child.getName();
            if (name.startsWith("direct-cutover-stage-") && isDirectoryNoFollow(child)) {
                out.add(child);
            }
        }
        out.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        return out;
    }

    private static final class Usage {
        long logicalBytes;
        long allocatedBytes;

        void add(Usage other) {
            if (other == null) return;
            logicalBytes = safeAdd(logicalBytes, other.logicalBytes);
            allocatedBytes = safeAdd(allocatedBytes, other.allocatedBytes);
        }
    }

    private static final class ScanProgressState {
        long visited;
        long logicalBytes;
        long allocatedBytes;
        long lastReportMs;
    }

    private static Usage sizeRoots(List<File> roots, Set<String> excludes, Set<String> seen,
                                   Progress progress, String phase, int completed, int totalPhases) {
        ScanProgressState state = new ScanProgressState();
        Usage total = new Usage();
        for (File root : roots) {
            total.add(sizeTreeInternal(root, excludes, seen, progress, phase, completed, totalPhases, state));
        }
        return total;
    }

    private static Usage sizeTree(File root, Set<String> excludes, Set<String> seen,
                                  Progress progress, String phase, int completed, int totalPhases) {
        return sizeTreeInternal(root, excludes, seen, progress, phase, completed, totalPhases,
            new ScanProgressState());
    }

    private static Usage sizeTreeInternal(File root, Set<String> excludes, Set<String> seen,
                                          Progress progress, String phase, int completed, int totalPhases,
                                          ScanProgressState state) {
        Usage usage = new Usage();
        if (root == null || !root.exists()) return usage;

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

            long allocated = allocatedBytes(st);
            usage.allocatedBytes = safeAdd(usage.allocatedBytes, allocated);
            state.allocatedBytes = safeAdd(state.allocatedBytes, allocated);

            if (OsConstants.S_ISREG(st.st_mode)) {
                long logical = Math.max(0, st.st_size);
                usage.logicalBytes = safeAdd(usage.logicalBytes, logical);
                state.logicalBytes = safeAdd(state.logicalBytes, logical);
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

        return usage;
    }

    private static long allocatedBytes(StructStat st) {
        long blocks = Math.max(0L, st.st_blocks);
        if (blocks > Long.MAX_VALUE / 512L) return Math.max(0L, st.st_size);
        return blocks * 512L;
    }

    private static long safeAdd(long a, long b) {
        if (b <= 0) return a;
        if (a > Long.MAX_VALUE - b) return Long.MAX_VALUE;
        return a + b;
    }

    private static void maybeReportTreeProgress(Progress progress, String phase, int completed,
                                                int totalPhases, ScanProgressState state) {
        if (progress == null) return;
        long now = SystemClock.elapsedRealtime();
        if (state.lastReportMs != 0 && now - state.lastReportMs < 750) return;
        state.lastReportMs = now;
        String detail = String.format(
            Locale.getDefault(),
            "%s · %,d elementos · %s en disco",
            phase,
            state.visited,
            formatBytes(state.allocatedBytes)
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
