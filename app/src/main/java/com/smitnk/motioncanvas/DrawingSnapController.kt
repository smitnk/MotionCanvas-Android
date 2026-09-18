package com.smitnk.motioncanvas

import androidx.compose.ui.geometry.Offset

object DrawingSnapController {
    fun snap(
        start: Offset,
        point: Offset,
        tool: Tool,
        guide: PerspectiveGuideModel
    ): Offset {
        val shapeTool = tool == Tool.LINE || tool == Tool.RECTANGLE || tool == Tool.ELLIPSE
        if (!shapeTool || !guide.snapEnabled) return point

        val perspective = guide.snap(point)
        return PrecisionRulerEngine.snapAngle(start, perspective).point
    }
}
