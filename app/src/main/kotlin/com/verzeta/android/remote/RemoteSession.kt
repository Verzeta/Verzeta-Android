// SPDX-FileCopyrightText: 2026 Aditya Mehra <aix.m@outlook.com>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

/**
 * @file RemoteSession.kt
 * @brief Owns a single WebSocket connection to a Verzeta host: connect
 *        (optionally with a pinned self-signed TLS fingerprint),
 *        request/response correlation by request id, an unsolicited-event
 *        flow, and a connection-state flow the ViewModel uses to drive
 *        reconnect and re-auth.
 * @layer API
 * @dependencies Protocol envelope models + RemoteJson, RemoteEndpoint,
 *               OkHttp WebSocket, kotlinx.coroutines (flows, deferreds),
 *               javax.net.ssl for certificate pinning.
 */

package com.verzeta.android.remote

import com.verzeta.android.protocol.RemoteError
import com.verzeta.android.protocol.RemoteEvent
import com.verzeta.android.protocol.RemoteJson
import com.verzeta.android.protocol.RemoteOp
import com.verzeta.android.protocol.RemoteResponse
import java.io.Closeable
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * Connection life-cycle as observed by the ViewModel. The host's
 * `remote-ws-session.cpp` tears down per-connection state when the socket
 * closes, so any drop forces us to re-establish the WebSocket *and* re-issue
 * `auth.token` before subsequent ops can succeed. The ViewModel listens to
 * this state to drive that recovery.
 */
enum class ConnectionState {
    /** No socket present — initial state and after explicit disconnect(). */
    Idle,
    /** TCP/TLS handshake in flight, before the WebSocket upgrade completes. */
    Connecting,
    /** Socket is open. Auth status is tracked separately by the ViewModel. */
    Open,
    /** Socket closed cleanly (server stop, intentional close). */
    Closed,
    /** Socket failed (network drop, refused). Should trigger a reconnect attempt. */
    Failed,
}

/**
 * Owns a single WebSocket connection and all in-flight request correlation.
 * The session is closeable and must be closed by the ViewModel that owns it.
 *
 * In addition to the request/response and event channels, the session
 * publishes a [connectionState] StateFlow so the ViewModel can detect drops
 * and drive reconnect+re-auth without polling.
 *
 * Every request is bounded by [REQUEST_TIMEOUT_MS], extended for large
 * frames so an attachment upload has time to leave the device. The host answers some
 * rejections (rate limiting, an oversized frame) with an `error` event
 * instead of a response, and a half-open socket never answers at all, so
 * an unbounded wait would leave the caller hanging. The default client
 * also sends a WebSocket ping every [PING_INTERVAL_SECONDS] so a dead
 * connection is detected and reported as [ConnectionState.Failed].
 *
 * Events are delivered in the order the host sent them: frames are queued
 * on one channel and a single coroutine drains it into [events].
 */
class RemoteSession(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .pingInterval(PING_INTERVAL_SECONDS, TimeUnit.SECONDS)
        .build(),
) : Closeable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pending = ConcurrentHashMap<String, CompletableDeferred<RemoteResponse>>()
    private val _events = MutableSharedFlow<RemoteEvent>(extraBufferCapacity = 64)
    private val eventQueue = Channel<RemoteEvent>(Channel.UNLIMITED)
    private val _connectionState = MutableStateFlow(ConnectionState.Idle)

    init {
        scope.launch {
            for (event in eventQueue) _events.emit(event)
        }
    }

    @Volatile
    private var webSocket: WebSocket? = null

    val events: SharedFlow<RemoteEvent> = _events
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    fun connect(endpoint: RemoteEndpoint): Result<Unit> = connect(endpoint, "")

    /**
     * Connect to [endpoint], optionally pinning the host's self-signed TLS
     * certificate by SHA-256 fingerprint. The fingerprint format is the
     * one the desktop dialog displays — uppercase colon-separated hex
     * (e.g. "AB:CD:..."). Empty string falls through to the system trust
     * store (correct for ws:// and CA-signed wss://).
     */
    fun connect(endpoint: RemoteEndpoint, tlsCertSha256: String): Result<Unit> {
        closeSocketOnly(ConnectionState.Idle)
        _connectionState.value = ConnectionState.Connecting
        return runCatching {
            val httpClient = if (endpoint.tls && tlsCertSha256.isNotBlank()) {
                pinnedClient(tlsCertSha256)
            } else {
                client
            }
            val request = Request.Builder().url(endpoint.websocketUrl()).build()
            webSocket = httpClient.newWebSocket(request, Listener())
        }.onFailure { _connectionState.value = ConnectionState.Failed }
    }

    /**
     * Build an OkHttpClient that trusts ONLY the certificate whose SHA-256
     * fingerprint matches [pinSha256]. Used for wss:// to a host with a
     * self-signed certificate — the user paired the fingerprint at Add
     * Host time and we refuse anything else.
     */
    private fun pinnedClient(pinSha256: String): OkHttpClient {
        val expected = pinSha256.replace(":", "").trim().uppercase()
        val tm = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                if (chain.isNullOrEmpty()) {
                    throw javax.net.ssl.SSLException("empty certificate chain")
                }
                val sha = MessageDigest.getInstance("SHA-256")
                    .digest(chain[0].encoded)
                    .joinToString("") { b -> "%02X".format(b) }
                if (sha != expected) {
                    throw javax.net.ssl.SSLException(
                        "certificate fingerprint mismatch: expected $expected, got $sha"
                    )
                }
            }
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
        val sslCtx = SSLContext.getInstance("TLS")
        sslCtx.init(null, arrayOf(tm), SecureRandom())
        return client.newBuilder()
            .sslSocketFactory(sslCtx.socketFactory, tm)
            .hostnameVerifier { _, _ -> true }  // self-signed, hostname not in cert
            .build()
    }

    fun disconnect() {
        closeSocketOnly(ConnectionState.Idle)
    }

    /**
     * @brief Sends one operation and waits for its response.
     * @param op Wire operation name, for example `conv.list`.
     * @param params Operation parameters.
     * @returns The host's response, or a failure when the socket is closed,
     *          the frame could not be queued, or no response arrived within
     *          [timeoutFor] for the frame size.
     */
    suspend fun send(op: String, params: JsonObject = JsonObject(emptyMap())): Result<RemoteResponse> {
        val socket = webSocket ?: return Result.failure(IllegalStateException("Not connected"))
        val requestId = UUID.randomUUID().toString()
        val response = CompletableDeferred<RemoteResponse>()
        pending[requestId] = response

        val envelope = RemoteOp(op = op, requestId = requestId, params = params)
        val payload = RemoteJson.encodeToString(RemoteOp.serializer(), envelope)
        if (!socket.send(payload)) {
            pending.remove(requestId)
            return Result.failure(IllegalStateException("WebSocket rejected outgoing operation"))
        }

        return try {
            val reply = withTimeoutOrNull(timeoutFor(payload.length)) { response.await() }
                ?: return Result.failure(
                    IllegalStateException("The host did not answer in time. Check the connection and try again."),
                )
            Result.success(reply)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            Result.failure(error)
        } finally {
            pending.remove(requestId)
        }
    }

    override fun close() {
        closeSocketOnly(ConnectionState.Idle)
        eventQueue.close()
        scope.cancel()
        pending.values.forEach { it.cancel() }
        pending.clear()
    }

    private fun closeSocketOnly(nextState: ConnectionState) {
        webSocket?.close(1000, "Closing session")
        webSocket = null
        pending.values.forEach { it.completeExceptionally(IllegalStateException("Connection closed")) }
        pending.clear()
        _connectionState.value = nextState
    }

    private inner class Listener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            _connectionState.value = ConnectionState.Open
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            runCatching {
                val root = RemoteJson.parseToJsonElement(text) as? JsonObject
                    ?: throw IllegalArgumentException("Protocol frame is not a JSON object")
                when (root["type"]?.jsonPrimitive?.content) {
                    "response" -> handleResponse(text)
                    "event" -> handleEvent(text)
                    else -> emitError("malformed_frame", "Unknown protocol frame type")
                }
            }.onFailure {
                emitError("malformed_frame", "Unable to parse host frame")
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            pending.values.forEach { it.completeExceptionally(t) }
            pending.clear()
            this@RemoteSession.webSocket = null
            _connectionState.value = ConnectionState.Failed
            emitError("connection_failed", t.message ?: "WebSocket connection failed")
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            pending.values.forEach {
                it.completeExceptionally(IllegalStateException("WebSocket closed: $code $reason"))
            }
            pending.clear()
            this@RemoteSession.webSocket = null
            _connectionState.value = ConnectionState.Closed
        }

        private fun handleResponse(text: String) {
            val response = RemoteJson.decodeFromString(RemoteResponse.serializer(), text)
            pending.remove(response.requestId)?.complete(response)
        }

        private fun handleEvent(text: String) {
            val event = RemoteJson.decodeFromString(RemoteEvent.serializer(), text)
            eventQueue.trySend(event)
        }

        private fun emitError(kind: String, detail: String) {
            eventQueue.trySend(
                RemoteEvent(
                    type = "event",
                    event = "error",
                    data = RemoteJson.encodeToJsonElement(
                        RemoteError.serializer(),
                        RemoteError(kind = kind, detail = detail),
                    ),
                ),
            )
        }
    }

    private companion object {
        /** Longest wait for a response to a small request before it fails. */
        const val REQUEST_TIMEOUT_MS = 30_000L

        /**
         * Extra wait per byte of outgoing frame, so a large attachment on a
         * slow uplink (about 64 KiB/s) is not reported as failed while it is
         * still being sent and the host is still saving it.
         */
        const val UPLOAD_MS_PER_KIB = 16L

        /**
         * @brief Response timeout for one request.
         * @param frameChars Length of the outgoing JSON frame.
         * @returns [REQUEST_TIMEOUT_MS] plus the upload allowance for the frame.
         */
        fun timeoutFor(frameChars: Int): Long =
            REQUEST_TIMEOUT_MS + (frameChars / 1024L) * UPLOAD_MS_PER_KIB

        /** WebSocket keepalive interval. */
        const val PING_INTERVAL_SECONDS = 20L
    }
}
