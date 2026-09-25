package com.isivoltpro.maginaolivo.feature.notebook

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.isivoltpro.maginaolivo.domain.expense.ExpenseSummary
import com.isivoltpro.maginaolivo.domain.expense.Money
import com.isivoltpro.maginaolivo.domain.notebook.CampaignNotebook
import com.isivoltpro.maginaolivo.domain.notebook.DiaryEntry
import com.isivoltpro.maginaolivo.domain.notebook.PhytoRecord
import com.isivoltpro.maginaolivo.domain.notebook.costs
import com.isivoltpro.maginaolivo.domain.notebook.diary
import com.isivoltpro.maginaolivo.domain.notebook.phytoRecords
import com.isivoltpro.maginaolivo.feature.activities.label
import com.isivoltpro.maginaolivo.feature.activities.tone
import com.isivoltpro.maginaolivo.feature.expenses.label
import com.isivoltpro.maginaolivo.ui.components.MoCompactListItem
import com.isivoltpro.maginaolivo.ui.components.MoEmptyState
import com.isivoltpro.maginaolivo.ui.components.MoIcons
import com.isivoltpro.maginaolivo.ui.components.MoSecondaryButton
import com.isivoltpro.maginaolivo.ui.components.MoSectionHeader
import com.isivoltpro.maginaolivo.ui.components.MoStatusChip
import com.isivoltpro.maginaolivo.ui.components.MoStatusTone
import com.isivoltpro.maginaolivo.ui.components.MoSummaryMetric
import com.isivoltpro.maginaolivo.ui.theme.MoOliveDark
import com.isivoltpro.maginaolivo.ui.theme.MoSpacing
import com.isivoltpro.maginaolivo.ui.theme.MoTextSecondary
import java.time.format.DateTimeFormatter

/**
 * UX-E Diario — one timeline of the Campaign, newest day first: work, jornadas, pesadas and
 * expenses, each row the canonical record and each tap its own screen.
 */
@Composable
internal fun DiaryView(notebook: CampaignNotebook, actions: NotebookActions) {
    val days = notebook.diary
    if (days.isEmpty()) {
        MoEmptyState(
            "Aún no hay nada anotado",
            "Lo que registres hoy aparecerá aquí al momento, ordenado por días.",
            icon = MoIcons.Checklist,
            modifier = Modifier.testTag("notebook-diary-empty"),
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(MoSpacing.xs), modifier = Modifier.testTag("notebook-diary")) {
        days.forEach { day ->
            MoSectionHeader(day.date.format(LONG_DAY).replaceFirstChar { it.titlecase(SPANISH) })
            day.entries.forEach { entry ->
                when (entry) {
                    is DiaryEntry.Work -> WorkRow(entry.activity) { actions.onActivity(entry.activity.id) }
                    is DiaryEntry.HarvestEntry -> HarvestRow(
                        entry.harvest,
                        notebook.pesadaCount(entry.harvest.id),
                        notebook.labourFor(entry.harvest.id),
                        notebook.jornadaCost(entry.harvest.id),
                    ) { actions.onHarvest(entry.harvest.id) }
                    is DiaryEntry.DeliveryEntry -> DeliveryRow(entry.delivery) { actions.onDelivery(entry.delivery.id) }
                    is DiaryEntry.ExpenseEntry -> ExpenseRow(entry.expense) { actions.onExpense(entry.expense.id) }
                }
            }
        }
    }
}

/**
 * UX-E Fitosanitario — the same treatment Activities, read as notebook entries. Only what was
 * written is shown; what is missing is named ("Falta: materia activa"), never filled in.
 */
@Composable
internal fun PhytoView(notebook: CampaignNotebook, actions: NotebookActions) {
    val records = notebook.phytoRecords
    if (records.isEmpty()) {
        MoEmptyState(
            "Sin tratamientos en esta campaña",
            "Los tratamientos que registres aparecerán aquí con su producto, su dosis y su parcela.",
            icon = MoIcons.Spray,
            modifier = Modifier.testTag("notebook-phyto-empty"),
        )
        return
    }
    val incomplete = records.count { it.gaps.isNotEmpty() }
    Text(
        when {
            incomplete == 0 -> if (records.size == 1) "1 tratamiento, completo" else "${records.size} tratamientos, todos completos"
            incomplete == 1 -> "${records.size} tratamientos · 1 con datos por completar"
            else -> "${records.size} tratamientos · $incomplete con datos por completar"
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MoTextSecondary,
        modifier = Modifier.testTag("notebook-phyto-summary"),
    )
    records.forEach { record -> PhytoRow(record) { actions.onActivity(record.activity.id) } }
}

@Composable
private fun PhytoRow(record: PhytoRecord, onClick: () -> Unit) {
    val lines = listOfNotNull(
        listOfNotNull(
            record.date.format(DAY),
            when (record.parcelNames.size) {
                0 -> null
                1 -> record.parcelNames.single()
                else -> "${record.parcelNames.size} parcelas"
            },
            record.surfaceM2?.let { formatHectares(it) },
        ).joinToString(" · "),
        listOfNotNull(record.activeSubstance, record.dose?.let { "dosis $it" }, record.quantity?.let { "total $it" })
            .takeIf { it.isNotEmpty() }?.joinToString(" · "),
        record.reason?.let { "Motivo: $it" },
        listOfNotNull(
            record.machinery.takeIf { it.isNotEmpty() }?.joinToString(", "),
            record.crew,
        ).takeIf { it.isNotEmpty() }?.joinToString(" · "),
        record.gaps.takeIf { it.isNotEmpty() }?.let { gaps -> "Falta: " + gaps.joinToString(", ") { it.label } },
    )
    MoCompactListItem(
        title = record.productName ?: record.activity.description,
        subtitle = lines.joinToString("\n"),
        icon = MoIcons.Spray,
        onClick = onClick,
        modifier = Modifier.testTag("notebook-phyto-record"),
        trailing = {
            if (record.gaps.isNotEmpty()) MoStatusChip("Incompleto", tone = MoStatusTone.Warning)
            else MoStatusChip(record.status.label(), tone = record.status.tone())
        },
    )
}

/**
 * UX-E Gastos — the one Expense ledger of the Campaign, grouped as the farmer asks: total,
 * people, machines, papers. Money only from posted Expenses; jornales and machine use add
 * people and hours, never a second amount.
 */
@Composable
internal fun CostsView(notebook: CampaignNotebook, actions: NotebookActions) {
    val costs = notebook.costs
    val ledger = costs.ledger
    Text(
        if (ledger.postedCount > 0) "Total contabilizado: ${Money.format(ledger.totalMinor, ledger.currency)}" else "Sin gastos contabilizados",
        style = MaterialTheme.typography.titleMedium,
        color = MoOliveDark,
        modifier = Modifier.testTag("notebook-expenses-total"),
    )
    if (ledger.draftCount > 0) {
        Text(
            if (ledger.draftCount == 1) "1 borrador sin contar" else "${ledger.draftCount} borradores sin contar",
            style = MaterialTheme.typography.bodySmall,
            color = MoTextSecondary,
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(MoSpacing.xs)) {
        MoSummaryMetric(
            "Jornales",
            money(costs.labourMoney),
            Modifier.fillMaxWidth().testTag("notebook-costs-labour"),
            icon = MoIcons.People,
            supportingText = notebook.labourSummary.takeUnless { it.isEmpty }?.label() ?: "Sin jornales anotados en las jornadas",
        )
        MoSummaryMetric(
            "Maquinaria",
            money(costs.machineryMoney),
            Modifier.fillMaxWidth().testTag("notebook-costs-machinery"),
            icon = MoIcons.Tractor,
            supportingText = listOfNotNull(
                costs.machineUses.takeIf { it > 0 }?.let { uses ->
                    val hours = costs.machineHours.takeIf { it > 0 }?.let { " · ${formatHours(it)}" } ?: ""
                    (if (uses == 1) "1 trabajo con máquina" else "$uses trabajos con máquina") + hours
                },
                notebook.equipmentSummary.takeUnless { it.isEmpty }?.label(),
            ).joinToString(" · ").ifEmpty { "Sin uso de maquinaria anotado" },
        )
        MoSummaryMetric(
            "Facturas y documentos",
            if (costs.documents.isEmpty()) "—" else if (costs.documents.size == 1) "1 papel" else "${costs.documents.size} papeles",
            Modifier.fillMaxWidth().testTag("notebook-costs-documents"),
            icon = MoIcons.Document,
            supportingText = if (costs.documents.isEmpty()) "Sin facturas ni tickets en esta campaña" else "Con número de factura o escaneados",
        )
    }
    if (ledger.byCategory.isNotEmpty()) {
        MoSectionHeader("Por categoría")
        ledger.byCategory.entries.sortedByDescending { it.value }.forEach { (category, amount) ->
            Row(Modifier.fillMaxWidth().testTag("notebook-costs-category")) {
                Text(category.label(), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(Money.format(amount, ledger.currency), style = MaterialTheme.typography.bodyMedium, color = MoOliveDark)
            }
        }
    }
    if (notebook.expenses.isEmpty()) {
        MoEmptyState(
            "Sin gastos en esta campaña",
            "Gastos, jornales pagados y facturas aparecerán aquí.",
            icon = MoIcons.Euro,
            modifier = Modifier.testTag("notebook-expenses-empty"),
        )
    } else {
        MoSectionHeader("Movimientos")
        notebook.expenses.sortedByDescending { it.expenseDate }.forEach { expense ->
            ExpenseRow(expense) { actions.onExpense(expense.id) }
        }
    }
}

/** UX-E Campaña — the Campaign summary plus the Pesadas still waiting for their yield. */
@Composable
internal fun CampaignView(notebook: CampaignNotebook, state: NotebookUiState, actions: NotebookActions) {
    if (notebook.pendingYieldCount > 0) {
        MoSecondaryButton(
            if (notebook.pendingYieldCount == 1) "1 pesada sin rendimiento" else "${notebook.pendingYieldCount} pesadas sin rendimiento",
            actions.onPendingYields,
            Modifier.fillMaxWidth().testTag("notebook-pending-yields"),
        )
    }
    SummaryTab(notebook, state.comparison)
}

private fun money(summary: ExpenseSummary): String =
    if (summary.postedCount == 0) "—" else Money.format(summary.totalMinor, summary.currency)

private fun formatHectares(m2: Double): String =
    String.format(SPANISH, "%.2f ha", m2 / 10_000.0)

private fun formatHours(hours: Double): String =
    if (hours % 1.0 == 0.0) "${hours.toLong()} h" else String.format(SPANISH, "%.1f h", hours)

private val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", SPANISH)
