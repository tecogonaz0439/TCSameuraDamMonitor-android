// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.domain.repository

import arrow.core.Either
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import net.tecogonaz.tcsameuradammonitor.domain.model.AutoUpdateInterval
import net.tecogonaz.tcsameuradammonitor.domain.model.DamConfig
import net.tecogonaz.tcsameuradammonitor.domain.model.DamHistoricalData
import net.tecogonaz.tcsameuradammonitor.domain.model.SudmonitorHistory


/**
 * sudmonitor の日次過去データ（`/v1/history/{damId}/latest.dat`、直近31暦日・毎日00:12 JST上書き）の
 * 取得・保存・読出しを担当するリポジトリ。
 *
 * 機能ゲート（[net.tecogonaz.tcsameuradammonitor.domain.model.AppSettings.historicalDataSource] が
 * `RealtimeDataSource.SUDMONITOR`）が無効な間は、全経路（自動取得・手動更新・表示）を無効化する。
 * 保存は既存の過去データ検索テーブル（`historical_search_meta` / `historical_dam_data`）とは独立の
 * 専用テーブル（`sudmonitor_history` / `sudmonitor_history_observation`）へ行う。
 */
interface SudmonitorHistoryRepository {

    /**
     * 日次過去データを取得し、専用テーブルへ保存します（ダムごと1件の上書き）。
     *
     * 取得は [net.tecogonaz.tcsameuradammonitor.data.source.remote.SudmonitorHistoricalClient.fetchLatest]
     * をそのまま使用し、404（未蓄積）・機能ゲート無効時は [SudmonitorHistoryFetchResult.NotStored] を返します。
     * 保存は観測明細の置換を含めて all-or-nothing で実行します。
     *
     * @param damId 対象ダムの観測所ID
     * @param damConfig 対象ダムの静的設定（パース時のダムID照合に使用）
     * @param trigger 取得トリガー（初回起動 / ダム変更 / 手動 / 自動）
     * @return 保存結果。404（未蓄積）・ゲート無効時は [SudmonitorHistoryFetchResult.NotStored]、
     *         5xx・タイムアウト・`X-TCS-Dam-Id` 不一致・パース失敗は [Either.Left]
     */
    suspend fun fetchAndStore(
        damId: String,
        damConfig: DamConfig,
        trigger: SudmonitorHistoryTrigger
    ): Either<Throwable, SudmonitorHistoryFetchResult>

    /**
     * 対象ダムの日次過去データ保存行を取得します。
     *
     * @param damId 対象ダムの観測所ID
     * @return 保存行、未保存の場合は null
     */
    suspend fun findByDamId(damId: String): SudmonitorHistory?

    /**
     * 対象ダムの日次過去データ保存行をリアクティブに監視する。
     *
     * 手動・自動更新の保存、デバッグセッションの待避・復元など保存行への書込があるたびに新しい行が再emitされる。
     * UI層はサイドバー常設エントリ・サマリーCard・表示データの自動反映に使用する。
     * 既定実装は [findByDamId] の one-shot 読込ラップで、テストダブル向け。
     *
     * @param damId 対象ダムの観測所ID
     * @return 保存行のFlow（未保存の場合は null をemit）
     */
    fun historyFlow(damId: String): Flow<SudmonitorHistory?> = flow { emit(findByDamId(damId)) }

    /**
     * 対象ダムの手動更新クールダウン終了時刻（保存済み [SudmonitorHistory.nextUpdateAtEpochMs]）を取得します。
     *
     * 行未保存・取得前は null（= いつでも手動更新可）を返します。
     *
     * @param damId 対象ダムの観測所ID
     * @return クールダウン終了時刻のミリ秒、未保存の場合は null
     */
    suspend fun manualRefreshAvailableAt(damId: String): Long?

    /**
     * 既存の自動更新スケジューラ（WorkManager の periodic work）から呼び出される定期自動取得（D7）。
     *
     * 間隔 [AutoUpdateInterval.ONE_HOUR] / [AutoUpdateInterval.TWELVE_HOURS] の場合は、保存済み
     * [SudmonitorHistory.nextUpdateAtEpochMs] が未来ならスキップし（結果的に最大1日1回）、明けた時のみ実行します。
     * [AutoUpdateInterval.ONE_DAY] / [AutoUpdateInterval.ONE_WEEK] は設定どおり毎回実行します。
     * 機能ゲート無効時は何もしません。行未保存時はクールダウン判定なしで実行します。
     * 実行失敗は握りつぶします（ログのみ）。並列実行は in-flight dedup で抑止されます。
     *
     * @param interval 自動更新の設定間隔
     * @param now 判定基準時刻（現在時刻）
     * @param damId 対象ダムの観測所ID
     */
    suspend fun autoFetch(interval: AutoUpdateInterval, now: Long, damId: String)

    /**
     * 対象ダムの観測明細行を時刻昇順で全件取得します。
     *
     * @param damId 対象ダムの観測所ID
     * @return 観測データのリスト（時刻昇順）
     */
    suspend fun getAllObservations(damId: String): List<DamHistoricalData>

    /**
     * 対象ダムの観測明細行を、時刻範囲（[from] 以上 [to] 以下）で時刻昇順に取得します。
     *
     * @param damId 対象ダムの観測所ID
     * @param from 範囲開始のミリ秒タイムスタンプ（含む）
     * @param to 範囲終了のミリ秒タイムスタンプ（含む）
     * @return 観測データのリスト（時刻昇順）
     */
    suspend fun getObservationsByTimeRange(damId: String, from: Long, to: Long): List<DamHistoricalData>

    /** 現在保持している日次過去データの生DATを返します。未保持・消失時は null。 */
    suspend fun getCurrentRawDatBytes(damId: String): ByteArray? = null

    /** 現在保持している日次過去データのexport向けファイル名を返します。 */
    suspend fun getCurrentRawDatFileName(damId: String): String? = null

    /** 現在保持している日次過去データの生DATが読み出せるかを返します。 */
    suspend fun hasCurrentRawDat(damId: String): Boolean = getCurrentRawDatBytes(damId) != null
}


/**
 * [SudmonitorHistoryRepository.fetchAndStore] の取得結果を表す sealed 型。
 */
sealed interface SudmonitorHistoryFetchResult {

    /**
     * 取得成功（保存完了）を表します。
     *
     * @property history 保存された日次過去データ
     */
    data class Success(val history: SudmonitorHistory) : SudmonitorHistoryFetchResult

    /**
     * 保存が行われなかったことを表します（404=未蓄積、機能ゲート無効、対象外ダムなど。エラーではない）。
     */
    data object NotStored : SudmonitorHistoryFetchResult
}


/**
 * [SudmonitorHistoryRepository.fetchAndStore] の取得トリガーを表す列挙型。
 */
enum class SudmonitorHistoryTrigger {
    /** 初回起動時（対象ダムの保存行が未保存の場合のみ） */
    INITIAL,

    /** ダム変更時（常に再取得・上書き。D3） */
    TARGET_CHANGE,

    /** 表示画面・サイドバーからの手動更新 */
    MANUAL,

    /** 既存の自動更新スケジューラへの連動（D7） */
    AUTO
}
