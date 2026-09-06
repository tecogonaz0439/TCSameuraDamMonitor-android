// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.ui.theme

import androidx.compose.ui.graphics.Color
import net.tecogonaz.tcsameuradammonitor.domain.model.Trend

/**
 * 方向を持つトレンドに適用する、テーマ非依存の固定強調色を返します。
 *
 * Apple版と同様に上昇は赤、下降は青とし、横ばい・未知・未指定では呼び出し側の
 * 通常色を使用できるよう `null` を返します。
 */
internal fun trendAccentColor(trend: Trend?): Color? = when (trend) {
    Trend.UP -> Color.Red
    Trend.DOWN -> Color.Blue
    Trend.FLAT,
    Trend.UNKNOWN,
    null -> null
}
