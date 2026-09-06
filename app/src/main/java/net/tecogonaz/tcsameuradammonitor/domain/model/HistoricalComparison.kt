// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.domain.model

/**
 * 過去比較グラフで比較するダム諸量の種類を表す列挙型。
 *
 * @property STORAGE_RATE 貯水率（%）を比較対象とする
 * @property STORAGE_VOLUME 貯水量（×10³m³）を比較対象とする
 */
enum class HistoricalComparisonMetric {
    STORAGE_RATE,
    STORAGE_VOLUME,
}

/**
 * 過去比較グラフの元となる単一の観測時点データ。
 *
 * バンドルassets内のdatファイルから射影され、両メトリック（貯水率・貯水量）の値を
 * まとめて保持する。値がnullの場合は欠測・異常値（正常品質以外）を表す。
 *
 * @property epochMillis 観測日時のJST基準エポックミリ秒（24:00表記は翌日00:00へ正規化済み）
 * @property storageRate 貯水率（%）。欠測・異常時はnull
 * @property storageVolume 貯水量（×10³m³）。欠測・異常時はnull
 */
data class HistoricalComparisonPoint(
    val epochMillis: Long,
    val storageRate: Float? = null,
    val storageVolume: Float? = null,
)

/**
 * 過去比較グラフの1年分の時系列データ。
 *
 * @property year 比較対象の過去年（例: 2024）
 * @property values 貯水率または貯水量の時系列値。[HistoricalComparisonData.hourlyAxisMillis]と
 *   パラレルなリストで、該当する時刻の観測が存在しない・欠測・異常の場合はnullが入る
 *   （正常品質の値のみが保持される）
 */
data class HistoricalComparisonSeries(
    val year: Int,
    val values: List<Float?>,
)

/**
 * 過去比較グラフの描画に必要な全体データ。
 *
 * 主系列年と比較対象年群の各時系列を、共通の1時間刻み時刻軸（JST）に揃えて保持する。
 *
 * @property currentYear 主系列年。リアルタイム・過去データ(日次)表示ではJST現在年、
 *   通常の過去データ表示では表示対象データの年
 * @property availablePastYears 比較対象年の昇順リスト。リアルタイム・日次表示では
 *   2002〜JST現在年-1、通常の過去データ表示では2002〜JST現在年から主系列年を除いた年
 * @property periodStartMillis 比較期間の開始エポックミリ秒（含む）
 * @property periodEndMillis 比較期間の終了エポックミリ秒（含む）
 * @property hourlyAxisMillis 1時間刻みの時刻軸（JST、両端含む、昇順）
 * @property series 比較対象年の昇順に並んだ各年の時系列。値は[hourlyAxisMillis]とパラレル。
 *   通常の過去データ表示ではJST現在年の系列がバンドル+読込済み日次過去データの合成になる
 */
data class HistoricalComparisonData(
    val currentYear: Int,
    val availablePastYears: List<Int>,
    val periodStartMillis: Long,
    val periodEndMillis: Long,
    val hourlyAxisMillis: List<Long>,
    val series: List<HistoricalComparisonSeries>,
)
