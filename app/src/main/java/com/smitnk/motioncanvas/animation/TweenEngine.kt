package com.smitnk.motioncanvas.animation

import androidx.compose.ui.geometry.Offset
import com.smitnk.motioncanvas.Frame
import com.smitnk.motioncanvas.LayerFrame
import com.smitnk.motioncanvas.Stroke
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sqrt

enum class TweenEasing { LINEAR, EASE_IN, EASE_OUT, EASE_IN_OUT }

object TweenEngine {
    fun ease(t: Float, easing: TweenEasing): Float {
        val x = t.coerceIn(0f, 1f)
        return when (easing) {
            TweenEasing.LINEAR -> x
            TweenEasing.EASE_IN -> x * x
            TweenEasing.EASE_OUT -> 1f - (1f - x) * (1f - x)
            TweenEasing.EASE_IN_OUT -> if (x < 0.5f) 2f * x * x else 1f - (-2f * x + 2f).pow(2f) / 2f
        }
    }

    fun interpolate(a: Frame, b: Frame, rawT: Float, easing: TweenEasing): Frame {
        val t = ease(rawT, easing)
        val count = maxOf(a.layers.size, b.layers.size)
        val layers = (0 until count).map { li ->
            val la = a.layers.getOrNull(li) ?: LayerFrame()
            val lb = b.layers.getOrNull(li) ?: LayerFrame()
            val strokes = interpolateStrokes(la.strokes, lb.strokes, t)
            LayerFrame(strokes, if (t < 0.5f) la.hold else lb.hold)
        }
        return Frame(layers)
    }

    private fun interpolateStrokes(a: List<Stroke>, b: List<Stroke>, t: Float): List<Stroke> {
        val count = maxOf(a.size, b.size)
        return (0 until count).map { i ->
            val sa = a.getOrNull(i) ?: b.getOrNull(i) ?: return@map Stroke(emptyList(), color = androidx.compose.ui.graphics.Color.Transparent, width = 1f)
            val sb = b.getOrNull(i) ?: sa
            interpolateStroke(sa, sb, t)
        }.filter { it.points.isNotEmpty() }
    }

    private fun interpolateStroke(a: Stroke, b: Stroke, t: Float): Stroke {
        val n = maxOf(a.points.size, b.points.size)
        if (n == 0) return a
        fun sample(points: List<Offset>, i: Int): Offset {
            if (points.size == 1) return points[0]
            val p = i.toFloat() / (n - 1).coerceAtLeast(1)
            val x = p * (points.size - 1)
            val lo = x.toInt().coerceIn(0, points.lastIndex)
            val hi = (lo + 1).coerceAtMost(points.lastIndex)
            val f = x - lo
            return Offset(
                points[lo].x + (points[hi].x - points[lo].x) * f,
                points[lo].y + (points[hi].y - points[lo].y) * f
            )
        }
        val points = (0 until n).map { i ->
            val pa = sample(a.points, i)
            val pb = sample(b.points, i)
            Offset(pa.x + (pb.x - pa.x) * t, pa.y + (pb.y - pa.y) * t)
        }
        return a.copy(
            points = points,
            color = androidx.compose.ui.graphics.Color(
                red = a.color.red + (b.color.red - a.color.red) * t,
                green = a.color.green + (b.color.green - a.color.green) * t,
                blue = a.color.blue + (b.color.blue - a.color.blue) * t,
                alpha = a.color.alpha + (b.color.alpha - a.color.alpha) * t
            ),
            width = a.width + (b.width - a.width) * t,
            opacity = a.opacity + (b.opacity - a.opacity) * t,
            closed = if (t < 0.5f) a.closed else b.closed,
            filled = if (t < 0.5f) a.filled else b.filled
        )
    }
}
