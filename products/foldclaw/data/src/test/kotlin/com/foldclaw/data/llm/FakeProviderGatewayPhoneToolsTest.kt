package com.foldclaw.data.llm

import com.foldclaw.domain.model.Role
import com.foldclaw.domain.model.NormalizedMessage
import com.foldclaw.domain.model.StreamEvent
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class FakeProviderGatewayPhoneToolsTest {
    private val fake = FakeProviderGateway(latencyMs = 0)

    @Test
    fun `打开淘宝解析为 open_app`() = runTest {
        val events = fake.stream(
            listOf(NormalizedMessage(Role.USER, "打开淘宝")),
            emptyList(),
        ).toList()
        val call = events.filterIsInstance<StreamEvent.ToolCallCompleted>().single()
        assertEquals("open_app", call.name)
        assertTrue(call.argumentsJson.contains("淘宝"))
    }

    @Test
    fun `明天北京天气解析为 get_weather`() = runTest {
        val events = fake.stream(
            listOf(NormalizedMessage(Role.USER, "明天北京的天气")),
            emptyList(),
        ).toList()
        val call = events.filterIsInstance<StreamEvent.ToolCallCompleted>().single()
        assertEquals("get_weather", call.name)
        assertTrue(call.argumentsJson.contains("北京"))
        assertTrue(call.argumentsJson.contains("\"dayOffset\":1"))
    }

    private fun assertTrue(v: Boolean) = org.junit.Assert.assertTrue(v)
}
