// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.domain.repository

import arrow.core.Either
import net.tecogonaz.tcsameuradammonitor.domain.model.DamData
import net.tecogonaz.tcsameuradammonitor.domain.model.DamLoadStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * ダムの最新観測データの取得、保持、および更新状態の管理を行うリポジトリ。
 *
 * 国土交通省（MLIT）のリアルタイム観測データを取得し、データベースへの保存やロードステータスの管理、
 * ウィジェットや常駐通知に供給するためのデータフローを提供します。
 */
interface DamDataRepository {
    
    /**
     * 最後にダムデータを取得した時刻をミリ秒で取得します。
     *
     * @return 最終取得時刻のタイムスタンプ（ミリ秒）。未取得の場合は0L。
     */
    fun getLastFetchTimeMillis(): Long
    
    /**
     * 最後に取得またはデバッグで読み込んだ観測データ（.dat形式）の生バイトデータを取得します。
     *
     * @return Rawバイト配列。未取得の場合はnull。
     */
    fun getLastRawDatBytes(): ByteArray?
    
    /**
     * 最後に実際のネットワーク通信（リアルタイム更新）で取得した観測データ（.dat形式）の生バイトデータを取得します。
     *
     * @return リアルタイムのRawバイト配列。未取得の場合はnull。
     */
    fun getLastRealtimeRawDatBytes(): ByteArray?
    
    /**
     * 最後に読み込んだ.datファイルの名前を取得します。
     *
     * @return ファイル名。未取得の場合はnull。
     */
    fun getLastDatFileName(): String?

    /**
     * 現在の最新ダム観測データ（貯水率や貯水量などのモデル）を監視するための[Flow]。
     */
    val damDataFlow: Flow<DamData?>
    
    /**
     * 前回のデータロード時にネットワークエラーが発生したかどうかを監視するための[StateFlow]。
     */
    val isLastLoadNetworkError: StateFlow<Boolean>
    
    /**
     * ネットワークエラーの状態を手動で設定します。
     *
     * @param isError エラーが発生した場合はtrue
     */
    fun setNetworkError(isError: Boolean)
    
    /**
     * 現在のデータ読み込み状態（[DamLoadStatus]）を監視するための[StateFlow]。
     */
    val loadStatus: StateFlow<DamLoadStatus>
    
    /**
     * データ読み込み状態を設定します。
     *
     * @param status 設定する[DamLoadStatus]
     */
    fun setLoadStatus(status: DamLoadStatus)

    /**
     * サーバーまたは設定に応じたデバッグソースから最新のダム観測データを取得（手動更新 / 自動更新）します。
     *
     * @param recordLastFetchTimeMillis 最終取得時刻を更新する場合はtrue（デフォルト: true）
     * @return 成功時は[DamData]、失敗時はエラーを包んだ[Either]
     */
    suspend fun fetchLatestData(recordLastFetchTimeMillis: Boolean = true): Either<Throwable, DamData>
    
    /**
     * キャッシュされた（ローカルデータベースに保存された）ダムデータが存在するかどうかを判定します。
     *
     * @return キャッシュが存在する場合はtrue
     */
    suspend fun hasCachedData(): Boolean

    /**
     * キャッシュされたダムデータおよびリポジトリ内の状態（最終取得時刻、生バイト等）をクリアします。
     */
    suspend fun clearData()

    /**
     * Roomと内部cacheファイルから、最終取得時刻・raw DAT等のメモリ状態を再読込します。
     * Debugセッションから通常データを復元した直後に使用します。
     */
    suspend fun reloadCachedStateFromStorage()
}
