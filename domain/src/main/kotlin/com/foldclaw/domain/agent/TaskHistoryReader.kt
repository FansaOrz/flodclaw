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
 * 进程死亡后发现的中断任务：只恢复状态提示，不自动重放副作用。
 */
data class InterruptedTask(
    val taskId: String,
    val instruction: String,
    val lastState: String,
)

interface TaskHistoryReader {
    suspend fun recentTasks(limit: Int = 30): List<TaskSummary>

    /** 将仍处于 RUNNING/WAITING_* 的任务标记为 INTERRUPTED_PROCESS。 */
    suspend fun markStaleRunningAsInterrupted(): Int

    suspend fun latestInterrupted(): InterruptedTask?
}
