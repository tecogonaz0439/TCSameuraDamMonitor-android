// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.ui.main

import androidx.compose.ui.graphics.Color
import net.tecogonaz.tcsameuradammonitor.domain.model.Trend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Drawerの選択状態を考慮したトレンド強調色を検証するユニットテスト。 */
class DrawerTrendAccentColorTest {
    @Test
    fun drawerTrendAccentColor_suppressesAccentColorWhileRealtimeItemIsSelected() {
        Trend.entries.forEach { trend ->
            assertNull(drawerTrendAccentColor(trend = trend, isSelected = true))
        }
        assertNull(drawerTrendAccentColor(trend = null, isSelected = true))
    }

    @Test
    fun drawerTrendAccentColor_returnsFixedColorsWhileRealtimeItemIsNotSelected() {
        assertEquals(Color.Red, drawerTrendAccentColor(trend = Trend.UP, isSelected = false))
        assertEquals(Color.Blue, drawerTrendAccentColor(trend = Trend.DOWN, isSelected = false))
        assertNull(drawerTrendAccentColor(trend = Trend.FLAT, isSelected = false))
        assertNull(drawerTrendAccentColor(trend = Trend.UNKNOWN, isSelected = false))
        assertNull(drawerTrendAccentColor(trend = null, isSelected = false))
    }
}
