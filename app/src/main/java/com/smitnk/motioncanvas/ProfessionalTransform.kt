package com.smitnk.motioncanvas

import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import kotlin.math.max

data class TransformOptions(
    val lockAspectRatio: Boolean = false,
    val snapRotation: Boolean = false,
    val rotationSnapDegrees: Float = 15f,
    val allowFlip: Boolean = true
)

object ProfessionalTransform {
    fun resize(
        box: TransformBox,
        handle: TransformHandle,
        pointerDelta: Offset,
        options: TransformOptions = TransformOptions(),
        minSize: Float = 8f
    ): TransformBox {
        val horizontal = handle in setOf(
            TransformHandle.TOP_LEFT, TransformHandle.TOP_RIGHT,
            TransformHandle.RIGHT, TransformHandle.BOTTOM_RIGHT,
            TransformHandle.BOTTOM_LEFT, TransformHandle.LEFT
        )
        val vertical = handle in setOf(
            TransformHandle.TOP_LEFT, TransformHandle.TOP,
            TransformHandle.TOP_RIGHT, TransformHandle.BOTTOM_RIGHT,
            TransformHandle.BOTTOM, TransformHandle.BOTTOM_LEFT
        )

        var width = if (horizontal) box.width + horizontalDelta(handle, pointerDelta.x) else box.width
        var height = if (vertical) box.height + verticalDelta(handle, pointerDelta.y) else box.height

        if (options.lockAspectRatio && horizontal && vertical) {
            val ratio = (box.width / max(box.height, minSize)).coerceAtLeast(0.001f)
            if (abs(pointerDelta.x) >= abs(pointerDelta.y)) {
                height = abs(width / ratio) * if (height >= 0f) 1f else -1f
            } else {
                width = abs(height * ratio) * if (width >= 0f) 1f else -1f
            }
        }

        if (!options.allowFlip) {
            width = max(minSize, abs(width))
            height = max(minSize, abs(height))
        } else {
            if (abs(width) < minSize) width = if (width < 0f) -minSize else minSize
            if (abs(height) < minSize) height = if (height < 0f) -minSize else minSize
        }

        val sx = width - box.width
        val sy = height - box.height
        return box.copy(
            center = box.center + Offset(
                if (horizontal) sx / 2f else 0f,
                if (vertical) sy / 2f else 0f
            ),
            width = abs(width),
            height = abs(height)
        )
    }

    fun rotation(
        box: TransformBox,
        startPointer: Offset,
        pointer: Offset,
        options: TransformOptions = TransformOptions()
    ): TransformBox {
        val before = TransformHandleGeometry.angle(box.center, startPointer)
        val after = TransformHandleGeometry.angle(box.center, pointer)
        var rotation = box.rotationDegrees + (after - before)
        if (options.snapRotation) {
            val step = options.rotationSnapDegrees.coerceAtLeast(1f)
            rotation = kotlin.math.round(rotation / step) * step
        }
        return box.copy(rotationDegrees = rotation)
    }

    fun flipHorizontal(box: TransformBox): TransformBox =
        box.copy(width = -box.width)

    fun flipVertical(box: TransformBox): TransformBox =
        box.copy(height = -box.height)

    private fun horizontalDelta(handle: TransformHandle, dx: Float): Float =
        when (handle) {
            TransformHandle.TOP_LEFT, TransformHandle.LEFT, TransformHandle.BOTTOM_LEFT -> -dx
            else -> dx
        }

    private fun verticalDelta(handle: TransformHandle, dy: Float): Float =
        when (handle) {
            TransformHandle.TOP_LEFT, TransformHandle.TOP, TransformHandle.TOP_RIGHT -> -dy
            else -> dy
        }
}
