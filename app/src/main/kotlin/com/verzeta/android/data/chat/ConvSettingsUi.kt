// SPDX-FileCopyrightText: 2026 Aditya Mehra <aix.m@outlook.com>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

/**
 * @file ConvSettingsUi.kt
 * @brief UI model snapshot of a conversation's configurable settings together with
 *        the host-global agent flags surfaced by `conv.settings.get`, plus the
 *        `AgentPattern` enum used across conversation and folder configuration.
 * @layer Model
 * @dependencies None — pure Kotlin data declarations.
 */

package com.verzeta.android.data.chat

/**
 * One-shot snapshot of a conversation's settings + the host-global flags
 * the desktop's RightSettingsPanel renders inline. Wire shape from
 * `AgentSettingsController::conversationSettingsFor`
 * (agent-settings-controller.cpp:201) plus a `ragEnabled` field mixed in
 * by `Session::opConvSettingsGet` at remote-ws-session.cpp:3338-3342.
 *
 * **camelCase**: response keys come back camelCase because the host
 * doesn't normalise on the way out. Write payloads (`conv.settings.save`)
 * use snake_case (`system_prompt`, `max_tokens`, etc.) per the protocol
 * convention.
 *
 * **Everything here is per-conversation.** `agentPattern`,
 * `requireConfirmation` and `toolsEnabled` each take an explicit `conv_id`
 * on the wire, and `ragEnabled` lives on the conversation's `llm_config`.
 * None of them is host-wide, so no panel should label them "global". This
 * paragraph used to claim the opposite, which is how the RAG toggle ended up
 * writing through a host-global op that no longer exists.
 *
 * Validation bounds (host enforces, surface client-side too):
 *   - `temperature` ∈ [0.0, 2.0]
 *   - `maxTokens` ∈ [128, 128_000]
 *   - `contextWindow` ∈ [1024, 131_072]
 *   - `autoSurfaceMaxPerDay` ∈ [1, 24]
 */
data class ConvSettingsUi(
    val systemPrompt: String = "",
    val temperature: Double = 0.7,
    val maxTokens: Int = 4096,
    val contextWindow: Int = 8192,
    val streaming: Boolean = true,
    val thinking: Boolean = false,
    val providerId: String = "",
    val modelName: String = "",
    val isGroup: Boolean = false,
    val folderId: String = "",
    val primaryAgentId: String = "",
    val heartbeatAutoSurface: Boolean = false,
    val autoSurfaceMaxPerDay: Int = 1,
    // Host-global flags carried inline so the panel populates with one op.
    val agentPattern: AgentPattern = AgentPattern.Direct,
    val requireConfirmation: Boolean = false,
    val toolsEnabled: Boolean = true,
    val ragEnabled: Boolean = false,
    // Sampling overrides added host-side for advanced per-conversation
    // tuning. Values use the host's -1 sentinel for "use model default";
    // the host's per-(provider, model) recipe overrides them at request
    // time while [forceAppSampling] is on.
    /** Top-K sampling override; -1 = model default. */
    val topK: Double = -1.0,
    /** Top-P (nucleus) sampling override; -1 = model default. */
    val topP: Double = -1.0,
    /** Repeat penalty override; -1 = model default. */
    val repeatPenalty: Double = -1.0,
    /** Presence penalty override; -1 = model default. */
    val presencePenalty: Double = -1.0,
    /** Frequency penalty override; -1 = model default. */
    val frequencyPenalty: Double = -1.0,
    /**
     * When true (default) the host force-applies its app-recommended
     * sampling recipe for models that have one (e.g. qwen3.5/3.6 on
     * Ollama), overriding the sliders above at request time.
     */
    val forceAppSampling: Boolean = true,
    /**
     * Embed the written tool list in the system prompt. Default OFF —
     * it duplicates the structured tool channel and costs ~3,000
     * tokens per request; only some smaller models need it.
     */
    val toolsInSystemPrompt: Boolean = false,
    /**
     * Dynamic compaction: the host summarises older messages in the
     * background so long conversations never lose the thread.
     */
    val dynamicCompactEnabled: Boolean = true,
    /**
     * Proactive compaction cadence in agent replies (0 = only under
     * context pressure). Host clamps to [0, 500].
     */
    val compactEveryTurns: Int = 20,
)

/**
 * Agent execution pattern. Wire enum string at agent.pattern{,.set} —
 * validated at remote-ws-session.cpp:3592-3604.
 */
enum class AgentPattern(val wireValue: String, val displayName: String) {
    Direct("direct", "Direct"),
    React("react", "ReAct"),
    Planner("planner", "Planner"),
    Router("router", "Router"),
    MultiAgent("multi_agent", "Multi-agent"),
    Memory("memory", "Memory");

    companion object {
        fun fromWire(value: String?): AgentPattern =
            entries.firstOrNull { it.wireValue == value } ?: Direct
    }
}
