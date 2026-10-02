package com.isivoltpro.maginaolivo.domain.expense

import com.isivoltpro.maginaolivo.domain.equipment.EquipmentLine
import com.isivoltpro.maginaolivo.domain.equipment.EquipmentPriceSnapshot
import com.isivoltpro.maginaolivo.domain.equipment.EquipmentType
import com.isivoltpro.maginaolivo.domain.labour.LabourEntry
import com.isivoltpro.maginaolivo.domain.labour.LabourUnit
import java.util.UUID
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test fun machineryUsesConfirmedUnitSnapshotsOnlyOnce() {
        val cost = DayCostCalculator.equipment(
            listOf(line(EquipmentType.SHAKER, 2, 7_000), line(EquipmentType.COMB, 1, 2_000), line(EquipmentType.TRAILER, 1, 3_000)),
        )!!
        assertEquals(19_000L, cost.amountMinor)
        assertNull(cost.unpriced)
        assertTrue(cost.note.contains("2 vibradoras"))
    }

    @Test fun missingEquipmentPriceStaysUnknownAndZeroIsConfirmed() {
        val cost = DayCostCalculator.equipment(listOf(line(EquipmentType.SHAKER, 1, 0), line(EquipmentType.TRACTOR, 1)))!!
        assertEquals(0L, cost.amountMinor)
        assertEquals("1 tractor sin precio", cost.unpriced)
    }

    @Test(expected = ArithmeticException::class)
    fun equipmentMultiplicationOverflowIsRejected() {
        DayCostCalculator.equipment(listOf(line(EquipmentType.SHAKER, 2, Long.MAX_VALUE)))
    }

    private fun labour(quantity: Int, unit: LabourUnit, minutes: Int? = null) =
        LabourEntry(UUID.randomUUID(), day, null, null, quantity, unit, minutes, 1)

    private fun line(type: EquipmentType, quantity: Int, unitMinor: Long? = null) =
        EquipmentLine(UUID.randomUUID(), day, type, null, quantity, null, 1,
            unitMinor?.let { EquipmentPriceSnapshot(it, "EUR", LocalDate.of(2026, 10, 1)) })
    @Test
    fun onlyAReplacingHandTypedCostStandsForTheCalculation() {
        assertTrue(DayCostKind.EQUIPMENT.isReplacedBy(ExpenseCategory.MACHINERY, JornadaExpenseKind.RENTAL.concept(null)))
        assertTrue(DayCostKind.EQUIPMENT.isReplacedBy(ExpenseCategory.MACHINERY, "Vibradora de Paco"))
        assertFalse(DayCostKind.EQUIPMENT.isReplacedBy(ExpenseCategory.MACHINERY, JornadaExpenseKind.LUBRICANT.concept(null)))
        assertFalse(DayCostKind.EQUIPMENT.isReplacedBy(ExpenseCategory.MACHINERY, JornadaExpenseKind.LUBRICANT.concept("Aceite hidráulico")))
        assertFalse(DayCostKind.EQUIPMENT.isReplacedBy(ExpenseCategory.FUEL, "Gasoil"))
        // Typed in Gastos without the label: oil is still oil.
        assertFalse(DayCostKind.EQUIPMENT.isReplacedBy(ExpenseCategory.MACHINERY, "Aceite hidráulico"))
        assertFalse(DayCostKind.EQUIPMENT.isReplacedBy(ExpenseCategory.MACHINERY, "Grasa para la vibradora"))
        assertTrue(DayCostKind.EQUIPMENT.isReplacedBy(ExpenseCategory.MACHINERY, "Alquiler vibradora"))
        assertTrue(DayCostKind.LABOUR.isReplacedBy(ExpenseCategory.LABOR, "Cuadrilla"))
        assertEquals("Aceite/lubricante · Aceite hidráulico", JornadaExpenseKind.LUBRICANT.concept("Aceite hidráulico"))
        assertEquals("Gasoil del tractor", JornadaExpenseKind.DIESEL.concept("Gasoil del tractor"))
    }
    @Test
    fun unlinkedSameDateCostsAreAmbiguousOnlyWhenTheyCouldStandForTheCalculation() {
        val farm = UUID.randomUUID()
        val date = java.time.LocalDate.of(2026, 11, 18)
        fun expense(origin: ExpenseOrigin, category: ExpenseCategory, concept: String, harvest: UUID? = null, on: java.time.LocalDate = date) =
            Expense(UUID.randomUUID(), UUID.randomUUID(), on, concept, category, 10_000, "EUR", ExpenseStatus.POSTED, origin, farmId = farm, harvestId = harvest)
        val calculated = expense(ExpenseOrigin.DAY_LABOUR, ExpenseCategory.LABOR, DayCostKind.LABOUR.concept, harvest = day)
        val crew = expense(ExpenseOrigin.MANUAL, ExpenseCategory.LABOR, "Cuadrilla")
        val diesel = expense(ExpenseOrigin.MANUAL, ExpenseCategory.FUEL, "Gasoil")
        val otherDay = expense(ExpenseOrigin.MANUAL, ExpenseCategory.LABOR, "Cuadrilla", on = date.plusDays(1))
        val linkedElsewhere = expense(ExpenseOrigin.MANUAL, ExpenseCategory.LABOR, "Cuadrilla", harvest = UUID.randomUUID())
        // An Activity's cost stays its Activity's: its next edit would drop a day link.
        val activityCost = expense(ExpenseOrigin.ACTIVITY_COST, ExpenseCategory.LABOR, "Poda")
        val scanned = expense(ExpenseOrigin.DOCUMENT_OCR, ExpenseCategory.LABOR, "Factura cuadrilla")
        val rows = listOf(calculated, crew, diesel, otherDay, linkedElsewhere, activityCost, scanned)

        assertEquals(listOf(crew, scanned), UnlinkedDayCosts.of(day, farm, date, rows))
        // No calculated cost on the day: nothing is ambiguous.
        assertEquals(emptyList<Expense>(), UnlinkedDayCosts.of(day, farm, date, rows - calculated))
    }
}
