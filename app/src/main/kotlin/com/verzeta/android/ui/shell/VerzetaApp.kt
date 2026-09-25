// SPDX-FileCopyrightText: 2026 Aditya Mehra <aix.m@outlook.com>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

/**
 * @file VerzetaApp.kt
 * @brief Root shell composable: routes MainScreen values to their screens,
 *        owns the hardware/gesture back-handler logic (including the
 *        master-detail special case), switches between bottom navigation
 *        and the navigation rail per form factor, and hosts the app-wide
 *        tool-call confirmation dialog and the snackbar that reports failed
 *        actions on every screen.
 * @layer Frontend
 * @dependencies MainViewModel / MainUiState / MainScreen, every top-level
 *               screen composable (chat, folders, settings, skills, tools,
 *               MCP, project rooms, activity, add-host), VerzetaBottomNav,
 *               VerzetaNavRail, AdaptiveContent, LocalVerzetaWindowSize,
 *               Jetpack Compose Material 3 Scaffold.
 */

package com.verzeta.android.ui.shell

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.verzeta.android.MainScreen
import com.verzeta.android.MainUiState
import com.verzeta.android.MainViewModel
import com.verzeta.android.ui.theme.LocalVerzetaWindowSize
import com.verzeta.android.ui.activity.ActivityTimelineScreen
import com.verzeta.android.ui.addhost.AddHostScreen
import com.verzeta.android.ui.chat.ChatScreen
import com.verzeta.android.ui.chat.ConvSettingsScreen
import com.verzeta.android.ui.chat.ConversationsScreen
import com.verzeta.android.ui.chat.ArtifactsScreen
import com.verzeta.android.ui.chat.CanvasScreen
import com.verzeta.android.ui.chat.GeneratedMediaScreen
import com.verzeta.android.ui.chat.PlansOverlayScreen
import com.verzeta.android.ui.chat.ToolCallLogScreen
import com.verzeta.android.ui.chat.ToolConfirmationDialog
import com.verzeta.android.ui.folders.FolderConversationsScreen
import com.verzeta.android.ui.folders.FoldersScreen
import com.verzeta.android.ui.folders.HeartbeatActivityScreen
import com.verzeta.android.ui.group.NewGroupScreen
import com.verzeta.android.ui.home.HomeScreen
import com.verzeta.android.ui.mcp.McpServerToolsScreen
import com.verzeta.android.ui.mcp.McpServersScreen
import com.verzeta.android.ui.settings.AboutScreen
import com.verzeta.android.ui.settings.HelpScreen
import com.verzeta.android.ui.settings.RevokedDevicesScreen
import com.verzeta.android.ui.settings.SettingsScreen
import com.verzeta.android.ui.skills.SkillDetailScreen
import com.verzeta.android.ui.search.SearchProvidersScreen
import com.verzeta.android.ui.skills.SkillsScreen
import com.verzeta.android.ui.tools.ToolsScreen

/**
 * Root composable. Owns the bottom navigation, the screen-level [Scaffold],
 * and routing between the five surfaces backed by host ops:
 * Home, AddHost, Conversations, Chat, Settings.
 */
@Composable
fun VerzetaApp(state: MainUiState, actions: MainViewModel) {
    val window = LocalVerzetaWindowSize.current
    val inMasterDetail = window.supportsTwoPane &&
        (state.screen == MainScreen.Conversations ||
         state.screen == MainScreen.Chat)

    BackHandler(enabled = state.screen != MainScreen.Home) {
        if (inMasterDetail) {
            if (state.selectedConversationId != null) {
                actions.closeConversation()
            } else {
                actions.showHome()
            }
            return@BackHandler
        }
        when (state.screen) {
            MainScreen.Home -> Unit  // unreachable due to enabled guard
            MainScreen.AddHost -> actions.showHome()
            MainScreen.Conversations -> actions.showHome()
            MainScreen.Chat -> {
                // Leave the conversation, as the top-bar back arrow does, so
                // it is unsubscribed and its mentions notify again.
                actions.closeConversation()
                actions.showConversations()
            }
            // Same destination as the Folders top-bar back arrow.
            MainScreen.Folders -> actions.showConversations()
            MainScreen.FolderConversations -> actions.closeFolder()
            MainScreen.NewGroup -> actions.closeNewGroup()
            MainScreen.Settings -> actions.showHome()
            MainScreen.Tools -> actions.closeTools()
            MainScreen.McpServers -> actions.closeMcpServers()
            MainScreen.McpServerTools -> actions.closeMcpServer()
            MainScreen.Skills -> actions.closeSkills()
            MainScreen.SkillDetail -> actions.closeSkillDetail()
            MainScreen.SearchProviders -> actions.closeSearchProviders()
            MainScreen.HeartbeatActivity -> actions.closeHeartbeatActivity()
            MainScreen.ActivityTimeline -> actions.closeActivityTimeline()
            MainScreen.ConvSettings -> actions.closeConvSettings()
            MainScreen.PlansOverlay -> actions.closePlansOverlay()
            MainScreen.ToolCallLog -> actions.closeToolCallLog()
            MainScreen.Artifacts -> actions.closeArtifacts()
            MainScreen.GeneratedMedia -> actions.closeGeneratedMedia()
            MainScreen.Canvas -> actions.closeCanvas()
            MainScreen.ProjectRoomsLanding -> actions.closeProjectRoomsLanding()
            MainScreen.TemplateLibrary -> actions.closeTemplateLibrary()
            MainScreen.ProjectCreate -> actions.closeProjectCreate()
            MainScreen.RevokedDevices -> actions.closeRevokedDevices()
            MainScreen.About -> actions.closeAbout()
            MainScreen.Help -> {
                if (state.selectedHelpDoc != null) actions.closeHelpDoc()
                else actions.closeHelp()
            }
        }
    }

    val useRail = window.isTv || window.supportsTwoPane

    val showsNavSurface = state.screen.showsBottomNav || inMasterDetail

    // App-wide failure surface. Most screens do not render the status line,
    // so every failed action is also shown here. A newer failure replaces
    // the one on screen.
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.errorId) {
        val message = state.errorMessage ?: return@LaunchedEffect
        val shownId = state.errorId
        snackbarHostState.showSnackbar(
            message = message,
            withDismissAction = true,
            duration = SnackbarDuration.Long,
        )
        actions.consumeError(shownId)
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.surface,
            contentWindowInsets = WindowInsets.systemBars,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                if (!useRail && state.screen.showsBottomNav) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(modifier = Modifier.widthIn(max = 600.dp)) {
                            VerzetaBottomNav(state = state, actions = actions)
                        }
                    }
                }
            },
        ) { padding ->
            Row(modifier = Modifier.fillMaxSize().padding(padding)) {
                if (useRail && showsNavSurface) {
                    VerzetaNavRail(state = state, actions = actions)
                }
                Box(modifier = Modifier.fillMaxSize()) {
                if (AdaptiveContent.shouldRender(state.screen)) {
                    AdaptiveContent.Render(state = state, actions = actions)
                    return@Box
                }
                when (state.screen) {
                    MainScreen.Home -> HomeScreen(state = state, actions = actions)
                    MainScreen.AddHost -> AddHostScreen(state = state, actions = actions)
                    MainScreen.Conversations -> ConversationsScreen(state = state, actions = actions)
                    MainScreen.Chat -> ChatScreen(state = state, actions = actions)
                    MainScreen.Folders -> FoldersScreen(state = state, actions = actions)
                    MainScreen.FolderConversations -> FolderConversationsScreen(state = state, actions = actions)
                    MainScreen.NewGroup -> NewGroupScreen(state = state, actions = actions)
                    MainScreen.Settings -> SettingsScreen(state = state, actions = actions)
                    MainScreen.Tools -> ToolsScreen(state = state, actions = actions)
                    MainScreen.McpServers -> McpServersScreen(state = state, actions = actions)
                    MainScreen.McpServerTools -> McpServerToolsScreen(state = state, actions = actions)
                    MainScreen.Skills -> SkillsScreen(state = state, actions = actions)
                    MainScreen.SkillDetail -> SkillDetailScreen(state = state, actions = actions)
                    MainScreen.SearchProviders -> SearchProvidersScreen(state = state, actions = actions)
                    MainScreen.HeartbeatActivity -> HeartbeatActivityScreen(state = state, actions = actions)
                    MainScreen.ActivityTimeline -> ActivityTimelineScreen(state = state, actions = actions)
                    MainScreen.ConvSettings -> ConvSettingsScreen(state = state, actions = actions)
                    MainScreen.PlansOverlay -> PlansOverlayScreen(state = state, actions = actions)
                    MainScreen.ToolCallLog -> ToolCallLogScreen(state = state, actions = actions)
                    MainScreen.Artifacts -> ArtifactsScreen(state = state, actions = actions)
                    MainScreen.GeneratedMedia -> GeneratedMediaScreen(state = state, actions = actions)
                    MainScreen.Canvas -> CanvasScreen(state = state, actions = actions)
                    MainScreen.ProjectRoomsLanding ->
                        com.verzeta.android.ui.projectrooms.ProjectRoomsLandingScreen(
                            state = state, actions = actions,
                        )
                    MainScreen.TemplateLibrary ->
                        com.verzeta.android.ui.projectrooms.TemplateLibraryScreen(
                            state = state, actions = actions,
                        )
                    MainScreen.ProjectCreate ->
                        com.verzeta.android.ui.projectrooms.ProjectCreateScreen(
                            state = state, actions = actions,
                        )
                    MainScreen.RevokedDevices -> RevokedDevicesScreen(state = state, actions = actions)
                    MainScreen.About -> AboutScreen(state = state, actions = actions)
                    MainScreen.Help -> HelpScreen(state = state, actions = actions)
                }
                }  // Box (content)
            }  
        }
    }

    // Host-wide tool-call confirmation modal. tool_call.requested fires
    // unfiltered for the whole authenticated session; the dialog must
    // render regardless of which screen the user is on (matches the
    // desktop ToolConfirmationDialog's app-overlay parent).
    state.pendingToolConfirmation?.let { pending ->
        ToolConfirmationDialog(
            pending = pending,
            onApprove = { actions.approveToolCall(pending.callId) },
            onDeny = { actions.denyToolCall(pending.callId) },
        )
    }
}

private val MainScreen.showsBottomNav: Boolean
    get() = this != MainScreen.AddHost &&
        this != MainScreen.Chat &&        // composer + IME use the full bottom
        this != MainScreen.NewGroup &&
        this != MainScreen.McpServerTools &&
        this != MainScreen.SkillDetail &&
        this != MainScreen.HeartbeatActivity &&
        this != MainScreen.ActivityTimeline &&
        this != MainScreen.ConvSettings &&
        this != MainScreen.PlansOverlay &&
        this != MainScreen.ToolCallLog &&
        this != MainScreen.Artifacts &&
        this != MainScreen.GeneratedMedia &&
        this != MainScreen.Canvas &&
        this != MainScreen.ProjectRoomsLanding &&
        this != MainScreen.TemplateLibrary &&
        this != MainScreen.ProjectCreate &&
        this != MainScreen.RevokedDevices &&
        this != MainScreen.About &&
        this != MainScreen.Help
