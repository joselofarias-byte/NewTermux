package com.newtermux.features;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import androidx.annotation.NonNull;

import com.termux.terminal.TerminalSession;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.GZIPOutputStream;

/** Opt-in, bounded raw PTY capture. Long outputs are saved automatically as streaming gzip logs. */
public final class AutoOutputCapture {
    private static final int MAX_BYTES = 32 * 1024 * 1024;
    // Internal definition of "long": users only choose whether automatic saving is on or off.
    private static final int AUTO_SAVE_LINE_THRESHOLD = 500;
    private static final Map<TerminalSession, Capture> CAPTURES = new IdentityHashMap<>();
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final class Capture {
        final ByteArrayOutputStream pending = new ByteArrayOutputStream();
        int lines;
        int bytes;
        Uri uri;
        File file;
        boolean opened;
        boolean stopped;
    }

    private AutoOutputCapture() {}

    public static void accept(@NonNull Context context, @NonNull TerminalSession session, byte[] data, int length) {
        if (!NewTermuxSettings.isAutoSaveOutputEnabled(context) || length <= 0) return;
        byte[] chunk = Arrays.copyOf(data, length);
        Context app = context.getApplicationContext();
        IO.execute(() -> write(app, session, chunk, AUTO_SAVE_LINE_THRESHOLD));
    }

    public static void finish(@NonNull TerminalSession session) {
        IO.execute(() -> {
            Capture capture = CAPTURES.remove(session);
            if (capture != null) capture.pending.reset();
        });
    }

    private static void write(Context context, TerminalSession session, byte[] chunk, int threshold) {
        Capture c = CAPTURES.get(session);
        if (c == null) {
            c = new Capture();
            CAPTURES.put(session, c);
        }
        if (c.stopped) return;
        for (byte b : chunk) if (b == '\n') c.lines++;
        if (c.bytes + chunk.length > MAX_BYTES) {
            // Keep the complete prefix and mark the size limit in an already exported file.
            if (c.opened) {
                try (OutputStream output = append(context, c)) {
                    output.write("\n[NewTermux: límite de 32 MiB alcanzado; captura detenida]\n"
                        .getBytes(StandardCharsets.UTF_8));
                } catch (Exception ignored) {}
            }
            c.stopped = true;
            c.pending.reset();
            return;
        }
        c.bytes += chunk.length;
        try {
            if (!c.opened) {
                c.pending.write(chunk);
                if (c.lines < threshold) return;
                try (OutputStream output = open(context, c)) {
                    c.pending.writeTo(output);
                }
                c.opened = true;
                c.pending.reset();
            } else {
                // Each chunk is a complete gzip member; the file stays readable while the shell lives.
                try (OutputStream output = append(context, c)) { output.write(chunk); }
            }
        } catch (Exception e) {
            c.stopped = true;
            c.pending.reset();
            if (c.uri != null) {
                try { context.getContentResolver().delete(c.uri, null, null); } catch (Exception ignored) {}
            } else if (c.file != null) c.file.delete();
        }
    }

    private static OutputStream open(Context context, Capture c) throws Exception {
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.ROOT).format(new Date());
        String name = "NewTermux-salida-" + stamp + ".log.gz";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentResolver resolver = context.getContentResolver();
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
            values.put(MediaStore.MediaColumns.MIME_TYPE, "application/gzip");
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/NewTermux");
            c.uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (c.uri == null) throw new IllegalStateException("No se pudo crear el registro");
            OutputStream stream = resolver.openOutputStream(c.uri, "w");
            if (stream == null) throw new IllegalStateException("No se pudo abrir el registro");
            return new GZIPOutputStream(stream, true);
        }
        File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "NewTermux");
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IllegalStateException("No se pudo crear Descargas/NewTermux");
        c.file = new File(dir, name);
        return new GZIPOutputStream(new FileOutputStream(c.file), true);
    }

    private static OutputStream append(Context context, Capture c) throws Exception {
        OutputStream stream = c.uri != null
            ? context.getContentResolver().openOutputStream(c.uri, "wa")
            : new FileOutputStream(c.file, true);
        if (stream == null) throw new IllegalStateException("No se pudo continuar el registro");
        return new GZIPOutputStream(stream, true);
    }
}
