package com.foldclaw.device.intent

import android.content.Context
import com.foldclaw.agent.tools.IntentBackend
import com.foldclaw.domain.model.Result
import com.foldclaw.domain.tool.AlarmSetTool
import com.foldclaw.domain.tool.CalendarInsertTool
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * IntentBackend 的生产实现:委托给 IntentExecutors。
 * 把 agent 模块的抽象接到 device 模块的真实 Intent。
 */
@Singleton
class IntentBackendImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : IntentBackend {

    private val executors = IntentExecutors(context)

    override fun createCalendarEvent(args: CalendarInsertTool.Args): Result<Unit> =
        executors.createCalendarEvent(args)

    override fun setAlarm(args: AlarmSetTool.Args): Result<Unit> =
        executors.setAlarm(args)
}
