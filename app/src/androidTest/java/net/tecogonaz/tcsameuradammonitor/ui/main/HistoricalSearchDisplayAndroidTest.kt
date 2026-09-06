// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.ui.main

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import net.tecogonaz.tcsameuradammonitor.MainActivity
import net.tecogonaz.tcsameuradammonitor.R
import net.tecogonaz.tcsameuradammonitor.di.DatabaseModule
import net.tecogonaz.tcsameuradammonitor.di.RepositoryModule
import net.tecogonaz.tcsameuradammonitor.di.WorkerModule
import net.tecogonaz.tcsameuradammonitor.domain.model.AppSettings
import net.tecogonaz.tcsameuradammonitor.domain.model.AutoUpdateInterval
import net.tecogonaz.tcsameuradammonitor.domain.model.DamHistoricalData
import net.tecogonaz.tcsameuradammonitor.domain.model.DamLoadStatus
import net.tecogonaz.tcsameuradammonitor.domain.model.HistoricalSearchMeta
import net.tecogonaz.tcsameuradammonitor.domain.repository.DamDataRepository
import net.tecogonaz.tcsameuradammonitor.domain.repository.DatabaseMaintenanceRepository
import net.tecogonaz.tcsameuradammonitor.domain.repository.DebugDataSessionRepository
import net.tecogonaz.tcsameuradammonitor.domain.repository.DebugLogRepository
import net.tecogonaz.tcsameuradammonitor.domain.repository.HistoricalComparisonRepository
import net.tecogonaz.tcsameuradammonitor.domain.repository.HistoricalSearchRepository
import net.tecogonaz.tcsameuradammonitor.domain.repository.SettingsRepository
import net.tecogonaz.tcsameuradammonitor.domain.repository.SudmonitorHistoryRepository
import net.tecogonaz.tcsameuradammonitor.testutil.HiltFakeDamDataRepository
import net.tecogonaz.tcsameuradammonitor.testutil.HiltFakeDamWorkManagerGateway
import net.tecogonaz.tcsameuradammonitor.testutil.HiltFakeDatabaseMaintenanceRepository
import net.tecogonaz.tcsameuradammonitor.testutil.HiltFakeDebugDataSessionRepository
import net.tecogonaz.tcsameuradammonitor.testutil.HiltFakeDebugLogRepository
import net.tecogonaz.tcsameuradammonitor.testutil.HiltFakeHistoricalComparisonRepository
import net.tecogonaz.tcsameuradammonitor.testutil.HiltFakeHistoricalSearchRepository
import net.tecogonaz.tcsameuradammonitor.testutil.HiltFakeSettingsRepository
import net.tecogonaz.tcsameuradammonitor.testutil.HiltFakeSudmonitorHistoryRepository
import net.tecogonaz.tcsameuradammonitor.testutil.androidTestDamData
import net.tecogonaz.tcsameuradammonitor.util.TimeUtils
import net.tecogonaz.tcsameuradammonitor.worker.DamWorkManagerGateway
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * 過去データ検索結果の表示（過去データ表示モード）における [ObservationGraphCard] への
 * ダムID配線（[MainUiState.displayedDamConfig]）を検証する Instrumentation テストクラス。
 *
 * ドロワーの保存メタエントリから過去データ表示へ遷移し、検索対象ダムが早明浦以外の場合は
 * 年比較modeチップ（貯水率(年比較)／流域平均雨量・貯水量(年比較)／流入量／放流量）と
 * 年チップ（「全て」チップ・各年チップ）が非表示になること、検索対象ダムが早明浦の場合は
 * 年比較modeチップが表示され、比較データ読込（空成功）後に年チップが現れることを検証します。
 */
@HiltAndroidTest
@UninstallModules(
    RepositoryModule::class,
    DatabaseModule::class,
    WorkerModule::class
)
class HistoricalSearchDisplayAndroidTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createEmptyComposeRule()

    private val settingsFake = HiltFakeSettingsRepository(defaultSettings())
    private val damDataFake = HiltFakeDamDataRepository(androidTestDamData())
    private val debugLogFake = HiltFakeDebugLogRepository()
    private val historicalSearchFake = HiltFakeHistoricalSearchRepository()
    private val historicalComparisonFake = HiltFakeHistoricalComparisonRepository()
    private val databaseMaintenanceFake = HiltFakeDatabaseMaintenanceRepository()
    private val debugDataSessionFake = HiltFakeDebugDataSessionRepository()
    private val workGatewayFake = HiltFakeDamWorkManagerGateway()
    private val sudmonitorHistoryFake = HiltFakeSudmonitorHistoryRepository()

    @BindValue
    @JvmField
    val boundSettingsRepository: SettingsRepository = settingsFake

    @BindValue
    @JvmField
    val boundDamDataRepository: DamDataRepository = damDataFake

    @BindValue
    @JvmField
    val boundDebugLogRepository: DebugLogRepository = debugLogFake

    @BindValue
    @JvmField
    val boundHistoricalSearchRepository: HistoricalSearchRepository = historicalSearchFake

    @BindValue
    @JvmField
    val boundHistoricalComparisonRepository: HistoricalComparisonRepository = historicalComparisonFake

    @BindValue
    @JvmField
    val boundDatabaseMaintenanceRepository: DatabaseMaintenanceRepository = databaseMaintenanceFake

    @BindValue
    @JvmField
    val boundDebugDataSessionRepository: DebugDataSessionRepository = debugDataSessionFake

    @BindValue
    @JvmField
    val boundDamWorkManagerGateway: DamWorkManagerGateway = workGatewayFake

    @BindValue
    @JvmField
    val boundSudmonitorHistoryRepository: SudmonitorHistoryRepository = sudmonitorHistoryFake

    @Before
    fun setUp() {
        settingsFake.reset(defaultSettings())
        damDataFake.reset(androidTestDamData(), DamLoadStatus.SUCCESS)
        debugLogFake.reset()
        historicalSearchFake.reset()
        historicalComparisonFake.reset()
        workGatewayFake.reset()
        sudmonitorHistoryFake.reset()
        hiltRule.inject()
    }

    @Test
    fun historicalMode_nonSameuraDam_hidesComparisonChips() {
        // 検索対象ダム=岩尾内ダム（早明浦以外）の保存メタエントリをドロワー経由で導入する
        val meta = historicalSearchMeta(
            id = 100L,
            damConfigId = NON_SAMEURA_DAM_ID,
            observationStationName = "Iwaonai Dam",
            riverSystemName = "Teshio River",
            riverName = "Teshio River"
        )
        historicalSearchFake.reset(
            metaList = listOf(meta),
            dataByMetaId = mapOf(meta.id to historicalRows())
        )

        ActivityScenario.launch(MainActivity::class.java).use {
            waitForMainRoot()
            enterHistoricalModeViaDrawer()
            waitForText(targetString(R.string.main_title_historical_data))

            // 陽性対照: 観測行があるためグラフCard自体は表示される
            composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_CARD).performScrollTo()

            // 検索対象ダムが早明浦以外のため、年比較modeチップは表示しない
            listOf(
                R.string.main_graph_rainfall_storage_history_select,
                R.string.main_graph_volume_flow_history_select
            ).forEach { resId ->
                assertEquals(
                    "検索対象ダムが早明浦以外の過去データ表示では年比較modeチップ（${targetString(resId)}）を表示しないこと",
                    0,
                    composeRule.onAllNodesWithText(targetString(resId)).fetchSemanticsNodes().size
                )
            }
            // 年チップ（「全て」チップと各年チップ）も表示しない
            // （OBSERVATION_GRAPH_YEAR_SCROLL_ROW はライン切替チップ行自体のタグで、
            // 年比較mode以外でもメトリックラインチップを表示するため常時存在する）
            assertEquals(
                "検索対象ダムが早明浦以外の過去データ表示では「全て」年チップを表示しないこと",
                0,
                composeRule.onAllNodesWithTag(TestTags.OBSERVATION_GRAPH_YEAR_ALL_CHIP)
                    .fetchSemanticsNodes().size
            )
            listOf(2026, 2024).forEach { year ->
                assertEquals(
                    "検索対象ダムが早明浦以外の過去データ表示では年チップ（$year）を表示しないこと",
                    0,
                    composeRule.onAllNodesWithTag(TestTags.OBSERVATION_GRAPH_YEAR_CHIP_PREFIX + year)
                        .fetchSemanticsNodes().size
                )
            }
        }
    }

    @Test
    fun historicalMode_sameuraDam_showsComparisonChips() {
        // 検索対象ダム=早明浦ダムの保存メタエントリをドロワー経由で導入する
        val meta = historicalSearchMeta(
            id = 101L,
            damConfigId = AppSettings.DEFAULT_DAM_ID,
            observationStationName = "早明浦ダム",
            riverSystemName = "吉野川",
            riverName = "吉野川"
        )
        historicalSearchFake.reset(
            metaList = listOf(meta),
            dataByMetaId = mapOf(meta.id to historicalRows())
        )

        ActivityScenario.launch(MainActivity::class.java).use {
            waitForMainRoot()
            enterHistoricalModeViaDrawer()
            waitForText(targetString(R.string.main_title_historical_data))

            composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_CARD).performScrollTo()

            // 検索対象ダムが早明浦のため、年比較modeチップ2つが表示される
            // （LazyRow仮想化のため端のチップはスクロールしてから存在を確認する）
            val rainfallStorageHistory =
                targetString(R.string.main_graph_rainfall_storage_history_select)
            val volumeFlowHistory = targetString(R.string.main_graph_volume_flow_history_select)
            scrollModeRowTo(rainfallStorageHistory)
            assertTrue(
                "早明浦ダムの過去データ表示では年比較modeチップ（$rainfallStorageHistory）を表示すること",
                composeRule.onAllNodesWithText(rainfallStorageHistory)
                    .fetchSemanticsNodes().isNotEmpty()
            )
            scrollModeRowTo(volumeFlowHistory)
            assertTrue(
                "早明浦ダムの過去データ表示では年比較modeチップ（$volumeFlowHistory）を表示すること",
                composeRule.onAllNodesWithText(volumeFlowHistory)
                    .fetchSemanticsNodes().isNotEmpty()
            )

            // 貯水率(年比較)チップを選択すると比較データ読込（fake が空の比較データを返す）後に
            // 年チップが表示される
            clickGraphChip(rainfallStorageHistory)
            composeRule.onNodeWithText(rainfallStorageHistory).assertIsSelected()
            composeRule.waitUntil(timeoutMillis = 5_000) {
                composeRule.onAllNodesWithTag(TestTags.OBSERVATION_GRAPH_YEAR_ALL_CHIP)
                    .fetchSemanticsNodes().isNotEmpty()
            }
            // 年チップはグラフキャンバス直下のライン切替チップ行にあり、画面外の場合は
            // スクロールで表示してから検証する。
            composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_YEAR_ALL_CHIP).performScrollTo()
            composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_YEAR_ALL_CHIP).assertIsDisplayed()
        }
    }

    // ---- ヘルパー ----

    private fun enterHistoricalModeViaDrawer() {
        openDrawerIfNeeded()
        composeRule.onNodeWithText(
            DRAWER_META_PERIOD_TEXT,
            substring = true,
            useUnmergedTree = true
        ).performClick()
        composeRule.waitForIdle()
    }

    private fun openDrawerIfNeeded() {
        val menuDescription = targetString(R.string.desc_menu)
        val menuNodes = composeRule.onAllNodesWithContentDescription(menuDescription).fetchSemanticsNodes()
        if (menuNodes.isNotEmpty()) {
            composeRule.onAllNodesWithContentDescription(menuDescription)[0].performClick()
            waitForText(targetString(R.string.nav_settings))
        }
    }

    private fun scrollModeRowTo(label: String) {
        // スマホ表示（840dp未満）では mode チップが1行の横スクロール列になるため、
        // スクロール列があれば対象チップまでスクロールする
        if (composeRule.onAllNodesWithTag(TestTags.OBSERVATION_GRAPH_MODE_SCROLL_ROW)
                .fetchSemanticsNodes().isNotEmpty()
        ) {
            composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_MODE_SCROLL_ROW)
                .performScrollToNode(hasText(label))
        }
    }

    private fun clickGraphChip(label: String) {
        scrollModeRowTo(label)
        composeRule.onNodeWithText(label).performClick()
    }

    private fun waitForMainRoot() {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(TestTags.MAIN_ROOT).fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithTag(TestTags.PERMANENT_ROOT).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()
    }

    private fun targetString(resId: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resId)

    private fun defaultSettings(initialDialogShown: Boolean = true): AppSettings =
        AppSettings(
            initialAutoUpdateDialogShown = initialDialogShown,
            autoUpdateInterval = AutoUpdateInterval.ONE_WEEK,
            autoUpdateCustomTimingMillisWeekly = jstMillis("2099/01/05 05:15"),
            autoUpdateCustomTimingMillisDaily = jstMillis("2099/01/01 05:15"),
            autoUpdateCustomTimingMillis12Hours = jstMillis("2099/01/01 05:15"),
            autoUpdateCustomTimingMillisHourly = jstMillis("2099/01/01 05:15")
        )

    /**
     * ドロワー保存メタエントリ用の [HistoricalSearchMeta] を生成します。
     * 検索期間は 2026/05/01 - 2026/05/02（[DRAWER_META_PERIOD_TEXT] の期間表記に対応）で固定です。
     * ドロワーには起動時にピン留め済みメタのみ表示されるため、常にピン留め状態で生成します。
     */
    private fun historicalSearchMeta(
        id: Long,
        damConfigId: String,
        observationStationName: String,
        riverSystemName: String,
        riverName: String
    ): HistoricalSearchMeta =
        HistoricalSearchMeta(
            id = id,
            observationStationId = damConfigId,
            observationStationName = observationStationName,
            riverSystemName = riverSystemName,
            riverName = riverName,
            damConfigId = damConfigId,
            searchBgnDate = "20260501",
            searchEndDate = "20260501",
            fetchedAt = 0L,
            dataStartTimeStr = "2026/05/01 01:00",
            dataEndTimeStr = "2026/05/02 00:00",
            dataStartStoragePct = 80.0f,
            dataEndStoragePct = 81.0f,
            dataMinStoragePct = 79.0f,
            dataMaxStoragePct = 82.0f,
            isPinned = true
        )

    /** 2026/05/01 01:00 - 2026/05/02 00:00 の観測行（時刻昇順・複数行）。 */
    private fun historicalRows(): List<DamHistoricalData> =
        listOf(
            historicalRow("2026/05/01 01:00", storagePercentage = 79.0f),
            historicalRow("2026/05/01 09:00", storagePercentage = 80.0f),
            historicalRow("2026/05/01 15:00", storagePercentage = 80.5f),
            historicalRow("2026/05/01 21:00", storagePercentage = 81.0f),
            historicalRow("2026/05/02 00:00", storagePercentage = 81.5f)
        )

    private fun historicalRow(time: String, storagePercentage: Float): DamHistoricalData =
        DamHistoricalData(
            time = time,
            catchmentAverageRainfall = 0.0f,
            storageVolume = 100_000f,
            inflow = 10f,
            outflow = 9f,
            storagePercentage = storagePercentage
        )

    private fun jstMillis(value: String): Long =
        TimeUtils.parseJstMillis(value, "yyyy/MM/dd HH:mm") ?: error("Invalid date: $value")

    private companion object {
        /** 早明浦ダム以外の検索対象ダム（岩尾内ダム）。 */
        private const val NON_SAMEURA_DAM_ID = "1368010125140"

        /** ドロワー保存メタエントリの期間表記（JSTサフィックス有無に依存しない部分一致用）。 */
        private const val DRAWER_META_PERIOD_TEXT = "2026/05/01 01:00 - 2026/05/02 00:00"
    }
}
