// SPDX-FileCopyrightText: 2026 Aditya Mehra <aix.m@outlook.com>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

/**
 * @file ConvSettingsScreen.kt
 * @brief Per-conversation settings panel — Android port of the desktop
 *        right-hand settings panel. Loads everything via
 *        `conv.settings.get` and saves edits through `conv.settings.save`
 *        (debounced for free-form inputs) across sections for members,
 *        agent, model, system prompt, sampling parameters, tools,
 *        skills, heartbeat, compaction and RAG.
 * @layer Frontend
 * @dependencies MainViewModel + MainUiState, data.chat.ConvSettingsUi +
 *               AgentPattern, data.agent.AgentSummaryUi,
 *               ModelPickerSheet, SectionHeader; Jetpack Compose
 *               Material 3.
 */

package com.verzeta.android.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.verzeta.android.MainUiState
import com.verzeta.android.MainViewModel
import com.verzeta.android.data.agent.AgentSummaryUi
import com.verzeta.android.data.chat.AgentPattern
import com.verzeta.android.data.chat.ConvSettingsUi
import com.verzeta.android.data.host.HostStatus
import com.verzeta.android.ui.components.SectionHeader
import com.verzeta.android.ui.tools.TopBar
import kotlinx.coroutines.delay

/**
 * Per-conversation settings panel — Android port of
 * `frontend/pages/RightSettingsPanel.qml`. One `conv.settings.get` op
 * populates everything (per-conv + host-global flags). Edits flow back
 * through `conv.settings.save` (partial-field, 400 ms debounce on
 * free-form text inputs) or one of the dedicated setters.
 *
 * Sections (top → bottom, matches desktop order):
 *   1. Group Members — only when isGroup; routes to MembershipEditor sheet
 *      (existing surface — not duplicated here).
 *   2. Primary Agent — 1:1 chats only.
 *   3. Model — opens [com.verzeta.android.ui.components.ModelPickerSheet].
 *   4. System Prompt — debounced save.
 *   5. Parameters: temperature, max tokens, context window, streaming,
 *      thinking. Debounced save for sliders / numeric inputs.
 *   6. Tools toggle for this conversation.
 *   7. Agent: pattern dropdown + require-confirmation toggle, both stored
 *      per conversation.
 *   8. Preferred Skills — for a chat inside a project, opens
 *      PreferredSkillsSheet on the project's list and saves it to the
 *      host on confirm.
 *   9. Heartbeat: per-conversation auto-surface gate + cap. When
 *      enabling for the first time, the ViewModel pulls
 *      `heartbeat.suggested_cap_for_conv` to seed an intelligent default.
 *   10. RAG toggle for this conversation. Its state is read from the
 *       conversation's stored config (see MainViewModel.loadConvSettings).
 *
 * Validation bounds (host-enforced) are also enforced
 * client-side via `coerceIn` / numeric input filters so the user gets
 * instant feedback before the wire round-trip.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConvSettingsScreen(state: MainUiState, actions: MainViewModel) {
    val convId = state.selectedConversationId ?: return
    val connected = state.activeHost?.status == HostStatus.Connected
    val settings = state.convSettings[convId] ?: ConvSettingsUi()

    LaunchedEffect(convId, connected) {
        if (connected) actions.loadConvSettings(convId)
        if (connected && state.agents.isEmpty()) actions.loadAgents()
        if (connected && state.skills.isEmpty()) actions.refreshSkills()
    }

    var primaryAgentPickerOpen by remember { mutableStateOf(false) }
    var modelPickerOpen by remember { mutableStateOf(false) }
    var preferredSkillsOpen by remember { mutableStateOf(false) }
    var patternPickerOpen by remember { mutableStateOf(false) }

    // Load the parent project's preferred skills, which the "Edit
    // preferred skills" row edits. Conversation-scoped preferred skills are
    // not offered here.
    LaunchedEffect(settings.folderId, connected) {
        if (connected && settings.folderId.isNotBlank()) {
            actions.loadFolderPreferredSkills(settings.folderId)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopBar(
            title = "Conversation settings",
            subtitle = state.selectedConversation?.title,
            onBack = actions::closeConvSettings,
            onRefresh = if (connected) ({ actions.loadConvSettings(convId) }) else null,
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        LazyColumn(modifier = Modifier.fillMaxSize()) {

            if (settings.isGroup) {
                item { SectionHeader("Group members") }
                item {
                    InfoCard(
                        "Group members are managed in Verzeta Studio on the desktop. " +
                            "Open this conversation there to add " +
                            "or remove members.",
                    )
                }
            } else {
                item { SectionHeader("Primary agent") }
                item {
                    PrimaryAgentRow(
                        currentAgentId = settings.primaryAgentId,
                        agents = state.agents,
                        onPick = { primaryAgentPickerOpen = true },
                        onClear = { actions.setConvPrimaryAgent(convId, "") },
                    )
                }
            }

            item { SectionHeader("Model") }
            item {
                ModelRow(
                    providerId = settings.providerId,
                    modelName = settings.modelName,
                    onClick = { modelPickerOpen = true },
                )
            }

            item { SectionHeader("System prompt") }
            item {
                SystemPromptField(
                    initial = settings.systemPrompt,
                    onSave = { actions.saveConvSettings(convId, systemPrompt = it) },
                )
            }

            item { SectionHeader("Parameters") }
            item {
                ParametersBlock(
                    settings = settings,
                    onTemperature = { actions.saveConvSettings(convId, temperature = it) },
                    onMaxTokens = { actions.saveConvSettings(convId, maxTokens = it) },
                    onContextWindow = { actions.saveConvSettings(convId, contextWindow = it) },
                    onStreaming = { actions.saveConvSettings(convId, streaming = it) },
                    onThinking = { actions.saveConvSettings(convId, thinking = it) },
                )
            }

            item { SectionHeader("Sampling") }
            item {
                ToggleRow(
                    title = "Use app-recommended sampling",
                    detail = "Apply Verzeta's tested recipe for models that ship with " +
                        "problematic defaults (overrides the sliders below while on).",
                    icon = Icons.Filled.Tune,
                    checked = settings.forceAppSampling,
                    onCheckedChange = { actions.saveConvSettings(convId, forceAppSampling = it) },
                )
            }
            item {
                SamplingBlock(
                    settings = settings,
                    enabled = !settings.forceAppSampling,
                    onTopK = { actions.saveConvSettings(convId, topK = it) },
                    onTopP = { actions.saveConvSettings(convId, topP = it) },
                    onRepeatPenalty = { actions.saveConvSettings(convId, repeatPenalty = it) },
                    onPresencePenalty = { actions.saveConvSettings(convId, presencePenalty = it) },
                    onFrequencyPenalty = { actions.saveConvSettings(convId, frequencyPenalty = it) },
                )
            }

            item { SectionHeader("Tools") }
            item {
                ToggleRow(
                    title = "Tools enabled",
                    detail = "Whether the agent may call tools in this conversation.",
                    icon = Icons.Filled.Build,
                    checked = settings.toolsEnabled,
                    onCheckedChange = { actions.setToolsEnabled(convId, it) },
                )
            }
            item {
                ToggleRow(
                    title = "Describe tools in system prompt",
                    detail = "Adds a written tool list (~3,000 tokens per request). Only " +
                        "needed if a smaller model keeps forgetting its tools.",
                    icon = Icons.Filled.Build,
                    checked = settings.toolsInSystemPrompt,
                    onCheckedChange = { actions.saveConvSettings(convId, toolsInSystemPrompt = it) },
                )
            }

            item { SectionHeader("Conversation memory") }
            item {
                ToggleRow(
                    title = "Dynamic compaction",
                    detail = "Summarise older messages in the background so long chats " +
                        "never lose the thread. Your transcript is never altered.",
                    icon = Icons.Filled.Compress,
                    checked = settings.dynamicCompactEnabled,
                    onCheckedChange = { actions.saveConvSettings(convId, dynamicCompactEnabled = it) },
                )
            }
            item {
                Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                    IntField(
                        label = "Refresh summary every … agent replies",
                        value = settings.compactEveryTurns,
                        min = 0,
                        max = 500,
                        onCommit = { actions.saveConvSettings(convId, compactEveryTurns = it) },
                        hint = "0 = only when the context window fills up.",
                    )
                }
            }

            item { SectionHeader("Agent") }
            item {
                ListRow(
                    icon = Icons.Filled.SmartToy,
                    title = "Agent pattern",
                    detail = "${settings.agentPattern.displayName} · per-conversation",
                    onClick = { patternPickerOpen = true },
                )
            }
            item {
                ToggleRow(
                    title = "Require confirmation",
                    detail = "Pause for confirmation before each tool call in this conversation.",
                    icon = Icons.Filled.Check,
                    checked = settings.requireConfirmation,
                    onCheckedChange = { actions.setRequireConfirmation(convId, it) },
                )
            }

            item { SectionHeader("Preferred skills") }
            item {
                if (settings.folderId.isBlank()) {
                    InfoCard(
                        "Preferred skills are stored per scope. Move this conversation into a " +
                            "project to manage its preferred skills, or use the desktop's " +
                            "conversation settings for a per-conversation override.",
                    )
                } else {
                    ListRow(
                        icon = Icons.Filled.AutoFixHigh,
                        title = "Edit preferred skills",
                        detail = "Manages the parent project's preferred-skills list. " +
                            "Per-conversation override is desktop-only for now.",
                        onClick = { preferredSkillsOpen = true },
                    )
                }
            }

            item { SectionHeader("Heartbeat") }
            item {
                HeartbeatGateBlock(
                    allow = settings.heartbeatAutoSurface,
                    cap = settings.autoSurfaceMaxPerDay,
                    onToggle = { actions.setConvHeartbeatGate(convId, it) },
                    onCap = { actions.setConvHeartbeatGateCap(convId, it) },
                )
            }

            item { SectionHeader("RAG") }
            item {
                ToggleRow(
                    title = "RAG enabled",
                    detail = "Use retrieval-augmented generation in this conversation.",
                    icon = Icons.Filled.Memory,
                    checked = settings.ragEnabled,
                    onCheckedChange = { actions.setRagEnabled(convId, it) },
                )
            }

            item { Spacer(Modifier.size(40.dp)) }
        }
    }

    if (primaryAgentPickerOpen) {
        AgentPickerSheet(
            agents = state.agents,
            currentId = settings.primaryAgentId,
            onPick = { id ->
                actions.setConvPrimaryAgent(convId, id)
                primaryAgentPickerOpen = false
            },
            onDismiss = { primaryAgentPickerOpen = false },
        )
    }

    if (modelPickerOpen) {
        com.verzeta.android.ui.components.ModelPickerSheet(
            catalog = state.modelCatalog,
            onPick = { providerId, modelName ->
                actions.setActiveModel(providerId, modelName)
                modelPickerOpen = false
            },
            onDismiss = { modelPickerOpen = false },
        )
    }

    if (patternPickerOpen) {
        AgentPatternSheet(
            current = settings.agentPattern,
            onPick = { p ->
                actions.setAgentPattern(convId, p)
                patternPickerOpen = false
            },
            onDismiss = { patternPickerOpen = false },
        )
    }

    if (preferredSkillsOpen && settings.folderId.isNotBlank()) {
        val pref = state.folderPreferredSkills[settings.folderId]
        com.verzeta.android.ui.folders.PreferredSkillsSheet(
            allSkills = state.skills,
            initiallySelected = pref?.skillIds.orEmpty().toSet(),
            initialExposeOnly = pref?.exposeOnly ?: false,
            onConfirm = { selected, exposeOnly ->
                // Saved to the host right away; there is no later save here.
                actions.savePreferredSkills(settings.folderId, selected, exposeOnly)
                preferredSkillsOpen = false
            },
            onDismiss = { preferredSkillsOpen = false },
        )
    }
}

@Composable
private fun PrimaryAgentRow(
    currentAgentId: String,
    agents: List<AgentSummaryUi>,
    onPick: () -> Unit,
    onClear: () -> Unit,
) {
    val agent = agents.firstOrNull { it.id == currentAgentId }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.SmartToy,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                agent?.name ?: if (currentAgentId.isBlank()) "Default (no agent)" else "Unknown agent",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                if (agent != null) agent.description.ifBlank { "Agent template" }
                else "Tap to pick an agent for this 1:1 chat.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        TextButton(onClick = onPick) { Text(if (currentAgentId.isBlank()) "Set" else "Change") }
        if (currentAgentId.isNotBlank()) {
            TextButton(onClick = onClear) { Text("Clear") }
        }
    }
}

@Composable
private fun ModelRow(providerId: String, modelName: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                if (providerId.isBlank()) "No model selected"
                else "$providerId · $modelName",
                style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
            )
            Text(
                "Active model used by this conversation when sending messages.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onClick) { Text("Change") }
    }
}

@Composable
private fun SystemPromptField(initial: String, onSave: (String) -> Unit) {
    var local by remember(initial) { mutableStateOf(initial) }
    // 400 ms debounce — matches desktop's RightSettingsPanel auto-save.
    LaunchedEffect(local) {
        if (local == initial) return@LaunchedEffect
        delay(400)
        if (local != initial) onSave(local)
    }
    Column(modifier = Modifier.padding(horizontal = 24.dp)) {
        OutlinedTextField(
            value = local,
            onValueChange = { local = it },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            maxLines = 8,
            placeholder = { Text("System prompt for this conversation only.") },
        )
        Text(
            "Auto-saves 400 ms after you stop typing.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun ParametersBlock(
    settings: ConvSettingsUi,
    onTemperature: (Double) -> Unit,
    onMaxTokens: (Int) -> Unit,
    onContextWindow: (Int) -> Unit,
    onStreaming: (Boolean) -> Unit,
    onThinking: (Boolean) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 24.dp)) {
        // Temperature slider 0.0 – 2.0
        var temperature by remember(settings.temperature) { mutableStateOf(settings.temperature.toFloat()) }
        LaunchedEffect(temperature) {
            if (temperature.toDouble() == settings.temperature) return@LaunchedEffect
            delay(400)
            if (temperature.toDouble() != settings.temperature) onTemperature(temperature.toDouble())
        }
        Text("Temperature: ${"%.2f".format(temperature)}", style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = temperature,
            onValueChange = { temperature = it },
            valueRange = 0f..2f,
            steps = 39,
        )
        Text(
            "Higher = more creative; lower = more deterministic.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(12.dp))

        IntField(
            label = "Max tokens",
            value = settings.maxTokens,
            min = -1,
            max = 128_000,
            onCommit = onMaxTokens,
            hint = "Reply length cap (128–128,000). -1 = Auto, no cap (recommended).",
        )
        Spacer(Modifier.size(12.dp))

        IntField(
            label = "Context window",
            value = settings.contextWindow,
            min = 1024,
            max = 131_072,
            onCommit = onContextWindow,
            hint = "How much history the assistant sees (1,024–131,072).",
        )
        Spacer(Modifier.size(12.dp))

        ToggleRow(
            title = "Streaming",
            detail = "Stream the reply token-by-token instead of waiting for completion.",
            icon = Icons.Filled.Bolt,
            checked = settings.streaming,
            onCheckedChange = onStreaming,
        )
        ToggleRow(
            title = "Thinking",
            detail = "Surface model reasoning steps when the provider supports it.",
            icon = Icons.Filled.SmartToy,
            checked = settings.thinking,
            onCheckedChange = onThinking,
        )
    }
}

/**
 * The five advanced sampling sliders. Each uses the host's -1 sentinel
 * for "Auto (model default)"; the whole block is disabled while "Use
 * app-recommended sampling" is on, mirroring the desktop
 * RightSettingsPanel.
 */
@Composable
private fun SamplingBlock(
    settings: ConvSettingsUi,
    enabled: Boolean,
    onTopK: (Double) -> Unit,
    onTopP: (Double) -> Unit,
    onRepeatPenalty: (Double) -> Unit,
    onPresencePenalty: (Double) -> Unit,
    onFrequencyPenalty: (Double) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 24.dp)) {
        if (!enabled) {
            Text(
                "Sliders are overridden while app-recommended sampling is on.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(8.dp))
        }
        SamplingSlider("Top-K", settings.topK, 100f, 0, enabled, onTopK)
        SamplingSlider("Top-P", settings.topP, 1f, 2, enabled, onTopP)
        SamplingSlider("Repeat penalty", settings.repeatPenalty, 2f, 2, enabled, onRepeatPenalty)
        SamplingSlider("Presence penalty", settings.presencePenalty, 2f, 2, enabled, onPresencePenalty)
        SamplingSlider("Frequency penalty", settings.frequencyPenalty, 2f, 2, enabled, onFrequencyPenalty)
    }
}

/** One -1=Auto sampling slider with the screen's 400 ms save debounce. */
@Composable
private fun SamplingSlider(
    label: String,
    value: Double,
    max: Float,
    decimals: Int,
    enabled: Boolean,
    onCommit: (Double) -> Unit,
) {
    var local by remember(value) { mutableStateOf(value.toFloat()) }
    LaunchedEffect(local) {
        if (local.toDouble() == value) return@LaunchedEffect
        delay(400)
        if (local.toDouble() != value) onCommit(local.toDouble())
    }
    val display = if (local < 0f) "Auto" else "%.${decimals}f".format(local)
    Text("$label: $display", style = MaterialTheme.typography.bodyMedium)
    Slider(
        value = local,
        onValueChange = { local = it },
        valueRange = -1f..max,
        enabled = enabled,
    )
    Spacer(Modifier.size(4.dp))
}

@Composable
private fun IntField(
    label: String,
    value: Int,
    min: Int,
    max: Int,
    onCommit: (Int) -> Unit,
    hint: String,
) {
    var local by remember(value) { mutableStateOf(value.toString()) }
    val parsed = local.toIntOrNull()
    val outOfBounds = parsed == null || parsed < min || parsed > max
    LaunchedEffect(local) {
        if (local == value.toString()) return@LaunchedEffect
        delay(400)
        val current = local.toIntOrNull() ?: return@LaunchedEffect
        if (current in min..max && current != value) onCommit(current)
    }
    Column {
        OutlinedTextField(
            value = local,
            onValueChange = { v -> local = v.filter(Char::isDigit).take(7) },
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = outOfBounds && local.isNotEmpty(),
            supportingText = {
                Text(
                    if (outOfBounds && local.isNotEmpty()) "Must be in [$min, $max]." else hint,
                    style = MaterialTheme.typography.bodySmall,
                )
            },
        )
    }
}

@Composable
private fun ToggleRow(
    title: String,
    detail: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ListRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    detail: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HeartbeatGateBlock(
    allow: Boolean,
    cap: Int,
    onToggle: (Boolean) -> Unit,
    onCap: (Int) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Bolt,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Auto-surface heartbeat reports", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "When on, eligible heartbeat reports post into this conversation. " +
                        "Daily cap protects you from spam.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = allow, onCheckedChange = onToggle)
        }
        if (allow) {
            Spacer(Modifier.size(8.dp))
            var local by remember(cap) { mutableStateOf(cap.toFloat()) }
            LaunchedEffect(local) {
                if (local.toInt() == cap) return@LaunchedEffect
                delay(400)
                if (local.toInt() != cap) onCap(local.toInt())
            }
            Text("Daily cap: ${local.toInt()}", style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = local,
                onValueChange = { local = it },
                valueRange = 1f..24f,
                steps = 22,
            )
        }
    }
}

@Composable
private fun InfoCard(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(14.dp),
    ) {
        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AgentPickerSheet(
    agents: List<AgentSummaryUi>,
    currentId: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(bottom = 16.dp)) {
            Text(
                "Pick primary agent",
                modifier = Modifier.padding(start = 24.dp, top = 8.dp, bottom = 4.dp),
                style = MaterialTheme.typography.titleLarge,
            )
            LazyColumn(modifier = Modifier.heightIn(max = 480.dp)) {
                items(agents, key = { it.id }) { agent ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(agent.id) }
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(
                                    if (agent.id == currentId) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceContainerHigh,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (agent.id == currentId) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                        Spacer(Modifier.size(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(agent.name, style = MaterialTheme.typography.bodyLarge)
                            if (agent.description.isNotBlank()) {
                                Text(
                                    agent.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AgentPatternSheet(
    current: AgentPattern,
    onPick: (AgentPattern) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(bottom = 16.dp)) {
            Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                Text("Agent pattern", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Applies to every conversation on this host.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                AgentPattern.entries.forEach { p ->
                    PatternRow(
                        pattern = p,
                        selected = p == current,
                        onClick = { onPick(p) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PatternRow(pattern: AgentPattern, selected: Boolean, onClick: () -> Unit) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.outlineVariant
    val borderWidth = if (selected) 2.dp else 1.dp
    val bg = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.18f)
    else MaterialTheme.colorScheme.surfaceContainer

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(borderWidth, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                pattern.displayName,
                style = MaterialTheme.typography.titleSmall,
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                pattern.wireValue,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (selected) {
            Icon(Icons.Filled.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
        }
    }
}
