package com.smitnk.motioncanvas

import androidx.compose.ui.geometry.Offset
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt

enum class SnapKind { NONE, ANGLE, GRID, PERSPECTIVE }

data class PrecisionSnapResult(
    val point: Offset,
    val kind: SnapKind,
    val angleDegrees: Float
)

object PrecisionRulerEngine {
    private val commonAngles = floatArrayOf(0f, 30f, 45f, 60f, 90f, 120f, 135f, 150f, 180f)

    fun snapAngle(
        start: Offset,
        point: Offset,
        toleranceDegrees: Float = 7f
    ): PrecisionSnapResult {
        val dx = point.x - start.x
        val dy = point.y - start.y
        val distance = sqrt(dx * dx + dy * dy)
        if (distance < 1f) return PrecisionSnapResult(point, SnapKind.NONE, 0f)

        var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
        if (angle < 0f) angle += 360f

        var best = angle
        var bestDelta = Float.MAX_VALUE
        commonAngles.forEach { candidate ->
            val candidates = floatArrayOf(candidate, candidate + 180f, candidate + 360f)
            candidates.forEach { a ->
                val delta = kotlin.math.abs(angle - a)
                if (delta < bestDelta) {
                    bestDelta = delta
                    best = a
                }
            }
        }

        val nearestDegree = round(angle)
        if (bestDelta > toleranceDegrees) {
            best = nearestDegree
        }

        val radians = Math.toRadians(best.toDouble())
        val snapped = Offset(
            start.x + cos(radians).toFloat() * distance,
            start.y + sin(radians).toFloat() * distance
        )
        return PrecisionSnapResult(snapped, if (bestDelta <= toleranceDegrees) SnapKind.ANGLE else SnapKind.NONE, best)
    }

    fun snapGrid(point: Offset, spacing: Float): PrecisionSnapResult {
        if (spacing <= 0f) return PrecisionSnapResult(point, SnapKind.NONE, 0f)
        val snapped = Offset(
            round(point.x / spacing) * spacing,
            round(point.y / spacing) * spacing
        )
        return PrecisionSnapResult(snapped, SnapKind.GRID, 0f)
    }
}
