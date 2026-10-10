package com.isivoltpro.maginaolivo.feature.campaigns

import com.isivoltpro.maginaolivo.ui.theme.MoColors

import com.isivoltpro.maginaolivo.ui.components.OnEachSave
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
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.viewModelFactory
import com.isivoltpro.maginaolivo.app.LocalPersistence
import com.isivoltpro.maginaolivo.data.local.model.CampaignStatus
import com.isivoltpro.maginaolivo.data.local.model.isRunning
import com.isivoltpro.maginaolivo.domain.campaign.Campaign
import com.isivoltpro.maginaolivo.domain.campaign.CampaignParcelOption
import com.isivoltpro.maginaolivo.domain.notebook.legacyUnweighedGrams
import com.isivoltpro.maginaolivo.ui.components.MoDateInputField
import com.isivoltpro.maginaolivo.ui.components.MoEmptyState
import com.isivoltpro.maginaolivo.ui.components.MoErrorState
import com.isivoltpro.maginaolivo.ui.components.MoPrimaryButton
import com.isivoltpro.maginaolivo.ui.components.MoSecondaryButton
import com.isivoltpro.maginaolivo.ui.components.MoSectionHeader
import com.isivoltpro.maginaolivo.ui.components.MoStatusChip
import com.isivoltpro.maginaolivo.ui.components.MoStatusTone
import com.isivoltpro.maginaolivo.ui.components.MoTextField
import com.isivoltpro.maginaolivo.ui.theme.MoSpacing
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import com.isivoltpro.maginaolivo.domain.delivery.DeliverySummary
import com.isivoltpro.maginaolivo.domain.delivery.Percent
import com.isivoltpro.maginaolivo.domain.expense.Money
import com.isivoltpro.maginaolivo.domain.harvest.HarvestSummary
import com.isivoltpro.maginaolivo.domain.harvest.Weight
import com.isivoltpro.maginaolivo.ui.components.MoCompactListItem
import com.isivoltpro.maginaolivo.ui.components.MoDestructiveButton
import com.isivoltpro.maginaolivo.ui.components.MoIconBadge
import com.isivoltpro.maginaolivo.feature.reports.shareReport
import com.isivoltpro.maginaolivo.feature.reports.writeCampaignReport
import com.isivoltpro.maginaolivo.ui.components.MoIcons
import com.isivoltpro.maginaolivo.ui.components.MoKpiKind
import com.isivoltpro.maginaolivo.ui.components.MoKpiMetric
import com.isivoltpro.maginaolivo.ui.components.MoMetricGrid
import com.isivoltpro.maginaolivo.ui.components.MoTertiaryButton
import com.isivoltpro.maginaolivo.domain.labour.LabourEntry
import com.isivoltpro.maginaolivo.domain.equipment.EquipmentLine
import java.time.LocalDate
import java.util.UUID
import com.isivoltpro.maginaolivo.ui.theme.MoSurfaceTokens

@Composable
fun FarmCampaignsRoute(farmId: UUID, persistence: LocalPersistence, onCampaignSelected: (UUID) -> Unit) {
    val vm: FarmCampaignsViewModel = viewModel(key = "farm-campaigns-$farmId", factory = viewModelFactory {
        initializer { FarmCampaignsViewModel(farmId, persistence.campaignRepository) }
    })
    val state by vm.state.collectAsStateWithLifecycle()
    // #246: each card's figures, from the same Pesadas, days and posted ledger as the campaign.
    val deliveries by remember { persistence.deliveryRepository.observeAll() }.collectAsStateWithLifecycle(null)
    val harvests by remember { persistence.harvestRepository.observeAll() }.collectAsStateWithLifecycle(null)
    val expenses by remember { persistence.expenseRepository.observeAll() }.collectAsStateWithLifecycle(null)
    // #449: each campaign's jornales and machinery, so a card never reads as complete too early.
    val campaignIds = (state.current + state.history).map { it.id }
    val crews by remember(campaignIds) {
        if (campaignIds.isEmpty()) kotlinx.coroutines.flow.flowOf(emptyList<LabourEntry>() to emptyList<EquipmentLine>())
        else kotlinx.coroutines.flow.combine(campaignIds.map { id ->
            kotlinx.coroutines.flow.combine(
                persistence.labourRepository.observeForCampaign(id),
                persistence.equipmentRepository.observeForCampaign(id),
            ) { jornales, maquinaria -> jornales to maquinaria }
        }) { parts -> parts.flatMap { it.first } to parts.flatMap { it.second } }
    }.collectAsStateWithLifecycle(null)
    val summaries = remember(state.current, state.history, deliveries, harvests, expenses, crews) {
        val loadedDeliveries = deliveries
        val loadedHarvests = harvests
        val loadedExpenses = expenses
        val loadedCrews = crews
        if (loadedDeliveries == null || loadedHarvests == null || loadedExpenses == null || loadedCrews == null) emptyMap()
        else (state.current + state.history).associate { campaign ->
            campaign.id to CampaignCardSummary.of(campaign.id, loadedDeliveries, loadedHarvests, loadedExpenses,
                loadedCrews.first, loadedCrews.second)
        }
    }
    FarmCampaignsSection(state, onCampaignSelected, vm::create, summaries)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FarmCampaignsSection(
    state: FarmCampaignsUiState,
    onCampaignSelected: (UUID) -> Unit,
    onCreate: (CampaignDraft) -> Unit,
    /** #246: figures per campaign; a campaign missing here (still loading) shows its header only. */
    summaries: Map<UUID, CampaignCardSummary> = emptyMap(),
) {
    var editor by rememberSaveable { mutableStateOf(false) }
    OnEachSave(state.saveCount) { editor = false }
    MoSectionHeader("Campañas", action = { TextButton(onClick = { editor = true }, modifier = Modifier.testTag("add-campaign")) { Text("Añadir") } })
    when {
        state.isLoading -> CircularProgressIndicator()
        state.error != null -> MoErrorState("No pudimos abrir las campañas", state.error)
        state.current.isEmpty() && state.history.isEmpty() -> MoEmptyState("Aún no hay campañas", "Crea una campaña y selecciona las parcelas que participan.", icon = MoIcons.Campaign)
        else -> {
            state.current.forEach { CampaignRow(it, summaries[it.id], onCampaignSelected) }
            if (state.history.isNotEmpty()) {
                MoSectionHeader("Histórico")
                state.history.forEach { CampaignRow(it, summaries[it.id], onCampaignSelected) }
            }
        }
    }
    if (editor) ModalBottomSheet(onDismissRequest = { editor = false }) {
        CampaignEditor(state.parcels, state.nameError, state.dateError, state.isSaving, onCreate, { editor = false })
    }
}

@Composable
private fun CampaignRow(campaign: Campaign, summary: CampaignCardSummary?, onSelected: (UUID) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button) { onSelected(campaign.id) }.testTag("campaign-row"),
        colors = CardDefaults.cardColors(containerColor = MoSurfaceTokens.cardSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MoSurfaceTokens.cardStroke),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = MoSpacing.sm, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(MoSpacing.xs)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text(campaign.name, style = MaterialTheme.typography.titleSmall); Text("Inicio ${campaign.startDate.format(SHORT_DATE)}", style = MaterialTheme.typography.bodySmall, color = MoSurfaceTokens.secondaryText) }
                MoStatusChip(campaign.status.label(), tone = campaign.status.tone())
            }
            // #246: a quick summary outside, the full detail inside.
            summary?.let { CampaignFacts(campaignFacts(it)) }
        }
    }
}

@Composable
internal fun CampaignEditor(
    parcels: List<CampaignParcelOption>, nameError: String?, dateError: String?, isSaving: Boolean,
    onSave: (CampaignDraft) -> Unit, onCancel: () -> Unit, initial: CampaignDraft = CampaignDraft(),
    isEditing: Boolean = false,
) {
    var name by rememberSaveable(initial.name) { mutableStateOf(initial.name) }
    var date by rememberSaveable(initial.startDate) { mutableStateOf(initial.startDate?.toString().orEmpty()) }
    var notes by rememberSaveable(initial.notes) { mutableStateOf(initial.notes) }
    var selected by rememberSaveable(initial.parcelIds) { mutableStateOf(initial.parcelIds.map(UUID::toString)) }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(MoSpacing.screen), verticalArrangement = Arrangement.spacedBy(MoSpacing.md)) {
        Text(if (isEditing) "Editar campaña" else "Nueva campaña", style = MaterialTheme.typography.headlineSmall)
        MoTextField(name, { name = it }, "Nombre", isError = nameError != null, supportingText = nameError, modifier = Modifier.testTag("campaign-name"))
        MoDateInputField(date, { date = it }, "Fecha de inicio", isError = dateError != null, supportingText = dateError, modifier = Modifier.testTag("campaign-start-date"))
        MoSectionHeader("Parcelas")
        if (parcels.isEmpty()) Text("Primero añade una parcela a esta finca.", color = MoSurfaceTokens.secondaryText)
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
    /** CR-010 A2: hand-typed legacy kilos with no Pesada; shown apart, never in the total. */
    val legacyGrams: Long? = null,
    val fatYieldHundredths: Int? = null,
    /** #450: posted money per currency — never one currency shown as the whole ledger. Empty: none yet. */
    val expenses: List<com.isivoltpro.maginaolivo.domain.expense.RecollectionCurrency> = emptyList(),
    /** #365: «1 persona · 1 jornada · 65,00 €»; null while the jornales are still loading. */
    val labourLine: String? = null,
    /**
     * #449: false when jornales, machinery or costs of the campaign are still unconfirmed; null
     * while they load (never claimed complete or incomplete before they are read).
     */
    val costComplete: Boolean? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampaignDetailRoute(
    campaignId: UUID,
    persistence: LocalPersistence,
    onHarvests: () -> Unit = {},
    onDeliveries: () -> Unit = {},
    /** #373/#375: a running campaign's «Pesadas» opens Nueva pesada on its Farm, not the global list. */
    onNewPesada: (farmId: UUID) -> Unit = {},
    /** #365: «+ Añadir jornal» opens today's recolección day of this campaign's Farm on its Jornales. */
    onAddLabour: (farmId: UUID) -> Unit = {},
) {
    val vm: CampaignDetailViewModel = viewModel(key = "campaign-$campaignId", factory = viewModelFactory {
        initializer { CampaignDetailViewModel(campaignId, persistence.campaignRepository) }
    })
    val state by vm.state.collectAsStateWithLifecycle()
    val harvests by remember(campaignId) { persistence.harvestRepository.observeForCampaign(campaignId) }.collectAsStateWithLifecycle(emptyList())
    val deliveries by remember(campaignId) { persistence.deliveryRepository.observeForCampaign(campaignId) }.collectAsStateWithLifecycle(emptyList())
    val expenses by remember { persistence.expenseRepository.observeAll() }.collectAsStateWithLifecycle(emptyList())
    val labour by remember(campaignId) { persistence.labourRepository.observeForCampaign(campaignId) }
        .collectAsStateWithLifecycle(null)
    val equipment by remember(campaignId) { persistence.equipmentRepository.observeForCampaign(campaignId) }
        .collectAsStateWithLifecycle(null)
    var labourOpen by rememberSaveable { mutableStateOf(false) }
    val summary = remember(harvests, deliveries, expenses, labour, equipment) {
        val harvest = HarvestSummary.of(harvests)
        val delivery = DeliverySummary.of(deliveries)
        val ledger = com.isivoltpro.maginaolivo.domain.expense.RecollectionLedger.posted(expenses.filter { it.campaignId == campaignId })
        CampaignSummaryUi(
            harvestedGrams = harvest.totalGrams?.takeIf { harvest.weighedCount > 0 },
            // #366 (Codex): calendar days, the same count as Recolección and the Cuaderno.
            harvestCount = com.isivoltpro.maginaolivo.feature.harvests.harvestDayCount(harvests),
            deliveredGrams = delivery.deliveredGrams?.takeIf { delivery.deliveryCount > 0 },
            deliveryCount = delivery.deliveryCount,
            legacyGrams = legacyUnweighedGrams(harvests, deliveries)?.takeIf { it > 0 },
            fatYieldHundredths = delivery.fatYield?.hundredths,
            expenses = ledger,
            labourLine = labour?.let { entries ->
                com.isivoltpro.maginaolivo.feature.harvests.campaignLabourLine(
                    entries, com.isivoltpro.maginaolivo.domain.expense.RecollectionLedger.of(campaignId, expenses, deliveries),
                )
            },
            costComplete = labour?.let { jornales ->
                equipment?.let { maquinaria ->
                    val days = harvests.map { it.id }.toSet()
                    com.isivoltpro.maginaolivo.domain.expense.RecollectionCostCompleteness.of(
                        jornales.filter { it.harvestId in days },
                        maquinaria.filter { it.harvestId in days },
                        expenses.filter { it.campaignId == campaignId },
                    ).complete
                }
            },
        )
    }
    // Phase 25: the Farm's name and place head the report; without it the campaign's own
    // snapshot is used, so a renamed or archived Farm never rewrites printed history.
    val farm by remember(state.campaign?.farmId) {
        state.campaign?.farmId?.let { persistence.farmRepository.observeById(it) }
            ?: kotlinx.coroutines.flow.flowOf(null)
    }.collectAsStateWithLifecycle(null)
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var reportStatus by remember { mutableStateOf<String?>(null) }
    CampaignDetailScreen(
        state, vm::update, vm::activate, vm::markHarvest, vm::closeToday, vm::reopen, vm::archivePreparation,
        summary = summary,
        onHarvests = onHarvests,
        // #511: «Pesadas» always means the list belonging to this Campaign.
        // Creation, when allowed, lives inside that scoped list.
        onDeliveries = onDeliveries,
        onLabour = { labourOpen = true },
        onReport = {
            val campaign = state.campaign
            if (campaign != null) {
                reportStatus = "Preparando el informe…"
                scope.launch {
                    val written = runCatching {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            writeCampaignReport(context, campaign, farm, deliveries, harvests, expenses)
                        }
                    }
                    reportStatus = written.fold(
                        onSuccess = { file ->
                            shareReport(context, file)
                                .fold({ "Informe listo: ${file.name}" }, { "El informe está guardado, pero no hay con qué abrirlo." })
                        },
                        onFailure = { "No hemos podido crear el informe en este teléfono." },
                    )
                }
            }
        },
        reportStatus = reportStatus,
    )
    // #365: the one Jornales detail (people and payments) of this campaign, as from the Cuaderno.
    if (labourOpen) {
        ModalBottomSheet(containerColor = MoSurfaceTokens.cardSurface, onDismissRequest = { labourOpen = false }) {
            com.isivoltpro.maginaolivo.feature.harvests.LabourPaymentsRoute(
                campaignId,
                persistence,
                onAddLabour = {
                    val campaign = state.campaign
                    if (campaign != null && campaign.status.isRunning) {
                        labourOpen = false
                        onAddLabour(campaign.farmId)
                    }
                },
            ) { labourOpen = false }
        }
    }
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
    onClose: () -> Unit, onReopen: () -> Unit, onArchive: () -> Unit,
    summary: CampaignSummaryUi = CampaignSummaryUi(),
    onHarvests: () -> Unit = {},
    onDeliveries: () -> Unit = {},
    onLabour: () -> Unit = {},
    /** Phase 25: writes the campaign's PDF and offers to share it. */
    onReport: () -> Unit = {},
    /** What the report action is doing, or why it could not; null while nothing is said. */
    reportStatus: String? = null,
) {
    var confirmation by rememberSaveable { mutableStateOf<String?>(null) }
    var editor by rememberSaveable { mutableStateOf(false) }
    OnEachSave(state.saveCount) { editor = false }
    Scaffold(Modifier.fillMaxSize().testTag("campaign-detail-root"), containerColor = MoSurfaceTokens.appBackground, contentWindowInsets = WindowInsets(0, 0, 0, 0)) { padding ->
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
                        // CR-011 §20: Campaña's section colour is the deep green.
                        MoIconBadge(MoIcons.Campaign, tint = MoKpiKind.CAMPAIGN.tint, container = MoKpiKind.CAMPAIGN.container)
                        Text(campaign.name, style = MaterialTheme.typography.headlineMedium, color = MoColors.current.primaryText, modifier = Modifier.weight(1f))
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
                        color = MoSurfaceTokens.secondaryText,
                    )
                    MoMetricGrid(
                        content = listOf(
                            { m ->
                                MoKpiMetric(
                                    "Kg pesados",
                                    summary.deliveredGrams?.let(Weight::format) ?: "—",
                                    m.testTag("campaign-metric-weighed"),
                                    icon = MoIcons.Delivery,
                                    kind = MoKpiKind.PESADAS,
                                    supportingText = if (summary.deliveredGrams == null) "Aún no hay kilos pesados" else "Suma de todas las pesadas",
                                )
                            },
                            { m ->
                                MoKpiMetric(
                                    "Pesadas",
                                    summary.deliveryCount.toString(),
                                    m.testTag("campaign-metric-weighings"),
                                    icon = MoIcons.Checklist,
                                    kind = MoKpiKind.PESADAS,
                                    supportingText = if (summary.deliveryCount == 0) "Aún no hay pesadas" else "En cooperativa o almazara",
                                )
                            },
                            { m ->
                                MoKpiMetric(
                                    "Rendimiento graso",
                                    summary.fatYieldHundredths?.let(Percent::format) ?: "—",
                                    m.testTag("campaign-metric-yield"),
                                    icon = MoIcons.Percent,
                                    kind = MoKpiKind.PESADAS,
                                    supportingText = if (summary.fatYieldHundredths == null) "Llegará con los análisis de las pesadas" else "Ponderado por kilos",
                                )
                            },
                            { m ->
                                MoKpiMetric(
                                    "Gastos",
                                    if (summary.expenses.isEmpty()) "—" else summary.expenses.joinToString(" · ") { ledger ->
                                        ledger.amount()?.let { Money.format(it, ledger.currency) } ?: "Importe no disponible (${ledger.currency})"
                                    },
                                    m.testTag("campaign-metric-expenses"),
                                    icon = MoIcons.Euro,
                                    kind = MoKpiKind.COSTES,
                                    supportingText = when {
                                        summary.expenses.isEmpty() -> "Aún no hay gastos de esta campaña"
                                        summary.expenses.size > 1 -> "Varias monedas: cada una por separado, sin convertir"
                                        else -> "Gastos anotados"
                                    },
                                )
                            },
                        ),
                    )
                    summary.legacyGrams?.let { legacy ->
                        Text(
                            "Además, ${Weight.format(legacy)} registrados sin pesada (histórico): no entran en el total de kg pesados.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MoSurfaceTokens.secondaryText,
                            modifier = Modifier.testTag("campaign-legacy-kilos"),
                        )
                    }
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
                        title = "Días de recolección",
                        subtitle = if (summary.harvestCount == 0) "Se crean al registrar la primera pesada del día" else "${summary.harvestCount} ${if (summary.harvestCount == 1) "día" else "días"}",
                        icon = MoIcons.Harvest,
                        onClick = onHarvests,
                        modifier = Modifier.testTag("campaign-open-harvests"),
                        trailing = { Icon(MoIcons.ChevronRight, contentDescription = null, tint = MoSurfaceTokens.secondaryText, modifier = Modifier.size(18.dp)) },
                    )
                    MoCompactListItem(
                        title = "Pesadas",
                        subtitle = if (summary.deliveryCount == 0) "Registrar kilos y vale de entrega" else "${summary.deliveryCount} pesadas",
                        icon = MoIcons.Delivery,
                        onClick = onDeliveries,
                        modifier = Modifier.testTag("campaign-open-deliveries"),
                        trailing = { Icon(MoIcons.ChevronRight, contentDescription = null, tint = MoSurfaceTokens.secondaryText, modifier = Modifier.size(18.dp)) },
                    )
                    MoCompactListItem(
                        title = "Jornales",
                        subtitle = summary.labourLine ?: "Cargando jornales…",
                        icon = MoIcons.People,
                        onClick = onLabour,
                        modifier = Modifier.testTag("campaign-open-labour"),
                        trailing = { Icon(MoIcons.ChevronRight, contentDescription = null, tint = MoSurfaceTokens.secondaryText, modifier = Modifier.size(18.dp)) },
                    )
                    // Phase 25: the campaign's report, written on this phone and shared from here.
                    MoCompactListItem(
                        title = "Informe PDF de la campaña",
                        subtitle = reportStatus ?: "Producción, costes, pesadas y días, con lo que falte dicho",
                        icon = MoIcons.Document,
                        onClick = onReport,
                        modifier = Modifier.testTag("campaign-report-pdf"),
                        trailing = { Icon(MoIcons.ChevronRight, contentDescription = null, tint = MoSurfaceTokens.secondaryText, modifier = Modifier.size(18.dp)) },
                    )
                    Spacer(Modifier.height(MoSpacing.xs))
                    when (campaign.status) {
                        CampaignStatus.PREPARATION -> {
                            if (campaign.snapshots.isEmpty()) {
                                Text(
                                    if (state.parcels.isEmpty()) {
                                        "Esta finca todavía no tiene parcelas. Añade una parcela antes de activar la campaña."
                                    } else {
                                        "Para activar la campaña, selecciona al menos una parcela."
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MoSurfaceTokens.secondaryText,
                                    modifier = Modifier.testTag("campaign-parcels-required"),
                                )
                                if (state.parcels.isNotEmpty()) {
                                    MoPrimaryButton(
                                        "Seleccionar parcelas",
                                        { editor = true },
                                        modifier = Modifier.fillMaxWidth().testTag("campaign-select-parcels"),
                                        enabled = !state.isSaving,
                                    )
                                }
                            } else {
                                MoPrimaryButton(
                                    "Activar campaña",
                                    { confirmation = "activate" },
                                    modifier = Modifier.fillMaxWidth().testTag("activate-campaign"),
                                    enabled = !state.isSaving,
                                )
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MoSpacing.xs)) {
                                MoSecondaryButton("Editar", { editor = true }, modifier = Modifier.weight(1f).testTag("edit-campaign"), enabled = !state.isSaving)
                                MoDestructiveButton("Archivar borrador", { confirmation = "archive" }, modifier = Modifier.weight(1f))
                            }
                        }
                        // CR-010: once active the farmer records Pesadas straight away; no «Iniciar recolección».
                        CampaignStatus.ACTIVE, CampaignStatus.HARVEST -> MoDestructiveButton("Cerrar campaña", { confirmation = "close" }, modifier = Modifier.fillMaxWidth().testTag("close-campaign"), enabled = !state.isSaving)
                        CampaignStatus.CLOSED -> {
                            Text("Histórico protegido", style = MaterialTheme.typography.titleSmall, color = MoSurfaceTokens.secondaryText)
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
            CampaignDraft(campaign.name, campaign.startDate, campaign.snapshots.map { it.parcelId }.toSet(), campaign.notes.orEmpty()),
            isEditing = true)
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
                color = MoSurfaceTokens.secondaryText,
            )
            if (confirmation == "close" && summary.costComplete == false) {
                // #449: closing is allowed; the farmer is told the cost/kg stays marked incomplete.
                Text(
                    "Hay costes sin confirmar. Puedes cerrar la campaña, pero el coste/kg quedará marcado como incompleto.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MoColors.current.warningText,
                    modifier = Modifier.testTag("close-cost-warning"),
                )
            }
            val confirm = {
                when (confirmation) { "activate" -> onActivate(); "harvest" -> onHarvest(); "close" -> onClose(); "reopen" -> onReopen(); "archive" -> onArchive() }
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
    CampaignStatus.PREPARATION -> "Borrador"; CampaignStatus.ACTIVE, CampaignStatus.HARVEST -> "Activa"; CampaignStatus.CLOSED -> "Cerrada"
}
