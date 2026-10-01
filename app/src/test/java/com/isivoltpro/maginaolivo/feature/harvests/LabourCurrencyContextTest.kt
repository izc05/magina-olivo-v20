package com.isivoltpro.maginaolivo.feature.harvests

import com.isivoltpro.maginaolivo.domain.expense.*
import com.isivoltpro.maginaolivo.domain.labour.*
import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class LabourCurrencyContextTest {
    private val day = UUID.randomUUID()
    private val campaign = UUID.randomUUID()
    private val date = LocalDate.of(2026, 10, 12)
    private val legacy = LabourEntry(UUID.randomUUID(), day, UUID.randomUUID(), "Juan García López", 1, LabourUnit.FULL_DAY, null, 1)
    private val jpy = Expense(UUID.randomUUID(), UUID.randomUUID(), date, "Jornales", ExpenseCategory.LABOR, 1000, "JPY", ExpenseStatus.POSTED, ExpenseOrigin.DAY_LABOUR, campaignId = campaign, harvestId = day)
    private fun priced(currency: String) = legacy.copy(id = UUID.randomUUID(), appliedRate = LabourRateSnapshot(1000, currency, date, LabourRateBasis.DAY))

    @Test fun historicalLedgerDenominationOverridesCurrentFarmRatesForLegacyAndNewRows() {
        assertEquals("JPY", labourCurrencyContext(day, campaign, listOf(legacy), listOf(jpy), "EUR").currency)
        assertEquals("JPY", labourCurrencyContext(day, campaign, emptyList(), listOf(jpy), "EUR").currency)
    }
    @Test fun compatiblePartlyConfirmedDayKeepsJpyWithOrWithoutItsLedger() {
        val rows = listOf(legacy, priced("JPY"))
        assertEquals("JPY", labourCurrencyContext(day, campaign, rows, listOf(jpy), "EUR").currency)
        assertEquals("JPY", labourCurrencyContext(day, campaign, rows, emptyList(), "EUR").currency)
    }
    @Test fun conflictingSnapshotsAndLedgerCannotGuessACurrency() {
        val result = labourCurrencyContext(day, campaign, listOf(legacy, priced("EUR")), listOf(jpy), "EUR")
        assertNull(result.currency)
        assertTrue(result.error!!.contains("no coincide"))
        assertNull(labourCurrencyContext(day, campaign, listOf(priced("EUR"), priced("JPY")), emptyList(), "EUR").currency)
    }
    @Test fun multiplePostedLedgersStayAmbiguousEvenWhenDenominationsMatch() {
        val result = labourCurrencyContext(day, campaign, listOf(legacy), listOf(jpy, jpy.copy(id = UUID.randomUUID())), "EUR")
        assertNull(result.currency)
        assertTrue(result.error!!.contains("varios costes"))
    }
    @Test fun draftManualAndOtherDayOrCampaignCostsDoNotReplaceHistoricalContext() {
        val irrelevant = listOf(jpy.copy(status = ExpenseStatus.DRAFT), jpy.copy(origin = ExpenseOrigin.MANUAL), jpy.copy(harvestId = UUID.randomUUID()), jpy.copy(campaignId = UUID.randomUUID()))
        assertEquals("KWD", labourCurrencyContext(day, campaign, listOf(priced("KWD")), irrelevant, "EUR").currency)
    }
    @Test fun onlyDaysWithoutHistoricalContextUseCurrentCurrencyOrEurDefault() {
        assertEquals("KWD", labourCurrencyContext(day, campaign, listOf(legacy), emptyList(), "KWD").currency)
        assertEquals("EUR", labourCurrencyContext(day, campaign, listOf(legacy), emptyList(), null).currency)
    }
}
