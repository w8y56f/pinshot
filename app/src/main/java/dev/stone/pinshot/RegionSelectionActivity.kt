package dev.stone.pinshot

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.Button
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class RegionSelectionActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.windowInsetsController?.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
        val bitmap = BitmapFactory.decodeFile(File(cacheDir, "capture.png").path)
        val root = FrameLayout(this)
        val crop = CropView(this, bitmap)
        root.addView(crop, FrameLayout.LayoutParams(-1, -1))
        val actions = FrameLayout(this)
        val cancel = Button(this).apply { text = "取消"; setOnClickListener { finish() } }
        val pin = Button(this).apply { text = "钉图"; setOnClickListener {
            crop.saveCrop(File(cacheDir, "pinned.png"))
            startService(Intent(this@RegionSelectionActivity, OverlayService::class.java))
            finish()
        } }
        actions.addView(cancel, FrameLayout.LayoutParams(-2, -2, Gravity.START or Gravity.BOTTOM).apply { setMargins(24, 0, 0, 32) })
        actions.addView(pin, FrameLayout.LayoutParams(-2, -2, Gravity.END or Gravity.BOTTOM).apply { setMargins(0, 0, 24, 32) })
        root.addView(actions, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
    }
}

private class CropView(context: android.content.Context, private val bitmap: Bitmap) : View(context) {
    private val image = RectF(); private val crop = RectF(); private var downX = 0f; private var downY = 0f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val ratio = max(w.toFloat() / bitmap.width, h.toFloat() / bitmap.height)
        val bw = bitmap.width * ratio; val bh = bitmap.height * ratio
        image.set((w - bw) / 2, (h - bh) / 2, (w + bw) / 2, (h + bh) / 2)
        crop.set(w * .15f, h * .22f, w * .85f, h * .65f)
    }
    override fun onDraw(canvas: Canvas) {
        canvas.drawBitmap(bitmap, null, image, paint)
        paint.color = 0x99000000.toInt()
        canvas.drawRect(0f, 0f, width.toFloat(), crop.top, paint); canvas.drawRect(0f, crop.bottom, width.toFloat(), height.toFloat(), paint)
        canvas.drawRect(0f, crop.top, crop.left, crop.bottom, paint); canvas.drawRect(crop.right, crop.top, width.toFloat(), crop.bottom, paint)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 4f; paint.color = Color.WHITE; canvas.drawRect(crop, paint); paint.style = Paint.Style.FILL
    }
    override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
        when (event.action) {
            android.view.MotionEvent.ACTION_DOWN -> { downX = event.x; downY = event.y; crop.set(event.x, event.y, event.x + 1, event.y + 1) }
            android.view.MotionEvent.ACTION_MOVE -> { crop.set(min(downX,event.x), min(downY,event.y), max(downX,event.x), max(downY,event.y)); crop.intersect(image); invalidate() }
            android.view.MotionEvent.ACTION_UP -> if (abs(crop.width()) < 40 || abs(crop.height()) < 40) { crop.set(width*.15f,height*.22f,width*.85f,height*.65f); invalidate() }
        }
        return true
    }
    fun saveCrop(file: File) {
        val sx = bitmap.width / image.width(); val sy = bitmap.height / image.height()
        val left = ((crop.left - image.left) * sx).toInt().coerceIn(0, bitmap.width - 1)
        val top = ((crop.top - image.top) * sy).toInt().coerceIn(0, bitmap.height - 1)
        val right = ((crop.right - image.left) * sx).toInt().coerceIn(left + 1, bitmap.width)
        val bottom = ((crop.bottom - image.top) * sy).toInt().coerceIn(top + 1, bitmap.height)
        FileOutputStream(file).use { Bitmap.createBitmap(bitmap, left, top, right-left, bottom-top).compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
