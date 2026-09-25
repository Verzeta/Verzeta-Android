// SPDX-FileCopyrightText: 2026 Aditya Mehra <aix.m@outlook.com>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

/**
 * @file MembershipEditor.kt
 * @brief Reusable Compose component for editing the member roster of a
 *        project room, including per-member alias, coordinator designation,
 *        provider/model overrides, and tool whitelisting.
 * @layer UI
 * @dependencies Jetpack Compose, Material3, MainViewModel, AgentSummaryUi,
 *               ModelCatalogUi, ToolUi
 */

package com.verzeta.android.ui.projectrooms

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.verzeta.android.data.agent.AgentSummaryUi
import com.verzeta.android.data.model.ModelCatalogUi
import com.verzeta.android.data.tool.ToolUi

/**
 * One row in the editor's member list. Mirrors the desktop's
 * `membersModel` row shape in `MembershipEditor.qml`. The override
 * fields default to "no override" (empty strings + empty tool list);
 * a member with all-empty overrides inherits the conversation's
 * model + agent template's tools.
 */
data class MemberRowState(
    val agentId: String,
    val alias: String,
    val isCoordinator: Boolean = false,
    val agentName: String = "",
    val iconName: String = "",
    val wasPreloaded: Boolean = true,
    val enabled: Boolean = true,
    val modelProvider: String = "",
    val modelName: String = "",
    val allowedTools: List<String> = emptyList(),
) {
    val hasOverride: Boolean
        get() = modelProvider.isNotBlank() || modelName.isNotBlank() || allowedTools.isNotEmpty()

    val overrideSummary: String
        get() {
            val parts = mutableListOf<String>()
            if (modelProvider.isNotBlank()) {
                parts += if (modelName.isNotBlank()) "$modelProvider / $modelName" else modelProvider
            } else if (modelName.isNotBlank()) {
                parts += "model: $modelName"
            }
            if (allowedTools.isNotEmpty()) {
                parts += "${allowedTools.size} tool(s)"
            }
            return if (parts.isEmpty()) "Default model · all tools" else parts.joinToString("  ·  ")
        }
}

/**
 * Reusable per-member editor, a direct Android port of the desktop's
 * `MembershipEditor.qml`. Supports:
 *
 *   - Per-row alias edit (inline OutlinedTextField, uniqueness enforced).
 *   - Per-row coordinator toggle — radio-style: setting one unmarks others.
 *   - Per-row Configure sheet → provider override + model override +
 *     tool whitelist.
 *   - Soft-delete checkboxes (when [softDelete] is true) for preloaded
 *     members so the caller can exclude / re-include without losing
 *     alias state.
 *   - "Add Member" dialog with the agent registry as picker.
 *
 * @param members             current member rows
 * @param onMembersChange     called whenever any member row mutates
 * @param agentRegistry       all available agent templates (from `agent.list`)
 * @param modelCatalog        provider + model catalog for the override combo
 * @param availableTools      tool registry for the whitelist editor
 * @param softDelete          when true, preloaded members get a soft-delete
 *                            checkbox instead of a trash icon
 * @param allowedAgentIds     restricts the Add picker to these agent ids;
 *                            empty = entire registry
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MembershipEditor(
    members: List<MemberRowState>,
    onMembersChange: (List<MemberRowState>) -> Unit,
    agentRegistry: List<AgentSummaryUi>,
    modelCatalog: ModelCatalogUi,
    availableTools: List<ToolUi>,
    softDelete: Boolean = false,
    allowedAgentIds: Set<String> = emptySet(),
    modifier: Modifier = Modifier,
) {
    var addOpen by remember { mutableStateOf(false) }
    var configureIndex by remember { mutableStateOf(-1) }

    fun mutateRow(index: Int, transform: (MemberRowState) -> MemberRowState) {
        onMembersChange(members.mapIndexed { i, m -> if (i == index) transform(m) else m })
    }

    fun toggleCoordinator(index: Int) {
        val target = members.getOrNull(index) ?: return
        if (target.isCoordinator) {
            mutateRow(index) { it.copy(isCoordinator = false) }
        } else {
            // Clear all other coordinators first.
            onMembersChange(
                members.mapIndexed { i, m ->
                    when {
                        i == index -> m.copy(isCoordinator = true)
                        m.isCoordinator -> m.copy(isCoordinator = false)
                        else -> m
                    }
                },
            )
        }
    }

    fun aliasExists(alias: String): Boolean = members.any { it.alias.equals(alias, ignoreCase = true) }

    fun uniqueAlias(base: String): String {
        if (!aliasExists(base)) return base
        var i = 2
        while (aliasExists("$base $i")) ++i
        return "$base $i"
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (members.isEmpty()) {
            Text(
                "No members yet. Tap \"Add member\" to add an agent.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        } else {
            members.forEachIndexed { idx, member ->
                MemberRow(
                    member = member,
                    onAliasChange = { newAlias ->
                        if (newAlias.isBlank()) return@MemberRow
                        val conflict = members.indices.any { i ->
                            i != idx && members[i].alias.equals(newAlias, ignoreCase = true)
                        }
                        if (conflict) return@MemberRow
                        mutateRow(idx) { it.copy(alias = newAlias) }
                    },
                    onToggleCoordinator = { toggleCoordinator(idx) },
                    onConfigure = { configureIndex = idx },
                    onToggleEnabled = {
                        mutateRow(idx) { it.copy(enabled = !it.enabled) }
                    },
                    onDelete = {
                        onMembersChange(members.filterIndexed { i, _ -> i != idx })
                    },
                    softDelete = softDelete,
                )
            }
        }

        TextButton(
            onClick = { addOpen = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(Modifier.size(6.dp))
            Text("Add Member")
        }

        Text(
            "⭐ marks the coordinator, who answers when you don't @mention anyone. " +
                "Each alias must be unique so the same agent template can appear multiple times. " +
                "Use the configure button on a row to override its provider, model, or tools.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    // Add Member dialog.
    if (addOpen) {
        val picker = if (allowedAgentIds.isEmpty()) agentRegistry
                     else agentRegistry.filter { it.id in allowedAgentIds }
        AddMemberDialog(
            agentRegistry = picker,
            uniqueAlias = ::uniqueAlias,
            aliasExists = ::aliasExists,
            onCancel = { addOpen = false },
            onAdd = { agent, alias, coord ->
                val newMember = MemberRowState(
                    agentId = agent.id,
                    alias = alias,
                    isCoordinator = coord,
                    agentName = agent.name,
                    iconName = agent.iconName.ifBlank { "face-smile" },
                    wasPreloaded = false,
                    enabled = true,
                )
                val cleared = if (coord) members.map { it.copy(isCoordinator = false) }
                              else members
                onMembersChange(cleared + newMember)
                addOpen = false
            },
        )
    }

    // Configure sheet for the chosen member.
    if (configureIndex >= 0 && configureIndex < members.size) {
        val current = members[configureIndex]
        ConfigureMemberSheet(
            member = current,
            modelCatalog = modelCatalog,
            availableTools = availableTools,
            onApply = { provider, model, tools ->
                mutateRow(configureIndex) {
                    it.copy(modelProvider = provider, modelName = model, allowedTools = tools)
                }
                configureIndex = -1
            },
            onDismiss = { configureIndex = -1 },
        )
    }
}

// ---------------------------------------------------------------------
// One row.
// ---------------------------------------------------------------------

/**
 * Mobile member-row layout — two stacked sections so phone widths
 * (~360-400 dp) don't have to squeeze 4-5 controls onto one line:
 *
 *   ┌─────────────────────────────────────────────────────────────┐
 *   │ [avatar]  [alias OutlinedTextField — fillMaxWidth] [star] [×]│
 *   │           agent name (small)                                 │
 *   │           override summary (small, highlighted when set)     │
 *   │  ─────────────────────────────────────────────────────────  │
 *   │  [⚙ Configure model & tools  FilledTonalButton fillWidth]   │
 *   └─────────────────────────────────────────────────────────────┘
 *
 * The alias text field gets the full width minus the avatar + 2 small
 * IconButtons (star + delete). The Configure action moves to its own
 * row below as a labelled tonal button — clearly tappable, label
 * fully visible, no clipping.
 */
@Composable
private fun MemberRow(
    member: MemberRowState,
    onAliasChange: (String) -> Unit,
    onToggleCoordinator: () -> Unit,
    onConfigure: () -> Unit,
    onToggleEnabled: () -> Unit,
    onDelete: () -> Unit,
    softDelete: Boolean,
) {
    var aliasText by remember(member.alias) { mutableStateOf(member.alias) }
    val alpha = if (!member.enabled) 0.45f else 1f
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = alpha))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // ROW 1 — avatar + alias field + star + delete.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = alpha)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = alpha),
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.size(10.dp))
            OutlinedTextField(
                value = aliasText,
                onValueChange = { aliasText = it },
                singleLine = true,
                enabled = member.enabled,
                label = { Text("Alias") },
                modifier = Modifier.weight(1f),
            )
            if (aliasText != member.alias && aliasText.isNotBlank()) {
                onAliasChange(aliasText)
            }
            IconButton(onClick = onToggleCoordinator, enabled = member.enabled) {
                Icon(
                    if (member.isCoordinator) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = if (member.isCoordinator) "Unmark coordinator" else "Mark as coordinator",
                    tint = if (member.isCoordinator) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                )
            }
            if (softDelete && member.wasPreloaded) {
                IconButton(onClick = onToggleEnabled) {
                    Checkbox(
                        checked = member.enabled,
                        onCheckedChange = { onToggleEnabled() },
                    )
                }
            } else {
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.DeleteOutline,
                        contentDescription = "Remove",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        // ROW 2 — agent name + override summary (compact, left-aligned).
        if (member.agentName.isNotBlank() || member.overrideSummary.isNotBlank()) {
            Column(
                modifier = Modifier.padding(start = 46.dp),  // align under the alias text field
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (member.agentName.isNotBlank()) {
                    Text(
                        member.agentName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    member.overrideSummary,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                    color = if (member.hasOverride && member.enabled)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // ROW 3 — labelled Configure action on its own line.
        FilledTonalButton(
            onClick = onConfigure,
            enabled = member.enabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                Icons.Filled.Tune,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.size(8.dp))
            Text("Configure model & tools")
        }
    }
}

// ---------------------------------------------------------------------
// Add Member dialog.
// ---------------------------------------------------------------------

@Composable
private fun AddMemberDialog(
    agentRegistry: List<AgentSummaryUi>,
    uniqueAlias: (String) -> String,
    aliasExists: (String) -> Boolean,
    onCancel: () -> Unit,
    onAdd: (AgentSummaryUi, alias: String, coord: Boolean) -> Unit,
) {
    var pickedAgent by remember { mutableStateOf<AgentSummaryUi?>(null) }
    var aliasText by remember { mutableStateOf("") }
    var coordChecked by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Add Member") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("1. Pick an agent template", style = MaterialTheme.typography.titleSmall)
                if (agentRegistry.isEmpty()) {
                    Text(
                        "No agents available. Visit the Agents catalog on the desktop to create some first.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    agentRegistry.forEach { agent ->
                        val selected = pickedAgent?.id == agent.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceContainerLow,
                                )
                                .clickable {
                                    pickedAgent = agent
                                    if (aliasText.isBlank()) aliasText = uniqueAlias(agent.name)
                                    coordChecked = agent.isCoordinator
                                }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Filled.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.size(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        agent.name,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    )
                                    if (agent.isCoordinator) {
                                        Spacer(Modifier.size(6.dp))
                                        Text(
                                            "COORDINATOR",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                                if (agent.description.isNotBlank()) {
                                    Text(
                                        agent.description,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }

                Text("2. Set a unique alias", style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(
                    value = aliasText,
                    onValueChange = { aliasText = it },
                    singleLine = true,
                    label = { Text("Alias (e.g. Alice, Bob)") },
                    modifier = Modifier.fillMaxWidth(),
                    isError = aliasText.isNotBlank() && aliasExists(aliasText.trim()),
                    supportingText = {
                        if (aliasText.isNotBlank() && aliasExists(aliasText.trim()))
                            Text("⚠ This alias is already in use.", color = MaterialTheme.colorScheme.error)
                        else
                            Text("Used for @mentions in the chat.")
                    },
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { coordChecked = !coordChecked }
                        .padding(vertical = 4.dp),
                ) {
                    Checkbox(checked = coordChecked, onCheckedChange = { coordChecked = it })
                    Text(
                        "Mark as coordinator",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                Text(
                    "The new member starts with the conversation default model and all tools. Use the configure " +
                        "button on the row to give it its own provider, model, or tool whitelist.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val a = pickedAgent ?: return@TextButton
                    val alias = aliasText.trim()
                    if (alias.isEmpty() || aliasExists(alias)) return@TextButton
                    onAdd(a, alias, coordChecked)
                },
                enabled = pickedAgent != null && aliasText.trim().isNotEmpty() && !aliasExists(aliasText.trim()),
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("Cancel") }
        },
    )
}

// ---------------------------------------------------------------------
// Configure sheet — provider override + model override + tool whitelist.
// ---------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfigureMemberSheet(
    member: MemberRowState,
    modelCatalog: ModelCatalogUi,
    availableTools: List<ToolUi>,
    onApply: (provider: String, model: String, tools: List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var provider by remember(member.modelProvider) { mutableStateOf(member.modelProvider) }
    var model by remember(member.modelName) { mutableStateOf(member.modelName) }
    var tools by remember(member.allowedTools) { mutableStateOf(member.allowedTools) }

    var providerMenuOpen by remember { mutableStateOf(false) }
    var modelMenuOpen by remember { mutableStateOf(false) }

    val providers = modelCatalog.providers
    val modelsForProvider = if (provider.isBlank()) emptyList()
        else providers.firstOrNull { it.providerId == provider }?.models.orEmpty()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Configure @${member.alias}",
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                "This member's provider, model, and tool whitelist override the conversation default on every turn this member takes. Other members in the same chat are unaffected.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // -- Provider override -------------------------------------
            Text("Provider override", style = MaterialTheme.typography.titleSmall)
            Box {
                OutlinedTextField(
                    value = provider.ifBlank { "(Use conversation default)" },
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Provider") },
                    trailingIcon = {
                        IconButton(onClick = { providerMenuOpen = true }) {
                            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().clickable { providerMenuOpen = true },
                )
                DropdownMenu(
                    expanded = providerMenuOpen,
                    onDismissRequest = { providerMenuOpen = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("(Use conversation default)") },
                        onClick = {
                            provider = ""
                            model = ""
                            providerMenuOpen = false
                        },
                    )
                    providers.forEach { p ->
                        DropdownMenuItem(
                            text = {
                                // Primary line: display name (or fall back to
                                // the provider id when the host did not send
                                // a friendly label).  Secondary caption: the
                                // capability glyphs plus the "Custom server"
                                // tag for any provider whose id is namespaced
                                // under the host's custom OpenAI-API-compatible
                                // server registry — same visual signal the
                                // desktop's CustomServerCard renders.
                                Column {
                                    Text(p.displayName.ifBlank { p.providerId })
                                    val caps = buildList {
                                        if (p.isCustomServer) add("Custom server")
                                        if (p.supportsStreaming) add("streaming")
                                        if (p.supportsToolCalling) add("tools")
                                        if (p.supportsVision) add("vision")
                                    }
                                    if (caps.isNotEmpty()) {
                                        Text(
                                            caps.joinToString(" · "),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            },
                            onClick = {
                                provider = p.providerId
                                model = ""  // reset; user picks again
                                providerMenuOpen = false
                            },
                        )
                    }
                }
            }

            // -- Model override (only when provider is set) ----------
            if (provider.isNotBlank()) {
                Text("Model override", style = MaterialTheme.typography.titleSmall)
                Box {
                    OutlinedTextField(
                        value = model.ifBlank { "(Use provider default)" },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Model") },
                        trailingIcon = {
                            IconButton(onClick = { modelMenuOpen = true }) {
                                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().clickable { modelMenuOpen = true },
                    )
                    DropdownMenu(
                        expanded = modelMenuOpen,
                        onDismissRequest = { modelMenuOpen = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("(Use provider default)") },
                            onClick = { model = ""; modelMenuOpen = false },
                        )
                        modelsForProvider.forEach { m ->
                            DropdownMenuItem(
                                text = { Text(m) },
                                onClick = { model = m; modelMenuOpen = false },
                            )
                        }
                    }
                }
            }

            // -- Tool whitelist ---------------------------------------
            Text("Tool whitelist", style = MaterialTheme.typography.titleSmall)
            Text(
                "Tick the tools this member is allowed to call. Empty = inherit (all tools available).",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (availableTools.isEmpty()) {
                Text(
                    "No tools loaded. Open the Tools catalog (Settings → Tools) to populate the list.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                availableTools.forEach { tool ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                tools = if (tool.name in tools) tools - tool.name
                                        else tools + tool.name
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = tool.name in tools,
                            onCheckedChange = {
                                tools = if (it) tools + tool.name else tools - tool.name
                            },
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                tool.name,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            if (tool.description.isNotBlank()) {
                                Text(
                                    tool.description,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }

            // -- Apply / Cancel ---------------------------------------
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(Modifier.size(8.dp))
                TextButton(onClick = { onApply(provider, model, tools) }) {
                    Text("Apply")
                }
            }
        }
    }
}
