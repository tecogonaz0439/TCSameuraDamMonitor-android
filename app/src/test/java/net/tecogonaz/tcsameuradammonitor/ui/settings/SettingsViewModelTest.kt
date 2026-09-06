// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.ui.settings

import android.content.Context
import android.net.Uri
import android.util.Log
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.tecogonaz.tcsameuradammonitor.R
import net.tecogonaz.tcsameuradammonitor.data.source.local.DebugDatSourceReader
import net.tecogonaz.tcsameuradammonitor.domain.model.AppSettings
import net.tecogonaz.tcsameuradammonitor.domain.model.AutoUpdateInterval
import net.tecogonaz.tcsameuradammonitor.domain.model.DebugDatSelectionMode
import net.tecogonaz.tcsameuradammonitor.domain.model.DebugSimulateMode
import net.tecogonaz.tcsameuradammonitor.domain.model.RealtimeDataSource
import net.tecogonaz.tcsameuradammonitor.domain.repository.DebugDataSessionRepository
import net.tecogonaz.tcsameuradammonitor.domain.repository.SudmonitorHistoryRepository
import net.tecogonaz.tcsameuradammonitor.domain.repository.SudmonitorHistoryTrigger
import net.tecogonaz.tcsameuradammonitor.testutil.FakeDamDataRepository
import net.tecogonaz.tcsameuradammonitor.testutil.FakeDamWorkManagerGateway
import net.tecogonaz.tcsameuradammonitor.testutil.FakeDatabaseMaintenanceRepository
import net.tecogonaz.tcsameuradammonitor.testutil.FakeDebugLogRepository
import net.tecogonaz.tcsameuradammonitor.testutil.FakeSettingsRepository
import net.tecogonaz.tcsameuradammonitor.testutil.MainDispatcherRule
import net.tecogonaz.tcsameuradammonitor.util.TimeUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.nio.charset.Charset

/**
 * [SettingsViewModel] のユニットテストクラス。
 * アプリケーション設定（自動更新の有効/無効、更新間隔の変更、デバッグ用擬似モードの設定など）に関連する
 * ViewModel のビジネスロジックや、それに連動する [SettingsRepository]・[DamWorkManagerGateway] の挙動を検証します。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun updateAutoUpdateEnabled_trueSavesNextTimingAndSchedulesWork() = runTest(mainDispatcherRule.testDispatcher) {
        val next = jstMillis("2099/01/01 05:15")
        val fixture = createViewModel(
            settings = stableSettings()
                .copy(autoUpdateInterval = AutoUpdateInterval.ONE_HOUR, autoUpdateCustomTimingMillisHourly = next)
        )

        fixture.viewModel.updateAutoUpdateEnabled(true)
        advanceUntilIdle()

        assertTrue(fixture.settingsRepository.current.autoUpdateEnabled)
        assertTrue(fixture.settingsRepository.current.isFirstRunAfterReschedule)
        assertEquals(next, fixture.settingsRepository.current.nextScheduledUpdateMillis)
        assertEquals(next, fixture.workGateway.scheduled.single().second)
        assertEquals("Auto update enabled.", fixture.debugLogRepository.addedEntries.single().first)
    }

    @Test
    fun updateAutoUpdateEnabled_falseClearsNextTimingAndCancelsWork() = runTest(mainDispatcherRule.testDispatcher) {
        val fixture = createViewModel(
            settings = stableSettings().copy(
                autoUpdateEnabled = true,
                nextScheduledUpdateMillis = jstMillis("2099/01/01 05:15")
            )
        )

        fixture.viewModel.updateAutoUpdateEnabled(false)
        advanceUntilIdle()

        assertEquals(false, fixture.settingsRepository.current.autoUpdateEnabled)
        assertEquals(0L, fixture.settingsRepository.current.nextScheduledUpdateMillis)
        assertEquals(1, fixture.workGateway.cancelCount)
        assertEquals("Auto update disabled.", fixture.debugLogRepository.addedEntries.single().first)
    }

    @Test
    fun updateAutoUpdateInterval_whenDisabledUpdatesIntervalWithoutScheduling() = runTest(mainDispatcherRule.testDispatcher) {
        val fixture = createViewModel(settings = stableSettings().copy(autoUpdateEnabled = false))

        fixture.viewModel.updateAutoUpdateInterval(AutoUpdateInterval.TWELVE_HOURS)
        advanceUntilIdle()

        assertEquals(AutoUpdateInterval.TWELVE_HOURS, fixture.settingsRepository.current.autoUpdateInterval)
        assertTrue(fixture.workGateway.scheduled.isEmpty())
        assertEquals("Auto update interval changed.", fixture.debugLogRepository.addedEntries.single().first)
    }

    @Test
    fun updateAutoUpdateInterval_whenEnabledReschedulesWorkAndSetsFirstRunFlag() = runTest(mainDispatcherRule.testDispatcher) {
        val next = jstMillis("2099/01/01 17:15")
        val fixture = createViewModel(
            settings = stableSettings().copy(
                autoUpdateEnabled = true,
                autoUpdateCustomTimingMillis12Hours = next
            )
        )

        fixture.viewModel.updateAutoUpdateInterval(AutoUpdateInterval.TWELVE_HOURS)
        advanceUntilIdle()

        assertEquals(AutoUpdateInterval.TWELVE_HOURS, fixture.settingsRepository.current.autoUpdateInterval)
        assertEquals(next, fixture.settingsRepository.current.nextScheduledUpdateMillis)
        assertTrue(fixture.settingsRepository.current.isFirstRunAfterReschedule)
        assertEquals(next, fixture.workGateway.scheduled.single().second)
    }

    @Test
    fun updateAutoUpdateCustomTiming_whenDisabledSavesTimingWithoutScheduling() = runTest(mainDispatcherRule.testDispatcher) {
        val next = jstMillis("2099/01/02 06:45")
        val fixture = createViewModel(
            settings = stableSettings()
                .copy(autoUpdateEnabled = false, autoUpdateInterval = AutoUpdateInterval.ONE_HOUR)
        )

        fixture.viewModel.updateAutoUpdateCustomTiming(next)
        advanceUntilIdle()

        val updated = fixture.settingsRepository.current
        val jst = java.time.ZoneId.of("Asia/Tokyo")
        val hourlyZdt = java.time.Instant.ofEpochMilli(updated.autoUpdateCustomTimingMillisHourly).atZone(jst)
        assertEquals(45, hourlyZdt.minute)
        assertEquals(next, updated.nextScheduledUpdateMillis)
        assertTrue(fixture.workGateway.scheduled.isEmpty())
        assertEquals("Auto update next timing set.", fixture.debugLogRepository.addedEntries.single().first)
    }

    @Test
    fun updateAutoUpdateCustomTiming_propagatesToAllIntervalTimings() = runTest(mainDispatcherRule.testDispatcher) {
        val next = jstMillis("2099/01/02 20:30")
        val fixture = createViewModel(
            settings = stableSettings()
                .copy(autoUpdateEnabled = true, autoUpdateInterval = AutoUpdateInterval.ONE_DAY)
        )

        fixture.viewModel.updateAutoUpdateCustomTiming(next)
        advanceUntilIdle()

        val updated = fixture.settingsRepository.current
        val jst = java.time.ZoneId.of("Asia/Tokyo")

        val weeklyZdt = java.time.Instant.ofEpochMilli(updated.autoUpdateCustomTimingMillisWeekly).atZone(jst)
        assertEquals(20, weeklyZdt.hour)
        assertEquals(30, weeklyZdt.minute)

        val dailyZdt = java.time.Instant.ofEpochMilli(updated.autoUpdateCustomTimingMillisDaily).atZone(jst)
        assertEquals(20, dailyZdt.hour)
        assertEquals(30, dailyZdt.minute)

        val twelveZdt = java.time.Instant.ofEpochMilli(updated.autoUpdateCustomTimingMillis12Hours).atZone(jst)
        assertTrue(twelveZdt.hour == 8 || twelveZdt.hour == 20)
        assertEquals(30, twelveZdt.minute)

        val hourlyZdt = java.time.Instant.ofEpochMilli(updated.autoUpdateCustomTimingMillisHourly).atZone(jst)
        assertEquals(30, hourlyZdt.minute)

        assertEquals(next, updated.nextScheduledUpdateMillis)
        assertEquals(next, fixture.workGateway.scheduled.single().second)
        assertEquals("Auto update next timing set.", fixture.debugLogRepository.addedEntries.single().first)
    }

    @Test
    fun updateDebugSimulateMode_updatesSettings() = runTest(mainDispatcherRule.testDispatcher) {
        val fixture = createViewModel(settings = stableSettings())

        fixture.viewModel.updateDebugSimulateMode(DebugSimulateMode.LOADING_FAILURE)
        advanceUntilIdle()

        assertEquals(DebugSimulateMode.LOADING_FAILURE, fixture.settingsRepository.current.debugSimulateMode)
    }

    @Test
    fun updateDebugModeEnabled_delegatesBackupAndRestore() = runTest(mainDispatcherRule.testDispatcher) {
        val fixture = createViewModel(settings = stableSettings())

        fixture.viewModel.updateDebugModeEnabled(true)
        advanceUntilIdle()
        fixture.viewModel.updateDebugModeEnabled(false)
        advanceUntilIdle()

        io.mockk.coVerify(exactly = 1) { fixture.debugDataSessionRepository.enterDebugMode() }
        io.mockk.coVerify(exactly = 1) { fixture.debugDataSessionRepository.exitDebugMode() }
        assertFalse(fixture.viewModel.isDebugModeSwitching.value)
    }

    @Test
    fun updateDebugModeEnabled_backupFailureShowsError() = runTest(mainDispatcherRule.testDispatcher) {
        val fixture = createViewModel(settings = stableSettings())
        io.mockk.coEvery { fixture.debugDataSessionRepository.enterDebugMode() } returns
            Result.failure(IllegalStateException("backup failed"))

        fixture.viewModel.updateDebugModeEnabled(true)
        advanceUntilIdle()

        assertEquals(
            "string-${R.string.settings_debug_backup_failed}",
            fixture.viewModel.debugModeSwitchError.value
        )
        assertFalse(fixture.viewModel.isDebugModeSwitching.value)
    }

    @Test
    fun updateDebugRealtimeDatFileUri_setsUserSelectedModeAndClearsPeriod() = runTest(mainDispatcherRule.testDispatcher) {
        val fixture = createViewModel(
            settings = stableSettings().copy(
                debugRealtimeDatFileMode = DebugDatSelectionMode.BUNDLED,
                debugRealtimeDataEndMillis = jstMillis("2099/01/01 05:15")
            )
        )

        fixture.viewModel.updateDebugRealtimeDatFileUri("content://test/file.dat")
        advanceUntilIdle()

        assertEquals(DebugDatSelectionMode.USER_SELECTED, fixture.settingsRepository.current.debugRealtimeDatFileMode)
        assertEquals("content://test/file.dat", fixture.settingsRepository.current.debugRealtimeDatFileUri)
        assertEquals(null, fixture.settingsRepository.current.debugRealtimeDataEndMillis)
    }

    @Test
    fun updateDebugRealtimeDatFileMode_changesModeAndClearsUriAndPeriod() = runTest(mainDispatcherRule.testDispatcher) {
        val fixture = createViewModel(
            settings = stableSettings().copy(
                debugRealtimeDatFileMode = DebugDatSelectionMode.USER_SELECTED,
                debugRealtimeDatFileUri = "content://test/file.dat",
                debugRealtimeDataEndMillis = jstMillis("2099/01/01 05:15")
            )
        )

        fixture.viewModel.updateDebugRealtimeDatFileMode(DebugDatSelectionMode.LATEST)
        advanceUntilIdle()

        assertEquals(DebugDatSelectionMode.LATEST, fixture.settingsRepository.current.debugRealtimeDatFileMode)
        assertEquals(null, fixture.settingsRepository.current.debugRealtimeDatFileUri)
        assertEquals(null, fixture.settingsRepository.current.debugRealtimeDataEndMillis)
    }

    @Test
    fun updateDebugRealtimeDataPeriodAutoAdvanceEnabled_updatesSettings() = runTest(mainDispatcherRule.testDispatcher) {
        val fixture = createViewModel(settings = stableSettings())

        fixture.viewModel.updateDebugRealtimeDataPeriodAutoAdvanceEnabled(false)
        advanceUntilIdle()

        assertFalse(fixture.settingsRepository.current.debugRealtimeDataPeriodAutoAdvanceEnabled)
    }

    @Test
    fun updateDebugHistoricalDailyDatFileUri_doesNotChangeRealtimeSelection() =
        runTest(mainDispatcherRule.testDispatcher) {
            val fixture = createViewModel(
                settings = stableSettings().copy(debugRealtimeDatFileMode = DebugDatSelectionMode.LATEST)
            )

            fixture.viewModel.updateDebugHistoricalDailyDatFileUri("content://test/history.dat")
            advanceUntilIdle()

            assertEquals(DebugDatSelectionMode.LATEST, fixture.settingsRepository.current.debugRealtimeDatFileMode)
            assertEquals(
                DebugDatSelectionMode.USER_SELECTED,
                fixture.settingsRepository.current.debugHistoricalDailyDatFileMode
            )
            assertEquals(
                "content://test/history.dat",
                fixture.settingsRepository.current.debugHistoricalDailyDatFileUri
            )
        }

    @Test
    fun toggleDebugSettingsVisibility_whenVisibleDisablesDebugSettings() = runTest(mainDispatcherRule.testDispatcher) {
        val fixture = createViewModel(
            settings = stableSettings().copy(
                debugSettingsVisible = true,
                debugModeEnabled = true,
                debugSimulateMode = DebugSimulateMode.LOADING_FAILURE
            )
        )

        fixture.viewModel.toggleDebugSettingsVisibility()
        advanceUntilIdle()

        assertFalse(fixture.settingsRepository.current.debugSettingsVisible)
        assertFalse(fixture.settingsRepository.current.debugModeEnabled)
        assertEquals(DebugSimulateMode.NONE, fixture.settingsRepository.current.debugSimulateMode)
    }

    
    
    
    
    
    
    

    @Test
    fun updateShowNotification_true_setsShowNotificationInRepository() = runTest(mainDispatcherRule.testDispatcher) {
        val fixture = createViewModel(settings = stableSettings().copy(showNotification = false))

        fixture.viewModel.updateShowNotification(true)
        advanceUntilIdle()

        assertTrue(fixture.settingsRepository.current.showNotification)
    }

    @Test
    fun updateShowNotification_false_unsetsShowNotificationInRepository() = runTest(mainDispatcherRule.testDispatcher) {
        val fixture = createViewModel(settings = stableSettings().copy(showNotification = true))

        fixture.viewModel.updateShowNotification(false)
        advanceUntilIdle()

        assertFalse(fixture.settingsRepository.current.showNotification)
    }

    @Test
    fun vacuumDatabase_failureReturnsLocalizedMessage() = runTest(mainDispatcherRule.testDispatcher) {
        val fixture = createViewModel(settings = stableSettings())

        fixture.databaseMaintenanceRepository.error = IllegalStateException("boom")
        val failure = fixture.viewModel.vacuumDatabase()

        assertTrue(failure.startsWith("string-"))
    }

    @Test
    fun resolveExportFileName_usesDefaultAndUtf8Suffix() = runTest {
        val fixture = createViewModel(settings = stableSettings())

        assertEquals("realtime.dat", fixture.viewModel.resolveExportFileName(DebugDatKind.REALTIME, false))
        assertEquals("realtime_utf8.dat", fixture.viewModel.resolveExportFileName(DebugDatKind.REALTIME, true))

        fixture.damDataRepository.testLastDatFileName = "current.dat"

        assertEquals("current.dat", fixture.viewModel.resolveExportFileName(DebugDatKind.REALTIME, false))
        assertEquals("current_utf8.dat", fixture.viewModel.resolveExportFileName(DebugDatKind.REALTIME, true))
    }

    @Test
    fun exportDatFile_writesRealtimeBytesAndUtf8Bom() = runTest(mainDispatcherRule.testDispatcher) {
        val outputUri = mockk<Uri>()
        val fixture = createViewModel(
            settings = stableSettings().copy(debugModeEnabled = false)
        )
        val currentBytes = "年月日,時刻\n2026/05/01,01:00".toByteArray(Charset.forName("Shift_JIS"))
        fixture.damDataRepository.testLastRealtimeRawDatBytes = currentBytes
        fixture.damDataRepository.testLastRawDatBytes = currentBytes

        val error = fixture.viewModel.exportDatFile(DebugDatKind.REALTIME, outputUri, useUtf8 = true)

        assertEquals(null, error)
        val bytes = fixture.datExporter.writes.single().second
        assertEquals(outputUri, fixture.datExporter.writes.single().first)
        assertEquals(0xEF.toByte(), bytes[0])
        assertEquals(0xBB.toByte(), bytes[1])
        assertEquals(0xBF.toByte(), bytes[2])
        assertTrue(String(bytes.drop(3).toByteArray(), Charsets.UTF_8).contains("2026/05/01"))
    }

    @Test
    fun needsStorageRateResetConfirmation_afterDeleteReturnsFalseForAllModes() = runTest(mainDispatcherRule.testDispatcher) {
        val fixture = createViewModel(
            settings = afterDeleteSettings(),
            configureContext = configureForAfterDelete()
        )
        val job = launch { fixture.viewModel.appSettings.collect {} }
        advanceUntilIdle()

        assertFalse(fixture.viewModel.needsStorageRateResetConfirmation("delete"))
        assertFalse(fixture.viewModel.needsStorageRateResetConfirmation("ja"))
        assertFalse(fixture.viewModel.needsStorageRateResetConfirmation("non_ja"))

        job.cancel()
    }

    @Test
    fun needsStorageRateResetConfirmation_afterJaResetReturnsFalseForAllModes() = runTest(mainDispatcherRule.testDispatcher) {
        val fixture = createViewModel(
            settings = afterJaSettings(),
            configureContext = configureForAfterJa()
        )
        val job = launch { fixture.viewModel.appSettings.collect {} }
        advanceUntilIdle()

        assertFalse(fixture.viewModel.needsStorageRateResetConfirmation("delete"))
        assertFalse(fixture.viewModel.needsStorageRateResetConfirmation("ja"))
        assertFalse(fixture.viewModel.needsStorageRateResetConfirmation("non_ja"))

        job.cancel()
    }

    @Test
    fun needsStorageRateResetConfirmation_withEdits_returnsTrue() = runTest(mainDispatcherRule.testDispatcher) {
        val editedSettings = stableSettings().copy(
            state80_100 = "custom",
            msg80_100 = "custom-msg"
        )
        val fixture = createViewModel(settings = editedSettings)
        val job = launch { fixture.viewModel.appSettings.collect {} }
        advanceUntilIdle()

        assertTrue(fixture.viewModel.needsStorageRateResetConfirmation("delete"))
        assertTrue(fixture.viewModel.needsStorageRateResetConfirmation("ja"))
        assertTrue(fixture.viewModel.needsStorageRateResetConfirmation("non_ja"))

        job.cancel()
    }

    @Test
    fun needsOtherStorageRateResetConfirmation_withPresetValues_returnsFalse() = runTest(mainDispatcherRule.testDispatcher) {
        val fixture = createViewModel(
            settings = otherPresetSettings()
        )
        val job = launch { fixture.viewModel.appSettings.collect {} }
        advanceUntilIdle()

        // 早明浦ダム以外用のメッセージが一般向けプリセットと一致していれば確認は不要
        assertFalse(fixture.viewModel.needsOtherStorageRateResetConfirmation())

        job.cancel()
    }

    @Test
    fun needsOtherStorageRateResetConfirmation_withEdits_returnsTrue() = runTest(mainDispatcherRule.testDispatcher) {
        val editedSettings = stableSettings().copy(
            otherState80_100 = "custom",
            otherMsg80_100 = "custom-msg"
        )
        val fixture = createViewModel(settings = editedSettings)
        val job = launch { fixture.viewModel.appSettings.collect {} }
        advanceUntilIdle()

        // 早明浦ダム以外用のメッセージを編集すると確認が必要になる
        assertTrue(fixture.viewModel.needsOtherStorageRateResetConfirmation())

        job.cancel()
    }

    @Test
    fun resetOtherStorageRateMessages_savesGeneralPresetToOtherFields() = runTest(mainDispatcherRule.testDispatcher) {
        val fixture = createViewModel(settings = stableSettings())

        fixture.viewModel.resetOtherStorageRateMessages()
        advanceUntilIdle()

        val current = fixture.settingsRepository.current
        // 一般向けプリセット（non_ja相当）がother*フィールドに設定される
        assertEquals("string-${R.string.main_emoji_80_100}", current.otherState80_100)
        assertEquals("string-${R.string.storage_default_msg_non_ja_80_100}", current.otherMsg80_100)
        assertEquals("string-${R.string.storage_default_msg_non_ja_80_100}", current.otherMsg80_100Ja)
        assertEquals("string-${R.string.main_emoji_all_abnormal}", current.otherStateAllAbnormal)
        assertEquals("string-${R.string.storage_default_msg_non_ja_all_abnormal}", current.otherMsgAllAbnormal)
        assertEquals("string-${R.string.storage_default_msg_ja_all_abnormal}", current.otherMsgAllAbnormalJa)
        // 早明浦ダム用の既存フィールドは変更されない
        assertEquals("", current.state80_100)
        assertEquals("", current.msg80_100)
    }

    @Test
    fun needsOtherMessagesResetConfirmation_withNoEdits_returnsFalse() = runTest(mainDispatcherRule.testDispatcher) {
        val noEditSettings = stableSettings().copy(
            stateInitialMessage = "S0", msgInitialMessage = "M0", msgInitialMessageJa = "J0",
            stateNetworkUnavailable = "S1", msgNetworkUnavailable = "M1", msgNetworkUnavailableJa = "J1",
            stateLoadingError = "S2", msgLoadingError = "M2", msgLoadingErrorJa = "J2",
            stateDataDistributionStopped = "S3", msgDataDistributionStopped = "M3", msgDataDistributionStoppedJa = "J3",
            stateDataDistributionResumed = "S4", msgDataDistributionResumed = "M4", msgDataDistributionResumedJa = "J4"
        )
        val fixture = createViewModel(
            settings = noEditSettings,
            configureContext = { context ->
                every { context.getString(R.string.main_emoji_initial_message) } returns "S0"
                every { context.getString(R.string.storage_default_msg_non_ja_initial_message) } returns "M0"
                every { context.getString(R.string.storage_default_msg_ja_initial_message) } returns "J0"
                every { context.getString(R.string.main_emoji_network_unavailable) } returns "S1"
                every { context.getString(R.string.storage_default_msg_non_ja_network_unavailable) } returns "M1"
                every { context.getString(R.string.storage_default_msg_ja_network_unavailable) } returns "J1"
                every { context.getString(R.string.main_emoji_loading_error) } returns "S2"
                every { context.getString(R.string.storage_default_msg_non_ja_loading_error) } returns "M2"
                every { context.getString(R.string.storage_default_msg_ja_loading_error) } returns "J2"
                every { context.getString(R.string.main_emoji_data_distribution_stopped) } returns "S3"
                every { context.getString(R.string.storage_default_msg_non_ja_data_distribution_stopped) } returns "M3"
                every { context.getString(R.string.storage_default_msg_ja_data_distribution_stopped) } returns "J3"
                every { context.getString(R.string.main_emoji_data_distribution_resumed) } returns "S4"
                every { context.getString(R.string.storage_default_msg_non_ja_data_distribution_resumed) } returns "M4"
                every { context.getString(R.string.storage_default_msg_ja_data_distribution_resumed) } returns "J4"
            }
        )
        val job = launch { fixture.viewModel.appSettings.collect {} }
        advanceUntilIdle()

        assertFalse(fixture.viewModel.needsOtherMessagesResetConfirmation())

        job.cancel()
    }

    @Test
    fun needsOtherMessagesResetConfirmation_withEdits_returnsTrue() = runTest(mainDispatcherRule.testDispatcher) {
        val fixture = createViewModel(settings = stableSettings())
        val job = launch { fixture.viewModel.appSettings.collect {} }
        advanceUntilIdle()

        assertTrue(fixture.viewModel.needsOtherMessagesResetConfirmation())

        job.cancel()
    }

    @Test
    fun changeTargetDam_updatesSettingsClearsCacheAndStartsReplacementInitialLoad() = runTest(mainDispatcherRule.testDispatcher) {
        val fixture = createViewModel(
            settings = stableSettings().copy(
                targetDamId = "1368080700010",
                lastLoadResultMessage = "old result",
                wasLastDataAllInvalid = true
            )
        )
        var completed = false

        fixture.viewModel.changeTargetDam("1368010125140") {
            completed = true
        }
        advanceUntilIdle()

        assertEquals("1368010125140", fixture.settingsRepository.current.targetDamId)
        assertEquals("", fixture.settingsRepository.current.lastLoadResultMessage)
        assertFalse(fixture.settingsRepository.current.wasLastDataAllInvalid)
        assertEquals(1, fixture.damDataRepository.clearDataCount)
        assertEquals("Dam changed.", fixture.debugLogRepository.addedEntries.single().first)
        assertEquals(1, fixture.widgetUpdateRequester.updateCount)
        assertEquals(listOf("INITIAL"), fixture.workGateway.replacedWorkTypes)
        assertTrue(completed)
    }

    @Test
    fun changeTargetDam_sameIdCompletesWithoutSideEffects() = runTest(mainDispatcherRule.testDispatcher) {
        val fixture = createViewModel(settings = stableSettings().copy(targetDamId = "1368080700010"))
        var completed = false

        fixture.viewModel.changeTargetDam("1368080700010") {
            completed = true
        }
        advanceUntilIdle()

        assertEquals(0, fixture.damDataRepository.clearDataCount)
        assertTrue(fixture.debugLogRepository.addedEntries.isEmpty())
        assertEquals(0, fixture.widgetUpdateRequester.updateCount)
        assertTrue(fixture.workGateway.replacedWorkTypes.isEmpty())
        assertTrue(fixture.workGateway.replacedSudmonitorHistoryTriggers.isEmpty())
        assertTrue(completed)
    }

    @Test
    fun changeTargetDam_gateEnabled_replacesDailyHistoryWork() = runTest(mainDispatcherRule.testDispatcher) {
        val fixture = createViewModel(
            settings = stableSettings().copy(
                targetDamId = "1368080700010",
                historicalDataSource = RealtimeDataSource.SUDMONITOR
            )
        )

        fixture.viewModel.changeTargetDam("1368010125140")
        advanceUntilIdle()

        // ダム変更時は日次過去データの再取得 work を REPLACE で enqueue する（D3）
        assertEquals(
            listOf(SudmonitorHistoryTrigger.TARGET_CHANGE),
            fixture.workGateway.replacedSudmonitorHistoryTriggers
        )
        // 既存の realtime INITIAL の REPLACE も維持される
        assertEquals(listOf("INITIAL"), fixture.workGateway.replacedWorkTypes)
    }

    @Test
    fun changeTargetDam_gateDisabled_doesNotReplaceDailyHistoryWork() = runTest(mainDispatcherRule.testDispatcher) {
        val fixture = createViewModel(
            settings = stableSettings().copy(
                targetDamId = "1368080700010",
                historicalDataSource = RealtimeDataSource.MLIT_DIRECT
            )
        )

        fixture.viewModel.changeTargetDam("1368010125140")
        advanceUntilIdle()

        assertTrue(fixture.workGateway.replacedSudmonitorHistoryTriggers.isEmpty())
        assertEquals(listOf("INITIAL"), fixture.workGateway.replacedWorkTypes)
    }

    @Test
    fun updateRealtimeDataSource_toSudmonitorWithNonSameuraDam_exposesPendingConfirmWithoutSaving() = runTest(mainDispatcherRule.testDispatcher) {
        val fixture = createViewModel(
            settings = stableSettings().copy(
                targetDamId = "1368010125140",
                realtimeDataSource = RealtimeDataSource.MLIT_DIRECT
            )
        )

        fixture.viewModel.updateRealtimeDataSource(RealtimeDataSource.SUDMONITOR)
        advanceUntilIdle()

        assertEquals(RealtimeDataSource.SUDMONITOR, fixture.viewModel.pendingRealtimeDataSource.value)
        assertEquals(RealtimeDataSource.MLIT_DIRECT, fixture.settingsRepository.current.realtimeDataSource)
        assertEquals("1368010125140", fixture.settingsRepository.current.targetDamId)
        assertEquals(0, fixture.damDataRepository.clearDataCount)
    }

    @Test
    fun confirmRealtimeDataSourceChange_changesDamToSameuraAndSavesSourceWithRestartFlow() = runTest(mainDispatcherRule.testDispatcher) {
        val fixture = createViewModel(
            settings = stableSettings().copy(
                targetDamId = "1368010125140",
                realtimeDataSource = RealtimeDataSource.MLIT_DIRECT
            )
        )
        fixture.viewModel.updateRealtimeDataSource(RealtimeDataSource.SUDMONITOR)
        advanceUntilIdle()

        fixture.viewModel.confirmRealtimeDataSourceChange()
        advanceUntilIdle()

        assertEquals(null, fixture.viewModel.pendingRealtimeDataSource.value)
        assertEquals("1368080700010", fixture.settingsRepository.current.targetDamId)
        assertEquals(RealtimeDataSource.SUDMONITOR, fixture.settingsRepository.current.realtimeDataSource)
        assertEquals(1, fixture.damDataRepository.clearDataCount)
        assertEquals(1, fixture.widgetUpdateRequester.updateCount)
        assertEquals(listOf("INITIAL"), fixture.workGateway.replacedWorkTypes)
    }

    @Test
    fun cancelRealtimeDataSourceChange_clearsPendingWithoutSideEffects() = runTest(mainDispatcherRule.testDispatcher) {
        val fixture = createViewModel(
            settings = stableSettings().copy(
                targetDamId = "1368010125140",
                realtimeDataSource = RealtimeDataSource.MLIT_DIRECT
            )
        )
        fixture.viewModel.updateRealtimeDataSource(RealtimeDataSource.SUDMONITOR)
        advanceUntilIdle()

        fixture.viewModel.cancelRealtimeDataSourceChange()
        advanceUntilIdle()

        assertEquals(null, fixture.viewModel.pendingRealtimeDataSource.value)
        assertEquals(RealtimeDataSource.MLIT_DIRECT, fixture.settingsRepository.current.realtimeDataSource)
        assertEquals("1368010125140", fixture.settingsRepository.current.targetDamId)
        assertEquals(0, fixture.damDataRepository.clearDataCount)
    }

    @Test
    fun updateRealtimeDataSource_toSudmonitorWithSameuraDam_savesSourceWithoutRestart() = runTest(mainDispatcherRule.testDispatcher) {
        val fixture = createViewModel(
            settings = stableSettings().copy(
                targetDamId = "1368080700010",
                realtimeDataSource = RealtimeDataSource.MLIT_DIRECT
            )
        )

        fixture.viewModel.updateRealtimeDataSource(RealtimeDataSource.SUDMONITOR)
        advanceUntilIdle()

        assertEquals(null, fixture.viewModel.pendingRealtimeDataSource.value)
        assertEquals(RealtimeDataSource.SUDMONITOR, fixture.settingsRepository.current.realtimeDataSource)
        assertEquals(0, fixture.damDataRepository.clearDataCount)
        assertTrue(fixture.workGateway.replacedWorkTypes.isEmpty())
    }

    @Test
    fun updateRealtimeDataSource_toMlitDirect_savesSourceWithoutRestart() = runTest(mainDispatcherRule.testDispatcher) {
        val fixture = createViewModel(
            settings = stableSettings().copy(
                targetDamId = "1368080700010",
                realtimeDataSource = RealtimeDataSource.SUDMONITOR
            )
        )

        fixture.viewModel.updateRealtimeDataSource(RealtimeDataSource.MLIT_DIRECT)
        advanceUntilIdle()

        assertEquals(RealtimeDataSource.MLIT_DIRECT, fixture.settingsRepository.current.realtimeDataSource)
        assertEquals("1368080700010", fixture.settingsRepository.current.targetDamId)
        assertEquals(0, fixture.damDataRepository.clearDataCount)
    }

    @Test
    fun updateHistoricalDataSource_savesSourceWithoutDamChange() = runTest(mainDispatcherRule.testDispatcher) {
        val fixture = createViewModel(settings = stableSettings())

        fixture.viewModel.updateHistoricalDataSource(RealtimeDataSource.MLIT_DIRECT)
        advanceUntilIdle()

        assertEquals(RealtimeDataSource.MLIT_DIRECT, fixture.settingsRepository.current.historicalDataSource)
        assertEquals("1368080700010", fixture.settingsRepository.current.targetDamId)
        assertEquals(0, fixture.damDataRepository.clearDataCount)
        assertTrue(fixture.workGateway.replacedWorkTypes.isEmpty())
    }

    @Test
    fun updateHistoricalDataSource_toSudmonitorRestoresDefault() = runTest(mainDispatcherRule.testDispatcher) {
        val fixture = createViewModel(
            settings = stableSettings().copy(historicalDataSource = RealtimeDataSource.MLIT_DIRECT)
        )

        fixture.viewModel.updateHistoricalDataSource(RealtimeDataSource.SUDMONITOR)
        advanceUntilIdle()

        assertEquals(RealtimeDataSource.SUDMONITOR, fixture.settingsRepository.current.historicalDataSource)
    }

    private fun afterDeleteSettings(): AppSettings = stableSettings().copy(
        state80_100 = "string-${R.string.main_emoji_80_100}",
        msg80_100 = "",
        msg80_100Ja = "",
        state60_80 = "string-${R.string.main_emoji_60_80}",
        msg60_80 = "",
        msg60_80Ja = "",
        state40_60 = "string-${R.string.main_emoji_40_60}",
        msg40_60 = "",
        msg40_60Ja = "",
        state20_40 = "string-${R.string.main_emoji_20_40}",
        msg20_40 = "",
        msg20_40Ja = "",
        state0_20 = "string-${R.string.main_emoji_0_20}",
        msg0_20 = "",
        msg0_20Ja = "",
        state0 = "string-${R.string.main_emoji_0}",
        msg0 = "",
        msg0Ja = "",
        stateAllAbnormal = "string-${R.string.main_emoji_all_abnormal}",
        msgAllAbnormal = "",
        msgAllAbnormalJa = "",
        stateAllDataInvalid = "string-${R.string.main_emoji_all_data_invalid}",
        msgAllDataInvalid = "",
        msgAllDataInvalidJa = ""
    )

    private fun configureForAfterDelete(): (Context) -> Unit = { context ->
        every { context.getString(R.string.main_emoji_80_100) } returns "string-${R.string.main_emoji_80_100}"
        every { context.getString(R.string.main_emoji_60_80) } returns "string-${R.string.main_emoji_60_80}"
        every { context.getString(R.string.main_emoji_40_60) } returns "string-${R.string.main_emoji_40_60}"
        every { context.getString(R.string.main_emoji_20_40) } returns "string-${R.string.main_emoji_20_40}"
        every { context.getString(R.string.main_emoji_0_20) } returns "string-${R.string.main_emoji_0_20}"
        every { context.getString(R.string.main_emoji_0) } returns "string-${R.string.main_emoji_0}"
        every { context.getString(R.string.main_emoji_all_abnormal) } returns "string-${R.string.main_emoji_all_abnormal}"
        every { context.getString(R.string.main_emoji_all_data_invalid) } returns "string-${R.string.main_emoji_all_data_invalid}"
    }

    private fun afterJaSettings(): AppSettings = stableSettings().copy(
        state80_100 = "string-${R.string.main_emoji_80_100}",
        msg80_100 = "string-${R.string.storage_default_msg_non_ja_80_100}",
        msg80_100Ja = "string-${R.string.storage_default_msg_ja_80_100}",
        state60_80 = "string-${R.string.main_emoji_60_80}",
        msg60_80 = "string-${R.string.storage_default_msg_non_ja_60_80}",
        msg60_80Ja = "string-${R.string.storage_default_msg_ja_60_80}",
        state40_60 = "string-${R.string.main_emoji_40_60}",
        msg40_60 = "string-${R.string.storage_default_msg_non_ja_40_60}",
        msg40_60Ja = "string-${R.string.storage_default_msg_ja_40_60}",
        state20_40 = "string-${R.string.main_emoji_20_40}",
        msg20_40 = "string-${R.string.storage_default_msg_non_ja_20_40}",
        msg20_40Ja = "string-${R.string.storage_default_msg_ja_20_40}",
        state0_20 = "string-${R.string.main_emoji_0_20}",
        msg0_20 = "string-${R.string.storage_default_msg_non_ja_0_20}",
        msg0_20Ja = "string-${R.string.storage_default_msg_ja_0_20}",
        state0 = "string-${R.string.main_emoji_0}",
        msg0 = "string-${R.string.storage_default_msg_non_ja_0}",
        msg0Ja = "string-${R.string.storage_default_msg_ja_0}",
        stateAllAbnormal = "string-${R.string.main_emoji_all_abnormal}",
        msgAllAbnormal = "string-${R.string.storage_default_msg_non_ja_all_abnormal}",
        msgAllAbnormalJa = "string-${R.string.storage_default_msg_ja_all_abnormal}",
        stateAllDataInvalid = "string-${R.string.main_emoji_all_data_invalid}",
        msgAllDataInvalid = "string-${R.string.storage_default_msg_non_ja_all_data_invalid}",
        msgAllDataInvalidJa = "string-${R.string.storage_default_msg_ja_all_data_invalid}"
    )

    private fun configureForAfterJa(): (Context) -> Unit = { context ->
        every { context.getString(R.string.main_emoji_80_100) } returns "string-${R.string.main_emoji_80_100}"
        every { context.getString(R.string.main_emoji_60_80) } returns "string-${R.string.main_emoji_60_80}"
        every { context.getString(R.string.main_emoji_40_60) } returns "string-${R.string.main_emoji_40_60}"
        every { context.getString(R.string.main_emoji_20_40) } returns "string-${R.string.main_emoji_20_40}"
        every { context.getString(R.string.main_emoji_0_20) } returns "string-${R.string.main_emoji_0_20}"
        every { context.getString(R.string.main_emoji_0) } returns "string-${R.string.main_emoji_0}"
        every { context.getString(R.string.main_emoji_all_abnormal) } returns "string-${R.string.main_emoji_all_abnormal}"
        every { context.getString(R.string.main_emoji_all_data_invalid) } returns "string-${R.string.main_emoji_all_data_invalid}"
        every { context.getString(R.string.storage_default_msg_ja_80_100) } returns "string-${R.string.storage_default_msg_ja_80_100}"
        every { context.getString(R.string.storage_default_msg_ja_60_80) } returns "string-${R.string.storage_default_msg_ja_60_80}"
        every { context.getString(R.string.storage_default_msg_ja_40_60) } returns "string-${R.string.storage_default_msg_ja_40_60}"
        every { context.getString(R.string.storage_default_msg_ja_20_40) } returns "string-${R.string.storage_default_msg_ja_20_40}"
        every { context.getString(R.string.storage_default_msg_ja_0_20) } returns "string-${R.string.storage_default_msg_ja_0_20}"
        every { context.getString(R.string.storage_default_msg_ja_0) } returns "string-${R.string.storage_default_msg_ja_0}"
        every { context.getString(R.string.storage_default_msg_ja_all_abnormal) } returns "string-${R.string.storage_default_msg_ja_all_abnormal}"
        every { context.getString(R.string.storage_default_msg_ja_all_data_invalid) } returns "string-${R.string.storage_default_msg_ja_all_data_invalid}"
        every { context.getString(R.string.storage_default_msg_non_ja_80_100) } returns "string-${R.string.storage_default_msg_non_ja_80_100}"
        every { context.getString(R.string.storage_default_msg_non_ja_60_80) } returns "string-${R.string.storage_default_msg_non_ja_60_80}"
        every { context.getString(R.string.storage_default_msg_non_ja_40_60) } returns "string-${R.string.storage_default_msg_non_ja_40_60}"
        every { context.getString(R.string.storage_default_msg_non_ja_20_40) } returns "string-${R.string.storage_default_msg_non_ja_20_40}"
        every { context.getString(R.string.storage_default_msg_non_ja_0_20) } returns "string-${R.string.storage_default_msg_non_ja_0_20}"
        every { context.getString(R.string.storage_default_msg_non_ja_0) } returns "string-${R.string.storage_default_msg_non_ja_0}"
        every { context.getString(R.string.storage_default_msg_non_ja_all_abnormal) } returns "string-${R.string.storage_default_msg_non_ja_all_abnormal}"
        every { context.getString(R.string.storage_default_msg_non_ja_all_data_invalid) } returns "string-${R.string.storage_default_msg_non_ja_all_data_invalid}"
    }

    /**
     * 早明浦ダム以外用の貯水率メッセージが一般向けプリセット（non_ja相当）と一致する設定を返します。
     * 既定のモックコンテキスト（`getString` が `"string-<リソースID>"` を返す）と突き合わせて構築します。
     */
    private fun otherPresetSettings(): AppSettings = stableSettings().copy(
        otherState80_100 = "string-${R.string.main_emoji_80_100}",
        otherMsg80_100 = "string-${R.string.storage_default_msg_non_ja_80_100}",
        otherMsg80_100Ja = "string-${R.string.storage_default_msg_non_ja_80_100}",
        otherState60_80 = "string-${R.string.main_emoji_60_80}",
        otherMsg60_80 = "string-${R.string.storage_default_msg_non_ja_60_80}",
        otherMsg60_80Ja = "string-${R.string.storage_default_msg_non_ja_60_80}",
        otherState40_60 = "string-${R.string.main_emoji_40_60}",
        otherMsg40_60 = "string-${R.string.storage_default_msg_non_ja_40_60}",
        otherMsg40_60Ja = "string-${R.string.storage_default_msg_non_ja_40_60}",
        otherState20_40 = "string-${R.string.main_emoji_20_40}",
        otherMsg20_40 = "string-${R.string.storage_default_msg_non_ja_20_40}",
        otherMsg20_40Ja = "string-${R.string.storage_default_msg_non_ja_20_40}",
        otherState0_20 = "string-${R.string.main_emoji_0_20}",
        otherMsg0_20 = "string-${R.string.storage_default_msg_non_ja_0_20}",
        otherMsg0_20Ja = "string-${R.string.storage_default_msg_non_ja_0_20}",
        otherState0 = "string-${R.string.main_emoji_0}",
        otherMsg0 = "string-${R.string.storage_default_msg_non_ja_0}",
        otherMsg0Ja = "string-${R.string.storage_default_msg_non_ja_0}",
        otherStateAllAbnormal = "string-${R.string.main_emoji_all_abnormal}",
        otherMsgAllAbnormal = "string-${R.string.storage_default_msg_non_ja_all_abnormal}",
        otherMsgAllAbnormalJa = "string-${R.string.storage_default_msg_ja_all_abnormal}",
        otherStateAllDataInvalid = "string-${R.string.main_emoji_all_data_invalid}",
        otherMsgAllDataInvalid = "string-${R.string.storage_default_msg_non_ja_all_data_invalid}",
        otherMsgAllDataInvalidJa = "string-${R.string.storage_default_msg_ja_all_data_invalid}"
    )

    private fun createViewModel(
        settings: AppSettings,
        configureContext: (Context) -> Unit = {}
    ): SettingsViewModelFixture {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
        val context = mockk<Context>().also { mockContext ->
            every { mockContext.getSystemService(android.app.LocaleManager::class.java) } returns null
            every { mockContext.getString(any()) } answers { "string-${firstArg<Int>()}" }
            every { mockContext.getString(any(), *anyVararg()) } answers {
                "string-${firstArg<Int>()}-${args.drop(1).joinToString("|")}"
            }
        }
        configureContext(context)
        val settingsRepository = FakeSettingsRepository(settings)
        val damDataRepository = FakeDamDataRepository()
        val sudmonitorHistoryRepository = mockk<SudmonitorHistoryRepository>(relaxed = true)
        val debugDataSessionRepository = mockk<net.tecogonaz.tcsameuradammonitor.domain.repository.DebugDataSessionRepository>(relaxed = true)
        io.mockk.coEvery { debugDataSessionRepository.enterDebugMode() } returns Result.success(Unit)
        io.mockk.coEvery { debugDataSessionRepository.exitDebugMode() } returns Result.success(Unit)
        val databaseMaintenanceRepository = FakeDatabaseMaintenanceRepository()
        val debugLogRepository = FakeDebugLogRepository()
        val workGateway = FakeDamWorkManagerGateway()
        val widgetUpdateRequester = FakeWidgetUpdateRequester(context)
        val debugDatSourceReader = FakeDebugDatSourceReader(context)
        val displayNameResolver = FakeDisplayNameResolver()
        val datExporter = FakeDatExporter()
        val viewModel = SettingsViewModel(
            settingsRepository = settingsRepository,
            damDataRepository = damDataRepository,
            sudmonitorHistoryRepository = sudmonitorHistoryRepository,
            debugDataSessionRepository = debugDataSessionRepository,
            databaseMaintenanceRepository = databaseMaintenanceRepository,
            debugLogRepository = debugLogRepository,
            damWorkManagerGateway = workGateway,
            widgetUpdateRequester = widgetUpdateRequester,
            debugDatSourceReader = debugDatSourceReader,
            displayNameResolver = displayNameResolver,
            datExporter = datExporter,
            context = context
        )
        return SettingsViewModelFixture(
            viewModel = viewModel,
            settingsRepository = settingsRepository,
            damDataRepository = damDataRepository,
            databaseMaintenanceRepository = databaseMaintenanceRepository,
            debugLogRepository = debugLogRepository,
            widgetUpdateRequester = widgetUpdateRequester,
            workGateway = workGateway,
            debugDatSourceReader = debugDatSourceReader,
            displayNameResolver = displayNameResolver,
            datExporter = datExporter,
            debugDataSessionRepository = debugDataSessionRepository
        )
    }

    private data class SettingsViewModelFixture(
        val viewModel: SettingsViewModel,
        val settingsRepository: FakeSettingsRepository,
        val damDataRepository: FakeDamDataRepository,
        val databaseMaintenanceRepository: FakeDatabaseMaintenanceRepository,
        val debugLogRepository: FakeDebugLogRepository,
        val widgetUpdateRequester: FakeWidgetUpdateRequester,
        val workGateway: FakeDamWorkManagerGateway,
        val debugDatSourceReader: FakeDebugDatSourceReader,
        val displayNameResolver: FakeDisplayNameResolver,
        val datExporter: FakeDatExporter,
        val debugDataSessionRepository: DebugDataSessionRepository
    )

    private class FakeWidgetUpdateRequester(context: Context) : WidgetUpdateRequester(context) {
        var updateCount = 0
            private set

        override suspend fun updateAllWidgetsImmediately() {
            updateCount += 1
        }
    }

    private class FakeDebugDatSourceReader(context: Context) : DebugDatSourceReader(context) {
        val bundledBytes = mutableMapOf<String, ByteArray>()
        val safBytes = mutableMapOf<String, ByteArray>()

        override fun readBundled(fileName: String): ByteArray =
            bundledBytes[fileName] ?: error("Unexpected bundled debug dat read: $fileName")

        override fun readSaf(uriString: String, maxSizeBytes: Int, tooLargeMessage: String, readErrorMessage: String): ByteArray =
            safBytes[uriString] ?: throw Exception(readErrorMessage)
    }

    private class FakeDisplayNameResolver : DisplayNameResolver(mockk()) {
        val resolvedNames = mutableMapOf<String, String>()

        override fun resolve(uriString: String?, defaultName: String): String =
            uriString?.let { resolvedNames[it] } ?: defaultName
    }

    private class FakeDatExporter : DatExporter(mockk()) {
        val writes = mutableListOf<Pair<Uri, ByteArray>>()
        var writeResult = true

        override fun write(outputUri: Uri, bytes: ByteArray): Boolean {
            writes += outputUri to bytes
            return writeResult
        }
    }

    private fun stableSettings(): AppSettings =
        AppSettings(
            autoUpdateCustomTimingMillisWeekly = jstMillis("2099/01/05 05:15"),
            autoUpdateCustomTimingMillisDaily = jstMillis("2099/01/01 05:15"),
            autoUpdateCustomTimingMillis12Hours = jstMillis("2099/01/01 05:15"),
            autoUpdateCustomTimingMillisHourly = jstMillis("2099/01/01 05:15")
        )

    private fun jstMillis(value: String): Long =
        TimeUtils.parseJstMillis(value, "yyyy/MM/dd HH:mm") ?: error("Invalid date: $value")
}
