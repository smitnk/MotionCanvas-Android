package com.smitnk.motioncanvas.brush

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.cos
import kotlin.math.sin

/**
 * Hokusai-inspired advanced raster brush layer.
 *
 * This is an independent Kotlin implementation of compatible brush concepts
 * (pressure response, spacing, scatter, direction/angle and deterministic jitter).
 * It does not copy Hokusai Rust source.
 */
object AdvancedBrushEngine {
    fun draw(
        bitmap: Bitmap,
        points: List<androidx.compose.ui.geometry.Offset>,
        pressures: List<Float>,
        color: Int,
        width: Float,
        opacity: Float,
        spacing: Float,
        scatter: Float,
        angleJitter: Float
    ) {
        if (bitmap.isRecycled || points.size < 2) return
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
            this.color = color
            style = Paint.Style.FILL
        }
        val step = (width * spacing.coerceIn(0.04f, 0.9f)).coerceAtLeast(1f)
        var distanceCarry = 0f
        var seed = 0x13579BDF

        fun random(): Float {
            seed = seed * 1103515245 + 12345
            return ((seed ushr 8) and 0x00FFFFFF) / 16777215f
        }

        for (i in 1 until points.size) {
            val a = points[i - 1]
            val b = points[i]
            val dx = b.x - a.x
            val dy = b.y - a.y
            val length = kotlin.math.sqrt(dx * dx + dy * dy)
            if (length <= 0f) continue
            var travelled = 0f
            while (travelled <= length) {
                val t = travelled / length
                val x = a.x + dx * t
                val y = a.y + dy * t
                val p = pressures.getOrNull(i)?.coerceIn(0.05f, 1.25f) ?: 1f
                val radius = (width * (0.38f + p * 0.72f)).coerceAtLeast(0.5f)
                val jitter = scatter.coerceIn(0f, 1f) * radius
                val jx = (random() - 0.5f) * 2f * jitter
                val jy = (random() - 0.5f) * 2f * jitter
                paint.alpha = (opacity.coerceIn(0f, 1f) * (0.55f + p * 0.45f) * 255f).toInt()
                canvas.drawCircle(x + jx, y + jy, radius, paint)
                travelled += step
            }
            distanceCarry += length
            if (distanceCarry > step * 4f) distanceCarry = 0f
        }
    }
}
