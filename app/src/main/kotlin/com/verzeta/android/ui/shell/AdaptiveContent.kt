// SPDX-FileCopyrightText: 2026 Aditya Mehra <aix.m@outlook.com>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

/**
 * @file AdaptiveContent.kt
 * @brief Master-detail shell layout for Conversations + Chat on wide
 *        landscape windows: a fixed-width conversations list pane beside the
 *        chat pane, with a list-visibility toggle plumbed to the chat top
 *        bar via CompositionLocals and an empty-detail placeholder when no
 *        conversation is selected.
 * @layer Frontend
 * @dependencies ConversationsScreen, ChatScreen, VerzetaMark component,
 *               LocalVerzetaWindowSize / LocalIsMasterDetail /
 *               LocalMasterDetailController (ui/theme), MainViewModel /
 *               MainUiState, Jetpack Compose Material 3.
 */

package com.verzeta.android.ui.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.verzeta.android.MainScreen
import com.verzeta.android.MainUiState
import com.verzeta.android.MainViewModel
import com.verzeta.android.ui.chat.ChatScreen
import com.verzeta.android.ui.chat.ConversationsScreen
import com.verzeta.android.ui.components.VerzetaMark
import com.verzeta.android.ui.theme.LocalIsMasterDetail
import com.verzeta.android.ui.theme.LocalMasterDetailController
import com.verzeta.android.ui.theme.LocalVerzetaWindowSize
import com.verzeta.android.ui.theme.MasterDetailController

/**
 * Master-detail layout for Conversations + Chat when the window can show
 * both panes (`supportsTwoPane`): a large landscape window, or an
 * unfolded foldable's inner screen in either orientation.
 *
 * The list toggle is a leading menu icon inside ChatTopBar (the
 * canonical Gmail / Slack / VS Code / desktop-chat-app pattern). An
 * earlier cut used a floating "Hide list / Show conversations"
 * FilterChip pill, which overlapped the chat title and used unclear
 * language; the toggle is now plumbed through
 * [LocalMasterDetailController] so the chat-pane top bar renders it in
 * the same place the back arrow used to sit.
 *
 * Behaviour —
 *   - List visible (default): 360 dp ConversationsScreen left + divider
 *     + ChatScreen / EmptyDetailPane right. Menu icon in chat top bar
 *     hides the list when tapped.
 *   - List hidden: chat fills full width. Menu icon in chat top bar
 *     re-opens the list when tapped.
 *   - If list is hidden AND no conv is selected: auto-show the list
 *     (otherwise the user would be stranded staring at EmptyDetailPane
 *     with no obvious way back to the list).
 *
 * Honors §1.10 (focus/input — standard menu-icon pattern works with
 * touch, mouse, keyboard, D-pad), §1.11 (responsive — layout adapts;
 * visual identity preserved), §1.15 (component reuse — ConversationsScreen
 * + ChatScreen reused verbatim).
 */
object AdaptiveContent {
    /** Width in dp of the list pane when expanded (Material list-detail spec). */
    const val LIST_PANE_WIDTH_DP = 360

    /**
     * Width in dp of the list pane on a Medium window (an unfolded
     * foldable), narrower so the chat pane keeps a usable width.
     */
    const val LIST_PANE_WIDTH_MEDIUM_DP = 280

    /**
     * Returns true if this composable will take ownership of rendering
     * the current screen.
     */
    @Composable
    fun shouldRender(screen: MainScreen): Boolean {
        val window = LocalVerzetaWindowSize.current
        if (!window.supportsTwoPane) return false
        return screen == MainScreen.Conversations || screen == MainScreen.Chat
    }

    /**
     * Render the master-detail layout. Caller is responsible for only
     * invoking this when [shouldRender] returned true.
     */
    @Composable
    fun Render(state: MainUiState, actions: MainViewModel) {
        // rememberSaveable so rotation / process death preserves the
        // user's hide-list choice. Default true — list visible on entry.
        var listVisible by rememberSaveable { mutableStateOf(true) }

        // Auto-show the list if it gets hidden while no conv is
        // selected. Without this guard the user would see only the
        // EmptyDetailPane with no obvious way back to the list. The
        // LaunchedEffect re-runs whenever selectedConversationId
        // becomes null (e.g. back-handler closeConversation).
        LaunchedEffect(state.selectedConversationId) {
            if (state.selectedConversationId == null && !listVisible) {
                listVisible = true
            }
        }

        val controller = MasterDetailController(
            listVisible = listVisible,
            setListVisible = { listVisible = it },
        )

        CompositionLocalProvider(
            LocalIsMasterDetail provides true,
            LocalMasterDetailController provides controller,
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                if (listVisible) {
                    val listWidth = if (LocalVerzetaWindowSize.current.isExpanded) {
                        LIST_PANE_WIDTH_DP
                    } else {
                        LIST_PANE_WIDTH_MEDIUM_DP
                    }
                    Box(modifier = Modifier.width(listWidth.dp)) {
                        ConversationsScreen(state = state, actions = actions)
                    }
                    VerticalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        thickness = 1.dp,
                    )
                }
                Box(modifier = Modifier.fillMaxSize()) {
                    if (state.selectedConversationId != null) {
                        ChatScreen(state = state, actions = actions)
                    } else {
                        EmptyDetailPane()
                    }
                }
            }
        }
    }

    @Composable
    private fun EmptyDetailPane() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            VerzetaMark(size = 80)
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Select a conversation",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Tap a chat on the left to open it here, or start a new chat from the conversations list.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
