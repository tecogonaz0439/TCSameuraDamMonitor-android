// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.domain.model

/**
 * メイン画面で開閉可能なCardを、リアルタイム表示と過去データ表示を区別して識別するキー。
 */
enum class MainCardExpansionKey {
    REALTIME_OBSERVATION,
    REALTIME_LATEST,
    REALTIME_HISTORY,
    REALTIME_GRAPH,
    REALTIME_LINKS,
    HISTORICAL_OBSERVATION,
    HISTORICAL_HISTORY,
    HISTORICAL_GRAPH,
    HISTORICAL_LINKS
}

/**
 * メイン画面にあるCardの展開状態。
 *
 * 初回利用時の表示を従来と合わせるため、すべて展開を既定値とする。
 */
data class MainCardExpansionState(
    val realtimeObservationExpanded: Boolean = true,
    val realtimeLatestExpanded: Boolean = true,
    val realtimeHistoryExpanded: Boolean = true,
    val realtimeGraphExpanded: Boolean = true,
    val realtimeLinksExpanded: Boolean = true,
    val historicalObservationExpanded: Boolean = true,
    val historicalHistoryExpanded: Boolean = true,
    val historicalGraphExpanded: Boolean = true,
    val historicalLinksExpanded: Boolean = true
) {
    /**
     * 指定したCardが展開されているかを返す。
     */
    operator fun get(key: MainCardExpansionKey): Boolean = when (key) {
        MainCardExpansionKey.REALTIME_OBSERVATION -> realtimeObservationExpanded
        MainCardExpansionKey.REALTIME_LATEST -> realtimeLatestExpanded
        MainCardExpansionKey.REALTIME_HISTORY -> realtimeHistoryExpanded
        MainCardExpansionKey.REALTIME_GRAPH -> realtimeGraphExpanded
        MainCardExpansionKey.REALTIME_LINKS -> realtimeLinksExpanded
        MainCardExpansionKey.HISTORICAL_OBSERVATION -> historicalObservationExpanded
        MainCardExpansionKey.HISTORICAL_HISTORY -> historicalHistoryExpanded
        MainCardExpansionKey.HISTORICAL_GRAPH -> historicalGraphExpanded
        MainCardExpansionKey.HISTORICAL_LINKS -> historicalLinksExpanded
    }

    /**
     * 指定したCardだけを反転した新しい状態を返す。
     */
    fun toggled(key: MainCardExpansionKey): MainCardExpansionState = when (key) {
        MainCardExpansionKey.REALTIME_OBSERVATION ->
            copy(realtimeObservationExpanded = !realtimeObservationExpanded)
        MainCardExpansionKey.REALTIME_LATEST ->
            copy(realtimeLatestExpanded = !realtimeLatestExpanded)
        MainCardExpansionKey.REALTIME_HISTORY ->
            copy(realtimeHistoryExpanded = !realtimeHistoryExpanded)
        MainCardExpansionKey.REALTIME_GRAPH ->
            copy(realtimeGraphExpanded = !realtimeGraphExpanded)
        MainCardExpansionKey.REALTIME_LINKS ->
            copy(realtimeLinksExpanded = !realtimeLinksExpanded)
        MainCardExpansionKey.HISTORICAL_OBSERVATION ->
            copy(historicalObservationExpanded = !historicalObservationExpanded)
        MainCardExpansionKey.HISTORICAL_HISTORY ->
            copy(historicalHistoryExpanded = !historicalHistoryExpanded)
        MainCardExpansionKey.HISTORICAL_GRAPH ->
            copy(historicalGraphExpanded = !historicalGraphExpanded)
        MainCardExpansionKey.HISTORICAL_LINKS ->
            copy(historicalLinksExpanded = !historicalLinksExpanded)
    }
}
