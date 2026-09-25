// SPDX-FileCopyrightText: 2026 Aditya Mehra <aix.m@outlook.com>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

/**
 * @file ProjectCards.kt
 * @brief Composable card components for displaying project room templates
 *        and existing project/organisation folders in the Project Rooms UI.
 * @layer UI
 * @dependencies Jetpack Compose, Material3, FolderUi, ProjectTemplateUi,
 *               TemplateBanner
 */

package com.verzeta.android.ui.projectrooms

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Workspaces
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verzeta.android.data.folder.FolderType
import com.verzeta.android.data.folder.FolderUi
import com.verzeta.android.data.projectroom.ProjectTemplateUi

/**
 * Template card on the landing / library grid — banner at the top,
 * name + tag + scenario + member count below, "Quick start" CTA at the
 * bottom. Mirrors the desktop ProjectTemplateCard.qml structure.
 */
@Composable
fun ProjectTemplateCard(
    template: ProjectTemplateUi,
    onClick: () -> Unit,
    onPin: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
    ) {
        Column {
            // Banner — procedural art tinted by the template's baseHue.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
            ) {
                TemplateBanner(
                    geometryKind = template.geometryKind.ifBlank { "circles" },
                    baseHue = template.baseHue,
                    cornerRadius = 0.dp,
                    baseColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                )
                // Tag chip overlaid in the top-right.
                if (template.tagLabel.isNotBlank()) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            template.tagLabel,
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.6.sp),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                // Source indicator — small icon top-left for user-saved.
                if (template.isUserSaved) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .size(28.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.tertiaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.AutoAwesome,
                            contentDescription = "User template",
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }

            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        template.name.ifBlank { template.id },
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (onPin != null) {
                        IconButton(onClick = onPin) {
                            Icon(
                                if (template.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                                contentDescription = if (template.isPinned) "Unpin" else "Pin to landing",
                                tint = if (template.isPinned) MaterialTheme.colorScheme.primary
                                       else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (onDelete != null) {
                        IconButton(onClick = onDelete) {
                            Icon(
                                Icons.Filled.DeleteOutline,
                                contentDescription = "Delete user template",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
                if (template.scenario.isNotBlank()) {
                    Text(
                        template.scenario,
                        modifier = Modifier.padding(top = 4.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (template.members.isNotEmpty() || template.coordinator.isNotBlank()) {
                    Spacer(Modifier.size(10.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${template.members.size} member${if (template.members.size == 1) "" else "s"}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        if (template.coordinator.isNotBlank()) {
                            Text(
                                "• coord: @${template.coordinator}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * "Your Rooms" card — an existing project / org folder. Banner at the
 * top (derived from a hash of the folder id so it's stable per-folder),
 * name + folder type pill + goal + member count.
 */
@Composable
fun ProjectRoomCard(
    folder: FolderUi,
    onClick: () -> Unit,
    memberCount: Int = 0,
    modifier: Modifier = Modifier,
) {
    val designIdx = (folder.id.hashCode().rem(TemplateDesigns.size).let {
        if (it < 0) it + TemplateDesigns.size else it
    })
    val design = TemplateDesigns[designIdx]
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
            ) {
                TemplateBanner(
                    geometryKind = design.geometryKind,
                    baseHue = design.baseHue,
                    cornerRadius = 0.dp,
                    baseColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth().height(80.dp),
                )
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Icon(
                        when (folder.folderType) {
                            FolderType.Project -> Icons.Filled.Workspaces
                            FolderType.Organization -> Icons.Filled.Apartment
                            FolderType.Regular -> Icons.Filled.Workspaces
                        },
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.size(4.dp))
                    Text(
                        folder.folderType.wireValue.uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.6.sp),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    folder.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (folder.goal.isNotBlank()) {
                    Text(
                        folder.goal,
                        modifier = Modifier.padding(top = 4.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (memberCount > 0) {
                    Text(
                        "$memberCount member${if (memberCount == 1) "" else "s"}",
                        modifier = Modifier.padding(top = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}
