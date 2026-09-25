// SPDX-FileCopyrightText: 2026 Aditya Mehra <aix.m@outlook.com>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

/**
 * @file FolderEditorSheet.kt
 * @brief Folder create / edit bottom sheet mirroring the desktop's folder
 *        settings dialog: name and type always visible, with goal,
 *        description, members, project documents, preferred skills, and
 *        heartbeat sections for project/organization folders. Documents and
 *        heartbeats save inline; the remaining fields are confirmed back to
 *        the caller in one save action.
 * @layer Frontend
 * @dependencies Folder/agent/skill/heartbeat data models (FolderUi,
 *               AgentSummaryUi, FolderMemberUi, ProjectDocumentUi,
 *               HeartbeatConfigUi, SkillUi), AndroidX Activity result
 *               contracts for document picking, Jetpack Compose Material 3
 *               ModalBottomSheet, kotlinx.coroutines.
 */

package com.verzeta.android.ui.folders

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.verzeta.android.data.agent.AgentSummaryUi
import com.verzeta.android.data.chat.ConversationUi
import com.verzeta.android.data.folder.FolderMemberUi
import com.verzeta.android.data.folder.FolderType
import com.verzeta.android.data.folder.FolderUi
import com.verzeta.android.data.folder.ProjectDocumentUi
import com.verzeta.android.data.heartbeat.HeartbeatConfigUi
import com.verzeta.android.data.skill.SkillUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.util.Base64

/**
 * Folder create / edit bottom sheet. Mirrors the desktop's
 * `FolderSettingsDialog.qml` end-to-end. Sections (per Section 7 of the
 * UI parity prompt):
 *
 *   - **Always visible**: Name, Type
 *   - **Project / Organization only**: Goal, Description, Members,
 *     Project Documents, Preferred Skills, Project Heartbeats
 *
 * Switching to Regular hides the project-only sections and clears the
 * member list on save (host enforces this same behaviour).
 *
 * Save flow (Section 8):
 *   1. `folder.update_metadata` (name + type + goal + description; we
 *      always pass `agent_ids: []` since the wire's legacy slot is
 *      retired in favour of `folder.members.set`).
 *   2. `folder.members.set` — current member list, or empty for regular.
 *   3. `skill.preferred.set` + `skill.set_expose_only_preferred` for
 *      project / org folders.
 *   4. Close → if member list is non-empty, the parent screen opens the
 *      [KickoffSheet] via [PendingKickoff] in MainUiState.
 *
 * Heartbeats and Documents are saved inline (no batched commit) — each
 * upload / heartbeat upsert hits the wire immediately so the UI doesn't
 * have to track dirty state across sections.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderEditorSheet(
    initial: FolderUi?,
    agents: List<AgentSummaryUi>,
    initialMembers: List<FolderMemberUi>,
    initialDocuments: List<ProjectDocumentUi>,
    initialHeartbeats: List<HeartbeatConfigUi>,
    initialPreferredSkillIds: Set<String>,
    initialExposeOnlyPreferred: Boolean,
    allSkills: List<SkillUi>,
    folderConversations: List<ConversationUi>,
    onConfirm: (
        name: String,
        type: FolderType,
        goal: String,
        description: String,
        members: List<FolderMemberUi>,
        preferredSkillIds: List<String>,
        exposeOnlyPreferred: Boolean,
    ) -> Unit,
    onDismiss: () -> Unit,
    onRefreshAgents: () -> Unit,
    onUploadDocument: (fileName: String, contentBase64: String) -> Unit,
    onRemoveDocument: (fileName: String) -> Unit,
    onAddHeartbeat: () -> Unit,
    onEditHeartbeat: (HeartbeatConfigUi) -> Unit,
    onRemoveHeartbeat: (id: String) -> Unit,
    onRunHeartbeat: (id: String) -> Unit,
    onShowHeartbeatActivity: () -> Unit,
    onShowActivityLog: () -> Unit = {},
    onEditPreferredSkills: () -> Unit,
    // Type preselected when creating (ignored when editing): Project
    // Rooms' "Blank project" opens this sheet with Project chosen.
    initialType: FolderType = FolderType.Regular,
    // True while a save is in flight; disables Save so it is not sent twice.
    saving: Boolean = false,
) {
    val isEdit = initial != null
    // Fields are keyed on the folder id, not the whole row: a folder.updated
    // event or a late folder.info reply must not wipe what the user typed.
    // A field the user has not touched still follows the incoming value.
    var name by rememberSyncedDraft(initial?.id, initial?.name.orEmpty())
    var type by rememberSyncedDraft(initial?.id, initial?.folderType ?: initialType)
    var goal by rememberSyncedDraft(initial?.id, initial?.goal.orEmpty())
    var description by rememberSyncedDraft(initial?.id, initial?.description.orEmpty())
    val members: SnapshotStateList<FolderMemberUi> = remember(initial?.id) {
        initialMembers.toMutableStateList()
    }
    // Same rule for the member list, which usually arrives after the sheet
    // opens: adopt the loaded roster unless the user has already edited it.
    var membersBaseline by remember(initial?.id) { mutableStateOf(initialMembers) }
    LaunchedEffect(initialMembers) {
        if (members.toList() == membersBaseline) {
            members.clear()
            members.addAll(initialMembers)
        }
        membersBaseline = initialMembers
    }
    var pickerOpen by remember { mutableStateOf(false) }

    // Track preferred-skills locally; the user opens the sub-sheet to
    // edit, and saves on dismissal of THAT sheet — we just store the
    // result here until folder save fires.
    var preferredSkillIds by remember(initialPreferredSkillIds) {
        mutableStateOf(initialPreferredSkillIds.toList())
    }
    var exposeOnlyPreferred by remember(initialExposeOnlyPreferred) {
        mutableStateOf(initialExposeOnlyPreferred)
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(bottom = 16.dp)) {
            Header(isEdit = isEdit)

            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
            ) {
                FieldLabel("Name")
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("e.g. Q2 campaign planning") },
                )
                Spacer(Modifier.size(16.dp))

                FieldLabel("Type")
                TypePicker(active = type, onSelect = { type = it })
                Spacer(Modifier.size(12.dp))

                if (type == FolderType.Regular) {
                    InfoCard(
                        "A regular folder is a structural container only. Choose Project or " +
                            "Organization to attach a goal, members, documents, preferred skills, " +
                            "and heartbeats.",
                    )
                } else {
                    FieldLabel("Goal (optional)")
                    OutlinedTextField(
                        value = goal,
                        onValueChange = { goal = it },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 4,
                        placeholder = { Text("What is this project trying to achieve?") },
                    )
                    Spacer(Modifier.size(16.dp))

                    FieldLabel("Description (optional)")
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 6,
                        placeholder = { Text("Notes for collaborators and context for the agents.") },
                    )
                    Spacer(Modifier.size(16.dp))

                    MembersSection(
                        members = members,
                        agents = agents,
                        onAddMember = { pickerOpen = true },
                        onRefreshAgents = onRefreshAgents,
                    )

                    if (initial != null) {
                        Spacer(Modifier.size(16.dp))
                        DocumentsSection(
                            documents = initialDocuments,
                            onUpload = onUploadDocument,
                            onRemove = onRemoveDocument,
                        )

                        Spacer(Modifier.size(16.dp))
                        PreferredSkillsSection(
                            allSkills = allSkills,
                            selectedIds = preferredSkillIds,
                            exposeOnly = exposeOnlyPreferred,
                            onEdit = {
                                onEditPreferredSkills()
                            },
                        )

                        Spacer(Modifier.size(16.dp))
                        HeartbeatsSection(
                            heartbeats = initialHeartbeats,
                            agents = agents,
                            onAdd = onAddHeartbeat,
                            onEdit = onEditHeartbeat,
                            onRemove = onRemoveHeartbeat,
                            onRun = onRunHeartbeat,
                            onShowActivity = onShowHeartbeatActivity,
                        )

                        Spacer(Modifier.size(16.dp))
                        androidx.compose.material3.OutlinedButton(
                            onClick = onShowActivityLog,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            androidx.compose.material3.Text("View Activity Log")
                        }
                    } else {
                        Spacer(Modifier.size(8.dp))
                        InfoCard(
                            "Documents, preferred skills, and heartbeats can be configured after " +
                                "the project is saved.",
                        )
                    }
                }

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
                        val finalMembers = if (type == FolderType.Regular) emptyList()
                        else members.toList()
                        val finalSkills = if (type == FolderType.Regular) emptyList()
                        else preferredSkillIds
                        onConfirm(
                            name.trim(), type, goal.trim(), description.trim(),
                            finalMembers, finalSkills,
                            exposeOnlyPreferred && finalSkills.isNotEmpty(),
                        )
                    },
                    enabled = name.trim().isNotEmpty() && !saving,
                ) {
                    Text(if (isEdit) "Save" else "Create")
                }
            }
        }
    }

    if (pickerOpen) {
        AgentPickerSheet(
            agents = agents,
            connected = true,
            onPick = { agent ->
                val base = agent.name.ifBlank { "Member" }
                val alias = uniqueAlias(base, members)
                members.add(
                    FolderMemberUi(
                        agentId = agent.id,
                        alias = alias,
                        isCoordinator = false,
                    ),
                )
                pickerOpen = false
            },
            onDismiss = { pickerOpen = false },
            onRefresh = onRefreshAgents,
        )
    }
}

@Composable
private fun MembersSection(
    members: SnapshotStateList<FolderMemberUi>,
    agents: List<AgentSummaryUi>,
    onAddMember: () -> Unit,
    onRefreshAgents: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        FieldLabel("Members")
        Spacer(Modifier.weight(1f))
        Text(
            "${members.size} member${if (members.size == 1) "" else "s"}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(8.dp))
        IconButton(onClick = onRefreshAgents) {
            Icon(Icons.Filled.Refresh, contentDescription = "Refresh agents")
        }
    }
    Text(
        "Same template can appear multiple times under different aliases. ⭐ marks the coordinator.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp),
    )

    if (members.isEmpty()) {
        InfoCard("No members yet. Tap \"Add member\" to pick an agent.")
    } else {
        members.forEachIndexed { index, member ->
            MemberCard(
                member = member,
                agentName = agents.firstOrNull { it.id == member.agentId }?.name
                    ?: member.agentId.take(8),
                onAliasChange = { newAlias ->
                    val trimmed = newAlias.trim()
                    if (trimmed.isBlank()) return@MemberCard
                    val conflict = members.indices.any { i ->
                        i != index && members[i].alias.equals(trimmed, ignoreCase = true)
                    }
                    if (conflict) return@MemberCard
                    members[index] = member.copy(alias = trimmed)
                },
                onStar = {
                    val wasCoord = member.isCoordinator
                    members.forEachIndexed { i, m ->
                        members[i] = m.copy(isCoordinator = false)
                    }
                    if (!wasCoord) members[index] = members[index].copy(isCoordinator = true)
                },
                onRemove = { members.removeAt(index) },
            )
            Spacer(Modifier.size(6.dp))
        }
    }

    OutlinedButton(
        onClick = onAddMember,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
    ) {
        Icon(Icons.Filled.Add, contentDescription = null)
        Spacer(Modifier.size(6.dp))
        Text("Add member")
    }
}

@Composable
private fun DocumentsSection(
    documents: List<ProjectDocumentUi>,
    onUpload: (fileName: String, contentBase64: String) -> Unit,
    onRemove: (fileName: String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var uploadError by remember { mutableStateOf<String?>(null) }

    val pickFile = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val picked = withContext(Dispatchers.IO) {
                runCatching {
                    val cr = context.contentResolver
                    var name = "upload"
                    cr.query(uri, null, null, null, null)?.use { c ->
                        val nameIdx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (nameIdx >= 0 && c.moveToFirst()) name = c.getString(nameIdx) ?: name
                    }
                    val bytes = cr.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
                    // The document travels as one base64 frame, which must
                    // stay inside the WebSocket's outgoing queue limit.
                    if (bytes.size > com.verzeta.android.remote.RemoteRepository.MAX_UPLOAD_RAW_BYTES) {
                        throw IllegalStateException("File too large. Project documents can be at most 11 MB.")
                    }
                    name to Base64.encodeToString(bytes, Base64.NO_WRAP)
                }
            }
            // Report a read failure or an oversized file here; throwing out
            // of this coroutine would crash the app.
            val (fileName, base64) = picked.getOrElse { e ->
                uploadError = e.message ?: "Could not read the file"
                return@launch
            }
            // Filename hygiene matching host validation at
            // remote-ws-session.cpp:2202-2208.
            val sanitized = fileName.trim()
            if (sanitized.isBlank()
                || sanitized.contains('/') || sanitized.contains('\\')
                || sanitized.contains("..") || sanitized.startsWith('.')
            ) {
                uploadError = "Filename can't contain /, \\, .. or start with ."
                return@launch
            }
            uploadError = null
            onUpload(sanitized, base64)
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        FieldLabel("Project documents")
        Spacer(Modifier.weight(1f))
        Text(
            "${documents.size} file${if (documents.size == 1) "" else "s"}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Text(
        "Maximum file size is 11 MB.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp),
    )

    uploadError?.let { msg ->
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.errorContainer)
                .padding(12.dp),
        ) {
            Text(
                msg,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
        Spacer(Modifier.size(8.dp))
    }

    if (documents.isEmpty()) {
        InfoCard("No documents yet. Tap \"Upload document\" to add one.")
    } else {
        documents.forEach { doc ->
            DocumentRow(doc = doc, onRemove = { onRemove(doc.name) })
            Spacer(Modifier.size(4.dp))
        }
    }

    OutlinedButton(
        onClick = { pickFile.launch("*/*") },
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
    ) {
        Icon(Icons.Filled.Add, contentDescription = null)
        Spacer(Modifier.size(6.dp))
        Text("Upload document")
    }
}

@Composable
private fun DocumentRow(doc: ProjectDocumentUi, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Description,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                doc.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                formatBytes(doc.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onRemove) {
            Icon(
                Icons.Filled.DeleteOutline,
                contentDescription = "Remove document",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PreferredSkillsSection(
    allSkills: List<SkillUi>,
    selectedIds: List<String>,
    exposeOnly: Boolean,
    onEdit: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        FieldLabel("Preferred skills")
        Spacer(Modifier.weight(1f))
        Text(
            "${selectedIds.size} selected",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    val text = when {
        allSkills.isEmpty() -> "No skills installed on the host."
        selectedIds.isEmpty() -> "No preference set; the assistant sees all available skills."
        exposeOnly -> "Only ${selectedIds.size} preferred skill" +
            (if (selectedIds.size == 1) " is" else "s are") +
                " visible. Other skills are hidden from the assistant in this project."
        else -> "${selectedIds.size} preferred skill" + (if (selectedIds.size == 1) "" else "s") +
            ". Other skills remain available."
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(12.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    OutlinedButton(
        onClick = onEdit,
        enabled = allSkills.isNotEmpty(),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
    ) {
        Icon(Icons.Filled.AutoFixHigh, contentDescription = null)
        Spacer(Modifier.size(6.dp))
        Text("Edit preferred skills")
    }
}

@Composable
private fun HeartbeatsSection(
    heartbeats: List<HeartbeatConfigUi>,
    agents: List<AgentSummaryUi>,
    onAdd: () -> Unit,
    onEdit: (HeartbeatConfigUi) -> Unit,
    onRemove: (id: String) -> Unit,
    onRun: (id: String) -> Unit,
    onShowActivity: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        FieldLabel("Project heartbeats")
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onShowActivity) { Text("Activity") }
    }
    Text(
        "Scheduled subagent runs that produce reports. Empty schedule = manual fire only.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp),
    )

    if (heartbeats.isEmpty()) {
        InfoCard("No heartbeats yet. Tap \"Add heartbeat\" to schedule one for a project member.")
    } else {
        heartbeats.forEach { cfg ->
            HeartbeatCard(
                config = cfg,
                agentName = agents.firstOrNull { it.id == cfg.agentId }?.name
                    ?: cfg.agentId.take(8),
                onEdit = { onEdit(cfg) },
                onRemove = { onRemove(cfg.id) },
                onRun = { onRun(cfg.id) },
            )
            Spacer(Modifier.size(6.dp))
        }
    }

    OutlinedButton(
        onClick = onAdd,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
    ) {
        Icon(Icons.Filled.Add, contentDescription = null)
        Spacer(Modifier.size(6.dp))
        Text("Add heartbeat")
    }
}

@Composable
private fun HeartbeatCard(
    config: HeartbeatConfigUi,
    agentName: String,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
    onRun: () -> Unit,
) {
    val borderColor = if (config.enabled) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.outlineVariant
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Bolt,
                    contentDescription = null,
                    tint = if (config.enabled) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.size(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    config.alias.ifBlank { agentName },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "$agentName · ${config.schedule.ifBlank { "manual fire only" }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onRun) {
                Icon(Icons.Filled.PlayArrow, contentDescription = "Run now")
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Filled.Edit, contentDescription = "Edit")
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.DeleteOutline, contentDescription = "Remove")
            }
        }
        if (config.goal.isNotBlank()) {
            Spacer(Modifier.size(4.dp))
            Text(
                config.goal,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (!config.enabled || config.lastFireOutcome.isNotBlank()) {
            Spacer(Modifier.size(4.dp))
            Row {
                if (!config.enabled) {
                    Text(
                        "disabled  ",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (config.lastFireOutcome.isNotBlank()) {
                    Text(
                        "last: ${config.lastFireOutcome}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun MemberCard(
    member: FolderMemberUi,
    agentName: String,
    onAliasChange: (String) -> Unit,
    onStar: () -> Unit,
    onRemove: () -> Unit,
) {
    val borderColor = if (member.isCoordinator) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.outlineVariant
    val borderWidth = if (member.isCoordinator) 2.dp else 1.dp

    Column(
        modifier = Modifier
            .fillMaxWidth()
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
                    agentName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (member.isCoordinator) "Coordinator" else "Member",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (member.isCoordinator) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onStar) {
                Icon(
                    if (member.isCoordinator) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = if (member.isCoordinator) "Unmark coordinator"
                    else "Mark as coordinator",
                    tint = if (member.isCoordinator) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Filled.DeleteOutline,
                    contentDescription = "Remove member",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.size(10.dp))
        AliasField(value = member.alias, onValueChange = onAliasChange)
    }
}

@Composable
private fun AliasField(value: String, onValueChange: (String) -> Unit) {
    var local by remember(value) { mutableStateOf(value) }
    OutlinedTextField(
        value = local,
        onValueChange = { local = it },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
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
    connected: Boolean,
    onPick: (AgentSummaryUi) -> Unit,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
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
                        "Pick an agent template. The same agent can be added more than once.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onRefresh, enabled = connected) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh agents")
                }
            }

            if (agents.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 24.dp)) {
                    Text(
                        "No agents yet. Create one in Verzeta Studio on the desktop and tap Refresh.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 480.dp)) {
                    items(agents, key = { it.id }) { agent ->
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
private fun Header(isEdit: Boolean) {
    Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
        Text(
            text = if (isEdit) "Edit folder" else "New folder",
            style = MaterialTheme.typography.titleLarge,
        )
        if (isEdit) {
            Text(
                "Type, goal, description, members, documents, preferred skills, and heartbeats are " +
                    "all editable. Switching to Regular hides the project sections and clears " +
                    "the member list on save.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TypePicker(active: FolderType, onSelect: (FolderType) -> Unit) {
    val options = listOf(
        TypeOption(
            FolderType.Regular, "Regular", Icons.Filled.Folder,
            "A structural container with no agents, goal, or description.",
        ),
        TypeOption(
            FolderType.Project, "Project", Icons.Filled.Workspaces,
            "Pin a goal, members, documents, preferred skills, and heartbeats.",
        ),
        TypeOption(
            FolderType.Organization, "Organization", Icons.Filled.Apartment,
            "A top-level organization with the same options as a project.",
        ),
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { opt ->
            TypeOptionCard(opt = opt, active = opt.type == active, onClick = { onSelect(opt.type) })
        }
    }
}

@Composable
private fun TypeOptionCard(opt: TypeOption, active: Boolean, onClick: () -> Unit) {
    val borderColor = if (active) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.outlineVariant
    val borderWidth = if (active) 2.dp else 1.dp
    val bg = if (active) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.18f)
    else MaterialTheme.colorScheme.surfaceContainer

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(borderWidth, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    if (active) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                opt.icon,
                contentDescription = null,
                tint = if (active) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                opt.label,
                style = MaterialTheme.typography.titleSmall,
                color = if (active) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                opt.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (active) {
            Spacer(Modifier.size(8.dp))
            Icon(Icons.Filled.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

private data class TypeOption(
    val type: FolderType,
    val label: String,
    val icon: ImageVector,
    val description: String,
)

@Composable
private fun InfoCard(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(14.dp),
    ) {
        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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

private fun uniqueAlias(base: String, members: List<FolderMemberUi>): String {
    val taken = members.map { it.alias.lowercase() }.toSet()
    if (base.lowercase() !in taken) return base
    var i = 2
    while ("${base.lowercase()} $i" in taken) i++
    return "$base $i"
}

private fun formatBytes(size: Long): String {
    if (size < 1024) return "$size B"
    if (size < 1024 * 1024) return "${size / 1024} KB"
    return "${size / (1024 * 1024)} MB"
}

/**
 * Editable copy of [incoming] that survives updates to it.
 *
 * The draft resets when [key] changes (another folder). When [incoming]
 * changes for the same key, the draft follows it only if the user has not
 * edited the draft since the last incoming value, so an update from the
 * host fills untouched fields without discarding typed input.
 */
@Composable
private fun <T> rememberSyncedDraft(key: Any?, incoming: T): MutableState<T> {
    val draft = remember(key) { mutableStateOf(incoming) }
    val baseline = remember(key) { mutableStateOf(incoming) }
    LaunchedEffect(key, incoming) {
        if (draft.value == baseline.value) draft.value = incoming
        baseline.value = incoming
    }
    return draft
}
