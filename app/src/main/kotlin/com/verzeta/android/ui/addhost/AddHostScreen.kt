// SPDX-FileCopyrightText: 2026 Aditya Mehra <aix.m@outlook.com>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

/**
 * @file AddHostScreen.kt
 * @brief Add-host / pair-device screen. Collects the host name, WebSocket
 *        endpoint, optional TLS certificate fingerprint, client name, and
 *        the 6-digit pairing code shown by the desktop, then submits the
 *        `auth.pair` request via MainViewModel. Keeps the pair-code input
 *        visible above the soft keyboard while typing.
 * @layer Frontend
 * @dependencies MainViewModel / MainUiState, PairCodeInput component,
 *               VerzetaTheme semantic colors, adaptiveContentMaxWidth,
 *               Jetpack Compose Material 3.
 */

package com.verzeta.android.ui.addhost

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.verzeta.android.MainUiState
import com.verzeta.android.MainViewModel
import com.verzeta.android.ui.components.PairCodeInput
import com.verzeta.android.ui.theme.VerzetaTheme
import com.verzeta.android.ui.theme.adaptiveContentMaxWidth

/**
 * Add-host / pair-device screen. The host's `remote-ws` protocol exposes
 * exactly one pairing surface — `auth.pair` with a 6-digit code from the
 * desktop. There is no manual-token entry op and no QR pairing op; previous
 * iterations of this screen had a 3-tab segmented control that was pure UI
 * fiction. It has been removed so the surface matches the wire protocol.
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun AddHostScreen(state: MainUiState, actions: MainViewModel) {
    val pairCodeRequester = remember { BringIntoViewRequester() }
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
    Column(
        modifier = Modifier
            .fillMaxSize()
            // Add IME padding at the screen root so the soft keyboard
            // pushes content up — without this the pair-code field at
            // the bottom is covered by the keyboard and the user can't
            // see what they're typing.
            .imePadding(),
    ) {
        TopAppBar(
            title = { Text("Pair device") },
            navigationIcon = {
                IconButton(onClick = actions::showHome) {
                    Icon(Icons.Filled.Close, contentDescription = "Close")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
        )

        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.TopCenter,
        ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .then(adaptiveContentMaxWidth())
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Text(
                text = "On the desktop, open Settings → Remote Access → Manage Remote " +
                    "Access and click Generate pair code. Enter the 6-digit code together " +
                    "with the address shown under CLIENTS CAN REACH THIS HOST AT.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.size(20.dp))

            FieldLabel("Host name")
            OutlinedTextField(
                value = state.addHostName,
                onValueChange = actions::updateAddHostName,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("workshop-mac") },
                enabled = !state.busy,
            )

            Spacer(Modifier.size(16.dp))

            FieldLabel("Endpoint")
            OutlinedTextField(
                value = state.addEndpoint,
                onValueChange = actions::updateAddEndpoint,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("ws://10.0.2.2:9180/ws") },
                enabled = !state.busy,
            )

            // TLS certificate fingerprint — shown only when the user types
            // wss://, since plain ws:// has no cert to pin. Optional even
            // for wss:// (a CA-signed cert validates without pinning).
            if (state.addEndpoint.trim().startsWith("wss://", ignoreCase = true)) {
                Spacer(Modifier.size(16.dp))
                FieldLabel("TLS certificate fingerprint (SHA-256)")
                OutlinedTextField(
                    value = state.addTlsCertSha256,
                    onValueChange = actions::updateAddTlsCertSha256,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    minLines = 2,
                    placeholder = { Text("AB:CD:EF:01:23:…") },
                    enabled = !state.busy,
                )
                Spacer(Modifier.size(6.dp))
                Text(
                    "Copy the fingerprint shown by the desktop's "
                        + "Settings → Remote Access dialog. Required when "
                        + "the desktop uses a self-signed certificate; "
                        + "leave blank for a publicly trusted CA.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.size(16.dp))

            FieldLabel("Client name")
            OutlinedTextField(
                value = state.clientName,
                onValueChange = actions::updateClientName,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.busy,
            )

            Spacer(Modifier.size(20.dp))

            FieldLabel("Pair code")
            // bringIntoViewRequester scrolls the pair-code input above
            // the IME when it's focused, so the keyboard never covers
            // the digits the user is entering. Without this, on phones
            // with the keyboard fully visible, the input is below the
            // fold and the user types blind.
            Column(
                modifier = Modifier.bringIntoViewRequester(pairCodeRequester),
            ) {
                PairCodeInput(
                    value = state.pairCode,
                    onValueChange = actions::updatePairCode,
                    enabled = !state.busy,
                )
                LaunchedEffect(state.pairCode) {
                    if (state.pairCode.isNotEmpty()) {
                        pairCodeRequester.bringIntoView()
                    }
                }
            }
            Spacer(Modifier.size(6.dp))
            Text(
                "Tap any cell to bring up the number pad.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.size(20.dp))
            InfoPanel()

            if (state.status.isNotBlank()) {
                Text(
                    state.status,
                    modifier = Modifier.padding(top = 12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.size(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = actions::showHome) {
                    Text("Cancel")
                }
                Spacer(Modifier.weight(1f))
                if (state.busy) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    Spacer(Modifier.size(12.dp))
                }
                Button(
                    onClick = actions::submitAddHost,
                    enabled = !state.busy &&
                        state.pairCode.length == 6 &&
                        state.addEndpoint.isNotBlank(),
                ) {
                    Icon(Icons.Filled.Link, contentDescription = null)
                    Spacer(Modifier.size(6.dp))
                    Text("Pair")
                }
            }
        }  // Column (form)
        }  
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text,
        modifier = Modifier.padding(bottom = 6.dp),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun InfoPanel() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Info, contentDescription = null, tint = VerzetaTheme.semantic.info)
        Spacer(Modifier.size(10.dp))
        Text(
            text = "Pairing codes are single-use and expire 5 minutes after the desktop generates them. " +
                "The token issued by the host is stored in this app's private storage and is " +
                "covered by Google's end-to-end-encrypted Auto Backup. You can revoke any paired " +
                "device from the desktop or from Settings at any time.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
