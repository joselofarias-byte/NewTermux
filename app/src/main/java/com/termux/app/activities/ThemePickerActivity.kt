package com.termux.app.activities

import com.termux.R

import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.newtermux.compose.MenuItemDivider
import com.newtermux.compose.NewTermuxComposeTheme
import com.newtermux.compose.outlinedMenuCard
import com.newtermux.features.ColorPickerDialog
import com.newtermux.features.NewTermuxColorTheme
import com.newtermux.features.NewTermuxTheme

/**
 * Terminal theme + accent color picker. Compose migration (Phase 4).
 *
 * The former custom Views (ThemePreviewView / AccentSwatchView) are re-drawn
 * here with Compose Canvas. The existing [ColorPickerDialog] (also used by
 * Settings) is reused as-is for picking custom colors.
 */
class ThemePickerActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NewTermuxComposeTheme(this) {
                ThemePickerScreen(onBack = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemePickerScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    var activeTheme by remember { mutableStateOf(NewTermuxColorTheme.getCurrentTheme(context)) }
    var activeAccent by remember { mutableIntStateOf(NewTermuxTheme.getAccentColor(context)) }
    var overflowOpen by remember { mutableStateOf(false) }

    // Custom-theme editor state
    var showScope by remember { mutableStateOf(false) }
    var editorKeys by remember { mutableStateOf<List<Pair<String, Int>>?>(null) } // key -> label

    val terminalKeys = remember {
        NewTermuxColorTheme.THEME_KEYS.filter { it != NewTermuxColorTheme.THEME_KEY_CUSTOM }
    }
    val accentColors = remember { NewTermuxTheme.COLORS.toList() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(context.getString(R.string.nt_l10n_themes_colors)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = context.getString(R.string.nt_l10n_back))
                    }
                },
                actions = {
                    IconButton(onClick = { overflowOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = context.getString(R.string.nt_l10n_more))
                    }
                    DropdownMenu(expanded = overflowOpen, onDismissRequest = { overflowOpen = false }, modifier = Modifier.outlinedMenuCard()) {
                        DropdownMenuItem(text = { Text(context.getString(R.string.nt_l10n_custom_theme)) }, onClick = { overflowOpen = false; showScope = true })
                    }
                },
            )
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) { SectionHeader(context.getString(R.string.nt_l10n_terminal_theme)) }

            items(terminalKeys, key = { it }) { key ->
                TerminalThemeCard(
                    key = key,
                    active = key == activeTheme,
                    accent = activeAccent,
                    onClick = {
                        NewTermuxColorTheme.applyTheme(context, key)
                        activeTheme = key
                    },
                )
            }

            item(span = { GridItemSpan(maxLineSpan) }) { SectionHeader(context.getString(R.string.nt_l10n_accent_color)) }

            items(accentColors, key = { it }) { color ->
                AccentSwatch(
                    color = color,
                    active = color == activeAccent,
                    isCustomSlot = false,
                    label = accentColorName(context, color),
                    onClick = {
                        NewTermuxTheme.setAccentColor(context, color)
                        activeAccent = color
                    },
                )
            }

            item {
                val customActive = NewTermuxTheme.isCustomAccentActive(context)
                AccentSwatch(
                    color = if (customActive) activeAccent else 0xFF666666.toInt(),
                    active = customActive,
                    isCustomSlot = true,
                    label = context.getString(R.string.nt_l10n_custom_color_slot),
                    onClick = {
                        ColorPickerDialog(context)
                            .setInitialColor(NewTermuxTheme.getAccentColor(context))
                            .setOnColorSelectedListener { picked ->
                                NewTermuxTheme.setAccentColor(context, picked)
                                activeAccent = picked
                            }
                            .show()
                    },
                )
            }
        }
    }

    // Scope chooser for the custom terminal-theme editor
    if (showScope) {
        AlertDialog(
            onDismissRequest = { showScope = false },
            title = { Text(context.getString(R.string.nt_l10n_custom_scope)) },
            text = {
                Column {
                    DropdownMenuItem(
                        text = { Text(context.getString(R.string.nt_l10n_core_colors)) },
                        onClick = { showScope = false; editorKeys = CORE_KEYS },
                    )
                    MenuItemDivider()
                    DropdownMenuItem(
                        text = { Text(context.getString(R.string.nt_l10n_all_colors)) },
                        onClick = { showScope = false; editorKeys = ALL_KEYS },
                    )
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showScope = false }) { Text(context.getString(R.string.nt_l10n_cancel)) } },
        )
    }

    editorKeys?.let { keys ->
        CustomThemeEditorDialog(
            keys = keys,
            onDismiss = { editorKeys = null },
            onApply = { colorMap ->
                val base = NewTermuxColorTheme.getCustomThemeContent(context)
                NewTermuxColorTheme.applyCustomTheme(context, buildThemeContent(base, colorMap))
                editorKeys = null
            },
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun TerminalThemeCard(key: String, active: Boolean, accent: Int, onClick: () -> Unit) {
    val context = LocalContext.current
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            TerminalPreview(
                colors = NewTermuxColorTheme.getPreviewColors(key),
                active = active,
                accent = accent,
                modifier = Modifier.fillMaxWidth().aspectRatio(1.3f).padding(4.dp),
            )
            Text(
                terminalThemeName(context, key),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = 6.dp, start = 4.dp, end = 4.dp),
            )
        }
    }
}

/** Compose re-draw of the former ThemePreviewView. colors = {bg, fg, toolbar, green, cursor}. */
@Composable
private fun TerminalPreview(colors: IntArray, active: Boolean, accent: Int, modifier: Modifier = Modifier) {
    val textPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.MONOSPACE } }
    Canvas(modifier = modifier.clip(RoundedCornerShape(4.dp))) {
        val d = 1.dp.toPx()
        val w = size.width
        val h = size.height
        val bg = Color(colors[0]); val fg = colors[1]; val toolbar = Color(colors[2])
        val green = colors[3]; val cursor = Color(colors[4])

        drawRect(bg)
        val toolH = 11 * d
        drawRect(toolbar, size = androidx.compose.ui.geometry.Size(w, toolH))
        // window dots
        drawCircle(Color(0xFFFF5F57), radius = 1.8f * d, center = Offset(5.5f * d, toolH / 2f))
        drawCircle(Color(0xFFFFBD2E), radius = 1.8f * d, center = Offset(10.5f * d, toolH / 2f))
        drawCircle(Color(0xFF28CA41), radius = 1.8f * d, center = Offset(15.5f * d, toolH / 2f))

        val ts = 6f * d
        textPaint.textSize = ts
        val x = 4 * d
        var y = toolH + 8 * d
        val lh = 8 * d
        drawIntoCanvas { c ->
            val nc = c.nativeCanvas
            val prompt = "$ "
            textPaint.color = green
            nc.drawText(prompt, x, y, textPaint)
            val pw = textPaint.measureText(prompt)
            textPaint.color = fg
            nc.drawText("ls -la", x + pw, y, textPaint)

            y += lh
            textPaint.color = dim(fg)
            nc.drawText("total 8", x, y, textPaint)

            y += lh
            textPaint.color = green
            val perm = "drwx "
            nc.drawText(perm, x, y, textPaint)
            val permW = textPaint.measureText(perm)
            textPaint.color = fg
            nc.drawText("home", x + permW, y, textPaint)

            if (y + lh + 2 * d < h) {
                y += lh
                textPaint.color = green
                nc.drawText(prompt, x, y, textPaint)
                val cx2 = x + textPaint.measureText(prompt)
                drawRect(cursor, topLeft = Offset(cx2, y - ts), size = androidx.compose.ui.geometry.Size(5 * d, ts + 1.5f * d))
            }
        }

        if (active) {
            val sw = 2.5f * d
            drawRect(
                color = Color(accent),
                topLeft = Offset(sw / 2f, sw / 2f),
                size = androidx.compose.ui.geometry.Size(w - sw, h - sw),
                style = Stroke(width = sw),
            )
        }
    }
}

@Composable
private fun AccentSwatch(color: Int, active: Boolean, isCustomSlot: Boolean, label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
    ) {
        Canvas(modifier = Modifier.size(52.dp)) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val d = 1.dp.toPx()
            val radius = minOf(cx, cy) - 3 * d
            if (isCustomSlot) {
                drawCircle(
                    brush = Brush.sweepGradient(
                        listOf(
                            Color(0xFFFF0000), Color(0xFFFF8C00), Color(0xFFFFFF00),
                            Color(0xFF00CC00), Color(0xFF0088FF), Color(0xFF8800FF), Color(0xFFFF0000),
                        ),
                        center = Offset(cx, cy),
                    ),
                    radius = radius,
                    center = Offset(cx, cy),
                )
            } else {
                drawCircle(Color(color), radius = radius, center = Offset(cx, cy))
            }
            if (active) {
                val sw = 2.5f * d
                drawCircle(Color.White, radius = radius - sw / 2f, center = Offset(cx, cy), style = Stroke(width = sw))
                drawIntoCanvas { c ->
                    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        this.color = android.graphics.Color.WHITE
                        textAlign = Paint.Align.CENTER
                        isFakeBoldText = true
                        textSize = 14 * d
                    }
                    c.nativeCanvas.drawText("✓", cx, cy + 5 * d, p)
                }
            }
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun CustomThemeEditorDialog(
    keys: List<Pair<String, Int>>,
    onDismiss: () -> Unit,
    onApply: (Map<String, Int>) -> Unit,
) {
    val context = LocalContext.current
    val base = remember { NewTermuxColorTheme.getCustomThemeContent(context) }
    val colorMap = remember {
        mutableStateMapOf<String, Int>().apply { putAll(parseThemeContent(base, keys.map { it.first })) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(context.getString(R.string.nt_l10n_custom_theme)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                keys.forEach { (key, label) ->
                    val cur = colorMap[key] ?: 0xFF808080.toInt()
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(context.getString(label), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.size(8.dp))
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .clickable {
                                    ColorPickerDialog(context)
                                        .setInitialColor(colorMap[key] ?: 0xFF808080.toInt())
                                        .setOnColorSelectedListener { picked -> colorMap[key] = picked }
                                        .show()
                                },
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) { drawRect(Color(cur)) }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onApply(colorMap.toMap()) }) { Text(context.getString(R.string.nt_l10n_apply)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(context.getString(R.string.nt_l10n_cancel)) } },
    )
}

// --- helpers ported from the former Java Activity ---

private fun dim(color: Int): Int {
    val r = (((color shr 16) and 0xFF) * 0.55f).toInt()
    val g = (((color shr 8) and 0xFF) * 0.55f).toInt()
    val b = ((color and 0xFF) * 0.55f).toInt()
    return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
}

private fun parseThemeContent(content: String?, keys: List<String>): Map<String, Int> {
    val map = LinkedHashMap<String, Int>()
    keys.forEach { map[it] = 0xFF808080.toInt() }
    if (content == null) return map
    for (raw in content.split("\n")) {
        val line = raw.trim()
        if (line.isEmpty() || line.startsWith("#")) continue
        val eq = line.indexOf('=')
        if (eq < 0) continue
        val k = line.substring(0, eq).trim()
        val v = line.substring(eq + 1).trim()
        if (map.containsKey(k)) {
            try {
                map[k] = android.graphics.Color.parseColor(if (v.startsWith("#")) v else "#$v")
            } catch (ignored: IllegalArgumentException) {
            }
        }
    }
    return map
}

private fun buildThemeContent(baseContent: String?, colorMap: Map<String, Int>): String {
    val allLines = LinkedHashMap<String, String>()
    if (baseContent != null) {
        for (raw in baseContent.split("\n")) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            val eq = line.indexOf('=')
            if (eq < 0) continue
            allLines[line.substring(0, eq).trim()] = line.substring(eq + 1).trim()
        }
    }
    for ((k, v) in colorMap) {
        allLines[k] = String.format("#%06X", 0xFFFFFF and v)
    }
    return buildString {
        for ((k, v) in allLines) append(k).append('=').append(v).append('\n')
    }
}

private val CORE_KEYS = listOf(
    "background" to R.string.nt_l10n_color_background,
    "foreground" to R.string.nt_l10n_color_foreground,
    "cursor" to R.string.nt_l10n_color_cursor,
)

private val ALL_KEYS = listOf(
    "background" to R.string.nt_l10n_color_background,
    "foreground" to R.string.nt_l10n_color_foreground,
    "cursor" to R.string.nt_l10n_color_cursor,
    "color0" to R.string.nt_l10n_color_0,
    "color1" to R.string.nt_l10n_color_1,
    "color2" to R.string.nt_l10n_color_2,
    "color3" to R.string.nt_l10n_color_3,
    "color4" to R.string.nt_l10n_color_4,
    "color5" to R.string.nt_l10n_color_5,
    "color6" to R.string.nt_l10n_color_6,
    "color7" to R.string.nt_l10n_color_7,
    "color8" to R.string.nt_l10n_color_8,
    "color9" to R.string.nt_l10n_color_9,
    "color10" to R.string.nt_l10n_color_10,
    "color11" to R.string.nt_l10n_color_11,
    "color12" to R.string.nt_l10n_color_12,
    "color13" to R.string.nt_l10n_color_13,
    "color14" to R.string.nt_l10n_color_14,
    "color15" to R.string.nt_l10n_color_15,
)


// Display labels are localized; persisted keys and actual colors stay unchanged.
private fun accentColorName(context: android.content.Context, color: Int): String {
    val labels = intArrayOf(R.string.nt_l10n_accent_purple, R.string.nt_l10n_accent_blue, R.string.nt_l10n_accent_green, R.string.nt_l10n_accent_orange, R.string.nt_l10n_accent_red, R.string.nt_l10n_accent_teal, R.string.nt_l10n_accent_pink, R.string.nt_l10n_accent_gold, R.string.nt_l10n_accent_white)
    val index = NewTermuxTheme.COLORS.indexOf(color)
    return if (index in labels.indices) context.getString(labels[index]) else NewTermuxTheme.getColorName(color)
}

private fun terminalThemeName(context: android.content.Context, key: String): String = when (key) {
    "default_dark" -> context.getString(R.string.nt_l10n_theme_default_dark)
    "oled_black" -> context.getString(R.string.nt_l10n_theme_oled_black)
    "amber" -> context.getString(R.string.nt_l10n_theme_amber)
    "low_contrast" -> context.getString(R.string.nt_l10n_theme_low_contrast)
    "custom" -> context.getString(R.string.nt_l10n_theme_custom)
    else -> NewTermuxColorTheme.getThemeName(key)
}
