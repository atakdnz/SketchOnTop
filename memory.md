# SketchOnTop - Project Memory

## Overview
Android overlay drawing app that allows drawing on top of any screen. Supports S Pen with pressure sensitivity.

## Current Status: Working ✅
Last updated: 2026-01-09

## Key Features (Working)
- 🖊️ Draw on top of any screen (system overlay)
- 📱 Draw mode toggle (on/off)
- ✒️ S Pen mode (stylus draws, fingers pass through to OS)
- ✏️ Tools: Pen, Highlighter, Eraser
- 🎨 Color picker page (9 colors + rainbow slider)
- 📏 Per-tool stroke width
- ↩️ Undo/Redo
- 🖌️ S Pen pressure sensitivity
- ⚙️ Settings button (opens MainActivity)
- 🚀 Auto-launch overlay on app icon tap

## Known Issues / In Progress

### 1. Brush Preview Size Mismatch
- **Issue**: The brush preview circle in the toolbar doesn't match the actual drawing size
- **Location**: `OverlayService.kt` → `updateBrushPreview()`
- **Current behavior**: Preview size is clamped between 6dp-36dp and uses raw strokeWidth as pixels
- **Expected**: Should scale proportionally to match actual stroke appearance

### 2. Gradient Drawing (Not Implemented)
- **Goal**: Path-based repeating rainbow gradient that follows the stroke
- **Current state**: Gradient mode exists but draws single color (color changes between strokes, not within)
- **Implementation needed**: 
  - Store gradient strokes as multiple small segments with different colors
  - Or use shader-based approach with path-following gradient
- **User preference**: Path-based (color based on distance drawn), repeating rainbow
- **Location**: `DrawingView.kt` → gradient-related code around lines 100-135

### 3. Custom Gradient Creator
- **Goal**: Let users create custom gradients
- **Status**: Not started - do after gradient drawing works

## Architecture

### Key Files
- `OverlayService.kt` - Main service, manages overlay windows and UI
- `DrawingView.kt` - Custom View handling touch/stylus input and drawing
- `MainActivity.kt` - Permission handling, auto-launches overlay
- `overlay_toolbar.xml` - Toolbar layout with color picker page
- `overlay_canvas.xml` - Canvas layout
- `Stroke.kt` - Data class for stroke persistence

### Two-Window Architecture
1. **Canvas Window** - Full screen, receives touch for drawing
2. **Toolbar Window** - Floating, always touchable

### S Pen Mode Implementation
- Uses dynamic `FLAG_NOT_TOUCHABLE` toggle
- When finger detected in S Pen mode → canvas becomes non-touchable for 2 seconds
- Allows subsequent finger touches to pass through to OS

## GitHub
- Repo: https://github.com/atakdnz/SketchOnTop
- Latest release: v1.1.1

## Build
```bash
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

## Permissions Required
- `SYSTEM_ALERT_WINDOW` - Display over other apps
- `FOREGROUND_SERVICE` - Keep overlay stable
