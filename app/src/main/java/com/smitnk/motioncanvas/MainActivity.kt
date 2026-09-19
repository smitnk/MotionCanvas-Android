package com.smitnk.motioncanvas

import android.os.Bundle
import android.content.ContentValues
import android.provider.MediaStore
import android.graphics.Bitmap.CompressFormat
import android.net.Uri
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.util.zip.ZipInputStream
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint as AndroidPaint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.awaitEachGesture
import androidx.compose.ui.input.pointer.awaitFirstDown
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.onSizeChanged
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import java.util.concurrent.TimeUnit
import com.squareup.gifencoder.GifEncoder
import com.smitnk.motioncanvas.brush.TextureBrushEngine
import com.smitnk.motioncanvas.brush.AdvancedBrushEngine
import com.squareup.gifencoder.ImageOptions

data class Stroke(
    val points: List<Offset>,
    val inHandles: List<Offset> = emptyList(),
    val outHandles: List<Offset> = emptyList(),
    val pressures: List<Float> = emptyList(),
    val color: Color,
    val width: Float,
    val opacity: Float = 1f,
    val closed: Boolean = false,
    val filled: Boolean = false
)
enum class LayerBlendMode { NORMAL, MULTIPLY, SCREEN, OVERLAY, ADD }

data class ArtLayer(
    val name: String,
    val visible: Boolean = true,
    val opacity: Float = 1f,
    val clipToBelow: Boolean = false,
    val blendMode: LayerBlendMode = LayerBlendMode.NORMAL
)
data class LayerFrame(val strokes: List<Stroke> = emptyList(), val hold: Int = 1)
data class Frame(val layers: List<LayerFrame> = emptyList())
enum class Tool { BRUSH, ERASER, LINE, RECTANGLE, ELLIPSE, SELECT, FILL, EYEDROPPER }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MotionCanvasApp() }
    }
}

fun pointInPolygon(point: Offset, polygon: List<Offset>): Boolean {
    if (polygon.size < 3) return false
    var inside = false
    var j = polygon.lastIndex
    for (i in polygon.indices) {
        val a = polygon[i]
        val b = polygon[j]
        val intersects = ((a.y > point.y) != (b.y > point.y)) &&
            (point.x < (b.x - a.x) * (point.y - a.y) / ((b.y - a.y).takeIf { it != 0f } ?: 0.0001f) + a.x)
        if (intersects) inside = !inside
        j = i
    }
    return inside
}

fun transformPoints(
    points: List<Offset>,
    center: Offset,
    scale: Float,
    degrees: Float,
    delta: Offset = Offset.Zero
): List<Offset> {
    val r = degrees * PI.toFloat() / 180f
    val c = cos(r)
    val s = sin(r)
    return points.map { p ->
        val x = (p.x - center.x) * scale
        val y = (p.y - center.y) * scale
        Offset(
            center.x + x * c - y * s + delta.x,
            center.y + x * s + y * c + delta.y
        )
    }
}

@Composable
fun MotionCanvasApp() {
    var layers by remember { mutableStateOf(listOf(ArtLayer("Layer 1"))) }
    var frameData by remember { mutableStateOf(listOf(Frame(listOf(LayerFrame())))) }
    var currentStrokes by remember { mutableStateOf(listOf(emptyList<Stroke>())) }
    val rasterWidth = 1600
    val rasterHeight = 1200
    var rasterLayers by remember { mutableStateOf(listOf(Bitmap.createBitmap(rasterWidth, rasterHeight, Bitmap.Config.ARGB_8888))) }
    var rasterFrames by remember { mutableStateOf(listOf(listOf(Bitmap.createBitmap(rasterWidth, rasterHeight, Bitmap.Config.ARGB_8888)))) }
    var selectedLayer by remember { mutableIntStateOf(0) }
    var current by remember { mutableStateOf(emptyList<Offset>()) }
    var currentPressures by remember { mutableStateOf(emptyList<Float>()) }
    var selection by remember { mutableStateOf(emptyList<Offset>()) }
    var selectedStrokeIds by remember { mutableStateOf(emptySet<Int>()) }
    var tool by remember { mutableStateOf(Tool.BRUSH) }
    var brush by remember { mutableStateOf(Color.Black) }
    var showColorPicker by remember { mutableStateOf(false) }
    var hue by remember { mutableFloatStateOf(0f) }
    var saturation by remember { mutableFloatStateOf(1f) }
    var value by remember { mutableFloatStateOf(1f) }
    var colorAlpha by remember { mutableFloatStateOf(1f) }
    var hexColor by remember { mutableStateOf("#000000") }
    var recentColors by remember { mutableStateOf(listOf(Color.Black, Color.White, Color.Red, Color.Blue)) }
    var brushType by remember { mutableStateOf("Pencil") }
    var pressureEnabled by remember { mutableStateOf(true) }
    var width by remember { mutableFloatStateOf(10f) }
    var opacity by remember { mutableFloatStateOf(1f) }
    var stabilization by remember { mutableFloatStateOf(0.35f) }
    var streamline by remember { mutableFloatStateOf(0.35f) }
    var deepBrushEngine by remember { mutableStateOf(true) }
    var textureAmount by remember { mutableFloatStateOf(0.55f) }
    var advancedScatter by remember { mutableFloatStateOf(0.18f) }
    var advancedAngleJitter by remember { mutableFloatStateOf(0.15f) }
    var quickShape by remember { mutableStateOf(true) }
    var editStrokeIndex by remember { mutableStateOf<Int?>(null) }
    var editNodeIndex by remember { mutableIntStateOf(-1) }
    var nodeEditorMode by remember { mutableStateOf(false) }
    var bezierHandleMode by remember { mutableStateOf(false) }
    var activeHandle by remember { mutableIntStateOf(-1) }
    var activeHandleSide by remember { mutableStateOf("out") }
    var editShapeLabel by remember { mutableStateOf("Edit Shape") }
    var weightPaintMode by remember { mutableStateOf(false) }
    var weightJoint by remember { mutableIntStateOf(-1) }
    var weightRadius by remember { mutableFloatStateOf(180f) }
    var weightStrength by remember { mutableFloatStateOf(0.75f) }
    var weightJoints by remember { mutableStateOf(listOf<Offset>()) }
    var rigJoints by remember { mutableStateOf(listOf<Offset>()) }
    var rigParents by remember { mutableStateOf(listOf<Int>()) }
    var rigMode by remember { mutableStateOf(false) }
    var rigSelected by remember { mutableIntStateOf(-1) }
    var sculptMode by remember { mutableStateOf(false) }
    var sculptTool by remember { mutableStateOf("Grab") }
    var sculptRadius by remember { mutableFloatStateOf(140f) }
    var sculptStrength by remember { mutableFloatStateOf(0.65f) }
    var sculptSnapshotTaken by remember { mutableStateOf(false) }
    var spacing by remember { mutableFloatStateOf(0.18f) }
    var taper by remember { mutableFloatStateOf(0f) }
    var shapeFilled by remember { mutableStateOf(false) }
    var symmetry by remember { mutableStateOf(false) }
    var symmetryAxis by remember { mutableFloatStateOf(0.5f) }
    var radialSymmetry by remember { mutableStateOf(false) }
    var radialCount by remember { mutableIntStateOf(6) }
    var alphaLock by remember { mutableStateOf(false) }
    var clippingMask by remember { mutableStateOf(false) }
    var showGrid by remember { mutableStateOf(false) }
    var gridType by remember { mutableStateOf("2D") }
    var gridSpacing by remember { mutableFloatStateOf(100f) }
    var perspectivePoints by remember { mutableIntStateOf(1) }
    var pingPong by remember { mutableStateOf(false) }
    var playDirection by remember { mutableIntStateOf(1) }
    data class EditorSnapshot(
        val strokes: List<List<Stroke>>,
        val rasters: List<Bitmap>
    )
    var undo by remember { mutableStateOf(emptyList<EditorSnapshot>()) }
    var redo by remember { mutableStateOf(emptyList<EditorSnapshot>()) }
    var scale by remember { mutableFloatStateOf(1f) }
    var rotation by remember { mutableFloatStateOf(0f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var canvasSize by remember { mutableStateOf(androidx.compose.ui.geometry.Size(rasterWidth.toFloat(), rasterHeight.toFloat())) }
    var onionSkin by remember { mutableStateOf(true) }
    var fps by remember { mutableIntStateOf(12) }
    var frameIndex by remember { mutableIntStateOf(0) }
    var playing by remember { mutableStateOf(false) }
    var exportStatus by remember { mutableStateOf("") }
    val context = LocalContext.current

    fun artScale(): Float {
        if (canvasSize.width <= 0f || canvasSize.height <= 0f) return 1f
        return minOf(canvasSize.width / rasterWidth.toFloat(), canvasSize.height / rasterHeight.toFloat()) * 0.92f
    }

    fun screenToArt(p: Offset): Offset {
        val base = artScale()
        val cx = canvasSize.width / 2f + pan.x
        val cy = canvasSize.height / 2f + pan.y
        val dx = p.x - cx
        val dy = p.y - cy
        val r = -rotation * PI.toFloat() / 180f
        val c = cos(r); val s = sin(r)
        return Offset(
            (dx * c - dy * s) / (base * scale) + rasterWidth / 2f,
            (dx * s + dy * c) / (base * scale) + rasterHeight / 2f
        )
    }

    fun copyBitmap(source: Bitmap): Bitmap = source.copy(Bitmap.Config.ARGB_8888, true)

    fun projectJson(): String {
        fun esc(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"")
        val layerJson = layers.joinToString(",") { "{\"name\":\"" + esc(it.name) + "\",\"visible\":" + it.visible + ",\"opacity\":" + it.opacity + "}" }
        val frameJson = frameData.joinToString(",") { frame ->
            val ls = frame.layers.joinToString(",") { lf ->
                val ss = lf.strokes.joinToString(",") { s ->
                    val pts = s.points.joinToString(",") { "[" + it.x + "," + it.y + "]" }
                    "{\"color\":" + s.color.toArgb() + ",\"width\":" + s.width + ",\"opacity\":" + s.opacity + ",\"closed\":" + s.closed + ",\"filled\":" + s.filled + ",\"points\":[" + pts + "]}"
                }
                "{\"hold\":" + lf.hold + ",\"strokes\":[" + ss + "]}"}
            "{" + "\"layers\":[" + ls + "]}"
        }
        return "{\"version\":1,\"width\":" + rasterWidth + ",\"height\":" + rasterHeight + ",\"layers\":[" + layerJson + "],\"frames\":[" + frameJson + "]}"
    }

    fun saveProject(uri: Uri) {
        try {
            context.contentResolver.openOutputStream(uri)?.use { output -> ZipOutputStream(output).use { zip ->
                zip.putNextEntry(ZipEntry("project.json")); zip.write(projectJson().toByteArray(Charsets.UTF_8)); zip.closeEntry()
                rasterFrames.forEachIndexed { fi, frame -> frame.forEachIndexed { li, bitmap ->
                    zip.putNextEntry(ZipEntry("frames/f" + fi + "_l" + li + ".png")); bitmap.compress(CompressFormat.PNG, 100, zip); zip.closeEntry()
                }}
            }}
            exportStatus = "Project saved"
        } catch (e: Exception) { exportStatus = "Save failed" }
    }

    fun loadProject(uri: Uri) {
        try {
            val temp = mutableMapOf<String, ByteArray>()
            context.contentResolver.openInputStream(uri)?.use { input -> ZipInputStream(input).use { zip ->
                while (true) { val entry = zip.nextEntry ?: break; if (!entry.isDirectory) temp[entry.name] = zip.readBytes() }
            }}
            val json = temp["project.json"] ?: error("Missing project")
            val textJson = json.toString(Charsets.UTF_8)
            val count = Regex("\\{\"layers\"").findAll(textJson).count().coerceAtLeast(1)
            val loaded = ArrayList<List<Bitmap>>()
            for (fi in 0 until count) {
                loaded.add(layers.indices.map { li ->
                    val bytes = temp["frames/f" + fi + "_l" + li + ".png"]
                    if (bytes != null) android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size).copy(Bitmap.Config.ARGB_8888, true)
                    else Bitmap.createBitmap(rasterWidth, rasterHeight, Bitmap.Config.ARGB_8888)
                })
            }
            rasterFrames = loaded; frameData = List(count) { Frame(layers.map { LayerFrame() }) }; loadFrame(0); exportStatus = "Project loaded"
        } catch (e: Exception) { exportStatus = "Load failed" }
    }
    fun blendPorterDuff(mode: LayerBlendMode): PorterDuff.Mode? = when (mode) {
        LayerBlendMode.NORMAL -> null
        LayerBlendMode.MULTIPLY -> PorterDuff.Mode.MULTIPLY
        LayerBlendMode.SCREEN -> PorterDuff.Mode.SCREEN
        LayerBlendMode.OVERLAY -> PorterDuff.Mode.OVERLAY
        LayerBlendMode.ADD -> PorterDuff.Mode.ADD
    }

    fun composeVisibleLayers(): Bitmap {
        val output = Bitmap.createBitmap(rasterWidth, rasterHeight, Bitmap.Config.ARGB_8888)
        val canvas = AndroidCanvas(output)
        canvas.drawColor(android.graphics.Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        rasterLayers.forEachIndexed { i, source ->
            val layer = layers.getOrNull(i) ?: return@forEachIndexed
            if (!layer.visible || source.isRecycled) return@forEachIndexed
            var drawable = source
            var temporary: Bitmap? = null
            if (layer.clipToBelow && i > 0) {
                val below = rasterLayers.getOrNull(i - 1)
                if (below != null && !below.isRecycled) {
                    temporary = source.copy(Bitmap.Config.ARGB_8888, true)
                    val maskCanvas = AndroidCanvas(temporary)
                    val maskPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG)
                    maskPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
                    maskCanvas.drawBitmap(below, 0f, 0f, maskPaint)
                    maskPaint.xfermode = null
                    drawable = temporary
                }
            }
            val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG or AndroidPaint.DITHER_FLAG)
            paint.alpha = (layer.opacity.coerceIn(0f, 1f) * 255f).toInt()
            blendPorterDuff(layer.blendMode)?.let { paint.xfermode = PorterDuffXfermode(it) }
            canvas.drawBitmap(drawable, 0f, 0f, paint)
            paint.xfermode = null
            temporary?.recycle()
        }
        return output
    }

    fun exportCurrentPng() {
        val merged = Bitmap.createBitmap(rasterWidth, rasterHeight, Bitmap.Config.ARGB_8888)
        val canvas = AndroidCanvas(merged)
        canvas.drawColor(android.graphics.Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)
        val composed = composeVisibleLayers()
        canvas.drawBitmap(composed, 0f, 0f, null)
        composed.recycle()
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "MotionCanvas_F${frameIndex + 1}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MotionCanvas")
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        if (uri == null) { exportStatus = "Export failed"; return }
        resolver.openOutputStream(uri)?.use { merged.compress(CompressFormat.PNG, 100, it) }
        merged.recycle()
        exportStatus = "PNG exported to Pictures/MotionCanvas"
    }

    fun saveRasterFrame() {
        rasterFrames = rasterFrames.toMutableList().also { it[frameIndex] = rasterLayers.map { bitmap -> copyBitmap(bitmap) } }
    }

    fun loadRasterFrame(index: Int) {
        if (index !in rasterFrames.indices) return
        rasterLayers = rasterFrames[index].map { bitmap -> copyBitmap(bitmap) }
    }

    fun saveFrame() {
        if (frameIndex !in frameData.indices) return
        saveRasterFrame()
        val old = frameData[frameIndex]
        frameData = frameData.toMutableList().also {
            it[frameIndex] = Frame(currentStrokes.mapIndexed { i, strokes ->
                LayerFrame(strokes, old.layers.getOrNull(i)?.hold ?: 1)
            })
        }
    }

    fun loadFrame(index: Int) {
        if (index !in frameData.indices) return
        frameIndex = index
        currentStrokes = layers.indices.map { frameData[index].layers.getOrNull(it)?.strokes ?: emptyList() }
        loadRasterFrame(index)
        selectedStrokeIds = emptySet()
        selection = emptyList()
    }

    fun snapshot() {
        undo = (undo + EditorSnapshot(currentStrokes, rasterLayers.map { copyBitmap(it) })).takeLast(20)
        redo = emptyList()
    }

    fun restoreSnapshot(snapshot: EditorSnapshot) {
        currentStrokes = snapshot.strokes
        rasterLayers = snapshot.rasters.map { copyBitmap(it) }
        saveFrame()
    }

    fun floodFill(bitmap: Bitmap, startX: Int, startY: Int, color: Int, tolerance: Int = 12) {
        if (bitmap.isRecycled || startX !in 0 until bitmap.width || startY !in 0 until bitmap.height) return
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val startIndex = startY * bitmap.width + startX
        val target = pixels[startIndex]
        if (target == color) return
        fun near(a: Int, b: Int): Boolean {
            val ar = (a ushr 16) and 255; val ag = (a ushr 8) and 255; val ab = a and 255; val aa = a ushr 24
            val br = (b ushr 16) and 255; val bg = (b ushr 8) and 255; val bb = b and 255; val ba = b ushr 24
            return kotlin.math.abs(ar - br) <= tolerance && kotlin.math.abs(ag - bg) <= tolerance &&
                kotlin.math.abs(ab - bb) <= tolerance && kotlin.math.abs(aa - ba) <= tolerance
        }
        if (!near(target, target)) return
        val queueX = IntArray(bitmap.width * bitmap.height)
        val queueY = IntArray(bitmap.width * bitmap.height)
        var head = 0; var tail = 0
        queueX[tail] = startX; queueY[tail++] = startY
        val visited = BooleanArray(pixels.size)
        visited[startIndex] = true
        while (head < tail) {
            val x = queueX[head]; val y = queueY[head++]
            pixels[y * bitmap.width + x] = color
            val neighbors = intArrayOf(x - 1, y, x + 1, y, x, y - 1, x, y + 1)
            var i = 0
            while (i < neighbors.size) {
                val nx = neighbors[i]; val ny = neighbors[i + 1]; i += 2
                if (nx in 0 until bitmap.width && ny in 0 until bitmap.height) {
                    val idx = ny * bitmap.width + nx
                    if (!visited[idx] && near(pixels[idx], target)) {
                        visited[idx] = true
                        queueX[tail] = nx; queueY[tail++] = ny
                    }
                }
            }
        }
        bitmap.setPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    }

    fun nearestEditNode(point: Offset): Int {
        val index = editStrokeIndex ?: return -1
        val nodes = currentStrokes.getOrNull(selectedLayer)?.getOrNull(index)?.points ?: return -1
        if (nodes.isEmpty()) return -1
        var best = -1
        var bestDistance = Float.MAX_VALUE
        nodes.forEachIndexed { i, node ->
            val dx = node.x - point.x
            val dy = node.y - point.y
            val distance = kotlin.math.sqrt(dx * dx + dy * dy)
            if (distance < bestDistance) { bestDistance = distance; best = i }
        }
        return if (bestDistance <= 42f) best else -1
    }

    fun ensureBezierHandles(stroke: Stroke): Stroke {
        if (stroke.points.isEmpty()) return stroke
        if (stroke.inHandles.size == stroke.points.size && stroke.outHandles.size == stroke.points.size) return stroke
        val ins = stroke.points.mapIndexed { i, p ->
            if (i == 0) Offset.Zero else Offset((stroke.points[i - 1].x - p.x) * 0.25f, (stroke.points[i - 1].y - p.y) * 0.25f)
        }
        val outs = stroke.points.mapIndexed { i, p ->
            if (i == stroke.points.lastIndex) Offset.Zero else Offset((stroke.points[i + 1].x - p.x) * 0.25f, (stroke.points[i + 1].y - p.y) * 0.25f)
        }
        return stroke.copy(inHandles = ins, outHandles = outs)
    }

    fun updateBezierHandle(point: Offset, side: String) {
        val index = editStrokeIndex ?: return
        val node = editNodeIndex
        val strokes = currentStrokes.getOrNull(selectedLayer) ?: return
        if (index !in strokes.indices || node !in strokes[index].points.indices) return
        val stroke = ensureBezierHandles(strokes[index])
        val delta = Offset(point.x - stroke.points[node].x, point.y - stroke.points[node].y)
        val ins = stroke.inHandles.toMutableList()
        val outs = stroke.outHandles.toMutableList()
        if (side == "in") ins[node] = delta else outs[node] = delta
        val updated = stroke.copy(inHandles = ins, outHandles = outs)
        currentStrokes = currentStrokes.toMutableList().also {
            it[selectedLayer] = strokes.toMutableList().also { list -> list[index] = updated }
        }
    }

    fun addBezierNodeAndHandles() {
        val index = editStrokeIndex ?: return
        val strokes = currentStrokes.getOrNull(selectedLayer) ?: return
        if (index !in strokes.indices || strokes[index].points.size < 2) return
        addEditNode()
    }

    fun addEditNode() {
        val index = editStrokeIndex ?: return
        val strokes = currentStrokes.getOrNull(selectedLayer) ?: return
        if (index !in strokes.indices) return
        val stroke = strokes[index]
        if (stroke.points.size < 2) return
        snapshot()
        val nodes = stroke.points.toMutableList()
        val at = (editNodeIndex + 1).coerceIn(1, nodes.size - 1)
        val a = nodes[at - 1]; val b = nodes[at]
        nodes.add(at, Offset((a.x + b.x) / 2f, (a.y + b.y) / 2f))
        currentStrokes = currentStrokes.toMutableList().also {
            it[selectedLayer] = strokes.toMutableList().also { list -> list[index] = stroke.copy(points = nodes) }
        }
        editNodeIndex = at
    }

    fun deleteEditNode() {
        val index = editStrokeIndex ?: return
        val node = editNodeIndex
        val strokes = currentStrokes.getOrNull(selectedLayer) ?: return
        if (index !in strokes.indices || node !in strokes[index].points.indices || strokes[index].points.size <= 2) return
        snapshot()
        val nodes = strokes[index].points.toMutableList()
        nodes.removeAt(node)
        currentStrokes = currentStrokes.toMutableList().also {
            it[selectedLayer] = strokes.toMutableList().also { list -> list[index] = strokes[index].copy(points = nodes) }
        }
        editNodeIndex = (node - 1).coerceAtLeast(0).coerceAtMost(nodes.lastIndex)
    }

    fun respaceEditNodes() {
        val index = editStrokeIndex ?: return
        val strokes = currentStrokes.getOrNull(selectedLayer) ?: return
        if (index !in strokes.indices || strokes[index].points.size < 3) return
        snapshot()
        val points = strokes[index].points
        val cumulative = FloatArray(points.size)
        for (i in 1 until points.size) {
            val dx = points[i].x - points[i - 1].x; val dy = points[i].y - points[i - 1].y
            cumulative[i] = cumulative[i - 1] + kotlin.math.sqrt(dx * dx + dy * dy)
        }
        val total = cumulative.last()
        if (total <= 0f) return
        val result = (0 until points.size).map { n ->
            val target = total * n / (points.lastIndex.coerceAtLeast(1))
            var seg = 1
            while (seg < cumulative.size && cumulative[seg] < target) seg++
            if (seg >= cumulative.size) points.last() else {
                val a = points[seg - 1]; val b = points[seg]
                val span = (cumulative[seg] - cumulative[seg - 1]).coerceAtLeast(0.001f)
                val t = (target - cumulative[seg - 1]) / span
                Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
            }
        }
        currentStrokes = currentStrokes.toMutableList().also {
            it[selectedLayer] = strokes.toMutableList().also { list -> list[index] = strokes[index].copy(points = result) }
        }
    }

    fun finishShapeEditing() {
        if (editStrokeIndex != null) {
            saveFrame()
            editStrokeIndex = null
            editNodeIndex = -1
        }
    }

    fun addRigJoint(point: Offset) {
        rigJoints = rigJoints + point
        rigParents = rigParents + if (rigJoints.isEmpty()) -1 else rigSelected
        rigSelected = rigJoints.lastIndex
    }

    fun moveRigJoint(index: Int, point: Offset) {
        if (index !in rigJoints.indices) return
        val delta = point - rigJoints[index]
        rigJoints = rigJoints.toMutableList().also { it[index] = point }
        if (delta == Offset.Zero) return
        val strokeIndex = editStrokeIndex ?: return
        val strokes = currentStrokes.getOrNull(selectedLayer) ?: return
        if (strokeIndex !in strokes.indices) return
        val stroke = strokes[strokeIndex]
        val moved = stroke.points.map { p ->
            val d = p - point
            val dist = kotlin.math.sqrt(d.x * d.x + d.y * d.y)
            val influence = (1f - dist / weightRadius).coerceIn(0f, 1f) * weightStrength
            p + delta * influence
        }
        currentStrokes = currentStrokes.toMutableList().also { layersList ->
            layersList[selectedLayer] = strokes.toMutableList().also { it[strokeIndex] = stroke.copy(points = moved) }
        }
    }

    fun resetRig() {
        rigJoints = emptyList()
        rigParents = emptyList()
        rigSelected = -1
    }

    fun ensureWeightJoints() {
        val index = editStrokeIndex ?: return
        val stroke = currentStrokes.getOrNull(selectedLayer)?.getOrNull(index) ?: return
        if (stroke.points.size < 2) return
        if (weightJoints.size != 2) weightJoints = listOf(stroke.points.first(), stroke.points.last())
    }

    fun poseWeightJoint(point: Offset) {
        val index = editStrokeIndex ?: return
        val strokes = currentStrokes.getOrNull(selectedLayer) ?: return
        if (index !in strokes.indices) return
        val stroke = strokes[index]
        if (stroke.points.size < 2) return
        ensureWeightJoints()
        if (weightJoint !in 0..1 || weightJoints.size != 2) return
        val oldJoint = weightJoints[weightJoint]
        val delta = point - oldJoint
        val last = stroke.points.lastIndex.coerceAtLeast(1)
        val updated = stroke.points.mapIndexed { i, p ->
            val t = i.toFloat() / last.toFloat()
            val baseWeight = if (weightJoint == 0) 1f - t else t
            val dx = p.x - oldJoint.x
            val dy = p.y - oldJoint.y
            val dist = kotlin.math.sqrt(dx * dx + dy * dy)
            val falloff = (1f - dist / weightRadius).coerceIn(0f, 1f)
            val w = (baseWeight * falloff * weightStrength).coerceIn(0f, 1f)
            Offset(p.x + delta.x * w, p.y + delta.y * w)
        }
        currentStrokes = currentStrokes.toMutableList().also {
            it[selectedLayer] = strokes.toMutableList().also { list -> list[index] = stroke.copy(points = updated) }
        }
        weightJoints = weightJoints.toMutableList().also { it[weightJoint] = point }
    }

    fun sculptStroke(point: Offset, delta: Offset) {
        val index = editStrokeIndex ?: return
        val strokes = currentStrokes.getOrNull(selectedLayer) ?: return
        if (index !in strokes.indices) return
        val stroke = strokes[index]
        if (stroke.points.size < 2) return
        val r = sculptRadius.coerceAtLeast(1f)
        val updated = stroke.points.mapIndexed { i, p ->
            val dx = p.x - point.x; val dy = p.y - point.y
            val dist = kotlin.math.sqrt(dx * dx + dy * dy)
            val falloff = (1f - dist / r).coerceIn(0f, 1f)
            val w = falloff * falloff * sculptStrength
            when (sculptTool) {
                "Grab" -> Offset(p.x + delta.x * w, p.y + delta.y * w)
                "Push" -> { val len = dist.coerceAtLeast(0.001f); val d = delta.getDistance(); Offset(p.x + dx / len * d * w, p.y + dy / len * d * w) }
                "Pinch" -> Offset(p.x - dx * w * 0.12f, p.y - dy * w * 0.12f)
                "Smooth" -> { val a = stroke.points.getOrNull(i - 1) ?: p; val b = stroke.points.getOrNull(i + 1) ?: p; Offset(p.x + (a.x + b.x - 2f * p.x) * w * 0.35f, p.y + (a.y + b.y - 2f * p.y) * w * 0.35f) }
                "Thickness" -> p
                else -> p
            }
        }
        val newWidth = if (sculptTool == "Thickness") (stroke.width + delta.x * 0.35f * sculptStrength).coerceIn(1f, 160f) else stroke.width
        currentStrokes = currentStrokes.toMutableList().also { it[selectedLayer] = strokes.toMutableList().also { list -> list[index] = stroke.copy(points = updated, width = newWidth) } }
    }

    fun commitStroke() {
        if (tool == Tool.FILL && current.isNotEmpty()) {
            snapshot()
            val p = current.last()
            val bitmap = rasterLayers[selectedLayer]
            floodFill(bitmap, p.x.toInt().coerceIn(0, rasterWidth - 1), p.y.toInt().coerceIn(0, rasterHeight - 1), brush.toArgb())
            rasterLayers = rasterLayers.toMutableList().also { it[selectedLayer] = bitmap }
            saveRasterFrame()
            current = emptyList()
            currentPressures = emptyList()
            return
        }
        if (current.size > 1) {
            snapshot()
            val stabilized = if (stabilization <= 0f) current else {
                val out = ArrayList<Offset>()
                var last = current.first()
                out.add(last)
                current.drop(1).forEach { p ->
                    last = Offset(
                        last.x + (p.x - last.x) * (1f - stabilization),
                        last.y + (p.y - last.y) * (1f - stabilization)
                    )
                    out.add(last)
                }
                out
            }
            val points = when (tool) {
                Tool.LINE -> listOf(stabilized.first(), stabilized.last())
                Tool.RECTANGLE -> {
                    val a = stabilized.first()
                    val b = stabilized.last()
                    listOf(a, Offset(b.x, a.y), b, Offset(a.x, b.y), a)
                }
                Tool.ELLIPSE -> {
                    val a = stabilized.first()
                    val b = stabilized.last()
                    val cx = (a.x + b.x) / 2f
                    val cy = (a.y + b.y) / 2f
                    val rx = kotlin.math.abs(b.x - a.x) / 2f
                    val ry = kotlin.math.abs(b.y - a.y) / 2f
                    (0..48).map { i ->
                        val t = i * 2f * PI.toFloat() / 48f
                        Offset(cx + rx * cos(t), cy + ry * sin(t))
                    }
                }
                else -> {
                    if (spacing <= 0f || stabilized.size < 2) stabilized else {
                        val out = ArrayList<Offset>()
                        out.add(stabilized.first())
                        var carry = 0f
                        for (i in 1 until stabilized.size) {
                            val a = stabilized[i - 1]; val b = stabilized[i]
                            val dx = b.x - a.x; val dy = b.y - a.y
                            val dist = kotlin.math.sqrt(dx * dx + dy * dy)
                            carry += dist
                            if (carry >= max(1f, width * spacing)) { out.add(b); carry = 0f }
                        }
                        if (out.last() != stabilized.last()) out.add(stabilized.last())
                        out
                    }
                }
            }
            if (quickShape && (tool == Tool.BRUSH || tool == Tool.ERASER) && points.size >= 6) {
                // Quick Shape: when enabled, gently regularize nearly straight strokes.
                val a = points.first(); val b = points.last()
                val dx = b.x - a.x; val dy = b.y - a.y
                if (kotlin.math.abs(dx) > kotlin.math.abs(dy) * 6f || kotlin.math.abs(dy) > kotlin.math.abs(dx) * 6f) {
                    val snapped = if (kotlin.math.abs(dx) >= kotlin.math.abs(dy)) points.map { Offset(it.x, a.y + (b.y-a.y) * ((it.x-a.x)/(dx.takeIf { it != 0f } ?: 1f))) } else points.map { Offset(a.x + (b.x-a.x) * ((it.y-a.y)/(dy.takeIf { it != 0f } ?: 1f)), it.y) }
                    current = snapped
                }
            }
            if (tool == Tool.BRUSH || tool == Tool.ERASER) {
                val bitmap = rasterLayers[selectedLayer]
                val androidCanvas = AndroidCanvas(bitmap)
                val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG or AndroidPaint.DITHER_FLAG)
                paint.color = if (tool == Tool.ERASER) android.graphics.Color.TRANSPARENT else brush.toArgb()
                paint.alpha = (opacity.coerceIn(0f, 1f) * 255f).toInt()
                if (alphaLock && tool == Tool.BRUSH) paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
                paint.style = AndroidPaint.Style.STROKE
                paint.strokeWidth = when (brushType) {
                    "Pen" -> width
                    "Marker" -> width * 1.35f
                    "Airbrush" -> width * 1.8f
                    "Pencil" -> width * 0.82f
                    else -> width
                }.coerceAtLeast(1f)
                if (deepBrushEngine) {
                    paint.strokeMiter = 4f
                    paint.isSubpixelText = true
                }
                paint.strokeCap = AndroidPaint.Cap.ROUND
                paint.strokeJoin = AndroidPaint.Join.ROUND
                if (tool == Tool.ERASER) paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                if (tool == Tool.BRUSH && brushType == "Texture") {
                    TextureBrushEngine.draw(bitmap, points, brush.toArgb(), paint.strokeWidth, opacity, textureAmount)
                } else if (tool == Tool.BRUSH && brushType == "Advanced") {
                    AdvancedBrushEngine.draw(bitmap, points, currentPressures, brush.toArgb(), paint.strokeWidth, opacity, spacing, advancedScatter, advancedAngleJitter)
                } else {
                val path = android.graphics.Path()
                path.moveTo(points.first().x, points.first().y)
                points.drop(1).forEach { path.lineTo(it.x, it.y) }
                if (pressureEnabled && currentPressures.isNotEmpty() && brushType != "Marker") {
                    val pressures = currentPressures
                    for (i in 0 until minOf(points.size, pressures.size)) {
                        val p = pressures[i].coerceIn(0.05f, 1.25f)
                        val pressurePaint = AndroidPaint(paint)
                        pressurePaint.strokeWidth = width * (0.45f + p * 0.85f) * (1f - taper * (i.toFloat() / max(1, points.lastIndex)))
                        pressurePaint.alpha = (opacity.coerceIn(0f, 1f) * (0.45f + p * 0.55f) * 255f).toInt()
                        if (i == 0) androidCanvas.drawCircle(points[i].x, points[i].y, pressurePaint.strokeWidth / 2f, pressurePaint)
                        else androidCanvas.drawLine(points[i-1].x, points[i-1].y, points[i].x, points[i].y, pressurePaint)
                    }
                } else {
                    androidCanvas.drawPath(path, paint)
                }
                }
                rasterLayers = rasterLayers.toMutableList().also { it[selectedLayer] = bitmap }
                saveRasterFrame()
            }
            val stroke = Stroke(
                points,
                currentPressures,
                if (tool == Tool.ERASER) Color.Transparent else brush,
                width,
                opacity,
                closed = tool == Tool.RECTANGLE || tool == Tool.ELLIPSE,
                filled = shapeFilled && (tool == Tool.RECTANGLE || tool == Tool.ELLIPSE)
            )
            val updated = currentStrokes.toMutableList()
            if (tool != Tool.BRUSH && tool != Tool.ERASER) {
                updated[selectedLayer] = updated[selectedLayer] + stroke
            }

            if (symmetry && tool != Tool.SELECT) {
                val axisX = sizeOfCanvasFallback(symmetryAxis)
                val mirrored = points.map { p -> Offset(axisX - (p.x - axisX), p.y) }
                updated[selectedLayer] = updated[selectedLayer] + stroke.copy(points = mirrored, pressures = currentPressures)
            }
            if (radialSymmetry && tool != Tool.SELECT) {
                val center = Offset(rasterWidth / 2f, rasterHeight / 2f)
                val count = radialCount.coerceIn(2, 24)
                for (copyIndex in 1 until count) {
                    val angle = copyIndex * 360f / count.toFloat()
                    updated[selectedLayer] = updated[selectedLayer] + stroke.copy(points = transformPoints(points, center, 1f, angle), pressures = currentPressures)
                }
            }

            currentStrokes = updated
            saveFrame()
            if (tool == Tool.LINE || tool == Tool.RECTANGLE || tool == Tool.ELLIPSE) {
                editStrokeIndex = updated[selectedLayer].lastIndex
                editNodeIndex = -1
                editShapeLabel = when (tool) {
                    Tool.LINE -> "Edit Line"
                    Tool.ELLIPSE -> "Edit Arc"
                    else -> "Edit Shape"
                }
            }
        }
        current = emptyList()
        currentPressures = emptyList()
    }

    fun transformSelection(scaleFactor: Float, degrees: Float, delta: Offset) {
        if (selectedStrokeIds.isEmpty()) return
        val strokes = currentStrokes[selectedLayer]
        val selected = selectedStrokeIds
        val points = selected.flatMap { strokes[it].points }
        if (points.isEmpty()) return
        val center = Offset(points.map { it.x }.average().toFloat(), points.map { it.y }.average().toFloat())
        snapshot()
        val updated = strokes.mapIndexed { index, stroke ->
            if (index in selected) stroke.copy(points = transformPoints(stroke.points, center, scaleFactor, degrees, delta))
            else stroke
        }
        currentStrokes = currentStrokes.toMutableList().also { it[selectedLayer] = updated }
        saveFrame()
    }

    fun selectFromLasso() {
        val hits = currentStrokes.getOrNull(selectedLayer).orEmpty().mapIndexedNotNull { index, stroke ->
            if (stroke.points.any { pointInPolygon(it, selection) }) index else null
        }.toSet()
        selectedStrokeIds = hits
    }

    fun addFrame() {
        saveFrame()
        frameData = frameData.toMutableList().also { it.add(frameIndex + 1, Frame(layers.map { LayerFrame() })) }
        rasterFrames = rasterFrames.toMutableList().also { it.add(frameIndex + 1, rasterLayers.map { Bitmap.createBitmap(rasterWidth, rasterHeight, Bitmap.Config.ARGB_8888) }) }
        loadFrame(frameIndex + 1)
    }

    fun duplicateFrame() {
        saveFrame()
        frameData = frameData.toMutableList().also { it.add(frameIndex + 1, frameData[frameIndex]) }
        rasterFrames = rasterFrames.toMutableList().also { it.add(frameIndex + 1, rasterLayers.map { bitmap -> copyBitmap(bitmap) }) }
        loadFrame(frameIndex + 1)
    }

    fun deleteFrame() {
        if (frameData.size > 1) {
            frameData = frameData.toMutableList().also { it.removeAt(frameIndex) }
            rasterFrames = rasterFrames.toMutableList().also { it.removeAt(frameIndex) }
            loadFrame(frameIndex.coerceAtMost(frameData.lastIndex))
        }
    }

    fun setHold(value: Int) {
        saveFrame()
        val f = frameData[frameIndex]
        frameData = frameData.toMutableList().also {
            it[frameIndex] = f.copy(layers = f.layers.map { layer -> layer.copy(hold = value.coerceIn(1, 12)) })
        }
    }

    fun addLayer() {
        saveFrame()
        layers = layers + ArtLayer("Layer " + (layers.size + 1))
        currentStrokes = currentStrokes + emptyList()
        rasterLayers = rasterLayers + Bitmap.createBitmap(rasterWidth, rasterHeight, Bitmap.Config.ARGB_8888)
        frameData = frameData.map { it.copy(layers = it.layers + LayerFrame()) }
        rasterFrames = rasterFrames.map { frame -> frame + Bitmap.createBitmap(rasterWidth, rasterHeight, Bitmap.Config.ARGB_8888) }
        selectedLayer = layers.lastIndex
    }

    fun deleteLayer() {
        if (layers.size > 1) {
            saveFrame()
            layers = layers.toMutableList().also { it.removeAt(selectedLayer) }
            currentStrokes = currentStrokes.toMutableList().also { it.removeAt(selectedLayer) }
            frameData = frameData.map { f -> f.copy(layers = f.layers.filterIndexed { i, _ -> i != selectedLayer }) }
            rasterLayers = rasterLayers.filterIndexed { i, _ -> i != selectedLayer }
            rasterFrames = rasterFrames.map { frame -> frame.filterIndexed { i, _ -> i != selectedLayer } }
            selectedLayer = max(0, selectedLayer - 1)
        }
    }

    fun toggleLayerVisibility(index: Int) {
        layers = layers.toMutableList().also { it[index] = it[index].copy(visible = !it[index].visible) }
    }

    LaunchedEffect(playing, fps, frameData.size, pingPong) {
        while (playing) {
            delay(1000L / fps)
            val next = frameIndex + playDirection
            if (next >= frameData.size || next < 0) {
                if (pingPong && frameData.size > 1) {
                    playDirection = -playDirection
                    loadFrame((frameIndex + playDirection).coerceIn(0, frameData.lastIndex))
                } else {
                    playDirection = 1
                    loadFrame(0)
                }
            } else loadFrame(next)
        }
    }

    fun renderFrameBitmap(index: Int): Bitmap {
        val merged = Bitmap.createBitmap(rasterWidth, rasterHeight, Bitmap.Config.ARGB_8888)
        val canvas = AndroidCanvas(merged)
        canvas.drawColor(android.graphics.Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)
        rasterFrames.getOrNull(index)?.forEachIndexed { i, bitmap ->
            if (layers.getOrNull(i)?.visible == true) {
                val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG)
                paint.alpha = (layers[i].opacity.coerceIn(0f, 1f) * 255f).toInt()
                canvas.drawBitmap(bitmap, 0f, 0f, paint)
            }
        }
        frameData.getOrNull(index)?.layers?.forEachIndexed { i, layer ->
            if (layers.getOrNull(i)?.visible != true) return@forEachIndexed
            layer.strokes.forEach { stroke ->
                if (stroke.points.isEmpty()) return@forEach
                val path = android.graphics.Path().apply {
                    moveTo(stroke.points.first().x, stroke.points.first().y)
                    stroke.points.drop(1).forEach { lineTo(it.x, it.y) }
                    if (stroke.closed) close()
                }
                val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG)
                paint.color = stroke.color.copy(alpha = stroke.opacity * layers[i].opacity).toArgb()
                paint.style = if (stroke.filled) AndroidPaint.Style.FILL else AndroidPaint.Style.STROKE
                paint.strokeWidth = stroke.width
                paint.strokeCap = AndroidPaint.Cap.ROUND
                canvas.drawPath(path, paint)
            }
        }
        return merged
    }

    fun exportGif(uri: Uri) {
        try {
            context.contentResolver.openOutputStream(uri)?.use { output ->
                val encoder = GifEncoder(output, rasterWidth, rasterHeight, 0)
                val options = ImageOptions()
                options.setDelay((1000L / fps).coerceAtLeast(1L), TimeUnit.MILLISECONDS)
                frameData.indices.forEach { index ->
                    val bitmap = renderFrameBitmap(index)
                    val pixels = IntArray(rasterWidth * rasterHeight)
                    bitmap.getPixels(pixels, 0, rasterWidth, 0, 0, rasterWidth, rasterHeight)
                    val data = Array(rasterWidth) { x -> IntArray(rasterHeight) { y -> pixels[y * rasterWidth + x] } }
                    encoder.addImage(data, options)
                    bitmap.recycle()
                }
                encoder.finishEncoding()
            }
            exportStatus = "GIF exported"
        } catch (e: Exception) {
            exportStatus = "GIF export failed: " + (e.message ?: "unknown error")
        }
    }

    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri -> if (uri != null) saveProject(uri) }
    val loadLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) loadProject(uri) }
    val gifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/gif")) { uri -> if (uri != null) exportGif(uri) }

    val colors = listOf(
        Color.Black, Color.White, Color.Red, Color(0xFFFF9800), Color.Yellow,
        Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color(0xFF795548)
    )

    fun openColorPicker() {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(brush.toArgb(), hsv)
        hue = hsv[0]; saturation = hsv[1]; value = hsv[2]; colorAlpha = brush.alpha
        hexColor = String.format("#%08X", brush.toArgb())
        showColorPicker = true
    }

    fun applyPickerColor() {
        brush = Color(android.graphics.Color.HSVToColor(
            (colorAlpha.coerceIn(0f, 1f) * 255f).toInt(),
            floatArrayOf(hue, saturation, value)
        ))
        recentColors = (listOf(brush) + recentColors.filter { it != brush }).take(12)
        showColorPicker = false
    }

    fun sampleColor(point: Offset) {
        val x = point.x.toInt().coerceIn(0, rasterWidth - 1)
        val y = point.y.toInt().coerceIn(0, rasterHeight - 1)
        for (i in rasterLayers.lastIndex downTo 0) {
            if (layers.getOrNull(i)?.visible != true) continue
            val pixel = rasterLayers[i].getPixel(x, y)
            if ((pixel ushr 24) != 0) { brush = Color(pixel); return }
        }
        currentStrokes.getOrNull(selectedLayer).orEmpty().minByOrNull { s ->
            s.points.minOfOrNull { p ->
                val dx = p.x - point.x; val dy = p.y - point.y
                dx * dx + dy * dy
            } ?: Float.MAX_VALUE
        }?.let { brush = it.color }
    }

    if (showColorPicker) {
        AlertDialog(
            onDismissRequest = { showColorPicker = false },
            title = { Text("Color Picker") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.fillMaxWidth().height(50.dp).background(brush).border(1.dp, Color.Gray))
                    Text("Hue " + hue.toInt() + "°"); Slider(hue, { hue = it }, valueRange = 0f..360f)
                    Text("Saturation " + (saturation * 100).toInt() + "%"); Slider(saturation, { saturation = it }, valueRange = 0f..1f)
                    Text("Value " + (value * 100).toInt() + "%"); Slider(value, { value = it }, valueRange = 0f..1f)
                    Text("Alpha " + (colorAlpha * 100).toInt() + "%"); Slider(colorAlpha, { colorAlpha = it }, valueRange = 0f..1f)
                    OutlinedTextField(
                        value = hexColor,
                        onValueChange = { input ->
                            hexColor = input
                            runCatching {
                                val parsed = android.graphics.Color.parseColor(input)
                                val hsv = FloatArray(3)
                                android.graphics.Color.colorToHSV(parsed, hsv)
                                hue = hsv[0]; saturation = hsv[1]; value = hsv[2]; colorAlpha = (parsed ushr 24) / 255f
                            }
                        },
                        label = { Text("HEX / ARGB") },
                        singleLine = true
                    )
                    Text("Recent colors")
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        recentColors.forEach { color ->
                            Box(Modifier.size(32.dp).clip(CircleShape).background(color).border(1.dp, Color.Gray, CircleShape).clickable {
                                brush = color
                                val hsv = FloatArray(3)
                                android.graphics.Color.colorToHSV(color.toArgb(), hsv)
                                hue = hsv[0]; saturation = hsv[1]; value = hsv[2]; colorAlpha = color.alpha
                                hexColor = String.format("#%08X", color.toArgb())
                            })
                        }
                    }
                }
            },
            confirmButton = { Button(onClick = ::applyPickerColor) { Text("Apply") } },
            dismissButton = { TextButton(onClick = { showColorPicker = false }) { Text("Cancel") } }
        )
    }


    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("MotionCanvas") },
            actions = {
                TextButton(onClick = { saveLauncher.launch("MotionCanvas_Project.motioncanvas") }) { Text("Save Project") }
                TextButton(onClick = { loadLauncher.launch(arrayOf("application/zip", "application/octet-stream")) }) { Text("Load Project") }
                TextButton(onClick = { exportCurrentPng() }) { Text("Export PNG") }
                TextButton(onClick = { gifLauncher.launch("MotionCanvas.gif") }) { Text("Export GIF") }
                TextButton(enabled = undo.isNotEmpty(), onClick = {
                    val previous = undo.last()
                    redo = (redo + EditorSnapshot(currentStrokes, rasterLayers.map { copyBitmap(it) })).takeLast(20)
                    undo = undo.dropLast(1)
                    restoreSnapshot(previous)
                }) { Text("Undo") }
                TextButton(enabled = redo.isNotEmpty(), onClick = {
                    val next = redo.last()
                    undo = (undo + EditorSnapshot(currentStrokes, rasterLayers.map { copyBitmap(it) })).takeLast(20)
                    redo = redo.dropLast(1)
                    restoreSnapshot(next)
                }) { Text("Redo") }
            }
        )

        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(5.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            FilterChip(tool == Tool.BRUSH, { tool = Tool.BRUSH }, label = { Text("Brush") })
            FilterChip(tool == Tool.ERASER, { tool = Tool.ERASER }, label = { Text("Eraser") })
            FilterChip(tool == Tool.LINE, { tool = Tool.LINE }, label = { Text("Line") })
            FilterChip(tool == Tool.RECTANGLE, { tool = Tool.RECTANGLE }, label = { Text("Rect") })
            FilterChip(tool == Tool.ELLIPSE, { tool = Tool.ELLIPSE }, label = { Text("Ellipse") })
            FilterChip(tool == Tool.SELECT, { tool = Tool.SELECT }, label = { Text("Lasso") })
            FilterChip(tool == Tool.FILL, { tool = Tool.FILL }, label = { Text("Fill") })
            FilterChip(tool == Tool.EYEDROPPER, { tool = Tool.EYEDROPPER }, label = { Text("Eyedropper") })
            Button(onClick = ::openColorPicker) { Text("Color Picker") }
            FilterChip(brushType == "Pencil", { brushType = "Pencil" }, label = { Text("Pencil") })
            FilterChip(brushType == "Pen", { brushType = "Pen" }, label = { Text("Pen") })
            FilterChip(brushType == "Marker", { brushType = "Marker" }, label = { Text("Marker") })
            FilterChip(brushType == "Airbrush", { brushType = "Airbrush" }, label = { Text("Airbrush") })
            FilterChip(brushType == "Texture", { brushType = "Texture" }, label = { Text("Texture") })
            FilterChip(brushType == "Advanced", { brushType = "Advanced" }, label = { Text("Advanced") })
            FilterChip(onionSkin, { onionSkin = !onionSkin }, label = { Text("Onion") })
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Size " + width.toInt(), Modifier.width(70.dp))
            Slider(width, { width = it }, valueRange = 1f..80f)
            Text("Opacity " + (opacity * 100).toInt() + "%", Modifier.width(95.dp))
            Slider(opacity, { opacity = it }, valueRange = 0.05f..1f)
        }

        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            colors.forEach { color ->
                Box(
                    Modifier.size(30.dp).clip(CircleShape).background(color)
                        .border(if (brush == color) 3.dp else 1.dp, Color.Gray, CircleShape)
                        .clickable { brush = color }
                )
            }
            FilterChip(shapeFilled, { shapeFilled = !shapeFilled }, label = { Text("Shape Fill") })
            FilterChip(symmetry, { symmetry = !symmetry }, label = { Text("Mirror") })
            FilterChip(radialSymmetry, { radialSymmetry = !radialSymmetry }, label = { Text("Radial") })
            FilterChip(alphaLock, { alphaLock = !alphaLock }, label = { Text("Alpha Lock") })
            FilterChip(clippingMask, { if (selectedLayer > 0) { clippingMask = !clippingMask; val updated = layers.toMutableList(); updated[selectedLayer] = updated[selectedLayer].copy(clipToBelow = clippingMask); layers = updated } }, enabled = selectedLayer > 0, label = { Text("Clip Below") })
            FilterChip(showGrid, { showGrid = !showGrid }, label = { Text("Grid") })
        }

        if (showGrid) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                FilterChip(gridType == "2D", { gridType = "2D" }, label = { Text("2D") })
                FilterChip(gridType == "ISO", { gridType = "ISO" }, label = { Text("Isometric") })
                FilterChip(gridType == "PERSPECTIVE", { gridType = "PERSPECTIVE" }, label = { Text("Perspective") })
                Text("Grid " + gridSpacing.toInt())
                Slider(gridSpacing, { gridSpacing = it }, valueRange = 40f..240f, modifier = Modifier.width(130.dp))
                if (gridType == "PERSPECTIVE") listOf(1, 2, 3).forEach { n ->
                    FilterChip(perspectivePoints == n, { perspectivePoints = n }, label = { Text(n.toString() + "P") })
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Stabilizer", Modifier.width(75.dp))
            Slider(stabilization, { stabilization = it }, valueRange = 0f..0.85f)
            Text((stabilization * 100).toInt().toString() + "%")
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Streamline", Modifier.width(75.dp))
            Slider(streamline, { streamline = it }, valueRange = 0f..0.9f)
            Text((streamline * 100).toInt().toString() + "%")
        }
        if (brushType == "Texture") {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Texture", Modifier.width(70.dp))
                Slider(textureAmount, { textureAmount = it }, valueRange = 0f..1f)
                Text((textureAmount * 100).toInt().toString() + "%")
            }
        }
        if (brushType == "Advanced") {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Scatter", Modifier.width(70.dp)); Slider(advancedScatter, { advancedScatter = it }, valueRange = 0f..1f); Text((advancedScatter * 100).toInt().toString() + "%")
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Angle", Modifier.width(70.dp)); Slider(advancedAngleJitter, { advancedAngleJitter = it }, valueRange = 0f..1f); Text((advancedAngleJitter * 100).toInt().toString() + "%")
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(deepBrushEngine, { deepBrushEngine = !deepBrushEngine }, label = { Text("Deep Brush") })
            FilterChip(quickShape, { quickShape = !quickShape }, label = { Text("Quick Shape") })
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(weightPaintMode, { weightPaintMode = !weightPaintMode; if (weightPaintMode) ensureWeightJoints() }, label = { Text("Weight Paint") })
            if (weightPaintMode) Text("Pose selected stroke", modifier = Modifier.padding(top = 10.dp))
        }
        if (weightPaintMode) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Weight Radius", Modifier.width(95.dp))
                Slider(weightRadius, { weightRadius = it }, valueRange = 40f..420f)
                Text(weightRadius.toInt().toString())
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Strength", Modifier.width(75.dp))
                Slider(weightStrength, { weightStrength = it }, valueRange = 0.1f..1f)
                Text((weightStrength * 100).toInt().toString() + "%")
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(sculptMode, { sculptMode = !sculptMode }, label = { Text("Sculpt Mode") })
            if (sculptMode) listOf("Grab", "Push", "Smooth", "Pinch", "Thickness").forEach { mode ->
                FilterChip(sculptTool == mode, { sculptTool = mode }, label = { Text(mode) })
            }
        }
        if (sculptMode) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Sculpt Radius", Modifier.width(95.dp)); Slider(sculptRadius, { sculptRadius = it }, valueRange = 30f..360f); Text(sculptRadius.toInt().toString())
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Strength", Modifier.width(75.dp)); Slider(sculptStrength, { sculptStrength = it }, valueRange = 0.1f..1f); Text((sculptStrength * 100).toInt().toString() + "%")
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Spacing", Modifier.width(75.dp))
            Slider(spacing, { spacing = it }, valueRange = 0.05f..0.6f)
            Text((spacing * 100).toInt().toString() + "%")
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Taper", Modifier.width(75.dp))
            Slider(taper, { taper = it }, valueRange = 0f..0.8f)
            Text((taper * 100).toInt().toString() + "%")
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.End) {
            FilterChip(pressureEnabled, { pressureEnabled = !pressureEnabled }, label = { Text("Pressure") })
        }

        Row(Modifier.weight(1f).fillMaxWidth()) {
            Box(
                Modifier.weight(1f).fillMaxHeight().background(Color.White)
                    .pointerInput(Unit) {
                        detectTransformGestures { _, panChange, zoomChange, rotationChange ->
                            scale = (scale * zoomChange).coerceIn(0.25f, 8f)
                            pan += panChange
                            rotation += rotationChange
                        }
                    }
            ) {
                Box(Modifier.fillMaxSize()) {
                Canvas(
                    Modifier.fillMaxSize()
                        .pointerInput(editStrokeIndex) {
                            detectTapGestures { tap ->
                                if (editStrokeIndex != null && nearestEditNode(screenToArt(tap)) < 0) {
                                    finishShapeEditing()
                                }
                            }
                        }
                        .onSizeChanged { canvasSize = androidx.compose.ui.geometry.Size(it.width.toFloat(), it.height.toFloat()) }.pointerInput(tool, selectedLayer, width, opacity, brush, stabilization, streamline, pressureEnabled, spacing, taper, quickShape, editStrokeIndex) {
                        detectDragGestures(
                            onDragStart = { start ->
                                val artStart = screenToArt(start)
                                if (editStrokeIndex != null) {
                                    if (rigMode) {
                                            var nearest = -1
                                            var bd = 42f * 42f
                                            rigJoints.forEachIndexed { i, p -> val d = (p - artStart).let { it.x*it.x + it.y*it.y }; if (d < bd) { bd=d; nearest=i } }
                                            rigSelected = nearest
                                        } else if (nodeEditorMode && bezierHandleMode) {
                                        val nodes = currentStrokes.getOrNull(selectedLayer)?.getOrNull(editStrokeIndex!!)?.points.orEmpty()
                                        var bestNode = -1
                                        var bestDist = Float.MAX_VALUE
                                        nodes.forEachIndexed { n, p ->
                                            val dx = p.x - artStart.x; val dy = p.y - artStart.y
                                            val d = dx * dx + dy * dy
                                            if (d < bestDist) { bestDist = d; bestNode = n }
                                        }
                                        if (bestNode >= 0 && bestDist <= 44f * 44f) {
                                            snapshot()
                                            editNodeIndex = bestNode
                                            activeHandle = bestNode
                                            activeHandleSide = "out"
                                        } else {
                                            activeHandle = -1
                                        }
                                    }
                                    ensureWeightJoints()
                                    if (sculptMode) {
                                        if (!sculptSnapshotTaken) { snapshot(); sculptSnapshotTaken = true }
                                        editNodeIndex = -1
                                    } else if (weightPaintMode) {
                                        weightJoint = weightJoints.indices.minByOrNull { i ->
                                            val dx = weightJoints[i].x - artStart.x
                                            val dy = weightJoints[i].y - artStart.y
                                            dx * dx + dy * dy
                                        } ?: -1
                                        if (weightJoint >= 0) snapshot()
                                    } else {
                                        editNodeIndex = nearestEditNode(artStart)
                                        if (editNodeIndex >= 0) snapshot()
                                    }
                                } else {
                                    current = listOf(artStart)
                                    if (tool == Tool.SELECT) selection = listOf(artStart)
                                }
                            },
                            onDrag = { change, _ ->
                                val artPoint = screenToArt(change.position)
                                if (rigMode && rigSelected >= 0) {
                                    moveRigJoint(rigSelected, artPoint)
                                } else if (editStrokeIndex != null && nodeEditorMode && bezierHandleMode && activeHandle >= 0) {
                                    updateBezierHandle(artPoint, activeHandleSide)
                                } else if (editStrokeIndex != null && sculptMode) {
                                    sculptStroke(artPoint, change.position - change.previousPosition)
                                } else if (editStrokeIndex != null && weightPaintMode && weightJoint >= 0) {
                                    poseWeightJoint(artPoint)
                                } else if (editStrokeIndex != null && editNodeIndex >= 0) {
                                    val strokeIndex = editStrokeIndex!!
                                    val strokes = currentStrokes[selectedLayer]
                                    if (strokeIndex in strokes.indices) {
                                        val updatedStroke = strokes[strokeIndex].copy(
                                            points = strokes[strokeIndex].points.toMutableList().also { it[editNodeIndex] = artPoint }
                                        )
                                        currentStrokes = currentStrokes.toMutableList().also {
                                            it[selectedLayer] = strokes.toMutableList().also { list -> list[strokeIndex] = updatedStroke }
                                        }
                                    }
                                } else {
                                    current = current + artPoint
                                    if (tool == Tool.SELECT) selection = selection + artPoint
                                }
                            },
                            onDragEnd = {
                                if (editStrokeIndex != null) {
                                    saveFrame()
                                    activeHandle = -1
                                    editNodeIndex = -1
                                    weightJoint = -1
                                    sculptSnapshotTaken = false
                                } else if (tool == Tool.SELECT) selectFromLasso() else commitStroke()
                            },
                            onDragCancel = {
                                current = emptyList()
                                selection = emptyList()
                                editNodeIndex = -1
                            }
                        )
                    }
                ) {
                    val baseScale = artScale()
                    translate(left = canvasSize.width / 2f + pan.x, top = canvasSize.height / 2f + pan.y) {
                        rotate(rotation) {
                            scale(baseScale * scale, baseScale * scale, Offset.Zero) {
                                translate(left = -rasterWidth / 2f, top = -rasterHeight / 2f) {
                    if (showGrid) {
                        val step = gridSpacing.coerceAtLeast(20f)
                        if (gridType == "2D") {
                            var gx = 0f
                            while (gx <= rasterWidth) { drawLine(Color.Gray.copy(alpha = 0.22f), Offset(gx, 0f), Offset(gx, rasterHeight.toFloat()), 1f); gx += step }
                            var gy = 0f
                            while (gy <= rasterHeight) { drawLine(Color.Gray.copy(alpha = 0.22f), Offset(0f, gy), Offset(rasterWidth.toFloat(), gy), 1f); gy += step }
                        } else if (gridType == "ISO") {
                            var gx = -rasterHeight.toFloat()
                            while (gx <= rasterWidth) { drawLine(Color.Gray.copy(alpha = 0.18f), Offset(gx, 0f), Offset(gx + rasterHeight, rasterHeight.toFloat()), 1f); gx += step }
                            var gx2 = 0f
                            while (gx2 <= rasterWidth + rasterHeight) { drawLine(Color.Gray.copy(alpha = 0.18f), Offset(gx2, 0f), Offset(gx2 - rasterHeight, rasterHeight.toFloat()), 1f); gx2 += step }
                        } else {
                            val center = Offset(rasterWidth / 2f, rasterHeight / 2f)
                            val vps = when (perspectivePoints) {
                                1 -> listOf(Offset(rasterWidth / 2f, -500f))
                                2 -> listOf(Offset(-500f, rasterHeight / 2f), Offset(rasterWidth + 500f, rasterHeight / 2f))
                                else -> listOf(Offset(-500f, -400f), Offset(rasterWidth + 500f, -400f), Offset(rasterWidth / 2f, rasterHeight + 1500f))
                            }
                            vps.forEach { vp -> drawLine(Color.Gray.copy(alpha = 0.28f), center, vp, 1.5f) }
                        }
                    }

                    if (onionSkin && frameIndex > 0) {
                        frameData[frameIndex - 1].layers.forEach { layer ->
                            layer.strokes.forEach { s -> drawStroke(this, s, Color.Red.copy(alpha = 0.14f)) }
                        }
                    }

                    val composed = composeVisibleLayers()
                    drawImage(composed.asImageBitmap())
                    composed.recycle()

                    if (weightPaintMode && editStrokeIndex != null) {
                        ensureWeightJoints()
                        weightJoints.forEachIndexed { i, joint ->
                            drawCircle(if (i == weightJoint) Color.Yellow else Color.Cyan, radius = 14f, center = joint)
                            drawCircle(Color.DarkGray, radius = 5f, center = joint)
                        }
                    }

                    if (rigMode && rigJoints.isNotEmpty()) {
                        rigJoints.forEachIndexed { i, p ->
                            val parent = rigParents.getOrNull(i) ?: -1
                            if (parent in rigJoints.indices) drawLine(Color.Cyan, rigJoints[parent], p, 4f)
                            drawCircle(if (i == rigSelected) Color.Yellow else Color.Cyan, 14f, p)
                            drawCircle(Color.DarkGray, 5f, p)
                        }
                    }

                    if (nodeEditorMode && bezierHandleMode && editStrokeIndex != null) {
                        val s = currentStrokes.getOrNull(selectedLayer)?.getOrNull(editStrokeIndex!!)
                        if (s != null) {
                            val b = ensureBezierHandles(s)
                            b.points.forEachIndexed { n, p ->
                                val ih = b.inHandles[n]; val oh = b.outHandles[n]
                                if (ih != Offset.Zero) { drawLine(Color.Magenta.copy(alpha = 0.55f), p, p + ih, 2f); drawCircle(Color.Magenta, 7f, p + ih) }
                                if (oh != Offset.Zero) { drawLine(Color.Magenta.copy(alpha = 0.55f), p, p + oh, 2f); drawCircle(Color.Magenta, 7f, p + oh) }
                            }
                        }
                    }

                    if (nodeEditorMode && editStrokeIndex != null) {
                        val idx = editStrokeIndex!!
                        val nodes = currentStrokes.getOrNull(selectedLayer)?.getOrNull(idx)?.points.orEmpty()
                        nodes.forEachIndexed { n, p ->
                            drawCircle(if (n == editNodeIndex) Color.Yellow else Color.Cyan, radius = 11f, center = p)
                            drawCircle(Color.DarkGray, radius = 4f, center = p)
                        }
                    }

                    currentStrokes.forEachIndexed { index, strokes ->
                        if (layers.getOrNull(index)?.visible == true) {
                            strokes.forEachIndexed { strokeIndex, s ->
                                drawStroke(this, s, s.color.copy(alpha = s.opacity * layers[index].opacity))
                                if (index == selectedLayer && strokeIndex in selectedStrokeIds) {
                                    drawStroke(this, s, Color.Blue.copy(alpha = 0.35f), outline = true)
                                }
                            }
                        }
                    }

                    if (tool == Tool.SELECT && selection.isNotEmpty()) {
                        val path = Path().apply {
                            moveTo(selection.first().x, selection.first().y)
                            selection.drop(1).forEach { lineTo(it.x, it.y) }
                        }
                        drawPath(path, Color.Blue.copy(alpha = 0.35f),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
                    }

                    if (current.isNotEmpty() && tool != Tool.SELECT) {
                        val preview = when (tool) {
                            Tool.LINE -> listOf(current.first(), current.last())
                            Tool.RECTANGLE -> {
                                val a = current.first(); val b = current.last()
                                listOf(a, Offset(b.x, a.y), b, Offset(a.x, b.y), a)
                            }
                            else -> current
                        }
                        drawStroke(
                            this,
                            Stroke(preview, emptyList(), if (tool == Tool.ERASER) Color.White else brush, width, opacity,
                                closed = tool == Tool.RECTANGLE, filled = shapeFilled && tool == Tool.RECTANGLE)
                        )
                    }
                                }
                            }
                        }
                    }
                }
                if (editStrokeIndex != null) {
                    Row(
                        Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Button(onClick = { weightPaintMode = !weightPaintMode; if (weightPaintMode) ensureWeightJoints() }) {
                            Text(if (weightPaintMode) "Weight Pose" else editShapeLabel)
                        }
                        if (weightPaintMode) Text("Drag joints to pose", modifier = Modifier.padding(top = 12.dp))
                            Button(onClick = { rigMode = !rigMode }) { Text(if (rigMode) "Rig On" else "Rig") }
                            if (rigMode) {
                                Button(onClick = { resetRig() }) { Text("Reset Rig") }
                                Button(onClick = { val p = currentStrokes.getOrNull(selectedLayer)?.getOrNull(editStrokeIndex ?: -1)?.points?.firstOrNull(); if (p != null) addRigJoint(p) }) { Text("Add Joint") }
                            }
                        if (!weightPaintMode && !sculptMode) {
                            Button(onClick = { nodeEditorMode = !nodeEditorMode }) { Text(if (nodeEditorMode) "Nodes On" else "Nodes") }
                            Button(onClick = { addEditNode() }, enabled = nodeEditorMode) { Text("Add") }
                            Button(onClick = { deleteEditNode() }, enabled = nodeEditorMode && editNodeIndex >= 0) { Text("Delete") }
                            Button(onClick = { respaceEditNodes() }, enabled = nodeEditorMode) { Text("Re-space") }
                            Button(onClick = { bezierHandleMode = !bezierHandleMode }, enabled = nodeEditorMode) { Text(if (bezierHandleMode) "Bezier On" else "Bezier") }
                        }
                    }
                }
                }
            }

            Surface(Modifier.width(132.dp).fillMaxHeight(), tonalElevation = 3.dp) {
                Column(Modifier.fillMaxSize()) {
                    Text("Layers", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(10.dp))
            Text("Clip = alpha mask • B = blend", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 10.dp))
                    LazyColumn(Modifier.weight(1f)) {
                        itemsIndexed(layers) { index, layer ->
                            Surface(
                                Modifier.fillMaxWidth().clickable { selectedLayer = index },
                                color = if (index == selectedLayer) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                            ) {
                                Row(Modifier.fillMaxWidth().padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(if (layer.visible) "◉" else "○", Modifier.clickable { toggleLayerVisibility(index) }.padding(4.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(layer.name)
                                        Text((if (layer.clipToBelow) "Clip • " else "") + layer.blendMode.name,
                                            style = MaterialTheme.typography.labelSmall)
                                    }
                                    Button(onClick = {
                                        layers = layers.toMutableList().also {
                                            it[index] = it[index].copy(clipToBelow = !it[index].clipToBelow)
                                        }
                                    }, enabled = index > 0) { Text(if (layer.clipToBelow) "Unclip" else "Clip") }
                                    Button(onClick = {
                                        val modes = LayerBlendMode.entries
                                        val next = modes[(modes.indexOf(layer.blendMode) + 1) % modes.size]
                                        layers = layers.toMutableList().also {
                                            it[index] = it[index].copy(blendMode = next)
                                        }
                                    }) { Text("B") }
                                }
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        Button(onClick = ::addLayer, modifier = Modifier.padding(3.dp)) { Text("+") }
                        Button(onClick = ::deleteLayer, enabled = layers.size > 1, modifier = Modifier.padding(3.dp)) { Text("−") }
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 5.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Button(onClick = { transformSelection(1f, 0f, Offset(-10f, 0f)) }, enabled = selectedStrokeIds.isNotEmpty()) { Text("←") }
            Button(onClick = { transformSelection(1f, 0f, Offset(10f, 0f)) }, enabled = selectedStrokeIds.isNotEmpty()) { Text("→") }
            Button(onClick = { transformSelection(0.9f, 0f, Offset.Zero) }, enabled = selectedStrokeIds.isNotEmpty()) { Text("Scale −") }
            Button(onClick = { transformSelection(1.1f, 0f, Offset.Zero) }, enabled = selectedStrokeIds.isNotEmpty()) { Text("Scale +") }
            Button(onClick = { transformSelection(1f, -15f, Offset.Zero) }, enabled = selectedStrokeIds.isNotEmpty()) { Text("↶") }
            Button(onClick = { transformSelection(1f, 15f, Offset.Zero) }, enabled = selectedStrokeIds.isNotEmpty()) { Text("↷") }
            Button(onClick = { selectedStrokeIds = emptySet(); selection = emptyList() }) { Text("Clear") }
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { playing = !playing }) { Text(if (playing) "Pause" else "Play") }
            FilterChip(pingPong, { pingPong = !pingPong }, label = { Text("Ping-Pong") })
            Text("FPS " + fps, Modifier.padding(horizontal = 4.dp))
            listOf(8, 12, 24).forEach { rate -> Button(onClick = { fps = rate }) { Text(rate.toString()) } }
            Button(onClick = { setHold((frameData[frameIndex].layers.firstOrNull()?.hold ?: 1) + 1) }) { Text("Hold+") }
            Button(onClick = { setHold(1) }) { Text("Hold 1") }
        }

        LazyRow(Modifier.fillMaxWidth().padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            itemsIndexed(frameData) { index, frame ->
                Surface(
                    Modifier.width(64.dp).height(58.dp).clickable { saveFrame(); loadFrame(index) },
                    tonalElevation = if (index == frameIndex) 6.dp else 1.dp,
                    color = if (index == frameIndex) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Text("F" + (index + 1))
                        Text(if (frame.layers.any { it.strokes.isNotEmpty() }) "●" else "○")
                        Text("hold " + (frame.layers.firstOrNull()?.hold ?: 1))
                    }
                }
            }
        }

        if (exportStatus.isNotEmpty()) Text(exportStatus, modifier = Modifier.padding(horizontal = 8.dp))

        Row(Modifier.fillMaxWidth().padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Button(onClick = ::addFrame) { Text("+ Frame") }
            Button(onClick = ::duplicateFrame) { Text("Duplicate") }
            Button(onClick = ::deleteFrame, enabled = frameData.size > 1) { Text("Delete") }
        }
    }
}

private fun sizeOfCanvasFallback(axis: Float): Float = 500f * axis

private fun drawStroke(
    scope: androidx.compose.ui.graphics.drawscope.DrawScope,
    stroke: Stroke,
    color: Color,
    outline: Boolean = false
) {
    if (stroke.points.isEmpty()) return
    val path = Path().apply {
        moveTo(stroke.points[0].x, stroke.points[0].y)
        if (stroke.inHandles.size == stroke.points.size && stroke.outHandles.size == stroke.points.size) {
            for (i in 0 until stroke.points.lastIndex) {
                val a = stroke.points[i]
                val b = stroke.points[i + 1]
                val c1 = Offset(a.x + stroke.outHandles[i].x, a.y + stroke.outHandles[i].y)
                val c2 = Offset(b.x + stroke.inHandles[i + 1].x, b.y + stroke.inHandles[i + 1].y)
                path.cubicTo(c1.x, c1.y, c2.x, c2.y, b.x, b.y)
            }
        } else stroke.points.drop(1).forEach { lineTo(it.x, it.y) }
        if (stroke.closed) close()
    }
    if (stroke.filled && !outline) {
        scope.drawPath(path, color, style = androidx.compose.ui.graphics.drawscope.Fill)
    } else {
        scope.drawPath(
            path,
            color,
            style = androidx.compose.ui.graphics.drawscope.Stroke(stroke.width, cap = StrokeCap.Round)
        )
    }
}
