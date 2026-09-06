// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.data.source.local.room

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey


/**
 * データベースのデバッグログテーブル（"debug_log"）に対応するエンティティクラス。
 *
 * @property id 自動生成される主キー
 * @property timestampMillis ログ記録日時のミリ秒タイムスタンプ
 * @property message ログの概要メッセージ
 * @property details エラー詳細やパラメータ情報などの補足情報（デフォルト: 空文字）
 */
@Entity(tableName = "debug_log")
data class DebugLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "timestamp_millis")
    val timestampMillis: Long,
    val message: String,
    @ColumnInfo(name = "details", defaultValue = "")
    val details: String = ""
)
