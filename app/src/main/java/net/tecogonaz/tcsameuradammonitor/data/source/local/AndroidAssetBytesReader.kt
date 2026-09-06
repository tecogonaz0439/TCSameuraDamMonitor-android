// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.data.source.local

import android.content.Context

/**
 * Androidのassetsからファイルをバイト列として読み出す[AssetBytesReader]実装。
 *
 * @property context アプリコンテキスト
 */
class AndroidAssetBytesReader(private val context: Context) : AssetBytesReader {
    override fun readBytes(filePath: String): ByteArray = context.assets.open(filePath).use { it.readBytes() }
}
