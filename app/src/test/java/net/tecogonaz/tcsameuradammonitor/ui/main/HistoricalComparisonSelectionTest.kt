// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.ui.main

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 選択年集合（過去年+今年）を管理する関数群（トグル・全選択判定）を検証するユニットテスト。 */
class HistoricalComparisonSelectionTest {

    private val availableYears = listOf(2002, 2003, 2004, 2005)

    /** 今年（2026年）を含む全対象年。 */
    private val availableYearsWithCurrentYear = (2002..2026).toList()

    @Test
    fun togglePastYearSelection_addsYearWhenNotSelected() {
        assertEquals(setOf(2003), togglePastYearSelection(selected = emptySet(), year = 2003))
    }

    @Test
    fun togglePastYearSelection_addsYearToNonEmptySet() {
        assertEquals(setOf(2002, 2005), togglePastYearSelection(selected = setOf(2002), year = 2005))
    }

    @Test
    fun togglePastYearSelection_removesYearWhenSelected() {
        assertEquals(setOf(2002), togglePastYearSelection(selected = setOf(2002, 2003), year = 2003))
    }

    @Test
    fun toggleAllPastYears_clearsAllWhenAllSelected() {
        assertEquals(
            emptySet<Int>(),
            toggleAllPastYears(selected = availableYears.toSet(), availableYears = availableYears)
        )
    }

    @Test
    fun toggleAllPastYears_selectsAllWhenPartiallySelected() {
        assertEquals(
            availableYears.toSet(),
            toggleAllPastYears(selected = setOf(2002), availableYears = availableYears)
        )
    }

    @Test
    fun toggleAllPastYears_selectsAllWhenNothingSelected() {
        assertEquals(
            availableYears.toSet(),
            toggleAllPastYears(selected = emptySet(), availableYears = availableYears)
        )
    }

    @Test
    fun isAllPastYearsSelected_returnsFalseWhenNoYearsAvailable() {
        assertFalse(isAllPastYearsSelected(selected = emptySet(), availableYears = emptyList()))
        assertFalse(isAllPastYearsSelected(selected = setOf(2002), availableYears = emptyList()))
    }

    @Test
    fun isAllPastYearsSelected_returnsFalseWhenPartiallySelected() {
        assertFalse(isAllPastYearsSelected(selected = emptySet(), availableYears = availableYears))
        assertFalse(isAllPastYearsSelected(selected = setOf(2002), availableYears = availableYears))
        assertFalse(
            isAllPastYearsSelected(selected = setOf(2002, 2003, 2004), availableYears = availableYears)
        )
    }

    @Test
    fun isAllPastYearsSelected_returnsTrueWhenAllYearsSelected() {
        assertTrue(isAllPastYearsSelected(selected = availableYears.toSet(), availableYears = availableYears))
    }

    @Test
    fun toggleAllPastYears_clearsAllIncludingCurrentYearWhenAllSelected() {
        assertEquals(
            emptySet<Int>(),
            toggleAllPastYears(
                selected = availableYearsWithCurrentYear.toSet(),
                availableYears = availableYearsWithCurrentYear
            )
        )
    }

    @Test
    fun toggleAllPastYears_selectsAllIncludingCurrentYearWhenPartiallySelected() {
        assertEquals(
            availableYearsWithCurrentYear.toSet(),
            toggleAllPastYears(
                selected = setOf(2002, 2026),
                availableYears = availableYearsWithCurrentYear
            )
        )
    }

    @Test
    fun toggleAllPastYears_selectsAllIncludingCurrentYearWhenNothingSelected() {
        assertEquals(
            availableYearsWithCurrentYear.toSet(),
            toggleAllPastYears(selected = emptySet(), availableYears = availableYearsWithCurrentYear)
        )
    }

    @Test
    fun togglePastYearSelection_togglesCurrentYearIndividually() {
        assertEquals(
            setOf(2002, 2026),
            togglePastYearSelection(selected = setOf(2002), year = 2026)
        )
        assertEquals(
            setOf(2002),
            togglePastYearSelection(selected = setOf(2002, 2026), year = 2026)
        )
    }

    @Test
    fun isAllPastYearsSelected_returnsFalseWhenCurrentYearNotSelected() {
        assertFalse(
            isAllPastYearsSelected(
                selected = (2002..2025).toSet(),
                availableYears = availableYearsWithCurrentYear
            )
        )
    }

    @Test
    fun isAllPastYearsSelected_returnsTrueOnlyWhenAllYearsIncludingCurrentYearSelected() {
        assertTrue(
            isAllPastYearsSelected(
                selected = availableYearsWithCurrentYear.toSet(),
                availableYears = availableYearsWithCurrentYear
            )
        )
    }
}
