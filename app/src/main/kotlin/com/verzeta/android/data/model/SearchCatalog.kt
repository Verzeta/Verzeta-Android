// SPDX-FileCopyrightText: 2026 Aditya Mehra <aix.m@outlook.com>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

/**
 * @file SearchCatalog.kt
 * @brief Web-search provider models for the host `search.providers` op:
 *        one entry per backend with its config status, plus the
 *        currently active selection. API keys are host-only — `hasKey` is a
 *        boolean status, never the key itself.
 * @layer Model
 * @dependencies None — pure Kotlin data declarations.
 */

package com.verzeta.android.data.model

/**
 * One web-search backend from the host's `search.providers` op.
 *
 *   id              stable identifier sent back on `search.set_active`
 *                   (e.g. "ddg-html", "tavily", "searxng", "custom").
 *   displayName     human label for the picker UI.
 *   requiresApiKey  whether the backend needs a host-side API key.
 *   hasKey          whether a key is configured on the host (status only).
 *   active          whether this is the active backend.
 *   baseUrl         configured base URL / override (empty when none).
 */
data class SearchProviderUi(
    val id: String,
    val displayName: String,
    val requiresApiKey: Boolean = false,
    val hasKey: Boolean = false,
    val active: Boolean = false,
    val baseUrl: String = "",
)

/** Snapshot of the host's web-search provider catalogue. */
data class SearchProvidersUi(
    val providers: List<SearchProviderUi> = emptyList(),
) {
    val activeId: String get() = providers.firstOrNull { it.active }?.id ?: ""
    val isEmpty: Boolean get() = providers.isEmpty()
}
