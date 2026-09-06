// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.ui.main

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.tecogonaz.tcsameuradammonitor.R
import net.tecogonaz.tcsameuradammonitor.domain.model.DamHistoricalData
import net.tecogonaz.tcsameuradammonitor.ui.common.MISSING_TEXT
import net.tecogonaz.tcsameuradammonitor.ui.common.mouseDragScroll
import net.tecogonaz.tcsameuradammonitor.util.TimeUtils
import java.util.Calendar
import java.util.Locale

internal val HISTORY_TABLE_FONT_SIZE = 12.sp

private val HISTORY_TABLE_DATETIME_WIDTH = 68.dp
private val HISTORY_TABLE_RAINFALL_WIDTH = 68.dp
private val HISTORY_TABLE_VOLUME_WIDTH = 52.dp
private val HISTORY_TABLE_INFLOW_WIDTH = 52.dp
private val HISTORY_TABLE_OUTFLOW_WIDTH = 52.dp
private val HISTORY_TABLE_STORAGE_WIDTH = 52.dp
private const val HISTORY_TABLE_BORDER_ALPHA = 0.75f
internal val HISTORY_TABLE_WIDTH =
    HISTORY_TABLE_DATETIME_WIDTH +
            HISTORY_TABLE_RAINFALL_WIDTH +
            HISTORY_TABLE_VOLUME_WIDTH +
            HISTORY_TABLE_INFLOW_WIDTH +
            HISTORY_TABLE_OUTFLOW_WIDTH +
            HISTORY_TABLE_STORAGE_WIDTH

/**
 * 観測履歴テーブルの各列の幅（[Dp]）を保持するデータクラスです。
 *
 * @property datetime 日時列の幅
 * @property rainfall 流域平均雨量列の幅
 * @property volume 貯水量列の幅
 * @property inflow 流入量列の幅
 * @property outflow 放流量列の幅
 * @property storage 貯水率列の幅
 */
internal data class HistoryTableColumnWidths(
    val datetime: Dp,
    val rainfall: Dp,
    val volume: Dp,
    val inflow: Dp,
    val outflow: Dp,
    val storage: Dp
) {
    val tableWidth: Dp
        get() = datetime + rainfall + volume + inflow + outflow + storage
}

internal val HISTORY_TABLE_BASE_COLUMN_WIDTHS = HistoryTableColumnWidths(
    datetime = HISTORY_TABLE_DATETIME_WIDTH,
    rainfall = HISTORY_TABLE_RAINFALL_WIDTH,
    volume = HISTORY_TABLE_VOLUME_WIDTH,
    inflow = HISTORY_TABLE_INFLOW_WIDTH,
    outflow = HISTORY_TABLE_OUTFLOW_WIDTH,
    storage = HISTORY_TABLE_STORAGE_WIDTH
)


/**
 * 画面上の利用可能な幅 [availableWidth] と各セルの測定結果に基づいて、
 * テーブル全体の列幅 [HistoryTableColumnWidths] を計算して記憶（remember）するComposable関数です。
 *
 * @param availableWidth 画面のレイアウトで利用可能な最大幅。
 * @param headerTexts テーブルのヘッダー文字列のリスト。
 * @param fontSize テキストの描画フォントサイズ。
 * @return 計算された各列の幅情報 [HistoryTableColumnWidths]。
 */
@Composable
internal fun rememberHistoryTableColumnWidths(
    availableWidth: Dp,
    headerTexts: List<String>,
    fontSize: androidx.compose.ui.unit.TextUnit
): HistoryTableColumnWidths {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    
    
    val samplePatterns = listOf(
        "12/31 23:59",
        "888.8",
        "8888888",
        "888.88",
        "888.88",
        "888.88"
    )
    val textStyle = MaterialTheme.typography.labelSmall.copy(fontSize = fontSize)
    val cellPadding = 4.dp  

    val measured = remember(headerTexts, fontSize) {
        headerTexts.zip(samplePatterns).map { (header, sample) ->
            val headerW = textMeasurer.measure(header, textStyle).size.width
            val sampleW = textMeasurer.measure(sample, textStyle).size.width
            with(density) { maxOf(headerW, sampleW).toDp() + cellPadding }
        }
    }

    val base = HistoryTableColumnWidths(
        datetime = maxOf(HISTORY_TABLE_DATETIME_WIDTH, measured[0]),
        rainfall = maxOf(HISTORY_TABLE_RAINFALL_WIDTH, measured[1]),
        volume = maxOf(HISTORY_TABLE_VOLUME_WIDTH, measured[2]),
        inflow = maxOf(HISTORY_TABLE_INFLOW_WIDTH, measured[3]),
        outflow = maxOf(HISTORY_TABLE_OUTFLOW_WIDTH, measured[4]),
        storage = maxOf(HISTORY_TABLE_STORAGE_WIDTH, measured[5])
    )

    val targetWidth = remember(base, availableWidth) {
        maxOf(base.tableWidth, availableWidth)
    }
    return remember(targetWidth) {
        val scale = targetWidth.value / base.tableWidth.value
        HistoryTableColumnWidths(
            datetime = (base.datetime.value * scale).dp,
            rainfall = (base.rainfall.value * scale).dp,
            volume = (base.volume.value * scale).dp,
            inflow = (base.inflow.value * scale).dp,
            outflow = (base.outflow.value * scale).dp,
            storage = (base.storage.value * scale).dp
        )
    }
}



private fun parseItemTimeMillis(timeStr: String): Long? {
    return TimeUtils.parseJstMillisAllow24Hour(timeStr, "yyyy/MM/dd HH:mm")
}


@Composable
internal fun historyTableTextStyle(isHeader: Boolean): TextStyle {
    val fontSize = HISTORY_TABLE_FONT_SIZE
    val lineHeight = fontSize * 1.5f
    return if (isHeader) {
        MaterialTheme.typography.labelSmall.copy(fontSize = fontSize, lineHeight = lineHeight)
    } else {
        MaterialTheme.typography.bodySmall.copy(fontSize = fontSize, lineHeight = lineHeight)
    }
}


@Composable
internal fun historyTableTextColor(isHeader: Boolean): Color {
    return if (isHeader) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurface
    }
}


internal fun formatHistoryDatetime(timeStr: String): String {
    return TimeUtils.parseAndFormatToJst(timeStr, "yyyy/MM/dd HH:mm", "MM/dd HH:mm")
        ?: MISSING_TEXT
}


/**
 * 水平スクロールが有効な場合に使用する、スクロール可能な観測データ列（雨量、貯水量、流入量、放流量、貯水率）を
 * 1行で描画するComposable関数です。
 *
 * @param col2 2列目のテキスト（流域平均雨量）。
 * @param col3 3列目のテキスト（貯水量）。
 * @param col4 4列目のテキスト（流入量）。
 * @param col5 5列目のテキスト（放流量）。
 * @param col6 6列目のテキスト（貯水率）。
 * @param isHeader ヘッダー行であるかどうかのフラグ。trueの場合、フォントや色のスタイルがヘッダー用に変更されます。
 * @param columnWidths 列の幅設定。
 */
@Composable
internal fun HistoryTableScrollableRow(
    col2: String,
    col3: String,
    col4: String,
    col5: String,
    col6: String,
    isHeader: Boolean,
    columnWidths: HistoryTableColumnWidths
) {
    val textStyle = historyTableTextStyle(isHeader)
    val color = historyTableTextColor(isHeader)

    Row(
        modifier = Modifier
            .width(columnWidths.tableWidth - columnWidths.datetime)
            .padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HistoryTableCell(
            text = col2,
            style = textStyle,
            color = color,
            width = columnWidths.rainfall,
            isHeader = isHeader,
            textAlign = TextAlign.End
        )
        HistoryTableCell(
            text = col3,
            style = textStyle,
            color = color,
            width = columnWidths.volume,
            isHeader = isHeader,
            textAlign = TextAlign.End
        )
        HistoryTableCell(
            text = col4,
            style = textStyle,
            color = color,
            width = columnWidths.inflow,
            isHeader = isHeader,
            textAlign = TextAlign.End
        )
        HistoryTableCell(
            text = col5,
            style = textStyle,
            color = color,
            width = columnWidths.outflow,
            isHeader = isHeader,
            textAlign = TextAlign.End
        )
        HistoryTableCell(
            text = col6,
            style = textStyle,
            color = color,
            width = columnWidths.storage,
            isHeader = isHeader,
            textAlign = TextAlign.End
        )
    }
}


@Composable
internal fun BoxScope.HistoryTableFixedBorder() {
    val color = historyTableBorderColor()
    Canvas(modifier = Modifier.matchParentSize()) {
        val strokeWidth = 1.dp.toPx()
        val halfStroke = strokeWidth / 2f
        val s = this.size
        
        drawLine(
            color = color,
            start = Offset(halfStroke, halfStroke),
            end = Offset(s.width, halfStroke),
            strokeWidth = strokeWidth
        )
        
        drawLine(
            color = color,
            start = Offset(halfStroke, s.height - halfStroke),
            end = Offset(s.width, s.height - halfStroke),
            strokeWidth = strokeWidth
        )
        
        drawLine(
            color = color,
            start = Offset(halfStroke, halfStroke),
            end = Offset(halfStroke, s.height - halfStroke),
            strokeWidth = strokeWidth
        )
    }
}


@Composable
internal fun BoxScope.HistoryTableScrollableVerticalLines(columnWidths: HistoryTableColumnWidths) {
    val color = historyTableBorderColor()
    Canvas(modifier = Modifier.matchParentSize()) {
        val strokeWidth = 1.dp.toPx()
        val verticalLines = listOf(
            columnWidths.rainfall,
            columnWidths.rainfall + columnWidths.volume,
            columnWidths.rainfall + columnWidths.volume + columnWidths.inflow,
            columnWidths.rainfall + columnWidths.volume + columnWidths.inflow + columnWidths.outflow
        ).map { it.toPx() }

        verticalLines.forEach { x ->
            drawLine(
                color = color,
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = strokeWidth
            )
        }
    }
}


@Composable
internal fun BoxScope.HistoryTableScrollableBorder(columnWidths: HistoryTableColumnWidths) {
    val color = historyTableBorderColor()
    Canvas(modifier = Modifier.matchParentSize()) {
        val strokeWidth = 1.dp.toPx()
        val halfStroke = strokeWidth / 2f
        val s = this.size
        val verticalLines = listOf(
            columnWidths.rainfall,
            columnWidths.rainfall + columnWidths.volume,
            columnWidths.rainfall + columnWidths.volume + columnWidths.inflow,
            columnWidths.rainfall + columnWidths.volume + columnWidths.inflow + columnWidths.outflow
        ).map { it.toPx() }

        drawRect(
            color = color,
            topLeft = Offset(halfStroke, halfStroke),
            size = Size(
                width = (s.width - strokeWidth).coerceAtLeast(0f),
                height = (s.height - strokeWidth).coerceAtLeast(0f)
            ),
            style = Stroke(width = strokeWidth)
        )
        verticalLines.forEach { x ->
            drawLine(
                color = color,
                start = Offset(x, halfStroke),
                end = Offset(x, s.height - halfStroke),
                strokeWidth = strokeWidth
            )
        }
    }
}


/**
 * 観測履歴データ一覧を表示するカードコンポーネントです。
 *
 * データの展開/折りたたみ機能や、横幅が不足する場合のスクロール処理（日時列を固定、残りの列を水平スクロール）を内包しています。
 *
 * @param historicalData 表示対象の観測データリスト（[DamHistoricalData]のリスト）。
 * @param isExpanded カードが展開されているかどうかのフラグ。
 * @param onExpandToggle 展開/折りたたみボタンがタップされたときのコールバック。
 * @param modifier このコンポーネントに適用する [Modifier]。
 * @param isHistorical 過去データモードかどうかのフラグ。trueの場合は流域平均雨量の単位が一時間雨量になります。
 * @param rainfallUnit 流域平均雨量の単位表記文字列。
 * @param locale ロケール。
 * @param onShowAllPeriod 「全期間を表示」などのボタンが押されたときのコールバック。
 * @param onCollapsed カードが折りたたまれたときの追加の処理。
 */
@Composable
internal fun ObservationHistoryCard(
    historicalData: List<DamHistoricalData>,
    isExpanded: Boolean,
    onExpandToggle: () -> Unit,
    modifier: Modifier = Modifier,
    isHistorical: Boolean = false,
    rainfallUnit: String = "(mm/10min)",
    locale: Locale = Locale.US,
    onShowAllPeriod: () -> Unit,
    onCollapsed: (() -> Unit)? = null
) {
    
    val allReversed = remember(historicalData) { historicalData.reversed() }

    
    val latestMillis: Long = remember(historicalData) {
        allReversed.firstOrNull()?.time?.let { parseItemTimeMillis(it) } ?: 0L
    }

    
    val initialCutoffMillis: Long = remember(historicalData) {
        if (latestMillis == 0L) {
            0L
        } else {
            Calendar.getInstance(TimeUtils.JST_TIMEZONE).apply {
                timeInMillis = latestMillis
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                add(Calendar.HOUR_OF_DAY, -1)
            }.timeInMillis
        }
    }

    
    val initialDisplayCount: Int = remember(historicalData) {
        val idx = allReversed.indexOfFirst { item ->
            (parseItemTimeMillis(item.time) ?: Long.MAX_VALUE) < initialCutoffMillis
        }
        if (idx == -1) allReversed.size else idx
    }

    
    var displayedCount by remember(historicalData) { mutableIntStateOf(initialDisplayCount) }
    val tableScrollState = rememberScrollState()

    
    LaunchedEffect(isExpanded) {
        if (!isExpanded) {
            displayedCount = initialDisplayCount
            onCollapsed?.invoke()
        }
    }

    Card(
        modifier = modifier,
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
                    text = stringResource(id = R.string.main_history_data),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { heading() }
                )
                IconButton(
                    onClick = onExpandToggle,
                    modifier = Modifier.testTag(TestTags.HISTORY_EXPAND_BUTTON)
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = if (isExpanded) stringResource(R.string.desc_collapse) else stringResource(
                            R.string.desc_expand
                        ),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            if (isExpanded) {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val headerCol1 = stringResource(id = R.string.main_history_col_datetime)
                    val headerCol2 = if (isHistorical) {
                        stringResource(id = R.string.main_history_col_rainfall_per_hour)
                    } else {
                        stringResource(id = R.string.main_history_col_rainfall)
                    }
                    val headerCol3 = stringResource(id = R.string.main_history_col_volume)
                    val headerCol4 = stringResource(id = R.string.main_history_col_inflow)
                    val headerCol5 = stringResource(id = R.string.main_history_col_outflow)
                    val headerCol6 = stringResource(id = R.string.main_history_col_storage)
                    val columnWidths = rememberHistoryTableColumnWidths(
                        maxWidth,
                        listOf(headerCol1, headerCol2, headerCol3, headerCol4, headerCol5, headerCol6),
                        HISTORY_TABLE_FONT_SIZE
                    )
                    val displayItems =
                        if (isHistorical) historicalData else allReversed.take(displayedCount)
                    val needsHorizontalScroll = columnWidths.tableWidth > maxWidth

                    if (needsHorizontalScroll) {
                        
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Box(modifier = Modifier.width(columnWidths.datetime)) {
                                Column {
                                    Box(modifier = Modifier.padding(vertical = 1.dp)) {
                                        val headerStyle = historyTableTextStyle(true)
                                        val headerColor = historyTableTextColor(true)
                                        HistoryTableCell(
                                            text = headerCol1,
                                            style = headerStyle,
                                            color = headerColor,
                                            width = columnWidths.datetime,
                                            isHeader = true,
                                            textAlign = TextAlign.Start
                                        )
                                    }
                                    HorizontalDivider(
                                        thickness = 1.dp,
                                        color = historyTableBorderColor()
                                    )
                                    displayItems.forEach { item ->
                                        key(item.time) {
                                            Box(modifier = Modifier.padding(vertical = 1.dp)) {
                                                val dataStyle = historyTableTextStyle(false)
                                                val dataColor = historyTableTextColor(false)
                                                HistoryTableCell(
                                                    text = formatHistoryDatetime(item.time),
                                                    style = dataStyle,
                                                    color = dataColor,
                                                    width = columnWidths.datetime,
                                                    isHeader = false,
                                                    textAlign = TextAlign.Start
                                                )
                                            }
                                        }
                                    }
                                }
                                HistoryTableFixedBorder()
                            }
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .mouseDragScroll(tableScrollState, Orientation.Horizontal)
                                    .horizontalScroll(tableScrollState)
                            ) {
                                Box(
                                    modifier = Modifier.width(
                                        columnWidths.tableWidth - columnWidths.datetime
                                    )
                                ) {
                                    Column {
                                        HistoryTableScrollableRow(
                                            col2 = headerCol2,
                                            col3 = headerCol3,
                                            col4 = headerCol4,
                                            col5 = headerCol5,
                                            col6 = headerCol6,
                                            isHeader = true,
                                            columnWidths = columnWidths
                                        )
                                        HorizontalDivider(
                                            thickness = 1.dp,
                                            color = historyTableBorderColor()
                                        )
                                        displayItems.forEach { item ->
                                            key(item.time) {
                                                HistoryTableScrollableRow(
                                                    col2 = formatRainfall(item.catchmentAverageRainfall, locale),
                                                    col3 = formatValue(item.storageVolume, decimals = 0, locale = locale),
                                                    col4 = formatValue(item.inflow, decimals = 2, locale = locale),
                                                    col5 = formatValue(item.outflow, decimals = 2, locale = locale),
                                                    col6 = formatValue(item.storagePercentage, decimals = 2, locale = locale),
                                                    isHeader = false,
                                                    columnWidths = columnWidths
                                                )
                                            }
                                        }
                                    }
                                    HistoryTableScrollableBorder(columnWidths = columnWidths)
                                }
                            }
                        }
                    } else {
                        
                        Box(modifier = Modifier.width(columnWidths.tableWidth)) {
                            Column {
                                HistoryTableRow(
                                        col1 = headerCol1,
                                        col2 = headerCol2,
                                        col3 = headerCol3,
                                        col4 = headerCol4,
                                        col5 = headerCol5,
                                        col6 = headerCol6,
                                        isHeader = true,
                                        columnWidths = columnWidths
                                    )
                                    HorizontalDivider(
                                        thickness = 1.dp,
                                        color = historyTableBorderColor()
                                    )
                                    displayItems.forEach { item ->
                                        key(item.time) {
                                            HistoryTableRow(
                                                col1 = formatHistoryDatetime(item.time),
                                                col2 = formatRainfall(item.catchmentAverageRainfall, locale),
                                                col3 = formatValue(item.storageVolume, decimals = 0, locale = locale),
                                                col4 = formatValue(item.inflow, decimals = 2, locale = locale),
                                                col5 = formatValue(item.outflow, decimals = 2, locale = locale),
                                                col6 = formatValue(item.storagePercentage, decimals = 2, locale = locale),
                                                isHeader = false,
                                                columnWidths = columnWidths
                                            )
                                        }
                                    }
                                }
                                HistoryTableBorder(columnWidths = columnWidths)
                        }
                    }
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FullWidthGraphActionChip(
                        label = stringResource(id = R.string.main_history_load_all),
                        onClick = onShowAllPeriod,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}


/**
 * テーブルの全列（日時、雨量、貯水量、流入量、放流量、貯水率）を水平スクロールなしで
 * 1行に並べて描画するComposable関数です。
 *
 * @param col1 1列目のテキスト（日時）。
 * @param col2 2列目のテキスト（流域平均雨量）。
 * @param col3 3列目のテキスト（貯水量）。
 * @param col4 4列目のテキスト（流入量）。
 * @param col5 5列目のテキスト（放流量）。
 * @param col6 6列目のテキスト（貯水率）。
 * @param isHeader ヘッダー行であるかどうかのフラグ。
 * @param columnWidths 列の幅設定。
 */
@Composable
internal fun HistoryTableRow(
    col1: String,
    col2: String,
    col3: String,
    col4: String,
    col5: String,
    col6: String,
    isHeader: Boolean,
    columnWidths: HistoryTableColumnWidths = HISTORY_TABLE_BASE_COLUMN_WIDTHS
) {
    val fontSize = HISTORY_TABLE_FONT_SIZE
    val lineHeight = fontSize * 1.5f
    val textStyle = if (isHeader) {
        MaterialTheme.typography.labelSmall.copy(fontSize = fontSize, lineHeight = lineHeight)
    } else {
        MaterialTheme.typography.bodySmall.copy(fontSize = fontSize, lineHeight = lineHeight)
    }
    val color = if (isHeader) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    val rowContentDescription = if (isHeader) {
        "$col1, $col2, $col3, $col4, $col5, $col6"
    } else {
        "$col1, $col2, $col3, $col4, $col5, $col6"
    }
    Row(
        modifier = Modifier
            .width(columnWidths.tableWidth)
            .padding(vertical = 1.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = rowContentDescription
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        HistoryTableCell(
            text = col1,
            style = textStyle,
            color = color,
            width = columnWidths.datetime,
            isHeader = isHeader,
            textAlign = TextAlign.Start
        )
        HistoryTableCell(
            text = col2,
            style = textStyle,
            color = color,
            width = columnWidths.rainfall,
            isHeader = isHeader,
            textAlign = TextAlign.End
        )
        HistoryTableCell(
            text = col3,
            style = textStyle,
            color = color,
            width = columnWidths.volume,
            isHeader = isHeader,
            textAlign = TextAlign.End
        )
        HistoryTableCell(
            text = col4,
            style = textStyle,
            color = color,
            width = columnWidths.inflow,
            isHeader = isHeader,
            textAlign = TextAlign.End
        )
        HistoryTableCell(
            text = col5,
            style = textStyle,
            color = color,
            width = columnWidths.outflow,
            isHeader = isHeader,
            textAlign = TextAlign.End
        )
        HistoryTableCell(
            text = col6,
            style = textStyle,
            color = color,
            width = columnWidths.storage,
            isHeader = isHeader,
            textAlign = TextAlign.End
        )
    }
}

/**
 * テーブルのセル1個分のテキストを描画するComposable関数です。
 *
 * @param text 表示するセルテキスト。
 * @param style テキストスタイル。
 * @param color 文字色。
 * @param width セルの横幅。
 * @param isHeader ヘッダーセルかどうかのフラグ。
 * @param textAlign テキストの配置位置（左寄せ/右寄せなど）。
 */
@Composable
internal fun HistoryTableCell(
    text: String,
    style: TextStyle,
    color: Color,
    width: Dp,
    isHeader: Boolean,
    textAlign: TextAlign
) {
    val hasUnit = text.contains('\n')
    Text(
        text = if (isHeader && !hasUnit) "$text\n" else text,
        style = style,
        color = color,
        textAlign = textAlign,
        minLines = if (isHeader) 2 else 1,
        maxLines = if (isHeader) 2 else 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
        modifier = Modifier
            .width(width)
            .padding(horizontal = 2.dp)
    )
}

/**
 * テーブル全体の枠線（外枠と各列の垂直区切り線）を描画するための Canvas Composable です。
 *
 * @param columnWidths 列の幅設定。
 */
@Composable
internal fun BoxScope.HistoryTableBorder(columnWidths: HistoryTableColumnWidths = HISTORY_TABLE_BASE_COLUMN_WIDTHS) {
    val color = historyTableBorderColor()
    Canvas(modifier = Modifier.matchParentSize()) {
        val strokeWidth = 1.dp.toPx()
        val halfStroke = strokeWidth / 2f
        val size = this.size
        val verticalLines = listOf(
            columnWidths.datetime,
            columnWidths.datetime + columnWidths.rainfall,
            columnWidths.datetime + columnWidths.rainfall + columnWidths.volume,
            columnWidths.datetime + columnWidths.rainfall + columnWidths.volume + columnWidths.inflow,
            columnWidths.datetime + columnWidths.rainfall + columnWidths.volume + columnWidths.inflow + columnWidths.outflow
        ).map { it.toPx() }

        drawRect(
            color = color,
            topLeft = androidx.compose.ui.geometry.Offset(halfStroke, halfStroke),
            size = androidx.compose.ui.geometry.Size(
                width = (size.width - strokeWidth).coerceAtLeast(0f),
                height = (size.height - strokeWidth).coerceAtLeast(0f)
            ),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)
        )
        verticalLines.forEach { x ->
            drawLine(
                color = color,
                start = androidx.compose.ui.geometry.Offset(x, halfStroke),
                end = androidx.compose.ui.geometry.Offset(x, size.height - halfStroke),
                strokeWidth = strokeWidth
            )
        }
    }
}

@Composable
internal fun historyTableBorderColor(): Color =
    MaterialTheme.colorScheme.outline.copy(alpha = HISTORY_TABLE_BORDER_ALPHA)
