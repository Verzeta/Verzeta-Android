// SPDX-FileCopyrightText: 2026 Aditya Mehra <aix.m@outlook.com>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

/**
 * @file ChatScreen.kt
 * @brief Primary chat view for an open conversation, including the message
 *        list, streaming assistant bubbles, the message composer with file
 *        attachment support, slash-command completion, and context-fill gauge.
 * @layer UI
 * @dependencies Jetpack Compose, MainViewModel, MainUiState, MessageContent,
 *               ModelPickerSheet, PollCard, AssistantThinkingDisclosure
 */

package com.verzeta.android.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuOpen
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verzeta.android.MainUiState
import com.verzeta.android.MainViewModel
import com.verzeta.android.R
import com.verzeta.android.data.chat.MessageRole
import com.verzeta.android.data.chat.MessageUi
import com.verzeta.android.data.host.HostStatus
import com.verzeta.android.remote.ConnectionState
import com.verzeta.android.ui.components.AssistantThinkingDisclosure
import com.verzeta.android.ui.components.EmptyState
import com.verzeta.android.ui.components.MessageContent
import com.verzeta.android.ui.components.ModelPickerSheet
import com.verzeta.android.ui.components.VerzetaMark
import com.verzeta.android.util.formatRelativeTimestamp

/**
 * Chat screen for the selected conversation.
 *
 * Messages load with `msg.list` on open and stay current through
 * `msg.subscribe` events; the composer sends with `msg.send` (or
 * `msg.send_with_attachments` when files are attached) and the stop button
 * uses `msg.stop`. The top bar's overflow menu opens Conversation settings,
 * Plans, Tool calls, Artifacts, Generated media, Canvas and the
 * conversation's Activity timeline. Per-conversation options such as tools
 * and RAG live in Conversation settings rather than as composer chips,
 * because `msg.send` carries only the conversation and the text.
 */
@Composable
fun ChatScreen(state: MainUiState, actions: MainViewModel) {
    val connected = state.activeHost?.status == HostStatus.Connected
    if (state.selectedConversationId == null) {
        ConversationsScreen(state = state, actions = actions)
        return
    }

    var modelPickerOpen by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .consumeWindowInsets(WindowInsets.systemBars)
            .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.ime))
    ) {
        ChatTopBar(
            state = state,
            actions = actions,
            onPickModel = { modelPickerOpen = true },
        )
        if (!connected) {
            EmptyState(
                title = "Disconnected",
                detail = "The host is no longer connected.",
                actionLabel = "Home",
                onAction = actions::showHome,
            )
            return
        }
        MessagePane(state = state, actions = actions, modifier = Modifier.weight(1f))
        // AgentProgressBanner sits above the composer when the host
        // reports an agent run in progress. Hidden otherwise, with no
        // extra layout cost.
        AgentProgressBanner(
            runState = state.agentRunState,
            steps = state.agentSteps,
        )
        Composer(state = state, actions = actions)
    }

    if (modelPickerOpen) {
        ModelPickerSheet(
            catalog = state.modelCatalog,
            onPick = { providerId, modelName ->
                actions.setActiveModel(providerId, modelName)
                modelPickerOpen = false
            },
            onDismiss = { modelPickerOpen = false },
        )
    }
}

/**
 * Compact, polished chat top bar:
 *
 *   [←]  Title                                          [●]  [⋮]
 *        provider · model                                       └─ Tool activity, …
 *
 * Single 56dp row. Title + tappable model subtitle in the middle column.
 * No host endpoint, no latency, no IP — those belong in Settings, not in
 * every chat header.
 *
 * The trailing controls are a tiny coloured connection dot (legible at a
 * glance, no text to clip) and an overflow kebab. Anything that isn't a
 * primary action — Tool Activity now, Plans / Artifacts when those land —
 * goes in the kebab so the bar stays clean as features grow.
 */
@Composable
private fun ChatTopBar(state: MainUiState, actions: MainViewModel, onPickModel: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    val masterDetail = com.verzeta.android.ui.theme.LocalMasterDetailController.current
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (masterDetail != null) {
                IconButton(
                    onClick = {
                        masterDetail.setListVisible(!masterDetail.listVisible)
                    },
                ) {
                    Icon(
                        imageVector = if (masterDetail.listVisible) {
                            Icons.AutoMirrored.Filled.MenuOpen
                        } else {
                            Icons.Filled.Menu
                        },
                        contentDescription = if (masterDetail.listVisible) {
                            "Hide conversations list"
                        } else {
                            "Show conversations list"
                        },
                    )
                }
            } else {
                IconButton(onClick = actions::closeConversation) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to conversations")
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp, end = 8.dp)
                    .clickable(onClick = onPickModel),
            ) {
                Text(
                    text = state.selectedConversation?.title.orEmpty().ifBlank { "Chat" },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                ModelSubtitle(state = state)
            }
            ConnectionDot(state.connectionState)
            Spacer(Modifier.size(4.dp))
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "More")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Conversation settings") },
                        leadingIcon = {
                            Icon(
                                androidx.compose.material.icons.Icons.Filled.Edit,
                                contentDescription = null,
                            )
                        },
                        onClick = { menuOpen = false; actions.showConvSettings() },
                    )
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Plans")
                                val count = state.selectedConversationId?.let {
                                    state.convPlans[it]?.size
                                } ?: 0
                                if (count > 0) {
                                    Spacer(Modifier.size(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary)
                                            .padding(horizontal = 6.dp, vertical = 1.dp),
                                    ) {
                                        Text(
                                            if (count > 99) "99+" else count.toString(),
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            style = MaterialTheme.typography.labelSmall,
                                        )
                                    }
                                }
                            }
                        },
                        leadingIcon = {
                            Icon(
                                androidx.compose.material.icons.Icons.Filled.AutoAwesome,
                                contentDescription = null,
                            )
                        },
                        onClick = { menuOpen = false; actions.showPlansOverlay() },
                    )
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Tool calls")
                                val count = state.selectedConversationId?.let {
                                    state.convToolCalls[it]?.size
                                } ?: 0
                                if (count > 0) {
                                    Spacer(Modifier.size(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary)
                                            .padding(horizontal = 6.dp, vertical = 1.dp),
                                    ) {
                                        Text(
                                            if (count > 99) "99+" else count.toString(),
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            style = MaterialTheme.typography.labelSmall,
                                        )
                                    }
                                }
                            }
                        },
                        leadingIcon = {
                            Icon(
                                androidx.compose.material.icons.Icons.Filled.Build,
                                contentDescription = null,
                            )
                        },
                        onClick = { menuOpen = false; actions.showToolCallLog() },
                    )
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Artifacts")
                                val count = state.selectedConversationId?.let {
                                    state.convArtifacts[it]?.size
                                } ?: 0
                                if (count > 0) {
                                    Spacer(Modifier.size(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary)
                                            .padding(horizontal = 6.dp, vertical = 1.dp),
                                    ) {
                                        Text(
                                            if (count > 99) "99+" else count.toString(),
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            style = MaterialTheme.typography.labelSmall,
                                        )
                                    }
                                }
                            }
                        },
                        leadingIcon = {
                            Icon(
                                androidx.compose.material.icons.Icons.Filled.Description,
                                contentDescription = null,
                            )
                        },
                        onClick = { menuOpen = false; actions.showArtifacts() },
                    )
                    DropdownMenuItem(
                        text = { Text("Generated media") },
                        leadingIcon = {
                            Icon(
                                androidx.compose.material.icons.Icons.Filled.Image,
                                contentDescription = null,
                            )
                        },
                        onClick = { menuOpen = false; actions.showGeneratedMedia() },
                    )
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Canvas")
                                val active = state.selectedConversationId?.let {
                                    state.convCanvas[it]
                                }
                                if (active != null) {
                                    Spacer(Modifier.size(8.dp))
                                    Text(
                                        active.filename,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                    )
                                }
                            }
                        },
                        leadingIcon = {
                            Icon(
                                androidx.compose.material.icons.Icons.Filled.Code,
                                contentDescription = null,
                            )
                        },
                        onClick = { menuOpen = false; actions.showCanvas() },
                    )
                    DropdownMenuItem(
                        text = { Text("Activity") },
                        leadingIcon = {
                            Icon(
                                androidx.compose.material.icons.Icons.Filled.History,
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            menuOpen = false
                            state.selectedConversationId?.let { convId ->
                                actions.showActivityForConversation(
                                    convId,
                                    state.selectedConversation?.title.orEmpty(),
                                )
                            }
                        },
                    )
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/**
 * Tiny connection-state dot. Replaces the old "Live"/"Reconnecting" pill
 * that fought the title for horizontal space and clipped the model name
 * on narrower devices. Colour encodes state at a glance:
 *   open       → success (green)
 *   connecting → info    (primary)
 *   closed     → warning (amber)
 *   failed     → error   (red)
 *   idle       → outline (neutral)
 */
@Composable
private fun ConnectionDot(state: ConnectionState) {
    val color = when (state) {
        ConnectionState.Open -> com.verzeta.android.ui.theme.VerzetaTheme.semantic.success
        ConnectionState.Connecting -> MaterialTheme.colorScheme.primary
        ConnectionState.Closed -> com.verzeta.android.ui.theme.VerzetaTheme.semantic.warning
        ConnectionState.Failed -> MaterialTheme.colorScheme.error
        ConnectionState.Idle -> MaterialTheme.colorScheme.outline
    }
    Box(
        modifier = Modifier
            .padding(horizontal = 6.dp)
            .size(10.dp)
            .clip(CircleShape)
            .background(color),
    )
}

/**
 * Single-line model subtitle:  provider · model
 *
 * Tappable as part of the title block to open the picker. Host endpoint
 * + latency intentionally moved to Settings — they were clogging the
 * top bar with text that clipped on every device narrower than a
 * tablet, and the user already sees connection state via the dot.
 */
@Composable
private fun ModelSubtitle(state: MainUiState) {
    val catalog = state.modelCatalog
    val (text, color) = if (catalog.hasActiveModel) {
        "${catalog.activeProvider} · ${catalog.activeModel}" to MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        "Tap to pick a model" to MaterialTheme.colorScheme.primary
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun MessagePane(state: MainUiState, actions: MainViewModel, modifier: Modifier = Modifier) {
    // Per-conv LazyListState — `remember(key) { LazyListState() }`
    // rather than `rememberLazyListState()`. Each conversation gets
    // its own fresh listState that defaults to firstVisibleItemIndex=0
    // and scrollOffset=0; with `reverseLayout = true` that anchors
    // the viewport at item index 0, i.e. the LATEST message at the
    // visual bottom.
    //
    // Pre-fix the listState was shared across every conversation the
    // user opened in a session: `rememberLazyListState()` is
    // unkeyed, so firstVisibleItemIndex / scrollOffset carried over
    // from conv A into conv B. If the user scrolled up in conv A and
    // then switched to conv B (or conv B had fewer messages), the
    // viewport for conv B landed at a stale index, NOT at the
    // bottom.  The conv-keyed `remember` gives each conversation a
    // clean LazyListState at default position.
    val listState = remember(state.selectedConversationId) { LazyListState() }

    var wasAtBottom by remember(state.selectedConversationId) { mutableStateOf(true) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .collect { scrolling ->
                if (!scrolling) {
                    wasAtBottom = listState.firstVisibleItemIndex == 0
                        && listState.firstVisibleItemScrollOffset == 0
                }
            }
    }

    val lastMsgId = state.messages.lastOrNull()?.id
    LaunchedEffect(lastMsgId) {
        if (lastMsgId != null && wasAtBottom) {
            listState.scrollToItem(0)
        }
    }


    Column(modifier = modifier.fillMaxSize()) {
        ChatStatusRow(state = state)
        if (state.messages.isEmpty()) {
            EmptyState(
                title = "No messages yet",
                detail = "Send a message to begin.",
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                reverseLayout = true,
                contentPadding = PaddingValues(bottom = 16.dp, top = 16.dp),
            ) {
                items(state.messages.asReversed(), key = { it.id }) { message ->
                    MessageRow(message = message, state = state, actions = actions)
                }
            }
        }
    }
}

@Composable
private fun ChatStatusRow(state: MainUiState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = state.chatStatus,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MessageRow(message: MessageUi, state: MainUiState, actions: MainViewModel) {
    when (message.role) {
        MessageRole.User -> UserBubble(message)
        MessageRole.System -> SystemNoteRow(message)
        MessageRole.Assistant, MessageRole.Tool, MessageRole.Unknown ->
            AssistantBubble(message = message, state = state, actions = actions)
    }
    Spacer(Modifier.size(8.dp))
}

@Composable
private fun SystemNoteRow(message: MessageUi) {
    val text = message.text.trim()
    val isShortNote = text.length <= 160 && !text.contains('\n')
    if (isShortNote) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        return
    }
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "System",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            val time = formatRelativeTimestamp(iso = message.createdAt)
            if (time.isNotBlank()) {
                Text(
                    time,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        SelectionContainer {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(14.dp),
            ) {
                MessageContent(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun UserBubble(message: MessageUi) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "You",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!message.memberAlias.isNullOrBlank()) {
                Spacer(Modifier.size(6.dp))
                Text(
                    "@${message.memberAlias}",
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.weight(1f))
            val time = formatRelativeTimestamp(iso = message.createdAt)
            if (time.isNotBlank()) {
                Text(
                    time,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // SelectionContainer makes every `Text` descendant
        // long-press-selectable + copyable on Android.  Wrapped at
        // the body Box level so the header row (You / @member /
        // timestamp) stays non-selectable — selection inside the
        // bubble's rendered content is what users want; dragging
        // across the metadata strip is awkward and rarely useful.
        SelectionContainer {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(14.dp),
            ) {
                MessageContent(
                    text = message.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
private fun AssistantBubble(
    message: MessageUi,
    state: MainUiState,
    actions: MainViewModel,
) {
    if (message.streaming && message.text.isBlank()) {
        // Empty streaming row — render a subtle thinking-dots strip rather
        // than a "Waiting…" bubble. Two reasons: (a) it doesn't look like
        // a real message, (b) if the row turns out to be a tool-call
        // placeholder, demoting it to activity won't cause a visible bubble
        // to disappear.
        ThinkingIndicator()
        return
    }
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VerzetaMark(size = 18)
            Spacer(Modifier.size(8.dp))
            Text(
                text = message.role.label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (message.modelUsed.isNotBlank()) {
                Spacer(Modifier.size(8.dp))
                Text(
                    "· ${message.modelUsed}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
            if (!message.memberAlias.isNullOrBlank()) {
                Spacer(Modifier.size(8.dp))
                Text(
                    "@${message.memberAlias}",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        val thinkingOnlyFallback =
            !message.streaming
                && message.text.isBlank()
                && message.thinkingContent.isNotBlank()
        // SelectionContainer makes every `Text` descendant
        // long-press-selectable + copyable on Android.  Wrapped at
        // the body Column level so users can copy the assistant
        // reply, the disclosure body, and the empty-content
        // fallback line (when applicable) — but NOT the
        // role / model / agent metadata in the header row above,
        // since selecting that on a long-press would be accidental
        // noise rather than the user's intent.
        SelectionContainer {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(start = 16.dp, top = 12.dp, end = 14.dp, bottom = 12.dp),
            ) {
                // Disclosure (collapsed by default; expanded by default
                // on the fallback path).  Render-only sidecar: the
                // composable internally returns nothing when
                // thinkingContent is blank, so this branch is a no-op
                // for the common non-reasoning case.
                AssistantThinkingDisclosure(
                    thinkingContent = message.thinkingContent,
                    startExpanded = thinkingOnlyFallback,
                )
                if (thinkingOnlyFallback) {
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = stringResource(R.string.thinking_only_fallback),
                        style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    if (message.thinkingContent.isNotBlank()) {
                        Spacer(Modifier.size(8.dp))
                    }
                    MessageContent(
                        text = message.text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
        val pollId = message.pollId
        if (pollId != null && pollId.isNotEmpty()) {
            val convId = state.selectedConversationId.orEmpty()
            val poll = state.convPolls[convId].orEmpty()
                .firstOrNull { it.id == pollId }
            Spacer(Modifier.size(8.dp))
            InlinePollCard(
                poll = poll,
                onVote = { optId -> actions.voteOnPoll(pollId, optId) },
                onClose = { actions.closePoll(pollId) },
            )
        }
        if (message.streaming) {
            Box(
                modifier = Modifier
                    .padding(top = 4.dp, start = 2.dp)
                    .height(2.dp)
                    .fillMaxWidth(0.4f)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
        AssistantFooter(message)
    }
}

/**
 * Footer rendered under each non-streaming assistant message. Surfaces the
 * fields the host emits in `Serialise::message()`: created_at, token_count,
 * finish_reason. We only show non-blank pieces.
 */
@Composable
private fun AssistantFooter(message: MessageUi) {
    if (message.streaming) return
    val time = formatRelativeTimestamp(iso = message.createdAt)
    val parts = buildList {
        if (time.isNotBlank()) add(time)
        if (message.tokenCount > 0) add("${message.tokenCount} tokens")
        if (message.finishReason.isNotBlank() && message.finishReason != "stop") {
            add("ended: ${message.finishReason}")
        }
        if (!message.agentId.isNullOrBlank() && message.memberAlias.isNullOrBlank()) {
            // Surface the agent id (truncated) when no alias is present —
            // names are not available without `agent.list`, but the id is
            // enough to disambiguate two replies in the same group chat.
            add("agent ${message.agentId.take(8)}")
        }
    }
    if (parts.isEmpty()) return
    Text(
        text = parts.joinToString(" · "),
        modifier = Modifier.padding(top = 4.dp, start = 4.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private val kSlashCommands = listOf(
    "/help" to "List available commands",
    "/compact" to "Summarise older messages now (non-destructive)",
    "/flashmemory" to "Wipe this conversation's messages (asks to confirm)",
    "/artifacts" to "List files this conversation produced",
    "/showtools" to "List registered tools",
    "/showmcptools" to "List MCP tools",
    "/tools" to "Filter the tool list",
    "/clear" to "Clear the visible transcript",
)

@Composable
private fun SlashCommandHints(draft: String, onPick: (String) -> Unit) {
    val trimmed = draft.trimStart()
    if (!trimmed.startsWith("/") || trimmed.contains(' ')) return
    val matches = kSlashCommands.filter { it.first.startsWith(trimmed, ignoreCase = true) }
    if (matches.isEmpty()) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(vertical = 4.dp),
    ) {
        matches.take(5).forEach { (cmd, hint) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPick("$cmd ") }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    cmd,
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.size(10.dp))
                Text(
                    hint,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
    Spacer(Modifier.size(6.dp))
}

@Composable
private fun Composer(state: MainUiState, actions: MainViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var pickError by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }

    // Storage Access Framework picker. We accept any MIME type so the
    // user can attach images, audio, plain text, code, or arbitrary
    // binary files. The host's vision/context routing decides what to do
    // based on mime_type. Cap per-file at 4 MiB raw bytes (host enforces
    // payload_too_large above that); reject upfront so we don't waste a
    // round-trip.
    val pickFile = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val out = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    val cr = context.contentResolver
                    var name = "attachment"
                    cr.query(uri, null, null, null, null)?.use { c ->
                        val nameIdx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (nameIdx >= 0 && c.moveToFirst()) name = c.getString(nameIdx) ?: name
                    }
                    val mime = cr.getType(uri) ?: "application/octet-stream"
                    val bytes = cr.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
                    if (bytes.size > 4 * 1024 * 1024) {
                        throw IllegalStateException("File too large (${bytes.size / 1024} KB; 4 MB max)")
                    }
                    com.verzeta.android.data.chat.OutgoingAttachment(
                        fileName = name,
                        mimeType = mime,
                        rawBytes = bytes.size.toLong(),
                        contentBase64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP),
                    )
                }
            }
            out.onSuccess { att ->
                val sanitized = att.fileName.trim()
                if (sanitized.isBlank() || sanitized.contains('/') || sanitized.contains('\\') ||
                    sanitized.contains("..") || sanitized.startsWith('.')) {
                    pickError = "Filename can't contain /, \\, .. or start with ."
                    return@onSuccess
                }
                if (state.pendingAttachments.size >= 8) {
                    pickError = "Maximum 8 attachments per message"
                    return@onSuccess
                }
                // The whole message travels as one base64 frame, which must
                // stay inside the WebSocket's outgoing queue limit.
                val totalAfter = state.pendingAttachments.sumOf { it.rawBytes } + att.rawBytes
                if (totalAfter > com.verzeta.android.remote.RemoteRepository.MAX_UPLOAD_RAW_BYTES) {
                    pickError = "Attachments can total at most 11 MB per message"
                    return@onSuccess
                }
                pickError = null
                actions.addPendingAttachment(att.copy(fileName = sanitized))
            }
            out.onFailure { e -> pickError = e.message ?: "Pick failed" }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        if (state.pendingAttachments.isNotEmpty()) {
            PendingAttachmentsStrip(
                attachments = state.pendingAttachments,
                onRemove = actions::removePendingAttachment,
            )
            Spacer(Modifier.size(6.dp))
        }
        pickError?.let { msg ->
            Text(
                msg,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 12.dp, bottom = 4.dp),
            )
        }
        SlashCommandHints(
            draft = state.messageDraft,
            onPick = actions::updateMessageDraft,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .padding(horizontal = 6.dp, vertical = 6.dp)
                .heightIn(min = 44.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = { pickFile.launch("*/*") },
                enabled = !state.busy && state.pendingAttachments.size < 8,
            ) {
                Icon(
                    Icons.Filled.AttachFile,
                    contentDescription = "Attach file",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box(modifier = Modifier.weight(1f).padding(horizontal = 4.dp, vertical = 8.dp)) {
                if (state.messageDraft.isEmpty()) {
                    Text(
                        if (state.pendingAttachments.isNotEmpty()) "Add a caption…" else "Ask anything…",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                BasicTextField(
                    value = state.messageDraft,
                    onValueChange = actions::updateMessageDraft,
                    textStyle = TextStyle(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 16.sp,
                        lineHeight = 22.sp,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.busy,
                )
            }
            val fill = state.selectedConversationId
                ?.let { state.contextFillPercents[it] }
            if (fill != null) {
                Text(
                    text = "$fill%",
                    style = MaterialTheme.typography.labelSmall,
                    color = when {
                        fill >= 90 -> MaterialTheme.colorScheme.error
                        fill >= 70 -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            val streaming = state.messages.lastOrNull()?.streaming == true
            val canSend = streaming ||
                state.pendingAttachments.isNotEmpty() ||
                state.messageDraft.isNotBlank()
            SendButton(
                streaming = streaming,
                enabled = canSend,
                onSend = actions::sendWithAttachments,
                onStop = actions::stopGeneration,
            )
        }
    }
}

@Composable
private fun PendingAttachmentsStrip(
    attachments: List<com.verzeta.android.data.chat.OutgoingAttachment>,
    onRemove: (Int) -> Unit,
) {
    val scroll = androidx.compose.foundation.rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scroll)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
    ) {
        attachments.forEachIndexed { index, att ->
            AttachmentChip(
                attachment = att,
                onRemove = { onRemove(index) },
            )
        }
    }
}

@Composable
private fun AttachmentChip(
    attachment: com.verzeta.android.data.chat.OutgoingAttachment,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val isImage = attachment.mimeType.startsWith("image/")
        Icon(
            if (isImage) androidx.compose.material.icons.Icons.Filled.Image
            else androidx.compose.material.icons.Icons.Filled.Description,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.size(6.dp))
        Text(
            attachment.fileName.let { if (it.length > 22) it.take(20) + "…" else it },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.size(2.dp))
        Text(
            "${attachment.rawBytes / 1024} KB",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(2.dp))
        IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "Remove",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@Composable
private fun SendButton(streaming: Boolean, enabled: Boolean, onSend: () -> Unit, onStop: () -> Unit) {
    val container = if (streaming) MaterialTheme.colorScheme.error
    else MaterialTheme.colorScheme.primary
    val onContainer = if (streaming) MaterialTheme.colorScheme.onError
    else MaterialTheme.colorScheme.onPrimary
    val callback = if (streaming) onStop else onSend
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(container.copy(alpha = if (enabled) 1f else 0.38f)),
        contentAlignment = Alignment.Center,
    ) {
        IconButton(
            onClick = callback,
            enabled = enabled,
            modifier = Modifier.size(40.dp),
        ) {
            Icon(
                imageVector = if (streaming) Icons.Filled.Stop else Icons.Filled.ArrowUpward,
                contentDescription = if (streaming) "Stop" else "Send",
                tint = onContainer,
            )
        }
    }
}

@Composable
private fun ThinkingIndicator() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 22.dp, top = 12.dp, end = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VerzetaMark(size = 18)
        Spacer(Modifier.size(8.dp))
        Text(
            text = "thinking",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(8.dp))
        Box(
            modifier = Modifier
                .size(width = 28.dp, height = 6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
        )
    }
}

private val MessageRole.label: String
    get() = when (this) {
        MessageRole.User -> "You"
        MessageRole.Assistant -> "Assistant"
        MessageRole.System -> "System"
        MessageRole.Tool -> "Tool"
        MessageRole.Unknown -> "Message"
    }
