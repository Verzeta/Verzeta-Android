// SPDX-FileCopyrightText: 2026 Aditya Mehra <aix.m@outlook.com>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

/**
 * @file MainViewModel.kt
 * @brief Primary ViewModel for the Verzeta Android app, owning the full UI state
 *        and orchestrating all wire operations via [RemoteSession] and [RemoteRepository].
 *        Drives navigation, conversation management, agent lifecycle, canvas, heartbeats,
 *        project rooms, and real-time event handling for the host wire protocol.
 * @layer Service
 * @dependencies RemoteRepository, RemoteSession, PrefsTokenStore,
 *               SharedPreferencesHostConfigStore, AndroidX Lifecycle ViewModel,
 *               Kotlin Coroutines, kotlinx.serialization
 */

package com.verzeta.android

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.verzeta.android.data.chat.AgentPattern
import com.verzeta.android.data.chat.ArtifactRowUi
import com.verzeta.android.data.chat.ConvSettingsUi
import com.verzeta.android.data.chat.ConversationKind
import com.verzeta.android.data.chat.ConversationUi
import com.verzeta.android.data.chat.GeneratedFileUi
import com.verzeta.android.data.chat.OutgoingAttachment
import com.verzeta.android.data.chat.MessageRole
import com.verzeta.android.data.chat.MessageUi
import com.verzeta.android.data.chat.ToolActivityUi
import com.verzeta.android.data.chat.isVisibleInChat
import com.verzeta.android.data.chat.toToolActivity
import com.verzeta.android.data.canvas.CanvasAiActionUi
import com.verzeta.android.data.canvas.CanvasRunStateUi
import com.verzeta.android.data.canvas.CanvasSandboxStateUi
import com.verzeta.android.data.canvas.CanvasToolUi
import com.verzeta.android.data.canvas.CanvasUi
import com.verzeta.android.data.canvas.ConsoleLineKind
import com.verzeta.android.data.canvas.ConsoleLineUi
import com.verzeta.android.data.agent.AgentRunStateUi
import com.verzeta.android.data.agent.AgentStepStatus
import com.verzeta.android.data.agent.AgentStepUi
import com.verzeta.android.data.agent.AgentSummaryUi
import com.verzeta.android.data.agent.PendingToolConfirmationUi
import com.verzeta.android.data.agent.ToolCallUi
import com.verzeta.android.data.folder.FolderMemberUi
import com.verzeta.android.data.folder.FolderType
import com.verzeta.android.data.folder.FolderUi
import com.verzeta.android.data.folder.GroupMemberInput
import com.verzeta.android.data.folder.ProjectDocumentUi
import com.verzeta.android.data.activity.ActivityEventUi
import com.verzeta.android.data.heartbeat.HeartbeatConfigChangeUi
import com.verzeta.android.data.heartbeat.HeartbeatConfigUi
import com.verzeta.android.data.heartbeat.HeartbeatNextFireUi
import com.verzeta.android.data.heartbeat.HeartbeatRunUi
import com.verzeta.android.data.heartbeat.HeartbeatScope
import com.verzeta.android.data.skill.PreferredSkillsScope
import com.verzeta.android.data.skill.PreferredSkillsUi
import com.verzeta.android.data.mcp.McpServerUi
import com.verzeta.android.data.mcp.McpToolUi
import com.verzeta.android.data.plan.PlanUi
import com.verzeta.android.data.model.SearchProvidersUi
import com.verzeta.android.data.model.ModelCatalogUi
import com.verzeta.android.data.skill.SkillUi
import com.verzeta.android.data.tool.ToolUi
import com.verzeta.android.data.host.HostConfig
import com.verzeta.android.data.host.HostStatus
import com.verzeta.android.data.host.HostUi
import com.verzeta.android.data.host.RecentBadge
import com.verzeta.android.data.host.RecentConversationUi
import com.verzeta.android.data.host.SharedPreferencesHostConfigStore
import com.verzeta.android.data.workbench.ClientUi
import com.verzeta.android.protocol.RemoteEvent
import com.verzeta.android.remote.ConnectionState
import com.verzeta.android.remote.RemoteEndpoint
import com.verzeta.android.remote.RemoteRepository
import com.verzeta.android.remote.RemoteSession
import com.verzeta.android.storage.PrefsTokenStore
import com.verzeta.android.util.normaliseTimestamp
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Top-level navigation surface. The bottom nav exposes Home / Chat / Settings;
 * everything else is reachable as a sub-screen (Conversations, AddHost,
 * folder browsing, paired catalogs). The Tools / McpServers / Skills routes
 * are populated from the host's `tool.list`, `mcp.list`,
 * `mcp.server_tools`, `skill.list`, and `skill.get` ops — they are read-only
 * catalogs and Android can only OBSERVE state (no install / enable / approve
 * ops exist on the wire today).
 */
enum class MainScreen {
    Home,
    AddHost,
    Conversations,
    Chat,
    Folders,
    FolderConversations,
    NewGroup,
    Settings,
    Tools,
    McpServers,
    McpServerTools,
    Skills,
    SkillDetail,
    HeartbeatActivity,
    // Read-only audit-log timeline.
    // Scope (project / conversation) is recorded in MainUiState
    // (activityScopeKind / activityScopeId).
    ActivityTimeline,
    ConvSettings,
    PlansOverlay,
    ToolCallLog,
    Artifacts,
    GeneratedMedia,
    Canvas,
    // Project Rooms surfaces.
    //   ProjectRoomsLanding: bounded template catalog + CTAs.
    //   TemplateLibrary:     full catalog + per-template pin / delete.
    //   ProjectCreate:       customise a draft + invoke create_project.
    ProjectRoomsLanding,
    TemplateLibrary,
    ProjectCreate,
    SearchProviders,
    // Read-only Revoked devices sub-screen.
    // Settings shows only active paired clients in the main list;
    // revoked clients drill into this screen for audit.
    RevokedDevices,
    // About surface (brand mark + version + verzeta.com + privacy + license).
    // Drilled into from the Settings About row.
    About,
    // In-app Help screen. Lists bundled user docs from assets/help/
    // (the canonical sources are docs/User/*.md, copied at build time
    // by the copyUserHelpDocs gradle task). Drills into a per-doc
    // viewer rendered via MarkdownText.
    Help,
}

/**
 * Argument carrier for the post-save kickoff sheet
 * (`folder.kickoff.{individual,group}`). [memberCount] gates the group
 * checkbox — host's `folder.kickoff.group` rejects with `server_error`
 * when the project has < 2 members, so we hide the option upfront to
 * avoid a wire round-trip just to fail.
 */
data class PendingKickoff(
    val folderId: String,
    val folderName: String,
    val memberCount: Int,
)

/** Identifies the bucket for a user-initiated download (artifact, generated image, or audio). */
enum class DownloadKind { Artifact, Image, Audio }

/**
 * Context carried into [com.verzeta.android.ui.group.NewGroupScreen] when
 * the user opens it. For un-scoped opens [folderId] is empty and the
 * picker pulls from the full agent registry. For project-scoped opens
 * (entered via FoldersScreen / FolderConversationsScreen "Start group
 * chat"), the picker defaults to the project's roster (`allowedAgentIds`
 * derived from `folder.members`), and freshly-picked agents not in the
 * roster are rolled into it after `group.create`.
 *
 * `preloadedMembers` is the project roster state we read at open time
 * (via `folder.members`). The save flow diffs the dialog's final list
 * against this snapshot to decide which members are net-new and need
 * `folder.member.add`.
 */
data class NewGroupContext(
    val folderId: String = "",
    val folderName: String = "",
    val preloadedMembers: List<FolderMemberUi> = emptyList(),
    val allowedAgentIds: Set<String> = emptySet(),
) {
    val isProjectScoped: Boolean get() = folderId.isNotBlank()
}

/**
 * Immutable snapshot of the entire UI state for the Verzeta Android app.
 * [MainViewModel] holds one [kotlinx.coroutines.flow.StateFlow] of this class;
 * Compose screens observe it and re-render on every change. All fields have
 * safe defaults so the app can render without a connected host.
 */
data class MainUiState(
    val screen: MainScreen = MainScreen.Home,
    /**
     * Currently-open user-doc slug (file basename without the `.md` extension,
     * e.g. "01-install-and-pair"). Null means the Help index is showing
     * instead of a specific doc.
     */
    val selectedHelpDoc: String? = null,
    /**
     * One-shot signal that the Folders screen should open its create-folder
     * sheet immediately on entry. Set by the Conversations-tab create menu
     * (whose "New folder / project" action navigates to Folders) and cleared
     * by FoldersScreen once it has consumed it.
     */
    val pendingFolderCreate: Boolean = false,
    /** With [pendingFolderCreate]: preselect the Project type in the sheet. */
    val pendingFolderCreateAsProject: Boolean = false,
    /**
     * One-shot signal that the Folders screen should open the edit sheet for
     * this folder id. Set by "Edit folder" in the Conversations tab.
     */
    val pendingFolderEditId: String? = null,
    /**
     * Per-conversation context-fill percentage from `chat.context_fill.changed`
     * (measured by the host at each request build). Drives the composer gauge;
     * absent key = no measurement yet for that conversation this session.
     */
    val contextFillPercents: Map<String, Int> = emptyMap(),
    val hosts: List<HostUi> = emptyList(),
    val activeHostId: String? = null,
    val recentConversations: List<RecentConversationUi> = emptyList(),
    val conversations: List<ConversationUi> = emptyList(),
    val conversationSearch: String = "",
    val selectedConversationId: String? = null,
    val messages: List<MessageUi> = emptyList(),
    /**
     * Filtered tool messages held outside the chat list — the [MessageUi]
     * `role == "tool"` rows and the empty assistant placeholders with
     * `finish_reason == "tool_calls"` produced by the host's tool flow.
     * Surfaced in [com.verzeta.android.ui.activity.ToolActivityScreen].
     */
    val toolActivity: List<ToolActivityUi> = emptyList(),
    val messageDraft: String = "",
    val chatStatus: String = "Connect a host to load conversations",
    val clients: List<ClientUi> = emptyList(),
    val settingsStatus: String = "Connect a host to load paired clients",
    /**
     * Folders for the active host: projects and organizations, plus regular
     * folders while [foldersShowAll] is on. Populated lazily
     * by [MainViewModel.refreshFolders] on first navigation into the
     * Folders screen and kept current via `folder.added` / `folder.updated`
     * / `folder.deleted` events.
     *
     * Note: filter chips were removed to mirror the desktop sidebar,
     * which only has a search field and never offered All/Pinned/Groups/
     * Agents/Canvas chip toggles. Visibility is now driven by the
     * SidebarFlatModel-equivalent section bucketing, the search field,
     * and folder collapse state.
     */
    val folders: List<FolderUi> = emptyList(),
    val selectedFolderId: String? = null,
    val foldersStatus: String = "Connect a host to load folders",
    /**
     * When true ("All folders" on the Folders screen), [folders] holds every
     * folder, regular ones included, instead of only projects and
     * organizations. Regular folders are reachable only this way, both on
     * the Folders screen and in the Conversations list.
     */
    val foldersShowAll: Boolean = false,
    /**
     * Agent registry, populated lazily from `agent.list` after auth.
     * The picker in [com.verzeta.android.ui.folders.FolderEditorSheet]
     * reads this; the host has no `agent.*` event yet so we refresh on
     * user-initiated screen entry rather than passively.
     */
    val agents: List<AgentSummaryUi> = emptyList(),
    val agentsStatus: String = "",
    val folderMembers: Map<String, List<FolderMemberUi>> = emptyMap(),
    /**
     * Per-conversation member lists. Same wire shape as folder members.
     * Currently no Android UI consumer; cached so the wire surface stays
     * complete and ready to light up when group-chat editing arrives.
     */
    val conversationMembers: Map<String, List<FolderMemberUi>> = emptyMap(),
    /**
     * Project documents per folder (`folder.documents`). Refreshed on
     * editor open and after every successful upload / remove (no event;
     * the host hasn't added a `folder.documents.changed` channel yet).
     */
    val folderDocuments: Map<String, List<ProjectDocumentUi>> = emptyMap(),
    /**
     * Heartbeat configs per folder. Kept current via
     * `heartbeat.config.changed` and `heartbeat.config.removed` events.
     */
    val folderHeartbeats: Map<String, List<HeartbeatConfigUi>> = emptyMap(),
    /**
     * Activity overlay caches: history of recent runs (capped 500), the
     * upcoming-fire preview, and the audit log of config changes. Loaded
     * on demand when the user opens the Activity surface.
     */
    // Append-only audit-log timeline state.
    // activityScopeKind ∈ {"", "project", "conversation"}; the screen's
    // open path sets all three together.
    val activityLog: List<ActivityEventUi> = emptyList(),
    val activityScopeKind: String = "",
    val activityScopeId: String = "",
    val activityScopeLabel: String = "",
    val recentHeartbeatRuns: List<HeartbeatRunUi> = emptyList(),
    val heartbeatNextFires: List<HeartbeatNextFireUi> = emptyList(),
    val recentHeartbeatChanges: List<HeartbeatConfigChangeUi> = emptyList(),
    /**
     * Preferred-skills configuration per folder (`skill.preferred.list` +
     * `skill.expose_only_preferred`). Re-fetched on editor open. Saving
     * the editor writes back via `skill.preferred.set` +
     * `skill.set_expose_only_preferred`. There's no host event for
     * change; siblings refresh on next open.
     */
    val folderPreferredSkills: Map<String, PreferredSkillsUi> = emptyMap(),
    /**
     * Pending kickoff context — set by [MainViewModel.saveFolderEdit] /
     * [createFolderWithMembers] when a project / org folder ended up
     * with members. The kickoff sheet (Section 4) reads this and the UI
     * triggers `folder.kickoff.individual` and/or `folder.kickoff.group`
     * via the explicit user choice.
     */
    val pendingKickoff: PendingKickoff? = null,
    /** Active context for the new-group flow. Empty in un-scoped mode. */
    val newGroupContext: NewGroupContext = NewGroupContext(),
    /**
     * Per-conversation settings cache. Populated on `conv.settings.get`,
     * mutated locally on user edit (so the panel reflects the change
     * without waiting for the host's `conv.updated` echo), and refreshed
     * after `conv.updated` events lands.
     */
    val convSettings: Map<String, ConvSettingsUi> = emptyMap(),
    /**
     * Per-conversation plan list. Populated by `plan.list_for_conv` on
     * overlay open; kept current via `plan.created` / `plan.updated` /
     * `plan.deleted` events filtered by `conversationId`. `step.updated`
     * triggers a `plan.get` re-fetch for the parent plan.
     *
     * Filtering note: plan/step events fire to all authenticated
     * sessions globally (the host has no per-conv subscription on this
     * channel). The handler must check `conversationId` against caches
     * before mutating to avoid leaking another conv's plans into the
     * active overlay.
     */
    val convPlans: Map<String, List<PlanUi>> = emptyMap(),
    /**
     * Conversation whose `task.start` is in flight (between the request and
     * the first `plan.created` event for that conversation), or null. Drives
     * the "Starting…" indicator in that conversation's Plans overlay only.
     */
    val taskStartingConvId: String? = null,
    /**
     * Per-conversation tool-call rows. Populated by
     * `tool_call.list_for_conv` on overlay open; kept current via
     * `tool_call.added` / `tool_call.updated` events filtered by the
     * session's msg.subscribe set (the host already gates on the
     * subscribed-conv list — Android receives only events for the conv
     * the user is currently viewing).
     */
    val convToolCalls: Map<String, List<ToolCallUi>> = emptyMap(),
    /**
     * Pending tool-call confirmation, set by `tool_call.requested` and
     * cleared by `tool_call.completed` for the same call_id (or by user
     * Approve / Deny). Host-wide — fires regardless of which conv is
     * foregrounded, so the dialog must render at the top-level shell.
     */
    val pendingToolConfirmation: PendingToolConfirmationUi? = null,
    /**
     * Live agent reasoning steps from `agent.step.started` /
     * `agent.step.completed`, capped in length. Cleared when
     * `agent.run.state` reports `is_running: false` and when another
     * conversation is opened. The AgentProgressBanner above the chat
     * composer renders this list when the agent is running.
     */
    val agentSteps: List<AgentStepUi> = emptyList(),
    val agentRunState: AgentRunStateUi = AgentRunStateUi(),
    /**
     * Artifact and generated-media caches keyed by conversationId.
     * `convArtifacts` is populated by `artifact.list_for_conv` on overlay
     * open; `artifact.added` events (filtered server-side by the
     * msg.subscribe set) append to the right bucket. Image and audio caches
     * are refreshed via `image.generated` / `audio.generated` events.
     */
    val convArtifacts: Map<String, List<ArtifactRowUi>> = emptyMap(),
    val convImages: Map<String, List<GeneratedFileUi>> = emptyMap(),
    val convAudio: Map<String, List<GeneratedFileUi>> = emptyMap(),
    /**
     * Open and closed polls keyed by conversation id. Refreshed on conv open
     * and on every poll.* event. Empty when the conversation has no polls.
     */
    val convPolls: Map<String, List<com.verzeta.android.data.poll.PollUi>> = emptyMap(),
    /**
     * Outgoing attachments staged in the composer. Cleared after a
     * successful send or on conversation switch. Validation caps live
     * in [com.verzeta.android.data.chat.OutgoingAttachment]'s docstring.
     */
    val pendingAttachments: List<OutgoingAttachment> = emptyList(),
    /**
     * Canvas state per conversationId. `convCanvas` holds the active canvas
     * snapshot; `convCanvasHistory` is loaded on demand for the history
     * dropdown. `canvasAiActionsByLang` / `canvasToolsByLang` are keyed by
     * language (the host's catalogs are per-language, not per-conversation).
     */
    val convCanvas: Map<String, CanvasUi> = emptyMap(),
    val convCanvasHistory: Map<String, List<CanvasUi>> = emptyMap(),
    val canvasAiActionsByLang: Map<String, List<CanvasAiActionUi>> = emptyMap(),
    val canvasToolsByLang: Map<String, List<CanvasToolUi>> = emptyMap(),
    /**
     * Console rows from `canvas.run.line` events. UNFILTERED: the host
     * broadcasts every line to every authenticated client because the
     * console is a single-instance singleton ("RUN executes on host,
     * shares output to Android always" — user directive). Capped at 50
     * here too (host already FIFO-trims, but a bounded buffer protects
     * against future host changes).
     */
    val consoleLines: List<ConsoleLineUi> = emptyList(),
    val consoleVisible: Boolean = false,
    val canvasRunState: CanvasRunStateUi = CanvasRunStateUi(),
    val canvasSandbox: CanvasSandboxStateUi = CanvasSandboxStateUi(),
    val canvasRunSupportedLanguages: List<String> = emptyList(),
    val canvasRunSupportedIdeLanguages: List<String> = emptyList(),
    /**
     * Read-only tool, MCP-server, and skill catalogs. Each is fetched on
     * screen-open and left to go stale until the next visit — the host has
     * no events for catalog churn (admin-driven and rare). Status strings
     * record the last load result so screens can show "Loading…" / errors
     * without polluting the global [status] line.
     */
    val tools: List<ToolUi> = emptyList(),
    val toolsStatus: String = "",
    val mcpServers: List<McpServerUi> = emptyList(),
    val mcpStatus: String = "",
    /**
     * Per-server tool cache for the [MainScreen.McpServerTools] drilldown.
     * Keyed by server name. Lazy: populated when the user taps a server
     * row, kept on disconnect so the back stack doesn't flicker.
     */
    val mcpServerTools: Map<String, List<McpToolUi>> = emptyMap(),
    val activeMcpServerName: String? = null,
    val skills: List<SkillUi> = emptyList(),
    val skillsStatus: String = "",
    /**
     * Currently-viewed skill detail. Populated synchronously from the
     * list row when the user taps in (the row carries the same fields)
     * and refreshed by a `skill.get` call to pick up any host-side
     * updates that landed since the list was fetched.
     */
    val selectedSkill: SkillUi? = null,
    /**
     * Conversation ids that we've sent `conv.delete` for but for which the
     * host has not yet emitted `conv.deleted`. Rows in this set render
     * greyed-out so the user sees the action took, while still surfacing
     * a phantom-delete on the server side.
     */
    val deletingConversationIds: Set<String> = emptySet(),
    /**
     * Provider/model catalog for the active host. Populated after auth and
     * kept current via `models.active_changed` events. The catalog itself
     * is stable per session; the active pair changes whenever any client
     * (Android, desktop, web) calls `models.set_active`.
     */
    val modelCatalog: ModelCatalogUi = ModelCatalogUi(),
    val searchProviders: SearchProvidersUi = SearchProvidersUi(),
    val searchProvidersStatus: String = "",
    /**
     * Collapse state for the bucketed Conversations browser. Holds both
     * top-level section ids (`SidebarRow.SectionPinned`, etc.) and per-folder
     * UUIDs in one set. Default empty means everything expanded. Mirrors the
     * desktop's `SidebarFlatModel::m_collapsedSections` + `m_expandedFolders`
     * — toggled by tap, not persisted to disk.
     */
    val collapsedSidebarIds: Set<String> = emptySet(),
    /**
     * Project Rooms catalog and draft state.
     *
     *  - [projectTemplates]:        full catalog from project_template.list.
     *  - [landingProjectTemplates]: bounded landing subset from .list_landing.
     *  - [selectedProjectTemplateId]: currently-viewed template id (drives
     *      ProjectCreate header + the roster lookup in [projectTemplateRosters]).
     *  - [projectTemplateRosters]:  per-template resolved roster cache.
     *  - [projectTemplateDraft]:    in-progress customisations the Quick Start
     *      screen tracks before invoking create_project. Reset on screen close.
     *  - [projectTemplateCreating]: true while create_project is in flight.
     *  - [projectTemplateCreated]:  one-shot success result of this client's
     *      own create, consumed by [MainViewModel.consumeProjectTemplateCreated],
     *      which navigates into the existing [pendingKickoff] flow for the
     *      new folder.
     *  - [projectTemplatesStatus]:  last load result for the screens' status row.
     */
    val projectTemplates: List<com.verzeta.android.data.projectroom.ProjectTemplateUi> = emptyList(),
    val landingProjectTemplates: List<com.verzeta.android.data.projectroom.ProjectTemplateUi> = emptyList(),
    val selectedProjectTemplateId: String? = null,
    val projectTemplateRosters: Map<String, List<com.verzeta.android.data.projectroom.TemplateRosterMemberUi>> = emptyMap(),
    val projectTemplateDraft: com.verzeta.android.data.projectroom.TemplateCustomisationsUi? = null,
    val projectTemplateCreating: Boolean = false,
    val projectTemplateCreated: com.verzeta.android.data.projectroom.ProjectTemplateCreatedUi? = null,
    val projectTemplatesStatus: String = "",
    val addHostName: String = "",
    val addEndpoint: String = "ws://10.0.2.2:9180/ws",
    /**
     * Optional SHA-256 fingerprint of the desktop's self-signed TLS
     * certificate. Required ONLY when [addEndpoint] starts with wss://
     * AND the desktop generated a self-signed cert (the default for
     * the host's "TLS on" toggle). The desktop's RemoteAccessDialog
     * displays the fingerprint string verbatim; the user pastes it
     * here. Empty / blank for plain ws:// or CA-signed wss://.
     */
    val addTlsCertSha256: String = "",
    val pairCode: String = "",
    val clientName: String = "Android",
    val status: String = "No host connected",
    /**
     * Latest failure to show in the app-wide snackbar, or null when there is
     * nothing to show. Set by every failed action on every screen, because
     * [status] is only rendered on a few screens. [errorId] changes with
     * each new failure so the same text reported twice is shown twice; the
     * shell clears the message with [MainViewModel.consumeError] once shown.
     */
    val errorMessage: String? = null,
    val errorId: Long = 0L,
    /**
     * Technical error detail surfaced only when the user taps the
     * "Unavailable" badge on the home / disconnected card. The handler
     * stores the structured detail here rather than painting raw JSON onto
     * [status]; the user sees a clean "Unavailable" pill and can tap to
     * reveal this text. Cleared on successful connection (ConnectionState.Open).
     */
    val lastConnectionError: String? = null,
    val hostHello: String = "",
    val authenticatedName: String = "",
    val authenticatedClientId: String = "",
    val authenticatedLastSeenAt: Long = 0L,
    val connectionState: ConnectionState = ConnectionState.Idle,
    val busy: Boolean = false,
) {
    val activeHost: HostUi?
        get() = hosts.firstOrNull { it.config.id == activeHostId }

    val selectedConversation: ConversationUi?
        get() = conversations.firstOrNull { it.id == selectedConversationId }

    val selectedFolder: FolderUi?
        get() = folders.firstOrNull { it.id == selectedFolderId }

    val folderConversations: List<ConversationUi>
        get() = selectedFolderId?.let { fid -> conversations.filter { it.folderId == fid } }
            .orEmpty()

    /**
     * Search-filtered conversations. Mirrors `SidebarFlatModel`'s
     * `matchesFilter` (sidebar-flat-model.cpp:512-515): case-insensitive
     * substring on the title only. No chip toggles — the desktop
     * sidebar doesn't have them and we removed Android's to match.
     */
    val filteredConversations: List<ConversationUi>
        get() = conversations.filter { conversation ->
            conversationSearch.isBlank() ||
                conversation.title.contains(conversationSearch, ignoreCase = true)
        }

    /**
     * Single flat list of conversations with pinned-first ordering,
     * matching the mobile-native "Pinned" + "All chats" layout.
     *
     * Inside each bucket, sort by `updated_at` desc — same as the
     * desktop sidebar's per-section ordering (sidebar-flat-model.cpp
     * sorts each bucket by updatedAt DESC). Falls back to `created_at`
     * when `updated_at` is empty (host returns empty for fresh rows
     * before the first message).
     */
    val pinnedConversations: List<ConversationUi>
        get() = filteredConversations
            .filter { it.isPinned }
            .sortedByDescending { it.updatedAt.ifBlank { it.createdAt } }

    val unpinnedConversations: List<ConversationUi>
        get() = filteredConversations
            .filterNot { it.isPinned }
            .sortedByDescending { it.updatedAt.ifBlank { it.createdAt } }
}

/**
 * ViewModel-owned orchestration. Owns one [RemoteSession] for the lifetime of
 * the view-model and closes it deterministically in [onCleared]. Action set
 * here is exhaustive against the host's 14-op surface.
 */
/**
 * Platform-notification seam for @user mention alerts. MainActivity installs
 * the NotificationManager-backed implementation (it owns the Context
 * and the POST_NOTIFICATIONS permission flow); unit tests leave it
 * null and mention events are simply dropped.
 */
interface MentionNotifier {
    /** Raise (or update) the system notification for an @user mention. */
    fun showMention(convId: String, alias: String, text: String)
}

/**
 * Primary ViewModel for the Verzeta Android app. Owns one [RemoteSession] for
 * the lifetime of the view-model and orchestrates all wire operations via
 * [RemoteRepository]. Handles navigation, real-time event dispatch, and the
 * complete host wire protocol action surface.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val session = RemoteSession()
    private val repository = RemoteRepository(session)
    private val tokenStore = PrefsTokenStore(application)
    private val hostStore = SharedPreferencesHostConfigStore(application)
    private val _uiState = MutableStateFlow(MainUiState())

    /** Installed by MainActivity for @mention notifications; null in unit tests. */
    var notifier: MentionNotifier? = null

    /**
     * Single-shot callback fired the next time a `models.active_changed`
     * event arrives. Used to chain `conv.create` after a `models.set_active`
     * so the new conversation inherits the model the user just picked
     * (the host stamps the active selection into the new row, and the
     * stamping happens after `set_active` is applied — not before).
     */
    private var pendingAfterModelChange: (() -> Unit)? = null

    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            loadHosts()
            // Pairing tokens are stored per-host in Android Keystore and the
            // host configs are persisted in SharedPreferences. On app launch
            // we have everything we need to reconnect to the most-recently
            // used host without any user action — so do that, otherwise the
            // user lands on a "No host connected" screen even though they
            // paired yesterday.
            autoReconnectMostRecentHost()
        }
        viewModelScope.launch { session.events.collect(::handleEvent) }
        viewModelScope.launch {
            repository.connectionState.collect { newState -> handleConnectionState(newState) }
        }
    }

    /**
     * Automatically reconnect to the host that was most-recently connected
     * (highest [HostConfig.lastConnectedAt]), provided a token is in
     * Keystore for that host. No-ops silently when there's no eligible
     * candidate — the user will see the "No active host" empty state and
     * can pair a new device.
     */
    private suspend fun autoReconnectMostRecentHost() {
        val candidate = uiState.value.hosts
            .filter { it.config.lastConnectedAt != null }
            .maxByOrNull { it.config.lastConnectedAt ?: 0L }
            ?: return
        val token = runCatching { tokenStore.readToken(candidate.config.id) }.getOrNull()
        if (token.isNullOrBlank()) return
        val endpoint = runCatching { RemoteEndpoint.fromEndpoint(candidate.config.endpoint) }.getOrNull()
            ?: return

        update {
            copy(
                activeHostId = candidate.config.id,
                status = "Reconnecting to ${candidate.config.name}",
            )
        }
        setHostStatus(candidate.config.id, HostStatus.Disconnected, "Reconnecting")

        repository.connect(endpoint, candidate.config.tlsCertSha256).onFailure {
            setHostStatus(candidate.config.id, HostStatus.Error, it.safeMessage())
            fail("Reconnect failed: ${it.safeMessage()}")
        }.onSuccess {
            authenticateWithToken(candidate.config.id, token)
        }
    }

    /**
     * React to socket-level state transitions. The host's `remote-ws-session.cpp`
     * tears down per-connection state when the socket closes, so a `Closed` /
     * `Failed` transition forces us to mark the host disconnected, attempt a
     * silent reconnect with the stored token, and re-issue any active
     * `msg.subscribe`.
     *
     * This is the only path that reuses `auth.token` after the user has
     * already paired once — the user-facing AddHost path goes through
     * `auth.pair`. Reconnects after drops are silent: no UI nag.
     */
    private fun handleConnectionState(state: ConnectionState) {
        update {
            // Clear any prior error detail when we're back online so the
            // home-screen "Unavailable" pill goes away automatically.
            // ConnectionState.Open is the canonical "connected and working"
            // trigger; the error stays surfaced through Connecting / Closed /
            // Failed transitions because the user may still want to read it.
            copy(
                connectionState = state,
                lastConnectionError = if (state == ConnectionState.Open) {
                    null
                } else {
                    lastConnectionError
                },
            )
        }
        val activeId = uiState.value.activeHostId ?: return
        when (state) {
            ConnectionState.Closed, ConnectionState.Failed -> {
                setHostStatus(
                    activeId,
                    HostStatus.Disconnected,
                    if (state == ConnectionState.Failed) "Connection dropped" else "Disconnected",
                )
                // Schedule a silent reconnect attempt. A failed attempt
                // drops the socket again and lands back here, so retries
                // continue, spaced by an exponential backoff (1 s doubling
                // to 30 s) that resets once authentication succeeds.
                viewModelScope.launch { attemptSilentReconnect(activeId) }
            }
            else -> Unit
        }
    }

    /**
     * Clear the snackbar message once the shell has shown it.
     *
     * @param id the [MainUiState.errorId] that was shown. A newer failure
     *        that arrived meanwhile is left in place.
     */
    fun consumeError(id: Long) {
        update { if (errorId == id) copy(errorMessage = null) else this }
    }

    /**
     * Show a failure that happened in UI code (for example decoding a
     * downloaded image) in the app-wide snackbar.
     */
    fun reportError(message: String) = fail(message)

    /**
     * Dismiss the "Unavailable" pill / error-detail dialog.
     * Clears the cached technical detail; the user has acknowledged it.
     */
    fun dismissConnectionError() {
        update { copy(lastConnectionError = null) }
    }

    /** Consecutive silent reconnect attempts since the last successful auth. */
    private var reconnectAttempts = 0

    private suspend fun attemptSilentReconnect(hostId: String) {
        val backoffMs = (1_000L shl reconnectAttempts.coerceAtMost(5)).coerceAtMost(30_000L)
        reconnectAttempts++
        delay(backoffMs)
        val state = uiState.value
        if (state.activeHostId != hostId) return
        if (state.connectionState == ConnectionState.Open || state.connectionState == ConnectionState.Connecting) return
        val host = state.hosts.firstOrNull { it.config.id == hostId }?.config ?: return
        val token = runCatching { tokenStore.readToken(hostId) }.getOrNull() ?: return
        val endpoint = runCatching { RemoteEndpoint.fromEndpoint(host.endpoint) }.getOrNull() ?: return
        repository.connect(endpoint, host.tlsCertSha256).onSuccess {
            authenticateWithToken(hostId, token, silent = true)
            if (uiState.value.activeHost?.status != HostStatus.Connected) return@onSuccess
            // Re-subscribe to whatever conversation was open when the socket
            // dropped — the host destroyed the subscription with the old
            // session, so msg.subscribe must be re-issued before deltas flow.
            // Then reload the messages: replies that finished during the
            // outage never reached us, and a bubble that was streaming would
            // otherwise stay marked as streaming.
            val convId = uiState.value.selectedConversationId
            if (convId != null) {
                repository.subscribeMessages(convId)
                refreshMessagesForConv(convId)
            }
        }
    }

    // ---- Navigation --------------------------------------------------------

    fun showHome() = update { copy(screen = MainScreen.Home) }
    fun showAddHost() = update {
        copy(
            screen = MainScreen.AddHost,
            status = "Add a desktop host",
            addHostName = "",
            addTlsCertSha256 = "",
            pairCode = "",
        )
    }
    fun showConversations() {
        update { copy(screen = MainScreen.Conversations) }
        if (uiState.value.activeHost?.status == HostStatus.Connected) {
            refreshConversations()
        }
    }
    fun showChat() {
        update { copy(screen = MainScreen.Chat) }
        if (uiState.value.activeHost?.status == HostStatus.Connected && uiState.value.conversations.isEmpty()) {
            refreshConversations()
        }
    }
    fun showSettings() {
        update { copy(screen = MainScreen.Settings) }
        if (uiState.value.activeHost?.status == HostStatus.Connected) refreshClients()
    }

    fun showFolders() {
        update { copy(screen = MainScreen.Folders) }
        if (uiState.value.activeHost?.status == HostStatus.Connected) refreshFolders()
    }

    /**
     * Navigate to the Folders screen and request it open the create-folder
     * sheet immediately. Entry point for the Conversations-tab create menu's
     * "New folder / project" item.
     */
    fun createFolderFlow() {
        update { copy(screen = MainScreen.Folders, pendingFolderCreate = true) }
        if (uiState.value.activeHost?.status == HostStatus.Connected) refreshFolders()
    }

    /**
     * Navigate to the Folders screen and open the create sheet with the
     * Project type preselected. Entry point for Project Rooms' "Blank
     * project" button.
     */
    fun createProjectFlow() {
        update {
            copy(
                screen = MainScreen.Folders,
                pendingFolderCreate = true,
                pendingFolderCreateAsProject = true,
            )
        }
        if (uiState.value.activeHost?.status == HostStatus.Connected) refreshFolders()
    }

    /**
     * Navigate to the Folders screen and open the edit sheet for [folderId].
     * Entry point for "Edit folder" in the Conversations tab.
     */
    fun editFolderFlow(folderId: String) {
        update { copy(screen = MainScreen.Folders, pendingFolderEditId = folderId) }
        if (uiState.value.activeHost?.status == HostStatus.Connected) refreshFolders()
    }

    /** FoldersScreen consumes the one-shot create-folder signal. */
    fun consumePendingFolderCreate() =
        update { copy(pendingFolderCreate = false, pendingFolderCreateAsProject = false) }

    /** FoldersScreen consumes the one-shot edit-folder signal. */
    fun consumePendingFolderEdit() = update { copy(pendingFolderEditId = null) }
    fun openFolder(folderId: String) {
        update { copy(screen = MainScreen.FolderConversations, selectedFolderId = folderId) }
    }
    fun closeFolder() = update { copy(screen = MainScreen.Folders, selectedFolderId = null) }

    /**
     * Open the new-group dialog. Pass [folderId] to scope it to a project
     * (NewGroupChatDialog Step 1, project-scoped path):
     *   - Pulls `folder.info` for the scope-banner name.
     *   - Pulls `folder.members` for the preloaded member list and the
     *     allowed-agent filter on the picker.
     *
     * Un-scoped opens populate an empty [NewGroupContext] and the picker
     * pulls from `state.agents` (full registry).
     */
    fun showNewGroup(folderId: String? = null) {
        update {
            copy(
                screen = MainScreen.NewGroup,
                newGroupContext = NewGroupContext(folderId = folderId.orEmpty()),
            )
        }
        if (uiState.value.activeHost?.status != HostStatus.Connected) return
        if (uiState.value.agents.isEmpty()) loadAgents()
        if (!folderId.isNullOrBlank()) {
            viewModelScope.launch {
                // Pull folder name for the scope banner. Folder may already be in
                // state.folders (cheap path); fall back to folder.info if not.
                val cachedName = uiState.value.folders.firstOrNull { it.id == folderId }?.name
                if (cachedName == null) {
                    repository.getFolder(folderId).onSuccess { folder ->
                        update {
                            copy(newGroupContext = newGroupContext.copy(folderName = folder.name))
                        }
                    }
                } else {
                    update { copy(newGroupContext = newGroupContext.copy(folderName = cachedName)) }
                }
                repository.listFolderMembers(folderId).onSuccess { members ->
                    update {
                        copy(
                            folderMembers = folderMembers + (folderId to members),
                            newGroupContext = newGroupContext.copy(
                                preloadedMembers = members,
                                allowedAgentIds = members.map { it.agentId }.toSet(),
                            ),
                        )
                    }
                }
            }
        }
    }
    fun closeNewGroup() = update {
        copy(screen = MainScreen.Conversations, newGroupContext = NewGroupContext())
    }

    // ---- Read-only catalog routing ----------------------------------------

    fun showTools() {
        update { copy(screen = MainScreen.Tools) }
        if (uiState.value.activeHost?.status == HostStatus.Connected) refreshTools()
    }
    fun closeTools() = update { copy(screen = MainScreen.Settings) }

    fun showMcpServers() {
        update { copy(screen = MainScreen.McpServers) }
        if (uiState.value.activeHost?.status == HostStatus.Connected) refreshMcpServers()
    }
    fun closeMcpServers() = update { copy(screen = MainScreen.Settings) }

    fun openMcpServer(serverName: String) {
        update { copy(screen = MainScreen.McpServerTools, activeMcpServerName = serverName) }
        if (uiState.value.activeHost?.status == HostStatus.Connected) refreshMcpServerTools(serverName)
    }
    fun closeMcpServer() = update {
        copy(screen = MainScreen.McpServers, activeMcpServerName = null)
    }

    fun showSkills() {
        update { copy(screen = MainScreen.Skills) }
        if (uiState.value.activeHost?.status == HostStatus.Connected) refreshSkills()
    }
    fun closeSkills() = update { copy(screen = MainScreen.Settings) }

    fun showSearchProviders() {
        update { copy(screen = MainScreen.SearchProviders) }
        if (uiState.value.activeHost?.status == HostStatus.Connected) refreshSearchProviders()
    }
    fun closeSearchProviders() = update { copy(screen = MainScreen.Settings) }

    fun refreshSearchProviders() {
        viewModelScope.launch {
            if (uiState.value.activeHost?.status != HostStatus.Connected) {
                update { copy(searchProvidersStatus = "Connect a host before loading providers") }
                return@launch
            }
            update { copy(searchProvidersStatus = "Loading providers…") }
            repository.getSearchProviders()
                .onSuccess { catalog ->
                    update {
                        copy(
                            searchProviders = catalog,
                            searchProvidersStatus = if (catalog.isEmpty) "No providers reported"
                            else "${catalog.providers.size} provider${if (catalog.providers.size == 1) "" else "s"}",
                        )
                    }
                }
                .onFailure {
                    update { copy(searchProvidersStatus = "Load failed: ${it.safeMessage()}") }
                }
        }
    }

    fun setActiveSearchProvider(providerId: String) {
        if (providerId.isBlank()) return
        viewModelScope.launch {
            // Optimistic: mark active locally, then confirm via re-fetch
            // (the host emits no search event). Restored on failure.
            val previous = uiState.value.searchProviders
            update {
                copy(
                    searchProviders = searchProviders.copy(
                        providers = searchProviders.providers.map {
                            it.copy(active = it.id == providerId)
                        },
                    ),
                )
            }
            repository.setActiveSearchProvider(providerId)
                .onSuccess { refreshSearchProviders() }
                .onFailure {
                    update { copy(searchProviders = previous) }
                    fail("Set search provider failed: ${it.safeMessage()}")
                }
        }
    }

    /**
     * Open the detail view for [skill]. Seeds [MainUiState.selectedSkill] with
     * the row we already have so the screen renders synchronously, then fires
     * a `skill.get` to pick up any host-side updates since the list was
     * fetched. If the refresh fails we keep the seeded copy — better than
     * bouncing the user back.
     */
    fun openSkillDetail(skill: SkillUi) {
        update { copy(screen = MainScreen.SkillDetail, selectedSkill = skill) }
        viewModelScope.launch {
            if (uiState.value.activeHost?.status != HostStatus.Connected) return@launch
            repository.getSkill(skill.id)
                .onSuccess { fresh -> update { copy(selectedSkill = fresh) } }
                .onFailure {
                    update { copy(skillsStatus = "Skill detail load failed: ${it.safeMessage()}") }
                }
        }
    }
    fun closeSkillDetail() = update {
        copy(screen = MainScreen.Skills, selectedSkill = null)
    }

    fun setConversationSearch(value: String) = update { copy(conversationSearch = value) }

    fun updateAddHostName(value: String) = update { copy(addHostName = value) }
    fun updateAddTlsCertSha256(value: String) = update { copy(addTlsCertSha256 = value.trim()) }
    fun updateAddEndpoint(value: String) = update { copy(addEndpoint = value) }
    fun updatePairCode(value: String) = update { copy(pairCode = value.filter(Char::isDigit).take(6)) }
    fun updateClientName(value: String) = update { copy(clientName = value) }
    fun updateMessageDraft(value: String) = update { copy(messageDraft = value) }

    // ---- Pairing -----------------------------------------------------------

    /**
     * Single-button add-and-pair flow. The host is **saved only on a
     * successful pair** — there is no separate "Save" action. Order:
     *
     *   1. Validate the typed endpoint and 6-digit code.
     *   2. Open the WebSocket. On failure: nothing persists, no host row
     *      appears in Settings.
     *   3. Send `auth.pair` with the code. On failure: the socket is closed,
     *      nothing persists.
     *   4. Store the host-issued token in Android Keystore.
     *   5. Persist the host config to disk and add it to the in-memory
     *      host list.
     *   6. Navigate Home.
     *
     * This means a wrong code or unreachable host never leaves a stale
     * entry in the user's host list — which was the bug in the previous
     * version where persistence happened before the pair attempt.
     *
     * If a row with the same endpoint already exists (re-pair after a
     * revoke / device wipe), we reuse its id so the user's recent-conv
     * navigation continues to work.
     */
    fun submitAddHost() {
        viewModelScope.launch {
            val state = uiState.value
            val endpoint = endpointFromInputOrReport() ?: return@launch
            if (state.pairCode.length != 6) {
                fail("Enter the 6-digit pair code shown on the desktop")
                return@launch
            }
            val existing = state.hosts.firstOrNull { it.config.endpoint == endpoint.websocketUrl() }
            // Re-pairing a saved host keeps its pinned certificate unless a
            // new fingerprint was entered.
            val candidate = existing?.config?.copy(
                tlsCertSha256 = state.addTlsCertSha256.ifBlank { existing.config.tlsCertSha256 },
            )
                ?: HostConfig(
                    id = UUID.randomUUID().toString(),
                    name = state.addHostName.trim().ifBlank { endpoint.host },
                    endpoint = endpoint.websocketUrl(),
                    tlsCertSha256 = state.addTlsCertSha256,
                )

            setBusy("Connecting")
            val connectResult = repository.connect(endpoint, candidate.tlsCertSha256)
            if (connectResult.isFailure) {
                val error = connectResult.exceptionOrNull()?.safeMessage() ?: "Connection failed"
                fail("Connection failed: $error")
                return@launch
            }

            setBusy("Pairing")
            val pairResult = repository.pair(
                code = state.pairCode,
                clientName = state.clientName.trim().ifBlank { "Android" },
            )
            val paired = pairResult.getOrElse {
                // Pair failed — close the socket so we don't leave a half-open
                // connection. Host config was never persisted, so nothing
                // shows up in Settings either.
                repository.disconnect()
                fail("Pairing failed: ${it.safeMessage()}")
                return@launch
            }

            val tokenSaved = runCatching { tokenStore.saveToken(candidate.id, paired.token) }
            if (tokenSaved.isFailure) {
                repository.disconnect()
                fail("Pairing succeeded but token storage failed: ${tokenSaved.exceptionOrNull()?.safeMessage().orEmpty()}")
                return@launch
            }

            // From here on, all writes succeed: persist the host, mark it
            // connected, navigate home. This is the only branch in this
            // function that mutates `hosts` / `activeHostId`.
            if (state.activeHostId != null && state.activeHostId != candidate.id) {
                update { withoutHostData() }
            }
            val nextConfigs = if (existing == null) {
                state.hosts.map { it.config } + candidate
            } else {
                state.hosts.map { if (it.config.id == candidate.id) candidate else it.config }
            }
            persistHosts(nextConfigs)
            val nextHosts = nextConfigs.map { config ->
                val isActive = config.id == candidate.id
                HostUi(
                    config = config,
                    status = if (isActive) HostStatus.Connected else statusFor(config.id),
                    detail = if (isActive) "Paired as ${paired.name}" else detailFor(config.id),
                    latencyMs = if (isActive) null else latencyFor(config.id),
                )
            }
            update {
                copy(
                    hosts = nextHosts,
                    activeHostId = candidate.id,
                    authenticatedName = paired.name,
                    authenticatedClientId = paired.clientId,
                    status = "Paired as ${paired.name}",
                    screen = MainScreen.Home,
                    pairCode = "",
                    busy = false,
                )
            }
            markHostConnected(candidate.id)
            refreshConversations()
            refreshClients()
            loadModelCatalog()
            loadAgents()
        }
    }

    private fun statusFor(hostId: String): HostStatus =
        uiState.value.hosts.firstOrNull { it.config.id == hostId }?.status ?: HostStatus.Disconnected

    private fun detailFor(hostId: String): String =
        uiState.value.hosts.firstOrNull { it.config.id == hostId }?.detail.orEmpty()

    private fun latencyFor(hostId: String): Long? =
        uiState.value.hosts.firstOrNull { it.config.id == hostId }?.latencyMs

    fun connectHost(hostId: String) {
        viewModelScope.launch {
            val current = uiState.value
            val host = current.hosts.firstOrNull { it.config.id == hostId }?.config
            if (host == null) {
                fail("Host does not exist")
                return@launch
            }
            // No-op when this host is already the active, fully-connected
            // session. Tapping it from "Other hosts" shouldn't tear down a
            // working socket and bounce the user into the AddHost flow,
            // which is what was happening because Other Hosts briefly
            // listed the active host during transient disconnect frames.
            if (current.activeHostId == hostId &&
                current.activeHost?.status == HostStatus.Connected &&
                current.connectionState == com.verzeta.android.remote.ConnectionState.Open
            ) {
                return@launch
            }

            setHostStatus(hostId, HostStatus.Disconnected, "Connecting")
            setBusy("Connecting to ${host.name}")

            val endpoint = runCatching { RemoteEndpoint.fromEndpoint(host.endpoint) }
                .onFailure {
                    setHostStatus(hostId, HostStatus.Error, it.safeMessage())
                    fail("Invalid host endpoint: ${it.safeMessage()}")
                }
                .getOrNull() ?: return@launch

            repository.connect(endpoint, host.tlsCertSha256)
                .onFailure {
                    setHostStatus(hostId, HostStatus.Error, it.safeMessage())
                    fail("Connection failed: ${it.safeMessage()}")
                }
                .onSuccess {
                    // Switching to another host: drop everything loaded from
                    // the previous one, so its folders, members and chats do
                    // not show under the new host.
                    update {
                        if (activeHostId != null && activeHostId != hostId) {
                            withoutHostData().copy(activeHostId = hostId)
                        } else {
                            copy(activeHostId = hostId)
                        }
                    }
                    val token = runCatching { tokenStore.readToken(hostId) }
                        .onFailure {
                            setHostStatus(hostId, HostStatus.Error, it.safeMessage())
                            fail("Token store read failed: ${it.safeMessage()}")
                        }
                        .getOrNull()
                    if (token.isNullOrBlank()) {
                        // Host config exists but the token is gone — this
                        // happens after `auth.revoke_self` or after a fresh
                        // backup-restore on a new device. The user has to
                        // re-pair. Route to AddHost ONLY when explicitly
                        // requested via this path, never as a side effect
                        // of a transient connect attempt elsewhere.
                        setHostStatus(hostId, HostStatus.PairingRequired, "Pairing code required")
                        update {
                            copy(
                                status = "Token missing for ${host.name}. Enter a fresh pair code from the desktop.",
                                screen = MainScreen.AddHost,
                                addHostName = host.name,
                                addEndpoint = host.endpoint,
                                addTlsCertSha256 = host.tlsCertSha256,
                                pairCode = "",
                                busy = false,
                            )
                        }
                    } else {
                        authenticateWithToken(hostId, token)
                    }
                }
        }
    }

    fun ping() {
        viewModelScope.launch {
            setBusy("Pinging")
            // Round-trip latency = the time we wait for the host to echo
            // back. The host's ping op returns its current wall-clock
            // (time_ms) which is NOT meaningful as latency — the prior
            // build was rendering the wall-clock as "ping" and showing
            // unix timestamps as the latency. Now we measure locally.
            val sentAt = System.currentTimeMillis()
            repository.ping()
                .onFailure { fail("Ping failed: ${it.safeMessage()}") }
                .onSuccess { pong ->
                    val latencyMs = System.currentTimeMillis() - sentAt
                    val hostId = uiState.value.activeHostId
                    if (hostId != null) setHostLatency(hostId, latencyMs)
                    if (pong.hostAlive) {
                        update { copy(status = "Host ping: ${latencyMs} ms", busy = false) }
                    } else {
                        reportDesktopNotRunning(hostId)
                    }
                }
        }
    }

    fun revokeSelf() {
        viewModelScope.launch {
            val hostId = uiState.value.activeHostId
            if (hostId == null) {
                fail("No active host")
                return@launch
            }
            setBusy("Revoking")
            repository.revokeSelf()
                .onFailure { fail("Revoke failed: ${it.safeMessage()}") }
                .onSuccess {
                    runCatching { tokenStore.clearToken(hostId) }
                        .onFailure {
                            fail("Revoke succeeded but local token clear failed: ${it.safeMessage()}")
                            return@launch
                        }
                    setHostStatus(hostId, HostStatus.AuthRequired, "Token revoked")
                    update {
                        copy(
                            authenticatedName = "",
                            status = "This device token was revoked",
                            conversations = emptyList(),
                            recentConversations = emptyList(),
                            selectedConversationId = null,
                            messages = emptyList(),
                            toolActivity = emptyList(),
                            messageDraft = "",
                            chatStatus = "Reconnect and authenticate to load conversations",
                            clients = emptyList(),
                            settingsStatus = "Reconnect and authenticate to load paired clients",
                            modelCatalog = ModelCatalogUi(),
                            agents = emptyList(),
                            folderMembers = emptyMap(),
                            conversationMembers = emptyMap(),
                            convSettings = emptyMap(),
                            convPlans = emptyMap(),
                            taskStartingConvId = null,
                            convToolCalls = emptyMap(),
                            pendingToolConfirmation = null,
                            agentSteps = emptyList(),
                            agentRunState = AgentRunStateUi(),
                            convArtifacts = emptyMap(),
                            convImages = emptyMap(),
                            convAudio = emptyMap(),
                            pendingAttachments = emptyList(),
                            convCanvas = emptyMap(),
                            convCanvasHistory = emptyMap(),
                            canvasAiActionsByLang = emptyMap(),
                            canvasToolsByLang = emptyMap(),
                            consoleLines = emptyList(),
                            consoleVisible = false,
                            canvasRunState = CanvasRunStateUi(),
                            canvasSandbox = CanvasSandboxStateUi(),
                            canvasRunSupportedLanguages = emptyList(),
                            canvasRunSupportedIdeLanguages = emptyList(),
                            folderDocuments = emptyMap(),
                            folderHeartbeats = emptyMap(),
                            recentHeartbeatRuns = emptyList(),
                            heartbeatNextFires = emptyList(),
                            recentHeartbeatChanges = emptyList(),
                            folderPreferredSkills = emptyMap(),
                            pendingKickoff = null,
                            tools = emptyList(),
                            mcpServers = emptyList(),
                            mcpServerTools = emptyMap(),
                            activeMcpServerName = null,
                            skills = emptyList(),
                            selectedSkill = null,
                            busy = false,
                        )
                    }
                }
        }
    }

    fun removeHost(hostId: String) {
        viewModelScope.launch {
            runCatching { tokenStore.clearToken(hostId) }
                .onFailure {
                    fail("Token clear failed: ${it.safeMessage()}")
                    return@launch
                }
            val configs = uiState.value.hosts.map { it.config }.filterNot { it.id == hostId }
            persistHosts(configs)
            if (uiState.value.activeHostId == hostId) repository.disconnect()
            update {
                val isActive = activeHostId == hostId
                copy(
                    hosts = hosts.filterNot { it.config.id == hostId },
                    activeHostId = activeHostId.takeUnless { it == hostId },
                    conversations = if (isActive) emptyList() else conversations,
                    recentConversations = if (isActive) emptyList() else recentConversations,
                    selectedConversationId = if (isActive) null else selectedConversationId,
                    messages = if (isActive) emptyList() else messages,
                    toolActivity = if (isActive) emptyList() else toolActivity,
                    clients = if (isActive) emptyList() else clients,
                    modelCatalog = if (isActive) ModelCatalogUi() else modelCatalog,
                    agents = if (isActive) emptyList() else agents,
                    folderMembers = if (isActive) emptyMap() else folderMembers,
                    conversationMembers = if (isActive) emptyMap() else conversationMembers,
                    convSettings = if (isActive) emptyMap() else convSettings,
                    convPlans = if (isActive) emptyMap() else convPlans,
                    taskStartingConvId = if (isActive) null else taskStartingConvId,
                    convToolCalls = if (isActive) emptyMap() else convToolCalls,
                    pendingToolConfirmation = if (isActive) null else pendingToolConfirmation,
                    agentSteps = if (isActive) emptyList() else agentSteps,
                    agentRunState = if (isActive) AgentRunStateUi() else agentRunState,
                    convArtifacts = if (isActive) emptyMap() else convArtifacts,
                    convImages = if (isActive) emptyMap() else convImages,
                    convAudio = if (isActive) emptyMap() else convAudio,
                    pendingAttachments = if (isActive) emptyList() else pendingAttachments,
                    convCanvas = if (isActive) emptyMap() else convCanvas,
                    convCanvasHistory = if (isActive) emptyMap() else convCanvasHistory,
                    consoleLines = if (isActive) emptyList() else consoleLines,
                    consoleVisible = if (isActive) false else consoleVisible,
                    canvasRunState = if (isActive) CanvasRunStateUi() else canvasRunState,
                    folderDocuments = if (isActive) emptyMap() else folderDocuments,
                    folderHeartbeats = if (isActive) emptyMap() else folderHeartbeats,
                    recentHeartbeatRuns = if (isActive) emptyList() else recentHeartbeatRuns,
                    heartbeatNextFires = if (isActive) emptyList() else heartbeatNextFires,
                    recentHeartbeatChanges = if (isActive) emptyList() else recentHeartbeatChanges,
                    folderPreferredSkills = if (isActive) emptyMap() else folderPreferredSkills,
                    pendingKickoff = if (isActive) null else pendingKickoff,
                    tools = if (isActive) emptyList() else tools,
                    mcpServers = if (isActive) emptyList() else mcpServers,
                    mcpServerTools = if (isActive) emptyMap() else mcpServerTools,
                    activeMcpServerName = if (isActive) null else activeMcpServerName,
                    skills = if (isActive) emptyList() else skills,
                    selectedSkill = if (isActive) null else selectedSkill,
                    status = "Host removed",
                    settingsStatus = "Host removed",
                    busy = false,
                )
            }
        }
    }

    /**
     * Copy of this state with everything loaded from a host cleared:
     * conversations, folders and members, per-conversation caches,
     * catalogs and templates. Host list, pairing form and navigation are
     * kept. Used when the active host changes.
     */
    private fun MainUiState.withoutHostData(): MainUiState = copy(
        conversations = emptyList(),
        recentConversations = emptyList(),
        selectedConversationId = null,
        messages = emptyList(),
        toolActivity = emptyList(),
        messageDraft = "",
        contextFillPercents = emptyMap(),
        deletingConversationIds = emptySet(),
        clients = emptyList(),
        modelCatalog = ModelCatalogUi(),
        searchProviders = SearchProvidersUi(),
        agents = emptyList(),
        folders = emptyList(),
        selectedFolderId = null,
        folderMembers = emptyMap(),
        conversationMembers = emptyMap(),
        folderDocuments = emptyMap(),
        folderHeartbeats = emptyMap(),
        folderPreferredSkills = emptyMap(),
        convSettings = emptyMap(),
        convPlans = emptyMap(),
        taskStartingConvId = null,
        convToolCalls = emptyMap(),
        pendingToolConfirmation = null,
        agentSteps = emptyList(),
        agentRunState = AgentRunStateUi(),
        convArtifacts = emptyMap(),
        convImages = emptyMap(),
        convAudio = emptyMap(),
        convPolls = emptyMap(),
        pendingAttachments = emptyList(),
        convCanvas = emptyMap(),
        convCanvasHistory = emptyMap(),
        canvasAiActionsByLang = emptyMap(),
        canvasToolsByLang = emptyMap(),
        consoleLines = emptyList(),
        consoleVisible = false,
        canvasRunState = CanvasRunStateUi(),
        activityLog = emptyList(),
        recentHeartbeatRuns = emptyList(),
        heartbeatNextFires = emptyList(),
        recentHeartbeatChanges = emptyList(),
        pendingKickoff = null,
        tools = emptyList(),
        mcpServers = emptyList(),
        mcpServerTools = emptyMap(),
        activeMcpServerName = null,
        skills = emptyList(),
        selectedSkill = null,
        projectTemplates = emptyList(),
        landingProjectTemplates = emptyList(),
        projectTemplateRosters = emptyMap(),
    )

    // ---- Conversations -----------------------------------------------------

    fun refreshConversations() {
        viewModelScope.launch {
            val hostId = uiState.value.activeHostId
            if (hostId == null || uiState.value.activeHost?.status != HostStatus.Connected) {
                update { copy(chatStatus = "Connect a host before loading conversations", busy = false) }
                return@launch
            }
            setBusy("Loading conversations")
            repository.listConversations()
                .onFailure { fail("Conversation load failed: ${it.safeMessage()}") }
                .onSuccess { conversations ->
                    update {
                        copy(
                            conversations = conversations,
                            recentConversations = conversations.take(8).map { it.toRecent(hostId) },
                            chatStatus = if (conversations.isEmpty()) "No conversations on this host"
                            else "Loaded ${conversations.size} conversation${if (conversations.size == 1) "" else "s"}",
                            status = "Loaded ${conversations.size} conversation${if (conversations.size == 1) "" else "s"}",
                            busy = false,
                        )
                    }
                    // The bucketed Conversations browser shows each project /
                    // org folder's members inline. Kick a refreshFolders +
                    // per-folder member load so rows have data on first paint
                    // instead of after the user taps a folder.
                    primeProjectFoldersForSidebar()
                }
        }
    }

    /**
     * Ensure `state.folders` is loaded AND each project / organization folder
     * has its `folderMembers[folderId]` populated, so the bucketed
     * Conversations browser can render TEAM MEMBERS rows without a per-row
     * lazy fetch. Called from [refreshConversations] and on host (re-)connect.
     * Idempotent — no-op when already loaded.
     */
    private fun primeProjectFoldersForSidebar() {
        viewModelScope.launch {
            if (uiState.value.activeHost?.status != HostStatus.Connected) return@launch
            // Load folders if we have none cached yet. refreshFolders
            // handles its own status messaging.
            if (uiState.value.folders.isEmpty()) {
                refreshFolders()
            }
            // Fan out folder.members for projects + orgs that we don't
            // already have cached. The cache holds onto previously-loaded
            // folders' members across navigations so this is cheap on
            // repeat invocations.
            val cached = uiState.value.folderMembers.keys
            val missing = uiState.value.folders
                .filter { it.folderType != com.verzeta.android.data.folder.FolderType.Regular }
                .map { it.id }
                .filter { it !in cached }
            for (id in missing) {
                loadFolderMembers(id)
            }
        }
    }

    /**
     * Toggle collapse state for a section header or a folder header in the
     * bucketed Conversations browser. The same set holds both (section ids
     * are "section:…", folder ids are UUIDs — they never collide).
     */
    fun toggleSidebarCollapsed(id: String) {
        if (id.isBlank()) return
        update {
            copy(
                collapsedSidebarIds = if (id in collapsedSidebarIds)
                    collapsedSidebarIds - id
                else
                    collapsedSidebarIds + id,
            )
        }
    }

    // ---- Project Rooms -----------------------------------------------------

    /**
     * Open the Project Rooms landing overlay. Refreshes the bounded
     * landing catalog (project_template.list_landing) so the page has
     * fresh data; the full catalog loads on-demand from
     * [showTemplateLibrary]. Safe on disconnected host — the screen
     * shows an empty state.
     */
    fun showProjectRoomsLanding() {
        update { copy(screen = MainScreen.ProjectRoomsLanding) }
        refreshLandingProjectTemplates()
    }

    fun closeProjectRoomsLanding() = update {
        copy(screen = MainScreen.Home, selectedProjectTemplateId = null)
    }

    fun showTemplateLibrary() {
        update { copy(screen = MainScreen.TemplateLibrary) }
        refreshProjectTemplates()
    }

    fun closeTemplateLibrary() = update {
        copy(screen = MainScreen.ProjectRoomsLanding)
    }

    /**
     * Pick a template and open the Quick Start customise screen.
     * Seeds [projectTemplateDraft] with the template's own defaults so
     * the form pre-fills, kicks a roster fetch in parallel.
     */
    fun selectProjectTemplate(templateId: String) {
        if (templateId.isBlank()) return
        val template = uiState.value.projectTemplates.firstOrNull { it.id == templateId }
            ?: uiState.value.landingProjectTemplates.firstOrNull { it.id == templateId }
        update {
            copy(
                screen = MainScreen.ProjectCreate,
                selectedProjectTemplateId = templateId,
                projectTemplateDraft = com.verzeta.android.data.projectroom.TemplateCustomisationsUi(
                    name = template?.name.orEmpty(),
                    goal = template?.goal.orEmpty(),
                    description = template?.description.orEmpty(),
                    scenario = template?.scenario.orEmpty(),
                    members = null,  // start with template default; user can edit
                ),
            )
        }
        loadProjectTemplateRoster(templateId)
    }

    fun closeProjectCreate() = update {
        copy(
            screen = MainScreen.ProjectRoomsLanding,
            selectedProjectTemplateId = null,
            projectTemplateDraft = null,
        )
    }

    fun updateProjectTemplateDraft(
        transform: (com.verzeta.android.data.projectroom.TemplateCustomisationsUi)
            -> com.verzeta.android.data.projectroom.TemplateCustomisationsUi,
    ) {
        update {
            val base = projectTemplateDraft
                ?: com.verzeta.android.data.projectroom.TemplateCustomisationsUi()
            copy(projectTemplateDraft = transform(base))
        }
    }

    /**
     * `project_template.project_created` payloads seen recently, keyed by
     * folder id. The event is broadcast to every client, so it is only used
     * to enrich this client's own create (see [createProjectFromTemplate]),
     * never to navigate on its own.
     */
    private val projectCreatedEvents =
        LinkedHashMap<String, com.verzeta.android.data.projectroom.ProjectTemplateCreatedUi>()

    /**
     * Invoke `project_template.create_project` with the in-flight draft.
     *
     * On success the app goes straight to the Folders screen with the
     * kickoff sheet for the new folder, so a second tap cannot create a
     * duplicate. The folder name and member count come from the host's
     * `project_template.project_created` event for that folder when it has
     * already arrived, otherwise from the draft.
     */
    fun createProjectFromTemplate() {
        val id = uiState.value.selectedProjectTemplateId?.takeIf { it.isNotBlank() } ?: return
        val draft = uiState.value.projectTemplateDraft
            ?: com.verzeta.android.data.projectroom.TemplateCustomisationsUi()
        viewModelScope.launch {
            if (uiState.value.activeHost?.status != HostStatus.Connected) {
                fail("Connect a host before creating a project")
                return@launch
            }
            update { copy(projectTemplateCreating = true) }
            repository.createProjectFromTemplate(id, draft)
                .onSuccess { folderId ->
                    val fromEvent = projectCreatedEvents.remove(folderId)
                    val created = fromEvent
                        ?: com.verzeta.android.data.projectroom.ProjectTemplateCreatedUi(
                            folderId = folderId,
                            folderName = draft.name.ifBlank { "New project" },
                            memberCount = draft.members?.size
                                ?: uiState.value.projectTemplateRosters[id]?.size
                                ?: 0,
                        )
                    update {
                        copy(
                            projectTemplateCreating = false,
                            projectTemplateCreated = created,
                        )
                    }
                    consumeProjectTemplateCreated()
                }
                .onFailure {
                    update { copy(projectTemplateCreating = false) }
                    fail("Create project failed: ${it.safeMessage()}")
                }
        }
    }

    /**
     * Consume the one-shot create-success in
     * [MainUiState.projectTemplateCreated]: navigate to the Folders screen
     * with the kickoff sheet pre-loaded for the new folder. Called by
     * [createProjectFromTemplate] on success; also safe to call from UI,
     * where it is a no-op when there is nothing to consume.
     */
    fun consumeProjectTemplateCreated() {
        val created = uiState.value.projectTemplateCreated ?: return
        update {
            copy(
                projectTemplateCreated = null,
                selectedProjectTemplateId = null,
                projectTemplateDraft = null,
                screen = MainScreen.Folders,
                pendingKickoff = PendingKickoff(
                    folderId = created.folderId,
                    folderName = created.folderName,
                    memberCount = created.memberCount,
                ),
            )
        }
        // Make sure the folder data is in cache so the kickoff sheet's
        // FolderEditorSheet (or whichever surface consumes pendingKickoff)
        // has a populated FolderUi to render.
        loadFolderInfo(created.folderId)
        loadFolderMembers(created.folderId)
    }

    fun pinProjectTemplate(templateId: String, pinned: Boolean) {
        if (templateId.isBlank()) return
        viewModelScope.launch {
            repository.setProjectTemplatePinned(templateId, pinned)
                .onSuccess { ok -> if (!ok) fail("Pin template failed: only your own templates can be pinned") }
                .onFailure { fail("Pin template failed: ${it.safeMessage()}") }
            // catalog_changed event refreshes the list.
        }
    }

    fun deleteUserProjectTemplate(templateId: String) {
        if (templateId.isBlank()) return
        viewModelScope.launch {
            repository.deleteUserProjectTemplate(templateId)
                .onSuccess { ok -> if (!ok) fail("Delete template failed: the host did not delete it") }
                .onFailure { fail("Delete template failed: ${it.safeMessage()}") }
            // catalog_changed event refreshes the list.
        }
    }

    /**
     * Save the current template (or a neutral default when no template
     * is selected) as a new USER template using the in-flight draft.
     * Re-uses the same [projectTemplateDraft] the Create form is using.
     */
    fun saveCurrentDraftAsNewTemplate(onSaved: (newId: String?) -> Unit = {}) {
        val sourceId = uiState.value.selectedProjectTemplateId.orEmpty()
        val edits = uiState.value.projectTemplateDraft
            ?: com.verzeta.android.data.projectroom.TemplateCustomisationsUi()
        viewModelScope.launch {
            repository.saveAsNewProjectTemplate(sourceId, edits)
                .onSuccess { newId -> onSaved(newId) }
                .onFailure {
                    fail("Save template failed: ${it.safeMessage()}")
                    onSaved(null)
                }
            // catalog_changed event refreshes the list.
        }
    }

    /** Refresh the bounded landing template list. */
    fun refreshLandingProjectTemplates() {
        viewModelScope.launch {
            if (uiState.value.activeHost?.status != HostStatus.Connected) return@launch
            repository.listLandingProjectTemplates()
                .onSuccess { list ->
                    update {
                        copy(
                            landingProjectTemplates = list,
                            projectTemplatesStatus = "Loaded ${list.size} landing template${if (list.size == 1) "" else "s"}",
                        )
                    }
                }
                .onFailure {
                    update { copy(projectTemplatesStatus = "Landing load failed: ${it.safeMessage()}") }
                }
        }
    }

    /** Refresh the full template catalog (Template Library). */
    fun refreshProjectTemplates() {
        viewModelScope.launch {
            if (uiState.value.activeHost?.status != HostStatus.Connected) return@launch
            repository.listProjectTemplates()
                .onSuccess { list ->
                    update {
                        copy(
                            projectTemplates = list,
                            projectTemplatesStatus = "Loaded ${list.size} template${if (list.size == 1) "" else "s"}",
                        )
                    }
                }
                .onFailure {
                    update { copy(projectTemplatesStatus = "Catalog load failed: ${it.safeMessage()}") }
                }
        }
    }

    /** Load (and cache) the resolved roster for one template. */
    private fun loadProjectTemplateRoster(templateId: String) {
        if (templateId.isBlank()) return
        if (uiState.value.projectTemplateRosters.containsKey(templateId)) return
        viewModelScope.launch {
            if (uiState.value.activeHost?.status != HostStatus.Connected) return@launch
            repository.getProjectTemplateRoster(templateId)
                .onSuccess { roster ->
                    update {
                        copy(projectTemplateRosters = projectTemplateRosters + (templateId to roster))
                    }
                }
        }
    }

    /**
     * Create a new conversation. Host returns `{id}` only — the actual row
     * arrives via the `conv.added` event. We wait for the event rather
     * than synthesizing a placeholder; on local LAN this is ~50 ms and
     * avoids reconciliation churn.
     */
    fun createConversation(title: String? = null, openOnReady: Boolean = true) {
        viewModelScope.launch {
            if (uiState.value.activeHost?.status != HostStatus.Connected) {
                fail("Connect a host before creating a conversation")
                return@launch
            }
            setBusy("Creating conversation")
            repository.createConversation(title)
                .onFailure { fail("Conversation create failed: ${it.safeMessage()}") }
                .onSuccess { id ->
                    update {
                        copy(
                            chatStatus = "Conversation created",
                            status = "Conversation created",
                            busy = false,
                        )
                    }
                    if (openOnReady) {
                        // Open immediately. listMessages will return empty
                        // until the first message is sent. The conv.added
                        // event will populate the conversations list shortly
                        // after; openConversation re-fetches the metadata
                        // via conv.get so the title is correct either way.
                        openConversation(id)
                    }
                }
        }
    }

    /** Optimistic rename — update locally, host echo via `conv.updated` reconciles. */
    fun renameConversation(id: String, title: String) {
        if (title.isBlank()) return
        viewModelScope.launch {
            update {
                copy(conversations = conversations.map {
                    if (it.id == id) it.copy(title = title) else it
                })
            }
            repository.renameConversation(id, title)
                .onFailure { fail("Rename failed: ${it.safeMessage()}") }
        }
    }

    /** Optimistic pin toggle — host echo via `conv.updated` reconciles. */
    fun setConversationPinned(id: String, pinned: Boolean) {
        viewModelScope.launch {
            update {
                copy(conversations = conversations.map {
                    if (it.id == id) it.copy(isPinned = pinned) else it
                })
            }
            repository.setConversationPinned(id, pinned)
                .onFailure { fail("Pin failed: ${it.safeMessage()}") }
        }
    }

    /**
     * Fire-and-forget delete. Mark the row as "deleting" (greyed out in
     * the UI) immediately; on the ack response, remove the row + clear
     * the flag optimistically. The `conv.deleted` event handler is
     * idempotent (filterNot), so a subsequent event becomes a no-op.
     *
     * The success path also clears the deleting flag directly rather than
     * waiting solely on the `conv.deleted` event; if the event never arrives
     * (socket race, subscription window missed the delete) the row would
     * otherwise remain greyed-out until app restart. The event handler is
     * idempotent, so both paths converging is safe.
     */
    fun deleteConversation(id: String) {
        viewModelScope.launch {
            update { copy(deletingConversationIds = deletingConversationIds + id) }
            repository.deleteConversation(id)
                .onFailure {
                    update { copy(deletingConversationIds = deletingConversationIds - id) }
                    fail("Delete failed: ${it.safeMessage()}")
                    return@launch
                }
            // Success → clear the row + flag optimistically. The
            // conv.deleted event handler is idempotent (filterNot) so
            // a subsequent event is harmless. Mirror the same cleanup
            // shape the event handler does so the two paths converge.
            update {
                copy(
                    conversations = conversations.filterNot { it.id == id },
                    deletingConversationIds = deletingConversationIds - id,
                    selectedConversationId = selectedConversationId?.takeUnless { it == id },
                    messages = if (selectedConversationId == id) emptyList() else messages,
                )
            }
        }
    }

    /**
     * Move a conversation to the given folder, or to root by passing an
     * empty string. Optimistic update — host echo via `conv.updated`
     * reconciles. On failure we revert the optimistic state so the row
     * doesn't remain bucketed in the wrong section.
     */
    fun moveConversationToFolder(conversationId: String, folderId: String) {
        viewModelScope.launch {
            val original = uiState.value.conversations.firstOrNull { it.id == conversationId }
            update {
                copy(conversations = conversations.map {
                    if (it.id == conversationId) it.copy(folderId = folderId.ifBlank { null }) else it
                })
            }
            repository.moveConversationToFolder(conversationId, folderId)
                .onFailure { error ->
                    if (original != null) {
                        update {
                            copy(conversations = conversations.map {
                                if (it.id == conversationId) original else it
                            })
                        }
                    }
                    fail("Move failed: ${error.safeMessage()}")
                }
        }
    }

    /** Conversation to open once the host is connected (a mention tap on cold start). */
    private var pendingOpenConversationId: String? = null

    /**
     * Open [conversationId] now if the host is connected, otherwise as soon
     * as authentication succeeds. Used for mention-notification taps, which
     * can arrive before the app has reconnected.
     */
    fun openConversationWhenConnected(conversationId: String) {
        if (conversationId.isBlank()) return
        if (uiState.value.activeHost?.status == HostStatus.Connected) {
            openConversation(conversationId)
        } else {
            pendingOpenConversationId = conversationId
        }
    }

    fun openConversation(conversationId: String) {
        viewModelScope.launch {
            if (uiState.value.activeHost?.status != HostStatus.Connected) {
                update { copy(chatStatus = "Connect a host before opening a conversation", busy = false) }
                return@launch
            }

            val previous = uiState.value.selectedConversationId
            if (previous != null && previous != conversationId) {
                repository.unsubscribeMessages(previous)
            }

            update {
                copy(
                    screen = MainScreen.Chat,
                    selectedConversationId = conversationId,
                    messages = emptyList(),
                    toolActivity = emptyList(),
                    messageDraft = "",
                    chatStatus = "Loading messages",
                    // Steps belong to the previous conversation's run.
                    agentSteps = if (previous != conversationId) emptyList() else agentSteps,
                )
            }
            setBusy("Loading messages")

            // Read the conv's saved provider+model from its llm_config —
            // populates the chat header + model picker without touching
            // the host's global active. The host has a single active
            // conv shared between desktop and any remote clients;
            // calling switchConversation here would hijack the desktop
            // user's view every time the Android user navigated. Send
            // is the only path that briefly flips host state (msg.send
            // does its own switchConversation just-in-time).
            repository.readConversationModel(conversationId).onSuccess { (prov, model) ->
                if (prov.isNotBlank() && model.isNotBlank()) {
                    update {
                        copy(modelCatalog = modelCatalog.copy(
                            activeProvider = prov,
                            activeModel    = model,
                        ))
                    }
                }
            }

            // conv.get refreshes the single conversation's metadata in case
            // the cached list is stale (title rename, pin toggle, agent
            // assignment from another client). Failures are non-fatal — the
            // existing list entry is good enough.
            repository.getConversation(conversationId).onSuccess { fresh ->
                update {
                    copy(
                        conversations = conversations.map { if (it.id == fresh.id) fresh else it },
                    )
                }
            }

            repository.listMessages(conversationId)
                .onFailure {
                    update { copy(chatStatus = "Could not load messages") }
                    fail("Message load failed: ${it.safeMessage()}")
                }
                .onSuccess { all ->
                    repository.subscribeMessages(conversationId)
                    val sorted = all.sortedBy { it.createdAt }
                    val (visible, hidden) = sorted.partition { it.isVisibleInChat() }
                    val activity = hidden.map { it.toToolActivity() }
                    update {
                        copy(
                            messages = visible,
                            toolActivity = activity,
                            chatStatus = if (visible.isEmpty()) "No messages in this conversation"
                            else "Loaded ${visible.size} message${if (visible.size == 1) "" else "s"}",
                            status = "Conversation opened",
                            busy = false,
                        )
                    }
                    // Fetch existing polls for this conversation.
                    refreshPollsForConv(conversationId)
                }
        }
    }

    /**
     * Manual "Verify session" action surfaced in Settings. Exercises the
     * `auth.me` op explicitly and updates `last_seen_at` shown in the UI.
     */
    fun verifySession() {
        viewModelScope.launch {
            if (uiState.value.activeHost?.status != HostStatus.Connected) {
                fail("Connect a host before verifying the session")
                return@launch
            }
            setBusy("Verifying session")
            repository.authMe()
                .onFailure { fail("Session check failed: ${it.safeMessage()}") }
                .onSuccess { me ->
                    update {
                        copy(
                            authenticatedName = me.name,
                            authenticatedClientId = me.clientId,
                            authenticatedLastSeenAt = me.lastSeenAt ?: authenticatedLastSeenAt,
                            status = "Session verified for ${me.name}",
                            busy = false,
                        )
                    }
                }
        }
    }

    fun closeConversation() {
        viewModelScope.launch {
            uiState.value.selectedConversationId?.let { repository.unsubscribeMessages(it) }
            update {
                copy(
                    selectedConversationId = null,
                    messages = emptyList(),
                    toolActivity = emptyList(),
                    messageDraft = "",
                    chatStatus = "Select a conversation",
                )
            }
        }
    }


    fun showConvSettings() {
        val convId = uiState.value.selectedConversationId ?: return
        update { copy(screen = MainScreen.ConvSettings) }
        loadConvSettings(convId)
    }
    fun closeConvSettings() = update { copy(screen = MainScreen.Chat) }

    fun loadConvSettings(conversationId: String) {
        if (conversationId.isBlank()) return
        viewModelScope.launch {
            if (uiState.value.activeHost?.status != HostStatus.Connected) return@launch
            repository.getConversationSettings(conversationId)
                .onSuccess { loaded ->
                    // conv.settings.get reports RAG as off whatever is
                    // stored, so take the flag from the conversation's own
                    // llm_config (conv.get). If that read fails, keep what
                    // conv.settings.get said.
                    val rag = repository.getConversationRagEnabled(conversationId).getOrNull()
                    val settings = if (rag != null) loaded.copy(ragEnabled = rag) else loaded
                    update {
                        copy(convSettings = convSettings + (conversationId to settings))
                    }
                }
                .onFailure { fail("Conversation settings load failed: ${it.safeMessage()}") }
        }
    }

    /**
     * Apply a local edit immediately (so the panel reflects the change
     * without waiting for the host's `conv.updated` echo), then fire
     * `conv.settings.save` with only [partial] fields. Caller is
     * responsible for debouncing free-form text inputs (the UI uses a
     * 400 ms debounce per the desktop's pattern).
     */
    fun saveConvSettings(
        conversationId: String,
        systemPrompt: String? = null,
        temperature: Double? = null,
        maxTokens: Int? = null,
        contextWindow: Int? = null,
        streaming: Boolean? = null,
        thinking: Boolean? = null,
        // Extended host sampling settings.
        topK: Double? = null,
        topP: Double? = null,
        repeatPenalty: Double? = null,
        presencePenalty: Double? = null,
        frequencyPenalty: Double? = null,
        forceAppSampling: Boolean? = null,
        toolsInSystemPrompt: Boolean? = null,
        dynamicCompactEnabled: Boolean? = null,
        compactEveryTurns: Int? = null,
    ) {
        viewModelScope.launch {
            // Local optimistic update.
            update {
                val existing = convSettings[conversationId] ?: ConvSettingsUi()
                copy(
                    convSettings = convSettings + (conversationId to existing.copy(
                        systemPrompt = systemPrompt ?: existing.systemPrompt,
                        temperature = temperature ?: existing.temperature,
                        maxTokens = maxTokens ?: existing.maxTokens,
                        contextWindow = contextWindow ?: existing.contextWindow,
                        streaming = streaming ?: existing.streaming,
                        thinking = thinking ?: existing.thinking,
                        topK = topK ?: existing.topK,
                        topP = topP ?: existing.topP,
                        repeatPenalty = repeatPenalty ?: existing.repeatPenalty,
                        presencePenalty = presencePenalty ?: existing.presencePenalty,
                        frequencyPenalty = frequencyPenalty ?: existing.frequencyPenalty,
                        forceAppSampling = forceAppSampling ?: existing.forceAppSampling,
                        toolsInSystemPrompt = toolsInSystemPrompt ?: existing.toolsInSystemPrompt,
                        dynamicCompactEnabled = dynamicCompactEnabled ?: existing.dynamicCompactEnabled,
                        compactEveryTurns = compactEveryTurns ?: existing.compactEveryTurns,
                    )),
                )
            }
            repository.saveConversationSettings(
                conversationId = conversationId,
                systemPrompt = systemPrompt,
                temperature = temperature,
                maxTokens = maxTokens,
                contextWindow = contextWindow,
                streaming = streaming,
                thinking = thinking,
                topK = topK,
                topP = topP,
                repeatPenalty = repeatPenalty,
                presencePenalty = presencePenalty,
                frequencyPenalty = frequencyPenalty,
                forceAppSampling = forceAppSampling,
                toolsInSystemPrompt = toolsInSystemPrompt,
                dynamicCompactEnabled = dynamicCompactEnabled,
                compactEveryTurns = compactEveryTurns,
            ).onFailure {
                fail("Settings save failed: ${it.safeMessage()}")
                // Re-fetch on failure so the optimistic update doesn't drift.
                loadConvSettings(conversationId)
            }
        }
    }

    fun setConvPrimaryAgent(conversationId: String, agentId: String) {
        viewModelScope.launch {
            update {
                val existing = convSettings[conversationId] ?: ConvSettingsUi()
                copy(
                    convSettings = convSettings + (conversationId to existing.copy(primaryAgentId = agentId)),
                )
            }
            repository.setConversationPrimaryAgent(conversationId, agentId)
                .onFailure {
                    fail("Primary agent save failed: ${it.safeMessage()}")
                    loadConvSettings(conversationId)
                }
        }
    }

    /**
     * Toggle the per-conversation heartbeat auto-surface gate. When
     * enabling for the first time we query
     * `heartbeat.suggested_cap_for_conv` to seed an intelligent default
     * (matches QML's default-cap behaviour); the user can adjust the cap
     * separately via [setConvHeartbeatGateCap].
     */
    fun setConvHeartbeatGate(conversationId: String, allow: Boolean) {
        viewModelScope.launch {
            val current = uiState.value.convSettings[conversationId] ?: ConvSettingsUi()
            val cap = if (allow && current.autoSurfaceMaxPerDay <= 0) {
                repository.heartbeatSuggestedCapForConv(conversationId).getOrNull() ?: 1
            } else {
                current.autoSurfaceMaxPerDay.coerceIn(1, 24)
            }
            update {
                val existing = convSettings[conversationId] ?: ConvSettingsUi()
                copy(
                    convSettings = convSettings + (conversationId to existing.copy(
                        heartbeatAutoSurface = allow,
                        autoSurfaceMaxPerDay = cap,
                    )),
                )
            }
            repository.setConversationHeartbeatGate(conversationId, allow, cap)
                .onFailure {
                    fail("Heartbeat gate save failed: ${it.safeMessage()}")
                    loadConvSettings(conversationId)
                }
        }
    }

    fun setConvHeartbeatGateCap(conversationId: String, cap: Int) {
        val clamped = cap.coerceIn(1, 24)
        viewModelScope.launch {
            val current = uiState.value.convSettings[conversationId] ?: ConvSettingsUi()
            update {
                val existing = convSettings[conversationId] ?: ConvSettingsUi()
                copy(
                    convSettings = convSettings + (conversationId to existing.copy(
                        autoSurfaceMaxPerDay = clamped,
                    )),
                )
            }
            repository.setConversationHeartbeatGate(conversationId, current.heartbeatAutoSurface, clamped)
                .onFailure {
                    fail("Heartbeat gate save failed: ${it.safeMessage()}")
                    loadConvSettings(conversationId)
                }
        }
    }

    // ---- Per-conversation agent settings ---------------------------------
    //
    // agent_pattern / require_confirmation / tools_enabled are per-conversation
    // settings stored in llm_config. The wire ops require an explicit conv_id;
    // these setters update only that conversation's cached settings and write
    // through the wire to that conv's row. RAG remains GLOBAL (RagService is
    // a singleton on the host).

    fun setAgentPattern(conversationId: String, pattern: AgentPattern) {
        if (conversationId.isBlank()) return
        viewModelScope.launch {
            update { applyConvSettings(conversationId) { it.copy(agentPattern = pattern) } }
            repository.setAgentPattern(conversationId, pattern).onFailure {
                fail("Agent pattern save failed: ${it.safeMessage()}")
            }
        }
    }

    fun setRequireConfirmation(conversationId: String, require: Boolean) {
        if (conversationId.isBlank()) return
        viewModelScope.launch {
            update { applyConvSettings(conversationId) { it.copy(requireConfirmation = require) } }
            repository.setRequireConfirmation(conversationId, require).onFailure {
                fail("Require-confirmation save failed: ${it.safeMessage()}")
            }
        }
    }

    fun setToolsEnabled(conversationId: String, enabled: Boolean) {
        if (conversationId.isBlank()) return
        viewModelScope.launch {
            update { applyConvSettings(conversationId) { it.copy(toolsEnabled = enabled) } }
            repository.setToolsEnabled(conversationId, enabled).onFailure {
                fail("Tools toggle save failed: ${it.safeMessage()}")
            }
        }
    }

    fun setRagEnabled(conversationId: String, enabled: Boolean) {
        if (conversationId.isBlank()) return
        viewModelScope.launch {
            update { applyConvSettings(conversationId) { it.copy(ragEnabled = enabled) } }
            repository.saveConversationSettings(conversationId, ragEnabled = enabled).onFailure {
                fail("RAG toggle save failed: ${it.safeMessage()}")
            }
        }
    }

    /**
     * Per-conversation cached-settings update. Mutate a single conversation's
     * cached [ConvSettingsUi], leaving every other conversation's snapshot
     * untouched (mirrors the per-conv storage shape on the host).
     */
    private fun MainUiState.applyConvSettings(
        conversationId: String,
        edit: (ConvSettingsUi) -> ConvSettingsUi,
    ): MainUiState {
        val current = convSettings[conversationId] ?: return this
        return copy(convSettings = convSettings + (conversationId to edit(current)))
    }

    // applyGlobalToAllConvSettings existed for "true GLOBAL flag flips
    // (currently only RAG)". RAG was never global — the host stores it per
    // conversation — and it was the helper's only caller, so both are gone.


    fun showPlansOverlay() {
        val convId = uiState.value.selectedConversationId ?: return
        // Reopening the overlay reloads the plan list from the host, which
        // is authoritative; a start that never produced a plan no longer
        // blocks the composer. A plan that arrives later still shows up
        // through plan.created.
        update {
            copy(
                screen = MainScreen.PlansOverlay,
                taskStartingConvId = taskStartingConvId?.takeUnless { it == convId },
            )
        }
        loadPlansForConversation(convId)
    }
    fun closePlansOverlay() = update { copy(screen = MainScreen.Chat) }

    fun loadPlansForConversation(conversationId: String) {
        if (conversationId.isBlank()) return
        viewModelScope.launch {
            if (uiState.value.activeHost?.status != HostStatus.Connected) return@launch
            repository.listPlansForConversation(conversationId)
                .onSuccess { plans ->
                    update { copy(convPlans = convPlans + (conversationId to plans)) }
                }
                .onFailure { fail("Plan load failed: ${it.safeMessage()}") }
        }
    }

    /**
     * Start a task in [conversationId].
     *
     * @param onAccepted called when the host queued the task, so the caller
     *        can clear its goal field; not called on failure, so the typed
     *        goal is kept.
     */
    fun startTask(conversationId: String, goal: String, onAccepted: () -> Unit = {}) {
        if (goal.isBlank()) return
        viewModelScope.launch {
            update { copy(taskStartingConvId = conversationId) }
            repository.startTask(conversationId, goal)
                .onSuccess { onAccepted() }
                .onFailure {
                    fail("Start task failed: ${it.safeMessage()}")
                    update {
                        copy(taskStartingConvId = taskStartingConvId?.takeUnless { id -> id == conversationId })
                    }
                }
            // The indicator clears in the plan.created handler when a plan
            // for [conversationId] arrives, or when the user reopens the
            // overlay (see showPlansOverlay).
        }
    }

    fun stopPlan(planId: String) {
        viewModelScope.launch {
            repository.stopPlan(planId, "Stopped from plans overlay")
                .onFailure { fail("Plan stop failed: ${it.safeMessage()}") }
            // plan.updated event will refresh the row.
        }
    }

    /**
     * Retry a step with optional corrective notes. Empty notes = retry
     * without guidance; the host treats it as a plain retry.
     */
    fun retryStep(stepId: String, notes: String) {
        viewModelScope.launch {
            repository.retryStep(stepId, notes)
                .onFailure { fail("Retry step failed: ${it.safeMessage()}") }
        }
    }

    fun overrideStepDone(stepId: String) {
        viewModelScope.launch {
            repository.overrideStepDone(stepId)
                .onFailure { fail("Mark done failed: ${it.safeMessage()}") }
        }
    }

    fun skipStep(stepId: String, reason: String = "Not needed") {
        viewModelScope.launch {
            repository.skipStep(stepId, reason)
                .onFailure { fail("Skip step failed: ${it.safeMessage()}") }
        }
    }

    private fun MainUiState.upsertPlan(plan: PlanUi): MainUiState {
        val existing = convPlans[plan.conversationId].orEmpty()
        val idx = existing.indexOfFirst { it.id == plan.id }
        val next = if (idx >= 0) existing.toMutableList().apply { set(idx, plan) }
        else existing + plan
        return copy(
            convPlans = convPlans + (plan.conversationId to next),
        )
    }

    // ---- Tool calls + agent confirmation -----------------------------------
    //
    // Read ops populate the log on overlay open; events keep it current.
    // tool_call.requested + .completed are host-wide (no conv-scope
    // filtering) so the confirmation modal can render regardless of which
    // conv is foregrounded.

    fun showToolCallLog() {
        val convId = uiState.value.selectedConversationId ?: return
        update { copy(screen = MainScreen.ToolCallLog) }
        loadToolCallsForConversation(convId)
    }
    fun closeToolCallLog() = update { copy(screen = MainScreen.Chat) }

    fun loadToolCallsForConversation(conversationId: String) {
        if (conversationId.isBlank()) return
        viewModelScope.launch {
            if (uiState.value.activeHost?.status != HostStatus.Connected) return@launch
            repository.listToolCallsForConv(conversationId)
                .onSuccess { calls ->
                    update { copy(convToolCalls = convToolCalls + (conversationId to calls)) }
                }
                .onFailure { fail("Tool log load failed: ${it.safeMessage()}") }
        }
    }

    fun approveToolCall(callId: String) {
        viewModelScope.launch {
            // Optimistic dismiss — even if the host rejects (`invalid_params`)
            // the user has acted. The follow-up `tool_call.completed` event
            // is the source of truth; this just keeps the dialog from
            // hanging while the wire round-trips.
            if (uiState.value.pendingToolConfirmation?.callId == callId) {
                update { copy(pendingToolConfirmation = null) }
            }
            repository.approveToolCall(callId).onFailure {
                fail("Approve tool call failed: ${it.safeMessage()}")
            }
        }
    }

    fun denyToolCall(callId: String) {
        viewModelScope.launch {
            if (uiState.value.pendingToolConfirmation?.callId == callId) {
                update { copy(pendingToolConfirmation = null) }
            }
            repository.denyToolCall(callId).onFailure {
                fail("Deny tool call failed: ${it.safeMessage()}")
            }
        }
    }

    private fun MainUiState.upsertToolCall(convId: String, call: ToolCallUi): MainUiState {
        val existing = convToolCalls[convId].orEmpty()
        val idx = existing.indexOfFirst { it.id == call.id }
        val next = if (idx >= 0) existing.toMutableList().apply { set(idx, call) }
        else existing + call  // host orders by started_at ASC; new rows go at the tail
        return copy(convToolCalls = convToolCalls + (convId to next))
    }

    // ---- Artifacts + generated media + attachments ------------------------

    fun showArtifacts() {
        val convId = uiState.value.selectedConversationId ?: return
        update { copy(screen = MainScreen.Artifacts) }
        loadArtifactsForConversation(convId)
    }
    fun closeArtifacts() = update { copy(screen = MainScreen.Chat) }

    fun showGeneratedMedia() {
        val convId = uiState.value.selectedConversationId ?: return
        update { copy(screen = MainScreen.GeneratedMedia) }
        loadGeneratedMediaForConversation(convId)
    }
    fun closeGeneratedMedia() = update { copy(screen = MainScreen.Chat) }

    fun loadArtifactsForConversation(conversationId: String) {
        if (conversationId.isBlank()) return
        viewModelScope.launch {
            if (uiState.value.activeHost?.status != HostStatus.Connected) return@launch
            repository.listArtifactsForConv(conversationId)
                .onSuccess { rows ->
                    update { copy(convArtifacts = convArtifacts + (conversationId to rows)) }
                }
                .onFailure { fail("Artifacts load failed: ${it.safeMessage()}") }
        }
    }

    fun loadGeneratedMediaForConversation(conversationId: String) {
        if (conversationId.isBlank()) return
        viewModelScope.launch {
            if (uiState.value.activeHost?.status != HostStatus.Connected) return@launch
            // Two independent reads. A failure is reported rather than
            // shown as an empty gallery.
            val imagesResult = repository.listImagesForConv(conversationId)
            val audioResult = repository.listAudioForConv(conversationId)
            (imagesResult.exceptionOrNull() ?: audioResult.exceptionOrNull())?.let {
                fail("Media load failed: ${it.safeMessage()}")
            }
            val images = imagesResult.getOrNull() ?: uiState.value.convImages[conversationId].orEmpty()
            val audio = audioResult.getOrNull() ?: uiState.value.convAudio[conversationId].orEmpty()
            update {
                copy(
                    convImages = convImages + (conversationId to images),
                    convAudio = convAudio + (conversationId to audio),
                )
            }
        }
    }

    /**
     * Fetch bytes for an artifact / image / audio file by `fileName`.
     * Returns the raw payload (incl. `truncated` flag) so the caller
     * can render inline (image), play (audio), or save to a Storage
     * Access Framework Uri (artifact). Caller is responsible for
     * picking the right repo method via [kind].
     */
    suspend fun downloadFile(
        kind: DownloadKind,
        conversationId: String,
        fileName: String,
        maxBytes: Int = 0,
    ): Result<com.verzeta.android.data.chat.DownloadPayloadUi> = when (kind) {
        DownloadKind.Artifact -> repository.downloadArtifact(conversationId, fileName, maxBytes)
        DownloadKind.Image -> repository.downloadImage(conversationId, fileName, maxBytes)
        DownloadKind.Audio -> repository.downloadAudio(conversationId, fileName, maxBytes)
    }

    fun addPendingAttachment(attachment: OutgoingAttachment) {
        update {
            copy(pendingAttachments = (pendingAttachments + attachment).take(8))
        }
    }
    fun removePendingAttachment(index: Int) {
        update {
            copy(
                pendingAttachments = pendingAttachments.toMutableList().apply {
                    if (index in indices) removeAt(index)
                },
            )
        }
    }
    fun clearPendingAttachments() = update { copy(pendingAttachments = emptyList()) }

    /**
     * Send the current draft (state.messageDraft) along with the
     * staged [pendingAttachments]. Routes through
     * `msg.send_with_attachments`; subsequent message lifecycle
     * arrives via the existing `message.*` events on the active
     * subscription.
     *
     * Validation:
     *   - Either text or attachments must be non-empty.
     *   - max 8 attachments, ≤ 4 MiB each, ≤ 11 MiB total (see
     *     [RemoteRepository.MAX_UPLOAD_RAW_BYTES]). The host re-validates
     *     each attachment.
     */
    fun sendWithAttachments() {
        val convId = uiState.value.selectedConversationId ?: return
        val text = uiState.value.messageDraft
        val atts = uiState.value.pendingAttachments
        if (atts.isEmpty()) {
            // Fall back to plain text send (no attachments).
            sendDraftMessage()
            return
        }
        viewModelScope.launch {
            setBusy("Sending message")
            repository.sendMessageWithAttachments(convId, text, atts)
                .onSuccess {
                    update {
                        copy(
                            messageDraft = "",
                            pendingAttachments = emptyList(),
                            busy = false,
                        )
                    }
                }
                .onFailure { fail("Send failed: ${it.safeMessage()}") }
        }
    }

    private fun MainUiState.upsertArtifact(convId: String, row: ArtifactRowUi): MainUiState {
        val existing = convArtifacts[convId].orEmpty()
        // Dedupe by (sourceMsgId, fileName) — the host emits one event per
        // extracted artifact, but the same row may appear in a follow-up
        // list_for_conv response too.
        val idx = existing.indexOfFirst {
            it.sourceMsgId == row.sourceMsgId && it.fileName == row.fileName
        }
        val next = if (idx >= 0) existing.toMutableList().apply { set(idx, row) }
        else existing + row
        return copy(convArtifacts = convArtifacts + (convId to next))
    }

    private fun MainUiState.upsertGeneratedFile(
        bucket: GenBucket,
        convId: String,
        file: GeneratedFileUi,
    ): MainUiState {
        val map = when (bucket) {
            GenBucket.Image -> convImages
            GenBucket.Audio -> convAudio
        }
        val existing = map[convId].orEmpty()
        val idx = existing.indexOfFirst { it.fileName == file.fileName }
        val next = if (idx >= 0) existing.toMutableList().apply { set(idx, file) }
        else existing + file
        return when (bucket) {
            GenBucket.Image -> copy(convImages = convImages + (convId to next))
            GenBucket.Audio -> copy(convAudio = convAudio + (convId to next))
        }
    }

    private enum class GenBucket { Image, Audio }

    // ---- Canvas + console + run-state ------------------------------------
    //
    // Run + Send-to-IDE always execute on the HOST. Android sends, host
    // runs, host streams output back via canvas.run.line events.

    fun showCanvas() {
        val convId = uiState.value.selectedConversationId ?: return
        update { copy(screen = MainScreen.Canvas) }
        loadCanvasForConversation(convId)
        loadCanvasRunCapabilities()
    }
    fun closeCanvas() = update { copy(screen = MainScreen.Chat) }

    fun loadCanvasForConversation(conversationId: String) {
        if (conversationId.isBlank()) return
        viewModelScope.launch {
            if (uiState.value.activeHost?.status != HostStatus.Connected) return@launch
            repository.getActiveCanvas(conversationId)
                .onSuccess { canvas ->
                    if (canvas != null) {
                        update { copy(convCanvas = convCanvas + (conversationId to canvas)) }
                        loadCanvasActionsForLanguage(canvas.language)
                    } else {
                        update { copy(convCanvas = convCanvas - conversationId) }
                    }
                }
                .onFailure { fail("Canvas load failed: ${it.safeMessage()}") }
        }
    }

    fun loadCanvasHistory(conversationId: String) {
        viewModelScope.launch {
            if (uiState.value.activeHost?.status != HostStatus.Connected) return@launch
            repository.getCanvasHistory(conversationId).onSuccess { rows ->
                update { copy(convCanvasHistory = convCanvasHistory + (conversationId to rows)) }
            }
        }
    }

    // ---- Polls ------------------------------------------------------------

    /**
     * Re-fetch the message list for [convId] and merge the host's
     * authoritative rows into uiState.messages. Used when a non-
     * streaming message lands (e.g. PollService's auto-persisted
     * poll-card) — the `message.added` wire event only carries
     * {conv_id, msg_id} which is insufficient for parser to
     * reconstruct the row + its metadata.
     */
    fun refreshMessagesForConv(convId: String) {
        if (convId.isBlank()) return
        viewModelScope.launch {
            repository.listMessages(convId)
                .onSuccess { all ->
                    if (uiState.value.selectedConversationId != convId) return@onSuccess
                    val sorted = all.sortedBy { it.createdAt }
                    val (visible, hidden) = sorted.partition { it.isVisibleInChat() }
                    val activity = hidden.map { it.toToolActivity() }
                    update {
                        copy(
                            messages = visible,
                            toolActivity = activity,
                        )
                    }
                }
        }
    }

    /**
     * Refresh every poll in [convId]. Called on conv open and on
     * poll.* events that don't carry a specific poll_id.
     */
    fun refreshPollsForConv(convId: String) {
        if (convId.isBlank()) return
        viewModelScope.launch {
            val polls = repository.listPollsForConversation(convId, false)
                .getOrNull().orEmpty()
            update { copy(convPolls = convPolls + (convId to polls)) }
        }
    }

    /**
     * Refresh exactly one poll inside [convId]. Cheaper than the full
     * list path for events that already carry a poll_id.
     */
    fun refreshSinglePoll(convId: String, pollId: String) {
        if (convId.isBlank() || pollId.isBlank()) return
        viewModelScope.launch {
            val updated = repository.pollResults(pollId).getOrNull()
                ?: return@launch
            update {
                val existing = convPolls[convId].orEmpty()
                val idx = existing.indexOfFirst { it.id == pollId }
                val next = if (idx >= 0) {
                    existing.toMutableList().also { it[idx] = updated }
                } else {
                    existing + updated
                }
                copy(convPolls = convPolls + (convId to next))
            }
        }
    }

    /**
     * User-initiated poll creation from the Android composer.
     * mode is "single" or "multi"; ranked is rejected by host.
     */
    fun startPoll(
        convId: String,
        question: String,
        options: List<String>,
        mode: String = "single",
        closesInMinutes: Int = 0,
    ) {
        if (convId.isBlank() || question.isBlank() || options.size < 2) return
        viewModelScope.launch {
            repository.startPoll(convId, question, options, mode, closesInMinutes)
                // Event handler will pick up the refresh.
                .onFailure {
                    fail("Failed to start poll: ${it.safeMessage()}")
                }
        }
    }

    fun voteOnPoll(pollId: String, optionId: String) {
        if (pollId.isBlank() || optionId.isBlank()) return
        viewModelScope.launch {
            repository.castPollVote(pollId, optionId = optionId)
                .onFailure {
                    fail("Failed to vote: ${it.safeMessage()}")
                }
        }
    }

    fun closePoll(pollId: String) {
        if (pollId.isBlank()) return
        viewModelScope.launch {
            repository.closePoll(pollId)
                .onFailure {
                    fail("Failed to close poll: ${it.safeMessage()}")
                }
        }
    }

    private fun loadCanvasActionsForLanguage(language: String) {
        if (language.isBlank()) return
        viewModelScope.launch {
            val ai = repository.listCanvasAiActions(language).getOrNull().orEmpty()
            val tools = repository.listCanvasTools(language).getOrNull().orEmpty()
            update {
                copy(
                    canvasAiActionsByLang = canvasAiActionsByLang + (language to ai),
                    canvasToolsByLang = canvasToolsByLang + (language to tools),
                )
            }
        }
    }

    private fun loadCanvasRunCapabilities() {
        viewModelScope.launch {
            if (uiState.value.activeHost?.status != HostStatus.Connected) return@launch
            val supported = repository.listCanvasRunSupportedLanguages().getOrNull().orEmpty()
            val supportedIde = repository.listCanvasRunSupportedIdeLanguages().getOrNull().orEmpty()
            val sandbox = repository.getCanvasRunSandboxState().getOrNull() ?: CanvasSandboxStateUi()
            val running = repository.getCanvasRunIsRunning().getOrNull() ?: false
            update {
                copy(
                    canvasRunSupportedLanguages = supported,
                    canvasRunSupportedIdeLanguages = supportedIde,
                    canvasSandbox = sandbox,
                    canvasRunState = CanvasRunStateUi(running = running),
                )
            }
        }
    }

    fun saveCanvasContent(conversationId: String, content: String) {
        viewModelScope.launch {
            update {
                val existing = convCanvas[conversationId] ?: return@update this
                copy(convCanvas = convCanvas + (conversationId to existing.copy(content = content)))
            }
            repository.editCanvas(conversationId, content).onFailure {
                fail("Canvas save failed: ${it.safeMessage()}")
                loadCanvasForConversation(conversationId)
            }
        }
    }

    fun closeActiveCanvas(conversationId: String) {
        viewModelScope.launch {
            repository.closeCanvas(conversationId).onFailure {
                fail("Canvas close failed: ${it.safeMessage()}")
            }
            // canvas.closed event will drop the entry.
        }
    }

    fun switchCanvas(conversationId: String, canvasId: String) {
        viewModelScope.launch {
            repository.switchToCanvas(conversationId, canvasId).onFailure {
                fail("Canvas switch failed: ${it.safeMessage()}")
            }
        }
    }

    fun runCanvasTool(conversationId: String, actionId: String) {
        viewModelScope.launch {
            repository.runCanvasTool(conversationId, actionId)
                .onSuccess { res ->
                    if (res.ok) {
                        update { copy(status = res.message.ifBlank { "Tool ran" }) }
                    } else {
                        fail(res.message.ifBlank { "Tool failed" })
                    }
                }
                .onFailure { fail("Tool failed: ${it.safeMessage()}") }
        }
    }

    /**
     * Runs a canvas AI action on [conversationId]'s canvas; the host posts
     * the composed prompt into that conversation.
     *
     * @param conversationId the conversation whose canvas is open.
     * @param actionId the action id from `canvas.ai_actions.list`.
     * @param submenuChoice the chosen submenu entry, or blank for none.
     */
    fun triggerCanvasAiAction(conversationId: String, actionId: String, submenuChoice: String = "") {
        viewModelScope.launch {
            repository.triggerCanvasAiAction(conversationId, actionId, submenuChoice).onFailure {
                fail("AI action failed: ${it.safeMessage()}")
            }
        }
    }

    fun startCanvasRun(conversationId: String) {
        viewModelScope.launch {
            update { copy(consoleVisible = true) }
            repository.startCanvasRun(conversationId).onFailure {
                fail("Canvas run failed: ${it.safeMessage()}")
            }
        }
    }

    fun cancelCanvasRun() {
        viewModelScope.launch {
            repository.cancelCanvasRun().onFailure {
                fail("Cancel run failed: ${it.safeMessage()}")
            }
        }
    }

    /**
     * Answers an `input()` prompt in the running script. Deliberately does not
     * echo locally: the host writes the typed line into the console itself, so
     * echoing here would duplicate every answer.
     */
    fun sendCanvasInput(text: String) {
        viewModelScope.launch {
            repository.sendCanvasInput(text).onFailure {
                fail("Sending input failed: ${it.safeMessage()}")
            }
        }
    }

    /** Closes the running script's stdin. */
    fun sendCanvasEof() {
        viewModelScope.launch {
            repository.sendCanvasEof().onFailure {
                fail("Ending input failed: ${it.safeMessage()}")
            }
        }
    }

    fun sendCanvasToIde(conversationId: String) {
        viewModelScope.launch {
            repository.sendCanvasToIde(conversationId)
                .onSuccess { ok ->
                    val canvas = uiState.value.convCanvas[conversationId]
                    if (ok) {
                        update { copy(status = "Opened on host: ${canvas?.filename ?: "canvas"}") }
                    } else {
                        fail("Open in IDE failed")
                    }
                }
                .onFailure { fail("Send to IDE failed: ${it.safeMessage()}") }
        }
    }

    fun clearCanvasConsole() {
        viewModelScope.launch {
            update { copy(consoleLines = emptyList()) }  // optimistic
            repository.clearCanvasConsole().onFailure {
                fail("Console clear failed: ${it.safeMessage()}")
            }
        }
    }

    fun setConsoleVisible(visible: Boolean) = update { copy(consoleVisible = visible) }

    fun sendDraftMessage() {
        viewModelScope.launch {
            val conversationId = uiState.value.selectedConversationId
            val text = uiState.value.messageDraft.trim()
            if (conversationId == null) {
                update { copy(chatStatus = "Open a conversation before sending", busy = false) }
                return@launch
            }
            if (text.isBlank()) return@launch
            // The host rejects msg.send text longer than this with a
            // misleading "'text' required". Check first and keep the draft.
            if (text.length > MAX_MESSAGE_CHARS) {
                update { copy(chatStatus = "Message too long") }
                fail(
                    "This message has ${text.length} characters. The limit is " +
                        "$MAX_MESSAGE_CHARS. Shorten it or send it in parts.",
                )
                return@launch
            }

            val localMessage = MessageUi(
                id = "local-${System.currentTimeMillis()}",
                role = MessageRole.User,
                text = text,
            )
            update { copy(messages = messages + localMessage, messageDraft = "", chatStatus = "Sending") }
            setBusy("Sending message")

            repository.sendMessage(conversationId, text)
                .onFailure { error ->
                    // Undo the optimistic bubble and give the text back, unless
                    // the user has already started typing something else.
                    update {
                        copy(
                            messages = messages.filterNot { it.id == localMessage.id },
                            messageDraft = if (messageDraft.isBlank()) text else messageDraft,
                            chatStatus = "Message not sent",
                        )
                    }
                    fail("Send failed: ${error.safeMessage()}")
                }
                .onSuccess { result ->
                    val placeholderId = result.assistantPlaceholderId
                    update {
                        copy(
                            messages = if (placeholderId.isNullOrBlank()) {
                                messages
                            } else {
                                messages + MessageUi(
                                    id = placeholderId,
                                    role = MessageRole.Assistant,
                                    text = "",
                                    streaming = true,
                                )
                            },
                            chatStatus = "Message sent",
                            status = "Message sent",
                            busy = false,
                        )
                    }
                }
        }
    }

    fun stopGeneration() {
        viewModelScope.launch {
            val conversationId = uiState.value.selectedConversationId
            if (conversationId == null) {
                update { copy(chatStatus = "Open a conversation before stopping generation", busy = false) }
                return@launch
            }
            setBusy("Stopping generation")
            repository.stopGeneration()
                .onFailure { fail("Stop failed: ${it.safeMessage()}") }
                .onSuccess {
                    update {
                        copy(
                            messages = messages.map { if (it.streaming) it.copy(streaming = false) else it },
                            chatStatus = "Generation stopped",
                            status = "Generation stopped",
                            busy = false,
                        )
                    }
                }
        }
    }

    // ---- Model catalog -----------------------------------------------------

    fun loadModelCatalog() {
        viewModelScope.launch {
            if (uiState.value.activeHost?.status != HostStatus.Connected) return@launch
            repository.getModelCatalog()
                .onSuccess { catalog -> update { copy(modelCatalog = catalog) } }
                .onFailure {
                    // Catalog is non-critical — chat works without it. Log
                    // the reason in the global status row so the user has a
                    // breadcrumb if the picker is empty.
                    update { copy(status = "Model catalog load failed: ${it.safeMessage()}") }
                }
        }
    }

    /**
     * Optimistically flip the active selection in local state, then fire
     * `models.set_active`. The host's `models.active_changed` event
     * confirms the change and is also emitted whenever the desktop user
     * (or a sibling client) changes the model — so this action and the
     * passive "I see somebody changed the model" path use the same state
     * mutation.
     */
    fun setActiveModel(providerId: String, modelName: String) {
        if (providerId.isBlank() || modelName.isBlank()) return
        viewModelScope.launch {
            update {
                copy(modelCatalog = modelCatalog.copy(
                    activeProvider = providerId,
                    activeModel = modelName,
                ))
            }
            repository.setActiveModel(providerId, modelName)
                .onFailure {
                    // Drop a chained "create a conversation" so a later,
                    // unrelated models.active_changed does not trigger it.
                    pendingAfterModelChange = null
                    fail("Set model failed: ${it.safeMessage()}")
                }
        }
    }

    /**
     * "Pick this model and then create a new conversation." Chains
     * `models.set_active` → wait for `models.active_changed` →
     * `conv.create`. Fires `conv.create` only after the host echoes the
     * change so the new conversation inherits the new selection (the
     * host stamps the *currently active* model into the new row at
     * create time).
     */
    fun setActiveModelAndCreateConversation(providerId: String, modelName: String) {
        if (providerId.isBlank() || modelName.isBlank()) return
        pendingAfterModelChange = { createConversation() }
        setActiveModel(providerId, modelName)
    }

    // ---- Folders / Groups --------------------------------------------------

    fun refreshFolders() {
        viewModelScope.launch {
            if (uiState.value.activeHost?.status != HostStatus.Connected) {
                update { copy(foldersStatus = "Connect a host before loading folders") }
                return@launch
            }
            val target = if (uiState.value.foldersShowAll) {
                repository.listAllFolders()
            } else {
                repository.listProjectFolders()
            }
            target.onFailure { update { copy(foldersStatus = "Folder load failed: ${it.safeMessage()}") } }
                .onSuccess { folders ->
                    update {
                        copy(
                            folders = folders,
                            foldersStatus = if (folders.isEmpty()) "No folders yet"
                            else "${folders.size} folder${if (folders.size == 1) "" else "s"}",
                        )
                    }
                }
        }
    }

    /**
     * Create a folder with the host's full metadata surface. The Android
     * UI always passes the explicit type chosen by the user (per host
     * spec: don't rely on the wire default when the user has a choice).
     * goal / description / agentIds are passed through; the host ignores
     * them for `regular` so we don't need to special-case here.
     */
    fun createFolder(
        name: String,
        type: FolderType = FolderType.Project,
        goal: String = "",
        description: String = "",
        agentIds: List<String> = emptyList(),
    ) {
        if (name.isBlank()) return
        viewModelScope.launch {
            setBusy("Creating folder")
            repository.createFolder(name, type, goal, description, agentIds)
                .onFailure { fail("Folder create failed: ${it.safeMessage()}") }
                .onSuccess { result ->
                    update {
                        copy(
                            status = "Folder created (${result.type.wireValue})",
                            busy = false,
                        )
                    }
                    // folder.added event populates state.folders shortly.
                }
        }
    }

    /**
     * Refresh a single folder's full metadata (folderType, goal,
     * description, agentIds) from the host. Called when the user opens
     * the editor on a row whose `state.folders` entry is incomplete —
     * e.g. it came from `folder.list_projects` before any
     * `folder.added` / `folder.updated` event arrived for it.
     */
    fun loadFolderInfo(folderId: String) {
        viewModelScope.launch {
            repository.getFolder(folderId)
                .onSuccess { folder ->
                    update {
                        val existingIdx = folders.indexOfFirst { it.id == folder.id }
                        copy(folders = if (existingIdx >= 0) {
                            folders.toMutableList().apply { set(existingIdx, folder) }
                        } else {
                            folders + folder
                        })
                    }
                }
                .onFailure { /* non-fatal — editor opens with whatever we have. */ }
        }
    }

    fun convertFolderToProject(folderId: String) {
        viewModelScope.launch {
            repository.updateFolderMetadata(
                id = folderId,
                folderType = FolderType.Project,
                goal = "",
                description = "",
                agentIds = emptyList(),
            ).onFailure { fail("Convert failed: ${it.safeMessage()}") }
        }
    }

    fun setFoldersShowAll(showAll: Boolean) {
        update { copy(foldersShowAll = showAll) }
        viewModelScope.launch {
            val target = if (showAll) repository.listAllFolders() else repository.listProjectFolders()
            target.onSuccess { folders ->
                update {
                    copy(
                        folders = folders,
                        foldersStatus = if (folders.isEmpty()) "No folders"
                        else "${folders.size} folder${if (folders.size == 1) "" else "s"}" +
                            if (showAll) " (all types)" else " (projects + orgs)",
                    )
                }
            }.onFailure { update { copy(foldersStatus = "Load failed: ${it.safeMessage()}") } }
        }
    }

    // ---- Agents ------------------------------------------------------------

    fun loadAgents() {
        viewModelScope.launch {
            if (uiState.value.activeHost?.status != HostStatus.Connected) return@launch
            repository.listAgents()
                .onSuccess { agents ->
                    update {
                        copy(
                            agents = agents,
                            agentsStatus = if (agents.isEmpty()) "No agents on this host"
                            else "${agents.size} agent${if (agents.size == 1) "" else "s"}",
                        )
                    }
                }
                .onFailure { update { copy(agentsStatus = "Agent load failed: ${it.safeMessage()}") } }
        }
    }

    fun renameFolder(id: String, name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            update {
                copy(folders = folders.map { if (it.id == id) it.copy(name = name) else it })
            }
            repository.renameFolder(id, name)
                .onFailure { fail("Folder rename failed: ${it.safeMessage()}") }
        }
    }

    fun deleteFolder(id: String) {
        viewModelScope.launch {
            repository.deleteFolder(id)
                .onFailure { fail("Folder delete failed: ${it.safeMessage()}") }
            // folder.deleted event will remove the row.
        }
    }

    fun updateFolderMetadata(
        id: String,
        folderType: FolderType,
        goal: String,
        description: String,
        agentIds: List<String>,
    ) {
        viewModelScope.launch {
            repository.updateFolderMetadata(id, folderType, goal, description, agentIds)
                .onFailure { fail("Folder update failed: ${it.safeMessage()}") }
        }
    }


    fun loadFolderMembers(folderId: String) {
        if (folderId.isBlank()) return
        viewModelScope.launch {
            if (uiState.value.activeHost?.status != HostStatus.Connected) return@launch
            repository.listFolderMembers(folderId)
                .onSuccess { members ->
                    update { copy(folderMembers = folderMembers + (folderId to members)) }
                }
                .onFailure {
                    update { copy(foldersStatus = "Member load failed: ${it.safeMessage()}") }
                }
        }
    }

    fun saveFolderEdit(
        id: String,
        name: String,
        currentName: String,
        type: FolderType,
        goal: String,
        description: String,
        members: List<FolderMemberUi>,
        preferredSkillIds: List<String>,
        exposeOnlyPreferred: Boolean,
        onComplete: (saved: Boolean) -> Unit = {},
    ) {
        viewModelScope.launch {
            val saved = saveFolderEditSteps(
                id, name, currentName, type, goal, description,
                members, preferredSkillIds, exposeOnlyPreferred,
            )
            onComplete(saved)
        }
    }

    /** The steps of [saveFolderEdit]; returns false at the first failure. */
    private suspend fun saveFolderEditSteps(
        id: String,
        name: String,
        currentName: String,
        type: FolderType,
        goal: String,
        description: String,
        members: List<FolderMemberUi>,
        preferredSkillIds: List<String>,
        exposeOnlyPreferred: Boolean,
    ): Boolean {
        setBusy("Saving folder")
        if (name != currentName) {
            repository.renameFolder(id, name).onFailure {
                fail("Folder rename failed: ${it.safeMessage()}")
                return false
            }
        }
        repository.updateFolderMetadata(
            id = id,
            folderType = type,
            goal = goal,
            description = description,
            agentIds = emptyList(),  // legacy slot — membership now in folder.members.set
        ).onFailure {
            fail("Folder update failed: ${it.safeMessage()}")
            return false
        }
        // Regular folders have no project members on the host; sending an
        // empty list clears any leftovers (matches FolderSettingsDialog.qml:151).
        val toSet = if (type == FolderType.Regular) emptyList() else members
        // folder.members.set rewrites every row. The members carry their
        // overrides and provenance, but a host that does not read those
        // fields would still erase them, so send it only when the roster
        // actually changed.
        if (rosterChanged(uiState.value.folderMembers[id], toSet)) {
            repository.setFolderMembers(id, toSet).onFailure {
                fail("Member save failed: ${it.safeMessage()}")
                return false
            }
        }
        if (type != FolderType.Regular) {
            repository.setPreferredSkills(PreferredSkillsScope.Folder, id, preferredSkillIds)
                .onFailure {
                    fail("Preferred skills save failed: ${it.safeMessage()}")
                    return false
                }
            repository.setExposeOnlyPreferred(
                PreferredSkillsScope.Folder, id, exposeOnlyPreferred,
            ).onFailure {
                fail("Expose-only-preferred save failed: ${it.safeMessage()}")
                return false
            }
        }
        // Trigger the kickoff sheet only when the folder ended up with
        // ≥1 member; an empty member list has nothing to kick off.
        val kickoff = if (type != FolderType.Regular && toSet.isNotEmpty()) {
            PendingKickoff(folderId = id, folderName = name, memberCount = toSet.size)
        } else null
        update {
            copy(
                status = "Folder saved",
                busy = false,
                pendingKickoff = kickoff,
            )
        }
        return true
    }

    /**
     * Whether [edited] differs from the roster last loaded from the host.
     *
     * Compares agent, alias and coordinator only, the fields
     * `folder.members.set` can write; order does not matter. When the
     * roster was never loaded ([loaded] is null), an empty edit is treated
     * as unchanged so a failed load cannot wipe the project's members.
     */
    private fun rosterChanged(loaded: List<FolderMemberUi>?, edited: List<FolderMemberUi>): Boolean {
        if (loaded == null) return edited.isNotEmpty()
        fun key(m: FolderMemberUi) = Triple(m.agentId, m.alias, m.isCoordinator)
        return loaded.size != edited.size ||
            loaded.map(::key).toSet() != edited.map(::key).toSet()
    }

    /**
     * Create a new folder, then set its type and metadata, then ship its
     * member list.
     *
     * `folder.create` stores only the name and always creates a regular
     * folder, so for a project or organization the type, goal and
     * description are written with `folder.update_metadata` right after.
     * Members then go through `folder.members.set`, as on the desktop.
     *
     * A new regular folder is only listed while "All folders" is on, so the
     * list switches to it after creating one.
     *
     * @param onComplete called with true once the folder exists on the
     *        host (a later step may still have failed and been reported),
     *        false when the folder itself could not be created, so the
     *        editor can stay open with the user's input.
     */
    fun createFolderWithMembers(
        name: String,
        type: FolderType,
        goal: String,
        description: String,
        members: List<FolderMemberUi>,
        preferredSkillIds: List<String> = emptyList(),
        exposeOnlyPreferred: Boolean = false,
        onComplete: (created: Boolean) -> Unit = {},
    ) {
        if (name.isBlank()) return
        viewModelScope.launch {
            setBusy("Creating folder")
            repository.createFolder(name, type, goal, description, emptyList())
                .onFailure {
                    fail("Folder create failed: ${it.safeMessage()}")
                    onComplete(false)
                    return@launch
                }
                .onSuccess { result ->
                    onComplete(true)
                    if (type == FolderType.Regular && !uiState.value.foldersShowAll) {
                        setFoldersShowAll(true)
                    }
                    if (type != FolderType.Regular) {
                        repository.updateFolderMetadata(
                            id = result.id,
                            folderType = type,
                            goal = goal,
                            description = description,
                            agentIds = emptyList(),
                        ).onFailure {
                            fail("Folder created, but setting its type failed: ${it.safeMessage()}")
                            return@onSuccess
                        }
                    }
                    val toSet = if (type == FolderType.Regular) emptyList() else members
                    if (toSet.isNotEmpty()) {
                        repository.setFolderMembers(result.id, toSet).onFailure {
                            fail("Member save failed: ${it.safeMessage()}")
                            return@onSuccess
                        }
                    }
                    if (type != FolderType.Regular && preferredSkillIds.isNotEmpty()) {
                        repository.setPreferredSkills(
                            PreferredSkillsScope.Folder, result.id, preferredSkillIds,
                        ).onFailure {
                            fail("Preferred skills save failed: ${it.safeMessage()}")
                            return@onSuccess
                        }
                        if (exposeOnlyPreferred) {
                            repository.setExposeOnlyPreferred(
                                PreferredSkillsScope.Folder, result.id, true,
                            ).onFailure {
                                fail("Expose-only-preferred save failed: ${it.safeMessage()}")
                                return@onSuccess
                            }
                        }
                    }
                    val kickoff = if (type != FolderType.Regular && toSet.isNotEmpty()) {
                        PendingKickoff(folderId = result.id, folderName = name, memberCount = toSet.size)
                    } else null
                    update {
                        copy(
                            status = "Folder created (${type.wireValue})",
                            busy = false,
                            pendingKickoff = kickoff,
                        )
                    }
                }
        }
    }


    fun loadFolderDocuments(folderId: String) {
        if (folderId.isBlank()) return
        viewModelScope.launch {
            if (uiState.value.activeHost?.status != HostStatus.Connected) return@launch
            repository.listFolderDocuments(folderId)
                .onSuccess { docs ->
                    update { copy(folderDocuments = folderDocuments + (folderId to docs)) }
                }
                .onFailure {
                    update { copy(foldersStatus = "Document load failed: ${it.safeMessage()}") }
                }
        }
    }

    /**
     * Upload bytes as a project document. Caller has already enforced the
     * size cap ([RemoteRepository.MAX_UPLOAD_RAW_BYTES]) and produced the
     * base64 string. Forbidden filename characters are pre-rejected by
     * the host (`/`, `\\`, `..`, leading `.`).
     */
    fun uploadFolderDocument(folderId: String, fileName: String, contentBase64: String) {
        viewModelScope.launch {
            setBusy("Uploading document")
            repository.uploadFolderDocument(folderId, fileName, contentBase64)
                .onSuccess {
                    update { copy(status = "Document uploaded: $fileName", busy = false) }
                    loadFolderDocuments(folderId)
                }
                .onFailure { fail("Document upload failed: ${it.safeMessage()}") }
        }
    }

    fun removeFolderDocument(folderId: String, fileName: String) {
        viewModelScope.launch {
            setBusy("Removing document")
            repository.removeFolderDocument(folderId, fileName)
                .onSuccess {
                    update { copy(status = "Document removed", busy = false) }
                    loadFolderDocuments(folderId)
                }
                .onFailure { fail("Document remove failed: ${it.safeMessage()}") }
        }
    }

    // ---- Folder kickoff ---------------------------------------------------

    fun runKickoffIndividual(folderId: String) {
        viewModelScope.launch {
            setBusy("Creating individual chats")
            repository.kickoffIndividualChats(folderId)
                .onSuccess { count ->
                    update { copy(status = "Created $count chat${if (count == 1) "" else "s"}", busy = false) }
                }
                .onFailure { fail("Individual kickoff failed: ${it.safeMessage()}") }
        }
    }

    fun runKickoffGroup(folderId: String, openOnReady: Boolean = false) {
        viewModelScope.launch {
            setBusy("Creating group chat")
            repository.kickoffGroupChat(folderId)
                .onSuccess { convId ->
                    update { copy(status = "Group chat created", busy = false) }
                    // openConversation switches the subscription and loads
                    // the messages; setting the selection by hand did not.
                    if (openOnReady) openConversation(convId)
                }
                .onFailure { fail("Group kickoff failed: ${it.safeMessage()}") }
        }
    }

    /**
     * Idempotently open or create a 1:1 chat with the given member of a
     * project. Reuses existing 1:1 if any. Useful for per-member
     * tap-to-chat in the sidebar.
     */
    fun openMemberChat(folderId: String, agentId: String, alias: String) {
        viewModelScope.launch {
            setBusy("Opening chat")
            repository.openMemberChat(folderId, agentId, alias)
                .onSuccess { convId ->
                    update { copy(busy = false) }
                    // openConversation switches the subscription and loads
                    // the messages; setting the selection by hand did not.
                    openConversation(convId)
                }
                .onFailure { fail("Open member chat failed: ${it.safeMessage()}") }
        }
    }

    fun dismissPendingKickoff() = update { copy(pendingKickoff = null) }

    // ---- Preferred skills -------------------------------------------------

    /**
     * Stash an in-progress preferred-skills selection from the sub-sheet.
     * Wire write happens later in [saveFolderEdit] — this just updates
     * the local cache so the parent editor reflects the choice.
     */
    fun applyLocalPreferredSkills(folderId: String, skillIds: List<String>, exposeOnly: Boolean) {
        update {
            copy(
                folderPreferredSkills = folderPreferredSkills + (folderId to PreferredSkillsUi(skillIds, exposeOnly)),
            )
        }
    }

    /**
     * Save a project's preferred skills straight to the host. Used by
     * Conversation settings, which has no later folder save to carry the
     * change (unlike the folder editor, which stages it with
     * [applyLocalPreferredSkills]). The cache updates first; on failure it
     * is reloaded from the host and the error is shown.
     */
    fun savePreferredSkills(folderId: String, skillIds: List<String>, exposeOnly: Boolean) {
        if (folderId.isBlank()) return
        applyLocalPreferredSkills(folderId, skillIds, exposeOnly)
        viewModelScope.launch {
            val result = repository.setPreferredSkills(PreferredSkillsScope.Folder, folderId, skillIds)
                .mapCatching {
                    repository.setExposeOnlyPreferred(
                        PreferredSkillsScope.Folder, folderId, exposeOnly && skillIds.isNotEmpty(),
                    ).getOrThrow()
                }
            result.onFailure {
                fail("Preferred skills save failed: ${it.safeMessage()}")
                loadFolderPreferredSkills(folderId)
            }
        }
    }

    fun loadFolderPreferredSkills(folderId: String) {
        if (folderId.isBlank()) return
        viewModelScope.launch {
            if (uiState.value.activeHost?.status != HostStatus.Connected) return@launch
            // Read both the list and the expose-only flag in parallel —
            // they're independent on the host side.
            val list = repository.listPreferredSkills(PreferredSkillsScope.Folder, folderId).getOrNull().orEmpty()
            val exposeOnly = repository.getExposeOnlyPreferred(PreferredSkillsScope.Folder, folderId).getOrNull() ?: false
            update {
                copy(folderPreferredSkills = folderPreferredSkills + (folderId to PreferredSkillsUi(list, exposeOnly)))
            }
        }
    }

    // ---- Heartbeats -------------------------------------------------------

    fun loadFolderHeartbeats(folderId: String) {
        if (folderId.isBlank()) return
        viewModelScope.launch {
            if (uiState.value.activeHost?.status != HostStatus.Connected) return@launch
            repository.listHeartbeatConfigsForFolder(folderId)
                .onSuccess { configs ->
                    update { copy(folderHeartbeats = folderHeartbeats + (folderId to configs)) }
                }
                .onFailure {
                    update { copy(foldersStatus = "Heartbeat load failed: ${it.safeMessage()}") }
                }
        }
    }

    fun upsertHeartbeatConfig(config: HeartbeatConfigUi, onComplete: ((String?) -> Unit)? = null) {
        viewModelScope.launch {
            setBusy(if (config.id.isBlank()) "Creating heartbeat" else "Updating heartbeat")
            repository.upsertHeartbeatConfig(config)
                .onSuccess { id ->
                    update { copy(status = "Heartbeat saved", busy = false) }
                    onComplete?.invoke(id)
                    // The heartbeat.config.changed event will refresh the
                    // list — no manual reload here.
                }
                .onFailure {
                    fail("Heartbeat save failed: ${it.safeMessage()}")
                    onComplete?.invoke(null)
                }
        }
    }

    fun removeHeartbeatConfig(id: String) {
        viewModelScope.launch {
            setBusy("Removing heartbeat")
            repository.removeHeartbeatConfig(id)
                .onSuccess { update { copy(status = "Heartbeat removed", busy = false) } }
                .onFailure { fail("Heartbeat remove failed: ${it.safeMessage()}") }
            // heartbeat.config.removed event drops it from state.
        }
    }

    fun runHeartbeatNow(configId: String) {
        viewModelScope.launch {
            setBusy("Running heartbeat")
            repository.runHeartbeatNow(configId)
                .onSuccess { update { copy(status = "Heartbeat queued", busy = false) } }
                .onFailure { fail("Heartbeat run failed: ${it.safeMessage()}") }
        }
    }

    fun cancelHeartbeatRun(runId: String) {
        viewModelScope.launch {
            repository.cancelHeartbeatRun(runId).onFailure {
                fail("Cancel run failed: ${it.safeMessage()}")
            }
        }
    }

    fun loadHeartbeatActivity() {
        viewModelScope.launch {
            if (uiState.value.activeHost?.status != HostStatus.Connected) return@launch
            // Three independent reads. A failure is reported rather than
            // shown as an empty list.
            val runsResult = repository.listRecentHeartbeatRuns()
            val firesResult = repository.listHeartbeatNextFires()
            val changesResult = repository.listRecentHeartbeatChanges()
            listOf(runsResult, firesResult, changesResult)
                .firstNotNullOfOrNull { it.exceptionOrNull() }
                ?.let { fail("Heartbeat activity load failed: ${it.safeMessage()}") }
            val runs = runsResult.getOrNull() ?: uiState.value.recentHeartbeatRuns
            val fires = firesResult.getOrNull() ?: uiState.value.heartbeatNextFires
            val changes = changesResult.getOrNull() ?: uiState.value.recentHeartbeatChanges
            update {
                copy(
                    recentHeartbeatRuns = runs,
                    heartbeatNextFires = fires,
                    recentHeartbeatChanges = changes,
                )
            }
        }
    }

    fun showHeartbeatActivity() {
        update { copy(screen = MainScreen.HeartbeatActivity) }
        if (uiState.value.activeHost?.status == HostStatus.Connected) loadHeartbeatActivity()
    }
    fun closeHeartbeatActivity() = update { copy(screen = MainScreen.Folders) }

    // -----------------------------------------------------------------
    // Read-only activity-log timeline.
    //
    // Scoped to a project folder or a conversation. The host's
    // AuditService.recentActivityFor* helpers return rows in DESC order
    // (newest first). Re-fetched on open and on user-initiated refresh
    // (a Refresh button is present in the screen's TopBar).
    // -----------------------------------------------------------------

    fun showActivityForProject(folderId: String, label: String) {
        update {
            copy(
                screen = MainScreen.ActivityTimeline,
                activityScopeKind = "project",
                activityScopeId = folderId,
                activityScopeLabel = label,
                activityLog = emptyList(),
            )
        }
        loadActivity()
    }

    fun showActivityForConversation(convId: String, label: String) {
        update {
            copy(
                screen = MainScreen.ActivityTimeline,
                activityScopeKind = "conversation",
                activityScopeId = convId,
                activityScopeLabel = label,
                activityLog = emptyList(),
            )
        }
        loadActivity()
    }

    fun closeActivityTimeline() = update {
        copy(
            // Back to where the timeline was opened from: the chat for a
            // conversation's activity, the Folders screen for a project's.
            screen = if (activityScopeKind == "conversation" && selectedConversationId != null) {
                MainScreen.Chat
            } else {
                MainScreen.Folders
            },
            activityScopeKind = "",
            activityScopeId = "",
            activityScopeLabel = "",
            activityLog = emptyList(),
        )
    }

    fun loadActivity() {
        viewModelScope.launch {
            val s = uiState.value
            if (s.activeHost?.status != HostStatus.Connected) return@launch
            val result = when (s.activityScopeKind) {
                "project" -> repository.activityForProject(s.activityScopeId)
                "conversation" -> repository.activityForConversation(s.activityScopeId)
                else -> Result.success(emptyList())
            }
            result
                .onSuccess { rows -> update { copy(activityLog = rows) } }
                .onFailure { fail("Activity load failed: ${it.safeMessage()}") }
        }
    }

    fun manualPostHeartbeatReport(reportId: String, targetConvId: String) {
        viewModelScope.launch {
            repository.manualPostHeartbeatReport(reportId, targetConvId)
                .onFailure { fail("Manual post failed: ${it.safeMessage()}") }
        }
    }

    fun manualDismissHeartbeatReport(reportId: String) {
        viewModelScope.launch {
            repository.manualDismissHeartbeatReport(reportId)
                .onFailure { fail("Manual dismiss failed: ${it.safeMessage()}") }
        }
    }

    /**
     * Create a group conversation. Mirrors NewGroupChatDialog's Step 4:
     *
     *   1. `group.create {title, members, folder_id?}` → returns `{id}`.
     *   2. **Project-scoped only**: for every member in [members] that's
     *      NOT already in the project's roster (compared on
     *      `agentId + alias.lowercase`), call `folder.member.add` to roll
     *      them into the roster. Coordinator state intentionally NOT
     *      forwarded — chat-level coordinator and project-level
     *      coordinator are separate concepts (matches QML lines 113-119).
     *      `invalid_params: alias 'X' already exists in this project` is
     *      a benign no-op (race with another client).
     *   3. Navigate the user to the new chat if [openOnReady].
     */
    fun createGroup(
        title: String,
        members: List<GroupMemberInput>,
        folderId: String? = null,
        openOnReady: Boolean = true,
    ) {
        if (title.isBlank() || members.isEmpty()) {
            fail("Group needs a title and at least one member")
            return
        }
        viewModelScope.launch {
            setBusy("Creating group")
            repository.createGroup(title, members, folderId)
                .onFailure { fail("Group create failed: ${it.safeMessage()}") }
                .onSuccess { id ->
                    if (!folderId.isNullOrBlank()) {
                        rollMembersIntoProjectRoster(folderId, members)
                    }
                    update {
                        copy(
                            screen = if (openOnReady) MainScreen.Chat else screen,
                            status = "Group created",
                            busy = false,
                            newGroupContext = NewGroupContext(),
                        )
                    }
                    if (openOnReady) openConversation(id)
                }
        }
    }

    /**
     * NewGroupChatDialog Step 4.2 — diff the dialog state against the
     * preloaded project roster and call `folder.member.add` for net-new
     * entries. Silently swallows `invalid_params: alias 'X' already
     * exists in this project` (race with another client touching the
     * same roster); other failures are surfaced.
     */
    private suspend fun rollMembersIntoProjectRoster(
        folderId: String,
        members: List<GroupMemberInput>,
    ) {
        val preloaded = uiState.value.newGroupContext.preloadedMembers
        val preloadedKeys = preloaded.map { "${it.agentId}|${it.alias.lowercase()}" }.toSet()
        members.forEach { m ->
            val key = "${m.agentId}|${m.alias.lowercase()}"
            if (key in preloadedKeys) return@forEach
            // Project-level coordinator is a separate concept — never carry
            // chat-level coordinator state into the project roster.
            repository.addFolderMember(
                folderId = folderId,
                agentId = m.agentId,
                alias = m.alias,
                isCoordinator = false,
            ).onFailure { e ->
                val msg = e.safeMessage()
                if (!msg.contains("alias '", ignoreCase = true) ||
                    !msg.contains("already exists", ignoreCase = true)
                ) {
                    fail("Could not add ${m.alias} to the project roster: $msg")
                }
            }
        }
    }

    // ---- Paired clients ----------------------------------------------------

    fun refreshClients() {
        viewModelScope.launch {
            if (uiState.value.activeHost?.status != HostStatus.Connected) {
                update { copy(settingsStatus = "Connect a host before loading paired clients", busy = false) }
                return@launch
            }
            setBusy("Loading paired clients")
            repository.listClients()
                .onFailure { fail("Client load failed: ${it.safeMessage()}") }
                .onSuccess { clients ->
                    // Match the current device by client_id (received from
                    // auth.pair / auth.me) — name alone is not unique, the
                    // user can re-pair multiple Android devices with the
                    // default `Android` name.
                    val ourId = uiState.value.authenticatedClientId
                    val tagged = clients.map { it.copy(current = ourId.isNotBlank() && it.id == ourId) }
                    update {
                        copy(
                            clients = tagged,
                            settingsStatus = if (tagged.isEmpty()) "No paired clients on this host"
                            else "${tagged.count { !it.revoked }} active · ${tagged.count { it.revoked }} revoked",
                            status = "Loaded ${tagged.size} paired device${if (tagged.size == 1) "" else "s"}",
                            busy = false,
                        )
                    }
                }
        }
    }

    /**
     * Revoke a paired client by id. Uses an optimistic update so the row
     * leaves the active list immediately; on wire failure the row is
     * reverted. The host's `clients.list` still returns revoked rows
     * (`includeRevoked=true`) so the user can audit them via the Revoked
     * devices sub-screen, but the main Settings list shows only active
     * clients.
     */
    fun revokeClient(clientId: String) {
        viewModelScope.launch {
            setBusy("Revoking client")
            // Optimistic: mark the matching client revoked locally so
            // the active list in SettingsScreen shrinks immediately
            // and the row appears in RevokedDevicesScreen on the next
            // drill-in.
            update {
                copy(clients = clients.map {
                    if (it.id == clientId) it.copy(revoked = true) else it
                })
            }
            repository.revokeClient(clientId)
                .onFailure {
                    // Revert the optimistic flip so the row reappears
                    // in the active list and the user can retry.
                    update {
                        copy(clients = clients.map {
                            if (it.id == clientId) it.copy(revoked = false) else it
                        })
                    }
                    fail("Client revoke failed: ${it.safeMessage()}")
                }
                .onSuccess {
                    update { copy(settingsStatus = "Client revoked", status = "Client revoked", busy = false) }
                    refreshClients()
                }
        }
    }

    /** Open the read-only Revoked devices sub-screen. */
    fun showRevokedDevices() = update { copy(screen = MainScreen.RevokedDevices) }

    /** Close the Revoked devices sub-screen and return to Settings. */
    fun closeRevokedDevices() = update { copy(screen = MainScreen.Settings) }

    /** Open the About sub-screen. */
    fun showAbout() = update { copy(screen = MainScreen.About) }

    /** Close the About sub-screen and return to Settings. */
    fun closeAbout() = update { copy(screen = MainScreen.Settings) }

    /** Open the in-app Help screen (user-docs viewer). */
    fun showHelp() = update { copy(screen = MainScreen.Help, selectedHelpDoc = null) }

    /** Close the Help screen and return to Settings. */
    fun closeHelp() = update { copy(screen = MainScreen.Settings, selectedHelpDoc = null) }

    /** Open one of the bundled help docs by slug (e.g. "01-install-and-pair"). */
    fun openHelpDoc(slug: String) = update { copy(selectedHelpDoc = slug) }

    /** Close the per-doc view and return to the Help index. */
    fun closeHelpDoc() = update { copy(selectedHelpDoc = null) }

    // ---- Read-only catalogs -----------------------------------------------
    //
    // These ops have no host-side change events (admin-driven, infrequent).
    // We refresh on screen-open and on user-initiated pull-to-refresh; status
    // strings track the last load result without polluting the global
    // [MainUiState.status] line.

    fun refreshTools() {
        viewModelScope.launch {
            if (uiState.value.activeHost?.status != HostStatus.Connected) {
                update { copy(toolsStatus = "Connect a host before loading tools") }
                return@launch
            }
            update { copy(toolsStatus = "Loading tools…") }
            repository.listTools()
                .onSuccess { tools ->
                    update {
                        copy(
                            tools = tools,
                            toolsStatus = if (tools.isEmpty()) "No tools registered" else "${tools.size} tool${if (tools.size == 1) "" else "s"}",
                        )
                    }
                }
                .onFailure { update { copy(toolsStatus = "Tool load failed: ${it.safeMessage()}") } }
        }
    }

    fun refreshMcpServers() {
        viewModelScope.launch {
            if (uiState.value.activeHost?.status != HostStatus.Connected) {
                update { copy(mcpStatus = "Connect a host before loading MCP servers") }
                return@launch
            }
            update { copy(mcpStatus = "Loading MCP servers…") }
            repository.listMcpServers()
                .onSuccess { servers ->
                    update {
                        copy(
                            mcpServers = servers,
                            mcpStatus = if (servers.isEmpty()) "No MCP servers configured"
                            else "${servers.size} server${if (servers.size == 1) "" else "s"}",
                        )
                    }
                }
                .onFailure { update { copy(mcpStatus = "MCP server load failed: ${it.safeMessage()}") } }
        }
    }

    fun refreshMcpServerTools(serverName: String) {
        viewModelScope.launch {
            if (uiState.value.activeHost?.status != HostStatus.Connected) return@launch
            repository.listMcpServerTools(serverName)
                .onSuccess { tools ->
                    update { copy(mcpServerTools = mcpServerTools + (serverName to tools)) }
                }
                .onFailure {
                    update { copy(mcpStatus = "MCP server tools load failed: ${it.safeMessage()}") }
                }
        }
    }

    fun refreshSkills() {
        viewModelScope.launch {
            if (uiState.value.activeHost?.status != HostStatus.Connected) {
                update { copy(skillsStatus = "Connect a host before loading skills") }
                return@launch
            }
            update { copy(skillsStatus = "Loading skills…") }
            repository.listSkills()
                .onSuccess { skills ->
                    update {
                        copy(
                            skills = skills,
                            skillsStatus = if (skills.isEmpty()) "No skills installed"
                            else "${skills.size} skill${if (skills.size == 1) "" else "s"}",
                        )
                    }
                }
                .onFailure { update { copy(skillsStatus = "Skill load failed: ${it.safeMessage()}") } }
        }
    }

    // ---- Internals ---------------------------------------------------------

    override fun onCleared() {
        session.close()
        super.onCleared()
    }

    private suspend fun loadHosts() {
        val hosts = hostStore.readHosts()
        update {
            copy(
                hosts = hosts.map {
                    HostUi(
                        config = it,
                        status = HostStatus.Disconnected,
                        detail = if (it.lastConnectedAt != null) "Saved host" else "Not connected",
                    )
                },
            )
        }
    }

    private suspend fun persistHosts(hosts: List<HostConfig>) {
        hostStore.saveHosts(hosts)
    }

    /**
     * Reuse a stored token to authenticate either after explicit Connect from
     * the host list, or silently after the WebSocket drops. The auth.token
     * response already carries the client identity, so we only call auth.me
     * afterwards as a "still alive?" round-trip — both ops are real host
     * endpoints and we want each exercised in the normal flow rather than
     * orphaned.
     *
     * @param silent  true when we're recovering from a drop. Suppresses the
     *                "navigate home + clear status" side-effects so the user
     *                doesn't get bounced out of an open chat by a transient
     *                network blip.
     */
    private suspend fun authenticateWithToken(hostId: String, token: String, silent: Boolean = false) {
        repository.authenticateToken(token)
            .onFailure { error ->
                // Critical: do NOT clear the stored token here. Earlier
                // versions did, but that turned every transient socket
                // drop / timeout / "host rebooting" into "user must
                // re-pair" — destroying credentials the user took the
                // trouble to type a 6-digit code for.
                //
                // Tokens are cleared in exactly three places:
                //   - revokeSelf()             (user-initiated)
                //   - removeHost()             (user-initiated)
                //   - "auth.revoked" event     (host-initiated, authoritative)
                //
                // Anything else (bad RTT, server bounce, kernel TCP RST,
                // certificate hiccup) is a connectivity problem, not an
                // auth one — surface it as Error and let auto-reconnect
                // try again.
                val message = error.safeMessage()
                val isAuthRevoked = message.contains("auth_revoked", ignoreCase = true) ||
                    message.contains("auth_failed", ignoreCase = true) ||
                    message.contains("invalid token", ignoreCase = true) ||
                    // The host's auth_failed detail for a revoked or unknown
                    // token is "invalid or revoked token"; the error kind is
                    // not part of the exception message.
                    message.contains("revoked token", ignoreCase = true)
                if (isAuthRevoked) {
                    runCatching { tokenStore.clearToken(hostId) }
                    setHostStatus(hostId, HostStatus.AuthRequired, message)
                    fail("Token rejected by host: $message")
                } else {
                    setHostStatus(hostId, HostStatus.Error, message)
                    // Background retries keep failing while the host is
                    // away; the host row already says so, so don't pop a
                    // message for every attempt.
                    fail("Reconnect failed: $message", notify = !silent)
                }
            }
            .onSuccess { auth ->
                reconnectAttempts = 0
                setHostStatus(hostId, HostStatus.Connected, "Authenticated as ${auth.name}")
                markHostConnected(hostId)
                update {
                    copy(
                        authenticatedName = auth.name,
                        authenticatedClientId = auth.clientId,
                        authenticatedLastSeenAt = auth.lastSeenAt ?: 0L,
                        status = if (silent) status else "Authenticated as ${auth.name}",
                        screen = if (silent) screen else MainScreen.Home,
                        busy = false,
                    )
                }
                // auth.me round-trip — confirms session liveness and refreshes
                // last_seen_at. The host updates last_seen_at on this call.
                repository.authMe().onSuccess { me ->
                    update { copy(authenticatedLastSeenAt = me.lastSeenAt ?: authenticatedLastSeenAt) }
                }
                // The remote server can be up while the desktop app behind it
                // is not; every op would then fail. Say so instead of showing
                // a plain "Connected".
                repository.ping().onSuccess { pong ->
                    if (!pong.hostAlive) reportDesktopNotRunning(hostId)
                }
                if (!silent) {
                    refreshConversations()
                    refreshClients()
                }
                pendingOpenConversationId?.let { convId ->
                    pendingOpenConversationId = null
                    openConversation(convId)
                }
                // Catalog is small + cheap; load it on every reconnect, not
                // just user-initiated logins. The provider list rarely
                // changes but the active selection might have drifted while
                // we were offline.
                loadModelCatalog()
                // Agents per host spec: refresh on connect + reconnect.
                // No event yet so this is the only way to pick up
                // desktop-side agent CRUD.
                loadAgents()
            }
    }

    private suspend fun markHostConnected(hostId: String) {
        val now = System.currentTimeMillis()
        val configs = uiState.value.hosts.map {
            if (it.config.id == hostId) it.config.copy(lastConnectedAt = now) else it.config
        }
        persistHosts(configs)
        update {
            copy(
                hosts = hosts.map {
                    if (it.config.id == hostId) it.copy(config = it.config.copy(lastConnectedAt = now)) else it
                },
            )
        }
    }

    private fun endpointFromInputOrReport(): RemoteEndpoint? =
        runCatching { RemoteEndpoint.fromEndpoint(uiState.value.addEndpoint) }
            .onFailure { fail(it.safeMessage()) }
            .getOrNull()

    /**
     * Event handler. Only handles the events the host actually emits from
     * `remote-ws-session.cpp`: `hello`, `error`, `message.added`,
     * `message.updated`, `message.deleted`, and the three `message.streaming.*`
     * variants. Anything else is unknown — we log and ignore.
     */
    private fun handleEvent(event: RemoteEvent) {
        when (event.event) {
            "hello" -> update { copy(hostHello = event.data?.toString() ?: "hello") }
            "message.added", "message.updated" -> mergeMessageEvent(event, mergeMode = MergeMode.Replace)
            "message.deleted" -> {
                val id = (event.data as? JsonObject)?.string("msg_id", "id", "message_id")
                if (id != null) update { copy(messages = messages.filterNot { it.id == id }) }
            }
            "message.streaming.started" -> mergeStreamingEvent(event, StreamingKind.Started)
            "message.streaming.delta" -> mergeStreamingEvent(event, StreamingKind.Delta)
            "message.streaming.aborted" -> mergeStreamingEvent(event, StreamingKind.Aborted)

            // Context gauge feed: per-conversation fill percentage measured
            // by the host at request build.
            "chat.context_fill.changed" -> {
                val data = event.data as? JsonObject ?: return
                val convId = data.string("conv_id") ?: return
                val percent = data["percent"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: return
                update {
                    copy(contextFillPercents = contextFillPercents + (convId to percent))
                }
            }

            // An agent @-mentioned the user. Raise a system notification
            // unless the mentioning conversation is currently open.
            "chat.user_mentioned" -> {
                val data = event.data as? JsonObject ?: return
                val convId = data.string("conv_id") ?: return
                val alias = data.string("alias").orEmpty()
                val text = data.string("text").orEmpty()
                if (uiState.value.selectedConversationId != convId) {
                    notifier?.showMention(convId = convId, alias = alias, text = text)
                }
            }

            // Conversation list events. The host fires these to every
            // authenticated session so the sidebar stays current without
            // polling — Android matches the desktop behaviour.
            "conv.added", "conv.updated" -> handleConversationSnapshotEvent(event)
            "conv.deleted" -> {
                // The host sends `{conv_id}`; `id` is accepted as well.
                val id = (event.data as? JsonObject)?.string("conv_id", "id") ?: return
                update {
                    copy(
                        conversations = conversations.filterNot { it.id == id },
                        deletingConversationIds = deletingConversationIds - id,
                        // If the user has the deleted conv open, close it out.
                        selectedConversationId = selectedConversationId?.takeUnless { it == id },
                        messages = if (selectedConversationId == id) emptyList() else messages,
                    )
                }
            }
            "conv.renamed" -> {
                val data = event.data as? JsonObject ?: return
                val id = data.string("id") ?: return
                val title = data.string("title") ?: return
                update {
                    copy(conversations = conversations.map {
                        if (it.id == id) it.copy(title = title) else it
                    })
                }
            }

            // Project Rooms events.
            //
            // project_template.project_created fires after a successful
            // create_project on any client. This client only uses it to
            // label the kickoff sheet of its own create; sibling clients
            // pick up the new folder through folder.added.
            //
            // project_template.catalog_changed fires when any client
            // saves / pins / unpins / deletes a user template. Every
            // paired client refreshes its catalog so the Template
            // Library stays current.
            "project_template.project_created" -> {
                // Broadcast to every client, including ones that did not
                // start the create, so it never navigates by itself. It is
                // kept for this client's own create to pick up by folder id.
                val data = event.data as? JsonObject ?: return
                val folderId = data.string("folder_id") ?: return
                val folderName = data.string("folder_name").orEmpty()
                val memberCount = (data["member_count"] as? JsonPrimitive)
                    ?.intOrNull ?: 0
                if (!uiState.value.projectTemplateCreating) return
                if (projectCreatedEvents.size >= 8) {
                    projectCreatedEvents.remove(projectCreatedEvents.keys.first())
                }
                projectCreatedEvents[folderId] = com.verzeta.android.data.projectroom
                    .ProjectTemplateCreatedUi(
                        folderId = folderId,
                        folderName = folderName.ifBlank { "New project" },
                        memberCount = memberCount,
                    )
            }
            "project_template.catalog_changed" -> {
                refreshProjectTemplates()
                refreshLandingProjectTemplates()
            }

            // Folder list events.
            "folder.added", "folder.updated" -> handleFolderSnapshotEvent(event)
            "folder.deleted" -> {
                // The host sends `{folder_id}`; `id` is accepted as well.
                val id = (event.data as? JsonObject)?.string("folder_id", "id") ?: return
                update {
                    copy(
                        folders = folders.filterNot { it.id == id },
                        folderMembers = folderMembers - id,
                        // If the user is browsing the deleted folder, bounce
                        // back to the folder list.
                        selectedFolderId = selectedFolderId?.takeUnless { it == id },
                        screen = if (selectedFolderId == id && screen == MainScreen.FolderConversations) {
                            MainScreen.Folders
                        } else {
                            screen
                        },
                    )
                }
            }

            // Folder / conversation membership events. The host fires these
            // after every membership write so the writer and sibling clients
            // converge. The payload normally carries only the id; when it
            // has no `members` array the list is re-read with
            // folder.members / conv.members instead of being treated as
            // empty.
            "folder.members.changed" -> {
                val data = event.data as? JsonObject ?: return
                val folderId = data.string("folder_id") ?: return
                val members = (data["members"] as? kotlinx.serialization.json.JsonArray)
                    ?.let(repository::parseMembersFromArray)
                if (members != null) {
                    update { copy(folderMembers = folderMembers + (folderId to members)) }
                } else {
                    val state = uiState.value
                    val known = folderId in state.folderMembers ||
                        state.folders.any { it.id == folderId }
                    if (known) loadFolderMembers(folderId)
                }
            }
            "conv.members.changed" -> {
                val data = event.data as? JsonObject ?: return
                val convId = data.string("conv_id") ?: return
                val members = (data["members"] as? kotlinx.serialization.json.JsonArray)
                    ?.let(repository::parseMembersFromArray)
                if (members != null) {
                    update { copy(conversationMembers = conversationMembers + (convId to members)) }
                } else if (convId in uiState.value.conversationMembers) {
                    viewModelScope.launch {
                        repository.listConversationMembers(convId).onSuccess { list ->
                            update { copy(conversationMembers = conversationMembers + (convId to list)) }
                        }
                    }
                }
            }

            // Heartbeat config CRUD events. Replaces or removes the matching
            // config in folderHeartbeats / conversationHeartbeats so the
            // editor list stays current without polling. The config payload
            // mirrors `configByIdMap` (camelCase, see
            // remote-ws-session.cpp:2611-2630).
            "heartbeat.config.changed" -> {
                val data = event.data as? JsonObject ?: return
                val cfg = repository.parseHeartbeatConfigObject(data) ?: return
                update {
                    val key = cfg.scopeId
                    if (cfg.scopeType == HeartbeatScope.Folder) {
                        val existing = folderHeartbeats[key].orEmpty()
                        val idx = existing.indexOfFirst { it.id == cfg.id }
                        val next = if (idx >= 0) existing.toMutableList().apply { set(idx, cfg) }
                        else existing + cfg
                        copy(folderHeartbeats = folderHeartbeats + (key to next))
                    } else {
                        // Conversation-scoped configs aren't surfaced in the
                        // folder editor; keep the cache available for a
                        // future per-conversation settings screen.
                        this
                    }
                }
            }
            "heartbeat.config.removed" -> {
                val data = event.data as? JsonObject ?: return
                val id = data.string("id") ?: return
                update {
                    val updated = folderHeartbeats.mapValues { (_, list) ->
                        list.filterNot { it.id == id }
                    }
                    copy(folderHeartbeats = updated)
                }
            }

            // Plan / step lifecycle events. All four fire globally to every
            // authenticated session — the handler filters by conversationId
            // so an overlay opened on conv A doesn't render plans from conv B.
            "plan.created", "plan.updated" -> {
                val data = event.data as? JsonObject ?: return
                val plan = repository.parsePlanObject(data) ?: return
                update {
                    var next = upsertPlan(plan)
                    // If we were in the middle of starting a task for this
                    // conv, this is the response we were waiting for —
                    // clear the "Starting…" indicator.
                    if (taskStartingConvId != null && plan.conversationId == taskStartingConvId) {
                        next = next.copy(taskStartingConvId = null)
                    }
                    next
                }
            }
            "plan.deleted" -> {
                val data = event.data as? JsonObject ?: return
                val id = data.string("id") ?: return
                update {
                    val updated = convPlans.mapValues { (_, list) ->
                        list.filterNot { it.id == id }
                    }
                    copy(convPlans = updated)
                }
            }
            "step.updated" -> {
                // The host only sends `{step_id, plan_id?}`; we re-pull the
                // parent plan via plan.get to refresh the steps array (matches
                // what PlansModel does on a step-row dataChanged — it re-emits
                // the parent plan's StepsRole so QML rebinds the whole row).
                val data = event.data as? JsonObject ?: return
                val planId = data.string("plan_id") ?: return
                viewModelScope.launch {
                    repository.getPlan(planId).onSuccess { plan ->
                        update { upsertPlan(plan) }
                    }
                    // Plan may have been deleted between the step.updated
                    // event and our follow-up plan.get — that's a race we
                    // ignore (the plan.deleted event will clean up).
                }
            }

            // ---- Tool calls + agent lifecycle ---------------------------
            //
            // tool_call.added / .updated are filtered by m_subscribedConvIds
            // server-side; Android receives them only for the conversation
            // it has subscribed to via msg.subscribe (handled in
            // openConversation). The other three events are host-wide.
            "tool_call.added", "tool_call.updated" -> {
                val data = event.data as? JsonObject ?: return
                val convId = data.string("conv_id") ?: return
                val call = repository.parseToolCallObject(data) ?: return
                update { upsertToolCall(convId, call) }
            }
            "tool_call.requested" -> {
                val data = event.data as? JsonObject ?: return
                val callId = data.string("call_id") ?: return
                val toolName = data.string("tool_name").orEmpty()
                val args = data["arguments"] ?: kotlinx.serialization.json.JsonNull
                val msgId = data.string("message_id").orEmpty()
                update {
                    copy(
                        pendingToolConfirmation = PendingToolConfirmationUi(
                            callId = callId,
                            toolName = toolName,
                            arguments = args,
                            messageId = msgId,
                        ),
                    )
                }
            }
            "tool_call.completed" -> {
                // Dismiss the modal if it still references this call_id
                // (user may have approved/denied via desktop, or this
                // arrived before the user clicked anything). The matching
                // tool_call.updated will patch the row in the log.
                val data = event.data as? JsonObject ?: return
                val callId = data.string("call_id") ?: return
                update {
                    if (pendingToolConfirmation?.callId == callId) {
                        copy(pendingToolConfirmation = null)
                    } else this
                }
            }
            "agent.step.started" -> {
                val data = event.data as? JsonObject ?: return
                val iteration = data["iteration"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
                val description = data.string("description").orEmpty()
                update {
                    copy(
                        // Bounded: without a host run-state signal the list
                        // is only cleared on conversation switch.
                        agentSteps = (agentSteps + AgentStepUi(
                            iteration = iteration,
                            description = description,
                            status = AgentStepStatus.Running,
                        )).takeLast(MAX_AGENT_STEPS),
                    )
                }
            }
            "agent.step.completed" -> {
                val data = event.data as? JsonObject ?: return
                val description = data.string("description").orEmpty()
                val success = data["success"]?.jsonPrimitive?.booleanOrNull
                    ?: (data["success"]?.jsonPrimitive?.contentOrNull?.equals("true", ignoreCase = true) == true)
                update {
                    // Find the most recent matching (description, Running)
                    // step and update it in place. Mirrors QML's "find the
                    // last running row matching description" behaviour.
                    val idx = agentSteps.indexOfLast {
                        it.description == description && it.status == AgentStepStatus.Running
                    }
                    if (idx < 0) this else copy(
                        agentSteps = agentSteps.toMutableList().apply {
                            set(idx, get(idx).copy(
                                status = if (success) AgentStepStatus.Success else AgentStepStatus.Error,
                            ))
                        },
                    )
                }
            }
            // ---- Artifacts + generated images/audio -------------------
            //
            // artifact.added fires per-row alongside the message.added that
            // produced it; image.generated / audio.generated fire when those
            // services finalise a file. All three are server-filtered by
            // the session's msg.subscribe set, so we only see events for
            // the conv we're viewing.
            "artifact.added" -> {
                val data = event.data as? JsonObject ?: return
                val convId = data.string("conv_id") ?: return
                val row = repository.parseArtifactObject(data) ?: return
                update { upsertArtifact(convId, row) }
            }
            "image.generated" -> {
                val data = event.data as? JsonObject ?: return
                val convId = data.string("conv_id") ?: return
                // The host sends the absolute `path`; downloads route by
                // file name, so keep only the basename.
                val name = generatedFileName(data) ?: return
                val bytes = data["total_bytes"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L
                update { upsertGeneratedFile(GenBucket.Image, convId, GeneratedFileUi(name, bytes, exists = bytes >= 0)) }
            }
            "audio.generated" -> {
                val data = event.data as? JsonObject ?: return
                val convId = data.string("conv_id") ?: return
                val name = generatedFileName(data) ?: return
                val bytes = data["total_bytes"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L
                update { upsertGeneratedFile(GenBucket.Audio, convId, GeneratedFileUi(name, bytes, exists = bytes >= 0)) }
            }

            // ---- Canvas + console + run-state ------------------------
            //
            // canvas.opened / .updated / .closed are server-filtered by
            // msg.subscribe set; canvas.error / canvas.run.line /
            // canvas.run.state are UNFILTERED (host-wide). The console
            // is a single-instance singleton per the user's directive
            // so every paired client renders the same stream.
            // Poll events.
            //
            // poll.created → also triggers a messages-list refresh so
            //   the auto-persisted poll-card message (carrying
            //   metadata.poll_id) lands in the conversation timeline.
            //   The host's `Signals::MessageAdded` only carries
            //   {conv_id, msg_id}, not the full row, so without this
            //   refresh the new message wouldn't pick up the metadata
            //   path on its own.
            // poll.updated / poll.closed / poll.vote_cast → just refresh
            //   the convPolls map so the inline PollCard re-reads its
            //   state. No message refresh needed (the message bubble
            //   itself doesn't change; only the card inside it).
            "poll.created" -> {
                val data = event.data as? JsonObject ?: return
                val convId = data.string("conv_id") ?: return
                val pollId = data.string("poll_id")
                if (pollId.isNullOrBlank()) {
                    refreshPollsForConv(convId)
                } else {
                    refreshSinglePoll(convId, pollId)
                }
                if (convId == uiState.value.selectedConversationId) {
                    refreshMessagesForConv(convId)
                }
            }
            "poll.updated", "poll.closed", "poll.vote_cast" -> {
                val data = event.data as? JsonObject ?: return
                val convId = data.string("conv_id") ?: return
                val pollId = data.string("poll_id")
                if (pollId.isNullOrBlank()) {
                    refreshPollsForConv(convId)
                } else {
                    refreshSinglePoll(convId, pollId)
                }
            }

            "canvas.opened", "canvas.updated" -> {
                val data = event.data as? JsonObject ?: return
                val convId = data.string("conv_id") ?: return
                // The daemon (wire-session.cpp) enriches both canvas.opened
                // and canvas.updated payloads with the full canvas row
                // shape parseCanvasObject expects. Pre-enrichment the event
                // only carried {conv_id, canvas_id} and this parse call
                // bailed silently, leaving convCanvas[convId] empty and
                // the Canvas top-bar button hidden.
                val canvas = repository.parseCanvasObject(data) ?: return
                update { copy(convCanvas = convCanvas + (convId to canvas)) }
                if (canvas.language.isNotBlank()) loadCanvasActionsForLanguage(canvas.language)
                // Auto-surface the Canvas screen when the AGENT opens a
                // canvas in the currently-selected conversation. Mirrors
                // the desktop QML behaviour (frontend/pages/MainWindow.qml
                // onCanvasOpened sets canvasShown = true + flips the
                // mobile tab to Canvas). Scope tight on purpose:
                //   - canvas.opened ONLY (not .updated — every edit
                //     revision fires .updated; stealing focus mid-read
                //     would be infuriating).
                //   - Only when the user is on the Chat screen for THIS
                //     conv (don't yank them away from Settings, Folders,
                //     etc., and don't navigate for a background conv).
                if (event.event == "canvas.opened"
                    && convId == uiState.value.selectedConversationId
                    && uiState.value.screen == MainScreen.Chat) {
                    update { copy(screen = MainScreen.Canvas) }
                    // Same as opening the canvas by hand: without this the
                    // Run and IDE buttons stay hidden.
                    loadCanvasRunCapabilities()
                }
            }
            "canvas.closed" -> {
                val data = event.data as? JsonObject ?: return
                val convId = data.string("conv_id") ?: return
                update { copy(convCanvas = convCanvas - convId) }
            }
            "canvas.error" -> {
                val data = event.data as? JsonObject ?: return
                val msg = data.string("message").orEmpty()
                fail(if (msg.isBlank()) "Canvas error" else "Canvas error: $msg")
            }
            "canvas.run.line" -> {
                val data = event.data as? JsonObject ?: return
                val line = ConsoleLineUi(
                    kind = ConsoleLineKind.fromWire(data.string("kind")),
                    text = data.string("text").orEmpty(),
                    timestamp = data.string("timestamp").orEmpty(),
                )
                update {
                    // Keep the rolling buffer to 50 entries. The host already
                    // FIFO-trims at 50; this protects the local cache from
                    // any future host-side change.
                    val next = (consoleLines + line).takeLast(50)
                    copy(
                        consoleLines = next,
                        // Auto-open the console on the first line of a run.
                        consoleVisible = consoleVisible || canvasRunState.running ||
                            line.kind == ConsoleLineKind.System,
                    )
                }
            }
            "canvas.run.state" -> {
                // The host's payload is empty: it only says that the run
                // state changed. Read the state with canvas.run.is_running
                // unless a `running` flag is present.
                val data = event.data as? JsonObject
                val running = data?.get("running")?.jsonPrimitive?.booleanOrNull
                if (running != null) {
                    applyCanvasRunning(running)
                } else {
                    viewModelScope.launch {
                        repository.getCanvasRunIsRunning().onSuccess(::applyCanvasRunning)
                    }
                }
            }

            "agent.run.state" -> {
                // Applied only when the payload carries `is_running`. The
                // current host sends an empty object, which says nothing
                // about the state, so it must not be read as "stopped" (that
                // used to wipe the step list on every event).
                val data = event.data as? JsonObject ?: return
                val runningPrim = data["is_running"]?.jsonPrimitive ?: return
                val running = runningPrim.booleanOrNull
                    ?: runningPrim.contentOrNull.equals("true", ignoreCase = true)
                val iteration = data["current_iteration"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                    ?: uiState.value.agentRunState.currentIteration
                update {
                    copy(
                        agentRunState = AgentRunStateUi(
                            isRunning = running,
                            currentIteration = iteration,
                        ),
                        // When a run finishes, drop the step list so the next
                        // run starts clean.
                        agentSteps = if (!running) emptyList() else agentSteps,
                    )
                }
            }

            // Per-provider live model list arriving from the host's async
            // refresh (provider's GET /models). Replace this provider's
            // models array in the cached catalog. Active selection is
            // unaffected per host spec — even if the previously-active
            // model has dropped out of the new list, leave it selected and
            // let the user pick something else manually. See
            // remote-ws-session.cpp:442-455 (sendEvent body) and
            // agent-settings-controller.cpp:152-159 (refreshModels trigger).
            "models.refreshed" -> {
                val data = event.data as? JsonObject
                if (data != null) {
                    val providerId = data.string("provider_id").orEmpty()
                    val models = (data["models"] as? kotlinx.serialization.json.JsonArray)
                        ?.mapNotNull { it.jsonPrimitive.contentOrNull }
                        ?: emptyList()
                    if (providerId.isNotBlank()) {
                        update {
                            val idx = modelCatalog.providers
                                .indexOfFirst { it.providerId == providerId }
                            if (idx < 0) this else copy(
                                modelCatalog = modelCatalog.copy(
                                    providers = modelCatalog.providers.toMutableList().apply {
                                        set(idx, get(idx).copy(models = models))
                                    },
                                ),
                            )
                        }
                    }
                }
            }

            // Model catalog active-pair changes. Fired by the host whenever
            // any client mutates it (this Android device, the desktop, a
            // sibling tablet) — so we re-apply the snapshot regardless of
            // who initiated. The pendingAfterModelChange hook lets a chained
            // create-conversation action wait for confirmation that the new
            // selection is live before stamping it onto a new row.
            "models.active_changed" -> {
                val data = event.data as? JsonObject
                if (data != null) {
                    val activeProvider = data.string("active_provider").orEmpty()
                    val activeModel = data.string("active_model").orEmpty()
                    update {
                        copy(modelCatalog = modelCatalog.copy(
                            activeProvider = activeProvider,
                            activeModel = activeModel,
                        ))
                    }
                }
                pendingAfterModelChange?.invoke()
                pendingAfterModelChange = null
            }

            // Per-conversation agent settings (agent_pattern / require_confirmation /
            // tools_enabled) live in llm_config. Every write fires
            // ConversationService::conversationUpdated → conv.updated, which
            // handleConversationSnapshotEvent uses to refresh any open
            // ConvSettingsScreen. The per-field events below are redundant
            // (they fire from local AgentSettings instances that wire clients
            // do not subscribe to). Explicit no-op handlers prevent silent
            // fall-through.
            "agent.pattern.changed",
            "agent.require_confirmation.changed",
            "tools.enabled.changed",
            "conv.settings.changed" -> {
                // Intentionally noop — conv.updated carries the data.
            }

            // Per-client queue notification. Fires when the wire client's
            // send had to queue behind another instance's in-flight slot.
            // Surfaces a chatStatus message so the user knows their send is
            // pending. The bridge subscribes to wire events only (per-client
            // CC events go through SessionRouter), so this is the correct
            // path to reach Android for queue state.
            "user.message.queued" -> {
                update {
                    copy(
                        chatStatus = "Message queued until the host finishes its current turn",
                        busy = false,
                    )
                }
            }

            // No "rag.enabled.changed" case: the host stopped emitting that event
            // when RAG became per-conversation. The handler that used to live here
            // fanned one flip across every cached conversation, which is the wrong
            // model now. Per-conversation changes arrive on conv.settings.changed.

            "error" -> {
                // Never paint raw error JSON onto the user-facing status
                // string. Parse the {kind, detail} envelope and store the
                // technical detail under [lastConnectionError] for the
                // home-screen "Unavailable" pill's tap-to-detail dialog.
                // Status keeps its prior value so the UI renders a polished
                // sentence rather than a JSON dump.
                val obj = event.data as? JsonObject
                val kind = obj?.string("kind").orEmpty()
                val detail = obj?.string("detail").orEmpty()
                val composed = when {
                    detail.isNotBlank() && kind.isNotBlank() ->
                        "[$kind] $detail"
                    detail.isNotBlank() -> detail
                    kind.isNotBlank()   -> kind
                    else                -> event.data?.toString()
                        ?: "Unknown host error"
                }
                update { copy(lastConnectionError = composed, busy = false) }
            }
        }
    }

    /**
     * Handle `conv.added` / `conv.updated`. The payload is the same key
     * set as a `conv.list` row, so we hand it to the same parser used by
     * [RemoteRepository.listConversations].
     */
    private fun handleConversationSnapshotEvent(event: RemoteEvent) {
        val obj = event.data as? JsonObject ?: return
        val incoming = parseConversationFromEvent(obj) ?: return
        update {
            val existingIdx = conversations.indexOfFirst { it.id == incoming.id }
            val merged = if (existingIdx >= 0) {
                // Preserve client-only state (streaming flag) when host
                // re-emits a snapshot.
                conversations[existingIdx].let { existing ->
                    incoming.copy(streaming = existing.streaming)
                }
            } else {
                incoming
            }
            val nextConversations = if (existingIdx >= 0) {
                conversations.toMutableList().apply { set(existingIdx, merged) }
            } else {
                conversations + merged
            }
            copy(
                conversations = nextConversations,
                deletingConversationIds = deletingConversationIds - merged.id,
                recentConversations = activeHostId?.let { hostId ->
                    nextConversations.take(8).map { it.toRecent(hostId) }
                } ?: recentConversations,
            )
        }
        // When a conv's row is updated on the host (any llm_config write —
        // model, system prompt, agent pattern, tools toggle, etc.) and we
        // already have a settings snapshot cached, re-fetch so the
        // ConvSettingsScreen reflects the latest values.
        // ConversationService::conversationUpdated is the canonical
        // "this conv mutated" trigger; the bridge forwards it as conv.updated
        // and Android uses it to converge any open settings panel without
        // per-field refresh events.
        if (uiState.value.convSettings.containsKey(incoming.id)) {
            loadConvSettings(incoming.id)
        }
    }

    private fun parseConversationFromEvent(obj: JsonObject): ConversationUi? {
        val id = obj.string("id") ?: return null
        return ConversationUi(
            id = id,
            title = obj.string("title").orEmpty().ifBlank { "Untitled" },
            folderId = obj.string("folder_id"),
            primaryAgentId = obj.string("primary_agent_id"),
            isGroup = obj.intFlag("is_group"),
            isPinned = obj.intFlag("is_pinned"),
            groupAgentIds = (obj["group_agent_ids"] as? kotlinx.serialization.json.JsonArray)
                ?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList(),
            // Event rows carry epoch milliseconds; conv.list carries ISO.
            createdAt = normaliseTimestamp(obj.string("created_at").orEmpty()),
            updatedAt = normaliseTimestamp(obj.string("updated_at").orEmpty()),
            tokenTotal = obj["token_total"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L,
            systemPrompt = obj.string("system_prompt").orEmpty(),
        )
    }

    /**
     * Same fix as [RemoteRepository.intFlag]. The host emits `is_group`
     * and `is_pinned` as JSON booleans, the previous int-only parse
     * silently dropped every `true` and routed groups into Plain Chats.
     */
    private fun JsonObject.intFlag(key: String): Boolean {
        val prim = this[key]?.jsonPrimitive ?: return false
        prim.booleanOrNull?.let { return it }
        val text = prim.contentOrNull ?: return false
        return text.toIntOrNull()?.let { it != 0 } ?: text.equals("true", ignoreCase = true)
    }

    /**
     * Handle `folder.added` / `folder.updated`. Reuses the parser from
     * [RemoteRepository] so the wire shape is decoded the same way as
     * `folder.list_projects`. Non-project folders ARE allowed in this
     * stream; we filter them out client-side because the only places we
     * surface folders today are project/organization listings.
     */
    private fun handleFolderSnapshotEvent(event: RemoteEvent) {
        val obj = event.data as? JsonObject ?: return
        val parsed = runCatching { repository.parseFolderFromObject(obj) }.getOrNull() ?: return
        update {
            val existingIdx = folders.indexOfFirst { it.id == parsed.id }
            val merged = if (existingIdx >= 0) {
                folders.toMutableList().apply { set(existingIdx, parsed) }
            } else {
                folders + parsed
            }
            // When the user has toggled "Show all" we keep every folder
            // type so the cleanup surface can list legacy regulars.
            // Otherwise we hide regulars (host's `folder.list_projects`
            // semantics).
            val nextFolders = if (foldersShowAll) merged
            else merged.filter { it.folderType != FolderType.Regular }
            copy(folders = nextFolders)
        }
    }

    private enum class MergeMode { Replace, Append }

    /**
     * Handler for `message.added` and `message.updated`. Builds the final
     * [MessageUi], applies the [isVisibleInChat] filter, and routes to
     * either `messages` or `toolActivity` accordingly.
     *
     * If the same id is already in the chat list (e.g. an assistant
     * placeholder we inserted optimistically) and the host now signals it
     * as a tool-call placeholder, we *demote* it: drop from chat, push to
     * activity. The reverse direction also works — a row that was
     * previously hidden (e.g. tool message that arrived early via
     * `message.added`) but is later revised to a non-tool role.
     */
    private fun mergeMessageEvent(event: RemoteEvent, mergeMode: MergeMode) {
        val data = event.data as? JsonObject ?: return
        val convId = data.string("conversation_id", "conv_id")
        if (convId != null && convId != uiState.value.selectedConversationId) return
        val id = data.string("id", "msg_id", "message_id") ?: return
        val role = data.string("role").toMessageRole()
        val incomingText = data.string("content", "text", "delta").orEmpty()
        val createdAt = data.string("created_at").orEmpty()
        val agentId = data.string("agent_id")
        val memberAlias = data.string("member_alias")
        val turnId = data.string("turn_id")
        val finishReason = data.string("finish_reason").orEmpty()
        val tokenCount = data["token_count"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
        val modelUsed = data.string("model_used").orEmpty()
        // `thinking_content` flows on `message.added` / `message.updated`
        // payloads from hosts that support it. This handler builds `MessageUi`
        // directly from the event JSON rather than going through
        // `RemoteRepository.parseMessages`, so the parser extension in that
        // path does not cover this code path. Older hosts return null for the
        // key; `.orEmpty()` resolves it to "" and the `.ifBlank { ... }`
        // fallback preserves any value the chat row already had (defensive
        // against partial events).
        val thinkingContent = data.string("thinking_content").orEmpty()

        update {
            val existingChat = messages.firstOrNull { it.id == id }
            val existingActivity = toolActivity.firstOrNull { it.id == id }
            val baseRole = role.takeIf { it != MessageRole.Unknown }
                ?: existingChat?.role
                ?: existingActivity?.kind?.let {
                    if (it == ToolActivityUi.Kind.ToolResult) MessageRole.Tool else MessageRole.Assistant
                }
                ?: MessageRole.Unknown
            val baseText = if (mergeMode == MergeMode.Append && existingChat != null) {
                existingChat.text + incomingText
            } else {
                incomingText
            }
            val merged = MessageUi(
                id = id,
                role = baseRole,
                text = baseText,
                createdAt = createdAt.ifBlank { existingChat?.createdAt ?: existingActivity?.createdAt.orEmpty() },
                modelUsed = modelUsed.ifBlank { existingChat?.modelUsed.orEmpty() },
                tokenCount = tokenCount ?: existingChat?.tokenCount ?: 0L,
                finishReason = finishReason.ifBlank { existingChat?.finishReason.orEmpty() },
                agentId = agentId ?: existingChat?.agentId ?: existingActivity?.agentId,
                memberAlias = memberAlias ?: existingChat?.memberAlias,
                turnId = turnId ?: existingChat?.turnId ?: existingActivity?.turnId,
                streaming = false,
                thinkingContent = thinkingContent.ifBlank { existingChat?.thinkingContent.orEmpty() },
            )

            // Dedup local optimistic user bubble against host-authoritative
            // message.added. The local bubble was inserted in
            // sendDraftMessage with id="local-<ts>" for snappy UX; once the
            // host echoes the real msg.added with a real UUID, swap the
            // local id for the real id in place rather than appending —
            // otherwise we render two identical user bubbles per send.
            //
            // Match on text.trim(): the host strips trailing whitespace in
            // some paths. First-match keeps things FIFO so two identical
            // back-to-back sends still pair up correctly.
            //
            // Only applies to User role — assistant ids are authoritative
            // (the optimistic assistant placeholder we insert in
            // sendDraftMessage uses the host's `placeholder_assistant_msg_id`
            // already, so the streaming events match it directly).
            if (baseRole == MessageRole.User && existingChat == null) {
                val twin = messages.firstOrNull {
                    it.id.startsWith("local-") && it.text.trim() == baseText.trim()
                }
                if (twin != null) {
                    val rebound = merged.copy(
                        createdAt = merged.createdAt.ifBlank { twin.createdAt },
                    )
                    return@update copy(
                        messages = messages.map { if (it.id == twin.id) rebound else it },
                        chatStatus = "Message updated",
                    )
                }
            }

            routeMessageInto(this, merged)
        }
    }

    /**
     * Insert / update / move a [MessageUi] across the chat list and the
     * tool-activity list per the [isVisibleInChat] predicate. Pure function
     * over [MainUiState] — easy to reason about state transitions.
     */
    private fun routeMessageInto(state: MainUiState, msg: MessageUi): MainUiState {
        val visibleNow = msg.isVisibleInChat()
        val inChat = state.messages.any { it.id == msg.id }
        val inActivity = state.toolActivity.any { it.id == msg.id }

        return when {
            visibleNow && inChat -> state.copy(
                messages = state.messages.map { if (it.id == msg.id) msg else it },
                toolActivity = state.toolActivity, // unchanged
                chatStatus = "Message updated",
            )
            visibleNow && inActivity -> state.copy(
                // Promote: was hidden, now visible. Rare but possible if the
                // host revises a row's role/finish_reason. Append rather
                // than sort — the host emits events in arrival order, and a
                // streaming seed with createdAt="" would otherwise jump to
                // the top of the list.
                messages = state.messages + msg,
                toolActivity = state.toolActivity.filterNot { it.id == msg.id },
                chatStatus = "Message updated",
            )
            visibleNow /* && !inChat && !inActivity */ -> state.copy(
                messages = state.messages + msg,
                chatStatus = "Message added",
            )
            !visibleNow && inChat -> state.copy(
                // Demote: row was in chat (e.g. our optimistic placeholder),
                // host now reveals it as a tool-call placeholder. Drop from
                // chat list and push to activity.
                messages = state.messages.filterNot { it.id == msg.id },
                toolActivity = upsertActivity(state.toolActivity, msg.toToolActivity()),
                chatStatus = "Tool activity",
            )
            !visibleNow && inActivity -> state.copy(
                toolActivity = upsertActivity(state.toolActivity, msg.toToolActivity()),
            )
            else /* !visibleNow && new */ -> state.copy(
                toolActivity = upsertActivity(state.toolActivity, msg.toToolActivity()),
                chatStatus = "Tool activity",
            )
        }
    }

    private fun upsertActivity(
        list: List<ToolActivityUi>,
        entry: ToolActivityUi,
    ): List<ToolActivityUi> {
        val replaced = list.any { it.id == entry.id }
        val merged = if (replaced) {
            list.map { if (it.id == entry.id) entry else it }
        } else {
            list + entry
        }
        return merged.sortedBy { it.createdAt }
    }

    private fun mergeStreamingEvent(event: RemoteEvent, kind: StreamingKind) {
        val data = event.data as? JsonObject ?: return
        val convId = data.string("conversation_id", "conv_id")
        if (convId != null && convId != uiState.value.selectedConversationId) return

        val messageId = data.string("msg_id", "message_id", "id") ?: return
        // Raw read: a delta of only spaces or newlines is real content
        // (word breaks, paragraph breaks) and must not be dropped.
        val delta = data.rawString("delta", "text", "content").orEmpty()
        val role = data.string("role").toMessageRole().takeIf { it != MessageRole.Unknown }
            ?: MessageRole.Assistant
        val agentId = data.string("agent_id")
        val memberAlias = data.string("member_alias")
        val turnId = data.string("turn_id")

        update {
            val existing = messages.firstOrNull { it.id == messageId }
            when (kind) {
                StreamingKind.Started -> {
                    if (existing != null) return@update copy() // already seeded; idempotent
                    val seed = MessageUi(
                        id = messageId,
                        role = role,
                        text = "",
                        agentId = agentId,
                        memberAlias = memberAlias,
                        turnId = turnId,
                        streaming = true,
                    )
                    copy(messages = messages + seed, chatStatus = "Streaming response", busy = false)
                }
                StreamingKind.Delta -> {
                    if (existing == null) {
                        // streaming.started lost / out of order — defensive seed.
                        val seed = MessageUi(
                            id = messageId,
                            role = role,
                            text = delta,
                            agentId = agentId,
                            memberAlias = memberAlias,
                            turnId = turnId,
                            streaming = true,
                        )
                        return@update copy(
                            messages = messages + seed,
                            chatStatus = "Streaming response",
                            busy = false,
                        )
                    }
                    val updated = existing.copy(text = existing.text + delta, streaming = true)
                    copy(messages = messages.map { if (it.id == messageId) updated else it })
                }
                StreamingKind.Aborted -> {
                    if (existing == null) return@update copy(busy = false)
                    if (existing.text.isBlank()) {
                        return@update copy(
                            messages = messages.filterNot { it.id == messageId },
                            chatStatus = "Streaming aborted (no content)",
                            busy = false,
                        )
                    }
                    val updated = existing.copy(streaming = false)
                    copy(
                        messages = messages.map { if (it.id == messageId) updated else it },
                        chatStatus = "Streaming aborted",
                        busy = false,
                    )
                }
            }
        }
    }

    private enum class StreamingKind { Started, Delta, Aborted }

    private fun setHostLatency(hostId: String, latencyMs: Long) {
        update {
            copy(
                hosts = hosts.map {
                    if (it.config.id == hostId) it.copy(latencyMs = latencyMs) else it
                },
            )
        }
    }

    private fun setHostStatus(hostId: String, status: HostStatus, detail: String) {
        update {
            copy(
                hosts = hosts.map {
                    if (it.config.id == hostId) it.copy(status = status, detail = detail) else it
                },
            )
        }
    }

    /**
     * The remote server answered, but Verzeta Studio behind it is not
     * running. The socket stays connected; the host detail and status say
     * why every action will fail until the desktop app is started.
     */
    private fun reportDesktopNotRunning(hostId: String?) {
        if (hostId != null) {
            setHostStatus(hostId, HostStatus.Connected, "Verzeta Studio is not running on this computer")
        }
        fail("Connected, but Verzeta Studio is not running on the host. Start it on the desktop.")
    }

    private fun setBusy(status: String) = update { copy(status = status, busy = true) }
    /**
     * Record a failed action: updates [MainUiState.status] and, unless
     * [notify] is false, raises the app-wide snackbar via
     * [MainUiState.errorMessage].
     */
    private fun fail(message: String, notify: Boolean = true) = update {
        if (notify) {
            copy(status = message, busy = false, errorMessage = message, errorId = errorId + 1)
        } else {
            copy(status = message, busy = false)
        }
    }

    private fun update(block: MainUiState.() -> MainUiState) {
        _uiState.value = _uiState.value.block()
    }

    private fun Throwable.safeMessage(): String = message ?: this::class.java.simpleName

    private fun ConversationUi.toRecent(hostId: String): RecentConversationUi =
        RecentConversationUi(
            id = id,
            hostId = hostId,
            title = title,
            updatedLabel = updatedAt.ifBlank { "recent" },
            badges = buildList {
                if (folderId != null) add(RecentBadge.Plan)
                if (!primaryAgentId.isNullOrBlank()) add(RecentBadge.Agent)
                if (isGroup) add(RecentBadge.Group)
            },
            pinned = isPinned,
            streaming = streaming,
        )

    private fun JsonObject.string(vararg keys: String): String? =
        keys.firstNotNullOfOrNull { key ->
            this[key]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank)
        }

    /** Like [string] but keeps whitespace-only values. */
    private fun JsonObject.rawString(vararg keys: String): String? =
        keys.firstNotNullOfOrNull { key ->
            (this[key] as? JsonPrimitive)?.contentOrNull
        }

    /**
     * File name of a generated image or audio file: `file_name` when
     * present, otherwise the basename of the host's absolute `path`.
     */
    private fun generatedFileName(data: JsonObject): String? =
        data.string("file_name")
            ?: data.string("path")?.substringAfterLast('/')?.substringAfterLast('\\')
                ?.takeIf(String::isNotBlank)

    /** Apply a canvas run-state value; opens the console when a run starts. */
    private fun applyCanvasRunning(running: Boolean) {
        update {
            copy(
                canvasRunState = CanvasRunStateUi(running = running),
                // Open the console when a run starts, leave the user's
                // state alone when it stops (they may want to inspect
                // output before dismissing).
                consoleVisible = consoleVisible || running,
            )
        }
    }

    private fun String?.toMessageRole(): MessageRole =
        when (this?.lowercase()) {
            "user" -> MessageRole.User
            "assistant" -> MessageRole.Assistant
            "system" -> MessageRole.System
            "tool" -> MessageRole.Tool
            else -> MessageRole.Unknown
        }

    private companion object {
        /** Most agent steps kept for the progress banner. */
        const val MAX_AGENT_STEPS = 50

        /** Longest text the host accepts in `msg.send` (UTF-16 units). */
        const val MAX_MESSAGE_CHARS = 4096
    }
}
