// SPDX-FileCopyrightText: 2026 Aditya Mehra <aix.m@outlook.com>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

/**
 * @file AssistantThinkingDisclosure.kt
 * @brief Collapsible disclosure that surfaces an assistant message's
 *        captured reasoning ("thinking") sidecar: a clickable header with
 *        character count and chevron, plus an animated, height-bounded
 *        scrolling monospace body. Render-only — never writes back to the
 *        model layer.
 * @layer Frontend
 * @dependencies theme tokens (VerzetaRadius, VerzetaSpacing,
 *               LocalVerzetaWindowSize); Jetpack Compose Material 3 +
 *               material3-window-size-class.
 */

package com.verzeta.android.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.verzeta.android.R
import com.verzeta.android.ui.theme.LocalVerzetaWindowSize
import com.verzeta.android.ui.theme.VerzetaRadius
import com.verzeta.android.ui.theme.VerzetaSpacing
import com.verzeta.android.ui.theme.VerzetaWindowSize
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.material3.Surface

/**
 * Collapsible disclosure that surfaces an assistant message's captured
 * reasoning ("thinking") sidecar.  Mirrors the desktop QML disclosure
 * (`frontend/components/AssistantThinkingDisclosure.qml`) in behaviour
 * and visual structure but ported to Material 3 + the Verzeta token
 * layer.
 *
 * Rendering rules:
 *   - `thinkingContent` blank → composes nothing (no slot occupied).
 *   - Non-blank → a clickable header row showing
 *     "Reasoning (N chars)" with an expand chevron, plus an
 *     animated-visibility body that scrolls bounded to `maxBodyHeight`.
 *   - `startExpanded = true` (the caller passes this for the
 *     empty-content fallback case) makes the body expanded on first
 *     composition.
 *
 * Render-only by contract — this component receives the reasoning
 * text and renders it; it never writes back to the model layer and
 * the field never round-trips out-bound through any wire op. The
 * same render-only invariant holds on the desktop frontend.
 *
 * Honours the project's design-system charter:
 *   - §1 colour: every surface, text and icon resolves through
 *     `MaterialTheme.colorScheme` roles; no hex literals.
 *   - §4 shape: the rounded surface uses the `VerzetaRadius.xs`
 *     token (matches the small-pill scale used by inline chips).
 *   - §5 spacing: every padding / gap is a `VerzetaSpacing` token.
 *   - §6 typography: header uses `labelMedium`, body uses
 *     `bodySmall` in the monospace family for visual distinction
 *     from chat content.
 *   - §7 icons: Material `Icons.Filled.ExpandLess` / `ExpandMore`.
 *   - §8 + §9 + §10 interactive / state / focus: standard
 *     `clickable` + `focusable` with `Role.Button` semantics, a
 *     stateDescription that TalkBack reads as
 *     "Reasoning, expanded" / "Reasoning, collapsed", and a
 *     content-description for the chevron.
 *   - §11 responsive: the caller chooses `maxBodyHeight` from the
 *     current window-size class so the body grows with screen
 *     real-estate.
 *   - §15 component reuse: lives under `ui/components/` as a
 *     single primitive callers compose into bubble surfaces.
 *
 * @param thinkingContent the captured reasoning text.  Empty / blank
 *        composes nothing.
 * @param modifier        outer modifier.
 * @param startExpanded   true on first composition unless the user has
 *                        toggled the disclosure since.  Set by the
 *                        caller for the empty-content fallback case
 *                        ([com.verzeta.android.data.chat.MessageUi.text]
 *                        blank AND `thinkingContent` non-blank) so the
 *                        user sees the reasoning immediately rather
 *                        than an otherwise-empty bubble.
 * @param maxBodyHeight   maximum height for the scrolling body slot
 *                        before vertical scrolling kicks in.  The
 *                        default reads [LocalVerzetaWindowSize] and
 *                        scales per width-class (Compact 220 dp /
 *                        Medium 320 dp / Expanded or TV 420 dp), so
 *                        the body grows with screen real-estate
 *                        without callers having to plumb the
 *                        WindowSizeClass through every signature.
 *                        Callers MAY pass an explicit value for
 *                        previews or scenarios where the bubble's
 *                        outer container has tighter constraints.
 */
@Composable
fun AssistantThinkingDisclosure(
    thinkingContent: String,
    modifier: Modifier = Modifier,
    startExpanded: Boolean = false,
    maxBodyHeight: Dp = defaultDisclosureBodyHeight(),
) {
    if (thinkingContent.isBlank()) return

    // `rememberSaveable` keyed by the content so a new message with
    // its own reasoning doesn't inherit the user's prior expanded
    // state — each row owns its toggle.
    var expanded by rememberSaveable(thinkingContent) { mutableStateOf(startExpanded) }
    val toggleLabel = stringResource(R.string.thinking_disclosure_toggle)
    val stateLabel = stringResource(
        if (expanded) R.string.thinking_disclosure_state_expanded
        else R.string.thinking_disclosure_state_collapsed,
    )

    Column(modifier = modifier.fillMaxWidth()) {
        // Header — clickable, focusable, announces its state to
        // TalkBack via `stateDescription`.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(VerzetaRadius.xs)
                .clickable(
                    onClickLabel = toggleLabel,
                    role = Role.Button,
                ) { expanded = !expanded }
                .focusable()
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                .padding(
                    horizontal = VerzetaSpacing.sm,
                    vertical = VerzetaSpacing.xs,
                )
                .semantics { stateDescription = stateLabel },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(VerzetaSpacing.lg),
            )
            Spacer(Modifier.width(VerzetaSpacing.xs))
            Text(
                text = stringResource(
                    R.string.thinking_disclosure_header,
                    thinkingContent.length,
                ),
                style = MaterialTheme.typography.labelMedium.copy(fontStyle = FontStyle.Italic),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Body — animated visibility, recessed-surface box,
        // vertical-scroll bounded by `maxBodyHeight`.  Same
        // `surfaceContainerLowest` tone as the header so the
        // disclosure reads as one block when expanded, with a faint
        // 1-px outline to separate it from the surrounding bubble
        // surface in both light and dark themes.
        AnimatedVisibility(visible = expanded) {
            Box(
                modifier = Modifier
                    .padding(top = VerzetaSpacing.xs)
                    .fillMaxWidth()
                    .clip(VerzetaRadius.xs)
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        shape = VerzetaRadius.xs,
                    )
                    .heightIn(max = maxBodyHeight)
                    .verticalScroll(rememberScrollState())
                    .padding(VerzetaSpacing.sm),
            ) {
                Text(
                    text = thinkingContent,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/**
 * Default `maxBodyHeight` resolved from the current
 * [LocalVerzetaWindowSize].  Single source of truth for the
 * adaptive scale used by [AssistantThinkingDisclosure] so
 * scaling tweaks land in one place.
 *
 * TV always returns the largest height regardless of width —
 * viewers sit ~10 ft from the screen and benefit from extra
 * vertical breathing room when stepping through long reasoning
 * with a D-pad.  Phones and small tablets stick to a modest
 * height so the surrounding chat list still scrolls naturally
 * when the disclosure is expanded.
 *
 * Returns 220 dp / 320 dp / 420 dp for Compact / Medium /
 * Expanded width classes; 420 dp for TV.
 */
@Composable
@ReadOnlyComposable
private fun defaultDisclosureBodyHeight(): Dp {
    val ws = LocalVerzetaWindowSize.current
    if (ws.isTv) return 420.dp
    return when (ws.widthClass) {
        WindowWidthSizeClass.Compact -> 220.dp
        WindowWidthSizeClass.Medium  -> 320.dp
        else                          -> 420.dp
    }
}

// ---------------------------------------------------------------------------
// Previews — render the disclosure at the three width classes plus the
// thinking-only fallback start-expanded variant so adaptive sizing is
// visible inside Android Studio's Preview pane without having to spin
// up an emulator.
// ---------------------------------------------------------------------------

private const val PreviewThinking = """The user asked me to open a file called \"Hello World python\" in the canvas for editing. I need to use the open_canvas function to do this. However, I need to know the full filename and path. The user said \"Hello World python\" but I should probably make a reasonable assumption about the filename.

Let me call open_canvas with a reasonable filename like \"hello_world.py\" and content that shows the Python Hello World code."""

@Preview(name = "Disclosure — Compact", showBackground = true, widthDp = 412)
@Composable
private fun PreviewDisclosureCompact() {
    PreviewHarness(WindowWidthSizeClass.Compact) {
        AssistantThinkingDisclosure(thinkingContent = PreviewThinking)
    }
}

@Preview(name = "Disclosure — Medium", showBackground = true, widthDp = 720)
@Composable
private fun PreviewDisclosureMedium() {
    PreviewHarness(WindowWidthSizeClass.Medium) {
        AssistantThinkingDisclosure(thinkingContent = PreviewThinking)
    }
}

@Preview(name = "Disclosure — Expanded", showBackground = true, widthDp = 1080)
@Composable
private fun PreviewDisclosureExpanded() {
    PreviewHarness(WindowWidthSizeClass.Expanded) {
        AssistantThinkingDisclosure(thinkingContent = PreviewThinking)
    }
}

@Preview(name = "Disclosure — fallback start-expanded", showBackground = true, widthDp = 412)
@Composable
private fun PreviewDisclosureFallbackExpanded() {
    PreviewHarness(WindowWidthSizeClass.Compact) {
        AssistantThinkingDisclosure(
            thinkingContent = PreviewThinking,
            startExpanded = true,
        )
    }
}

/**
 * Preview harness that provides a [VerzetaWindowSize] override so the
 * previewed composable receives the right adaptive bucket.  Wraps
 * the content in a `MaterialTheme` so the colour roles resolve.
 */
@Composable
private fun PreviewHarness(
    widthClass: WindowWidthSizeClass,
    content: @Composable () -> Unit,
) {
    val windowSize = VerzetaWindowSize(
        widthClass = widthClass,
        heightClass = WindowHeightSizeClass.Medium,
        isTv = false,
        isLandscape = false,
    )
    CompositionLocalProvider(LocalVerzetaWindowSize provides windowSize) {
        androidx.compose.material3.MaterialTheme {
            Surface {
                Box(modifier = Modifier.padding(VerzetaSpacing.lg)) {
                    content()
                }
            }
        }
    }
}
