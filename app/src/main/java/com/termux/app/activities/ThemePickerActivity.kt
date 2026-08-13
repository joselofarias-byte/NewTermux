package com.termux.app.activities

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
    var showScope by remember { mutableStateOf(false) }
    var editorKeys by remember { mutableStateOf<List<Pair<String, String>>?>(null) }
    val terminalKeys = remember { NewTermuxColorTheme.THEME_KEYS.filter { it != NewTermuxColorTheme.THEME_KEY_CUSTOM } }
    val accentColors = remember { NewTermuxTheme.COLORS.toList() }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Themes & Colors") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
        }, actions = {
            IconButton(onClick = { overflowOpen = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More") }
            DropdownMenu(expanded = overflowOpen, onDismissRequest = { overflowOpen = false }, modifier = Modifier.outlinedMenuCard()) {
                DropdownMenuItem(text = { Text("Custom Theme") }, onClick = { overflowOpen = false; showScope = true })
            }
        })
    }) { padding ->
        LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.fillMaxSize().padding(padding), contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item(span = { GridItemSpan(maxLineSpan) }) { SectionHeader("Terminal Theme") }
            items(terminalKeys, key = { it }) { key ->
                TerminalThemeCard(key, key == activeTheme, activeAccent) {
                    NewTermuxColorTheme.applyTheme(context, key)
                    activeTheme = key
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) { SectionHeader("Accent Color") }
            items(accentColors, key = { it }) { color ->
                AccentSwatch(color, color == activeAccent, false, NewTermuxTheme.getColorName(color)) {
                    NewTermuxTheme.setAccentColor(context, color)
                    activeAccent = color
                }
            }
            item {
                val customActive = NewTermuxTheme.isCustomAccentActive(context)
                AccentSwatch(if (customActive) activeAccent else 0xFF666666.toInt(), customActive, true, "Custom…") {
                    ColorPickerDialog(context).setInitialColor(NewTermuxTheme.getAccentColor(context)).setOnColorSelectedListener { picked ->
                        NewTermuxTheme.setAccentColor(context, picked)
                        activeAccent = picked
                    }.show()
                }
            }
        }
    }

    if (showScope) {
        AlertDialog(onDismissRequest = { showScope = false }, title = { Text("Custom Theme Scope") }, text = {
            Column {
                DropdownMenuItem(text = { Text("Core 3  (Background, Foreground, Cursor)") }, onClick = { showScope = false; editorKeys = CORE_KEYS })
                MenuItemDivider()
                DropdownMenuItem(text = { Text("All 18 terminal colors") }, onClick = { showScope = false; editorKeys = ALL_KEYS })
            }
        }, confirmButton = {}, dismissButton = { TextButton(onClick = { showScope = false }) { Text("Cancel") } })
    }

    editorKeys?.let { keys ->
        CustomThemeEditorDialog(keys, onDismiss = { editorKeys = null }) { colorMap ->
            val base = NewTermuxColorTheme.getCustomThemeContent(context)
            NewTermuxColorTheme.applyCustomTheme(context, buildThemeContent(base, colorMap))
            editorKeys = null
        }
    }
}

@Composable private fun SectionHeader(text: String) { Text(text, modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary) }

@Composable
private fun TerminalThemeCard(key: String, active: Boolean, accent: Int, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            TerminalPreview(NewTermuxColorTheme.getPreviewColors(key), active, accent, Modifier.fillMaxWidth().aspectRatio(1.3f).padding(4.dp))
            Text(NewTermuxColorTheme.getThemeName(key), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(bottom = 6.dp, start = 4.dp, end = 4.dp))
        }
    }
}

@Composable
private fun TerminalPreview(colors: IntArray, active: Boolean, accent: Int, modifier: Modifier = Modifier) {
    val textPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.MONOSPACE } }
    Canvas(modifier = modifier.clip(RoundedCornerShape(4.dp))) {
        val d = 1.dp.toPx(); val w = size.width; val h = size.height
        val bg = Color(colors[0]); val fg = colors[1]; val toolbar = Color(colors[2]); val green = colors[3]; val cursor = Color(colors[4])
        drawRect(bg); val toolH = 11 * d; drawRect(toolbar, size = androidx.compose.ui.geometry.Size(w, toolH))
        drawCircle(Color(0xFFFF5F57), 1.8f * d, Offset(5.5f * d, toolH / 2f)); drawCircle(Color(0xFFFFBD2E), 1.8f * d, Offset(10.5f * d, toolH / 2f)); drawCircle(Color(0xFF28CA41), 1.8f * d, Offset(15.5f * d, toolH / 2f))
        val ts = 6f * d; textPaint.textSize = ts; val x = 4 * d; var y = toolH + 8 * d; val lh = 8 * d
        drawIntoCanvas { c ->
            val nc = c.nativeCanvas; val prompt = "$ "; textPaint.color = green; nc.drawText(prompt, x, y, textPaint); val pw = textPaint.measureText(prompt); textPaint.color = fg; nc.drawText("ls -la", x + pw, y, textPaint)
            y += lh; textPaint.color = dim(fg); nc.drawText("total 8", x, y, textPaint); y += lh; textPaint.color = green; val perm = "drwx "; nc.drawText(perm, x, y, textPaint); val permW = textPaint.measureText(perm); textPaint.color = fg; nc.drawText("home", x + permW, y, textPaint)
            if (y + lh + 2 * d < h) { y += lh; textPaint.color = green; nc.drawText(prompt, x, y, textPaint); val cx2 = x + textPaint.measureText(prompt); drawRect(cursor, Offset(cx2, y - ts), androidx.compose.ui.geometry.Size(5 * d, ts + 1.5f * d)) }
        }
        if (active) { val sw = 2.5f * d; drawRect(Color(accent), Offset(sw / 2f, sw / 2f), androidx.compose.ui.geometry.Size(w - sw, h - sw), style = Stroke(sw)) }
    }
}

@Composable
private fun AccentSwatch(color: Int, active: Boolean, isCustomSlot: Boolean, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp)) {
        Canvas(Modifier.size(52.dp)) {
            val cx = size.width / 2f; val cy = size.height / 2f; val d = 1.dp.toPx(); val radius = minOf(cx, cy) - 3 * d
            if (isCustomSlot) drawCircle(Brush.sweepGradient(listOf(Color(0xFFFF0000), Color(0xFFFF8C00), Color(0xFFFFFF00), Color(0xFF00CC00), Color(0xFF0088FF), Color(0xFF8800FF), Color(0xFFFF0000)), Offset(cx, cy)), radius, Offset(cx, cy)) else drawCircle(Color(color), radius, Offset(cx, cy))
            if (active) { val sw = 2.5f * d; drawCircle(Color.White, radius - sw / 2f, Offset(cx, cy), style = Stroke(sw)); drawIntoCanvas { c -> val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = android.graphics.Color.WHITE; textAlign = Paint.Align.CENTER; isFakeBoldText = true; textSize = 14 * d }; c.nativeCanvas.drawText("✓", cx, cy + 5 * d, p) } }
        }
        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

@Composable
private fun CustomThemeEditorDialog(keys: List<Pair<String, String>>, onDismiss: () -> Unit, onApply: (Map<String, Int>) -> Unit) {
    val context = LocalContext.current; val base = remember { NewTermuxColorTheme.getCustomThemeContent(context) }; val colorMap = remember { mutableStateMapOf<String, Int>().apply { putAll(parseThemeContent(base, keys.map { it.first })) } }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Custom Theme") }, text = { Column(Modifier.verticalScroll(rememberScrollState())) { keys.forEach { (key, label) -> val cur = colorMap[key] ?: 0xFF808080.toInt(); Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) { Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium); Spacer(Modifier.size(8.dp)); Box(Modifier.size(36.dp).clip(RoundedCornerShape(6.dp)).clickable { ColorPickerDialog(context).setInitialColor(colorMap[key] ?: 0xFF808080.toInt()).setOnColorSelectedListener { picked -> colorMap[key] = picked }.show() }) { Canvas(Modifier.fillMaxSize()) { drawRect(Color(cur)) } } } } } }, confirmButton = { TextButton(onClick = { onApply(colorMap.toMap()) }) { Text("Apply") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

private fun dim(color: Int): Int { val r = (((color shr 16) and 0xFF) * 0.55f).toInt(); val g = (((color shr 8) and 0xFF) * 0.55f).toInt(); val b = ((color and 0xFF) * 0.55f).toInt(); return (0xFF shl 24) or (r shl 16) or (g shl 8) or b }
private fun parseThemeContent(content: String?, keys: List<String>): Map<String, Int> { val map = LinkedHashMap<String, Int>(); keys.forEach { map[it] = 0xFF808080.toInt() }; if (content == null) return map; for (raw in content.split("\n")) { val line = raw.trim(); if (line.isEmpty() || line.startsWith("#")) continue; val eq = line.indexOf('='); if (eq < 0) continue; val k = line.substring(0, eq).trim(); val v = line.substring(eq + 1).trim(); if (map.containsKey(k)) try { map[k] = android.graphics.Color.parseColor(if (v.startsWith("#")) v else "#$v") } catch (_: IllegalArgumentException) {} }; return map }
private fun buildThemeContent(baseContent: String?, colorMap: Map<String, Int>): String { val allLines = LinkedHashMap<String, String>(); if (baseContent != null) for (raw in baseContent.split("\n")) { val line = raw.trim(); if (line.isEmpty()) continue; val eq = line.indexOf('='); if (eq < 0) continue; allLines[line.substring(0, eq).trim()] = line.substring(eq + 1).trim() }; for ((k, v) in colorMap) allLines[k] = String.format("#%06X", 0xFFFFFF and v); return buildString { for ((k, v) in allLines) append(k).append('=').append(v).append('\n') } }
private val CORE_KEYS = listOf("background" to "Background", "foreground" to "Foreground", "cursor" to "Cursor")
private val ALL_KEYS = listOf("background" to "Background", "foreground" to "Foreground", "cursor" to "Cursor", "color0" to "Color 0 (Black)", "color1" to "Color 1 (Red)", "color2" to "Color 2 (Green)", "color3" to "Color 3 (Yellow)", "color4" to "Color 4 (Blue)", "color5" to "Color 5 (Magenta)", "color6" to "Color 6 (Cyan)", "color7" to "Color 7 (White)", "color8" to "Color 8 (Bright Black)", "color9" to "Color 9 (Bright Red)", "color10" to "Color 10 (Bright Green)", "color11" to "Color 11 (Bright Yellow)", "color12" to "Color 12 (Bright Blue)", "color13" to "Color 13 (Bright Magenta)", "color14" to "Color 14 (Bright Cyan)", "color15" to "Color 15 (Bright White)")
