// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.ui.main

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import net.tecogonaz.tcsameuradammonitor.R
import net.tecogonaz.tcsameuradammonitor.domain.model.AppSettings
import net.tecogonaz.tcsameuradammonitor.domain.model.DamHistoricalData
import net.tecogonaz.tcsameuradammonitor.domain.model.HistoricalComparisonData
import net.tecogonaz.tcsameuradammonitor.domain.model.HistoricalComparisonMetric
import net.tecogonaz.tcsameuradammonitor.domain.model.HistoricalComparisonSeries
import net.tecogonaz.tcsameuradammonitor.ui.theme.TCSameuraDamMonitorTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.abs

/**
 * 過去比較グラフ（[ObservationGraphCard] の history mode）の UI テストクラス。
 *
 * 過去比較は早明浦ダム（[AppSettings.DEFAULT_DAM_ID]）のリアルタイム表示限定機能であるため、
 * 選択ダムや表示モードに応じたモードチップの出し分け、過去年+今年の選択チップ（全対象年/年別）
 * とメトリックラインのチップ（mode別構成・mode間・Card開閉を越えた選択保持）、
 * ライン非表示時の描画条件、読み込み中/エラー時のオーバーレイ表示を検証します。
 */
class HistoricalComparisonGraphAndroidTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun sameuraRealtime_showsFourModeChipsWithDefaultFirst() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        setGraph(
            damId = AppSettings.DEFAULT_DAM_ID,
            comparisonStates = mapOf(HistoricalComparisonMetric.STORAGE_RATE to readyState())
        )

        val rainfallStorage = context.getString(R.string.main_graph_rainfall_storage_select)
        val volumeFlow = context.getString(R.string.main_graph_volume_flow_select)
        val rainfallStorageHistory =
            context.getString(R.string.main_graph_rainfall_storage_history_select)
        val volumeFlowHistory = context.getString(R.string.main_graph_volume_flow_history_select)

        composeRule.onNodeWithText(rainfallStorage).assertIsSelected()
        composeRule.onNodeWithText(volumeFlow).assertIsNotSelected()
        // 3番目以降のchipはLazyRowの仮想化で未composeのため、スクロールで到達してから検証する。
        scrollModeRowTo(rainfallStorageHistory)
        composeRule.onNodeWithText(rainfallStorageHistory).assertIsNotSelected()
        scrollModeRowTo(volumeFlowHistory)
        composeRule.onNodeWithText(volumeFlowHistory).assertIsNotSelected()
    }

    @Test
    fun nonSameuraRealtime_showsOnlyTwoModeChips() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        setGraph(damId = "1234567890123")

        composeRule.onNodeWithText(context.getString(R.string.main_graph_rainfall_storage_select))
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.main_graph_volume_flow_select))
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.main_graph_rainfall_storage_history_select))
            .assertDoesNotExist()
        composeRule.onNodeWithText(context.getString(R.string.main_graph_volume_flow_history_select))
            .assertDoesNotExist()
    }

    @Test
    fun historicalMode_sameuraShowsFourModeChips() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        setGraph(isHistorical = true)

        val rainfallStorage = context.getString(R.string.main_graph_rainfall_storage_select)
        val volumeFlow = context.getString(R.string.main_graph_volume_flow_select)
        val rainfallStorageHistory =
            context.getString(R.string.main_graph_rainfall_storage_history_select)
        val volumeFlowHistory = context.getString(R.string.main_graph_volume_flow_history_select)

        // 過去データ表示(検索結果の閲覧)でも早明浦ダムなら4modeチップが表示される。
        composeRule.onNodeWithText(rainfallStorage).assertIsSelected()
        composeRule.onNodeWithText(volumeFlow).assertIsNotSelected()
        // 3番目以降のchipはLazyRowの仮想化で未composeのため、スクロールで到達してから検証する。
        scrollModeRowTo(rainfallStorageHistory)
        composeRule.onNodeWithText(rainfallStorageHistory).assertIsNotSelected()
        scrollModeRowTo(volumeFlowHistory)
        composeRule.onNodeWithText(volumeFlowHistory).assertIsNotSelected()
    }

    @Test
    fun historicalMode_yearChipsIncludeMainYearAndMainYearChipToggleControlsMainLine() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        setGraph(
            isHistorical = true,
            comparisonStates = mapOf(HistoricalComparisonMetric.STORAGE_RATE to readyState())
        )

        // 履歴modeへ切替後、年チップ(主系列年チップ=2026を含む)が初期全選択で表示される。
        clickModeChip(context.getString(R.string.main_graph_rainfall_storage_history_select))
        composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_YEAR_ALL_CHIP).assertIsSelected()
        scrollToYearChip(2026)
        composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_YEAR_CHIP_PREFIX + 2026)
            .assertIsSelected()

        // 主系列年チップの解除で主系列年が全対象年から外れ、「全て」チップも未選択になる
        // (通常の過去データ表示でも主系列線の表示/非表示は主系列年チップで制御する)。
        composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_YEAR_CHIP_PREFIX + 2026).performClick()
        composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_YEAR_CHIP_PREFIX + 2026)
            .assertIsNotSelected()
        scrollToYearAllChip()
        composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_YEAR_ALL_CHIP).assertIsNotSelected()

        // 主系列年チップを選択し直すと全選択へ復帰する。
        scrollToYearChip(2026)
        composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_YEAR_CHIP_PREFIX + 2026).performClick()
        composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_YEAR_CHIP_PREFIX + 2026)
            .assertIsSelected()
        scrollToYearAllChip()
        composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_YEAR_ALL_CHIP).assertIsSelected()
    }

    @Test
    fun yearChips_initialAllSelectedAndSharedAcrossHistoryModes() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        setGraph(
            comparisonStates = mapOf(
                HistoricalComparisonMetric.STORAGE_RATE to readyState(),
                HistoricalComparisonMetric.STORAGE_VOLUME to readyState()
            )
        )

        clickModeChip(context.getString(R.string.main_graph_rainfall_storage_history_select))
        assertAllYearChipsSelected()

        clickModeChip(context.getString(R.string.main_graph_volume_flow_history_select))
        assertAllYearChipsSelected()
    }

    @Test
    fun yearChipToggle_allTransitionStates() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        setGraph(
            comparisonStates = mapOf(HistoricalComparisonMetric.STORAGE_RATE to readyState())
        )
        clickModeChip(context.getString(R.string.main_graph_rainfall_storage_history_select))

        composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_YEAR_ALL_CHIP).assertIsSelected()
        assertGraphNodeDisplayed()

        scrollToYearChip(2024)
        composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_YEAR_CHIP_PREFIX + 2024).performClick()
        composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_YEAR_CHIP_PREFIX + 2024)
            .assertIsNotSelected()
        scrollToYearAllChip()
        composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_YEAR_ALL_CHIP).assertIsNotSelected()
        assertGraphNodeDisplayed()

        scrollToYearChip(2024)
        composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_YEAR_CHIP_PREFIX + 2024).performClick()
        scrollToYearAllChip()
        composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_YEAR_ALL_CHIP).assertIsSelected()
        assertGraphNodeDisplayed()

        scrollToYearAllChip()
        composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_YEAR_ALL_CHIP).performClick()
        composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_YEAR_ALL_CHIP).assertIsNotSelected()
        (2002..2026).forEach { year ->
            scrollToYearChip(year)
            composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_YEAR_CHIP_PREFIX + year)
                .assertIsNotSelected()
        }
        assertGraphNodeDisplayed()

        scrollToYearAllChip()
        composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_YEAR_ALL_CHIP).performClick()
        composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_YEAR_ALL_CHIP).assertIsSelected()
        (2002..2026).forEach { year ->
            scrollToYearChip(year)
            composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_YEAR_CHIP_PREFIX + year)
                .assertIsSelected()
        }
        assertGraphNodeDisplayed()
    }

    @Test
    fun loadingState_showsOverlayText() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        setGraph(
            comparisonStates = mapOf(
                HistoricalComparisonMetric.STORAGE_RATE to HistoricalComparisonMetricState(
                    loadState = HistoricalComparisonLoadState.LOADING
                )
            )
        )
        clickModeChip(context.getString(R.string.main_graph_rainfall_storage_history_select))

        composeRule.onNodeWithText(context.getString(R.string.graph_historical_comparison_loading))
            .assertIsDisplayed()
    }

    @Test
    fun errorState_showsErrorAndRetryInvokesCallback() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var recordedMetric by mutableStateOf<HistoricalComparisonMetric?>(null)
        setGraph(
            comparisonStates = mapOf(
                HistoricalComparisonMetric.STORAGE_RATE to HistoricalComparisonMetricState(
                    loadState = HistoricalComparisonLoadState.ERROR
                )
            ),
            onHistoricalMetricSelected = { metric, _, _, _, _ -> recordedMetric = metric }
        )
        clickModeChip(context.getString(R.string.main_graph_rainfall_storage_history_select))
        composeRule.runOnIdle { recordedMetric = null }

        composeRule.onNodeWithText(context.getString(R.string.graph_historical_comparison_error))
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.graph_historical_comparison_retry))
            .performClick()
        composeRule.runOnIdle {
            assertEquals(HistoricalComparisonMetric.STORAGE_RATE, recordedMetric)
        }
    }

    @Test
    fun collapseKeepsYearSelection() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        setGraph(
            comparisonStates = mapOf(HistoricalComparisonMetric.STORAGE_RATE to readyState())
        )
        clickModeChip(context.getString(R.string.main_graph_rainfall_storage_history_select))
        scrollToYearChip(2024)
        composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_YEAR_CHIP_PREFIX + 2024).performClick()
        composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_YEAR_CHIP_PREFIX + 2024)
            .assertIsNotSelected()

        composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_EXPAND_BUTTON).performClick()
        composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_YEAR_CHIP_PREFIX + 2024)
            .assertDoesNotExist()

        composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_EXPAND_BUTTON).performClick()
        scrollToYearChip(2024)
        composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_YEAR_CHIP_PREFIX + 2024)
            .assertIsNotSelected()
        scrollToYearAllChip()
        composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_YEAR_ALL_CHIP).assertIsNotSelected()
    }

    @Test
    fun compactWidth_yearChipsSingleLineAllScrollsInitialAtStart() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        setGraph(
            comparisonStates = mapOf(HistoricalComparisonMetric.STORAGE_RATE to readyState()),
            cardWidth = 390.dp
        )
        clickModeChip(context.getString(R.string.main_graph_rainfall_storage_history_select))

        // スマホ表示(840dp未満)では「全て」チップ(貯水率全て)も他の年chipと一緒に1行の
        // 横スクロール列(LazyRow)で表示される。
        composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_YEAR_ALL_CHIP).assertIsDisplayed()
        composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_YEAR_SCROLL_ROW).assertIsDisplayed()

        // 初期表示は先頭(左端)。仮想化のため画面外の直近の年chipは未compose。
        composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_YEAR_CHIP_PREFIX + 2002)
            .assertIsDisplayed()
        composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_YEAR_CHIP_PREFIX + 2026)
            .assertDoesNotExist()

        // スクロールで直近の年へ到達でき、スクロール後は「全て」チップも
        // 左へスクロールアウトする(左端固定ではない)。
        scrollToYearChip(2026)
        composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_YEAR_CHIP_PREFIX + 2026)
            .assertIsDisplayed()
        composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_YEAR_ALL_CHIP).assertDoesNotExist()
    }

    @Test
    fun compactWidth_yearRowFadeHints_followScrollPosition() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        setGraph(
            comparisonStates = mapOf(HistoricalComparisonMetric.STORAGE_RATE to readyState()),
            cardWidth = 390.dp
        )
        clickModeChip(context.getString(R.string.main_graph_rainfall_storage_history_select))

        // 初期(先頭)表示では右方向へスクロール余地があるため、左端フェードは非表示・右端は表示。
        assertEquals(
            RowFadeHintState(startVisible = false, endVisible = true),
            rowFadeHintsOf(TestTags.OBSERVATION_GRAPH_YEAR_SCROLL_ROW)
        )
        // 右端(最後のライン切替チップ)へスクロールすると、フェードの表示が左右反転する。
        composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_YEAR_SCROLL_ROW)
            .performScrollToNode(hasTestTag(TestTags.OBSERVATION_GRAPH_LINE_CHIP_PREFIX + "rainfall"))
        assertEquals(
            RowFadeHintState(startVisible = true, endVisible = false),
            rowFadeHintsOf(TestTags.OBSERVATION_GRAPH_YEAR_SCROLL_ROW)
        )
    }

    @Test
    fun lineChips_shownInAllModes_withModeSpecificAllChip() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        setGraph(
            comparisonStates = mapOf(
                HistoricalComparisonMetric.STORAGE_RATE to readyState(),
                HistoricalComparisonMetric.STORAGE_VOLUME to readyState()
            ),
            cardWidth = 900.dp
        )

        // mode 1(初期表示): メトリックラインのチップのみ全選択で表示される。
        composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_LINE_CHIP_PREFIX + "storage_rate")
            .assertExists()
        composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_LINE_CHIP_PREFIX + "rainfall")
            .assertExists()
        composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_YEAR_ALL_CHIP).assertDoesNotExist()

        // mode 2: 「貯水量」+「流入量」+「放流量」のみ。「全て」チップと年チップは表示しない。
        clickModeChip(context.getString(R.string.main_graph_volume_flow_select))
        composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_LINE_CHIP_PREFIX + "storage_volume")
            .assertExists()
        composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_LINE_CHIP_PREFIX + "inflow")
            .assertExists()
        composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_LINE_CHIP_PREFIX + "outflow")
            .assertExists()
        composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_LINE_CHIP_PREFIX + "storage_rate")
            .assertDoesNotExist()
        composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_LINE_CHIP_PREFIX + "rainfall")
            .assertDoesNotExist()
        composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_YEAR_ALL_CHIP).assertDoesNotExist()

        // mode 3: 「貯水率全て」+ 年チップ(今年を含む)+ 流域平均雨量のみ。
        // 「貯水率」チップはmode 3では表示しない(主線は今年チップの選択に従う)。
        // 「貯水量全て」はmode 3では表示しない。
        clickModeChip(context.getString(R.string.main_graph_rainfall_storage_history_select))
        // チップ列は1行横スクロールのため、対象チップまでスクロールしてから検証する。
        scrollToYearAllChip()
        composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_YEAR_ALL_CHIP).assertIsSelected()
        composeRule.onNodeWithText(context.getString(R.string.graph_line_chip_all_storage_rate))
            .assertExists()
        composeRule.onAllNodesWithText(context.getString(R.string.graph_line_chip_all_storage_rate))
            .assertCountEquals(1)
        composeRule.onNodeWithText(context.getString(R.string.graph_line_chip_all_storage_volume))
            .assertDoesNotExist()
        composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_LINE_CHIP_PREFIX + "storage_rate")
            .assertDoesNotExist()
        scrollToYearChip(2026)
        composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_YEAR_CHIP_PREFIX + 2026).assertIsSelected()
        scrollLineRowTo("rainfall")
        composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_LINE_CHIP_PREFIX + "rainfall")
            .assertExists()

        // mode 4: 「貯水量全て」+ 年チップ(今年を含む)+ 流入量/放流量のみ。
        // 「貯水量」チップはmode 4では表示しない(主線は今年チップの選択に従う)。
        // 「貯水率全て」はmode 4では表示しない。
        clickModeChip(context.getString(R.string.main_graph_volume_flow_history_select))
        // mode 3と同値の年リストのためスクロール位置は末尾に残る。先頭へ戻してから検証する。
        scrollToYearAllChip()
        composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_YEAR_ALL_CHIP).assertIsSelected()
        composeRule.onNodeWithText(context.getString(R.string.graph_line_chip_all_storage_volume))
            .assertExists()
        composeRule.onAllNodesWithText(context.getString(R.string.graph_line_chip_all_storage_volume))
            .assertCountEquals(1)
        composeRule.onNodeWithText(context.getString(R.string.graph_line_chip_all_storage_rate))
            .assertDoesNotExist()
        composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_LINE_CHIP_PREFIX + "storage_volume")
            .assertDoesNotExist()
        scrollToYearChip(2026)
        composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_YEAR_CHIP_PREFIX + 2026).assertIsSelected()
        scrollLineRowTo("inflow")
        composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_LINE_CHIP_PREFIX + "inflow")
            .assertExists()
        composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_LINE_CHIP_PREFIX + "outflow")
            .assertExists()
    }

    @Test
    fun lineChipToggle_keptAcrossModes() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        setGraph(
            comparisonStates = mapOf(
                HistoricalComparisonMetric.STORAGE_RATE to readyState(),
                HistoricalComparisonMetric.STORAGE_VOLUME to readyState()
            ),
            cardWidth = 900.dp
        )

        // mode 1で流域平均雨量チップを解除する。
        composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_LINE_CHIP_PREFIX + "rainfall")
            .performClick()
        composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_LINE_CHIP_PREFIX + "rainfall")
            .assertIsNotSelected()

        // mode 3へ切り替えてもメトリックラインの選択状態は保持される。
        // ただし「貯水率」チップは比較モードでは表示されない(主線は今年チップの選択に従う)。
        clickModeChip(context.getString(R.string.main_graph_rainfall_storage_history_select))
        scrollLineRowTo("rainfall")
        composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_LINE_CHIP_PREFIX + "rainfall")
            .assertIsNotSelected()
        composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_LINE_CHIP_PREFIX + "storage_rate")
            .assertDoesNotExist()

        // 年系統は解除していないため「貯水率全て」は全選択のまま(末尾スクロール後は先頭へ戻す)。
        scrollToYearAllChip()
        composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_YEAR_ALL_CHIP).assertIsSelected()
    }

    @Test
    fun currentYearChip_toggleAffectsAllYearsSelection() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        setGraph(
            comparisonStates = mapOf(HistoricalComparisonMetric.STORAGE_RATE to readyState()),
            cardWidth = 390.dp
        )
        clickModeChip(context.getString(R.string.main_graph_rainfall_storage_history_select))

        // 今年チップの解除で全対象年(2002〜今年)から外れ、「全て」チップも未選択になる。
        scrollToYearChip(2026)
        composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_YEAR_CHIP_PREFIX + 2026).performClick()
        composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_YEAR_CHIP_PREFIX + 2026)
            .assertIsNotSelected()
        scrollToYearAllChip()
        composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_YEAR_ALL_CHIP).assertIsNotSelected()

        // 今年チップを選択し直すと全選択へ復帰する。
        scrollToYearChip(2026)
        composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_YEAR_CHIP_PREFIX + 2026).performClick()
        composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_YEAR_CHIP_PREFIX + 2026)
            .assertIsSelected()
        scrollToYearAllChip()
        composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_YEAR_ALL_CHIP).assertIsSelected()
    }

    @Test
    fun lineVisibility_hiddenYearsSkipsHistoricalLineDrawing() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        setGraph(
            comparisonStates = mapOf(HistoricalComparisonMetric.STORAGE_RATE to readyState()),
            cardWidth = 390.dp
        )
        clickModeChip(context.getString(R.string.main_graph_rainfall_storage_history_select))

        // 初期状態(全対象年選択)では年系統の線の描画条件が成立する。
        assertHistoricalLinesDrawn(true)

        // 全対象年チップで全解除すると、年系統の線は描画条件を満たさなくなる
        // (ライン表示/非表示は描画のみに影響し、スケール・目盛りは不変)。
        scrollToYearAllChip()
        composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_YEAR_ALL_CHIP).performClick()
        composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_YEAR_ALL_CHIP).assertIsNotSelected()
        assertHistoricalLinesDrawn(false)

        // 全選択へ戻すと再び描画条件が成立する。
        scrollToYearAllChip()
        composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_YEAR_ALL_CHIP).performClick()
        composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_YEAR_ALL_CHIP).assertIsSelected()
        assertHistoricalLinesDrawn(true)
    }

    @Test
    fun lineSelection_keptAcrossHistoryModesAndCardCollapse() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        setGraph(
            comparisonStates = mapOf(
                HistoricalComparisonMetric.STORAGE_RATE to readyState(),
                HistoricalComparisonMetric.STORAGE_VOLUME to readyState()
            ),
            cardWidth = 390.dp
        )

        // mode 3で年チップ(2024年)とメトリックラインのチップ(流域平均雨量)を個別に解除する。
        clickModeChip(context.getString(R.string.main_graph_rainfall_storage_history_select))
        scrollToYearChip(2024)
        composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_YEAR_CHIP_PREFIX + 2024).performClick()
        composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_YEAR_CHIP_PREFIX + 2024)
            .assertIsNotSelected()
        scrollLineRowTo("rainfall")
        composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_LINE_CHIP_PREFIX + "rainfall").performClick()
        composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_LINE_CHIP_PREFIX + "rainfall")
            .assertIsNotSelected()

        // mode 4へ切り替えても年チップの解除状態は保持される(年系統はmode 3/4で共有)。
        clickModeChip(context.getString(R.string.main_graph_volume_flow_history_select))
        scrollToYearChip(2024)
        composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_YEAR_CHIP_PREFIX + 2024)
            .assertIsNotSelected()

        // mode 3へ戻るとメトリックラインの解除状態も保持されている。
        clickModeChip(context.getString(R.string.main_graph_rainfall_storage_history_select))
        scrollLineRowTo("rainfall")
        composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_LINE_CHIP_PREFIX + "rainfall")
            .assertIsNotSelected()

        // Cardの開閉を越えて年チップ・メトリックラインの選択状態が保持される。
        composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_EXPAND_BUTTON).performClick()
        composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_EXPAND_BUTTON).performClick()
        scrollLineRowTo("rainfall")
        composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_LINE_CHIP_PREFIX + "rainfall")
            .assertIsNotSelected()
        scrollToYearChip(2024)
        composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_YEAR_CHIP_PREFIX + 2024)
            .assertIsNotSelected()
        scrollToYearAllChip()
        composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_YEAR_ALL_CHIP).assertIsNotSelected()
    }

    @Test
    fun compactWidth_allYearChipAlignedToRowStart() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        setGraph(
            comparisonStates = mapOf(HistoricalComparisonMetric.STORAGE_RATE to readyState()),
            cardWidth = 390.dp
        )
        clickModeChip(context.getString(R.string.main_graph_rainfall_storage_history_select))

        // 初期表示は先頭で、「全て」チップ(貯水率全て)が年行の左端に揃って表示される。
        val allChipX = composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_YEAR_ALL_CHIP)
            .fetchSemanticsNode().positionInRoot.x
        val rowX = composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_YEAR_SCROLL_ROW)
            .fetchSemanticsNode().positionInRoot.x
        assertTrue(
            "「全て」チップが年行の左端に揃って初期表示されること",
            abs(allChipX - rowX) < 2f
        )
    }

    @Test
    fun compactWidth_modeRowFadeHints_initialStartNotVisible() {
        setGraph()

        // mode行は右方向へスクロール余地があるため、初期状態では左端フェードなし・右端フェードあり。
        assertEquals(
            RowFadeHintState(startVisible = false, endVisible = true),
            rowFadeHintsOf(TestTags.OBSERVATION_GRAPH_MODE_SCROLL_ROW)
        )
    }

    @Test
    fun compactWidth_yearRowFling_snapsToChipStart() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        setGraph(
            comparisonStates = mapOf(HistoricalComparisonMetric.STORAGE_RATE to readyState()),
            cardWidth = 390.dp
        )
        clickModeChip(context.getString(R.string.main_graph_rainfall_storage_history_select))

        // フリングで直近の年方向へスクロールさせ、停止後にチップ先頭が行の先頭へ
        // 整列(スナップ)することを確認する。
        composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_YEAR_SCROLL_ROW)
            .performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
        val (firstVisibleIndex, firstVisibleScrollOffset) =
            rowScrollPositionOf(TestTags.OBSERVATION_GRAPH_YEAR_SCROLL_ROW)
        assertTrue("フリングでスクロールが発生すること", firstVisibleIndex > 0)
        assertEquals(
            "フリング停止後はチップ先頭が行の先頭へ整列すること",
            0,
            firstVisibleScrollOffset
        )
    }

    @Test
    fun compactWidth_chipRowPosition_belowCanvasAboveRangeRow() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        setGraph(
            comparisonStates = mapOf(HistoricalComparisonMetric.STORAGE_RATE to readyState()),
            cardWidth = 390.dp
        )

        // ライン切替チップ行(年チップを含む)はグラフキャンバスの直下・期間チップ(クロスヘア)の上に表示される。
        clickModeChip(context.getString(R.string.main_graph_rainfall_storage_history_select))
        val graphNode = composeRule.waitForGraphSemanticsNode()
        val yearChip = composeRule.onNodeWithTag(
            TestTags.OBSERVATION_GRAPH_YEAR_CHIP_PREFIX + 2002
        ).fetchSemanticsNode()
        val crosshair = composeRule.onNodeWithText(
            context.getString(R.string.crosshair_enable_label)
        ).fetchSemanticsNode()
        assertTrue(
            "チップ行がグラフキャンバスの直下にあること",
            yearChip.positionInRoot.y >= graphNode.positionInRoot.y + graphNode.size.height
        )
        assertTrue(
            "チップ行が期間チップ(クロスヘア)の上にあること",
            yearChip.positionInRoot.y < crosshair.positionInRoot.y
        )
    }

    @Test
    fun wideWidth_yearChipsSingleLineScrollable() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        setGraph(
            comparisonStates = mapOf(HistoricalComparisonMetric.STORAGE_RATE to readyState()),
            cardWidth = 900.dp
        )
        clickModeChip(context.getString(R.string.main_graph_rainfall_storage_history_select))

        // タブレット・デスクトップ相当(840dp以上)でも、年チップ列は1行の横スクロール列
        // (LazyRow)で表示し、折返しはしない。
        // (900dp幅のCardはテスト端末の画面より広く、端のチップは画面外へクリップされる
        // ため、表示位置ではなく横スクロールで到達できることを確認する)
        composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_YEAR_ALL_CHIP).assertExists()
        composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_YEAR_SCROLL_ROW).assertExists()
        // 1行に収まらない年チップ(2026年)へ横スクロールで到達できる。
        composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_YEAR_SCROLL_ROW)
            .performScrollToNode(hasTestTag(TestTags.OBSERVATION_GRAPH_YEAR_CHIP_PREFIX + 2026))
        composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_YEAR_CHIP_PREFIX + 2026).assertExists()
    }

    @Test
    fun yearScrollPosition_keptAcrossHistoryModes() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        setGraph(
            comparisonStates = mapOf(
                HistoricalComparisonMetric.STORAGE_RATE to readyState(),
                HistoricalComparisonMetric.STORAGE_VOLUME to readyState()
            ),
            cardWidth = 390.dp
        )
        clickModeChip(context.getString(R.string.main_graph_rainfall_storage_history_select))
        scrollToYearChip(2002)
        composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_YEAR_CHIP_PREFIX + 2002)
            .assertIsDisplayed()

        // mode 4(貯水量)へ切り替えてもスクロール位置は維持される(初回先頭初期化は再実行しない)。
        clickModeChip(context.getString(R.string.main_graph_volume_flow_history_select))
        composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_YEAR_CHIP_PREFIX + 2002)
            .assertIsDisplayed()
    }

    @Test
    fun yearScrollPosition_keptAfterCollapse() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        setGraph(
            comparisonStates = mapOf(HistoricalComparisonMetric.STORAGE_RATE to readyState()),
            cardWidth = 390.dp
        )
        clickModeChip(context.getString(R.string.main_graph_rainfall_storage_history_select))
        scrollToYearChip(2002)
        composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_YEAR_CHIP_PREFIX + 2002)
            .assertIsDisplayed()

        composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_EXPAND_BUTTON).performClick()
        composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_YEAR_CHIP_PREFIX + 2002)
            .assertDoesNotExist()

        // 再展開後もスクロール位置は維持され、2002年が再び表示される。
        composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_EXPAND_BUTTON).performClick()
        composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_YEAR_CHIP_PREFIX + 2002)
            .assertIsDisplayed()
    }

    @Test
    fun rangeSelectedInRealtimeThenSwitchToRainfallStorageHistory_drawsHistoricalLines() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        setGraph(
            comparisonStates = mapOf(
                HistoricalComparisonMetric.STORAGE_RATE to readyState()
            )
        )

        clickRangeChip(context.getString(R.string.graph_range_past_24_hours))
        clickModeChip(context.getString(R.string.main_graph_rainfall_storage_history_select))

        val graphNode = composeRule.waitForGraphSemanticsNode()
        assertTrue(
            "過去比較グラフ表示への切替後は過去年の線の描画条件が成立すること",
            graphNode.config.getOrNull(HistoricalComparisonLinesDrawn) == true
        )
    }

    @Test
    fun rangeSelectedInRealtimeVolumeFlowThenSwitchToVolumeFlowHistory_drawsHistoricalLines() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        setGraph(
            comparisonStates = mapOf(
                HistoricalComparisonMetric.STORAGE_VOLUME to readyState()
            )
        )

        clickModeChip(context.getString(R.string.main_graph_volume_flow_select))
        clickRangeChip(context.getString(R.string.graph_range_past_24_hours))
        clickModeChip(context.getString(R.string.main_graph_volume_flow_history_select))

        val graphNode = composeRule.waitForGraphSemanticsNode()
        assertTrue(
            "貯水量の過去比較グラフ表示への切替後は過去年の線の描画条件が成立すること",
            graphNode.config.getOrNull(HistoricalComparisonLinesDrawn) == true
        )
    }

    @Test
    fun rangeSelectedInRealtimeWithoutHistoryMode_doesNotDrawHistoricalLines() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        setGraph(
            comparisonStates = mapOf(
                HistoricalComparisonMetric.STORAGE_RATE to readyState()
            )
        )

        clickRangeChip(context.getString(R.string.graph_range_past_24_hours))

        val graphNode = composeRule.waitForGraphSemanticsNode()
        assertTrue(
            "リアルタイム表示では過去年の線の描画条件が成立しないこと",
            graphNode.config.getOrNull(HistoricalComparisonLinesDrawn) == false
        )
    }

    @Test
    fun compactWidth_modeChipsSingleLineScrollable() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        setGraph(
            comparisonStates = mapOf(HistoricalComparisonMetric.STORAGE_RATE to readyState())
        )
        val volumeFlowHistory = context.getString(R.string.main_graph_volume_flow_history_select)

        // スマホ表示(840dp未満)ではモードチップが1行の横スクロール列になる。
        composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_MODE_SCROLL_ROW).assertIsDisplayed()
        // 初期状態では端のチップはスクロールなしで画面外。
        composeRule.onNodeWithText(volumeFlowHistory).assertIsNotDisplayed()
        // スクロールで端のチップへ到達し、選択操作できる。
        composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_MODE_SCROLL_ROW)
            .performScrollToNode(hasText(volumeFlowHistory))
        composeRule.onNodeWithText(volumeFlowHistory).assertIsDisplayed()
        composeRule.onNodeWithText(volumeFlowHistory).performClick()
        composeRule.onNodeWithText(volumeFlowHistory).assertIsSelected()
    }

    @Test
    fun wideWidth_modeChipsSingleLineScrollable() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        setGraph(
            comparisonStates = mapOf(HistoricalComparisonMetric.STORAGE_RATE to readyState()),
            cardWidth = 900.dp
        )
        val volumeFlowHistory = context.getString(R.string.main_graph_volume_flow_history_select)

        // タブレット・デスクトップ相当(840dp以上)でも、modeチップは1行の横スクロール列
        // (LazyRow)で表示し、折返しはしない。
        composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_MODE_SCROLL_ROW).assertExists()
        // 端のmodeチップへ横スクロールで到達できる。
        composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_MODE_SCROLL_ROW)
            .performScrollToNode(hasText(volumeFlowHistory))
        composeRule.onNodeWithText(volumeFlowHistory).assertExists()
    }

    @Test
    fun compactWidth_rangeChipsSingleLineScrollable() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // 期間チップ4つが1行に収まらないことが確実な幅で検証する。
        setGraph(cardWidth = 200.dp)
        val past24 = context.getString(R.string.graph_range_past_24_hours)

        composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_RANGE_SCROLL_ROW).assertIsDisplayed()
        // 初期状態では端の期間チップはスクロールなしで画面外。
        composeRule.onNodeWithText(past24).assertIsNotDisplayed()
        // 「タップ/ドラッグで値を表示する」チップはスクロール列の外に表示されたまま。
        composeRule.onNodeWithText(context.getString(R.string.crosshair_enable_label))
            .assertIsDisplayed()
        // スクロールで端の期間チップへ到達し、選択操作できる。
        composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_RANGE_SCROLL_ROW)
            .performScrollToNode(hasText(past24))
        composeRule.onNodeWithText(past24).assertIsDisplayed()
        composeRule.onNodeWithText(past24).performClick()
        composeRule.onNodeWithText(past24).assertIsSelected()
    }

    @Test
    fun wideWidth_rangeChipsSingleLineScrollable() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        setGraph(cardWidth = 900.dp)
        val past24 = context.getString(R.string.graph_range_past_24_hours)

        // タブレット・デスクトップ相当(840dp以上)でも、期間チップと
        // 「タップ/ドラッグで値を表示する」チップは1行の横スクロール列で表示する。
        composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_RANGE_SCROLL_ROW).assertExists()
        composeRule.onNodeWithText(past24).assertExists()
        composeRule.onNodeWithText(context.getString(R.string.crosshair_enable_label))
            .assertExists()
    }

    @Test
    fun modeScrollPosition_keptAfterCollapse() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        setGraph(
            comparisonStates = mapOf(
                HistoricalComparisonMetric.STORAGE_RATE to readyState(),
                HistoricalComparisonMetric.STORAGE_VOLUME to readyState()
            )
        )
        val volumeFlowHistory = context.getString(R.string.main_graph_volume_flow_history_select)
        clickModeChip(volumeFlowHistory)
        composeRule.onNodeWithText(volumeFlowHistory).assertIsDisplayed()

        // Card開閉を越えてモードチップのスクロール位置は維持される。
        composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_EXPAND_BUTTON).performClick()
        composeRule.onNodeWithText(volumeFlowHistory).assertDoesNotExist()
        composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_EXPAND_BUTTON).performClick()
        composeRule.onNodeWithText(volumeFlowHistory).assertIsDisplayed()
    }

    @Test
    fun rangeScrollPosition_keptAcrossGraphTypes() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        setGraph(cardWidth = 200.dp)
        val past24 = context.getString(R.string.graph_range_past_24_hours)
        val volumeFlow = context.getString(R.string.main_graph_volume_flow_select)
        clickRangeChip(past24)

        // グラフ種別を切り替えても期間チップのスクロール位置は維持される。
        clickModeChip(volumeFlow)
        composeRule.onNodeWithText(past24).assertIsDisplayed()
    }

    @Test
    fun rangeScrollPosition_resetAfterCollapse() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        setGraph(cardWidth = 200.dp)
        val past24 = context.getString(R.string.graph_range_past_24_hours)
        val fullRange = context.getString(R.string.graph_range_full_range)
        clickRangeChip(past24)
        composeRule.onNodeWithText(past24).assertIsDisplayed()
        // スクロール後は右端に寄っており、先頭のチップは画面外。
        composeRule.onNodeWithText(fullRange).assertIsNotDisplayed()

        // Card開閉時は期間が全期間へ戻るため、期間チップのスクロール位置も先頭へ戻る。
        composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_EXPAND_BUTTON).performClick()
        composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_EXPAND_BUTTON).performClick()
        composeRule.onNodeWithText(fullRange).assertIsDisplayed()
    }

    private fun setGraph(
        damId: String? = AppSettings.DEFAULT_DAM_ID,
        isHistorical: Boolean = false,
        comparisonStates: Map<HistoricalComparisonMetric, HistoricalComparisonMetricState> =
            emptyMap(),
        onHistoricalMetricSelected: ((HistoricalComparisonMetric, Long, Long, Int?, Boolean) -> Unit)? = null,
        cardWidth: Dp = 390.dp
    ) {
        composeRule.setContent {
            TCSameuraDamMonitorTheme(dynamicColor = false) {
                var expanded by remember { mutableStateOf(true) }
                // requiredWidth で親制約(エミュレータの画面幅)を無視して Card幅を
                // 強制し、compact/wide の出し分けを決定的に検証する。
                Box(modifier = Modifier.requiredWidth(cardWidth)) {
                    ObservationGraphCard(
                        historicalData = graphRows(),
                        isExpanded = expanded,
                        onExpandToggle = { expanded = !expanded },
                        isHistorical = isHistorical,
                        damId = damId,
                        comparisonStates = comparisonStates,
                        onHistoricalMetricSelected = onHistoricalMetricSelected
                    )
                }
            }
        }
        val context = ApplicationProvider.getApplicationContext<Context>()
        composeRule.onNodeWithText(context.getString(R.string.main_graph_observation_title))
            .assertIsDisplayed()
    }

    private fun readyState(): HistoricalComparisonMetricState =
        HistoricalComparisonMetricState(
            loadState = HistoricalComparisonLoadState.READY,
            data = historicalComparisonFixture(),
            windowStartMillis = 0L,
            windowEndMillis = 0L
        )

    private fun historicalComparisonFixture(): HistoricalComparisonData {
        val axisMillis = listOf(jstMillis(0), jstMillis(1), jstMillis(2))
        val series = (2002..2025).map { year ->
            val value = when (year % 3) {
                0 -> 10.0f
                1 -> 10.5f
                else -> 11.0f
            }
            HistoricalComparisonSeries(
                year = year,
                values = if (year == 2002) {
                    listOf(null, null, null)
                } else {
                    listOf(value, value, value)
                }
            )
        }
        return HistoricalComparisonData(
            currentYear = 2026,
            availablePastYears = (2002..2025).toList(),
            periodStartMillis = axisMillis.first(),
            periodEndMillis = axisMillis.last(),
            hourlyAxisMillis = axisMillis,
            series = series
        )
    }

    private fun jstMillis(hour: Int): Long =
        LocalDateTime.of(2026, 6, 28, hour, 0)
            .atZone(ZoneId.of("Asia/Tokyo"))
            .toInstant()
            .toEpochMilli()

    private fun assertAllYearChipsSelected() {
        // 「全て」チップはLazyRowの先頭itemで、前回の検証ループ後はスクロール
        // 位置が末尾に残っているため、先頭へ戻してから検証する。
        composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_YEAR_SCROLL_ROW)
            .performScrollToIndex(0)
        composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_YEAR_ALL_CHIP).assertIsSelected()
        (2002..2026).forEach { year ->
            scrollToYearChip(year)
            composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_YEAR_CHIP_PREFIX + year)
                .assertIsSelected()
        }
    }

    private fun scrollToYearChip(year: Int) {
        composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_YEAR_SCROLL_ROW)
            .performScrollToNode(hasTestTag(TestTags.OBSERVATION_GRAPH_YEAR_CHIP_PREFIX + year))
    }

    // 「全て」チップもLazyRow内の先頭itemのため、スクロール後は左端から
    // スクロールアウトして未composeになる。操作前に対象までスクロールし直す。
    private fun scrollToYearAllChip() {
        composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_YEAR_SCROLL_ROW)
            .performScrollToIndex(0)
    }

    private fun scrollLineRowTo(line: String) {
        composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_YEAR_SCROLL_ROW)
            .performScrollToNode(hasTestTag(TestTags.OBSERVATION_GRAPH_LINE_CHIP_PREFIX + line))
    }

    // 年系統の線の描画条件(HistoricalComparisonLinesDrawn)を検証する。
    // CanvasのピクセルはUIテストから直接検証できないため、描画条件と同値の
    // セマンティクスプロパティを参照する。
    private fun assertHistoricalLinesDrawn(expected: Boolean) {
        val graphNode = composeRule.waitForGraphSemanticsNode()
        assertTrue(
            if (expected) "年系統の線の描画条件が成立すること" else "年系統の線の描画条件が成立しないこと",
            graphNode.config.getOrNull(HistoricalComparisonLinesDrawn) == expected
        )
    }

    // EdgeFadeRow のBox(スクロール行の親ノード)から左右エッジフェードの可視状態を取得する。
    private fun rowFadeHintsOf(rowTag: String): RowFadeHintState {
        val matcher = hasAnyDescendant(hasTestTag(rowTag)) and
            SemanticsMatcher("row fade hints node") {
                it.config.getOrNull(RowFadeHints) != null
            }
        val node = composeRule.onNode(matcher, useUnmergedTree = true).fetchSemanticsNode()
        return requireNotNull(node.config.getOrNull(RowFadeHints))
    }

    // EdgeFadeRow のBox(スクロール行の親ノード)からスクロール位置
    // (firstVisibleItemIndex, firstVisibleItemScrollOffset)を取得する。
    private fun rowScrollPositionOf(rowTag: String): Pair<Int, Int> {
        val matcher = hasAnyDescendant(hasTestTag(rowTag)) and
            SemanticsMatcher("row scroll position node") {
                it.config.getOrNull(RowScrollPosition) != null
            }
        val node = composeRule.onNode(matcher, useUnmergedTree = true).fetchSemanticsNode()
        return requireNotNull(node.config.getOrNull(RowScrollPosition))
    }

    // モードチップへクリックする。スマホ表示(compact)では1行横スクロール列に
    // なるため、スクロール列が存在する場合は対象チップまでスクロールしてから
    // クリックする(タブレット・デスクトップ表示ではスクロール列が無い)。
    private fun clickModeChip(label: String) {
        if (composeRule.onAllNodesWithTag(TestTags.OBSERVATION_GRAPH_MODE_SCROLL_ROW)
            .fetchSemanticsNodes().isNotEmpty()
        ) {
            composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_MODE_SCROLL_ROW)
                .performScrollToNode(hasText(label))
        }
        composeRule.onNodeWithText(label).performClick()
    }

    // 期間チップへクリックする。clickModeChip と同じく、compact表示では
    // スクロール列が存在するため対象チップまでスクロールしてからクリックする。
    private fun clickRangeChip(label: String) {
        if (composeRule.onAllNodesWithTag(TestTags.OBSERVATION_GRAPH_RANGE_SCROLL_ROW)
            .fetchSemanticsNodes().isNotEmpty()
        ) {
            composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_RANGE_SCROLL_ROW)
                .performScrollToNode(hasText(label))
        }
        composeRule.onNodeWithText(label).performClick()
    }

    private fun scrollModeRowTo(text: String) {
        // LazyRowの仮想化で画面外のchipは未composeのため、テキスト照合前に到達させる。
        composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_MODE_SCROLL_ROW)
            .performScrollToNode(hasText(text))
    }

    private fun assertGraphNodeDisplayed() {
        val graphNode = composeRule.waitForGraphSemanticsNode()
        assertTrue(
            graphNode.config.getOrNull(SemanticsActions.CustomActions).orEmpty().isNotEmpty()
        )
    }

    private fun ComposeContentTestRule.waitForGraphSemanticsNode() =
        waitUntilNode(timeoutMillis = 5_000) {
            it.config.getOrNull(SemanticsActions.CustomActions).orEmpty().isNotEmpty()
        }

    private fun ComposeContentTestRule.waitUntilNode(
        timeoutMillis: Long,
        predicate: (androidx.compose.ui.semantics.SemanticsNode) -> Boolean
    ): androidx.compose.ui.semantics.SemanticsNode {
        val matcher = SemanticsMatcher("graph node predicate", predicate)
        waitUntil(timeoutMillis = timeoutMillis) {
            onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        return onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().first()
    }

    private fun graphRows(): List<DamHistoricalData> =
        listOf(
            DamHistoricalData("2026/05/01 00:00", 0.1f, 100f, 10f, 9f, 60f),
            DamHistoricalData("2026/05/02 06:00", 0.2f, 102f, 12f, 10f, 62f),
            DamHistoricalData(
                time = "2026/05/03 06:00",
                catchmentAverageRainfall = 0.3f,
                storageVolume = 103f,
                inflow = 13f,
                outflow = 11f,
                storagePercentage = 63f
            )
        )
}
