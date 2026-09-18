# MotionCanvas Android — Feature Audit

Date: 2026-09-18

## Main branch
373bcb18cc2a56d433e023aa78327e5748e7f53f

## Integrated in main
- Android/Compose app shell
- Vector drawing
- Pencil, Pen, Marker, Airbrush
- Brush size/opacity/pressure/stabilization/spacing/taper
- Raster brush and eraser
- Layers
- Animation frames, holds and playback
- FPS and onion skin
- Timeline foundation
- PNG/GIF export paths
- Project save/load
- Color picker and eyedropper
- Line/rectangle/ellipse/quick-shape
- Fill
- Mirror/radial symmetry
- Grid and perspective-guide foundation
- Alpha lock
- Node/Bézier editing
- Weight Paint
- Sculpt
- Select, additive selection and lasso
- Transform box/handles
- Delete and duplicate selected strokes
- Flip H/V
- Transform ratio/rotation-snap controls

## Feature branches only — not proven integrated into main
- PR #35: draggable transform pivot
- PR #36: true pivot-based rotation
- PR #33: Bézier-safe flip correction
- PR #32: improved lasso intersection
- PR #25+: Select precision improvements
- PR #8: real clipping renderer
- PR #10: advanced perspective-guide interaction
- PR #11: geometry snapping
- PR #12: precision ruler engine
- PR #22: professional transform quality
- PR #23: transform pivot/flip actions

## Build verification
The latest PR #36 workflow failed during **Set up Android SDK**, before the Gradle assembleDebug step. This is a CI setup failure at the observed step, not proof that the Kotlin changes compile or fail.

No latest transform APK is currently build-verified.

## Integration order
1. Establish a clean buildable baseline.
2. Fix/verify CI and produce a debug APK.
3. Install/test that APK on Android.
4. Consolidate Select/Transform improvements into one coherent feature set.
5. Consolidate clipping, snapping/ruler and advanced perspective work.
6. Continue major feature development only after build verification.

## Definitions
- **Integrated:** present in main and reachable from the app.
- **Feature branch only:** implementation exists elsewhere but is not part of main.
- **Verified:** successful build/test evidence exists.