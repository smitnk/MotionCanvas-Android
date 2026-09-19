from pathlib import Path

p = Path("build-source/app/src/main/java/com/smitnk/motioncanvas/MainActivity.kt")
s = p.read_text()

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
    before = s[:pos]
    if not before.rstrip().endswith("}"):
        s = before.rstrip() + "\n}\n\n" + s[pos:]
p.write_text(s)
print("V29 MainActivity repair + diagnostics applied")
