package com.foldpods.presentation.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.foldpods.domain.BatterySnapshot
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BatteryNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val nm = context.getSystemService(NotificationManager::class.java)

    init {
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_SCAN, "FoldPods 扫描", NotificationManager.IMPORTANCE_LOW),
            )
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_BATTERY, "FoldPods 电量", NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
    }

    fun scanServiceNotification(contentIntent: PendingIntent): Notification =
        NotificationCompat.Builder(context, CHANNEL_SCAN)
            .setContentTitle("FoldPods 正在扫描")
            .setContentText("监听附近 AirPods 开盖广播")
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .build()

    fun publishBattery(snapshot: BatterySnapshot?, launchIntent: Intent) {
        if (snapshot == null) {
            nm.cancel(ID_BATTERY)
            return
        }
        val pi = PendingIntent.getActivity(
            context,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val text = "L ${pct(snapshot.battery.leftPercent)} · R ${pct(snapshot.battery.rightPercent)} · Case ${pct(snapshot.battery.casePercent)}"
        val n = NotificationCompat.Builder(context, CHANNEL_BATTERY)
            .setContentTitle(snapshot.modelLabel)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentIntent(pi)
            .setOnlyAlertOnce(true)
            .build()
        nm.notify(ID_BATTERY, n)
    }

    private fun pct(v: Int?) = v?.let { "$it%" } ?: "—"

    companion object {
        const val CHANNEL_SCAN = "foldpods_scan"
        const val CHANNEL_BATTERY = "foldpods_battery"
        const val ID_SCAN = 4201
        const val ID_BATTERY = 4202
    }
}
