package com.foldclaw.domain.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RedactorTest {
    @Test
    fun `遮蔽 sk key`() {
        val out = Redactor.redact("key=sk-abcdefghijklmnop rest")
        assertTrue(out.contains("sk-***"))
        assertFalse(out.contains("sk-abcdefghijklmnop"))
    }

    @Test
    fun `遮蔽 bearer`() {
        val out = Redactor.redact("Authorization: Bearer abcdefghijklmnop")
        assertTrue(out.contains("***"))
        assertFalse(out.contains("abcdefghijklmnop"))
    }

    @Test
    fun `brief 截断`() {
        val out = Redactor.brief("a".repeat(100), maxLen = 20)
        assertTrue(out.length <= 21)
    }
}
