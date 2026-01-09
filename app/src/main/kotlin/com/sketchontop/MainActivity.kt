package com.sketchontop

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/**
 * Main Activity hosting the transparent drawing overlay.
 * Provides a floating toolbar for tool selection and canvas controls.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var drawingView: DrawingView
    
    // Tool buttons
    private lateinit var btnPen: ImageButton
    private lateinit var btnHighlighter: ImageButton
    private lateinit var btnEraser: ImageButton
    
    // Color buttons
    private lateinit var colorBlack: ImageButton
    private lateinit var colorRed: ImageButton
    private lateinit var colorBlue: ImageButton
    private lateinit var colorGreen: ImageButton
    private lateinit var colorYellow: ImageButton
    private lateinit var colorOrange: ImageButton
    private lateinit var colorPurple: ImageButton
    private lateinit var colorWhite: ImageButton
    
    // Action buttons
    private lateinit var btnUndo: ImageButton
    private lateinit var btnRedo: ImageButton
    private lateinit var btnClear: ImageButton
    private lateinit var btnClose: ImageButton
    
    // Stroke width
    private lateinit var strokeWidthSeekBar: SeekBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Enable edge-to-edge drawing
        setupFullscreen()
        
        setContentView(R.layout.activity_main)
        
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
        
        // Tool buttons
        btnPen = findViewById(R.id.btnPen)
        btnHighlighter = findViewById(R.id.btnHighlighter)
        btnEraser = findViewById(R.id.btnEraser)
        
        // Color buttons
        colorBlack = findViewById(R.id.colorBlack)
        colorRed = findViewById(R.id.colorRed)
        colorBlue = findViewById(R.id.colorBlue)
        colorGreen = findViewById(R.id.colorGreen)
        colorYellow = findViewById(R.id.colorYellow)
        colorOrange = findViewById(R.id.colorOrange)
        colorPurple = findViewById(R.id.colorPurple)
        colorWhite = findViewById(R.id.colorWhite)
        
        // Action buttons
        btnUndo = findViewById(R.id.btnUndo)
        btnRedo = findViewById(R.id.btnRedo)
        btnClear = findViewById(R.id.btnClear)
        btnClose = findViewById(R.id.btnClose)
        
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
        
        // Color selection
        colorBlack.setOnClickListener { selectColor(ContextCompat.getColor(this, R.color.black)) }
        colorRed.setOnClickListener { selectColor(ContextCompat.getColor(this, R.color.red)) }
        colorBlue.setOnClickListener { selectColor(ContextCompat.getColor(this, R.color.blue)) }
        colorGreen.setOnClickListener { selectColor(ContextCompat.getColor(this, R.color.green)) }
        colorYellow.setOnClickListener { selectColor(ContextCompat.getColor(this, R.color.yellow)) }
        colorOrange.setOnClickListener { selectColor(ContextCompat.getColor(this, R.color.orange)) }
        colorPurple.setOnClickListener { selectColor(ContextCompat.getColor(this, R.color.purple)) }
        colorWhite.setOnClickListener { selectColor(ContextCompat.getColor(this, R.color.white)) }
        
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
     * Selects a drawing tool and updates button states.
     */
    private fun selectTool(tool: DrawingView.Tool) {
        drawingView.setTool(tool)
        
        // Update visual selection states
        btnPen.isSelected = (tool == DrawingView.Tool.PEN)
        btnHighlighter.isSelected = (tool == DrawingView.Tool.HIGHLIGHTER)
        btnEraser.isSelected = (tool == DrawingView.Tool.ERASER)
    }
    
    /**
     * Sets the drawing color.
     */
    private fun selectColor(color: Int) {
        drawingView.setColor(color)
        
        // If eraser was selected, switch to pen when color is chosen
        if (drawingView.getTool() == DrawingView.Tool.ERASER) {
            selectTool(DrawingView.Tool.PEN)
        }
    }
    
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            // Re-apply fullscreen when window regains focus
            setupFullscreen()
        }
    }
}
