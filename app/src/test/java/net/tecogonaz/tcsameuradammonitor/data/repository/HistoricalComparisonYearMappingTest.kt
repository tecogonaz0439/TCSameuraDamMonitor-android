// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.data.repository

import net.tecogonaz.tcsameuradammonitor.util.TimeUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/**
 * 過去比較グラフ用の年写像ユーティリティ群のユニットテストクラス。
 *
 * [HistoricalComparisonRepositoryImpl] と同じパッケージの内部トップレベル関数
 * [availablePastYearsFor], [currentJstYear], [floorToJstHourMillis],
 * [buildHourlyAxisMillis], [mapYearToComparison], [resolveMappedBoundary],
 * [mapYearFromComparison] の動作を検証します。
 * エポックミリ秒は全てJST（[TimeUtils.JST_ZONE]）基準で組み立てます。
 */
class HistoricalComparisonYearMappingTest {

    @Test
    fun availablePastYearsFor_returnsAscendingYearsBeforeCurrentYear() {
        val expected = (2002..2025).toList()
        assertEquals(expected, availablePastYearsFor(2026))
        assertEquals(24, availablePastYearsFor(2026).size)
        assertTrue(availablePastYearsFor(2026) == availablePastYearsFor(2026).sorted())

        assertTrue(availablePastYearsFor(2002).isEmpty())
        assertEquals(listOf(2002), availablePastYearsFor(2003))
    }

    @Test
    fun currentJstYear_convertsClockMillisInJst() {
        assertEquals(2026, currentJstYear(jstMillis(2026, 1, 1, 0, 30)))
        assertEquals(2025, currentJstYear(jstMillis(2025, 12, 31, 23, 30)))
    }

    @Test
    fun floorToJstHourMillis_floorsMinutesAndSeconds() {
        assertEquals(jstMillis(2026, 6, 28, 12, 0), floorToJstHourMillis(jstMillisWithSeconds(2026, 6, 28, 12, 34, 56)))
        val normalizedNextDayMidnight = jstMillis(2026, 6, 29, 0, 0)
        assertEquals(normalizedNextDayMidnight, floorToJstHourMillis(normalizedNextDayMidnight))
    }

    @Test
    fun buildHourlyAxisMillis_buildsHourlyAxisIncludingBothEnds() {
        val start = jstMillis(2026, 6, 28, 0, 10)
        val end = jstMillis(2026, 6, 28, 2, 50)
        assertEquals(
            listOf(
                jstMillis(2026, 6, 28, 0, 0),
                jstMillis(2026, 6, 28, 1, 0),
                jstMillis(2026, 6, 28, 2, 0)
            ),
            buildHourlyAxisMillis(start, end)
        )
        assertTrue(buildHourlyAxisMillis(end, start).isEmpty())
    }

    @Test
    fun mapYearToComparison_mapsMonthAndTimeKeepingLeapDayHandling() {
        assertNull(mapYearToComparison(jstMillis(2024, 2, 29, 12, 0), 2024, 2025))

        assertEquals(
            jstMillis(2028, 2, 29, 12, 0),
            mapYearToComparison(jstMillis(2024, 2, 29, 12, 0), 2024, 2028)
        )

        assertEquals(
            jstMillis(2002, 1, 2, 0, 0),
            mapYearToComparison(jstMillis(2026, 1, 2, 0, 0), 2026, 2002)
        )

        assertEquals(
            jstMillis(2001, 12, 31, 23, 0),
            mapYearToComparison(jstMillis(2025, 12, 31, 23, 0), 2026, 2002)
        )
    }

    @Test
    fun resolveMappedBoundary_walksUpToThreeDaysOnlyOnFailure() {
        assertEquals(
            jstMillis(2025, 3, 1, 0, 0),
            resolveMappedBoundary(jstMillis(2024, 2, 29, 0, 0), 2024, 2025, +1)
        )
        assertEquals(
            jstMillis(2025, 2, 28, 0, 0),
            resolveMappedBoundary(jstMillis(2024, 2, 29, 0, 0), 2024, 2025, -1)
        )
        assertEquals(
            jstMillis(2025, 6, 28, 0, 0),
            resolveMappedBoundary(jstMillis(2024, 6, 28, 0, 0), 2024, 2025, +1)
        )
    }

    @Test
    fun mapYearFromComparison_mapsComparisonYearToBaseYear() {
        assertEquals(
            jstMillis(2026, 1, 2, 0, 0),
            mapYearFromComparison(jstMillis(2002, 1, 2, 0, 0), 2002, 2026)
        )
    }

    private fun jstMillis(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        jstMillisWithSeconds(year, month, day, hour, minute, 0)

    private fun jstMillisWithSeconds(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
        second: Int
    ): Long =
        LocalDateTime.of(year, month, day, hour, minute, second)
            .atZone(TimeUtils.JST_ZONE)
            .toInstant()
            .toEpochMilli()
}
