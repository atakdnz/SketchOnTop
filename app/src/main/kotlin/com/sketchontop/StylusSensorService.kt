package com.sketchontop

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.util.Log
import android.view.InputDevice
import android.view.MotionEvent
import android.view.accessibility.AccessibilityEvent

class StylusSensorService : AccessibilityService() {

    companion object {
        private const val TAG = "SketchOnTopStylus"

        @Volatile
        var isRunning: Boolean = false
            private set

        @Volatile
        var onStylusMotionEvent: ((MotionEvent) -> Unit)? = null

        @Volatile
        private var instance: StylusSensorService? = null

        @Volatile
        private var stylusListeningRequested: Boolean = false

        fun setStylusListeningEnabled(enabled: Boolean) {
            stylusListeningRequested = enabled
            instance?.configureMotionEventSources(enabled)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isRunning = true
        instance = this

        configureMotionEventSources(stylusListeningRequested)
    }

    private fun configureMotionEventSources(enabled: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Log.d(TAG, "Stylus motion events require Android 14+")
            return
        }

        serviceInfo = serviceInfo.apply {
            setMotionEventSources(
                if (enabled) {
                    InputDevice.SOURCE_STYLUS or InputDevice.SOURCE_BLUETOOTH_STYLUS
                } else {
                    0
                }
            )
        }
        Log.d(TAG, "Stylus motion event sources enabled=$enabled")
    }

    override fun onMotionEvent(event: MotionEvent) {
        val pointerIndex = event.actionIndex.coerceAtLeast(0)
        val toolType = event.getToolType(pointerIndex)
        val isStylus = toolType == MotionEvent.TOOL_TYPE_STYLUS ||
            toolType == MotionEvent.TOOL_TYPE_ERASER

        if (!isStylus) return

        Log.d(
            TAG,
            "event action=${event.actionMasked} x=${event.x} y=${event.y} rawX=${event.rawX} rawY=${event.rawY} " +
                "pressure=${event.pressure} history=${event.historySize} buttons=${event.buttonState}"
        )

        onStylusMotionEvent?.invoke(MotionEvent.obtain(event))
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // This service only observes stylus MotionEvents.
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        isRunning = false
        instance = null
        onStylusMotionEvent = null
        super.onDestroy()
    }
}
