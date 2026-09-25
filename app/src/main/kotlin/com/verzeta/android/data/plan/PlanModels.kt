// SPDX-FileCopyrightText: 2026 Aditya Mehra <aix.m@outlook.com>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

/**
 * @file PlanModels.kt
 * @brief Plan and plan-step models for the multi-agent task system: the
 *        per-plan row with budgets and progress counters, per-step rows
 *        with intervention gating, and the plan / step lifecycle enums.
 * @layer Model
 * @dependencies None — pure Kotlin data declarations.
 */

package com.verzeta.android.data.plan

/**
 * Plan + step models.
 *
 * The wire shape is `WireDbReader::planById` / `plansForConversation`, which
 * projects the `agent_plans` row in **snake_case** and appends a `steps` array
 * of `plan_steps` rows. Write payloads (`task.start` / `step.retry` /
 * `plan.stop`) use snake_case too. These models were once written against
 * `PlansModel`'s camelCase map instead, which the desktop QML overlay consumes
 * and no client ever receives.
 *
 * [PlanUi.totalSteps] and [PlanUi.doneSteps] are **derived** from the parsed
 * steps: the host sends neither, and its own overlay counts step statuses.
 *
 * [PlanStepUi.canIntervene] is likewise **derived**, because the wire has no
 * such column. It reproduces the host's rule from `plans-model.cpp:323-327`:
 * parent plan ∈ {Executing, Critiquing, Blocked} AND step ≠ Done.
 *
 * There are no budget or turn counters here. `turns_used` and
 * `heartbeats_used` still exist as columns but nothing increments them any
 * more, so the desktop overlay stopped rendering them and so do we.
 */
data class PlanUi(
    val id: String,
    val conversationId: String,
    val goal: String,
    val status: PlanStatus,
    val startedBy: String = "",
    val createdAtMs: Long = 0L,
    val updatedAtMs: Long = 0L,
    val totalSteps: Int = 0,
    val doneSteps: Int = 0,
    val steps: List<PlanStepUi> = emptyList(),
)

/**
 * One step of a plan, with owner alias, lifecycle status, acceptance
 * criteria, retry/rejection counters, and the server-computed
 * [canIntervene] flag that gates the per-step Retry / MarkDone / Skip
 * actions.
 */
data class PlanStepUi(
    val id: String,
    val title: String,
    val description: String = "",
    val ownerAlias: String = "",
    val status: StepStatus,
    val acceptance: String = "",
    val rejectionCount: Int = 0,
    val toolRetryCount: Int = 0,
    val executorTurnsUsed: Int = 0,
    val lastRejectionReason: String = "",
    val canIntervene: Boolean = false,
)

/**
 * Plan lifecycle states. Wire enum strings emitted by
 * `planStatusToString`. Action visibility:
 *   - Stop button: visible when status NOT in {Completed, Failed}
 *     (matches PlansOverlay.qml:163-164).
 */
enum class PlanStatus(val wireValue: String, val displayName: String) {
    Planning("planning", "Planning"),
    Executing("executing", "Executing"),
    Critiquing("critiquing", "Critiquing"),
    Blocked("blocked", "Blocked"),
    Completed("completed", "Completed"),
    Failed("failed", "Failed"),
    Stopped("stopped", "Stopped"),
    Unknown("", "Unknown");

    val isTerminal: Boolean get() = this == Completed || this == Failed || this == Stopped

    companion object {
        fun fromWire(value: String?): PlanStatus =
            entries.firstOrNull { it.wireValue == value } ?: Unknown
    }
}

/**
 * Step lifecycle states. Wire enum strings from `stepStatusToString`.
 * `Done` controls strike-through rendering of the title (PlansOverlay.qml:245).
 */
enum class StepStatus(val wireValue: String, val displayName: String) {
    Pending("pending", "Pending"),
    InProgress("in_progress", "In progress"),
    Submitted("submitted", "Submitted"),
    Done("done", "Done"),
    Blocked("blocked", "Blocked"),
    NeedsRework("needs_rework", "Needs rework"),
    Unknown("", "Unknown");

    companion object {
        fun fromWire(value: String?): StepStatus =
            entries.firstOrNull { it.wireValue == value } ?: Unknown
    }
}
