// SPDX-FileCopyrightText: 2026 Aditya Mehra <aix.m@outlook.com>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

/**
 * @file SectionHeader.kt
 * @brief Uppercase, letter-spaced section label used to head grouped
 *        settings and list sections throughout the app.
 * @layer Frontend
 * @dependencies Jetpack Compose Material 3.
 */

package com.verzeta.android.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        modifier = modifier.padding(start = 24.dp, end = 24.dp, top = 18.dp, bottom = 6.dp),
        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.2.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
