package com.foldclaw.domain.device

import com.foldclaw.domain.model.ObservationSnapshot
import com.foldclaw.domain.model.Rect
import com.foldclaw.domain.model.Result

/**
 * 设备控制器接口。业务只依赖它，真实实现走 AccessibilityService，测试用 Fake。
 *
 * 所有方法都返回 [Result]；[tap] 等返回成功只表示请求被接受，
 * 调用方必须用 [observe] 重新观察验证真实结果。
 */
interface DeviceController {
    /** 是否已连接且权限可用。 */
    fun isAvailable(): Boolean

    /** 获取当前窗口不可变快照。 */
    suspend fun observe(): Result<ObservationSnapshot>

    /** 点击屏幕坐标。只作用于当前可交互显示。 */
    suspend fun tap(rect: Rect): Result<Unit>

    /** 长按。 */
    suspend fun longPress(rect: Rect): Result<Unit>

    /** 滑动。 */
    suspend fun swipe(start: Rect, end: Rect, durationMs: Long): Result<Unit>

    /** 按节点 id 点击：优先 ACTION_CLICK，失败再手势 tap。 */
    suspend fun clickNode(nodeId: String): Result<Unit>

    /** 对可编辑节点设置文本。只对公开 ACTION_SET_TEXT 的节点有效。 */
    suspend fun setText(nodeId: String, text: String): Result<Unit>

    /** 全局动作：返回、Home、最近任务。 */
    suspend fun globalAction(action: GlobalAction): Result<Unit>

    /** 按包名/Activity 启动 App。用于第一类任务（日历/闹钟 Intent）。 */
    suspend fun launchApp(packageName: String): Result<Unit>

    /** 当前是否锁屏。 */
    fun isLocked(): Boolean
}

enum class GlobalAction { BACK, HOME, RECENTS, NOTIFICATIONS, QUICK_SETTINGS }
