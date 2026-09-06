// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.data.source.local

import android.content.Context
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject


/**
 * デバッグ用の観測データ（.datファイル）を読み込むためのファイルリーダー。
 *
 * アプリ内に組み込まれたアセット（BUNDLED）およびストレージアクセスフレームワーク（SAF）経由で
 * 選択された外部ファイルをバイト配列として読み込む機能を提供します。
 */
open class DebugDatSourceReader @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /**
     * アセットフォルダ（assets/debug/）から指定されたファイル名のバイトデータを読み込みます。
     *
     * @param fileName アセット内のデバッグファイル名（例: "bundled_debug.dat"）
     * @return 読み込んだバイト配列
     */
    open fun readBundled(fileName: String): ByteArray =
        context.assets.open("debug/$fileName").use { it.readBytes() }

    /**
     * ストレージアクセスフレームワーク (SAF) 経由で取得したURIからバイトデータを読み込みます。
     *
     * ファイルサイズの制限チェックを行い、制限を超えている場合は例外をスローします。
     *
     * @param uriString 読み込み対象のSAFコンテンツURIの文字列
     * @param maxSizeBytes 読み込みを許可する最大バイトサイズ
     * @param tooLargeMessage ファイルサイズが最大値を超えた際のエラーメッセージ
     * @param readErrorMessage ストリームを開けなかった際のエラーメッセージ
     * @return 読み込んだ生バイト配列
     */
    open fun readSaf(uriString: String, maxSizeBytes: Int, tooLargeMessage: String, readErrorMessage: String): ByteArray {
        val uri = uriString.toUri()
        return context.contentResolver.openInputStream(uri)?.use { stream ->
            val bytes = stream.readBytes()
            if (bytes.size > maxSizeBytes) {
                throw Exception(tooLargeMessage)
            }
            bytes
        } ?: throw Exception(readErrorMessage)
    }
}
