// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.ui.main

/**
 * 主系列(今年のライン)を描画するかを判定します。
 *
 * 非比較モード(mode 1/2)ではメトリックラインの選択状態
 * ([GraphLineSelectionState.visibleLines])に従い、主系列のチップ(「貯水率」「貯水量」)を
 * 外すと主線とmin/maxマーカーが描画されなくなります。
 * 比較モード(mode 3/4)では今年チップの選択状態([GraphLineSelectionState.selectedYears])に
 * 従います。比較データ非ロード時は今年チップが無いためfalse
 * (読み込みオーバーレイで覆われる)。
 *
 * @param isComparisonMode 比較モード(mode 3/4)であるかどうか
 * @param mainLine 主系列のライン([GraphLine.STORAGE_RATE]または[GraphLine.STORAGE_VOLUME])
 * @param visibleLines 選択中のメトリックラインの集合
 * @param comparisonCurrentYear 比較データが保持する今年。比較データ非ロード時はnull
 * @param selectedYears 選択中の年の集合(過去年+今年)
 * @return 主系列を描画する場合はtrue
 */
internal fun isCurrentYearLineVisible(
    isComparisonMode: Boolean,
    mainLine: GraphLine,
    visibleLines: Set<GraphLine>,
    comparisonCurrentYear: Int?,
    selectedYears: Set<Int>
): Boolean = if (isComparisonMode) {
    (comparisonCurrentYear ?: 0) in selectedYears
} else {
    mainLine in visibleLines
}

/**
 * 現在表示しているグラフの系列を描画するかを判定します。
 *
 * 主系列は比較モードでは今年チップ、通常モードでは主系列チップに従い、
 * 副系列は常にメトリックラインチップに従います。グラフ線と操作中の交点markerで
 * 同じ判定を使うことで、線だけが消えてmarkerが残る表示ずれを防ぎます。
 */
internal fun isGraphLineVisible(
    line: GraphLine,
    isComparisonMode: Boolean,
    mainLine: GraphLine,
    visibleLines: Set<GraphLine>,
    comparisonCurrentYear: Int?,
    selectedYears: Set<Int>
): Boolean = if (line == mainLine) {
    isCurrentYearLineVisible(
        isComparisonMode = isComparisonMode,
        mainLine = mainLine,
        visibleLines = visibleLines,
        comparisonCurrentYear = comparisonCurrentYear,
        selectedYears = selectedYears
    )
} else {
    line in visibleLines
}
