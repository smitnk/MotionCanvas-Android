package com.smitnk.motioncanvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.cos
import kotlin.math.sin

object RulerRenderer {
    fun DrawScope.draw(
        state: RulerToolState,
        width: Float,
        height: Float
    ) {
        val start = state.start ?: return
        val end = state.end ?: return

        drawLine(
            Color.Cyan.copy(alpha = 0.8f),
            start,
            end,
            strokeWidth = 4f
        )
        drawCircle(Color.Cyan, 14f, start)
        drawCircle(Color.Cyan, 14f, end)

        val dx = end.x - start.x
        val dy = end.y - start.y
        val angle = state.angleDegrees
        val radians = Math.toRadians(angle.toDouble())
        val tickLength = 28f
        val nx = (-sin(radians) * tickLength).toFloat()
        val ny = (cos(radians) * tickLength).toFloat()

        drawLine(
            Color.Cyan.copy(alpha = 0.7f),
            Offset(start.x - nx, start.y - ny),
            Offset(start.x + nx, start.y + ny),
            strokeWidth = 3f
        )
        drawLine(
            Color.Cyan.copy(alpha = 0.7f),
            Offset(end.x - nx, end.y - ny),
            Offset(end.x + nx, end.y + ny),
            strokeWidth = 3f
        )
    }
}
