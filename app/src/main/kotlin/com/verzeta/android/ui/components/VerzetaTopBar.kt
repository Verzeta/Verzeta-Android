// SPDX-FileCopyrightText: 2026 Aditya Mehra <aix.m@outlook.com>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

/**
 * @file VerzetaTopBar.kt
 * @brief Shared top-bar component for every top-level destination (Home,
 *        Chat/Conversations, Settings): one leading slot (back arrow or
 *        brand mark), a title, a trailing-actions row, and a divider
 *        underneath so the shell stays visually consistent across tabs.
 * @layer Frontend
 * @dependencies Jetpack Compose Material 3 + material-icons.
 */

package com.verzeta.android.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * A single top-bar component shared by every top-level destination
 * (Home, Chat/Conversations, Settings) so the shell stays visually
 * consistent. Previously each screen rolled its own header — same
 * pixel height but different paddings, different typography, and only
 * Settings drew the dividing line below — so jumping between tabs in
 * the bottom nav looked janky.
 *
 * One leading slot (back arrow OR brand mark, mutually exclusive) plus
 * a trailing-actions row. Always emits a HorizontalDivider underneath
 * so a screen rendered without a top-section card still has a clean
 * separation from the chrome.
 */
@Composable
fun VerzetaTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    leadingMark: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(
                    start = if (onBack != null) 4.dp else 16.dp,
                    end = 4.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when {
                onBack != null -> {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                }
                leadingMark != null -> {
                    Box { leadingMark() }
                    Spacer(Modifier.size(10.dp))
                }
            }
            Text(
                text = title,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = if (onBack != null) 4.dp else 0.dp),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            actions()
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}
