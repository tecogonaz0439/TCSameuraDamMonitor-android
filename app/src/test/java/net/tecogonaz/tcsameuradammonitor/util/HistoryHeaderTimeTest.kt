// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.OffsetDateTime

class HistoryHeaderTimeTest {
    @Test
    fun parse_acceptsOffsetDateTimesAndContractT24Values() {
        val validCases = listOf(
            "2026-07-01T01:00:00+09:00" to "2026-07-01T01:00:00+09:00",
            "2026-07-01T01:00+09:00" to "2026-07-01T01:00+09:00",
            "2026-07-01T01:00:00.123+09:00" to "2026-07-01T01:00:00.123+09:00",
            "2026-07-31T24:00+09:00" to "2026-08-01T00:00+09:00",
            "2026-07-31T24:00:00+09:00" to "2026-08-01T00:00:00+09:00",
            "2026-07-31T24:00Z" to "2026-08-01T00:00Z",
            " 2026-07-31T24:00+09:00 " to "2026-08-01T00:00+09:00"
        )

        validCases.forEach { (input, expected) ->
            assertEquals(input, OffsetDateTime.parse(expected), HistoryHeaderTime.parse(input))
        }
    }

    @Test
    fun parse_rejectsValuesOutsideTheHeaderContract() {
        val invalidValues = listOf(
            null,
            "",
            "   ",
            "not-a-date",
            "2026-07-31T24:01+09:00",
            "2026-07-31T24:00:01+09:00",
            "2026-07-31T25:00+09:00",
            "2026-07-31T24:00:00.5+09:00",
            "2026-02-30T24:00:00+09:00",
            "2026-7-31T24:00:00+09:00",
            "2026-07-31T24:00",
            "T24:00:00+09:00"
        )

        invalidValues.forEach { input ->
            assertNull(input, HistoryHeaderTime.parse(input))
        }
    }

    @Test
    fun rollingCoverage_t24EndUsesPreviousJstDay() {
        val range = HistoryHeaderTime.rollingCoverage(
            since = "2026-07-01T01:00:00+09:00",
            until = "2026-07-31T24:00:00+09:00"
        )

        assertEquals(
            HistoryHeaderTime.DayRange(
                start = LocalDate.of(2026, 7, 1),
                end = LocalDate.of(2026, 7, 31)
            ),
            range
        )
    }

    @Test
    fun rollingCoverage_midnightEndUsesPreviousDay() {
        val range = HistoryHeaderTime.rollingCoverage(
            since = "2026-07-01T01:00+09:00",
            until = "2026-08-01T00:00+09:00"
        )

        assertEquals(
            HistoryHeaderTime.DayRange(
                start = LocalDate.of(2026, 7, 1),
                end = LocalDate.of(2026, 7, 31)
            ),
            range
        )
    }

    @Test
    fun rollingCoverage_nonMidnightEndIncludesItsJstDay() {
        val range = HistoryHeaderTime.rollingCoverage(
            since = "2026-07-01T01:00+09:00",
            until = "2026-08-01T01:00+09:00"
        )

        assertEquals(
            HistoryHeaderTime.DayRange(
                start = LocalDate.of(2026, 7, 1),
                end = LocalDate.of(2026, 8, 1)
            ),
            range
        )
    }

    @Test
    fun rollingCoverage_missingOrInvalidHeaderReturnsNull() {
        assertNull(HistoryHeaderTime.rollingCoverage(null, "2026-07-31T24:00+09:00"))
        assertNull(HistoryHeaderTime.rollingCoverage("2026-07-01T01:00+09:00", null))
        assertNull(
            HistoryHeaderTime.rollingCoverage(
                since = "2026-07-01T01:00+09:00",
                until = "2026-07-31T24:01+09:00"
            )
        )
    }
}
