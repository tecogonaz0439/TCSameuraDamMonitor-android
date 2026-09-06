// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import net.tecogonaz.tcsameuradammonitor.R
import net.tecogonaz.tcsameuradammonitor.domain.model.AppSettings
import net.tecogonaz.tcsameuradammonitor.domain.model.DamHistoricalData
import net.tecogonaz.tcsameuradammonitor.domain.model.HistoricalComparisonData
import net.tecogonaz.tcsameuradammonitor.domain.model.HistoricalComparisonMetric
import net.tecogonaz.tcsameuradammonitor.util.TimeUtils
import java.time.Instant

enum class GraphType {
    STORAGE_PERCENTAGE,
    STORAGE_VOLUME,
    INFLOW,
    OUTFLOW
}

internal enum class RealtimeGraphRange(
    val hours: Long?,
    val labelResId: Int
) {
    ALL(null, R.string.graph_range_full_range),
    PAST_72_HOURS(72L, R.string.graph_range_past_72_hours),
    PAST_48_HOURS(48L, R.string.graph_range_past_48_hours),
    PAST_24_HOURS(24L, R.string.graph_range_past_24_hours)
}

enum class GraphDisplayType {
    RAINFALL_STORAGE,
    VOLUME_FLOW,
    RAINFALL_STORAGE_HISTORY,
    VOLUME_FLOW_HISTORY
}

/** 過去比較グラフ表示（history mode）であるかどうかを返す。 */
val GraphDisplayType.isHistoricalComparison: Boolean
    get() = this == GraphDisplayType.RAINFALL_STORAGE_HISTORY ||
        this == GraphDisplayType.VOLUME_FLOW_HISTORY

/** 過去比較グラフ表示における比較対象メトリックを返す。非history modeでは[HistoricalComparisonMetric.STORAGE_RATE]。 */
val GraphDisplayType.historyMetric: HistoricalComparisonMetric
    get() = when (this) {
        GraphDisplayType.RAINFALL_STORAGE_HISTORY -> HistoricalComparisonMetric.STORAGE_RATE
        GraphDisplayType.VOLUME_FLOW_HISTORY -> HistoricalComparisonMetric.STORAGE_VOLUME
        GraphDisplayType.RAINFALL_STORAGE, GraphDisplayType.VOLUME_FLOW -> HistoricalComparisonMetric.STORAGE_RATE
    }

/**
 * グラフのライン（表示/非表示を個別切替できる単位）を表す列挙型。
 *
 * 年系統のライン（過去年・今年）は [GraphLineSelectionState.selectedYears] が、
 * メトリック別のラインは [GraphLineSelectionState.visibleLines] が表示状態を管理する。
 */
enum class GraphLine {
    STORAGE_RATE,
    RAINFALL,
    STORAGE_VOLUME,
    INFLOW,
    OUTFLOW
}

/**
 * グラフのライン表示/非表示の選択状態。
 *
 * ライン切替チップ（年チップ・全対象年チップ・メトリックラインのチップ）の表示内容と
 * 選択状態、および選択変更コールバックをまとめてグラフCardへ渡す。
 * 選択状態は[ObservationGraphCard]のrememberスコープで保持され、Cardの展開・mode切替を
 * 越えて維持される。
 *
 * @property availableYears 年チップの対象年（過去年+今年）の昇順リスト。比較データ非ロード時は空
 * @property selectedYears 選択中の年の集合（全対象年の一括切替・年単位の切替で更新）
 * @property visibleLines 選択中のメトリックラインの集合
 * @property onYearToggle 年チップのトグルコールバック
 * @property onAllYearsToggle 全対象年チップのトグルコールバック
 * @property onLineToggle メトリックラインのチップのトグルコールバック
 */
data class GraphLineSelectionState(
    val availableYears: List<Int> = emptyList(),
    val selectedYears: Set<Int> = emptySet(),
    val visibleLines: Set<GraphLine> = GraphLine.entries.toSet(),
    val onYearToggle: (Int) -> Unit = {},
    val onAllYearsToggle: () -> Unit = {},
    val onLineToggle: (GraphLine) -> Unit = {}
)

/** メトリックラインの選択集合から1ラインをトグルする。 */
private fun toggleLineVisibility(visible: Set<GraphLine>, line: GraphLine): Set<GraphLine> =
    if (line in visible) visible - line else visible + line

/** グラフ種別(mode)チップ1つ分の表示定義。 */
private data class ModeChipSpec(
    val key: String,
    val label: String,
    val selected: Boolean,
    val onClick: () -> Unit
)

@Composable
fun ObservationGraphCard(
    historicalData: List<DamHistoricalData>,
    modifier: Modifier = Modifier,
    isExpanded: Boolean,
    onExpandToggle: () -> Unit,
    showJstSuffix: Boolean = false,
    isHistorical: Boolean = false,
    isDailyHistory: Boolean = false,
    rainfallUnitLabel: String = "(mm/10min)",
    historicalSearchStartMillis: Long? = null,
    historicalSearchEndMillis: Long? = null,
    damId: String? = null,
    comparisonStates: Map<HistoricalComparisonMetric, HistoricalComparisonMetricState> = emptyMap(),
    onHistoricalMetricSelected: ((HistoricalComparisonMetric, Long, Long, Int?) -> Unit)? = null
) {
    if (historicalData.isEmpty()) return

    // 日次過去データ（isDailyHistory）は既存の過去データ検索履歴（isHistorical）と同じ
    // 「履歴扱い」でグラフを表示する（期間表記 01:00〜00:00、範囲チップ非表示、全データ表示）。
    val graphIsHistorical = isHistorical || isDailyHistory

    var displayType by remember { mutableStateOf(GraphDisplayType.RAINFALL_STORAGE) }
    var realtimeRange by remember { mutableStateOf(RealtimeGraphRange.ALL) }
    var isCrosshairEnabled by remember { mutableStateOf(false) }
    // チップ行は全幅で1行の横スクロール列(EdgeFadeRow)で表示し、縦方向の折返しはしない。
    // modeチップのスクロール位置はCard開閉・mode切替を越えて維持する。期間チップの
    // スクロール位置はmode切替で維持し、Card開閉時は期間が全期間へ戻るため先頭へ戻す。
    val modeChipScrollState = remember { LazyListState() }
    val rangeScrollState = remember { LazyListState() }
    val realtimeRangeOptions = remember(historicalData, graphIsHistorical) {
        if (graphIsHistorical) {
            listOf(RealtimeGraphRange.ALL)
        } else {
            realtimeGraphRangeOptionsForData(historicalData)
        }
    }
    LaunchedEffect(isExpanded) {
        if (!isExpanded) {
            realtimeRange = RealtimeGraphRange.ALL
            isCrosshairEnabled = false
            rangeScrollState.scrollToItem(0)
        }
    }
    LaunchedEffect(realtimeRangeOptions, realtimeRange) {
        if (realtimeRange !in realtimeRangeOptions) {
            realtimeRange = RealtimeGraphRange.ALL
        }
    }

    val canShowHistoricalComparison = damId == AppSettings.DEFAULT_DAM_ID
    LaunchedEffect(canShowHistoricalComparison) {
        if (!canShowHistoricalComparison && displayType.isHistoricalComparison) {
            displayType = GraphDisplayType.RAINFALL_STORAGE
        }
    }

    val comparisonWindowMillis: Pair<Long, Long>? = remember(historicalData) {
        val times = historicalData.mapNotNull { parseGraphTimeMillis(it.time) }
        val minMillis = times.minOrNull()
        val maxMillis = times.maxOrNull()
        if (minMillis != null && maxMillis != null) minMillis to maxMillis else null
    }
    // 通常の過去データ表示（過去データ検索結果の閲覧）では、主系列年は表示対象データの年
    // （=表示窓開始ミリ秒のJST年）。それ以外（リアルタイム・過去データ(日次)表示）では
    // null（=JST現在年を主系列年とする従来挙動）。
    val comparisonMainYear: Int? = comparisonWindowMillis?.first?.let { startMillis ->
        if (isHistorical && !isDailyHistory) {
            Instant.ofEpochMilli(startMillis).atZone(TimeUtils.JST_ZONE).year
        } else {
            null
        }
    }
    val historyMetric = displayType.historyMetric
    val comparisonData = if (displayType.isHistoricalComparison) {
        comparisonStates[historyMetric]?.data
    } else {
        null
    }
    // 年チップの対象年は比較データの比較対象年（2002〜JST現在年のうち主系列年以外）に
    // 主系列年（comparisonData.currentYear。通常の過去データ表示では表示対象データの年、
    // それ以外ではJST現在年）を加えた昇順リスト。
    // 比較データ非ロード時は空のため、年チップは比較データのロード後に表示される。
    val availableYears = remember(comparisonData) {
        if (comparisonData == null) {
            emptyList()
        } else {
            (comparisonData.availablePastYears + comparisonData.currentYear).sorted()
        }
    }
    var selectedYears by remember(availableYears) { mutableStateOf(availableYears.toSet()) }
    // メトリックライン（貯水率・流域平均雨量・貯水量・流入量・放流量）の表示/非表示状態。
    // 初期状態は全ライン表示。選択状態はCardの開閉・mode切替を越えて保持し、
    // ダム変更・Activity再生成では remember ごと破棄されて全表示へ戻る。
    var visibleLines by remember { mutableStateOf(GraphLine.entries.toSet()) }

    // ライン切替チップ行は全幅で1行の横スクロール列(LazyRow)で表示する。
    // スクロール位置はmode切替とCardの開閉を越えて維持し、初期表示は先頭(左端、
    // 全対象年チップ側)である。ダム変更・Activity再生成では remember ごと破棄されて
    // 既存のリセット仕様(全選択へ戻る)と同時に先頭から表示し直す。
    val lineChipScrollState = remember(availableYears) { LazyListState() }
    val lineSelection = GraphLineSelectionState(
        availableYears = availableYears,
        selectedYears = selectedYears,
        visibleLines = visibleLines,
        onYearToggle = { year -> selectedYears = togglePastYearSelection(selectedYears, year) },
        onAllYearsToggle = { selectedYears = toggleAllPastYears(selectedYears, availableYears) },
        onLineToggle = { line -> visibleLines = toggleLineVisibility(visibleLines, line) }
    )

    val title = stringResource(R.string.main_graph_observation_title)
    val rainfallStorageLabel = stringResource(R.string.main_graph_rainfall_storage_select)
    val volumeFlowLabel = stringResource(R.string.main_graph_volume_flow_select)
    val rainfallStorageHistoryLabel = stringResource(R.string.main_graph_rainfall_storage_history_select)
    val volumeFlowHistoryLabel = stringResource(R.string.main_graph_volume_flow_history_select)
    val historicalComparisonLoadingLabel = stringResource(R.string.graph_historical_comparison_loading)
    val historicalComparisonErrorLabel = stringResource(R.string.graph_historical_comparison_error)
    val historicalComparisonRetryLabel = stringResource(R.string.graph_historical_comparison_retry)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag(TestTags.OBSERVATION_GRAPH_CARD),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { heading() }
                )
                IconButton(
                    onClick = onExpandToggle,
                    modifier = Modifier.testTag(TestTags.OBSERVATION_GRAPH_EXPAND_BUTTON)
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = if (isExpanded) stringResource(R.string.desc_collapse) else stringResource(R.string.desc_expand),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (isExpanded) {
                // modeチップは全幅で1行の横スクロール列([EdgeFadeRow])で表示する。
                // 各チップはLazyRowのitemとして1行横スクロール列に並べ、フリング停止時に
                // チップ先頭へ整列(スナップ)させる。
                val modeChipSpecs = buildList {
                    add(
                        ModeChipSpec(
                            key = "rainfall-storage",
                            label = rainfallStorageLabel,
                            selected = displayType == GraphDisplayType.RAINFALL_STORAGE,
                            onClick = {
                                if (displayType != GraphDisplayType.RAINFALL_STORAGE) {
                                    displayType = GraphDisplayType.RAINFALL_STORAGE
                                }
                            }
                        )
                    )
                    add(
                        ModeChipSpec(
                            key = "volume-flow",
                            label = volumeFlowLabel,
                            selected = displayType == GraphDisplayType.VOLUME_FLOW,
                            onClick = {
                                if (displayType != GraphDisplayType.VOLUME_FLOW) {
                                    displayType = GraphDisplayType.VOLUME_FLOW
                                }
                            }
                        )
                    )
                    if (canShowHistoricalComparison) {
                        add(
                            ModeChipSpec(
                                key = "rainfall-storage-history",
                                label = rainfallStorageHistoryLabel,
                                selected = displayType == GraphDisplayType.RAINFALL_STORAGE_HISTORY,
                                onClick = {
                                    if (displayType != GraphDisplayType.RAINFALL_STORAGE_HISTORY) {
                                        displayType = GraphDisplayType.RAINFALL_STORAGE_HISTORY
                                        comparisonWindowMillis?.let { (start, end) ->
                                            onHistoricalMetricSelected?.invoke(
                                                HistoricalComparisonMetric.STORAGE_RATE,
                                                start,
                                                end,
                                                comparisonMainYear
                                            )
                                        }
                                    }
                                }
                            )
                        )
                        add(
                            ModeChipSpec(
                                key = "volume-flow-history",
                                label = volumeFlowHistoryLabel,
                                selected = displayType == GraphDisplayType.VOLUME_FLOW_HISTORY,
                                onClick = {
                                    if (displayType != GraphDisplayType.VOLUME_FLOW_HISTORY) {
                                        displayType = GraphDisplayType.VOLUME_FLOW_HISTORY
                                        comparisonWindowMillis?.let { (start, end) ->
                                            onHistoricalMetricSelected?.invoke(
                                                HistoricalComparisonMetric.STORAGE_VOLUME,
                                                start,
                                                end,
                                                comparisonMainYear
                                            )
                                        }
                                    }
                                }
                            )
                        )
                    }
                }
                // mode行は全幅で1行の横スクロール列([EdgeFadeRow])で表示し、折返しはしない。
                // mode行と次の行(グラフ本体)は、グラフCard内の期間チップ行と
                // 「タップ/ドラッグで値を表示する」行の間隔(0dp)と同じく直付けにする。
                EdgeFadeRow(
                    lazyListState = modeChipScrollState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(TestTags.OBSERVATION_GRAPH_MODE_SCROLL_ROW),
                    fadeColor = CardDefaults.cardColors().containerColor
                ) {
                    modeChipSpecs.forEach { spec ->
                        item(key = spec.key) {
                            GraphTypeToggleChip(
                                label = spec.label,
                                selected = spec.selected,
                                onClick = spec.onClick
                            )
                        }
                    }
                }

                Box {
                    when (displayType) {
                        GraphDisplayType.RAINFALL_STORAGE,
                        GraphDisplayType.RAINFALL_STORAGE_HISTORY -> {
                            DamGraphCard(
                                historicalData = historicalData,
                                graphType = GraphType.STORAGE_PERCENTAGE,
                                title = if (displayType.isHistoricalComparison) {
                                    rainfallStorageHistoryLabel
                                } else {
                                    rainfallStorageLabel
                                },
                                showJstSuffix = showJstSuffix,
                                isHistorical = graphIsHistorical,
                                rainfallUnitLabel = rainfallUnitLabel,
                                historicalSearchStartMillis = historicalSearchStartMillis,
                                historicalSearchEndMillis = historicalSearchEndMillis,
                                embedded = true,
                                embeddedRealtimeRange = realtimeRange,
                                embeddedRealtimeRangeOptions = realtimeRangeOptions,
                                embeddedIsCrosshairEnabled = isCrosshairEnabled,
                                embeddedRealtimeRangeScrollState = rangeScrollState,
                                onRealtimeRangeChange = { realtimeRange = it },
                                onCrosshairEnabledChange = { isCrosshairEnabled = it },
                                comparisonData = if (displayType.isHistoricalComparison) comparisonData else null,
                                lineSelection = lineSelection,
                                lineChipScrollState = lineChipScrollState,
                                isComparisonMode = displayType.isHistoricalComparison
                            )
                        }
                        GraphDisplayType.VOLUME_FLOW,
                        GraphDisplayType.VOLUME_FLOW_HISTORY -> {
                            DamVolumeFlowGraphCard(
                                historicalData = historicalData,
                                title = if (displayType.isHistoricalComparison) {
                                    volumeFlowHistoryLabel
                                } else {
                                    volumeFlowLabel
                                },
                                showJstSuffix = showJstSuffix,
                                isHistorical = graphIsHistorical,
                                historicalSearchStartMillis = historicalSearchStartMillis,
                                historicalSearchEndMillis = historicalSearchEndMillis,
                                embedded = true,
                                embeddedRealtimeRange = realtimeRange,
                                embeddedRealtimeRangeOptions = realtimeRangeOptions,
                                embeddedIsCrosshairEnabled = isCrosshairEnabled,
                                embeddedRealtimeRangeScrollState = rangeScrollState,
                                onRealtimeRangeChange = { realtimeRange = it },
                                onCrosshairEnabledChange = { isCrosshairEnabled = it },
                                comparisonData = if (displayType.isHistoricalComparison) comparisonData else null,
                                lineSelection = lineSelection,
                                lineChipScrollState = lineChipScrollState,
                                isComparisonMode = displayType.isHistoricalComparison
                            )
                        }
                    }

                    if (displayType.isHistoricalComparison) {
                        when (comparisonStates[historyMetric]?.loadState) {
                            HistoricalComparisonLoadState.LOADING -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                                        .semantics { liveRegion = LiveRegionMode.Polite },
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    CircularProgressIndicator()
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(text = historicalComparisonLoadingLabel)
                                }
                            }
                            HistoricalComparisonLoadState.ERROR -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(text = historicalComparisonErrorLabel)
                                    TextButton(
                                        onClick = {
                                            comparisonWindowMillis?.let { (start, end) ->
                                                onHistoricalMetricSelected?.invoke(
                                                    historyMetric,
                                                    start,
                                                    end,
                                                    comparisonMainYear
                                                )
                                            }
                                        }
                                    ) {
                                        Text(text = historicalComparisonRetryLabel)
                                    }
                                }
                            }
                            HistoricalComparisonLoadState.IDLE,
                            HistoricalComparisonLoadState.READY,
                            null -> Unit
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
