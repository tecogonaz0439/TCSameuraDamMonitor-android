// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.domain.model


/**
 * ダムに関連する外部関連ウェブサイトへのリンク（URLと表示名称）を保持するデータモデル。
 *
 * @property label 外部リンクの表示ラベル・名称（例: "早明浦ダム管理所"）
 * @property url 開く対象の外部URL
 */
data class ExternalUrl(
    val label: String,
    val url: String
)
