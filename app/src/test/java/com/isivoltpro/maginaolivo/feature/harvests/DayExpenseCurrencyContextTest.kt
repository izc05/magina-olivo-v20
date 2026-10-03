package com.isivoltpro.maginaolivo.feature.harvests

import com.isivoltpro.maginaolivo.domain.expense.*
import com.isivoltpro.maginaolivo.domain.equipment.*
import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class DayExpenseCurrencyContextTest {
    private val day = UUID.randomUUID()
    private val campaign = UUID.randomUUID()
    private val date = LocalDate.of(2026, 10, 2)
    private fun cost(currency: String) = Expense(UUID.randomUUID(), UUID.randomUUID(), date, "Gasoil", ExpenseCategory.FUEL, 1000, currency, ExpenseStatus.POSTED, ExpenseOrigin.MANUAL, campaignId = campaign, harvestId = day)
    private fun resolve(costs: List<Expense>, usual: String? = "EUR", lines: List<EquipmentLine> = emptyList()) = dayExpenseCurrencyContext(day, campaign, emptyList(), lines, costs, usual)
    @Test fun postedHistoricalJpyWinsOverTodaysEur() { assertEquals("JPY", resolve(listOf(cost("JPY"))).currency) }
    @Test fun farmUsualCurrencyOnlySuppliesContextWhenHistoryHasNone() {
        assertEquals("KWD", resolve(emptyList(), "KWD").currency)
        assertEquals("EUR", resolve(emptyList(), null).currency)
        val snapshot = EquipmentLine(UUID.randomUUID(), day, EquipmentType.COMB, null, 1, null, 1, EquipmentPriceSnapshot(1000, "JPY", date))
        assertEquals("JPY", resolve(emptyList(), "EUR", listOf(snapshot)).currency)
    }
    @Test fun ambiguousAndUnsupportedCodesBlockWithoutReplacingTheOriginalCodes() {
        val mixed = resolve(listOf(cost("JPY"), cost("EUR")))
        assertNull(mixed.currency)
        assertTrue(mixed.error!!.contains("EUR, JPY"))
        val unknown = resolve(listOf(cost("ZZZ")))
        assertNull(unknown.currency)
        assertTrue(unknown.error!!.contains("ZZZ"))
    }
    @Test fun draftsAndAnotherDaysCostsNeverAssignHistoricalCurrency() {
        assertEquals("KWD", resolve(listOf(cost("JPY").copy(status = ExpenseStatus.DRAFT), cost("JPY").copy(harvestId = UUID.randomUUID())), "KWD").currency)
    }
    @Test fun missingResourceReadCannotEstablishANewExpensesCurrency() {
        val harvest = com.isivoltpro.maginaolivo.domain.harvest.Harvest(day, UUID.randomUUID(), UUID.randomUUID(), campaign, date, 0, emptyList(), null, null, null, null, 1)
        val pending = HarvestDetailUiState(isLoading = false, harvest = harvest, labourLoaded = false)
        assertNull(pending.newCostCurrencyContext().currency)
        assertNull(pending.copy(labourLoaded = true, costsLoaded = false).newCostCurrencyContext().currency)
        assertNull(pending.copy(labourLoaded = true, costsReadFailed = true).newCostCurrencyContext().currency)
        assertNull(pending.copy(labourLoaded = true, equipmentLoaded = false).newCostCurrencyContext().currency)
        assertNull(pending.copy(labourLoaded = true, ratesReadFailed = true).newCostCurrencyContext().currency)
        assertEquals("JPY", pending.copy(costs = listOf(cost("JPY"))).newCostCurrencyContext().currency)
    }
}
