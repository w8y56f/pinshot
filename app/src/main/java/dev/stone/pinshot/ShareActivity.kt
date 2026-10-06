package dev.stone.pinshot

import android.content.ClipData
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import kotlin.concurrent.thread

/** Keeps the source app visible while one shared image becomes a pin. */
class ShareActivity : AppCompatActivity() {
    private val processingDialog = ProcessingDialog(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.setBackgroundColor(Color.TRANSPARENT)
        setContentView(View(this).apply { setBackgroundColor(Color.TRANSPARENT) })

        // Rotation is handled in place; after process recreation a fresh import is required.
        val shareIntent = intent
        @Suppress("DEPRECATION")
        val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            ?: intent.clipData?.let { if (it.itemCount > 0) it.getItemAt(0).uri else null }
        if (intent.action != Intent.ACTION_SEND ||
            intent.type?.startsWith("image/") != true || uri?.scheme != "content") {
            Toast.makeText(this, "没有收到可读取的图片", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        if (!Settings.canDrawOverlays(this)) {
            // Keep the URI grant when handing the share to the existing permission flow.
            startActivity(Intent(this, MainActivity::class.java).apply {
                action = Intent.ACTION_SEND
                type = shareIntent.type
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = shareIntent.clipData ?: ClipData.newUri(contentResolver, "图片", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })
            finish()
            return
        }

        processingDialog.show()
        val context = applicationContext
        thread(name = "ImportSharedImage") {
            val file = try {
                SharedImageImport.copy(context, uri)
            } catch (_: Exception) {
                null
            } catch (_: OutOfMemoryError) {
                null
            }
            runOnUiThread {
                if (isDestroyed || isFinishing) {
                    file?.delete()
                } else if (file == null) {
                    processingDialog.dismiss()
                    Toast.makeText(this, "图片读取失败，请重新分享", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    pin(file)
                }
            }
        }
    }

    private fun pin(file: File) {
        processingDialog.show("正在创建钉图…")
        val receiver = object : ResultReceiver(Handler(Looper.getMainLooper())) {
            override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                if (!isDestroyed && !isFinishing) {
                    processingDialog.dismiss()
                    finish()
                }
            }
        }
        try {
            startService(Intent(this, OverlayService::class.java)
                .putExtra(OverlayService.IMAGE_PATH, file.absolutePath)
                .putExtra(OverlayService.PIN_RESULT, receiver))
            // OverlayService now owns and deletes the temporary file.
        } catch (_: RuntimeException) {
            file.delete()
            processingDialog.dismiss()
            Toast.makeText(this, "钉图启动失败，请重试", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onDestroy() {
        processingDialog.dismiss()
        super.onDestroy()
    }
}
