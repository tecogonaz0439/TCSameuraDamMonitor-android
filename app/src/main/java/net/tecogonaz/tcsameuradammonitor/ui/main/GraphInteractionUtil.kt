// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.ui.main

import kotlin.math.abs

internal data class GraphPointSelection<T>(
    val index: Int,
    val x: Float,
    val y: Float?,
    val point: T
)

internal fun clampGraphTouchX(
    touchX: Float,
    leftMargin: Float,
    graphWidth: Float
): Float =
    touchX.coerceIn(leftMargin, leftMargin + graphWidth)

internal fun <T> findClosestGraphPoint(
    touchX: Float,
    leftMargin: Float,
    graphWidth: Float,
    coords: List<Triple<Float, Float?, T>>
): GraphPointSelection<T>? {
    val clampedX = clampGraphTouchX(touchX, leftMargin, graphWidth)
    var closestIndex = -1
    var closestPoint: Triple<Float, Float?, T>? = null
    var closestDistance = Float.POSITIVE_INFINITY

    coords.forEachIndexed { index, coord ->
        val distance = abs(coord.first - clampedX)
        if (distance < closestDistance) {
            closestIndex = index
            closestPoint = coord
            closestDistance = distance
        }
    }

    val point = closestPoint ?: return null
    return GraphPointSelection(
        index = closestIndex,
        x = point.first,
        y = point.second,
        point = point.third
    )
}

internal fun findClosestGraphIndex(
    touchX: Float,
    leftMargin: Float,
    graphWidth: Float,
    pointCount: Int,
    xOf: (Int) -> Float
): Int? {
    if (pointCount <= 0) return null

    val clampedX = clampGraphTouchX(touchX, leftMargin, graphWidth)
    var closestIndex = 0
    var closestDistance = abs(xOf(0) - clampedX)

    for (index in 1 until pointCount) {
        val distance = abs(xOf(index) - clampedX)
        if (distance < closestDistance) {
            closestIndex = index
            closestDistance = distance
        }
    }

    return closestIndex
}

internal fun calculateTooltipStartX(
    anchorX: Float,
    tooltipWidth: Float,
    canvasWidth: Float,
    horizontalOffset: Float
): Float =
    if (anchorX + tooltipWidth + horizontalOffset > canvasWidth) {
        anchorX - tooltipWidth - horizontalOffset
    } else {
        anchorX + horizontalOffset
    }
