package com.foldclaw.domain.tool

import com.foldclaw.domain.model.ToolDescriptor

/**
 * 日历建日程工具。走 ACTION_INSERT，纯 Intent，不需 Accessibility。
 * 第一类 North Star 任务：用户文字 → 解析参数 → 建日程草稿。
 *
 * 风险 = REVERSIBLE_SIDE_EFFECT：只创建草稿/预填，用户在日历 App 内确认保存。
 */
object CalendarInsertTool {
    const val NAME = "create_calendar_event"

    val descriptor = ToolDescriptor(
        name = NAME,
        description = "在日历应用中打开新建日程页并预填标题、地点、开始/结束时间。用户需在日历应用内确认保存。不直接保存。",
        parametersJsonSchema = """
{
  "type": "object",
  "properties": {
    "title": { "type": "string", "description": "日程标题" },
    "location": { "type": "string", "description": "地点，可选" },
    "startEpochMs": { "type": "integer", "description": "开始时间，Unix 毫秒" },
    "endEpochMs": { "type": "integer", "description": "结束时间，Unix 毫秒，可选" },
    "allDay": { "type": "boolean", "description": "是否全天事件，默认 false" },
    "description": { "type": "string", "description": "备注，可选" }
  },
  "required": ["title", "startEpochMs"]
}
        """.trimIndent(),
    )

    data class Args(
        val title: String,
        val location: String?,
        val startEpochMs: Long,
        val endEpochMs: Long?,
        val allDay: Boolean = false,
        val description: String?,
    )
}

/**
 * 闹钟工具。走 AlarmClock.ACTION_SET_ALARM，纯 Intent。
 * 第二类 North Star 任务。
 *
 * 注意：ACTION_SET_ALARM 在多数设备上会「直接创建」闹钟，因此风险标记为
 * IRREVERSIBLE_SIDE_EFFECT —— Alpha 期策略层要求用户确认。
 */
object AlarmSetTool {
    const val NAME = "set_alarm"

    val descriptor = ToolDescriptor(
        name = NAME,
        description = "设置系统闹钟。会直接在时钟应用创建闹钟，需用户确认。",
        parametersJsonSchema = """
{
  "type": "object",
  "properties": {
    "hour": { "type": "integer", "description": "小时 0-23" },
    "minutes": { "type": "integer", "description": "分钟 0-59" },
    "label": { "type": "string", "description": "闹钟标签，可选" },
    "message": { "type": "string", "description": "闹钟消息，可选" },
    "vibrate": { "type": "boolean", "description": "是否振动，默认 true" },
    "skipUi": { "type": "boolean", "description": "是否跳过 UI 直接设置，默认 false（显示确认）" }
  },
  "required": ["hour", "minutes"]
}
        """.trimIndent(),
    )

    data class Args(
        val hour: Int,
        val minutes: Int,
        val label: String?,
        val message: String?,
        val vibrate: Boolean = true,
        val skipUi: Boolean = false,
    )
}
