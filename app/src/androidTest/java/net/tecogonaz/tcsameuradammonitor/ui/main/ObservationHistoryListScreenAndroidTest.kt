// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.ui.main

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import arrow.core.Either
import net.tecogonaz.tcsameuradammonitor.R
import net.tecogonaz.tcsameuradammonitor.domain.model.AppSettings
import net.tecogonaz.tcsameuradammonitor.domain.model.DamData
import net.tecogonaz.tcsameuradammonitor.domain.model.DamHistoricalData
import net.tecogonaz.tcsameuradammonitor.domain.model.HistoricalSearchMeta
import net.tecogonaz.tcsameuradammonitor.domain.usecase.QueryAvailableAppsUseCase
import net.tecogonaz.tcsameuradammonitor.testutil.HiltFakeDamDataRepository
import net.tecogonaz.tcsameuradammonitor.testutil.HiltFakeDamWorkManagerGateway
import net.tecogonaz.tcsameuradammonitor.testutil.HiltFakeDebugLogRepository
import net.tecogonaz.tcsameuradammonitor.testutil.HiltFakeHistoricalComparisonRepository
import net.tecogonaz.tcsameuradammonitor.testutil.HiltFakeHistoricalSearchRepository
import net.tecogonaz.tcsameuradammonitor.testutil.HiltFakeNetworkAvailability
import net.tecogonaz.tcsameuradammonitor.testutil.HiltFakeSettingsRepository
import net.tecogonaz.tcsameuradammonitor.testutil.HiltFakeSudmonitorHistoryRepository
import net.tecogonaz.tcsameuradammonitor.testutil.androidTestDamData
import net.tecogonaz.tcsameuradammonitor.ui.theme.TCSameuraDamMonitorTheme
import net.tecogonaz.tcsameuradammonitor.util.AppNotificationManager
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * ダムの観測履歴データ一覧画面（[ObservationHistoryListScreen]）の UI 表示動作を検証する UI テストクラス。
 * リアルタイムデータの降順（最新のデータが一番上）表示や戻るボタンの検知、過去データ検索から遷移した際の
 * 期間指定過去データの表示、およびデータが一件も存在しない場合のエンプティ状態（「履歴データが存在しません」などのメッセージ）と
 * テーブルヘッダーの表示仕様をテストします。
 */
class ObservationHistoryListScreenAndroidTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun realtimeSource_displaysRowsDescendingAndBackCallback() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var backClicked = false
        val viewModel = createViewModel(
            context = context,
            realtimeData = androidTestDamData(historicalData = historicalRows("2026/05/01"))
        )

        composeRule.setContent {
            TCSameuraDamMonitorTheme(dynamicColor = false) {
                ObservationHistoryListScreen(viewModel = viewModel, onBackClick = { backClicked = true })
            }
        }

        composeRule.onNodeWithTag(TestTags.OBSERVATION_HISTORY_LIST_ROOT).assertIsDisplayed()
        waitForText("05/01 03:00")
        composeRule.onNodeWithText("05/01 01:00").assertIsDisplayed()
        assertTrue(
            composeRule.onNodeWithText("05/01 03:00").fetchSemanticsNode().boundsInRoot.top <
                composeRule.onNodeWithText("05/01 01:00").fetchSemanticsNode().boundsInRoot.top
        )

        composeRule.onNodeWithContentDescription(context.getString(R.string.desc_back)).performClick()
        assertTrue(backClicked)
    }

    @Test
    fun historicalSource_displaysHistoricalRowsInsteadOfRealtimeRows() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val meta = historicalMeta(id = 10L)
        val historicalRepository = HiltFakeHistoricalSearchRepository().also {
            it.reset(dataByMetaId = mapOf(meta.id to historicalRows("2026/05/02")))
            it.nextFetchResult = Either.Right(meta)
        }
        val viewModel = createViewModel(
            context = context,
            realtimeData = androidTestDamData(historicalData = historicalRows("2026/05/01")),
            historicalRepository = historicalRepository
        )
        viewModel.fetchHistoricalData(
            damConfig = net.tecogonaz.tcsameuradammonitor.domain.model.DamConfig.DEFAULT,
            startDate = "20260502",
            endDate = "20260502"
        )

        composeRule.setContent {
            TCSameuraDamMonitorTheme(dynamicColor = false) {
                ObservationHistoryListScreen(viewModel = viewModel, onBackClick = {})
            }
        }

        waitForText("05/02 03:00")
        composeRule.onNodeWithText("05/02 01:00").assertIsDisplayed()
        composeRule.onNodeWithText("05/01 03:00").assertDoesNotExist()
    }

    @Test
    fun emptySource_displaysEmptyState() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val viewModel = createViewModel(
            context = context,
            realtimeData = androidTestDamData(historicalData = emptyList())
        )

        composeRule.setContent {
            TCSameuraDamMonitorTheme(dynamicColor = false) {
                ObservationHistoryListScreen(viewModel = viewModel, onBackClick = {})
            }
        }

        waitForText(context.getString(R.string.observation_history_empty))
        composeRule.onNodeWithTag(TestTags.OBSERVATION_HISTORY_LIST_TABLE).assertIsDisplayed()
    }

    private fun createViewModel(
        context: Context,
        realtimeData: DamData?,
        historicalRepository: HiltFakeHistoricalSearchRepository = HiltFakeHistoricalSearchRepository()
    ): MainViewModel =
        MainViewModel(
            context = context,
            settingsRepository = HiltFakeSettingsRepository(AppSettings(initialAutoUpdateDialogShown = true)),
            damDataRepository = HiltFakeDamDataRepository(realtimeData),
            debugLogRepository = HiltFakeDebugLogRepository(),
            historicalSearchRepository = historicalRepository,
            historicalComparisonRepository = HiltFakeHistoricalComparisonRepository(),
            appNotificationManager = AppNotificationManager(context),
            queryAvailableAppsUseCase = QueryAvailableAppsUseCase(context),
            damWorkManagerGateway = HiltFakeDamWorkManagerGateway(),
            networkAvailability = HiltFakeNetworkAvailability(),
            sudmonitorHistoryRepository = HiltFakeSudmonitorHistoryRepository()
        )

    private fun waitForText(text: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun historicalRows(date: String): List<DamHistoricalData> =
        listOf(
            DamHistoricalData("$date 01:00", 0.1f, 101f, 11f, 10f, 61f),
            DamHistoricalData("$date 02:00", 0.2f, 102f, 12f, 11f, 62f),
            DamHistoricalData("$date 03:00", 0.3f, 103f, 13f, 12f, 63f)
        )

    private fun historicalMeta(id: Long): HistoricalSearchMeta =
        HistoricalSearchMeta(
            id = id,
            observationStationId = "1368080700010",
            observationStationName = "早明浦ダム",
            riverSystemName = "吉野川",
            riverName = "吉野川",
            damConfigId = "1368080700010",
            searchBgnDate = "20260502",
            searchEndDate = "20260502",
            fetchedAt = 0L,
            dataStartTimeStr = "2026/05/02 01:00",
            dataEndTimeStr = "2026/05/02 03:00",
            dataStartStoragePct = 61f,
            dataEndStoragePct = 63f,
            dataMinStoragePct = 61f,
            dataMaxStoragePct = 63f
        )
}
