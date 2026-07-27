package com.foldclaw.device.status

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.BatteryManager
import android.os.PowerManager
import com.foldclaw.device.a11y.FoldClawAccessibilityService
import com.foldclaw.domain.device.DeviceController
import com.foldclaw.domain.tool.DeviceStatusBackend
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceStatusBackendImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val device: DeviceController,
) : DeviceStatusBackend {

    override fun statusSummary(): String {
        val battery = batteryLine()
        val ringer = ringerLine()
        val power = powerLine()
        val fg = foregroundLine()
        val a11y = if (device.isAvailable()) "无障碍：已连接" else "无障碍：未连接"
        return listOf(battery, ringer, power, fg, a11y).joinToString("\n")
    }

    private fun batteryLine(): String {
        val bm = context.getSystemService(BatteryManager::class.java)
        val pct = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        return "电量：${if (pct >= 0) "$pct%" else "未知"}${if (charging) "（充电中）" else ""}"
    }

    private fun ringerLine(): String {
        val am = context.getSystemService(AudioManager::class.java) ?: return "铃声：未知"
        val mode = when (am.ringerMode) {
            AudioManager.RINGER_MODE_SILENT -> "静音"
            AudioManager.RINGER_MODE_VIBRATE -> "振动"
            AudioManager.RINGER_MODE_NORMAL -> "响铃"
            else -> "未知"
        }
        val nm = context.getSystemService(NotificationManager::class.java)
        val dnd = if (nm?.isNotificationPolicyAccessGranted == true) {
            when (nm.currentInterruptionFilter) {
                NotificationManager.INTERRUPTION_FILTER_ALL -> "勿扰关"
                NotificationManager.INTERRUPTION_FILTER_NONE -> "勿扰：全静"
                NotificationManager.INTERRUPTION_FILTER_PRIORITY -> "勿扰：仅优先"
                NotificationManager.INTERRUPTION_FILTER_ALARMS -> "勿扰：仅闹钟"
                else -> "勿扰：其他"
            }
        } else {
            "勿扰权限未开"
        }
        return "铃声：$mode · $dnd"
    }

    private fun powerLine(): String {
        val pm = context.getSystemService(PowerManager::class.java)
        val interactive = pm?.isInteractive == true
        return "屏幕：${if (interactive) "亮" else "灭/息屏"}"
    }

    private fun foregroundLine(): String {
        val root = FoldClawAccessibilityService.instance?.activeRoot()
        val pkg = root?.packageName?.toString()
        root?.recycle()
        return when {
            !pkg.isNullOrBlank() -> "前台包名：$pkg"
            device.isAvailable() -> "前台包名：无障碍已连但窗口为空"
            else -> "前台包名：无障碍未开，无法可靠读取"
        }
    }
}
