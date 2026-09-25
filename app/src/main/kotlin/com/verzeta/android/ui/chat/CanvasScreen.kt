// SPDX-FileCopyrightText: 2026 Aditya Mehra <aix.m@outlook.com>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

/**
 * @file CanvasScreen.kt
 * @brief Full-screen canvas viewer and editor for the active conversation.
 *        Hosts a line-numbered code editor with syntax highlighting, AI action
 *        chips, a run/stop control for host-side script execution, and a
 *        collapsible console drawer showing live canvas.run.line events.
 * @layer UI
 * @dependencies Jetpack Compose, MainViewModel, MainUiState, CanvasUi,
 *               SyntaxHighlighter, canvas.* wire ops
 */

package com.verzeta.android.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatAlignLeft
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Subject
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WrapText
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verzeta.android.MainUiState
import com.verzeta.android.MainViewModel
import com.verzeta.android.data.canvas.CanvasAiActionUi
import com.verzeta.android.data.canvas.CanvasToolUi
import com.verzeta.android.data.canvas.CanvasUi
import com.verzeta.android.data.canvas.ConsoleLineKind
import com.verzeta.android.data.canvas.ConsoleLineUi
import com.verzeta.android.data.host.HostStatus
import com.verzeta.android.ui.chat.canvas.SyntaxHighlighter
import com.verzeta.android.ui.components.EmptyState
import kotlinx.coroutines.delay

/**
 * Canvas viewer and editor — Android port of the desktop CanvasPanel.
 * Run and Send-to-IDE always execute on the host (bubblewrap is Linux-only).
 * The console panel below the editor renders `canvas.run.line` events live,
 * broadcast unfiltered to every authenticated client.
 *
 * Sections (top to bottom):
 *   - Header: back, filename, language tag, history dropdown, word-wrap toggle,
 *     run/stop button (when language is in `canvas.run.supported_languages`),
 *     open-in-IDE (when language is in `canvas.run.supported_ide_languages`),
 *     tools menu (Validate / Format), and close-canvas.
 *   - Sandbox warning banner when sandbox.available == false.
 *   - Editor: BasicTextField with monospace font, line-numbered gutter, and
 *     syntax highlighting via [SyntaxHighlighter].
 *   - 1000 ms debounced save via `canvas.edit`. The host's `canvas.updated`
 *     event echoes back; the round-trip is acceptable on local networks.
 *   - Action bar: AI action chips for the active language.
 *   - Console drawer (bottom) when [consoleVisible].
 */
@Composable
fun CanvasScreen(state: MainUiState, actions: MainViewModel) {
    val convId = state.selectedConversationId ?: return
    val connected = state.activeHost?.status == HostStatus.Connected
    val canvas = state.convCanvas[convId]

    LaunchedEffect(convId, connected) {
        if (connected) actions.loadCanvasForConversation(convId)
    }

    // Word-wrap toggle — defaults to ON for mobile because horizontal scroll
    // on a phone is poor UX. rememberSaveable survives recomposition and
    // process recreation; not yet persisted to DataStore.
    var wordWrap by rememberSaveable(canvas?.id) { mutableStateOf(true) }

    Column(modifier = Modifier.fillMaxSize()) {
        CanvasHeader(
            state = state,
            actions = actions,
            canvas = canvas,
            wordWrap = wordWrap,
            onToggleWordWrap = { wordWrap = !wordWrap },
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        if (canvas == null) {
            EmptyState(
                title = "No canvas in this conversation",
                detail = "Ask the assistant to open a canvas (e.g. \"open a canvas with my Python script\") " +
                    "or use a tool that calls open_canvas.",
            )
            return@Column
        }

        if (!state.canvasSandbox.available) {
            SandboxBanner(reason = state.canvasSandbox.blockerReason)
        }

        // Editor body — weight(1f) so the action bar + console always
        // get their slice of the screen even when content is huge.
        CanvasEditor(
            canvas = canvas,
            wordWrap = wordWrap,
            onContentChange = { newText ->
                actions.saveCanvasContent(convId, newText)
            },
            modifier = Modifier.weight(1f),
        )

        // AI action chips. The action bar collapses overflow into a
        // "More" menu when the primary set won't fit — mirrors the
        // desktop's wide/compact/tiny regimes but at phone widths
        // we're always closer to compact. The bar is shown even when the
        // language has no AI actions, because it also holds the Console
        // toggle; without it a closed console could not be reopened.
        val aiActions = state.canvasAiActionsByLang[canvas.language].orEmpty()
        CanvasActionBar(
            actions = aiActions,
            consoleVisible = state.consoleVisible,
            onTrigger = { action, choice -> actions.triggerCanvasAiAction(convId, action.id, choice) },
            onToggleConsole = { actions.setConsoleVisible(!state.consoleVisible) },
        )

        if (state.consoleVisible) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            CanvasConsoleDrawer(
                lines = state.consoleLines,
                running = state.canvasRunState.running,
                onClear = { actions.clearCanvasConsole() },
                onClose = { actions.setConsoleVisible(false) },
                onSendInput = { actions.sendCanvasInput(it) },
                onSendEof = { actions.sendCanvasEof() },
            )
        }
    }
}

@Composable
private fun CanvasHeader(
    state: MainUiState,
    actions: MainViewModel,
    canvas: CanvasUi?,
    wordWrap: Boolean = false,
    onToggleWordWrap: () -> Unit = {},
) {
    var historyOpen by remember { mutableStateOf(false) }
    var toolsOpen by remember { mutableStateOf(false) }
    val convId = state.selectedConversationId ?: return
    val running = state.canvasRunState.running
    val canRun = canvas != null && canvas.language in state.canvasRunSupportedLanguages
    val canIde = canvas != null && canvas.language in state.canvasRunSupportedIdeLanguages
    val tools = canvas?.let { state.canvasToolsByLang[it.language] }.orEmpty()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(start = 4.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = actions::closeCanvas) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                canvas?.filename ?: "Canvas",
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (canvas != null) {
                Text(
                    canvas.language.ifBlank { "plain" } +
                        " · ${canvas.lineCount} line${if (canvas.lineCount == 1) "" else "s"}" +
                        " · rev ${canvas.revision}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Word-wrap toggle — filled tint when wrap is ON so the active
        // state is visible at a glance.
        IconButton(onClick = onToggleWordWrap) {
            Icon(
                Icons.Filled.WrapText,
                contentDescription = if (wordWrap) "Disable word wrap" else "Enable word wrap",
                tint = if (wordWrap) {
                    MaterialTheme.colorScheme.primary
                } else {
                    androidx.compose.material3.LocalContentColor.current
                },
            )
        }

        // History dropdown — pulls full canvas list for this conv.
        Box {
            IconButton(
                onClick = {
                    historyOpen = true
                    actions.loadCanvasHistory(convId)
                },
            ) {
                Icon(Icons.Filled.History, contentDescription = "Canvas history")
            }
            DropdownMenu(
                expanded = historyOpen,
                onDismissRequest = { historyOpen = false },
            ) {
                val history = state.convCanvasHistory[convId].orEmpty()
                if (history.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text("No history yet") },
                        onClick = { historyOpen = false },
                        enabled = false,
                    )
                } else {
                    history.forEach { row ->
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        row.filename.ifBlank { row.id.take(8) },
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    if (row.isArchived) {
                                        Spacer(Modifier.size(6.dp))
                                        Text(
                                            "archived",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            },
                            onClick = {
                                historyOpen = false
                                if (row.isArchived) actions.switchCanvas(convId, row.id)
                            },
                        )
                    }
                }
            }
        }

        // Tools menu (in-process Validate / Format / etc.).
        if (tools.isNotEmpty()) {
            Box {
                IconButton(onClick = { toolsOpen = true }) {
                    Icon(Icons.Filled.Tune, contentDescription = "Tools")
                }
                DropdownMenu(
                    expanded = toolsOpen,
                    onDismissRequest = { toolsOpen = false },
                ) {
                    tools.forEach { tool -> ToolMenuItem(tool, onClick = {
                        toolsOpen = false
                        actions.runCanvasTool(convId, tool.id)
                    }) }
                }
            }
        }

        if (canRun) {
            IconButton(
                onClick = {
                    if (running) actions.cancelCanvasRun()
                    else actions.startCanvasRun(convId)
                },
            ) {
                Icon(
                    if (running) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                    contentDescription = if (running) "Stop" else "Run",
                    tint = if (running) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary,
                )
            }
        }

        if (canIde && !running) {
            IconButton(onClick = { actions.sendCanvasToIde(convId) }) {
                Icon(Icons.Filled.OpenInNew, contentDescription = "Open on host IDE")
            }
        }

        IconButton(onClick = { actions.closeActiveCanvas(convId) }) {
            Icon(Icons.Filled.Close, contentDescription = "Close canvas")
        }
    }
}

@Composable
private fun ToolMenuItem(tool: CanvasToolUi, onClick: () -> Unit) {
    DropdownMenuItem(
        text = {
            Column {
                Text(tool.label, style = MaterialTheme.typography.bodyMedium)
                if (tool.description.isNotBlank()) {
                    Text(
                        tool.description,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        enabled = tool.enabled,
        onClick = onClick,
    )
}

@Composable
private fun SandboxBanner(reason: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.size(8.dp))
        Text(
            "Sandbox unavailable on host: ${reason.ifBlank { "no detail" }}. " +
                "Running scripts is at your own risk.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

@Composable
private fun CanvasEditor(
    canvas: CanvasUi,
    onContentChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    wordWrap: Boolean = false,
) {
    var local by remember(canvas.id, canvas.revision) { mutableStateOf(canvas.content) }
    // 1000 ms debounce per spec — matches desktop's auto-save cadence.
    LaunchedEffect(local) {
        if (local == canvas.content) return@LaunchedEffect
        delay(1000)
        if (local != canvas.content) onContentChange(local)
    }

    Row(modifier = modifier.fillMaxWidth()) {
        // Line-number gutter — right-aligned, dimmed, monospace.
        val lineCount = local.count { it == '\n' } + 1
        Column(
            modifier = Modifier
                .width(48.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.End,
        ) {
            for (i in 1..lineCount) {
                Text(
                    "$i",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // Editor body — when word-wrap is ON, drop the horizontalScroll so
        // long lines wrap to fit the viewport.
        val hScroll = rememberScrollState()
        val vScroll = rememberScrollState()
        val editorModifier = if (wordWrap) {
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surface)
                .verticalScroll(vScroll)
                .padding(8.dp)
        } else {
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surface)
                .verticalScroll(vScroll)
                .horizontalScroll(hScroll)
                .padding(8.dp)
        }
        Box(modifier = editorModifier) {
            // Wire the canvas BasicTextField through SyntaxHighlighter via
            // visualTransformation, which overlays span colors on top of the
            // live editing buffer for the canvas language. Unsupported
            // languages fall through to plain monospace.
            BasicTextField(
                value = local,
                onValueChange = { local = it },
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 18.sp,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                visualTransformation = SyntaxHighlighter.visualTransformation(canvas.language),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * Mobile-friendly canvas action bar displayed at the bottom of the canvas
 * editor. Contains two generously-sized buttons:
 *
 *   - "AI Actions" (primary, left) — opens a [ModalBottomSheet] that lists
 *     every action as a 2-column grid of icon-tile cards. Tapping a tile
 *     triggers the action; if the action has submenu choices the sheet swaps
 *     to a choice list with a back affordance.
 *   - "Console" (secondary, right) — toggles the console drawer.
 *
 * Each action gets a Material icon picked by [iconForCanvasAction] based on
 * keyword matches in its label and id. Falls back to AutoAwesome when nothing
 * matches.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CanvasActionBar(
    actions: List<CanvasAiActionUi>,
    consoleVisible: Boolean,
    onTrigger: (CanvasAiActionUi, String) -> Unit,
    onToggleConsole: () -> Unit,
) {
    var sheetOpen by remember { mutableStateOf(false) }
    var submenuFor by remember { mutableStateOf<CanvasAiActionUi?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalButton(
                onClick = { sheetOpen = true },
                enabled = actions.isNotEmpty(),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
            ) {
                Icon(
                    Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    if (actions.isEmpty()) "No actions for this language"
                    else "AI actions · ${actions.size}",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            FilledTonalButton(
                onClick = onToggleConsole,
                modifier = Modifier.height(48.dp),
                colors = if (consoleVisible) {
                    androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                } else {
                    androidx.compose.material3.ButtonDefaults.filledTonalButtonColors()
                },
            ) {
                Icon(
                    Icons.Filled.Terminal,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.size(6.dp))
                Text(if (consoleVisible) "Hide" else "Console", maxLines = 1)
            }
        }
    }

    if (sheetOpen) {
        ModalBottomSheet(
            sheetState = sheetState,
            onDismissRequest = {
                sheetOpen = false
                submenuFor = null
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            val current = submenuFor
            if (current == null) {
                CanvasActionGrid(
                    actions = actions,
                    onPick = { picked ->
                        if (picked.submenu.isNotEmpty()) {
                            submenuFor = picked
                        } else {
                            sheetOpen = false
                            onTrigger(picked, "")
                        }
                    },
                )
            } else {
                CanvasActionSubmenu(
                    action = current,
                    onPickChoice = { choice ->
                        sheetOpen = false
                        submenuFor = null
                        onTrigger(current, choice)
                    },
                    onBack = { submenuFor = null },
                )
            }
        }
    }
}

@Composable
private fun CanvasActionGrid(
    actions: List<CanvasAiActionUi>,
    onPick: (CanvasAiActionUi) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            "AI actions",
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            style = MaterialTheme.typography.titleMedium,
        )
        // Sort so primaries land first — the user's most-likely picks.
        val sorted = remember(actions) {
            actions.sortedByDescending { it.primary }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 480.dp),
        ) {
            items(sorted, key = { it.id }) { action ->
                CanvasActionTile(action = action, onClick = { onPick(action) })
            }
        }
    }
}

@Composable
private fun CanvasActionTile(action: CanvasAiActionUi, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        color = if (action.primary) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = iconForCanvasAction(action),
                    contentDescription = null,
                    tint = if (action.primary) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.size(22.dp),
                )
                if (action.submenu.isNotEmpty()) {
                    Spacer(Modifier.weight(1f))
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = "Has options",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                action.label,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = if (action.primary) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        }
    }
}

@Composable
private fun CanvasActionSubmenu(
    action: CanvasAiActionUi,
    onPickChoice: (String) -> Unit,
    onBack: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                action.label,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
        ) {
            action.submenu.forEach { choice ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPickChoice(choice) }
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.size(14.dp))
                    Text(choice, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

/**
 * Pick a Material icon for an AI canvas action based on keyword matches in its
 * label and id. The host does not carry icon hints in the action wire shape, so
 * this is a heuristic. Falls back to AutoAwesome when nothing matches.
 */
private fun iconForCanvasAction(action: CanvasAiActionUi): ImageVector {
    val key = (action.id + " " + action.label).lowercase()
    return when {
        "summar"     in key -> Icons.Filled.Summarize
        "explain"    in key -> Icons.Filled.QuestionAnswer
        "refactor"   in key -> Icons.Filled.Build
        "format"     in key -> Icons.Filled.FormatAlignLeft
        "valid"      in key -> Icons.Filled.CheckCircle
        "improve"    in key -> Icons.Filled.AutoFixHigh
        "translate"  in key -> Icons.Filled.Translate
        "comment"    in key -> Icons.Filled.Subject
        "doc"        in key -> Icons.Filled.Description
        "edit"       in key -> Icons.Filled.Edit
        "code"       in key -> Icons.Filled.Code
        "fix"        in key -> Icons.Filled.AutoFixHigh
        "idea"       in key -> Icons.Filled.Lightbulb
        "run"        in key -> Icons.Filled.PlayArrow
        else                -> Icons.Filled.AutoAwesome
    }
}

@Composable
private fun CanvasConsoleDrawer(
    lines: List<ConsoleLineUi>,
    running: Boolean,
    onClear: () -> Unit,
    onClose: () -> Unit,
    onSendInput: (String) -> Unit,
    onSendEof: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 160.dp, max = 280.dp)
            .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Terminal,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.size(6.dp))
            Text(
                "Console",
                style = MaterialTheme.typography.titleSmall,
            )
            if (lines.isNotEmpty()) {
                Spacer(Modifier.size(6.dp))
                Text(
                    "(${lines.size})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onClear, enabled = lines.isNotEmpty()) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Clear console",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onClose) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Hide console",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        if (lines.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                Text(
                    "Console is empty.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 4.dp)) {
                items(lines) { line -> ConsoleLineRow(line) }
            }
        }
        // Only while a script is actually running: an input box on a finished
        // run has nothing to write to. The host echoes each answer back as an
        // "input" console row, so nothing is echoed locally.
        if (running) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            ConsoleInputBar(onSendInput = onSendInput, onSendEof = onSendEof)
        }
    }
}

/**
 * Answers `input()` prompts in a running canvas script.
 *
 * An empty box submits a blank line, which is what a script sees when you press
 * Enter at a real prompt, so Send is never disabled. **End input** closes stdin
 * for scripts that read until EOF.
 */
@Composable
private fun ConsoleInputBar(
    onSendInput: (String) -> Unit,
    onSendEof: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    val submit = {
        onSendInput(text)
        text = ""
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.weight(1f),
            singleLine = true,
            placeholder = { Text("Answer the prompt") },
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { submit() }),
        )
        Spacer(Modifier.size(6.dp))
        IconButton(onClick = submit) {
            Icon(
                Icons.AutoMirrored.Filled.Send,
                contentDescription = "Send input",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        TextButton(onClick = onSendEof) { Text("End input") }
    }
}

@Composable
private fun ConsoleLineRow(line: ConsoleLineUi) {
    Row(verticalAlignment = Alignment.Top) {
        Text(
            line.timestamp.ifBlank { "—" },
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(8.dp))
        Text(
            line.text,
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
            color = consoleColor(line.kind),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun consoleColor(kind: ConsoleLineKind): Color = when (kind) {
    ConsoleLineKind.Stderr, ConsoleLineKind.ExitErr -> MaterialTheme.colorScheme.error
    ConsoleLineKind.System -> MaterialTheme.colorScheme.tertiary
    ConsoleLineKind.ExitOk -> MaterialTheme.colorScheme.primary
    // Lines the user typed, echoed by the host. Accented so the console reads
    // as a prompt/answer transcript, matching the desktop console.
    ConsoleLineKind.Input -> MaterialTheme.colorScheme.primary
    else -> MaterialTheme.colorScheme.onSurface
}
