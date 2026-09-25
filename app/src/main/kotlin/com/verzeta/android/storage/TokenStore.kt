// SPDX-FileCopyrightText: 2026 Aditya Mehra <aix.m@outlook.com>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

/**
 * @file TokenStore.kt
 * @brief Storage abstraction for host-issued pairing tokens, with both
 *        single-host (default) and per-host read/save/clear operations.
 * @layer Service
 * @dependencies None — pure Kotlin interface consumed by RemoteRepository
 *               and implemented by PrefsTokenStore.
 */

package com.verzeta.android.storage

/**
 * Durable storage for the host-issued pairing token. Implementations must not
 * log or expose the token outside explicit auth operations.
 */
interface TokenStore {
    suspend fun readToken(): String?
    suspend fun saveToken(token: String)
    suspend fun clearToken()
    suspend fun readToken(hostId: String): String?
    suspend fun saveToken(hostId: String, token: String)
    suspend fun clearToken(hostId: String)
}
