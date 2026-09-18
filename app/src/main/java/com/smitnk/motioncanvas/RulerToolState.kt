package com.smitnk.motioncanvas

import androidx.compose.ui.geometry.Offset
import kotlin.math.atan2
import kotlin.math.sqrt

data class RulerToolState(
    val enabled: Boolean = false,
    val start: Offset? = null,
    val end: Offset? = null,
    val snapAngles: Boolean = true
) {
    val length: Float
        get() {
            val a = start ?: return 0f
            val b = end ?: return 0f
            val dx = b.x - a.x
            val dy = b.y - a.y
            return sqrt(dx * dx + dy * dy)
        }

    val angleDegrees: Float
        get() {
            val a = start ?: return 0f
            val b = end ?: return 0f
            return Math.toDegrees(atan2((b.y - a.y).toDouble(), (b.x - a.x).toDouble())).toFloat()
        }

    fun begin(point: Offset) = copy(enabled = true, start = point, end = point)
    fun move(point: Offset) = copy(end = point)
    fun clear() = copy(start = null, end = null)
}
