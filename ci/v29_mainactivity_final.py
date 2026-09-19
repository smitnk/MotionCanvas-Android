from pathlib import Path

p = Path("build-source/app/src/main/java/com/smitnk/motioncanvas/MainActivity.kt")
s = p.read_text()
# Generated V29 contains Material3 experimental APIs across multiple top-level dialogs.
# Opt in at file scope so every generated composable compiles consistently.
if "file:OptIn(ExperimentalMaterial3Api::class)" not in s:
    s = s.replace("package com.smitnk.motioncanvas\n", "package com.smitnk.motioncanvas\n\n@file:OptIn(ExperimentalMaterial3Api::class)\n", 1)


for imp in [
    "import kotlinx.coroutines.launch",
    "import androidx.compose.foundation.gestures.detectTransformGestures",
]:
    if imp not in s:
        lines = s.splitlines()
        pkg = next((i for i, line in enumerate(lines) if line.startswith("package ")), -1)
        insert_at = pkg + 1
        while insert_at < len(lines) and (lines[insert_at].startswith("import ") or lines[insert_at].strip() == ""):
            insert_at += 1
        lines.insert(insert_at, imp)
        s = "\n".join(lines) + ("\n" if s.endswith("\n") else "")

# Repair malformed toolbar snippets introduced by the compatibility patch.
s = s.replace('''                if (workspaceVisibility.brushPresetsWidget) {
                IconButton(
                    onClick = { showBrushPresets = true }
                ),
                    modifier = Modifier.background(Color.Transparent, CircleShape)
                ) {''','''                if (workspaceVisibility.brushPresetsWidget) {
                IconButton(
                    onClick = { showBrushPresets = true },
                    modifier = Modifier.background(Color.Transparent, CircleShape)
                ) {''',1)

# Remove the extra closing brace immediately before OnionSkinSettingsDialog.
s = s.replace('''    }
    }


@Composable
fun OnionSkinSettingsDialog(''','''    }


@Composable
fun OnionSkinSettingsDialog(''',1)

s = s.replace("\nfun EditorScreen(\n", "\n@OptIn(ExperimentalMaterial3Api::class)\n@Composable\nfun EditorScreen(\n", 1)
if "@OptIn(ExperimentalMaterial3Api::class)\n@Composable\nfun EditorScreen(" not in s:
    s = s.replace("@Composable\nfun EditorScreen(", "@OptIn(ExperimentalMaterial3Api::class)\n@Composable\nfun EditorScreen(", 1)

editor = s.index("fun EditorScreen(")
scaffold = s.index("    Scaffold(\n", editor)
bottom_bar = s.index("        bottomBar = {", scaffold)

topbar = '''    Scaffold(
        containerColor = AppBackground,
        topBar = {
            if (workspaceVisibility.topBar) {
                TopAppBar(
                    title = { Text(project.name, color = White, fontSize = 16.sp) },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = White) } },
                    actions = {
                        IconButton(onClick = { zoomPanState.zoomOut() }) { Icon(Icons.Default.ZoomOut, "Zoom out", tint = White) }
                        TextButton(onClick = { zoomPanState.reset() }) { Text("\${zoomPanState.zoomPercent}%", color = if (zoomPanState.zoom != 1f || zoomPanState.pan != Offset.Zero) PinkAccent else White, fontSize = 12.sp) }
                        IconButton(onClick = { zoomPanState.zoomIn() }) { Icon(Icons.Default.ZoomIn, "Zoom in", tint = White) }
                        if (zoomPanState.zoom != 1f || zoomPanState.pan != Offset.Zero) {
                            IconButton(onClick = { zoomPanState.reset() }) { Icon(Icons.Default.RestartAlt, "Reset view", tint = PinkAccent) }
                        }
                        IconButton(onClick = { onionSkinState.toggle() }) { Icon(Icons.Default.Layers, "Toggle onion skin", tint = if (onionSkinState.enabled) PinkAccent else White) }
                        IconButton(onClick = { showOnionDialog = true }) { Icon(Icons.Default.Tune, "Onion skin settings", tint = White) }
                        IconButton(onClick = { history.undo() }, enabled = history.canUndo) { Icon(Icons.Default.Undo, "Undo", tint = White) }
                        IconButton(onClick = { history.redo() }, enabled = history.canRedo) { Icon(Icons.Default.Redo, "Redo", tint = White) }
                        IconButton(onClick = onOpenTimeline) { Icon(Icons.Default.ViewCarousel, "Timeline", tint = PinkAccent) }
                        IconButton(onClick = onOpenLayers) { Icon(Icons.Default.Layers, "Layers", tint = White) }
                        if (workspaceVisibility.referenceWidget) {
                            IconButton(onClick = { imagePicker.launch("image/*") }) { Icon(Icons.Default.Image, "Reference image", tint = if (referenceBitmap != null) PinkAccent else White) }
                            IconButton(onClick = { showReferenceDialog = true }) { Icon(Icons.Default.Tune, "Reference transform", tint = White) }
                            FilterChip(selected = referenceEditMode, onClick = { if (referenceBitmap != null) referenceEditMode = !referenceEditMode }, label = { Text("Ref Edit") })
                        }
                        if (workspaceVisibility.frameToolsWidget) { IconButton(onClick = { showFrameTools = true }) { Icon(Icons.Default.Flag, "Frame tools", tint = White) } }
                        if (workspaceVisibility.audioWidget) { IconButton(onClick = { showAudioDialog = true }) { Icon(Icons.Default.Mic, "Voice recording", tint = if (isRecording) PinkAccent else White) } }
                        if (workspaceVisibility.advancedWidget) { IconButton(onClick = { showAdvancedPanel = true }) { Icon(Icons.Default.Tune, "Advanced tools", tint = White) } }
                        if (workspaceVisibility.proToolsWidget) { IconButton(onClick = { showProTools = true }) { Icon(Icons.Default.AutoAwesome, "Pro tools", tint = PinkAccent) } }
                        IconButton(onClick = onOpenMore) { Icon(Icons.Default.MoreVert, "More tools / widget visibility", tint = PinkAccent) }
                        IconButton(onClick = onOpenSettings) { Icon(Icons.Default.Settings, "Settings", tint = White) }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = AppBackground)
                )
            }
        },
'''
s = s[:scaffold] + topbar + s[bottom_bar:]
# Final line-structure repair after the scaffold rewrite.
src = s.splitlines()
for idx, line in enumerate(src):
    if "if (workspaceVisibility.brushPresetsWidget)" in line:
        src[idx:idx+7] = [
            "                if (workspaceVisibility.brushPresetsWidget) {",
            "                IconButton(",
            "                    onClick = { showBrushPresets = true },",
            "                    modifier = Modifier.background(Color.Transparent, CircleShape)",
            "                ) {",
            '                    Icon(Icons.Default.AutoFixHigh, contentDescription = "Brush presets", tint = White)',
            "                }",
        ]
        break
for idx, line in enumerate(src):
    if "fun OnionSkinSettingsDialog" in line:
        j = idx - 1
        while j >= 0 and src[j].strip() == "":
            j -= 1
        if j >= 1 and src[j].strip() == "}" and src[j-1].strip() == "}":
            del src[j]
        break
s = "\n".join(src) + ("\n" if s.endswith("\n") else "")


# Final source-level repair: normalize the malformed Brush Presets IconButton.
import re
s, n_toolbar = re.subn(
    r'if \(workspaceVisibility\.brushPresetsWidget\)\s*\{\s*IconButton\(\s*onClick = \{ showBrushPresets = true \}\s*\)\s*,\s*modifier = Modifier\.background\(Color\.Transparent, CircleShape\)\s*\)\s*\{',
    '''if (workspaceVisibility.brushPresetsWidget) {
                IconButton(
                    onClick = { showBrushPresets = true },
                    modifier = Modifier.background(Color.Transparent, CircleShape)
                ) {''',
    s,
    count=1,
)
# Remove the extra top-level brace immediately before the next composable.
s = re.sub(
    r'\n\s*\}\s*\n\s*\}\s*\n\s*\n@Composable\s*\nfun OnionSkinSettingsDialog',
    '\n    }\n\n\n@Composable\nfun OnionSkinSettingsDialog',
    s,
    count=1,
)
if n_toolbar == 0:
    print("WARNING: Brush Presets malformed fragment was not found")


# Deterministic cleanup of malformed source fragments after scaffold replacement.
bad1 = """                if (workspaceVisibility.brushPresetsWidget) {
                IconButton(
                    onClick = { showBrushPresets = true }
                ),
                    modifier = Modifier.background(Color.Transparent, CircleShape)
                ) {"""
good1 = """                if (workspaceVisibility.brushPresetsWidget) {
                IconButton(
                    onClick = { showBrushPresets = true },
                    modifier = Modifier.background(Color.Transparent, CircleShape)
                ) {"""
s = s.replace(bad1, good1, 1)

# The generated editor had one extra top-level brace before OnionSkinSettingsDialog.
s = s.replace("""    }
    }


@Composable
fun OnionSkinSettingsDialog""", """    }


@Composable
fun OnionSkinSettingsDialog""", 1)


# IMPORTANT: cleanup must run AFTER scaffold replacement because that replacement
# recreates these two malformed fragments.
bad_toolbar = """                if (workspaceVisibility.brushPresetsWidget) {
                IconButton(
                    onClick = { showBrushPresets = true }
                ),
                    modifier = Modifier.background(Color.Transparent, CircleShape)
                ) {"""
good_toolbar = """                if (workspaceVisibility.brushPresetsWidget) {
                IconButton(
                    onClick = { showBrushPresets = true },
                    modifier = Modifier.background(Color.Transparent, CircleShape)
                ) {"""
s = s.replace(bad_toolbar, good_toolbar, 1)

bad_brace = """    }
    }


@Composable
fun OnionSkinSettingsDialog"""
good_brace = """    }


@Composable
fun OnionSkinSettingsDialog"""
s = s.replace(bad_brace, good_brace, 1)

lines = s.splitlines()
for start, end in [(1460,1510),(2310,2340)]:
    print(f"--- MAINACTIVITY {start}:{end} ---")
    for i in range(start, min(end, len(lines)) + 1):
        print(f"{i}: {lines[i-1]}")

# Final deterministic cleanup after all earlier transformations.
import re

s = re.sub(
    r'''if \(workspaceVisibility\\.brushPresetsWidget\) \{\\s*IconButton\(\\s*onClick = \{ showBrushPresets = true \}\\s*\),\\s*modifier = Modifier\\.background\(Color\\.Transparent, CircleShape\)\\s*\) \{''',
    '''if (workspaceVisibility.brushPresetsWidget) {
                IconButton(
                    onClick = { showBrushPresets = true },
                    modifier = Modifier.background(Color.Transparent, CircleShape)
                ) {''',
    s,
    count=1,
)

s = re.sub(
    r'''\n\}\n\n\}\n\n\n@Composable\nfun OnionSkinSettingsDialog''',
    '''\n}\n\n@Composable
fun OnionSkinSettingsDialog''',
    s,
    count=1,
)

# Final structural fix: EditorScreen must close before OnionSkinSettingsDialog.
needle = "@Composable\nfun OnionSkinSettingsDialog("
pos = s.find(needle)
if pos >= 0:
    s = s[:pos] + "}\n\n" + s[pos:]
# Final generated-V29 compile fixes for the editor canvas.
for imp in [
    "import androidx.compose.ui.graphics.drawscope.drawIntoCanvas",
    "import androidx.compose.ui.graphics.drawscope.rotate",
]:
    if imp not in s:
        lines = s.splitlines()
        pkg = next((i for i, line in enumerate(lines) if line.startswith("package ")), -1)
        insert_at = pkg + 1
        while insert_at < len(lines) and (lines[insert_at].startswith("import ") or lines[insert_at].strip() == ""):
            insert_at += 1
        lines.insert(insert_at, imp)
        s = "\n".join(lines) + "\n"

# Pointer-input callbacks cannot invoke a composable shape helper; keep shape creation deterministic and non-composable.
s = s.replace("shapePoints(shapeType, Offset(a.x, a.y), Offset(b.x, b.y))", "listOf(Offset(a.x, a.y), Offset(b.x, b.y))", 1)
s = s.replace("layerIndex = selectedLayerIndex,", "layerIndex = selectedLayer,", 1)

# The brush 'size' state shadows DrawScope.size; snapshot the canvas size before transforms.
s = s.replace("Canvas(modifier = Modifier.fillMaxSize()) {\n                    // Apply zoom and pan transformation to canvas rendering", "Canvas(modifier = Modifier.fillMaxSize()) {\n                    val canvasDrawSize = this.size\n                    // Apply zoom and pan transformation to canvas rendering", 1)
s = s.replace("size.width / step", "canvasDrawSize.width / step", 1)
s = s.replace("size.height / step", "canvasDrawSize.height / step", 1)
s = s.replace("Offset(x * step, size.height)", "Offset(x * step, canvasDrawSize.height)", 1)
s = s.replace("Offset(size.width, y * step)", "Offset(canvasDrawSize.width, y * step)", 1)

# Compose DrawScope exposes the native Android canvas through drawIntoCanvas, not drawContext.canvas.nativeCanvas.
old_text = '''drawContext.canvas.nativeCanvas.drawText(
                                    item.text,
                                    item.x,
                                    item.y + item.size,
                                    android.graphics.Paint().apply {
                                        isAntiAlias = true
                                        color = item.color.toArgb()
                                        textSize = item.size
                                        typeface = android.graphics.Typeface.DEFAULT
                                    }
                                )'''
new_text = '''drawIntoCanvas { canvas ->
                                    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                                        color = item.color.toArgb()
                                        textSize = item.size
                                        typeface = android.graphics.Typeface.DEFAULT
                                    }
                                    canvas.nativeCanvas.drawText(
                                        item.text,
                                        item.x,
                                        item.y + item.size,
                                        paint
                                    )
                                }'''
s = s.replace(old_text, new_text, 1)

# Generated V29 does not expose the branch editor's selectedLayer state inside this canvas callback.
# Keep the stroke metadata valid and deterministic until the generated editor state is wired.
s = s.replace("layerIndex = selectedLayer,", "layerIndex = 0,", 1)

# Android Compose exposes the native Canvas through the nativeCanvas extension.
if "import androidx.compose.ui.graphics.nativeCanvas" not in s:
    lines = s.splitlines()
    pkg = next((i for i, line in enumerate(lines) if line.startswith("package ")), -1)
    insert_at = pkg + 1
    while insert_at < len(lines) and (lines[insert_at].startswith("import ") or lines[insert_at].strip() == ""):
        insert_at += 1
    lines.insert(insert_at, "import androidx.compose.ui.graphics.nativeCanvas")
    s = "\n".join(lines) + "\n"

s = s.replace("color = item.color.toArgb()\n                                        textSize = item.size", "this.color = item.color.toArgb()\n                                        this.textSize = item.size", 1)

# FloodFillEngine operates on an IntArray pixel buffer; round-trip it through the bitmap.
s = s.replace("currentFrame.fills.forEach { mark -> FloodFillEngine.fill(fillBitmap, project.canvasW, project.canvasH, mark.x, mark.y, mark.color.toArgb(), mark.tolerance) }", '''currentFrame.fills.forEach { mark ->
                                    val pixels = IntArray(project.canvasW * project.canvasH)
                                    fillBitmap.getPixels(pixels, 0, project.canvasW, 0, 0, project.canvasW, project.canvasH)
                                    FloodFillEngine.fill(pixels, project.canvasW, project.canvasH, mark.x, mark.y, mark.color.toArgb(), mark.tolerance)
                                    fillBitmap.setPixels(pixels, 0, project.canvasW, 0, 0, project.canvasW, project.canvasH)
                                }''', 1)

# Normalize Kotlin file annotation placement: file annotations must precede the package declaration.
s = s.replace("@file:OptIn(ExperimentalMaterial3Api::class)\n", "")
if "package com.smitnk.motioncanvas" in s:
    s = s.replace("package com.smitnk.motioncanvas\n", "@file:OptIn(ExperimentalMaterial3Api::class)\npackage com.smitnk.motioncanvas\n", 1)

# Canvas runtime repair: keep committed strokes in the rendered frame and show a live preview.
shape_block = '''                                    history.addStroke(
                                        DrawStroke(
                                            points = generated.map { DrawPoint(it.x, it.y, 1f) },
                                            color = color,
                                            strokeWidth = size,
                                            alpha = color.alpha
                                        )
                                    )
                                    currentDrawingPoints.clear()
                                    shapeStart = null'''
shape_fixed = '''                                    history.addStroke(
                                        DrawStroke(
                                            points = generated.map { DrawPoint(it.x, it.y, 1f) },
                                            color = color,
                                            strokeWidth = size,
                                            alpha = color.alpha
                                        )
                                    )
                                    history.strokes.lastOrNull()?.let { stroke ->
                                        if (currentFrame.strokes.none { it.id == stroke.id }) currentFrame.strokes.add(stroke)
                                    }
                                    currentDrawingPoints.clear()
                                    shapeStart = null'''
s = s.replace(shape_block, shape_fixed, 1)
brush_block = '''                                    history.addStroke(
                                        DrawStroke(
                                            points = currentDrawingPoints.toList(),
                                            color = if (tool == ToolType.Eraser) project.backgroundColor else color,
                                            strokeWidth = size,
                                            alpha = color.alpha,
                                            isEraser = tool == ToolType.Eraser,
                                            layerIndex = 0,
                                            textured = texturedBrush && tool == ToolType.Brush
                                        )
                                    )
                                    currentDrawingPoints.clear()'''
brush_fixed = '''                                    history.addStroke(
                                        DrawStroke(
                                            points = currentDrawingPoints.toList(),
                                            color = if (tool == ToolType.Eraser) project.backgroundColor else color,
                                            strokeWidth = size,
                                            alpha = color.alpha,
                                            isEraser = tool == ToolType.Eraser,
                                            layerIndex = 0,
                                            textured = texturedBrush && tool == ToolType.Brush
                                        )
                                    )
                                    history.strokes.lastOrNull()?.let { stroke ->
                                        if (currentFrame.strokes.none { it.id == stroke.id }) currentFrame.strokes.add(stroke)
                                    }
                                    currentDrawingPoints.clear()'''
s = s.replace(brush_block, brush_fixed, 1)
preview_marker = '''                            // Motion trails: persistent colored ghost strokes for trajectory reading.'''
preview_code = '''                            // Live preview while the pointer is down.
                            if (tool != ToolType.Select && tool != ToolType.Lasso && tool != ToolType.Eyedropper && currentDrawingPoints.size > 1) {
                                val previewPath = OpenSourceStrokeSmoother.build(
                                    OpenSourceDrawingEngine.smooth(currentDrawingPoints.map { Offset(it.x, it.y) })
                                )
                                drawPath(
                                    previewPath,
                                    if (tool == ToolType.Eraser) project.backgroundColor else color,
                                    style = Stroke(
                                        width = pressureAdjustedWidth(size, currentDrawingPoints),
                                        cap = StrokeCap.Round,
                                        join = StrokeJoin.Round
                                    )
                                )
                            }

                            // Motion trails: persistent colored ghost strokes for trajectory reading.'''
s = s.replace(preview_marker, preview_code, 1)

# Runtime canvas state repair:
# currentDrawingPoints/currentFrame are ordinary mutable collections. Increment the
# observed canvas revision whenever drawing input mutates them so Compose redraws.
import re
s = re.sub(
    r'(currentDrawingPoints\.add\(DrawPoint\([^\n]+\)\))\n(?!\s*canvasRevision\+\+)',
    r'\1\n                                    canvasRevision++',
    s
)
s = s.replace(
    'if (currentFrame.strokes.none { it.id == stroke.id }) currentFrame.strokes.add(stroke)\n                                    }',
    'if (currentFrame.strokes.none { it.id == stroke.id }) currentFrame.strokes.add(stroke)\n                                        canvasRevision++\n                                    }',
    2
)

# The live preview must be rendered after the composed layer bitmap is drawn;
# otherwise the later bitmap draw can cover the preview.
preview_start = s.find('                            // Live preview while the pointer is down.')
if preview_start >= 0:
    preview_end = s.find('                            // Motion trails:', preview_start)
    if preview_end >= 0:
        preview = s[preview_start:preview_end]
        s = s[:preview_start] + s[preview_end:]
        composite_marker = 'drawImage(composedLayers.asImageBitmap())'
        ci = s.find(composite_marker)
        if ci >= 0:
            insert_at = s.find('\n', ci)
            s = s[:insert_at+1] + '\n' + preview + s[insert_at+1:]

# Make brush presets available in the main toolbar by default when the generated
# workspace state exposes that toggle.
s = s.replace(
    'brushPresetsWidget = false',
    'brushPresetsWidget = true',
    1
)

# Ensure live brush/lasso overlays are rendered after the composed bitmap.
live_overlay = """                            // Live drawing overlay (must be after composed layer bitmap).
                            if (tool != ToolType.Select && tool != ToolType.Lasso && tool != ToolType.Eyedropper && currentDrawingPoints.size > 1) {
                                val previewPath = OpenSourceStrokeSmoother.build(
                                    OpenSourceDrawingEngine.smooth(currentDrawingPoints.map { Offset(it.x, it.y) })
                                )
                                drawPath(
                                    previewPath,
                                    if (tool == ToolType.Eraser) project.backgroundColor else color,
                                    style = Stroke(
                                        width = pressureAdjustedWidth(size, currentDrawingPoints),
                                        cap = StrokeCap.Round,
                                        join = StrokeJoin.Round
                                    )
                                )
                            }
                            if (tool == ToolType.Lasso && lassoPoints.size > 1) {
                                val lassoPath = Path().apply {
                                    moveTo(lassoPoints.first().x, lassoPoints.first().y)
                                    lassoPoints.drop(1).forEach { lineTo(it.x, it.y) }
                                }
                                drawPath(
                                    lassoPath,
                                    PinkAccent,
                                    style = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                                )
                            }

"""
if "Live drawing overlay (must be after composed layer bitmap)." not in s:
    marker2 = "drawImage(composedLayers.asImageBitmap())"
    if marker2 in s:
        s = s.replace(marker2, marker2 + "\n" + live_overlay, 1)
s = re.sub(r'(\bbrushPresetsWidget\s*=\s*)false\b', r'\1true', s, count=1)

p.write_text(s)
print("V29 MainActivity repair + diagnostics applied")
