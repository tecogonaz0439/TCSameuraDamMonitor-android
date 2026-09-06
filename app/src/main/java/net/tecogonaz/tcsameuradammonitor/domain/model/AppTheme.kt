// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.domain.model

/**
 * アプリの表示テーマを表す列挙型。
 */
enum class AppTheme {
    /** 端末のシステム設定（ダーク・ライト）に追従する */
    SYSTEM,
    /** ライトテーマ固定 */
    LIGHT,
    /** ダークテーマ固定 */
    DARK
}
