package com.isivoltpro.maginaolivo.feature.harvests

import com.isivoltpro.maginaolivo.domain.equipment.EquipmentLine
import com.isivoltpro.maginaolivo.domain.expense.Expense
import com.isivoltpro.maginaolivo.domain.expense.ExpenseCategory
import com.isivoltpro.maginaolivo.domain.expense.ExpenseOrigin
import com.isivoltpro.maginaolivo.domain.expense.ExpenseStatus
import java.util.UUID

internal data class EquipmentCurrencyContext(val currency: String?, val error: String? = null)

/** Resolve historical denomination before showing today's usual prices. */
internal fun equipmentCurrencyContext(harvestId: UUID, campaignId: UUID?, lines: List<EquipmentLine>,
    costs: List<Expense>, usualCurrency: String?): EquipmentCurrencyContext {
    val ledgers = costs.filter { it.harvestId == harvestId && it.campaignId == campaignId &&
        it.origin == ExpenseOrigin.DAY_EQUIPMENT && it.category == ExpenseCategory.MACHINERY &&
        it.status == ExpenseStatus.POSTED }
    val snapshots = lines.filter { it.harvestId == harvestId }.mapNotNull { it.appliedPrice?.currency }.distinct()
    if (ledgers.size > 1) return EquipmentCurrencyContext(null,
        "Hay varios costes de maquinaria para este día. Revisa estos registros antes de confirmar la moneda.")
    val historical = ledgers.singleOrNull()?.currency
    if (snapshots.size > 1 || (historical != null && snapshots.any { it != historical })) {
        return EquipmentCurrencyContext(null,
            "La moneda histórica no coincide entre los precios y el gasto. Revisa estos registros antes de guardar.")
    }
    return EquipmentCurrencyContext(historical ?: snapshots.singleOrNull() ?: usualCurrency ?: "EUR")
}
