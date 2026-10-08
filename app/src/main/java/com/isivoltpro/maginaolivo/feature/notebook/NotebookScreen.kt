package com.isivoltpro.maginaolivo.feature.notebook

import com.isivoltpro.maginaolivo.feature.expenses.DATE_FORMAT
import com.isivoltpro.maginaolivo.domain.analytics.CampaignDashboard
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import com.isivoltpro.maginaolivo.feature.harvests.moneyLabel
import com.isivoltpro.maginaolivo.feature.harvests.title
import com.isivoltpro.maginaolivo.feature.harvests.icon
import com.isivoltpro.maginaolivo.feature.harvests.UNKNOWN_DAY_ORIGIN
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.isivoltpro.maginaolivo.app.LocalPersistence
import com.isivoltpro.maginaolivo.domain.activity.Activity
import com.isivoltpro.maginaolivo.domain.analytics.CampaignComparison
import com.isivoltpro.maginaolivo.domain.analytics.CampaignSeries
import com.isivoltpro.maginaolivo.domain.delivery.Delivery
import com.isivoltpro.maginaolivo.domain.delivery.Percent
import com.isivoltpro.maginaolivo.domain.expense.Expense
import com.isivoltpro.maginaolivo.domain.expense.ExpenseStatus
import com.isivoltpro.maginaolivo.domain.expense.Money
import com.isivoltpro.maginaolivo.domain.harvest.Harvest
import com.isivoltpro.maginaolivo.domain.harvest.Weight
import com.isivoltpro.maginaolivo.domain.labour.LabourSummary
import com.isivoltpro.maginaolivo.domain.notebook.CampaignNotebook
import com.isivoltpro.maginaolivo.domain.notebook.RecollectionItem
import com.isivoltpro.maginaolivo.domain.notebook.legacyUnweighedGrams
import com.isivoltpro.maginaolivo.domain.notebook.pendingDeliveryGrams
import com.isivoltpro.maginaolivo.feature.activities.icon
import com.isivoltpro.maginaolivo.feature.activities.label
import com.isivoltpro.maginaolivo.feature.activities.tone
import com.isivoltpro.maginaolivo.feature.expenses.label
import com.isivoltpro.maginaolivo.ui.components.MoCompactListItem
import com.isivoltpro.maginaolivo.ui.components.MoEmptyState
import com.isivoltpro.maginaolivo.ui.components.MoIcons
import com.isivoltpro.maginaolivo.ui.components.MoPrimaryButton
import com.isivoltpro.maginaolivo.ui.components.MoSecondaryButton
import com.isivoltpro.maginaolivo.ui.components.MoSectionHeader
import com.isivoltpro.maginaolivo.ui.components.MoMetricGrid
import com.isivoltpro.maginaolivo.ui.components.MoStatusChip
import com.isivoltpro.maginaolivo.ui.components.MoStatusTone
import com.isivoltpro.maginaolivo.ui.components.MoKpiKind
import com.isivoltpro.maginaolivo.ui.components.MoKpiMetric
import com.isivoltpro.maginaolivo.ui.theme.MoOliveDark
import com.isivoltpro.maginaolivo.ui.theme.MoShape
import com.isivoltpro.maginaolivo.ui.theme.MoSpacing
import com.isivoltpro.maginaolivo.ui.theme.MoTextSecondary
import com.isivoltpro.maginaolivo.ui.theme.MoWarmWhite
import androidx.compose.ui.graphics.vector.ImageVector
import com.isivoltpro.maginaolivo.data.local.model.ActivityStatus
import com.isivoltpro.maginaolivo.data.local.model.isRunning
import com.isivoltpro.maginaolivo.ui.components.MoIconTone
import com.isivoltpro.maginaolivo.ui.theme.MoSurfaceSoft
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

/** Where each Cuaderno row and action leads: always the canonical screen of that record. */
data class NotebookActions(
    val onActivity: (UUID) -> Unit = {},
    val onHarvest: (UUID) -> Unit = {},
    val onDelivery: (UUID) -> Unit = {},
    val onExpense: (UUID) -> Unit = {},
    /** The Farm's work list (every work of the Farm, done and planned). */
    val onWorks: () -> Unit = {},
    val onHarvests: () -> Unit = {},
    val onDeliveries: () -> Unit = {},
    /** Phase 19C: the Pesadas still waiting for their yield. */
    val onPendingYields: () -> Unit = {},
    val onExpenses: () -> Unit = {},
    val onCampaigns: () -> Unit = {},
    val onLabour: (UUID) -> Unit = {},
    val onCampaignExpenses: ((UUID) -> Unit)? = null,
)

@Composable
internal fun WorkRow(work: Activity, onClick: () -> Unit) {
    val pending = work.status == ActivityStatus.PLANNED || work.status == ActivityStatus.DRAFT
    MoCompactListItem(
        title = work.description,
        // The day is on the day line above, so the row starts with what was done.
        subtitle = listOfNotNull(
            work.type.label(),
            work.targets.takeIf { it.isNotEmpty() }?.let { if (it.size == 1) it.single().parcelName else "${it.size} parcelas" },
        ).joinToString(" · "),
        icon = work.type.icon(),
        onClick = onClick,
        modifier = Modifier.testTag("notebook-work"),
        container = if (pending) MoSurfaceSoft else MoWarmWhite,
        trailing = { MoStatusChip(work.status.label(), tone = work.status.tone(), icon = work.status.chipIcon()) },
    )
}

/**
 * CR-011 §10–11 — the recolección entry of the Campaña view. «+ Nueva pesada» comes first while
 * the Campaign runs (the Pesada is the record; its day is grouped automatically), then the
 * Pesadas still waiting for their yield and the hand-typed history named apart (A2). The days
 * themselves are listed once, in the Diario; the figures once, in the summary below.
 */
@Composable
internal fun RecollectionActions(notebook: CampaignNotebook, actions: NotebookActions) {
    if (notebook.campaign.status.isRunning) {
        MoPrimaryButton("+ Nueva pesada", actions.onDeliveries, Modifier.fillMaxWidth().testTag("notebook-open-deliveries"))
    }
    if (notebook.pendingYieldCount > 0) {
        MoSecondaryButton(
            if (notebook.pendingYieldCount == 1) "1 pesada sin rendimiento" else "${notebook.pendingYieldCount} pesadas sin rendimiento",
            actions.onPendingYields,
            Modifier.fillMaxWidth().testTag("notebook-pending-yields"),
        )
    }
    val legacy = notebook.legacyUnweighedGrams
    if (legacy == null || legacy > 0) {
        Text(
            legacy?.let { "Además, ${Weight.format(it)} registrados sin pesada (histórico)" }
                ?: "Kilos históricos sin pesada no disponibles",
            style = MaterialTheme.typography.bodySmall,
            color = MoTextSecondary,
            modifier = Modifier.testTag("notebook-legacy-kilos"),
        )
    }
}

@Composable
internal fun HarvestRow(
    harvest: Harvest,
    pesadas: Int,
    labour: LabourSummary,
    cost: List<com.isivoltpro.maginaolivo.domain.expense.RecollectionCurrency>,
    /** "rend. 21 %" / "rend. pendiente": the Pesadas are not listed again, so their yield shows here. */
    yieldLabel: String? = null,
    onClick: () -> Unit,
) {
    MoCompactListItem(
        title = "Día de recolección · ${if (harvest.awaitingPesadas) "kg pendientes de pesada" else Weight.format(harvest.totalGrams)}",
        subtitle = listOfNotNull(
            when {
                // #458: a day with no Pesada yet has no known origin; it is not the whole Farm.
                harvest.shares.isEmpty() -> UNKNOWN_DAY_ORIGIN
                harvest.shares.size == 1 -> harvest.shares.single().parcelName
                else -> "${harvest.shares.size} parcelas"
            },
            when (pesadas) {
                0 -> null
                1 -> "1 pesada"
                else -> "$pesadas pesadas"
            },
            labour.takeUnless { it.isEmpty }?.let { if (it.people == 1) "1 jornal" else "${it.people} jornales" },
            // #450: one amount per currency, never a euro total standing for all.
            cost.takeIf { it.isNotEmpty() }?.joinToString(" · ") { ledger ->
                ledger.amount()?.let { Money.format(it, ledger.currency) } ?: "Importe no disponible (${ledger.currency})"
            },
            yieldLabel,
        ).joinToString(" · "),
        icon = MoIcons.Harvest,
        // The Jornada is field work (olive); its Pesadas are value (gold); its costs are earth.
        iconTint = MoIconTone.GROVE.tint,
        iconContainer = MoIconTone.GROVE.container,
        onClick = onClick,
        modifier = Modifier.testTag("notebook-harvest"),
    )
}

@Composable
internal fun DeliveryRow(delivery: Delivery, onClick: () -> Unit) {
    // The cooperative is never hidden on a Pesada row (CR-005 §5).
    MoCompactListItem(
        title = listOfNotNull("Pesada", (delivery.ticketNumber ?: delivery.deliveryNumber)?.let { "nº $it" }).joinToString(" "),
        subtitle = listOfNotNull(delivery.destinationName, delivery.origin?.label, Weight.format(delivery.netGrams)).joinToString(" · "),
        icon = MoIcons.Delivery,
        onClick = onClick,
        modifier = Modifier.testTag("notebook-delivery"),
        trailing = {
            val fat = delivery.analysis?.fatYieldHundredths
            if (fat != null) MoStatusChip(Percent.format(fat), tone = MoStatusTone.Success)
            else MoStatusChip("Rendimiento pendiente", tone = MoStatusTone.Warning)
        },
    )
}

@Composable
internal fun ExpenseRow(expense: Expense, relatedWork: String? = null, onClick: () -> Unit) {
    MoCompactListItem(
        title = expense.concept,
        subtitle = listOfNotNull(
            "${expense.category.label()} · ${Money.format(expense.amountMinor, expense.currency)}",
            // #478: a Gasto tied to a work says which, and is opened as the Gasto it is.
            relatedWork?.let { "Relacionado con $it" },
        ).joinToString(" · "),
        icon = MoIcons.Euro,
        iconTint = MoIconTone.MONEY.tint,
        iconContainer = MoIconTone.MONEY.container,
        onClick = onClick,
        modifier = Modifier.testTag("notebook-expense"),
        container = if (expense.status == ExpenseStatus.DRAFT) MoSurfaceSoft else MoWarmWhite,
        trailing = { if (expense.status == ExpenseStatus.DRAFT) MoStatusChip("Borrador", tone = MoStatusTone.Neutral) },
    )
}

@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
internal fun SummaryTab(
    notebook: CampaignNotebook,
    comparison: List<CampaignComparison> = emptyList(),
    today: LocalDate = LocalDate.now(),
    payments: List<com.isivoltpro.maginaolivo.domain.labour.LabourPayment> = emptyList(),
    onLabour: () -> Unit = {},
    onExpenses: () -> Unit = {},
    onHarvest: (UUID) -> Unit = {},
    /** #355: a campaign tapped in the history opens that campaign here. */
    onSelectCampaign: (UUID) -> Unit = {},
) {
    val deliveries = notebook.deliverySummary
    val ledger = com.isivoltpro.maginaolivo.domain.expense.RecollectionLedger.of(notebook.campaign.id, notebook.expenses, notebook.deliveries)
    var machineryDetail by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(MoSpacing.xs), modifier = Modifier.testTag("notebook-summary")) {
        MoSectionHeader("Producción")
        CampaignAtAGlance(CampaignDashboard.of(notebook, today), deliveries.deliveredGrams)
        MoKpiMetric("Días de recolección", com.isivoltpro.maginaolivo.feature.harvests.harvestDayCount(notebook.harvests).toString(), Modifier.fillMaxWidth(), icon = MoIcons.Harvest, kind = MoKpiKind.CAMPAIGN)
        MoKpiMetric("Kg pesados", if (deliveries.deliveryCount == 0) "—" else deliveries.deliveredGrams?.let(Weight::format) ?: "No disponible", Modifier.fillMaxWidth(),
            icon = MoIcons.Delivery, kind = MoKpiKind.PESADAS, supportingText = if (deliveries.deliveryCount == 1) "1 pesada" else "${deliveries.deliveryCount} pesadas")
        MoKpiMetric("Rendimiento", deliveries.fatYield?.let { Percent.format(it.hundredths) } ?: "—", Modifier.fillMaxWidth(),
            icon = MoIcons.Percent, kind = MoKpiKind.YIELD,
            supportingText = deliveries.fatYield?.let { yield ->
                deliveries.coveragePercent(yield)?.let { "Sobre el $it % de los kilos" } ?: "Cobertura no disponible"
            } ?: "Pendiente de análisis")
        MoSectionHeader("Costes de recogida")
        val accounts = runCatching { com.isivoltpro.maginaolivo.feature.harvests.labourAccounts(notebook.campaign.id, notebook.labour, notebook.expenses, payments) }.getOrNull()
        val balances = accounts?.flatMap { it.balances }.orEmpty()
        val settlements = balances.groupBy { it.currency }.map { (currency, rows) ->
            runCatching {
                val paid = rows.fold(0L) { total, row -> Math.addExact(total, row.paidMinor) }
                val pending = rows.fold(0L) { total, row -> Math.addExact(total, row.pendingMinor) }
                "${Money.format(paid, currency)} pagados · ${Money.format(pending, currency)} pendientes"
            }.getOrDefault("Saldo no disponible ($currency)")
        }
        MoKpiMetric("Jornales", ledger.moneyLabel(com.isivoltpro.maginaolivo.domain.expense.RecollectionBucket.LABOUR),
            Modifier.fillMaxWidth().testTag("notebook-summary-labour"), icon = MoIcons.People, kind = MoKpiKind.JORNALES,
            supportingText = (listOf("${notebook.labourByWorker.size} personas · ${notebook.labourSummary.label()}") + settlements +
                listOfNotNull(if (!notebook.unnamedLabour.isEmpty) "Sin identificar (histórico)" else null,
                    com.isivoltpro.maginaolivo.feature.harvests.labourCostNote(notebook.labour, notebook.expenses),
                    if (accounts == null || accounts.any { it.unconfirmed } || (balances.isEmpty() && ledger.any { (it.amount(com.isivoltpro.maginaolivo.domain.expense.RecollectionBucket.LABOUR) ?: 0) > 0 })) "Hay precios o costes sin atribuir" else null)).joinToString("\n"), onClick = onLabour)
        MoKpiMetric("Maquinaria", ledger.moneyLabel(com.isivoltpro.maginaolivo.domain.expense.RecollectionBucket.EQUIPMENT),
            Modifier.fillMaxWidth().testTag("notebook-summary-equipment"), icon = MoIcons.Tractor, kind = MoKpiKind.MAQUINARIA,
            supportingText = listOfNotNull("${notebook.equipment.sumOf { it.quantity }} equipos/usos · Ver detalle",
                com.isivoltpro.maginaolivo.feature.harvests.equipmentCostNote(notebook.equipment, notebook.expenses)).joinToString("\n"), onClick = { machineryDetail = true })
        MoKpiMetric("Otros gastos", ledger.moneyLabel(com.isivoltpro.maginaolivo.domain.expense.RecollectionBucket.OTHER),
            Modifier.fillMaxWidth().testTag("notebook-summary-other"), icon = MoIcons.Euro, kind = MoKpiKind.COSTES,
            supportingText = "Combustible, transporte, reparaciones y otros · Ver gastos", onClick = onExpenses)
        com.isivoltpro.maginaolivo.feature.harvests.RecollectionTotalCards(ledger, false,
            deliveries.deliveredGrams?.takeIf { it > 0 }?.let(Weight::format),
            com.isivoltpro.maginaolivo.domain.expense.RecollectionCostCompleteness.of(notebook.labour, notebook.equipment, notebook.expenses))
        ParcelYields(notebook)
        // Phase 19G: charts and year-over-year, all derived from the same records.
        CampaignCharts(CampaignSeries.of(notebook))
        val legacy = notebook.legacyUnweighedGrams
        if (legacy == null || legacy > 0) {
            // CR-010 (A2): the charts are drawn from Pesadas; the legacy kilos are named, not hidden.
            Text(
                legacy?.let { "Las gráficas solo incluyen pesadas. ${Weight.format(it)} registrados sin pesada (histórico) no aparecen en ellas." }
                    ?: "Las gráficas solo incluyen pesadas. Kilos históricos sin pesada no disponibles.",
                style = MaterialTheme.typography.bodySmall,
                color = MoTextSecondary,
                modifier = Modifier.testTag("notebook-chart-legacy-note"),
            )
        }
        // #355: the Farm's campaigns side by side, then the same numbers as text.
        CampaignHistoryCharts(com.isivoltpro.maginaolivo.domain.analytics.CampaignHistory.of(comparison), notebook.campaign.id, onSelectCampaign)
        CampaignComparisonList(comparison, onSelectCampaign)
    }
    if (machineryDetail) {
        androidx.compose.material3.ModalBottomSheet(containerColor = com.isivoltpro.maginaolivo.ui.theme.MoWarmWhite, onDismissRequest = { machineryDetail = false },
            sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(MoSpacing.screen), verticalArrangement = Arrangement.spacedBy(MoSpacing.sm)) {
                MoSectionHeader("Maquinaria de la campaña")
                if (notebook.equipment.isEmpty()) Text("Sin maquinaria anotada", color = MoTextSecondary)
                notebook.equipment.forEach { line ->
                    MoCompactListItem(title = "${line.quantity} · ${line.type.title()}",
                        subtitle = notebook.harvests.firstOrNull { it.id == line.harvestId }?.harvestDate?.format(DATE_FORMAT),
                        icon = line.type.icon(), iconTint = com.isivoltpro.maginaolivo.ui.theme.MoEarthText,
                        iconContainer = com.isivoltpro.maginaolivo.ui.theme.MoEarthTint,
                        onClick = { machineryDetail = false; onHarvest(line.harvestId) })
                }
                com.isivoltpro.maginaolivo.ui.components.MoTertiaryButton("Cerrar", { machineryDetail = false })
            }
        }
    }

}

/**
 * CR-010 §12 — the Campaign in seconds: its days, Pesada and jornal days, first/last Pesada,
 * close date and the ledger's cost with cost per kilo. Each unknown shows «—», never 0.
 */
@Composable
private fun CampaignAtAGlance(dashboard: CampaignDashboard, weighedGrams: Long?) {
    val date = { value: LocalDate? -> value?.let { DATE_FORMAT.format(it) } ?: "—" }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MoSpacing.xs)) {
        MoKpiMetric(
            "Días de campaña", dashboard.calendarDays?.toString() ?: "—", Modifier.weight(1f).testTag("dashboard-days"),
            icon = MoIcons.Calendar,
            kind = MoKpiKind.CAMPAIGN,
            supportingText = dashboard.countedFrom?.let { from ->
                dashboard.closedOn?.let { "Del ${date(from)} al ${date(it)}" } ?: "Desde el ${date(from)}"
            } ?: dashboard.closedOn?.let { "Cerrada el ${date(it)}" } ?: "Sin empezar",
        )
        MoKpiMetric(
            "Días con pesadas", dashboard.pesadaDays.toString(), Modifier.weight(1f).testTag("dashboard-pesada-days"),
            icon = MoIcons.Delivery,
            kind = MoKpiKind.PESADAS,
            supportingText = if (dashboard.labourDays == 1) "1 día con jornales" else "${dashboard.labourDays} días con jornales",
        )
    }
    if (dashboard.firstPesada != null) {
        Text(
            "Primera pesada ${date(dashboard.firstPesada)} · última ${date(dashboard.lastPesada)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MoTextSecondary,
            modifier = Modifier.testTag("dashboard-pesada-dates"),
        )
    }

}

/**
 * Phase 19C: yield per Parcel, only from Pesadas whose kilos there are known (one origin
 * Parcel or an exact split). Mixed loads count in the campaign yield above, never here.
 */
@Composable
private fun ParcelYields(notebook: CampaignNotebook) {
    if (notebook.deliveries.isEmpty()) return
    MoSectionHeader("Rendimiento por parcela")
    if (notebook.parcelYields.isEmpty()) {
        Text(
            "Las pesadas de esta campaña mezclan parcelas sin reparto conocido: su rendimiento cuenta solo en el total.",
            style = MaterialTheme.typography.bodySmall,
            color = MoTextSecondary,
            modifier = Modifier.testTag("notebook-parcel-yield-none"),
        )
        return
    }
    notebook.parcelYields.forEach { parcel ->
        Row(Modifier.fillMaxWidth().testTag("notebook-parcel-yield"), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(parcel.parcelName, style = MaterialTheme.typography.bodyMedium)
                Text(
                    parcel.fatYield?.let { "Sobre el ${parcel.coveragePercent} % de ${Weight.format(parcel.attributedGrams)}" }
                        ?: "${Weight.format(parcel.attributedGrams)} · rendimiento pendiente",
                    style = MaterialTheme.typography.bodySmall,
                    color = MoTextSecondary,
                )
            }
            Text(parcel.fatYield?.let { Percent.format(it.hundredths) } ?: "—", style = MaterialTheme.typography.titleMedium, color = MoOliveDark)
        }
    }
    notebook.deliverySummary.unallocatedGrams?.takeIf { it > 0 }?.let { unallocated ->
        Text(
            "${Weight.format(unallocated)} sin reparto por parcela cuentan solo en el total de la campaña.",
            style = MaterialTheme.typography.bodySmall,
            color = MoTextSecondary,
        )
    }
}

internal val SPANISH: Locale = Locale.forLanguageTag("es-ES")
internal val LONG_DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", SPANISH)

/** Done and planned differ by mark as well as colour (✓ / clock). */
internal fun ActivityStatus.chipIcon(): ImageVector? = when (this) {
    ActivityStatus.COMPLETED -> MoIcons.Check
    ActivityStatus.PLANNED -> MoIcons.Clock
    else -> null
}
