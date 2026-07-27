package com.foldclaw.domain.model

/**
 * 归一化流式事件。三家 Provider 的 SSE 各自映射到这里：
 * 文本增量、工具调用增量（分片 JSON）、完成、错误。
 *
 * 设计要点：工具参数一律「累积字符串 + 完成后统一 parse」，不假设中间分片合法。
 */
sealed class StreamEvent {
    /** 文本增量。 */
    data class TextDelta(val text: String) : StreamEvent()

    /**
     * 工具调用参数增量。同一 index 的 delta 需拼接，直到 [ToolCallCompleted]。
     */
    data class ToolCallDelta(val index: Int, val id: String?, val name: String?, val partialArgumentsJson: String) : StreamEvent()

    /** 一个工具调用参数已完整。argumentsJson 可 parse。 */
    data class ToolCallCompleted(val index: Int, val id: String, val name: String, val argumentsJson: String) : StreamEvent()

    /** 模型一轮回复结束。 */
    data class MessageCompleted(val usage: ProviderUsage?) : StreamEvent()

    /** Provider 错误。 */
    data class Error(val error: DomainError) : StreamEvent()
}
