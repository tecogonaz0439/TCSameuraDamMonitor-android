// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.ui.settings

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import net.tecogonaz.tcsameuradammonitor.BuildConfig
import net.tecogonaz.tcsameuradammonitor.R
import net.tecogonaz.tcsameuradammonitor.data.source.local.DebugDatSourceReader
import net.tecogonaz.tcsameuradammonitor.domain.model.AppSettings
import net.tecogonaz.tcsameuradammonitor.domain.model.AppTheme
import net.tecogonaz.tcsameuradammonitor.domain.model.AutoUpdateInterval
import net.tecogonaz.tcsameuradammonitor.domain.model.AutoUpdateScheduler
import net.tecogonaz.tcsameuradammonitor.domain.model.DebugDatSelectionMode
import net.tecogonaz.tcsameuradammonitor.domain.model.DebugSimulateMode
import net.tecogonaz.tcsameuradammonitor.domain.model.RealtimeDataSource
import net.tecogonaz.tcsameuradammonitor.domain.model.StorageRateMessageCategory
import net.tecogonaz.tcsameuradammonitor.domain.repository.DatabaseMaintenanceRepository
import net.tecogonaz.tcsameuradammonitor.domain.repository.DamDataRepository
import net.tecogonaz.tcsameuradammonitor.domain.repository.SettingsRepository
import net.tecogonaz.tcsameuradammonitor.domain.repository.SudmonitorHistoryRepository
import net.tecogonaz.tcsameuradammonitor.domain.repository.DebugLogRepository
import net.tecogonaz.tcsameuradammonitor.domain.repository.DebugDataSessionRepository
import net.tecogonaz.tcsameuradammonitor.domain.repository.SudmonitorHistoryTrigger
import net.tecogonaz.tcsameuradammonitor.domain.model.getDamConfig
import net.tecogonaz.tcsameuradammonitor.util.TimeUtils
import net.tecogonaz.tcsameuradammonitor.util.CsvEncodingUtils
import net.tecogonaz.tcsameuradammonitor.worker.DamWorkManagerGateway
import net.tecogonaz.tcsameuradammonitor.worker.DamWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

enum class DebugDatKind {
    REALTIME,
    HISTORICAL_DAILY
}

/**
 * 設定画面（SettingsScreen）およびデバッグ設定画面（DebugScreen）のビジネスロジックを管理する [ViewModel] です。
 *
 * アプリのテーマ設定、初回読込や自動更新の間隔・スケジュール変更、
 * 閾値ごとの「貯水率メッセージ」やネットワークエラー等のカスタムメッセージ編集、
 * デバッグモードやシミュレートモード、テスト用の観測データ（.datファイル）読み込み設定などの状態保持と操作を提供します。
 *
 * @property settingsRepository アプリの設定情報（[AppSettings]）を永続化するリポジトリ。
 * @property damDataRepository ダム観測データ（リアルタイムデータなど）のリポジトリ。
 * @property databaseMaintenanceRepository データベースのVACUUMなどのメンテナンスを行うリポジトリ。
 * @property debugLogRepository アプリの動作ログ（デバッグログ）を記録・管理するリポジトリ。
 * @property damWorkManagerGateway 自動更新のバックグラウンドジョブ（WorkManager）をスケジューリングするゲートウェイ。
 * @property widgetUpdateRequester ウィジェット（App Widget）の更新要求を管理するクラス。
 * @property debugDatSourceReader デバッグ用の `.dat` ファイルを読み取るクラス。
 * @property displayNameResolver アプリ表示用の名前を解決するユーティリティ。
 * @property datExporter リアルタイムデータ `.dat` ファイルのエクスポートを行うクラス。
 * @property context アプリケーションコンテキスト。
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val damDataRepository: DamDataRepository,
    private val sudmonitorHistoryRepository: SudmonitorHistoryRepository,
    private val debugDataSessionRepository: DebugDataSessionRepository,
    private val databaseMaintenanceRepository: DatabaseMaintenanceRepository,
    private val debugLogRepository: DebugLogRepository,
    private val damWorkManagerGateway: DamWorkManagerGateway,
    private val widgetUpdateRequester: WidgetUpdateRequester,
    private val debugDatSourceReader: DebugDatSourceReader,
    private val displayNameResolver: DisplayNameResolver,
    private val datExporter: DatExporter,
    @ApplicationContext private val context: Context
) : ViewModel() {

    companion object {
        
        private const val MAX_DEBUG_EXPORT_FILE_SIZE = 10 * 1024 * 1024
    }

    val appSettings: StateFlow<AppSettings?> = settingsRepository.appSettingsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    private val _isDebugModeSwitching = MutableStateFlow(false)
    val isDebugModeSwitching: StateFlow<Boolean> = _isDebugModeSwitching.asStateFlow()
    private val _debugModeSwitchError = MutableStateFlow<String?>(null)
    val debugModeSwitchError: StateFlow<String?> = _debugModeSwitchError.asStateFlow()

    
    private val _isAppLocaleSystemDefault = MutableStateFlow(computeIsAppLocaleSystemDefault())
    val isAppLocaleSystemDefault: StateFlow<Boolean> = _isAppLocaleSystemDefault.asStateFlow()

    private val _pendingRealtimeDataSource = MutableStateFlow<RealtimeDataSource?>(null)
    val pendingRealtimeDataSource: StateFlow<RealtimeDataSource?> = _pendingRealtimeDataSource.asStateFlow()

    
    private fun computeIsAppLocaleSystemDefault(): Boolean =
        runCatching {
            context.getSystemService(android.app.LocaleManager::class.java)
                ?.applicationLocales
                ?.isEmpty
                ?: true
        }.getOrDefault(true)

    
    fun updateTheme(theme: AppTheme) {
        viewModelScope.launch {
            settingsRepository.updateSettings { it.copy(theme = theme) }
        }
    }

    
    fun updateUpdateOnBoot(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateSettings { it.copy(updateOnBoot = enabled) }
        }
    }

    
    fun updateAutoUpdateEnabled(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled) {
                val settings = settingsRepository.appSettingsFlow.first()
                val now = System.currentTimeMillis()
                val (rawNext, updatedA) = settings.calculateEffectiveNextRunTimeAndA(now)
                val effectiveNext = AutoUpdateScheduler.advanceIfWithinWindow(
                    settings.autoUpdateInterval, rawNext, now
                )
                
                settingsRepository.updateSettings { s ->
                    var updated = s.copy(
                        autoUpdateEnabled = true,
                        nextScheduledUpdateMillis = effectiveNext,
                        isFirstRunAfterReschedule = true
                    )
                    if (updatedA != s.currentCustomTimingMillis) {
                        updated = updated.withCustomTimingMillis(updatedA)
                    }
                    updated
                }
                val updatedSettings = settingsRepository.appSettingsFlow.first()
                damWorkManagerGateway.scheduleWorkAtTime(updatedSettings, effectiveNext)
                val intervalName = updatedSettings.autoUpdateInterval.name
                val nextStr = TimeUtils.formatToJstIso8601(effectiveNext)
                debugLogRepository.addEntry(
                    "Auto update enabled.",
                    "Interval: $intervalName, Next scheduled: $nextStr"
                )
            } else {
                settingsRepository.updateSettings { it.copy(autoUpdateEnabled = false, nextScheduledUpdateMillis = 0L) }
                damWorkManagerGateway.cancelWork()
                debugLogRepository.addEntry("Auto update disabled.")
            }
        }
    }

    
    fun updateShowNotification(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateSettings { it.copy(showNotification = enabled) }
        }
    }

    
    fun updateAutoUpdateInterval(interval: AutoUpdateInterval) {
        viewModelScope.launch {
            val prevSettings = settingsRepository.appSettingsFlow.first()
            val prevInterval = prevSettings.autoUpdateInterval
            
            val newSettings = prevSettings.copy(autoUpdateInterval = interval)
            val now = System.currentTimeMillis()
            val (rawNext, updatedA) = newSettings.calculateEffectiveNextRunTimeAndA(now)
            val effectiveNext = if (prevSettings.autoUpdateEnabled) {
                AutoUpdateScheduler.advanceIfWithinWindow(interval, rawNext, now)
            } else {
                rawNext
            }
            
            settingsRepository.updateSettings { s ->
                var updated = s.copy(autoUpdateInterval = interval)
                if (updatedA != newSettings.currentCustomTimingMillis) {
                    updated = updated.withCustomTimingMillis(updatedA)
                }
                if (s.autoUpdateEnabled) {
                    updated = updated.copy(
                        nextScheduledUpdateMillis = effectiveNext,
                        isFirstRunAfterReschedule = true
                    )
                }
                updated
            }
            val settings = settingsRepository.appSettingsFlow.first()
            if (settings.autoUpdateEnabled) {
                damWorkManagerGateway.scheduleWorkAtTime(settings, effectiveNext)
            }
            if (prevInterval != interval) {
                if (settings.autoUpdateEnabled) {
                    val nextStr = TimeUtils.formatToJstIso8601(effectiveNext)
                    debugLogRepository.addEntry(
                        "Auto update interval changed.",
                        "Interval: ${prevInterval.name} → ${interval.name}, Next scheduled: $nextStr"
                    )
                } else {
                    debugLogRepository.addEntry(
                        "Auto update interval changed.",
                        "Interval: ${prevInterval.name} → ${interval.name}"
                    )
                }
            }
        }
    }

    
    fun updateAutoUpdateCustomTiming(millis: Long) {
        viewModelScope.launch {
            val jstZdt = Instant.ofEpochMilli(millis).atZone(ZoneId.of("Asia/Tokyo"))
            val hour = jstZdt.hour
            val minute = jstZdt.minute
            val now = System.currentTimeMillis()
            
            settingsRepository.updateSettings { s ->
                val updated = AutoUpdateScheduler.recalculateAllCustomTimings(s, now, hour, minute)
                    .copy(nextScheduledUpdateMillis = millis)
                if (s.autoUpdateEnabled) updated.copy(isFirstRunAfterReschedule = true) else updated
            }
            val settings = settingsRepository.appSettingsFlow.first()
            if (settings.autoUpdateEnabled) {
                
                damWorkManagerGateway.scheduleWorkAtTime(settings, millis)
            }
            val formattedTime = TimeUtils.formatToJstIso8601(millis)
            debugLogRepository.addEntry(
                "Auto update next timing set.",
                "Interval: ${settings.autoUpdateInterval.name}, Next scheduled: $formattedTime"
            )
        }
    }

    /**
     * しきい値ごとの状態・メッセージ（貯水率メッセージまたはその他のメッセージ）を更新します。
     *
     * @param threshold しきい値（例: "80_100"、"initial_message"）
     * @param state メッセージの区分状態
     * @param messageNonJa 英語のメッセージ
     * @param messageJa 日本語のメッセージ
     * @param forOtherMessages 貯水率メッセージ以外（initial_message等）を更新するかどうか
     * @param category 貯水率メッセージを更新する場合の分類（[StorageRateMessageCategory]）
     */
    fun updateStateAndMessage(
        threshold: String,
        state: String,
        messageNonJa: String,
        messageJa: String,
        forOtherMessages: Boolean,
        category: StorageRateMessageCategory = StorageRateMessageCategory.SAMEURA
    ) {
        viewModelScope.launch {
            settingsRepository.updateSettings {
                if (forOtherMessages) {
                    when (threshold) {
                        "initial_message" -> it.copy(stateInitialMessage = state, msgInitialMessage = messageNonJa, msgInitialMessageJa = messageJa)
                        "network_unavailable" -> it.copy(stateNetworkUnavailable = state, msgNetworkUnavailable = messageNonJa, msgNetworkUnavailableJa = messageJa)
                        "loading_error" -> it.copy(stateLoadingError = state, msgLoadingError = messageNonJa, msgLoadingErrorJa = messageJa)
                        "data_distribution_stopped" -> it.copy(stateDataDistributionStopped = state, msgDataDistributionStopped = messageNonJa, msgDataDistributionStoppedJa = messageJa)
                        "data_distribution_resumed" -> it.copy(stateDataDistributionResumed = state, msgDataDistributionResumed = messageNonJa, msgDataDistributionResumedJa = messageJa)
                        else -> it
                    }
                } else {
                    it.withStorageRateLevel(threshold, state, messageNonJa, messageJa, category)
                }
            }
        }
    }

    
    fun needsStorageRateResetConfirmation(mode: String): Boolean {
        val current = appSettings.value ?: return false
        val afterDelete = current.withStorageRateLevelsReset(context, "delete")
        val afterJa = current.withStorageRateLevelsReset(context, "ja")
        val afterNonJa = current.withStorageRateLevelsReset(context, "non_ja")

        val currentLevels = current.storageRateLevels
        return currentLevels != afterDelete.storageRateLevels &&
            currentLevels != afterJa.storageRateLevels &&
            currentLevels != afterNonJa.storageRateLevels
    }

    /**
     * 早明浦ダム以外用の貯水率メッセージが一般向けプリセットと異なる場合にtrueを返します。
     */
    fun needsOtherStorageRateResetConfirmation(): Boolean {
        val current = appSettings.value ?: return false
        val afterReset = current.withStorageRateLevelsReset(
            context,
            "non_ja",
            StorageRateMessageCategory.OTHER
        )
        return current.otherStorageRateLevels != afterReset.otherStorageRateLevels
    }

    
    fun needsOtherMessagesResetConfirmation(): Boolean {
        val current = appSettings.value ?: return false
        return current.stateInitialMessage != context.getString(R.string.main_emoji_initial_message) ||
            current.msgInitialMessage != context.getString(R.string.storage_default_msg_non_ja_initial_message) ||
            current.msgInitialMessageJa != context.getString(R.string.storage_default_msg_ja_initial_message) ||
            current.stateNetworkUnavailable != context.getString(R.string.main_emoji_network_unavailable) ||
            current.msgNetworkUnavailable != context.getString(R.string.storage_default_msg_non_ja_network_unavailable) ||
            current.msgNetworkUnavailableJa != context.getString(R.string.storage_default_msg_ja_network_unavailable) ||
            current.stateLoadingError != context.getString(R.string.main_emoji_loading_error) ||
            current.msgLoadingError != context.getString(R.string.storage_default_msg_non_ja_loading_error) ||
            current.msgLoadingErrorJa != context.getString(R.string.storage_default_msg_ja_loading_error) ||
            current.stateDataDistributionStopped != context.getString(R.string.main_emoji_data_distribution_stopped) ||
            current.msgDataDistributionStopped != context.getString(R.string.storage_default_msg_non_ja_data_distribution_stopped) ||
            current.msgDataDistributionStoppedJa != context.getString(R.string.storage_default_msg_ja_data_distribution_stopped) ||
            current.stateDataDistributionResumed != context.getString(R.string.main_emoji_data_distribution_resumed) ||
            current.msgDataDistributionResumed != context.getString(R.string.storage_default_msg_non_ja_data_distribution_resumed) ||
            current.msgDataDistributionResumedJa != context.getString(R.string.storage_default_msg_ja_data_distribution_resumed)
    }

    
    fun resetStorageRateMessages(mode: String) {
        viewModelScope.launch {
            settingsRepository.updateSettings { it.withStorageRateLevelsReset(context, mode) }
        }
    }

    /**
     * 早明浦ダム以外用の貯水率メッセージを一般向けプリセットへリセットします。
     */
    fun resetOtherStorageRateMessages() {
        viewModelScope.launch {
            settingsRepository.updateSettings {
                it.withStorageRateLevelsReset(context, "non_ja", StorageRateMessageCategory.OTHER)
            }
        }
    }

    
    fun resetOtherMessages() {
        viewModelScope.launch {
            settingsRepository.updateSettings {
                it.copy(
                    stateInitialMessage = context.getString(R.string.main_emoji_initial_message),
                    msgInitialMessage = context.getString(R.string.storage_default_msg_non_ja_initial_message),
                    msgInitialMessageJa = context.getString(R.string.storage_default_msg_ja_initial_message),
                    stateNetworkUnavailable = context.getString(R.string.main_emoji_network_unavailable),
                    msgNetworkUnavailable = context.getString(R.string.storage_default_msg_non_ja_network_unavailable),
                    msgNetworkUnavailableJa = context.getString(R.string.storage_default_msg_ja_network_unavailable),
                    stateLoadingError = context.getString(R.string.main_emoji_loading_error),
                    msgLoadingError = context.getString(R.string.storage_default_msg_non_ja_loading_error),
                    msgLoadingErrorJa = context.getString(R.string.storage_default_msg_ja_loading_error),
                    stateDataDistributionStopped = context.getString(R.string.main_emoji_data_distribution_stopped),
                    msgDataDistributionStopped = context.getString(R.string.storage_default_msg_non_ja_data_distribution_stopped),
                    msgDataDistributionStoppedJa = context.getString(R.string.storage_default_msg_ja_data_distribution_stopped),
                    stateDataDistributionResumed = context.getString(R.string.main_emoji_data_distribution_resumed),
                    msgDataDistributionResumed = context.getString(R.string.storage_default_msg_non_ja_data_distribution_resumed),
                    msgDataDistributionResumedJa = context.getString(R.string.storage_default_msg_ja_data_distribution_resumed)
                )
            }
        }
    }

    fun updateShowStorageRateMessage(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateSettings { it.copy(showStorageRateMessage = enabled) }
        }
    }

    
    fun updateDebugModeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            if (_isDebugModeSwitching.value) return@launch
            _isDebugModeSwitching.value = true
            val result = if (enabled) {
                debugDataSessionRepository.enterDebugMode()
            } else {
                debugDataSessionRepository.exitDebugMode()
            }
            result.onFailure {
                _debugModeSwitchError.value = context.getString(
                    if (enabled) R.string.settings_debug_backup_failed else R.string.settings_debug_restore_failed
                )
            }
            _isDebugModeSwitching.value = false
        }
    }

    fun consumeDebugModeSwitchError() {
        _debugModeSwitchError.value = null
    }

    
    fun toggleDebugSettingsVisibility() {
        viewModelScope.launch {
            if (_isDebugModeSwitching.value) return@launch
            val before = settingsRepository.appSettingsFlow.first()
            if (before.debugSettingsVisible && before.debugModeEnabled) {
                _isDebugModeSwitching.value = true
                val restored = debugDataSessionRepository.exitDebugMode()
                _isDebugModeSwitching.value = false
                if (restored.isFailure) {
                    _debugModeSwitchError.value = context.getString(R.string.settings_debug_restore_failed)
                    return@launch
                }
            }
            settingsRepository.updateSettings { current ->
                val newVisible = !current.debugSettingsVisible
                when {
                    newVisible -> current.copy(debugSettingsVisible = true)
                    else -> current.copy(
                        debugSettingsVisible = false,
                        debugModeEnabled = false,
                        debugSimulateMode = DebugSimulateMode.NONE
                    )
                }
            }
        }
    }

    fun updateDebugRealtimeDatFileUri(uri: String?) {
        viewModelScope.launch {
            settingsRepository.updateSettings {
                it.copy(
                    debugRealtimeDatFileMode = if (uri == null) {
                        DebugDatSelectionMode.BUNDLED
                    } else {
                        DebugDatSelectionMode.USER_SELECTED
                    },
                    debugRealtimeDatFileUri = uri,
                    debugRealtimeDataStartMillis = null,
                    debugRealtimeDataEndMillis = null
                )
            }
        }
    }

    fun updateDebugRealtimeDatFileMode(mode: DebugDatSelectionMode) {
        viewModelScope.launch {
            settingsRepository.updateSettings {
                it.copy(
                    debugRealtimeDatFileMode = mode,
                    debugRealtimeDatFileUri = if (mode == DebugDatSelectionMode.USER_SELECTED) {
                        it.debugRealtimeDatFileUri
                    } else {
                        null
                    },
                    debugRealtimeDataStartMillis = null,
                    debugRealtimeDataEndMillis = null
                )
            }
        }
    }

    fun updateDebugHistoricalDailyDatFileUri(uri: String?) {
        viewModelScope.launch {
            settingsRepository.updateSettings {
                it.copy(
                    debugHistoricalDailyDatFileMode = if (uri == null) {
                        DebugDatSelectionMode.BUNDLED
                    } else {
                        DebugDatSelectionMode.USER_SELECTED
                    },
                    debugHistoricalDailyDatFileUri = uri
                )
            }
        }
    }

    fun updateDebugHistoricalDailyDatFileMode(mode: DebugDatSelectionMode) {
        viewModelScope.launch {
            settingsRepository.updateSettings {
                it.copy(
                    debugHistoricalDailyDatFileMode = mode,
                    debugHistoricalDailyDatFileUri = if (mode == DebugDatSelectionMode.USER_SELECTED) {
                        it.debugHistoricalDailyDatFileUri
                    } else {
                        null
                    }
                )
            }
        }
    }

    fun updateDebugRealtimeDataPeriod(startMillis: Long?, endMillis: Long?) {
        viewModelScope.launch {
            settingsRepository.updateSettings {
                it.copy(
                    debugRealtimeDataStartMillis = startMillis,
                    debugRealtimeDataEndMillis = endMillis
                )
            }
        }
    }

    fun updateDebugRealtimeDataPeriodAutoAdvanceEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateSettings {
                it.copy(debugRealtimeDataPeriodAutoAdvanceEnabled = enabled)
            }
        }
    }

    fun hasRealtimeDatFile(): Boolean = damDataRepository.getLastRealtimeRawDatBytes() != null
    fun hasCurrentRealtimeDatFile(): Boolean = damDataRepository.getLastRawDatBytes() != null

    suspend fun hasHistoricalDailyDatFile(): Boolean = withContext(Dispatchers.IO) {
        val damId = settingsRepository.appSettingsFlow.first().targetDamId
        sudmonitorHistoryRepository.hasCurrentRawDat(damId)
    }

    
    suspend fun resolveDisplayName(uriString: String?, defaultName: String): String = withContext(Dispatchers.IO) {
        displayNameResolver.resolve(uriString, defaultName)
    }

    suspend fun resolveDebugDataTimes(settings: AppSettings): List<Long> = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = resolveDebugSourceBytes(settings) ?: return@runCatching emptyList()
            CsvEncodingUtils.decodeWithAutoEncoding(bytes)
                .lines()
                .mapNotNull { line ->
                    if (line.startsWith("#")) return@mapNotNull null
                    val row = line.trim().split(",")
                    if (row.size <= 5) return@mapNotNull null
                    val date = row.getOrNull(0)?.trim().orEmpty()
                    val time = row.getOrNull(1)?.trim().orEmpty()
                    TimeUtils.parseJstMillisAllow24Hour("$date $time", "yyyy/MM/dd HH:mm")
                }
                .distinct()
                .sorted()
        }.getOrDefault(emptyList())
    }

    private fun resolveDebugSourceBytes(settings: AppSettings): ByteArray? =
        when (settings.debugRealtimeDatFileMode) {
            DebugDatSelectionMode.BUNDLED -> debugDatSourceReader.readBundled(DebugDatSelectionMode.BUNDLED_FILE_NAME)
            DebugDatSelectionMode.LATEST -> damDataRepository.getLastRealtimeRawDatBytes()
            DebugDatSelectionMode.USER_SELECTED -> {
                val uriString = settings.debugRealtimeDatFileUri ?: return null
                debugDatSourceReader.readSaf(
                    uriString = uriString,
                    maxSizeBytes = MAX_DEBUG_EXPORT_FILE_SIZE,
                    tooLargeMessage = context.getString(R.string.error_debug_file_too_large),
                    readErrorMessage = context.getString(R.string.error_debug_file_read)
                )
            }
        }

    
    fun updateDebugSimulateMode(mode: DebugSimulateMode) {
        viewModelScope.launch {
            settingsRepository.updateSettings { it.copy(debugSimulateMode = mode) }
        }
    }

    suspend fun vacuumDatabase(): String = try {
        val result = databaseMaintenanceRepository.vacuumDatabase()
        context.getString(
            R.string.settings_database_vacuum_done,
            formatByteSize(result.beforeSizeBytes),
            formatByteSize(result.afterSizeBytes)
        )
    } catch (e: Exception) {
        if (BuildConfig.DEBUG) {
            Log.e("SettingsViewModel", "Failed to vacuum database", e)
        }
        context.getString(R.string.settings_database_vacuum_failed)
    }

    private fun formatByteSize(bytes: Long): String {
        val kib = 1024.0
        val mib = kib * 1024.0
        return when {
            bytes >= mib -> String.format(Locale.US, "%.1f MB", bytes / mib)
            bytes >= kib -> String.format(Locale.US, "%.1f KB", bytes / kib)
            else -> context.getString(R.string.settings_database_size_bytes, bytes)
        }
    }

    
    suspend fun resolveExportFileName(kind: DebugDatKind, useUtf8: Boolean): String = withContext(Dispatchers.IO) {
        val settings = settingsRepository.appSettingsFlow.first()
        val baseName = when (kind) {
            DebugDatKind.REALTIME -> damDataRepository.getLastDatFileName()
            DebugDatKind.HISTORICAL_DAILY -> {
                sudmonitorHistoryRepository.getCurrentRawDatFileName(settings.targetDamId)
            }
        } ?: when (kind) {
            DebugDatKind.REALTIME -> "realtime.dat"
            DebugDatKind.HISTORICAL_DAILY -> "historical_daily.dat"
        }
        if (useUtf8) {
            baseName.substringBeforeLast(".") + "_utf8.dat"
        } else {
            baseName
        }
    }

    
    suspend fun exportDatFile(kind: DebugDatKind, outputUri: Uri, useUtf8: Boolean): String? = withContext(Dispatchers.IO) {
        try {
            val settings = settingsRepository.appSettingsFlow.first()
            val sourceBytes: ByteArray = when (kind) {
                DebugDatKind.REALTIME -> damDataRepository.getLastRawDatBytes()
                DebugDatKind.HISTORICAL_DAILY -> {
                    sudmonitorHistoryRepository.getCurrentRawDatBytes(settings.targetDamId)
                }
            } ?: return@withContext context.getString(R.string.settings_debug_export_no_data)

            val outputBytes: ByteArray = if (useUtf8) {
                CsvEncodingUtils.encodeWithBom(CsvEncodingUtils.decodeWithAutoEncoding(sourceBytes))
            } else {
                sourceBytes
            }
            
            if (!datExporter.write(outputUri, outputBytes)) {
                return@withContext context.getString(R.string.error_file_write)
            }
            null
        } catch (e: Exception) {
            e.message ?: context.getString(R.string.error_file_write)
        }
    }

    
    fun updateRealtimeDataSource(source: RealtimeDataSource) {
        viewModelScope.launch {
            val settings = settingsRepository.appSettingsFlow.first()
            if (settings.realtimeDataSource == source) return@launch
            if (source == RealtimeDataSource.SUDMONITOR &&
                settings.targetDamId != AppSettings.DEFAULT_DAM_ID
            ) {
                _pendingRealtimeDataSource.value = source
                return@launch
            }
            settingsRepository.updateSettings { it.copy(realtimeDataSource = source) }
        }
    }

    fun confirmRealtimeDataSourceChange() {
        val source = _pendingRealtimeDataSource.value ?: return
        _pendingRealtimeDataSource.value = null
        changeTargetDam(AppSettings.DEFAULT_DAM_ID) {
            viewModelScope.launch {
                settingsRepository.updateSettings { it.copy(realtimeDataSource = source) }
            }
        }
    }

    /**
     * 過去データの取得データソースを更新します。
     *
     * @param source 設定する過去データのデータソース（[RealtimeDataSource]）
     */
    fun updateHistoricalDataSource(source: RealtimeDataSource) {
        viewModelScope.launch {
            val settings = settingsRepository.appSettingsFlow.first()
            if (settings.historicalDataSource == source) return@launch
            settingsRepository.updateSettings { it.copy(historicalDataSource = source) }
        }
    }

    fun cancelRealtimeDataSourceChange() {
        _pendingRealtimeDataSource.value = null
    }

    fun changeTargetDam(id: String, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            val prevSettings = settingsRepository.appSettingsFlow.first()
            if (prevSettings.targetDamId != id) {
                val prevDamNameEn = getDamConfig(prevSettings.targetDamId).nameEn
                val newDamNameEn = getDamConfig(id).nameEn
                if (BuildConfig.DEBUG) {
                    Log.d("SettingsViewModel", "Dam changed: $prevDamNameEn -> $newDamNameEn")
                }
                debugLogRepository.addEntry("Dam changed.", "Dam name: $prevDamNameEn → $newDamNameEn")
                settingsRepository.updateSettings { it.copy(targetDamId = id, lastLoadResultMessage = "", wasLastDataAllInvalid = false) }
                damDataRepository.clearData()
                widgetUpdateRequester.updateAllWidgetsImmediately()
                damWorkManagerGateway.replaceOneTimeWork(DamWorker.WORK_TYPE_INITIAL)
                // ダム変更時は日次過去データも常に再取得・上書きする（D3）。
                // 機能ゲート（過去データの取得元が sudmonitor）が有効な場合のみ実行する。
                val updatedSettings = settingsRepository.appSettingsFlow.first()
                if (updatedSettings.historicalDataSource == RealtimeDataSource.SUDMONITOR) {
                    damWorkManagerGateway.replaceSudmonitorHistoryWork(SudmonitorHistoryTrigger.TARGET_CHANGE)
                }
            }
            onComplete()
        }
    }

    
    fun refreshForLocaleChange() {
        viewModelScope.launch {
            settingsRepository.invalidateSettingsCache()
            _isAppLocaleSystemDefault.value = computeIsAppLocaleSystemDefault()
        }
    }
}
