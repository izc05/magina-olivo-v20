package com.isivoltpro.maginaolivo.feature.parcels

import androidx.compose.ui.unit.dp
import java.time.ZoneId
import com.isivoltpro.maginaolivo.feature.maps.ParcelMap
import com.isivoltpro.maginaolivo.feature.maps.MapParcel
import androidx.compose.foundation.layout.Arrangement
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
import com.isivoltpro.maginaolivo.ui.theme.MoOliveDark
import com.isivoltpro.maginaolivo.ui.theme.MoShape
import com.isivoltpro.maginaolivo.ui.theme.MoWarmWhite
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
import com.isivoltpro.maginaolivo.domain.attachment.AttachmentOwner
import com.isivoltpro.maginaolivo.domain.attachment.AttachmentOwnerType
import com.isivoltpro.maginaolivo.feature.attachments.AttachmentsRoute
import com.isivoltpro.maginaolivo.app.LocalPersistence
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
    )
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
) {
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
            Row {
                onMap?.let { TextButton(onClick = it, modifier = Modifier.testTag("parcels-map")) { Text("Mapa") } }
                onImportFromCatastro?.let {
                    TextButton(onClick = it, modifier = Modifier.testTag("import-catastro")) { Text("Catastro") }
                }
                TextButton(onClick = { editorVisible = true }, modifier = Modifier.testTag("add-parcel")) { Text("Añadir") }
            }
        },
    )
    when {
        state.isLoading -> CircularProgressIndicator()
        state.active.isEmpty() -> MoEmptyState(
            title = "Aún no hay parcelas",
            body = if (onImportFromCatastro != null) {
                "Añádela a mano o búscala en Catastro por su referencia catastral."
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
            )
        }
    }
}

@Composable
fun ParcelDetailRoute(
    parcelId: UUID,
    persistence: LocalPersistence,
    onArchived: () -> Unit,
) {
    val viewModel: ParcelDetailViewModel = viewModel(
        key = "parcel-$parcelId",
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
                onEdit = { editorVisible = true },
                onArchive = { archiveConfirmation = true },
                attachmentContent = attachmentContent,
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
                oliveError = state.oliveError,
            )
        }
    }
    if (archiveConfirmation) ModalBottomSheet(onDismissRequest = { archiveConfirmation = false }) {
        Column(Modifier.fillMaxWidth().padding(MoSpacing.screen), verticalArrangement = Arrangement.spacedBy(MoSpacing.md)) {
            Text("Archivar parcela", style = MaterialTheme.typography.headlineSmall)
            Text("Se conservarán sus datos y el histórico de pertenencia a la finca.", color = MoTextSecondary)
            MoPrimaryButton("Archivar", { archiveConfirmation = false; onArchive() }, Modifier.fillMaxWidth())
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
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    attachmentContent: @Composable () -> Unit,
    modifier: Modifier,
) {
    var tab by rememberSaveable { mutableStateOf(ParcelTab.ACTIVITY) }
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(MoSpacing.sm),
    ) {
        // Design v3 (CR-004): photo header; the bundled photo is decorative, not this parcel.
        MoPhotoHeader(
            title = parcel.displayName,
            heightFraction = 0.36f,
            location = listOfNotNull(farmName, parcel.municipality).joinToString(" · ").ifEmpty { null },
            trailing = {
                MoStatusChip(
                    text = if (parcel.archivedAt == null) "Activa" else "Archivada",
                    tone = if (parcel.archivedAt == null) MoStatusTone.Success else MoStatusTone.Neutral,
                )
            },
        )
        Column(
            Modifier.padding(horizontal = MoSpacing.screen),
            verticalArrangement = Arrangement.spacedBy(MoSpacing.sm),
        ) {
            MoStatusChip(
                text = if (parcel.source == ParcelSource.CATASTRO) "Importada de Catastro" else "Entrada manual",
                tone = if (parcel.source == ParcelSource.CATASTRO) MoStatusTone.Success else MoStatusTone.Neutral,
            )
            val agronomy = parcel.agronomy
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MoSpacing.xs)) {
                MoStatTile(MoStat("Superficie", parcel.areaLabel(), MoIcons.Area), Modifier.weight(1f).testTag("parcel-stat-area"))
                MoStatTile(MoStat("Olivos", agronomy.oliveTreeCount?.let(::grouped) ?: "—", MoIcons.Olive), Modifier.weight(1f).testTag("parcel-stat-trees"))
                MoStatTile(MoStat("Variedad", agronomy.variety ?: "—", MoIcons.Leaf), Modifier.weight(1f).testTag("parcel-stat-variety"))
                MoStatTile(MoStat("Riego", agronomy.irrigationSystem?.shortLabel() ?: "—", MoIcons.Drop), Modifier.weight(1f).testTag("parcel-stat-irrigation"))
            }
            IrrigationCard(agronomy, onEdit)
            ParcelTabs(tab) { tab = it }
            when (tab) {
                ParcelTab.ACTIVITY -> ParcelActivities(activities)
                ParcelTab.DATA -> Column(verticalArrangement = Arrangement.spacedBy(MoSpacing.sm)) {
                    // Phase 18: the saved boundary, drawn from this phone's copy (works offline).
                    parcel.geometryGeoJson?.let { geometry ->
                        ParcelMap(
                            listOf(MapParcel(parcel.id.toString(), parcel.displayName, geometry)),
                            Modifier.fillMaxWidth().height(300.dp).testTag("parcel-map"),
                        )
                    }
                    ParcelValue("Superficie catastral", parcel.cadastralAreaM2?.let {
                        "${NumberFormat.getNumberInstance(SPANISH).apply { maximumFractionDigits = 2 }.format(it / 10_000)} ha"
                    })
                    ParcelValue("Referencia catastral", parcel.cadastralReference)
                    ParcelValue("Municipio", parcel.municipality)
                    ParcelValue("Polígono", parcel.cadastralPolygon)
                    ParcelValue("Parcela", parcel.cadastralParcel)
                    ParcelValue("Geometría", if (parcel.geometryGeoJson == null) null else "Polígono guardado en el teléfono")
                    ParcelValue("Importada el", parcel.sourceImportedAt?.atZone(ZoneId.systemDefault())?.toLocalDate()?.format(IMPORT_DATE))
                    ParcelValue("Notas", parcel.notes)
                }
                ParcelTab.DOCUMENTS -> attachmentContent()
            }
            MoPrimaryButton("Editar parcela", onEdit, Modifier.fillMaxWidth().testTag("parcel-edit"))
            MoDestructiveButton("Archivar parcela", onArchive, Modifier.fillMaxWidth())
            Spacer(Modifier.height(MoSpacing.xl))
        }
    }
}

private enum class ParcelTab(val label: String) { ACTIVITY("Actividad"), DATA("Datos"), DOCUMENTS("Documentos") }

@Composable
private fun ParcelTabs(selected: ParcelTab, onSelect: (ParcelTab) -> Unit) {
    TabRow(
        selectedTabIndex = selected.ordinal,
        containerColor = MoWarmWhite,
        contentColor = MoOliveDark,
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
                        color = if (tab == selected) MoOliveDark else MoTextSecondary,
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
            Text("Aún no has indicado cómo se riega esta parcela.", style = MaterialTheme.typography.bodyMedium, color = MoTextSecondary)
            TextButton(onClick = onEdit) { Text("Añadir datos de riego") }
        } else {
            Row(Modifier.fillMaxWidth()) {
                MoLabeledValue("Tipo", agronomy.irrigationSystem?.label(), Modifier.weight(1f))
                MoLabeledValue("Sector", agronomy.irrigationSector, Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth()) {
                MoLabeledValue("Comunidad / red", agronomy.irrigationNetwork, Modifier.weight(1f))
                MoLabeledValue("Días habituales", agronomy.irrigationDays.label(), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ParcelActivities(activities: List<Activity>) {
    if (activities.isEmpty()) {
        MoEmptyState(
            "Sin trabajos en esta parcela",
            "Registra una poda, un riego o un tratamiento y aparecerá aquí.",
            icon = MoIcons.History,
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(MoSpacing.xs)) {
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
    oliveError: String? = null,
) {
    var draft by remember(initial) { mutableStateOf(initial) }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(MoSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(MoSpacing.sm),
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        MoTextField(draft.displayName, { draft = draft.copy(displayName = it) }, "Alias *", Modifier.fillMaxWidth().testTag("parcel-name"), isError = nameError != null, supportingText = nameError)
        MoTextField(draft.managedAreaHectares, { draft = draft.copy(managedAreaHectares = it) }, "Superficie gestionada (ha)", Modifier.fillMaxWidth(), isError = areaError != null, supportingText = areaError)
        GroveFields(draft, oliveError) { draft = it }
        MoTextField(draft.cadastralReference, { draft = draft.copy(cadastralReference = it) }, "Referencia catastral", Modifier.fillMaxWidth())
        MoTextField(draft.cadastralPolygon, { draft = draft.copy(cadastralPolygon = it) }, "Polígono", Modifier.fillMaxWidth())
        MoTextField(draft.cadastralParcel, { draft = draft.copy(cadastralParcel = it) }, "Parcela", Modifier.fillMaxWidth())
        MoTextField(draft.municipality, { draft = draft.copy(municipality = it) }, "Municipio", Modifier.fillMaxWidth())
        MoTextField(draft.province, { draft = draft.copy(province = it) }, "Provincia", Modifier.fillMaxWidth())
        MoTextField(draft.notes, { draft = draft.copy(notes = it) }, "Notas", Modifier.fillMaxWidth(), singleLine = false)
        Text("Los datos escritos aquí se guardan como entrada manual; la app no los presenta como verificados por Catastro.", color = MoTextSecondary)
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
    Text("Riego", style = MaterialTheme.typography.labelLarge, color = MoTextSecondary)
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
        Text("Días habituales de riego", style = MaterialTheme.typography.labelLarge, color = MoTextSecondary)
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

/** Managed area, else the cadastral one; "—" when neither is known. */
private fun Parcel.areaLabel(): String = (managedAreaM2 ?: cadastralAreaM2)?.let {
    "${NumberFormat.getNumberInstance(SPANISH).apply { maximumFractionDigits = 2 }.format(it / 10_000)} ha"
} ?: "—"

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
