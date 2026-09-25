// SPDX-FileCopyrightText: 2026 Aditya Mehra <aix.m@outlook.com>
// SPDX-License-Identifier: LGPL-3.0-or-later

package com.verzeta.android.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins [normaliseTimestamp]: the host sends some timestamps as epoch
 * milliseconds and others as ISO text, and the UI formats and sorts them as
 * strings, so both must end up in the same ISO form.
 */
class TimestampsTest {

    @Test
    fun `epoch milliseconds become UTC ISO with milliseconds`() {
        assertEquals("2024-03-09T16:00:00.000Z", normaliseTimestamp("1710000000000"))
        assertEquals("2024-03-09T16:00:00.123Z", normaliseTimestamp("1710000000123"))
    }

    @Test
    fun `ISO text passes through unchanged`() {
        val iso = "2024-03-09T16:00:00.123Z"
        assertEquals(iso, normaliseTimestamp(iso))
    }

    @Test
    fun `zero and empty mean no timestamp`() {
        assertEquals("", normaliseTimestamp("0"))
        assertEquals("", normaliseTimestamp(""))
    }

    @Test
    fun `normalised values sort in time order`() {
        // A conv.list row (ISO) and an event row (milliseconds) must sort
        // together; raw digits used to sort below every ISO string.
        val older = normaliseTimestamp("2024-03-09T16:00:00.000Z")
        val newer = normaliseTimestamp("1710000060000")
        assertTrue(newer > older)
    }
}
