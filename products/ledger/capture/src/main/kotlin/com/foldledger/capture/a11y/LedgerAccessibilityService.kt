package com.foldledger.capture.a11y

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.foldledger.capture.pipeline.CapturePipeline
import com.foldledger.data.parse.PaymentNotificationParser
import com.foldledger.domain.model.ParsedPayment
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Read-only capture of Alipay / WeChat success screens.
 * On fold split-screen, scans ALL application windows so the payment app
 * can be read even when FoldLedger is focused on the other pane.
 */
@AndroidEntryPoint
class LedgerAccessibilityService : AccessibilityService() {

    @Inject lateinit var parser: PaymentNotificationParser
    @Inject lateinit var pipeline: CapturePipeline

    private var lastFingerprint: String? = null
    private var lastScanAt = 0L
    private var lastRawHash: Int = 0

    override fun onServiceConnected() {
        instance = this
        Log.i(TAG, "a11y connected")
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                AccessibilityEvent.TYPE_WINDOWS_CHANGED or
                AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            notificationTimeout = 80
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        if (event.eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
            val pkg = event.packageName?.toString().orEmpty()
            handleNotificationEvent(pkg, event)
            return
        }

        // On fold split-screen, content-change events may come from either pane.
        // Always scan payment-app windows globally instead of trusting event package alone.
        val now = System.currentTimeMillis()
        // 支付成功页文案刷新很快，节流过长会漏抓；空窗不计入节流，避免首帧空白挡住后续
        if (now - lastScanAt < 180) return

        val snapshots = collectPaymentWindowTexts()
        if (snapshots.isEmpty()) return
        lastScanAt = now

        val joinedHash = snapshots.joinToString { "${it.pkg}:${it.text}" }.hashCode()
        if (joinedHash == lastRawHash) return
        lastRawHash = joinedHash

        for (snap in snapshots) {
            Log.i(TAG, "win pkg=${snap.pkg} len=${snap.text.length} snippet=${snap.text.take(80).replace('\n', ' ')}")
            val parsed = parseWindow(snap.pkg, snap.text, now) ?: continue
            submitIfNew(parsed)
        }
    }

    private fun handleNotificationEvent(pkg: String, event: AccessibilityEvent) {
        if (pkg != PaymentNotificationParser.PKG_WECHAT && pkg != PaymentNotificationParser.PKG_ALIPAY) return
        val pieces = event.text?.mapNotNull { it?.toString() }.orEmpty()
        val body = (pieces + listOfNotNull(event.contentDescription?.toString())).joinToString(" ")
        if (body.isBlank()) return
        Log.i(TAG, "a11y-notif pkg=$pkg body=${body.take(100)}")
        val parsed = parser.parse(pkg, pieces.firstOrNull().orEmpty(), body, System.currentTimeMillis()) ?: return
        submitIfNew(parsed)
    }

    private fun parseWindow(pkg: String, text: String, now: Long): ParsedPayment? {
        return when (pkg) {
            PaymentNotificationParser.PKG_WECHAT -> parser.parseWechatUiText(text, now)
            PaymentNotificationParser.PKG_ALIPAY -> parser.parseAlipayUiText(text, now)
            else -> null
        }
    }

    private fun submitIfNew(parsed: ParsedPayment) {
        if (parsed.fingerprint == lastFingerprint) return
        lastFingerprint = parsed.fingerprint
        Log.i(TAG, "submit ${parsed.source} ${parsed.amountFen} ${parsed.merchant}")
        pipeline.submit(parsed)
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (instance === this) instance = null
        Log.i(TAG, "a11y destroyed")
        super.onDestroy()
    }

    data class WindowText(val pkg: String, val text: String)

    private fun collectPaymentWindowTexts(): List<WindowText> {
        val out = LinkedHashMap<String, StringBuilder>()

        fun add(pkg: String?, node: AccessibilityNodeInfo?) {
            if (pkg == null || node == null) return
            if (pkg != PaymentNotificationParser.PKG_WECHAT && pkg != PaymentNotificationParser.PKG_ALIPAY) {
                node.recycle()
                return
            }
            val sb = out.getOrPut(pkg) { StringBuilder() }
            sb.append(collectText(node)).append(' ')
            node.recycle()
        }

        runCatching {
            windows?.forEach { win ->
                if (win.type != AccessibilityWindowInfo.TYPE_APPLICATION &&
                    win.type != AccessibilityWindowInfo.TYPE_SPLIT_SCREEN_DIVIDER
                ) {
                    // still try application-like windows; skip system UI
                }
                if (win.type == AccessibilityWindowInfo.TYPE_APPLICATION ||
                    win.type == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY
                ) {
                    val root = win.root ?: return@forEach
                    val pkg = root.packageName?.toString() ?: win.title?.toString()
                    add(pkg, root)
                }
            }
        }

        // Fallback active root (single-app / cover screen)
        rootInActiveWindow?.let { root ->
            add(root.packageName?.toString(), root)
        }

        return out.map { (pkg, sb) -> WindowText(pkg, sb.toString().take(2000)) }
            .filter { it.text.isNotBlank() }
    }

    private fun collectText(node: AccessibilityNodeInfo, depth: Int = 0): String {
        if (depth > 16) return ""
        val sb = StringBuilder()
        node.text?.let { sb.append(it).append(' ') }
        node.contentDescription?.let { sb.append(it).append(' ') }
        node.hintText?.let { sb.append(it).append(' ') }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            sb.append(collectText(child, depth + 1))
            child.recycle()
        }
        return sb.toString()
    }

    /** Debug: list every payment-app window text even if FoldLedger is focused. */
    fun debugSnapshot(): String {
        val snaps = collectPaymentWindowTexts()
        if (snaps.isEmpty()) {
            val active = rootInActiveWindow
            val activePkg = active?.packageName?.toString() ?: "(none)"
            active?.recycle()
            return buildString {
                appendLine("未找到微信/支付宝窗口文本。")
                appendLine("当前活动窗: $activePkg")
                appendLine("分屏时请保持左侧微信成功页可见，再点诊断。")
                appendLine("若仍为空，说明微信未向无障碍暴露控件树。")
            }
        }
        return buildString {
            snaps.forEach { snap ->
                val parsed = parseWindow(snap.pkg, snap.text, System.currentTimeMillis())
                appendLine("包名: ${snap.pkg}")
                appendLine("文本长度: ${snap.text.length}")
                appendLine("解析: ${parsed?.let { "¥${it.amountFen / 100.0} ${it.merchant} (${it.source})" } ?: "未识别"}")
                appendLine("摘要: ${snap.text.take(240).replace('\n', ' ')}")
                appendLine("---")
            }
        }
    }

    companion object {
        private const val TAG = "FoldLedgerCapture"

        @Volatile
        var instance: LedgerAccessibilityService? = null

        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ).orEmpty()
            val cn = "${context.packageName}/${LedgerAccessibilityService::class.java.name}"
            return enabled.split(':').any {
                it.equals(cn, ignoreCase = true) || it.contains(LedgerAccessibilityService::class.java.simpleName)
            }
        }

        fun openSettings(context: Context) {
            context.startActivity(
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}
