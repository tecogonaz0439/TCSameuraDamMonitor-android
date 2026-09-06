// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.ui.debuglog

import android.net.Uri
import app.cash.turbine.test
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.tecogonaz.tcsameuradammonitor.domain.model.DebugLogEntry
import net.tecogonaz.tcsameuradammonitor.testutil.FakeDebugLogRepository
import net.tecogonaz.tcsameuradammonitor.testutil.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * [DebugLogViewModel] のユニットテストクラス。
 * デバッグログのFlow配信、全削除機能、CSV形式へのエスケープや保存処理、
 * 共有用一時ファイルの作成機能が正しく動作することを検証します。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DebugLogViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun allEntries_emitsRepositoryEntriesAndDeleteAllClearsThem() = runTest(mainDispatcherRule.testDispatcher) {
        val repository = FakeDebugLogRepository()
        val viewModel = DebugLogViewModel(repository, FakeDebugLogShareFileCreator())
        val entry = DebugLogEntry(id = 1L, timestampMillis = 100L, message = "Loaded.")

        viewModel.allEntries.test {
            assertEquals(emptyList<DebugLogEntry>(), awaitItem())

            repository.emitEntries(listOf(entry))
            assertEquals(listOf(entry), awaitItem())

            viewModel.deleteAll()
            advanceUntilIdle()
            assertEquals(emptyList<DebugLogEntry>(), awaitItem())
        }
    }

    @Test
    fun saveLogCsv_escapesCommaQuoteCrLfAndEmptyDetails() = runTest(mainDispatcherRule.testDispatcher) {
        val uri = mockk<Uri>()
        val shareFileCreator = FakeDebugLogShareFileCreator()
        val viewModel = DebugLogViewModel(FakeDebugLogRepository(), shareFileCreator)

        viewModel.saveLogCsv(
            uri = uri,
            entries = listOf(
                DebugLogEntry(
                    id = 1L,
                    timestampMillis = 0L,
                    message = "comma,message",
                    details = "quote \" detail"
                ),
                DebugLogEntry(
                    id = 2L,
                    timestampMillis = 60_000L,
                    message = "line\r\nbreak",
                    details = ""
                )
            )
        )

        val csv = shareFileCreator.savedText.single().second
        assertEquals(uri, shareFileCreator.savedText.single().first)
        assertTrue(csv.startsWith("timestamp,message,details\n"))
        assertTrue(csv.contains("1970-01-01T09:00:00+09:00,\"comma,message\",\"quote \"\" detail\""))
        assertTrue(csv.contains("1970-01-01T09:01:00+09:00,\"line\r\nbreak\","))
    }

    @Test
    fun createShareFile_usesCsvTextAndFileName() = runTest(mainDispatcherRule.testDispatcher) {
        val uri = mockk<Uri>()
        val shareFileCreator = FakeDebugLogShareFileCreator(shareUri = uri)
        val viewModel = DebugLogViewModel(FakeDebugLogRepository(), shareFileCreator)

        val result = viewModel.createShareFile(
            entries = listOf(DebugLogEntry(id = 1L, timestampMillis = 0L, message = "Loaded.")),
            fileName = "debug.csv"
        )

        assertEquals(uri, result)
        assertEquals("debug.csv", shareFileCreator.shareFileName)
        assertTrue(shareFileCreator.shareText.startsWith("timestamp,message,details\n"))
        assertTrue(shareFileCreator.shareText.contains("Loaded."))
    }

    /**
     * テスト用のデバッグログ共有ファイル作成フェイク。
     * CSVデータの出力内容や出力先 Uri をインメモリに保存し、ファイルシステムや Intent に依存せずに検証を行えるようにします。
     */
    private class FakeDebugLogShareFileCreator(
        private val shareUri: Uri = mockk()
    ) : DebugLogShareFileCreator(mockk()) {
        val savedText = mutableListOf<Pair<Uri, String>>()
        var shareText = ""
            private set
        var shareFileName = ""
            private set

        override fun saveLogCsv(uri: Uri, text: String) {
            savedText += uri to text
        }

        override fun createShareFile(text: String, fileName: String): Uri {
            shareText = text
            shareFileName = fileName
            return shareUri
        }
    }
}
