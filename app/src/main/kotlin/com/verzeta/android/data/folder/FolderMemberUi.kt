// SPDX-FileCopyrightText: 2026 Aditya Mehra <aix.m@outlook.com>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

/**
 * @file FolderMemberUi.kt
 * @brief Model for one project / organization member row as returned by
 *        `folder.members` and pushed by `folder.members.changed`.
 * @layer Model
 * @dependencies None — pure Kotlin data declarations.
 */

package com.verzeta.android.data.folder

data class FolderMemberUi(
    val agentId: String,
    val alias: String,
    val isCoordinator: Boolean,
    val modelProvider: String = "",
    val modelName: String = "",
    val allowedTools: List<String> = emptyList(),
    val addedByKind: String = "",
    val addedByAgentId: String = "",
)
