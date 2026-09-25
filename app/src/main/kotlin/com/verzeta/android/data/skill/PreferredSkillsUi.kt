// SPDX-FileCopyrightText: 2026 Aditya Mehra <aix.m@outlook.com>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

/**
 * @file PreferredSkillsUi.kt
 * @brief Per-scope preferred-skills configuration model (folder,
 *        conversation, or global scope) plus the scope enum used by the
 *        `skill.preferred.*` wire ops.
 * @layer Model
 * @dependencies None — pure Kotlin data declarations.
 */

package com.verzeta.android.data.skill

/**
 * Per-scope preferred-skills configuration. Wire ops `skill.preferred.*`
 * + `skill.expose_only_preferred`. Scope is folder, conversation, or
 * global.
 *
 * - [skillIds] — ids of the skills the user prefers in this scope.
 *   Empty list means "no preference set; defer to parent scope or all".
 * - [exposeOnly] — when true AND [skillIds] is non-empty, the assistant
 *   only sees the preferred skills (the rest are hidden). Stored
 *   independently so a user can toggle the expose-only behaviour without
 *   losing the preferred list.
 *
 * Per the host validation at remote-ws-session.cpp:2411-2432, scope_id
 * MUST be a real folder / conversation UUID; for `global`, scope_id is
 * always the empty string.
 */
data class PreferredSkillsUi(
    val skillIds: List<String> = emptyList(),
    val exposeOnly: Boolean = false,
)

/**
 * Scope a preferred-skills entry applies to. For [Folder] and
 * [Conversation] the accompanying scope_id is the entity UUID; for
 * [Global] it is always the empty string.
 */
enum class PreferredSkillsScope(val wireValue: String) {
    Folder("folder"),
    Conversation("conversation"),
    Global("global");
}
