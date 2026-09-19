package com.smitnk.motioncanvas.brush

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import kotlin.math.max

/**
 * Texture brush built around Android's open-source Canvas/Shader primitives.
 * AndroidX/Android graphics sources are Apache-2.0 licensed.
 *
 * The engine generates a small procedural paper/charcoal tile and uses a
 * BitmapShader so the texture follows the brush stroke without requiring
 * per-pixel work for every dab.
 */
object TextureBrushEngine {
    fun draw(
        bitmap: Bitmap,
        points: List<androidx.compose.ui.geometry.Offset>,
        color: Int,
        width: Float,
        opacity: Float,
        textureAmount: Float = 0.55f
    ) {
        if (points.size < 2 || bitmap.isRecycled) return

        val tileSize = max(32, (width * 2.5f).toInt())
        val tile = Bitmap.createBitmap(tileSize, tileSize, Bitmap.Config.ARGB_8888)
        val tileCanvas = Canvas(tile)
        tileCanvas.drawColor(Color.TRANSPARENT)

        val grain = Paint(Paint.ANTI_ALIAS_FLAG)
        val seed = (width * 31f + color * 0.0001f).toInt()
        for (y in 0 until tileSize step 3) {
            for (x in 0 until tileSize step 3) {
                val n = ((x * 73856093) xor (y * 19349663) xor seed) and 255
                val alpha = (n * textureAmount.coerceIn(0f, 1f) * 0.45f).toInt()
                grain.color = Color.argb(alpha, 255, 255, 255)
                tileCanvas.drawCircle(x.toFloat(), y.toFloat(), 1.2f, grain)
            }
        }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
        paint.color = color
        paint.alpha = (opacity.coerceIn(0f, 1f) * 255f).toInt()
        paint.strokeWidth = width.coerceAtLeast(1f)
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        paint.shader = BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)

        val path = Path()
        path.moveTo(points.first().x, points.first().y)
        points.drop(1).forEach { path.lineTo(it.x, it.y) }
        Canvas(bitmap).drawPath(path, paint)

        paint.shader = null
        tile.recycle()
    }
}
