// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.ui.main

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 主系列(今年のライン)の描画判定関数 [isCurrentYearLineVisible] を検証するユニットテスト。 */
class GraphLineVisibilityTest {

    private val allLines = GraphLine.entries.toSet()

    @Test
    fun nonComparisonMode_returnsTrueWhenMainLineSelected() {
        assertTrue(
            isCurrentYearLineVisible(
                isComparisonMode = false,
                mainLine = GraphLine.STORAGE_RATE,
                visibleLines = allLines,
                comparisonCurrentYear = null,
                selectedYears = emptySet()
            )
        )
        assertTrue(
            isCurrentYearLineVisible(
                isComparisonMode = false,
                mainLine = GraphLine.STORAGE_VOLUME,
                visibleLines = setOf(GraphLine.STORAGE_VOLUME, GraphLine.INFLOW, GraphLine.OUTFLOW),
                comparisonCurrentYear = null,
                selectedYears = emptySet()
            )
        )
    }

    @Test
    fun nonComparisonMode_returnsFalseWhenMainLineChipDeselected() {
        assertFalse(
            isCurrentYearLineVisible(
                isComparisonMode = false,
                mainLine = GraphLine.STORAGE_RATE,
                visibleLines = allLines - GraphLine.STORAGE_RATE,
                comparisonCurrentYear = null,
                selectedYears = emptySet()
            )
        )
        assertFalse(
            isCurrentYearLineVisible(
                isComparisonMode = false,
                mainLine = GraphLine.STORAGE_VOLUME,
                visibleLines = setOf(GraphLine.INFLOW, GraphLine.OUTFLOW),
                comparisonCurrentYear = null,
                selectedYears = emptySet()
            )
        )
    }

    @Test
    fun nonComparisonMode_mainLineSelectionIndependentOfYearSelection() {
        assertTrue(
            isCurrentYearLineVisible(
                isComparisonMode = false,
                mainLine = GraphLine.STORAGE_RATE,
                visibleLines = setOf(GraphLine.STORAGE_RATE),
                comparisonCurrentYear = 2026,
                selectedYears = emptySet()
            )
        )
        assertFalse(
            isCurrentYearLineVisible(
                isComparisonMode = false,
                mainLine = GraphLine.STORAGE_RATE,
                visibleLines = setOf(GraphLine.RAINFALL),
                comparisonCurrentYear = 2026,
                selectedYears = setOf(2026)
            )
        )
    }

    @Test
    fun comparisonMode_returnsTrueWhenCurrentYearSelected() {
        assertTrue(
            isCurrentYearLineVisible(
                isComparisonMode = true,
                mainLine = GraphLine.STORAGE_RATE,
                visibleLines = allLines - GraphLine.STORAGE_RATE,
                comparisonCurrentYear = 2026,
                selectedYears = setOf(2026)
            )
        )
    }

    @Test
    fun comparisonMode_returnsFalseWhenCurrentYearNotSelected() {
        assertFalse(
            isCurrentYearLineVisible(
                isComparisonMode = true,
                mainLine = GraphLine.STORAGE_RATE,
                visibleLines = setOf(GraphLine.STORAGE_RATE),
                comparisonCurrentYear = 2026,
                selectedYears = (2002..2025).toSet()
            )
        )
    }

    @Test
    fun comparisonMode_returnsFalseWhenComparisonDataNotLoaded() {
        // 比較データ非ロード時は今年がnullのため、年選択状態に関わらず非表示。
        assertFalse(
            isCurrentYearLineVisible(
                isComparisonMode = true,
                mainLine = GraphLine.STORAGE_VOLUME,
                visibleLines = allLines,
                comparisonCurrentYear = null,
                selectedYears = emptySet()
            )
        )
        assertFalse(
            isCurrentYearLineVisible(
                isComparisonMode = true,
                mainLine = GraphLine.STORAGE_VOLUME,
                visibleLines = allLines,
                comparisonCurrentYear = null,
                selectedYears = setOf(2026)
            )
        )
    }

    @Test
    fun nonComparisonMode_markerVisibilityFollowsEachLineChip() {
        val visible = allLines
        assertTrue(
            isGraphLineVisible(
                line = GraphLine.STORAGE_RATE,
                isComparisonMode = false,
                mainLine = GraphLine.STORAGE_RATE,
                visibleLines = visible,
                comparisonCurrentYear = null,
                selectedYears = emptySet()
            )
        )
        assertTrue(
            isGraphLineVisible(
                line = GraphLine.RAINFALL,
                isComparisonMode = false,
                mainLine = GraphLine.STORAGE_RATE,
                visibleLines = visible,
                comparisonCurrentYear = null,
                selectedYears = emptySet()
            )
        )
        assertFalse(
            isGraphLineVisible(
                line = GraphLine.RAINFALL,
                isComparisonMode = false,
                mainLine = GraphLine.STORAGE_RATE,
                visibleLines = visible - GraphLine.RAINFALL,
                comparisonCurrentYear = null,
                selectedYears = emptySet()
            )
        )
        assertFalse(
            isGraphLineVisible(
                line = GraphLine.STORAGE_VOLUME,
                isComparisonMode = false,
                mainLine = GraphLine.STORAGE_VOLUME,
                visibleLines = visible - GraphLine.STORAGE_VOLUME,
                comparisonCurrentYear = null,
                selectedYears = emptySet()
            )
        )
        assertTrue(
            isGraphLineVisible(
                line = GraphLine.INFLOW,
                isComparisonMode = false,
                mainLine = GraphLine.STORAGE_VOLUME,
                visibleLines = visible - GraphLine.STORAGE_VOLUME,
                comparisonCurrentYear = null,
                selectedYears = emptySet()
            )
        )
        assertFalse(
            isGraphLineVisible(
                line = GraphLine.OUTFLOW,
                isComparisonMode = false,
                mainLine = GraphLine.STORAGE_VOLUME,
                visibleLines = visible - GraphLine.OUTFLOW,
                comparisonCurrentYear = null,
                selectedYears = emptySet()
            )
        )
    }

    @Test
    fun comparisonMode_mainMarkerFollowsCurrentYearAndSecondaryMarkersFollowLineChips() {
        val visible = allLines
        assertTrue(
            isGraphLineVisible(
                line = GraphLine.STORAGE_RATE,
                isComparisonMode = true,
                mainLine = GraphLine.STORAGE_RATE,
                visibleLines = emptySet(),
                comparisonCurrentYear = 2026,
                selectedYears = setOf(2026)
            )
        )
        assertFalse(
            isGraphLineVisible(
                line = GraphLine.STORAGE_RATE,
                isComparisonMode = true,
                mainLine = GraphLine.STORAGE_RATE,
                visibleLines = visible,
                comparisonCurrentYear = 2026,
                selectedYears = setOf(2025)
            )
        )
        assertTrue(
            isGraphLineVisible(
                line = GraphLine.RAINFALL,
                isComparisonMode = true,
                mainLine = GraphLine.STORAGE_RATE,
                visibleLines = visible,
                comparisonCurrentYear = 2026,
                selectedYears = setOf(2025)
            )
        )
        assertFalse(
            isGraphLineVisible(
                line = GraphLine.RAINFALL,
                isComparisonMode = true,
                mainLine = GraphLine.STORAGE_RATE,
                visibleLines = visible - GraphLine.RAINFALL,
                comparisonCurrentYear = 2026,
                selectedYears = setOf(2026)
            )
        )
        assertFalse(
            isGraphLineVisible(
                line = GraphLine.OUTFLOW,
                isComparisonMode = true,
                mainLine = GraphLine.STORAGE_VOLUME,
                visibleLines = visible - GraphLine.OUTFLOW,
                comparisonCurrentYear = 2026,
                selectedYears = setOf(2026)
            )
        )
    }
}
