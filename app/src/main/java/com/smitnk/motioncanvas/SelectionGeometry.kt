package com.smitnk.motioncanvas

import androidx.compose.ui.geometry.Offset
import kotlin.math.max
import kotlin.math.min

data class SelectionBounds(
    val min: Offset,
    val max: Offset
) {
    val center: Offset get() = Offset((min.x + max.x) / 2f, (min.y + max.y) / 2f)
    val width: Float get() = max.x - min.x
    val height: Float get() = max.y - min.y

    fun toTransformBox(): TransformBox =
        TransformBox(center = center, width = max(1f, width), height = max(1f, height))
}

object SelectionGeometry {
    fun strokeBounds(strokes: List<Stroke>): SelectionBounds? {
        val points = strokes.flatMap { it.points }
        if (points.isEmpty()) return null

        var minX = points.first().x
        var minY = points.first().y
        var maxX = minX
        var maxY = minY

        for (point in points.drop(1)) {
            minX = min(minX, point.x)
            minY = min(minY, point.y)
            maxX = max(maxX, point.x)
            maxY = max(maxY, point.y)
        }

        return SelectionBounds(Offset(minX, minY), Offset(maxX, maxY))
    }

    fun transformStrokes(
        strokes: List<Stroke>,
        from: TransformBox,
        to: TransformBox
    ): List<Stroke> {
        val sx = if (from.width == 0f) 1f else to.width / from.width
        val sy = if (from.height == 0f) 1f else to.height / from.height
        val rotationDelta = to.rotationDegrees - from.rotationDegrees

        return strokes.map { stroke ->
            fun transformPoint(point: Offset): Offset {
                val local = point - from.center
                val scaled = Offset(local.x * sx, local.y * sy)
                return rotate(scaled, rotationDelta) + to.center
            }
            fun transformHandle(handle: Offset): Offset {
                val scaled = Offset(handle.x * sx, handle.y * sy)
                return rotate(scaled, rotationDelta)
            }

            val transformed = stroke.points.map(::transformPoint)
            val transformedInHandles = if (stroke.inHandles.size == stroke.points.size) {
                stroke.inHandles.map(::transformHandle)
            } else stroke.inHandles
            val transformedOutHandles = if (stroke.outHandles.size == stroke.points.size) {
                stroke.outHandles.map(::transformHandle)
            } else stroke.outHandles

            stroke.copy(
                points = transformed,
                inHandles = transformedInHandles,
                outHandles = transformedOutHandles
            )
        }
    }

    private fun rotate(point: Offset, degrees: Float): Offset {
        val radians = Math.toRadians(degrees.toDouble())
        val c = kotlin.math.cos(radians).toFloat()
        val s = kotlin.math.sin(radians).toFloat()
        return Offset(point.x * c - point.y * s, point.x * s + point.y * c)
    }
}
