package com.foldclaw.presentation.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.core.layout.WindowWidthSizeClass
import com.foldclaw.policy.ApprovalRequest
import com.foldclaw.presentation.theme.FoldClawColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onOpenSettings: () -> Unit = {},
    autoStartVoice: Boolean = false,
    autoSendAfterVoice: Boolean = false,
    onAutoStartConsumed: () -> Unit = {},
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    var rememberApproval by remember { mutableStateOf(false) }
    val widthClass = currentWindowAdaptiveInfo().windowSizeClass.windowWidthSizeClass
    val dualPane = widthClass != WindowWidthSizeClass.COMPACT
    val startVoice = rememberVoiceToggleHandler(
        isRecording = uiState.isRecording,
        isTranscribing = uiState.isTranscribing,
        onToggle = viewModel::toggleVoiceCapture,
    )

    LaunchedEffect(autoStartVoice, autoSendAfterVoice) {
        if (autoStartVoice) {
            viewModel.armAssistVoice(autoSendAfterVoice)
        }
    }

    AutoStartVoiceEffect(
        enabled = autoStartVoice,
        isRecording = uiState.isRecording,
        isTranscribing = uiState.isTranscribing,
        isRunning = uiState.isRunning,
        onStart = viewModel::startVoiceCapture,
        onConsumed = onAutoStartConsumed,
    )

    LaunchedEffect(uiState.voiceDraft) {
        val draft = uiState.voiceDraft ?: return@LaunchedEffect
        input = if (input.isBlank()) draft else "$input $draft"
        viewModel.clearVoiceDraft()
    }

    LaunchedEffect(uiState.messages.size, uiState.pendingApproval, uiState.timeline.size) {
        val last = uiState.messages.lastIndex
        if (last >= 0) listState.animateScrollToItem(last)
    }

    if (uiState.showHistory) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { viewModel.hideHistory() },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            HistorySheet(
                items = uiState.history,
                onItemClick = viewModel::openHistoryItem,
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        FoldClawColors.Mist,
                        FoldClawColors.Foam,
                        FoldClawColors.MistDeep.copy(alpha = 0.55f),
                    ),
                ),
            ),
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                "FoldClaw",
                                style = MaterialTheme.typography.titleLarge,
                                color = FoldClawColors.Ink,
                            )
                            Text(
                                "手机原生 AI Agent",
                                style = MaterialTheme.typography.labelMedium,
                                color = FoldClawColors.Teal,
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.toggleHistory() }) {
                            Icon(Icons.Default.History, contentDescription = "历史任务")
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Default.Settings, contentDescription = "设置")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = FoldClawColors.Foam.copy(alpha = 0.92f),
                    ),
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
                AnimatedVisibility(
                    visible = !uiState.isRunning,
                    enter = fadeIn() + slideInVertically(),
                    exit = fadeOut(),
                ) {
                    QuickPromptRow(
                        onSelect = { prompt -> viewModel.sendInstruction(prompt.instruction) },
                    )
                }
                if (dualPane) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
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
                                isRecording = uiState.isRecording,
                                isTranscribing = uiState.isTranscribing,
                                onInputChange = { input = it },
                                onSend = {
                                    viewModel.sendInstruction(input)
                                    input = ""
                                },
                                onStop = { viewModel.stop() },
                                onVoice = startVoice,
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        DetailPane(
                            modifier = Modifier
                                .weight(0.85f)
                                .fillMaxHeight()
                                .clip(MaterialTheme.shapes.large)
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)),
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
                        isRecording = uiState.isRecording,
                        isTranscribing = uiState.isTranscribing,
                        onInputChange = { input = it },
                        onSend = {
                            viewModel.sendInstruction(input)
                            input = ""
                        },
                        onStop = { viewModel.stop() },
                        onVoice = startVoice,
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickPromptRow(onSelect: (QuickPrompt) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Text(
            "试试这些",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(6.dp))
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
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        labelColor = FoldClawColors.Ink,
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = false,
                        borderColor = FoldClawColors.Teal.copy(alpha = 0.35f),
                    ),
                    shape = RoundedCornerShape(20.dp),
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
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
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
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("任务状态", style = MaterialTheme.typography.titleMedium)
        Text(
            when {
                isRunning -> "执行中"
                lastTaskState != null -> lastTaskState
                else -> "空闲"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
        Text("操作步骤", style = MaterialTheme.typography.titleSmall)
        if (timeline.isEmpty()) {
            Text(
                "执行任务后步骤会出现在这里。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(timeline, key = { it.id }) { item ->
                    TimelineLine(item)
                }
            }
        }
    }
}

@Composable
private fun TimelineLine(item: TimelineItem) {
    val prefix = when (item.status) {
        TimelineStatus.RUNNING -> "…"
        TimelineStatus.OK -> "✓"
        TimelineStatus.FAIL -> "✗"
    }
    val tint = when (item.status) {
        TimelineStatus.RUNNING -> MaterialTheme.colorScheme.primary
        TimelineStatus.OK -> FoldClawColors.Teal
        TimelineStatus.FAIL -> MaterialTheme.colorScheme.error
    }
    Text(
        "$prefix ${item.step + 1}. ${item.label}",
        style = MaterialTheme.typography.bodySmall,
        color = tint,
    )
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
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
    ) {
        if (uiState.messages.isEmpty() && !uiState.isRunning) {
            item {
                EmptyHint()
            }
        }
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
                    shape = MaterialTheme.shapes.medium,
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
private fun EmptyHint() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 36.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "今天想让手机帮你做什么？",
            style = MaterialTheme.typography.headlineMedium,
            color = FoldClawColors.Ink,
        )
        Text(
            "可以说「字体调大」「设个闹钟」，或点下方麦克风用语音。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HistorySheet(
    items: List<HistoryUiItem>,
    onItemClick: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 480.dp)
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
    ) {
        Text("历史任务", style = MaterialTheme.typography.titleMedium)
        Text(
            "点选一条可查看该次对话详情",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (items.isEmpty()) {
            Text("还没有任务记录", style = MaterialTheme.typography.bodyMedium)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(items, key = { it.taskId }) { item ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onItemClick(item.taskId) }
                            .padding(vertical = 8.dp),
                    ) {
                        Text(item.title, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "${item.subtitle} · ${item.stateLabel}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                }
            }
        }
    }
}

@Composable
private fun TimelinePanel(items: List<TimelineItem>) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("操作步骤", style = MaterialTheme.typography.labelLarge, color = FoldClawColors.Teal)
            items.forEach { item -> TimelineLine(item) }
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
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.large,
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
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = remember, onCheckedChange = onRememberChange)
                Text("始终允许此类操作", style = MaterialTheme.typography.bodySmall)
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
    val userShape = RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp)
    val assistantShape = RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp)
    val bubbleColor = if (msg.isUser) FoldClawColors.Teal else MaterialTheme.colorScheme.surface
    val contentColor = if (msg.isUser) Color.White else FoldClawColors.Ink
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
            shape = if (msg.isUser) userShape else assistantShape,
            shadowElevation = if (msg.isUser) 0.dp else 1.dp,
            modifier = Modifier
                .widthIn(max = 320.dp)
                .combinedClickable(
                    onClick = {},
                    onLongClick = copyText,
                ),
        ) {
            Row(
                modifier = Modifier.padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = msg.text,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .padding(end = 2.dp, top = 2.dp, bottom = 2.dp),
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
                        tint = contentColor.copy(alpha = 0.7f),
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun RunningIndicator() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            strokeWidth = 2.dp,
            color = FoldClawColors.Teal,
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            "正在规划并执行…",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun InputRow(
    input: String,
    isRunning: Boolean,
    isRecording: Boolean,
    isTranscribing: Boolean,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onVoice: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        shape = MaterialTheme.shapes.large,
        shadowElevation = 3.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = onInputChange,
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        when {
                            isRecording -> "正在听，再点麦克风结束…"
                            isTranscribing -> "正在识别…"
                            else -> "指令或语音…"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                },
                singleLine = true,
                enabled = !isRunning && !isRecording && !isTranscribing,
                shape = MaterialTheme.shapes.medium,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    disabledBorderColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                ),
            )
            if (isRunning) {
                CircleActionButton(
                    onClick = onStop,
                    filled = false,
                    contentDescription = "停止",
                ) {
                    Icon(
                        Icons.Default.Stop,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp),
                    )
                }
            } else {
                CircleActionButton(
                    onClick = onVoice,
                    filled = isRecording,
                    enabled = !isTranscribing,
                    contentDescription = if (isRecording) "结束录音" else "语音输入",
                ) {
                    if (isTranscribing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = FoldClawColors.Teal,
                        )
                    } else {
                        Icon(
                            Icons.Default.Mic,
                            contentDescription = null,
                            tint = if (isRecording) Color.White else FoldClawColors.Ink,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                CircleActionButton(
                    onClick = onSend,
                    enabled = input.isNotBlank() && !isRecording && !isTranscribing,
                    filled = input.isNotBlank() && !isRecording && !isTranscribing,
                    contentDescription = "发送",
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = null,
                        tint = if (input.isNotBlank() && !isRecording) Color.White else FoldClawColors.Ink.copy(alpha = 0.35f),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

/** 麦克风 / 发送共用：同尺寸圆环，填充态仅用于可发送。 */
@Composable
private fun CircleActionButton(
    onClick: () -> Unit,
    contentDescription: String,
    filled: Boolean,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val border = FoldClawColors.Ink.copy(alpha = if (enabled) 0.18f else 0.08f)
    val bg = if (filled && enabled) FoldClawColors.Ink else Color.Transparent
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(bg)
            .border(width = 1.dp, color = border, shape = CircleShape)
            .semantics { this.contentDescription = contentDescription }
            .clickable(
                enabled = enabled,
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
        content = { content() },
    )
}
