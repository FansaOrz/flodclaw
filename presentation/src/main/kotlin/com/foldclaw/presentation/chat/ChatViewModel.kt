package com.foldclaw.presentation.chat

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foldclaw.agent.AgentOrchestrator
import com.foldclaw.agent.FollowUpTarget
import com.foldclaw.agent.OrchestratorEvent
import com.foldclaw.device.fgs.AgentRunSession
import com.foldclaw.device.speech.MediaAudioRecorder
import com.foldclaw.domain.agent.TaskHistoryReader
import com.foldclaw.domain.agent.TaskSummary
import com.foldclaw.domain.model.Role
import com.foldclaw.domain.model.TaskState
import com.foldclaw.domain.speech.SpeechAsrClient
import com.foldclaw.domain.speech.TtsSpeaker
import com.foldclaw.domain.tool.ToolOutcome
import com.foldclaw.policy.ApprovalRequest
import com.foldclaw.policy.CapabilityEnvelope
import com.foldclaw.presentation.approval.UiApprovalGate
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val timeline: List<TimelineItem> = emptyList(),
    val history: List<HistoryUiItem> = emptyList(),
    val showHistory: Boolean = false,
    val isRunning: Boolean = false,
    val pendingApproval: ApprovalRequest? = null,
    val lastTaskState: TaskState? = null,
    val followUpTarget: FollowUpTarget? = null,
    val followUpLabel: String? = null,
    /** 进程死亡后发现的中断任务提示；不自动续跑。 */
    val interruptedBanner: String? = null,
    val interruptedInstruction: String? = null,
    val isRecording: Boolean = false,
    val isTranscribing: Boolean = false,
    /** 语音识别结果，UI 消费后应 clearVoiceDraft。 */
    val voiceDraft: String? = null,
    /** 下一次识别结果自动发送（助理/磁贴唤起）。 */
    val autoSendAfterVoice: Boolean = false,
)

data class ChatMessage(
    val id: String,
    val role: Role,
    val text: String,
    val isUser: Boolean,
)

data class TimelineItem(
    val id: String,
    val step: Int,
    val label: String,
    val status: TimelineStatus,
)

data class HistoryUiItem(
    val taskId: String,
    val title: String,
    val subtitle: String,
    val stateLabel: String,
)

enum class TimelineStatus { RUNNING, OK, FAIL }

@HiltViewModel
class ChatViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val orchestrator: AgentOrchestrator,
    private val approvalGate: UiApprovalGate,
    private val historyReader: TaskHistoryReader,
    private val runSession: AgentRunSession,
    private val asr: SpeechAsrClient,
    private val recorder: MediaAudioRecorder,
    private val tts: TtsSpeaker,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val timeFmt = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())

    init {
        viewModelScope.launch {
            approvalGate.pending.collect { req ->
                _uiState.update { it.copy(pendingApproval = req) }
            }
        }
        viewModelScope.launch {
            orchestrator.events.collect { event ->
                when (event) {
                    is OrchestratorEvent.StepStarted -> {
                        _uiState.update { state ->
                            state.copy(
                                timeline = state.timeline + TimelineItem(
                                    id = "s${event.step}-${event.toolName}",
                                    step = event.step,
                                    label = "执行 ${event.toolName}",
                                    status = TimelineStatus.RUNNING,
                                ),
                            )
                        }
                    }
                    is OrchestratorEvent.StepCompleted -> {
                        val ok = event.outcome !is ToolOutcome.Failure
                        val label = when (val o = event.outcome) {
                            is ToolOutcome.SideEffect -> o.summary
                            is ToolOutcome.Text -> o.text
                            is ToolOutcome.Failure -> o.error.reason
                            is ToolOutcome.NeedsUserHandoff -> o.reason
                        }
                        _uiState.update { state ->
                            val updated = state.timeline.map {
                                if (it.step == event.step && it.status == TimelineStatus.RUNNING) {
                                    it.copy(
                                        label = label,
                                        status = if (ok) TimelineStatus.OK else TimelineStatus.FAIL,
                                    )
                                } else it
                            }
                            state.copy(timeline = updated)
                        }
                    }
                    is OrchestratorEvent.StateChanged -> Unit
                    is OrchestratorEvent.Error -> Unit
                }
            }
        }
        viewModelScope.launch {
            historyReader.markStaleRunningAsInterrupted()
            val interrupted = historyReader.latestInterrupted()
            if (interrupted != null) {
                _uiState.update {
                    it.copy(
                        interruptedBanner = "发现中断任务（不自动重放副作用）",
                        interruptedInstruction = interrupted.instruction,
                    )
                }
            }
            refreshHistory()
        }
    }

    fun dismissInterrupted() {
        _uiState.update { it.copy(interruptedBanner = null, interruptedInstruction = null) }
    }

    fun clearVoiceDraft() {
        _uiState.update { it.copy(voiceDraft = null) }
    }

    /** 助理/磁贴唤起：标记自动发送，并由 UI 在授权后调用 [startVoiceCapture]。 */
    fun armAssistVoice(autoSend: Boolean) {
        _uiState.update { it.copy(autoSendAfterVoice = autoSend) }
    }

    fun startVoiceCapture() {
        if (_uiState.value.isRunning || _uiState.value.isTranscribing || _uiState.value.isRecording) return
        startRecording()
    }

    fun toggleVoiceCapture() {
        if (_uiState.value.isRunning || _uiState.value.isTranscribing) return
        if (_uiState.value.isRecording) {
            stopAndTranscribe()
        } else {
            startRecording()
        }
    }

    private fun startRecording() {
        tts.stop()
        try {
            recorder.start()
            _uiState.update { it.copy(isRecording = true, voiceDraft = null) }
            Toast.makeText(appContext, "正在听…再说完后点麦克风结束", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.w("FoldClaw", "startRecording", e)
            recorder.cancel()
            Toast.makeText(appContext, "无法开始录音：${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopAndTranscribe() {
        val file = recorder.stop()
        val autoSend = _uiState.value.autoSendAfterVoice
        _uiState.update { it.copy(isRecording = false, isTranscribing = true) }
        viewModelScope.launch {
            try {
                if (file == null) {
                    Toast.makeText(appContext, "没有录到声音", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val result = withContext(Dispatchers.IO) { asr.transcribe(file) }
                file.delete()
                when (result) {
                    is com.foldclaw.domain.model.Result.Success -> {
                        val text = result.data
                        if (autoSend) {
                            _uiState.update { it.copy(autoSendAfterVoice = false, voiceDraft = null) }
                            sendInstruction(text)
                        } else {
                            _uiState.update { it.copy(voiceDraft = text) }
                        }
                    }
                    is com.foldclaw.domain.model.Result.Failure -> {
                        _uiState.update { it.copy(autoSendAfterVoice = false) }
                        Toast.makeText(appContext, result.error.reason, Toast.LENGTH_LONG).show()
                    }
                }
            } finally {
                _uiState.update { it.copy(isTranscribing = false) }
            }
        }
    }

    fun retryInterrupted() {
        val instruction = _uiState.value.interruptedInstruction ?: return
        dismissInterrupted()
        sendInstruction(instruction)
    }

    fun sendInstruction(instruction: String) {
        if (instruction.isBlank() || _uiState.value.isRunning) return
        val userMsg = ChatMessage(
            id = UUID.randomUUID().toString(),
            role = Role.USER,
            text = instruction,
            isUser = true,
        )
        _uiState.update {
            it.copy(
                messages = it.messages + userMsg,
                isRunning = true,
                timeline = emptyList(),
                lastTaskState = null,
                followUpTarget = null,
                followUpLabel = null,
                showHistory = false,
                interruptedBanner = null,
                interruptedInstruction = null,
            )
        }
        viewModelScope.launch {
            val taskId = "task_${System.currentTimeMillis()}"
            val envelope = CapabilityEnvelope.alphaDefault(taskId = taskId)
            runSession.start(instruction)
            var finalState: TaskState = TaskState.FAILED
            var detail: String? = null
            try {
                val result = orchestrator.run(taskId, instruction, envelope)
                val run = result.getOrNull()
                finalState = run?.state ?: TaskState.FAILED
                detail = when (result) {
                    is com.foldclaw.domain.model.Result.Failure -> result.error.reason
                    else -> run?.userVisibleMessage
                }
                val reply = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    role = Role.ASSISTANT,
                    text = stateToText(finalState, detail),
                    isUser = false,
                )
                _uiState.update {
                    it.copy(
                        messages = it.messages + reply,
                        isRunning = false,
                        lastTaskState = finalState,
                        pendingApproval = null,
                        followUpTarget = run?.followUpTarget,
                        followUpLabel = followUpLabelFor(run?.followUpTarget),
                    )
                }
                if (finalState == TaskState.COMPLETED) {
                    tts.speak(reply.text)
                }
            } finally {
                runSession.finish(finalState, detail)
            }
            refreshHistory()
        }
    }

    fun openFollowUpApp() {
        val target = _uiState.value.followUpTarget ?: return
        val candidates = when (target) {
            FollowUpTarget.CLOCK -> listOf(
                Intent(AlarmClock.ACTION_SHOW_ALARMS),
                Intent(AlarmClock.ACTION_SET_ALARM),
                appContext.packageManager.getLaunchIntentForPackage("com.sec.android.app.clockpackage"),
                appContext.packageManager.getLaunchIntentForPackage("com.android.deskclock"),
                appContext.packageManager.getLaunchIntentForPackage("com.google.android.deskclock"),
            )
            FollowUpTarget.CALENDAR -> listOf(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALENDAR),
                Intent(Intent.ACTION_VIEW).setData(CalendarContract.CONTENT_URI),
                appContext.packageManager.getLaunchIntentForPackage("com.samsung.android.calendar"),
                appContext.packageManager.getLaunchIntentForPackage("com.android.calendar"),
                appContext.packageManager.getLaunchIntentForPackage("com.google.android.calendar"),
            )
        }
        for (raw in candidates) {
            val intent = raw ?: continue
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (intent.resolveActivity(appContext.packageManager) != null) {
                    appContext.startActivity(intent)
                    return
                }
            } catch (e: Exception) {
                Log.w("FoldClaw", "openFollowUpApp failed for $intent", e)
            }
        }
        Toast.makeText(appContext, "无法打开对应应用，请手动从桌面进入", Toast.LENGTH_SHORT).show()
    }

    fun approvePending(remember: Boolean) {
        approvalGate.respond(approved = true, remember = remember)
    }

    fun denyPending() {
        approvalGate.respond(approved = false, remember = false)
    }

    fun stop() {
        if (_uiState.value.isRecording) {
            recorder.cancel()
            _uiState.update { it.copy(isRecording = false) }
        }
        tts.stop()
        approvalGate.cancelPending()
        orchestrator.cancel()
        runSession.stop()
    }

    override fun onCleared() {
        if (_uiState.value.isRecording) recorder.cancel()
        tts.stop()
        super.onCleared()
    }

    fun toggleHistory() {
        val show = !_uiState.value.showHistory
        _uiState.update { it.copy(showHistory = show) }
        if (show) refreshHistory()
    }

    fun hideHistory() {
        _uiState.update { it.copy(showHistory = false) }
    }

    private fun refreshHistory() {
        viewModelScope.launch {
            val items = historyReader.recentTasks(40).map { it.toUi() }
            _uiState.update { it.copy(history = items) }
        }
    }

    private fun TaskSummary.toUi(): HistoryUiItem {
        val time = timeFmt.format(Date(lastEpochMs))
        val tools = if (toolNames.isEmpty()) "无工具" else toolNames.joinToString(", ")
        return HistoryUiItem(
            taskId = taskId,
            title = instruction,
            subtitle = "$time · $tools",
            stateLabel = finalState ?: "UNKNOWN",
        )
    }

    private fun followUpLabelFor(target: FollowUpTarget?): String? = when (target) {
        FollowUpTarget.CLOCK -> "再次打开时钟检查"
        FollowUpTarget.CALENDAR -> "再次打开日历检查"
        null -> null
    }

    private fun stateToText(state: TaskState, detail: String? = null): String = when (state) {
        TaskState.COMPLETED -> detail?.takeIf { it.isNotBlank() }
            ?: "已完成。若已弹出系统界面，请在对应应用内确认。"
        TaskState.FAILED -> buildString {
            append("失败了，请重试或换个说法。")
            if (!detail.isNullOrBlank()) append("（$detail）")
        }
        TaskState.WAITING_FOR_APPROVAL -> detail ?: "这步需要你确认。"
        TaskState.WAITING_FOR_USER -> detail ?: "需要你补充说明，或换个说法。"
        TaskState.NEEDS_REAUTH -> detail ?: "需要你先登录或解锁，完成后告诉我继续。"
        TaskState.CANCELLED -> detail ?: "已取消。"
        TaskState.PAUSED_LOCKED -> "已暂停（锁屏或权限问题）。"
        TaskState.INTERRUPTED_PROCESS -> "任务被中断，需要重新观察后继续。"
        else -> detail ?: "当前状态: $state"
    }
}
