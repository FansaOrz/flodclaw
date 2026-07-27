package com.foldclaw.presentation.assist

import android.content.Context
import android.content.Intent
import com.foldclaw.presentation.MainActivity

/**
 * 数字助理 / 快捷磁贴等外部唤起入口约定。
 */
object AssistLaunch {
    const val EXTRA_AUTO_VOICE = "com.foldclaw.extra.AUTO_VOICE"
    /** 识别完成后自动下发任务（助理手势/磁贴场景）。 */
    const val EXTRA_AUTO_SEND = "com.foldclaw.extra.AUTO_SEND"

    fun isAssistStyleAction(action: String?): Boolean = when (action) {
        Intent.ACTION_ASSIST,
        Intent.ACTION_VOICE_COMMAND,
        "android.intent.action.VOICE_ASSIST",
        -> true
        else -> false
    }

    fun voiceCaptureIntent(context: Context): Intent =
        Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_ASSIST
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_AUTO_VOICE, true)
            putExtra(EXTRA_AUTO_SEND, true)
        }

    fun wantsAutoVoice(intent: Intent?): Boolean {
        intent ?: return false
        if (intent.getBooleanExtra(EXTRA_AUTO_VOICE, false)) return true
        return isAssistStyleAction(intent.action)
    }

    fun wantsAutoSend(intent: Intent?): Boolean {
        intent ?: return false
        if (intent.getBooleanExtra(EXTRA_AUTO_SEND, false)) return true
        return isAssistStyleAction(intent.action)
    }
}
