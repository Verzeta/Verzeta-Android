// SPDX-FileCopyrightText: 2026 Aditya Mehra <aix.m@outlook.com>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

/**
 * @file ProjectDocumentUi.kt
 * @brief Model for one project document row as listed by the host's
 *        `folder.documents` op (name, host-side path, byte size).
 * @layer Model
 * @dependencies None — pure Kotlin data declarations.
 */

package com.verzeta.android.data.folder

/**
 * One project document as returned by `folder.documents`. Wire shape from
 * `ConversationController::projectDocuments` (conversation-controller.cpp:341)
 * — `{name, path, size}`.
 *
 * `path` is the host-side absolute filesystem path (useful for diagnostics
 * / debugging only — Android can't open it). `size` is bytes on disk.
 */
data class ProjectDocumentUi(
    val name: String,
    val path: String = "",
    val size: Long = 0L,
)
