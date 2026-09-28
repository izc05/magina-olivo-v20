package com.isivoltpro.maginaolivo.domain.expense

import com.isivoltpro.maginaolivo.domain.equipment.EquipmentLine
import com.isivoltpro.maginaolivo.domain.equipment.EquipmentType
import com.isivoltpro.maginaolivo.domain.labour.LabourEntry
import com.isivoltpro.maginaolivo.domain.labour.LabourUnit
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** CR-010 §8–9: a day's cost from its attendance/equipment and the Farm's optional prices. */
class DayCostCalculatorTest {
    private val day = UUID.randomUUID()

    @Test fun fullHalfAndHoursArePricedApartAndRoundedToTheCent() {
        val rates = RecollectionRates(fullDayMinor = 7_001, hourlyMinor = 900)
        val cost = DayCostCalculator.labour(
            listOf(labour(5, LabourUnit.FULL_DAY), labour(2, LabourUnit.HALF_DAY), labour(1, LabourUnit.HOURS, 90)),
            rates,
        )!!
        // 5 × 70,01 + 2 × 35,01 (half, rounded up) + 1 h 30 min × 9 = 350,05 + 70,02 + 13,50.
        assertEquals(43_357L, cost.amountMinor)
        assertNull(cost.unpriced)
        assertEquals(3_501L, DayCostCalculator.halfDayMinor(7_001))
    }

    @Test fun aMissingPriceIsUnknownNeverZero() {
        val cost = DayCostCalculator.labour(
            listOf(labour(4, LabourUnit.FULL_DAY), labour(2, LabourUnit.HOURS, 180)),
            RecollectionRates(fullDayMinor = 7_000),
        )!!
        assertEquals(28_000L, cost.amountMinor)
        assertTrue(cost.unpriced!!.contains("6 h"))
        // Nothing to price at all: no cost, and no attendance means nothing to calculate.
        assertEquals(0L, DayCostCalculator.labour(listOf(labour(3, LabourUnit.FULL_DAY)), RecollectionRates())!!.amountMinor)
        assertNull(DayCostCalculator.labour(emptyList(), RecollectionRates(fullDayMinor = 7_000)))
    }

    @Test fun machineryIsPricedPerTypeAndDay() {
        val cost = DayCostCalculator.equipment(
            listOf(line(EquipmentType.SHAKER, 2), line(EquipmentType.TRACTOR, 1), line(EquipmentType.TRAILER, 1)),
            RecollectionRates(equipmentDayMinor = mapOf(EquipmentType.TRACTOR to 6_000, EquipmentType.SHAKER to 3_500)),
        )!!
        assertEquals(13_000L, cost.amountMinor)
        assertEquals("1 remolque sin precio", cost.unpriced)
        assertTrue(cost.note.startsWith("1 tractor"))
    }

    private fun labour(quantity: Int, unit: LabourUnit, minutes: Int? = null) =
        LabourEntry(UUID.randomUUID(), day, null, null, quantity, unit, minutes, 1)

    private fun line(type: EquipmentType, quantity: Int) =
        EquipmentLine(UUID.randomUUID(), day, type, null, quantity, null, 1)
}
