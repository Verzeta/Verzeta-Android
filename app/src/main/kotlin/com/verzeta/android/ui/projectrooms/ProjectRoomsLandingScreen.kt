// SPDX-FileCopyrightText: 2026 Aditya Mehra <aix.m@outlook.com>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

/**
 * @file ProjectRoomsLandingScreen.kt
 * @brief Landing screen for the Project Rooms feature, presenting a hero
 *        panel, the user's existing project folders, and a searchable,
 *        filterable template catalog for starting new rooms.
 * @layer UI
 * @dependencies Jetpack Compose, Material3, MainViewModel, MainUiState,
 *               ProjectTemplateCard, ProjectRoomCard, TemplateBanner
 */

package com.verzeta.android.ui.projectrooms

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verzeta.android.MainUiState
import com.verzeta.android.MainViewModel
import com.verzeta.android.data.folder.FolderType
import com.verzeta.android.data.folder.FolderUi
import com.verzeta.android.data.host.HostStatus
import com.verzeta.android.data.projectroom.ProjectTemplateUi
import com.verzeta.android.ui.components.EmptyState
import com.verzeta.android.ui.components.VerzetaTopBar
import com.verzeta.android.ui.theme.adaptiveCatalogColumns
import com.verzeta.android.ui.theme.adaptiveContentMaxWidth

/**
 * Project Rooms landing — full Android port mirroring the desktop's
 * `frontend/components/ProjectRoomOverlay.qml`. Region structure matches
 * the desktop's panel ladder: HERO → YOUR ROOMS → TEMPLATES.
 *
 *   ┌─ Header: title + breadcrumb + search ─────────────────────────────┐
 *   ├─ HERO panel: "Start a project room" + Blank / Quick Start CTAs ──┤
 *   ├─ YOUR ROOMS panel: existing project folder cards ────────────────┤
 *   └─ TEMPLATES panel: filter pills + template cards with banners ───┘
 */
@Composable
fun ProjectRoomsLandingScreen(state: MainUiState, actions: MainViewModel) {
    val connected = state.activeHost?.status == HostStatus.Connected

    var searchText by remember { mutableStateOf("") }
    var activeFilter by remember { mutableStateOf("all") }

    LaunchedEffect(connected) {
        if (connected) {
            if (state.landingProjectTemplates.isEmpty()) actions.refreshLandingProjectTemplates()
            if (state.folders.isEmpty()) actions.refreshFolders()
        }
    }

    // Bridge the one-shot create-success event into the kickoff sheet.
    LaunchedEffect(state.projectTemplateCreated) {
        if (state.projectTemplateCreated != null) {
            actions.consumeProjectTemplateCreated()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header: top bar with title + back, then breadcrumb chip + search
        // field on a second row (mobile-stacked, matching the desktop's
        // mobile breakpoint).
        VerzetaTopBar(
            title = "Project Rooms",
            onBack = actions::closeProjectRoomsLanding,
        )

        if (!connected) {
            EmptyState(
                title = "No connected host",
                detail = "Connect or pair a desktop host before opening Project Rooms.",
                actionLabel = "Home",
                onAction = actions::showHome,
            )
            return@Column
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                "HOME · NEW PROJECT",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(8.dp))
            SearchField(
                value = searchText,
                onValueChange = { searchText = it },
                placeholder = "Search projects + templates…",
            )
        }

        val filteredTemplates = state.landingProjectTemplates.filter { t ->
            (activeFilter == "all" || t.category.equals(activeFilter, ignoreCase = true)) &&
                (searchText.isBlank() ||
                    listOf(t.name, t.description, t.tagLabel)
                        .any { it.contains(searchText, ignoreCase = true) })
        }
        val projectFolders = state.folders.filter {
            it.folderType == FolderType.Project || it.folderType == FolderType.Organization
        }

        // Center the catalog content at tablet / desktop widths so it
        // doesn't stretch absurdly on wide screens.
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter,
        ) {
        // Use LazyVerticalGrid so the template cards render as a
        // 1/2/3-column grid on phone/tablet-portrait/tablet-landscape
        // respectively, matching the Template Library full-screen catalog.
        // Non-card items (HERO, YOUR ROOMS header + LazyRow, section
        // header, filter pills) span the full grid width via
        // GridItemSpan(maxLineSpan).
        val cols = adaptiveCatalogColumns()
        LazyVerticalGrid(
            columns = GridCells.Fixed(cols),
            modifier = Modifier.fillMaxSize().then(adaptiveContentMaxWidth()),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // ----- HERO panel — compact -----------------------------
            item(span = { GridItemSpan(maxLineSpan) }) { HeroPanel(
                onBlank = { actions.createProjectFlow() },
                onQuickStart = actions::showTemplateLibrary,
            ) }

            // ----- YOUR ROOMS — horizontal scroll of small cards ----
            //
            // Was a SectionPanel with vertical-stacked full-width cards
            // that ate ~250-400 dp of vertical real estate and pushed
            // the templates section below the fold. Horizontal LazyRow
            // with fixed-width tiles caps the section at ~150 dp
            // regardless of folder count, so the templates section
            // header shows above the fold on most phones.
            if (projectFolders.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Your rooms",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        )
                        Text(
                            "${projectFolders.size}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp),
                    ) {
                        items(projectFolders, key = { it.id }) { folder ->
                            ProjectRoomCard(
                                folder = folder,
                                onClick = { actions.openFolder(folder.id) },
                                modifier = Modifier.width(220.dp),
                            )
                        }
                    }
                }
            }

            // ----- TEMPLATES section — clearly framed --------------
            //
            // The whole templates section sits inside one Card so the
            // header + filter pills + first card visibly belong to the
            // same panel. Without this framing the user only saw the
            // "Start from a template" headline and didn't realise the
            // cards live directly below it (the original bug).
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Start from a template",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    )
                    AssistChip(
                        onClick = actions::showTemplateLibrary,
                        label = { Text("Browse all", style = MaterialTheme.typography.labelMedium) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ),
                    )
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                FilterPillRow(activeFilter = activeFilter, onChange = { activeFilter = it })
            }

            // ----- TEMPLATE CARDS — adaptive grid (1/2/3 cols) -------
            if (filteredTemplates.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        if (searchText.isNotBlank()) "No templates match \"$searchText\""
                        else if (activeFilter != "all") "No templates in this category"
                        else "Loading templates…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            } else {
                gridItems(filteredTemplates, key = { it.id }) { template ->
                    ProjectTemplateCard(
                        template = template,
                        onClick = { actions.selectProjectTemplate(template.id) },
                        onPin = if (template.isUserSaved) {
                            { actions.pinProjectTemplate(template.id, !template.isPinned) }
                        } else null,
                    )
                }
            }
        }  // LazyVerticalGrid
        }  // Box centered container
    }
}

// ---------------------------------------------------------------------
// HERO panel.
// ---------------------------------------------------------------------

/**
 * HERO panel — kept deliberately compact so the YOUR ROOMS + the
 * templates section render above the fold on a 360-411 dp phone
 * width. Two-line copy at most, tonal background, action row tight
 * against the description.
 */
@Composable
private fun HeroPanel(onBlank: () -> Unit, onQuickStart: () -> Unit) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f),
                            MaterialTheme.colorScheme.surfaceContainerLow,
                        ),
                    ),
                )
                .padding(14.dp),
        ) {
            Column {
                Text(
                    "Start a project room",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                )
                Text(
                    "Create a chat, roster, goal, and tasks in one step.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Spacer(Modifier.size(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = onBlank,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("Blank")
                    }
                    Button(
                        onClick = onQuickStart,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                        ),
                    ) {
                        Icon(Icons.Filled.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("Quick start")
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------
// Filter pill row — All / Sales / Engineering / Marketing / Exec / Discovery.
// Identical to the desktop's _filterModel in ProjectRoomOverlay.qml.
// ---------------------------------------------------------------------

@Composable
private fun FilterPillRow(activeFilter: String, onChange: (String) -> Unit) {
    val filters = listOf(
        "all" to "All",
        "sales" to "Sales",
        "engineering" to "Engineering",
        "marketing" to "Marketing",
        "exec" to "Exec",
        "discovery" to "Discovery",
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        filters.forEach { (id, label) ->
            FilterChip(
                selected = activeFilter == id,
                onClick = { onChange(id) },
                label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            )
        }
    }
}

// ---------------------------------------------------------------------
// Search field — identical shape to the Conversations browser's.
// ---------------------------------------------------------------------

@Composable
private fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.size(10.dp))
        Box(modifier = Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(
                    placeholder,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 16.sp,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
