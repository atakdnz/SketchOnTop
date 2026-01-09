package com.sketchontop

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.sketchontop.models.Stroke

/**
 * Custom View for drawing on a transparent overlay.
 * Supports stylus/S Pen with pressure sensitivity and button-based eraser mode.
 */
class DrawingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // ================================
    // Tool Types
    // ================================
    enum class Tool {
        PEN,
        HIGHLIGHTER,
        ERASER
    }

    // ================================
    // Drawing State
    // ================================
    
    /** Off-screen bitmap to store drawn content */
    private var bitmap: Bitmap? = null
    
    /** Canvas for the off-screen bitmap */
    private var bitmapCanvas: Canvas? = null
    
    /** List of completed strokes for undo functionality */
    private val strokes = mutableListOf<Stroke>()
    
    /** Stack of undone strokes for redo functionality */
    private val redoStack = mutableListOf<Stroke>()
    
    /** Currently in-progress stroke path */
    private var currentPath: Path? = null
    
    /** Paint for the current stroke */
    private val currentPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.BLACK
        strokeWidth = 10f
    }
    
    /** Paint for rendering completed strokes and bitmap */
    private val bitmapPaint = Paint(Paint.DITHER_FLAG)

    // ================================
    // Tool Settings
    // ================================
    
    /** Currently selected tool */
    private var currentTool: Tool = Tool.PEN
    
    /** Per-tool stroke widths - each tool remembers its own size */
    private val toolStrokeWidths = mutableMapOf(
        Tool.PEN to 8f,
        Tool.HIGHLIGHTER to 20f,
        Tool.ERASER to 30f
    )
    
    /** Current drawing color (solid color) */
    private var currentColor: Int = Color.BLACK
    
    /** Alpha value for highlighter (0-255) */
    private val highlighterAlpha: Int = 80
    
    /** Whether drawings are currently visible */
    private var drawingsVisible: Boolean = true
    
    // ================================
    // Drawing Mode Controls
    // ================================
    
    /** Whether drawing is enabled (false = touches pass through to phone) */
    var drawingEnabled: Boolean = true
    
    /** S Pen only mode: fingers pass through, only stylus can draw */
    var sPenOnlyMode: Boolean = false
    
    /** Callback when finger touch is detected in S Pen mode - used for dynamic FLAG toggle */
    var onFingerTouchDetected: (() -> Unit)? = null
    
    /** Track if a stylus has ever been detected on this device */
    private var stylusDetected: Boolean = false
    
    /** Track temporary eraser mode (when S Pen button is pressed) */
    private var temporaryEraserMode: Boolean = false
    
    /** Store the tool before temporary eraser mode was activated */
    private var toolBeforeTemporaryEraser: Tool = Tool.PEN
    
    // ================================
    // Gradient Brush System
    // ================================
    
    /** Whether to use gradient brush instead of solid color */
    var gradientBrushEnabled: Boolean = false
    
    /** Currently selected gradient preset */
    var currentGradient: GradientPreset = GradientPreset.RAINBOW
    
    /** Custom gradient colors (for custom mode) */
    var customGradientColors: IntArray = intArrayOf(Color.RED, Color.BLUE)
    
    /** Gradient presets */
    enum class GradientPreset(val colors: IntArray, val displayName: String) {
        RAINBOW(intArrayOf(0xFFFF0000.toInt(), 0xFFFF7F00.toInt(), 0xFFFFFF00.toInt(), 
                          0xFF00FF00.toInt(), 0xFF0000FF.toInt(), 0xFF8B00FF.toInt()), "Rainbow"),
        FIRE(intArrayOf(0xFFFFFF00.toInt(), 0xFFFF7F00.toInt(), 0xFFFF0000.toInt()), "Fire"),
        OCEAN(intArrayOf(0xFF00FFFF.toInt(), 0xFF0080FF.toInt(), 0xFF0000FF.toInt()), "Ocean"),
        SUNSET(intArrayOf(0xFFFF6B6B.toInt(), 0xFFFFE66D.toInt(), 0xFFFF8E53.toInt()), "Sunset"),
        FOREST(intArrayOf(0xFF2D5016.toInt(), 0xFF4A7C23.toInt(), 0xFF8BC34A.toInt()), "Forest"),
        NEON(intArrayOf(0xFFFF00FF.toInt(), 0xFF00FFFF.toInt(), 0xFFFF00FF.toInt()), "Neon"),
        CUSTOM(intArrayOf(Color.RED, Color.BLUE), "Custom")
    }
    
    /** Distance traveled for gradient color cycling */
    private var gradientDistance: Float = 0f
    
    /** Gradient cycle length in pixels */
    private val gradientCycleLength: Float = 200f

    // ================================
    // Touch Tracking
    // ================================
    
    /** Last X coordinate for smooth drawing */
    private var lastX: Float = 0f
    
    /** Last Y coordinate for smooth drawing */
    private var lastY: Float = 0f

    // ================================
    // Initialization
    // ================================
    
    init {
        // Make the view focusable to receive touch events
        isFocusable = true
        isFocusableInTouchMode = true
        
        // Set layer type to software for proper xfermode support (eraser)
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        
        // Create or recreate the off-screen bitmap when size changes
        if (w > 0 && h > 0) {
            bitmap?.recycle()
            bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bitmapCanvas = Canvas(bitmap!!)
            
            // Redraw all strokes onto the new bitmap
            redrawAllStrokes()
        }
    }

    // ================================
    // Drawing
    // ================================
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // Only draw if visibility is enabled
        if (!drawingsVisible) return
        
        // Draw the off-screen bitmap (contains all completed strokes)
        bitmap?.let { canvas.drawBitmap(it, 0f, 0f, bitmapPaint) }
        
        // Draw the current in-progress stroke
        currentPath?.let { path ->
            canvas.drawPath(path, currentPaint)
        }
    }
    
    /**
     * Redraws all strokes onto the off-screen bitmap.
     * Called after undo/redo or when bitmap is recreated.
     */
    private fun redrawAllStrokes() {
        bitmapCanvas?.let { canvas ->
            // Clear the bitmap with transparent color
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
            
            // Redraw all strokes
            for (stroke in strokes) {
                canvas.drawPath(stroke.path, stroke.paint)
            }
        }
    }

    // ================================
    // Touch Handling
    // ================================
    
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val pointerIndex = event.actionIndex
        val toolType = event.getToolType(pointerIndex)
        
        // --------------------------------
        // Stylus Detection
        // --------------------------------
        // Check if this is a stylus input
        val isStylus = toolType == MotionEvent.TOOL_TYPE_STYLUS
        val isStylusEraser = toolType == MotionEvent.TOOL_TYPE_ERASER
        val isFinger = toolType == MotionEvent.TOOL_TYPE_FINGER
        
        if (isStylus || isStylusEraser) {
            stylusDetected = true
        }
        
        // --------------------------------
        // Drawing Mode Checks
        // --------------------------------
        
        // If drawing is completely disabled, pass all touches through
        if (!drawingEnabled) {
            // Exception: if sPenOnlyMode is on and this is a stylus, still allow drawing
            if (!(sPenOnlyMode && (isStylus || isStylusEraser))) {
                return false  // Pass through to underlying app
            }
        }
        
        // S Pen only mode: allow stylus to draw, fingers pass through
        if (sPenOnlyMode && isFinger) {
            // Notify on ACTION_DOWN so OverlayService can toggle FLAG_NOT_TOUCHABLE
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                onFingerTouchDetected?.invoke()
            }
            return false  // Finger touches pass through to use the phone
        }
        
        // --------------------------------
        // S Pen Button Detection
        // --------------------------------
        // Check if stylus button is pressed (S Pen side button)
        // BUTTON_STYLUS_PRIMARY is the typical side button
        // BUTTON_STYLUS_SECONDARY is sometimes used for a second button
        val isStylusButtonPressed = (event.buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY) != 0 ||
                                    (event.buttonState and MotionEvent.BUTTON_STYLUS_SECONDARY) != 0
        
        // --------------------------------
        // Determine Effective Tool
        // --------------------------------
        // Priority: 1) Stylus eraser end, 2) S Pen button pressed, 3) Selected tool
        val effectiveTool = when {
            isStylusEraser -> Tool.ERASER  // Physical eraser end of stylus
            isStylusButtonPressed && isStylus -> Tool.ERASER  // S Pen button = temporary eraser
            else -> currentTool
        }
        
        // Track temporary eraser mode for proper stroke handling
        if (isStylusButtonPressed && isStylus && !temporaryEraserMode) {
            temporaryEraserMode = true
            toolBeforeTemporaryEraser = currentTool
        } else if (!isStylusButtonPressed && temporaryEraserMode) {
            temporaryEraserMode = false
        }
        
        // Get coordinates
        val x = event.x
        val y = event.y
        
        // --------------------------------
        // Pressure Sensitivity
        // --------------------------------
        // Read pressure value (0.0 to 1.0, with some devices going higher)
        val pressure = event.pressure.coerceIn(0f, 1f)
        
        // Get base width for the effective tool
        val baseWidth = toolStrokeWidths[effectiveTool] ?: 10f
        
        // Map pressure to stroke width:
        // At pressure 0: use 50% of base width
        // At pressure 1: use 100% of base width
        val dynamicWidth = if (isStylus || isStylusEraser) {
            baseWidth * (0.5f + pressure * 0.5f)
        } else {
            baseWidth  // No pressure adjustment for finger input
        }
        
        // --------------------------------
        // Optional: Tilt and Orientation (for future brush effects)
        // --------------------------------
        // val tilt = event.getAxisValue(MotionEvent.AXIS_TILT)
        // val orientation = event.orientation
        // These could be used to create calligraphy-style brushes
        
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // Request unbuffered dispatch for lower latency stylus input
                requestUnbufferedDispatch(event)
                
                // Start a new stroke
                startStroke(x, y, dynamicWidth, effectiveTool)
                return true
            }
            
            MotionEvent.ACTION_MOVE -> {
                // Process all historical points for smoother lines
                val historySize = event.historySize
                for (i in 0 until historySize) {
                    val historicalX = event.getHistoricalX(i)
                    val historicalY = event.getHistoricalY(i)
                    continueStroke(historicalX, historicalY)
                }
                
                // Process current point
                continueStroke(x, y)
                
                // Update stroke width based on pressure (for variable-width strokes)
                currentPaint.strokeWidth = dynamicWidth
                
                invalidate()
                return true
            }
            
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                // Finish the stroke
                finishStroke()
                return true
            }
        }
        
        return super.onTouchEvent(event)
    }
    
    /**
     * Starts a new stroke at the given coordinates.
     */
    private fun startStroke(x: Float, y: Float, strokeWidth: Float, tool: Tool) {
        // Clear redo stack when a new stroke is started
        redoStack.clear()
        
        // Reset gradient distance for new stroke
        gradientDistance = 0f
        
        // Create a new path
        currentPath = Path().apply {
            moveTo(x, y)
        }
        
        // Configure paint based on tool
        configurePaintForTool(tool, strokeWidth)
        
        // Store last position
        lastX = x
        lastY = y
        
        invalidate()
    }
    
    /**
     * Continues the current stroke to the given coordinates.
     * Uses quadratic bezier curves for smooth lines.
     * Also updates gradient color if gradient brush is enabled.
     */
    private fun continueStroke(x: Float, y: Float) {
        currentPath?.let { path ->
            // Calculate distance traveled
            val dx = x - lastX
            val dy = y - lastY
            val distance = kotlin.math.sqrt(dx * dx + dy * dy)
            gradientDistance += distance
            
            // Update gradient color if enabled and not eraser
            if (gradientBrushEnabled && currentTool != Tool.ERASER) {
                val gradientColor = getGradientColorAtDistance(gradientDistance)
                currentPaint.color = gradientColor
                if (currentTool == Tool.HIGHLIGHTER) {
                    currentPaint.alpha = highlighterAlpha
                }
            }
            
            // Use quadratic bezier for smoother curves
            // The control point is the midpoint between last and current position
            val midX = (lastX + x) / 2
            val midY = (lastY + y) / 2
            path.quadTo(lastX, lastY, midX, midY)
            
            lastX = x
            lastY = y
        }
    }
    
    /**
     * Gets the gradient color at a given distance along the stroke.
     */
    private fun getGradientColorAtDistance(distance: Float): Int {
        val colors = if (currentGradient == GradientPreset.CUSTOM) {
            customGradientColors
        } else {
            currentGradient.colors
        }
        
        if (colors.size < 2) return colors.firstOrNull() ?: currentColor
        
        // Calculate position in gradient cycle (0 to 1)
        val cyclePosition = (distance / gradientCycleLength) % 1f
        
        // Find which two colors to interpolate between
        val scaledPosition = cyclePosition * (colors.size - 1)
        val index1 = scaledPosition.toInt().coerceIn(0, colors.size - 2)
        val index2 = index1 + 1
        val fraction = scaledPosition - index1
        
        // Interpolate between colors
        return interpolateColor(colors[index1], colors[index2], fraction)
    }
    
    /**
     * Interpolates between two colors.
     */
    private fun interpolateColor(color1: Int, color2: Int, fraction: Float): Int {
        val a1 = Color.alpha(color1)
        val r1 = Color.red(color1)
        val g1 = Color.green(color1)
        val b1 = Color.blue(color1)
        
        val a2 = Color.alpha(color2)
        val r2 = Color.red(color2)
        val g2 = Color.green(color2)
        val b2 = Color.blue(color2)
        
        val a = (a1 + (a2 - a1) * fraction).toInt()
        val r = (r1 + (r2 - r1) * fraction).toInt()
        val g = (g1 + (g2 - g1) * fraction).toInt()
        val b = (b1 + (b2 - b1) * fraction).toInt()
        
        return Color.argb(a, r, g, b)
    }
    
    /**
     * Finishes the current stroke and adds it to the stroke list.
     */
    private fun finishStroke() {
        currentPath?.let { path ->
            // Add final line to last position
            path.lineTo(lastX, lastY)
            
            // Create stroke with paint copy
            val stroke = Stroke(
                path = Path(path),  // Create a copy of the path
                paint = Stroke.createPaintCopy(currentPaint)
            )
            
            // Add to stroke list
            strokes.add(stroke)
            
            // Draw to off-screen bitmap
            bitmapCanvas?.drawPath(path, currentPaint)
        }
        
        // Reset current path
        currentPath = null
        
        invalidate()
    }
    
    /**
     * Configures the current paint based on the selected tool.
     */
    private fun configurePaintForTool(tool: Tool, strokeWidth: Float) {
        currentPaint.apply {
            this.strokeWidth = strokeWidth
            xfermode = null  // Reset xfermode
            
            when (tool) {
                Tool.PEN -> {
                    color = currentColor
                    alpha = 255
                }
                Tool.HIGHLIGHTER -> {
                    color = currentColor
                    alpha = highlighterAlpha
                    // Highlighter typically has thicker stroke
                    this.strokeWidth = strokeWidth * 2
                }
                Tool.ERASER -> {
                    // Use CLEAR xfermode to erase from bitmap
                    xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                    color = Color.TRANSPARENT
                    alpha = 255
                }
            }
        }
    }

    // ================================
    // Public Tool Controls
    // ================================
    
    /**
     * Sets the current drawing tool.
     */
    fun setTool(tool: Tool) {
        currentTool = tool
    }
    
    /**
     * Gets the current drawing tool.
     */
    fun getTool(): Tool = currentTool
    
    /**
     * Sets the drawing color.
     */
    fun setColor(color: Int) {
        currentColor = color
        // Only update current paint if not in eraser mode
        if (currentTool != Tool.ERASER) {
            currentPaint.color = color
        }
    }
    
    /**
     * Sets the stroke width for the current tool.
     */
    fun setStrokeWidth(width: Float) {
        val clampedWidth = width.coerceIn(1f, 100f)
        toolStrokeWidths[currentTool] = clampedWidth
        currentPaint.strokeWidth = clampedWidth
    }
    
    /**
     * Gets the stroke width for the current tool.
     */
    fun getStrokeWidth(): Float = toolStrokeWidths[currentTool] ?: 10f
    
    /**
     * Toggles visibility of all drawings.
     */
    fun toggleDrawingsVisibility(): Boolean {
        drawingsVisible = !drawingsVisible
        invalidate()
        return drawingsVisible
    }
    
    /**
     * Gets whether drawings are visible.
     */
    fun areDrawingsVisible(): Boolean = drawingsVisible
    
    /**
     * Undoes the last stroke.
     */
    fun undo() {
        if (strokes.isNotEmpty()) {
            val lastStroke = strokes.removeAt(strokes.lastIndex)
            redoStack.add(lastStroke)
            redrawAllStrokes()
            invalidate()
        }
    }
    
    /**
     * Redoes the last undone stroke.
     */
    fun redo() {
        if (redoStack.isNotEmpty()) {
            val stroke = redoStack.removeAt(redoStack.lastIndex)
            strokes.add(stroke)
            bitmapCanvas?.drawPath(stroke.path, stroke.paint)
            invalidate()
        }
    }
    
    /**
     * Clears all strokes from the canvas.
     */
    fun clear() {
        strokes.clear()
        redoStack.clear()
        bitmapCanvas?.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        invalidate()
    }
    
    /**
     * Checks if undo is available.
     */
    fun canUndo(): Boolean = strokes.isNotEmpty()
    
    /**
     * Checks if redo is available.
     */
    fun canRedo(): Boolean = redoStack.isNotEmpty()

    // ================================
    // S Pen Remote SDK Integration Point
    // ================================
    // 
    // For future integration with Samsung's S Pen Remote SDK for Air actions:
    // 
    // 1. Add Samsung SDK dependency
    // 2. Implement SpenRemote.Callback interface
    // 3. Register for button events: spenRemote.connect(this, callback)
    // 4. Handle button events in callback:
    //    - Single click: Toggle tool
    //    - Double click: Undo
    //    - Long press: Clear
    // 5. Handle hover events for preview
    //
    // This would allow using S Pen button without touching the screen.
    // ================================
}
