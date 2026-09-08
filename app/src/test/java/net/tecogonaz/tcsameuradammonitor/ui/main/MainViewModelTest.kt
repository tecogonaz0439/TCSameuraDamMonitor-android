// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.ui.main

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.LocaleList
import android.os.SystemClock
import androidx.work.WorkInfo
import androidx.work.workDataOf
import app.cash.turbine.test
import arrow.core.Either
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.tecogonaz.tcsameuradammonitor.domain.model.AppSettings
import net.tecogonaz.tcsameuradammonitor.domain.model.AutoUpdateInterval
import net.tecogonaz.tcsameuradammonitor.domain.model.DamConfig
import net.tecogonaz.tcsameuradammonitor.domain.model.DamHistoricalData
import net.tecogonaz.tcsameuradammonitor.domain.model.DamListData
import net.tecogonaz.tcsameuradammonitor.domain.model.DamLoadStatus
import net.tecogonaz.tcsameuradammonitor.domain.model.DebugSimulateMode
import net.tecogonaz.tcsameuradammonitor.domain.model.HistoricalSearchMeta
import net.tecogonaz.tcsameuradammonitor.domain.model.MainCardExpansionKey
import net.tecogonaz.tcsameuradammonitor.domain.model.MainCardExpansionState
import net.tecogonaz.tcsameuradammonitor.domain.model.HistoricalComparisonMetric
import net.tecogonaz.tcsameuradammonitor.domain.model.RealtimeDataSource
import net.tecogonaz.tcsameuradammonitor.domain.model.SudmonitorHistory
import net.tecogonaz.tcsameuradammonitor.domain.repository.SudmonitorHistoryTrigger
import net.tecogonaz.tcsameuradammonitor.domain.usecase.QueryAvailableAppsUseCase
import net.tecogonaz.tcsameuradammonitor.testutil.FakeDamDataRepository
import net.tecogonaz.tcsameuradammonitor.testutil.FakeDamWorkManagerGateway
import net.tecogonaz.tcsameuradammonitor.testutil.FakeDebugLogRepository
import net.tecogonaz.tcsameuradammonitor.testutil.FakeHistoricalComparisonRepository
import net.tecogonaz.tcsameuradammonitor.testutil.FakeHistoricalSearchRepository
import net.tecogonaz.tcsameuradammonitor.testutil.FakeNetworkAvailability
import net.tecogonaz.tcsameuradammonitor.testutil.FakeSettingsRepository
import net.tecogonaz.tcsameuradammonitor.testutil.FakeSudmonitorHistoryRepository
import net.tecogonaz.tcsameuradammonitor.testutil.MainDispatcherRule
import net.tecogonaz.tcsameuradammonitor.testutil.SharedFakeHistoricalComparisonRepository
import net.tecogonaz.tcsameuradammonitor.testutil.damData
import net.tecogonaz.tcsameuradammonitor.util.AppNotificationManager
import net.tecogonaz.tcsameuradammonitor.worker.DamWorker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import net.tecogonaz.tcsameuradammonitor.util.TimeUtils
import java.util.Locale
import java.util.UUID

/**
 * メイン画面の ViewModel ([MainViewModel]) のユニットテストクラス。
 * 初回起動時の挙動、ダムデータの取得成功/失敗に伴う UI 状態の遷移、
 * 手動更新処理時の [DamWorkManagerGateway] との連携、ネットワーク状態に応じた処理、
 * バックグラウンドでの自動更新イベントのハンドリングなどを網羅的に検証します。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var context: Context
    private lateinit var appNotificationManager: AppNotificationManager
    private lateinit var queryAvailableAppsUseCase: QueryAvailableAppsUseCase

    @Before
    fun setUp() {
        mockkStatic(SystemClock::class)
        every { SystemClock.elapsedRealtime() } returns 1_000L

        context = mockk()
        every { context.getString(any()) } answers { "string-${firstArg<Int>()}" }
        every { context.getString(any(), *anyVararg()) } answers { "string-${firstArg<Int>()}" }
        val resources = mockk<Resources>()
        val configuration = mockk<Configuration>()
        val locales = mockk<LocaleList>()
        every { context.resources } returns resources
        every { resources.configuration } returns configuration
        every { configuration.locales } returns locales
        every { locales[0] } returns Locale.JAPAN

        appNotificationManager = mockk(relaxed = true)
        every { appNotificationManager.formatNotificationMessage(any(), any()) } returns "notification"
        every { appNotificationManager.formatSnackbarMessage(any(), any()) } returns "dam\ndata\nstate"

        queryAvailableAppsUseCase = mockk()
        coEvery { queryAvailableAppsUseCase.invoke(any()) } returns emptyList()
    }

    @After
    fun tearDown() {
        unmockkStatic(SystemClock::class)
    }

    @Test
    fun init_withoutCachedData_enqueuesInitialLoadAndShowsInitialDialog() = runTest(mainDispatcherRule.testDispatcher) {
        val fixture = createViewModel(settings = stableSettings(initialDialogShown = false), initialData = null)

        advanceUntilIdle()

        assertTrue(fixture.viewModel.uiState.value.showInitialAutoUpdateDialog)
        assertEquals(listOf(DamWorker.WORK_TYPE_INITIAL), fixture.workGateway.enqueuedWorkTypes)
    }

    @Test
    fun latestDamDataReceived_showsDamDataAndClearsError() = runTest(mainDispatcherRule.testDispatcher) {
        val fixture = createViewModel(settings = stableSettings(), initialData = null)
        val data = damData(storagePercentage = 81.25f)
        advanceUntilIdle()

        fixture.damRepository.emitData(data)
        advanceUntilIdle()

        assertSame(data, fixture.viewModel.uiState.value.damData)
        assertEquals(null, fixture.viewModel.uiState.value.errorMessage)
    }

    @Test
    fun latestStoragePercentageTimeChanged_showsMultilineSnackbarMessage() =
        runTest(mainDispatcherRule.testDispatcher) {
            val fixture = createViewModel(
                settings = stableSettings(),
                initialData = damData(updatedAt = "2026/05/18 05:15")
            )
            advanceUntilIdle()

            fixture.viewModel.snackbarMessage.test {
                fixture.damRepository.emitData(damData(updatedAt = "2026/05/18 05:25"))
                advanceUntilIdle()

                assertEquals("dam\ndata\nstate", awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun dataDistributionResumed_showsSameMultilineStorageRateSnackbarMessage() =
        runTest(mainDispatcherRule.testDispatcher) {
            val fixture = createViewModel(
                settings = stableSettings().copy(wasLastDataAllInvalid = true),
                initialData = damData(updatedAt = "2026/05/18 05:15")
            )

            fixture.viewModel.snackbarMessage.test {
                advanceUntilIdle()

                assertEquals("dam\ndata\nstate", awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun loadFailureReported_showsFailureStatus() = runTest(mainDispatcherRule.testDispatcher) {
        val fixture = createViewModel(settings = stableSettings(), initialData = null)
        advanceUntilIdle()

        fixture.damRepository.setLoadStatus(DamLoadStatus.LOADING_FAILURE)
        advanceUntilIdle()

        assertEquals(DamLoadStatus.LOADING_FAILURE, fixture.viewModel.uiState.value.damLoadStatus)
    }

    @Test
    fun fetchData_manualUpdate_enqueuesManualWork() = runTest(mainDispatcherRule.testDispatcher) {
        val fixture = createViewModel(settings = stableSettings(), initialData = null)
        advanceUntilIdle()
        fixture.workGateway.enqueuedWorkTypes.clear()

        fixture.viewModel.fetchData(isManual = true)
        advanceUntilIdle()

        assertEquals(listOf(DamWorker.WORK_TYPE_MANUAL), fixture.workGateway.enqueuedWorkTypes)
    }

    @Test
    fun fetchData_manualUpdate_networkUnavailable_showsSnackbarWithoutEnqueue() = runTest(mainDispatcherRule.testDispatcher) {
        val fixture = createViewModel(
            settings = stableSettings(),
            initialData = null,
            networkAvailable = false
        )
        advanceUntilIdle()
        fixture.workGateway.enqueuedWorkTypes.clear()

        fixture.viewModel.fetchData(isManual = true)
        advanceUntilIdle()

        assertEquals(emptyList<String>(), fixture.workGateway.enqueuedWorkTypes)
        fixture.viewModel.snackbarMessage.test {
            assertEquals("早明浦ダム", awaitItem())
        }
    }

    @Test
    fun fetchData_manualUpdate_networkUnavailable_simulatedMode_stillEnqueues() = runTest(mainDispatcherRule.testDispatcher) {
        val fixture = createViewModel(
            settings = stableSettings().copy(
                debugModeEnabled = true,
                debugSimulateMode = DebugSimulateMode.NETWORK_UNAVAILABLE
            ),
            initialData = null,
            networkAvailable = false
        )
        advanceUntilIdle()
        fixture.workGateway.enqueuedWorkTypes.clear()

        fixture.viewModel.fetchData(isManual = true)
        advanceUntilIdle()

        assertEquals(listOf(DamWorker.WORK_TYPE_MANUAL), fixture.workGateway.enqueuedWorkTypes)
    }

    @Test
    fun fetchData_manualUpdate_networkUnavailable_debugModeWithoutSimulation_stillEnqueues() =
        runTest(mainDispatcherRule.testDispatcher) {
            val fixture = createViewModel(
                settings = stableSettings().copy(
                    debugModeEnabled = true,
                    debugSimulateMode = DebugSimulateMode.NONE
                ),
                initialData = null,
                networkAvailable = false
            )
            advanceUntilIdle()
            fixture.workGateway.enqueuedWorkTypes.clear()

            fixture.viewModel.fetchData(isManual = true)
            advanceUntilIdle()

            assertEquals(listOf(DamWorker.WORK_TYPE_MANUAL), fixture.workGateway.enqueuedWorkTypes)
        }

    @Test
    fun init_newBoot_resetsDebugSettings() = runTest(mainDispatcherRule.testDispatcher) {
        val fixture = createViewModel(
            settings = stableSettings().copy(
                lastBootTime = 1L,
                debugSettingsVisible = true,
                debugModeEnabled = true,
                debugSimulateMode = DebugSimulateMode.LOADING_FAILURE
            ),
            initialData = null,
        )
        advanceUntilIdle()

        assertFalse(fixture.settingsRepository.current.debugSettingsVisible)
        assertFalse(fixture.settingsRepository.current.debugModeEnabled)
        assertEquals(DebugSimulateMode.NONE, fixture.settingsRepository.current.debugSimulateMode)
    }

    @Test
    fun autoWorkRunning_showsAutoUpdateRunning() = runTest(mainDispatcherRule.testDispatcher) {
        val fixture = createViewModel(settings = stableSettings(), initialData = null)
        advanceUntilIdle()

        fixture.workGateway.emitAutoWorkInfos(
            listOf(workInfo(WorkInfo.State.RUNNING, DamWorker.WORK_TYPE_AUTO))
        )
        advanceUntilIdle()

        assertTrue(fixture.viewModel.uiState.value.isAutoUpdateRunning)
    }

    @Test
    fun manualWorkRunning_showsManualUpdateRunningOnly() = runTest(mainDispatcherRule.testDispatcher) {
        val fixture = createViewModel(settings = stableSettings(), initialData = null)
        advanceUntilIdle()

        fixture.workGateway.emitOneTimeWorkInfos(
            listOf(workInfo(WorkInfo.State.RUNNING, DamWorker.WORK_TYPE_MANUAL))
        )
        advanceUntilIdle()

        assertTrue(fixture.viewModel.uiState.value.isManualUpdateRunning)
        assertFalse(fixture.viewModel.uiState.value.isInitialLoadRunning)
    }

    @Test
    fun initialWorkRunning_showsInitialLoadRunningOnly() = runTest(mainDispatcherRule.testDispatcher) {
        val fixture = createViewModel(settings = stableSettings(), initialData = null)
        advanceUntilIdle()

        fixture.workGateway.emitOneTimeWorkInfos(
            listOf(workInfo(WorkInfo.State.RUNNING, DamWorker.WORK_TYPE_INITIAL))
        )
        advanceUntilIdle()

        assertFalse(fixture.viewModel.uiState.value.isManualUpdateRunning)
        assertTrue(fixture.viewModel.uiState.value.isInitialLoadRunning)
    }

    @Test
    fun bootWorkRunning_showsBootUpdateRunning() = runTest(mainDispatcherRule.testDispatcher) {
        val fixture = createViewModel(settings = stableSettings(), initialData = null)
        advanceUntilIdle()

        fixture.workGateway.emitBootWorkInfos(
            listOf(workInfo(WorkInfo.State.RUNNING, DamWorker.WORK_TYPE_BOOT))
        )
        advanceUntilIdle()

        assertTrue(fixture.viewModel.uiState.value.isBootUpdateRunning)
    }

    @Test
    fun sudmonitorHistoryWorkRunning_showsHistoryUpdateRunningOnly() =
        runTest(mainDispatcherRule.testDispatcher) {
            val fixture = createViewModel(settings = stableSettings(), initialData = null)
            advanceUntilIdle()

            // 日次取得ワーク（ワークタイプタグのみ）の実行中は日次用フラグのみ真となり、
            // 定期自動更新フラグ（自動更新アイコンの回転条件）へは影響しない
            fixture.workGateway.emitSudmonitorHistoryWorkInfos(
                listOf(workInfo(WorkInfo.State.RUNNING, DamWorker.WORK_TYPE_SUDMONITOR_HISTORY))
            )
            advanceUntilIdle()

            assertTrue(fixture.viewModel.uiState.value.isSudmonitorHistoryUpdateRunning)
            assertFalse(fixture.viewModel.uiState.value.isSudmonitorHistoryInitialRunning)
            assertFalse(fixture.viewModel.uiState.value.isSudmonitorHistoryManualRunning)
            assertFalse(fixture.viewModel.uiState.value.isAutoUpdateRunning)
        }

    @Test
    fun sudmonitorHistoryInitialWorkRunning_showsInitialFlagNotManual() =
        runTest(mainDispatcherRule.testDispatcher) {
            val fixture = createViewModel(settings = stableSettings(), initialData = null)
            advanceUntilIdle()

            // 初回起動の日次取得（INITIAL）はリアルタイム側の「初回読込」と同等に
            // 自動更新アイコンの回転条件へ反映する（手動更新フラグは真にしない）
            fixture.workGateway.emitSudmonitorHistoryWorkInfos(
                listOf(
                    workInfo(
                        WorkInfo.State.RUNNING,
                        DamWorker.sudmonitorHistoryTriggerTag(SudmonitorHistoryTrigger.INITIAL)
                    )
                )
            )
            advanceUntilIdle()

            val state = fixture.viewModel.uiState.value
            assertTrue(state.isSudmonitorHistoryUpdateRunning)
            assertTrue(state.isSudmonitorHistoryInitialRunning)
            assertFalse(state.isSudmonitorHistoryManualRunning)
        }

    @Test
    fun sudmonitorHistoryTargetChangeWorkRunning_showsInitialFlagNotManual() =
        runTest(mainDispatcherRule.testDispatcher) {
            val fixture = createViewModel(settings = stableSettings(), initialData = null)
            advanceUntilIdle()

            // ダム変更の日次取得（TARGET_CHANGE）も初回読込系として扱う
            fixture.workGateway.emitSudmonitorHistoryWorkInfos(
                listOf(
                    workInfo(
                        WorkInfo.State.RUNNING,
                        DamWorker.sudmonitorHistoryTriggerTag(SudmonitorHistoryTrigger.TARGET_CHANGE)
                    )
                )
            )
            advanceUntilIdle()

            val state = fixture.viewModel.uiState.value
            assertTrue(state.isSudmonitorHistoryUpdateRunning)
            assertTrue(state.isSudmonitorHistoryInitialRunning)
            assertFalse(state.isSudmonitorHistoryManualRunning)
        }

    @Test
    fun sudmonitorHistoryManualWorkRunning_showsManualFlagNotInitial() =
        runTest(mainDispatcherRule.testDispatcher) {
            val fixture = createViewModel(settings = stableSettings(), initialData = null)
            advanceUntilIdle()

            // 手動更新（MANUAL）は手動更新アイコンの回転条件のみへ反映する
            // （自動更新アイコンの回転条件=初回読込系は真にしない）
            fixture.workGateway.emitSudmonitorHistoryWorkInfos(
                listOf(
                    workInfo(
                        WorkInfo.State.RUNNING,
                        DamWorker.sudmonitorHistoryTriggerTag(SudmonitorHistoryTrigger.MANUAL)
                    )
                )
            )
            advanceUntilIdle()

            val state = fixture.viewModel.uiState.value
            assertTrue(state.isSudmonitorHistoryUpdateRunning)
            assertFalse(state.isSudmonitorHistoryInitialRunning)
            assertTrue(state.isSudmonitorHistoryManualRunning)
        }

    @Test
    fun workInfosCleared_clearsRunningIndicators() = runTest(mainDispatcherRule.testDispatcher) {
        val fixture = createViewModel(settings = stableSettings(), initialData = null)
        advanceUntilIdle()

        fixture.workGateway.emitAutoWorkInfos(
            listOf(workInfo(WorkInfo.State.RUNNING, DamWorker.WORK_TYPE_AUTO))
        )
        fixture.workGateway.emitOneTimeWorkInfos(
            listOf(workInfo(WorkInfo.State.RUNNING, DamWorker.WORK_TYPE_MANUAL))
        )
        fixture.workGateway.emitBootWorkInfos(
            listOf(workInfo(WorkInfo.State.RUNNING, DamWorker.WORK_TYPE_BOOT))
        )
        advanceUntilIdle()

        fixture.workGateway.emitAutoWorkInfos(emptyList())
        fixture.workGateway.emitOneTimeWorkInfos(emptyList())
        fixture.workGateway.emitBootWorkInfos(emptyList())
        advanceUntilIdle()
        assertFalse(fixture.viewModel.uiState.value.isAutoUpdateRunning)
        assertFalse(fixture.viewModel.uiState.value.isManualUpdateRunning)
        assertFalse(fixture.viewModel.uiState.value.isInitialLoadRunning)
        assertFalse(fixture.viewModel.uiState.value.isBootUpdateRunning)
    }

    @Test
    fun manualUpdateFailureAfterRunning_showsErrorAndStoresLastResult() =
        runTest(mainDispatcherRule.testDispatcher) {
            val fixture = createViewModel(settings = stableSettings(), initialData = null)
            advanceUntilIdle()

            fixture.viewModel.snackbarMessage.test {
                fixture.workGateway.emitOneTimeWorkInfos(
                    listOf(workInfo(WorkInfo.State.RUNNING, DamWorker.WORK_TYPE_MANUAL))
                )
                advanceUntilIdle()
                fixture.workGateway.emitOneTimeWorkInfos(
                    listOf(
                        workInfo(
                            state = WorkInfo.State.FAILED,
                            tag = DamWorker.WORK_TYPE_MANUAL,
                            errorMessage = "manual failed"
                        )
                    )
                )
                advanceUntilIdle()

                assertEquals("manual failed", awaitItem())
                assertEquals("manual failed", fixture.settingsRepository.current.lastLoadResultMessage)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun canRefresh_respectsLastFetchCooldown() = runTest(mainDispatcherRule.testDispatcher) {
        val fixture = createViewModel(settings = stableSettings(), initialData = null)
        fixture.damRepository.testLastFetchTimeMillis = 1_000_000L
        advanceUntilIdle()

        assertFalse(fixture.viewModel.canRefresh(now = 1_000_000L + 9 * 60 * 1000))
        assertTrue(fixture.viewModel.canRefresh(now = 1_000_000L + 10 * 60 * 1000))
    }

    @Test
    fun canRefresh_usesSavedManualRefreshAvailableAtMillisWhenPresent() = runTest(mainDispatcherRule.testDispatcher) {
        // sudmonitor 取得成功時に保存された X-TCS-Next-Update-At 由来の時刻で判定する（§7）
        val savedAvailableAt = 1_000_000L + 15 * 60 * 1000
        val fixture = createViewModel(
            settings = stableSettings().copy(manualRefreshAvailableAtMillis = savedAvailableAt),
            initialData = null
        )
        fixture.damRepository.testLastFetchTimeMillis = 1_000_000L
        // appSettings は WhileSubscribed のため、判定に必要な現在設定を購読しておく
        val settingsJob = launch { fixture.viewModel.appSettings.collect {} }
        advanceUntilIdle()

        // 保存済み時刻が未来の間は更新不可（従来の10分経過後でも不可）
        assertFalse(fixture.viewModel.canRefresh(now = 1_000_000L + 12 * 60 * 1000))
        // 保存済み時刻が過ぎていれば更新可能（10分経過前でも可）
        assertTrue(fixture.viewModel.canRefresh(now = savedAvailableAt))
        settingsJob.cancel()
    }

    @Test
    fun canRefresh_mlitDirectWithoutSavedHeader_keepsTenMinuteCooldown() = runTest(mainDispatcherRule.testDispatcher) {
        // MLIT 直接取得時は X-TCS-Next-Update-At が存在しないため、従来どおり
        // lastFetchTimeMillis + 10分で判定する（保存済み時刻は無い）
        val fixture = createViewModel(
            settings = stableSettings().copy(realtimeDataSource = RealtimeDataSource.MLIT_DIRECT),
            initialData = null
        )
        fixture.damRepository.testLastFetchTimeMillis = 1_000_000L
        advanceUntilIdle()

        assertFalse(fixture.viewModel.canRefresh(now = 1_000_000L + 9 * 60 * 1000))
        assertTrue(fixture.viewModel.canRefresh(now = 1_000_000L + 10 * 60 * 1000))
    }

    @Test
    fun init_gateEnabledRowNotSaved_enqueuesDailyHistoryInitialWork() = runTest(mainDispatcherRule.testDispatcher) {
        val fixture = createViewModel(settings = stableSettings(), initialData = null)
        advanceUntilIdle()

        // ゲート有効かつ行未保存のときのみ、日次過去データの初回取得 work を enqueue する（§3.1）
        assertEquals(
            listOf(SudmonitorHistoryTrigger.INITIAL),
            fixture.workGateway.enqueuedSudmonitorHistoryTriggers
        )
    }

    @Test
    fun init_gateEnabledRowSaved_doesNotEnqueueDailyHistoryInitialWork() =
        runTest(mainDispatcherRule.testDispatcher) {
            val fixture = createViewModel(
                settings = stableSettings(),
                initialData = null,
                sudmonitorHistoryRow = sudmonitorHistory()
            )
            advanceUntilIdle()

            assertTrue(fixture.workGateway.enqueuedSudmonitorHistoryTriggers.isEmpty())
        }

    @Test
    fun init_gateDisabled_doesNotEnqueueDailyHistoryInitialWork() = runTest(mainDispatcherRule.testDispatcher) {
        val fixture = createViewModel(
            settings = stableSettings().copy(historicalDataSource = RealtimeDataSource.MLIT_DIRECT),
            initialData = null
        )
        advanceUntilIdle()

        assertTrue(fixture.workGateway.enqueuedSudmonitorHistoryTriggers.isEmpty())
    }

    @Test
    fun targetDamChange_clearsDailyHistoryStateAndLoadsNewDamRow() = runTest(mainDispatcherRule.testDispatcher) {
        val targetDamId = "1368010125140"
        val newDamHistory = sudmonitorHistory(damId = targetDamId)
        val fixture = createViewModel(
            settings = stableSettings(),
            initialData = null,
            sudmonitorHistoryRow = sudmonitorHistory()
        )
        advanceUntilIdle()
        fixture.viewModel.switchToSudmonitorHistoryMode()
        advanceUntilIdle()
        assertTrue(fixture.viewModel.uiState.value.isSudmonitorHistoryMode)

        // ダム変更時は日次表示状態（モード・表示データ）をクリアし、新ダムの行を読み込む（D3）
        // （新ダムの保存行をRoom Flowがemitし、旧ダム行との差異で表示状態がクリアされる）
        fixture.sudmonitorHistoryRepository.history = newDamHistory
        fixture.settingsRepository.updateSettings { it.copy(targetDamId = targetDamId) }
        advanceUntilIdle()

        val state = fixture.viewModel.uiState.value
        assertFalse(state.isSudmonitorHistoryMode)
        assertNull(state.sudmonitorHistoryDamConfig)
        assertTrue(state.sudmonitorHistoryAllData.isEmpty())
        assertTrue(state.sudmonitorHistoryVisibleData.isEmpty())
        assertEquals(newDamHistory, state.sudmonitorHistory)
    }

    @Test
    fun historicalDataSourceGateOff_clearsDailyHistoryEntryFromState() =
        runTest(mainDispatcherRule.testDispatcher) {
            val fixture = createViewModel(
                settings = stableSettings(),
                initialData = null,
                sudmonitorHistoryRow = sudmonitorHistory()
            )
            advanceUntilIdle()
            assertNotNull(fixture.viewModel.uiState.value.sudmonitorHistory)

            // 機能ゲート（過去データの取得元）を MLIT 直接へ切替えると、常設エントリを出さない
            fixture.settingsRepository.updateSettings {
                it.copy(historicalDataSource = RealtimeDataSource.MLIT_DIRECT)
            }
            advanceUntilIdle()

            assertNull(fixture.viewModel.uiState.value.sudmonitorHistory)
        }

    @Test
    fun debugModeExit_reloadsRestoredDailyHistoryForDrawerImmediately() =
        runTest(mainDispatcherRule.testDispatcher) {
            val debugHistory = sudmonitorHistory().copy(fetchedAtEpochMs = 900L)
            val restoredHistory = sudmonitorHistory().copy(fetchedAtEpochMs = 100L)
            val fixture = createViewModel(
                settings = stableSettings().copy(debugModeEnabled = true),
                initialData = null,
                sudmonitorHistoryRow = debugHistory
            )
            advanceUntilIdle()
            assertEquals(debugHistory, fixture.viewModel.uiState.value.sudmonitorHistory)

            // DebugDataSessionRepositoryがRoomを復元した後に設定をOFFへ更新する順序を再現する。
            fixture.sudmonitorHistoryRepository.history = restoredHistory
            fixture.settingsRepository.updateSettings { it.copy(debugModeEnabled = false) }
            advanceUntilIdle()

            assertEquals(restoredHistory, fixture.viewModel.uiState.value.sudmonitorHistory)
        }

    @Test
    fun debugModeExit_withEmptyBackup_keepsDailyModeWithNameOnly() =
        runTest(mainDispatcherRule.testDispatcher) {
            val fixture = createViewModel(
                settings = stableSettings().copy(debugModeEnabled = true),
                initialData = null,
                sudmonitorHistoryRow = sudmonitorHistory(),
                sudmonitorHistoryObservations = historicalDataSeries(
                    "2026/08/01 01:00",
                    "2026/08/01 02:00",
                    "2026/08/01 03:00"
                )
            )
            advanceUntilIdle()
            fixture.viewModel.switchToSudmonitorHistoryMode()
            advanceUntilIdle()
            assertTrue(fixture.viewModel.uiState.value.isSudmonitorHistoryMode)

            // Debug退出で空バックアップ（行も観測行も無し）が復元されても、機能ゲート（過去データの取得元が
            // sudmonitor）は有効なままなので日次モードは離脱せず、ダム名のみの常設エントリを維持する
            fixture.sudmonitorHistoryRepository.history = null
            fixture.sudmonitorHistoryRepository.observations = emptyList()
            fixture.settingsRepository.updateSettings { it.copy(debugModeEnabled = false) }
            advanceUntilIdle()

            val state = fixture.viewModel.uiState.value
            assertTrue(state.isSudmonitorHistoryMode)
            assertNull(state.sudmonitorHistory)
            assertNotNull(state.sudmonitorHistoryDamConfig)
            assertTrue(state.sudmonitorHistoryAllData.isEmpty())
        }

    @Test
    fun switchToSudmonitorHistoryMode_withoutRow_entersModeWithDamConfig() =
        runTest(mainDispatcherRule.testDispatcher) {
            val fixture = createViewModel(settings = stableSettings(), initialData = null)
            advanceUntilIdle()

            // 保存行が未保存（初回取得失敗など）でも日次モードへは遷移し、ダム名のみの常設エントリを出す
            fixture.viewModel.switchToSudmonitorHistoryMode()
            advanceUntilIdle()

            val state = fixture.viewModel.uiState.value
            assertTrue(state.isSudmonitorHistoryMode)
            assertFalse(state.isHistoricalMode)
            assertNull(state.sudmonitorHistory)
            assertNotNull(state.sudmonitorHistoryDamConfig)
            assertTrue(state.sudmonitorHistoryAllData.isEmpty())
            assertTrue(state.sudmonitorHistoryVisibleData.isEmpty())
            assertTrue(state.sudmonitorHistoryDisplayData.isEmpty())
        }

    @Test
    fun fetchSudmonitorHistoryData_manual_offline_showsSnackbarAndDoesNotEnqueue() =
        runTest(mainDispatcherRule.testDispatcher) {
            val fixture = createViewModel(
                settings = stableSettings(),
                initialData = null,
                networkAvailable = false
            )
            advanceUntilIdle()
            fixture.workGateway.enqueuedSudmonitorHistoryTriggers.clear()

            // 手動更新はオフライン時、enqueueせずにリアルタイム手動更新と同じ「ネットワーク接続なし」Snackbarを表示する
            fixture.viewModel.fetchSudmonitorHistoryData(isManual = true)
            advanceUntilIdle()

            assertTrue(fixture.workGateway.enqueuedSudmonitorHistoryTriggers.isEmpty())
            fixture.viewModel.snackbarMessage.test {
                assertEquals("早明浦ダム", awaitItem())
            }
        }

    @Test
    fun fetchSudmonitorHistoryData_manual_online_enqueuesManualTrigger() =
        runTest(mainDispatcherRule.testDispatcher) {
            val fixture = createViewModel(settings = stableSettings(), initialData = null)
            advanceUntilIdle()
            fixture.workGateway.enqueuedSudmonitorHistoryTriggers.clear()

            fixture.viewModel.fetchSudmonitorHistoryData(isManual = true)
            advanceUntilIdle()

            assertEquals(
                listOf(SudmonitorHistoryTrigger.MANUAL),
                fixture.workGateway.enqueuedSudmonitorHistoryTriggers
            )
        }

    @Test
    fun dailyHistory_flowEmission_updatesSidebarState() = runTest(mainDispatcherRule.testDispatcher) {
        val fixture = createViewModel(settings = stableSettings(), initialData = null)
        advanceUntilIdle()
        assertNull(fixture.viewModel.uiState.value.sudmonitorHistory)

        // 保存行の書込（historyFlow のemit）だけでサイドバー常設エントリが更新される（WorkInfo完了検知不要）
        fixture.sudmonitorHistoryRepository.history = sudmonitorHistory()
        advanceUntilIdle()

        assertEquals(sudmonitorHistory(), fixture.viewModel.uiState.value.sudmonitorHistory)
    }

    @Test
    fun dailyHistory_flowEmission_reloadsDisplayWhenInDailyMode() = runTest(mainDispatcherRule.testDispatcher) {
        val fixture = createViewModel(
            settings = stableSettings(),
            initialData = null,
            sudmonitorHistoryRow = sudmonitorHistory(),
            sudmonitorHistoryObservations = historicalDataSeries(
                "2026/08/01 20:00",
                "2026/08/01 22:00"
            )
        )
        advanceUntilIdle()
        fixture.viewModel.switchToSudmonitorHistoryMode()
        advanceUntilIdle()
        assertTrue(fixture.viewModel.uiState.value.isSudmonitorHistoryMode)

        // 変更された保存行+新しい観測行をemitすると表示データが再読込される
        val updatedHistory = sudmonitorHistory().copy(updatedAtEpochMs = jstMillis("2026/08/02 05:15"))
        val updatedObservations = historicalDataSeries(
            "2026/08/01 20:00",
            "2026/08/01 22:00",
            "2026/08/02 01:00"
        )
        fixture.sudmonitorHistoryRepository.observations = updatedObservations
        fixture.sudmonitorHistoryRepository.history = updatedHistory
        advanceUntilIdle()

        val state = fixture.viewModel.uiState.value
        assertEquals(updatedHistory, state.sudmonitorHistory)
        assertEquals(updatedObservations, state.sudmonitorHistoryAllData)
        assertEquals(updatedObservations, state.sudmonitorHistoryVisibleData)
        assertEquals(
            listOf("2026/08/02 01:00", "2026/08/01 22:00", "2026/08/01 20:00"),
            state.sudmonitorHistoryDisplayData.map { it.time }
        )
    }

    @Test
    fun dailyHistory_flowEmission_resetsOutOfRangeFilter() = runTest(mainDispatcherRule.testDispatcher) {
        val fixture = createViewModel(
            settings = stableSettings(),
            initialData = null,
            sudmonitorHistoryRow = sudmonitorHistory(),
            sudmonitorHistoryObservations = historicalDataSeries(
                "2026/08/01 12:00",
                "2026/08/15 12:00",
                "2026/08/31 12:00"
            )
        )
        advanceUntilIdle()
        fixture.viewModel.switchToSudmonitorHistoryMode()
        advanceUntilIdle()

        fixture.viewModel.applySudmonitorHistoryDisplayRange(
            fromMillis = jstMillis("2026/08/10 00:00"),
            toMillis = jstMillis("2026/08/21 00:00")
        )
        advanceUntilIdle()
        assertTrue(fixture.viewModel.uiState.value.isSudmonitorHistoryRangeFiltered)

        // 絞り込み範囲を外れる期間の新しい保存行をemitすると絞り込みが解除され全期間表示へ戻る
        fixture.sudmonitorHistoryRepository.history = sudmonitorHistory().copy(
            periodStartEpochMs = jstMillis("2026/09/01 00:00"),
            periodEndEpochMs = jstMillis("2026/09/30 00:00"),
            updatedAtEpochMs = jstMillis("2026/09/01 05:15")
        )
        advanceUntilIdle()

        val state = fixture.viewModel.uiState.value
        assertFalse(state.isSudmonitorHistoryRangeFiltered)
        assertNull(state.sudmonitorHistoryDisplayStartDate)
        assertNull(state.sudmonitorHistoryDisplayEndDate)
        assertEquals(3, state.sudmonitorHistoryVisibleData.size)
    }

    @Test
    fun dailyHistory_flowEmission_keepsInRangeFilter() = runTest(mainDispatcherRule.testDispatcher) {
        val fixture = createViewModel(
            settings = stableSettings(),
            initialData = null,
            sudmonitorHistoryRow = sudmonitorHistory(),
            sudmonitorHistoryObservations = historicalDataSeries(
                "2026/08/01 12:00",
                "2026/08/15 12:00",
                "2026/08/31 12:00"
            )
        )
        advanceUntilIdle()
        fixture.viewModel.switchToSudmonitorHistoryMode()
        advanceUntilIdle()

        fixture.viewModel.applySudmonitorHistoryDisplayRange(
            fromMillis = jstMillis("2026/08/10 00:00"),
            toMillis = jstMillis("2026/08/21 00:00")
        )
        advanceUntilIdle()
        assertTrue(fixture.viewModel.uiState.value.isSudmonitorHistoryRangeFiltered)

        // 絞り込み範囲が新保存行の期間内に収まる場合は絞り込みが維持される
        fixture.sudmonitorHistoryRepository.history =
            sudmonitorHistory().copy(updatedAtEpochMs = jstMillis("2026/08/02 05:15"))
        advanceUntilIdle()

        val state = fixture.viewModel.uiState.value
        assertTrue(state.isSudmonitorHistoryRangeFiltered)
        assertEquals("2026/08/10", state.sudmonitorHistoryDisplayStartDate)
        assertEquals("2026/08/20", state.sudmonitorHistoryDisplayEndDate)
        assertEquals(listOf("2026/08/15 12:00"), state.sudmonitorHistoryVisibleData.map { it.time })
    }

    @Test
    fun dailyHistory_flowEmission_null_keepsModeWithNameOnlyEntry() = runTest(mainDispatcherRule.testDispatcher) {
        val fixture = createViewModel(
            settings = stableSettings(),
            initialData = null,
            sudmonitorHistoryRow = sudmonitorHistory(),
            sudmonitorHistoryObservations = historicalDataSeries(
                "2026/08/01 01:00",
                "2026/08/01 02:00"
            )
        )
        advanceUntilIdle()
        fixture.viewModel.switchToSudmonitorHistoryMode()
        advanceUntilIdle()
        assertTrue(fixture.viewModel.uiState.value.isSudmonitorHistoryMode)

        // 保存行がnullになった（行削除・初回取得失敗）場合でも、機能ゲート有効な間は日次モードを維持し、
        // 履歴由来の表示状態のみをクリアする（サイドバーにダム名のみの常設エントリを出し続ける）
        fixture.sudmonitorHistoryRepository.history = null
        advanceUntilIdle()

        val state = fixture.viewModel.uiState.value
        assertTrue(state.isSudmonitorHistoryMode)
        assertNull(state.sudmonitorHistory)
        assertNotNull(state.sudmonitorHistoryDamConfig)
        assertTrue(state.sudmonitorHistoryAllData.isEmpty())
        assertTrue(state.sudmonitorHistoryVisibleData.isEmpty())
        assertTrue(state.sudmonitorHistoryDisplayData.isEmpty())
        assertFalse(state.isSudmonitorHistoryRangeFiltered)
        assertNull(state.sudmonitorHistoryDisplayStartDate)
        assertNull(state.sudmonitorHistoryDisplayEndDate)
    }

    @Test
    fun dailyHistory_gateOff_clearsSidebarEntry() = runTest(mainDispatcherRule.testDispatcher) {
        val fixture = createViewModel(
            settings = stableSettings(),
            initialData = null,
            sudmonitorHistoryRow = sudmonitorHistory(),
            sudmonitorHistoryObservations = historicalDataSeries("2026/08/01 01:00")
        )
        advanceUntilIdle()
        fixture.viewModel.switchToSudmonitorHistoryMode()
        advanceUntilIdle()
        assertTrue(fixture.viewModel.uiState.value.isSudmonitorHistoryMode)

        // 機能ゲートを MLIT 直接へ切替えるとモードを離脱し、常設エントリも出さない
        fixture.settingsRepository.updateSettings {
            it.copy(historicalDataSource = RealtimeDataSource.MLIT_DIRECT)
        }
        advanceUntilIdle()

        val state = fixture.viewModel.uiState.value
        assertFalse(state.isSudmonitorHistoryMode)
        assertNull(state.sudmonitorHistory)
    }

    @Test
    fun dailyHistory_flowEmission_refreshesReadyComparison() = runTest(mainDispatcherRule.testDispatcher) {
        val fixture = createViewModel(
            settings = stableSettings(),
            initialData = damData(),
            sudmonitorHistoryRow = sudmonitorHistory(),
            sudmonitorHistoryObservations = historicalDataSeries("2026/08/01 01:00")
        )
        advanceUntilIdle()

        fixture.viewModel.ensureHistoricalComparison(
            HistoricalComparisonMetric.STORAGE_RATE,
            windowStartMillis = 1_000L,
            windowEndMillis = 2_000L
        )
        advanceUntilIdle()
        assertEquals(
            HistoricalComparisonLoadState.READY,
            fixture.viewModel.uiState.value.historicalComparisonStates[
                HistoricalComparisonMetric.STORAGE_RATE
            ]?.loadState
        )
        assertEquals(1, fixture.comparisonRepository.loadCount)

        // 保存行の更新emitで、同じwindow・mainYearのまま比較グラフが再読込される
        fixture.sudmonitorHistoryRepository.history =
            sudmonitorHistory().copy(updatedAtEpochMs = jstMillis("2026/08/02 05:15"))
        advanceUntilIdle()

        assertEquals(2, fixture.comparisonRepository.loadCount)
        assertEquals(1_000L, fixture.comparisonRepository.lastWindowStartMillis)
        assertEquals(2_000L, fixture.comparisonRepository.lastWindowEndMillis)
        assertEquals(null, fixture.comparisonRepository.lastMainYear)
        assertEquals(
            HistoricalComparisonLoadState.READY,
            fixture.viewModel.uiState.value.historicalComparisonStates[
                HistoricalComparisonMetric.STORAGE_RATE
            ]?.loadState
        )
    }

    @Test
    fun dailyHistory_debugRestore_revertsDisplay() = runTest(mainDispatcherRule.testDispatcher) {
        val historyA = sudmonitorHistory()
        val observationsA = historicalDataSeries(
            "2026/08/01 01:00",
            "2026/08/01 02:00"
        )
        val fixture = createViewModel(
            settings = stableSettings(),
            initialData = null,
            sudmonitorHistoryRow = historyA,
            sudmonitorHistoryObservations = observationsA
        )
        advanceUntilIdle()
        fixture.viewModel.switchToSudmonitorHistoryMode()
        advanceUntilIdle()
        assertTrue(fixture.viewModel.uiState.value.isSudmonitorHistoryMode)

        // B（Debugデータ）へ切替
        val historyB = sudmonitorHistory().copy(updatedAtEpochMs = jstMillis("2026/08/02 05:15"))
        val observationsB = historicalDataSeries(
            "2026/08/02 01:00",
            "2026/08/02 02:00"
        )
        fixture.sudmonitorHistoryRepository.observations = observationsB
        fixture.sudmonitorHistoryRepository.history = historyB
        advanceUntilIdle()
        assertEquals(observationsB, fixture.viewModel.uiState.value.sudmonitorHistoryAllData)

        // A（復元）へ戻すと表示もAの内容へ戻る
        fixture.sudmonitorHistoryRepository.observations = observationsA
        fixture.sudmonitorHistoryRepository.history = historyA
        advanceUntilIdle()

        val state = fixture.viewModel.uiState.value
        assertEquals(historyA, state.sudmonitorHistory)
        assertEquals(observationsA, state.sudmonitorHistoryAllData)
        assertEquals(observationsA, state.sudmonitorHistoryVisibleData)
        assertTrue(state.isSudmonitorHistoryMode)
    }

    @Test
    fun enableAutoUpdateFromInitialDialog_updatesSettingsSchedulesWorkAndLogs() = runTest(mainDispatcherRule.testDispatcher) {
        val next = jstMillis("2099/01/01 05:15")
        val settings = stableSettings()
            .copy(autoUpdateInterval = AutoUpdateInterval.ONE_HOUR, autoUpdateCustomTimingMillisHourly = next)
        val fixture = createViewModel(settings = settings, initialData = null)
        advanceUntilIdle()

        fixture.viewModel.enableAutoUpdateFromInitialDialog()
        advanceUntilIdle()

        assertTrue(fixture.settingsRepository.current.autoUpdateEnabled)
        assertTrue(fixture.settingsRepository.current.isFirstRunAfterReschedule)
        assertEquals(next, fixture.settingsRepository.current.nextScheduledUpdateMillis)
        assertEquals(next, fixture.workGateway.scheduled.single().second)
        assertEquals("Auto update enabled (initial dialog).", fixture.debugLogRepository.addedEntries.single().first)
    }

    @Test
    fun markInitialAutoUpdateDialogShown_hidesDialogAndSetsNotificationFlag() = runTest(mainDispatcherRule.testDispatcher) {
        val fixture = createViewModel(settings = stableSettings(initialDialogShown = false), initialData = null)
        advanceUntilIdle()

        fixture.viewModel.markInitialAutoUpdateDialogShown()
        advanceUntilIdle()

        assertTrue(fixture.settingsRepository.current.initialAutoUpdateDialogShown)
        assertFalse(fixture.viewModel.uiState.value.showInitialAutoUpdateDialog)
        assertTrue(fixture.viewModel.uiState.value.showInitialNotificationPermissionRequest)
    }

    @Test
    fun markInitialNotificationPermissionRequested_clearsNotificationFlag() = runTest(mainDispatcherRule.testDispatcher) {
        val fixture = createViewModel(settings = stableSettings(initialDialogShown = false), initialData = null)
        advanceUntilIdle()

        fixture.viewModel.markInitialAutoUpdateDialogShown()
        advanceUntilIdle()
        assertTrue(fixture.viewModel.uiState.value.showInitialNotificationPermissionRequest)

        fixture.viewModel.markInitialNotificationPermissionRequested()
        assertFalse(fixture.viewModel.uiState.value.showInitialNotificationPermissionRequest)
    }

    @Test
    fun updateShowNotification_true_setsShowNotificationInRepository() = runTest(mainDispatcherRule.testDispatcher) {
        val fixture = createViewModel(settings = stableSettings().copy(showNotification = false), initialData = null)
        advanceUntilIdle()

        fixture.viewModel.updateShowNotification(true)
        advanceUntilIdle()

        assertTrue(fixture.settingsRepository.current.showNotification)
    }

    @Test
    fun updateShowNotification_false_unsetsShowNotificationInRepository() = runTest(mainDispatcherRule.testDispatcher) {
        val fixture = createViewModel(settings = stableSettings().copy(showNotification = true), initialData = null)
        advanceUntilIdle()

        fixture.viewModel.updateShowNotification(false)
        advanceUntilIdle()

        assertFalse(fixture.settingsRepository.current.showNotification)
    }

    @Test
    fun fetchHistoricalData_success_entersHistoricalModeAndRefreshesMetaLists() = runTest(mainDispatcherRule.testDispatcher) {
        val fixture = createViewModel(settings = stableSettings(), initialData = null)
        val meta = historicalSearchMeta(id = 10L, searchBgnDate = "20260501", searchEndDate = "20260501")
        val data = historicalDataSeries(
            "2026/05/01 00:00",
            "2026/05/01 01:00",
            "2026/05/01 07:00"
        )
        fixture.historicalSearchRepository.nextFetchResult = Either.Right(meta)
        fixture.historicalSearchRepository.setDataForMeta(meta.id, data)
        advanceUntilIdle()

        fixture.viewModel.fetchHistoricalData(DamConfig.DEFAULT, "20260501", "20260501")
        advanceUntilIdle()

        val state = fixture.viewModel.uiState.value
        assertTrue(state.isHistoricalMode)
        assertFalse(state.isHistoricalSearchLoading)
        assertEquals(meta, state.historicalDamMeta)
        assertEquals(data, state.historicalAllData)
        assertEquals(data, state.historicalVisibleData)
        assertEquals(listOf("2026/05/01 07:00", "2026/05/01 01:00"), state.historicalDisplayData.map { it.time })
        assertEquals(listOf(meta), state.historicalMetaList)
        assertEquals(listOf(meta), state.allHistoricalMetaList)
        assertNull(state.historicalErrorMessage)
    }

    @Test
    fun fetchHistoricalData_failure_setsHistoricalErrorMessage() = runTest(mainDispatcherRule.testDispatcher) {
        val fixture = createViewModel(settings = stableSettings(), initialData = null)
        fixture.historicalSearchRepository.nextFetchResult = Either.Left(IllegalStateException("history failed"))
        advanceUntilIdle()

        fixture.viewModel.fetchHistoricalData(DamConfig.DEFAULT, "20260501", "20260501")
        advanceUntilIdle()

        val state = fixture.viewModel.uiState.value
        assertFalse(state.isHistoricalMode)
        assertFalse(state.isHistoricalSearchLoading)
        assertEquals("history failed", state.historicalErrorMessage)
    }

    @Test
    fun switchToHistoricalMode_withMetaId_loadsSavedHistoricalData() = runTest(mainDispatcherRule.testDispatcher) {
        val fixture = createViewModel(settings = stableSettings(), initialData = null)
        val meta = historicalSearchMeta(id = 20L, searchBgnDate = "20260502", searchEndDate = "20260502")
        val data = historicalDataSeries(
            "2026/05/02 00:00",
            "2026/05/02 04:00",
            "2026/05/02 08:00"
        )
        fixture.historicalSearchRepository.metaList = listOf(meta)
        fixture.historicalSearchRepository.setDataForMeta(meta.id, data)
        advanceUntilIdle()

        fixture.viewModel.switchToHistoricalMode(meta.id)
        advanceUntilIdle()

        val state = fixture.viewModel.uiState.value
        assertTrue(state.isHistoricalMode)
        assertEquals(meta, state.historicalDamMeta)
        assertEquals(DamConfig.DEFAULT.id, state.historicalDamConfig?.id)
        assertEquals(data, state.historicalAllData)
        assertEquals(listOf("2026/05/02 08:00", "2026/05/02 04:00"), state.historicalDisplayData.map { it.time })
    }

    @Test
    fun switchToHistoricalMode_with24HourTimestamp_sortsAsNextDayMidnight() = runTest(mainDispatcherRule.testDispatcher) {
        val fixture = createViewModel(settings = stableSettings(), initialData = null)
        val meta = historicalSearchMeta(id = 21L, searchBgnDate = "20260502", searchEndDate = "20260502")
        val data = historicalDataSeries(
            "2026/05/02 23:00",
            "2026/05/02 24:00"
        )
        fixture.historicalSearchRepository.metaList = listOf(meta)
        fixture.historicalSearchRepository.setDataForMeta(meta.id, data)
        advanceUntilIdle()

        fixture.viewModel.switchToHistoricalMode(meta.id)
        advanceUntilIdle()

        assertEquals(
            listOf("2026/05/02 24:00", "2026/05/02 23:00"),
            fixture.viewModel.uiState.value.historicalDisplayData.map { it.time }
        )
    }

    @Test
    fun switchToSudmonitorHistoryMode_with24HourLastRow_loadsInitialDisplayData() =
        runTest(mainDispatcherRule.testDispatcher) {
            val fixture = createViewModel(
                settings = stableSettings(),
                initialData = null,
                sudmonitorHistoryRow = sudmonitorHistory(),
                sudmonitorHistoryObservations = historicalDataSeries(
                    "2026/08/01 23:00",
                    "2026/08/01 24:00"
                )
            )
            advanceUntilIdle()

            fixture.viewModel.switchToSudmonitorHistoryMode()
            advanceUntilIdle()

            val state = fixture.viewModel.uiState.value
            assertTrue(state.isSudmonitorHistoryMode)
            // 最新行が「24:00」でも翌日 00:00 としてパースされ、初期表示（直近6時間相当）が空にならない
            assertEquals(
                listOf("2026/08/01 24:00", "2026/08/01 23:00"),
                state.sudmonitorHistoryDisplayData.map { it.time }
            )
        }

    @Test
    fun applyHistoricalDisplayRange_filtersVisibleDataAndResetRestoresAllData() = runTest(mainDispatcherRule.testDispatcher) {
        val fixture = createViewModel(settings = stableSettings(), initialData = null)
        val meta = historicalSearchMeta(id = 30L, searchBgnDate = "20260501", searchEndDate = "20260503")
        val data = historicalDataSeries(
            "2026/05/01 12:00",
            "2026/05/02 12:00",
            "2026/05/03 12:00"
        )
        fixture.historicalSearchRepository.nextFetchResult = Either.Right(meta)
        fixture.historicalSearchRepository.setDataForMeta(meta.id, data)
        advanceUntilIdle()
        fixture.viewModel.fetchHistoricalData(DamConfig.DEFAULT, "20260501", "20260503")
        advanceUntilIdle()

        fixture.viewModel.applyHistoricalDisplayRange("20260502", "20260502")
        advanceUntilIdle()

        var state = fixture.viewModel.uiState.value
        assertTrue(state.isHistoricalDisplayRangeFiltered)
        assertEquals("20260502", state.historicalDisplayStartDate)
        assertEquals(listOf("2026/05/02 12:00"), state.historicalVisibleData.map { it.time })

        fixture.viewModel.resetHistoricalDisplayRange()
        advanceUntilIdle()

        state = fixture.viewModel.uiState.value
        assertFalse(state.isHistoricalDisplayRangeFiltered)
        assertEquals(data, state.historicalVisibleData)
        assertNull(state.historicalDisplayStartDate)
    }

    @Test
    fun deleteCurrentHistoricalData_returnsToRealtimeAndRefreshesHistoryList() = runTest(mainDispatcherRule.testDispatcher) {
        val fixture = createViewModel(settings = stableSettings(), initialData = null)
        val currentMeta = historicalSearchMeta(id = 40L)
        val remainingMeta = historicalSearchMeta(id = 41L, searchBgnDate = "20260502", searchEndDate = "20260502")
        fixture.historicalSearchRepository.nextFetchResult = Either.Right(currentMeta)
        fixture.historicalSearchRepository.metaList = listOf(remainingMeta)
        fixture.historicalSearchRepository.setDataForMeta(currentMeta.id, historicalDataSeries("2026/05/01 00:00"))
        advanceUntilIdle()
        fixture.viewModel.fetchHistoricalData(DamConfig.DEFAULT, "20260501", "20260501")
        advanceUntilIdle()

        fixture.viewModel.deleteHistoricalData(currentMeta.id)
        advanceUntilIdle()

        val state = fixture.viewModel.uiState.value
        assertEquals(listOf(currentMeta.id), fixture.historicalSearchRepository.deletedMetaIds)
        assertFalse(state.isHistoricalMode)
        assertNull(state.historicalDamMeta)
        assertEquals(emptyList<DamHistoricalData>(), state.historicalAllData)
        assertEquals(emptyList<HistoricalSearchMeta>(), state.historicalMetaList)
        assertEquals(listOf(remainingMeta), state.allHistoricalMetaList)
    }

    @Test
    fun deleteAllHistoricalData_returnsToRealtimeWithEmptyHistoryList() = runTest(mainDispatcherRule.testDispatcher) {
        val fixture = createViewModel(settings = stableSettings(), initialData = null)
        val meta = historicalSearchMeta(id = 50L)
        fixture.historicalSearchRepository.nextFetchResult = Either.Right(meta)
        fixture.historicalSearchRepository.setDataForMeta(meta.id, historicalDataSeries("2026/05/01 00:00"))
        advanceUntilIdle()
        fixture.viewModel.fetchHistoricalData(DamConfig.DEFAULT, "20260501", "20260501")
        advanceUntilIdle()

        fixture.viewModel.deleteAllHistoricalData()
        advanceUntilIdle()

        val state = fixture.viewModel.uiState.value
        assertEquals(1, fixture.historicalSearchRepository.deletedAllCount)
        assertFalse(state.isHistoricalMode)
        assertNull(state.historicalDamMeta)
        assertEquals(emptyList<HistoricalSearchMeta>(), state.historicalMetaList)
        assertEquals(emptyList<HistoricalSearchMeta>(), state.allHistoricalMetaList)
    }

    @Test
    fun canOpenHistoricalSearchDialog_atMaxStoredCount_returnsFalse() = runTest(mainDispatcherRule.testDispatcher) {
        val fixture = createViewModel(settings = stableSettings(), initialData = null)
        fixture.historicalSearchRepository.metaList = List(MainViewModel.HISTORICAL_SEARCH_MAX_STORED_COUNT) { index ->
            historicalSearchMeta(id = index + 1L)
        }
        advanceUntilIdle()

        assertFalse(fixture.viewModel.canOpenHistoricalSearchDialog())

        fixture.historicalSearchRepository.metaList = fixture.historicalSearchRepository.metaList.drop(1)
        assertTrue(fixture.viewModel.canOpenHistoricalSearchDialog())
    }

    @Test
    fun reorderHistoricalMeta_reordersStoredAndDisplayedHistory() = runTest(mainDispatcherRule.testDispatcher) {
        val fixture = createViewModel(settings = stableSettings(), initialData = null)
        val first = historicalSearchMeta(id = 1L, searchBgnDate = "20260501", searchEndDate = "20260501")
        val second = historicalSearchMeta(id = 2L, searchBgnDate = "20260502", searchEndDate = "20260502")
        val third = historicalSearchMeta(id = 3L, searchBgnDate = "20260503", searchEndDate = "20260503")
        fixture.historicalSearchRepository.metaList = listOf(first, second, third)
        advanceUntilIdle()

        fixture.viewModel.reorderHistoricalMeta(listOf(3L, 1L, 2L))
        advanceUntilIdle()

        assertEquals(listOf(3L, 1L, 2L), fixture.historicalSearchRepository.reorderedIds)
        assertEquals(listOf(third, first, second), fixture.viewModel.uiState.value.allHistoricalMetaList)
        assertEquals(emptyList<HistoricalSearchMeta>(), fixture.viewModel.uiState.value.historicalMetaList)
    }

    @Test
    fun switchToRealtimeMode_keepsHistoricalDataButClearsRangeFilter() = runTest(mainDispatcherRule.testDispatcher) {
        val fixture = createViewModel(settings = stableSettings(), initialData = null)
        val meta = historicalSearchMeta(id = 60L, searchBgnDate = "20260501", searchEndDate = "20260502")
        val data = historicalDataSeries(
            "2026/05/01 00:00",
            "2026/05/01 12:00",
            "2026/05/02 00:00"
        )
        fixture.historicalSearchRepository.nextFetchResult = Either.Right(meta)
        fixture.historicalSearchRepository.setDataForMeta(meta.id, data)
        advanceUntilIdle()
        fixture.viewModel.fetchHistoricalData(DamConfig.DEFAULT, "20260501", "20260502")
        advanceUntilIdle()
        fixture.viewModel.applyHistoricalDisplayRange("20260502", "20260502")
        advanceUntilIdle()

        fixture.viewModel.switchToRealtimeMode()

        val state = fixture.viewModel.uiState.value
        assertFalse(state.isHistoricalMode)
        assertEquals(data, state.historicalAllData)
        assertEquals(data, state.historicalVisibleData)
        assertFalse(state.isHistoricalDisplayRangeFiltered)
        assertNull(state.historicalDisplayStartDate)
        assertNull(state.historicalDisplayEndDate)
    }

    @Test
    fun targetDamChange_updatesConfigAndClearsHistoricalDisplay() = runTest(mainDispatcherRule.testDispatcher) {
        val fixture = createViewModel(settings = stableSettings(), initialData = null)
        val meta = historicalSearchMeta(id = 61L)
        val data = historicalDataSeries("2026/05/01 00:00")
        fixture.historicalSearchRepository.nextFetchResult = Either.Right(meta)
        fixture.historicalSearchRepository.setDataForMeta(meta.id, data)
        advanceUntilIdle()
        fixture.viewModel.fetchHistoricalData(DamConfig.DEFAULT, "20260501", "20260501")
        advanceUntilIdle()

        val targetDamId = "1368010125140"
        fixture.settingsRepository.updateSettings { it.copy(targetDamId = targetDamId) }
        advanceUntilIdle()

        val state = fixture.viewModel.uiState.value
        assertEquals(targetDamId, state.damConfig?.id)
        assertFalse(state.isHistoricalMode)
        assertNull(state.historicalDamConfig)
        assertNull(state.historicalDamMeta)
        assertTrue(state.historicalAllData.isEmpty())
        assertTrue(state.historicalVisibleData.isEmpty())
    }

    @Test
    fun switchToHistoricalMode_withoutMetaIdRestoresCurrentHistoricalState() = runTest(mainDispatcherRule.testDispatcher) {
        val fixture = createViewModel(settings = stableSettings(), initialData = null)
        val meta = historicalSearchMeta(id = 70L, searchBgnDate = "20260501", searchEndDate = "20260501")
        val data = historicalDataSeries(
            "2026/05/01 00:00",
            "2026/05/01 06:00",
            "2026/05/01 12:00"
        )
        fixture.historicalSearchRepository.nextFetchResult = Either.Right(meta)
        fixture.historicalSearchRepository.setDataForMeta(meta.id, data)
        advanceUntilIdle()
        fixture.viewModel.fetchHistoricalData(DamConfig.DEFAULT, "20260501", "20260501")
        advanceUntilIdle()
        fixture.viewModel.applyHistoricalDisplayRange("20260501", "20260501")
        advanceUntilIdle()
        fixture.viewModel.switchToRealtimeMode()

        fixture.viewModel.switchToHistoricalMode()

        val state = fixture.viewModel.uiState.value
        assertTrue(state.isHistoricalMode)
        assertEquals(meta, state.historicalDamMeta)
        assertEquals(data, state.historicalVisibleData)
        assertFalse(state.isHistoricalDisplayRangeFiltered)
        assertNull(state.historicalDisplayStartDate)
        assertNull(state.historicalDisplayEndDate)
    }

    @Test
    fun resetHistoricalDisplayToInitial_keepsFilteredRangeButRestoresInitialPage() =
        runTest(mainDispatcherRule.testDispatcher) {
            val fixture = createViewModel(settings = stableSettings(), initialData = null)
            val meta = historicalSearchMeta(id = 80L, searchBgnDate = "20260501", searchEndDate = "20260502")
            val data = historicalDataSeries(
                "2026/05/01 00:00",
                "2026/05/01 06:00",
                "2026/05/01 12:00",
                "2026/05/02 00:00",
                "2026/05/02 06:00",
                "2026/05/02 12:00"
            )
            fixture.historicalSearchRepository.nextFetchResult = Either.Right(meta)
            fixture.historicalSearchRepository.setDataForMeta(meta.id, data)
            advanceUntilIdle()
            fixture.viewModel.fetchHistoricalData(DamConfig.DEFAULT, "20260501", "20260502")
            advanceUntilIdle()
            fixture.viewModel.applyHistoricalDisplayRange("20260501", "20260501")
            advanceUntilIdle()

            fixture.viewModel.resetHistoricalDisplayToInitial()
            advanceUntilIdle()

            val state = fixture.viewModel.uiState.value
            assertTrue(state.isHistoricalDisplayRangeFiltered)
            assertEquals("20260501", state.historicalDisplayStartDate)
            assertEquals("20260501", state.historicalDisplayEndDate)
            assertEquals(
                listOf("2026/05/02 00:00"),
                state.historicalDisplayData.map { it.time }
            )
            assertFalse(state.isAllHistoricalLoaded)
        }

    @Test
    fun ensureHistoricalComparison_sameura_loadsThroughLoadingAndCachesByWindow() =
        runTest(mainDispatcherRule.testDispatcher) {
            val fixture = createViewModel(settings = stableSettings(), initialData = damData())
            fixture.comparisonRepository.delayMillis = 1L
            advanceUntilIdle()

            fixture.viewModel.ensureHistoricalComparison(
                HistoricalComparisonMetric.STORAGE_RATE,
                windowStartMillis = 1_000L,
                windowEndMillis = 2_000L
            )
            mainDispatcherRule.testDispatcher.scheduler.runCurrent()

            var state = fixture.viewModel.uiState.value.historicalComparisonStates[
                HistoricalComparisonMetric.STORAGE_RATE
            ]
            assertEquals(HistoricalComparisonLoadState.LOADING, state?.loadState)

            advanceUntilIdle()
            state = fixture.viewModel.uiState.value.historicalComparisonStates[
                HistoricalComparisonMetric.STORAGE_RATE
            ]
            assertEquals(HistoricalComparisonLoadState.READY, state?.loadState)
            assertSame(fixture.comparisonRepository.result.getOrNull(), state?.data)
            assertEquals(1_000L, state?.windowStartMillis)
            assertEquals(2_000L, state?.windowEndMillis)
            assertEquals(1, fixture.comparisonRepository.loadCount)
            assertEquals("1368080700010", fixture.comparisonRepository.lastDamId)
            assertEquals(HistoricalComparisonMetric.STORAGE_RATE, fixture.comparisonRepository.lastMetric)

            fixture.viewModel.ensureHistoricalComparison(
                HistoricalComparisonMetric.STORAGE_RATE,
                windowStartMillis = 1_000L,
                windowEndMillis = 2_000L
            )
            advanceUntilIdle()
            assertEquals(1, fixture.comparisonRepository.loadCount)
        }

    @Test
    fun ensureHistoricalComparison_mainYearPropagatedToRepository() =
        runTest(mainDispatcherRule.testDispatcher) {
            val fixture = createViewModel(settings = stableSettings(), initialData = damData())
            advanceUntilIdle()

            fixture.viewModel.ensureHistoricalComparison(
                HistoricalComparisonMetric.STORAGE_RATE,
                windowStartMillis = 1_000L,
                windowEndMillis = 2_000L,
                mainYear = 2018
            )
            advanceUntilIdle()

            assertEquals(1, fixture.comparisonRepository.loadCount)
            assertEquals(2018, fixture.comparisonRepository.lastMainYear)
            assertEquals(
                HistoricalComparisonLoadState.READY,
                fixture.viewModel.uiState.value.historicalComparisonStates[
                    HistoricalComparisonMetric.STORAGE_RATE
                ]?.loadState
            )
        }

    @Test
    fun ensureHistoricalComparison_nullMainYearPropagatedAsNull() =
        runTest(mainDispatcherRule.testDispatcher) {
            val fixture = createViewModel(settings = stableSettings(), initialData = damData())
            advanceUntilIdle()

            fixture.viewModel.ensureHistoricalComparison(
                HistoricalComparisonMetric.STORAGE_RATE,
                windowStartMillis = 1_000L,
                windowEndMillis = 2_000L
            )
            advanceUntilIdle()

            assertEquals(1, fixture.comparisonRepository.loadCount)
            assertEquals(null, fixture.comparisonRepository.lastMainYear)
        }

    @Test
    fun ensureHistoricalComparison_readyCacheIgnoresMainYearDifference() =
        runTest(mainDispatcherRule.testDispatcher) {
            val fixture = createViewModel(settings = stableSettings(), initialData = damData())
            advanceUntilIdle()

            fixture.viewModel.ensureHistoricalComparison(
                HistoricalComparisonMetric.STORAGE_RATE,
                windowStartMillis = 1_000L,
                windowEndMillis = 2_000L,
                mainYear = 2018
            )
            advanceUntilIdle()
            assertEquals(1, fixture.comparisonRepository.loadCount)

            // READYキャッシュ判定はwindow一致のみ（mainYearはwindow開始から一意に定まるため再読込しない）。
            fixture.viewModel.ensureHistoricalComparison(
                HistoricalComparisonMetric.STORAGE_RATE,
                windowStartMillis = 1_000L,
                windowEndMillis = 2_000L,
                mainYear = 2020
            )
            advanceUntilIdle()
            assertEquals(1, fixture.comparisonRepository.loadCount)
        }

    @Test
    fun ensureHistoricalComparison_differentWindow_reloadsAndUpdatesState() =
        runTest(mainDispatcherRule.testDispatcher) {
            val fixture = createViewModel(settings = stableSettings(), initialData = damData())
            advanceUntilIdle()

            fixture.viewModel.ensureHistoricalComparison(
                HistoricalComparisonMetric.STORAGE_RATE,
                windowStartMillis = 1_000L,
                windowEndMillis = 2_000L
            )
            advanceUntilIdle()
            assertEquals(1, fixture.comparisonRepository.loadCount)

            fixture.viewModel.ensureHistoricalComparison(
                HistoricalComparisonMetric.STORAGE_RATE,
                windowStartMillis = 2_000L,
                windowEndMillis = 3_000L
            )
            advanceUntilIdle()

            assertEquals(2, fixture.comparisonRepository.loadCount)
            val state = fixture.viewModel.uiState.value.historicalComparisonStates[
                HistoricalComparisonMetric.STORAGE_RATE
            ]
            assertEquals(HistoricalComparisonLoadState.READY, state?.loadState)
            assertEquals(2_000L, state?.windowStartMillis)
            assertEquals(3_000L, state?.windowEndMillis)
        }

    @Test
    fun ensureHistoricalComparison_errorThenRetry_recoversToReady() =
        runTest(mainDispatcherRule.testDispatcher) {
            val fixture = createViewModel(settings = stableSettings(), initialData = damData())
            fixture.comparisonRepository.result = Either.Left(IllegalStateException("comparison failed"))
            advanceUntilIdle()

            fixture.viewModel.ensureHistoricalComparison(
                HistoricalComparisonMetric.STORAGE_VOLUME,
                windowStartMillis = 1_000L,
                windowEndMillis = 2_000L
            )
            advanceUntilIdle()

            var state = fixture.viewModel.uiState.value.historicalComparisonStates[
                HistoricalComparisonMetric.STORAGE_VOLUME
            ]
            assertEquals(HistoricalComparisonLoadState.ERROR, state?.loadState)
            assertEquals("comparison failed", state?.error?.message)

            fixture.comparisonRepository.result =
                Either.Right(SharedFakeHistoricalComparisonRepository.emptyComparisonData())
            fixture.viewModel.ensureHistoricalComparison(
                HistoricalComparisonMetric.STORAGE_VOLUME,
                windowStartMillis = 1_000L,
                windowEndMillis = 2_000L
            )
            advanceUntilIdle()

            state = fixture.viewModel.uiState.value.historicalComparisonStates[
                HistoricalComparisonMetric.STORAGE_VOLUME
            ]
            assertEquals(HistoricalComparisonLoadState.READY, state?.loadState)
            assertNull(state?.error)
            assertEquals(2, fixture.comparisonRepository.loadCount)
        }

    @Test
    fun ensureHistoricalComparison_nonSameuraDam_doesNothing() =
        runTest(mainDispatcherRule.testDispatcher) {
            val fixture = createViewModel(
                settings = stableSettings().copy(targetDamId = "1368010125140"),
                initialData = damData()
            )
            advanceUntilIdle()
            assertEquals("1368010125140", fixture.viewModel.uiState.value.damConfig?.id)

            fixture.viewModel.ensureHistoricalComparison(
                HistoricalComparisonMetric.STORAGE_RATE,
                windowStartMillis = 1_000L,
                windowEndMillis = 2_000L
            )
            advanceUntilIdle()

            assertEquals(0, fixture.comparisonRepository.loadCount)
            val state = fixture.viewModel.uiState.value.historicalComparisonStates[
                HistoricalComparisonMetric.STORAGE_RATE
            ]
            assertEquals(
                HistoricalComparisonLoadState.IDLE,
                state?.loadState ?: HistoricalComparisonLoadState.IDLE
            )
        }

    @Test
    fun ensureHistoricalComparison_historicalModeNonSameuraDam_doesNothing() =
        runTest(mainDispatcherRule.testDispatcher) {
            val fixture = createViewModel(settings = stableSettings(), initialData = damData())
            val nonSameuraDamConfig = DamListData.allDams.first { it.id != TEST_DAM_ID }
            val meta = historicalSearchMeta(id = 90L, searchBgnDate = "20260501", searchEndDate = "20260501")
            fixture.historicalSearchRepository.nextFetchResult = Either.Right(meta)
            fixture.historicalSearchRepository.setDataForMeta(meta.id, historicalDataSeries("2026/05/01 00:00"))
            advanceUntilIdle()

            // リアルタイム表示は早明浦のまま、早明浦以外のダムの過去データ検索結果を表示する
            fixture.viewModel.fetchHistoricalData(nonSameuraDamConfig, "20260501", "20260501")
            advanceUntilIdle()
            assertTrue(fixture.viewModel.uiState.value.isHistoricalMode)
            assertEquals(nonSameuraDamConfig.id, fixture.viewModel.uiState.value.historicalDamConfig?.id)

            // 過去データ検索モードでは検索対象ダム基準で判定するため、早明浦以外なら読み込まない
            fixture.viewModel.ensureHistoricalComparison(
                HistoricalComparisonMetric.STORAGE_RATE,
                windowStartMillis = 1_000L,
                windowEndMillis = 2_000L
            )
            advanceUntilIdle()

            assertEquals(0, fixture.comparisonRepository.loadCount)
            val state = fixture.viewModel.uiState.value.historicalComparisonStates[
                HistoricalComparisonMetric.STORAGE_RATE
            ]
            assertEquals(
                HistoricalComparisonLoadState.IDLE,
                state?.loadState ?: HistoricalComparisonLoadState.IDLE
            )
        }

    @Test
    fun ensureHistoricalComparison_historicalModeSameuraDam_loadsEvenIfRealtimeDamDiffers() =
        runTest(mainDispatcherRule.testDispatcher) {
            val fixture = createViewModel(
                settings = stableSettings().copy(targetDamId = "1368010125140"),
                initialData = damData()
            )
            val meta = historicalSearchMeta(id = 91L, searchBgnDate = "20260501", searchEndDate = "20260501")
            fixture.historicalSearchRepository.nextFetchResult = Either.Right(meta)
            fixture.historicalSearchRepository.setDataForMeta(meta.id, historicalDataSeries("2026/05/01 00:00"))
            advanceUntilIdle()
            assertEquals("1368010125140", fixture.viewModel.uiState.value.damConfig?.id)

            // リアルタイム表示が早明浦以外でも、過去データ検索で早明浦の結果を表示した場合は年比較を読める
            fixture.viewModel.fetchHistoricalData(DamConfig.DEFAULT, "20260501", "20260501")
            advanceUntilIdle()
            assertTrue(fixture.viewModel.uiState.value.isHistoricalMode)
            assertEquals(TEST_DAM_ID, fixture.viewModel.uiState.value.historicalDamConfig?.id)

            fixture.viewModel.ensureHistoricalComparison(
                HistoricalComparisonMetric.STORAGE_RATE,
                windowStartMillis = 1_000L,
                windowEndMillis = 2_000L
            )
            advanceUntilIdle()

            assertEquals(1, fixture.comparisonRepository.loadCount)
            assertEquals(TEST_DAM_ID, fixture.comparisonRepository.lastDamId)
            val state = fixture.viewModel.uiState.value.historicalComparisonStates[
                HistoricalComparisonMetric.STORAGE_RATE
            ]
            assertEquals(HistoricalComparisonLoadState.READY, state?.loadState)
        }

    @Test
    fun ensureHistoricalComparison_targetDamChange_clearsStateAndCancelsPendingJob() =
        runTest(mainDispatcherRule.testDispatcher) {
            val fixture = createViewModel(settings = stableSettings(), initialData = damData())
            fixture.comparisonRepository.delayMillis = 10_000L
            advanceUntilIdle()

            fixture.viewModel.ensureHistoricalComparison(
                HistoricalComparisonMetric.STORAGE_RATE,
                windowStartMillis = 1_000L,
                windowEndMillis = 2_000L
            )
            mainDispatcherRule.testDispatcher.scheduler.runCurrent()
            assertEquals(
                HistoricalComparisonLoadState.LOADING,
                fixture.viewModel.uiState.value.historicalComparisonStates[
                    HistoricalComparisonMetric.STORAGE_RATE
                ]?.loadState
            )

            fixture.settingsRepository.updateSettings { it.copy(targetDamId = "1368010125140") }
            advanceUntilIdle()

            assertTrue(fixture.viewModel.uiState.value.historicalComparisonStates.isEmpty())

            mainDispatcherRule.testDispatcher.scheduler.advanceTimeBy(20_000L)
            advanceUntilIdle()
            assertTrue(fixture.viewModel.uiState.value.historicalComparisonStates.isEmpty())
            assertEquals(1, fixture.comparisonRepository.loadCount)
        }

    @Test
    fun ensureHistoricalComparison_requeueDuringLoading_isIgnored() =
        runTest(mainDispatcherRule.testDispatcher) {
            val fixture = createViewModel(settings = stableSettings(), initialData = damData())
            fixture.comparisonRepository.delayMillis = 10_000L
            advanceUntilIdle()

            fixture.viewModel.ensureHistoricalComparison(
                HistoricalComparisonMetric.STORAGE_RATE,
                windowStartMillis = 1_000L,
                windowEndMillis = 2_000L
            )
            mainDispatcherRule.testDispatcher.scheduler.runCurrent()
            assertEquals(
                HistoricalComparisonLoadState.LOADING,
                fixture.viewModel.uiState.value.historicalComparisonStates[
                    HistoricalComparisonMetric.STORAGE_RATE
                ]?.loadState
            )

            fixture.viewModel.ensureHistoricalComparison(
                HistoricalComparisonMetric.STORAGE_RATE,
                windowStartMillis = 1_000L,
                windowEndMillis = 2_000L
            )
            fixture.viewModel.ensureHistoricalComparison(
                HistoricalComparisonMetric.STORAGE_RATE,
                windowStartMillis = 2_000L,
                windowEndMillis = 3_000L
            )
            advanceUntilIdle()

            assertEquals(1, fixture.comparisonRepository.loadCount)
            val state = fixture.viewModel.uiState.value.historicalComparisonStates[
                HistoricalComparisonMetric.STORAGE_RATE
            ]
            assertEquals(HistoricalComparisonLoadState.READY, state?.loadState)
            assertEquals(1_000L, state?.windowStartMillis)
            assertEquals(2_000L, state?.windowEndMillis)
        }

    @Test
    fun ensureHistoricalComparison_metricSwitch_staleResultDoesNotOverwriteNewSelection() =
        runTest(mainDispatcherRule.testDispatcher) {
            val fixture = createViewModel(settings = stableSettings(), initialData = damData())
            fixture.comparisonRepository.delayMillis = 10_000L
            advanceUntilIdle()

            fixture.viewModel.ensureHistoricalComparison(
                HistoricalComparisonMetric.STORAGE_RATE,
                windowStartMillis = 1_000L,
                windowEndMillis = 2_000L
            )
            mainDispatcherRule.testDispatcher.scheduler.runCurrent()
            assertEquals(
                HistoricalComparisonLoadState.LOADING,
                fixture.viewModel.uiState.value.historicalComparisonStates[
                    HistoricalComparisonMetric.STORAGE_RATE
                ]?.loadState
            )

            fixture.comparisonRepository.delayMillis = 0L
            fixture.viewModel.ensureHistoricalComparison(
                HistoricalComparisonMetric.STORAGE_VOLUME,
                windowStartMillis = 5_000L,
                windowEndMillis = 6_000L
            )
            mainDispatcherRule.testDispatcher.scheduler.runCurrent()

            val volumeState = fixture.viewModel.uiState.value.historicalComparisonStates[
                HistoricalComparisonMetric.STORAGE_VOLUME
            ]
            assertEquals(HistoricalComparisonLoadState.READY, volumeState?.loadState)
            assertEquals(5_000L, volumeState?.windowStartMillis)
            assertEquals(6_000L, volumeState?.windowEndMillis)

            advanceUntilIdle()
            val rateState = fixture.viewModel.uiState.value.historicalComparisonStates[
                HistoricalComparisonMetric.STORAGE_RATE
            ]
            assertEquals(HistoricalComparisonLoadState.READY, rateState?.loadState)
            assertEquals(1_000L, rateState?.windowStartMillis)
            assertEquals(2_000L, rateState?.windowEndMillis)
            assertEquals(2, fixture.comparisonRepository.loadCount)
            assertEquals(
                HistoricalComparisonLoadState.READY,
                fixture.viewModel.uiState.value.historicalComparisonStates[
                    HistoricalComparisonMetric.STORAGE_VOLUME
                ]?.loadState
            )
            assertEquals(
                5_000L,
                fixture.viewModel.uiState.value.historicalComparisonStates[
                    HistoricalComparisonMetric.STORAGE_VOLUME
                ]?.windowStartMillis
            )
        }

    @Test
    fun refreshRealtimeHistoricalComparisons_extendsRealtimeWindowWhenLatestAdvancesByHourOrMore() =
        runTest(mainDispatcherRule.testDispatcher) {
            val fixture = createViewModel(settings = stableSettings(), initialData = damData())
            advanceUntilIdle()

            fixture.viewModel.ensureHistoricalComparison(
                HistoricalComparisonMetric.STORAGE_RATE,
                windowStartMillis = jstMillis("2026/05/18 00:00"),
                windowEndMillis = jstMillis("2026/05/18 05:00"),
                isRealtimeWindow = true
            )
            advanceUntilIdle()
            assertEquals(1, fixture.comparisonRepository.loadCount)

            fixture.damRepository.emitData(
                damData(updatedAt = "2026/05/18 06:30").copy(
                    historicalData = historicalDataSeries("2026/05/18 05:15", "2026/05/18 06:30")
                )
            )
            advanceUntilIdle()

            assertEquals(2, fixture.comparisonRepository.loadCount)
            assertEquals(
                jstMillis("2026/05/18 00:00"),
                fixture.comparisonRepository.lastWindowStartMillis
            )
            assertEquals(
                jstMillis("2026/05/18 06:30"),
                fixture.comparisonRepository.lastWindowEndMillis
            )
            val state = fixture.viewModel.uiState.value.historicalComparisonStates[
                HistoricalComparisonMetric.STORAGE_RATE
            ]
            assertEquals(HistoricalComparisonLoadState.READY, state?.loadState)
            assertEquals(jstMillis("2026/05/18 00:00"), state?.windowStartMillis)
            assertEquals(jstMillis("2026/05/18 06:30"), state?.windowEndMillis)
            assertEquals(true, state?.isRealtimeWindow)
        }

    @Test
    fun refreshRealtimeHistoricalComparisons_doesNotReloadWhenLatestAdvancesLessThanHour() =
        runTest(mainDispatcherRule.testDispatcher) {
            val fixture = createViewModel(settings = stableSettings(), initialData = damData())
            advanceUntilIdle()

            fixture.viewModel.ensureHistoricalComparison(
                HistoricalComparisonMetric.STORAGE_RATE,
                windowStartMillis = jstMillis("2026/05/18 00:00"),
                windowEndMillis = jstMillis("2026/05/18 05:00"),
                isRealtimeWindow = true
            )
            advanceUntilIdle()
            assertEquals(1, fixture.comparisonRepository.loadCount)

            fixture.damRepository.emitData(
                damData(updatedAt = "2026/05/18 05:40").copy(
                    historicalData = historicalDataSeries("2026/05/18 05:15", "2026/05/18 05:40")
                )
            )
            advanceUntilIdle()

            assertEquals(1, fixture.comparisonRepository.loadCount)
            assertEquals(
                jstMillis("2026/05/18 05:00"),
                fixture.viewModel.uiState.value.historicalComparisonStates[
                    HistoricalComparisonMetric.STORAGE_RATE
                ]?.windowEndMillis
            )
        }

    @Test
    fun refreshRealtimeHistoricalComparisons_ignoresNonRealtimeWindow() =
        runTest(mainDispatcherRule.testDispatcher) {
            val fixture = createViewModel(settings = stableSettings(), initialData = damData())
            advanceUntilIdle()

            fixture.viewModel.ensureHistoricalComparison(
                HistoricalComparisonMetric.STORAGE_RATE,
                windowStartMillis = jstMillis("2026/05/18 00:00"),
                windowEndMillis = jstMillis("2026/05/18 05:00")
            )
            advanceUntilIdle()
            assertEquals(1, fixture.comparisonRepository.loadCount)

            fixture.damRepository.emitData(
                damData(updatedAt = "2026/05/18 06:30").copy(
                    historicalData = historicalDataSeries("2026/05/18 05:15", "2026/05/18 06:30")
                )
            )
            advanceUntilIdle()

            assertEquals(1, fixture.comparisonRepository.loadCount)
            assertEquals(
                jstMillis("2026/05/18 05:00"),
                fixture.viewModel.uiState.value.historicalComparisonStates[
                    HistoricalComparisonMetric.STORAGE_RATE
                ]?.windowEndMillis
            )
        }

    @Test
    fun toggleMainCardExpansion_togglesOnlyRequestedModeSpecificCard() =
        runTest(mainDispatcherRule.testDispatcher) {
            val fixture = createViewModel(settings = stableSettings(), initialData = null)
            var expected = MainCardExpansionState()

            MainCardExpansionKey.entries.forEach { key ->
                fixture.viewModel.toggleMainCardExpansion(key)
                advanceUntilIdle()
                expected = expected.toggled(key)

                assertEquals(expected, fixture.settingsRepository.currentMainCardExpansionState)
            }
        }

    private fun createViewModel(
        settings: AppSettings,
        initialData: net.tecogonaz.tcsameuradammonitor.domain.model.DamData?,
        networkAvailable: Boolean = true,
        sudmonitorHistoryRow: SudmonitorHistory? = null,
        sudmonitorHistoryObservations: List<DamHistoricalData> = emptyList()
    ): MainViewModelFixture {
        val settingsRepository = FakeSettingsRepository(settings)
        val damRepository = FakeDamDataRepository(initialData)
        val debugLogRepository = FakeDebugLogRepository()
        val historicalSearchRepository = FakeHistoricalSearchRepository()
        val historicalComparisonRepository = FakeHistoricalComparisonRepository()
        val sudmonitorHistoryRepository = FakeSudmonitorHistoryRepository().also {
            it.reset(
                history = sudmonitorHistoryRow,
                observations = sudmonitorHistoryObservations
            )
        }
        val workGateway = FakeDamWorkManagerGateway()
        val networkAvailability = FakeNetworkAvailability(networkAvailable)
        val viewModel = MainViewModel(
            context = context,
            settingsRepository = settingsRepository,
            damDataRepository = damRepository,
            debugLogRepository = debugLogRepository,
            historicalSearchRepository = historicalSearchRepository,
            historicalComparisonRepository = historicalComparisonRepository,
            appNotificationManager = appNotificationManager,
            queryAvailableAppsUseCase = queryAvailableAppsUseCase,
            damWorkManagerGateway = workGateway,
            networkAvailability = networkAvailability,
            sudmonitorHistoryRepository = sudmonitorHistoryRepository
        )
        return MainViewModelFixture(
            viewModel = viewModel,
            settingsRepository = settingsRepository,
            damRepository = damRepository,
            debugLogRepository = debugLogRepository,
            historicalSearchRepository = historicalSearchRepository,
            comparisonRepository = historicalComparisonRepository,
            workGateway = workGateway,
            networkAvailability = networkAvailability,
            sudmonitorHistoryRepository = sudmonitorHistoryRepository
        )
    }

    private data class MainViewModelFixture(
        val viewModel: MainViewModel,
        val settingsRepository: FakeSettingsRepository,
        val damRepository: FakeDamDataRepository,
        val debugLogRepository: FakeDebugLogRepository,
        val historicalSearchRepository: FakeHistoricalSearchRepository,
        val comparisonRepository: FakeHistoricalComparisonRepository,
        val workGateway: FakeDamWorkManagerGateway,
        val networkAvailability: FakeNetworkAvailability,
        val sudmonitorHistoryRepository: FakeSudmonitorHistoryRepository
    )

    private fun stableSettings(initialDialogShown: Boolean = true): AppSettings =
        AppSettings(
            initialAutoUpdateDialogShown = initialDialogShown,
            lastBootTime = System.currentTimeMillis() - 1_000L,
            autoUpdateCustomTimingMillisWeekly = jstMillis("2099/01/05 05:15"),
            autoUpdateCustomTimingMillisDaily = jstMillis("2099/01/01 05:15"),
            autoUpdateCustomTimingMillis12Hours = jstMillis("2099/01/01 05:15"),
            autoUpdateCustomTimingMillisHourly = jstMillis("2099/01/01 05:15")
        )

    private fun jstMillis(value: String): Long =
        TimeUtils.parseJstMillis(value, "yyyy/MM/dd HH:mm") ?: error("Invalid date: $value")

    private fun historicalSearchMeta(
        id: Long,
        searchBgnDate: String = "20260501",
        searchEndDate: String = "20260501"
    ): HistoricalSearchMeta =
        HistoricalSearchMeta(
            id = id,
            observationStationId = TEST_DAM_ID,
            observationStationName = "早明浦ダム",
            riverSystemName = "吉野川",
            riverName = "吉野川",
            damConfigId = TEST_DAM_ID,
            searchBgnDate = searchBgnDate,
            searchEndDate = searchEndDate,
            fetchedAt = jstMillis("2026/05/01 00:00"),
            dataStartTimeStr = null,
            dataEndTimeStr = null,
            dataStartStoragePct = null,
            dataEndStoragePct = null,
            dataMinStoragePct = null,
            dataMaxStoragePct = null
        )

    private fun historicalDataSeries(vararg times: String): List<DamHistoricalData> =
        times.mapIndexed { index, time ->
            DamHistoricalData(
                time = time,
                storagePercentage = 80.0f + index,
                storageVolume = 100_000.0f + index,
                inflow = 10.0f + index,
                outflow = 9.0f + index
            )
        }

    private fun sudmonitorHistory(damId: String = TEST_DAM_ID): SudmonitorHistory =
        SudmonitorHistory(
            damId = damId,
            periodStartEpochMs = jstMillis("2026/08/01 00:00"),
            periodEndEpochMs = jstMillis("2026/08/31 00:00"),
            status = SudmonitorHistory.STATUS_SUCCESS,
            rowCount = 2,
            firstStorageRatePct = 80f,
            lastStorageRatePct = 81f,
            minStorageRatePct = 79f,
            maxStorageRatePct = 82f,
            fetchedAtEpochMs = jstMillis("2026/08/01 05:15"),
            nextUpdateAtEpochMs = jstMillis("2026/08/02 00:13"),
            rawDatPath = null,
            updatedAtEpochMs = jstMillis("2026/08/01 05:15")
        )

    private fun workInfo(
        state: WorkInfo.State,
        tag: String,
        errorMessage: String? = null
    ): WorkInfo =
        WorkInfo(
            id = UUID.randomUUID(),
            state = state,
            tags = setOf(tag),
            outputData = errorMessage?.let {
                workDataOf(DamWorker.KEY_ERROR_MESSAGE to it)
            } ?: androidx.work.Data.EMPTY
        )

    private companion object {
        private const val TEST_DAM_ID = "1368080700010"
    }
}
