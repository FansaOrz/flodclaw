package com.foldclaw.data.llm

import com.foldclaw.domain.model.NormalizedMessage
import com.foldclaw.domain.model.Role
import com.foldclaw.domain.model.StreamEvent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeProviderGatewayTest {

    private val provider = FakeProviderGateway(latencyMs = 0)

    private fun stream(events: List<StreamEvent>): List<StreamEvent> = events

    @Test
    fun `建日程指令解析成 create_calendar_event 工具调用`() = runTest {
        val msgs = listOf(NormalizedMessage(Role.USER, "明天下午三点建团队会议日程"))
        val events = provider.stream(msgs, emptyList()).toList()
        val completed = events.filterIsInstance<StreamEvent.ToolCallCompleted>().first()
        assertEquals("create_calendar_event", completed.name)
        // extractTitle 去掉关键词后应留下非空标题（如"团队"）
        assertTrue("应包含标题字段", completed.argumentsJson.contains("\"title\""))
        assertTrue(completed.argumentsJson.contains("startEpochMs"))
    }

    @Test
    fun `闹钟指令解析成 set_alarm 工具调用`() = runTest {
        val msgs = listOf(NormalizedMessage(Role.USER, "帮我设7点半起床闹钟"))
        val events = provider.stream(msgs, emptyList()).toList()
        val completed = events.filterIsInstance<StreamEvent.ToolCallCompleted>().first()
        assertEquals("set_alarm", completed.name)
        assertTrue(completed.argumentsJson.contains("hour"))
        assertTrue(completed.argumentsJson.contains("minutes"))
    }

    @Test
    fun `天气指令解析成 get_weather`() = runTest {
        val msgs = listOf(NormalizedMessage(Role.USER, "今天天气怎么样"))
        val events = provider.stream(msgs, emptyList()).toList()
        val completed = events.filterIsInstance<StreamEvent.ToolCallCompleted>().first()
        assertEquals("get_weather", completed.name)
    }

    @Test
    fun `无法识别指令返回文本而非工具调用`() = runTest {
        val msgs = listOf(NormalizedMessage(Role.USER, "讲个笑话"))
        val events = provider.stream(msgs, emptyList()).toList()
        assertTrue(events.any { it is StreamEvent.TextDelta })
        assertTrue(events.none { it is StreamEvent.ToolCallCompleted })
    }

    @Test
    fun `流以 MessageCompleted 结尾`() = runTest {
        val msgs = listOf(NormalizedMessage(Role.USER, "设8点闹钟"))
        val events = provider.stream(msgs, emptyList()).toList()
        assertTrue(events.last() is StreamEvent.MessageCompleted)
    }

    @Test
    fun `闹钟参数中的小时被正确提取`() = runTest {
        val msgs = listOf(NormalizedMessage(Role.USER, "下午3点设闹钟"))
        val events = provider.stream(msgs, emptyList()).toList()
        val completed = events.filterIsInstance<StreamEvent.ToolCallCompleted>().first()
        // 下午3点 = 15
        assertTrue(completed.argumentsJson.contains("\"hour\":15"))
    }

    @Test
    fun `ping 始终成功`() = runTest {
        assertEquals(Unit, (provider.ping() as com.foldclaw.domain.model.Result.Success).data)
    }

    @Test
    fun `收到tool结果后不再重复发起工具调用`() = runTest {
        val msgs = listOf(
            NormalizedMessage(Role.USER, "帮我设7点闹钟"),
            NormalizedMessage(Role.TOOL, "已打开闹钟设置 7:00", toolCallId = "call_1"),
        )
        val events = provider.stream(msgs, emptyList()).toList()
        assertTrue(events.none { it is StreamEvent.ToolCallCompleted })
        assertTrue(events.any { it is StreamEvent.TextDelta })
        assertTrue(events.last() is StreamEvent.MessageCompleted)
    }
}
