package com.isivoltpro.maginaolivo.feature.parcels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.isivoltpro.maginaolivo.app.LocalPersistence
import com.isivoltpro.maginaolivo.domain.parcel.Parcel
import com.isivoltpro.maginaolivo.domain.parcel.ParcelSource
import com.isivoltpro.maginaolivo.ui.components.MoEmptyState
import com.isivoltpro.maginaolivo.ui.components.MoErrorState
import com.isivoltpro.maginaolivo.ui.components.MoMetricCard
import com.isivoltpro.maginaolivo.ui.components.MoParcelRow
import com.isivoltpro.maginaolivo.ui.components.MoPrimaryButton
import com.isivoltpro.maginaolivo.ui.components.MoSecondaryButton
import com.isivoltpro.maginaolivo.ui.components.MoSectionHeader
import com.isivoltpro.maginaolivo.ui.components.MoStatusChip
import com.isivoltpro.maginaolivo.ui.components.MoStatusTone
import com.isivoltpro.maginaolivo.ui.components.MoTextField
import com.isivoltpro.maginaolivo.ui.theme.MoCream
import com.isivoltpro.maginaolivo.ui.theme.MoSpacing
import com.isivoltpro.maginaolivo.ui.theme.MoTextSecondary
import java.text.NumberFormat
import java.util.Locale
import java.util.UUID

@Composable
fun FarmParcelsRoute(
    farmId: UUID,
    persistence: LocalPersistence,
    onParcelSelected: (UUID) -> Unit,
) {
    val viewModel: FarmParcelsViewModel = viewModel(
        key = "farm-parcels-$farmId",
        factory = viewModelFactory {
            initializer { FarmParcelsViewModel(farmId, persistence.parcelRepository) }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    FarmParcelsSection(
        state = state,
        onParcelSelected = onParcelSelected,
        onCreate = viewModel::create,
        onRestore = viewModel::restore,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FarmParcelsSection(
    state: FarmParcelsUiState,
    onParcelSelected: (UUID) -> Unit,
    onCreate: (ParcelDraft) -> Unit,
    onRestore: (UUID) -> Unit,
) {
    var editorVisible by rememberSaveable { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(state.message) {
        if (state.message == "Parcela guardada en este dispositivo") {
            editorVisible = false
            focusManager.clearFocus(force = true)
            keyboard?.hide()
        }
    }
    MoSectionHeader(
        title = "Parcelas",
        action = { TextButton(onClick = { editorVisible = true }) { Text("Añadir") } },
    )
    when {
        state.isLoading -> CircularProgressIndicator()
        state.active.isEmpty() -> MoEmptyState(
            title = "Aún no hay parcelas",
            body = "Añade una parcela manualmente. Podrás completar su geometría y Catastro más adelante.",
        )
        else -> state.active.forEach { parcel ->
            MoParcelRow(
                name = parcel.displayName,
                area = parcel.areaLabel(),
                variety = parcel.municipality ?: "Municipio sin registrar",
                onClick = { onParcelSelected(parcel.id) },
                modifier = Modifier.testTag("parcel-${parcel.id}"),
            )
        }
    }
    if (state.archived.isNotEmpty()) {
        MoSectionHeader(title = "Archivadas")
        state.archived.forEach { parcel ->
            Column(verticalArrangement = Arrangement.spacedBy(MoSpacing.xs)) {
                Text(parcel.displayName, style = MaterialTheme.typography.titleMedium)
                MoSecondaryButton(
                    text = "Restaurar parcela",
                    onClick = { onRestore(parcel.id) },
                    enabled = !state.isSaving,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    if (editorVisible) {
        ModalBottomSheet(onDismissRequest = { editorVisible = false }) {
            ParcelEditor(
                title = "Nueva parcela",
                initial = ParcelDraft(),
                isSaving = state.isSaving,
                nameError = state.nameError,
                areaError = state.areaError,
                onSave = onCreate,
                onCancel = { editorVisible = false },
            )
        }
    }
}

@Composable
fun ParcelDetailRoute(
    parcelId: UUID,
    farmId: UUID,
    persistence: LocalPersistence,
    onArchived: () -> Unit,
) {
    val viewModel: ParcelDetailViewModel = viewModel(
        key = "parcel-$parcelId",
        factory = viewModelFactory {
            initializer { ParcelDetailViewModel(parcelId, farmId, persistence.parcelRepository) }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    ParcelDetailScreen(
        state = state,
        onUpdate = viewModel::update,
        onArchive = viewModel::archive,
        onArchived = onArchived,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParcelDetailScreen(
    state: ParcelDetailUiState,
    onUpdate: (ParcelDraft) -> Unit,
    onArchive: () -> Unit,
    onArchived: () -> Unit,
) {
    var editorVisible by rememberSaveable { mutableStateOf(false) }
    var archiveConfirmation by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.message) {
        if (state.message == "Parcela archivada") onArchived()
        if (state.message != null) editorVisible = false
    }
    Scaffold(
        modifier = Modifier.fillMaxSize().testTag("parcel-detail-root"),
        containerColor = MoCream,
    ) { padding ->
        when {
            state.isLoading -> Column(
                Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) { CircularProgressIndicator() }
            state.parcel == null -> Column(
                Modifier.fillMaxSize().padding(padding).padding(MoSpacing.screen),
                verticalArrangement = Arrangement.Center,
            ) { MoErrorState("Parcela no disponible", state.error ?: "No se encuentra en el dispositivo.") }
            else -> ParcelDetailContent(
                parcel = state.parcel,
                onEdit = { editorVisible = true },
                onArchive = { archiveConfirmation = true },
                modifier = Modifier.padding(padding),
            )
        }
    }
    state.parcel?.let { parcel ->
        if (editorVisible) ModalBottomSheet(onDismissRequest = { editorVisible = false }) {
            ParcelEditor(
                title = "Editar parcela",
                initial = parcel.toDraft(),
                isSaving = state.isSaving,
                nameError = state.nameError,
                areaError = state.areaError,
                onSave = onUpdate,
                onCancel = { editorVisible = false },
            )
        }
    }
    if (archiveConfirmation) ModalBottomSheet(onDismissRequest = { archiveConfirmation = false }) {
        Column(Modifier.fillMaxWidth().padding(MoSpacing.screen), verticalArrangement = Arrangement.spacedBy(MoSpacing.md)) {
            Text("Archivar parcela", style = MaterialTheme.typography.headlineSmall)
            Text("Se conservarán sus datos y el histórico de pertenencia a la finca.", color = MoTextSecondary)
            MoPrimaryButton("Archivar", { archiveConfirmation = false; onArchive() }, Modifier.fillMaxWidth())
            MoSecondaryButton("Cancelar", { archiveConfirmation = false }, Modifier.fillMaxWidth())
            Spacer(Modifier.height(MoSpacing.md))
        }
    }
}

@Composable
private fun ParcelDetailContent(parcel: Parcel, onEdit: () -> Unit, onArchive: () -> Unit, modifier: Modifier) {
    Column(
        modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(MoSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(MoSpacing.md),
    ) {
        Text(parcel.displayName, style = MaterialTheme.typography.headlineMedium)
        MoStatusChip(
            text = if (parcel.source == ParcelSource.CATASTRO) "Catastro verificado" else "Entrada manual",
            tone = if (parcel.source == ParcelSource.CATASTRO) MoStatusTone.Success else MoStatusTone.Neutral,
        )
        MoMetricCard("Superficie gestionada", parcel.areaLabel(), Modifier.fillMaxWidth())
        ParcelValue("Referencia catastral", parcel.cadastralReference)
        ParcelValue("Municipio", parcel.municipality)
        ParcelValue("Polígono", parcel.cadastralPolygon)
        ParcelValue("Parcela", parcel.cadastralParcel)
        ParcelValue("Geometría", if (parcel.geometryGeoJson == null) null else "Polígono guardado")
        ParcelValue("Notas", parcel.notes)
        MoSecondaryButton("Editar parcela", onEdit, Modifier.fillMaxWidth())
        MoSecondaryButton("Archivar parcela", onArchive, Modifier.fillMaxWidth())
        Spacer(Modifier.height(MoSpacing.xl))
    }
}

@Composable
private fun ParcelValue(label: String, value: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(MoSpacing.xs)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MoTextSecondary)
        Text(value ?: "Sin registrar", style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ParcelEditor(
    title: String,
    initial: ParcelDraft,
    isSaving: Boolean,
    nameError: String?,
    areaError: String?,
    onSave: (ParcelDraft) -> Unit,
    onCancel: () -> Unit,
) {
    var draft by remember(initial) { mutableStateOf(initial) }
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(MoSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(MoSpacing.sm),
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        MoTextField(draft.displayName, { draft = draft.copy(displayName = it) }, "Alias *", Modifier.fillMaxWidth().testTag("parcel-name"), isError = nameError != null, supportingText = nameError)
        MoTextField(draft.managedAreaHectares, { draft = draft.copy(managedAreaHectares = it) }, "Superficie gestionada (ha)", Modifier.fillMaxWidth(), isError = areaError != null, supportingText = areaError)
        MoTextField(draft.cadastralReference, { draft = draft.copy(cadastralReference = it) }, "Referencia catastral", Modifier.fillMaxWidth())
        MoTextField(draft.cadastralPolygon, { draft = draft.copy(cadastralPolygon = it) }, "Polígono", Modifier.fillMaxWidth())
        MoTextField(draft.cadastralParcel, { draft = draft.copy(cadastralParcel = it) }, "Parcela", Modifier.fillMaxWidth())
        MoTextField(draft.municipality, { draft = draft.copy(municipality = it) }, "Municipio", Modifier.fillMaxWidth())
        MoTextField(draft.province, { draft = draft.copy(province = it) }, "Provincia", Modifier.fillMaxWidth())
        MoTextField(draft.notes, { draft = draft.copy(notes = it) }, "Notas", Modifier.fillMaxWidth(), singleLine = false)
        Text("Los datos escritos aquí se guardan como entrada manual; la app no los presenta como verificados por Catastro.", color = MoTextSecondary)
        MoPrimaryButton("Guardar parcela", { onSave(draft) }, Modifier.fillMaxWidth().testTag("save-parcel"), enabled = !isSaving)
        MoSecondaryButton("Cancelar", onCancel, Modifier.fillMaxWidth(), enabled = !isSaving)
        Spacer(Modifier.height(MoSpacing.lg))
    }
}

private fun Parcel.toDraft() = ParcelDraft(
    displayName = displayName,
    cadastralReference = cadastralReference.orEmpty(),
    cadastralPolygon = cadastralPolygon.orEmpty(),
    cadastralParcel = cadastralParcel.orEmpty(),
    municipality = municipality.orEmpty(),
    province = province.orEmpty(),
    managedAreaHectares = managedAreaM2?.div(10_000)?.let { NumberFormat.getNumberInstance(Locale.forLanguageTag("es-ES")).format(it) }.orEmpty(),
    notes = notes.orEmpty(),
    geometryGeoJson = geometryGeoJson,
    cadastralAreaM2 = cadastralAreaM2,
)

private fun Parcel.areaLabel(): String = managedAreaM2?.let {
    "${NumberFormat.getNumberInstance(Locale.forLanguageTag("es-ES")).format(it / 10_000)} ha"
} ?: "Sin registrar"
