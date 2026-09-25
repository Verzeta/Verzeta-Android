// SPDX-FileCopyrightText: 2026 Aditya Mehra <aix.m@outlook.com>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

/**
 * @file ChatModels.kt
 * @brief Core chat models: the conversation row with its sidebar
 *        classification logic, the message row (including the render-only
 *        reasoning sidecar), the message role enum, and the send-result
 *        id pair.
 * @layer Model
 * @dependencies None — pure Kotlin data declarations.
 */

package com.verzeta.android.data.chat

/**
 * Conversation row, projected from the host's `conv.list` / `conv.get`
 * response. Field set tracks the host's `Serialise::conversation()` exactly:
 * id, title, folder_id, created_at, updated_at, system_prompt, token_total,
 * primary_agent_id, is_group, is_pinned, group_agent_ids. No fictional
 * fields (preview, agent name, member count, etc.) — the host doesn't emit
 * those over remote-ws.
 */
data class ConversationUi(
    val id: String,
    val title: String,
    val folderId: String? = null,
    val primaryAgentId: String? = null,
    val isGroup: Boolean = false,
    val isPinned: Boolean = false,
    val groupAgentIds: List<String> = emptyList(),
    val createdAt: String = "",
    val updatedAt: String = "",
    val tokenTotal: Long = 0L,
    val systemPrompt: String = "",
    val streaming: Boolean = false,
) {
    /**
     * Classify against the host's `SidebarFlatModel` rules from
     * `frontend/components/sidebar-flat-model.cpp`.
     *
     * Pinned wins over every other classification. Otherwise the
     * conversation goes into Projects&Orgs ONLY when its `folder_id`
     * resolves to a project or organization folder. Conversations whose
     * `folder_id` resolves to a regular folder (or to a folder we
     * haven't received metadata for yet) fall through to the root-level
     * rules — desktop QML doesn't show them under the Projects header
     * either, and Android must match.
     *
     * @param projectFolderIds  ids of folders whose `folder_type` is
     *                          `project` or `organization`. Pass an
     *                          empty set when no folder metadata is
     *                          loaded yet — that defaults every
     *                          folder-attached conversation to root
     *                          classification, which is the safer
     *                          default than mis-bucketing them as
     *                          Projects&Orgs.
     */
    fun classify(projectFolderIds: Set<String> = emptySet()): ConversationKind = when {
        isPinned -> ConversationKind.Pinned
        folderId != null && folderId in projectFolderIds -> ConversationKind.ProjectsAndOrgs
        isGroup -> ConversationKind.StandaloneGroupChat
        !primaryAgentId.isNullOrBlank() -> ConversationKind.DirectAgentChat
        else -> ConversationKind.PlainChat
    }
}

/**
 * Sidebar bucket a conversation classifies into — pinned wins, then
 * project / organization membership, then the root-level group / direct /
 * plain split.
 */
enum class ConversationKind {
    Pinned,
    ProjectsAndOrgs,
    StandaloneGroupChat,
    DirectAgentChat,
    PlainChat,
}

/**
 * Message row, projected from the host's `msg.list` response and from the
 * `message.added` / `message.updated` / `message.streaming.*` events. Field
 * set tracks `Serialise::message()` exactly.
 */
data class MessageUi(
    val id: String,
    val role: MessageRole,
    val text: String,
    val createdAt: String = "",
    val tokenCount: Long = 0L,
    val modelUsed: String = "",
    val finishReason: String = "",
    val agentId: String? = null,
    val memberAlias: String? = null,
    val turnId: String? = null,
    val streaming: Boolean = false,
    /**
     * Non-null when this message owns an inline poll card. Parsed
     * from `msg.list` row's `metadata.poll_id` field. PollService
     * auto-persists a message with this metadata for every poll it
     * creates so the card renders inline in the conversation
     * timeline.
     */
    val pollId: String? = null,
    /**
     * Reasoning sidecar captured from the provider's thinking channel
     * on the desktop host (schema v16 `messages.thinking_content`).
     * Empty for non-reasoning models, for messages produced before
     * schema v16, and for messages whose captured thinking was
     * whitespace-only after the host's trim-to-empty discard at the
     * persistence boundary.
     *
     * Render-only by contract.  NEVER written into any outbound wire
     * payload — no `msg.send` / `msg.send_with_attachments` / similar
     * accepts the field.  Mirrors the desktop's locked invariant:
     * surfacing thinking that round-trips into prompts explodes
     * context AND creates feedback loops.  Treat this field as
     * inbound-only data the chat surface renders inside an
     * [AssistantThinkingDisclosure] collapsed-by-default inside the
     * assistant bubble.
     */
    val thinkingContent: String = "",
)

/**
 * Author role of a message row. [Unknown] absorbs wire values this
 * client version doesn't recognise.
 */
enum class MessageRole {
    User,
    Assistant,
    System,
    Tool,
    Unknown,
}

/**
 * Id pair returned by a send: the persisted user message and the
 * assistant placeholder the streaming events will fill in. Either may be
 * null when the host did not create the corresponding row.
 */
data class SendMessageResult(
    val userMessageId: String?,
    val assistantPlaceholderId: String?,
)
