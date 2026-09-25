// SPDX-FileCopyrightText: 2026 Aditya Mehra <aix.m@outlook.com>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

/**
 * @file PreferredSkillsSheet.kt
 * @brief Modal bottom sheet for selecting a folder's preferred skills:
 *        multi-select rows over the host's installed skill list plus an
 *        "expose only preferred skills" toggle. Confirms the selection back
 *        to the caller; the wire write happens at folder save.
 * @layer Frontend
 * @dependencies SkillUi (data/skill), Jetpack Compose Material 3
 *               ModalBottomSheet.
 */

package com.verzeta.android.ui.folders

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.verzeta.android.data.skill.SkillUi

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreferredSkillsSheet(
    allSkills: List<SkillUi>,
    initiallySelected: Set<String>,
    initialExposeOnly: Boolean,
    onConfirm: (selected: List<String>, exposeOnly: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val selected: SnapshotStateMap<String, Boolean> = remember(initiallySelected) {
        mutableStateMapOf<String, Boolean>().apply {
            allSkills.forEach { put(it.id, initiallySelected.contains(it.id)) }
        }
    }
    var exposeOnly by remember(initialExposeOnly) { mutableStateOf(initialExposeOnly) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(bottom = 16.dp)) {
            Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                Text("Preferred skills", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Skills the assistant should reach for in this project. Empty list = no preference.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (allSkills.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
                    Text(
                        "No skills installed on the host. Install skills from the desktop in " +
                            "Settings → Skills.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .heightIn(max = 420.dp)
                        .padding(horizontal = 16.dp),
                ) {
                    items(allSkills, key = { it.id }) { skill ->
                        SkillSelectRow(
                            skill = skill,
                            selected = selected[skill.id] == true,
                            onToggle = { selected[skill.id] = !(selected[skill.id] == true) },
                        )
                    }
                }

                val anySelected = selected.values.any { it }
                if (anySelected) {
                    Spacer(Modifier.size(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Expose only preferred skills",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                "Hide other skills from the assistant in this project.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = exposeOnly, onCheckedChange = { exposeOnly = it })
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = {
                        val list = selected.entries.filter { it.value }.map { it.key }
                        onConfirm(list, exposeOnly && list.isNotEmpty())
                    },
                ) { Text("Save") }
            }
        }
    }
}

@Composable
private fun SkillSelectRow(skill: SkillUi, selected: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(
                    if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceContainerHighest,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Spacer(Modifier.size(10.dp))
        Icon(
            Icons.Filled.AutoFixHigh,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.size(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    skill.id,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (skill.version.isNotBlank()) {
                    Spacer(Modifier.size(8.dp))
                    Text(
                        "v${skill.version}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (skill.description.isNotBlank()) {
                Text(
                    skill.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
