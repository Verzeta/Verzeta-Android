// SPDX-FileCopyrightText: 2026 Aditya Mehra <aix.m@outlook.com>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

/**
 * @file SkillsScreen.kt
 * @brief Read-only browser for the host's installed skills via `skill.list`,
 *        showing each skill's id, version, state badge, tags, and description,
 *        with tap-to-navigate into the detail view.
 * @layer UI
 * @dependencies Jetpack Compose, Material3, MainViewModel, MainUiState,
 *               SkillUi, SkillDetailScreen
 */

package com.verzeta.android.ui.skills

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
import androidx.compose.material.icons.filled.AutoFixHigh
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.verzeta.android.MainUiState
import com.verzeta.android.MainViewModel
import com.verzeta.android.data.host.HostStatus
import com.verzeta.android.data.skill.SkillState
import com.verzeta.android.data.skill.SkillUi
import com.verzeta.android.ui.components.EmptyState
import com.verzeta.android.ui.tools.TopBar

/**
 * Read-only browser for `skill.list`. Each row is tappable, navigating to
 * [SkillDetailScreen] which fires `skill.get` for the latest metadata.
 *
 * Approve / block / delete actions live in the desktop's SkillsPage —
 * Android cannot mutate state because the host has no `skill.approve`,
 * `skill.block`, or `skill.delete` op for remote clients.
 */
@Composable
fun SkillsScreen(state: MainUiState, actions: MainViewModel) {
    val connected = state.activeHost?.status == HostStatus.Connected
    LaunchedEffect(connected) { if (connected) actions.refreshSkills() }

    Column(modifier = Modifier.fillMaxSize()) {
        TopBar(
            title = "Skills",
            subtitle = state.skillsStatus.takeIf { it.isNotBlank() },
            onBack = actions::closeSkills,
            onRefresh = if (connected) actions::refreshSkills else null,
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        if (!connected) {
            EmptyState(
                title = "Not connected",
                detail = "Connect a host to see installed skills.",
            )
            return@Column
        }
        if (state.skills.isEmpty()) {
            EmptyState(
                title = "No skills installed",
                detail = "Install skills from the desktop in Settings → Skills. " +
                    "They will appear here automatically.",
            )
            return@Column
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(state.skills, key = { it.id }) { skill ->
                SkillRow(skill = skill, onClick = { actions.openSkillDetail(skill) })
            }
            item { Spacer(Modifier.height(40.dp)) }
        }
    }
}

@Composable
internal fun SkillRow(skill: SkillUi, onClick: () -> Unit) {
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
            Icon(
                Icons.Filled.AutoFixHigh,
                contentDescription = null,
                tint = stateColor(skill.state),
            )
        }
        Column(modifier = Modifier.padding(start = 14.dp).weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    skill.id,
                    style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (skill.version.isNotBlank()) {
                    Spacer(Modifier.size(8.dp))
                    Text(
                        "v${skill.version}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.size(8.dp))
                StateBadge(skill.state)
            }
            if (skill.description.isNotBlank()) {
                Text(
                    skill.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (skill.tags.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    skill.tags.joinToString("  ") { "#$it" },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
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
private fun StateBadge(state: SkillState) {
    val (label, color) = when (state) {
        SkillState.Approved -> "approved" to MaterialTheme.colorScheme.primary
        SkillState.Blocked -> "blocked" to MaterialTheme.colorScheme.error
        SkillState.UnreviewedWithWarnings -> "warnings" to MaterialTheme.colorScheme.tertiary
        SkillState.Unreviewed -> "unreviewed" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

@Composable
internal fun stateColor(state: SkillState) = when (state) {
    SkillState.Approved -> MaterialTheme.colorScheme.primary
    SkillState.Blocked -> MaterialTheme.colorScheme.error
    SkillState.UnreviewedWithWarnings -> MaterialTheme.colorScheme.tertiary
    SkillState.Unreviewed -> MaterialTheme.colorScheme.onSurfaceVariant
}
