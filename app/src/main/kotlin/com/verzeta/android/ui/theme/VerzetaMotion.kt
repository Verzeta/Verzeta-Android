// SPDX-FileCopyrightText: 2026 Aditya Mehra <aix.m@outlook.com>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

/**
 * @file VerzetaMotion.kt
 * @brief Motion tokens — the single source of truth for animation durations
 *        and easing curves across the Android client, plus tween factory
 *        helpers for the common fast/medium/slow cases.
 * @layer Frontend
 * @dependencies androidx.compose.animation.core (tween, Easing,
 *               CubicBezierEasing).
 */

package com.verzeta.android.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween

/**
 * Motion tokens for the Android client.
 *
 * Single source of truth for animation durations and easing across the
 * Android client. Previously every `tween(N)` / `spring()` call site
 * picked its own N, producing inconsistent motion feel (some screens
 * snapping at 80 ms, others gliding at 600 ms). The token surface lets
 * a future motion tuning sweep change every animation by editing
 * THIS file, not 30 call sites.
 *
 * Honors §1.13 (animation / motion consistency — one motion language
 * across all surfaces and form factors).
 *
 * Usage —
 * ```kotlin
 * animateContentSize(animationSpec = tween(VerzetaMotion.MEDIUM_MS, easing = VerzetaMotion.STANDARD_EASING))
 * AnimatedVisibility(visible = expanded, enter = fadeIn(VerzetaMotion.fast()))
 * ```
 *
 * Durations roughly follow Material 3's motion scale:
 *   - FAST    150 ms — small, expected (focus rings, button hover).
 *   - MEDIUM  300 ms — content swaps, panel reveals.
 *   - SLOW    500 ms — large surface transitions (full-page).
 *   - DELIBERATE 800 ms — only for confirmations / first-run wizards.
 *
 * Easings follow Material's "standard" + "emphasized" patterns.
 * Standard is symmetric ease-in-out; emphasized accelerates more on
 * exit (used for incoming/highlighted motion).
 */
object VerzetaMotion {
    /** Duration constants in milliseconds. Use with `tween(N)`. */
    const val FAST_MS = 150
    const val MEDIUM_MS = 300
    const val SLOW_MS = 500
    const val DELIBERATE_MS = 800

    /**
     * Material 3 'standard' easing — symmetric. Use for content swaps,
     * size changes, default transitions.
     */
    val STANDARD_EASING: Easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)

    /**
     * Material 3 'emphasized' easing — overshoots slightly on enter.
     * Use for incoming highlighted content (modal sheets, focused
     * surfaces).
     */
    val EMPHASIZED_EASING: Easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)

    /** Tween for fast / small-motion animations (focus rings, hover). */
    fun <T> fast(easing: Easing = STANDARD_EASING) = tween<T>(FAST_MS, easing = easing)

    /** Tween for medium-motion animations (content swaps, panel reveals). */
    fun <T> medium(easing: Easing = STANDARD_EASING) = tween<T>(MEDIUM_MS, easing = easing)

    /** Tween for slow-motion animations (full-page transitions). */
    fun <T> slow(easing: Easing = STANDARD_EASING) = tween<T>(SLOW_MS, easing = easing)
}
