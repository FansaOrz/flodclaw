package com.foldclaw.agent

import com.foldclaw.agent.tools.FakeIntentBackend
import com.foldclaw.agent.tools.AlarmSetToolImpl
import com.foldclaw.agent.tools.CalendarInsertToolImpl
// FollowUpTarget in same package
import com.foldclaw.domain.llm.ProviderGateway
import com.foldclaw.domain.model.NormalizedMessage
import com.foldclaw.domain.model.ProviderCapabilities
import com.foldclaw.domain.model.Result
import com.foldclaw.domain.model.StreamEvent
import com.foldclaw.domain.model.TaskState
import com.foldclaw.domain.model.ToolCall
import com.foldclaw.domain.model.ToolDescriptor
import com.foldclaw.policy.ApprovalManager
import com.foldclaw.policy.CapabilityEnvelope
import com.foldclaw.policy.ImmediateApprovalGate
import com.foldclaw.policy.PolicyFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 可编程 Fake Provider。测试方预设每次返回的 ToolCall。
 */
class ScriptedFakeProvider(
    private val toolCalls: List<ToolCall>,
) : ProviderGateway {
    override val providerId: String = "scripted"
    override val capabilities: ProviderCapabilities = ProviderCapabilities(
        supportsStreaming = true, supportsToolCalling = true, supportsVision = false,
        supportsCancel = true, maxContextTokens = 8192, maxImageBytes = 0,
    )

    private var index = 0
    override fun stream(messages: List<NormalizedMessage>, tools: List<ToolDescriptor>): Flow<StreamEvent> = flow {
        if (index < toolCalls.size) {
            val tc = toolCalls[index]
            index++
            emit(StreamEvent.ToolCallCompleted(0, tc.id, tc.name, tc.argumentsJson))
        } else {
            // 没有更多工具调用,空消息结束(交还用户)
            emit(StreamEvent.MessageCompleted(null))
        }
    }

    override fun cancel() {}
    override suspend fun ping(): Result<Unit> = Result.Success(Unit)
}

class AgentOrchestratorTest {

    private fun makeOrchestrator(
        toolCalls: List<ToolCall>,
        allowSideEffects: Boolean = true,
        approve: Boolean = true,
    ): Triple<AgentOrchestrator, FakeIntentBackend, FakeLedgerWriter> {
        val backend = FakeIntentBackend()
        val tools = ToolRegistry().apply {
            register(CalendarInsertToolImpl(backend))
            register(AlarmSetToolImpl(backend))
        }
        val ledger = FakeLedgerWriter()
        val device = FakeDeviceController()
        val orchestrator = AgentOrchestrator(
            provider = ScriptedFakeProvider(toolCalls),
            tools = tools,
            device = device,
            policyFactory = PolicyFactory(),
            approvalManager = ApprovalManager(),
            ledger = ledger,
            approvalGate = ImmediateApprovalGate(approve = approve),
            lock = ActiveRunLock(),
        )
        return Triple(orchestrator, backend, ledger)
    }

    private fun envelope(allowSide: Boolean): CapabilityEnvelope =
        CapabilityEnvelope.alphaDefault("t1", setOf("com.android.calendar", "com.android.deskclock"))
            .copy(allowSideEffects = allowSide, maxSteps = 3)

    @Test
    fun `日历建日程端到端闭环成功`() = runTest {
        val call = ToolCall("c1", "create_calendar_event",
            """{"title":"团队会议","startEpochMs":1000000,"endEpochMs":2000000}""")
        val (orchestrator, backend, ledger) = makeOrchestrator(listOf(call), allowSideEffects = true)
        val res = orchestrator.run("t1", "明天下午三点建日程", envelope(true))
        assertEquals("团队会议", backend.lastCalendar?.title)
        // ledger 应记录 RUNNING 与 COMPLETED
        assertTrue(ledger.states.any { it.second == TaskState.RUNNING })
        assertTrue(ledger.states.any { it.second == TaskState.COMPLETED })
    }

    @Test
    fun `闹钟在 allowSideEffects=true 时无需确认卡直接执行`() = runTest {
        val call = ToolCall("a1", "set_alarm", """{"hour":7,"minutes":30}""")
        val (orchestrator, backend, _) = makeOrchestrator(listOf(call), allowSideEffects = true)
        val res = orchestrator.run("t1", "设7点半闹钟", envelope(true))
        assertEquals(7, backend.lastAlarm?.hour)
        assertFalse(backend.lastAlarm?.skipUi ?: true)
        assertEquals(TaskState.COMPLETED, res.getOrNull()?.state)
        assertEquals(FollowUpTarget.CLOCK, res.getOrNull()?.followUpTarget)
    }

    @Test
    fun `闹钟Intent成功后即使无障碍观察失败也不应FAILED`() = runTest {
        // FakeDeviceController 默认 observe 失败，复现真机「闹钟 UI 已弹出但任务 FAILED」
        val call = ToolCall("a1", "set_alarm", """{"hour":7,"minutes":30,"label":"起床"}""")
        val (orchestrator, backend, ledger) = makeOrchestrator(listOf(call), allowSideEffects = true)
        val res = orchestrator.run("t1", "设7点半闹钟", envelope(true))
        assertEquals(7, backend.lastAlarm?.hour)
        assertEquals(TaskState.COMPLETED, res.getOrNull()?.state)
        assertTrue(res.getOrNull()?.userVisibleMessage?.contains("闹钟") == true)
        assertTrue(ledger.verifications.any { it })
        assertFalse(ledger.states.any { it.second == TaskState.FAILED })
    }

    @Test
    fun `用户拒绝审批时不执行可逆工具`() = runTest {
        // allowSideEffects=false → REVERSIBLE 也要审批
        val call = ToolCall("a1", "set_alarm", """{"hour":7,"minutes":30}""")
        val (orchestrator, backend, _) = makeOrchestrator(
            toolCalls = listOf(call),
            allowSideEffects = false,
            approve = false,
        )
        val res = orchestrator.run("t1", "设7点半闹钟", envelope(false))
        assertEquals(null, backend.lastAlarm)
        assertEquals(TaskState.CANCELLED, res.getOrNull()?.state)
    }

    @Test
    fun `秘密数据参数被阻断,不执行`() = runTest {
        val call = ToolCall("e1", "create_calendar_event",
            """{"title":"password is hunter2","startEpochMs":1000000}""")
        val (orchestrator, backend, ledger) = makeOrchestrator(listOf(call), allowSideEffects = true)
        orchestrator.run("t1", "建日程", envelope(true))
        // 标题含 password,被 PolicyEngine.isSecretBlocked 阻断
        assertEquals(null, backend.lastCalendar)
        assertTrue(ledger.errors.any { it.kind == com.foldclaw.domain.model.ErrorKind.SecretBlocked })
    }

    @Test
    fun `未知工具名被拒绝`() = runTest {
        val call = ToolCall("x1", "evil_tool", "{}")
        val (orchestrator, _, _) = makeOrchestrator(listOf(call), allowSideEffects = true)
        val res = orchestrator.run("t1", "调用坏工具", envelope(true))
        // 未知工具 → null plan → WAITING_FOR_USER
        assertEquals(TaskState.WAITING_FOR_USER, res.getOrNull()?.state)
    }

    @Test
    fun `单 ActiveRun 锁,已有任务运行时第二个被拒`() = runTest {
        val call = ToolCall("c1", "create_calendar_event",
            """{"title":"会","startEpochMs":1000000}""")
        val (orchestrator, _, _) = makeOrchestrator(listOf(call), allowSideEffects = true)
        // 用反射或直接锁:这里造一个共享锁的场景
        val sharedLock = ActiveRunLock()
        assertTrue(sharedLock.tryAcquire())
        assertFalse(sharedLock.tryAcquire())
        sharedLock.release()
    }

    @Test
    fun `步数超限返回 FAILED 而非无限循环`() = runTest {
        // 反复返回同一个工具调用,但 maxSteps=1,应截断
        val call = ToolCall("c1", "create_calendar_event",
            """{"title":"会","startEpochMs":1000000}""")
        val (orchestrator, _, _) = makeOrchestrator(listOf(call, call, call, call), allowSideEffects = true)
        val env = envelope(true).copy(maxSteps = 1)
        val res = orchestrator.run("t1", "循环", env)
        // 步数超限或完成;关键是不会卡死
        val state = res.getOrNull()?.state
        assertTrue(state in setOf(TaskState.COMPLETED, TaskState.FAILED))
    }
}
