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
            stroke.points.forEach { sample ->
                val distance = distance(point, sample)
                if (distance <= bestDistance) {
                    bestDistance = distance
                    bestIndex = index
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

        val found = strokes.mapIndexedNotNull { index, stroke ->
            if (stroke.points.isNotEmpty() && stroke.points.all {
                    it.x in left..right && it.y in top..bottom
                }) index else null
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
}
