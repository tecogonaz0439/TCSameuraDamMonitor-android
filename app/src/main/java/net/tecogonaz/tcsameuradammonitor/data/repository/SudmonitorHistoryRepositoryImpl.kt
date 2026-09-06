// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.data.repository

import android.content.Context
import android.util.Log
import arrow.core.Either
import arrow.core.raise.either
import net.tecogonaz.tcsameuradammonitor.data.source.local.room.DatabaseTransactionRunner
import net.tecogonaz.tcsameuradammonitor.data.source.local.DebugDatSourceReader
import net.tecogonaz.tcsameuradammonitor.data.source.local.room.SudmonitorHistoryDao
import net.tecogonaz.tcsameuradammonitor.data.source.local.room.SudmonitorHistoryObservationEntity
import net.tecogonaz.tcsameuradammonitor.data.source.local.room.toDomain
import net.tecogonaz.tcsameuradammonitor.data.source.remote.DamFileParser
import net.tecogonaz.tcsameuradammonitor.data.source.remote.SudmonitorHistoricalClient
import net.tecogonaz.tcsameuradammonitor.domain.model.AutoUpdateInterval
import net.tecogonaz.tcsameuradammonitor.domain.model.DebugDatSelectionMode
import net.tecogonaz.tcsameuradammonitor.domain.model.DebugSimulateMode
import net.tecogonaz.tcsameuradammonitor.domain.model.DamConfig
import net.tecogonaz.tcsameuradammonitor.domain.model.DamHistoricalData
import net.tecogonaz.tcsameuradammonitor.domain.model.RealtimeDataSource
import net.tecogonaz.tcsameuradammonitor.domain.model.SudmonitorHistory
import net.tecogonaz.tcsameuradammonitor.domain.model.getDamConfig
import net.tecogonaz.tcsameuradammonitor.domain.repository.SettingsRepository
import net.tecogonaz.tcsameuradammonitor.domain.repository.SudmonitorHistoryFetchResult
import net.tecogonaz.tcsameuradammonitor.domain.repository.SudmonitorHistoryRepository
import net.tecogonaz.tcsameuradammonitor.domain.repository.SudmonitorHistoryTrigger
import net.tecogonaz.tcsameuradammonitor.util.TimeUtils
import net.tecogonaz.tcsameuradammonitor.util.catchNonCancellationSuspend
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [SudmonitorHistoryRepository]の具現化クラス。
 *
 * sudmonitor の日次過去データ（`/v1/history/{damId}/latest.dat`、直近31暦日）を取得し、
 * 専用のRoomテーブル（`sudmonitor_history` / `sudmonitor_history_observation`）へ保存します。
 * 404（未蓄積）はエラーにせず [SudmonitorHistoryFetchResult.NotStored] として静かにスキップし、
 * 取得した .dat バイト列は cacheDir へ best-effort で保存します（デバッグ・差分調査用）。
 */
@Singleton
class SudmonitorHistoryRepositoryImpl(
    private val sudmonitorHistoricalClient: SudmonitorHistoricalClient,
    private val parser: DamFileParser,
    private val transactionRunner: DatabaseTransactionRunner,
    private val sudmonitorHistoryDao: SudmonitorHistoryDao,
    private val settingsRepository: SettingsRepository,
    private val debugDatSourceReader: DebugDatSourceReader,
    @ApplicationContext private val context: Context,
    private val clock: () -> Long = System::currentTimeMillis,
    private val operationMutex: DebugDataOperationMutex = DebugDataOperationMutex()
) : SudmonitorHistoryRepository {

    @Inject
    constructor(
        sudmonitorHistoricalClient: SudmonitorHistoricalClient,
        parser: DamFileParser,
        transactionRunner: DatabaseTransactionRunner,
        sudmonitorHistoryDao: SudmonitorHistoryDao,
        settingsRepository: SettingsRepository,
        debugDatSourceReader: DebugDatSourceReader,
        @ApplicationContext context: Context,
        operationMutex: DebugDataOperationMutex
    ) : this(
        sudmonitorHistoricalClient,
        parser,
        transactionRunner,
        sudmonitorHistoryDao,
        settingsRepository,
        debugDatSourceReader,
        context,
        System::currentTimeMillis,
        operationMutex
    )

    /** 同時取得アクセスを防ぐ in-flight dedup 用のミューテックス（既存 `fetchMutex` 相当）。 */
    private val fetchMutex = Mutex()

    /**
     * 日次過去データを取得し、専用テーブルへ all-or-nothing で保存します。
     *
     * 1. 機能ゲート（`historicalDataSource == SUDMONITOR`）確認。無効なら [SudmonitorHistoryFetchResult.NotStored]
     * 2. [SudmonitorHistoricalClient.fetchLatest] を呼び、null（404=未蓄積）は [SudmonitorHistoryFetchResult.NotStored]
     * 3. `X-TCS-Dam-Id` 不一致等はクライアント側で Left 済み（そのまま伝播）
     * 4. `X-TCS-History-Since/Until` を寛容解釈して実カバレッジを計算（欠落・非パースはカバレッジ不明）
     * 5. 期間が判明した場合のみ [DamFileParser.parseHistoricalDatToDataList] でパース
     * 6. 統計（先頭/末尾/min/max 貯水率）を算出
     * 7. `X-TCS-Next-Update-At` を寛容 parse。失敗・欠落時は「取得日の翌日 00:13 JST」へフォールバック（D5）
     * 8. raw .dat を cacheDir へ best-effort 保存（成功条件には含めない）
     * 9. 保存行の upsert + 観測行の一括置換を単一トランザクションで実行
     */
    override suspend fun fetchAndStore(
        damId: String,
        damConfig: DamConfig,
        trigger: SudmonitorHistoryTrigger
    ): Either<Throwable, SudmonitorHistoryFetchResult> = operationMutex.withLock { fetchMutex.withLock {
        either {
            val settings = catchNonCancellationSuspend {
                settingsRepository.appSettingsFlow.first()
            }.bind()
            if (settings.historicalDataSource != RealtimeDataSource.SUDMONITOR) {
                return@either SudmonitorHistoryFetchResult.NotStored
            }

            if (settings.debugModeEnabled && settings.debugSimulateMode == DebugSimulateMode.NETWORK_UNAVAILABLE) {
                raise(IOException("Debug mode: network unavailable."))
            }
            if (settings.debugModeEnabled && settings.debugSimulateMode == DebugSimulateMode.LOADING_FAILURE) {
                raise(IOException("Debug mode: historical daily data loading failed."))
            }

            if (settings.debugModeEnabled) {
                val bytes = when (settings.debugHistoricalDailyDatFileMode) {
                    DebugDatSelectionMode.BUNDLED -> catchNonCancellationSuspend {
                        debugDatSourceReader.readBundled(BUNDLED_HISTORICAL_DAILY_FILE_NAME)
                    }.bind()
                    DebugDatSelectionMode.LATEST -> readCurrentRawDatOrThrow(damId).bind()
                    DebugDatSelectionMode.USER_SELECTED -> {
                        val uri = settings.debugHistoricalDailyDatFileUri
                            ?: raise(IOException("A historical daily debug DAT file has not been selected."))
                        catchNonCancellationSuspend {
                            debugDatSourceReader.readSaf(
                                uriString = uri,
                                maxSizeBytes = MAX_DEBUG_DAT_FILE_SIZE,
                                tooLargeMessage = "The historical daily debug DAT file is too large.",
                                readErrorMessage = "The historical daily debug DAT file could not be read."
                            )
                        }.bind()
                    }
                }
                return@either parseAndStoreDebugDat(bytes, damId, damConfig).bind()
            }

            val result = sudmonitorHistoricalClient.fetchLatest(damId).bind()
            if (result == null) {
                return@either SudmonitorHistoryFetchResult.NotStored
            }

            val coverage = resolveCoverage(result.since, result.until)
            val parsedData: List<DamHistoricalData> = if (coverage != null) {
                parser.parseHistoricalDatToDataList(
                    csvBytes = result.bytes,
                    damConfigId = damConfig.id,
                    searchBgnDate = coverage.startDate,
                    searchEndDate = coverage.endDate
                ).bind().second
            } else {
                emptyList()
            }

            val observations = parsedData.mapIndexedNotNull { index, data ->
                val timeEpochMs = TimeUtils.parseJstMillisAllow24Hour(data.time, TIME_FORMAT)
                    ?: return@mapIndexedNotNull null
                SudmonitorHistoryObservationEntity(
                    damId = damId,
                    rowNo = index,
                    timeText = data.time,
                    timeEpochMs = timeEpochMs,
                    rainfallHourlyMm = data.catchmentAverageRainfall,
                    storageVolume1000m3 = data.storageVolume,
                    inflowM3s = data.inflow,
                    outflowM3s = data.outflow,
                    storageRatePct = data.storagePercentage
                )
            }
            val storageRates = parsedData.mapNotNull { it.storagePercentage }
            val now = clock()
            val history = SudmonitorHistory(
                damId = damId,
                periodStartEpochMs = coverage?.periodStartEpochMs,
                periodEndEpochMs = coverage?.periodEndEpochMs,
                status = SudmonitorHistory.STATUS_SUCCESS,
                rowCount = observations.size,
                firstStorageRatePct = parsedData.firstOrNull()?.storagePercentage,
                lastStorageRatePct = parsedData.lastOrNull()?.storagePercentage,
                minStorageRatePct = storageRates.minOrNull(),
                maxStorageRatePct = storageRates.maxOrNull(),
                fetchedAtEpochMs = now,
                nextUpdateAtEpochMs = resolveNextUpdateAtEpochMs(
                    nextUpdateAtHeader = result.nextUpdateAtHeader,
                    fetchedAtHeader = result.fetchedAtHeader,
                    now = now
                ),
                rawDatPath = saveRawDatBestEffort(result.bytes, damId),
                updatedAtEpochMs = now
            )

            catchNonCancellationSuspend {
                transactionRunner.withTransaction {
                    sudmonitorHistoryDao.deleteObservations(damId)
                    sudmonitorHistoryDao.upsertHistory(history.toEntity())
                    if (observations.isNotEmpty()) {
                        sudmonitorHistoryDao.insertObservations(observations)
                    }
                }
            }.bind()

            return@either SudmonitorHistoryFetchResult.Success(history)
        }
    } }

    override suspend fun findByDamId(damId: String): SudmonitorHistory? =
        catchNonCancellationSuspend {
            sudmonitorHistoryDao.findByDamId(damId)?.let(SudmonitorHistory::fromEntity)
        }.getOrNull()

    /**
     * 対象ダムの日次過去データ保存行をRoom Flowでリアクティブに監視します。
     *
     * `sudmonitor_history` テーブルへの書込（取得保存・デバッグ待避復元など）があるたびに、
     * マッピング済みの新しい保存行が再emitされます。
     */
    override fun historyFlow(damId: String): Flow<SudmonitorHistory?> =
        sudmonitorHistoryDao.findByDamIdFlow(damId).map { entity ->
            entity?.let(SudmonitorHistory::fromEntity)
        }

    override suspend fun manualRefreshAvailableAt(damId: String): Long? =
        catchNonCancellationSuspend {
            val settings = settingsRepository.appSettingsFlow.first()
            if (settings.debugModeEnabled) null else sudmonitorHistoryDao.findByDamId(damId)?.nextUpdateAtEpochMs
        }.getOrNull()

    /**
     * 既存の自動更新スケジューラへの連動（D7）。
     *
     * 間隔 1時間/12時間 は保存済み [SudmonitorHistory.nextUpdateAtEpochMs] が未来ならスキップし
     * （最大1日1回）、1日/7日 は毎回実行します。行未保存時はクールダウン判定なしで実行し、
     * 並列実行は [fetchMutex] の in-flight dedup で抑止されます。失敗は握りつぶします（ログのみ）。
     */
    override suspend fun autoFetch(interval: AutoUpdateInterval, now: Long, damId: String) {
        val settings = catchNonCancellationSuspend {
            settingsRepository.appSettingsFlow.first()
        }.getOrNull() ?: return
        if (settings.historicalDataSource != RealtimeDataSource.SUDMONITOR) {
            return
        }
        val existing = catchNonCancellationSuspend {
            sudmonitorHistoryDao.findByDamId(damId)
        }.getOrNull()
        if (!settings.debugModeEnabled &&
            (interval == AutoUpdateInterval.ONE_HOUR || interval == AutoUpdateInterval.TWELVE_HOURS)
        ) {
            val nextUpdateAtEpochMs = existing?.nextUpdateAtEpochMs
            if (nextUpdateAtEpochMs != null && nextUpdateAtEpochMs > now) {
                Log.d(
                    LOG_TAG,
                    "Auto fetch skipped (cooldown): damId=$damId nextUpdateAtEpochMs=$nextUpdateAtEpochMs"
                )
                return
            }
        }
        val result = fetchAndStore(damId, getDamConfig(damId), SudmonitorHistoryTrigger.AUTO)
        result.onLeft { error ->
            Log.w(LOG_TAG, "Auto fetch failed: damId=$damId", error)
        }
    }

    override suspend fun getAllObservations(damId: String): List<DamHistoricalData> =
        catchNonCancellationSuspend {
            sudmonitorHistoryDao.getAllObservations(damId).map { it.toDomain() }
        }.getOrNull() ?: emptyList()

    override suspend fun getObservationsByTimeRange(
        damId: String,
        from: Long,
        to: Long
    ): List<DamHistoricalData> =
        catchNonCancellationSuspend {
            sudmonitorHistoryDao.queryObservationsByDamIdAndTimeRange(damId, from, to)
                .map { it.toDomain() }
        }.getOrNull() ?: emptyList()

    override suspend fun getCurrentRawDatBytes(damId: String): ByteArray? =
        catchNonCancellationSuspend {
            val path = sudmonitorHistoryDao.findByDamId(damId)?.rawDatPath ?: return@catchNonCancellationSuspend null
            File(path).takeIf { it.isFile }?.readBytes()
        }.getOrNull()

    override suspend fun getCurrentRawDatFileName(damId: String): String? =
        catchNonCancellationSuspend {
            val path = sudmonitorHistoryDao.findByDamId(damId)?.rawDatPath ?: return@catchNonCancellationSuspend null
            File(path).takeIf { it.isFile }?.name
        }.getOrNull()

    override suspend fun hasCurrentRawDat(damId: String): Boolean = getCurrentRawDatBytes(damId) != null

    private suspend fun readCurrentRawDatOrThrow(damId: String): Either<Throwable, ByteArray> =
        catchNonCancellationSuspend {
            getCurrentRawDatBytes(damId)
                ?: throw IOException("No current historical daily DAT data is available.")
        }

    /** Debug DATはヘッダー期間に依存せず、全観測行そのものから期間を確定して保存します。 */
    private suspend fun parseAndStoreDebugDat(
        bytes: ByteArray,
        damId: String,
        damConfig: DamConfig
    ): Either<Throwable, SudmonitorHistoryFetchResult> = either {
        val parsed = parser.parseHistoricalDatToDataList(
            csvBytes = bytes,
            damConfigId = damConfig.id,
            searchBgnDate = "00010101",
            searchEndDate = "99991231"
        ).bind()
        val meta = parsed.first
        val parsedData = parsed.second
        if (meta.observationStationId != damId) {
            raise(IOException("Historical daily DAT station mismatch: expected $damId but was ${meta.observationStationId}."))
        }
        if (parsedData.size != DEBUG_HISTORICAL_DAILY_ROW_COUNT) {
            raise(IOException("Historical daily DAT must contain $DEBUG_HISTORICAL_DAILY_ROW_COUNT observations."))
        }
        val timed = parsedData.map { data ->
            val epochMs = TimeUtils.parseJstMillisAllow24Hour(data.time, TIME_FORMAT)
                ?: raise(IOException("Historical daily DAT contains an invalid time: ${data.time}."))
            data to epochMs
        }
        if (timed.first().first.time.substringAfterLast(' ') != "01:00") {
            raise(IOException("Historical daily DAT must start at 01:00."))
        }
        if (timed.last().first.time.substringAfterLast(' ') != "24:00") {
            raise(IOException("Historical daily DAT must end at 24:00."))
        }
        if (timed.zipWithNext().any { (current, next) -> current.second >= next.second }) {
            raise(IOException("Historical daily DAT observations must be in strictly increasing time order."))
        }

        val observations = timed.mapIndexed { index, (data, epochMs) ->
            SudmonitorHistoryObservationEntity(
                damId = damId,
                rowNo = index,
                timeText = data.time,
                timeEpochMs = epochMs,
                rainfallHourlyMm = data.catchmentAverageRainfall,
                storageVolume1000m3 = data.storageVolume,
                inflowM3s = data.inflow,
                outflowM3s = data.outflow,
                storageRatePct = data.storagePercentage
            )
        }
        val storageRates = parsedData.mapNotNull { it.storagePercentage }
        val now = clock()
        val previousRawPath = catchNonCancellationSuspend {
            sudmonitorHistoryDao.findByDamId(damId)?.rawDatPath
        }.bind()
        val newRawPath = saveDebugRawDatBestEffort(bytes, damId, now)
        val history = SudmonitorHistory(
            damId = damId,
            periodStartEpochMs = timed.first().second,
            periodEndEpochMs = timed.last().second,
            status = SudmonitorHistory.STATUS_SUCCESS,
            rowCount = observations.size,
            firstStorageRatePct = parsedData.first().storagePercentage,
            lastStorageRatePct = parsedData.last().storagePercentage,
            minStorageRatePct = storageRates.minOrNull(),
            maxStorageRatePct = storageRates.maxOrNull(),
            fetchedAtEpochMs = now,
            nextUpdateAtEpochMs = 0L,
            rawDatPath = newRawPath,
            updatedAtEpochMs = now
        )
        val stored = catchNonCancellationSuspend {
            transactionRunner.withTransaction {
                sudmonitorHistoryDao.deleteObservations(damId)
                sudmonitorHistoryDao.upsertHistory(history.toEntity())
                sudmonitorHistoryDao.insertObservations(observations)
            }
        }
        stored.onLeft {
            newRawPath?.let { path -> runCatching { File(path).delete() } }
        }.bind()
        if (previousRawPath != null && previousRawPath != newRawPath) {
            runCatching { File(previousRawPath).delete() }
        }
        SudmonitorHistoryFetchResult.Success(history)
    }

    /**
     * `X-TCS-History-Since/Until` ヘッダを解釈して、実カバレッジ（JST日単位）を計算します。
     *
     * ヘッダ欠落・パース失敗は「カバレッジ不明」として null を返し、過剰なカバレッジ主張は行いません
     * （既存 [net.tecogonaz.tcsameuradammonitor.data.repository.HistoricalSearchRepositoryImpl] の
     * カバレッジ判定と同じロジック）。until が 00:00（24:00表記）の場合は前日までをカバーとみなします。
     *
     * @param sinceHeader `X-TCS-History-Since`（または `X-TCS-History-Start`）の値
     * @param untilHeader `X-TCS-History-Until`（または `X-TCS-History-End`）の値
     * @return カバレッジ（開始日・終了日とJST日単位のepochミリ秒）、ヘッダ欠落・非パース・期間異常の場合は null
     */
    private fun resolveCoverage(sinceHeader: String?, untilHeader: String?): Coverage? {
        val since = sinceHeader?.let { runCatching { OffsetDateTime.parse(it) }.getOrNull() } ?: return null
        val until = untilHeader?.let { runCatching { OffsetDateTime.parse(it) }.getOrNull() } ?: return null
        val startDay = since.atZoneSameInstant(TimeUtils.JST_ZONE).toLocalDate()
        val untilDay = until.atZoneSameInstant(TimeUtils.JST_ZONE).toLocalDate()
        val endDay = if (until.toLocalTime() == LocalTime.MIDNIGHT) untilDay.minusDays(1) else untilDay
        if (endDay.isBefore(startDay)) return null
        return Coverage(
            startDate = startDay.format(DateTimeFormatter.BASIC_ISO_DATE),
            endDate = endDay.format(DateTimeFormatter.BASIC_ISO_DATE),
            periodStartEpochMs = startDay.atStartOfDay(TimeUtils.JST_ZONE).toInstant().toEpochMilli(),
            periodEndEpochMs = endDay.atStartOfDay(TimeUtils.JST_ZONE).toInstant().toEpochMilli()
        )
    }

    /**
     * `X-TCS-Next-Update-At` を寛容に解釈し、失敗・欠落時は「取得日（`X-TCS-Fetched-At`、欠落時は現在時刻）の
     * 翌日 00:13 JST」へフォールバックします（D5・共通契約 §4）。
     *
     * @param nextUpdateAtHeader `X-TCS-Next-Update-At` の値
     * @param fetchedAtHeader `X-TCS-Fetched-At` の値（フォールバックの取得日基準）
     * @param now 現在時刻（フォールバックの取得日基準）
     * @return 次回更新予定時刻のepochミリ秒
     */
    private fun resolveNextUpdateAtEpochMs(
        nextUpdateAtHeader: String?,
        fetchedAtHeader: String?,
        now: Long
    ): Long {
        nextUpdateAtHeader?.let { header ->
            runCatching { Instant.parse(header).toEpochMilli() }.getOrNull()?.let { return it }
        }
        val fetchedAtMillis = fetchedAtHeader?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
            ?: now
        return nextDayAtJst(fetchedAtMillis)
    }

    /**
     * [baseMillis] の JST 日付の翌日 00:13 の epoch ミリ秒を計算します（日次過去データのフォールバック）。
     *
     * @param baseMillis 基準時刻（取得日）
     * @return 翌日 00:13 JST のepochミリ秒
     */
    private fun nextDayAtJst(baseMillis: Long): Long {
        val baseDate = Instant.ofEpochMilli(baseMillis).atZone(TimeUtils.JST_ZONE).toLocalDate()
        return baseDate.plusDays(1)
            .atTime(NEXT_UPDATE_FALLBACK_HOUR, NEXT_UPDATE_FALLBACK_MINUTE)
            .atZone(TimeUtils.JST_ZONE)
            .toInstant()
            .toEpochMilli()
    }

    /**
     * 取得した .dat バイト列を cacheDir の `sudmonitor_history_{damId}.dat` へ best-effort で保存します
     * （既存 `last_realtime_raw.dat` キャッシュと同じ方式）。保存失敗は取得成功条件に含めません。
     *
     * @param bytes 生 .dat バイト列
     * @param damId 対象ダムの観測所ID
     * @return 保存先の絶対パス、保存失敗時は null
     */
    private suspend fun saveRawDatBestEffort(bytes: ByteArray, damId: String): String? =
        kotlin.runCatching {
            withContext(Dispatchers.IO) {
                val file = File(context.cacheDir, "$RAW_DAT_FILE_PREFIX$damId.dat")
                file.writeBytes(bytes)
                file.absolutePath
            }
        }.getOrNull()

    /** DB置換失敗時に既存rawを失わないよう、Debug読込は世代別ファイルへ先に保存します。 */
    private suspend fun saveDebugRawDatBestEffort(bytes: ByteArray, damId: String, now: Long): String? =
        kotlin.runCatching {
            withContext(Dispatchers.IO) {
                val file = File(context.cacheDir, "${RAW_DAT_FILE_PREFIX}debug_${damId}_$now.dat")
                file.writeBytes(bytes)
                file.absolutePath
            }
        }.getOrNull()

    private data class Coverage(
        val startDate: String,
        val endDate: String,
        val periodStartEpochMs: Long,
        val periodEndEpochMs: Long
    )

    companion object {
        private const val LOG_TAG = "SudmonitorHistoryRepositoryImpl"
        private const val TIME_FORMAT = "yyyy/MM/dd HH:mm"
        private const val RAW_DAT_FILE_PREFIX = "sudmonitor_history_"
        private const val BUNDLED_HISTORICAL_DAILY_FILE_NAME = "historical_daily_sameura.dat"
        private const val MAX_DEBUG_DAT_FILE_SIZE = 5 * 1024 * 1024
        private const val DEBUG_HISTORICAL_DAILY_ROW_COUNT = 744
        private const val NEXT_UPDATE_FALLBACK_HOUR = 0
        private const val NEXT_UPDATE_FALLBACK_MINUTE = 13
    }
}
