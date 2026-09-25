// SPDX-FileCopyrightText: 2026 Aditya Mehra <aix.m@outlook.com>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

/**
 * @file RemoteModels.kt
 * @brief Serializable envelope and payload models for the Verzeta wire
 *        protocol: the client-to-host operation envelope, host responses
 *        and events, sanitised errors, and the auth/pairing handshake
 *        payloads.
 * @layer API
 * @dependencies kotlinx.serialization (Serializable, SerialName,
 *               JsonElement/JsonObject).
 */

package com.verzeta.android.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
/**
 * Client-to-host operation envelope. Every operation carries a client-generated
 * request id so the WebSocket session can match the host response.
 */
data class RemoteOp(
    val op: String,
    @SerialName("request_id") val requestId: String,
    val params: JsonObject = JsonObject(emptyMap()),
)

@Serializable
/**
 * Host-to-client response envelope for a previously submitted operation.
 */
data class RemoteResponse(
    val type: String,
    @SerialName("request_id") val requestId: String,
    val ok: Boolean,
    val data: JsonElement? = null,
    val error: RemoteError? = null,
)

@Serializable
/**
 * Host-to-client event envelope. Events are unsolicited and may arrive before
 * authentication, such as the initial hello event.
 */
data class RemoteEvent(
    val type: String,
    val event: String,
    val data: JsonElement? = null,
)

@Serializable
/**
 * Sanitised protocol error returned by the host.
 */
data class RemoteError(
    val kind: String,
    val detail: String,
)

@Serializable
/**
 * Payload returned by a successful pairing exchange: the durable auth token
 * plus the host-assigned client identity.
 */
data class PairResponse(
    val token: String,
    @SerialName("client_id") val clientId: String,
    val name: String,
)

@Serializable
/**
 * Payload returned when re-authenticating with a previously issued token.
 */
data class TokenAuthResponse(
    @SerialName("client_id") val clientId: String,
    val name: String,
    @SerialName("last_seen_at") val lastSeenAt: Long? = null,
)

@Serializable
/**
 * Payload describing the authenticated client's own identity (auth.me).
 */
data class AuthMeResponse(
    @SerialName("client_id") val clientId: String,
    val name: String,
    @SerialName("last_seen_at") val lastSeenAt: Long? = null,
)

@Serializable
/**
 * Payload returned by the protocol-level ping, carrying the host clock.
 *
 * [hostAlive] is false when the remote server answers but the Verzeta
 * Studio desktop app behind it is not running, so every other op would
 * fail. Hosts that predate the field omit it; the default keeps them
 * reported as alive.
 */
data class PingResponse(
    @SerialName("time_ms") val timeMs: Long,
    @SerialName("host_alive") val hostAlive: Boolean = true,
)
