// SPDX-FileCopyrightText: 2026 Aditya Mehra <aix.m@outlook.com>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

/**
 * @file FoldersScreen.kt
 * @brief Folder management screen — lists the connected host's project and
 *        organization folders, or every folder including regular ones when
 *        "All folders" is chosen, and hosts the full management flow: create
 *        and edit via FolderEditorSheet, quick rename, delete with
 *        confirmation, convert-to-project, plus the stacked heartbeat,
 *        preferred-skills, and post-save kickoff sub-sheets.
 * @layer Frontend
 * @dependencies MainViewModel / MainUiState, FolderEditorSheet,
 *               HeartbeatEditorSheet, PreferredSkillsSheet, KickoffSheet,
 *               folder/heartbeat/skill data models, EmptyState component,
 *               Jetpack Compose Material 3.
 */

package com.verzeta.android.ui.folders

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Workspaces
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verzeta.android.MainUiState
import com.verzeta.android.MainViewModel
import com.verzeta.android.data.chat.ConversationUi
import com.verzeta.android.data.folder.FolderMemberUi
import com.verzeta.android.data.folder.FolderType
import com.verzeta.android.data.folder.FolderUi
import com.verzeta.android.data.heartbeat.HeartbeatConfigUi
import com.verzeta.android.data.host.HostStatus
import com.verzeta.android.ui.components.EmptyState
import com.verzeta.android.ui.theme.adaptiveContentMaxWidth

/**
 * Folders sub-screen — Android peer to the host's folder management UI.
 *
 *   - List loads via `folder.list_projects` (the "Projects + Orgs" chip,
 *     default) or `folder.list_all` (the "All folders" chip), which also
 *     lists regular folders so they can be opened, edited, converted to a
 *     project or deleted.
 *   - Create / Edit open [FolderEditorSheet] with the full host metadata
 *     surface (name, type, goal, description, agents). The same sheet
 *     handles both flows; pre-fill is from `state.folders[id]`, and
 *     [MainViewModel.loadFolderInfo] runs in parallel when the cached
 *     row is missing metadata.
 *   - Rename uses a quick AlertDialog (the editor would also work but
 *     rename is the most common single-field action).
 *   - Delete prompts for confirmation explaining the host-side cascade
 *     (conversations inside move to root).
 *
 * Long-press on a regular folder gets an extra "Convert to project"
 * action.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FoldersScreen(state: MainUiState, actions: MainViewModel) {
    val connected = state.activeHost?.status == HostStatus.Connected
    var showCreateOrEdit by remember { mutableStateOf<FolderUi?>(null) }
    var creatingNew by remember { mutableStateOf(false) }
    var createAsProject by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<FolderUi?>(null) }
    var deleting by remember { mutableStateOf<FolderUi?>(null) }
    // True while a create or save from an editor sheet is in flight. The
    // sheet closes only when the host accepted it; on failure it stays open
    // with the user's input and the error is shown in the snackbar.
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(connected) {
        if (connected && state.agents.isEmpty()) actions.loadAgents()
        if (connected) actions.refreshFolders()
    }

    LaunchedEffect(state.pendingFolderCreate) {
        if (state.pendingFolderCreate) {
            createAsProject = state.pendingFolderCreateAsProject
            creatingNew = true
            showCreateOrEdit = null
            actions.consumePendingFolderCreate()
        }
    }

    // "Edit folder" in the Conversations tab navigates here with a one-shot
    // request to open that folder's edit sheet.
    LaunchedEffect(state.pendingFolderEditId, state.folders) {
        val id = state.pendingFolderEditId ?: return@LaunchedEffect
        val folder = state.folders.firstOrNull { it.id == id } ?: return@LaunchedEffect
        creatingNew = false
        showCreateOrEdit = folder
        actions.consumePendingFolderEdit()
    }

    Scaffold(
        floatingActionButton = {
            if (connected) {
                ExtendedFloatingActionButton(
                    onClick = {
                        creatingNew = true
                        showCreateOrEdit = null
                    },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("New folder") },
                )
            }
        },
        floatingActionButtonPosition = FabPosition.End,
        containerColor = MaterialTheme.colorScheme.surface,
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TopBar(
                onBack = actions::showConversations,
                onRefresh = actions::refreshFolders,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            if (!connected) {
                EmptyState(
                    title = "No connected host",
                    detail = "Connect or pair a desktop host to manage folders.",
                    actionLabel = "Home",
                    onAction = actions::showHome,
                )
                return@Column
            }
            // Regular folders are only listed with "All folders".
            ShowAllToggle(
                showAll = state.foldersShowAll,
                onToggle = actions::setFoldersShowAll,
            )
            if (state.folders.isEmpty()) {
                EmptyState(
                    title = "No folders yet",
                    detail = state.foldersStatus.ifBlank { "Tap + to create a project or organization folder." },
                    actionLabel = "New folder",
                    onAction = { creatingNew = true },
                )
                return@Column
            }
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopCenter,
            ) {
            LazyColumn(modifier = Modifier.fillMaxSize().then(adaptiveContentMaxWidth())) {
                items(state.folders, key = { it.id }) { folder ->
                    FolderRow(
                        folder = folder,
                        onOpen = { actions.openFolder(folder.id) },
                        onEdit = {
                            actions.loadFolderInfo(folder.id)
                            showCreateOrEdit = folder
                        },
                        onRename = { renaming = folder },
                        onDelete = { deleting = folder },
                        onConvertToProject = { actions.convertFolderToProject(folder.id) },
                    )
                }
                item { Spacer(Modifier.height(96.dp)) }
            }  // LazyColumn
            }  
        }
    }

    // Sub-sheet state owned at the FoldersScreen level so it survives
    // re-composition of the parent FolderEditorSheet without remounting it.
    var heartbeatEditing by remember { mutableStateOf<HeartbeatEditorContext?>(null) }
    var preferredSkillsEditing by remember { mutableStateOf<String?>(null) }

    if (creatingNew) {
        FolderEditorSheet(
            initial = null,
            initialType = if (createAsProject) FolderType.Project else FolderType.Regular,
            agents = state.agents,
            initialMembers = emptyList(),
            initialDocuments = emptyList(),
            initialHeartbeats = emptyList(),
            initialPreferredSkillIds = emptySet(),
            initialExposeOnlyPreferred = false,
            allSkills = state.skills,
            folderConversations = emptyList(),
            onConfirm = { name, type, goal, description, members, prefIds, exposeOnly ->
                saving = true
                actions.createFolderWithMembers(
                    name = name, type = type, goal = goal, description = description,
                    members = members,
                    preferredSkillIds = prefIds,
                    exposeOnlyPreferred = exposeOnly,
                ) { created ->
                    saving = false
                    if (created) {
                        creatingNew = false
                        createAsProject = false
                    }
                }
            },
            saving = saving,
            onDismiss = { creatingNew = false; createAsProject = false },
            onRefreshAgents = actions::loadAgents,
            onUploadDocument = { _, _ -> /* unreachable in create mode */ },
            onRemoveDocument = { /* unreachable */ },
            onAddHeartbeat = { /* unreachable in create mode */ },
            onEditHeartbeat = { /* unreachable */ },
            onRemoveHeartbeat = { /* unreachable */ },
            onRunHeartbeat = { /* unreachable */ },
            onShowHeartbeatActivity = { /* unreachable */ },
            onEditPreferredSkills = { /* unreachable in create mode */ },
        )
    }

    showCreateOrEdit?.let { folder ->
        val live = state.folders.firstOrNull { it.id == folder.id } ?: folder
        // Pre-load every section's state from the host. Each call is cheap;
        // events keep them current after save.
        LaunchedEffect(live.id) {
            actions.loadFolderMembers(live.id)
            actions.loadFolderDocuments(live.id)
            actions.loadFolderHeartbeats(live.id)
            actions.loadFolderPreferredSkills(live.id)
            // skills list is for the picker; refresh in case of new installs
            actions.refreshSkills()
        }
        val initialMembers = state.folderMembers[live.id].orEmpty()
        val initialDocuments = state.folderDocuments[live.id].orEmpty()
        val initialHeartbeats = state.folderHeartbeats[live.id].orEmpty()
        val pref = state.folderPreferredSkills[live.id]
        val initialPreferredIds = pref?.skillIds.orEmpty().toSet()
        val initialExposeOnly = pref?.exposeOnly ?: false
        val folderConvs = state.conversations.filter { it.folderId == live.id }

        FolderEditorSheet(
            initial = live,
            agents = state.agents,
            initialMembers = initialMembers,
            initialDocuments = initialDocuments,
            initialHeartbeats = initialHeartbeats,
            initialPreferredSkillIds = initialPreferredIds,
            initialExposeOnlyPreferred = initialExposeOnly,
            allSkills = state.skills,
            folderConversations = folderConvs,
            onConfirm = { name, type, goal, description, members, prefIds, exposeOnly ->
                saving = true
                actions.saveFolderEdit(
                    id = live.id,
                    name = name,
                    currentName = live.name,
                    type = type,
                    goal = goal,
                    description = description,
                    members = members,
                    preferredSkillIds = prefIds,
                    exposeOnlyPreferred = exposeOnly,
                ) { saved ->
                    saving = false
                    if (saved) showCreateOrEdit = null
                }
            },
            saving = saving,
            onDismiss = { showCreateOrEdit = null },
            onRefreshAgents = actions::loadAgents,
            onUploadDocument = { fileName, b64 ->
                actions.uploadFolderDocument(live.id, fileName, b64)
            },
            onRemoveDocument = { fileName -> actions.removeFolderDocument(live.id, fileName) },
            onAddHeartbeat = {
                heartbeatEditing = HeartbeatEditorContext(
                    folderId = live.id,
                    config = null,
                    members = initialMembers,
                    convs = folderConvs,
                )
            },
            onEditHeartbeat = { cfg ->
                heartbeatEditing = HeartbeatEditorContext(
                    folderId = live.id,
                    config = cfg,
                    members = initialMembers,
                    convs = folderConvs,
                )
            },
            onRemoveHeartbeat = { id -> actions.removeHeartbeatConfig(id) },
            onRunHeartbeat = { id -> actions.runHeartbeatNow(id) },
            onShowHeartbeatActivity = { actions.showHeartbeatActivity() },
            onShowActivityLog = {
                actions.showActivityForProject(live.id, live.name)
            },
            onEditPreferredSkills = { preferredSkillsEditing = live.id },
        )
    }

    // Heartbeat editor — second-level modal stacked above the folder editor.
    heartbeatEditing?.let { ctx ->
        HeartbeatEditorSheet(
            initial = ctx.config,
            folderId = ctx.folderId,
            members = ctx.members,
            agents = state.agents,
            candidateTargets = ctx.convs,
            onConfirm = { cfg ->
                saving = true
                // Close only when the host saved it; on failure keep the form.
                actions.upsertHeartbeatConfig(cfg) { id ->
                    saving = false
                    if (id != null) heartbeatEditing = null
                }
            },
            saving = saving,
            onDismiss = { heartbeatEditing = null },
        )
    }

    // Preferred skills editor — second-level modal stacked above the folder editor.
    preferredSkillsEditing?.let { folderId ->
        val pref = state.folderPreferredSkills[folderId]
        PreferredSkillsSheet(
            allSkills = state.skills,
            initiallySelected = pref?.skillIds.orEmpty().toSet(),
            initialExposeOnly = pref?.exposeOnly ?: false,
            onConfirm = { selected, exposeOnly ->
                // Save back into the per-folder cache so the FolderEditorSheet
                // shows it immediately. The wire write happens at folder save
                // (saveFolderEdit). Same model as the desktop dialog.
                actions.applyLocalPreferredSkills(folderId, selected, exposeOnly)
                preferredSkillsEditing = null
            },
            onDismiss = { preferredSkillsEditing = null },
        )
    }

    // Post-save kickoff sheet — opens when state.pendingKickoff is set.
    state.pendingKickoff?.let { info ->
        KickoffSheet(
            info = info,
            onStart = { individual, group ->
                if (individual) actions.runKickoffIndividual(info.folderId)
                if (group) actions.runKickoffGroup(info.folderId, openOnReady = false)
                actions.dismissPendingKickoff()
            },
            onDismiss = { actions.dismissPendingKickoff() },
        )
    }

    renaming?.let { folder ->
        RenameDialog(
            initial = folder.name,
            onConfirm = { newName ->
                actions.renameFolder(folder.id, newName)
                renaming = null
            },
            onDismiss = { renaming = null },
        )
    }

    deleting?.let { folder ->
        DeleteConfirmDialog(
            folder = folder,
            onConfirm = {
                actions.deleteFolder(folder.id)
                deleting = null
            },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun TopBar(onBack: () -> Unit, onRefresh: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
        Text(
            "Folders",
            modifier = Modifier.weight(1f).padding(start = 4.dp),
            style = MaterialTheme.typography.titleLarge,
        )
        IconButton(onClick = onRefresh) {
            Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
        }
    }
}

@Composable
private fun ShowAllToggle(showAll: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(
            selected = !showAll,
            onClick = { onToggle(false) },
            label = { Text("Projects + Orgs") },
        )
        Spacer(Modifier.size(8.dp))
        FilterChip(
            selected = showAll,
            onClick = { onToggle(true) },
            label = { Text("All folders") },
            leadingIcon = {
                if (showAll) Icon(Icons.Filled.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp))
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderRow(
    folder: FolderUi,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onConvertToProject: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val (icon, badgeBg, badgeFg) = folderIconForType(folder.folderType)
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onOpen, onLongClick = { menuOpen = true })
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(badgeBg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = badgeFg)
            }
            Column(modifier = Modifier.padding(start = 14.dp).weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(folder.name, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.size(8.dp))
                    Text(
                        folder.folderType.wireValue.uppercase(),
                        style = MaterialTheme.typography.labelMedium.copy(
                            letterSpacing = 1.sp,
                            fontFamily = FontFamily.Monospace,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (folder.agentIds.isNotEmpty()) {
                        Spacer(Modifier.size(8.dp))
                        Text(
                            "${folder.agentIds.size} agent${if (folder.agentIds.size == 1) "" else "s"}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (folder.description.isNotBlank()) {
                    Text(
                        folder.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (folder.goal.isNotBlank()) {
                    Text(
                        "Goal: ${folder.goal}",
                        modifier = Modifier.padding(top = 2.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text("Edit") },
                leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                onClick = { menuOpen = false; onEdit() },
            )
            DropdownMenuItem(
                text = { Text("Rename only") },
                leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                onClick = { menuOpen = false; onRename() },
            )
            if (folder.folderType == FolderType.Regular) {
                DropdownMenuItem(
                    text = { Text("Convert to project") },
                    leadingIcon = { Icon(Icons.Filled.Workspaces, contentDescription = null) },
                    onClick = { menuOpen = false; onConvertToProject() },
                )
            }
            DropdownMenuItem(
                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                leadingIcon = {
                    Icon(
                        Icons.Filled.DeleteOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                },
                onClick = { menuOpen = false; onDelete() },
            )
        }
    }
}

private data class FolderIcon(val icon: ImageVector, val badgeBg: androidx.compose.ui.graphics.Color, val badgeFg: androidx.compose.ui.graphics.Color)

@Composable
private fun folderIconForType(type: FolderType): Triple<ImageVector, androidx.compose.ui.graphics.Color, androidx.compose.ui.graphics.Color> = when (type) {
    FolderType.Project -> Triple(
        Icons.Filled.Workspaces,
        MaterialTheme.colorScheme.tertiaryContainer,
        MaterialTheme.colorScheme.onTertiaryContainer,
    )
    FolderType.Organization -> Triple(
        Icons.Filled.Apartment,
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.onPrimaryContainer,
    )
    FolderType.Regular -> Triple(
        Icons.Filled.Folder,
        MaterialTheme.colorScheme.surfaceContainerHigh,
        MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun RenameDialog(
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename folder") },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text("Name") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(value.trim()) },
                enabled = value.trim().isNotEmpty() && value.trim() != initial,
            ) { Text("Rename") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun DeleteConfirmDialog(folder: FolderUi, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete folder?") },
        text = {
            Column {
                Text(
                    "Delete \"${folder.name}\"? Conversations inside the folder will move to root " +
                        "automatically — they will not be deleted.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.size(8.dp))
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = { Text(folder.folderType.wireValue) },
                    colors = AssistChipDefaults.assistChipColors(
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) { Text("Delete") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * Context bundle passed to [HeartbeatEditorSheet] when the user taps add /
 * edit. Captured at the moment the sub-sheet opens so re-composition of
 * the parent FolderEditorSheet doesn't replace the picker's data.
 */
private data class HeartbeatEditorContext(
    val folderId: String,
    val config: HeartbeatConfigUi?,
    val members: List<FolderMemberUi>,
    val convs: List<ConversationUi>,
)
