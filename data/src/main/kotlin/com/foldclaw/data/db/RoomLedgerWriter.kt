package com.foldclaw.data.db

import android.content.Context
import com.foldclaw.domain.agent.LedgerWriter
import com.foldclaw.domain.model.DomainError
import com.foldclaw.domain.model.TaskState
import com.foldclaw.domain.security.Redactor
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * LedgerWriter 的 Room 实现。把领域事件翻译成 TaskEventEntity 追加写。
 *
 * 单调序号由 seq + 1 保证;同一 taskId 下用 Mutex 串行化,避免并发写乱序。
 * 不存秘密原文:错误消息由调用方保证脱敏。
 */
@Singleton
class RoomLedgerWriter @Inject constructor(
    @ApplicationContext private val context: Context,
) : LedgerWriter {

    private val mutex = Mutex()
    private val dao by lazy { FoldClawDatabase.get(context).taskEventDao() }

    override suspend fun writeStarted(taskId: String, instruction: String) {
        append(
            taskId,
            type = "start",
            stateBefore = null,
            stateAfter = TaskState.RUNNING.name,
            step = 0,
            outcome = Redactor.redact(instruction, maxLen = 200),
        )
    }

    override suspend fun writeState(taskId: String, step: Int, state: TaskState) {
        append(taskId, type = "state", stateBefore = null, stateAfter = state.name, step = step)
    }

    override suspend fun writeTool(taskId: String, step: Int, toolName: String, outcome: String) {
        append(
            taskId,
            type = "tool",
            stateBefore = null,
            stateAfter = null,
            step = step,
            toolName = toolName,
            outcome = Redactor.redact(outcome, maxLen = 200),
        )
    }

    override suspend fun writeCancel(taskId: String, step: Int) {
        append(taskId, type = "cancel", stateBefore = null, stateAfter = TaskState.CANCELLED.name, step = step)
    }

    override suspend fun writeError(taskId: String, step: Int, error: DomainError) {
        append(
            taskId,
            type = "error",
            stateBefore = null,
            stateAfter = null,
            step = step,
            errorMessage = Redactor.redact("${error.kind.name}: ${error.reason}", maxLen = 240),
        )
    }

    override suspend fun writeFailure(taskId: String, step: Int, reason: String) {
        append(
            taskId,
            type = "failure",
            stateBefore = null,
            stateAfter = TaskState.FAILED.name,
            step = step,
            errorMessage = Redactor.redact(reason, maxLen = 240),
        )
    }

    override suspend fun writeVerification(taskId: String, step: Int, ok: Boolean) {
        append(
            taskId,
            type = "verification",
            stateBefore = null,
            stateAfter = null,
            step = step,
            outcome = if (ok) "verified" else "verification_failed",
        )
    }

    private suspend fun append(
        taskId: String,
        type: String,
        stateBefore: String?,
        stateAfter: String?,
        step: Int,
        toolName: String? = null,
        argumentsDigest: String? = null,
        riskLevel: String? = null,
        decision: String? = null,
        approvalTokenId: String? = null,
        outcome: String? = null,
        errorMessage: String? = null,
    ) = mutex.withLock {
        val maxSeq = dao.maxSeq(taskId) ?: -1
        dao.insert(
            TaskEventEntity(
                taskId = taskId,
                seq = maxSeq + 1,
                type = type,
                stateBefore = stateBefore ?: "",
                stateAfter = stateAfter ?: "",
                provider = null,
                modelId = null,
                toolName = toolName,
                argumentsDigest = argumentsDigest,
                riskLevel = riskLevel,
                decision = decision,
                approvalTokenId = approvalTokenId,
                outcome = outcome,
                errorMessage = errorMessage,
                epochMs = System.currentTimeMillis(),
            )
        )
    }
}
