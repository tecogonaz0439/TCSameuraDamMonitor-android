// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.ui.main

import net.tecogonaz.tcsameuradammonitor.domain.model.HistoricalComparisonData
import net.tecogonaz.tcsameuradammonitor.domain.model.HistoricalComparisonSeries
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 過去年の毎時系列をX軸domainの左右端まで延長するクランプ処理
 * （[buildComparisonEdgeExtendedPoints]）と、表示domain内で線が実際に描かれる年の判定
 * （[comparisonLinesDrawnYears]）を検証するユニットテスト。
 *
 * domain境界への直前/末尾の毎時実測値のクランプ、明示欠測（null）を跨がないためのガード、
 * domain内の欠測マーカー保持、入力リストの非破壊を検証します。
 */
class HistoricalComparisonEdgePointsTest {

    private val hour = 3_600_000L

    @Test
    fun buildComparisonEdgeExtendedPoints_leftClampsPreDomainHourlyValueWhenDomainStartsMidHour() {
        val axis = listOf(0L, hour, 2 * hour, 3 * hour)
        val values = listOf(10f, 11f, 12f, 13f)

        val result = buildComparisonEdgeExtendedPoints(axis, values, rangeStart = hour / 2, rangeEnd = 3 * hour)

        assertEquals(
            listOf(
                hour / 2 to 10f,
                hour to 11f,
                2 * hour to 12f,
                3 * hour to 13f
            ),
            result
        )
    }

    @Test
    fun buildComparisonEdgeExtendedPoints_doesNotLeftClampWhenPreDomainPointIsNull() {
        val axis = listOf(0L, hour, 2 * hour)
        val values = listOf(null, 11f, 12f)

        val result = buildComparisonEdgeExtendedPoints(axis, values, rangeStart = hour / 2, rangeEnd = 2 * hour)

        assertEquals(listOf(hour to 11f, 2 * hour to 12f), result)
    }

    @Test
    fun buildComparisonEdgeExtendedPoints_doesNotLeftClampWhenFirstInDomainPointIsNull() {
        val axis = listOf(0L, hour, 2 * hour)
        val values = listOf(10f, null, 12f)

        val result = buildComparisonEdgeExtendedPoints(axis, values, rangeStart = hour / 2, rangeEnd = 2 * hour)

        assertEquals(listOf(hour to null, 2 * hour to 12f), result)
    }

    @Test
    fun buildComparisonEdgeExtendedPoints_doesNotLeftClampWhenFirstInDomainPointIsOnRangeStart() {
        val axis = listOf(0L, hour, 2 * hour)
        val values = listOf(10f, 11f, 12f)

        val result = buildComparisonEdgeExtendedPoints(axis, values, rangeStart = hour, rangeEnd = 2 * hour)

        assertEquals(listOf(hour to 11f, 2 * hour to 12f), result)
    }

    @Test
    fun buildComparisonEdgeExtendedPoints_doesNotLeftClampWhenFirstInDomainIndexIsZero() {
        val axis = listOf(0L, hour, 2 * hour)
        val values = listOf(10f, 11f, 12f)

        val result = buildComparisonEdgeExtendedPoints(axis, values, rangeStart = 0L, rangeEnd = 2 * hour)

        assertEquals(listOf(0L to 10f, hour to 11f, 2 * hour to 12f), result)
    }

    @Test
    fun buildComparisonEdgeExtendedPoints_rightClampsLastInDomainValueWhenDomainEndsMidHour() {
        val axis = listOf(0L, hour, 2 * hour)
        val values = listOf(10f, 11f, 12f)

        val result = buildComparisonEdgeExtendedPoints(axis, values, rangeStart = 0L, rangeEnd = 2 * hour + hour / 2)

        assertEquals(
            listOf(0L to 10f, hour to 11f, 2 * hour to 12f, 2 * hour + hour / 2 to 12f),
            result
        )
    }

    @Test
    fun buildComparisonEdgeExtendedPoints_doesNotRightClampWhenLastInDomainPointIsNull() {
        val axis = listOf(0L, hour, 2 * hour)
        val values = listOf(10f, 11f, null)

        val result = buildComparisonEdgeExtendedPoints(axis, values, rangeStart = 0L, rangeEnd = 2 * hour + hour / 2)

        assertEquals(listOf(0L to 10f, hour to 11f, 2 * hour to null), result)
    }

    @Test
    fun buildComparisonEdgeExtendedPoints_doesNotRightClampWhenLastInDomainPointIsOnRangeEnd() {
        val axis = listOf(0L, hour, 2 * hour)
        val values = listOf(10f, 11f, 12f)

        val result = buildComparisonEdgeExtendedPoints(axis, values, rangeStart = 0L, rangeEnd = 2 * hour)

        assertEquals(listOf(0L to 10f, hour to 11f, 2 * hour to 12f), result)
    }

    @Test
    fun buildComparisonEdgeExtendedPoints_returnsEmptyWhenNoInDomainPoints() {
        val axis = listOf(0L, hour, 2 * hour)
        val values = listOf(10f, 11f, 12f)

        val result = buildComparisonEdgeExtendedPoints(axis, values, rangeStart = 100 * hour, rangeEnd = 101 * hour)

        assertEquals(emptyList<Pair<Long, Float?>>(), result)
    }

    @Test
    fun buildComparisonEdgeExtendedPoints_preservesNullMarkersInsideDomain() {
        val axis = listOf(0L, hour, 2 * hour, 3 * hour, 4 * hour)
        val values = listOf(10f, null, 12f, null, 14f)

        val result = buildComparisonEdgeExtendedPoints(axis, values, rangeStart = 0L, rangeEnd = 4 * hour)

        assertEquals(
            listOf(
                0L to 10f,
                hour to null,
                2 * hour to 12f,
                3 * hour to null,
                4 * hour to 14f
            ),
            result
        )
    }

    @Test
    fun buildComparisonEdgeExtendedPoints_doesNotMutateInputs() {
        val axis = listOf(0L, hour, 2 * hour, 3 * hour)
        val values = listOf(10f, null, 12f, null)
        val axisBefore = axis.toList()
        val valuesBefore = values.toList()

        buildComparisonEdgeExtendedPoints(axis, values, rangeStart = hour / 2, rangeEnd = 3 * hour + hour / 2)

        assertEquals(axisBefore, axis)
        assertEquals(valuesBefore, values)
    }

    @Test
    fun comparisonLinesDrawnYears_includesOnlyYearsWithInDomainObservationPoints() {
        val data = comparisonData(
            axis = listOf(0L, hour, 2 * hour),
            series = listOf(
                2002 to listOf(null, null, null),
                2003 to listOf(10f, null, 12f),
                2004 to listOf(null, 11f, 13f)
            )
        )

        val result = comparisonLinesDrawnYears(data, rangeStart = hour / 2, rangeEnd = 2 * hour)

        assertEquals(setOf(2003, 2004), result)
    }

    @Test
    fun comparisonLinesDrawnYears_returnsEmptyWhenDomainHasNoPoints() {
        val data = comparisonData(
            axis = listOf(0L, hour, 2 * hour),
            series = listOf(
                2002 to listOf(10f, 11f, 12f),
                2003 to listOf(10f, 11f, 12f)
            )
        )

        val result = comparisonLinesDrawnYears(data, rangeStart = 100 * hour, rangeEnd = 101 * hour)

        assertEquals(emptySet<Int>(), result)
    }

    @Test
    fun comparisonLinesDrawnYears_includesValuesOnDomainBoundariesInclusive() {
        val data = comparisonData(
            axis = listOf(0L, hour, 2 * hour),
            series = listOf(
                2002 to listOf(null, 10f, null),
                2003 to listOf(null, null, 12f),
                2004 to listOf(10f, null, null)
            )
        )

        val result = comparisonLinesDrawnYears(data, rangeStart = hour, rangeEnd = 2 * hour)

        // ちょうど rangeStart / rangeEnd 上の観測値は線を描くため対象に含まれ、domain外のみの年は除外される。
        assertEquals(setOf(2002, 2003), result)
    }

    @Test
    fun comparisonLinesDrawnYears_ignoresValuesWithoutAxisTime() {
        val data = comparisonData(
            axis = listOf(0L, hour),
            series = listOf(
                2002 to listOf(10f, null, 11f),
                2003 to listOf(null, null, 12f)
            )
        )

        val result = comparisonLinesDrawnYears(data, rangeStart = 0L, rangeEnd = hour)

        // valuesが軸より長い場合、軸に対応しない末尾の値は描画されないため対象外となる。
        assertEquals(setOf(2002), result)
    }

    private fun comparisonData(
        axis: List<Long>,
        series: List<Pair<Int, List<Float?>>>
    ): HistoricalComparisonData = HistoricalComparisonData(
        currentYear = 2026,
        availablePastYears = series.map { it.first },
        periodStartMillis = axis.first(),
        periodEndMillis = axis.last(),
        hourlyAxisMillis = axis,
        series = series.map { (year, values) ->
            HistoricalComparisonSeries(year = year, values = values)
        }
    )
}
