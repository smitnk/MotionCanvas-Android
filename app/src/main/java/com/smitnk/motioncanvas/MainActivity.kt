package com.smitnk.motioncanvas

import android.os.Bundle
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
        if (frameIndex !in frameData.indices) return
        val old = frameData[frameIndex]
        val savedLayers = currentStrokes.mapIndexed { i, strokes ->
            LayerFrame(strokes, old.layers.getOrNull(i)?.hold ?: 1)
        }
        frameData = frameData.toMutableList().also { it[frameIndex] = Frame(savedLayers) }
    }

    fun loadFrame(index: Int) {
        if (index !in frameData.indices) return
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
            val strokeColor = if (tool == Tool.ERASER) Color.White else brush
            val stroke = Stroke(points, strokeColor, width, opacity)
            val updated = currentStrokes.toMutableList()
            updated[selectedLayer] = updated[selectedLayer] + stroke
            currentStrokes = updated
            saveFrame()
        }
        current = emptyList()
    }

    fun addFrame() {
        saveFrame()
        frameData = frameData.toMutableList().also {
            it.add(frameIndex + 1, Frame(layers.map { LayerFrame() }))
        }
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
        val newIndex = layers.size
        layers = layers + ArtLayer("Layer " + (newIndex + 1))
        currentStrokes = currentStrokes + emptyList()
        frameData = frameData.map { it.copy(layers = it.layers + LayerFrame()) }
        selectedLayer = newIndex
    }

    fun deleteLayer() {
        if (layers.size > 1) {
            saveFrame()
            layers = layers.toMutableList().also { it.removeAt(selectedLayer) }
            currentStrokes = currentStrokes.toMutableList().also { it.removeAt(selectedLayer) }
            frameData = frameData.map { f ->
                f.copy(layers = f.layers.filterIndexed { index, _ -> index != selectedLayer })
            }
            selectedLayer = max(0, selectedLayer - 1)
        }
    }

    fun toggleLayerVisibility(index: Int) {
        layers = layers.toMutableList().also {
            it[index] = it[index].copy(visible = !it[index].visible)
        }
    }

    LaunchedEffect(playing, fps, frameData.size) {
        while (playing) {
            delay(1000L / fps)
            loadFrame((frameIndex + 1) % frameData.size)
        }
    }

    val colors = listOf(
        Color.Black, Color.White, Color.Red, Color(0xFFFF9800),
        Color.Yellow, Color.Green, Color.Cyan, Color.Blue,
        Color.Magenta, Color(0xFF795548)
    )

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
                TextButton(onClick = { scale = 1f; rotation = 0f; pan = Offset.Zero }) { Text("Reset View") }
            }
        )

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(5.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            FilterChip(tool == Tool.BRUSH, { tool = Tool.BRUSH }, label = { Text("Brush") })
            FilterChip(tool == Tool.ERASER, { tool = Tool.ERASER }, label = { Text("Eraser") })
            FilterChip(tool == Tool.LINE, { tool = Tool.LINE }, label = { Text("Line") })
            FilterChip(onionSkin, { onionSkin = !onionSkin }, label = { Text("Onion") })
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Size " + width.toInt() + " px", Modifier.width(90.dp))
            Slider(width, { width = it }, valueRange = 1f..80f)
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Opacity " + (opacity * 100).toInt() + "%", Modifier.width(90.dp))
            Slider(opacity, { opacity = it }, valueRange = 0.05f..1f)
        }

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            colors.forEach { color ->
                Box(
                    Modifier.size(30.dp).clip(CircleShape).background(color)
                        .border(if (brush == color) 3.dp else 1.dp, Color.Gray, CircleShape)
                        .clickable { brush = color }
                )
            }
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
                        frameData[frameIndex - 1].layers.forEach { layer ->
                            layer.strokes.forEach { s ->
                                if (s.points.isNotEmpty()) {
                                    val path = Path().apply {
                                        moveTo(s.points[0].x, s.points[0].y)
                                        s.points.drop(1).forEach { lineTo(it.x, it.y) }
                                    }
                                    drawPath(path, Color.Red.copy(alpha = 0.16f),
                                        style = androidx.compose.ui.graphics.drawscope.Stroke(s.width))
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
                                    drawPath(
                                        path, s.color,
                                        s.opacity * layers[index].opacity,
                                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                                            s.width, cap = StrokeCap.Round
                                        )
                                    )
                                }
                            }
                        }
                    }

                    if (current.isNotEmpty()) {
                        val path = Path().apply {
                            moveTo(current[0].x, current[0].y)
                            current.drop(1).forEach { lineTo(it.x, it.y) }
                        }
                        drawPath(
                            path, if (tool == Tool.ERASER) Color.White else brush, opacity,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width, cap = StrokeCap.Round)
                        )
                    }
                }
            }

            Surface(
                modifier = Modifier.width(132.dp).fillMaxHeight(),
                tonalElevation = 3.dp
            ) {
                Column(Modifier.fillMaxSize()) {
                    Text(
                        "Layers",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(10.dp)
                    )
                    LazyColumn(Modifier.weight(1f)) {
                        itemsIndexed(layers) { index, layer ->
                            Surface(
                                modifier = Modifier.fillMaxWidth().clickable { selectedLayer = index },
                                color = if (index == selectedLayer)
                                    MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surface
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().padding(7.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(onClick = { toggleLayerVisibility(index) }) {
                                        Icon(
                                            if (layer.visible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                            contentDescription = "Visibility"
                                        )
                                    }
                                    Column(Modifier.weight(1f)) {
                                        Text(layer.name, style = MaterialTheme.typography.labelLarge)
                                        Text(
                                            if (currentStrokes.getOrNull(index)?.isNotEmpty() == true) "Content" else "Empty",
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        Button(onClick = ::addLayer, modifier = Modifier.padding(4.dp)) { Text("+") }
                        Button(
                            onClick = ::deleteLayer,
                            enabled = layers.size > 1,
                            modifier = Modifier.padding(4.dp)
                        ) { Text("−") }
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { playing = !playing }) { Text(if (playing) "Pause" else "Play") }
            Text("FPS " + fps, Modifier.padding(horizontal = 5.dp))
            listOf(8, 12, 24).forEach { rate -> Button(onClick = { fps = rate }) { Text(rate.toString()) } }
            Button(onClick = { setHold((frameData[frameIndex].layers.firstOrNull()?.hold ?: 1) + 1) }) { Text("Hold +") }
            Button(onClick = { setHold(1) }) { Text("Hold 1") }
        }

        Text(
            "Timeline • tap a frame to scrub • F" + (frameIndex + 1),
            Modifier.fillMaxWidth().padding(start = 8.dp, top = 4.dp)
        )

        LazyRow(
            Modifier.fillMaxWidth().padding(5.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            itemsIndexed(frameData) { index, frame ->
                val selected = index == frameIndex
                Surface(
                    modifier = Modifier.width(64.dp).height(60.dp).clickable {
                        saveFrame()
                        loadFrame(index)
                    },
                    tonalElevation = if (selected) 6.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("F" + (index + 1), style = MaterialTheme.typography.labelLarge)
                        Text(if (frame.layers.any { it.strokes.isNotEmpty() }) "●" else "○")
                        Text("hold " + (frame.layers.firstOrNull()?.hold ?: 1))
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(5.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Button(onClick = ::addFrame) { Text("+ Frame") }
            Button(onClick = ::duplicateFrame) { Text("Duplicate") }
            Button(onClick = ::deleteFrame, enabled = frameData.size > 1) { Text("Delete") }
        }

        Text(
            "Selected layer: " + layers.getOrNull(selectedLayer)?.name.orEmpty() +
                " • " + layers.size + " layers • " + frameData.size + " frames",
            Modifier.fillMaxWidth().padding(7.dp),
            style = MaterialTheme.typography.labelMedium
        )
    }
}
