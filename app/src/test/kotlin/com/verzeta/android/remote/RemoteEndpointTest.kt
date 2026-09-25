// SPDX-FileCopyrightText: 2026 Aditya Mehra <aix.m@outlook.com>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

package com.verzeta.android.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RemoteEndpointTest {
    @Test
    fun websocketUrlUsesWsForPlainConnections() {
        val endpoint = RemoteEndpoint(host = "10.0.2.2", port = 9180, tls = false)

        assertEquals("ws://10.0.2.2:9180/ws", endpoint.websocketUrl())
    }

    @Test
    fun websocketUrlUsesWssForTlsConnections() {
        val endpoint = RemoteEndpoint(host = "example.local", port = 9443, tls = true)

        assertEquals("wss://example.local:9443/ws", endpoint.websocketUrl())
    }

    @Test
    fun rejectsInvalidPorts() {
        assertThrows(IllegalArgumentException::class.java) {
            RemoteEndpoint(host = "example.local", port = 0, tls = false)
        }
    }
}
