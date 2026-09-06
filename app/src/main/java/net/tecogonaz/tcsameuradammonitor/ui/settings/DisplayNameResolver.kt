// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.ui.settings

import android.content.Context
import android.provider.OpenableColumns
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject


/**
 * 指定された [Uri] 文字列から、ストレージ上の表示用ファイル名を解決するためのヘルパークラスです。
 *
 * 主に Storage Access Framework (SAF) で選択されたデバッグ用の `.dat` ファイル名を表示する際に使用されます。
 *
 * @property context アプリケーションコンテキスト。
 */
open class DisplayNameResolver @Inject constructor(
    @ApplicationContext private val context: Context
) {
    open fun resolve(uriString: String?, defaultName: String): String {
        if (uriString.isNullOrEmpty()) return defaultName
        return runCatching {
            val uri = uriString.toUri()
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) cursor.getString(nameIndex) else null
                    } else {
                        null
                    }
                }
                ?: uriString
        }.getOrElse { uriString }
    }
}
