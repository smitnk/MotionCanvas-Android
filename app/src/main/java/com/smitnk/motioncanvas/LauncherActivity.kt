package com.smitnk.motioncanvas

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.min

private val MotionPink = Color(0xFFFF4F7B)
private val MotionDark = Color(0xFF35383D)
private val MotionGray = Color(0xFF6B6D72)
private val MotionPaper = Color(0xFFFAFAF8)

data class MotionProject(
    val name: String,
    val fps: Int,
    val width: Int,
    val height: Int,
    val duration: String
)

class LauncherActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = MotionPink,
                    onPrimary = Color.White,
                    background = Color.White,
                    surface = Color.White,
                    onSurface = MotionDark
                )
            ) {
                MotionCanvasHome { project ->
                    startActivity(
                        Intent(this, MainActivity::class.java).apply {
                            putExtra("project_name", project.name)
                            putExtra("project_fps", project.fps)
                            putExtra("project_width", project.width)
                            putExtra("project_height", project.height)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun MotionCanvasHome(openEditor: (MotionProject) -> Unit) {
    var screen by remember { mutableStateOf("home") }
    var projects by remember {
        mutableStateOf(
            listOf(
                MotionProject("S", 12, 1280, 720, "0:00"),
                MotionProject("Ss", 24, 1280, 720, "0:22"),
                MotionProject("Ss2", 30, 1280, 720, "0:07"),
                MotionProject("Ss3", 30, 1280, 720, "0:08"),
                MotionProject("Ss4", 30, 1280, 720, "0:38"),
                MotionProject("Ss5", 30, 1280, 720, "1:00")
            )
        )
    }

    if (screen == "home") {
        HomeScreen(projects, { screen = "create" }, openEditor)
    } else {
        CreateProjectScreen(
            onBack = { screen = "home" },
            onCreate = { project ->
                projects = listOf(project) + projects
                openEditor(project)
            }
        )
    }
}

@Composable
private fun HomeScreen(
    projects: List<MotionProject>,
    onCreate: () -> Unit,
    onOpen: (MotionProject) -> Unit
) {
    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Color.White,
            bottomBar = {
                NavigationBar(containerColor = Color.White) {
                    NavigationBarItem(
                        selected = true,
                        onClick = {},
                        icon = { Text("▣", fontSize = 25.sp, color = MotionPink) },
                        label = { Text("HOME", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    )
                    NavigationBarItem(
                        selected = false,
                        onClick = {},
                        icon = { Text("✦", fontSize = 25.sp, color = MotionDark) },
                        label = { Text("DISCOVER", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    )
                }
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 26.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("☰", fontSize = 30.sp, color = MotionDark)
                    Spacer(Modifier.weight(1f))
                    Text("MotionCanvas", color = MotionDark, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.weight(1f))
                    Text("⌕", fontSize = 36.sp, color = MotionDark)
                }
                Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalAlignment = Alignment.Bottom) {
                    Text("Projects", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = MotionDark)
                    Spacer(Modifier.width(28.dp))
                    Text("Movies", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = MotionGray)
                    Spacer(Modifier.weight(1f))
                    Text("•••", color = Color.LightGray, fontSize = 20.sp)
                }
                Box(Modifier.height(3.dp).width(88.dp).background(MotionPink))
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize().padding(top = 30.dp),
                    horizontalArrangement = Arrangement.spacedBy(28.dp),
                    verticalArrangement = Arrangement.spacedBy(34.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(projects) { project -> ProjectCard(project) { onOpen(project) } }
                }
            }
        }
        FloatingActionButton(
            onClick = onCreate,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 72.dp).size(66.dp),
            containerColor = MotionPink,
            contentColor = Color.White
        ) {
            Text("+", fontSize = 38.sp, fontWeight = FontWeight.Light)
        }
    }
}

@Composable
private fun ProjectCard(project: MotionProject, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1.36f).clip(RoundedCornerShape(22.dp))
                .background(MotionPaper).border(1.dp, Color(0xFFE8E8E5), RoundedCornerShape(22.dp))
        ) {
            Canvas(Modifier.fillMaxSize().padding(12.dp)) {
                val w = size.width
                val h = size.height
                drawRect(Color(0xFFFDFCF9), style = Fill)
                when (project.name) {
                    "S" -> {
                        drawCircle(MotionDark, radius = min(w, h) * .14f, center = Offset(w*.45f, h*.38f), style = Stroke(3f))
                        drawLine(MotionDark, Offset(w*.40f,h*.52f), Offset(w*.30f,h*.75f), 4f, StrokeCap.Round)
                        drawLine(MotionDark, Offset(w*.50f,h*.52f), Offset(w*.60f,h*.72f), 4f, StrokeCap.Round)
                    }
                    "Ss" -> {
                        drawCircle(MotionPink, radius = min(w,h)*.23f, center = Offset(w*.50f,h*.48f), style = Fill)
                        drawCircle(Color.White, radius = min(w,h)*.10f, center = Offset(w*.42f,h*.40f), style = Fill)
                    }
                    "Ss2", "Ss3" -> {
                        val p = Path().apply {
                            moveTo(w*.18f,h*.72f); quadraticTo(w*.48f,h*.18f,w*.80f,h*.70f)
                            quadraticTo(w*.52f,h*.88f,w*.18f,h*.72f)
                        }
                        drawPath(p, Color(0xFFDCE9FF), style = Fill)
                        drawPath(p, MotionDark, style = Stroke(3f))
                    }
                    else -> {
                        drawRoundRect(Color(0xFFE9F2EA), topLeft = Offset(w*.10f,h*.12f),
                            size = androidx.compose.ui.geometry.Size(w*.80f,h*.72f),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(18f,18f), style = Fill)
                        drawCircle(MotionPink, radius = min(w,h)*.13f, center = Offset(w*.50f,h*.48f), style = Fill)
                    }
                }
            }
            Row(Modifier.align(Alignment.BottomEnd).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                MetaPill(project.duration)
                MetaPill(project.fps.toString() + " fps")
            }
        }
        Text(project.name, Modifier.padding(top = 12.dp), fontSize = 17.sp, color = MotionDark, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun MetaPill(text: String) {
    Surface(color = Color.White, shape = RoundedCornerShape(14.dp), shadowElevation = 1.dp) {
        Text(text, Modifier.padding(horizontal = 9.dp, vertical = 4.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun CreateProjectScreen(onBack: () -> Unit, onCreate: (MotionProject) -> Unit) {
    var name by remember { mutableStateOf("") }
    var width by remember { mutableIntStateOf(1280) }
    var height by remember { mutableIntStateOf(720) }
    var fps by remember { mutableIntStateOf(12) }
    var showCanvasSizes by remember { mutableStateOf(false) }
    var showFps by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(Color.White)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("×", fontSize = 40.sp, color = MotionDark, modifier = Modifier.clickable(onClick = onBack))
        }
        Text("Project name", Modifier.padding(horizontal = 28.dp), color = MotionPink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            placeholder = { Text("Name your animation", fontSize = 28.sp, fontWeight = FontWeight.Bold) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 8.dp),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MotionPink, unfocusedBorderColor = MotionPink)
        )
        Text("Choose background", Modifier.padding(horizontal = 28.dp, vertical = 10.dp), color = MotionPink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Box(
            Modifier.fillMaxWidth().padding(horizontal = 28.dp).height(260.dp)
                .clip(RoundedCornerShape(22.dp)).background(MotionPaper)
                .border(1.dp, Color(0xFFE5E5E2), RoundedCornerShape(22.dp)),
            contentAlignment = Alignment.BottomCenter
        ) {
            Row(
                Modifier.padding(bottom = 22.dp).clip(RoundedCornerShape(28.dp)).background(Color.White)
                    .padding(horizontal = 24.dp, vertical = 13.dp),
                horizontalArrangement = Arrangement.spacedBy(26.dp)
            ) {
                Text("▯", fontSize = 26.sp); Text("◇", fontSize = 26.sp); Text("▧", fontSize = 26.sp); Text("▣", fontSize = 26.sp)
            }
        }
        Text("Format", Modifier.padding(horizontal = 28.dp, vertical = 28.dp), color = MotionPink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        SettingRow("Choose canvas size", width.toString() + " × " + height + " px") { showCanvasSizes = true }
        SettingRow("Choose frames per second", fps.toString() + " FPS") { showFps = true }
        Spacer(Modifier.weight(1f))
        Button(
            onClick = { onCreate(MotionProject(name.ifBlank { "Untitled" }, fps, width, height, "0:00")) },
            modifier = Modifier.fillMaxWidth().padding(28.dp).height(64.dp),
            shape = RoundedCornerShape(34.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MotionPink),
            enabled = name.isNotBlank()
        ) { Text("CREATE PROJECT", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
    }

    if (showCanvasSizes) {
        ChoiceSheet(
            "Canvas size",
            listOf(
                "YouTube (1080p)" to (1920 to 1080), "YouTube (720p)" to (1280 to 720),
                "Instagram (16x9)" to (1920 to 1080), "Instagram (1x1)" to (1080 to 1080),
                "TikTok (1080p)" to (1080 to 1920), "TikTok (720p)" to (720 to 1280),
                "Vimeo (1080p)" to (1920 to 1080), "Facebook (720p)" to (1280 to 720),
                "Tumblr (16x9)" to (1920 to 1080), "Tumblr (4x3)" to (1440 to 1080)
            ),
            onDismiss = { showCanvasSizes = false }
        ) { pair -> width = pair.first; height = pair.second; showCanvasSizes = false }
    }

    if (showFps) {
        ChoiceSheet("Frames per second", (8..60).map { it.toString() + " FPS" to it }, onDismiss = { showFps = false }) {
            fps = it; showFps = false
        }
    }
}

@Composable
private fun <T> ChoiceSheet(
    title: String,
    choices: List<Pair<String, T>>,
    onDismiss: () -> Unit,
    onChoose: (T) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.heightIn(max = 520.dp)) {
                choices.forEach { (label, value) ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onChoose(value) }.padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(label, Modifier.weight(1f), fontSize = 17.sp)
                        Text("✓", color = MotionPink, fontSize = 22.sp)
                    }
                }
            }
        },
        confirmButton = {}
    )
}

@Composable
private fun SettingRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 28.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, Modifier.weight(1f), fontSize = 17.sp, color = MotionDark)
        Text(value, color = MotionPink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
    }
}
