// SPDX-FileCopyrightText: 2026 Aditya Mehra <aix.m@outlook.com>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

/**
 * @file PrefsTokenStore.kt
 * @brief SharedPreferences-backed TokenStore keeping one pairing token per
 *        host. Deliberately not Keystore-encrypted so tokens survive Android
 *        Auto Backup restore; see the class documentation for the full
 *        rationale.
 * @layer Service
 * @dependencies TokenStore interface, Android SharedPreferences,
 *               kotlinx.coroutines (IO dispatcher).
 */

package com.verzeta.android.storage

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Per-host token storage backed by a private SharedPreferences file. Tokens
 * are stored as plain UTF-8 strings — NOT Keystore-encrypted — because:
 *
 * 1. The whole point of this file is to survive uninstall/reinstall via
 *    Android Auto Backup. Auto Backup carries SharedPreferences but does
 *    NOT carry Keystore master keys; on restore the ciphertext written by
 *    a Keystore-backed store would be undecryptable, and the user would be
 *    forced to re-pair every host. That defeats the purpose.
 *
 * 2. Tokens are 256-bit random per-device credentials. The host stores
 *    only `SHA-256(token)` and exposes `clients.list` + `clients.revoke`
 *    so the user can immediately invalidate any device that may have
 *    leaked. No part of the token leaks user data on its own.
 *
 * 3. Auto Backup itself is end-to-end encrypted with the user's Google
 *    account credentials (post-Android 9), so the backup blob is not
 *    plain text on Google's servers either.
 *
 * The XML rules in `res/xml/backup_rules.xml` /
 * `res/xml/data_extraction_rules.xml` whitelist this prefs file
 * explicitly so device transfer / cloud restore picks up tokens AND host
 * configs. The previous KeystoreTokenStore file is gone — there's no
 * point keeping a fallback that doesn't survive restore.
 */
class PrefsTokenStore(context: Context) : TokenStore {
    private val preferences = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override suspend fun readToken(): String? = readToken(DEFAULT_HOST_ID)

    override suspend fun saveToken(token: String) = saveToken(DEFAULT_HOST_ID, token)

    override suspend fun clearToken() = clearToken(DEFAULT_HOST_ID)

    override suspend fun readToken(hostId: String): String? = withContext(Dispatchers.IO) {
        preferences.getString(tokenKey(hostId), null)
    }

    override suspend fun saveToken(hostId: String, token: String) = withContext(Dispatchers.IO) {
        val saved = preferences.edit().putString(tokenKey(hostId), token).commit()
        check(saved) { "Failed to persist remote auth token" }
    }

    override suspend fun clearToken(hostId: String) = withContext(Dispatchers.IO) {
        val saved = preferences.edit().remove(tokenKey(hostId)).commit()
        check(saved) { "Failed to clear remote auth token" }
    }

    private fun tokenKey(hostId: String): String = "token_$hostId"

    private companion object {
        const val PREFS_NAME = "verzeta_remote_auth"
        const val DEFAULT_HOST_ID = "default"
    }
}
