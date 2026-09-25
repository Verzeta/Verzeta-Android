// SPDX-FileCopyrightText: 2026 Aditya Mehra <aix.m@outlook.com>
// SPDX-License-Identifier: LGPL-3.0-or-later

package com.verzeta.android.remote

import com.verzeta.android.data.plan.PlanStatus
import com.verzeta.android.data.plan.StepStatus
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the plan payload the host actually sends.
 *
 * `WireDbReader::planById` projects the `agent_plans` row verbatim in
 * snake_case and appends a `steps` array. The parser used to read the camelCase
 * key names of `PlansModel`, the desktop's QML-facing model, which no client
 * ever receives — so `conversationId`, `startedBy`, the timestamps and the
 * progress counters all silently defaulted, and the Plans overlay showed
 * "Steps 0/0" with an empty progress bar on every plan.
 *
 * Two fields the wire cannot carry are derived here instead: progress (the host
 * counts step statuses in its own overlay) and `canIntervene` (there is no such
 * column, so the per-step buttons were permanently hidden).
 */
class PlanParsingTest {

    private val repo = RemoteRepository(RemoteSession())

    private fun obj(json: String): JsonObject = Json.parseToJsonElement(json) as JsonObject

    /** The exact shape WireDbReader emits: snake_case row plus `steps`. */
    private fun wirePlan(
        planStatus: String = "executing",
        stepStatuses: List<String> = listOf("done", "done", "pending"),
    ): JsonObject = obj(
        """
        {
          "id": "plan-1",
          "conversation_id": "conv-9",
          "goal": "Ship it",
          "status": "$planStatus",
          "started_by": "Mark",
          "created_at": 1710000000000,
          "updated_at": 1710000009999,
          "turns_used": 0,
          "heartbeats_used": 0,
          "steps": [
            ${stepStatuses.mapIndexed { i, s ->
            """{"id":"s$i","title":"Step $i","owner_alias":"Rob",
                    "status":"$s","acceptance_criteria":"crit",
                    "rejection_count":2,"tool_retry_count":3,
                    "executor_turns_used":4,"last_rejection_reason":"nope"}"""
        }.joinToString(",")}
          ]
        }
        """.trimIndent(),
    )

    @Test
    fun `snake_case plan fields are read, not silently defaulted`() {
        val plan = repo.parsePlanObject(wirePlan())!!

        assertEquals("plan-1", plan.id)
        assertEquals("conv-9", plan.conversationId)
        assertEquals("Ship it", plan.goal)
        assertEquals(PlanStatus.Executing, plan.status)
        assertEquals("Mark", plan.startedBy)
        assertEquals(1710000000000L, plan.createdAtMs)
        assertEquals(1710000009999L, plan.updatedAtMs)
    }

    @Test
    fun `progress is derived from step statuses, which the host never sends`() {
        val plan = repo.parsePlanObject(wirePlan())!!

        assertEquals(3, plan.totalSteps)
        assertEquals(2, plan.doneSteps)
    }

    @Test
    fun `a completed task renders a full bar because every step is Done`() {
        // complete_task marks non-terminal steps Done in the database, so the
        // derived counters reach parity and the bar fills.
        val plan = repo.parsePlanObject(
            wirePlan(planStatus = "completed", stepStatuses = listOf("done", "done")),
        )!!

        assertEquals(plan.totalSteps, plan.doneSteps)
        assertEquals(PlanStatus.Completed, plan.status)
    }

    @Test
    fun `snake_case step fields are read`() {
        val step = repo.parsePlanObject(wirePlan())!!.steps.first()

        assertEquals("Rob", step.ownerAlias)
        assertEquals("crit", step.acceptance)
        assertEquals(2, step.rejectionCount)
        assertEquals(3, step.toolRetryCount)
        assertEquals(4, step.executorTurnsUsed)
        assertEquals("nope", step.lastRejectionReason)
    }

    @Test
    fun `canIntervene is derived, matching the host rule`() {
        // Plan executing, step not Done -> intervene.
        val executing = repo.parsePlanObject(
            wirePlan(planStatus = "executing", stepStatuses = listOf("pending")),
        )!!
        assertTrue(executing.steps.first().canIntervene)

        // Plan executing, step Done -> no.
        val doneStep = repo.parsePlanObject(
            wirePlan(planStatus = "executing", stepStatuses = listOf("done")),
        )!!
        assertFalse(doneStep.steps.first().canIntervene)

        // Plan terminal -> no, whatever the step says.
        val completed = repo.parsePlanObject(
            wirePlan(planStatus = "completed", stepStatuses = listOf("pending")),
        )!!
        assertFalse(completed.steps.first().canIntervene)
    }

    @Test
    fun `critiquing and blocked plans also allow intervention`() {
        for (s in listOf("critiquing", "blocked")) {
            val plan = repo.parsePlanObject(
                wirePlan(planStatus = s, stepStatuses = listOf("in_progress")),
            )!!
            assertTrue("plan status $s should allow intervention", plan.steps.first().canIntervene)
        }
    }

    @Test
    fun `camelCase payloads still parse, so either source works`() {
        val plan = repo.parsePlanObject(
            obj(
                """
                {
                  "id": "plan-2",
                  "conversationId": "conv-camel",
                  "goal": "Legacy",
                  "status": "executing",
                  "startedBy": "Alice",
                  "createdAtMs": 5,
                  "updatedAtMs": 6,
                  "steps": [
                    {"id":"s0","title":"t","ownerAlias":"Bob","status":"pending",
                     "acceptance":"a","rejectionCount":1,"toolRetryCount":1,
                     "executorTurnsUsed":1,"lastRejectionReason":"r"}
                  ]
                }
                """.trimIndent(),
            ),
        )!!

        assertEquals("conv-camel", plan.conversationId)
        assertEquals("Alice", plan.startedBy)
        assertEquals(5L, plan.createdAtMs)
        assertEquals("Bob", plan.steps.first().ownerAlias)
        assertEquals(StepStatus.Pending, plan.steps.first().status)
    }

    @Test
    fun `a plan without an id is rejected`() {
        assertEquals(null, repo.parsePlanObject(obj("""{"goal":"no id"}""")))
    }
}
