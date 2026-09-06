// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.widget

import net.tecogonaz.tcsameuradammonitor.domain.model.Trend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * ホーム画面のウィジェットに表示する文字列の整形処理を行う [WidgetDisplayFormatter] のユニットテストクラス。
 * トレンド記号の変換、日付と時刻のプレフィックス構築、およびテキストが
 * 領域からはみ出る（Overflow）推定ロジックが文字種（英数字、記号、日本語、スペース）ごとに
 * 正しく動作することを検証します。
 */
class WidgetDisplayFormatterTest {
    @Test
    fun trendText_returnsUpSymbol() {
        assertEquals("↗", WidgetDisplayFormatter.trendText(Trend.UP))
    }

    @Test
    fun trendText_returnsDownSymbol() {
        assertEquals("↘", WidgetDisplayFormatter.trendText(Trend.DOWN))
    }

    @Test
    fun trendText_returnsFlatSymbol() {
        assertEquals("→", WidgetDisplayFormatter.trendText(Trend.FLAT))
    }

    @Test
    fun trendText_returnsEmptyForUnknownTrend() {
        assertEquals("", WidgetDisplayFormatter.trendText(Trend.UNKNOWN))
    }

    @Test
    fun trendText_returnsEmptyForNullTrend() {
        assertEquals("", WidgetDisplayFormatter.trendText(null))
    }

    @Test
    fun buildDateTimePrefix_joinsDateAndTime() {
        assertEquals("2026/05/16 05:15", WidgetDisplayFormatter.buildDateTimePrefix("2026/05/16", "05:15"))
    }

    @Test
    fun buildDateTimePrefix_usesDateOnlyWhenTimeIsMissing() {
        assertEquals("2026/05/16", WidgetDisplayFormatter.buildDateTimePrefix("2026/05/16", ""))
    }

    @Test
    fun buildDateTimePrefix_usesTimeOnlyWhenDateIsMissing() {
        assertEquals("05:15", WidgetDisplayFormatter.buildDateTimePrefix("", "05:15"))
    }

    @Test
    fun buildDateTimePrefix_returnsEmptyWhenDateAndTimeAreMissing() {
        assertEquals("", WidgetDisplayFormatter.buildDateTimePrefix("", ""))
    }

    @Test
    fun percentageAndTrendText_withValue_formatsPercentageAndTrend() {
        assertEquals(
            "80.00% ↗",
            WidgetDisplayFormatter.percentageAndTrendText(
                percentage = 80f,
                trend = Trend.UP,
                locale = Locale.ENGLISH,
                missingPercentageText = "-- %"
            )
        )
    }

    @Test
    fun percentageAndTrendText_withoutValue_returnsPlaceholderWithoutTrend() {
        assertEquals(
            "-- %",
            WidgetDisplayFormatter.percentageAndTrendText(
                percentage = null,
                trend = Trend.UP,
                locale = Locale.ENGLISH,
                missingPercentageText = "-- %"
            )
        )
    }

    @Test
    fun dayChangeText_withValue_formatsLocalizedLabelValueAndTrend() {
        assertEquals(
            "Day-over-day +1.25% ↘",
            WidgetDisplayFormatter.dayChangeText(
                label = "Day-over-day",
                dayChange = 1.25f,
                trend = Trend.DOWN,
                locale = Locale.ENGLISH
            )
        )
    }

    @Test
    fun dayChangeText_withoutValue_returnsLocalizedPlaceholder() {
        assertEquals(
            "前日比 -- %",
            WidgetDisplayFormatter.dayChangeText(
                label = "前日比",
                dayChange = null,
                trend = Trend.UP,
                locale = Locale.JAPANESE
            )
        )
    }

    @Test
    fun isTextOverflowEstimated_returnsFalseWhenAsciiTextFits() {
        
        assertFalse(WidgetDisplayFormatter.isTextOverflowEstimated("80.00%", 100f, 12f))
    }

    @Test
    fun isTextOverflowEstimated_returnsTrueWhenJapaneseTextIsWiderThanContent() {
        
        assertTrue(WidgetDisplayFormatter.isTextOverflowEstimated("早明浦ダム貯水率モニタ", 80f, 16f))
    }

    @Test
    fun isTextOverflowEstimated_returnsFalseWhenEstimatedWidthEqualsContentWidth() {
        assertFalse(WidgetDisplayFormatter.isTextOverflowEstimated("80.00%", 43.2f, 12f))
    }

    @Test
    fun isTextOverflowEstimated_accountsForWhitespaceBucket() {
        
        assertFalse(WidgetDisplayFormatter.isTextOverflowEstimated("    ", 14f, 10f))
        assertTrue(WidgetDisplayFormatter.isTextOverflowEstimated("    ", 13.9f, 10f))
    }

    @Test
    fun isTextOverflowEstimated_accountsForAsciiSymbolBucket() {
        
        assertFalse(WidgetDisplayFormatter.isTextOverflowEstimated("%/+-", 24f, 10f))
        assertTrue(WidgetDisplayFormatter.isTextOverflowEstimated("%/+-", 23.9f, 10f))
    }

    @Test
    fun isTextOverflowEstimated_accountsForAsciiLetterBucket() {
        
        assertFalse(WidgetDisplayFormatter.isTextOverflowEstimated("DamX", 22f, 10f))
        assertTrue(WidgetDisplayFormatter.isTextOverflowEstimated("DamX", 21.9f, 10f))
    }

    @Test
    fun isTextOverflowEstimated_accountsForUnicodeBucket() {
        
        assertFalse(WidgetDisplayFormatter.isTextOverflowEstimated("早明浦川", 40f, 10f))
        assertTrue(WidgetDisplayFormatter.isTextOverflowEstimated("早明浦川", 39.9f, 10f))
    }
}
