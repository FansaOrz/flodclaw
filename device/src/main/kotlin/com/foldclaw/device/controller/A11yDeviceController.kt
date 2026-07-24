package com.foldclaw.device.controller

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import com.foldclaw.domain.device.DeviceController
import com.foldclaw.domain.device.GlobalAction
import com.foldclaw.domain.model.DomainError
import com.foldclaw.domain.model.ErrorKind
import com.foldclaw.domain.model.ObservationSnapshot
import com.foldclaw.domain.model.Rect
import com.foldclaw.domain.model.Result
import com.foldclaw.device.a11y.FoldClawAccessibilityService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * DeviceController 生产实现：走 AccessibilityService。
 */
@Singleton
class A11yDeviceController @Inject constructor(
    @ApplicationContext private val context: Context,
) : DeviceController {

    private val keyguardManager: KeyguardManager by lazy {
        context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
    }

    override fun isAvailable(): Boolean = FoldClawAccessibilityService.instance != null

    override suspend fun observe(): Result<ObservationSnapshot> = withContext(Dispatchers.Main) {
        val service = FoldClawAccessibilityService.instance
            ?: return@withContext Result.Failure(
                DomainError(ErrorKind.DeviceCapabilityMissing, "Accessibility 未连接"),
            )
        val root = service.activeRoot()
        val locked = isLocked()
        if (root == null) {
            return@withContext Result.Success(
                ObservationSnapshot(
                    taskId = "",
                    stepIndex = 0,
                    packageName = null,
                    windowTitle = null,
                    displayId = 0,
                    isLocked = locked,
                    isSecureWindow = !locked,
                    rootId = null,
                    nodes = emptyMap(),
                    capturedAtEpochMs = System.currentTimeMillis(),
                ),
            )
        }
        try {
            val tree = service.uiTreeTranslator.translate(root)
            Result.Success(
                ObservationSnapshot(
                    taskId = "",
                    stepIndex = 0,
                    packageName = root.packageName?.toString(),
                    windowTitle = root.window?.title?.toString(),
                    displayId = 0,
                    isLocked = locked,
                    isSecureWindow = false,
                    rootId = tree.rootId,
                    nodes = tree.nodes,
                    capturedAtEpochMs = System.currentTimeMillis(),
                ),
            )
        } finally {
            root.recycle()
        }
    }

    override suspend fun tap(rect: Rect): Result<Unit> =
        performGesture(rect.centerX.toFloat(), rect.centerY.toFloat(), 50)

    override suspend fun longPress(rect: Rect): Result<Unit> =
        performGesture(rect.centerX.toFloat(), rect.centerY.toFloat(), 800)

    override suspend fun swipe(start: Rect, end: Rect, durationMs: Long): Result<Unit> {
        val service = service() ?: return missing()
        return suspendCancellableCoroutine { cont ->
            val path = Path().apply {
                moveTo(start.centerX.toFloat(), start.centerY.toFloat())
                lineTo(end.centerX.toFloat(), end.centerY.toFloat())
            }
            val stroke = GestureDescription.StrokeDescription(path, 0, durationMs.coerceAtLeast(50))
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            val dispatched = service.dispatchGesture(
                gesture,
                object : AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(g: GestureDescription?) {
                        if (cont.isActive) cont.resume(Result.Success(Unit))
                    }
                    override fun onCancelled(g: GestureDescription?) {
                        if (cont.isActive) {
                            cont.resume(Result.Failure(DomainError(ErrorKind.ActionFailed, "手势被取消")))
                        }
                    }
                },
                null,
            )
            if (!dispatched && cont.isActive) {
                cont.resume(Result.Failure(DomainError(ErrorKind.ActionFailed, "手势派发失败")))
            }
        }
    }

    override suspend fun clickNode(nodeId: String): Result<Unit> {
        val service = FoldClawAccessibilityService.instance
            ?: return missing()
        val live = service.uiTreeTranslator.getLive(nodeId)
            ?: return Result.Failure(DomainError(ErrorKind.ActionFailed, "节点 $nodeId 已失效，请先 get_ui_tree"))
        if (live.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            return Result.Success(Unit)
        }
        val bounds = android.graphics.Rect().also { live.getBoundsInScreen(it) }
        if (bounds.isEmpty) {
            return Result.Failure(DomainError(ErrorKind.ActionFailed, "节点 $nodeId 不可点击且无坐标"))
        }
        return tap(Rect(bounds.left, bounds.top, bounds.right, bounds.bottom))
    }

    override suspend fun setText(nodeId: String, text: String): Result<Unit> {
        val service = FoldClawAccessibilityService.instance
            ?: return missing()
        val live = service.uiTreeTranslator.getLive(nodeId)
            ?: return Result.Failure(DomainError(ErrorKind.ActionFailed, "节点 $nodeId 已失效，请先 get_ui_tree"))
        if (live.isPassword) {
            return Result.Failure(DomainError(ErrorKind.SecretBlocked, "禁止向密码框输入"))
        }
        if (!live.isEditable && !live.actionList.any { it.id == AccessibilityNodeInfo.ACTION_SET_TEXT }) {
            return Result.Failure(DomainError(ErrorKind.ActionFailed, "节点 $nodeId 不可编辑"))
        }
        live.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return if (live.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
            Result.Success(Unit)
        } else {
            Result.Failure(DomainError(ErrorKind.ActionFailed, "SET_TEXT 失败"))
        }
    }

    override suspend fun globalAction(action: GlobalAction): Result<Unit> {
        val service = service() ?: return missing()
        val a = when (action) {
            GlobalAction.BACK -> AccessibilityService.GLOBAL_ACTION_BACK
            GlobalAction.HOME -> AccessibilityService.GLOBAL_ACTION_HOME
            GlobalAction.RECENTS -> AccessibilityService.GLOBAL_ACTION_RECENTS
            GlobalAction.NOTIFICATIONS -> AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS
            GlobalAction.QUICK_SETTINGS -> AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS
        }
        return if (service.performGlobalAction(a)) Result.Success(Unit)
        else Result.Failure(DomainError(ErrorKind.ActionFailed, "全局动作 $action 失败"))
    }

    override suspend fun launchApp(packageName: String): Result<Unit> {
        return runCatching {
            val launcher = context.packageManager.getLaunchIntentForPackage(packageName)
                ?: Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    `package` = packageName
                }
            launcher.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launcher)
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = {
                Result.Failure(DomainError(ErrorKind.ActionFailed, "启动 $packageName 失败: ${it.message}"))
            },
        )
    }

    override fun isLocked(): Boolean = keyguardManager.isKeyguardLocked

    private fun service(): AccessibilityService? = FoldClawAccessibilityService.instance

    private fun missing(): Result<Unit> =
        Result.Failure(DomainError(ErrorKind.DeviceCapabilityMissing, "Accessibility 未连接"))

    private suspend fun performGesture(x: Float, y: Float, durationMs: Long): Result<Unit> {
        val service = service() ?: return missing()
        return suspendCancellableCoroutine { cont ->
            val path = Path().apply { moveTo(x, y) }
            val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            val dispatched = service.dispatchGesture(
                gesture,
                object : AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(g: GestureDescription?) {
                        if (cont.isActive) cont.resume(Result.Success(Unit))
                    }
                    override fun onCancelled(g: GestureDescription?) {
                        if (cont.isActive) {
                            cont.resume(Result.Failure(DomainError(ErrorKind.ActionFailed, "手势被取消")))
                        }
                    }
                },
                null,
            )
            if (!dispatched && cont.isActive) {
                cont.resume(Result.Failure(DomainError(ErrorKind.ActionFailed, "手势派发失败")))
            }
        }
    }
}
