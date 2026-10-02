package com.isivoltpro.maginaolivo.feature.harvests

import com.isivoltpro.maginaolivo.domain.equipment.EquipmentLine
import com.isivoltpro.maginaolivo.domain.equipment.EquipmentPriceSnapshot
import com.isivoltpro.maginaolivo.domain.equipment.EquipmentType
import com.isivoltpro.maginaolivo.domain.expense.Expense
import com.isivoltpro.maginaolivo.domain.expense.ExpenseCategory
import com.isivoltpro.maginaolivo.domain.expense.ExpenseOrigin
import com.isivoltpro.maginaolivo.domain.expense.ExpenseStatus
import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class EquipmentCurrencyContextTest {
    private val day = UUID.randomUUID()
    private val campaign = UUID.randomUUID()
    private val date = LocalDate.of(2026, 10, 1)

    @Test fun postedYenHistoryWinsOverTodaysEuroPreference() {
        val result = equipmentCurrencyContext(day, campaign, listOf(line(null)), listOf(ledger("JPY")), "EUR")
        assertEquals("JPY", result.currency)
    }

    @Test fun mixedConfirmedDenominationsBlockEditing() {
        val result = equipmentCurrencyContext(day, campaign,
            listOf(line(EquipmentPriceSnapshot(7_000, "EUR", date))), listOf(ledger("JPY")), "EUR")
        assertNotNull(result.error)
    }

    private fun line(price: EquipmentPriceSnapshot?) = EquipmentLine(UUID.randomUUID(), day, EquipmentType.SHAKER,
        null, 1, null, 1, price)
    private fun ledger(currency: String) = Expense(UUID.randomUUID(), UUID.randomUUID(), date, "Maquinaria",
        ExpenseCategory.MACHINERY, 7_000, currency, ExpenseStatus.POSTED, ExpenseOrigin.DAY_EQUIPMENT,
        campaignId = campaign, harvestId = day)
}
