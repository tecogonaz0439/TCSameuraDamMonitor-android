// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.ui.main

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.compose.ui.platform.testTag
import net.tecogonaz.tcsameuradammonitor.R
import net.tecogonaz.tcsameuradammonitor.domain.model.DamHistoricalData
import net.tecogonaz.tcsameuradammonitor.ui.theme.TCSameuraDamMonitorTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * リアルタイムグラフカード（[ObservationGraphCard]）の UI とアクセシビリティセマンティクスを検証する UI テストクラス。
 * グラフの種類（「貯水率／流域平均雨量」と「貯水量／流入量／放流量」）や表示期間（過去24時間など）のチップ切り替えに
 * 伴う選択状態の制御、文字列表示（数値行）の行順、スクリーンリーダー対応としてのアクセシビリティ用の
 * コンテンツ説明文（ContentDescription）、グラフ上の点データを順次読み上げるカスタムアクション、および
 * データが全欠損している場合の表示上の堅牢性を検証します。
 */
class ObservationGraphCardAndroidTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun graphTypeAndRangeChipsSwitchVisibleSelection() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        setGraph(data = graphRows(includeMissingRecentRange = false))

        val rainfallStorage = context.getString(R.string.main_graph_rainfall_storage_select)
        val volumeFlow = context.getString(R.string.main_graph_volume_flow_select)
        val past24 = context.getString(R.string.graph_range_past_24_hours)

        composeRule.onNodeWithText(rainfallStorage).assertIsSelected()
        clickModeChip(volumeFlow)
        composeRule.onNodeWithText(volumeFlow).assertIsSelected()

        clickRangeChip(past24)
        composeRule.onNodeWithText(past24).assertIsSelected()
    }

    @Test
    fun graphSemanticsExposeSummaryCustomActionsAndStateDescription() {
        setGraph(data = graphRows(includeMissingRecentRange = false))

        val graphNode = composeRule.waitForGraphSemanticsNode()
        val contentDescription = graphNode.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
        assertTrue(contentDescription.joinToString().isNotBlank())
        val actions = graphNode.config.getOrNull(SemanticsActions.CustomActions).orEmpty()
        assertTrue(actions.isNotEmpty())

        composeRule.runOnIdle {
            assertTrue(actions.first().action.invoke())
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.waitForGraphSemanticsNode()
                .config
                .getOrNull(SemanticsProperties.StateDescription) != null
        }
        assertNotNull(
            composeRule.waitForGraphSemanticsNode()
                .config
                .getOrNull(SemanticsProperties.StateDescription)
        )
    }

    @Test
    fun allMissingSelectedRealtimeRangeStillKeepsGraphUiReachable() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        setGraph(data = graphRows(includeMissingRecentRange = true))

        clickRangeChip(context.getString(R.string.graph_range_past_24_hours))

        composeRule.onNodeWithText(context.getString(R.string.graph_range_past_24_hours)).assertIsSelected()
        assertTrue(composeRule.waitForGraphSemanticsNode().config.getOrNull(SemanticsActions.CustomActions).orEmpty().isNotEmpty())
    }

    @Test
    fun realtimeRangeIgnoresRetainedHistoricalPeriodForBothGraphTypes() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val staleStart = requireNotNull(parseGraphTimeMillis("2025/01/01 00:00"))
        val staleEnd = requireNotNull(parseGraphTimeMillis("2025/01/02 00:00"))
        setGraph(
            data = graphRows(includeMissingRecentRange = false),
            historicalSearchStartMillis = staleStart,
            historicalSearchEndMillis = staleEnd
        )

        clickRangeChip(context.getString(R.string.graph_range_past_24_hours))

        val expectedPeriod =
            "${context.getString(R.string.graph_label_period)} 2026/05/02 06:00 - 2026/05/03 06:00"
        composeRule.onNodeWithText(expectedPeriod, substring = true).assertIsDisplayed()
        assertTrue(
            composeRule.onAllNodesWithText("2025/01/01", substring = true)
                .fetchSemanticsNodes()
                .isEmpty()
        )

        clickModeChip(context.getString(R.string.main_graph_volume_flow_select))

        composeRule.onNodeWithText(expectedPeriod, substring = true).assertIsDisplayed()
        val graphNode = composeRule.waitForGraphSemanticsNode()
        val actions = graphNode.config.getOrNull(SemanticsActions.CustomActions).orEmpty()
        assertTrue(actions.isNotEmpty())
        composeRule.runOnIdle {
            assertTrue(actions.first().action.invoke())
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.waitForGraphSemanticsNode()
                .config
                .getOrNull(SemanticsProperties.StateDescription)
                ?.contains("2026/05/02") == true
        }
    }

    @Test
    fun storageVolumeGraphUsesLocalizedExponentUnitInAccessibilityTooltip() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        composeRule.setContent {
            TCSameuraDamMonitorTheme(dynamicColor = false) {
                DamGraphCard(
                    historicalData = graphRows(includeMissingRecentRange = false),
                    graphType = GraphType.STORAGE_VOLUME,
                    title = context.getString(R.string.main_storage_volume)
                )
            }
        }

        val actions = composeRule.waitForGraphSemanticsNode()
            .config
            .getOrNull(SemanticsActions.CustomActions)
            .orEmpty()
        composeRule.runOnIdle {
            assertTrue(actions.first().action.invoke())
        }
        val storageVolumeUnit = context.getString(R.string.graph_unit_storage_volume)
            .removePrefix("(")
            .removeSuffix(")")
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.waitForGraphSemanticsNode()
                .config
                .getOrNull(SemanticsProperties.StateDescription)
                ?.contains(storageVolumeUnit) == true
        }
        val stateDescription = composeRule.waitForGraphSemanticsNode()
            .config
            .getOrNull(SemanticsProperties.StateDescription)
            .orEmpty()
        assertTrue(!stateDescription.contains("千m³"))
    }

    @Test
    fun collapsedStateHidesGraphContentAndExpandButtonDelegatesToggle() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val graphTypeLabel = context.getString(R.string.main_graph_rainfall_storage_select)
        composeRule.setContent {
            TCSameuraDamMonitorTheme(dynamicColor = false) {
                var expanded by remember { mutableStateOf(false) }
                ObservationGraphCard(
                    historicalData = graphRows(includeMissingRecentRange = false),
                    isExpanded = expanded,
                    onExpandToggle = { expanded = !expanded }
                )
            }
        }

        composeRule.onNodeWithText(graphTypeLabel).assertDoesNotExist()
        composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_EXPAND_BUTTON).performClick()
        composeRule.onNodeWithText(graphTypeLabel).assertIsDisplayed()
    }

    @Test
    fun graphSummaryLines_followSpecifiedRowOrder() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        setGraph(data = graphRows(includeMissingRecentRange = false))

        val labelPeriod = context.getString(R.string.graph_label_period)
        val labelStorageRateMax = context.getString(R.string.graph_label_storage_rate_max)
        val labelStorageRateMin = context.getString(R.string.graph_label_storage_rate_min)
        val labelRainfallTotal = context.getString(R.string.graph_label_rainfall_total)
        val labelRainfallMax = context.getString(R.string.graph_label_rainfall_max)

        // 文字列表示(数値行)の行順は仕様で固定:
        // 期間 → 貯水率 最大 → 貯水率 最小 → 流域平均雨量(合計) → 流域平均雨量 最大。
        val summaryNodes = composeRule.onAllNodesWithText(labelPeriod, substring = true)
            .fetchSemanticsNodes()
        assertEquals(1, summaryNodes.size)
        val summaryText = summaryNodes.first()
            .config
            .getOrNull(SemanticsProperties.Text)
            .orEmpty()
            .joinToString()
        val lines = summaryText.split("\n").filter { it.isNotBlank() }
        assertEquals(5, lines.size)
        assertTrue("1行目は期間であること", lines[0].startsWith(labelPeriod))
        assertTrue("2行目は貯水率 最大であること", lines[1].startsWith(labelStorageRateMax))
        assertTrue("3行目は貯水率 最小であること", lines[2].startsWith(labelStorageRateMin))
        assertTrue("4行目は流域平均雨量(合計)であること", lines[3].startsWith(labelRainfallTotal))
        assertTrue("5行目は流域平均雨量 最大であること", lines[4].startsWith(labelRainfallMax))
    }

    @Test
    fun graphCardCanvasVisibleAndRangeControlsReachableInFiniteViewport() {
        val viewportHeight = 640.dp
        val viewportTag = "observation_graph_viewport"
        composeRule.setContent {
            TCSameuraDamMonitorTheme(dynamicColor = false) {
                Box(
                    modifier = Modifier
                        .requiredWidth(900.dp)
                        .height(viewportHeight)
                        .testTag(viewportTag)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        ObservationGraphCard(
                            historicalData = graphRows(includeMissingRecentRange = false),
                            isExpanded = true,
                            onExpandToggle = {}
                        )
                    }
                }
            }
        }

        val graphNode = composeRule.waitForGraphSemanticsNode()
        assertTrue(
            "グラフCanvasが描画可能な高さを持つこと",
            graphNode.boundsInRoot.height > 0f
        )

        composeRule.onNodeWithTag(TestTags.OBSERVATION_GRAPH_RANGE_SCROLL_ROW)
            .performScrollTo()
            .assertIsDisplayed()
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

    private fun setGraph(
        data: List<DamHistoricalData>,
        historicalSearchStartMillis: Long? = null,
        historicalSearchEndMillis: Long? = null
    ) {        composeRule.setContent {
            TCSameuraDamMonitorTheme(dynamicColor = false) {
                ObservationGraphCard(
                    historicalData = data,
                    isExpanded = true,
                    onExpandToggle = {},
                    historicalSearchStartMillis = historicalSearchStartMillis,
                    historicalSearchEndMillis = historicalSearchEndMillis
                )
            }
        }
        val context = ApplicationProvider.getApplicationContext<Context>()
        composeRule.onNodeWithText(context.getString(R.string.main_graph_observation_title)).assertIsDisplayed()
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.waitForGraphSemanticsNode() =
        waitUntilNode(timeoutMillis = 5_000) {
            it.config.getOrNull(SemanticsActions.CustomActions).orEmpty().isNotEmpty()
        }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.waitUntilNode(
        timeoutMillis: Long,
        predicate: (androidx.compose.ui.semantics.SemanticsNode) -> Boolean
    ): androidx.compose.ui.semantics.SemanticsNode {
        val matcher = SemanticsMatcher("graph node predicate", predicate)
        waitUntil(timeoutMillis = timeoutMillis) {
            onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        return onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().first()
    }

    private fun graphRows(includeMissingRecentRange: Boolean): List<DamHistoricalData> =
        listOf(
            DamHistoricalData("2026/05/01 00:00", 0.1f, 100f, 10f, 9f, 60f),
            DamHistoricalData("2026/05/02 06:00", 0.2f, 102f, 12f, 10f, 62f),
            DamHistoricalData(
                time = "2026/05/03 06:00",
                catchmentAverageRainfall = 0.3f,
                storageVolume = if (includeMissingRecentRange) null else 103f,
                inflow = if (includeMissingRecentRange) null else 13f,
                outflow = if (includeMissingRecentRange) null else 11f,
                storagePercentage = if (includeMissingRecentRange) null else 63f
            )
        )
}
