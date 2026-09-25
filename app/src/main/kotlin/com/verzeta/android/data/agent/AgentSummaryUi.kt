// SPDX-FileCopyrightText: 2026 Aditya Mehra <aix.m@outlook.com>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

/**
 * @file AgentSummaryUi.kt
 * @brief Slim agent record projected from the host's `agent.list` op —
 *        just the fields the agent picker needs.
 * @layer Model
 * @dependencies None — pure Kotlin data declarations.
 */

package com.verzeta.android.data.agent

/**
 * Slim agent record from the host's `agent.list` op. The wire payload
 * carries the full agent shape (id, name, description, iconName,
 * systemPrompt, defaultPattern, modelProvider, modelName, allowedTools,
 * isCoordinator, isBuiltin, plus a couple of heartbeat-hint fields); for
 * v1 we only need the picker fields. The rest can be added to this
 * struct when agent management UI lands.
 */
data class AgentSummaryUi(
    val id: String,
    val name: String,
    val description: String = "",
    val iconName: String = "",
    val isBuiltin: Boolean = false,
    val isCoordinator: Boolean = false,
)
