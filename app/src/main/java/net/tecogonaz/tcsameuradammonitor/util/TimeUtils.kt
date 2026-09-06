// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.util

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle
import java.time.temporal.ChronoField
import java.time.temporal.TemporalAccessor
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone


/**
 * JST（日本標準時）へのタイムゾーン変換、日時のフォーマット・パース処理を集約した時間処理ユーティリティ。
 *
 * ダムデータや過去ログで扱われる様々な日時（EUC-JP HTMLの時刻や.datファイルの「24:00」等の特異な日付表現など）を正しくハンドリングします。
 */
object TimeUtils {

    /** JST（日本標準時）のZoneId定義 */
    val JST_ZONE: ZoneId = ZoneId.of("Asia/Tokyo")

    /** JSTのTimeZoneオブジェクト定義 */
    val JST_TIMEZONE: TimeZone = TimeZone.getTimeZone(JST_ZONE)

    /** 非JST端末で時刻表記の末尾に付与するサフィックス */
    private const val JST_SUFFIX = " (JST)"

    /**
     * 端末の現在のタイムゾーンがJSTであるかどうかを判定します。
     *
     * @return 端末タイムゾーンがJSTの場合はtrue
     */
    fun isJst(): Boolean {
        val tz = TimeZone.getDefault()
        val jstOffset = JST_TIMEZONE.rawOffset 
        
        return tz.rawOffset == jstOffset &&
               tz.getOffset(System.currentTimeMillis()) == jstOffset
    }

    /**
     * ミリ秒タイムスタンプをJST基準で指定されたパターン文字列にフォーマットします。
     *
     * @param timeMillis ミリ秒タイムスタンプ
     * @param pattern フォーマットパターン（例: "yyyy/MM/dd HH:mm"）
     * @return フォーマットされた文字列
     */
    fun formatToJst(timeMillis: Long, pattern: String): String {
        return Instant.ofEpochMilli(timeMillis)
            .atZone(JST_ZONE)
            .format(formatter(pattern, Locale.US))
    }

    /**
     * ロケールを明示的に指定してミリ秒タイムスタンプをJSTでフォーマットします。
     */
    fun formatToJstWithLocale(timeMillis: Long, pattern: String, locale: Locale = Locale.US): String {
        return Instant.ofEpochMilli(timeMillis)
            .atZone(JST_ZONE)
            .format(formatter(pattern, locale))
    }

    /**
     * 送信元フォーマットの日時文字列をパースし、別の表示用フォーマットに変換します。
     * 「24:00」などの時間表記のパース・マッピングも処理します。
     */
    fun parseAndFormatToJst(src: String, srcPattern: String, dstPattern: String): String? {
        val millis = parseJstMillisAllow24Hour(src, srcPattern) ?: return null
        return formatToJst(millis, dstPattern)
    }

    
    fun parseAndFormatToJstWithSuffix(src: String, srcPattern: String, dstPattern: String): String? {
        val formatted = parseAndFormatToJst(src, srcPattern, dstPattern) ?: return null
        return appendJstSuffix(formatted)
    }

    
    /**
     * 時刻文字列の末尾にJST表記のサフィックス（「 (JST)」）を付与します。
     *
     * 端末のタイムゾーンがJSTの場合は入力をそのまま返します。非JSTの場合はサフィックスを付与しますが、
     * すでに末尾に付与済みの場合は二重に付与しません（冪等）。
     */
    fun appendJstSuffix(time: String): String {
        if (isJst()) return time
        if (time.endsWith(JST_SUFFIX)) return time
        return "$time$JST_SUFFIX"
    }

    
    fun formatToJstIso8601(timeMillis: Long): String {
        return formatToJst(timeMillis, "yyyy-MM-dd'T'HH:mm:ssXXX")
    }

    
    fun parseToJstIso8601(src: String, srcPattern: String): String? {
        return parseAndFormatToJst(src, srcPattern, "yyyy-MM-dd'T'HH:mm:ssXXX")
    }

    
    fun calendarInJst(timeMillis: Long = System.currentTimeMillis()): Calendar {
        return Calendar.getInstance(JST_TIMEZONE).apply { this.timeInMillis = timeMillis }
    }

    
    /**
     * 日時文字列をJST基準のミリ秒タイムスタンプに厳密パースします。
     *
     * @param src 解析する日時文字列
     * @param pattern パースに使用するフォーマットパターン文字列
     * @param locale 使用ロケール (デフォルト: Locale.US)
     * @return 成功時はJSTのミリ秒タイムスタンプ、失敗時はnull
     */
    fun parseJstMillis(src: String, pattern: String, locale: Locale = Locale.US): Long? {
        return runCatching {
            val parsed = parseWithFormatterFallback(src, pattern, locale)
            parsed.toJstMillis()
        }.getOrNull()
    }

    /**
     * 日時文字列をJST基準のミリ秒にパースします。国土交通省の.datファイルで時折用いられる、
     * 「24:00」という特殊な深夜時間表示（例: "2026/06/01 24:00"）を「翌日の00:00」に自動補正・進捗させてパースします。
     *
     * @param src 解析する日時文字列
     * @param pattern パース用パターン
     * @param locale ロケール
     * @return 成功時はJSTのミリ秒タイムスタンプ、失敗時はnull
     */
    fun parseJstMillisAllow24Hour(src: String, pattern: String, locale: Locale = Locale.US): Long? {
        parseJstMillis(src, pattern, locale)?.let { return it }
        val spaceIndex = src.indexOf(' ')
        val patternSpaceIndex = pattern.indexOf(' ')
        if (spaceIndex < 0 || patternSpaceIndex < 0) return null
        if (src.substring(spaceIndex + 1) != "24:00") return null

        val datePattern = pattern.substring(0, patternSpaceIndex)
        val date = runCatching {
            LocalDate.from(parseWithFormatterFallback(src.substring(0, spaceIndex), datePattern, locale))
        }.getOrNull() ?: return null
        return date.plusDays(1)
            .atStartOfDay(JST_ZONE)
            .toInstant()
            .toEpochMilli()
    }

    private fun parseWithFormatterFallback(
        src: String,
        pattern: String,
        locale: Locale
    ): TemporalAccessor {
        return runCatching {
            formatter(pattern, locale, forParsing = true).parse(src)
        }.getOrElse {
            val relaxedPattern = relaxedParsingPattern(pattern)
            if (relaxedPattern == pattern) throw it
            formatter(relaxedPattern, locale, forParsing = true).parse(src)
        }
    }

    private fun formatter(
        pattern: String,
        locale: Locale,
        forParsing: Boolean = false
    ): DateTimeFormatter {
        val javaTimePattern = if (forParsing) pattern.replace("yyyy", "uuuu") else pattern
        return DateTimeFormatter.ofPattern(javaTimePattern, locale)
            .withResolverStyle(ResolverStyle.STRICT)
            .withZone(JST_ZONE)
    }

    private fun relaxedParsingPattern(pattern: String): String {
        return if ('/' in pattern || '-' in pattern || ':' in pattern) {
            pattern
                .replace("MM", "M")
                .replace("dd", "d")
                .replace("HH", "H")
                .replace("mm", "m")
                .replace("ss", "s")
        } else {
            pattern
        }
    }

    
    fun toUtcMidnightFromJstDay(millis: Long?): Long {
        val jstCal = Calendar.getInstance(JST_TIMEZONE).apply {
            timeInMillis = millis ?: System.currentTimeMillis()
        }
        return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            set(jstCal.get(Calendar.YEAR), jstCal.get(Calendar.MONTH), jstCal.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    
    fun utcMidnightToJstDayStart(utcMillis: Long): Long {
        val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = utcMillis
        }
        return Calendar.getInstance(JST_TIMEZONE).apply {
            set(utcCal.get(Calendar.YEAR), utcCal.get(Calendar.MONTH), utcCal.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun TemporalAccessor.toJstMillis(): Long {
        val date = if (isSupported(ChronoField.EPOCH_DAY)) {
            LocalDate.from(this)
        } else {
            null
        }
        val time = if (isSupported(ChronoField.NANO_OF_DAY)) {
            LocalTime.from(this)
        } else {
            null
        }
        return when {
            date != null && time != null -> LocalDateTime.of(date, time).atZone(JST_ZONE).toInstant().toEpochMilli()
            date != null -> date.atStartOfDay(JST_ZONE).toInstant().toEpochMilli()
            isSupported(ChronoField.INSTANT_SECONDS) -> Instant.from(this).toEpochMilli()
            else -> throw DateTimeParseException("Unsupported temporal fields", toString(), 0)
        }
    }
}
