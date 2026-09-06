// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.domain.model

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import net.tecogonaz.tcsameuradammonitor.R
import net.tecogonaz.tcsameuradammonitor.util.TimeUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * アプリケーション設定ドメインモデル [AppSettings] のユニットテストクラス。
 * デバッグ設定のリセット処理、ダム貯水率レベル（80%以上、60〜80%など）や異常値・通信エラー等の状態に
 * 応じた言語別（日本語/英語）表示文字列の取得ロジック（`getStateText`, `getLoadingErrorText`など）、
 * 自動更新間隔（1日、1時間など）に応じた次回実行日時の算出処理、表示用絵文字/テキストしきい値のマッピング、
 * およびしきい値初期化機能（デフォルトリセット、全削除など）が仕様通りに機能することを検証します。
 * 貯水率メッセージは早明浦ダム用（貯水率＋貯水量で分類・既存フィールド）と早明浦ダム以外用
 * （貯水率のみで分類・other* フィールド）の2系統に分かれているため、両系統の分類・フィールド選択を検証します。
 */
class AppSettingsTest {
    @Test
    fun defaultRealtimeDataSource_isSudmonitorAndOriginFetchedAtIsNull() {
        val settings = AppSettings()

        assertEquals(RealtimeDataSource.SUDMONITOR, settings.realtimeDataSource)
        assertEquals(null, settings.originFetchedAtMillis)
    }

    @Test
    fun defaultHistoricalDataSource_isSudmonitor() {
        val settings = AppSettings()

        assertEquals(RealtimeDataSource.SUDMONITOR, settings.historicalDataSource)
    }

    @Test
    fun historicalDataSource_copyUpdatesValue() {
        val settings = AppSettings()

        val updated = settings.copy(historicalDataSource = RealtimeDataSource.MLIT_DIRECT)

        assertEquals(RealtimeDataSource.MLIT_DIRECT, updated.historicalDataSource)
        assertEquals(RealtimeDataSource.SUDMONITOR, settings.historicalDataSource)
    }

    @Test
    fun realtimeDataSource_enumConversion_roundTripsByName() {
        RealtimeDataSource.entries.forEach { source ->
            val restored = runCatching { RealtimeDataSource.valueOf(source.name) }.getOrNull()

            assertEquals(source, restored)
        }
        assertEquals(
            RealtimeDataSource.MLIT_DIRECT,
            runCatching { RealtimeDataSource.valueOf("INVALID") }.getOrNull() ?: RealtimeDataSource.MLIT_DIRECT
        )
    }

    @Test
    fun resetDebugSettings_returnsDebugValuesToDefaults() {
        val settings = AppSettings(
            debugSettingsVisible = true,
            debugModeEnabled = true,
            debugRealtimeDatFileMode = DebugDatSelectionMode.USER_SELECTED,
            debugRealtimeDatFileUri = "content://debug.dat",
            debugHistoricalDailyDatFileMode = DebugDatSelectionMode.USER_SELECTED,
            debugHistoricalDailyDatFileUri = "content://historical.dat",
            debugRealtimeDataStartMillis = 1000L,
            debugRealtimeDataEndMillis = 2000L,
            debugSimulateMode = DebugSimulateMode.LOADING_FAILURE,
            debugRealtimeDataPeriodAutoAdvanceEnabled = false
        )

        val reset = settings.resetDebugSettings()

        assertEquals(AppSettings().debugSettingsVisible, reset.debugSettingsVisible)
        assertEquals(AppSettings().debugModeEnabled, reset.debugModeEnabled)
        assertEquals(AppSettings().debugRealtimeDatFileMode, reset.debugRealtimeDatFileMode)
        assertEquals(AppSettings().debugRealtimeDatFileUri, reset.debugRealtimeDatFileUri)
        assertEquals(AppSettings().debugHistoricalDailyDatFileMode, reset.debugHistoricalDailyDatFileMode)
        assertEquals(AppSettings().debugHistoricalDailyDatFileUri, reset.debugHistoricalDailyDatFileUri)
        assertEquals(AppSettings().debugRealtimeDataStartMillis, reset.debugRealtimeDataStartMillis)
        assertEquals(AppSettings().debugRealtimeDataEndMillis, reset.debugRealtimeDataEndMillis)
        assertEquals(AppSettings().debugSimulateMode, reset.debugSimulateMode)
        assertEquals(AppSettings().debugRealtimeDataPeriodAutoAdvanceEnabled, reset.debugRealtimeDataPeriodAutoAdvanceEnabled)
    }

    // 早明浦ダム以外（isSameura=false・既定）は貯水率のみで分類し、other* フィールドのメッセージを返す。
    // このグループは従来仕様の分類ロジックを維持したまま、返り値のみ other* フィールドへ移行している。

    @Test
    fun getStateForPercentage_at80Percent_selects80To100Message() {
        val settings = settings()

        assertEquals("O80" to "ohigh-ja", settings.getStateForPercentage(80.0f, isJapanese = true))
    }

    @Test
    fun getStateForPercentage_below80Percent_selects60To80Message() {
        val settings = settings()

        assertEquals("O60" to "omiddle-en", settings.getStateForPercentage(79.9f, isJapanese = false))
    }

    @Test
    fun getStateForPercentage_at79Point99Percent_selects60To80Message() {
        val settings = settings()

        assertEquals("O60" to "omiddle-en", settings.getStateForPercentage(79.99f, isJapanese = false))
    }

    @Test
    fun getStateForPercentage_at60Percent_selects60To80Message() {
        val settings = settings()

        assertEquals("O60" to "omiddle-ja", settings.getStateForPercentage(60.0f, isJapanese = true))
    }

    @Test
    fun getStateForPercentage_below60Percent_selects40To60Message() {
        val settings = settings()

        assertEquals("O40" to "olow-en", settings.getStateForPercentage(59.9f, isJapanese = false))
    }

    @Test
    fun getStateForPercentage_at40Percent_selects40To60Message() {
        val settings = settings()

        assertEquals("O40" to "olow-ja", settings.getStateForPercentage(40.0f, isJapanese = true))
    }

    @Test
    fun getStateForPercentage_at20Percent_selects20To40Message() {
        val settings = settings()

        assertEquals("O20" to "overly-low-en", settings.getStateForPercentage(20.0f, isJapanese = false))
    }

    @Test
    fun getStateForPercentage_above0Percent_selects0To20Message() {
        val settings = settings()

        assertEquals("O0-20" to "ocritical-ja", settings.getStateForPercentage(0.1f, isJapanese = true))
    }

    @Test
    fun getStateForPercentage_at0Percent_selectsEmptyMessage() {
        val settings = settings()

        assertEquals("O0" to "oempty-en", settings.getStateForPercentage(0f, isJapanese = false))
    }

    @Test
    fun getStateForPercentage_nullPercentage_selectsAllAbnormalMessage() {
        val settings = settings()

        // percentage==null は isSameura に関わらず既存フィールドを返す
        assertEquals("NA" to "missing-ja", settings.getStateForPercentage(null, isJapanese = true))
    }

    @Test
    fun getStateForPercentage_returnsBlankPairWhenStorageRateMessageDisabled() {
        val settings = settings(showStorageRateMessage = false)

        assertEquals("" to "", settings.getStateForPercentage(90f, isJapanese = true))
    }

    @Test
    fun getStateText_returnsBlankWhenStorageRateMessageDisabled() {
        val settings = settings(showStorageRateMessage = false)

        assertEquals("", settings.getStateText(null, isJapanese = false))
    }

    @Test
    fun getStateText_selectsEnglishAndJapaneseMessages() {
        val settings = settings()

        // isSameura=false（既定）は other* フィールドを返す
        assertEquals("O80 ohigh-en", settings.getStateText(80f, isJapanese = false))
        assertEquals("O80 ohigh-ja", settings.getStateText(80f, isJapanese = true))
    }

    @Test
    fun getStateText_returnsStateOnlyWhenSelectedMessageIsEmpty() {
        val settings = settings().copy(
            otherMsg80_100 = "",
            otherMsg80_100Ja = ""
        )

        assertEquals("O80", settings.getStateText(80f, isJapanese = false))
        assertEquals("O80", settings.getStateText(80f, isJapanese = true))
    }

    // ---- 早明浦ダム（isSameura=true）用の分類テスト ----
    // 早明浦ダムは貯水率と貯水量の両方で分類し、既存フィールド（state80_100 等）のメッセージを返す。

    @Test
    fun getStateForPercentage_sameuraRateAtOrAbove80_selects80To100Message() {
        val settings = settings()

        // 貯水率80%以上は80_100（100超も含む）
        assertEquals("80" to "high-ja", settings.getStateForPercentage(100.0f, isJapanese = true, isSameura = true))
        assertEquals("80" to "high-ja", settings.getStateForPercentage(100.1f, isJapanese = true, isSameura = true))
        assertEquals("80" to "high-ja", settings.getStateForPercentage(80.0f, isJapanese = true, isSameura = true))
    }

    @Test
    fun getStateForPercentage_sameuraRateOver100_ignoresStorageVolume() {
        val settings = settings()

        // 貯水率100超は貯水量に関係なく80_100
        assertEquals("80" to "high-en", settings.getStateForPercentage(120.0f, isJapanese = false, isSameura = true, storageVolumeForMessage = 0f))
        assertEquals("80" to "high-en", settings.getStateForPercentage(100.1f, isJapanese = false, isSameura = true, storageVolumeForMessage = null))
    }

    @Test
    fun getStateForPercentage_sameuraBelow80_usesStorageVolumeThresholds() {
        val settings = settings()

        // 貯水率が80%未満の場合は貯水量で分類する
        assertEquals("60" to "middle-en", settings.getStateForPercentage(79.9f, isJapanese = false, isSameura = true, storageVolumeForMessage = 100000f))
        assertEquals("60" to "middle-en", settings.getStateForPercentage(79.9f, isJapanese = false, isSameura = true, storageVolumeForMessage = 80000f))
        assertEquals("40" to "low-en", settings.getStateForPercentage(79.9f, isJapanese = false, isSameura = true, storageVolumeForMessage = 79999.9f))
        assertEquals("40" to "low-en", settings.getStateForPercentage(79.9f, isJapanese = false, isSameura = true, storageVolumeForMessage = 60000f))
        assertEquals("20" to "very-low-en", settings.getStateForPercentage(79.9f, isJapanese = false, isSameura = true, storageVolumeForMessage = 59999.9f))
        assertEquals("20" to "very-low-en", settings.getStateForPercentage(79.9f, isJapanese = false, isSameura = true, storageVolumeForMessage = 40000f))
        assertEquals("0-20" to "critical-en", settings.getStateForPercentage(79.9f, isJapanese = false, isSameura = true, storageVolumeForMessage = 39999.9f))
    }

    @Test
    fun getStateForPercentage_sameuraBelow80_withoutStorageVolume_fallsBackToPercentage() {
        val settings = settings()

        // 貯水量不明（null）の場合は貯水率で分類する
        assertEquals("60" to "middle-en", settings.getStateForPercentage(79.9f, isJapanese = false, isSameura = true))
        assertEquals("40" to "low-en", settings.getStateForPercentage(59.9f, isJapanese = false, isSameura = true))
        assertEquals("20" to "very-low-en", settings.getStateForPercentage(39.9f, isJapanese = false, isSameura = true))
        assertEquals("0-20" to "critical-en", settings.getStateForPercentage(19.9f, isJapanese = false, isSameura = true))
    }

    @Test
    fun getStateForPercentage_sameuraRate0OrNegative_selectsEmptyMessage() {
        val settings = settings()

        // 貯水率0%以下は0（貯水量があっても0扱い）
        assertEquals("0" to "empty-ja", settings.getStateForPercentage(0f, isJapanese = true, isSameura = true, storageVolumeForMessage = 50000f))
        assertEquals("0" to "empty-ja", settings.getStateForPercentage(-0.1f, isJapanese = true, isSameura = true))
    }

    @Test
    fun getStateForPercentage_sameuraNullPercentage_selectsExistingAllAbnormalOrAllDataInvalid() {
        val settings = settings()

        // percentage==null のときは isSameura=true でも既存フィールドを返す
        assertEquals("NA" to "missing-ja", settings.getStateForPercentage(null, isJapanese = true, isSameura = true))
        assertEquals("INV" to "invalid-en", settings.getStateForPercentage(null, isJapanese = false, isSameura = true, isAllDataInvalid = true))
    }

    @Test
    fun getStateForPercentage_fieldSelectionSwitchesByIsSameura() {
        val settings = settings()

        // isSameura=false は other* フィールド、true は既存フィールドを返す
        assertEquals("O80" to "ohigh-en", settings.getStateForPercentage(80.0f, isJapanese = false))
        assertEquals("80" to "high-en", settings.getStateForPercentage(80.0f, isJapanese = false, isSameura = true))
    }

    @Test
    fun getStateText_sameura_selectsMessagesByVolumeAndPercentage() {
        val settings = settings()

        assertEquals("80 high-ja", settings.getStateText(80f, isJapanese = true, isSameura = true))
        assertEquals("60 middle-ja", settings.getStateText(79.9f, isJapanese = true, isSameura = true, storageVolumeForMessage = 100000f))
        assertEquals("O80 ohigh-en", settings.getStateText(80f, isJapanese = false))
    }

    @Test
    fun getLoadingErrorText_selectsEnglishAndJapaneseMessages() {
        val settings = settings()

        assertEquals("ERR loading-error-en", settings.getLoadingErrorText(isJapanese = false))
        assertEquals("ERR loading-error-ja", settings.getLoadingErrorText(isJapanese = true))
    }

    @Test
    fun getLoadingErrorText_returnsStateOnlyWhenSelectedMessageIsEmpty() {
        val settings = settings().copy(
            msgLoadingError = "",
            msgLoadingErrorJa = ""
        )

        assertEquals("ERR", settings.getLoadingErrorText(isJapanese = false))
        assertEquals("ERR", settings.getLoadingErrorText(isJapanese = true))
    }

    @Test
    fun getLoadingErrorText_trimsWhenStateIsEmpty() {
        val settings = settings().copy(stateLoadingError = "")

        assertEquals("loading-error-en", settings.getLoadingErrorText(isJapanese = false))
        assertEquals("loading-error-ja", settings.getLoadingErrorText(isJapanese = true))
    }

    @Test
    fun getInitialMessageText_selectsEnglishAndJapaneseMessages() {
        val settings = settings()

        assertEquals("INIT initial-en", settings.getInitialMessageText(isJapanese = false))
        assertEquals("INIT initial-ja", settings.getInitialMessageText(isJapanese = true))
    }

    @Test
    fun getInitialMessageText_returnsStateOnlyWhenSelectedMessageIsEmpty() {
        val settings = settings().copy(
            msgInitialMessage = "",
            msgInitialMessageJa = ""
        )

        assertEquals("INIT", settings.getInitialMessageText(isJapanese = false))
        assertEquals("INIT", settings.getInitialMessageText(isJapanese = true))
    }

    @Test
    fun getInitialMessageText_trimsWhenStateIsEmpty() {
        val settings = settings().copy(stateInitialMessage = "")

        assertEquals("initial-en", settings.getInitialMessageText(isJapanese = false))
        assertEquals("initial-ja", settings.getInitialMessageText(isJapanese = true))
    }

    @Test
    fun getNetworkUnavailableText_selectsEnglishAndJapaneseMessages() {
        val settings = settings()

        assertEquals("NET network-unavailable-en", settings.getNetworkUnavailableText(isJapanese = false))
        assertEquals("NET network-unavailable-ja", settings.getNetworkUnavailableText(isJapanese = true))
    }

    @Test
    fun getNetworkUnavailableText_returnsStateOnlyWhenSelectedMessageIsEmpty() {
        val settings = settings().copy(
            msgNetworkUnavailable = "",
            msgNetworkUnavailableJa = ""
        )

        assertEquals("NET", settings.getNetworkUnavailableText(isJapanese = false))
        assertEquals("NET", settings.getNetworkUnavailableText(isJapanese = true))
    }

    @Test
    fun getNetworkUnavailableText_trimsWhenStateIsEmpty() {
        val settings = settings().copy(stateNetworkUnavailable = "")

        assertEquals("network-unavailable-en", settings.getNetworkUnavailableText(isJapanese = false))
        assertEquals("network-unavailable-ja", settings.getNetworkUnavailableText(isJapanese = true))
    }

    @Test
    fun calculateNextRunTime_oneDayUsesDefaultMorning515Jst() {
        val settings = settings(autoUpdateInterval = AutoUpdateInterval.ONE_DAY)
        val now = jstMillis("2026/05/16 05:14")

        assertEquals(jstMillis("2026/05/16 05:15"), settings.calculateNextRunTime(now))
    }

    @Test
    fun calculateNextRunTime_oneHourAdvancesWhenDefaultMinutePassed() {
        val settings = settings(autoUpdateInterval = AutoUpdateInterval.ONE_HOUR)
        val now = jstMillis("2026/05/16 05:16")

        assertEquals(jstMillis("2026/05/16 06:15"), settings.calculateNextRunTime(now))
    }

    @Test
    fun storageRateLevels_returnsAll8LevelsInOrder() {
        val settings = settings()

        val levels = settings.storageRateLevels

        assertEquals(8, levels.size)
        assertEquals("80_100", levels[0].threshold)
        assertEquals("60_80", levels[1].threshold)
        assertEquals("40_60", levels[2].threshold)
        assertEquals("20_40", levels[3].threshold)
        assertEquals("0_20", levels[4].threshold)
        assertEquals("0", levels[5].threshold)
        assertEquals("all_abnormal", levels[6].threshold)
        assertEquals("all_data_invalid", levels[7].threshold)
    }

    @Test
    fun storageRateLevels_reflectsIndividualFieldValues() {
        val settings = settings()

        val level = settings.storageRateLevels.first { it.threshold == "80_100" }

        assertEquals("80", level.state)
        assertEquals("high-en", level.msg)
        assertEquals("high-ja", level.msgJa)
    }

    @Test
    fun getThresholdState_returnsCorrectLevel() {
        val settings = settings()

        val level = settings.getThresholdState("40_60")

        assertEquals("40_60", level.threshold)
        assertEquals("40", level.state)
        assertEquals("low-en", level.msg)
        assertEquals("low-ja", level.msgJa)
    }

    @Test
    fun withStorageRateLevel_updatesSingleLevel() {
        val settings = settings()

        val updated = settings.withStorageRateLevel("0", "ZERO", "zero-en", "zero-ja")

        assertEquals("ZERO", updated.state0)
        assertEquals("zero-en", updated.msg0)
        assertEquals("zero-ja", updated.msg0Ja)
        assertEquals("80", updated.state80_100)
    }

    @Test
    fun withStorageRateLevel_updatesAllAbnormalLevel() {
        val settings = settings()

        val updated = settings.withStorageRateLevel("all_abnormal", "ERR", "error-en", "error-ja")

        assertEquals("ERR", updated.stateAllAbnormal)
        assertEquals("error-en", updated.msgAllAbnormal)
        assertEquals("error-ja", updated.msgAllAbnormalJa)
    }

    @Test
    fun withStorageRateLevel_unknownThresholdReturnsUnchanged() {
        val settings = settings()

        val updated = settings.withStorageRateLevel("invalid", "X", "Y", "Z")

        assertEquals(settings, updated)
    }

    @Test
    fun withStorageRateLevelsReset_deleteClearsAllStorageRateMessages() {
        val context = mockk<Context>(relaxed = true)
        every { context.getString(any()) } answers { "" }
        val settings = settings()

        val reset = settings.withStorageRateLevelsReset(context, "delete")

        reset.storageRateLevels.forEach { level ->
            assertEquals("", level.state)
            assertEquals("", level.msg)
            assertEquals("", level.msgJa)
        }
    }

    @Test
    fun withStorageRateLevelsReset_jaSetsJapaneseDefaults() {
        val context = mockk<Context>(relaxed = true)
        every { context.getString(any()) } answers {
            val id = firstArg<Int>()
            when (id) {
                R.string.main_emoji_80_100 -> "😊"
                R.string.storage_default_msg_ja_80_100 -> "ja-80"
                R.string.storage_default_msg_non_ja_80_100 -> "non-80"
                else -> "other-$id"
            }
        }
        val settings = settings()

        val reset = settings.withStorageRateLevelsReset(context, "ja")

        assertEquals("😊", reset.state80_100)
        assertEquals("non-80", reset.msg80_100)
        assertEquals("ja-80", reset.msg80_100Ja)
    }

    @Test
    fun withStorageRateLevelsReset_nonJaKeepsAllAbnormalJapaneseDefault() {
        val context = mockk<Context>(relaxed = true)
        every { context.getString(any()) } answers {
            val id = firstArg<Int>()
            when (id) {
                R.string.main_emoji_all_abnormal -> "😑"
                R.string.storage_default_msg_ja_all_abnormal -> "ja-aa"
                R.string.storage_default_msg_non_ja_all_abnormal -> "non-aa"
                else -> "other-$id"
            }
        }
        val settings = settings()

        val reset = settings.withStorageRateLevelsReset(context, "non_ja")

        assertEquals("😑", reset.stateAllAbnormal)
        assertEquals("non-aa", reset.msgAllAbnormal)
        assertEquals("ja-aa", reset.msgAllAbnormalJa)
    }

    @Test
    fun STORAGE_RATE_THRESHOLD_KEYS_containsAll8Keys() {
        assertEquals(8, AppSettings.STORAGE_RATE_THRESHOLD_KEYS.size)
        assertEquals(8, AppSettings.STORAGE_RATE_THRESHOLD_KEYS.toSet().size)
    }

    @Test
    fun getThresholdState_allThresholdsReturnValidLevel() {
        val settings = settings()

        AppSettings.STORAGE_RATE_THRESHOLD_KEYS.forEach { threshold ->
            val level = settings.getThresholdState(threshold)
            assertNotNull(level)
            assertEquals(threshold, level.threshold)
        }
    }

    @Test
    fun storageRateLevels_identicalAfterSameResetMode() {
        val context = mockk<Context>(relaxed = true)
        every { context.getString(any()) } answers {
            "default-${firstArg<Int>()}"
        }
        val settings = settings()

        val afterJa = settings.withStorageRateLevelsReset(context, "ja")
        val afterJaAgain = afterJa.withStorageRateLevelsReset(context, "ja")

        assertEquals(afterJa.storageRateLevels, afterJaAgain.storageRateLevels)
    }

    @Test
    fun storageRateLevels_differentForDifferentResetModes() {
        val context = mockk<Context>(relaxed = true)
        every { context.getString(any()) } answers {
            "default-${firstArg<Int>()}"
        }
        val settings = settings()

        val afterDelete = settings.withStorageRateLevelsReset(context, "delete")
        val afterJa = settings.withStorageRateLevelsReset(context, "ja")

        assertTrue(afterDelete.storageRateLevels != afterJa.storageRateLevels)
    }

    // ---- 早明浦ダム以外（StorageRateMessageCategory.OTHER）用のしきい値操作テスト ----

    @Test
    fun otherStorageRateLevels_returnsAll8LevelsWithOtherFieldValues() {
        val settings = settings()

        val levels = settings.otherStorageRateLevels

        assertEquals(8, levels.size)
        assertEquals("80_100", levels[0].threshold)
        assertEquals("O80", levels[0].state)
        assertEquals("ohigh-en", levels[0].msg)
        assertEquals("ohigh-ja", levels[0].msgJa)
        assertEquals("O0", levels[5].state)
        assertEquals("ONA", levels[6].state)
        assertEquals("OINV", levels[7].state)
    }

    @Test
    fun getThresholdState_withOtherCategory_returnsOtherLevel() {
        val settings = settings()

        val level = settings.getThresholdState("40_60", StorageRateMessageCategory.OTHER)

        assertEquals("40_60", level.threshold)
        assertEquals("O40", level.state)
        assertEquals("olow-en", level.msg)
        assertEquals("olow-ja", level.msgJa)
    }

    @Test
    fun getThresholdState_allThresholdsWithOtherCategoryReturnValidLevel() {
        val settings = settings()

        AppSettings.STORAGE_RATE_THRESHOLD_KEYS.forEach { threshold ->
            val level = settings.getThresholdState(threshold, StorageRateMessageCategory.OTHER)
            assertNotNull(level)
            assertEquals(threshold, level.threshold)
        }
    }

    @Test
    fun withStorageRateLevel_updatesOtherLevelOnly() {
        val settings = settings()

        val updated = settings.withStorageRateLevel(
            "0", "OZERO", "ozero-en", "ozero-ja", StorageRateMessageCategory.OTHER
        )

        assertEquals("OZERO", updated.otherState0)
        assertEquals("ozero-en", updated.otherMsg0)
        assertEquals("ozero-ja", updated.otherMsg0Ja)
        // 早明浦ダム用の既存フィールドは変更されない
        assertEquals("0", updated.state0)
        // 他のother*フィールドも変更されない
        assertEquals("O80", updated.otherState80_100)
    }

    @Test
    fun withStorageRateLevel_unknownThresholdWithOtherCategoryReturnsUnchanged() {
        val settings = settings()

        val updated = settings.withStorageRateLevel(
            "invalid", "X", "Y", "Z", StorageRateMessageCategory.OTHER
        )

        assertEquals(settings, updated)
    }

    @Test
    fun withStorageRateLevelsReset_otherDeleteAppliesGeneralPreset() {
        val context = mockk<Context>(relaxed = true)
        every { context.getString(any()) } answers {
            val id = firstArg<Int>()
            when (id) {
                R.string.main_emoji_80_100 -> "😊"
                R.string.main_emoji_all_abnormal -> "😑"
                R.string.storage_default_msg_ja_80_100 -> "ja-80"
                R.string.storage_default_msg_non_ja_80_100 -> "non-80"
                R.string.storage_default_msg_ja_all_abnormal -> "ja-aa"
                R.string.storage_default_msg_non_ja_all_abnormal -> "non-aa"
                else -> "other-$id"
            }
        }
        val settings = settings()

        // OTHERリセットは mode="delete" でも一般向けプリセット（non_ja相当）を適用する
        val reset = settings.withStorageRateLevelsReset(context, "delete", StorageRateMessageCategory.OTHER)

        assertEquals("😊", reset.otherState80_100)
        assertEquals("non-80", reset.otherMsg80_100)
        // 一般レベルのmsgJaは英語既定（non_ja）を採用する
        assertEquals("non-80", reset.otherMsg80_100Ja)
        assertEquals("😑", reset.otherStateAllAbnormal)
        assertEquals("non-aa", reset.otherMsgAllAbnormal)
        // all_abnormal/all_data_invalidのmsgJaのみ日本語既定を採用する
        assertEquals("ja-aa", reset.otherMsgAllAbnormalJa)
        // 早明浦ダム用の既存フィールドは変更されない
        assertEquals("80", reset.state80_100)
        assertEquals("high-en", reset.msg80_100)
    }

    @Test
    fun withStorageRateLevelsReset_otherModeIsIgnoredAlwaysGeneralPreset() {
        val context = mockk<Context>(relaxed = true)
        every { context.getString(any()) } answers {
            "default-${firstArg<Int>()}"
        }
        val settings = settings()

        // OTHERリセットはモード（delete/ja/non_ja）に関係なく一般向けプリセットになる
        val afterDelete = settings.withStorageRateLevelsReset(context, "delete", StorageRateMessageCategory.OTHER)
        val afterJa = settings.withStorageRateLevelsReset(context, "ja", StorageRateMessageCategory.OTHER)
        val afterNonJa = settings.withStorageRateLevelsReset(context, "non_ja", StorageRateMessageCategory.OTHER)

        assertEquals(afterDelete.otherStorageRateLevels, afterJa.otherStorageRateLevels)
        assertEquals(afterDelete.otherStorageRateLevels, afterNonJa.otherStorageRateLevels)
        // 一般向けプリセットのmsgJaは一般レベルが英語既定（non_ja）、all_abnormalが日本語既定（ja）
        assertEquals("default-${R.string.storage_default_msg_non_ja_80_100}", afterDelete.otherMsg80_100Ja)
        assertEquals("default-${R.string.storage_default_msg_ja_all_abnormal}", afterDelete.otherMsgAllAbnormalJa)
        assertEquals("default-${R.string.storage_default_msg_ja_all_data_invalid}", afterDelete.otherMsgAllDataInvalidJa)
    }

    private fun settings(
        autoUpdateInterval: AutoUpdateInterval = AutoUpdateInterval.ONE_WEEK,
        showStorageRateMessage: Boolean = true
    ): AppSettings = AppSettings(
        autoUpdateInterval = autoUpdateInterval,
        showStorageRateMessage = showStorageRateMessage,
        state80_100 = "80",
        msg80_100 = "high-en",
        msg80_100Ja = "high-ja",
        state60_80 = "60",
        msg60_80 = "middle-en",
        msg60_80Ja = "middle-ja",
        state40_60 = "40",
        msg40_60 = "low-en",
        msg40_60Ja = "low-ja",
        state20_40 = "20",
        msg20_40 = "very-low-en",
        msg20_40Ja = "very-low-ja",
        state0_20 = "0-20",
        msg0_20 = "critical-en",
        msg0_20Ja = "critical-ja",
        state0 = "0",
        msg0 = "empty-en",
        msg0Ja = "empty-ja",
        stateAllAbnormal = "NA",
        msgAllAbnormal = "missing-en",
        msgAllAbnormalJa = "missing-ja",
        stateAllDataInvalid = "INV",
        msgAllDataInvalid = "invalid-en",
        msgAllDataInvalidJa = "invalid-ja",
        otherState80_100 = "O80",
        otherMsg80_100 = "ohigh-en",
        otherMsg80_100Ja = "ohigh-ja",
        otherState60_80 = "O60",
        otherMsg60_80 = "omiddle-en",
        otherMsg60_80Ja = "omiddle-ja",
        otherState40_60 = "O40",
        otherMsg40_60 = "olow-en",
        otherMsg40_60Ja = "olow-ja",
        otherState20_40 = "O20",
        otherMsg20_40 = "overly-low-en",
        otherMsg20_40Ja = "overly-low-ja",
        otherState0_20 = "O0-20",
        otherMsg0_20 = "ocritical-en",
        otherMsg0_20Ja = "ocritical-ja",
        otherState0 = "O0",
        otherMsg0 = "oempty-en",
        otherMsg0Ja = "oempty-ja",
        otherStateAllAbnormal = "ONA",
        otherMsgAllAbnormal = "omissing-en",
        otherMsgAllAbnormalJa = "omissing-ja",
        otherStateAllDataInvalid = "OINV",
        otherMsgAllDataInvalid = "oinvalid-en",
        otherMsgAllDataInvalidJa = "oinvalid-ja",
        stateInitialMessage = "INIT",
        msgInitialMessage = "initial-en",
        msgInitialMessageJa = "initial-ja",
        stateNetworkUnavailable = "NET",
        msgNetworkUnavailable = "network-unavailable-en",
        msgNetworkUnavailableJa = "network-unavailable-ja",
        stateLoadingError = "ERR",
        msgLoadingError = "loading-error-en",
        msgLoadingErrorJa = "loading-error-ja"
    )

    private fun jstMillis(value: String): Long =
        TimeUtils.parseJstMillis(value, "yyyy/MM/dd HH:mm") ?: error("Invalid date: $value")
}
