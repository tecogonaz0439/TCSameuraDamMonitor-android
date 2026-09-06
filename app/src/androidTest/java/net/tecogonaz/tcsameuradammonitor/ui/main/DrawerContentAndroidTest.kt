// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.ui.main

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import net.tecogonaz.tcsameuradammonitor.R
import net.tecogonaz.tcsameuradammonitor.domain.model.AppSettings
import net.tecogonaz.tcsameuradammonitor.domain.model.DamConfig
import net.tecogonaz.tcsameuradammonitor.domain.model.DamData
import net.tecogonaz.tcsameuradammonitor.domain.model.DamLoadStatus
import net.tecogonaz.tcsameuradammonitor.domain.model.HistoricalSearchMeta
import net.tecogonaz.tcsameuradammonitor.domain.model.SudmonitorHistory
import net.tecogonaz.tcsameuradammonitor.domain.model.Trend
import net.tecogonaz.tcsameuradammonitor.testutil.androidTestDamData
import net.tecogonaz.tcsameuradammonitor.util.TimeUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * ナビゲーションドロワーの中身（[DrawerContent]）の UI とアクション制御を検証する UI テストクラス。
 * 各メニュー項目（設定、過去データ検索、過去データ管理、デバッグログ※表示条件を満たした場合のみ）の
 * クリックイベントの検知、情報源（国土交通省水文水質データベース）リンク選択時のコンテキストメニュー（共有・コピー処理）、
 * および保存済みの過去データリスト・sudmonitor 日次過去データの常設エントリを選択した際の
 * 過去データ表示モード切り替えイベントの発火を検証します。
 */
@RunWith(AndroidJUnit4::class)
@MediumTest
class DrawerContentAndroidTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun settingsItem_invokesSettingsCallback() {
        val events = mutableListOf<String>()
        setDrawerContent(
            onSettingsClick = { events += "settings" }
        )

        composeRule.onNodeWithText(context.getString(R.string.nav_settings)).performClick()

        composeRule.runOnIdle {
            assertEquals(listOf("settings"), events)
        }
    }

    @Test
    fun collapseButton_invokesCollapseCallback() {
        val events = mutableListOf<String>()
        setDrawerContent(
            onCollapseSidebarClick = { events += "collapse" }
        )

        composeRule.onNodeWithTag(TestTags.SIDEBAR_COLLAPSE_BUTTON).performClick()

        composeRule.runOnIdle {
            assertEquals(listOf("collapse"), events)
        }
    }

    @Test
    fun collapseButton_notShownWithoutCallback() {
        setDrawerContent()

        assertEquals(
            0,
            composeRule.onAllNodesWithTag(TestTags.SIDEBAR_COLLAPSE_BUTTON).fetchSemanticsNodes().size
        )
    }

    @Test
    fun debugItem_hiddenByDefault() {
        setDrawerContent()

        assertEquals(
            0,
            composeRule.onAllNodesWithText(context.getString(R.string.nav_debug)).fetchSemanticsNodes().size
        )
    }

    @Test
    fun debugItem_whenVisibleInvokesDebugCallback() {
        val events = mutableListOf<String>()
        setDrawerContent(
            appSettings = AppSettings(debugSettingsVisible = true),
            onDebugClick = { events += "debug" }
        )

        composeRule.onNodeWithText(context.getString(R.string.nav_debug)).performClick()

        composeRule.runOnIdle {
            assertEquals(listOf("debug"), events)
        }
    }

    @Test
    fun historicalSearchItem_invokesSearchCallback() {
        val events = mutableListOf<String>()
        setDrawerContent(
            onHistoricalSearchClick = { events += "search" }
        )

        composeRule.onNodeWithText(context.getString(R.string.nav_historical_search)).performClick()

        composeRule.runOnIdle {
            assertEquals(listOf("search"), events)
        }
    }

    @Test
    fun historicalManageItem_invokesManageCallback() {
        val events = mutableListOf<String>()
        setDrawerContent(
            onManageHistoricalClick = { events += "manage" }
        )

        composeRule.onNodeWithText(context.getString(R.string.nav_historical_manage)).performClick()

        composeRule.runOnIdle {
            assertEquals(listOf("manage"), events)
        }
    }

    @Test
    fun sourceLinkUrlShare_sharesSourceUrlInsteadOfTitle() {
        val sharedTexts = mutableListOf<String>()
        setDrawerContent(
            onShareText = { sharedTexts += it }
        )

        composeRule.onNodeWithText(context.getString(R.string.nav_source_db)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.action_share_url)).performClick()

        composeRule.runOnIdle {
            assertEquals(listOf("https://www1.river.go.jp/"), sharedTexts)
        }
    }

    @Test
    fun sourceLinkTitleShare_sharesTitleInsteadOfUrl() {
        val sharedTexts = mutableListOf<String>()
        setDrawerContent(
            onShareText = { sharedTexts += it }
        )

        composeRule.onNodeWithText(context.getString(R.string.nav_source_db)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.action_share_link_title)).performClick()

        composeRule.runOnIdle {
            assertEquals(listOf(context.getString(R.string.nav_source_db)), sharedTexts)
        }
    }

    @Test
    fun sourceLinkTitleCopy_copiesTitleToClipboard() {
        val copiedTexts = mutableListOf<String>()
        setDrawerContent(
            onCopied = { copiedTexts += it }
        )

        composeRule.onNodeWithText(context.getString(R.string.nav_source_db)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.action_copy_link_title)).performClick()

        composeRule.runOnIdle {
            assertEquals(listOf(context.getString(R.string.nav_source_db)), copiedTexts)
        }
    }

    @Test
    fun storedHistoricalMetaItem_invokesSwitchToHistoricalMode() {
        val events = mutableListOf<String>()
        val meta = historicalSearchMeta(id = 7L)
        setDrawerContent(
            historicalMetaList = listOf(meta),
            onSwitchToHistoricalMode = { events += "switch:$it" }
        )

        composeRule.onNodeWithText(
            "2026/05/01 01:00 - 2026/05/02 00:00",
            substring = true,
            useUnmergedTree = true
        ).performClick()

        composeRule.runOnIdle {
            assertEquals(listOf("switch:7"), events)
        }
    }

    @Test
    fun realtimeItem_withUpTrendWhileSelected_keepsPercentageTrendAndAccessibilitySemantics() {
        val events = mutableListOf<String>()
        setDrawerContent(
            realtimeDamData = androidTestDamData().copy(storagePercentageTrend = Trend.UP),
            isHistoricalMode = false,
            onRealtimeClick = { events += "realtime" }
        )

        composeRule.onNodeWithContentDescription("80.00% ↗", substring = true)
            .performClick()
        composeRule.onNodeWithText("80.00% ↗", substring = true, useUnmergedTree = true)
            .assertTextContains("80.00% ↗", substring = true)
        composeRule.runOnIdle {
            assertEquals(emptyList<String>(), events)
        }
    }

    @Test
    fun realtimeItem_withUpTrendWhileNotSelected_keepsPercentageTrendAndInvokesCallback() {
        val events = mutableListOf<String>()
        setDrawerContent(
            realtimeDamData = androidTestDamData().copy(storagePercentageTrend = Trend.UP),
            isHistoricalMode = true,
            onRealtimeClick = { events += "realtime" }
        )

        composeRule.onNodeWithContentDescription("80.00% ↗", substring = true)
            .performClick()
        composeRule.onNodeWithText("80.00% ↗", substring = true, useUnmergedTree = true)
            .assertTextContains("80.00% ↗", substring = true)
        composeRule.runOnIdle {
            assertEquals(listOf("realtime"), events)
        }
    }

    @Test
    fun realtimeItem_loadingPendingWithoutData_hidesStatusMessage() {
        setDrawerContent(
            realtimeDamData = null,
            realtimeLoadStatus = DamLoadStatus.INITIAL,
            appSettings = AppSettings(
                stateInitialMessage = "?",
                msgInitialMessage = "INIT-EN",
                msgInitialMessageJa = "INIT-JA"
            )
        )

        assertEquals(
            0,
            composeRule.onAllNodesWithText("INIT-EN", substring = true, useUnmergedTree = true)
                .fetchSemanticsNodes().size
        )
        assertEquals(
            0,
            composeRule.onAllNodesWithText("INIT-JA", substring = true, useUnmergedTree = true)
                .fetchSemanticsNodes().size
        )
    }

    @Test
    fun realtimeItem_loadingFailureWithoutData_showsStatusMessage() {
        setDrawerContent(
            realtimeDamData = null,
            realtimeLoadStatus = DamLoadStatus.LOADING_FAILURE,
            appSettings = AppSettings(
                stateLoadingError = "!",
                msgLoadingError = "ERR-EN",
                msgLoadingErrorJa = "ERR-JA"
            )
        )

        val displayed =
            composeRule.onAllNodesWithText("ERR-EN", substring = true, useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithText("ERR-JA", substring = true, useUnmergedTree = true)
                    .fetchSemanticsNodes().isNotEmpty()
        assertTrue("Expected loading error message in drawer realtime entry", displayed)
    }

    @Test
    fun dailyHistoryEntry_showsThreeLinesAndInvokesSwitchToDailyMode() {
        val events = mutableListOf<String>()
        val history = dailyHistory(damId = DamConfig.DEFAULT.id)
        setDrawerContent(
            sudmonitorHistory = history,
            isDailyHistoryEnabled = true,
            onSwitchToSudmonitorHistoryMode = { events += "daily" }
        )

        // セクション見出しを確認する
        composeRule.onNodeWithText(context.getString(R.string.nav_daily_history_data)).assertIsDisplayed()
        // 常設エントリの3行表示（ダム名 / 期間 / 貯水率推移）は clearAndSetSemantics により
        // アイテムの contentDescription としてマージされるため、そちらで確認する。
        // 期間終端の (JST) は端末TZ依存のため、appendJstSuffix 経由のTZ非依存期待値で検証する
        composeRule.onNodeWithContentDescription(
            "早明浦ダム, 2026/07/01 01:00 - ${TimeUtils.appendJstSuffix("2026/08/01 00:00")}, 80.00% → 81.00% (79.00% ~ 82.00%)",
            substring = true
        ).assertIsDisplayed()
        // 3行の個別テキストはマージされず unmerged tree に残る（既存 meta 項目と同じ扱い）。
        // ダム名はリアルタイムセクションにも存在するため全ノードで存在確認する
        assertTrue(
            composeRule.onAllNodesWithText("早明浦ダム", substring = true, useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        )
        composeRule.onNodeWithText(
            "2026/07/01 01:00 - ${TimeUtils.appendJstSuffix("2026/08/01 00:00")}",
            substring = true,
            useUnmergedTree = true
        ).assertIsDisplayed()
        composeRule.onNodeWithText(
            "80.00% → 81.00% (79.00% ~ 82.00%)",
            substring = true,
            useUnmergedTree = true
        ).assertIsDisplayed()

        composeRule.onNodeWithText(
            "2026/07/01 01:00 - ${TimeUtils.appendJstSuffix("2026/08/01 00:00")}",
            substring = true,
            useUnmergedTree = true
        ).performClick()

        composeRule.runOnIdle {
            assertEquals(listOf("daily"), events)
        }
    }

    @Test
    fun dailyHistoryEntry_notSelected_doesNotInvokeCallbackOnRepeatedClick() {
        val events = mutableListOf<String>()
        val history = dailyHistory(damId = DamConfig.DEFAULT.id)
        setDrawerContent(
            sudmonitorHistory = history,
            isDailyHistoryEnabled = true,
            isSudmonitorHistoryMode = true,
            onSwitchToSudmonitorHistoryMode = { events += "daily" }
        )

        // 選択中（isSudmonitorHistoryMode = true）の常設エントリは callback を発火しない
        composeRule.onNodeWithText(
            "2026/07/01 01:00 - ${TimeUtils.appendJstSuffix("2026/08/01 00:00")}",
            substring = true,
            useUnmergedTree = true
        ).performClick()

        composeRule.runOnIdle {
            assertEquals(emptyList<String>(), events)
        }
    }

    @Test
    fun dailyHistorySections_visibleWithNameOnlyWhenNoRowLoadedAndGateEnabled() {
        val events = mutableListOf<String>()
        // リアルタイム項目にデータを与え、ダム名のみの contentDescription を日次エントリに限定する
        setDrawerContent(
            realtimeDamData = androidTestDamData(),
            sudmonitorHistory = null,
            isDailyHistoryEnabled = true,
            onSwitchToSudmonitorHistoryMode = { events += "daily" }
        )

        // 機能ゲート有効+行未保存でもセクション見出しと常設エントリ（ダム名のみ）を表示する。
        // ダム名のみエントリは contentDescription が「ダム名」のみ（区切り無し）で、期間・
        // 貯水率行を含むリアルタイム項目（「, 」区切り）と区別するため、カンマを含まないノードに限定する
        composeRule.onNodeWithText(context.getString(R.string.nav_daily_history_data)).assertIsDisplayed()
        val nameOnlyMatcher = SemanticsMatcher("ダム名のみの日次エントリ") { node ->
            node.config.getOrNull(SemanticsProperties.ContentDescription)
                ?.any { it.contains("早明浦ダム") && !it.contains(", ") } == true
        }
        val nameOnlyNode = composeRule.onAllNodes(nameOnlyMatcher)[0]
        nameOnlyNode.assertIsDisplayed()
        // 期間行・貯水率推移行は省略される（日次エントリ固有の表記「yyyy/MM/dd 01:00」と
        // 貯水率前後の「% → 」＝範囲行の書式で判定。リアルタイム項目のトレンド矢印「NN% →」とは無関係）
        assertEquals(
            0,
            composeRule.onAllNodesWithText("2026/07/01 01:00", substring = true, useUnmergedTree = true)
                .fetchSemanticsNodes().size
        )
        assertEquals(
            0,
            composeRule.onAllNodesWithText("% → ", substring = true, useUnmergedTree = true)
                .fetchSemanticsNodes().size
        )

        // エントリをタップすると日次モードへの切替 callback が発火する
        nameOnlyNode.performClick()

        composeRule.runOnIdle {
            assertEquals(listOf("daily"), events)
        }
    }

    @Test
    fun dailyHistorySections_hiddenWhenGateDisabled() {
        setDrawerContent(sudmonitorHistory = null, isDailyHistoryEnabled = false)

        // 機能ゲート無効時（過去データ=MLIT直接）は行の有無に関わらずセクション自体を出さない
        assertEquals(
            0,
            composeRule.onAllNodesWithText(context.getString(R.string.nav_daily_history_data))
                .fetchSemanticsNodes().size
        )
    }

    private fun setDrawerContent(
        realtimeDamData: DamData? = null,
        realtimeLoadStatus: DamLoadStatus = DamLoadStatus.INITIAL,
        isHistoricalMode: Boolean = false,
        onRealtimeClick: () -> Unit = {},
        historicalMetaList: List<HistoricalSearchMeta> = emptyList(),
        onHistoricalSearchClick: () -> Unit = {},
        onManageHistoricalClick: () -> Unit = {},
        onSwitchToHistoricalMode: (Long) -> Unit = {},
        appSettings: AppSettings = AppSettings(),
        onDebugClick: () -> Unit = {},
        onSettingsClick: () -> Unit = {},
        onShareText: (String) -> Unit = {},
        onCopied: (String) -> Unit = {},
        isDailyHistoryEnabled: Boolean = false,
        sudmonitorHistory: SudmonitorHistory? = null,
        isSudmonitorHistoryMode: Boolean = false,
        onSwitchToSudmonitorHistoryMode: () -> Unit = {},
        onCollapseSidebarClick: (() -> Unit)? = null
    ) {
        composeRule.setContent {
            MaterialTheme {
                DrawerContent(
                    isPermanent = false,
                    contentPage = ContentPage.MAIN,
                    realtimeDamData = realtimeDamData,
                    realtimeDamConfig = DamConfig.DEFAULT,
                    realtimeLoadStatus = realtimeLoadStatus,
                    appSettings = appSettings,
                    isHistoricalMode = isHistoricalMode,
                    onRealtimeClick = onRealtimeClick,
                    onHistoricalSearchClick = onHistoricalSearchClick,
                    onManageHistoricalClick = onManageHistoricalClick,
                    onShowMain = {},
                    historicalMetaList = historicalMetaList,
                    currentHistoricalMetaId = 0L,
                    getDamConfig = { DamConfig.DEFAULT },
                    onSwitchToHistoricalMode = onSwitchToHistoricalMode,
                    isDailyHistoryEnabled = isDailyHistoryEnabled,
                    sudmonitorHistory = sudmonitorHistory,
                    isSudmonitorHistoryMode = isSudmonitorHistoryMode,
                    onSwitchToSudmonitorHistoryMode = onSwitchToSudmonitorHistoryMode,
                    lastFetchDateStr = "2026/05/17",
                    availableApps = emptyList(),
                    onDebugClick = onDebugClick,
                    onSettingsClick = onSettingsClick,
                    onAppInfoClick = {},
                    onOpenUrl = {},
                    onOpenUrlWith = { _, _ -> },
                    onShareText = onShareText,
                    onCopied = onCopied,
                    onQueryApps = {},
                    onClearApps = {},
                    onCollapseSidebarClick = onCollapseSidebarClick
                )
            }
        }
    }

    private fun dailyHistory(
        damId: String,
        nextUpdateAtEpochMs: Long = 0L
    ): SudmonitorHistory =
        SudmonitorHistory(
            damId = damId,
            periodStartEpochMs = jstDayStartMillis(2026, 7, 1),
            periodEndEpochMs = jstDayStartMillis(2026, 7, 31),
            status = SudmonitorHistory.STATUS_SUCCESS,
            rowCount = 2,
            firstStorageRatePct = 80f,
            lastStorageRatePct = 81f,
            minStorageRatePct = 79f,
            maxStorageRatePct = 82f,
            fetchedAtEpochMs = 1L,
            nextUpdateAtEpochMs = nextUpdateAtEpochMs,
            rawDatPath = null,
            updatedAtEpochMs = 1L
        )

    private fun jstDayStartMillis(year: Int, month: Int, day: Int): Long =
        LocalDate.of(year, month, day).atStartOfDay(TimeUtils.JST_ZONE).toInstant().toEpochMilli()

    private fun historicalSearchMeta(id: Long): HistoricalSearchMeta =
        HistoricalSearchMeta(
            id = id,
            observationStationId = DamConfig.DEFAULT.id,
            observationStationName = "早明浦ダム",
            riverSystemName = "吉野川",
            riverName = "吉野川",
            damConfigId = DamConfig.DEFAULT.id,
            searchBgnDate = "20260501",
            searchEndDate = "20260501",
            fetchedAt = 0L,
            dataStartTimeStr = "2026/05/01 01:00",
            dataEndTimeStr = "2026/05/02 00:00",
            dataStartStoragePct = 80f,
            dataEndStoragePct = 81f,
            dataMinStoragePct = 79f,
            dataMaxStoragePct = 82f
        )
}
