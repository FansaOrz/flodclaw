package com.foldclaw.agent.tools

import com.foldclaw.domain.model.ErrorKind
import com.foldclaw.domain.model.Result
import com.foldclaw.domain.tool.AlarmSetTool
import com.foldclaw.domain.tool.CalendarInsertTool
import com.foldclaw.domain.tool.ToolContext
import com.foldclaw.domain.tool.ToolOutcome
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 记录调用参数的 Fake IntentBackend。
 */
class FakeIntentBackend : IntentBackend {
    var lastCalendar: CalendarInsertTool.Args? = null
    var lastAlarm: AlarmSetTool.Args? = null
    var calendarResult: Result<Unit> = Result.Success(Unit)
    var alarmResult: Result<Unit> = Result.Success(Unit)

    override fun createCalendarEvent(args: CalendarInsertTool.Args): Result<Unit> {
        lastCalendar = args
        return calendarResult
    }

    override fun setAlarm(args: AlarmSetTool.Args): Result<Unit> {
        lastAlarm = args
        return alarmResult
    }
}

class CalendarInsertToolImplTest {
    private val backend = FakeIntentBackend()
    private val tool = CalendarInsertToolImpl(backend)
    private val ctx = ToolContext("t1", 0, null, emptySet(), emptySet())

    @Test
    fun `合法参数预填草稿成功`() = runTest {
        val args = """{"title":"团队会议","startEpochMs":1000000,"endEpochMs":2000000,"location":"会议室A"}"""
        val res = tool.execute(ctx, args)
        assertTrue(res.getOrNull() is ToolOutcome.SideEffect)
        val eff = (res.getOrNull() as ToolOutcome.SideEffect)
        assertFalse(eff.irreversible) // 日历是草稿,不直接保存
        assertEquals("团队会议", eff.expectedText)
        assertEquals("团队会议", backend.lastCalendar?.title)
    }

    @Test
    fun `缺标题被拒绝`() = runTest {
        val args = """{"title":"","startEpochMs":1000000}"""
        val res = tool.execute(ctx, args)
        assertTrue(res.errorOrNull()?.kind == ErrorKind.PolicyDenied)
    }

    @Test
    fun `缺开始时间解析失败`() = runTest {
        val args = """{"title":"会"}"""
        val res = tool.execute(ctx, args)
        assertTrue(res.errorOrNull()?.kind == ErrorKind.ProviderInvalidResponse)
    }

    @Test
    fun `非法 JSON 解析失败`() = runTest {
        val res = tool.execute(ctx, "{not json")
        assertTrue(res.errorOrNull()?.kind == ErrorKind.ProviderInvalidResponse)
    }

    @Test
    fun `后端失败返回 Failure outcome`() = runTest {
        backend.calendarResult = Result.Failure(com.foldclaw.domain.model.DomainError(ErrorKind.ActionFailed, "无日历"))
        val args = """{"title":"会","startEpochMs":1000000}"""
        val res = tool.execute(ctx, args)
        assertTrue(res.getOrNull() is ToolOutcome.Failure)
    }
}

class AlarmSetToolImplTest {
    private val backend = FakeIntentBackend()
    private val tool = AlarmSetToolImpl(backend)
    private val ctx = ToolContext("t1", 0, null, emptySet(), emptySet())

    @Test
    fun `合法时间设闹钟成功`() = runTest {
        val args = """{"hour":7,"minutes":30,"label":"起床"}"""
        val res = tool.execute(ctx, args)
        assertTrue(res.getOrNull() is ToolOutcome.SideEffect)
        val eff = (res.getOrNull() as ToolOutcome.SideEffect)
        assertFalse(eff.irreversible) // 系统时钟再确认，FoldClaw 侧视为可逆辅助
        assertEquals(7, backend.lastAlarm?.hour)
        assertEquals(30, backend.lastAlarm?.minutes)
        assertEquals("起床", backend.lastAlarm?.label)
    }

    @Test
    fun `skipUi 永远被强制为 false`() = runTest {
        val args = """{"hour":7,"minutes":30,"skipUi":true}"""
        tool.execute(ctx, args)
        assertFalse("绝不能静默设置闹钟", backend.lastAlarm?.skipUi ?: true)
        // 风险已改为可逆（系统时钟再确认），此处只保证强制展示 UI
    }

    @Test
    fun `非法小时被拒绝`() = runTest {
        val args = """{"hour":25,"minutes":0}"""
        val res = tool.execute(ctx, args)
        assertTrue(res.errorOrNull()?.kind == ErrorKind.PolicyDenied)
    }

    @Test
    fun `非法分钟被拒绝`() = runTest {
        val args = """{"hour":7,"minutes":99}"""
        val res = tool.execute(ctx, args)
        assertTrue(res.errorOrNull()?.kind == ErrorKind.PolicyDenied)
    }

    @Test
    fun `缺分钟解析失败`() = runTest {
        val args = """{"hour":7}"""
        val res = tool.execute(ctx, args)
        assertTrue(res.errorOrNull()?.kind == ErrorKind.ProviderInvalidResponse)
        assertNull(backend.lastAlarm)
    }
}
