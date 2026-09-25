// SPDX-FileCopyrightText: 2026 Aditya Mehra <aix.m@outlook.com>
// SPDX-License-Identifier: LGPL-3.0-or-later

package com.verzeta.android.remote

import com.verzeta.android.data.folder.FolderMemberUi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the reply shapes the host actually sends for id-like and boolean
 * results, the member-row fields, and the member and canvas-action
 * request payloads.
 *
 * The host's `asyncInvoke(..., "bool")` answers `ok: true` with a bare
 * boolean as data, and `asyncInvoke(..., "QString")` with a bare string.
 * `asyncInvokeWrapId` wraps a string as `{<key>: "..."}`, with `id` as the
 * default key. The parsers accept both the bare and the wrapped form.
 */
class WireReplyParsingTest {

    private val repo = RemoteRepository(RemoteSession())

    private fun json(text: String): JsonElement = Json.parseToJsonElement(text)

    @Test
    fun `id reply accepts a bare string`() {
        assertEquals("run-1", repo.parseIdOrNull(json("\"run-1\""), "run_id"))
    }

    @Test
    fun `id reply accepts the wrapped key`() {
        // folder.member.chat.open and folder.kickoff.group wrap as {id}.
        assertEquals("conv-1", repo.parseIdOrNull(json("""{"id":"conv-1"}"""), "id", "conv_id"))
        // poll.start wraps as {poll_id}.
        assertEquals("poll-1", repo.parseIdOrNull(json("""{"poll_id":"poll-1"}"""), "poll_id", "id"))
    }

    @Test
    fun `empty id reply is treated as missing`() {
        // heartbeat.run_now answers "" when heartbeats are paused.
        assertNull(repo.parseIdOrNull(json("\"\""), "run_id"))
        assertNull(repo.parseIdOrNull(json("""{"file_name":""}"""), "file_name"))
        assertNull(repo.parseIdOrNull(json("{}"), "id"))
    }

    @Test
    fun `bool reply accepts a bare boolean and the wrapped key`() {
        assertTrue(repo.parseBoolReply(json("true"), "running"))
        assertFalse(repo.parseBoolReply(json("false"), "running"))
        assertTrue(repo.parseBoolReply(json("""{"running":true}"""), "running"))
        assertFalse(repo.parseBoolReply(json("{}"), "running"))
    }

    @Test
    fun `only a bare false is a refused write`() {
        assertTrue(repo.isRefusal(json("false")))
        assertFalse(repo.isRefusal(json("true")))
        assertFalse(repo.isRefusal(json("""{"queued":true}""")))
        assertFalse(repo.isRefusal(json("\"false\"")))
        assertFalse(repo.isRefusal(null))
    }

    @Test
    fun `member rows keep the per-member overrides`() {
        val rows = json(
            """
            [{"containerId":"f1","agentId":"a1","alias":"Rob","isCoordinator":true,
              "modelProvider":"ollama","modelName":"qwen3","allowedTools":["read_file","web_search"]},
             {"agentId":"a2","alias":"Ann","isCoordinator":false}]
            """.trimIndent(),
        ) as JsonArray
        val members = repo.parseMembersFromArray(rows)
        assertEquals(2, members.size)
        assertEquals("ollama", members[0].modelProvider)
        assertEquals("qwen3", members[0].modelName)
        assertEquals(listOf("read_file", "web_search"), members[0].allowedTools)
        assertTrue(members[0].isCoordinator)
        assertEquals("", members[1].modelProvider)
        assertTrue(members[1].allowedTools.isEmpty())
    }

    @Test
    fun `member rows keep who added the member`() {
        val rows = json(
            """
            [{"agentId":"a1","alias":"Rob","isCoordinator":false,
              "addedByKind":"agent","addedByAgentId":"a9"},
             {"agentId":"a2","alias":"Ann","isCoordinator":false}]
            """.trimIndent(),
        ) as JsonArray
        val members = repo.parseMembersFromArray(rows)
        assertEquals("agent", members[0].addedByKind)
        assertEquals("a9", members[0].addedByAgentId)
        assertEquals("", members[1].addedByKind)
        assertEquals("", members[1].addedByAgentId)
    }

    @Test
    fun `members set sends overrides and provenance back`() {
        val rows = json(
            """
            [{"agentId":"a1","alias":"Rob","isCoordinator":true,
              "modelProvider":"ollama","modelName":"qwen3","allowedTools":["read_file"],
              "addedByKind":"agent","addedByAgentId":"a9"}]
            """.trimIndent(),
        ) as JsonArray
        val sent = repo.folderMembersToJsonArray(repo.parseMembersFromArray(rows))
        val m = sent[0] as JsonObject
        assertEquals("a1", m.str("agent_id"))
        assertEquals("Rob", m.str("alias"))
        assertEquals("true", m.str("is_coordinator"))
        assertEquals("ollama", m.str("model_provider"))
        assertEquals("qwen3", m.str("model_name"))
        assertEquals(listOf("read_file"), (m["allowed_tools"] as JsonArray).map { (it as JsonPrimitive).content })
        assertEquals("agent", m.str("added_by_kind"))
        assertEquals("a9", m.str("added_by_agent_id"))
    }

    @Test
    fun `members set leaves out absent overrides and invalid provenance`() {
        val sent = repo.folderMembersToJsonArray(
            listOf(
                FolderMemberUi(agentId = "a1", alias = "Rob", isCoordinator = false),
                FolderMemberUi(
                    agentId = "a2", alias = "Ann", isCoordinator = false,
                    allowedTools = listOf("", " "), addedByKind = "system",
                ),
            ),
        )
        val expectedKeys = setOf("agent_id", "alias", "is_coordinator")
        assertEquals(expectedKeys, (sent[0] as JsonObject).keys)
        // The host rejects the whole call for an unknown kind or a blank tool name.
        assertEquals(expectedKeys, (sent[1] as JsonObject).keys)
    }

    @Test
    fun `canvas ai action trigger carries the open conversation`() {
        val params = repo.canvasAiActionTriggerParams("c1", "explain", "")
        assertEquals("c1", params.str("conv_id"))
        assertEquals("explain", params.str("action_id"))
        assertFalse(params.containsKey("submenu_choice"))
        val withChoice = repo.canvasAiActionTriggerParams("c1", "translate", "Rust")
        assertEquals("Rust", withChoice.str("submenu_choice"))
        assertFalse(repo.canvasAiActionTriggerParams("", "explain", "").containsKey("conv_id"))
    }

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.content

    @Test
    fun `tool call timestamps in epoch milliseconds become ISO`() {
        val call = repo.parseToolCallObject(
            json(
                """{"id":"t1","tool_name":"read_file","status":"success",
                    "started_at":1710000000000,"completed_at":0}""",
            ) as JsonObject,
        )!!
        assertEquals("2024-03-09T16:00:00.000Z", call.startedAt)
        assertEquals("", call.completedAt)
    }
}
