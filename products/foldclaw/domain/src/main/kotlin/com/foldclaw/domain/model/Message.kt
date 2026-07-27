package com.foldclaw.domain.model

import kotlinx.serialization.Serializable

/**
 * 归一化角色。内部统一表示，不绑定任何具体 Provider 的字段名。
 */
@Serializable
enum class Role { SYSTEM, USER, ASSISTANT, TOOL }

/**
 * 归一化消息。ProviderGateway 实现负责把它翻译成各家请求体。
 *
 * 注意：[content] 标记可信；来自屏幕/通知/工具的不可信内容必须通过 [UntrustedContent] 包装，
 * 策略层据此拒绝其扩大能力信封。
 */
@Serializable
data class NormalizedMessage(
    val role: Role,
    val content: String,
    val isUntrusted: Boolean = false,
    /** 工具结果回传时携带的 tool call id。 */
    val toolCallId: String? = null,
    /** assistant 消息携带的对工具调用。 */
    val toolCalls: List<ToolCall> = emptyList(),
)

@Serializable
data class ToolCall(
    val id: String,
    val name: String,
    /** 增量 JSON 字符串；消费端需累积后解析。非流式场景这里直接是完整 JSON。 */
    val argumentsJson: String,
)

@Serializable
data class ToolDescriptor(
    val name: String,
    val description: String,
    /** 该工具期望的参数 JSON Schema（字符串形式）。 */
    val parametersJsonSchema: String,
)

/**
 * Provider 能力。决定 Alpha 期是否允许使用视觉、流式 tool call 等。
 */
data class ProviderCapabilities(
    val supportsStreaming: Boolean,
    val supportsToolCalling: Boolean,
    val supportsVision: Boolean,
    val supportsCancel: Boolean,
    val maxContextTokens: Int,
    val maxImageBytes: Int,
)

/**
 * 一次推理的用量与费用统计，写 Timeline。
 */
@Serializable
data class ProviderUsage(
    val provider: String,
    val model: String,
    val inputTokens: Int,
    val outputTokens: Int,
    val cachedTokens: Int = 0,
    val estimatedCostUsd: Double = 0.0,
)
