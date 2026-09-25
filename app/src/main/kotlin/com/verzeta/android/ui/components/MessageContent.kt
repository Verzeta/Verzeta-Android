// SPDX-FileCopyrightText: 2026 Aditya Mehra <aix.m@outlook.com>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

/**
 * @file MessageContent.kt
 * @brief Renders a chat message body by splitting raw markdown into
 *        alternating prose and fenced-code segments, delegating prose to
 *        MarkdownText and code to CodeBlock. Mirrors the host frontend's
 *        MessageBubble delegate selection so Android matches the desktop
 *        visual structure.
 * @layer Frontend
 * @dependencies MarkdownText, CodeBlock, util.splitContentSegments;
 *               Jetpack Compose foundation + Material 3.
 */

package com.verzeta.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.verzeta.android.util.MessageSegment
import com.verzeta.android.util.splitContentSegments

/**
 * Render a message body. Mirrors `MessageBubble.qml` from the host
 * frontend: split the raw markdown into alternating prose/code segments
 * with [splitContentSegments] (port of
 * `MarkdownConverter::splitContentSegments`), then render prose with
 * [MarkdownText] and fenced code with [CodeBlock]. Matches the host's
 * delegate selection one-for-one — the user gets the same visual
 * structure on Android as on the desktop.
 */
@Composable
fun MessageContent(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    color: Color = LocalContentColor.current,
) {
    val segments = remember(text) { splitContentSegments(text) }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        segments.forEach { segment ->
            when (segment) {
                is MessageSegment.Text -> MarkdownText(
                    text = segment.content,
                    style = style,
                    color = color,
                )
                is MessageSegment.Code -> CodeBlock(
                    code = segment.content,
                    language = segment.language,
                )
            }
        }
    }
}
