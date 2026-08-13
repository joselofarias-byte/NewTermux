package com.newtermux.compose

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.newtermux.features.NewTermuxTheme

@Composable
fun NewTermuxComposeTheme(
    context: Context,
    content: @Composable () -> Unit,
) {
    val accent = Color(NewTermuxTheme.getAccentColor(context))
    val colorScheme = darkColorScheme(
        primary = accent,
        onPrimary = Color.Black,
        primaryContainer = accent.copy(alpha = 0.24f),
        onPrimaryContainer = Color(0xFFECECEC),
        secondary = accent,
        onSecondary = Color.Black,
        background = Color(0xFF121212),
        onBackground = Color(0xFFECECEC),
        surface = Color(0xFF1E1E1E),
        onSurface = Color(0xFFECECEC),
        surfaceVariant = Color(0xFF2A2A2A),
        onSurfaceVariant = Color(0xFFB6B6B6),
        outline = Color(0xFF444444),
        error = Color(0xFFCF6679),
        onError = Color.Black,
    )
    MaterialTheme(colorScheme = colorScheme, content = content)
}
