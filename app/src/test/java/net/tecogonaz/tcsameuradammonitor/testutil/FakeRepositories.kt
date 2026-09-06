// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.testutil

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import net.tecogonaz.tcsameuradammonitor.data.source.local.room.SudmonitorHistoryDao
import net.tecogonaz.tcsameuradammonitor.data.source.local.room.SudmonitorHistoryEntity
import net.tecogonaz.tcsameuradammonitor.data.source.local.room.SudmonitorHistoryObservationEntity
import net.tecogonaz.tcsameuradammonitor.data.source.remote.NetworkAvailability
import net.tecogonaz.tcsameuradammonitor.domain.model.AppSettings
import net.tecogonaz.tcsameuradammonitor.domain.model.DamData
import net.tecogonaz.tcsameuradammonitor.domain.model.DamLoadStatus

/**
 * ユニットテスト用の擬似設定リポジトリ。
 */
class FakeSettingsRepository(initial: AppSettings) : SharedFakeSettingsRepository(initial)

/**
 * ユニットテスト用の擬似ダムデータリポジトリ。
 * 初期データの設定や取得成功時のEither型での返却をサポートします。
 */
class FakeDamDataRepository(initialData: DamData? = null) :
    SharedFakeDamDataRepository(initialData, initialStatus = DamLoadStatus.INITIAL) {
    init {
        nextFetchResult = initialData?.let { arrow.core.Either.Right(it) }
            ?: arrow.core.Either.Left(IllegalStateException("No test data configured."))
    }
}

/**
 * ユニットテスト用の擬似デバッグログリポジトリ。
 */
class FakeDebugLogRepository : SharedFakeDebugLogRepository()

/**
 * ユニットテスト用の擬似過去データ検索リポジトリ。
 */
class FakeHistoricalSearchRepository : SharedFakeHistoricalSearchRepository()

/**
 * ユニットテスト用の擬似過去比較グラフリポジトリ。
 */
class FakeHistoricalComparisonRepository : SharedFakeHistoricalComparisonRepository()

/**
 * ユニットテスト用の擬似データベースメンテナンスリポジトリ。
 */
class FakeDatabaseMaintenanceRepository : SharedFakeDatabaseMaintenanceRepository()

/**
 * ユニットテスト用の擬似 sudmonitor 日次過去データリポジトリ。
 */
class FakeSudmonitorHistoryRepository : SharedFakeSudmonitorHistoryRepository()

/**
 * ユニットテスト用の擬似 sudmonitor 日次過去データ DAO。
 *
 * [SudmonitorHistoryDao.upsertHistory] は REPLACE 相当として保存行を上書きし、
 * 観測明細は [SudmonitorHistoryDao.deleteObservations] + [SudmonitorHistoryDao.insertObservations] の
 * 一括置換を記録します（ダムごと1件の上書き契約 D3）。
 */
class FakeSudmonitorHistoryDao : SudmonitorHistoryDao {
    private val historyRowState = MutableStateFlow<SudmonitorHistoryEntity?>(null)
    var historyRow: SudmonitorHistoryEntity?
        get() = historyRowState.value
        set(value) {
            historyRowState.value = value
        }
    var observations: List<SudmonitorHistoryObservationEntity> = emptyList()
    var upsertCalls = 0
        private set
    var insertObservationsCalls = 0
        private set
    var deleteObservationsCalls = 0
        private set
    var deleteAllCalls = 0
        private set
    val insertedObservationLists = mutableListOf<List<SudmonitorHistoryObservationEntity>>()
    var lastQueryRangeFrom: Long = 0L
        private set
    var lastQueryRangeTo: Long = 0L
        private set

    fun reset(
        historyRow: SudmonitorHistoryEntity? = null,
        observations: List<SudmonitorHistoryObservationEntity> = emptyList()
    ) {
        this.historyRow = historyRow
        this.observations = observations
        upsertCalls = 0
        insertObservationsCalls = 0
        deleteObservationsCalls = 0
        deleteAllCalls = 0
        insertedObservationLists.clear()
        lastQueryRangeFrom = 0L
        lastQueryRangeTo = 0L
    }

    override suspend fun findByDamId(damId: String): SudmonitorHistoryEntity? =
        historyRow?.takeIf { it.damId == damId }

    override fun findByDamIdFlow(damId: String): Flow<SudmonitorHistoryEntity?> =
        historyRowState.map { it?.takeIf { row -> row.damId == damId } }

    override suspend fun getAllHistory(): List<SudmonitorHistoryEntity> = listOfNotNull(historyRow)

    override suspend fun getAllObservations(): List<SudmonitorHistoryObservationEntity> = observations

    override suspend fun upsertHistory(entity: SudmonitorHistoryEntity) {
        upsertCalls += 1
        historyRow = entity
    }

    override suspend fun insertObservations(list: List<SudmonitorHistoryObservationEntity>) {
        insertObservationsCalls += 1
        insertedObservationLists += list
        observations = list
    }

    override suspend fun deleteObservations(damId: String) {
        deleteObservationsCalls += 1
        observations = emptyList()
    }

    override suspend fun getAllObservations(damId: String): List<SudmonitorHistoryObservationEntity> =
        observations

    override suspend fun queryObservationsByDamIdAndTimeRange(
        damId: String,
        from: Long,
        to: Long
    ): List<SudmonitorHistoryObservationEntity> {
        lastQueryRangeFrom = from
        lastQueryRangeTo = to
        return observations.filter { it.timeEpochMs in from..to }
    }

    override suspend fun deleteAllObservations() {
        deleteAllCalls += 1
        observations = emptyList()
    }

    override suspend fun deleteAllHistory() {
        deleteAllCalls += 1
        historyRow = null
    }
}

/**
 * ユニットテスト用の擬似WorkManagerゲートウェイ。
 */
class FakeDamWorkManagerGateway : SharedFakeDamWorkManagerGateway()

/**
 * ユニットテスト用の擬似ネットワーク接続確認オブジェクト。
 */
class FakeNetworkAvailability(
    var available: Boolean = true
) : NetworkAvailability {
    override fun isNetworkAvailable(): Boolean = available
}
