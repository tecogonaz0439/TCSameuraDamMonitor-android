// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.ui.main

import net.tecogonaz.tcsameuradammonitor.domain.model.HistoricalComparisonMetric
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 過去比較グラフのツールチップ行生成を検証するユニットテスト。
 *
 * ソート（値降順・同値は今年優先・年降順・欠測は最後）、過去年9年以上の圧縮表示、
 * 欠測（null）の "--%" / "--×10³m³" 表記、メトリック別の値書式を検証します。
 */
class HistoricalComparisonTooltipTest {

    @Test
    fun sortComparisonTooltipRows_ordersByValueDescending() {
        val rows = listOf(
            pastRow(year = 2024, value = 10.0f),
            pastRow(year = 2023, value = 11.0f),
            pastRow(year = 2025, value = 9.5f),
        )

        val sorted = sortComparisonTooltipRows(rows)

        assertEquals(listOf(2023, 2024, 2025), sorted.map { it.year })
    }

    @Test
    fun sortComparisonTooltipRows_ordersCurrentYearFirstAmongEqualValues() {
        val rows = listOf(
            pastRow(year = 2025, value = 10.5f),
            pastRow(year = 2024, value = 10.5f),
            currentRow(value = 10.5f),
        )

        val sorted = sortComparisonTooltipRows(rows)

        assertEquals(listOf(2026, 2025, 2024), sorted.map { it.year })
    }

    @Test
    fun sortComparisonTooltipRows_ordersMissingValuesLastByYearDescending() {
        val rows = listOf(
            pastRow(year = 2025, value = 10.0f),
            pastRow(year = 2024, value = null),
            pastRow(year = 2026, value = 10.0f),
            pastRow(year = 2023, value = null),
        )

        val sorted = sortComparisonTooltipRows(rows)

        assertEquals(listOf(2026, 2025, 2024, 2023), sorted.map { it.year })
    }

    @Test
    fun buildComparisonTooltipYearLines_showsAllSixRowsWithoutSeparators() {
        val rows = (2002..2006).map { pastRow(it, compressedPastValue(it)) } + currentRow(value = 30.0f)

        val lines = buildComparisonTooltipYearLines(rows, HistoricalComparisonMetric.STORAGE_RATE) { "${it}年" }

        assertEquals(6, lines.size)
        assertEquals(0, lines.count { it == ":" })
        assertEquals("2026年: 30.00%", lines.first())
        assertEquals("2002年: --%", lines.last())
    }

    @Test
    fun compressComparisonTooltipRows_selectsTopAndWorstThreeYearsWithSeparators() {
        val rows = compressedPastRows() + currentRow(value = 10.5f)

        val compressed = compressComparisonTooltipRows(rows, currentYear = 2026)

        assertEquals(9, compressed.size)
        assertEquals(2, compressed.count { it == null })
        assertRow(compressed[0], year = 2024, value = 11.0f)
        assertRow(compressed[1], year = 2021, value = 11.0f)
        assertRow(compressed[2], year = 2018, value = 11.0f)
        assertNull(compressed[3])
        assertRow(compressed[4], year = 2026, value = 10.5f, isCurrentYear = true)
        assertNull(compressed[5])
        assertRow(compressed[6], year = 2010, value = 10.0f)
        assertRow(compressed[7], year = 2007, value = 10.0f)
        assertRow(compressed[8], year = 2004, value = 10.0f)
    }

    @Test
    fun compressComparisonTooltipRows_placesCurrentYearInTopWithSingleSeparator() {
        val rows = compressedPastRows() + currentRow(value = 11.5f)

        val compressed = compressComparisonTooltipRows(rows, currentYear = 2026)

        assertEquals(7, compressed.size)
        assertEquals(1, compressed.count { it == null })
        assertRow(compressed[0], year = 2026, value = 11.5f, isCurrentYear = true)
        assertRow(compressed[1], year = 2024, value = 11.0f)
        assertRow(compressed[2], year = 2021, value = 11.0f)
        assertNull(compressed[3])
        assertRow(compressed[4], year = 2010, value = 10.0f)
        assertRow(compressed[5], year = 2007, value = 10.0f)
        assertRow(compressed[6], year = 2004, value = 10.0f)
    }

    @Test
    fun compressComparisonTooltipRows_placesCurrentYearInWorstWithSingleSeparator() {
        val rows = compressedPastRows() + currentRow(value = 9.5f)

        val compressed = compressComparisonTooltipRows(rows, currentYear = 2026)

        assertEquals(7, compressed.size)
        assertEquals(1, compressed.count { it == null })
        assertRow(compressed[0], year = 2024, value = 11.0f)
        assertRow(compressed[1], year = 2021, value = 11.0f)
        assertRow(compressed[2], year = 2018, value = 11.0f)
        assertNull(compressed[3])
        assertRow(compressed[4], year = 2007, value = 10.0f)
        assertRow(compressed[5], year = 2004, value = 10.0f)
        assertRow(compressed[6], year = 2026, value = 9.5f, isCurrentYear = true)
    }

    @Test
    fun compressComparisonTooltipRows_keepsWorstDisjointFromTopWhenNumericYearsAreFew() {
        // 過去年2002..2004のうち数値を持つのは2003・2004の2年（2002は欠測）。今年も欠測のため数値年は計2年。
        val rows = (2002..2004).map { pastRow(it, compressedPastValue(it)) } + currentRow(value = null)

        val compressed = compressComparisonTooltipRows(rows, currentYear = 2026)

        // 全数値年がtopに含まれ、worstはtopと年が重複するため除外されて空になる（区切り2個で重複表示なし）。
        assertEquals(listOf(2003, 2004, null, 2026, null), compressed.map { it?.year })
        assertEquals(2, compressed.count { it == null })
    }

    @Test
    fun buildComparisonTooltipYearLines_compressesWhenNineOrMorePastYears() {
        val rows = compressedPastRows() + currentRow(value = 10.5f)

        val lines = buildComparisonTooltipYearLines(rows, HistoricalComparisonMetric.STORAGE_RATE) { "${it}年" }

        assertEquals(9, lines.size)
        assertEquals(2, lines.count { it == ":" })
        assertEquals("2024年: 11.00%", lines.first())
        assertEquals("2026年: 10.50%", lines[4])
        assertEquals("2004年: 10.00%", lines.last())
    }

    @Test
    fun buildComparisonTooltipYearLines_showsMissingCurrentYearRow() {
        val rows = compressedPastRows() + currentRow(value = null)

        val lines = buildComparisonTooltipYearLines(rows, HistoricalComparisonMetric.STORAGE_RATE) { "${it}年" }

        assertEquals(9, lines.size)
        assertEquals(2, lines.count { it == ":" })
        assertTrue("2026年: --%" in lines)
        assertEquals("2026年: --%", lines[4])
    }

    @Test
    fun buildComparisonTooltipYearLines_formatsRowWithJapaneseYearLabel() {
        val rows = listOf(currentRow(value = 54.10f))

        val lines = buildComparisonTooltipYearLines(rows, HistoricalComparisonMetric.STORAGE_RATE) { "${it}年" }

        assertEquals(listOf("2026年: 54.10%"), lines)
    }

    @Test
    fun buildComparisonTooltipYearLines_showsOnlyCurrentYearWhenAllLinesHidden() {
        // mode 3/4で全ラインを非表示にした場合、Card側は「今年のみ」の行リストを渡す。
        // 過去年の行が1つも無いときは区切りなしで今年の行だけが表示される。
        val rows = listOf(currentRow(value = null))

        val lines = buildComparisonTooltipYearLines(rows, HistoricalComparisonMetric.STORAGE_RATE) { "${it}年" }

        assertEquals(listOf("2026年: --%"), lines)
    }

    @Test
    fun formatComparisonTooltipValue_formatsStorageRateWithTwoDecimalPlaces() {
        assertEquals("54.10%", formatComparisonTooltipValue(54.10f, HistoricalComparisonMetric.STORAGE_RATE))
        assertEquals("0.00%", formatComparisonTooltipValue(0f, HistoricalComparisonMetric.STORAGE_RATE))
        assertEquals("100.00%", formatComparisonTooltipValue(100f, HistoricalComparisonMetric.STORAGE_RATE))
    }

    @Test
    fun formatComparisonTooltipValue_formatsStorageVolumeWithoutDecimalPlaces() {
        assertEquals("200000×10³m³", formatComparisonTooltipValue(200000f, HistoricalComparisonMetric.STORAGE_VOLUME))
        assertEquals("0×10³m³", formatComparisonTooltipValue(0f, HistoricalComparisonMetric.STORAGE_VOLUME))
    }

    @Test
    fun formatComparisonTooltipValue_returnsMetricSpecificMissingNotationForNull() {
        assertEquals("--%", formatComparisonTooltipValue(null, HistoricalComparisonMetric.STORAGE_RATE))
        assertEquals("--×10³m³", formatComparisonTooltipValue(null, HistoricalComparisonMetric.STORAGE_VOLUME))
    }

    private fun pastRow(year: Int, value: Float?): HistoricalComparisonTooltipRow =
        HistoricalComparisonTooltipRow(year = year, value = value)

    private fun currentRow(year: Int = 2026, value: Float?): HistoricalComparisonTooltipRow =
        HistoricalComparisonTooltipRow(year = year, value = value, isCurrentYear = true)

    /**
     * 過去年2002..2025を想定した圧縮テスト用の値。
     * `year % 3` が 0→10.0f, 1→10.5f, 2→11.0f。2002のみ欠測（null）。
     */
    private fun compressedPastValue(year: Int): Float? = when {
        year == 2002 -> null
        year % 3 == 0 -> 10.0f
        year % 3 == 1 -> 10.5f
        else -> 11.0f
    }

    private fun compressedPastRows(): List<HistoricalComparisonTooltipRow> =
        (2002..2025).map { pastRow(it, compressedPastValue(it)) }

    private fun assertRow(
        row: HistoricalComparisonTooltipRow?,
        year: Int,
        value: Float?,
        isCurrentYear: Boolean = false,
    ) {
        assertEquals(year, row?.year)
        assertEquals(value, row?.value)
        assertEquals(isCurrentYear, row?.isCurrentYear)
    }
}
