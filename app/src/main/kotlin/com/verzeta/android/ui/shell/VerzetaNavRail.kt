// SPDX-FileCopyrightText: 2026 Aditya Mehra <aix.m@outlook.com>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

/**
 * @file VerzetaNavRail.kt
 * @brief Vertical icon-only NavigationRail used on Android TV and landscape
 *        tablets / desktop windows as the primary navigation surface. Same
 *        three destinations as the bottom nav (Home / Chat / Settings),
 *        oriented vertically for D-pad traversal and mirroring the desktop
 *        client's left sidebar.
 * @layer Frontend
 * @dependencies MainViewModel / MainUiState / MainScreen, Jetpack Compose
 *               Material 3 NavigationRail.
 */

package com.verzeta.android.ui.shell

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.verzeta.android.MainScreen
import com.verzeta.android.MainUiState
import com.verzeta.android.MainViewModel

@Composable
fun VerzetaNavRail(state: MainUiState, actions: MainViewModel) {
    NavigationRail(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        RailItem(
            label = "Home",
            selected = state.screen == MainScreen.Home,
            selectedIcon = Icons.Filled.Home,
            unselectedIcon = Icons.Outlined.Home,
            onClick = actions::showHome,
        )
        RailItem(
            label = "Chat",
            selected = state.screen == MainScreen.Chat || state.screen == MainScreen.Conversations,
            selectedIcon = Icons.Filled.Forum,
            unselectedIcon = Icons.Outlined.Forum,
            onClick = actions::showConversations,
        )
        RailItem(
            label = "Settings",
            selected = state.screen == MainScreen.Settings,
            selectedIcon = Icons.Filled.Tune,
            unselectedIcon = Icons.Outlined.Tune,
            onClick = actions::showSettings,
        )
    }
}

@Composable
private fun RailItem(
    label: String,
    selected: Boolean,
    selectedIcon: ImageVector,
    unselectedIcon: ImageVector,
    onClick: () -> Unit,
) {
    NavigationRailItem(
        selected = selected,
        onClick = onClick,
        icon = {
            Icon(
                imageVector = if (selected) selectedIcon else unselectedIcon,
                contentDescription = label,
                modifier = Modifier.size(28.dp),
            )
        },
        // Label intentionally null — desktop QML rail is icon-only.
        label = null,
        colors = NavigationRailItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    )
}
