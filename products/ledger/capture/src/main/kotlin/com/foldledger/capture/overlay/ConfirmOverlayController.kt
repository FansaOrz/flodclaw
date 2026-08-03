package com.foldledger.capture.overlay

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import com.foldledger.capture.R
import com.foldledger.capture.notify.CaptureAlertNotifier
import com.foldledger.capture.pipeline.CapturePipeline
import com.foldledger.domain.model.ParsedPayment
import com.foldledger.domain.util.MoneyFormat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@Singleton
class ConfirmOverlayController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val pipeline: CapturePipeline,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var currentView: View? = null
    private var currentPayment: ParsedPayment? = null
    private val started = AtomicBoolean(false)

    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            pipeline.confirmEvents.collect { payment ->
                show(payment)
            }
        }
    }

    fun canDrawOverlays(): Boolean = Settings.canDrawOverlays(context)

    fun openOverlaySettings() {
        context.startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                android.net.Uri.parse("package:${context.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    private fun show(payment: ParsedPayment) {
        if (!canDrawOverlays()) {
            // pending + 通知已由 pipeline 发出；此处只记日志提醒开悬浮窗
            Log.w(TAG, "no overlay permission; pending kept for in-app confirm")
            return
        }
        dismissViewOnly()
        currentPayment = payment
        val view = LayoutInflater.from(context).inflate(R.layout.overlay_confirm, null)
        val directionLabel = when (payment.direction.name) {
            "INCOME" -> "收入"
            "TRANSFER" -> "转账"
            else -> "支出"
        }
        view.findViewById<TextView>(R.id.overlayAmount).text =
            "¥${MoneyFormat.fenToYuan(payment.amountFen)}"
        view.findViewById<TextView>(R.id.overlayMerchant).text =
            payment.merchant.orEmpty().ifBlank { "待识别商户" }
        val sourceLabel = when (payment.source.name) {
            "WECHAT_NLS" -> "微信通知"
            "WECHAT_A11Y" -> "微信页面"
            "ALIPAY_NLS" -> "支付宝通知"
            "ALIPAY_A11Y" -> "支付宝页面"
            "BANK_SMS" -> "银行短信"
            else -> payment.source.name
        }
        view.findViewById<TextView>(R.id.overlaySource).text = "$directionLabel · $sourceLabel"
        view.findViewById<Button>(R.id.btnConfirm).setOnClickListener {
            val p = currentPayment ?: return@setOnClickListener
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            scope.launch(Dispatchers.IO) {
                runCatching { pipeline.confirmFromOverlay(p) }
                    .onFailure { Log.e(TAG, "confirmFromOverlay failed", it) }
                CaptureAlertNotifier.cancel(context)
            }
            dismissAnimated()
        }
        view.findViewById<Button>(R.id.btnReview).setOnClickListener {
            val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                runCatching { context.startActivity(launch) }
                    .onFailure { Log.e(TAG, "open app for review failed", it) }
            }
            CaptureAlertNotifier.cancel(context)
            dismissAnimated()
        }
        view.findViewById<TextView>(R.id.btnLater).setOnClickListener {
            dismissAnimated()
        }

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val density = context.resources.displayMetrics.density
        val screenWidth = context.resources.displayMetrics.widthPixels
        val horizontalMargin = (16 * density).toInt()
        val maxWidth = (420 * density).toInt()
        val params = WindowManager.LayoutParams(
            (screenWidth - horizontalMargin * 2).coerceAtMost(maxWidth),
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = (16 * density).toInt()
        }
        runCatching {
            wm.addView(view, params)
            currentView = view
            view.alpha = 0f
            view.translationY = -24 * density
            view.scaleX = 0.98f
            view.scaleY = 0.98f
            view.animate()
                .alpha(1f)
                .translationY(0f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(280L)
                .start()
        }.onFailure {
            Log.e(TAG, "add overlay failed", it)
        }
    }

    fun dismiss() {
        dismissAnimated()
    }

    private fun dismissAnimated() {
        val view = currentView ?: return
        currentView = null
        currentPayment = null
        view.animate()
            .alpha(0f)
            .translationY(-16 * context.resources.displayMetrics.density)
            .setDuration(180L)
            .withEndAction { runCatching { wm.removeView(view) } }
            .start()
    }

    private fun dismissViewOnly() {
        currentView?.let { runCatching { wm.removeView(it) } }
        currentView = null
        currentPayment = null
    }

    companion object {
        private const val TAG = "FoldLedgerOverlay"
    }
}
