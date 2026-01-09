package com.sketchontop

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/**
 * A transparent view that detects S Pen hover events.
 * Used to detect when S Pen is near the screen so we can enable drawing.
 * 
 * This view stays always "touchable" (not FLAG_NOT_TOUCHABLE) so it receives
 * hover events, but it passes all touch events through.
 */
class HoverDetectorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    /** Callback for hover state changes */
    var onHoverStateChanged: ((isHovering: Boolean, isStylus: Boolean) -> Unit)? = null
    
    /** Callback for stylus touch detected */
    var onStylusTouchDetected: (() -> Unit)? = null

    init {
        // This view should be transparent and not draw anything
        setBackgroundColor(0x00000000)
    }

    override fun onHoverEvent(event: MotionEvent): Boolean {
        val isStylus = event.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS
        
        when (event.actionMasked) {
            MotionEvent.ACTION_HOVER_ENTER -> {
                onHoverStateChanged?.invoke(true, isStylus)
            }
            MotionEvent.ACTION_HOVER_EXIT -> {
                onHoverStateChanged?.invoke(false, isStylus)
            }
        }
        
        // Don't consume hover events
        return false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val isStylus = event.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS ||
                       event.getToolType(0) == MotionEvent.TOOL_TYPE_ERASER
        
        if (isStylus && event.actionMasked == MotionEvent.ACTION_DOWN) {
            onStylusTouchDetected?.invoke()
        }
        
        // IMPORTANT: Return false to let the touch pass through to views below
        return false
    }
}
