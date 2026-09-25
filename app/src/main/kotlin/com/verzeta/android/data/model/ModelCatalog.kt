// SPDX-FileCopyrightText: 2026 Aditya Mehra <aix.m@outlook.com>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

/**
 * @file ModelCatalog.kt
 * @brief Provider/model catalog models for the `models.catalog` snapshot:
 *        one entry per configured provider with its model list and
 *        capability flags, plus the host's currently active selection.
 * @layer Model
 * @dependencies None — pure Kotlin data declarations.
 */

package com.verzeta.android.data.model

/**
 * Provider entry from the host's `models.catalog` op.
 *
 *   provider_id            stable identifier the host uses to address the provider
 *                          (e.g. "openai", "anthropic", "ollama", or "custom.<slug>"
 *                          for user-configured custom OpenAI-API-compatible
 *                          servers).  Sent back on `models.set_active`.
 *   display_name           human label for the picker UI.  For custom servers
 *                          this is the user-chosen name from the desktop's
 *                          setup sheet (e.g. "LM Studio at home").
 *   models                 list of model names available under this provider.
 *   supports_streaming     whether the provider's chat-completion endpoint
 *                          emits SSE chunks.
 *   supports_tool_calling  whether the provider's chat-completion endpoint
 *                          accepts the OpenAI `tools` schema and emits
 *                          `tool_calls` chunks.
 *   supports_vision        whether the provider accepts image inputs via
 *                          `image_url` content parts.
 *
 * The three capability flags ride through every catalog snapshot so the
 * model-picker UI can render per-provider glyphs (· streaming · tools ·
 * vision) without an extra round-trip and so member-override flows can
 * pre-filter providers by required capability.
 */
data class ProviderUi(
    val providerId: String,
    val displayName: String,
    val models: List<String>,
    val supportsStreaming: Boolean = true,
    val supportsToolCalling: Boolean = false,
    val supportsVision: Boolean = false,
) {
    /**
     * True iff the provider id is namespaced under the host's
     * custom OpenAI-API-compatible server registry.  Lets the UI
     * branch on "is this a configured custom server" without
     * looking up a separate catalogue.
     */
    val isCustomServer: Boolean get() = providerId.startsWith("custom.")
}

/**
 * Full snapshot returned by `models.catalog`, plus the host's currently
 * active selection. Catalog is stable per host session — we cache it and
 * refresh on reconnect; the active pair is kept current via
 * `models.active_changed` events.
 *
 * Both `activeProvider` and `activeModel` may be empty when the host has
 * no provider configured yet, OR when the operator has not picked a model.
 * UI must treat empty as "no model selected" and gate new-chat creation
 * behind a picker.
 */
data class ModelCatalogUi(
    val providers: List<ProviderUi> = emptyList(),
    val activeProvider: String = "",
    val activeModel: String = "",
) {
    val hasActiveModel: Boolean get() = activeProvider.isNotBlank() && activeModel.isNotBlank()
    val isEmpty: Boolean get() = providers.isEmpty()

    fun isActive(providerId: String, modelName: String): Boolean =
        providerId == activeProvider && modelName == activeModel
}
