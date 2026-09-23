package com.isivoltpro.maginaolivo.feature.farms

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.isivoltpro.maginaolivo.domain.attachment.AttachmentOwner
import com.isivoltpro.maginaolivo.domain.attachment.AttachmentOwnerType
import com.isivoltpro.maginaolivo.feature.attachments.AttachmentsRoute
import com.isivoltpro.maginaolivo.app.LocalPersistence
import com.isivoltpro.maginaolivo.domain.farm.Farm
import com.isivoltpro.maginaolivo.feature.parcels.FarmParcelsRoute
import com.isivoltpro.maginaolivo.feature.activities.FarmActivitiesRoute
import com.isivoltpro.maginaolivo.feature.campaigns.FarmCampaignsRoute
import com.isivoltpro.maginaolivo.ui.components.MoEmptyState
import com.isivoltpro.maginaolivo.ui.components.MoErrorState
import com.isivoltpro.maginaolivo.ui.components.MoFarmCard
import com.isivoltpro.maginaolivo.ui.components.MoListSkeleton
import com.isivoltpro.maginaolivo.ui.components.MoPhotoCover
import com.isivoltpro.maginaolivo.ui.components.MoPrimaryButton
import com.isivoltpro.maginaolivo.ui.components.MoSecondaryButton
import com.isivoltpro.maginaolivo.ui.components.MoSectionHeader
import com.isivoltpro.maginaolivo.ui.components.MoStatusChip
import com.isivoltpro.maginaolivo.ui.components.MoStatusTone
import com.isivoltpro.maginaolivo.ui.components.MoTextField
import com.isivoltpro.maginaolivo.ui.theme.MoCream
import com.isivoltpro.maginaolivo.ui.theme.MoOliveDark
import com.isivoltpro.maginaolivo.ui.theme.MoSpacing
import com.isivoltpro.maginaolivo.ui.theme.MoTextSecondary
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.isivoltpro.maginaolivo.ui.components.MoDestructiveButton
import com.isivoltpro.maginaolivo.ui.components.MoIcons
import com.isivoltpro.maginaolivo.ui.components.MoMetricGrid
import com.isivoltpro.maginaolivo.ui.components.MoSummaryMetric
import com.isivoltpro.maginaolivo.ui.components.MoTertiaryButton
import com.isivoltpro.maginaolivo.ui.theme.MoInk
import com.isivoltpro.maginaolivo.ui.theme.MoOliveMid
import com.isivoltpro.maginaolivo.ui.theme.MoOutline
import com.isivoltpro.maginaolivo.ui.theme.MoShape
import com.isivoltpro.maginaolivo.ui.theme.MoSurfaceSoft
import com.isivoltpro.maginaolivo.ui.theme.MoWarmWhite
import java.time.LocalDate
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale
import java.util.UUID

@Composable
fun FarmListRoute(
    persistence: LocalPersistence,
    onFarmSelected: (UUID) -> Unit,
    modifier: Modifier = Modifier,
    onMachinery: () -> Unit = {},
) {
    val viewModel: FarmListViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                FarmListViewModel(
                    farmRepository = persistence.farmRepository,
                    workspaceRepository = persistence.workspaceRepository,
                )
            }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val agenda by remember { persistence.activityRepository.observeAgenda() }.collectAsStateWithLifecycle(emptyList())
    val today = remember { LocalDate.now() }
    val nextWork = remember(agenda) {
        agenda.filter { !it.activityDate.isBefore(today) && it.farmId != null }
            .groupBy { it.farmId!! }
            .mapValues { (_, entries) -> entries.minBy { it.activityDate } }
    }
    FarmListScreen(
        state = state,
        onFarmSelected = onFarmSelected,
        onCreate = viewModel::create,
        onRestore = viewModel::restore,
        onRetry = viewModel::retry,
        modifier = modifier,
        onMachinery = onMachinery,
        cover = { farmId ->
            val uri by remember(farmId) { persistence.farmCoverRepository.observeCoverUri(farmId) }.collectAsStateWithLifecycle(null)
            uri
        },
        nextWork = { farmId -> nextWork[farmId]?.let { "Próximo: ${it.description} · ${relativeDay(it.activityDate, today)}" } },
    )
}

private val DAY_MONTH = java.time.format.DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("es-ES"))

internal fun relativeDay(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "hoy"
    today.plusDays(1) -> "mañana"
    else -> date.format(DAY_MONTH)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FarmListScreen(
    state: FarmListUiState,
    onFarmSelected: (UUID) -> Unit,
    onCreate: (FarmDraft) -> Unit,
    onRestore: (UUID) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    onMachinery: () -> Unit = {},
    /** The Farm's cover photo, when one was chosen (UI polish v2). */
    cover: @Composable (UUID) -> String? = { null },
    /** A short line for the next planned work of a Farm, when there is one. */
    nextWork: (UUID) -> String? = { null },
) {
    var editorVisible by rememberSaveable { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(state.message) {
        if (state.message != null) {
            editorVisible = false
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("farms-root"),
        containerColor = MoCream,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding()
                .testTag("farm-list"),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = MoSpacing.screen,
                end = MoSpacing.screen,
                top = MoSpacing.sm,
                bottom = MoSpacing.lg,
            ),
            verticalArrangement = Arrangement.spacedBy(MoSpacing.sm),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Mis fincas",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MoOliveDark,
                    )
                    // Machinery is a shared resource, not a Farm: reached from here, no new root.
                    TextButton(onClick = onMachinery, modifier = Modifier.testTag("open-machinery")) {
                        Text("Maquinaria")
                    }
                }
                Text(
                    text = "Todo tu olivar organizado por fincas y parcelas.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MoTextSecondary,
                )
            }

            if (!state.isLoading && state.error == null) {
                item {
                    FarmTotals(state.farms)
                }
            }

            item {
                MoPrimaryButton(
                    text = "Añadir finca",
                    onClick = { editorVisible = true },
                    enabled = !state.isLoading && !state.isSaving,
                    modifier = Modifier.testTag("add-farm"),
                )
            }

            when {
                state.isLoading -> item { MoListSkeleton() }
                state.error != null -> item {
                    MoErrorState(
                        title = "No pudimos abrir tus fincas",
                        body = state.error,
                        onRetry = onRetry,
                    )
                }
                state.farms.isEmpty() -> item {
                    MoEmptyState(
                        title = "Aún no tienes fincas",
                        body = "Crea tu primera finca. Se guardará en este dispositivo aunque no tengas cobertura.",
                        actionText = "Crear mi primera finca",
                        onAction = { editorVisible = true },
                        icon = MoIcons.Tree,
                    )
                }
                else -> items(
                    items = state.farms,
                    key = { farm -> farm.id },
                ) { farm ->
                    FarmCard(
                        farm = farm,
                        coverUri = cover(farm.id),
                        nextWork = nextWork(farm.id),
                        onClick = { onFarmSelected(farm.id) },
                    )
                }
            }

            if (state.archivedFarms.isNotEmpty()) {
                item { MoSectionHeader(title = "Fincas archivadas") }
                items(
                    items = state.archivedFarms,
                    key = { farm -> "archived-${farm.id}" },
                ) { farm ->
                    Column(verticalArrangement = Arrangement.spacedBy(MoSpacing.xs)) {
                        Text(
                            text = farm.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = MoOliveDark,
                        )
                        MoSecondaryButton(
                            text = "Restaurar finca",
                            onClick = { onRestore(farm.id) },
                            enabled = !state.isSaving,
                        )
                    }
                }
            }

            state.message?.let { message ->
                item {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }

    if (editorVisible) {
        ModalBottomSheet(onDismissRequest = { editorVisible = false }) {
            FarmEditor(
                title = "Nueva finca",
                initial = FarmDraft(),
                isSaving = state.isSaving,
                nameError = state.nameError,
                onSave = onCreate,
                onCancel = { editorVisible = false },
            )
        }
    }
}

@Composable
fun FarmDetailRoute(
    farmId: UUID,
    persistence: LocalPersistence,
    onParcelSelected: (UUID) -> Unit,
    onCampaignSelected: (UUID) -> Unit,
    onActivitySelected: (UUID) -> Unit,
    onArchived: () -> Unit,
    modifier: Modifier = Modifier,
    onImportFromCatastro: (() -> Unit)? = null,
) {
    val viewModel: FarmDetailViewModel = viewModel(
        key = "farm-$farmId",
        factory = viewModelFactory {
            initializer {
                FarmDetailViewModel(
                    farmId = farmId,
                    farmRepository = persistence.farmRepository,
                    farmCoverRepository = persistence.farmCoverRepository,
                )
            }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    FarmDetailScreen(
        state = state,
        onUpdate = viewModel::update,
        onCoverSelected = viewModel::attachCover,
        onArchive = viewModel::archive,
        onArchived = onArchived,
        parcelContent = {
            FarmParcelsRoute(
                farmId = farmId,
                persistence = persistence,
                onParcelSelected = onParcelSelected,
                onImportFromCatastro = onImportFromCatastro,
            )
        },
        campaignContent = {
            FarmCampaignsRoute(
                farmId = farmId,
                persistence = persistence,
                onCampaignSelected = onCampaignSelected,
            )
        },
        activityContent = {
            FarmActivitiesRoute(
                farmId = farmId,
                persistence = persistence,
                onActivitySelected = onActivitySelected,
            )
        },
        attachmentContent = {
            AttachmentsRoute(
                owner = AttachmentOwner(AttachmentOwnerType.FARM, farmId),
                persistence = persistence,
                title = "Documentos de la finca",
            )
        },
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FarmDetailScreen(
    state: FarmDetailUiState,
    onUpdate: (FarmDraft) -> Unit,
    onCoverSelected: (String) -> Unit,
    onArchive: () -> Unit,
    onArchived: () -> Unit,
    parcelContent: @Composable () -> Unit = {},
    campaignContent: @Composable () -> Unit = {},
    activityContent: @Composable () -> Unit = {},
    attachmentContent: @Composable () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var editorVisible by rememberSaveable { mutableStateOf(false) }
    var archiveConfirmation by rememberSaveable { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { onCoverSelected(it.toString()) }
    }
    LaunchedEffect(state.message) {
        if (state.message == "Finca archivada") onArchived()
        if (state.message != null) {
            editorVisible = false
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("farm-detail-root"),
        containerColor = MoCream,
    ) { innerPadding ->
        when {
            state.isLoading -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) { CircularProgressIndicator() }
            state.farm == null -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(MoSpacing.screen),
                verticalArrangement = Arrangement.Center,
            ) {
                MoErrorState(
                    title = "Finca no disponible",
                    body = state.error ?: "No encontramos esta finca en el dispositivo.",
                )
            }
            else -> FarmDetailContent(
                farm = state.farm,
                coverUri = state.coverUri,
                isSaving = state.isSaving,
                onChooseCover = { coverPicker.launch(arrayOf("image/*")) },
                onEdit = { editorVisible = true },
                onArchive = { archiveConfirmation = true },
                parcelContent = parcelContent,
                campaignContent = campaignContent,
                activityContent = activityContent,
                attachmentContent = attachmentContent,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }

    val farm = state.farm
    if (editorVisible && farm != null) {
        ModalBottomSheet(onDismissRequest = { editorVisible = false }) {
            FarmEditor(
                title = "Editar finca",
                initial = farm.toDraft(),
                isSaving = state.isSaving,
                nameError = state.nameError,
                onSave = onUpdate,
                onCancel = { editorVisible = false },
            )
        }
    }

    if (archiveConfirmation) {
        ModalBottomSheet(onDismissRequest = { archiveConfirmation = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(MoSpacing.screen),
                verticalArrangement = Arrangement.spacedBy(MoSpacing.md),
            ) {
                Text("Archivar finca", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "La finca desaparecerá de la lista activa, pero conservará sus datos y podrás restaurarla.",
                    color = MoTextSecondary,
                )
                MoDestructiveButton(
                    text = "Archivar",
                    onClick = {
                        archiveConfirmation = false
                        onArchive()
                    },
                    enabled = !state.isSaving,
                    modifier = Modifier.fillMaxWidth(),
                )
                MoTertiaryButton(
                    text = "Cancelar",
                    onClick = { archiveConfirmation = false },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(MoSpacing.md))
            }
        }
    }
}

/**
 * UI polish v2: a compact Farm detail. Hero, three key figures and quick access to the
 * sections first; the long sections follow, reachable in one tap.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FarmDetailContent(
    farm: Farm,
    coverUri: String?,
    isSaving: Boolean,
    onChooseCover: () -> Unit,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    parcelContent: @Composable () -> Unit,
    campaignContent: @Composable () -> Unit,
    activityContent: @Composable () -> Unit,
    attachmentContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val parcels = remember { BringIntoViewRequester() }
    val campaigns = remember { BringIntoViewRequester() }
    val activities = remember { BringIntoViewRequester() }
    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = MoSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(MoSpacing.sm),
    ) {
        Spacer(Modifier.height(MoSpacing.xxs))
        MoPhotoCover(
            title = farm.name,
            subtitle = farm.locationLabel(),
            imageModel = coverUri,
            modifier = Modifier.height(190.dp),
            badge = {
                MoStatusChip(
                    text = farm.activeCampaignName ?: "Sin campaña activa",
                    tone = if (farm.activeCampaignName == null) MoStatusTone.Neutral else MoStatusTone.Success,
                )
            },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onChooseCover, enabled = !isSaving, modifier = Modifier.testTag("farm-cover-button")) {
                Text(if (coverUri == null) "Añadir foto de portada" else "Cambiar foto")
            }
        }
        MoMetricGrid(
            columns = 3,
            content = listOf(
                { m -> MoSummaryMetric("Superficie", farm.areaLabel(), m, icon = MoIcons.Area) },
                { m -> MoSummaryMetric("Parcelas", farm.parcelCount.toString(), m, icon = MoIcons.Parcels) },
                { m -> MoSummaryMetric("Campaña", farm.activeCampaignName ?: "Ninguna", m, icon = MoIcons.Campaign) },
            ),
        )
        if (farm.totalAreaM2 == null) {
            Text("Añade superficie a las parcelas para calcular rendimientos.", style = MaterialTheme.typography.bodySmall, color = MoTextSecondary)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MoSpacing.xs)) {
            QuickAccess("Parcelas", MoIcons.Parcels, "farm-quick-parcels", Modifier.weight(1f)) { scope.launch { parcels.bringIntoView() } }
            QuickAccess("Campañas", MoIcons.Campaign, "farm-quick-campaigns", Modifier.weight(1f)) { scope.launch { campaigns.bringIntoView() } }
            QuickAccess("Trabajos", MoIcons.Checklist, "farm-quick-activities", Modifier.weight(1f)) { scope.launch { activities.bringIntoView() } }
            // The map arrives with its own phase (land registry + geometry); until then it
            // says so instead of opening sample data.
            QuickAccess("Mapa", MoIcons.Map, "farm-quick-map", Modifier.weight(1f), enabled = false, note = "Pronto") {}
        }
        farm.description?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MoTextSecondary) }
        farm.notes?.let {
            MoSectionHeader(title = "Notas")
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MoTextSecondary)
        }
        Column(Modifier.bringIntoViewRequester(parcels), verticalArrangement = Arrangement.spacedBy(MoSpacing.xs)) { parcelContent() }
        Column(Modifier.bringIntoViewRequester(campaigns), verticalArrangement = Arrangement.spacedBy(MoSpacing.xs)) { campaignContent() }
        Column(Modifier.bringIntoViewRequester(activities), verticalArrangement = Arrangement.spacedBy(MoSpacing.xs)) { activityContent() }
        attachmentContent()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MoSpacing.xs)) {
            MoSecondaryButton(
                text = "Editar finca",
                onClick = onEdit,
                enabled = !isSaving,
                modifier = Modifier.weight(1f),
            )
            MoDestructiveButton(
                text = "Archivar finca",
                onClick = onArchive,
                enabled = !isSaving,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(MoSpacing.lg))
    }
}

@Composable
private fun QuickAccess(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tag: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    note: String? = null,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier
            .heightIn(min = 64.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { if (!enabled && note != null) contentDescription = "$label, disponible próximamente" }
            .testTag(tag),
        shape = MoShape.card,
        color = if (enabled) MoWarmWhite else MoSurfaceSoft,
        border = BorderStroke(1.dp, MoOutline),
    ) {
        Column(
            Modifier.padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Icon(icon, contentDescription = null, tint = if (enabled) MoOliveMid else MoTextSecondary, modifier = Modifier.size(22.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = if (enabled) MoInk else MoTextSecondary)
            if (note != null) Text(note, style = MaterialTheme.typography.labelSmall, color = MoTextSecondary)
        }
    }
}

@Composable
private fun FarmTotals(farms: List<Farm>) {
    val knownArea = farms.mapNotNull { it.totalAreaM2 }.sum().takeIf { farms.any { farm -> farm.totalAreaM2 != null } }
    Column(verticalArrangement = Arrangement.spacedBy(MoSpacing.xxs)) {
        MoMetricGrid(
            columns = 3,
            content = listOf(
                { m -> MoSummaryMetric("Fincas", farms.size.toString(), m, icon = MoIcons.Tree) },
                { m -> MoSummaryMetric("Parcelas", farms.sumOf { it.parcelCount }.toString(), m, icon = MoIcons.Parcels) },
                { m -> MoSummaryMetric("Superficie", knownArea?.let(::formatArea) ?: "—", m, icon = MoIcons.Area) },
            ),
        )
        if (knownArea == null && farms.isNotEmpty()) {
            Text(
                "Añade superficie a tus parcelas para calcular rendimientos.",
                style = MaterialTheme.typography.bodySmall,
                color = MoTextSecondary,
            )
        }
    }
}

@Composable
private fun FarmCard(
    farm: Farm,
    coverUri: String?,
    nextWork: String?,
    onClick: () -> Unit,
) {
    MoFarmCard(
        name = farm.name,
        municipality = farm.locationLabel(),
        area = farm.totalAreaM2?.let(::formatArea) ?: "Añade superficie",
        parcels = farm.parcelCount.toString(),
        campaignStatus = farm.activeCampaignName ?: "Sin campaña activa",
        modifier = Modifier
            .fillMaxWidth()
            .testTag("farm-${farm.id}"),
        onClick = onClick,
        imageModel = coverUri,
        nextWork = nextWork,
        campaignActive = farm.activeCampaignName != null,
        artworkSeed = farm.id.hashCode(),
    )
}

@Composable
private fun FarmEditor(
    title: String,
    initial: FarmDraft,
    isSaving: Boolean,
    nameError: String?,
    onSave: (FarmDraft) -> Unit,
    onCancel: () -> Unit,
) {
    var name by rememberSaveable(initial.name) { mutableStateOf(initial.name) }
    var municipality by rememberSaveable(initial.municipality) { mutableStateOf(initial.municipality) }
    var province by rememberSaveable(initial.province) { mutableStateOf(initial.province) }
    var description by rememberSaveable(initial.description) { mutableStateOf(initial.description) }
    var notes by rememberSaveable(initial.notes) { mutableStateOf(initial.notes) }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = MoSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(MoSpacing.sm),
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall, color = MoOliveDark)
        Text(
            "Se guardará primero en este dispositivo.",
            style = MaterialTheme.typography.bodyMedium,
            color = MoTextSecondary,
        )
        MoTextField(
            value = name,
            onValueChange = { name = it },
            label = "Nombre de la finca",
            isError = nameError != null,
            supportingText = nameError,
            enabled = !isSaving,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("farm-name"),
        )
        MoTextField(
            value = municipality,
            onValueChange = { municipality = it },
            label = "Municipio (opcional)",
            enabled = !isSaving,
            modifier = Modifier.fillMaxWidth(),
        )
        MoTextField(
            value = province,
            onValueChange = { province = it },
            label = "Provincia (opcional)",
            enabled = !isSaving,
            modifier = Modifier.fillMaxWidth(),
        )
        MoTextField(
            value = description,
            onValueChange = { description = it },
            label = "Descripción (opcional)",
            singleLine = false,
            enabled = !isSaving,
            modifier = Modifier.fillMaxWidth(),
        )
        MoTextField(
            value = notes,
            onValueChange = { notes = it },
            label = "Notas (opcional)",
            singleLine = false,
            enabled = !isSaving,
            modifier = Modifier.fillMaxWidth(),
        )
        MoPrimaryButton(
            text = if (isSaving) "Guardando…" else "Guardar finca",
            onClick = {
                focusManager.clearFocus(force = true)
                keyboardController?.hide()
                onSave(
                    initial.copy(
                        name = name,
                        municipality = municipality,
                        province = province,
                        description = description,
                        notes = notes,
                    ),
                )
            },
            enabled = !isSaving,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("save-farm"),
        )
        MoTertiaryButton(
            text = "Cancelar",
            onClick = onCancel,
            enabled = !isSaving,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(MoSpacing.lg))
    }
}

private fun Farm.toDraft() = FarmDraft(
    name = name,
    description = description.orEmpty(),
    municipality = municipality.orEmpty(),
    province = province.orEmpty(),
    notes = notes.orEmpty(),
    coverDocumentId = coverDocumentId,
)

private fun Farm.locationLabel(): String = listOfNotNull(municipality, province)
    .filter { it.isNotBlank() }
    .joinToString(", ")
    .ifBlank { "Completa la ubicación para ver la finca en el mapa" }

private fun Farm.areaLabel(): String = totalAreaM2?.let(::formatArea) ?: "—"

private fun formatArea(areaM2: Double): String {
    val formatter = NumberFormat.getNumberInstance(Locale.forLanguageTag("es-ES")).apply {
        minimumFractionDigits = 0
        maximumFractionDigits = 2
    }
    return "${formatter.format(areaM2 / 10_000.0)} ha"
}
