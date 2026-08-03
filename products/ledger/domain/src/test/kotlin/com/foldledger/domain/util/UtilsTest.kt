package com.foldledger.domain.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UtilsTest {
    @Test
    fun fenToYuan() {
        assertEquals("12.34", MoneyFormat.fenToYuan(1234))
        assertEquals("0.01", MoneyFormat.fenToYuan(1))
    }

    @Test
    fun fingerprintStableInWindow() {
        val a = Fingerprint.of("pkg", 100, "商户A", 1_000_000)
        val b = Fingerprint.of("pkg", 100, "商户A", 1_000_000 + 30_000)
        assertEquals(a, b)
        val c = Fingerprint.of("pkg", 100, "商户A", 1_000_000 + 150_000)
        assertTrue(a != c)
    }
}
