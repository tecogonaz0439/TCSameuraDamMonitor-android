// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.data.source.local.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow


/**
 * sudmonitor の日次過去データ（[SudmonitorHistoryEntity]）とその観測明細（[SudmonitorHistoryObservationEntity]）へのデータアクセスオブジェクト（DAO）。
 *
 * 観測明細の削除は外部キーに CASCADE を設定せず、本 DAO の明示的な DELETE で実施する
 * （[insertObservations] の前に [deleteObservations] で既存行を一括削除する方式）。
 */
@Dao
interface SudmonitorHistoryDao {

    /**
     * 日次過去データの保存行を登録または上書きします（ダムごと1件）。
     *
     * 同一 [SudmonitorHistoryEntity.damId] の既存行がある場合は REPLACE（削除+挿入）で置き換える。
     * 外部キー制約のため、呼び出し側は本メソッドの前に [deleteObservations] で観測明細を削除すること。
     *
     * @param entity 保存対象の[SudmonitorHistoryEntity]
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHistory(entity: SudmonitorHistoryEntity)

    /**
     * 観測明細行の一覧を一括登録します。
     *
     * 保存前に [deleteObservations] で当該ダムの既存観測行を削除し、全件置き換えること。
     *
     * @param list 登録対象の[SudmonitorHistoryObservationEntity]リスト
     */
    @Insert
    suspend fun insertObservations(list: List<SudmonitorHistoryObservationEntity>)

    /**
     * 指定されたダムの観測明細行をすべて削除します。
     *
     * @param damId 対象ダムの観測所ID
     */
    @Query("DELETE FROM sudmonitor_history_observation WHERE damId = :damId")
    suspend fun deleteObservations(damId: String)

    /**
     * 指定されたダムの日次過去データ保存行を取得します。
     *
     * @param damId 対象ダムの観測所ID
     * @return 保存行、未保存の場合は null
     */
    @Query("SELECT * FROM sudmonitor_history WHERE damId = :damId")
    suspend fun findByDamId(damId: String): SudmonitorHistoryEntity?

    /**
     * 指定されたダムの日次過去データ保存行をリアクティブに監視します。
     *
     * `sudmonitor_history` テーブルへの書込（取得保存・デバッグ待避復元など）があるたびに再emitします。
     *
     * @param damId 対象ダムの観測所ID
     * @return 保存行のFlow（未保存の場合は null をemit）
     */
    @Query("SELECT * FROM sudmonitor_history WHERE damId = :damId")
    fun findByDamIdFlow(damId: String): Flow<SudmonitorHistoryEntity?>

    /** デバッグセッション開始時の待避用に、すべての日次保存行を取得します。 */
    @Query("SELECT * FROM sudmonitor_history ORDER BY damId ASC")
    suspend fun getAllHistory(): List<SudmonitorHistoryEntity>

    /** デバッグセッション開始時の待避用に、すべての観測明細を取得します。 */
    @Query("SELECT * FROM sudmonitor_history_observation ORDER BY damId ASC, rowNo ASC")
    suspend fun getAllObservations(): List<SudmonitorHistoryObservationEntity>

    /**
     * すべての観測明細行を削除します。
     */
    @Query("DELETE FROM sudmonitor_history_observation")
    suspend fun deleteAllObservations()

    /**
     * すべての日次過去データ保存行を削除します。
     */
    @Query("DELETE FROM sudmonitor_history")
    suspend fun deleteAllHistory()

    /**
     * データ初期化・デバッグ用に、すべての日次過去データ（保存行と観測明細）を削除します。
     *
     * 外部キー制約のため、保存行の削除の前に観測明細を先に削除します。
     */
    suspend fun deleteAll() {
        deleteAllObservations()
        deleteAllHistory()
    }

    /**
     * 指定されたダムの観測明細行を時刻昇順で全件取得します。
     *
     * @param damId 対象ダムの観測所ID
     * @return 観測明細エンティティのリスト（時刻昇順）
     */
    @Query("SELECT * FROM sudmonitor_history_observation WHERE damId = :damId ORDER BY timeEpochMs ASC")
    suspend fun getAllObservations(damId: String): List<SudmonitorHistoryObservationEntity>

    /**
     * 指定されたダムの観測明細行を、時刻範囲（[from] 以上 [to] 以下）で時刻昇順に取得します。
     *
     * @param damId 対象ダムの観測所ID
     * @param from 範囲開始のミリ秒タイムスタンプ（含む）
     * @param to 範囲終了のミリ秒タイムスタンプ（含む）
     * @return 観測明細エンティティのリスト（時刻昇順）
     */
    @Query(
        "SELECT * FROM sudmonitor_history_observation " +
            "WHERE damId = :damId AND timeEpochMs BETWEEN :from AND :to ORDER BY timeEpochMs ASC"
    )
    suspend fun queryObservationsByDamIdAndTimeRange(
        damId: String,
        from: Long,
        to: Long
    ): List<SudmonitorHistoryObservationEntity>
}
