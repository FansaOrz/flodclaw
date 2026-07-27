package com.foldclaw.data.db

import android.content.Context
import com.foldclaw.domain.agent.InterruptedTask
import com.foldclaw.domain.agent.TaskDetail
import com.foldclaw.domain.agent.TaskHistoryReader
import com.foldclaw.domain.agent.TaskStepDetail
import com.foldclaw.domain.agent.TaskSummary
import com.foldclaw.domain.model.TaskState
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskHistoryReaderImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : TaskHistoryReader {

    private val dao by lazy { FoldClawDatabase.get(context).taskEventDao() }

    private val stale = setOf(
        TaskState.RUNNING.name,
        TaskState.WAITING_FOR_UI.name,
        TaskState.WAITING_FOR_USER.name,
        TaskState.WAITING_FOR_APPROVAL.name,
        TaskState.NEEDS_REOBSERVATION.name,
        TaskState.PAUSED_LOCKED.name,
    )

    override suspend fun recentTasks(limit: Int): List<TaskSummary> {
        val ids = dao.recentTaskIds(limit)
        return ids.map { taskId -> summarize(taskId) }
    }

    override suspend fun taskDetail(taskId: String): TaskDetail? {
        val events = dao.getTimeline(taskId)
        if (events.isEmpty()) return null
        return detailFromEvents(taskId, events)
    }

    override suspend fun recentConversation(limit: Int): List<TaskDetail> {
        // recentTaskIds 为新→旧；对话展示需要旧→新
        return dao.recentTaskIds(limit)
            .asReversed()
            .mapNotNull { taskId -> taskDetail(taskId) }
    }

    override suspend fun markStaleRunningAsInterrupted(): Int {
        var count = 0
        for (taskId in dao.recentTaskIds(20)) {
            val state = lastState(taskId) ?: continue
            if (state in stale) {
                val seq = (dao.maxSeq(taskId) ?: 0L) + 1
                dao.insert(
                    TaskEventEntity(
                        taskId = taskId,
                        seq = seq,
                        type = "state",
                        stateBefore = state,
                        stateAfter = TaskState.INTERRUPTED_PROCESS.name,
                        provider = null,
                        modelId = null,
                        toolName = null,
                        argumentsDigest = null,
                        riskLevel = null,
                        decision = null,
                        approvalTokenId = null,
                        outcome = "进程重建：标记为中断，不自动重放副作用",
                        errorMessage = null,
                        epochMs = System.currentTimeMillis(),
                    ),
                )
                count++
            }
        }
        return count
    }

    override suspend fun latestInterrupted(): InterruptedTask? {
        for (taskId in dao.recentTaskIds(20)) {
            val state = lastState(taskId) ?: continue
            if (state == TaskState.INTERRUPTED_PROCESS.name) {
                val events = dao.getTimeline(taskId)
                val instruction = events.firstOrNull { it.type == "start" }?.outcome
                    ?.takeIf { it.isNotBlank() }
                    ?: "(无指令)"
                return InterruptedTask(taskId, instruction, state)
            }
        }
        return null
    }

    private suspend fun summarize(taskId: String): TaskSummary {
        val events = dao.getTimeline(taskId)
        val instruction = events.firstOrNull { it.type == "start" }?.outcome
            ?.takeIf { it.isNotBlank() }
            ?: "(无指令)"
        val finalState = events.lastOrNull { it.stateAfter.isNotBlank() }?.stateAfter
        val tools = events.mapNotNull { it.toolName }.filter { it.isNotBlank() }.distinct()
        val lastMs = events.maxOfOrNull { it.epochMs } ?: 0L
        return TaskSummary(
            taskId = taskId,
            instruction = instruction,
            finalState = finalState,
            lastEpochMs = lastMs,
            toolNames = tools,
        )
    }

    private fun detailFromEvents(taskId: String, events: List<TaskEventEntity>): TaskDetail {
        val instruction = events.firstOrNull { it.type == "start" }?.outcome
            ?.takeIf { it.isNotBlank() }
            ?: "(无指令)"
        val finalState = events.lastOrNull { it.stateAfter.isNotBlank() }?.stateAfter
        val persistedReply = events.lastOrNull { it.type == "reply" }?.outcome?.takeIf { it.isNotBlank() }
        val fallbackReply = events.lastOrNull { it.type == "tool" }?.outcome?.takeIf { it.isNotBlank() }
            ?: events.lastOrNull { !it.errorMessage.isNullOrBlank() }?.errorMessage
        val steps = events
            .filter { it.type == "tool" && !it.toolName.isNullOrBlank() }
            .mapIndexed { index, e ->
                TaskStepDetail(
                    step = index + 1,
                    toolName = e.toolName.orEmpty(),
                    outcome = e.outcome?.takeIf { it.isNotBlank() } ?: e.toolName.orEmpty(),
                    ok = e.errorMessage.isNullOrBlank(),
                )
            }
        val lastMs = events.maxOfOrNull { it.epochMs } ?: 0L
        return TaskDetail(
            taskId = taskId,
            instruction = instruction,
            finalState = finalState,
            replyText = persistedReply ?: fallbackReply,
            replyPersisted = persistedReply != null,
            lastEpochMs = lastMs,
            steps = steps,
        )
    }

    private suspend fun lastState(taskId: String): String? =
        dao.getTimeline(taskId).lastOrNull { it.stateAfter.isNotBlank() }?.stateAfter
}
