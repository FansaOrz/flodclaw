package com.foldclaw.policy

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApprovalManagerTest {

    private val mgr = ApprovalManager(ttlMs = 60_000L)
    private val now = 1_000_000L

    @Test
    fun `参数未变且未过期时校验通过`() {
        val args = "{\"hour\":9,\"minutes\":30}"
        val token = mgr.issue("set_alarm", "com.android.deskclock", args, null, 0, now)
        assertTrue(mgr.validate(token, args, "com.android.deskclock", null, 0, now + 10))
    }

    @Test
    fun `参数变了校验失败（防 TOCTOU）`() {
        val args = "{\"hour\":9,\"minutes\":30}"
        val token = mgr.issue("set_alarm", "com.android.deskclock", args, null, 0, now)
        // 攻击者在审批后改了分钟
        assertFalse(mgr.validate(token, "{\"hour\":9,\"minutes\":31}", "com.android.deskclock", null, 0, now + 10))
    }

    @Test
    fun `目标包名变了校验失败`() {
        val args = "{\"hour\":9}"
        val token = mgr.issue("set_alarm", "com.android.deskclock", args, null, 0, now)
        assertFalse(mgr.validate(token, args, "com.evil.bank", null, 0, now + 10))
    }

    @Test
    fun `窗口标题变了校验失败`() {
        val args = "{}"
        val token = mgr.issue("set_alarm", "com.android.deskclock", args, "时钟", 0, now)
        assertFalse(mgr.validate(token, args, "com.android.deskclock", "伪装时钟", 0, now + 10))
    }

    @Test
    fun `过期校验失败`() {
        val args = "{}"
        val token = mgr.issue("set_alarm", null, args, null, 0, now)
        assertFalse(mgr.validate(token, args, null, null, 0, now + 61_000))
    }

    @Test
    fun `令牌一次性,二次校验失败`() {
        val args = "{}"
        val token = mgr.issue("set_alarm", null, args, null, 0, now)
        assertTrue(mgr.validate(token, args, null, null, 0, now + 10))
        // 已消费,二次失败
        assertFalse(mgr.validate(token, args, null, null, 0, now + 20))
    }

    @Test
    fun `令牌不存明文参数,只存摘要`() {
        val args = "{\"label\":\"上班\"}"
        val token = mgr.issue("set_alarm", null, args, null, 0, now)
        // argumentsJson 是摘要,不含原文
        assertTrue(token.argumentsDigest.matches(Regex("^[0-9a-f]{64}$")))
        assertFalse(token.argumentsDigest.contains("上班"))
    }

    @Test
    fun `签发时包名为null则不因后续出现包名而失败`() {
        val args = "{\"hour\":7}"
        val token = mgr.issue("set_alarm", null, args, null, 0, now)
        assertTrue(mgr.validate(token, args, "com.sec.android.app.clockpackage", null, 0, now + 10))
    }
}
