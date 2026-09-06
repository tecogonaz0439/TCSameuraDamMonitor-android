// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.ui.settings

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject


/**
 * 取得したダム観測データのRAWデータ（.datファイル）を外部ストレージにエクスポート（書き出し）するためのヘルパークラスです。
 *
 * @property context アプリケーションコンテキスト。
 */
open class DatExporter @Inject constructor(
    @ApplicationContext private val context: Context
) {
    open fun write(outputUri: Uri, bytes: ByteArray): Boolean =
        context.contentResolver.openOutputStream(outputUri)?.use {
            it.write(bytes)
            true
        } ?: false
}
