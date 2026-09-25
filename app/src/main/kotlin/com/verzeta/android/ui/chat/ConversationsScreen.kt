// SPDX-FileCopyrightText: 2026 Aditya Mehra <aix.m@outlook.com>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

/**
 * @file ConversationsScreen.kt
 * @brief Conversation browser that serves as the Chat-tab landing screen,
 *        organising all conversations into collapsible sections (Pinned,
 *        Projects and Organizations, Group Chats, Direct Agent Chats, Plain
 *        Chats) with inline folder expansion, search filtering, and context
 *        actions such as rename, pin, move, and delete.
 * @layer UI
 * @dependencies Jetpack Compose, MainViewModel, MainUiState, SidebarRow,
 *               categoriseSidebar, FolderUi, AgentSummaryUi, ModelPickerSheet
 */

package com.verzeta.android.ui.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Workspaces
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verzeta.android.MainUiState
import com.verzeta.android.MainViewModel
import com.verzeta.android.data.agent.AgentSummaryUi
import com.verzeta.android.data.chat.ActionKind
import com.verzeta.android.data.chat.ConversationUi
import com.verzeta.android.data.chat.SidebarRow
import com.verzeta.android.data.chat.categoriseSidebar
import com.verzeta.android.data.folder.FolderType
import com.verzeta.android.data.folder.FolderUi
import com.verzeta.android.data.host.HostStatus
import com.verzeta.android.ui.components.EmptyState
import com.verzeta.android.ui.components.ModelPickerSheet
import com.verzeta.android.ui.components.VerzetaTopBar
import com.verzeta.android.util.formatRelativeTimestamp

/**
 * Conversations browser — the Chat-tab landing.
 *
 * Mirrors the desktop sidebar's `SidebarFlatModel` exactly: five top-level
 * sections (Pinned / Projects & Organizations / Standalone Group Chats /
 * Direct Agent Chats / Plain Chats) with nested project bodies — each
 * project / org folder header expands into TEAM MEMBERS rows + "Start Group
 * Chat with All" action + Group Chats subsection + Direct Chats subsection.
 * Tapping a member opens (or reuses) a 1:1 with that agent via
 * `folder.member.chat.open`.
 *
 * Folder editing lives elsewhere (Folders screen + the existing editor
 * sheet). This screen is the BROWSER only.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ConversationsScreen(state: MainUiState, actions: MainViewModel) {
    val connected = state.activeHost?.status == HostStatus.Connected

    var renaming by remember { mutableStateOf<ConversationUi?>(null) }
    var moving by remember { mutableStateOf<ConversationUi?>(null) }
    var modelPickerOpen by remember { mutableStateOf(false) }
    var pickerThenCreate by remember { mutableStateOf(false) }
    var deletingFolder by remember { mutableStateOf<FolderUi?>(null) }
    var deletingConversation by remember { mutableStateOf<ConversationUi?>(null) }
    var createMenuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(connected) {
        if (connected && state.folders.isEmpty()) actions.refreshFolders()
    }

    // Pure-function categoriser. `remember(...)` keys on every input that
    // affects the output — Compose re-evaluates only when one changes.
    val rows = remember(
        state.conversations,
        state.folders,
        state.folderMembers,
        state.deletingConversationIds,
        state.conversationSearch,
        state.collapsedSidebarIds,
    ) {
        categoriseSidebar(
            conversations = state.conversations,
            folders = state.folders,
            folderMembers = state.folderMembers,
            deletingIds = state.deletingConversationIds,
            filterText = state.conversationSearch,
            collapsedSidebarIds = state.collapsedSidebarIds,
        )
    }

    Scaffold(
        floatingActionButton = {
            if (connected) {
                Box {
                    ExtendedFloatingActionButton(
                        onClick = { createMenuOpen = true },
                        icon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                        text = { Text("New") },
                    )
                    DropdownMenu(
                        expanded = createMenuOpen,
                        onDismissRequest = { createMenuOpen = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("New chat") },
                            leadingIcon = {
                                Icon(Icons.Filled.ChatBubble, contentDescription = null)
                            },
                            onClick = {
                                createMenuOpen = false
                                if (state.modelCatalog.hasActiveModel) {
                                    actions.createConversation()
                                } else {
                                    pickerThenCreate = true
                                    modelPickerOpen = true
                                }
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("New group chat") },
                            leadingIcon = {
                                Icon(Icons.Filled.GroupAdd, contentDescription = null)
                            },
                            onClick = {
                                createMenuOpen = false
                                actions.showNewGroup()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("New folder / project") },
                            leadingIcon = {
                                Icon(Icons.Filled.Folder, contentDescription = null)
                            },
                            onClick = {
                                createMenuOpen = false
                                actions.createFolderFlow()
                            },
                        )
                    }
                }
            }
        },
        floatingActionButtonPosition = FabPosition.End,
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            val isMasterDetail = com.verzeta.android.ui.theme.LocalIsMasterDetail.current
            VerzetaTopBar(
                title = "Conversations",
                onBack = if (isMasterDetail) null else actions::showHome,
            )
            Spacer(Modifier.height(8.dp))
            SearchField(
                value = state.conversationSearch,
                onValueChange = actions::setConversationSearch,
            )
            if (!connected) {
                EmptyState(
                    title = "No connected host",
                    detail = "Connect or pair a desktop host before browsing conversations.",
                    actionLabel = "Home",
                    onAction = actions::showHome,
                )
                return@Column
            }
            if (rows.isEmpty()) {
                EmptyState(
                    title = if (state.conversationSearch.isNotBlank()) "No conversations match"
                    else "No conversations loaded",
                    detail = state.chatStatus,
                )
                return@Column
            }

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(rows, key = { it.key }) { row ->
                    when (row) {
                        is SidebarRow.SectionHeader -> SectionHeaderRow(
                            row = row,
                            onToggle = { actions.toggleSidebarCollapsed(row.sectionId) },
                        )
                        is SidebarRow.FolderHeader -> FolderHeaderRow(
                            row = row,
                            onToggle = { actions.toggleSidebarCollapsed(row.folderId) },
                            onDelete = {
                                deletingFolder = state.folders.firstOrNull { it.id == row.folderId }
                            },
                            onEdit = {
                                // The editor sheet lives on the Folders
                                // screen; navigate there and open it.
                                actions.editFolderFlow(row.folderId)
                            },
                        )
                        is SidebarRow.SubsectionHeader -> SubsectionHeaderRow(row = row)
                        is SidebarRow.MemberRow -> MemberRowItem(
                            row = row,
                            agents = state.agents,
                            onClick = {
                                actions.openMemberChat(row.folderId, row.agentId, row.alias)
                            },
                        )
                        is SidebarRow.ActionRow -> ActionRowItem(
                            row = row,
                            onClick = {
                                when (row.kind) {
                                    ActionKind.StartGroupChat -> actions.showNewGroup(row.folderId)
                                }
                            },
                        )
                        is SidebarRow.ConversationItem -> ConversationRowItem(
                            row = row,
                            onClick = { actions.openConversation(row.conversation.id) },
                            onRename = { renaming = row.conversation },
                            onTogglePin = { actions.setConversationPinned(row.conversation.id, !row.conversation.isPinned) },
                            onMove = { moving = row.conversation },
                            onDelete = { deletingConversation = row.conversation },
                        )
                    }
                }
                item { Spacer(Modifier.height(96.dp)) }
            }
        }
    }

    renaming?.let { conv ->
        RenameConversationDialog(
            initial = conv.title,
            onConfirm = { newName ->
                actions.renameConversation(conv.id, newName)
                renaming = null
            },
            onDismiss = { renaming = null },
        )
    }

    moving?.let { conv ->
        MoveConversationSheet(
            conversation = conv,
            folders = state.folders,
            onPick = { folderId ->
                actions.moveConversationToFolder(conv.id, folderId)
                moving = null
            },
            onDismiss = { moving = null },
        )
    }

    deletingConversation?.let { conv ->
        AlertDialog(
            onDismissRequest = { deletingConversation = null },
            title = { Text("Delete conversation?") },
            text = {
                Text("\"${conv.title}\" and all of its messages will be deleted from the desktop. This cannot be undone.")
            },
            confirmButton = {
                TextButton(onClick = {
                    actions.deleteConversation(conv.id)
                    deletingConversation = null
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingConversation = null }) { Text("Cancel") }
            },
        )
    }

    deletingFolder?.let { folder ->
        AlertDialog(
            onDismissRequest = { deletingFolder = null },
            title = { Text("Delete folder?") },
            text = {
                Text(
                    "\"${folder.name}\" will be removed. Conversations inside move to the root and are not deleted.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    actions.deleteFolder(folder.id)
                    deletingFolder = null
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingFolder = null }) { Text("Cancel") }
            },
        )
    }

    if (modelPickerOpen) {
        ModelPickerSheet(
            catalog = state.modelCatalog,
            onPick = { providerId, modelName ->
                if (pickerThenCreate) {
                    actions.setActiveModelAndCreateConversation(providerId, modelName)
                } else {
                    actions.setActiveModel(providerId, modelName)
                }
                pickerThenCreate = false
                modelPickerOpen = false
            },
            onDismiss = {
                pickerThenCreate = false
                modelPickerOpen = false
            },
        )
    }
}

// ---------------------------------------------------------------------
// Row composables, one per SidebarRow variant.
// ---------------------------------------------------------------------

@Composable
private fun SectionHeaderRow(row: SidebarRow.SectionHeader, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (row.collapsed) Icons.Filled.ChevronRight else Icons.Filled.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.size(8.dp))
        Text(
            text = row.label.uppercase(),
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.8.sp,
            ),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = row.count.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FolderHeaderRow(
    row: SidebarRow.FolderHeader,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val icon: ImageVector = when (row.folderType) {
        FolderType.Project -> Icons.Filled.Workspaces
        FolderType.Organization -> Icons.Filled.Apartment
        FolderType.Regular -> Icons.Filled.Folder
    }
    val badgeBg: Color = when (row.folderType) {
        FolderType.Project -> MaterialTheme.colorScheme.tertiaryContainer
        FolderType.Organization -> MaterialTheme.colorScheme.primaryContainer
        FolderType.Regular -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val badgeFg: Color = when (row.folderType) {
        FolderType.Project -> MaterialTheme.colorScheme.onTertiaryContainer
        FolderType.Organization -> MaterialTheme.colorScheme.onPrimaryContainer
        FolderType.Regular -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onToggle,
                    onLongClick = { menuOpen = true },
                )
                .padding(start = 28.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (row.collapsed) Icons.Filled.ChevronRight else Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.size(8.dp))
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(badgeBg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = badgeFg, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.size(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = row.folderName,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = row.folderType.wireValue.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.6.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (row.convCount > 0) {
                Text(
                    text = row.convCount.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
        ) {
            DropdownMenuItem(
                text = { Text("Edit folder") },
                leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                onClick = { menuOpen = false; onEdit() },
            )
            DropdownMenuItem(
                text = { Text("Delete folder", color = MaterialTheme.colorScheme.error) },
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

@Composable
private fun SubsectionHeaderRow(row: SidebarRow.SubsectionHeader) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 56.dp, end = 16.dp, top = 12.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = row.label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.6.sp,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = row.count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

@Composable
private fun MemberRowItem(
    row: SidebarRow.MemberRow,
    agents: List<AgentSummaryUi>,
    onClick: () -> Unit,
) {
    val agent = agents.firstOrNull { it.id == row.agentId }
    val agentName = agent?.name.orEmpty()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 56.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.size(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "@${row.alias}",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (row.isCoordinator) {
                    Spacer(Modifier.size(6.dp))
                    Icon(
                        Icons.Filled.Star,
                        contentDescription = "Coordinator",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
            if (agentName.isNotBlank()) {
                Text(
                    text = agentName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Icon(
            Icons.Filled.ChatBubble,
            contentDescription = "Open 1:1 chat",
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun ActionRowItem(row: SidebarRow.ActionRow, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 56.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.tertiaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.GroupAdd,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.size(10.dp))
        Text(
            text = row.label,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationRowItem(
    row: SidebarRow.ConversationItem,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onTogglePin: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val conv = row.conversation
    val (icon, badgeBg, badgeFg) = when {
        conv.isGroup -> Triple(
            Icons.Filled.Groups,
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer,
        )
        !conv.primaryAgentId.isNullOrBlank() -> Triple(
            Icons.Filled.SmartToy,
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer,
        )
        else -> Triple(
            Icons.Filled.ChatBubble,
            MaterialTheme.colorScheme.surfaceContainerHigh,
            MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    val alpha = if (row.deleting) 0.4f else 1f
    val startPad = when (row.depth) {
        1 -> 16.dp        // root-level bucket rows
        2 -> 32.dp        // child of a regular folder
        3 -> 56.dp        // inside a project subsection
        else -> 16.dp
    }

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { menuOpen = true },
                )
                .padding(start = startPad, end = 16.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(badgeBg.copy(alpha = alpha)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = badgeFg.copy(alpha = alpha))
            }
            Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (conv.isPinned) {
                        Icon(
                            Icons.Filled.PushPin,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
                        )
                        Spacer(Modifier.size(6.dp))
                    }
                    Text(
                        text = row.overrideTitle?.takeIf { it.isNotBlank() } ?: conv.title,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val timeLabel = formatRelativeTimestamp(
                        iso = conv.updatedAt.ifBlank { conv.createdAt },
                    )
                    if (timeLabel.isNotBlank()) {
                        Text(
                            timeLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (row.deleting) {
                    Text(
                        "deleting…",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
        ) {
            DropdownMenuItem(
                text = { Text("Rename") },
                leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                onClick = { menuOpen = false; onRename() },
            )
            DropdownMenuItem(
                text = { Text(if (conv.isPinned) "Unpin" else "Pin") },
                leadingIcon = { Icon(Icons.Filled.PushPin, contentDescription = null) },
                onClick = { menuOpen = false; onTogglePin() },
            )
            DropdownMenuItem(
                text = { Text("Move to folder") },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = null) },
                onClick = { menuOpen = false; onMove() },
            )
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

// ---------------------------------------------------------------------
// Helpers reused inside the screen.
// ---------------------------------------------------------------------

@Composable
private fun SearchField(value: String, onValueChange: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.size(10.dp))
        Box(modifier = Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(
                    "Search conversations",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 16.sp,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun RenameConversationDialog(
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename conversation") },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text("Title") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(value.trim()) },
                enabled = value.trim().isNotEmpty() && value.trim() != initial,
            ) { Text("Rename") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoveConversationSheet(
    conversation: ConversationUi,
    folders: List<FolderUi>,
    onPick: (folderId: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(bottom = 16.dp)) {
            Text(
                "Move \"${conversation.title}\"",
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                style = MaterialTheme.typography.titleLarge,
            )
            FolderPickerRow(
                label = "Root (no folder)",
                description = "Remove from any project / organization.",
                onClick = { onPick("") },
                emphasis = conversation.folderId == null,
            )
            if (folders.isEmpty()) {
                Text(
                    "No folders yet. Create one from Folders.",
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                folders.forEach { folder ->
                    FolderPickerRow(
                        label = folder.name,
                        description = folder.description.ifBlank { folder.folderType.wireValue },
                        onClick = { onPick(folder.id) },
                        emphasis = conversation.folderId == folder.id,
                    )
                }
            }
        }
    }
}

@Composable
private fun FolderPickerRow(
    label: String,
    description: String,
    onClick: () -> Unit,
    emphasis: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (emphasis) Icons.Outlined.PushPin else Icons.Filled.Folder,
            contentDescription = null,
            tint = if (emphasis) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(14.dp))
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
