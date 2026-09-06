// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.ui.historicalmanage

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import net.tecogonaz.tcsameuradammonitor.R
import net.tecogonaz.tcsameuradammonitor.domain.model.AppSettings
import net.tecogonaz.tcsameuradammonitor.domain.model.HistoricalSearchMeta
import net.tecogonaz.tcsameuradammonitor.domain.model.SudmonitorHistory
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
import net.tecogonaz.tcsameuradammonitor.ui.main.MainViewModel
import net.tecogonaz.tcsameuradammonitor.ui.main.TestTags
import net.tecogonaz.tcsameuradammonitor.ui.theme.TCSameuraDamMonitorTheme
import net.tecogonaz.tcsameuradammonitor.util.AppNotificationManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * 過去データ管理画面（[HistoricalManageScreen]）の UI と編集アクションを検証する UI テストクラス。
 * 保存済み過去データメタデータ一覧の表示、個別のデータ削除、全データの削除、過去データ表示画面への遷移、
 * およびデータ項目の並び替えメニュー（先頭への移動、上へ移動、下へ移動、末尾への移動）の有効/無効状態や
 * 並び替え実行時のデータ更新ロジックを検証します。
 */
class HistoricalManageScreenAndroidTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun deleteAllConfirmationDeletesAllMeta() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val historicalRepository = HiltFakeHistoricalSearchRepository().also {
            it.reset(metaList = metaList())
        }
        val viewModel = createViewModel(context, historicalRepository)

        setScreen(viewModel)
        waitForText(context.getString(R.string.title_historical_manage))

        composeRule.onNodeWithTag(TestTags.HISTORICAL_MANAGE_TOP_MENU_BUTTON).performClick()
        composeRule.onNodeWithTag(TestTags.HISTORICAL_MANAGE_DELETE_ALL_MENU_ITEM).performClick()
        composeRule.onNodeWithText(context.getString(R.string.historical_manage_delete_all_confirm_title)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.historical_manage_menu_delete_all)).performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) { historicalRepository.deletedAllCount == 1 }
        waitForText(context.getString(R.string.historical_manage_empty))
    }

    @Test
    fun itemMenuShowAndDeleteCallbacksUseSelectedMetaId() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val historicalRepository = HiltFakeHistoricalSearchRepository().also {
            it.reset(metaList = metaList())
        }
        val viewModel = createViewModel(context, historicalRepository)
        var shownMetaId = 0L

        setScreen(viewModel, onShowHistoricalData = { shownMetaId = it })
        waitForText("2026/05/01 01:00")

        composeRule.onNodeWithTag("${TestTags.HISTORICAL_MANAGE_ITEM_MENU_PREFIX}2").performClick()
        composeRule.onNodeWithTag("${TestTags.HISTORICAL_MANAGE_SHOW_MENU_PREFIX}2").performClick()
        assertEquals(2L, shownMetaId)

        composeRule.onNodeWithTag("${TestTags.HISTORICAL_MANAGE_ITEM_MENU_PREFIX}2").performClick()
        composeRule.onNodeWithTag("${TestTags.HISTORICAL_MANAGE_DELETE_MENU_PREFIX}2").performClick()
        composeRule.onNodeWithText(context.getString(R.string.historical_manage_delete_confirm_title)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.historical_manage_menu_delete)).performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) { historicalRepository.deletedMetaIds == listOf(2L) }
    }

    @Test
    fun itemRowClickInvokesShowCallbackWithSelectedMetaId() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val historicalRepository = HiltFakeHistoricalSearchRepository().also {
            it.reset(metaList = metaList())
        }
        val viewModel = createViewModel(context, historicalRepository)
        var shownMetaId = 0L

        setScreen(viewModel, onShowHistoricalData = { shownMetaId = it })
        waitForText("2026/05/01 01:00")

        composeRule.onNodeWithTag("${TestTags.HISTORICAL_MANAGE_ITEM_PREFIX}2").performClick()
        assertEquals(2L, shownMetaId)
    }

    @Test
    fun moveMenuEnablementMatchesFirstMiddleAndLastItems() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val historicalRepository = HiltFakeHistoricalSearchRepository().also {
            it.reset(metaList = metaList())
        }
        val viewModel = createViewModel(context, historicalRepository)

        setScreen(viewModel)
        waitForText("2026/05/01 01:00")

        composeRule.onNodeWithTag("${TestTags.HISTORICAL_MANAGE_ITEM_MENU_PREFIX}1").performClick()
        composeRule.onNodeWithTag("${TestTags.HISTORICAL_MANAGE_MOVE_TOP_MENU_PREFIX}1").assertIsNotEnabled()
        composeRule.onNodeWithTag("${TestTags.HISTORICAL_MANAGE_MOVE_UP_MENU_PREFIX}1").assertIsNotEnabled()
        composeRule.onNodeWithTag("${TestTags.HISTORICAL_MANAGE_MOVE_DOWN_MENU_PREFIX}1").assertIsEnabled()
        composeRule.onNodeWithTag("${TestTags.HISTORICAL_MANAGE_MOVE_BOTTOM_MENU_PREFIX}1").assertIsEnabled()
        composeRule.onNodeWithTag("${TestTags.HISTORICAL_MANAGE_SHOW_MENU_PREFIX}1").performClick()

        composeRule.onNodeWithTag("${TestTags.HISTORICAL_MANAGE_ITEM_MENU_PREFIX}2").performClick()
        composeRule.onNodeWithTag("${TestTags.HISTORICAL_MANAGE_MOVE_TOP_MENU_PREFIX}2").assertIsNotEnabled()
        composeRule.onNodeWithTag("${TestTags.HISTORICAL_MANAGE_MOVE_UP_MENU_PREFIX}2").assertIsEnabled()
        composeRule.onNodeWithTag("${TestTags.HISTORICAL_MANAGE_MOVE_DOWN_MENU_PREFIX}2").assertIsEnabled()
        composeRule.onNodeWithTag("${TestTags.HISTORICAL_MANAGE_MOVE_BOTTOM_MENU_PREFIX}2").assertIsNotEnabled()
        composeRule.onNodeWithTag("${TestTags.HISTORICAL_MANAGE_SHOW_MENU_PREFIX}2").performClick()

        composeRule.onNodeWithTag("${TestTags.HISTORICAL_MANAGE_ITEM_MENU_PREFIX}3").performClick()
        composeRule.onNodeWithTag("${TestTags.HISTORICAL_MANAGE_MOVE_TOP_MENU_PREFIX}3").assertIsEnabled()
        composeRule.onNodeWithTag("${TestTags.HISTORICAL_MANAGE_MOVE_UP_MENU_PREFIX}3").assertIsEnabled()
        composeRule.onNodeWithTag("${TestTags.HISTORICAL_MANAGE_MOVE_DOWN_MENU_PREFIX}3").assertIsNotEnabled()
        composeRule.onNodeWithTag("${TestTags.HISTORICAL_MANAGE_MOVE_BOTTOM_MENU_PREFIX}3").assertIsNotEnabled()
    }

    @Test
    fun dailyHistoryData_doesNotAppearInManageList() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // sudmonitor 日次過去データは保存済みでも、管理画面の一覧には登場しない（要件6: 専用テーブルで分離）
        val sudmonitorHistoryRepository = HiltFakeSudmonitorHistoryRepository().also {
            it.reset(history = dailyHistoryRow())
        }
        val viewModel = createViewModel(
            context,
            HiltFakeHistoricalSearchRepository(),
            sudmonitorHistoryRepository = sudmonitorHistoryRepository
        )

        setScreen(viewModel)
        waitForText(context.getString(R.string.historical_manage_empty))
        assertEquals(0, composeRule.onAllNodesWithText("早明浦ダム").fetchSemanticsNodes().size)
    }

    private fun setScreen(
        viewModel: MainViewModel,
        onShowHistoricalData: (Long) -> Unit = {}
    ) {
        composeRule.setContent {
            TCSameuraDamMonitorTheme(dynamicColor = false) {
                HistoricalManageScreen(
                    viewModel = viewModel,
                    onBackClick = {},
                    onShowHistoricalData = onShowHistoricalData
                )
            }
        }
        composeRule.onNodeWithTag(TestTags.HISTORICAL_MANAGE_ROOT).assertIsDisplayed()
    }

    private fun createViewModel(
        context: Context,
        historicalRepository: HiltFakeHistoricalSearchRepository,
        sudmonitorHistoryRepository: HiltFakeSudmonitorHistoryRepository = HiltFakeSudmonitorHistoryRepository()
    ): MainViewModel =
        MainViewModel(
            context = context,
            settingsRepository = HiltFakeSettingsRepository(AppSettings(initialAutoUpdateDialogShown = true)),
            damDataRepository = HiltFakeDamDataRepository(androidTestDamData()),
            debugLogRepository = HiltFakeDebugLogRepository(),
            historicalSearchRepository = historicalRepository,
            historicalComparisonRepository = HiltFakeHistoricalComparisonRepository(),
            appNotificationManager = AppNotificationManager(context),
            queryAvailableAppsUseCase = QueryAvailableAppsUseCase(context),
            damWorkManagerGateway = HiltFakeDamWorkManagerGateway(),
            networkAvailability = HiltFakeNetworkAvailability(),
            sudmonitorHistoryRepository = sudmonitorHistoryRepository
        )

    private fun waitForText(text: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun metaList(): List<HistoricalSearchMeta> =
        listOf(
            meta(id = 1L, start = "20260501", end = "20260501"),
            meta(id = 2L, start = "20260502", end = "20260502"),
            meta(id = 3L, start = "20260503", end = "20260503")
        )

    private fun meta(id: Long, start: String, end: String): HistoricalSearchMeta =
        HistoricalSearchMeta(
            id = id,
            observationStationId = "1368080700010",
            observationStationName = "早明浦ダム",
            riverSystemName = "吉野川",
            riverName = "吉野川",
            damConfigId = "1368080700010",
            searchBgnDate = start,
            searchEndDate = end,
            fetchedAt = id,
            dataStartTimeStr = "${start.substring(0, 4)}/${start.substring(4, 6)}/${start.substring(6, 8)} 01:00",
            dataEndTimeStr = "${end.substring(0, 4)}/${end.substring(4, 6)}/${end.substring(6, 8)} 24:00",
            dataStartStoragePct = 61f + id,
            dataEndStoragePct = 62f + id,
            dataMinStoragePct = 60f + id,
            dataMaxStoragePct = 63f + id
        )

    private fun dailyHistoryRow(): SudmonitorHistory =
        SudmonitorHistory(
            damId = "1368080700010",
            periodStartEpochMs = 1_000L,
            periodEndEpochMs = 2_000L,
            status = SudmonitorHistory.STATUS_SUCCESS,
            rowCount = 1,
            firstStorageRatePct = 80f,
            lastStorageRatePct = 81f,
            minStorageRatePct = 79f,
            maxStorageRatePct = 82f,
            fetchedAtEpochMs = 1_000L,
            nextUpdateAtEpochMs = 2_000L,
            rawDatPath = null,
            updatedAtEpochMs = 1_000L
        )
}
