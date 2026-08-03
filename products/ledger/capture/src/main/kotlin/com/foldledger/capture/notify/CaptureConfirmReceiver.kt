package com.foldledger.capture.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.foldledger.capture.pipeline.CapturePipeline
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 通知栏「记一笔 / 忽略」：无悬浮窗权限时的兜底确认。
 */
@AndroidEntryPoint
class CaptureConfirmReceiver : BroadcastReceiver() {
    @Inject lateinit var pipeline: CapturePipeline

    override fun onReceive(context: Context, intent: Intent?) {
        intent ?: return
        val fingerprint = intent.getStringExtra(EXTRA_FINGERPRINT).orEmpty()
        if (fingerprint.isBlank()) return
        val pendingResult = goAsync()
        scope.launch {
            try {
                when (intent.action) {
                    ACTION_CONFIRM -> pipeline.confirmByFingerprint(fingerprint)
                    ACTION_DISMISS -> pipeline.dismissByFingerprint(fingerprint)
                }
                CaptureAlertNotifier.cancel(context)
            } catch (t: Throwable) {
                Log.e(TAG, "action failed", t)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_CONFIRM = "com.foldledger.action.CONFIRM_PENDING"
        const val ACTION_DISMISS = "com.foldledger.action.DISMISS_PENDING"
        const val EXTRA_FINGERPRINT = "fingerprint"
        private const val TAG = "FoldLedgerConfirmRx"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
