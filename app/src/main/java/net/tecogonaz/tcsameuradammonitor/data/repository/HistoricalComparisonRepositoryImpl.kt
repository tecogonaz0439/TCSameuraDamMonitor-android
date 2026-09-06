// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.data.repository

import arrow.core.Either
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import net.tecogonaz.tcsameuradammonitor.data.source.local.HistoricalComparisonAssetStore
import net.tecogonaz.tcsameuradammonitor.domain.model.AppSettings
import net.tecogonaz.tcsameuradammonitor.domain.model.DamHistoricalData
import net.tecogonaz.tcsameuradammonitor.domain.model.HistoricalComparisonData
import net.tecogonaz.tcsameuradammonitor.domain.model.HistoricalComparisonMetric
import net.tecogonaz.tcsameuradammonitor.domain.model.HistoricalComparisonSeries
import net.tecogonaz.tcsameuradammonitor.domain.repository.HistoricalComparisonRepository
import net.tecogonaz.tcsameuradammonitor.domain.repository.SudmonitorHistoryRepository
import net.tecogonaz.tcsameuradammonitor.util.TimeUtils
import net.tecogonaz.tcsameuradammonitor.util.catchNonCancellationSuspend
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 過去比較グラフ用のダム諸量データを、バンドルassets（基本ソース）から読み込んで提供するリポジトリ実装。
 *
 * 早明浦ダム（[AppSettings.DEFAULT_DAM_ID]）以外はassetsを一切読まずに拒否する。
 * 主系列年指定時（通常の過去データ表示）のみ、JST現在年の比較系列へ読込済み日次過去データ
 * （[SudmonitorHistoryRepository]経由。未読込・未注入時はバンドルのみ）を上書き合成する。
 * [clock]はJST現在年の算出にのみ使用する。
 *
 * @property assetStore バンドルassets内の過去datファイルを読み込むストア
 * @property clock 現在時刻（ミリ秒）を返すクロック
 * @property sudmonitorHistoryRepository 読込済み日次過去データの読出しに使うリポジトリ（任意）
 */
@Singleton
class HistoricalComparisonRepositoryImpl(
    private val assetStore: HistoricalComparisonAssetStore,
    private val clock: () -> Long = System::currentTimeMillis,
    private val sudmonitorHistoryRepository: SudmonitorHistoryRepository? = null
) : HistoricalComparisonRepository {

    @Inject
    constructor(
        assetStore: HistoricalComparisonAssetStore,
        sudmonitorHistoryRepository: SudmonitorHistoryRepository
    ) : this(assetStore, System::currentTimeMillis, sudmonitorHistoryRepository)

    /**
     * 指定された期間の過去比較データを取得します。
     *
     * 1時間刻みの時刻軸を構築し、各比較対象年について同一期間（月日・時刻）をassetsから読み込んで
     * 時刻軸上へ逆写像します。[mainYear]指定時はJST現在年も比較対象年に含め、JST現在年の系列には
     * 読込済み日次過去データ（非null値のみ・JST時floorで軸へ写像）を上書き合成します。
     * 対象年が無い・軸が空の場合は正常な空データを返し、
     * 読み込み失敗は部分グラフを作らず全体を失敗として扱います。
     *
     * @param damId 対象ダムの観測所ID（早明浦以外は拒否）
     * @param metric 比較対象のダム諸量（貯水率または貯水量）
     * @param windowStartMillis 比較期間の開始エポックミリ秒（含む）
     * @param windowEndMillis 比較期間の終了エポックミリ秒（含む）
     * @param mainYear 主系列年。未指定時はJST現在年を主系列年とする従来挙動
     * @return 成功時は[HistoricalComparisonData]、失敗時はエラーを包んだ[Either]
     */
    override suspend fun loadComparison(
        damId: String,
        metric: HistoricalComparisonMetric,
        windowStartMillis: Long,
        windowEndMillis: Long,
        mainYear: Int?
    ): Either<Throwable, HistoricalComparisonData> = catchNonCancellationSuspend {
        if (damId != AppSettings.DEFAULT_DAM_ID) {
            throw IllegalArgumentException("historical comparison is only available for Sameura Dam")
        }
        if (windowEndMillis < windowStartMillis) {
            throw IllegalArgumentException("windowEndMillis must be greater than or equal to windowStartMillis")
        }
        val clockYear = currentJstYear(clock())
        val baseYear = mainYear ?: clockYear
        val pastYears = if (mainYear == null) {
            availablePastYearsFor(baseYear)
        } else {
            (HistoricalComparisonAssetStore.FIRST_YEAR..clockYear).filterNot { it == baseYear }.toList()
        }
        // 日次過去データはJST現在年の比較系列の合成にのみ使う。合成対象（clockYear）が
        // 比較対象年に含まれる場合のみ、一度だけ読込む。
        val dailyRows = if (clockYear != baseYear) {
            sudmonitorHistoryRepository?.getAllObservations(damId) ?: emptyList()
        } else {
            emptyList()
        }
        val hourlyAxis = buildHourlyAxisMillis(windowStartMillis, windowEndMillis)
        if (hourlyAxis.isEmpty()) {
            HistoricalComparisonData(
                currentYear = baseYear,
                availablePastYears = pastYears,
                periodStartMillis = windowStartMillis,
                periodEndMillis = windowEndMillis,
                hourlyAxisMillis = emptyList(),
                series = emptyList()
            )
        } else {
            val series = if (pastYears.isEmpty()) {
                emptyList()
            } else {
                val semaphore = Semaphore(MAX_CONCURRENT_YEARS)
                val results = coroutineScope {
                    pastYears.map { year ->
                        async(Dispatchers.IO) {
                            semaphore.withPermit {
                                loadYearSeries(
                                    year,
                                    baseYear,
                                    clockYear,
                                    metric,
                                    hourlyAxis,
                                    dailyRows = if (year == clockYear) dailyRows else emptyList()
                                )
                            }
                        }
                    }.awaitAll()
                }
                val firstLeft = results.firstOrNull { it is Either.Left }
                if (firstLeft is Either.Left) {
                    throw firstLeft.value
                }
                results.mapNotNull { (it as? Either.Right)?.value }
            }
            HistoricalComparisonData(
                currentYear = baseYear,
                availablePastYears = pastYears,
                periodStartMillis = windowStartMillis,
                periodEndMillis = windowEndMillis,
                hourlyAxisMillis = hourlyAxis,
                series = series
            )
        }
    }

    /**
     * 1年分の時系列を構築します。
     *
     * 時刻軸の両端を対象年へ写像し、その範囲をassetsから読み込んで時刻軸上へ逆写像します。
     * [dailyRows]が空でない場合（JST現在年の系列のみ、呼び出し側が指定）は、日次過去データの
     * 非null値をバンドル値の上に合成します（時刻は24:00表記を許容し、JST時floorで軸へ写像。
     * 軸外・写像不可の行は無視する）。
     * 写像が成立しない・範囲が空の場合は全nullの時系列を返します。
     */
    private suspend fun loadYearSeries(
        year: Int,
        baseYear: Int,
        clockYear: Int,
        metric: HistoricalComparisonMetric,
        hourlyAxis: List<Long>,
        dailyRows: List<DamHistoricalData> = emptyList()
    ): Either<Throwable, HistoricalComparisonSeries> {
        val axisHead = hourlyAxis.first()
        val axisLast = hourlyAxis.last()
        val sourceStart = resolveMappedBoundary(axisHead, baseYear, year, +1)
        val sourceEnd = resolveMappedBoundary(axisLast, baseYear, year, -1)
        if (sourceStart == null || sourceEnd == null || sourceEnd < sourceStart) {
            return Either.Right(
                HistoricalComparisonSeries(
                    year,
                    overlayDailyRows(List(hourlyAxis.size) { null }, metric, hourlyAxis, clockYear, baseYear, dailyRows)
                )
            )
        }
        return assetStore.loadRange(sourceStart, sourceEnd).map { points ->
            val valuesByAxis = points.mapNotNull { point ->
                val mapped = mapYearFromComparison(point.epochMillis, year, baseYear)
                if (mapped == null) {
                    null
                } else {
                    val value = when (metric) {
                        HistoricalComparisonMetric.STORAGE_RATE -> point.storageRate
                        HistoricalComparisonMetric.STORAGE_VOLUME -> point.storageVolume
                    }
                    mapped to value
                }
            }.toMap()
            val bundledValues = hourlyAxis.map { axisMillis -> valuesByAxis[axisMillis] }
            HistoricalComparisonSeries(
                year = year,
                values = overlayDailyRows(bundledValues, metric, hourlyAxis, clockYear, baseYear, dailyRows)
            )
        }
    }

    /**
     * バンドル由来の時系列値の上に、日次過去データの非null値（品質正常のみ）を合成します。
     *
     * 各行の時刻は24:00表記を許容してJSTでパースし、JST時floorの後、[clockYear]→[baseYear]の
     * 年写像で時刻軸上のindexへ対応付けます。対応先にのみ上書きし、バンドル・日次の両方に
     * 値が無い期間はnull（ライン途切れ）のままです。
     */
    private fun overlayDailyRows(
        values: List<Float?>,
        metric: HistoricalComparisonMetric,
        hourlyAxis: List<Long>,
        clockYear: Int,
        baseYear: Int,
        dailyRows: List<DamHistoricalData>
    ): List<Float?> {
        if (dailyRows.isEmpty()) return values
        val overlaid = values.toMutableList()
        for (row in dailyRows) {
            val millis = TimeUtils.parseJstMillisAllow24Hour(row.time, "yyyy/MM/dd HH:mm") ?: continue
            val floored = floorToJstHourMillis(millis)
            val mapped = mapYearToComparison(floored, clockYear, baseYear) ?: continue
            val index = hourlyAxis.indexOf(mapped)
            if (index < 0) continue
            val dailyValue = when (metric) {
                HistoricalComparisonMetric.STORAGE_RATE -> row.storagePercentage
                HistoricalComparisonMetric.STORAGE_VOLUME -> row.storageVolume
            } ?: continue
            overlaid[index] = dailyValue
        }
        return overlaid
    }

    private companion object {
        /** 過去年の時系列構築の最大並列数 */
        const val MAX_CONCURRENT_YEARS = 4
    }
}

/**
 * 現在年がデータ開始年以下かどうかを判定し、比較可能な過去年の昇順リストを返します。
 *
 * @param currentYear 現在年（JST基準）
 * @return データ開始年から現在年の前年までの年リスト。現在年が開始年以下の場合は空
 */
internal fun availablePastYearsFor(currentYear: Int): List<Int> =
    if (currentYear <= HistoricalComparisonAssetStore.FIRST_YEAR) {
        emptyList()
    } else {
        (HistoricalComparisonAssetStore.FIRST_YEAR until currentYear).toList()
    }

/**
 * JST基準で現在の年を返します。
 *
 * @param clockMillis 現在時刻のエポックミリ秒
 * @return JSTでの年
 */
internal fun currentJstYear(clockMillis: Long): Int =
    Instant.ofEpochMilli(clockMillis).atZone(TimeUtils.JST_ZONE).year

/**
 * エポックミリ秒をJSTの1時間刻み（分以下0）へ切り捨てます。
 *
 * @param millis 切り捨て対象のエポックミリ秒
 * @return JSTで分・秒・ナノ秒が0のエポックミリ秒
 */
internal fun floorToJstHourMillis(millis: Long): Long =
    Instant.ofEpochMilli(millis)
        .atZone(TimeUtils.JST_ZONE)
        .withMinute(0)
        .withSecond(0)
        .withNano(0)
        .toInstant()
        .toEpochMilli()

/**
 * 両端を含む1時間刻みの時刻軸をJST基準で構築します。
 *
 * @param startMillis 開始エポックミリ秒
 * @param endMillis 終了エポックミリ秒
 * @return 昇順の1時間刻みエポックミリ秒リスト。切り捨て後の開始が終了より後の場合は空
 */
internal fun buildHourlyAxisMillis(startMillis: Long, endMillis: Long): List<Long> {
    val first = floorToJstHourMillis(startMillis)
    val last = floorToJstHourMillis(endMillis)
    if (first > last) return emptyList()
    return buildList {
        var current = first
        while (current <= last) {
            add(current)
            current += HOUR_MILLIS
        }
    }
}

/**
 * エポックミリ秒の年月日・時刻をJSTのまま、年だけを別の年へ置き換えます。
 *
 * 月日と時刻は保持されるため、うるう日の2/29を非うるう年へ写像する場合は
 * [DateTimeException]となりnullを返します（2/28や3/1への丸めは行わない）。
 *
 * @param epochMillis 写像対象のエポックミリ秒
 * @param baseYear 写像元の基準年
 * @param targetYear 写像先の年
 * @return 写像後のエポックミリ秒。写像できない場合はnull
 */
internal fun mapYearToComparison(epochMillis: Long, baseYear: Int, targetYear: Int): Long? {
    val jst = Instant.ofEpochMilli(epochMillis).atZone(TimeUtils.JST_ZONE)
    val mappedYear = targetYear + (jst.year - baseYear)
    return runCatching {
        LocalDate.of(mappedYear, jst.monthValue, jst.dayOfMonth)
            .atTime(jst.toLocalTime())
            .atZone(TimeUtils.JST_ZONE)
            .toInstant()
            .toEpochMilli()
    }.getOrNull()
}

/**
 * 年写像が失敗する場合のみ、指定方向（+1=翌日、-1=前日）へ最大3日歩いて成功する最初の日時を返します。
 *
 * @param epochMillis 写像対象のエポックミリ秒
 * @param baseYear 写像元の基準年
 * @param targetYear 写像先の年
 * @param direction 歩く方向（1なら+1日、-1なら-1日）
 * @return 成功した最初の写像結果。全て失敗した場合はnull
 */
internal fun resolveMappedBoundary(epochMillis: Long, baseYear: Int, targetYear: Int, direction: Int): Long? {
    var current = Instant.ofEpochMilli(epochMillis).atZone(TimeUtils.JST_ZONE).toLocalDateTime()
    for (step in 0..3) {
        val mapped = mapYearToComparison(
            current.atZone(TimeUtils.JST_ZONE).toInstant().toEpochMilli(),
            baseYear,
            targetYear
        )
        if (mapped != null) return mapped
        current = current.plusDays(direction.toLong())
    }
    return null
}

/**
 * 過去年の観測時刻を現在年の時刻軸へ逆写像します。
 *
 * @param epochMillis 過去年の観測エポックミリ秒
 * @param comparisonYear 観測が属する過去年
 * @param baseYear 現在年
 * @return 現在年の時刻軸上のエポックミリ秒。写像できない場合はnull
 */
internal fun mapYearFromComparison(epochMillis: Long, comparisonYear: Int, baseYear: Int): Long? =
    mapYearToComparison(epochMillis, comparisonYear, baseYear)

private const val HOUR_MILLIS = 60L * 60L * 1000L
