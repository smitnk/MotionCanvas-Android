package com.smitnk.motioncanvas

import androidx.compose.ui.geometry.Offset
import kotlin.math.max
import kotlin.math.min

data class TransformInteraction(
    val handle: TransformHandle = TransformHandle.NONE,
    val startPointer: Offset = Offset.Zero,
    val startBox: TransformBox? = null
)

object TransformInteractionController {
    fun begin(box: TransformBox, pointer: Offset): TransformInteraction =
        TransformInteraction(
            handle = TransformHandleGeometry.hitTest(box, pointer).handle,
            startPointer = pointer,
            startBox = box
        )

    fun move(
        interaction: TransformInteraction,
        pointer: Offset,
        minSize: Float = 1f
    ): TransformBox? {
        val box = interaction.startBox ?: return null
        val handle = interaction.handle
        val dx = pointer.x - interaction.startPointer.x
        val dy = pointer.y - interaction.startPointer.y

        return when (handle) {
            TransformHandle.NONE -> null
            TransformHandle.PIVOT -> box.copy(pivot = box.pivot + Offset(dx, dy))
            TransformHandle.ROTATE -> {
                val before = TransformHandleGeometry.angle(box.center, interaction.startPointer)
                val after = TransformHandleGeometry.angle(box.center, pointer)
                box.copy(rotationDegrees = box.rotationDegrees + (after - before))
            }
            TransformHandle.TOP_LEFT -> resize(box, -dx, -dy, true, true, minSize)
            TransformHandle.TOP -> resize(box, 0f, -dy, false, true, minSize)
            TransformHandle.TOP_RIGHT -> resize(box, dx, -dy, true, true, minSize)
            TransformHandle.RIGHT -> resize(box, dx, 0f, true, false, minSize)
            TransformHandle.BOTTOM_RIGHT -> resize(box, dx, dy, true, true, minSize)
            TransformHandle.BOTTOM -> resize(box, 0f, dy, false, true, minSize)
            TransformHandle.BOTTOM_LEFT -> resize(box, -dx, dy, true, true, minSize)
            TransformHandle.LEFT -> resize(box, -dx, 0f, true, false, minSize)
        }
    }

    private fun resize(
        box: TransformBox,
        dx: Float,
        dy: Float,
        horizontal: Boolean,
        vertical: Boolean,
        minSize: Float
    ): TransformBox {
        val newWidth = if (horizontal) max(minSize, box.width + dx) else box.width
        val newHeight = if (vertical) max(minSize, box.height + dy) else box.height
        val centerShiftX = if (horizontal) dx / 2f else 0f
        val centerShiftY = if (vertical) dy / 2f else 0f
        return box.copy(
            center = box.center + Offset(centerShiftX, centerShiftY),
            width = min(newWidth, 100000f),
            height = min(newHeight, 100000f)
        )
    }
}
