package com.isivoltpro.maginaolivo.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MoFormGuardTest {
    @Test fun anUntouchedFormClosesFreely() = assertFalse(MoFormGuard().dirty)

    @Test fun typedTextCountsWhenTheFormDoesNotCompareItsValues() {
        val guard = MoFormGuard()
        guard.noteEdit()
        assertTrue(guard.dirty)
    }

    @Test fun aComparingFormDecidesEvenAfterTyping() {
        val guard = MoFormGuard()
        guard.noteEdit()
        guard.compared = false
        assertFalse(guard.dirty)
        guard.compared = true
        assertTrue(guard.dirty)
    }
}
