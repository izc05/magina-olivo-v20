package com.isivoltpro.maginaolivo.domain.harvest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WeightTest {
    @Test
    fun readsKilosTheWayAFarmerTypesThem() {
        assertEquals(2_850_000L, Weight.parseGrams("2850"))
        assertEquals(2_850_000L, Weight.parseGrams("2.850"))
        assertEquals(2_850_000L, Weight.parseGrams("2.850 kg"))
        assertEquals(2_850_500L, Weight.parseGrams("2850,5"))
        assertEquals(2_850_500L, Weight.parseGrams("2.850,5"))
        assertEquals(12_500L, Weight.parseGrams("12.5"))
        assertEquals(1_234_567_000L, Weight.parseGrams("1.234.567"))
        assertEquals(125L, Weight.parseGrams("0,125"))
    }

    @Test
    fun anAmbiguousOrUnreadableFigureIsNullNotAGuess() {
        assertNull(Weight.parseGrams(null))
        assertNull(Weight.parseGrams(""))
        assertNull(Weight.parseGrams("  "))
        assertNull(Weight.parseGrams("2850.500"))
        assertNull(Weight.parseGrams("2,850,5"))
        assertNull(Weight.parseGrams("-20"))
        assertNull(Weight.parseGrams("unos 200"))
        assertNull(Weight.parseGrams("2850,1234"))
    }

    /** #497: a well-formed but enormous figure is null, never an exception that crashes the form. */
    @Test
    fun aFigureBeyondALongIsNullNotACrash() {
        assertEquals(Long.MAX_VALUE, Weight.parseGrams("9223372036854775,807"))
        assertNull(Weight.parseGrams("9223372036854775,808"))
        assertNull(Weight.parseGrams("9223372036854775808"))
        assertNull(Weight.parseGrams("9.223.372.036.854.775,808"))
    }

    @Test
    fun theEditableFormReadsBackToTheSameGrams() {
        listOf(2_850_000L, 2_850_500L, 12_500L, 125L, 1L).forEach { grams ->
            assertEquals(grams, Weight.parseGrams(Weight.editable(grams)))
        }
        assertEquals("2850", Weight.editable(2_850_000L))
        assertEquals("2850,5", Weight.editable(2_850_500L))
        assertEquals("", Weight.editable(null))
    }
}
