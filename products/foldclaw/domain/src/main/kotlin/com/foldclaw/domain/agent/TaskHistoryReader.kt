package com.foldclaw.domain.agent

/**
 * 历史任务摘要，供 UI 列表展示。
 */
data class TaskSummary(
    val taskId: String,
    val instruction: String,
    val finalState: String?,
    val lastEpochMs: Long,
    val toolNames: List<String>,
)

/**
 * 单次任务详情，用于恢复聊天气泡与步骤时间线。
 */
data class TaskDetail(
    val taskId: String,
    val instruction: String,
    val finalState: String?,
    /** 助手可见文案；若 [replyPersisted] 为 true，则为当时 UI 原文。 */
    val replyText: String?,
    val replyPersisted: Boolean = false,
    val lastEpochMs: Long,
    val steps: List<TaskStepDetail>,
)

data class TaskStepDetail(
    val step: Int,
    val toolName: String,
    val outcome: String,
    val ok: Boolean,
)

/**
 * 进程死亡后发现的中断任务：只恢复状态提示，不自动重放副作用。
 */
data class InterruptedTask(
    val taskId: String,
    val instruction: String,
    val lastState: String,
)

interface TaskHistoryReader {
    suspend fun recentTasks(limit: Int = 30): List<TaskSummary>

    /** 读取单任务详情；不存在返回 null。 */
    suspend fun taskDetail(taskId: String): TaskDetail?

    /**
     * 按时间升序返回最近若干任务详情，用于重启后拼回「上一次对话」。
     */
    suspend fun recentConversation(limit: Int = 20): List<TaskDetail>

    /** 将仍处于 RUNNING/WAITING_* 的任务标记为 INTERRUPTED_PROCESS。 */
    suspend fun markStaleRunningAsInterrupted(): Int

    suspend fun latestInterrupted(): InterruptedTask?
}
