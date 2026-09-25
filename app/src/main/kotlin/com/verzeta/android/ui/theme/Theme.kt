// SPDX-FileCopyrightText: 2026 Aditya Mehra <aix.m@outlook.com>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

/**
 * @file Theme.kt
 * @brief Application-wide Material 3 theme entry point. Selects Material You
 *        dynamic color on Android 12+ (falling back to the brand palette on
 *        older devices), provides the semantic color extension via a
 *        CompositionLocal, and syncs system-bar icon appearance with the
 *        active light/dark theme.
 * @layer Frontend
 * @dependencies VerzetaLightColorScheme/VerzetaDarkColorScheme and
 *               VerzetaSemanticColors (VerzetaTokens.kt), Jetpack Compose
 *               Material 3, AndroidX Core WindowCompat.
 */

package com.verzeta.android.ui.theme

import android.app.Activity
import android.os.Build
import android.util.Log
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Verzeta Material 3 theme.
 *
 * **ALWAYS FOLLOWS THE SYSTEM THEME** — uses Material You dynamic color
 * on every device that supports it (Android 12+ / API 31+ /
 * Build.VERSION_CODES.S). This is a hard product requirement: the app must
 * never revert to fixed colors on a dynamic-color-capable device.
 *
 * The dynamic color API reads the user's current wallpaper accent +
 * any OEM theme overrides (Samsung One UI, OxygenOS, MIUI all wire into
 * the Material 3 dynamicColorScheme functions). On a pre-Android-12
 * device we fall back to the brand-tuned [VerzetaLightColorScheme] /
 * [VerzetaDarkColorScheme] (orange/slate, derived from the launcher
 * icon).
 *
 * The `dynamicColor` parameter is intentionally absent from the public
 * signature — callers should NOT be able to opt out. If a future
 * Settings toggle is needed, expose it explicitly with documentation
 * tied to that decision.
 *
 * Semantic colors that Material 3 does not model (success, warning,
 * code surfaces, gradient seeds) are always supplied via
 * [LocalVerzetaSemanticColors].
 *
 * Diagnostic: this composable logs which color path was taken at first
 * composition so it's verifiable from `adb logcat -s VerzetaTheme` what
 * the device actually rendered. If the user reports "the app isn't
 * using my system colors" on a known-Android-12+ device, the logcat
 * trace will say `path=dynamicDark` / `path=dynamicLight` confirming
 * the Material You API was called.
 */
@Composable
fun VerzetaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val supportsDynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val colorScheme = when {
        supportsDynamic && darkTheme -> dynamicDarkColorScheme(context)
        supportsDynamic && !darkTheme -> dynamicLightColorScheme(context)
        darkTheme -> VerzetaDarkColorScheme
        else -> VerzetaLightColorScheme
    }
    val semantic = if (darkTheme) VerzetaSemanticDark else VerzetaSemanticLight

    // Log the color path ONCE per theme-state change so subsequent
    // diagnostic runs can confirm Material You actually fired. remember
    // gates the log to one emit per (darkTheme + supportsDynamic)
    // permutation — recompositions don't re-spam logcat.
    remember(darkTheme, supportsDynamic) {
        val path = when {
            supportsDynamic && darkTheme -> "dynamicDark (Material You)"
            supportsDynamic && !darkTheme -> "dynamicLight (Material You)"
            darkTheme -> "fallback VerzetaDarkColorScheme (pre-Android-12)"
            else -> "fallback VerzetaLightColorScheme (pre-Android-12)"
        }
        Log.i(
            "VerzetaTheme",
            "color path=$path · SDK=${Build.VERSION.SDK_INT} · darkTheme=$darkTheme " +
                "· primary=#${Integer.toHexString(colorScheme.primary.toArgb())} " +
                "· surface=#${Integer.toHexString(colorScheme.surface.toArgb())}",
        )
    }

    // Bridge the system-bar appearance to the active theme. With
    // `enableEdgeToEdge` the activity already drew transparent system bars,
    // so we only need to flip the light/dark icon flags here. Setting
    // statusBarColor/navigationBarColor explicitly is intentionally omitted —
    // both are deprecated on Android 15+ in edge-to-edge mode.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    CompositionLocalProvider(LocalVerzetaSemanticColors provides semantic) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content,
        )
    }
}

private fun androidx.compose.ui.graphics.Color.toArgb(): Int =
    android.graphics.Color.argb(
        (alpha * 255).toInt(),
        (red * 255).toInt(),
        (green * 255).toInt(),
        (blue * 255).toInt(),
    )
