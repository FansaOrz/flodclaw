package com.foldclaw.domain.agent

import com.foldclaw.domain.model.DomainError
import com.foldclaw.domain.model.TaskState

/**
 * 账本写入接口。domain 层纯 Kotlin 抽象,不绑定 Android。
 *
 * Orchestrator(agent) 依赖它;真实实现 RoomLedgerWriter(data) 也依赖它,
 * 两者都只绑 domain,避免 data↔agent 循环。
 */
interface LedgerWriter {
    /** 任务开始，写入用户指令摘要（供历史列表）。 */
    suspend fun writeStarted(taskId: String, instruction: String)
    suspend fun writeState(taskId: String, step: Int, state: TaskState)
    suspend fun writeCancel(taskId: String, step: Int)
    suspend fun writeError(taskId: String, step: Int, error: DomainError)
    suspend fun writeFailure(taskId: String, step: Int, reason: String)
    suspend fun writeVerification(taskId: String, step: Int, ok: Boolean)
    /** 记录已执行的工具名，便于历史与「打开应用检查」。 */
    suspend fun writeTool(taskId: String, step: Int, toolName: String, outcome: String)
}
