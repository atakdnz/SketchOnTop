package com.sketchontop

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap

/**
 * Main Activity hosting the transparent drawing overlay.
 * Provides a floating toolbar for tool selection and canvas controls.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var drawingView: DrawingView
    
    // Toolbar container
    private lateinit var toolbar: LinearLayout
    private lateinit var btnExpand: ImageButton
    
    // Tool buttons
    private lateinit var btnPen: ImageButton
    private lateinit var btnHighlighter: ImageButton
    private lateinit var btnEraser: ImageButton
    
    // Action buttons
    private lateinit var btnMinimize: ImageButton
    private lateinit var btnToggleVisibility: ImageButton
    private lateinit var btnUndo: ImageButton
    private lateinit var btnRedo: ImageButton
    private lateinit var btnClear: ImageButton
    private lateinit var btnClose: ImageButton
    
    // Color picker
    private lateinit var colorPickerGradient: View
    private lateinit var currentColorIndicator: View
    
    // Stroke width
    private lateinit var strokeWidthSeekBar: SeekBar
    
    // State
    private var currentColor: Int = Color.BLACK
    private var isToolbarExpanded: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContentView(R.layout.activity_main)
        
        // Enable edge-to-edge drawing (after setContentView)
        setupFullscreen()
        
        // Initialize views
        initViews()
        
        // Setup toolbar interactions
        setupToolbar()
        
        // Set default tool
        selectTool(DrawingView.Tool.PEN)
    }
    
    /**
     * Configures the activity for fullscreen transparent overlay.
     */
    private fun setupFullscreen() {
        // Make the activity extend into system bars
        window.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )
        
        // Hide system bars for immersive experience
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.systemBars())
                controller.systemBarsBehavior = 
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
            )
        }
    }
    
    /**
     * Finds and initializes all view references.
     */
    private fun initViews() {
        drawingView = findViewById(R.id.drawingView)
        
        // Toolbar
        toolbar = findViewById(R.id.toolbar)
        btnExpand = findViewById(R.id.btnExpand)
        
        // Tool buttons
        btnPen = findViewById(R.id.btnPen)
        btnHighlighter = findViewById(R.id.btnHighlighter)
        btnEraser = findViewById(R.id.btnEraser)
        
        // Action buttons
        btnMinimize = findViewById(R.id.btnMinimize)
        btnToggleVisibility = findViewById(R.id.btnToggleVisibility)
        btnUndo = findViewById(R.id.btnUndo)
        btnRedo = findViewById(R.id.btnRedo)
        btnClear = findViewById(R.id.btnClear)
        btnClose = findViewById(R.id.btnClose)
        
        // Color picker
        colorPickerGradient = findViewById(R.id.colorPickerGradient)
        currentColorIndicator = findViewById(R.id.currentColorIndicator)
        
        // Stroke width
        strokeWidthSeekBar = findViewById(R.id.strokeWidthSeekBar)
    }
    
    /**
     * Sets up all toolbar button click listeners.
     */
    private fun setupToolbar() {
        // Tool selection
        btnPen.setOnClickListener { selectTool(DrawingView.Tool.PEN) }
        btnHighlighter.setOnClickListener { selectTool(DrawingView.Tool.HIGHLIGHTER) }
        btnEraser.setOnClickListener { selectTool(DrawingView.Tool.ERASER) }
        
        // Minimize/expand toolbar
        btnMinimize.setOnClickListener { minimizeToolbar() }
        btnExpand.setOnClickListener { expandToolbar() }
        
        // Toggle drawings visibility
        btnToggleVisibility.setOnClickListener { toggleDrawingsVisibility() }
        
        // Gradient color picker - touch to pick color
        setupGradientColorPicker()
        
        // Stroke width slider
        strokeWidthSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                // Map progress (0-50) to stroke width (2-52)
                val strokeWidth = (progress + 2).toFloat()
                drawingView.setStrokeWidth(strokeWidth)
            }
            
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        
        // Action buttons
        btnUndo.setOnClickListener { drawingView.undo() }
        btnRedo.setOnClickListener { drawingView.redo() }
        btnClear.setOnClickListener { drawingView.clear() }
        btnClose.setOnClickListener { finish() }
    }
    
    /**
     * Sets up the gradient color picker touch handling.
     */
    private fun setupGradientColorPicker() {
        colorPickerGradient.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    val color = getColorFromGradient(view, event.x, event.y)
                    selectColor(color)
                    true
                }
                else -> false
            }
        }
    }
    
    /**
     * Gets a color from the gradient view at the specified coordinates.
     */
    private fun getColorFromGradient(view: View, x: Float, y: Float): Int {
        // Create a bitmap from the gradient drawable
        val drawable = view.background
        if (drawable is GradientDrawable) {
            // For gradient drawable, calculate color based on position
            val width = view.width.toFloat()
            val ratio = (x.coerceIn(0f, width) / width)
            
            // Full spectrum: Red -> Yellow -> Green -> Cyan -> Blue -> Magenta -> Red
            val hue = ratio * 360f
            return Color.HSVToColor(floatArrayOf(hue, 1f, 1f))
        }
        
        // Fallback: try to get pixel from a bitmap
        try {
            val bitmap = drawable.toBitmap(view.width, view.height)
            val px = x.toInt().coerceIn(0, bitmap.width - 1)
            val py = y.toInt().coerceIn(0, bitmap.height - 1)
            return bitmap.getPixel(px, py)
        } catch (e: Exception) {
            return Color.BLACK
        }
    }
    
    /**
     * Selects a drawing tool and updates button states.
     * Also updates the SeekBar to show the tool's saved stroke width.
     */
    private fun selectTool(tool: DrawingView.Tool) {
        drawingView.setTool(tool)
        
        // Update visual selection states
        btnPen.isSelected = (tool == DrawingView.Tool.PEN)
        btnHighlighter.isSelected = (tool == DrawingView.Tool.HIGHLIGHTER)
        btnEraser.isSelected = (tool == DrawingView.Tool.ERASER)
        
        // Update SeekBar to reflect this tool's saved stroke width
        val toolWidth = drawingView.getStrokeWidth()
        strokeWidthSeekBar.progress = (toolWidth - 2).toInt().coerceIn(0, 50)
    }
    
    /**
     * Sets the drawing color.
     */
    private fun selectColor(color: Int) {
        currentColor = color
        drawingView.setColor(color)
        
        // Update indicator
        currentColorIndicator.setBackgroundColor(color)
        
        // If eraser was selected, switch to pen when color is chosen
        if (drawingView.getTool() == DrawingView.Tool.ERASER) {
            selectTool(DrawingView.Tool.PEN)
        }
    }
    
    /**
     * Minimizes the toolbar to a small button.
     */
    private fun minimizeToolbar() {
        isToolbarExpanded = false
        toolbar.visibility = View.GONE
        btnExpand.visibility = View.VISIBLE
    }
    
    /**
     * Expands the toolbar from the minimized button.
     */
    private fun expandToolbar() {
        isToolbarExpanded = true
        toolbar.visibility = View.VISIBLE
        btnExpand.visibility = View.GONE
    }
    
    /**
     * Toggles visibility of all drawings.
     */
    private fun toggleDrawingsVisibility() {
        val isVisible = drawingView.toggleDrawingsVisibility()
        
        // Update button icon
        val iconRes = if (isVisible) R.drawable.ic_visibility else R.drawable.ic_visibility_off
        btnToggleVisibility.setImageResource(iconRes)
    }
    
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            // Re-apply fullscreen when window regains focus
            setupFullscreen()
        }
    }
}
