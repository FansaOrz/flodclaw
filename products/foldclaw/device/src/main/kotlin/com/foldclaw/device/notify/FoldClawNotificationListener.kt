package com.foldclaw.device.notify

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.foldclaw.domain.tool.NotificationSummaryBackend
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 只读通知摘要缓存。不点击、不清除、不回复。
 */
class FoldClawNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        activeInstance = this
        refreshActive()
    }

    override fun onListenerDisconnected() {
        if (activeInstance === this) activeInstance = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        cache.put(sbn.key, toSummary(sbn))
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn ?: return
        cache.remove(sbn.key)
    }

    private fun refreshActive() {
        runCatching {
            activeNotifications?.forEach { sbn ->
                cache[sbn.key] = toSummary(sbn)
            }
        }
    }

    companion object {
        @Volatile
        var activeInstance: FoldClawNotificationListener? = null

        private val cache = ConcurrentHashMap<String, NotificationSummary>()

        fun snapshots(limit: Int): List<NotificationSummary> =
            cache.values
                .sortedByDescending { it.postTime }
                .take(limit.coerceIn(1, 40))

        fun toSummary(sbn: StatusBarNotification): NotificationSummary {
            val extras = sbn.notification?.extras
            val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
            val text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
            val big = extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
            val body = big.ifBlank { text }.take(120)
            return NotificationSummary(
                key = sbn.key,
                packageName = sbn.packageName.orEmpty(),
                title = title.take(80),
                text = body,
                postTime = sbn.postTime,
            )
        }
    }
}

data class NotificationSummary(
    val key: String,
    val packageName: String,
    val title: String,
    val text: String,
    val postTime: Long,
)

@Singleton
class NotificationSummaryBackendImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : NotificationSummaryBackend {

    override fun isAccessGranted(): Boolean {
        val flat = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners",
        ).orEmpty()
        val cn = ComponentName(context, FoldClawNotificationListener::class.java)
        return flat.split(':').any {
            ComponentName.unflattenFromString(it)?.flattenToString() == cn.flattenToString() ||
                it.contains(context.packageName)
        }
    }

    override fun recentSummaries(limit: Int): String {
        val items = FoldClawNotificationListener.snapshots(limit)
        if (items.isEmpty()) {
            return if (FoldClawNotificationListener.activeInstance == null) {
                "通知监听已授权但尚未连接，请关掉再打开通知使用权，或重启 App。"
            } else {
                "当前没有可见通知。"
            }
        }
        return items.joinToString("\n") { n ->
            val title = n.title.ifBlank { "(无标题)" }
            val text = n.text.ifBlank { "(无正文)" }
            "- [${n.packageName}] $title · $text"
        }
    }
}
