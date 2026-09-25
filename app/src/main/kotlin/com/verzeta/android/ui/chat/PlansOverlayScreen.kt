// SPDX-FileCopyrightText: 2026 Aditya Mehra <aix.m@outlook.com>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

/**
 * @file PlansOverlayScreen.kt
 * @brief Per-conversation task and plan viewer that shows all agent-driven
 *        plans with their step breakdown, progress, meta counters, and
 *        optional human-in-the-loop intervention controls (Retry, Mark Done,
 *        Skip), plus an inline composer to start new tasks.
 * @layer UI
 * @dependencies Jetpack Compose, MainViewModel, MainUiState, PlanUi,
 *               PlanStepUi, PlanStatus, StepStatus
 */

package com.verzeta.android.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.verzeta.android.MainUiState
import com.verzeta.android.MainViewModel
import com.verzeta.android.data.host.HostStatus
import com.verzeta.android.data.plan.PlanStatus
import com.verzeta.android.data.plan.PlanStepUi
import com.verzeta.android.data.plan.PlanUi
import com.verzeta.android.data.plan.StepStatus
import com.verzeta.android.ui.tools.TopBar
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Plans / tasks overlay — Android port of
 * `frontend/components/PlansOverlay.qml`. Bound to
 * `state.convPlans[selectedConversationId]`; updates flow from
 * `plan.created` / `plan.updated` / `plan.deleted` events filtered by
 * conversationId, plus `step.updated` triggering a `plan.get` re-fetch
 * for the parent plan.
 *
 * Card layout matches the QML 1:1: status icon (coloured) + goal (bold,
 * 2 lines) + status/steps subtitle + Stop button (when status is not
 * Completed/Failed). Meta row underneath: started/updated timestamps +
 * calls/budgetCallsMax + heartbeats/budgetHeartbeatMax. Progress bar
 * clamped to `max(1, totalSteps)` to avoid divide-by-zero.
 *
 * Per-step rows show icon + title (strike-through when done) + @owner
 * alias, an optional stats row (rework/text-retry/turns counters when
 * non-zero), an optional rejection-reason note (italic, 2 lines), and
 * an action row with Retry / Mark Done / Skip ONLY when
 * `canIntervene === true` (the host computes this — never override
 * client-side).
 */
@Composable
fun PlansOverlayScreen(state: MainUiState, actions: MainViewModel) {
    val convId = state.selectedConversationId ?: return
    val connected = state.activeHost?.status == HostStatus.Connected
    val plans = state.convPlans[convId].orEmpty()

    LaunchedEffect(convId, connected) {
        if (connected) actions.loadPlansForConversation(convId)
    }

    var newGoal by remember { mutableStateOf("") }
    var retryStep by remember { mutableStateOf<PlanStepUi?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        TopBar(
            title = "Plans (${plans.size})",
            subtitle = state.selectedConversation?.title?.takeIf { it.isNotBlank() }
                ?.let { "in $it" } ?: "Per-conversation tasks",
            onBack = actions::closePlansOverlay,
            onRefresh = if (connected) ({ actions.loadPlansForConversation(convId) }) else null,
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // Always-visible "Start a Task" composer so the user can launch
        // tasks without leaving the overlay (the desktop has this in the
        // chat input bar; on Android it makes more sense inline).
        StartTaskComposer(
            goal = newGoal,
            onGoalChange = { newGoal = it },
            onStart = {
                if (newGoal.isNotBlank()) {
                    // Clear the field only once the host accepted the task,
                    // so a failed start keeps what was typed.
                    val started = newGoal
                    actions.startTask(convId, started.trim()) {
                        if (newGoal == started) newGoal = ""
                    }
                }
            },
            taskStarting = state.taskStartingConvId == convId,
            enabled = connected && state.taskStartingConvId != convId,
        )

        if (plans.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "No plans in this conversation. Use the composer above to start one.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Column
        }

        LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            items(plans, key = { it.id }) { plan ->
                Spacer(Modifier.height(12.dp))
                PlanCard(
                    plan = plan,
                    onStop = { actions.stopPlan(plan.id) },
                    onRetryStep = { step -> retryStep = step },
                    onMarkDone = { stepId -> actions.overrideStepDone(stepId) },
                    onSkip = { stepId -> actions.skipStep(stepId) },
                )
            }
            item { Spacer(Modifier.height(40.dp)) }
        }
    }

    retryStep?.let { step ->
        RetryNotesDialog(
            step = step,
            onConfirm = { notes ->
                actions.retryStep(step.id, notes)
                retryStep = null
            },
            onDismiss = { retryStep = null },
        )
    }
}

@Composable
private fun StartTaskComposer(
    goal: String,
    onGoalChange: (String) -> Unit,
    onStart: () -> Unit,
    taskStarting: Boolean,
    enabled: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        OutlinedTextField(
            value = goal,
            onValueChange = onGoalChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = false,
            minLines = 1,
            maxLines = 3,
            label = { Text("Start a task") },
            placeholder = { Text("Describe the goal. The agent breaks it into steps.") },
            keyboardOptions = KeyboardOptions.Default,
            enabled = enabled,
        )
        Spacer(Modifier.size(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (taskStarting) {
                Icon(
                    Icons.Filled.HourglassTop,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.size(6.dp))
                Text(
                    "Starting…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.weight(1f))
            Button(onClick = onStart, enabled = enabled && goal.isNotBlank()) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null)
                Spacer(Modifier.size(6.dp))
                Text("Start task")
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun PlanCard(
    plan: PlanUi,
    onStop: () -> Unit,
    onRetryStep: (PlanStepUi) -> Unit,
    onMarkDone: (stepId: String) -> Unit,
    onSkip: (stepId: String) -> Unit,
) {
    val borderColor = planStatusColor(plan.status)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(1.dp, borderColor.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        // Header: icon + goal + Stop button
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                planStatusIcon(plan.status),
                contentDescription = null,
                tint = borderColor,
                modifier = Modifier.size(22.dp).padding(top = 2.dp),
            )
            Spacer(Modifier.size(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    plan.goal,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "Status: ${plan.status.displayName}  •  Steps ${plan.doneSteps}/${plan.totalSteps}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // Stop visible only while the plan is not in a terminal state —
            // matches QML's `status !== completed && !== failed` condition.
            if (!plan.status.isTerminal) {
                TextButton(onClick = onStop) {
                    Icon(Icons.Filled.Stop, contentDescription = null)
                    Spacer(Modifier.size(4.dp))
                    Text("Stop")
                }
            }
        }

        Spacer(Modifier.size(8.dp))

        // Meta row — started / updated. The "calls" and "nudges" pills are gone:
        // they read counters nothing increments any more, so they always showed
        // zero. The desktop overlay dropped them for the same reason.
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MetaPill("▶ started ${formatHms(plan.createdAtMs)}")
            MetaPill("⟳ updated ${formatHms(plan.updatedAtMs)}")
        }

        Spacer(Modifier.size(10.dp))
        LinearProgressIndicator(
            progress = {
                val total = if (plan.totalSteps <= 0) 1f else plan.totalSteps.toFloat()
                (plan.doneSteps.toFloat() / total).coerceIn(0f, 1f)
            },
            modifier = Modifier.fillMaxWidth().height(6.dp),
        )

        Spacer(Modifier.size(8.dp))
        plan.steps.forEach { step ->
            StepRow(
                step = step,
                onRetry = { onRetryStep(step) },
                onMarkDone = { onMarkDone(step.id) },
                onSkip = { onSkip(step.id) },
            )
            Spacer(Modifier.size(4.dp))
        }
    }
}

@Composable
private fun MetaPill(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun StepRow(
    step: PlanStepUi,
    onRetry: () -> Unit,
    onMarkDone: () -> Unit,
    onSkip: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 4.dp, top = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                stepStatusIcon(step.status),
                contentDescription = null,
                tint = stepStatusColor(step.status),
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.size(8.dp))
            Text(
                step.title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    textDecoration = if (step.status == StepStatus.Done) TextDecoration.LineThrough
                    else TextDecoration.None,
                ),
                color = if (step.status == StepStatus.Done) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (step.ownerAlias.isNotBlank()) {
                Spacer(Modifier.size(8.dp))
                Text(
                    "@${step.ownerAlias}",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Stats row — only when at least one counter is non-zero (matches QML).
        if (step.rejectionCount > 0 || step.toolRetryCount > 0 || step.executorTurnsUsed > 0) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(start = 22.dp, top = 2.dp),
            ) {
                if (step.rejectionCount > 0) {
                    Text(
                        "rework ${step.rejectionCount}/3",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (step.toolRetryCount > 0) {
                    Text(
                        "text retry ${step.toolRetryCount}/3",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (step.executorTurnsUsed > 0) {
                    Text(
                        "${step.executorTurnsUsed}/20 turns",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Last rejection reason — italic, 2 lines, ellipsis.
        if (step.lastRejectionReason.isNotBlank()) {
            Text(
                "note: ${step.lastRejectionReason}",
                style = MaterialTheme.typography.bodySmall.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 22.dp, top = 2.dp),
            )
        }

        // Intervention buttons — gated entirely by canIntervene.
        if (step.canIntervene) {
            Row(
                modifier = Modifier.padding(start = 18.dp, top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                TextButton(onClick = onRetry) {
                    Icon(Icons.Filled.Refresh, contentDescription = null)
                    Spacer(Modifier.size(4.dp))
                    Text("Retry")
                }
                TextButton(onClick = onMarkDone) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null)
                    Spacer(Modifier.size(4.dp))
                    Text("Mark done")
                }
                TextButton(onClick = onSkip) {
                    Icon(Icons.Filled.SkipNext, contentDescription = null)
                    Spacer(Modifier.size(4.dp))
                    Text("Skip")
                }
            }
        }
    }
}

@Composable
private fun RetryNotesDialog(
    step: PlanStepUi,
    onConfirm: (notes: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var notes by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Retry step") },
        text = {
            Column {
                Text(
                    "Give the agent specific guidance on what to change. This note will be " +
                        "injected into the next executor turn as corrective context.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    "Step: ${step.title}",
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.size(8.dp))
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 6,
                    placeholder = {
                        Text("e.g. Focus on a playful tone, include a clear CTA, keep it under 150 words")
                    },
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(notes.trim()) }) { Text("Retry") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun planStatusIcon(status: PlanStatus): ImageVector = when (status) {
    PlanStatus.Completed -> Icons.Filled.CheckCircle
    PlanStatus.Failed -> Icons.Filled.Error
    PlanStatus.Stopped -> Icons.Filled.Cancel
    PlanStatus.Blocked -> Icons.Filled.Warning
    PlanStatus.Critiquing -> Icons.Filled.Refresh
    else -> Icons.Filled.TaskAlt
}

@Composable
private fun planStatusColor(status: PlanStatus): Color = when (status) {
    PlanStatus.Completed -> MaterialTheme.colorScheme.primary
    PlanStatus.Failed -> MaterialTheme.colorScheme.error
    PlanStatus.Blocked -> MaterialTheme.colorScheme.tertiary
    PlanStatus.Stopped -> MaterialTheme.colorScheme.onSurfaceVariant
    else -> MaterialTheme.colorScheme.primary
}

@Composable
private fun stepStatusIcon(status: StepStatus): ImageVector = when (status) {
    StepStatus.Done -> Icons.Filled.CheckCircle
    StepStatus.Blocked -> Icons.Filled.Warning
    StepStatus.NeedsRework -> Icons.Filled.Refresh
    StepStatus.InProgress -> Icons.Filled.PlayArrow
    StepStatus.Submitted -> Icons.Filled.Send
    else -> Icons.Filled.Check
}

@Composable
private fun stepStatusColor(status: StepStatus): Color = when (status) {
    StepStatus.Done -> MaterialTheme.colorScheme.primary
    StepStatus.Blocked -> MaterialTheme.colorScheme.error
    StepStatus.NeedsRework -> MaterialTheme.colorScheme.tertiary
    StepStatus.InProgress -> MaterialTheme.colorScheme.primary
    else -> MaterialTheme.colorScheme.onSurface
}

private fun formatHms(ms: Long): String {
    if (ms <= 0L) return "—"
    return runCatching {
        Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("HH:mm:ss"))
    }.getOrDefault("—")
}
