package com.foldclaw.device.fgs

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
import androidx.core.app.ServiceCompat
import com.foldclaw.device.di.AgentOrchestratorEntryPoint
import com.foldclaw.domain.security.Redactor
import dagger.hilt.android.EntryPointAccessors

/**
 * 用户显式启动任务后的前台服务：保持进程在切到目标 App 时不被轻易杀掉。
 * 不自动开跑；不承诺锁屏续跑。停止任务或任务结束时必须 stopSelf。
 */
class AgentRunForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            runCatching {
                EntryPointAccessors.fromApplication(
                    applicationContext,
                    AgentOrchestratorEntryPoint::class.java,
                ).orchestrator().cancel()
            }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val summary = Redactor.brief(intent?.getStringExtra(EXTRA_SUMMARY) ?: "任务执行中")
        ensureChannel()
        val notification = buildNotification(summary)
        if (Build.VERSION.SDK_INT >= 34) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        return START_NOT_STICKY
    }

    private fun ensureChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "FoldClaw 任务",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Agent 任务运行期间的状态通知"
        }
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(summary: String): Notification {
        val launch = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = PendingIntent.getActivity(
            this,
            0,
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = Intent(this, AgentRunForegroundService::class.java).setAction(ACTION_STOP)
        val stopPending = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("FoldClaw 正在执行")
            .setContentText(summary)
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentIntent(pending)
            .setOngoing(true)
            .addAction(0, "停止", stopPending)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "foldclaw_agent_run"
        const val NOTIFICATION_ID = 71001
        const val EXTRA_SUMMARY = "summary"
        const val ACTION_STOP = "com.foldclaw.action.STOP_AGENT_FGS"
        const val ACTION_UPDATE = "com.foldclaw.action.UPDATE_AGENT_FGS"

        fun start(context: Context, summary: String) {
            val intent = Intent(context, AgentRunForegroundService::class.java).apply {
                putExtra(EXTRA_SUMMARY, summary)
            }
            context.startForegroundService(intent)
        }

        fun update(context: Context, summary: String) {
            val intent = Intent(context, AgentRunForegroundService::class.java).apply {
                action = ACTION_UPDATE
                putExtra(EXTRA_SUMMARY, summary)
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AgentRunForegroundService::class.java))
        }
    }
}
