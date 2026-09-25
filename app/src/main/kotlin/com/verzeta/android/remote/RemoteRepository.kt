// SPDX-FileCopyrightText: 2026 Aditya Mehra <aix.m@outlook.com>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

/**
 * @file RemoteRepository.kt
 * @brief Typed facade over the host's wire-protocol op surface, providing suspend
 *        functions that map 1:1 to the ops dispatched by the host's
 *        `backend/remote/server/wire-session.cpp::dispatchOp`.
 * @layer Remote
 * @dependencies RemoteSession; protocol.RemoteJson; data.* UI models;
 *               kotlinx.serialization.json
 */

package com.verzeta.android.remote

import com.verzeta.android.data.activity.ActivityEventUi
import com.verzeta.android.data.chat.AgentPattern
import com.verzeta.android.data.chat.ArtifactRowUi
import com.verzeta.android.data.chat.AttachmentMetaUi
import com.verzeta.android.data.chat.AttachmentType
import com.verzeta.android.data.chat.ConvSettingsUi
import com.verzeta.android.data.chat.ConversationUi
import com.verzeta.android.data.chat.DownloadPayloadUi
import com.verzeta.android.data.chat.GeneratedFileUi
import com.verzeta.android.data.chat.OutgoingAttachment
import com.verzeta.android.data.chat.MessageRole
import com.verzeta.android.data.chat.MessageUi
import com.verzeta.android.data.chat.SendMessageResult
import com.verzeta.android.data.agent.AgentSummaryUi
import com.verzeta.android.data.agent.ToolCallStatus
import com.verzeta.android.data.agent.ToolCallUi
import com.verzeta.android.data.canvas.CanvasAiActionUi
import com.verzeta.android.data.canvas.CanvasSandboxStateUi
import com.verzeta.android.data.canvas.CanvasToolResultUi
import com.verzeta.android.data.canvas.CanvasToolUi
import com.verzeta.android.data.canvas.CanvasUi
import com.verzeta.android.data.folder.FolderCreateResult
import com.verzeta.android.data.folder.FolderMemberUi
import com.verzeta.android.data.folder.FolderType
import com.verzeta.android.data.folder.FolderUi
import com.verzeta.android.data.folder.GroupMemberInput
import com.verzeta.android.data.folder.ProjectDocumentUi
import com.verzeta.android.data.heartbeat.HeartbeatConfigChangeUi
import com.verzeta.android.data.heartbeat.HeartbeatConfigUi
import com.verzeta.android.data.heartbeat.HeartbeatNextFireUi
import com.verzeta.android.data.heartbeat.HeartbeatRunUi
import com.verzeta.android.data.heartbeat.HeartbeatScope
import com.verzeta.android.data.heartbeat.HeartbeatStatusUi
import com.verzeta.android.data.mcp.McpServerUi
import com.verzeta.android.data.mcp.McpStatus
import com.verzeta.android.data.mcp.McpToolUi
import com.verzeta.android.data.plan.PlanStatus
import com.verzeta.android.data.plan.PlanStepUi
import com.verzeta.android.data.plan.PlanUi
import com.verzeta.android.data.plan.StepStatus
import com.verzeta.android.data.model.ModelCatalogUi
import com.verzeta.android.data.model.ProviderUi
import com.verzeta.android.data.model.SearchProviderUi
import com.verzeta.android.data.model.SearchProvidersUi
import com.verzeta.android.data.projectroom.ProjectTemplateUi
import com.verzeta.android.data.projectroom.TemplateCustomisationsUi
import com.verzeta.android.data.projectroom.TemplateMemberSummary
import com.verzeta.android.data.projectroom.TemplateRosterMemberUi
import com.verzeta.android.data.skill.PreferredSkillsScope
import com.verzeta.android.data.skill.PreferredSkillsUi
import com.verzeta.android.data.skill.SkillUi
import com.verzeta.android.data.skill.SkillWarningUi
import com.verzeta.android.data.tool.ToolKind
import com.verzeta.android.data.tool.ToolParameterUi
import com.verzeta.android.data.tool.ToolUi
import com.verzeta.android.data.workbench.ClientUi
import com.verzeta.android.protocol.AuthMeResponse
import com.verzeta.android.protocol.PairResponse
import com.verzeta.android.protocol.PingResponse
import com.verzeta.android.protocol.RemoteError
import com.verzeta.android.protocol.RemoteJson
import com.verzeta.android.protocol.TokenAuthResponse
import com.verzeta.android.util.normaliseTimestamp
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Typed facade over the host's wire-protocol op surface.
 *
 * Every suspend fun below maps 1:1 to a `case` in the host's
 * `backend/remote/server/wire-session.cpp::dispatchOp`. The dispatch table
 * covers ~150 ops across auth / conv / msg / folder / group / models /
 * agent / tool / mcp / skill / poll / activity / heartbeat / canvas / plan /
 * task / project_template — the wire-session.cpp dispatch table is the source
 * of truth. If a method here doesn't map to a wire dispatch case the host
 * returns `unknown_op` and the Result lands as failure.
 *
 * Do NOT add ops here that the host doesn't dispatch — the Android UI
 * must not pretend a feature exists.
 */
class RemoteRepository(
    private val session: RemoteSession,
) {
    val connectionState: kotlinx.coroutines.flow.StateFlow<ConnectionState> = session.connectionState

    fun connect(endpoint: RemoteEndpoint): Result<Unit> = session.connect(endpoint)
    fun connect(endpoint: RemoteEndpoint, tlsCertSha256: String): Result<Unit> =
        session.connect(endpoint, tlsCertSha256)
    fun disconnect() = session.disconnect()

    // --- Auth ---------------------------------------------------------------

    suspend fun pair(code: String, clientName: String): Result<PairResponse> =
        request(
            op = "auth.pair",
            params = buildJsonObject {
                put("code", code)
                put("client_name", clientName)
            },
            serializer = PairResponse.serializer(),
        )

    suspend fun authenticateToken(token: String): Result<TokenAuthResponse> =
        request(
            op = "auth.token",
            params = buildJsonObject { put("token", token) },
            serializer = TokenAuthResponse.serializer(),
        )

    suspend fun authMe(): Result<AuthMeResponse> =
        request("auth.me", JsonObject(emptyMap()), AuthMeResponse.serializer())

    suspend fun ping(): Result<PingResponse> =
        request("ping", JsonObject(emptyMap()), PingResponse.serializer())

    suspend fun revokeSelf(): Result<Unit> = ack("auth.revoke_self", JsonObject(emptyMap()))

    // --- Conversations ------------------------------------------------------

    suspend fun listConversations(limit: Int = 200, offset: Int = 0): Result<List<ConversationUi>> =
        requestJson(
            op = "conv.list",
            params = buildJsonObject {
                put("limit", limit)
                put("offset", offset)
            },
        ).mapCatching(::parseConversations)

    suspend fun getConversation(conversationId: String): Result<ConversationUi> =
        requestJson(
            op = "conv.get",
            params = buildJsonObject { put("id", conversationId) },
        ).mapCatching(::parseConversation)

    /**
     * Create a new conversation. Host returns only `{id}`; the actual
     * row state arrives via the `conv.added` event. Caller should NOT
     * synthesize a `ConversationUi` from the returned id — wait for the
     * event so all fields land at once.
     */
    suspend fun createConversation(title: String? = null): Result<String> =
        requestJson(
            op = "conv.create",
            params = buildJsonObject {
                if (!title.isNullOrBlank()) put("title", title)
            },
        ).mapCatching(::parseId)

    suspend fun createConversationWithAgent(agentId: String, title: String? = null): Result<String> =
        requestJson(
            op = "conv.create_with_agent",
            params = buildJsonObject {
                put("agent_id", agentId)
                if (!title.isNullOrBlank()) put("title", title)
            },
        ).mapCatching(::parseId)

    suspend fun renameConversation(id: String, title: String): Result<Unit> =
        ack(
            op = "conv.rename",
            params = buildJsonObject {
                put("id", id)
                put("title", title)
            },
        )

    suspend fun setConversationPinned(id: String, pinned: Boolean): Result<Unit> =
        ack(
            op = "conv.set_pinned",
            params = buildJsonObject {
                put("id", id)
                put("pinned", pinned)
            },
        )

    /**
     * Fire-and-forget delete. Host responds `{queued: true}` and emits
     * `conv.deleted` once the row is gone. UI should mark the row as
     * "deleting" until the event arrives — see MainViewModel.deleteConversation.
     */
    suspend fun deleteConversation(id: String): Result<Unit> =
        ack(op = "conv.delete", params = buildJsonObject { put("id", id) })

    /**
     * Move a conversation to a folder. Pass an empty string to move it to
     * root — do NOT omit the field, the host requires it explicitly.
     */
    suspend fun moveConversationToFolder(conversationId: String, folderId: String): Result<Unit> =
        ack(
            op = "conv.move_to_folder",
            params = buildJsonObject {
                put("conv_id", conversationId)
                put("folder_id", folderId)
            },
        )

    suspend fun isConversationGroup(id: String): Result<Boolean> =
        requestJson(
            op = "conv.is_group",
            params = buildJsonObject { put("id", id) },
        ).mapCatching { (it as? JsonObject)?.boolFlag("is_group") ?: false }

    suspend fun listConversationGroupMembers(id: String): Result<List<JsonObject>> =
        requestJson(
            op = "conv.group_members",
            params = buildJsonObject { put("id", id) },
        ).mapCatching { element ->
            (element as? JsonArray)?.mapNotNull { it as? JsonObject } ?: emptyList()
        }

    suspend fun getConversationFolderId(id: String): Result<String> =
        requestJson(
            op = "conv.folder_id",
            params = buildJsonObject { put("id", id) },
        ).mapCatching { (it as? JsonObject)?.string("folder_id").orEmpty() }

    // --- Messages -----------------------------------------------------------

    suspend fun listMessages(conversationId: String, limit: Int = 200): Result<List<MessageUi>> =
        requestJson(
            op = "msg.list",
            params = buildJsonObject {
                put("conv_id", conversationId)
                put("limit", limit)
            },
        ).mapCatching(::parseMessages)

    suspend fun sendMessage(conversationId: String, text: String): Result<SendMessageResult> =
        requestJson(
            op = "msg.send",
            params = buildJsonObject {
                put("conv_id", conversationId)
                put("text", text)
            },
        ).mapCatching(::parseSendMessageResult)

    /**
     * The host's `msg.stop` op takes no params — it stops whichever conversation
     * the wire client's per-client ChatController currently has in flight.
     * No `conv_id` is sent because the host routes via the per-client
     * ChatController and silently drops extraneous fields.
     */
    suspend fun stopGeneration(): Result<Unit> =
        ack("msg.stop", JsonObject(emptyMap()))

    suspend fun subscribeMessages(conversationId: String): Result<Unit> =
        ack("msg.subscribe", buildJsonObject { put("conv_id", conversationId) })

    suspend fun unsubscribeMessages(conversationId: String): Result<Unit> =
        ack("msg.unsubscribe", buildJsonObject { put("conv_id", conversationId) })

    /**
     * Read the conversation's saved provider+model from its llm_config.
     * Does NOT change the host's active conv (would hijack the desktop's
     * view — see opConvOpen on the wire side). Returns
     * (provider, model) — empty when the conv has no saved config.
     * The Android UI calls this on chat-open and binds the chat header
     * + model picker to these values so each conversation shows its
     * own configured model independently of what desktop has selected.
     */
    suspend fun readConversationModel(conversationId: String): Result<Pair<String, String>> =
        requestJson(
            op = "conv.open",
            params = buildJsonObject { put("id", conversationId) },
        ).mapCatching { data ->
            val obj = data as? JsonObject ?: JsonObject(emptyMap())
            (obj.string("provider").orEmpty()) to (obj.string("model").orEmpty())
        }

    // --- Folders / Projects / Organizations ---------------------------------

    /** Returns folders whose `folder_type` is `project` or `organization`. */
    suspend fun listProjectFolders(): Result<List<FolderUi>> =
        requestJson("folder.list_projects", JsonObject(emptyMap())).mapCatching(::parseFoldersFromList)

    /**
     * Returns every folder regardless of type — used by the cleanup /
     * admin surface in the Android FoldersScreen "Show all" toggle so the
     * user can see legacy `regular` folders that don't appear in
     * `folder.list_projects` and convert or delete them.
     */
    suspend fun listAllFolders(): Result<List<FolderUi>> =
        requestJson("folder.list_all", JsonObject(emptyMap())).mapCatching(::parseFoldersFromList)

    /**
     * Returns the conversation rows whose `folder_id` matches [folderId].
     * Pass empty string for root-level conversations.
     *
     * Host emits a slim shape here ({id, title, isGroup, updatedAtMs}) — we
     * map it onto [ConversationUi] with the fields populated; callers that
     * need the full snapshot should call [getConversation] on a row.
     */
    suspend fun listFolderConversations(folderId: String): Result<List<ConversationUi>> =
        requestJson(
            op = "folder.conversations",
            params = buildJsonObject { put("folder_id", folderId) },
        ).mapCatching(::parseFolderConversations)

    /**
     * Create a folder.
     *
     * The host's `folder.create` stores only the name: every new folder
     * starts as `regular` and the reply always reports `type: "regular"`,
     * whatever `type`, `goal`, `description` or `agent_ids` were sent.
     * Those fields are still sent for hosts that honour them, but a caller
     * that wants a project or organization must follow up with
     * [updateFolderMetadata] (see MainViewModel.createFolderWithMembers).
     */
    suspend fun createFolder(
        name: String,
        type: FolderType = FolderType.Project,
        goal: String = "",
        description: String = "",
        agentIds: List<String> = emptyList(),
    ): Result<FolderCreateResult> = requestJson(
        op = "folder.create",
        params = buildJsonObject {
            put("name", name)
            put("type", type.wireValue)
            if (type != FolderType.Regular) {
                if (goal.isNotEmpty()) put("goal", goal)
                if (description.isNotEmpty()) put("description", description)
                if (agentIds.isNotEmpty()) {
                    put("agent_ids", JsonArray(agentIds.map { kotlinx.serialization.json.JsonPrimitive(it) }))
                }
            }
        },
    ).mapCatching(::parseFolderCreateResult)

    suspend fun renameFolder(id: String, name: String): Result<Unit> =
        ack(
            op = "folder.rename",
            params = buildJsonObject {
                put("id", id)
                put("name", name)
            },
        )

    suspend fun deleteFolder(id: String): Result<Unit> =
        ack(op = "folder.delete", params = buildJsonObject { put("id", id) })

    suspend fun getFolder(id: String): Result<FolderUi> =
        requestJson(
            op = "folder.info",
            params = buildJsonObject { put("id", id) },
        ).mapCatching(::parseFolderInfo)

    suspend fun updateFolderMetadata(
        id: String,
        folderType: FolderType,
        goal: String,
        description: String,
        agentIds: List<String>,
    ): Result<Unit> = ack(
        op = "folder.update_metadata",
        params = buildJsonObject {
            put("id", id)
            put("folder_type", folderType.wireValue)
            put("goal", goal)
            put("description", description)
            put("agent_ids", JsonArray(agentIds.map { kotlinx.serialization.json.JsonPrimitive(it) }))
        },
    )


    /**
     * Read the project / organization member list for a folder. Returns an
     * empty list for regular folders (host has nothing to return). Response
     * keys are camelCase per `MembershipService::memberToMap`.
     */
    suspend fun listFolderMembers(folderId: String): Result<List<FolderMemberUi>> =
        requestJson(
            op = "folder.members",
            params = buildJsonObject { put("folder_id", folderId) },
        ).mapCatching(::parseMembers)

    /**
     * Replace the folder's member list. Host validates upfront — see
     * the wire-rejection table in the spec. Common errors:
     * `invalid_params` / `not_found`. The reply data is a bare boolean;
     * `false` (for example a duplicate alias rolled back) is reported as a
     * failure by [ack].
     *
     * The host deletes and re-inserts every row. Each member therefore
     * carries its model provider, model name, tool allowlist and
     * provenance (`added_by_kind`, `added_by_agent_id`) when it has them,
     * so overrides set on the desktop survive the save. A host that does
     * not read those fields still drops them, so call this only when the
     * roster changed.
     *
     * Wire payload uses snake_case (`agent_id`, `is_coordinator`); the
     * host accepts camelCase too but we stay consistent with the rest
     * of the protocol.
     */
    suspend fun setFolderMembers(folderId: String, members: List<FolderMemberUi>): Result<Unit> =
        ack(
            op = "folder.members.set",
            params = buildJsonObject {
                put("folder_id", folderId)
                put("members", folderMembersToJsonArray(members))
            },
        )

    /** Read a conversation's member list. Same shape as folder.members. */
    suspend fun listConversationMembers(conversationId: String): Result<List<FolderMemberUi>> =
        requestJson(
            op = "conv.members",
            params = buildJsonObject { put("conv_id", conversationId) },
        ).mapCatching(::parseMembers)

    suspend fun setConversationMembers(
        conversationId: String,
        members: List<FolderMemberUi>,
    ): Result<Unit> = ack(
        op = "conv.members.set",
        params = buildJsonObject {
            put("conv_id", conversationId)
            put("members", membersToJsonArray(members))
        },
    )

    /**
     * Set / clear the conversation's coordinator. Pass an empty alias to
     * clear coordinator. The targeted member's `isCoordinator` flips and
     * any other coordinator in the same conversation is cleared.
     */
    suspend fun setConversationCoordinator(conversationId: String, alias: String): Result<Unit> =
        ack(
            op = "conv.coordinator.set",
            params = buildJsonObject {
                put("conv_id", conversationId)
                put("alias", alias)
            },
        )


    suspend fun addFolderMember(
        folderId: String,
        agentId: String,
        alias: String,
        isCoordinator: Boolean = false,
    ): Result<Unit> = ack(
        op = "folder.member.add",
        params = buildJsonObject {
            put("folder_id", folderId)
            put("agent_id", agentId)
            put("alias", alias)
            put("is_coordinator", isCoordinator)
        },
    )

    suspend fun removeFolderMember(folderId: String, alias: String): Result<Unit> = ack(
        op = "folder.member.remove",
        params = buildJsonObject {
            put("folder_id", folderId)
            put("alias", alias)
        },
    )

    suspend fun addConversationMember(
        conversationId: String,
        agentId: String,
        alias: String,
        isCoordinator: Boolean = false,
    ): Result<Unit> = ack(
        op = "conv.member.add",
        params = buildJsonObject {
            put("conv_id", conversationId)
            put("agent_id", agentId)
            put("alias", alias)
            put("is_coordinator", isCoordinator)
        },
    )

    suspend fun removeConversationMember(conversationId: String, alias: String): Result<Unit> = ack(
        op = "conv.member.remove",
        params = buildJsonObject {
            put("conv_id", conversationId)
            put("alias", alias)
        },
    )


    /**
     * List every poll (open + closed) in [conversationId], or just open
     * polls when [openOnly] is true. Returns parsed PollUi entries.
     */
    suspend fun listPollsForConversation(
        conversationId: String,
        openOnly: Boolean = false,
    ): Result<List<com.verzeta.android.data.poll.PollUi>> =
        requestJson(
            op = "poll.list",
            params = buildJsonObject {
                put("conv_id", conversationId)
                put("open_only", openOnly)
            },
        ).mapCatching(::parsePollList)

    suspend fun pollResults(pollId: String):
        Result<com.verzeta.android.data.poll.PollUi> =
        requestJson(
            op = "poll.results",
            params = buildJsonObject { put("poll_id", pollId) },
        ).mapCatching { element ->
            (element as? JsonObject)
                ?.let(::parsePollObject)
                ?: throw RemoteProtocolException(
                    RemoteError("missing_data", "poll.results missing payload"),
                )
        }

    /**
     * User-initiated poll creation. Agent-initiated polls go through
     * the start_poll tool path on the host. mode is "single" or
     * "multi"; ranked is rejected by the host.
     */
    suspend fun startPoll(
        conversationId: String,
        question: String,
        options: List<String>,
        mode: String = "single",
        closesInMinutes: Int = 0,
    ): Result<String> =
        requestJson(
            op = "poll.start",
            params = buildJsonObject {
                put("conv_id", conversationId)
                put("question", question)
                put("options", JsonArray(options.map { JsonPrimitive(it) }))
                put("mode", mode)
                put("closes_in_minutes", closesInMinutes)
            },
        ).mapCatching { parseIdOf(it, "poll_id", "id") }

    /**
     * Cast the human user's vote. Provide [optionId] when known, or
     * [optionText] for case-insensitive resolution server-side.
     */
    suspend fun castPollVote(
        pollId: String,
        optionId: String? = null,
        optionText: String? = null,
    ): Result<Unit> = ack(
        op = "poll.vote",
        params = buildJsonObject {
            put("poll_id", pollId)
            optionId?.let { put("option_id", it) }
            optionText?.let { put("option_text", it) }
        },
    )

    suspend fun closePoll(pollId: String): Result<Unit> = ack(
        op = "poll.close",
        params = buildJsonObject { put("poll_id", pollId) },
    )


    suspend fun activityForProject(
        folderId: String,
        limit: Int = 100,
    ): Result<List<ActivityEventUi>> =
        requestJson(
            op = "activity.for_project",
            params = buildJsonObject {
                put("folder_id", folderId)
                put("limit", limit)
            },
        ).mapCatching(::parseActivityList)

    suspend fun activityForConversation(
        conversationId: String,
        limit: Int = 100,
    ): Result<List<ActivityEventUi>> =
        requestJson(
            op = "activity.for_conversation",
            params = buildJsonObject {
                put("conv_id", conversationId)
                put("limit", limit)
            },
        ).mapCatching(::parseActivityList)

    suspend fun activityByTurn(turnId: String): Result<List<ActivityEventUi>> =
        requestJson(
            op = "activity.by_turn",
            params = buildJsonObject { put("turn_id", turnId) },
        ).mapCatching(::parseActivityList)


    suspend fun listProjectTemplates(): Result<List<ProjectTemplateUi>> =
        requestJson("project_template.list", JsonObject(emptyMap()))
            .mapCatching(::parseProjectTemplateList)

    suspend fun listLandingProjectTemplates(): Result<List<ProjectTemplateUi>> =
        requestJson("project_template.list_landing", JsonObject(emptyMap()))
            .mapCatching(::parseProjectTemplateList)

    suspend fun getProjectTemplate(templateId: String): Result<ProjectTemplateUi> =
        requestJson(
            op = "project_template.get",
            params = buildJsonObject { put("id", templateId) },
        ).mapCatching(::parseProjectTemplateMap)

    suspend fun getProjectTemplateRoster(
        templateId: String,
    ): Result<List<TemplateRosterMemberUi>> =
        requestJson(
            op = "project_template.roster",
            params = buildJsonObject { put("id", templateId) },
        ).mapCatching(::parseTemplateRoster)

    /**
     * Create a new project from a template. `customisations` carries any
     * of `name`, `goal`, `description`, `scenario`, `members` — all
     * optional; the host falls back to the template's built-in defaults
     * for anything omitted. `members` REPLACES the template's default
     * roster entirely when present.
     *
     * Returns the new folder UUID on success; the wire-side
     * createProjectFromTemplate runs the SQL inside a single transaction
     * so either every row writes or every row rolls back.
     */
    suspend fun createProjectFromTemplate(
        templateId: String,
        customisations: TemplateCustomisationsUi = TemplateCustomisationsUi(),
    ): Result<String> = requestJson(
        op = "project_template.create_project",
        params = buildJsonObject {
            put("id", templateId)
            put("customisations", buildJsonObject {
                if (customisations.name.isNotBlank()) put("name", customisations.name)
                if (customisations.goal.isNotBlank()) put("goal", customisations.goal)
                if (customisations.description.isNotBlank())
                    put("description", customisations.description)
                if (customisations.scenario.isNotBlank())
                    put("scenario", customisations.scenario)
                customisations.members?.let { roster ->
                    put("members", JsonArray(
                        roster.map { m ->
                            buildJsonObject {
                                put("agentId", m.agentId)
                                put("alias", m.alias)
                                put("isCoordinator", m.isCoordinator)
                                // The host stores these per-member overrides;
                                // send only what is set.
                                if (m.modelProvider.isNotBlank()) put("modelProvider", m.modelProvider)
                                if (m.modelName.isNotBlank()) put("modelName", m.modelName)
                                if (m.allowedTools.isNotEmpty()) {
                                    put("allowedTools", JsonArray(m.allowedTools.map { JsonPrimitive(it) }))
                                }
                            }
                        },
                    ))
                }
            })
        },
    ).mapCatching { data ->
        (data as? JsonObject)?.string("folder_id").orEmpty().takeIf { it.isNotBlank() }
            ?: throw RemoteProtocolException(
                RemoteError("missing_folder_id",
                              "project_template.create_project response missing folder_id"),
            )
    }

    suspend fun setProjectTemplatePinned(
        templateId: String,
        pinned: Boolean,
    ): Result<Boolean> = requestJson(
        op = "project_template.pin_user",
        params = buildJsonObject {
            put("id", templateId)
            put("pinned", pinned)
        },
    ).mapCatching(::parseBoolReply)

    /**
     * Save the current template (or a neutral default when [sourceTemplateId]
     * is empty) as a new USER template. `edits` carries the fields the
     * user wants to set: `name`, `scenario`, `goal`, `description`,
     * `members`. Members use MembershipEditor-style rows ({agentId, alias,
     * isCoordinator}).
     *
     * Returns the new user-template id on success.
     */
    suspend fun saveAsNewProjectTemplate(
        sourceTemplateId: String,
        edits: TemplateCustomisationsUi,
    ): Result<String> = requestJson(
        op = "project_template.save_as_new",
        params = buildJsonObject {
            put("source_template_id", sourceTemplateId)
            put("edits", buildJsonObject {
                if (edits.name.isNotBlank()) put("name", edits.name)
                if (edits.goal.isNotBlank()) put("goal", edits.goal)
                if (edits.description.isNotBlank()) put("description", edits.description)
                if (edits.scenario.isNotBlank()) put("scenario", edits.scenario)
                // The design chosen with the banner arrows.
                if (edits.geometryKind.isNotBlank()) put("geometryKind", edits.geometryKind)
                edits.baseHue?.let { put("baseHue", it) }
                edits.members?.let { roster ->
                    put("members", JsonArray(
                        roster.map { m ->
                            buildJsonObject {
                                put("agentId", m.agentId)
                                put("alias", m.alias)
                                put("isCoordinator", m.isCoordinator)
                                // The host stores these per-member overrides;
                                // send only what is set.
                                if (m.modelProvider.isNotBlank()) put("modelProvider", m.modelProvider)
                                if (m.modelName.isNotBlank()) put("modelName", m.modelName)
                                if (m.allowedTools.isNotEmpty()) {
                                    put("allowedTools", JsonArray(m.allowedTools.map { JsonPrimitive(it) }))
                                }
                            }
                        },
                    ))
                }
            })
        },
    ).mapCatching { data ->
        // The host answers an empty id when it refused to save, which
        // happens when the name is empty or the file could not be written.
        (data as? JsonObject)?.string("template_id").orEmpty().takeIf { it.isNotBlank() }
            ?: throw RemoteProtocolException(
                RemoteError(
                    "missing_template_id",
                    "The desktop did not save the template. Check that it has a name and try again.",
                ),
            )
    }

    suspend fun deleteUserProjectTemplate(templateId: String): Result<Boolean> =
        requestJson(
            op = "project_template.delete_user",
            params = buildJsonObject { put("id", templateId) },
        ).mapCatching(::parseBoolReply)


    suspend fun listFolderDocuments(folderId: String): Result<List<ProjectDocumentUi>> =
        requestJson(
            op = "folder.documents",
            params = buildJsonObject { put("folder_id", folderId) },
        ).mapCatching(::parseDocuments)

    /**
     * Upload a document into a project's docs folder. The caller is
     * responsible for rejecting sources larger than
     * [MAX_UPLOAD_RAW_BYTES], which keeps the base64 frame inside the
     * WebSocket's outgoing queue limit.
     *
     * `file_name` must not contain `/`, `\\`, `..`, and must not start
     * with `.` (host rejects with `invalid_params`).
     *
     * @returns success when the host stored the document. The host replies
     *          `{file_name}` and reports a failed copy as an empty name,
     *          which is returned as a failure here.
     */
    suspend fun uploadFolderDocument(
        folderId: String,
        fileName: String,
        contentBase64: String,
    ): Result<Unit> = requestJson(
        op = "folder.documents.upload",
        params = buildJsonObject {
            put("folder_id", folderId)
            put("file_name", fileName)
            put("content_base64", contentBase64)
        },
    ).mapCatching { data ->
        if (parseIdOrNull(data, "file_name").isNullOrBlank()) {
            throw RemoteProtocolException(
                RemoteError("not_applied", "The host could not store the document"),
            )
        }
    }

    suspend fun removeFolderDocument(folderId: String, fileName: String): Result<Unit> =
        ack(
            op = "folder.documents.remove",
            params = buildJsonObject {
                put("folder_id", folderId)
                put("file_name", fileName)
            },
        )


    suspend fun kickoffIndividualChats(folderId: String): Result<Int> =
        requestJson(
            op = "folder.kickoff.individual",
            params = buildJsonObject { put("folder_id", folderId) },
        ).mapCatching { (it as? JsonObject)?.get("created_count")?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0 }

    suspend fun kickoffGroupChat(folderId: String): Result<String> =
        requestJson(
            op = "folder.kickoff.group",
            params = buildJsonObject { put("folder_id", folderId) },
        ).mapCatching {
            // The host wraps the new conversation id as `{id}`.
            parseIdOrNull(it, "id", "conv_id")
                ?: throw RemoteProtocolException(
                    RemoteError("missing_data", "The host did not create the group chat"),
                )
        }

    suspend fun openMemberChat(folderId: String, agentId: String, alias: String): Result<String> =
        requestJson(
            op = "folder.member.chat.open",
            params = buildJsonObject {
                put("folder_id", folderId)
                put("agent_id", agentId)
                put("alias", alias)
            },
        ).mapCatching {
            // The host wraps the conversation id as `{id}`.
            parseIdOrNull(it, "id", "conv_id")
                ?: throw RemoteProtocolException(
                    RemoteError("missing_data", "The host could not open a chat with this member"),
                )
        }


    suspend fun listPreferredSkills(scope: PreferredSkillsScope, scopeId: String): Result<List<String>> =
        requestJson(
            op = "skill.preferred.list",
            params = buildJsonObject {
                put("scope_type", scope.wireValue)
                put("scope_id", scopeId)
            },
        ).mapCatching { element ->
            (element as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
        }

    suspend fun setPreferredSkills(
        scope: PreferredSkillsScope,
        scopeId: String,
        skillIds: List<String>,
    ): Result<Unit> = ack(
        op = "skill.preferred.set",
        params = buildJsonObject {
            put("scope_type", scope.wireValue)
            put("scope_id", scopeId)
            put("skill_ids", JsonArray(skillIds.map { kotlinx.serialization.json.JsonPrimitive(it) }))
        },
    )

    suspend fun getExposeOnlyPreferred(scope: PreferredSkillsScope, scopeId: String): Result<Boolean> =
        requestJson(
            op = "skill.expose_only_preferred",
            params = buildJsonObject {
                put("scope_type", scope.wireValue)
                put("scope_id", scopeId)
            },
        ).mapCatching { (it as? JsonObject)?.boolFlag("expose_only") ?: false }

    suspend fun setExposeOnlyPreferred(
        scope: PreferredSkillsScope,
        scopeId: String,
        exposeOnly: Boolean,
    ): Result<Unit> = ack(
        op = "skill.set_expose_only_preferred",
        params = buildJsonObject {
            put("scope_type", scope.wireValue)
            put("scope_id", scopeId)
            put("expose_only", exposeOnly)
        },
    )

    suspend fun getOverrideParentFolder(conversationId: String): Result<Boolean> =
        requestJson(
            op = "skill.override_parent_folder",
            params = buildJsonObject { put("conv_id", conversationId) },
        ).mapCatching { (it as? JsonObject)?.boolFlag("override") ?: false }

    suspend fun setOverrideParentFolder(conversationId: String, override: Boolean): Result<Unit> =
        ack(
            op = "skill.set_override_parent_folder",
            params = buildJsonObject {
                put("conv_id", conversationId)
                put("override", override)
            },
        )


    suspend fun listHeartbeatConfigsForFolder(folderId: String): Result<List<HeartbeatConfigUi>> =
        requestJson(
            op = "heartbeat.configs_for_folder",
            params = buildJsonObject { put("folder_id", folderId) },
        ).mapCatching(::parseHeartbeatConfigs)

    suspend fun listHeartbeatConfigsForConversation(conversationId: String): Result<List<HeartbeatConfigUi>> =
        requestJson(
            op = "heartbeat.configs_for_conversation",
            params = buildJsonObject { put("conv_id", conversationId) },
        ).mapCatching(::parseHeartbeatConfigs)

    suspend fun getHeartbeatConfig(id: String): Result<HeartbeatConfigUi> =
        requestJson(
            op = "heartbeat.config_by_id",
            params = buildJsonObject { put("id", id) },
        ).mapCatching {
            (it as? JsonObject)?.let(::parseHeartbeatConfigObject)
                ?: throw RemoteProtocolException(RemoteError("not_found", "heartbeat config missing"))
        }

    suspend fun getHeartbeatConfigStatus(id: String): Result<HeartbeatStatusUi> =
        requestJson(
            op = "heartbeat.config_status",
            params = buildJsonObject { put("id", id) },
        ).mapCatching(::parseHeartbeatStatus)

    /**
     * Insert (omit id) or update (provide id) a heartbeat config. All
     * write payloads use snake_case per the protocol convention; the
     * host's `heartbeat.upsert` accepts both forms but stays snake_case
     * here so wire dumps stay consistent.
     */
    suspend fun upsertHeartbeatConfig(config: HeartbeatConfigUi): Result<String> =
        requestJson(
            op = "heartbeat.upsert",
            params = buildJsonObject {
                if (config.id.isNotBlank()) put("id", config.id)
                put("agent_id", config.agentId)
                put("scope_type", config.scopeType.wireValue)
                put("scope_id", config.scopeId)
                put("alias", config.alias)
                put("enabled", config.enabled)
                put("schedule", config.schedule)
                put("goal", config.goal)
                put("surface_criteria", config.surfaceCriteria)
                put("max_runs_per_day", config.maxRunsPerDay)
                put("auto_surface_target_conversation_id", config.autoSurfaceTargetConversationId)
                put("self_config_allowed", config.selfConfigAllowed)
            },
        ).mapCatching { data ->
            // The host replies with the config id as a bare string; an
            // empty string means the write was refused.
            parseIdOrNull(data, "id")
                ?: throw RemoteProtocolException(
                    RemoteError("not_applied", "The host did not save the heartbeat"),
                )
        }

    suspend fun removeHeartbeatConfig(id: String): Result<Unit> =
        ack("heartbeat.remove", buildJsonObject { put("id", id) })

    suspend fun runHeartbeatNow(configId: String): Result<String> =
        requestJson(
            op = "heartbeat.run_now",
            params = buildJsonObject { put("id", configId) },
        ).mapCatching {
            // Bare string run id; empty when heartbeats are paused on the
            // desktop, the config is unknown, or the run queue is full.
            parseIdOrNull(it, "run_id")
                ?: throw RemoteProtocolException(
                    RemoteError(
                        "not_applied",
                        "The heartbeat did not start. Heartbeats may be paused on the desktop, or the run queue is full.",
                    ),
                )
        }

    suspend fun cancelHeartbeatRun(runId: String): Result<Unit> =
        ack("heartbeat.cancel_run", buildJsonObject { put("run_id", runId) })

    suspend fun listRecentHeartbeatRuns(limit: Int = 50): Result<List<HeartbeatRunUi>> =
        requestJson(
            op = "heartbeat.recent_runs",
            params = buildJsonObject { put("limit", limit) },
        ).mapCatching(::parseHeartbeatRuns)

    suspend fun listHeartbeatNextFires(limit: Int = 10): Result<List<HeartbeatNextFireUi>> =
        requestJson(
            op = "heartbeat.next_fires_preview",
            params = buildJsonObject { put("limit", limit) },
        ).mapCatching(::parseHeartbeatNextFires)

    suspend fun listRecentHeartbeatChanges(limit: Int = 100): Result<List<HeartbeatConfigChangeUi>> =
        requestJson(
            op = "heartbeat.recent_config_changes",
            params = buildJsonObject { put("limit", limit) },
        ).mapCatching(::parseHeartbeatChanges)

    suspend fun manualPostHeartbeatReport(reportId: String, targetConvId: String): Result<Unit> =
        ack(
            op = "heartbeat.manual_post",
            params = buildJsonObject {
                put("report_id", reportId)
                put("target_conv_id", targetConvId)
            },
        )

    suspend fun manualDismissHeartbeatReport(reportId: String): Result<Unit> =
        ack("heartbeat.manual_dismiss", buildJsonObject { put("report_id", reportId) })

    suspend fun heartbeatSuggestedCapForConv(conversationId: String): Result<Int> =
        requestJson(
            op = "heartbeat.suggested_cap_for_conv",
            params = buildJsonObject { put("conv_id", conversationId) },
        ).mapCatching { (it as? JsonObject)?.get("cap")?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 1 }

    suspend fun heartbeatSuggestedCapForFolder(folderId: String): Result<Int> =
        requestJson(
            op = "heartbeat.suggested_cap_for_folder",
            params = buildJsonObject { put("folder_id", folderId) },
        ).mapCatching { (it as? JsonObject)?.get("cap")?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 1 }


    /**
     * One-shot panel populator. Carries every per-conv field the desktop
     * RightSettingsPanel binds to PLUS the host-global flags (agentPattern,
     * requireConfirmation, toolsEnabled, ragEnabled) so the wire client
     * doesn't need parallel `agent.pattern` / `tools.enabled` / `rag.enabled`
     * round-trips for population.
     */
    suspend fun getConversationSettings(conversationId: String): Result<ConvSettingsUi> =
        requestJson(
            op = "conv.settings.get",
            params = buildJsonObject { put("conv_id", conversationId) },
        ).mapCatching(::parseConvSettings)

    /**
     * Read the conversation's retrieval (RAG) flag from its stored
     * `llm_config.rag_enabled`, via `conv.get`.
     *
     * `conv.settings.get` currently overwrites its `ragEnabled` field with
     * a read of a host property that no longer exists, so it always reports
     * false even after a successful save. `conv.get` returns the stored
     * config untouched.
     *
     * @returns the stored flag; false when the conversation has no stored
     *          config or no `rag_enabled` key (the host's own default).
     */
    suspend fun getConversationRagEnabled(conversationId: String): Result<Boolean> =
        requestJson(
            op = "conv.get",
            params = buildJsonObject { put("id", conversationId) },
        ).mapCatching { data ->
            val config = (data as? JsonObject)?.get("llm_config") as? JsonObject
            config?.boolOrNull("rag_enabled") ?: false
        }

    /**
     * Save a partial subset of editable per-conv fields. Pass only the
     * fields that changed — host writes whichever ones are present and
     * leaves the rest untouched. Returns immediately with `queued: true`;
     * the `conv.updated` event arrives once the write lands.
     *
     * Host validation bounds:
     *   - temperature ∈ [0.0, 2.0]
     *   - max_tokens ∈ [128, 128000]
     *   - context_window ∈ [1024, 131072]
     */
    suspend fun saveConversationSettings(
        conversationId: String,
        systemPrompt: String? = null,
        temperature: Double? = null,
        maxTokens: Int? = null,
        contextWindow: Int? = null,
        streaming: Boolean? = null,
        thinking: Boolean? = null,
        /**
         * Retrieval-augmented generation, scoped to THIS conversation. The host
         * keeps it on `llm_config.rag_enabled` and carries it here; the old
         * host-global `rag.enabled.set` op no longer exists.
         */
        ragEnabled: Boolean? = null,
        topK: Double? = null,
        topP: Double? = null,
        repeatPenalty: Double? = null,
        presencePenalty: Double? = null,
        frequencyPenalty: Double? = null,
        forceAppSampling: Boolean? = null,
        toolsInSystemPrompt: Boolean? = null,
        dynamicCompactEnabled: Boolean? = null,
        compactEveryTurns: Int? = null,
    ): Result<Unit> = ack(
        op = "conv.settings.save",
        params = buildJsonObject {
            put("conv_id", conversationId)
            if (systemPrompt != null) put("system_prompt", systemPrompt)
            if (temperature != null) put("temperature", temperature)
            if (maxTokens != null) put("max_tokens", maxTokens)
            if (contextWindow != null) put("context_window", contextWindow)
            if (streaming != null) put("streaming", streaming)
            if (thinking != null) put("thinking", thinking)
            if (ragEnabled != null) put("rag_enabled", ragEnabled)
            if (topK != null) put("top_k", topK)
            if (topP != null) put("top_p", topP)
            if (repeatPenalty != null) put("repeat_penalty", repeatPenalty)
            if (presencePenalty != null) put("presence_penalty", presencePenalty)
            if (frequencyPenalty != null) put("frequency_penalty", frequencyPenalty)
            if (forceAppSampling != null) put("force_app_sampling", forceAppSampling)
            if (toolsInSystemPrompt != null) put("tools_in_system_prompt", toolsInSystemPrompt)
            if (dynamicCompactEnabled != null) put("dynamic_compact_enabled", dynamicCompactEnabled)
            if (compactEveryTurns != null) put("compact_every_turns", compactEveryTurns)
        },
    )

    suspend fun getConversationPrimaryAgent(conversationId: String): Result<String> =
        requestJson(
            op = "conv.primary_agent",
            params = buildJsonObject { put("conv_id", conversationId) },
        ).mapCatching { (it as? JsonObject)?.string("agent_id").orEmpty() }

    /**
     * Set or clear the conversation's primary agent. Empty [agentId]
     * clears (legacy / no agent). 1:1 chats only — the desktop hides
     * this section for group chats.
     */
    suspend fun setConversationPrimaryAgent(conversationId: String, agentId: String): Result<Unit> =
        ack(
            op = "conv.primary_agent.set",
            params = buildJsonObject {
                put("conv_id", conversationId)
                put("agent_id", agentId)
            },
        )

    suspend fun getConversationHeartbeatGate(conversationId: String): Result<Pair<Boolean, Int>> =
        requestJson(
            op = "conv.heartbeat.gate",
            params = buildJsonObject { put("conv_id", conversationId) },
        ).mapCatching { element ->
            val obj = element as? JsonObject ?: return@mapCatching false to 1
            val allow = obj.boolFlag("allow")
            val cap = obj["max_per_day"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 1
            allow to cap
        }

    suspend fun setConversationHeartbeatGate(
        conversationId: String,
        allow: Boolean,
        maxPerDay: Int,
    ): Result<Unit> = ack(
        op = "conv.heartbeat.gate.set",
        params = buildJsonObject {
            put("conv_id", conversationId)
            put("allow", allow)
            put("max_per_day", maxPerDay)
        },
    )


    suspend fun getAgentPattern(conversationId: String): Result<AgentPattern> =
        requestJson(
            "agent.pattern",
            buildJsonObject { put("conv_id", conversationId) },
        ).mapCatching { AgentPattern.fromWire((it as? JsonObject)?.string("pattern")) }

    suspend fun setAgentPattern(conversationId: String, pattern: AgentPattern): Result<Unit> =
        ack("agent.pattern.set", buildJsonObject {
            put("conv_id", conversationId)
            put("pattern", pattern.wireValue)
        })

    suspend fun getRequireConfirmation(conversationId: String): Result<Boolean> =
        requestJson(
            "agent.require_confirmation",
            buildJsonObject { put("conv_id", conversationId) },
        ).mapCatching { (it as? JsonObject)?.boolFlag("require") ?: false }

    suspend fun setRequireConfirmation(conversationId: String, require: Boolean): Result<Unit> =
        ack("agent.require_confirmation.set", buildJsonObject {
            put("conv_id", conversationId)
            put("require", require)
        })

    suspend fun getToolsEnabled(conversationId: String): Result<Boolean> =
        requestJson(
            "tools.enabled",
            buildJsonObject { put("conv_id", conversationId) },
        ).mapCatching { (it as? JsonObject)?.boolFlag("enabled") ?: true }

    suspend fun setToolsEnabled(conversationId: String, enabled: Boolean): Result<Unit> =
        ack("tools.enabled.set", buildJsonObject {
            put("conv_id", conversationId)
            put("enabled", enabled)
        })


    suspend fun listPlansForConversation(conversationId: String): Result<List<PlanUi>> =
        requestJson(
            op = "plan.list_for_conv",
            params = buildJsonObject { put("conv_id", conversationId) },
        ).mapCatching(::parsePlans)

    suspend fun getPlan(id: String): Result<PlanUi> =
        requestJson(
            op = "plan.get",
            params = buildJsonObject { put("id", id) },
        ).mapCatching { element ->
            val obj = element as? JsonObject ?: throw RemoteProtocolException(
                RemoteError("missing_data", "plan.get response is not an object"),
            )
            parsePlanObject(obj) ?: throw RemoteProtocolException(
                RemoteError("missing_data", "plan.get response missing id"),
            )
        }

    /**
     * Fire-and-forget task start. Response is `{queued: true, conv_id}`;
     * the actual plan id arrives via the `plan.created` event. UI should
     * render a "Starting…" indicator until the event lands.
     */
    suspend fun startTask(conversationId: String, goal: String): Result<Unit> =
        ack(
            op = "task.start",
            params = buildJsonObject {
                put("conv_id", conversationId)
                put("goal", goal)
            },
        )

    suspend fun stopPlan(planId: String, reason: String = "Stopped by user"): Result<Unit> =
        ack(
            op = "plan.stop",
            params = buildJsonObject {
                put("plan_id", planId)
                put("reason", reason)
            },
        )

    suspend fun stopAllPlansInConversation(conversationId: String): Result<Int> =
        requestJson(
            op = "plan.stop_all_in_conv",
            params = buildJsonObject { put("conv_id", conversationId) },
        ).mapCatching {
            (it as? JsonObject)?.get("stopped_count")?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
        }

    suspend fun retryStep(stepId: String, notes: String = ""): Result<Unit> =
        ack(
            op = "step.retry",
            params = buildJsonObject {
                put("step_id", stepId)
                if (notes.isNotEmpty()) put("notes", notes)
            },
        )

    suspend fun overrideStepDone(stepId: String): Result<Unit> =
        ack("step.override_done", buildJsonObject { put("step_id", stepId) })

    suspend fun skipStep(stepId: String, reason: String = "Skipped by user"): Result<Unit> =
        ack(
            op = "step.skip",
            params = buildJsonObject {
                put("step_id", stepId)
                put("reason", reason)
            },
        )


    suspend fun listToolCallsForConv(conversationId: String): Result<List<ToolCallUi>> =
        requestJson(
            op = "tool_call.list_for_conv",
            params = buildJsonObject { put("conv_id", conversationId) },
        ).mapCatching(::parseToolCalls)

    suspend fun listToolCallsForMessage(messageId: String): Result<List<ToolCallUi>> =
        requestJson(
            op = "tool_call.list_for_message",
            params = buildJsonObject { put("message_id", messageId) },
        ).mapCatching(::parseToolCalls)

    suspend fun listToolCallsForTurn(messageId: String, agentId: String = ""): Result<List<ToolCallUi>> =
        requestJson(
            op = "tool_call.list_for_turn",
            params = buildJsonObject {
                put("message_id", messageId)
                if (agentId.isNotBlank()) put("agent_id", agentId)
            },
        ).mapCatching(::parseToolCalls)

    suspend fun getToolCall(id: String): Result<ToolCallUi> =
        requestJson(
            op = "tool_call.get",
            params = buildJsonObject { put("id", id) },
        ).mapCatching {
            (it as? JsonObject)?.let(::parseToolCallObject)
                ?: throw RemoteProtocolException(RemoteError("not_found", "tool call missing"))
        }

    suspend fun approveToolCall(callId: String): Result<Unit> =
        ack("tool_call.approve", buildJsonObject { put("call_id", callId) })

    suspend fun denyToolCall(callId: String): Result<Unit> =
        ack("tool_call.deny", buildJsonObject { put("call_id", callId) })


    suspend fun listAttachmentsForMessage(messageId: String): Result<List<AttachmentMetaUi>> =
        requestJson(
            op = "attachment.list_for_message",
            params = buildJsonObject { put("message_id", messageId) },
        ).mapCatching(::parseAttachments)

    suspend fun getAttachment(id: String, maxBytes: Int = 0): Result<AttachmentMetaUi> =
        requestJson(
            op = "attachment.get",
            params = buildJsonObject {
                put("id", id)
                if (maxBytes > 0) put("max_bytes", maxBytes)
            },
        ).mapCatching {
            (it as? JsonObject)?.let(::parseAttachmentObject)
                ?: throw RemoteProtocolException(RemoteError("not_found", "attachment missing"))
        }

    suspend fun listArtifactsForConv(conversationId: String): Result<List<ArtifactRowUi>> =
        requestJson(
            op = "artifact.list_for_conv",
            params = buildJsonObject { put("conv_id", conversationId) },
        ).mapCatching(::parseArtifacts)

    suspend fun downloadArtifact(
        conversationId: String,
        fileName: String,
        maxBytes: Int = 0,
    ): Result<DownloadPayloadUi> = requestJson(
        op = "artifact.download",
        params = buildJsonObject {
            put("conv_id", conversationId)
            put("file_name", fileName)
            if (maxBytes > 0) put("max_bytes", maxBytes)
        },
    ).mapCatching(::parseDownloadPayload)

    suspend fun listImagesForConv(conversationId: String): Result<List<GeneratedFileUi>> =
        requestJson(
            op = "image.list_for_conv",
            params = buildJsonObject { put("conv_id", conversationId) },
        ).mapCatching(::parseGeneratedFiles)

    suspend fun downloadImage(
        conversationId: String,
        fileName: String,
        maxBytes: Int = 0,
    ): Result<DownloadPayloadUi> = requestJson(
        op = "image.download",
        params = buildJsonObject {
            put("conv_id", conversationId)
            put("file_name", fileName)
            if (maxBytes > 0) put("max_bytes", maxBytes)
        },
    ).mapCatching(::parseDownloadPayload)

    suspend fun listAudioForConv(conversationId: String): Result<List<GeneratedFileUi>> =
        requestJson(
            op = "audio.list_for_conv",
            params = buildJsonObject { put("conv_id", conversationId) },
        ).mapCatching(::parseGeneratedFiles)

    suspend fun downloadAudio(
        conversationId: String,
        fileName: String,
        maxBytes: Int = 0,
    ): Result<DownloadPayloadUi> = requestJson(
        op = "audio.download",
        params = buildJsonObject {
            put("conv_id", conversationId)
            put("file_name", fileName)
            if (maxBytes > 0) put("max_bytes", maxBytes)
        },
    ).mapCatching(::parseDownloadPayload)


    suspend fun getActiveCanvas(conversationId: String): Result<CanvasUi?> =
        requestJson(
            op = "canvas.active_for_conv",
            params = buildJsonObject { put("conv_id", conversationId) },
        ).mapCatching { element ->
            // Empty object {} means "no active canvas in this conversation".
            // Distinguished from a populated object by the presence of `id`.
            (element as? JsonObject)?.takeIf { it.string("id") != null }?.let(::parseCanvasObject)
        }

    suspend fun getCanvasHistory(conversationId: String): Result<List<CanvasUi>> =
        requestJson(
            op = "canvas.history_for_conv",
            params = buildJsonObject { put("conv_id", conversationId) },
        ).mapCatching(::parseCanvases)

    suspend fun readCanvasSlice(
        conversationId: String,
        startLine: Int,
        endLine: Int,
    ): Result<String> = requestJson(
        op = "canvas.read_slice",
        params = buildJsonObject {
            put("conv_id", conversationId)
            put("start_line", startLine)
            put("end_line", endLine)
        },
    ).mapCatching { (it as? JsonObject)?.string("content").orEmpty() }

    suspend fun getCanvasDiskMirrorPath(conversationId: String): Result<String> =
        requestJson(
            op = "canvas.disk_mirror_path",
            params = buildJsonObject { put("conv_id", conversationId) },
        ).mapCatching { (it as? JsonObject)?.string("path").orEmpty() }

    suspend fun getCanvasSuggestedExportName(conversationId: String): Result<String> =
        requestJson(
            op = "canvas.suggested_export_name",
            params = buildJsonObject { put("conv_id", conversationId) },
        ).mapCatching { (it as? JsonObject)?.string("file_name").orEmpty() }

    suspend fun listCanvasTools(language: String): Result<List<CanvasToolUi>> =
        requestJson(
            op = "canvas.tools.list",
            params = buildJsonObject { put("language", language) },
        ).mapCatching(::parseCanvasTools)

    suspend fun listCanvasAiActions(language: String): Result<List<CanvasAiActionUi>> =
        requestJson(
            op = "canvas.ai_actions.list",
            params = buildJsonObject { put("language", language) },
        ).mapCatching(::parseCanvasAiActions)

    suspend fun listCanvasRunSupportedLanguages(): Result<List<String>> =
        requestJson("canvas.run.supported_languages", JsonObject(emptyMap()))
            .mapCatching { element ->
                (element as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
            }

    suspend fun listCanvasRunSupportedIdeLanguages(): Result<List<String>> =
        requestJson("canvas.run.supported_ide_languages", JsonObject(emptyMap()))
            .mapCatching { element ->
                (element as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
            }

    suspend fun getCanvasRunSandboxState(): Result<CanvasSandboxStateUi> =
        requestJson("canvas.run.sandbox_state", JsonObject(emptyMap()))
            .mapCatching {
                val obj = it as? JsonObject ?: return@mapCatching CanvasSandboxStateUi()
                CanvasSandboxStateUi(
                    available = obj.boolFlag("available"),
                    blockerReason = obj.string("blocker_reason").orEmpty(),
                )
            }

    /** Whether a canvas run is in progress on the host. The reply is a bare boolean. */
    suspend fun getCanvasRunIsRunning(): Result<Boolean> =
        requestJson("canvas.run.is_running", JsonObject(emptyMap()))
            .mapCatching { parseBoolReply(it, "running") }

    suspend fun openCanvas(
        conversationId: String,
        filename: String,
        language: String,
        content: String,
        sourceMsgId: String = "",
    ): Result<String> = requestJson(
        op = "canvas.open",
        params = buildJsonObject {
            put("conv_id", conversationId)
            put("filename", filename)
            put("language", language)
            put("content", content)
            if (sourceMsgId.isNotBlank()) put("source_msg_id", sourceMsgId)
        },
    ).mapCatching {
        parseIdOrNull(it, "canvas_id", "id")
            ?: throw RemoteProtocolException(RemoteError("missing_id", "canvas.open response missing canvas_id"))
    }

    suspend fun editCanvas(conversationId: String, content: String): Result<Unit> =
        ack(
            op = "canvas.edit",
            params = buildJsonObject {
                put("conv_id", conversationId)
                put("content", content)
            },
        )

    suspend fun closeCanvas(conversationId: String): Result<Unit> =
        ack("canvas.close", buildJsonObject { put("conv_id", conversationId) })

    suspend fun switchToCanvas(conversationId: String, canvasId: String): Result<Unit> =
        ack(
            op = "canvas.switch_to",
            params = buildJsonObject {
                put("conv_id", conversationId)
                put("canvas_id", canvasId)
            },
        )

    suspend fun runCanvasTool(conversationId: String, actionId: String): Result<CanvasToolResultUi> =
        requestJson(
            op = "canvas.tools.run",
            params = buildJsonObject {
                put("conv_id", conversationId)
                put("action_id", actionId)
            },
        ).mapCatching(::parseCanvasToolResult)

    /**
     * Runs a canvas AI action. The host first switches this client's own
     * session to [conversationId], then reads that conversation's active
     * canvas and posts the composed prompt into it. A host that does not
     * read `conv_id` runs the action on the conversation this client last
     * had open on it.
     *
     * @param conversationId the conversation whose canvas the action acts
     *        on; omitted from the request when blank.
     * @param actionId the action id from `canvas.ai_actions.list`.
     * @param submenuChoice the chosen submenu entry, or blank for none.
     */
    suspend fun triggerCanvasAiAction(
        conversationId: String,
        actionId: String,
        submenuChoice: String = "",
    ): Result<Unit> =
        ack(
            op = "canvas.ai_actions.trigger",
            params = canvasAiActionTriggerParams(conversationId, actionId, submenuChoice),
        )

    /** Builds the `canvas.ai_actions.trigger` params; see [triggerCanvasAiAction]. */
    internal fun canvasAiActionTriggerParams(
        conversationId: String,
        actionId: String,
        submenuChoice: String,
    ): JsonObject = buildJsonObject {
        put("action_id", actionId)
        if (submenuChoice.isNotBlank()) put("submenu_choice", submenuChoice)
        if (conversationId.isNotBlank()) put("conv_id", conversationId)
    }

    suspend fun startCanvasRun(conversationId: String): Result<Boolean> =
        requestJson(
            op = "canvas.run.start",
            params = buildJsonObject { put("conv_id", conversationId) },
        ).mapCatching { parseBoolReply(it, "dispatched") }

    suspend fun cancelCanvasRun(): Result<Unit> =
        ack("canvas.run.cancel", JsonObject(emptyMap()))

    /**
     * Answers an `input()` prompt in the running canvas script.
     *
     * The host appends the newline, echoes the line back as a console row, and
     * restarts the run's inactivity window. An empty [text] sends a blank line,
     * which is what a script sees when you press Enter at a real prompt. The
     * host rejects a body over 8 KiB with `payload_too_large`.
     */
    suspend fun sendCanvasInput(text: String): Result<Unit> =
        ack("canvas.run.send_input", buildJsonObject { put("text", text) })

    /** Closes the script's stdin, so a script reading until EOF sees it. */
    suspend fun sendCanvasEof(): Result<Unit> =
        ack("canvas.run.eof", JsonObject(emptyMap()))

    suspend fun sendCanvasToIde(conversationId: String): Result<Boolean> =
        requestJson(
            op = "canvas.run.send_to_ide",
            params = buildJsonObject { put("conv_id", conversationId) },
        ).mapCatching { parseBoolReply(it, "ok") }

    suspend fun clearCanvasConsole(): Result<Unit> =
        ack("canvas.console.clear", JsonObject(emptyMap()))

    /**
     * Send a message with one-or-more inline attachments. Caller has
     * already enforced size + count caps:
     *   - max 8 attachments
     *   - per-attachment raw bytes ≤ 4 MiB
     *   - total raw bytes ≤ [MAX_UPLOAD_RAW_BYTES], so the base64 frame
     *     stays inside OkHttp's 16 MiB outgoing WebSocket queue (a larger
     *     frame closes the socket)
     * The host re-validates each attachment and answers `invalid_params`
     * if one cannot be stored.
     *
     * `text` MAY be empty when there's at least one attachment (image-only
     * messages send the user's photo to a vision model).
     *
     * Each attachment is sent with `filename`, the key the host reads;
     * `file_name` is kept alongside for older hosts.
     */
    suspend fun sendMessageWithAttachments(
        conversationId: String,
        text: String,
        attachments: List<OutgoingAttachment>,
    ): Result<Unit> = ack(
        op = "msg.send_with_attachments",
        params = buildJsonObject {
            put("conv_id", conversationId)
            put("text", text)
            put(
                "attachments",
                JsonArray(
                    attachments.map { a ->
                        buildJsonObject {
                            put("filename", a.fileName)
                            put("file_name", a.fileName)
                            put("mime_type", a.mimeType)
                            put("content_base64", a.contentBase64)
                        }
                    },
                ),
            )
        },
    )

    private fun membersToJsonArray(members: List<FolderMemberUi>): JsonArray =
        JsonArray(members.map { JsonObject(memberBaseFields(it)) })

    /**
     * Builds the `members` array for `folder.members.set`: the base fields
     * plus each member's override and provenance fields when it has them.
     * Blank values are left out so the host applies its own defaults.
     * `added_by_kind` is sent only when it is `user` or `agent`, since the
     * host rejects the whole call for any other value, and blank tool
     * names are dropped for the same reason.
     */
    internal fun folderMembersToJsonArray(members: List<FolderMemberUi>): JsonArray =
        JsonArray(
            members.map { m ->
                val fields = memberBaseFields(m)
                if (m.modelProvider.isNotBlank()) fields["model_provider"] = JsonPrimitive(m.modelProvider)
                if (m.modelName.isNotBlank()) fields["model_name"] = JsonPrimitive(m.modelName)
                val tools = m.allowedTools.filter { it.isNotBlank() }
                if (tools.isNotEmpty()) fields["allowed_tools"] = JsonArray(tools.map { JsonPrimitive(it) })
                if (m.addedByKind == "user" || m.addedByKind == "agent") {
                    fields["added_by_kind"] = JsonPrimitive(m.addedByKind)
                }
                if (m.addedByAgentId.isNotBlank()) fields["added_by_agent_id"] = JsonPrimitive(m.addedByAgentId)
                JsonObject(fields)
            },
        )

    /** Agent, alias and coordinator: the fields every member write carries. */
    private fun memberBaseFields(m: FolderMemberUi): MutableMap<String, JsonElement> =
        linkedMapOf(
            "agent_id" to JsonPrimitive(m.agentId),
            "alias" to JsonPrimitive(m.alias),
            "is_coordinator" to JsonPrimitive(m.isCoordinator),
        )

    // --- Agents -------------------------------------------------------------

    /**
     * Full agent registry from the host. Used by the folder editor's
     * agent picker and the (future) group-chat member picker. Cache
     * per session — the host has no agent.* event yet so a manual
     * refresh on screen open is acceptable.
     */
    suspend fun listAgents(): Result<List<AgentSummaryUi>> =
        requestJson("agent.list", JsonObject(emptyMap())).mapCatching(::parseAgentsList)


    /**
     * Full tool catalog. Includes built-ins, user-defined customs, and
     * tools advertised by every connected MCP server. Each row carries
     * `enabled`, which the UI surfaces as a static state — there's no
     * `tool.set_enabled` op for remote clients.
     */
    suspend fun listTools(): Result<List<ToolUi>> =
        requestJson("tool.list", JsonObject(emptyMap())).mapCatching(::parseTools)

    /** Configured MCP servers with their live connection state. */
    suspend fun listMcpServers(): Result<List<McpServerUi>> =
        requestJson("mcp.list", JsonObject(emptyMap())).mapCatching(::parseMcpServers)

    /**
     * Tools advertised by a specific MCP server. Returns an empty list when
     * the server isn't found or hasn't completed its handshake yet (the host
     * silently returns `[]` rather than erroring — see mcp-service.cpp:241).
     */
    suspend fun listMcpServerTools(serverName: String): Result<List<McpToolUi>> =
        requestJson(
            op = "mcp.server_tools",
            params = buildJsonObject { put("server_name", serverName) },
        ).mapCatching(::parseMcpTools)

    /** Installed skills metadata (one entry per skill under skills/installed/). */
    suspend fun listSkills(): Result<List<SkillUi>> =
        requestJson("skill.list", JsonObject(emptyMap())).mapCatching(::parseSkills)

    /**
     * Full metadata for a single skill. Same wire shape as a `skill.list`
     * row — we use the same parser. The id parameter is the skill slug,
     * NOT a UUID; host validates only that it's non-empty + bounded.
     */
    suspend fun getSkill(id: String): Result<SkillUi> =
        requestJson(
            op = "skill.get",
            params = buildJsonObject { put("id", id) },
        ).mapCatching(::parseSkill)

    // --- Group chat creation ------------------------------------------------

    /**
     * Create a group chat with [members] as the initial member set. Pass
     * a non-blank [folderId] to attach it to a project/organization
     * folder, or null/blank to create a standalone group chat.
     *
     * Members are sent with the snake_case keys the wire protocol uses
     * (`agent_id` / `alias` / `is_coordinator`).
     */
    suspend fun createGroup(
        title: String,
        members: List<GroupMemberInput>,
        folderId: String? = null,
    ): Result<String> = requestJson(
        op = "group.create",
        params = buildJsonObject {
            put("title", title)
            put(
                "members",
                JsonArray(members.map { member ->
                    buildJsonObject {
                        put("agent_id", member.agentId)
                        put("alias", member.alias)
                        put("is_coordinator", member.isCoordinator)
                    }
                }),
            )
            if (!folderId.isNullOrBlank()) put("folder_id", folderId)
        },
    ).mapCatching(::parseId)

    // --- Model catalog ------------------------------------------------------

    /**
     * Fetch the host's full provider/model catalog plus the currently
     * active selection. Stable per session; call once after auth and
     * cache. Re-fetch on reconnect.
     */
    suspend fun getModelCatalog(): Result<ModelCatalogUi> =
        requestJson("models.catalog", JsonObject(emptyMap())).mapCatching(::parseModelCatalog)

    /**
     * Fire-and-forget: change the host's active provider+model. Host
     * responds `{queued: true}` and emits `models.active_changed` once
     * the change is applied. Caller can update UI optimistically; the
     * event is the trigger for any downstream action that depends on
     * the new selection being live (e.g. `conv.create`).
     */
    suspend fun setActiveModel(providerId: String, modelName: String): Result<Unit> = ack(
        op = "models.set_active",
        params = buildJsonObject {
            put("provider_id", providerId)
            put("model_name", modelName)
        },
    )


    /**
     * Fetch the host's web-search provider catalogue. Host op
     * `search.providers` returns the provider list as a JSON array.
     */
    suspend fun getSearchProviders(): Result<SearchProvidersUi> =
        requestJson("search.providers", JsonObject(emptyMap()))
            .mapCatching(::parseSearchProviders)

    /**
     * Switch the host's active web-search provider. Fire-and-forget. API
     * keys are host-only and never sent over the wire — an unconfigured
     * provider set active simply falls back to DuckDuckGo on the host.
     */
    suspend fun setActiveSearchProvider(providerId: String): Result<Unit> = ack(
        op = "search.set_active",
        params = buildJsonObject { put("provider_id", providerId) },
    )

    // --- Paired clients -----------------------------------------------------

    suspend fun listClients(): Result<List<ClientUi>> =
        requestJson("clients.list", JsonObject(emptyMap())).mapCatching(::parseClients)

    suspend fun revokeClient(clientId: String): Result<Unit> =
        ack("clients.revoke", buildJsonObject { put("client_id", clientId) })

    // --- Internals ----------------------------------------------------------

    private suspend fun <T> request(
        op: String,
        params: JsonObject,
        serializer: KSerializer<T>,
    ): Result<T> =
        session.send(op, params).mapCatching { response ->
            if (!response.ok) throw RemoteProtocolException(response.error)
            val data = response.data ?: throw RemoteProtocolException(
                RemoteError("missing_data", "Host response did not include data"),
            )
            RemoteJson.decodeFromJsonElement(serializer, data)
        }

    private suspend fun requestJson(op: String, params: JsonObject): Result<JsonElement> =
        session.send(op, params).mapCatching { response ->
            if (!response.ok) throw RemoteProtocolException(response.error)
            response.data ?: JsonObject(emptyMap())
        }

    /**
     * Send a write op whose reply carries no data the caller needs.
     *
     * Many host write ops answer `ok: true` with a bare boolean as data,
     * where `false` means the host refused or rolled back the change (for
     * example a duplicate alias in `folder.members.set`). That case is
     * returned as a failure so the UI does not report success.
     */
    private suspend fun ack(op: String, params: JsonObject): Result<Unit> =
        session.send(op, params).mapCatching { response ->
            if (!response.ok) throw RemoteProtocolException(response.error)
            if (isRefusal(response.data)) {
                throw RemoteProtocolException(
                    RemoteError("not_applied", "The host did not apply the change"),
                )
            }
        }

    // --- JSON parsing -------------------------------------------------------
    //
    // The keys here mirror exactly what the host's `remote-ws-serialise.cpp`
    // emits — `id`, `title`, `folder_id`, `is_group`, `is_pinned`,
    // `primary_agent_id`, `group_agent_ids`, `created_at`, `updated_at`,
    // `token_total`, `system_prompt`. Do NOT introduce key-aliasing
    // fallbacks here unless the host actually emits the alias; otherwise
    // we silently mask protocol drift.

    private fun parseConversations(data: JsonElement): List<ConversationUi> =
        data.listPayload("conversations", "items", "results", "data").mapNotNull(::parseConversationOrNull)

    private fun parseConversation(data: JsonElement): ConversationUi =
        parseConversationOrNull(data)
            ?: (data as? JsonObject)?.firstObject("conversation", "item", "data")?.let(::parseConversationOrNull)
            ?: throw RemoteProtocolException(RemoteError("missing_data", "Host response did not include a conversation"))

    private fun parseConversationOrNull(data: JsonElement): ConversationUi? {
        val obj = data as? JsonObject ?: return null
        val id = obj.string("id") ?: return null
        return ConversationUi(
            id = id,
            title = obj.string("title").orEmpty().ifBlank { "Untitled" },
            folderId = obj.string("folder_id"),
            primaryAgentId = obj.string("primary_agent_id"),
            isGroup = obj.intFlag("is_group"),
            isPinned = obj.intFlag("is_pinned"),
            groupAgentIds = (obj["group_agent_ids"] as? JsonArray)
                ?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList(),
            // `conv.list` sends ISO strings; `conv.get` sends epoch ms.
            createdAt = normaliseTimestamp(obj.string("created_at").orEmpty()),
            updatedAt = normaliseTimestamp(obj.string("updated_at").orEmpty()),
            tokenTotal = obj["token_total"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L,
            systemPrompt = obj.string("system_prompt").orEmpty(),
        )
    }

    private fun parseMessages(data: JsonElement): List<MessageUi> =
        data.listPayload("messages", "items", "results", "data").mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            val id = obj.string("id") ?: return@mapNotNull null
            val metadata = obj["metadata"] as? JsonObject
            val pollId = metadata?.string("poll_id")?.takeIf { it.isNotEmpty() }
            MessageUi(
                id = id,
                role = obj.string("role").toMessageRole(),
                text = obj.string("content").orEmpty(),
                createdAt = obj.string("created_at").orEmpty(),
                tokenCount = obj["token_count"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L,
                modelUsed = obj.string("model_used").orEmpty(),
                finishReason = obj.string("finish_reason").orEmpty(),
                agentId = obj.string("agent_id"),
                memberAlias = obj.string("member_alias"),
                turnId = obj.string("turn_id"),
                pollId = pollId,
                thinkingContent = obj.string("thinking_content").orEmpty(),
            )
        }

    private fun parseSendMessageResult(data: JsonElement): SendMessageResult {
        val obj = data as? JsonObject ?: return SendMessageResult(null, null)
        return SendMessageResult(
            userMessageId = obj.string("user_msg_id"),
            assistantPlaceholderId = obj.string("placeholder_assistant_msg_id"),
        )
    }

    /**
     * Parse a `{id: "..."}` response. Used by all create-style ops (
     * `conv.create`, `conv.create_with_agent`, `folder.create`,
     * `group.create`).
     */
    private fun parseId(data: JsonElement): String =
        parseIdOf(data, "id")

    /**
     * Parse an id reply that may arrive either as a bare string or wrapped
     * in an object under one of [keys].
     *
     * @throws RemoteProtocolException when no non-blank id is present.
     */
    private fun parseIdOf(data: JsonElement, vararg keys: String): String =
        parseIdOrNull(data, *keys)
            ?: throw RemoteProtocolException(
                RemoteError("missing_id", "Host response did not include `${keys.first()}`"),
            )

    fun parseFolderFromObject(obj: JsonObject): FolderUi {
        val id = obj.string("id") ?: error("folder missing id")
        val agentIds = (obj["agentIds"] as? JsonArray ?: obj["agent_ids"] as? JsonArray)
            ?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty()
        return FolderUi(
            id = id,
            name = obj.string("name").orEmpty().ifBlank { "Untitled" },
            parentId = obj.string("parentId") ?: obj.string("parent_id"),
            folderType = FolderType.fromWire(obj.string("folderType") ?: obj.string("folder_type") ?: obj.string("type")),
            goal = obj.string("goal").orEmpty(),
            description = obj.string("description").orEmpty(),
            agentIds = agentIds,
        )
    }

    private fun parseFoldersFromList(data: JsonElement): List<FolderUi> =
        data.listPayload("folders", "items", "results", "data").mapNotNull {
            val obj = it as? JsonObject ?: return@mapNotNull null
            runCatching { parseFolderFromObject(obj) }.getOrNull()
        }

    private fun parseFolderCreateResult(data: JsonElement): FolderCreateResult {
        val obj = data as? JsonObject
            ?: throw RemoteProtocolException(RemoteError("missing_data", "folder.create response is not an object"))
        val id = obj.string("id")
            ?: throw RemoteProtocolException(RemoteError("missing_id", "folder.create response missing id"))
        return FolderCreateResult(
            id = id,
            type = FolderType.fromWire(obj.string("type")),
        )
    }

    private fun parseTools(data: JsonElement): List<ToolUi> =
        data.listPayload("tools", "items", "results", "data").mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            val name = obj.string("name") ?: return@mapNotNull null
            ToolUi(
                name = name,
                description = obj.string("description").orEmpty(),
                enabled = obj.boolFlag("enabled"),
                isBuiltIn = obj.boolFlag("isBuiltIn") || obj.boolFlag("is_built_in"),
                kind = ToolKind.fromWire(obj.string("kind")),
                mcpServer = obj.string("mcpServer"),
                shortName = obj.string("shortName"),
                parameters = (obj["parameters"] as? JsonArray)?.mapNotNull { p ->
                    val po = p as? JsonObject ?: return@mapNotNull null
                    val pn = po.string("name") ?: return@mapNotNull null
                    ToolParameterUi(
                        name = pn,
                        type = po.string("type").orEmpty(),
                        description = po.string("description").orEmpty(),
                        required = po.boolFlag("required"),
                    )
                }.orEmpty(),
            )
        }

    private fun parseMcpServers(data: JsonElement): List<McpServerUi> =
        data.listPayload("servers", "items", "results", "data").mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            val name = obj.string("name") ?: return@mapNotNull null
            McpServerUi(
                name = name,
                type = obj.string("type").orEmpty().ifBlank { "stdio" },
                command = obj.string("command").orEmpty(),
                args = obj.string("args").orEmpty(),
                url = obj.string("url").orEmpty(),
                disabled = obj.boolFlag("disabled"),
                status = McpStatus.fromWire(obj.string("status")),
                errorMessage = obj.string("error").orEmpty(),
                toolCount = obj["toolCount"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0,
            )
        }

    private fun parseMcpTools(data: JsonElement): List<McpToolUi> =
        data.listPayload("tools", "items", "results", "data").mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            val name = obj.string("name") ?: return@mapNotNull null
            McpToolUi(
                name = name,
                description = obj.string("description").orEmpty(),
                server = obj.string("server").orEmpty(),
            )
        }

    private fun parseSkills(data: JsonElement): List<SkillUi> =
        data.listPayload("skills", "items", "results", "data").mapNotNull { item ->
            (item as? JsonObject)?.let(::parseSkillObject)
        }

    private fun parseSkill(data: JsonElement): SkillUi =
        (data as? JsonObject)?.let(::parseSkillObject)
            ?: throw RemoteProtocolException(RemoteError("missing_data", "skill.get response is not an object"))

    private fun parseSkillObject(obj: JsonObject): SkillUi? {
        val id = obj.string("id") ?: return null
        return SkillUi(
            id = id,
            source = obj.string("source").orEmpty(),
            sourceUrl = obj.string("sourceUrl").orEmpty(),
            version = obj.string("version").orEmpty(),
            description = obj.string("description").orEmpty(),
            tags = (obj["tags"] as? JsonArray)
                ?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty(),
            declaredTools = (obj["declaredTools"] as? JsonArray)
                ?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty(),
            contentHashShort = obj.string("contentHashShort").orEmpty(),
            contentHashSha256 = obj.string("contentHashSha256").orEmpty(),
            installedAtMs = obj["installedAtMs"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L,
            updatedAtMs = obj["updatedAtMs"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L,
            installPath = obj.string("installPath").orEmpty(),
            reviewState = obj.string("reviewState").orEmpty().ifBlank { "unreviewed" },
            warnings = (obj["warnings"] as? JsonArray)?.mapNotNull { w ->
                val wo = w as? JsonObject ?: return@mapNotNull null
                SkillWarningUi(
                    regexName = wo.string("regexName").orEmpty(),
                    fileRelativePath = wo.string("fileRelativePath").orEmpty(),
                    lineNumber = wo["lineNumber"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0,
                    matchedExcerpt = wo.string("matchedExcerpt").orEmpty(),
                )
            }.orEmpty(),
        )
    }

    /**
     * Parse a `folder.members` / `conv.members` response, or the
     * `members` array inside a `folder.members.changed` /
     * `conv.members.changed` event. Wire keys are camelCase
     * (`agentId`, `isCoordinator`) — the host's `MembershipService::memberToMap`
     * doesn't normalise on the way out. We accept both forms defensively
     * in case the host adds normalisation later.
     */
    fun parseMembersFromArray(arr: JsonArray): List<FolderMemberUi> =
        arr.mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            val agentId = obj.string("agentId") ?: obj.string("agent_id") ?: return@mapNotNull null
            FolderMemberUi(
                agentId = agentId,
                alias = obj.string("alias").orEmpty(),
                isCoordinator = obj.boolFlag("isCoordinator") || obj.boolFlag("is_coordinator"),
                modelProvider = obj.anyString("modelProvider", "model_provider").orEmpty(),
                modelName = obj.anyString("modelName", "model_name").orEmpty(),
                allowedTools = ((obj["allowedTools"] ?: obj["allowed_tools"]) as? JsonArray)
                    ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                    .orEmpty(),
                addedByKind = obj.anyString("addedByKind", "added_by_kind").orEmpty(),
                addedByAgentId = obj.anyString("addedByAgentId", "added_by_agent_id").orEmpty(),
            )
        }

    private fun parseMembers(data: JsonElement): List<FolderMemberUi> =
        when (data) {
            is JsonArray -> parseMembersFromArray(data)
            is JsonObject -> (data["members"] as? JsonArray)?.let(::parseMembersFromArray).orEmpty()
            else -> emptyList()
        }

    fun parseCanvasObject(obj: JsonObject): CanvasUi? {
        val id = obj.string("id") ?: return null
        return CanvasUi(
            id = id,
            conversationId = obj.string("conversationId").orEmpty(),
            filename = obj.string("filename").orEmpty(),
            language = obj.string("language").orEmpty(),
            content = obj.string("content").orEmpty()
                .ifEmpty { obj["content"]?.jsonPrimitive?.contentOrNull.orEmpty() },
            revision = obj["revision"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0,
            isArchived = obj.boolFlag("isArchived"),
            sourceMsgId = obj.string("sourceMsgId").orEmpty(),
            createdAt = obj.string("createdAt").orEmpty(),
            updatedAt = obj.string("updatedAt").orEmpty(),
            byteSize = obj["byteSize"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L,
            lineCount = obj["lineCount"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0,
        )
    }

    private fun parseCanvases(data: JsonElement): List<CanvasUi> =
        data.listPayload("canvases", "items", "results", "data").mapNotNull {
            (it as? JsonObject)?.let(::parseCanvasObject)
        }

    private fun parseCanvasTools(data: JsonElement): List<CanvasToolUi> =
        data.listPayload("tools", "items", "results", "data").mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            val id = obj.string("id") ?: return@mapNotNull null
            CanvasToolUi(
                id = id,
                label = obj.string("label").orEmpty(),
                description = obj.string("description").orEmpty(),
                enabled = obj.boolFlag("enabled"),
                language = obj.string("language").orEmpty(),
            )
        }

    private fun parseCanvasAiActions(data: JsonElement): List<CanvasAiActionUi> =
        data.listPayload("actions", "items", "results", "data").mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            val id = obj.string("id") ?: return@mapNotNull null
            CanvasAiActionUi(
                id = id,
                label = obj.string("label").orEmpty(),
                iconName = obj.string("iconName").orEmpty(),
                primary = obj.boolFlag("primary"),
                submenu = (obj["submenu"] as? JsonArray)
                    ?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList(),
                description = obj.string("description").orEmpty(),
            )
        }

    private fun parseCanvasToolResult(data: JsonElement): CanvasToolResultUi {
        val obj = data as? JsonObject ?: return CanvasToolResultUi(ok = false)
        return CanvasToolResultUi(
            ok = obj.boolFlag("ok"),
            message = obj.string("message").orEmpty(),
            mutated = obj.boolFlag("mutated"),
            errorLine = obj["errorLine"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: -1,
            errorOffset = obj["errorOffset"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: -1,
        )
    }

    private fun parseAttachments(data: JsonElement): List<AttachmentMetaUi> =
        data.listPayload("attachments", "items", "results", "data").mapNotNull {
            (it as? JsonObject)?.let(::parseAttachmentObject)
        }

    private fun parseAttachmentObject(obj: JsonObject): AttachmentMetaUi? {
        val id = obj.string("id") ?: return null
        return AttachmentMetaUi(
            id = id,
            messageId = obj.string("message_id").orEmpty(),
            type = AttachmentType.fromWire(obj.string("type")),
            fileName = obj.string("file_name").orEmpty(),
            mimeType = obj.string("mime_type").orEmpty(),
            hasInlineData = obj.boolFlag("has_inline_data"),
            hasPath = obj.boolFlag("has_path"),
            totalBytes = obj["total_bytes"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: -1L,
            createdAt = obj.string("created_at").orEmpty(),
        )
    }

    /**
     * Parse an artifact-row object — used by both `artifact.list_for_conv`
     * (array) and `artifact.added` events (single object embedded with
     * a `conv_id` key alongside).
     */
    fun parseArtifactObject(obj: JsonObject): ArtifactRowUi? {
        val fileName = obj.string("file_name") ?: return null
        return ArtifactRowUi(
            sourceMsgId = obj.string("source_msg_id").orEmpty(),
            path = obj.string("path").orEmpty(),
            fileName = fileName,
            toolName = obj.string("tool_name").orEmpty(),
            planId = obj.string("plan_id").orEmpty(),
            stepId = obj.string("step_id").orEmpty(),
            stepTitle = obj.string("step_title").orEmpty(),
            planGoal = obj.string("plan_goal").orEmpty(),
            submittedBy = obj.string("submitted_by").orEmpty(),
        )
    }

    private fun parseArtifacts(data: JsonElement): List<ArtifactRowUi> =
        data.listPayload("artifacts", "items", "results", "data").mapNotNull {
            (it as? JsonObject)?.let(::parseArtifactObject)
        }

    private fun parseGeneratedFiles(data: JsonElement): List<GeneratedFileUi> =
        data.listPayload("files", "items", "results", "data").mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            val name = obj.string("file_name") ?: return@mapNotNull null
            GeneratedFileUi(
                fileName = name,
                totalBytes = obj["total_bytes"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L,
                exists = obj["exists"]?.jsonPrimitive?.let { p ->
                    p.contentOrNull?.lowercase() in setOf("1", "true")
                } ?: true,
            )
        }

    private fun parseDownloadPayload(data: JsonElement): DownloadPayloadUi {
        val obj = data as? JsonObject ?: return DownloadPayloadUi(fileName = "")
        return DownloadPayloadUi(
            fileName = obj.string("file_name").orEmpty(),
            mimeType = obj.string("mime_type").orEmpty(),
            totalBytes = obj["total_bytes"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L,
            contentBase64 = obj.string("content_base64").orEmpty(),
            truncated = obj.boolFlag("truncated"),
        )
    }

    private fun parseToolCalls(data: JsonElement): List<ToolCallUi> =
        data.listPayload("tool_calls", "items", "results", "data").mapNotNull {
            (it as? JsonObject)?.let(::parseToolCallObject)
        }

    /**
     * Parse a tool-call object. Used both by the read ops
     * (tool_call.list_for_*, tool_call.get) and by the event handlers
     * (tool_call.added / tool_call.updated, which embed a full snapshot
     * alongside the {conv_id, call_id} keys).
     */
    fun parseToolCallObject(obj: JsonObject): ToolCallUi? {
        val id = obj.string("id") ?: return null
        return ToolCallUi(
            id = id,
            messageId = obj.string("message_id").orEmpty(),
            toolName = obj.string("tool_name").orEmpty(),
            arguments = obj["arguments"] ?: JsonNull,
            result = obj["result"] ?: JsonNull,
            status = ToolCallStatus.fromWire(obj.string("status")),
            // The host sends these as epoch milliseconds.
            startedAt = normaliseTimestamp(obj.string("started_at").orEmpty()),
            completedAt = normaliseTimestamp(obj.string("completed_at").orEmpty()),
            planStepId = obj.string("plan_step_id").orEmpty(),
        )
    }

    /**
     * Parse a `plan.list_for_conv` array.
     *
     * ⚠ The wire shape is `WireDbReader::planById` / `plansForConversation`,
     * which projects the DB row verbatim in **snake_case** and adds a `steps`
     * array. It is NOT `PlansModel`'s camelCase map — that one feeds the
     * desktop's QML overlay and never reaches a client. This parser was
     * originally written against the QML shape, so every key but `id`, `goal`,
     * `status` and `steps` silently defaulted: the overlay showed `Steps 0/0`
     * and an empty progress bar on every plan. Read snake_case first and keep
     * a camelCase fallback so a payload from either source parses.
     */
    private fun parsePlans(data: JsonElement): List<PlanUi> =
        data.listPayload("plans", "items", "results", "data").mapNotNull {
            (it as? JsonObject)?.let(::parsePlanObject)
        }

    fun parsePlanObject(obj: JsonObject): PlanUi? {
        val id = obj.string("id") ?: return null
        val status = PlanStatus.fromWire(obj.string("status"))
        val steps = (obj["steps"] as? JsonArray)?.mapNotNull { s ->
            (s as? JsonObject)?.let { parsePlanStepObject(it, status) }
        }.orEmpty()
        return PlanUi(
            id = id,
            conversationId = obj.anyString("conversation_id", "conversationId").orEmpty(),
            goal = obj.string("goal").orEmpty(),
            status = status,
            startedBy = obj.anyString("started_by", "startedBy").orEmpty(),
            createdAtMs = obj.anyLong("created_at", "createdAtMs") ?: 0L,
            updatedAtMs = obj.anyLong("updated_at", "updatedAtMs") ?: 0L,
            // The host does not send progress counters; its own overlay derives
            // them from step statuses. Doing the same here is what makes a
            // completed task render a full bar, because completing a task marks
            // every non-terminal step Done in the database.
            totalSteps = steps.size,
            doneSteps = steps.count { it.status == StepStatus.Done },
            steps = steps,
        )
    }

    /**
     * @param planStatus the parent plan's status, needed to derive
     *        [PlanStepUi.canIntervene] — the wire never carries that flag.
     */
    private fun parsePlanStepObject(obj: JsonObject, planStatus: PlanStatus): PlanStepUi? {
        val id = obj.string("id") ?: return null
        val stepStatus = StepStatus.fromWire(obj.string("status"))
        return PlanStepUi(
            id = id,
            title = obj.string("title").orEmpty(),
            description = obj.string("description").orEmpty(),
            ownerAlias = obj.anyString("owner_alias", "ownerAlias").orEmpty(),
            status = stepStatus,
            acceptance = obj.anyString("acceptance_criteria", "acceptance").orEmpty(),
            rejectionCount = obj.anyInt("rejection_count", "rejectionCount") ?: 0,
            toolRetryCount = obj.anyInt("tool_retry_count", "toolRetryCount") ?: 0,
            executorTurnsUsed = obj.anyInt("executor_turns_used", "executorTurnsUsed") ?: 0,
            lastRejectionReason =
                obj.anyString("last_rejection_reason", "lastRejectionReason").orEmpty(),
            // Derived, not read: WireDbReader projects the plan_steps row and has
            // no such column, so this arrived as `false` forever and the per-step
            // Retry / Mark-done / Skip buttons never appeared. Mirrors the host's
            // own rule in plans-model.cpp:323-327 exactly.
            canIntervene = (
                planStatus == PlanStatus.Executing ||
                    planStatus == PlanStatus.Critiquing ||
                    planStatus == PlanStatus.Blocked
                ) && stepStatus != StepStatus.Done,
        )
    }

    private fun parseConvSettings(data: JsonElement): ConvSettingsUi {
        val obj = data as? JsonObject ?: return ConvSettingsUi()
        return ConvSettingsUi(
            systemPrompt = obj.string("systemPrompt").orEmpty(),
            temperature = obj["temperature"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull() ?: 0.7,
            maxTokens = obj["maxTokens"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 4096,
            contextWindow = obj["contextWindow"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 8192,
            streaming = obj.boolFlag("streaming"),
            thinking = obj.boolFlag("thinking"),
            providerId = obj.string("providerId").orEmpty(),
            modelName = obj.string("modelName").orEmpty(),
            isGroup = obj.boolFlag("isGroup"),
            folderId = obj.string("folderId").orEmpty(),
            primaryAgentId = obj.string("primaryAgentId").orEmpty(),
            heartbeatAutoSurface = obj.boolFlag("heartbeatAutoSurface"),
            autoSurfaceMaxPerDay = obj["autoSurfaceMaxPerDay"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 1,
            agentPattern = AgentPattern.fromWire(obj.string("agentPattern")),
            requireConfirmation = obj.boolFlag("requireConfirmation"),
            toolsEnabled = obj["toolsEnabled"]?.jsonPrimitive?.let { p ->
                p.contentOrNull?.lowercase() in setOf("1", "true")
            } ?: true,
            ragEnabled = obj.boolFlag("ragEnabled"),
            topK = obj["topK"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull() ?: -1.0,
            topP = obj["topP"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull() ?: -1.0,
            repeatPenalty = obj["repeatPenalty"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull() ?: -1.0,
            presencePenalty = obj["presencePenalty"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull() ?: -1.0,
            frequencyPenalty = obj["frequencyPenalty"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull() ?: -1.0,
            forceAppSampling = obj["forceAppSampling"]?.jsonPrimitive?.let { p ->
                p.contentOrNull?.lowercase() in setOf("1", "true")
            } ?: true,
            toolsInSystemPrompt = obj.boolFlag("toolsInSystemPrompt"),
            dynamicCompactEnabled = obj["dynamicCompactEnabled"]?.jsonPrimitive?.let { p ->
                p.contentOrNull?.lowercase() in setOf("1", "true")
            } ?: true,
            compactEveryTurns = obj["compactEveryTurns"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 20,
        )
    }

    private fun parseDocuments(data: JsonElement): List<ProjectDocumentUi> =
        data.listPayload("documents", "items", "results", "data").mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            val name = obj.string("name") ?: return@mapNotNull null
            ProjectDocumentUi(
                name = name,
                path = obj.string("path").orEmpty(),
                size = obj["size"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L,
            )
        }

    fun parseHeartbeatConfigObject(obj: JsonObject): HeartbeatConfigUi? {
        val id = obj.string("id") ?: return null
        return HeartbeatConfigUi(
            id = id,
            agentId = obj.string("agentId").orEmpty(),
            scopeType = HeartbeatScope.fromWire(obj.string("scopeType")),
            scopeId = obj.string("scopeId").orEmpty(),
            alias = obj.string("alias").orEmpty(),
            enabled = obj.boolFlag("enabled"),
            schedule = obj.string("schedule").orEmpty(),
            goal = obj.string("goal").orEmpty(),
            surfaceCriteria = obj.string("surfaceCriteria").orEmpty(),
            maxRunsPerDay = obj["maxRunsPerDay"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 24,
            autoSurfaceTargetConversationId =
                obj.string("autoSurfaceTargetConversationId").orEmpty(),
            selfConfigAllowed = obj.boolFlag("selfConfigAllowed"),
            lastFireAt = obj.string("lastFireAt").orEmpty(),
            lastFireOutcome = obj.string("lastFireOutcome").orEmpty(),
        )
    }

    private fun parseHeartbeatConfigs(data: JsonElement): List<HeartbeatConfigUi> =
        data.listPayload("configs", "items", "results", "data").mapNotNull {
            (it as? JsonObject)?.let(::parseHeartbeatConfigObject)
        }

    private fun parseHeartbeatStatus(data: JsonElement): HeartbeatStatusUi {
        val obj = data as? JsonObject ?: return HeartbeatStatusUi(
            enabled = false, schedule = "", lastFireAt = "", lastFireOutcome = "",
            inflight = false, queuedCount = 0,
        )
        return HeartbeatStatusUi(
            enabled = obj.boolFlag("enabled"),
            schedule = obj.string("schedule").orEmpty(),
            lastFireAt = obj.string("lastFireAt").orEmpty(),
            lastFireOutcome = obj.string("lastFireOutcome").orEmpty(),
            inflight = obj.boolFlag("inflight"),
            queuedCount = obj["queuedCount"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0,
        )
    }

    private fun parseHeartbeatRuns(data: JsonElement): List<HeartbeatRunUi> =
        data.listPayload("runs", "items", "results", "data").mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            val id = obj.string("id") ?: return@mapNotNull null
            HeartbeatRunUi(
                id = id,
                configId = obj.string("configId").orEmpty(),
                agentName = obj.string("agentName").orEmpty(),
                alias = obj.string("alias").orEmpty(),
                startedAtMs = obj["startedAtMs"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: -1L,
                durationMs = obj["durationMs"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: -1L,
                outcome = obj.string("outcome").orEmpty(),
                title = obj.string("title").orEmpty(),
                surfaceStatus = obj.string("surfaceStatus").orEmpty(),
            )
        }

    private fun parseHeartbeatNextFires(data: JsonElement): List<HeartbeatNextFireUi> =
        data.listPayload("fires", "items", "results", "data").mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            val configId = obj.string("configId") ?: return@mapNotNull null
            HeartbeatNextFireUi(
                configId = configId,
                alias = obj.string("alias").orEmpty(),
                schedule = obj.string("schedule").orEmpty(),
                nextFireMs = obj["nextFireMs"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L,
            )
        }

    private fun parseHeartbeatChanges(data: JsonElement): List<HeartbeatConfigChangeUi> =
        data.listPayload("changes", "items", "results", "data").mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            val id = obj.string("id") ?: return@mapNotNull null
            HeartbeatConfigChangeUi(
                id = id,
                configId = obj.string("configId").orEmpty(),
                changedAtMs = obj["changedAtMs"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: -1L,
                field = obj.string("field").orEmpty(),
                oldValue = obj.string("oldValue").orEmpty(),
                newValue = obj.string("newValue").orEmpty(),
                source = obj.string("source").orEmpty(),
                alias = obj.string("alias").orEmpty(),
                agentId = obj.string("agentId").orEmpty(),
            )
        }


    private fun parseActivityList(data: JsonElement): List<ActivityEventUi> =
        data.listPayload("activity", "items", "results", "data").mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            val id = obj.string("id") ?: return@mapNotNull null
            ActivityEventUi(
                id = id,
                createdAt = obj.string("createdAt").orEmpty(),
                projectFolderId = obj.string("projectFolderId").orEmpty(),
                conversationId = obj.string("conversationId").orEmpty(),
                turnId = obj.string("turnId").orEmpty(),
                actorKind = obj.string("actorKind").orEmpty(),
                actorAlias = obj.string("actorAlias").orEmpty(),
                actorAgentId = obj.string("actorAgentId").orEmpty(),
                actorClientId = obj.string("actorClientId").orEmpty(),
                eventType = obj.string("eventType").orEmpty(),
                toolName = obj.string("toolName").orEmpty(),
                eventSummary = obj.string("eventSummary").orEmpty(),
                eventDetail = (obj["eventDetail"] as? JsonObject)
                    ?.entries
                    ?.associate { (k, v) ->
                        k to when (v) {
                            is JsonObject -> v.toString()
                            else -> v.jsonPrimitive.contentOrNull
                        }
                    }
                    ?: emptyMap(),
            )
        }


    private fun parseProjectTemplateList(data: JsonElement): List<ProjectTemplateUi> =
        data.listPayload("templates", "items", "results", "data").mapNotNull {
            (it as? JsonObject)?.let(::parseProjectTemplateObject)
        }

    /**
     * Parse the {data: {...row...}} envelope that wire-session returns
     * for `project_template.get`. Empty / missing id throws so the
     * caller's `mapCatching` propagates the failure into Result.failure.
     */
    private fun parseProjectTemplateMap(data: JsonElement): ProjectTemplateUi {
        val obj = data as? JsonObject
            ?: throw RemoteProtocolException(RemoteError("bad_shape", "expected object"))
        return parseProjectTemplateObject(obj)
            ?: throw RemoteProtocolException(RemoteError("bad_template", "missing id"))
    }

    /**
     * Parse one template row. Returns null when the id is missing —
     * lets list parsers skip malformed entries without aborting the
     * whole list.
     */
    private fun parseProjectTemplateObject(obj: JsonObject): ProjectTemplateUi? {
        val id = obj.string("id") ?: return null
        val membersJson = (obj["members"] as? JsonArray) ?: JsonArray(emptyList())
        val members = membersJson.mapNotNull { item ->
            val m = item as? JsonObject ?: return@mapNotNull null
            TemplateMemberSummary(
                agentName = m.string("agentName").orEmpty().ifBlank {
                    m.string("name").orEmpty()
                },
                alias = m.string("alias").orEmpty(),
                isCoordinator = m.boolFlag("isCoordinator"),
            )
        }
        val seedDocs = (obj["seedDocuments"] as? JsonArray)
            ?.mapNotNull { it.jsonPrimitive.contentOrNull }
            ?: emptyList()
        return ProjectTemplateUi(
            id = id,
            name = obj.string("name").orEmpty(),
            tagLabel = obj.string("tagLabel").orEmpty(),
            category = obj.string("category").orEmpty(),
            geometryKind = obj.string("geometryKind").orEmpty(),
            baseHue = (obj["baseHue"] as? JsonPrimitive)?.intOrNull ?: 0,
            scenario = obj.string("scenario").orEmpty(),
            goal = obj.string("goal").orEmpty(),
            description = obj.string("description").orEmpty(),
            coordinator = obj.string("coordinator").orEmpty(),
            members = members,
            seedDocuments = seedDocs,
            isUserSaved = obj.boolFlag("isUserSaved"),
            isPinned = obj.boolFlag("isPinned"),
        )
    }

    private fun parseTemplateRoster(data: JsonElement): List<TemplateRosterMemberUi> {
        val arr = data as? JsonArray ?: return emptyList()
        return arr.mapNotNull { item ->
            val m = item as? JsonObject ?: return@mapNotNull null
            val agentId = m.string("agentId").orEmpty()
            if (agentId.isBlank()) return@mapNotNull null
            TemplateRosterMemberUi(
                agentId = agentId,
                alias = m.string("alias").orEmpty(),
                isCoordinator = m.boolFlag("isCoordinator"),
                agentName = m.string("agentName").orEmpty(),
                iconName = m.string("iconName").orEmpty(),
                modelProvider = m.string("modelProvider").orEmpty(),
                modelName = m.string("modelName").orEmpty(),
                allowedTools = (m["allowedTools"] as? JsonArray)
                    ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                    .orEmpty(),
            )
        }
    }


    private fun parsePollList(data: JsonElement):
        List<com.verzeta.android.data.poll.PollUi> =
        data.listPayload("polls", "items", "results", "data").mapNotNull {
            (it as? JsonObject)?.let(::parsePollObject)
        }

    private fun parsePollObject(obj: JsonObject):
        com.verzeta.android.data.poll.PollUi? {
        val id = obj.string("id") ?: return null
        val optionsArr = obj["options"] as? JsonArray
        val opts = optionsArr?.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val oid = o.string("id") ?: return@mapNotNull null
            com.verzeta.android.data.poll.PollOptionUi(
                id = oid,
                text = o.string("text").orEmpty(),
                ordering = (o["ordering"] as? JsonPrimitive)
                    ?.content?.toIntOrNull() ?: 0,
                votes = (o["votes"] as? JsonPrimitive)
                    ?.content?.toIntOrNull() ?: 0,
            )
        }.orEmpty()
        val winners = (obj["winning_option_ids"] as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            .orEmpty()
        return com.verzeta.android.data.poll.PollUi(
            id = id,
            conversationId = obj.string("conversation_id").orEmpty(),
            creatorKind = obj.string("creator_kind").orEmpty(),
            creatorAlias = obj.string("creator_alias").orEmpty(),
            question = obj.string("question").orEmpty(),
            mode = obj.string("mode").orEmpty().ifBlank { "single" },
            status = obj.string("status").orEmpty().ifBlank { "open" },
            createdAt = obj.string("created_at").orEmpty(),
            closesAt = obj.string("closes_at")?.takeIf { it.isNotEmpty() },
            closedAt = obj.string("closed_at")?.takeIf { it.isNotEmpty() },
            options = opts,
            winningOptionIds = winners,
            totalVotes = (obj["total_votes"] as? JsonPrimitive)
                ?.content?.toIntOrNull() ?: 0,
            voterCount = (obj["voter_count"] as? JsonPrimitive)
                ?.content?.toIntOrNull() ?: 0,
        )
    }

    private fun parseAgentsList(data: JsonElement): List<AgentSummaryUi> =
        data.listPayload("agents", "items", "results", "data").mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            val id = obj.string("id") ?: return@mapNotNull null
            AgentSummaryUi(
                id = id,
                name = obj.string("name").orEmpty().ifBlank { "Unnamed" },
                description = obj.string("description").orEmpty(),
                iconName = obj.string("iconName") ?: obj.string("icon_name").orEmpty(),
                isBuiltin = obj.boolFlag("isBuiltin") || obj.boolFlag("is_builtin"),
                isCoordinator = obj.boolFlag("isCoordinator") || obj.boolFlag("is_coordinator"),
            )
        }

    private fun parseFolderInfo(data: JsonElement): FolderUi {
        val obj = data as? JsonObject
            ?: throw RemoteProtocolException(RemoteError("missing_data", "folder.info response is not an object"))
        return parseFolderFromObject(obj)
    }

    /**
     * Parse `folder.conversations` rows. The host emits a slim shape here
     * ({id, title, isGroup, updatedAtMs}) — fill in the [ConversationUi]
     * fields we have and leave the rest at defaults; the caller can call
     * [getConversation] for a full row when needed.
     */
    private fun parseFolderConversations(data: JsonElement): List<ConversationUi> =
        data.listPayload("conversations", "items", "results", "data").mapNotNull {
            val obj = it as? JsonObject ?: return@mapNotNull null
            val id = obj.string("id") ?: return@mapNotNull null
            ConversationUi(
                id = id,
                title = obj.string("title").orEmpty().ifBlank { "Untitled" },
                folderId = obj.string("folder_id") ?: obj.string("folderId"),
                isGroup = obj["isGroup"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull()
                    ?: obj.intFlag("is_group"),
                updatedAt = normaliseTimestamp(
                    obj.string("updated_at")
                        ?: obj["updatedAtMs"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                ),
            )
        }

    private fun parseModelCatalog(data: JsonElement): ModelCatalogUi {
        val obj = data as? JsonObject ?: return ModelCatalogUi()
        val providers = (obj["providers"] as? JsonArray)?.mapNotNull { providerElement ->
            val provider = providerElement as? JsonObject ?: return@mapNotNull null
            val providerId = provider.string("provider_id") ?: return@mapNotNull null
            val models = (provider["models"] as? JsonArray)
                ?.mapNotNull { it.jsonPrimitive.contentOrNull }
                .orEmpty()
            ProviderUi(
                providerId = providerId,
                displayName = provider.string("display_name").orEmpty().ifBlank { providerId },
                models = models,
                // Capability flags are additive on the wire — older host
                // builds omit them and the missing fields default to
                // false/true via the data-class defaults (streaming
                // defaults true because the OpenAI / Anthropic / Gemini
                // baseline supports it; tool calling defaults false so an
                // unknown provider is not advertised as tool-capable; vision
                // defaults false for the same reason).  When the host is
                // current, the values come through verbatim from
                // OpenAICompatProvider / OpenAIProvider / etc.
                supportsStreaming   = provider.boolOrNull("supports_streaming") ?: true,
                supportsToolCalling = provider.boolOrNull("supports_tool_calling") ?: false,
                supportsVision      = provider.boolOrNull("supports_vision") ?: false,
            )
        }.orEmpty()
        return ModelCatalogUi(
            providers = providers,
            activeProvider = obj.string("active_provider").orEmpty(),
            activeModel = obj.string("active_model").orEmpty(),
        )
    }

    /**
     * Parse a `search.providers` reply. The host op returns the provider
     * list directly as a JSON array (camelCase keys); an object wrapper with
     * a `providers` array is accepted defensively.
     */
    private fun parseSearchProviders(data: JsonElement): SearchProvidersUi {
        val arr = data as? JsonArray
            ?: (data as? JsonObject)?.get("providers") as? JsonArray
        val providers = arr?.mapNotNull { element ->
            val o = element as? JsonObject ?: return@mapNotNull null
            val id = o.string("id") ?: return@mapNotNull null
            SearchProviderUi(
                id = id,
                displayName = o.string("displayName").orEmpty().ifBlank { id },
                requiresApiKey = o.boolOrNull("requiresApiKey") ?: false,
                hasKey = o.boolOrNull("hasKey") ?: false,
                active = o.boolOrNull("active") ?: false,
                baseUrl = o.string("baseUrl").orEmpty(),
            )
        }.orEmpty()
        return SearchProvidersUi(providers)
    }

    private fun parseClients(data: JsonElement): List<ClientUi> =
        data.listPayload("clients", "items", "results", "data").mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            val id = obj.string("id") ?: return@mapNotNull null
            ClientUi(
                id = id,
                name = obj.string("name").orEmpty().ifBlank { "Unnamed client" },
                // The host sends these as epoch milliseconds (or null).
                createdAt = normaliseTimestamp(obj.string("created_at").orEmpty()),
                lastSeenAt = normaliseTimestamp(obj.string("last_seen_at").orEmpty()),
                revoked = obj.boolFlag("revoked"),
            )
        }

    /**
     * Read an id-like reply that is either a bare JSON string or an object
     * carrying the value under one of [keys].
     *
     * @returns the first non-blank value, or null when none is present.
     */
    internal fun parseIdOrNull(data: JsonElement, vararg keys: String): String? {
        (data as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
            ?.takeIf(String::isNotBlank)?.let { return it }
        val obj = data as? JsonObject ?: return null
        return obj.anyString(*keys)
    }

    /**
     * Read a boolean reply that is either a bare JSON boolean (the host's
     * `asyncInvoke(..., "bool")` shape) or an object with the flag under
     * [key].
     */
    internal fun parseBoolReply(data: JsonElement, key: String = "result"): Boolean {
        (data as? JsonPrimitive)?.let { prim ->
            prim.booleanOrNull?.let { return it }
            return prim.contentOrNull?.lowercase() in setOf("1", "true")
        }
        return (data as? JsonObject)?.boolFlag(key) ?: false
    }

    /** True when a write op's reply data is the bare boolean `false`. */
    internal fun isRefusal(data: JsonElement?): Boolean =
        (data as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull == false

    private fun JsonElement.listPayload(vararg objectKeys: String): List<JsonElement> =
        when (this) {
            is JsonArray -> this.toList()
            is JsonObject -> objectKeys.firstNotNullOfOrNull { key ->
                val value = this[key] ?: return@firstNotNullOfOrNull null
                when (value) {
                    is JsonArray -> value.toList()
                    is JsonObject -> value.listPayload(*objectKeys)
                    JsonNull -> null
                    else -> null
                }
            } ?: emptyList()
            else -> emptyList()
        }

    private fun JsonObject.string(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank)

    /**
     * First non-blank string among [keys].
     *
     * The host projects some payloads straight from the database in
     * snake_case (`WireDbReader`) and others through a QML-facing model in
     * camelCase. Rather than guess, read the wire spelling first and fall back
     * to the model spelling, mirroring the VS Code client's `a ?? b` chains.
     */
    private fun JsonObject.anyString(vararg keys: String): String? =
        keys.firstNotNullOfOrNull { string(it) }

    /** First parseable Int among [keys]; null when none is present. */
    private fun JsonObject.anyInt(vararg keys: String): Int? =
        keys.firstNotNullOfOrNull { this[it]?.jsonPrimitive?.contentOrNull?.toIntOrNull() }

    /** First parseable Long among [keys]; null when none is present. */
    private fun JsonObject.anyLong(vararg keys: String): Long? =
        keys.firstNotNullOfOrNull { this[it]?.jsonPrimitive?.contentOrNull?.toLongOrNull() }

    /**
     * Truthiness of a flag field from the host. The host's
     * `remote-ws-serialise.cpp` emits booleans (`is_group`, `is_pinned`)
     * as JSON booleans (Qt serialises a `bool` as `true` / `false`), but
     * legacy / mixed payloads sometimes use 0/1 integers and "true"
     * strings. Accept all three so the same helper works for every
     * snapshot path.
     *
     * Earlier this function only checked `toIntOrNull` on the string
     * form — which silently returned false for `true` (JSON boolean),
     * making every group chat appear as a non-group on Android. That
     * was the actual root cause of "groups don't appear under the
     * Group filter".
     */
    private fun JsonObject.intFlag(key: String): Boolean {
        val prim = this[key]?.jsonPrimitive ?: return false
        prim.booleanOrNull?.let { return it }
        val text = prim.contentOrNull ?: return false
        return text.toIntOrNull()?.let { it != 0 } ?: (text.equals("true", ignoreCase = true))
    }

    private fun JsonObject.boolFlag(key: String): Boolean =
        this[key]?.jsonPrimitive?.contentOrNull?.lowercase() in setOf("1", "true")

    /**
     * Nullable boolean reader.  Returns `null` when the field is
     * absent OR present-but-not-parseable so callers can supply a
     * default that differs from `false`.  Accepts JSON booleans,
     * 0/1 integers, and "true" / "false" strings — same shape
     * tolerance the `intFlag` helper applies to legacy mixed
     * payloads.  Required for the `models.catalog` capability-flag
     * fields where streaming defaults `true` and tools / vision
     * default `false` when an older host omits them.
     */
    private fun JsonObject.boolOrNull(key: String): Boolean? {
        val prim = this[key]?.jsonPrimitive ?: return null
        prim.booleanOrNull?.let { return it }
        val text = prim.contentOrNull ?: return null
        return text.toIntOrNull()?.let { it != 0 }
            ?: when (text.lowercase()) {
                "true" -> true
                "false" -> false
                else -> null
            }
    }

    private fun JsonObject.firstObject(vararg keys: String): JsonObject? =
        keys.firstNotNullOfOrNull { key -> this[key] as? JsonObject }

    private fun String?.toMessageRole(): MessageRole =
        when (this?.lowercase()) {
            "user" -> MessageRole.User
            "assistant" -> MessageRole.Assistant
            "system" -> MessageRole.System
            "tool" -> MessageRole.Tool
            else -> MessageRole.Unknown
        }

    companion object {
        /**
         * Largest total of raw bytes the client sends in one frame
         * (attachments on a message, or one project document).
         *
         * Base64 grows data by a third, and OkHttp closes a WebSocket whose
         * outgoing queue passes 16 MiB, so 11 MiB of raw bytes (about
         * 14.7 MiB encoded) is the most that fits with room for the JSON
         * envelope. The host itself accepts up to 16 MiB of content.
         */
        const val MAX_UPLOAD_RAW_BYTES: Long = 11L * 1024 * 1024
    }
}

/**
 * Exception type for host-declared protocol errors. Token values are never
 * embedded in messages.
 */
class RemoteProtocolException(error: RemoteError?) : IllegalStateException(
    error?.detail ?: "Remote protocol operation failed",
)
