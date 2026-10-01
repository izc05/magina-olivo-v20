package com.isivoltpro.maginaolivo.feature.notebook

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.isivoltpro.maginaolivo.app.ActiveFarmStore
import com.isivoltpro.maginaolivo.app.LocalPersistence
import com.isivoltpro.maginaolivo.data.local.model.isRunning
import com.isivoltpro.maginaolivo.domain.farm.Farm
import com.isivoltpro.maginaolivo.feature.activities.RegisterActivityViewModel
import com.isivoltpro.maginaolivo.ui.components.MoEmptyState
import com.isivoltpro.maginaolivo.ui.components.MoErrorState
import com.isivoltpro.maginaolivo.ui.components.MoIconTone
import com.isivoltpro.maginaolivo.ui.components.MoIcons
import com.isivoltpro.maginaolivo.ui.components.MoSecondaryButton
import com.isivoltpro.maginaolivo.ui.components.MoTertiaryButton
import com.isivoltpro.maginaolivo.ui.theme.MoCream
import com.isivoltpro.maginaolivo.ui.theme.MoInk
import com.isivoltpro.maginaolivo.ui.theme.MoOliveDark
import com.isivoltpro.maginaolivo.ui.theme.MoOutline
import com.isivoltpro.maginaolivo.ui.theme.MoShape
import com.isivoltpro.maginaolivo.ui.theme.MoSpacing
import com.isivoltpro.maginaolivo.ui.theme.MoTextSecondary
import com.isivoltpro.maginaolivo.ui.theme.MoWarmWhite
import java.util.UUID

/**
 * Issue #246 §4B, CR-011 §5: the six daily actions, shown directly — no second menu repeats
 * them. Documents live inside their record (a ticket in its Gasto or Pesada) and machinery use
 * inside its work or recolección day; «Mis máquinas» is in Perfil.
 */
enum class NotebookQuickAction(val label: String, val tag: String, val description: String) {
    WORK("Trabajo", "notebook-quick-work", "Poda, abonado, labores del suelo…"),
    IRRIGATION("Riego", "notebook-quick-irrigation", "Horas, m³ y sector"),
    TREATMENT("Tratamiento", "notebook-quick-treatment", "Producto, dosis y motivo"),
    WEIGHING("Pesada", "notebook-quick-weighing", "Kilos pesados en la cooperativa o almazara"),
    LABOUR("Jornal", "notebook-quick-labour", "Quién trabajó y cuánto"),
    EXPENSE("Gasto", "notebook-quick-expense", "Gasto, ticket o factura"),
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
    NotebookQuickAction.WEIGHING -> MoIcons.Delivery
    NotebookQuickAction.LABOUR -> MoIcons.People
    NotebookQuickAction.EXPENSE -> MoIcons.Euro
}

/** CR-011 §20/§21: each Cuaderno action has its own section colour, set here rather than by glyph. */
internal fun NotebookQuickAction.tone(): MoIconTone = when (this) {
    NotebookQuickAction.WORK -> MoIconTone.GROVE
    NotebookQuickAction.IRRIGATION -> MoIconTone.WATER
    NotebookQuickAction.TREATMENT -> MoIconTone.TREATMENT
    NotebookQuickAction.WEIGHING -> MoIconTone.VALUE
    NotebookQuickAction.LABOUR -> MoIconTone.LABOUR
    NotebookQuickAction.EXPENSE -> MoIconTone.MONEY
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun NotebookRootRoute(
    persistence: LocalPersistence,
    activeFarmStore: ActiveFarmStore,
    /** The quick action, the active Farm, and whether its Campaign is running. */
    onQuickAction: (NotebookQuickAction, UUID, Boolean) -> Unit,
    actionsFor: (UUID) -> NotebookActions,
    onGoToFields: () -> Unit,
    /**
     * CR-011 §14/§18: the Farm to open on, when the Cuaderno is reached from a Farm, a Parcel
     * or Inicio. Applied once; the Farm then stays the active one.
     */
    farmRequest: UUID? = null,
    onFarmRequestHandled: () -> Unit = {},
    /** A Parcel coming from Mi Campo, carried to the work form until the farmer drops it. */
    parcelContext: String? = null,
    onClearParcel: () -> Unit = {},
    /** CR-011 §17: the view to open on (Inicio's campaign card opens Campaña). */
    tabRequest: NotebookHubTab? = null,
    onTabRequestHandled: () -> Unit = {},
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
    LaunchedEffect(farmRequest) {
        val requested = farmRequest ?: return@LaunchedEffect
        chosen = requested.toString()
        activeFarmStore.set(requested)
        onFarmRequestHandled()
    }
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
    var labourCampaign by rememberSaveable { mutableStateOf<String?>(null) }
    NotebookHomeScreen(
        isLoading = farms.isLoading,
        error = farms.error,
        farms = farms.farms,
        activeFarm = activeFarm,
        notebook = notebook?.first,
        actions = activeFarm?.let { actionsFor(it.id).copy(onLabour = { campaignId -> labourCampaign = campaignId.toString() }) },
        onSelectFarm = { id ->
            chosen = id.toString()
            activeFarmStore.set(id)
            // A Parcel belongs to its Farm: another Farm drops it.
            onClearParcel()
        },
        onSelectCampaign = { id -> notebook?.second?.selectCampaign(id) },
        onQuickAction = { action ->
            val farm = activeFarm ?: return@NotebookHomeScreen
            onQuickAction(action, farm.id, notebook?.first?.notebook?.campaign?.status?.isRunning == true)
        },
        onRetry = farmsViewModel::retry,
        onGoToFields = onGoToFields,
        parcelContext = parcelContext,
        onClearParcel = onClearParcel,
        tabRequest = tabRequest,
        onTabRequestHandled = onTabRequestHandled,
    )
    labourCampaign?.let { id ->
        androidx.compose.material3.ModalBottomSheet(onDismissRequest = { labourCampaign = null }) {
            com.isivoltpro.maginaolivo.feature.harvests.LabourPaymentsRoute(UUID.fromString(id), persistence) { labourCampaign = null }
        }
    }
}

/**
 * UX-C (Issue #246), CR-011 — the one Cuaderno: context first (Farm, Campaign and, from Mi
 * Campo, the Parcel), the six actions directly, then the notebook as Diario · Fitosanitario ·
 * Gastos · Campaña. Every view reads the same local records; nothing is copied or totalled
 * twice, and all of it works without signal.
 */
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
    onQuickAction: (NotebookQuickAction) -> Unit,
    onRetry: () -> Unit = {},
    onGoToFields: () -> Unit = {},
    parcelContext: String? = null,
    onClearParcel: () -> Unit = {},
    tabRequest: NotebookHubTab? = null,
    onTabRequestHandled: () -> Unit = {},
) {
    var tab by rememberSaveable { mutableStateOf(NotebookHubTab.DIARY) }
    LaunchedEffect(tabRequest) {
        tabRequest?.let {
            tab = it
            onTabRequestHandled()
        }
    }
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
                activeFarm == null -> MoEmptyState(
                    "Aún no tienes fincas",
                    "Crea una finca en Mi Campo y aquí llevarás su cuaderno del día a día.",
                    actionText = "Ir a Mi Campo",
                    onAction = onGoToFields,
                    icon = MoIcons.Tree,
                    modifier = Modifier.testTag("notebook-root-no-farms"),
                )
                else -> {
                    // Context always visible: which Farm and which Campaign this writes into.
                    val campaign = notebook?.notebook?.campaign
                    Text(
                        contextLine(activeFarm, campaign?.name),
                        style = MaterialTheme.typography.titleMedium,
                        color = MoOliveDark,
                        modifier = Modifier.testTag("notebook-context"),
                    )
                    parcelContext?.let { parcel ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Parcela: $parcel",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MoTextSecondary,
                                modifier = Modifier.weight(1f).testTag("notebook-parcel-context"),
                            )
                            MoTertiaryButton("Toda la finca", onClearParcel, modifier = Modifier.testTag("notebook-parcel-clear"))
                        }
                    }
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
                    QuickActionGrid(onQuickAction)
                    NotebookHub(notebook, actions, tab, { tab = it }, onSelectCampaign)
                }
            }
            Spacer(Modifier.height(MoSpacing.lg))
        }
    }
}

/** CR-011 §4–5: the six actions as large, labelled tiles, three per row; one tap opens the form. */
@Composable
private fun QuickActionGrid(onQuickAction: (NotebookQuickAction) -> Unit) {
    // Device check (build 683): at 360 dp with large text «Tratamiento» broke onto two lines.
    // Three tiles per row only when a tile still fits the longest label at this font scale.
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val fontScale = LocalDensity.current.fontScale
        // Each tile gets the width left after the two gaps between three tiles (Codex #305).
        val tileWidth = (maxWidth - MoSpacing.xs * 2) / 3
        val columns = if (tileWidth >= QUICK_TILE_MIN_WIDTH * fontScale) 3 else 2
        QuickActionRows(columns, onQuickAction)
    }
}

/** The narrowest tile that keeps «Tratamiento» on one line at 100 % text. */
private val QUICK_TILE_MIN_WIDTH = 104.dp

@Composable
private fun QuickActionRows(columns: Int, onQuickAction: (NotebookQuickAction) -> Unit) {
    Column(Modifier.fillMaxWidth().testTag("notebook-quick-actions"), verticalArrangement = Arrangement.spacedBy(MoSpacing.xs)) {
        NotebookQuickAction.entries.chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MoSpacing.xs)) {
                row.forEach { action -> QuickActionTile(action, { onQuickAction(action) }, Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun QuickActionTile(action: NotebookQuickAction, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val icon = action.icon()
    val tone = action.tone()
    Surface(
        modifier = modifier
            .heightIn(min = 88.dp)
            .clip(MoShape.card)
            .clickable(role = Role.Button, onClick = onClick)
            .testTag(action.tag),
        shape = MoShape.card,
        color = MoWarmWhite,
        border = BorderStroke(1.dp, MoOutline),
    ) {
        Column(
            Modifier.padding(vertical = MoSpacing.sm, horizontal = MoSpacing.xs),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(Modifier.size(40.dp).background(tone.container, CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp), tint = tone.tint)
            }
            Text(action.label, style = MaterialTheme.typography.labelLarge, color = MoInk, textAlign = TextAlign.Center, maxLines = 2)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
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
        state.notebook == null || actions == null -> {
            MoEmptyState(
                "Aún no hay campañas",
                "El cuaderno se ordena por campañas. Crea la de este año y aquí verás lo que registres.",
                actionText = actions?.let { "Ir a Campañas" },
                onAction = actions?.onCampaigns,
                icon = MoIcons.Campaign,
                modifier = Modifier.testTag("notebook-no-campaign"),
            )
            // Work can be written down before any Campaign exists; the Farm's list keeps it.
            actions?.let { FarmWorksLink(it) }
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
            }
            // Device checks (builds 680, 683): fixed tabs cut «Fitosanitario», and scrollable tabs
            // hid «Campaña» at 360 dp. Four chips that wrap keep every view visible and whole at
            // any width or font scale.
            FlowRow(
                Modifier.fillMaxWidth().testTag("notebook-views"),
                horizontalArrangement = Arrangement.spacedBy(MoSpacing.xs),
            ) {
                NotebookHubTab.entries.forEach { option ->
                    FilterChip(
                        selected = option == tab,
                        onClick = { onTab(option) },
                        label = { Text(option.label, maxLines = 1) },
                        modifier = Modifier.testTag(option.tag),
                    )
                }
            }
            // UX-E: four views of the same records; nothing is copied or totalled twice.
            when (tab) {
                NotebookHubTab.DIARY -> {
                    DiaryView(notebook, actions)
                    FarmWorksLink(actions)
                }
                NotebookHubTab.PHYTO -> PhytoView(notebook, actions)
                NotebookHubTab.EXPENSES -> CostsView(notebook, actions)
                NotebookHubTab.CAMPAIGN -> CampaignView(notebook, state, actions)
            }
        }
    }
}

/** Every work of the Farm, done and planned, in its own list (all Campaigns). */
@Composable
private fun FarmWorksLink(actions: NotebookActions) {
    MoTertiaryButton("Ver todos los trabajos de la finca", actions.onWorks, modifier = Modifier.fillMaxWidth().testTag("notebook-farm-works"))
}

/** "Finca · Campaña 2026/27" — the context shown before anything is written. */
internal fun contextLine(farm: Farm, campaignName: String?): String = listOf(
    farm.name,
    campaignName?.let { if (it.startsWith("Campaña", ignoreCase = true)) it else "Campaña $it" } ?: "Sin campaña en marcha",
).joinToString(" · ")
