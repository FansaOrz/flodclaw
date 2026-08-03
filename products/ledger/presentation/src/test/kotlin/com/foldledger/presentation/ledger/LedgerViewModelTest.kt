package com.foldledger.presentation.ledger

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LedgerViewModelTest {
    @Test
    fun parseYuanToFen_usesExactDecimalConversion() {
        assertEquals(1L, LedgerViewModel.parseYuanToFen("0.01"))
        assertEquals(1_230L, LedgerViewModel.parseYuanToFen("12.3"))
        assertEquals(9_999_999L, LedgerViewModel.parseYuanToFen("99999.99"))
    }

    @Test
    fun parseYuanToFen_rejectsInvalidOrSubCentValues() {
        assertNull(LedgerViewModel.parseYuanToFen(""))
        assertNull(LedgerViewModel.parseYuanToFen("0"))
        assertNull(LedgerViewModel.parseYuanToFen("-12.00"))
        assertNull(LedgerViewModel.parseYuanToFen("1.001"))
        assertNull(LedgerViewModel.parseYuanToFen("not-money"))
    }
}
