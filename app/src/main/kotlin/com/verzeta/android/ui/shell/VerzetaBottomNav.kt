// SPDX-FileCopyrightText: 2026 Aditya Mehra <aix.m@outlook.com>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

/**
 * @file VerzetaBottomNav.kt
 * @brief Bottom navigation bar for compact (phone) layouts with three
 *        destinations: Home, Chat, and Settings. Selection state derives
 *        from the current MainScreen; navigation dispatches through
 *        MainViewModel.
 * @layer Frontend
 * @dependencies MainViewModel / MainUiState / MainScreen, Jetpack Compose
 *               Material 3 NavigationBar.
 */

package com.verzeta.android.ui.shell

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.verzeta.android.MainScreen
import com.verzeta.android.MainUiState
import com.verzeta.android.MainViewModel

/**
 * Bottom navigation. Three destinations: Home, Chat, Settings. The
 * Agents / Tools / Skills catalogs ARE exposed on the wire today
 * (verified: agent.list / tool.list / skill.list / mcp.list all
 * dispatch in `backend/remote/server/wire-session.cpp`), but they're
 * read-only browsers that surface from inside Settings rather than
 * as primary destinations. Promoting any of them to a top-level nav
 * slot is a future candidate once the catalog screens grow editing
 * affordances.
 */
@Composable
fun VerzetaBottomNav(state: MainUiState, actions: MainViewModel) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp,
    ) {
        NavItem(
            label = "Home",
            selected = state.screen == MainScreen.Home,
            selectedIcon = Icons.Filled.Home,
            unselectedIcon = Icons.Outlined.Home,
            onClick = actions::showHome,
        )
        NavItem(
            label = "Chat",
            selected = state.screen == MainScreen.Chat || state.screen == MainScreen.Conversations,
            selectedIcon = Icons.Filled.Forum,
            unselectedIcon = Icons.Outlined.Forum,
            onClick = actions::showConversations,
        )
        NavItem(
            label = "Settings",
            selected = state.screen == MainScreen.Settings,
            selectedIcon = Icons.Filled.Tune,
            unselectedIcon = Icons.Outlined.Tune,
            onClick = actions::showSettings,
        )
    }
}

@Composable
private fun RowScope.NavItem(
    label: String,
    selected: Boolean,
    selectedIcon: ImageVector,
    unselectedIcon: ImageVector,
    onClick: () -> Unit,
) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = {
            Icon(
                imageVector = if (selected) selectedIcon else unselectedIcon,
                contentDescription = label,
            )
        },
        label = { Text(label) },
        alwaysShowLabel = true,
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedTextColor = MaterialTheme.colorScheme.onSurface,
            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
            indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    )
}
