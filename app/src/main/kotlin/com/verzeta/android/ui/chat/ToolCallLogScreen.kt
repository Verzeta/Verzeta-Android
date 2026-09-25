// SPDX-FileCopyrightText: 2026 Aditya Mehra <aix.m@outlook.com>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

/**
 * @file ToolCallLogScreen.kt
 * @brief Full-screen log of tool calls executed in the selected conversation,
 *        populated via `tool_call.list_for_conv` on entry and kept current
 *        through `tool_call.added` / `tool_call.updated` events.
 * @layer UI
 * @dependencies Jetpack Compose, MainViewModel, ToolCallUi, MainUiState
 */

package com.verzeta.android.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.verzeta.android.MainUiState
import com.verzeta.android.MainViewModel
import com.verzeta.android.data.agent.ToolCallStatus
import com.verzeta.android.data.agent.ToolCallUi
import com.verzeta.android.data.host.HostStatus
import com.verzeta.android.ui.components.EmptyState
import com.verzeta.android.ui.tools.TopBar
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Full-screen tool-call activity log for the selected conversation.
 *
 * Reads from `state.convToolCalls[selectedConversationId]`, populated by
 * `tool_call.list_for_conv` on entry and kept live via `tool_call.added`
 * and `tool_call.updated` events filtered server-side by the session's
 * `msg.subscribe` set.
 *
 * Each row shows a status dot, tool name in monospace, a short timestamp,
 * and collapsible argument / result code blocks. Border colour, dot colour,
 * and result text colour all derive from the call's status: error uses the
 * error colour token, running uses tertiary, success uses primary, and
 * pending uses the surface-variant token.
 */
@Composable
fun ToolCallLogScreen(state: MainUiState, actions: MainViewModel) {
    val convId = state.selectedConversationId ?: return
    val connected = state.activeHost?.status == HostStatus.Connected
    val calls = state.convToolCalls[convId].orEmpty()

    LaunchedEffect(convId, connected) {
        if (connected) actions.loadToolCallsForConversation(convId)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopBar(
            title = "Tool Activity Log",
            subtitle = if (calls.isEmpty()) null else "${calls.size} tool call${if (calls.size == 1) "" else "s"}",
            onBack = actions::closeToolCallLog,
            onRefresh = if (connected) ({ actions.loadToolCallsForConversation(convId) }) else null,
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        if (calls.isEmpty()) {
            EmptyState(
                title = "No tool calls yet",
                detail = "When the assistant uses tools (shell commands, file operations), " +
                    "their execution history will appear here.",
            )
            return@Column
        }

        LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            items(calls, key = { it.id }) { call ->
                Spacer(Modifier.height(10.dp))
                ToolCallCard(call)
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun ToolCallCard(call: ToolCallUi) {
    val borderColor = when (call.status) {
        ToolCallStatus.Error -> MaterialTheme.colorScheme.error
        ToolCallStatus.Running -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(1.dp, borderColor.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(call.status)
            Spacer(Modifier.size(8.dp))
            Text(
                call.toolName.ifBlank { "—" },
                style = MaterialTheme.typography.titleSmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                formatHm(call.startedAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Args + result blocks rendered in a code-style surfaceContainerHighest
        // surface; result text uses the error colour token when status=error.
        if (call.arguments != JsonNull) {
            Spacer(Modifier.size(8.dp))
            CodeBlock(
                content = prettyJson(call.arguments),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        if (call.result != JsonNull) {
            Spacer(Modifier.size(6.dp))
            CodeBlock(
                content = prettyJson(call.result),
                color = if (call.status == ToolCallStatus.Error) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun StatusDot(status: ToolCallStatus) {
    val color = when (status) {
        ToolCallStatus.Success -> MaterialTheme.colorScheme.primary
        ToolCallStatus.Running -> MaterialTheme.colorScheme.tertiary
        ToolCallStatus.Error -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(color),
    )
}

@Composable
private fun CodeBlock(content: String, color: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(8.dp),
    ) {
        Text(
            content,
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
            color = color,
        )
    }
}

private val PRETTY = Json { prettyPrint = true; prettyPrintIndent = "  " }

/** Pretty-print any JsonElement; falls back to a primitive's content for raw strings. */
private fun prettyJson(value: JsonElement): String = runCatching {
    when (value) {
        is JsonPrimitive -> value.contentOrNull ?: PRETTY.encodeToString(JsonElement.serializer(), value)
        else -> PRETTY.encodeToString(JsonElement.serializer(), value)
    }
}.getOrDefault(value.toString())

/** Format an ISO 8601 timestamp as `HH:MM` (matches the QML's locale short time). */
private fun formatHm(iso: String): String {
    if (iso.isBlank()) return ""
    return runCatching {
        Instant.parse(iso).atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("HH:mm"))
    }.getOrDefault(iso)
}
