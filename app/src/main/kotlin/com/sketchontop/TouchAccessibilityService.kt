package com.sketchontop

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.view.MotionEvent
import android.view.accessibility.AccessibilityEvent

/**
 * AccessibilityService for enhanced S Pen mode.
 * 
 * This service intercepts touch events and can distinguish between
 * stylus and finger input BEFORE they reach any window, allowing
 * perfect S Pen-only drawing while fingers interact with the phone.
 * 
 * User must manually enable this in Settings → Accessibility → SketchOnTop.
 */
class TouchAccessibilityService : AccessibilityService() {

    companion object {
        // Static reference for communication with OverlayService
        var instance: TouchAccessibilityService? = null
            private set
        
        var isServiceEnabled: Boolean = false
            private set
        
        // Callback for stylus events - set by OverlayService
        var onStylusTouchEvent: ((MotionEvent) -> Boolean)? = null
        
        // Whether S Pen mode is active (set by OverlayService)
        var sPenModeActive: Boolean = false
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        isServiceEnabled = true
        
        // Configure to receive touch events
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE or
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            notificationTimeout = 0
        }
        serviceInfo = info
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // We don't need to handle accessibility events, just touch exploration
    }

    override fun onInterrupt() {
        // Required but not used
    }

    /**
     * Called when a touch event is detected.
     * This is the key method - we can intercept touches before they reach apps.
     */
    override fun onMotionEvent(event: MotionEvent) {
        if (!sPenModeActive) {
            // S Pen mode not active, let all events pass through normally
            super.onMotionEvent(event)
            return
        }
        
        val toolType = event.getToolType(0)
        
        when (toolType) {
            MotionEvent.TOOL_TYPE_STYLUS, MotionEvent.TOOL_TYPE_ERASER -> {
                // Stylus event - forward to our drawing overlay
                val consumed = onStylusTouchEvent?.invoke(event) ?: false
                if (!consumed) {
                    super.onMotionEvent(event)
                }
            }
            MotionEvent.TOOL_TYPE_FINGER -> {
                // Finger event - let it pass through to underlying apps
                super.onMotionEvent(event)
            }
            else -> {
                super.onMotionEvent(event)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        isServiceEnabled = false
    }
}
