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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.max

data class Stroke(val points: List<Offset>, val color: Color, val width: Float, val opacity: Float = 1f)
data class ArtLayer(val name: String, val visible: Boolean = true, val opacity: Float = 1f)
data class LayerFrame(val strokes: List<Stroke> = emptyList(), val hold: Int = 1)
data class Frame(val layers: List<LayerFrame> = emptyList())
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
    var frameData by remember { mutableStateOf(listOf(Frame(listOf(LayerFrame())))) }
    var currentStrokes by remember { mutableStateOf(listOf(emptyList<Stroke>())) }
    var selectedLayer by remember { mutableIntStateOf(0) }
    var current by remember { mutableStateOf(emptyList<Offset>()) }
    var tool by remember { mutableStateOf(Tool.BRUSH) }
    var brush by remember { mutableStateOf(Color.Black) }
    var width by remember { mutableFloatStateOf(10f) }
    var opacity by remember { mutableFloatStateOf(1f) }
    var undo by remember { mutableStateOf(emptyList<List<List<Stroke>>>()) }
    var redo by remember { mutableStateOf(emptyList<List<List<Stroke>>>()) }
    var scale by remember { mutableFloatStateOf(1f) }
    var rotation by remember { mutableFloatStateOf(0f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var onionSkin by remember { mutableStateOf(true) }
    var fps by remember { mutableIntStateOf(12) }
    var frameIndex by remember { mutableIntStateOf(0) }
    var playing by remember { mutableStateOf(false) }

    fun saveFrame() {
        val updated = frameData.toMutableList()
        updated[frameIndex] = Frame(currentStrokes.map { LayerFrame(it, updated[frameIndex].layers.getOrNull(currentStrokes.indexOf(it))?.hold ?: 1) })
        frameData = updated
    }
    fun loadFrame(index: Int) {
        frameIndex = index
        val source = frameData[index]
        currentStrokes = layers.indices.map { source.layers.getOrNull(it)?.strokes ?: emptyList() }
    }
    fun snapshot() {
        undo = (undo + currentStrokes).takeLast(40)
        redo = emptyList()
    }
    fun commitStroke() {
        if (current.size > 1) {
            snapshot()
            val points = if (tool == Tool.LINE) listOf(current.first(), current.last()) else current
            val color = if (tool == Tool.ERASER) Color.White else brush
            val stroke = Stroke(points, color, width, opacity)
            val updated = currentStrokes.toMutableList()
            updated[selectedLayer] = updated[selectedLayer] + stroke
            currentStrokes = updated
            saveFrame()
        }
        current = emptyList()
    }
    fun addFrame() {
        saveFrame()
        val empty = Frame(layers.map { LayerFrame() })
        frameData = frameData.toMutableList().also { it.add(frameIndex + 1, empty) }
        loadFrame(frameIndex + 1)
    }
    fun duplicateFrame() {
        saveFrame()
        frameData = frameData.toMutableList().also { it.add(frameIndex + 1, frameData[frameIndex]) }
        loadFrame(frameIndex + 1)
    }
    fun deleteFrame() {
        if (frameData.size > 1) {
            frameData = frameData.toMutableList().also { it.removeAt(frameIndex) }
            frameIndex = frameIndex.coerceAtMost(frameData.lastIndex)
            loadFrame(frameIndex)
        }
    }
    fun setHold(value: Int) {
        saveFrame()
        val f = frameData[frameIndex]
        frameData = frameData.toMutableList().also { it[frameIndex] = f.copy(layers = f.layers.map { l -> l.copy(hold = value) }) }
    }
    fun addLayer() {
        saveFrame()
        val newIndex = layers.size
        layers = layers + ArtLayer("Layer " + (newIndex + 1))
        currentStrokes = currentStrokes + emptyList()
        frameData = frameData.map { f -> f.copy(layers = f.layers + LayerFrame()) }
        selectedLayer = newIndex
    }
    fun deleteLayer() {
        if (layers.size > 1) {
            saveFrame()
            layers = layers.toMutableList().also { it.removeAt(selectedLayer) }
            currentStrokes = currentStrokes.toMutableList().also { it.removeAt(selectedLayer) }
            frameData = frameData.map { f -> f.copy(layers = f.layers.filterIndexed { i, _ -> i != selectedLayer }) }
            selectedLayer = max(0, selectedLayer - 1)
        }
    }

    LaunchedEffect(playing, fps, frameData.size) {
        while (playing) {
            delay(1000L / fps)
            val next = (frameIndex + 1) % frameData.size
            loadFrame(next)
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("MotionCanvas") },
            actions = {
                TextButton(enabled = undo.isNotEmpty(), onClick = {
                    redo = redo + currentStrokes
                    currentStrokes = undo.last()
                    undo = undo.dropLast(1)
                    saveFrame()
                }) { Text("Undo") }
                TextButton(enabled = redo.isNotEmpty(), onClick = {
                    undo = undo + currentStrokes
                    currentStrokes = redo.last()
                    redo = redo.dropLast(1)
                    saveFrame()
                }) { Text("Redo") }
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

        Box(
            Modifier.weight(1f).fillMaxWidth().background(Color.White)
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
                ).pointerInput(selectedLayer, tool, brush, width, opacity) {
                    detectDragGestures(
                        onDragStart = { current = listOf(it) },
                        onDrag = { change, _ -> current = current + change.position },
                        onDragEnd = ::commitStroke,
                        onDragCancel = { current = emptyList() }
                    )
                }
            ) {
                if (onionSkin && frameIndex > 0) {
                    val previous = frameData[frameIndex - 1]
                    previous.layers.forEach { layer ->
                        layer.strokes.forEach { s ->
                            if (s.points.isNotEmpty()) {
                                val path = Path().apply {
                                    moveTo(s.points[0].x, s.points[0].y)
                                    s.points.drop(1).forEach { lineTo(it.x, it.y) }
                                }
                                drawPath(path, Color.Red.copy(alpha = 0.16f), style = androidx.compose.ui.graphics.drawscope.Stroke(s.width))
                            }
                        }
                    }
                }

                currentStrokes.forEachIndexed { index, strokes ->
                    if (layers.getOrNull(index)?.visible == true) {
                        strokes.forEach { s ->
                            if (s.points.isNotEmpty()) {
                                val path = Path().apply {
                                    moveTo(s.points[0].x, s.points[0].y)
                                    s.points.drop(1).forEach { lineTo(it.x, it.y) }
                                }
                                drawPath(path, s.color, s.opacity * layers[index].opacity,
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(s.width, cap = StrokeCap.Round))
                            }
                        }
                    }
                }

                if (current.isNotEmpty()) {
                    val path = Path().apply {
                        moveTo(current[0].x, current[0].y)
                        current.drop(1).forEach { lineTo(it.x, it.y) }
                    }
                    drawPath(path, if (tool == Tool.ERASER) Color.White else brush, opacity,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width, cap = StrokeCap.Round))
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { playing = !playing }) { Text(if (playing) "Pause" else "Play") }
            Text("FPS " + fps, Modifier.padding(horizontal = 6.dp))
            listOf(8, 12, 24).forEach { rate -> Button(onClick = { fps = rate }) { Text(rate.toString()) } }
            Button(onClick = { setHold(frameData[frameIndex].layers.firstOrNull()?.hold ?: 1 + 1) }) { Text("Hold+") }
            Button(onClick = { setHold(1) }) { Text("Hold 1") }
        }

        Text(
            "Timeline  •  drag to scrub  •  current frame " + (frameIndex + 1),
            Modifier.fillMaxWidth().padding(start = 8.dp, top = 4.dp)
        )

        LazyRow(Modifier.fillMaxWidth().padding(5.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            itemsIndexed(frameData) { index, frame ->
                val selected = index == frameIndex
                Surface(
                    modifier = Modifier.width(62.dp).height(58.dp).clickable {
                        saveFrame()
                        loadFrame(index)
                    },
                    tonalElevation = if (selected) 6.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Text("F" + (index + 1), style = MaterialTheme.typography.labelLarge)
                        Text(
                            if (frame.layers.any { it.strokes.isNotEmpty() }) "●" else "○",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text("×" + frame.layers.firstOrNull()?.hold.orEmpty().toString(), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(5.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Button(onClick = ::addFrame) { Text("+ Frame") }
            Button(onClick = ::duplicateFrame) { Text("Duplicate") }
            Button(onClick = ::deleteFrame, enabled = frameData.size > 1) { Text("Delete") }
        }

        Row(Modifier.fillMaxWidth().padding(5.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Button(onClick = ::addLayer) { Text("+ Layer") }
            Button(onClick = ::deleteLayer, enabled = layers.size > 1) { Text("Delete Layer") }
            Text("Layers " + layers.size + " • Frame " + (frameIndex + 1) + "/" + frameData.size, Modifier.padding(10.dp))
        }
    }
}
