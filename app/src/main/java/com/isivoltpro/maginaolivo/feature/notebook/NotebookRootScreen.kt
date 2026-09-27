package com.isivoltpro.maginaolivo.feature.notebook

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.isivoltpro.maginaolivo.app.ActiveFarmStore
import com.isivoltpro.maginaolivo.app.LocalPersistence
import com.isivoltpro.maginaolivo.data.local.model.CampaignStatus
import com.isivoltpro.maginaolivo.domain.farm.Farm
import com.isivoltpro.maginaolivo.feature.activities.RegisterActivityViewModel
import com.isivoltpro.maginaolivo.ui.components.MoEmptyState
import com.isivoltpro.maginaolivo.ui.components.MoErrorState
import com.isivoltpro.maginaolivo.ui.components.MoIcons
import com.isivoltpro.maginaolivo.ui.components.MoPrimaryButton
import com.isivoltpro.maginaolivo.ui.components.MoSecondaryButton
import com.isivoltpro.maginaolivo.ui.theme.MoCream
import com.isivoltpro.maginaolivo.ui.theme.MoOliveDark
import com.isivoltpro.maginaolivo.ui.theme.MoShape
import com.isivoltpro.maginaolivo.ui.theme.MoSpacing
import com.isivoltpro.maginaolivo.ui.theme.MoTextSecondary
import com.isivoltpro.maginaolivo.ui.theme.MoWarmWhite
import java.util.UUID

/** Issue #246 §2: the nine things a farmer writes down, one tap each. */
enum class NotebookQuickAction(val label: String, val tag: String, val description: String) {
    WORK("Trabajo", "notebook-quick-work", "Poda, abonado, labores del suelo…"),
    IRRIGATION("Riego", "notebook-quick-irrigation", "Horas, m³ y sector"),
    TREATMENT("Tratamiento", "notebook-quick-treatment", "Producto, dosis y motivo"),
    LABOUR("Jornal", "notebook-quick-labour", "Quién trabajó y cuánto"),
    HARVEST("Cosecha", "notebook-quick-harvest", "Kilos recogidos en el campo"),
    DELIVERY("Entrega", "notebook-quick-delivery", "Pesada en la cooperativa o almazara"),
    EXPENSE("Gasto", "notebook-quick-expense", "Facturas, tickets y pagos"),
    MACHINERY("Maquinaria", "notebook-quick-machinery", "Uso del tractor u otra máquina"),
    DOCUMENT("Documento", "notebook-quick-document", "Foto o PDF de un papel"),
}

/** Issue #246 §4: Diario · Fitosanitario · Gastos · Campaña — views of the same records. */
enum class NotebookHubTab(val label: String, val tag: String) {
    DIARY("Diario", "notebook-tab-diary"),
    PHYTO("Fitosanitario", "notebook-tab-phyto"),
    EXPENSES("Gastos", "notebook-tab-expenses"),
    CAMPAIGN("Campaña", "notebook-tab-campaign"),
}

internal fun NotebookQuickAction.icon(): ImageVector = when (this) {
    NotebookQuickAction.WORK -> MoIcons.Activity
    NotebookQuickAction.IRRIGATION -> MoIcons.Drop
    NotebookQuickAction.TREATMENT -> MoIcons.Spray
    NotebookQuickAction.LABOUR -> MoIcons.People
    NotebookQuickAction.HARVEST -> MoIcons.Harvest
    NotebookQuickAction.DELIVERY -> MoIcons.Delivery
    NotebookQuickAction.EXPENSE -> MoIcons.Euro
    NotebookQuickAction.MACHINERY -> MoIcons.Tractor
    NotebookQuickAction.DOCUMENT -> MoIcons.Document
}

@Composable
fun NotebookRootRoute(
    persistence: LocalPersistence,
    activeFarmStore: ActiveFarmStore,
    /** The active Farm, whether its Campaign is in recolección, and the "Finca · Campaña" line. */
    onRegisterToday: (farmId: UUID?, inRecollection: Boolean, context: String?) -> Unit,
    /** The quick action, the active Farm, and whether its Campaign is in recolección. */
    onQuickAction: (NotebookQuickAction, UUID, Boolean) -> Unit,
    actionsFor: (UUID) -> NotebookActions,
    onGoToFields: () -> Unit,
    /**
     * UX-F (Issue #246 §5): "Registrar" pressed on a Farm or Parcel in Mi Campo. The notebook
     * switches to that Farm and opens "Registrar hoy" with its context, once.
     */
    registerRequestFarmId: UUID? = null,
    onRegisterRequestHandled: () -> Unit = {},
) {
    // The same Farm list the register flow already uses; no second source.
    val farmsViewModel: RegisterActivityViewModel = viewModel(
        key = "notebook-root",
        factory = viewModelFactory {
            initializer { RegisterActivityViewModel(persistence.farmRepository, persistence.workspaceRepository) }
        },
    )
    val farms by farmsViewModel.state.collectAsStateWithLifecycle()
    var chosen by rememberSaveable { mutableStateOf(activeFarmStore.get()?.toString()) }
    val activeFarm = farms.farms.firstOrNull { it.id.toString() == chosen } ?: farms.farms.firstOrNull()
    val notebook = activeFarm?.let { farm ->
        val viewModel: NotebookViewModel = viewModel(
            key = "notebook-${farm.id}",
            factory = viewModelFactory {
                initializer {
                    NotebookViewModel(
                        farm.id, persistence.campaignRepository, persistence.activityRepository,
                        persistence.harvestRepository, persistence.deliveryRepository, persistence.expenseRepository,
                        persistence.labourRepository, persistence.equipmentRepository,
                    )
                }
            },
        )
        val state by viewModel.state.collectAsStateWithLifecycle()
        state to viewModel
    }
    LaunchedEffect(registerRequestFarmId) {
        registerRequestFarmId?.let { chosen = it.toString() }
    }
    val notebookReady = notebook?.first?.isLoading == false
    LaunchedEffect(registerRequestFarmId, activeFarm?.id, notebookReady, farms.isLoading) {
        val requested = registerRequestFarmId ?: return@LaunchedEffect
        if (farms.isLoading) return@LaunchedEffect
        // The Farm is gone (archived meanwhile): nothing to open.
        if (farms.farms.none { it.id == requested }) {
            onRegisterRequestHandled()
            return@LaunchedEffect
        }
        val farm = activeFarm?.takeIf { it.id == requested } ?: return@LaunchedEffect
        if (!notebookReady) return@LaunchedEffect
        val campaign = notebook?.first?.notebook?.campaign
        onRegisterToday(farm.id, campaign?.status == CampaignStatus.HARVEST, contextLine(farm, campaign?.name))
        onRegisterRequestHandled()
    }
    NotebookHomeScreen(
        isLoading = farms.isLoading,
        error = farms.error,
        farms = farms.farms,
        activeFarm = activeFarm,
        notebook = notebook?.first,
        actions = activeFarm?.let { actionsFor(it.id) },
        onSelectFarm = { id ->
            chosen = id.toString()
            activeFarmStore.set(id)
        },
        onSelectCampaign = { id -> notebook?.second?.selectCampaign(id) },
        onRegisterToday = {
            val campaign = notebook?.first?.notebook?.campaign
            onRegisterToday(
                activeFarm?.id,
                campaign?.status == CampaignStatus.HARVEST,
                activeFarm?.let { contextLine(it, campaign?.name) },
            )
        },
        onQuickAction = { action ->
            val farm = activeFarm ?: return@NotebookHomeScreen
            onQuickAction(action, farm.id, notebook?.first?.notebook?.campaign?.status == CampaignStatus.HARVEST)
        },
        onRetry = farmsViewModel::retry,
        onGoToFields = onGoToFields,
    )
}

/**
 * UX-C (Issue #246) — Mi Cuaderno: ¿Qué has hecho hoy? Context first (Farm and Campaign), one
 * big "Registrar hoy", the nine quick actions, then the notebook as Diario · Fitosanitario ·
 * Gastos · Campaña. Every view reads the same local records; nothing is copied or totalled
 * twice, and all of it works without signal.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NotebookHomeScreen(
    isLoading: Boolean,
    error: String?,
    farms: List<Farm>,
    activeFarm: Farm?,
    notebook: NotebookUiState?,
    actions: NotebookActions?,
    onSelectFarm: (UUID) -> Unit,
    onSelectCampaign: (UUID) -> Unit,
    onRegisterToday: () -> Unit,
    onQuickAction: (NotebookQuickAction) -> Unit,
    onRetry: () -> Unit = {},
    onGoToFields: () -> Unit = {},
) {
    var tab by rememberSaveable { mutableStateOf(NotebookHubTab.DIARY) }
    var choosingFarm by remember { mutableStateOf(false) }
    Scaffold(
        Modifier.fillMaxSize().testTag("notebook-root"),
        containerColor = MoCream,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).statusBarsPadding().verticalScroll(rememberScrollState())
                .padding(horizontal = MoSpacing.screen),
            verticalArrangement = Arrangement.spacedBy(MoSpacing.sm),
        ) {
            Spacer(Modifier.height(MoSpacing.sm))
            Text("Mi Cuaderno", style = MaterialTheme.typography.headlineLarge, color = MoOliveDark)
            when {
                isLoading -> CircularProgressIndicator(Modifier.testTag("notebook-root-loading"))
                error != null -> MoErrorState("No pudimos abrir tus fincas", error, onRetry = onRetry)
                activeFarm == null -> {
                    // "Registrar hoy" stays reachable; its flow explains that a Farm comes first.
                    MoPrimaryButton("Registrar hoy", onRegisterToday, Modifier.fillMaxWidth().testTag("notebook-register-today"))
                    MoEmptyState(
                        "Aún no tienes fincas",
                        "Crea una finca en Mi Campo y aquí llevarás su cuaderno del día a día.",
                        actionText = "Ir a Mi Campo",
                        onAction = onGoToFields,
                        icon = MoIcons.Tree,
                        modifier = Modifier.testTag("notebook-root-no-farms"),
                    )
                }
                else -> {
                    // Context always visible: which Farm and which Campaign this writes into.
                    val campaign = notebook?.notebook?.campaign
                    Text(
                        contextLine(activeFarm, campaign?.name),
                        style = MaterialTheme.typography.titleMedium,
                        color = MoOliveDark,
                        modifier = Modifier.testTag("notebook-context"),
                    )
                    if (farms.size > 1) {
                        if (choosingFarm) {
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(MoSpacing.xs)) {
                                farms.forEach { farm ->
                                    FilterChip(
                                        selected = farm.id == activeFarm.id,
                                        onClick = {
                                            onSelectFarm(farm.id)
                                            choosingFarm = false
                                        },
                                        label = { Text(farm.name) },
                                        modifier = Modifier.testTag("notebook-farm-option"),
                                    )
                                }
                            }
                        } else {
                            MoSecondaryButton("Cambiar finca", { choosingFarm = true }, Modifier.testTag("notebook-change-farm"))
                        }
                    }
                    Text("¿Qué has hecho hoy?", style = MaterialTheme.typography.bodyLarge, color = MoTextSecondary)
                    MoPrimaryButton("Registrar hoy", onRegisterToday, Modifier.fillMaxWidth().testTag("notebook-register-today"))
                    FlowRow(
                        Modifier.fillMaxWidth().testTag("notebook-quick-actions"),
                        horizontalArrangement = Arrangement.spacedBy(MoSpacing.xs),
                        verticalArrangement = Arrangement.spacedBy(MoSpacing.xs),
                    ) {
                        NotebookQuickAction.entries.forEach { action ->
                            FilterChip(
                                selected = false,
                                onClick = { onQuickAction(action) },
                                label = { Text(action.label) },
                                leadingIcon = { Icon(action.icon(), contentDescription = null) },
                                modifier = Modifier.testTag(action.tag),
                            )
                        }
                    }
                    NotebookHub(notebook, actions, tab, { tab = it }, onSelectCampaign)
                }
            }
            Spacer(Modifier.height(MoSpacing.lg))
        }
    }
}

@Composable
private fun NotebookHub(
    state: NotebookUiState?,
    actions: NotebookActions?,
    tab: NotebookHubTab,
    onTab: (NotebookHubTab) -> Unit,
    onSelectCampaign: (UUID) -> Unit,
) {
    when {
        state == null || state.isLoading -> CircularProgressIndicator(Modifier.testTag("notebook-loading"))
        state.error != null -> Text(state.error, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("notebook-error"))
        state.notebook == null || actions == null -> MoEmptyState(
            "Aún no hay campañas",
            "El cuaderno se ordena por campañas. Crea la de este año y aquí verás lo que registres.",
            actionText = actions?.let { "Ir a Campañas" },
            onAction = actions?.onCampaigns,
            icon = MoIcons.Campaign,
            modifier = Modifier.testTag("notebook-no-campaign"),
        )
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
            }
            TabRow(
                selectedTabIndex = tab.ordinal,
                containerColor = MoWarmWhite,
                contentColor = MoOliveDark,
                modifier = Modifier.clip(MoShape.card),
            ) {
                NotebookHubTab.entries.forEach { option ->
                    Tab(
                        selected = option == tab,
                        onClick = { onTab(option) },
                        text = { Text(option.label, maxLines = 1) },
                        modifier = Modifier.testTag(option.tag),
                    )
                }
            }
            // UX-E: four views of the same records; nothing is copied or totalled twice.
            when (tab) {
                NotebookHubTab.DIARY -> DiaryView(notebook, actions)
                NotebookHubTab.PHYTO -> PhytoView(notebook, actions)
                NotebookHubTab.EXPENSES -> CostsView(notebook, actions)
                NotebookHubTab.CAMPAIGN -> CampaignView(notebook, state, actions)
            }
        }
    }
}

/** "Finca · Campaña 2026/27" — the context shown before anything is written. */
internal fun contextLine(farm: Farm, campaignName: String?): String = listOf(
    farm.name,
    campaignName?.let { if (it.startsWith("Campaña", ignoreCase = true)) it else "Campaña $it" } ?: "Sin campaña en marcha",
).joinToString(" · ")
