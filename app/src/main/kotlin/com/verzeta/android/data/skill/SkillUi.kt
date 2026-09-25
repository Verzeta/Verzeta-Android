// SPDX-FileCopyrightText: 2026 Aditya Mehra <aix.m@outlook.com>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

/**
 * @file SkillUi.kt
 * @brief Models for installed-skill metadata as served by the host's skill
 *        catalog ops, including the review-state machine and per-file
 *        static-scan warnings surfaced in the skill browser.
 * @layer Model
 * @dependencies None — pure Kotlin data declarations.
 */

package com.verzeta.android.data.skill

/**
 * Installed skill metadata. Wire shape comes from `SkillService::skillToVariantMap`
 * (skill-service.cpp:294) and is identical between `skill.list` rows and
 * the `skill.get` detail response — we use one model for both.
 *
 * Spec used `displayName`, `summary`, `author`; the host has none of those.
 * Real fields are:
 *   id                 author-defined slug (NOT a UUID)
 *   source             where the skill came from (e.g. "user", "marketplace")
 *   sourceUrl          if external
 *   version            free-form version string
 *   description        free-form description (closest thing to "summary")
 *   tags               list of tags
 *   declaredTools      tools the skill declares it will call
 *   contentHashShort   first 12 chars of sha256 — useful for the row badge
 *   contentHashSha256  full sha256
 *   installedAtMs      ms since epoch
 *   updatedAtMs        ms since epoch
 *   installPath        absolute install dir (used by skill.get for detail)
 *   reviewState        "unreviewed" / "approved" / "blocked"
 *   warnings           static-scan findings — empty for clean skills
 */
data class SkillUi(
    val id: String,
    val source: String = "",
    val sourceUrl: String = "",
    val version: String = "",
    val description: String = "",
    val tags: List<String> = emptyList(),
    val declaredTools: List<String> = emptyList(),
    val contentHashShort: String = "",
    val contentHashSha256: String = "",
    val installedAtMs: Long = 0L,
    val updatedAtMs: Long = 0L,
    val installPath: String = "",
    val reviewState: String = "unreviewed",
    val warnings: List<SkillWarningUi> = emptyList(),
) {
    val state: SkillState
        get() = when (reviewState.lowercase()) {
            "approved" -> SkillState.Approved
            "blocked" -> SkillState.Blocked
            else -> if (warnings.isNotEmpty()) SkillState.UnreviewedWithWarnings else SkillState.Unreviewed
        }
}

/**
 * Derived review state for badge rendering: the host's three review values
 * plus a fourth client-side bucket for unreviewed skills that carry
 * static-scan warnings (rendered with a stronger caution treatment).
 */
enum class SkillState { Approved, Blocked, UnreviewedWithWarnings, Unreviewed }

/**
 * One static-scan finding inside an installed skill: which detection rule
 * fired, where (file + line), and the matched excerpt shown in the detail
 * sheet.
 */
data class SkillWarningUi(
    val regexName: String,
    val fileRelativePath: String,
    val lineNumber: Int,
    val matchedExcerpt: String,
)
