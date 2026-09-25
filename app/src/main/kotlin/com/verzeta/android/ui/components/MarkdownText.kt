// SPDX-FileCopyrightText: 2026 Aditya Mehra <aix.m@outlook.com>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

/**
 * @file MarkdownText.kt
 * @brief Inline Markdown renderer for prose message segments. Implements
 *        the subset the chat needs (headings, bullet/numbered lists,
 *        block quotes, bold, italic, inline code, strikethrough, links)
 *        as AnnotatedString blocks; fenced code blocks are handled
 *        separately by CodeBlock.
 * @layer Frontend
 * @dependencies util.splitContentSegments (segment contract); Jetpack
 *               Compose text + Material 3.
 */

package com.verzeta.android.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Inline Markdown renderer for prose segments produced by
 * [com.verzeta.android.util.splitContentSegments]. Mirrors what the host's
 * `MarkdownText.qml` shows in the chat bubble — the host uses Qt's
 * RichText engine fed through `MarkdownConverter::toHtml` (which is
 * `QTextDocument::setMarkdown`); on Android we don't have that, so this
 * file implements the subset we actually need:
 *
 *   - Headings: lines starting with `#`, `##`, `###`
 *   - Bullet lists: lines starting with `- ` / `* ` / `+ `
 *   - Numbered lists: lines starting with `1. ` / `2. ` ...
 *   - Block quotes: lines starting with `> `
 *   - Bold: `**bold**` / `__bold__`
 *   - Italic: `*ital*` / `_ital_`
 *   - Inline code: `` `code` ``
 *   - Strikethrough: `~~strike~~`
 *   - Links: `[label](url)` (rendered as primary-coloured underlined text;
 *     URL navigation isn't wired yet — the user can long-press the
 *     selected text)
 *
 * Fenced code blocks are explicitly NOT handled here — the segment
 * splitter peels them off into their own [MessageSegment.Code] entries
 * which are rendered by [CodeBlock]. This keeps the markdown grammar here
 * tight and avoids re-parsing fences twice.
 */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    color: Color = LocalContentColor.current,
) {
    val theme = MaterialTheme.colorScheme
    val annotated = remember(text, theme) {
        renderMarkdown(text, theme.primary, theme.surfaceContainerHigh, theme.onSurface)
    }
    Column(modifier = modifier) {
        annotated.forEach { block ->
            BlockRenderer(block = block, style = style, color = color)
        }
    }
}

@Composable
private fun BlockRenderer(block: MarkdownBlock, style: TextStyle, color: Color) {
    when (block) {
        is MarkdownBlock.Heading -> {
            val resolvedStyle = when (block.level) {
                1 -> MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                2 -> MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
                else -> MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
            }
            androidx.compose.material3.Text(
                text = block.spans,
                style = resolvedStyle,
                color = color,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
            )
        }
        is MarkdownBlock.Paragraph -> {
            androidx.compose.material3.Text(
                text = block.spans,
                style = style,
                color = color,
                modifier = Modifier.padding(vertical = 2.dp),
            )
        }
        is MarkdownBlock.BulletItem -> {
            androidx.compose.material3.Text(
                text = bulletPrefix("•", block.spans),
                style = style,
                color = color,
                modifier = Modifier.padding(start = 4.dp, top = 1.dp, bottom = 1.dp),
            )
        }
        is MarkdownBlock.NumberedItem -> {
            androidx.compose.material3.Text(
                text = bulletPrefix("${block.index}.", block.spans),
                style = style,
                color = color,
                modifier = Modifier.padding(start = 4.dp, top = 1.dp, bottom = 1.dp),
            )
        }
        is MarkdownBlock.Quote -> {
            androidx.compose.material3.Text(
                text = block.spans,
                style = style.copy(fontStyle = FontStyle.Italic),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(
                    PaddingValues(start = 12.dp, top = 4.dp, bottom = 4.dp),
                ),
            )
        }
    }
}

private fun bulletPrefix(marker: String, body: AnnotatedString): AnnotatedString =
    buildAnnotatedString {
        append("$marker  ")
        append(body)
    }

/**
 * One block-level Markdown element produced by [renderMarkdown]; each
 * variant carries its already-styled inline spans.
 */
private sealed interface MarkdownBlock {
    val spans: AnnotatedString

    /** Heading block (`#` / `##` / `###`); level is clamped to 1–3. */
    data class Heading(val level: Int, override val spans: AnnotatedString) : MarkdownBlock

    /** Plain prose paragraph (consecutive non-marker lines joined). */
    data class Paragraph(override val spans: AnnotatedString) : MarkdownBlock

    /** Single bullet-list item (`- ` / `* ` / `+ `). */
    data class BulletItem(override val spans: AnnotatedString) : MarkdownBlock

    /** Single numbered-list item; index is the literal number parsed from source. */
    data class NumberedItem(val index: Int, override val spans: AnnotatedString) : MarkdownBlock

    /** Block-quote line (`> `), rendered italic in the variant colour. */
    data class Quote(override val spans: AnnotatedString) : MarkdownBlock
}

private val headingPattern = Regex("^(#{1,6})\\s+(.*)$")
private val bulletPattern = Regex("^[-*+]\\s+(.*)$")
private val numberedPattern = Regex("^(\\d+)\\.\\s+(.*)$")

private fun renderMarkdown(
    text: String,
    primary: Color,
    inlineCodeBg: Color,
    inlineCodeFg: Color,
): List<MarkdownBlock> {
    val lines = text.lines()
    val blocks = mutableListOf<MarkdownBlock>()
    val paragraphBuf = StringBuilder()

    fun flushParagraph() {
        if (paragraphBuf.isNotEmpty()) {
            blocks += MarkdownBlock.Paragraph(
                renderInline(paragraphBuf.toString().trimEnd(), primary, inlineCodeBg, inlineCodeFg),
            )
            paragraphBuf.clear()
        }
    }

    for (rawLine in lines) {
        val line = rawLine
        when {
            line.isBlank() -> flushParagraph()

            headingPattern.matches(line) -> {
                flushParagraph()
                val match = headingPattern.matchEntire(line)!!
                val level = match.groupValues[1].length.coerceAtMost(3)
                val body = match.groupValues[2]
                blocks += MarkdownBlock.Heading(
                    level,
                    renderInline(body, primary, inlineCodeBg, inlineCodeFg),
                )
            }

            line.startsWith("> ") -> {
                flushParagraph()
                blocks += MarkdownBlock.Quote(
                    renderInline(line.removePrefix("> "), primary, inlineCodeBg, inlineCodeFg),
                )
            }

            bulletPattern.matches(line) -> {
                flushParagraph()
                val body = bulletPattern.matchEntire(line)!!.groupValues[1]
                blocks += MarkdownBlock.BulletItem(
                    renderInline(body, primary, inlineCodeBg, inlineCodeFg),
                )
            }

            numberedPattern.matches(line) -> {
                flushParagraph()
                val match = numberedPattern.matchEntire(line)!!
                val idx = match.groupValues[1].toIntOrNull() ?: 1
                val body = match.groupValues[2]
                blocks += MarkdownBlock.NumberedItem(
                    idx,
                    renderInline(body, primary, inlineCodeBg, inlineCodeFg),
                )
            }

            else -> {
                if (paragraphBuf.isNotEmpty()) paragraphBuf.append('\n')
                paragraphBuf.append(line)
            }
        }
    }
    flushParagraph()
    return blocks
}

/**
 * Inline span renderer. Walks the text once, peeling off the highest-
 * precedence delimiter at each position so e.g. `**bold**` doesn't get
 * mistaken for `*italic*` and inline ` `code` ` wins over surrounding
 * emphasis (matches CommonMark behaviour for our subset).
 */
private fun renderInline(
    text: String,
    primary: Color,
    inlineCodeBg: Color,
    inlineCodeFg: Color,
): AnnotatedString = buildAnnotatedString {
    var i = 0
    val n = text.length
    while (i < n) {
        when {
            // Inline code first — content is literal, no nested formatting.
            text[i] == '`' -> {
                val close = text.indexOf('`', i + 1)
                if (close == -1) {
                    append(text[i]); i += 1
                } else {
                    pushStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = inlineCodeBg,
                            color = inlineCodeFg,
                            fontSize = 14.sp,
                        ),
                    )
                    append(text.substring(i + 1, close))
                    pop()
                    i = close + 1
                }
            }

            // Bold (** or __) — must come before italic because both share `*`/`_`.
            text.startsWithAt(i, "**") || text.startsWithAt(i, "__") -> {
                val token = text.substring(i, i + 2)
                val close = text.indexOf(token, i + 2)
                if (close == -1) {
                    append(token); i += 2
                } else {
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                    append(renderInline(text.substring(i + 2, close), primary, inlineCodeBg, inlineCodeFg))
                    pop()
                    i = close + 2
                }
            }

            // Italic (* or _) — single delimiter; require non-space neighbour
            // so we don't munge prose like "5 * 3 = 15".
            (text[i] == '*' || text[i] == '_') &&
                i + 1 < n && !text[i + 1].isWhitespace() -> {
                val token = text[i].toString()
                val close = findMatchingDelimiter(text, i + 1, token)
                if (close == -1) {
                    append(text[i]); i += 1
                } else {
                    pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                    append(renderInline(text.substring(i + 1, close), primary, inlineCodeBg, inlineCodeFg))
                    pop()
                    i = close + 1
                }
            }

            // Strikethrough (~~).
            text.startsWithAt(i, "~~") -> {
                val close = text.indexOf("~~", i + 2)
                if (close == -1) {
                    append("~~"); i += 2
                } else {
                    pushStyle(SpanStyle(textDecoration = TextDecoration.LineThrough))
                    append(renderInline(text.substring(i + 2, close), primary, inlineCodeBg, inlineCodeFg))
                    pop()
                    i = close + 2
                }
            }

            // Links: [label](url). URL stored as annotation tag for future
            // tap-to-open wiring; currently we just style it.
            text[i] == '[' -> {
                val labelEnd = text.indexOf(']', i + 1)
                if (labelEnd != -1 && labelEnd + 1 < n && text[labelEnd + 1] == '(') {
                    val urlEnd = text.indexOf(')', labelEnd + 2)
                    if (urlEnd != -1) {
                        val label = text.substring(i + 1, labelEnd)
                        val url = text.substring(labelEnd + 2, urlEnd)
                        pushStringAnnotation(tag = "URL", annotation = url)
                        pushStyle(
                            SpanStyle(color = primary, textDecoration = TextDecoration.Underline),
                        )
                        append(renderInline(label, primary, inlineCodeBg, inlineCodeFg))
                        pop()
                        pop()
                        i = urlEnd + 1
                        continue
                    }
                }
                append(text[i]); i += 1
            }

            else -> {
                append(text[i]); i += 1
            }
        }
    }
}

private fun String.startsWithAt(index: Int, prefix: String): Boolean =
    index + prefix.length <= length && substring(index, index + prefix.length) == prefix

/**
 * Find the next standalone `*` / `_` from [start] that closes an italic run.
 * Skips delimiters that are part of `**`/`__` (those are bold) so e.g.
 * `**foo** bar _italic_` parses correctly.
 */
private fun findMatchingDelimiter(text: String, start: Int, token: String): Int {
    var i = start
    while (i < text.length) {
        if (text[i].toString() == token) {
            // Reject if part of a doubled delimiter
            val prevDouble = i > 0 && text[i - 1].toString() == token
            val nextDouble = i + 1 < text.length && text[i + 1].toString() == token
            if (!prevDouble && !nextDouble) return i
            i += 1
        } else {
            i += 1
        }
    }
    return -1
}
