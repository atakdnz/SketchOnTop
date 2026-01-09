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
import android.os.IBinder
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
 * This allows drawing on top of other apps while keeping the OS usable.
 */
class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private var drawingView: DrawingView? = null
    
    // UI elements
    private var toolbar: LinearLayout? = null
    private var btnExpand: ImageButton? = null
    
    // State
    private var isDrawModeEnabled = true
    private var isSPenModeEnabled = false
    private var isGradientModeEnabled = false
    private var isToolbarExpanded = true
    private var currentColor = Color.BLACK

    companion object {
        const val CHANNEL_ID = "SketchOnTopChannel"
        const val NOTIFICATION_ID = 1
        const val ACTION_STOP = "com.sketchontop.STOP"
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
        removeOverlay()
    }

    /**
     * Creates the notification channel for the foreground service.
     */
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

    /**
     * Creates the foreground notification.
     */
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
     * Creates the overlay window with drawing view and toolbar.
     */
    private fun createOverlay() {
        // Inflate the overlay layout
        val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        overlayView = inflater.inflate(R.layout.overlay_layout, null)
        
        // Setup the window parameters
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE,
            // Initially set to receive touches
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        
        // Add the overlay to window manager
        windowManager.addView(overlayView, params)
        
        // Initialize views
        initViews()
        setupToolbar()
        setupGradientPresets()
        
        // Set default tool
        selectTool(DrawingView.Tool.PEN)
    }

    /**
     * Removes the overlay from the window manager.
     */
    private fun removeOverlay() {
        overlayView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                // View might already be removed
            }
        }
        overlayView = null
        drawingView = null
    }

    /**
     * Initialize all view references.
     */
    private fun initViews() {
        overlayView?.let { view ->
            drawingView = view.findViewById(R.id.drawingView)
            toolbar = view.findViewById(R.id.toolbar)
            btnExpand = view.findViewById(R.id.btnExpand)
        }
    }

    /**
     * Sets up toolbar button click listeners.
     */
    private fun setupToolbar() {
        overlayView?.let { view ->
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
                toggleDrawingsVisibility(view) 
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
                        val width = (progress + 2).toFloat()
                        drawingView?.setStrokeWidth(width)
                    }
                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                }
            )
        }
    }

    /**
     * Sets up the color picker touch handling.
     */
    private fun setupColorPicker(view: View) {
        val colorPicker = view.findViewById<View>(R.id.colorPickerGradient)
        val colorIndicator = view.findViewById<View>(R.id.currentColorIndicator)
        
        colorPicker.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    val width = v.width.toFloat()
                    val ratio = (event.x.coerceIn(0f, width) / width)
                    val hue = ratio * 360f
                    val color = Color.HSVToColor(floatArrayOf(hue, 1f, 1f))
                    
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

    /**
     * Sets up gradient preset buttons.
     */
    private fun setupGradientPresets() {
        overlayView?.let { view ->
            val container = view.findViewById<LinearLayout>(R.id.gradientPresets)
            val presets = DrawingView.GradientPreset.values().filter { 
                it != DrawingView.GradientPreset.CUSTOM 
            }
            
            for (preset in presets) {
                val button = View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(48, 24).apply {
                        marginEnd = 4
                    }
                    background = GradientDrawable(
                        GradientDrawable.Orientation.LEFT_RIGHT,
                        preset.colors
                    ).apply { cornerRadius = 4f }
                    
                    setOnClickListener {
                        drawingView?.currentGradient = preset
                    }
                }
                container.addView(button)
            }
        }
    }

    /**
     * Toggles draw mode and updates window flags for touch passthrough.
     */
    private fun toggleDrawMode() {
        isDrawModeEnabled = !isDrawModeEnabled
        drawingView?.drawingEnabled = isDrawModeEnabled
        
        // Update window flags for touch passthrough
        updateWindowFlags()
        
        // Update button color
        overlayView?.findViewById<ImageButton>(R.id.btnDrawMode)?.let { btn ->
            val tint = if (isDrawModeEnabled) 0xFF4CAF50.toInt() else 0xFF888888.toInt()
            btn.setColorFilter(tint)
        }
    }

    /**
     * Toggles S Pen only mode.
     */
    private fun toggleSPenMode() {
        isSPenModeEnabled = !isSPenModeEnabled
        drawingView?.sPenOnlyMode = isSPenModeEnabled
        
        // Update window flags
        updateWindowFlags()
        
        overlayView?.findViewById<ImageButton>(R.id.btnSPenMode)?.let { btn ->
            val tint = if (isSPenModeEnabled) 0xFF2196F3.toInt() else 0xFFFFFFFF.toInt()
            btn.setColorFilter(tint)
        }
    }

    /**
     * Updates window flags based on current mode.
     * When draw mode is off (and S Pen mode is off), touches pass through.
     */
    private fun updateWindowFlags() {
        overlayView?.let { view ->
            val params = view.layoutParams as WindowManager.LayoutParams
            
            if (!isDrawModeEnabled && !isSPenModeEnabled) {
                // Pass all touches through to underlying apps
                params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            } else {
                // Receive touches
                params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            }
            
            windowManager.updateViewLayout(view, params)
        }
    }

    /**
     * Toggles gradient brush mode.
     */
    private fun toggleGradientMode() {
        isGradientModeEnabled = !isGradientModeEnabled
        drawingView?.gradientBrushEnabled = isGradientModeEnabled
        
        overlayView?.let { view ->
            view.findViewById<ImageButton>(R.id.btnGradientMode)?.let { btn ->
                val tint = if (isGradientModeEnabled) 0xFFFF9800.toInt() else 0xFFFFFFFF.toInt()
                btn.setColorFilter(tint)
            }
            
            view.findViewById<HorizontalScrollView>(R.id.gradientPresetsContainer)?.visibility = 
                if (isGradientModeEnabled) View.VISIBLE else View.GONE
        }
    }

    /**
     * Selects a drawing tool.
     */
    private fun selectTool(tool: DrawingView.Tool) {
        drawingView?.setTool(tool)
        
        overlayView?.let { view ->
            view.findViewById<ImageButton>(R.id.btnPen).isSelected = 
                (tool == DrawingView.Tool.PEN)
            view.findViewById<ImageButton>(R.id.btnHighlighter).isSelected = 
                (tool == DrawingView.Tool.HIGHLIGHTER)
            view.findViewById<ImageButton>(R.id.btnEraser).isSelected = 
                (tool == DrawingView.Tool.ERASER)
            
            // Update seek bar
            val width = drawingView?.getStrokeWidth() ?: 10f
            view.findViewById<SeekBar>(R.id.strokeWidthSeekBar).progress = 
                (width - 2).toInt().coerceIn(0, 50)
        }
    }

    /**
     * Minimizes the toolbar.
     */
    private fun minimizeToolbar() {
        isToolbarExpanded = false
        toolbar?.visibility = View.GONE
        btnExpand?.visibility = View.VISIBLE
    }

    /**
     * Expands the toolbar.
     */
    private fun expandToolbar() {
        isToolbarExpanded = true
        toolbar?.visibility = View.VISIBLE
        btnExpand?.visibility = View.GONE
    }

    /**
     * Toggles drawing visibility.
     */
    private fun toggleDrawingsVisibility(view: View) {
        val isVisible = drawingView?.toggleDrawingsVisibility() ?: true
        val iconRes = if (isVisible) R.drawable.ic_visibility else R.drawable.ic_visibility_off
        view.findViewById<ImageButton>(R.id.btnToggleVisibility).setImageResource(iconRes)
    }
}
