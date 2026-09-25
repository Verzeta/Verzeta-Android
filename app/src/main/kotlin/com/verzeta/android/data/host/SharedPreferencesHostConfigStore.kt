// SPDX-FileCopyrightText: 2026 Aditya Mehra <aix.m@outlook.com>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

/**
 * @file SharedPreferencesHostConfigStore.kt
 * @brief SharedPreferences-backed implementation of HostConfigStore that
 *        persists the configured-host list as a single JSON-encoded entry
 *        in private app storage.
 * @layer Model
 * @dependencies HostConfigStore, HostConfig, Android SharedPreferences,
 *               kotlinx.serialization, kotlinx.coroutines (IO dispatcher).
 */

package com.verzeta.android.data.host

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Small JSON-backed host store. The list is private app data and contains no
 * auth token material.
 */
class SharedPreferencesHostConfigStore(context: Context) : HostConfigStore {
    private val preferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override suspend fun readHosts(): List<HostConfig> = withContext(Dispatchers.IO) {
        val encoded = preferences.getString(KEY_HOSTS, null) ?: return@withContext emptyList()
        runCatching {
            json.decodeFromString(ListSerializer(HostConfig.serializer()), encoded)
        }.getOrDefault(emptyList())
    }

    override suspend fun saveHosts(hosts: List<HostConfig>) = withContext(Dispatchers.IO) {
        val encoded = json.encodeToString(ListSerializer(HostConfig.serializer()), hosts)
        val saved = preferences.edit().putString(KEY_HOSTS, encoded).commit()
        check(saved) { "Failed to persist configured hosts" }
    }

    private companion object {
        const val PREFS_NAME = "verzeta_hosts"
        const val KEY_HOSTS = "hosts"
    }
}
