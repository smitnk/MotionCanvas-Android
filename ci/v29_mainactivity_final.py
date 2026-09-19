from pathlib import Path

p = Path("build-source/app/src/main/java/com/smitnk/motioncanvas/MainActivity.kt")
s = p.read_text()
s = s.replace("\nfun EditorScreen(\n", "\n@Composable\nfun EditorScreen(\n", 1)

editor = s.index("@Composable\nfun EditorScreen(")
start = s.index("    Scaffold(\n", editor)
end = s.index("        bottomBar = {", start)

topbar = '''    Scaffold(
        containerColor = AppBackground,
        topBar = {
            if (workspaceVisibility.topBar) {
                TopAppBar(
                    title = { Text(project.name, color = White, fontSize = 16.sp) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = White)
                        }
                    },
                    actions = {
                        IconButton(onClick = { zoomPanState.zoomOut() }) {
                            Icon(Icons.Default.ZoomOut, contentDescription = "Zoom Out", tint = White)
                        }
                        TextButton(onClick = { zoomPanState.reset() }) {
                            Text(
                                zoomPanState.zoomPercent.toString() + "%",
                                color = if (zoomPanState.zoom != 1f || zoomPanState.pan != Offset.Zero) PinkAccent else White,
                                fontSize = 12.sp
                            )
                        }
                        IconButton(onClick = { zoomPanState.zoomIn() }) {
                            Icon(Icons.Default.ZoomIn, contentDescription = "Zoom In", tint = White)
                        }
                        if (zoomPanState.zoom != 1f || zoomPanState.pan != Offset.Zero) {
                            IconButton(onClick = { zoomPanState.reset() }) {
                                Icon(Icons.Default.RestartAlt, contentDescription = "Reset View", tint = PinkAccent)
                            }
                        }
                        IconButton(onClick = { onionSkinState.toggle() }) {
                            Icon(Icons.Default.Layers, contentDescription = "Toggle Onion Skin", tint = if (onionSkinState.enabled) PinkAccent else TextSecondary.copy(alpha = 0.5f))
                        }
                        IconButton(onClick = { showOnionDialog = true }) {
                            Icon(Icons.Default.Tune, contentDescription = "Onion Skin Settings", tint = if (onionSkinState.enabled) PinkAccent else TextSecondary.copy(alpha = 0.5f))
                        }
                        IconButton(onClick = { history.undo() }, enabled = history.canUndo) {
                            Icon(Icons.Default.Undo, contentDescription = "Undo", tint = if (history.canUndo) White else TextSecondary.copy(alpha = 0.35f))
                        }
                        IconButton(onClick = { history.redo() }, enabled = history.canRedo) {
                            Icon(Icons.Default.Redo, contentDescription = "Redo", tint = if (history.canRedo) White else TextSecondary.copy(alpha = 0.35f))
                        }
                        IconButton(onClick = onOpenTimeline) {
                            Icon(Icons.Default.ViewCarousel, contentDescription = "Timeline", tint = PinkAccent)
                        }
                        IconButton(onClick = onOpenLayers) {
                            Icon(Icons.Default.Layers, contentDescription = "Layers", tint = White)
                        }
                        if (workspaceVisibility.referenceWidget) {
                            IconButton(onClick = { imagePicker.launch("image/*") }) {
                                Icon(Icons.Default.Image, contentDescription = "Reference image", tint = if (referenceBitmap != null) PinkAccent else White)
                            }
                            IconButton(onClick = { showReferenceDialog = true }) {
                                Icon(Icons.Default.Tune, contentDescription = "Reference transform", tint = White)
                            }
                            FilterChip(
                                selected = referenceEditMode,
                                onClick = { if (referenceBitmap != null) referenceEditMode = !referenceEditMode },
                                label = { Text("Ref Edit") }
                            )
                        }
                        if (workspaceVisibility.frameToolsWidget) {
                            IconButton(onClick = { showFrameTools = true }) {
                                Icon(Icons.Default.Flag, contentDescription = "Frame tools", tint = White)
                            }
                        }
                        if (workspaceVisibility.audioWidget) {
                            IconButton(onClick = { showAudioDialog = true }) {
                                Icon(Icons.Default.Mic, contentDescription = "Voice recording", tint = if (isRecording) PinkAccent else White)
                            }
                        }
                        if (workspaceVisibility.advancedWidget) {
                            IconButton(onClick = { showAdvancedPanel = true }) {
                                Icon(Icons.Default.Tune, contentDescription = "Advanced animation tools", tint = White)
                            }
                        }
                        if (workspaceVisibility.proToolsWidget) {
                            IconButton(onClick = { showProTools = true }) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = "Pro tools", tint = PinkAccent)
                            }
                        }
                        IconButton(onClick = onOpenMore) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More tools / show-hide widgets", tint = PinkAccent)
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings", tint = White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = AppBackground)
                )
            }
        },
'''
s = s[:start] + topbar + s[end:]

needle = "\n}\n\n\n}\n\n@Composable\nfun OnionSkinSettingsDialog("
if needle in s:
    s = s.replace(needle, "\n}\n\n@Composable\nfun OnionSkinSettingsDialog(", 1)

p.write_text(s)
print("V29 MainActivity final syntax repair applied")
