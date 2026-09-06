// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.data.repository

import net.tecogonaz.tcsameuradammonitor.data.source.local.room.DebugLogDao
import net.tecogonaz.tcsameuradammonitor.data.source.local.room.DebugLogEntity
import net.tecogonaz.tcsameuradammonitor.domain.model.DebugLogEntry
import net.tecogonaz.tcsameuradammonitor.domain.repository.DebugLogRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject


/**
 * [DebugLogRepository]の実装クラス。
 *
 * ローカルデータベースのデバッグログテーブル（[DebugLogDao]）を仲介し、
 * アプリ内の各種デバッグ用イベント・エラーログ情報の記録、監視フローの提供、一括削除を行います。
 */
class DebugLogRepositoryImpl @Inject constructor(
    private val dao: DebugLogDao
) : DebugLogRepository {

    /**
     * 保存されているすべてのデバッグログを、監視可能なドメインモデル[DebugLogEntry]のリストとして、タイムスタンプ降順で取得します。
     *
     * @return デバッグログリストを監視する[Flow]
     */
    override fun getAllEntriesFlow(): Flow<List<DebugLogEntry>> =
        dao.getAllEntriesFlow().map { entities ->
            entities.map { it.toDomain() }
        }

    /**
     * 新しいデバッグログレコードを作成して追加します。
     *
     * 保存件数が制限値を超える場合、古いログレコードは自動的にクリーンアップされます。
     *
     * @param message ログ概要メッセージ
     * @param details 補足情報や例外のスタックトレース等
     */
    override suspend fun addEntry(message: String, details: String) {
        val entity = DebugLogEntity(
            timestampMillis = System.currentTimeMillis(),
            message = message,
            details = details
        )
        dao.insertAndCleanup(entity)
    }

    /**
     * 保存されているすべてのデバッグログを一括削除します。
     */
    override suspend fun deleteAll() {
        dao.deleteAll()
    }

    
    private fun DebugLogEntity.toDomain() = DebugLogEntry(
        id = id,
        timestampMillis = timestampMillis,
        message = message,
        details = details
    )
}
