package com.foldledger.presentation

import com.foldledger.presentation.nav.TopDest
import org.junit.Assert.assertEquals
import org.junit.Test

class FoldLedgerAppTest {
    @Test
    fun resolveDisplayedTab_switchesImmediatelyWhenTransitionIsIdle() {
        assertEquals(
            TopDest.Stats,
            resolveDisplayedTab(
                current = TopDest.Ledger,
                requested = TopDest.Stats,
                transitionRunning = false,
            ),
        )
    }

    @Test
    fun resolveDisplayedTab_keepsCurrentContentDuringActiveTransition() {
        assertEquals(
            TopDest.Stats,
            resolveDisplayedTab(
                current = TopDest.Stats,
                requested = TopDest.Settings,
                transitionRunning = true,
            ),
        )
    }
}
