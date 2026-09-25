// SPDX-FileCopyrightText: 2026 Aditya Mehra <aix.m@outlook.com>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

/**
 * @file McpServerToolsScreen.kt
 * @brief Drill-down screen displaying the tools advertised by a single MCP
 *        server, fetched via `mcp.server_tools`. Shows each tool's name,
 *        description, and qualified `server:toolname` identifier.
 * @layer UI
 * @dependencies Jetpack Compose, MainViewModel, MainUiState, McpToolUi
 */

package com.verzeta.android.ui.mcp

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
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.verzeta.android.MainUiState
import com.verzeta.android.MainViewModel
import com.verzeta.android.data.host.HostStatus
import com.verzeta.android.data.mcp.McpToolUi
import com.verzeta.android.ui.components.EmptyState
import com.verzeta.android.ui.tools.TopBar

/**
 * Drill-down for `mcp.server_tools`. Reads from
 * [MainUiState.mcpServerTools] keyed by [MainUiState.activeMcpServerName].
 *
 * The wire shape for this op is intentionally slim — just `name`,
 * `description`, `server` per row. We render a one-line description and
 * the qualified `server:toolname` underneath, since the host exposes them
 * to the LLM that way.
 */
@Composable
fun McpServerToolsScreen(state: MainUiState, actions: MainViewModel) {
    val connected = state.activeHost?.status == HostStatus.Connected
    val serverName = state.activeMcpServerName
    LaunchedEffect(connected, serverName) {
        if (connected && !serverName.isNullOrBlank()) actions.refreshMcpServerTools(serverName)
    }

    val tools = serverName?.let { state.mcpServerTools[it].orEmpty() } ?: emptyList()
    val server = state.mcpServers.firstOrNull { it.name == serverName }
    val subtitle = when {
        serverName.isNullOrBlank() -> null
        server == null -> "Server not in current catalog"
        tools.isEmpty() -> "No tools advertised yet"
        else -> "${tools.size} tool${if (tools.size == 1) "" else "s"}"
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopBar(
            title = serverName.orEmpty().ifBlank { "MCP Server" },
            subtitle = subtitle,
            onBack = actions::closeMcpServer,
            onRefresh = if (connected && !serverName.isNullOrBlank()) {
                { actions.refreshMcpServerTools(serverName) }
            } else null,
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        if (!connected) {
            EmptyState(
                title = "Not connected",
                detail = "Reconnect to load this server's tool list.",
            )
            return@Column
        }
        if (serverName.isNullOrBlank()) {
            EmptyState(
                title = "No server selected",
                detail = "Tap a server from the list to see its tools.",
            )
            return@Column
        }
        if (tools.isEmpty()) {
            EmptyState(
                title = "No tools yet",
                detail = "The server hasn't completed its handshake or hasn't advertised any tools. " +
                    "Try refreshing once it's connected.",
            )
            return@Column
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(tools, key = { "${it.server}:${it.name}" }) { tool -> McpToolRow(tool) }
            item { Spacer(Modifier.height(40.dp)) }
        }
    }
}

@Composable
private fun McpToolRow(tool: McpToolUi) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Build,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(modifier = Modifier.padding(start = 14.dp).weight(1f)) {
            Text(
                tool.name,
                style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (tool.description.isNotBlank()) {
                Text(
                    tool.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "${tool.server}:${tool.name}",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
