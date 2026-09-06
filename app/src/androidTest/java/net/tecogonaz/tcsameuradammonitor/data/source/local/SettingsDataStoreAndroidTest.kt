// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.data.source.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.test.runTest
import net.tecogonaz.tcsameuradammonitor.R
import net.tecogonaz.tcsameuradammonitor.domain.model.AppSettings
import net.tecogonaz.tcsameuradammonitor.domain.model.AppTheme
import net.tecogonaz.tcsameuradammonitor.domain.model.AutoUpdateInterval
import net.tecogonaz.tcsameuradammonitor.domain.model.DebugDatSelectionMode
import net.tecogonaz.tcsameuradammonitor.domain.model.MainCardExpansionKey
import net.tecogonaz.tcsameuradammonitor.domain.model.RealtimeDataSource
import net.tecogonaz.tcsameuradammonitor.domain.model.StorageRateMessageCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/**
 * Jetpack DataStore (Preferences) を用いて設定データを永続化する [SettingsDataStore] の Instrumentation テストクラス。
 * テスト用のダミーファイル名での DataStore インスタンス生成と後処理、
 * コア設定項目（テーマ、自動更新、通知表示、更新間隔、絵文字/多言語表示しきい値メッセージ）の書き込み・読み込み確認、
 * 早明浦ダム用（既存フィールド）と早明浦ダム以外用（other* フィールド）の2系統の永続化確認、
 * および `triggerRefresh` 実行時に Flow を介して現在の設定値が再配信される動作を検証します。
 */
@RunWith(AndroidJUnit4::class)
@SmallTest
class SettingsDataStoreAndroidTest {
    private lateinit var settingsDataStore: SettingsDataStore
    private lateinit var context: Context
    private lateinit var dataStoreFileName: String

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        dataStoreFileName = "test_settings_${UUID.randomUUID()}"
        settingsDataStore = SettingsDataStore.createForTest(context, dataStoreFileName)
    }

    @org.junit.After
    fun tearDown() {
        File(context.filesDir, "datastore/$dataStoreFileName.preferences_pb").delete()
    }

    @Test
    fun updateSettings_persistsCoreSchedulingAndMessageValues() = runTest {
        settingsDataStore.updateSettings {
            AppSettings(
                theme = AppTheme.DARK,
                autoUpdateEnabled = true,
                showNotification = false,
                autoUpdateInterval = AutoUpdateInterval.TWELVE_HOURS,
                initialAutoUpdateDialogShown = true,
                autoUpdateCustomTimingMillis12Hours = 123456L,
                targetDamId = "9999999999999",
                state80_100 = "state",
                msg80_100 = "stored-en",
                msg80_100Ja = "stored-ja",
                stateLoadingError = "!",
                msgLoadingError = "failed",
                msgLoadingErrorJa = "失敗"
            )
        }

        val stored = settingsDataStore.appSettingsFlow.first()

        assertEquals(AppTheme.DARK, stored.theme)
        assertEquals(true, stored.autoUpdateEnabled)
        assertEquals(false, stored.showNotification)
        assertEquals(AutoUpdateInterval.TWELVE_HOURS, stored.autoUpdateInterval)
        assertEquals(true, stored.initialAutoUpdateDialogShown)
        assertEquals(123456L, stored.currentCustomTimingMillis)
        assertEquals("9999999999999", stored.targetDamId)
        // 早明浦ダム用（isSameura=true）は既存フィールドのメッセージを返す
        assertEquals("state" to "stored-en", stored.getStateForPercentage(80.0f, isJapanese = false, isSameura = true))
        assertEquals("! failed", stored.getLoadingErrorText(isJapanese = false))
        assertEquals("! 失敗", stored.getLoadingErrorText(isJapanese = true))
    }

    @Test
    fun updateSettings_persistsOtherStorageRateMessageValues() = runTest {
        settingsDataStore.updateSettings {
            it.copy(
                otherState80_100 = "Ostate",
                otherMsg80_100 = "ostored-en",
                otherMsg80_100Ja = "ostored-ja",
                otherState0 = "O0",
                otherMsg0 = "oempty-en",
                otherMsg0Ja = "oempty-ja",
                otherStateAllAbnormal = "ONA",
                otherMsgAllAbnormal = "omissing-en",
                otherMsgAllAbnormalJa = "omissing-ja",
                stateAllAbnormal = "SNA",
                msgAllAbnormal = "smissing-en",
                msgAllAbnormalJa = "smissing-ja"
            )
        }

        val stored = settingsDataStore.appSettingsFlow.first()

        // 早明浦ダム以外（isSameura=false）は other* フィールドのメッセージを返す
        assertEquals("Ostate" to "ostored-en", stored.getStateForPercentage(80.0f, isJapanese = false))
        assertEquals("Ostate" to "ostored-ja", stored.getStateForPercentage(80.0f, isJapanese = true))
        assertEquals("O0" to "oempty-ja", stored.getStateForPercentage(0f, isJapanese = true))
        // percentage==null は isSameura に関わらず既存フィールドを返す
        assertEquals("SNA" to "smissing-ja", stored.getStateForPercentage(null, isJapanese = true))
        // 早明浦ダム用（isSameura=true）の既存フィールドはリソース既定値のまま
        assertEquals(context.getString(R.string.main_emoji_80_100), stored.state80_100)
    }

    @Test
    fun updateSettings_withOtherCategoryLevelUpdate_persistsOtherFieldsIndependently() = runTest {
        settingsDataStore.updateSettings {
            it.withStorageRateLevel(
                "80_100", "O80", "o-en", "o-ja", StorageRateMessageCategory.OTHER
            )
        }

        val stored = settingsDataStore.appSettingsFlow.first()

        assertEquals("O80", stored.otherState80_100)
        assertEquals("o-en", stored.otherMsg80_100)
        assertEquals("o-ja", stored.otherMsg80_100Ja)
        // 早明浦ダム用の既存フィールドはリソース既定値のまま
        assertEquals(context.getString(R.string.main_emoji_80_100), stored.state80_100)
    }

    @Test
    fun resetOtherStorageRateMessages_restoresGeneralPreset() = runTest {
        settingsDataStore.updateSettings {
            AppSettings(
                otherState80_100 = "custom",
                otherMsg80_100 = "custom-en",
                otherMsg80_100Ja = "custom-ja",
                otherStateAllAbnormal = "custom",
                otherMsgAllAbnormal = "custom-en",
                otherMsgAllAbnormalJa = "custom-ja"
            )
        }

        // OTHERリセット（mode=delete）は一般向けプリセット（non_ja相当）へ戻す
        settingsDataStore.updateSettings {
            it.withStorageRateLevelsReset(context, "delete", StorageRateMessageCategory.OTHER)
        }

        val stored = settingsDataStore.appSettingsFlow.first()

        assertEquals(context.getString(R.string.main_emoji_80_100), stored.otherState80_100)
        assertEquals(context.getString(R.string.storage_default_msg_non_ja_80_100), stored.otherMsg80_100)
        // 一般レベルのmsgJaは英語既定（non_ja）を採用する
        assertEquals(context.getString(R.string.storage_default_msg_non_ja_80_100), stored.otherMsg80_100Ja)
        assertEquals(context.getString(R.string.main_emoji_all_abnormal), stored.otherStateAllAbnormal)
        assertEquals(context.getString(R.string.storage_default_msg_non_ja_all_abnormal), stored.otherMsgAllAbnormal)
        // all_abnormal/all_data_invalidのmsgJaのみ日本語既定を採用する
        assertEquals(context.getString(R.string.storage_default_msg_ja_all_abnormal), stored.otherMsgAllAbnormalJa)
    }

    @Test
    fun realtimeDataSource_missingKey_defaultsToSudmonitor() = runTest {
        val stored = settingsDataStore.appSettingsFlow.first()

        assertEquals(RealtimeDataSource.SUDMONITOR, stored.realtimeDataSource)
        assertEquals(null, stored.originFetchedAtMillis)
    }

    @Test
    fun historicalDataSource_missingKey_defaultsToSudmonitor() = runTest {
        val stored = settingsDataStore.appSettingsFlow.first()

        assertEquals(RealtimeDataSource.SUDMONITOR, stored.historicalDataSource)
    }

    @Test
    fun debugDatSelections_roundTripIndependently() = runTest {
        settingsDataStore.updateSettings {
            it.copy(
                debugRealtimeDatFileMode = DebugDatSelectionMode.USER_SELECTED,
                debugRealtimeDatFileUri = "content://debug/realtime.dat",
                debugHistoricalDailyDatFileMode = DebugDatSelectionMode.LATEST,
                debugHistoricalDailyDatFileUri = null,
                debugRealtimeDataStartMillis = 1000L,
                debugRealtimeDataEndMillis = 2000L,
                debugRealtimeDataPeriodAutoAdvanceEnabled = false
            )
        }

        val stored = settingsDataStore.appSettingsFlow.first()

        assertEquals(DebugDatSelectionMode.USER_SELECTED, stored.debugRealtimeDatFileMode)
        assertEquals("content://debug/realtime.dat", stored.debugRealtimeDatFileUri)
        assertEquals(DebugDatSelectionMode.LATEST, stored.debugHistoricalDailyDatFileMode)
        assertNull(stored.debugHistoricalDailyDatFileUri)
        assertEquals(1000L, stored.debugRealtimeDataStartMillis)
        assertEquals(2000L, stored.debugRealtimeDataEndMillis)
        assertEquals(false, stored.debugRealtimeDataPeriodAutoAdvanceEnabled)
    }

    @Test
    fun historicalDataSource_roundTripsAndKeepsRealtimeSourceIndependent() = runTest {
        settingsDataStore.updateSettings {
            it.copy(historicalDataSource = RealtimeDataSource.MLIT_DIRECT)
        }

        val stored = settingsDataStore.appSettingsFlow.first()

        assertEquals(RealtimeDataSource.MLIT_DIRECT, stored.historicalDataSource)
        assertEquals(RealtimeDataSource.SUDMONITOR, stored.realtimeDataSource)

        settingsDataStore.updateSettings {
            it.copy(historicalDataSource = RealtimeDataSource.SUDMONITOR)
        }

        val restored = settingsDataStore.appSettingsFlow.first()

        assertEquals(RealtimeDataSource.SUDMONITOR, restored.historicalDataSource)
    }

    @Test
    fun realtimeDataSource_roundTripsAndPersistsOriginFetchedAt() = runTest {
        settingsDataStore.updateSettings {
            it.copy(realtimeDataSource = RealtimeDataSource.SUDMONITOR, originFetchedAtMillis = 123456789L)
        }

        val stored = settingsDataStore.appSettingsFlow.first()

        assertEquals(RealtimeDataSource.SUDMONITOR, stored.realtimeDataSource)
        assertEquals(123456789L, stored.originFetchedAtMillis)
    }

    @Test
    fun realtimeDataSource_missingKey_defaultsToSudmonitorRegardlessOfTargetDam() = runTest {
        settingsDataStore.updateSettings { it.copy(targetDamId = "1368010125140") }

        val stored = settingsDataStore.appSettingsFlow.first()

        assertEquals("1368010125140", stored.targetDamId)
        assertEquals(RealtimeDataSource.SUDMONITOR, stored.realtimeDataSource)
    }

    @Test
    fun triggerRefresh_reemitsCurrentSettings() = runTest {
        settingsDataStore.updateSettings { it.copy(targetDamId = "1368080700010") }

        val emissions = Channel<AppSettings>(capacity = Channel.UNLIMITED)
        val realDispatcher = Dispatchers.Default.limitedParallelism(1)
        val collectJob = launch(realDispatcher) {
            settingsDataStore.appSettingsFlow.collect { emissions.send(it) }
        }
        val first = withContext(realDispatcher) {
            withTimeout(5_000L) { emissions.receive() }
        }

        settingsDataStore.triggerRefresh()
        val second = withContext(realDispatcher) {
            withTimeout(5_000L) { emissions.receive() }
        }
        collectJob.cancel()

        assertEquals("1368080700010", first.targetDamId)
        assertEquals("1368080700010", second.targetDamId)
    }

    @Test
    fun mainCardExpansionState_defaultsExpandedAndPersistsEveryModeSpecificToggle() = runTest {
        val initial = settingsDataStore.mainCardExpansionStateFlow.first()
        MainCardExpansionKey.entries.forEach { key ->
            assertTrue("Expected $key to be expanded initially", initial[key])
        }

        MainCardExpansionKey.entries.forEach { key ->
            settingsDataStore.toggleMainCardExpansion(key)
        }
        val collapsed = settingsDataStore.mainCardExpansionStateFlow.first()

        MainCardExpansionKey.entries.forEach { key ->
            assertEquals("Expected $key to persist collapsed", false, collapsed[key])
        }
    }

    @Test
    fun mainCardExpansionState_keepsRealtimeAndHistoricalCardsIndependent() = runTest {
        settingsDataStore.toggleMainCardExpansion(MainCardExpansionKey.REALTIME_OBSERVATION)

        val stored = settingsDataStore.mainCardExpansionStateFlow.first()

        assertEquals(false, stored[MainCardExpansionKey.REALTIME_OBSERVATION])
        assertEquals(true, stored[MainCardExpansionKey.HISTORICAL_OBSERVATION])
    }

    @Test
    fun togglingMainCardExpansion_doesNotReemitAppSettings() = runTest {
        val emissions = Channel<AppSettings>(capacity = Channel.UNLIMITED)
        val realDispatcher = Dispatchers.Default.limitedParallelism(1)
        val collectJob = launch(realDispatcher) {
            settingsDataStore.appSettingsFlow.collect { emissions.send(it) }
        }
        withContext(realDispatcher) {
            withTimeout(5_000L) { emissions.receive() }
        }

        settingsDataStore.toggleMainCardExpansion(MainCardExpansionKey.REALTIME_LINKS)
        val unexpected = withContext(realDispatcher) {
            withTimeoutOrNull(500L) { emissions.receive() }
        }
        collectJob.cancel()

        assertNull(unexpected)
    }
}
