package com.foldclaw.presentation.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.core.layout.WindowWidthSizeClass
import com.foldclaw.policy.ApprovalRequest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onOpenSettings: () -> Unit = {},
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    var rememberApproval by remember { mutableStateOf(false) }
    val widthClass = currentWindowAdaptiveInfo().windowSizeClass.windowWidthSizeClass
    val dualPane = widthClass != WindowWidthSizeClass.COMPACT

    LaunchedEffect(uiState.messages.size, uiState.pendingApproval, uiState.timeline.size) {
        val last = uiState.messages.lastIndex
        if (last >= 0) listState.animateScrollToItem(last)
    }

    if (uiState.showHistory) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { viewModel.hideHistory() },
            sheetState = sheetState,
        ) {
            HistorySheet(items = uiState.history)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("FoldClaw", style = MaterialTheme.typography.titleLarge) },
                actions = {
                    IconButton(onClick = { viewModel.toggleHistory() }) {
                        Icon(Icons.Default.History, contentDescription = "历史任务")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "设置")
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
                .navigationBarsPadding(),
        ) {
            if (uiState.interruptedBanner != null) {
                InterruptedBanner(
                    banner = uiState.interruptedBanner!!,
                    instruction = uiState.interruptedInstruction,
                    onDismiss = viewModel::dismissInterrupted,
                    onRetry = viewModel::retryInterrupted,
                )
            }
            if (!uiState.isRunning) {
                QuickPromptRow(
                    onSelect = { prompt -> viewModel.sendInstruction(prompt.instruction) },
                )
            }
            if (dualPane) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1.15f)
                            .fillMaxHeight(),
                    ) {
                        ChatMessages(
                            modifier = Modifier.weight(1f),
                            listState = listState,
                            uiState = uiState,
                            rememberApproval = rememberApproval,
                            onRememberChange = { rememberApproval = it },
                            onApprove = {
                                viewModel.approvePending(rememberApproval)
                                rememberApproval = false
                            },
                            onDeny = {
                                viewModel.denyPending()
                                rememberApproval = false
                            },
                            onFollowUp = { viewModel.openFollowUpApp() },
                            showInlineTimeline = false,
                        )
                        InputRow(
                            input = input,
                            isRunning = uiState.isRunning,
                            onInputChange = { input = it },
                            onSend = {
                                viewModel.sendInstruction(input)
                                input = ""
                            },
                            onStop = { viewModel.stop() },
                        )
                    }
                    HorizontalDivider(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(1.dp),
                    )
                    DetailPane(
                        modifier = Modifier
                            .weight(0.85f)
                            .fillMaxHeight(),
                        timeline = uiState.timeline,
                        lastTaskState = uiState.lastTaskState?.name,
                        isRunning = uiState.isRunning,
                    )
                }
            } else {
                ChatMessages(
                    modifier = Modifier.weight(1f),
                    listState = listState,
                    uiState = uiState,
                    rememberApproval = rememberApproval,
                    onRememberChange = { rememberApproval = it },
                    onApprove = {
                        viewModel.approvePending(rememberApproval)
                        rememberApproval = false
                    },
                    onDeny = {
                        viewModel.denyPending()
                        rememberApproval = false
                    },
                    onFollowUp = { viewModel.openFollowUpApp() },
                    showInlineTimeline = true,
                )
                InputRow(
                    input = input,
                    isRunning = uiState.isRunning,
                    onInputChange = { input = it },
                    onSend = {
                        viewModel.sendInstruction(input)
                        input = ""
                    },
                    onStop = { viewModel.stop() },
                )
            }
        }
    }
}

@Composable
private fun QuickPromptRow(onSelect: (QuickPrompt) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Text(
            "快捷任务（兼容矩阵）",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            QuickPrompts.ALL.forEach { prompt ->
                FilterChip(
                    selected = false,
                    onClick = { onSelect(prompt) },
                    label = { Text(prompt.label) },
                )
            }
        }
    }
}

@Composable
private fun InterruptedBanner(
    banner: String,
    instruction: String?,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(banner, style = MaterialTheme.typography.titleSmall)
            if (!instruction.isNullOrBlank()) {
                Text(
                    "原指令：$instruction",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(
                "不会自动重放副作用。可重新执行（需再次确认），或忽略。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onDismiss) { Text("忽略") }
                Button(onClick = onRetry) { Text("重新执行") }
            }
        }
    }
}

@Composable
private fun DetailPane(
    modifier: Modifier,
    timeline: List<TimelineItem>,
    lastTaskState: String?,
    isRunning: Boolean,
) {
    Column(
        modifier = modifier.padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("任务状态", style = MaterialTheme.typography.titleMedium)
        Text(
            when {
                isRunning -> "RUNNING"
                lastTaskState != null -> lastTaskState
                else -> "空闲"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        HorizontalDivider()
        Text("操作步骤", style = MaterialTheme.typography.titleSmall)
        if (timeline.isEmpty()) {
            Text(
                "执行任务后步骤会出现在这里。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(timeline, key = { it.id }) { item ->
                    val prefix = when (item.status) {
                        TimelineStatus.RUNNING -> "…"
                        TimelineStatus.OK -> "✓"
                        TimelineStatus.FAIL -> "✗"
                    }
                    Text(
                        "$prefix ${item.step + 1}. ${item.label}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatMessages(
    modifier: Modifier,
    listState: androidx.compose.foundation.lazy.LazyListState,
    uiState: ChatUiState,
    rememberApproval: Boolean,
    onRememberChange: (Boolean) -> Unit,
    onApprove: () -> Unit,
    onDeny: () -> Unit,
    onFollowUp: () -> Unit,
    showInlineTimeline: Boolean,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        state = listState,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
    ) {
        items(uiState.messages, key = { it.id }) { msg ->
            MessageBubble(msg)
        }
        if (showInlineTimeline && uiState.timeline.isNotEmpty()) {
            item { TimelinePanel(uiState.timeline) }
        }
        if (uiState.followUpLabel != null && !uiState.isRunning) {
            item {
                OutlinedButton(
                    onClick = onFollowUp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(uiState.followUpLabel!!)
                }
            }
        }
        if (uiState.pendingApproval != null) {
            item {
                ApprovalCard(
                    request = uiState.pendingApproval!!,
                    remember = rememberApproval,
                    onRememberChange = onRememberChange,
                    onApprove = onApprove,
                    onDeny = onDeny,
                )
            }
        } else if (uiState.isRunning) {
            item { RunningIndicator() }
        }
    }
}

@Composable
private fun HistorySheet(items: List<HistoryUiItem>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 480.dp)
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
    ) {
        Text("历史任务", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        if (items.isEmpty()) {
            Text("还没有任务记录", style = MaterialTheme.typography.bodyMedium)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(items, key = { it.taskId }) { item ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                    ) {
                        Text(item.title, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "${item.subtitle} · ${item.stateLabel}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun TimelinePanel(items: List<TimelineItem>) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("操作步骤", style = MaterialTheme.typography.labelLarge)
            items.forEach { item ->
                val prefix = when (item.status) {
                    TimelineStatus.RUNNING -> "…"
                    TimelineStatus.OK -> "✓"
                    TimelineStatus.FAIL -> "✗"
                }
                Text(
                    "$prefix ${item.step + 1}. ${item.label}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun ApprovalCard(
    request: ApprovalRequest,
    remember: Boolean,
    onRememberChange: (Boolean) -> Unit,
    onApprove: () -> Unit,
    onDeny: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("需要确认", style = MaterialTheme.typography.titleMedium)
            Text(request.humanSummary, style = MaterialTheme.typography.bodyMedium)
            Text(
                "工具: ${request.toolName} · 风险: ${request.riskLevel}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { onRememberChange(!remember) },
            ) {
                Checkbox(checked = remember, onCheckedChange = onRememberChange)
                Text("始终允许此类操作", style = MaterialTheme.typography.bodyMedium)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onApprove, modifier = Modifier.weight(1f)) {
                    Text("允许")
                }
                OutlinedButton(onClick = onDeny, modifier = Modifier.weight(1f)) {
                    Text("拒绝")
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(msg: ChatMessage) {
    val context = LocalContext.current
    val bubbleColor = if (msg.isUser) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = if (msg.isUser) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val copyText = remember(msg.id, msg.text) {
        {
            if (msg.text.isNotBlank()) {
                val cm = context.getSystemService(ClipboardManager::class.java)
                cm.setPrimaryClip(ClipData.newPlainText("foldclaw_message", msg.text))
                Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (msg.isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        Surface(
            color = bubbleColor,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
                .widthIn(max = 320.dp)
                .combinedClickable(
                    onClick = {},
                    onLongClick = copyText,
                ),
        ) {
            Row(
                modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = msg.text,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .padding(vertical = 2.dp, horizontal = 0.dp)
                        .padding(end = 2.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor,
                )
                IconButton(
                    onClick = copyText,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "复制",
                        tint = contentColor.copy(alpha = 0.75f),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun RunningIndicator() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.height(16.dp).width(16.dp),
            strokeWidth = 2.dp,
        )
        Spacer(Modifier.width(8.dp))
        Text("思考中…", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun InputRow(
    input: String,
    isRunning: Boolean,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = input,
            onValueChange = onInputChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("打开设置、读界面、设闹钟…") },
            singleLine = true,
            enabled = !isRunning,
        )
        Spacer(Modifier.width(8.dp))
        if (isRunning) {
            IconButton(onClick = onStop) {
                Icon(Icons.Default.Stop, contentDescription = "停止")
            }
        } else {
            IconButton(onClick = onSend, enabled = input.isNotBlank()) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "发送")
            }
        }
    }
}
