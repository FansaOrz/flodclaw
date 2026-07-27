package com.foldclaw.device.intent

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.util.Log
import com.foldclaw.domain.model.DomainError
import com.foldclaw.domain.model.ErrorKind
import com.foldclaw.domain.model.Result
import com.foldclaw.domain.tool.AlarmSetTool
import com.foldclaw.domain.tool.CalendarInsertTool

/**
 * Intent 执行器。第一类任务（日历/闹钟）走这里，纯 Intent 不需 Accessibility。
 *
 * 风险控制：闹钟 ACTION_SET_ALARM 会直接创建闹钟，因此调用方（Orchestrator）
 * 必须先经 PolicyEngine → ApprovalManager，确认后才调这里。
 */
class IntentExecutors(private val context: Context) {

    /**
     * 打开日历新建日程页并预填。不直接保存，用户在日历 App 内确认。
     */
    fun createCalendarEvent(args: CalendarInsertTool.Args): Result<Unit> {
        return try {
            Log.i(TAG, "createCalendarEvent: title=${args.title} start=${args.startEpochMs}")
            val intent = Intent(Intent.ACTION_INSERT).apply {
                data = CalendarContract.Events.CONTENT_URI
                putExtra(CalendarContract.Events.TITLE, args.title)
                putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, args.startEpochMs)
                if (args.endEpochMs != null) {
                    putExtra(CalendarContract.EXTRA_EVENT_END_TIME, args.endEpochMs)
                }
                if (args.location != null) {
                    putExtra(CalendarContract.Events.EVENT_LOCATION, args.location)
                }
                if (args.description != null) {
                    putExtra(CalendarContract.Events.DESCRIPTION, args.description)
                }
                putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, args.allDay)
                // Alpha 期需要用户在前台点击，因此 NEW_TASK
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Log.i(TAG, "createCalendarEvent: startActivity ok")
            Result.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "createCalendarEvent failed", e)
            Result.Failure(DomainError(ErrorKind.ActionFailed, "无法打开日历: ${e.message}"))
        }
    }

    /**
     * 设置系统闹钟。skipUi=false 时会显示确认界面。
     * 需要 com.android.alarm.permission.SET_ALARM 权限。
     */
    fun setAlarm(args: AlarmSetTool.Args): Result<Unit> {
        return try {
            Log.i(TAG, "setAlarm: ${args.hour}:${args.minutes} label=${args.label}")
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, args.hour)
                putExtra(AlarmClock.EXTRA_MINUTES, args.minutes)
                if (args.label != null) {
                    putExtra(AlarmClock.EXTRA_MESSAGE, args.label)
                }
                putExtra(AlarmClock.EXTRA_VIBRATE, args.vibrate)
                putExtra(AlarmClock.EXTRA_SKIP_UI, args.skipUi)
                // ACTION_SET_ALARM 必须前台启动
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Log.i(TAG, "setAlarm: startActivity ok")
            Result.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "setAlarm failed", e)
            Result.Failure(DomainError(ErrorKind.ActionFailed, "无法设置闹钟: ${e.message}"))
        }
    }

    companion object {
        private const val TAG = "FoldClaw/Intent"
    }
}
