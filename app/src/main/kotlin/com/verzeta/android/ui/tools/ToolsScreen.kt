// SPDX-FileCopyrightText: 2026 Aditya Mehra <aix.m@outlook.com>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

package com.verzeta.android.ui.tools

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.verzeta.android.MainUiState
import com.verzeta.android.MainViewModel
import com.verzeta.android.data.host.HostStatus
import com.verzeta.android.data.tool.ToolKind
import com.verzeta.android.data.tool.ToolUi
import com.verzeta.android.ui.components.EmptyState
import com.verzeta.android.ui.components.SectionHeader

/**
 * Read-only browser for the host's full tool catalog (`tool.list`). Mirrors
 * the desktop's ToolsPage tabs (built-in / custom / MCP) by grouping rows
 * under section headers, but lays out as a single LazyColumn so the user
 * can scroll all three groups in one gesture — phone-native.
 *
 * Disabled tools are dimmed and tagged "off"; there's no toggle because
 * the host has no remote-client `tool.set_enabled` op. Mutations are
 * desktop-only for v1.
 */
@Composable
fun ToolsScreen(state: MainUiState, actions: MainViewModel) {
    val connected = state.activeHost?.status == HostStatus.Connected
    LaunchedEffect(connected) { if (connected) actions.refreshTools() }

    Column(modifier = Modifier.fillMaxSize()) {
        TopBar(
            title = "Tools",
            subtitle = state.toolsStatus.takeIf { it.isNotBlank() },
            onBack = actions::closeTools,
            onRefresh = if (connected) actions::refreshTools else null,
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        if (!connected) {
            EmptyState(
                title = "Not connected",
                detail = "Connect a host to see its registered tools.",
            )
            return@Column
        }

        if (state.tools.isEmpty()) {
            EmptyState(
                title = "No tools registered",
                detail = "The host hasn't registered any tools. Check that Verzeta Studio " +
                    "started correctly on the desktop.",
            )
            return@Column
        }

        val groups = listOf(
            Triple("Built-in", ToolKind.BuiltIn, state.tools.filter { it.kind == ToolKind.BuiltIn }),
            Triple("Custom", ToolKind.Custom, state.tools.filter { it.kind == ToolKind.Custom }),
            Triple("MCP", ToolKind.Mcp, state.tools.filter { it.kind == ToolKind.Mcp }),
        )

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            groups.forEach { (label, _, tools) ->
                if (tools.isEmpty()) return@forEach
                item(key = "h-$label") { SectionHeader("$label (${tools.size})") }
                items(tools, key = { "${it.kind.wireValue}-${it.name}" }) { tool ->
                    ToolRow(tool)
                }
            }
            item { Spacer(Modifier.height(40.dp)) }
        }
    }
}

@Composable
private fun ToolRow(tool: ToolUi) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val nameColor = if (tool.enabled) onSurface else onSurfaceVariant

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
            Icon(Icons.Filled.Build, contentDescription = null, tint = onSurfaceVariant)
        }
        Column(modifier = Modifier.padding(start = 14.dp).weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = tool.shortName ?: tool.name,
                    style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                    color = nameColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (tool.kind == ToolKind.Mcp && !tool.mcpServer.isNullOrBlank()) {
                    Spacer(Modifier.size(8.dp))
                    KindBadge("MCP · ${tool.mcpServer}")
                }
                if (!tool.enabled) {
                    Spacer(Modifier.size(8.dp))
                    Text(
                        "off",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            if (tool.description.isNotBlank()) {
                Text(
                    tool.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = onSurfaceVariant,
                )
            }
            if (tool.parameters.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    tool.parameters.joinToString(separator = "  ") { p ->
                        val mark = if (p.required) "*" else ""
                        "${p.name}$mark: ${p.type}"
                    },
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun KindBadge(label: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
        )
    }
}

@Composable
internal fun TopBar(
    title: String,
    subtitle: String?,
    onBack: () -> Unit,
    onRefresh: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (subtitle.isNullOrBlank()) 56.dp else 64.dp)
            .padding(start = 4.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (onRefresh != null) {
            IconButton(onClick = onRefresh) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
            }
        }
    }
}
