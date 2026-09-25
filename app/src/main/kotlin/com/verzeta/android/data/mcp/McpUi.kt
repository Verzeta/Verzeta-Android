// SPDX-FileCopyrightText: 2026 Aditya Mehra <aix.m@outlook.com>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

/**
 * @file McpUi.kt
 * @brief Models for configured MCP servers (`mcp.list` rows with transport,
 *        enablement, and connection status) and the tools each server
 *        advertises (`mcp.server_tools` rows).
 * @layer Model
 * @dependencies None — pure Kotlin data declarations.
 */

package com.verzeta.android.data.mcp

/**
 * One configured MCP server, as returned by `mcp.list`
 * (mcp-service.cpp:104). Wire fields:
 *   name        unique server name (used as key for `mcp.server_tools`)
 *   type        transport: "stdio" / "sse" / "streamable_http"
 *   command/args/url  one set populated based on transport
 *   disabled    true when the user has flipped the enable switch off
 *   status      enum: connected / connecting / error / disconnected / disabled
 *   error       only present when status=error (lastError() string)
 *   toolCount   number of tools the server has advertised so far
 *
 * Spec described `enabled: bool` and `connected: bool`; the host actually
 * emits `disabled` (inverted) and a richer 5-state enum we surface as
 * [McpStatus]. Use [McpStatus.fromWire] to parse.
 */
data class McpServerUi(
    val name: String,
    val type: String,
    val command: String = "",
    val args: String = "",
    val url: String = "",
    val disabled: Boolean = false,
    val status: McpStatus = McpStatus.Disconnected,
    val errorMessage: String = "",
    val toolCount: Int = 0,
) {
    /** A single human-readable line describing how the server is reached. */
    val transportDetail: String
        get() = when (type) {
            "stdio" -> listOf(command, args).filter { it.isNotBlank() }.joinToString(" ")
            else -> url
        }
}

/**
 * Connection status of an MCP server as reported by the host's 5-state
 * enum. Unrecognised wire values fall back to [Disconnected].
 */
enum class McpStatus(val wireValue: String) {
    Connected("connected"),
    Connecting("connecting"),
    Error("error"),
    Disconnected("disconnected"),
    Disabled("disabled");

    companion object {
        fun fromWire(value: String?): McpStatus =
            entries.firstOrNull { it.wireValue == value } ?: Disconnected
    }
}

/**
 * One tool advertised by an MCP server (`mcp.server_tools` row,
 * mcp-service.cpp:238). Note: spec promised `parameters_schema` but the
 * host only emits `{name, description, server}` for this op.
 */
data class McpToolUi(
    val name: String,
    val description: String,
    val server: String,
)
