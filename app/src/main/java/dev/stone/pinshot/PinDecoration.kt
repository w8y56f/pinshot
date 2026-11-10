package dev.stone.pinshot

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.ceil
import kotlin.math.roundToInt

/** Shared screen-pixel decoration for the floating preview and full-resolution export. */
internal class PinDecoration(density: Float) {
    private val shadowSize = 4f * density
    val padding = ceil(shadowSize).toInt() + 1
    private val shadowPaints = List(ceil(shadowSize).toInt().coerceAtLeast(1)) { index ->
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb((56f / ceil(shadowSize)).roundToInt().coerceAtLeast(1), 0, 0, 0)
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            strokeWidth = 2f * (shadowSize - index)
        }
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(120, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val borderBounds = RectF()

    fun drawShadow(canvas: Canvas, bounds: RectF) {
        val save = canvas.save()
        // Preserve transparent image interiors; only the outside receives a shadow.
        canvas.clipOutRect(bounds)
        shadowPaints.forEach { canvas.drawRect(bounds, it) }
        canvas.restoreToCount(save)
    }

    fun drawBorder(canvas: Canvas, bounds: RectF) {
        borderBounds.set(bounds.left + 1f, bounds.top + 1f, bounds.right - 1f, bounds.bottom - 1f)
        canvas.drawRoundRect(borderBounds, 10f, 10f, borderPaint)
    }
}
