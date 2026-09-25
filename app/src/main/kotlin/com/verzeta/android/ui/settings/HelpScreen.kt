// SPDX-FileCopyrightText: 2026 Aditya Mehra <aix.m@outlook.com>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

/**
 * @file HelpScreen.kt
 * @brief In-app Help & Docs screen: an index of the bundled user docs plus
 *        a reader that loads the selected markdown from app assets and
 *        renders it with MarkdownText.
 * @layer Frontend
 * @dependencies MainViewModel + MainUiState, VerzetaTopBar / SectionHeader /
 *               MarkdownText components, Android asset manager, Compose
 *               Material3.
 */

package com.verzeta.android.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.verzeta.android.MainUiState
import com.verzeta.android.MainViewModel
import com.verzeta.android.ui.components.MarkdownText
import com.verzeta.android.ui.components.SectionHeader
import com.verzeta.android.ui.components.VerzetaTopBar
import com.verzeta.android.ui.theme.adaptiveContentMaxWidth

/**
 * In-app Help & Docs screen.
 *
 * Two-level surface:
 *   1. Index — lists all 16 bundled user docs by friendly title.
 *   2. Reader — when [MainUiState.selectedHelpDoc] is set, loads the
 *      corresponding markdown from `assets/help/<slug>.md` and renders
 *      it with [MarkdownText].
 *
 * The bundled markdown is regenerated from the canonical `docs/User`
 * sources at every build by the `copyUserHelpDocs` gradle task —
 * `app/src/main/assets/help/` is gitignored so there's only one source
 * of truth on disk.
 *
 * Layer: Frontend UI.
 * Dependencies: VerzetaTopBar, MarkdownText, asset loader.
 */
@Composable
fun HelpScreen(state: MainUiState, actions: MainViewModel) {
    val slug = state.selectedHelpDoc
    if (slug == null) HelpIndex(actions = actions)
    else HelpReader(slug = slug, actions = actions)
}

/** Index of all bundled docs, in display order. */
@Composable
private fun HelpIndex(actions: MainViewModel) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(modifier = Modifier.fillMaxSize().then(adaptiveContentMaxWidth())) {
            item {
                VerzetaTopBar(
                    title = "Help & docs",
                    onBack = actions::closeHelp,
                )
            }
            item { Spacer(Modifier.height(8.dp)) }
            item {
                Text(
                    text = "Everything Verzeta for Android can do, in plain English. " +
                        "Tap a topic to read it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }
            item { Spacer(Modifier.height(8.dp)) }
            item { SectionHeader("Topics") }
            items(HELP_ENTRIES, key = { it.slug }) { entry ->
                HelpRow(entry = entry, onClick = { actions.openHelpDoc(entry.slug) })
            }
            item { Spacer(Modifier.height(40.dp)) }
        }
    }
}

/** Reader for one bundled markdown doc. */
@Composable
private fun HelpReader(slug: String, actions: MainViewModel) {
    val context = LocalContext.current
    var content by remember(slug) { mutableStateOf<String?>(null) }
    var error by remember(slug) { mutableStateOf<String?>(null) }
    val title = HELP_ENTRIES.firstOrNull { it.slug == slug }?.title ?: slug

    LaunchedEffect(slug) {
        try {
            content = context.assets
                .open("help/$slug.md")
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
        } catch (t: Throwable) {
            // Most likely cause is the copyUserHelpDocs gradle task did not
            // run (e.g. someone built via Android Studio without sync). Show
            // a clear error so the user knows it's a build-side issue.
            error = "Could not load this doc: ${t.message ?: "unknown error"}"
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(adaptiveContentMaxWidth())
                .verticalScroll(rememberScrollState()),
        ) {
            VerzetaTopBar(
                title = title,
                onBack = actions::closeHelpDoc,
            )
            Spacer(Modifier.height(16.dp))
            val body = content
            if (body != null) {
                MarkdownText(
                    text = stripHtmlComments(body),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                )
            } else if (error != null) {
                Text(
                    text = error!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(24.dp),
                )
            } else {
                Text(
                    text = "Loading…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun HelpRow(entry: HelpEntry, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.MenuBook,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(modifier = Modifier.fillMaxWidth(0.85f)) {
            Text(entry.title, style = MaterialTheme.typography.titleSmall)
            if (entry.summary.isNotBlank()) {
                Text(
                    entry.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Remove HTML-comment audit citations like `<!-- [A§3.2] -->` so they
 * don't render in the user-facing view. The citations exist in the
 * source markdown for review traceability; they're not meant to
 * appear in-app.
 */
private fun stripHtmlComments(source: String): String =
    Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL).replace(source, "").trimEnd() + "\n"

private data class HelpEntry(
    val slug: String,
    val title: String,
    val summary: String,
)

private val HELP_ENTRIES = listOf(
    HelpEntry("00-introduction", "What Verzeta for Android is",
        "How the Android app pairs with Verzeta Studio on the desktop, and what it can and can't do."),
    HelpEntry("01-install-and-pair", "Install and pair",
        "Step-by-step pairing with your desktop using the 6-digit code."),
    HelpEntry("02-conversations", "Browse conversations",
        "The five sections of the bucketed conversations list and how to manage them."),
    HelpEntry("03-chatting", "Chatting",
        "Send messages, attach files, stop streaming, copy text."),
    HelpEntry("04-projects-and-rooms", "Projects and Project Rooms",
        "Folders, project rooms, group chats, members, and kickoff."),
    HelpEntry("05-canvas", "Canvas",
        "View, edit, and run code or text files the assistant produces."),
    HelpEntry("06-tasks-and-tool-calls", "Tasks and tool calls",
        "Plans overlay, step intervention, the tool-call approval modal."),
    HelpEntry("07-heartbeats", "Heartbeats",
        "When and how to use scheduled agent runs."),
    HelpEntry("08-artifacts-and-media", "Artifacts and media",
        "Save generated files to your device, and view generated images and audio."),
    HelpEntry("09-host-catalogs", "Host catalogs",
        "Browse the host's tools, MCP servers, skills and web search providers."),
    HelpEntry("10-activity-timeline", "Activity timeline",
        "Read-only audit log of what happened in a project."),
    HelpEntry("11-polls", "Polls",
        "Inline voting cards in the chat stream."),
    HelpEntry("12-settings", "Settings",
        "Hosts, paired devices, host catalogs, multi-host management."),
    HelpEntry("13-troubleshooting", "Troubleshooting",
        "Common pairing and connection issues, with fixes."),
    HelpEntry("14-faq", "FAQ",
        "Quick answers to the most common questions."),
    HelpEntry("15-privacy-summary", "Privacy at a glance",
        "What the app does and doesn't collect, on one screen."),
)
