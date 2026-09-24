package com.isivoltpro.maginaolivo.feature.notebook

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.isivoltpro.maginaolivo.app.LocalPersistence
import com.isivoltpro.maginaolivo.domain.activity.Activity
import com.isivoltpro.maginaolivo.domain.delivery.Delivery
import com.isivoltpro.maginaolivo.domain.delivery.Percent
import com.isivoltpro.maginaolivo.domain.expense.Expense
import com.isivoltpro.maginaolivo.domain.expense.ExpenseStatus
import com.isivoltpro.maginaolivo.domain.expense.Money
import com.isivoltpro.maginaolivo.domain.harvest.Harvest
import com.isivoltpro.maginaolivo.domain.harvest.Weight
import com.isivoltpro.maginaolivo.domain.notebook.CampaignNotebook
import com.isivoltpro.maginaolivo.domain.notebook.RecollectionItem
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
import com.isivoltpro.maginaolivo.ui.components.MoStat
import com.isivoltpro.maginaolivo.ui.components.MoStatStrip
import com.isivoltpro.maginaolivo.ui.components.MoStatusChip
import com.isivoltpro.maginaolivo.ui.components.MoStatusTone
import com.isivoltpro.maginaolivo.ui.components.MoSummaryMetric
import com.isivoltpro.maginaolivo.ui.theme.MoOliveDark
import com.isivoltpro.maginaolivo.ui.theme.MoShape
import com.isivoltpro.maginaolivo.ui.theme.MoSpacing
import com.isivoltpro.maginaolivo.ui.theme.MoTextSecondary
import com.isivoltpro.maginaolivo.ui.theme.MoWarmWhite
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

/** Where each Cuaderno row and action leads: always the canonical screen of that record. */
data class NotebookActions(
    val onActivity: (UUID) -> Unit = {},
    val onHarvest: (UUID) -> Unit = {},
    val onDelivery: (UUID) -> Unit = {},
    val onExpense: (UUID) -> Unit = {},
    /** The Farm's work list, where work is registered and planned. */
    val onWorks: () -> Unit = {},
    val onHarvests: () -> Unit = {},
    val onDeliveries: () -> Unit = {},
    val onExpenses: () -> Unit = {},
    val onCampaigns: () -> Unit = {},
)

@Composable
fun NotebookRoute(farmId: UUID, persistence: LocalPersistence, actions: NotebookActions) {
    val viewModel: NotebookViewModel = viewModel(
        key = "notebook-$farmId",
        factory = viewModelFactory {
            initializer {
                NotebookViewModel(
                    farmId, persistence.campaignRepository, persistence.activityRepository,
                    persistence.harvestRepository, persistence.deliveryRepository, persistence.expenseRepository,
                )
            }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    NotebookSection(state, viewModel::selectCampaign, actions)
}

/** Phase 19A: Trabajos · Recolección · Resumen of one Campaign, inside the Farm section screen. */
@Composable
fun NotebookSection(state: NotebookUiState, onSelectCampaign: (UUID) -> Unit, actions: NotebookActions) {
    var tab by rememberSaveable { mutableStateOf(NotebookTab.WORKS) }
    when {
        state.isLoading -> CircularProgressIndicator(Modifier.testTag("notebook-loading"))
        state.error != null -> Text(state.error, color = MaterialTheme.colorScheme.error)
        state.notebook == null -> {
            MoEmptyState(
                "Aún no hay campañas",
                "El cuaderno se ordena por campañas. Crea la de este año y aquí verás sus trabajos y su recolección.",
                actionText = "Ir a Campañas",
                onAction = actions.onCampaigns,
                icon = MoIcons.Campaign,
                modifier = Modifier.testTag("notebook-no-campaign"),
            )
            // Work can be written down before any Campaign exists.
            MoSecondaryButton("Registrar o planificar trabajo", actions.onWorks, Modifier.fillMaxWidth().testTag("notebook-open-works"))
        }
        else -> {
            val notebook = state.notebook
            if (state.campaigns.size > 1) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(MoSpacing.xs)) {
                    state.campaigns.sortedByDescending { it.startDate }.forEach { campaign ->
                        FilterChip(
                            selected = campaign.id == state.selectedCampaignId,
                            onClick = { onSelectCampaign(campaign.id) },
                            label = { Text(campaign.name) },
                            modifier = Modifier.testTag("notebook-campaign"),
                        )
                    }
                }
            } else {
                Text(
                    "Campaña ${notebook.campaign.name}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MoOliveDark,
                    modifier = Modifier.testTag("notebook-campaign-name"),
                )
            }
            NotebookTabs(tab) { tab = it }
            when (tab) {
                NotebookTab.WORKS -> WorksTab(notebook, actions)
                NotebookTab.RECOLLECTION -> RecollectionTab(notebook, actions)
                NotebookTab.SUMMARY -> SummaryTab(notebook)
            }
        }
    }
}

@Composable
private fun NotebookTabs(selected: NotebookTab, onSelect: (NotebookTab) -> Unit) {
    TabRow(
        selectedTabIndex = selected.ordinal,
        containerColor = MoWarmWhite,
        contentColor = MoOliveDark,
        modifier = Modifier.clip(MoShape.card),
    ) {
        NotebookTab.entries.forEach { tab ->
            Tab(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                modifier = Modifier.testTag("notebook-tab-${tab.name.lowercase()}"),
                text = {
                    Text(
                        tab.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (tab == selected) MoOliveDark else MoTextSecondary,
                    )
                },
            )
        }
    }
}

@Composable
private fun WorksTab(notebook: CampaignNotebook, actions: NotebookActions) {
    MoPrimaryButton("Registrar o planificar trabajo", actions.onWorks, Modifier.fillMaxWidth().testTag("notebook-open-works"))
    if (notebook.works.isEmpty()) {
        MoEmptyState(
            "Sin trabajos en esta campaña",
            "Poda, abonado, tratamientos o riegos aparecerán aquí por meses.",
            icon = MoIcons.Checklist,
        )
        return
    }
    notebook.works.groupBy { it.activityDate.withDayOfMonth(1) }.forEach { (month, works) ->
        MoSectionHeader(month.format(MONTH).replaceFirstChar { it.titlecase(SPANISH) })
        works.forEach { work -> WorkRow(work) { actions.onActivity(work.id) } }
    }
}

@Composable
private fun WorkRow(work: Activity, onClick: () -> Unit) {
    MoCompactListItem(
        title = work.description,
        subtitle = listOfNotNull(
            work.activityDate.format(DAY),
            work.type.label(),
            work.targets.takeIf { it.isNotEmpty() }?.let { if (it.size == 1) it.single().parcelName else "${it.size} parcelas" },
        ).joinToString(" · "),
        icon = work.type.icon(),
        onClick = onClick,
        modifier = Modifier.testTag("notebook-work"),
        trailing = { MoStatusChip(work.status.label(), tone = work.status.tone()) },
    )
}

@Composable
private fun RecollectionTab(notebook: CampaignNotebook, actions: NotebookActions) {
    val deliveries = notebook.deliverySummary
    val fat = deliveries.fatYield
    MoStatStrip(
        listOf(
            MoStat("Recogido", notebook.harvestSummary.totalGrams.takeIf { notebook.harvests.isNotEmpty() }?.let(Weight::format) ?: "—", MoIcons.Harvest),
            MoStat("Entregado", deliveries.deliveredGrams.takeIf { deliveries.deliveryCount > 0 }?.let(Weight::format) ?: "—", MoIcons.Delivery),
            MoStat("Rendimiento", fat?.let { Percent.format(it.hundredths) } ?: "—", MoIcons.Percent),
        ),
        Modifier.testTag("notebook-recollection-summary"),
    )
    Text(
        listOfNotNull(
            if (deliveries.deliveryCount == 1) "1 pesada" else "${deliveries.deliveryCount} pesadas",
            fat?.let { "rendimiento sobre el ${deliveries.coveragePercent(it)} % de los kilos" }
                ?: if (deliveries.deliveryCount > 0) "rendimiento pendiente" else null,
            notebook.recollectionExpenseSummary.takeIf { it.postedCount > 0 }?.let { "gastos ${Money.format(it.totalMinor, it.currency)}" },
        ).joinToString(" · "),
        style = MaterialTheme.typography.bodySmall,
        color = MoTextSecondary,
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MoSpacing.xs)) {
        MoSecondaryButton("Cosecha", actions.onHarvests, Modifier.weight(1f).testTag("notebook-open-harvests"))
        MoSecondaryButton("Pesada", actions.onDeliveries, Modifier.weight(1f).testTag("notebook-open-deliveries"))
        MoSecondaryButton("Gasto", actions.onExpenses, Modifier.weight(1f).testTag("notebook-open-expenses"))
    }
    if (notebook.recollectionDays.isEmpty()) {
        MoEmptyState(
            "Aún no hay recolección",
            "Los kilos recogidos, las pesadas y los gastos de la cosecha aparecerán aquí por días.",
            icon = MoIcons.Harvest,
        )
        return
    }
    notebook.recollectionDays.forEach { day ->
        MoSectionHeader(day.date.format(LONG_DAY).replaceFirstChar { it.titlecase(SPANISH) })
        day.items.forEach { item ->
            when (item) {
                is RecollectionItem.HarvestItem -> HarvestRow(item.harvest) { actions.onHarvest(item.harvest.id) }
                is RecollectionItem.DeliveryItem -> DeliveryRow(item.delivery) { actions.onDelivery(item.delivery.id) }
                is RecollectionItem.HarvestDayItem -> WorkRow(item.activity) { actions.onActivity(item.activity.id) }
                is RecollectionItem.ExpenseItem -> ExpenseRow(item.expense) { actions.onExpense(item.expense.id) }
            }
        }
    }
}

@Composable
private fun HarvestRow(harvest: Harvest, onClick: () -> Unit) {
    MoCompactListItem(
        title = "Cosecha · ${Weight.format(harvest.totalGrams)}",
        subtitle = when {
            harvest.shares.isEmpty() -> "Toda la finca"
            harvest.shares.size == 1 -> harvest.shares.single().parcelName
            else -> "${harvest.shares.size} parcelas"
        },
        icon = MoIcons.Harvest,
        onClick = onClick,
        modifier = Modifier.testTag("notebook-harvest"),
    )
}

@Composable
private fun DeliveryRow(delivery: Delivery, onClick: () -> Unit) {
    // The cooperative is never hidden on a Pesada row (CR-005 §5).
    MoCompactListItem(
        title = listOfNotNull("Pesada", (delivery.ticketNumber ?: delivery.deliveryNumber)?.let { "nº $it" }).joinToString(" "),
        subtitle = "${delivery.destinationName} · ${Weight.format(delivery.netGrams)}",
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
private fun ExpenseRow(expense: Expense, onClick: () -> Unit) {
    MoCompactListItem(
        title = expense.concept,
        subtitle = "${expense.category.label()} · ${Money.format(expense.amountMinor, expense.currency)}",
        icon = MoIcons.Euro,
        onClick = onClick,
        modifier = Modifier.testTag("notebook-expense"),
        trailing = { if (expense.status == ExpenseStatus.DRAFT) MoStatusChip("Borrador", tone = MoStatusTone.Neutral) },
    )
}

@Composable
private fun SummaryTab(notebook: CampaignNotebook) {
    val harvest = notebook.harvestSummary
    val deliveries = notebook.deliverySummary
    val expenses = notebook.expenseSummary
    Column(verticalArrangement = Arrangement.spacedBy(MoSpacing.xs), modifier = Modifier.testTag("notebook-summary")) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MoSpacing.xs)) {
            MoSummaryMetric(
                "Trabajos", "${notebook.completedWorks} hechos", Modifier.weight(1f),
                icon = MoIcons.Checklist,
                supportingText = if (notebook.plannedWorks == 0) "Nada planificado" else "${notebook.plannedWorks} planificados",
            )
            MoSummaryMetric(
                "Recogido", if (notebook.harvests.isEmpty()) "—" else Weight.format(harvest.totalGrams), Modifier.weight(1f),
                icon = MoIcons.Harvest,
                supportingText = if (harvest.unallocatedGrams > 0) "${Weight.format(harvest.unallocatedGrams)} sin repartir" else null,
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MoSpacing.xs)) {
            MoSummaryMetric(
                "Entregado", if (deliveries.deliveryCount == 0) "—" else Weight.format(deliveries.deliveredGrams), Modifier.weight(1f),
                icon = MoIcons.Delivery,
                supportingText = if (deliveries.deliveryCount == 1) "1 pesada" else "${deliveries.deliveryCount} pesadas",
            )
            MoSummaryMetric(
                "Rendimiento", deliveries.fatYield?.let { Percent.format(it.hundredths) } ?: "—", Modifier.weight(1f),
                icon = MoIcons.Percent,
                supportingText = deliveries.fatYield?.let { "Sobre el ${deliveries.coveragePercent(it)} % de los kilos" } ?: "Pendiente de análisis",
            )
        }
        MoSummaryMetric(
            "Gastos de la campaña",
            if (expenses.postedCount == 0) "—" else Money.format(expenses.totalMinor, expenses.currency),
            Modifier.fillMaxWidth().testTag("notebook-summary-expenses"),
            icon = MoIcons.Euro,
            supportingText = when {
                expenses.draftCount > 0 -> "${expenses.draftCount} en borrador sin contar"
                expenses.postedCount == 0 -> "Sin gastos anotados"
                else -> null
            },
        )
        expenses.byCategory.entries.sortedByDescending { it.value }.forEach { (category, amount) ->
            Row(Modifier.fillMaxWidth()) {
                Text(category.label(), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(Money.format(amount, expenses.currency), style = MaterialTheme.typography.bodyMedium, color = MoOliveDark)
            }
        }
    }
}

private val SPANISH: Locale = Locale.forLanguageTag("es-ES")
private val MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", SPANISH)
private val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", SPANISH)
private val LONG_DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", SPANISH)
