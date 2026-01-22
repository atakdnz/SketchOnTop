package com.sketchontop.models

import android.graphics.Paint
import android.graphics.Path

/**
 * Represents a single segment of a stroke.
 * Each segment captures the width and color at that specific point,
 * preserving pressure sensitivity and gradient colors.
 */
data class StrokeSegment(
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float,
    val color: Int,
    val strokeWidth: Float,
    val alpha: Int = 255
)

/**
 * Represents a complete stroke drawn on the canvas.
 * Stores a list of segments, each with its own width/color.
 */
data class Stroke(
    val segments: MutableList<StrokeSegment> = mutableListOf(),
    val isEraser: Boolean = false
) {
    companion object {
        /**
         * Creates a Paint configured for drawing a segment.
         */
        fun createPaintForSegment(segment: StrokeSegment, isEraser: Boolean): Paint {
            return Paint().apply {
                isAntiAlias = true
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                color = segment.color
                strokeWidth = segment.strokeWidth
                alpha = segment.alpha
                
                if (isEraser) {
                    xfermode = android.graphics.PorterDuffXfermode(
                        android.graphics.PorterDuff.Mode.CLEAR
                    )
                }
            }
        }
    }
}
