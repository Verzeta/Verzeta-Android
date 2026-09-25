// SPDX-FileCopyrightText: 2026 Aditya Mehra <aix.m@outlook.com>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

/**
 * @file ConversationCategoriser.kt
 * @brief Pure-function categoriser that flattens conversations, folders,
 *        and member rosters into the ordered SidebarRow list the
 *        Conversations browser renders, with title filtering and
 *        per-section collapse handling.
 * @layer Model
 * @dependencies ChatModels (ConversationUi), SidebarRow, FolderUi /
 *               FolderType / FolderMemberUi.
 */

package com.verzeta.android.data.chat

import com.verzeta.android.data.folder.FolderMemberUi
import com.verzeta.android.data.folder.FolderType
import com.verzeta.android.data.folder.FolderUi

/**
 * Pure-function categoriser that turns the wire-side conversation +
 * folder + member state into a flat list of [SidebarRow]s for the
 * Conversations browser.
 *
 * Mirrors the desktop's
 * `SidebarFlatModel::gatherCategorisedConversations` +
 * `appendFolderRows` + `rebuild` (`frontend/components/sidebar-flat-model.cpp`
 * on the host repo) so the Android sidebar reads with the same hierarchy:
 *
 *   1. Pinned section            (every is_pinned conv, regardless of folder)
 *   2. Projects & Organizations  (each folder header → members → group chats → direct chats)
 *   3. Standalone Group Chats    (root-level is_group convs)
 *   4. Direct Agent Chats        (root-level convs with non-empty primary_agent_id)
 *   5. Plain Chats               (everything else at root)
 *
 * Filter (`filterText`) is title-only, case-insensitive, applies per-conv.
 * A section / folder is hidden entirely if every conversation it owns is
 * filtered out — search becomes "focused subset of the same hierarchy"
 * rather than a flat substring grep.
 *
 * Collapse state lives in [collapsedSidebarIds]: a set containing either
 * `SidebarRow.SectionXxx` ids or folder UUIDs. Default empty → everything
 * expanded.
 *
 * @param conversations   from `state.conversations`
 * @param folders         from `state.folders` (project + org + regular)
 * @param folderMembers   map `folderId → members` from `state.folderMembers`;
 *                        empty for unloaded folders (member rows then
 *                        suppress for that folder).
 * @param deletingIds     `state.deletingConversationIds`
 * @param filterText      `state.conversationSearch`
 * @param collapsedSidebarIds  `state.collapsedSidebarIds`
 * @return flat list ready for `LazyColumn.items`. Order matches the
 *         desktop sidebar exactly.
 */
fun categoriseSidebar(
    conversations: List<ConversationUi>,
    folders: List<FolderUi>,
    folderMembers: Map<String, List<FolderMemberUi>>,
    deletingIds: Set<String>,
    filterText: String,
    collapsedSidebarIds: Set<String>,
): List<SidebarRow> {
    val out = mutableListOf<SidebarRow>()

    val filter = filterText.trim().lowercase()
    fun matchesFilter(c: ConversationUi): Boolean =
        filter.isEmpty() || c.title.lowercase().contains(filter)

    fun sortByRecency(list: List<ConversationUi>): List<ConversationUi> =
        list.sortedByDescending { it.updatedAt.ifBlank { it.createdAt } }

    val isCollapsed: (String) -> Boolean = { id -> id in collapsedSidebarIds }

    // Folders the client actually has. A conversation whose folderId is
    // set but points to a folder NOT in this set is treated as a root
    // conversation by the buckets below, so it can never vanish from the
    // sidebar while its folder is still syncing (e.g. an auto-created
    // workspace-mount anchor folder that has not arrived yet).
    val knownFolderIds: Set<String> = folders.mapTo(mutableSetOf()) { it.id }
    fun isRootConv(c: ConversationUi): Boolean =
        c.folderId.isNullOrBlank() || c.folderId !in knownFolderIds

    // ----------------------------------------------------------------
    // 1. Pinned section — every is_pinned conv, regardless of folder.
    //    Pinned duplicates: a pinned conv also appears in its natural
    //    bucket below (matching desktop semantics).
    // ----------------------------------------------------------------
    run {
        val pinned = sortByRecency(conversations.filter { it.isPinned && matchesFilter(it) })
        if (pinned.isNotEmpty()) {
            val collapsed = isCollapsed(SidebarRow.SectionPinned)
            out.add(
                SidebarRow.SectionHeader(
                    sectionId = SidebarRow.SectionPinned,
                    label = "Pinned",
                    count = pinned.size,
                    collapsed = collapsed,
                ),
            )
            if (!collapsed) {
                for (c in pinned) {
                    out.add(
                        SidebarRow.ConversationItem(
                            conversation = c,
                            parentFolderId = c.folderId,
                            deleting = c.id in deletingIds,
                            depth = 1,
                        ),
                    )
                }
            }
        }
    }

    // ----------------------------------------------------------------
    // 2. Projects & Organizations section — wraps every project /
    //    organization / regular folder. Folders sort by name ASC
    //    (matching the desktop's folder-tree natural order).
    //
    //    Per-folder convs:
    //      - Project / Org body: TEAM MEMBERS subsection + member rows
    //          + Start Group Chat action (when >= 2 members)
    //          + Group Chats subsection (when any) + rows at depth 3
    //          + Direct Chats subsection (when any) + rows at depth 3
    //      - Regular body: child convs at depth 2, no subsection split.
    // ----------------------------------------------------------------
    run {
        // Pre-collect filter-matching convs per folder so we can decide
        // folder visibility without a second pass.
        val convsByFolder: Map<String, List<ConversationUi>> = conversations
            .filter { !it.folderId.isNullOrBlank() && matchesFilter(it) }
            .groupBy { it.folderId!! }
            .mapValues { (_, list) -> sortByRecency(list) }

        val orderedFolders = folders.sortedBy { it.name.lowercase() }

        // Hide folder when a filter is active AND it has no matching convs.
        // No filter → every folder appears.
        val visibleFolders = orderedFolders.filter { folder ->
            filter.isEmpty() || convsByFolder[folder.id]?.isNotEmpty() == true
        }

        if (visibleFolders.isNotEmpty()) {
            val sectionCollapsed = isCollapsed(SidebarRow.SectionProjectsOrgs)
            out.add(
                SidebarRow.SectionHeader(
                    sectionId = SidebarRow.SectionProjectsOrgs,
                    label = "Projects & Organizations",
                    count = visibleFolders.size,
                    collapsed = sectionCollapsed,
                ),
            )
            if (!sectionCollapsed) {
                for (folder in visibleFolders) {
                    val folderConvs = convsByFolder[folder.id].orEmpty()
                    val folderCollapsed = isCollapsed(folder.id)
                    out.add(
                        SidebarRow.FolderHeader(
                            folderId = folder.id,
                            folderName = folder.name,
                            folderType = folder.folderType,
                            convCount = folderConvs.size,
                            collapsed = folderCollapsed,
                        ),
                    )
                    if (folderCollapsed) continue

                    val isProjectish = folder.folderType == FolderType.Project
                        || folder.folderType == FolderType.Organization
                    if (isProjectish) {
                        appendProjectBody(out, folder, folderConvs, folderMembers[folder.id].orEmpty(), deletingIds)
                    } else {
                        // Regular folder: child convs at depth 2, no subsection split.
                        for (c in folderConvs) {
                            out.add(
                                SidebarRow.ConversationItem(
                                    conversation = c,
                                    parentFolderId = folder.id,
                                    deleting = c.id in deletingIds,
                                    depth = 2,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }

    // ----------------------------------------------------------------
    // 3. Standalone Group Chats — root convs with isGroup = true.
    // ----------------------------------------------------------------
    appendRootBucket(
        out = out,
        sectionId = SidebarRow.SectionStandaloneGrp,
        label = "Standalone Group Chats",
        filtered = sortByRecency(
            conversations.filter { isRootConv(it) && it.isGroup && matchesFilter(it) },
        ),
        deletingIds = deletingIds,
        collapsed = isCollapsed(SidebarRow.SectionStandaloneGrp),
    )

    // ----------------------------------------------------------------
    // 4. Direct Agent Chats — root convs with !isGroup AND primary_agent_id.
    // ----------------------------------------------------------------
    appendRootBucket(
        out = out,
        sectionId = SidebarRow.SectionDirectAgents,
        label = "Direct Agent Chats",
        filtered = sortByRecency(
            conversations.filter {
                isRootConv(it) && !it.isGroup && !it.primaryAgentId.isNullOrBlank() && matchesFilter(it)
            },
        ),
        deletingIds = deletingIds,
        collapsed = isCollapsed(SidebarRow.SectionDirectAgents),
    )

    // ----------------------------------------------------------------
    // 5. Plain Chats — root convs with !isGroup AND no primary_agent_id.
    // ----------------------------------------------------------------
    appendRootBucket(
        out = out,
        sectionId = SidebarRow.SectionPlainChats,
        label = "Plain Chats",
        filtered = sortByRecency(
            conversations.filter {
                isRootConv(it) && !it.isGroup && it.primaryAgentId.isNullOrBlank() && matchesFilter(it)
            },
        ),
        deletingIds = deletingIds,
        collapsed = isCollapsed(SidebarRow.SectionPlainChats),
    )

    return out
}

/**
 * Project / organization folder body: members + optional Start-Group-Chat
 * action + Group Chats + Direct Chats subsections. Mirrors the desktop's
 * `SidebarFlatModel::appendFolderRows` projectish branch.
 */
private fun appendProjectBody(
    out: MutableList<SidebarRow>,
    folder: FolderUi,
    folderConvs: List<ConversationUi>,
    members: List<FolderMemberUi>,
    deletingIds: Set<String>,
) {
    if (members.isNotEmpty()) {
        out.add(
            SidebarRow.SubsectionHeader(
                folderId = folder.id,
                label = "TEAM MEMBERS",
                count = members.size,
                subsectionId = "members",
            ),
        )
        for (m in members) {
            out.add(
                SidebarRow.MemberRow(
                    folderId = folder.id,
                    agentId = m.agentId,
                    alias = m.alias,
                    isCoordinator = m.isCoordinator,
                ),
            )
        }
        if (members.size >= 2) {
            out.add(
                SidebarRow.ActionRow(
                    folderId = folder.id,
                    label = "Start Group Chat with All",
                    kind = ActionKind.StartGroupChat,
                ),
            )
        }
    }

    // Split convs into Group / Direct subsections. folderConvs is already
    // recency-sorted by the caller.
    val groupConvs = folderConvs.filter { it.isGroup }
    val directConvs = folderConvs.filter { !it.isGroup }

    if (groupConvs.isNotEmpty()) {
        out.add(
            SidebarRow.SubsectionHeader(
                folderId = folder.id,
                label = "Group Chats",
                count = groupConvs.size,
                subsectionId = "groupChats",
            ),
        )
        for (c in groupConvs) {
            out.add(
                SidebarRow.ConversationItem(
                    conversation = c,
                    parentFolderId = folder.id,
                    deleting = c.id in deletingIds,
                    depth = 3,
                ),
            )
        }
    }

    if (directConvs.isNotEmpty()) {
        out.add(
            SidebarRow.SubsectionHeader(
                folderId = folder.id,
                label = "Direct Chats",
                count = directConvs.size,
                subsectionId = "directChats",
            ),
        )
        for (c in directConvs) {
            val derived = derivedDirectChatTitle(c.title, members)
            out.add(
                SidebarRow.ConversationItem(
                    conversation = c,
                    parentFolderId = folder.id,
                    deleting = c.id in deletingIds,
                    overrideTitle = derived,
                    depth = 3,
                ),
            )
        }
    }
}

/**
 * Strip "Chat with @<alias>" prefix from a project Direct Chat title
 * and append "(AgentName)" when we can resolve the alias against the
 * project's member roster. Mirrors the desktop's
 * `SidebarFlatModel::derivedDirectChatTitle`.
 *
 * Returns null when the title doesn't match the prefix shape — the
 * caller falls back to the conversation's own title.
 */
private fun derivedDirectChatTitle(title: String, members: List<FolderMemberUi>): String? {
    val prefix = "Chat with @"
    if (!title.startsWith(prefix)) return null
    val alias = title.removePrefix(prefix).trim()
    if (alias.isBlank()) return null
    // We only need the agent name for the "(AgentName)" suffix, and
    // FolderMemberUi doesn't carry agentName today — the rendering side
    // already resolves it from `state.agents`. Return just the alias
    // here; the UI layer may further enrich.
    return alias
}

/**
 * Append a root-level bucket (Standalone Groups / Direct Agents / Plain).
 * Hides entirely if [filtered] is empty.
 */
private fun appendRootBucket(
    out: MutableList<SidebarRow>,
    sectionId: String,
    label: String,
    filtered: List<ConversationUi>,
    deletingIds: Set<String>,
    collapsed: Boolean,
) {
    if (filtered.isEmpty()) return
    out.add(
        SidebarRow.SectionHeader(
            sectionId = sectionId,
            label = label,
            count = filtered.size,
            collapsed = collapsed,
        ),
    )
    if (collapsed) return
    for (c in filtered) {
        out.add(
            SidebarRow.ConversationItem(
                conversation = c,
                parentFolderId = null,
                deleting = c.id in deletingIds,
                depth = 1,
            ),
        )
    }
}
