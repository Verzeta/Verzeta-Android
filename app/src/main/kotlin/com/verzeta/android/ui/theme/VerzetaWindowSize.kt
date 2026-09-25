// SPDX-FileCopyrightText: 2026 Aditya Mehra <aix.m@outlook.com>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

/**
 * @file VerzetaWindowSize.kt
 * @brief Multi-form-factor adaptive layout dispatch: the VerzetaWindowSize
 *        bucket model (Compact/Medium/Expanded width classes plus TV and
 *        landscape flags), the CompositionLocals screens read to adapt
 *        their layout, and the master-detail pane controller handle.
 * @layer Frontend
 * @dependencies androidx.compose.material3.windowsizeclass, Compose runtime
 *               CompositionLocal, Android Configuration (uiMode /
 *               orientation).
 */

package com.verzeta.android.ui.theme

import android.content.res.Configuration
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration

/**
 * Multi-form-factor adaptive layout dispatch bucket.
 *
 * Every Verzeta-Android screen reads `LocalVerzetaWindowSize.current`
 * to decide its layout for the current device class. Three top-level
 * width buckets matching Material 3's adaptive guidance:
 *
 *  - [WindowWidthSizeClass.Compact]  (< 600 dp)  — phone, foldable outer
 *  - [WindowWidthSizeClass.Medium]   (600-839 dp) — small tablet, foldable inner
 *  - [WindowWidthSizeClass.Expanded] (≥ 840 dp)   — large tablet, TV, desktop
 *
 * The [isTv] flag is computed from
 * `Configuration.uiMode & UI_MODE_TYPE_MASK == UI_MODE_TYPE_TELEVISION`
 * so the TV branch can short-circuit the width dispatch (TV is always
 * Expanded by width but needs a totally different shell with D-pad
 * focus + leanback navigation).
 *
 * Honored by §1.11 (responsive consistency — adapt layout, not visual
 * identity) and §1.10 (focus / input consistency — TV input model
 * differs from touch).
 */
data class VerzetaWindowSize(
    val widthClass: WindowWidthSizeClass,
    val heightClass: WindowHeightSizeClass,
    val isTv: Boolean,
    val isLandscape: Boolean,
    /**
     * True when the window is on a foldable's inner screen, which Jetpack
     * WindowManager reports as a display with a folding feature (the
     * hinge). False on a foldable's cover screen and on ordinary devices.
     */
    val hasFoldingFeature: Boolean = false,
) {
    val isCompact: Boolean get() = widthClass == WindowWidthSizeClass.Compact
    val isMedium: Boolean get() = widthClass == WindowWidthSizeClass.Medium
    val isExpanded: Boolean get() = widthClass == WindowWidthSizeClass.Expanded

    /**
     * Wide enough for the conversation list and the chat side by side.
     *
     * True for an Expanded window in landscape (large tablets, desktop),
     * and for an unfolded foldable's inner screen in either orientation
     * whenever it is at least Medium wide. The inner screen of most
     * foldables is Medium in both orientations, so width alone would
     * never select it. Large tablets held upright stay single-pane.
     * Never true on TV.
     */
    val supportsTwoPane: Boolean
        get() = !isTv && ((isExpanded && isLandscape) || (hasFoldingFeature && !isCompact))
}

/**
 * CompositionLocal that signals when a screen is being rendered INSIDE
 * [com.verzeta.android.ui.shell.AdaptiveContent]'s
 * master-detail layout. ConversationsScreen + ChatScreen read this to
 * suppress their own back-buttons (the back action is meaningless when
 * both panes are visible) and to compress the top bar so two top bars
 * don't stack visually side-by-side.
 *
 * Default false — single-pane mode (the phone / portrait / non-Expanded
 * default).
 */
val LocalIsMasterDetail = androidx.compose.runtime.compositionLocalOf { false }

/**
 * Master-detail list-pane toggle handle.
 *
 * Provided by [com.verzeta.android.ui.shell.AdaptiveContent] when the
 * master-detail layout is active. Carries the current visibility state
 * of the list (conversations) pane + a setter the chat-pane top bar
 * uses to render a leading menu icon that hides / shows the list.
 *
 * Null when not in master-detail (single-pane phone / portrait tablet).
 * The chat top bar checks `?: return null` to decide whether to show
 * the menu icon at all.
 */
data class MasterDetailController(
    val listVisible: Boolean,
    val setListVisible: (Boolean) -> Unit,
)

val LocalMasterDetailController =
    androidx.compose.runtime.compositionLocalOf<MasterDetailController?> { null }

/**
 * CompositionLocal so any composable in the tree can read the current
 * adaptive bucket without re-computing or threading WindowSizeClass
 * through every signature. Provided by [com.verzeta.android.ui.shell.VerzetaApp]
 * at the composition root.
 *
 * Default value if accessed outside a provider — Compact + Medium
 * height + non-TV — so previews / unit tests don't have to wire the
 * provider manually.
 */
val LocalVerzetaWindowSize = compositionLocalOf {
    VerzetaWindowSize(
        widthClass = WindowWidthSizeClass.Compact,
        heightClass = WindowHeightSizeClass.Medium,
        isTv = false,
        isLandscape = false,
    )
}

/**
 * Build the [VerzetaWindowSize] for the current configuration. Read
 * once at the composition root and propagate via
 * [LocalVerzetaWindowSize]. Reads `LocalConfiguration.current` so it
 * recomposes when the window is resized (DeX / ChromeOS / foldable
 * fold-unfold).
 *
 * @param windowSizeClass Width and height classes for the current window.
 * @param hasFoldingFeature Whether WindowManager reports a folding feature
 *        for the current window (an unfolded foldable's inner screen).
 * @returns The adaptive bucket for the current window.
 */
@Composable
@ReadOnlyComposable
fun rememberVerzetaWindowSize(
    windowSizeClass: WindowSizeClass,
    hasFoldingFeature: Boolean,
): VerzetaWindowSize {
    val config = LocalConfiguration.current
    val isTv = (config.uiMode and Configuration.UI_MODE_TYPE_MASK) ==
        Configuration.UI_MODE_TYPE_TELEVISION
    // Orientation comes from the resources Configuration rather than
    // window dimensions because the latter can lag a recomposition on
    // rotation. ORIENTATION_LANDSCAPE is true on landscape phones too;
    // supportsTwoPane excludes them through the Compact height check.
    val isLandscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE
    return VerzetaWindowSize(
        widthClass = windowSizeClass.widthSizeClass,
        heightClass = windowSizeClass.heightSizeClass,
        isTv = isTv,
        isLandscape = isLandscape,
        hasFoldingFeature = hasFoldingFeature,
    )
}
