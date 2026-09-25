// SPDX-FileCopyrightText: 2026 Aditya Mehra <aix.m@outlook.com>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

/**
 * @file HomeScreen.kt
 * @brief Home screen showing the active host card, disconnected host list,
 *        Project Rooms entry point, and a recent-conversation feed for the
 *        connected host.
 * @layer UI
 * @dependencies Jetpack Compose, MainViewModel, MainUiState, HostUi,
 *               RecentConversationUi, ConnectionState
 */

package com.verzeta.android.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.outlined.ChatBubble
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verzeta.android.MainUiState
import com.verzeta.android.MainViewModel
import com.verzeta.android.data.host.HostStatus
import com.verzeta.android.data.host.HostUi
import com.verzeta.android.data.host.RecentBadge
import com.verzeta.android.data.host.RecentConversationUi
import com.verzeta.android.ui.components.ConnectionBadge
import com.verzeta.android.ui.components.HostStatusDot
import com.verzeta.android.ui.components.SectionHeader
import com.verzeta.android.ui.components.VerzetaMark
import com.verzeta.android.ui.components.VerzetaTopBar
import com.verzeta.android.ui.theme.adaptiveContentMaxWidth
import com.verzeta.android.util.formatRelativeTimestamp

/**
 * Home / launch surface. Layout rules to avoid duplicate CTAs:
 *  - The `+` icon in the top app bar is the canonical, always-available
 *    Add-host action.
 *  - When no host has been configured yet, the empty connected-host card
 *    shows a primary `Add host` button as the first-run hint.
 *  - When one or more hosts already exist, the empty card disappears and
 *    the user is expected to use the top-bar `+`. The "Other hosts" section
 *    renders only if there are disconnected hosts beyond the active one;
 *    it never carries its own Add-host button.
 */
@Composable
fun HomeScreen(state: MainUiState, actions: MainViewModel) {
    val connected = state.hosts.firstOrNull { it.status == HostStatus.Connected }
    val others = state.hosts.filter { it.status != HostStatus.Connected }
    val firstRun = state.hosts.isEmpty()

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(modifier = Modifier.fillMaxSize().then(adaptiveContentMaxWidth())) {
        item {
            VerzetaTopBar(
                title = "Verzeta",
                leadingMark = { VerzetaMark() },
                actions = {
                    IconButton(onClick = actions::showAddHost) {
                        Icon(Icons.Filled.Add, contentDescription = "Add host")
                    }
                },
            )
        }
        item { Spacer(Modifier.height(16.dp)) }

        item {
            if (connected != null) {
                ConnectedHostCard(
                    host = connected,
                    connectionState = state.connectionState,
                    onOpenChat = actions::showChat,
                    onManage = actions::showSettings,
                )
            } else {
                NoActiveHostCard(
                    detail = state.status,
                    showAddHostButton = firstRun,
                    onAddHost = actions::showAddHost,
                    lastConnectionError = state.lastConnectionError,
                    onDismissError = actions::dismissConnectionError,
                )
            }
        }

        if (others.isNotEmpty()) {
            item { SectionHeader("Other hosts") }
            items(others, key = { it.config.id }) { host ->
                HostRow(host = host, onClick = { actions.connectHost(host.config.id) })
            }
        }

        if (connected != null) {
            item {
                ProjectRoomsCta(onClick = actions::showProjectRoomsLanding)
            }
        }

        item {
            SectionHeader("Recent${state.activeHost?.let { " · ${it.config.name}" }.orEmpty()}")
        }

        if (state.recentConversations.isEmpty()) {
            item {
                EmptyRecentRow(
                    detail = if (state.activeHostId == null) {
                        "Connect a host to load recent conversations."
                    } else {
                        "Recent conversations from the active host will appear here."
                    },
                )
            }
        } else {
            items(state.recentConversations, key = { it.id }) { recent ->
                RecentConversationRow(recent = recent, onClick = { actions.openConversation(recent.id) })
            }
        }

        item { HomeFooter(state = state, actions = actions) }
        item { Spacer(Modifier.height(20.dp)) }
        }  // LazyColumn
    }  
}


@Composable
private fun ConnectedHostCard(
    host: HostUi,
    connectionState: com.verzeta.android.remote.ConnectionState,
    onOpenChat: () -> Unit,
    onManage: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.surfaceContainer,
                        ),
                    ),
                ),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HostStatusDot(host.status)
                    Spacer(Modifier.size(10.dp))
                    Text(
                        text = "CONNECTED",
                        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.6.sp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Spacer(Modifier.weight(1f))
                    ConnectionBadge(state = connectionState)
                    host.latencyMs?.let { latency ->
                        Spacer(Modifier.size(8.dp))
                        Text(
                            text = "${latency}ms",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.size(14.dp))
                Text(
                    text = host.config.name,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.size(4.dp))
                Text(
                    text = host.config.endpoint,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.size(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onOpenChat, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Bolt, contentDescription = null)
                        Spacer(Modifier.size(6.dp))
                        Text("Open chat")
                    }
                    FilledTonalButton(onClick = onManage) {
                        Icon(Icons.Filled.Settings, contentDescription = null)
                        Spacer(Modifier.size(6.dp))
                        Text("Manage")
                    }
                }
            }
        }
    }
}

@Composable
private fun NoActiveHostCard(
    detail: String,
    showAddHostButton: Boolean,
    onAddHost: () -> Unit,
    lastConnectionError: String? = null,
    onDismissError: () -> Unit = {},
) {
    var detailDialogOpen by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (showAddHostButton) "No active host" else "Disconnected",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                if (lastConnectionError != null) {
                    UnavailablePill(onClick = { detailDialogOpen = true })
                }
            }
            Spacer(Modifier.size(6.dp))
            Text(
                text = if (showAddHostButton) {
                    "Pair this Android device with a Verzeta desktop host to open the workbench."
                } else {
                    detail.ifBlank { "Tap a host below to reconnect, or use + to pair another." }
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (showAddHostButton) {
                Spacer(Modifier.size(16.dp))
                Button(onClick = onAddHost) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.size(6.dp))
                    Text("Add host")
                }
            }
        }
    }

    if (detailDialogOpen && lastConnectionError != null) {
        AlertDialog(
            onDismissRequest = { detailDialogOpen = false },
            icon = {
                Icon(
                    Icons.Filled.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
            },
            title = { Text("Host unavailable") },
            text = {
                Text(
                    text = lastConnectionError,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    detailDialogOpen = false
                    onDismissError()
                }) {
                    Text("Dismiss")
                }
            },
            dismissButton = {
                TextButton(onClick = { detailDialogOpen = false }) {
                    Text("Keep")
                }
            },
        )
    }
}

@Composable
private fun UnavailablePill(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.errorContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.ErrorOutline,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onErrorContainer,
        )
        Spacer(Modifier.size(6.dp))
        Text(
            text = "Unavailable",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

@Composable
private fun HostRow(host: HostUi, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Dns, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(modifier = Modifier.padding(start = 14.dp).weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(host.config.name, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.size(8.dp))
                HostStatusDot(host.status)
            }
            Text(
                host.config.endpoint,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (host.detail.isNotBlank()) {
                Text(
                    host.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Icon(
            imageVector = if (host.status == HostStatus.AuthRequired || host.status == HostStatus.PairingRequired) {
                Icons.Filled.Lock
            } else {
                Icons.Filled.ChevronRight
            },
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RecentConversationRow(recent: RecentConversationUi, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = if (recent.pinned) Icons.Outlined.PushPin else Icons.Outlined.ChatBubble,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
        Column(modifier = Modifier.padding(start = 14.dp).weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    recent.title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    formatRelativeTimestamp(iso = recent.updatedLabel).ifBlank { recent.updatedLabel },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                modifier = Modifier.padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (recent.streaming) {
                    Text(
                        "● streaming",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                recent.badges.forEach { badge ->
                    Text(
                        badge.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyRecentRow(detail: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Text("No recent conversations", style = MaterialTheme.typography.bodyLarge)
        Text(
            detail,
            modifier = Modifier.padding(top = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HomeFooter(state: MainUiState, actions: MainViewModel) {
    Column {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                state.status,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.activeHostId != null) {
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(onClick = actions::ping, enabled = !state.busy) {
                        Text("Ping")
                    }
                    OutlinedButton(onClick = actions::revokeSelf, enabled = !state.busy) {
                        Text("Revoke this device")
                    }
                }
            }
        }
    }
}

private val RecentBadge.label: String
    get() = when (this) {
        RecentBadge.Plan -> "Plan"
        RecentBadge.Agent -> "Agent"
        RecentBadge.Canvas -> "Canvas"
        RecentBadge.Group -> "Group"
    }

@Composable
private fun ProjectRoomsCta(onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.tertiary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Bolt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiary,
                )
            }
            Spacer(Modifier.size(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Project Rooms",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    ),
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Text(
                    "Start a team from a template. Pick the agents and set the goal.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
    }
}
