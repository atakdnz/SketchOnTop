package com.sketchontop

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.CheckBox
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Toast
import androidx.core.app.NotificationCompat
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Foreground Service that creates a system overlay for drawing.
 * 
 * S Pen Mode Implementation:
 * - Default: Dynamic FLAG toggle - canvas is touchable, detects tool type on ACTION_DOWN
 *   - Stylus: draws normally
 *   - Finger: toggles FLAG_NOT_TOUCHABLE to pass through (first touch lost, subsequent work)
 * - Enhanced (with AccessibilityService): Perfect separation, all touches work
 */
class OverlayService : Service(), SharedPreferences.OnSharedPreferenceChangeListener {

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
    private var toolbarDragged: Boolean = false
    
    private var drawingView: DrawingView? = null
    private var toolbar: LinearLayout? = null
    private var btnExpand: ImageButton? = null
    
    // State
    private var isDrawModeEnabled = true
    private var isSPenModeEnabled = false
    private var isGradientModeEnabled = false
    private var isWhiteboardModeEnabled = false
    private var isToolbarExpanded = true
    private var currentColor = Color.BLACK
    private var accessibilityStylusInputEnabled = true
    private var compactToolbarEnabled = true
    private var sPenButtonTogglesDrawMode = false
    private var fingerPassthroughResetDelayMs = SPenSettings.DEFAULT_FINGER_PASSTHROUGH_RESET_DELAY_MS
    private var sPenButtonReenableDelayMs = SPenSettings.DEFAULT_SPEN_BUTTON_REENABLE_DELAY_MS
    private var hoverArmsDrawing = false
    private var hoverExitDelayMs = SPenSettings.DEFAULT_HOVER_EXIT_DELAY_MS
    private var isSPenHovering = false
    private var isSPenInContact = false
    private var accessibilityStylusModeActive = false
    private var accessibilityStylusStartedOnToolbar = false
    private var accessibilityStylusToolbarTarget: View? = null
    
    // Handler for delayed canvas reset
    private val handler = Handler(Looper.getMainLooper())
    private val resetCanvasRunnable = Runnable { 
        // Re-enable canvas after finger touch
        if (isSPenModeEnabled && isDrawModeEnabled && !isWhiteboardModeEnabled) {
            setCanvasTouchable(true)
        }
    }

    private val reenableDrawModeRunnable = Runnable {
        if (isSPenModeEnabled) {
            setDrawModeEnabled(true)
        }
    }

    private val hoverExitRunnable = Runnable {
        if (hoverArmsDrawing && isSPenModeEnabled && !isSPenHovering && !isSPenInContact) {
            setHoverListeningMode()
        }
    }

    companion object {
        const val CHANNEL_ID = "SketchOnTopChannel"
        const val NOTIFICATION_ID = 1
        const val ACTION_STOP = "com.sketchontop.STOP"
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        loadSPenSettings()
        SPenSettings.prefs(this).registerOnSharedPreferenceChangeListener(this)
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
        StylusSensorService.setStylusListeningEnabled(false)
        if (StylusSensorService.onStylusMotionEvent != null) {
            StylusSensorService.onStylusMotionEvent = null
        }
        SPenSettings.prefs(this).unregisterOnSharedPreferenceChangeListener(this)
        removeOverlay()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == SPenSettings.KEY_SPEN_MODE_ENABLED ||
            key == SPenSettings.KEY_SPEN_BUTTON_TOGGLES_DRAW_MODE ||
            key == SPenSettings.KEY_FINGER_PASSTHROUGH_RESET_DELAY_MS ||
            key == SPenSettings.KEY_SPEN_BUTTON_REENABLE_DELAY_MS ||
            key == SPenSettings.KEY_HOVER_ARM_DRAWING ||
            key == SPenSettings.KEY_HOVER_EXIT_DELAY_MS ||
            key == SPenSettings.KEY_ACCESSIBILITY_STYLUS_INPUT ||
            key == SPenSettings.KEY_COMPACT_TOOLBAR
        ) {
            loadSPenSettings()
        }
    }

    private fun loadSPenSettings() {
        accessibilityStylusInputEnabled = SPenSettings.accessibilityStylusInput(this)
        compactToolbarEnabled = SPenSettings.compactToolbar(this)
        isSPenModeEnabled = SPenSettings.sPenModeEnabled(this)
        sPenButtonTogglesDrawMode = SPenSettings.sPenButtonTogglesDrawMode(this)
        fingerPassthroughResetDelayMs = SPenSettings.fingerPassthroughResetDelayMs(this)
        sPenButtonReenableDelayMs = SPenSettings.sPenButtonReenableDelayMs(this)
        hoverArmsDrawing = SPenSettings.hoverArmsDrawing(this)
        hoverExitDelayMs = SPenSettings.hoverExitDelayMs(this)
        drawingView?.sPenButtonTogglesDrawMode = sPenButtonTogglesDrawMode
        drawingView?.sPenOnlyMode = isSPenModeEnabled
        drawingView?.hoverGateEnabled = hoverArmsDrawing
        if (!hoverArmsDrawing) {
            drawingView?.hoverDrawingArmed = true
        }
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
        toolbarView = inflater.inflate(
            if (compactToolbarEnabled) R.layout.overlay_toolbar_compact else R.layout.overlay_toolbar,
            null
        )
        
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
            x = if (compactToolbarEnabled) (displayWidth / 2) - 130 else displayWidth - 150
            y = if (compactToolbarEnabled) 24 else 48
        }
        windowManager.addView(toolbarView, toolbarParams)
        
        // Initialize views
        initViews()
        setupDrawingViewCallbacks()
        setupAccessibilityStylusInput()
        setupToolbar()
        setupEraserPage()
        applyDrawingSettings()
        
        // Set default tool
        selectTool(drawingView?.getTool() ?: DrawingView.Tool.PEN, persist = false)
        applySPenModeState()
        setWhiteboardModeEnabled(isWhiteboardModeEnabled)
        
        // Enable toolbar dragging
        setupToolbarDrag()
    }

    private fun setupAccessibilityStylusInput() {
        StylusSensorService.setStylusListeningEnabled(accessibilityStylusInputEnabled)
        accessibilityStylusModeActive = accessibilityStylusInputEnabled &&
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            StylusSensorService.isRunning

        StylusSensorService.onStylusMotionEvent = { event ->
            handler.post {
                try {
                    accessibilityStylusModeActive = accessibilityStylusInputEnabled &&
                        android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                        StylusSensorService.isRunning
                    if (accessibilityStylusModeActive && isSPenModeEnabled) {
                        if (!isWhiteboardModeEnabled) {
                            setCanvasTouchable(false)
                        }
                        if (!handleAccessibilityToolbarStylusEvent(event)) {
                            drawingView?.handleExternalStylusEvent(event)
                        }
                    }
                } finally {
                    event.recycle()
                }
            }
        }
    }

    private fun handleAccessibilityToolbarStylusEvent(event: MotionEvent): Boolean {
        val toolbarRoot = toolbarView ?: return false
        val target = findToolbarChildForStylusEvent(toolbarRoot, event)
        val isInsideToolbar = target != null || isRawPointInsideView(event.rawX, event.rawY, toolbarRoot)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                accessibilityStylusStartedOnToolbar = isInsideToolbar
                accessibilityStylusToolbarTarget = target
                return accessibilityStylusStartedOnToolbar
            }
            MotionEvent.ACTION_MOVE -> {
                return accessibilityStylusStartedOnToolbar
            }
            MotionEvent.ACTION_UP -> {
                if (accessibilityStylusStartedOnToolbar) {
                    accessibilityStylusStartedOnToolbar = false
                    (target ?: accessibilityStylusToolbarTarget)?.performClick()
                    accessibilityStylusToolbarTarget = null
                    return true
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                if (accessibilityStylusStartedOnToolbar) {
                    accessibilityStylusStartedOnToolbar = false
                    accessibilityStylusToolbarTarget = null
                    return true
                }
            }
        }

        return isInsideToolbar
    }

    private fun findToolbarChildForStylusEvent(toolbarRoot: View, event: MotionEvent): View? {
        findClickableChildAt(toolbarRoot, event.rawX, event.rawY)?.let { return it }

        val localX = event.x
        val localY = event.y
        if (localX >= 0f && localX <= toolbarRoot.width && localY >= 0f && localY <= toolbarRoot.height) {
            return findClickableChildAtLocal(toolbarRoot, localX, localY)
        }

        return null
    }

    private fun isRawPointInsideView(rawX: Float, rawY: Float, view: View): Boolean {
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        return rawX >= location[0] &&
            rawX <= location[0] + view.width &&
            rawY >= location[1] &&
            rawY <= location[1] + view.height
    }

    private fun findClickableChildAt(view: View, rawX: Float, rawY: Float): View? {
        if (!view.isShown || !isRawPointInsideView(rawX, rawY, view)) return null

        if (view is ViewGroup) {
            for (i in view.childCount - 1 downTo 0) {
                val childHit = findClickableChildAt(view.getChildAt(i), rawX, rawY)
                if (childHit != null) return childHit
            }
        }

        return if (view.isClickable || view.hasOnClickListeners()) view else null
    }

    private fun findClickableChildAtLocal(view: View, localX: Float, localY: Float): View? {
        if (!view.isShown || localX < 0f || localY < 0f || localX > view.width || localY > view.height) {
            return null
        }

        if (view is ViewGroup) {
            for (i in view.childCount - 1 downTo 0) {
                val child = view.getChildAt(i)
                val childHit = findClickableChildAtLocal(
                    child,
                    localX - child.left + child.scrollX,
                    localY - child.top + child.scrollY
                )
                if (childHit != null) return childHit
            }
        }

        return if (view.isClickable || view.hasOnClickListeners()) view else null
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
            if (isSPenModeEnabled && !isWhiteboardModeEnabled) {
                // Make canvas non-touchable for this gesture
                setCanvasTouchable(false)
                
                // Schedule reset to touchable after a short delay
                // This allows the next stylus input to be detected
                handler.removeCallbacks(resetCanvasRunnable)
                handler.postDelayed(resetCanvasRunnable, fingerPassthroughResetDelayMs)
            }
        }
        drawingView?.onSPenButtonModeSwitchRequested = {
            if (sPenButtonTogglesDrawMode) {
                setDrawModeEnabled(false)
                handler.removeCallbacks(reenableDrawModeRunnable)
                if (sPenButtonReenableDelayMs > 0L) {
                    handler.postDelayed(reenableDrawModeRunnable, sPenButtonReenableDelayMs)
                }
            }
        }
        drawingView?.onSPenHoverChanged = { isHovering ->
            if (hoverArmsDrawing && isSPenModeEnabled) {
                isSPenHovering = isHovering
                handler.removeCallbacks(hoverExitRunnable)
                if (isHovering) {
                    drawingView?.hoverDrawingArmed = true
                    setDrawModeEnabled(true)
                } else if (!isSPenInContact) {
                    handler.postDelayed(hoverExitRunnable, hoverExitDelayMs)
                }
            }
        }
        drawingView?.onSPenContactChanged = { isInContact ->
            if (hoverArmsDrawing && isSPenModeEnabled) {
                isSPenInContact = isInContact
                handler.removeCallbacks(hoverExitRunnable)
                if (isInContact) {
                    drawingView?.hoverDrawingArmed = true
                    setDrawModeEnabled(true)
                } else if (!isSPenHovering) {
                    handler.postDelayed(hoverExitRunnable, hoverExitDelayMs)
                }
            }
        }
        drawingView?.sPenButtonTogglesDrawMode = sPenButtonTogglesDrawMode
        drawingView?.hoverGateEnabled = hoverArmsDrawing
    }
    
    /**
     * Sets up drag functionality for the toolbar.
     * User can drag from any part of the toolbar background.
     */
    private fun setupToolbarDrag() {
        toolbarView?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    toolbarDragged = false
                    toolbarParams?.let { params ->
                        initialX = params.x
                        initialY = params.y
                    }
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - initialTouchX
                    val deltaY = event.rawY - initialTouchY
                    if (kotlin.math.abs(deltaX) > 6 || kotlin.math.abs(deltaY) > 6) {
                        toolbarDragged = true
                    }
                    toolbarParams?.let { params ->
                        params.x = initialX + deltaX.toInt()
                        params.y = initialY + deltaY.toInt()
                        try {
                            windowManager.updateViewLayout(toolbarView, params)
                        } catch (e: Exception) {}
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!toolbarDragged && !isToolbarExpanded) {
                        expandToolbar()
                    }
                    true
                }
                else -> true
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
                if (compactToolbarEnabled) {
                    openPenSettingsPage()
                }
            }
            view.findViewById<ImageButton?>(R.id.btnHighlighter)?.setOnClickListener { 
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
            view.findViewById<ImageButton?>(R.id.btnDrawMode)?.setOnClickListener { 
                toggleDrawMode() 
            }
            view.findViewById<ImageButton?>(R.id.btnSPenMode)?.setOnClickListener { 
                toggleSPenMode() 
            }
            view.findViewById<ImageButton?>(R.id.btnGradientMode)?.setOnClickListener { 
                toggleGradientMode() 
            }
            view.findViewById<ImageButton?>(R.id.btnWhiteboardMode)?.setOnClickListener {
                toggleWhiteboardMode()
            }
            view.findViewById<ImageButton?>(R.id.btnWhiteboardSnapshot)?.setOnClickListener {
                saveWhiteboardSnapshot()
            }
            
            // Action buttons
            view.findViewById<ImageButton?>(R.id.btnMinimize)?.setOnClickListener { 
                minimizeToolbar() 
            }
            view.findViewById<ImageButton?>(R.id.btnSettings)?.setOnClickListener { 
                openSettings() 
            }
            btnExpand?.setOnClickListener { expandToolbar() }
            view.findViewById<View?>(R.id.btnMore)?.setOnClickListener {
                openOverflowPage()
            }
            view.findViewById<View?>(R.id.btnOverflowBack)?.setOnClickListener {
                closeSecondaryPanels()
            }
            view.findViewById<View?>(R.id.btnPenSettingsBack)?.setOnClickListener {
                closeSecondaryPanels()
            }
            
            view.findViewById<ImageButton?>(R.id.btnToggleVisibility)?.setOnClickListener { 
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
                        if (fromUser) {
                            saveCurrentToolSettings()
                        }
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
        val penSettingsPage = toolbarView?.findViewById<View>(R.id.penSettingsPage)
        
        // Open color picker page when brush preview is tapped
        view.findViewById<View>(R.id.btnOpenColorPicker)?.setOnClickListener {
            toolbar?.visibility = View.GONE
            penSettingsPage?.visibility = View.GONE
            colorPickerPage?.visibility = View.VISIBLE
        }
        
        // Back button closes color picker page
        colorPickerPage?.findViewById<View>(R.id.btnColorPickerBack)?.setOnClickListener {
            colorPickerPage.visibility = View.GONE
            if (compactToolbarEnabled) {
                penSettingsPage?.visibility = View.VISIBLE
            } else {
                toolbar?.visibility = View.VISIBLE
            }
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
                if (compactToolbarEnabled) {
                    penSettingsPage?.visibility = View.VISIBLE
                } else {
                    toolbar?.visibility = View.VISIBLE
                }
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
                saveDrawingSettings()
                
                // Update gradient mode button tint
                toolbarView?.findViewById<ImageButton>(R.id.btnGradientMode)?.imageTintList = 
                    android.content.res.ColorStateList.valueOf(0xFF4CAF50.toInt())
                
                // Close color picker and return to toolbar
                colorPickerPage?.visibility = View.GONE
                if (compactToolbarEnabled) {
                    penSettingsPage?.visibility = View.VISIBLE
                } else {
                    toolbar?.visibility = View.VISIBLE
                }
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
        saveDrawingSettings()
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
                    setOnClickListener {
                        drawingView?.currentGradient = preset
                        saveDrawingSettings()
                    }
                }
                container.addView(button)
            }
        }
    }

    /**
     * Toggles draw mode. When OFF, the canvas window becomes non-touchable.
     */
    private fun toggleDrawMode() {
        setDrawModeEnabled(!isDrawModeEnabled)
    }

    private fun setDrawModeEnabled(enabled: Boolean) {
        isDrawModeEnabled = enabled
        drawingView?.drawingEnabled = isDrawModeEnabled || isWhiteboardModeEnabled

        updateCanvasTouchability()

        // Update button tint
        toolbarView?.findViewById<ImageButton>(R.id.btnDrawMode)?.let { btn ->
            btn.setColorFilter(if (isDrawModeEnabled) 0xFF4CAF50.toInt() else 0xFF888888.toInt())
        }
    }

    private fun setHoverListeningMode() {
        isDrawModeEnabled = false
        drawingView?.drawingEnabled = true
        drawingView?.hoverDrawingArmed = false
        updateCanvasTouchability()

        toolbarView?.findViewById<ImageButton>(R.id.btnDrawMode)?.let { btn ->
            btn.setColorFilter(0xFF888888.toInt())
        }
    }

    /**
     * Toggles S Pen mode.
     * When ON: Canvas is touchable to detect tool type. On finger touch,
     * it becomes non-touchable to pass through, then resets for next stylus.
     */
    private fun toggleSPenMode() {
        setSPenModeEnabled(!isSPenModeEnabled, persist = true)
    }

    private fun setSPenModeEnabled(enabled: Boolean, persist: Boolean) {
        isSPenModeEnabled = enabled
        drawingView?.sPenOnlyMode = isSPenModeEnabled
        handler.removeCallbacks(resetCanvasRunnable)
        
        // In S Pen mode, canvas needs to be touchable to detect tool type
        accessibilityStylusModeActive = accessibilityStylusInputEnabled &&
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            StylusSensorService.isRunning

        updateCanvasTouchability()
        
        toolbarView?.findViewById<ImageButton>(R.id.btnSPenMode)?.let { btn ->
            btn.setColorFilter(if (isSPenModeEnabled) 0xFF2196F3.toInt() else 0xFFFFFFFF.toInt())
        }

        if (persist) {
            SPenSettings.prefs(this)
                .edit()
                .putBoolean(SPenSettings.KEY_SPEN_MODE_ENABLED, isSPenModeEnabled)
                .apply()
        }
    }

    private fun applySPenModeState() {
        setSPenModeEnabled(isSPenModeEnabled, persist = false)
    }
    
    /**
     * Opens the eraser settings page.
     */
    private fun openEraserPage() {
        val eraserPage = toolbarView?.findViewById<View>(R.id.eraserPage) ?: return
        
        toolbar?.visibility = View.GONE
        eraserPage.visibility = View.VISIBLE

        updateEraserControls()

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
            saveDrawingSettings()
            updateEraserModeIndicators()
            eraserPage.visibility = View.GONE
            toolbar?.visibility = View.VISIBLE
        }
        
        // Stroke eraser option
        eraserPage.findViewById<View>(R.id.btnStrokeEraser)?.setOnClickListener {
            drawingView?.eraserMode = DrawingView.EraserMode.STROKE
            saveDrawingSettings()
            updateEraserModeIndicators()
            eraserPage.visibility = View.GONE
            toolbar?.visibility = View.VISIBLE
        }

        eraserPage.findViewById<SeekBar?>(R.id.eraserSizeSeekBar)?.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val width = (progress + 1).toFloat()
                    drawingView?.setStrokeWidthForTool(DrawingView.Tool.ERASER, width)
                    eraserPage.findViewById<android.widget.TextView?>(R.id.eraserSizeValue)?.text =
                        width.toInt().toString()
                    if (fromUser) {
                        SPenSettings.prefs(this@OverlayService)
                            .edit()
                            .putFloat(SPenSettings.KEY_ERASER_WIDTH, width)
                            .apply()
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            }
        )

        eraserPage.findViewById<CheckBox?>(R.id.checkboxEraserPressure)?.setOnCheckedChangeListener { _, isChecked ->
            drawingView?.eraserPressureEnabled = isChecked
            saveDrawingSettings()
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

    private fun updateEraserControls() {
        val eraserPage = toolbarView?.findViewById<View>(R.id.eraserPage) ?: return
        val eraserWidth = drawingView?.getStrokeWidthForTool(DrawingView.Tool.ERASER) ?: 30f
        eraserPage.findViewById<SeekBar?>(R.id.eraserSizeSeekBar)?.progress =
            (eraserWidth - 1).toInt().coerceIn(0, 99)
        eraserPage.findViewById<android.widget.TextView?>(R.id.eraserSizeValue)?.text =
            eraserWidth.toInt().toString()
        eraserPage.findViewById<CheckBox?>(R.id.checkboxEraserPressure)?.isChecked =
            drawingView?.eraserPressureEnabled == true
    }

    private fun toggleGradientMode() {
        isGradientModeEnabled = !isGradientModeEnabled
        drawingView?.gradientBrushEnabled = isGradientModeEnabled
        saveDrawingSettings()
        
        toolbarView?.let { view ->
            view.findViewById<ImageButton>(R.id.btnGradientMode)?.setColorFilter(
                if (isGradientModeEnabled) 0xFFFF9800.toInt() else 0xFFFFFFFF.toInt()
            )
            view.findViewById<HorizontalScrollView>(R.id.gradientPresetsContainer)?.visibility = 
                if (isGradientModeEnabled) View.VISIBLE else View.GONE
        }
    }

    private fun toggleWhiteboardMode() {
        setWhiteboardModeEnabled(!isWhiteboardModeEnabled)
    }

    private fun setWhiteboardModeEnabled(enabled: Boolean) {
        isWhiteboardModeEnabled = enabled
        canvasView?.setBackgroundColor(if (enabled) Color.WHITE else Color.TRANSPARENT)
        drawingView?.drawingEnabled = isDrawModeEnabled || enabled
        drawingView?.whiteboardMode = enabled
        handler.removeCallbacks(resetCanvasRunnable)
        updateCanvasTouchability()

        toolbarView?.findViewById<ImageButton?>(R.id.btnWhiteboardMode)?.setColorFilter(
            if (enabled) 0xFF03A9F4.toInt() else 0xFFFFFFFF.toInt()
        )
        toolbarView?.findViewById<ImageButton?>(R.id.btnWhiteboardSnapshot)?.visibility =
            if (enabled) View.VISIBLE else View.GONE
    }

    private fun saveWhiteboardSnapshot() {
        if (!isWhiteboardModeEnabled) {
            Toast.makeText(this, "Turn on whiteboard first", Toast.LENGTH_SHORT).show()
            return
        }

        val snapshot = drawingView?.createSnapshot(Color.WHITE)
        if (snapshot == null) {
            Toast.makeText(this, "Whiteboard is not ready yet", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val dir = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "whiteboards")
            if (!dir.exists()) dir.mkdirs()
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val file = File(dir, "SketchOnTop_$timestamp.png")
            FileOutputStream(file).use { output ->
                snapshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output)
            }
            Toast.makeText(this, "Saved whiteboard snapshot", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Could not save snapshot", Toast.LENGTH_SHORT).show()
        } finally {
            snapshot.recycle()
        }
    }

    private fun updateCanvasTouchability() {
        when {
            isWhiteboardModeEnabled -> setCanvasTouchable(true)
            !isDrawModeEnabled -> setCanvasTouchable(false)
            accessibilityStylusModeActive && isSPenModeEnabled -> setCanvasTouchable(false)
            else -> setCanvasTouchable(true)
        }
    }

    private fun applyDrawingSettings() {
        currentColor = SPenSettings.currentColor(this)
        isGradientModeEnabled = SPenSettings.gradientEnabled(this)

        drawingView?.let { view ->
            DrawingView.Tool.values().forEach { tool ->
                view.setStrokeWidthForTool(tool, SPenSettings.toolWidth(this, tool))
            }
            view.setColor(currentColor)
            view.eraserMode = SPenSettings.eraserMode(this)
            view.eraserPressureEnabled = SPenSettings.eraserPressureEnabled(this)
            view.gradientBrushEnabled = isGradientModeEnabled
            view.currentGradient = SPenSettings.gradientPreset(this)
            view.setTool(SPenSettings.savedTool(this))
        }

        toolbarView?.findViewById<ImageButton?>(R.id.btnGradientMode)?.setColorFilter(
            if (isGradientModeEnabled) 0xFFFF9800.toInt() else 0xFFFFFFFF.toInt()
        )
        toolbarView?.findViewById<HorizontalScrollView?>(R.id.gradientPresetsContainer)?.visibility =
            if (isGradientModeEnabled) View.VISIBLE else View.GONE
        updateBrushPreview()
    }

    private fun saveCurrentToolSettings() {
        val tool = drawingView?.getTool() ?: return
        val key = when (tool) {
            DrawingView.Tool.PEN -> SPenSettings.KEY_PEN_WIDTH
            DrawingView.Tool.HIGHLIGHTER -> SPenSettings.KEY_HIGHLIGHTER_WIDTH
            DrawingView.Tool.ERASER -> SPenSettings.KEY_ERASER_WIDTH
        }
        SPenSettings.prefs(this)
            .edit()
            .putFloat(key, drawingView?.getStrokeWidth() ?: 10f)
            .apply()
    }

    private fun saveDrawingSettings() {
        val view = drawingView ?: return
        val editor = SPenSettings.prefs(this).edit()
            .putString(SPenSettings.KEY_SELECTED_TOOL, view.getTool().name)
            .putInt(SPenSettings.KEY_CURRENT_COLOR, currentColor)
            .putString(SPenSettings.KEY_ERASER_MODE, view.eraserMode.name)
            .putBoolean(SPenSettings.KEY_ERASER_PRESSURE_ENABLED, view.eraserPressureEnabled)
            .putBoolean(SPenSettings.KEY_GRADIENT_ENABLED, isGradientModeEnabled)
            .putString(SPenSettings.KEY_GRADIENT_PRESET, view.currentGradient.name)

        val widthKey = when (view.getTool()) {
            DrawingView.Tool.PEN -> SPenSettings.KEY_PEN_WIDTH
            DrawingView.Tool.HIGHLIGHTER -> SPenSettings.KEY_HIGHLIGHTER_WIDTH
            DrawingView.Tool.ERASER -> SPenSettings.KEY_ERASER_WIDTH
        }
        editor.putFloat(widthKey, view.getStrokeWidth()).apply()
    }

    private fun selectTool(tool: DrawingView.Tool, persist: Boolean = true) {
        drawingView?.setTool(tool)
        if (persist) {
            saveDrawingSettings()
        }
        
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
        closeSecondaryPanels()
        toolbar?.visibility = View.GONE
        btnExpand?.visibility = View.VISIBLE
    }

    private fun expandToolbar() {
        isToolbarExpanded = true
        toolbar?.visibility = View.VISIBLE
        btnExpand?.visibility = View.GONE
    }

    private fun openPenSettingsPage() {
        val penSettingsPage = toolbarView?.findViewById<View>(R.id.penSettingsPage) ?: return
        closeSecondaryPanels()
        toolbar?.visibility = View.GONE
        penSettingsPage.visibility = View.VISIBLE
    }

    private fun closeSecondaryPanels() {
        toolbarView?.findViewById<View>(R.id.overflowPage)?.visibility = View.GONE
        toolbarView?.findViewById<View>(R.id.penSettingsPage)?.visibility = View.GONE
        toolbarView?.findViewById<View>(R.id.colorPickerPage)?.visibility = View.GONE
        toolbarView?.findViewById<View>(R.id.eraserPage)?.visibility = View.GONE
        if (isToolbarExpanded) {
            toolbar?.visibility = View.VISIBLE
        }
    }

    private fun openOverflowPage() {
        val overflowPage = toolbarView?.findViewById<View>(R.id.overflowPage) ?: return
        closeSecondaryPanels()
        toolbar?.visibility = View.GONE
        overflowPage.visibility = View.VISIBLE
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
