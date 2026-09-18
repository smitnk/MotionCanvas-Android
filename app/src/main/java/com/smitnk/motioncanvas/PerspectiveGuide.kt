package com.smitnk.motioncanvas

import androidx.compose.ui.geometry.Offset
import kotlin.math.sqrt

data class PerspectiveGuide(
    val points: List<Offset>,
    val horizonY: Float = 600f,
    val snapStrength: Float = 0.65f
) {
    fun nearestVanishingPoint(point: Offset): Int {
        var index = -1
        var best = Float.MAX_VALUE
        points.forEachIndexed { i, p ->
            val dx = p.x - point.x
            val dy = p.y - point.y
            val d = dx * dx + dy * dy
            if (d < best) { best = d; index = i }
        }
        return index
    }

    fun snap(point: Offset): Offset {
        if (points.isEmpty()) return point
        val vp = points.minByOrNull { p ->
            val dx = p.x - point.x
            val dy = p.y - point.y
            dx * dx + dy * dy
        } ?: return point
        val dx = vp.x - point.x
        val dy = vp.y - point.y
        val length = sqrt(dx * dx + dy * dy).coerceAtLeast(0.001f)
        val influence = snapStrength.coerceIn(0f, 1f)
        return Offset(point.x + dx / length * length * influence, point.y + dy / length * length * influence)
    }
}