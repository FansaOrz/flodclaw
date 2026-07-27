package com.foldclaw.domain.tool

import com.foldclaw.domain.model.ToolDescriptor

object RememberFactTool {
    const val NAME = "remember_fact"
    val descriptor = ToolDescriptor(
        name = NAME,
        description = "记住一条用户明确要求保存的个人偏好或事实（如城市、常用闹钟标签）。key 简短，value 为内容。",
        parametersJsonSchema = """
{"type":"object","properties":{"key":{"type":"string"},"value":{"type":"string"}},"required":["key","value"]}
        """.trimIndent(),
    )
}

object ForgetFactTool {
    const val NAME = "forget_fact"
    val descriptor = ToolDescriptor(
        name = NAME,
        description = "删除一条已保存的个人记忆。传 key 或 id。",
        parametersJsonSchema = """
{"type":"object","properties":{"key":{"type":"string"},"id":{"type":"integer"}}}
        """.trimIndent(),
    )
}

object ListMemoriesTool {
    const val NAME = "list_memories"
    val descriptor = ToolDescriptor(
        name = NAME,
        description = "列出当前已保存的个人记忆。",
        parametersJsonSchema = """{"type":"object","properties":{}}""",
    )
}

object GetDeviceStatusTool {
    const val NAME = "get_device_status"
    val descriptor = ToolDescriptor(
        name = NAME,
        description = "只读：电量、铃声模式、前台应用包名等设备状态摘要。",
        parametersJsonSchema = """{"type":"object","properties":{}}""",
    )
}

object GetNotificationsTool {
    const val NAME = "get_notifications"
    val descriptor = ToolDescriptor(
        name = NAME,
        description = "只读：最近通知标题/摘要（需用户开启通知使用权）。不点击、不清除通知。",
        parametersJsonSchema = """
{"type":"object","properties":{"limit":{"type":"integer","description":"最多返回条数，默认 12"}}}
        """.trimIndent(),
    )
}

interface DeviceStatusBackend {
    fun statusSummary(): String
}

interface NotificationSummaryBackend {
    fun recentSummaries(limit: Int): String
    fun isAccessGranted(): Boolean
}
