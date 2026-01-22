package com.sketchontop

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.SeekBar
import androidx.core.app.NotificationCompat

/**
 * Foreground Service that creates a system overlay for drawing.
 * 
 * S Pen Mode Implementation:
 * - Default: Dynamic FLAG toggle - canvas is touchable, detects tool type on ACTION_DOWN
 *   - Stylus: draws normally
 *   - Finger: toggles FLAG_NOT_TOUCHABLE to pass through (first touch lost, subsequent work)
 * - Enhanced (with AccessibilityService): Perfect separation, all touches work
 */
class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    
    // Separate windows for canvas and toolbar
    private var canvasView: View? = null
    private var toolbarView: View? = null
    private var canvasParams: WindowManager.LayoutParams? = null
    private var toolbarParams: WindowManager.LayoutParams? = null
    
    // Drag tracking for movable toolbar
    private var initialX: Int = 0
    private var initialY: Int = 0
    private var initialTouchX: Float = 0f
    private var initialTouchY: Float = 0f
    
    private var drawingView: DrawingView? = null
    private var toolbar: LinearLayout? = null
    private var btnExpand: ImageButton? = null
    
    // State
    private var isDrawModeEnabled = true
    private var isSPenModeEnabled = false
    private var isGradientModeEnabled = false
    private var isToolbarExpanded = true
    private var currentColor = Color.BLACK
    
    // Handler for delayed canvas reset
    private val handler = Handler(Looper.getMainLooper())
    private val resetCanvasRunnable = Runnable { 
        // Re-enable canvas after finger touch
        if (isSPenModeEnabled && isDrawModeEnabled) {
            setCanvasTouchable(true)
        }
    }

    companion object {
        const val CHANNEL_ID = "SketchOnTopChannel"
        const val NOTIFICATION_ID = 1
        const val ACTION_STOP = "com.sketchontop.STOP"
        // Time to keep canvas non-touchable after finger touch
        // Long enough for user interaction, then resets for stylus
        const val CANVAS_RESET_DELAY_MS = 5000L
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        createOverlay()
        
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        removeOverlay()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "SketchOnTop Overlay",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows when screen annotation is active"
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val stopIntent = Intent(this, OverlayService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val openIntent = Intent(this, MainActivity::class.java)
        val openPendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SketchOnTop Active")
            .setContentText("Drawing overlay is running")
            .setSmallIcon(R.drawable.ic_pen)
            .setOngoing(true)
            .setContentIntent(openPendingIntent)
            .addAction(R.drawable.ic_close, "Stop", stopPendingIntent)
            .build()
    }

    /**
     * Creates TWO overlay windows: one for canvas, one for toolbar.
     */
    private fun createOverlay() {
        val themedContext = android.view.ContextThemeWrapper(this, R.style.Theme_SketchOnTop)
        val inflater = LayoutInflater.from(themedContext)
        
        val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        
        // === Canvas Window (full screen, touchable to detect tool type) ===
        canvasView = inflater.inflate(R.layout.overlay_canvas, null)
        canvasParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
        windowManager.addView(canvasView, canvasParams)
        
        // === Toolbar Window (wrap content, always touchable) ===
        toolbarView = inflater.inflate(R.layout.overlay_toolbar, null)
        
        // Get display width for top-right positioning
        val displayMetrics = resources.displayMetrics
        val displayWidth = displayMetrics.widthPixels
        
        toolbarParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = displayWidth - 150  // Start on right side (approximate toolbar width)
            y = 48
        }
        windowManager.addView(toolbarView, toolbarParams)
        
        // Initialize views
        initViews()
        setupDrawingViewCallbacks()
        setupToolbar()
        setupEraserPage()
        
        // Set default tool
        selectTool(DrawingView.Tool.PEN)
        
        // Enable toolbar dragging
        setupToolbarDrag()
    }

    private fun removeOverlay() {
        canvasView?.let {
            try { windowManager.removeView(it) } catch (e: Exception) {}
        }
        toolbarView?.let {
            try { windowManager.removeView(it) } catch (e: Exception) {}
        }
        canvasView = null
        toolbarView = null
        drawingView = null
    }

    private fun initViews() {
        drawingView = canvasView?.findViewById(R.id.drawingView)
        toolbar = toolbarView?.findViewById(R.id.toolbar)
        btnExpand = toolbarView?.findViewById(R.id.btnExpand)
    }
    
    /**
     * Sets up callbacks for DrawingView events.
     */
    private fun setupDrawingViewCallbacks() {
        // When finger is detected in S Pen mode, make canvas non-touchable
        // so subsequent events in this gesture pass through
        drawingView?.onFingerTouchDetected = {
            if (isSPenModeEnabled) {
                // Make canvas non-touchable for this gesture
                setCanvasTouchable(false)
                
                // Schedule reset to touchable after a short delay
                // This allows the next stylus input to be detected
                handler.removeCallbacks(resetCanvasRunnable)
                handler.postDelayed(resetCanvasRunnable, CANVAS_RESET_DELAY_MS)
            }
        }
    }
    
    /**
     * Sets up drag functionality for the toolbar.
     * User can drag from any part of the toolbar background.
     */
    private fun setupToolbarDrag() {
        toolbarView?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    toolbarParams?.let { params ->
                        initialX = params.x
                        initialY = params.y
                    }
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    toolbarParams?.let { params ->
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        try {
                            windowManager.updateViewLayout(toolbarView, params)
                        } catch (e: Exception) {}
                    }
                    true
                }
                else -> false
            }
        }
    }
    
    /**
     * Sets canvas touchability directly.
     */
    private fun setCanvasTouchable(touchable: Boolean) {
        canvasView?.let { view ->
            canvasParams?.let { params ->
                val currentlyTouchable = (params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE) == 0
                if (currentlyTouchable == touchable) return // No change needed
                
                if (touchable) {
                    params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
                } else {
                    params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                }
                try {
                    windowManager.updateViewLayout(view, params)
                } catch (e: Exception) {
                    // View might not be attached
                }
            }
        }
    }

    private fun setupToolbar() {
        toolbarView?.let { view ->
            // Tool buttons
            view.findViewById<ImageButton>(R.id.btnPen).setOnClickListener { 
                selectTool(DrawingView.Tool.PEN) 
            }
            view.findViewById<ImageButton>(R.id.btnHighlighter).setOnClickListener { 
                selectTool(DrawingView.Tool.HIGHLIGHTER) 
            }
            view.findViewById<ImageButton>(R.id.btnEraser).setOnClickListener { 
                if (drawingView?.getTool() == DrawingView.Tool.ERASER) {
                    // Already in eraser mode, open eraser settings page
                    openEraserPage()
                } else {
                    selectTool(DrawingView.Tool.ERASER)
                }
            }
            
            // Mode toggles
            view.findViewById<ImageButton>(R.id.btnDrawMode).setOnClickListener { 
                toggleDrawMode() 
            }
            view.findViewById<ImageButton>(R.id.btnSPenMode).setOnClickListener { 
                toggleSPenMode() 
            }
            view.findViewById<ImageButton>(R.id.btnGradientMode).setOnClickListener { 
                toggleGradientMode() 
            }
            
            // Action buttons
            view.findViewById<ImageButton>(R.id.btnMinimize).setOnClickListener { 
                minimizeToolbar() 
            }
            view.findViewById<ImageButton>(R.id.btnSettings).setOnClickListener { 
                openSettings() 
            }
            btnExpand?.setOnClickListener { expandToolbar() }
            
            view.findViewById<ImageButton>(R.id.btnToggleVisibility).setOnClickListener { 
                toggleDrawingsVisibility() 
            }
            view.findViewById<ImageButton>(R.id.btnUndo).setOnClickListener { 
                drawingView?.undo() 
            }
            view.findViewById<ImageButton>(R.id.btnRedo).setOnClickListener { 
                drawingView?.redo() 
            }
            view.findViewById<ImageButton>(R.id.btnClear).setOnClickListener { 
                drawingView?.clear() 
            }
            view.findViewById<ImageButton>(R.id.btnClose).setOnClickListener { 
                stopSelf() 
            }
            
            // Color picker
            setupColorPicker(view)
            
            // Stroke width
            view.findViewById<SeekBar>(R.id.strokeWidthSeekBar).setOnSeekBarChangeListener(
                object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        drawingView?.setStrokeWidth((progress + 2).toFloat())
                        updateBrushPreview()
                    }
                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                }
            )
        }
    }

    private fun setupColorPicker(view: View) {
        val colorPickerPage = toolbarView?.findViewById<View>(R.id.colorPickerPage)
        
        // Open color picker page when brush preview is tapped
        view.findViewById<View>(R.id.btnOpenColorPicker)?.setOnClickListener {
            toolbar?.visibility = View.GONE
            colorPickerPage?.visibility = View.VISIBLE
        }
        
        // Back button closes color picker page
        colorPickerPage?.findViewById<View>(R.id.btnColorPickerBack)?.setOnClickListener {
            colorPickerPage.visibility = View.GONE
            toolbar?.visibility = View.VISIBLE
        }
        
        // Color grid click handlers
        val pickerColors = mapOf(
            R.id.colorPick1 to 0xFF000000.toInt(),
            R.id.colorPick2 to 0xFFFFFFFF.toInt(),
            R.id.colorPick3 to 0xFFFF0000.toInt(),
            R.id.colorPick4 to 0xFF00FF00.toInt(),
            R.id.colorPick5 to 0xFF0000FF.toInt(),
            R.id.colorPick6 to 0xFFFFFF00.toInt(),
            R.id.colorPick7 to 0xFFFF00FF.toInt(),
            R.id.colorPick8 to 0xFF00FFFF.toInt(),
            R.id.colorPick9 to 0xFFFF8000.toInt()
        )
        
        for ((viewId, color) in pickerColors) {
            colorPickerPage?.findViewById<View>(viewId)?.setOnClickListener {
                selectColor(color)
                // Auto close after selection
                colorPickerPage.visibility = View.GONE
                toolbar?.visibility = View.VISIBLE
            }
        }
        
        // Rainbow slider for any color
        colorPickerPage?.findViewById<View>(R.id.rainbowSlider)?.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    val ratio = (event.x.coerceIn(0f, v.width.toFloat()) / v.width)
                    val color = Color.HSVToColor(floatArrayOf(ratio * 360f, 1f, 1f))
                    selectColor(color)
                    true
                }
                else -> false
            }
        }
        
        // Gradient preset click handlers
        val gradientPresets = mapOf(
            R.id.gradientRainbow to DrawingView.GradientPreset.RAINBOW,
            R.id.gradientFire to DrawingView.GradientPreset.FIRE,
            R.id.gradientOcean to DrawingView.GradientPreset.OCEAN,
            R.id.gradientSunset to DrawingView.GradientPreset.SUNSET,
            R.id.gradientForest to DrawingView.GradientPreset.FOREST,
            R.id.gradientNeon to DrawingView.GradientPreset.NEON
        )
        
        for ((viewId, preset) in gradientPresets) {
            colorPickerPage?.findViewById<View>(viewId)?.setOnClickListener {
                // Enable gradient mode and select preset
                drawingView?.gradientBrushEnabled = true
                drawingView?.currentGradient = preset
                isGradientModeEnabled = true
                
                // Update gradient mode button tint
                toolbarView?.findViewById<ImageButton>(R.id.btnGradientMode)?.imageTintList = 
                    android.content.res.ColorStateList.valueOf(0xFF4CAF50.toInt())
                
                // Close color picker and return to toolbar
                colorPickerPage?.visibility = View.GONE
                toolbar?.visibility = View.VISIBLE
            }
        }
        
        // Initial brush preview update
        updateBrushPreview()
    }
    
    /**
     * Selects a color and updates the UI and drawing view.
     */
    private fun selectColor(color: Int) {
        currentColor = color
        drawingView?.setColor(color)
        updateBrushPreview()
        
        if (drawingView?.getTool() == DrawingView.Tool.ERASER) {
            selectTool(DrawingView.Tool.PEN)
        }
    }
    
    /**
     * Updates the brush preview to show current color and size.
     */
    private fun updateBrushPreview() {
        val brushPreview = toolbarView?.findViewById<View>(R.id.brushPreview) ?: return
        val strokeWidth = drawingView?.getStrokeWidth() ?: 10f
        
        // Update size (clamp between 6dp and 36dp for visibility)
        val sizePx = strokeWidth.coerceIn(6f, 36f).toInt()
        val params = brushPreview.layoutParams
        params.width = sizePx
        params.height = sizePx
        brushPreview.layoutParams = params
        
        // Update color using GradientDrawable for circular shape
        val drawable = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(currentColor)
        }
        brushPreview.background = drawable
    }

    private fun setupGradientPresets() {
        toolbarView?.let { view ->
            val container = view.findViewById<LinearLayout>(R.id.gradientPresets)
            val presets = DrawingView.GradientPreset.values().filter { 
                it != DrawingView.GradientPreset.CUSTOM 
            }
            
            for (preset in presets) {
                val button = View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(48, 24).apply { marginEnd = 4 }
                    background = GradientDrawable(
                        GradientDrawable.Orientation.LEFT_RIGHT,
                        preset.colors
                    ).apply { cornerRadius = 4f }
                    setOnClickListener { drawingView?.currentGradient = preset }
                }
                container.addView(button)
            }
        }
    }

    /**
     * Toggles draw mode. When OFF, the canvas window becomes non-touchable.
     */
    private fun toggleDrawMode() {
        isDrawModeEnabled = !isDrawModeEnabled
        drawingView?.drawingEnabled = isDrawModeEnabled
        
        // Update canvas touchability
        if (!isDrawModeEnabled) {
            // Draw mode OFF: canvas non-touchable (everything passes through)
            setCanvasTouchable(false)
        } else {
            // Draw mode ON: canvas must be touchable to receive input
            // (S Pen mode will handle finger passthrough dynamically)
            setCanvasTouchable(true)
        }
        
        // Update button tint
        toolbarView?.findViewById<ImageButton>(R.id.btnDrawMode)?.let { btn ->
            btn.setColorFilter(if (isDrawModeEnabled) 0xFF4CAF50.toInt() else 0xFF888888.toInt())
        }
    }

    /**
     * Toggles S Pen mode.
     * When ON: Canvas is touchable to detect tool type. On finger touch,
     * it becomes non-touchable to pass through, then resets for next stylus.
     */
    private fun toggleSPenMode() {
        isSPenModeEnabled = !isSPenModeEnabled
        drawingView?.sPenOnlyMode = isSPenModeEnabled
        
        // In S Pen mode, canvas needs to be touchable to detect tool type
        if (isSPenModeEnabled && isDrawModeEnabled) {
            setCanvasTouchable(true)
        }
        
        toolbarView?.findViewById<ImageButton>(R.id.btnSPenMode)?.let { btn ->
            btn.setColorFilter(if (isSPenModeEnabled) 0xFF2196F3.toInt() else 0xFFFFFFFF.toInt())
        }
    }
    
    /**
     * Opens the eraser settings page.
     */
    private fun openEraserPage() {
        val eraserPage = toolbarView?.findViewById<View>(R.id.eraserPage) ?: return
        
        toolbar?.visibility = View.GONE
        eraserPage.visibility = View.VISIBLE
        
        // Update indicators based on current mode
        updateEraserModeIndicators()
    }
    
    /**
     * Sets up the eraser page click handlers.
     */
    private fun setupEraserPage() {
        val eraserPage = toolbarView?.findViewById<View>(R.id.eraserPage) ?: return
        
        // Back button
        eraserPage.findViewById<View>(R.id.btnEraserBack)?.setOnClickListener {
            eraserPage.visibility = View.GONE
            toolbar?.visibility = View.VISIBLE
        }
        
        // Pixel eraser option
        eraserPage.findViewById<View>(R.id.btnPixelEraser)?.setOnClickListener {
            drawingView?.eraserMode = DrawingView.EraserMode.PIXEL
            updateEraserModeIndicators()
            eraserPage.visibility = View.GONE
            toolbar?.visibility = View.VISIBLE
        }
        
        // Stroke eraser option
        eraserPage.findViewById<View>(R.id.btnStrokeEraser)?.setOnClickListener {
            drawingView?.eraserMode = DrawingView.EraserMode.STROKE
            updateEraserModeIndicators()
            eraserPage.visibility = View.GONE
            toolbar?.visibility = View.VISIBLE
        }
    }
    
    /**
     * Updates the eraser mode indicators to show which mode is active.
     */
    private fun updateEraserModeIndicators() {
        val eraserPage = toolbarView?.findViewById<View>(R.id.eraserPage) ?: return
        val isPixelMode = drawingView?.eraserMode == DrawingView.EraserMode.PIXEL
        
        eraserPage.findViewById<View>(R.id.pixelEraserIndicator)?.backgroundTintList = 
            android.content.res.ColorStateList.valueOf(
                if (isPixelMode) 0xFF4CAF50.toInt() else 0xFF888888.toInt()
            )
        eraserPage.findViewById<View>(R.id.strokeEraserIndicator)?.backgroundTintList = 
            android.content.res.ColorStateList.valueOf(
                if (!isPixelMode) 0xFF4CAF50.toInt() else 0xFF888888.toInt()
            )
    }

    private fun toggleGradientMode() {
        isGradientModeEnabled = !isGradientModeEnabled
        drawingView?.gradientBrushEnabled = isGradientModeEnabled
        
        toolbarView?.let { view ->
            view.findViewById<ImageButton>(R.id.btnGradientMode)?.setColorFilter(
                if (isGradientModeEnabled) 0xFFFF9800.toInt() else 0xFFFFFFFF.toInt()
            )
            view.findViewById<HorizontalScrollView>(R.id.gradientPresetsContainer)?.visibility = 
                if (isGradientModeEnabled) View.VISIBLE else View.GONE
        }
    }

    private fun selectTool(tool: DrawingView.Tool) {
        drawingView?.setTool(tool)
        
        toolbarView?.let { view ->
            view.findViewById<ImageButton>(R.id.btnPen).isSelected = (tool == DrawingView.Tool.PEN)
            view.findViewById<ImageButton>(R.id.btnHighlighter).isSelected = (tool == DrawingView.Tool.HIGHLIGHTER)
            view.findViewById<ImageButton>(R.id.btnEraser).isSelected = (tool == DrawingView.Tool.ERASER)
            
            val width = drawingView?.getStrokeWidth() ?: 10f
            view.findViewById<SeekBar>(R.id.strokeWidthSeekBar).progress = (width - 2).toInt().coerceIn(0, 50)
        }
    }

    private fun minimizeToolbar() {
        isToolbarExpanded = false
        toolbar?.visibility = View.GONE
        btnExpand?.visibility = View.VISIBLE
    }

    private fun expandToolbar() {
        isToolbarExpanded = true
        toolbar?.visibility = View.VISIBLE
        btnExpand?.visibility = View.GONE
    }
    
    /**
     * Opens the settings/MainActivity.
     */
    private fun openSettings() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(MainActivity.EXTRA_FROM_OVERLAY, true)
        }
        startActivity(intent)
    }

    private fun toggleDrawingsVisibility() {
        val isVisible = drawingView?.toggleDrawingsVisibility() ?: true
        val iconRes = if (isVisible) R.drawable.ic_visibility else R.drawable.ic_visibility_off
        toolbarView?.findViewById<ImageButton>(R.id.btnToggleVisibility)?.setImageResource(iconRes)
    }
}
