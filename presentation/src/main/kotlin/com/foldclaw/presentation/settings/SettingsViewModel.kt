package com.foldclaw.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foldclaw.data.keystore.KeyVault
import com.foldclaw.data.llm.ProviderRouter
import com.foldclaw.data.prefs.LlmProviderDefaults
import com.foldclaw.data.prefs.ProviderSettingsStore
import com.foldclaw.domain.memory.MemoryItem
import com.foldclaw.domain.memory.MemoryStore
import com.foldclaw.domain.model.Result
import com.foldclaw.domain.tool.NotificationSummaryBackend
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val useRealApi: Boolean = false,
    val preset: String = LlmProviderDefaults.DEFAULT_PRESET,
    val workspaceId: String = "",
    val baseUrl: String = LlmProviderDefaults.DEFAULT_BASE_URL,
    val modelId: String = LlmProviderDefaults.DEFAULT_MODEL,
    val apiKeyInput: String = "",
    val hasApiKey: Boolean = false,
    val statusMessage: String? = null,
    val statusIsError: Boolean = false,
    val isSaving: Boolean = false,
    val isTesting: Boolean = false,
    val memories: List<MemoryItem> = emptyList(),
    val notificationAccessGranted: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsStore: ProviderSettingsStore,
    private val keyVault: KeyVault,
    private val providerRouter: ProviderRouter,
    private val memoryStore: MemoryStore,
    private val notificationBackend: NotificationSummaryBackend,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsStore.settingsFlow.collect { s ->
                val hasKey = keyVault.hasApiKey(s.providerId)
                _uiState.update {
                    it.copy(
                        useRealApi = s.useRealApi,
                        preset = s.preset,
                        workspaceId = s.workspaceId,
                        baseUrl = s.baseUrl,
                        modelId = s.modelId,
                        hasApiKey = hasKey,
                        apiKeyInput = if (hasKey && it.apiKeyInput.isBlank()) "" else it.apiKeyInput,
                        notificationAccessGranted = notificationBackend.isAccessGranted(),
                    )
                }
            }
        }
        refreshMemories()
    }

    fun refreshExtras() {
        _uiState.update {
            it.copy(notificationAccessGranted = notificationBackend.isAccessGranted())
        }
        refreshMemories()
    }

    fun refreshMemories() {
        viewModelScope.launch {
            val items = memoryStore.list(50)
            _uiState.update { it.copy(memories = items) }
        }
    }

    fun deleteMemory(id: Long) {
        viewModelScope.launch {
            memoryStore.deleteById(id)
            refreshMemories()
        }
    }

    fun onUseRealApiChange(v: Boolean) = _uiState.update { it.copy(useRealApi = v, statusMessage = null) }

    fun onPresetChange(preset: String) {
        _uiState.update { state ->
            when (preset) {
                LlmProviderDefaults.PRESET_BAILIAN -> {
                    val ws = state.workspaceId.trim()
                    val url = if (ws.isNotEmpty()) {
                        LlmProviderDefaults.bailianWorkspaceBaseUrl(ws)
                    } else {
                        LlmProviderDefaults.BAILIAN_DASH_SCOPE_BASE_URL
                    }
                    state.copy(
                        preset = preset,
                        baseUrl = url,
                        modelId = LlmProviderDefaults.BAILIAN_MODEL,
                        statusMessage = null,
                    )
                }
                LlmProviderDefaults.PRESET_OPENAI -> state.copy(
                    preset = preset,
                    baseUrl = LlmProviderDefaults.OPENAI_BASE_URL,
                    modelId = LlmProviderDefaults.OPENAI_MODEL,
                    workspaceId = "",
                    statusMessage = null,
                )
                else -> state.copy(preset = LlmProviderDefaults.PRESET_CUSTOM, statusMessage = null)
            }
        }
    }

    fun onWorkspaceIdChange(v: String) {
        _uiState.update { state ->
            val trimmed = v.trim()
            val url = if (trimmed.isNotEmpty()) {
                LlmProviderDefaults.bailianWorkspaceBaseUrl(trimmed)
            } else {
                LlmProviderDefaults.BAILIAN_DASH_SCOPE_BASE_URL
            }
            state.copy(
                workspaceId = v,
                preset = LlmProviderDefaults.PRESET_BAILIAN,
                baseUrl = url,
                statusMessage = null,
            )
        }
    }

    fun onBaseUrlChange(v: String) = _uiState.update {
        it.copy(
            baseUrl = v,
            preset = LlmProviderDefaults.inferPreset(v),
            workspaceId = LlmProviderDefaults.extractWorkspaceId(v).ifBlank { it.workspaceId },
            statusMessage = null,
        )
    }

    fun onModelChange(v: String) = _uiState.update { it.copy(modelId = v, statusMessage = null) }
    fun onApiKeyChange(v: String) = _uiState.update { it.copy(apiKeyInput = v, statusMessage = null) }

    fun save() {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, statusMessage = null) }
            try {
                if (state.useRealApi) {
                    val key = state.apiKeyInput.trim().ifBlank {
                        keyVault.getApiKey(KeyVault.DEFAULT_PROVIDER_ID).orEmpty()
                    }
                    if (key.isBlank()) {
                        _uiState.update {
                            it.copy(
                                isSaving = false,
                                statusIsError = true,
                                statusMessage = "启用真实 API 时必须填写百炼 API Key（DASHSCOPE_API_KEY）",
                            )
                        }
                        return@launch
                    }
                    if (state.apiKeyInput.isNotBlank()) {
                        keyVault.storeApiKey(KeyVault.DEFAULT_PROVIDER_ID, state.apiKeyInput.trim())
                    }
                }
                if (state.baseUrl.isBlank()) {
                    _uiState.update {
                        it.copy(isSaving = false, statusIsError = true, statusMessage = "Base URL 不能为空")
                    }
                    return@launch
                }
                if (state.modelId.isBlank()) {
                    _uiState.update {
                        it.copy(isSaving = false, statusIsError = true, statusMessage = "Model 不能为空")
                    }
                    return@launch
                }
                settingsStore.save(
                    useRealApi = state.useRealApi,
                    baseUrl = state.baseUrl.trim().trimEnd('/'),
                    modelId = state.modelId.trim(),
                    providerId = KeyVault.DEFAULT_PROVIDER_ID,
                    preset = state.preset,
                    workspaceId = state.workspaceId,
                )
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        hasApiKey = keyVault.hasApiKey(KeyVault.DEFAULT_PROVIDER_ID),
                        apiKeyInput = "",
                        statusIsError = false,
                        statusMessage = if (state.useRealApi) "已保存，聊天将使用百炼/兼容 API" else "已保存，聊天使用本地 Fake",
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        statusIsError = true,
                        statusMessage = "保存失败: ${e.message}",
                    )
                }
            }
        }
    }

    fun clearKey() {
        viewModelScope.launch {
            keyVault.deleteApiKey(KeyVault.DEFAULT_PROVIDER_ID)
            settingsStore.save(
                useRealApi = false,
                baseUrl = _uiState.value.baseUrl,
                modelId = _uiState.value.modelId,
                preset = _uiState.value.preset,
                workspaceId = _uiState.value.workspaceId,
            )
            _uiState.update {
                it.copy(
                    hasApiKey = false,
                    useRealApi = false,
                    apiKeyInput = "",
                    statusIsError = false,
                    statusMessage = "已清除 API Key，并关闭真实 API",
                )
            }
        }
    }

    fun testConnection() {
        viewModelScope.launch {
            val state = _uiState.value
            val baseUrl = state.baseUrl.trim().trimEnd('/')
            val modelId = state.modelId.trim().ifBlank { LlmProviderDefaults.DEFAULT_MODEL }
            val keyFromInput = state.apiKeyInput.trim()
            val key = keyFromInput.ifBlank {
                keyVault.getApiKey(KeyVault.DEFAULT_PROVIDER_ID).orEmpty()
            }

            if (baseUrl.isBlank()) {
                _uiState.update {
                    it.copy(statusIsError = true, statusMessage = "请先填写 Base URL")
                }
                return@launch
            }
            if (key.isBlank()) {
                _uiState.update {
                    it.copy(statusIsError = true, statusMessage = "请先填写百炼 API Key 再测试连接")
                }
                return@launch
            }

            _uiState.update { it.copy(isTesting = true, statusMessage = null) }
            when (val res = providerRouter.verifyRealConnection(baseUrl, key, modelId)) {
                is Result.Success -> {
                    if (keyFromInput.isNotBlank()) {
                        keyVault.storeApiKey(KeyVault.DEFAULT_PROVIDER_ID, keyFromInput)
                    }
                    settingsStore.save(
                        useRealApi = true,
                        baseUrl = baseUrl,
                        modelId = modelId,
                        preset = state.preset,
                        workspaceId = state.workspaceId,
                    )
                    _uiState.update {
                        it.copy(
                            isTesting = false,
                            useRealApi = true,
                            hasApiKey = true,
                            apiKeyInput = "",
                            statusIsError = false,
                            statusMessage = "连接成功（已验证 chat/completions）",
                        )
                    }
                }
                is Result.Failure -> _uiState.update {
                    it.copy(
                        isTesting = false,
                        statusIsError = true,
                        statusMessage = "连接失败: ${res.error.reason}",
                    )
                }
            }
        }
    }
}
