package com.foldclaw.policy

import com.foldclaw.domain.model.Result
import com.foldclaw.domain.model.ToolDescriptor
import com.foldclaw.domain.tool.RiskLevel
import com.foldclaw.domain.tool.SensitiveTapLabels
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SensitiveTapPolicyTest {

    @Test
    fun `敏感文案识别`() {
        assertTrue(SensitiveTapLabels.isSensitive("发送"))
        assertTrue(SensitiveTapLabels.isSensitive("立即支付"))
        assertTrue(SensitiveTapLabels.isSensitive("删除账号"))
        assertFalse(SensitiveTapLabels.isSensitive("搜索"))
        assertFalse(SensitiveTapLabels.isSensitive("蓝牙"))
    }

    @Test
    fun `tap_node 带发送文案被策略拒绝`() {
        val env = CapabilityEnvelope.alphaDefault("t1")
        val gate = CapabilityGate(env)
        val tool = ToolDescriptor("tap_node", "", "{}")
        val res = gate.checkTool(
            "tap_node",
            RiskLevel.REVERSIBLE_SIDE_EFFECT,
            tool,
            """{"text":"发送"}""",
        )
        assertTrue(res is Result.Failure)
    }

    @Test
    fun `alphaDefault 含 A11y 工具`() {
        val env = CapabilityEnvelope.alphaDefault("t1")
        assertTrue("get_ui_tree" in env.allowedTools)
        assertTrue("tap_node" in env.allowedTools)
        assertTrue("type_text" in env.allowedTools)
        assertTrue(env.allowedPackages.contains("com.android.settings"))
    }
}
