// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.ui.main

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkInfo
import arrow.core.Either
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import java.util.UUID
import net.tecogonaz.tcsameuradammonitor.MainActivity
import net.tecogonaz.tcsameuradammonitor.R
import net.tecogonaz.tcsameuradammonitor.di.DatabaseModule
import net.tecogonaz.tcsameuradammonitor.di.RepositoryModule
import net.tecogonaz.tcsameuradammonitor.di.WorkerModule
import net.tecogonaz.tcsameuradammonitor.domain.model.AppSettings
import net.tecogonaz.tcsameuradammonitor.domain.model.DamHistoricalData
import net.tecogonaz.tcsameuradammonitor.domain.model.DamLoadStatus
import net.tecogonaz.tcsameuradammonitor.domain.model.SudmonitorHistory
import net.tecogonaz.tcsameuradammonitor.domain.model.AutoUpdateInterval
import net.tecogonaz.tcsameuradammonitor.domain.repository.DamDataRepository
import net.tecogonaz.tcsameuradammonitor.domain.repository.DatabaseMaintenanceRepository
import net.tecogonaz.tcsameuradammonitor.domain.repository.DebugDataSessionRepository
import net.tecogonaz.tcsameuradammonitor.domain.repository.DebugLogRepository
import net.tecogonaz.tcsameuradammonitor.domain.repository.HistoricalComparisonRepository
import net.tecogonaz.tcsameuradammonitor.domain.repository.HistoricalSearchRepository
import net.tecogonaz.tcsameuradammonitor.domain.repository.SettingsRepository
import net.tecogonaz.tcsameuradammonitor.domain.repository.SudmonitorHistoryRepository
import net.tecogonaz.tcsameuradammonitor.domain.repository.SudmonitorHistoryTrigger
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
import net.tecogonaz.tcsameuradammonitor.worker.DamWorker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * sudmonitor 日次過去データ表示画面（過去データ(日次)モード）の UI を検証する Instrumentation テストクラス。
 *
 * サイドバーの常設エントリから日次モードへ遷移し、タイトル・右上アイコン（[期間指定][自動更新][手動更新]）の並び順、
 * サマリーCardの期間表記（終端 = 最終被覆日の翌日 00:00）、観測一覧の新しい順表示、期間指定ダイアログ、
 * 手動更新クールダウンの Snackbar（次回更新時刻表示）、取得実行中の手動更新アイコンクリック時の進行中 Snackbar
 * （初回読込系は自動更新進行中・手動更新は手動更新進行中。リアルタイム表示と同等）、早明浦ダム限定の比較グラフ利用、
 * 検索拒否メッセージの表示、行未保存時の常設エントリ非表示を検証します。
 */
@HiltAndroidTest
@UninstallModules(
    RepositoryModule::class,
    DatabaseModule::class,
    WorkerModule::class
)
class SudmonitorHistoryDisplayAndroidTest {
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
    fun dailyHistoryMode_displaysTitleAndActionIconsInOrder() {
        sudmonitorHistoryFake.reset(history = dailyHistory(), observations = dailyObservations())

        ActivityScenario.launch(MainActivity::class.java).use {
            waitForMainRoot()
            enterDailyHistoryMode()

            // タイトル
            waitForText(targetString(R.string.main_title_daily_history_data))

            // 右上アイコン: [期間指定][自動更新][手動更新] の順で左から並ぶ
            val rangeX = composeRule.onNodeWithContentDescription(
                targetString(R.string.desc_historical_range)
            ).fetchSemanticsNode().positionInRoot.x
            val autoX = composeRule.onNodeWithTag(TestTags.AUTO_UPDATE_BUTTON)
                .fetchSemanticsNode().positionInRoot.x
            val manualX = composeRule.onNodeWithTag(TestTags.MANUAL_UPDATE_BUTTON)
                .fetchSemanticsNode().positionInRoot.x
            assertTrue(
                "期間指定・自動更新・手動更新の順に並ぶこと (range=$rangeX auto=$autoX manual=$manualX)",
                rangeX < autoX && autoX < manualX
            )
        }
    }

    @Test
    fun dailyHistoryMode_observationList_showsNewestFirst() {
        sudmonitorHistoryFake.reset(history = dailyHistory(), observations = dailyObservations())

        ActivityScenario.launch(MainActivity::class.java).use {
            waitForMainRoot()
            enterDailyHistoryMode()
            waitForText(targetString(R.string.main_title_daily_history_data))

            // 観測データ(一覧)Card: 新しい順（最新が先頭）で表示される
            composeRule.onNodeWithTag(TestTags.HISTORY_CARD).performScrollTo()
            waitForText("08/07 21:00")
            // 先頭7行相当の初期表示には、直近6時間より古い行が含まれない
            assertEquals(
                0,
                composeRule.onAllNodesWithText("08/01 01:00").fetchSemanticsNodes().size
            )
        }
    }

    @Test
    fun dailyHistoryMode_summaryCard_periodEndShowsNextDayMidnight() {
        sudmonitorHistoryFake.reset(history = dailyHistory(), observations = dailyObservations())

        ActivityScenario.launch(MainActivity::class.java).use {
            waitForMainRoot()
            enterDailyHistoryMode()
            waitForText(targetString(R.string.main_title_daily_history_data))

            // サマリーCardの期間行: 終端は最終被覆日の 24:00 = 翌日 00:00 表記になる
            // （periodEndEpochMs = 2026/07/31 00:00 の日単位規約 → 表示は 2026/08/01 00:00）。
            // 端末TZがJST以外の場合は終端にのみ (JST) が付くため、appendJstSuffix 経由の
            // TZ非依存期待値で検証する。同一テキストがDrawer常設エントリにも表示されるため、
            // サマリーCard配下に限定して検証する
            // PERMANENTレイアウトでは日次サマリーCardは非表示仕様(サイドバー常設エントリが
            // 同等の期間情報を表示する)のため、Card表示の検証はCOMPACT(DRAWER)レイアウトのみで行う
            if (isPermanentLayout()) return

            composeRule.onNodeWithTag(TestTags.HISTORICAL_SUMMARY_CARD).performScrollTo()
            composeRule.onNode(
                hasText("2026/07/01 01:00 - ${TimeUtils.appendJstSuffix("2026/08/01 00:00")}") and
                        hasAnyAncestor(hasTestTag(TestTags.HISTORICAL_SUMMARY_CARD)),
                useUnmergedTree = true
            ).assertIsDisplayed()
        }
    }

    @Test
    fun dailyMode_emptyState_summaryCardShowsDamNameOnlyAndHidesCards() {
        sudmonitorHistoryFake.reset(history = null, observations = emptyList())

        ActivityScenario.launch(MainActivity::class.java).use {
            waitForMainRoot()
            openDrawerIfNeeded()

            // ダム名のみの常設エントリをタップして日次モードへ入る
            val nameOnlyMatcher = SemanticsMatcher("ダム名のみの日次エントリ") { node ->
                node.config.getOrNull(SemanticsProperties.ContentDescription)
                    ?.any { it.contains("早明浦ダム") && !it.contains(", ") } == true
            }
            composeRule.onAllNodes(nameOnlyMatcher)[0].performClick()
            waitForText(targetString(R.string.main_title_daily_history_data))

            // サマリーCardはダム名のみ（期間行・貯水率範囲行・欠測プレースホルダーの表示なし）
            // PERMANENTレイアウトでは日次サマリーCardは非表示仕様のため、Card表示の検証は
            // COMPACT(DRAWER)レイアウトのみで行う(日次モードへの遷移自体はPERMANENTでも検証済み)
            if (isPermanentLayout()) return

            composeRule.onNodeWithTag(TestTags.HISTORICAL_SUMMARY_CARD).assertIsDisplayed()
            composeRule.onNode(
                hasText("早明浦ダム", substring = true) and
                        hasAnyAncestor(hasTestTag(TestTags.HISTORICAL_SUMMARY_CARD)),
                useUnmergedTree = true
            ).assertIsDisplayed()
            assertEquals(
                0,
                composeRule.onAllNodes(
                    hasText("2026/07/01 01:00", substring = true) and
                            hasAnyAncestor(hasTestTag(TestTags.HISTORICAL_SUMMARY_CARD)),
                    useUnmergedTree = true
                ).fetchSemanticsNodes().size
            )
            assertEquals(
                0,
                composeRule.onAllNodes(
                    hasText("%", substring = true) and
                            hasAnyAncestor(hasTestTag(TestTags.HISTORICAL_SUMMARY_CARD)),
                    useUnmergedTree = true
                ).fetchSemanticsNodes().size
            )

            // 観測データ(一覧)Card・観測データ(グラフ)Cardは行未保存の間は表示しない
            // （メインコンテンツは verticalScroll の Column で全子をコンポーズするため非存在を検証できる）
            assertEquals(
                0,
                composeRule.onAllNodesWithTag(TestTags.HISTORY_CARD).fetchSemanticsNodes().size
            )
            assertEquals(
                0,
                composeRule.onAllNodesWithTag(TestTags.OBSERVATION_GRAPH_CARD).fetchSemanticsNodes().size
            )
        }
    }

    @Test
    fun dailyHistoryMode_rangeDialog_opensWithinLoadedPeriod() {
        sudmonitorHistoryFake.reset(history = dailyHistory(), observations = dailyObservations())

        ActivityScenario.launch(MainActivity::class.java).use {
            waitForMainRoot()
            enterDailyHistoryMode()
            waitForText(targetString(R.string.main_title_daily_history_data))

            composeRule.onNodeWithContentDescription(
                targetString(R.string.desc_historical_range)
            ).performClick()

            waitForText(targetString(R.string.historical_range_dialog_title))
            composeRule.onNodeWithText(targetString(R.string.historical_range_display_button))
                .assertIsDisplayed()
            // 読込済み期間（2026/07/01〜2026/07/31）が初期表示される
            composeRule.onNodeWithText("2026/07/01").assertIsDisplayed()
            composeRule.onNodeWithText("2026/07/31").assertIsDisplayed()
            // 既存の表示期間ダイアログと同じ説明書き（開始日 01:00 〜 終了翌日 00:00）が表示される
            composeRule.onNodeWithText(
                String.format(
                    targetString(R.string.historical_search_period_note),
                    "2026/07/01",
                    "2026/08/01"
                )
            ).assertIsDisplayed()
        }
    }

    @Test
    fun dailyHistoryMode_manualUpdateCooldown_showsSnackbarWithSavedTime() {
        val nextUpdateAt = System.currentTimeMillis() + 60 * 60 * 1000L
        sudmonitorHistoryFake.reset(
            history = dailyHistory(nextUpdateAtEpochMs = nextUpdateAt),
            observations = dailyObservations()
        )

        ActivityScenario.launch(MainActivity::class.java).use {
            waitForMainRoot()
            enterDailyHistoryMode()
            waitForText(targetString(R.string.main_title_daily_history_data))

            // cooldown 中の手動更新は実行されず、保存済み次回更新時刻を Snackbar 表示する
            composeRule.onNodeWithTag(TestTags.MANUAL_UPDATE_BUTTON).performClick()

            val expectedTime = TimeUtils.appendJstSuffix(
                TimeUtils.formatToJst(nextUpdateAt, "yyyy/MM/dd HH:mm")
            )
            composeRule.waitUntil(timeoutMillis = 5_000) {
                composeRule.onAllNodesWithText(expectedTime, substring = true)
                    .fetchSemanticsNodes().isNotEmpty()
            }
            assertEquals(0, workGatewayFake.enqueuedSudmonitorHistoryTriggers.size)
        }
    }

    @Test
    fun dailyHistoryMode_initialWorkRunning_manualClickShowsAutoUpdateInProgressSnackbar() {
        sudmonitorHistoryFake.reset(history = dailyHistory(), observations = dailyObservations())

        ActivityScenario.launch(MainActivity::class.java).use {
            waitForMainRoot()
            enterDailyHistoryMode()
            waitForText(targetString(R.string.main_title_daily_history_data))

            // 初回読込系（INITIAL）実行中に手動更新アイコンをクリックすると、
            // リアルタイム表示と同等に「自動更新が進行中です」の Snackbar を表示する
            workGatewayFake.emitSudmonitorHistoryWorkInfos(
                listOf(
                    sudmonitorHistoryWorkInfo(
                        WorkInfo.State.RUNNING,
                        SudmonitorHistoryTrigger.INITIAL
                    )
                )
            )
            composeRule.waitForIdle()
            composeRule.onNodeWithTag(TestTags.MANUAL_UPDATE_BUTTON).performClick()

            waitForText(targetString(R.string.main_autorenew_in_progress))
            assertEquals(0, workGatewayFake.enqueuedSudmonitorHistoryTriggers.size)
        }
    }

    @Test
    fun dailyHistoryMode_manualWorkRunning_manualClickShowsManualUpdateInProgressSnackbar() {
        sudmonitorHistoryFake.reset(history = dailyHistory(), observations = dailyObservations())

        ActivityScenario.launch(MainActivity::class.java).use {
            waitForMainRoot()
            enterDailyHistoryMode()
            waitForText(targetString(R.string.main_title_daily_history_data))

            // 手動更新（MANUAL）実行中の再クリックでは「手動更新が進行中です」の Snackbar を表示する
            workGatewayFake.emitSudmonitorHistoryWorkInfos(
                listOf(
                    sudmonitorHistoryWorkInfo(
                        WorkInfo.State.RUNNING,
                        SudmonitorHistoryTrigger.MANUAL
                    )
                )
            )
            composeRule.waitForIdle()
            composeRule.onNodeWithTag(TestTags.MANUAL_UPDATE_BUTTON).performClick()

            waitForText(targetString(R.string.main_manual_update_in_progress))
            assertEquals(0, workGatewayFake.enqueuedSudmonitorHistoryTriggers.size)
        }
    }

    @Test
    fun dailyHistoryMode_sameuraDam_offersComparisonGraphModes() {
        sudmonitorHistoryFake.reset(history = dailyHistory(), observations = dailyObservations())

        ActivityScenario.launch(MainActivity::class.java).use {
            waitForMainRoot()
            enterDailyHistoryMode()
            waitForText(targetString(R.string.main_title_daily_history_data))

            // グラフCardへスクロールし、早明浦限定の比較モード（3・4）を利用できる
            composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_CARD).performScrollTo()
            // 期間表記は履歴扱い（開始日 01:00 - 終了日 00:00）になる
            waitForText("2026/08/01 01:00 - 2026/08/07 00:00")
            // 履歴扱いのため「全期間/過去72時間/過去48時間/過去24時間」の範囲チップは表示しない
            listOf(
                R.string.graph_range_full_range,
                R.string.graph_range_past_72_hours,
                R.string.graph_range_past_48_hours,
                R.string.graph_range_past_24_hours
            ).forEach { resId ->
                assertEquals(
                    "日次モードでは範囲チップ（${targetString(resId)}）を表示しないこと",
                    0,
                    composeRule.onAllNodesWithText(targetString(resId)).fetchSemanticsNodes().size
                )
            }
            val mode3 = targetString(R.string.main_graph_rainfall_storage_history_select)
            clickGraphChip(mode3)
            composeRule.onNodeWithText(mode3).assertIsSelected()
            // 読み込み成功（fake が空の比較データを返す）後、年チップが表示される
            composeRule.waitUntil(timeoutMillis = 5_000) {
                composeRule.onAllNodesWithTag(TestTags.OBSERVATION_GRAPH_YEAR_ALL_CHIP)
                    .fetchSemanticsNodes().isNotEmpty()
            }
            val mode4 = targetString(R.string.main_graph_volume_flow_history_select)
            clickGraphChip(mode4)
            composeRule.onNodeWithText(mode4).assertIsSelected()
            // 年チップはグラフキャンバス直下のライン切替チップ行にあり、画面外の場合は
            // スクロールで表示してから検証する。
            composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_YEAR_ALL_CHIP).performScrollTo()
            composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_YEAR_ALL_CHIP).assertIsDisplayed()
        }
    }

    @Test
    fun searchRejection_dailyLoadedError_shownInSearchDialog() {
        val rejectionMessage = targetString(R.string.historical_search_error_sudmonitor_daily_loaded)
        historicalSearchFake.nextFetchResult = Either.Left(Exception(rejectionMessage))

        ActivityScenario.launch(MainActivity::class.java).use {
            waitForMainRoot()

            openDrawerIfNeeded()
            composeRule.onNodeWithText(targetString(R.string.nav_historical_search)).performClick()
            waitForText(targetString(R.string.historical_search_dialog_title))

            composeRule.onNodeWithText(targetString(R.string.historical_search_button)).performClick()

            // 検索拒否（読込済み期間と完全同一）のメッセージがダイアログ内に表示される
            waitForText(rejectionMessage)
        }
    }

    @Test
    fun drawerEntry_nameOnlyWhenNoRowLoaded() {
        sudmonitorHistoryFake.reset(history = null, observations = emptyList())

        ActivityScenario.launch(MainActivity::class.java).use {
            waitForMainRoot()
            openDrawerIfNeeded()

            // 行未保存でも機能ゲート有効なら常設エントリ（ダム名のみ）とセクション見出しを表示する
            composeRule.onNodeWithText(targetString(R.string.nav_daily_history_data)).assertIsDisplayed()
            // 日次常設エントリの記述はダム名のみ（期間・貯水率推移行は「, 」区切りで付加されるため、
            // 「, 」を含まないダム名記述ノードの存在で判定する）
            val nameOnlyNodes = composeRule.onAllNodes(
                SemanticsMatcher("ダム名のみの日次エントリ") { node ->
                    node.config.getOrNull(SemanticsProperties.ContentDescription)
                        ?.any { it.contains("早明浦ダム") && !it.contains(", ") } == true
                }
            ).fetchSemanticsNodes()
            assertTrue("ダム名のみの常設エントリが表示されること", nameOnlyNodes.isNotEmpty())
        }
    }

    // ---- ヘルパー ----

    private fun enterDailyHistoryMode() {
        openDrawerIfNeeded()
        composeRule.onNodeWithText(
            "2026/07/01 01:00 - 2026/08/01 00:00",
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

    private fun clickGraphChip(label: String) {
        // スマホ表示（840dp未満）では mode チップが1行の横スクロール列になるため、
        // スクロール列があれば対象チップまでスクロールしてからクリックする
        if (composeRule.onAllNodesWithTag(TestTags.OBSERVATION_GRAPH_MODE_SCROLL_ROW)
                .fetchSemanticsNodes().isNotEmpty()
        ) {
            composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_MODE_SCROLL_ROW)
                .performScrollToNode(hasText(label))
        }
        composeRule.onNodeWithText(label).performClick()
    }

    private fun waitForMainRoot() {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(TestTags.MAIN_ROOT).fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithTag(TestTags.PERMANENT_ROOT).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()
    }

    private fun isPermanentLayout(): Boolean =
        composeRule.onAllNodesWithTag(TestTags.PERMANENT_ROOT).fetchSemanticsNodes().isNotEmpty()

    private fun waitForText(text: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()
    }

    private fun targetString(resId: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resId)

    private fun sudmonitorHistoryWorkInfo(
        state: WorkInfo.State,
        trigger: SudmonitorHistoryTrigger
    ): WorkInfo =
        WorkInfo(
            id = UUID.randomUUID(),
            state = state,
            tags = setOf(
                DamWorker.WORK_TYPE_SUDMONITOR_HISTORY,
                DamWorker.sudmonitorHistoryTriggerTag(trigger)
            ),
            outputData = androidx.work.Data.EMPTY
        )

    private fun defaultSettings(initialDialogShown: Boolean = true): AppSettings =
        AppSettings(
            initialAutoUpdateDialogShown = initialDialogShown,
            autoUpdateInterval = AutoUpdateInterval.ONE_WEEK,
            autoUpdateCustomTimingMillisWeekly = jstMillis("2099/01/05 05:15"),
            autoUpdateCustomTimingMillisDaily = jstMillis("2099/01/01 05:15"),
            autoUpdateCustomTimingMillis12Hours = jstMillis("2099/01/01 05:15"),
            autoUpdateCustomTimingMillisHourly = jstMillis("2099/01/01 05:15")
        )

    private fun dailyHistory(
        nextUpdateAtEpochMs: Long = jstMillis("2026/08/02 00:13")
    ): SudmonitorHistory =
        SudmonitorHistory(
            damId = AppSettings.DEFAULT_DAM_ID,
            periodStartEpochMs = jstMillis("2026/07/01 00:00"),
            periodEndEpochMs = jstMillis("2026/07/31 00:00"),
            status = SudmonitorHistory.STATUS_SUCCESS,
            rowCount = 35,
            firstStorageRatePct = 80f,
            lastStorageRatePct = 81f,
            minStorageRatePct = 79f,
            maxStorageRatePct = 82f,
            fetchedAtEpochMs = jstMillis("2026/08/01 05:15"),
            nextUpdateAtEpochMs = nextUpdateAtEpochMs,
            rawDatPath = null,
            updatedAtEpochMs = jstMillis("2026/08/01 05:15")
        )

    /** 2026/08/01〜2026/08/07 の各日 01:00/09:00/15:00/17:00/21:00 の観測行（時刻昇順）。 */
    private fun dailyObservations(): List<DamHistoricalData> =
        buildList {
            for (day in 1..7) {
                for (hour in listOf(1, 9, 15, 17, 21)) {
                    add(
                        DamHistoricalData(
                            time = "2026/08/%02d %02d:00".format(day, hour),
                            catchmentAverageRainfall = 0.0f,
                            storageVolume = 100_000f,
                            inflow = 10f,
                            outflow = 9f,
                            storagePercentage = 80f
                        )
                    )
                }
            }
        }

    private fun jstMillis(value: String): Long =
        TimeUtils.parseJstMillis(value, "yyyy/MM/dd HH:mm") ?: error("Invalid date: $value")
}
