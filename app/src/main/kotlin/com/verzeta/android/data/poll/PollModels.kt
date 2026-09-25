// SPDX-FileCopyrightText: 2026 Aditya Mehra <aix.m@outlook.com>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

/**
 * @file PollModels.kt
 * @brief Poll models for inline poll cards in group chats: the poll row
 *        with question / mode / status and per-option tallies, plus the
 *        option row.
 * @layer Model
 * @dependencies None — pure Kotlin data declarations.
 */

package com.verzeta.android.data.poll

/**
 * Android-side poll model. Mirrors the host's
 * PollService::pollResults() QVariantMap projection: question, mode,
 * status, options with per-option tallies + a winning_option_ids
 * list for highlighting the leader.
 *
 * `mode` is one of "single" / "multi". `status` is one of
 * "open" / "closed". v1 does NOT surface ranked polls — the host
 * rejects creating them, so they'll never reach Android.
 */
data class PollUi(
    val id: String,
    val conversationId: String,
    val creatorKind: String,
    val creatorAlias: String,
    val question: String,
    val mode: String,
    val status: String,
    val createdAt: String,
    val closesAt: String? = null,
    val closedAt: String? = null,
    val options: List<PollOptionUi> = emptyList(),
    val winningOptionIds: List<String> = emptyList(),
    val totalVotes: Int = 0,
    val voterCount: Int = 0,
) {
    val isOpen: Boolean get() = status == "open"
}

/**
 * One poll option with its display order and current vote tally.
 */
data class PollOptionUi(
    val id: String,
    val text: String,
    val ordering: Int = 0,
    val votes: Int = 0,
)
