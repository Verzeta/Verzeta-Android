// SPDX-FileCopyrightText: 2026 Aditya Mehra <aix.m@outlook.com>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

/**
 * @file CodeBlock.kt
 * @brief Dark-surface fenced-code renderer for chat messages: language
 *        label, copy-to-clipboard button, horizontally scrollable
 *        monospace body with language-aware syntax highlighting and a
 *        regex-based fallback for untagged blocks.
 * @layer Frontend
 * @dependencies chat.canvas.SyntaxHighlighter, theme.VerzetaTheme
 *               semantic colours, util.splitContentSegments (segment
 *               contract); Jetpack Compose Material 3.
 */

package com.verzeta.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verzeta.android.ui.chat.canvas.SyntaxHighlighter
import com.verzeta.android.ui.theme.VerzetaTheme

/**
 * Dark-surface code block matching the host's `CodeBlock.qml`. Used for
 * fenced segments produced by [com.verzeta.android.util.splitContentSegments]
 * — the chat renderer routes triple-backtick fences here. The surface is
 * intentionally always dark, regardless of the surrounding chat theme,
 * because (a) that's what the host does and (b) syntax-highlight palettes
 * are tuned for dark backgrounds.
 */
@Composable
fun CodeBlock(
    code: String,
    language: String = "",
    modifier: Modifier = Modifier,
) {
    val semantic = VerzetaTheme.semantic
    val clipboard = LocalClipboardManager.current
    // Route through the language-aware SyntaxHighlighter
    // (which has 18 per-language tokenizers) instead of the previous
    // 4-rule language-agnostic regex that only ever recognised a fixed
    // Kotlin/JS keyword set. Falls back to the legacy regex when the
    // highlighter doesn't know the language so plain code blocks
    // (e.g. fences with no language tag) still render with basic
    // keyword/string colouring.
    val annotated = if (language.isNotBlank()) {
        SyntaxHighlighter.annotated(code, language)
    } else {
        remember(code, semantic) { highlightCodeFallback(code, semantic) }
    }

    Box(modifier = modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(12.dp))
        .background(semantic.codeBackground)) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .padding(start = 14.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = language.uppercase().ifBlank { "CODE" },
                    color = semantic.codeForeground.copy(alpha = 0.5f),
                    style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.4.sp),
                    fontFamily = FontFamily.Monospace,
                )
                Spacer(Modifier.weight(1f))
                IconButton(
                    onClick = { clipboard.setText(AnnotatedString(code)) },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        Icons.Filled.ContentCopy,
                        contentDescription = "Copy code",
                        tint = semantic.codeForeground.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Text(
                    text = annotated,
                    color = semantic.codeForeground,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                    ),
                )
            }
        }
    }
}

/**
 * Language-agnostic regex highlighter for fenced code blocks that
 * arrived with no language tag. The primary path is
 * [SyntaxHighlighter.annotated] (18 per-language tokenizers); this
 * stays as a reasonable best-effort for untagged blocks since dropping
 * all colouring there would be a regression.
 */
private fun highlightCodeFallback(code: String, semantic: com.verzeta.android.ui.theme.VerzetaSemanticColors): AnnotatedString {
    val keyword = Regex(
        "\\b(fun|val|var|class|interface|object|sealed|data|return|import|" +
            "package|private|public|internal|protected|abstract|override|open|" +
            "companion|if|else|when|while|for|in|is|as|null|true|false|this|super|" +
            "def|lambda|async|await|function|const|let|export|from|yield|" +
            "type|struct|enum|impl|trait|pub|use|let|mut)\\b",
    )
    val string = Regex("\"([^\"\\\\]|\\\\.)*\"|'([^'\\\\]|\\\\.)*'")
    val number = Regex("\\b(\\d+(?:\\.\\d+)?)\\b")
    val comment = Regex("//[^\\n]*|#[^\\n]*|/\\*[\\s\\S]*?\\*/")

    return buildAnnotatedString {
        append(code)
        comment.findAll(code).forEach {
            addStyle(
                SpanStyle(color = semantic.codeForeground.copy(alpha = 0.45f)),
                it.range.first, it.range.last + 1,
            )
        }
        string.findAll(code).forEach {
            addStyle(SpanStyle(color = semantic.codeString), it.range.first, it.range.last + 1)
        }
        number.findAll(code).forEach {
            addStyle(SpanStyle(color = semantic.codeNumber), it.range.first, it.range.last + 1)
        }
        keyword.findAll(code).forEach {
            addStyle(SpanStyle(color = semantic.codeKeyword), it.range.first, it.range.last + 1)
        }
    }
}

