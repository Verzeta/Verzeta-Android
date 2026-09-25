// SPDX-FileCopyrightText: 2026 Aditya Mehra <aix.m@outlook.com>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

/**
 * @file SkillDetailScreen.kt
 * @brief Read-only detail view for a single installed skill, displaying
 *        review state, version, provenance, declared tools, and any
 *        static-scan warnings returned by the host's `skill.get` op.
 * @layer UI
 * @dependencies Jetpack Compose, Material3, MainViewModel, MainUiState,
 *               SkillUi, SkillWarningUi
 */

package com.verzeta.android.ui.skills

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.verzeta.android.MainUiState
import com.verzeta.android.MainViewModel
import com.verzeta.android.data.skill.SkillState
import com.verzeta.android.data.skill.SkillUi
import com.verzeta.android.data.skill.SkillWarningUi
import com.verzeta.android.ui.components.EmptyState
import com.verzeta.android.ui.components.SectionHeader
import com.verzeta.android.ui.tools.TopBar
import com.verzeta.android.util.formatRelativeTimestamp
import java.time.Instant

/**
 * Full skill metadata view backed by a `skill.get` round-trip plus the
 * row that was tapped (cached as [MainUiState.selectedSkill]). Shows
 * everything the host returns in `skillToVariantMap` — review state,
 * version, source, tags, declared tools, install path, hash, timestamps,
 * and any static-scan warnings.
 *
 * No approve / block / delete buttons: those need host ops we don't have
 * for remote clients. Pointing at desktop in the empty-warnings line.
 */
@Composable
fun SkillDetailScreen(state: MainUiState, actions: MainViewModel) {
    val skill = state.selectedSkill

    Column(modifier = Modifier.fillMaxSize()) {
        TopBar(
            title = skill?.id.orEmpty().ifBlank { "Skill" },
            subtitle = skill?.version?.takeIf { it.isNotBlank() }?.let { "v$it" },
            onBack = actions::closeSkillDetail,
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        if (skill == null) {
            EmptyState(
                title = "No skill selected",
                detail = "Tap a skill from the list to see its details.",
            )
            return@Column
        }

        LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
            item {
                Spacer(Modifier.height(12.dp))
                StateRow(skill)
                Spacer(Modifier.height(16.dp))
            }
            if (skill.description.isNotBlank()) {
                item {
                    Text(
                        skill.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(16.dp))
                }
            }
            if (skill.tags.isNotEmpty()) {
                item {
                    Text("Tags", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        skill.tags.joinToString("  ") { "#$it" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                }
            }
            if (skill.declaredTools.isNotEmpty()) {
                item {
                    Text("Declared tools", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        skill.declaredTools.joinToString("\n") { it },
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                }
            }
            item {
                Text("Provenance", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
                MetaRow("Source", skill.source.ifBlank { "—" })
                if (skill.sourceUrl.isNotBlank()) MetaRow("URL", skill.sourceUrl)
                MetaRow(
                    "Installed",
                    skill.installedAtMs.takeIf { it > 0 }?.let(::formatMs) ?: "—",
                )
                MetaRow(
                    "Updated",
                    skill.updatedAtMs.takeIf { it > 0 }?.let(::formatMs) ?: "—",
                )
                if (skill.contentHashShort.isNotBlank()) MetaRow("Hash", skill.contentHashShort)
                if (skill.installPath.isNotBlank()) MetaRow("Path", skill.installPath)
                Spacer(Modifier.height(16.dp))
            }
            if (skill.warnings.isNotEmpty()) {
                item { SectionHeader("Static-scan warnings (${skill.warnings.size})") }
                skill.warnings.forEachIndexed { index, warning ->
                    item(key = "w-$index") {
                        WarningRow(warning)
                    }
                }
            }
            item { Spacer(Modifier.height(40.dp)) }
        }
    }
}

@Composable
private fun StateRow(skill: SkillUi) {
    val (label, color) = when (skill.state) {
        SkillState.Approved -> "Approved" to MaterialTheme.colorScheme.primary
        SkillState.Blocked -> "Blocked" to MaterialTheme.colorScheme.error
        SkillState.UnreviewedWithWarnings -> "Unreviewed · has warnings" to MaterialTheme.colorScheme.tertiary
        SkillState.Unreviewed -> "Unreviewed" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(color.copy(alpha = 0.15f))
                .padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = color)
        }
        Spacer(Modifier.size(12.dp))
        if (skill.state != SkillState.Approved) {
            Text(
                "Review on the desktop to enable",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MetaRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            "$label  ",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun WarningRow(w: SkillWarningUi) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                w.regexName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            if (w.fileRelativePath.isNotBlank()) {
                Spacer(Modifier.size(8.dp))
                Text(
                    "${w.fileRelativePath}:${w.lineNumber}",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (w.matchedExcerpt.isNotBlank()) {
            Text(
                w.matchedExcerpt,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatMs(ms: Long): String =
    formatRelativeTimestamp(Instant.ofEpochMilli(ms).toString())
