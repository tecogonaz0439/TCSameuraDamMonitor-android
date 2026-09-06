// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.data.repository

import android.content.Context
import arrow.core.Either
import arrow.core.raise.either
import net.tecogonaz.tcsameuradammonitor.R
import net.tecogonaz.tcsameuradammonitor.data.source.local.DebugDatSourceReader
import net.tecogonaz.tcsameuradammonitor.data.source.local.room.DamDao
import net.tecogonaz.tcsameuradammonitor.data.source.local.room.toDomain
import net.tecogonaz.tcsameuradammonitor.data.source.local.room.toEntity
import net.tecogonaz.tcsameuradammonitor.data.source.remote.DatUrlParseError
import net.tecogonaz.tcsameuradammonitor.data.source.remote.DamFileParser
import net.tecogonaz.tcsameuradammonitor.data.source.remote.DamNetworkDataSource
import net.tecogonaz.tcsameuradammonitor.data.source.remote.SudmonitorNetworkDataSource
import net.tecogonaz.tcsameuradammonitor.di.ApplicationScope
import net.tecogonaz.tcsameuradammonitor.domain.model.AppSettings
import net.tecogonaz.tcsameuradammonitor.domain.model.DamConfigProvider
import net.tecogonaz.tcsameuradammonitor.domain.model.DamData
import net.tecogonaz.tcsameuradammonitor.domain.model.DamLoadStatus
import net.tecogonaz.tcsameuradammonitor.domain.model.DebugDatSelectionMode
import net.tecogonaz.tcsameuradammonitor.domain.model.DebugSimulateMode
import net.tecogonaz.tcsameuradammonitor.domain.model.RealtimeDataSource
import net.tecogonaz.tcsameuradammonitor.domain.model.SUDMONITOR_BASE_URL
import net.tecogonaz.tcsameuradammonitor.domain.model.SUDMONITOR_SUPPORTED_DAM_IDS
import net.tecogonaz.tcsameuradammonitor.domain.repository.DamDataRepository
import net.tecogonaz.tcsameuradammonitor.domain.repository.SettingsRepository
import net.tecogonaz.tcsameuradammonitor.util.catchNonCancellationSuspend
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton
import java.io.File
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * [DamDataRepository]の具現化クラス。
 *
 * 国土交通省（MLIT）のウェブサイトからリアルタイム観測データをHTML経由で取得（スクレイピング）・解析し、
 * Roomデータベースへキャッシュとして保存します。
 * デバッグモードが有効な場合は、SDカード/ファイル選択（SAF）、以前保存した生データ（REALTIME）、
 * アプリ内蔵のアセット（BUNDLED）などのデバッグ用.datファイルから擬似的にデータを読み込みます。
 * また、デバッグデータ期間の自動更新（自動進捗機能）やシミュレーションエラー機能などのデバッグ用機能も制御します。
 */
@Singleton
class DamDataRepositoryImpl @Inject constructor(
    private val networkDataSource: DamNetworkDataSource,
    private val sudmonitorNetworkDataSource: SudmonitorNetworkDataSource,
    private val parser: DamFileParser,
    private val settingsRepository: SettingsRepository,
    private val damDao: DamDao,
    @ApplicationScope private val applicationScope: CoroutineScope,
    @ApplicationContext private val context: Context,
    private val damConfigProvider: DamConfigProvider,
    private val debugDatSourceReader: DebugDatSourceReader,
    private val operationMutex: DebugDataOperationMutex = DebugDataOperationMutex()
) : DamDataRepository {
    /**
     * 実装内部の診断用エラー。
     *
     * public Repository APIはViewModel / Workerへの波及を避けるため `Either<Throwable, T>` のまま維持し、
     * ここで既存のlocalized Throwableへ変換する。
     */
    private sealed interface DamDataError {
        data class DatUrlNotFound(val parseError: DatUrlParseError) : DamDataError
    }

    /** 最後にデータを正常取得した時刻のミリ秒タイムスタンプ */
    private var _lastFetchTimeMillis: Long = 0L
    
    /** 最後に読み込んだ.datファイルの生バイトデータ（ネットワークまたはデバッグファイル） */
    private var _lastRawDatBytes: ByteArray? = null
    
    /** 最後に実際のネットワーク通信で取得した.datファイルの生バイトデータ（デバッグ書き出し用） */
    private var _lastRealtimeRawDatBytes: ByteArray? = null
    
    /** 最後に読み込んだ.datファイルのURLまたはパス */
    private var _lastDatUrl: String? = null

    /** 最後にネットワーク取得したリアルタイム.datファイルのURL */
    private var _lastRealtimeDatUrl: String? = null

    override fun getLastFetchTimeMillis(): Long = _lastFetchTimeMillis
    override fun getLastRawDatBytes(): ByteArray? = _lastRawDatBytes
    override fun getLastRealtimeRawDatBytes(): ByteArray? = _lastRealtimeRawDatBytes
    override fun getLastDatFileName(): String? = _lastDatUrl?.substringAfterLast("/")

    
    /** 前回の更新処理がネットワーク接続エラーにより失敗したかどうかを示す状態フロー */
    private val _isLastLoadNetworkError = MutableStateFlow(false)
    override val isLastLoadNetworkError: StateFlow<Boolean> = _isLastLoadNetworkError.asStateFlow()

    /** ダムデータの最新ロード状態を示すフロー */
    private val _loadStatus = MutableStateFlow(DamLoadStatus.INITIAL)
    override val loadStatus: StateFlow<DamLoadStatus> = _loadStatus.asStateFlow()

    
    /** 同時データ取得アクセスを防ぐ排他制御用のミューテックス */
    private val fetchMutex = Mutex()

    companion object {
        
        /** デバッグモードで読み込み可能な.datファイルの最大サイズ制限（10MB） */
        private const val MAX_DEBUG_FILE_SIZE = 10 * 1024 * 1024
    }

    init {
        // アプリ起動時にローカルDBのキャッシュデータから最終更新時刻およびステータスを復元します
        applicationScope.launch {
            reloadCachedStateFromStorage()
        }
    }

    override val damDataFlow: Flow<DamData?> = combine(
        damDao.getDamDataFlow(),
        settingsRepository.appSettingsFlow
    ) { entity, settings ->
        entity
            ?.takeIf { it.observationStationId == settings.targetDamId }
            ?.toDomain()
    }

    override suspend fun hasCachedData(): Boolean {
        val settings = settingsRepository.appSettingsFlow.first()
        return damDao.getDamData()?.observationStationId == settings.targetDamId
    }

    override suspend fun clearData() = operationMutex.withLock { fetchMutex.withLock {
        damDao.deleteAll()
        _lastFetchTimeMillis = 0L
        _lastRawDatBytes = null
        _lastRealtimeRawDatBytes = null
        _lastDatUrl = null
        _lastRealtimeDatUrl = null
        
        // キャッシュファイルを削除します
        kotlin.runCatching {
            withContext(Dispatchers.IO) {
                File(context.cacheDir, "last_realtime_raw.dat").delete()
                File(context.cacheDir, "last_realtime_url.txt").delete()
                File(context.cacheDir, "last_current_raw.dat").delete()
                File(context.cacheDir, "last_current_url.txt").delete()
            }
        }
        
        _isLastLoadNetworkError.value = false
        _loadStatus.value = DamLoadStatus.INITIAL
    } }

    override suspend fun reloadCachedStateFromStorage() = fetchMutex.withLock {
        val entity = damDao.getDamData()
        _lastFetchTimeMillis = entity?.lastFetchTimeMillis ?: 0L
        _loadStatus.value = if (entity == null) DamLoadStatus.INITIAL else DamLoadStatus.SUCCESS
        _isLastLoadNetworkError.value = false
        withContext(Dispatchers.IO) {
            val realtimeDatFile = File(context.cacheDir, "last_realtime_raw.dat")
            val realtimeUrlFile = File(context.cacheDir, "last_realtime_url.txt")
            _lastRealtimeRawDatBytes = realtimeDatFile.takeIf(File::isFile)?.readBytes()
            _lastRealtimeDatUrl = realtimeUrlFile.takeIf(File::isFile)?.readText()
            val currentDatFile = File(context.cacheDir, "last_current_raw.dat")
            val currentUrlFile = File(context.cacheDir, "last_current_url.txt")
            _lastRawDatBytes = currentDatFile.takeIf(File::isFile)?.readBytes() ?: _lastRealtimeRawDatBytes
            _lastDatUrl = currentUrlFile.takeIf(File::isFile)?.readText() ?: _lastRealtimeDatUrl
        }
    }

    
    override fun setNetworkError(isError: Boolean) {
        _isLastLoadNetworkError.value = isError
        _loadStatus.value = if (isError) DamLoadStatus.NETWORK_UNAVAILABLE else DamLoadStatus.INITIAL
    }

    override fun setLoadStatus(status: DamLoadStatus) {
        _loadStatus.value = status
        _isLastLoadNetworkError.value = status == DamLoadStatus.NETWORK_UNAVAILABLE
    }

    /**
     * 最新データ取得の副作用境界。
     *
     * DAO / DataStore / config / debug file読込の通常例外はLeft化する。
     * Coroutine cancellationだけは処理を継続してはいけないため [catchNonCancellationSuspend] で再スローする。
     */
    override suspend fun fetchLatestData(recordLastFetchTimeMillis: Boolean): Either<Throwable, DamData> = operationMutex.withLock { fetchMutex.withLock {
        either {
        val appSettings = catchNonCancellationSuspend {
            settingsRepository.appSettingsFlow.first()
        }.bind()
        val config = catchNonCancellationSuspend {
            damConfigProvider.get(appSettings.targetDamId)
        }.bind()

        if (appSettings.debugModeEnabled && appSettings.debugSimulateMode == DebugSimulateMode.LOADING_FAILURE) {
            raise(Exception(context.getString(R.string.settings_debug_simulate_error_msg)))
        }

        if (appSettings.debugModeEnabled) {
            val bytes = when (appSettings.debugRealtimeDatFileMode) {
                DebugDatSelectionMode.USER_SELECTED -> {
                    val uriString = appSettings.debugRealtimeDatFileUri
                        ?: raise(Exception(context.getString(R.string.error_debug_file_read)))
                    catchNonCancellationSuspend {
                        debugDatSourceReader.readSaf(
                            uriString = uriString,
                            maxSizeBytes = MAX_DEBUG_FILE_SIZE,
                            tooLargeMessage = context.getString(R.string.error_debug_file_too_large),
                            readErrorMessage = context.getString(R.string.error_debug_file_read)
                        )
                    }.bind()
                }
                DebugDatSelectionMode.LATEST -> {
                    _lastRealtimeRawDatBytes
                        ?: raise(Exception(context.getString(R.string.settings_debug_export_no_data)))
                }
                DebugDatSelectionMode.BUNDLED -> {
                    catchNonCancellationSuspend {
                        debugDatSourceReader.readBundled(DebugDatSelectionMode.BUNDLED_FILE_NAME)
                    }.bind()
                }
            }
            
            _lastRawDatBytes = bytes
            _lastDatUrl = when (appSettings.debugRealtimeDatFileMode) {
                DebugDatSelectionMode.USER_SELECTED -> appSettings.debugRealtimeDatFileUri
                    ?.substringAfterLast('/')
                    ?.takeIf { it.isNotBlank() }
                    ?: "debug.dat"
                DebugDatSelectionMode.LATEST -> _lastRealtimeDatUrl ?: "realtime.dat"
                DebugDatSelectionMode.BUNDLED -> DebugDatSelectionMode.BUNDLED_FILE_NAME
            }
            val effectiveDebugDataEndMillis = resolveAutoAdvancedDebugDataEndMillis(
                bytes = bytes,
                currentEndMillis = appSettings.debugRealtimeDataEndMillis,
                enabled = appSettings.debugRealtimeDataPeriodAutoAdvanceEnabled
            ).bind()
            val parsed = parser.parseDatCsv(
                bytes,
                config.id,
                config.nameJa,
                appSettings.debugRealtimeDataStartMillis,
                appSettings.debugRealtimeDataEndMillis
            ).bind()
            val now = System.currentTimeMillis()
            val fetchTimeMillis = if (recordLastFetchTimeMillis) now else _lastFetchTimeMillis
            catchNonCancellationSuspend {
                damDao.insert(parsed.toEntity(fetchTimeMillis))
            }.bind()
            _lastFetchTimeMillis = fetchTimeMillis
            persistCurrentRawBestEffort(bytes, _lastDatUrl)
            _loadStatus.value = DamLoadStatus.SUCCESS
            _isLastLoadNetworkError.value = false
            if (effectiveDebugDataEndMillis != appSettings.debugRealtimeDataEndMillis) {
                catchNonCancellationSuspend {
                    settingsRepository.updateSettings {
                        it.copy(debugRealtimeDataEndMillis = effectiveDebugDataEndMillis)
                    }
                }.bind()
            }
            return@either parsed
        }

        if (appSettings.realtimeDataSource == RealtimeDataSource.SUDMONITOR) {
            val effectiveDamId = if (appSettings.targetDamId in SUDMONITOR_SUPPORTED_DAM_IDS) {
                appSettings.targetDamId
            } else {
                AppSettings.DEFAULT_DAM_ID
            }
            val effectiveConfig = if (effectiveDamId == appSettings.targetDamId) {
                config
            } else {
                catchNonCancellationSuspend {
                    damConfigProvider.get(effectiveDamId)
                }.bind()
            }
            val datUrl = "$SUDMONITOR_BASE_URL/v1/realtime/$effectiveDamId/latest.dat"
            val response = sudmonitorNetworkDataSource.fetchDat(datUrl).bind()
            response.damIdHeader?.let { headerDamId ->
                if (headerDamId != effectiveDamId) {
                    raise(Exception("X-TCS-Dam-Id header mismatch: expected $effectiveDamId but was $headerDamId."))
                }
            }
            val datBytes = response.bytes
            _lastRawDatBytes = datBytes
            _lastRealtimeRawDatBytes = datBytes
            _lastDatUrl = datUrl
            _lastRealtimeDatUrl = datUrl
            kotlin.runCatching {
                withContext(Dispatchers.IO) {
                    val datFile = File(context.cacheDir, "last_realtime_raw.dat")
                    val urlFile = File(context.cacheDir, "last_realtime_url.txt")
                    datFile.writeBytes(datBytes)
                    urlFile.writeText(datUrl)
                }
            }
            persistCurrentRawBestEffort(datBytes, datUrl)
            val parsed = parser.parseDatCsv(datBytes, effectiveConfig.id, effectiveConfig.nameJa).bind()
            val now = System.currentTimeMillis()
            val fetchTimeMillis = if (recordLastFetchTimeMillis) now else _lastFetchTimeMillis
            catchNonCancellationSuspend {
                damDao.insert(parsed.toEntity(fetchTimeMillis))
            }.bind()
            _lastFetchTimeMillis = fetchTimeMillis
            _isLastLoadNetworkError.value = false
            _loadStatus.value = DamLoadStatus.SUCCESS
            response.fetchedAtHeader?.let { fetchedAt ->
                runCatching { Instant.parse(fetchedAt).toEpochMilli() }.getOrNull()?.let { millis ->
                    catchNonCancellationSuspend {
                        settingsRepository.updateSettings { it.copy(originFetchedAtMillis = millis) }
                    }
                }
            }
            response.nextUpdateAtHeader?.let { nextUpdateAt ->
                runCatching { Instant.parse(nextUpdateAt).toEpochMilli() }.getOrNull()?.let { millis ->
                    catchNonCancellationSuspend {
                        settingsRepository.updateSettings { it.copy(manualRefreshAvailableAtMillis = millis) }
                    }
                }
            }
            return@either parsed
        }

        
        val htmlBytes = networkDataSource.fetchBytes(config.dataUrl).bind()
        val datUrl = parseLatestDatUrl(htmlBytes)
            .mapLeft { it.toThrowable() }
            .bind()

        
        val datBytes = networkDataSource.fetchBytes(datUrl).bind()

        
        _lastRawDatBytes = datBytes
        _lastRealtimeRawDatBytes = datBytes
        _lastDatUrl = datUrl
        _lastRealtimeDatUrl = datUrl
        
        // 最新データの生DAT cacheはデバッグ表示用のbest-effort保存で、取得成功条件には含めない。
        kotlin.runCatching {
            withContext(Dispatchers.IO) {
                val datFile = File(context.cacheDir, "last_realtime_raw.dat")
                val urlFile = File(context.cacheDir, "last_realtime_url.txt")
                datFile.writeBytes(datBytes)
                urlFile.writeText(datUrl)
            }
        }
        persistCurrentRawBestEffort(datBytes, datUrl)

        val parsed = parser.parseDatCsv(datBytes, config.id, config.nameJa).bind()
        val now = System.currentTimeMillis()
        val fetchTimeMillis = if (recordLastFetchTimeMillis) now else _lastFetchTimeMillis
        catchNonCancellationSuspend {
            damDao.insert(parsed.toEntity(fetchTimeMillis))
        }.bind()
        _lastFetchTimeMillis = fetchTimeMillis
        
        _isLastLoadNetworkError.value = false
        _loadStatus.value = DamLoadStatus.SUCCESS
        return@either parsed
        }
    } }

    /**
     * デバッグモードでのデータ自動進捗（時間の自動進捗機能）のための終了時間計算。
     *
     * 自動進捗が有効でかつ現在のデバッグ用終了時間が指定されている場合、
     * .datファイル内に含まれる日付データの中で、現在の終了時間より直近で未来の時間を次の終了時間候補として計算して返します。
     *
     * @param bytes .datファイルのバイト配列
     * @param currentEndMillis 現在のデバッグデータ終了時間（ミリ秒）
     * @param enabled 自動進捗機能が有効な場合はtrue
     * @return 進捗した新しい終了時間のミリ秒タイムスタンプ、または元の終了時間
     */
    private fun resolveAutoAdvancedDebugDataEndMillis(
        bytes: ByteArray,
        currentEndMillis: Long?,
        enabled: Boolean
    ): Either<Throwable, Long?> = either {
        if (!enabled || currentEndMillis == null) return@either currentEndMillis
        val dataTimes = parser.parseRealtimeDataTimes(bytes).bind()
        val maxMillis = dataTimes.lastOrNull() ?: return@either currentEndMillis
        if (currentEndMillis >= maxMillis) return@either currentEndMillis
        dataTimes.firstOrNull { it > currentEndMillis } ?: currentEndMillis
    }

    /** 現在表示中のsnapshotに対応するraw DATと名前をprocess再生成後も復元できるよう保存する。 */
    private suspend fun persistCurrentRawBestEffort(bytes: ByteArray, source: String?) {
        kotlin.runCatching {
            withContext(Dispatchers.IO) {
                File(context.cacheDir, "last_current_raw.dat").writeBytes(bytes)
                File(context.cacheDir, "last_current_url.txt").writeText(source ?: "export.dat")
            }
        }
    }

    /** `.dat` URL抽出の診断情報を内部errorに保持し、公開境界では既存メッセージへ変換する。 */
    private fun parseLatestDatUrl(htmlBytes: ByteArray): Either<DamDataError, String> =
        parser.parseHtmlForDatUrlResult(htmlBytes)
            .mapLeft { DamDataError.DatUrlNotFound(it) }

    private fun DamDataError.toThrowable(): Throwable =
        when (this) {
            is DamDataError.DatUrlNotFound -> Exception(context.getString(R.string.error_dat_url_not_found))
        }
}
