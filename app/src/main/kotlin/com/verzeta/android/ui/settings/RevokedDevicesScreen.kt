// SPDX-FileCopyrightText: 2026 Aditya Mehra <aix.m@outlook.com>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

/**
 * @file RevokedDevicesScreen.kt
 * @brief Read-only audit screen listing previously revoked paired clients.
 *        Reached from the Settings screen, which itself shows only active
 *        devices.
 * @layer Frontend
 * @dependencies MainViewModel + MainUiState (state.clients), ClientUi model,
 *               VerzetaTopBar / EmptyState shared components, Compose
 *               Material3.
 */

package com.verzeta.android.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.verzeta.android.MainUiState
import com.verzeta.android.MainViewModel
import com.verzeta.android.data.workbench.ClientUi
import com.verzeta.android.ui.components.EmptyState
import com.verzeta.android.ui.components.VerzetaTopBar
import com.verzeta.android.ui.theme.adaptiveContentMaxWidth
import com.verzeta.android.util.formatRelativeTimestamp

/**
 * Read-only audit history of revoked paired clients. The main Settings
 * screen lists only ACTIVE paired devices; users who want to see / verify
 * previously revoked devices drill into this screen via the
 * "Revoked devices" SettingsRow.
 *
 * Pure read surface. No actions. Mirrors the active-client row shape (icon
 * + name + monospace ids + timestamps) so the visual language stays
 * consistent with [SettingsScreen]'s ClientRow.
 */
@Composable
fun RevokedDevicesScreen(state: MainUiState, actions: MainViewModel) {
    val revoked = state.clients.filter { it.revoked }
    // Centered max-width container keeps the list readable on wide layouts.
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
    LazyColumn(modifier = Modifier.fillMaxSize().then(adaptiveContentMaxWidth())) {
        item {
            VerzetaTopBar(
                title = "Revoked devices",
                onBack = actions::closeRevokedDevices,
            )
        }
        item { Spacer(Modifier.height(8.dp)) }
        if (revoked.isEmpty()) {
            item {
                EmptyState(
                    title = "No revoked devices",
                    detail = "Devices you revoke stay listed here for audit until they are purged on the host.",
                )
            }
        } else {
            items(revoked, key = { it.id }) { client ->
                RevokedClientRow(client)
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }  // LazyColumn
    }  // Box centered container
}

/**
 * Read-only row for a revoked paired client. Same visual rhythm as
 * SettingsScreen.ClientRow but without the Revoke button (this surface
 * is audit-only) and with an explicit "Revoked" trailing label so the
 * row's purpose stays obvious.
 */
@Composable
private fun RevokedClientRow(client: ClientUi) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.History,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(modifier = Modifier.padding(start = 14.dp).weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    client.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val sub = listOfNotNull(
                client.lastSeenAt.takeIf(String::isNotBlank)?.let { "last seen ${formatRelativeTimestamp(it)}" },
                client.createdAt.takeIf(String::isNotBlank)?.let { "added ${formatRelativeTimestamp(it)}" },
            ).joinToString(" · ").ifBlank { client.id }
            Text(
                sub,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            "Revoked",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }
}
