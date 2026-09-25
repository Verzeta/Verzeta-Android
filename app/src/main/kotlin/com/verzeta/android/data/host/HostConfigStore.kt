// SPDX-FileCopyrightText: 2026 Aditya Mehra <aix.m@outlook.com>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

/**
 * @file HostConfigStore.kt
 * @brief Persistence boundary interface for the configured-host list —
 *        read and save operations over HostConfig entries.
 * @layer Model
 * @dependencies HostConfig.
 */

package com.verzeta.android.data.host

/**
 * Persistence boundary for configured hosts.
 */
interface HostConfigStore {
    suspend fun readHosts(): List<HostConfig>
    suspend fun saveHosts(hosts: List<HostConfig>)
}
