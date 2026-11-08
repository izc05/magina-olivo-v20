package com.isivoltpro.maginaolivo.feature.parcels

import com.isivoltpro.maginaolivo.ui.theme.MoColors

import com.isivoltpro.maginaolivo.ui.components.OnEachSave
import androidx.compose.ui.unit.dp
import java.time.ZoneId
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.semantics.stateDescription
import com.isivoltpro.maginaolivo.feature.sigpac.SigpacCard
import com.isivoltpro.maginaolivo.feature.sigpac.SigpacUiState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.ui.semantics.heading
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.ui.draw.clip
import com.isivoltpro.maginaolivo.domain.activity.Activity
import com.isivoltpro.maginaolivo.domain.parcel.IrrigationSystem
import com.isivoltpro.maginaolivo.domain.parcel.ParcelAgronomy
import com.isivoltpro.maginaolivo.feature.activities.icon
import com.isivoltpro.maginaolivo.feature.activities.label
import com.isivoltpro.maginaolivo.ui.components.MoCompactListItem
import com.isivoltpro.maginaolivo.ui.components.MoDestructiveButton
import com.isivoltpro.maginaolivo.ui.components.MoLabeledValue
import com.isivoltpro.maginaolivo.ui.components.MoPhotoHeader
import com.isivoltpro.maginaolivo.ui.components.MoSectionCard
import com.isivoltpro.maginaolivo.ui.components.MoStat
import com.isivoltpro.maginaolivo.ui.components.MoStatTile
import com.isivoltpro.maginaolivo.ui.theme.MoShape
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.Surface
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.isivoltpro.maginaolivo.domain.attachment.AttachmentOwner
import com.isivoltpro.maginaolivo.domain.attachment.AttachmentOwnerType
import com.isivoltpro.maginaolivo.feature.attachments.AttachmentsRoute
import com.isivoltpro.maginaolivo.app.LocalPersistence
import com.isivoltpro.maginaolivo.domain.feed.SpanishMunicipalityCatalog
import com.isivoltpro.maginaolivo.ui.components.ProvinceMunicipalityPicker
import com.isivoltpro.maginaolivo.domain.parcel.Parcel
import com.isivoltpro.maginaolivo.domain.parcel.ParcelSource
import com.isivoltpro.maginaolivo.ui.components.MoIcons
import com.isivoltpro.maginaolivo.ui.components.MoTertiaryButton
import com.isivoltpro.maginaolivo.ui.components.MoEmptyState
import com.isivoltpro.maginaolivo.ui.components.MoErrorState
import com.isivoltpro.maginaolivo.ui.components.MoParcelRow
import com.isivoltpro.maginaolivo.ui.components.MoPrimaryButton
import com.isivoltpro.maginaolivo.ui.components.MoSecondaryButton
import com.isivoltpro.maginaolivo.ui.components.MoSectionHeader
import com.isivoltpro.maginaolivo.ui.components.MoStatusChip
import com.isivoltpro.maginaolivo.ui.components.MoStatusTone
import com.isivoltpro.maginaolivo.ui.components.MoTextField
import com.isivoltpro.maginaolivo.ui.theme.MoSpacing
import java.text.NumberFormat
import java.util.Locale
import java.util.UUID
import com.isivoltpro.maginaolivo.ui.theme.MoSurfaceTokens

@Composable
fun FarmParcelsRoute(
    farmId: UUID,
    persistence: LocalPersistence,
    onParcelSelected: (UUID) -> Unit,
    onImportFromCatastro: (() -> Unit)? = null,
    onMap: (() -> Unit)? = null,
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
        onImportFromCatastro = onImportFromCatastro,
        onMap = onMap,
        locatePlace = CATASTRO_PLACE,
    )
}

@Composable
internal fun ParcelAddMethodOptions(
    onManual: () -> Unit,
    onMap: (() -> Unit)?,
    onDismiss: () -> Unit,
    mapTitle: String = "Desde el mapa y Catastro",
    mapSubtitle: String = "Marca una o varias parcelas",
) {
    Column(
        Modifier.fillMaxWidth().padding(top = MoSpacing.xs).padding(bottom = MoSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(MoSpacing.sm),
    ) {
        Text("¿Cómo quieres añadirla?", style = MaterialTheme.typography.titleSmall, color = MoColors.current.primaryText)
        MoCompactListItem(
            title = "A mano",
            subtitle = "Escribe el nombre y los datos que tengas",
            icon = MoIcons.Parcels,
            onClick = onManual,
            modifier = Modifier.testTag("add-parcel-manual"),
        )
        if (onMap != null) {
            MoCompactListItem(
                title = mapTitle,
                subtitle = mapSubtitle,
                icon = MoIcons.Map,
                onClick = onMap,
                modifier = Modifier.testTag("add-parcel-map"),
            )
        }
        MoTertiaryButton("Cancelar", onDismiss, Modifier.fillMaxWidth())
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FarmParcelsSection(
    state: FarmParcelsUiState,
    onParcelSelected: (UUID) -> Unit,
    onCreate: (ParcelDraft) -> Unit,
    onRestore: (UUID) -> Unit,
    onImportFromCatastro: (() -> Unit)? = null,
    onMap: (() -> Unit)? = null,
    locatePlace: PlaceLookup? = null,
) {
    var addOptionsVisible by rememberSaveable { mutableStateOf(false) }
    var editorVisible by rememberSaveable { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(state.savedCount) {
        if (state.savedCount > 0 && state.message == "Parcela guardada en este dispositivo") {
            editorVisible = false
            focusManager.clearFocus(force = true)
            keyboard?.hide()
        }
    }
    MoSectionHeader(
        title = "Parcelas",
        action = {
            TextButton(onClick = { addOptionsVisible = true }, modifier = Modifier.testTag("add-parcel")) { Text("Añadir") }
        },
    )
    if (addOptionsVisible) {
        ParcelAddMethodOptions(
            onManual = { addOptionsVisible = false; editorVisible = true },
            // The chooser closes first, so coming back from the map or Catastro shows the list.
            onMap = (onMap ?: onImportFromCatastro)?.let { open -> { addOptionsVisible = false; open() } },
            onDismiss = { addOptionsVisible = false },
            mapTitle = if (onMap != null) "Desde el mapa y Catastro" else "Buscar en Catastro",
            mapSubtitle = if (onMap != null) "Marca una o varias parcelas" else "Con la referencia catastral",
        )
    }
    when {
        state.isLoading -> CircularProgressIndicator()
        state.active.isEmpty() -> MoEmptyState(
            title = "Aún no hay parcelas",
            body = if (onMap != null || onImportFromCatastro != null) {
                "Añade una parcela a mano o búscala en Catastro."
            } else {
                "Añade una parcela manualmente o consulta Catastro desde Mapa y Catastro en Inicio."
            },
            icon = MoIcons.Parcels,
        )
        else -> state.active.forEach { parcel ->
            MoParcelRow(
                name = parcel.displayName,
                area = listOfNotNull(
                    parcel.areaLabel(),
                    parcel.agronomy.oliveTreeCount?.let { "${grouped(it)} olivos" },
                ).joinToString(" · "),
                variety = listOfNotNull(parcel.agronomy.variety, parcel.municipality).joinToString(" · ")
                    .ifEmpty { "Municipio sin registrar" },
                onClick = { onParcelSelected(parcel.id) },
                modifier = Modifier.testTag("parcel-row"),
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
                oliveError = state.oliveError,
                locatePlace = locatePlace,
            )
        }
    }
}

@Composable
fun ParcelDetailRoute(
    parcelId: UUID,
    persistence: LocalPersistence,
    onArchived: () -> Unit,
    onLocate: ((UUID) -> Unit)? = null,
    /** UX-F (Issue #246 §5): opens Mi Cuaderno → Registrar hoy with this Parcel's Farm and the Parcel. */
    onRegister: ((farmId: UUID, parcelName: String) -> Unit)? = null,
    openEditorInitially: Boolean = false,
    onOpenMap: ((farmId: UUID) -> Unit)? = null,
) {
    val ownerScope = persistence.workspaceRepository.currentOwnerId()?.toString() ?: "default"
    val viewModel: ParcelDetailViewModel = viewModel(
        key = "parcel-$parcelId-$ownerScope",
        factory = viewModelFactory {
            initializer {
                ParcelDetailViewModel(parcelId, persistence.parcelRepository, persistence.activityRepository, persistence.farmRepository)
            }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    ParcelDetailScreen(
        state = state,
        onUpdate = viewModel::update,
        onArchive = viewModel::archive,
        onArchived = onArchived,
        onLocate = onLocate,
        onRegister = onRegister,
        openEditorInitially = openEditorInitially,
        onOpenMap = onOpenMap,
        locatePlace = CATASTRO_PLACE,
        onRecoverLocation = { viewModel.recoverCadastralLocation(CATASTRO_PLACE) },
        onLoadSigpac = viewModel::loadSigpac,
        attachmentContent = {
            AttachmentsRoute(
                owner = AttachmentOwner(AttachmentOwnerType.PARCEL, parcelId),
                persistence = persistence,
                title = "Documentos de la parcela",
            )
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParcelDetailScreen(
    state: ParcelDetailUiState,
    onUpdate: (ParcelDraft) -> Unit,
    onArchive: () -> Unit,
    onArchived: () -> Unit,
    attachmentContent: @Composable () -> Unit = {},
    onLocate: ((UUID) -> Unit)? = null,
    onRegister: ((farmId: UUID, parcelName: String) -> Unit)? = null,
    locatePlace: PlaceLookup? = null,
    onRecoverLocation: (() -> Unit)? = null,
    openEditorInitially: Boolean = false,
    onOpenMap: ((farmId: UUID) -> Unit)? = null,
    onLoadSigpac: (force: Boolean) -> Unit = {},
) {
    var editorVisible by rememberSaveable { mutableStateOf(openEditorInitially) }
    var archiveConfirmation by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.message) { if (state.message == "Parcela archivada") onArchived() }
    OnEachSave(state.saveCount) { editorVisible = false }
    LaunchedEffect(state.parcel?.id) {
        val parcel = state.parcel
        if (parcel?.source == ParcelSource.CATASTRO &&
            (parcel.municipality.isNullOrBlank() || parcel.province.isNullOrBlank())) onRecoverLocation?.invoke()
    }
    Scaffold(
        modifier = Modifier.fillMaxSize().testTag("parcel-detail-root"),
        containerColor = MoSurfaceTokens.appBackground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
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
                farmName = state.farmName,
                activities = state.activities,
                sigpac = state.sigpac,
                onLoadSigpac = onLoadSigpac,
                onEdit = { editorVisible = true },
                onRecoverLocation = onRecoverLocation,
                onArchive = { archiveConfirmation = true },
                attachmentContent = attachmentContent,
                modifier = Modifier.padding(padding),
                // Only a parcel without a boundary is offered; one on a farm, active.
                onLocate = state.parcel.farmId
                    ?.takeIf { state.parcel.geometryGeoJson == null && state.parcel.archivedAt == null }
                    ?.let { farmId -> onLocate?.let { locate -> { locate(farmId) } } },
                // Only an active parcel that belongs to a farm can be written into.
                onRegister = state.parcel.farmId
                    ?.takeIf { state.parcel.archivedAt == null }
                    ?.let { farmId -> onRegister?.let { register -> { register(farmId, state.parcel.displayName) } } },
                onOpenMap = state.parcel.farmId
                    ?.takeIf { state.parcel.geometryGeoJson != null }
                    ?.let { farmId -> onOpenMap?.let { open -> { open(farmId) } } },
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
                oliveError = state.oliveError,
                locatePlace = locatePlace,
                cadastralSource = parcel.source == ParcelSource.CATASTRO,
            )
        }
    }
    if (archiveConfirmation) ModalBottomSheet(onDismissRequest = { archiveConfirmation = false }) {
        Column(Modifier.fillMaxWidth().padding(MoSpacing.screen), verticalArrangement = Arrangement.spacedBy(MoSpacing.md)) {
            Text("Archivar parcela", style = MaterialTheme.typography.headlineSmall)
            Text("Se conservarán sus datos y el histórico de pertenencia a la finca.", color = MoSurfaceTokens.secondaryText)
            MoDestructiveButton("Archivar", { archiveConfirmation = false; onArchive() }, Modifier.fillMaxWidth())
            MoTertiaryButton("Cancelar", { archiveConfirmation = false }, Modifier.fillMaxWidth())
            Spacer(Modifier.height(MoSpacing.md))
        }
    }
}

@Composable
private fun ParcelDetailContent(
    parcel: Parcel,
    farmName: String?,
    activities: List<Activity>,
    sigpac: SigpacUiState,
    onLoadSigpac: (force: Boolean) -> Unit,
    onEdit: () -> Unit,
    onRecoverLocation: (() -> Unit)?,
    onArchive: () -> Unit,
    attachmentContent: @Composable () -> Unit,
    modifier: Modifier,
    onLocate: (() -> Unit)? = null,
    onRegister: (() -> Unit)? = null,
    onOpenMap: (() -> Unit)? = null,
) {
    var tab by rememberSaveable { mutableStateOf(ParcelTab.ACTIVITY) }
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(MoSpacing.sm),
    ) {
        ParcelHeader(parcel, farmName, onOpenMap, onArchive)
        Column(
            Modifier.padding(horizontal = MoSpacing.screen),
            verticalArrangement = Arrangement.spacedBy(MoSpacing.sm),
        ) {
            ParcelIndicators(parcel, onEdit)
            val agronomy = parcel.agronomy
            val incomplete = parcel.managedAreaM2 == null || agronomy.oliveTreeCount == null ||
                agronomy.variety == null || agronomy.irrigationSystem == null
            MoPrimaryButton(if (incomplete) "Completar datos" else "Editar datos de parcela", onEdit,
                Modifier.fillMaxWidth().testTag("parcel-edit"))
            onLocate?.let {
                MoCompactListItem(
                    title = "Ubicar en el mapa",
                    subtitle = "Toca tu parcela y se añaden su referencia y su contorno de Catastro",
                    icon = MoIcons.Map,
                    onClick = it,
                    modifier = Modifier.testTag("parcel-locate"),
                    trailing = { Icon(MoIcons.ChevronRight, contentDescription = null, tint = MoSurfaceTokens.secondaryText) },
                )
            }
            ParcelTabs(tab) { tab = it }
            when (tab) {
                ParcelTab.ACTIVITY -> ParcelActivities(activities, onRegister)
                ParcelTab.DATA -> ParcelDataBlocks(parcel, sigpac, onLoadSigpac, onEdit, onRecoverLocation)
                ParcelTab.DOCUMENTS -> attachmentContent()
            }
            Spacer(Modifier.height(MoSpacing.xl))
        }
    }
}

/** Neutral on purpose: no stock photograph may pass for this parcel. */
@Composable
private fun ParcelHeader(parcel: Parcel, farmName: String?, onOpenMap: (() -> Unit)?, onArchive: () -> Unit) {
    Surface(color = MoSurfaceTokens.cardSurface, modifier = Modifier.fillMaxWidth().testTag("parcel-header")) {
        Row(
            Modifier.fillMaxWidth().padding(start = MoSpacing.screen, end = MoSpacing.xs, top = MoSpacing.sm, bottom = MoSpacing.sm),
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(parcel.displayName, style = MaterialTheme.typography.headlineSmall, color = MoColors.current.primaryText,
                    modifier = Modifier.semantics { heading() })
                val identity = listOfNotNull(parcel.cadastralPolygon?.let { "Pol. ${it.trimStart('0').ifEmpty { "0" }}" },
                    parcel.cadastralParcel?.let { "Parc. ${it.trimStart('0').ifEmpty { "0" }}" }).joinToString(" · ")
                if (identity.isNotEmpty() && identity != parcel.displayName) Text(identity, style = MaterialTheme.typography.bodyMedium, color = MoColors.current.bodyText,
                    modifier = Modifier.testTag("parcel-header-identity"))
                farmName?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MoSurfaceTokens.secondaryText) }
                listOfNotNull(parcel.municipality, parcel.province).joinToString(", ").takeIf { it.isNotEmpty() }?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MoSurfaceTokens.secondaryText)
                }
                Spacer(Modifier.height(MoSpacing.xs))
                MoStatusChip(
                    text = if (parcel.archivedAt == null) "Activa" else "Archivada",
                    tone = if (parcel.archivedAt == null) MoStatusTone.Success else MoStatusTone.Neutral,
                )
            }
            ParcelActionsMenu(parcel, onOpenMap, onArchive)
        }
    }
}

@Composable
private fun ParcelActionsMenu(parcel: Parcel, onOpenMap: (() -> Unit)?, onArchive: () -> Unit) {
    if (onOpenMap == null && parcel.archivedAt != null) return
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.size(48.dp).testTag("parcel-actions")) {
            Icon(MoIcons.More, contentDescription = "Más acciones")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            onOpenMap?.let { openMap ->
                DropdownMenuItem(text = { Text("Ver mapa") }, onClick = { open = false; openMap() },
                    modifier = Modifier.testTag("parcel-open-map"))
            }
            if (parcel.archivedAt == null) DropdownMenuItem(
                text = { Text("Archivar parcela") }, onClick = { open = false; onArchive() },
                modifier = Modifier.testTag("parcel-archive"),
            )
        }
    }
}

/** Four labelled figures. A value the farmer has not given reads «Sin indicar», never another source. */
@Composable
private fun ParcelIndicators(parcel: Parcel, onEdit: () -> Unit) {
    val agronomy = parcel.agronomy
    @Composable
    fun RowScope.tile(label: String, value: String?, icon: androidx.compose.ui.graphics.vector.ImageVector, tag: String) {
        MoStatTile(
            MoStat(label, value ?: "Sin indicar", icon),
            Modifier.weight(1f).clip(MoShape.card)
                .clickable(role = Role.Button, onClickLabel = "Editar", onClick = onEdit).testTag(tag),
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(MoSpacing.xs)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MoSpacing.xs)) {
            tile("Superficie trabajada", parcel.managedAreaM2?.let(::hectares), MoIcons.Area, "parcel-stat-area")
            tile("Olivos", agronomy.oliveTreeCount?.let(::grouped), MoIcons.Olive, "parcel-stat-trees")
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MoSpacing.xs)) {
            tile("Variedad", agronomy.variety, MoIcons.Leaf, "parcel-stat-variety")
            tile("Riego", agronomy.irrigationSystem?.shortLabel(), MoIcons.Drop, "parcel-stat-irrigation")
        }
    }
}

/**
 * Datos in folded sections, so nothing has to be read or filled at once. Only stored values, and
 * each surface and figure keeps its source. SIGPAC is asked only when its section is opened.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ParcelDataBlocks(
    parcel: Parcel,
    sigpac: SigpacUiState,
    onLoadSigpac: (force: Boolean) -> Unit,
    onEdit: () -> Unit,
    onRecoverLocation: (() -> Unit)?,
) {
    val locationPending = parcel.source == ParcelSource.CATASTRO &&
        (parcel.municipality.isNullOrBlank() || parcel.province.isNullOrBlank())
    var summary by rememberSaveable(parcel.id) { mutableStateOf(true) }
    var crop by rememberSaveable(parcel.id) { mutableStateOf(false) }
    var official by rememberSaveable(parcel.id) { mutableStateOf(locationPending) }
    var irrigation by rememberSaveable(parcel.id) { mutableStateOf(false) }
    var extra by rememberSaveable(parcel.id) { mutableStateOf(false) }
    var calculator by rememberSaveable(parcel.id) { mutableStateOf(false) }
    if (calculator) ModalBottomSheet(onDismissRequest = { calculator = false }) {
        IrrigationCalculatorSheet(parcel.agronomy.oliveTreeCount)
    }
    LaunchedEffect(parcel.id, official) { if (official) onLoadSigpac(false) }
    Column(verticalArrangement = Arrangement.spacedBy(MoSpacing.xs)) {
        ParcelSection("Resumen agrícola", "summary", summary, { summary = !summary }) {
            Column(Modifier.semantics(mergeDescendants = true) {}.testTag("parcel-area-hero"), verticalArrangement = Arrangement.spacedBy(MoSpacing.xs)) {
                SurfaceLine("Superficie trabajada", parcel.managedAreaM2?.let(::hectares) ?: "Sin indicar",
                    if (parcel.managedAreaM2 != null) "Introducido por el agricultor" else null)
                SurfaceLine("Superficie catastral", parcel.cadastralAreaM2?.let(::hectares) ?: "Sin registrar",
                    if (parcel.cadastralAreaM2 != null) "Según Catastro" else null)
                if (parcel.managedAreaM2 != null && parcel.cadastralAreaM2 != null && parcel.managedAreaM2 > parcel.cadastralAreaM2 * 1.02) {
                    Text("La superficie trabajada supera la catastral. Revísala.", style = MaterialTheme.typography.bodySmall,
                        color = MoColors.current.warningText, modifier = Modifier.testTag("parcel-area-warning"))
                }
            }
            // Phase 18: the saved boundary, drawn from this phone's copy (works offline).
            parcel.geometryGeoJson?.let { geometry ->
                ParcelBoundaryPreview(parcel.id.toString(), parcel.displayName, geometry)
            }
        }
        ParcelSection("Cultivo y plantación", "crop", crop, { crop = !crop }) {
            ValuePair("Olivos", parcel.agronomy.oliveTreeCount?.let(::grouped), "Variedad", parcel.agronomy.variety)
            MoTertiaryButton("Editar cultivo", onEdit, Modifier.fillMaxWidth().testTag("parcel-crop-edit"))
        }
        ParcelSection("Catastro y SIGPAC", "official", official, { official = !official }) {
            SigpacCard(sigpac, parcel.cadastralReference != null, { onLoadSigpac(true) })
            MoSectionCard(title = "Datos catastrales", icon = MoIcons.Document, modifier = Modifier.testTag("parcel-cadastral")) {
                MoLabeledValue("Referencia catastral", parcel.cadastralReference ?: "Sin registrar")
                ValuePair("Polígono", parcel.cadastralPolygon, "Parcela", parcel.cadastralParcel)
                if (locationPending) {
                    Text("Ubicación pendiente de verificar", color = MoSurfaceTokens.secondaryText)
                    ValuePair("Municipio", parcel.municipality, "Provincia", parcel.province)
                    MoTertiaryButton("Elegir provincia y municipio", onEdit, Modifier.fillMaxWidth())
                    onRecoverLocation?.let { retry ->
                        MoTertiaryButton("Reintentar consulta de Catastro", retry, Modifier.fillMaxWidth())
                    }
                } else {
                    ValuePair("Municipio", parcel.municipality, "Provincia", parcel.province)
                }
            }
            MoSectionCard(title = "Geometría", icon = MoIcons.Map, modifier = Modifier.testTag("parcel-geometry")) {
                ValuePair(
                    "Contorno", if (parcel.geometryGeoJson == null) "Sin contorno" else "Guardado en el teléfono",
                    "Origen", if (parcel.source == ParcelSource.CATASTRO) "Catastro" else "Entrada manual",
                )
                MoLabeledValue("Importada el", parcel.sourceImportedAt?.atZone(ZoneId.systemDefault())?.toLocalDate()?.format(IMPORT_DATE) ?: "Sin registrar")
            }
        }
        ParcelSection("Riego e instalaciones", "irrigation", irrigation, { irrigation = !irrigation }) {
            IrrigationCard(parcel.agronomy, onEdit)
            MoSecondaryButton("Calculadora de riego", { calculator = true }, Modifier.fillMaxWidth().testTag("parcel-irrigation-calculator"))
        }
        ParcelSection("Información adicional", "extra", extra, { extra = !extra }) {
            MoSectionCard(title = "Observaciones", icon = MoIcons.Notebook, modifier = Modifier.testTag("parcel-extra")) {
                Text(
                    parcel.notes?.takeIf { it.isNotBlank() } ?: "Sin notas. Puedes añadirlas al editar la parcela.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (parcel.notes.isNullOrBlank()) MoSurfaceTokens.secondaryText else MoColors.current.bodyText,
                )
            }
        }
    }
}

@Composable
private fun ParcelSection(title: String, key: String, expanded: Boolean, onToggle: () -> Unit, content: @Composable () -> Unit) {
    Surface(shape = MoShape.card, color = MoSurfaceTokens.cardSurface, modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp)
                    .clickable(role = Role.Button, onClick = onToggle)
                    .semantics { stateDescription = if (expanded) "Desplegado" else "Plegado" }
                    .padding(horizontal = MoSpacing.md)
                    .testTag("parcel-section-$key"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleMedium, color = MoColors.current.primaryText)
                Icon(if (expanded) MoIcons.ChevronUp else MoIcons.ChevronDown, contentDescription = null, tint = MoSurfaceTokens.secondaryText)
            }
            if (expanded) Column(
                Modifier.fillMaxWidth().padding(start = MoSpacing.md, end = MoSpacing.md, bottom = MoSpacing.md),
                verticalArrangement = Arrangement.spacedBy(MoSpacing.xs),
            ) { content() }
        }
    }
}

@Composable
private fun SurfaceLine(label: String, value: String, source: String?) {
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MoSurfaceTokens.secondaryText)
        Text(value, style = MaterialTheme.typography.titleLarge, color = MoColors.current.primaryText)
        source?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MoSurfaceTokens.secondaryText) }
    }
}

/** Two labelled values side by side; a missing value reads «Sin registrar», never a guess. */
@Composable
private fun ValuePair(firstLabel: String, first: String?, secondLabel: String, second: String?) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MoSpacing.sm)) {
        MoLabeledValue(firstLabel, first ?: "Sin registrar", Modifier.weight(1f))
        MoLabeledValue(secondLabel, second ?: "Sin registrar", Modifier.weight(1f))
    }
}

private fun hectares(m2: Double): String =
    "${NumberFormat.getNumberInstance(SPANISH).apply { maximumFractionDigits = 2 }.format(m2 / 10_000)} ha"

private enum class ParcelTab(val label: String) { ACTIVITY("Actividad"), DATA("Datos"), DOCUMENTS("Documentos") }

@Composable
private fun ParcelTabs(selected: ParcelTab, onSelect: (ParcelTab) -> Unit) {
    TabRow(
        selectedTabIndex = selected.ordinal,
        containerColor = MoSurfaceTokens.cardSurface,
        contentColor = MoColors.current.primaryText,
        modifier = Modifier.clip(MoShape.card),
    ) {
        ParcelTab.entries.forEach { tab ->
            Tab(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                modifier = Modifier.testTag("parcel-tab-${tab.name.lowercase()}"),
                text = {
                    Text(
                        tab.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (tab == selected) MoColors.current.primaryText else MoSurfaceTokens.secondaryText,
                    )
                },
            )
        }
    }
}

@Composable
private fun IrrigationCard(agronomy: ParcelAgronomy, onEdit: () -> Unit) {
    MoSectionCard(
        title = "Riego",
        icon = MoIcons.Drop,
        modifier = Modifier.testTag("parcel-irrigation"),
    ) {
        val known = agronomy.irrigationSystem != null || agronomy.irrigationNetwork != null ||
            agronomy.irrigationSector != null || agronomy.irrigationDays.isNotEmpty()
        if (!known) {
            Text("Aún no has indicado cómo se riega esta parcela.", style = MaterialTheme.typography.bodyMedium, color = MoSurfaceTokens.secondaryText)
            TextButton(onClick = onEdit) { Text("Añadir datos de riego") }
        } else {
            // Only what the farmer told us: no grid of dashes.
            val facts = listOfNotNull(
                agronomy.irrigationSystem?.label()?.let { "Tipo" to it },
                agronomy.irrigationSector?.let { "Sector" to it },
                agronomy.irrigationNetwork?.let { "Comunidad / red" to it },
                agronomy.irrigationDays.label()?.let { "Días habituales" to it },
            )
            facts.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth()) {
                    pair.forEach { (label, value) -> MoLabeledValue(label, value, Modifier.weight(1f)) }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            if (facts.size < 4) {
                TextButton(onClick = onEdit, modifier = Modifier.testTag("parcel-irrigation-complete")) {
                    Text("Completar riego")
                }
            }
        }
    }
}

@Composable
private fun ParcelActivities(activities: List<Activity>, onRegister: (() -> Unit)?) {
    if (activities.isEmpty()) {
        MoEmptyState(
            "Todavía no has registrado trabajos en esta parcela",
            "Una poda, un riego o un tratamiento aparecerán aquí.",
            icon = MoIcons.History,
            actionText = onRegister?.let { "Registrar actividad" },
            onAction = onRegister,
            actionModifier = Modifier.testTag("parcel-register"),
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(MoSpacing.xs)) {
        onRegister?.let { MoPrimaryButton("Registrar actividad", it, Modifier.fillMaxWidth().testTag("parcel-register")) }
        activities.take(10).forEach { activity ->
            MoCompactListItem(
                title = activity.description,
                subtitle = "${activity.type.label()} · ${activity.activityDate.format(SHORT_DATE)}",
                icon = activity.type.icon(),
                modifier = Modifier.testTag("parcel-activity"),
            )
        }
    }
}

/** Catastro's place for a 14-character reference (null when it cannot say). */
typealias PlaceLookup = suspend (String) -> com.isivoltpro.maginaolivo.domain.registry.RegistryLocation?

private val CATASTRO_PLACE: PlaceLookup = { reference -> com.isivoltpro.maginaolivo.feature.catastro.OfficialCadastreClient().locate(reference) }
private val CADASTRAL_REFERENCE = Regex("[A-Z0-9]{14}")

@Composable
internal fun ParcelEditor(
    title: String,
    initial: ParcelDraft,
    isSaving: Boolean,
    nameError: String?,
    areaError: String?,
    onSave: (ParcelDraft) -> Unit,
    onCancel: () -> Unit,
    oliveError: String? = null,
    /** Catastro's municipality/province for a reference; null where no lookup is offered. */
    locatePlace: PlaceLookup? = null,
    cadastralSource: Boolean = false,
) {
    var draft by remember(initial) { mutableStateOf(initial) }
    var placeStatus by remember(initial) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    var moreOpen by remember(initial) {
        mutableStateOf(
            initial.oliveTreeCount.isNotBlank() || initial.variety.isNotBlank() || initial.irrigationSystem != null ||
                initial.cadastralReference.isNotBlank() || initial.cadastralPolygon.isNotBlank() ||
                // Province is not counted: a new parcel comes with "Jaén" filled in.
                initial.cadastralParcel.isNotBlank() || initial.municipality.isNotBlank() ||
                initial.notes.isNotBlank(),
        )
    }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(MoSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(MoSpacing.sm),
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        MoTextField(draft.displayName, { draft = draft.copy(displayName = it) }, "Alias *", Modifier.fillMaxWidth().testTag("parcel-name"), isError = nameError != null, supportingText = nameError)
        MoTextField(draft.managedAreaHectares, { draft = draft.copy(managedAreaHectares = it) }, "Superficie gestionada (ha)", Modifier.fillMaxWidth(), isError = areaError != null, supportingText = areaError)
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) { moreOpen = !moreOpen }
                .testTag("parcel-more-details"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MoSpacing.xs),
        ) {
            Icon(if (moreOpen || oliveError != null) MoIcons.ChevronDown else MoIcons.ChevronRight, contentDescription = null, tint = MoColors.current.primaryText)
            Column(Modifier.weight(1f)) {
                Text("Más datos del olivar", style = MaterialTheme.typography.titleSmall, color = MoColors.current.primaryText)
                if (!moreOpen && oliveError == null) Text("Olivos, riego y Catastro", style = MaterialTheme.typography.bodySmall, color = MoSurfaceTokens.secondaryText)
            }
        }
        // An error in a folded field opens the fold, so the farmer sees what to fix.
        if (moreOpen || oliveError != null) {
            GroveFields(draft, oliveError) { draft = it }
            MoTextField(draft.cadastralReference, { draft = draft.copy(cadastralReference = it) }, "Referencia catastral", Modifier.fillMaxWidth())
            MoTextField(draft.cadastralPolygon, { draft = draft.copy(cadastralPolygon = it) }, "Polígono", Modifier.fillMaxWidth())
            MoTextField(draft.cadastralParcel, { draft = draft.copy(cadastralParcel = it) }, "Parcela catastral", Modifier.fillMaxWidth())
            // Owner 2026-10-03: with a reference, Catastro can fill municipality and province.
            val reference = draft.cadastralReference.trim().uppercase()
            if (locatePlace != null && CADASTRAL_REFERENCE.matches(reference)) {
                TextButton(
                    onClick = {
                        placeStatus = "Consultando en Catastro…"
                        scope.launch {
                            val place = runCatching { locatePlace(reference) }.getOrNull()
                            // The answer is for this reference only, and it only fills what is still empty:
                            // whatever the farmer wrote (before or while asking) stays.
                            val current = draft
                            when {
                                current.cadastralReference.trim().uppercase() != reference -> placeStatus = null
                                place == null -> placeStatus = "Ubicación pendiente de verificar: selecciónala abajo."
                                else -> {
                                    draft = current.copy(
                                        municipality = current.municipality.ifBlank { place.municipality },
                                        province = current.province.ifBlank { place.province.orEmpty() },
                                    )
                                    placeStatus = if (current.municipality.isBlank()) "Según Catastro · puedes cambiarlo" else "Se mantiene el municipio que escribiste"
                                }
                            }
                        }
                    },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("parcel-locate-place"),
                ) { Text("Completar municipio y provincia desde Catastro") }
            }
            if (cadastralSource) {
                val province = SpanishMunicipalityCatalog.resolveProvince(draft.province)
                val municipality = SpanishMunicipalityCatalog.resolve(draft.municipality, draft.province)
                if (placeStatus != null) Text(placeStatus!!, color = MoSurfaceTokens.secondaryText)
                ProvinceMunicipalityPicker(
                    provinceCode = province?.code,
                    municipality = municipality,
                    onProvinceSelected = { code ->
                        draft = draft.copy(
                            province = SpanishMunicipalityCatalog.resolveProvince(code)?.name.orEmpty(),
                            municipality = "",
                        )
                        placeStatus = null
                    },
                    onMunicipalitySelected = { chosen ->
                        draft = draft.copy(province = chosen.provinceName, municipality = chosen.displayName)
                        placeStatus = null
                    },
                    tagPrefix = "parcel-location",
                )
            } else {
                MoTextField(
                    draft.municipality, { draft = draft.copy(municipality = it); placeStatus = null }, "Municipio",
                    Modifier.fillMaxWidth().testTag("parcel-municipality"), supportingText = placeStatus,
                )
                MoTextField(draft.province, { draft = draft.copy(province = it) }, "Provincia", Modifier.fillMaxWidth().testTag("parcel-province"))
            }
            MoTextField(draft.notes, { draft = draft.copy(notes = it) }, "Notas", Modifier.fillMaxWidth(), singleLine = false)
            Text("Los datos escritos aquí se guardan como entrada manual; la app no los presenta como verificados por Catastro.", color = MoSurfaceTokens.secondaryText)
        }
        MoPrimaryButton(
            "Guardar parcela",
            {
                focusManager.clearFocus(force = true)
                keyboard?.hide()
                onSave(draft)
            },
            Modifier.fillMaxWidth().testTag("save-parcel"),
            enabled = !isSaving,
        )
        MoTertiaryButton("Cancelar", onCancel, Modifier.fillMaxWidth(), enabled = !isSaving)
        Spacer(Modifier.height(MoSpacing.lg))
    }
}

/** CR-004 grove description: olive trees, variety and how the parcel is watered. All optional. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GroveFields(draft: ParcelDraft, oliveError: String?, onChange: (ParcelDraft) -> Unit) {
    MoTextField(
        draft.oliveTreeCount,
        { onChange(draft.copy(oliveTreeCount = it.filter(Char::isDigit).take(7))) },
        "Nº de olivos",
        Modifier.fillMaxWidth().testTag("parcel-olive-trees"),
        isError = oliveError != null,
        supportingText = oliveError,
    )
    MoTextField(draft.variety, { onChange(draft.copy(variety = it.take(80))) }, "Variedad (Picual, Hojiblanca…)", Modifier.fillMaxWidth().testTag("parcel-variety"))
    Text("Riego", style = MaterialTheme.typography.labelLarge, color = MoSurfaceTokens.secondaryText)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(MoSpacing.xs)) {
        IrrigationSystem.entries.forEach { system ->
            FilterChip(
                selected = draft.irrigationSystem == system,
                // Tapping the chosen option again clears it: "not told" is different from "secano".
                onClick = { onChange(draft.copy(irrigationSystem = system.takeIf { draft.irrigationSystem != it })) },
                label = { Text(system.label()) },
                modifier = Modifier.testTag("parcel-irrigation-${system.name.lowercase()}"),
            )
        }
    }
    if (draft.irrigationSystem != null && draft.irrigationSystem != IrrigationSystem.DRYLAND) {
        MoTextField(draft.irrigationNetwork, { onChange(draft.copy(irrigationNetwork = it.take(80))) }, "Comunidad de regantes / red", Modifier.fillMaxWidth().testTag("parcel-irrigation-network"))
        MoTextField(draft.irrigationSector, { onChange(draft.copy(irrigationSector = it.take(80))) }, "Sector", Modifier.fillMaxWidth().testTag("parcel-irrigation-sector"))
        Text("Días habituales de riego", style = MaterialTheme.typography.labelLarge, color = MoSurfaceTokens.secondaryText)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(MoSpacing.xs)) {
            DayOfWeek.entries.forEach { day ->
                val selected = day in draft.irrigationDays
                FilterChip(
                    selected = selected,
                    onClick = {
                        onChange(draft.copy(irrigationDays = if (selected) draft.irrigationDays - day else draft.irrigationDays + day))
                    },
                    label = { Text(day.getDisplayName(TextStyle.SHORT, SPANISH).replaceFirstChar { it.titlecase(SPANISH) }) },
                )
            }
        }
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
    oliveTreeCount = agronomy.oliveTreeCount?.toString().orEmpty(),
    variety = agronomy.variety.orEmpty(),
    irrigationSystem = agronomy.irrigationSystem,
    irrigationNetwork = agronomy.irrigationNetwork.orEmpty(),
    irrigationSector = agronomy.irrigationSector.orEmpty(),
    irrigationDays = agronomy.irrigationDays,
)

private val SPANISH = Locale.forLanguageTag("es-ES")
private val SHORT_DATE = DateTimeFormatter.ofPattern("d MMM yyyy", SPANISH)

/** Parcel list row: the cadastral figure says so, it is never presented as worked land. */
private fun Parcel.areaLabel(): String = managedAreaM2?.let(::hectares) ?: cadastralAreaM2?.let { "${hectares(it)} catastrales" } ?: "—"

private fun grouped(value: Int): String = NumberFormat.getIntegerInstance(SPANISH).format(value)

internal fun IrrigationSystem.label() = when (this) {
    IrrigationSystem.DRYLAND -> "Secano"
    IrrigationSystem.DRIP -> "Riego por goteo"
    IrrigationSystem.SPRINKLER -> "Aspersión"
    IrrigationSystem.OTHER -> "Otro riego"
}

internal fun IrrigationSystem.shortLabel() = when (this) {
    IrrigationSystem.DRYLAND -> "Secano"
    IrrigationSystem.DRIP -> "Goteo"
    IrrigationSystem.SPRINKLER -> "Aspersión"
    IrrigationSystem.OTHER -> "Otro"
}

internal fun Set<DayOfWeek>.label(): String? {
    val names = sorted().map { it.getDisplayName(TextStyle.FULL, SPANISH).replaceFirstChar { c -> c.titlecase(SPANISH) } }
    return when (names.size) {
        0 -> null
        1 -> names.single()
        else -> names.dropLast(1).joinToString(", ") + " y " + names.last()
    }
}

private val IMPORT_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", SPANISH)
