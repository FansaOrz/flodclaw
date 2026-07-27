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
import com.foldclaw.domain.model.ToolDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * OpenAI 兼容 Chat Completions（非流式，完成后拆成归一化 StreamEvent）。
 * 支持自定义 baseUrl，兼容国内 OpenAI-compatible 网关。
 */
class OpenAiCompatibleGateway(
    private val baseUrl: String,
    private val apiKey: String,
    private val modelId: String,
    private val http: OkHttpClient = defaultClient(),
) : ProviderGateway {

    override val providerId: String = "openai_compatible"
    override val capabilities: ProviderCapabilities = ProviderCapabilities(
        supportsStreaming = false,
        supportsToolCalling = true,
        supportsVision = false,
        supportsCancel = true,
        maxContextTokens = 128_000,
        maxImageBytes = 0,
    )

    private val callRef = AtomicReference<okhttp3.Call?>(null)
    private val json = Json { ignoreUnknownKeys = true }

    override fun stream(
        messages: List<NormalizedMessage>,
        tools: List<ToolDescriptor>,
    ): Flow<StreamEvent> = flow {
        val body = buildJsonObject {
            put("model", modelId)
            put("messages", messagesToJson(messages))
            if (tools.isNotEmpty()) {
                put("tools", toolsToJson(tools))
                put("tool_choice", "auto")
            }
        }.toString()

        val url = baseUrl.trimEnd('/') + "/chat/completions"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(body.toRequestBody(JSON))
            .build()

        val call = http.newCall(request)
        callRef.set(call)
        try {
            val response = call.execute()
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                emit(
                    StreamEvent.Error(
                        DomainError(
                            ErrorKind.ProviderUnavailable,
                            "HTTP ${response.code}: ${raw.take(300)}",
                        ),
                    ),
                )
                return@flow
            }
            val root = json.parseToJsonElement(raw).jsonObject
            val choice = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject
            val message = choice?.get("message")?.jsonObject
            if (message == null) {
                emit(StreamEvent.Error(DomainError(ErrorKind.ProviderInvalidResponse, "无 choices.message")))
                return@flow
            }
            val content = message["content"]?.jsonPrimitive?.contentOrNull
            val toolCalls = message["tool_calls"]?.jsonArray
            if (toolCalls != null && toolCalls.isNotEmpty()) {
                toolCalls.forEachIndexed { index, el ->
                    val tc = el.jsonObject
                    val id = tc["id"]?.jsonPrimitive?.contentOrNull ?: "call_$index"
                    val fn = tc["function"]?.jsonObject
                    val name = fn?.get("name")?.jsonPrimitive?.contentOrNull ?: return@forEachIndexed
                    val args = fn["arguments"]?.jsonPrimitive?.contentOrNull ?: "{}"
                    emit(StreamEvent.ToolCallCompleted(index, id, name, args))
                }
            } else if (!content.isNullOrBlank()) {
                emit(StreamEvent.TextDelta(content))
            } else {
                emit(StreamEvent.TextDelta("模型未返回可用内容。"))
            }
            val usageObj = root["usage"]?.jsonObject
            val usage = ProviderUsage(
                provider = providerId,
                model = modelId,
                inputTokens = usageObj?.get("prompt_tokens")?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0,
                outputTokens = usageObj?.get("completion_tokens")?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0,
            )
            emit(StreamEvent.MessageCompleted(usage))
        } catch (e: Exception) {
            if (call.isCanceled()) {
                emit(StreamEvent.Error(DomainError(ErrorKind.TaskCancelled, "请求已取消")))
            } else {
                emit(StreamEvent.Error(DomainError(ErrorKind.ProviderUnavailable, e.message ?: "网络错误")))
            }
        } finally {
            callRef.compareAndSet(call, null)
        }
    }.flowOn(Dispatchers.IO)

    override fun cancel() {
        callRef.getAndSet(null)?.cancel()
    }

    override suspend fun ping(): Result<Unit> {
        // 轻量：发一个极短 completion
        var ok = false
        var err: DomainError? = null
        stream(
            listOf(NormalizedMessage(Role.USER, "ping")),
            emptyList(),
        ).collect { ev ->
            when (ev) {
                is StreamEvent.MessageCompleted -> ok = true
                is StreamEvent.Error -> err = ev.error
                else -> Unit
            }
        }
        return if (ok) Result.Success(Unit)
        else Result.Failure(err ?: DomainError(ErrorKind.ProviderUnavailable, "ping 失败"))
    }

    private fun messagesToJson(messages: List<NormalizedMessage>): JsonArray = buildJsonArray {
        for (m in messages) {
            when (m.role) {
                Role.SYSTEM -> add(buildJsonObject {
                    put("role", "system")
                    put("content", m.content)
                })
                Role.USER -> add(buildJsonObject {
                    put("role", "user")
                    put("content", m.content)
                })
                Role.ASSISTANT -> {
                    if (m.toolCalls.isNotEmpty()) {
                        add(buildJsonObject {
                            put("role", "assistant")
                            put("content", m.content) // 可为空串
                            put(
                                "tool_calls",
                                buildJsonArray {
                                    for (tc in m.toolCalls) {
                                        add(
                                            buildJsonObject {
                                                put("id", tc.id)
                                                put("type", "function")
                                                put(
                                                    "function",
                                                    buildJsonObject {
                                                        put("name", tc.name)
                                                        put("arguments", tc.argumentsJson)
                                                    },
                                                )
                                            },
                                        )
                                    }
                                },
                            )
                        })
                    } else {
                        add(buildJsonObject {
                            put("role", "assistant")
                            put("content", m.content)
                        })
                    }
                }
                Role.TOOL -> add(buildJsonObject {
                    put("role", "tool")
                    put("tool_call_id", m.toolCallId ?: "")
                    put("content", m.content)
                })
            }
        }
    }

    private fun toolsToJson(tools: List<ToolDescriptor>): JsonArray = buildJsonArray {
        for (t in tools) {
            val params = try {
                json.parseToJsonElement(t.parametersJsonSchema)
            } catch (_: Exception) {
                buildJsonObject { put("type", "object") }
            }
            add(
                buildJsonObject {
                    put("type", "function")
                    put(
                        "function",
                        buildJsonObject {
                            put("name", t.name)
                            put("description", t.description)
                            put("parameters", params)
                        },
                    )
                },
            )
        }
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }
}
