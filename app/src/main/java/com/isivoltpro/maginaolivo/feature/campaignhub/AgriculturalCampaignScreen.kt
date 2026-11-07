package com.isivoltpro.maginaolivo.feature.campaignhub

import androidx.compose.foundation.BorderStroke
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.isivoltpro.maginaolivo.app.ActiveFarmStore
import com.isivoltpro.maginaolivo.app.LocalPersistence
import com.isivoltpro.maginaolivo.data.local.model.isRunning
import com.isivoltpro.maginaolivo.domain.analytics.CampaignHistory
import com.isivoltpro.maginaolivo.domain.analytics.CampaignSeries
import com.isivoltpro.maginaolivo.domain.analytics.OliveSeason
import com.isivoltpro.maginaolivo.feature.farms.FarmOverviewSection
import com.isivoltpro.maginaolivo.feature.notebook.CampaignComparisonList
import com.isivoltpro.maginaolivo.feature.notebook.CampaignHistoryCharts
import com.isivoltpro.maginaolivo.feature.notebook.CampaignStatsSection
import com.isivoltpro.maginaolivo.feature.notebook.CampaignView
import com.isivoltpro.maginaolivo.feature.notebook.FarmCostsView
import com.isivoltpro.maginaolivo.feature.notebook.NotebookActions
import com.isivoltpro.maginaolivo.feature.notebook.NotebookUiState
import com.isivoltpro.maginaolivo.feature.notebook.NotebookViewModel
import com.isivoltpro.maginaolivo.feature.notebook.resolveNotebookFarmId
import com.isivoltpro.maginaolivo.domain.cycle.AgriculturalCycle
import com.isivoltpro.maginaolivo.domain.cycle.CycleSnapshot
import com.isivoltpro.maginaolivo.ui.components.*
import com.isivoltpro.maginaolivo.ui.theme.MoShape
import com.isivoltpro.maginaolivo.ui.theme.MoSpacing
import com.isivoltpro.maginaolivo.ui.theme.MoSurfaceTokens
import java.time.LocalDate
import java.util.UUID

internal enum class CampaignSection(val title: String, val description: String) {
    WORKS("Trabajos del año", "Consultar el historial de esta finca en Cuaderno"),
    RECOLLECTION("Recolección", "Días, pesadas, jornales y gastos de recogida"),
    ECONOMY("Economía y balance", "Gastos contabilizados · balance incompleto"),
    CHARTS("Gráficas e históricos", "Producción, rendimiento y comparación histórica"),
}

@Composable
fun AgriculturalCampaignRoute(
    persistence: LocalPersistence,
    activeFarmStore: ActiveFarmStore,
    requestedFarmId: UUID?,
    onRequestHandled: () -> Unit,
    actionsFor: (UUID) -> NotebookActions,
    onNotebook: (UUID) -> Unit,
    onGoToFields: () -> Unit,
    onRecollection: (UUID) -> Unit,
    onActiveFarmChanged: (UUID?) -> Unit = {},
) {
    var retry by remember { mutableStateOf(0) }
    val farms by remember(persistence, retry) {
        observeCampaignFarms(persistence.workspaceRepository, persistence.farmRepository)
    }.collectAsStateWithLifecycle(CampaignFarmsState())
    var chosen by rememberSaveable { mutableStateOf(activeFarmStore.get()?.toString()) }
    var allFarms by rememberSaveable { mutableStateOf(false) }
    val active = farms.farms.filter { it.archivedAt == null }
    val activeIds = active.map { it.id }
    LaunchedEffect(farms.isLoading, farms.error, farms.workspaceId, activeIds, requestedFarmId) {
        if (!farms.isLoading && farms.error == null) {
            val resolved = resolveNotebookFarmId(activeIds, requestedFarmId, chosen?.let { runCatching { UUID.fromString(it) }.getOrNull() })
            chosen = resolved?.toString()
            if (requestedFarmId != null) { allFarms = false; onRequestHandled() }
        }
    }
    val farm = active.firstOrNull { it.id.toString() == chosen }
    LaunchedEffect(farms.isLoading, farms.error, farm?.id, allFarms) {
        val visible = farm?.id.takeIf { !farms.isLoading && farms.error == null && !allFarms }
        onActiveFarmChanged(visible)
        visible?.let(activeFarmStore::set)
    }
    val scroll = rememberScrollState()
    Column(Modifier.fillMaxSize().testTag("agricultural-campaign-root")
        .verticalScroll(scroll).padding(MoSpacing.screen), verticalArrangement = Arrangement.spacedBy(MoSpacing.sm)) {
        Text("Campaña", style = MaterialTheme.typography.headlineLarge)
        if(persistence.agriculturalCycles==null || allFarms) Text("Agrupación provisional: septiembre–agosto", style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.testTag("campaign-provisional-period"))
        when {
            farms.isLoading -> CircularProgressIndicator()
            farms.error != null -> MoErrorState("No pudimos abrir Campaña", farms.error!!, onRetry = { retry++ })
            farms.farms.isEmpty() -> MoEmptyState("Aún no tienes fincas", "Crea una finca para consultar su campaña.",
                actionText = "Ir a Mi Campo", onAction = onGoToFields, icon = MoIcons.Tree)
            else -> {
                var farmMenu by remember { mutableStateOf(false) }
                Box {
                    MoSecondaryButton(if (allFarms) "Todas mis fincas · resumen" else farm?.name ?: "Seleccionar finca", { farmMenu = true },
                        Modifier.fillMaxWidth().testTag("campaign-farm-selector"))
                    DropdownMenu(farmMenu, { farmMenu = false }) {
                        active.forEach { option -> DropdownMenuItem(text = { Text(option.name) }, onClick = {
                            chosen = option.id.toString(); activeFarmStore.set(option.id); allFarms = false; farmMenu = false
                        }) }
                        DropdownMenuItem(text = { Text("Todas mis fincas · solo consulta") }, onClick = { allFarms = true; farmMenu = false })
                    }
                }
                if (allFarms || farm == null) {
                    key(farms.workspaceId, farms.farms.map { it.id }, retry) {
                        val overview by remember { observeCampaignOverview(persistence, farms.workspaceId!!, farms.farms) }
                            .collectAsStateWithLifecycle(CampaignOverviewState())
                        HoldingSummary(overview, farms, { retry++ }) { id ->
                            if (id in activeIds) { chosen = id.toString(); allFarms = false }
                        }
                    }
                } else {
                    // A workspace change destroys the old read state and all contextual callbacks.
                    key(farms.workspaceId, farm.id, retry) {
                        val model: NotebookViewModel = viewModel(key = "campaign-hub-${farms.workspaceId}-${farm.id}-$retry", factory = viewModelFactory {
                            initializer { NotebookViewModel(farm.id, persistence.campaignRepository, persistence.activityRepository,
                                persistence.harvestRepository, persistence.deliveryRepository, persistence.expenseRepository,
                                persistence.labourRepository, persistence.equipmentRepository) }
                        })
                        val state by model.state.collectAsStateWithLifecycle()
                        var selected by rememberSaveable { mutableStateOf<String?>(null) }
                        var labour by rememberSaveable { mutableStateOf<String?>(null) }
                        val actions = actionsFor(farm.id).copy(onLabour = { id -> labour = id.toString() })
                        var cycleSnapshot by remember {mutableStateOf<CycleSnapshot?>(null)}
                        var viewedCycle by remember {mutableStateOf<UUID?>(null)}
                        val agriculturalCycle=cycleSnapshot?.cycles?.firstOrNull {it.id==viewedCycle}
                        persistence.agriculturalCycles?.let {cycles->
                            AgriculturalCyclePanel(cycles,farm.id,LocalDate.now(),onSelection={snapshot,id->
                                cycleSnapshot=snapshot;viewedCycle=id
                            },onRecord={record->when(record.kind) {
                                "ACTIVITY"->actions.onActivity(record.id)
                                "EXPENSE"->actions.onExpense(record.id)
                                "RECOLLECTION"->onRecollection(record.id)
                            }})
                        }
                        LaunchedEffect(state.campaigns,selected,viewedCycle,cycleSnapshot?.scope?.revision) {
                            val allowed=if(agriculturalCycle==null) state.campaigns else {
                                val ids=cycleSnapshot!!.records.filter {it.cycleId==viewedCycle && it.kind=="RECOLLECTION"}.map {it.id}.toSet()
                                state.campaigns.filter {it.id in ids}
                            }
                            val desired=allowed.firstOrNull {it.id.toString()==selected} ?: if(agriculturalCycle!=null)
                                allowed.firstOrNull {it.id==state.selectedCampaignId} ?: allowed.firstOrNull() else null
                            if(desired!=null && desired.id!=state.selectedCampaignId) model.selectCampaign(desired.id)
                        }
                        if(persistence.agriculturalCycles==null || cycleSnapshot!=null) {
                            val scoped=if(agriculturalCycle==null) state else state.forAgriculturalCycle(cycleSnapshot!!,agriculturalCycle.id)
                            AgriculturalCampaignContent(scoped, actions, { selected = it.toString(); model.selectCampaign(it) },
                                { onNotebook(farm.id) }, { retry++ }, onRecollection,
                                onSectionChanged = { scroll.scrollTo(0) },agriculturalCycle=agriculturalCycle,
                                cyclesTracked=persistence.agriculturalCycles!=null)
                        }
                        labour?.let { id ->
                            @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
                            androidx.compose.material3.ModalBottomSheet(onDismissRequest = { labour = null }, containerColor = MoSurfaceTokens.cardSurface) {
                                com.isivoltpro.maginaolivo.feature.harvests.LabourPaymentsRoute(UUID.fromString(id), persistence) { labour = null }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun AgriculturalCampaignContent(state: NotebookUiState, actions: NotebookActions, onSelectCampaign: (UUID) -> Unit,
    onNotebook: () -> Unit, onRetry: () -> Unit, onRecollection: (UUID) -> Unit,
    onSectionChanged: suspend () -> Unit = {}, agriculturalCycle: AgriculturalCycle? = null,
    cyclesTracked: Boolean = false) {
    var section by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler(enabled = section != null) { section = null }
    LaunchedEffect(section) { onSectionChanged() }
    when {
        state.isLoading -> CircularProgressIndicator(Modifier.testTag("campaign-loading"))
        state.error != null -> MoErrorState("No pudimos consultar los registros", state.error, onRetry = onRetry)
        else -> {
            var menu by remember { mutableStateOf(false) }
            val notebook = state.notebook
            if (section == null) {
                if(agriculturalCycle==null && cyclesTracked) Text("Histórico sin ciclo asignado · agrupación provisional septiembre–agosto",
                    style=MaterialTheme.typography.bodySmall,modifier=Modifier.testTag("campaign-provisional-period"))
                Text(agriculturalCycle?.let {"${it.label} · desde ${it.startDate.cycleDate()}"}
                    ?: "Periodo ${OliveSeason.of(notebook?.campaign?.startDate ?: LocalDate.now())}", style = MaterialTheme.typography.titleMedium)
                Box {
                    MoTertiaryButton(notebook?.campaign?.let { "${it.name} · Recolección ${when {
                        it.status.isRunning -> "abierta"
                        it.status == com.isivoltpro.maginaolivo.data.local.model.CampaignStatus.CLOSED -> "cerrada"
                        else -> "en borrador"
                    }}" }
                        ?: "Sin recolección", { menu = true }, Modifier.testTag("campaign-recollection-selector"))
                    DropdownMenu(menu, { menu = false }) {
                        state.campaigns.sortedByDescending { it.startDate }.forEach { campaign ->
                            DropdownMenuItem(text = { Text(campaign.name) }, onClick = { onSelectCampaign(campaign.id); menu = false })
                        }
                    }
                }
                CampaignSection.entries.forEach { option ->
                    Surface(shape = MoShape.card, color = MoSurfaceTokens.cardSurface, border = BorderStroke(1.dp, MoSurfaceTokens.cardStroke),
                        modifier = Modifier.fillMaxWidth()) {
                        MoCompactListItem(title = option.title, subtitle = option.description,
                            icon = when (option) { CampaignSection.WORKS -> MoIcons.Activity; CampaignSection.RECOLLECTION -> MoIcons.Harvest
                                CampaignSection.ECONOMY -> MoIcons.Euro; CampaignSection.CHARTS -> MoIcons.Campaign },
                            onClick = { if (option == CampaignSection.WORKS && agriculturalCycle==null) onNotebook() else section = option.name },
                            modifier = Modifier.testTag("campaign-section-${option.name}"))
                    }
                }
                Text(if(agriculturalCycle==null) "Trabajos abre el historial completo de la finca; no se asigna automáticamente a este periodo."
                    else "Solo se muestran los registros vinculados a esta campaña. El resto del histórico sigue en Cuaderno.", style = MaterialTheme.typography.bodySmall)
            } else {
                MoTertiaryButton("← Volver a Campaña", { section = null },
                    Modifier.testTag("campaign-sections-back"))
                Text(CampaignSection.entries.first { it.name == section }.title,
                    style = MaterialTheme.typography.titleLarge)
                notebook?.campaign?.let { Text(if(agriculturalCycle==null) "${it.name} · ${OliveSeason.of(it.startDate)}" else "${agriculturalCycle.label} · ${it.name}",
                    style = MaterialTheme.typography.bodySmall) }
            }
            when (section) {
                CampaignSection.WORKS.name -> {
                    val works=state.farmNotebook?.activities.orEmpty()
                    if(works.isEmpty()) Text("Aún no hay trabajos vinculados a esta campaña.")
                    works.forEach {work->com.isivoltpro.maginaolivo.feature.notebook.WorkRow(work) {actions.onActivity(work.id)}}
                    MoTertiaryButton("Ver el histórico completo en Cuaderno",onNotebook)
                }
                CampaignSection.RECOLLECTION.name -> if (notebook == null) NoRecollection(actions) else {
                    MoSecondaryButton("Ver días y pesadas de esta recolección", { onRecollection(notebook.campaign.id) }, Modifier.fillMaxWidth())
                    CampaignView(notebook, state, actions, onSelectCampaign)
                }
                CampaignSection.ECONOMY.name -> {
                    Text("Ingresos pendientes de registrar · balance incompleto", modifier = Modifier.testTag("campaign-balance-incomplete"))
                    Text(if(agriculturalCycle==null) "Gastos de esta finca: historial completo, sin gastos compartidos sin asignar. El informe por periodo está en Todas mis fincas."
                        else "Gastos vinculados a esta campaña, sin duplicar los costes de trabajos y jornadas. Los gastos sin asignar siguen en Cuaderno.", style = MaterialTheme.typography.bodySmall)
                    state.farmNotebook?.let { FarmCostsView(it, state.campaigns.filter { c -> c.status.isRunning }.maxByOrNull { c -> c.startDate }, actions) }
                }
                CampaignSection.CHARTS.name -> if (notebook == null) NoRecollection(actions) else {
                    CampaignStatsSection(notebook, CampaignSeries.of(notebook))
                    CampaignHistoryCharts(CampaignHistory.of(state.comparison), notebook.campaign.id, onSelectCampaign)
                    CampaignComparisonList(state.comparison, onSelectCampaign)
                }
            }
        }
    }
}

@Composable
private fun NoRecollection(actions: NotebookActions) = MoEmptyState("Aún no hay recolecciones",
    "Tus trabajos y gastos generales siguen disponibles en Cuaderno.", actionText = "Gestionar recolecciones", onAction = actions.onCampaigns,
    icon = MoIcons.Harvest, modifier = Modifier.testTag("campaign-no-recollection"))

@Composable
private fun HoldingSummary(state: CampaignOverviewState, farms: CampaignFarmsState, onRetry: () -> Unit, onFarm: (UUID) -> Unit) {
    Text("Resumen de todas mis fincas · solo consulta", style = MaterialTheme.typography.titleMedium)
    Text("Ingresos pendientes de registrar · balance incompleto")
    when {
        state.isLoading -> CircularProgressIndicator()
        state.error != null -> MoErrorState("Resumen no disponible", state.error, onRetry = onRetry)
        else -> {
            Text("Incluye fincas activas y archivadas. Los gastos sin finca están Sin asignar y solo cuentan en este resumen global.", style = MaterialTheme.typography.bodySmall)
            var scope by rememberSaveable { mutableStateOf(false) }
            MoTertiaryButton(if (scope) "Ocultar ámbito del informe" else "Ver fincas y recolecciones incluidas", { scope = !scope })
            if (scope) farms.farms.forEach { farm ->
                Text(farm.name + if (farm.archivedAt != null) " · Archivada" else "")
                state.campaigns.filter { it.farmId == farm.id }.forEach { campaign -> Text("${OliveSeason.of(campaign.startDate)} · ${campaign.name}", style = MaterialTheme.typography.bodySmall) }
            }
            if (state.unassigned.isNotEmpty()) Text("Sin asignar: ${state.unassigned.size} gastos sin finca. No se atribuyen a ninguna finca.")
            if (state.overviews.isEmpty()) Text("Sin recolecciones para elaborar un informe por periodo. Los gastos de cada finca se consultan en Economía y balance.")
            FarmOverviewSection(state.overviews, onFarm)
        }
    }
}
