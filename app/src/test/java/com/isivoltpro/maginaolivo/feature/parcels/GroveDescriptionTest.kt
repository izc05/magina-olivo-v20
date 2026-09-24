package com.isivoltpro.maginaolivo.feature.parcels

import com.isivoltpro.maginaolivo.domain.parcel.IrrigationSystem
import java.time.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GroveDescriptionTest {

    @Test
    fun oliveTreeCountAcceptsWholePositiveNumbersOnly() {
        assertEquals(247, parseOliveTrees("247"))
        assertEquals(1200, parseOliveTrees("1.200"))
        assertNull(parseOliveTrees(""))
        assertNull(parseOliveTrees("0"))
        assertNull(parseOliveTrees("12,5"))
        assertNull(parseOliveTrees("muchos"))
    }

    @Test
    fun irrigationDaysReadAsSpanishInWeekOrder() {
        assertNull(emptySet<DayOfWeek>().label())
        assertEquals("Lunes", setOf(DayOfWeek.MONDAY).label())
        assertEquals("Lunes y Jueves", setOf(DayOfWeek.THURSDAY, DayOfWeek.MONDAY).label())
        assertEquals("Lunes, Miércoles y Viernes", setOf(DayOfWeek.FRIDAY, DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY).label())
    }

    @Test
    fun irrigationSystemsHaveFarmerLabels() {
        assertEquals("Goteo", IrrigationSystem.DRIP.shortLabel())
        assertEquals("Riego por goteo", IrrigationSystem.DRIP.label())
        assertEquals("Secano", IrrigationSystem.DRYLAND.shortLabel())
    }
}
