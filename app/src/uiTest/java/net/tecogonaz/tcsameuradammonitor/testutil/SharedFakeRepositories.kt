// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.testutil

import androidx.work.WorkInfo
import arrow.core.Either
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import net.tecogonaz.tcsameuradammonitor.domain.model.AppSettings
import net.tecogonaz.tcsameuradammonitor.domain.model.AutoUpdateInterval
import net.tecogonaz.tcsameuradammonitor.domain.model.DamConfig
import net.tecogonaz.tcsameuradammonitor.domain.model.DamData
import net.tecogonaz.tcsameuradammonitor.domain.model.DamHistoricalData
import net.tecogonaz.tcsameuradammonitor.domain.model.DamLoadStatus
import net.tecogonaz.tcsameuradammonitor.domain.model.DatabaseVacuumResult
import net.tecogonaz.tcsameuradammonitor.domain.model.DebugLogEntry
import net.tecogonaz.tcsameuradammonitor.domain.model.HistoricalComparisonData
import net.tecogonaz.tcsameuradammonitor.domain.model.HistoricalComparisonMetric
import net.tecogonaz.tcsameuradammonitor.domain.model.HistoricalSearchMeta
import net.tecogonaz.tcsameuradammonitor.domain.model.MainCardExpansionKey
import net.tecogonaz.tcsameuradammonitor.domain.model.MainCardExpansionState
import net.tecogonaz.tcsameuradammonitor.domain.model.SudmonitorHistory
import net.tecogonaz.tcsameuradammonitor.domain.repository.DamDataRepository
import net.tecogonaz.tcsameuradammonitor.domain.repository.DatabaseMaintenanceRepository
import net.tecogonaz.tcsameuradammonitor.domain.repository.DebugDataSessionRecovery
import net.tecogonaz.tcsameuradammonitor.domain.repository.DebugDataSessionRepository
import net.tecogonaz.tcsameuradammonitor.domain.repository.DebugLogRepository
import net.tecogonaz.tcsameuradammonitor.domain.repository.HistoricalComparisonRepository
import net.tecogonaz.tcsameuradammonitor.domain.repository.HistoricalSearchRepository
import net.tecogonaz.tcsameuradammonitor.domain.repository.SettingsRepository
import net.tecogonaz.tcsameuradammonitor.domain.repository.SudmonitorHistoryFetchResult
import net.tecogonaz.tcsameuradammonitor.domain.repository.SudmonitorHistoryRepository
import net.tecogonaz.tcsameuradammonitor.domain.repository.SudmonitorHistoryTrigger
import net.tecogonaz.tcsameuradammonitor.data.source.remote.NetworkAvailability
import net.tecogonaz.tcsameuradammonitor.util.TimeUtils
import net.tecogonaz.tcsameuradammonitor.worker.DamWorkManagerGateway

/** Debug data session切替を副作用なしで成功させるHilt UIテスト用fakeです。 */
open class SharedFakeDebugDataSessionRepository : DebugDataSessionRepository {
    override suspend fun enterDebugMode(): Result<Unit> = Result.success(Unit)

    override suspend fun exitDebugMode(): Result<Unit> = Result.success(Unit)

    override suspend fun recoverSessionOnStartup(): Result<DebugDataSessionRecovery> =
        Result.success(DebugDataSessionRecovery.NOTHING_TO_DO)
}

/**
 * テスト用の擬似設定リポジトリ (Fake SettingsRepository)。
 * アプリケーションの設定データの読み書きやキャッシュの無効化処理のテスト用スタブを提供します。
 */
open class SharedFakeSettingsRepository(initial: AppSettings) : SettingsRepository {
    private val settings = MutableStateFlow(initial)
    private val mainCardExpansionState = MutableStateFlow(MainCardExpansionState())
    override val appSettingsFlow: Flow<AppSettings> = settings
    override val mainCardExpansionStateFlow: Flow<MainCardExpansionState> =
        mainCardExpansionState
    var invalidateSettingsCacheCount = 0
        private set

    val current: AppSettings
        get() = settings.value
    val currentMainCardExpansionState: MainCardExpansionState
        get() = mainCardExpansionState.value

    fun reset(value: AppSettings) {
        settings.value = value
        mainCardExpansionState.value = MainCardExpansionState()
        invalidateSettingsCacheCount = 0
    }

    fun resetMainCardExpansionState(value: MainCardExpansionState) {
        mainCardExpansionState.value = value
    }

    override suspend fun updateSettings(transform: suspend (AppSettings) -> AppSettings) {
        settings.value = transform(settings.value)
    }

    override suspend fun toggleMainCardExpansion(key: MainCardExpansionKey) {
        mainCardExpansionState.update { it.toggled(key) }
    }

    override suspend fun invalidateSettingsCache() {
        invalidateSettingsCacheCount += 1
        settings.value = settings.value.copy()
    }
}

/**
 * テスト用の擬似ダムデータリポジトリ (Fake DamDataRepository)。
 * 最新のダムデータの取得や通信エラー状態のシミュレーション、キャッシュデータのクリアなどのテストをサポートします。
 */
open class SharedFakeDamDataRepository(
    initialData: DamData? = null,
    initialStatus: DamLoadStatus = if (initialData == null) DamLoadStatus.INITIAL else DamLoadStatus.SUCCESS
) : DamDataRepository {
    private val damData = MutableStateFlow(initialData)
    private val combinedState = MutableStateFlow(
        DamRepositoryState(
            networkError = initialStatus == DamLoadStatus.NETWORK_UNAVAILABLE,
            status = initialStatus
        )
    )

    var nextFetchResult: Either<Throwable, DamData>? = null
    var testLastFetchTimeMillis: Long = 0L
    var testLastRawDatBytes: ByteArray? = null
    var testLastRealtimeRawDatBytes: ByteArray? = null
    var testLastDatFileName: String? = null
    var clearDataCount = 0
    var reloadCachedStateCount = 0
    val recordLastFetchTimeMillisArgs = mutableListOf<Boolean>()

    override val damDataFlow: Flow<DamData?> = damData
    override val isLastLoadNetworkError: StateFlow<Boolean> =
        derivedStateFlow(combinedState) { it.networkError }
    override val loadStatus: StateFlow<DamLoadStatus> =
        derivedStateFlow(combinedState) { it.status }

    override fun getLastFetchTimeMillis(): Long = testLastFetchTimeMillis
    override fun getLastRawDatBytes(): ByteArray? = testLastRawDatBytes
    override fun getLastRealtimeRawDatBytes(): ByteArray? = testLastRealtimeRawDatBytes
    override fun getLastDatFileName(): String? = testLastDatFileName

    fun reset(
        data: DamData?,
        loadStatus: DamLoadStatus = if (data == null) DamLoadStatus.INITIAL else DamLoadStatus.SUCCESS
    ) {
        damData.value = data
        updateState(loadStatus == DamLoadStatus.NETWORK_UNAVAILABLE, loadStatus)
        testLastFetchTimeMillis = 0L
        testLastRawDatBytes = null
        testLastRealtimeRawDatBytes = null
        testLastDatFileName = null
        clearDataCount = 0
        reloadCachedStateCount = 0
        recordLastFetchTimeMillisArgs.clear()
        nextFetchResult = null
    }

    fun emitData(data: DamData?) {
        damData.value = data
    }

    override fun setNetworkError(isError: Boolean) {
        updateState(
            networkError = isError,
            status = if (isError) DamLoadStatus.NETWORK_UNAVAILABLE else DamLoadStatus.INITIAL
        )
    }

    override fun setLoadStatus(status: DamLoadStatus) {
        updateState(status == DamLoadStatus.NETWORK_UNAVAILABLE, status)
    }

    override suspend fun fetchLatestData(recordLastFetchTimeMillis: Boolean): Either<Throwable, DamData> {
        recordLastFetchTimeMillisArgs += recordLastFetchTimeMillis
        return nextFetchResult
            ?: damData.value?.let { Either.Right(it) }
            ?: Either.Left(IllegalStateException("No test data configured."))
    }

    override suspend fun hasCachedData(): Boolean = damData.value != null

    override suspend fun clearData() {
        clearDataCount += 1
        reset(data = null)
        clearDataCount = 1
    }

    override suspend fun reloadCachedStateFromStorage() {
        reloadCachedStateCount += 1
    }

    private fun updateState(networkError: Boolean, status: DamLoadStatus) {
        combinedState.update { it.copy(networkError = networkError, status = status) }
    }

    private data class DamRepositoryState(
        val networkError: Boolean,
        val status: DamLoadStatus
    )
}

/**
 * テスト用の擬似デバッグログリポジトリ (Fake DebugLogRepository)。
 * デバッグログの追加・全削除、Flow経由のログ配信のシミュレーションを行います。
 */
open class SharedFakeDebugLogRepository : DebugLogRepository {
    private val entries = MutableStateFlow<List<DebugLogEntry>>(emptyList())
    val addedEntries = mutableListOf<Pair<String, String>>()

    override fun getAllEntriesFlow(): Flow<List<DebugLogEntry>> = entries

    fun reset() {
        entries.value = emptyList()
        addedEntries.clear()
    }

    fun emitEntries(value: List<DebugLogEntry>) {
        entries.value = value
    }

    override suspend fun addEntry(message: String, details: String) {
        addedEntries += message to details
        val nextId = (entries.value.maxOfOrNull { it.id } ?: 0L) + 1L
        entries.value = listOf(
            DebugLogEntry(id = nextId, timestampMillis = 0L, message = message, details = details)
        ) + entries.value
    }

    override suspend fun deleteAll() {
        entries.value = emptyList()
    }
}

/**
 * テスト用の擬似過去データ検索リポジトリ (Fake HistoricalSearchRepository)。
 * 過去データの取得・保存、並び替え、重複チェック、削除、メタデータの取得に関するテスト用スタブを提供します。
 */
open class SharedFakeHistoricalSearchRepository : HistoricalSearchRepository {
    var nextFetchResult: Either<Throwable, HistoricalSearchMeta> =
        Either.Left(IllegalStateException("No historical test result configured."))
    var allData: List<DamHistoricalData> = emptyList()
    var metaList: List<HistoricalSearchMeta> = emptyList()
    var allMetaList: List<HistoricalSearchMeta> = emptyList()
    var duplicateResult: Boolean = false
    val requestedMetaIds = mutableListOf<Long>()
    val deletedMetaIds = mutableListOf<Long>()
    val deletedAllCount: Int
        get() = deletedAllCallCount
    var reorderedIds: List<Long> = emptyList()

    private var deletedAllCallCount = 0
    private val dataByMetaId = mutableMapOf<Long, List<DamHistoricalData>>()

    fun reset(
        metaList: List<HistoricalSearchMeta> = emptyList(),
        allMetaList: List<HistoricalSearchMeta> = metaList,
        dataByMetaId: Map<Long, List<DamHistoricalData>> = emptyMap()
    ) {
        this.metaList = metaList
        this.allMetaList = allMetaList
        this.dataByMetaId.clear()
        this.dataByMetaId.putAll(dataByMetaId)
        allData = emptyList()
        requestedMetaIds.clear()
        deletedMetaIds.clear()
        deletedAllCallCount = 0
        reorderedIds = emptyList()
        duplicateResult = false
        nextFetchResult = Either.Left(IllegalStateException("No historical test result configured."))
    }

    fun setDataForMeta(metaId: Long, data: List<DamHistoricalData>) {
        dataByMetaId[metaId] = data
    }

    override suspend fun fetchAndStore(
        damConfig: DamConfig,
        startDate: String,
        endDate: String
    ): Either<Throwable, HistoricalSearchMeta> {
        nextFetchResult.fold(
            ifLeft = {},
            ifRight = { meta ->
                if (metaList.none { it.id == meta.id }) {
                    metaList = listOf(meta) + metaList
                    allMetaList = listOf(meta) + allMetaList
                }
            }
        )
        return nextFetchResult
    }

    override suspend fun getMetaList(): List<HistoricalSearchMeta> = metaList.take(5)
    override suspend fun getAllMetaList(): List<HistoricalSearchMeta> =
        if (allMetaList.isEmpty()) metaList else allMetaList
    override suspend fun getStoredMetaCount(): Int = getAllMetaList().size
    override suspend fun getMetaById(metaId: Long): HistoricalSearchMeta? {
        requestedMetaIds += metaId
        return getAllMetaList().firstOrNull { it.id == metaId }
    }
    override suspend fun getAllDataByMetaId(metaId: Long): List<DamHistoricalData> =
        dataForMeta(metaId).sortedByTime()
    override suspend fun getDataByMetaIdAndTimeRange(
        metaId: Long,
        fromMillis: Long,
        toMillis: Long
    ): List<DamHistoricalData> = dataForMeta(metaId)
        .filter { data ->
            val millis = data.time.toJstMillis()
            millis != null && millis >= fromMillis && millis <= toMillis
        }
        .sortedByTime()
    override suspend fun getNewestTimeMillisByMetaId(metaId: Long): Long? =
        dataForMeta(metaId).mapNotNull { it.time.toJstMillis() }.maxOrNull()
    override suspend fun getOldestTimeMillisByMetaId(metaId: Long): Long? =
        dataForMeta(metaId).mapNotNull { it.time.toJstMillis() }.minOrNull()
    override suspend fun deleteHistoricalData(metaId: Long) {
        deletedMetaIds += metaId
        metaList = metaList.filterNot { it.id == metaId }
        allMetaList = allMetaList.filterNot { it.id == metaId }
        dataByMetaId.remove(metaId)
    }
    override suspend fun reorderHistoricalMeta(orderedIds: List<Long>) {
        reorderedIds = orderedIds
        val currentAllMeta = getAllMetaList()
        val byId = currentAllMeta.associateBy { it.id }
        allMetaList = orderedIds.mapNotNull { byId[it] } + currentAllMeta.filterNot { it.id in orderedIds }
        metaList = allMetaList.take(5)
    }
    override suspend fun checkDuplicate(damConfigId: String, startDate: String, endDate: String): Boolean =
        duplicateResult
    override suspend fun deleteAllHistoricalData() {
        deletedAllCallCount += 1
        metaList = emptyList()
        allMetaList = emptyList()
        allData = emptyList()
        dataByMetaId.clear()
    }

    override suspend fun setPinned(metaId: Long, isPinned: Boolean) {
        metaList = metaList.map { if (it.id == metaId) it.copy(isPinned = isPinned) else it }
        allMetaList = allMetaList.map { if (it.id == metaId) it.copy(isPinned = isPinned) else it }
    }

    override suspend fun countPinnedMeta(): Int =
        allMetaList.count { it.isPinned }

    private fun dataForMeta(metaId: Long): List<DamHistoricalData> = dataByMetaId[metaId].orEmpty()

    private fun List<DamHistoricalData>.sortedByTime(): List<DamHistoricalData> =
        sortedBy { it.time.toJstMillis() ?: Long.MAX_VALUE }

    private fun String.toJstMillis(): Long? =
        TimeUtils.parseJstMillisAllow24Hour(this, "yyyy/MM/dd HH:mm")
}

/**
 * テスト用の擬似過去比較グラフリポジトリ (Fake HistoricalComparisonRepository)。
 * 過去比較グラフ用データの取得結果・遅延・呼び出し記録（回数・引数）を制御するテスト用スタブを提供します。
 */
open class SharedFakeHistoricalComparisonRepository : HistoricalComparisonRepository {
    var result: Either<Throwable, HistoricalComparisonData> = Either.Right(emptyComparisonData())
    var loadCount: Int = 0
    var lastDamId: String? = null
    var lastMetric: HistoricalComparisonMetric? = null
    var lastWindowStartMillis: Long = 0L
    var lastWindowEndMillis: Long = 0L
    var lastMainYear: Int? = null
    var delayMillis: Long = 0L

    fun reset(
        result: Either<Throwable, HistoricalComparisonData> = Either.Right(emptyComparisonData()),
        delayMillis: Long = 0L
    ) {
        this.result = result
        loadCount = 0
        lastDamId = null
        lastMetric = null
        lastWindowStartMillis = 0L
        lastWindowEndMillis = 0L
        lastMainYear = null
        this.delayMillis = delayMillis
    }

    override suspend fun loadComparison(
        damId: String,
        metric: HistoricalComparisonMetric,
        windowStartMillis: Long,
        windowEndMillis: Long,
        mainYear: Int?
    ): Either<Throwable, HistoricalComparisonData> {
        loadCount += 1
        lastDamId = damId
        lastMetric = metric
        lastWindowStartMillis = windowStartMillis
        lastWindowEndMillis = windowEndMillis
        lastMainYear = mainYear
        if (delayMillis > 0L) {
            delay(delayMillis)
        }
        return result
    }

    companion object {
        /**
         * 空の過去比較データを生成します（currentYear=2026、過去年=2002..2025、時刻軸・時系列は空）。
         */
        fun emptyComparisonData(): HistoricalComparisonData =
            HistoricalComparisonData(
                currentYear = 2026,
                availablePastYears = (2002..2025).toList(),
                periodStartMillis = 0L,
                periodEndMillis = 0L,
                hourlyAxisMillis = emptyList(),
                series = emptyList()
            )
    }
}

/**
 * テスト用の擬似データベースメンテナンスリポジトリ (Fake DatabaseMaintenanceRepository)。
 * VACUUM処理やエラー発生時の動作をテストするために使用します。
 */
open class SharedFakeDatabaseMaintenanceRepository : DatabaseMaintenanceRepository {
    var result = DatabaseVacuumResult(beforeSizeBytes = 1024L, afterSizeBytes = 512L)
    var error: Throwable? = null

    override suspend fun vacuumDatabase(): DatabaseVacuumResult {
        error?.let { throw it }
        return result
    }
}

/**
 * テスト用の擬似 sudmonitor 日次過去データリポジトリ (Fake SudmonitorHistoryRepository)。
 * 日次過去データの取得・保存、保存行・観測行の読出し、クールダウン判定、
 * 自動更新連動（[SudmonitorHistoryRepository.autoFetch]）の呼び出し記録を制御するテスト用スタブを提供します。
 */
open class SharedFakeSudmonitorHistoryRepository : SudmonitorHistoryRepository {
    private val historyByDamId = MutableStateFlow<Map<String, SudmonitorHistory>>(emptyMap())
    var observations: List<DamHistoricalData> = emptyList()
    var fetchResult: Either<Throwable, SudmonitorHistoryFetchResult> =
        Either.Right(SudmonitorHistoryFetchResult.NotStored)
    val fetchAndStoreCalls = mutableListOf<Triple<String, DamConfig, SudmonitorHistoryTrigger>>()
    val autoFetchCalls = mutableListOf<Triple<AutoUpdateInterval, Long, String>>()

    var history: SudmonitorHistory?
        get() = historyByDamId.value.values.firstOrNull()
        set(value) {
            historyByDamId.value = if (value == null) {
                emptyMap()
            } else {
                historyByDamId.value + (value.damId to value)
            }
        }

    fun reset(
        history: SudmonitorHistory? = null,
        observations: List<DamHistoricalData> = emptyList(),
        fetchResult: Either<Throwable, SudmonitorHistoryFetchResult> =
            Either.Right(SudmonitorHistoryFetchResult.NotStored)
    ) {
        historyByDamId.value = if (history == null) emptyMap() else mapOf(history.damId to history)
        this.observations = observations
        this.fetchResult = fetchResult
        fetchAndStoreCalls.clear()
        autoFetchCalls.clear()
    }

    override suspend fun fetchAndStore(
        damId: String,
        damConfig: DamConfig,
        trigger: SudmonitorHistoryTrigger
    ): Either<Throwable, SudmonitorHistoryFetchResult> {
        fetchAndStoreCalls += Triple(damId, damConfig, trigger)
        return fetchResult
    }

    override suspend fun findByDamId(damId: String): SudmonitorHistory? = historyByDamId.value[damId]

    override fun historyFlow(damId: String): Flow<SudmonitorHistory?> =
        historyByDamId.map { it[damId] }

    override suspend fun manualRefreshAvailableAt(damId: String): Long? =
        historyByDamId.value[damId]?.nextUpdateAtEpochMs

    override suspend fun autoFetch(interval: AutoUpdateInterval, now: Long, damId: String) {
        autoFetchCalls += Triple(interval, now, damId)
    }

    override suspend fun getAllObservations(damId: String): List<DamHistoricalData> = observations

    override suspend fun getObservationsByTimeRange(
        damId: String,
        from: Long,
        to: Long
    ): List<DamHistoricalData> = observations.filter { data ->
        val millis = data.time.toJstMillis()
        millis != null && millis >= from && millis <= to
    }

    private fun String.toJstMillis(): Long? =
        TimeUtils.parseJstMillisAllow24Hour(this, "yyyy/MM/dd HH:mm")
}

/**
 * テスト用の擬似WorkManagerゲートウェイ (Fake DamWorkManagerGateway)。
 * バックグラウンド定期処理やワンタイム処理のエンキュー、スケジュール設定、キャンセルなどのテスト検証をサポートします。
 */
open class SharedFakeDamWorkManagerGateway : DamWorkManagerGateway {
    private val autoInfos = MutableStateFlow<List<WorkInfo>>(emptyList())
    private val oneTimeInfos = MutableStateFlow<List<WorkInfo>>(emptyList())
    private val bootInfos = MutableStateFlow<List<WorkInfo>>(emptyList())
    private val sudmonitorHistoryInfos = MutableStateFlow<List<WorkInfo>>(emptyList())

    val enqueuedWorkTypes = mutableListOf<String>()
    val replacedWorkTypes = mutableListOf<String>()
    val scheduled = mutableListOf<Pair<AppSettings, Long>>()
    var cancelCount = 0
    val enqueuedSudmonitorHistoryTriggers = mutableListOf<SudmonitorHistoryTrigger>()
    val replacedSudmonitorHistoryTriggers = mutableListOf<SudmonitorHistoryTrigger>()

    override fun autoWorkInfosFlow(): Flow<List<WorkInfo>> = autoInfos
    override fun oneTimeWorkInfosFlow(): Flow<List<WorkInfo>> = oneTimeInfos
    override fun bootWorkInfosFlow(): Flow<List<WorkInfo>> = bootInfos
    override fun sudmonitorHistoryWorkInfosFlow(): Flow<List<WorkInfo>> = sudmonitorHistoryInfos

    fun reset() {
        autoInfos.value = emptyList()
        oneTimeInfos.value = emptyList()
        bootInfos.value = emptyList()
        sudmonitorHistoryInfos.value = emptyList()
        enqueuedWorkTypes.clear()
        replacedWorkTypes.clear()
        scheduled.clear()
        cancelCount = 0
        enqueuedSudmonitorHistoryTriggers.clear()
        replacedSudmonitorHistoryTriggers.clear()
    }

    fun emitAutoWorkInfos(value: List<WorkInfo>) {
        autoInfos.value = value
    }

    fun emitOneTimeWorkInfos(value: List<WorkInfo>) {
        oneTimeInfos.value = value
    }

    fun emitBootWorkInfos(value: List<WorkInfo>) {
        bootInfos.value = value
    }

    fun emitSudmonitorHistoryWorkInfos(value: List<WorkInfo>) {
        sudmonitorHistoryInfos.value = value
    }

    override fun enqueueOneTimeWork(workType: String) {
        enqueuedWorkTypes += workType
    }

    override fun replaceOneTimeWork(workType: String) {
        replacedWorkTypes += workType
    }

    override fun enqueueSudmonitorHistoryWork(trigger: SudmonitorHistoryTrigger) {
        enqueuedSudmonitorHistoryTriggers += trigger
    }

    override fun replaceSudmonitorHistoryWork(trigger: SudmonitorHistoryTrigger) {
        replacedSudmonitorHistoryTriggers += trigger
    }

    override fun scheduleWorkAtTime(settings: AppSettings, nextRunMillis: Long) {
        scheduled += settings to nextRunMillis
    }

    override fun cancelWork() {
        cancelCount += 1
    }
}

/**
 * テスト用の擬似ネットワーク接続確認オブジェクト (Fake NetworkAvailability)。
 * ネットワークが接続されているかどうかの状態を任意に切り替えることができます。
 */
open class FakeNetworkAvailability(
    var available: Boolean = true
) : NetworkAvailability {
    override fun isNetworkAvailable(): Boolean = available
}

@OptIn(kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi::class)
private class MappedStateFlow<T, R>(
    private val upstream: StateFlow<T>,
    private val transform: (T) -> R
) : StateFlow<R> {
    override val replayCache: List<R>
        get() = listOf(value)
    override val value: R
        get() = transform(upstream.value)

    override suspend fun collect(collector: kotlinx.coroutines.flow.FlowCollector<R>): Nothing =
        upstream.collect { collector.emit(transform(it)) }
}

private fun <T, R> derivedStateFlow(
    upstream: StateFlow<T>,
    transform: (T) -> R
): StateFlow<R> = MappedStateFlow(upstream, transform)
