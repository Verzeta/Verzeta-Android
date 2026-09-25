// SPDX-FileCopyrightText: 2026 Aditya Mehra <aix.m@outlook.com>
// SPDX-License-Identifier: LGPL-3.0-or-later

package com.verzeta.android.data.chat

import com.verzeta.android.data.folder.FolderType
import com.verzeta.android.data.folder.FolderUi
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the categoriser's never-vanish contract: a conversation whose
 * folderId points to a folder the client does not have must still
 * appear in a root bucket rather than disappearing from the sidebar,
 * and a conversation inside a known regular folder renders under that
 * folder.
 */
class ConversationCategoriserTest {

    private fun categorise(
        conversations: List<ConversationUi>,
        folders: List<FolderUi>,
    ): List<SidebarRow> =
        categoriseSidebar(
            conversations = conversations,
            folders = folders,
            folderMembers = emptyMap(),
            deletingIds = emptySet(),
            filterText = "",
            collapsedSidebarIds = emptySet(),
        )

    @Test
    fun convInUnknownFolderFallsBackToPlainChats_neverVanishes() {
        val rows = categorise(
            conversations = listOf(
                ConversationUi(id = "orphan", title = "Hello Friend", folderId = "missing-folder"),
            ),
            folders = emptyList(), // client has not synced the folder
        )
        val rendered = rows.any { it is SidebarRow.ConversationItem && it.conversation.id == "orphan" }
        assertTrue("orphaned conversation must still render", rendered)
    }

    @Test
    fun convInKnownRegularFolderRendersUnderTheFolder() {
        val rows = categorise(
            conversations = listOf(
                ConversationUi(id = "hello", title = "Hello Friend", folderId = "aegis"),
            ),
            folders = listOf(
                FolderUi(id = "aegis", name = "Aegis", folderType = FolderType.Regular),
            ),
        )
        val hasFolderHeader = rows.any { it is SidebarRow.FolderHeader && it.folderId == "aegis" }
        val convUnderFolder = rows.any {
            it is SidebarRow.ConversationItem &&
                it.conversation.id == "hello" &&
                it.parentFolderId == "aegis"
        }
        assertTrue("regular folder must render a header", hasFolderHeader)
        assertTrue("conv must render under its regular folder", convUnderFolder)
    }
}
