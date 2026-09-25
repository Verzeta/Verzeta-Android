// SPDX-FileCopyrightText: 2026 Aditya Mehra <aix.m@outlook.com>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

/**
 * @file HeartbeatEditorSheet.kt
 * @brief Modal bottom sheet for creating or editing one project heartbeat
 *        configuration: member (agent + alias) selection, schedule, goal,
 *        surface criteria, daily run cap, optional auto-surface target
 *        conversation, and the self-config permission toggle. Mirrors the
 *        host's heartbeat validation rules client-side for instant feedback.
 * @layer Frontend
 * @dependencies HeartbeatConfigUi / HeartbeatScope (data/heartbeat),
 *               FolderMemberUi, AgentSummaryUi, ConversationUi, Jetpack
 *               Compose Material 3 ModalBottomSheet.
 */

package com.verzeta.android.ui.folders

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.verzeta.android.data.agent.AgentSummaryUi
import com.verzeta.android.data.chat.ConversationUi
import com.verzeta.android.data.folder.FolderMemberUi
import com.verzeta.android.data.heartbeat.HeartbeatConfigUi
import com.verzeta.android.data.heartbeat.HeartbeatScope

/**
 * Editor for one heartbeat config. Wire op: `heartbeat.upsert` (create or
 * update). Validation enforced at the wire boundary
 * (remote-ws-session.cpp:2742-2803):
 *
 *   - schedule formats: `@hourly`, `@daily at HH:MM`, `@weekly`,
 *     `@weekly on DAY at HH:MM`, `@interval N` (N ≥ 5). Empty schedule
 *     means "manual fire only" — host accepts it. We surface the format
 *     hint to give instant feedback before save.
 *   - max_runs_per_day ∈ [1, 1440].
 *   - alias non-empty bounded.
 *   - auto_surface_target_conversation_id may be empty (overlay-only)
 *     or a valid conv UUID.
 *
 * The agent + alias come from the project's member list (we can't
 * pre-populate from the agent registry alone — the alias is per-member).
 * Choosing a member fixes both. The conversation target is optional and
 * picked from the conversation list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeartbeatEditorSheet(
    initial: HeartbeatConfigUi?,
    folderId: String,
    members: List<FolderMemberUi>,
    agents: List<AgentSummaryUi>,
    candidateTargets: List<ConversationUi>,
    onConfirm: (HeartbeatConfigUi) -> Unit,
    /** True while a save is in flight; disables Save so it is not sent twice. */
    saving: Boolean = false,
    onDismiss: () -> Unit,
) {
    var agentId by remember(initial) { mutableStateOf(initial?.agentId.orEmpty()) }
    var alias by remember(initial) { mutableStateOf(initial?.alias.orEmpty()) }
    var enabled by remember(initial) { mutableStateOf(initial?.enabled ?: true) }
    var schedule by remember(initial) { mutableStateOf(initial?.schedule.orEmpty()) }
    var goal by remember(initial) { mutableStateOf(initial?.goal.orEmpty()) }
    var surfaceCriteria by remember(initial) { mutableStateOf(initial?.surfaceCriteria.orEmpty()) }
    var maxRunsPerDay by remember(initial) {
        mutableStateOf((initial?.maxRunsPerDay ?: 24).toString())
    }
    var targetConvId by remember(initial) {
        mutableStateOf(initial?.autoSurfaceTargetConversationId.orEmpty())
    }
    var selfConfigAllowed by remember(initial) { mutableStateOf(initial?.selfConfigAllowed ?: false) }

    val maxRunsInt = maxRunsPerDay.toIntOrNull() ?: 0
    val canSave = agentId.isNotBlank() && alias.isNotBlank() && maxRunsInt in 1..1440

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(bottom = 16.dp)) {
            Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                Text(
                    if (initial == null) "New heartbeat" else "Edit heartbeat",
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    "Scheduled subagent run for one project member.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
            ) {
                Label("Member")
                MemberPicker(
                    members = members,
                    agents = agents,
                    selectedAgentId = agentId,
                    selectedAlias = alias,
                    onPick = { m ->
                        agentId = m.agentId
                        alias = m.alias
                    },
                )

                Spacer(Modifier.size(16.dp))
                Label("Goal")
                OutlinedTextField(
                    value = goal,
                    onValueChange = { goal = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4,
                    placeholder = { Text("What should the subagent do every fire?") },
                )

                Spacer(Modifier.size(16.dp))
                Label("Schedule")
                OutlinedTextField(
                    value = schedule,
                    onValueChange = { schedule = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("@daily at 09:00") },
                    supportingText = {
                        Text(
                            "Formats: @hourly · @daily at HH:MM · @weekly · " +
                                "@weekly on Mon at HH:MM · @interval N (N ≥ 5). " +
                                "Empty = manual fire only.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                )

                Spacer(Modifier.size(16.dp))
                Label("Max runs per day")
                OutlinedTextField(
                    value = maxRunsPerDay,
                    onValueChange = { v -> maxRunsPerDay = v.filter(Char::isDigit).take(4) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = maxRunsInt !in 1..1440 && maxRunsPerDay.isNotEmpty(),
                    supportingText = { Text("Cap: 1 to 1440 (per host validation).") },
                )

                Spacer(Modifier.size(16.dp))
                Label("Surface criteria (optional)")
                OutlinedTextField(
                    value = surfaceCriteria,
                    onValueChange = { surfaceCriteria = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 1,
                    maxLines = 3,
                    placeholder = { Text("e.g. only post when there's a real anomaly") },
                )

                Spacer(Modifier.size(16.dp))
                Label("Auto-surface target (optional)")
                ConversationPicker(
                    conversations = candidateTargets,
                    selectedId = targetConvId,
                    onPick = { id -> targetConvId = id.orEmpty() },
                )

                Spacer(Modifier.size(16.dp))
                ToggleRow(
                    title = "Enabled",
                    detail = "Disabled configs don't auto-fire. Manual run is still allowed.",
                    checked = enabled,
                    onCheckedChange = { enabled = it },
                )
                ToggleRow(
                    title = "Allow self-config",
                    detail = "⚠ The agent can change its own goal, schedule (within cap), surface " +
                        "criteria, and enable/disable itself. Schedule format and max-runs/day cap " +
                        "still enforced. All changes audited.",
                    checked = selfConfigAllowed,
                    onCheckedChange = { selfConfigAllowed = it },
                )

                Spacer(Modifier.size(20.dp))
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = {
                        onConfirm(
                            HeartbeatConfigUi(
                                id = initial?.id.orEmpty(),
                                agentId = agentId,
                                scopeType = HeartbeatScope.Folder,
                                scopeId = folderId,
                                alias = alias,
                                enabled = enabled,
                                schedule = schedule,
                                goal = goal,
                                surfaceCriteria = surfaceCriteria,
                                maxRunsPerDay = maxRunsInt,
                                autoSurfaceTargetConversationId = targetConvId,
                                selfConfigAllowed = selfConfigAllowed,
                                lastFireAt = initial?.lastFireAt.orEmpty(),
                                lastFireOutcome = initial?.lastFireOutcome.orEmpty(),
                            ),
                        )
                    },
                    enabled = canSave && !saving,
                ) { Text(if (initial == null) "Create" else "Save") }
            }
        }
    }
}

@Composable
private fun MemberPicker(
    members: List<FolderMemberUi>,
    agents: List<AgentSummaryUi>,
    selectedAgentId: String,
    selectedAlias: String,
    onPick: (FolderMemberUi) -> Unit,
) {
    if (members.isEmpty()) {
        Text(
            "Add at least one member to the project first.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 220.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        items(members, key = { "${it.agentId}-${it.alias}" }) { m ->
            val agentName = agents.firstOrNull { it.id == m.agentId }?.name ?: m.agentId.take(8)
            val isPicked = m.agentId == selectedAgentId && m.alias == selectedAlias
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPick(m) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(
                            if (isPicked) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceContainerHighest,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isPicked) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
                Spacer(Modifier.size(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        m.alias,
                        style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                    )
                    Text(
                        agentName + if (m.isCoordinator) "  ⭐" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ConversationPicker(
    conversations: List<ConversationUi>,
    selectedId: String,
    onPick: (String?) -> Unit,
) {
    Column {
        OptionRow(
            label = "Overlay only (no target conversation)",
            selected = selectedId.isBlank(),
            onClick = { onPick(null) },
        )
        if (conversations.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 220.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer),
            ) {
                items(conversations, key = { it.id }) { c ->
                    OptionRow(
                        label = c.title.ifBlank { "Untitled" },
                        selected = c.id == selectedId,
                        onClick = { onPick(c.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun OptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(
                    if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceContainerHighest,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
        Spacer(Modifier.size(10.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ToggleRow(title: String, detail: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text,
        modifier = Modifier.padding(bottom = 6.dp),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
