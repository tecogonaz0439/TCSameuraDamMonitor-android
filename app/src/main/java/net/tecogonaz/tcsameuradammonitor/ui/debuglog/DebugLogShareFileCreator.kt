// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.ui.debuglog

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject


/**
 * アプリのデバッグログ（CSV形式）をストレージに保存、または一時ファイルとして生成して
 * 他のアプリと共有するための [Uri] を作成するヘルパークラスです。
 *
 * @property context アプリケーションコンテキスト。
 */
open class DebugLogShareFileCreator @Inject constructor(
    @ApplicationContext private val context: Context
) {
    open fun saveLogCsv(uri: Uri, text: String) {
        context.contentResolver.openOutputStream(uri)?.use { stream ->
            stream.write(text.toByteArray(Charsets.UTF_8))
        }
    }

    open fun createShareFile(text: String, fileName: String): Uri {
        val debugLogCacheDir = File(context.cacheDir, DEBUG_LOG_CACHE_DIR).apply {
            mkdirs()
        }
        val cacheFile = File(debugLogCacheDir, fileName)
        cacheFile.writeText(text, Charsets.UTF_8)
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            cacheFile
        )
    }

    companion object {
        const val DEBUG_LOG_CACHE_DIR = "debug-log"
    }
}
