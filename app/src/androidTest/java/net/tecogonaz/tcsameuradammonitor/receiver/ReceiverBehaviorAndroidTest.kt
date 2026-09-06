// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.receiver

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import kotlinx.coroutines.test.runTest
import net.tecogonaz.tcsameuradammonitor.domain.model.AppSettings
import net.tecogonaz.tcsameuradammonitor.domain.model.DamData
import net.tecogonaz.tcsameuradammonitor.domain.model.DamLoadStatus
import net.tecogonaz.tcsameuradammonitor.domain.model.DebugDatSelectionMode
import net.tecogonaz.tcsameuradammonitor.domain.model.DebugSimulateMode
import net.tecogonaz.tcsameuradammonitor.testutil.HiltFakeDamDataRepository
import net.tecogonaz.tcsameuradammonitor.testutil.HiltFakeDamWorkManagerGateway
import net.tecogonaz.tcsameuradammonitor.testutil.HiltFakeDebugLogRepository
import net.tecogonaz.tcsameuradammonitor.testutil.HiltFakeSettingsRepository
import net.tecogonaz.tcsameuradammonitor.testutil.androidTestDamData
import net.tecogonaz.tcsameuradammonitor.util.AppNotificationManager
import net.tecogonaz.tcsameuradammonitor.worker.DamWorker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith

/**
 * システム各種変更イベント受信器（`BootCompletedReceiver`, `LocaleChangeReceiver`, `TimeZoneChangeReceiver`）の
 * ビジネスロジックと連動する通知・タスク更新処理を検証する Instrumentation テストクラス。
 * 端末起動完了時の通知初期化やバックグラウンドタスクのエンキューおよびログ出力処理、
 * OSシステム言語の切り替え時のキャッシュ無効化と多言語通知メッセージの再構成、タイムゾーン（TimeZone）変更に伴う
 * 時刻表示通知の即時リフレッシュ処理が正しく実行されるかを検証します。
 */
@RunWith(AndroidJUnit4::class)
@MediumTest
class ReceiverBehaviorAndroidTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun bootCompleted_withCachedDataAndBootUpdateEnabled_enqueuesBootWorkAndLogsCompletion() = runTest {
        val settingsRepository = HiltFakeSettingsRepository(
            AppSettings(
                showNotification = false,
                updateOnBoot = true,
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
        )
        val damDataRepository = HiltFakeDamDataRepository(androidTestDamData())
        val debugLogRepository = HiltFakeDebugLogRepository()
        val workGateway = HiltFakeDamWorkManagerGateway()
        val notificationManager = RecordingNotificationManager(context)
        val receiver = BootCompletedReceiver().apply {
            this.settingsRepository = settingsRepository
            this.damDataRepository = damDataRepository
            this.debugLogRepository = debugLogRepository
            this.damWorkManagerGateway = workGateway
            this.debugDataSessionRepository = object : net.tecogonaz.tcsameuradammonitor.domain.repository.DebugDataSessionRepository {
                override suspend fun enterDebugMode() = Result.success(Unit)
                override suspend fun exitDebugMode() = Result.success(Unit)
                override suspend fun recoverSessionOnStartup() = Result.success(
                    net.tecogonaz.tcsameuradammonitor.domain.repository.DebugDataSessionRecovery.NOTHING_TO_DO
                )
            }
            this.notificationManager = notificationManager
        }

        receiver.handleBootCompleted()

        assertEquals(listOf(DamWorker.WORK_TYPE_BOOT), workGateway.enqueuedWorkTypes)
        assertEquals(1, notificationManager.cancelBootNotificationCount)
        assertEquals(AppSettings().debugSettingsVisible, settingsRepository.current.debugSettingsVisible)
        assertEquals(AppSettings().debugModeEnabled, settingsRepository.current.debugModeEnabled)
        assertEquals(AppSettings().debugRealtimeDatFileMode, settingsRepository.current.debugRealtimeDatFileMode)
        assertEquals(AppSettings().debugRealtimeDatFileUri, settingsRepository.current.debugRealtimeDatFileUri)
        assertEquals(AppSettings().debugHistoricalDailyDatFileMode, settingsRepository.current.debugHistoricalDailyDatFileMode)
        assertEquals(AppSettings().debugHistoricalDailyDatFileUri, settingsRepository.current.debugHistoricalDailyDatFileUri)
        assertEquals(AppSettings().debugRealtimeDataStartMillis, settingsRepository.current.debugRealtimeDataStartMillis)
        assertEquals(AppSettings().debugRealtimeDataEndMillis, settingsRepository.current.debugRealtimeDataEndMillis)
        assertEquals(AppSettings().debugSimulateMode, settingsRepository.current.debugSimulateMode)
        assertEquals(
            AppSettings().debugRealtimeDataPeriodAutoAdvanceEnabled,
            settingsRepository.current.debugRealtimeDataPeriodAutoAdvanceEnabled
        )
        assertEquals(
            listOf("Boot completed. Notification and work scheduling finished." to ""),
            debugLogRepository.addedEntries
        )
    }

    @Test
    fun localeChanged_invalidatesSettingsAndRefreshesNotifications() = runTest {
        val settings = AppSettings(showNotification = true)
        val settingsRepository = HiltFakeSettingsRepository(settings)
        val damData = androidTestDamData()
        val damDataRepository = HiltFakeDamDataRepository(damData)
        val notificationManager = RecordingNotificationManager(context)
        val receiver = LocaleChangeReceiver().apply {
            this.settingsRepository = settingsRepository
            this.damDataRepository = damDataRepository
            this.notificationManager = notificationManager
        }

        receiver.handleLocaleChanged(context, updateWidgets = false)

        assertEquals(1, settingsRepository.invalidateSettingsCacheCount)
        assertEquals(1, notificationManager.updateNotificationCount)
        assertEquals(1, notificationManager.updateBootNotificationIfActiveCount)
        assertSame(damData, notificationManager.lastDamData)
        assertEquals(settings, notificationManager.lastSettings)
        assertEquals(DamLoadStatus.SUCCESS, notificationManager.lastLoadStatus)
    }

    @Test
    fun timeZoneChanged_refreshesNotificationWithCurrentRepositoryState() = runTest {
        val settings = AppSettings(showNotification = true)
        val damData = androidTestDamData()
        val damDataRepository = HiltFakeDamDataRepository(damData)
        val notificationManager = RecordingNotificationManager(context)
        val receiver = TimeZoneChangeReceiver().apply {
            this.settingsRepository = HiltFakeSettingsRepository(settings)
            this.damDataRepository = damDataRepository
            this.notificationManager = notificationManager
        }

        receiver.handleTimeZoneChanged(context, updateWidgets = false)

        assertEquals(1, notificationManager.updateNotificationCount)
        assertSame(damData, notificationManager.lastDamData)
        assertEquals(settings, notificationManager.lastSettings)
        assertEquals(DamLoadStatus.SUCCESS, notificationManager.lastLoadStatus)
    }

    private class RecordingNotificationManager(context: Context) : AppNotificationManager(context) {
        var showBootNotificationCount = 0
            private set
        var cancelBootNotificationCount = 0
            private set
        var updateNotificationCount = 0
            private set
        var updateBootNotificationIfActiveCount = 0
            private set
        var lastDamData: DamData? = null
            private set
        var lastSettings: AppSettings? = null
            private set
        var lastLoadStatus: DamLoadStatus? = null
            private set

        override fun showBootNotification(damData: DamData?, settings: AppSettings?) {
            showBootNotificationCount += 1
            lastDamData = damData
            lastSettings = settings
        }

        override fun cancelBootNotification() {
            cancelBootNotificationCount += 1
        }

        override fun updateNotification(
            damData: DamData?,
            settings: AppSettings,
            loadStatus: DamLoadStatus
        ) {
            updateNotificationCount += 1
            lastDamData = damData
            lastSettings = settings
            lastLoadStatus = loadStatus
        }

        override fun updateBootNotificationIfActive(damData: DamData?, settings: AppSettings?) {
            updateBootNotificationIfActiveCount += 1
        }
    }
}
