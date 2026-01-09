package com.sketchontop

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
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
    
    // Mode buttons
    private lateinit var btnDrawMode: ImageButton
    private lateinit var btnSPenMode: ImageButton
    private lateinit var btnGradientMode: ImageButton
    
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
    
    // Gradient presets
    private lateinit var gradientPresetsContainer: HorizontalScrollView
    private lateinit var gradientPresets: LinearLayout
    
    // Stroke width
    private lateinit var strokeWidthSeekBar: SeekBar
    
    // State
    private var currentColor: Int = Color.BLACK
    private var isToolbarExpanded: Boolean = true
    private var isDrawModeEnabled: Boolean = true
    private var isSPenModeEnabled: Boolean = false
    private var isGradientModeEnabled: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContentView(R.layout.activity_main)
        
        // Enable edge-to-edge drawing (after setContentView)
        setupFullscreen()
        
        // Initialize views
        initViews()
        
        // Setup toolbar interactions
        setupToolbar()
        
        // Setup gradient presets
        setupGradientPresets()
        
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
        
        // Mode buttons
        btnDrawMode = findViewById(R.id.btnDrawMode)
        btnSPenMode = findViewById(R.id.btnSPenMode)
        btnGradientMode = findViewById(R.id.btnGradientMode)
        
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
        
        // Gradient presets
        gradientPresetsContainer = findViewById(R.id.gradientPresetsContainer)
        gradientPresets = findViewById(R.id.gradientPresets)
        
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
        
        // Mode toggles
        btnDrawMode.setOnClickListener { toggleDrawMode() }
        btnSPenMode.setOnClickListener { toggleSPenMode() }
        btnGradientMode.setOnClickListener { toggleGradientMode() }
        
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
     * Sets up gradient preset buttons.
     */
    private fun setupGradientPresets() {
        val presets = DrawingView.GradientPreset.values().filter { it != DrawingView.GradientPreset.CUSTOM }
        
        for (preset in presets) {
            val button = createGradientPresetButton(preset)
            gradientPresets.addView(button)
        }
    }
    
    /**
     * Creates a button for a gradient preset.
     */
    private fun createGradientPresetButton(preset: DrawingView.GradientPreset): View {
        val button = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(48, 24).apply {
                marginEnd = 4
            }
            
            // Create gradient drawable
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                preset.colors
            ).apply {
                cornerRadius = 4f
            }
            
            setOnClickListener {
                selectGradientPreset(preset)
            }
        }
        return button
    }
    
    /**
     * Selects a gradient preset.
     */
    private fun selectGradientPreset(preset: DrawingView.GradientPreset) {
        drawingView.currentGradient = preset
    }
    
    /**
     * Sets up the gradient color picker touch handling.
     */
    private fun setupGradientColorPicker() {
        colorPickerGradient.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    val color = getColorFromGradient(view, event.x)
                    selectColor(color)
                    true
                }
                else -> false
            }
        }
    }
    
    /**
     * Gets a color from the gradient view at the specified X coordinate.
     */
    private fun getColorFromGradient(view: View, x: Float): Int {
        val width = view.width.toFloat()
        val ratio = (x.coerceIn(0f, width) / width)
        
        // Full spectrum: use HSV for smooth color picking
        val hue = ratio * 360f
        return Color.HSVToColor(floatArrayOf(hue, 1f, 1f))
    }
    
    /**
     * Toggles draw mode on/off.
     * When off, finger touches pass through to the phone but drawings remain visible.
     */
    private fun toggleDrawMode() {
        isDrawModeEnabled = !isDrawModeEnabled
        drawingView.drawingEnabled = isDrawModeEnabled
        
        // Update button color
        val tintColor = if (isDrawModeEnabled) 0xFF4CAF50.toInt() else 0xFF888888.toInt()
        btnDrawMode.setColorFilter(tintColor)
    }
    
    /**
     * Toggles S Pen only mode.
     * When on, finger touches pass through, only stylus can draw.
     */
    private fun toggleSPenMode() {
        isSPenModeEnabled = !isSPenModeEnabled
        drawingView.sPenOnlyMode = isSPenModeEnabled
        
        // Update button color
        val tintColor = if (isSPenModeEnabled) 0xFF2196F3.toInt() else 0xFFFFFFFF.toInt()
        btnSPenMode.setColorFilter(tintColor)
    }
    
    /**
     * Toggles gradient brush mode.
     */
    private fun toggleGradientMode() {
        isGradientModeEnabled = !isGradientModeEnabled
        drawingView.gradientBrushEnabled = isGradientModeEnabled
        
        // Update button and show/hide presets
        val tintColor = if (isGradientModeEnabled) 0xFFFF9800.toInt() else 0xFFFFFFFF.toInt()
        btnGradientMode.setColorFilter(tintColor)
        
        gradientPresetsContainer.visibility = if (isGradientModeEnabled) View.VISIBLE else View.GONE
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
