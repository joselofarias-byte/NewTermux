package com.newtermux.features;

import android.content.ClipData;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Saves terminal text directly to the public Downloads collection without opening SAF.
 * MediaStore is used on modern Android so the resulting TXT can immediately be opened
 * or shared with other apps.
 */
public final class TerminalTextExport {

    private TerminalTextExport() {}

    public interface Callback {
        void onSuccess(@Nullable Uri uri, @NonNull String displayName);
        void onError(@NonNull Exception error);
    }

    public static String buildFileName(@NonNull String prefix) {
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(new Date());
        return prefix + "-" + stamp + ".txt";
    }

    public static void saveToDownloads(@NonNull Context context,
                                       @NonNull String text,
                                       @NonNull String displayName,
                                       @NonNull Callback callback) {
        new Thread(() -> {
            Uri uri = null;
            try {
                ContentResolver resolver = context.getContentResolver();
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, displayName);
                values.put(MediaStore.MediaColumns.MIME_TYPE, "text/plain");

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                    values.put(MediaStore.MediaColumns.IS_PENDING, 1);
                    uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                } else {
                    File downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                    if (!downloads.exists() && !downloads.mkdirs()) {
                        throw new IllegalStateException("Could not create Downloads directory");
                    }
                    values.put(MediaStore.MediaColumns.DATA, new File(downloads, displayName).getAbsolutePath());
                    uri = resolver.insert(MediaStore.Files.getContentUri("external"), values);
                }

                if (uri == null) throw new IllegalStateException("MediaStore insert returned null");

                try (OutputStream os = resolver.openOutputStream(uri, "w")) {
                    if (os == null) throw new IllegalStateException("Could not open output stream");
                    os.write(text.getBytes(StandardCharsets.UTF_8));
                    os.flush();
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ContentValues ready = new ContentValues();
                    ready.put(MediaStore.MediaColumns.IS_PENDING, 0);
                    resolver.update(uri, ready, null, null);
                }

                Uri resultUri = uri;
                android.os.Handler main = new android.os.Handler(context.getMainLooper());
                main.post(() -> callback.onSuccess(resultUri, displayName));
            } catch (Exception e) {
                if (uri != null) {
                    try { context.getContentResolver().delete(uri, null, null); } catch (Exception ignored) {}
                }
                android.os.Handler main = new android.os.Handler(context.getMainLooper());
                main.post(() -> callback.onError(e));
            }
        }, "newtermux-txt-export").start();
    }

    public static void openText(@NonNull Context context, @NonNull Uri uri) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "text/plain");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    public static void shareTextFile(@NonNull Context context,
                                     @NonNull Uri uri,
                                     @NonNull String displayName,
                                     @NonNull String chooserTitle) {
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_SUBJECT, displayName);
        send.putExtra(Intent.EXTRA_STREAM, uri);
        send.setClipData(ClipData.newRawUri(displayName, uri));
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        Intent chooser = Intent.createChooser(send, chooserTitle);
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(chooser);
    }
}
