package dev.stone.pinshot

import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

/** Shared progress UI for image import, crop saving, and overlay creation. */
internal class ProcessingDialog(private val activity: AppCompatActivity) {
    private var dialog: AlertDialog? = null
    private var messageView: TextView? = null

    fun show(message: String = "正在处理图片，请稍候…") {
        if (activity.isDestroyed || activity.isFinishing) return
        messageView?.let {
            it.text = message
            return
        }
        fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(24))
            addView(ProgressBar(activity).apply { isIndeterminate = true },
                LinearLayout.LayoutParams(dp(32), dp(32)))
        }
        messageView = TextView(activity).apply {
            text = message
            textSize = 16f
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        row.addView(messageView, LinearLayout.LayoutParams(0, -2, 1f).apply {
            marginStart = dp(16)
        })
        dialog = AlertDialog.Builder(activity)
            .setView(row)
            .setCancelable(false)
            .create().also { it.show() }
    }

    fun dismiss() {
        dialog?.dismiss()
        dialog = null
        messageView = null
    }
}
