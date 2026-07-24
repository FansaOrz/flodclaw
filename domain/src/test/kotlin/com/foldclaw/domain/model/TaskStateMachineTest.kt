package com.foldclaw.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskStateMachineTest {

    @Test
    fun `CREATED 转 RUNNING 合法`() {
        assertTrue(TaskStateMachine.canTransition(TaskState.CREATED, TaskState.RUNNING))
        assertTrue(TaskStateMachine.transition(TaskState.CREATED, TaskState.RUNNING).getOrNull() == TaskState.RUNNING)
    }

    @Test
    fun `RUNNING 转 INTERRUPTED_PROCESS 合法（进程死亡恢复）`() {
        assertTrue(TaskStateMachine.canTransition(TaskState.RUNNING, TaskState.INTERRUPTED_PROCESS))
    }

    @Test
    fun `INTERRUPTED_PROCESS 不能直接转回 RUNNING（必须先重新观察）`() {
        // 审查报告 §3.6:恢复后必须重新观察,不直接续跑
        assertFalse(TaskStateMachine.canTransition(TaskState.INTERRUPTED_PROCESS, TaskState.RUNNING))
        assertTrue(TaskStateMachine.canTransition(TaskState.INTERRUPTED_PROCESS, TaskState.NEEDS_REOBSERVATION))
    }

    @Test
    fun `COMPLETED 是终态不可再转换`() {
        for (target in TaskState.values()) {
            assertFalse("COMPLETED 不应能转 $target", TaskStateMachine.canTransition(TaskState.COMPLETED, target))
        }
    }

    @Test
    fun `FAILED 与 CANCELLED 是终态`() {
        assertTrue(TaskState.FAILED.isTerminal)
        assertTrue(TaskState.CANCELLED.isTerminal)
        assertFalse(TaskState.RUNNING.isTerminal)
    }

    @Test
    fun `非法转换返回 Failure`() {
        val res = TaskStateMachine.transition(TaskState.INTERRUPTED_PROCESS, TaskState.RUNNING)
        assertTrue(res.errorOrNull()?.kind == ErrorKind.InvalidStateTransition)
    }

    @Test
    fun `WAITING_FOR_APPROVAL 只能转 RUNNING 或 FAILED 或 CANCELLED`() {
        assertTrue(TaskStateMachine.canTransition(TaskState.WAITING_FOR_APPROVAL, TaskState.RUNNING))
        assertTrue(TaskStateMachine.canTransition(TaskState.WAITING_FOR_APPROVAL, TaskState.FAILED))
        assertTrue(TaskStateMachine.canTransition(TaskState.WAITING_FOR_APPROVAL, TaskState.CANCELLED))
        // 不能直接 COMPLETED（审批不是成功）
        assertFalse(TaskStateMachine.canTransition(TaskState.WAITING_FOR_APPROVAL, TaskState.COMPLETED))
    }

    @Test
    fun `自身转换合法（幂等）`() {
        assertTrue(TaskStateMachine.transition(TaskState.RUNNING, TaskState.RUNNING).getOrNull() == TaskState.RUNNING)
    }
}
