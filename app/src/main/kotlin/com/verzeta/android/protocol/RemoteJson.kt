// SPDX-FileCopyrightText: 2026 Aditya Mehra <aix.m@outlook.com>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

/**
 * @file RemoteJson.kt
 * @brief Shared kotlinx.serialization Json instance for the Verzeta wire
 *        protocol. Ignores unknown keys so newer hosts can add fields
 *        without breaking older clients.
 * @layer API
 * @dependencies kotlinx.serialization.json.
 */

package com.verzeta.android.protocol

import kotlinx.serialization.json.Json

/**
 * Shared JSON configuration for the Verzeta wire protocol.
 */
val RemoteJson: Json = Json {
    ignoreUnknownKeys = true
}
