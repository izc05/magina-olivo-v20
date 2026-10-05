package com.isivoltpro.maginaolivo.domain.expense

import com.isivoltpro.maginaolivo.domain.equipment.EquipmentLine
import com.isivoltpro.maginaolivo.domain.equipment.EquipmentPriceSnapshot
import com.isivoltpro.maginaolivo.domain.equipment.EquipmentType
import com.isivoltpro.maginaolivo.domain.expense.RecollectionCostCompleteness.Reason
import com.isivoltpro.maginaolivo.domain.labour.LabourEntry
import com.isivoltpro.maginaolivo.domain.labour.LabourRateBasis
import com.isivoltpro.maginaolivo.domain.labour.LabourRateSnapshot
import com.isivoltpro.maginaolivo.domain.labour.LabourUnit
import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** #449: the posted money is trusted as the whole cost only when nothing is still unknown. */
class RecollectionCostCompletenessTest {
    private val day = UUID.randomUUID()
    private val date = LocalDate.of(2026, 12, 10)

    @Test fun pricedAndPostedIsComplete() {
        val result = RecollectionCostCompleteness.of(
            listOf(jornal(6_500)), listOf(machine(9_000)),
            listOf(cost(6_500, ExpenseOrigin.DAY_LABOUR, ExpenseCategory.LABOR), cost(9_000, ExpenseOrigin.DAY_EQUIPMENT, ExpenseCategory.MACHINERY)),
        )
        assertTrue(result.complete)
    }

    @Test fun aJornalWithoutPriceMakesItIncompleteAndAddsNothing() {
        val result = RecollectionCostCompleteness.of(
            listOf(jornal(6_500), jornal(null)), emptyList(),
            listOf(cost(6_500, ExpenseOrigin.DAY_LABOUR, ExpenseCategory.LABOR, ExpenseStatus.DRAFT)),
        )
        assertEquals(setOf(Reason.LABOUR_UNPRICED, Reason.UNPOSTED_RESOURCE_COST, Reason.DRAFT_COSTS), result.reasons)
    }

    @Test fun aMachineWithoutPriceMakesItIncomplete() {
        val result = RecollectionCostCompleteness.of(emptyList(), listOf(machine(null)), emptyList())
        assertEquals(setOf(Reason.EQUIPMENT_UNPRICED), result.reasons)
    }

    /** #475: a hand-typed cost that replaces the calculation leaves that draft calculation aside, not pending. */
    @Test fun aSupersededDraftCalculationIsNotPending() {
        val result = RecollectionCostCompleteness.of(
            listOf(jornal(6_500)), emptyList(),
            listOf(
                cost(6_500, ExpenseOrigin.DAY_LABOUR, ExpenseCategory.LABOR, ExpenseStatus.DRAFT),
                cost(7_000, ExpenseOrigin.DAY_REPLACEMENT, ExpenseCategory.LABOR),
            ),
        )
        assertTrue(result.complete)
    }

    /** Codex #605: a former replacement taken back to «Se añade» and left as a draft is pending again. */
    @Test fun anAdditiveDraftBesideAPostedCalculationIsPending() {
        val result = RecollectionCostCompleteness.of(
            listOf(jornal(6_500)), emptyList(),
            listOf(
                cost(6_500, ExpenseOrigin.DAY_LABOUR, ExpenseCategory.LABOR),
                cost(2_000, ExpenseOrigin.MANUAL, ExpenseCategory.LABOR, ExpenseStatus.DRAFT),
            ),
        )
        assertEquals(setOf(Reason.DRAFT_COSTS), result.reasons)
    }

    /** Codex #605: an explicit replacement stands for the day's cost even in another currency. */
    @Test fun aReplacementInAnotherCurrencyCoversTheDay() {
        val result = RecollectionCostCompleteness.of(
            listOf(jornal(6_500)), emptyList(),
            listOf(
                cost(6_500, ExpenseOrigin.DAY_LABOUR, ExpenseCategory.LABOR, ExpenseStatus.DRAFT),
                cost(8_000, ExpenseOrigin.DAY_REPLACEMENT, ExpenseCategory.LABOR).copy(currency = "USD"),
            ),
        )
        assertTrue(result.complete)
    }

    @Test fun anUnconfirmedGastoMakesItIncomplete() {
        val result = RecollectionCostCompleteness.of(emptyList(), emptyList(),
            listOf(cost(4_000, ExpenseOrigin.MANUAL, ExpenseCategory.FUEL, ExpenseStatus.DRAFT)))
        assertEquals(setOf(Reason.DRAFT_COSTS), result.reasons)
    }

    @Test fun paymentsNeverEnterTheCompleteness() {
        // Nothing about payments is an input: confirming a price is what completes the cost.
        val before = RecollectionCostCompleteness.of(listOf(jornal(null)), emptyList(), emptyList())
        val after = RecollectionCostCompleteness.of(listOf(jornal(6_500)), emptyList(),
            listOf(cost(6_500, ExpenseOrigin.DAY_LABOUR, ExpenseCategory.LABOR)))
        assertEquals(false, before.complete)
        assertTrue(after.complete)
    }

    private fun jornal(minor: Long?) = LabourEntry(
        id = UUID.randomUUID(), harvestId = day, workerId = UUID.randomUUID(), workerName = "Ana", quantity = 1,
        unit = LabourUnit.FULL_DAY, minutes = null, version = 1,
        appliedRate = minor?.let { LabourRateSnapshot(it, "EUR", date, LabourRateBasis.DAY) },
    )

    private fun machine(minor: Long?) = EquipmentLine(
        id = UUID.randomUUID(), harvestId = day, type = EquipmentType.TRACTOR, label = null, quantity = 1,
        machineId = null, version = 1, appliedPrice = minor?.let { EquipmentPriceSnapshot(it, "EUR", date) },
    )

    private fun cost(minor: Long, origin: ExpenseOrigin, category: ExpenseCategory, status: ExpenseStatus = ExpenseStatus.POSTED) = Expense(
        id = UUID.randomUUID(), workspaceId = UUID.randomUUID(), expenseDate = date, concept = category.name,
        category = category, amountMinor = minor, currency = "EUR", status = status, origin = origin, harvestId = day,
    )
}
