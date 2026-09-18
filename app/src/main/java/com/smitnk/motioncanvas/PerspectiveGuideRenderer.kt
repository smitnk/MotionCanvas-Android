package com.smitnk.motioncanvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope

object PerspectiveGuideRenderer {
    fun DrawScope.drawGuide(
        guide: PerspectiveGuideModel,
        selectedPoint: Int = -1,
        width: Float,
        height: Float
    ) {
        drawLine(
            Color.Gray.copy(alpha = 0.45f),
            Offset(0f, guide.horizonY),
            Offset(width, guide.horizonY),
            2f
        )
        val origin = Offset(width / 2f, guide.horizonY)
        guide.vanishingPoints.forEachIndexed { index, point ->
            drawLine(Color.Gray.copy(alpha = 0.28f), origin, point, 1.5f)
            drawCircle(
                if (index == selectedPoint) Color.Yellow else Color.Cyan,
                18f,
                point
            )
        }
    }
}
