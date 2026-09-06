// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.ui.historicalmanage

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.tecogonaz.tcsameuradammonitor.R
import net.tecogonaz.tcsameuradammonitor.domain.model.HistoricalSearchMeta
import net.tecogonaz.tcsameuradammonitor.ui.common.MISSING_PERCENTAGE_TEXT
import net.tecogonaz.tcsameuradammonitor.ui.common.mouseDragScroll
import net.tecogonaz.tcsameuradammonitor.ui.main.MainViewModel
import net.tecogonaz.tcsameuradammonitor.ui.main.TestTags
import net.tecogonaz.tcsameuradammonitor.util.LocaleUtils
import net.tecogonaz.tcsameuradammonitor.util.TimeUtils
import java.util.Calendar
import java.util.Locale


/**
 * 保存されたすべての過去データ検索結果（メタデータ一覧）を表示し、
 * 検索結果の削除や一覧の並び替え（ドラッグ/ボタン操作）、表示対象過去データの切り替えを行う管理画面のメインComposable関数です。
 *
 * リスト表示はタッチ、マウスホイール、およびマウス左ボタンドラッグによる滑らかなスクロールに対応しています。
 *
 * @param viewModel UI状態と過去データの永続化管理を担当する [MainViewModel]。
 * @param onBackClick 画面左上の戻るボタンがタップされたときの遷移コールバック。メイン画面へ戻ります。
 * @param onShowHistoricalData 保存された特定の過去データ検索結果をアクティブにしてメイン画面で表示するためのコールバック。
 * @param showTopAppBar 画面上部の TopAppBar を表示するかどうかのフラグ。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoricalManageScreen(
    viewModel: MainViewModel,
    onBackClick: () -> Unit,
    onShowHistoricalData: (Long) -> Unit,
    showTopAppBar: Boolean = true
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val appLocale = LocaleUtils.effectiveLocale(LocalContext.current)
    val appLocaleTag = appLocale.toLanguageTag()
    
    var topMenuExpanded by remember { mutableStateOf(false) }
    
    var showDeleteAllDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.snackbarMessage.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    Scaffold(
        modifier = Modifier.testTag(TestTags.HISTORICAL_MANAGE_ROOT),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (showTopAppBar) {
                TopAppBar(
                    title = { Text(stringResource(R.string.title_historical_manage)) },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = stringResource(R.string.desc_back)
                            )
                        }
                    },
                    actions = {
                        Box {
                            IconButton(
                                onClick = { topMenuExpanded = true },
                                modifier = Modifier.testTag(TestTags.HISTORICAL_MANAGE_TOP_MENU_BUTTON)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.MoreVert,
                                    contentDescription = stringResource(R.string.desc_more_options)
                                )
                            }
                            DropdownMenu(
                                expanded = topMenuExpanded,
                                onDismissRequest = { topMenuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    modifier = Modifier.testTag(TestTags.HISTORICAL_MANAGE_DELETE_ALL_MENU_ITEM),
                                    text = { Text(stringResource(R.string.historical_manage_menu_delete_all)) },
                                    onClick = {
                                        topMenuExpanded = false
                                        showDeleteAllDialog = true
                                    }
                                )
                            }
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        if (uiState.allHistoricalMetaList.isEmpty()) {
            
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.historical_manage_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            DraggableHistoricalList(
                items = uiState.allHistoricalMetaList,
                currentHistoricalMetaId = uiState.historicalDamMeta?.id ?: 0L,
                isHistoricalMode = uiState.isHistoricalMode,
                getDamName = { damConfigId ->
                    viewModel.getDamConfig(damConfigId)?.let {
                        LocaleUtils.normalDamName(appLocaleTag, it)
                    } ?: damConfigId
                },
                locale = appLocale,
                onReorder = { newOrderIds -> viewModel.reorderHistoricalMeta(newOrderIds) },
                onShowItem = onShowHistoricalData,
                onPinItem = { metaId -> viewModel.setPinned(metaId, true) },
                onUnpinItem = { metaId -> viewModel.setPinned(metaId, false) },
                onDeleteItem = { metaId -> viewModel.deleteHistoricalData(metaId) },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
        }
    }

    
    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            title = { Text(stringResource(R.string.historical_manage_delete_all_confirm_title)) },
            text = { Text(stringResource(R.string.historical_manage_delete_all_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteAllDialog = false
                    viewModel.deleteAllHistoricalData()
                }) {
                    Text(stringResource(R.string.historical_manage_menu_delete_all))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}


@Composable
private fun DraggableHistoricalList(
    items: List<HistoricalSearchMeta>,
    currentHistoricalMetaId: Long,
    isHistoricalMode: Boolean,
    getDamName: (String) -> String,
    locale: Locale,
    onReorder: (List<Long>) -> Unit,
    onShowItem: (Long) -> Unit,
    onPinItem: (Long) -> Unit,
    onUnpinItem: (Long) -> Unit,
    onDeleteItem: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    
    val mutableItems = remember { items.toMutableStateList() }
    LaunchedEffect(items) {
        mutableItems.clear()
        mutableItems.addAll(items)
    }

    val currentOnReorder by rememberUpdatedState(onReorder)
    val listState = rememberLazyListState()

    LazyColumn(
        state = listState,
        modifier = modifier.mouseDragScroll(listState, Orientation.Vertical)
    ) {
        itemsIndexed(mutableItems, key = { _, item -> item.id }) { idx, item ->

            
            val onMoveToTop: () -> Unit = {
                val reordered = mutableItems.toMutableList()
                reordered.removeAt(idx)
                reordered.add(0, item)
                currentOnReorder(reordered.map { it.id })
            }
            val onMoveUp: () -> Unit = {
                val reordered = mutableItems.toMutableList()
                reordered.removeAt(idx)
                reordered.add(idx - 1, item)
                currentOnReorder(reordered.map { it.id })
            }
            val onMoveDown: () -> Unit = {
                val reordered = mutableItems.toMutableList()
                reordered.removeAt(idx)
                reordered.add(idx + 1, item)
                currentOnReorder(reordered.map { it.id })
            }
            val onMoveToBottom: () -> Unit = {
                val reordered = mutableItems.toMutableList()
                reordered.removeAt(idx)
                reordered.add(item)
                currentOnReorder(reordered.map { it.id })
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                HistoricalManageItem(
                    meta = item,
                    index = idx,
                    totalCount = mutableItems.size,
                    isSelected = item.id == currentHistoricalMetaId && isHistoricalMode,
                    damName = getDamName(item.damConfigId),
                    locale = locale,
                    onShowItem = onShowItem,
                    onPinItem = { onPinItem(item.id) },
                    onUnpinItem = { onUnpinItem(item.id) },
                    onMoveToTop = onMoveToTop,
                    onMoveUp = onMoveUp,
                    onMoveDown = onMoveDown,
                    onMoveToBottom = onMoveToBottom,
                    onDeleteItem = onDeleteItem
                )
            }
        }
    }
}


@Composable
private fun HistoricalManageItem(
    meta: HistoricalSearchMeta,
    index: Int,
    totalCount: Int,
    isSelected: Boolean,
    damName: String,
    locale: Locale,
    onShowItem: (Long) -> Unit,
    onPinItem: () -> Unit,
    onUnpinItem: () -> Unit,
    onMoveToTop: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onMoveToBottom: () -> Unit,
    onDeleteItem: (Long) -> Unit
) {
    
    var menuExpanded by remember { mutableStateOf(false) }
    
    var showDeleteDialog by remember { mutableStateOf(false) }
    val currentDatasetDesc = stringResource(R.string.desc_current_dataset)

    val line2 = run {
        fun String.toYmdSlash(): String =
            if (length == 8) "${substring(0, 4)}/${substring(4, 6)}/${substring(6, 8)}" else this
        
        fun String.toNextDayYmdSlash(): String {
            if (length != 8) return toYmdSlash()
            val dateMillis = TimeUtils.parseJstMillis(this, "yyyyMMdd")
                ?: return toYmdSlash()
            val cal = Calendar.getInstance(TimeUtils.JST_TIMEZONE)
            cal.timeInMillis = dateMillis
            cal.add(Calendar.DAY_OF_MONTH, 1)
            val next = TimeUtils.formatToJst(cal.timeInMillis, "yyyyMMdd")
            return if (next.length == 8) "${next.substring(0, 4)}/${next.substring(4, 6)}/${next.substring(6, 8)}" else next
        }
        "${meta.searchBgnDate.toYmdSlash()} 01:00" +
            " - " +
            TimeUtils.appendJstSuffix("${meta.searchEndDate.toNextDayYmdSlash()} 00:00")
    }
    val line3 = run {
        val startPct = meta.dataStartStoragePct
        val endPct = meta.dataEndStoragePct
        val minPct = meta.dataMinStoragePct
        val maxPct = meta.dataMaxStoragePct
        if (startPct != null && endPct != null) {
            val rangeStr = if (minPct != null && maxPct != null) {
                " (${String.format(locale, "%.2f", minPct)}% ~ ${String.format(locale, "%.2f", maxPct)}%)"
            } else ""
            "${String.format(locale, "%.2f", startPct)}% → ${String.format(locale, "%.2f", endPct)}%$rangeStr"
        } else MISSING_PERCENTAGE_TEXT
    }

    
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("${TestTags.HISTORICAL_MANAGE_ITEM_PREFIX}${meta.id}")
                    .clickable(
                        onClickLabel = stringResource(R.string.historical_manage_menu_show),
                        role = Role.Button
                    ) { onShowItem(meta.id) }
            .semantics(mergeDescendants = true) {
                contentDescription = listOfNotNull(
                    damName,
                    line2.takeIf { it.isNotEmpty() },
                    line3.takeIf { it.isNotEmpty() }
                ).joinToString(". ")
                if (isSelected) {
                    selected = true
                    stateDescription = currentDatasetDesc
                }
            }
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        
        Icon(
            imageVector = if (meta.isPinned) Icons.Outlined.PushPin else Icons.Outlined.Timeline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .size(28.dp)
        )

        
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(
                text = damName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (line2.isNotEmpty()) {
                Text(
                    text = line2,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            if (line3.isNotEmpty()) {
                Text(
                    text = line3,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(modifier = Modifier.width(4.dp))

        
        Box {
            IconButton(
                onClick = { menuExpanded = true },
                modifier = Modifier.testTag("${TestTags.HISTORICAL_MANAGE_ITEM_MENU_PREFIX}${meta.id}")
            ) {
                Icon(
                    imageVector = Icons.Outlined.MoreVert,
                    contentDescription = stringResource(R.string.desc_more_options)
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                
                DropdownMenuItem(
                    modifier = Modifier.testTag("${TestTags.HISTORICAL_MANAGE_SHOW_MENU_PREFIX}${meta.id}"),
                    text = { Text(stringResource(R.string.historical_manage_menu_show)) },
                    onClick = {
                        menuExpanded = false
                        onShowItem(meta.id)
                    }
                )
                if (meta.isPinned) {
                    DropdownMenuItem(
                        modifier = Modifier.testTag("${TestTags.HISTORICAL_MANAGE_UNPIN_MENU_PREFIX}${meta.id}"),
                        text = { Text(stringResource(R.string.historical_manage_menu_unpin)) },
                        onClick = {
                            menuExpanded = false
                            onUnpinItem()
                        }
                    )
                } else {
                    DropdownMenuItem(
                        modifier = Modifier.testTag("${TestTags.HISTORICAL_MANAGE_PIN_MENU_PREFIX}${meta.id}"),
                        text = { Text(stringResource(R.string.historical_manage_menu_pin)) },
                        onClick = {
                            menuExpanded = false
                            onPinItem()
                        }
                    )
                }
                HorizontalDivider()
                
                DropdownMenuItem(
                    modifier = Modifier.testTag("${TestTags.HISTORICAL_MANAGE_MOVE_TOP_MENU_PREFIX}${meta.id}"),
                    text = { Text(stringResource(R.string.historical_manage_menu_move_to_top)) },
                    enabled = index > 1,
                    onClick = {
                        menuExpanded = false
                        onMoveToTop()
                    }
                )
                
                DropdownMenuItem(
                    modifier = Modifier.testTag("${TestTags.HISTORICAL_MANAGE_MOVE_UP_MENU_PREFIX}${meta.id}"),
                    text = { Text(stringResource(R.string.historical_manage_menu_move_up)) },
                    enabled = index > 0,
                    onClick = {
                        menuExpanded = false
                        onMoveUp()
                    }
                )
                
                DropdownMenuItem(
                    modifier = Modifier.testTag("${TestTags.HISTORICAL_MANAGE_MOVE_DOWN_MENU_PREFIX}${meta.id}"),
                    text = { Text(stringResource(R.string.historical_manage_menu_move_down)) },
                    enabled = index < totalCount - 1,
                    onClick = {
                        menuExpanded = false
                        onMoveDown()
                    }
                )
                
                DropdownMenuItem(
                    modifier = Modifier.testTag("${TestTags.HISTORICAL_MANAGE_MOVE_BOTTOM_MENU_PREFIX}${meta.id}"),
                    text = { Text(stringResource(R.string.historical_manage_menu_move_to_bottom)) },
                    enabled = index < totalCount - 2,
                    onClick = {
                        menuExpanded = false
                        onMoveToBottom()
                    }
                )
                HorizontalDivider()
                
                DropdownMenuItem(
                    modifier = Modifier.testTag("${TestTags.HISTORICAL_MANAGE_DELETE_MENU_PREFIX}${meta.id}"),
                    text = { Text(stringResource(R.string.historical_manage_menu_delete)) },
                    onClick = {
                        menuExpanded = false
                        showDeleteDialog = true
                    }
                )
            }
        }
    }

    
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.historical_manage_delete_confirm_title)) },
            text = { Text(stringResource(R.string.historical_manage_delete_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    onDeleteItem(meta.id)
                }) {
                    Text(stringResource(R.string.historical_manage_menu_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}
