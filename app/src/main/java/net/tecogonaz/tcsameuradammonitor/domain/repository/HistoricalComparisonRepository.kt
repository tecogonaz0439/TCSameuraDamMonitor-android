// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.domain.repository

import arrow.core.Either
import net.tecogonaz.tcsameuradammonitor.domain.model.HistoricalComparisonData
import net.tecogonaz.tcsameuradammonitor.domain.model.HistoricalComparisonMetric

/**
 * 過去比較グラフ用のダム諸量データを提供するリポジトリ。
 *
 * 早明浦ダム限定の機能であり、バンドルされているassets内の過去datファイルを基本ソースとして読み込む。
 * 主系列年指定時（通常の過去データ表示）のみ、JST現在年の比較系列の合成に読込済み日次過去データ
 * （sudmonitor）も参照する。現在年の算出にのみ時計（JST）を使用する。
 */
interface HistoricalComparisonRepository {

    /**
     * 指定された期間の過去比較データを取得します。
     *
     * 主系列年（[mainYear]、未指定時はJST現在年）を基準に、比較可能な各年について同一期間
     * （月日・時刻）のデータをバンドルassetsから読み込み、1時間刻みの時刻軸に揃えて返します。
     * [mainYear]指定時はJST現在年も比較対象年に含まれ、JST現在年の系列はバンドルデータと
     * 読込済み日次過去データ（sudmonitor）の合成になります（日次がバンドルより優先。
     * いずれにも無い期間はnull=ライン途切れ）。
     * 一部の年（JST現在年以外）にデータが無い場合はその年の値が全てnullになりますが、
     * 読み込み自体が失敗した場合は全体を失敗（[Either.Left]）として扱います。
     *
     * @param damId 対象ダムの観測所ID。早明浦以外（[net.tecogonaz.tcsameuradammonitor.domain.model.AppSettings.DEFAULT_DAM_ID]以外）は拒否する
     * @param metric 比較対象のダム諸量（貯水率または貯水量）
     * @param windowStartMillis 比較期間の開始エポックミリ秒（含む）
     * @param windowEndMillis 比較期間の終了エポックミリ秒（含む）
     * @param mainYear 主系列年。通常の過去データ表示では表示対象データの年を指定し、
     * リアルタイム・過去データ(日次)表示ではnull（主系列年=JST現在年の従来挙動）
     * @return 成功時は[HistoricalComparisonData]、失敗時はエラーを包んだ[Either]
     */
    suspend fun loadComparison(
        damId: String,
        metric: HistoricalComparisonMetric,
        windowStartMillis: Long,
        windowEndMillis: Long,
        mainYear: Int? = null
    ): Either<Throwable, HistoricalComparisonData>
}
