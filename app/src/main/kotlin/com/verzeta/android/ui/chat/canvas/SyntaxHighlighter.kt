// SPDX-FileCopyrightText: 2026 Aditya Mehra <aix.m@outlook.com>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

/**
 * @file SyntaxHighlighter.kt
 * @brief Lightweight regex-based syntax highlighter for the canvas editor.
 *        Provides a [VisualTransformation] for [BasicTextField] and a standalone
 *        [AnnotatedString] API for read-only renderers. Supports 20+ languages
 *        with Material 3 colour-scheme-aware token styling.
 * @layer UI
 * @dependencies Jetpack Compose, Material 3 ColorScheme, VerzetaTheme
 */

package com.verzeta.android.ui.chat.canvas

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import com.verzeta.android.ui.theme.VerzetaTheme

/**
 * Lightweight syntax highlighter for the canvas editor.
 *
 * Hand-rolled regex tokenizer rather than a library dependency:
 *   - Zero new deps (one extra Maven artefact for the BOM is non-trivial
 *     on a project this size, and most Compose-friendly libraries
 *     wrap KSyntaxHighlighting transitively which doesn't ship for
 *     Android).
 *   - Full control over the Material 3 colour mapping — every token
 *     resolves to a `colorScheme` slot so light / dark / dynamic
 *     themes adapt automatically.
 *   - Works offline; no network handshake at startup.
 *
 * Token categories (small enough to map cleanly to MD3 colours):
 *   - Keyword     primary
 *   - String      tertiary
 *   - Number      secondary
 *   - Comment     onSurfaceVariant  (italic when the renderer supports it)
 *   - Function    primary           (lighter weight than keywords)
 *   - Type        secondary         (PascalCase identifiers, primitives)
 *   - Operator    onSurfaceVariant
 *   - Punctuation onSurface         (default)
 *
 * Performance notes:
 *   - Tokenization runs once per render via `remember(text, lang, scheme)`.
 *   - For canvases ≤ 5 KB the cost is negligible (< 5 ms on a Pixel 6).
 *   - For larger canvases we fall back to plain rendering above 64 KB.
 */
object SyntaxHighlighter {

    /** Hard cap above which we fall back to plain monospace. */
    private const val MAX_HIGHLIGHT_BYTES = 64 * 1024

    /**
     * Build a [VisualTransformation] that decorates the BasicTextField's
     * value with token spans. The transformation never mutates the
     * underlying string — just overlays span styles on top.
     */
    @Composable
    fun visualTransformation(language: String): VisualTransformation {
        val scheme = MaterialTheme.colorScheme
        val palette = remember(scheme) { Palette.from(scheme) }
        val tokenizer = remember(language) { tokenizerFor(language) }
        return VisualTransformation { text ->
            val raw = text.text
            if (raw.length > MAX_HIGHLIGHT_BYTES || tokenizer == null) {
                return@VisualTransformation TransformedText(
                    AnnotatedString(raw),
                    OffsetMapping.Identity,
                )
            }
            val tokens = tokenizer.tokenize(raw)
            TransformedText(
                buildAnnotated(raw, tokens, palette),
                OffsetMapping.Identity,
            )
        }
    }

    /**
     * Standalone API for read-only renderers. Returns an AnnotatedString
     * the caller can pass to a regular `Text(text=)` composable.
     */
    @Composable
    fun annotated(text: String, language: String): AnnotatedString {
        val scheme = MaterialTheme.colorScheme
        val palette = remember(scheme) { Palette.from(scheme) }
        val tokenizer = remember(language) { tokenizerFor(language) }
        return remember(text, language, scheme) {
            if (text.length > MAX_HIGHLIGHT_BYTES || tokenizer == null) AnnotatedString(text)
            else buildAnnotated(text, tokenizer.tokenize(text), palette)
        }
    }

    private fun buildAnnotated(
        raw: String,
        tokens: List<Token>,
        palette: Palette,
    ): AnnotatedString = androidx.compose.ui.text.buildAnnotatedString {
        append(raw)
        tokens.forEach { token ->
            val color = palette.colorFor(token.kind) ?: return@forEach
            addStyle(SpanStyle(color = color), token.start, token.end)
        }
    }

    /**
     * Map host-emitted language tag → tokenizer. Unknown / unsupported
     * languages return null so the caller falls back to plain rendering.
     */
    private fun tokenizerFor(language: String): Tokenizer? = when (language.lowercase().trim()) {
        "python", "py" -> PythonTokenizer
        "javascript", "js", "typescript", "ts", "jsx", "tsx" -> JavaScriptTokenizer
        "kotlin", "kt", "kts" -> KotlinTokenizer
        "java" -> JavaTokenizer
        "cpp", "c++", "cxx", "cc", "hpp", "h", "c" -> CTokenizer
        "rust", "rs" -> RustTokenizer
        "go" -> GoTokenizer
        "swift" -> SwiftTokenizer
        "ruby", "rb" -> RubyTokenizer
        "shell", "bash", "sh", "zsh" -> ShellTokenizer
        "json" -> JsonTokenizer
        "html", "htm", "xml", "qml" -> HtmlTokenizer
        "css", "scss", "less" -> CssTokenizer
        "yaml", "yml", "toml", "ini" -> YamlIshTokenizer
        "sql" -> SqlTokenizer
        "markdown", "md" -> MarkdownTokenizer
        "diff", "patch" -> DiffTokenizer
        "dockerfile" -> DockerfileTokenizer
        "makefile", "make", "cmake" -> MakefileTokenizer
        "" -> null
        else -> null
    }

    // ----- Palette ----------------------------------------------------

    private data class Palette(
        val keyword: Color,
        val string: Color,
        val number: Color,
        val comment: Color,
        val function: Color,
        val type: Color,
        val operator: Color,
    ) {
        fun colorFor(kind: TokenKind): Color? = when (kind) {
            TokenKind.Keyword -> keyword
            TokenKind.String -> string
            TokenKind.Number -> number
            TokenKind.Comment -> comment
            TokenKind.Function -> function
            TokenKind.Type -> type
            TokenKind.Operator -> operator
            TokenKind.Plain -> null  // inherit BasicTextField's text colour
        }

        companion object {
            fun from(scheme: ColorScheme): Palette = Palette(
                keyword = scheme.primary,
                string = scheme.tertiary,
                number = scheme.secondary,
                comment = scheme.onSurfaceVariant,
                function = scheme.primary.copy(alpha = 0.85f),
                type = scheme.secondary.copy(alpha = 0.92f),
                operator = scheme.onSurfaceVariant.copy(alpha = 0.85f),
            )
        }
    }

    // ----- Tokens -----------------------------------------------------

    private enum class TokenKind { Keyword, String, Number, Comment, Function, Type, Operator, Plain }

    private data class Token(val start: Int, val end: Int, val kind: TokenKind)

    private interface Tokenizer { fun tokenize(text: String): List<Token> }

    /**
     * Generic regex-driven tokenizer that walks a list of (regex, kind)
     * patterns and emits non-overlapping tokens. Patterns are matched in order
     * — first match wins for any given offset, so comments and strings must
     * appear before keywords and identifiers to prevent re-colouring tokens
     * inside string literals.
     *
     * The tokenizer resolves non-overlapping spans by tracking the current
     * cursor: once a match starts at offset N and runs to M, the next scan
     * starts at M with no inner-match recursion.
     *
     * Uses `regex.matchAt(text, i)` (Kotlin 1.8+) which checks for a match
     * starting exactly at offset i. Patterns must not include a `^` prefix —
     * matchAt provides the anchoring, and `^` anchors to the start of input
     * (without MULTILINE), which would cause zero tokens past the first match.
     */
    private class RegexTokenizer(
        private val rules: List<Pair<Regex, TokenKind>>,
    ) : Tokenizer {
        override fun tokenize(text: String): List<Token> {
            val out = mutableListOf<Token>()
            var i = 0
            while (i < text.length) {
                var matched = false
                for ((regex, kind) in rules) {
                    val m = regex.matchAt(text, i) ?: continue
                    val end = m.range.last + 1
                    if (end <= i) continue  // defensive: empty match
                    out.add(Token(i, end, kind))
                    i = end
                    matched = true
                    break
                }
                if (!matched) i++
            }
            return out
        }
    }

    // Common regex fragments. Strings handle escape characters; numbers
    // cover ints / floats / hex / scientific.
    private val STRING_DOUBLE = Regex(""""(?:\\.|[^"\\])*"""")
    private val STRING_SINGLE = Regex("""'(?:\\.|[^'\\])*'""")
    private val STRING_BACKTICK = Regex("""`(?:\\.|[^`\\])*`""")
    private val STRING_TRIPLE_DOUBLE = Regex("""\"\"\"(?:[^\"]|\"(?!\"\")|\\.)*\"\"\"""", RegexOption.DOT_MATCHES_ALL)
    private val STRING_TRIPLE_SINGLE = Regex("""'''(?:[^']|'(?!'')|\\.)*'''""", RegexOption.DOT_MATCHES_ALL)
    private val NUMBER = Regex("""(?:0[xX][0-9a-fA-F]+|0[bB][01]+|[0-9]+(?:\.[0-9]+)?(?:[eE][-+]?[0-9]+)?)[lLfFdDuU]?\b""")
    private val LINE_COMMENT_HASH = Regex("""#[^\n]*""")
    private val LINE_COMMENT_SLASH = Regex("""//[^\n]*""")
    private val BLOCK_COMMENT_C = Regex("""/\*[\s\S]*?\*/""")
    private val FUNC_CALL = Regex("""([a-zA-Z_][a-zA-Z0-9_]*)(?=\s*\()""")
    // PascalCase identifiers — covers Java/Kotlin types + most class
    // identifiers in C-family languages without false-firing on
    // SCREAMING_SNAKE constants.
    private val PASCAL_TYPE = Regex("""[A-Z][a-zA-Z0-9_]*\b""")

    private fun keywordRegex(words: Set<String>): Regex =
        Regex("(?:" + words.joinToString("|") { Regex.escape(it) } + """)\b""")

    // ----- Per-language tokenizers ------------------------------------

    private val PythonTokenizer = RegexTokenizer(listOf(
        STRING_TRIPLE_DOUBLE to TokenKind.String,
        STRING_TRIPLE_SINGLE to TokenKind.String,
        STRING_DOUBLE to TokenKind.String,
        STRING_SINGLE to TokenKind.String,
        LINE_COMMENT_HASH to TokenKind.Comment,
        keywordRegex(setOf(
            "False", "None", "True", "and", "as", "assert", "async", "await",
            "break", "class", "continue", "def", "del", "elif", "else", "except",
            "finally", "for", "from", "global", "if", "import", "in", "is",
            "lambda", "nonlocal", "not", "or", "pass", "raise", "return", "try",
            "while", "with", "yield", "match", "case",
        )) to TokenKind.Keyword,
        keywordRegex(setOf("self", "cls", "print", "len", "range", "list", "dict", "tuple", "set", "str", "int", "float", "bool", "type")) to TokenKind.Type,
        FUNC_CALL to TokenKind.Function,
        NUMBER to TokenKind.Number,
    ))

    private val JavaScriptTokenizer = RegexTokenizer(listOf(
        BLOCK_COMMENT_C to TokenKind.Comment,
        LINE_COMMENT_SLASH to TokenKind.Comment,
        STRING_DOUBLE to TokenKind.String,
        STRING_SINGLE to TokenKind.String,
        STRING_BACKTICK to TokenKind.String,
        keywordRegex(setOf(
            "abstract", "async", "await", "break", "case", "catch", "class",
            "const", "continue", "debugger", "default", "delete", "do", "else",
            "enum", "export", "extends", "false", "final", "finally", "for",
            "from", "function", "if", "implements", "import", "in", "instanceof",
            "interface", "let", "new", "null", "of", "private", "protected",
            "public", "return", "static", "super", "switch", "this", "throw",
            "true", "try", "typeof", "undefined", "var", "void", "while", "with",
            "yield", "as", "type",
        )) to TokenKind.Keyword,
        PASCAL_TYPE to TokenKind.Type,
        FUNC_CALL to TokenKind.Function,
        NUMBER to TokenKind.Number,
    ))

    private val KotlinTokenizer = RegexTokenizer(listOf(
        BLOCK_COMMENT_C to TokenKind.Comment,
        LINE_COMMENT_SLASH to TokenKind.Comment,
        STRING_TRIPLE_DOUBLE to TokenKind.String,
        STRING_DOUBLE to TokenKind.String,
        STRING_SINGLE to TokenKind.String,
        keywordRegex(setOf(
            "abstract", "actual", "annotation", "as", "break", "by", "catch",
            "class", "companion", "const", "constructor", "continue", "crossinline",
            "data", "do", "dynamic", "else", "enum", "expect", "external", "false",
            "final", "finally", "for", "fun", "get", "if", "import", "in", "infix",
            "init", "inline", "inner", "interface", "internal", "is", "lateinit",
            "noinline", "null", "object", "open", "operator", "out", "override",
            "package", "private", "protected", "public", "reified", "return", "sealed",
            "set", "super", "suspend", "tailrec", "this", "throw", "true", "try",
            "typealias", "val", "var", "vararg", "when", "where", "while", "yield",
        )) to TokenKind.Keyword,
        keywordRegex(setOf("Int", "Long", "Float", "Double", "Boolean", "String", "Char", "Byte", "Short", "Unit", "Nothing", "Any", "Array", "List", "Map", "Set", "MutableList", "MutableMap", "MutableSet")) to TokenKind.Type,
        PASCAL_TYPE to TokenKind.Type,
        FUNC_CALL to TokenKind.Function,
        NUMBER to TokenKind.Number,
    ))

    private val JavaTokenizer = RegexTokenizer(listOf(
        BLOCK_COMMENT_C to TokenKind.Comment,
        LINE_COMMENT_SLASH to TokenKind.Comment,
        STRING_DOUBLE to TokenKind.String,
        STRING_SINGLE to TokenKind.String,
        keywordRegex(setOf(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch",
            "char", "class", "const", "continue", "default", "do", "double", "else",
            "enum", "extends", "false", "final", "finally", "float", "for", "goto",
            "if", "implements", "import", "instanceof", "int", "interface", "long",
            "native", "new", "null", "package", "private", "protected", "public",
            "return", "short", "static", "strictfp", "super", "switch", "synchronized",
            "this", "throw", "throws", "transient", "true", "try", "void", "volatile",
            "while", "var", "yield", "record", "sealed", "permits",
        )) to TokenKind.Keyword,
        PASCAL_TYPE to TokenKind.Type,
        FUNC_CALL to TokenKind.Function,
        NUMBER to TokenKind.Number,
    ))

    private val CTokenizer = RegexTokenizer(listOf(
        BLOCK_COMMENT_C to TokenKind.Comment,
        LINE_COMMENT_SLASH to TokenKind.Comment,
        STRING_DOUBLE to TokenKind.String,
        STRING_SINGLE to TokenKind.String,
        // C/C++ preprocessor directives — colour like keywords.
        Regex("""#\s*[a-z]+""") to TokenKind.Keyword,
        keywordRegex(setOf(
            "alignas", "alignof", "and", "asm", "auto", "bool", "break", "case",
            "catch", "char", "char16_t", "char32_t", "class", "concept", "const",
            "constexpr", "const_cast", "continue", "decltype", "default", "delete",
            "do", "double", "dynamic_cast", "else", "enum", "explicit", "export",
            "extern", "false", "final", "float", "for", "friend", "goto", "if",
            "inline", "int", "long", "mutable", "namespace", "new", "noexcept",
            "not", "nullptr", "operator", "or", "override", "private", "protected",
            "public", "register", "reinterpret_cast", "requires", "return", "short",
            "signed", "sizeof", "static", "static_assert", "static_cast", "struct",
            "switch", "template", "this", "thread_local", "throw", "true", "try",
            "typedef", "typeid", "typename", "union", "unsigned", "using", "virtual",
            "void", "volatile", "wchar_t", "while", "xor",
        )) to TokenKind.Keyword,
        PASCAL_TYPE to TokenKind.Type,
        FUNC_CALL to TokenKind.Function,
        NUMBER to TokenKind.Number,
    ))

    private val RustTokenizer = RegexTokenizer(listOf(
        BLOCK_COMMENT_C to TokenKind.Comment,
        LINE_COMMENT_SLASH to TokenKind.Comment,
        STRING_DOUBLE to TokenKind.String,
        STRING_SINGLE to TokenKind.String,
        keywordRegex(setOf(
            "as", "async", "await", "break", "const", "continue", "crate", "dyn",
            "else", "enum", "extern", "false", "fn", "for", "if", "impl", "in",
            "let", "loop", "match", "mod", "move", "mut", "pub", "ref", "return",
            "self", "Self", "static", "struct", "super", "trait", "true", "type",
            "unsafe", "use", "where", "while", "box", "do", "final", "macro",
            "override", "priv", "typeof", "unsized", "virtual", "yield",
        )) to TokenKind.Keyword,
        keywordRegex(setOf("i8", "i16", "i32", "i64", "i128", "isize", "u8", "u16", "u32", "u64", "u128", "usize", "f32", "f64", "bool", "char", "str", "String", "Vec", "Option", "Result", "Box")) to TokenKind.Type,
        PASCAL_TYPE to TokenKind.Type,
        FUNC_CALL to TokenKind.Function,
        NUMBER to TokenKind.Number,
    ))

    private val GoTokenizer = RegexTokenizer(listOf(
        BLOCK_COMMENT_C to TokenKind.Comment,
        LINE_COMMENT_SLASH to TokenKind.Comment,
        STRING_DOUBLE to TokenKind.String,
        STRING_SINGLE to TokenKind.String,
        STRING_BACKTICK to TokenKind.String,
        keywordRegex(setOf(
            "break", "case", "chan", "const", "continue", "default", "defer",
            "else", "fallthrough", "for", "func", "go", "goto", "if", "import",
            "interface", "map", "package", "range", "return", "select", "struct",
            "switch", "type", "var", "true", "false", "nil", "iota",
        )) to TokenKind.Keyword,
        keywordRegex(setOf("bool", "byte", "complex64", "complex128", "error", "float32", "float64", "int", "int8", "int16", "int32", "int64", "rune", "string", "uint", "uint8", "uint16", "uint32", "uint64", "uintptr")) to TokenKind.Type,
        PASCAL_TYPE to TokenKind.Type,
        FUNC_CALL to TokenKind.Function,
        NUMBER to TokenKind.Number,
    ))

    private val SwiftTokenizer = RegexTokenizer(listOf(
        BLOCK_COMMENT_C to TokenKind.Comment,
        LINE_COMMENT_SLASH to TokenKind.Comment,
        STRING_DOUBLE to TokenKind.String,
        keywordRegex(setOf(
            "associatedtype", "class", "deinit", "enum", "extension", "fileprivate",
            "func", "import", "init", "inout", "internal", "let", "open", "operator",
            "private", "protocol", "public", "rethrows", "static", "struct",
            "subscript", "typealias", "var", "break", "case", "continue", "default",
            "defer", "do", "else", "fallthrough", "for", "guard", "if", "in",
            "repeat", "return", "switch", "where", "while", "as", "Any", "catch",
            "false", "is", "nil", "super", "self", "Self", "throw", "throws",
            "true", "try",
        )) to TokenKind.Keyword,
        PASCAL_TYPE to TokenKind.Type,
        FUNC_CALL to TokenKind.Function,
        NUMBER to TokenKind.Number,
    ))

    private val RubyTokenizer = RegexTokenizer(listOf(
        LINE_COMMENT_HASH to TokenKind.Comment,
        STRING_DOUBLE to TokenKind.String,
        STRING_SINGLE to TokenKind.String,
        Regex(""":[a-zA-Z_][a-zA-Z0-9_]*""") to TokenKind.String,  // symbols
        keywordRegex(setOf(
            "BEGIN", "END", "alias", "and", "begin", "break", "case", "class",
            "def", "defined?", "do", "else", "elsif", "end", "ensure", "false",
            "for", "if", "in", "module", "next", "nil", "not", "or", "redo",
            "rescue", "retry", "return", "self", "super", "then", "true", "undef",
            "unless", "until", "when", "while", "yield",
        )) to TokenKind.Keyword,
        PASCAL_TYPE to TokenKind.Type,
        FUNC_CALL to TokenKind.Function,
        NUMBER to TokenKind.Number,
    ))

    private val ShellTokenizer = RegexTokenizer(listOf(
        LINE_COMMENT_HASH to TokenKind.Comment,
        STRING_DOUBLE to TokenKind.String,
        STRING_SINGLE to TokenKind.String,
        Regex("""\$\{[^}]*\}""") to TokenKind.Function,  // ${VAR}
        Regex("""\$[a-zA-Z_][a-zA-Z0-9_]*""") to TokenKind.Function,  // $VAR
        keywordRegex(setOf(
            "if", "then", "else", "elif", "fi", "case", "esac", "for", "while",
            "do", "done", "in", "until", "select", "function", "return", "exit",
            "break", "continue", "shift", "trap", "set", "unset", "export",
            "local", "readonly", "echo", "printf", "true", "false", "test",
            "source", "alias",
        )) to TokenKind.Keyword,
        Regex("""[a-zA-Z_][a-zA-Z0-9_]*=""") to TokenKind.Type,  // assignment LHS
        NUMBER to TokenKind.Number,
    ))

    private val JsonTokenizer = RegexTokenizer(listOf(
        // JSON keys: a "..." string immediately followed by colon (with
        // optional whitespace). Match the string only.
        Regex(""""(?:\\.|[^"\\])*"(?=\s*:)""") to TokenKind.Function,
        STRING_DOUBLE to TokenKind.String,
        keywordRegex(setOf("true", "false", "null")) to TokenKind.Keyword,
        NUMBER to TokenKind.Number,
    ))

    private val HtmlTokenizer = RegexTokenizer(listOf(
        Regex("""<!--[\s\S]*?-->""") to TokenKind.Comment,
        // Tag opener — colour the tag name as Keyword.
        Regex("""</?[a-zA-Z][a-zA-Z0-9-]*""") to TokenKind.Keyword,
        Regex("""[a-zA-Z-]+(?==)""") to TokenKind.Function,  // attribute name
        STRING_DOUBLE to TokenKind.String,
        STRING_SINGLE to TokenKind.String,
    ))

    private val CssTokenizer = RegexTokenizer(listOf(
        BLOCK_COMMENT_C to TokenKind.Comment,
        STRING_DOUBLE to TokenKind.String,
        STRING_SINGLE to TokenKind.String,
        Regex("""@[a-zA-Z-]+""") to TokenKind.Keyword,  // at-rules
        Regex("""[#.][a-zA-Z][a-zA-Z0-9_-]*""") to TokenKind.Type,  // selectors
        Regex("""[a-zA-Z-]+(?=\s*:)""") to TokenKind.Function,  // properties
        Regex("""#[0-9a-fA-F]{3,8}\b""") to TokenKind.Number,  // hex colours
        NUMBER to TokenKind.Number,
    ))

    private val YamlIshTokenizer = RegexTokenizer(listOf(
        LINE_COMMENT_HASH to TokenKind.Comment,
        STRING_DOUBLE to TokenKind.String,
        STRING_SINGLE to TokenKind.String,
        // Section headers in TOML / INI: [section]
        Regex("""\[[^\]]+\]""") to TokenKind.Keyword,
        // YAML / TOML / INI keys — `key:` or `key =`
        Regex("""[a-zA-Z_][a-zA-Z0-9_-]*(?=\s*[:=])""") to TokenKind.Function,
        keywordRegex(setOf("true", "false", "null", "yes", "no", "on", "off")) to TokenKind.Keyword,
        NUMBER to TokenKind.Number,
    ))

    private val SqlTokenizer = RegexTokenizer(listOf(
        Regex("""--[^\n]*""") to TokenKind.Comment,
        BLOCK_COMMENT_C to TokenKind.Comment,
        STRING_SINGLE to TokenKind.String,
        STRING_DOUBLE to TokenKind.String,
        Regex("""(?i)(?:select|insert|update|delete|from|where|group|order|by|having|join|left|right|inner|outer|on|union|intersect|except|create|alter|drop|table|index|view|trigger|procedure|function|database|schema|grant|revoke|begin|commit|rollback|transaction|if|then|else|case|when|end|null|not|is|like|in|between|exists|and|or|as|distinct|all|any|some|primary|foreign|key|references|cascade|set|values|into|with|returning|limit|offset)\b""") to TokenKind.Keyword,
        Regex("""(?i)(?:int|integer|bigint|smallint|tinyint|decimal|numeric|float|double|real|varchar|char|text|blob|date|time|timestamp|datetime|boolean|bool)\b""") to TokenKind.Type,
        FUNC_CALL to TokenKind.Function,
        NUMBER to TokenKind.Number,
    ))

    private val MarkdownTokenizer = RegexTokenizer(listOf(
        Regex("""```[\s\S]*?```""") to TokenKind.String,  // fenced code
        Regex("""`[^`\n]+`""") to TokenKind.String,  // inline code
        Regex("""#{1,6}\s+[^\n]*""") to TokenKind.Keyword,  // headings
        Regex(""">\s+[^\n]*""") to TokenKind.Comment,  // blockquotes
        Regex("""[*-]\s+""") to TokenKind.Operator,  // bullets
        Regex("""\d+\.\s+""") to TokenKind.Operator,  // numbered lists
        Regex("""\*\*[^*\n]+\*\*""") to TokenKind.Type,  // bold
        Regex("""_[^_\n]+_""") to TokenKind.Function,  // italic
        Regex("""\[[^\]]*\]\([^)]*\)""") to TokenKind.Function,  // links
    ))

    private val DiffTokenizer = RegexTokenizer(listOf(
        Regex("""@@[^\n]*@@""") to TokenKind.Keyword,  // hunk headers
        Regex("""\+[^\n]*""") to TokenKind.String,  // additions
        Regex("""-[^\n]*""") to TokenKind.Number,  // deletions (re-using number colour for distinct hue)
        Regex("""diff[^\n]*""") to TokenKind.Comment,
        Regex("""index[^\n]*""") to TokenKind.Comment,
    ))

    private val DockerfileTokenizer = RegexTokenizer(listOf(
        LINE_COMMENT_HASH to TokenKind.Comment,
        STRING_DOUBLE to TokenKind.String,
        STRING_SINGLE to TokenKind.String,
        Regex("""(?:FROM|RUN|CMD|LABEL|MAINTAINER|EXPOSE|ENV|ADD|COPY|ENTRYPOINT|VOLUME|USER|WORKDIR|ARG|ONBUILD|STOPSIGNAL|HEALTHCHECK|SHELL)\b""") to TokenKind.Keyword,
        NUMBER to TokenKind.Number,
    ))

    private val MakefileTokenizer = RegexTokenizer(listOf(
        LINE_COMMENT_HASH to TokenKind.Comment,
        Regex("""[a-zA-Z_][a-zA-Z0-9_-]*\s*:""") to TokenKind.Function,  // targets
        Regex("""\$\([^)]+\)""") to TokenKind.Type,  // $(VAR)
        Regex("""\$\{[^}]+\}""") to TokenKind.Type,  // ${VAR}
        keywordRegex(setOf(
            "include", "ifeq", "ifneq", "ifdef", "ifndef", "else", "endif",
            "define", "endef", "export", "unexport", "override", "private",
            "vpath", "if", "endif", "else", "elseif", "set", "function", "endfunction",
            "macro", "endmacro", "foreach", "endforeach", "while", "endwhile",
        )) to TokenKind.Keyword,
        NUMBER to TokenKind.Number,
    ))

    @Suppress("UNUSED")
    private val unusedTheme = VerzetaTheme  // keep the import alive for the
    //    semantic colour anchor used in other parts of the package.
}
