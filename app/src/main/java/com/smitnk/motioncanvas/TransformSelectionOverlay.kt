package com.smitnk.motioncanvas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * Reusable selection/transform interaction surface.
 *
 * Inspired by the handle-driven interaction architecture used by
 * SmartToolFactory's MIT-licensed Compose-Image project, but implemented
 * independently for MotionCanvas's TransformGeometry model.
 */
@Composable
fun TransformSelectionOverlay(
    box: TransformBox?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    handleRadius: Float = 12f,
    onTransformStart: (TransformInteraction) -> Unit = {},
    onTransformChange: (TransformBox) -> Unit = {},
    onTransformEnd: (TransformBox) -> Unit = {}
) {
    if (!enabled || box == null) return

    var interaction by remember(box) { mutableStateOf<TransformInteraction?>(null) }

    Canvas(
        modifier = modifier.pointerInput(box) {
            detectDragGestures(
                onDragStart = { point ->
                    val started = TransformInteractionController.begin(box, point)
                    if (started.handle != TransformHandle.NONE) {
                        interaction = started
                        onTransformStart(started)
                    }
                },
                onDrag = { change, _ ->
                    val active = interaction ?: return@detectDragGestures
                    change.consume()
                    val updated = TransformInteractionController.move(active, change.position)
                        ?: return@detectDragGestures
                    onTransformChange(updated)
                    interaction = active.copy(startBox = active.startBox)
                },
                onDragEnd = {
                    interaction?.startBox?.let(onTransformEnd)
                    interaction = null
                },
                onDragCancel = {
                    interaction = null
                }
            )
        }
    ) {
        val handleMap = TransformHandleGeometry.handles(box)
        val corners = box.corners()

        for (index in corners.indices) {
            val start = corners[index]
            val end = corners[(index + 1) % corners.size]
            drawLine(Color.Black, start, end, strokeWidth = 2f)
        }

        handleMap.forEach { (handle, point) ->
            if (handle != TransformHandle.NONE) {
                drawCircle(
                    color = if (handle == TransformHandle.ROTATE) Color.Black else Color.White,
                    radius = handleRadius,
                    center = point
                )
                drawCircle(
                    color = Color.Black,
                    radius = handleRadius,
                    center = point,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f)
                )
            }
        }
    }
}
