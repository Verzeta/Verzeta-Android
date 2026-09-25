// SPDX-FileCopyrightText: 2026 Aditya Mehra <aix.m@outlook.com>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

/**
 * @file HeartbeatActivityScreen.kt
 * @brief Read-only heartbeat diagnostics screen showing three sections:
 *        recent heartbeat runs, the upcoming-fires preview, and recent
 *        config changes. Mirrors the desktop's heartbeat activity overlay;
 *        actions (run-now / edit) live in the heartbeat editor instead.
 * @layer Frontend
 * @dependencies MainViewModel / MainUiState, heartbeat data models
 *               (HeartbeatRunUi, HeartbeatNextFireUi,
 *               HeartbeatConfigChangeUi), EmptyState / SectionHeader /
 *               TopBar components, java.time formatting.
 */

package com.verzeta.android.ui.folders

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
import com.verzeta.android.data.heartbeat.HeartbeatConfigChangeUi
import com.verzeta.android.data.heartbeat.HeartbeatNextFireUi
import com.verzeta.android.data.heartbeat.HeartbeatRunUi
import com.verzeta.android.ui.components.EmptyState
import com.verzeta.android.ui.components.SectionHeader
import com.verzeta.android.ui.tools.TopBar
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Heartbeat activity overlay. Three sections:
 *
 *   - Recent runs (`heartbeat.recent_runs`)
 *   - Next fires preview (`heartbeat.next_fires_preview`)
 *   - Recent config changes (`heartbeat.recent_config_changes`)
 *
 * Mirrors the desktop's HeartbeatActivityOverlay. Diagnostics-only;
 * no actions live here (run-now / cancel are in the editor).
 */
@Composable
fun HeartbeatActivityScreen(state: MainUiState, actions: MainViewModel) {
    LaunchedEffect(Unit) { actions.loadHeartbeatActivity() }

    Column(modifier = Modifier.fillMaxSize()) {
        TopBar(
            title = "Heartbeat activity",
            subtitle = "Recent runs · upcoming fires · audit log",
            onBack = actions::closeHeartbeatActivity,
            onRefresh = actions::loadHeartbeatActivity,
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        if (state.recentHeartbeatRuns.isEmpty() &&
            state.heartbeatNextFires.isEmpty() &&
            state.recentHeartbeatChanges.isEmpty()
        ) {
            EmptyState(
                title = "No heartbeat activity",
                detail = "Configure a heartbeat in a project's settings and tap Run now to see " +
                    "data here.",
            )
            return@Column
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            if (state.heartbeatNextFires.isNotEmpty()) {
                item { SectionHeader("Next fires (${state.heartbeatNextFires.size})") }
                items(state.heartbeatNextFires, key = { "f-${it.configId}" }) { fire ->
                    NextFireRow(fire)
                }
            }
            if (state.recentHeartbeatRuns.isNotEmpty()) {
                item { SectionHeader("Recent runs (${state.recentHeartbeatRuns.size})") }
                items(state.recentHeartbeatRuns, key = { "r-${it.id}" }) { run ->
                    RunRow(run)
                }
            }
            if (state.recentHeartbeatChanges.isNotEmpty()) {
                item { SectionHeader("Audit log (${state.recentHeartbeatChanges.size})") }
                items(state.recentHeartbeatChanges, key = { "c-${it.id}" }) { change ->
                    ChangeRow(change)
                }
            }
            item { Spacer(Modifier.height(40.dp)) }
        }
    }
}

@Composable
private fun NextFireRow(fire: HeartbeatNextFireUi) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                fire.alias.ifBlank { "—" },
                style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                fire.schedule.ifBlank { "manual fire only" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            formatTimestamp(fire.nextFireMs),
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun RunRow(run: HeartbeatRunUi) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                run.title.ifBlank { "Untitled run" },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${run.alias} · ${run.agentName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.size(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            OutcomePill(run.outcome)
            Text(
                formatTimestamp(run.startedAtMs),
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ChangeRow(change: HeartbeatConfigChangeUi) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp)) {
        Row {
            Text(
                "${change.alias.ifBlank { "—" }} · ${change.field}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                change.source,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            "${change.oldValue.ifBlank { "—" }}  →  ${change.newValue.ifBlank { "—" }}",
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            formatTimestamp(change.changedAtMs),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun OutcomePill(outcome: String) {
    val color = when (outcome.lowercase()) {
        "success", "ok" -> MaterialTheme.colorScheme.primary
        "error", "failed" -> MaterialTheme.colorScheme.error
        "skipped" -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            outcome.ifBlank { "—" },
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}

private fun formatTimestamp(ms: Long): String {
    if (ms <= 0L) return "—"
    return runCatching {
        Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("MMM d HH:mm"))
    }.getOrDefault("—")
}
