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
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.foldclaw.data.prefs.LlmProviderDefaults
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    val lifecycleOwner = LocalLifecycleOwner.current
    BackHandler(onBack = onBack)
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) viewModel.exportBackup(uri)
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) viewModel.importBackup(uri)
    }
    // Key 输入页禁止截屏/录屏进入最近任务预览（审查报告 BYOK）
    DisposableEffect(Unit) {
        val window = context.findActivity()?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshExtras()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
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

            Spacer(modifier = Modifier.height(12.dp))
            Text("随时唤起", style = MaterialTheme.typography.titleSmall)
            Text(
                "1) 设置 → 应用 → 默认应用 → 数字助理应用 → 选 FoldClaw（手势/侧键助理会开麦）\n" +
                    "2) 下拉快捷设置面板，编辑磁贴，添加「FoldClaw 开麦」\n" +
                    "三星侧键若仍绑 Bixby，需在侧键设置里单独改。",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedButton(
                onClick = {
                    runCatching {
                        context.startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
                    }.onFailure {
                        context.startActivity(Intent(Settings.ACTION_SETTINGS))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("打开默认应用设置")
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text("语音与情报", style = MaterialTheme.typography.titleSmall)
            Text(
                "语音输入走百炼 ASR（同 API Key）。通知摘要需开启通知使用权。",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("任务结果 TTS 播报", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (state.ttsSpeakResults) "完成后用系统语音念出结果" else "完成后仅显示文字，不播报",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = state.ttsSpeakResults,
                    onCheckedChange = viewModel::onTtsSpeakResultsChange,
                )
            }
            OutlinedButton(
                onClick = {
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (state.notificationAccessGranted) "通知使用权：已开启（点此管理）"
                    else "开启通知使用权（只读摘要）",
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text("数据备份（跨重装兼容）", style = MaterialTheme.typography.titleSmall)
            Text(
                "Android 会按包名隔离数据：卸载、清数据、或签名不一致的覆盖安装都会清空记忆。" +
                    "请定期导出 JSON；换机/重装后导入即可恢复。" +
                    "API Key 因系统 Keystore 无法随备份迁移，需重新填写。" +
                    "日常更新请用覆盖安装（adb install -r），不要先卸载。",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault())
                            .format(Date())
                        exportLauncher.launch("foldclaw_backup_$stamp.json")
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !state.isBackingUp,
                ) {
                    Text(if (state.isBackingUp) "处理中…" else "导出备份")
                }
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("application/json", "text/*", "*/*")) },
                    modifier = Modifier.weight(1f),
                    enabled = !state.isBackingUp,
                ) {
                    Text("导入备份")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text("个人记忆", style = MaterialTheme.typography.titleSmall)
            Text(
                "仅你自己使用时可放心保存偏好；也可在对话里说「记住…」。",
                style = MaterialTheme.typography.bodySmall,
            )
            if (state.memories.isEmpty()) {
                Text("暂无记忆", style = MaterialTheme.typography.bodyMedium)
            } else {
                state.memories.forEach { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.key, style = MaterialTheme.typography.titleSmall)
                            Text(item.value, style = MaterialTheme.typography.bodySmall)
                        }
                        OutlinedButton(onClick = { viewModel.deleteMemory(item.id) }) {
                            Text("删除")
                        }
                    }
                }
            }
        }
    }
}
