package com.foldclaw.agent.tools

import com.foldclaw.agent.FakeDeviceController
import com.foldclaw.domain.model.ObservationSnapshot
import com.foldclaw.domain.model.Rect
import com.foldclaw.domain.model.UiNode
import com.foldclaw.domain.tool.ToolContext
import com.foldclaw.domain.tool.ToolOutcome
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class A11yToolImplementationsTest {

    private val snap = ObservationSnapshot(
        taskId = "t",
        stepIndex = 0,
        packageName = "com.android.settings",
        windowTitle = "Settings",
        displayId = 0,
        isLocked = false,
        isSecureWindow = false,
        rootId = "n0",
        nodes = mapOf(
            "n0" to UiNode(
                id = "n0",
                parentId = null,
                packageName = "com.android.settings",
                className = "android.widget.FrameLayout",
                text = null,
                contentDescription = null,
                resourceId = null,
                isClickable = false,
                isEditable = false,
                isPassword = false,
                boundsInScreen = Rect(0, 0, 100, 100),
                children = listOf("n1"),
            ),
            "n1" to UiNode(
                id = "n1",
                parentId = "n0",
                packageName = "com.android.settings",
                className = "android.widget.TextView",
                text = "搜索",
                contentDescription = "Search",
                resourceId = "search",
                isClickable = true,
                isEditable = false,
                isPassword = false,
                boundsInScreen = Rect(10, 10, 80, 40),
            ),
            "n2" to UiNode(
                id = "n2",
                parentId = "n0",
                packageName = "com.android.settings",
                className = "android.widget.Button",
                text = "发送",
                contentDescription = null,
                resourceId = null,
                isClickable = true,
                isEditable = false,
                isPassword = false,
                boundsInScreen = Rect(10, 50, 80, 80),
            ),
        ),
        capturedAtEpochMs = 0L,
    )

    private val device = FakeDeviceController(snap)
    private val ctx = ToolContext(
        "t1",
        0,
        snap,
        setOf("com.android.settings"),
        emptySet(),
    )

    @Test
    fun `get_ui_tree 返回摘要`() = runTest {
        val tool = GetUiTreeToolImpl(device)
        val res = tool.execute(ctx, "{}")
        val text = res.getOrNull() as ToolOutcome.Text
        assertTrue(text.text.contains("com.android.settings"))
        assertTrue(text.text.contains("搜索"))
    }

    @Test
    fun `tap 搜索成功`() = runTest {
        val tool = TapNodeToolImpl(device)
        val res = tool.execute(ctx, """{"text":"搜索"}""")
        assertTrue(res.getOrNull() is ToolOutcome.SideEffect)
        assertTrue(device.clicks.contains("n1"))
    }

    @Test
    fun `tap 发送被拒绝`() = runTest {
        val tool = TapNodeToolImpl(device)
        val res = tool.execute(ctx, """{"text":"发送"}""")
        assertTrue(res.getOrNull() is ToolOutcome.Failure)
    }
}
