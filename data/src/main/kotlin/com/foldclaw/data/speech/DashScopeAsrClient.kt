package com.foldclaw.data.speech

import android.util.Log
import com.foldclaw.data.keystore.KeyVault
import com.foldclaw.data.prefs.LlmProviderDefaults
import com.foldclaw.data.prefs.ProviderSettingsStore
import com.foldclaw.domain.model.DomainError
import com.foldclaw.domain.model.ErrorKind
import com.foldclaw.domain.model.Result
import com.foldclaw.domain.speech.SpeechAsrClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.Base64
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 百炼 Qwen-ASR：走 OpenAI 兼容 [chat/completions] + base64 音频。
 * 注意：DashScope **没有** `/audio/transcriptions`，旧实现会 404。
 */
@Singleton
class DashScopeAsrClient @Inject constructor(
    private val keyVault: KeyVault,
    private val settings: ProviderSettingsStore,
    private val http: OkHttpClient,
) : SpeechAsrClient {

    private val json = Json { ignoreUnknownKeys = true }
    private val asrHttp: OkHttpClient by lazy {
        http.newBuilder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(90, TimeUnit.SECONDS)
            .build()
    }

    override suspend fun transcribe(audioFile: File): Result<String> = withContext(Dispatchers.IO) {
        if (!audioFile.exists() || audioFile.length() < 64) {
            return@withContext Result.Failure(DomainError(ErrorKind.ActionFailed, "录音太短或无效"))
        }
        // Base64 后约 +33%，接口上限约 10MB 编码后
        if (audioFile.length() > 6_500_000) {
            return@withContext Result.Failure(DomainError(ErrorKind.ActionFailed, "录音过长，请说短一些"))
        }
        val key = keyVault.getApiKey(KeyVault.DEFAULT_PROVIDER_ID)?.trim().orEmpty()
        if (key.isEmpty()) {
            return@withContext Result.Failure(
                DomainError(ErrorKind.ActionFailed, "请先在设置里配置百炼 API Key，才能用国内语音识别"),
            )
        }
        val base = resolveAsrBase(settings.current().baseUrl)
        val url = "$base/chat/completions"
        val mime = mimeFor(audioFile)
        val dataUri = "data:$mime;base64," + Base64.getEncoder().encodeToString(audioFile.readBytes())
        val bodyJson = buildJsonObject {
            put("model", MODEL)
            put("stream", false)
            putJsonArray("messages") {
                add(
                    buildJsonObject {
                        put("role", "user")
                        putJsonArray("content") {
                            add(
                                buildJsonObject {
                                    put("type", "input_audio")
                                    putJsonObject("input_audio") {
                                        put("data", dataUri)
                                    }
                                },
                            )
                        }
                    },
                )
            }
            putJsonObject("asr_options") {
                put("enable_itn", false)
                put("language", "zh")
            }
        }.toString()

        Log.i(TAG, "ASR POST $url model=$MODEL bytes=${audioFile.length()} mime=$mime")
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $key")
            .addHeader("Content-Type", "application/json")
            .post(bodyJson.toRequestBody("application/json".toMediaType()))
            .build()
        try {
            asrHttp.newCall(request).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    Log.w(TAG, "ASR fail HTTP ${resp.code}: ${raw.take(300)}")
                    return@withContext Result.Failure(
                        DomainError(
                            ErrorKind.ActionFailed,
                            "语音识别失败 HTTP ${resp.code}: ${raw.take(160)}",
                        ),
                    )
                }
                val text = extractText(raw)
                if (text.isEmpty()) {
                    Log.w(TAG, "ASR empty text: ${raw.take(300)}")
                    Result.Failure(DomainError(ErrorKind.ActionFailed, "未识别到有效内容"))
                } else {
                    Log.i(TAG, "ASR ok: ${text.take(80)}")
                    Result.Success(text)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "ASR exception", e)
            Result.Failure(DomainError(ErrorKind.ActionFailed, "语音识别异常: ${e.message}"))
        }
    }

    private fun resolveAsrBase(configured: String): String {
        val base = configured.trim().trimEnd('/')
        // ASR 与聊天共用 OpenAI 兼容 base；非百炼域名则回落到 DashScope
        return when {
            base.contains("compatible-mode") -> base
            base.contains("dashscope.aliyuncs.com") || base.contains("maas.aliyuncs.com") ->
                if (base.endsWith("/v1")) base else "$base/compatible-mode/v1"
            else -> LlmProviderDefaults.BAILIAN_DASH_SCOPE_BASE_URL
        }
    }

    private fun mimeFor(file: File): String {
        val name = file.name.lowercase()
        return when {
            name.endsWith(".m4a") || name.endsWith(".mp4") || name.endsWith(".aac") -> "audio/mp4"
            name.endsWith(".mp3") -> "audio/mpeg"
            name.endsWith(".wav") -> "audio/wav"
            name.endsWith(".ogg") -> "audio/ogg"
            else -> "audio/mp4"
        }
    }

    private fun extractText(raw: String): String {
        val root = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull()
            ?: return ""
        val choices = root["choices"] as? JsonArray ?: return ""
        val message = choices.firstOrNull()?.jsonObject?.get("message")?.jsonObject
        val content = message?.get("content") ?: return ""
        return when (content) {
            is JsonPrimitive -> content.contentOrNull?.trim().orEmpty()
            is JsonArray -> content.joinToString("") { el ->
                val o = el as? JsonObject ?: return@joinToString ""
                o["text"]?.jsonPrimitive?.contentOrNull
                    ?: o["content"]?.jsonPrimitive?.contentOrNull
                    ?: ""
            }.trim()
            else -> ""
        }
    }

    companion object {
        private const val TAG = "FoldClaw/ASR"
        const val MODEL = "qwen3-asr-flash"
    }
}
