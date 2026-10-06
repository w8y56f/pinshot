package dev.stone.pinshot

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.io.File
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/** A crop is stored in normalized image coordinates, independent of screen size. */
class CropActivity : AppCompatActivity() {
    private lateinit var crop: CropView
    private lateinit var confirm: Button
    private lateinit var title: TextView
    private var saving = false
    private val processingDialog = ProcessingDialog(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
        }
        val toolbar = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.rgb(48, 53, 60))
        }
        fun button(label: String, description: String) = Button(this).apply {
            text = label
            textSize = 26f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.TRANSPARENT)
            contentDescription = description
        }
        val size = (64 * resources.displayMetrics.density).roundToInt()
        toolbar.addView(button("×", "取消裁剪").apply { setOnClickListener { if (!saving) finish() } }, LinearLayout.LayoutParams(size, size))
        title = TextView(this).apply {
            text = "正在读取图片…"
            textSize = 20f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
        }
        toolbar.addView(title, LinearLayout.LayoutParams(0, size, 1f))
        confirm = button("✓", "确认裁剪并钉图").apply {
            isEnabled = false
            alpha = .4f
            setOnClickListener { saveCrop() }
        }
        toolbar.addView(confirm, LinearLayout.LayoutParams(size, size))
        root.addView(toolbar)
        crop = CropView(this)
        savedInstanceState?.getFloatArray("selection")?.takeIf { it.size == 4 }?.let {
            crop.selection.set(it[0], it[1], it[2], it[3])
        }
        root.addView(crop, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(TextView(this).apply {
            text = "拖动四角或边缘裁剪 · 拖动框内移动选区"
            gravity = Gravity.CENTER
            setTextColor(Color.LTGRAY)
            setPadding(8, 16, 8, 20)
        })
        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        val uri = intent.data ?: run { finish(); return }
        processingDialog.show()
        thread(name = "LoadCropImage") {
            try {
                val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, uri)) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    val longest = max(info.size.width, info.size.height)
                    if (longest > 4096) decoder.setTargetSize(
                        max(1, (info.size.width * 4096L / longest).toInt()),
                        max(1, (info.size.height * 4096L / longest).toInt()))
                }
                runOnUiThread {
                    if (isDestroyed || isFinishing) bitmap.recycle() else {
                        processingDialog.dismiss()
                        crop.bitmap = bitmap
                        crop.invalidate()
                        title.text = "裁剪"
                        confirm.isEnabled = true
                        confirm.alpha = 1f
                    }
                }
            } catch (_: Exception) {
                runOnUiThread {
                    if (!isDestroyed && !isFinishing) {
                        processingDialog.dismiss()
                        Toast.makeText(this, "图片读取失败，请重新选择", Toast.LENGTH_LONG).show()
                        finish()
                    }
                }
            }
        }
    }

    private fun saveCrop() {
        val source = crop.bitmap ?: return
        if (saving) return
        val bounds = RectF(crop.selection)
        saving = true
        crop.isEnabled = false
        confirm.isEnabled = false
        title.text = "正在生成钉图…"
        processingDialog.show()
        thread(name = "SaveCropImage") {
            var file: File? = null
            try {
                val left = (bounds.left * source.width).roundToInt().coerceIn(0, source.width - 1)
                val top = (bounds.top * source.height).roundToInt().coerceIn(0, source.height - 1)
                val right = (bounds.right * source.width).roundToInt().coerceIn(left + 1, source.width)
                val bottom = (bounds.bottom * source.height).roundToInt().coerceIn(top + 1, source.height)
                val result = Bitmap.createBitmap(source, left, top, right - left, bottom - top)
                try {
                    file = File.createTempFile("cropped-pin-", ".png", cacheDir)
                    file.outputStream().use { check(result.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                } finally {
                    if (result !== source) result.recycle()
                }
                val output = file
                runOnUiThread {
                    if (isDestroyed || isFinishing) output?.delete() else {
                        processingDialog.dismiss()
                        setResult(RESULT_OK, Intent().putExtra(OverlayService.IMAGE_PATH, output?.absolutePath))
                        finish()
                    }
                }
            } catch (_: Exception) {
                file?.delete()
                runOnUiThread {
                    if (!isDestroyed && !isFinishing) {
                        processingDialog.dismiss()
                        saving = false
                        crop.isEnabled = true
                        confirm.isEnabled = true
                        title.text = "裁剪"
                        Toast.makeText(this, "裁剪保存失败，请重试", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        processingDialog.dismiss()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        val r = crop.selection
        outState.putFloatArray("selection", floatArrayOf(r.left, r.top, r.right, r.bottom))
        super.onSaveInstanceState(outState)
    }

    private class CropView(context: Context) : View(context) {
        var bitmap: Bitmap? = null
        val selection = RectF(0f, 0f, 1f, 1f)
        private val imageBounds = RectF()
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val density = resources.displayMetrics.density
        private var edges = 0
        private var lastX = 0f
        private var lastY = 0f

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val image = bitmap ?: return
            val margin = 16 * density
            val scale = minOf((width - margin * 2).coerceAtLeast(1f) / image.width,
                (height - margin * 2).coerceAtLeast(1f) / image.height)
            val w = image.width * scale
            val h = image.height * scale
            imageBounds.set((width - w) / 2, (height - h) / 2, (width + w) / 2, (height + h) / 2)
            paint.color = Color.WHITE
            paint.style = Paint.Style.FILL
            canvas.drawBitmap(image, null, imageBounds, paint)
            val r = screenSelection()
            paint.color = 0x99000000.toInt()
            canvas.drawRect(imageBounds.left, imageBounds.top, imageBounds.right, r.top, paint)
            canvas.drawRect(imageBounds.left, r.bottom, imageBounds.right, imageBounds.bottom, paint)
            canvas.drawRect(imageBounds.left, r.top, r.left, r.bottom, paint)
            canvas.drawRect(r.right, r.top, imageBounds.right, r.bottom, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = density
            paint.color = 0x88FFFFFF.toInt()
            for (i in 1..2) {
                val x = r.left + r.width() * i / 3
                val y = r.top + r.height() * i / 3
                canvas.drawLine(x, r.top, x, r.bottom, paint)
                canvas.drawLine(r.left, y, r.right, y, paint)
            }
            paint.color = Color.WHITE
            canvas.drawRect(r, paint)
            paint.strokeWidth = 4 * density
            val length = minOf(18 * density, r.width() / 3, r.height() / 3)
            for (x in floatArrayOf(r.left, r.right)) for (y in floatArrayOf(r.top, r.bottom)) {
                canvas.drawLine(x, y, x + if (x == r.left) length else -length, y, paint)
                canvas.drawLine(x, y, x, y + if (y == r.top) length else -length, paint)
            }
            paint.style = Paint.Style.FILL
        }

        private fun screenSelection() = RectF(
            imageBounds.left + selection.left * imageBounds.width(),
            imageBounds.top + selection.top * imageBounds.height(),
            imageBounds.left + selection.right * imageBounds.width(),
            imageBounds.top + selection.bottom * imageBounds.height())

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (!isEnabled || bitmap == null || imageBounds.isEmpty) return false
            val x = event.x
            val y = event.y
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    val r = screenSelection()
                    val hit = 28 * density
                    if (x < r.left - hit || x > r.right + hit || y < r.top - hit || y > r.bottom + hit) return false
                    edges = 0
                    if (abs(x - r.left) < hit && abs(x - r.left) <= abs(x - r.right)) edges = edges or 1
                    else if (abs(x - r.right) < hit) edges = edges or 2
                    if (abs(y - r.top) < hit && abs(y - r.top) <= abs(y - r.bottom)) edges = edges or 4
                    else if (abs(y - r.bottom) < hit) edges = edges or 8
                    lastX = x
                    lastY = y
                    parent.requestDisallowInterceptTouchEvent(true)
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (x - lastX) / imageBounds.width()
                    val dy = (y - lastY) / imageBounds.height()
                    val minW = minOf(selection.width(), 48 * density / imageBounds.width())
                    val minH = minOf(selection.height(), 48 * density / imageBounds.height())
                    if (edges == 0) {
                        selection.offset(dx.coerceIn(-selection.left, 1 - selection.right),
                            dy.coerceIn(-selection.top, 1 - selection.bottom))
                    } else {
                        if (edges and 1 != 0) selection.left = (selection.left + dx).coerceIn(0f, selection.right - minW)
                        if (edges and 2 != 0) selection.right = (selection.right + dx).coerceIn(selection.left + minW, 1f)
                        if (edges and 4 != 0) selection.top = (selection.top + dy).coerceIn(0f, selection.bottom - minH)
                        if (edges and 8 != 0) selection.bottom = (selection.bottom + dy).coerceIn(selection.top + minH, 1f)
                    }
                    lastX = x
                    lastY = y
                    invalidate()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> parent.requestDisallowInterceptTouchEvent(false)
            }
            return true
        }
    }
}
