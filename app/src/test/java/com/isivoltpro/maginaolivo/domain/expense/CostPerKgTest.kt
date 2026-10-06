package com.isivoltpro.maginaolivo.domain.expense

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** #486: cost per kilo is a ratio with thousandths, never rounded to the currency's cents. */
class CostPerKgTest {
    @Test fun theIssueExamplesKeepTheirThousandths() {
        assertEquals(253L, CostPerKg.milli(144_000, "EUR", 5_700_000)) // 1.440 € / 5.700 kg
        assertEquals(114L, CostPerKg.milli(65_000, "EUR", 5_700_000))  // 650 € / 5.700 kg
        assertEquals(250L, CostPerKg.milli(25_000, "EUR", 100_000))    // exactly 0,250
    }

    @Test fun itReadsWithThreeDecimals() {
        val text = CostPerKg.format(253, "EUR")
        assertTrue(text, text.contains("0,253"))
        assertTrue(text, text.endsWith("€/kg"))
        assertTrue(CostPerKg.format(250, "EUR").contains("0,250"))
    }

    @Test fun noKilosOrAnUnknownCurrencyIsNoRatioNeverZero() {
        assertNull(CostPerKg.milli(10_000, "EUR", 0))
        assertNull(CostPerKg.milli(10_000, "ZZZ", 1_000))
    }

    @Test fun currenciesWithOtherDecimalsUseTheirOwnUnit() {
        assertEquals(166L, CostPerKg.milli(530, "JPY", 3_200_000))     // 530 ¥ / 3.200 kg
        assertEquals(166L, CostPerKg.milli(530_000, "KWD", 3_200_000)) // 530 KWD / 3.200 kg
    }
}
