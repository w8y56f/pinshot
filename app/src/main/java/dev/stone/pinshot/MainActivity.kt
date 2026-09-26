package dev.stone.pinshot

import android.content.Intent
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.provider.MediaStore
import android.widget.ScrollView
import androidx.core.content.FileProvider
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import java.io.File
import kotlin.concurrent.thread
import kotlin.math.max

/** Receives one shared image and keeps its own copy before requesting overlay permission. */
class MainActivity : AppCompatActivity() {
    private lateinit var status: TextView
    private lateinit var permissionButton: Button
    private var pendingImage: File? = null
    private var cameraFile: File? = null
    private var resumed = false
    private var handedOff = false
    private var textPinInProgress = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
        val accent = Color.rgb(0, 157, 187)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(accent)
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(28))
            addView(TextView(this@MainActivity).apply {
                text = "PinShot"
                textSize = 28f
                setTextColor(Color.WHITE)
            })
            addView(TextView(this@MainActivity).apply {
                text = packageManager.getPackageInfo(packageName, 0).versionName ?: ""
                textSize = 14f
                setTextColor(Color.WHITE)
                alpha = .8f
                setPadding(dp(12), dp(8), 0, 0)
            })
        }
        root.addView(header)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(24))
        }
        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.rgb(244, 247, 250))
            addView(content)
        }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        fun card(): LinearLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dp(20).toFloat()
            }
            content.addView(this, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(20) })
        }
        val moreDescription = "图片可以来源于：\n• 相册\n• 区域截图\n• 截图\n\n图片会悬浮在其他应用上方。\n• 单指拖动图片\n• 拖动右下角小手柄等比例缩放\n• 双击可关闭、逆时针旋转\n• 可保存当前图片到相册\n• 支持再次分享\n• 支持同时多张悬浮图"
        val introduction = card()
        introduction.addView(TextView(this).apply {
            text = "图片 → 分享 → 选择 PinShot → 实现钉图"
            textSize = 17f
            setTextColor(Color.rgb(55, 65, 78))
            setLineSpacing(dp(5).toFloat(), 1f)
        })
        val helpLinks = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(16), 0, 0)
        }
        introduction.addView(helpLinks)
        fun helpLink(label: String, onClick: () -> Unit) {
            helpLinks.addView(TextView(this).apply {
                text = label
                textSize = 16f
                setTextColor(accent)
                paintFlags = paintFlags or Paint.UNDERLINE_TEXT_FLAG
                setPadding(0, dp(4), dp(24), dp(4))
                setOnClickListener { onClick() }
            })
        }
        helpLink("更多说明") {
            AlertDialog.Builder(this)
                .setTitle("更多说明")
                .setMessage(moreDescription)
                .setPositiveButton("知道了", null)
                .show()
        }
        helpLink("分享技巧") {
            startActivity(Intent(this, ShareTipsActivity::class.java))
        }
        val textCard = card()
        textCard.addView(TextView(this).apply {
            text = "贴文字"
            textSize = 20f
            setTextColor(accent)
            setPadding(0, 0, 0, dp(12))
        })
        val textInput = EditText(this).apply {
            hint = "输入文字…"
            textSize = 17f
            minLines = 3
            maxLines = 8
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            gravity = android.view.Gravity.TOP or android.view.Gravity.START
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = GradientDrawable().apply {
                setColor(Color.rgb(247, 249, 251))
                setStroke(dp(1), Color.rgb(207, 222, 228))
                cornerRadius = dp(12).toFloat()
            }
        }
        textCard.addView(textInput, LinearLayout.LayoutParams(-1, -2))
        val textActions = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(12), 0, 0)
        }
        textCard.addView(textActions)
        val pinActions = LinearLayout(this)
        textActions.addView(pinActions)
        val editActions = LinearLayout(this)
        textActions.addView(editActions)
        fun textButton(parent: LinearLayout, label: String, action: () -> Unit) {
            parent.addView(Button(this).apply {
                text = label
                textSize = 16f
                setTextColor(accent)
                backgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(226, 247, 250))
                setOnClickListener { action() }
            }, LinearLayout.LayoutParams(0, dp(52), 1f))
        }
        textButton(pinActions, "钉剪切板文字") {
            readClipboardText()?.let(::pinText)
        }
        textButton(pinActions, "钉输入框文字") {
            val value = textInput.text.toString()
            if (value.isBlank()) showTextMessage("请先输入文字") else pinText(value)
        }
        textButton(editActions, "CTRL+V粘贴") {
            readClipboardText()?.let { value ->
                val start = textInput.selectionStart.coerceAtLeast(0)
                val end = textInput.selectionEnd.coerceAtLeast(0)
                textInput.text.replace(minOf(start, end), maxOf(start, end), value)
            }
        }
        textButton(editActions, "清空输入框") {
            textInput.text.clear()
        }
        val pictures = card()
        pictures.addView(TextView(this).apply {
            text = "贴图片"
            textSize = 20f
            setTextColor(accent)
            setPadding(0, 0, 0, dp(12))
        })
        val actions = LinearLayout(this)
        pictures.addView(actions)
        fun pictureButton(label: String, action: () -> Unit) {
            actions.addView(Button(this).apply {
                text = label
                textSize = 17f
                setTextColor(accent)
                backgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(226, 247, 250))
                setOnClickListener { action() }
            }, LinearLayout.LayoutParams(0, dp(56), 1f).apply {
                marginStart = dp(4)
                marginEnd = dp(4)
            })
        }
        pictureButton("相册") {
            try {
                val picker = if (Build.VERSION.SDK_INT >= 33) {
                    Intent(MediaStore.ACTION_PICK_IMAGES).apply { type = "image/*" }
                } else {
                    Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        type = "image/*"
                        addCategory(Intent.CATEGORY_OPENABLE)
                    }
                }
                startActivityForResult(picker, 101)
            } catch (_: android.content.ActivityNotFoundException) {
                status.text = "无法打开图片选择器。"
            }
        }
        pictureButton("相机") { takePhoto() }
        val permission = card()
        status = TextView(this).apply {
            textSize = 15f
            setTextColor(Color.rgb(85, 99, 113))
        }
        permission.addView(status)
        permissionButton = Button(this).apply {
            text = "允许显示在其他应用上层"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            }
        }
        permission.addView(permissionButton)
        setContentView(root)
        var keyboardWasVisible = false
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val keyboard = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, keyboard.bottom))
            val keyboardVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            if (keyboardVisible != keyboardWasVisible) {
                keyboardWasVisible = keyboardVisible
                header.visibility = if (keyboardVisible) View.GONE else View.VISIBLE
                textInput.minLines = if (keyboardVisible) 2 else 3
                textInput.maxLines = if (keyboardVisible) 3 else 8
            }
            if (keyboardVisible && textInput.hasFocus()) {
                scroll.post {
                    // Include both button rows below the editor in the visible area.
                    val cardBottom = textCard.bottom + dp(12)
                    scroll.scrollTo(0, (cardBottom - scroll.height).coerceAtLeast(0))
                }
            }
            insets
        }
        cameraFile = savedInstanceState?.getString("cameraFile")?.let(::File)

        handedOff = savedInstanceState?.getBoolean("handedOff") ?: false
        pendingImage = savedInstanceState?.getString("pendingImage")?.let(::File)?.takeIf { it.isFile }
        if (handedOff) {
            finish()
        } else if (pendingImage == null && intent.action == Intent.ACTION_SEND) {
            @Suppress("DEPRECATION")
            val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                ?: intent.clipData?.let { if (it.itemCount > 0) it.getItemAt(0).uri else null }
            if (intent.type?.startsWith("image/") == true && uri?.scheme == "content") {
                importImage(uri)
            } else {
                status.text = "没有收到可读取的图片，请从截图或相册中分享一张图片。"
            }
        }
    }

    private fun showTextMessage(message: String) {
        status.text = message
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun readClipboardText(): String? {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val description = clipboard.primaryClipDescription
        val hasText = description?.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) == true ||
            description?.hasMimeType(ClipDescription.MIMETYPE_TEXT_HTML) == true
        if (!hasText) {
            showTextMessage("剪贴板里没有可用的文字")
            return null
        }
        val value = clipboard.primaryClip?.let { clip ->
            if (clip.itemCount > 0) clip.getItemAt(0).coerceToText(this)?.toString() else null
        }
        if (value.isNullOrBlank()) {
            showTextMessage("剪贴板里没有可用的文字")
            return null
        }
        return value
    }

    private fun pinText(value: String) {
        if (textPinInProgress) return
        textPinInProgress = true
        status.text = "正在生成文字钉图…"
        thread(name = "RenderTextPin") {
            var file: File? = null
            try {
                val bitmap = TextPinRenderer.render(this, value)
                try {
                    file = File.createTempFile("text-pin-", ".png", cacheDir)
                    file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                } finally {
                    bitmap.recycle()
                }
                val rendered = file
                runOnUiThread {
                    textPinInProgress = false
                    if (isDestroyed || isFinishing) rendered?.delete() else {
                        pendingImage = rendered
                        status.text = "文字已准备好。请允许悬浮窗，返回后会自动钉图。"
                        pinIfReady()
                    }
                }
            } catch (error: IllegalArgumentException) {
                file?.delete()
                runOnUiThread {
                    textPinInProgress = false
                    if (!isDestroyed && !isFinishing) showTextMessage(error.message ?: "文字无法钉到屏幕")
                }
            } catch (_: Exception) {
                file?.delete()
                runOnUiThread {
                    textPinInProgress = false
                    if (!isDestroyed && !isFinishing) showTextMessage("文字钉图生成失败，请重试")
                }
            }
        }
    }

    private fun takePhoto() {
        try {
            cameraFile?.delete()
            val directory = File(cacheDir, "camera").apply { mkdirs() }
            val file = File.createTempFile("photo-", ".jpg", directory)
            cameraFile = file
            val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
            startActivityForResult(Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                putExtra(MediaStore.EXTRA_OUTPUT, uri)
                clipData = ClipData.newRawUri("photo", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }, 102)
        } catch (_: Exception) {
            cameraFile?.delete()
            cameraFile = null
            status.text = "无法启动相机，请从相册选择图片。"
        }
    }

    @Deprecated("Uses the platform activity result API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) {
            if (requestCode == 102 || requestCode == 103) {
                cameraFile?.delete()
                cameraFile = null
            }
            return
        }
        when (requestCode) {
            101 -> data?.data?.let { openCrop(it) }
            102 -> cameraFile?.let {
                openCrop(FileProvider.getUriForFile(this, "$packageName.files", it))
            }
            103 -> {
                cameraFile?.delete()
                cameraFile = null
                pendingImage = data?.getStringExtra(OverlayService.IMAGE_PATH)?.let(::File)
                status.text = "图片已准备好。请允许悬浮窗，返回后会自动钉图。"
                pinIfReady()
            }
        }
    }

    private fun openCrop(uri: Uri) {
        startActivityForResult(Intent(this, CropActivity::class.java).apply {
            data = uri
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, 103)
    }

    private fun importImage(uri: Uri) {
        status.text = "正在读取图片…"
        thread(name = "ImportSharedImage") {
            var file: File? = null
            try {
                val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, uri)) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    val longest = max(info.size.width, info.size.height)
                    if (longest > 4096) {
                        decoder.setTargetSize(
                            max(1, (info.size.width * 4096L / longest).toInt()),
                            max(1, (info.size.height * 4096L / longest).toInt())
                        )
                    }
                }
                try {
                    file = File.createTempFile("shared-pin-", ".png", cacheDir)
                    file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                } finally {
                    bitmap.recycle()
                }
                val imported = file
                runOnUiThread {
                    if (isDestroyed || isFinishing) {
                        imported?.delete()
                    } else {
                        pendingImage = imported
                        status.text = "图片已准备好。请允许悬浮窗，返回后会自动钉图。"
                        pinIfReady()
                    }
                }
            } catch (_: Exception) {
                file?.delete()
                runOnUiThread {
                    if (!isDestroyed && !isFinishing) status.text = "图片读取失败，请重新分享图片。"
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        permissionButton.visibility = if (Settings.canDrawOverlays(this)) View.GONE else View.VISIBLE
        if (intent.action != Intent.ACTION_SEND) {
            status.text = if (Settings.canDrawOverlays(this)) "已就绪，可以从截图或相册分享图片到钉图。" else "首次使用请先允许悬浮窗。"
        }
        pinIfReady()
    }

    private fun pinIfReady() {
        val file = pendingImage ?: return
        if (!resumed || handedOff || !Settings.canDrawOverlays(this)) return
        startService(Intent(this, OverlayService::class.java).putExtra(OverlayService.IMAGE_PATH, file.absolutePath))
        handedOff = true
        pendingImage = null
        finish()
    }

    override fun onPause() {
        resumed = false
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("cameraFile", cameraFile?.absolutePath)
        outState.putString("pendingImage", pendingImage?.absolutePath)
        outState.putBoolean("handedOff", handedOff)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        if (isFinishing) {
            pendingImage?.delete()
            cameraFile?.delete()
        }
        super.onDestroy()
    }
}
