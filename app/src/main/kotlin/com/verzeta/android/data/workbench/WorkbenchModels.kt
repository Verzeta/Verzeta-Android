// SPDX-FileCopyrightText: 2026 Aditya Mehra <aix.m@outlook.com>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

/**
 * @file WorkbenchModels.kt
 * @brief Models backing the workbench screen — currently the paired-client
 *        row from the host's `clients.list` response.
 * @layer Model
 * @dependencies None — pure Kotlin data declarations.
 */

package com.verzeta.android.data.workbench

/**
 * Paired-client row from the host's `clients.list` response. Field set
 * tracks `Serialise::clientRecord()` in
 * `backend/services/remote-ws/remote-ws-serialise.cpp` exactly: id, name,
 * created_at, last_seen_at, revoked.
 *
 * Other workbench data (agents, skills, plans, canvases, artifacts,
 * heartbeats, settings entries, tool log entries) intentionally do NOT live
 * in this file — the host's remote-ws dispatcher does not expose ops for
 * any of them yet, so a stub model would only encourage shipping
 * non-functional UI. They will return when the host adds the corresponding
 * `agent.list` / `skill.list` / `plan.list` / `canvas.get` /
 * `heartbeat.recent_changes` / `settings.get` ops.
 */
data class ClientUi(
    val id: String,
    val name: String,
    val createdAt: String = "",
    val lastSeenAt: String = "",
    val revoked: Boolean = false,
    val current: Boolean = false,
)
