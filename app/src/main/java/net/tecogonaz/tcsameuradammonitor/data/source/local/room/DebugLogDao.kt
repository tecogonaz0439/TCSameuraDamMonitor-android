// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.data.source.local.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow


/**
 * アプリケーションのデバッグログ（[DebugLogEntity]）へのデータアクセスオブジェクト（DAO）。
 *
 * ログファイルのインサート、クリーンアップ（最大128件の保持制限制御）、および一括削除を管理します。
 */
@Dao
interface DebugLogDao {

    companion object {
        /** データベース内に保存するデバッグログレコードの最大件数 */
        const val MAX_ENTRIES = 128
    }

    /**
     * 保存されているすべてのデバッグログを主キーの降順（最新順）で監視するための[Flow]を取得します。
     *
     * @return デバッグログエンティティのリストを流すフロー
     */
    @Query("SELECT * FROM debug_log ORDER BY id DESC")
    fun getAllEntriesFlow(): Flow<List<DebugLogEntity>>

    /**
     * 新しいデバッグログエントリを直接挿入します。
     *
     * @param entry 登録対象の[DebugLogEntity]
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: DebugLogEntity)

    /**
     * 保持上限数を超えた古いデバッグログレコードを削除します。
     *
     * @param maxCount 残す最新ログ件数の閾値（デフォルト: [MAX_ENTRIES]）
     */
    @Query("DELETE FROM debug_log WHERE id NOT IN (SELECT id FROM debug_log ORDER BY id DESC LIMIT :maxCount)")
    suspend fun deleteOldEntries(maxCount: Int = MAX_ENTRIES)

    /**
     * ログレコードを挿入したのち、自動的に古い余剰レコードを削除するトランザクション処理。
     *
     * @param entry 登録対象の[DebugLogEntity]
     */
    @Transaction
    suspend fun insertAndCleanup(entry: DebugLogEntity) {
        insert(entry)
        deleteOldEntries()
    }

    /**
     * 保存されているすべてのデバッグログレコードを一括削除します。
     */
    @Query("DELETE FROM debug_log")
    suspend fun deleteAll()
}
