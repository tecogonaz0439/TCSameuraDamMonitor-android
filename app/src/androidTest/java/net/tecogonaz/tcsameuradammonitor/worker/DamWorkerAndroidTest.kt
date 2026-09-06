// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.worker

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import net.tecogonaz.tcsameuradammonitor.data.repository.DamDataRepositoryImpl
import net.tecogonaz.tcsameuradammonitor.data.source.local.DebugDatSourceReader
import net.tecogonaz.tcsameuradammonitor.data.source.local.room.DamDatabase
import net.tecogonaz.tcsameuradammonitor.data.source.remote.DamFileParser
import net.tecogonaz.tcsameuradammonitor.data.source.remote.DamNetworkDataSource
import net.tecogonaz.tcsameuradammonitor.data.source.remote.MlitEndpointConfig
import net.tecogonaz.tcsameuradammonitor.data.source.remote.SudmonitorEndpointConfig
import net.tecogonaz.tcsameuradammonitor.data.source.remote.SudmonitorNetworkDataSource
import net.tecogonaz.tcsameuradammonitor.domain.model.AppSettings
import net.tecogonaz.tcsameuradammonitor.domain.model.AutoUpdateInterval
import net.tecogonaz.tcsameuradammonitor.domain.model.DamConfig
import net.tecogonaz.tcsameuradammonitor.domain.model.DamConfigProvider
import net.tecogonaz.tcsameuradammonitor.domain.model.DamLoadStatus
import net.tecogonaz.tcsameuradammonitor.domain.model.DebugSimulateMode
import net.tecogonaz.tcsameuradammonitor.domain.repository.DamDataRepository
import net.tecogonaz.tcsameuradammonitor.domain.repository.DebugLogRepository
import net.tecogonaz.tcsameuradammonitor.domain.repository.SettingsRepository

import net.tecogonaz.tcsameuradammonitor.testutil.HiltFakeDamDataRepository
import net.tecogonaz.tcsameuradammonitor.testutil.HiltFakeDebugLogRepository
import net.tecogonaz.tcsameuradammonitor.testutil.HiltFakeSettingsRepository
import net.tecogonaz.tcsameuradammonitor.testutil.HiltFakeSudmonitorHistoryRepository
import net.tecogonaz.tcsameuradammonitor.testutil.androidTestDamData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@MediumTest
/**
 * バックグラウンドで定期的にダムデータを更新する [DamWorker] の動作を検証する Instrumentation テストクラス。
 * `TestListenableWorkerBuilder` を用いて、自動更新（AUTO）タスクの取得成功に伴う次回実行スケジュール設定や
 * デバッグログ書き込み処理、一時的な取得失敗時のリトライ判定（[DamWorker.MAX_AUTO_RETRIES] 未満）、
 * 許容リトライ上限到達時のタスク失敗判定、手動（MANUAL）および端末起動時（BOOT）のワンタイム更新の成否判定、
 * およびバックグラウンド実行ポリシー（[DamWorkerPolicy]）に連動するログ出力を検証します。
 */
class DamWorkerAndroidTest {
    private lateinit var context: Context
    private lateinit var settingsRepository: HiltFakeSettingsRepository
    private lateinit var damDataRepository: HiltFakeDamDataRepository
    private lateinit var debugLogRepository: HiltFakeDebugLogRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        settingsRepository = HiltFakeSettingsRepository(workerSettings())
        damDataRepository = HiltFakeDamDataRepository(androidTestDamData())
        debugLogRepository = HiltFakeDebugLogRepository()
    }

    @Test
    fun autoWork_success_updatesNextRunSettingsAndWritesDebugLog() = runTest {
        val worker = buildWorker(DamWorker.WORK_TYPE_AUTO)

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        assertTrue(settingsRepository.current.lastAutoUpdateMillis > 0L)
        assertTrue(settingsRepository.current.nextScheduledUpdateMillis > settingsRepository.current.lastAutoUpdateMillis)
        assertEquals(false, settingsRepository.current.isFirstRunAfterReschedule)
        assertEquals("Auto update succeeded.", debugLogRepository.addedEntries.single().first)
        assertTrue(debugLogRepository.addedEntries.single().second.contains("Dam name:"))
    }

    @Test
    fun autoWork_fetchFailureBeforeMaxRetries_returnsRetryAndLogsAttempt() = runTest {
        damDataRepository.reset(data = null, loadStatus = DamLoadStatus.INITIAL)
        val worker = buildWorker(DamWorker.WORK_TYPE_AUTO, runAttemptCount = 0)

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.retry(), result)
        assertEquals("Auto update failed.", debugLogRepository.addedEntries.single().first)
        assertTrue(debugLogRepository.addedEntries.single().second.contains("Attempt: 1/4"))
    }

    @Test
    fun autoWork_fetchFailureAfterMaxRetries_returnsFailure() = runTest {
        damDataRepository.reset(data = null, loadStatus = DamLoadStatus.INITIAL)
        val worker = buildWorker(DamWorker.WORK_TYPE_AUTO, runAttemptCount = DamWorker.MAX_AUTO_RETRIES)

        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Failure)
        assertEquals("Auto update failed.", debugLogRepository.addedEntries.single().first)
        assertTrue(debugLogRepository.addedEntries.single().second.contains("Attempt: 4/4"))
    }

    @Test
    fun autoWork_simulatedNetworkUnavailableBeforeMaxRetries_returnsRetry() = runTest {
        settingsRepository.reset(
            workerSettings().copy(debugSimulateMode = DebugSimulateMode.NETWORK_UNAVAILABLE)
        )
        val worker = buildWorker(DamWorker.WORK_TYPE_AUTO, runAttemptCount = 0)

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.retry(), result)
        assertEquals("Auto update failed.", debugLogRepository.addedEntries.single().first)
        assertTrue(debugLogRepository.addedEntries.single().second.contains("Simulated network unavailable"))
    }

    @Test
    fun manualWork_fetchFailure_returnsFailureOutputAndLoadingFailureStatus() = runTest {
        damDataRepository.reset(data = null, loadStatus = DamLoadStatus.INITIAL)
        val worker = buildWorker(DamWorker.WORK_TYPE_MANUAL)

        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Failure)
        assertTrue(result.outputData.getString(DamWorker.KEY_ERROR_MESSAGE).orEmpty().contains("failed"))
        assertEquals(DamLoadStatus.LOADING_FAILURE, damDataRepository.loadStatus.value)
        assertEquals("Manual update failed.", debugLogRepository.addedEntries.single().first)
    }

    @Test
    fun manualWork_simulatedNetworkUnavailable_returnsFailureOutputAndNetworkStatus() = runTest {
        settingsRepository.reset(
            workerSettings().copy(debugSimulateMode = DebugSimulateMode.NETWORK_UNAVAILABLE)
        )
        val worker = buildWorker(DamWorker.WORK_TYPE_MANUAL)

        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Failure)
        assertTrue(result.outputData.getString(DamWorker.KEY_ERROR_MESSAGE).orEmpty().contains("offline"))
        assertEquals(DamLoadStatus.NETWORK_UNAVAILABLE, damDataRepository.loadStatus.value)
        assertEquals("Manual update failed.", debugLogRepository.addedEntries.single().first)
    }

    @Test
    fun manualWork_debugMode_doesNotRecordLastFetchTime() = runTest {
        settingsRepository.reset(
            workerSettings().copy(debugModeEnabled = true)
        )
        val worker = buildWorker(DamWorker.WORK_TYPE_MANUAL)

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(listOf(false), damDataRepository.recordLastFetchTimeMillisArgs)
    }

    @Test
    fun manualWork_normalMode_recordsLastFetchTime() = runTest {
        settingsRepository.reset(
            workerSettings().copy(debugModeEnabled = false)
        )
        val worker = buildWorker(DamWorker.WORK_TYPE_MANUAL)

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(listOf(true), damDataRepository.recordLastFetchTimeMillisArgs)
    }

    @Test
    fun manualWork_successWithRealRepository_storesDamDataInRoomCache() = runTest {
        settingsRepository.reset(workerSettings())
        val database = Room.inMemoryDatabaseBuilder(context, DamDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val endpointConfig = MlitEndpointConfig.localhostHttp()
        val realRepository = DamDataRepositoryImpl(
            networkDataSource = DamNetworkDataSource(endpointConfig),
            sudmonitorNetworkDataSource = SudmonitorNetworkDataSource(
                SudmonitorEndpointConfig.production()
            ),
            parser = DamFileParser(endpointConfig),
            settingsRepository = settingsRepository,
            damDao = database.damDao(),
            applicationScope = applicationScope,
            context = context,
            damConfigProvider = object : DamConfigProvider {
                override fun get(targetDamId: String): DamConfig = DamConfig.DEFAULT
            },
            debugDatSourceReader = DebugDatSourceReader(context)
        )
        try {
            val worker = buildWorker(
                workType = DamWorker.WORK_TYPE_MANUAL,
                damDataRepositoryOverride = realRepository
            )

            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            val cached = database.damDao().getDamData()
            assertEquals("1368080700010", cached?.observationStationId)
            assertTrue(realRepository.getLastRawDatBytes()?.isNotEmpty() == true)
            assertEquals(0L, realRepository.getLastFetchTimeMillis())
            assertEquals(DamLoadStatus.SUCCESS, realRepository.loadStatus.value)
            assertEquals("Manual update succeeded.", debugLogRepository.addedEntries.single().first)
        } finally {
            applicationScope.cancel()
            database.close()
        }
    }

    @Test
    fun initialWork_success_logsInitialLoadWithoutChangingAutoSchedule() = runTest {
        val worker = buildWorker(DamWorker.WORK_TYPE_INITIAL)

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(0L, settingsRepository.current.lastAutoUpdateMillis)
        assertEquals(0L, settingsRepository.current.nextScheduledUpdateMillis)
        assertEquals(true, settingsRepository.current.isFirstRunAfterReschedule)
        assertEquals("Initial load succeeded.", debugLogRepository.addedEntries.single().first)
    }

    @Test
    fun bootWork_success_logsBootUpdateWithoutChangingAutoSchedule() = runTest {
        val worker = buildWorker(DamWorker.WORK_TYPE_BOOT)

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(0L, settingsRepository.current.lastAutoUpdateMillis)
        assertEquals(0L, settingsRepository.current.nextScheduledUpdateMillis)
        assertEquals(true, settingsRepository.current.isFirstRunAfterReschedule)
        assertEquals("Boot update succeeded.", debugLogRepository.addedEntries.single().first)
    }

    private fun buildWorker(
        workType: String,
        runAttemptCount: Int = 0,
        damDataRepositoryOverride: DamDataRepository = damDataRepository
    ): DamWorker =
        TestListenableWorkerBuilder.from(context, DamWorker::class.java)
            .setInputData(workDataOf(DamWorker.KEY_WORK_TYPE to workType))
            .setRunAttemptCount(runAttemptCount)
            .setWorkerFactory(
                TestDamWorkerFactory(
                    settingsRepository = settingsRepository,
                    damDataRepository = damDataRepositoryOverride,
                    debugLogRepository = debugLogRepository
                )
            )
            .build()

    private fun workerSettings(): AppSettings =
        AppSettings(
            autoUpdateInterval = AutoUpdateInterval.ONE_HOUR,
            debugModeEnabled = true,
            isFirstRunAfterReschedule = true,
            stateLoadingError = "!",
            msgLoadingError = "failed",
            msgLoadingErrorJa = "failed",
            stateNetworkUnavailable = "?",
            msgNetworkUnavailable = "offline",
            msgNetworkUnavailableJa = "offline"
        )

    private class TestDamWorkerFactory(
        private val settingsRepository: SettingsRepository,
        private val damDataRepository: DamDataRepository,
        private val debugLogRepository: DebugLogRepository
    ) : WorkerFactory() {
        override fun createWorker(
            appContext: Context,
            workerClassName: String,
            workerParameters: WorkerParameters
        ): ListenableWorker? =
            if (workerClassName == DamWorker::class.java.name) {
                DamWorker(
                    appContext = appContext,
                    workerParams = workerParameters,
                    debugLogRepository = debugLogRepository,
                    settingsRepository = settingsRepository,
                    damDataRepository = damDataRepository,
                    sudmonitorHistoryRepository = HiltFakeSudmonitorHistoryRepository()
                )
            } else {
                null
            }
    }
}
