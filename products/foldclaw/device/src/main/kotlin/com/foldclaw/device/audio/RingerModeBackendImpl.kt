package com.foldclaw.device.audio

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.foldclaw.domain.model.DomainError
import com.foldclaw.domain.model.ErrorKind
import com.foldclaw.domain.model.Result
import com.foldclaw.domain.tool.RingerModeBackend
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RingerModeBackendImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : RingerModeBackend {

    override fun setMode(mode: String): Result<String> {
        val key = mode.trim().lowercase()
        val target = when (key) {
            "silent", "mute", "静音", "无声" -> AudioManager.RINGER_MODE_SILENT
            "vibrate", "vibration", "振动", "震动" -> AudioManager.RINGER_MODE_VIBRATE
            "normal", "ring", "响铃", "铃声" -> AudioManager.RINGER_MODE_NORMAL
            else -> return Result.Failure(
                DomainError(ErrorKind.PolicyDenied, "未知模式 $mode，可用 silent/vibrate/normal"),
            )
        }

        val am = context.getSystemService(AudioManager::class.java)
            ?: return Result.Failure(DomainError(ErrorKind.ActionFailed, "无法访问音频服务"))

        // 部分机型切到 SILENT 需要勿扰权限；先探测再写
        if (target == AudioManager.RINGER_MODE_SILENT && !hasPolicyAccess()) {
            // 先尝试；失败再降级到振动并提示开权限
            val silentOk = runCatching {
                am.ringerMode = AudioManager.RINGER_MODE_SILENT
                am.ringerMode == AudioManager.RINGER_MODE_SILENT
            }.getOrDefault(false)
            if (!silentOk) {
                am.ringerMode = AudioManager.RINGER_MODE_VIBRATE
                openNotificationPolicySettings()
                Log.w(TAG, "silent needs DND access; fell back to vibrate")
                return Result.Success(
                    "系统限制完整静音，已先设为振动；请在弹出的「勿扰权限」里允许 FoldClaw 后再说一次「静音」。",
                )
            }
        } else {
            try {
                am.ringerMode = target
            } catch (e: SecurityException) {
                Log.e(TAG, "setMode denied mode=$key", e)
                if (target == AudioManager.RINGER_MODE_SILENT) {
                    am.ringerMode = AudioManager.RINGER_MODE_VIBRATE
                    openNotificationPolicySettings()
                    return Result.Success(
                        "无完整静音权限，已设为振动；请授予勿扰权限后重试「静音」。",
                    )
                }
                return Result.Failure(DomainError(ErrorKind.ActionFailed, "设置失败: ${e.message}"))
            }
        }

        val actual = when (am.ringerMode) {
            AudioManager.RINGER_MODE_SILENT -> "静音"
            AudioManager.RINGER_MODE_VIBRATE -> "振动"
            else -> "响铃"
        }
        Log.i(TAG, "setMode ok requested=$key actual=$actual")
        return Result.Success("已将铃声模式设为「$actual」。")
    }

    private fun hasPolicyAccess(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val nm = context.getSystemService(NotificationManager::class.java) ?: return false
        return nm.isNotificationPolicyAccessGranted
    }

    private fun openNotificationPolicySettings() {
        runCatching {
            val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    companion object {
        private const val TAG = "FoldClaw/Ringer"
    }
}
