// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.ui.debuglog

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.tecogonaz.tcsameuradammonitor.R
import net.tecogonaz.tcsameuradammonitor.domain.model.DebugLogEntry
import net.tecogonaz.tcsameuradammonitor.ui.common.mouseDragScroll
import net.tecogonaz.tcsameuradammonitor.util.TimeUtils
import kotlinx.coroutines.launch


/**
 * システムの動作ログ（自動更新やデータロードの状況など）を新しい順に一覧表示するデバッグログ画面のメインComposable関数です。
 *
 * ログのCSVファイル保存、外部共有（インテントによるCSV送信）、および全件削除ダイアログ制御を内包しています。
 * マウス左ボタンドラッグやフリック操作による滑らかな縦スクロールをサポートします。
 *
 * @param viewModel デバッグログの状態とCSV出力を管理する [DebugLogViewModel]。
 * @param onBackClick 画面左上の戻るボタンがタップされたときの遷移コールバック。デバッグ設定画面へ戻ります。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugLogScreen(
    viewModel: DebugLogViewModel,
    onBackClick: () -> Unit
) {
    val entries by viewModel.allEntries.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val shareSubject = stringResource(R.string.debug_log_share_subject)

    var showDeleteConfirm by remember { mutableStateOf(false) }

    
    val saveFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        uri?.let { targetUri ->
            coroutineScope.launch {
                runCatching { viewModel.saveLogCsv(targetUri, entries) }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_activity_debug_log)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = stringResource(R.string.desc_back)
                            )
                    }
                },
                actions = {
                    
                    IconButton(onClick = {
                        saveFileLauncher.launch(viewModel.generateDebugLogFileName())
                    }) {
                        Icon(
                            imageVector = Icons.Outlined.Save,
                            contentDescription = stringResource(R.string.debug_log_menu_save)
                        )
                    }
                    
                    IconButton(onClick = {
                        val fileName = viewModel.generateDebugLogFileName()
                        coroutineScope.launch {
                            runCatching {
                                val fileUri = viewModel.createShareFile(entries, fileName)
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/csv"
                                    putExtra(
                                        Intent.EXTRA_SUBJECT,
                                        shareSubject
                                    )
                                    putExtra(Intent.EXTRA_STREAM, fileUri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, null))
                            }
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Outlined.Share,
                            contentDescription = stringResource(R.string.debug_log_menu_share)
                        )
                    }
                    
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = stringResource(R.string.debug_log_menu_delete_all)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        if (entries.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .semantics { liveRegion = LiveRegionMode.Polite }
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.debug_log_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            val listState = rememberLazyListState()
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .mouseDragScroll(listState, Orientation.Vertical),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(entries, key = { it.id }) { entry ->
                    DebugLogItem(entry = entry)
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.debug_log_delete_confirm_title)) },
            text = { Text(stringResource(R.string.debug_log_delete_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteAll()
                    showDeleteConfirm = false
                }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }
}


@Composable
private fun DebugLogItem(entry: DebugLogEntry) {
    val dateStr = remember(entry.timestampMillis) {
        TimeUtils.formatToJstIso8601(entry.timestampMillis)
    }
    val contentDesc = listOfNotNull(
        dateStr,
        entry.message,
        entry.details.takeIf { it.isNotEmpty() }
    ).joinToString(". ")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = contentDesc }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = dateStr,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = entry.message,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 2.dp)
        )
        if (entry.details.isNotEmpty()) {
            Text(
                text = entry.details,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}
