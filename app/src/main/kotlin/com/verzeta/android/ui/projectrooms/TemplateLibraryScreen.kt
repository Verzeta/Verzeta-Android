// SPDX-FileCopyrightText: 2026 Aditya Mehra <aix.m@outlook.com>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

/**
 * @file TemplateLibraryScreen.kt
 * @brief Full-screen template catalog showing all built-in and user-saved
 *        project room templates in an adaptive grid, with pin and delete
 *        affordances for user-owned entries.
 * @layer UI
 * @dependencies Jetpack Compose, Material3, MainViewModel, MainUiState,
 *               ProjectTemplateCard
 */

package com.verzeta.android.ui.projectrooms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.verzeta.android.MainUiState
import com.verzeta.android.MainViewModel
import com.verzeta.android.data.host.HostStatus
import com.verzeta.android.data.projectroom.ProjectTemplateUi
import com.verzeta.android.ui.components.EmptyState
import com.verzeta.android.ui.components.VerzetaTopBar
import com.verzeta.android.ui.theme.adaptiveCatalogColumns

/**
 * Template Library — the full template catalog. Built-ins and user-saved
 * templates are shown intermixed; user-saved entries get the pin and delete
 * affordances inline on each card. Mirrors the desktop Template Library
 * surface with the same actions, adapted for mobile-narrow screens.
 */
@Composable
fun TemplateLibraryScreen(state: MainUiState, actions: MainViewModel) {
    val connected = state.activeHost?.status == HostStatus.Connected
    var deleting by remember { mutableStateOf<ProjectTemplateUi?>(null) }

    LaunchedEffect(connected) {
        if (connected && state.projectTemplates.isEmpty()) {
            actions.refreshProjectTemplates()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        VerzetaTopBar(
            title = "Template Library",
            onBack = actions::closeTemplateLibrary,
        )

        if (!connected) {
            EmptyState(
                title = "No connected host",
                detail = "Connect or pair a desktop host before browsing the template library.",
                actionLabel = "Home",
                onAction = actions::showHome,
            )
            return@Column
        }

        if (state.projectTemplates.isEmpty()) {
            EmptyState(
                title = "Loading templates…",
                detail = state.projectTemplatesStatus,
            )
            return@Column
        }

        // Adaptive catalog grid: phone (Compact) = 1 column, medium tablet
        // / foldable inner = 2 columns, expanded large tablet / desktop =
        // 3 columns. ProjectTemplateCard renders identically in every cell.
        LazyVerticalGrid(
            columns = GridCells.Fixed(adaptiveCatalogColumns()),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(state.projectTemplates, key = { it.id }) { template ->
                ProjectTemplateCard(
                    template = template,
                    onClick = { actions.selectProjectTemplate(template.id) },
                    onPin = if (template.isUserSaved) {
                        { actions.pinProjectTemplate(template.id, !template.isPinned) }
                    } else null,
                    onDelete = if (template.isUserSaved) {
                        { deleting = template }
                    } else null,
                )
            }
        }
    }

    deleting?.let { template ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete template?") },
            text = {
                Text(
                    "\"${template.name.ifBlank { template.id }}\" will be removed from your library. Built-in templates aren't affected.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    actions.deleteUserProjectTemplate(template.id)
                    deleting = null
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text("Cancel") }
            },
        )
    }
}
