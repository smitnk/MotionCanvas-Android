package com.smitnk.motioncanvas

import androidx.compose.ui.geometry.Offset
import kotlin.math.atan2
import kotlin.math.sqrt

enum class TransformHandle {
    NONE, TOP_LEFT, TOP, TOP_RIGHT, RIGHT, BOTTOM_RIGHT, BOTTOM, BOTTOM_LEFT, LEFT, ROTATE, PIVOT
}

data class TransformHandleHit(
    val handle: TransformHandle,
    val distance: Float
)

object TransformHandleGeometry {
    fun handles(box: TransformBox, rotateOffset: Float = 70f): Map<TransformHandle, Offset> {
        val c = box.corners()
        val topMid = midpoint(c[0], c[1])
        val rightMid = midpoint(c[1], c[2])
        val bottomMid = midpoint(c[2], c[3])
        val leftMid = midpoint(c[3], c[0])
        val pivot = box.pivot.takeIf { it != Offset.Zero } ?: box.center
        val dy = pivot.y - box.center.y
        val dx = pivot.x - box.center.x
        val len = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
        val rotateCenter = Offset(
            topMid.x + (topMid.x - box.center.x) * rotateOffset / len,
            topMid.y + (topMid.y - box.center.y) * rotateOffset / len
        )
        return mapOf(
            TransformHandle.TOP_LEFT to c[0],
            TransformHandle.TOP to topMid,
            TransformHandle.TOP_RIGHT to c[1],
            TransformHandle.RIGHT to rightMid,
            TransformHandle.BOTTOM_RIGHT to c[2],
            TransformHandle.BOTTOM to bottomMid,
            TransformHandle.BOTTOM_LEFT to c[3],
            TransformHandle.LEFT to leftMid,
            TransformHandle.ROTATE to rotateCenter,
            TransformHandle.PIVOT to pivot
        )
    }

    fun hitTest(box: TransformBox, point: Offset, radius: Float = 32f): TransformHandleHit {
        var best = TransformHandle.NONE
        var bestDistance = Float.MAX_VALUE
        handles(box).forEach { (handle, position) ->
            val dx = point.x - position.x
            val dy = point.y - position.y
            val distance = sqrt(dx * dx + dy * dy)
            if (distance <= radius && distance < bestDistance) {
                best = handle
                bestDistance = distance
            }
        }
        return TransformHandleHit(best, bestDistance)
    }

    fun angle(center: Offset, point: Offset): Float =
        Math.toDegrees(atan2((point.y - center.y).toDouble(), (point.x - center.x).toDouble())).toFloat()

    private fun midpoint(a: Offset, b: Offset) =
        Offset((a.x + b.x) / 2f, (a.y + b.y) / 2f)
}
