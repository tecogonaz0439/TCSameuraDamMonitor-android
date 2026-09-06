// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.ui.common

import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.Velocity
import kotlin.math.abs
import kotlin.math.sign


/**
 * スクロール可能なコンポーネント（LazyColumnやスクロール可能なRowなど）に対して、
 * マウスの左ボタンドラッグによるスクロール操作と、ドラッグを勢いよく離した時の慣性スクロール（Fling）を有効化する [Modifier] です。
 *
 * デスクトップ表示（マウス操作）でのスクロール体験を向上させるために使用されます。
 *
 * @param state 対象のスクロール可能な状態オブジェクト（[ScrollableState]）。
 * @param orientation スクロールの方向（[Orientation.Horizontal] または [Orientation.Vertical]）。
 * @return マウスドラッグスクロール処理が追加された [Modifier]。
 */
fun Modifier.mouseDragScroll(
    state: ScrollableState,
    orientation: Orientation,
): Modifier = composed {
    val flingBehavior = ScrollableDefaults.flingBehavior()
    pointerInput(state, orientation, flingBehavior) {
        while (true) {
            val flingVelocity = awaitMouseDragScroll(state, orientation)
            if (flingVelocity != null && abs(flingVelocity) > 1f) {
                state.scroll(MutatePriority.UserInput) {
                    with(flingBehavior) {
                        performFling(flingVelocity)
                    }
                }
            }
        }
    }
}

private suspend fun PointerInputScope.awaitMouseDragScroll(
    state: ScrollableState,
    orientation: Orientation,
): Float? {
    var flingVelocity: Float? = null
    awaitPointerEventScope {
        val down = awaitMousePrimaryDown()
        val velocityTracker = VelocityTracker()
        velocityTracker.addPosition(down.uptimeMillis, down.position)

        var pointerId = down.id
        var lastPosition = down.position.axisValue(orientation)
        var pendingDrag = 0f
        var dragging = false
        var canceled = false

        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Main)
            val change = event.changes.firstOrNull { it.id == pointerId }
                ?: event.changes.firstOrNull { it.type == PointerType.Mouse }
                ?: continue
            pointerId = change.id

            if (change.isConsumed && !dragging) {
                canceled = true
                break
            }
            if (change.changedToUpIgnoreConsumed() || !event.buttons.isPrimaryPressed) {
                if (dragging && !canceled) {
                    flingVelocity = -velocityTracker.calculateVelocity().axisValue(orientation)
                }
                break
            }
            if (change.type != PointerType.Mouse) {
                continue
            }

            val positionChange = change.positionChangeIgnoreConsumed().axisValue(orientation)
            if (positionChange == 0f) {
                continue
            }

            velocityTracker.addPosition(change.uptimeMillis, change.position)
            val currentPosition = change.position.axisValue(orientation)
            val delta = currentPosition - lastPosition
            lastPosition = currentPosition

            if (!dragging) {
                pendingDrag += delta
                if (abs(pendingDrag) <= viewConfiguration.touchSlop) {
                    continue
                }
                dragging = true
                val overSlop = pendingDrag - sign(pendingDrag) * viewConfiguration.touchSlop
                dispatchMouseScrollDelta(state, -overSlop, change)
            } else {
                dispatchMouseScrollDelta(state, -delta, change)
            }
        }
    }
    return flingVelocity
}

private suspend fun androidx.compose.ui.input.pointer.AwaitPointerEventScope.awaitMousePrimaryDown(): PointerInputChange {
    while (true) {
        val event = awaitPointerEvent(PointerEventPass.Main)
        val change = event.changes.firstOrNull {
            it.type == PointerType.Mouse && it.pressed && event.buttons.isPrimaryPressed
        }
        if (change != null) {
            return change
        }
    }
}

private fun dispatchMouseScrollDelta(
    state: ScrollableState,
    delta: Float,
    change: PointerInputChange,
) {
    if (abs(delta) < 0.5f) {
        return
    }
    val consumed = state.dispatchRawDelta(delta)
    if (abs(consumed) > 0.5f) {
        change.consume()
    }
}

private fun Offset.axisValue(orientation: Orientation): Float =
    when (orientation) {
        Orientation.Horizontal -> x
        Orientation.Vertical -> y
    }

private fun Velocity.axisValue(orientation: Orientation): Float =
    when (orientation) {
        Orientation.Horizontal -> x
        Orientation.Vertical -> y
    }
