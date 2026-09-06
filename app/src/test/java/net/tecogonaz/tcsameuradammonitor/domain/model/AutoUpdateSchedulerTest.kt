// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.domain.model

import net.tecogonaz.tcsameuradammonitor.ui.main.effectiveAutoUpdateIntervalForDailyHistory
import net.tecogonaz.tcsameuradammonitor.util.TimeUtils
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * バックグラウンド自動更新のタイミング制御クラス [AutoUpdateScheduler] のユニットテストクラス。
 * 各種更新スパン（1時間、12時間、1日、1週間）におけるデフォルト値およびカスタム指定時の
 * 次回スケジュール時刻計算処理、月末や閏年（2月29日）をまたぐ際の日付の繰り上がり処理、
 * 設定直後の初期ランダムスロット割り当て、および手動/自動更新成功後の次回スケジュール時間の
 * 遷移ロジックを検証します。
 */
class AutoUpdateSchedulerTest {
    @Test
    fun calculateNextRunTime_oneDayBeforeDefaultTime_returnsToday515() {
        val settings = settings(AutoUpdateInterval.ONE_DAY)

        assertEquals(
            jstMillis("2026/05/18 05:15"),
            settings.calculateNextRunTime(jstMillis("2026/05/18 03:00"))
        )
    }

    @Test
    fun calculateNextRunTime_oneDayAtDefaultTime_returnsTomorrow515() {
        val settings = settings(AutoUpdateInterval.ONE_DAY)

        assertEquals(
            jstMillis("2026/05/19 05:15"),
            settings.calculateNextRunTime(jstMillis("2026/05/18 05:15"))
        )
    }

    @Test
    fun calculateNextRunTime_oneDayAfterDefaultTime_returnsTomorrow515() {
        val settings = settings(AutoUpdateInterval.ONE_DAY)

        assertEquals(
            jstMillis("2026/05/19 05:15"),
            settings.calculateNextRunTime(jstMillis("2026/05/18 08:00"))
        )
    }

    @Test
    fun calculateNextRunTime_oneHourBeforeDefaultMinute_returnsCurrentHour15() {
        val settings = settings(AutoUpdateInterval.ONE_HOUR)

        assertEquals(
            jstMillis("2026/05/18 05:15"),
            settings.calculateNextRunTime(jstMillis("2026/05/18 05:10"))
        )
    }

    @Test
    fun calculateNextRunTime_oneHourAtDefaultMinute_returnsNextHour15() {
        val settings = settings(AutoUpdateInterval.ONE_HOUR)

        assertEquals(
            jstMillis("2026/05/18 06:15"),
            settings.calculateNextRunTime(jstMillis("2026/05/18 05:15"))
        )
    }

    @Test
    fun calculateNextRunTime_oneHourAfterDefaultMinute_returnsNextHour15() {
        val settings = settings(AutoUpdateInterval.ONE_HOUR)

        assertEquals(
            jstMillis("2026/05/18 06:15"),
            settings.calculateNextRunTime(jstMillis("2026/05/18 05:25"))
        )
    }

    @Test
    fun calculateNextRunTime_oneWeekBeforeMondayDefaultTime_returnsSameMonday515() {
        val settings = settings(AutoUpdateInterval.ONE_WEEK)

        assertEquals(
            jstMillis("2026/05/18 05:15"),
            settings.calculateNextRunTime(jstMillis("2026/05/18 03:00"))
        )
    }

    @Test
    fun calculateNextRunTime_oneWeekAtMondayDefaultTime_returnsNextMonday515() {
        val settings = settings(AutoUpdateInterval.ONE_WEEK)

        assertEquals(
            jstMillis("2026/05/25 05:15"),
            settings.calculateNextRunTime(jstMillis("2026/05/18 05:15"))
        )
    }

    @Test
    fun calculateNextRunTime_oneWeekAfterMondayDefaultTime_returnsNextMonday515() {
        val settings = settings(AutoUpdateInterval.ONE_WEEK)

        assertEquals(
            jstMillis("2026/05/25 05:15"),
            settings.calculateNextRunTime(jstMillis("2026/05/18 08:00"))
        )
    }

    @Test
    fun calculateNextRunTime_oneWeekOnNonMonday_returnsNextMonday515() {
        val settings = settings(AutoUpdateInterval.ONE_WEEK)

        assertEquals(
            jstMillis("2026/05/25 05:15"),
            settings.calculateNextRunTime(jstMillis("2026/05/19 03:00"))
        )
    }

    @Test
    fun calculateNextRunTime_twelveHoursBeforeMorningSlot_returnsMorning515() {
        val settings = settings(AutoUpdateInterval.TWELVE_HOURS)

        assertEquals(
            jstMillis("2026/05/18 05:15"),
            settings.calculateNextRunTime(jstMillis("2026/05/18 03:00"))
        )
    }

    @Test
    fun calculateNextRunTime_twelveHoursBetweenSlots_returnsEvening515() {
        val settings = settings(AutoUpdateInterval.TWELVE_HOURS)

        assertEquals(
            jstMillis("2026/05/18 17:15"),
            settings.calculateNextRunTime(jstMillis("2026/05/18 12:00"))
        )
    }

    @Test
    fun calculateNextRunTime_twelveHoursAfterEveningSlot_returnsTomorrowMorning515() {
        val settings = settings(AutoUpdateInterval.TWELVE_HOURS)

        assertEquals(
            jstMillis("2026/05/19 05:15"),
            settings.calculateNextRunTime(jstMillis("2026/05/18 20:00"))
        )
    }

    @Test
    fun calculateNextRunTime_weeklyCustomTime_usesStoredDayOfWeekHourAndMinute() {
        val settings = settings(AutoUpdateInterval.ONE_WEEK)
            .copy(autoUpdateCustomTimingMillisWeekly = jstMillis("2026/05/18 06:30"))

        assertEquals(
            jstMillis("2026/05/18 06:30"),
            settings.calculateNextRunTime(jstMillis("2026/05/18 06:00"))
        )
        assertEquals(
            jstMillis("2026/05/25 06:30"),
            settings.calculateNextRunTime(jstMillis("2026/05/18 06:31"))
        )
    }

    @Test
    fun calculateNextRunTime_dailyCustomTime_rollsToTomorrowWhenPassed() {
        val settings = settings(AutoUpdateInterval.ONE_DAY)
            .copy(autoUpdateCustomTimingMillisDaily = jstMillis("2026/05/18 06:30"))

        assertEquals(
            jstMillis("2026/05/18 06:30"),
            settings.calculateNextRunTime(jstMillis("2026/05/18 06:00"))
        )
        assertEquals(
            jstMillis("2026/05/19 06:30"),
            settings.calculateNextRunTime(jstMillis("2026/05/18 06:31"))
        )
    }

    @Test
    fun calculateNextRunTime_twelveHourCustomTime_usesTwoDailySlots() {
        val settings = settings(AutoUpdateInterval.TWELVE_HOURS)
            .copy(autoUpdateCustomTimingMillis12Hours = jstMillis("2026/05/18 18:30"))

        assertEquals(
            jstMillis("2026/05/18 06:30"),
            settings.calculateNextRunTime(jstMillis("2026/05/18 05:00"))
        )
        assertEquals(
            jstMillis("2026/05/18 18:30"),
            settings.calculateNextRunTime(jstMillis("2026/05/18 12:00"))
        )
        assertEquals(
            jstMillis("2026/05/19 06:30"),
            settings.calculateNextRunTime(jstMillis("2026/05/18 20:00"))
        )
    }

    @Test
    fun calculateNextRunTime_hourlyCustomMinute_rollsToNextHourWhenPassed() {
        val settings = settings(AutoUpdateInterval.ONE_HOUR)
            .copy(autoUpdateCustomTimingMillisHourly = jstMillis("2026/05/18 06:45"))

        assertEquals(
            jstMillis("2026/05/18 05:45"),
            settings.calculateNextRunTime(jstMillis("2026/05/18 05:30"))
        )
        assertEquals(
            jstMillis("2026/05/18 06:45"),
            settings.calculateNextRunTime(jstMillis("2026/05/18 05:50"))
        )
    }

    @Test
    fun calculateEffectiveNextRunTimeAndA_withoutCustomTiming_initializesDefaultA() {
        val settings = settings(AutoUpdateInterval.ONE_DAY)
        val now = jstMillis("2026/05/18 03:00")

        assertEquals(
            jstMillis("2026/05/18 05:15") to jstMillis("2026/05/18 05:15"),
            settings.calculateEffectiveNextRunTimeAndA(now)
        )
    }

    @Test
    fun calculateInitialCustomTiming_withRandomRangeLowerBound_usesSpecifiedTime() {
        val now = jstMillis("2026/05/18 03:00")
        val cases = listOf(
            settings(AutoUpdateInterval.ONE_WEEK) to jstMillis("2026/05/25 00:15"),
            settings(AutoUpdateInterval.ONE_DAY) to jstMillis("2026/05/19 00:15"),
            settings(AutoUpdateInterval.TWELVE_HOURS) to jstMillis("2026/05/18 12:15"),
            settings(AutoUpdateInterval.ONE_HOUR) to jstMillis("2026/05/18 03:15")
        )

        cases.forEach { (settings, expected) ->
            assertEquals(expected, settings.calculateInitialCustomTiming(now, 0, 15))
        }
    }

    @Test
    fun calculateInitialCustomTiming_withRandomRangeUpperBound_usesSpecifiedTime() {
        val now = jstMillis("2026/05/18 06:00")
        val cases = listOf(
            settings(AutoUpdateInterval.ONE_WEEK) to jstMillis("2026/05/25 05:59"),
            settings(AutoUpdateInterval.ONE_DAY) to jstMillis("2026/05/19 05:59"),
            settings(AutoUpdateInterval.TWELVE_HOURS) to jstMillis("2026/05/18 17:59"),
            settings(AutoUpdateInterval.ONE_HOUR) to jstMillis("2026/05/18 06:59")
        )

        cases.forEach { (settings, expected) ->
            assertEquals(expected, settings.calculateInitialCustomTiming(now, 5, 59))
        }
    }

    @Test
    fun calculateEffectiveNextRunTimeAndA_withFutureCustomTiming_preservesA() {
        val future = jstMillis("2026/05/18 05:16")
        val settings = settings(AutoUpdateInterval.ONE_HOUR)
            .copy(autoUpdateCustomTimingMillisHourly = future)

        assertEquals(
            future to future,
            settings.calculateEffectiveNextRunTimeAndA(jstMillis("2026/05/18 05:15"))
        )
    }

    @Test
    fun calculateEffectiveNextRunTimeAndA_withPastCustomTiming_advancesByIntervalUntilFuture() {
        val a = jstMillis("2026/05/18 05:15")
        val settings = settings(AutoUpdateInterval.TWELVE_HOURS)
            .copy(autoUpdateCustomTimingMillis12Hours = a)

        assertEquals(
            jstMillis("2026/05/18 17:15") to jstMillis("2026/05/18 17:15"),
            settings.calculateEffectiveNextRunTimeAndA(jstMillis("2026/05/18 05:15"))
        )
    }

    @Test
    fun calculateEffectiveNextRunTimeAndA_advancesEachIntervalType() {
        val now = jstMillis("2026/05/18 08:00")
        val cases = listOf(
            settings(AutoUpdateInterval.ONE_WEEK).copy(autoUpdateCustomTimingMillisWeekly = jstMillis("2026/05/11 05:15")) to
                jstMillis("2026/05/25 05:15"),
            settings(AutoUpdateInterval.ONE_DAY).copy(autoUpdateCustomTimingMillisDaily = jstMillis("2026/05/17 05:15")) to
                jstMillis("2026/05/19 05:15"),
            settings(AutoUpdateInterval.TWELVE_HOURS).copy(autoUpdateCustomTimingMillis12Hours = jstMillis("2026/05/17 17:15")) to
                jstMillis("2026/05/18 17:15"),
            settings(AutoUpdateInterval.ONE_HOUR).copy(autoUpdateCustomTimingMillisHourly = jstMillis("2026/05/18 05:15")) to
                jstMillis("2026/05/18 08:15")
        )

        cases.forEach { (settings, expected) ->
            assertEquals(expected to expected, settings.calculateEffectiveNextRunTimeAndA(now))
        }
    }

    @Test
    fun calculateNextRunTimeAfterSuccess_addsSelectedInterval() {
        val now = jstMillis("2026/05/18 05:15")
        val settings = settings(AutoUpdateInterval.ONE_DAY)

        assertEquals(now + AutoUpdateInterval.ONE_DAY.intervalMillis, settings.calculateNextRunTimeAfterSuccess(now))
    }

    @Test
    fun calculateNextRunTime_dailyAcrossMonthEnd_returnsFirstDayOfNextMonth() {
        val settings = settings(AutoUpdateInterval.ONE_DAY)

        assertEquals(
            jstMillis("2026/06/01 05:15"),
            settings.calculateNextRunTime(jstMillis("2026/05/31 08:00"))
        )
    }

    @Test
    fun calculateNextRunTime_dailyAcrossLeapDay_returnsFebruary29() {
        val settings = settings(AutoUpdateInterval.ONE_DAY)

        assertEquals(
            jstMillis("2028/02/29 05:15"),
            settings.calculateNextRunTime(jstMillis("2028/02/28 08:00"))
        )
    }

    @Test
    fun calculateEffectiveNextRunTimeAndA_hourlyAcrossMonthEndAdvancesToNextMonth() {
        val settings = settings(AutoUpdateInterval.ONE_HOUR)
            .copy(autoUpdateCustomTimingMillisHourly = jstMillis("2026/05/31 23:45"))

        assertEquals(
            jstMillis("2026/06/01 00:45") to jstMillis("2026/06/01 00:45"),
            settings.calculateEffectiveNextRunTimeAndA(jstMillis("2026/05/31 23:45"))
        )
    }

    @Test
    fun currentCustomTimingMillisAndWithCustomTimingMillis_useCurrentIntervalField() {
        val updated = settings(AutoUpdateInterval.TWELVE_HOURS).withCustomTimingMillis(12345L)

        assertEquals(12345L, updated.currentCustomTimingMillis)
        assertEquals(0L, updated.autoUpdateCustomTimingMillisWeekly)
        assertEquals(0L, updated.autoUpdateCustomTimingMillisDaily)
        assertEquals(0L, updated.autoUpdateCustomTimingMillisHourly)
    }

    @Test
    fun calculateInitialCustomTiming_oneWeekOnMondayWithForce_goesToNextMonday() {
        val settings = settings(AutoUpdateInterval.ONE_WEEK)
        val now = jstMillis("2026/05/18 01:00")

        assertEquals(
            jstMillis("2026/05/25 05:15"),
            settings.calculateInitialCustomTiming(now, 5, 15)
        )
    }

    @Test
    fun advanceIfWithinWindow_nextRunFarAhead_noChange() {
        val interval = AutoUpdateInterval.ONE_HOUR
        val nextRun = jstMillis("2026/05/18 06:00")
        val now = jstMillis("2026/05/18 05:30")

        assertEquals(
            nextRun,
            AutoUpdateScheduler.advanceIfWithinWindow(interval, nextRun, now)
        )
    }

    @Test
    fun advanceIfWithinWindow_nextRunWithinWindow_advancesByOneInterval() {
        val interval = AutoUpdateInterval.ONE_HOUR
        val nextRun = jstMillis("2026/05/18 05:35")
        val now = jstMillis("2026/05/18 05:30")

        assertEquals(
            jstMillis("2026/05/18 06:35"),
            AutoUpdateScheduler.advanceIfWithinWindow(interval, nextRun, now)
        )
    }

    @Test
    fun advanceIfWithinWindow_nextRunAtNow_advancesByInterval() {
        val interval = AutoUpdateInterval.ONE_DAY
        val nextRun = jstMillis("2026/05/18 05:30")
        val now = jstMillis("2026/05/18 05:30")

        assertEquals(
            jstMillis("2026/05/19 05:30"),
            AutoUpdateScheduler.advanceIfWithinWindow(interval, nextRun, now)
        )
    }

    @Test
    fun advanceIfWithinWindow_nextRunAtWindowBoundary_noChange() {
        val interval = AutoUpdateInterval.ONE_HOUR
        val nextRun = jstMillis("2026/05/18 05:45")
        val now = jstMillis("2026/05/18 05:30")

        assertEquals(
            nextRun,
            AutoUpdateScheduler.advanceIfWithinWindow(interval, nextRun, now)
        )
    }

    @Test
    fun advanceIfWithinWindow_nextRunMultipleWindowsBehind_advancesMultipleIntervals() {
        val interval = AutoUpdateInterval.ONE_HOUR
        val nextRun = jstMillis("2026/05/18 05:00")
        val now = jstMillis("2026/05/18 06:00")

        assertEquals(
            jstMillis("2026/05/18 07:00"),
            AutoUpdateScheduler.advanceIfWithinWindow(interval, nextRun, now)
        )
    }

    @Test
    fun recalculateAllCustomTimings_setsAllFourIntervalsFromAnchor() {
        val now = jstMillis("2026/05/18 03:00")
        val settings = settings(AutoUpdateInterval.ONE_WEEK)

        val result = AutoUpdateScheduler.recalculateAllCustomTimings(settings, now, 20, 30)

        assertEquals(
            jstMillis("2026/05/25 20:30"),
            result.autoUpdateCustomTimingMillisWeekly
        )
        assertEquals(
            jstMillis("2026/05/18 20:30"),
            result.autoUpdateCustomTimingMillisDaily
        )
        assertEquals(
            jstMillis("2026/05/18 08:30"),
            result.autoUpdateCustomTimingMillis12Hours
        )
        assertEquals(
            jstMillis("2026/05/18 03:30"),
            result.autoUpdateCustomTimingMillisHourly
        )
    }

    @Test
    fun effectiveAutoUpdateIntervalForDailyHistory_oneHourAndTwelveHoursMapToOneDay() {
        // 1時間 / 12時間 はクールダウン（保存済み nextUpdateAtEpochMs）により実質1日1回になる（D7）
        assertEquals(
            AutoUpdateInterval.ONE_DAY,
            effectiveAutoUpdateIntervalForDailyHistory(AutoUpdateInterval.ONE_HOUR)
        )
        assertEquals(
            AutoUpdateInterval.ONE_DAY,
            effectiveAutoUpdateIntervalForDailyHistory(AutoUpdateInterval.TWELVE_HOURS)
        )
    }

    @Test
    fun effectiveAutoUpdateIntervalForDailyHistory_oneDayAndOneWeekKeepTheirInterval() {
        assertEquals(
            AutoUpdateInterval.ONE_DAY,
            effectiveAutoUpdateIntervalForDailyHistory(AutoUpdateInterval.ONE_DAY)
        )
        assertEquals(
            AutoUpdateInterval.ONE_WEEK,
            effectiveAutoUpdateIntervalForDailyHistory(AutoUpdateInterval.ONE_WEEK)
        )
    }

    private fun settings(interval: AutoUpdateInterval): AppSettings =
        AppSettings(autoUpdateInterval = interval)

    private fun jstMillis(value: String): Long =
        TimeUtils.parseJstMillis(value, "yyyy/MM/dd HH:mm") ?: error("Invalid date: $value")
}
