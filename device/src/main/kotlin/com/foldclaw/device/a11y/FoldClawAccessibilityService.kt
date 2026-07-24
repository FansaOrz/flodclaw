package com.foldclaw.device.a11y

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * 折叠屏 AccessibilityService：提供窗口内容与手势能力，不持有任务真相、不决策风险。
 */
class FoldClawAccessibilityService : AccessibilityService() {

    val uiTreeTranslator = UiTreeTranslator()

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        connectionState.value = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 事件驱动刷新留给后续；当前由 Agent 主动 observe()
    }

    override fun onInterrupt() {
        connectionState.value = false
    }

    override fun onDestroy() {
        uiTreeTranslator.clear()
        instance = null
        connectionState.value = false
        super.onDestroy()
    }

    fun activeRoot(): AccessibilityNodeInfo? = rootInActiveWindow

    companion object {
        @Volatile
        var instance: FoldClawAccessibilityService? = null
            private set

        val connectionState = MutableStateFlow(false)
    }
}
