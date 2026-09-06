// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.domain.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime


/**
 * 自動更新（自動アップデート）の次回実行日時を計算するスケジューラオブジェクト。
 *
 * 1週間（毎週月曜日08:00等）、1日（毎日08:00等）、12時間（12時間毎）、1時間（1時間毎）といった更新間隔と、
 * ユーザーのカスタムタイミング設定（JST基準）に基づいて正確な次回実行時刻（タイムスタンプ）を算出します。
 */
object AutoUpdateScheduler {
    private val JST_ZONE: ZoneId = ZoneId.of("Asia/Tokyo")

    /**
     * 現在設定されている自動更新設定と現在時刻に基づいて、次回実行すべきミリ秒タイムスタンプを計算します。
     *
     * @param settings アプリ設定（[AppSettings]）
     * @param now 現在時刻のミリ秒タイムスタンプ
     * @return 次回実行日時のタイムスタンプ（ミリ秒）
     */
    fun calculateNextRunTime(settings: AppSettings, now: Long): Long {
        val customMillis = settings.currentCustomTimingMillis
        return if (customMillis == 0L) {
            calculateDefaultNextRunTime(settings.autoUpdateInterval, now, JST_ZONE)
        } else {
            calculateCustomNextRunTime(settings.autoUpdateInterval, now, customMillis, JST_ZONE)
        }
    }

    /**
     * 初期カスタムタイミングの時刻をデフォルト（08:00）で計算します。
     *
     * @param settings アプリ設定
     * @param now 現在時刻のミリ秒タイムスタンプ
     * @return 計算されたミリ秒タイムスタンプ
     */
    fun calculateInitialCustomTiming(settings: AppSettings, now: Long): Long =
        calculateDefaultNextRunTime(
            settings.autoUpdateInterval,
            now,
            JST_ZONE,
            AppSettings.DEFAULT_HOUR,
            AppSettings.DEFAULT_MINUTE
        )

    /**
     * 指定された時間・分に基づいて、初期カスタムタイミングのミリ秒タイムスタンプを計算します。
     * ONE_WEEKの場合は必ず次週月曜をターゲットにします。
     *
     * @param settings アプリ設定
     * @param now 現在時刻のミリ秒タイムスタンプ
     * @param hour 対象時（JST）
     * @param minute 対象分（JST）
     * @return 計算されたミリ秒タイムスタンプ
     */
    fun calculateInitialCustomTiming(settings: AppSettings, now: Long, hour: Int, minute: Int): Long =
        calculateDefaultNextRunTime(
            settings.autoUpdateInterval,
            now,
            JST_ZONE,
            hour,
            minute,
            forceNextMonday = true
        )

    /**
     * 有効な次回実行時刻およびカスタムタイミング基準値を再計算し、ペアで取得します。
     */
    fun calculateEffectiveNextRunTimeAndA(settings: AppSettings, now: Long): Pair<Long, Long> {
        val a = settings.currentCustomTimingMillis
        if (a == 0L) {
            val initial = calculateInitialCustomTiming(settings, now)
            return initial to initial
        }
        val aMinute = a / AppSettings.MILLIS_PER_MINUTE
        val nowMinute = now / AppSettings.MILLIS_PER_MINUTE
        return if (aMinute > nowMinute) {
            a to a
        } else {
            val advanced = advanceToFuture(settings.autoUpdateInterval, a, now)
            advanced to advanced
        }
    }

    /**
     * 次回実行時刻が現在時刻から指定ウィンドウ以内の場合、次周期へ進める。
     * Apple BGTaskの15分制約と同様の処置。
     *
     * @param nextRun 現在の次回実行時刻（ミリ秒）
     * @param now 現在時刻（ミリ秒）
     * @param windowMillis ウィンドウ幅（ミリ秒）。デフォルト15分。
     * @return 必要に応じて次周期へ進めた次回実行時刻
     */
    fun advanceIfWithinWindow(
        interval: AutoUpdateInterval,
        nextRun: Long,
        now: Long,
        windowMillis: Long = AppSettings.MIN_SCHEDULE_ADVANCE_MILLIS
    ): Long {
        var adjusted = nextRun
        while (adjusted - now < windowMillis) {
            adjusted += interval.intervalMillis
        }
        return adjusted
    }

    
    fun calculateNextRunTimeAfterSuccess(settings: AppSettings, now: Long): Long =
        now + settings.autoUpdateInterval.intervalMillis

    private fun advanceToFuture(interval: AutoUpdateInterval, a: Long, now: Long): Long {
        val intervalMillis = interval.intervalMillis
        val nowMinute = now / AppSettings.MILLIS_PER_MINUTE
        val stepsNeeded = ((now - a) / intervalMillis).coerceAtLeast(0L) + 1L
        var advanced = a + stepsNeeded * intervalMillis
        while (advanced / AppSettings.MILLIS_PER_MINUTE <= nowMinute) {
            advanced += intervalMillis
        }
        return advanced
    }

    private fun calculateDefaultNextRunTime(
        interval: AutoUpdateInterval,
        now: Long,
        jst: ZoneId,
        defaultHour: Int = AppSettings.DEFAULT_HOUR,
        defaultMinute: Int = AppSettings.DEFAULT_MINUTE,
        forceNextMonday: Boolean = false
    ): Long = when (interval) {
        AutoUpdateInterval.ONE_WEEK -> {
            val nowZdt = Instant.ofEpochMilli(now).atZone(jst)
            val target = nowZdt
                .withHour(defaultHour)
                .withMinute(defaultMinute)
                .withSecond(0)
                .withNano(0)
            val daysToMonday = (DayOfWeek.MONDAY.value - target.dayOfWeek.value + 7) % 7
            if (forceNextMonday) {
                target.plusDays(if (daysToMonday == 0) 7L else daysToMonday.toLong())
                    .toInstant().toEpochMilli()
            } else if (target.dayOfWeek == DayOfWeek.MONDAY && target.toInstant().toEpochMilli() > now) {
                target.toInstant().toEpochMilli()
            } else {
                target.plusDays(if (daysToMonday == 0) 7L else daysToMonday.toLong())
                    .toInstant().toEpochMilli()
            }
        }
        AutoUpdateInterval.ONE_DAY -> {
            val target = Instant.ofEpochMilli(now).atZone(jst)
                .withHour(defaultHour)
                .withMinute(defaultMinute)
                .withSecond(0)
                .withNano(0)
            if (target.toInstant().toEpochMilli() > now) {
                target.toInstant().toEpochMilli()
            } else {
                target.plusDays(1).toInstant().toEpochMilli()
            }
        }
        AutoUpdateInterval.TWELVE_HOURS -> {
            val nowZdt = Instant.ofEpochMilli(now).atZone(jst)
            val firstHour = if (defaultHour < 12) defaultHour else defaultHour - 12
            val secondHour = firstHour + 12
            val calMorning = nowZdt
                .withHour(firstHour)
                .withMinute(defaultMinute)
                .withSecond(0)
                .withNano(0)
            val calEvening = nowZdt
                .withHour(secondHour)
                .withMinute(defaultMinute)
                .withSecond(0)
                .withNano(0)
            when {
                calMorning.toInstant().toEpochMilli() > now -> calMorning.toInstant().toEpochMilli()
                calEvening.toInstant().toEpochMilli() > now -> calEvening.toInstant().toEpochMilli()
                else -> calMorning.plusDays(1).toInstant().toEpochMilli()
            }
        }
        AutoUpdateInterval.ONE_HOUR -> {
            val target = Instant.ofEpochMilli(now).atZone(jst)
                .withMinute(defaultMinute)
                .withSecond(0)
                .withNano(0)
            if (target.toInstant().toEpochMilli() > now) {
                target.toInstant().toEpochMilli()
            } else {
                target.plusHours(1).toInstant().toEpochMilli()
            }
        }
    }

    private fun calculateCustomNextRunTime(
        interval: AutoUpdateInterval,
        now: Long,
        customMillis: Long,
        jst: ZoneId
    ): Long {
        val customZdt = Instant.ofEpochMilli(customMillis).atZone(jst)
        val customDow = customZdt.dayOfWeek
        val customHour = customZdt.hour
        val customMinute = customZdt.minute

        return when (interval) {
            AutoUpdateInterval.ONE_WEEK -> {
                val nowZdt = Instant.ofEpochMilli(now).atZone(jst)
                val todayWithCustomTime = nowZdt
                    .withHour(customHour)
                    .withMinute(customMinute)
                    .withSecond(0)
                    .withNano(0)
                if (nowZdt.dayOfWeek == customDow && todayWithCustomTime.toInstant().toEpochMilli() > now) {
                    todayWithCustomTime.toInstant().toEpochMilli()
                } else {
                    val daysToCustomDow = (customDow.value - nowZdt.dayOfWeek.value + 7) % 7
                    todayWithCustomTime.plusDays(
                        if (daysToCustomDow == 0) 7L else daysToCustomDow.toLong()
                    ).toInstant().toEpochMilli()
                }
            }
            AutoUpdateInterval.ONE_DAY -> {
                val target = Instant.ofEpochMilli(now).atZone(jst)
                    .withHour(customHour)
                    .withMinute(customMinute)
                    .withSecond(0)
                    .withNano(0)
                if (target.toInstant().toEpochMilli() > now) {
                    target.toInstant().toEpochMilli()
                } else {
                    target.plusDays(1).toInstant().toEpochMilli()
                }
            }
            AutoUpdateInterval.TWELVE_HOURS -> calculateTwelveHourNextRunTime(
                now = now,
                customHour = customHour,
                customMinute = customMinute,
                jst = jst
            )
            AutoUpdateInterval.ONE_HOUR -> {
                val target = Instant.ofEpochMilli(now).atZone(jst)
                    .withMinute(customMinute)
                    .withSecond(0)
                    .withNano(0)
                if (target.toInstant().toEpochMilli() > now) {
                    target.toInstant().toEpochMilli()
                } else {
                    target.plusHours(1).toInstant().toEpochMilli()
                }
            }
        }
    }

    private fun calculateTwelveHourNextRunTime(
        now: Long,
        customHour: Int,
        customMinute: Int,
        jst: ZoneId
    ): Long {
        val firstHour = if (customHour < 12) customHour else customHour - 12
        val secondHour = firstHour + 12
        val nowZdt = Instant.ofEpochMilli(now).atZone(jst)
        val first = nowZdt
            .withHour(firstHour)
            .withMinute(customMinute)
            .withSecond(0)
            .withNano(0)
        val second = nowZdt
            .withHour(secondHour)
            .withMinute(customMinute)
            .withSecond(0)
            .withNano(0)
        return when {
            first.toInstant().toEpochMilli() > now -> first.toInstant().toEpochMilli()
            second.toInstant().toEpochMilli() > now -> second.toInstant().toEpochMilli()
            else -> first.plusDays(1).toInstant().toEpochMilli()
        }
    }

    fun recalculateAllCustomTimings(settings: AppSettings, now: Long, hour: Int, minute: Int): AppSettings {
        val weekly = calculateInitialCustomTiming(
            settings.copy(autoUpdateInterval = AutoUpdateInterval.ONE_WEEK), now, hour, minute
        )
        val daily = calculateInitialCustomTiming(
            settings.copy(autoUpdateInterval = AutoUpdateInterval.ONE_DAY), now, hour, minute
        )
        val twelveHours = calculateInitialCustomTiming(
            settings.copy(autoUpdateInterval = AutoUpdateInterval.TWELVE_HOURS), now, hour, minute
        )
        val hourly = calculateInitialCustomTiming(
            settings.copy(autoUpdateInterval = AutoUpdateInterval.ONE_HOUR), now, hour, minute
        )
        return settings.copy(
            autoUpdateCustomTimingMillisWeekly = weekly,
            autoUpdateCustomTimingMillisDaily = daily,
            autoUpdateCustomTimingMillis12Hours = twelveHours,
            autoUpdateCustomTimingMillisHourly = hourly
        )
    }
}
