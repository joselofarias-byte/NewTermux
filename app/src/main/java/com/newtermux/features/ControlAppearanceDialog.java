package com.newtermux.features;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/** Persistent visual configuration for NewTermux quick controls. */
public final class ControlAppearanceDialog {
    private ControlAppearanceDialog() {}

    public static final String PREFS = "newtermux_controls";

    public static final String KEY_TOOLBAR_CLEAR = "toolbar_clear";
    public static final String KEY_TOOLBAR_COPY = "toolbar_copy";
    public static final String KEY_TOOLBAR_PASTE = "toolbar_paste";
    public static final String KEY_ESC = "key_esc";
    public static final String KEY_Y = "key_y";
    public static final String KEY_TAB = "key_tab";
    public static final String KEY_N = "key_n";
    public static final String KEY_CTRL = "key_ctrl";
    public static final String KEY_ENTER = "key_enter";
    public static final String KEY_ARROWS = "key_arrows";

    public static final int DEFAULT_TOOLBAR_CLEAR = 0xFF54545B;
    public static final int DEFAULT_TOOLBAR_COPY = 0xFF52718A;
    public static final int DEFAULT_TOOLBAR_PASTE = 0xFF7A673E;
    public static final int DEFAULT_ESC = 0xFF7A4C57;
    public static final int DEFAULT_Y = 0xFF466C58;
    public static final int DEFAULT_TAB = 0xFF4D6680;
    public static final int DEFAULT_N = 0xFF704952;
    public static final int DEFAULT_CTRL = 0xFF64557A;
    public static final int DEFAULT_ENTER = 0xFF3E7068;
    public static final int DEFAULT_ARROWS = 0xFF5A5752;

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static int get(Context context, String key, int defaultColor) {
        return prefs(context).getInt(key, defaultColor);
    }

    public static void applyToolbarColors(Context context, View clear, View copy, View paste) {
        if (clear != null) clear.setBackgroundTintList(ColorStateList.valueOf(
            get(context, KEY_TOOLBAR_CLEAR, DEFAULT_TOOLBAR_CLEAR)));
        if (copy != null) copy.setBackgroundTintList(ColorStateList.valueOf(
            get(context, KEY_TOOLBAR_COPY, DEFAULT_TOOLBAR_COPY)));
        if (paste != null) paste.setBackgroundTintList(ColorStateList.valueOf(
            get(context, KEY_TOOLBAR_PASTE, DEFAULT_TOOLBAR_PASTE)));
    }

    public static void show(Context context, Runnable onChanged) {
        String[] names = {
            "Limpiar",
            "Copiar",
            "Pegar+↵",
            "ESC",
            "Y",
            "TAB",
            "N",
            "CTRL",
            "ENTER",
            "Direccionales ↑ ↓ ← →"
        };
        String[] keys = {
            KEY_TOOLBAR_CLEAR,
            KEY_TOOLBAR_COPY,
            KEY_TOOLBAR_PASTE,
            KEY_ESC,
            KEY_Y,
            KEY_TAB,
            KEY_N,
            KEY_CTRL,
            KEY_ENTER,
            KEY_ARROWS
        };
        int[] defaults = {
            DEFAULT_TOOLBAR_CLEAR,
            DEFAULT_TOOLBAR_COPY,
            DEFAULT_TOOLBAR_PASTE,
            DEFAULT_ESC,
            DEFAULT_Y,
            DEFAULT_TAB,
            DEFAULT_N,
            DEFAULT_CTRL,
            DEFAULT_ENTER,
            DEFAULT_ARROWS
        };

        List<CharSequence> rows = new ArrayList<>();
        for (int i = 0; i < names.length; i++) {
            int color = get(context, keys[i], defaults[i]);
            SpannableString row = new SpannableString("●  " + names[i]);
            row.setSpan(new ForegroundColorSpan(color), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            rows.add(row);
        }
        rows.add("Restablecer paleta pastel");

        new AlertDialog.Builder(context)
            .setTitle("Personalizar controles")
            .setItems(rows.toArray(new CharSequence[0]), (dialog, which) -> {
                if (which == names.length) {
                    prefs(context).edit().clear().apply();
                    Toast.makeText(context, "Paleta pastel restablecida", Toast.LENGTH_SHORT).show();
                    if (onChanged != null) onChanged.run();
                    return;
                }

                final int index = which;
                int current = get(context, keys[index], defaults[index]);
                new ColorPickerDialog(context)
                    .setInitialColor(current)
                    .setOnColorSelectedListener(color -> {
                        prefs(context).edit().putInt(keys[index], color).apply();
                        Toast.makeText(context, names[index] + ": color guardado", Toast.LENGTH_SHORT).show();
                        if (onChanged != null) onChanged.run();
                    })
                    .show();
            })
            .setNegativeButton("Cerrar", null)
            .show();
    }
}
