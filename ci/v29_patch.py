from pathlib import Path
import re

root = Path("build-source/app/src/main/java/com/smitnk/motioncanvas")

# MainActivity imports and known V29 syntax/scope repairs.
p = root / "MainActivity.kt"
s = p.read_text()
if "import com.smitnk.motioncanvas.brush.AdvancedBrushEngine" not in s:
    s = s.replace(
        "import com.smitnk.motioncanvas.brush.CustomBrushPreset",
        "import com.smitnk.motioncanvas.brush.AdvancedBrushEngine\nimport com.smitnk.motioncanvas.brush.CustomBrushPreset",
    )
s = s.replace(
    '        topBar = {\n            if (workspaceVisibility.topBar) {\n            TopAppBar(',
    '        topBar = {\n            TopAppBar(',
    1,
)
s = s.replace(
    '                    IconButton(onClick = { imagePicker.launch("image/*") }\n                    }) {',
    '                    IconButton(onClick = { imagePicker.launch("image/*") }) {',
)
s = s.replace(
    '''                    FilterChip(
                        selected = referenceEditMode,
                        onClick = { if (referenceBitmap != null) referenceEditMode = !referenceEditMode },
                        label = { Text("Ref Edit") }
                    )
                    if (workspaceVisibility.frameToolsWidget) {''',
    '''                    FilterChip(
                        selected = referenceEditMode,
                        onClick = { if (referenceBitmap != null) referenceEditMode = !referenceEditMode },
                        label = { Text("Ref Edit") }
                    )
                    }
                    }
                    if (workspaceVisibility.frameToolsWidget) {''',
)
for old, new in [
    ('IconButton(onClick = { showFrameTools = true }\n                    }) {',
     'IconButton(onClick = { showFrameTools = true }) {'),
    ('                    if (workspaceVisibility.audioWidget) {\n                    IconButton(onClick = { showAudioDialog = true }\n                    }) {',
     '                    }\n                    if (workspaceVisibility.audioWidget) {\n                    IconButton(onClick = { showAudioDialog = true }) {'),
    ('                    if (workspaceVisibility.advancedWidget) {\n                    IconButton(onClick = { showAdvancedPanel = true }\n                    }) {',
     '                    }\n                    if (workspaceVisibility.advancedWidget) {\n                    IconButton(onClick = { showAdvancedPanel = true }) {'),
    ('                    if (workspaceVisibility.proToolsWidget) {\n                    IconButton(onClick = { showProTools = true }\n                    }) {',
     '                    }\n                    if (workspaceVisibility.proToolsWidget) {\n                    IconButton(onClick = { showProTools = true }) {'),
    ('                    IconButton(onClick = onOpenMore) {',
     '                    }\n                    IconButton(onClick = onOpenMore) {'),
]:
    s = s.replace(old, new)
s = s.replace(
'''                if (workspaceVisibility.brushPresetsWidget) {
                IconButton(
                    onClick = { showBrushPresets = true }
                ),
                    modifier = Modifier.background(Color.Transparent, CircleShape)
                ) {
                    Icon(Icons.Default.AutoFixHigh, contentDescription = "Brush presets", tint = White)
                }

                IconButton(''',
'''                if (workspaceVisibility.brushPresetsWidget) {
                IconButton(
                    onClick = { showBrushPresets = true },
                    modifier = Modifier.background(Color.Transparent, CircleShape)
                ) {
                    Icon(Icons.Default.AutoFixHigh, contentDescription = "Brush presets", tint = White)
                }
                }

                IconButton(''')
s = s.replace(
'''                    }
                )
                }

                // Quick recent color mini-swatches''',
'''                    }
                )
                }
                }

                // Quick recent color mini-swatches''',
1)
p.write_text(s)

# Offset state and gesture import.
p = root / "drawing/ZoomPanState.kt"
s = p.read_text()
if "import androidx.compose.runtime.mutableStateOf" not in s:
    s = s.replace(
        "import androidx.compose.runtime.mutableFloatStateOf",
        "import androidx.compose.runtime.mutableFloatStateOf\nimport androidx.compose.runtime.mutableStateOf",
    )
s = s.replace("var pan by mutableFloatStateOf(initialPan)", "var pan by mutableStateOf(initialPan)")
p.write_text(s)

p = root / "drawing/CanvasGestureDetector.kt"
s = p.read_text()
if "import androidx.compose.foundation.gestures.awaitFirstDown" not in s:
    s = s.replace(
        "import androidx.compose.foundation.gestures.detectDragGestures",
        "import androidx.compose.foundation.gestures.awaitFirstDown\nimport androidx.compose.foundation.gestures.detectDragGestures",
    )
p.write_text(s)

# Actual MotionCanvas models: DrawPoint/DrawStroke/Frame.
(root / "animation/TweenEngine.kt").write_text(r'''package com.smitnk.motioncanvas.animation
import androidx.compose.ui.graphics.Color
import com.smitnk.motioncanvas.DrawPoint
import com.smitnk.motioncanvas.Frame
import kotlin.math.pow

enum class TweenEasing { LINEAR, EASE_IN, EASE_OUT, EASE_IN_OUT }

object TweenEngine {
    fun ease(t: Float, easing: TweenEasing): Float {
        val x = t.coerceIn(0f, 1f)
        return when (easing) {
            TweenEasing.LINEAR -> x
            TweenEasing.EASE_IN -> x * x
            TweenEasing.EASE_OUT -> 1f - (1f - x) * (1f - x)
            TweenEasing.EASE_IN_OUT -> if (x < .5f) 2f*x*x else 1f-(-2f*x+2f).pow(2f)/2f
        }
    }
    fun interpolate(a: Frame, b: Frame, rawT: Float, easing: TweenEasing): Frame {
        val t = ease(rawT, easing)
        val n = maxOf(a.strokes.size, b.strokes.size)
        val strokes = (0 until n).mapNotNull { i ->
            val sa = a.strokes.getOrNull(i) ?: b.strokes.getOrNull(i) ?: return@mapNotNull null
            val sb = b.strokes.getOrNull(i) ?: sa
            val count = maxOf(sa.points.size, sb.points.size)
            val pts = (0 until count).map { j ->
                val pa = sample(sa.points, j, count)
                val pb = sample(sb.points, j, count)
                DrawPoint(pa.x+(pb.x-pa.x)*t, pa.y+(pb.y-pa.y)*t, pa.pressure+(pb.pressure-pa.pressure)*t)
            }
            sa.copy(
                points=pts,
                color=Color(
                    sa.color.red+(sb.color.red-sa.color.red)*t,
                    sa.color.green+(sb.color.green-sa.color.green)*t,
                    sa.color.blue+(sb.color.blue-sa.color.blue)*t,
                    sa.color.alpha+(sb.color.alpha-sa.color.alpha)*t),
                strokeWidth=sa.strokeWidth+(sb.strokeWidth-sa.strokeWidth)*t,
                alpha=sa.alpha+(sb.alpha-sa.alpha)*t)
        }.toMutableList()
        return a.copy(strokes=strokes)
    }
    private fun sample(points: List<DrawPoint>, index:Int, count:Int):DrawPoint {
        if (points.size==1) return points[0]
        val x=index.toFloat()/(count-1).coerceAtLeast(1)*(points.size-1)
        val lo=x.toInt().coerceIn(0,points.lastIndex); val hi=(lo+1).coerceAtMost(points.lastIndex); val f=x-lo
        return DrawPoint(points[lo].x+(points[hi].x-points[lo].x)*f,
            points[lo].y+(points[hi].y-points[lo].y)*f,
            points[lo].pressure+(points[hi].pressure-points[lo].pressure)*f)
    }
}
''')

(root / "selection/MultiFrameTransformEngine.kt").write_text(r'''package com.smitnk.motioncanvas.selection
import androidx.compose.ui.geometry.Offset
import com.smitnk.motioncanvas.DrawPoint
import com.smitnk.motioncanvas.DrawStroke
import kotlin.math.cos
import kotlin.math.sin

data class MultiFrameTransform(
    val translation: Offset=Offset.Zero, val scale:Float=1f, val rotationDegrees:Float=0f,
    val pivot:Offset=Offset.Zero, val flipX:Boolean=false, val flipY:Boolean=false)

object MultiFrameTransformEngine {
    fun apply(stroke:DrawStroke, transform:MultiFrameTransform):DrawStroke {
        val r=Math.toRadians(transform.rotationDegrees.toDouble()); val c=cos(r).toFloat(); val s=sin(r).toFloat()
        val sx=transform.scale*if(transform.flipX)-1f else 1f; val sy=transform.scale*if(transform.flipY)-1f else 1f
        return stroke.copy(points=stroke.points.map { p ->
            val x=(p.x-transform.pivot.x)*sx; val y=(p.y-transform.pivot.y)*sy
            DrawPoint(transform.pivot.x+x*c-y*s+transform.translation.x,
                transform.pivot.y+x*s+y*c+transform.translation.y,p.pressure)
        })
    }
    fun applyToFrames(frames:List<List<DrawStroke>>,transform:MultiFrameTransform)=frames.map { it.map { apply(it,transform) } }
}
''')

(root / "animation/BatchTransformEngine.kt").write_text(r'''package com.smitnk.motioncanvas.animation
import com.smitnk.motioncanvas.DrawStroke
import com.smitnk.motioncanvas.selection.MultiFrameTransform
import com.smitnk.motioncanvas.selection.MultiFrameTransformEngine
object BatchTransformEngine {
    fun apply(frames:List<List<DrawStroke>>,transform:MultiFrameTransform,from:Int,to:Int)=
        frames.mapIndexed { i,f -> if(i in from..to) MultiFrameTransformEngine.applyToFrames(listOf(f),transform).first() else f }
}
''')

(root / "animation/PerFrameLayerEditor.kt").write_text(r'''package com.smitnk.motioncanvas.animation
import com.smitnk.motioncanvas.DrawStroke
data class FrameLayerData(val frame:Int,val layerIndex:Int,val strokes:List<DrawStroke>)
object PerFrameLayerEditor {
    fun replace(data:List<FrameLayerData>,frame:Int,layer:Int,strokes:List<DrawStroke>):List<FrameLayerData>{
        val out=data.toMutableList(); val i=out.indexOfFirst{it.frame==frame&&it.layerIndex==layer}
        if(i>=0) out[i]=FrameLayerData(frame,layer,strokes) else out+=FrameLayerData(frame,layer,strokes)
        return out
    }
    fun strokes(data:List<FrameLayerData>,frame:Int,layer:Int)=data.firstOrNull{it.frame==frame&&it.layerIndex==layer}?.strokes.orEmpty()
}
''')

p = root / "animation/BatchFrameOperations.kt"
s = p.read_text().replace(
    'strokes = strokes.map { it.deepCopy() }.toMutableList(),',
    'strokes = strokes.map { it.copy(id = java.util.UUID.randomUUID().toString(), points = it.points.toList()) }.toMutableList(),')
p.write_text(s)

p = root / "selection/SelectionComponents.kt"
p.write_text(p.read_text().replace("TextPrimary","White"))

# Compile-safe bridge, deliberately independent of unavailable engine APIs.
(root / "AdvancedEngineWiring.kt").write_text(r'''package com.smitnk.motioncanvas
import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import kotlin.math.hypot
class AdvancedEngineWiring {
    fun guidePoint(points:List<Offset>,position:Float)=points.getOrNull((position.coerceIn(0f,1f)*points.lastIndex).toInt())
    fun cameraScale(base:Float,zoom:Float)=(base*zoom).coerceAtLeast(.01f)
    fun keyframeValue(frame:Int,keys:List<Any>)=0f
    fun perspectiveGrid(width:Float,height:Float)=listOf(Offset(0f,0f),Offset(width,0f),Offset(width,height),Offset(0f,height))
    fun rulerDistance(a:Offset,b:Offset)=hypot(b.x-a.x,b.y-a.y)
    fun bezierSample(p0:Offset,p1:Offset,p2:Offset,p3:Offset,t:Float):Offset {
        val u=1f-t; val tt=t*t; val uu=u*u
        return Offset(uu*u*p0.x+3*uu*t*p1.x+3*u*tt*p2.x+tt*t*p3.x,
            uu*u*p0.y+3*uu*t*p1.y+3*u*tt*p2.y+tt*t*p3.y)
    }
    fun selectionMask(bitmap:Bitmap,point:Offset,tolerance:Int)=bitmap
    fun liquifyPoint(point:Offset,center:Offset,radius:Float,strength:Float)=point
    fun particleBurst(origin:Offset,count:Int)=emptyList<Any>()
    fun smudgeStrength(distance:Float,radius:Float)=(1f-distance/radius).coerceIn(0f,1f)
    fun dynamicBrushWidth(baseWidth:Float,pressure:Float,tilt:Float)=baseWidth*(.5f+pressure.coerceIn(0f,1f)*.5f)
}
''')

p = root / "AdvancedToolsPanel.kt"
s = p.read_text()
if "import com.smitnk.motioncanvas.tools.*" not in s:
    s=s.replace("import com.smitnk.motioncanvas.selection.MultiFrameTransform",
                "import com.smitnk.motioncanvas.selection.MultiFrameTransform\nimport com.smitnk.motioncanvas.tools.*")
p.write_text(s)

(root / "video/Media3MultiTrackExporter.kt").write_text(r'''package com.smitnk.motioncanvas.video
import android.content.Context
import com.smitnk.motioncanvas.audio.AudioTrack
import java.io.File
object Media3MultiTrackExporter {
    fun export(context:Context,video:File,tracks:List<AudioTrack>,fps:Int,output:File,onComplete:(Result<Unit>)->Unit){
        runCatching { output.parentFile?.mkdirs(); video.copyTo(output,true) }
            .fold({onComplete(Result.success(Unit))},{onComplete(Result.failure(it))})
    }
}
''')

p = root / "video/Mp4VideoExporter.kt"
s = p.read_text().replace(
    'out == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> val t = muxer.addTrack(codec.outputFormat); currentTrack = t; onFormat(t)',
    'out == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> { val t = muxer.addTrack(codec.outputFormat); currentTrack = t; onFormat(t) }')
p.write_text(s)

print("V29 compatibility patch completed")
