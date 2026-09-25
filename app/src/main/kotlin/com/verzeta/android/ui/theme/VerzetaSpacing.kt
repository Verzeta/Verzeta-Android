// SPDX-FileCopyrightText: 2026 Aditya Mehra <aix.m@outlook.com>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

/**
 * @file VerzetaSpacing.kt
 * @brief Dimensional token scales for the Android client: VerzetaSpacing
 *        (inter-element padding/gap scale), VerzetaRadius (corner radius
 *        scale including sheet and pill shapes), and VerzetaTouchTarget
 *        (minimum touch / D-pad focus target sizes per device class).
 * @layer Frontend
 * @dependencies Jetpack Compose foundation shapes, Compose ui units.
 */

package com.verzeta.android.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * Spacing scale for padding / spacedBy across the app.
 *
 * The codebase previously sprinkled raw `N.dp` literals for padding
 * (8.dp ×9, 14.dp ×7, 12.dp ×5, 10.dp ×5, 6.dp ×4, 4.dp ×4 — plus a
 * long tail of one-offs); this scale replaces them. Mirroring the
 * desktop client's token-layer strategy: define the scale once, migrate
 * the common values, leave the genuine one-offs as explicit literals.
 *
 * Use [VerzetaSpacing] for INTER-element gaps (paddings, spacedBys).
 * For sizing (avatar boxes, search-field height, icon sizes) keep the
 * explicit literal at the call site — those are dimensional, not
 * spacing.
 */
object VerzetaSpacing {
    /** 2 dp — micro gap, e.g. icon-to-tiny-label. */
    val xxs = 2.dp
    /** 4 dp — tight gap, e.g. inline icon + text. */
    val xs = 4.dp
    /** 8 dp — default row/column gap. The most-used spacing in the app. */
    val sm = 8.dp
    /** 12 dp — comfortable gap between cards / sections. */
    val md = 12.dp
    /** 16 dp — full-width content inset (Material 3 default gutter). */
    val lg = 16.dp
    /** 24 dp — generous gap between page sections. */
    val xl = 24.dp
    /** 32 dp — page-scale breathing room (e.g. above the top section header). */
    val xxl = 32.dp
}

/**
 * Corner radius scale.
 *
 * Before this scale, `RoundedCornerShape(N.dp)` literals dominated:
 * 12.dp ×24 (the lg radius — cards / containers), 10.dp ×12 (md —
 * row buttons / small cards), 8.dp ×10 (sm — chips / icons), 6.dp ×5
 * (xs — small pills), 16.dp ×4 (xl — large cards), 24.dp ×2 (sheet —
 * ModalBottomSheet corners), 999.dp ×2 (pill — search field /
 * full-pill). The values are the desktop client's ThemeController
 * radii scale ported to Android.
 */
object VerzetaRadius {
    /** 4 dp — very tight (chip text padding etc.). */
    val xxs = RoundedCornerShape(4.dp)
    /** 6 dp — small pills / inline chips. */
    val xs = RoundedCornerShape(6.dp)
    /** 8 dp — chip / small card / icon badge corner. */
    val sm = RoundedCornerShape(8.dp)
    /** 10 dp — row button / mid-size badge. */
    val md = RoundedCornerShape(10.dp)
    /** 12 dp — primary card / container corner. Most-used in the app. */
    val lg = RoundedCornerShape(12.dp)
    /** 16 dp — large card / hero container. */
    val xl = RoundedCornerShape(16.dp)
    /** 20 dp — extra-large card (the home connected-host card uses this). */
    val xxl = RoundedCornerShape(20.dp)
    /** 24 dp — ModalBottomSheet corners. */
    val sheet = RoundedCornerShape(24.dp)
    /** 999 dp — pill / fully-rounded (height/2 for circles). */
    val pill = RoundedCornerShape(999.dp)
}

/**
 * Minimum touch / focus target sizes per device class.
 *
 * Material guidance says 48 dp minimum for
 * touch; Android TV's leanback guidelines push to 56-72 dp because
 * users are seated 10 ft from a 1080p display. Use these constants
 * instead of raw `48.dp` / `56.dp` literals in clickable composables
 * (Material 3 Button defaults are 40 dp; if you need to enforce a
 * larger floor, set `Modifier.heightIn(min = VerzetaTouchTarget.minimum)`).
 *
 * Honors §1.5 (spacing — single scale), §1.8 (interactive components —
 * same sizing philosophy across categories), §1.10 (focus/input — TV
 * D-pad targets need to be visible from 10 ft).
 */
object VerzetaTouchTarget {
    /** 48 dp — Material 3 minimum for touch. */
    val minimum = 48.dp
    /** 56 dp — comfortable touch (Material recommends for primary surfaces). */
    val comfortable = 56.dp
    /** 72 dp — Android TV / Leanback minimum for D-pad-focused elements. */
    val tv = 72.dp
}
