package com.foldclaw.data.llm

import com.foldclaw.domain.llm.ProviderGateway
import com.foldclaw.domain.model.DomainError
import com.foldclaw.domain.model.ErrorKind
import com.foldclaw.domain.model.NormalizedMessage
import com.foldclaw.domain.model.ProviderCapabilities
import com.foldclaw.domain.model.ProviderUsage
import com.foldclaw.domain.model.Result
import com.foldclaw.domain.model.Role
import com.foldclaw.domain.model.StreamEvent
import com.foldclaw.domain.model.ToolCall
import com.foldclaw.domain.model.ToolDescriptor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Fake LLM。Phase -1 / Walking Skeleton 用它跑通 Agent 闭环，不依赖真实 Provider。
 *
 * 它按规则把用户文字指令解析成工具调用：
 *   - 含「日程/日历」 → create_calendar_event
 *   - 含「闹钟」 → set_alarm
 *   - 含「打开」 → open_app
 *   - 含「天气」 → get_weather
 *   - 否则 → 文本回复
 *
 * 这是确定性「假大脑」，用来验证 Orchestrator/PolicyEngine/Verifier/Timeline，
 * 而不是验证模型能力。真实模型接入后用 Fake 做 E2E 回归。
 */
class FakeProviderGateway(
    private val latencyMs: Long = 50L,
) : ProviderGateway {
    // 由 AppModule / ProviderRouter 注入，无需 @Inject 构造（保持可单测）

    override val providerId: String = "fake"
    override val capabilities: ProviderCapabilities = ProviderCapabilities(
        supportsStreaming = true,
        supportsToolCalling = true,
        supportsVision = false,
        supportsCancel = true,
        maxContextTokens = 8192,
        maxImageBytes = 0,
    )

    @Volatile
    private var cancelled = false

    override fun stream(
        messages: List<NormalizedMessage>,
        tools: List<ToolDescriptor>,
    ): Flow<StreamEvent> = flow {
        // 上一轮工具已执行完毕：结束循环，避免按同一条用户指令反复 set_alarm
        if (messages.lastOrNull()?.role == Role.TOOL) {
            val summary = messages.last().content
            emit(StreamEvent.TextDelta(summary.ifBlank { "已处理完毕。" }))
            emit(StreamEvent.MessageCompleted(usage = ProviderUsage("fake", "fake-1", 4, 8)))
            return@flow
        }

        // 取最后一条可信 user 指令（忽略屏幕观察注入）
        val userMsg = messages.lastOrNull { it.role == Role.USER && !it.isUntrusted }
        val text = userMsg?.content.orEmpty()
        val hasUiTree = messages.any {
            it.role == Role.TOOL && it.content.contains("Package:")
        }
        val toolCall = planToolCall(text, hasUiTree)
        if (toolCall != null) {
            emit(StreamEvent.ToolCallDelta(0, toolCall.id, toolCall.name, ""))
            emit(StreamEvent.ToolCallCompleted(0, toolCall.id, toolCall.name, toolCall.argumentsJson))
        } else {
            // 无法解析：回退到文本
            emit(StreamEvent.TextDelta("我没理解这条指令。当前支持：建日程、设闹钟、打开应用、查天气、读界面/点击/输入。"))
        }
        emit(StreamEvent.MessageCompleted(usage = ProviderUsage("fake", "fake-1", 10, 5)))
    }

    override fun cancel() { cancelled = true }

    override suspend fun ping(): Result<Unit> = Result.Success(Unit)

    /**
     * 简单确定性解析。真实模型会做 NLU，这里只做关键词匹配。
     */
    private fun planToolCall(text: String, hasUiTree: Boolean): ToolCall? {
        val callId = "call_${System.currentTimeMillis()}"
        return when {
            text.contains("静音") || text.contains("勿扰") ||
                (text.contains("振动") || text.contains("震动")) && text.contains("设") -> {
                val mode = when {
                    text.contains("响铃") || text.contains("恢复") -> "normal"
                    text.contains("振动") || text.contains("震动") -> "vibrate"
                    else -> "silent"
                }
                ToolCall(callId, "set_ringer_mode", """{"mode":"$mode"}""")
            }
            text.contains("响铃") && (text.contains("恢复") || text.contains("设为") || text.contains("设置成")) -> {
                ToolCall(callId, "set_ringer_mode", """{"mode":"normal"}""")
            }
            text.contains("clash", ignoreCase = true) ||
                (text.contains("代理") && (text.contains("关闭") || text.contains("关掉") || text.contains("打开"))) -> {
                if (!hasUiTree) {
                    ToolCall(callId, "open_app", """{"appName":"Clash","packageName":null}""")
                } else if (text.contains("关闭") || text.contains("关掉") || text.contains("停止")) {
                    // 由真实模型/后续轮用 tap；Fake 先读树
                    ToolCall(callId, "get_ui_tree", "{}")
                } else {
                    ToolCall(callId, "get_ui_tree", "{}")
                }
            }
            text.contains("字体") || text.contains("显示大小") -> {
                if (!hasUiTree) {
                    ToolCall(callId, "open_settings_page", """{"page":"font"}""")
                } else {
                    ToolCall(callId, "get_ui_tree", "{}")
                }
            }
            // 打开设置并继续操作：先 open_app，下一轮 get_ui_tree / tap
            (text.contains("设置") && (text.contains("打开") || text.contains("进入") || text.contains("搜索"))) &&
                !text.contains("闹钟") -> {
                if (!hasUiTree && !text.contains("界面")) {
                    if (text.contains("打开") || text.contains("进入")) {
                        ToolCall(
                            callId,
                            "open_app",
                            """{"appName":"设置","packageName":null}""",
                        )
                    } else {
                        ToolCall(callId, "get_ui_tree", "{}")
                    }
                } else {
                    ToolCall(callId, "get_ui_tree", "{}")
                }
            }
            text.contains("界面") || text.contains("UI树") || text.contains("屏幕上有什么") -> {
                ToolCall(callId, "get_ui_tree", "{}")
            }
            text.contains("天气") -> {
                val city = Regex("([\u4e00-\u9fa5]{2,8})的?天气").find(text)?.groupValues?.get(1)
                    ?: Regex("(北京|上海|广州|深圳|杭州|成都|武汉|西安|南京|重庆)").find(text)?.groupValues?.get(1)
                    ?: "北京"
                val dayOffset = if (text.contains("明天") || text.contains("明日")) 1 else 0
                val args = buildJsonObject(
                    "city" to jsonString(city),
                    "dayOffset" to dayOffset.toString(),
                )
                ToolCall(callId, "get_weather", args)
            }
            text.contains("打开") || text.contains("启动") -> {
                val appName = text
                    .replace(Regex("(帮我|请|一下|打开|启动|运行)"), "")
                    .trim()
                    .ifBlank { "设置" }
                val args = buildJsonObject(
                    "appName" to jsonString(appName),
                    "packageName" to "null",
                )
                ToolCall(callId, "open_app", args)
            }
            text.contains("日程") || text.contains("日历") || text.contains("会议") -> {
                val title = extractTitle(text) ?: "新建日程"
                val (hour, minute) = extractTime(text)
                val startMs = nextOccurrenceMs(hour, minute)
                val endMs = startMs + 60 * 60 * 1000
                val args = buildJsonObject(
                    "title" to jsonString(title),
                    "startEpochMs" to startMs.toString(),
                    "endEpochMs" to endMs.toString(),
                    "location" to "null",
                    "allDay" to "false",
                    "description" to "null",
                )
                ToolCall(callId, "create_calendar_event", args)
            }
            text.contains("闹钟") || text.contains("叫醒") -> {
                val (hour, minute) = extractTime(text)
                val label = extractTitle(text)
                val args = buildJsonObject(
                    "hour" to hour.toString(),
                    "minutes" to minute.toString(),
                    "label" to (if (label != null) jsonString(label) else "null"),
                    "message" to "null",
                    "vibrate" to "true",
                    "skipUi" to "false",
                )
                ToolCall(callId, "set_alarm", args)
            }
            else -> null
        }
    }

    private fun extractTitle(text: String): String? {
        // 去掉关键词后取剩余文本作为标题
        val cleaned = text
            .replace(Regex("(帮我|创建|新建|设置|设|一个|明天|今天|下午|上午|早上|晚上|[0-9]+点|[0-9]+分|半)"), "")
            .replace(Regex("(日程|日历|闹钟|会议|叫醒)"), "")
            .trim()
        return cleaned.ifBlank { null }
    }

    private fun extractTime(text: String): Pair<Int, Int> {
        val hourMatch = Regex("(\\d{1,2})点").find(text)
        val minMatch = Regex("(\\d{1,2})分").find(text)
        val isAfternoon = text.contains("下午") || text.contains("晚上")
        var hour = hourMatch?.groupValues?.get(1)?.toIntOrNull() ?: 9
        val minute = minMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
        if (isAfternoon && hour < 12) hour += 12
        return hour to minute
    }

    private fun nextOccurrenceMs(hour: Int, minute: Int): Long {
        val now = java.util.Calendar.getInstance()
        val target = now.clone() as java.util.Calendar
        target.set(java.util.Calendar.HOUR_OF_DAY, hour)
        target.set(java.util.Calendar.MINUTE, minute)
        target.set(java.util.Calendar.SECOND, 0)
        if (target.timeInMillis <= now.timeInMillis) {
            target.add(java.util.Calendar.DAY_OF_MONTH, 1)
        }
        return target.timeInMillis
    }

    private fun buildJsonObject(vararg pairs: Pair<String, String>): String =
        "{${pairs.joinToString(",") { (k, v) -> "\"$k\":$v" }}}"

    private fun jsonString(s: String): String = "\"${s.replace("\"", "\\\"")}\""
}
