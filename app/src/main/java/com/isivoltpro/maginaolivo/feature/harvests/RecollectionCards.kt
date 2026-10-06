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
    // #449: only a draft that is not one side of the day's explicit #475 pair is pending.
    val unconfirmed = costs.any { RecollectionCostCompleteness.isPendingDraft(it, costs) } || resources.any { (day, currency) ->
        currency == null || costs.none { it.status == ExpenseStatus.POSTED && it.harvestId == day && it.currency == currency }
    }
    return if (unconfirmed) "Hay precios o costes sin confirmar · solo suma lo confirmado" else null
}

internal fun List<RecollectionCurrency>.costKgLabel(): String =
    if (isEmpty()) "—" else joinToString("\n") { ledger ->
        ledger.costPerKgMilli?.let { CostPerKg.format(it, ledger.currency) } ?: "— (${ledger.currency})"
    }

/** #449: what is still unknown, in the farmer's words; null when the cost is complete. */
internal fun RecollectionCostCompleteness.pendingLabel(): String? = if (complete) null else {
    val what = listOfNotNull(
        "jornales sin precio".takeIf { RecollectionCostCompleteness.Reason.LABOUR_UNPRICED in reasons },
        "maquinaria sin precio".takeIf { RecollectionCostCompleteness.Reason.EQUIPMENT_UNPRICED in reasons },
        "costes del día sin calcular".takeIf { RecollectionCostCompleteness.Reason.UNPOSTED_RESOURCE_COST in reasons },
        "gastos sin confirmar".takeIf { RecollectionCostCompleteness.Reason.DRAFT_COSTS in reasons },
    )
    "Incompleto · " + what.joinToString(", ")
}

@Composable
internal fun RecollectionTotalCards(
    ledger: List<RecollectionCurrency>,
    day: Boolean,
    weighed: String?,
    /**
     * #449: unknown costs are named, never summed as 0; the money stays the posted ledger. Null
     * while any of the day's sources is still loading or could not be read: never shown as final.
     */
    completeness: RecollectionCostCompleteness?,
) {
    val pending = if (completeness == null) "Comprobando si faltan costes…" else completeness.pendingLabel()
    MoKpiMetric(
        when {
            pending != null -> if (day) "Coste contabilizado del día" else "Total contabilizado"
            day -> "Coste del día"
            else -> "Total recogida"
        },
        ledger.moneyLabel(),
        Modifier.fillMaxWidth().testTag(if (day) "day-cost" else "dashboard-cost"),
        icon = MoIcons.Euro, kind = MoKpiKind.TOTAL,
        supportingText = listOfNotNull(pending, "Solo gastos confirmados · pagar no cambia el coste").joinToString("\n"))
    MoKpiMetric(if (pending != null) "Coste contabilizado/kg" else "Coste/kg", ledger.costKgLabel(),
        Modifier.fillMaxWidth().testTag(if (day) "day-cost-per-kg" else "dashboard-cost-per-kg"),
        icon = MoIcons.Percent, kind = MoKpiKind.TOTAL,
        supportingText = listOfNotNull(pending, weighed?.let { "Sobre $it pesados" } ?: "Sin kilos pesados").joinToString("\n"))
}
