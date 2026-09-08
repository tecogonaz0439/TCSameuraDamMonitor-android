// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.ui.main

import net.tecogonaz.tcsameuradammonitor.domain.model.DamHistoricalData
import net.tecogonaz.tcsameuradammonitor.domain.model.HistoricalComparisonData
import net.tecogonaz.tcsameuradammonitor.ui.common.MISSING_PERCENTAGE_TEXT
import net.tecogonaz.tcsameuradammonitor.ui.common.MISSING_TEXT
import net.tecogonaz.tcsameuradammonitor.util.TimeUtils

private const val MILLIS_PER_HOUR = 3600L * 1000L

internal fun parseGraphTimeMillis(time: String): Long? {
    val normalizedTime = normalizeTime(time)
    return TimeUtils.parseJstMillis(normalizedTime, "yyyy/MM/dd HH:mm")
}

internal fun filterRealtimeGraphData(
    historicalData: List<DamHistoricalData>,
    range: RealtimeGraphRange
): List<DamHistoricalData> {
    val hours = range.hours ?: return historicalData
    val timedData = historicalData.mapNotNull { data ->
        parseGraphTimeMillis(data.time)?.let { timeMillis -> data to timeMillis }
    }
    val latestMillis = timedData.maxOfOrNull { it.second } ?: return historicalData
    val startMillis = latestMillis - hours * MILLIS_PER_HOUR
    return timedData
        .filter { (_, timeMillis) -> timeMillis >= startMillis }
        .map { it.first }
}

internal fun realtimeGraphRangeOptionsForData(historicalData: List<DamHistoricalData>): List<RealtimeGraphRange> {
    val times = historicalData.mapNotNull { parseGraphTimeMillis(it.time) }
    val minMillis = times.minOrNull() ?: return listOf(RealtimeGraphRange.ALL)
    val maxMillis = times.maxOrNull() ?: return listOf(RealtimeGraphRange.ALL)
    val periodMillis = maxMillis - minMillis
    return buildList {
        add(RealtimeGraphRange.ALL)
        if (periodMillis > 72L * MILLIS_PER_HOUR) {
            add(RealtimeGraphRange.PAST_72_HOURS)
        }
        if (periodMillis > 48L * MILLIS_PER_HOUR) {
            add(RealtimeGraphRange.PAST_48_HOURS)
        }
        if (periodMillis > 24L * MILLIS_PER_HOUR) {
            add(RealtimeGraphRange.PAST_24_HOURS)
        }
    }
}

internal fun DamHistoricalData.hasAnyObservationValue(): Boolean =
    catchmentAverageRainfall != null ||
        storagePercentage != null ||
        storageVolume != null ||
        inflow != null ||
        outflow != null

private fun DamHistoricalData.hasAnyObservationValueExceptStoragePercentage(): Boolean =
    catchmentAverageRainfall != null ||
        storageVolume != null ||
        inflow != null ||
        outflow != null

private fun DamHistoricalData.shouldFillRealtimeStoragePercentage(
    lastStoragePercentage: Float?,
    lastStoragePercentageMillis: Long?,
    timeMillis: Long
): Boolean =
    storagePercentage == null &&
        hasAnyObservationValueExceptStoragePercentage() &&
        lastStoragePercentage != null &&
        lastStoragePercentageMillis != null &&
        timeMillis - lastStoragePercentageMillis <= MILLIS_PER_HOUR

internal fun buildStorageGraphTooltipText(
    timeStr: String,
    rainfallValue: Float?,
    storagePercentageValue: Float?,
    labelTooltipRainfall: String,
    labelTooltipStorageRate: String,
    rainfallUnitLabel: String,
    storagePercentageDecimals: Int,
    separator: String = " "
): String {
    val rainfallStr = if (rainfallValue != null) {
        String.format(java.util.Locale.US, "%.1f", rainfallValue)
    } else {
        MISSING_TEXT
    }
    val percentStrWithUnit = if (storagePercentageValue != null) {
        "${formatValue(storagePercentageValue, storagePercentageDecimals)}%"
    } else {
        MISSING_PERCENTAGE_TEXT
    }
    return "$timeStr$separator$labelTooltipRainfall ${rainfallStr}$rainfallUnitLabel$separator$labelTooltipStorageRate $percentStrWithUnit"
}

internal data class RealtimeGraphDisplayData(
    val data: List<DamHistoricalData>,
    val windowStartMillis: Long?,
    val windowEndMillis: Long?,
    /** 表示データの最新観測時刻（エポックミリ秒）。時刻パース不能データのみの場合は null */
    val latestMillis: Long? = null
)

internal fun resolveGraphXAxisRange(
    isHistorical: Boolean,
    historicalStartMillis: Long?,
    historicalEndMillis: Long?,
    realtimeStartMillis: Long?,
    realtimeEndMillis: Long?
): Pair<Long, Long>? {
    val startMillis = if (isHistorical) historicalStartMillis else realtimeStartMillis
    val endMillis = if (isHistorical) historicalEndMillis else realtimeEndMillis
    return if (startMillis != null && endMillis != null && startMillis < endMillis) {
        startMillis to endMillis
    } else {
        null
    }
}

/**
 * 過去比較グラフ表示（history mode）のX軸表示範囲を解決します。
 *
 * リアルタイムの期間選択で「全期間」を選んだ場合は [resolveGraphXAxisRange] の結果が null になるため、
 * 比較データが保持する期間（[HistoricalComparisonData.periodStartMillis] / [periodEndMillis]）で
 * フォールバックします。この期間はリポジトリが毎時軸を構築したウィンドウそのものであり、
 * 過去年の系列・Tooltip・X軸ラベルを他の期間選択と同じ経路で描画できます。
 * 期間指定（過去24/48/72時間）や通常modeでは [resolvedRange] をそのまま返します。
 *
 * リアルタイム表示中に自動更新で今年の線が比較データの期間終端を超えて延びた場合は、
 * 終端を [realtimeLatestMillis]（最新リアルタイム観測時刻）と比較データ期間終端の大きい方へ
 * 拡張する。これにより期間ラベル・X軸・今年の線の描画エリアが自動更新に追従し、
 * 今年の線がX軸の右側に飛び出すことを防ぐ。過去年の線は
 * [buildComparisonEdgeExtendedPoints] がdomain内にクランプするため描画エリア外には出ない。
 *
 * @param isComparisonMode 過去比較グラフ表示であるかどうか
 * @param resolvedRange 既存の [resolveGraphXAxisRange] の結果
 * @param comparisonPeriod 比較データの表示期間（開始・終了エポックミリ秒）
 * @param realtimeLatestMillis リアルタイム表示データの最新観測時刻（エポックミリ秒）。null許可
 * @return 使用するX軸範囲。利用できない場合は null
 */
internal fun resolveComparisonXAxisRange(
    isComparisonMode: Boolean,
    resolvedRange: Pair<Long, Long>?,
    comparisonPeriod: Pair<Long, Long>?,
    realtimeLatestMillis: Long? = null
): Pair<Long, Long>? =
    if (isComparisonMode && resolvedRange == null && comparisonPeriod != null &&
        comparisonPeriod.first < comparisonPeriod.second
    ) {
        val endMillis = maxOf(comparisonPeriod.second, realtimeLatestMillis ?: comparisonPeriod.second)
        comparisonPeriod.first to endMillis
    } else {
        resolvedRange
    }

/**
 * 過去比較グラフ表示（history mode）で過去年の線を描画するX軸範囲を解決します。
 *
 * 期間指定（過去24/48/72時間）の場合はX軸domainの終端から選択期間ぶん遡った範囲を、
 * 「全期間」の場合はdomain全体をそのまま返します。domainはリアルタイムの表示期間
 * （または比較データの期間フォールバック）に一致するため、過去年の系列・Tooltipと
 * 同じ座標系で描画できます。
 *
 * 過去比較グラフ表示でない場合やdomainが利用できない場合は null を返します。
 * この関数は毎回のコンポジションで再計算される前提の純粋関数です。
 * [remember] でキャッシュすると過去比較グラフ表示への切り替え時に古い null が
 * 残り、過去年の線が描画されなくなるため、必ずそのまま使ってください。
 *
 * @param isComparisonMode 過去比較グラフ表示であるかどうか
 * @param xAxisRangeMillis 表示domain（開始・終了エポックミリ秒）
 * @param realtimeRange リアルタイムの期間選択
 * @return 過去年の線を描画するX軸範囲。利用できない場合は null
 */
internal fun resolveComparisonRangeMillis(
    isComparisonMode: Boolean,
    xAxisRangeMillis: Pair<Long, Long>?,
    realtimeRange: RealtimeGraphRange
): Pair<Long, Long>? {
    if (!isComparisonMode || xAxisRangeMillis == null) return null
    val end = xAxisRangeMillis.second
    val start = when (realtimeRange) {
        RealtimeGraphRange.ALL -> xAxisRangeMillis.first
        else -> maxOf(xAxisRangeMillis.first, end - (realtimeRange.hours ?: 0L) * MILLIS_PER_HOUR)
    }
    return start to end
}

internal fun buildRealtimeGraphDisplayData(
    historicalData: List<DamHistoricalData>,
    range: RealtimeGraphRange
): RealtimeGraphDisplayData {
    val hours = range.hours
    val timedData = historicalData.mapNotNull { data ->
        parseGraphTimeMillis(data.time)?.let { timeMillis -> data to timeMillis }
    }.sortedBy { it.second }
    val latestMillis = timedData.maxOfOrNull { it.second }
        ?: return RealtimeGraphDisplayData(historicalData, null, null, null)
    if (hours == null) {
        return RealtimeGraphDisplayData(historicalData, null, null, latestMillis)
    }
    val startMillis = latestMillis - hours * MILLIS_PER_HOUR
    val inWindow = timedData
        .filter { (_, timeMillis) -> timeMillis >= startMillis && timeMillis <= latestMillis }
        .map { it.first }
    return RealtimeGraphDisplayData(inWindow, startMillis, latestMillis, latestMillis)
}

internal fun realtimeGraphScaleValues(
    allRangeValues: List<Float>,
    selectedRangeValues: List<Float>,
    isHistorical: Boolean
): List<Float> =
    if (isHistorical) selectedRangeValues else allRangeValues

/**
 * 貯水量グラフの左軸目盛（Y軸最大値）の計算に使う値を返します。
 *
 * Web版と同じく、過去比較グラフ表示では選択中の過去年の貯水量（正常値のみ）を
 * 今年の値へ連結します。選択年以外の過去年と、期間指定による切り詰めは行いません。
 *
 * @param isComparisonMode 過去比較グラフ表示であるかどうか
 * @param baseValues 今年の貯水量の目盛候補値（既存の [realtimeGraphScaleValues] の結果）
 * @param comparisonData 過去比較データ（null なら追加しない）
 * @param selectedPastYears 選択中の過去年の集合
 * @return 目盛最大値の計算に使う値のリスト
 */
internal fun buildComparisonVolumeScaleValues(
    isComparisonMode: Boolean,
    baseValues: List<Float>,
    comparisonData: HistoricalComparisonData?,
    selectedPastYears: Set<Int>
): List<Float> {
    if (!isComparisonMode || comparisonData == null) return baseValues
    val pastValues = comparisonData.series
        .filter { it.year in selectedPastYears }
        .flatMap { it.values.filterNotNull() }
    return baseValues + pastValues
}

/**
 * 過去年の毎時系列をX軸domainの左右端まで延長するためのクランプ点を返します。
 *
 * 毎時00分の観測点のみで構成される過去年の線が、domainの両端（例: 最新リアルタイム時刻や
 * それからN時間前の中間時刻）で欠けて見えるのを防ぎます。左端はdomain直前の毎時観測値を
 * [rangeStart]へクランプした点を先頭に加え、右端は末尾の毎時観測値を[rangeEnd]へクランプした
 * 点を末尾に加えます。明示欠測（null）は跨がず、domain外の点は含めません。
 * Apple版edgeExtendedPointsと同規則です。
 *
 * @param axis 1時間刻みの時刻軸（JST、昇順）。[values]とパラレル
 * @param values 各時刻の観測値。欠測・異常はnull
 * @param rangeStart 表示domainの開始エポックミリ秒（含む）
 * @param rangeEnd 表示domainの終了エポックミリ秒（含む）
 * @return domain内の点（nullは欠測として保持）に左右端のクランプ点を加えたリスト。
 *   domain内に点が無い場合は空リスト
 */
internal fun buildComparisonEdgeExtendedPoints(
    axis: List<Long>,
    values: List<Float?>,
    rangeStart: Long,
    rangeEnd: Long,
): List<Pair<Long, Float?>> {
    val firstIn = axis.indexOfFirst { it >= rangeStart && it <= rangeEnd }
    if (firstIn == -1) return emptyList()
    val lastIn = axis.indexOfLast { it >= rangeStart && it <= rangeEnd }
    val result = mutableListOf<Pair<Long, Float?>>()
    if (axis[firstIn] > rangeStart && firstIn > 0 &&
        values.getOrNull(firstIn - 1) != null && values.getOrNull(firstIn) != null
    ) {
        result.add(rangeStart to values[firstIn - 1]!!)
    }
    for (index in firstIn..lastIn) {
        result.add(axis[index] to values.getOrNull(index))
    }
    if (axis[lastIn] < rangeEnd && values.getOrNull(lastIn) != null) {
        result.add(rangeEnd to values[lastIn]!!)
    }
    return result
}

/**
 * 過去比較グラフの表示domain内で線が実際に描かれる年を返します。
 *
 * [buildComparisonEdgeExtendedPoints] の描画条件（表示domain内に少なくとも1つの正常観測値が
 * 存在する）と同じ条件で判定する。左右端のクランプ点も正常観測値を起点とするため、
 * domain内に正常値が1つも無い年は線が描かれず、ここにも含まれない。
 * mode 3/4のツールチップ比較対象を「表示中のラインの年」だけに絞るために使う。
 *
 * @param comparisonData 過去比較データ
 * @param rangeStart 表示domainの開始エポックミリ秒（含む）
 * @param rangeEnd 表示domainの終了エポックミリ秒（含む）
 * @return 表示domain内で線が描かれる年の集合
 */
internal fun comparisonLinesDrawnYears(
    comparisonData: HistoricalComparisonData,
    rangeStart: Long,
    rangeEnd: Long
): Set<Int> {
    val axis = comparisonData.hourlyAxisMillis
    return comparisonData.series
        .filter { series ->
            series.values.indices.any { index ->
                val millis = axis.getOrNull(index) ?: return@any false
                millis in rangeStart..rangeEnd && series.values[index] != null
            }
        }
        .mapTo(mutableSetOf()) { it.year }
}

internal data class RealtimeStorageGraphDisplayData(
    val data: List<DamHistoricalData>,
    val windowStartMillis: Long?,
    val windowEndMillis: Long?,
    /** 表示データの最新観測時刻（エポックミリ秒）。時刻パース不能データのみの場合は null */
    val latestMillis: Long? = null
)

internal fun buildRealtimeStorageGraphDisplayData(
    historicalData: List<DamHistoricalData>,
    range: RealtimeGraphRange
): RealtimeStorageGraphDisplayData {
    val hours = range.hours
    val timedData = historicalData.mapNotNull { data ->
        parseGraphTimeMillis(data.time)?.let { timeMillis -> data to timeMillis }
    }.sortedBy { it.second }
    val latestMillis = timedData.maxOfOrNull { it.second }
        ?: return RealtimeStorageGraphDisplayData(historicalData, null, null, null)

    if (hours == null) {
        val completedData = completeStoragePercentage(timedData)
        return RealtimeStorageGraphDisplayData(completedData, null, null, latestMillis)
    }

    val startMillis = latestMillis - hours * MILLIS_PER_HOUR
    val inWindow = timedData.filter { (_, timeMillis) -> timeMillis >= startMillis && timeMillis <= latestMillis }

    val previousValid = timedData
        .asReversed()
        .firstOrNull { (data, timeMillis) ->
            timeMillis < startMillis &&
                startMillis - timeMillis <= MILLIS_PER_HOUR &&
                data.storagePercentage != null
        }

    val result = mutableListOf<DamHistoricalData>()
    var lastStoragePercentage = previousValid?.first?.storagePercentage
    var lastStoragePercentageMillis = previousValid?.second

    val firstWindowMillis = inWindow.firstOrNull()?.second
    if (
        previousValid != null &&
        lastStoragePercentage != null &&
        (firstWindowMillis == null || firstWindowMillis > startMillis)
    ) {
        result.add(
            previousValid.first.copy(
                time = TimeUtils.formatToJst(startMillis, "yyyy/MM/dd HH:mm"),
                catchmentAverageRainfall = null,
                storagePercentage = lastStoragePercentage,
                storageVolume = null,
                inflow = null,
                outflow = null
            )
        )
        lastStoragePercentageMillis = startMillis
    }

    result.addAll(
        completeStoragePercentage(inWindow, lastStoragePercentage, lastStoragePercentageMillis)
    )

    return RealtimeStorageGraphDisplayData(
        data = result,
        windowStartMillis = startMillis,
        windowEndMillis = latestMillis,
        latestMillis = latestMillis
    )
}

private fun completeStoragePercentage(
    timedData: List<Pair<DamHistoricalData, Long>>,
    initialStoragePercentage: Float? = null,
    initialStoragePercentageMillis: Long? = null
): List<DamHistoricalData> {
    val result = mutableListOf<DamHistoricalData>()
    var lastStoragePercentage = initialStoragePercentage
    var lastStoragePercentageMillis = initialStoragePercentageMillis

    timedData.forEach { (data, timeMillis) ->
        val displayData = if (data.shouldFillRealtimeStoragePercentage(
                lastStoragePercentage = lastStoragePercentage,
                lastStoragePercentageMillis = lastStoragePercentageMillis,
                timeMillis = timeMillis
            )
        ) {
            data.copy(storagePercentage = lastStoragePercentage)
        } else {
            data
        }
        result.add(displayData)
        if (displayData.storagePercentage != null) {
            lastStoragePercentage = displayData.storagePercentage
            lastStoragePercentageMillis = timeMillis
        }
    }

    return result
}
