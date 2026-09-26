package dev.stone.pinshot

import android.app.AlertDialog
import android.app.Service
import android.content.ContentValues
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.IBinder
import android.provider.MediaStore
import android.provider.Settings
import android.view.Choreographer
import android.view.GestureDetector
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

class OverlayService : Service() {
    companion object { const val IMAGE_PATH = "image_path" }

    private lateinit var windowManager: WindowManager
    private val pins = mutableListOf<Pin>()
    private var actionDialog: AlertDialog? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        pins.forEach { it.ensureReachable() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val file = intent?.getStringExtra(IMAGE_PATH)?.let(::File)
        if (!Settings.canDrawOverlays(this)) {
            file?.delete()
            Toast.makeText(this, "请先允许显示在其他应用上层", Toast.LENGTH_LONG).show()
            if (pins.isEmpty()) stopSelfResult(startId)
            return START_NOT_STICKY
        }

        val bitmap = file?.let { BitmapFactory.decodeFile(it.path) }
        file?.delete()
        if (bitmap == null) {
            Toast.makeText(this, "图片无法显示，请重新分享", Toast.LENGTH_SHORT).show()
            if (pins.isEmpty()) stopSelfResult(startId)
            return START_NOT_STICKY
        }

        val pin = Pin(bitmap, pins.size)
        try {
            windowManager.addView(pin.window, pin.params)
            pins.add(pin)
        } catch (_: RuntimeException) {
            bitmap.recycle()
            Toast.makeText(this, "悬浮窗无法显示，请检查悬浮窗权限", Toast.LENGTH_LONG).show()
            if (pins.isEmpty()) stopSelfResult(startId)
        }
        return START_NOT_STICKY
    }

    private inner class Pin(var bitmap: Bitmap, stackIndex: Int) {
        val image = ImageView(this@OverlayService).apply {
            setImageBitmap(bitmap)
            scaleType = ImageView.ScaleType.FIT_CENTER
            background = GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                setStroke(2, Color.argb(120, 255, 255, 255))
                cornerRadius = 10f
            }
        }
        val window = FrameLayout(this@OverlayService).apply {
            addView(image, FrameLayout.LayoutParams(-1, -1))
        }

        private val aspectRatio get() = bitmap.width.toFloat() / bitmap.height
        private val density = resources.displayMetrics.density
        private val minSide = 48f * density
        private val minWidth get() = minSide * max(1f, aspectRatio)
        private val maxWidth get() = minOf(
            resources.displayMetrics.widthPixels * 4f,
            resources.displayMetrics.heightPixels * 4f * aspectRatio
        ).coerceAtLeast(minWidth)
        private val choreographer = Choreographer.getInstance()
        private var frameScheduled = false
        private var removed = false
        private var targetWidth: Float

        val params: WindowManager.LayoutParams

        init {
            val factor = minOf(
                1f,
                resources.displayMetrics.widthPixels * .65f / bitmap.width,
                resources.displayMetrics.heightPixels * .65f / bitmap.height
            )
            val width = max(1, (bitmap.width * factor).roundToInt())
            val height = max(1, (bitmap.height * factor).roundToInt())
            targetWidth = width.toFloat()
            val offset = stackIndex.coerceAtMost(8)
            params = WindowManager.LayoutParams(
                width, height, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.LEFT
                // Use display coordinates; keepReachable applies system-bar insets itself.
                setFitInsetsTypes(0)
                x = ((resources.displayMetrics.widthPixels - width) / 2f + offset * 20 * density).roundToInt()
                y = ((48 + offset * 28) * density).roundToInt()
            }
            keepReachable()
            installGestures()
            installResizeHandle()
        }

        private val frameCallback = Choreographer.FrameCallback {
            frameScheduled = false
            if (removed) return@FrameCallback
            val width = targetWidth.roundToInt()
            val height = (targetWidth / aspectRatio).roundToInt().coerceAtLeast(1)
            if (width != params.width || height != params.height) {
                // The resize handle changes size while keeping the top-left corner fixed.
                params.width = width
                params.height = height
                updateLayout()
            }
        }

        private fun scheduleScaleLayout() {
            if (!frameScheduled && !removed) {
                frameScheduled = true
                choreographer.postFrameCallback(frameCallback)
            }
        }

        private fun keepReachable() {
            val metrics = windowManager.currentWindowMetrics
            val bars = metrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars())
            val margin = (8 * density).roundToInt()
            val left = metrics.bounds.left + bars.left + margin
            val top = metrics.bounds.top + bars.top + margin
            val right = metrics.bounds.right - bars.right - margin
            val bottom = metrics.bounds.bottom - bars.bottom - margin
            // Symmetric limits: leave one quarter of each dimension reachable on any edge.
            // Very large images cannot expose more than the available display area.
            val visibleWidth = ((params.width + 3) / 4).coerceAtMost((right - left).coerceAtLeast(1))
            val visibleHeight = ((params.height + 3) / 4).coerceAtMost((bottom - top).coerceAtLeast(1))
            params.x = params.x.coerceIn(left - params.width + visibleWidth, right - visibleWidth)
            params.y = params.y.coerceIn(top - params.height + visibleHeight, bottom - visibleHeight)
        }

        private fun updateLayout() {
            keepReachable()
            if (!removed && window.isAttachedToWindow) {
                windowManager.updateViewLayout(window, params)
            }
        }

        fun ensureReachable() = updateLayout()

        private fun installGestures() {
            var startX = 0
            var startY = 0
            var originX = 0
            var originY = 0
            var dragging = false

            val taps = GestureDetector(this@OverlayService,
                object : GestureDetector.SimpleOnGestureListener() {
                    override fun onDoubleTap(e: MotionEvent): Boolean {
                        showActions(this@Pin)
                        return true
                    }
                })

            image.setOnTouchListener { _: View, event: MotionEvent ->
                taps.onTouchEvent(event)
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = event.rawX.roundToInt()
                        startY = event.rawY.roundToInt()
                        originX = params.x
                        originY = params.y
                        dragging = true
                    }
                    MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_POINTER_UP -> dragging = false
                    MotionEvent.ACTION_MOVE -> if (dragging && event.pointerCount == 1) {
                        val currentX = event.rawX.roundToInt()
                        val currentY = event.rawY.roundToInt()
                        val proposedX = originX + currentX - startX
                        val proposedY = originY + currentY - startY
                        params.x = proposedX
                        params.y = proposedY
                        updateLayout()
                        if (params.x != proposedX) {
                            originX = params.x
                            startX = currentX
                        }
                        if (params.y != proposedY) {
                            originY = params.y
                            startY = currentY
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> dragging = false
                }
                true
            }
        }

        private fun installResizeHandle() {
            val size = (48 * density).roundToInt()
            val handle = ResizeHandleView(this@OverlayService)
            window.addView(handle, FrameLayout.LayoutParams(size, size, Gravity.END or Gravity.BOTTOM))

            var lastX = 0f
            var lastY = 0f
            handle.setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        lastX = event.rawX
                        lastY = event.rawY
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - lastX
                        val dy = event.rawY - lastY
                        lastX = event.rawX
                        lastY = event.rawY
                        val heightPerWidth = 1f / aspectRatio
                        val widthChange = (dx + dy * heightPerWidth) /
                            (1f + heightPerWidth * heightPerWidth)
                        targetWidth = (targetWidth + widthChange).coerceIn(minWidth, maxWidth)
                        scheduleScaleLayout()
                    }
                }
                true
            }
        }

        fun rotateCounterclockwise(): Boolean {
            if (removed) return false
            val rotated = try {
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height,
                    Matrix().apply { postRotate(-90f) }, false)
            } catch (_: OutOfMemoryError) {
                Toast.makeText(this@OverlayService, "内存不足，无法旋转图片", Toast.LENGTH_SHORT).show()
                return false
            }
            if (frameScheduled) {
                choreographer.removeFrameCallback(frameCallback)
                frameScheduled = false
            }
            val centerX = params.x + params.width / 2f
            val centerY = params.y + params.height / 2f
            val previousWidth = params.width
            params.width = params.height
            params.height = previousWidth
            targetWidth = params.width.toFloat()
            params.x = (centerX - params.width / 2f).roundToInt()
            params.y = (centerY - params.height / 2f).roundToInt()
            // Let the renderer release the previous bitmap after its last frame.
            bitmap = rotated
            image.setImageBitmap(rotated)
            updateLayout()
            return true
        }

        fun saveToGallery() {
            if (removed) return
            // Own a snapshot so closing or rotating this pin cannot invalidate the export.
            val snapshot = try {
                bitmap.copy(Bitmap.Config.ARGB_8888, false)
            } catch (_: OutOfMemoryError) {
                null
            }
            if (snapshot == null) {
                Toast.makeText(this@OverlayService, "内存不足，无法保存图片", Toast.LENGTH_SHORT).show()
                return
            }
            val context = applicationContext
            Thread({
                val resolver = context.contentResolver
                var uri: android.net.Uri? = null
                val message = try {
                    val values = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, "PinShot_${UUID.randomUUID()}.png")
                        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                        put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/PinShot")
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }
                    val inserted = checkNotNull(resolver.insert(
                        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values))
                    uri = inserted
                    checkNotNull(resolver.openOutputStream(inserted)).use { output ->
                        check(snapshot.compress(Bitmap.CompressFormat.PNG, 100, output))
                    }
                    val published = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
                    check(resolver.update(inserted, published, null, null) == 1)
                    "已保存到相册（Pictures/PinShot）"
                } catch (_: Exception) {
                    uri?.let { runCatching { resolver.delete(it, null, null) } }
                    "保存失败，请检查可用存储空间后重试"
                } finally {
                    snapshot.recycle()
                }
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                }
            }, "PinShot-gallery-save").start()
        }

        fun share() {
            if (removed) return
            // Keep a private copy while the file is written; this pin may be rotated or closed.
            val snapshot = try {
                bitmap.copy(Bitmap.Config.ARGB_8888, false)
            } catch (_: OutOfMemoryError) {
                null
            }
            if (snapshot == null) {
                Toast.makeText(this@OverlayService, "内存不足，无法分享图片", Toast.LENGTH_SHORT).show()
                return
            }
            val context = applicationContext
            Thread({
                var file: File? = null
                try {
                    val directory = File(context.cacheDir, "shared-images").apply { mkdirs() }
                    // Give receiving apps time to read earlier shares.
                    directory.listFiles()?.filter {
                        it.isFile && System.currentTimeMillis() - it.lastModified() > 24 * 60 * 60 * 1000L
                    }?.forEach { it.delete() }
                    file = File.createTempFile("pin-", ".png", directory)
                    file.outputStream().use { output ->
                        check(snapshot.compress(Bitmap.CompressFormat.PNG, 100, output))
                    }
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                    val chooser = Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                        type = "image/png"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        clipData = ClipData.newRawUri("钉图", uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }, "分享钉图")
                    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    Handler(Looper.getMainLooper()).post {
                        try {
                            context.startActivity(chooser)
                        } catch (_: Exception) {
                            Toast.makeText(context, "无法打开分享面板", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (_: Exception) {
                    file?.delete()
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(context, "分享图片准备失败", Toast.LENGTH_SHORT).show()
                    }
                } finally {
                    snapshot.recycle()
                }
            }, "PinShot-share").start()
        }

        fun remove() {
            if (removed) return
            removed = true
            if (frameScheduled) choreographer.removeFrameCallback(frameCallback)
            if (window.isAttachedToWindow) windowManager.removeView(window)
            image.setImageDrawable(null)
            bitmap.recycle()
        }
    }

    private class ResizeHandleView(context: Context) : View(context) {
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(235, 255, 255, 255)
            style = Paint.Style.FILL
        }
        private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(25, 25, 25)
            style = Paint.Style.STROKE
            strokeWidth = 1.2f * resources.displayMetrics.density
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        init {
            // The image fills the window. Keep the handle above it in draw and touch order.
            elevation = 8f * resources.displayMetrics.density
            contentDescription = "拖动缩放钉图"
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val density = resources.displayMetrics.density
            val size = 12f * density
            val right = width - 2f * density
            val bottom = height - 2f * density
            val left = right - size
            val top = bottom - size
            val centerX = (left + right) / 2f
            val centerY = (top + bottom) / 2f
            canvas.drawCircle(centerX, centerY, size / 2f, fill)
            canvas.drawCircle(centerX, centerY, size / 2f, stroke)
            canvas.drawLine(left + size * .30f, top + size * .30f,
                left + size * .70f, top + size * .70f, stroke)
            canvas.drawLine(left + size * .46f, top + size * .70f,
                left + size * .70f, top + size * .70f, stroke)
            canvas.drawLine(left + size * .70f, top + size * .70f,
                left + size * .70f, top + size * .46f, stroke)
        }
    }

    private fun showActions(pin: Pin) {
        if (actionDialog?.isShowing == true) return
        val dialog = AlertDialog.Builder(this)
            .setTitle("钉图操作")
            .setItems(arrayOf("关闭", "逆时针旋转", "保存", "分享"), null)
            .setNegativeButton("取消", null)
            .create()
        actionDialog = dialog
        dialog.setOnDismissListener { if (actionDialog === dialog) actionDialog = null }
        dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        dialog.show()
        var rotationCount = 0
        // Replace AlertDialog's default item listener, which dismisses after every tap.
        dialog.listView.setOnItemClickListener { _, view, which, _ ->
            when (which) {
                0 -> {
                    dialog.dismiss()
                    pin.remove()
                    pins.remove(pin)
                    if (pins.isEmpty()) stopSelf()
                }
                1 -> if (pin.rotateCounterclockwise()) {
                    rotationCount++
                    dialog.setTitle("钉图操作 · 已逆时针旋转 ${rotationCount} 次")
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                }
                2 -> {
                    dialog.dismiss()
                    pin.saveToGallery()
                }
                3 -> {
                    dialog.dismiss()
                    pin.share()
                }
            }
        }
    }

    override fun onDestroy() {
        actionDialog?.dismiss()
        actionDialog = null
        pins.forEach { it.remove() }
        pins.clear()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
