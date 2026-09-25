// SPDX-FileCopyrightText: 2026 Aditya Mehra <aix.m@outlook.com>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

/**
 * @file AgentProgressBanner.kt
 * @brief Composable banner displayed above the chat composer while an agent
 *        run is in progress. Shows the current iteration count and a scrollable
 *        list of recent step rows with animated status icons.
 * @layer UI
 * @dependencies Jetpack Compose, AgentRunStateUi, AgentStepUi, AgentStepStatus
 */

package com.verzeta.android.ui.chat

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.verzeta.android.data.agent.AgentRunStateUi
import com.verzeta.android.data.agent.AgentStepStatus
import com.verzeta.android.data.agent.AgentStepUi

/**
 * Agent Progress banner — Android port of `AgentProgressPanel.qml`.
 * Embedded above the chat composer; visible iff
 * [state.agentRunState.isRunning]. Matches the QML's behaviour:
 *   - Header: "Agent Progress" + right-aligned "Step <currentIteration>".
 *   - Step rows: status icon (running spins) + description (red on
 *     error, dimmed when pending).
 *
 * The running flag and iteration come from `agent.run.state` events that
 * carry `is_running` / `current_iteration`. Hosts whose event payload is
 * empty never set the flag, so the banner stays hidden rather than guessing
 * from step events.
 */
@Composable
fun AgentProgressBanner(
    runState: AgentRunStateUi,
    steps: List<AgentStepUi>,
) {
    if (!runState.isRunning) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Bolt,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.size(6.dp))
            Text(
                "Agent Progress",
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.weight(1f))
            Text(
                "Step ${runState.currentIteration}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (steps.isNotEmpty()) {
            Spacer(Modifier.size(6.dp))
            Column(
                modifier = Modifier.heightIn(max = 200.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // Render most-recent first so the in-flight step is at the top.
                steps.takeLast(8).asReversed().forEach { step -> StepRow(step) }
            }
        }
    }
}

@Composable
private fun StepRow(step: AgentStepUi) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        StatusIcon(step.status)
        Spacer(Modifier.size(8.dp))
        Text(
            step.description,
            style = MaterialTheme.typography.bodySmall,
            color = when (step.status) {
                AgentStepStatus.Error -> MaterialTheme.colorScheme.error
                AgentStepStatus.Pending -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                else -> MaterialTheme.colorScheme.onSurface
            },
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StatusIcon(status: AgentStepStatus) {
    val tint: Color = when (status) {
        AgentStepStatus.Running -> MaterialTheme.colorScheme.tertiary
        AgentStepStatus.Success -> MaterialTheme.colorScheme.primary
        AgentStepStatus.Error -> MaterialTheme.colorScheme.error
        AgentStepStatus.Pending -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    when (status) {
        AgentStepStatus.Running -> {
            // Spin the refresh icon — matches the QML's RotationAnimator.
            val transition = rememberInfiniteTransition(label = "agent-step-spin")
            val angle by transition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 1200, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
                label = "agent-step-angle",
            )
            Icon(
                Icons.Filled.Refresh,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(14.dp).rotate(angle),
            )
        }
        AgentStepStatus.Success -> Icon(
            Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(14.dp),
        )
        AgentStepStatus.Error -> Icon(
            Icons.Filled.Error,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(14.dp),
        )
        AgentStepStatus.Pending -> Icon(
            Icons.Filled.HourglassTop,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(14.dp),
        )
    }
}
