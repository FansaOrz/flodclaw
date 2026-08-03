package com.foldledger.capture.nls

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.foldledger.capture.pipeline.CapturePipeline
import com.foldledger.data.parse.PaymentNotificationParser
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class LedgerNotificationListener : NotificationListenerService() {

    @Inject lateinit var parser: PaymentNotificationParser
    @Inject lateinit var pipeline: CapturePipeline

    override fun onListenerConnected() {
        connected = true
    }

    override fun onListenerDisconnected() {
        connected = false
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        val n = sbn.notification ?: return
        val extras = n.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val big = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
        val sub = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString().orEmpty()
        val summary = extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)?.toString().orEmpty()
        val info = extras.getCharSequence(Notification.EXTRA_INFO_TEXT)?.toString().orEmpty()
        val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            ?.mapNotNull { it?.toString() }
            ?.joinToString("\n")
            .orEmpty()
        // MessagingStyle / 部分支付通知把金额塞进 messages
        val messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
            ?.mapNotNull { msg ->
                runCatching {
                    val bundle = msg as? android.os.Bundle ?: return@mapNotNull null
                    bundle.getCharSequence("text")?.toString()
                }.getOrNull()
            }
            ?.joinToString("\n")
            .orEmpty()
        val body = listOf(big, text, lines, messages, sub, summary, info)
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString("\n")
        if (title.isBlank() && body.isBlank()) return
        val parsed = parser.parse(sbn.packageName.orEmpty(), title, body, sbn.postTime) ?: return
        pipeline.submit(parsed)
    }

    companion object {
        @Volatile
        var connected: Boolean = false

        fun isAccessGranted(context: Context): Boolean {
            val flat = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners",
            ).orEmpty()
            val cn = ComponentName(context, LedgerNotificationListener::class.java)
            return flat.split(':').any {
                ComponentName.unflattenFromString(it)?.flattenToString() == cn.flattenToString() ||
                    it.contains(context.packageName)
            }
        }

        fun openSettings(context: Context) {
            context.startActivity(
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}
