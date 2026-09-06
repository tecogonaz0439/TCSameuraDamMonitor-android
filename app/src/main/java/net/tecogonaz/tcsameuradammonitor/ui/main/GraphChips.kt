// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.tecogonaz.tcsameuradammonitor.R

private val GRAPH_CONTROL_VISUAL_CHIP_GAP = 8.dp
internal const val WIDE_GRAPH_CONTROL_MIN_WIDTH_DP = 840

internal val REALTIME_GRAPH_RANGE_OPTIONS = listOf(
    RealtimeGraphRange.ALL,
    RealtimeGraphRange.PAST_72_HOURS,
    RealtimeGraphRange.PAST_48_HOURS,
    RealtimeGraphRange.PAST_24_HOURS
)

@Composable
internal fun RangeFilterChip(
    range: RealtimeGraphRange,
    selected: Boolean,
    onRangeClick: (RealtimeGraphRange) -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = {
            if (!selected) {
                onRangeClick(range)
            }
        },
        label = {
            Text(
                text = stringResource(range.labelResId),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false
            )
        },
        leadingIcon = if (selected) {
            {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    modifier = Modifier.size(FilterChipDefaults.IconSize)
                )
            }
        } else null
    )
}

@Composable
internal fun CrosshairFilterChip(
    isCrosshairEnabled: Boolean,
    onCrosshairClick: () -> Unit
) {
    FilterChip(
        selected = isCrosshairEnabled,
        onClick = onCrosshairClick,
        label = {
            Text(
                text = stringResource(R.string.crosshair_enable_label),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false
            )
        },
        leadingIcon = if (isCrosshairEnabled) {
            {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    modifier = Modifier.size(FilterChipDefaults.IconSize)
                )
            }
        } else null
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GraphControlRow(
    showRangeSelector: Boolean,
    rangeOptions: List<RealtimeGraphRange> = REALTIME_GRAPH_RANGE_OPTIONS,
    range: RealtimeGraphRange,
    onRangeClick: (RealtimeGraphRange) -> Unit,
    isCrosshairEnabled: Boolean,
    onCrosshairClick: () -> Unit,
    rangeScrollState: LazyListState? = null
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val isWideLayout = maxWidth.value >= WIDE_GRAPH_CONTROL_MIN_WIDTH_DP

        if (showRangeSelector && isWideLayout) {
            // タブレット・デスクトップ表示(840dp以上)では、期間チップと
            // 「タップ/ドラッグで値を表示する」チップを1行の横スクロール列へ
            // まとめて表示する(1行に収まらない場合は横スクロール)。
            EdgeFadeRow(
                lazyListState = rangeScrollState ?: remember { LazyListState() },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TestTags.OBSERVATION_GRAPH_RANGE_SCROLL_ROW),
                fadeColor = CardDefaults.cardColors().containerColor
            ) {
                rangeOptions.forEach { selectableRange ->
                    item(key = selectableRange.name) {
                        RangeFilterChip(
                            range = selectableRange,
                            selected = selectableRange == range,
                            onRangeClick = onRangeClick
                        )
                    }
                }
                item(key = "crosshair") {
                    CrosshairFilterChip(
                        isCrosshairEnabled = isCrosshairEnabled,
                        onCrosshairClick = onCrosshairClick
                    )
                }
            }
        } else if (showRangeSelector) {
            // スマホ表示(compact)では期間チップを1行の横スクロール列で表示する。
            // 「タップ/ドラッグで値を表示する」チップはスクロール列の外に別行で固定表示する。
            Column(modifier = Modifier.fillMaxWidth()) {
                EdgeFadeRow(
                    lazyListState = rangeScrollState ?: remember { LazyListState() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(TestTags.OBSERVATION_GRAPH_RANGE_SCROLL_ROW),
                    fadeColor = CardDefaults.cardColors().containerColor
                ) {
                    rangeOptions.forEach { selectableRange ->
                        item(key = selectableRange.name) {
                            RangeFilterChip(
                                range = selectableRange,
                                selected = selectableRange == range,
                                onRangeClick = onRangeClick
                            )
                        }
                    }
                }
                CrosshairFilterChip(
                    isCrosshairEnabled = isCrosshairEnabled,
                    onCrosshairClick = onCrosshairClick
                )
            }
        } else {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(GRAPH_CONTROL_VISUAL_CHIP_GAP),
                maxLines = 4
            ) {
                CrosshairFilterChip(
                    isCrosshairEnabled = isCrosshairEnabled,
                    onCrosshairClick = onCrosshairClick
                )
            }
        }
    }
}

@Composable
internal fun GraphTypeToggleChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false
            )
        },
        leadingIcon = if (selected) {
            {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    modifier = Modifier.size(FilterChipDefaults.IconSize)
                )
            }
        } else null
    )
}

/**
 * グラフのライン表示/非表示を切り替えるチップ1つ分の表示定義。
 *
 * @property key チップ列のLazyRow item key
 * @property label チップの表示文言
 * @property selected 選択（表示）状態
 * @property testTag UIテスト用のタグ
 * @property onClick チップタップ時のコールバック
 */
internal data class GraphLineChipSpec(
    val key: String,
    val label: String,
    val selected: Boolean,
    val testTag: String,
    val onClick: () -> Unit
)

/** [GraphLine] のチップ表示定義を作る。タグは [TestTags.OBSERVATION_GRAPH_LINE_CHIP_PREFIX] にライン名を連結する。 */
internal fun graphLineChipSpec(
    line: GraphLine,
    label: String,
    visibleLines: Set<GraphLine>,
    onLineToggle: (GraphLine) -> Unit
): GraphLineChipSpec = GraphLineChipSpec(
    key = line.name,
    label = label,
    selected = line in visibleLines,
    testTag = TestTags.OBSERVATION_GRAPH_LINE_CHIP_PREFIX + line.name.lowercase(),
    onClick = { onLineToggle(line) }
)

/**
 * グラフキャンバス直下・期間チップの上に常時表示するライン切替チップ行。
 *
 * 全幅でチップ列を1行の横スクロール列([EdgeFadeRow])で表示し、縦方向の折返しはしない。
 * スクロール位置は呼び出し側が保持する [scrollState] に委ねる。
 */
@Composable
internal fun GraphLineChipRow(
    chips: List<GraphLineChipSpec>,
    scrollState: LazyListState,
    modifier: Modifier = Modifier
) {
    EdgeFadeRow(
        lazyListState = scrollState,
        modifier = modifier
            .fillMaxWidth()
            .testTag(TestTags.OBSERVATION_GRAPH_YEAR_SCROLL_ROW),
        fadeColor = CardDefaults.cardColors().containerColor
    ) {
        chips.forEach { spec ->
            item(key = spec.key) {
                GraphTypeToggleChip(
                    label = spec.label,
                    selected = spec.selected,
                    onClick = spec.onClick,
                    modifier = Modifier.testTag(spec.testTag)
                )
            }
        }
    }
}

@Composable
internal fun FullWidthGraphActionChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilterChip(
        selected = false,
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        label = {
            Text(
                text = label,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false
            )
        }
    )
}
