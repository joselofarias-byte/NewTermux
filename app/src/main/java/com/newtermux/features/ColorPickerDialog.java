package com.newtermux.features;

import android.content.Context;
import android.graphics.Color;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.termux.R;

/**
 * Builder-style reusable color picker dialog.
 * Supports HSV color wheel and RGB sliders.
 * Remembers last picker style in SharedPreferences ("newtermux_theme" / "color_picker_style").
 */
public class ColorPickerDialog {

    public interface OnColorSelectedListener {
        void onColorSelected(int color);
    }

    private static final String PREF_STYLE_KEY = "color_picker_style";
    private static final String STYLE_HSV = "hsv";
    private static final String STYLE_RGB = "rgb";

    private final Context mContext;
    private int mInitialColor = Color.WHITE;
    private OnColorSelectedListener mListener;

    public ColorPickerDialog(Context context) {
        mContext = context;
    }

    public ColorPickerDialog setInitialColor(int color) {
        mInitialColor = color;
        return this;
    }

    public ColorPickerDialog setOnColorSelectedListener(OnColorSelectedListener listener) {
        mListener = listener;
        return this;
    }

    public void show() {
        String lastStyle = mContext.getSharedPreferences("newtermux_theme", Context.MODE_PRIVATE)
            .getString(PREF_STYLE_KEY, STYLE_HSV);
        String[] items = {mContext.getString(R.string.nt_l10n_color_wheel), mContext.getString(R.string.nt_l10n_rgb_sliders)};
        int defaultItem = STYLE_HSV.equals(lastStyle) ? 0 : 1;

        AlertDialog chooser = new AlertDialog.Builder(mContext)
            .setTitle(mContext.getString(R.string.nt_l10n_picker_style))
            .setSingleChoiceItems(items, defaultItem, null)
            .setPositiveButton(mContext.getString(R.string.nt_l10n_next), null)
            .setNegativeButton(mContext.getString(R.string.nt_l10n_cancel), null)
            .create();

        chooser.setOnShowListener(d -> {
            chooser.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                int selected = chooser.getListView().getCheckedItemPosition();
                if (selected < 0) selected = defaultItem;
                String style = selected == 0 ? STYLE_HSV : STYLE_RGB;
                mContext.getSharedPreferences("newtermux_theme", Context.MODE_PRIVATE)
                    .edit().putString(PREF_STYLE_KEY, style).apply();
                chooser.dismiss();
                if (STYLE_HSV.equals(style)) {
                    showHsvPicker();
                } else {
                    showRgbPicker();
                }
            });
        });
        chooser.show();
    }

    private void showHsvPicker() {
        View view = LayoutInflater.from(mContext).inflate(R.layout.dialog_color_picker_hsv, null);
        HsvColorWheelView wheel = view.findViewById(R.id.color_wheel);
        SeekBar brightnessBar = view.findViewById(R.id.seek_brightness);
        View preview = view.findViewById(R.id.color_preview);
        EditText hexInput = view.findViewById(R.id.edit_hex);

        final boolean[] syncing = {false};

        wheel.setColor(mInitialColor);
        brightnessBar.setMax(255);
        brightnessBar.setProgress((int) (wheel.getBrightness() * 255));
        preview.setBackgroundColor(mInitialColor);
        hexInput.setText(colorToHex(mInitialColor));

        wheel.setOnColorChangedListener(color -> {
            if (syncing[0]) return;
            syncing[0] = true;
            preview.setBackgroundColor(color);
            hexInput.setText(colorToHex(color));
            syncing[0] = false;
        });

        brightnessBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (!fromUser || syncing[0]) return;
                syncing[0] = true;
                wheel.setBrightness(progress / 255f);
                int color = wheel.getColor();
                preview.setBackgroundColor(color);
                hexInput.setText(colorToHex(color));
                syncing[0] = false;
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });

        hexInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                if (syncing[0]) return;
                String hex = s.toString();
                if (!hex.startsWith("#")) hex = "#" + hex;
                if (hex.length() != 7) return;
                try {
                    int color = Color.parseColor(hex);
                    syncing[0] = true;
                    wheel.setColor(color);
                    brightnessBar.setProgress((int) (wheel.getBrightness() * 255));
                    preview.setBackgroundColor(color);
                    syncing[0] = false;
                } catch (IllegalArgumentException ignored) {}
            }
        });

        new AlertDialog.Builder(mContext)
            .setTitle(mContext.getString(R.string.nt_l10n_custom_hsv))
            .setView(view)
            .setPositiveButton(mContext.getString(R.string.nt_l10n_ok), (d, w) -> {
                if (mListener != null) mListener.onColorSelected(wheel.getColor());
            })
            .setNegativeButton(mContext.getString(R.string.nt_l10n_cancel), null)
            .show();
    }

    private void showRgbPicker() {
        View view = LayoutInflater.from(mContext).inflate(R.layout.dialog_color_picker_rgb, null);
        View preview = view.findViewById(R.id.color_preview);
        SeekBar seekR = view.findViewById(R.id.seek_r);
        SeekBar seekG = view.findViewById(R.id.seek_g);
        SeekBar seekB = view.findViewById(R.id.seek_b);
        TextView valR = view.findViewById(R.id.val_r);
        TextView valG = view.findViewById(R.id.val_g);
        TextView valB = view.findViewById(R.id.val_b);
        EditText hexInput = view.findViewById(R.id.edit_hex);

        final boolean[] syncing = {false};
        final int[] rgb = {
            Color.red(mInitialColor),
            Color.green(mInitialColor),
            Color.blue(mInitialColor)
        };

        seekR.setMax(255); seekR.setProgress(rgb[0]);
        seekG.setMax(255); seekG.setProgress(rgb[1]);
        seekB.setMax(255); seekB.setProgress(rgb[2]);
        valR.setText(String.valueOf(rgb[0]));
        valG.setText(String.valueOf(rgb[1]));
        valB.setText(String.valueOf(rgb[2]));
        preview.setBackgroundColor(mInitialColor);
        hexInput.setText(colorToHex(mInitialColor));

        SeekBar.OnSeekBarChangeListener sbListener = new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser || syncing[0]) return;
                int id = seekBar.getId();
                if (id == R.id.seek_r) { rgb[0] = progress; valR.setText(String.valueOf(progress)); }
                else if (id == R.id.seek_g) { rgb[1] = progress; valG.setText(String.valueOf(progress)); }
                else if (id == R.id.seek_b) { rgb[2] = progress; valB.setText(String.valueOf(progress)); }
                int color = Color.rgb(rgb[0], rgb[1], rgb[2]);
                syncing[0] = true;
                preview.setBackgroundColor(color);
                hexInput.setText(colorToHex(color));
                syncing[0] = false;
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        };

        seekR.setOnSeekBarChangeListener(sbListener);
        seekG.setOnSeekBarChangeListener(sbListener);
        seekB.setOnSeekBarChangeListener(sbListener);

        hexInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                if (syncing[0]) return;
                String hex = s.toString();
                if (!hex.startsWith("#")) hex = "#" + hex;
                if (hex.length() != 7) return;
                try {
                    int color = Color.parseColor(hex);
                    syncing[0] = true;
                    rgb[0] = Color.red(color);
                    rgb[1] = Color.green(color);
                    rgb[2] = Color.blue(color);
                    seekR.setProgress(rgb[0]); valR.setText(String.valueOf(rgb[0]));
                    seekG.setProgress(rgb[1]); valG.setText(String.valueOf(rgb[1]));
                    seekB.setProgress(rgb[2]); valB.setText(String.valueOf(rgb[2]));
                    preview.setBackgroundColor(color);
                    syncing[0] = false;
                } catch (IllegalArgumentException ignored) {}
            }
        });

        new AlertDialog.Builder(mContext)
            .setTitle(mContext.getString(R.string.nt_l10n_custom_rgb))
            .setView(view)
            .setPositiveButton(mContext.getString(R.string.nt_l10n_ok), (d, w) -> {
                if (mListener != null) mListener.onColorSelected(Color.rgb(rgb[0], rgb[1], rgb[2]));
            })
            .setNegativeButton(mContext.getString(R.string.nt_l10n_cancel), null)
            .show();
    }

    private static String colorToHex(int color) {
        return String.format("#%06X", 0xFFFFFF & color);
    }
}
