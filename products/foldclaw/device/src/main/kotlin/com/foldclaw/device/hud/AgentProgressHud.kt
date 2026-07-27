package com.foldclaw.device.hud

import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.foldclaw.device.a11y.FoldClawAccessibilityService
import com.foldclaw.domain.security.Redactor
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 跨应用步骤提示：暗色玻璃胶囊，不抢焦点、不拦截触摸。
 */
@Singleton
class AgentProgressHud @Inject constructor() {

    private val main = Handler(Looper.getMainLooper())
    private var host: View? = null
    private var card: LinearLayout? = null
    private var statusDot: View? = null
    private var brandView: TextView? = null
    private var stepView: TextView? = null
    private var detailView: TextView? = null
    private var hideRunnable: Runnable? = null

    fun showRunning(instruction: String) = main.post {
        cancelHide()
        ensureAttached()
        applyTone(HudTone.Running)
        brandView?.text = "FOLDCLAW"
        stepView?.text = "准备执行"
        detailView?.text = Redactor.brief(instruction, 52)
        detailView?.visibility = if (instruction.isBlank()) View.GONE else View.VISIBLE
        host?.visibility = View.VISIBLE
    }

    fun showStatus(stepLine: String, detail: String = "") = main.post {
        cancelHide()
        ensureAttached()
        applyTone(HudTone.Running)
        brandView?.text = "FOLDCLAW"
        stepView?.text = stepLine
        detailView?.text = Redactor.brief(detail, 52)
        detailView?.visibility = if (detail.isBlank()) View.GONE else View.VISIBLE
        host?.visibility = View.VISIBLE
    }

    fun updateStep(step: Int, toolName: String, detail: String? = null) = main.post {
        cancelHide()
        ensureAttached()
        applyTone(HudTone.Running)
        brandView?.text = "FOLDCLAW  ·  STEP ${step + 1}"
        stepView?.text = friendlyTool(toolName)
        val d = detail?.let { Redactor.brief(it, 52) }.orEmpty()
        detailView?.text = d
        detailView?.visibility = if (d.isBlank()) View.GONE else View.VISIBLE
        host?.visibility = View.VISIBLE
    }

    fun showTerminal(ok: Boolean, message: String) = main.post {
        ensureAttached()
        applyTone(if (ok) HudTone.Success else HudTone.End)
        brandView?.text = if (ok) "FOLDCLAW  ·  DONE" else "FOLDCLAW  ·  END"
        stepView?.text = if (ok) "任务已完成" else "已结束"
        detailView?.text = Redactor.brief(message, 52)
        detailView?.visibility = if (message.isBlank()) View.GONE else View.VISIBLE
        host?.visibility = View.VISIBLE
        scheduleHide(2_200L)
    }

    fun hide() = main.post {
        cancelHide()
        detach()
    }

    private fun scheduleHide(delayMs: Long) {
        cancelHide()
        val r = Runnable { detach() }
        hideRunnable = r
        main.postDelayed(r, delayMs)
    }

    private fun cancelHide() {
        hideRunnable?.let { main.removeCallbacks(it) }
        hideRunnable = null
    }

    private fun applyTone(tone: HudTone) {
        val service = FoldClawAccessibilityService.instance ?: return
        card?.background = cardBackground(service, tone)
        statusDot?.background = dotBackground(service, tone.dot)
        brandView?.setTextColor(tone.brand)
        stepView?.setTextColor(tone.step)
        detailView?.setTextColor(tone.detail)
    }

    private fun ensureAttached() {
        if (host != null) return
        val service = FoldClawAccessibilityService.instance ?: return

        val panel = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(service, 18), dp(service, 14), dp(service, 18), dp(service, 14))
            elevation = dp(service, 12).toFloat()
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            background = cardBackground(service, HudTone.Running)
        }

        // 品牌行：小圆点 + 字距拉开的品牌名（垂直居中对齐）
        val brandRow = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val dot = View(service).apply {
            val size = dp(service, 6)
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                marginEnd = dp(service, 8)
            }
            background = dotBackground(service, HudTone.Running.dot)
        }
        val brand = TextView(service).apply {
            text = "FOLDCLAW"
            setTextColor(HudTone.Running.brand)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            letterSpacing = 0.18f
            includeFontPadding = false
        }
        brandRow.addView(dot)
        brandRow.addView(brand)

        val step = TextView(service).apply {
            setTextColor(HudTone.Running.step)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            includeFontPadding = false
            setPadding(0, dp(service, 8), 0, 0)
        }
        val detail = TextView(service).apply {
            setTextColor(HudTone.Running.detail)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            includeFontPadding = false
            setPadding(0, dp(service, 4), 0, 0)
            maxLines = 2
        }

        panel.addView(
            brandRow,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
        panel.addView(
            step,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
        panel.addView(
            detail,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )

        // 外层居中包裹，避免全宽拉伸显得廉价
        val wrap = FrameLayout(service).apply {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            addView(
                panel,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(service, 44)
            width = (service.resources.displayMetrics.widthPixels * 0.88f).toInt()
                .coerceAtMost(dp(service, 420))
        }

        runCatching {
            service.getSystemService(WindowManager::class.java).addView(wrap, params)
            host = wrap
            card = panel
            statusDot = dot
            brandView = brand
            stepView = step
            detailView = detail
        }
    }

    private fun cardBackground(
        service: FoldClawAccessibilityService,
        tone: HudTone,
    ): GradientDrawable =
        GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(tone.bgTop, tone.bgBottom),
        ).apply {
            cornerRadius = dp(service, 22).toFloat()
            setStroke(dp(service, 1), tone.stroke)
        }

    private fun dotBackground(service: FoldClawAccessibilityService, color: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            setSize(dp(service, 6), dp(service, 6))
        }

    private fun detach() {
        val view = host ?: return
        val service = FoldClawAccessibilityService.instance
        runCatching {
            service?.getSystemService(WindowManager::class.java)?.removeView(view)
        }
        host = null
        card = null
        statusDot = null
        brandView = null
        stepView = null
        detailView = null
    }

    private fun dp(service: FoldClawAccessibilityService, value: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            service.resources.displayMetrics,
        ).toInt()

    /**
     * 深炭玻璃 + 细描边；状态只靠小圆点，不用色块条。
     */
    private enum class HudTone(
        val bgTop: Int,
        val bgBottom: Int,
        val stroke: Int,
        val dot: Int,
        val brand: Int,
        val step: Int,
        val detail: Int,
    ) {
        Running(
            bgTop = 0xE61A2228.toInt(),
            bgBottom = 0xE612171C.toInt(),
            stroke = 0x33FFFFFF.toInt(),
            dot = 0xFFD4AF77.toInt(),
            brand = 0xB3FFFFFF.toInt(),
            step = 0xFFF5F1EA.toInt(),
            detail = 0x99E8E2D8.toInt(),
        ),
        Success(
            bgTop = 0xE61A2228.toInt(),
            bgBottom = 0xE612171C.toInt(),
            stroke = 0x40D4AF77.toInt(),
            dot = 0xFFB8C9A8.toInt(),
            brand = 0xB3FFFFFF.toInt(),
            step = 0xFFF5F1EA.toInt(),
            detail = 0x99E8E2D8.toInt(),
        ),
        End(
            bgTop = 0xE61A2228.toInt(),
            bgBottom = 0xE612171C.toInt(),
            stroke = 0x33FFFFFF.toInt(),
            dot = 0xFFE0A090.toInt(),
            brand = 0xB3FFFFFF.toInt(),
            step = 0xFFF5F1EA.toInt(),
            detail = 0x99E8E2D8.toInt(),
        ),
    }

    companion object {
        fun friendlyTool(name: String): String = when (name) {
            "open_app" -> "打开应用"
            "open_settings_page" -> "打开设置页"
            "set_ringer_mode" -> "设置铃声模式"
            "get_ui_tree" -> "读取屏幕"
            "tap_node" -> "点击控件"
            "type_text" -> "输入文字"
            "swipe" -> "滑动"
            "go_back" -> "返回上一页"
            "go_home" -> "回到桌面"
            "set_alarm" -> "设置闹钟"
            "create_calendar_event" -> "创建日程"
            "get_weather" -> "查询天气"
            "web_search" -> "联网搜索"
            "play_music" -> "播放音乐"
            else -> name
        }
    }
}
