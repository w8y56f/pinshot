package dev.stone.pinshot

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.IBinder
import androidx.core.app.NotificationCompat
import java.io.File
import java.io.FileOutputStream

class CaptureService : Service() {
    companion object { const val RESULT_CODE = "result_code"; const val RESULT_DATA = "result_data"; private const val CHANNEL = "capture" }
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannel()
        startForeground(11, NotificationCompat.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.ic_menu_camera).setContentTitle("正在准备截图").setOngoing(true).build())
        val data = intent?.getParcelableExtra<Intent>(RESULT_DATA) ?: return START_NOT_STICKY
        val code = intent.getIntExtra(RESULT_CODE, 0)
        projection = (getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager).getMediaProjection(code, data)
        projection?.registerCallback(object : MediaProjection.Callback() {}, null)
        takeScreenshot()
        return START_NOT_STICKY
    }

    private fun takeScreenshot() {
        val metrics = resources.displayMetrics
        val w = metrics.widthPixels; val h = metrics.heightPixels
        reader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
        reader!!.setOnImageAvailableListener({ source ->
            val image = source.acquireLatestImage() ?: return@setOnImageAvailableListener
            val plane = image.planes[0]
            val rowPadding = plane.rowStride - plane.pixelStride * w
            val wide = Bitmap.createBitmap(w + rowPadding / plane.pixelStride, h, Bitmap.Config.ARGB_8888)
            wide.copyPixelsFromBuffer(plane.buffer)
            image.close()
            val bitmap = Bitmap.createBitmap(wide, 0, 0, w, h)
            val target = File(cacheDir, "capture.png")
            FileOutputStream(target).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            cleanup()
            startActivity(Intent(this, RegionSelectionActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            stopSelf()
        }, null)
        display = projection!!.createVirtualDisplay("PinShot", w, h, metrics.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader!!.surface, null, null)
    }

    private fun cleanup() { display?.release(); reader?.close(); projection?.stop() }
    private fun createChannel() { (getSystemService(NotificationManager::class.java)).createNotificationChannel(NotificationChannel(CHANNEL, "截图", NotificationManager.IMPORTANCE_LOW)) }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() { cleanup(); super.onDestroy() }
}
