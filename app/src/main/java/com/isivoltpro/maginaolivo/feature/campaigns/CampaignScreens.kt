package com.isivoltpro.maginaolivo.feature.campaigns

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.isivoltpro.maginaolivo.app.LocalPersistence
import com.isivoltpro.maginaolivo.data.local.model.CampaignStatus
import com.isivoltpro.maginaolivo.domain.campaign.Campaign
import com.isivoltpro.maginaolivo.domain.campaign.CampaignParcelOption
import com.isivoltpro.maginaolivo.ui.components.MoDateInputField
import com.isivoltpro.maginaolivo.ui.components.MoEmptyState
import com.isivoltpro.maginaolivo.ui.components.MoErrorState
import com.isivoltpro.maginaolivo.ui.components.MoPrimaryButton
import com.isivoltpro.maginaolivo.ui.components.MoSecondaryButton
import com.isivoltpro.maginaolivo.ui.components.MoSectionHeader
import com.isivoltpro.maginaolivo.ui.components.MoStatusChip
import com.isivoltpro.maginaolivo.ui.components.MoStatusTone
import com.isivoltpro.maginaolivo.ui.components.MoTextField
import com.isivoltpro.maginaolivo.ui.theme.MoCream
import com.isivoltpro.maginaolivo.ui.theme.MoOutline
import com.isivoltpro.maginaolivo.ui.theme.MoSpacing
import com.isivoltpro.maginaolivo.ui.theme.MoTextSecondary
import com.isivoltpro.maginaolivo.ui.theme.MoWarmWhite
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import com.isivoltpro.maginaolivo.domain.delivery.DeliverySummary
import com.isivoltpro.maginaolivo.domain.delivery.Percent
import com.isivoltpro.maginaolivo.domain.expense.ExpenseSummary
import com.isivoltpro.maginaolivo.domain.expense.Money
import com.isivoltpro.maginaolivo.domain.harvest.HarvestSummary
import com.isivoltpro.maginaolivo.domain.harvest.Weight
import com.isivoltpro.maginaolivo.ui.components.MoCompactListItem
import com.isivoltpro.maginaolivo.ui.components.MoDestructiveButton
import com.isivoltpro.maginaolivo.ui.components.MoIcons
import com.isivoltpro.maginaolivo.ui.components.MoMetricGrid
import com.isivoltpro.maginaolivo.ui.components.MoSummaryMetric
import com.isivoltpro.maginaolivo.ui.components.MoTertiaryButton
import com.isivoltpro.maginaolivo.ui.theme.MoOliveDark
import java.time.LocalDate
import java.util.UUID

@Composable
fun FarmCampaignsRoute(farmId: UUID, persistence: LocalPersistence, onCampaignSelected: (UUID) -> Unit) {
    val vm: FarmCampaignsViewModel = viewModel(key = "farm-campaigns-$farmId", factory = viewModelFactory {
        initializer { FarmCampaignsViewModel(farmId, persistence.campaignRepository) }
    })
    val state by vm.state.collectAsStateWithLifecycle()
    FarmCampaignsSection(state, onCampaignSelected, vm::create)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FarmCampaignsSection(state: FarmCampaignsUiState, onCampaignSelected: (UUID) -> Unit, onCreate: (CampaignDraft) -> Unit) {
    var editor by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.message) { if (state.message != null) editor = false }
    MoSectionHeader("Campañas", action = { TextButton(onClick = { editor = true }, modifier = Modifier.testTag("add-campaign")) { Text("Añadir") } })
    when {
        state.isLoading -> CircularProgressIndicator()
        state.error != null -> MoErrorState("No pudimos abrir las campañas", state.error)
        state.current.isEmpty() && state.history.isEmpty() -> MoEmptyState("Aún no hay campañas", "Crea una campaña y selecciona las parcelas que participan.", icon = MoIcons.Campaign)
        else -> {
            state.current.forEach { CampaignRow(it, onCampaignSelected) }
            if (state.history.isNotEmpty()) {
                MoSectionHeader("Histórico")
                state.history.forEach { CampaignRow(it, onCampaignSelected) }
            }
        }
    }
    if (editor) ModalBottomSheet(onDismissRequest = { editor = false }) {
        CampaignEditor(state.parcels, state.nameError, state.dateError, state.isSaving, onCreate, { editor = false })
    }
}

@Composable
private fun CampaignRow(campaign: Campaign, onSelected: (UUID) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button) { onSelected(campaign.id) }.testTag("campaign-row"),
        colors = CardDefaults.cardColors(containerColor = MoWarmWhite),
        border = androidx.compose.foundation.BorderStroke(1.dp, MoOutline),
    ) { Row(Modifier.fillMaxWidth().padding(horizontal = MoSpacing.sm, vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(campaign.name, style = MaterialTheme.typography.titleSmall); Text("Inicio ${campaign.startDate.format(SHORT_DATE)}", style = MaterialTheme.typography.bodySmall, color = MoTextSecondary) }
        MoStatusChip(campaign.status.label(), tone = campaign.status.tone())
    } }
}

@Composable
internal fun CampaignEditor(
    parcels: List<CampaignParcelOption>, nameError: String?, dateError: String?, isSaving: Boolean,
    onSave: (CampaignDraft) -> Unit, onCancel: () -> Unit, initial: CampaignDraft = CampaignDraft(),
) {
    var name by rememberSaveable(initial.name) { mutableStateOf(initial.name) }
    var date by rememberSaveable(initial.startDate) { mutableStateOf(initial.startDate?.toString().orEmpty()) }
    var notes by rememberSaveable(initial.notes) { mutableStateOf(initial.notes) }
    var selected by rememberSaveable(initial.parcelIds) { mutableStateOf(initial.parcelIds.map(UUID::toString)) }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(MoSpacing.screen), verticalArrangement = Arrangement.spacedBy(MoSpacing.md)) {
        Text("Nueva campaña", style = MaterialTheme.typography.headlineSmall)
        MoTextField(name, { name = it }, "Nombre", isError = nameError != null, supportingText = nameError, modifier = Modifier.testTag("campaign-name"))
        MoDateInputField(date, { date = it }, "Fecha de inicio", isError = dateError != null, supportingText = dateError, modifier = Modifier.testTag("campaign-start-date"))
        MoSectionHeader("Parcelas")
        if (parcels.isEmpty()) Text("Primero añade una parcela a esta finca.", color = MoTextSecondary)
        parcels.forEach { parcel ->
            val checked = parcel.id.toString() in selected
            Row(Modifier.fillMaxWidth().testTag("campaign-parcel-option").clickable { selected = if (checked) selected - parcel.id.toString() else selected + parcel.id.toString() }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked, { value -> selected = if (value) selected + parcel.id.toString() else selected - parcel.id.toString() })
                Text(parcel.name)
            }
        }
        MoTextField(notes, { notes = it }, "Notas")
        MoPrimaryButton("Guardar campaña", { onSave(CampaignDraft(name, runCatching { LocalDate.parse(date) }.getOrNull(), selected.map(UUID::fromString).toSet(), notes)) }, modifier = Modifier.fillMaxWidth().testTag("save-campaign"), enabled = !isSaving)
        MoTertiaryButton("Cancelar", onCancel, modifier = Modifier.fillMaxWidth())
    }
}

/**
 * The campaign's figures, derived from the ledgers that own them (Harvest, Delivery,
 * Expense). Null means nothing recorded yet — shown as a sentence, never as a zero.
 */
data class CampaignSummaryUi(
    val harvestedGrams: Long? = null,
    val harvestCount: Int = 0,
    val deliveredGrams: Long? = null,
    val deliveryCount: Int = 0,
    val fatYieldHundredths: Int? = null,
    val expensesMinor: Long? = null,
)

@Composable
fun CampaignDetailRoute(
    campaignId: UUID,
    persistence: LocalPersistence,
    onHarvests: () -> Unit = {},
    onDeliveries: () -> Unit = {},
) {
    val vm: CampaignDetailViewModel = viewModel(key = "campaign-$campaignId", factory = viewModelFactory {
        initializer { CampaignDetailViewModel(campaignId, persistence.campaignRepository) }
    })
    val state by vm.state.collectAsStateWithLifecycle()
    val harvests by remember(campaignId) { persistence.harvestRepository.observeForCampaign(campaignId) }.collectAsStateWithLifecycle(emptyList())
    val deliveries by remember(campaignId) { persistence.deliveryRepository.observeForCampaign(campaignId) }.collectAsStateWithLifecycle(emptyList())
    val expenses by remember { persistence.expenseRepository.observeAll() }.collectAsStateWithLifecycle(emptyList())
    val summary = remember(harvests, deliveries, expenses) {
        val harvest = HarvestSummary.of(harvests)
        val delivery = DeliverySummary.of(deliveries)
        val ledger = ExpenseSummary.of(expenses.filter { it.campaignId == campaignId })
        CampaignSummaryUi(
            harvestedGrams = harvest.totalGrams.takeIf { harvest.harvestCount > 0 },
            harvestCount = harvest.harvestCount,
            deliveredGrams = delivery.deliveredGrams.takeIf { delivery.deliveryCount > 0 },
            deliveryCount = delivery.deliveryCount,
            fatYieldHundredths = delivery.fatYield?.hundredths,
            expensesMinor = ledger.totalMinor.takeIf { ledger.postedCount > 0 },
        )
    }
    CampaignDetailScreen(
        state, vm::update, vm::activate, vm::markHarvest, vm::close, vm::reopen, vm::archivePreparation,
        summary = summary,
        onHarvests = onHarvests,
        onDeliveries = onDeliveries,
    )
}

/**
 * UI polish v2: the important part first — a compact header and a two-column summary —
 * then the parcels and the links to harvest and deliveries. Closing a campaign is a
 * soft-red secondary action and still asks for confirmation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampaignDetailScreen(
    state: CampaignDetailUiState, onUpdate: (CampaignDraft) -> Unit, onActivate: () -> Unit, onHarvest: () -> Unit,
    onClose: (LocalDate) -> Unit, onReopen: () -> Unit, onArchive: () -> Unit,
    summary: CampaignSummaryUi = CampaignSummaryUi(),
    onHarvests: () -> Unit = {},
    onDeliveries: () -> Unit = {},
) {
    var confirmation by rememberSaveable { mutableStateOf<String?>(null) }
    var editor by rememberSaveable { mutableStateOf(false) }
    Scaffold(Modifier.fillMaxSize().testTag("campaign-detail-root"), containerColor = MoCream, contentWindowInsets = WindowInsets(0, 0, 0, 0)) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).statusBarsPadding().verticalScroll(rememberScrollState())
                .padding(horizontal = MoSpacing.screen, vertical = MoSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(MoSpacing.sm),
        ) {
            when {
                state.isLoading -> CircularProgressIndicator()
                state.campaign == null -> MoErrorState("Campaña no disponible", state.error ?: "No está guardada en este dispositivo.")
                else -> {
                    val campaign = state.campaign
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MoSpacing.xs), verticalAlignment = Alignment.CenterVertically) {
                        Text(campaign.name, style = MaterialTheme.typography.headlineMedium, color = MoOliveDark, modifier = Modifier.weight(1f))
                        MoStatusChip(campaign.status.label(), tone = campaign.status.tone())
                    }
                    Text(
                        listOfNotNull(
                            campaign.snapshots.map { it.farmName }.distinct().singleOrNull(),
                            "Inicio ${campaign.startDate.format(SHORT_DATE)}",
                            campaign.endDate?.let { "Cierre ${it.format(SHORT_DATE)}" },
                            "${campaign.snapshots.size} ${if (campaign.snapshots.size == 1) "parcela" else "parcelas"}",
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MoTextSecondary,
                    )
                    MoMetricGrid(
                        content = listOf(
                            { m ->
                                MoSummaryMetric(
                                    "Kg recogidos",
                                    summary.harvestedGrams?.let(Weight::format) ?: "—",
                                    m.testTag("campaign-metric-harvest"),
                                    icon = MoIcons.Harvest,
                                    supportingText = if (summary.harvestedGrams == null) "Aún no has registrado cosecha" else "${summary.harvestCount} registros",
                                )
                            },
                            { m ->
                                MoSummaryMetric(
                                    "Entregas",
                                    summary.deliveredGrams?.let(Weight::format) ?: "—",
                                    m.testTag("campaign-metric-deliveries"),
                                    icon = MoIcons.Delivery,
                                    supportingText = if (summary.deliveredGrams == null) "Aún sin entregas" else "${summary.deliveryCount} entregas",
                                )
                            },
                            { m ->
                                MoSummaryMetric(
                                    "Rendimiento graso",
                                    summary.fatYieldHundredths?.let(Percent::format) ?: "—",
                                    m.testTag("campaign-metric-yield"),
                                    icon = MoIcons.Percent,
                                    supportingText = if (summary.fatYieldHundredths == null) "Llegará con los análisis de entrega" else "Ponderado por kilos",
                                )
                            },
                            { m ->
                                MoSummaryMetric(
                                    "Gastos",
                                    summary.expensesMinor?.let { Money.format(it) } ?: "—",
                                    m.testTag("campaign-metric-expenses"),
                                    icon = MoIcons.Euro,
                                    supportingText = if (summary.expensesMinor == null) "Aún no hay gastos de esta campaña" else "Gastos anotados",
                                )
                            },
                        ),
                    )
                    MoSectionHeader("Parcelas de la campaña")
                    if (campaign.snapshots.isEmpty()) {
                        MoEmptyState("Sin parcelas", "Edita la campaña para elegir qué parcelas participan.", icon = MoIcons.Parcels)
                    }
                    campaign.snapshots.forEach { snapshot ->
                        MoCompactListItem(
                            title = snapshot.parcelName,
                            subtitle = listOfNotNull(snapshot.farmName, snapshot.cadastralReference?.let { "Ref. catastral: $it" }).joinToString(" · "),
                            icon = MoIcons.Parcels,
                            modifier = Modifier.testTag("campaign-snapshot"),
                        )
                    }
                    MoSectionHeader("Producción")
                    MoCompactListItem(
                        title = "Cosecha",
                        subtitle = if (summary.harvestCount == 0) "Registrar los kilos recogidos" else "${summary.harvestCount} registros",
                        icon = MoIcons.Harvest,
                        onClick = onHarvests,
                        modifier = Modifier.testTag("campaign-open-harvests"),
                        trailing = { Icon(MoIcons.ChevronRight, contentDescription = null, tint = MoTextSecondary, modifier = Modifier.size(18.dp)) },
                    )
                    MoCompactListItem(
                        title = "Entregas a cooperativa",
                        subtitle = if (summary.deliveryCount == 0) "Registrar entregas y tickets" else "${summary.deliveryCount} entregas",
                        icon = MoIcons.Delivery,
                        onClick = onDeliveries,
                        modifier = Modifier.testTag("campaign-open-deliveries"),
                        trailing = { Icon(MoIcons.ChevronRight, contentDescription = null, tint = MoTextSecondary, modifier = Modifier.size(18.dp)) },
                    )
                    Spacer(Modifier.height(MoSpacing.xs))
                    when (campaign.status) {
                        CampaignStatus.PREPARATION -> {
                            MoPrimaryButton("Activar campaña", { confirmation = "activate" }, modifier = Modifier.fillMaxWidth().testTag("activate-campaign"), enabled = !state.isSaving)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MoSpacing.xs)) {
                                MoSecondaryButton("Editar", { editor = true }, modifier = Modifier.weight(1f).testTag("edit-campaign"), enabled = !state.isSaving)
                                MoDestructiveButton("Archivar borrador", { confirmation = "archive" }, modifier = Modifier.weight(1f))
                            }
                        }
                        CampaignStatus.ACTIVE -> MoPrimaryButton("Iniciar recolección", { confirmation = "harvest" }, modifier = Modifier.fillMaxWidth(), enabled = !state.isSaving)
                        CampaignStatus.HARVEST -> MoDestructiveButton("Cerrar campaña", { confirmation = "close" }, modifier = Modifier.fillMaxWidth().testTag("close-campaign"), enabled = !state.isSaving)
                        CampaignStatus.CLOSED -> {
                            Text("Histórico protegido", style = MaterialTheme.typography.titleSmall, color = MoTextSecondary)
                            MoSecondaryButton("Reabrir campaña", { confirmation = "reopen" }, modifier = Modifier.fillMaxWidth().testTag("reopen-campaign"))
                        }
                    }
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }
    val campaign = state.campaign
    if (editor && campaign != null) ModalBottomSheet(onDismissRequest = { editor = false }) {
        CampaignEditor(state.parcels, null, null, state.isSaving, onUpdate, { editor = false },
            CampaignDraft(campaign.name, campaign.startDate, campaign.snapshots.map { it.parcelId }.toSet(), campaign.notes.orEmpty()))
    }
    if (confirmation != null) ModalBottomSheet(onDismissRequest = { confirmation = null }) {
        val destructive = confirmation == "close" || confirmation == "archive"
        Column(Modifier.fillMaxWidth().padding(horizontal = MoSpacing.screen).padding(bottom = MoSpacing.md), verticalArrangement = Arrangement.spacedBy(MoSpacing.sm)) {
            Text("Confirmar cambio", style = MaterialTheme.typography.headlineSmall)
            Text(
                when (confirmation) {
                    "close" -> "La campaña quedará cerrada y su histórico protegido. Podrás reabrirla si lo necesitas."
                    "archive" -> "El borrador desaparecerá de la lista. Sus datos no se borran."
                    else -> "Esta acción actualizará el estado de la campaña guardada en este dispositivo."
                },
                color = MoTextSecondary,
            )
            val confirm = {
                when (confirmation) { "activate" -> onActivate(); "harvest" -> onHarvest(); "close" -> onClose(LocalDate.now()); "reopen" -> onReopen(); "archive" -> onArchive() }
                confirmation = null
            }
            if (destructive) {
                MoDestructiveButton("Confirmar", confirm, modifier = Modifier.fillMaxWidth().testTag("confirm-campaign-action"))
            } else {
                MoPrimaryButton("Confirmar", confirm, modifier = Modifier.fillMaxWidth().testTag("confirm-campaign-action"))
            }
            MoTertiaryButton("Cancelar", { confirmation = null }, modifier = Modifier.fillMaxWidth())
        }
    }
}

private val SHORT_DATE = java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", java.util.Locale.forLanguageTag("es-ES"))

private fun CampaignStatus.tone() = when (this) {
    CampaignStatus.PREPARATION -> MoStatusTone.Info
    CampaignStatus.ACTIVE, CampaignStatus.HARVEST -> MoStatusTone.Success
    CampaignStatus.CLOSED -> MoStatusTone.Neutral
}

private fun CampaignStatus.label() = when (this) {
    CampaignStatus.PREPARATION -> "Preparación"; CampaignStatus.ACTIVE -> "Activa"; CampaignStatus.HARVEST -> "Recolección"; CampaignStatus.CLOSED -> "Cerrada"
}
