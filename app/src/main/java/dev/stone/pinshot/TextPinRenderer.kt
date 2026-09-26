package dev.stone.pinshot

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.TypedValue
import kotlin.math.roundToInt

/** Draws text into a bitmap so it can use the existing movable, resizable pin window. */
internal object TextPinRenderer {
    fun render(context: Context, value: String): Bitmap {
        require(value.isNotBlank()) { "请先输入文字" }
        require(value.length <= 15_000) { "文字过长，请缩短后再钉到屏幕" }

        val metrics = context.resources.displayMetrics
        val density = metrics.density
        val padding = (18 * density).roundToInt()
        val minWidth = (160 * density).roundToInt()
        val maxWidth = minOf((360 * density).roundToInt(), metrics.widthPixels - (32 * density).roundToInt())
            .coerceAtLeast(minWidth)
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(32, 42, 55)
            textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 17f, metrics)
        }
        val widestLine = value.split('\n').maxOf { textPaint.measureText(it) }
        val width = (widestLine + padding * 2).roundToInt().coerceIn(minWidth, maxWidth)
        val layout = StaticLayout.Builder.obtain(value, 0, value.length, textPaint, width - padding * 2)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY)
            .build()
        val height = layout.height + padding * 2
        require(height <= 4096) { "文字内容太长，请缩短后再钉到屏幕" }
        val bitmap = try {
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        } catch (_: OutOfMemoryError) {
            throw IllegalArgumentException("内存不足，无法生成文字钉图")
        }
        val canvas = Canvas(bitmap)
        val background = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(208, 219, 225)
            style = Paint.Style.STROKE
            strokeWidth = density
        }
        val bounds = RectF(density / 2, density / 2, width - density / 2, height - density / 2)
        canvas.drawRoundRect(bounds, 12 * density, 12 * density, background)
        canvas.drawRoundRect(bounds, 12 * density, 12 * density, border)
        canvas.save()
        canvas.translate(padding.toFloat(), padding.toFloat())
        layout.draw(canvas)
        canvas.restore()
        return bitmap
    }
}
