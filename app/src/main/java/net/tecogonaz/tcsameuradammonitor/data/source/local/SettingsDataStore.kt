// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.data.source.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStore
import net.tecogonaz.tcsameuradammonitor.BuildConfig
import net.tecogonaz.tcsameuradammonitor.R
import net.tecogonaz.tcsameuradammonitor.domain.model.AppSettings
import net.tecogonaz.tcsameuradammonitor.domain.model.AppTheme
import net.tecogonaz.tcsameuradammonitor.domain.model.AutoUpdateInterval
import net.tecogonaz.tcsameuradammonitor.domain.model.DebugDatSelectionMode
import net.tecogonaz.tcsameuradammonitor.domain.model.DebugSimulateMode
import net.tecogonaz.tcsameuradammonitor.domain.model.MainCardExpansionKey
import net.tecogonaz.tcsameuradammonitor.domain.model.MainCardExpansionState
import net.tecogonaz.tcsameuradammonitor.domain.model.RealtimeDataSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")


/**
 * Jetpack Preferences DataStore を使用して、アプリケーション設定（テーマ、自動更新、貯水率メッセージ等）を
 * ローカルストレージに永続化し、メモリキャッシュ同期制御を含む統一されたインターフェースを提供するデータストア。
 */
@Singleton
class SettingsDataStore private constructor(
    private val context: Context,
    private val dataStore: DataStore<Preferences>
) {
    /**
     * アプリケーションコンテキストからDataStoreインスタンスを初期化するパブリックコンストラクタ。
     *
     * @param context アプリケーションコンテキスト
     */
    @Inject
    constructor(@ApplicationContext context: Context) : this(context, context.dataStore)

    
    private val refreshTrigger = MutableSharedFlow<Unit>(replay = 1, extraBufferCapacity = 1).also { it.tryEmit(Unit) }

    companion object {
        fun createForTest(context: Context, dataStoreFileName: String): SettingsDataStore =
            SettingsDataStore(
                context,
                PreferenceDataStoreFactory.create(
                    produceFile = { File(context.filesDir, "datastore/$dataStoreFileName.preferences_pb") }
                )
            )

        val THEME_KEY = stringPreferencesKey("app_theme")
        val AUTO_UPDATE_ENABLED = booleanPreferencesKey("auto_update_enabled")
        val SHOW_NOTIFICATION = booleanPreferencesKey("show_notification")
        val AUTO_UPDATE_INTERVAL = stringPreferencesKey("auto_update_interval")
        
        val INITIAL_AUTO_UPDATE_DIALOG_SHOWN = booleanPreferencesKey("initial_auto_update_dialog_shown")
        
        val AUTO_UPDATE_CUSTOM_TIMING_MILLIS_WEEKLY = longPreferencesKey("auto_update_custom_timing_millis_weekly")
        
        val AUTO_UPDATE_CUSTOM_TIMING_MILLIS_DAILY = longPreferencesKey("auto_update_custom_timing_millis_daily")
        
        val AUTO_UPDATE_CUSTOM_TIMING_MILLIS_12H = longPreferencesKey("auto_update_custom_timing_millis_12h")
        
        val AUTO_UPDATE_CUSTOM_TIMING_MILLIS_HOURLY = longPreferencesKey("auto_update_custom_timing_millis_hourly")

        
        val SHOW_STORAGE_RATE_MESSAGE = booleanPreferencesKey("show_storage_rate_message")

        val STATE_80_100 = stringPreferencesKey("state_80_100")
        val MSG_80_100 = stringPreferencesKey("msg_80_100")
        val MSG_80_100_JA = stringPreferencesKey("msg_80_100_ja")
        val STATE_60_80 = stringPreferencesKey("state_60_80")
        val MSG_60_80 = stringPreferencesKey("msg_60_80")
        val MSG_60_80_JA = stringPreferencesKey("msg_60_80_ja")
        val STATE_40_60 = stringPreferencesKey("state_40_60")
        val MSG_40_60 = stringPreferencesKey("msg_40_60")
        val MSG_40_60_JA = stringPreferencesKey("msg_40_60_ja")
        val STATE_20_40 = stringPreferencesKey("state_20_40")
        val MSG_20_40 = stringPreferencesKey("msg_20_40")
        val MSG_20_40_JA = stringPreferencesKey("msg_20_40_ja")
        val STATE_0_20 = stringPreferencesKey("state_0_20")
        val MSG_0_20 = stringPreferencesKey("msg_0_20")
        val MSG_0_20_JA = stringPreferencesKey("msg_0_20_ja")
        val STATE_0 = stringPreferencesKey("state_0")
        val MSG_0 = stringPreferencesKey("msg_0")
        val MSG_0_JA = stringPreferencesKey("msg_0_ja")

        val STATE_ALL_ABNORMAL = stringPreferencesKey("state_all_abnormal")
        val MSG_ALL_ABNORMAL = stringPreferencesKey("msg_all_abnormal")
        val MSG_ALL_ABNORMAL_JA = stringPreferencesKey("msg_all_abnormal_ja")

        val STATE_ALL_DATA_INVALID = stringPreferencesKey("state_all_data_invalid")
        val MSG_ALL_DATA_INVALID = stringPreferencesKey("msg_all_data_invalid")
        val MSG_ALL_DATA_INVALID_JA = stringPreferencesKey("msg_all_data_invalid_ja")

        val OTHER_STATE_80_100 = stringPreferencesKey("other_state_80_100")
        val OTHER_MSG_80_100 = stringPreferencesKey("other_msg_80_100")
        val OTHER_MSG_80_100_JA = stringPreferencesKey("other_msg_80_100_ja")
        val OTHER_STATE_60_80 = stringPreferencesKey("other_state_60_80")
        val OTHER_MSG_60_80 = stringPreferencesKey("other_msg_60_80")
        val OTHER_MSG_60_80_JA = stringPreferencesKey("other_msg_60_80_ja")
        val OTHER_STATE_40_60 = stringPreferencesKey("other_state_40_60")
        val OTHER_MSG_40_60 = stringPreferencesKey("other_msg_40_60")
        val OTHER_MSG_40_60_JA = stringPreferencesKey("other_msg_40_60_ja")
        val OTHER_STATE_20_40 = stringPreferencesKey("other_state_20_40")
        val OTHER_MSG_20_40 = stringPreferencesKey("other_msg_20_40")
        val OTHER_MSG_20_40_JA = stringPreferencesKey("other_msg_20_40_ja")
        val OTHER_STATE_0_20 = stringPreferencesKey("other_state_0_20")
        val OTHER_MSG_0_20 = stringPreferencesKey("other_msg_0_20")
        val OTHER_MSG_0_20_JA = stringPreferencesKey("other_msg_0_20_ja")
        val OTHER_STATE_0 = stringPreferencesKey("other_state_0")
        val OTHER_MSG_0 = stringPreferencesKey("other_msg_0")
        val OTHER_MSG_0_JA = stringPreferencesKey("other_msg_0_ja")
        val OTHER_STATE_ALL_ABNORMAL = stringPreferencesKey("other_state_all_abnormal")
        val OTHER_MSG_ALL_ABNORMAL = stringPreferencesKey("other_msg_all_abnormal")
        val OTHER_MSG_ALL_ABNORMAL_JA = stringPreferencesKey("other_msg_all_abnormal_ja")
        val OTHER_STATE_ALL_DATA_INVALID = stringPreferencesKey("other_state_all_data_invalid")
        val OTHER_MSG_ALL_DATA_INVALID = stringPreferencesKey("other_msg_all_data_invalid")
        val OTHER_MSG_ALL_DATA_INVALID_JA = stringPreferencesKey("other_msg_all_data_invalid_ja")

        val STATE_INITIAL_MESSAGE = stringPreferencesKey("state_initial_message")
        val MSG_INITIAL_MESSAGE = stringPreferencesKey("msg_initial_message")
        val MSG_INITIAL_MESSAGE_JA = stringPreferencesKey("msg_initial_message_ja")

        val STATE_NETWORK_UNAVAILABLE = stringPreferencesKey("state_network_unavailable")
        val MSG_NETWORK_UNAVAILABLE = stringPreferencesKey("msg_network_unavailable")
        val MSG_NETWORK_UNAVAILABLE_JA = stringPreferencesKey("msg_network_unavailable_ja")

        val STATE_LOADING_ERROR = stringPreferencesKey("state_loading_error")
        val MSG_LOADING_ERROR = stringPreferencesKey("msg_loading_error")
        val MSG_LOADING_ERROR_JA = stringPreferencesKey("msg_loading_error_ja")

        val STATE_DATA_DISTRIBUTION_STOPPED = stringPreferencesKey("state_data_distribution_stopped")
        val MSG_DATA_DISTRIBUTION_STOPPED = stringPreferencesKey("msg_data_distribution_stopped")
        val MSG_DATA_DISTRIBUTION_STOPPED_JA = stringPreferencesKey("msg_data_distribution_stopped_ja")

        val STATE_DATA_DISTRIBUTION_RESUMED = stringPreferencesKey("state_data_distribution_resumed")
        val MSG_DATA_DISTRIBUTION_RESUMED = stringPreferencesKey("msg_data_distribution_resumed")
        val MSG_DATA_DISTRIBUTION_RESUMED_JA = stringPreferencesKey("msg_data_distribution_resumed_ja")

        val DEBUG_SETTINGS_VISIBLE = booleanPreferencesKey("debug_settings_visible")
        val DEBUG_MODE_ENABLED = booleanPreferencesKey("debug_mode_enabled")
        val DEBUG_REALTIME_DAT_FILE_MODE = stringPreferencesKey("debug_realtime_dat_file_mode")
        val DEBUG_REALTIME_DAT_FILE_URI = stringPreferencesKey("debug_realtime_dat_file_uri")
        val DEBUG_HISTORICAL_DAILY_DAT_FILE_MODE = stringPreferencesKey("debug_historical_daily_dat_file_mode")
        val DEBUG_HISTORICAL_DAILY_DAT_FILE_URI = stringPreferencesKey("debug_historical_daily_dat_file_uri")
        val DEBUG_REALTIME_DATA_START_MILLIS = longPreferencesKey("debug_realtime_data_start_millis")
        val DEBUG_REALTIME_DATA_END_MILLIS = longPreferencesKey("debug_realtime_data_end_millis")
        val DEBUG_SIMULATE_MODE = stringPreferencesKey("debug_simulate_mode")
        val DEBUG_REALTIME_DATA_PERIOD_AUTO_ADVANCE_ENABLED = booleanPreferencesKey("debug_realtime_data_period_auto_advance_enabled")

        val LAST_BOOT_TIME = longPreferencesKey("last_boot_time")
        val UPDATE_ON_BOOT = booleanPreferencesKey("update_on_boot")
        val TARGET_DAM_ID = stringPreferencesKey("target_dam_id")
        val REALTIME_DATA_SOURCE = stringPreferencesKey("realtime_data_source")
        val HISTORICAL_DATA_SOURCE = stringPreferencesKey("historical_data_source")
        val LAST_ORIGIN_FETCHED_AT_MILLIS = longPreferencesKey("last_origin_fetched_at_millis")
        val MANUAL_REFRESH_AVAILABLE_AT_MILLIS = longPreferencesKey("manual_refresh_available_at_millis")
        val WAS_LAST_DATA_ALL_INVALID = booleanPreferencesKey("was_last_data_all_invalid")
        val LAST_LOAD_RESULT_MESSAGE = stringPreferencesKey("last_load_result_message")
        val LAST_AUTO_UPDATE_MILLIS = longPreferencesKey("last_auto_update_millis")
        val NEXT_SCHEDULED_UPDATE_MILLIS = longPreferencesKey("next_scheduled_update_millis")
        val IS_FIRST_RUN_AFTER_RESCHEDULE = booleanPreferencesKey("is_first_run_after_reschedule")

        val MAIN_REALTIME_OBSERVATION_EXPANDED =
            booleanPreferencesKey("main_realtime_observation_card_expanded")
        val MAIN_REALTIME_LATEST_EXPANDED =
            booleanPreferencesKey("main_realtime_latest_card_expanded")
        val MAIN_REALTIME_HISTORY_EXPANDED =
            booleanPreferencesKey("main_realtime_history_card_expanded")
        val MAIN_REALTIME_GRAPH_EXPANDED =
            booleanPreferencesKey("main_realtime_graph_card_expanded")
        val MAIN_REALTIME_LINKS_EXPANDED =
            booleanPreferencesKey("main_realtime_links_card_expanded")
        val MAIN_HISTORICAL_OBSERVATION_EXPANDED =
            booleanPreferencesKey("main_historical_observation_card_expanded")
        val MAIN_HISTORICAL_HISTORY_EXPANDED =
            booleanPreferencesKey("main_historical_history_card_expanded")
        val MAIN_HISTORICAL_GRAPH_EXPANDED =
            booleanPreferencesKey("main_historical_graph_card_expanded")
        val MAIN_HISTORICAL_LINKS_EXPANDED =
            booleanPreferencesKey("main_historical_links_card_expanded")

        const val DEFAULT_THEME = "SYSTEM"
    }

    
    private fun buildAppSettings(prefs: Preferences): AppSettings {
        val debugSettingsVisible = prefs[DEBUG_SETTINGS_VISIBLE] ?: false
        val debugModeEnabled = BuildConfig.DEBUG && (prefs[DEBUG_MODE_ENABLED] ?: false)
        val debugSimulateMode = if (BuildConfig.DEBUG) {
            prefs[DEBUG_SIMULATE_MODE]
                ?.let { runCatching { DebugSimulateMode.valueOf(it) }.getOrDefault(DebugSimulateMode.NONE) }
                ?: DebugSimulateMode.NONE
        } else {
            DebugSimulateMode.NONE
        }
        fun debugDatSelectionMode(key: Preferences.Key<String>): DebugDatSelectionMode =
            if (BuildConfig.DEBUG) {
                prefs[key]
                    ?.let { runCatching { DebugDatSelectionMode.valueOf(it) }.getOrDefault(DebugDatSelectionMode.BUNDLED) }
                    ?: DebugDatSelectionMode.BUNDLED
            } else {
                DebugDatSelectionMode.BUNDLED
            }
        return AppSettings(
            theme = prefs[THEME_KEY]
                ?.let { runCatching { AppTheme.valueOf(it) }.getOrNull() }
                ?: AppTheme.SYSTEM,
            autoUpdateEnabled = prefs[AUTO_UPDATE_ENABLED] ?: false,
            showNotification = prefs[SHOW_NOTIFICATION] ?: true,
            autoUpdateInterval = prefs[AUTO_UPDATE_INTERVAL]?.let {
                runCatching { AutoUpdateInterval.valueOf(it) }.getOrNull()
            } ?: AutoUpdateInterval.ONE_WEEK,
            initialAutoUpdateDialogShown = prefs[INITIAL_AUTO_UPDATE_DIALOG_SHOWN] ?: false,
            autoUpdateCustomTimingMillisWeekly = prefs[AUTO_UPDATE_CUSTOM_TIMING_MILLIS_WEEKLY] ?: 0L,
            autoUpdateCustomTimingMillisDaily = prefs[AUTO_UPDATE_CUSTOM_TIMING_MILLIS_DAILY] ?: 0L,
            autoUpdateCustomTimingMillis12Hours = prefs[AUTO_UPDATE_CUSTOM_TIMING_MILLIS_12H] ?: 0L,
            autoUpdateCustomTimingMillisHourly = prefs[AUTO_UPDATE_CUSTOM_TIMING_MILLIS_HOURLY] ?: 0L,
            showStorageRateMessage = prefs[SHOW_STORAGE_RATE_MESSAGE] ?: true,
            
            state80_100 = prefs[STATE_80_100] ?: context.getString(R.string.main_emoji_80_100),
            msg80_100 = prefs[MSG_80_100] ?: context.getString(R.string.storage_default_msg_non_ja_80_100),
            msg80_100Ja = prefs[MSG_80_100_JA] ?: context.getString(R.string.storage_default_msg_ja_80_100),
            state60_80 = prefs[STATE_60_80] ?: context.getString(R.string.main_emoji_60_80),
            msg60_80 = prefs[MSG_60_80] ?: context.getString(R.string.storage_default_msg_non_ja_60_80),
            msg60_80Ja = prefs[MSG_60_80_JA] ?: context.getString(R.string.storage_default_msg_ja_60_80),
            state40_60 = prefs[STATE_40_60] ?: context.getString(R.string.main_emoji_40_60),
            msg40_60 = prefs[MSG_40_60] ?: context.getString(R.string.storage_default_msg_non_ja_40_60),
            msg40_60Ja = prefs[MSG_40_60_JA] ?: context.getString(R.string.storage_default_msg_ja_40_60),
            state20_40 = prefs[STATE_20_40] ?: context.getString(R.string.main_emoji_20_40),
            msg20_40 = prefs[MSG_20_40] ?: context.getString(R.string.storage_default_msg_non_ja_20_40),
            msg20_40Ja = prefs[MSG_20_40_JA] ?: context.getString(R.string.storage_default_msg_ja_20_40),
            state0_20 = prefs[STATE_0_20] ?: context.getString(R.string.main_emoji_0_20),
            msg0_20 = prefs[MSG_0_20] ?: context.getString(R.string.storage_default_msg_non_ja_0_20),
            msg0_20Ja = prefs[MSG_0_20_JA] ?: context.getString(R.string.storage_default_msg_ja_0_20),
            state0 = prefs[STATE_0] ?: context.getString(R.string.main_emoji_0),
            msg0 = prefs[MSG_0] ?: context.getString(R.string.storage_default_msg_non_ja_0),
            msg0Ja = prefs[MSG_0_JA] ?: context.getString(R.string.storage_default_msg_ja_0),
            stateAllAbnormal = prefs[STATE_ALL_ABNORMAL] ?: context.getString(R.string.main_emoji_all_abnormal),
            msgAllAbnormal = prefs[MSG_ALL_ABNORMAL] ?: context.getString(R.string.storage_default_msg_non_ja_all_abnormal),
            msgAllAbnormalJa = prefs[MSG_ALL_ABNORMAL_JA] ?: context.getString(R.string.storage_default_msg_ja_all_abnormal),
            stateAllDataInvalid = prefs[STATE_ALL_DATA_INVALID] ?: context.getString(R.string.main_emoji_all_data_invalid),
            msgAllDataInvalid = prefs[MSG_ALL_DATA_INVALID] ?: context.getString(R.string.storage_default_msg_non_ja_all_data_invalid),
            msgAllDataInvalidJa = prefs[MSG_ALL_DATA_INVALID_JA] ?: context.getString(R.string.storage_default_msg_ja_all_data_invalid),
            
            otherState80_100 = prefs[OTHER_STATE_80_100] ?: context.getString(R.string.main_emoji_80_100),
            otherMsg80_100 = prefs[OTHER_MSG_80_100] ?: context.getString(R.string.storage_default_msg_non_ja_80_100),
            otherMsg80_100Ja = prefs[OTHER_MSG_80_100_JA] ?: context.getString(R.string.storage_default_msg_non_ja_80_100),
            otherState60_80 = prefs[OTHER_STATE_60_80] ?: context.getString(R.string.main_emoji_60_80),
            otherMsg60_80 = prefs[OTHER_MSG_60_80] ?: context.getString(R.string.storage_default_msg_non_ja_60_80),
            otherMsg60_80Ja = prefs[OTHER_MSG_60_80_JA] ?: context.getString(R.string.storage_default_msg_non_ja_60_80),
            otherState40_60 = prefs[OTHER_STATE_40_60] ?: context.getString(R.string.main_emoji_40_60),
            otherMsg40_60 = prefs[OTHER_MSG_40_60] ?: context.getString(R.string.storage_default_msg_non_ja_40_60),
            otherMsg40_60Ja = prefs[OTHER_MSG_40_60_JA] ?: context.getString(R.string.storage_default_msg_non_ja_40_60),
            otherState20_40 = prefs[OTHER_STATE_20_40] ?: context.getString(R.string.main_emoji_20_40),
            otherMsg20_40 = prefs[OTHER_MSG_20_40] ?: context.getString(R.string.storage_default_msg_non_ja_20_40),
            otherMsg20_40Ja = prefs[OTHER_MSG_20_40_JA] ?: context.getString(R.string.storage_default_msg_non_ja_20_40),
            otherState0_20 = prefs[OTHER_STATE_0_20] ?: context.getString(R.string.main_emoji_0_20),
            otherMsg0_20 = prefs[OTHER_MSG_0_20] ?: context.getString(R.string.storage_default_msg_non_ja_0_20),
            otherMsg0_20Ja = prefs[OTHER_MSG_0_20_JA] ?: context.getString(R.string.storage_default_msg_non_ja_0_20),
            otherState0 = prefs[OTHER_STATE_0] ?: context.getString(R.string.main_emoji_0),
            otherMsg0 = prefs[OTHER_MSG_0] ?: context.getString(R.string.storage_default_msg_non_ja_0),
            otherMsg0Ja = prefs[OTHER_MSG_0_JA] ?: context.getString(R.string.storage_default_msg_non_ja_0),
            otherStateAllAbnormal = prefs[OTHER_STATE_ALL_ABNORMAL] ?: context.getString(R.string.main_emoji_all_abnormal),
            otherMsgAllAbnormal = prefs[OTHER_MSG_ALL_ABNORMAL] ?: context.getString(R.string.storage_default_msg_non_ja_all_abnormal),
            otherMsgAllAbnormalJa = prefs[OTHER_MSG_ALL_ABNORMAL_JA] ?: context.getString(R.string.storage_default_msg_ja_all_abnormal),
            otherStateAllDataInvalid = prefs[OTHER_STATE_ALL_DATA_INVALID] ?: context.getString(R.string.main_emoji_all_data_invalid),
            otherMsgAllDataInvalid = prefs[OTHER_MSG_ALL_DATA_INVALID] ?: context.getString(R.string.storage_default_msg_non_ja_all_data_invalid),
            otherMsgAllDataInvalidJa = prefs[OTHER_MSG_ALL_DATA_INVALID_JA] ?: context.getString(R.string.storage_default_msg_ja_all_data_invalid),
            
            stateInitialMessage = prefs[STATE_INITIAL_MESSAGE] ?: context.getString(R.string.main_emoji_initial_message),
            msgInitialMessage = prefs[MSG_INITIAL_MESSAGE] ?: context.getString(R.string.storage_default_msg_non_ja_initial_message),
            msgInitialMessageJa = prefs[MSG_INITIAL_MESSAGE_JA] ?: context.getString(R.string.storage_default_msg_ja_initial_message),
            stateNetworkUnavailable = prefs[STATE_NETWORK_UNAVAILABLE] ?: context.getString(R.string.main_emoji_network_unavailable),
            msgNetworkUnavailable = prefs[MSG_NETWORK_UNAVAILABLE] ?: context.getString(R.string.storage_default_msg_non_ja_network_unavailable),
            msgNetworkUnavailableJa = prefs[MSG_NETWORK_UNAVAILABLE_JA] ?: context.getString(R.string.storage_default_msg_ja_network_unavailable),
            stateLoadingError = prefs[STATE_LOADING_ERROR] ?: context.getString(R.string.main_emoji_loading_error),
            msgLoadingError = prefs[MSG_LOADING_ERROR] ?: context.getString(R.string.storage_default_msg_non_ja_loading_error),
            msgLoadingErrorJa = prefs[MSG_LOADING_ERROR_JA] ?: context.getString(R.string.storage_default_msg_ja_loading_error),
            stateDataDistributionStopped = prefs[STATE_DATA_DISTRIBUTION_STOPPED] ?: context.getString(R.string.main_emoji_data_distribution_stopped),
            msgDataDistributionStopped = prefs[MSG_DATA_DISTRIBUTION_STOPPED] ?: context.getString(R.string.storage_default_msg_non_ja_data_distribution_stopped),
            msgDataDistributionStoppedJa = prefs[MSG_DATA_DISTRIBUTION_STOPPED_JA] ?: context.getString(R.string.storage_default_msg_ja_data_distribution_stopped),
            stateDataDistributionResumed = prefs[STATE_DATA_DISTRIBUTION_RESUMED] ?: context.getString(R.string.main_emoji_data_distribution_resumed),
            msgDataDistributionResumed = prefs[MSG_DATA_DISTRIBUTION_RESUMED] ?: context.getString(R.string.storage_default_msg_non_ja_data_distribution_resumed),
            msgDataDistributionResumedJa = prefs[MSG_DATA_DISTRIBUTION_RESUMED_JA] ?: context.getString(R.string.storage_default_msg_ja_data_distribution_resumed),
            debugSettingsVisible = debugSettingsVisible,
            debugModeEnabled = debugModeEnabled,
            debugRealtimeDatFileMode = debugDatSelectionMode(DEBUG_REALTIME_DAT_FILE_MODE),
            debugRealtimeDatFileUri = prefs[DEBUG_REALTIME_DAT_FILE_URI].takeIf { BuildConfig.DEBUG },
            debugHistoricalDailyDatFileMode = debugDatSelectionMode(DEBUG_HISTORICAL_DAILY_DAT_FILE_MODE),
            debugHistoricalDailyDatFileUri = prefs[DEBUG_HISTORICAL_DAILY_DAT_FILE_URI].takeIf { BuildConfig.DEBUG },
            debugRealtimeDataStartMillis = prefs[DEBUG_REALTIME_DATA_START_MILLIS].takeIf { BuildConfig.DEBUG },
            debugRealtimeDataEndMillis = prefs[DEBUG_REALTIME_DATA_END_MILLIS].takeIf { BuildConfig.DEBUG },
            debugSimulateMode = debugSimulateMode,
            debugRealtimeDataPeriodAutoAdvanceEnabled = BuildConfig.DEBUG &&
                (prefs[DEBUG_REALTIME_DATA_PERIOD_AUTO_ADVANCE_ENABLED] ?: true),
            lastBootTime = prefs[LAST_BOOT_TIME] ?: 0L,
            updateOnBoot = prefs[UPDATE_ON_BOOT] ?: true,
            targetDamId = prefs[TARGET_DAM_ID] ?: AppSettings.DEFAULT_DAM_ID,
            realtimeDataSource = prefs[REALTIME_DATA_SOURCE]
                ?.let { runCatching { RealtimeDataSource.valueOf(it) }.getOrNull() }
                ?: RealtimeDataSource.SUDMONITOR,
            historicalDataSource = prefs[HISTORICAL_DATA_SOURCE]
                ?.let { runCatching { RealtimeDataSource.valueOf(it) }.getOrNull() }
                ?: RealtimeDataSource.SUDMONITOR,
            wasLastDataAllInvalid = prefs[WAS_LAST_DATA_ALL_INVALID] ?: false,
            lastLoadResultMessage = prefs[LAST_LOAD_RESULT_MESSAGE] ?: "",
            lastAutoUpdateMillis = prefs[LAST_AUTO_UPDATE_MILLIS] ?: 0L,
            nextScheduledUpdateMillis = prefs[NEXT_SCHEDULED_UPDATE_MILLIS] ?: 0L,
            isFirstRunAfterReschedule = prefs[IS_FIRST_RUN_AFTER_RESCHEDULE] ?: false,
            originFetchedAtMillis = prefs[LAST_ORIGIN_FETCHED_AT_MILLIS],
            manualRefreshAvailableAtMillis = prefs[MANUAL_REFRESH_AVAILABLE_AT_MILLIS]
        )
    }

    
    private fun writeAppSettings(prefs: androidx.datastore.preferences.core.MutablePreferences, settings: AppSettings) {
        prefs[THEME_KEY] = settings.theme.name
        prefs[AUTO_UPDATE_ENABLED] = settings.autoUpdateEnabled
        prefs[SHOW_NOTIFICATION] = settings.showNotification
        prefs[AUTO_UPDATE_INTERVAL] = settings.autoUpdateInterval.name
        prefs[INITIAL_AUTO_UPDATE_DIALOG_SHOWN] = settings.initialAutoUpdateDialogShown
        prefs[AUTO_UPDATE_CUSTOM_TIMING_MILLIS_WEEKLY] = settings.autoUpdateCustomTimingMillisWeekly
        prefs[AUTO_UPDATE_CUSTOM_TIMING_MILLIS_DAILY] = settings.autoUpdateCustomTimingMillisDaily
        prefs[AUTO_UPDATE_CUSTOM_TIMING_MILLIS_12H] = settings.autoUpdateCustomTimingMillis12Hours
        prefs[AUTO_UPDATE_CUSTOM_TIMING_MILLIS_HOURLY] = settings.autoUpdateCustomTimingMillisHourly
        prefs[SHOW_STORAGE_RATE_MESSAGE] = settings.showStorageRateMessage
        
        prefs[STATE_80_100] = settings.state80_100
        prefs[MSG_80_100] = settings.msg80_100
        prefs[MSG_80_100_JA] = settings.msg80_100Ja
        prefs[STATE_60_80] = settings.state60_80
        prefs[MSG_60_80] = settings.msg60_80
        prefs[MSG_60_80_JA] = settings.msg60_80Ja
        prefs[STATE_40_60] = settings.state40_60
        prefs[MSG_40_60] = settings.msg40_60
        prefs[MSG_40_60_JA] = settings.msg40_60Ja
        prefs[STATE_20_40] = settings.state20_40
        prefs[MSG_20_40] = settings.msg20_40
        prefs[MSG_20_40_JA] = settings.msg20_40Ja
        prefs[STATE_0_20] = settings.state0_20
        prefs[MSG_0_20] = settings.msg0_20
        prefs[MSG_0_20_JA] = settings.msg0_20Ja
        prefs[STATE_0] = settings.state0
        prefs[MSG_0] = settings.msg0
        prefs[MSG_0_JA] = settings.msg0Ja
        prefs[STATE_ALL_ABNORMAL] = settings.stateAllAbnormal
        prefs[MSG_ALL_ABNORMAL] = settings.msgAllAbnormal
        prefs[MSG_ALL_ABNORMAL_JA] = settings.msgAllAbnormalJa
        prefs[STATE_ALL_DATA_INVALID] = settings.stateAllDataInvalid
        prefs[MSG_ALL_DATA_INVALID] = settings.msgAllDataInvalid
        prefs[MSG_ALL_DATA_INVALID_JA] = settings.msgAllDataInvalidJa
        
        prefs[OTHER_STATE_80_100] = settings.otherState80_100
        prefs[OTHER_MSG_80_100] = settings.otherMsg80_100
        prefs[OTHER_MSG_80_100_JA] = settings.otherMsg80_100Ja
        prefs[OTHER_STATE_60_80] = settings.otherState60_80
        prefs[OTHER_MSG_60_80] = settings.otherMsg60_80
        prefs[OTHER_MSG_60_80_JA] = settings.otherMsg60_80Ja
        prefs[OTHER_STATE_40_60] = settings.otherState40_60
        prefs[OTHER_MSG_40_60] = settings.otherMsg40_60
        prefs[OTHER_MSG_40_60_JA] = settings.otherMsg40_60Ja
        prefs[OTHER_STATE_20_40] = settings.otherState20_40
        prefs[OTHER_MSG_20_40] = settings.otherMsg20_40
        prefs[OTHER_MSG_20_40_JA] = settings.otherMsg20_40Ja
        prefs[OTHER_STATE_0_20] = settings.otherState0_20
        prefs[OTHER_MSG_0_20] = settings.otherMsg0_20
        prefs[OTHER_MSG_0_20_JA] = settings.otherMsg0_20Ja
        prefs[OTHER_STATE_0] = settings.otherState0
        prefs[OTHER_MSG_0] = settings.otherMsg0
        prefs[OTHER_MSG_0_JA] = settings.otherMsg0Ja
        prefs[OTHER_STATE_ALL_ABNORMAL] = settings.otherStateAllAbnormal
        prefs[OTHER_MSG_ALL_ABNORMAL] = settings.otherMsgAllAbnormal
        prefs[OTHER_MSG_ALL_ABNORMAL_JA] = settings.otherMsgAllAbnormalJa
        prefs[OTHER_STATE_ALL_DATA_INVALID] = settings.otherStateAllDataInvalid
        prefs[OTHER_MSG_ALL_DATA_INVALID] = settings.otherMsgAllDataInvalid
        prefs[OTHER_MSG_ALL_DATA_INVALID_JA] = settings.otherMsgAllDataInvalidJa
        
        prefs[STATE_INITIAL_MESSAGE] = settings.stateInitialMessage
        prefs[MSG_INITIAL_MESSAGE] = settings.msgInitialMessage
        prefs[MSG_INITIAL_MESSAGE_JA] = settings.msgInitialMessageJa
        prefs[STATE_NETWORK_UNAVAILABLE] = settings.stateNetworkUnavailable
        prefs[MSG_NETWORK_UNAVAILABLE] = settings.msgNetworkUnavailable
        prefs[MSG_NETWORK_UNAVAILABLE_JA] = settings.msgNetworkUnavailableJa
        prefs[STATE_LOADING_ERROR] = settings.stateLoadingError
        prefs[MSG_LOADING_ERROR] = settings.msgLoadingError
        prefs[MSG_LOADING_ERROR_JA] = settings.msgLoadingErrorJa
        prefs[STATE_DATA_DISTRIBUTION_STOPPED] = settings.stateDataDistributionStopped
        prefs[MSG_DATA_DISTRIBUTION_STOPPED] = settings.msgDataDistributionStopped
        prefs[MSG_DATA_DISTRIBUTION_STOPPED_JA] = settings.msgDataDistributionStoppedJa
        prefs[STATE_DATA_DISTRIBUTION_RESUMED] = settings.stateDataDistributionResumed
        prefs[MSG_DATA_DISTRIBUTION_RESUMED] = settings.msgDataDistributionResumed
        prefs[MSG_DATA_DISTRIBUTION_RESUMED_JA] = settings.msgDataDistributionResumedJa
        prefs[DEBUG_SETTINGS_VISIBLE] = settings.debugSettingsVisible
        prefs[DEBUG_MODE_ENABLED] = settings.debugModeEnabled
        prefs[DEBUG_REALTIME_DAT_FILE_MODE] = settings.debugRealtimeDatFileMode.name
        if (settings.debugRealtimeDatFileUri != null) {
            prefs[DEBUG_REALTIME_DAT_FILE_URI] = settings.debugRealtimeDatFileUri
        } else {
            prefs.remove(DEBUG_REALTIME_DAT_FILE_URI)
        }
        prefs[DEBUG_HISTORICAL_DAILY_DAT_FILE_MODE] = settings.debugHistoricalDailyDatFileMode.name
        if (settings.debugHistoricalDailyDatFileUri != null) {
            prefs[DEBUG_HISTORICAL_DAILY_DAT_FILE_URI] = settings.debugHistoricalDailyDatFileUri
        } else {
            prefs.remove(DEBUG_HISTORICAL_DAILY_DAT_FILE_URI)
        }
        if (settings.debugRealtimeDataStartMillis != null) {
            prefs[DEBUG_REALTIME_DATA_START_MILLIS] = settings.debugRealtimeDataStartMillis
        } else {
            prefs.remove(DEBUG_REALTIME_DATA_START_MILLIS)
        }
        if (settings.debugRealtimeDataEndMillis != null) {
            prefs[DEBUG_REALTIME_DATA_END_MILLIS] = settings.debugRealtimeDataEndMillis
        } else {
            prefs.remove(DEBUG_REALTIME_DATA_END_MILLIS)
        }
        prefs[DEBUG_SIMULATE_MODE] = settings.debugSimulateMode.name
        prefs[DEBUG_REALTIME_DATA_PERIOD_AUTO_ADVANCE_ENABLED] = settings.debugRealtimeDataPeriodAutoAdvanceEnabled
        prefs[LAST_BOOT_TIME] = settings.lastBootTime
        prefs[UPDATE_ON_BOOT] = settings.updateOnBoot
        prefs[TARGET_DAM_ID] = settings.targetDamId
        prefs[REALTIME_DATA_SOURCE] = settings.realtimeDataSource.name
        prefs[HISTORICAL_DATA_SOURCE] = settings.historicalDataSource.name
        prefs[WAS_LAST_DATA_ALL_INVALID] = settings.wasLastDataAllInvalid
        prefs[LAST_LOAD_RESULT_MESSAGE] = settings.lastLoadResultMessage
        prefs[LAST_AUTO_UPDATE_MILLIS] = settings.lastAutoUpdateMillis
        prefs[NEXT_SCHEDULED_UPDATE_MILLIS] = settings.nextScheduledUpdateMillis
        prefs[IS_FIRST_RUN_AFTER_RESCHEDULE] = settings.isFirstRunAfterReschedule
        if (settings.originFetchedAtMillis != null) {
            prefs[LAST_ORIGIN_FETCHED_AT_MILLIS] = settings.originFetchedAtMillis
        } else {
            prefs.remove(LAST_ORIGIN_FETCHED_AT_MILLIS)
        }
        if (settings.manualRefreshAvailableAtMillis != null) {
            prefs[MANUAL_REFRESH_AVAILABLE_AT_MILLIS] = settings.manualRefreshAvailableAtMillis
        } else {
            prefs.remove(MANUAL_REFRESH_AVAILABLE_AT_MILLIS)
        }
    }

    private fun buildMainCardExpansionState(prefs: Preferences): MainCardExpansionState =
        MainCardExpansionState(
            realtimeObservationExpanded = prefs[MAIN_REALTIME_OBSERVATION_EXPANDED] ?: true,
            realtimeLatestExpanded = prefs[MAIN_REALTIME_LATEST_EXPANDED] ?: true,
            realtimeHistoryExpanded = prefs[MAIN_REALTIME_HISTORY_EXPANDED] ?: true,
            realtimeGraphExpanded = prefs[MAIN_REALTIME_GRAPH_EXPANDED] ?: true,
            realtimeLinksExpanded = prefs[MAIN_REALTIME_LINKS_EXPANDED] ?: true,
            historicalObservationExpanded = prefs[MAIN_HISTORICAL_OBSERVATION_EXPANDED] ?: true,
            historicalHistoryExpanded = prefs[MAIN_HISTORICAL_HISTORY_EXPANDED] ?: true,
            historicalGraphExpanded = prefs[MAIN_HISTORICAL_GRAPH_EXPANDED] ?: true,
            historicalLinksExpanded = prefs[MAIN_HISTORICAL_LINKS_EXPANDED] ?: true
        )


    
    /**
     * アプリケーション設定の現在値（[AppSettings]）を監視するための[Flow]。
     *
     * メモリキャッシュのリフレッシュ要求（[triggerRefresh]）とDataStoreのデータ更新の両方をトリガーに、
     * 最新の[AppSettings]インスタンスをリアルタイムで再生成して供給します。
     */
    private val storedAppSettingsFlow: Flow<AppSettings> =
        dataStore.data
            .map(::buildAppSettings)
            .distinctUntilChanged()

    val appSettingsFlow: Flow<AppSettings> = combine(
        storedAppSettingsFlow,
        refreshTrigger
    ) { settings, _ -> settings }

    /**
     * メイン画面のCard開閉状態を監視するFlow。
     */
    val mainCardExpansionStateFlow: Flow<MainCardExpansionState> =
        dataStore.data
            .map(::buildMainCardExpansionState)
            .distinctUntilChanged()

    /**
     * アプリの表示テーマをDataStoreへ保存します。
     *
     * @param theme 設定する[AppTheme]
     */
    suspend fun saveTheme(theme: AppTheme) {
        dataStore.edit { preferences -> preferences[THEME_KEY] = theme.name }
    }

    /**
     * アプリ設定情報をトランザクショナルに一括更新します。
     *
     * @param transform 現在の設定情報を受け取り、新しい設定情報を生成して返すサスペンド関数
     */
    suspend fun updateSettings(transform: suspend (AppSettings) -> AppSettings) {
        dataStore.edit { prefs ->
            val current = buildAppSettings(prefs)
            val updated = transform(current)
            writeAppSettings(prefs, updated)
        }
    }

    /**
     * 指定したメイン画面Cardの展開状態を、現在値から原子的に反転する。
     */
    suspend fun toggleMainCardExpansion(key: MainCardExpansionKey) {
        dataStore.edit { preferences ->
            val preferenceKey = when (key) {
                MainCardExpansionKey.REALTIME_OBSERVATION -> MAIN_REALTIME_OBSERVATION_EXPANDED
                MainCardExpansionKey.REALTIME_LATEST -> MAIN_REALTIME_LATEST_EXPANDED
                MainCardExpansionKey.REALTIME_HISTORY -> MAIN_REALTIME_HISTORY_EXPANDED
                MainCardExpansionKey.REALTIME_GRAPH -> MAIN_REALTIME_GRAPH_EXPANDED
                MainCardExpansionKey.REALTIME_LINKS -> MAIN_REALTIME_LINKS_EXPANDED
                MainCardExpansionKey.HISTORICAL_OBSERVATION -> MAIN_HISTORICAL_OBSERVATION_EXPANDED
                MainCardExpansionKey.HISTORICAL_HISTORY -> MAIN_HISTORICAL_HISTORY_EXPANDED
                MainCardExpansionKey.HISTORICAL_GRAPH -> MAIN_HISTORICAL_GRAPH_EXPANDED
                MainCardExpansionKey.HISTORICAL_LINKS -> MAIN_HISTORICAL_LINKS_EXPANDED
            }
            preferences[preferenceKey] = !(preferences[preferenceKey] ?: true)
        }
    }

    /**
     * データのメモリキャッシュを強制的に更新（リフレッシュ）するためのシグナルをフローに送信します。
     */
    suspend fun triggerRefresh() {
        refreshTrigger.emit(Unit)
    }
}
