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
import android.graphics.RectF
import android.os.Build
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
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID
import kotlin.math.max
import kotlin.math.ceil
import kotlin.math.floor
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
        private val density = resources.displayMetrics.density
        private val minSide = 48f * density
        private val minimumVisibleSize = 30f * density
        private val aspectRatio get() = bitmap.width.toFloat() / bitmap.height
        private val minWidth get() = minSide * max(1f, aspectRatio)
        private val maxWidth get() = minOf(
            resources.displayMetrics.widthPixels * 4f,
            resources.displayMetrics.heightPixels * 4f * aspectRatio
        ).coerceAtLeast(minWidth)
        private val geometry: PinGeometry
        private val pinch = PinchTransform(8f * density)
        private val choreographer = Choreographer.getInstance()
        private var frameScheduled = false
        private var removed = false
        private var expanded = false
        private var gestureActive = false
        private var multiTouchGesture = false
        private var handleGesture = false
        private var lastTouch: PinTouch? = null
        private val screenLocation = IntArray(2)
        private val bitmapMatrix = Matrix()
        private val imageBounds = RectF()
        private val reachableBounds = RectF()
        private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(120, 255, 255, 255)
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        private val handleSize = (24 * density).roundToInt()
        private val handle = ResizeHandleView(this@OverlayService)
        private val image = object : View(this@OverlayService) {
            private var drawnOriginX = Int.MIN_VALUE
            private var drawnOriginY = Int.MIN_VALUE

            fun invalidateIfOriginChanged() {
                getLocationOnScreen(screenLocation)
                if (drawnOriginX != screenLocation[0] || drawnOriginY != screenLocation[1]) {
                    invalidate()
                }
            }

            override fun onDraw(canvas: Canvas) {
                if (removed) return
                // Read the actual origin: a WindowManager relayout may still be pending.
                getLocationOnScreen(screenLocation)
                drawnOriginX = screenLocation[0]
                drawnOriginY = screenLocation[1]
                val x = geometry.x - screenLocation[0]
                val y = geometry.y - screenLocation[1]
                bitmapMatrix.setScale(geometry.width / bitmap.width, geometry.height / bitmap.height)
                bitmapMatrix.postTranslate(x, y)
                canvas.drawBitmap(bitmap, bitmapMatrix, bitmapPaint)
                imageBounds.set(x + 1f, y + 1f, x + geometry.width - 1f, y + geometry.height - 1f)
                canvas.drawRoundRect(imageBounds, 10f, 10f, borderPaint)
            }
        }
        val window = object : FrameLayout(this@OverlayService) {
            // One event stream, including fingers initially landing on the handle.
            override fun onInterceptTouchEvent(event: MotionEvent) = true
        }.apply {
            isMotionEventSplittingEnabled = false
            addView(image, FrameLayout.LayoutParams(-1, -1))
            addView(handle, FrameLayout.LayoutParams(handleSize, handleSize, Gravity.TOP or Gravity.LEFT))
        }
        val params: WindowManager.LayoutParams

        init {
            val factor = minOf(1f, resources.displayMetrics.widthPixels * .65f / bitmap.width,
                resources.displayMetrics.heightPixels * .65f / bitmap.height)
            val width = max(1f, bitmap.width * factor)
            val offset = stackIndex.coerceAtMost(8)
            geometry = PinGeometry(
                (resources.displayMetrics.widthPixels - width) / 2f + offset * 20 * density,
                (48 + offset * 28) * density, width, aspectRatio)
            params = WindowManager.LayoutParams(
                1, 1, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.LEFT
                // Geometry and raw touches use display coordinates. Disabling inset fitting
                // alone does not remove the cutout-safe parent origin (e.g. +128px at top),
                // which otherwise shifts the tight window and clips the bitmap inside it.
                setFitInsetsTypes(0)
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                // Expanding/collapsing the surface must not animate its screen origin:
                // the bitmap already compensates for that origin in the same frame.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    setCanPlayMoveAnimation(false)
                }
            }
            refreshReachableBounds()
            constrainGeometry()
            setWindowBounds()
            // Surface changes and model changes use the same screen-space geometry.
            window.viewTreeObserver.addOnPreDrawListener {
                // Relayout can change the origin after renderGeometry() invalidated the view.
                // Do not reuse a display list recorded relative to the old window position.
                image.invalidateIfOriginChanged()
                positionHandle()
                true
            }
            installGestures()
        }

        private val frameCallback = Choreographer.FrameCallback {
            frameScheduled = false
            if (!removed) renderGeometry()
        }

        private fun scheduleFrame() {
            if (!frameScheduled && !removed) {
                frameScheduled = true
                choreographer.postFrameCallback(frameCallback)
            }
        }

        private fun refreshReachableBounds() {
            val metrics = windowManager.currentWindowMetrics
            val bars = metrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars())
            val margin = 8 * density
            reachableBounds.set(metrics.bounds.left + bars.left + margin,
                metrics.bounds.top + bars.top + margin,
                metrics.bounds.right - bars.right - margin,
                metrics.bounds.bottom - bars.bottom - margin)
        }

        private fun constrainGeometry() {
            geometry.constrain(reachableBounds.left, reachableBounds.top,
                reachableBounds.right, reachableBounds.bottom, minimumVisibleSize)
        }

        private fun setWindowBounds() {
            if (expanded) {
                val bounds = windowManager.currentWindowMetrics.bounds
                params.x = bounds.left
                params.y = bounds.top
                params.width = bounds.width()
                params.height = bounds.height()
            } else {
                params.x = floor(geometry.x).toInt()
                params.y = floor(geometry.y).toInt()
                params.width = (ceil(geometry.x + geometry.width).toInt() - params.x).coerceAtLeast(1)
                params.height = (ceil(geometry.y + geometry.height).toInt() - params.y).coerceAtLeast(1)
            }
        }

        private fun positionHandle() {
            window.getLocationOnScreen(screenLocation)
            handle.translationX = geometry.x + geometry.width - screenLocation[0] - handleSize
            handle.translationY = geometry.y + geometry.height - screenLocation[1] - handleSize
        }

        private fun renderGeometry() {
            // While expanded, only redraw the bitmap matrix. No per-frame window relayout.
            if (!expanded) updateWindowBounds()
            positionHandle()
            image.invalidate()
        }

        private fun updateWindowBounds() {
            val x = params.x
            val y = params.y
            val width = params.width
            val height = params.height
            setWindowBounds()
            if (window.isAttachedToWindow &&
                (x != params.x || y != params.y || width != params.width || height != params.height)) {
                windowManager.updateViewLayout(window, params)
            }
        }

        private fun beginPinch() {
            if (expanded) return
            // Older Android versions cannot disable window move animations through public API.
            // Keep the tight surface there, avoiding a large origin jump at both gesture edges.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
            expanded = true
            updateWindowBounds()
            image.invalidate()
        }

        private fun endGesture() {
            gestureActive = false
            lastTouch = null
            pinch.rebase(emptyList())
            if (frameScheduled) {
                choreographer.removeFrameCallback(frameCallback)
                frameScheduled = false
            }
            expanded = false
            constrainGeometry()
            renderGeometry()
        }

        fun ensureReachable() {
            // Display rotation invalidates touch coordinates. Ignore this stream until next DOWN.
            refreshReachableBounds()
            endGesture()
        }

        private fun installGestures() {
            val taps = GestureDetector(this@OverlayService,
                object : GestureDetector.SimpleOnGestureListener() {
                    override fun onDoubleTap(e: MotionEvent): Boolean {
                        showActions(this@Pin)
                        return true
                    }
                })
            window.setOnTouchListener { _, event ->
                if (removed) return@setOnTouchListener true
                val points = (0 until event.pointerCount).map {
                    PinTouch(event.getPointerId(it), event.getRawX(it), event.getRawY(it))
                }
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        refreshReachableBounds()
                        gestureActive = true
                        multiTouchGesture = false
                        val p = points.first()
                        lastTouch = p
                        handleGesture = geometry.isInResizeHandle(p.x, p.y, handleSize.toFloat()) &&
                            !geometry.shouldDragInsteadOfResize(reachableBounds.left, reachableBounds.top,
                                reachableBounds.right, reachableBounds.bottom, handleSize.toFloat())
                        if (!handleGesture) taps.onTouchEvent(event)
                    }
                    MotionEvent.ACTION_POINTER_DOWN -> if (gestureActive) {
                        if (!multiTouchGesture) {
                            val cancel = MotionEvent.obtain(event)
                            cancel.action = MotionEvent.ACTION_CANCEL
                            taps.onTouchEvent(cancel)
                            cancel.recycle()
                        }
                        multiTouchGesture = true
                        handleGesture = false
                        pinch.rebase(points)
                        beginPinch()
                    }
                    MotionEvent.ACTION_MOVE -> if (gestureActive) {
                        if (!multiTouchGesture && !handleGesture) taps.onTouchEvent(event)
                        if (points.size >= 2) {
                            pinch.move(points, geometry, minWidth, maxWidth)
                        } else {
                            val p = points.first()
                            lastTouch?.takeIf { it.id == p.id }?.let { previous ->
                                val dx = p.x - previous.x
                                val dy = p.y - previous.y
                                if (handleGesture) {
                                    val heightPerWidth = 1f / aspectRatio
                                    geometry.width = (geometry.width + (dx + dy * heightPerWidth) /
                                        (1f + heightPerWidth * heightPerWidth)).coerceIn(minWidth, maxWidth)
                                } else {
                                    geometry.x += dx
                                    geometry.y += dy
                                }
                            }
                            lastTouch = p
                        }
                        constrainGeometry()
                        scheduleFrame()
                    }
                    MotionEvent.ACTION_POINTER_UP -> if (gestureActive) {
                        val remaining = points.filterIndexed { index, _ -> index != event.actionIndex }
                        pinch.rebase(remaining)
                        lastTouch = remaining.singleOrNull()
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        if (gestureActive && !multiTouchGesture && !handleGesture) taps.onTouchEvent(event)
                        endGesture()
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
            endGesture()
            val centerX = geometry.x + geometry.width / 2f
            val centerY = geometry.y + geometry.height / 2f
            val previousHeight = geometry.height
            bitmap = rotated
            geometry.aspectRatio = aspectRatio
            geometry.width = previousHeight
            geometry.x = centerX - geometry.width / 2f
            geometry.y = centerY - geometry.height / 2f
            constrainGeometry()
            renderGeometry()
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
        val actions = arrayOf("关闭", "逆时针旋转", "保存", "分享")
        val dialog = AlertDialog.Builder(this)
            .setTitle("钉图操作")
            .setItems(actions, null)
            .setNegativeButton("取消", null)
            .create()
        actionDialog = dialog
        dialog.setOnDismissListener { if (actionDialog === dialog) actionDialog = null }
        dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        dialog.show()
        // The ListView selector covers the full row and receives native press/hotspot states.
        dialog.listView.setSelector(R.drawable.pin_action_feedback)
        val actionColors = dialog.getButton(AlertDialog.BUTTON_NEGATIVE).textColors
        dialog.listView.adapter = object : ArrayAdapter<String>(
            dialog.context, android.R.layout.simple_list_item_1, actions
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                return (super.getView(position, convertView, parent) as TextView).apply {
                    setTextColor(actionColors)
                }
            }
        }
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
