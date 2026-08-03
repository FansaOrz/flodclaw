package com.foldledger.capture.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.foldledger.domain.model.ParsedPayment
import com.foldledger.domain.util.MoneyFormat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Heads-up style alert when a payment is pending confirmation.
 * Overlay may be blocked on some ROMs; notification action is the reliable fallback.
 */
@Singleton
class CaptureAlertNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val nm = context.getSystemService(NotificationManager::class.java)

    init {
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "待确认账单",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "自动识别到支付后提醒确认记账"
            },
        )
    }

    fun notifyPending(payment: ParsedPayment) {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: return
        val contentPi = PendingIntent.getActivity(
            context,
            0,
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val confirmPi = actionPending(CaptureConfirmReceiver.ACTION_CONFIRM, payment.fingerprint, 1)
        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(com.foldledger.capture.R.drawable.ic_foldledger_notification)
            .setContentTitle("识别到 ¥${MoneyFormat.fenToYuan(payment.amountFen)}")
            .setContentText(payment.merchant)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("${payment.merchant}\n待你核对后写入 FoldLedger"),
            )
            .setContentIntent(contentPi)
            .addAction(0, "确认记账", confirmPi)
            .addAction(0, "打开核对", contentPi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        nm.notify(NOTIF_ID, notif)
    }

    private fun actionPending(action: String, fingerprint: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, CaptureConfirmReceiver::class.java).apply {
            this.action = action
            putExtra(CaptureConfirmReceiver.EXTRA_FINGERPRINT, fingerprint)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        private const val CHANNEL_ID = "foldledger_pending"
        private const val NOTIF_ID = 1002

        fun cancel(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.cancel(NOTIF_ID)
        }
    }
}
