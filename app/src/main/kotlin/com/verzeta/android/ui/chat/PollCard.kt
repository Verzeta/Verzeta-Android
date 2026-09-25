// SPDX-FileCopyrightText: 2026 Aditya Mehra <aix.m@outlook.com>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

/**
 * @file PollCard.kt
 * @brief Inline poll card rendered inside an assistant message bubble when
 *        the message carries a `poll_id` in its metadata, displaying vote
 *        options with animated progress bars, a live countdown, and a close
 *        action for open polls.
 * @layer UI
 * @dependencies Jetpack Compose, PollUi, androidx.compose.animation
 */

package com.verzeta.android.ui.chat

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HowToVote
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verzeta.android.data.poll.PollUi
import kotlinx.coroutines.delay

/**
 * Inline poll card rendered inside the assistant message bubble that owns
 * `metadata.poll_id` (see AssistantBubble in ChatScreen.kt). Mirrors the
 * desktop QML PollCard:
 *
 *   • Vote buttons with per-option progress bars showing the share
 *     of total votes.
 *   • Live `closes_at` countdown for open polls — re-derived every
 *     second from the system clock.
 *   • Status pill (OPEN / CLOSED).
 *   • Closed polls highlight the winning option in primary color
 *     and disable the vote buttons.
 *   • Responsive: fills the bubble width; no fixed sizes.
 */
@Composable
fun InlinePollCard(
    poll: PollUi?,
    onVote: (optionId: String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (poll == null) return
    val colors = MaterialTheme.colorScheme
    val open = poll.isOpen
    val borderColor = if (open) {
        colors.primary.copy(alpha = 0.4f)
    } else {
        colors.outline
    }
    val bg = if (open) {
        colors.primary.copy(alpha = 0.05f)
    } else {
        colors.surfaceVariant
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Header: icon + question + status pill
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = if (open) Icons.Filled.HowToVote
                              else Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = if (open) colors.primary else colors.tertiary,
                modifier = Modifier.size(20.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = poll.question,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold),
                    maxLines = 3,
                    color = colors.onSurface,
                )
                Spacer(Modifier.size(2.dp))
                Text(
                    text = buildString {
                        if (poll.creatorAlias.isNotEmpty()) {
                            append("by ${poll.creatorAlias}")
                            append("  •  ")
                        }
                        append("${poll.mode} mode")
                        append("  •  ")
                        append("${poll.totalVotes} vote${if (poll.totalVotes == 1) "" else "s"}")
                        append(" / ")
                        append("${poll.voterCount} voter${if (poll.voterCount == 1) "" else "s"}")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
            StatusPill(open = open)
        }

        // Live countdown for polls with closes_at.
        if (open && !poll.closesAt.isNullOrBlank()) {
            CountdownRow(closesAtIso = poll.closesAt!!)
        }

        // Per-option vote buttons + progress bars.
        val total = poll.totalVotes.coerceAtLeast(0)
        poll.options.forEach { opt ->
            val isWinner = !open &&
                poll.winningOptionIds.contains(opt.id) &&
                opt.votes > 0
            val share = if (total > 0 && opt.votes > 0) {
                opt.votes.toFloat() / total.toFloat()
            } else {
                0f
            }
            val animatedShare by animateFloatAsState(
                targetValue = share,
                label = "voteShare-${opt.id}",
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedButton(
                    onClick = { if (open) onVote(opt.id) },
                    enabled = open,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = opt.text,
                            modifier = Modifier.weight(1f),
                            maxLines = 2,
                            color = colors.onSurface,
                        )
                        VoteCountBadge(
                            votes = opt.votes,
                            highlighted = isWinner,
                        )
                    }
                }
                // Progress bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(colors.outline.copy(alpha = 0.10f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(animatedShare)
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                if (isWinner) colors.tertiary
                                else colors.primary
                            ),
                    )
                }
            }
        }

        // Footer: close button (open) or winner caption (closed).
        if (open) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onClose) { Text("Close poll") }
            }
        } else {
            val winners = poll.winningOptionIds
            val winnerText = when {
                winners.isEmpty() -> "Closed with no votes."
                winners.size > 1 -> "Closed with a tie."
                else -> {
                    val w = poll.options.firstOrNull { it.id == winners.first() }
                    "Winner: ${w?.text ?: "—"}"
                }
            }
            Text(
                text = winnerText,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.Medium),
                color = colors.tertiary,
            )
        }
    }
}

@Composable
private fun StatusPill(open: Boolean) {
    val colors = MaterialTheme.colorScheme
    val bg = if (open) colors.primary.copy(alpha = 0.20f)
              else colors.tertiary.copy(alpha = 0.20f)
    val fg = if (open) colors.primary else colors.tertiary
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = if (open) "OPEN" else "CLOSED",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = fg,
            fontSize = 10.sp,
        )
    }
}

@Composable
private fun VoteCountBadge(votes: Int, highlighted: Boolean) {
    val colors = MaterialTheme.colorScheme
    val bg = if (highlighted) {
        colors.tertiary.copy(alpha = 0.30f)
    } else {
        colors.outline.copy(alpha = 0.15f)
    }
    val fg = if (highlighted) colors.tertiary else colors.onSurface
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(bg)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = votes.toString(),
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold),
            color = fg,
        )
    }
}

/**
 * Live countdown showing "closes in Xm Ys". Re-derives every second
 * from the system clock; relies on Compose's recomposition to redraw.
 */
@Composable
private fun CountdownRow(closesAtIso: String) {
    val colors = MaterialTheme.colorScheme
    val closesMs = remember(closesAtIso) {
        runCatching { java.time.Instant.parse(closesAtIso).toEpochMilli() }
            .getOrNull() ?: 0L
    }
    if (closesMs <= 0L) return

    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(closesAtIso) {
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(1000L)
            if (System.currentTimeMillis() >= closesMs) break
        }
    }

    val remainMs = (closesMs - nowMs).coerceAtLeast(0L)
    val label = formatRemaining(remainMs)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Schedule,
            contentDescription = null,
            tint = colors.onSurfaceVariant,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
        )
    }
}

private fun formatRemaining(ms: Long): String {
    if (ms <= 0L) return "closing…"
    val totalSec = (ms / 1000L).toInt()
    if (totalSec < 60) return "closes in ${totalSec}s"
    val minutes = totalSec / 60
    val seconds = totalSec % 60
    if (minutes < 60) return "closes in ${minutes}m ${seconds}s"
    val hours = minutes / 60
    val mm = minutes % 60
    return "closes in ${hours}h ${mm}m"
}
