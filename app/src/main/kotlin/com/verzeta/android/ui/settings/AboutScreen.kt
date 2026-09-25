// SPDX-FileCopyrightText: 2026 Aditya Mehra <aix.m@outlook.com>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

/**
 * @file AboutScreen.kt
 * @brief About surface with the Verzeta brand mark, app version (sourced
 *        from BuildConfig), in-app Help link, privacy/license rows, and an
 *        open-source attribution list. Reached from the Settings screen.
 * @layer Frontend
 * @dependencies MainViewModel + MainUiState, VerzetaTopBar / SectionHeader /
 *               SettingsRow / VerzetaMark components, Compose LocalUriHandler
 *               for HTTPS links, BuildConfig.
 */

package com.verzeta.android.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.verzeta.android.BuildConfig
import com.verzeta.android.MainUiState
import com.verzeta.android.MainViewModel
import com.verzeta.android.ui.components.SectionHeader
import com.verzeta.android.ui.components.VerzetaMark
import com.verzeta.android.ui.components.VerzetaTopBar
import com.verzeta.android.ui.theme.adaptiveContentMaxWidth

/**
 * About surface with the real Verzeta brand, version (sourced from
 * BuildConfig so it cannot drift on a release bump), in-app Help link,
 * privacy policy, license, and a factual open-source attribution list.
 *
 * Reached from SettingsScreen's About row.
 */
@Composable
fun AboutScreen(state: MainUiState, actions: MainViewModel) {
    val uri = LocalUriHandler.current
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(modifier = Modifier.fillMaxSize().then(adaptiveContentMaxWidth())) {
            item {
                VerzetaTopBar(
                    title = "About",
                    onBack = actions::closeAbout,
                )
            }
            item { Spacer(Modifier.height(16.dp)) }

            item {
                // Brand header — big mark + name + tagline. Centered to make
                // this read like an actual About surface (Material apps do this:
                // see Settings → About on Pixel devices for the pattern).
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    VerzetaMark(size = 80)
                    Text(
                        text = "Verzeta",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Paired Android client for the Verzeta Studio desktop",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        // Pull version directly from BuildConfig so the
                        // value cannot drift from app/build.gradle.
                        text = "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item { Spacer(Modifier.height(8.dp)) }

            item { SectionHeader("Help") }
            item {
                SettingsRow(
                    icon = Icons.Filled.MenuBook,
                    title = "Help & docs",
                    subtitle = "In-app guides for pairing, chatting, projects, canvas, settings, troubleshooting, and the FAQ.",
                    onClick = actions::showHelp,
                )
            }

            item { SectionHeader("Links") }
            item {
                SettingsRow(
                    icon = Icons.Filled.Public,
                    title = "Website",
                    subtitle = "https://verzeta.com",
                    onClick = { uri.openUri("https://verzeta.com") },
                )
            }
            item {
                SettingsRow(
                    icon = Icons.Filled.Description,
                    title = "Privacy policy",
                    subtitle = "How Verzeta handles your data. The Android client " +
                        "stores only the pairing token your desktop issued; nothing " +
                        "leaves your device except messages you send to your own host.",
                    // Once the privacy policy is hosted at a stable HTTPS
                    // URL this row points at it. Until then the link points
                    // at the eventual canonical URL — it may 404 pre-launch;
                    // the in-app Help & docs above contains the same content.
                    onClick = { uri.openUri("https://verzeta.com/privacy") },
                )
            }

            item { SectionHeader("Project") }
            item {
                SettingsRow(
                    icon = Icons.Filled.VerifiedUser,
                    title = "License",
                    subtitle = "This Android client: LGPL-3.0-or-later. " +
                        "Verzeta Studio (the desktop app) is open source — see " +
                        "the desktop project's LICENSING for details. " +
                        "The Android client is a paired frontend.",
                )
            }
            item {
                SettingsRow(
                    icon = Icons.Filled.Info,
                    title = "What this app does",
                    subtitle = "Verzeta is a multi-agent AI workspace that runs on " +
                        "your desktop. This Android client pairs over WebSocket and " +
                        "lets you chat with the agents your desktop hosts. No " +
                        "data is processed on Verzeta-controlled servers.",
                )
            }

            item { SectionHeader("Open-source libraries") }
            ATTRIBUTIONS.forEach { line ->
                item {
                    SettingsRow(
                        icon = Icons.Filled.VerifiedUser,
                        title = line.name,
                        subtitle = "${line.license} · ${line.purpose}",
                    )
                }
            }

            item { Spacer(Modifier.height(40.dp)) }
        }
    }
}

/**
 * One entry in the open-source attribution list. Update this list
 * whenever `gradle/libs.versions.toml` adds or removes a dependency.
 */
private data class Attribution(val name: String, val license: String, val purpose: String)

private val ATTRIBUTIONS = listOf(
    Attribution(
        "Jetpack Compose + AndroidX",
        "Apache 2.0",
        "UI framework, activity host, lifecycle integration",
    ),
    Attribution(
        "Material 3 + WindowSizeClass",
        "Apache 2.0",
        "Material You theming, adaptive layouts (phone / tablet / TV / DeX)",
    ),
    Attribution(
        "kotlinx.coroutines",
        "Apache 2.0",
        "Coroutines runtime",
    ),
    Attribution(
        "kotlinx.serialization",
        "Apache 2.0",
        "Wire-frame JSON serialisation",
    ),
    Attribution(
        "OkHttp",
        "Apache 2.0",
        "WebSocket connection to your paired desktop only",
    ),
)
