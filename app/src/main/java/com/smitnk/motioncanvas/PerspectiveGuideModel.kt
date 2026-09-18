package com.smitnk.motioncanvas

import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import kotlin.math.sqrt

data class PerspectiveGuideModel(
    val horizonY: Float = 600f,
    val vanishingPoints: List<Offset> = listOf(Offset(800f, -500f)),
    val snapEnabled: Boolean = false
) {
    fun withPointCount(count: Int, width: Float, height: Float): PerspectiveGuideModel {
        val y = horizonY.coerceIn(0f, height)
        val points = when (count.coerceIn(1, 3)) {
            1 -> listOf(Offset(width / 2f, y - 1100f))
            2 -> listOf(Offset(-900f, y), Offset(width + 900f, y))
            else -> listOf(
                Offset(-900f, y - 900f),
                Offset(width + 900f, y - 900f),
                Offset(width / 2f, y + 1400f)
            )
        }
        return copy(horizonY = y, vanishingPoints = points)
    }

    fun nearestPoint(point: Offset, radius: Float = 70f): Int {
        var best = -1
        var bestDistance = radius * radius
        vanishingPoints.forEachIndexed { index, vp ->
            val dx = vp.x - point.x
            val dy = vp.y - point.y
            val distance = dx * dx + dy * dy
            if (distance < bestDistance) {
                best = index
                bestDistance = distance
            }
        }
        return best
    }

    fun movePoint(index: Int, point: Offset): PerspectiveGuideModel {
        if (index !in vanishingPoints.indices) return this
        val updated = vanishingPoints.toMutableList()
        updated[index] = point
        return copy(vanishingPoints = updated)
    }

    fun moveHorizon(y: Float, height: Float): PerspectiveGuideModel =
        copy(horizonY = y.coerceIn(0f, height))

    fun snap(point: Offset): Offset {
        if (!snapEnabled || vanishingPoints.isEmpty()) return point
        val center = Offset(point.x, horizonY)
        val vp = vanishingPoints.minByOrNull { p ->
            val dx = p.x - point.x
            val dy = p.y - point.y
            dx * dx + dy * dy
        } ?: return point
        val dx = vp.x - center.x
        val dy = vp.y - center.y
        val length = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
        val ux = dx / length
        val uy = dy / length
        val px = point.x - center.x
        val py = point.y - center.y
        val projection = px * ux + py * uy
        return Offset(center.x + projection * ux, center.y + projection * uy)
    }

    fun isNearHorizon(point: Offset, tolerance: Float = 45f): Boolean =
        abs(point.y - horizonY) <= tolerance
}
