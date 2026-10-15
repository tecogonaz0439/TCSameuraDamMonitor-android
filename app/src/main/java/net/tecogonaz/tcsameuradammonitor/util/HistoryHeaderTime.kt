// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.util

import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime

/**
 * sudmonitor 履歴レスポンスの期間ヘッダーを解析します。
 *
 * DAT 本文の時刻解析とは分け、ISO 8601 の offset 付き日時と、契約で許可する `T24:00` 表記を扱います。
 */
object HistoryHeaderTime {
    /** JST の日付単位で表したカバレッジ範囲。 */
    data class DayRange(
        val start: LocalDate,
        val end: LocalDate
    )

    /**
     * ISO 8601 の offset 付き日時、または契約内の `T24:00` 表記を解析します。
     *
     * `T24:00` と `T24:00:00` のみを翌日00:00へ正規化します。秒なし・秒あり・小数秒を含む通常の日時を受理し、
     * 不正日付、契約外の `T24:` 時刻、offset 欠落は null を返します。
     */
    fun parse(raw: String?): OffsetDateTime? {
        val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return parseIso(value) ?: parseT24(value)
    }

    /**
     * 日次 `since` / `until` ヘッダーを JST の日付範囲へ変換します。
     *
     * until の時刻が00:00の場合は前日を最終日として扱います。片方でも欠落・不正なら null を返します。
     */
    fun rollingCoverage(since: String?, until: String?): DayRange? {
        val sinceValue = parse(since) ?: return null
        val untilValue = parse(until) ?: return null
        return runCatching {
            val startDay = sinceValue.atZoneSameInstant(TimeUtils.JST_ZONE).toLocalDate()
            val untilDay = untilValue.atZoneSameInstant(TimeUtils.JST_ZONE).toLocalDate()
            val endDay = if (untilValue.toLocalTime() == LocalTime.MIDNIGHT) {
                untilDay.minusDays(1)
            } else {
                untilDay
            }
            DayRange(start = startDay, end = endDay)
        }.getOrNull()
    }

    private fun parseIso(value: String): OffsetDateTime? =
        runCatching { OffsetDateTime.parse(value) }.getOrNull()

    private fun parseT24(value: String): OffsetDateTime? {
        val markerIndex = value.indexOf(T24_MARKER)
        if (markerIndex <= 0) return null

        val dayText = value.substring(0, markerIndex)
        val rest = value.substring(markerIndex + T24_MARKER.length)
        val offsetIndex = rest.indexOfFirst { it == 'Z' || it == '+' || it == '-' }
        if (offsetIndex < 0) return null

        val timePart = rest.substring(0, offsetIndex)
        val offsetPart = rest.substring(offsetIndex)
        val normalizedTime = when (timePart) {
            "00", "00:00" -> "00:00"
            "00:00:00" -> "00:00:00"
            else -> return null
        }

        return runCatching {
            val nextDay = LocalDate.parse(dayText).plusDays(1)
            OffsetDateTime.parse("${nextDay}T$normalizedTime$offsetPart")
        }.getOrNull()
    }

    private const val T24_MARKER = "T24:"
}
