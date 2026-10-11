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

    @Test fun attributedKilosAreNeverAllowedToExceedTheCampaign() {
        // Corrupt or imported data (#497) must not produce a negative "mixed" figure.
        val breakdown = KiloBreakdown.of(1_000_000L, listOf(900_000L, 900_000L))
        assertNull(breakdown.mixedGrams)
        assertNull(kiloBreakdownLine(breakdown))
    }
}
