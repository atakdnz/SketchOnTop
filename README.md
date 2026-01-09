# SketchOnTop

Epic Pen-style screen annotation app for Android with full stylus/S Pen support.

## Features

- **Transparent Overlay**: Draw on top of any screen content
- **Drawing Tools**:
  - Pen: Solid strokes with adjustable width
  - Highlighter: Semi-transparent strokes
  - Eraser: Clear mode erasing
- **Stylus/S Pen Support**:
  - Pressure sensitivity for dynamic stroke width
  - S Pen button → temporary eraser mode
  - Tool type detection (stylus vs finger)
  - Optional stylus-only mode
- **Actions**: Undo, Redo, Clear, Close
- **Color Picker**: 8 preset colors
- **Stroke Width Slider**: Adjustable line thickness

## S Pen Features

- **Pressure Sensitivity**: Stroke width varies based on pen pressure
- **Button Eraser**: Hold the S Pen side button while drawing to temporarily erase
- **Eraser End**: Use the eraser end of the stylus to erase (if supported)
- **Stylus Priority**: Optional mode to ignore finger input when using S Pen

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
│   ├── MainActivity.kt      # Transparent overlay Activity
│   ├── DrawingView.kt       # Custom drawing View with stylus support
│   └── models/
│       └── Stroke.kt        # Stroke data class (path + paint)
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
