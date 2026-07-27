package com.foldclaw.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.providerSettingsStore by preferencesDataStore(name = "foldclaw_provider_settings")

data class LlmSettings(
    val useRealApi: Boolean = false,
    val providerId: String = LlmProviderDefaults.PROVIDER_ID,
    val baseUrl: String = LlmProviderDefaults.DEFAULT_BASE_URL,
    val modelId: String = LlmProviderDefaults.DEFAULT_MODEL,
    val preset: String = LlmProviderDefaults.DEFAULT_PRESET,
    val workspaceId: String = "",
    /** 是否已配置过 Key（不暴露明文）。 */
    val hasApiKey: Boolean = false,
    /** 任务成功后是否用系统 TTS 念出结果。默认开启以保持既有行为。 */
    val ttsSpeakResults: Boolean = true,
)

@Singleton
class ProviderSettingsStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val useRealKey = booleanPreferencesKey("use_real_api")
    private val providerKey = stringPreferencesKey("provider_id")
    private val baseUrlKey = stringPreferencesKey("base_url")
    private val modelKey = stringPreferencesKey("model_id")
    private val presetKey = stringPreferencesKey("preset")
    private val workspaceKey = stringPreferencesKey("workspace_id")
    private val ttsSpeakResultsKey = booleanPreferencesKey("tts_speak_results")

    val settingsFlow: Flow<LlmSettings> = context.providerSettingsStore.data.map { prefs ->
        val rawBase = prefs[baseUrlKey]
        val rawModel = prefs[modelKey]
        val useReal = prefs[useRealKey] ?: false
        // 未真正启用过 OpenAI 时，把旧默认值迁移到百炼
        val migratedFromOpenAi =
            !useReal &&
                (rawBase == null || rawBase == LlmProviderDefaults.OPENAI_BASE_URL) &&
                (rawModel == null || rawModel == LlmProviderDefaults.OPENAI_MODEL)

        val baseUrl = if (migratedFromOpenAi) {
            LlmProviderDefaults.DEFAULT_BASE_URL
        } else {
            rawBase ?: LlmProviderDefaults.DEFAULT_BASE_URL
        }
        val modelId = if (migratedFromOpenAi) {
            LlmProviderDefaults.DEFAULT_MODEL
        } else {
            rawModel ?: LlmProviderDefaults.DEFAULT_MODEL
        }
        val workspaceId = prefs[workspaceKey]
            ?: LlmProviderDefaults.extractWorkspaceId(baseUrl)
        val preset = prefs[presetKey] ?: LlmProviderDefaults.inferPreset(baseUrl)

        LlmSettings(
            useRealApi = useReal,
            providerId = prefs[providerKey] ?: LlmProviderDefaults.PROVIDER_ID,
            baseUrl = baseUrl,
            modelId = modelId,
            preset = preset,
            workspaceId = workspaceId,
            ttsSpeakResults = prefs[ttsSpeakResultsKey] ?: true,
        )
    }

    suspend fun current(): LlmSettings = settingsFlow.first()

    suspend fun save(
        useRealApi: Boolean,
        baseUrl: String,
        modelId: String,
        providerId: String = LlmProviderDefaults.PROVIDER_ID,
        preset: String = LlmProviderDefaults.inferPreset(baseUrl),
        workspaceId: String = "",
    ) {
        context.providerSettingsStore.edit { prefs ->
            prefs[useRealKey] = useRealApi
            prefs[baseUrlKey] = baseUrl.trim().trimEnd('/')
            prefs[modelKey] = modelId.trim()
            prefs[providerKey] = providerId
            prefs[presetKey] = preset
            prefs[workspaceKey] = workspaceId.trim()
        }
    }

    suspend fun setTtsSpeakResults(enabled: Boolean) {
        context.providerSettingsStore.edit { prefs ->
            prefs[ttsSpeakResultsKey] = enabled
        }
    }
}
