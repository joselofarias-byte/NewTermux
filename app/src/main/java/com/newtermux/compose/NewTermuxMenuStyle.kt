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

/**
 * Shared outlined-card look for the app's pop-out menus, mirroring Bannerlator's MenuStyle:
 * a rounded (10dp) card filled with the surface color and a 1dp outline. Apply to a
 * DropdownMenu's `modifier` so the popup reads as an outlined card. Keep every menu call-site
 * on this so they stay identical.
 */
@Composable
fun Modifier.outlinedMenuCard(): Modifier {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(10.dp)
    return this
        .clip(shape)
        .background(cs.surface)
        .border(1.dp, cs.outline, shape)
}

/** The thin gray separator that sits between options inside an outlined menu card / option list. */
@Composable
fun MenuItemDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
}
