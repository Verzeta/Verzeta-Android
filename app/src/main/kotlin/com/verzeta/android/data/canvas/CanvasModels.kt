// SPDX-FileCopyrightText: 2026 Aditya Mehra <aix.m@outlook.com>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

/**
 * @file CanvasModels.kt
 * @brief Canvas, console, and run-state models for the canvas editor:
 *        canvas rows, AI actions, in-process tools and their results,
 *        console output lines, and the sandbox/run flags.
 * @layer Model
 * @dependencies None — pure Kotlin data declarations.
 */

package com.verzeta.android.data.canvas

/**
 * One canvas row. Wire shape from
 * `CanvasService::canvasRowToMap` (canvas-service.cpp:49) — camelCase
 * because the desktop's QML bindings consume the same map verbatim.
 *
 * **Run + Send-to-IDE always execute on the host.** The canvas runner
 * uses bubblewrap (Linux-only), so Android never executes anything
 * locally — it requests, the host runs, the host streams output back
 * via `canvas.run.line` events. `canvas.run.send_to_ide` opens the
 * canvas's disk-mirror file in the host's IDE; Android shows a toast.
 */
data class CanvasUi(
    val id: String,
    val conversationId: String = "",
    val filename: String = "",
    val language: String = "",
    val content: String = "",
    val revision: Int = 0,
    val isArchived: Boolean = false,
    val sourceMsgId: String = "",
    val createdAt: String = "",
    val updatedAt: String = "",
    val byteSize: Long = 0L,
    val lineCount: Int = 0,
)

/**
 * AI action descriptor from `canvas.ai_actions.list`. `primary == true`
 * renders as an inline chip; the rest go into a "More" overflow menu.
 * `submenu` non-empty means the chip opens a sub-picker before
 * triggering — pass the chosen value as `submenu_choice`.
 */
data class CanvasAiActionUi(
    val id: String,
    val label: String,
    val iconName: String = "",
    val primary: Boolean = false,
    val submenu: List<String> = emptyList(),
    val description: String = "",
)

/**
 * In-process tool descriptor from `canvas.tools.list` (Validate /
 * Format / etc.). `enabled=false` greys the menu item without hiding
 * it (matches the desktop behaviour where Format only enables for
 * JSON-like languages).
 */
data class CanvasToolUi(
    val id: String,
    val label: String,
    val description: String = "",
    val enabled: Boolean = true,
    val language: String = "",
)

/**
 * Result of `canvas.tools.run`. `mutated == true` means the canvas
 * content changed (a `canvas.updated` event will follow). Validation
 * actions return diagnostic info in [errorLine] / [errorOffset].
 */
data class CanvasToolResultUi(
    val ok: Boolean,
    val message: String = "",
    val mutated: Boolean = false,
    val errorLine: Int = -1,
    val errorOffset: Int = -1,
)

/**
 * One line from the host's `canvas.run.line` event. The host caps at
 * 50 rows globally + 500 chars per line; client doesn't need extra
 * trimming. Colour by [kind].
 */
data class ConsoleLineUi(
    val kind: ConsoleLineKind,
    val text: String,
    val timestamp: String,
)

/**
 * Console-line category used to colour output rows: process stdout/stderr,
 * lines the user typed at a prompt, system notices, and the two exit outcomes.
 * [Unknown] absorbs wire values this client version doesn't recognise.
 */
enum class ConsoleLineKind(val wireValue: String) {
    System("system"),
    Stdout("stdout"),
    Stderr("stderr"),
    /**
     * A line the user typed at an `input()` prompt. The host echoes it into the
     * console itself, so clients render this row rather than echoing locally.
     */
    Input("input"),
    ExitOk("exit-ok"),
    ExitErr("exit-err"),
    Unknown("");

    companion object {
        fun fromWire(value: String?): ConsoleLineKind =
            entries.firstOrNull { it.wireValue == value } ?: Unknown
    }
}

/**
 * Sandbox state from `canvas.run.sandbox_state`. When `available ==
 * false` the editor shows a banner with `blockerReason` text.
 */
data class CanvasSandboxStateUi(
    val available: Boolean = true,
    val blockerReason: String = "",
)

/** Coalesced (running) flag from `canvas.run.state`. */
data class CanvasRunStateUi(
    val running: Boolean = false,
)
