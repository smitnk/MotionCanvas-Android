package com.smitnk.motioncanvas

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

data class Stroke(val points: List<Offset>, val color: Color, val width: Float)

class MainActivity : ComponentActivity() {
 override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { MotionCanvasApp() } }
}

@Composable
fun MotionCanvasApp() {
 var strokes by remember { mutableStateOf(listOf<Stroke>()) }
 var current by remember { mutableStateOf(listOf<Offset>()) }
 var brush by remember { mutableStateOf(Color.Black) }
 var width by remember { mutableFloatStateOf(10f) }
 var undo by remember { mutableStateOf(listOf<List<Stroke>>()) }

 fun addStroke() {
   if (current.size > 1) { undo = undo + strokes; strokes = strokes + Stroke(current, brush, width); current = emptyList() }
 }

 Column(Modifier.fillMaxSize()) {
   TopAppBar(title={Text("MotionCanvas")}, actions={
     TextButton(onClick={ if(undo.isNotEmpty()){ strokes=undo.last(); undo=undo.dropLast(1)}}){Text("Undo")}
     TextButton(onClick={ current=emptyList(); strokes=emptyList(); undo=emptyList() }){Text("Clear")}
   })
   Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement=Arrangement.spacedBy(8.dp)) {
     Button(onClick={brush=Color.Black}){Text("Pen")}
     Button(onClick={brush=Color.Red}){Text("Red")}
     Button(onClick={brush=Color.Blue}){Text("Blue")}
     Text("Size $" + "{width.toInt()}px")
   }
   Canvas(Modifier.fillMaxWidth().weight(1f).background(Color.White).pointerInput(Unit) {
     detectDragGestures(
       onDragStart={ current=listOf(it) },
       onDrag={ change, _ -> current=current + change.position },
       onDragEnd={ addStroke() },
       onDragCancel={ current=emptyList() }
     )
   }) {
     strokes.forEach { s -> drawPath(Path().apply { if(s.points.isNotEmpty()){moveTo(s.points[0].x,s.points[0].y); s.points.drop(1).forEach{lineTo(it.x,it.y)}}}, s.color, s.width) }
     if(current.isNotEmpty()) drawPath(Path().apply {moveTo(current[0].x,current[0].y); current.drop(1).forEach{lineTo(it.x,it.y)}}, brush, width)
   }
   Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(8.dp), horizontalArrangement=Arrangement.spacedBy(8.dp)) {
     listOf(4f,8f,12f,20f,32f).forEach { w -> Button(onClick={width=w}){Text(w.toInt().toString())} }
   }
 }
}
