package com.isivoltpro.maginaolivo.feature.notebook

import com.isivoltpro.maginaolivo.domain.notebook.FarmNotebook
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
import androidx.compose.ui.semantics.semantics
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
import com.isivoltpro.maginaolivo.ui.components.MoStatusChip
import com.isivoltpro.maginaolivo.ui.components.MoStatusTone
import com.isivoltpro.maginaolivo.ui.theme.MoInk
import com.isivoltpro.maginaolivo.ui.theme.MoOliveDark
import com.isivoltpro.maginaolivo.ui.theme.MoShape
import com.isivoltpro.maginaolivo.ui.theme.MoSpacing
import java.util.UUID
import com.isivoltpro.maginaolivo.ui.theme.MoSurfaceTokens

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

/**
 * #369: how the one Cuaderno was reached. From the Cuaderno tab it is the general hub and the
 * Farm can be changed there; from a Farm or a Parcel in Mi Campo that Farm is already chosen,
 * so the Cuaderno keeps it and Back returns to it.
 */
enum class NotebookOrigin { ROOT, FARM_CONTEXT, PARCEL_CONTEXT }

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
    /** #369: only the Cuaderno tab offers «Cambiar finca». */
    origin: NotebookOrigin = NotebookOrigin.ROOT,
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
    val activeFarmIds = farms.farms.map { it.id }

    // #427: validate persisted/navigation context only after the active Farm list is known.
    // Never persist an archived/foreign request; repair a stale stored UUID to a real fallback.
    LaunchedEffect(farms.isLoading, farms.error, farmRequest, activeFarmIds) {
        if (farms.isLoading || farms.error != null) return@LaunchedEffect

        val stored = chosen?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        val requested = farmRequest?.takeIf { it in activeFarmIds }
        val resolved = resolveNotebookFarmId(activeFarmIds, requested, stored)
        val invalidRequest = farmRequest != null && requested == null
        val staleStored = stored != null && stored !in activeFarmIds && requested == null

        chosen = resolved?.toString()
        if (resolved != null) activeFarmStore.set(resolved) else activeFarmStore.clear()

        // Parcel context belongs to the Farm that supplied it. Drop it only when that context
        // is proven invalid; a valid Farm/Parcel navigation request keeps the Parcel selected.
        if (invalidRequest || staleStored) onClearParcel()
        if (farmRequest != null) onFarmRequestHandled()
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
            // Codex #368: the Farm runs a campaign if any of its campaigns runs, whichever one the
            // farmer is looking at in the Cuaderno.
            onQuickAction(action, farm.id, notebook?.first?.campaigns.orEmpty().any { it.status.isRunning })
        },
        onRetry = farmsViewModel::retry,
        onGoToFields = onGoToFields,
        parcelContext = parcelContext,
        onClearParcel = onClearParcel,
        tabRequest = tabRequest,
        onTabRequestHandled = onTabRequestHandled,
        canChangeFarm = origin == NotebookOrigin.ROOT,
    )
    labourCampaign?.let { id ->
        androidx.compose.material3.ModalBottomSheet(containerColor = MoSurfaceTokens.cardSurface, onDismissRequest = { labourCampaign = null }) {
            com.isivoltpro.maginaolivo.feature.harvests.LabourPaymentsRoute(UUID.fromString(id), persistence) { labourCampaign = null }
        }
    }
}

/**
 * #427: one deterministic rule for the Cuaderno's operational Farm.
 *
 * A navigation request wins only if it is active; otherwise the last valid choice is kept,
 * then the first active Farm is the fallback. With no active Farms there is no effective id.
 */
internal fun resolveNotebookFarmId(
    activeFarmIds: List<UUID>,
    requested: UUID?,
    stored: UUID?,
): UUID? =
    requested?.takeIf { it in activeFarmIds }
        ?: stored?.takeIf { it in activeFarmIds }
        ?: activeFarmIds.firstOrNull()

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
    /** #369: false when the Cuaderno was opened from a Farm or a Parcel, whose Farm stays fixed. */
    canChangeFarm: Boolean = true,
) {
    var tab by rememberSaveable { mutableStateOf(NotebookHubTab.DIARY) }
    LaunchedEffect(tabRequest) {
        tabRequest?.let {
            tab = it
            onTabRequestHandled()
        }
    }
    var choosingFarm by remember { mutableStateOf(false) }
    // #350/#378: Jornal opens the recogida day with a running campaign and the Farm's own labour
    // («Jornal fuera de campaña») without one. Until the campaign is known, it waits: which of
    // the two it is must never be guessed.
    val onAction: (NotebookQuickAction) -> Unit = { action ->
        // Codex #368: a failed load is not «no campaign»; Jornal waits for a known state.
        if (action != NotebookQuickAction.LABOUR || (notebook != null && !notebook.isLoading && notebook.error == null)) onQuickAction(action)
    }
    Scaffold(
        Modifier.fillMaxSize().testTag("notebook-root"),
        containerColor = MoSurfaceTokens.appBackground,
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
                    // #511: the general Cuaderno context is operational, never the historical
                    // Campaign selected only for consultation in the Campaña tab.
                    val runningCampaign = notebook?.campaigns.orEmpty()
                        .filter { it.status.isRunning }
                        .maxByOrNull { it.startDate }
                    Surface(shape = MoShape.card, color = MoSurfaceTokens.cardSurface, border = BorderStroke(1.dp, MoSurfaceTokens.cardStroke), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(MoSpacing.md), verticalArrangement = Arrangement.spacedBy(MoSpacing.xs)) {
                            Column(
                                Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.testTag("notebook-context"),
                                verticalArrangement = Arrangement.spacedBy(MoSpacing.xs),
                            ) {
                                Text("Finca", style = MaterialTheme.typography.labelLarge, color = MoSurfaceTokens.secondaryText)
                                Text(activeFarm.name, style = MaterialTheme.typography.headlineSmall, color = MoOliveDark)
                                // Status in words as well as colour (accessibility contract).
                                MoStatusChip(
                                    campaignChipText(runningCampaign?.name, runningCampaign?.status),
                                    tone = if (runningCampaign != null) MoStatusTone.Success else MoStatusTone.Neutral,
                                    modifier = Modifier.testTag("notebook-campaign-chip"),
                                )
                            }
                            parcelContext?.let { parcel ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "Parcela: $parcel",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MoSurfaceTokens.secondaryText,
                                        modifier = Modifier.weight(1f).testTag("notebook-parcel-context"),
                                    )
                                    MoTertiaryButton("Toda la finca", onClearParcel, modifier = Modifier.testTag("notebook-parcel-clear"))
                                }
                            }
                            if (canChangeFarm && farms.size > 1) {
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
                                    // A secondary action: it changes the context, it is not the context.
                                    MoTertiaryButton("Cambiar finca", { choosingFarm = true }, Modifier.testTag("notebook-change-farm"))
                                }
                            }
                        }
                    }
                    Text("¿Qué has hecho hoy?", style = MaterialTheme.typography.bodyLarge, color = MoSurfaceTokens.secondaryText)
                    QuickActionGrid(onAction)
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
        color = MoSurfaceTokens.cardSurface,
        border = BorderStroke(1.dp, MoSurfaceTokens.cardStroke),
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
        actions == null -> Unit
        else -> {
            // #417: Diario, Fitosanitario and Gastos are the Farm's, with or without a Campaign;
            // only the Campaña view needs one.
            val farmNotebook = state.farmNotebook ?: state.notebook?.let { FarmNotebook.of(it) } ?: FarmNotebook.EMPTY
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
                        border = BorderStroke(1.dp, if (option == tab) MaterialTheme.colorScheme.primary else MoSurfaceTokens.cardStroke),
                        onClick = { onTab(option) },
                        label = { Text(option.label, maxLines = 1) },
                        modifier = Modifier.testTag(option.tag),
                    )
                }
            }
            // UX-E: four views of the same records; nothing is copied or totalled twice.
            when (tab) {
                NotebookHubTab.DIARY -> {
                    DiaryView(farmNotebook, actions)
                    FarmWorksLink(actions)
                }
                NotebookHubTab.PHYTO -> PhytoView(farmNotebook, actions)
                NotebookHubTab.EXPENSES -> {
                    val runningCampaign = state.campaigns
                        .filter { it.status.isRunning }
                        .maxByOrNull { it.startDate }
                    FarmCostsView(farmNotebook, runningCampaign, actions)
                }
                NotebookHubTab.CAMPAIGN -> {
                    val notebook = state.notebook
                    if (notebook == null) {
                        MoEmptyState(
                            "Aún no hay campañas",
                            "La campaña es la recogida: pesadas, días y jornales. El resto del año ya se anota en Diario.",
                            actionText = "Ir a Campañas",
                            onAction = actions.onCampaigns,
                            icon = MoIcons.Campaign,
                            modifier = Modifier.testTag("notebook-no-campaign"),
                        )
                    } else {
                        if (state.campaigns.size > 1) {
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(MoSpacing.xs)) {
                                state.campaigns.sortedByDescending { it.startDate }.forEach { campaign ->
                                    FilterChip(
                                        selected = campaign.id == state.selectedCampaignId,
                                        border = BorderStroke(1.dp, if (campaign.id == state.selectedCampaignId) MaterialTheme.colorScheme.primary else MoSurfaceTokens.cardStroke),
                                        onClick = { onSelectCampaign(campaign.id) },
                                        label = { Text(campaign.name) },
                                        modifier = Modifier.testTag("notebook-campaign"),
                                    )
                                }
                            }
                        }
                        if (!notebook.campaign.status.isRunning) {
                            Text(
                                "Viendo ${campaignChipText(notebook.campaign.name, notebook.campaign.status)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MoSurfaceTokens.secondaryText,
                                modifier = Modifier.testTag("notebook-selected-campaign-context"),
                            )
                        }
                        CampaignView(notebook, state, actions, onSelectCampaign)
                    }
                }
            }
        }
    }
}

/** Every work of the Farm, done and planned, in its own list (all Campaigns). */
@Composable
private fun FarmWorksLink(actions: NotebookActions) {
    MoTertiaryButton("Ver todos los trabajos de la finca", actions.onWorks, modifier = Modifier.fillMaxWidth().testTag("notebook-farm-works"))
}

/** «Campaña 2026/27 · En marcha»: the campaign and its state in words, never by colour alone. */
internal fun campaignChipText(campaignName: String?, status: com.isivoltpro.maginaolivo.data.local.model.CampaignStatus?): String {
    if (campaignName == null) return campaignLabel(null)
    val state = when (status) {
        com.isivoltpro.maginaolivo.data.local.model.CampaignStatus.ACTIVE -> "En marcha"
        com.isivoltpro.maginaolivo.data.local.model.CampaignStatus.HARVEST -> "En recolección"
        com.isivoltpro.maginaolivo.data.local.model.CampaignStatus.PREPARATION -> "En preparación"
        com.isivoltpro.maginaolivo.data.local.model.CampaignStatus.CLOSED -> "Cerrada"
        null -> null
    }
    return listOfNotNull(campaignLabel(campaignName), state).joinToString(" · ")
}

/** «Campaña 2026/27» whether the farmer typed the word or not; «Sin campaña en marcha» without one. */
internal fun campaignLabel(campaignName: String?): String =
    campaignName?.let { if (it.startsWith("Campaña", ignoreCase = true)) it else "Campaña $it" } ?: "Sin campaña en marcha"
