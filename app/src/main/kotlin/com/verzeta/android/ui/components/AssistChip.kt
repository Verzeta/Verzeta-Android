// SPDX-FileCopyrightText: 2026 Aditya Mehra <aix.m@outlook.com>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

/**
 * @file AssistChip.kt
 * @brief Compact 32 dp selectable chip with optional leading icon, used
 *        in the chat composer row where Material 3's FilterChip is too
 *        tall.
 * @layer Frontend
 * @dependencies Jetpack Compose Material 3.
 */

package com.verzeta.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * Verzeta workbench chip. Material 3's FilterChip is too tall for our composer
 * row; this is a compact 32dp variant with optional leading icon. Selected state
 * tints the chip with secondary container; unselected uses an outlined tile.
 */
@Composable
fun VerzetaChip(
    label: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    leading: ImageVector? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer
    else MaterialTheme.colorScheme.surfaceContainerLow
    val contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
    else MaterialTheme.colorScheme.onSurface
    val borderColor = if (selected) MaterialTheme.colorScheme.secondaryContainer
    else MaterialTheme.colorScheme.outlineVariant

    val clickModifier = if (onClick != null && enabled) Modifier.clickable(onClick = onClick) else Modifier

    Row(
        modifier = modifier
            .height(32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(containerColor)
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .then(clickModifier)
            .padding(PaddingValues(horizontal = 12.dp)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        leading?.let {
            Icon(it, contentDescription = null, modifier = Modifier.size(16.dp), tint = contentColor)
            Spacer(Modifier.size(6.dp))
        }
        Text(label, color = contentColor, style = MaterialTheme.typography.labelLarge)
    }
}
