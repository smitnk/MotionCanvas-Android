package com.smitnk.motioncanvas

import androidx.compose.ui.geometry.Offset
import kotlin.math.pow
import kotlin.math.sqrt

data class StrokeSelection(
    val indices: Set<Int> = emptySet(),
    val transformBox: TransformBox? = null
) {
    val active: Boolean get() = indices.isNotEmpty()
}

object StrokeSelectionEngine {
    fun hitTest(
        strokes: List<Stroke>,
        point: Offset,
        tolerance: Float = 28f
    ): Int? {
        var bestIndex: Int? = null
        var bestDistance = tolerance

        strokes.forEachIndexed { index, stroke ->
            if (stroke.points.size == 1) {
                val d = distance(point, stroke.points.first())
                if (d <= bestDistance) {
                    bestDistance = d
                    bestIndex = index
                }
            } else {
                for (i in 0 until stroke.points.lastIndex) {
                    val d = if (stroke.inHandles.size == stroke.points.size &&
                        stroke.outHandles.size == stroke.points.size) {
                        distanceToCubic(
                            point,
                            stroke.points[i],
                            stroke.points[i] + stroke.outHandles[i],
                            stroke.points[i + 1] + stroke.inHandles[i + 1],
                            stroke.points[i + 1]
                        )
                    } else {
                        distanceToSegment(point, stroke.points[i], stroke.points[i + 1])
                    }
                    if (d <= bestDistance) {
                        bestDistance = d
                        bestIndex = index
                    }
                }
            }
        }
        return bestIndex
    }

    fun select(
        strokes: List<Stroke>,
        point: Offset,
        tolerance: Float = 28f,
        additive: Boolean = false,
        current: Set<Int> = emptySet()
    ): StrokeSelection {
        val hit = hitTest(strokes, point, tolerance)
        if (hit == null) return StrokeSelection(if (additive) current else emptySet())

        val indices = if (additive) current + hit else setOf(hit)
        val selected = indices.mapNotNull { strokes.getOrNull(it) }
        return StrokeSelection(indices, SelectionGeometry.strokeBounds(selected)?.toTransformBox())
    }

    fun toggleSelection(
        strokes: List<Stroke>,
        point: Offset,
        tolerance: Float = 28f,
        current: Set<Int> = emptySet()
    ): StrokeSelection {
        val hit = hitTest(strokes, point, tolerance) ?: return StrokeSelection(current, SelectionGeometry.strokeBounds(current.mapNotNull { strokes.getOrNull(it) })?.toTransformBox())
        val indices = if (hit in current) current - hit else current + hit
        val selected = indices.mapNotNull { strokes.getOrNull(it) }
        return StrokeSelection(indices, SelectionGeometry.strokeBounds(selected)?.toTransformBox())
    }

    fun selectWithin(
        strokes: List<Stroke>,
        min: Offset,
        max: Offset,
        additive: Boolean = false,
        current: Set<Int> = emptySet()
    ): StrokeSelection {
        val left = minOf(min.x, max.x)
        val right = maxOf(min.x, max.x)
        val top = minOf(min.y, max.y)
        val bottom = maxOf(min.y, max.y)

        fun intersects(stroke: Stroke): Boolean {
            if (stroke.points.isEmpty()) return false
            var minX = Float.POSITIVE_INFINITY
            var maxX = Float.NEGATIVE_INFINITY
            var minY = Float.POSITIVE_INFINITY
            var maxY = Float.NEGATIVE_INFINITY

            fun include(point: Offset) {
                minX = minOf(minX, point.x)
                maxX = maxOf(maxX, point.x)
                minY = minOf(minY, point.y)
                maxY = maxOf(maxY, point.y)
            }

            stroke.points.forEachIndexed { i, point ->
                include(point)
                if (stroke.inHandles.size == stroke.points.size &&
                    stroke.outHandles.size == stroke.points.size
                ) {
                    include(point + stroke.inHandles[i])
                    include(point + stroke.outHandles[i])
                }
            }

            return maxX >= left && minX <= right && maxY >= top && minY <= bottom
        }

        val found = strokes.mapIndexedNotNull { index, stroke ->
            if (intersects(stroke)) index else null
        }.toSet()

        val indices = if (additive) current + found else found
        val selected = indices.mapNotNull { strokes.getOrNull(it) }
        return StrokeSelection(indices, SelectionGeometry.strokeBounds(selected)?.toTransformBox())
    }

    fun applyTransform(
        strokes: List<Stroke>,
        selection: StrokeSelection,
        from: TransformBox,
        to: TransformBox
    ): List<Stroke> {
        if (!selection.active) return strokes
        val selected = selection.indices.mapNotNull { strokes.getOrNull(it) }
        val transformed = SelectionGeometry.transformStrokes(selected, from, to)
        val result = strokes.toMutableList()
        selection.indices.sorted().forEachIndexed { position, index ->
            if (position < transformed.size && index in result.indices) {
                result[index] = transformed[position]
            }
        }
        return result
    }

    private fun distance(a: Offset, b: Offset): Float =
        sqrt((a.x - b.x).pow(2) + (a.y - b.y).pow(2))

    private fun distanceToCubic(point: Offset, p0: Offset, p1: Offset, p2: Offset, p3: Offset): Float {
        var best = Float.MAX_VALUE
        var previous = p0
        val steps = 24
        for (i in 1..steps) {
            val t = i / steps.toFloat()
            val u = 1f - t
            val current = Offset(
                u * u * u * p0.x + 3f * u * u * t * p1.x + 3f * u * t * t * p2.x + t * t * t * p3.x,
                u * u * u * p0.y + 3f * u * u * t * p1.y + 3f * u * t * t * p2.y + t * t * t * p3.y
            )
            best = minOf(best, distanceToSegment(point, previous, current))
            previous = current
        }
        return best
    }

    private fun distanceToSegment(point: Offset, a: Offset, b: Offset): Float {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val lengthSquared = dx * dx + dy * dy
        if (lengthSquared <= 0.0001f) return distance(point, a)
        val t = (((point.x - a.x) * dx + (point.y - a.y) * dy) / lengthSquared)
            .coerceIn(0f, 1f)
        val projection = Offset(a.x + t * dx, a.y + t * dy)
        return distance(point, projection)
    }
}
