// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.domain.model


/**
 * データベースのVACUUMによる最適化（不要領域解放）結果を保持するデータクラス。
 *
 * @property beforeSizeBytes 最適化前のデータベースファイル総バイト数
 * @property afterSizeBytes 最最適化後のデータベースファイル総バイト数
 */
data class DatabaseVacuumResult(
    val beforeSizeBytes: Long,
    val afterSizeBytes: Long
)
