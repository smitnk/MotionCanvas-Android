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
