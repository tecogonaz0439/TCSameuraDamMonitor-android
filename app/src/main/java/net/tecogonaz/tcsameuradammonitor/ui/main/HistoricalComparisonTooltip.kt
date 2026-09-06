// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.ui.main

import net.tecogonaz.tcsameuradammonitor.domain.model.HistoricalComparisonMetric
import java.util.Locale

/**
 * 過去比較グラフのツールチップに表示する1年分の行データ。
 *
 * @property year 比較対象の年
 * @property value 該当年の貯水率（%）または貯水量（×10³m³）。欠測・異常の場合はnull
 * @property isCurrentYear 現在年（今年）の行であるかどうか
 */
data class HistoricalComparisonTooltipRow(
    val year: Int,
    val value: Float? = null,
    val isCurrentYear: Boolean = false
)

/**
 * 過去比較グラフのツールチップ行をWeb仕様の表示順にソートします。
 *
 * 並び順は値降順 → 同値は今年優先 → 年降順 → 欠測（null）は最後で年降順です。
 *
 * @param rows ソート対象の行リスト
 * @return ソート済みの行リスト
 */
fun sortComparisonTooltipRows(rows: List<HistoricalComparisonTooltipRow>): List<HistoricalComparisonTooltipRow> =
    rows.sortedWith(
        compareByDescending<HistoricalComparisonTooltipRow> { it.value }
            .thenByDescending { it.isCurrentYear }
            .thenByDescending { it.year }
    )

/**
 * 過去年の選択数が多い場合（9以上）のツールチップ行を圧縮します。
 *
 * 欠測（null）はソート済みリストから除外した上で、上位3年（top）と下位3年（worst）を抽出します。
 * worstは末尾3年からtopに含まれる年を除外します（重複排除）。
 * 今年がtopまたはworstに含まれる場合は [top, 区切り, worst]、含まれない場合は [top, 区切り, 今年, 区切り, worst] を返します。
 * 今年の行がrowsに無い場合は、value=null の今年の行を補います。
 * 区切りは null 要素で表され、表示時に「:」へ変換されます。
 *
 * @param rows ソート対象の行リスト
 * @param currentYear 現在年
 * @return 圧縮後の行リスト。null 要素は区切りを表す
 */
fun compressComparisonTooltipRows(
    rows: List<HistoricalComparisonTooltipRow>,
    currentYear: Int
): List<HistoricalComparisonTooltipRow?> {
    val numeric = sortComparisonTooltipRows(rows.filter { it.value != null })
    val top = numeric.take(3)
    val topYears = top.mapTo(mutableSetOf()) { it.year }
    val worst = numeric.takeLast(3).filter { it.year !in topYears }
    val currentYearRow = rows.firstOrNull { it.isCurrentYear }
        ?: HistoricalComparisonTooltipRow(year = currentYear, value = null, isCurrentYear = true)
    val currentYearInTopOrWorst = (top + worst).any { it.year == currentYear }
    return if (currentYearInTopOrWorst) {
        buildList {
            addAll(top)
            add(null)
            addAll(worst)
        }
    } else {
        buildList {
            addAll(top)
            add(null)
            add(currentYearRow)
            add(null)
            addAll(worst)
        }
    }
}

/**
 * 過去比較のツールチップに表示する値をフォーマットします。
 *
 * STORAGE_RATEは "%.2f%%"（[Locale.US]）、STORAGE_VOLUMEは "%.0f×10³m³" の形式です。
 * 欠測（null）はSTORAGE_RATEなら "--%"、STORAGE_VOLUMEなら "--×10³m³" を返します。
 *
 * @param value フォーマット対象の値
 * @param metric 比較対象のダム諸量
 * @return フォーマット済みの文字列
 */
fun formatComparisonTooltipValue(value: Float?, metric: HistoricalComparisonMetric): String {
    if (value == null) {
        return when (metric) {
            HistoricalComparisonMetric.STORAGE_RATE -> "--%"
            HistoricalComparisonMetric.STORAGE_VOLUME -> "--×10³m³"
        }
    }
    return when (metric) {
        HistoricalComparisonMetric.STORAGE_RATE -> String.format(Locale.US, "%.2f%%", value)
        HistoricalComparisonMetric.STORAGE_VOLUME -> String.format(Locale.US, "%.0f×10³m³", value)
    }
}

/**
 * 過去比較ツールチップの年別行全体を組み立てます。
 *
 * 1行目は日時（呼び出し側で付与）のため、ここでは年別の行と区切り行のみを返します。
 * 過去年の選択数（rows内の!isCurrentYearの数）が8以下なら全行をソート表示し、
 * 9以上なら[compressComparisonTooltipRows]で圧縮表示します。
 * 行フォーマットは "${yearLabel(row.year)}: ${formatComparisonTooltipValue(...)}"、
 * 区切り行は ":" です。
 *
 * @param rows 年別の行データ（現在年 + 選択中かつ線が表示されている過去年）
 * @param metric 比較対象のダム諸量
 * @param yearLabel 年の表示ラベルを返す関数（例: 日本語ロケールでは "${it}年"）
 * @return ツールチップの年別行（区切りを含む）のリスト
 */
fun buildComparisonTooltipYearLines(
    rows: List<HistoricalComparisonTooltipRow>,
    metric: HistoricalComparisonMetric,
    yearLabel: (Int) -> String
): List<String> {
    val pastCount = rows.count { !it.isCurrentYear }
    if (pastCount <= 8) {
        return sortComparisonTooltipRows(rows).map { row ->
            "${yearLabel(row.year)}: ${formatComparisonTooltipValue(row.value, metric)}"
        }
    }
    val currentYear = rows.firstOrNull { it.isCurrentYear }?.year
    val compressed: List<HistoricalComparisonTooltipRow?> = if (currentYear == null) {
        sortComparisonTooltipRows(rows)
    } else {
        compressComparisonTooltipRows(rows, currentYear)
    }
    return compressed.map { row ->
        if (row == null) {
            ":"
        } else {
            "${yearLabel(row.year)}: ${formatComparisonTooltipValue(row.value, metric)}"
        }
    }
}
