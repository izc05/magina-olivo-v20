package com.isivoltpro.maginaolivo.feature.farms

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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.isivoltpro.maginaolivo.app.LocalPersistence
import com.isivoltpro.maginaolivo.domain.farm.Farm
import com.isivoltpro.maginaolivo.ui.components.MoEmptyState
import com.isivoltpro.maginaolivo.ui.components.MoErrorState
import com.isivoltpro.maginaolivo.ui.components.MoFarmCard
import com.isivoltpro.maginaolivo.ui.components.MoListSkeleton
import com.isivoltpro.maginaolivo.ui.components.MoMetricCard
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
import java.text.NumberFormat
import java.util.Locale
import java.util.UUID

@Composable
fun FarmListRoute(
    persistence: LocalPersistence,
    onFarmSelected: (UUID) -> Unit,
    modifier: Modifier = Modifier,
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
    FarmListScreen(
        state = state,
        onFarmSelected = onFarmSelected,
        onCreate = viewModel::create,
        onRestore = viewModel::restore,
        onRetry = viewModel::retry,
        modifier = modifier,
    )
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
) {
    var editorVisible by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.message) {
        if (state.message != null) editorVisible = false
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
                .statusBarsPadding(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = MoSpacing.screen,
                end = MoSpacing.screen,
                top = MoSpacing.lg,
                bottom = MoSpacing.xl,
            ),
            verticalArrangement = Arrangement.spacedBy(MoSpacing.md),
        ) {
            item {
                Text(
                    text = "Mis fincas",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MoOliveDark,
                )
                Text(
                    text = "Todo tu olivar organizado por fincas y parcelas.",
                    style = MaterialTheme.typography.bodyLarge,
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add-farm"),
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
                    )
                }
                else -> items(
                    items = state.farms,
                    key = { farm -> farm.id },
                ) { farm ->
                    FarmCard(farm = farm, onClick = { onFarmSelected(farm.id) })
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
    onArchived: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: FarmDetailViewModel = viewModel(
        key = "farm-$farmId",
        factory = viewModelFactory {
            initializer { FarmDetailViewModel(farmId, persistence.farmRepository) }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    FarmDetailScreen(
        state = state,
        onUpdate = viewModel::update,
        onArchive = viewModel::archive,
        onArchived = onArchived,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FarmDetailScreen(
    state: FarmDetailUiState,
    onUpdate: (FarmDraft) -> Unit,
    onArchive: () -> Unit,
    onArchived: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var editorVisible by rememberSaveable { mutableStateOf(false) }
    var archiveConfirmation by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.message) {
        if (state.message == "Finca archivada") onArchived()
        if (state.message != null) editorVisible = false
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
                isSaving = state.isSaving,
                onEdit = { editorVisible = true },
                onArchive = { archiveConfirmation = true },
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
                MoPrimaryButton(
                    text = "Archivar",
                    onClick = {
                        archiveConfirmation = false
                        onArchive()
                    },
                    enabled = !state.isSaving,
                    modifier = Modifier.fillMaxWidth(),
                )
                MoSecondaryButton(
                    text = "Cancelar",
                    onClick = { archiveConfirmation = false },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(MoSpacing.md))
            }
        }
    }
}

@Composable
private fun FarmDetailContent(
    farm: Farm,
    isSaving: Boolean,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = MoSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(MoSpacing.md),
    ) {
        Spacer(Modifier.height(MoSpacing.xs))
        MoPhotoCover(
            title = farm.name,
            subtitle = farm.locationLabel(),
            modifier = Modifier.height(260.dp),
            badge = {
                MoStatusChip(
                    text = farm.activeCampaignName ?: "Sin campaña",
                    tone = if (farm.activeCampaignName == null) MoStatusTone.Neutral else MoStatusTone.Success,
                )
            },
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MoSpacing.sm),
        ) {
            MoMetricCard(
                label = "Superficie",
                value = farm.areaLabel(),
                modifier = Modifier.weight(1f),
            )
            MoMetricCard(
                label = "Parcelas",
                value = farm.parcelCount.toString(),
                modifier = Modifier.weight(1f),
            )
        }
        farm.description?.let { Text(it, color = MoTextSecondary) }
        farm.notes?.let {
            MoSectionHeader(title = "Notas")
            Text(it, style = MaterialTheme.typography.bodyLarge, color = MoTextSecondary)
        }
        MoSectionHeader(title = "Parcelas")
        MoEmptyState(
            title = "Aún no hay parcelas",
            body = "Las parcelas de esta finca se incorporarán en la siguiente fase.",
        )
        MoSecondaryButton(
            text = "Editar finca",
            onClick = onEdit,
            enabled = !isSaving,
            modifier = Modifier.fillMaxWidth(),
        )
        MoSecondaryButton(
            text = "Archivar finca",
            onClick = onArchive,
            enabled = !isSaving,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(MoSpacing.xl))
    }
}

@Composable
private fun FarmTotals(farms: List<Farm>) {
    val knownArea = farms.mapNotNull { it.totalAreaM2 }.sum().takeIf { farms.any { farm -> farm.totalAreaM2 != null } }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MoSpacing.sm),
    ) {
        MoMetricCard(
            label = "Fincas",
            value = farms.size.toString(),
            supportingText = "En este dispositivo",
            modifier = Modifier.weight(1f),
        )
        MoMetricCard(
            label = "Parcelas",
            value = farms.sumOf { it.parcelCount }.toString(),
            supportingText = "Asociaciones activas",
            modifier = Modifier.weight(1f),
        )
    }
    Spacer(Modifier.height(MoSpacing.sm))
    MoMetricCard(
        label = "Superficie conocida",
        value = knownArea?.let(::formatArea) ?: "Sin registrar",
        supportingText = "Derivada de parcelas activas",
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun FarmCard(
    farm: Farm,
    onClick: () -> Unit,
) {
    MoFarmCard(
        name = farm.name,
        municipality = farm.locationLabel(),
        area = farm.areaLabel(),
        parcels = farm.parcelCount.toString(),
        campaignStatus = farm.activeCampaignName ?: "Sin campaña",
        modifier = Modifier
            .fillMaxWidth()
            .testTag("farm-${farm.id}"),
        onClick = onClick,
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
        MoSecondaryButton(
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
    .ifBlank { "Ubicación sin registrar" }

private fun Farm.areaLabel(): String = totalAreaM2?.let(::formatArea) ?: "Sin registrar"

private fun formatArea(areaM2: Double): String {
    val formatter = NumberFormat.getNumberInstance(Locale.forLanguageTag("es-ES")).apply {
        minimumFractionDigits = 0
        maximumFractionDigits = 2
    }
    return "${formatter.format(areaM2 / 10_000.0)} ha"
}
