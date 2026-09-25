// SPDX-FileCopyrightText: 2026 Aditya Mehra <aix.m@outlook.com>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

/**
 * @file ActivityTimelineScreen.kt
 * @brief Read-only chronological view of the conversation and project activity
 *        log. Displays actor-kind pill, alias, timestamp, event type, tool name,
 *        and expandable event detail for each logged entry.
 * @layer UI
 * @dependencies Jetpack Compose, MainViewModel, MainUiState, ActivityEventUi
 */

package com.verzeta.android.ui.activity

import androidx.compose.foundation.background
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
import androidx.compose.material3.HorizontalDivider
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
import com.verzeta.android.data.activity.ActivityEventUi
import com.verzeta.android.ui.components.EmptyState
import com.verzeta.android.ui.tools.TopBar
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Read-only activity-log timeline screen. Mirrors the desktop's
 * ActivityTimeline.qml at the data and interaction level. Each row shows an
 * actor-kind pill, alias, and timestamp on top; event-type label and tool name
 * in the middle; and event summary at the bottom.
 *
 * Mobile-responsive Material 3 layout:
 *   - LazyColumn for built-in scrolling sized to the viewport.
 *   - No fixed-width columns — Row + weight(1f) + Column layout.
 *   - Long content wraps (eventSummary maxLines=4 with ellipsis;
 *     eventDetail shown full-text in monospace).
 *   - Full-row tap targets for accessibility.
 */
@Composable
fun ActivityTimelineScreen(state: MainUiState, actions: MainViewModel) {
    LaunchedEffect(state.activityScopeId) {
        if (state.activityScopeId.isNotEmpty()) actions.loadActivity()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopBar(
            title = "Activity Log",
            subtitle = when (state.activityScopeKind) {
                "project" -> "Project: ${state.activityScopeLabel}"
                "conversation" -> "Conversation: ${state.activityScopeLabel}"
                else -> ""
            },
            onBack = actions::closeActivityTimeline,
            onRefresh = actions::loadActivity,
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        if (state.activityLog.isEmpty()) {
            EmptyState(
                title = "No activity yet",
                detail = "Agent turns, tool invocations, polls, member changes, and " +
                    "file / canvas writes will appear here as they happen.",
            )
            return@Column
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(state.activityLog, key = { it.id }) { row ->
                ActivityRow(row)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            item { Spacer(Modifier.height(40.dp)) }
        }
    }
}

@Composable
private fun ActivityRow(row: ActivityEventUi) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        // Top row: actor-kind pill + @alias + timestamp.
        Row(verticalAlignment = Alignment.CenterVertically) {
            ActorKindPill(row.actorKind)
            Spacer(Modifier.size(8.dp))
            Text(
                row.actorAlias.ifBlank { "—" }
                    // 'system' has no @-prefixed alias; the alias is the
                    // human-readable subsystem name (sweeper, scheduler).
                    // Every other kind uses @-prefixed monospace alias.
                    .let { if (row.actorKind == "system") it else "@${it.replace(' ', '_')}" },
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                ),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                formatTimestamp(row.createdAt),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.size(4.dp))

        // Middle row: event-type label + tool-name (if any).
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                eventTypeLabel(row.eventType),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (row.toolName.isNotBlank()) {
                ToolNamePill(row.toolName)
            }
        }

        Spacer(Modifier.size(4.dp))

        // Bottom: summary — wraps on narrow viewports.
        Text(
            row.eventSummary.ifBlank { "—" },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )

        // Expandable detail (event_detail map) — always shown when non-empty.
        // Signal-driven expand/collapse is deferred to a future iteration.
        if (row.eventDetail.isNotEmpty()) {
            Spacer(Modifier.size(4.dp))
            Text(
                row.eventDetail.entries.joinToString("\n") { (k, v) -> "$k: ${v ?: "—"}" },
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ActorKindPill(kind: String) {
    val color = when (kind.lowercase()) {
        "user" -> MaterialTheme.colorScheme.tertiary
        "agent" -> MaterialTheme.colorScheme.primary
        "system" -> MaterialTheme.colorScheme.secondary
        // Paired-client actions use inversePrimary so the pill is visually
        // distinct from the three host-side categories while staying within
        // the Material 3 palette.
        "client" -> MaterialTheme.colorScheme.inversePrimary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            kind.ifBlank { "—" },
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}

@Composable
private fun ToolNamePill(toolName: String) {
    val color = MaterialTheme.colorScheme.secondary
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            toolName,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
            ),
            color = color,
        )
    }
}

private fun eventTypeLabel(eventType: String): String = when (eventType) {
    "agent_turn" -> "Agent turn"
    "image_generated" -> "Image generated"
    "tool_invoked" -> "Tool invoked"
    "poll_created" -> "Poll created"
    "poll_vote" -> "Poll vote"
    "poll_closed" -> "Poll closed"
    "member_added" -> "Member added"
    "member_removed" -> "Member removed"
    "canvas_edited" -> "Canvas edited"
    "file_written" -> "File written"
    "permission_denied" -> "Permission denied"
    "deliverable_produced" -> "Deliverable"
    // Workspace-mount lifecycle events.
    "workspace.mount.registered" -> "Workspace mount registered"
    "workspace.mount.unregistered" -> "Workspace mount unregistered"
    "workspace.mount.replaced" -> "Workspace mount replaced"
    "workspace.mount.tree_updated" -> "Workspace tree refreshed"
    "workspace.mount.tier_changed" -> "Workspace tier changed"
    "workspace.mount.stale" -> "Workspace mount stale"
    else -> eventType
}

private fun formatTimestamp(iso: String): String {
    if (iso.isBlank()) return "—"
    return runCatching {
        // Host emits ISODateWithMs UTC (e.g. "2026-05-16T14:32:01.234Z").
        OffsetDateTime.parse(iso)
            .atZoneSameInstant(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("MMM d HH:mm"))
    }.getOrDefault(iso)
}
