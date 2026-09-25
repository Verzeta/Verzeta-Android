// SPDX-FileCopyrightText: 2026 Aditya Mehra <aix.m@outlook.com>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

/**
 * @file ProjectTemplateUi.kt
 * @brief Project Rooms template models: the catalog row, shipped-template
 *        member summaries, the resolved roster used by the Quick Start
 *        customise flow, the local customisation buffer, and the
 *        create-result handoff.
 * @layer Model
 * @dependencies None — pure Kotlin data declarations.
 */

package com.verzeta.android.data.projectroom

/**
 * One row in the Project Rooms template catalog, as served by the host's
 * `project_template.list` / `.list_landing` / `.get` wire ops.
 * Mirrors the desktop QVariantMap shape verbatim — see
 * `backend/services/project-template-service.cpp` on the host repo and
 * `project-template-service.h` for the template schema.
 *
 * Field set is the union of built-in and user-saved template fields.
 * Built-in templates ship with the binary under `:/project-templates/`;
 * user templates live under `<AppDataLocation>/user-templates/` and
 * carry [isUserSaved] = true.
 */
data class ProjectTemplateUi(
    val id: String,
    val name: String,
    val tagLabel: String = "",
    val category: String = "",
    val geometryKind: String = "",
    val baseHue: Int = 0,
    val scenario: String = "",
    val goal: String = "",
    val description: String = "",
    val coordinator: String = "",
    val members: List<TemplateMemberSummary> = emptyList(),
    val seedDocuments: List<String> = emptyList(),
    val isUserSaved: Boolean = false,
    val isPinned: Boolean = false,
)

/**
 * Member of a template AS SHIPPED (the JSON catalog). Members reference
 * agents by NAME (since UUIDs are per-install); the host resolves
 * agentName → agentId via AgentRegistry at create time. For display
 * purposes [agentName] is what shows; for the create flow the host's
 * [templateRoster] resolves to a richer shape.
 */
data class TemplateMemberSummary(
    val agentName: String,
    val alias: String,
    val isCoordinator: Boolean,
)

/**
 * One row of the resolved roster from `project_template.roster`. The
 * desktop's `templateRoster(templateId)` walks the template's members
 * through AgentRegistry and emits per-row {agentId, alias, isCoordinator,
 * agentName, iconName}. Members whose agent name fails to resolve in the
 * local registry are dropped server-side.
 *
 * This is the shape the Quick Start customise screen uses to seed
 * MembershipEditor-style rows: each one already has a real agents.id so
 * we can pass it back as `agentId` in createProjectFromTemplate's
 * `customisations.members[]`.
 */
data class TemplateRosterMemberUi(
    val agentId: String,
    val alias: String,
    val isCoordinator: Boolean,
    val agentName: String,
    val iconName: String,
    /** Per-member overrides set in the Quick Start editor; empty = none. */
    val modelProvider: String = "",
    val modelName: String = "",
    val allowedTools: List<String> = emptyList(),
)

/**
 * In-progress customisations the Android Quick Start screen tracks
 * locally before calling `project_template.create_project`. None of
 * the fields are required — the host falls back to the template's
 * built-in defaults for any missing or blank value.
 *
 * `members` REPLACES the template's default roster entirely when
 * non-empty; pass null to inherit the template default.
 */
data class TemplateCustomisationsUi(
    val name: String = "",
    val goal: String = "",
    val description: String = "",
    val scenario: String = "",
    val members: List<TemplateRosterMemberUi>? = null,
    /**
     * Banner design picked on the Quick Start screen. Stored only when the
     * draft is saved as a user template (`project_template.save_as_new`);
     * empty / null inherit the source template's design.
     */
    val geometryKind: String = "",
    val baseHue: Int? = null,
)

/**
 * Single-shot result of a successful create. Surfaced briefly in
 * MainUiState so the UI can navigate into the new folder's kickoff
 * sheet OR show a toast. Cleared after the UI has consumed it.
 */
data class ProjectTemplateCreatedUi(
    val folderId: String,
    val folderName: String,
    val memberCount: Int,
)
