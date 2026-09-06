// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.util

import java.nio.charset.Charset


/**
 * 国土交通省の.datデータファイルなどのCSV風テキストファイルをデコード・エンコードする際の文字コード自動判定ユーティリティ。
 */
object CsvEncodingUtils {

    /** UTF-8のBOM（Byte Order Mark）を示すバイト配列 */
    val UTF8_BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())

    /**
     * バイト配列から文字エンコードを自動判定してデコードします。
     *
     * 先頭の3バイトがUTF-8 BOMである場合はUTF-8でデコードし、そうでない場合はShift_JISとしてデコードします。
     *
     * @param csvBytes デコード対象のバイト配列
     * @return デコードされた文字列
     */
    fun decodeWithAutoEncoding(csvBytes: ByteArray): String {
        val isUtf8WithBom = csvBytes.size >= 3 &&
            csvBytes[0] == 0xEF.toByte() &&
            csvBytes[1] == 0xBB.toByte() &&
            csvBytes[2] == 0xBF.toByte()
        return if (isUtf8WithBom) {
            String(csvBytes, 3, csvBytes.size - 3, Charsets.UTF_8)
        } else {
            String(csvBytes, Charset.forName("Shift_JIS"))
        }
    }

    /**
     * 文字列をBOM付きUTF-8のバイト配列に変換します（デバッグファイルの書き出し用などに使用されます）。
     *
     * @param text 変換対象の文字列
     * @return BOM付きUTF-8バイト配列
     */
    fun encodeWithBom(text: String): ByteArray =
        UTF8_BOM + text.toByteArray(Charsets.UTF_8)
}
