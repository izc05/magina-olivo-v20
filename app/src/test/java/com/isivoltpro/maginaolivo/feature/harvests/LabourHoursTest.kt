package com.isivoltpro.maginaolivo.feature.harvests

import org.junit.Assert.*
import org.junit.Test

class LabourHoursTest {
    @Test fun overflowingHoursNeverWrapIntoValidMinutes() {
        assertNull(parseHours("71582789:00"))
        assertNull(parseHours("9999999999999999999999"))
        assertNull(parseHours("NaN"))
    }
    @Test fun usualHoursStillParseExactly() {
        assertEquals(180, parseHours("3"))
        assertEquals(210, parseHours("3,5"))
        assertEquals(210, parseHours("3:30"))
    }
}
