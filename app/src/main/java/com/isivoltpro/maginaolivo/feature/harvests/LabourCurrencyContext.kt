package com.isivoltpro.maginaolivo.feature.harvests

import com.isivoltpro.maginaolivo.domain.expense.Expense
import com.isivoltpro.maginaolivo.domain.expense.ExpenseCategory
import com.isivoltpro.maginaolivo.domain.expense.ExpenseOrigin
import com.isivoltpro.maginaolivo.domain.expense.ExpenseStatus
import com.isivoltpro.maginaolivo.domain.labour.LabourEntry
import java.util.UUID

internal data class LabourCurrencyContext(val currency: String?, val error: String? = null)

internal data class DayExpenseCurrencyContext(val currency: String?, val error: String? = null)

internal fun HarvestDetailUiState.newCostCurrencyContext(): DayExpenseCurrencyContext {
    val day = harvest ?: return DayExpenseCurrencyContext(null, "El día de recolección no está disponible.")
    if (!costsLoaded) return DayExpenseCurrencyContext(null, "Cargando la moneda de los gastos del día…")
    if (costsReadFailed) return DayExpenseCurrencyContext(null, "No pudimos leer los gastos. Vuelve a abrir el día antes de añadir un gasto.")
    val hasPostedContext = costs.any { it.harvestId == day.id && it.campaignId == day.campaignId && it.status == ExpenseStatus.POSTED }
    if (!hasPostedContext) {
        if (!labourLoaded || !equipmentLoaded || !ratesLoaded) return DayExpenseCurrencyContext(null, "Cargando los precios históricos del día…")
        if (labourReadFailed || equipmentReadFailed || ratesReadFailed) return DayExpenseCurrencyContext(null, "No pudimos confirmar la moneda histórica. Vuelve a abrir el día antes de añadir un gasto.")
    }
    return dayExpenseCurrencyContext(day.id, day.campaignId, labour, equipment, costs, rates?.currency)
}

/** New day costs use confirmed historical denomination before today's usual currency. */
internal fun dayExpenseCurrencyContext(harvestId: UUID, campaignId: UUID?, entries: List<LabourEntry>,
    equipment: List<com.isivoltpro.maginaolivo.domain.equipment.EquipmentLine>, costs: List<Expense>, usualCurrency: String?): DayExpenseCurrencyContext {
    val posted = costs.filter { it.harvestId == harvestId && it.campaignId == campaignId && it.status == ExpenseStatus.POSTED }
        .map { it.currency }.distinct()
    val snapshots = (entries.filter { it.harvestId == harvestId }.mapNotNull { it.appliedRate?.currency } +
        equipment.filter { it.harvestId == harvestId }.mapNotNull { it.appliedPrice?.currency }).distinct()
    val historical = posted.ifEmpty { snapshots }
    if (historical.size > 1) return DayExpenseCurrencyContext(null,
        "Hay varias monedas históricas (${historical.sorted().joinToString(", ")}). Revisa los gastos del día antes de añadir otro; puedes usar el formulario normal de Gastos.")
    val currency = historical.singleOrNull() ?: usualCurrency ?: "EUR"
    if (!runCatching { java.util.Currency.getInstance(currency).defaultFractionDigits >= 0 }.getOrDefault(false)) {
        return DayExpenseCurrencyContext(null, "La moneda histórica $currency no admite edición. Se conserva el importe original; revisa el gasto antes de añadir otro.")
    }
    return DayExpenseCurrencyContext(currency)
}

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
