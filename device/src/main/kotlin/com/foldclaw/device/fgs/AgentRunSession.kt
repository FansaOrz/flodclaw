package com.foldclaw.device.fgs

import android.content.Context
import android.content.Intent
import android.util.Log
import com.foldclaw.agent.AgentOrchestrator
import com.foldclaw.agent.OrchestratorEvent
import com.foldclaw.device.hud.AgentProgressHud
import com.foldclaw.domain.model.TaskState
import com.foldclaw.domain.tool.ToolOutcome
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 封装 FGS / 步骤悬窗 / 结束后回前台，供 Presentation 侧调用。
 */
@Singleton
class AgentRunSession @Inject constructor(
    @ApplicationContext private val context: Context,
    private val orchestrator: AgentOrchestrator,
    private val hud: AgentProgressHud,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var eventsJob: Job? = null

    @Volatile
    private var active = false

    fun start(instruction: String) {
        active = true
        AgentRunForegroundService.start(context, instruction)
        hud.showRunning(instruction)
        eventsJob?.cancel()
        eventsJob = scope.launch {
            orchestrator.events.collect { event ->
                if (!active) return@collect
                when (event) {
                    is OrchestratorEvent.StepStarted -> {
                        val line = "第 ${event.step + 1} 步 · ${AgentProgressHud.friendlyTool(event.toolName)}"
                        hud.updateStep(event.step, event.toolName)
                        AgentRunForegroundService.update(context, line)
                    }
                    is OrchestratorEvent.StepCompleted -> {
                        val detail = when (val o = event.outcome) {
                            is ToolOutcome.SideEffect -> o.summary
                            is ToolOutcome.Text -> o.text
                            is ToolOutcome.Failure -> o.error.reason
                            is ToolOutcome.NeedsUserHandoff -> o.reason
                        }
                        hud.updateStep(event.step, event.toolName, detail)
                        AgentRunForegroundService.update(
                            context,
                            "第 ${event.step + 1} 步完成 · ${AgentProgressHud.friendlyTool(event.toolName)}",
                        )
                    }
                    is OrchestratorEvent.StateChanged -> {
                        if (event.state == TaskState.WAITING_FOR_APPROVAL) {
                            hud.showStatus("等待你确认", "请回到 FoldClaw 审批")
                            AgentRunForegroundService.update(context, "等待确认")
                            bringAppToFront()
                        }
                    }
                    is OrchestratorEvent.Error -> Unit
                }
            }
        }
    }

    /**
     * 任务结束：提示终态 → 回到 FoldClaw → 收起 FGS/悬窗。
     */
    fun finish(state: TaskState, summary: String?) {
        if (!active) {
            AgentRunForegroundService.stop(context)
            return
        }
        active = false
        eventsJob?.cancel()
        eventsJob = null

        val ok = state == TaskState.COMPLETED
        val message = summary?.takeIf { it.isNotBlank() } ?: stateLabel(state)
        hud.showTerminal(ok, message)

        scope.launch {
            // 稍等用户看清最后一步，再拉回本 App
            delay(700)
            bringAppToFront()
            delay(1_400)
            AgentRunForegroundService.stop(context)
            // 终态文案再留一会儿，由 Hud 自己延时 detach
        }
    }

    fun stop() {
        if (!active) {
            AgentRunForegroundService.stop(context)
            hud.hide()
            return
        }
        finish(TaskState.CANCELLED, "已取消")
    }

    private fun bringAppToFront() {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        launch.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED,
        )
        runCatching { context.startActivity(launch) }
            .onFailure { Log.w(TAG, "bringAppToFront failed", it) }
    }

    private fun stateLabel(state: TaskState): String = when (state) {
        TaskState.COMPLETED -> "任务已完成"
        TaskState.FAILED -> "任务失败"
        TaskState.CANCELLED -> "已取消"
        TaskState.WAITING_FOR_APPROVAL -> "等待确认"
        TaskState.WAITING_FOR_USER -> "需要你补充"
        TaskState.NEEDS_REAUTH -> "需要登录或解锁"
        TaskState.PAUSED_LOCKED -> "已暂停（锁屏）"
        TaskState.INTERRUPTED_PROCESS -> "任务被中断"
        else -> "状态：$state"
    }

    companion object {
        private const val TAG = "AgentRunSession"
    }
}
