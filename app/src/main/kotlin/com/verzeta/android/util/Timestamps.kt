// SPDX-FileCopyrightText: 2026 Aditya Mehra <aix.m@outlook.com>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

/**
 * @file Timestamps.kt
 * @brief Formats host-emitted ISO 8601 timestamps into compact relative
 *        labels (now / Nm / Nh / Yesterday / weekday / date) matching the
 *        desktop sidebar's buckets, and normalises epoch-millisecond
 *        timestamps to the same ISO 8601 form.
 * @layer Utility
 * @dependencies java.time (Instant, LocalDate, ZoneId, DateTimeFormatter).
 */

package com.verzeta.android.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Format an ISO 8601 timestamp (with or without milliseconds) emitted by the
 * host's `Serialise::isoMs()` into a compact, sidebar-friendly relative label.
 *
 * Buckets, mirroring how the desktop sidebar reads:
 *   - now (< 1 minute)
 *   - Nm   (< 60 minutes)
 *   - Nh   (< 24 hours, same calendar day)
 *   - Yesterday
 *   - Mon / Tue / ...   (same ISO week)
 *   - MMM d              (same year)
 *   - MMM d, yyyy        (older)
 *
 * Returns the input verbatim when parsing fails so we never silently mask
 * protocol drift.
 */
fun formatRelativeTimestamp(
    iso: String,
    now: Instant = Instant.now(),
    zone: ZoneId = ZoneId.systemDefault(),
): String {
    if (iso.isBlank()) return ""
    val instant = runCatching { Instant.parse(iso) }.getOrNull() ?: return iso
    val deltaSeconds = ChronoUnit.SECONDS.between(instant, now)
    if (deltaSeconds in 0..59) return "now"
    val deltaMinutes = ChronoUnit.MINUTES.between(instant, now)
    if (deltaMinutes in 0..59) return "${deltaMinutes}m"
    val today = LocalDate.now(zone)
    val date = instant.atZone(zone).toLocalDate()
    val daysAgo = ChronoUnit.DAYS.between(date, today)
    return when {
        daysAgo == 0L -> "${ChronoUnit.HOURS.between(instant, now)}h"
        daysAgo == 1L -> "Yesterday"
        daysAgo in 2..6 -> date.dayOfWeek.name.lowercase().replaceFirstChar(Char::uppercase).take(3)
        date.year == today.year -> date.format(DateTimeFormatter.ofPattern("MMM d"))
        else -> date.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
    }
}

/** UTC ISO 8601 with milliseconds, the form the host uses for ISO timestamps. */
private val isoMillisFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC)

/**
 * Normalise a host timestamp to ISO 8601.
 *
 * Some host payloads carry timestamps as epoch milliseconds (for example
 * the rows attached to `conv.added` / `conv.updated`, `clients.list` and
 * tool-call rows) while others carry ISO strings. Converting the numeric
 * form here lets every screen format and sort timestamps the same way.
 *
 * @param raw the value as it arrived: ISO text, a decimal millisecond
 *        count, or empty.
 * @returns the ISO 8601 UTC form with milliseconds for a positive
 *          millisecond count, an empty string for zero or a negative
 *          count, and [raw] unchanged otherwise.
 */
fun normaliseTimestamp(raw: String): String {
    val trimmed = raw.trim()
    val ms = trimmed.toLongOrNull() ?: return raw
    if (ms <= 0L) return ""
    return isoMillisFormatter.format(Instant.ofEpochMilli(ms))
}
