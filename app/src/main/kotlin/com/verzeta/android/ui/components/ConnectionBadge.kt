// SPDX-FileCopyrightText: 2026 Aditya Mehra <aix.m@outlook.com>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

/**
 * @file ConnectionBadge.kt
 * @brief Compact status pill that surfaces the current WebSocket
 *        connection state (Idle / Connecting / Live / Disconnected /
 *        Reconnecting) in the chat top bar.
 * @layer Frontend
 * @dependencies StatusDot, remote.ConnectionState, theme.VerzetaStatus;
 *               Jetpack Compose Material 3.
 */

package com.verzeta.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.verzeta.android.remote.ConnectionState
import com.verzeta.android.ui.theme.VerzetaStatus

/**
 * Compact pill that surfaces the current WebSocket connection state. Shown in
 * the chat top bar so the user can immediately see when a `Closed` or
 * `Failed` transition has interrupted streaming.
 */
@Composable
fun ConnectionBadge(state: ConnectionState, modifier: Modifier = Modifier) {
    val (label, status) = when (state) {
        ConnectionState.Idle -> "Idle" to VerzetaStatus.Neutral
        ConnectionState.Connecting -> "Connecting" to VerzetaStatus.Info
        ConnectionState.Open -> "Live" to VerzetaStatus.Ok
        ConnectionState.Closed -> "Disconnected" to VerzetaStatus.Warning
        ConnectionState.Failed -> "Reconnecting" to VerzetaStatus.Error
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(status = status, size = 6)
        Spacer(Modifier.size(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
