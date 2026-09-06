// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.domain.model


/**
 * アプリ内デバッグログ画面等に表示するための、ドメイン層のログエントリデータモデル。
 *
 * @property id ログエントリID
 * @property timestampMillis ログ記録時刻（ミリ秒タイムスタンプ）
 * @property message ログの概要メッセージ
 * @property details 例外スタックトレースやパラメータなどの詳細テキスト
 */
data class DebugLogEntry(
    val id: Long = 0,
    val timestampMillis: Long,
    val message: String,
    val details: String = ""
)
