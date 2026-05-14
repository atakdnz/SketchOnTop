package com.sketchontop

import android.content.Context
import android.content.SharedPreferences

object SPenSettings {
    const val PREFS_NAME = "sketch_on_top_settings"
    const val KEY_SPEN_MODE_ENABLED = "spen_mode_enabled"
    const val KEY_SPEN_BUTTON_TOGGLES_DRAW_MODE = "spen_button_toggles_draw_mode"
    const val KEY_FINGER_PASSTHROUGH_RESET_DELAY_MS = "finger_passthrough_reset_delay_ms"
    const val KEY_SPEN_BUTTON_REENABLE_DELAY_MS = "spen_button_reenable_delay_ms"
    const val KEY_HOVER_ARM_DRAWING = "hover_arm_drawing"
    const val KEY_HOVER_EXIT_DELAY_MS = "hover_exit_delay_ms"
    const val KEY_ACCESSIBILITY_STYLUS_INPUT = "accessibility_stylus_input"
    const val KEY_COMPACT_TOOLBAR = "compact_toolbar"
    const val KEY_SELECTED_TOOL = "selected_tool"
    const val KEY_CURRENT_COLOR = "current_color"
    const val KEY_PEN_WIDTH = "pen_width"
    const val KEY_HIGHLIGHTER_WIDTH = "highlighter_width"
    const val KEY_ERASER_WIDTH = "eraser_width"
    const val KEY_ERASER_MODE = "eraser_mode"
    const val KEY_ERASER_PRESSURE_ENABLED = "eraser_pressure_enabled"
    const val KEY_GRADIENT_ENABLED = "gradient_enabled"
    const val KEY_GRADIENT_PRESET = "gradient_preset"

    const val DEFAULT_FINGER_PASSTHROUGH_RESET_DELAY_MS = 5000L
    const val DEFAULT_SPEN_BUTTON_REENABLE_DELAY_MS = 1500L
    const val DEFAULT_HOVER_EXIT_DELAY_MS = 800L

    private const val MIN_DELAY_MS = 0L
    private const val MAX_DELAY_MS = 30000L

    fun prefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun sPenButtonTogglesDrawMode(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_SPEN_BUTTON_TOGGLES_DRAW_MODE, false)
    }

    fun sPenModeEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_SPEN_MODE_ENABLED, false)
    }

    fun fingerPassthroughResetDelayMs(context: Context): Long {
        return prefs(context)
            .getLong(KEY_FINGER_PASSTHROUGH_RESET_DELAY_MS, DEFAULT_FINGER_PASSTHROUGH_RESET_DELAY_MS)
            .coerceIn(MIN_DELAY_MS, MAX_DELAY_MS)
    }

    fun sPenButtonReenableDelayMs(context: Context): Long {
        return prefs(context)
            .getLong(KEY_SPEN_BUTTON_REENABLE_DELAY_MS, DEFAULT_SPEN_BUTTON_REENABLE_DELAY_MS)
            .coerceIn(MIN_DELAY_MS, MAX_DELAY_MS)
    }

    fun hoverArmsDrawing(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_HOVER_ARM_DRAWING, false)
    }

    fun hoverExitDelayMs(context: Context): Long {
        return prefs(context)
            .getLong(KEY_HOVER_EXIT_DELAY_MS, DEFAULT_HOVER_EXIT_DELAY_MS)
            .coerceIn(MIN_DELAY_MS, MAX_DELAY_MS)
    }

    fun accessibilityStylusInput(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_ACCESSIBILITY_STYLUS_INPUT, true)
    }

    fun compactToolbar(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_COMPACT_TOOLBAR, true)
    }

    fun sanitizeDelay(value: Long): Long = value.coerceIn(MIN_DELAY_MS, MAX_DELAY_MS)

    fun savedTool(context: Context): DrawingView.Tool {
        return enumValueOrDefault(
            prefs(context).getString(KEY_SELECTED_TOOL, null),
            DrawingView.Tool.PEN
        )
    }

    fun currentColor(context: Context): Int {
        return prefs(context).getInt(KEY_CURRENT_COLOR, android.graphics.Color.BLACK)
    }

    fun toolWidth(context: Context, tool: DrawingView.Tool): Float {
        val key = when (tool) {
            DrawingView.Tool.PEN -> KEY_PEN_WIDTH
            DrawingView.Tool.HIGHLIGHTER -> KEY_HIGHLIGHTER_WIDTH
            DrawingView.Tool.ERASER -> KEY_ERASER_WIDTH
        }
        val defaultValue = when (tool) {
            DrawingView.Tool.PEN -> 8f
            DrawingView.Tool.HIGHLIGHTER -> 20f
            DrawingView.Tool.ERASER -> 30f
        }
        return prefs(context).getFloat(key, defaultValue).coerceIn(1f, 100f)
    }

    fun eraserMode(context: Context): DrawingView.EraserMode {
        return enumValueOrDefault(
            prefs(context).getString(KEY_ERASER_MODE, null),
            DrawingView.EraserMode.PIXEL
        )
    }

    fun eraserPressureEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_ERASER_PRESSURE_ENABLED, false)
    }

    fun gradientEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_GRADIENT_ENABLED, false)
    }

    fun gradientPreset(context: Context): DrawingView.GradientPreset {
        return enumValueOrDefault(
            prefs(context).getString(KEY_GRADIENT_PRESET, null),
            DrawingView.GradientPreset.RAINBOW
        )
    }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String?, defaultValue: T): T {
        return value?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: defaultValue
    }
}
