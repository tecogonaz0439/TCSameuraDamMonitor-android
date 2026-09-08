// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.ui.main

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.focused
import androidx.compose.ui.semantics.stateDescription

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.tecogonaz.tcsameuradammonitor.BuildConfig
import net.tecogonaz.tcsameuradammonitor.data.repository.floorToJstHourMillis
import net.tecogonaz.tcsameuradammonitor.domain.model.HistoricalComparisonData
import net.tecogonaz.tcsameuradammonitor.domain.model.HistoricalComparisonMetric
import net.tecogonaz.tcsameuradammonitor.ui.common.MISSING_PERCENTAGE_TEXT
import net.tecogonaz.tcsameuradammonitor.ui.common.MISSING_TEXT
import net.tecogonaz.tcsameuradammonitor.R
import net.tecogonaz.tcsameuradammonitor.domain.model.DamHistoricalData
import net.tecogonaz.tcsameuradammonitor.util.LocaleUtils
import net.tecogonaz.tcsameuradammonitor.util.TimeUtils
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.ceil
import kotlin.math.floor

import kotlin.math.log10
import kotlin.math.pow

private const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L
private const val MILLIS_PER_HOUR = 3600L * 1000L
private const val MILLIS_PER_HOUR_L = 3600L * 1000L

private const val GRAPH_MIN_HEIGHT_RATIO = 0.5f
private val GRAPH_MIN_HEIGHT = 250.dp
private val GRAPH_MAX_HEIGHT = 420.dp
// Canvas内マージンを考慮した最小高さ
private val GRAPH_CANVAS_MIN_HEIGHT = 80.dp
private const val MILLIS_PER_DAY_L = 24L * MILLIS_PER_HOUR_L
private const val MILLIS_PER_WEEK_L = 7L * MILLIS_PER_DAY_L
private const val MILLIS_PER_2_WEEKS_L = 14L * MILLIS_PER_DAY_L

internal fun observationGraphHeight(width: Dp, maxHeight: Dp? = null): Dp {
    val base = maxOf(GRAPH_MIN_HEIGHT, minOf(width * GRAPH_MIN_HEIGHT_RATIO, GRAPH_MAX_HEIGHT))
    return maxHeight?.let { minOf(base, maxOf(GRAPH_CANVAS_MIN_HEIGHT, it)) } ?: base
}

fun normalizeTime(time: String): String {
    val spaceIdx = time.indexOf(' ')
    if (spaceIdx < 0) return time
    val dateStr = time.substring(0, spaceIdx)
    val timeStr = time.substring(spaceIdx + 1)
    return try {
        
        if (timeStr == "24:00") {
            val dateMillis = TimeUtils.parseJstMillis(dateStr, "yyyy/M/d") ?: return time
            "${TimeUtils.formatToJst(dateMillis + MILLIS_PER_DAY, "yyyy/MM/dd")} 00:00"
        } else {
            val dateMillis = TimeUtils.parseJstMillis(dateStr, "yyyy/M/d") ?: return time
            "${TimeUtils.formatToJst(dateMillis, "yyyy/MM/dd")} $timeStr"
        }
    } catch (e: Exception) {
        if (BuildConfig.DEBUG) {
            android.util.Log.w("DamGraphCard", "時刻正規化に失敗: $time", e)
        }
        time
    }
}


private enum class XAxisGranularity {
    HOURLY,
    SIX_HOURLY,
    TWELVE_HOURLY,
    DAILY,
    WEEKLY
}


private enum class XAxisLabelGranularity {
    HOURLY_00,
    SIX_HOURLY,
    TWELVE_HOURLY,
    DAILY,
    EVERY_2_DAYS,
    WEEKLY_MON,
    EVERY_2_WEEKS_MON
}


private const val LINE_BREAK_GAP_THRESHOLD = 6


private const val LINE_BREAK_GAP_MILLIS = 3L * 3600L * 1000L


fun computeNiceStep(range: Float): Float {
    if (range <= 0f) return 1f
    val rawStep = range / 5f
    val magnitude = 10.0.pow(floor(log10(rawStep.toDouble()))).toFloat()
    val fraction = rawStep / magnitude
    return when {
        fraction <= 1f -> magnitude
        fraction <= 2f -> 2f * magnitude
        fraction <= 5f -> 5f * magnitude
        else -> 10f * magnitude
    }
}




private const val RAINFALL_Y_MAX_DEFAULT = 10f


const val TOOLTIP_BACKGROUND_ALPHA = 0.75f

private data class XAxisTickConfig(
    val gridGranularity: XAxisGranularity,
    val tickGranularity: XAxisGranularity,
    val labelGranularity: XAxisLabelGranularity
)

private fun selectXAxisConfig(periodMillis: Long): XAxisTickConfig = when {
    periodMillis <= MILLIS_PER_DAY_L -> XAxisTickConfig(
        gridGranularity = XAxisGranularity.HOURLY,
        tickGranularity = XAxisGranularity.HOURLY,
        labelGranularity = XAxisLabelGranularity.DAILY
    )
    periodMillis <= 2L * MILLIS_PER_DAY_L -> XAxisTickConfig(
        gridGranularity = XAxisGranularity.SIX_HOURLY,
        tickGranularity = XAxisGranularity.SIX_HOURLY,
        labelGranularity = XAxisLabelGranularity.DAILY
    )
    periodMillis <= 7L * MILLIS_PER_DAY_L -> XAxisTickConfig(
        gridGranularity = XAxisGranularity.TWELVE_HOURLY,
        tickGranularity = XAxisGranularity.TWELVE_HOURLY,
        labelGranularity = XAxisLabelGranularity.DAILY
    )
    periodMillis <= MILLIS_PER_2_WEEKS_L -> XAxisTickConfig(
        gridGranularity = XAxisGranularity.DAILY,
        tickGranularity = XAxisGranularity.DAILY,
        labelGranularity = XAxisLabelGranularity.DAILY
    )
    else -> XAxisTickConfig(
        gridGranularity = XAxisGranularity.WEEKLY,
        tickGranularity = XAxisGranularity.DAILY,
        labelGranularity = XAxisLabelGranularity.WEEKLY_MON
    )
}

private fun buildXAxisData(
    dataPoints: List<Pair<String, Float?>>,
    isHistorical: Boolean,
    useTimeBasedX: Boolean,
    historicalSearchStartMillis: Long?,
    historicalSearchEndMillis: Long?,
    pointMillisList: List<Long>
): Triple<List<Float>, List<Float>, List<Pair<Float, String>>> {
    if (dataPoints.isEmpty()) {
        return Triple(emptyList(), emptyList(), emptyList())
    }
    val startMillis: Long
    val endMillis: Long
    if (useTimeBasedX && historicalSearchStartMillis != null && historicalSearchEndMillis != null) {
        startMillis = historicalSearchStartMillis
        endMillis = historicalSearchEndMillis
    } else {
        val firstMillis = pointMillisList.firstOrNull()?.takeIf { it != 0L }
            ?: parseGraphTimeMillis(dataPoints.first().first) ?: 0L
        val lastMillis = pointMillisList.lastOrNull()?.takeIf { it != 0L }
            ?: parseGraphTimeMillis(dataPoints.last().first) ?: 0L
        startMillis = firstMillis
        endMillis = lastMillis
    }
    if (startMillis == 0L || endMillis == 0L || startMillis >= endMillis) {
        return Triple(emptyList(), emptyList(), emptyList())
    }
    val periodMillis = endMillis - startMillis
    val tickConfig = selectXAxisConfig(periodMillis)
    val periodF = periodMillis.toFloat()
    val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Tokyo"))

    
    fun alignCal(gran: XAxisGranularity) {
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        when (gran) {
            XAxisGranularity.HOURLY -> cal.set(Calendar.MINUTE, 0)
            XAxisGranularity.SIX_HOURLY -> {
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.HOUR_OF_DAY, (cal.get(Calendar.HOUR_OF_DAY) / 6) * 6)
            }
            XAxisGranularity.TWELVE_HOURLY -> {
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.HOUR_OF_DAY, (cal.get(Calendar.HOUR_OF_DAY) / 12) * 12)
            }
            XAxisGranularity.DAILY -> {
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
            }
            XAxisGranularity.WEEKLY -> {
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                while (cal.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
                    cal.add(Calendar.DAY_OF_MONTH, -1)
                }
            }
        }
    }

    fun stepMillis(gran: XAxisGranularity): Long = when (gran) {
        XAxisGranularity.HOURLY -> MILLIS_PER_HOUR_L
        XAxisGranularity.SIX_HOURLY -> 6L * MILLIS_PER_HOUR_L
        XAxisGranularity.TWELVE_HOURLY -> 12L * MILLIS_PER_HOUR_L
        XAxisGranularity.DAILY -> MILLIS_PER_DAY_L
        XAxisGranularity.WEEKLY -> MILLIS_PER_WEEK_L
    }

    fun labelMatches(lg: XAxisLabelGranularity): Boolean = when (lg) {
        XAxisLabelGranularity.HOURLY_00 -> cal.get(Calendar.MINUTE) == 0
        XAxisLabelGranularity.SIX_HOURLY -> cal.get(Calendar.MINUTE) == 0 && cal.get(Calendar.HOUR_OF_DAY) % 6 == 0
        XAxisLabelGranularity.TWELVE_HOURLY -> cal.get(Calendar.MINUTE) == 0 && cal.get(Calendar.HOUR_OF_DAY) % 12 == 0
        XAxisLabelGranularity.DAILY -> cal.get(Calendar.HOUR_OF_DAY) == 0 && cal.get(Calendar.MINUTE) == 0
        XAxisLabelGranularity.EVERY_2_DAYS -> cal.get(Calendar.HOUR_OF_DAY) == 0 && cal.get(Calendar.MINUTE) == 0 && cal.get(Calendar.DAY_OF_YEAR) % 2 == 1
        XAxisLabelGranularity.WEEKLY_MON -> cal.get(Calendar.HOUR_OF_DAY) == 0 && cal.get(Calendar.MINUTE) == 0 && cal.get(Calendar.DAY_OF_WEEK) == Calendar.MONDAY
        XAxisLabelGranularity.EVERY_2_WEEKS_MON -> cal.get(Calendar.HOUR_OF_DAY) == 0 && cal.get(Calendar.MINUTE) == 0 && cal.get(Calendar.DAY_OF_WEEK) == Calendar.MONDAY && cal.get(Calendar.WEEK_OF_YEAR) % 2 == 0
    }

    fun tickLabel(lg: XAxisLabelGranularity): String = when (lg) {
        XAxisLabelGranularity.HOURLY_00, XAxisLabelGranularity.SIX_HOURLY, XAxisLabelGranularity.TWELVE_HOURLY ->
            String.format(java.util.Locale.US, "%02d:00", cal.get(Calendar.HOUR_OF_DAY))
        else -> {
            val dayStr = TimeUtils.formatToJst(cal.timeInMillis, "yyyy/MM/dd")
            dayStr.substringAfter("/")
        }
    }

    val gridData = mutableListOf<Float>()
    val tickData = mutableListOf<Float>()
    val labelData = mutableListOf<Pair<Float, String>>()
    val gridStep = stepMillis(tickConfig.gridGranularity)
    val tickStep = stepMillis(tickConfig.tickGranularity)

    
    cal.timeInMillis = startMillis
    alignCal(tickConfig.gridGranularity)
    if (cal.timeInMillis < startMillis) cal.timeInMillis += gridStep
    while (cal.timeInMillis <= endMillis) {
        val frac = ((cal.timeInMillis - startMillis) / periodF).coerceIn(0f, 1f)
        gridData.add(frac)
        cal.timeInMillis += gridStep
    }

    
    cal.timeInMillis = startMillis
    alignCal(tickConfig.tickGranularity)
    if (cal.timeInMillis < startMillis) cal.timeInMillis += tickStep
    while (cal.timeInMillis <= endMillis) {
        val frac = ((cal.timeInMillis - startMillis) / periodF).coerceIn(0f, 1f)
        tickData.add(frac)
        if (labelMatches(tickConfig.labelGranularity)) {
            labelData.add(frac to tickLabel(tickConfig.labelGranularity))
        }
        cal.timeInMillis += tickStep
    }

    return Triple(gridData, tickData, labelData)
}


@Composable
private fun GraphCardScaffold(
    embedded: Boolean,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    title: String,
    modifier: Modifier,
    content: @Composable () -> Unit
) {
    if (embedded) {
        Column(modifier = modifier) {
            content()
        }
    } else {
        Card(
            modifier = modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .weight(1f)
                            .semantics { heading() }
                    )
                    IconButton(onClick = onToggleExpand) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                            contentDescription = if (isExpanded) stringResource(R.string.desc_collapse) else stringResource(R.string.desc_expand),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                if (isExpanded) {
                    content()
                }
            }
        }
    }
}

@Composable
internal fun DamGraphCard(
    historicalData: List<DamHistoricalData>,
    modifier: Modifier = Modifier,
    graphType: GraphType = GraphType.STORAGE_PERCENTAGE,
    title: String,
    showJstSuffix: Boolean = false,
    isHistorical: Boolean = false,
    rainfallUnitLabel: String = "(mm/10min)",
    historicalSearchStartMillis: Long? = null,
    historicalSearchEndMillis: Long? = null,
    embedded: Boolean = false,
    embeddedRealtimeRange: RealtimeGraphRange? = null,
    embeddedRealtimeRangeOptions: List<RealtimeGraphRange>? = null,
    embeddedIsCrosshairEnabled: Boolean? = null,
    embeddedRealtimeRangeScrollState: LazyListState? = null,
    onRealtimeRangeChange: ((RealtimeGraphRange) -> Unit)? = null,
    onCrosshairEnabledChange: ((Boolean) -> Unit)? = null,
    comparisonData: HistoricalComparisonData? = null,
    lineSelection: GraphLineSelectionState = GraphLineSelectionState(),
    lineChipScrollState: LazyListState? = null,
    isComparisonMode: Boolean = false
) {
    if (historicalData.isEmpty()) return

    var internalRealtimeRange by remember(historicalData, isHistorical) { mutableStateOf(RealtimeGraphRange.ALL) }
    val realtimeRange = embeddedRealtimeRange ?: internalRealtimeRange
    val realtimeRangeOptions = remember(historicalData, isHistorical, embeddedRealtimeRangeOptions) {
        if (isHistorical) {
            listOf(RealtimeGraphRange.ALL)
        } else {
            embeddedRealtimeRangeOptions ?: realtimeGraphRangeOptionsForData(historicalData)
        }
    }
    LaunchedEffect(realtimeRangeOptions, realtimeRange, embeddedRealtimeRange) {
        if (realtimeRange !in realtimeRangeOptions) {
            if (embeddedRealtimeRange != null) {
                onRealtimeRangeChange?.invoke(RealtimeGraphRange.ALL)
            } else {
                internalRealtimeRange = RealtimeGraphRange.ALL
            }
        }
    }
    val realtimeStorageDisplay = remember(historicalData, isHistorical, graphType, realtimeRange) {
        if (!isHistorical && graphType == GraphType.STORAGE_PERCENTAGE) {
            buildRealtimeStorageGraphDisplayData(historicalData, realtimeRange)
        } else {
            null
        }
    }
    val displayData = remember(historicalData, isHistorical, realtimeRange, realtimeStorageDisplay) {
        when {
            isHistorical -> historicalData
            realtimeStorageDisplay != null -> realtimeStorageDisplay.data
            else -> filterRealtimeGraphData(historicalData, realtimeRange)
        }
    }
    if (displayData.isEmpty()) return

    
    val points = remember(displayData, graphType) {
        displayData.map { data ->
            val value = when (graphType) {
                GraphType.STORAGE_PERCENTAGE -> data.storagePercentage
                GraphType.STORAGE_VOLUME -> data.storageVolume
                GraphType.INFLOW -> data.inflow
                GraphType.OUTFLOW -> data.outflow
            }
            Pair(normalizeTime(data.time), value)
        }
    }

    val validValues = remember(points) { points.mapNotNull { it.second } }
    val allRangeValues = remember(historicalData, graphType) {
        historicalData.mapNotNull { data ->
            when (graphType) {
                GraphType.STORAGE_PERCENTAGE -> data.storagePercentage
                GraphType.STORAGE_VOLUME -> data.storageVolume
                GraphType.INFLOW -> data.inflow
                GraphType.OUTFLOW -> data.outflow
            }
        }
    }
    val scaleValues = remember(allRangeValues, validValues, isHistorical) {
        realtimeGraphScaleValues(
            allRangeValues = allRangeValues,
            selectedRangeValues = validValues,
            isHistorical = isHistorical
        )
    }

    val minValue = remember(scaleValues) { scaleValues.minOrNull() ?: 0f }
    val maxValue = remember(scaleValues) { scaleValues.maxOrNull() ?: 0f }

    
    val (yMin, yMax) = remember(graphType, minValue, maxValue) {
        if (graphType == GraphType.STORAGE_PERCENTAGE) {
            Pair(0f, 100f)
        } else {
            val diff = if (maxValue == minValue) 1f else maxValue - minValue
            Pair(minValue - diff * 0.1f, maxValue + diff * 0.1f)
        }
    }

    
    val rainfallPoints = remember(displayData, graphType) {
        if (graphType == GraphType.STORAGE_PERCENTAGE) {
            displayData.map { data ->
                Pair(normalizeTime(data.time), data.catchmentAverageRainfall)
            }
        } else {
            emptyList()
        }
    }
    val validRainfallValues = remember(rainfallPoints) { rainfallPoints.mapNotNull { it.second } }
    val allRangeRainfallValues = remember(historicalData, graphType) {
        if (graphType == GraphType.STORAGE_PERCENTAGE) {
            historicalData.mapNotNull { it.catchmentAverageRainfall }
        } else {
            emptyList()
        }
    }
    val scaleRainfallValues = remember(allRangeRainfallValues, validRainfallValues, isHistorical, graphType) {
        realtimeGraphScaleValues(
            allRangeValues = allRangeRainfallValues,
            selectedRangeValues = validRainfallValues,
            isHistorical = isHistorical || graphType != GraphType.STORAGE_PERCENTAGE
        )
    }

    
    
    
    
    
    val rainfallYMax = remember(scaleRainfallValues) {
        val actualMax = scaleRainfallValues.maxOrNull() ?: 0f
        if (actualMax > RAINFALL_Y_MAX_DEFAULT) {
            (ceil(actualMax.toDouble() / 10.0) * 10.0).toFloat()
        } else {
            RAINFALL_Y_MAX_DEFAULT
        }
    }

    val xAxisRangeMillis = resolveComparisonXAxisRange(
        isComparisonMode = isComparisonMode,
        resolvedRange = resolveGraphXAxisRange(
            isHistorical = isHistorical,
            historicalStartMillis = historicalSearchStartMillis,
            historicalEndMillis = historicalSearchEndMillis,
            realtimeStartMillis = realtimeStorageDisplay?.windowStartMillis,
            realtimeEndMillis = realtimeStorageDisplay?.windowEndMillis
        ),
        comparisonPeriod = comparisonData?.let { it.periodStartMillis to it.periodEndMillis },
        realtimeLatestMillis = realtimeStorageDisplay?.latestMillis
    )

    val useTimeBasedX = xAxisRangeMillis != null

    val pointMillisList: List<Long> = remember(points, useTimeBasedX) {
        if (!useTimeBasedX) return@remember emptyList()
        points.map { (timeStr, _) ->
            parseGraphTimeMillis(timeStr) ?: 0L
        }
    }

    
    val comparisonRangeMillis: Pair<Long, Long>? = resolveComparisonRangeMillis(
        isComparisonMode = isComparisonMode,
        xAxisRangeMillis = xAxisRangeMillis,
        realtimeRange = realtimeRange
    )

    val context = LocalContext.current
    val yearLabel: (Int) -> String = remember {
        val isJapaneseLocale = LocaleUtils.isJapanese(context)
        val label: (Int) -> String = { year -> if (isJapaneseLocale) "${year}年" else year.toString() }
        label
    }

    
    val labelPeriod = stringResource(R.string.graph_label_period)
    val labelRainfallTotal = stringResource(R.string.graph_label_rainfall_total)
    val labelRainfallMax = stringResource(R.string.graph_label_rainfall_max)
    val labelStorageRateMax = stringResource(R.string.graph_label_storage_rate_max)
    val labelStorageRateMin = stringResource(R.string.graph_label_storage_rate_min)
    val unitStorageRate = stringResource(R.string.graph_unit_storage_rate)
    val unitStorageVolume = stringResource(R.string.graph_unit_storage_volume)
    val storageVolumeUnitSuffix = unitStorageVolume.trim().removePrefix("(").removeSuffix(")")
    val labelTooltipRainfall = stringResource(R.string.graph_tooltip_rainfall)
    val labelTooltipStorageRate = stringResource(R.string.graph_tooltip_storage_rate)
    val allStorageRateChipLabel = stringResource(R.string.graph_line_chip_all_storage_rate)
    val storageRateChipLabel = stringResource(R.string.graph_line_chip_storage_rate)
    val rainfallChipLabel = stringResource(R.string.graph_line_chip_rainfall)
    val currentValue = points.lastOrNull()?.second?.let { formatValue(it, 2) } ?: ""
    val minEntry = points.filter { it.second != null }.minByOrNull { it.second!! }
    val maxEntry = points.filter { it.second != null }.maxByOrNull { it.second!! }
    val minStr = minEntry?.let { "${formatValue(it.second!!, 2)} (${it.first})" } ?: ""
    val maxStr = maxEntry?.let { "${formatValue(it.second!!, 2)} (${it.first})" } ?: ""
    val trendDirection = when {
        points.size >= 2 && points.last().second != null && points.first().second != null && points.last().second!! > points.first().second!! ->
            stringResource(R.string.desc_trend_up)
        points.size >= 2 && points.last().second != null && points.first().second != null && points.last().second!! < points.first().second!! ->
            stringResource(R.string.desc_trend_down)
        else -> stringResource(R.string.desc_trend_stable)
    }
    val dataPointsText = pluralStringResource(
        R.plurals.desc_graph_summary_data_points,
        displayData.size,
        displayData.size
    )
    val graphContentDescription = stringResource(
        R.string.desc_graph_summary_detailed,
        title,
        dataPointsText,
        points.firstOrNull()?.first.orEmpty(),
        points.lastOrNull()?.first.orEmpty(),
        currentValue,
        minStr,
        maxStr,
        trendDirection
    )

    val buildAccessibilityTooltip: (Int) -> String = remember(
        points, graphType, displayData, rainfallPoints,
        labelTooltipStorageRate, labelTooltipRainfall, rainfallUnitLabel, storageVolumeUnitSuffix
    ) {
        { index ->
            if (index !in points.indices) ""
            else {
                val point = points[index]
                val timeStr = point.first
                if (graphType == GraphType.STORAGE_PERCENTAGE) {
                    val rainfallValue = rainfallPoints.getOrNull(index)?.second
                    buildStorageGraphTooltipText(
                        timeStr = timeStr,
                        rainfallValue = rainfallValue,
                        storagePercentageValue = point.second,
                        labelTooltipRainfall = labelTooltipRainfall,
                        labelTooltipStorageRate = labelTooltipStorageRate,
                        rainfallUnitLabel = rainfallUnitLabel,
                        storagePercentageDecimals = 1
                    )
                } else {
                    val suffix = when (graphType) {
                        GraphType.STORAGE_VOLUME -> storageVolumeUnitSuffix
                        GraphType.INFLOW -> "m³/s"
                        GraphType.OUTFLOW -> "m³/s"
                        GraphType.STORAGE_PERCENTAGE -> ""
                    }
                    if (point.second != null) "$timeStr ${formatValue(point.second, 2)}$suffix"
                    else "$timeStr $MISSING_TEXT"
                }
            }
        }
    }

    
    var isExpanded by remember { mutableStateOf(true) }

    var touchX by remember { mutableStateOf<Float?>(null) }
    var accessibilitySelectedIndex by remember { mutableIntStateOf(-1) }
    
    var internalIsCrosshairEnabled by remember { mutableStateOf(false) }
    val isCrosshairEnabled = embeddedIsCrosshairEnabled ?: internalIsCrosshairEnabled
    LaunchedEffect(isExpanded) {
        if (!isExpanded && embeddedRealtimeRange == null) {
            internalIsCrosshairEnabled = false
            touchX = null
            internalRealtimeRange = RealtimeGraphRange.ALL
        }
    }
    val onRangeClick: (RealtimeGraphRange) -> Unit = { range ->
        if (embeddedRealtimeRange != null) {
            onRealtimeRangeChange?.invoke(range)
        } else {
            internalRealtimeRange = range
        }
    }
    val onCrosshairClick: () -> Unit = {
        if (embeddedIsCrosshairEnabled != null) {
            onCrosshairEnabledChange?.invoke(!isCrosshairEnabled)
        } else {
            internalIsCrosshairEnabled = !internalIsCrosshairEnabled
        }
    }
    val textMeasurer = rememberTextMeasurer()
    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val tooltipBgColor = MaterialTheme.colorScheme.surfaceVariant
    val tooltipTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val pastLineColor = MaterialTheme.colorScheme.outline
    val maxMarkerColor = primaryColor
    val minMarkerColor = tertiaryColor
    val expandedContent: @Composable () -> Unit = {
                val firstTime = points.firstOrNull()?.first ?: ""
                val lastTime = points.lastOrNull()?.first ?: ""
                
                
                val periodStart = if (xAxisRangeMillis != null) {
                    if (isHistorical) {
                        "${TimeUtils.formatToJst(xAxisRangeMillis.first, "yyyy/MM/dd")} 01:00"
                    } else {
                        TimeUtils.formatToJst(xAxisRangeMillis.first, "yyyy/MM/dd HH:mm")
                    }
                } else {
                    firstTime
                }
                val periodEnd = if (xAxisRangeMillis != null) {
                    val endStr = if (isHistorical) {
                        "${TimeUtils.formatToJst(xAxisRangeMillis.second, "yyyy/MM/dd")} 00:00"
                    } else {
                        TimeUtils.formatToJst(xAxisRangeMillis.second, "yyyy/MM/dd HH:mm")
                    }
                    if (showJstSuffix) "$endStr (JST)" else endStr
                } else {
                    if (showJstSuffix) "$lastTime (JST)" else lastTime
                }

                val hasAnyDisplayValue = validValues.isNotEmpty() || validRainfallValues.isNotEmpty()

                
                val rainfallSummaryText =
                    if (hasAnyDisplayValue && graphType == GraphType.STORAGE_PERCENTAGE && validRainfallValues.isNotEmpty()) {
                        val rainfallSum = String.format(java.util.Locale.US, "%.1f", validRainfallValues.sum())
                        val rainfallMaxVal = validRainfallValues.max()
                        val rainfallMax = String.format(java.util.Locale.US, "%.1f", rainfallMaxVal)
                        val rainfallUnitStr = rainfallUnitLabel.trim().removePrefix("(").removeSuffix(")")
                        buildString {
                            append("$labelRainfallTotal ${rainfallSum}mm")
                            if (rainfallMaxVal != 0f) {
                                append('\n')
                                append("$labelRainfallMax ${rainfallMax}$rainfallUnitStr")
                            }
                        }
                    } else {
                        ""
                    }

                val storageSummaryText =
                    if (hasAnyDisplayValue) {
                        points.filter { it.second != null }.let { validPoints ->
                            val maxPoint = validPoints.maxByOrNull { it.second!! }
                            val minPoint = validPoints.minByOrNull { it.second!! }
                            if (maxPoint != null && minPoint != null) {
                                val suffix = when (graphType) {
                                    GraphType.STORAGE_PERCENTAGE -> "%"
                                    GraphType.STORAGE_VOLUME -> storageVolumeUnitSuffix
                                    GraphType.INFLOW -> "m³/s"
                                    GraphType.OUTFLOW -> "m³/s"
                                }
                                val maxTime = if (showJstSuffix) "${maxPoint.first} (JST)" else maxPoint.first
                                val minTime = if (showJstSuffix) "${minPoint.first} (JST)" else minPoint.first
                                val maxStr = String.format(java.util.Locale.US, "%.2f", maxPoint.second)
                                val minStr = String.format(java.util.Locale.US, "%.2f", minPoint.second)
                                "$labelStorageRateMax $maxTime $maxStr$suffix\n$labelStorageRateMin $minTime $minStr$suffix"
                            } else {
                                ""
                            }
                        }
                    } else {
                        ""
                    }
                // 行順は仕様で固定: 期間 → 貯水率 最大 → 貯水率 最小 →
                // 流域平均雨量(合計) → 流域平均雨量 最大。
                val summaryText = buildString {
                    append("$labelPeriod $periodStart - $periodEnd")
                    if (storageSummaryText.isNotEmpty()) {
                        append('\n')
                        append(storageSummaryText)
                    }
                    if (rainfallSummaryText.isNotEmpty()) {
                        append('\n')
                        append(rainfallSummaryText)
                    }
                }
                Text(
                    text = summaryText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                
                val (xGridData, xTickData, xLabelData) = remember(
                    points, isHistorical, useTimeBasedX,
                    xAxisRangeMillis, pointMillisList
                ) {
                    buildXAxisData(
                        dataPoints = points,
                        isHistorical = isHistorical,
                        useTimeBasedX = useTimeBasedX,
                        historicalSearchStartMillis = xAxisRangeMillis?.first,
                        historicalSearchEndMillis = xAxisRangeMillis?.second,
                        pointMillisList = pointMillisList
                    )
                }
                val shouldDrawHistoricalLines = isComparisonMode && comparisonData != null &&
                    xAxisRangeMillis != null && comparisonRangeMillis != null &&
                    lineSelection.selectedYears.isNotEmpty()
                // 今年のライン(主系列)は比較モードでは今年チップの選択状態に従う。
                // 非比較モードではメトリックラインのチップ(「貯水率」)の選択状態に従う。
                // 比較データ非ロード時は今年チップが無いため非表示(読み込みオーバーレイで覆われる)。
                val isCurrentGraphLineVisible: (GraphLine) -> Boolean = { line ->
                    isGraphLineVisible(
                        line = line,
                        isComparisonMode = isComparisonMode,
                        mainLine = GraphLine.STORAGE_RATE,
                        visibleLines = lineSelection.visibleLines,
                        comparisonCurrentYear = comparisonData?.currentYear,
                        selectedYears = lineSelection.selectedYears
                    )
                }
                val currentYearLineVisible = isCurrentGraphLineVisible(GraphLine.STORAGE_RATE)
                // 表示domain内で線が実際に描かれる過去年。ツールチップの比較対象を
                // 選択中でも観測点が無く線が描かれない年を除外した年のみに絞るために使う。
                val drawnComparisonYears = remember(comparisonData, comparisonRangeMillis) {
                    if (comparisonData != null && comparisonRangeMillis != null) {
                        comparisonLinesDrawnYears(
                            comparisonData,
                            comparisonRangeMillis.first,
                            comparisonRangeMillis.second
                        )
                    } else {
                        emptySet()
                    }
                }
                BoxWithConstraints(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val graphHeight = observationGraphHeight(maxWidth, maxHeight)
                    val prevLabel = stringResource(R.string.desc_graph_previous_point)
                    val nextLabel = stringResource(R.string.desc_graph_next_point)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(graphHeight)
                            .semantics(mergeDescendants = false) {
                                contentDescription = graphContentDescription
                                historicalComparisonLinesDrawn = shouldDrawHistoricalLines
                                if (accessibilitySelectedIndex >= 0 && accessibilitySelectedIndex < points.size) {
                                    stateDescription = buildAccessibilityTooltip(accessibilitySelectedIndex)
                                }
                                focused = true
                                customActions = buildList {
                                    if (accessibilitySelectedIndex > 0) {
                                        add(CustomAccessibilityAction(prevLabel) {
                                            accessibilitySelectedIndex = maxOf(0, accessibilitySelectedIndex - 1)
                                            true
                                        })
                                    }
                                    if (accessibilitySelectedIndex < points.size - 1) {
                                        add(CustomAccessibilityAction(nextLabel) {
                                            accessibilitySelectedIndex = minOf(points.size - 1, accessibilitySelectedIndex + 1)
                                            true
                                        })
                                    }
                                    if (accessibilitySelectedIndex < 0 && points.isNotEmpty()) {
                                        add(CustomAccessibilityAction(nextLabel) {
                                            accessibilitySelectedIndex = 0
                                            true
                                        })
                                    }
                                }
                            }
                        .then(
                            
                            if (isCrosshairEnabled) {
                                Modifier
                                    .pointerInput(displayData) {
                                        detectDragGestures(
                                            onDragEnd = { touchX = null },
                                            onDragCancel = { touchX = null },
                                            onDrag = { change, _ ->
                                                touchX = change.position.x
                                            }
                                        )
                                    }
                                    .pointerInput(displayData) {
                                        detectTapGestures(
                                            onPress = { offset ->
                                                touchX = offset.x
                                                tryAwaitRelease()
                                                touchX = null
                                            }
                                        )
                                    }
                            } else Modifier
                        )
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val width = size.width
                        val height = size.height

                        val yStart = ceil(yMin.toDouble() / 10.0).toInt() * 10
                        val yEnd = floor(yMax.toDouble() / 10.0).toInt() * 10
                        val leftLabelMaxWidth = (yStart..yEnd step 10).map { yVal ->
                            textMeasurer.measure(
                                text = "$yVal",
                                style = TextStyle(color = labelColor, fontSize = 12.sp)
                            ).size.width.toFloat()
                        }.maxOrNull() ?: 50f
                        val leftMargin = 15f + 10f + leftLabelMaxWidth
                        val rightMargin = if (graphType == GraphType.STORAGE_PERCENTAGE) {
                            val rightLabelMaxWidth = (yStart..yEnd step 10).map { yVal ->
                                val rValue = yVal.toFloat() / (yMax - yMin) * rainfallYMax
                                textMeasurer.measure(
                                    text = String.format(java.util.Locale.US, "%.1f", rValue),
                                    style = TextStyle(color = tertiaryColor, fontSize = 12.sp)
                                ).size.width.toFloat()
                            }.maxOrNull() ?: 50f
                            15f + 10f + rightLabelMaxWidth
                        } else {
                            24.dp.toPx()
                        }
                        val yLabelHeight = textMeasurer.measure(
                            text = "100",
                            style = TextStyle(color = labelColor, fontSize = 12.sp)
                        ).size.height.toFloat()
                        val topMargin: Float = yLabelHeight + 24.dp.toPx()
                        val bottomMargin: Float = yLabelHeight + 8.dp.toPx()

                        val graphWidth = width - leftMargin - rightMargin
                        val graphHeight = (height - topMargin - bottomMargin).coerceAtLeast(0f)
                        if (graphHeight <= 0f || graphWidth <= 0f) return@Canvas

                        
                        val filteredLabels = if (xLabelData.size <= 1) {
                            xLabelData
                        } else {
                            val result = mutableListOf<Pair<Float, String>>()
                            var lastRightFraction = -1f
                            for ((fraction, label) in xLabelData) {
                                val textWidthPx = textMeasurer.measure(
                                    text = label,
                                    style = TextStyle(color = labelColor, fontSize = 12.sp)
                                ).size.width.toFloat()
                                val halfTextFraction = (textWidthPx / 2f) / graphWidth
                                val leftFraction = fraction - halfTextFraction
                                val rightFraction = fraction + halfTextFraction
                                if (lastRightFraction < 0f || leftFraction > lastRightFraction) {
                                    result.add(fraction to label)
                                    lastRightFraction = rightFraction
                                }
                            }
                            if (result.size >= 2) {
                                val firstRight = result.first().let { (f, _) -> f + textMeasurer.measure(
                                    text = result.first().second,
                                    style = TextStyle(color = labelColor, fontSize = 12.sp)
                                ).size.width.toFloat() / 2f / graphWidth }
                                val lastLeft = result.last().let { (f, _) -> f - textMeasurer.measure(
                                    text = result.last().second,
                                    style = TextStyle(color = labelColor, fontSize = 12.sp)
                                ).size.width.toFloat() / 2f / graphWidth }
                                if (1.0f - lastLeft + firstRight < 0f) {
                                    result.removeAt(result.lastIndex)
                                }
                            }
                            result
                        }

                        
                        val coords = points.mapIndexed { index, point ->
                            val axisRange = xAxisRangeMillis
                            val x = if (axisRange != null && pointMillisList.size > index) {
                                val range = (axisRange.second - axisRange.first).toFloat()
                                if (range > 0f) leftMargin + ((pointMillisList[index] - axisRange.first).toFloat() / range) * graphWidth
                                else leftMargin + graphWidth / 2f
                            } else if (points.size > 1) {
                                leftMargin + (index.toFloat() / (points.size - 1)) * graphWidth
                            } else {
                                leftMargin + graphWidth / 2f
                            }
                            val y =
                                if (point.second != null) topMargin + graphHeight - ((point.second!! - yMin) / (yMax - yMin)) * graphHeight else null
                            Triple(x, y, point)
                        }

                        
                        val rainfallCoords = if (graphType == GraphType.STORAGE_PERCENTAGE) {
                            rainfallPoints.mapIndexed { index, point ->
                                val axisRange = xAxisRangeMillis
                                val x = if (axisRange != null && pointMillisList.size > index) {
                                    val range = (axisRange.second - axisRange.first).toFloat()
                                    if (range > 0f) leftMargin + ((pointMillisList[index] - axisRange.first).toFloat() / range) * graphWidth
                                    else leftMargin + graphWidth / 2f
                                } else if (rainfallPoints.size > 1) {
                                    leftMargin + (index.toFloat() / (rainfallPoints.size - 1)) * graphWidth
                                } else {
                                    leftMargin + graphWidth / 2f
                                }
                                val y =
                                    if (point.second != null) topMargin + graphHeight - (point.second!! / rainfallYMax) * graphHeight else null
                                Triple(x, y, point)
                            }
                        } else {
                            emptyList()
                        }

                        
                        drawLine(
                            color = onSurfaceColor,
                            start = Offset(leftMargin, topMargin),
                            end = Offset(leftMargin, topMargin + graphHeight),
                            strokeWidth = 2f
                        )
                        drawLine(
                            color = onSurfaceColor,
                            start = Offset(leftMargin, topMargin + graphHeight),
                            end = Offset(leftMargin + graphWidth, topMargin + graphHeight),
                            strokeWidth = 2f
                        )

                        
                        if (graphType == GraphType.STORAGE_PERCENTAGE) {
                            drawLine(
                                color = onSurfaceColor,
                                start = Offset(leftMargin + graphWidth, topMargin),
                                end = Offset(leftMargin + graphWidth, topMargin + graphHeight),
                                strokeWidth = 2f
                            )
                        }

                        
                        for (yVal in yStart..yEnd step 10) {
                            val yPos = topMargin + graphHeight - ((yVal - yMin) / (yMax - yMin)) * graphHeight

                            
                            drawLine(
                                color = labelColor.copy(alpha = 0.3f),
                                start = Offset(leftMargin, yPos),
                                end = Offset(leftMargin + graphWidth, yPos),
                                strokeWidth = 2f,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                            )

                            
                            drawLine(
                                color = onSurfaceColor,
                                start = Offset(leftMargin - 10f, yPos),
                                end = Offset(leftMargin, yPos),
                                strokeWidth = 2f
                            )

                            
                            val textLayoutResult = textMeasurer.measure(
                                text = "$yVal",
                                style = TextStyle(color = labelColor, fontSize = 12.sp)
                            )
                            drawText(
                                textLayoutResult = textLayoutResult,
                                topLeft = Offset(
                                    leftMargin - 15f - textLayoutResult.size.width,
                                    yPos - textLayoutResult.size.height / 2f
                                )
                            )
                        }

                        
                        if (graphType == GraphType.STORAGE_PERCENTAGE) {
                            val unitLayout = textMeasurer.measure(
                                text = unitStorageRate,
                                style = TextStyle(color = labelColor, fontSize = 12.sp)
                            )
                            
                            drawText(
                                textLayoutResult = unitLayout,
                                topLeft = Offset(leftMargin - 15f - unitLayout.size.width, 4f)
                            )
                        }

                        
                        
                        
                        if (graphType == GraphType.STORAGE_PERCENTAGE) {
                            for (yVal in yStart..yEnd step 10) {
                                val rPos = topMargin + graphHeight - ((yVal - yMin) / (yMax - yMin)) * graphHeight
                                
                                val rValue = yVal.toFloat() / (yMax - yMin) * rainfallYMax

                                
                                drawLine(
                                    color = onSurfaceColor,
                                    start = Offset(leftMargin + graphWidth, rPos),
                                    end = Offset(leftMargin + graphWidth + 10f, rPos),
                                    strokeWidth = 2f
                                )

                                
                                val rainfallLabel = String.format(java.util.Locale.US, "%.1f", rValue)
                                val rTextLayout = textMeasurer.measure(
                                    text = rainfallLabel,
                                    style = TextStyle(color = tertiaryColor, fontSize = 12.sp)
                                )
                                drawText(
                                    textLayoutResult = rTextLayout,
                                    topLeft = Offset(leftMargin + graphWidth + 15f, rPos - rTextLayout.size.height / 2f)
                                )
                            }

                            
                            val rainfallUnitLayout = textMeasurer.measure(
                                text = rainfallUnitLabel,
                                style = TextStyle(color = tertiaryColor, fontSize = 11.sp)
                            )
                            drawText(
                                textLayoutResult = rainfallUnitLayout,
                                topLeft = Offset(width - rainfallUnitLayout.size.width - 4f, 4f)
                            )
                        }

                        
                        for (tickFraction in xGridData) {
                            val cx = leftMargin + tickFraction * graphWidth
                            drawLine(
                                color = labelColor.copy(alpha = 0.3f),
                                start = Offset(cx, topMargin),
                                end = Offset(cx, topMargin + graphHeight),
                                strokeWidth = 2f,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                            )
                        }

                        
                        val labeledTickFractions = filteredLabels.map { it.first }.toSet()
                        for (tickFraction in xTickData) {
                            val cx = leftMargin + tickFraction * graphWidth
                            val tickLength = if (tickFraction in labeledTickFractions) 16f else 10f
                            drawLine(
                                color = onSurfaceColor,
                                start = Offset(cx, topMargin + graphHeight),
                                end = Offset(cx, topMargin + graphHeight + tickLength),
                                strokeWidth = 2f
                            )
                        }

                        
                        for ((tickFraction, tickLabel) in filteredLabels) {
                            val cx = leftMargin + tickFraction * graphWidth
                            val dateTextLayout = textMeasurer.measure(
                                text = tickLabel,
                                style = TextStyle(color = labelColor, fontSize = 12.sp)
                            )
                            drawText(
                                textLayoutResult = dateTextLayout,
                                topLeft = Offset(
                                    cx - dateTextLayout.size.width / 2f,
                                    topMargin + graphHeight + 15f
                                )
                            )
                        }

                        
                        if (isComparisonMode && comparisonData != null &&
                            xAxisRangeMillis != null && comparisonRangeMillis != null
                        ) {
                            val (rangeStart, rangeEnd) = comparisonRangeMillis
                            val rangeMillis = (rangeEnd - rangeStart).toFloat()
                            for (series in comparisonData.series) {
                                if (series.year !in lineSelection.selectedYears) continue
                                val pastPath = Path()
                                var lastValidMillis: Long? = null
                                buildComparisonEdgeExtendedPoints(
                                    axis = comparisonData.hourlyAxisMillis,
                                    values = series.values,
                                    rangeStart = rangeStart,
                                    rangeEnd = rangeEnd
                                ).forEach { (millis, value) ->
                                    if (value == null) {
                                        lastValidMillis = null
                                        return@forEach
                                    }
                                    val x = if (rangeMillis > 0f) {
                                        leftMargin + ((millis - rangeStart).toFloat() / rangeMillis) * graphWidth
                                    } else {
                                        leftMargin + graphWidth / 2f
                                    }
                                    val y = topMargin + graphHeight - ((value - yMin) / (yMax - yMin)) * graphHeight
                                    val shouldBreak = lastValidMillis == null ||
                                        millis - lastValidMillis > LINE_BREAK_GAP_MILLIS
                                    if (shouldBreak) {
                                        pastPath.moveTo(x, y)
                                    } else {
                                        pastPath.lineTo(x, y)
                                    }
                                    lastValidMillis = millis
                                }
                                drawPath(
                                    path = pastPath,
                                    color = pastLineColor,
                                    style = Stroke(width = 1.dp.toPx())
                                )
                            }
                        }

                        
                        if (graphType == GraphType.STORAGE_PERCENTAGE && rainfallCoords.isNotEmpty() &&
                            GraphLine.RAINFALL in lineSelection.visibleLines
                        ) {
                            val rainfallPath = Path()
                            var lastValidRainfallIndex = -1
                            rainfallCoords.forEachIndexed { index, (x, y, _) ->
                                if (y != null) {
                                    val shouldBreak = lastValidRainfallIndex == -1 || (
                                        if (useTimeBasedX && pointMillisList.size > index && lastValidRainfallIndex >= 0)
                                            pointMillisList[index] - pointMillisList[lastValidRainfallIndex] > LINE_BREAK_GAP_MILLIS
                                        else
                                            (index - lastValidRainfallIndex) > LINE_BREAK_GAP_THRESHOLD
                                    )
                                    if (shouldBreak) {
                                        rainfallPath.moveTo(x, y)
                                    } else {
                                        rainfallPath.lineTo(x, y)
                                    }
                                    lastValidRainfallIndex = index
                                }
                            }
                            drawPath(
                                path = rainfallPath,
                                color = tertiaryColor,
                                style = Stroke(width = 2.dp.toPx())
                            )
                        }

                        
                        val path = Path()
                        var lastValidIndex = -1
                        coords.forEachIndexed { index, (x, y, _) ->
                            if (y != null) {
                                val shouldBreak = lastValidIndex == -1 || (
                                    if (useTimeBasedX && pointMillisList.size > index && lastValidIndex >= 0)
                                        pointMillisList[index] - pointMillisList[lastValidIndex] > LINE_BREAK_GAP_MILLIS
                                    else
                                        (index - lastValidIndex) > LINE_BREAK_GAP_THRESHOLD
                                )
                                if (shouldBreak) {
                                    path.moveTo(x, y)
                                } else {
                                    path.lineTo(x, y)
                                }
                                lastValidIndex = index
                            }
                        }
                        if (currentYearLineVisible) {
                            drawPath(
                                path = path,
                                color = primaryColor,
                                style = Stroke(width = 2.dp.toPx())
                            )
                        }

                        
                        val nonNullCoords = coords.filter { it.second != null }
                        if (currentYearLineVisible && nonNullCoords.isNotEmpty()) {
                            val minCoord = nonNullCoords.minByOrNull { it.third.second!! }!!
                            val maxCoord = nonNullCoords.maxByOrNull { it.third.second!! }!!
                            
                            if (minCoord.third.second != 0f) {
                                val cxMin = minCoord.first
                                val cyMin = minCoord.second!!
                                val rMin = 3.dp.toPx()
                                val diamondMin = Path().apply {
                                    moveTo(cxMin, cyMin - rMin)
                                    lineTo(cxMin + rMin, cyMin)
                                    lineTo(cxMin, cyMin + rMin)
                                    lineTo(cxMin - rMin, cyMin)
                                    close()
                                }
                                drawPath(path = diamondMin, color = minMarkerColor)
                            }
                            
                            if (maxCoord.third.second != 0f) {
                                val cxMax = maxCoord.first
                                val cyMax = maxCoord.second!!
                                val rMax = 3.dp.toPx()
                                drawRect(
                                    color = maxMarkerColor,
                                    topLeft = Offset(cxMax - rMax, cyMax - rMax),
                                    size = Size(rMax * 2f, rMax * 2f)
                                )
                            }
                        }

                        
                        if (graphType == GraphType.STORAGE_PERCENTAGE &&
                            GraphLine.RAINFALL in lineSelection.visibleLines
                        ) {
                            val nonNullRainfallCoords = rainfallCoords.filter { it.second != null }
                            if (nonNullRainfallCoords.isNotEmpty()) {
                                val maxRainfallCoord = nonNullRainfallCoords.maxByOrNull { it.third.second!! }!!
                                if (maxRainfallCoord.third.second != 0f) {
                                    val cxRMax = maxRainfallCoord.first
                                    val cyRMax = maxRainfallCoord.second!!
                                    val rRMax = 3.dp.toPx()
                                    drawRect(
                                        color = maxMarkerColor,
                                        topLeft = Offset(cxRMax - rRMax, cyRMax - rRMax),
                                        size = Size(rRMax * 2f, rRMax * 2f)
                                    )
                                }
                            }
                        }

                        
                        touchX?.let { tx ->
                            val closest = findClosestGraphPoint(
                                touchX = tx,
                                leftMargin = leftMargin,
                                graphWidth = graphWidth,
                                coords = coords
                            )
                            if (closest != null) {
                                val closestIndex = closest.index
                                val cx = closest.x
                                val cy = closest.y
                                val point = closest.point

                                
                                drawLine(
                                    color = onSurfaceColor.copy(alpha = 0.5f),
                                    start = Offset(cx, topMargin),
                                    end = Offset(cx, topMargin + graphHeight),
                                    strokeWidth = 2f,
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                                )

                                
                                if (isCurrentGraphLineVisible(GraphLine.STORAGE_RATE) && cy != null) {
                                    drawCircle(color = primaryColor, radius = 5.dp.toPx(), center = Offset(cx, cy))
                                }

                                
                                if (graphType == GraphType.STORAGE_PERCENTAGE &&
                                    isCurrentGraphLineVisible(GraphLine.RAINFALL) &&
                                    closestIndex < rainfallCoords.size
                                ) {
                                    val rcy = rainfallCoords[closestIndex].second
                                    if (rcy != null) {
                                        drawCircle(
                                            color = tertiaryColor,
                                            radius = 5.dp.toPx(),
                                            center = Offset(cx, rcy)
                                        )
                                    }
                                }

                                
                                val timeStr = point.first

                                
                                val rainfallValue =
                                    if (graphType == GraphType.STORAGE_PERCENTAGE && closestIndex < rainfallPoints.size) {
                                        rainfallPoints[closestIndex].second
                                    } else {
                                        null
                                    }
                                val rainfallStr = if (rainfallValue != null) {
                                    String.format(java.util.Locale.US, "%.1f", rainfallValue)
                                } else {
                                    MISSING_TEXT
                                }

                                val storagePercentageValue = if (graphType == GraphType.STORAGE_PERCENTAGE) {
                                    point.second
                                } else {
                                    point.second
                                }

                                val textStr = if (isComparisonMode) {
                                    val rows = buildList {
                                        add(
                                            HistoricalComparisonTooltipRow(
                                                year = comparisonData?.currentYear ?: 0,
                                                value = storagePercentageValue,
                                                isCurrentYear = true
                                            )
                                        )
                                        val axis = comparisonData?.hourlyAxisMillis ?: emptyList()
                                        val floored = pointMillisList
                                            .getOrNull(closestIndex)
                                            ?.let { floorToJstHourMillis(it) }
                                        val axisIndex = floored?.let { axis.indexOf(it) } ?: -1
                                        for (series in comparisonData?.series ?: emptyList()) {
                                            if (series.year !in lineSelection.selectedYears) continue
                                            // 線が実際に描かれている年のみを比較対象にする(観測点が無い年は除外)。
                                            if (series.year !in drawnComparisonYears) continue
                                            val pastValue =
                                                if (axisIndex >= 0) series.values.getOrNull(axisIndex) else null
                                            // ホバー時刻に値が無い年(最新年のカバレッジ外など)は行自体を出さない=値非表示。
                                            if (pastValue == null) continue
                                            add(HistoricalComparisonTooltipRow(year = series.year, value = pastValue))
                                        }
                                    }
                                    (listOf(timeStr) +
                                        buildComparisonTooltipYearLines(
                                            rows,
                                            HistoricalComparisonMetric.STORAGE_RATE,
                                            yearLabel
                                        )
                                        ).joinToString("\n")
                                } else if (graphType == GraphType.STORAGE_PERCENTAGE) {
                                    val rainfallUnitStr = rainfallUnitLabel.trim().removePrefix("(").removeSuffix(")")
                                    buildStorageGraphTooltipText(
                                        timeStr = timeStr,
                                        rainfallValue = rainfallValue,
                                        storagePercentageValue = storagePercentageValue,
                                        labelTooltipRainfall = labelTooltipRainfall,
                                        labelTooltipStorageRate = labelTooltipStorageRate,
                                        rainfallUnitLabel = rainfallUnitStr,
                                        storagePercentageDecimals = 2,
                                        separator = "\n"
                                    )
                                } else {
                                    val suffix = when (graphType) {
                                        GraphType.STORAGE_VOLUME -> storageVolumeUnitSuffix
                                        GraphType.INFLOW -> "m³/s"
                                        GraphType.OUTFLOW -> "m³/s"
                                        GraphType.STORAGE_PERCENTAGE -> ""
                                    }
                                    if (point.second != null) "$timeStr\n${
                                        formatValue(
                                            point.second,
                                            2
                                        )
                                    }$suffix" else "$timeStr\n$MISSING_TEXT"
                                }

                                val textLayoutResult = textMeasurer.measure(
                                    text = textStr,
                                    style = TextStyle(color = tooltipTextColor, fontSize = 12.sp)
                                )
                                val tw = textLayoutResult.size.width
                                val th = textLayoutResult.size.height

                                
                                val tipX = calculateTooltipStartX(
                                    anchorX = cx,
                                    tooltipWidth = tw.toFloat(),
                                    canvasWidth = width,
                                    horizontalOffset = 10.dp.toPx()
                                )
                                val tipY = topMargin

                                
                                drawRect(
                                    color = tooltipBgColor.copy(alpha = TOOLTIP_BACKGROUND_ALPHA),
                                    topLeft = Offset(tipX - 10f, tipY - 10f),
                                    size = Size(tw + 20f, th + 20f)
                                )

                                
                                drawText(
                                    textMeasurer = textMeasurer,
                                    text = textStr,
                                    topLeft = Offset(tipX, tipY),
                                    style = TextStyle(color = tooltipTextColor, fontSize = 12.sp)
                                )
                            }
                        }
                    }
                    }
                }
                // グラフキャンバス直下・期間チップの上に、全modeで常時表示するライン切替チップ行。
                // 比較モード(mode 3)では先頭に「貯水率全て」チップと年チップ(2002〜今年)を並べ、
                // その後ろに流域平均雨量のチップを並べる。「全て」チップはmode別に1つだけ
                // 表示するため、このCard(貯水率系)では「貯水率全て」のみで「貯水量全て」は出さない。
                // 「貯水率」チップは非比較モード(mode 1)のみに表示する
                // (比較モードの主線は今年チップの選択状態に従う)。
                val lineChips = buildList {
                    if (isComparisonMode) {
                        add(
                            GraphLineChipSpec(
                                key = "all-storage-rate",
                                label = allStorageRateChipLabel,
                                selected = isAllPastYearsSelected(
                                    lineSelection.selectedYears,
                                    lineSelection.availableYears
                                ),
                                testTag = TestTags.OBSERVATION_GRAPH_YEAR_ALL_CHIP,
                                onClick = lineSelection.onAllYearsToggle
                            )
                        )
                        lineSelection.availableYears.forEach { year ->
                            add(
                                GraphLineChipSpec(
                                    key = "year-$year",
                                    label = year.toString(),
                                    selected = year in lineSelection.selectedYears,
                                    testTag = TestTags.OBSERVATION_GRAPH_YEAR_CHIP_PREFIX + year,
                                    onClick = { lineSelection.onYearToggle(year) }
                                )
                            )
                        }
                    }
                    if (!isComparisonMode) {
                        add(
                            graphLineChipSpec(
                                line = GraphLine.STORAGE_RATE,
                                label = storageRateChipLabel,
                                visibleLines = lineSelection.visibleLines,
                                onLineToggle = lineSelection.onLineToggle
                            )
                        )
                    }
                    add(
                        graphLineChipSpec(
                            line = GraphLine.RAINFALL,
                            label = rainfallChipLabel,
                            visibleLines = lineSelection.visibleLines,
                            onLineToggle = lineSelection.onLineToggle
                        )
                    )
                }
                GraphLineChipRow(
                    chips = lineChips,
                    scrollState = lineChipScrollState ?: remember { LazyListState() }
                )
                GraphControlRow(
                    showRangeSelector = !isHistorical,
                    rangeOptions = realtimeRangeOptions,
                    range = realtimeRange,
                    onRangeClick = onRangeClick,
                    isCrosshairEnabled = isCrosshairEnabled,
                    onCrosshairClick = onCrosshairClick,
                    rangeScrollState = embeddedRealtimeRangeScrollState
                )
                Spacer(modifier = Modifier.height(16.dp))
    }

    GraphCardScaffold(
        embedded = embedded,
        isExpanded = isExpanded,
        onToggleExpand = { isExpanded = !isExpanded },
        title = title,
        modifier = modifier,
        content = expandedContent
    )
}


@Composable
internal fun DamVolumeFlowGraphCard(
    historicalData: List<DamHistoricalData>,
    title: String,
    modifier: Modifier = Modifier,
    showJstSuffix: Boolean = false,
    isHistorical: Boolean = false,
    historicalSearchStartMillis: Long? = null,
    historicalSearchEndMillis: Long? = null,
    embedded: Boolean = false,
    embeddedRealtimeRange: RealtimeGraphRange? = null,
    embeddedRealtimeRangeOptions: List<RealtimeGraphRange>? = null,
    embeddedIsCrosshairEnabled: Boolean? = null,
    embeddedRealtimeRangeScrollState: LazyListState? = null,
    onRealtimeRangeChange: ((RealtimeGraphRange) -> Unit)? = null,
    onCrosshairEnabledChange: ((Boolean) -> Unit)? = null,
    comparisonData: HistoricalComparisonData? = null,
    lineSelection: GraphLineSelectionState = GraphLineSelectionState(),
    lineChipScrollState: LazyListState? = null,
    isComparisonMode: Boolean = false
) {
    if (historicalData.isEmpty()) return

    var internalRealtimeRange by remember(historicalData, isHistorical) { mutableStateOf(RealtimeGraphRange.ALL) }
    val realtimeRange = embeddedRealtimeRange ?: internalRealtimeRange
    val realtimeRangeOptions = remember(historicalData, isHistorical, embeddedRealtimeRangeOptions) {
        if (isHistorical) {
            listOf(RealtimeGraphRange.ALL)
        } else {
            embeddedRealtimeRangeOptions ?: realtimeGraphRangeOptionsForData(historicalData)
        }
    }
    LaunchedEffect(realtimeRangeOptions, realtimeRange, embeddedRealtimeRange) {
        if (realtimeRange !in realtimeRangeOptions) {
            if (embeddedRealtimeRange != null) {
                onRealtimeRangeChange?.invoke(RealtimeGraphRange.ALL)
            } else {
                internalRealtimeRange = RealtimeGraphRange.ALL
            }
        }
    }
    val realtimeDisplay = remember(historicalData, isHistorical, realtimeRange) {
        if (!isHistorical) buildRealtimeGraphDisplayData(historicalData, realtimeRange) else null
    }
    val displayData = remember(historicalData, isHistorical, realtimeDisplay) {
        if (isHistorical) historicalData else realtimeDisplay?.data ?: historicalData
    }
    if (displayData.isEmpty()) return

    
    val volumePoints = remember(displayData) {
        displayData.map { Pair(normalizeTime(it.time), it.storageVolume) }
    }
    val inflowPoints = remember(displayData) {
        displayData.map { Pair(normalizeTime(it.time), it.inflow) }
    }
    val outflowPoints = remember(displayData) {
        displayData.map { Pair(normalizeTime(it.time), it.outflow) }
    }

    val validVolumeValues = remember(volumePoints) { volumePoints.mapNotNull { it.second } }
    val validInflowValues = remember(inflowPoints) { inflowPoints.mapNotNull { it.second } }
    val validOutflowValues = remember(outflowPoints) { outflowPoints.mapNotNull { it.second } }

    val hasVolumeData = validVolumeValues.isNotEmpty()
    val hasFlowData = validInflowValues.isNotEmpty() || validOutflowValues.isNotEmpty()

    val xAxisRangeMillis = resolveComparisonXAxisRange(
        isComparisonMode = isComparisonMode,
        resolvedRange = resolveGraphXAxisRange(
            isHistorical = isHistorical,
            historicalStartMillis = historicalSearchStartMillis,
            historicalEndMillis = historicalSearchEndMillis,
            realtimeStartMillis = realtimeDisplay?.windowStartMillis,
            realtimeEndMillis = realtimeDisplay?.windowEndMillis
        ),
        comparisonPeriod = comparisonData?.let { it.periodStartMillis to it.periodEndMillis },
        realtimeLatestMillis = realtimeDisplay?.latestMillis
    )
    
    val useTimeBasedX = xAxisRangeMillis != null
    
    val pointMillisList: List<Long> = remember(volumePoints, useTimeBasedX) {
        if (!useTimeBasedX) return@remember emptyList()
        volumePoints.map { (timeStr, _) ->
            parseGraphTimeMillis(timeStr) ?: 0L
        }
    }

    
    val comparisonRangeMillis: Pair<Long, Long>? = resolveComparisonRangeMillis(
        isComparisonMode = isComparisonMode,
        xAxisRangeMillis = xAxisRangeMillis,
        realtimeRange = realtimeRange
    )

    val context = LocalContext.current
    val yearLabel: (Int) -> String = remember {
        val isJapaneseLocale = LocaleUtils.isJapanese(context)
        val label: (Int) -> String = { year -> if (isJapaneseLocale) "${year}年" else year.toString() }
        label
    }

    
    val allRangeVolumeValues = remember(historicalData) {
        historicalData.mapNotNull { it.storageVolume }
    }
    val scaleVolumeValues = remember(allRangeVolumeValues, validVolumeValues, isHistorical) {
        realtimeGraphScaleValues(
            allRangeValues = allRangeVolumeValues,
            selectedRangeValues = validVolumeValues,
            isHistorical = isHistorical
        )
    }
    val volumeScaleValues = remember(scaleVolumeValues, comparisonData, lineSelection.selectedYears) {
        buildComparisonVolumeScaleValues(
            isComparisonMode = isComparisonMode,
            baseValues = scaleVolumeValues,
            comparisonData = comparisonData,
            selectedPastYears = lineSelection.selectedYears
        )
    }
    val (volumeYMin, volumeYMax) = remember(volumeScaleValues) {
        if (volumeScaleValues.isEmpty()) {
            Pair(0f, 1f)
        } else {
            val maxV = volumeScaleValues.maxOrNull() ?: 0f
            val diff = if (maxV == 0f) 1f else maxV
            Pair(0f, maxV + diff * 0.1f)
        }
    }

    
    val allRangeInflowValues = remember(historicalData) {
        historicalData.mapNotNull { it.inflow }
    }
    val scaleInflowValues = remember(allRangeInflowValues, validInflowValues, isHistorical) {
        realtimeGraphScaleValues(
            allRangeValues = allRangeInflowValues,
            selectedRangeValues = validInflowValues,
            isHistorical = isHistorical
        )
    }
    val allRangeOutflowValues = remember(historicalData) {
        historicalData.mapNotNull { it.outflow }
    }
    val scaleOutflowValues = remember(allRangeOutflowValues, validOutflowValues, isHistorical) {
        realtimeGraphScaleValues(
            allRangeValues = allRangeOutflowValues,
            selectedRangeValues = validOutflowValues,
            isHistorical = isHistorical
        )
    }
    val allFlowValues = remember(scaleInflowValues, scaleOutflowValues) {
        scaleInflowValues + scaleOutflowValues
    }
    val hasFlowScaleData = allFlowValues.isNotEmpty()
    val (flowYMin, flowYMax) = remember(allFlowValues) {
        if (allFlowValues.isEmpty()) {
            Pair(0f, 1f)
        } else {
            val maxV = allFlowValues.maxOrNull() ?: 0f
            val diff = if (maxV == 0f) 1f else maxV
            Pair(0f, maxV + diff * 0.1f)
        }
    }

    
    val volumeMaxPoint = remember(volumePoints) {
        volumePoints.filter { it.second != null }.maxByOrNull { it.second!! }
    }
    val volumeMinPoint = remember(volumePoints) {
        volumePoints.filter { it.second != null }.minByOrNull { it.second!! }
    }
    val inflowMaxPoint = remember(inflowPoints) {
        inflowPoints.filter { it.second != null }.maxByOrNull { it.second!! }
    }
    val inflowMinPoint = remember(inflowPoints) {
        inflowPoints.filter { it.second != null }.minByOrNull { it.second!! }
    }
    val outflowMaxPoint = remember(outflowPoints) {
        outflowPoints.filter { it.second != null }.maxByOrNull { it.second!! }
    }
    val outflowMinPoint = remember(outflowPoints) {
        outflowPoints.filter { it.second != null }.minByOrNull { it.second!! }
    }

    
    val labelPeriod = stringResource(R.string.graph_label_period)
    val labelVolumeMax = stringResource(R.string.graph_label_storage_volume_max)
    val labelVolumeMin = stringResource(R.string.graph_label_storage_volume_min)
    val labelInflowMax = stringResource(R.string.graph_label_inflow_max)
    val labelInflowMin = stringResource(R.string.graph_label_inflow_min)
    val labelOutflowMax = stringResource(R.string.graph_label_outflow_max)
    val labelOutflowMin = stringResource(R.string.graph_label_outflow_min)
    val unitStorageVolume = stringResource(R.string.graph_unit_storage_volume)
    val unitFlow = stringResource(R.string.graph_unit_flow)
    val labelTooltipStorageVolume = stringResource(R.string.graph_tooltip_storage_volume)
     val labelTooltipInflow = stringResource(R.string.graph_tooltip_inflow)
    val labelTooltipOutflow = stringResource(R.string.graph_tooltip_outflow)
    val allStorageVolumeChipLabel = stringResource(R.string.graph_line_chip_all_storage_volume)
    val storageVolumeChipLabel = stringResource(R.string.graph_line_chip_storage_volume)
    val inflowChipLabel = stringResource(R.string.graph_line_chip_inflow)
    val outflowChipLabel = stringResource(R.string.graph_line_chip_outflow)
    val currentValue = volumePoints.lastOrNull()?.second?.let { formatValue(it, 2) } ?: ""
    val minEntry = volumePoints.filter { it.second != null }.minByOrNull { it.second!! }
    val maxEntry = volumePoints.filter { it.second != null }.maxByOrNull { it.second!! }
    val minStr = minEntry?.let { "${formatValue(it.second!!, 2)} (${it.first})" } ?: ""
    val maxStr = maxEntry?.let { "${formatValue(it.second!!, 2)} (${it.first})" } ?: ""
    val trendDirection = when {
        volumePoints.size >= 2 && volumePoints.last().second != null && volumePoints.first().second != null && volumePoints.last().second!! > volumePoints.first().second!! ->
            stringResource(R.string.desc_trend_up)
        volumePoints.size >= 2 && volumePoints.last().second != null && volumePoints.first().second != null && volumePoints.last().second!! < volumePoints.first().second!! ->
            stringResource(R.string.desc_trend_down)
        else -> stringResource(R.string.desc_trend_stable)
    }
    val dataPointsText = pluralStringResource(
        R.plurals.desc_graph_summary_data_points,
        displayData.size,
        displayData.size
    )
    val graphContentDescription = stringResource(
        R.string.desc_graph_summary_detailed,
        title,
        dataPointsText,
        volumePoints.firstOrNull()?.first.orEmpty(),
        volumePoints.lastOrNull()?.first.orEmpty(),
        currentValue,
        minStr,
        maxStr,
        trendDirection
    )

    val buildAccessibilityTooltip: (Int) -> String = remember(
        volumePoints, inflowPoints, outflowPoints,
        labelTooltipStorageVolume, labelTooltipInflow, labelTooltipOutflow
    ) {
        { index ->
            if (index !in volumePoints.indices) ""
            else {
                val timeStr = volumePoints[index].first
                val volumeStr = volumePoints[index].second?.toInt()?.toString() ?: MISSING_TEXT
                val inflowStr = inflowPoints.getOrNull(index)?.second?.let {
                    String.format(java.util.Locale.US, "%.2f", it)
                } ?: MISSING_TEXT
                val outflowStr = outflowPoints.getOrNull(index)?.second?.let {
                    String.format(java.util.Locale.US, "%.2f", it)
                } ?: MISSING_TEXT
                "$timeStr $labelTooltipStorageVolume ${volumeStr}×10³m³ $labelTooltipInflow ${inflowStr}m³/s $labelTooltipOutflow ${outflowStr}m³/s"
            }
        }
    }

    
    var isExpanded by remember { mutableStateOf(true) }
    var touchX by remember { mutableStateOf<Float?>(null) }
    var accessibilitySelectedIndex by remember { mutableIntStateOf(-1) }
    
    var internalIsCrosshairEnabled by remember { mutableStateOf(false) }
    val isCrosshairEnabled = embeddedIsCrosshairEnabled ?: internalIsCrosshairEnabled
    LaunchedEffect(isExpanded) {
        if (!isExpanded && embeddedRealtimeRange == null) {
            internalIsCrosshairEnabled = false
            touchX = null
            internalRealtimeRange = RealtimeGraphRange.ALL
        }
    }
    val onRangeClick: (RealtimeGraphRange) -> Unit = { range ->
        if (embeddedRealtimeRange != null) {
            onRealtimeRangeChange?.invoke(range)
        } else {
            internalRealtimeRange = range
        }
    }
    val onCrosshairClick: () -> Unit = {
        if (embeddedIsCrosshairEnabled != null) {
            onCrosshairEnabledChange?.invoke(!isCrosshairEnabled)
        } else {
            internalIsCrosshairEnabled = !internalIsCrosshairEnabled
        }
    }

    val textMeasurer = rememberTextMeasurer()
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val tooltipBgColor = MaterialTheme.colorScheme.surfaceVariant
    val tooltipTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val pastLineColor = MaterialTheme.colorScheme.outline
    val maxMarkerColor = primaryColor
    val minMarkerColor = secondaryColor
    val expandedContent: @Composable () -> Unit = {
                val firstTime = volumePoints.firstOrNull()?.first ?: ""
                val lastTime = volumePoints.lastOrNull()?.first ?: ""
                
                
                val periodStart = if (xAxisRangeMillis != null) {
                    if (isHistorical) {
                        "${TimeUtils.formatToJst(xAxisRangeMillis.first, "yyyy/MM/dd")} 01:00"
                    } else {
                        TimeUtils.formatToJst(xAxisRangeMillis.first, "yyyy/MM/dd HH:mm")
                    }
                } else {
                    firstTime
                }
                val periodEnd = if (xAxisRangeMillis != null) {
                    val endStr = if (isHistorical) {
                        "${TimeUtils.formatToJst(xAxisRangeMillis.second, "yyyy/MM/dd")} 00:00"
                    } else {
                        TimeUtils.formatToJst(xAxisRangeMillis.second, "yyyy/MM/dd HH:mm")
                    }
                    if (showJstSuffix) "$endStr (JST)" else endStr
                } else {
                    if (showJstSuffix) "$lastTime (JST)" else lastTime
                }

                
                val statsLines = buildList {
                    add("$labelPeriod $periodStart - $periodEnd")
                    if (hasVolumeData && volumeMaxPoint != null && (volumeMaxPoint.second ?: 0f) != 0f) {
                        val t = if (showJstSuffix) "${volumeMaxPoint.first} (JST)" else volumeMaxPoint.first
                        add("$labelVolumeMax $t ${volumeMaxPoint.second?.toInt() ?: MISSING_TEXT}${unitStorageVolume}")
                    }
                    if (hasVolumeData && volumeMinPoint != null && (volumeMinPoint.second ?: 0f) != 0f) {
                        val t = if (showJstSuffix) "${volumeMinPoint.first} (JST)" else volumeMinPoint.first
                        add("$labelVolumeMin $t ${volumeMinPoint.second?.toInt() ?: MISSING_TEXT}${unitStorageVolume}")
                    }
                    if (validInflowValues.isNotEmpty() && inflowMaxPoint != null && (inflowMaxPoint.second ?: 0f) != 0f) {
                        val t = if (showJstSuffix) "${inflowMaxPoint.first} (JST)" else inflowMaxPoint.first
                        val v = inflowMaxPoint.second?.let { String.format(java.util.Locale.US, "%.2f", it) } ?: MISSING_TEXT
                        add("$labelInflowMax $t ${v}${unitFlow}")
                    }
                    if (validInflowValues.isNotEmpty() && inflowMinPoint != null && (inflowMinPoint.second ?: 0f) != 0f) {
                        val t = if (showJstSuffix) "${inflowMinPoint.first} (JST)" else inflowMinPoint.first
                        val v = inflowMinPoint.second?.let { String.format(java.util.Locale.US, "%.2f", it) } ?: MISSING_TEXT
                        add("$labelInflowMin $t ${v}${unitFlow}")
                    }
                    if (validOutflowValues.isNotEmpty() && outflowMaxPoint != null && (outflowMaxPoint.second ?: 0f) != 0f) {
                        val t = if (showJstSuffix) "${outflowMaxPoint.first} (JST)" else outflowMaxPoint.first
                        val v = outflowMaxPoint.second?.let { String.format(java.util.Locale.US, "%.2f", it) } ?: MISSING_TEXT
                        add("$labelOutflowMax $t ${v}${unitFlow}")
                    }
                    if (validOutflowValues.isNotEmpty() && outflowMinPoint != null && (outflowMinPoint.second ?: 0f) != 0f) {
                        val t = if (showJstSuffix) "${outflowMinPoint.first} (JST)" else outflowMinPoint.first
                        val v = outflowMinPoint.second?.let { String.format(java.util.Locale.US, "%.2f", it) } ?: MISSING_TEXT
                        add("$labelOutflowMin $t ${v}${unitFlow}")
                    }
                }
                Text(
                    text = statsLines.joinToString("\n"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                
                val (xGridData, xTickData, xLabelData) = remember(
                    volumePoints, isHistorical, useTimeBasedX,
                    historicalSearchStartMillis, historicalSearchEndMillis, pointMillisList
                ) {
                    buildXAxisData(
                        dataPoints = volumePoints,
                        isHistorical = isHistorical,
                        useTimeBasedX = useTimeBasedX,
                        historicalSearchStartMillis = xAxisRangeMillis?.first,
                        historicalSearchEndMillis = xAxisRangeMillis?.second,
                        pointMillisList = pointMillisList
                    )
                }
                val shouldDrawHistoricalLines = isComparisonMode && comparisonData != null &&
                    xAxisRangeMillis != null && comparisonRangeMillis != null &&
                    lineSelection.selectedYears.isNotEmpty()
                // 今年のライン(主系列)は比較モードでは今年チップの選択状態に従う。
                // 非比較モードではメトリックラインのチップ(「貯水量」)の選択状態に従う。
                // 比較データ非ロード時は今年チップが無いため非表示(読み込みオーバーレイで覆われる)。
                val isCurrentGraphLineVisible: (GraphLine) -> Boolean = { line ->
                    isGraphLineVisible(
                        line = line,
                        isComparisonMode = isComparisonMode,
                        mainLine = GraphLine.STORAGE_VOLUME,
                        visibleLines = lineSelection.visibleLines,
                        comparisonCurrentYear = comparisonData?.currentYear,
                        selectedYears = lineSelection.selectedYears
                    )
                }
                val currentYearLineVisible = isCurrentGraphLineVisible(GraphLine.STORAGE_VOLUME)
                // 表示domain内で線が実際に描かれる過去年。ツールチップの比較対象を
                // 選択中でも観測点が無く線が描かれない年を除外した年のみに絞るために使う。
                val drawnComparisonYears = remember(comparisonData, comparisonRangeMillis) {
                    if (comparisonData != null && comparisonRangeMillis != null) {
                        comparisonLinesDrawnYears(
                            comparisonData,
                            comparisonRangeMillis.first,
                            comparisonRangeMillis.second
                        )
                    } else {
                        emptySet()
                    }
                }
                BoxWithConstraints(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val graphHeight = observationGraphHeight(maxWidth, maxHeight)
                    val prevLabel = stringResource(R.string.desc_graph_previous_point)
                    val nextLabel = stringResource(R.string.desc_graph_next_point)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(graphHeight)
                            .semantics(mergeDescendants = false) {
                                contentDescription = graphContentDescription
                                historicalComparisonLinesDrawn = shouldDrawHistoricalLines
                                if (accessibilitySelectedIndex >= 0 && accessibilitySelectedIndex < volumePoints.size) {
                                    stateDescription = buildAccessibilityTooltip(accessibilitySelectedIndex)
                                }
                                focused = true
                                customActions = buildList {
                                    if (accessibilitySelectedIndex > 0) {
                                        add(CustomAccessibilityAction(prevLabel) {
                                            accessibilitySelectedIndex = maxOf(0, accessibilitySelectedIndex - 1)
                                            true
                                        })
                                    }
                                    if (accessibilitySelectedIndex < volumePoints.size - 1) {
                                        add(CustomAccessibilityAction(nextLabel) {
                                            accessibilitySelectedIndex = minOf(volumePoints.size - 1, accessibilitySelectedIndex + 1)
                                            true
                                        })
                                    }
                                    if (accessibilitySelectedIndex < 0 && volumePoints.isNotEmpty()) {
                                        add(CustomAccessibilityAction(nextLabel) {
                                            accessibilitySelectedIndex = 0
                                            true
                                        })
                                    }
                                }
                            }
                        .then(
                            
                            if (isCrosshairEnabled) {
                                Modifier
                                    .pointerInput(displayData) {
                                        detectDragGestures(
                                            onDragEnd = { touchX = null },
                                            onDragCancel = { touchX = null },
                                            onDrag = { change, _ -> touchX = change.position.x }
                                        )
                                    }
                                    .pointerInput(displayData) {
                                        detectTapGestures(
                                            onPress = { offset ->
                                                touchX = offset.x
                                                tryAwaitRelease()
                                                touchX = null
                                            }
                                        )
                                    }
                            } else Modifier
                        )
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val width = size.width
                        val height = size.height

                        val volumeStep = computeNiceStep(volumeYMax - volumeYMin)
                        val volumeTickStart = ceil(volumeYMin.toDouble() / volumeStep.toDouble()).toFloat() * volumeStep
                        val leftLabelMaxWidth = buildList {
                            var vTick = volumeTickStart
                            while (vTick <= volumeYMax + volumeStep * 0.01f) {
                                add(vTick.toInt().toString())
                                vTick += volumeStep
                            }
                        }.map { label ->
                            textMeasurer.measure(
                                text = label,
                                style = TextStyle(color = labelColor, fontSize = 12.sp)
                            ).size.width.toFloat()
                        }.maxOrNull() ?: 50f
                        val leftMargin = 15f + 10f + leftLabelMaxWidth
                        val rightLabelMaxWidth = if (hasFlowScaleData) {
                            val flowStep = computeNiceStep(flowYMax - flowYMin)
                            val flowTickStart = ceil(flowYMin.toDouble() / flowStep.toDouble()).toFloat() * flowStep
                            buildList {
                                var fTick = flowTickStart
                                while (fTick <= flowYMax + flowStep * 0.01f) {
                                    add(String.format(java.util.Locale.US, "%.1f", fTick))
                                    fTick += flowStep
                                }
                            }.map { label ->
                                textMeasurer.measure(
                                    text = label,
                                    style = TextStyle(color = tertiaryColor, fontSize = 12.sp)
                                ).size.width.toFloat()
                            }.maxOrNull() ?: 50f
                        } else {
                            50f
                        }
                        val rightMargin = 15f + 10f + rightLabelMaxWidth
                        val yLabelHeight = textMeasurer.measure(
                            text = "1000",
                            style = TextStyle(color = labelColor, fontSize = 12.sp)
                        ).size.height.toFloat()
                        val topMargin: Float = yLabelHeight + 24.dp.toPx()
                        val bottomMargin: Float = yLabelHeight + 8.dp.toPx()

                        val graphWidth = width - leftMargin - rightMargin
                        val graphHeight = (height - topMargin - bottomMargin).coerceAtLeast(0f)
                        if (graphHeight <= 0f || graphWidth <= 0f) return@Canvas

                        
                        val filteredLabels = if (xLabelData.size <= 1) {
                            xLabelData
                        } else {
                            val result = mutableListOf<Pair<Float, String>>()
                            var lastRightFraction = -1f
                            for ((fraction, label) in xLabelData) {
                                val textWidthPx = textMeasurer.measure(
                                    text = label,
                                    style = TextStyle(color = labelColor, fontSize = 12.sp)
                                ).size.width.toFloat()
                                val halfTextFraction = (textWidthPx / 2f) / graphWidth
                                val leftFraction = fraction - halfTextFraction
                                val rightFraction = fraction + halfTextFraction
                                if (lastRightFraction < 0f || leftFraction > lastRightFraction) {
                                    result.add(fraction to label)
                                    lastRightFraction = rightFraction
                                }
                            }
                            if (result.size >= 2) {
                                val firstRight = result.first().let { (f, _) -> f + textMeasurer.measure(
                                    text = result.first().second,
                                    style = TextStyle(color = labelColor, fontSize = 12.sp)
                                ).size.width.toFloat() / 2f / graphWidth }
                                val lastLeft = result.last().let { (f, _) -> f - textMeasurer.measure(
                                    text = result.last().second,
                                    style = TextStyle(color = labelColor, fontSize = 12.sp)
                                ).size.width.toFloat() / 2f / graphWidth }
                                if (1.0f - lastLeft + firstRight < 0f) {
                                    result.removeAt(result.lastIndex)
                                }
                            }
                            result
                        }

                        val n = volumePoints.size

                        
                        fun xOf(index: Int): Float {
                            return if (xAxisRangeMillis != null && pointMillisList.size > index) {
                                val range = (xAxisRangeMillis.second - xAxisRangeMillis.first).toFloat()
                                if (range > 0f) leftMargin + ((pointMillisList[index] - xAxisRangeMillis.first).toFloat() / range) * graphWidth
                                else leftMargin + graphWidth / 2f
                            } else if (n > 1) {
                                leftMargin + (index.toFloat() / (n - 1)) * graphWidth
                            } else {
                                leftMargin + graphWidth / 2f
                            }
                        }

                        
                        fun yVolume(value: Float): Float =
                            topMargin + graphHeight - ((value - volumeYMin) / (volumeYMax - volumeYMin)) * graphHeight

                        
                        fun yFlow(value: Float): Float =
                            topMargin + graphHeight - ((value - flowYMin) / (flowYMax - flowYMin)) * graphHeight

                        
                        drawLine(
                            color = onSurfaceColor,
                            start = Offset(leftMargin, topMargin),
                            end = Offset(leftMargin, topMargin + graphHeight),
                            strokeWidth = 2f
                        )
                        drawLine(
                            color = onSurfaceColor,
                            start = Offset(leftMargin, topMargin + graphHeight),
                            end = Offset(leftMargin + graphWidth, topMargin + graphHeight),
                            strokeWidth = 2f
                        )
                        drawLine(
                            color = onSurfaceColor,
                            start = Offset(leftMargin + graphWidth, topMargin),
                            end = Offset(leftMargin + graphWidth, topMargin + graphHeight),
                            strokeWidth = 2f
                        )

                        
                        var vTick = volumeTickStart
                        while (vTick <= volumeYMax + volumeStep * 0.01f) {
                            val yPos = yVolume(vTick)
                            if (yPos >= topMargin - 1f && yPos <= topMargin + graphHeight + 1f) {
                                drawLine(
                                    color = labelColor.copy(alpha = 0.3f),
                                    start = Offset(leftMargin, yPos),
                                    end = Offset(leftMargin + graphWidth, yPos),
                                    strokeWidth = 2f,
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                                )
                                drawLine(
                                    color = onSurfaceColor,
                                    start = Offset(leftMargin - 10f, yPos),
                                    end = Offset(leftMargin, yPos),
                                    strokeWidth = 2f
                                )
                                val label = vTick.toInt().toString()
                                val textLayout = textMeasurer.measure(
                                    text = label,
                                    style = TextStyle(color = labelColor, fontSize = 12.sp)
                                )
                                drawText(
                                    textLayoutResult = textLayout,
                                    topLeft = Offset(
                                        leftMargin - 15f - textLayout.size.width,
                                        yPos - textLayout.size.height / 2f
                                    )
                                )
                            }
                            vTick += volumeStep
                        }
                        
                        val volumeUnitLayout = textMeasurer.measure(
                            text = unitStorageVolume,
                            style = TextStyle(color = labelColor, fontSize = 12.sp)
                        )
                        drawText(
                            textLayoutResult = volumeUnitLayout,
                            topLeft = Offset(leftMargin - 4f - volumeUnitLayout.size.width, 4f)
                        )

                        
                        
                        if (hasFlowScaleData) {
                            val flowStep = computeNiceStep(flowYMax - flowYMin)
                            val flowTickStart = ceil(flowYMin.toDouble() / flowStep.toDouble()).toFloat() * flowStep
                            val flowGridPathEffect =
                                PathEffect.dashPathEffect(floatArrayOf(12f, 4f, 2f, 4f, 2f, 4f), 0f)
                            var fTick = flowTickStart
                            while (fTick <= flowYMax + flowStep * 0.01f) {
                                val yPos = yFlow(fTick)
                                if (yPos >= topMargin - 1f && yPos <= topMargin + graphHeight + 1f) {
                                    
                                    drawLine(
                                        color = tertiaryColor.copy(alpha = 0.3f),
                                        start = Offset(leftMargin, yPos),
                                        end = Offset(leftMargin + graphWidth, yPos),
                                        strokeWidth = 2f,
                                        pathEffect = flowGridPathEffect
                                    )
                                    drawLine(
                                        color = onSurfaceColor,
                                        start = Offset(leftMargin + graphWidth, yPos),
                                        end = Offset(leftMargin + graphWidth + 10f, yPos),
                                        strokeWidth = 2f
                                    )
                                    val label = String.format(java.util.Locale.US, "%.1f", fTick)
                                    val textLayout = textMeasurer.measure(
                                        text = label,
                                        style = TextStyle(color = tertiaryColor, fontSize = 12.sp)
                                    )
                                    drawText(
                                        textLayoutResult = textLayout,
                                        topLeft = Offset(
                                            leftMargin + graphWidth + 15f,
                                            yPos - textLayout.size.height / 2f
                                        )
                                    )
                                }
                                fTick += flowStep
                            }
                            
                            val flowUnitLayout = textMeasurer.measure(
                                text = unitFlow,
                                style = TextStyle(color = tertiaryColor, fontSize = 11.sp)
                            )
                            drawText(
                                textLayoutResult = flowUnitLayout,
                                topLeft = Offset(width - flowUnitLayout.size.width - 4f, 4f)
                            )
                        }

                        
                        for (tickFraction in xGridData) {
                            val cx = leftMargin + tickFraction * graphWidth
                            drawLine(
                                color = labelColor.copy(alpha = 0.3f),
                                start = Offset(cx, topMargin),
                                end = Offset(cx, topMargin + graphHeight),
                                strokeWidth = 2f,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                            )
                        }

                        
                        val labeledTickFractions = filteredLabels.map { it.first }.toSet()
                        for (tickFraction in xTickData) {
                            val cx = leftMargin + tickFraction * graphWidth
                            val tickLength = if (tickFraction in labeledTickFractions) 16f else 10f
                            drawLine(
                                color = onSurfaceColor,
                                start = Offset(cx, topMargin + graphHeight),
                                end = Offset(cx, topMargin + graphHeight + tickLength),
                                strokeWidth = 2f
                            )
                        }

                        
                        for ((tickFraction, tickLabel) in filteredLabels) {
                            val cx = leftMargin + tickFraction * graphWidth
                            val dateTextLayout = textMeasurer.measure(
                                text = tickLabel,
                                style = TextStyle(color = labelColor, fontSize = 12.sp)
                            )
                            drawText(
                                textLayoutResult = dateTextLayout,
                                topLeft = Offset(
                                    cx - dateTextLayout.size.width / 2f,
                                    topMargin + graphHeight + 15f
                                )
                            )
                        }

                        
                        if (validInflowValues.isNotEmpty() &&
                            isCurrentGraphLineVisible(GraphLine.INFLOW)
                        ) {
                            val inflowPath = Path()
                            var lastValidIndex = -1
                            inflowPoints.forEachIndexed { index, (_, value) ->
                                if (value != null) {
                                    val x = xOf(index)
                                    val y = yFlow(value)
                                    val shouldBreak = lastValidIndex == -1 || (
                                        if (useTimeBasedX && pointMillisList.size > index && lastValidIndex >= 0)
                                            pointMillisList[index] - pointMillisList[lastValidIndex] > LINE_BREAK_GAP_MILLIS
                                        else
                                            (index - lastValidIndex) > LINE_BREAK_GAP_THRESHOLD
                                    )
                                    if (shouldBreak) {
                                        inflowPath.moveTo(x, y)
                                    } else {
                                        inflowPath.lineTo(x, y)
                                    }
                                    lastValidIndex = index
                                }
                            }
                            drawPath(path = inflowPath, color = tertiaryColor, style = Stroke(width = 2.dp.toPx()))
                        }

                        
                        if (validOutflowValues.isNotEmpty() &&
                            isCurrentGraphLineVisible(GraphLine.OUTFLOW)
                        ) {
                            val outflowPath = Path()
                            var lastValidIndex = -1
                            outflowPoints.forEachIndexed { index, (_, value) ->
                                if (value != null) {
                                    val x = xOf(index)
                                    val y = yFlow(value)
                                    val shouldBreak = lastValidIndex == -1 || (
                                        if (useTimeBasedX && pointMillisList.size > index && lastValidIndex >= 0)
                                            pointMillisList[index] - pointMillisList[lastValidIndex] > LINE_BREAK_GAP_MILLIS
                                        else
                                            (index - lastValidIndex) > LINE_BREAK_GAP_THRESHOLD
                                    )
                                    if (shouldBreak) {
                                        outflowPath.moveTo(x, y)
                                    } else {
                                        outflowPath.lineTo(x, y)
                                    }
                                    lastValidIndex = index
                                }
                            }
                            drawPath(path = outflowPath, color = secondaryColor, style = Stroke(width = 2.dp.toPx()))
                        }

                        if (isComparisonMode && comparisonData != null &&
                            xAxisRangeMillis != null && comparisonRangeMillis != null
                        ) {
                            val (rangeStart, rangeEnd) = comparisonRangeMillis
                            val rangeMillis = (rangeEnd - rangeStart).toFloat()
                            for (series in comparisonData.series) {
                                if (series.year !in lineSelection.selectedYears) continue
                                val pastPath = Path()
                                var lastValidMillis: Long? = null
                                buildComparisonEdgeExtendedPoints(
                                    axis = comparisonData.hourlyAxisMillis,
                                    values = series.values,
                                    rangeStart = rangeStart,
                                    rangeEnd = rangeEnd
                                ).forEach { (millis, value) ->
                                    if (value == null) {
                                        lastValidMillis = null
                                        return@forEach
                                    }
                                    val x = if (rangeMillis > 0f) {
                                        leftMargin + ((millis - rangeStart).toFloat() / rangeMillis) * graphWidth
                                    } else {
                                        leftMargin + graphWidth / 2f
                                    }
                                    val y = yVolume(value)
                                    val shouldBreak = lastValidMillis == null ||
                                        millis - lastValidMillis > LINE_BREAK_GAP_MILLIS
                                    if (shouldBreak) {
                                        pastPath.moveTo(x, y)
                                    } else {
                                        pastPath.lineTo(x, y)
                                    }
                                    lastValidMillis = millis
                                }
                                drawPath(
                                    path = pastPath,
                                    color = pastLineColor,
                                    style = Stroke(width = 1.dp.toPx())
                                )
                            }
                        }

                        
                        if (hasVolumeData && currentYearLineVisible) {
                            val volumePath = Path()
                            var lastValidIndex = -1
                            volumePoints.forEachIndexed { index, (_, value) ->
                                if (value != null) {
                                    val x = xOf(index)
                                    val y = yVolume(value)
                                    val shouldBreak = lastValidIndex == -1 || (
                                        if (useTimeBasedX && pointMillisList.size > index && lastValidIndex >= 0)
                                            pointMillisList[index] - pointMillisList[lastValidIndex] > LINE_BREAK_GAP_MILLIS
                                        else
                                            (index - lastValidIndex) > LINE_BREAK_GAP_THRESHOLD
                                    )
                                    if (shouldBreak) {
                                        volumePath.moveTo(x, y)
                                    } else {
                                        volumePath.lineTo(x, y)
                                    }
                                    lastValidIndex = index
                                }
                            }
                            drawPath(path = volumePath, color = primaryColor, style = Stroke(width = 2.dp.toPx()))
                        }

                        
                        if (currentYearLineVisible) {
                            volumeMaxPoint?.let { pt ->
                                if ((pt.second ?: 0f) != 0f) {
                                    val idx = volumePoints.indexOf(pt)
                                    if (idx >= 0) {
                                        val cxM = xOf(idx)
                                        val cyM = yVolume(pt.second!!)
                                        val rM = 3.dp.toPx()
                                        drawRect(
                                            color = maxMarkerColor,
                                            topLeft = Offset(cxM - rM, cyM - rM),
                                            size = Size(rM * 2f, rM * 2f)
                                        )
                                    }
                                }
                            }
                            volumeMinPoint?.let { pt ->
                                if ((pt.second ?: 0f) != 0f) {
                                    val idx = volumePoints.indexOf(pt)
                                    if (idx >= 0) {
                                        val cxM = xOf(idx)
                                        val cyM = yVolume(pt.second!!)
                                        val rM = 3.dp.toPx()
                                        val diamondM = Path().apply {
                                            moveTo(cxM, cyM - rM)
                                            lineTo(cxM + rM, cyM)
                                            lineTo(cxM, cyM + rM)
                                            lineTo(cxM - rM, cyM)
                                            close()
                                        }
                                        drawPath(path = diamondM, color = minMarkerColor)
                                    }
                                }
                            }
                        }
                        if (GraphLine.INFLOW in lineSelection.visibleLines) {
                            inflowMaxPoint?.let { pt ->
                                if ((pt.second ?: 0f) != 0f) {
                                    val idx = inflowPoints.indexOf(pt)
                                    if (idx >= 0) {
                                        val cxM = xOf(idx)
                                        val cyM = yFlow(pt.second!!)
                                        val rM = 3.dp.toPx()
                                        drawRect(
                                            color = maxMarkerColor,
                                            topLeft = Offset(cxM - rM, cyM - rM),
                                            size = Size(rM * 2f, rM * 2f)
                                        )
                                    }
                                }
                            }
                            inflowMinPoint?.let { pt ->
                                if ((pt.second ?: 0f) != 0f) {
                                    val idx = inflowPoints.indexOf(pt)
                                    if (idx >= 0) {
                                        val cxM = xOf(idx)
                                        val cyM = yFlow(pt.second!!)
                                        val rM = 3.dp.toPx()
                                        val diamondM = Path().apply {
                                            moveTo(cxM, cyM - rM)
                                            lineTo(cxM + rM, cyM)
                                            lineTo(cxM, cyM + rM)
                                            lineTo(cxM - rM, cyM)
                                            close()
                                        }
                                        drawPath(path = diamondM, color = minMarkerColor)
                                    }
                                }
                            }
                        }
                        if (GraphLine.OUTFLOW in lineSelection.visibleLines) {
                            outflowMaxPoint?.let { pt ->
                                if ((pt.second ?: 0f) != 0f) {
                                    val idx = outflowPoints.indexOf(pt)
                                    if (idx >= 0) {
                                        val cxM = xOf(idx)
                                        val cyM = yFlow(pt.second!!)
                                        val rM = 3.dp.toPx()
                                        drawRect(
                                            color = maxMarkerColor,
                                            topLeft = Offset(cxM - rM, cyM - rM),
                                            size = Size(rM * 2f, rM * 2f)
                                        )
                                    }
                                }
                            }
                            outflowMinPoint?.let { pt ->
                                if ((pt.second ?: 0f) != 0f) {
                                    val idx = outflowPoints.indexOf(pt)
                                    if (idx >= 0) {
                                        val cxM = xOf(idx)
                                        val cyM = yFlow(pt.second!!)
                                        val rM = 3.dp.toPx()
                                        val diamondM = Path().apply {
                                            moveTo(cxM, cyM - rM)
                                            lineTo(cxM + rM, cyM)
                                            lineTo(cxM, cyM + rM)
                                            lineTo(cxM - rM, cyM)
                                            close()
                                        }
                                        drawPath(path = diamondM, color = minMarkerColor)
                                    }
                                }
                            }
                        }

                        
                        touchX?.let { tx ->
                            val closestIndex = findClosestGraphIndex(
                                touchX = tx,
                                leftMargin = leftMargin,
                                graphWidth = graphWidth,
                                pointCount = n,
                                xOf = { index -> xOf(index) }
                            ) ?: return@let
                            val cx = xOf(closestIndex)

                            
                            drawLine(
                                color = onSurfaceColor.copy(alpha = 0.5f),
                                start = Offset(cx, topMargin),
                                end = Offset(cx, topMargin + graphHeight),
                                strokeWidth = 2f,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                            )

                            
                            if (isCurrentGraphLineVisible(GraphLine.STORAGE_VOLUME)) {
                                volumePoints[closestIndex].second?.let { v ->
                                    drawCircle(color = primaryColor, radius = 5.dp.toPx(), center = Offset(cx, yVolume(v)))
                                }
                            }
                            if (isCurrentGraphLineVisible(GraphLine.INFLOW)) {
                                inflowPoints[closestIndex].second?.let { v ->
                                    drawCircle(color = tertiaryColor, radius = 5.dp.toPx(), center = Offset(cx, yFlow(v)))
                                }
                            }
                            if (isCurrentGraphLineVisible(GraphLine.OUTFLOW)) {
                                outflowPoints[closestIndex].second?.let { v ->
                                    drawCircle(color = secondaryColor, radius = 5.dp.toPx(), center = Offset(cx, yFlow(v)))
                                }
                            }

                            
                            val timeStr = volumePoints[closestIndex].first
                            val volumeStr = volumePoints[closestIndex].second?.toInt()?.toString() ?: MISSING_TEXT
                            val inflowStr = inflowPoints[closestIndex].second?.let {
                                String.format(java.util.Locale.US, "%.2f", it)
                            } ?: MISSING_TEXT
                            val outflowStr = outflowPoints[closestIndex].second?.let {
                                String.format(java.util.Locale.US, "%.2f", it)
                            } ?: MISSING_TEXT
                            val textStr = if (isComparisonMode) {
                                val rows = buildList {
                                    add(
                                        HistoricalComparisonTooltipRow(
                                            year = comparisonData?.currentYear ?: 0,
                                            value = volumePoints[closestIndex].second,
                                            isCurrentYear = true
                                        )
                                    )
                                    val axis = comparisonData?.hourlyAxisMillis ?: emptyList()
                                    val floored = pointMillisList
                                        .getOrNull(closestIndex)
                                        ?.let { floorToJstHourMillis(it) }
                                    val axisIndex = floored?.let { axis.indexOf(it) } ?: -1
                                    for (series in comparisonData?.series ?: emptyList()) {
                                        if (series.year !in lineSelection.selectedYears) continue
                                        // 線が実際に描かれている年のみを比較対象にする(観測点が無い年は除外)。
                                        if (series.year !in drawnComparisonYears) continue
                                        val pastValue =
                                            if (axisIndex >= 0) series.values.getOrNull(axisIndex) else null
                                        // ホバー時刻に値が無い年(最新年のカバレッジ外など)は行自体を出さない=値非表示。
                                        if (pastValue == null) continue
                                        add(HistoricalComparisonTooltipRow(year = series.year, value = pastValue))
                                    }
                                }
                                (listOf(timeStr) +
                                    buildComparisonTooltipYearLines(
                                        rows,
                                        HistoricalComparisonMetric.STORAGE_VOLUME,
                                        yearLabel
                                    )
                                    ).joinToString("\n")
                            } else {
                                "$timeStr\n$labelTooltipStorageVolume ${volumeStr}×10³m³\n$labelTooltipInflow ${inflowStr}m³/s\n$labelTooltipOutflow ${outflowStr}m³/s"
                            }

                            val textLayoutResult = textMeasurer.measure(
                                text = textStr,
                                style = TextStyle(color = tooltipTextColor, fontSize = 12.sp)
                            )
                            val tw = textLayoutResult.size.width
                            val th = textLayoutResult.size.height
                            val tipX = calculateTooltipStartX(
                                anchorX = cx,
                                tooltipWidth = tw.toFloat(),
                                canvasWidth = width,
                                horizontalOffset = 10.dp.toPx()
                            )
                            val tipY = topMargin

                            drawRect(
                                color = tooltipBgColor.copy(alpha = TOOLTIP_BACKGROUND_ALPHA),
                                topLeft = Offset(tipX - 10f, tipY - 10f),
                                size = Size(tw + 20f, th + 20f)
                            )
                            drawText(
                                textMeasurer = textMeasurer,
                                text = textStr,
                                topLeft = Offset(tipX, tipY),
                                style = TextStyle(color = tooltipTextColor, fontSize = 12.sp)
                            )
                        }
                    }
                    }
                }
                // グラフキャンバス直下・期間チップの上に、全modeで常時表示するライン切替チップ行。
                // 比較モード(mode 4)では先頭に「貯水量全て」チップと年チップ(2002〜今年)を並べ、
                // その後ろに流入量・放流量のチップを並べる。「全て」チップはmode別に1つだけ
                // 表示するため、このCard(貯水量系)では「貯水量全て」のみで「貯水率全て」は出さない。
                // 「貯水量」チップは非比較モード(mode 2)のみに表示する
                // (比較モードの主線は今年チップの選択状態に従う)。
                val lineChips = buildList {
                    if (isComparisonMode) {
                        add(
                            GraphLineChipSpec(
                                key = "all-storage-volume",
                                label = allStorageVolumeChipLabel,
                                selected = isAllPastYearsSelected(
                                    lineSelection.selectedYears,
                                    lineSelection.availableYears
                                ),
                                testTag = TestTags.OBSERVATION_GRAPH_YEAR_ALL_CHIP,
                                onClick = lineSelection.onAllYearsToggle
                            )
                        )
                        lineSelection.availableYears.forEach { year ->
                            add(
                                GraphLineChipSpec(
                                    key = "year-$year",
                                    label = year.toString(),
                                    selected = year in lineSelection.selectedYears,
                                    testTag = TestTags.OBSERVATION_GRAPH_YEAR_CHIP_PREFIX + year,
                                    onClick = { lineSelection.onYearToggle(year) }
                                )
                            )
                        }
                    }
                    if (!isComparisonMode) {
                        add(
                            graphLineChipSpec(
                                line = GraphLine.STORAGE_VOLUME,
                                label = storageVolumeChipLabel,
                                visibleLines = lineSelection.visibleLines,
                                onLineToggle = lineSelection.onLineToggle
                            )
                        )
                    }
                    add(
                        graphLineChipSpec(
                            line = GraphLine.INFLOW,
                            label = inflowChipLabel,
                            visibleLines = lineSelection.visibleLines,
                            onLineToggle = lineSelection.onLineToggle
                        )
                    )
                    add(
                        graphLineChipSpec(
                            line = GraphLine.OUTFLOW,
                            label = outflowChipLabel,
                            visibleLines = lineSelection.visibleLines,
                            onLineToggle = lineSelection.onLineToggle
                        )
                    )
                }
                GraphLineChipRow(
                    chips = lineChips,
                    scrollState = lineChipScrollState ?: remember { LazyListState() }
                )
                GraphControlRow(
                    showRangeSelector = !isHistorical,
                    rangeOptions = realtimeRangeOptions,
                    range = realtimeRange,
                    onRangeClick = onRangeClick,
                    isCrosshairEnabled = isCrosshairEnabled,
                    onCrosshairClick = onCrosshairClick,
                    rangeScrollState = embeddedRealtimeRangeScrollState
                )
                Spacer(modifier = Modifier.height(16.dp))
    }

    GraphCardScaffold(
        embedded = embedded,
        isExpanded = isExpanded,
        onToggleExpand = { isExpanded = !isExpanded },
        title = title,
        modifier = modifier,
        content = expandedContent
    )
}
