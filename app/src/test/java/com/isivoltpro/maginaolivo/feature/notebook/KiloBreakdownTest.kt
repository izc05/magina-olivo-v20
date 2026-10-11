package com.isivoltpro.maginaolivo.feature.notebook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** #355 — the DEMO figures, which were right all along, told apart in words. */
class KiloBreakdownTest {
    private val demoDelivered = 6_680_000L
    private val demoParcels = listOf(2_710_000L, 1_850_000L) // El Barranco, Los Llanos

    @Test fun theDemoCampaignSplitsIntoAttributedAndMixedWithoutChangingAnyTotal() {
        val breakdown = KiloBreakdown.of(demoDelivered, demoParcels)
        assertEquals(6_680_000L, breakdown.deliveredGrams)
        assertEquals(4_560_000L, breakdown.attributedGrams)
        assertEquals(2_120_000L, breakdown.mixedGrams)   // the Loma + Llanos Pesada
        val line = kiloBreakdownLine(breakdown)!!
        assertTrue(line, line.contains("6.680 kg"))
        assertTrue(line, line.contains("4.560 kg"))
        assertTrue(line, line.contains("2.120 kg"))
        // The old sentence called every weighed kilo "sin reparto"; that claim is gone.
        assertTrue(line, !line.contains("6.680 kg sin reparto"))
    }

    @Test fun everyKiloWithItsParcelIsSaidPlainly() {
        val line = kiloBreakdownLine(KiloBreakdown.of(4_560_000L, demoParcels))!!
        assertTrue(line, line.contains("repartidos por parcela"))
    }

    @Test fun anUnknownTotalSaysNothingRatherThanGuessing() {
        assertNull(kiloBreakdownLine(KiloBreakdown.of(null, demoParcels)))
        assertNull(kiloBreakdownLine(KiloBreakdown.of(demoDelivered, emptyList())))
    }

    @Test fun aSumThatWouldNotFitInALongIsUnknownRatherThanWrappedAround() {
        // #497: two parcels whose kilos overflow Long. Adding them would wrap to a negative
        // number and the screen would print a kilo figure that is a lie.
        val overflowing = listOf(Long.MAX_VALUE - 10, 1_000L)
        val breakdown = KiloBreakdown.of(Long.MAX_VALUE, overflowing)
        assertNull(breakdown.attributedGrams)
        assertNull(breakdown.mixedGrams)
        assertNull(kiloBreakdownLine(breakdown))
        // Right at the edge it still answers, because that sum does fit.
        val exact = KiloBreakdown.of(Long.MAX_VALUE, listOf(Long.MAX_VALUE - 10, 10L))
        assertEquals(Long.MAX_VALUE, exact.attributedGrams)
        assertEquals(0L, exact.mixedGrams)
    }

    @Test fun aNegativeFigureIsUnknown() {
        // Corrupt or imported records can carry one; it must not become a kilo on screen.
        assertNull(KiloBreakdown.of(demoDelivered, listOf(-1L, 2_000_000L)).attributedGrams)
        assertNull(KiloBreakdown.of(-5L, demoParcels).deliveredGrams)
        assertNull(kiloBreakdownLine(KiloBreakdown.of(-5L, demoParcels)))
    }

    @Test fun attributedKilosAreNeverAllowedToExceedTheCampaign() {
        // Corrupt or imported data (#497) must not produce a negative "mixed" figure.
        val breakdown = KiloBreakdown.of(1_000_000L, listOf(900_000L, 900_000L))
        assertNull(breakdown.mixedGrams)
        assertNull(kiloBreakdownLine(breakdown))
    }
}
