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
import com.isivoltpro.maginaolivo.feature.harvests.moneyLabel
import com.isivoltpro.maginaolivo.domain.expense.RecollectionLedger
import com.isivoltpro.maginaolivo.domain.expense.RecollectionBucket
import com.isivoltpro.maginaolivo.domain.expense.Money
import com.isivoltpro.maginaolivo.domain.notebook.CampaignNotebook
import com.isivoltpro.maginaolivo.domain.notebook.DiaryEntry
import com.isivoltpro.maginaolivo.domain.notebook.FarmNotebook
import com.isivoltpro.maginaolivo.domain.labour.LabourSummary
import com.isivoltpro.maginaolivo.domain.notebook.PhytoRecord
import com.isivoltpro.maginaolivo.domain.notebook.costs
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
import com.isivoltpro.maginaolivo.ui.components.MoKpiKind
import com.isivoltpro.maginaolivo.ui.components.MoKpiMetric
import com.isivoltpro.maginaolivo.ui.theme.MoOliveDark
import com.isivoltpro.maginaolivo.ui.theme.MoSpacing
import com.isivoltpro.maginaolivo.ui.theme.MoTextSecondary
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** A Campaign's records read as its Farm's Diario (screens and tests built from one Campaign). */
@Composable
internal fun DiaryView(notebook: CampaignNotebook, actions: NotebookActions, today: LocalDate = LocalDate.now()) =
    DiaryView(FarmNotebook.of(notebook), actions, today)

/**
 * UX-E Diario, #417 — the Farm's timeline, newest day first: work, jornadas, pesadas and
 * expenses through the whole year, with or without a Campaign; each row the canonical record.
 */
@Composable
internal fun DiaryView(notebook: FarmNotebook, actions: NotebookActions, today: LocalDate = LocalDate.now()) {
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
            NotebookDayMarker(day.date, today)
            day.entries.forEach { entry ->
                when (entry) {
                    is DiaryEntry.Work -> WorkRow(entry.activity) { actions.onActivity(entry.activity.id) }
                    is DiaryEntry.HarvestEntry -> HarvestRow(
                        entry.harvest,
                        notebook.pesadaCount(entry.harvest.id),
                        notebook.labourFor(entry.harvest.id),
                        notebook.jornadaCost(entry.harvest.id),
                        notebook.jornadaYieldLabel(entry.harvest.id),
                    ) { actions.onHarvest(entry.harvest.id) }
                    is DiaryEntry.DeliveryEntry -> DeliveryRow(entry.delivery) { actions.onDelivery(entry.delivery.id) }
                    is DiaryEntry.ExpenseEntry -> ExpenseRow(entry.expense, entry.relatedWork) { actions.onExpense(entry.expense.id) }
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
internal fun PhytoView(notebook: FarmNotebook, actions: NotebookActions) {
    // #417: the Farm's treatments, whatever the Campaign; never «en esta campaña».
    val records = notebook.phytoRecords
    if (records.isEmpty()) {
        MoEmptyState(
            "Sin tratamientos registrados",
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
    MoSecondaryButton("Jornales y pagos", { actions.onLabour(notebook.campaign.id) }, Modifier.fillMaxWidth().testTag("notebook-open-labour"))
    val costs = notebook.costs
    val draftCount = notebook.expenses.count { it.status == com.isivoltpro.maginaolivo.domain.expense.ExpenseStatus.DRAFT }
    val currencies = RecollectionLedger.of(notebook.campaign.id, notebook.expenses, notebook.deliveries)
    Text(
        if (currencies.isNotEmpty()) "Total contabilizado: ${currencies.moneyLabel()}" else "Sin gastos contabilizados",
        style = MaterialTheme.typography.titleMedium,
        color = MoOliveDark,
        modifier = Modifier.testTag("notebook-expenses-total"),
    )
    if (draftCount > 0) {
        Text(
            if (draftCount == 1) "1 borrador sin contar" else "$draftCount borradores sin contar",
            style = MaterialTheme.typography.bodySmall,
            color = MoTextSecondary,
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(MoSpacing.xs)) {
        MoKpiMetric(
            "Jornales",
            currencies.moneyLabel(RecollectionBucket.LABOUR),
            Modifier.fillMaxWidth().testTag("notebook-costs-labour"),
            icon = MoIcons.People,
            kind = MoKpiKind.JORNALES,
            supportingText = notebook.labourSummary.takeUnless { it.isEmpty }?.label() ?: "Sin jornales anotados en los días de recolección",
        )
        MoKpiMetric(
            "Maquinaria",
            currencies.moneyLabel(RecollectionBucket.EQUIPMENT),
            Modifier.fillMaxWidth().testTag("notebook-costs-machinery"),
            icon = MoIcons.Tractor,
            kind = MoKpiKind.MAQUINARIA,
            supportingText = listOfNotNull(
                costs.machineUses.takeIf { it > 0 }?.let { uses ->
                    val hours = costs.machineHours.takeIf { it > 0 }?.let { " · ${formatHours(it)}" } ?: ""
                    (if (uses == 1) "1 trabajo con máquina" else "$uses trabajos con máquina") + hours
                },
                notebook.equipmentSummary.takeUnless { it.isEmpty }?.label(),
            ).joinToString(" · ").ifEmpty { "Sin uso de maquinaria anotado" },
        )
        MoKpiMetric("Otros gastos", currencies.moneyLabel(RecollectionBucket.OTHER), Modifier.fillMaxWidth().testTag("notebook-costs-other"),
            icon = MoIcons.Euro, kind = MoKpiKind.COSTES, supportingText = "Combustible, transporte, reparaciones y otros")
        MoKpiMetric(
            "Facturas y documentos",
            if (costs.documents.isEmpty()) "—" else if (costs.documents.size == 1) "1 papel" else "${costs.documents.size} papeles",
            Modifier.fillMaxWidth().testTag("notebook-costs-documents"),
            icon = MoIcons.Document,
            kind = MoKpiKind.COSTES,
            supportingText = if (costs.documents.isEmpty()) "Sin facturas ni tickets en esta campaña" else "Con número de factura o escaneados",
        )
    }
    currencies.forEach { currency ->
        MoSectionHeader("Por categoría · ${currency.currency}")
        currency.posted.groupBy { it.category }.forEach { (category, rows) ->
            Row(Modifier.fillMaxWidth().testTag("notebook-costs-category")) {
                Text(category.label(), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                val amount = runCatching { rows.fold(0L) { total, row -> Math.addExact(total, row.amountMinor) } }.getOrNull()
                Text(amount?.let { Money.format(it, currency.currency) } ?: "No disponible", style = MaterialTheme.typography.bodyMedium, color = MoOliveDark)
            }
        }
    }
    if (notebook.expenses.isEmpty()) {
        MoEmptyState(
            "Sin gastos en esta campaña",
            "Gastos confirmados y facturas aparecerán aquí. Los pagos se consultan en Jornales.",
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

/**
 * #417 Gastos — the Farm's one Expense ledger: general costs and those explicitly linked to a
 * Campaign shown apart, never one total confused with the other. Money only from posted Expenses.
 */
@Composable
internal fun FarmCostsView(notebook: FarmNotebook, campaign: com.isivoltpro.maginaolivo.domain.campaign.Campaign?, actions: NotebookActions) {
    campaign?.let {
        MoSecondaryButton(
            "Jornales y pagos · ${campaignLabel(it.name)}",
            { actions.onLabour(it.id) },
            Modifier.fillMaxWidth().testTag("notebook-open-labour"),
        )
    }
    val currencies = RecollectionLedger.posted(notebook.expenses)
    val draftCount = notebook.expenses.count { it.status == com.isivoltpro.maginaolivo.domain.expense.ExpenseStatus.DRAFT }
    Text(
        if (currencies.isNotEmpty()) "Total contabilizado de la finca: ${currencies.moneyLabel()}" else "Sin gastos contabilizados",
        style = MaterialTheme.typography.titleMedium,
        color = MoOliveDark,
        modifier = Modifier.testTag("notebook-expenses-total"),
    )
    if (draftCount > 0) {
        Text(if (draftCount == 1) "1 borrador sin contar" else "$draftCount borradores sin contar", style = MaterialTheme.typography.bodySmall, color = MoTextSecondary)
    }
    val recollection = RecollectionLedger.posted(notebook.recollectionExpenses)
    val general = RecollectionLedger.posted(notebook.generalExpenses)
    val machines = notebook.machineWork
    val documents = notebook.expenses.filter { !it.invoiceNumber.isNullOrBlank() || it.origin == com.isivoltpro.maginaolivo.domain.expense.ExpenseOrigin.DOCUMENT_OCR }
    Column(verticalArrangement = Arrangement.spacedBy(MoSpacing.xs)) {
        MoKpiMetric("Recogida", if (recollection.isEmpty()) "—" else recollection.moneyLabel(), Modifier.fillMaxWidth().testTag("notebook-costs-recollection"),
            icon = MoIcons.Delivery, kind = MoKpiKind.COSTES, supportingText = "Solo lo vinculado a una campaña")
        MoKpiMetric("Fuera de campaña", if (general.isEmpty()) "—" else general.moneyLabel(), Modifier.fillMaxWidth().testTag("notebook-costs-general"),
            icon = MoIcons.Euro, kind = MoKpiKind.COSTES, supportingText = "Poda, tratamientos, riego, gasóleo y otros gastos de la finca")
        MoKpiMetric("Jornales", currencies.moneyLabel(RecollectionBucket.LABOUR), Modifier.fillMaxWidth().testTag("notebook-costs-labour"),
            icon = MoIcons.People, kind = MoKpiKind.JORNALES,
            supportingText = LabourSummary.of(notebook.labour).takeUnless { it.isEmpty }?.label() ?: "Sin jornales anotados")
        MoKpiMetric("Maquinaria", currencies.moneyLabel(RecollectionBucket.EQUIPMENT), Modifier.fillMaxWidth().testTag("notebook-costs-machinery"),
            icon = MoIcons.Tractor, kind = MoKpiKind.MAQUINARIA,
            supportingText = listOfNotNull(
                machines.size.takeIf { it > 0 }?.let { if (it == 1) "1 trabajo con máquina" else "$it trabajos con máquina" },
                notebook.equipmentSummary.takeUnless { it.isEmpty }?.label(),
            ).joinToString(" · ").ifEmpty { "Sin uso de maquinaria anotado" })
        MoKpiMetric("Facturas y documentos", if (documents.isEmpty()) "—" else if (documents.size == 1) "1 papel" else "${documents.size} papeles",
            Modifier.fillMaxWidth().testTag("notebook-costs-documents"), icon = MoIcons.Document, kind = MoKpiKind.COSTES,
            supportingText = if (documents.isEmpty()) "Sin facturas ni tickets" else "Con número de factura o escaneados")
    }
    if (notebook.expenses.isEmpty()) {
        MoEmptyState(
            "Sin gastos en esta finca",
            "Los gastos que registres, generales o de recogida, aparecerán aquí.",
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

/**
 * UX-E Campaña — CR-011: the one place for the Campaign as a whole. «+ Nueva pesada», the
 * Pesadas still waiting for their yield, then the summary; the per-Farm Cuaderno that repeated
 * these is gone.
 */
@Composable
internal fun CampaignView(notebook: CampaignNotebook, state: NotebookUiState, actions: NotebookActions, onSelectCampaign: (java.util.UUID) -> Unit = {}) {
    RecollectionActions(notebook, actions)
    SummaryTab(notebook, state.comparison, payments = state.labourPayments, onLabour = { actions.onLabour(notebook.campaign.id) }, onExpenses = { actions.onCampaignExpenses?.invoke(notebook.campaign.id) ?: actions.onExpenses() }, onHarvest = actions.onHarvest, onSelectCampaign = onSelectCampaign)
}

private fun formatHectares(m2: Double): String =
    String.format(SPANISH, "%.2f ha", m2 / 10_000.0)

private fun formatHours(hours: Double): String =
    if (hours % 1.0 == 0.0) "${hours.toLong()} h" else String.format(SPANISH, "%.1f h", hours)

private val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", SPANISH)
