// SPDX-FileCopyrightText: 2026 Aditya Mehra <aix.m@outlook.com>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

/**
 * @file NewGroupScreen.kt
 * @brief Wizard screen for creating a new group conversation, supporting both
 *        standalone and project-scoped creation flows with agent member
 *        selection, alias assignment, and coordinator designation.
 * @layer UI
 * @dependencies Jetpack Compose, MainViewModel, MainUiState, AgentSummaryUi,
 *               FolderUi, GroupMemberInput
 */

package com.verzeta.android.ui.group

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Workspaces
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.verzeta.android.MainUiState
import com.verzeta.android.MainViewModel
import com.verzeta.android.NewGroupContext
import com.verzeta.android.data.agent.AgentSummaryUi
import com.verzeta.android.data.folder.FolderUi
import com.verzeta.android.data.folder.GroupMemberInput
import com.verzeta.android.data.host.HostStatus
import com.verzeta.android.ui.components.SectionHeader

/**
 * Screen for creating a new group conversation with support for both
 * standalone and project-scoped flows.
 *
 * In the standalone flow the user picks an optional folder from the full
 * folder list; in the project-scoped flow the folder is pre-determined and
 * the member picker defaults to agents already in the project roster
 * (toggle "Project members only" off to add agents outside the roster —
 * those are enrolled via `folder.member.add` after `group.create`).
 *
 * Preloaded members in project-scoped mode use a checkbox soft-exclude
 * rather than a hard-delete icon: unchecking retains the alias and
 * coordinator state in case the user re-enables the member before
 * submitting. Net-new members use a standard remove icon.
 *
 * The Create button is enabled only when the title is non-empty, at least
 * two members are enabled, every enabled member has a non-empty unique
 * alias, and the form is not busy. If no enabled member is marked as
 * coordinator, the first one is automatically promoted before the
 * `group.create` request is dispatched.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewGroupScreen(state: MainUiState, actions: MainViewModel) {
    val connected = state.activeHost?.status == HostStatus.Connected
    val ctx = state.newGroupContext
    val scoped = ctx.isProjectScoped

    LaunchedEffect(connected) { if (connected && state.agents.isEmpty()) actions.loadAgents() }

    var title by remember { mutableStateOf("") }
    // Folder picker only matters in un-scoped mode. In scoped mode the
    // dialog already knows its folderId.
    var selectedFolderId by remember { mutableStateOf<String?>(null) }
    val members: SnapshotStateList<MemberDraft> = remember(ctx.folderId, ctx.preloadedMembers) {
        ctx.preloadedMembers
            .map { m ->
                val agentName = state.agents.firstOrNull { it.id == m.agentId }?.name.orEmpty()
                MemberDraft(
                    agentId = m.agentId,
                    agentName = agentName,
                    alias = m.alias,
                    isCoordinator = m.isCoordinator,
                    wasPreloaded = true,
                    enabled = true,
                )
            }
            .toMutableStateList()
    }
    var pickerOpen by remember { mutableStateOf(false) }
    var projectMembersOnly by remember(scoped) { mutableStateOf(scoped) }

    val enabledCount = members.count { it.enabled }
    val canSubmit = !state.busy &&
        title.trim().isNotEmpty() &&
        enabledCount >= 2 &&
        members.all { !it.enabled || it.alias.isNotBlank() }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(if (scoped) "Group chat in project" else "New group") },
            navigationIcon = {
                IconButton(onClick = actions::closeNewGroup) {
                    Icon(Icons.Filled.Close, contentDescription = "Cancel")
                }
            },
            actions = {
                if (connected) {
                    IconButton(onClick = actions::loadAgents, enabled = !state.busy) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh agents")
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .weight(1f),
        ) {
            if (scoped) {
                ScopeBanner(folderName = ctx.folderName)
                Spacer(Modifier.size(16.dp))
            }

            FieldLabel("Title")
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("e.g. Q2 campaign planning") },
                enabled = !state.busy,
            )

            if (!scoped) {
                Spacer(Modifier.size(20.dp))
                FieldLabel("Folder (optional)")
                FolderPicker(
                    folders = state.folders,
                    selectedFolderId = selectedFolderId,
                    onPick = { selectedFolderId = it },
                )
            }

            Spacer(Modifier.size(20.dp))
            SectionHeader("Members")
            Text(
                text = "Add at least two members. The same agent template can appear multiple " +
                    "times under different aliases. ⭐ marks the coordinator, who " +
                    "answers when no @alias is mentioned.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 12.dp),
            )

            if (members.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .padding(16.dp),
                ) {
                    Text(
                        "No members yet. Tap \"Add member\" to pick an agent.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                members.forEachIndexed { index, draft ->
                    MemberCard(
                        draft = draft,
                        softDelete = scoped && draft.wasPreloaded,
                        onAliasChange = { newAlias ->
                            val trimmed = newAlias.trim()
                            if (trimmed.isBlank()) return@MemberCard
                            // Alias-uniqueness includes disabled rows so a
                            // soft-deleted member's alias slot stays reserved.
                            val conflict = members.indices.any { i ->
                                i != index && members[i].alias.equals(trimmed, ignoreCase = true)
                            }
                            if (conflict) return@MemberCard
                            members[index] = draft.copy(alias = trimmed)
                        },
                        onStar = {
                            val wasCoord = draft.isCoordinator
                            members.forEachIndexed { i, m ->
                                members[i] = m.copy(isCoordinator = false)
                            }
                            if (!wasCoord) members[index] = members[index].copy(isCoordinator = true)
                        },
                        onRemove = { members.removeAt(index) },
                        onToggleInclude = {
                            members[index] = draft.copy(enabled = !draft.enabled)
                        },
                    )
                }
            }

            OutlinedButton(
                onClick = { pickerOpen = true },
                enabled = !state.busy && (connected || state.agents.isNotEmpty()),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.size(6.dp))
                Text("Add member")
            }

            if (state.status.isNotBlank()) {
                Text(
                    state.status,
                    modifier = Modifier.padding(top = 16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = actions::closeNewGroup) { Text("Cancel") }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = {
                    val effective = members.filter { it.enabled }
                    val withCoord = ensureCoordinator(effective)
                    val payload = withCoord.map {
                        GroupMemberInput(
                            agentId = it.agentId,
                            alias = it.alias.trim(),
                            isCoordinator = it.isCoordinator,
                        )
                    }
                    val targetFolderId = if (scoped) ctx.folderId else selectedFolderId
                    actions.createGroup(
                        title = title.trim(),
                        members = payload,
                        folderId = targetFolderId,
                    )
                },
                enabled = canSubmit,
            ) {
                Icon(Icons.Filled.Check, contentDescription = null)
                Spacer(Modifier.size(6.dp))
                Text("Create")
            }
        }
    }

    if (pickerOpen) {
        AgentPickerSheet(
            agents = state.agents,
            agentsStatus = state.agentsStatus,
            connected = connected,
            scoped = scoped,
            allowedAgentIds = ctx.allowedAgentIds,
            projectMembersOnly = projectMembersOnly,
            onProjectMembersOnlyChanged = { projectMembersOnly = it },
            onPick = { agent ->
                val base = agent.name.ifBlank { "Member" }
                val alias = uniqueAlias(base, members)
                members.add(
                    MemberDraft(
                        agentId = agent.id,
                        agentName = agent.name,
                        alias = alias,
                        isCoordinator = false,
                        wasPreloaded = false,
                        enabled = true,
                    ),
                )
                pickerOpen = false
            },
            onDismiss = { pickerOpen = false },
            onRefresh = actions::loadAgents,
        )
    }
}

@Composable
private fun ScopeBanner(folderName: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.18f))
            .border(
                1.dp,
                MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Workspaces,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.size(10.dp))
        Text(
            "Scoped to " + (if (folderName.isNotBlank()) "project “$folderName”" else "this project") +
                ". The picker shows project members by default. Turn off \"Project members only\" " +
                "to add new agents. They join the project roster when you tap Create.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun MemberCard(
    draft: MemberDraft,
    softDelete: Boolean,
    onAliasChange: (String) -> Unit,
    onStar: () -> Unit,
    onRemove: () -> Unit,
    onToggleInclude: () -> Unit,
) {
    val borderColor = if (draft.isCoordinator && draft.enabled) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.outlineVariant
    val borderWidth = if (draft.isCoordinator && draft.enabled) 2.dp else 1.dp
    val rowAlpha = if (draft.enabled) 1f else 0.45f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(borderWidth, borderColor, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.SmartToy,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    draft.agentName.ifBlank { "Unknown agent" },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = rowAlpha),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val role = when {
                    !draft.enabled -> "Excluded"
                    draft.isCoordinator -> "Coordinator"
                    else -> "Member"
                }
                Text(
                    role,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (draft.isCoordinator && draft.enabled) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onStar, enabled = draft.enabled) {
                Icon(
                    if (draft.isCoordinator) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = if (draft.isCoordinator) "Unmark coordinator"
                    else "Mark as coordinator",
                    tint = if (draft.isCoordinator) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (softDelete) {
                IconButton(onClick = onToggleInclude) {
                    Icon(
                        if (draft.enabled) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank,
                        contentDescription = if (draft.enabled) "Exclude from chat"
                        else "Include in chat",
                        tint = if (draft.enabled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                IconButton(onClick = onRemove) {
                    Icon(
                        Icons.Filled.DeleteOutline,
                        contentDescription = "Remove member",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(Modifier.size(10.dp))
        AliasField(value = draft.alias, enabled = draft.enabled, onValueChange = onAliasChange)
    }
}

@Composable
private fun AliasField(value: String, enabled: Boolean, onValueChange: (String) -> Unit) {
    var local by remember(value) { mutableStateOf(value) }
    OutlinedTextField(
        value = local,
        onValueChange = { local = it },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        enabled = enabled,
        textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
        label = { Text("@alias (must be unique)") },
        supportingText = {
            Text(
                "@-mention this alias in chat to address the member directly.",
                style = MaterialTheme.typography.bodySmall,
            )
        },
    )
    if (local != value) {
        LaunchedEffect(local) { onValueChange(local) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AgentPickerSheet(
    agents: List<AgentSummaryUi>,
    agentsStatus: String,
    connected: Boolean,
    scoped: Boolean,
    allowedAgentIds: Set<String>,
    projectMembersOnly: Boolean,
    onProjectMembersOnlyChanged: (Boolean) -> Unit,
    onPick: (AgentSummaryUi) -> Unit,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val visible = if (scoped && projectMembersOnly && allowedAgentIds.isNotEmpty()) {
        agents.filter { it.id in allowedAgentIds }
    } else agents

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(bottom = 16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Add member", style = MaterialTheme.typography.titleLarge)
                    Text(
                        if (scoped)
                            "Pick from the project's roster, or toggle off to add a new agent."
                        else "Pick an agent template. The same agent can be added more than once.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onRefresh, enabled = connected) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh agents")
                }
            }

            if (scoped) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Project members only", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "When off, picked agents are added to the project's roster on Create.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = projectMembersOnly, onCheckedChange = onProjectMembersOnlyChanged)
                }
            }

            if (visible.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 24.dp)) {
                    Text(
                        if (!connected) "Connect a host to load the agent registry."
                        else if (agents.isEmpty()) agentsStatus.ifBlank {
                            "No agents yet. Create one in Verzeta Studio on the desktop and tap Refresh."
                        } else "No agents in this project's roster. Toggle \"Project members only\" off to add a new agent.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 480.dp)) {
                    items(visible, key = { it.id }) { agent ->
                        AgentRow(agent = agent, onClick = { onPick(agent) })
                    }
                }
            }
        }
    }
}

@Composable
private fun AgentRow(agent: AgentSummaryUi, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.SmartToy,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    agent.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (agent.isBuiltin) {
                    Spacer(Modifier.size(8.dp))
                    Text(
                        "built-in",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (agent.isCoordinator) {
                    Spacer(Modifier.size(6.dp))
                    Icon(
                        Icons.Filled.Star,
                        contentDescription = "Default coordinator",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
            if (agent.description.isNotBlank()) {
                Text(
                    agent.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun FolderPicker(folders: List<FolderUi>, selectedFolderId: String?, onPick: (String?) -> Unit) {
    Column {
        FolderOption(
            label = "No folder",
            description = "Standalone group chat",
            selected = selectedFolderId == null,
            onClick = { onPick(null) },
        )
        folders.forEach { folder ->
            FolderOption(
                label = folder.name,
                description = folder.description.ifBlank { folder.folderType.wireValue },
                selected = selectedFolderId == folder.id,
                onClick = { onPick(folder.id) },
            )
        }
    }
}

@Composable
private fun FolderOption(label: String, description: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (selected) Icons.Filled.Check else Icons.Filled.Folder,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (description.isNotBlank()) {
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text,
        modifier = Modifier.padding(bottom = 6.dp),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private data class MemberDraft(
    val agentId: String = "",
    val agentName: String = "",
    val alias: String = "",
    val isCoordinator: Boolean = false,
    /** True for rows pre-populated from the project roster. */
    val wasPreloaded: Boolean = false,
    /** Soft-delete flag: when false the row is excluded from the
     *  final group payload. Only meaningful in scoped mode for
     *  preloaded rows; net-new rows toggle their existence via
     *  list add/remove instead. */
    val enabled: Boolean = true,
)

private fun uniqueAlias(base: String, members: List<MemberDraft>): String {
    val taken = members.map { it.alias.lowercase() }.toSet()
    if (base.lowercase() !in taken) return base
    var i = 2
    while ("${base.lowercase()} $i" in taken) i++
    return "$base $i"
}

private fun ensureCoordinator(members: List<MemberDraft>): List<MemberDraft> {
    if (members.isEmpty()) return members
    if (members.any { it.isCoordinator }) return members
    return members.mapIndexed { i, m -> m.copy(isCoordinator = i == 0) }
}
