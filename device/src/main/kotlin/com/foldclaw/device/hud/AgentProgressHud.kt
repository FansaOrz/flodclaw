package com.foldclaw.device.hud

import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.foldclaw.device.a11y.FoldClawAccessibilityService
import com.foldclaw.domain.security.Redactor
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 跨应用步骤提示：挂在 AccessibilityService 的 TYPE_ACCESSIBILITY_OVERLAY 上，
 * 不抢焦点、不拦截触摸，避免挡 agent 点击。
 */
@Singleton
class AgentProgressHud @Inject constructor() {

    private val main = Handler(Looper.getMainLooper())
    private var root: LinearLayout? = null
    private var titleView: TextView? = null
    private var stepView: TextView? = null
    private var detailView: TextView? = null
    private var hideRunnable: Runnable? = null

    fun showRunning(instruction: String) = main.post {
        cancelHide()
        ensureAttached()
        titleView?.text = "FoldClaw 执行中"
        stepView?.text = "准备中…"
        detailView?.text = Redactor.brief(instruction, 48)
        root?.visibility = View.VISIBLE
    }

    fun showStatus(stepLine: String, detail: String = "") = main.post {
        cancelHide()
        ensureAttached()
        titleView?.text = "FoldClaw 执行中"
        stepView?.text = stepLine
        detailView?.text = Redactor.brief(detail, 56)
        root?.visibility = View.VISIBLE
    }

    fun updateStep(step: Int, toolName: String, detail: String? = null) = main.post {
        cancelHide()
        ensureAttached()
        titleView?.text = "FoldClaw 执行中"
        stepView?.text = "第 ${step + 1} 步 · ${friendlyTool(toolName)}"
        detailView?.text = detail?.let { Redactor.brief(it, 56) }.orEmpty()
        root?.visibility = View.VISIBLE
    }

    fun showTerminal(ok: Boolean, message: String) = main.post {
        ensureAttached()
        titleView?.text = if (ok) "FoldClaw 已完成" else "FoldClaw 已结束"
        stepView?.text = if (ok) "任务完成" else "请查看结果"
        detailView?.text = Redactor.brief(message, 56)
        root?.visibility = View.VISIBLE
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

    private fun ensureAttached() {
        if (root != null) return
        val service = FoldClawAccessibilityService.instance ?: return
        val pad = dp(service, 14)
        val panel = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(0xE0121820.toInt())
            elevation = dp(service, 8).toFloat()
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
        val title = TextView(service).apply {
            setTextColor(0xFFFFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.DEFAULT_BOLD
        }
        val step = TextView(service).apply {
            setTextColor(0xFFE8F0FF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setPadding(0, dp(service, 4), 0, 0)
        }
        val detail = TextView(service).apply {
            setTextColor(0xFFB0B8C4.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(0, dp(service, 2), 0, 0)
            maxLines = 2
        }
        panel.addView(title)
        panel.addView(step)
        panel.addView(detail)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(service, 36)
            width = service.resources.displayMetrics.widthPixels - dp(service, 32)
        }

        runCatching {
            service.getSystemService(WindowManager::class.java).addView(panel, params)
            root = panel
            titleView = title
            stepView = step
            detailView = detail
        }
    }

    private fun detach() {
        val panel = root ?: return
        val service = FoldClawAccessibilityService.instance
        runCatching {
            service?.getSystemService(WindowManager::class.java)?.removeView(panel)
        }
        root = null
        titleView = null
        stepView = null
        detailView = null
    }

    private fun dp(service: FoldClawAccessibilityService, value: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            service.resources.displayMetrics,
        ).toInt()

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
            else -> name
        }
    }
}
