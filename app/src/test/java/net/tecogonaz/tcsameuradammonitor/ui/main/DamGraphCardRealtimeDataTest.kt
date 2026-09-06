// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.ui.main

import androidx.compose.ui.unit.dp
import net.tecogonaz.tcsameuradammonitor.domain.model.DamHistoricalData
import net.tecogonaz.tcsameuradammonitor.domain.model.HistoricalComparisonData
import net.tecogonaz.tcsameuradammonitor.domain.model.HistoricalComparisonSeries
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * リアルタイムグラフ（[ObservationGraphCard]）の描画用データ構築ロジックを検証するユニットテストクラス。
 * 過去24時間/48時間/72時間/全範囲における欠損値（メンテナンス時間帯など）の補間、グラフの表示開始/終了点の算出、
 * ツールチップに表示するテキストのフォーマット、過去データのスパンに応じた表示レンジ選択肢の制御などが
 * 仕様通りに動作することを検証します。
 */
class DamGraphCardRealtimeDataTest {
    @Test
    fun observationGraphHeight_satisfiesMinimum() {
        assertEquals(250.dp, observationGraphHeight(100.dp))
    }

    @Test
    fun observationGraphHeight_scalesProportionally() {
        assertEquals(300.dp, observationGraphHeight(600.dp))
    }

    @Test
    fun observationGraphHeight_limitsWideViews() {
        assertEquals(420.dp, observationGraphHeight(1_200.dp))
    }

    @Test
    fun observationGraphHeight_capsByMaxHeight() {
        assertEquals(290.dp, observationGraphHeight(1_200.dp, maxHeight = 290.dp))
    }

    @Test
    fun observationGraphHeight_honorsSmallMaxHeight() {
        assertEquals(100.dp, observationGraphHeight(1_200.dp, maxHeight = 100.dp))
    }

    @Test
    fun observationGraphHeight_ignoresLargerMaxHeight() {
        assertEquals(420.dp, observationGraphHeight(1_200.dp, maxHeight = 2_000.dp))
    }

    @Test
    fun observationGraphHeight_hasMinimumCanvasHeight() {
        assertEquals(80.dp, observationGraphHeight(1_200.dp, maxHeight = 50.dp))
    }

    @Test
    fun buildRealtimeStorageGraphDisplayData_past24HoursAddsStartPointFromPreviousHourlyValue() {
        val data = listOf(
            historicalData("2026/05/18 05:00", 70f),
            historicalData("2026/05/18 05:20", null),
            historicalData("2026/05/18 06:00", 71f),
            historicalData("2026/05/19 05:00", 80f),
            historicalData("2026/05/19 05:15", null)
        )

        val result = buildRealtimeStorageGraphDisplayData(data, RealtimeGraphRange.PAST_24_HOURS)

        assertEquals("2026/05/18 05:15", result.data.first().time)
        assertEquals(70f, result.data.first().storagePercentage)
    }

    @Test
    fun buildRealtimeStorageGraphDisplayData_past24HoursFillsTrailingPartialHour() {
        val data = listOf(
            historicalData("2026/05/18 05:00", 70f),
            historicalData("2026/05/18 06:00", 71f),
            historicalData("2026/05/19 05:00", 80f),
            historicalData("2026/05/19 05:10", null, catchmentAverageRainfall = 0f)
        )

        val result = buildRealtimeStorageGraphDisplayData(data, RealtimeGraphRange.PAST_24_HOURS)

        assertEquals("2026/05/19 05:10", result.data.last().time)
        assertEquals(80f, result.data.last().storagePercentage)
    }

    @Test
    fun buildRealtimeStorageGraphDisplayData_doesNotFillAllMissingMaintenanceRows() {
        val data = listOf(
            historicalData("2026/05/18 05:00", 70f, catchmentAverageRainfall = 0f),
            historicalData("2026/05/19 05:00", 80f, catchmentAverageRainfall = 0f),
            historicalData("2026/05/19 05:10", null),
            historicalData("2026/05/19 05:20", null)
        )

        val result = buildRealtimeStorageGraphDisplayData(data, RealtimeGraphRange.PAST_24_HOURS)

        assertEquals(null, result.data[result.data.lastIndex - 1].storagePercentage)
        assertEquals(null, result.data.last().storagePercentage)
    }

    @Test
    fun buildRealtimeStorageGraphDisplayData_doesNotFillStartWhenPreviousValueIsTooOld() {
        val data = listOf(
            historicalData("2026/05/18 04:00", 70f),
            historicalData("2026/05/18 05:20", null),
            historicalData("2026/05/19 05:15", null)
        )

        val result = buildRealtimeStorageGraphDisplayData(data, RealtimeGraphRange.PAST_24_HOURS)

        assertEquals("2026/05/18 05:20", result.data.first().time)
        assertEquals(null, result.data.first().storagePercentage)
    }

    @Test
    fun buildRealtimeStorageGraphDisplayData_allRangeFillsIntermediatePoints() {
        val data = listOf(
            historicalData("2026/05/18 05:00", 70f, catchmentAverageRainfall = 0f),
            historicalData("2026/05/18 05:10", null, catchmentAverageRainfall = 0f),
            historicalData("2026/05/18 05:20", null, catchmentAverageRainfall = 0f),
            historicalData("2026/05/18 06:00", 71f, catchmentAverageRainfall = 0f)
        )

        val result = buildRealtimeStorageGraphDisplayData(data, RealtimeGraphRange.ALL)

        assertEquals(4, result.data.size)
        assertEquals(70f, result.data[0].storagePercentage)
        assertEquals(70f, result.data[1].storagePercentage)
        assertEquals(70f, result.data[2].storagePercentage)
        assertEquals(71f, result.data[3].storagePercentage)
        assertEquals(null, result.windowStartMillis)
        assertEquals(null, result.windowEndMillis)
    }

    @Test
    fun buildRealtimeStorageGraphDisplayData_allRangeDoesNotFillWhenNoObservationValues() {
        val data = listOf(
            historicalData("2026/05/18 05:00", 70f),
            historicalData("2026/05/18 05:10", null)
        )

        val result = buildRealtimeStorageGraphDisplayData(data, RealtimeGraphRange.ALL)

        assertEquals(2, result.data.size)
        assertEquals(70f, result.data[0].storagePercentage)
        assertEquals(null, result.data[1].storagePercentage)
    }

    @Test
    fun buildRealtimeStorageGraphDisplayData_allRangeDoesNotFillBeyondOneHour() {
        val data = listOf(
            historicalData("2026/05/18 05:00", 70f, catchmentAverageRainfall = 0f),
            historicalData("2026/05/18 06:10", null, catchmentAverageRainfall = 0f)
        )

        val result = buildRealtimeStorageGraphDisplayData(data, RealtimeGraphRange.ALL)

        assertEquals(2, result.data.size)
        assertEquals(70f, result.data[0].storagePercentage)
        assertEquals(null, result.data[1].storagePercentage)
    }

    @Test
    fun buildRealtimeStorageGraphDisplayData_allRangeFillsAtExactlyOneHour() {
        val data = listOf(
            historicalData("2026/05/18 05:00", 70f, catchmentAverageRainfall = 0f),
            historicalData("2026/05/18 06:00", null, catchmentAverageRainfall = 0f)
        )

        val result = buildRealtimeStorageGraphDisplayData(data, RealtimeGraphRange.ALL)

        assertEquals(2, result.data.size)
        assertEquals(70f, result.data[0].storagePercentage)
        assertEquals(70f, result.data[1].storagePercentage)
    }

    @Test
    fun buildRealtimeGraphDisplayData_keepsAllMissingRowsInSelectedRange() {
        val data = listOf(
            historicalData("2026/05/18 05:00", 70f, storageVolume = 100000f, inflow = 10f, outflow = 9f),
            historicalData("2026/05/19 05:00", null),
            historicalData("2026/05/19 05:10", null)
        )

        val result = buildRealtimeGraphDisplayData(data, RealtimeGraphRange.PAST_24_HOURS)

        assertEquals(2, result.data.count { !it.hasAnyObservationValue() })
        assertEquals("2026/05/19 05:10", result.data.last().time)
    }

    @Test
    fun buildRealtimeGraphDisplayData_keeps24HourRowsInSelectedRange() {
        val data = listOf(
            historicalData("2026/05/18 23:50", 70f, storageVolume = 100000f, inflow = 10f, outflow = 9f),
            historicalData("2026/05/18 24:00", 71f, storageVolume = 100100f, inflow = 11f, outflow = 10f),
            historicalData("2026/05/19 00:10", 72f, storageVolume = 100200f, inflow = 12f, outflow = 11f)
        )

        val result = buildRealtimeGraphDisplayData(data, RealtimeGraphRange.PAST_24_HOURS)

        assertEquals(3, result.data.size)
        assertEquals("2026/05/18 24:00", result.data[1].time)
    }

    @Test
    fun buildStorageGraphTooltipText_usesMissingPercentageWithoutBackfilling() {
        val result = buildStorageGraphTooltipText(
            timeStr = "2026/05/19 05:10",
            rainfallValue = null,
            storagePercentageValue = null,
            labelTooltipRainfall = "雨量",
            labelTooltipStorageRate = "貯水率",
            rainfallUnitLabel = "mm/10min",
            storagePercentageDecimals = 2
        )

        assertEquals("2026/05/19 05:10 雨量 --mm/10min 貯水率 -- %", result)
    }

    @Test
    fun realtimeGraphScaleValues_usesAllRangeValuesForRealtimeRange() {
        val result = realtimeGraphScaleValues(
            allRangeValues = listOf(1f, 100f),
            selectedRangeValues = listOf(20f, 30f),
            isHistorical = false
        )

        assertEquals(listOf(1f, 100f), result)
    }

    @Test
    fun realtimeGraphScaleValues_usesSelectedValuesForHistoricalMode() {
        val result = realtimeGraphScaleValues(
            allRangeValues = listOf(1f, 100f),
            selectedRangeValues = listOf(20f, 30f),
            isHistorical = true
        )

        assertEquals(listOf(20f, 30f), result)
    }

    @Test
    fun resolveGraphXAxisRange_realtimeIgnoresHistoricalRange() {
        val result = resolveGraphXAxisRange(
            isHistorical = false,
            historicalStartMillis = 1L,
            historicalEndMillis = 2L,
            realtimeStartMillis = 10L,
            realtimeEndMillis = 20L
        )

        assertEquals(10L to 20L, result)
    }

    @Test
    fun resolveGraphXAxisRange_historicalUsesHistoricalRange() {
        val result = resolveGraphXAxisRange(
            isHistorical = true,
            historicalStartMillis = 1L,
            historicalEndMillis = 2L,
            realtimeStartMillis = 10L,
            realtimeEndMillis = 20L
        )

        assertEquals(1L to 2L, result)
    }

    @Test
    fun realtimeGraphRangeOptionsForData_hidesPast24AndPast48WhenPeriodIs24HoursOrLess() {
        val data = listOf(
            historicalData("2026/05/18 05:00", 70f),
            historicalData("2026/05/19 05:00", 80f)
        )

        val result = realtimeGraphRangeOptionsForData(data)

        assertEquals(listOf(RealtimeGraphRange.ALL), result)
    }

    @Test
    fun realtimeGraphRangeOptionsForData_hidesPast48WhenPeriodIs48HoursOrLess() {
        val data = listOf(
            historicalData("2026/05/17 05:00", 70f),
            historicalData("2026/05/19 05:00", 80f)
        )

        val result = realtimeGraphRangeOptionsForData(data)

        assertEquals(
            listOf(RealtimeGraphRange.ALL, RealtimeGraphRange.PAST_24_HOURS),
            result
        )
    }

    @Test
    fun realtimeGraphRangeOptionsForData_showsAllRangeOptionsWhenPeriodIsLongerThan48Hours() {
        val data = listOf(
            historicalData("2026/05/17 04:50", 70f),
            historicalData("2026/05/19 05:00", 80f)
        )

        val result = realtimeGraphRangeOptionsForData(data)

        assertEquals(
            listOf(
                RealtimeGraphRange.ALL,
                RealtimeGraphRange.PAST_48_HOURS,
                RealtimeGraphRange.PAST_24_HOURS
            ),
            result
        )
    }

    @Test
    fun realtimeGraphRangeOptionsForData_showsAllRangeOptionsWhenPeriodIsLongerThan72Hours() {
        val data = listOf(
            historicalData("2026/05/13 05:00", 70f),
            historicalData("2026/05/19 05:00", 80f)
        )

        val result = realtimeGraphRangeOptionsForData(data)

        assertEquals(REALTIME_GRAPH_RANGE_OPTIONS, result)
    }

    @Test
    fun resolveComparisonXAxisRange_allRangeFallsBackToComparisonPeriod() {
        val resolved: Pair<Long, Long>? = null
        val period = 1_000L to 2_000L

        val result = resolveComparisonXAxisRange(
            isComparisonMode = true,
            resolvedRange = resolved,
            comparisonPeriod = period
        )

        assertEquals(period, result)
    }

    @Test
    fun resolveComparisonXAxisRange_specifiedRangeKeepsResolvedWindow() {
        val resolved = 100L to 900L
        val period = 1_000L to 2_000L

        val result = resolveComparisonXAxisRange(
            isComparisonMode = true,
            resolvedRange = resolved,
            comparisonPeriod = period
        )

        assertEquals(resolved, result)
    }

    @Test
    fun resolveComparisonXAxisRange_nonComparisonKeepsResolvedOrNull() {
        val period = 1_000L to 2_000L
        assertEquals(null, resolveComparisonXAxisRange(false, null, period))
        assertEquals(5L to 6L, resolveComparisonXAxisRange(false, 5L to 6L, period))
    }

    @Test
    fun resolveComparisonXAxisRange_invalidComparisonPeriodIsIgnored() {
        val reversed = 2_000L to 1_000L
        assertEquals(null, resolveComparisonXAxisRange(true, null, reversed))
        assertEquals(null, resolveComparisonXAxisRange(true, null, null))
    }

    @Test
    fun resolveComparisonRangeMillis_specifiedRangeShrinksToSelectedHoursFromDomainEnd() {
        val end = 1_000_000L
        val start = end - 7L * 24L * 3_600_000L
        val xAxis = start to end

        val result = resolveComparisonRangeMillis(
            isComparisonMode = true,
            xAxisRangeMillis = xAxis,
            realtimeRange = RealtimeGraphRange.PAST_24_HOURS
        )

        assertEquals(end - 24L * 3_600_000L to end, result)
    }

    @Test
    fun resolveComparisonRangeMillis_specifiedRangeClampsToDomainStartWhenDomainIsShorter() {
        val xAxis = 1_000L to 10_000L

        val result = resolveComparisonRangeMillis(
            isComparisonMode = true,
            xAxisRangeMillis = xAxis,
            realtimeRange = RealtimeGraphRange.PAST_72_HOURS
        )

        assertEquals(1_000L to 10_000L, result)
    }

    @Test
    fun resolveComparisonRangeMillis_allRangeKeepsDomain() {
        val xAxis = 1_000L to 10_000L
        assertEquals(xAxis, resolveComparisonRangeMillis(true, xAxis, RealtimeGraphRange.ALL))
    }

    @Test
    fun resolveComparisonRangeMillis_nonComparisonModeReturnsNull() {
        val xAxis = 1_000L to 10_000L
        assertEquals(null, resolveComparisonRangeMillis(false, xAxis, RealtimeGraphRange.PAST_24_HOURS))
        assertEquals(null, resolveComparisonRangeMillis(false, null, RealtimeGraphRange.PAST_24_HOURS))
    }

    @Test
    fun resolveComparisonRangeMillis_nullDomainReturnsNull() {
        assertEquals(null, resolveComparisonRangeMillis(true, null, RealtimeGraphRange.PAST_24_HOURS))
        assertEquals(null, resolveComparisonRangeMillis(true, null, RealtimeGraphRange.ALL))
    }

    @Test
    fun buildComparisonVolumeScaleValues_appendsSelectedPastYearValuesOnly() {
        val comparisonData = HistoricalComparisonData(
            currentYear = 2026,
            availablePastYears = listOf(2024, 2025),
            periodStartMillis = 0L,
            periodEndMillis = 3_600_000L,
            hourlyAxisMillis = listOf(0L, 3_600_000L),
            series = listOf(
                HistoricalComparisonSeries(2024, listOf(100_000f, null, 110_000f)),
                HistoricalComparisonSeries(2025, listOf(95_000f, 96_000f, null))
            )
        )

        val result = buildComparisonVolumeScaleValues(
            isComparisonMode = true,
            baseValues = listOf(60_000f),
            comparisonData = comparisonData,
            selectedPastYears = setOf(2024)
        )

        assertEquals(listOf(60_000f, 100_000f, 110_000f), result)
    }

    @Test
    fun buildComparisonVolumeScaleValues_ignoresDeselectedYears() {
        val comparisonData = HistoricalComparisonData(
            currentYear = 2026,
            availablePastYears = listOf(2024, 2025),
            periodStartMillis = 0L,
            periodEndMillis = 3_600_000L,
            hourlyAxisMillis = listOf(0L, 3_600_000L),
            series = listOf(
                HistoricalComparisonSeries(2024, listOf(100_000f)),
                HistoricalComparisonSeries(2025, listOf(95_000f))
            )
        )

        val result = buildComparisonVolumeScaleValues(
            isComparisonMode = true,
            baseValues = listOf(60_000f),
            comparisonData = comparisonData,
            selectedPastYears = emptySet()
        )

        assertEquals(listOf(60_000f), result)
    }

    @Test
    fun buildComparisonVolumeScaleValues_returnsBaseValuesInNormalModeOrWithoutData() {
        val comparisonData = HistoricalComparisonData(
            currentYear = 2026,
            availablePastYears = listOf(2024),
            periodStartMillis = 0L,
            periodEndMillis = 3_600_000L,
            hourlyAxisMillis = listOf(0L, 3_600_000L),
            series = listOf(HistoricalComparisonSeries(2024, listOf(100_000f)))
        )
        val base = listOf(60_000f)

        assertEquals(base, buildComparisonVolumeScaleValues(false, base, comparisonData, setOf(2024)))
        assertEquals(base, buildComparisonVolumeScaleValues(true, base, null, setOf(2024)))
    }

    private fun historicalData(
        time: String,
        storagePercentage: Float?,
        catchmentAverageRainfall: Float? = null,
        storageVolume: Float? = null,
        inflow: Float? = null,
        outflow: Float? = null
    ): DamHistoricalData =
        DamHistoricalData(
            time = time,
            catchmentAverageRainfall = catchmentAverageRainfall,
            storagePercentage = storagePercentage,
            storageVolume = storageVolume,
            inflow = inflow,
            outflow = outflow
        )
}
