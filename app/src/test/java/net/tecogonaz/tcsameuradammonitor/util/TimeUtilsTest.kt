// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.Locale
import java.util.TimeZone

/**
 * 日時操作のユーティリティ [TimeUtils] のユニットテストクラス。
 * 日本標準時（JST）のミリ秒変換、タイムゾーンの違いに応じた JST 表記サフィックス（「(JST)」）の付与、
 * DatePicker 用の UTC/JST 変換処理、および MLIT 特有の表記である「24:00」を翌日「00:00」へ繰り上げる
 * パース/フォーマットロジックなどを検証します。
 */
class TimeUtilsTest {
    private lateinit var originalTimeZone: TimeZone

    @Before
    fun setUp() {
        originalTimeZone = TimeZone.getDefault()
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(originalTimeZone)
    }

    @Test
    fun parseJstMillis_returnsNullForInvalidInput() {
        assertNull(TimeUtils.parseJstMillis("bad", "yyyy/MM/dd HH:mm"))
        assertNull(TimeUtils.parseJstMillis("2026/02/30 00:00", "yyyy/MM/dd HH:mm"))
    }

    @Test
    fun parseJstMillis_parsesDateTimeAsJstEpochMillis() {
        val millis = TimeUtils.parseJstMillis("2026/05/01 09:30", "yyyy/MM/dd HH:mm")

        assertEquals("2026-05-01T09:30:00+09:00", TimeUtils.formatToJstIso8601(millis!!))
    }

    @Test
    fun formatToJst_formatsEpochMillisWithPattern() {
        val millis = TimeUtils.parseJstMillis("2026/05/01 09:30", "yyyy/MM/dd HH:mm")!!

        assertEquals("20260501 0930", TimeUtils.formatToJst(millis, "yyyyMMdd HHmm"))
    }

    @Test
    fun formatToJstWithLocale_usesRequestedLocale() {
        val millis = TimeUtils.parseJstMillis("2026/05/01 09:30", "yyyy/MM/dd HH:mm")!!

        assertEquals("Fri", TimeUtils.formatToJstWithLocale(millis, "EEE", Locale.US))
        assertEquals("金", TimeUtils.formatToJstWithLocale(millis, "EEE", Locale.JAPAN))
    }

    @Test
    fun parseAndFormatToJstWithSuffix_appendsSuffixOnlyOutsideJst() {
        withDefaultTimeZone("Asia/Tokyo") {
            assertEquals(
                "05/01 09:30",
                TimeUtils.parseAndFormatToJstWithSuffix("2026/05/01 09:30", "yyyy/MM/dd HH:mm", "MM/dd HH:mm")
            )
        }

        withDefaultTimeZone("UTC") {
            assertEquals(
                "05/01 09:30 (JST)",
                TimeUtils.parseAndFormatToJstWithSuffix("2026/05/01 09:30", "yyyy/MM/dd HH:mm", "MM/dd HH:mm")
            )
        }
    }

    @Test
    fun appendJstSuffix_isIdempotentOutsideJst() {
        withDefaultTimeZone("UTC") {
            assertEquals(
                "05:15 (JST)",
                TimeUtils.appendJstSuffix(TimeUtils.appendJstSuffix("05:15"))
            )
        }
    }

    @Test
    fun appendJstSuffix_returnsInputUnchangedInJst() {
        withDefaultTimeZone("Asia/Tokyo") {
            assertEquals(
                "05:15",
                TimeUtils.appendJstSuffix("05:15")
            )
        }
    }

    @Test
    fun isJst_checksDefaultTimeZoneOffset() {
        withDefaultTimeZone("Asia/Tokyo") {
            assertTrue(TimeUtils.isJst())
        }

        withDefaultTimeZone("UTC") {
            assertFalse(TimeUtils.isJst())
        }
    }

    @Test
    fun datePickerUtcConversion_roundTripsJstDay() {
        val jstMillis = TimeUtils.parseJstMillis("2026/05/01 18:30", "yyyy/MM/dd HH:mm")!!

        val utcMidnight = TimeUtils.toUtcMidnightFromJstDay(jstMillis)
        val jstDayStart = TimeUtils.utcMidnightToJstDayStart(utcMidnight)

        assertEquals("2026/05/01 00:00", TimeUtils.formatToJst(jstDayStart, "yyyy/MM/dd HH:mm"))
    }

    @Test
    fun parseJstMillisAllow24Hour_rejectsInvalid24HourForms() {
        assertNull(TimeUtils.parseJstMillisAllow24Hour("2026/05/01 24:01", "yyyy/MM/dd HH:mm"))
        assertNull(TimeUtils.parseJstMillisAllow24Hour("2026/05/01 25:00", "yyyy/MM/dd HH:mm"))
    }

    @Test
    fun parseAndFormatToJst_acceptsNonZeroPaddedHistoricalDate() {
        val formatted = TimeUtils.parseAndFormatToJst(
            src = "2019/5/1 01:00",
            srcPattern = "yyyy/MM/dd HH:mm",
            dstPattern = "MM/dd HH:mm"
        )

        assertEquals("05/01 01:00", formatted)
    }

    @Test
    fun parseJstMillisAllow24Hour_acceptsNonZeroPaddedHistoricalDate() {
        val millis = TimeUtils.parseJstMillisAllow24Hour(
            src = "2019/5/1 24:00",
            pattern = "yyyy/MM/dd HH:mm"
        )

        assertNotNull(millis)
        assertEquals("2019/05/02 00:00", TimeUtils.formatToJst(millis!!, "yyyy/MM/dd HH:mm"))
    }

    @Test
    fun parseAndFormatToJst_formats24HourAsNextDayMidnight() {
        val formatted = TimeUtils.parseAndFormatToJst(
            src = "2019/5/1 24:00",
            srcPattern = "yyyy/MM/dd HH:mm",
            dstPattern = "MM/dd HH:mm"
        )

        assertEquals("05/02 00:00", formatted)
    }

    private inline fun withDefaultTimeZone(id: String, block: () -> Unit) {
        val previous = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone(id))
        try {
            block()
        } finally {
            TimeZone.setDefault(previous)
        }
    }
}
