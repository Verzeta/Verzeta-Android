// SPDX-FileCopyrightText: 2026 Aditya Mehra <aix.m@outlook.com>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

/**
 * @file SidebarRow.kt
 * @brief Sealed interface and supporting types that model every renderable row
 *        in the sidebar conversation browser, mirroring the host's flat
 *        sidebar categoriser output.
 * @layer Model
 * @dependencies com.verzeta.android.data.folder.FolderType
 */

package com.verzeta.android.data.chat

import com.verzeta.android.data.folder.FolderType

/**
 * One renderable row in the sidebar/conversations browser.
 *
 * Mirrors the desktop's `SidebarFlatModel::Row` (see
 * `frontend/components/sidebar-flat-model.cpp` on the host repo). The
 * categoriser emits a flat list; the UI maps each row to a Composable
 * indented by [depth]. Depth conventions match the desktop:
 *
 *   0 = top-level section header  (Pinned / Projects&Orgs / …)
 *   1 = folder header  (under Projects&Orgs)  OR  conversation row
 *       under a top-level non-Projects&Orgs section
 *   2 = members / subsection headers / action rows  (inside a project body)
 *       OR  conversation row inside a non-project folder body
 *   3 = conversation row inside a project's Group Chats / Direct Chats
 *       subsection
 *
 * Each variant carries a stable [key] so `LazyColumn.items(key=…)` keeps
 * scroll position across rebuilds.
 */
sealed interface SidebarRow {
    val key: String
    val depth: Int

    /** Stable section ids — keys for [com.verzeta.android.MainUiState.collapsedSidebarIds]. */
    companion object {
        const val SectionPinned        = "section:pinned"
        const val SectionProjectsOrgs  = "section:projects-orgs"
        const val SectionStandaloneGrp = "section:standalone-groups"
        const val SectionDirectAgents  = "section:direct-agents"
        const val SectionPlainChats    = "section:plain-chats"
    }

    /** Collapsible top-level section header (Pinned / Projects&Orgs / …). */
    data class SectionHeader(
        val sectionId: String,
        val label: String,
        val count: Int,
        val collapsed: Boolean,
    ) : SidebarRow {
        override val depth: Int = 0
        override val key: String = "section:$sectionId"
    }

    /** Project / Org / Regular folder header — always inside Projects&Orgs. */
    data class FolderHeader(
        val folderId: String,
        val folderName: String,
        val folderType: FolderType,
        val convCount: Int,
        val collapsed: Boolean,
    ) : SidebarRow {
        override val depth: Int = 1
        override val key: String = "folder:$folderId"
    }

    /**
     * Always-expanded subsection header inside a project body: "TEAM
     * MEMBERS", "Group Chats", "Direct Chats". Empty subsections never
     * emit a header.
     */
    data class SubsectionHeader(
        val folderId: String,
        val label: String,
        val count: Int,
        val subsectionId: String,
    ) : SidebarRow {
        override val depth: Int = 2
        override val key: String = "sub:$folderId:$subsectionId"
    }

    /**
     * One team member inside a project body — tap opens (or reuses) a
     * 1:1 with this agent via `folder.member.chat.open`. The agent's
     * display name and icon are resolved at render time from
     * `state.agents` keyed by [agentId]; we don't denormalise here.
     */
    data class MemberRow(
        val folderId: String,
        val agentId: String,
        val alias: String,
        val isCoordinator: Boolean,
    ) : SidebarRow {
        override val depth: Int = 2
        override val key: String = "member:$folderId:$alias"
    }

    /** Inline "Start group chat" action row inside a project body. */
    data class ActionRow(
        val folderId: String,
        val label: String,
        val kind: ActionKind,
    ) : SidebarRow {
        override val depth: Int = 2
        override val key: String = "action:$folderId:${kind.name}"
    }

    /**
     * Conversation row. [overrideTitle] is set for project Direct
     * Chats — desktop strips the "Chat with @<alias>" prefix and
     * appends "(AgentName)" via the project's member roster (see
     * `SidebarFlatModel::derivedDirectChatTitle`).
     */
    data class ConversationItem(
        val conversation: ConversationUi,
        val parentFolderId: String?,
        val deleting: Boolean,
        val overrideTitle: String? = null,
        override val depth: Int,
    ) : SidebarRow {
        override val key: String = "conv:${conversation.id}:$depth"
    }
}

/**
 * Discriminator for inline action rows inside a project body. Each variant maps
 * to a distinct tap target that the Android UI renders as a labelled button row.
 */
enum class ActionKind {
    /** "+ Start Group Chat with All" — emitted when a project has >= 2 members. */
    StartGroupChat,
}
