package com.foldclaw.domain.model

/**
 * 持久任务状态机。状态来自审查报告 §5：进程死亡后恢复为 INTERRUPTED_PROCESS，
 * 不自动重放副作用。所有转换必须显式经 [TaskStateMachine] 校验。
 */
enum class TaskState {
    /** 已创建，未启动。 */
    CREATED,

    /** 正在执行某一步。 */
    RUNNING,

    /** 等待 UI 变化稳定以重新观察。 */
    WAITING_FOR_UI,

    /** 等待用户输入或接管。 */
    WAITING_FOR_USER,

    /** 等待审批令牌。 */
    WAITING_FOR_APPROVAL,

    /** 锁屏或权限被撤销，已暂停。 */
    PAUSED_LOCKED,

    /** 进程死亡后恢复，需重新观察并确认。 */
    INTERRUPTED_PROCESS,

    /** 需要重新认证（登录/解锁等），由用户处理。 */
    NEEDS_REAUTH,

    /** 需要重新观察当前页面（坐标/节点已失效）。 */
    NEEDS_REOBSERVATION,

    /** 用户取消。 */
    CANCELLED,

    /** 完成。 */
    COMPLETED,

    /** 失败。 */
    FAILED,
}

/**
 * 是否处于可恢复的活跃态（而非终态）。
 */
val TaskState.isActive: Boolean
    get() = this in setOf(
        TaskState.CREATED,
        TaskState.RUNNING,
        TaskState.WAITING_FOR_UI,
        TaskState.WAITING_FOR_USER,
        TaskState.WAITING_FOR_APPROVAL,
        TaskState.PAUSED_LOCKED,
        TaskState.INTERRUPTED_PROCESS,
        TaskState.NEEDS_REAUTH,
        TaskState.NEEDS_REOBSERVATION,
    )

val TaskState.isTerminal: Boolean
    get() = this in setOf(TaskState.COMPLETED, TaskState.FAILED, TaskState.CANCELLED)
