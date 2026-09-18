package com.smitnk.motioncanvas

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import kotlin.math.max

/**
 * Small raster paint engine inspired by the architecture used by open-source
 * Android drawing apps. It does not copy third-party source code.
 *
 * Each layer owns a mutable ARGB bitmap. Brush strokes are committed directly
 * to pixels, while the existing vector tools remain available for shapes and
 * selection.
 */
class RasterBrushEngine(
    private val width: Int,
    private val height: Int
) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        style = Paint.Style.STROKE
    }

    fun newLayer(): Bitmap =
        Bitmap.createBitmap(max(1, width), max(1, height), Bitmap.Config.ARGB_8888)

    fun drawStroke(
        bitmap: Bitmap,
        points: List<Pair<Float, Float>>,
        color: Int,
        brushSize: Float,
        opacity: Float,
        eraser: Boolean = false
    ) {
        if (points.isEmpty() || bitmap.isRecycled) return
        val canvas = Canvas(bitmap)
        paint.reset()
        paint.isAntiAlias = true
        paint.isDither = true
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = brushSize.coerceAtLeast(1f)
        paint.alpha = (opacity.coerceIn(0f, 1f) * 255f).toInt()

        if (eraser) {
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        }

        if (points.size == 1) {
            canvas.drawCircle(points[0].first, points[0].second, brushSize / 2f, paint)
        } else {
            val path = Path()
            path.moveTo(points[0].first, points[0].second)
            for (i in 1 until points.size) {
                path.lineTo(points[i].first, points[i].second)
            }
            paint.color = color
            canvas.drawPath(path, paint)
        }
        paint.xfermode = null
    }
}
