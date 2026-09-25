// SPDX-FileCopyrightText: 2026 Aditya Mehra <aix.m@outlook.com>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

/**
 * @file FolderUi.kt
 * @brief Folder models: the folder row served by `folder.list_projects` /
 *        `folder.info`, the regular/project/organization type enum, the
 *        `folder.create` result echo, and the member input shape sent to
 *        `group.create`.
 * @layer Model
 * @dependencies None — pure Kotlin data declarations.
 */

package com.verzeta.android.data.folder

/**
 * Folder row from the host's `folder.list_projects` / `folder.info` ops.
 * Mirrors the host C++ struct in `backend/services/conversation-service.h`:
 * folders are a single table with a `folder_type` column whose value is
 * one of `regular`, `project`, or `organization`. The `agentIds` field is
 * a v1-legacy convenience — newer code uses the `project_members` table
 * via membership ops, which the remote-ws dispatcher does not yet expose.
 */
data class FolderUi(
    val id: String,
    val name: String,
    val parentId: String? = null,
    val folderType: FolderType = FolderType.Regular,
    val goal: String = "",
    val description: String = "",
    val agentIds: List<String> = emptyList(),
)

/**
 * The host's `folder_type` column values. [Regular] is a plain grouping
 * folder; [Project] and [Organization] carry a member roster and render
 * with the richer project body in the sidebar. Unrecognised wire values
 * fall back to [Regular].
 */
enum class FolderType {
    Regular,
    Project,
    Organization;

    val wireValue: String
        get() = when (this) {
            Regular -> "regular"
            Project -> "project"
            Organization -> "organization"
        }

    companion object {
        fun fromWire(value: String?): FolderType = when (value?.lowercase()) {
            "project" -> Project
            "organization" -> Organization
            else -> Regular
        }
    }
}

/**
 * Result echoed by the host's `folder.create`: the new id plus the
 * resolved type (the wire op defaults to `"project"` when type is omitted,
 * so the response confirms what was actually created).
 */
data class FolderCreateResult(
    val id: String,
    val type: FolderType,
)

/**
 * Member shape the Android client sends to `group.create`. Snake-case
 * keys on the wire (the host accepts camelCase too but the rest of the
 * protocol is snake_case so we stay consistent).
 */
data class GroupMemberInput(
    val agentId: String,
    val alias: String,
    val isCoordinator: Boolean = false,
)
