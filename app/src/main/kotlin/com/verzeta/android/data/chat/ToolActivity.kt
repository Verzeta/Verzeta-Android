// SPDX-FileCopyrightText: 2026 Aditya Mehra <aix.m@outlook.com>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

/**
 * @file ToolActivity.kt
 * @brief UI model and helper functions for the Tool Activity log, which captures
 *        assistant tool-call and tool-result messages filtered out of the main
 *        chat list and surfaced in a dedicated activity view.
 * @layer Model
 * @dependencies com.verzeta.android.protocol.RemoteJson; kotlinx.serialization.json
 */

package com.verzeta.android.data.chat

import com.verzeta.android.protocol.RemoteJson
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * One row in the Tool Activity log. The host's `remote-ws-session.cpp`
 * deliberately emits every message over the wire (`role == "tool"` rows and
 * empty assistant placeholders with `finish_reason == "tool_calls"`); the
 * desktop QML hides them from the chat list (`message-list-model.cpp:82-87`)
 * and we mirror that filter on Android, holding the filtered rows here.
 *
 * Two kinds:
 *   - [Kind.AssistantToolCall]  — assistant placeholder rows that *issue*
 *     tool calls. `payload` is the raw assistant content (often a JSON
 *     `tool_calls` array; sometimes empty when the host emits the calls
 *     out-of-band).
 *   - [Kind.ToolResult]         — `role == "tool"` rows that carry the
 *     execution *result*. `payload` is whatever the host put in the message
 *     content (typically a JSON object with name + input + output).
 *
 * Grouping is by `turnId`. The screen renders one section per turn in
 * chronological order and one row per [ToolActivityUi] entry inside.
 */
data class ToolActivityUi(
    val id: String,
    val kind: Kind,
    val toolName: String,
    val argsSummary: String,
    val payload: String,
    val agentId: String?,
    val turnId: String?,
    val createdAt: String,
) {
    /** Classifies a [ToolActivityUi] row as either an outgoing tool call issued by the
     *  assistant or an incoming tool result returned by the host's tool executor. */
    enum class Kind { AssistantToolCall, ToolResult }
}

/**
 * Convert a filtered message into a [ToolActivityUi] entry. The display
 * fields are extracted with a best-effort JSON walk — the host's wire format
 * is not strictly typed (different agent runtimes emit slightly different
 * shapes), so we look at common keys and fall back to the raw content
 * string when nothing matches.
 */
fun MessageUi.toToolActivity(): ToolActivityUi {
    val kind = if (role == MessageRole.Tool) {
        ToolActivityUi.Kind.ToolResult
    } else {
        ToolActivityUi.Kind.AssistantToolCall
    }
    val parsed = parseToolPayload(text)
    return ToolActivityUi(
        id = id,
        kind = kind,
        toolName = parsed.toolName.ifBlank {
            when (kind) {
                ToolActivityUi.Kind.ToolResult -> "tool result"
                ToolActivityUi.Kind.AssistantToolCall -> "tool call"
            }
        },
        argsSummary = parsed.argsSummary,
        payload = text,
        agentId = agentId,
        turnId = turnId,
        createdAt = createdAt,
    )
}

private data class ToolPayloadInfo(val toolName: String, val argsSummary: String)

/**
 * Walk the JSON content emitted by the host on a tool-related message to
 * pull out a name + a one-line args summary. Returns blank fields if the
 * content is not JSON or none of the known keys are present.
 */
private fun parseToolPayload(content: String): ToolPayloadInfo {
    if (content.isBlank()) return ToolPayloadInfo("", "")
    val element = runCatching { RemoteJson.parseToJsonElement(content) }.getOrNull()
        ?: return ToolPayloadInfo("", "")

    return when (element) {
        is JsonObject -> extractFromObject(element)
        is JsonArray -> {
            // OpenAI-style: tool_calls is a JSON array. Use the first call's
            // function.name + function.arguments.
            val first = element.firstOrNull() as? JsonObject
                ?: return ToolPayloadInfo("", "")
            extractFromObject(first)
        }
        else -> ToolPayloadInfo("", "")
    }
}

private fun extractFromObject(obj: JsonObject): ToolPayloadInfo {
    val name = obj.string("tool_name", "name", "function_name")
        ?: ((obj["function"] as? JsonObject)?.string("name"))
        ?: ""
    val args = obj["input"] ?: obj["arguments"] ?: obj["args"] ?: obj["parameters"]
        ?: (obj["function"] as? JsonObject)?.get("arguments")
    val summary = when (args) {
        null, JsonNull -> ""
        is JsonObject -> compactObject(args)
        is JsonArray -> args.take(3).joinToString(", ") { compactValue(it) } +
            if (args.size > 3) ", …" else ""
        is JsonPrimitive -> args.contentOrNull.orEmpty().lineSequence().firstOrNull().orEmpty()
        else -> ""
    }
    return ToolPayloadInfo(name, summary.take(140))
}

private fun compactObject(obj: JsonObject): String =
    obj.entries.take(4).joinToString(", ") { (k, v) -> "$k=${compactValue(v)}" } +
        if (obj.size > 4) ", …" else ""

private fun compactValue(v: JsonElement): String = when (v) {
    is JsonPrimitive -> v.contentOrNull.orEmpty().let { if (it.length > 32) it.take(31) + "…" else it }
    is JsonObject -> "{${v.size}}"
    is JsonArray -> "[${v.size}]"
    else -> ""
}

private fun JsonObject.string(vararg keys: String): String? =
    keys.firstNotNullOfOrNull { key ->
        this[key]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank)
    }

/**
 * Mirrors the host's predicate from
 * `backend/models/message-list-model.cpp:82-87` exactly — applied at every
 * point in the Android client where messages enter UI state. Returning
 * `false` routes the message to the Tool Activity log instead of the chat
 * list.
 */
fun MessageUi.isVisibleInChat(): Boolean =
    role != MessageRole.Tool &&
        !(role == MessageRole.Assistant && text.isBlank() && finishReason == "tool_calls")
