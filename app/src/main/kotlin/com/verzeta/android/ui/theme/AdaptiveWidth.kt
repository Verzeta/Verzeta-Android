// SPDX-FileCopyrightText: 2026 Aditya Mehra <aix.m@outlook.com>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

/**
 * @file AdaptiveWidth.kt
 * @brief Adaptive-width composition helpers: a max-width modifier that caps
 *        top-level screen content to a readable line length on Medium and
 *        Expanded window classes, and an adaptive column count for catalog
 *        grids. Keeps phone-first layouts from stretching edge-to-edge on
 *        tablets and desktop windows.
 * @layer Frontend
 * @dependencies LocalVerzetaWindowSize (VerzetaWindowSize.kt), Jetpack
 *               Compose foundation layout.
 */

package com.verzeta.android.ui.theme

import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Adaptive content-width cap for top-level screens.
 *
 * On tablets / desktop windows the phone-first single-column layouts
 * stretch edge-to-edge by default, which makes the app look like an
 * oversized phone. Apply this modifier to the outer scroller of any
 * top-level screen (HomeScreen, SettingsScreen, AboutScreen,
 * ProjectRoomsLandingScreen, etc.) and the content caps at a readable
 * line length on Medium / Expanded.
 *
 * Caps —
 *  - Compact (< 600 dp): no cap, content fills the screen as today.
 *  - Medium  (600-839 dp): 680 dp (Material reading-line guidance for
 *    medium screens).
 *  - Expanded (≥ 840 dp): 920 dp (large surfaces but still readable;
 *    above ~960 dp lines become uncomfortably wide).
 *
 * Used inside a centered parent (`Box(contentAlignment = Alignment.TopCenter)`)
 * so the capped content sits in the middle of the screen. The TV branch
 * is Compact-equivalent here as well — the TV shell sets its own
 * constraints.
 *
 * Honors §1.11 (responsive consistency — adapt layout, not visual
 * identity) and §1.12 (display scaling — content stays comfortable at
 * any density / display size).
 */
@Composable
@ReadOnlyComposable
fun adaptiveContentMaxWidth(): Modifier {
    val window = LocalVerzetaWindowSize.current
    if (window.isTv) return Modifier  // TV shell handles its own width
    return when {
        window.isExpanded -> Modifier.widthIn(max = 920.dp)
        window.isMedium  -> Modifier.widthIn(max = 680.dp)
        else             -> Modifier  // Compact — no cap, phone fills
    }
}

/**
 * Adaptive column count for catalog grids (Project Rooms templates,
 * Folders, etc.). Returns 1 on Compact, 2 on Medium, 3 on Expanded.
 *
 * Honors §1.11 (responsive — adapt layout, not identity).
 */
@Composable
@ReadOnlyComposable
fun adaptiveCatalogColumns(): Int {
    val window = LocalVerzetaWindowSize.current
    return when {
        window.isExpanded -> 3
        window.isMedium  -> 2
        else             -> 1
    }
}
