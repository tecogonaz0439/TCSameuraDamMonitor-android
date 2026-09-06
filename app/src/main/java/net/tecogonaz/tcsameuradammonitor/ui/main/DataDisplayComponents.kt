// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.ui.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.FilterListOff
import androidx.compose.material.icons.outlined.NorthEast
import androidx.compose.material.icons.outlined.SouthEast
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.SubcomposeLayout
import net.tecogonaz.tcsameuradammonitor.R
import net.tecogonaz.tcsameuradammonitor.domain.model.Trend
import net.tecogonaz.tcsameuradammonitor.ui.theme.trendAccentColor
import net.tecogonaz.tcsameuradammonitor.ui.common.MISSING_TEXT
import java.util.Locale

private enum class AdaptiveInfoRowSlot {
    LABEL,
    VALUE
}


/**
 * デバイスの画面幅に基づいて、適切なナビゲーションレイアウト（[NavigationLayout.PERMANENT] または [NavigationLayout.DRAWER]）を
 * 判定し記憶（remember）するComposable関数です。
 *
 * 画面幅が840dp以上の場合は永続的なサイドナビゲーション（PERMANENT）を、
 * それ未満の場合はドロワー型のナビゲーション（DRAWER）を選択します。
 */
@Composable
internal fun rememberNavigationLayout(): NavigationLayout {
    val windowInfo = LocalWindowInfo.current
    val density = LocalDensity.current
    val widthDp = with(density) { windowInfo.containerSize.width.toDp() }
    return if (widthDp >= 840.dp) NavigationLayout.PERMANENT
    else NavigationLayout.DRAWER
}

/**
 * ラベルと値を左右に並べて表示する、シンプルな情報表示行コンポーネントです。
 *
 * 画面幅が狭くラベルと値が重なる場合は、自動的に2行に折りたたまれます。
 *
 * @param label 表示する項目の名前（ラベル）。
 * @param value 表示する項目の値。
 */
@Composable
fun InfoRow(label: String, value: String) {
    AdaptiveInfoRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        value = {
            Text(text = value, style = MaterialTheme.typography.bodyLarge)
        }
    )
}

@Composable
private fun AdaptiveInfoRow(
    modifier: Modifier = Modifier,
    label: @Composable () -> Unit,
    value: @Composable () -> Unit
) {
    SubcomposeLayout(modifier = modifier) { constraints ->
        val childConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        val labelPlaceables = subcompose(AdaptiveInfoRowSlot.LABEL, label).map {
            it.measure(childConstraints)
        }
        val valuePlaceables = subcompose(AdaptiveInfoRowSlot.VALUE, value).map {
            it.measure(childConstraints)
        }
        val labelWidth = labelPlaceables.maxOfOrNull { it.width } ?: 0
        val labelHeight = labelPlaceables.maxOfOrNull { it.height } ?: 0
        val valueWidth = valuePlaceables.maxOfOrNull { it.width } ?: 0
        val valueHeight = valuePlaceables.maxOfOrNull { it.height } ?: 0
        val maxWidth = constraints.maxWidth
        val isSingleLineLayout = labelWidth + valueWidth <= maxWidth
        val layoutHeight = if (isSingleLineLayout) {
            maxOf(labelHeight, valueHeight)
        } else {
            labelHeight + valueHeight
        }.coerceIn(constraints.minHeight, constraints.maxHeight)

        layout(width = maxWidth, height = layoutHeight) {
            if (isSingleLineLayout) {
                val labelY = (layoutHeight - labelHeight) / 2
                val valueY = (layoutHeight - valueHeight) / 2
                labelPlaceables.forEach { it.placeRelative(0, labelY) }
                valuePlaceables.forEach {
                    it.placeRelative((maxWidth - valueWidth).coerceAtLeast(0), valueY)
                }
            } else {
                labelPlaceables.forEach { it.placeRelative(0, 0) }
                valuePlaceables.forEach {
                    it.placeRelative((maxWidth - valueWidth).coerceAtLeast(0), labelHeight)
                }
            }
        }
    }
}

/**
 * 過去観測データ表示における期間選択（日付選択）およびフィルターリセットを行うための
 * アクションアイコンボタン群を配置するComposable関数です。
 *
 * @param isFiltered 現在期間フィルターが適用されているかどうかのフラグ。
 * @param onResetRange 期間選択フィルターをクリアして初期状態に戻すコールバック。
 * @param onOpenRangeDialog 期間選択用のダイアログを開くコールバック。
 */
@Composable
internal fun HistoricalRangeActionIcons(
    isFiltered: Boolean,
    onResetRange: () -> Unit,
    onOpenRangeDialog: () -> Unit
) {
    if (isFiltered) {
        IconButton(onClick = onResetRange) {
            Icon(
                Icons.Outlined.FilterListOff,
                contentDescription = stringResource(R.string.desc_historical_range_reset)
            )
        }
    }
    IconButton(onClick = onOpenRangeDialog) {
        Icon(
            Icons.Outlined.DateRange,
            contentDescription = stringResource(R.string.desc_historical_range)
        )
    }
}


/**
 * ダムの所在地などの地理座標（Geo URI）情報を外部地図アプリ等と連携するためのリンク形式の情報表示行です。
 *
 * @param label 表示するラベル。
 * @param geoUri 地図アプリに送る `geo:` 形式のURI。
 * @param availableApps 連携可能なアプリ（[AppLinkInfo]）のリスト。
 * @param onOpenUrl URLを標準ブラウザ等で開くコールバック。
 * @param onShareText テキストを共有するコールバック。
 * @param onCopied クリップボードへのコピー完了時に呼ばれるコールバック。
 */
@Composable
fun GeoUriInfoRow(
    label: String,
    geoUri: String,
    availableApps: List<AppLinkInfo>,
    onOpenUrl: (String) -> Unit,
    onShareText: (String) -> Unit,
    onCopied: (String) -> Unit
) {
    LinkDropdownMenu(
        title = geoUri,
        url = geoUri,
        availableApps = availableApps,
        onOpenUrl = onOpenUrl,
        onOpenUrlWith = { _, _ -> },
        onShareText = onShareText,
        onCopied = onCopied,
        onQueryApps = {},
        onClearApps = {},
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        isGeo = true,
        horizontalPadding = 0.dp,
        textToCopy = geoUri,
        textStyle = MaterialTheme.typography.bodyLarge,
        textColor = MaterialTheme.colorScheme.primary,
        textAlign = TextAlign.End,
        headerContent = {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    )
}

/**
 * 貯水量、流入量、放流量などの観測データ項目を、前日比・前週比の推移（トレンド）を表す矢印アイコン付きで表示するコンポーネントです。
 *
 * @param label 観測項目のラベル（例: 流入量、放流量）。
 * @param value 書式化された観測値。
 * @param trend 前日比・前週比などの上昇・下降トレンド（[Trend]）。
 * @param isMissing データが欠損しているかどうかのフラグ。trueの場合、トレンド表示などを抑止します。
 */
@Composable
fun DataRow(label: String, value: String, trend: Trend?, isMissing: Boolean = false) {
    val trendColor = trendAccentColor(trend) ?: MaterialTheme.colorScheme.onSurfaceVariant

    AdaptiveInfoRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .semantics(mergeDescendants = true) {},
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        value = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isMissing) MaterialTheme.colorScheme.onSurfaceVariant else trendColor
                )

                Box(modifier = Modifier.width(28.dp), contentAlignment = Alignment.CenterEnd) {
                    if (!isMissing && trend != null && trend != Trend.UNKNOWN) {
                        val icon = when (trend) {
                            Trend.UP -> Icons.Outlined.NorthEast
                            Trend.DOWN -> Icons.Outlined.SouthEast
                            else -> Icons.AutoMirrored.Outlined.ArrowForward
                        }
                    val trendIconDescription = when (trend) {
                        Trend.UP -> stringResource(R.string.desc_trend_up)
                        Trend.DOWN -> stringResource(R.string.desc_trend_down)
                        else -> stringResource(R.string.desc_trend_stable)
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = trendIconDescription,
                        tint = trendColor,
                        modifier = Modifier.size(16.dp)
                    )
                    }
                }
            }
        }
    )
}

/**
 * 数値を指定された小数点以下の桁数にフォーマットするユーティリティ関数です。
 *
 * 値が null の場合は欠損値表示 [MISSING_TEXT] を返します。
 *
 * @param value フォーマット対象の浮動小数点数値。
 * @param decimals 小数点以下の表示桁数。0の場合は整数に丸められます。
 * @param locale ロケール。
 * @return フォーマットされた文字列。
 */
fun formatValue(value: Float?, decimals: Int = 0, locale: Locale = Locale.US): String {
    if (value == null) return MISSING_TEXT
    return if (decimals == 0) {
        if (value % 1 == 0f) value.toInt().toString() else value.toInt().toString()
    } else {
        String.format(locale, "%.${decimals}f", value)
    }
}

/**
 * 流域平均雨量をフォーマットするユーティリティ関数です（小数点第一位まで）。
 *
 * 値が null の場合は欠損値表示 [MISSING_TEXT] を返します。
 *
 * @param value 流域平均雨量の数値。
 * @param locale ロケール。
 * @return 小数点第一位までフォーマットされた雨量文字列。
 */
fun formatRainfall(value: Float?, locale: Locale = Locale.US): String {
    if (value == null) return MISSING_TEXT
    return String.format(locale, "%.1f", value)
}

/**
 * 前日比や前週比の値を符号付き（+ / -）でフォーマットするユーティリティ関数です（小数点第二位まで）。
 *
 * 値が null の場合は欠損値表示 [MISSING_TEXT] を返します。
 *
 * @param value 前日比・前週比などの数値。
 * @param locale ロケール。
 * @return 符号付きで小数点第二位までフォーマットされた文字列。
 */
fun formatChangeValue(value: Float?, locale: Locale = Locale.US): String {
    if (value == null) return MISSING_TEXT
    return String.format(locale, "%+.2f", value)
}
