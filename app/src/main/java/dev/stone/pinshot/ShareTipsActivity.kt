package dev.stone.pinshot

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

/** Illustrated steps for pinning PinShot in the OriginOS image share panel. */
class ShareTipsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val accent = Color.rgb(0, 157, 187)
        val ink = Color.rgb(40, 51, 63)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(244, 247, 250))
        }
        val header = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(accent)
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        header.addView(TextView(this).apply {
            text = "‹"
            textSize = 36f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            contentDescription = "返回 PinShot"
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(dp(48), dp(52)))
        header.addView(TextView(this).apply {
            text = "分享技巧"
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
        }, LinearLayout.LayoutParams(0, dp(52), 1f))
        header.addView(TextView(this), LinearLayout.LayoutParams(dp(48), dp(52)))
        root.addView(header)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(22), dp(18), dp(28))
        }
        root.addView(ScrollView(this).apply {
            isFillViewport = true
            addView(content)
        }, LinearLayout.LayoutParams(-1, 0, 1f))

        content.addView(TextView(this).apply {
            text = "让 PinShot 出现在分享前排"
            textSize = 23f
            setTextColor(ink)
            setPadding(dp(2), 0, dp(2), dp(8))
        })
        content.addView(TextView(this).apply {
            text = "在 OriginOS 分享面板中把 PinShot 加入置顶服务，以后分享图片时就能更快找到它。"
            textSize = 16f
            setTextColor(Color.rgb(97, 111, 125))
            setPadding(dp(2), 0, dp(2), dp(18))
        })

        fun step(number: String, title: String, imageRes: Int) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(16), dp(16), dp(16))
                background = GradientDrawable().apply {
                    setColor(Color.WHITE)
                    cornerRadius = dp(18).toFloat()
                }
            }
            content.addView(card, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(16)
            })
            val heading = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            card.addView(heading, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) })
            heading.addView(TextView(this).apply {
                text = number
                textSize = 16f
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                background = GradientDrawable().apply {
                    setColor(accent)
                    shape = GradientDrawable.OVAL
                }
            }, LinearLayout.LayoutParams(dp(32), dp(32)).apply { rightMargin = dp(12) })
            heading.addView(TextView(this).apply {
                text = title
                textSize = 17f
                setTextColor(ink)
            }, LinearLayout.LayoutParams(0, -2, 1f))
            card.addView(ImageView(this).apply {
                setImageResource(imageRes)
                adjustViewBounds = true
                scaleType = ImageView.ScaleType.FIT_CENTER
                contentDescription = "第${number}步：${title}"
                background = GradientDrawable().apply {
                    setColor(Color.rgb(246, 248, 250))
                    cornerRadius = dp(12).toFloat()
                    setStroke(dp(1), Color.rgb(225, 232, 237))
                }
                clipToOutline = true
            }, LinearLayout.LayoutParams(-1, -2))
        }
        step("1", "选择一张图片并分享，向右滑到“全部”", R.drawable.tip_share_all)
        step("2", "点左上角的“编辑”按钮", R.drawable.tip_edit_services)
        step("3", "找到 PinShot，点 + 加入置顶服务，再点 ✓ 保存", R.drawable.tip_pin_service)
        step("4", "以后分享图片时，PinShot 就会出现在前排", R.drawable.tip_pinned_result)

        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
    }
}
