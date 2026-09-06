// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.ui.debuglog

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * デバッグログ共有用一時ファイル作成クラス [DebugLogShareFileCreator] の Instrumentation テストクラス。
 * キャッシュディレクトリ配下への CSV データ出力、および共有を想定した FileProvider を経由する `content://` スキーム Uri の
 * 払い出し機能、および SAF（Storage Access Framework）経由での CSV 出力処理が Android OS 上で正しく機能することを検証します。
 */
@RunWith(AndroidJUnit4::class)
class DebugLogShareFileCreatorAndroidTest {
    private lateinit var context: Context
    private lateinit var cacheDir: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        cacheDir = File(context.cacheDir, DebugLogShareFileCreator.DEBUG_LOG_CACHE_DIR)
        cacheDir.deleteRecursively()
    }

    @After
    fun tearDown() {
        cacheDir.deleteRecursively()
    }

    @Test
    fun createShareFile_writesCsvUnderCacheAndReturnsFileProviderUri() {
        val creator = DebugLogShareFileCreator(context)

        val uri = creator.createShareFile("timestamp,message,details\nrow\n", "debug-log.csv")

        val output = File(cacheDir, "debug-log.csv")
        assertTrue(output.exists())
        assertEquals("timestamp,message,details\nrow\n", output.readText(Charsets.UTF_8))
        assertEquals("content", uri.scheme)
        assertEquals("${context.packageName}.fileprovider", uri.authority)
    }

    @Test
    fun saveLogCsv_writesUtf8TextToContentUri() {
        val creator = DebugLogShareFileCreator(context)
        val output = File(cacheDir.apply { mkdirs() }, "saf-output.csv")
        val uri = Uri.fromFile(output)

        creator.saveLogCsv(uri, "timestamp,message,details\n日本語\n")

        assertEquals("timestamp,message,details\n日本語\n", output.readText(Charsets.UTF_8))
    }
}
