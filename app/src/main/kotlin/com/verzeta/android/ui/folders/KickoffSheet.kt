// SPDX-FileCopyrightText: 2026 Aditya Mehra <aix.m@outlook.com>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

/**
 * @file KickoffSheet.kt
 * @brief Post-save kickoff bottom sheet shown after a folder with members is
 *        saved. Offers two options: create a 1:1 conversation with each
 *        member and/or start a group chat with all members (group hidden
 *        for single-member projects).
 * @layer Frontend
 * @dependencies PendingKickoff (MainViewModel state), Jetpack Compose
 *               Material 3 ModalBottomSheet.
 */

package com.verzeta.android.ui.folders

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.unit.dp
import com.verzeta.android.PendingKickoff

/**
 * Post-save kickoff sheet. Mirrors `kickoffDialog` in
 * FolderSettingsDialog.qml:837-925 — two checkboxes (1:1 with each
 * member, group with all). The group option is hidden when memberCount
 * < 2 because `folder.kickoff.group` rejects with `server_error` for
 * single-member projects.
 *
 * `folder.kickoff.individual` is idempotent on the host, so re-running
 * after the user adds a member later only creates the missing chats.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KickoffSheet(
    info: PendingKickoff,
    onStart: (createIndividual: Boolean, createGroup: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var individual by remember { mutableStateOf(true) }
    var group by remember { mutableStateOf(info.memberCount >= 2) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(bottom = 16.dp)) {
            Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                Text("Start chatting with your team?", style = MaterialTheme.typography.titleLarge)
                Text(
                    "You saved \"${info.folderName}\" with ${info.memberCount} member" +
                        (if (info.memberCount == 1) "" else "s") +
                            ". Start some conversations now?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                CheckRow(
                    checked = individual,
                    onCheckedChange = { individual = it },
                    title = "Create a 1:1 conversation with each member",
                    detail = "One separate chat per member. Existing 1:1 chats are reused, not duplicated.",
                    enabled = true,
                )
                Spacer(Modifier.size(4.dp))
                CheckRow(
                    checked = group,
                    onCheckedChange = { group = it },
                    title = "Start a group chat with all members",
                    detail = if (info.memberCount >= 2)
                        "One team chat with everyone. @-mention an alias to address them specifically."
                    else "Group chats need at least 2 members.",
                    enabled = info.memberCount >= 2,
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) { Text("Not now") }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = { onStart(individual, group) },
                    enabled = individual || group,
                ) {
                    Text("Start")
                }
            }
        }
    }
}

@Composable
private fun CheckRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    title: String,
    detail: String,
    enabled: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .background(
                if (checked && enabled) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.18f)
                else MaterialTheme.colorScheme.surfaceContainer,
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Checkbox(
            checked = checked && enabled,
            onCheckedChange = { onCheckedChange(it) },
            enabled = enabled,
        )
        Spacer(Modifier.size(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.size(4.dp))
        Box(
            modifier = Modifier.size(0.dp),
        )
    }
}
