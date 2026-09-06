// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.ui

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.dragAndDrop
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
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
import net.tecogonaz.tcsameuradammonitor.domain.model.DamLoadStatus
import net.tecogonaz.tcsameuradammonitor.domain.model.MainCardExpansionKey
import net.tecogonaz.tcsameuradammonitor.domain.repository.DamDataRepository
import net.tecogonaz.tcsameuradammonitor.domain.repository.DatabaseMaintenanceRepository
import net.tecogonaz.tcsameuradammonitor.domain.repository.DebugDataSessionRepository
import net.tecogonaz.tcsameuradammonitor.domain.repository.DebugLogRepository
import net.tecogonaz.tcsameuradammonitor.domain.repository.HistoricalSearchRepository
import net.tecogonaz.tcsameuradammonitor.domain.repository.HistoricalComparisonRepository
import net.tecogonaz.tcsameuradammonitor.domain.repository.SettingsRepository
import net.tecogonaz.tcsameuradammonitor.domain.repository.SudmonitorHistoryRepository
import net.tecogonaz.tcsameuradammonitor.testutil.HiltFakeDamDataRepository
import net.tecogonaz.tcsameuradammonitor.testutil.HiltFakeDamWorkManagerGateway
import net.tecogonaz.tcsameuradammonitor.testutil.HiltFakeDatabaseMaintenanceRepository
import net.tecogonaz.tcsameuradammonitor.testutil.HiltFakeDebugDataSessionRepository
import net.tecogonaz.tcsameuradammonitor.testutil.HiltFakeDebugLogRepository
import net.tecogonaz.tcsameuradammonitor.testutil.HiltFakeHistoricalSearchRepository
import net.tecogonaz.tcsameuradammonitor.testutil.HiltFakeHistoricalComparisonRepository
import net.tecogonaz.tcsameuradammonitor.testutil.HiltFakeSettingsRepository
import net.tecogonaz.tcsameuradammonitor.testutil.HiltFakeSudmonitorHistoryRepository
import net.tecogonaz.tcsameuradammonitor.testutil.androidTestDamData
import net.tecogonaz.tcsameuradammonitor.ui.main.TestTags
import net.tecogonaz.tcsameuradammonitor.util.TimeUtils
import net.tecogonaz.tcsameuradammonitor.worker.DamWorkManagerGateway
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@HiltAndroidTest
@UninstallModules(
    RepositoryModule::class,
    DatabaseModule::class,
    WorkerModule::class
)
/**
 * メイン画面（[MainActivity]）のアクセシビリティ対応用セマンティクス（Semantics）および基本的な画面要素の表示を
 * 検証する UI テストクラス。
 * ダムデータの正常取得表示、取得エラー時のステータスセマンティクス（読み上げ対象の LiveRegion など）、
 * 初回起動時の自動更新ダイアログの表示と状態変更、見出しセマンティクス（Heading）、
 * コンテナのトラバーサル設定（TraversalGroup）、およびメイン画面全体のスクロール処理（マウスドラッグによるスクロールを含む）を
 * 検証します。
 */
class MainActivitySemanticsTest {
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
        workGatewayFake.reset()
        sudmonitorHistoryFake.reset()
        hiltRule.inject()
    }

    @Test
    fun successState_displaysKnownSummaryContent() {
        launchMainActivity().use {
            waitForMainRoot()

            composeRule.onNodeWithTag(TestTags.MAIN_ROOT).assertIsDisplayed()
            assertMainStatusContainerVisible()
            assertAnyText("80.00%")
            composeRule.onNodeWithTag(TestTags.MANUAL_UPDATE_BUTTON, useUnmergedTree = true).assertIsDisplayed()
            assertAnyContentDescription(targetString(R.string.desc_reload))
            composeRule.onNodeWithTag(TestTags.AUTO_UPDATE_BUTTON, useUnmergedTree = true).assertIsDisplayed()
            assertAnyContentDescription(targetString(R.string.nav_auto_update))
            assertGraphOrPermanentMainContentVisible()
        }
    }

    @Test
    fun successState_compactSummaryUsesWidgetFourLineContent() {
        settingsFake.reset(
            defaultSettings().copy(
                state80_100 = "○",
                msg80_100 = "Normal",
                msg80_100Ja = "平常"
            )
        )

        launchMainActivity().use {
            waitForMainRoot()

            if (mainLayoutMode() == MainLayoutMode.COMPACT) {
                assertAnyText("80.00% →")
                assertAnyText(
                    "${targetString(R.string.summary_storage_percentage_day_change)} +0.00% →"
                )
                composeRule.onNodeWithTag(TestTags.SUMMARY_STATUS_TEXT).assertIsDisplayed()
            }
        }
    }

    @Test
    fun loadingErrorState_displaysConfiguredErrorState() {
        settingsFake.reset(defaultSettings().copy(stateLoadingError = "!"))
        damDataFake.reset(data = null, loadStatus = DamLoadStatus.LOADING_FAILURE)

        launchMainActivity().use {
            waitForMainRoot()

            assertMainStatusContainerVisible()
            assertAnyText("!")
        }
    }

    @Test
    fun firstRunDialog_displaysAndDismisses() {
        settingsFake.reset(defaultSettings(initialDialogShown = false))
        damDataFake.reset(data = null, loadStatus = DamLoadStatus.INITIAL)
        val title = targetString(R.string.dialog_initial_auto_update_title)
        val no = targetString(R.string.action_no)

        launchMainActivity().use {
            waitForMainRoot()

            composeRule.onNodeWithText(title).assertIsDisplayed()
            composeRule.onNodeWithText(no).performClick()
            composeRule.waitForIdle()

            assertTrue(settingsFake.current.initialAutoUpdateDialogShown)
        }
    }

    @Test
    fun mainScreen_hasHeadingSemantics() {
        launchMainActivity().use {
            waitForMainRoot()

            val hasHeading = SemanticsMatcher("node has heading() semantics") { node ->
                node.config.getOrNull(SemanticsProperties.Heading) != null
            }
            val headings = composeRule.onAllNodes(hasHeading).fetchSemanticsNodes()
            assertTrue("Expected headings in main screen for card and section titles", headings.isNotEmpty())
        }
    }

    @Test
    fun mainCards_haveTraversalGroup() {
        launchMainActivity().use {
            waitForMainRoot()

            when (mainLayoutMode()) {
                MainLayoutMode.COMPACT -> {
                    val node = composeRule.onNodeWithTag(TestTags.SUMMARY_CARD).fetchSemanticsNode()
                    assertEquals(
                        "Expected compact summary card to be a traversal group",
                        true,
                        node.config.getOrNull(SemanticsProperties.IsTraversalGroup)
                    )
                }
                MainLayoutMode.PERMANENT -> {
                    composeRule.onNodeWithTag(TestTags.PERMANENT_ROOT).assertIsDisplayed()
                    assertAnyText(targetString(R.string.nav_settings))
                    assertAnyText("80.00%")
                }
            }
        }
    }

    @Test
    fun observationCardExpansion_survivesActivityRecreation() {
        launchMainActivity().use { scenario ->
            waitForMainRoot()
            val expandedDescriptions =
                taggedContentDescriptions(TestTags.OBSERVATION_EXPAND_BUTTON)
            composeRule.onNodeWithTag(
                TestTags.OBSERVATION_EXPAND_BUTTON,
                useUnmergedTree = true
            ).performScrollTo().performClick()
            composeRule.waitUntil(timeoutMillis = 5_000) {
                !settingsFake.currentMainCardExpansionState[
                    MainCardExpansionKey.REALTIME_OBSERVATION
                ]
            }
            composeRule.waitUntil(timeoutMillis = 5_000) {
                taggedContentDescriptions(TestTags.OBSERVATION_EXPAND_BUTTON) !=
                    expandedDescriptions
            }
            val collapsedDescriptions =
                taggedContentDescriptions(TestTags.OBSERVATION_EXPAND_BUTTON)

            scenario.recreate()
            waitForMainRoot()

            composeRule.waitUntil(timeoutMillis = 5_000) {
                taggedContentDescriptions(TestTags.OBSERVATION_EXPAND_BUTTON) ==
                    collapsedDescriptions
            }
        }
    }

    @Test
    fun mainScrollContainer_acceptsMouseDragWhenScrollable() {
        launchMainActivity().use {
            waitForMainRoot()

            val before = mainScrollAxisValue()
            val max = mainScrollAxisMaxValue()
            composeRule.onNodeWithTag(TestTags.MAIN_SCROLL_CONTAINER).assertIsDisplayed()

            if (max > before) {
                composeRule.onNodeWithTag(TestTags.MAIN_SCROLL_CONTAINER).performMouseInput {
                    dragAndDrop(start = bottomCenter, end = topCenter, durationMillis = 250)
                }

                composeRule.waitUntil(timeoutMillis = 5_000) {
                    mainScrollAxisValue() > before
                }
                assertTrue("Expected mouse drag to move the main scroll container", mainScrollAxisValue() > before)
            } else {
                assertEquals(
                    "Non-scrollable layout should expose a stable zero-length scroll range",
                    before,
                    max
                )
            }
        }
    }

    @Test
    fun loadingErrorState_hasErrorSemantics() {
        settingsFake.reset(defaultSettings().copy(stateLoadingError = "!"))
        damDataFake.reset(data = null, loadStatus = DamLoadStatus.LOADING_FAILURE)

        launchMainActivity().use {
            waitForMainRoot()

            when (mainLayoutMode()) {
                MainLayoutMode.COMPACT -> {
                    val statusNode = composeRule.onNodeWithTag(TestTags.SUMMARY_STATUS_TEXT).fetchSemanticsNode()
                    assertTrue(
                        "Expected live region semantics on summary status text",
                        statusNode.config.getOrNull(SemanticsProperties.LiveRegion) != null
                    )
                }
                MainLayoutMode.PERMANENT -> {
                    composeRule.onNodeWithTag(TestTags.PERMANENT_ROOT).assertIsDisplayed()
                    assertAnyText("!")
                }
            }
        }
    }

    @Test
    fun initialStateWithoutData_hidesStatusMessage() {
        settingsFake.reset(
            defaultSettings().copy(
                stateInitialMessage = "?",
                msgInitialMessage = "INIT-EN",
                msgInitialMessageJa = "INIT-JA"
            )
        )
        damDataFake.reset(data = null, loadStatus = DamLoadStatus.INITIAL)

        launchMainActivity().use {
            waitForMainRoot()

            when (mainLayoutMode()) {
                MainLayoutMode.COMPACT -> {
                    composeRule.onNodeWithTag(TestTags.SUMMARY_CARD).assertIsDisplayed()
                    assertFalse(
                        "Expected no summary status text while initial load is pending",
                        hasNodesWithTag(TestTags.SUMMARY_STATUS_TEXT)
                    )
                }
                MainLayoutMode.PERMANENT -> {
                    composeRule.onNodeWithTag(TestTags.PERMANENT_ROOT).assertIsDisplayed()
                }
            }
            assertNoText("INIT-EN")
            assertNoText("INIT-JA")
        }
    }

    @Test
    fun successWithoutData_hidesStatusMessage() {
        settingsFake.reset(
            defaultSettings().copy(
                stateLoadingError = "!",
                msgLoadingError = "ERR-EN",
                msgLoadingErrorJa = "ERR-JA"
            )
        )
        damDataFake.reset(data = null, loadStatus = DamLoadStatus.SUCCESS)

        launchMainActivity().use {
            waitForMainRoot()

            when (mainLayoutMode()) {
                MainLayoutMode.COMPACT -> {
                    composeRule.onNodeWithTag(TestTags.SUMMARY_CARD).assertIsDisplayed()
                    assertFalse(
                        "Expected no summary status text while data delivery is pending",
                        hasNodesWithTag(TestTags.SUMMARY_STATUS_TEXT)
                    )
                }
                MainLayoutMode.PERMANENT -> {
                    composeRule.onNodeWithTag(TestTags.PERMANENT_ROOT).assertIsDisplayed()
                }
            }
            assertNoText("ERR-EN")
            assertNoText("ERR-JA")
        }
    }

    @Test
    fun networkUnavailableStateWithoutData_displaysNetworkMessage() {
        settingsFake.reset(
            defaultSettings().copy(
                stateNetworkUnavailable = "~",
                msgNetworkUnavailable = "NET-EN",
                msgNetworkUnavailableJa = "NET-JA"
            )
        )
        damDataFake.reset(data = null, loadStatus = DamLoadStatus.NETWORK_UNAVAILABLE)

        launchMainActivity().use {
            waitForMainRoot()

            when (mainLayoutMode()) {
                MainLayoutMode.COMPACT ->
                    composeRule.onNodeWithTag(TestTags.SUMMARY_STATUS_TEXT).assertIsDisplayed()
                MainLayoutMode.PERMANENT ->
                    composeRule.onNodeWithTag(TestTags.PERMANENT_ROOT).assertIsDisplayed()
            }
            val displayed =
                composeRule.onAllNodesWithText("NET-EN", substring = true)
                    .fetchSemanticsNodes().isNotEmpty() ||
                    composeRule.onAllNodesWithText("NET-JA", substring = true)
                        .fetchSemanticsNodes().isNotEmpty()
            assertTrue("Expected network unavailable message", displayed)
        }
    }

    private enum class MainLayoutMode {
        COMPACT,
        PERMANENT
    }

    private fun launchMainActivity(): ActivityScenario<MainActivity> =
        ActivityScenario.launch(MainActivity::class.java)

    private fun waitForMainRoot() {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(TestTags.MAIN_ROOT).fetchSemanticsNodes().isNotEmpty() &&
                composeRule.onAllNodesWithTag(TestTags.MAIN_SCROLL_CONTAINER)
                    .fetchSemanticsNodes()
                    .isNotEmpty()
        }
        composeRule.waitForIdle()
    }

    private fun mainLayoutMode(): MainLayoutMode =
        when {
            hasNodesWithTag(TestTags.SUMMARY_CARD) -> MainLayoutMode.COMPACT
            hasNodesWithTag(TestTags.PERMANENT_ROOT) -> MainLayoutMode.PERMANENT
            else -> error("Expected compact summary card or permanent layout root.")
        }

    private fun assertMainStatusContainerVisible() {
        when (mainLayoutMode()) {
            MainLayoutMode.COMPACT -> composeRule.onNodeWithTag(TestTags.SUMMARY_CARD).assertIsDisplayed()
            MainLayoutMode.PERMANENT -> composeRule.onNodeWithTag(TestTags.PERMANENT_ROOT).assertIsDisplayed()
        }
    }

    private fun assertGraphOrPermanentMainContentVisible() {
        when (mainLayoutMode()) {
            MainLayoutMode.COMPACT -> {
                when {
                    hasNodesWithTag(TestTags.OBSERVATION_GRAPH_CARD) -> {
                        composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_CARD).performScrollTo()
                        composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_CARD).assertIsDisplayed()
                    }
                    hasNodesWithTag(TestTags.LATEST_CARD) -> {
                        composeRule.onNodeWithTag(TestTags.LATEST_CARD).performScrollTo()
                        composeRule.onNodeWithTag(TestTags.LATEST_CARD).assertIsDisplayed()
                    }
                    hasNodesWithTag(TestTags.OBSERVATION_CARD) -> {
                        composeRule.onNodeWithTag(TestTags.OBSERVATION_CARD).performScrollTo()
                        composeRule.onNodeWithTag(TestTags.OBSERVATION_CARD).assertIsDisplayed()
                    }
                    else -> error("Expected graph, latest, or observation content in compact main layout.")
                }
            }
            MainLayoutMode.PERMANENT -> {
                composeRule.onNodeWithTag(TestTags.PERMANENT_ROOT).assertIsDisplayed()
                assertAnyText("80.00%")
            }
        }
    }

    private fun hasNodesWithTag(tag: String): Boolean =
        composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    private fun mainScrollAxisValue(): Float =
        composeRule.onNodeWithTag(TestTags.MAIN_SCROLL_CONTAINER)
            .fetchSemanticsNode()
            .config
            .getOrNull(SemanticsProperties.VerticalScrollAxisRange)
            ?.value
            ?.invoke()
            ?: 0f

    private fun mainScrollAxisMaxValue(): Float =
        composeRule.onNodeWithTag(TestTags.MAIN_SCROLL_CONTAINER)
            .fetchSemanticsNode()
            .config
            .getOrNull(SemanticsProperties.VerticalScrollAxisRange)
            ?.maxValue
            ?.invoke()
            ?: 0f

    private fun assertAnyText(text: String) {
        assertTrue(composeRule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty())
    }

    private fun assertNoText(text: String) {
        assertTrue(composeRule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isEmpty())
    }

    private fun assertAnyContentDescription(text: String, substring: Boolean = false) {
        assertTrue(
            composeRule.onAllNodesWithContentDescription(text, substring = substring)
                .fetchSemanticsNodes()
                .isNotEmpty() ||
                composeRule.onAllNodesWithContentDescription(
                    text,
                    substring = substring,
                    useUnmergedTree = true
                ).fetchSemanticsNodes().isNotEmpty()
        )
    }

    private fun taggedContentDescriptions(tag: String): List<String> =
        composeRule.onNodeWithTag(tag)
            .fetchSemanticsNode()
            .config
            .getOrNull(SemanticsProperties.ContentDescription)
            .orEmpty()

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

    private fun jstMillis(value: String): Long =
        TimeUtils.parseJstMillis(value, "yyyy/MM/dd HH:mm") ?: error("Invalid date: $value")
}
