// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.data.source.local.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * ダムの最新観測データ（[DamEntity]）へのデータアクセスオブジェクト（DAO）。
 *
 * リアルタイム取得された最新データの保存、取得、および全データ消去を行います。
 */
@Dao
interface DamDao {
    /**
     * 最新のダム観測データを監視するための[Flow]を取得します。
     *
     * @return 最新の[DamEntity]を流すフロー。存在しない場合はnull。
     */
    @Query("SELECT * FROM dam_data ORDER BY lastFetchTimeMillis DESC LIMIT 1")
    fun getDamDataFlow(): Flow<DamEntity?>

    /**
     * データベースにキャッシュされている最新のダム観測データを直接取得します。
     *
     * @return キャッシュされている[DamEntity]。存在しない場合はnull。
     */
    @Query("SELECT * FROM dam_data ORDER BY lastFetchTimeMillis DESC LIMIT 1")
    suspend fun getDamData(): DamEntity?

    /** デバッグセッション開始時の待避用に、すべての保存行を取得します。 */
    @Query("SELECT * FROM dam_data ORDER BY observationStationId ASC")
    suspend fun getAllDamData(): List<DamEntity>

    /**
     * ダム観測データを新規挿入または上書き保存します。
     *
     * @param damData 挿入・更新対象の[DamEntity]
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(damData: DamEntity)

    /** デバッグセッション終了時の復元用に、複数の保存行を一括登録します。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(damData: List<DamEntity>)

    /**
     * 最新ダム観測データテーブルのキャッシュレコードをすべて削除します。
     */
    @Query("DELETE FROM dam_data")
    suspend fun deleteAll()
}
