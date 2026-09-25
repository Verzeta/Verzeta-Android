// SPDX-FileCopyrightText: 2026 Aditya Mehra <aix.m@outlook.com>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

/**
 * @file VerzetaTokens.kt
 * @brief Brand color tokens for the app: the light/dark Material 3 fallback
 *        color schemes used when dynamic color is unavailable, the
 *        VerzetaSemanticColors extension (status, code-surface, and gradient
 *        colors Material 3 does not model), the VerzetaTheme accessor object,
 *        and the VerzetaStatus state enum with its color mapping.
 * @layer Frontend
 * @dependencies Jetpack Compose Material 3 color schemes, Compose runtime
 *               CompositionLocal.
 */

package com.verzeta.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

// Verzeta Material 3 role tokens. Mirror the production design tokens and are
// used as the fallback palette when dynamic color is unavailable; semantic
// extensions (success/warning/info, code
// surfaces, primary gradient) live in [VerzetaSemanticColors] and are always
// applied regardless of dynamic color.


internal val VerzetaLightColorScheme = lightColorScheme(
    primary = Color(0xFFCD6700),                  // brand orange — deep for light contrast
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDDB7),         // soft orange tint
    onPrimaryContainer = Color(0xFF301400),
    secondary = Color(0xFF765749),                // warm secondary (brown-orange family)
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDCC7),
    onSecondaryContainer = Color(0xFF2B1709),
    tertiary = Color(0xFF655F44),                 // muted ochre
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFEEE3BF),
    onTertiaryContainer = Color(0xFF201C07),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFFFBFF),               // warm off-white
    onBackground = Color(0xFF201A17),
    surface = Color(0xFFFFFBFF),
    onSurface = Color(0xFF201A17),
    surfaceVariant = Color(0xFFF4DED3),
    onSurfaceVariant = Color(0xFF52443D),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFCF1EC),
    surfaceContainer = Color(0xFFF6EBE5),
    surfaceContainerHigh = Color(0xFFF0E5DF),
    surfaceContainerHighest = Color(0xFFEAE0DA),
    outline = Color(0xFF85746B),
    outlineVariant = Color(0xFFD7C2B8),
)

internal val VerzetaDarkColorScheme = darkColorScheme(
    primary = Color(0xFFFFB466),                  // brand orange — lighter for dark legibility, vivid
    onPrimary = Color(0xFF4A2800),
    primaryContainer = Color(0xFF6A3C00),         // deeper orange container
    onPrimaryContainer = Color(0xFFFFDDB7),
    secondary = Color(0xFFE6BFAB),
    onSecondary = Color(0xFF432B1D),
    secondaryContainer = Color(0xFF5C4131),
    onSecondaryContainer = Color(0xFFFFDCC7),
    tertiary = Color(0xFFD2C7A4),
    onTertiary = Color(0xFF36311A),
    tertiaryContainer = Color(0xFF4D472E),
    onTertiaryContainer = Color(0xFFEEE3BF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF10141B),               // launcher-icon slate
    onBackground = Color(0xFFEDE0D9),
    surface = Color(0xFF10141B),
    onSurface = Color(0xFFEDE0D9),
    surfaceVariant = Color(0xFF52443D),
    onSurfaceVariant = Color(0xFFD7C2B8),
    surfaceContainerLowest = Color(0xFF0A0D13),
    surfaceContainerLow = Color(0xFF181C24),
    surfaceContainer = Color(0xFF1C2029),
    surfaceContainerHigh = Color(0xFF262A33),
    surfaceContainerHighest = Color(0xFF30343E),
    outline = Color(0xFFA08D83),
    outlineVariant = Color(0xFF52443D),
)

/**
 * Semantic colors that the Material 3 ColorScheme does not cover directly: status
 * dots, code editor surface, gradient mark seed colors, and positive container.
 */
data class VerzetaSemanticColors(
    val success: Color,
    val warning: Color,
    val info: Color,
    val positiveContainer: Color,
    val onPositiveContainer: Color,
    val codeBackground: Color,
    val codeForeground: Color,
    val codeAccent: Color,
    val codeKeyword: Color,
    val codeString: Color,
    val codeNumber: Color,
    val gradientStart: Color,
    val gradientEnd: Color,
)


internal val VerzetaSemanticLight = VerzetaSemanticColors(
    success = Color(0xFF1F8A5B),
    warning = Color(0xFFB8860B),
    info = Color(0xFFCD6700),                     // brand orange (matches primary)
    positiveContainer = Color(0xFFC7EFD8),
    onPositiveContainer = Color(0xFF002115),
    codeBackground = Color(0xFF0E1117),
    codeForeground = Color(0xFFE6EBF2),
    codeAccent = Color(0xFFFFB466),               // brand orange highlight
    codeKeyword = Color(0xFFFFB466),
    codeString = Color(0xFFE6BFAB),
    codeNumber = Color(0xFFC8B6FF),
    gradientStart = Color(0xFFE88F00),            // brand orange (light end)
    gradientEnd = Color(0xFFCD6700),              // brand orange (deep end)
)

internal val VerzetaSemanticDark = VerzetaSemanticColors(
    success = Color(0xFF7BD9A8),
    warning = Color(0xFFF4C36A),
    info = Color(0xFFFFB466),                     // brand orange (matches primary)
    positiveContainer = Color(0xFF0C4A30),
    onPositiveContainer = Color(0xFFC7EFD8),
    codeBackground = Color(0xFF080B11),
    codeForeground = Color(0xFFE6EBF2),
    codeAccent = Color(0xFFFFB466),               // brand orange highlight
    codeKeyword = Color(0xFFFFB466),
    codeString = Color(0xFFE6BFAB),
    codeNumber = Color(0xFFC8B6FF),
    gradientStart = Color(0xFFFFB466),            // brand orange (light end)
    gradientEnd = Color(0xFFE88F00),              // brand orange (deep end)
)

internal val LocalVerzetaSemanticColors = compositionLocalOf<VerzetaSemanticColors> {
    error("VerzetaSemanticColors not provided. Wrap composables in VerzetaTheme.")
}

/**
 * Static accessor for Verzeta theme extensions that sit alongside
 * MaterialTheme — currently the semantic color set provided by VerzetaTheme's
 * CompositionLocal. Use `VerzetaTheme.semantic` inside any composable wrapped
 * in the app theme.
 */
object VerzetaTheme {
    val semantic: VerzetaSemanticColors
        @Composable
        @ReadOnlyComposable
        get() = LocalVerzetaSemanticColors.current
}

/**
 * Status dot color for a host or skill state.
 */
@Composable
@ReadOnlyComposable
fun statusColor(status: VerzetaStatus): Color = when (status) {
    VerzetaStatus.Ok -> VerzetaTheme.semantic.success
    VerzetaStatus.Warning -> VerzetaTheme.semantic.warning
    VerzetaStatus.Error -> MaterialTheme.colorScheme.error
    VerzetaStatus.Info -> VerzetaTheme.semantic.info
    VerzetaStatus.Neutral -> MaterialTheme.colorScheme.outline
}

/**
 * High-level state of a host, skill, or other monitored entity, used to pick
 * a status dot / accent color via [statusColor].
 */
enum class VerzetaStatus {
    Ok,
    Warning,
    Error,
    Info,
    Neutral,
}
