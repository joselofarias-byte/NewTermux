package com.newtermux.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

@Composable
fun Modifier.outlinedMenuCard(): Modifier {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(10.dp)
    return clip(shape)
        .background(colors.surface)
        .border(1.dp, colors.outline, shape)
}

@Composable
fun MenuItemDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
}
