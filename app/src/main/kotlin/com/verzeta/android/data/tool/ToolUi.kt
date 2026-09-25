// SPDX-FileCopyrightText: 2026 Aditya Mehra <aix.m@outlook.com>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

/**
 * @file ToolUi.kt
 * @brief UI-facing models for the host's tool catalog: one row per registered
 *        tool (built-in, custom, or MCP-advertised) plus its parameter
 *        descriptors and a kind discriminator.
 * @layer Model
 * @dependencies None — pure Kotlin data declarations.
 */

package com.verzeta.android.data.tool

/**
 * One row from the host's `tool.list` catalog. Wire shape comes from
 * `ToolService::registeredToolsList` (tool-service.cpp:222) — built-ins,
 * user-defined customs, and tools advertised by connected MCP servers all
 * appear here, distinguished by [kind].
 *
 * Spec called the parameter container `parameters_schema`; the host
 * actually emits an array of `{name, type, description, required}` rows
 * under the key `parameters`. This UI is read-only in v1 — the host has no
 * `tool.set_enabled` op for remote clients yet, so [enabled] is shown as a
 * static state.
 */
data class ToolUi(
    val name: String,
    val description: String,
    val enabled: Boolean,
    val isBuiltIn: Boolean,
    val kind: ToolKind,
    val mcpServer: String? = null,
    val shortName: String? = null,
    val parameters: List<ToolParameterUi> = emptyList(),
)

/**
 * Origin of a catalog tool: shipped with the host binary, user-defined
 * custom tool, or advertised by a connected MCP server. [Unknown] absorbs
 * wire values this client version doesn't recognise.
 */
enum class ToolKind(val wireValue: String) {
    BuiltIn("builtin"),
    Custom("custom"),
    Mcp("mcp"),
    Unknown("");

    companion object {
        fun fromWire(value: String?): ToolKind =
            entries.firstOrNull { it.wireValue == value } ?: Unknown
    }
}

/**
 * One declared parameter of a catalog tool — name, type label, description,
 * and whether the tool requires it. Rendered in the tool-detail sheet.
 */
data class ToolParameterUi(
    val name: String,
    val type: String,
    val description: String,
    val required: Boolean,
)
