// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.ui.debuglog

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import net.tecogonaz.tcsameuradammonitor.domain.model.DebugLogEntry
import net.tecogonaz.tcsameuradammonitor.domain.repository.DebugLogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import net.tecogonaz.tcsameuradammonitor.util.TimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject


/**
 * デバッグログ画面（DebugLogScreen）のビジネスロジックを管理する [ViewModel] です。
 *
 * 蓄積された動作ログ（デバッグログ）の全件取得 [allEntries]、ログの全削除、
 * およびデバッグ用CSVファイルへの保存や外部共有のためのファイル生成などの処理を提供します。
 *
 * @property debugLogRepository デバッグログの保存・取得・削除を処理するリポジトリ。
 * @property shareFileCreator 共有用CSVファイルを生成・書き込みするためのヘルパー。
 */
@HiltViewModel
class DebugLogViewModel @Inject constructor(
    private val debugLogRepository: DebugLogRepository,
    private val shareFileCreator: DebugLogShareFileCreator
) : ViewModel() {

    
    val allEntries: StateFlow<List<DebugLogEntry>> =
        debugLogRepository.getAllEntriesFlow()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList()
            )

    
    fun deleteAll() {
        viewModelScope.launch {
            debugLogRepository.deleteAll()
        }
    }

    
    suspend fun saveLogCsv(uri: Uri, entries: List<DebugLogEntry>) = withContext(Dispatchers.IO) {
        shareFileCreator.saveLogCsv(uri, buildShareText(entries))
    }

    
    suspend fun createShareFile(entries: List<DebugLogEntry>, fileName: String): Uri =
        withContext(Dispatchers.IO) {
            shareFileCreator.createShareFile(buildShareText(entries), fileName)
    }

    
    fun generateDebugLogFileName(): String {
        val timestamp = TimeUtils.formatToJst(System.currentTimeMillis(), "yyyyMMdd'T'HHmmssZ").replace("+", "_")
        return "TCSameuraDamMonitor-DebugLog-$timestamp.csv"
    }

    private fun buildShareText(entries: List<DebugLogEntry>): String {
        val rows = entries.joinToString("\n") { entry ->
            listOf(
                TimeUtils.formatToJstIso8601(entry.timestampMillis),
                entry.message,
                entry.details
            ).joinToString(",") { it.toCsvField() }
        }
        return "timestamp,message,details\n$rows\n"
    }

    private fun String.toCsvField(): String {
        val escaped = replace("\"", "\"\"")
        val needsQuote = any { it == ',' || it == '"' || it == '\r' || it == '\n' }
        return if (needsQuote) "\"$escaped\"" else escaped
    }

}
