// SPDX-FileCopyrightText: 2026 Aditya Mehra <aix.m@outlook.com>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

/**
 * @file TemplateBanner.kt
 * @brief Procedural Canvas-based banner art for project room template and
 *        folder cards, rendering one of eight named geometry patterns tinted
 *        by a configurable HSL hue.
 * @layer UI
 * @dependencies Jetpack Compose Canvas, kotlin.math
 */

package com.verzeta.android.ui.projectrooms

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Procedural banner art for a Project Rooms template / room card.
 *
 * Direct Android port of the desktop's
 * `frontend/components/ProjectTemplateBanner.qml` — same eight
 * geometryKinds (`circles` / `grid` / `triangle` / `wave` / `bars` /
 * `arrows` / `spiral` / `dots`), same HSL-from-baseHue colour scheme.
 *
 * Renders in a single Compose Canvas pass clipped to the supplied
 * corner radius. No bitmap caching needed — geometries are cheap and
 * Compose recomposition only re-runs the draw when params change.
 *
 * @param geometryKind  one of the 8 named patterns (case-sensitive).
 *                       Unknown values render layers 1+2 only.
 * @param baseHue        HSL hue 0..360 for the foreground geometry.
 * @param cornerRadius   round-corner clip; 0 = square.
 * @param baseColor      bottom-layer flat fill (matches the desktop's
 *                       Kirigami.Theme.alternateBackgroundColor).
 */
@Composable
fun TemplateBanner(
    geometryKind: String,
    baseHue: Int,
    cornerRadius: Dp,
    baseColor: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        val cornerPx = cornerRadius.toPx()
        val clip = Path().apply {
            addRoundRect(
                androidx.compose.ui.geometry.RoundRect(
                    rect = androidx.compose.ui.geometry.Rect(Offset.Zero, Size(w, h)),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerPx, cornerPx),
                ),
            )
        }

        clipPath(clip) {
            // Layer 1 — solid base.
            drawRect(color = baseColor, size = Size(w, h))

            // Layer 2 — diagonal hairline stripes.
            // Derived from Color.Black + alpha to satisfy the design-system
            // invariant (no raw hex Color(0x...) literals outside the token file).
            val stripeColor = Color.Black.copy(alpha = 0.15f)
            var sx = -h
            while (sx < w + h) {
                drawLine(
                    color = stripeColor,
                    start = Offset(sx, 0f),
                    end = Offset(sx + h, h),
                    strokeWidth = 1.0f,
                )
                sx += 10f
            }

            // Layer 3 — foreground geometry, tinted by baseHue.
            val shape = hslColor(baseHue, 0.55f, 0.55f, 1.0f)
            val cx = w * 0.5f
            val cy = h * 0.55f

            when (geometryKind) {
                "circles" -> {
                    val baseR = min(w, h) * 0.32f
                    for (i in 1..4) {
                        val r = baseR * i
                        val alpha = 1.0f - (i - 1) * 0.22f
                        drawCircle(
                            color = shape.copy(alpha = alpha * 0.9f),
                            radius = r,
                            center = Offset(cx, cy),
                            style = Stroke(width = 2.0f),
                        )
                    }
                }

                "grid" -> {
                    val cols = 6
                    val rows = 4
                    val gx0 = w * 0.15f
                    val gx1 = w * 0.85f
                    val gy0 = h * 0.20f
                    val gy1 = h * 0.85f
                    val gridColor = shape.copy(alpha = 0.9f)
                    for (c in 0..cols) {
                        val px = gx0 + (gx1 - gx0) * (c / cols.toFloat())
                        drawLine(gridColor, Offset(px, gy0), Offset(px, gy1), 1.5f)
                    }
                    for (r in 0..rows) {
                        val py = gy0 + (gy1 - gy0) * (r / rows.toFloat())
                        drawLine(gridColor, Offset(gx0, py), Offset(gx1, py), 1.5f)
                    }
                }

                "triangle" -> {
                    val tx = w * 0.62f
                    val ty = h * 0.20f
                    val sz = min(w, h) * 0.55f
                    val path = Path().apply {
                        moveTo(tx, ty)
                        lineTo(tx - sz * 0.5f, ty + sz * 0.866f)
                        lineTo(tx + sz * 0.5f, ty + sz * 0.866f)
                        close()
                    }
                    drawPath(path = path, color = shape.copy(alpha = 0.85f))
                }

                "wave" -> {
                    for (k in 0 until 4) {
                        val amp = h * 0.10f
                        val midY = h * (0.30f + k * 0.13f)
                        val alpha = 1.0f - k * 0.18f
                        val path = Path().apply {
                            moveTo(0f, midY)
                            var xx = 0f
                            while (xx <= w) {
                                val yy = midY + sin((xx / w) * PI.toFloat() * 3 + k) * amp
                                lineTo(xx, yy)
                                xx += 4f
                            }
                        }
                        drawPath(
                            path = path,
                            color = shape.copy(alpha = alpha * 0.85f),
                            style = Stroke(width = 2.5f),
                        )
                    }
                }

                "bars" -> {
                    val nBars = 8
                    val barW = (w * 0.7f) / nBars
                    val barX0 = w * 0.18f
                    val barBottom = h * 0.85f
                    val heights = floatArrayOf(0.30f, 0.55f, 0.42f, 0.70f, 0.50f, 0.85f, 0.60f, 0.78f)
                    for (b in 0 until nBars) {
                        val bh = (h * 0.55f) * heights[b]
                        drawRect(
                            color = shape.copy(alpha = 0.85f),
                            topLeft = Offset(barX0 + b * barW + 2f, barBottom - bh),
                            size = Size(barW - 4f, bh),
                        )
                    }
                }

                "arrows" -> {
                    for (a in 0 until 5) {
                        val ax = w * (0.20f + a * 0.13f)
                        val ay = h * 0.55f
                        val asz = min(w, h) * 0.18f
                        val alpha = 0.4f + a * 0.13f
                        val color = shape.copy(alpha = alpha * 0.9f)
                        drawLine(color, Offset(ax - asz * 0.5f, ay - asz * 0.5f),
                                  Offset(ax + asz * 0.5f, ay), 3.0f)
                        drawLine(color, Offset(ax + asz * 0.5f, ay),
                                  Offset(ax - asz * 0.5f, ay + asz * 0.5f), 3.0f)
                    }
                }

                "spiral" -> {
                    val maxR = min(w, h) * 0.4f
                    val path = Path()
                    var t = 0f
                    val tMax = (PI * 6).toFloat()
                    while (t < tMax) {
                        val r = maxR * (t / tMax)
                        val px = cx + r * cos(t)
                        val py = cy + r * sin(t)
                        if (t == 0f) path.moveTo(px, py) else path.lineTo(px, py)
                        t += 0.08f
                    }
                    drawPath(
                        path = path,
                        color = shape.copy(alpha = 0.95f),
                        style = Stroke(width = 2.0f),
                    )
                }

                "dots" -> {
                    val dx0 = w * 0.12f
                    val dy0 = h * 0.18f
                    val dx1 = w * 0.88f
                    val dy1 = h * 0.82f
                    val dcols = 12
                    val drows = 6
                    for (dc in 0 until dcols) {
                        for (dr in 0 until drows) {
                            val dpx = dx0 + (dx1 - dx0) * (dc / (dcols - 1).toFloat())
                            val dpy = dy0 + (dy1 - dy0) * (dr / (drows - 1).toFloat())
                            val sz = 3.0f * (0.6f + 0.6f * sin((dc + dr * 1.3f) * 0.6f))
                            drawCircle(
                                color = shape.copy(alpha = 0.9f),
                                radius = sz,
                                center = Offset(dpx, dpy),
                            )
                        }
                    }
                }

                else -> { /* unknown kind → leave layers 1+2 only */ }
            }
        }
    }
}

/**
 * The 8 built-in designs that pair with a baseHue. Identical to the
 * desktop's `_designs` array in ProjectQuickStartSheet.qml. Used by the
 * Quick Start screen's ◀ ▶ design cycler.
 */
data class TemplateDesign(val geometryKind: String, val baseHue: Int)

val TemplateDesigns: List<TemplateDesign> = listOf(
    TemplateDesign("circles", 210),
    TemplateDesign("wave", 180),
    TemplateDesign("grid", 130),
    TemplateDesign("bars", 240),
    TemplateDesign("triangle", 30),
    TemplateDesign("arrows", 300),
    TemplateDesign("spiral", 45),
    TemplateDesign("dots", 270),
)

/** Index of the design whose geometryKind matches; 0 if none. */
fun indexForDesign(geometryKind: String): Int {
    val idx = TemplateDesigns.indexOfFirst { it.geometryKind == geometryKind }
    return if (idx >= 0) idx else 0
}

/**
 * HSL → ARGB Color. kotlinx + Compose have no built-in HSL helper, so
 * we port the standard CSS / Qt formula. Hue in 0..360, saturation +
 * lightness + alpha in 0..1.
 */
private fun hslColor(hueDeg: Int, saturation: Float, lightness: Float, alpha: Float): Color {
    val h = (hueDeg % 360 + 360) % 360 / 360.0f
    val s = saturation.coerceIn(0f, 1f)
    val l = lightness.coerceIn(0f, 1f)
    if (s == 0f) {
        return Color(l, l, l, alpha)
    }
    val q = if (l < 0.5f) l * (1f + s) else l + s - l * s
    val p = 2f * l - q
    val r = hueToRgb(p, q, h + 1f / 3f)
    val g = hueToRgb(p, q, h)
    val b = hueToRgb(p, q, h - 1f / 3f)
    return Color(r, g, b, alpha)
}

private fun hueToRgb(p: Float, q: Float, hIn: Float): Float {
    var h = hIn
    if (h < 0f) h += 1f
    if (h > 1f) h -= 1f
    return when {
        h < 1f / 6f -> p + (q - p) * 6f * h
        h < 1f / 2f -> q
        h < 2f / 3f -> p + (q - p) * (2f / 3f - h) * 6f
        else -> p
    }
}
