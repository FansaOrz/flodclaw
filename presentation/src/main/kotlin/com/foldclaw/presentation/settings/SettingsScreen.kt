package com.foldclaw.presentation.settings

import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.foldclaw.data.prefs.LlmProviderDefaults

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Key 输入页禁止截屏/录屏进入最近任务预览（审查报告 BYOK）
    DisposableEffect(Unit) {
        val window = context.findActivity()?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("模型设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "默认对接阿里云百炼（通义千问）OpenAI 兼容接口。填入控制台 API Key 即可调用。",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text("平台预设", style = MaterialTheme.typography.titleSmall)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = state.preset == LlmProviderDefaults.PRESET_BAILIAN,
                    onClick = { viewModel.onPresetChange(LlmProviderDefaults.PRESET_BAILIAN) },
                    label = { Text("阿里云百炼") },
                )
                FilterChip(
                    selected = state.preset == LlmProviderDefaults.PRESET_OPENAI,
                    onClick = { viewModel.onPresetChange(LlmProviderDefaults.PRESET_OPENAI) },
                    label = { Text("OpenAI") },
                )
                FilterChip(
                    selected = state.preset == LlmProviderDefaults.PRESET_CUSTOM,
                    onClick = { viewModel.onPresetChange(LlmProviderDefaults.PRESET_CUSTOM) },
                    label = { Text("自定义") },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("启用真实 API", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (state.useRealApi) "当前走云端模型" else "当前使用本地 Fake 规则引擎",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = state.useRealApi,
                    onCheckedChange = viewModel::onUseRealApiChange,
                )
            }
            if (state.preset == LlmProviderDefaults.PRESET_BAILIAN) {
                OutlinedTextField(
                    value = state.workspaceId,
                    onValueChange = viewModel::onWorkspaceIdChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("业务空间 ID（可选）") },
                    placeholder = { Text("llm-xxxxxxxx") },
                    supportingText = {
                        Text("留空用 dashscope.aliyuncs.com；填写后自动拼专属域名")
                    },
                    singleLine = true,
                )
            }
            OutlinedTextField(
                value = state.baseUrl,
                onValueChange = viewModel::onBaseUrlChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Base URL") },
                placeholder = { Text(LlmProviderDefaults.DEFAULT_BASE_URL) },
                supportingText = {
                    Text("北京专属域名：https://{WorkspaceId}.cn-beijing.maas.aliyuncs.com/compatible-mode/v1")
                },
                singleLine = true,
            )
            OutlinedTextField(
                value = state.modelId,
                onValueChange = viewModel::onModelChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Model") },
                placeholder = { Text("qwen-plus / qwen3.7-plus / qwen-flash") },
                singleLine = true,
            )
            OutlinedTextField(
                value = state.apiKeyInput,
                onValueChange = viewModel::onApiKeyChange,
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text(
                        if (state.hasApiKey) {
                            "API Key（已保存，留空则不改）"
                        } else {
                            "百炼 API Key（DASHSCOPE_API_KEY）"
                        },
                    )
                },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
            )
            if (state.statusMessage != null) {
                Text(
                    state.statusMessage!!,
                    color = if (state.statusIsError) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = viewModel::save,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isSaving,
            ) {
                Text(if (state.isSaving) "保存中…" else "保存")
            }
            OutlinedButton(
                onClick = viewModel::testConnection,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isTesting,
            ) {
                Text(if (state.isTesting) "测试中…" else "测试连接")
            }
            if (state.hasApiKey) {
                OutlinedButton(
                    onClick = viewModel::clearKey,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("清除已保存的 API Key")
                }
            }
        }
    }
}
