package com.isivoltpro.maginaolivo.domain.production

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** #497: a split never uses an overflowed sum as if it were a real allocation. */
class ParcelSplitTest {
    @Test
    fun ordinarySplitsAreUnchanged() {
        assertNull(ParcelSplit.problem(3_000, listOf(1_000, 2_000)))
        assertNull(ParcelSplit.problem(3_000, listOf(1_000, null)))
        assertEquals("exceeds_total", ParcelSplit.problem(3_000, listOf(2_000, 2_000)))
        assertEquals("does_not_reconcile", ParcelSplit.problem(3_000, listOf(1_000, 1_000)))
        assertEquals("nothing_left_unallocated", ParcelSplit.problem(3_000, listOf(3_000, null)))
    }

    @Test
    fun weightsWhoseSumOverflowsExceedTheTotal() {
        val huge = Long.MAX_VALUE - 10
        assertEquals("exceeds_total", ParcelSplit.problem(1_000, listOf(huge, huge)))
        assertEquals("exceeds_total", ParcelSplit.problem(Long.MAX_VALUE, listOf(huge, 100, null)))
        assertEquals("exceeds_total", ParcelSplit.problem(Long.MAX_VALUE, listOf(Long.MAX_VALUE, 1)))
    }
}
