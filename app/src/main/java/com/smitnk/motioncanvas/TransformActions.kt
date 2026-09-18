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
        if (!selection.active) return strokes
        val center = selection.transformBox?.center ?: return strokes
        val selected = selection.indices.toSet()
        return strokes.mapIndexed { index, stroke ->
            if (index !in selected) stroke
            else stroke.copy(points = stroke.points.map { mapper(it, center) })
        }
    }
}
