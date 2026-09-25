// SPDX-FileCopyrightText: 2026 Aditya Mehra <aix.m@outlook.com>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

/**
 * @file ToolCallUi.kt
 * @brief Tool-call and agent-step models: persisted tool-call rows with
 *        status, the pending confirmation request raised before a gated
 *        tool executes, per-iteration agent reasoning steps, and the
 *        coalesced agent run state.
 * @layer Model
 * @dependencies kotlinx.serialization.json (JsonElement payloads).
 */

package com.verzeta.android.data.agent

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull

/**
 * One row from the host's `tool_calls` table. Wire shape from
 * `ChatController::toolCallToWireMap` (chat-controller.cpp:1980) and
 * delivered via `tool_call.list_for_conv` / `tool_call.added` /
 * `tool_call.updated` / `tool_call.get`.
 *
 * **Wire keys are snake_case** (matches `msg.*` / `conv.*` convention).
 * `arguments` is a parsed JSON object, `result` is any JSON value
 * (object / array / primitive / null) — both retained as JsonElement so
 * the UI can pretty-print without re-parsing strings.
 *
 * `status` ∈ {pending, running, success, error}. Visual treatment:
 *   - status dot: success=green, running=yellow, error=red, pending=grey
 *   - card border: error=error, running=tertiary, default=outlineVariant
 *   - result text: error=error colour, otherwise primary-tinted code colour
 */
data class ToolCallUi(
    val id: String,
    val messageId: String,
    val toolName: String,
    val arguments: JsonElement = JsonNull,
    val result: JsonElement = JsonNull,
    val status: ToolCallStatus,
    val startedAt: String = "",
    val completedAt: String = "",
    val planStepId: String = "",
)

/**
 * Lifecycle status of a persisted tool call. [Unknown] absorbs wire
 * values this client version doesn't recognise.
 */
enum class ToolCallStatus(val wireValue: String) {
    Pending("pending"),
    Running("running"),
    Success("success"),
    Error("error"),
    Unknown("");

    companion object {
        fun fromWire(value: String?): ToolCallStatus =
            entries.firstOrNull { it.wireValue == value } ?: Unknown
    }
}

/**
 * Pending request from a `tool_call.requested` event. Drives the
 * host-wide ToolConfirmationDialog. The pending row is NOT in the
 * `tool_calls` table yet — `tool_call.get` would return `not_found` —
 * so the event payload IS the data source. The matching
 * `tool_call.completed` event (or a new `tool_call.requested` for a
 * different call) clears it.
 */
data class PendingToolConfirmationUi(
    val callId: String,
    val toolName: String,
    val arguments: JsonElement = JsonNull,
    val messageId: String = "",
)

/**
 * One reasoning step from `agent.step.started` /
 * `agent.step.completed`. Status transitions:
 *   - on `started`: append with Running.
 *   - on `completed` with success=true: most-recent matching
 *     (description + Running) → Success.
 *   - on `completed` with success=false: same → Error.
 *   - on `agent.run.state {is_running: false}`: cleared.
 */
data class AgentStepUi(
    val iteration: Int,
    val description: String,
    val status: AgentStepStatus,
)

/**
 * Status of one agent reasoning step, driving the step list's per-row
 * progress indicator.
 */
enum class AgentStepStatus { Pending, Running, Success, Error }

/**
 * Coalesced (running, iteration) tuple from `agent.run.state`. The host
 * coalesces `isRunningChanged` + `iterationChanged` into a single event
 * so both fields land together — never split.
 */
data class AgentRunStateUi(
    val isRunning: Boolean = false,
    val currentIteration: Int = 0,
)
