# SketchOnTop

Epic Pen-style screen annotation app for Android with full stylus/S Pen support.

## Features

- **Transparent Overlay**: Draw on top of any screen content
- **Drawing Tools**:
  - Pen: Solid strokes with adjustable width
  - Highlighter: Semi-transparent strokes
  - Eraser: Clear mode erasing
- **Stylus/S Pen Support**:
  - Accessibility-powered S Pen input on Android 14+
  - Finger navigation while S Pen draws, when accessibility mode is enabled
  - Pressure sensitivity for dynamic stroke width
  - S Pen button → temporary eraser mode
  - Tool type detection (stylus vs finger)
  - Optional stylus-only mode
- **Actions**: Undo, Redo, Clear, Close
- **Color Picker**: 8 preset colors
- **Stroke Width Slider**: Adjustable line thickness

## S Pen Features

- **Best S Pen Mode**: On Android 14+, SketchOnTop can use an Accessibility Service to read S Pen motion events directly. This lets finger touches pass through to the app underneath while the S Pen draws on top.
- **Pressure Sensitivity**: Stroke width varies based on pen pressure
- **Button Eraser**: Hold the S Pen side button while drawing to temporarily erase
- **Eraser End**: Use the eraser end of the stylus to erase (if supported)
- **Stylus Priority**: Optional mode to ignore finger input when using S Pen

## Permissions

SketchOnTop needs Android's **Display over other apps** permission for the drawing overlay.

For the best S Pen experience on Android 14+, also enable the SketchOnTop Accessibility Service:

1. Open SketchOnTop.
2. Tap **Open Accessibility Settings**.
3. Enable **SketchOnTop**.
4. Return to the app and start the overlay.
5. Turn on S Pen mode from the toolbar.

If Android shows **Controlled by Restricted Setting** for a sideloaded APK, allow the restricted setting with ADB:

```bash
adb shell cmd appops set com.sketchontop ACCESS_RESTRICTED_SETTINGS allow
```

Then reopen Accessibility settings and enable SketchOnTop.

## Requirements

- Android 7.0 (API 24) or higher
- Android Studio Hedgehog (2023.1.1) or newer
- Kotlin 1.9.x

## Building

```bash
# Debug build
./gradlew assembleDebug

# Install on connected device
./gradlew installDebug
```

## Project Structure

```
app/src/main/
├── kotlin/com/sketchontop/
│   ├── MainActivity.kt          # Permission and settings Activity
│   ├── OverlayService.kt        # Overlay windows and toolbar
│   ├── StylusSensorService.kt   # Android 14+ accessibility stylus input
│   ├── DrawingView.kt           # Custom drawing View with stylus support
│   └── models/
│       └── Stroke.kt            # Stroke data model
└── res/
    ├── layout/
    │   └── activity_main.xml
    ├── drawable/
    │   └── (icons and backgrounds)
    └── values/
        ├── colors.xml
        ├── strings.xml
        └── themes.xml
```

## Future Enhancements

- [ ] Samsung S Pen Remote SDK for Air Actions
- [ ] Save/export drawings
- [ ] More brush effects using tilt/orientation
- [ ] Custom color picker
- [ ] System overlay mode (requires SYSTEM_ALERT_WINDOW permission)

## License

MIT License
