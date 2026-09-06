// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.tecogonaz.tcsameuradammonitor.R
import net.tecogonaz.tcsameuradammonitor.domain.model.DamHistoricalData
import net.tecogonaz.tcsameuradammonitor.ui.common.mouseDragScroll


/**
 * リアルタイムデータまたは過去データ（履歴データ）の観測値（雨量、貯水量、流入量、放流量、貯水率）を、
 * ヘッダー固定 of 全画面テーブル形式で表示する画面のメインComposable関数です。
 *
 * 画面の幅が狭い場合は、日時列を左側に固定し、残り5列（雨量〜貯水率）を水平スクロール可能にするレスポンシブ表示に対応します。
 * 縦スクロールおよび横スクロールは、タッチ、マウスホイール、およびマウス左ボタンドラッグをサポートします。
 *
 * @param viewModel UI状態の保持およびデータ提供を担当する [MainViewModel]。
 * @param onBackClick 画面左上の戻るボタンがタップされたときの遷移コールバック。メイン画面へ戻ります。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ObservationHistoryListScreen(
    viewModel: MainViewModel,
    onBackClick: () -> Unit
) {
    BackHandler(onBack = onBackClick)

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val sourceData = when {
        uiState.isSudmonitorHistoryMode -> uiState.sudmonitorHistoryVisibleData
        uiState.isHistoricalMode -> uiState.historicalVisibleData
        else -> uiState.damData?.historicalData.orEmpty()
    }

    var isLoading by remember { mutableStateOf(true) }
    var displayItems by remember { mutableStateOf<List<DamHistoricalData>>(emptyList()) }

    LaunchedEffect(sourceData, uiState.isHistoricalMode, uiState.isSudmonitorHistoryMode) {
        isLoading = true
        displayItems = withContext(Dispatchers.Default) {
            sourceData.asReversed().toList()
        }
        isLoading = false
    }

    Scaffold(
        modifier = Modifier.testTag(TestTags.OBSERVATION_HISTORY_LIST_ROOT),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_observation_history_list)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.desc_back)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Card(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag(TestTags.OBSERVATION_HISTORY_LIST_TABLE)
                    ) {
                        val headerCol1 = stringResource(id = R.string.main_history_col_datetime)
                        val headerCol2 = if (uiState.isHistoricalMode || uiState.isSudmonitorHistoryMode) {
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
                        val tableScrollState = rememberScrollState()
                        val needsHorizontalScroll = columnWidths.tableWidth > maxWidth

                        if (needsHorizontalScroll) {
                            
                            Box(modifier = Modifier.fillMaxSize()) {
                                Column {
                                    SplitHeaderRow(
                                        col1 = headerCol1,
                                        col2 = headerCol2,
                                        col3 = headerCol3,
                                        col4 = headerCol4,
                                        col5 = headerCol5,
                                        col6 = headerCol6,
                                        columnWidths = columnWidths,
                                        tableScrollState = tableScrollState
                                    )
                                    HorizontalDivider(
                                        thickness = 1.dp,
                                        color = historyTableBorderColor()
                                    )
                                    if (displayItems.isEmpty()) {
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxWidth(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = stringResource(R.string.observation_history_empty),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    } else {
                                        val listState = rememberLazyListState()
                                        LazyColumn(
                                            state = listState,
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxWidth()
                                                .mouseDragScroll(listState, Orientation.Vertical)
                                        ) {
                                            itemsIndexed(
                                                items = displayItems,
                                                key = { index, item -> "${item.time}_$index" }
                                            ) { _, item ->
                                                SplitDataRow(
                                                    col1 = formatHistoryDatetime(item.time),
                                                    col2 = formatRainfall(item.catchmentAverageRainfall),
                                                    col3 = formatValue(item.storageVolume, decimals = 0),
                                                    col4 = formatValue(item.inflow, decimals = 2),
                                                    col5 = formatValue(item.outflow, decimals = 2),
                                                    col6 = formatValue(item.storagePercentage, decimals = 2),
                                                    columnWidths = columnWidths,
                                                    tableScrollState = tableScrollState
                                                )
                                            }
                                        }
                                    }
                                }
                                HistoryTableListSplitBorder(columnWidths = columnWidths)
                            }
                        } else {
                            
                            Box(
                                modifier = Modifier
                                    .width(columnWidths.tableWidth)
                                    .fillMaxHeight()
                            ) {
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
                                    if (displayItems.isEmpty()) {
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = stringResource(R.string.observation_history_empty),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    } else {
                                        val listState = rememberLazyListState()
                                        LazyColumn(
                                            state = listState,
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxSize()
                                                .mouseDragScroll(listState, Orientation.Vertical),
                                            contentPadding = PaddingValues(vertical = 8.dp)
                                        ) {
                                            itemsIndexed(
                                                items = displayItems,
                                                key = { index, item -> "${item.time}_$index" }
                                            ) { _, item ->
                                                HistoryTableRow(
                                                    col1 = formatHistoryDatetime(item.time),
                                                    col2 = formatRainfall(item.catchmentAverageRainfall),
                                                    col3 = formatValue(item.storageVolume, decimals = 0),
                                                    col4 = formatValue(item.inflow, decimals = 2),
                                                    col5 = formatValue(item.outflow, decimals = 2),
                                                    col6 = formatValue(item.storagePercentage, decimals = 2),
                                                    isHeader = false,
                                                    columnWidths = columnWidths
                                                )
                                            }
                                        }
                                    }
                                }
                                HistoryTableBorder(columnWidths = columnWidths)
                            }
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun BoxScope.HistoryTableListSplitBorder(columnWidths: HistoryTableColumnWidths) {
    val color = historyTableBorderColor()
    Canvas(modifier = Modifier.matchParentSize()) {
        val strokeWidth = 1.dp.toPx()
        val halfStroke = strokeWidth / 2f
        val s = this.size
        
        drawRect(
            color = color,
            topLeft = Offset(halfStroke, halfStroke),
            size = Size(
                width = (s.width - strokeWidth).coerceAtLeast(0f),
                height = (s.height - strokeWidth).coerceAtLeast(0f)
            ),
            style = Stroke(width = strokeWidth)
        )
        
        val dtBoundary = columnWidths.datetime.toPx()
        drawLine(
            color = color,
            start = Offset(dtBoundary, halfStroke),
            end = Offset(dtBoundary, s.height - halfStroke),
            strokeWidth = strokeWidth
        )
    }
}


@Composable
private fun SplitHeaderRow(
    col1: String,
    col2: String,
    col3: String,
    col4: String,
    col5: String,
    col6: String,
    columnWidths: HistoryTableColumnWidths,
    tableScrollState: ScrollState
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.width(columnWidths.datetime)) {
            Box(modifier = Modifier.padding(vertical = 1.dp)) {
                val textStyle = historyTableTextStyle(true)
                val textColor = historyTableTextColor(true)
                HistoryTableCell(
                    text = col1,
                    style = textStyle,
                    color = textColor,
                    width = columnWidths.datetime,
                    isHeader = true,
                    textAlign = TextAlign.Start
                )
            }
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .mouseDragScroll(tableScrollState, Orientation.Horizontal)
                .horizontalScroll(tableScrollState)
        ) {
            Box(modifier = Modifier.width(columnWidths.tableWidth - columnWidths.datetime)) {
                HistoryTableScrollableRow(
                    col2 = col2,
                    col3 = col3,
                    col4 = col4,
                    col5 = col5,
                    col6 = col6,
                    isHeader = true,
                    columnWidths = columnWidths
                )
                HistoryTableScrollableVerticalLines(columnWidths = columnWidths)
            }
        }
    }
}


@Composable
private fun SplitDataRow(
    col1: String,
    col2: String,
    col3: String,
    col4: String,
    col5: String,
    col6: String,
    columnWidths: HistoryTableColumnWidths,
    tableScrollState: ScrollState
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.width(columnWidths.datetime)) {
            Box(modifier = Modifier.padding(vertical = 1.dp)) {
                val textStyle = historyTableTextStyle(false)
                val textColor = historyTableTextColor(false)
                HistoryTableCell(
                    text = col1,
                    style = textStyle,
                    color = textColor,
                    width = columnWidths.datetime,
                    isHeader = false,
                    textAlign = TextAlign.Start
                )
            }
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .mouseDragScroll(tableScrollState, Orientation.Horizontal)
                .horizontalScroll(tableScrollState)
        ) {
            Box(modifier = Modifier.width(columnWidths.tableWidth - columnWidths.datetime)) {
                HistoryTableScrollableRow(
                    col2 = col2,
                    col3 = col3,
                    col4 = col4,
                    col5 = col5,
                    col6 = col6,
                    isHeader = false,
                    columnWidths = columnWidths
                )
                HistoryTableScrollableVerticalLines(columnWidths = columnWidths)
            }
        }
    }
}
