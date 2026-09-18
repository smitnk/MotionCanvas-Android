package com.smitnk.motioncanvas

import android.os.Bundle
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
import androidx.compose.ui.input.pointer.awaitEachGesture
import androidx.compose.ui.input.pointer.awaitFirstDown
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

data class Stroke(
    val points: List<Offset>,
    val pressures: List<Float> = emptyList(),
    val color: Color,
    val width: Float,
    val opacity: Float = 1f,
    val closed: Boolean = false,
    val filled: Boolean = false
)
data class ArtLayer(val name: String, val visible: Boolean = true, val opacity: Float = 1f)
data class LayerFrame(val strokes: List<Stroke> = emptyList(), val hold: Int = 1)
data class Frame(val layers: List<LayerFrame> = emptyList())
enum class Tool { BRUSH, ERASER, LINE, RECTANGLE, ELLIPSE, SELECT, FILL }

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
    var brushType by remember { mutableStateOf("Pencil") }
    var pressureEnabled by remember { mutableStateOf(true) }
    var width by remember { mutableFloatStateOf(10f) }
    var opacity by remember { mutableFloatStateOf(1f) }
    var stabilization by remember { mutableFloatStateOf(0.35f) }
    var spacing by remember { mutableFloatStateOf(0.18f) }
    var taper by remember { mutableFloatStateOf(0f) }
    var shapeFilled by remember { mutableStateOf(false) }
    var symmetry by remember { mutableStateOf(false) }
    var symmetryAxis by remember { mutableFloatStateOf(0.5f) }
    data class EditorSnapshot(
        val strokes: List<List<Stroke>>,
        val rasters: List<Bitmap>
    )
    var undo by remember { mutableStateOf(emptyList<EditorSnapshot>()) }
    var redo by remember { mutableStateOf(emptyList<EditorSnapshot>()) }
    var scale by remember { mutableFloatStateOf(1f) }
    var rotation by remember { mutableFloatStateOf(0f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var onionSkin by remember { mutableStateOf(true) }
    var fps by remember { mutableIntStateOf(12) }
    var frameIndex by remember { mutableIntStateOf(0) }
    var playing by remember { mutableStateOf(false) }

    fun copyBitmap(source: Bitmap): Bitmap = source.copy(Bitmap.Config.ARGB_8888, true)

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
            if (tool == Tool.BRUSH || tool == Tool.ERASER) {
                val bitmap = rasterLayers[selectedLayer]
                val androidCanvas = AndroidCanvas(bitmap)
                val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG or AndroidPaint.DITHER_FLAG)
                paint.color = if (tool == Tool.ERASER) android.graphics.Color.TRANSPARENT else brush.toArgb()
                paint.alpha = (opacity.coerceIn(0f, 1f) * 255f).toInt()
                paint.style = AndroidPaint.Style.STROKE
                paint.strokeWidth = when (brushType) {
                    "Pen" -> width
                    "Marker" -> width * 1.35f
                    "Airbrush" -> width * 1.8f
                    else -> width
                }.coerceAtLeast(1f)
                paint.strokeCap = AndroidPaint.Cap.ROUND
                paint.strokeJoin = AndroidPaint.Join.ROUND
                if (tool == Tool.ERASER) paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
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

            currentStrokes = updated
            saveFrame()
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

    LaunchedEffect(playing, fps, frameData.size) {
        while (playing) {
            delay(1000L / fps)
            loadFrame((frameIndex + 1) % frameData.size)
        }
    }

    val colors = listOf(
        Color.Black, Color.White, Color.Red, Color(0xFFFF9800), Color.Yellow,
        Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color(0xFF795548)
    )

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("MotionCanvas") },
            actions = {
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
            FilterChip(brushType == "Pencil", { brushType = "Pencil" }, label = { Text("Pencil") })
            FilterChip(brushType == "Pen", { brushType = "Pen" }, label = { Text("Pen") })
            FilterChip(brushType == "Marker", { brushType = "Marker" }, label = { Text("Marker") })
            FilterChip(brushType == "Airbrush", { brushType = "Airbrush" }, label = { Text("Airbrush") })
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
            FilterChip(symmetry, { symmetry = !symmetry }, label = { Text("Symmetry") })
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Stabilizer", Modifier.width(75.dp))
            Slider(stabilization, { stabilization = it }, valueRange = 0f..0.85f)
            Text((stabilization * 100).toInt().toString() + "%")
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
                Canvas(
                    Modifier.fillMaxSize().graphicsLayer(
                        scaleX = scale, scaleY = scale, rotationZ = rotation,
                        translationX = pan.x, translationY = pan.y
                    ).pointerInput(tool, selectedLayer, width, opacity, brush, stabilization, pressureEnabled, spacing, taper) {
                        detectDragGestures(
                            onDragStart = { start ->
                                current = listOf(start)
                                if (tool == Tool.SELECT) selection = listOf(start)
                            },
                            onDrag = { change, _ ->
                                current = current + change.position
                                if (tool == Tool.SELECT) selection = selection + change.position
                            },
                            onDragEnd = {
                                if (tool == Tool.SELECT) selectFromLasso() else commitStroke()
                            },
                            onDragCancel = { current = emptyList(); selection = emptyList() }
                        )
                    }
                ) {
                    if (onionSkin && frameIndex > 0) {
                        frameData[frameIndex - 1].layers.forEach { layer ->
                            layer.strokes.forEach { s -> drawStroke(this, s, Color.Red.copy(alpha = 0.14f)) }
                        }
                    }

                    rasterLayers.forEachIndexed { index, bitmap ->
                        if (layers.getOrNull(index)?.visible == true) {
                            drawImage(bitmap.asImageBitmap())
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

            Surface(Modifier.width(132.dp).fillMaxHeight(), tonalElevation = 3.dp) {
                Column(Modifier.fillMaxSize()) {
                    Text("Layers", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(10.dp))
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
                                        Text(if (currentStrokes.getOrNull(index)?.isNotEmpty() == true) "Content" else "Empty",
                                            style = MaterialTheme.typography.labelSmall)
                                    }
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
        stroke.points.drop(1).forEach { lineTo(it.x, it.y) }
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
