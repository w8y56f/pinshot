package dev.stone.pinshot

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/** Entry point used both by the launcher and the Quick Settings tile. */
class CaptureActivity : AppCompatActivity() {
    private val capture = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val intent = Intent(this, CaptureService::class.java)
                .putExtra(CaptureService.RESULT_CODE, result.resultCode)
                .putExtra(CaptureService.RESULT_DATA, result.data)
            ContextCompat.startForegroundService(this, intent)
        } else Toast.makeText(this, "未授予屏幕截图权限", Toast.LENGTH_SHORT).show()
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "请允许钉图显示在其他应用上层", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        } else requestCapture()
    }

    override fun onResume() {
        super.onResume()
        if (Settings.canDrawOverlays(this) && !captureLaunched) requestCapture()
    }

    private var captureLaunched = false
    private fun requestCapture() {
        if (captureLaunched) return
        captureLaunched = true
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        capture.launch(manager.createScreenCaptureIntent())
    }
}
