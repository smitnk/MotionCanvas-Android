package com.smitnk.motioncanvas

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.max

data class Stroke(val points: List<Offset>, val color: Color, val width: Float)
data class ArtLayer(val name: String, val strokes: List<Stroke> = emptyList(), val visible: Boolean = true, val opacity: Float = 1f)

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
    var brush by remember { mutableStateOf(Color.Black) }
    var width by remember { mutableFloatStateOf(10f) }
    var undo by remember { mutableStateOf(emptyList<List<ArtLayer>>()) }
    var scale by remember { mutableFloatStateOf(1f) }
    var rotation by remember { mutableFloatStateOf(0f) }
    var pan by remember { mutableStateOf(Offset.Zero) }

    fun saveUndo() { undo = (undo + layers).takeLast(30) }

    fun commitStroke() {
        if (current.size > 1) {
            saveUndo()
            val layer = layers[selectedLayer]
            layers = layers.toMutableList().also {
                it[selectedLayer] = layer.copy(strokes = layer.strokes + Stroke(current, brush, width))
            }
        }
        current = emptyList()
    }

    fun addLayer() {
        saveUndo()
        layers = layers.toMutableList().also { it.add(ArtLayer("Layer " + (it.size + 1))) }
        selectedLayer = layers.lastIndex
    }

    fun deleteLayer() {
        if (layers.size > 1) {
            saveUndo()
            layers = layers.toMutableList().also { it.removeAt(selectedLayer) }
            selectedLayer = max(0, selectedLayer - 1)
        }
    }

    fun undoAction() {
        if (undo.isNotEmpty()) {
            layers = undo.last()
            undo = undo.dropLast(1)
            selectedLayer = selectedLayer.coerceIn(0, layers.lastIndex)
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("MotionCanvas") }, actions = {
            TextButton(onClick = ::undoAction) { Text("Undo") }
            TextButton(onClick = { scale = 1f; rotation = 0f; pan = Offset.Zero }) { Text("Reset View") }
        })

        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(onClick = { brush = Color.Black }) { Text("Pen") }
            Button(onClick = { brush = Color.Red }) { Text("Red") }
            Button(onClick = { brush = Color.Blue }) { Text("Blue") }
            Text("Size " + width.toInt() + " px", modifier = Modifier.padding(10.dp))
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
            Canvas(Modifier.fillMaxSize()
                .graphicsLayer(scaleX = scale, scaleY = scale, rotationZ = rotation, translationX = pan.x, translationY = pan.y)
                .pointerInput(selectedLayer, brush, width) {
                    detectDragGestures(
                        onDragStart = { current = listOf(it) },
                        onDrag = { change, _ -> current = current + change.position },
                        onDragEnd = ::commitStroke,
                        onDragCancel = { current = emptyList() }
                    )
                }
            ) {
                layers.forEach { layer ->
                    if (layer.visible) layer.strokes.forEach { s ->
                        val path = Path().apply {
                            if (s.points.isNotEmpty()) {
                                moveTo(s.points[0].x, s.points[0].y)
                                s.points.drop(1).forEach { lineTo(it.x, it.y) }
                            }
                        }
                        drawPath(path, s.color, s.width, alpha = layer.opacity)
                    }
                }
                if (current.isNotEmpty()) {
                    val path = Path().apply {
                        moveTo(current[0].x, current[0].y)
                        current.drop(1).forEach { lineTo(it.x, it.y) }
                    }
                    drawPath(path, brush, width)
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(4f, 8f, 12f, 20f, 32f).forEach { w ->
                Button(onClick = { width = w }) { Text(w.toInt().toString()) }
            }
        }

        Row(Modifier.fillMaxWidth().padding(6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(onClick = ::addLayer) { Text("+ Layer") }
            Button(onClick = ::deleteLayer) { Text("Delete") }
            Text("Layers", modifier = Modifier.padding(10.dp))
        }

        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 180.dp)) {
            itemsIndexed(layers.asReversed()) { reverseIndex, layer ->
                val index = layers.lastIndex - reverseIndex
                ListItem(
                    headlineContent = { Text(layer.name) },
                    supportingContent = { Text(if (index == selectedLayer) "Selected" else "Tap to select") },
                    modifier = Modifier.clickable { selectedLayer = index }
                )
            }
        }
    }
}
