package com.foldclaw.policy

import com.foldclaw.domain.tool.CalendarInsertTool
import com.foldclaw.domain.tool.RiskLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PolicyEngineTest {

    private fun engine(allowSideEffects: Boolean = false, packages: Set<String> = setOf("com.android.calendar")): PolicyEngine {
        val envelope = CapabilityEnvelope.alphaDefault("t1", packages).copy(allowSideEffects = allowSideEffects)
        return PolicyEngine(envelope)
    }

    @Test
    fun `REVERSIBLE_SIDE_EFFECT 在 allowSideEffects=false 时要求审批`() {
        val e = engine(allowSideEffects = false)
        val res = e.evaluate(CalendarInsertTool.NAME, RiskLevel.REVERSIBLE_SIDE_EFFECT, CalendarInsertTool.descriptor)
        assertTrue(res.getOrNull() is PolicyDecision.RequireApproval)
    }

    @Test
    fun `REVERSIBLE_SIDE_EFFECT 在 allowSideEffects=true 时允许`() {
        val e = engine(allowSideEffects = true)
        val res = e.evaluate(CalendarInsertTool.NAME, RiskLevel.REVERSIBLE_SIDE_EFFECT, CalendarInsertTool.descriptor)
        assertTrue(res.getOrNull() is PolicyDecision.Allow)
    }

    @Test
    fun `闹钟 REVERSIBLE 在 allowSideEffects=true 时免确认`() {
        val e = engine(allowSideEffects = true)
        val res = e.evaluate(
            "set_alarm",
            RiskLevel.REVERSIBLE_SIDE_EFFECT,
            com.foldclaw.domain.tool.AlarmSetTool.descriptor,
        )
        assertTrue(res.getOrNull() is PolicyDecision.Allow)
    }

    @Test
    fun `CRITICAL 工具即使 allowSideEffects=true 也要求审批`() {
        val env = CapabilityEnvelope.alphaDefault("t1", emptySet())
            .copy(allowedTools = setOf("pay", "set_alarm"), allowSideEffects = true)
        val e = PolicyEngine(env)
        val descriptor = com.foldclaw.domain.model.ToolDescriptor("pay", "支付", "{}")
        val res = e.evaluate("pay", RiskLevel.IRREVERSIBLE_SIDE_EFFECT, descriptor)
        assertTrue(res.getOrNull() is PolicyDecision.RequireApproval)
    }

    @Test
    fun `READ_ONLY 直接允许`() {
        val env = CapabilityEnvelope.alphaDefault("t1", setOf("com.android.calendar"))
            .copy(allowedTools = setOf("get_ui_tree", "create_calendar_event"))
        val e = PolicyEngine(env)
        val descriptor = com.foldclaw.domain.model.ToolDescriptor("get_ui_tree", "", "{}")
        val res = e.evaluate("get_ui_tree", RiskLevel.READ_ONLY, descriptor)
        assertTrue(res.getOrNull() is PolicyDecision.Allow)
    }

    @Test
    fun `不在白名单的工具被拒绝`() {
        val e = engine()
        val descriptor = com.foldclaw.domain.model.ToolDescriptor("evil_tool", "", "{}")
        val res = e.evaluate("evil_tool", RiskLevel.READ_ONLY, descriptor)
        assertTrue(res.errorOrNull()?.kind == com.foldclaw.domain.model.ErrorKind.PolicyCapabilityExceeded)
    }

    @Test
    fun `秘密数据被永久阻断`() {
        val e = engine()
        assertTrue(e.isSecretBlocked("我的 OTP 是 123456"))
        assertTrue(e.isSecretBlocked("password is hunter2"))
        assertTrue(e.isSecretBlocked("cvv 123"))
        assertTrue(e.isSecretBlocked("api key sk-xxxx"))
        assertTrue(e.isSecretBlocked("助记词 abandon ability"))
        assertFalse(e.isSecretBlocked("明天下午三点建日程"))
    }

    @Test
    fun `目标包名不在白名单被拒绝`() {
        val e = engine(packages = setOf("com.android.calendar"))
        assertTrue(e.checkTargetPackage("com.android.calendar").getOrNull() != null)
        assertTrue(e.checkTargetPackage("com.evil.bank").errorOrNull()?.kind == com.foldclaw.domain.model.ErrorKind.PolicyDenied)
    }

    @Test
    fun `空包名校验通过`() {
        val e = engine()
        assertTrue(e.checkTargetPackage(null).getOrNull() != null)
    }

    @Test
    fun `alphaDefault 信封上限合理`() {
        val env = CapabilityEnvelope.alphaDefault("t1", emptySet())
        assertEquals(12, env.maxSteps)
        assertEquals(120_000L, env.maxDurationMs)
        assertEquals(80_000, env.maxTokens)
        assertEquals(0.35, env.maxCostUsd, 0.001)
        assertTrue(env.allowSideEffects)
    }
}
