// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.domain.repository

import arrow.core.Either
import net.tecogonaz.tcsameuradammonitor.domain.model.DamConfig
import net.tecogonaz.tcsameuradammonitor.domain.model.DamHistoricalData
import net.tecogonaz.tcsameuradammonitor.domain.model.HistoricalSearchMeta


/**
 * 国土交通省（MLIT）から過去のダム観測データを検索・取得し、ローカルに永続化して管理するリポジトリ。
 */
interface HistoricalSearchRepository {

    companion object {
        /** ローカルデータベースに保存可能な過去データ検索履歴メタデータの最大件数 */
        const val MAX_STORED_META_COUNT = 16
    }

    /**
     * 指定されたダムと期間の過去観測データをサーバーから取得し、データベースに検索履歴メタデータおよび履歴明細データとして保存します。
     *
     * 保存件数が[MAX_STORED_META_COUNT]を超える場合は、古い検索結果から順に自動削除（ローテーション）されます。
     *
     * @param damConfig 対象ダムの静的設定（[DamConfig]）
     * @param startDate 検索開始日（フォーマット: YYYY/MM/DD または YYYY-MM-DD等、データソース仕様準拠）
     * @param endDate 検索終了日
     * @return 成功時は保存された検索メタデータ[HistoricalSearchMeta]、失敗時はエラーを包んだ[Either]
     */
    suspend fun fetchAndStore(
        damConfig: DamConfig,
        startDate: String,
        endDate: String
    ): Either<Throwable, HistoricalSearchMeta>

    /**
     * 表示順（[HistoricalSearchMeta.displayOrder]）の昇順で、保存されている検索メタデータのリストを取得します。
     *
     * @return 過去検索メタデータのリスト
     */
    suspend fun getMetaList(): List<HistoricalSearchMeta>

    /**
     * 内部IDの昇順（または作成順）で、すべての検索メタデータのリストを取得します。
     *
     * @return すべての過去検索メタデータのリスト
     */
    suspend fun getAllMetaList(): List<HistoricalSearchMeta>

    /**
     * 現在ローカルデータベースに保存されている検索履歴メタデータの件数を取得します。
     *
     * @return 履歴件数
     */
    suspend fun getStoredMetaCount(): Int

    /**
     * 指定されたメタIDに合致する過去検索メタデータを取得します。
     *
     * @param metaId 検索履歴メタデータのローカルID
     * @return 合致する[HistoricalSearchMeta]、存在しない場合はnull
     */
    suspend fun getMetaById(metaId: Long): HistoricalSearchMeta?

    /**
     * 指定されたメタIDに紐づくすべての過去ダム観測履歴データを取得します。
     *
     * @param metaId 検索履歴メタデータのローカルID
     * @return 過去観測履歴データのリスト
     */
    suspend fun getAllDataByMetaId(metaId: Long): List<DamHistoricalData>

    /**
     * 指定されたメタIDに紐づく過去ダム観測データから、さらに特定の時間範囲を絞り込んで取得します。
     *
     * @param metaId 検索履歴メタデータのローカルID
     * @param fromMillis 範囲開始日時のミリ秒タイムスタンプ（含む）
     * @param toMillis 範囲終了日時のミリ秒タイムスタンプ（含む）
     * @return 指定期間内の過去観測履歴データのリスト
     */
    suspend fun getDataByMetaIdAndTimeRange(
        metaId: Long,
        fromMillis: Long,
        toMillis: Long
    ): List<DamHistoricalData>

    /**
     * 指定されたメタIDに紐づく過去ダムデータのうち、最新の観測日時のミリ秒タイムスタンプを取得します。
     *
     * @param metaId 検索履歴メタデータのローカルID
     * @return 最新観測日時のタイムスタンプ（ミリ秒）。データがない場合はnull。
     */
    suspend fun getNewestTimeMillisByMetaId(metaId: Long): Long?

    /**
     * 指定されたメタIDに紐づく過去ダムデータのうち、最古の観測日時のミリ秒タイムスタンプを取得します。
     *
     * @param metaId 検索履歴メタデータのローカルID
     * @return 最古観測日時のタイムスタンプ（ミリ秒）。データがない場合はnull。
     */
    suspend fun getOldestTimeMillisByMetaId(metaId: Long): Long?

    /**
     * 指定された検索履歴メタデータと、それに紐づく過去観測履歴データをデータベースから削除します。
     *
     * @param metaId 削除対象の検索履歴メタデータのローカルID
     */
    suspend fun deleteHistoricalData(metaId: Long)

    /**
     * 過去データ検索結果リストの表示順を再ソートして並び替えます。
     *
     * @param orderedIds 並び替え順に整列されたメタIDのリスト
     */
    suspend fun reorderHistoricalMeta(orderedIds: List<Long>)

    /**
     * 同一ダムかつ同一期間の過去検索結果がすでにローカルデータベースに存在するかどうかを確認します（重複検知）。
     *
     * @param damConfigId ダム識別用ID（例: "sameura"）
     * @param startDate 検索開始日
     * @param endDate 検索終了日
     * @return 重複している場合はtrue
     */
    suspend fun checkDuplicate(damConfigId: String, startDate: String, endDate: String): Boolean

    /**
     * 保存されているすべての過去データ検索履歴メタデータおよび観測履歴明細データを一括削除します。
     */
    suspend fun deleteAllHistoricalData()

    /**
     * 指定されたメタIDのピン留め状態を設定します。
     *
     * @param metaId 検索履歴メタデータのローカルID
     * @param isPinned ピン留めする場合はtrue、解除する場合はfalse
     */
    suspend fun setPinned(metaId: Long, isPinned: Boolean)

    /**
     * ピン留めされている検索履歴メタデータの件数を取得します。
     *
     * @return ピン留め件数
     */
    suspend fun countPinnedMeta(): Int
}
