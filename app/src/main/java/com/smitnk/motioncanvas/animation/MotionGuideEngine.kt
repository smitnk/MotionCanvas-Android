package com.smitnk.motioncanvas.animation

import androidx.compose.ui.geometry.Offset
import kotlin.math.sqrt

data class MotionGuide(val points: List<Offset> = emptyList(), val visible: Boolean = true)

object MotionGuideEngine {
    fun nearestPoint(guide: MotionGuide, point: Offset): Offset? {
        if (guide.points.isEmpty()) return null
        var best = guide.points.first()
        var bestD = Float.MAX_VALUE
        for (i in 1 until guide.points.size) {
            val a = guide.points[i - 1]
            val b = guide.points[i]
            val dx = b.x - a.x
            val dy = b.y - a.y
            val len2 = dx * dx + dy * dy
            val t = if (len2 <= 0f) 0f else ((point.x - a.x) * dx + (point.y - a.y) * dy) / len2
            val u = t.coerceIn(0f, 1f)
            val p = Offset(a.x + dx * u, a.y + dy * u)
            val d = (p.x - point.x) * (p.x - point.x) + (p.y - point.y) * (p.y - point.y)
            if (d < bestD) { bestD = d; best = p }
        }
        return best
    }

    fun sample(guide: MotionGuide, progress: Float): Offset? {
        if (guide.points.isEmpty()) return null
        if (guide.points.size == 1) return guide.points.first()
        val segments = guide.points.zipWithNext()
        val lengths = segments.map { (a,b) -> sqrt((b.x-a.x)*(b.x-a.x)+(b.y-a.y)*(b.y-a.y)) }
        val total = lengths.sum().coerceAtLeast(0.001f)
        var target = progress.coerceIn(0f,1f) * total
        for (i in segments.indices) {
            if (target <= lengths[i]) {
                val f = target / lengths[i].coerceAtLeast(0.001f)
                val a = segments[i].first; val b = segments[i].second
                return Offset(a.x + (b.x-a.x)*f, a.y + (b.y-a.y)*f)
            }
            target -= lengths[i]
        }
        return guide.points.last()
    }
}
