from pathlib import Path
import re

p = Path("build-source/app/src/main/java/com/smitnk/motioncanvas/MainActivity.kt")
s = p.read_text()

# Keep the existing editor implementation, but make the generated V29 source
# structurally valid after the workspace-visibility patch has been applied.
s = s.replace("\nfun EditorScreen(\n", "\n@Composable\nfun EditorScreen(\n", 1)

# Repair the malformed IconButton calls produced by the older visibility edit.
s = s.replace(
    'IconButton(onClick = { imagePicker.launch("image/*") }\n                    }) {',
    'IconButton(onClick = { imagePicker.launch("image/*") }) {'
)
s = s.replace(
    'IconButton(onClick = { showFrameTools = true }\n                    }) {',
    'IconButton(onClick = { showFrameTools = true }) {'
)
s = s.replace(
    'IconButton(onClick = { showAudioDialog = true }\n                    }) {',
    'IconButton(onClick = { showAudioDialog = true }) {'
)
s = s.replace(
    'IconButton(onClick = { showAdvancedPanel = true }\n                    }) {',
    'IconButton(onClick = { showAdvancedPanel = true }) {'
)
s = s.replace(
    'IconButton(onClick = { showProTools = true }\n                    }) {',
    'IconButton(onClick = { showProTools = true }) {'
)

# A previous edit left the modifier outside IconButton's argument list.
s = s.replace(
    '''IconButton(
                    onClick = { showBrushPresets = true }
                ),
                    modifier = Modifier.background(Color.Transparent, CircleShape)
                ) {''',
    '''IconButton(
                    onClick = { showBrushPresets = true },
                    modifier = Modifier.background(Color.Transparent, CircleShape)
                ) {'''
)

# Remove an unmatched workspace-visibility wrapper if it is still present in
# the generated top bar. The visibility checks themselves remain intact.
s = s.replace(
    '''        topBar = {
            if (workspaceVisibility.topBar) {
            TopAppBar(''',
    '''        topBar = {
            TopAppBar(''', 1
)

# Compose Material3 APIs used by the unchanged MotionCanvas UI are experimental
# with the pinned Compose BOM; opt in rather than removing those controls.
if "@OptIn(ExperimentalMaterial3Api::class)" not in s:
    s = s.replace("@Composable\nfun EditorScreen(", "@OptIn(ExperimentalMaterial3Api::class)\n@Composable\nfun EditorScreen(", 1)

p.write_text(s)
print("V29 MainActivity targeted syntax repair applied")
