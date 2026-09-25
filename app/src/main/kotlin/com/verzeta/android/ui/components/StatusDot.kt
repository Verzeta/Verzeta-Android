// SPDX-FileCopyrightText: 2026 Aditya Mehra <aix.m@outlook.com>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

/**
 * @file StatusDot.kt
 * @brief Small coloured circle indicating a semantic status, plus a
 *        HostStatus convenience wrapper and the HostStatus →
 *        VerzetaStatus mapping used across host listings.
 * @layer Frontend
 * @dependencies data.host.HostStatus, theme.VerzetaStatus +
 *               theme.statusColor; Jetpack Compose foundation.
 */

package com.verzeta.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.verzeta.android.data.host.HostStatus
import com.verzeta.android.ui.theme.VerzetaStatus
import com.verzeta.android.ui.theme.statusColor

@Composable
fun StatusDot(status: VerzetaStatus, modifier: Modifier = Modifier, size: Int = 8) {
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(statusColor(status)),
    )
}

@Composable
fun HostStatusDot(status: HostStatus, modifier: Modifier = Modifier, size: Int = 8) {
    StatusDot(status = status.toVerzetaStatus(), modifier = modifier, size = size)
}

fun HostStatus.toVerzetaStatus(): VerzetaStatus = when (this) {
    HostStatus.Connected -> VerzetaStatus.Ok
    HostStatus.AuthRequired,
    HostStatus.PairingRequired -> VerzetaStatus.Warning
    HostStatus.Error -> VerzetaStatus.Error
    HostStatus.Disconnected -> VerzetaStatus.Neutral
}
