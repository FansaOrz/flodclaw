package com.foldclaw.data.llm

import com.foldclaw.data.keystore.KeyVault
import com.foldclaw.data.prefs.LlmProviderDefaults
import com.foldclaw.data.prefs.ProviderSettingsStore
import com.foldclaw.domain.llm.ProviderGateway
import com.foldclaw.domain.model.DomainError
import com.foldclaw.domain.model.ErrorKind
import com.foldclaw.domain.model.NormalizedMessage
import com.foldclaw.domain.model.ProviderCapabilities
import com.foldclaw.domain.model.Result
import com.foldclaw.domain.model.StreamEvent
import com.foldclaw.domain.model.ToolDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 按设置在 Fake / OpenAI-compatible 之间路由。
 * 「测试连接」永远走真实 HTTP，绝不回落到 Fake。
 */
@Singleton
class ProviderRouter @Inject constructor(
    private val fake: FakeProviderGateway,
    private val settings: ProviderSettingsStore,
    private val keyVault: KeyVault,
    private val http: OkHttpClient,
) : ProviderGateway {

    @Volatile
    private var active: ProviderGateway = fake

    override val providerId: String
        get() = active.providerId

    override val capabilities: ProviderCapabilities
        get() = active.capabilities

    override fun stream(
        messages: List<NormalizedMessage>,
        tools: List<ToolDescriptor>,
    ): Flow<StreamEvent> = flow {
        val delegate = resolveForChat()
        active = delegate
        delegate.stream(messages, tools).collect { emit(it) }
    }

    override fun cancel() {
        active.cancel()
    }

    /**
     * 聊天用 ping：仅在真实模式下探测；未开真实 API 时明确失败，避免 Fake 假成功。
     */
    override suspend fun ping(): Result<Unit> {
        val cfg = settings.current()
        if (!cfg.useRealApi) {
            return Result.Failure(
                DomainError(ErrorKind.ProviderAuth, "未启用真实 API，请先在设置中打开并填写 Key"),
            )
        }
        val key = keyVault.getApiKey(cfg.providerId)
        if (key.isNullOrBlank()) {
            return Result.Failure(DomainError(ErrorKind.ProviderAuth, "未配置 API Key"))
        }
        return verifyRealConnection(cfg.baseUrl, key)
    }

    /**
     * 设置页「测试连接」专用：用表单里的参数直接请求真实接口。
     * 不依赖 Fake，不读「是否启用真实 API」开关。
     */
    suspend fun verifyRealConnection(
        baseUrl: String,
        apiKey: String,
        modelId: String = "",
    ): Result<Unit> {
        val urlBase = baseUrl.trim().trimEnd('/')
        val key = apiKey.trim()
        val model = modelId.trim().ifBlank { LlmProviderDefaults.DEFAULT_MODEL }
        if (urlBase.isBlank()) {
            return Result.Failure(DomainError(ErrorKind.ProviderAuth, "Base URL 不能为空"))
        }
        if (key.isBlank()) {
            return Result.Failure(DomainError(ErrorKind.ProviderAuth, "请先填写 API Key"))
        }
        // POST /chat/completions：百炼等兼容网关对 /models 支持不一致，用最小 completion 验 Key+模型+域名
        return withContext(Dispatchers.IO) {
            try {
                val payload = buildJsonObject {
                    put("model", model)
                    put(
                        "messages",
                        buildJsonArray {
                            add(
                                buildJsonObject {
                                    put("role", "user")
                                    put("content", "ping")
                                },
                            )
                        },
                    )
                    put("max_tokens", 1)
                }.toString()
                val request = Request.Builder()
                    .url("$urlBase/chat/completions")
                    .addHeader("Authorization", "Bearer $key")
                    .addHeader("Content-Type", "application/json")
                    .post(payload.toRequestBody(JSON_MEDIA))
                    .build()
                http.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    when (response.code) {
                        in 200..299 -> Result.Success(Unit)
                        401, 403 -> Result.Failure(
                            DomainError(ErrorKind.ProviderAuth, "鉴权失败（${response.code}），请检查 API Key"),
                        )
                        else -> Result.Failure(
                            DomainError(
                                ErrorKind.ProviderUnavailable,
                                "HTTP ${response.code}: ${body.take(200).ifBlank { response.message }}",
                            ),
                        )
                    }
                }
            } catch (e: Exception) {
                Result.Failure(
                    DomainError(ErrorKind.ProviderUnavailable, "无法连接: ${e.message ?: e.javaClass.simpleName}"),
                )
            }
        }
    }

    private companion object {
        val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }

    private suspend fun resolveForChat(): ProviderGateway {
        val cfg = settings.current()
        if (!cfg.useRealApi) return fake
        val key = keyVault.getApiKey(cfg.providerId)
        if (key.isNullOrBlank()) {
            return MissingKeyGateway()
        }
        return OpenAiCompatibleGateway(
            baseUrl = cfg.baseUrl,
            apiKey = key,
            modelId = cfg.modelId,
            http = http,
        )
    }
}

/** 已开真实 API 但缺 Key：stream/ping 都失败，绝不走 Fake。 */
private class MissingKeyGateway : ProviderGateway {
    override val providerId: String = "openai_compatible"
    override val capabilities: ProviderCapabilities = ProviderCapabilities(
        supportsStreaming = false,
        supportsToolCalling = true,
        supportsVision = false,
        supportsCancel = false,
        maxContextTokens = 0,
        maxImageBytes = 0,
    )

    override fun stream(
        messages: List<NormalizedMessage>,
        tools: List<ToolDescriptor>,
    ): Flow<StreamEvent> = flow {
        emit(
            StreamEvent.Error(
                DomainError(ErrorKind.ProviderAuth, "已启用真实 API，但未配置 API Key"),
            ),
        )
    }

    override fun cancel() = Unit

    override suspend fun ping(): Result<Unit> =
        Result.Failure(DomainError(ErrorKind.ProviderAuth, "未配置 API Key"))
}
