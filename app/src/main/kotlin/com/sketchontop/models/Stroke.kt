package com.sketchontop.models

import android.graphics.Paint
import android.graphics.Path

/**
 * Represents a single stroke drawn on the canvas.
 * Stores both the path and a snapshot of the paint settings at the time of drawing.
 */
data class Stroke(
    val path: Path,
    val paint: Paint
) {
    companion object {
        /**
         * Creates a copy of the paint to preserve its state at the time of stroke creation.
         * This is important because the same Paint object may be reused with different settings.
         */
        fun createPaintCopy(original: Paint): Paint {
            return Paint(original).apply {
                // Copy all essential properties
                color = original.color
                strokeWidth = original.strokeWidth
                alpha = original.alpha
                style = original.style
                strokeCap = original.strokeCap
                strokeJoin = original.strokeJoin
                isAntiAlias = original.isAntiAlias
                xfermode = original.xfermode
            }
        }
    }
}
