package com.foldclaw.domain.model

/**
 * 任务状态机转换校验器。纯 Kotlin,无 Android 依赖,可单测。
 *
 * 审查报告 §3.6：进程死亡后恢复为 INTERRUPTED_PROCESS,不自动重放副作用。
 * 所有转换必须显式经此校验,非法转换直接拒绝。
 */
object TaskStateMachine {

    private val transitions: Map<TaskState, Set<TaskState>> = mapOf(
        TaskState.CREATED to setOf(
            TaskState.RUNNING,
            TaskState.CANCELLED,
        ),
        TaskState.RUNNING to setOf(
            TaskState.WAITING_FOR_UI,
            TaskState.WAITING_FOR_USER,
            TaskState.WAITING_FOR_APPROVAL,
            TaskState.PAUSED_LOCKED,
            TaskState.INTERRUPTED_PROCESS,
            TaskState.NEEDS_REAUTH,
            TaskState.NEEDS_REOBSERVATION,
            TaskState.COMPLETED,
            TaskState.FAILED,
            TaskState.CANCELLED,
        ),
        TaskState.WAITING_FOR_UI to setOf(
            TaskState.RUNNING,
            TaskState.PAUSED_LOCKED,
            TaskState.FAILED,
            TaskState.CANCELLED,
        ),
        TaskState.WAITING_FOR_USER to setOf(
            TaskState.RUNNING,
            TaskState.NEEDS_REOBSERVATION,
            TaskState.FAILED,
            TaskState.CANCELLED,
        ),
        TaskState.WAITING_FOR_APPROVAL to setOf(
            TaskState.RUNNING,
            TaskState.FAILED,
            TaskState.CANCELLED,
        ),
        TaskState.PAUSED_LOCKED to setOf(
            TaskState.RUNNING,
            TaskState.INTERRUPTED_PROCESS,
            TaskState.FAILED,
            TaskState.CANCELLED,
        ),
        TaskState.INTERRUPTED_PROCESS to setOf(
            TaskState.NEEDS_REOBSERVATION,
            TaskState.WAITING_FOR_USER,
            TaskState.FAILED,
            TaskState.CANCELLED,
            TaskState.COMPLETED,
        ),
        TaskState.NEEDS_REAUTH to setOf(
            TaskState.NEEDS_REOBSERVATION,
            TaskState.WAITING_FOR_USER,
            TaskState.FAILED,
            TaskState.CANCELLED,
        ),
        TaskState.NEEDS_REOBSERVATION to setOf(
            TaskState.RUNNING,
            TaskState.WAITING_FOR_USER,
            TaskState.FAILED,
            TaskState.CANCELLED,
        ),
        // 终态不可再转换
        TaskState.COMPLETED to emptySet(),
        TaskState.FAILED to emptySet(),
        TaskState.CANCELLED to emptySet(),
    )

    fun canTransition(from: TaskState, to: TaskState): Boolean =
        transitions[from]?.contains(to) == true

    fun transition(from: TaskState, to: TaskState): Result<TaskState> {
        if (from == to) return Result.Success(to)
        return if (canTransition(from, to)) {
            Result.Success(to)
        } else {
            Result.Failure(DomainError(ErrorKind.InvalidStateTransition, "$from → $to 非法"))
        }
    }
}
