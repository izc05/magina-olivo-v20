package com.isivoltpro.maginaolivo.feature.harvests

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.isivoltpro.maginaolivo.domain.expense.*
import com.isivoltpro.maginaolivo.domain.equipment.EquipmentLine
import com.isivoltpro.maginaolivo.domain.labour.LabourEntry
import com.isivoltpro.maginaolivo.ui.components.*
import java.util.UUID

internal fun List<RecollectionCurrency>.moneyLabel(bucket: RecollectionBucket? = null): String {
    val available = filter { bucket == null || it.hasPosted(bucket) }
    return if (available.isEmpty()) "—" else available.joinToString("\n") { ledger ->
        ledger.amount(bucket)?.let { Money.format(it, ledger.currency) } ?: "Importe no disponible (${ledger.currency})"
    }
}

internal fun labourCostNote(entries: List<LabourEntry>, expenses: List<Expense>): String? =
    resourceCostNote(RecollectionBucket.LABOUR, entries.map { it.harvestId to it.appliedRate?.currency }, expenses)

internal fun equipmentCostNote(lines: List<EquipmentLine>, expenses: List<Expense>): String? =
    resourceCostNote(RecollectionBucket.EQUIPMENT, lines.map { it.harvestId to it.appliedPrice?.currency }, expenses)

/** Operational snapshots only disclose incomplete coverage; they never contribute money. */
private fun resourceCostNote(bucket: RecollectionBucket, resources: List<Pair<UUID, String?>>, expenses: List<Expense>): String? {
    val costs = expenses.filter { RecollectionBucket.of(it) == bucket }
    val unconfirmed = costs.any { it.status == ExpenseStatus.DRAFT } || resources.any { (day, currency) ->
        currency == null || costs.none { it.status == ExpenseStatus.POSTED && it.harvestId == day && it.currency == currency }
    }
    return if (unconfirmed) "Hay precios o costes sin confirmar · solo suma lo confirmado" else null
}

internal fun List<RecollectionCurrency>.costKgLabel(): String =
    if (isEmpty()) "—" else joinToString("\n") { ledger ->
        ledger.costPerKgMinor?.let { "${Money.format(it, ledger.currency)}/kg" } ?: "— (${ledger.currency})"
    }

@Composable
internal fun RecollectionTotalCards(ledger: List<RecollectionCurrency>, day: Boolean, weighed: String?) {
    MoKpiMetric(if (day) "Coste del día" else "Total recogida", ledger.moneyLabel(),
        Modifier.fillMaxWidth().testTag(if (day) "day-cost" else "dashboard-cost"),
        icon = MoIcons.Euro, kind = MoKpiKind.TOTAL,
        supportingText = "Solo gastos confirmados · pagar no cambia el coste")
    MoKpiMetric("Coste/kg", ledger.costKgLabel(),
        Modifier.fillMaxWidth().testTag(if (day) "day-cost-per-kg" else "dashboard-cost-per-kg"),
        icon = MoIcons.Percent, kind = MoKpiKind.TOTAL,
        supportingText = weighed?.let { "Sobre $it pesados" } ?: "Sin kilos pesados")
}
