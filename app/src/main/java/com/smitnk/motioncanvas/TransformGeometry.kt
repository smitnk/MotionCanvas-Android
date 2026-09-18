package com.smitnk.motioncanvas

import androidx.compose.ui.geometry.Offset
import kotlin.math.cos
import kotlin.math.sin

data class TransformBox(
    val center: Offset,
    val width: Float,
    val height: Float,
    val rotationDegrees: Float = 0f,
    val pivot: Offset = Offset.Zero
) {
    val left: Float get() = center.x - width / 2f
    val right: Float get() = center.x + width / 2f
    val top: Float get() = center.y - height / 2f
    val bottom: Float get() = center.y + height / 2f

    fun corners(): List<Offset> {
        val local = listOf(
            Offset(-width / 2f, -height / 2f),
            Offset(width / 2f, -height / 2f),
            Offset(width / 2f, height / 2f),
            Offset(-width / 2f, height / 2f)
        )
        val r = Math.toRadians(rotationDegrees.toDouble())
        val c = cos(r).toFloat()
        val s = sin(r).toFloat()
        return local.map { p ->
            Offset(
                center.x + p.x * c - p.y * s,
                center.y + p.x * s + p.y * c
            )
        }
    }

    fun contains(point: Offset, tolerance: Float = 0f): Boolean {
        val r = Math.toRadians((-rotationDegrees).toDouble())
        val c = cos(r).toFloat()
        val s = sin(r).toFloat()
        val dx = point.x - center.x
        val dy = point.y - center.y
        val x = dx * c - dy * s
        val y = dx * s + dy * c
        return x in (-width / 2f - tolerance)..(width / 2f + tolerance) &&
            y in (-height / 2f - tolerance)..(height / 2f + tolerance)
    }
}

object TransformGeometry {
    fun bounds(points: List<Offset>): TransformBox? {
        if (points.isEmpty()) return null
        val minX = points.minOf { it.x }
        val maxX = points.maxOf { it.x }
        val minY = points.minOf { it.y }
        val maxY = points.maxOf { it.y }
        return TransformBox(
            center = Offset((minX + maxX) / 2f, (minY + maxY) / 2f),
            width = (maxX - minX).coerceAtLeast(1f),
            height = (maxY - minY).coerceAtLeast(1f)
        )
    }

    fun move(points: List<Offset>, delta: Offset): List<Offset> =
        points.map { it + delta }

    fun scale(points: List<Offset>, center: Offset, sx: Float, sy: Float): List<Offset> =
        points.map {
            Offset(
                center.x + (it.x - center.x) * sx,
                center.y + (it.y - center.y) * sy
            )
        }

    fun rotate(points: List<Offset>, center: Offset, degrees: Float): List<Offset> {
        val r = Math.toRadians(degrees.toDouble())
        val c = cos(r).toFloat()
        val s = sin(r).toFloat()
        return points.map {
            val dx = it.x - center.x
            val dy = it.y - center.y
            Offset(center.x + dx * c - dy * s, center.y + dx * s + dy * c)
        }
    }

    fun flipHorizontal(points: List<Offset>, centerX: Float): List<Offset> =
        points.map { Offset(2f * centerX - it.x, it.y) }

    fun flipVertical(points: List<Offset>, centerY: Float): List<Offset> =
        points.map { Offset(it.x, 2f * centerY - it.y) }
}
