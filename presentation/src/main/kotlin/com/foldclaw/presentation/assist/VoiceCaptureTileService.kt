package com.foldclaw.presentation.assist

import android.app.PendingIntent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast

/**
 * 快捷设置「FoldClaw 开麦」：一点即唤起 App 并开始录音。
 */
class VoiceCaptureTileService : TileService() {

    override fun onStartListening() {
        qsTile?.apply {
            label = "FoldClaw 开麦"
            contentDescription = "打开 FoldClaw 并开始语音输入"
            state = Tile.STATE_INACTIVE
            updateTile()
        }
    }

    override fun onClick() {
        val intent = AssistLaunch.voiceCaptureIntent(this)
        val launch = {
            try {
                if (Build.VERSION.SDK_INT >= 34) {
                    val pi = PendingIntent.getActivity(
                        this,
                        REQUEST_VOICE,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    )
                    startActivityAndCollapse(pi)
                } else {
                    @Suppress("DEPRECATION")
                    startActivityAndCollapse(intent)
                }
            } catch (e: Exception) {
                Toast.makeText(this, "无法打开 FoldClaw：${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
        if (isLocked) {
            unlockAndRun(launch)
        } else {
            launch()
        }
    }

    companion object {
        private const val REQUEST_VOICE = 7101
    }
}
