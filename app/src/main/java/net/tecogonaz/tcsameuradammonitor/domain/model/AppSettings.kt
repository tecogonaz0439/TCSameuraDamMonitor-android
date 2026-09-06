// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.domain.model

import android.content.Context
import net.tecogonaz.tcsameuradammonitor.R


/**
 * 貯水率メッセージのしきい値ごとの設定項目（テキスト）を保持するデータモデル。
 *
 * @property threshold しきい値（例: "80"）
 * @property state メッセージの区分状態
 * @property msg 英語の貯水率メッセージ
 * @property msgJa 日本語の貯水率メッセージ
 */
data class StorageRateMessageLevel(
    val threshold: String,
    val state: String,
    val msg: String,
    val msgJa: String
)


/**
 * 貯水率メッセージの分類対象を表す列挙型。
 *
 * 早明浦ダム用は貯水率と貯水量の両方で分類し、早明浦ダム以外用は貯水率のみで分類します。
 */
enum class StorageRateMessageCategory {
    /** 早明浦ダム用（貯水率＋貯水量で分類） */
    SAMEURA,
    /** 早明浦ダム以外用（貯水率のみで分類） */
    OTHER
}


/**
 * デバッグ用の通信シミュレーションエラーモードを表す列挙型。
 */
enum class DebugSimulateMode {
    /** シミュレーションなし（通常通信） */
    NONE,
    /** ネットワーク切断・圏外状態のシミュレーション */
    NETWORK_UNAVAILABLE,
    /** ロード失敗（例外発生）のシミュレーション */
    LOADING_FAILURE
}


/**
 * デバッグ用の.datファイル読み込みモードを表す列挙型。
 */
enum class DebugDatSelectionMode {
    /** アプリに内蔵されたデバッグアセットファイルから読み込む */
    BUNDLED,
    /** 最後に通信で保存したリアルタイム生データを再ロードする */
    LATEST,
    /** ストレージアクセスフレームワーク（SAF）を使用して外部ファイルを選択して読み込む */
    USER_SELECTED;

    companion object {
        /** 内蔵されているデフォルトのデバッグ.datファイル名 */
        const val BUNDLED_FILE_NAME = "531368080700010202605241048683.dat"
    }
}


/**
 * アプリケーション全体の永続設定情報をまとめたデータクラス。
 */
data class AppSettings(
    val theme: AppTheme = AppTheme.SYSTEM,
    val autoUpdateEnabled: Boolean = false,
    val showNotification: Boolean = true,
    val autoUpdateInterval: AutoUpdateInterval = AutoUpdateInterval.ONE_WEEK,
    
    val initialAutoUpdateDialogShown: Boolean = false,
    
    val autoUpdateCustomTimingMillisWeekly: Long = 0L,
    
    val autoUpdateCustomTimingMillisDaily: Long = 0L,
    
    val autoUpdateCustomTimingMillis12Hours: Long = 0L,
    
    val autoUpdateCustomTimingMillisHourly: Long = 0L,
    
    val showStorageRateMessage: Boolean = true,
    
    val state80_100: String = "",
    val msg80_100: String = "",
    val msg80_100Ja: String = "",
    val state60_80: String = "",
    val msg60_80: String = "",
    val msg60_80Ja: String = "",
    val state40_60: String = "",
    val msg40_60: String = "",
    val msg40_60Ja: String = "",
    val state20_40: String = "",
    val msg20_40: String = "",
    val msg20_40Ja: String = "",
    val state0_20: String = "",
    val msg0_20: String = "",
    val msg0_20Ja: String = "",
    val state0: String = "",
    val msg0: String = "",
    val msg0Ja: String = "",
    val stateAllAbnormal: String = "",
    val msgAllAbnormal: String = "",
    val msgAllAbnormalJa: String = "",
    val stateAllDataInvalid: String = "",
    val msgAllDataInvalid: String = "",
    val msgAllDataInvalidJa: String = "",
    
    val otherState80_100: String = "",
    val otherMsg80_100: String = "",
    val otherMsg80_100Ja: String = "",
    val otherState60_80: String = "",
    val otherMsg60_80: String = "",
    val otherMsg60_80Ja: String = "",
    val otherState40_60: String = "",
    val otherMsg40_60: String = "",
    val otherMsg40_60Ja: String = "",
    val otherState20_40: String = "",
    val otherMsg20_40: String = "",
    val otherMsg20_40Ja: String = "",
    val otherState0_20: String = "",
    val otherMsg0_20: String = "",
    val otherMsg0_20Ja: String = "",
    val otherState0: String = "",
    val otherMsg0: String = "",
    val otherMsg0Ja: String = "",
    val otherStateAllAbnormal: String = "",
    val otherMsgAllAbnormal: String = "",
    val otherMsgAllAbnormalJa: String = "",
    val otherStateAllDataInvalid: String = "",
    val otherMsgAllDataInvalid: String = "",
    val otherMsgAllDataInvalidJa: String = "",
    
    val stateInitialMessage: String = "",
    val msgInitialMessage: String = "",
    val msgInitialMessageJa: String = "",
    val stateNetworkUnavailable: String = "",
    val msgNetworkUnavailable: String = "",
    val msgNetworkUnavailableJa: String = "",
    val stateLoadingError: String = "",
    val msgLoadingError: String = "",
    val msgLoadingErrorJa: String = "",
    val stateDataDistributionStopped: String = "",
    val msgDataDistributionStopped: String = "",
    val msgDataDistributionStoppedJa: String = "",
    val stateDataDistributionResumed: String = "",
    val msgDataDistributionResumed: String = "",
    val msgDataDistributionResumedJa: String = "",
    val debugSettingsVisible: Boolean = false,
    val debugModeEnabled: Boolean = false,
    val debugRealtimeDatFileMode: DebugDatSelectionMode = DebugDatSelectionMode.BUNDLED,
    val debugRealtimeDatFileUri: String? = null,
    val debugHistoricalDailyDatFileMode: DebugDatSelectionMode = DebugDatSelectionMode.BUNDLED,
    val debugHistoricalDailyDatFileUri: String? = null,
    val debugRealtimeDataStartMillis: Long? = null,
    val debugRealtimeDataEndMillis: Long? = null,
    val debugSimulateMode: DebugSimulateMode = DebugSimulateMode.NONE,
    val debugRealtimeDataPeriodAutoAdvanceEnabled: Boolean = true,
    
    val updateOnBoot: Boolean = true,
    val lastBootTime: Long = 0L,
    
    val wasLastDataAllInvalid: Boolean = false,
    val targetDamId: String = DEFAULT_DAM_ID,
    val realtimeDataSource: RealtimeDataSource = RealtimeDataSource.SUDMONITOR,
    val historicalDataSource: RealtimeDataSource = RealtimeDataSource.SUDMONITOR,
    val lastLoadResultMessage: String = "",
    
    val lastAutoUpdateMillis: Long = 0L,
    
    val nextScheduledUpdateMillis: Long = 0L,
    
    val isFirstRunAfterReschedule: Boolean = false,
    val originFetchedAtMillis: Long? = null,
    val manualRefreshAvailableAtMillis: Long? = null
) {
    
    fun resetDebugSettings(): AppSettings = copy(
        debugSettingsVisible = false,
        debugModeEnabled = false,
        debugRealtimeDatFileMode = DebugDatSelectionMode.BUNDLED,
        debugRealtimeDatFileUri = null,
        debugHistoricalDailyDatFileMode = DebugDatSelectionMode.BUNDLED,
        debugHistoricalDailyDatFileUri = null,
        debugRealtimeDataStartMillis = null,
        debugRealtimeDataEndMillis = null,
        debugSimulateMode = DebugSimulateMode.NONE,
        debugRealtimeDataPeriodAutoAdvanceEnabled = true
    )

    companion object {
        
        const val DEFAULT_DAM_ID = "1368080700010"

        
        const val BOOT_TIME_TOLERANCE_MS = 10000L

        
        const val DEFAULT_HOUR = 5

        
        const val DEFAULT_MINUTE = 15

        
        const val INITIAL_AUTO_UPDATE_RANDOM_START_HOUR = 0

        
        const val INITIAL_AUTO_UPDATE_RANDOM_START_MINUTE = 15

        
        const val INITIAL_AUTO_UPDATE_RANDOM_END_HOUR = 5

        
        const val INITIAL_AUTO_UPDATE_RANDOM_END_MINUTE = 59

        
        const val MIN_SCHEDULE_ADVANCE_MILLIS = 15 * 60 * 1000L

        
        const val MILLIS_PER_MINUTE = 60_000L

    
    val STORAGE_RATE_THRESHOLD_KEYS = listOf("80_100", "60_80", "40_60", "20_40", "0_20", "0", "all_abnormal", "all_data_invalid")

    
    const val SAMEURA_RATE_AT_LEAST_80 = 80f

    
    const val SAMEURA_AT_LEAST_60_VOLUME_THRESHOLD = 80000f

    
    const val SAMEURA_AT_LEAST_40_VOLUME_THRESHOLD = 60000f

    
    const val SAMEURA_AT_LEAST_20_VOLUME_THRESHOLD = 40000f

    
    private val PERCENTAGE_THRESHOLDS = listOf(
        80f to "80_100",
        60f to "60_80",
        40f to "40_60",
        20f to "20_40",
        0f to "0_20"
    )
    }

    
    val storageRateLevels: List<StorageRateMessageLevel>
        get() = STORAGE_RATE_THRESHOLD_KEYS.map { threshold ->
            storageRateMessageLevel(threshold, StorageRateMessageCategory.SAMEURA)
        }

    /**
     * 早明浦ダム以外用の貯水率メッセージのしきい値ごとの設定項目一覧（[StorageRateMessageLevel]）を取得します。
     */
    val otherStorageRateLevels: List<StorageRateMessageLevel>
        get() = STORAGE_RATE_THRESHOLD_KEYS.map { threshold ->
            storageRateMessageLevel(threshold, StorageRateMessageCategory.OTHER)
        }

    private fun storageRateMessageLevel(
        threshold: String,
        category: StorageRateMessageCategory
    ): StorageRateMessageLevel = when (category) {
        StorageRateMessageCategory.SAMEURA -> when (threshold) {
            "80_100" -> StorageRateMessageLevel(threshold, state80_100, msg80_100, msg80_100Ja)
            "60_80" -> StorageRateMessageLevel(threshold, state60_80, msg60_80, msg60_80Ja)
            "40_60" -> StorageRateMessageLevel(threshold, state40_60, msg40_60, msg40_60Ja)
            "20_40" -> StorageRateMessageLevel(threshold, state20_40, msg20_40, msg20_40Ja)
            "0_20" -> StorageRateMessageLevel(threshold, state0_20, msg0_20, msg0_20Ja)
            "0" -> StorageRateMessageLevel(threshold, state0, msg0, msg0Ja)
            "all_abnormal" -> StorageRateMessageLevel(threshold, stateAllAbnormal, msgAllAbnormal, msgAllAbnormalJa)
            "all_data_invalid" -> StorageRateMessageLevel(threshold, stateAllDataInvalid, msgAllDataInvalid, msgAllDataInvalidJa)
            else -> throw IllegalArgumentException("Unknown threshold: $threshold")
        }
        StorageRateMessageCategory.OTHER -> when (threshold) {
            "80_100" -> StorageRateMessageLevel(threshold, otherState80_100, otherMsg80_100, otherMsg80_100Ja)
            "60_80" -> StorageRateMessageLevel(threshold, otherState60_80, otherMsg60_80, otherMsg60_80Ja)
            "40_60" -> StorageRateMessageLevel(threshold, otherState40_60, otherMsg40_60, otherMsg40_60Ja)
            "20_40" -> StorageRateMessageLevel(threshold, otherState20_40, otherMsg20_40, otherMsg20_40Ja)
            "0_20" -> StorageRateMessageLevel(threshold, otherState0_20, otherMsg0_20, otherMsg0_20Ja)
            "0" -> StorageRateMessageLevel(threshold, otherState0, otherMsg0, otherMsg0Ja)
            "all_abnormal" -> StorageRateMessageLevel(threshold, otherStateAllAbnormal, otherMsgAllAbnormal, otherMsgAllAbnormalJa)
            "all_data_invalid" -> StorageRateMessageLevel(threshold, otherStateAllDataInvalid, otherMsgAllDataInvalid, otherMsgAllDataInvalidJa)
            else -> throw IllegalArgumentException("Unknown threshold: $threshold")
        }
    }

    
    fun getThresholdState(
        threshold: String,
        category: StorageRateMessageCategory = StorageRateMessageCategory.SAMEURA
    ): StorageRateMessageLevel = storageRateMessageLevel(threshold, category)

    /**
     * 指定したしきい値の貯水率メッセージを更新した[AppSettings]を返します。
     *
     * @param threshold しきい値（例: "80_100"）
     * @param state メッセージの区分状態
     * @param msg 英語の貯水率メッセージ
     * @param msgJa 日本語の貯水率メッセージ
     * @param category 更新対象の分類（[StorageRateMessageCategory]）
     */
    fun withStorageRateLevel(
        threshold: String,
        state: String,
        msg: String,
        msgJa: String,
        category: StorageRateMessageCategory = StorageRateMessageCategory.SAMEURA
    ): AppSettings = when (category) {
        StorageRateMessageCategory.SAMEURA -> when (threshold) {
            "80_100" -> copy(state80_100 = state, msg80_100 = msg, msg80_100Ja = msgJa)
            "60_80" -> copy(state60_80 = state, msg60_80 = msg, msg60_80Ja = msgJa)
            "40_60" -> copy(state40_60 = state, msg40_60 = msg, msg40_60Ja = msgJa)
            "20_40" -> copy(state20_40 = state, msg20_40 = msg, msg20_40Ja = msgJa)
            "0_20" -> copy(state0_20 = state, msg0_20 = msg, msg0_20Ja = msgJa)
            "0" -> copy(state0 = state, msg0 = msg, msg0Ja = msgJa)
            "all_abnormal" -> copy(stateAllAbnormal = state, msgAllAbnormal = msg, msgAllAbnormalJa = msgJa)
            "all_data_invalid" -> copy(stateAllDataInvalid = state, msgAllDataInvalid = msg, msgAllDataInvalidJa = msgJa)
            else -> this
        }
        StorageRateMessageCategory.OTHER -> when (threshold) {
            "80_100" -> copy(otherState80_100 = state, otherMsg80_100 = msg, otherMsg80_100Ja = msgJa)
            "60_80" -> copy(otherState60_80 = state, otherMsg60_80 = msg, otherMsg60_80Ja = msgJa)
            "40_60" -> copy(otherState40_60 = state, otherMsg40_60 = msg, otherMsg40_60Ja = msgJa)
            "20_40" -> copy(otherState20_40 = state, otherMsg20_40 = msg, otherMsg20_40Ja = msgJa)
            "0_20" -> copy(otherState0_20 = state, otherMsg0_20 = msg, otherMsg0_20Ja = msgJa)
            "0" -> copy(otherState0 = state, otherMsg0 = msg, otherMsg0Ja = msgJa)
            "all_abnormal" -> copy(otherStateAllAbnormal = state, otherMsgAllAbnormal = msg, otherMsgAllAbnormalJa = msgJa)
            "all_data_invalid" -> copy(otherStateAllDataInvalid = state, otherMsgAllDataInvalid = msg, otherMsgAllDataInvalidJa = msgJa)
            else -> this
        }
    }

    
    /**
     * 貯水率メッセージをリセットした[AppSettings]を返します。
     *
     * [StorageRateMessageCategory.SAMEURA] はモードに応じて削除・日本語既定・一般向け既定を適用し、
     * [StorageRateMessageCategory.OTHER] はモードに関係なく一般向けプリセット（[StorageRateMessageCategory.SAMEURA] の
     * "non_ja" と同等）を適用します。
     *
     * @param context リソース解決用のコンテキスト
     * @param mode リセットモード（"delete" / "ja" / "non_ja"）
     * @param category リセット対象の分類（[StorageRateMessageCategory]）
     */
    fun withStorageRateLevelsReset(
        context: Context,
        mode: String,
        category: StorageRateMessageCategory = StorageRateMessageCategory.SAMEURA
    ): AppSettings {
        val msgNonJaSelector: (Int, Int) -> String = { _, nonJaKey ->
            when (mode) {
                "delete" -> ""
                "ja" -> context.getString(nonJaKey)
                "non_ja" -> context.getString(nonJaKey)
                else -> ""
            }
        }
        val msgJaSelector: (Int, Int) -> String = { jaKey, nonJaKey ->
            when (mode) {
                "delete" -> ""
                "ja" -> context.getString(jaKey)
                "non_ja" -> context.getString(nonJaKey)
                else -> ""
            }
        }
        val msgJaForAllAbnormalSelector: (Int, Int) -> String = { jaKey, _ ->
            when (mode) {
                "delete" -> ""
                "ja" -> context.getString(jaKey)
                "non_ja" -> context.getString(jaKey)
                else -> ""
            }
        }

        return STORAGE_RATE_THRESHOLD_KEYS.fold(this) { acc, threshold ->
            val (stateResId, jaResId, nonJaResId) = when (threshold) {
                "80_100" -> Triple(R.string.main_emoji_80_100, R.string.storage_default_msg_ja_80_100, R.string.storage_default_msg_non_ja_80_100)
                "60_80" -> Triple(R.string.main_emoji_60_80, R.string.storage_default_msg_ja_60_80, R.string.storage_default_msg_non_ja_60_80)
                "40_60" -> Triple(R.string.main_emoji_40_60, R.string.storage_default_msg_ja_40_60, R.string.storage_default_msg_non_ja_40_60)
                "20_40" -> Triple(R.string.main_emoji_20_40, R.string.storage_default_msg_ja_20_40, R.string.storage_default_msg_non_ja_20_40)
                "0_20" -> Triple(R.string.main_emoji_0_20, R.string.storage_default_msg_ja_0_20, R.string.storage_default_msg_non_ja_0_20)
                "0" -> Triple(R.string.main_emoji_0, R.string.storage_default_msg_ja_0, R.string.storage_default_msg_non_ja_0)
                "all_abnormal" -> Triple(R.string.main_emoji_all_abnormal, R.string.storage_default_msg_ja_all_abnormal, R.string.storage_default_msg_non_ja_all_abnormal)
                "all_data_invalid" -> Triple(R.string.main_emoji_all_data_invalid, R.string.storage_default_msg_ja_all_data_invalid, R.string.storage_default_msg_non_ja_all_data_invalid)
                else -> return@fold acc
            }
            when (category) {
                StorageRateMessageCategory.SAMEURA -> {
                    val msgJa = if (threshold == "all_abnormal" || threshold == "all_data_invalid") {
                        msgJaForAllAbnormalSelector(jaResId, nonJaResId)
                    } else {
                        msgJaSelector(jaResId, nonJaResId)
                    }
                    acc.withStorageRateLevel(
                        threshold = threshold,
                        state = context.getString(stateResId),
                        msg = msgNonJaSelector(jaResId, nonJaResId),
                        msgJa = msgJa
                    )
                }
                StorageRateMessageCategory.OTHER -> {
                    val msgJaResId = if (threshold == "all_abnormal" || threshold == "all_data_invalid") {
                        jaResId
                    } else {
                        nonJaResId
                    }
                    acc.withStorageRateLevel(
                        threshold = threshold,
                        state = context.getString(stateResId),
                        msg = context.getString(nonJaResId),
                        msgJa = context.getString(msgJaResId),
                        category = StorageRateMessageCategory.OTHER
                    )
                }
            }
        }
    }

    
    val currentCustomTimingMillis: Long
        get() = when (autoUpdateInterval) {
            AutoUpdateInterval.ONE_WEEK -> autoUpdateCustomTimingMillisWeekly
            AutoUpdateInterval.ONE_DAY -> autoUpdateCustomTimingMillisDaily
            AutoUpdateInterval.TWELVE_HOURS -> autoUpdateCustomTimingMillis12Hours
            AutoUpdateInterval.ONE_HOUR -> autoUpdateCustomTimingMillisHourly
        }

    
    fun withCustomTimingMillis(millis: Long): AppSettings = when (autoUpdateInterval) {
        AutoUpdateInterval.ONE_WEEK -> copy(autoUpdateCustomTimingMillisWeekly = millis)
        AutoUpdateInterval.ONE_DAY -> copy(autoUpdateCustomTimingMillisDaily = millis)
        AutoUpdateInterval.TWELVE_HOURS -> copy(autoUpdateCustomTimingMillis12Hours = millis)
        AutoUpdateInterval.ONE_HOUR -> copy(autoUpdateCustomTimingMillisHourly = millis)
    }

    
    fun calculateNextRunTime(now: Long): Long =
        AutoUpdateScheduler.calculateNextRunTime(this, now)

    
    fun calculateInitialCustomTiming(now: Long): Long =
        AutoUpdateScheduler.calculateInitialCustomTiming(this, now)

    
    fun calculateInitialCustomTiming(now: Long, hour: Int, minute: Int): Long =
        AutoUpdateScheduler.calculateInitialCustomTiming(this, now, hour, minute)

    
    fun calculateEffectiveNextRunTimeAndA(now: Long): Pair<Long, Long> =
        AutoUpdateScheduler.calculateEffectiveNextRunTimeAndA(this, now)

    
    fun calculateNextRunTimeAfterSuccess(now: Long): Long =
        AutoUpdateScheduler.calculateNextRunTimeAfterSuccess(this, now)

    
    /**
     * 貯水率に応じた状態とメッセージのペアを取得します。
     *
     * 早明浦ダム用（[isSameura] = true）は貯水率と貯水量（[storageVolumeForMessage]）で分類し、
     * 早明浦ダム以外用は貯水率のみで分類します。メッセージの実体は分類に応じたフィールドから取得します。
     *
     * @param percentage 貯水率（%）。nullは欠測扱い
     * @param isJapanese 日本語メッセージを返すかどうか
     * @param isAllDataInvalid 全観測データが欠測かどうか
     * @param isSameura 早明浦ダム用の分類を行うかどうか
     * @param storageVolumeForMessage 欠測前の最新正常貯水量（×10³m³）。nullは不明扱い
     * @return 状態文字列とメッセージ文字列のペア
     */
    fun getStateForPercentage(
        percentage: Float?,
        isJapanese: Boolean,
        isAllDataInvalid: Boolean = false,
        isSameura: Boolean = false,
        storageVolumeForMessage: Float? = null
    ): Pair<String, String> {
        if (!showStorageRateMessage) return "" to ""
        if (percentage == null) {
            return if (isAllDataInvalid) {
                stateAllDataInvalid to (if (isJapanese) msgAllDataInvalidJa else msgAllDataInvalid)
            } else {
                stateAllAbnormal to (if (isJapanese) msgAllAbnormalJa else msgAllAbnormal)
            }
        }
        val category = if (isSameura) StorageRateMessageCategory.SAMEURA else StorageRateMessageCategory.OTHER
        val threshold = if (isSameura) {
            when {
                percentage >= SAMEURA_RATE_AT_LEAST_80 -> "80_100"
                percentage <= 0f -> "0"
                storageVolumeForMessage != null -> when {
                    storageVolumeForMessage >= SAMEURA_AT_LEAST_60_VOLUME_THRESHOLD -> "60_80"
                    storageVolumeForMessage >= SAMEURA_AT_LEAST_40_VOLUME_THRESHOLD -> "40_60"
                    storageVolumeForMessage >= SAMEURA_AT_LEAST_20_VOLUME_THRESHOLD -> "20_40"
                    else -> "0_20"
                }
                else -> when {
                    percentage >= 60f -> "60_80"
                    percentage >= 40f -> "40_60"
                    percentage >= 20f -> "20_40"
                    else -> "0_20"
                }
            }
        } else {
            PERCENTAGE_THRESHOLDS.firstOrNull { (minPct, _) ->
                if (minPct == 0f) percentage > minPct else percentage >= minPct
            }?.second ?: "0"
        }
        val level = getThresholdState(threshold, category)
        return level.state to (if (isJapanese) level.msgJa else level.msg)
    }

    /**
     * 貯水率に応じた状態とメッセージを組み合わせた表示文字列を取得します。
     *
     * @param percentage 貯水率（%）。nullは欠測扱い
     * @param isJapanese 日本語メッセージを返すかどうか
     * @param isAllDataInvalid 全観測データが欠測かどうか
     * @param isSameura 早明浦ダム用の分類を行うかどうか
     * @param storageVolumeForMessage 欠測前の最新正常貯水量（×10³m³）。nullは不明扱い
     * @return 状態とメッセージを組み合わせた表示文字列
     */
    fun getStateText(
        percentage: Float?,
        isJapanese: Boolean,
        isAllDataInvalid: Boolean = false,
        isSameura: Boolean = false,
        storageVolumeForMessage: Float? = null
    ): String {
        val (state, msg) = getStateForPercentage(
            percentage,
            isJapanese,
            isAllDataInvalid,
            isSameura,
            storageVolumeForMessage
        )
        return if (msg.isNotEmpty()) "$state $msg" else state
    }

    
    fun getLoadingErrorText(isJapanese: Boolean): String {
        val msg = if (isJapanese) msgLoadingErrorJa else msgLoadingError
        val displayMsg = if (msg.isNotEmpty()) "$stateLoadingError $msg" else stateLoadingError
        return displayMsg.trim()
    }

    
    fun getInitialMessageText(isJapanese: Boolean): String {
        val msg = if (isJapanese) msgInitialMessageJa else msgInitialMessage
        val displayMsg = if (msg.isNotEmpty()) "$stateInitialMessage $msg" else stateInitialMessage
        return displayMsg.trim()
    }

    
    fun getNetworkUnavailableText(isJapanese: Boolean): String {
        val msg = if (isJapanese) msgNetworkUnavailableJa else msgNetworkUnavailable
        val displayMsg = if (msg.isNotEmpty()) "$stateNetworkUnavailable $msg" else stateNetworkUnavailable
        return displayMsg.trim()
    }

    
    fun getDataDistributionStoppedText(isJapanese: Boolean): String {
        val msg = if (isJapanese) msgDataDistributionStoppedJa else msgDataDistributionStopped
        val displayMsg = if (msg.isNotEmpty()) "$stateDataDistributionStopped $msg" else stateDataDistributionStopped
        return displayMsg.trim()
    }

    
    fun getDataDistributionResumedText(isJapanese: Boolean): String {
        val msg = if (isJapanese) msgDataDistributionResumedJa else msgDataDistributionResumed
        val displayMsg = if (msg.isNotEmpty()) "$stateDataDistributionResumed $msg" else stateDataDistributionResumed
        return displayMsg.trim()
    }
}
