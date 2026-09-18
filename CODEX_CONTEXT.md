# MotionCanvas-Android — Codex Project Context

## Project
Native Android 2D drawing and animation app.

Repository: smitnk/MotionCanvas-Android
Default branch: main
Current build/development branch: feature/build-apk-verification
Package: com.smitnk.motioncanvas

## User workflow
- User works mainly from Android; do not assume PC/Android Studio access.
- When possible, work directly in GitHub.
- Do not merge PRs unless explicitly requested.
- Before code changes: inspect relevant source and current CI errors first.
- Classify verification as NEW / STUCK / PASSED.
- Make the smallest coherent fix, then verify with GitHub CI.
- Never claim build success without actual CI/build verification.
- Build success proves compilation, not runtime UI behavior.

## Stack
- Kotlin 2.1.21
- Jetpack Compose
- AGP 8.7.3
- Compose BOM 2024.12.01
- activity-compose 1.10.0
- compileSdk 35
- minSdk 26
- targetSdk 35
- Java/JVM 17
- Gradle 8.9

## Latest verified state
Build #89:
- Commit: 828c75514f8339dd9a54c5d064a659f6a9a0d67d
- Status: PASSED
- Debug APK generated.
- This was CI verification only; runtime appearance still needs device testing.

## Current known issue
The main unresolved user-facing issue is canvas visibility/layout. Earlier APK screenshots showed the UI dominated by controls and no clearly visible drawing canvas.

Recent changes:
- settings area made scrollable/constrained
- canvas given weighted remaining space
- explicit gray workspace added
- white 1600x1200 artboard with dark border added

If canvas is still not visible, inspect composable nesting and measurement before changing code. Check:
- parent Column measurement
- fixed versus weighted heights
- actual Canvas height
- nested Rows/Columns consuming space
- onSizeChanged/canvasSize
- artboard coordinates
- alignment between explicit artboard and transformed drawing content

A robust layout should guarantee a substantial weighted canvas region while keeping controls/timeline bounded.

## Main integrated features
- Vector drawing
- Pencil, Pen, Marker, Airbrush
- Brush size/opacity/pressure/stabilization/spacing/taper
- Raster brush and eraser
- Layers
- Animation frames, holds, playback
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
- Select, additive selection, lasso
- Transform box/handles
- Delete/duplicate selected strokes
- Flip H/V
- Multi-joint puppet rigging
- Third-party licensing notice

## Branches/features requiring state verification
Do not assume these are merged:
- draggable transform pivot
- true pivot-based rotation
- Bézier-safe flip correction
- improved lasso intersection
- select precision improvements
- real clipping renderer
- advanced perspective-guide interaction
- geometry snapping
- precision ruler engine
- professional transform quality
- transform pivot/flip actions

## Important technical notes
Raster/artboard size: 1600 x 1200.

Stroke contains:
- points
- optional inHandles/outHandles
- optional pressures
- color
- width
- opacity
- closed
- filled

drawStroke is a top-level DrawScope helper supporting polyline/cubic Bézier rendering.

Perspective guide supports horizon, vanishing points, snapping, nearest point and moving points/horizon.

Geometry snapping supports perspective snapping.

Precision ruler supports angle and grid snapping.

Clipping model has clipToBelow. A real offscreen PorterDuff DST_IN renderer exists on feature/real-clipping-renderer; it clips a layer to the immediately previous layer.

Node/Bézier editor is currently limited: polyline-oriented, no full smooth/corner toggle, no true linked handles, limited curvature editing.

Weight Paint is a simple two-joint system.

Sculpt tools: Grab, Push, Smooth, Pinch, Thickness.

Color picker is an MVP with controls/sliders, not a full 2D SV square.

Timeline supports LOOP, ONCE, PING_PONG, frame, playing, direction, FPS and speed. GIF dependency: com.squareup:gifencoder:0.10.1.

## Important build-history fixes
- ffde81f053d4f800ae6d40e07ee0190ad45cf743 — JVM target mismatch fix
- ccd13cf5c768720c9a4d2d9795bcdc0be20665f9 — DrawScope transform changes
- cb4ce09b88f7edd7bfa1bdc56edcb018ec83b64d — brace/scope correction
- aba1997afb4f5b7390abe01bcf5f2613dc5ddcc0 — MotionCanvasApp closure correction
- 788474007dc4618593c682671a14d51bef37b3d3 — Canvas closure moved so composables are outside Canvas lambda
- f85491ffd35b378047d657499d6c5c79f58eb354 — settings/canvas layout adjustment
- e50044830319a37a6c29b28f4c735e44b0a3919a — verticalScroll import
- 828c75514f8339dd9a54c5d064a659f6a9a0d67d — explicit artboard/workspace; Build #89 PASSED

## Transform caveat
True pivot rotation work may have a cumulative-delta bug if the full rotation delta is repeatedly applied to already-transformed strokes. Robust implementation should capture original selected strokes at drag start and transform from that source state, or apply only incremental delta.

Verify whether TransformSelectionOverlay is actually used before modifying it; active transforms may be inline.

## Open-source reuse rules
When using third-party repositories:
1. No license restrictions: reuse may be possible.
2. Permissive license: copy/modify while preserving required license/copyright/NOTICE.
3. Major licensing incompatibility: use as reference and write original code.
Always verify the current license before copying source and update THIRD_PARTY_NOTICES.md when source is bundled.

Known references:
- SmartToolFactory/Compose-Drawing-App — MIT
- SmartToolFactory/Compose-Image — permissive/reference
- BennyKok/PxerStudio — Apache-2.0
- Korilakkuma/CanvasView — MIT
- meaayu/DroidCanvas — MIT
- vyukti13/SnappyDrawingApp — snapping/ruler reference
- ruochao/cidrawing — Apache-2.0 reference
- artistso/socreate — verify license before reuse

## CI verification procedure
For every relevant commit:
1. Fetch workflow runs.
2. Identify Android Build run.
3. Inspect job status.
4. Inspect compiler/build logs if failed.
5. Classify errors NEW / STUCK / PASSED compared with the preceding relevant build.
6. Confirm debug APK artifact exists after success.

## Working principle
Keep the app buildable and functional after each coherent change. Preserve existing functionality when restructuring UI or fixing features. Prefer coherent feature branches/PRs over unnecessary micro-PRs.
