// SPDX-FileCopyrightText: 2026 Aditya Mehra <aix.m@outlook.com>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

/**
 * @file ModelPickerSheet.kt
 * @brief Modal bottom sheet for selecting the host's active
 *        provider + model pair. Lists the full provider catalog with
 *        per-provider capability captions, highlights the active model,
 *        and shows a host-side-configuration hint when the catalog is
 *        empty.
 * @layer Frontend
 * @dependencies data.model.ModelCatalogUi + ProviderUi, EmptyState;
 *               Jetpack Compose Material 3.
 */

package com.verzeta.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verzeta.android.data.model.ModelCatalogUi
import com.verzeta.android.data.model.ProviderUi

/**
 * Bottom sheet for selecting the host's active provider+model. Reads the
 * full catalog from [com.verzeta.android.MainUiState.modelCatalog]; the
 * active pair is highlighted with a primary check icon.
 *
 * Two callers today:
 *   - ChatScreen top bar: tap the model badge → [onPick] just changes
 *     the active model.
 *   - ConversationsScreen "New chat": when no model is active yet,
 *     opens this sheet first → [onPick] chains into `conv.create` after
 *     `models.active_changed` arrives (see
 *     [MainViewModel.setActiveModelAndCreateConversation]).
 *
 * The empty-catalog hint mirrors what the spec calls out: configuration
 * is host-side, the user has to open Verzeta Studio on the desktop.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelPickerSheet(
    catalog: ModelCatalogUi,
    onPick: (providerId: String, modelName: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(bottom = 16.dp)) {
            Header(catalog = catalog)
            if (catalog.isEmpty) {
                EmptyState(
                    title = "No providers configured on host",
                    detail = "Open Verzeta Studio on the desktop and add an API key in " +
                        "Settings → Providers.",
                )
            } else {
                LazyColumn {
                    catalog.providers.forEach { provider ->
                        item(key = "p-${provider.providerId}") { ProviderHeader(provider) }
                        items(provider.models, key = { "m-${provider.providerId}-$it" }) { model ->
                            ModelRow(
                                providerId = provider.providerId,
                                modelName = model,
                                isActive = catalog.isActive(provider.providerId, model),
                                onClick = { onPick(provider.providerId, model) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(catalog: ModelCatalogUi) {
    Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
        Text("Select model", style = MaterialTheme.typography.titleLarge)
        if (catalog.hasActiveModel) {
            Text(
                "Active: ${catalog.activeProvider} · ${catalog.activeModel}",
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else if (!catalog.isEmpty) {
            Text(
                "No model selected. Pick one below to enable new chats.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ProviderHeader(provider: ProviderUi) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, top = 14.dp, bottom = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Cloud,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(8.dp))
            Text(
                provider.displayName.uppercase(),
                style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.2.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Text(
                provider.providerId,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
        // Capability / custom-server caption — same content the
        // MembershipEditor dropdown surfaces, so users see consistent
        // signals across every provider listing on Android.
        val caps = buildList {
            if (provider.isCustomServer) add("Custom server")
            if (provider.supportsStreaming) add("streaming")
            if (provider.supportsToolCalling) add("tools")
            if (provider.supportsVision) add("vision")
        }
        if (caps.isNotEmpty()) {
            Text(
                caps.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                modifier = Modifier.padding(start = 32.dp, top = 2.dp),
            )
        }
    }
}

@Composable
private fun ModelRow(
    providerId: String,
    modelName: String,
    isActive: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    if (isActive) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (isActive) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                modelName,
                style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                color = if (isActive) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                providerId,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
