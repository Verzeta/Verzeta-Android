// SPDX-FileCopyrightText: 2026 Aditya Mehra <aix.m@outlook.com>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

/**
 * @file HostConfig.kt
 * @brief Models for locally configured desktop hosts: the persisted host
 *        entry (endpoint + optional pinned TLS fingerprint), the live
 *        connection status, the host list-row projection, and the recent
 *        conversations shown on the landing screen.
 * @layer Model
 * @dependencies kotlinx.serialization (persisted host entry).
 */

package com.verzeta.android.data.host

import kotlinx.serialization.Serializable

@Serializable
/**
 * Locally configured desktop host. Authentication tokens are stored separately
 * by [com.verzeta.android.storage.PrefsTokenStore] (private SharedPreferences,
 * not Keystore — see that file's header for rationale) and are never embedded
 * here.
 */
data class HostConfig(
    val id: String,
    val name: String,
    val endpoint: String,
    val lastConnectedAt: Long? = null,
    /**
     * SHA-256 fingerprint of the host's self-signed TLS certificate, in
     * uppercase colon-separated hex (e.g. "AB:CD:..."). Set when the
     * user pairs over wss:// — the desktop's RemoteAccessDialog displays
     * this string and the user pastes it into the Add Host screen.
     * Empty / null means plain ws:// (no TLS) OR a cert signed by a
     * publicly-trusted CA.
     *
     * Pinning is required for self-signed certs because the device's
     * default trust store rejects them. RemoteSession.connect installs
     * a custom X509TrustManager that accepts ONLY this fingerprint.
     */
    val tlsCertSha256: String = "",
)

/**
 * Live connection state of a configured host as tracked by the session
 * layer — from connected through the auth / pairing prerequisites to a
 * hard error.
 */
enum class HostStatus {
    Connected,
    Disconnected,
    AuthRequired,
    PairingRequired,
    Error,
}

/**
 * Host list-row projection: the persisted [HostConfig] joined with its
 * current [HostStatus], measured latency, and a free-form detail line for
 * the hosts screen.
 */
data class HostUi(
    val config: HostConfig,
    val status: HostStatus,
    val latencyMs: Long? = null,
    val detail: String = "",
)

/**
 * One recent conversation shown on the landing screen, keyed by the host
 * it lives on, with feature badges and pinned / streaming indicators.
 */
data class RecentConversationUi(
    val id: String,
    val hostId: String,
    val title: String,
    val updatedLabel: String,
    val badges: List<RecentBadge> = emptyList(),
    val pinned: Boolean = false,
    val streaming: Boolean = false,
)

/**
 * Feature badges rendered on a recent-conversation card: has an active
 * plan, is agent-backed, owns canvases, or is a group chat.
 */
enum class RecentBadge {
    Plan,
    Agent,
    Canvas,
    Group,
}
