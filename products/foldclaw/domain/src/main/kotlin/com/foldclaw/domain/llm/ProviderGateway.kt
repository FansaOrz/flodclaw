package com.foldclaw.domain.llm

import com.foldclaw.domain.model.DomainError
import com.foldclaw.domain.model.NormalizedMessage
import com.foldclaw.domain.model.ProviderCapabilities
import com.foldclaw.domain.model.Result
import com.foldclaw.domain.model.StreamEvent
import com.foldclaw.domain.model.ToolDescriptor
import kotlinx.coroutines.flow.Flow

/**
 * LLM Provider 网关。Alpha 期只有一个真实实现 + 一个 Fake。
 *
 * 职责边界（来自审查报告）：只做推理与流式事件归一化，不做安全策略。
 * 安全策略由 PolicyEngine 在调用方执行。
 */
interface ProviderGateway {
    val providerId: String
    val capabilities: ProviderCapabilities

    /**
     * 流式对话。返回归一化事件流。
     * [messages] 为对话历史；[tools] 为可用工具描述。
     */
    fun stream(
        messages: List<NormalizedMessage>,
        tools: List<ToolDescriptor>,
    ): Flow<StreamEvent>

    /**
     * 取消当前请求（尽力而为）。
     */
    fun cancel()

    /** 测试连通性。 */
    suspend fun ping(): Result<Unit>
}

/**
 * Provider 配置。BYOK 场景下从 DataStore 读取。
 */
data class ProviderConfig(
    val providerId: String,
    val displayName: String,
    val baseUrl: String,
    val apiKeyRef: String, // 不存明文，只存引用；运行时从 KeyVault 解密
    val modelId: String,
)
