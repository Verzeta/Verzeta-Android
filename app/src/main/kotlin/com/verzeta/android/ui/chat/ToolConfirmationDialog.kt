// SPDX-FileCopyrightText: 2026 Aditya Mehra <aix.m@outlook.com>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

/**
 * @file ToolConfirmationDialog.kt
 * @brief Modal dialog displayed when the host emits a `tool_call.requested`
 *        event, prompting the user to approve or deny the tool execution
 *        before the assistant proceeds.
 * @layer UI
 * @dependencies Jetpack Compose, PendingToolConfirmationUi, kotlinx.serialization
 */

package com.verzeta.android.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.verzeta.android.data.agent.PendingToolConfirmationUi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull

/**
 * Host-wide tool confirmation dialog displayed whenever the host emits a
 * `tool_call.requested` event. Rendered at the top level of
 * [com.verzeta.android.ui.shell.VerzetaApp] so it appears regardless of
 * which conversation or screen is currently foregrounded.
 *
 * The dialog presents the tool name and a scrollable, read-only monospace
 * block of the 2-space pretty-printed JSON arguments, then offers Approve
 * and Deny actions. Tapping outside the dialog is treated as a denial,
 * matching the keyboard-dismiss behaviour on desktop. The dialog is
 * dismissed automatically by [com.verzeta.android.MainViewModel]'s event
 * router when a `tool_call.completed` event arrives for the same call id,
 * preventing a stale prompt when approval was granted from another client.
 */
@Composable
fun ToolConfirmationDialog(
    pending: PendingToolConfirmationUi,
    onApprove: () -> Unit,
    onDeny: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDeny,  // tap-outside = deny
        title = { Text("Tool Call Confirmation") },
        text = {
            Column {
                Text(
                    "The assistant wants to execute a tool. Review and approve?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(12.dp))
                Text(
                    "Tool: ${pending.toolName}",
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.size(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .padding(10.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        prettyArgs(pending.arguments),
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Spacer(Modifier.size(8.dp))
                Text(
                    "Approving will execute this tool and return the result to the assistant.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(onClick = onApprove) { Text("Approve") }
        },
        dismissButton = {
            TextButton(onClick = onDeny) { Text("Deny") }
        },
    )
}

private val PRETTY = Json { prettyPrint = true; prettyPrintIndent = "  " }

private fun prettyArgs(value: JsonElement): String = when (value) {
    JsonNull -> "{}"
    else -> runCatching { PRETTY.encodeToString(JsonElement.serializer(), value) }
        .getOrDefault(value.toString())
}
