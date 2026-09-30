package com.newtermux.features;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.terminal.TerminalSession;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.WeakHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lightweight, best-effort monitor for long terminal jobs.
 *
 * It never intercepts or changes terminal I/O. It only observes raw PTY output and /proc data so
 * NewTermux can translate noisy package/build logs into a compact human-readable status.
 */
public final class TerminalTaskMonitor {

    public enum Health {
        ACTIVE,
        QUIET,
        SLOW,
        STALLED,
        FINISHED,
        FAILED
    }

    public static final class Snapshot {
        public final boolean tracked;
        public final boolean completed;
        public final String title;
        public final String phase;
        public final String item;
        public final String lastLine;
        public final String processName;
        public final Health health;
        public final int percent;
        public final int completedItems;
        public final int totalItems;
        public final int rootPid;
        public final int processCount;
        public final long startedAtMs;
        public final long finishedAtMs;
        public final long lastOutputAtMs;
        public final long bytes;
        public final long lines;
        public final long rssBytes;
        public final long writeBytes;
        public final boolean processActive;
        public final int exitStatus;

        Snapshot(State state, Health health) {
            this.tracked = state.tracked;
            this.completed = state.completed;
            this.title = safe(state.title, "Tarea en terminal");
            this.phase = safe(state.phase, "Trabajando");
            this.item = safe(state.item, "");
            this.lastLine = safe(state.lastLine, "");
            this.processName = safe(state.processName, "");
            this.health = health;
            this.percent = state.percent;
            this.completedItems = state.completedItems;
            this.totalItems = state.totalItems;
            this.rootPid = state.rootPid;
            this.processCount = state.processCount;
            this.startedAtMs = state.startedAtMs;
            this.finishedAtMs = state.finishedAtMs;
            this.lastOutputAtMs = state.lastOutputAtMs;
            this.bytes = state.bytes;
            this.lines = state.lines;
            this.rssBytes = state.rssBytes;
            this.writeBytes = state.writeBytes;
            this.processActive = state.processActive;
            this.exitStatus = state.exitStatus;
        }

        public boolean shouldDisplay(long nowMs) {
            if (!tracked) return false;
            if (!completed) return true;
            long doneAt = finishedAtMs > 0 ? finishedAtMs : lastOutputAtMs;
            return doneAt > 0 && nowMs - doneAt < 15_000L;
        }

        public long elapsedMs(long nowMs) {
            if (startedAtMs <= 0) return 0;
            long end = completed && finishedAtMs > 0 ? finishedAtMs : nowMs;
            return Math.max(0, end - startedAtMs);
        }

        public long outputAgeMs(long nowMs) {
            return lastOutputAtMs <= 0 ? Long.MAX_VALUE : Math.max(0, nowMs - lastOutputAtMs);
        }
    }

    static final class Analysis {
        final String kind;
        final String title;
        final String phase;
        final String item;
        final int explicitPercent;
        final boolean countItem;
        final boolean completed;
        final boolean failed;

        Analysis(String kind, String title, String phase, String item, int explicitPercent,
                 boolean countItem, boolean completed, boolean failed) {
            this.kind = kind;
            this.title = title;
            this.phase = phase;
            this.item = item;
            this.explicitPercent = explicitPercent;
            this.countItem = countItem;
            this.completed = completed;
            this.failed = failed;
        }
    }

    private static final class State {
        final StringBuilder pending = new StringBuilder();
        final Set<String> seenItems = new HashSet<>();
        String kind;
        String title;
        String phase;
        String item;
        String lastLine;
        String processName;
        boolean tracked;
        boolean completed;
        boolean failed;
        boolean processActive;
        int percent = -1;
        int completedItems;
        int totalItems;
        int exitStatus = Integer.MIN_VALUE;
        int rootPid = -1;
        int processCount;
        long startedAtMs;
        long finishedAtMs;
        long lastOutputAtMs;
        long bytes;
        long lines;
        long rssBytes;
        long writeBytes;
        long cpuTicks;
        long previousCpuTicks = -1;
        long previousWriteBytes = -1;
        String previousProcessName;
    }

    private static final Map<TerminalSession, State> STATES = new WeakHashMap<>();

    private static final Pattern ANSI_CSI =
        Pattern.compile("\\u001B\\[[0-?]*[ -/]*[@-~]");
    private static final Pattern ANSI_OSC =
        Pattern.compile("\\u001B\\][^\\u0007]*(?:\\u0007|\\u001B\\\\)");
    private static final Pattern PERCENT =
        Pattern.compile("(?<!\\d)(100|[0-9]{1,2})%");
    private static final Pattern APT_TOTAL =
        Pattern.compile("(\\d+) upgraded, (\\d+) newly installed(?:, (\\d+) to remove)?");
    private static final Pattern NPM_ADDED =
        Pattern.compile("(?:added|removed|changed) (\\d+) packages?");
    private static final Pattern PROMPT =
        Pattern.compile(".*(?:[$#]|❯|➜)\\s*$");

    private TerminalTaskMonitor() {}

    /**
     * Consume raw PTY output. Returns true when this session is currently recognized as a long task
     * and the Activity should schedule a compact UI refresh.
     */
    public static synchronized boolean accept(@NonNull TerminalSession session, byte[] data, int length) {
        if (length <= 0) return false;
        State state = STATES.get(session);
        if (state == null) {
            state = new State();
            STATES.put(session, state);
        }

        long now = System.currentTimeMillis();
        state.lastOutputAtMs = now;
        state.bytes += length;

        String text = new String(data, 0, length, StandardCharsets.UTF_8);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n' || c == '\r') {
                if (state.pending.length() > 0) {
                    consumeLine(state, state.pending.toString(), now);
                    state.pending.setLength(0);
                }
            } else {
                // Bound partial-line memory for pathological progress streams.
                if (state.pending.length() < 8192) state.pending.append(c);
            }
        }

        // Shell prompts are commonly emitted without a trailing newline.
        if (state.tracked && state.pending.length() > 0) {
            String partial = clean(state.pending.toString()).trim();
            if (looksLikePrompt(partial)) markCompleted(state, false, now);
        }

        return state.tracked;
    }

    public static synchronized boolean isTracked(@Nullable TerminalSession session) {
        State state = session == null ? null : STATES.get(session);
        return state != null && state.tracked;
    }

    /** Mark a shell/session exit. The snapshot is kept briefly so the user can see the outcome. */
    public static synchronized void finish(@NonNull TerminalSession session, int exitStatus) {
        State state = STATES.get(session);
        if (state == null || !state.tracked) return;
        state.exitStatus = exitStatus;
        markCompleted(state, exitStatus != 0, System.currentTimeMillis());
    }

    public static synchronized void forget(@NonNull TerminalSession session) {
        STATES.remove(session);
    }

    /**
     * Return the latest snapshot and best-effort process stats. /proc failures are intentionally
     * ignored: monitoring must never affect the terminal session itself.
     */
    @Nullable
    public static synchronized Snapshot snapshot(@Nullable TerminalSession session, boolean sampleProcesses) {
        if (session == null) return null;
        State state = STATES.get(session);
        if (state == null || !state.tracked) return null;

        if (sampleProcesses && session.isRunning()) sampleProcessTree(session, state);

        long now = System.currentTimeMillis();
        Health health;
        if (state.completed) {
            health = state.failed ? Health.FAILED : Health.FINISHED;
        } else {
            long silence = state.lastOutputAtMs <= 0 ? Long.MAX_VALUE : now - state.lastOutputAtMs;
            if (silence <= 10_000L || state.processActive) health = Health.ACTIVE;
            else if (silence <= 45_000L) health = Health.QUIET;
            else if (silence <= 180_000L) health = Health.SLOW;
            else health = Health.STALLED;
        }
        return new Snapshot(state, health);
    }

    static Analysis analyzeLine(String rawLine) {
        String line = clean(rawLine).trim();
        if (line.isEmpty()) return null;

        Matcher total = APT_TOTAL.matcher(line);
        if (total.find()) {
            return new Analysis("apt", "Instalando paquetes", "Preparando instalación", "",
                parsePercent(line), false, false, false);
        }

        if (line.startsWith("Setting up ")) {
            String item = packageNameAfter(line, "Setting up ");
            return new Analysis("apt", titleForPackage(item), "Configurando paquetes", item,
                parsePercent(line), true, false, false);
        }
        if (line.startsWith("Unpacking ")) {
            String item = packageNameAfter(line, "Unpacking ");
            return new Analysis("apt", titleForPackage(item), "Desempaquetando paquetes", item,
                parsePercent(line), true, false, false);
        }
        if (line.startsWith("Selecting previously unselected package ")) {
            String item = line.substring("Selecting previously unselected package ".length())
                .replaceAll("[. ]+$", "");
            return new Analysis("apt", titleForPackage(item), "Seleccionando paquetes", item,
                parsePercent(line), true, false, false);
        }
        if (line.startsWith("Preparing to unpack ")) {
            return new Analysis("apt", "Instalando paquetes", "Preparando paquetes", "",
                parsePercent(line), false, false, false);
        }
        if (line.startsWith("Processing triggers for ")) {
            String item = packageNameAfter(line, "Processing triggers for ");
            return new Analysis("apt", "Instalando paquetes", "Procesando disparadores", item,
                parsePercent(line), false, false, false);
        }
        if (line.startsWith("Get:") || line.startsWith("Fetched ")) {
            return new Analysis("apt", "Instalando paquetes", "Descargando paquetes", "",
                parsePercent(line), false, false, false);
        }
        if (line.startsWith("Reading package lists") || line.startsWith("Building dependency tree")
            || line.startsWith("Reading state information")) {
            return new Analysis("apt", "Instalando paquetes", "Resolviendo dependencias", "",
                parsePercent(line), false, false, false);
        }

        if (line.startsWith("Collecting ")) {
            String item = firstToken(line.substring("Collecting ".length()));
            return new Analysis("pip", "Instalando Python", "Buscando paquetes", item,
                parsePercent(line), true, false, false);
        }
        if (line.startsWith("Downloading ") && line.contains(".whl")) {
            return new Analysis("pip", "Instalando Python", "Descargando paquetes", "",
                parsePercent(line), false, false, false);
        }
        if (line.startsWith("Installing collected packages:")) {
            return new Analysis("pip", "Instalando Python", "Instalando paquetes", "",
                parsePercent(line), false, false, false);
        }
        if (line.startsWith("Successfully installed ")) {
            return new Analysis("pip", "Instalando Python", "Instalación completada", "",
                100, false, true, false);
        }

        Matcher npm = NPM_ADDED.matcher(line);
        if (npm.find()) {
            return new Analysis("npm", "Instalando Node.js / NPM", "Instalación completada", "",
                100, false, true, false);
        }

        if (line.contains("Receiving objects:") || line.contains("Resolving deltas:")
            || line.contains("Compressing objects:")) {
            String phase = line.contains("Receiving objects:") ? "Recibiendo objetos"
                : line.contains("Resolving deltas:") ? "Resolviendo cambios" : "Comprimiendo objetos";
            return new Analysis("git", "Descargando repositorio", phase, "",
                parsePercent(line), false, false, false);
        }

        if (line.startsWith("> Task :")) {
            return new Analysis("gradle", "Compilando proyecto", "Gradle", line.substring(7).trim(),
                parsePercent(line), true, false, false);
        }
        if (line.contains("BUILD SUCCESSFUL")) {
            return new Analysis("gradle", "Compilando proyecto", "Compilación completada", "",
                100, false, true, false);
        }
        if (line.contains("BUILD FAILED")) {
            return new Analysis("gradle", "Compilando proyecto", "Compilación fallida", "",
                -1, false, true, true);
        }

        if (line.contains("proot-distro") && (line.contains("install") || line.contains("Installing"))) {
            return new Analysis("proot", "Instalando PRoot", "Preparando distribución", "",
                parsePercent(line), false, false, false);
        }

        if (line.startsWith("npm ERR!") || line.startsWith("E: ") || line.startsWith("dpkg: error")) {
            return new Analysis("error", "Tarea en terminal", "Error", "", -1, false, true, true);
        }

        return null;
    }

    private static void consumeLine(State state, String rawLine, long now) {
        String line = clean(rawLine).trim();
        if (line.isEmpty()) return;
        state.lines++;
        state.lastLine = truncate(line, 500);

        Matcher total = APT_TOTAL.matcher(line);
        if (total.find()) {
            int upgraded = parseInt(total.group(1));
            int installed = parseInt(total.group(2));
            state.totalItems = Math.max(state.totalItems, upgraded + installed);
        }

        Analysis a = analyzeLine(line);
        if (a == null) {
            if (state.tracked && looksLikePrompt(line)) markCompleted(state, false, now);
            return;
        }

        if (state.completed && !a.completed) resetForNewTask(state, now);

        if (!state.tracked) {
            state.tracked = true;
            state.startedAtMs = now;
        }

        if (a.kind != null && !a.kind.equals(state.kind)) {
            state.kind = a.kind;
            state.seenItems.clear();
            state.completedItems = 0;
        }

        if (a.phase != null && !a.phase.equals(state.phase) && a.countItem) {
            state.seenItems.clear();
            state.completedItems = 0;
        }

        if (a.title != null && !a.title.isEmpty()) state.title = a.title;
        if (a.phase != null && !a.phase.isEmpty()) state.phase = a.phase;
        if (a.item != null && !a.item.isEmpty()) state.item = a.item;

        if (a.countItem && a.item != null && !a.item.isEmpty() && state.seenItems.add(a.item)) {
            state.completedItems++;
        }

        if (a.explicitPercent >= 0) {
            state.percent = Math.min(100, a.explicitPercent);
        } else if (state.totalItems > 0 && state.completedItems > 0) {
            state.percent = Math.min(99, (int) Math.round(
                state.completedItems * 100.0 / state.totalItems));
        }

        if (a.completed) markCompleted(state, a.failed, now);
    }

    private static void resetForNewTask(State state, long now) {
        state.completed = false;
        state.failed = false;
        state.finishedAtMs = 0;
        state.exitStatus = Integer.MIN_VALUE;
        state.startedAtMs = now;
        state.percent = -1;
        state.completedItems = 0;
        state.totalItems = 0;
        state.bytes = 0;
        state.lines = 0;
        state.seenItems.clear();
    }

    private static void markCompleted(State state, boolean failed, long now) {
        if (!state.tracked) return;
        state.completed = true;
        state.failed = failed;
        state.finishedAtMs = now;
        if (!failed) state.percent = 100;
        state.phase = failed ? "Finalizó con error" : "Completado";
    }

    private static boolean looksLikePrompt(String text) {
        if (text == null || text.length() < 2 || text.length() > 220) return false;
        return PROMPT.matcher(text.trim()).matches();
    }

    private static String clean(String value) {
        String result = ANSI_OSC.matcher(value).replaceAll("");
        result = ANSI_CSI.matcher(result).replaceAll("");
        return result.replace("\u0000", "");
    }

    private static int parsePercent(String line) {
        Matcher m = PERCENT.matcher(line);
        return m.find() ? parseInt(m.group(1)) : -1;
    }

    private static int parseInt(String value) {
        try { return Integer.parseInt(value); } catch (Exception e) { return 0; }
    }

    private static String packageNameAfter(String line, String prefix) {
        String rest = line.substring(Math.min(prefix.length(), line.length())).trim();
        int paren = rest.indexOf(" (");
        if (paren > 0) rest = rest.substring(0, paren);
        return rest.replaceAll("[.: ]+$", "");
    }

    private static String firstToken(String value) {
        int space = value.indexOf(' ');
        return space > 0 ? value.substring(0, space) : value;
    }

    private static String titleForPackage(String item) {
        String p = item == null ? "" : item.toLowerCase(Locale.ROOT);
        if (p.startsWith("node-") || p.equals("nodejs") || p.equals("npm"))
            return "Instalando Node.js / NPM";
        if (p.startsWith("python") || p.startsWith("libpython"))
            return "Instalando Python";
        if (p.contains("clang") || p.contains("cmake") || p.equals("make"))
            return "Instalando herramientas de compilación";
        return "Instalando paquetes";
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }

    private static String safe(String value, String fallback) {
        return value == null || value.isEmpty() ? fallback : value;
    }

    private static void sampleProcessTree(TerminalSession session, State state) {
        int rootPid = session.getPid();
        if (rootPid < 1) return;

        ProcSample sample = readProcessTree(rootPid);
        state.rootPid = rootPid;
        state.processCount = sample.processCount;
        state.rssBytes = sample.rssBytes;
        state.writeBytes = sample.writeBytes;
        state.cpuTicks = sample.cpuTicks;
        state.processName = sample.processName;

        boolean cpuMoved = state.previousCpuTicks >= 0 && sample.cpuTicks > state.previousCpuTicks;
        boolean ioMoved = state.previousWriteBytes >= 0 && sample.writeBytes > state.previousWriteBytes;
        boolean processChanged = state.previousProcessName != null
            && sample.processName != null
            && !sample.processName.equals(state.previousProcessName);
        state.processActive = cpuMoved || ioMoved || processChanged;

        state.previousCpuTicks = sample.cpuTicks;
        state.previousWriteBytes = sample.writeBytes;
        state.previousProcessName = sample.processName;
    }

    private static final class ProcSample {
        long cpuTicks;
        long rssBytes;
        long writeBytes;
        int processCount;
        String processName;
    }

    private static ProcSample readProcessTree(int rootPid) {
        ProcSample total = new ProcSample();
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        Set<Integer> seen = new HashSet<>();
        queue.add(rootPid);

        while (!queue.isEmpty() && seen.size() < 128) {
            int pid = queue.removeFirst();
            if (!seen.add(pid)) continue;
            total.processCount++;

            String stat = readSmallFile("/proc/" + pid + "/stat", 8192);
            if (stat != null) {
                int close = stat.lastIndexOf(')');
                if (close > 0 && close + 2 < stat.length()) {
                    String[] fields = stat.substring(close + 2).trim().split("\\s+");
                    // fields[0] is Linux stat field 3 (state), so utime/stime are indexes 11/12.
                    if (fields.length > 12) {
                        total.cpuTicks += parseLong(fields[11]);
                        total.cpuTicks += parseLong(fields[12]);
                    }
                }
            }

            String status = readSmallFile("/proc/" + pid + "/status", 32768);
            if (status != null) {
                for (String line : status.split("\n")) {
                    if (line.startsWith("VmRSS:")) {
                        String digits = line.replaceAll("[^0-9]", "");
                        total.rssBytes += parseLong(digits) * 1024L;
                        break;
                    }
                }
            }

            String io = readSmallFile("/proc/" + pid + "/io", 16384);
            if (io != null) {
                for (String line : io.split("\n")) {
                    if (line.startsWith("write_bytes:")) {
                        total.writeBytes += parseLong(line.substring("write_bytes:".length()).trim());
                        break;
                    }
                }
            }

            if (pid != rootPid) {
                String cmd = readSmallFile("/proc/" + pid + "/cmdline", 4096);
                if (cmd != null) {
                    cmd = cmd.replace('\u0000', ' ').trim();
                    if (!cmd.isEmpty()) total.processName = truncate(cmd, 120);
                }
            }

            String children = readSmallFile("/proc/" + pid + "/task/" + pid + "/children", 16384);
            if (children != null && !children.trim().isEmpty()) {
                for (String child : children.trim().split("\\s+")) {
                    int childPid = parseInt(child);
                    if (childPid > 0 && !seen.contains(childPid)) queue.addLast(childPid);
                }
            }
        }

        if (total.processName == null) {
            String cmd = readSmallFile("/proc/" + rootPid + "/cmdline", 4096);
            if (cmd != null) total.processName = truncate(cmd.replace('\u0000', ' ').trim(), 120);
        }
        return total;
    }

    private static String readSmallFile(String path, int maxChars) {
        File file = new File(path);
        if (!file.isFile() || !file.canRead()) return null;
        StringBuilder out = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            char[] buffer = new char[1024];
            int read;
            while ((read = reader.read(buffer)) > 0 && out.length() < maxChars) {
                out.append(buffer, 0, Math.min(read, maxChars - out.length()));
            }
            return out.toString();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static long parseLong(String value) {
        try { return Long.parseLong(value); } catch (Exception e) { return 0L; }
    }

    public static String formatDuration(long ms) {
        long seconds = Math.max(0, ms / 1000L);
        long hours = seconds / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        long secs = seconds % 60L;
        if (hours > 0) return String.format(Locale.ROOT, "%dh %02dm %02ds", hours, minutes, secs);
        if (minutes > 0) return String.format(Locale.ROOT, "%dm %02ds", minutes, secs);
        return String.format(Locale.ROOT, "%ds", secs);
    }

    public static String formatBytes(long bytes) {
        if (bytes < 1024L) return bytes + " B";
        double kib = bytes / 1024.0;
        if (kib < 1024.0) return String.format(Locale.ROOT, "%.1f KiB", kib);
        double mib = kib / 1024.0;
        if (mib < 1024.0) return String.format(Locale.ROOT, "%.1f MiB", mib);
        return String.format(Locale.ROOT, "%.1f GiB", mib / 1024.0);
    }
}
