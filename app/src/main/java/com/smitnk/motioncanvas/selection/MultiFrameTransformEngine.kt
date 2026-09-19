package com.smitnk.motioncanvas.selection

import androidx.compose.ui.geometry.Offset
import com.smitnk.motioncanvas.Stroke
import kotlin.math.cos
import kotlin.math.sin

data class MultiFrameTransform(
    val translation: Offset = Offset.Zero,
    val scale: Float = 1f,
    val rotationDegrees: Float = 0f,
    val pivot: Offset = Offset.Zero,
    val flipX: Boolean = false,
    val flipY: Boolean = false
)

object MultiFrameTransformEngine {
    fun apply(stroke: Stroke, transform: MultiFrameTransform): Stroke {
        if (stroke.points.isEmpty()) return stroke
        val r = Math.toRadians(transform.rotationDegrees.toDouble())
        val c = cos(r).toFloat()
        val s = sin(r).toFloat()
        val sx = transform.scale * if (transform.flipX) -1f else 1f
        val sy = transform.scale * if (transform.flipY) -1f else 1f
        val points = stroke.points.map { p ->
            val x = (p.x - transform.pivot.x) * sx
            val y = (p.y - transform.pivot.y) * sy
            Offset(
                transform.pivot.x + x * c - y * s + transform.translation.x,
                transform.pivot.y + x * s + y * c + transform.translation.y
            )
        }
        return stroke.copy(points = points)
    }

    fun applyToFrames(frames: List<List<Stroke>>, transform: MultiFrameTransform): List<List<Stroke>> =
        frames.map { frame -> frame.map { apply(it, transform) } }
}
