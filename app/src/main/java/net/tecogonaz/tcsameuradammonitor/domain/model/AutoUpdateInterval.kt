// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.domain.model


/**
 * バックグラウンドデータ自動更新の間隔定義を表す列挙型。
 *
 * @property intervalMillis 各自動更新間隔に対応するミリ秒表現
 */
enum class AutoUpdateInterval(val intervalMillis: Long) {
    
    /** 1週間（7日間）間隔 */
    ONE_WEEK(7L * 24 * 60 * 60 * 1000),

    /** 1日（24時間）間隔 */
    ONE_DAY(24L * 60 * 60 * 1000),

    /** 12時間間隔 */
    TWELVE_HOURS(12L * 60 * 60 * 1000),

    /** 1時間間隔 */
    ONE_HOUR(1L * 60 * 60 * 1000)
}
