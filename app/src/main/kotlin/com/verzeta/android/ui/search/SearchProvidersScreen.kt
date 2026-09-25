// SPDX-FileCopyrightText: 2026 Aditya Mehra <aix.m@outlook.com>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

/**
 * @file SearchProvidersScreen.kt
 * @brief Web-search provider selection. Lists the host's search
 *        backends via `search.providers`, shows each one's status, and lets
 *        the user set the active backend via `search.set_active`. API keys,
 *        base URLs and custom-endpoint config are HOST-ONLY — configured on
 *        the desktop, never over the wire; this screen only selects + shows
 *        status.
 * @layer UI
 * @dependencies Jetpack Compose, Material3, MainViewModel, MainUiState,
 *               SearchProviderUi.
 */

package com.verzeta.android.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.verzeta.android.MainUiState
import com.verzeta.android.MainViewModel
import com.verzeta.android.data.host.HostStatus
import com.verzeta.android.data.model.SearchProviderUi
import com.verzeta.android.ui.components.EmptyState
import com.verzeta.android.ui.tools.TopBar

/**
 * Read-mostly browser for `search.providers`. Tapping a non-active provider
 * fires `search.set_active`. Configuration (keys / URLs / custom endpoint)
 * lives on the desktop host; an unconfigured provider set active simply
 * falls back to DuckDuckGo on the host.
 */
@Composable
fun SearchProvidersScreen(state: MainUiState, actions: MainViewModel) {
    val connected = state.activeHost?.status == HostStatus.Connected
    LaunchedEffect(connected) { if (connected) actions.refreshSearchProviders() }

    Column(modifier = Modifier.fillMaxSize()) {
        TopBar(
            title = "Web Search",
            subtitle = state.searchProvidersStatus.takeIf { it.isNotBlank() },
            onBack = actions::closeSearchProviders,
            onRefresh = if (connected) actions::refreshSearchProviders else null,
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        if (!connected) {
            EmptyState(
                title = "Not connected",
                detail = "Connect a host to choose a web-search provider.",
            )
            return@Column
        }
        if (state.searchProviders.isEmpty) {
            EmptyState(
                title = "No providers reported",
                detail = "The host has not reported any web-search providers yet.",
            )
            return@Column
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(state.searchProviders.providers, key = { it.id }) { provider ->
                SearchProviderRow(
                    provider = provider,
                    onClick = { actions.setActiveSearchProvider(provider.id) },
                )
            }
            item { Spacer(Modifier.height(40.dp)) }
        }
    }
}

private fun statusText(p: SearchProviderUi): String = when {
    p.active -> "Active"
    p.requiresApiKey && !p.hasKey ->
        "Needs an API key. Configure it on the desktop host."
    p.id == "searxng" && p.baseUrl.isBlank() ->
        "Needs a base URL. Configure it on the desktop host."
    p.id == "custom" ->
        "Configure the endpoint on the desktop host, then activate."
    else -> "Tap to set as active."
}

@Composable
private fun SearchProviderRow(provider: SearchProviderUi, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !provider.active, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(modifier = Modifier.padding(start = 14.dp).weight(1f)) {
            Text(
                provider.displayName,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                statusText(provider),
                style = MaterialTheme.typography.bodySmall,
                color = if (provider.active) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (provider.active) {
            Icon(
                Icons.Filled.Check,
                contentDescription = "Active",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
