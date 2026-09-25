// SPDX-FileCopyrightText: 2026 Aditya Mehra <aix.m@outlook.com>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

/**
 * @file ProjectCreateScreen.kt
 * @brief Multi-panel screen for configuring and spinning up a new project
 *        room from a template, including name, scenario, goal, description,
 *        and member roster editing.
 * @layer UI
 * @dependencies Jetpack Compose, Material3, MainViewModel, MainUiState,
 *               MembershipEditor, TemplateBanner
 */

package com.verzeta.android.ui.projectrooms

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verzeta.android.MainUiState
import com.verzeta.android.MainViewModel
import com.verzeta.android.data.host.HostStatus
import com.verzeta.android.data.projectroom.TemplateCustomisationsUi
import com.verzeta.android.data.projectroom.TemplateRosterMemberUi
import com.verzeta.android.ui.components.EmptyState
import com.verzeta.android.ui.components.VerzetaTopBar

/**
 * Project Rooms Quick Start — full Android port mirroring the
 * desktop `frontend/components/ProjectQuickStartSheet.qml`.
 *
 * Region structure (same as desktop):
 *
 *   ┌─ Header: title + breadcrumb ──────────────────────────────────────┐
 *   ├─ PANEL 1 — Project (banner + ◀ design cycler ▶ + name + scenario)─┤
 *   ├─ PANEL 2 — Goal & Context (goal + description text areas) ───────┤
 *   ├─ PANEL 3 — Teammates (embedded MembershipEditor) ────────────────┤
 *   └─ Footer: Save as template + Cancel + Spin up room ──────────────┘
 */
@Composable
fun ProjectCreateScreen(state: MainUiState, actions: MainViewModel) {
    val templateId = state.selectedProjectTemplateId
    val template = state.projectTemplates.firstOrNull { it.id == templateId }
        ?: state.landingProjectTemplates.firstOrNull { it.id == templateId }
    val draft = state.projectTemplateDraft
    val rosterFromTemplate = state.projectTemplateRosters[templateId].orEmpty()
    val creating = state.projectTemplateCreating

    // Seed the agents + tools caches the MembershipEditor reads. The
    // configure sheet's provider override needs modelCatalog (already
    // loaded with the host) and the whitelist editor needs the tool
    // catalog. agent.list backs the Add-Member picker.
    LaunchedEffect(state.activeHostId, state.activeHost?.status) {
        if (state.activeHost?.status == HostStatus.Connected) {
            if (state.agents.isEmpty()) actions.loadAgents()
            if (state.tools.isEmpty()) actions.refreshTools()
        }
    }

    // Local draft state for member rows + design cycle. The draft
    // members list starts seeded from the template's resolved roster,
    // converted to MemberRowState. Subsequent edits live here until
    // the user taps "Spin up room".
    var members by remember(templateId, rosterFromTemplate) {
        mutableStateOf(rosterFromTemplate.map { it.toMemberRow() })
    }
    var designIndex by remember(template?.geometryKind) {
        mutableIntStateOf(indexForDesign(template?.geometryKind ?: "circles"))
    }
    var savedHint by remember { mutableStateOf("") }

    val design = TemplateDesigns[designIndex]

    Column(modifier = Modifier.fillMaxSize()) {
        VerzetaTopBar(
            title = "Quick Start",
            onBack = actions::closeProjectCreate,
        )

        if (templateId == null || template == null || draft == null) {
            EmptyState(
                title = "No template selected",
                detail = "Pick a template from Project Rooms to get started.",
                actionLabel = "Project Rooms",
                onAction = actions::showProjectRoomsLanding,
            )
            return@Column
        }

        Text(
            buildString {
                append("HOME · NEW PROJECT")
                if (template.tagLabel.isNotBlank()) {
                    append(" · ")
                    append(template.tagLabel)
                }
            },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ============= PANEL 1 — Project ===============
            item {
                Panel {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Banner + design cycler
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp)
                                .clip(RoundedCornerShape(12.dp)),
                        ) {
                            TemplateBanner(
                                geometryKind = design.geometryKind,
                                baseHue = design.baseHue,
                                cornerRadius = 0.dp,
                                baseColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier.fillMaxWidth().height(140.dp),
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(onClick = {
                                designIndex = (designIndex - 1 + TemplateDesigns.size) % TemplateDesigns.size
                            }) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous design")
                            }
                            Text(
                                "Design ${designIndex + 1} / ${TemplateDesigns.size}",
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                            IconButton(onClick = {
                                designIndex = (designIndex + 1) % TemplateDesigns.size
                            }) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next design")
                            }
                        }

                        FieldLabel("PROJECT NAME")
                        OutlinedTextField(
                            value = draft.name,
                            onValueChange = { v -> actions.updateProjectTemplateDraft { it.copy(name = v) } },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            placeholder = { Text("Name your project room") },
                        )
                        FieldLabel("SCENARIO")
                        OutlinedTextField(
                            value = draft.scenario,
                            onValueChange = { v -> actions.updateProjectTemplateDraft { it.copy(scenario = v) } },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            placeholder = { Text("Shown in the room header, e.g. \"Launch · multi-channel\"") },
                        )
                    }
                }
            }

            // ============= PANEL 2 — Goal & Context ==============
            item {
                Panel {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FieldLabel("GOAL: WHAT DOES SUCCESS LOOK LIKE?")
                        OutlinedTextField(
                            value = draft.goal,
                            onValueChange = { v -> actions.updateProjectTemplateDraft { it.copy(goal = v) } },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3,
                            placeholder = { Text("Describe the outcome the team should deliver.") },
                        )
                        Spacer(Modifier.size(4.dp))
                        FieldLabel("PROJECT DESCRIPTION / CONTEXT")
                        OutlinedTextField(
                            value = draft.description,
                            onValueChange = { v -> actions.updateProjectTemplateDraft { it.copy(description = v) } },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3,
                            placeholder = { Text("Paste a brief, link a doc, or describe the situation.") },
                        )
                    }
                }
            }

            // ============= PANEL 3 — Teammates ===================
            item {
                Panel {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "Teammates",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        )
                        Text(
                            "Add, remove, or rename agents and tune each one's provider, model and tools. Everything is editable again once the room exists.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        MembershipEditor(
                            members = members,
                            onMembersChange = { members = it },
                            agentRegistry = state.agents,
                            modelCatalog = state.modelCatalog,
                            availableTools = state.tools,
                        )
                    }
                }
            }
        }

        // ============= Footer ===============
        //
        // Two stacked rows so phone widths don't have to squeeze three
        // buttons onto one line:
        //
        //   ┌────────────────────────────────────────────────────────┐
        //   │ saved-hint text (when present)                          │
        //   │  ─────────────────────────────────────────────────────  │
        //   │           [+ Save as template — OutlinedButton fillW] │
        //   │  ─────────────────────────────────────────────────────  │
        //   │  [Cancel TextButton]            [✓ Spin up room Button]│
        //   └────────────────────────────────────────────────────────┘
        //
        // The primary "Spin up room" button uses Icon + Text content
        // (Material 3 button content slot) instead of an emoji prefix
        // baked into a Text string — emoji glyphs inside a Button drop
        // back to the default font, render small + off-baseline, and
        // looked broken on the previous iteration.
        if (savedHint.isNotBlank()) {
            Text(
                savedHint,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Row 1 — secondary "Save as template" action, full width.
            OutlinedButton(
                onClick = {
                    actions.updateProjectTemplateDraft {
                        it.copy(
                            members = members.map { m -> m.toRosterMember() },
                            // Keep the banner design picked with the arrows.
                            geometryKind = design.geometryKind,
                            baseHue = design.baseHue,
                        )
                    }
                    actions.saveCurrentDraftAsNewTemplate { newId ->
                        // On failure the reason is shown in the snackbar.
                        savedHint = if (newId != null) "✓ Saved to your Template Library"
                                    else "The template was not saved."
                    }
                },
                enabled = !creating && draft.name.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    Icons.Filled.BookmarkBorder,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.size(8.dp))
                Text("Save as template")
            }

            // Row 2 — primary action bar: Cancel + Spin up room.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(
                    onClick = actions::closeProjectCreate,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = {
                        actions.updateProjectTemplateDraft {
                            it.copy(members = members.map { m -> m.toRosterMember() })
                        }
                        actions.createProjectFromTemplate()
                    },
                    enabled = !creating && draft.name.isNotBlank(),
                    modifier = Modifier.weight(2f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    if (creating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(Modifier.size(8.dp))
                        Text("Creating…")
                    } else {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.size(8.dp))
                        Text("Create room")
                    }
                }
            }
        }
    }
}

// --- Reusable bits -------------------------------------------------

@Composable
private fun Panel(content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(modifier = Modifier.padding(16.dp)) { content() }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

// --- Bidirectional mapping between roster + member-row types --------

private fun TemplateRosterMemberUi.toMemberRow(): MemberRowState = MemberRowState(
    agentId = agentId,
    alias = alias,
    isCoordinator = isCoordinator,
    agentName = agentName,
    iconName = iconName,
    wasPreloaded = true,
    enabled = true,
    modelProvider = modelProvider,
    modelName = modelName,
    allowedTools = allowedTools,
)

private fun MemberRowState.toRosterMember(): TemplateRosterMemberUi = TemplateRosterMemberUi(
    agentId = agentId,
    alias = alias,
    isCoordinator = isCoordinator,
    agentName = agentName,
    iconName = iconName,
    modelProvider = modelProvider,
    modelName = modelName,
    allowedTools = allowedTools,
)
