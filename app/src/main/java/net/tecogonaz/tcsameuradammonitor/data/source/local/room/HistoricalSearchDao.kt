// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.data.source.local.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query


/**
 * 過去データ検索履歴（[HistoricalSearchMetaEntity]）および紐づく過去観測履歴明細データ（[HistoricalDamDataEntity]）へのデータアクセスオブジェクト（DAO）。
 */
@Dao
interface HistoricalSearchDao {

    /**
     * 指定されたメタIDに紐づく過去ダム観測データをすべて削除します。
     *
     * @param metaId 検索履歴メタデータのローカルID
     */
    @Query("DELETE FROM historical_dam_data WHERE searchMetaId = :metaId")
    suspend fun deleteDataByMetaId(metaId: Long)

    /**
     * 指定されたメタIDに紐づく検索履歴メタデータをデータベースから削除します。
     *
     * @param metaId 検索履歴メタデータのローカルID
     */
    @Query("DELETE FROM historical_search_meta WHERE id = :metaId")
    suspend fun deleteMetaById(metaId: Long)

    /**
     * 過去データ検索履歴メタデータを新規登録します。
     *
     * @param meta 登録対象の[HistoricalSearchMetaEntity]
     * @return 自動生成された新規レコードのID
     */
    @Insert
    suspend fun insertMeta(meta: HistoricalSearchMetaEntity): Long

    /**
     * 過去ダム観測履歴明細データの一覧を一括登録します。
     *
     * @param list 登録対象の[HistoricalDamDataEntity]リスト
     */
    @Insert
    suspend fun insertDataList(list: List<HistoricalDamDataEntity>)

    
    /**
     * 表示順の昇順で、最大5件の検索履歴メタデータ一覧を取得します。
     *
     * @return 検索履歴メタデータエンティティのリスト（最大5件）
     */
    @Query("SELECT * FROM historical_search_meta ORDER BY sortOrder ASC LIMIT 5")
    suspend fun getMetaList(): List<HistoricalSearchMetaEntity>

    /**
     * 表示順の昇順で、すべての検索履歴メタデータ一覧を取得します。
     *
     * @return 検索履歴メタデータエンティティのリスト
     */
    @Query("SELECT * FROM historical_search_meta ORDER BY sortOrder ASC")
    suspend fun getAllMeta(): List<HistoricalSearchMetaEntity>

    /**
     * すべての検索履歴メタデータの表示順（sortOrder）を1つずつ押し上げます（新規挿入データを最上位にするため）。
     */
    @Query("UPDATE historical_search_meta SET sortOrder = sortOrder + 1")
    suspend fun shiftAllSortOrderUp()

    /**
     * 指定された検索履歴メタデータの表示順を設定します。
     *
     * @param metaId 検索履歴メタデータのID
     * @param sortOrder 設定する表示順の数値
     */
    @Query("UPDATE historical_search_meta SET sortOrder = :sortOrder WHERE id = :metaId")
    suspend fun updateSortOrder(metaId: Long, sortOrder: Int)

    /**
     * 指定されたIDに紐づく過去データ検索履歴メタデータを取得します。
     *
     * @param metaId 検索履歴メタデータのローカルID
     * @return 検索履歴メタデータエンティティ。存在しない場合はnull。
     */
    @Query("SELECT * FROM historical_search_meta WHERE id = :metaId LIMIT 1")
    suspend fun getMetaById(metaId: Long): HistoricalSearchMetaEntity?

    /**
     * 現在保存されている過去データ検索履歴メタデータの総件数を取得します。
     *
     * @return 件数
     */
    @Query("SELECT COUNT(*) FROM historical_search_meta")
    suspend fun countMeta(): Int

    /**
     * 同一ダムかつ同一期間の過去検索結果がすでにローカルデータベースに存在するかどうかを確認（重複カウント）します。
     *
     * @param damConfigId ダム識別用ID
     * @param searchBgnDate 検索開始日 (フォーマット: YYYYMMDD)
     * @param searchEndDate 検索終了日 (フォーマット: YYYYMMDD)
     * @return 重複しているレコード件数
     */
    @Query("""
        SELECT COUNT(*) FROM historical_search_meta 
        WHERE damConfigId = :damConfigId 
        AND searchBgnDate = :searchBgnDate 
        AND searchEndDate = :searchEndDate
    """)
    suspend fun countDuplicates(damConfigId: String, searchBgnDate: String, searchEndDate: String): Int

    /**
     * 保存されているすべての過去ダム観測履歴明細データレコードを一括削除します。
     */
    @Query("DELETE FROM historical_dam_data")
    suspend fun deleteAllData()

    /**
     * 保存されているすべての過去データ検索履歴メタデータレコードを一括削除します。
     */
    @Query("DELETE FROM historical_search_meta")
    suspend fun deleteAllMeta()

    /**
     * 指定されたメタIDに紐づく過去観測データから、さらに特定の時間範囲を絞り込んで取得します。
     *
     * @param metaId 検索履歴メタデータのローカルID
     * @param fromMillis 範囲開始日時のミリ秒タイムスタンプ（含む）
     * @param toMillis 範囲終了日時のミリ秒タイムスタンプ（含む）
     * @return 指定期間内の過去観測明細エンティティのリスト（観測日時昇順）
     */
    @Query("SELECT * FROM historical_dam_data WHERE searchMetaId = :metaId AND timeMillis >= :fromMillis AND timeMillis <= :toMillis ORDER BY timeMillis ASC")
    suspend fun queryDataByMetaIdAndTimeRange(metaId: Long, fromMillis: Long, toMillis: Long): List<HistoricalDamDataEntity>

    /**
     * 指定されたメタIDに紐づく過去ダムデータのうち、最古の観測日時のミリ秒タイムスタンプを取得します。
     *
     * @param metaId 検索履歴メタデータのローカルID
     * @return 最古観測日時のタイムスタンプ（ミリ秒）。データがない場合はnull。
     */
    @Query("SELECT MIN(timeMillis) FROM historical_dam_data WHERE searchMetaId = :metaId")
    suspend fun queryOldestTimeMillisByMetaId(metaId: Long): Long?

    /**
     * 指定されたメタIDに紐づく過去ダムデータのうち、最新の観測日時のミリ秒タイムスタンプを取得します。
     *
     * @param metaId 検索履歴メタデータのローカルID
     * @return 最新観測日時のタイムスタンプ（ミリ秒）。データがない場合はnull。
     */
    @Query("SELECT MAX(timeMillis) FROM historical_dam_data WHERE searchMetaId = :metaId")
    suspend fun queryNewestTimeMillisByMetaId(metaId: Long): Long?

    /**
     * 指定されたメタIDに紐づくすべての過去ダム観測履歴明細データを観測日時昇順で取得します。
     *
     * @param metaId 検索履歴メタデータのローカルID
     * @return 過去観測履歴明細エンティティのリスト
     */
    @Query("SELECT * FROM historical_dam_data WHERE searchMetaId = :metaId ORDER BY timeMillis ASC")
    suspend fun queryAllDataByMetaId(metaId: Long): List<HistoricalDamDataEntity>

    /**
     * 指定されたメタIDのピン留め状態を更新します。
     *
     * @param metaId 検索履歴メタデータのローカルID
     * @param isPinned ピン留めする場合はtrue、解除する場合はfalse
     */
    @Query("UPDATE historical_search_meta SET isPinned = :isPinned WHERE id = :metaId")
    suspend fun updatePinnedStatus(metaId: Long, isPinned: Boolean)

    /**
     * ピン留めされている検索履歴メタデータの件数を取得します。
     *
     * @return ピン留め件数
     */
    @Query("SELECT COUNT(*) FROM historical_search_meta WHERE isPinned = 1")
    suspend fun countPinnedMeta(): Int
}
