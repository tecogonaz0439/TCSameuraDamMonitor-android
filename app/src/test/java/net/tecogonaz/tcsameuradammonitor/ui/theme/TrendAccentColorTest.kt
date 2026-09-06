// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.ui.theme

import androidx.compose.ui.graphics.Color
import net.tecogonaz.tcsameuradammonitor.domain.model.Trend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** トレンドに対応する固定強調色を検証するユニットテスト。 */
class TrendAccentColorTest {
    @Test
    fun trendAccentColor_returnsFixedRedForUpTrend() {
        assertEquals(Color.Red, trendAccentColor(Trend.UP))
    }

    @Test
    fun trendAccentColor_returnsFixedBlueForDownTrend() {
        assertEquals(Color.Blue, trendAccentColor(Trend.DOWN))
    }

    @Test
    fun trendAccentColor_returnsNullForTrendsWithoutAccentColor() {
        assertNull(trendAccentColor(Trend.FLAT))
        assertNull(trendAccentColor(Trend.UNKNOWN))
        assertNull(trendAccentColor(null))
    }
}
