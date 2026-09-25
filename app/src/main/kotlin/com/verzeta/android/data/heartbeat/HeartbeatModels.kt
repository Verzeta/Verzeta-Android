// SPDX-FileCopyrightText: 2026 Aditya Mehra <aix.m@outlook.com>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

/**
 * @file HeartbeatModels.kt
 * @brief Heartbeat models: the per-agent heartbeat configuration row, its
 *        live status, historical runs, upcoming-fire previews, and the
 *        config-change audit rows.
 * @layer Model
 * @dependencies None — pure Kotlin data declarations.
 */

package com.verzeta.android.data.heartbeat

/**
 * Heartbeat config row. Wire shape from
 * `HeartbeatConfigService::configMapFromConfig` (heartbeat-config-service.cpp:31).
 * Response keys are camelCase — the host doesn't normalise on the way out;
 * write payloads use snake_case (the `heartbeat.upsert` op accepts both
 * but our wire convention is snake_case).
 *
 * Validation rules at the wire boundary (heartbeat.upsert at
 * remote-ws-session.cpp:2708):
 *   - agent_id + scope_id must be valid UUIDs.
 *   - scope_type ∈ {folder, conversation}.
 *   - alias non-empty bounded.
 *   - max_runs_per_day ∈ [1, 1440].
 *   - schedule formats: `@hourly` / `@daily at HH:MM` /
 *     `@weekly[ on DAY at HH:MM]` / `@interval N` (N ≥ 5).
 *     Empty schedule = manual-fire only.
 *   - auto_surface_target_conversation_id may be empty (overlay-only)
 *     or a valid existing conv UUID.
 */
data class HeartbeatConfigUi(
    val id: String,
    val agentId: String,
    val scopeType: HeartbeatScope,
    val scopeId: String,
    val alias: String,
    val enabled: Boolean,
    val schedule: String,
    val goal: String,
    val surfaceCriteria: String,
    val maxRunsPerDay: Int,
    val autoSurfaceTargetConversationId: String,
    val selfConfigAllowed: Boolean,
    val lastFireAt: String,
    val lastFireOutcome: String,
)

/**
 * Scope a heartbeat config is attached to — a folder or a single
 * conversation. [Unknown] absorbs unrecognised wire values.
 */
enum class HeartbeatScope(val wireValue: String) {
    Folder("folder"),
    Conversation("conversation"),
    Unknown("");

    companion object {
        fun fromWire(value: String?): HeartbeatScope =
            entries.firstOrNull { it.wireValue == value } ?: Unknown
    }
}

/**
 * Live status for a single config — fields that change between fires.
 * Wire shape from `HeartbeatSubagentService::configStatus`
 * (heartbeat-subagent-service.cpp:327).
 *
 * **Spec deviation note**: the prompt mentions `nextFireAt` and
 * `runsToday` for this op; the host actually returns `inflight` and
 * `queuedCount` instead. `nextFireAt` is computed client-side from the
 * config's schedule + lastFireAt by [HeartbeatConfigService]'s
 * scheduling logic. We surface what the host emits.
 */
data class HeartbeatStatusUi(
    val enabled: Boolean,
    val schedule: String,
    val lastFireAt: String,
    val lastFireOutcome: String,
    val inflight: Boolean,
    val queuedCount: Int,
)

/**
 * One historical run (heartbeat.recent_runs row). Shape from
 * heartbeat-subagent-service.cpp:381-393.
 */
data class HeartbeatRunUi(
    val id: String,
    val configId: String,
    val agentName: String,
    val alias: String,
    val startedAtMs: Long,
    val durationMs: Long,
    val outcome: String,
    val title: String,
    val surfaceStatus: String,
)

/** One upcoming fire (heartbeat.next_fires_preview row). */
data class HeartbeatNextFireUi(
    val configId: String,
    val alias: String,
    val schedule: String,
    val nextFireMs: Long,
)

/** One audit-log row (heartbeat.recent_config_changes). */
data class HeartbeatConfigChangeUi(
    val id: String,
    val configId: String,
    val changedAtMs: Long,
    val field: String,
    val oldValue: String,
    val newValue: String,
    val source: String,
    val alias: String,
    val agentId: String,
)
