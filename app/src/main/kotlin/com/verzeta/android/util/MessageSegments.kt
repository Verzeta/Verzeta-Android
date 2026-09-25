// SPDX-FileCopyrightText: 2026 Aditya Mehra <aix.m@outlook.com>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

/**
 * @file MessageSegments.kt
 * @brief Splits Markdown message content into alternating prose and
 *        fenced-code segments so the chat renderer can pick the right
 *        delegate per segment, mirroring the host's segmentation logic.
 * @layer Utility
 * @dependencies Kotlin stdlib (Regex) only.
 */

package com.verzeta.android.util

/**
 * Port of the host's `MarkdownConverter::splitContentSegments` from
 * `backend/utils/markdown-utils.cpp:111-174`. Splits a Markdown string into
 * an alternating sequence of text and fenced-code segments. The chat
 * renderer uses this to decide between the prose-markdown delegate and the
 * code-block delegate, exactly like the host's `MessageBubble.qml` does.
 *
 * Fence regex is the same one the host uses:  ```` ```([^\n]*)\n([\s\S]*?)``` ````
 */
fun splitContentSegments(markdown: String): List<MessageSegment> {
    if (markdown.isEmpty()) return emptyList()

    val segments = mutableListOf<MessageSegment>()
    val fence = Regex("```([^\\n]*)\\n([\\s\\S]*?)```", RegexOption.MULTILINE)
    var lastEnd = 0

    fence.findAll(markdown).forEach { match ->
        val matchStart = match.range.first
        if (matchStart > lastEnd) {
            val text = markdown.substring(lastEnd, matchStart).trim()
            if (text.isNotEmpty()) segments += MessageSegment.Text(text)
        }
        segments += MessageSegment.Code(
            language = match.groupValues[1].trim(),
            content = match.groupValues[2],
        )
        lastEnd = match.range.last + 1
    }

    if (lastEnd < markdown.length) {
        val text = markdown.substring(lastEnd).trim()
        if (text.isNotEmpty()) segments += MessageSegment.Text(text)
    }

    if (segments.isEmpty()) {
        segments += MessageSegment.Text(markdown)
    }
    return segments
}

/**
 * One renderable slice of a chat message: either prose Markdown or a
 * fenced code block.
 */
sealed interface MessageSegment {
    /** Prose Markdown content between (or outside) code fences. */
    data class Text(val content: String) : MessageSegment

    /** A fenced code block with its declared language tag (may be empty). */
    data class Code(val language: String, val content: String) : MessageSegment
}
