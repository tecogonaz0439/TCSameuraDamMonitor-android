// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.ui.main

import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver

/**
 * 過去比較グラフ表示（history mode）で比較年系統の線（過去年+今年）が描画条件を満たしていることを表すセマンティクスプロパティ。
 * CanvasのピクセルはUIテストから直接検証できないため、描画条件と同じ値をここへ公開して
 * Compose UIテストとアクセシビリティ支援から参照できるようにする。
 */
val HistoricalComparisonLinesDrawn = SemanticsPropertyKey<Boolean>("HistoricalComparisonLinesDrawn")

/** [HistoricalComparisonLinesDrawn] への代入用レシーバ。 */
var SemanticsPropertyReceiver.historicalComparisonLinesDrawn by HistoricalComparisonLinesDrawn

/**
 * チップ行([EdgeFadeRow])の左右エッジフェードの可視状態。
 * [EdgeFadeRow] はスクロール余地がある側へフェードを描画するため、この状態は
 * スクロール位置に応じて左右反転する(先頭側フェードは canScrollBackward、末尾側は
 * canScrollForward と一致)。
 */
data class RowFadeHintState(
    val startVisible: Boolean,
    val endVisible: Boolean
)

/** チップ行([EdgeFadeRow])の左右エッジフェード可視状態を表すセマンティクスプロパティ。 */
val RowFadeHints = SemanticsPropertyKey<RowFadeHintState>("RowFadeHints")

/** [RowFadeHints] への代入用レシーバ。 */
var SemanticsPropertyReceiver.rowFadeHints by RowFadeHints

/**
 * チップ行([EdgeFadeRow])のスクロール位置(firstVisibleItemIndex, firstVisibleItemScrollOffset)。
 * スクロールのスナップ検証などUIテストから参照する。
 */
val RowScrollPosition = SemanticsPropertyKey<Pair<Int, Int>>("RowScrollPosition")

/** [RowScrollPosition] への代入用レシーバ。 */
var SemanticsPropertyReceiver.rowScrollPosition by RowScrollPosition

object TestTags {
    const val MAIN_ROOT = "main_root"
    const val MAIN_SCROLL_CONTAINER = "main_scroll_container"
    const val DRAWER_ROOT = "drawer_root"
    const val PERMANENT_ROOT = "permanent_root"
    const val SIDEBAR_EXPAND_BUTTON = "sidebar_expand_button"
    const val SIDEBAR_COLLAPSE_BUTTON = "sidebar_collapse_button"
    const val SUMMARY_CARD = "main_summary_card"
    const val SUMMARY_STATUS_TEXT = "main_summary_status_text"
    const val HISTORICAL_SUMMARY_CARD = "main_historical_summary_card"
    const val OBSERVATION_CARD = "main_observation_card"
    const val OBSERVATION_EXPAND_BUTTON = "main_observation_expand_button"
    const val LATEST_CARD = "main_latest_card"
    const val LATEST_EXPAND_BUTTON = "main_latest_expand_button"
    const val HISTORY_CARD = "main_history_card"
    const val HISTORY_EXPAND_BUTTON = "main_history_expand_button"
    const val OBSERVATION_GRAPH_CARD = "main_observation_graph_card"
    const val OBSERVATION_GRAPH_EXPAND_BUTTON = "main_observation_graph_expand_button"
    const val OBSERVATION_GRAPH_YEAR_ALL_CHIP = "observation_graph_year_all_chip"
    const val OBSERVATION_GRAPH_YEAR_CHIP_PREFIX = "observation_graph_year_chip_"
    const val OBSERVATION_GRAPH_YEAR_SCROLL_ROW = "observation_graph_year_scroll_row"
    const val OBSERVATION_GRAPH_LINE_CHIP_PREFIX = "observation_graph_line_chip_"
    const val OBSERVATION_GRAPH_MODE_SCROLL_ROW = "observation_graph_mode_scroll_row"
    const val OBSERVATION_GRAPH_RANGE_SCROLL_ROW = "observation_graph_range_scroll_row"
    const val LINKS_CARD = "main_links_card"
    const val LINKS_EXPAND_BUTTON = "main_links_expand_button"
    const val MANUAL_UPDATE_BUTTON = "main_manual_update_button"
    const val AUTO_UPDATE_BUTTON = "main_auto_update_button"
    const val SNACKBAR_HOST = "main_snackbar_host"
    const val OBSERVATION_HISTORY_LIST_ROOT = "observation_history_list_root"
    const val OBSERVATION_HISTORY_LIST_TABLE = "observation_history_list_table"
    const val HISTORICAL_MANAGE_ROOT = "historical_manage_root"
    const val HISTORICAL_MANAGE_TOP_MENU_BUTTON = "historical_manage_top_menu_button"
    const val HISTORICAL_MANAGE_DELETE_ALL_MENU_ITEM = "historical_manage_delete_all_menu_item"
    const val HISTORICAL_MANAGE_ITEM_PREFIX = "historical_manage_item_"
    const val HISTORICAL_MANAGE_ITEM_MENU_PREFIX = "historical_manage_item_menu_"
    const val HISTORICAL_MANAGE_SHOW_MENU_PREFIX = "historical_manage_show_menu_"
    const val HISTORICAL_MANAGE_DELETE_MENU_PREFIX = "historical_manage_delete_menu_"
    const val HISTORICAL_MANAGE_MOVE_TOP_MENU_PREFIX = "historical_manage_move_top_menu_"
    const val HISTORICAL_MANAGE_MOVE_UP_MENU_PREFIX = "historical_manage_move_up_menu_"
    const val HISTORICAL_MANAGE_MOVE_DOWN_MENU_PREFIX = "historical_manage_move_down_menu_"
    const val HISTORICAL_MANAGE_MOVE_BOTTOM_MENU_PREFIX = "historical_manage_move_bottom_menu_"
    const val HISTORICAL_MANAGE_PIN_MENU_PREFIX = "historical_manage_pin_menu_"
    const val HISTORICAL_MANAGE_UNPIN_MENU_PREFIX = "historical_manage_unpin_menu_"
}
