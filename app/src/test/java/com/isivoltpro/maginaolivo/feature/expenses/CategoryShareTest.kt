package com.isivoltpro.maginaolivo.feature.expenses

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** #500: the Gastos category list never shows an overflowed share or amount. */
class CategoryShareTest {
    @Test
    fun ordinaryShares() {
        assertEquals(71L, categoryShare(5_000, 7_000))
        assertEquals(0L, categoryShare(0, 0))
    }

    @Test
    fun unknownSumsHaveNoShare() {
        assertNull(categoryShare(null, 7_000))
        assertNull(categoryShare(5_000, null))
    }

    @Test
    fun largeAmountsDoNotOverflowTheShare() {
        assertEquals(50L, categoryShare(Long.MAX_VALUE / 2, Long.MAX_VALUE - 1))
    }
}
