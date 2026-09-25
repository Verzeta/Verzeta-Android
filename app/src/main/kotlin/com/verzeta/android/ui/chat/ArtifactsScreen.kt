// SPDX-FileCopyrightText: 2026 Aditya Mehra <aix.m@outlook.com>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

/**
 * @file ArtifactsScreen.kt
 * @brief Overlay screen listing all tool-generated files produced in the active
 *        conversation. Each row shows the file name, optional task context pill,
 *        submitter alias, path, and a save-to-device button backed by the
 *        Storage Access Framework.
 * @layer UI
 * @dependencies Jetpack Compose, MainViewModel, MainUiState, ArtifactRowUi,
 *               artifact.list_for_conv wire op, artifact.added wire events
 */

package com.verzeta.android.ui.chat

import android.content.Intent
import android.util.Base64
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.verzeta.android.DownloadKind
import com.verzeta.android.MainUiState
import com.verzeta.android.MainViewModel
import com.verzeta.android.data.chat.ArtifactRowUi
import com.verzeta.android.data.host.HostStatus
import com.verzeta.android.ui.components.EmptyState
import com.verzeta.android.ui.tools.TopBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Artifacts overlay — Android port of the desktop's `artifactsDialog`
 * (ChatPanel.qml:493). Lists every tool-generated file produced in the
 * active conversation. Bound to `state.convArtifacts[selectedConvId]`,
 * populated by `artifact.list_for_conv` on screen open and kept current
 * via `artifact.added` events filtered server-side by msg.subscribe.
 *
 * Per-row visual mirrors the QML 1:1:
 *   - text-x-script-style icon
 *   - bold monospace fileName
 *   - "Task: <planGoal> — <stepTitle>" pill when stepId is set
 *   - "by @alias" dim label when submittedBy is set
 *   - dim path label (eliding) — informational only; downloads route by
 *     `{conv_id, file_name}`
 *   - SaveAlt button → Storage Access Framework picker → write bytes to
 *     the chosen Uri
 *
 * Plan-artifact rows produced by `submit_result` have an empty `path`
 * — their content lives only in the assistant message, not on disk. We
 * still surface the row + Task pill but the download button is hidden.
 */
@Composable
fun ArtifactsScreen(state: MainUiState, actions: MainViewModel) {
    val convId = state.selectedConversationId ?: return
    val connected = state.activeHost?.status == HostStatus.Connected
    val rows = state.convArtifacts[convId].orEmpty()

    LaunchedEffect(convId, connected) {
        if (connected) actions.loadArtifactsForConversation(convId)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopBar(
            title = "Artifacts",
            subtitle = state.selectedConversation?.title?.takeIf { it.isNotBlank() }
                ?: "${rows.size} file${if (rows.size == 1) "" else "s"}",
            onBack = actions::closeArtifacts,
            onRefresh = if (connected) ({ actions.loadArtifactsForConversation(convId) }) else null,
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        if (rows.isEmpty()) {
            EmptyState(
                title = "No artifacts yet",
                detail = "Tool calls that generate files will appear here.",
            )
            return@Column
        }

        LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            items(rows, key = { "${it.sourceMsgId}-${it.fileName}" }) { row ->
                Spacer(Modifier.height(8.dp))
                ArtifactRow(
                    row = row,
                    onSave = { uri, payload ->
                        // Caller-supplied save handler runs on Dispatchers.IO.
                    },
                    convId = convId,
                    actions = actions,
                )
            }
            item { Spacer(Modifier.height(40.dp)) }
        }
    }
}

@Composable
private fun ArtifactRow(
    row: ArtifactRowUi,
    onSave: (uri: android.net.Uri, payload: com.verzeta.android.data.chat.DownloadPayloadUi) -> Unit,
    convId: String,
    actions: MainViewModel,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var statusMessage by remember { mutableStateOf<String?>(null) }

    val canDownload = row.path.isNotBlank() || row.toolName != "submit_result"

    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(
            mimeType = "application/octet-stream",
        ),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            statusMessage = "Downloading…"
            actions.downloadFile(DownloadKind.Artifact, convId, row.fileName)
                .onSuccess { payload ->
                    val ok = withContext(Dispatchers.IO) {
                        runCatching {
                            val bytes = Base64.decode(payload.contentBase64, Base64.NO_WRAP)
                            context.contentResolver.openOutputStream(uri)?.use { out ->
                                out.write(bytes)
                            }
                            true
                        }.getOrDefault(false)
                    }
                    statusMessage = when {
                        !ok -> "Save failed"
                        payload.truncated -> "Saved a truncated copy. The full file is ${payload.totalBytes} bytes."
                        else -> "Saved ${payload.totalBytes} bytes"
                    }
                }
                .onFailure { statusMessage = "Download failed: ${it.message ?: "unknown"}" }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Description,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.size(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    row.fileName.ifBlank { "—" },
                    style = MaterialTheme.typography.titleSmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (row.stepId.isNotBlank()) {
                    Spacer(Modifier.size(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        TaskPill(planGoal = row.planGoal, stepTitle = row.stepTitle)
                        if (row.submittedBy.isNotBlank()) {
                            Text(
                                "by @${row.submittedBy}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (row.path.isNotBlank()) {
                    Text(
                        row.path,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (canDownload) {
                IconButton(
                    onClick = { saveLauncher.launch(row.fileName) },
                ) {
                    Icon(
                        Icons.Filled.SaveAlt,
                        contentDescription = "Save",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        statusMessage?.let { msg ->
            Spacer(Modifier.size(4.dp))
            Text(
                msg,
                style = MaterialTheme.typography.labelSmall,
                color = if (msg.startsWith("Save failed") || msg.startsWith("Download failed"))
                    MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun TaskPill(planGoal: String, stepTitle: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f))
            .border(
                1.dp,
                MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                RoundedCornerShape(999.dp),
            )
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            "Task: ${planGoal.ifBlank { "—" }} · ${stepTitle.ifBlank { "—" }}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
