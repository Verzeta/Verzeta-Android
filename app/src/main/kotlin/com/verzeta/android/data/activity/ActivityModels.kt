// SPDX-FileCopyrightText: 2026 Aditya Mehra <aix.m@outlook.com>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

/**
 * @file ActivityModels.kt
 * @brief UI model for a single row of the host's append-only activity audit log,
 *        deserialised from the wire payload produced by `AuditService::rowToMap`.
 * @layer Model
 * @dependencies None — pure Kotlin data declarations.
 */

package com.verzeta.android.data.activity

/**
 * One row of the host's `activity_log` audit table, serialised over the wire
 * by `AuditService::rowToMap` with camelCase keys.
 *
 * `eventDetail` is the JSON payload the host attached to the event
 * (provider+model for agent_turn, path+bytes for file_written, etc).
 * The repository deserialises it to a `Map<String, Any>` so the
 * Compose layer can drill in without a manual parse.
 *
 * `createdAt` is an ISO-8601 string with millisecond precision in UTC
 * (Qt::ISODateWithMs convention). The repository keeps it as a string;
 * the UI formats via `formatTimestamp` for display.
 */
data class ActivityEventUi(
    val id: String,
    val createdAt: String,
    val projectFolderId: String,
    val conversationId: String,
    val turnId: String,
    val actorKind: String,
    val actorAlias: String,
    val actorAgentId: String,
    val actorClientId: String,
    val eventType: String,
    val toolName: String,
    val eventSummary: String,
    val eventDetail: Map<String, Any?> = emptyMap(),
)
