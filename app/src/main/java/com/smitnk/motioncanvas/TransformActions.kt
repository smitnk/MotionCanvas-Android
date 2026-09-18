package com.smitnk.motioncanvas

import androidx.compose.ui.geometry.Offset

object TransformActions {
    fun flipHorizontal(
        strokes: List<Stroke>,
        selection: StrokeSelection
    ): List<Stroke> = transformAroundCenter(strokes, selection) { point, center ->
        Offset(center.x - (point.x - center.x), point.y)
    }

    fun flipVertical(
        strokes: List<Stroke>,
        selection: StrokeSelection
    ): List<Stroke> = transformAroundCenter(strokes, selection) { point, center ->
        Offset(point.x, center.y - (point.y - center.y))
    }

    fun rotateAroundPivot(
        strokes: List<Stroke>,
        selection: StrokeSelection,
        degrees: Float
    ): List<Stroke> {
        if (!selection.active) return strokes
        val box = selection.transformBox ?: return strokes
        val pivot = box.pivot
        val radians = Math.toRadians(degrees.toDouble())
        val cos = kotlin.math.cos(radians).toFloat()
        val sin = kotlin.math.sin(radians).toFloat()

        fun rotatePoint(point: Offset): Offset {
            val dx = point.x - pivot.x
            val dy = point.y - pivot.y
            return Offset(
                pivot.x + dx * cos - dy * sin,
                pivot.y + dx * sin + dy * cos
            )
        }

        fun rotateHandle(handle: Offset): Offset {
            return Offset(
                handle.x * cos - handle.y * sin,
                handle.x * sin + handle.y * cos
            )
        }

        val selected = selection.indices.toSet()
        return strokes.mapIndexed { index, stroke ->
            if (index !in selected) stroke
            else stroke.copy(
                points = stroke.points.map(::rotatePoint),
                inHandles = stroke.inHandles.map(::rotateHandle),
                outHandles = stroke.outHandles.map(::rotateHandle)
            )
        }
    }

    fun movePivot(box: TransformBox, pivot: Offset): TransformBox =
        box.copy(pivot = pivot)

    private fun transformAroundCenter(
        strokes: List<Stroke>,
        selection: StrokeSelection,
        mapper: (Offset, Offset) -> Offset
    ): List<Stroke> {
        fun mapHandle(handle: Offset): Offset = when {
            mapper(Offset(handle.x, 0f), Offset.Zero).x == -handle.x &&
                mapper(Offset(0f, handle.y), Offset.Zero).y == handle.y -> Offset(-handle.x, handle.y)
            else -> Offset(handle.x, -handle.y)
        }
        if (!selection.active) return strokes
        val center = selection.transformBox?.center ?: return strokes
        val selected = selection.indices.toSet()
        return strokes.mapIndexed { index, stroke ->
            if (index !in selected) stroke
            else stroke.copy(
                points = stroke.points.map { mapper(it, center) },
                inHandles = stroke.inHandles.map { handle -> mapHandle(handle) },
                outHandles = stroke.outHandles.map { handle -> mapHandle(handle) }
            )
        }
    }
}
