package com.smitnk.motioncanvas.brush

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.ui.geometry.Offset
import kotlin.math.max
import kotlin.math.min

/**
 * Lightweight raster smudge/smear engine for MotionCanvas.
 * Uses a local bitmap sample and drag direction to pull pixels.
 */
object SmudgeEngine {
    fun smear(bitmap: Bitmap, from: Offset, to: Offset, radius: Float, strength: Float) {
        if (bitmap.isRecycled) return
        val r = radius.coerceIn(2f, 256f)
        val left = max(0, (min(from.x, to.x) - r).toInt())
        val top = max(0, (min(from.y, to.y) - r).toInt())
        val right = min(bitmap.width, (max(from.x, to.x) + r).toInt() + 1)
        val bottom = min(bitmap.height, (max(from.y, to.y) + r).toInt() + 1)
        if (right <= left || bottom <= top) return
        val w = right - left
        val h = bottom - top
        val patch = Bitmap.createBitmap(bitmap, left, top, w, h)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            alpha = (strength.coerceIn(0f, 1f) * 255f).toInt()
        }
        val dx = to.x - from.x
        val dy = to.y - from.y
        canvas.save()
        canvas.clipRect(left, top, right, bottom)
        canvas.drawBitmap(patch, left + dx * 0.55f, top + dy * 0.55f, paint)
        canvas.restore()
        patch.recycle()
    }
}
