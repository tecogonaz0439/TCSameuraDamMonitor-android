// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme()
private val LightColorScheme = lightColorScheme()

/**
 * アプリケーションの全体的なデザインシステム（Material 3）を適用するカスタムテーマ用Composable関数です。
 *
 * ダークテーマとライトテーマの自動切り替え、および Android 12 以降で利用可能なダイナミックカラー（Material You）に対応します。
 *
 * @param darkTheme ダークカラーテーマを使用するかどうかのフラグ。デフォルトはシステムのテーマ設定と連動します。
 * @param dynamicColor ダイナミックカラーテーマを使用するかどうかのフラグ。
 * @param content テーマを適用する配下の Composable コンテンツ。
 */
@Composable
fun TCSameuraDamMonitorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
