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
 * Uses two separate windows: canvas (can be non-touchable) and toolbar (always touchable).
 * 
 * In S Pen mode, the canvas is non-touchable by default (fingers pass through).
 * When S Pen hovers, the canvas temporarily becomes touchable for drawing.
 */
class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    
    // Separate windows for canvas and toolbar
    private var canvasView: View? = null
    private var toolbarView: View? = null
    private var canvasParams: WindowManager.LayoutParams? = null
    
    private var drawingView: DrawingView? = null
    private var hoverDetector: HoverDetectorView? = null
    private var toolbar: LinearLayout? = null
    private var btnExpand: ImageButton? = null
    
    // State
    private var isDrawModeEnabled = true
    private var isSPenModeEnabled = false
    private var isGradientModeEnabled = false
    private var isToolbarExpanded = true
    private var currentColor = Color.BLACK
    
    // S Pen hover state
    private var isStylusHovering = false
    private var isStylusDrawing = false
    private val handler = Handler(Looper.getMainLooper())
    private val disableCanvasRunnable = Runnable { 
        if (isSPenModeEnabled && !isStylusDrawing) {
            setCanvasTouchable(false)
        }
    }

    companion object {
        const val CHANNEL_ID = "SketchOnTopChannel"
        const val NOTIFICATION_ID = 1
        const val ACTION_STOP = "com.sketchontop.STOP"
        const val HOVER_TIMEOUT_MS = 500L
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
        
        // === Canvas Window (full screen, can be made non-touchable) ===
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
        val toolbarParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 16
            y = 48
        }
        windowManager.addView(toolbarView, toolbarParams)
        
        // Initialize views
        initViews()
        setupHoverDetection()
        setupToolbar()
        setupGradientPresets()
        
        // Set default tool
        selectTool(DrawingView.Tool.PEN)
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
        hoverDetector = null
    }

    private fun initViews() {
        drawingView = canvasView?.findViewById(R.id.drawingView)
        hoverDetector = canvasView?.findViewById(R.id.hoverDetector)
        toolbar = toolbarView?.findViewById(R.id.toolbar)
        btnExpand = toolbarView?.findViewById(R.id.btnExpand)
    }
    
    /**
     * Sets up S Pen hover detection for dynamic canvas touchability.
     */
    private fun setupHoverDetection() {
        hoverDetector?.onHoverStateChanged = { isHovering, isStylus ->
            if (isSPenModeEnabled && isStylus) {
                isStylusHovering = isHovering
                if (isHovering) {
                    // S Pen is hovering - enable canvas for drawing
                    handler.removeCallbacks(disableCanvasRunnable)
                    setCanvasTouchable(true)
                } else {
                    // S Pen left - disable canvas after delay (allows for quick re-hover)
                    handler.postDelayed(disableCanvasRunnable, HOVER_TIMEOUT_MS)
                }
            }
        }
        
        hoverDetector?.onStylusTouchDetected = {
            if (isSPenModeEnabled) {
                // Stylus touched - keep canvas enabled
                isStylusDrawing = true
                handler.removeCallbacks(disableCanvasRunnable)
                setCanvasTouchable(true)
            }
        }
        
        // Also track when drawing ends via DrawingView
        drawingView?.setOnTouchListener { _, event ->
            if (isSPenModeEnabled) {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        isStylusDrawing = true
                        handler.removeCallbacks(disableCanvasRunnable)
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        isStylusDrawing = false
                        // Disable canvas after drawing ends (with delay)
                        handler.postDelayed(disableCanvasRunnable, HOVER_TIMEOUT_MS)
                    }
                }
            }
            false // Let DrawingView handle the event
        }
    }
    
    /**
     * Sets canvas touchability directly.
     */
    private fun setCanvasTouchable(touchable: Boolean) {
        canvasView?.let { view ->
            canvasParams?.let { params ->
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
                selectTool(DrawingView.Tool.ERASER) 
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
                    }
                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                }
            )
        }
    }

    private fun setupColorPicker(view: View) {
        val colorPicker = view.findViewById<View>(R.id.colorPickerGradient)
        val colorIndicator = view.findViewById<View>(R.id.currentColorIndicator)
        
        colorPicker.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    val ratio = (event.x.coerceIn(0f, v.width.toFloat()) / v.width)
                    val color = Color.HSVToColor(floatArrayOf(ratio * 360f, 1f, 1f))
                    
                    currentColor = color
                    drawingView?.setColor(color)
                    colorIndicator.setBackgroundColor(color)
                    
                    if (drawingView?.getTool() == DrawingView.Tool.ERASER) {
                        selectTool(DrawingView.Tool.PEN)
                    }
                    true
                }
                else -> false
            }
        }
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
        updateCanvasTouchability()
        
        // Update button tint
        toolbarView?.findViewById<ImageButton>(R.id.btnDrawMode)?.let { btn ->
            btn.setColorFilter(if (isDrawModeEnabled) 0xFF4CAF50.toInt() else 0xFF888888.toInt())
        }
    }

    private fun toggleSPenMode() {
        isSPenModeEnabled = !isSPenModeEnabled
        drawingView?.sPenOnlyMode = isSPenModeEnabled
        
        // When S Pen mode turns on, make canvas non-touchable initially
        // It will become touchable when S Pen hovers
        updateCanvasTouchability()
        
        toolbarView?.findViewById<ImageButton>(R.id.btnSPenMode)?.let { btn ->
            btn.setColorFilter(if (isSPenModeEnabled) 0xFF2196F3.toInt() else 0xFFFFFFFF.toInt())
        }
    }
    
    /**
     * Updates canvas touchability based on current mode.
     */
    private fun updateCanvasTouchability() {
        // If draw mode is off, canvas is non-touchable
        // If S Pen mode is on, canvas starts non-touchable (hover will enable it)
        val shouldBeNonTouchable = !isDrawModeEnabled || isSPenModeEnabled
        setCanvasTouchable(!shouldBeNonTouchable)
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

    private fun toggleDrawingsVisibility() {
        val isVisible = drawingView?.toggleDrawingsVisibility() ?: true
        val iconRes = if (isVisible) R.drawable.ic_visibility else R.drawable.ic_visibility_off
        toolbarView?.findViewById<ImageButton>(R.id.btnToggleVisibility)?.setImageResource(iconRes)
    }
}
