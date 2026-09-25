// SPDX-FileCopyrightText: 2026 Aditya Mehra <aix.m@outlook.com>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

/**
 * @file RemoteEndpoint.kt
 * @brief Value type describing a user-selected host endpoint (host, port,
 *        TLS flag) with parsing/validation from a ws:// or wss:// string and
 *        construction of the single `/ws` WebSocket URL the host exposes.
 * @layer API
 * @dependencies java.net.URI.
 */

package com.verzeta.android.remote

import java.net.URI

/**
 * Host endpoint selected by the user. The host plan exposes exactly one path:
 * `/ws`.
 */
data class RemoteEndpoint(
    val host: String,
    val port: Int,
    val tls: Boolean,
) {
    init {
        require(host.isNotBlank()) { "Host must not be blank" }
        require(port in 1..65535) { "Port must be between 1 and 65535" }
    }

    fun websocketUrl(): String {
        val scheme = if (tls) "wss" else "ws"
        return "$scheme://${host.trim()}:$port/ws"
    }

    companion object {
        fun fromEndpoint(endpoint: String): RemoteEndpoint {
            val parsed = URI(endpoint.trim())
            val tls = when (parsed.scheme) {
                "ws" -> false
                "wss" -> true
                else -> throw IllegalArgumentException("Endpoint must start with ws:// or wss://")
            }
            val host = parsed.host ?: throw IllegalArgumentException("Endpoint must include a host")
            val port = if (parsed.port > 0) parsed.port else if (tls) 443 else 80
            val path = parsed.path ?: ""
            require(path.isBlank() || path == "/ws") { "Endpoint path must be /ws" }
            return RemoteEndpoint(host = host, port = port, tls = tls)
        }
    }
}
