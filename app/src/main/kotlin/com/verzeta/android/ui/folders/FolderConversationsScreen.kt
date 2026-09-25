// SPDX-FileCopyrightText: 2026 Aditya Mehra <aix.m@outlook.com>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

/**
 * @file FolderConversationsScreen.kt
 * @brief Screen listing the conversations inside the currently selected
 *        folder, with a "start group chat" entry for project/organization
 *        folders. Rows open the conversation; the list stays fresh via the
 *        host's conversation events.
 * @layer Frontend
 * @dependencies MainViewModel / MainUiState, ConversationUi (data/chat),
 *               EmptyState component, formatRelativeTimestamp utility,
 *               Jetpack Compose Material 3.
 */

package com.verzeta.android.ui.folders

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verzeta.android.MainUiState
import com.verzeta.android.MainViewModel
import com.verzeta.android.data.chat.ConversationUi
import com.verzeta.android.ui.components.EmptyState
import com.verzeta.android.util.formatRelativeTimestamp

/**
 * Conversations belonging to the [MainUiState.selectedFolder]. Reads from
 * `state.folderConversations` (already filtered from `state.conversations`
 * by `folder_id`); the source of truth is `state.conversations`, kept
 * fresh by the host's `conv.added` / `conv.updated` events. Tap a row to
 * open it via [com.verzeta.android.MainViewModel.openConversation].
 */
@Composable
fun FolderConversationsScreen(state: MainUiState, actions: MainViewModel) {
    val folder = state.selectedFolder
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = actions::closeFolder) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to folders")
            }
            Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
                Text(
                    folder?.name.orEmpty().ifBlank { "Folder" },
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (folder != null) {
                    Text(
                        folder.folderType.wireValue.uppercase(),
                        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // Project / Org folders get a "Start group chat" entry that opens
            // the NewGroupScreen scoped to this folder. Regular folders don't —
            // they have no member roster to scope by.
            if (folder != null && folder.folderType != com.verzeta.android.data.folder.FolderType.Regular) {
                TextButton(onClick = { actions.showNewGroup(folder.id) }) {
                    Icon(Icons.Filled.GroupAdd, contentDescription = null)
                    Spacer(Modifier.size(4.dp))
                    Text("Group chat")
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        if (folder == null) {
            EmptyState(
                title = "Folder not found",
                detail = "The folder may have been deleted.",
                actionLabel = "Folders",
                onAction = actions::closeFolder,
            )
            return@Column
        }
        if (folder.description.isNotBlank() || folder.goal.isNotBlank()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(14.dp),
            ) {
                if (folder.description.isNotBlank()) {
                    Text(folder.description, style = MaterialTheme.typography.bodyMedium)
                }
                if (folder.goal.isNotBlank()) {
                    Text(
                        "Goal: ${folder.goal}",
                        modifier = Modifier.padding(top = 4.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        val rows = state.folderConversations
        if (rows.isEmpty()) {
            EmptyState(
                title = "No conversations in this folder",
                detail = "Move a conversation here from the Conversations screen.",
            )
            return@Column
        }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(rows, key = { it.id }) { conversation ->
                ConversationRow(
                    conversation = conversation,
                    onClick = { actions.openConversation(conversation.id) },
                )
            }
            item { Spacer(Modifier.height(40.dp)) }
        }
    }
}

@Composable
private fun ConversationRow(conversation: ConversationUi, onClick: () -> Unit) {
    val (icon, badgeBg, badgeFg) = when {
        conversation.isGroup -> Triple(
            Icons.Filled.Groups,
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer,
        )
        !conversation.primaryAgentId.isNullOrBlank() -> Triple(
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
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
                Text(
                    conversation.title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val time = formatRelativeTimestamp(conversation.updatedAt.ifBlank { conversation.createdAt })
                if (time.isNotBlank()) {
                    Text(time, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
