package com.isivoltpro.maginaolivo.feature.harvests

import com.isivoltpro.maginaolivo.domain.expense.Expense
import com.isivoltpro.maginaolivo.domain.expense.ExpenseCategory
import com.isivoltpro.maginaolivo.domain.expense.ExpenseOrigin
import com.isivoltpro.maginaolivo.domain.expense.ExpenseStatus
import com.isivoltpro.maginaolivo.domain.labour.LabourEntry
import java.util.UUID

internal data class LabourCurrencyContext(val currency: String?, val error: String? = null)

/** A day retains its ledger denomination; confirmed snapshots must agree with it. */
internal fun labourCurrencyContext(harvestId: UUID, campaignId: UUID?, entries: List<LabourEntry>, costs: List<Expense>, usualCurrency: String?): LabourCurrencyContext {
    val ledgers = costs.filter { it.harvestId == harvestId && it.campaignId == campaignId && it.origin == ExpenseOrigin.DAY_LABOUR && it.category == ExpenseCategory.LABOR && it.status == ExpenseStatus.POSTED }
    val snapshots = entries.filter { it.harvestId == harvestId }.mapNotNull { it.appliedRate?.currency }.distinct()
    if (ledgers.size > 1) return LabourCurrencyContext(null, "Hay varios costes de jornales para este día. Revisa estos registros antes de confirmar la moneda.")
    val ledgerCurrency = ledgers.singleOrNull()?.currency
    if (snapshots.size > 1 || (ledgerCurrency != null && snapshots.any { it != ledgerCurrency })) {
        return LabourCurrencyContext(null, "La moneda histórica no coincide entre los precios y los gastos. Revisa estos registros antes de guardar.")
    }
    return LabourCurrencyContext(ledgerCurrency ?: snapshots.singleOrNull() ?: usualCurrency ?: "EUR")
}
