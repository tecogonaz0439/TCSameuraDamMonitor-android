// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.widget

import net.tecogonaz.tcsameuradammonitor.domain.model.Trend
import java.util.Locale

/**
 * ホーム画面ウィジェットの表示テキストの整形ユーティリティオブジェクト。
 *
 * 貯水率の変動傾向（トレンド）を示す矢印記号への変換、日時文字列の結合処理、テキスト幅の推定などを担当します。
 */
internal object WidgetDisplayFormatter {
    /**
     * 貯水率の変動傾向（トレンド）をウィジェット表示用の矢印記号に変換します。
     *
     * @param trend 貯水率の変動傾向（[Trend]）
     * @return 変動傾向に対応する矢印記号（"↗"、"↘"、"→"）。未定義または null の場合は空文字。
     */
    fun trendText(trend: Trend?) = when (trend) {
        Trend.UP -> "↗"
        Trend.DOWN -> "↘"
        Trend.FLAT -> "→"
        else -> ""
    }

    /**
     * 指定されたテキストを指定されたフォントサイズで描画した際、指定幅を超過（はみ出し）するかどうかを推定します。
     *
     * @param text 判定対象の文字列
     * @param contentWidthDp ウィジェットのコンテンツ有効幅（dp単位）
     * @param fontSizeSp 描画時のフォントサイズ（sp単位）
     * @return はみ出すと推定される場合は true、収まる場合は false
     */
    fun isTextOverflowEstimated(text: String, contentWidthDp: Float, fontSizeSp: Float): Boolean =
        estimateTextWidthSp(text) * fontSizeSp > contentWidthDp

    /**
     * 日付文字列と時刻文字列を結合し、ウィジェット表示用の日時接頭辞を作成します。
     *
     * @param dateFormatted フォーマットされた日付文字列（例: "2026/06/01"）
     * @param timeFormatted フォーマットされた時刻文字列（例: "12:00 JST"）
     * @return 結合された日時文字列。前後の不要な空白はトリムされます。
     */
    fun buildDateTimePrefix(dateFormatted: String, timeFormatted: String) = buildString {
        if (dateFormatted.isNotEmpty()) append("$dateFormatted ")
        if (timeFormatted.isNotEmpty()) append(timeFormatted)
    }.trim()

    /**
     * 貯水率とトレンド記号を1行表示用の文字列へ整形します。
     *
     * @param percentage 貯水率。nullの場合は欠測表示を使用します。
     * @param trend 貯水率の変動傾向
     * @param locale 数値書式に使用するロケール
     * @param missingPercentageText 欠測時の表示文字列
     */
    fun percentageAndTrendText(
        percentage: Float?,
        trend: Trend?,
        locale: Locale,
        missingPercentageText: String
    ): String {
        val percentageText = percentage?.let { String.format(locale, "%.2f%%", it) }
            ?: missingPercentageText
        val trendText = if (percentage != null) trendText(trend) else ""
        return "$percentageText $trendText".trim()
    }

    /**
     * 貯水率前日比とトレンド記号を1行表示用の文字列へ整形します。
     *
     * @param label ローカライズ済みの前日比ラベル
     * @param dayChange 貯水率前日比。nullの場合は欠測表示を使用します。
     * @param trend 前日比の変動傾向
     * @param locale 数値書式に使用するロケール
     */
    fun dayChangeText(
        label: String,
        dayChange: Float?,
        trend: Trend?,
        locale: Locale
    ): String {
        val valueText = dayChange?.let { String.format(locale, "%+.2f%%", it) } ?: "-- %"
        val trendText = if (dayChange != null) trendText(trend) else ""
        return listOf(label, valueText, trendText)
            .filter { it.isNotEmpty() }
            .joinToString(" ")
    }

    /**
     * テキスト全体の推定横幅（sp基準）を算出します。
     *
     * @param text 対象のテキスト
     * @return 推定されるテキストの総横幅
     */
    private fun estimateTextWidthSp(text: String): Float =
        text.sumOf { char -> estimateCharacterWidthSp(char).toDouble() }.toFloat()

    /**
     * 文字ごとの推定横幅比率（全角文字を 1.0 とした相対値）を返します。
     *
     * @param char 対象の文字
     * @return 推定される文字幅の比率
     */
    private fun estimateCharacterWidthSp(char: Char): Float = when {
        char.isWhitespace() -> 0.35f
        char.code < 0x80 && (char.isDigit() || char in "%./:()[]+-_") -> 0.6f
        char.code < 0x80 -> 0.55f
        else -> 1.0f
    }
}
