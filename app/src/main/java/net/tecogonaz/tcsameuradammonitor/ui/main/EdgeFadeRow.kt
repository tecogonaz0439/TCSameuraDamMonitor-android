// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.ui.main

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * チップを1行横スクロールで表示し、左右端にスクロール余地がある側へエッジフェードを
 * 描画する共通コンポーネント。
 *
 * スクロール位置は呼び出し側が保持する [LazyListState] に委ねるため、mode切替・Card開閉を
 * 越えた位置維持は呼び出し側の remember の寿命に従う。
 *
 * フリング停止時にchip先頭を行の先端(左端)へ整列させる(Web版の scroll-snap-align: start 相当)。
 * ドラッグ停止時は整列しない。これは foundation 1.11.4 の LazyRow がフリング時のスナップ
 * API しか提供せず、ドラッグ停止時の整列APIが無いことによるAndroid標準挙動である。
 *
 * フェード幅は32.dpで、外側へ向かうほどフェード色へ、内側へ向かうほど透明になる。
 * 描画は [androidx.compose.ui.graphics.Brush] ではなく列単位の [SolidColor] による
 * 明示的なα線形補間で行う(環境によってBrushの線形グラデーションが幅いっぱいに広がり、
 * フェードが「突如途切れる」見え方になる問題があるため)。αはスクロール余地の有無に
 * 応じて150msで遷移する。drawWithContentで描画するためタッチ操作は遮らない。
 *
 * @param lazyListState スクロール位置を保持するLazyListState。
 * @param modifier LazyRowへ適用するModifier。
 * @param fadeColor フェードの終端色。呼び出し側のCard背景色を渡す。
 * @param content LazyRowのitem定義。
 */
@Composable
internal fun EdgeFadeRow(
    lazyListState: LazyListState,
    modifier: Modifier = Modifier,
    fadeColor: Color,
    content: LazyListScope.() -> Unit
) {
    val startFadeVisible by remember(lazyListState) {
        derivedStateOf { lazyListState.canScrollBackward }
    }
    val endFadeVisible by remember(lazyListState) {
        derivedStateOf { lazyListState.canScrollForward }
    }
    val scrollPosition by remember(lazyListState) {
        derivedStateOf {
            lazyListState.firstVisibleItemIndex to lazyListState.firstVisibleItemScrollOffset
        }
    }
    val startFadeAlpha by animateFloatAsState(
        targetValue = if (startFadeVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 150),
        label = "RowStartFadeAlpha"
    )
    val endFadeAlpha by animateFloatAsState(
        targetValue = if (endFadeVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 150),
        label = "RowEndFadeAlpha"
    )
    Box(
        modifier = Modifier.semantics {
            rowFadeHints = RowFadeHintState(
                startVisible = startFadeVisible,
                endVisible = endFadeVisible
            )
            rowScrollPosition = scrollPosition
        }
    ) {
        LazyRow(
            state = lazyListState,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            flingBehavior = rememberSnapFlingBehavior(
                lazyListState = lazyListState,
                snapPosition = SnapPosition.Start
            ),
            modifier = modifier.drawWithContent {
                drawContent()
                // Brushの線形グラデーションは環境によってノード全体へ広がるため、
                // 列単位のSolidColorでαを明示的に線形補間する(32列で滑らかになる)。
                val fadeWidthPx = FADE_WIDTH_DP.toPx()
                if (startFadeAlpha > 0f) {
                    drawAlphaFade(atStart = true, alpha = startFadeAlpha, widthPx = fadeWidthPx, color = fadeColor)
                }
                if (endFadeAlpha > 0f) {
                    drawAlphaFade(atStart = false, alpha = endFadeAlpha, widthPx = fadeWidthPx, color = fadeColor)
                }
            }
        ) {
            content()
        }
    }
}

/** エッジフェードの幅。Web版(mobile 640px以下)のフェード幅32pxに合わせる。 */
private val FADE_WIDTH_DP = 32.dp

/** フェードを列単位の [SolidColor] で描画する(αは幅にわたって線形に 1→0)。 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawAlphaFade(
    atStart: Boolean,
    alpha: Float,
    widthPx: Float,
    color: Color
) {
    val steps = 32
    val step = widthPx / steps
    for (i in 0 until steps) {
        // 外側(行の端)ほど不透明、内側へ向かって線形に透明になる。
        val t = (steps - i).toFloat() / steps
        drawRect(
            brush = SolidColor(color.copy(alpha = alpha * t)),
            topLeft = if (atStart) {
                Offset(i * step, 0f)
            } else {
                Offset(size.width - (i + 1) * step, 0f)
            },
            size = Size(step + 0.5f, size.height)
        )
    }
}
