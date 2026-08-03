package com.foldledger.capture.fgs

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.foldledger.capture.a11y.LedgerAccessibilityService
import com.foldledger.capture.nls.LedgerNotificationListener
import com.foldledger.capture.overlay.ConfirmOverlayController
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class CaptureKeepAliveService : Service() {

    @Inject lateinit var overlay: ConfirmOverlayController

    override fun onCreate() {
        super.onCreate()
        createChannel()
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                NOTIF_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIF_ID, notification)
        }
        overlay.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID, buildNotification())
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        val nlsOk = LedgerNotificationListener.isAccessGranted(this) && LedgerNotificationListener.connected
        val a11yOk = LedgerAccessibilityService.isEnabled(this)
        val status = buildString {
            append(if (nlsOk) "通知监听正常" else "通知监听未就绪")
            append(" · ")
            append(if (a11yOk) "无障碍已开" else "无障碍未开")
        }
        val launch = packageManager.getLaunchIntentForPackage(packageName)
        val pi = PendingIntent.getActivity(
            this,
            0,
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("FoldLedger 自动记账")
            .setContentText(status)
            .setSmallIcon(android.R.drawable.ic_menu_save)
            .setOngoing(true)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "自动记账保活", NotificationManager.IMPORTANCE_LOW),
        )
    }

    companion object {
        private const val CHANNEL_ID = "foldledger_capture"
        private const val NOTIF_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, CaptureKeepAliveService::class.java)
            context.startForegroundService(intent)
        }
    }
}
