package com.smitnk.motioncanvas

import androidx.compose.ui.geometry.Offset
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

data class SnapResult(
    val point: Offset,
    val snapped: Boolean,
    val angleDegrees: Float
)

object GeometrySnapEngine {
    fun snapToPerspective(point: Offset, origin: Offset, vanishingPoints: List<Offset>, radius: Float = 140f): SnapResult {
        if (vanishingPoints.isEmpty()) return SnapResult(point, false, 0f)
        var bestPoint = point
        var bestDistance = Float.MAX_VALUE
        var bestAngle = 0f
        vanishingPoints.forEach { vp ->
            val dx = vp.x - origin.x
            val dy = vp.y - origin.y
            val length = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
            val ux = dx / length
            val uy = dy / length
            val px = point.x - origin.x
            val py = point.y - origin.y
            val projection = px * ux + py * uy
            val projected = Offset(origin.x + projection * ux, origin.y + projection * uy)
            val distance = kotlin.math.sqrt(
                (projected.x - point.x) * (projected.x - point.x) +
                    (projected.y - point.y) * (projected.y - point.y)
            )
            if (distance < bestDistance) {
                bestDistance = distance
                bestPoint = projected
                bestAngle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
            }
        }
        return SnapResult(bestPoint, bestDistance <= radius, bestAngle)
    }
}
