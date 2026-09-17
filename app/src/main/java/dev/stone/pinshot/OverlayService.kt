package dev.stone.pinshot

import android.app.AlertDialog
import android.app.Service
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import java.io.File
import kotlin.math.max

class OverlayService : Service() {
    private lateinit var wm: WindowManager
    private lateinit var image: ImageView
    private lateinit var params: WindowManager.LayoutParams
    private var removed = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (::image.isInitialized) wm.removeView(image)
        val bitmap = BitmapFactory.decodeFile(File(cacheDir, "pinned.png").path) ?: return START_NOT_STICKY
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        image = ImageView(this).apply {
            setImageBitmap(bitmap); scaleType = ImageView.ScaleType.FIT_CENTER
            background = GradientDrawable().apply { setColor(Color.TRANSPARENT); setStroke(2, Color.argb(120, 255, 255, 255)); cornerRadius = 10f }
            elevation = 16f
        }
        val displayWidth = resources.displayMetrics.widthPixels
        val width = max(180, minOf((displayWidth * .65f).toInt(), bitmap.width))
        val height = max(120, (width.toFloat() * bitmap.height / bitmap.width).toInt())
        params = WindowManager.LayoutParams(width, height, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, android.graphics.PixelFormat.TRANSLUCENT).apply { x = 0; y = 0 }
        installGestures()
        wm.addView(image, params)
        return START_NOT_STICKY
    }

    private fun installGestures() {
        var startX = 0; var startY = 0; var originX = 0; var originY = 0
        val scale = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val newWidth = (params.width * detector.scaleFactor).toInt().coerceIn(120, resources.displayMetrics.widthPixels)
                val ratio = params.height.toFloat() / params.width
                params.width = newWidth; params.height = (newWidth * ratio).toInt()
                wm.updateViewLayout(image, params)
                return true
            }
        })
        val taps = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean { confirmClose(); return true }
        })
        image.setOnTouchListener { _: View, event: MotionEvent ->
            scale.onTouchEvent(event); taps.onTouchEvent(event)
            if (!scale.isInProgress) when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { startX = event.rawX.toInt(); startY = event.rawY.toInt(); originX = params.x; originY = params.y }
                MotionEvent.ACTION_MOVE -> { params.x = originX + (event.rawX.toInt() - startX); params.y = originY + (event.rawY.toInt() - startY); wm.updateViewLayout(image, params) }
            }
            true
        }
    }

    private fun confirmClose() {
        val dialog = AlertDialog.Builder(this).setTitle("关闭钉图？").setMessage("这张悬浮图片将被关闭。").setNegativeButton("取消", null).setPositiveButton("关闭") { _, _ -> stopSelf() }.create()
        dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        dialog.show()
    }
    override fun onDestroy() { if (::image.isInitialized && !removed) { try { wm.removeView(image) } catch (_: Exception) {}; removed = true }; super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null
}
