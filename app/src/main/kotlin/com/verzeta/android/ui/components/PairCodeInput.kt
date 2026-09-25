// SPDX-FileCopyrightText: 2026 Aditya Mehra <aix.m@outlook.com>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

/**
 * @file PairCodeInput.kt
 * @brief Six-cell numeric pair-code input used during host pairing. A
 *        hidden text field captures keyboard input while six rounded
 *        cells render the digits, with the next empty cell highlighted.
 * @layer Frontend
 * @dependencies Jetpack Compose foundation text + Material 3.
 */

package com.verzeta.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/**
 * Six-cell pair-code input. Renders as six rounded boxes whose content mirrors a
 * single hidden TextField; the field is focusable so the keyboard opens, while
 * the visible cells render the digits with monospaced typography matching the
 * desktop pairing dialog.
 *
 * The active cell (the next empty slot) is highlighted with the primary border
 * to telegraph where the next digit will land.
 */
@Composable
fun PairCodeInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Box {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                repeat(6) { index ->
                    PairCodeCell(
                        digit = value.getOrNull(index)?.toString().orEmpty(),
                        isActive = index == value.length,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            BasicTextField(
                value = value,
                onValueChange = { input ->
                    val digits = input.filter(Char::isDigit).take(6)
                    if (digits != value) onValueChange(digits)
                },
                modifier = Modifier
                    .matchParentSize(),
                enabled = enabled,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                singleLine = true,
                textStyle = TextStyle(color = androidx.compose.ui.graphics.Color.Transparent),
                cursorBrush = SolidColor(androidx.compose.ui.graphics.Color.Transparent),
            )
        }
    }
}

@Composable
private fun PairCodeCell(digit: String, isActive: Boolean, modifier: Modifier = Modifier) {
    val borderColor = if (isActive) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.outline
    Box(
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .padding(4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = digit,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Monospace),
        )
    }
}
