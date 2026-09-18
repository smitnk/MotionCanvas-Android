package com.smitnk.motioncanvas

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.max

data class Stroke(val points: List<Offset>, val color: Color, val width: Float, val opacity: Float = 1f)
data class ArtLayer(val name: String, val strokes: List<Stroke> = emptyList(), val visible: Boolean = true, val opacity: Float = 1f)
data class Frame(val strokes: List<Stroke> = emptyList())
enum class Tool { BRUSH, ERASER, LINE }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MotionCanvasApp() }
    }
}

@Composable
fun MotionCanvasApp() {
    var layers by remember { mutableStateOf(listOf(ArtLayer("Layer 1"))) }
    var selectedLayer by remember { mutableIntStateOf(0) }
    var current by remember { mutableStateOf(emptyList<Offset>()) }
    var tool by remember { mutableStateOf(Tool.BRUSH) }
    var brush by remember { mutableStateOf(Color.Black) }
    var width by remember { mutableFloatStateOf(10f) }
    var opacity by remember { mutableFloatStateOf(1f) }
    var undo by remember { mutableStateOf(emptyList<List<ArtLayer>>()) }
    var redo by remember { mutableStateOf(emptyList<List<ArtLayer>>()) }
    var scale by remember { mutableFloatStateOf(1f) }
    var rotation by remember { mutableFloatStateOf(0f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var onionSkin by remember { mutableStateOf(true) }
    var fps by remember { mutableIntStateOf(12) }
    var frames by remember { mutableStateOf(listOf(Frame())) }
    var frameIndex by remember { mutableIntStateOf(0) }
    var playing by remember { mutableStateOf(false) }

    fun saveFrame() {
        val strokes = layers.flatMap { it.strokes }
        frames = frames.toMutableList().also { it[frameIndex] = Frame(strokes) }
    }
    fun loadFrame(index: Int) {
        frameIndex = index
        val strokes = frames[index].strokes
        layers = layers.map { it.copy(strokes = emptyList()) }.toMutableList().also {
            if (it.isNotEmpty()) it[selectedLayer] = it[selectedLayer].copy(strokes = strokes)
        }
    }
    fun snapshot() { undo = (undo + layers).takeLast(40); redo = emptyList() }
    fun commitStroke() {
        if (current.size > 1) {
            snapshot()
            val points = if (tool == Tool.LINE) listOf(current.first(), current.last()) else current
            val color = if (tool == Tool.ERASER) Color.White else brush
            val stroke = Stroke(points, color, width, opacity)
            val layer = layers[selectedLayer]
            layers = layers.toMutableList().also { it[selectedLayer] = layer.copy(strokes = layer.strokes + stroke) }
            saveFrame()
        }
        current = emptyList()
    }
    fun addFrame() {
        saveFrame()
        frames = frames.toMutableList().also { it.add(frameIndex + 1, Frame()) }
        loadFrame(frameIndex + 1)
    }
    fun duplicateFrame() {
        saveFrame()
        val copy = frames[frameIndex]
        frames = frames.toMutableList().also { it.add(frameIndex + 1, copy) }
        loadFrame(frameIndex + 1)
    }
    fun deleteFrame() {
        if (frames.size > 1) {
            frames = frames.toMutableList().also { it.removeAt(frameIndex) }
            frameIndex = frameIndex.coerceAtMost(frames.lastIndex)
            loadFrame(frameIndex)
        }
    }
    fun undoAction() {
        if (undo.isNotEmpty()) {
            redo = redo + layers
            layers = undo.last()
            undo = undo.dropLast(1)
            saveFrame()
        }
    }
    fun redoAction() {
        if (redo.isNotEmpty()) {
            undo = undo + layers
            layers = redo.last()
            redo = redo.dropLast(1)
            saveFrame()
        }
    }
    fun addLayer() {
        snapshot()
        layers = layers.toMutableList().also { it.add(ArtLayer("Layer " + (it.size + 1))) }
        selectedLayer = layers.lastIndex
    }
    fun deleteLayer() {
        if (layers.size > 1) {
            snapshot()
            layers = layers.toMutableList().also { it.removeAt(selectedLayer) }
            selectedLayer = max(0, selectedLayer - 1)
        }
    }

    LaunchedEffect(playing, fps, frames.size) {
        while (playing) {
            delay(1000L / fps)
            val next = (frameIndex + 1) % frames.size
            loadFrame(next)
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("MotionCanvas") },
            actions = {
                TextButton(enabled = undo.isNotEmpty(), onClick = ::undoAction) { Text("Undo") }
                TextButton(enabled = redo.isNotEmpty(), onClick = ::redoAction) { Text("Redo") }
                TextButton(onClick = { scale = 1f; rotation = 0f; pan = Offset.Zero }) { Text("Reset") }
            }
        )

        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(5.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            FilterChip(tool == Tool.BRUSH, { tool = Tool.BRUSH }, label = { Text("Brush") })
            FilterChip(tool == Tool.ERASER, { tool = Tool.ERASER }, label = { Text("Eraser") })
            FilterChip(tool == Tool.LINE, { tool = Tool.LINE }, label = { Text("Line") })
            FilterChip(onionSkin, { onionSkin = !onionSkin }, label = { Text("Onion") })
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Size " + width.toInt() + " px", Modifier.width(95.dp))
            Slider(width, { width = it }, valueRange = 1f..80f)
        }

        Box(Modifier.weight(1f).fillMaxWidth().background(Color.White)
            .pointerInput(Unit) {
                detectTransformGestures { _, panChange, zoomChange, rotationChange ->
                    scale = (scale * zoomChange).coerceIn(0.25f, 8f)
                    pan += panChange
                    rotation += rotationChange
                }
            }
        ) {
            Canvas(Modifier.fillMaxSize().graphicsLayer(
                scaleX = scale, scaleY = scale, rotationZ = rotation,
                translationX = pan.x, translationY = pan.y
            ).pointerInput(selectedLayer, tool, brush, width, opacity) {
                detectDragGestures(
                    onDragStart = { current = listOf(it) },
                    onDrag = { change, _ -> current = current + change.position },
                    onDragEnd = ::commitStroke,
                    onDragCancel = { current = emptyList() }
                )
            }) {
                val previous = if (onionSkin && frameIndex > 0) frames[frameIndex - 1] else null
                previous?.strokes?.forEach { s ->
                    val path = Path().apply {
                        moveTo(s.points[0].x, s.points[0].y)
                        s.points.drop(1).forEach { lineTo(it.x, it.y) }
                    }
                    drawPath(path, Color.Red.copy(alpha = 0.18f), style = DrawStroke(s.width))
                }

                layers.forEach { layer ->
                    if (layer.visible) layer.strokes.forEach { s ->
                        val path = Path().apply {
                            moveTo(s.points[0].x, s.points[0].y)
                            s.points.drop(1).forEach { lineTo(it.x, it.y) }
                        }
                        drawPath(path, s.color, s.opacity * layer.opacity,
                            style = DrawStroke(s.width, cap = StrokeCap.Round))
                    }
                }
                if (current.isNotEmpty()) {
                    val path = Path().apply {
                        moveTo(current[0].x, current[0].y)
                        current.drop(1).forEach { lineTo(it.x, it.y) }
                    }
                    drawPath(path, if (tool == Tool.ERASER) Color.White else brush, opacity,
                        style = DrawStroke(width, cap = StrokeCap.Round))
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { playing = !playing }) { Text(if (playing) "Pause" else "Play") }
            Text("FPS $fps", Modifier.padding(horizontal = 8.dp))
            listOf(8, 12, 24).forEach { rate -> Button(onClick = { fps = rate }) { Text(rate.toString()) } }
        }

        LazyRow(Modifier.fillMaxWidth().padding(5.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            itemsIndexed(frames) { index, _ ->
                Button(onClick = { saveFrame(); loadFrame(index) }) {
                    Text((index + 1).toString())
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(5.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Button(onClick = ::addFrame) { Text("+ Frame") }
            Button(onClick = ::duplicateFrame) { Text("Duplicate") }
            Button(onClick = ::deleteFrame, enabled = frames.size > 1) { Text("Delete Frame") }
        }

        Row(Modifier.fillMaxWidth().padding(5.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Button(onClick = ::addLayer) { Text("+ Layer") }
            Button(onClick = ::deleteLayer, enabled = layers.size > 1) { Text("Delete Layer") }
            Text("Frame " + (frameIndex + 1) + "/" + frames.size, Modifier.padding(10.dp))
        }
    }
}
