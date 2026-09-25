// SPDX-FileCopyrightText: 2026 Aditya Mehra <aix.m@outlook.com>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

/**
 * @file McpServersScreen.kt
 * @brief Read-only browser for the host's configured MCP servers, fetched via
 *        `mcp.list`. Each row shows connection status and tool count; tapping
 *        navigates to the per-server tool drill-down.
 * @layer UI
 * @dependencies Jetpack Compose, MainViewModel, MainUiState, McpServerUi, McpStatus
 */

package com.verzeta.android.ui.mcp

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.verzeta.android.MainUiState
import com.verzeta.android.MainViewModel
import com.verzeta.android.data.host.HostStatus
import com.verzeta.android.data.mcp.McpServerUi
import com.verzeta.android.data.mcp.McpStatus
import com.verzeta.android.ui.components.EmptyState
import com.verzeta.android.ui.tools.TopBar

/**
 * Read-only browser for `mcp.list`. Each row is tappable, which navigates
 * to [McpServerToolsScreen] for the per-server `mcp.server_tools` drill-down.
 *
 * The status colour mirrors the desktop ToolsPage MCP card border: green
 * connected, yellow connecting, red error, grey disconnected/disabled.
 * Switches/edit/remove from the desktop are intentionally absent — there's
 * no `mcp.set_enabled` / `mcp.add` / `mcp.remove` op for remote clients.
 */
@Composable
fun McpServersScreen(state: MainUiState, actions: MainViewModel) {
    val connected = state.activeHost?.status == HostStatus.Connected
    LaunchedEffect(connected) { if (connected) actions.refreshMcpServers() }

    Column(modifier = Modifier.fillMaxSize()) {
        TopBar(
            title = "MCP Servers",
            subtitle = state.mcpStatus.takeIf { it.isNotBlank() },
            onBack = actions::closeMcpServers,
            onRefresh = if (connected) actions::refreshMcpServers else null,
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        if (!connected) {
            EmptyState(
                title = "Not connected",
                detail = "Connect a host to see its configured MCP servers.",
            )
            return@Column
        }
        if (state.mcpServers.isEmpty()) {
            EmptyState(
                title = "No MCP servers configured",
                detail = "Add MCP servers from the desktop in Settings → Tools → MCP Servers. " +
                    "They will appear here once configured.",
            )
            return@Column
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(state.mcpServers, key = { it.name }) { server ->
                McpServerRow(server = server, onClick = { actions.openMcpServer(server.name) })
            }
            item { Spacer(Modifier.height(40.dp)) }
        }
    }
}

@Composable
private fun McpServerRow(server: McpServerUi, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(server.status)
        Column(modifier = Modifier.padding(start = 14.dp).weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    server.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.size(8.dp))
                TypeBadge(server.type)
            }
            Spacer(Modifier.height(2.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                StatusText(server.status)
                if (server.status == McpStatus.Connected) {
                    Text(
                        "·  ${server.toolCount} tool${if (server.toolCount == 1) "" else "s"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (server.transportDetail.isNotBlank()) {
                Text(
                    server.transportDetail,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (server.status == McpStatus.Error && server.errorMessage.isNotBlank()) {
                Text(
                    server.errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatusDot(status: McpStatus) {
    Box(
        modifier = Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(statusColor(status)),
    )
}

@Composable
private fun StatusText(status: McpStatus) {
    Text(
        when (status) {
            McpStatus.Connected -> "Connected"
            McpStatus.Connecting -> "Connecting"
            McpStatus.Error -> "Error"
            McpStatus.Disconnected -> "Disconnected"
            McpStatus.Disabled -> "Disabled"
        },
        style = MaterialTheme.typography.bodySmall,
        color = statusColor(status),
    )
}

@Composable
private fun statusColor(status: McpStatus): Color = when (status) {
    McpStatus.Connected -> MaterialTheme.colorScheme.primary
    McpStatus.Connecting -> MaterialTheme.colorScheme.tertiary
    McpStatus.Error -> MaterialTheme.colorScheme.error
    McpStatus.Disconnected, McpStatus.Disabled -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun TypeBadge(type: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            type,
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
