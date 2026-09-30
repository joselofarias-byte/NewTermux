package com.newtermux.features;

import android.content.Context;
import android.os.Environment;
import android.os.StatFs;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructStat;

import com.termux.shared.termux.TermuxConstants;

import java.io.File;
import java.io.IOException;
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

    private NativeStorageManager() {}

    public static Snapshot scan(Context context) {
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

        long homeBytes = sizeTree(home, homeExcludes, globalSeen);
        items.add(new Item(
            "home", "HOME", home.getAbsolutePath(), homeBytes,
            true, true, "Archivos personales y proyectos; modelos, cachés y respaldos se muestran aparte."
        ));

        long prefixBytes = sizeTree(prefix, prefixExcludes, globalSeen);
        items.add(new Item(
            "prefix", "Paquetes / PREFIX", prefix.getAbsolutePath(), prefixBytes,
            true, false, "Paquetes y herramientas de NewTermux. Restauración requiere compatibilidad exacta."
        ));

        for (ProotRoot p : discoverProots(prootBase)) {
            long bytes = sizeTree(p.root, Collections.emptySet(), globalSeen);
            items.add(new Item(
                "proot:" + p.name, "PRoot · " + p.name, p.root.getAbsolutePath(), bytes,
                true, true, "Sistema Linux aislado administrado por proot-distro."
            ));
        }

        long modelBytes = sizeRoots(modelRoots, Collections.emptySet(), globalSeen);
        if (modelBytes > 0 || !modelRoots.isEmpty()) {
            items.add(new Item(
                "models", "Modelos LLM", joinPaths(modelRoots), modelBytes,
                true, true, "Modelos detectados en rutas conocidas de HOME."
            ));
        }

        // Cache roots may contain model roots; exclude those so models are never counted twice.
        long cacheBytes = sizeRoots(cacheRoots, canonicalSet(modelRoots), globalSeen);
        items.add(new Item(
            "cache", "Cachés y temporales", joinPaths(cacheRoots), cacheBytes,
            false, false, "Contenido regenerable. No se incluye en respaldos."
        ));

        long backupBytes = sizeRoots(backupRoots, Collections.emptySet(), globalSeen);
        items.add(new Item(
            "backups", "Respaldos", joinPaths(backupRoots), backupBytes,
            false, false, "Los respaldos existentes nunca se incluyen dentro de otro respaldo."
        ));

        File sharedLogs = new File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "NewTermux"
        );
        Set<String> logExcludes = canonicalSet(backupRoots);
        long outputBytes = sizeTree(sharedLogs, logExcludes, globalSeen);
        if (outputBytes > 0) {
            items.add(new Item(
                "outputs", "Salidas y registros", sharedLogs.getAbsolutePath(), outputBytes,
                false, false, "Archivos exportados y registros comprimidos de NewTermux."
            ));
        }

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
                File root = new File(c, "rootfs");
                if (isDirectoryNoFollow(root) && names.add(c.getName())) {
                    out.add(new ProotRoot(c.getName(), root));
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

    private static long sizeRoots(List<File> roots, Set<String> excludes, Set<String> seen) {
        long total = 0;
        for (File root : roots) total += sizeTree(root, excludes, seen);
        return total;
    }

    private static long sizeTree(File root, Set<String> excludes, Set<String> seen) {
        if (root == null || !root.exists()) return 0;
        String canonical = canonical(root);
        if (canonical == null || isExcluded(canonical, excludes)) return 0;

        StructStat st;
        try {
            st = Os.lstat(root.getAbsolutePath());
        } catch (Exception e) {
            return 0;
        }

        if (OsConstants.S_ISLNK(st.st_mode)) return 0;

        String inode = st.st_dev + ":" + st.st_ino;
        if (!seen.add(inode)) return 0;

        if (OsConstants.S_ISREG(st.st_mode)) return Math.max(0, st.st_size);
        if (!OsConstants.S_ISDIR(st.st_mode)) return 0;

        long sum = 0;
        File[] children = safeList(root);
        if (children == null) return 0;
        for (File child : children) {
            sum += sizeTree(child, excludes, seen);
        }
        return sum;
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

    private static String canonical(File file) {
        try {
            return file.getCanonicalPath();
        } catch (IOException e) {
            return null;
        }
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
