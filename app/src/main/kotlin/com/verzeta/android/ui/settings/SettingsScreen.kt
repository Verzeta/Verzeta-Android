// SPDX-FileCopyrightText: 2026 Aditya Mehra <aix.m@outlook.com>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

/**
 * @file SettingsScreen.kt
 * @brief Main Settings surface: host list management (add / connect /
 *        remove), paired-device list with revoke, self-revoke, host catalog
 *        entry points (Tools / MCP Servers / Skills), session status, and
 *        Help / About navigation.
 * @layer Frontend
 * @dependencies MainViewModel + MainUiState, HostUi / ClientUi models,
 *               VerzetaTopBar / SectionHeader / HostStatusDot components,
 *               relative-timestamp formatter, Compose Material3.
 */

package com.verzeta.android.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.verzeta.android.MainUiState
import com.verzeta.android.MainViewModel
import com.verzeta.android.data.host.HostStatus
import com.verzeta.android.data.host.HostUi
import com.verzeta.android.data.workbench.ClientUi
import com.verzeta.android.ui.components.HostStatusDot
import com.verzeta.android.ui.components.SectionHeader
import com.verzeta.android.ui.components.VerzetaTopBar
import com.verzeta.android.ui.theme.adaptiveContentMaxWidth
import com.verzeta.android.util.formatRelativeTimestamp

/**
 * Settings. Surfaces only the host-supported control surface:
 *  - Hosts: list / add / connect / remove (local persistence + `auth.token`).
 *  - Paired clients: `clients.list` + `clients.revoke`.
 *  - This device: `auth.revoke_self`.
 *  - About: static info.
 *
 * Local toggle preferences (Show thinking / Tool activity inline / RAG by
 * default / High contrast / Dynamic color) have been removed because
 * `msg.send` accepts only `conv_id` and `text`, so toggle state had no host
 * effect — keeping them would have been pure UI fiction. They will return
 * once the host accepts conversation-level options over the wire.
 *
 * Connection diagnostics, heartbeat activity, and the host settings entries
 * panel have also been removed: the host's `remote-ws` dispatcher does not
 * expose `settings.get` / `heartbeat.recent_changes` ops.
 */
@Composable
fun SettingsScreen(state: MainUiState, actions: MainViewModel) {
    // Centered max-width container keeps the list readable on wide layouts.
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
    LazyColumn(modifier = Modifier.fillMaxSize().then(adaptiveContentMaxWidth())) {
        // Shared VerzetaTopBar so Home / Chat / Settings headers
        // match. Refresh action stays in the trailing slot;
        // the bar emits its own divider so the explicit one below
        // here was dropped.
        item {
            val connected = state.activeHost?.status == HostStatus.Connected
            VerzetaTopBar(
                title = "Settings",
                actions = {
                    IconButton(
                        onClick = actions::refreshClients,
                        enabled = connected && !state.busy,
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh paired clients")
                    }
                },
            )
        }
        // 8-dp gap between top-bar divider and first
        // SectionHeader. SectionHeader's own 18-dp top padding
        // provides additional breathing room; the small spacer here
        // matches Home's 16-dp gap visually since SectionHeader text
        // sits closer to its top edge.
        item { Spacer(Modifier.height(8.dp)) }

        item { SectionHeader("Hosts") }
        if (state.hosts.isEmpty()) {
            item {
                SettingsRow(
                    icon = Icons.Filled.Add,
                    title = "Add a desktop host",
                    subtitle = "Pair a Verzeta desktop running with Remote Access enabled.",
                    onClick = actions::showAddHost,
                )
            }
        } else {
            items(state.hosts, key = { it.config.id }) { host ->
                HostRow(
                    host = host,
                    busy = state.busy,
                    onConnect = { actions.connectHost(host.config.id) },
                    onRemove = { actions.removeHost(host.config.id) },
                )
            }
            item {
                SettingsRow(
                    icon = Icons.Filled.Add,
                    title = "Add another host",
                    subtitle = "Pair this device with another Verzeta desktop.",
                    onClick = actions::showAddHost,
                )
            }
        }

        item {
            SettingsRow(
                icon = Icons.Filled.VpnKey,
                title = "Revoke this device",
                subtitle = if (state.activeHostId == null) "Connect a host first"
                else "Drop the token issued by ${state.activeHost?.config?.name ?: "the host"}.",
                onClick = actions::revokeSelf,
                enabled = state.activeHost?.status == HostStatus.Connected && !state.busy,
            )
        }

        item { SectionHeader("Devices paired with this host") }
        item {
            SettingsRow(
                icon = Icons.Filled.Info,
                title = "What is this?",
                subtitle = "Every device paired with the active host appears here, such as another " +
                    "Android phone or a VS Code window. Each device has its own token. " +
                    "Revoking a device invalidates its token on the host immediately.",
            )
        }
        when {
            state.activeHost?.status != HostStatus.Connected -> item {
                SettingsRow(
                    icon = Icons.Filled.Person,
                    title = "Not connected",
                    subtitle = "Connect a host to see its paired devices.",
                )
            }
            state.clients.isEmpty() -> item {
                SettingsRow(
                    icon = Icons.Filled.Person,
                    title = "No devices paired",
                    subtitle = state.settingsStatus,
                )
            }
            else -> {
                // Settings shows ONLY active paired clients. Revoked
                // clients drill into the dedicated RevokedDevicesScreen
                // via the link below the active list — rendering both
                // active and revoked inline made the list endless once
                // a device had been re-paired a few times.
                val active = state.clients.filterNot { it.revoked }
                val revokedCount = state.clients.count { it.revoked }
                items(active, key = { it.id }) { client ->
                    ClientRow(
                        client = client,
                        busy = state.busy,
                        onRevoke = { actions.revokeClient(client.id) },
                    )
                }
                if (revokedCount > 0) {
                    item {
                        SettingsRow(
                            icon = Icons.Filled.History,
                            title = "Revoked devices",
                            subtitle = "$revokedCount previously paired " +
                                (if (revokedCount == 1) "device" else "devices") +
                                ", kept as read-only audit history.",
                            onClick = actions::showRevokedDevices,
                        )
                    }
                }
            }
        }

        item { SectionHeader("Host catalogs") }
        item {
            SettingsRow(
                icon = Icons.Filled.Build,
                title = "Tools",
                subtitle = if (state.activeHost?.status != HostStatus.Connected) {
                    "Connect a host to browse registered tools."
                } else {
                    "Built-in, custom, and MCP tools the assistant can call."
                },
                onClick = actions::showTools,
                enabled = state.activeHost?.status == HostStatus.Connected,
            )
        }
        item {
            SettingsRow(
                icon = Icons.Filled.Cable,
                title = "MCP Servers",
                subtitle = if (state.activeHost?.status != HostStatus.Connected) {
                    "Connect a host to browse MCP servers."
                } else {
                    "Configured Model Context Protocol servers and their connection state."
                },
                onClick = actions::showMcpServers,
                enabled = state.activeHost?.status == HostStatus.Connected,
            )
        }
        item {
            SettingsRow(
                icon = Icons.Filled.AutoFixHigh,
                title = "Skills",
                subtitle = if (state.activeHost?.status != HostStatus.Connected) {
                    "Connect a host to browse installed skills."
                } else {
                    "Installed skill packs with review state and metadata."
                },
                onClick = actions::showSkills,
                enabled = state.activeHost?.status == HostStatus.Connected,
            )
        }
        item {
            SettingsRow(
                icon = Icons.Filled.Search,
                title = "Web Search",
                subtitle = if (state.activeHost?.status != HostStatus.Connected) {
                    "Connect a host to choose a web-search provider."
                } else {
                    "Pick the web-search backend (keys are configured on the desktop)."
                },
                onClick = actions::showSearchProviders,
                enabled = state.activeHost?.status == HostStatus.Connected,
            )
        }

        item { SectionHeader("Session") }
        item {
            SettingsRow(
                icon = Icons.Filled.Info,
                title = state.status.ifBlank { "Idle" },
                subtitle = if (state.activeHostId != null) {
                    val parts = listOfNotNull(
                        state.authenticatedName.takeIf(String::isNotBlank)?.let { "as $it" },
                        state.authenticatedClientId.takeIf(String::isNotBlank)?.let { "id ${it.take(8)}" },
                    )
                    if (parts.isEmpty()) "Authenticated" else "Authenticated " + parts.joinToString(" · ")
                } else {
                    "No active host."
                },
            )
        }
        item {
            SettingsRow(
                icon = Icons.Filled.Refresh,
                title = "Verify session",
                subtitle = "Ask the host to confirm it still accepts this device's token.",
                onClick = actions::verifySession,
                enabled = state.activeHost?.status == HostStatus.Connected && !state.busy,
            )
        }

        item { SectionHeader("Help & About") }
        item {
            // The in-app Help screen renders the bundled user docs
            // from assets/help/ (canonical source docs/User/*.md,
            // copied at build time).
            SettingsRow(
                icon = Icons.Filled.MenuBook,
                title = "Help & docs",
                subtitle = "Guides for pairing, chatting, projects, canvas, settings, and troubleshooting.",
                onClick = actions::showHelp,
            )
        }
        item {
            // Drill into the proper About screen (brand mark + version +
            // verzeta.com + privacy + license). The previous static row
            // had no version, no links, nothing actionable. The clickable
            // row + dedicated AboutScreen matches Material's standard
            // About-app pattern.
            SettingsRow(
                icon = Icons.Filled.Info,
                title = "About Verzeta",
                subtitle = "Version, project links, privacy policy, license, and open-source attributions.",
                onClick = actions::showAbout,
            )
        }

        item { Spacer(Modifier.height(40.dp)) }
    }  // LazyColumn
    }  // Box centered container
}

// SettingsTopBar was folded into the shared VerzetaTopBar invocation
// in SettingsScreen for header consistency across the top-level
// destinations.

@Composable
private fun HostRow(host: HostUi, busy: Boolean, onConnect: () -> Unit, onRemove: () -> Unit) {
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
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
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
        TextButton(onClick = onConnect, enabled = !busy && host.status != HostStatus.Connected) {
            Text("Connect")
        }
        TextButton(onClick = onRemove, enabled = !busy) {
            Text("Remove")
        }
    }
}

@Composable
private fun ClientRow(client: ClientUi, busy: Boolean, onRevoke: () -> Unit) {
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
            Icon(Icons.Filled.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(modifier = Modifier.padding(start = 14.dp).weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    client.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (client.current) {
                    Spacer(Modifier.size(8.dp))
                    Text(
                        "This device",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (client.revoked) {
                    Spacer(Modifier.size(8.dp))
                    Text(
                        "Revoked",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
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
        TextButton(onClick = onRevoke, enabled = !busy && !client.current && !client.revoked) {
            Text("Revoke")
        }
    }
}

@Composable
internal fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
) {
    val rowModifier = if (onClick != null && enabled) {
        Modifier.fillMaxWidth().clickable(onClick = onClick)
    } else {
        Modifier.fillMaxWidth()
    }
    Row(
        modifier = rowModifier
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.size(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            if (subtitle.isNotBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
