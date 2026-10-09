package com.isivoltpro.maginaolivo.feature.activities

import com.isivoltpro.maginaolivo.ui.theme.MoColors

import com.isivoltpro.maginaolivo.ui.components.OnEachSave
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import com.isivoltpro.maginaolivo.domain.machinery.MachineOption
import com.isivoltpro.maginaolivo.domain.machinery.MachineUseInput
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.isivoltpro.maginaolivo.domain.attachment.AttachmentOwner
import com.isivoltpro.maginaolivo.domain.attachment.AttachmentOwnerType
import com.isivoltpro.maginaolivo.feature.attachments.AttachmentsRoute
import com.isivoltpro.maginaolivo.app.LocalPersistence
import com.isivoltpro.maginaolivo.core.time.AppClock
import com.isivoltpro.maginaolivo.data.repository.todayForWorkspace
import com.isivoltpro.maginaolivo.domain.expense.Money
import com.isivoltpro.maginaolivo.data.local.model.ActivityStatus
import com.isivoltpro.maginaolivo.domain.activity.Activity
import com.isivoltpro.maginaolivo.domain.activity.ActivityDetail
import com.isivoltpro.maginaolivo.domain.activity.ActivityParcelOption
import com.isivoltpro.maginaolivo.domain.activity.ActivityType
import com.isivoltpro.maginaolivo.domain.campaign.Campaign
import com.isivoltpro.maginaolivo.domain.activity.IncidentSeverity
import com.isivoltpro.maginaolivo.domain.activity.IncidentState
import com.isivoltpro.maginaolivo.domain.activity.IrrigationPricingBasis
import com.isivoltpro.maginaolivo.domain.farm.Farm
import com.isivoltpro.maginaolivo.ui.components.MoDateInputField
import com.isivoltpro.maginaolivo.ui.components.MoEmptyState
import com.isivoltpro.maginaolivo.ui.components.MoErrorState
import com.isivoltpro.maginaolivo.ui.components.MoPrimaryButton
import com.isivoltpro.maginaolivo.ui.components.MoCompactListItem
import com.isivoltpro.maginaolivo.ui.components.MoSecondaryButton
import com.isivoltpro.maginaolivo.ui.components.MoSectionHeader
import com.isivoltpro.maginaolivo.ui.components.MoStatusChip
import com.isivoltpro.maginaolivo.ui.components.MoStatusTone
import com.isivoltpro.maginaolivo.ui.components.MoTextField
import com.isivoltpro.maginaolivo.ui.theme.MoSpacing
import androidx.compose.foundation.layout.size
import com.isivoltpro.maginaolivo.ui.components.MoCompactListItem
import com.isivoltpro.maginaolivo.ui.components.MoIconBadge
import com.isivoltpro.maginaolivo.ui.components.MoDestructiveButton
import com.isivoltpro.maginaolivo.ui.components.MoIcons
import com.isivoltpro.maginaolivo.ui.components.MoSummaryMetric
import com.isivoltpro.maginaolivo.ui.components.MoTertiaryButton
import java.time.LocalDate
import java.util.UUID
import com.isivoltpro.maginaolivo.ui.theme.MoSurfaceTokens

@Composable
fun FarmActivitiesRoute(
    farmId: UUID,
    persistence: LocalPersistence,
    onActivitySelected: (UUID) -> Unit,
    startWithEditor: Boolean = false,
    /** UX-D: what "Registrar hoy" already knows (type, today's date). */
    initialDraft: ActivityDraft = ActivityDraft(),
    editorTitle: String = "Nuevo trabajo",
    /** The Cuaderno already asked for the type, so the editor must not ask again. */
    lockInitialType: Boolean = false,
    /** Register-from-notebook uses a real page; farm history keeps its contextual sheet. */
    editorAsScreen: Boolean = false,
    /** "Registrar hoy" is a diary entry for work already done, unlike farm planning. */
    completeOnSave: Boolean = false,
    /** #435: canonical day of the Workspace; required by done-work entry points. */
    today: LocalDate? = null,
) {
    val vm: FarmActivitiesViewModel = viewModel(key = "farm-activities-$farmId", factory = viewModelFactory {
        initializer {
            FarmActivitiesViewModel(
                farmId,
                persistence.activityRepository,
                persistence.campaignRepository,
                today = today,
            )
        }
    })
    val state by vm.state.collectAsStateWithLifecycle()
    FarmActivitiesSection(
        state = state,
        onActivitySelected = onActivitySelected,
        onCreate = { draft, asDraft -> vm.create(draft, asDraft, completeImmediately = completeOnSave && !asDraft) },
        startWithEditor = startWithEditor,
        initialDraft = initialDraft,
        editorTitle = editorTitle,
        lockInitialType = lockInitialType,
        editorAsScreen = editorAsScreen,
        quickEntry = completeOnSave,
        today = today,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FarmActivitiesSection(
    state: FarmActivitiesUiState,
    onActivitySelected: (UUID) -> Unit,
    onCreate: (ActivityDraft, Boolean) -> Unit,
    startWithEditor: Boolean = false,
    initialDraft: ActivityDraft = ActivityDraft(),
    editorTitle: String = "Nuevo trabajo",
    lockInitialType: Boolean = false,
    editorAsScreen: Boolean = false,
    /** #414: a Cuaderno quick action records work already done: no planning, no draft step. */
    quickEntry: Boolean = false,
    /** #435: canonical Workspace day for visual validation. */
    today: LocalDate? = null,
) {
    var editor by rememberSaveable { mutableStateOf(startWithEditor) }
    OnEachSave(state.saveCount) { editor = false }
    if (editorAsScreen && editor) {
        ActivityEditor(
            parcels = state.parcels,
            campaigns = state.campaigns,
            campaignError = state.campaignError,
            machines = state.machines,
            descriptionError = state.descriptionError,
            dateError = state.dateError,
            parcelsError = state.parcelsError,
            isSaving = state.isSaving,
            onSave = { draft -> onCreate(draft, false) },
            // #414: saving is already local-first; a quick entry needs no second «borrador» choice.
            onSaveDraft = if (quickEntry) null else { draft -> onCreate(draft, true) },
            onCancel = { editor = false },
            initial = initialDraft,
            title = editorTitle,
            lockInitialType = lockInitialType,
            doneWork = quickEntry,
            autoSelectSingleParcel = true,
            today = today,
        )
    } else {
        // UX-D: saving is confirmed where the farmer is looking, not only by the closed sheet.
        state.message?.let { message ->
            MoStatusChip(message, tone = MoStatusTone.Success, modifier = Modifier.testTag("activities-saved"))
        }
        MoSectionHeader(
            "Trabajos",
            action = {
                TextButton(onClick = { editor = true }, modifier = Modifier.testTag("add-activity")) { Text("Añadir") }
            },
        )
        when {
            state.isLoading -> CircularProgressIndicator()
            state.error != null -> MoErrorState("No pudimos abrir los trabajos", state.error)
            state.drafts.isEmpty() && state.planned.isEmpty() && state.history.isEmpty() ->
                MoEmptyState("Aún no hay trabajos", "Registra un trabajo y selecciona las parcelas donde se realiza.", icon = MoIcons.Activity)
            else -> {
                if (state.drafts.isNotEmpty()) {
                    MoSectionHeader("Borradores")
                    state.drafts.forEach { ActivityRow(it, onActivitySelected) }
                }
                state.planned.forEach { ActivityRow(it, onActivitySelected) }
                if (state.history.isNotEmpty()) {
                    MoSectionHeader("Histórico")
                    state.history.forEach { ActivityRow(it, onActivitySelected) }
                }
            }
        }
        if (editor) {
            ModalBottomSheet(onDismissRequest = { editor = false }) {
                ActivityEditor(
                    parcels = state.parcels,
                    campaigns = state.campaigns,
                    campaignError = state.campaignError,
                    machines = state.machines,
                    descriptionError = state.descriptionError,
                    dateError = state.dateError,
                    parcelsError = state.parcelsError,
                    isSaving = state.isSaving,
                    onSave = { draft -> onCreate(draft, false) },
                    onSaveDraft = { draft -> onCreate(draft, true) },
                    onCancel = { editor = false },
                    initial = initialDraft,
                    title = editorTitle,
                    lockInitialType = lockInitialType,
                    autoSelectSingleParcel = true,
                    today = today,
                )
            }
        }
    }
}

/**
 * The real «Registrar trabajo» flow behind the Cuaderno actions, and Avisos → «Planificar trabajo»
 * when [planning] (CR-011 §12: the Cuaderno records what happened, Avisos plans the future).
 *
 * It is the same aggregate and the same editor the Farm detail uses: the only extra
 * step is resolving which Farm the work belongs to, because a global entry point has
 * no Farm in context. One Farm resolves itself.
 */
@Composable
fun RegisterActivityRoute(
    persistence: LocalPersistence,
    onActivitySelected: (UUID) -> Unit,
    preselectedFarmId: UUID? = null,
    onFarmPreselected: () -> Unit = {},
    /** UX-D: the type chosen in "Registrar hoy" (Riego, Tratamiento…); null lets the farmer pick. */
    presetType: ActivityType? = null,
    /** UX-F: the Parcel "Registrar" was pressed on; it belongs to [preselectedFarmId]. */
    preselectedParcelId: UUID? = null,
    /** CR-011 §12: opened from Avisos; the work is saved as planned, never as done. */
    planning: Boolean = false,
    /** #435/#619: resolves today using the Workspace timezone, not the phone timezone. */
    clock: AppClock,
) {
    var selectedTypeName by rememberSaveable { mutableStateOf(presetType?.name) }
    LaunchedEffect(presetType) { if (presetType != null) selectedTypeName = presetType.name }
    // The Farm the Parcel belongs to, kept after the Farm preselection is consumed.
    val parcelFarmId by rememberSaveable { mutableStateOf(preselectedFarmId?.toString()) }
    val vm: RegisterActivityViewModel = viewModel(factory = viewModelFactory {
        initializer {
            RegisterActivityViewModel(persistence.farmRepository, persistence.workspaceRepository)
        }
    })
    val state by vm.state.collectAsStateWithLifecycle()
    val workspaceId = state.farms.firstOrNull()?.workspaceId
    var workspaceToday by remember(workspaceId) { mutableStateOf<LocalDate?>(null) }
    LaunchedEffect(workspaceId, clock) {
        workspaceToday = workspaceId?.let { id ->
            runCatching { persistence.database.todayForWorkspace(id, clock) }.getOrNull()
        }
    }
    // #498: validate the contextual Farm before consuming navigation state.
    LaunchedEffect(preselectedFarmId) {
        if (preselectedFarmId != null) vm.preselectFarm(preselectedFarmId)
    }
    LaunchedEffect(preselectedFarmId, state.contextualFarmId, state.contextualFarmAccepted) {
        if (
            preselectedFarmId != null &&
            state.contextualFarmId == preselectedFarmId &&
            state.contextualFarmAccepted
        ) {
            onFarmPreselected()
        }
    }
    Scaffold(
        modifier = Modifier.fillMaxSize().testTag("register-activity-root"),
        containerColor = MoSurfaceTokens.appBackground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding()
                .then(if (selectedTypeName == null || state.selectedFarmId == null) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(horizontal = MoSpacing.screen),
            verticalArrangement = Arrangement.spacedBy(MoSpacing.md),
        ) {
            if (selectedTypeName == null) {
                Text(if (planning) "Planificar trabajo" else "Registrar trabajo", style = MaterialTheme.typography.headlineLarge, color = MoColors.current.primaryText)
            }
            when {
                state.isLoading -> CircularProgressIndicator()
                state.error != null -> MoErrorState(
                    if (state.contextualFarmId != null) "Esta finca ya no está disponible" else "No pudimos abrir tus fincas",
                    state.error.orEmpty(),
                    onRetry = vm::retry,
                )
                state.farms.isEmpty() -> MoEmptyState(
                    "Aún no tienes fincas",
                    "Crea una finca en Mi Campo y podrás registrar trabajos en sus parcelas.",
                    icon = MoIcons.Tree,
                )
                workspaceToday == null -> CircularProgressIndicator(Modifier.testTag("activity-workspace-day-loading"))
                else -> {
                    val selectedFarmId = state.selectedFarmId
                    if (selectedFarmId == null) {
                        MoSectionHeader("¿En qué finca?")
                        state.farms.forEach { farm ->
                            FarmChoiceRow(farm) { vm.selectFarm(farm.id) }
                        }
                    } else {
                        val selectedFarm = state.farms.firstOrNull { it.id == selectedFarmId }
                        val activityType = selectedTypeName?.let { runCatching { ActivityType.valueOf(it) }.getOrNull() }
                        if (activityType == null) {
                            ActivityTypeChooser(
                                farmName = selectedFarm?.name.orEmpty(),
                                planning = planning,
                                onSelected = { selectedTypeName = it.name },
                            )
                            return@Column
                        }
                        // #375: a Farm brought from the Cuaderno is context; only a free choice can change.
                        val farmFromContext = parcelFarmId != null && selectedFarmId.toString() == parcelFarmId
                        val changeFarm: (@Composable () -> Unit)? =
                            if (state.farms.size > 1 && !farmFromContext) {
                                {
                                    TextButton(
                                        onClick = vm::changeFarm,
                                        modifier = Modifier.testTag("change-activity-farm"),
                                    ) { Text("Cambiar finca") }
                                }
                            } else {
                                null
                            }
                        MoSectionHeader(selectedFarm?.name ?: "Finca seleccionada", action = changeFarm)
                        FarmActivitiesRoute(
                            farmId = selectedFarmId,
                            persistence = persistence,
                            onActivitySelected = onActivitySelected,
                            startWithEditor = true,
                            // Today by default (editable); the type already chosen; the Farm in the title.
                            initialDraft = ActivityDraft(
                                type = activityType,
                                activityDate = workspaceToday,
                                // #414: the type already says what was done; a short detail is optional.
                                description = "",
                                // Only while the Parcel's own Farm is the one chosen.
                                parcelIds = preselectedParcelId
                                    ?.takeIf { selectedFarmId.toString() == parcelFarmId }
                                    ?.let { setOf(it) }
                                    .orEmpty(),
                            ),
                            editorTitle = if (planning) "Planificar · ${activityType.label()}" else activityType.label(),
                            lockInitialType = true,
                            editorAsScreen = true,
                            completeOnSave = !planning,
                            today = workspaceToday,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun ActivityTypeChooser(farmName: String, onSelected: (ActivityType) -> Unit, planning: Boolean = false) {
    Column(
        Modifier.fillMaxWidth().testTag("activity-type-chooser"),
        verticalArrangement = Arrangement.spacedBy(MoSpacing.xs),
    ) {
        Text(if (planning) "¿Qué trabajo quieres planificar?" else "¿Qué trabajo vas a apuntar?", style = MaterialTheme.typography.headlineSmall, color = MoColors.current.primaryText)
        Text(farmName, style = MaterialTheme.typography.titleMedium, color = MoSurfaceTokens.secondaryText)
        workTypes(planning).forEach { type ->
            MoCompactListItem(
                title = type.label(),
                subtitle = type.shortDescription(),
                icon = type.icon(),
                onClick = { onSelected(type) },
                modifier = Modifier.testTag("register-activity-type-${type.name.lowercase()}"),
            )
        }
    }
}

/**
 * #378/#410: one concept, one main path.
 * Cuaderno → Trabajo is only for general work. Riego and Tratamiento keep their direct
 * Cuaderno actions, and Jornada de recolección belongs to Campaña/Avisos.
 * Planning still exposes the complete catalogue because Avisos is the planning surface.
 */
internal fun workTypes(planning: Boolean): List<ActivityType> =
    if (planning) ActivityType.entries
    else ActivityType.entries.filterNot {
        it == ActivityType.HARVEST_DAY ||
            it == ActivityType.IRRIGATION ||
            it == ActivityType.PHYTOSANITARY
    }

internal fun ActivityType.needsAffectedArea(): Boolean =
    this == ActivityType.PHYTOSANITARY || this == ActivityType.FERTILIZATION || this == ActivityType.IRRIGATION

internal fun ActivityType.needsDateInterval(): Boolean =
    this == ActivityType.PHYTOSANITARY || this == ActivityType.FERTILIZATION || this == ActivityType.IRRIGATION

private fun ActivityType.shortDescription(): String = when (this) {
    ActivityType.OBSERVATION -> "Revisar el estado del olivar"
    ActivityType.PRUNING -> "Poda de los olivos"
    ActivityType.SOIL_WORK -> "Desbroce, laboreo y suelo"
    ActivityType.FERTILIZATION -> "Abono y nutrientes"
    ActivityType.PHYTOSANITARY -> "Cura y tratamiento"
    ActivityType.IRRIGATION -> "Agua, horas y sector"
    ActivityType.MAINTENANCE -> "Reparaciones y mantenimiento"
    ActivityType.INCIDENT -> "Daños o incidencias"
    ActivityType.OTHER -> "Otro trabajo del campo"
    ActivityType.HARVEST_DAY -> "Organizar una jornada de recogida"
}

@Composable
private fun FarmChoiceRow(farm: Farm, onSelected: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("register-farm-option")
            .clickable(role = Role.Button, onClick = onSelected),
        colors = CardDefaults.cardColors(containerColor = MoSurfaceTokens.cardSurface),
        border = BorderStroke(1.dp, MoSurfaceTokens.cardStroke),
    ) {
        Column(Modifier.padding(MoSpacing.md)) {
            Text(farm.name, style = MaterialTheme.typography.titleMedium)
            Text(
                "${farm.parcelCount} parcelas",
                style = MaterialTheme.typography.bodyMedium,
                color = MoSurfaceTokens.secondaryText,
            )
        }
    }
}

@Composable
private fun ActivityRow(activity: Activity, onSelected: (UUID) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button) { onSelected(activity.id) }.testTag("activity-row"),
        colors = CardDefaults.cardColors(containerColor = MoSurfaceTokens.cardSurface),
        border = BorderStroke(1.dp, MoSurfaceTokens.cardStroke),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = MoSpacing.sm, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(MoSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MoIconBadge(activity.type.icon())
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(activity.description, style = MaterialTheme.typography.titleSmall, maxLines = 2)
                Text(
                    "${activity.type.label()} · ${activity.compactDateLabel()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MoSurfaceTokens.secondaryText,
                    maxLines = 1,
                )
                Text(activity.targetsLabel(), style = MaterialTheme.typography.bodySmall, color = MoSurfaceTokens.secondaryText, maxLines = 1)
            }
            MoStatusChip(activity.status.label(), tone = activity.status.tone())
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun ActivityEditor(
    parcels: List<ActivityParcelOption>,
    descriptionError: String?,
    dateError: String?,
    parcelsError: String?,
    isSaving: Boolean,
    onSave: (ActivityDraft) -> Unit,
    onCancel: () -> Unit,
    onSaveDraft: ((ActivityDraft) -> Unit)? = null,
    initial: ActivityDraft = ActivityDraft(),
    title: String = "Nuevo trabajo",
    /** Machines that can be named; empty hides nothing but the choice (Phase 15). */
    machines: List<MachineOption> = emptyList(),
    /** True when the preceding Cuaderno choice already fixed the work type. */
    lockInitialType: Boolean = false,
    /**
     * #414: the Cuaderno records work already done. Its typed fields start open, and the
     * planning and reminders of Avisos → Planificar trabajo are not offered.
     */
    doneWork: Boolean = false,
    /** #414: only creation flows tick a Farm's single Parcel; edits keep exactly what was saved. */
    autoSelectSingleParcel: Boolean = false,
    /** #441 (Codex #530): shown under [parcelsError], inside the sheet, so its links can be used. */
    parcelsErrorContent: @Composable () -> Unit = {},
    /** #482: Campaign context is only relevant to HARVEST_DAY planning. Appended for source compatibility. */
    campaigns: List<Campaign> = emptyList(),
    campaignError: String? = null,
    /** #435: canonical Workspace day. Null keeps non-writing previews source-compatible. */
    today: LocalDate? = null,
) {
    var description by rememberSaveable(initial.description) { mutableStateOf(initial.description) }
    var date by rememberSaveable(initial.activityDate) { mutableStateOf(initial.activityDate?.toString().orEmpty()) }
    var endDate by rememberSaveable(initial.activityEndDate) { mutableStateOf(initial.activityEndDate?.toString().orEmpty()) }
    var intervalOpen by rememberSaveable(initial.activityEndDate) { mutableStateOf(initial.activityEndDate != null) }
    var notes by rememberSaveable(initial.notes) { mutableStateOf(initial.notes) }
    var type by rememberSaveable(initial.type) { mutableStateOf(initial.type.name) }
    var selected by rememberSaveable(initial.parcelIds) { mutableStateOf(initial.parcelIds.map(UUID::toString)) }
    var selectedCampaignId by rememberSaveable(initial.campaignId) {
        mutableStateOf(initial.campaignId?.toString())
    }
    val selectedType = runCatching { ActivityType.valueOf(type) }.getOrDefault(ActivityType.OTHER)
    LaunchedEffect(type, campaigns) {
        if (selectedType == ActivityType.HARVEST_DAY) {
            if (selectedCampaignId == null && campaigns.size == 1) {
                selectedCampaignId = campaigns.single().id.toString()
            } else if (selectedCampaignId != null && campaigns.none { it.id.toString() == selectedCampaignId }) {
                selectedCampaignId = null
            }
        }
    }
    val selectedCampaign = if (selectedType == ActivityType.HARVEST_DAY) {
        campaigns.firstOrNull { it.id.toString() == selectedCampaignId } ?: campaigns.singleOrNull()
    } else {
        null
    }
    val selectableParcels = if (selectedType == ActivityType.HARVEST_DAY) {
        val allowed = selectedCampaign?.snapshots?.map { it.parcelId }?.toSet().orEmpty()
        parcels.filter { it.id in allowed }
    } else {
        parcels
    }
    LaunchedEffect(type, selectedCampaign?.id) {
        if (selectedType == ActivityType.HARVEST_DAY) {
            val allowed = selectableParcels.map { it.id.toString() }.toSet()
            selected = selected.filter { it in allowed }
        }
    }
    // #546: area is explicit per target. The Parcel's managed area is only a visible suggestion,
    // never inferred later by the repository.
    val parcelAreaHa = remember(initial.parcelAreasM2) {
        mutableStateMapOf<String, String>().apply {
            initial.parcelAreasM2.forEach { (id, areaM2) ->
                areaM2?.let { put(id.toString(), editableAreaHa(it)) }
            }
        }
    }
    val parcelAreaErrors = remember { mutableStateMapOf<String, String>() }
    // #414: a new entry on a Farm with a single Parcel needs no choice; it is ticked once (and can
    // still be unticked). Never on an edit: a record saved without Parcels keeps none.
    var singleParcelOffered by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(selectableParcels, autoSelectSingleParcel, selected) {
        if (autoSelectSingleParcel && !singleParcelOffered && selected.isEmpty() && selectableParcels.size == 1) {
            selected = listOf(selectableParcels.single().id.toString())
            singleParcelOffered = true
        }
    }
    fun readParcelAreas(): Map<UUID, Double?>? {
        parcelAreaErrors.clear()
        // #546/#440: a type that does not ask for the surface never saves one typed while another
        // type was chosen (its field is hidden). A new entry saves none; an edit keeps exactly the
        // surface each target already had — never lost silently, never replaced by a hidden value.
        val currentType = runCatching { ActivityType.valueOf(type) }.getOrDefault(ActivityType.OTHER)
        if (!currentType.needsAffectedArea()) {
            return selected.map(UUID::fromString).associateWith { initial.parcelAreasM2[it] }
        }
        val result = linkedMapOf<UUID, Double?>()
        selected.forEach { idText ->
            val parcel = selectableParcels.firstOrNull { it.id.toString() == idText }
            val text = parcelAreaHa[idText].orEmpty().trim()
            if (text.isBlank()) {
                result[UUID.fromString(idText)] = null
                return@forEach
            }
            val hectares = text.replace(',', '.').toDoubleOrNull()
            val maxHa = parcel?.managedAreaM2?.div(10_000.0)
            val error = when {
                hectares == null || !hectares.isFinite() || hectares <= 0.0 ->
                    "Escribe una superficie mayor que 0, por ejemplo 0,50"
                maxHa != null && hectares > maxHa + 0.000001 ->
                    "No puede superar ${editableAreaHa(maxHa * 10_000.0)} ha de esta parcela"
                else -> null
            }
            if (error != null) parcelAreaErrors[idText] = error
            else result[UUID.fromString(idText)] = hectares!! * 10_000.0
        }
        return result.takeIf { parcelAreaErrors.isEmpty() }
    }
    // Deliberately not rememberSaveable: the sheet itself does not survive process death,
    // so saving the typed block alone would restore it into an editor that is not there.
    val detailFields = remember(initial.detail) {
        mutableStateMapOf<String, String>().apply { putAll(initial.detail.toFields()) }
    }
    // #473: typed text the form could not read, keyed by field; shown next to it until corrected.
    val detailErrors = remember { mutableStateMapOf<String, String>() }
    var agronomicDetailsOpen by rememberSaveable(initial.type) {
        mutableStateOf(initial.detail.toFields().isNotEmpty() || doneWork)
    }
    // Selected machine id → the hours typed for it ("" when not given).
    val machineHours = remember(initial.machines) {
        mutableStateMapOf<String, String>().apply {
            initial.machines.forEach { use -> put(use.machineId.toString(), use.usageHours?.let(::editableHours).orEmpty()) }
        }
    }
    var machinesError by rememberSaveable { mutableStateOf<String?>(null) }
    // Phase 16: optional planning and reminders. Not rememberSaveable, like the typed block.
    var planning by remember(initial.planning, initial.reminders) {
        mutableStateOf(PlanningInput.of(initial.planning, initial.reminders))
    }
    var planningError by remember { mutableStateOf<PlanningInput.Result.Invalid?>(null) }
    fun readPlanning(): PlanningInput.Result.Ok? = when (val result = planning.read()) {
        is PlanningInput.Result.Ok -> result.also { planningError = null }
        is PlanningInput.Result.Invalid -> { planningError = result; null }
    }
    fun readMachines(): List<MachineUseInput>? {
        val uses = machineHours.entries.map { (id, hours) ->
            val value = hours.replace(',', '.').trim()
            if (value.isNotEmpty() && value.toDoubleOrNull() == null) {
                machinesError = "Escribe las horas como 3 o 3,5"
                return null
            }
            MachineUseInput(UUID.fromString(id), usageHours = value.toDoubleOrNull())
        }
        machinesError = null
        return uses.sortedBy { it.machineId.toString() }
    }

    /** #473: false (and the errors shown, the block open) while a typed value cannot be read. */
    fun readTypedDetail(): Boolean {
        val errors = detailFieldErrors(runCatching { ActivityType.valueOf(type) }.getOrDefault(ActivityType.OTHER), detailFields)
        detailErrors.clear()
        detailErrors.putAll(errors)
        if (errors.isNotEmpty()) agronomicDetailsOpen = true
        return errors.isEmpty()
    }

    // Hour, people, machinery, reminders and notes are optional: folded unless used.
    val hasExtras = initial.machines.isNotEmpty() || initial.planning != null || initial.reminders.isNotEmpty() ||
        initial.notes.isNotBlank()
    var moreOpen by rememberSaveable(hasExtras) { mutableStateOf(hasExtras) }
    val showMore = moreOpen || machinesError != null || planningError != null

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(MoSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(MoSpacing.sm),
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        if (!lockInitialType) {
            // One kind of work per record: compact chips with their icon, one choice only.
            FormLabel("¿Qué trabajo?")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(MoSpacing.xs),
                verticalArrangement = Arrangement.spacedBy(MoSpacing.xs),
            ) {
                // #378/#410: a recogida day is planned in Avisos and Riego/Tratamiento have their own
                // Cuaderno actions, so a new Trabajo does not offer them; an existing record keeps
                // its own type.
                val offered = workTypes(planning = initial.planning != null || initial.type == ActivityType.HARVEST_DAY)
                (offered + listOfNotNull(initial.type.takeIf { it !in offered })).forEach { option ->
                    FilterChip(
                        selected = option.name == type,
                        onClick = { type = option.name },
                        label = { Text(option.label()) },
                        leadingIcon = { Icon(option.icon(), contentDescription = null, modifier = Modifier.size(18.dp)) },
                        modifier = Modifier.testTag("activity-type-option").semantics { role = Role.RadioButton },
                    )
                }
            }
        }
        val chosenType = runCatching { ActivityType.valueOf(type) }.getOrDefault(ActivityType.OTHER)
        if (chosenType == ActivityType.HARVEST_DAY) {
            FormLabel("Campaña")
            when {
                campaigns.isEmpty() -> Text(
                    "Crea o prepara una campaña antes de planificar una jornada de recolección.",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("harvest-day-no-campaign"),
                )
                campaigns.size == 1 -> {
                    MoStatusChip(
                        campaigns.single().name,
                        tone = MoStatusTone.Info,
                        modifier = Modifier.testTag("harvest-day-campaign-fixed"),
                    )
                }
                else -> FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(MoSpacing.xs),
                    verticalArrangement = Arrangement.spacedBy(MoSpacing.xs),
                ) {
                    campaigns.forEach { campaign ->
                        FilterChip(
                            selected = selectedCampaignId == campaign.id.toString(),
                            onClick = {
                                selectedCampaignId = campaign.id.toString()
                                selected = emptyList()
                                singleParcelOffered = false
                            },
                            label = { Text(campaign.name) },
                            modifier = Modifier.testTag("harvest-day-campaign-option"),
                        )
                    }
                }
            }
            campaignError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
        MoTextField(
            description, { description = it },
            // #414: only Observación and Otro need words; any other type already says what was done.
            when {
                chosenType.needsDescription() -> "Descripción"
                // #414: an Incidencia must say what happened: its category or a short detail.
                chosenType == ActivityType.INCIDENT -> "Detalle breve (o indica la categoría)"
                else -> "Detalle breve (opcional)"
            },
            isError = descriptionError != null, supportingText = descriptionError,
            modifier = Modifier.testTag("activity-description"),
        )
        // #435/#414: work already done cannot be dated ahead; it is never saved as a quiet plan.
        val parsedStart = runCatching { LocalDate.parse(date) }.getOrNull()
        val activityType = runCatching { ActivityType.valueOf(type) }.getOrDefault(ActivityType.OTHER)
        val intervalRelevant = activityType.needsDateInterval() || initial.activityEndDate != null
        val parsedEnd = endDate
            .takeIf { intervalRelevant && intervalOpen }
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val futureDoneWork = isFutureDoneWork(
            activityDate = parsedStart,
            today = today,
            doneWork = doneWork,
            type = selectedType,
        )
        val futureDoneEnd = isFutureDoneWork(
            activityDate = parsedEnd,
            today = today,
            doneWork = doneWork,
            type = selectedType,
        )
        val endBeforeStart = parsedStart != null && parsedEnd != null && parsedEnd.isBefore(parsedStart)
        val missingEnd = intervalRelevant && intervalOpen && endDate.isBlank()
        MoDateInputField(
            date, { date = it }, "Fecha",
            isError = dateError != null || futureDoneWork,
            supportingText = if (futureDoneWork) FUTURE_DONE_WORK else dateError,
            modifier = Modifier.testTag("activity-date"),
        )
        if (intervalRelevant) {
            if (!intervalOpen) {
                TextButton(
                    onClick = { intervalOpen = true },
                    modifier = Modifier.testTag("activity-date-interval-open"),
                ) { Text("Duró varios días") }
            } else {
                MoDateInputField(
                    endDate,
                    { endDate = it },
                    "Hasta",
                    isError = missingEnd || endBeforeStart || futureDoneEnd,
                    supportingText = when {
                        missingEnd -> "Selecciona la fecha final"
                        endBeforeStart -> "La fecha final no puede ser anterior a la inicial"
                        futureDoneEnd -> FUTURE_DONE_WORK
                        else -> "Último día incluido"
                    },
                    modifier = Modifier.testTag("activity-end-date"),
                )
                TextButton(
                    onClick = { intervalOpen = false; endDate = "" },
                    modifier = Modifier.testTag("activity-date-single-day"),
                ) { Text("Fue un solo día") }
            }
        }
        if (activityType.hasTypedDetail()) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .clickable(role = Role.Button) { agronomicDetailsOpen = !agronomicDetailsOpen }
                    .testTag("activity-detail-more"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MoSpacing.xs),
            ) {
                Icon(
                    if (agronomicDetailsOpen) MoIcons.ChevronDown else MoIcons.ChevronRight,
                    contentDescription = null,
                    tint = MoColors.current.actionText,
                )
                Column(Modifier.weight(1f)) {
                    Text("Detalles de ${activityType.label().lowercase()}", style = MaterialTheme.typography.titleSmall, color = MoColors.current.actionText)
                    if (!agronomicDetailsOpen) {
                        Text("Datos opcionales", style = MaterialTheme.typography.bodySmall, color = MoSurfaceTokens.secondaryText)
                    }
                }
            }
            if (agronomicDetailsOpen) {
                ActivityTypedDetailFields(type = activityType, fields = detailFields, errors = detailErrors)
            }
        }
        FormLabel("Parcelas")
        // One canonical Activity may target many Parcels; selecting several never
        // creates several Activities.
        if (selectableParcels.isEmpty()) {
            Text(
                when {
                    chosenType == ActivityType.HARVEST_DAY && selectedCampaign == null ->
                        "Elige primero la campaña de esta jornada."
                    chosenType == ActivityType.HARVEST_DAY ->
                        "Esta campaña todavía no tiene parcelas disponibles para la jornada."
                    else -> "Primero añade una parcela a esta finca."
                },
                color = MoSurfaceTokens.secondaryText,
            )
        }
        parcelsError?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
            parcelsErrorContent()
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(MoSpacing.xs),
            verticalArrangement = Arrangement.spacedBy(MoSpacing.xs),
        ) {
            selectableParcels.forEach { parcel ->
                val checked = parcel.id.toString() in selected
                FilterChip(
                    selected = checked,
                    onClick = {
                        val key = parcel.id.toString()
                        if (checked) {
                            selected = selected - key
                            parcelAreaHa.remove(key)
                            parcelAreaErrors.remove(key)
                        } else {
                            selected = selected + key
                        }
                    },
                    label = { Text(parcel.name) },
                    leadingIcon = if (checked) {
                        { Icon(MoIcons.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    } else {
                        null
                    },
                    modifier = Modifier.testTag("activity-parcel-option"),
                )
            }
        }
        if (chosenType.needsAffectedArea()) {
            selectableParcels.filter { it.id.toString() in selected }.forEach { parcel ->
                val key = parcel.id.toString()
                val known = parcel.managedAreaM2?.let { " · parcela ${hectaresLabel(it)}" }.orEmpty()
                MoTextField(
                    parcelAreaHa[key].orEmpty(),
                    { parcelAreaHa[key] = it; parcelAreaErrors.remove(key) },
                    "Superficie afectada · ${parcel.name} (ha)",
                    isError = parcelAreaErrors[key] != null,
                    supportingText = parcelAreaErrors[key] ?: if (known.isNotEmpty()) {
                        "Parcela conocida$known · confirma la superficie realmente trabajada"
                    } else {
                        "Indica la superficie realmente trabajada"
                    },
                    modifier = Modifier.fillMaxWidth().testTag("activity-parcel-area"),
                )
                parcel.managedAreaM2?.let { fullArea ->
                    if (parcelAreaHa[key].orEmpty() != editableAreaHa(fullArea)) {
                        TextButton(
                            onClick = {
                                parcelAreaHa[key] = editableAreaHa(fullArea)
                                parcelAreaErrors.remove(key)
                            },
                            modifier = Modifier.testTag("activity-parcel-use-full-area"),
                        ) {
                            Text("Usar toda · ${hectaresLabel(fullArea)}")
                        }
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) { moreOpen = !showMore }
                .testTag("activity-more"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MoSpacing.xs),
        ) {
            Icon(if (showMore) MoIcons.ChevronDown else MoIcons.ChevronRight, contentDescription = null, tint = MoColors.current.actionText)
            Column(Modifier.weight(1f)) {
                Text("Más opciones", style = MaterialTheme.typography.titleSmall, color = MoColors.current.actionText)
                if (!showMore) {
                    Text(
                        when {
                            doneWork -> "Maquinaria y notas"
                            else -> "Hora, personas, maquinaria, avisos y notas"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MoSurfaceTokens.secondaryText,
                    )
                }
            }
        }
        if (showMore) {
            // Phase 15: optional. An Activity never needs a machine, and hours are optional too.
            if (machines.isNotEmpty()) {
                FormLabel("Uso de maquinaria")
                machinesError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                machines.forEach { machine ->
                    val key = machine.id.toString()
                    val checked = key in machineHours
                    Row(
                        Modifier.fillMaxWidth().testTag("activity-machine-option").clickable {
                            if (checked) machineHours.remove(key) else machineHours[key] = ""
                        }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked, { value -> if (value) machineHours[key] = "" else machineHours.remove(key) })
                        Text(machine.name)
                    }
                    if (checked) {
                        MoTextField(
                            machineHours[key].orEmpty(),
                            { machineHours[key] = it; machinesError = null },
                            "Horas de ${machine.name} (opcional)",
                            modifier = Modifier.fillMaxWidth().testTag("activity-machine-hours"),
                        )
                    }
                }
            }
            // #414: planning and reminders live in Avisos → Planificar trabajo, not in done work.
            if (!doneWork) {
                PlanningFields(
                    input = planning,
                    error = planningError,
                    onChange = { planning = it; planningError = null },
                )
            }
            MoTextField(notes, { notes = it }, "Notas")
            // #416/#429: the editor holds no money. Only draft or planned work is edited here and it
            // never counts a cost; a cost linked before 1.0 is corrected on its own Gasto.
        }
        MoPrimaryButton(
            if (doneWork && !chosenType.needsDescription()) "Guardar ${chosenType.label().lowercase()}" else "Guardar trabajo",
            {
                val machineUses = readMachines() ?: return@MoPrimaryButton
                val planned = readPlanning() ?: return@MoPrimaryButton
                if (!readTypedDetail()) return@MoPrimaryButton
                if (missingEnd || endBeforeStart || futureDoneEnd) return@MoPrimaryButton
                val parcelAreas = readParcelAreas() ?: return@MoPrimaryButton
                onSave(
                    ActivityDraft(
                        runCatching { ActivityType.valueOf(type) }.getOrDefault(ActivityType.OTHER),
                        runCatching { LocalDate.parse(date) }.getOrNull(),
                        description.ifBlank { chosenType.defaultDescription(detailFields) },
                        selected.map(UUID::fromString).toSet(),
                        notes,
                        buildActivityDetail(
                            runCatching { ActivityType.valueOf(type) }.getOrDefault(ActivityType.OTHER),
                            detailFields,
                            runCatching { LocalDate.parse(date) }.getOrNull(),
                        ),
                        machines = machineUses,
                        planning = planned.planning,
                        reminders = planned.reminders,
                        parcelAreasM2 = parcelAreas,
                        campaignId = selectedCampaign?.id,
                        activityEndDate = parsedEnd,
                    ),
                )
            },
            modifier = Modifier.fillMaxWidth().testTag("save-activity"),
            enabled = !isSaving && !futureDoneWork && !futureDoneEnd && !missingEnd && !endBeforeStart,
        )
        onSaveDraft?.let { saveDraft ->
            MoSecondaryButton(
                "Guardar borrador",
                {
                    val machineUses = readMachines() ?: return@MoSecondaryButton
                    val planned = readPlanning() ?: return@MoSecondaryButton
                    if (!readTypedDetail()) return@MoSecondaryButton
                    if (missingEnd || endBeforeStart) return@MoSecondaryButton
                    val parcelAreas = readParcelAreas() ?: return@MoSecondaryButton
                    saveDraft(
                        ActivityDraft(
                            runCatching { ActivityType.valueOf(type) }.getOrDefault(ActivityType.OTHER),
                            runCatching { LocalDate.parse(date) }.getOrNull(),
                            description.ifBlank { chosenType.defaultDescription(detailFields) },
                            selected.map(UUID::fromString).toSet(),
                            notes,
                            buildActivityDetail(
                                runCatching { ActivityType.valueOf(type) }.getOrDefault(ActivityType.OTHER),
                                detailFields,
                                runCatching { LocalDate.parse(date) }.getOrNull(),
                            ),
                            machines = machineUses,
                            planning = planned.planning,
                            reminders = planned.reminders,
                            parcelAreasM2 = parcelAreas,
                            campaignId = selectedCampaign?.id,
                            activityEndDate = parsedEnd,
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth().testTag("save-activity-draft"),
            )
        }
        MoTertiaryButton("Cancelar", onCancel, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun ActivityDetailRoute(
    activityId: UUID,
    persistence: LocalPersistence,
    /** #426/#435: canonical Workspace day for inline validation of historical corrections. */
    clock: AppClock? = null,
    /** #416: opens Gasto tied to this work (its Farm, its single Parcel, the work itself). */
    onAddRelatedExpense: ((Activity) -> Unit)? = null,
    /** #416: opens one Gasto by id (the historic cost row of this work). */
    onOpenExpense: ((UUID) -> Unit)? = null,
    /** #416: a related Gasto was just saved from this work; said once on return. */
    relatedExpenseAdded: Boolean = false,
    onRelatedExpenseNoticeShown: () -> Unit = {},
) {
    val vm: ActivityDetailViewModel = viewModel(key = "activity-$activityId", factory = viewModelFactory {
        initializer { ActivityDetailViewModel(activityId, persistence.activityRepository) }
    })
    val state by vm.state.collectAsStateWithLifecycle()
    val workspaceId = state.activity?.workspaceId
    var workspaceToday by remember(workspaceId) { mutableStateOf<LocalDate?>(null) }
    LaunchedEffect(workspaceId, clock) {
        workspaceToday = if (workspaceId != null && clock != null) {
            runCatching { persistence.database.todayForWorkspace(workspaceId, clock) }.getOrNull()
        } else {
            null
        }
    }
    // #416: the canonical Expense row of a cost typed on the work before 1.0 (money lives in Gastos).
    val linkedExpenses by remember(activityId) { persistence.expenseRepository.observeForActivity(activityId) }
        .collectAsStateWithLifecycle(emptyList())
    val historicCostId = linkedExpenses.firstOrNull { it.origin == com.isivoltpro.maginaolivo.domain.expense.ExpenseOrigin.ACTIVITY_COST }?.id
    // #416: a work of a closed Campaign cannot take new money until the Campaign is reopened.
    val campaignId = state.activity?.campaignId
    val campaign by remember(campaignId) {
        campaignId?.let(persistence.campaignRepository::observe) ?: kotlinx.coroutines.flow.flowOf(null)
    }.collectAsStateWithLifecycle(null)
    val campaignClosed = campaign?.status == com.isivoltpro.maginaolivo.data.local.model.CampaignStatus.CLOSED
    ActivityDetailScreen(
        state,
        vm::update,
        vm::plan,
        vm::complete,
        vm::cancel,
        vm::reopen,
        vm::archive,
        onCorrect = vm::correct,
        correctionCampaign = campaign,
        today = workspaceToday,
        onAddRelatedExpense = onAddRelatedExpense,
        historicCostExpenseId = historicCostId,
        // #437: Gastos of their own pointing at this work (never its convenience cost).
        relatedExpenses = linkedExpenses.filter { it.origin != com.isivoltpro.maginaolivo.domain.expense.ExpenseOrigin.ACTIVITY_COST },
        onOpenExpense = onOpenExpense,
        campaignClosed = campaignClosed,
        relatedExpenseAdded = relatedExpenseAdded,
        onRelatedExpenseNoticeShown = onRelatedExpenseNoticeShown,
        attachmentContent = {
            AttachmentsRoute(
                owner = AttachmentOwner(AttachmentOwnerType.ACTIVITY, activityId),
                persistence = persistence,
                title = "Fotos y documentos",
            )
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityDetailScreen(
    state: ActivityDetailUiState,
    onUpdate: (ActivityDraft) -> Unit,
    onPlan: () -> Unit,
    onComplete: () -> Unit,
    onCancelActivity: () -> Unit,
    onReopen: () -> Unit,
    onArchive: () -> Unit,
    /** #426: historical correction keeps COMPLETED; null for legacy/test callers. */
    onCorrect: ((ActivityDraft) -> Unit)? = null,
    /** Current Campaign snapshot for a historical HARVEST_DAY correction. */
    correctionCampaign: Campaign? = null,
    /** #435: canonical Workspace day; null only in source-compatible previews/tests. */
    today: LocalDate? = null,
    onAddRelatedExpense: ((Activity) -> Unit)? = null,
    historicCostExpenseId: UUID? = null,
    relatedExpenses: List<com.isivoltpro.maginaolivo.domain.expense.Expense> = emptyList(),
    onOpenExpense: ((UUID) -> Unit)? = null,
    campaignClosed: Boolean = false,
    relatedExpenseAdded: Boolean = false,
    onRelatedExpenseNoticeShown: () -> Unit = {},
    attachmentContent: @Composable () -> Unit = {},
) {
    var confirmation by rememberSaveable { mutableStateOf<String?>(null) }
    // #416: the notice stays a few seconds, then the flag is spent so it never repeats.
    LaunchedEffect(relatedExpenseAdded) {
        if (relatedExpenseAdded) {
            kotlinx.coroutines.delay(RELATED_EXPENSE_NOTICE_MS)
            onRelatedExpenseNoticeShown()
        }
    }
    var editor by rememberSaveable { mutableStateOf(false) }
    OnEachSave(state.saveCount) { editor = false }
    Scaffold(Modifier.fillMaxSize().testTag("activity-detail-root"), containerColor = MoSurfaceTokens.appBackground, contentWindowInsets = WindowInsets(0, 0, 0, 0)) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).statusBarsPadding()
                .verticalScroll(rememberScrollState()).padding(MoSpacing.screen),
            verticalArrangement = Arrangement.spacedBy(MoSpacing.md),
        ) {
            when {
                state.isLoading -> CircularProgressIndicator()
                state.activity == null ->
                    MoErrorState("Trabajo no disponible", state.error ?: "No está guardado en este dispositivo.")
                else -> {
                    val activity = state.activity
                    // UI polish v2: one first card with what the farmer needs at a glance.
                    ActivityHeaderCard(activity)
                    activity.notes?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MoSurfaceTokens.secondaryText) }
                    if (activity.targets.size > 1) {
                        MoSectionHeader("Parcelas afectadas")
                    }
                    activity.targets.forEach { target ->
                        MoCompactListItem(
                            title = target.parcelName,
                            subtitle = target.areaAffectedM2?.let { "Superficie trabajada: ${hectaresLabel(it)}" },
                            icon = MoIcons.Parcels,
                            modifier = Modifier.testTag("activity-target"),
                        )
                    }
                    if (activity.targets.isEmpty()) {
                        MoEmptyState(
                            "Sin parcelas todavía",
                            "Edita el trabajo y elige en qué parcelas se hace.",
                            icon = MoIcons.Parcels,
                        )
                    }

                    activity.detail?.let { ActivityDetailSummary(it) }
                    if (activity.machines.isNotEmpty()) {
                        MoSectionHeader("Uso de maquinaria")
                        activity.machines.forEach { machine ->
                            Text(
                                listOfNotNull(
                                    machine.name + if (machine.archived) " (retirada)" else "",
                                    machine.hoursUsed?.let { "${editableHours(it)} h" },
                                ).joinToString(" · "),
                                modifier = Modifier.testTag("activity-machine"),
                            )
                        }
                    }
                    PlanningSummary(activity.planning, activity.reminders)
                    activity.costMinor?.let { cost ->
                        // #416: a cost typed on the work before 1.0 stays visible, read as what it is.
                        MoSummaryMetric(
                            "Coste histórico vinculado",
                            Money.format(cost),
                            Modifier.fillMaxWidth().testTag("activity-cost-summary"),
                            icon = MoIcons.Euro,
                            supportingText = "Ya contabilizado en Gastos una sola vez",
                        )
                        // #416: the money is corrected on its Expense row, without reopening the work.
                        if (historicCostExpenseId != null && onOpenExpense != null) {
                            MoTertiaryButton(
                                "Ver / corregir gasto histórico",
                                { onOpenExpense(historicCostExpenseId) },
                                modifier = Modifier.fillMaxWidth().testTag("activity-historic-expense"),
                            )
                        }
                    }
                    // #416: money for done work is a Gasto of its own, tied to the work — never a second figure.
                    if (onAddRelatedExpense != null && activity.status == ActivityStatus.COMPLETED && activity.farmId != null) {
                        val historic = activity.costMinor
                        if (relatedExpenseAdded) {
                            Text(
                                "Gasto añadido · queda vinculado a este trabajo en Gastos.",
                                style = MaterialTheme.typography.bodyMedium, color = MoColors.current.primaryText,
                                modifier = Modifier.fillMaxWidth().testTag("activity-expense-added"),
                            )
                        }
                        MoSecondaryButton(
                            if (historic != null) "Añadir otro gasto relacionado" else "Añadir gasto relacionado",
                            { onAddRelatedExpense(activity) },
                            modifier = Modifier.fillMaxWidth().testTag("activity-add-expense"),
                            enabled = !campaignClosed,
                        )
                        when {
                            campaignClosed -> Text(
                                "La campaña está cerrada. Reábrela para añadir gastos de recogida.",
                                style = MaterialTheme.typography.bodySmall, color = MoSurfaceTokens.secondaryText,
                                modifier = Modifier.testTag("activity-add-expense-closed"),
                            )
                            historic != null -> Text(
                                "El coste histórico de ${Money.format(historic)} ya está contabilizado. " +
                                    "Añade otro gasto solo si es un importe distinto.",
                                style = MaterialTheme.typography.bodySmall, color = MoSurfaceTokens.secondaryText,
                                modifier = Modifier.testTag("activity-add-expense-note"),
                            )
                        }
                    }

                    // #429: a counted cost on work that is (or would become) not done is reviewed
                    // on its Gasto first; only completing the work stays open, since it makes it coherent.
                    val costHeld = activity.costMinor != null
                    if (costHeld && activity.status != ActivityStatus.COMPLETED) {
                        CostToReview(
                            "Este trabajo no está hecho, pero tiene un coste contabilizado. Revísalo: consérvalo " +
                                "como gasto independiente si ocurrió, o deja de contabilizarlo.",
                            historicCostExpenseId, onOpenExpense,
                        )
                    }
                    // Principal / secundaria / destructiva — never three large green buttons.
                    when (activity.status) {
                        ActivityStatus.DRAFT -> {
                            MoPrimaryButton("Planificar", { confirmation = "plan" }, modifier = Modifier.fillMaxWidth().testTag("plan-activity"), enabled = !state.isSaving && !costHeld)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MoSpacing.xs)) {
                                MoSecondaryButton("Editar borrador", { editor = true }, modifier = Modifier.weight(1f).testTag("edit-activity"), enabled = !state.isSaving)
                                MoDestructiveButton("Archivar borrador", { confirmation = "archive" }, modifier = Modifier.weight(1f).testTag("archive-activity"), enabled = !costHeld && relatedExpenses.isEmpty())
                            }
                            LinkedExpensesHoldArchive(relatedExpenses, onOpenExpense)
                        }
                        ActivityStatus.PLANNED -> {
                            MoPrimaryButton("Marcar completado", { confirmation = "complete" }, modifier = Modifier.fillMaxWidth().testTag("complete-activity"), enabled = !state.isSaving)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MoSpacing.xs)) {
                                MoSecondaryButton("Editar", { editor = true }, modifier = Modifier.weight(1f).testTag("edit-activity"), enabled = !state.isSaving)
                                MoDestructiveButton("Cancelar trabajo", { confirmation = "cancel" }, modifier = Modifier.weight(1f).testTag("cancel-activity"), enabled = !costHeld)
                            }
                        }
                        ActivityStatus.COMPLETED -> {
                            Text("Trabajo realizado", style = MaterialTheme.typography.titleSmall, color = MoSurfaceTokens.secondaryText)
                            if (onCorrect != null) {
                                val harvestContextMissing =
                                    activity.type == ActivityType.HARVEST_DAY && correctionCampaign == null
                                MoPrimaryButton(
                                    "Corregir datos",
                                    { editor = true },
                                    modifier = Modifier.fillMaxWidth().testTag("correct-activity"),
                                    enabled = !state.isSaving && !campaignClosed && !harvestContextMissing,
                                )
                                if (campaignClosed) {
                                    Text(
                                        "La campaña está cerrada. Reábrela antes de corregir este histórico.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MoSurfaceTokens.secondaryText,
                                        modifier = Modifier.testTag("correct-activity-closed"),
                                    )
                                }
                            }
                            MoSecondaryButton(
                                "Volver a planificar",
                                { confirmation = "reopen" },
                                modifier = Modifier.fillMaxWidth().testTag("reopen-activity"),
                                enabled = !costHeld,
                            )
                            if (costHeld) CostToReview(costToReview("volver a planificarlo"), historicCostExpenseId, onOpenExpense)
                        }
                        ActivityStatus.CANCELLED -> {
                            Text("Trabajo cancelado", style = MaterialTheme.typography.titleSmall, color = MoSurfaceTokens.secondaryText)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MoSpacing.xs)) {
                                MoSecondaryButton("Reabrir", { confirmation = "reopen" }, modifier = Modifier.weight(1f).testTag("reopen-activity"), enabled = !costHeld)
                                MoDestructiveButton("Archivar", { confirmation = "archive" }, modifier = Modifier.weight(1f).testTag("archive-activity"), enabled = !costHeld && relatedExpenses.isEmpty())
                            }
                            LinkedExpensesHoldArchive(relatedExpenses, onOpenExpense)
                        }
                    }
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    // #441: the Gastos that keep a Parcel in the work, one tap away.
                    if (state.error == PARCEL_HAS_EXPENSES_TEXT) {
                        LinkedExpensesList(relatedExpenses.filter { it.parcelId != null }, onOpenExpense)
                    }
                    attachmentContent()
                }
            }
        }
    }
    val activity = state.activity
    if (editor && activity != null) {
        ModalBottomSheet(onDismissRequest = { editor = false }) {
            val correctionParcels = (
                state.parcels + activity.targets
                    .filter { target -> state.parcels.none { it.id == target.parcelId } }
                    .map { target -> ActivityParcelOption(target.parcelId, target.parcelName, null) }
            ).distinctBy { it.id }
            ActivityEditor(
                parcels = correctionParcels,
                campaigns = listOfNotNull(correctionCampaign),
                descriptionError = null,
                dateError = null,
                // #441: said where the Parcels are chosen; the Gastos to review are listed below the work.
                parcelsError = state.error?.takeIf { it == PARCEL_HAS_EXPENSES_TEXT },
                // Codex #530: the Gastos to review are one tap away inside the editor itself.
                parcelsErrorContent = {
                    LinkedExpensesList(relatedExpenses.filter { it.parcelId != null }) { id ->
                        editor = false
                        onOpenExpense?.invoke(id)
                    }
                },
                isSaving = state.isSaving,
                onSave = { draft ->
                    if (activity.status == ActivityStatus.COMPLETED && onCorrect != null) onCorrect(draft)
                    else onUpdate(draft)
                },
                onCancel = { editor = false },
                initial = ActivityDraft(
                    type = activity.type,
                    activityDate = activity.activityDate,
                    description = activity.description,
                    parcelIds = activity.targets.map { it.parcelId }.toSet(),
                    notes = activity.notes.orEmpty(),
                    parcelAreasM2 = activity.targets.associate { it.parcelId to it.areaAffectedM2 },
                    detail = activity.detail,
                    machines = activity.machines.map { MachineUseInput(it.machineId, it.startHours, it.endHours, it.usageHours) },
                    planning = activity.planning,
                    reminders = activity.reminders.map { it.toRequest() },
                    campaignId = activity.campaignId,
                    activityEndDate = activity.activityEndDate,
                ),
                title = if (activity.status == ActivityStatus.COMPLETED) "Corregir registro" else "Editar trabajo",
                // #426: a historical correction does not expose planning/reminder controls.
                doneWork = activity.status == ActivityStatus.COMPLETED,
                // A retired machine the Activity already named stays choosable here only.
                machines = state.machines + activity.machines.filter { it.archived }
                    .map { MachineOption(it.machineId, "${it.name} (retirada)", it.category) },
                today = today,
            )
        }
    }
    if (confirmation != null) {
        ModalBottomSheet(onDismissRequest = { confirmation = null }) {
            Column(
                Modifier.fillMaxWidth().padding(MoSpacing.screen),
                verticalArrangement = Arrangement.spacedBy(MoSpacing.md),
            ) {
                Text("Confirmar cambio", style = MaterialTheme.typography.headlineSmall)
                Text(
                    if (confirmation == "reopen") {
                        "Volverá a quedar pendiente y aparecerá otra vez en Avisos. Úsalo solo si el trabajo realmente debe volver a planificarse."
                    } else {
                        "Esta acción actualizará el estado del trabajo guardado en este dispositivo."
                    },
                    color = MoSurfaceTokens.secondaryText,
                )
                val confirm = {
                    when (confirmation) {
                        "plan" -> onPlan()
                        "complete" -> onComplete()
                        "cancel" -> onCancelActivity()
                        "reopen" -> onReopen()
                        "archive" -> onArchive()
                    }
                    confirmation = null
                }
                if (confirmation == "cancel" || confirmation == "archive") {
                    MoDestructiveButton("Confirmar", confirm, modifier = Modifier.fillMaxWidth().testTag("confirm-activity-action"))
                } else {
                    MoPrimaryButton("Confirmar", confirm, modifier = Modifier.fillMaxWidth().testTag("confirm-activity-action"))
                }
                MoTertiaryButton("Volver", { confirmation = null }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

private fun Activity.targetsLabel(): String = when (targets.size) {
    0 -> "Sin parcelas"
    1 -> targets.single().parcelName
    else -> "${targets.size} parcelas"
}

/**
 * The typed agronomic block, and only the one that belongs to the chosen type.
 *
 * This is what keeps the editor from becoming a giant form: the common header is always
 * there, and underneath it exactly one block appears — pruning, fertilisation, treatment,
 * soil work, irrigation, maintenance or incident. Observation and Other show none,
 * because the contract gives them no structured fields to show.
 */
@Composable
private fun ActivityTypedDetailFields(
    type: ActivityType,
    fields: SnapshotStateMap<String, String>,
    errors: SnapshotStateMap<String, String> = remember { mutableStateMapOf() },
) {
    if (!type.hasTypedDetail()) return
    // #414: what the record held when the editor opened. A retired field stays editable only there.
    val stored = remember { fields.filterValues { it.isNotBlank() }.keys.toSet() }
    val layout = detailLayout(type)
    // A retired field is shown while it holds a stored value, or while it carries an error to fix.
    val retired = layout.retired.filter { it in stored || it in errors }
    var advancedOpen by rememberSaveable(type) { mutableStateOf(layout.advanced.any { it in stored }) }
    MoSectionHeader(type.detailSectionTitle())
    Column(
        Modifier.fillMaxWidth().testTag("activity-detail-block"),
        verticalArrangement = Arrangement.spacedBy(MoSpacing.sm),
    ) {
        layout.visible.forEach { key -> DetailInput(fields, key, errors) }
        if (layout.advanced.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .clickable(role = Role.Button) { advancedOpen = !advancedOpen }
                    .testTag("activity-detail-advanced"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MoSpacing.xs),
            ) {
                Icon(if (advancedOpen) MoIcons.ChevronDown else MoIcons.ChevronRight, contentDescription = null, tint = MoColors.current.actionText)
                Text("Más detalles", style = MaterialTheme.typography.titleSmall, color = MoColors.current.actionText)
            }
            if (advancedOpen || layout.advanced.any { it in errors }) layout.advanced.forEach { key -> DetailInput(fields, key, errors) }
        }
        if (retired.isNotEmpty()) {
            // Kept so an older record loses nothing; new records no longer ask for these.
            Text("Datos anteriores de este registro", style = MaterialTheme.typography.titleSmall, color = MoSurfaceTokens.secondaryText)
            retired.forEach { key -> DetailInput(fields, key, errors) }
        }
    }
}

/**
 * #414: which typed fields a form shows first, which wait behind «Más detalles», and which new
 * records no longer ask for (Jornal holds people and hours, Maquinaria the equipment, and the
 * Expense ledger the money), shown only while an older record still holds a value.
 */
internal data class DetailLayout(val visible: List<String>, val advanced: List<String> = emptyList(), val retired: List<String> = emptyList())

internal fun detailLayout(type: ActivityType): DetailLayout = when (type) {
    ActivityType.PRUNING -> DetailLayout(
        visible = listOf(ActivityDetailFields.PRUNING_TYPE),
        advanced = listOf(ActivityDetailFields.RESIDUE_MANAGEMENT),
        retired = listOf(ActivityDetailFields.WORKER_COUNT, ActivityDetailFields.HOURS),
    )
    ActivityType.FERTILIZATION -> DetailLayout(
        visible = listOf(ActivityDetailFields.PRODUCT_NAME, ActivityDetailFields.DOSE_VALUE, ActivityDetailFields.DOSE_UNIT),
        advanced = listOf(ActivityDetailFields.TOTAL_QUANTITY, ActivityDetailFields.UNIT, ActivityDetailFields.APPLICATION_METHOD),
    )
    ActivityType.PHYTOSANITARY -> DetailLayout(
        visible = listOf(ActivityDetailFields.PRODUCT_NAME, ActivityDetailFields.DOSE_VALUE, ActivityDetailFields.DOSE_UNIT, ActivityDetailFields.REASON),
        advanced = listOf(ActivityDetailFields.ACTIVE_SUBSTANCE, ActivityDetailFields.TOTAL_QUANTITY, ActivityDetailFields.UNIT),
        retired = listOf(ActivityDetailFields.EQUIPMENT_TEXT),
    )
    ActivityType.SOIL_WORK -> DetailLayout(visible = listOf(ActivityDetailFields.WORK_TYPE, ActivityDetailFields.METHOD))
    ActivityType.IRRIGATION -> DetailLayout(
        visible = listOf(ActivityDetailFields.DURATION_MINUTES, ActivityDetailFields.VOLUME_M3, ActivityDetailFields.SECTOR_TEXT),
        advanced = listOf(ActivityDetailFields.SYSTEM_TEXT),
        // A historical tariff snapshot, never summed into money: the Expense ledger is the cost.
        retired = listOf(
            ActivityDetailFields.PRICE_BASIS, ActivityDetailFields.UNIT_PRICE,
            ActivityDetailFields.PRICED_QUANTITY, ActivityDetailFields.PRICE_DATE,
        ),
    )
    ActivityType.MAINTENANCE -> DetailLayout(visible = listOf(ActivityDetailFields.MAINTENANCE_TYPE, ActivityDetailFields.ASSET_TEXT))
    ActivityType.INCIDENT -> DetailLayout(
        visible = listOf(
            ActivityDetailFields.CATEGORY, ActivityDetailFields.SEVERITY,
            ActivityDetailFields.INCIDENT_STATE, ActivityDetailFields.ACTION_TAKEN,
        ),
    )
    ActivityType.OBSERVATION, ActivityType.OTHER, ActivityType.HARVEST_DAY -> DetailLayout(emptyList())
}

/** One typed input: a choice for the closed lists, a text field for the rest. */
@Composable
private fun DetailInput(fields: SnapshotStateMap<String, String>, key: String, errors: SnapshotStateMap<String, String>) {
    when (key) {
        ActivityDetailFields.SEVERITY -> {
            Text("Gravedad", style = MaterialTheme.typography.titleSmall)
            DetailChoice(fields, key, IncidentSeverity.entries.map { it.name to it.label() })
        }
        ActivityDetailFields.INCIDENT_STATE -> {
            Text("Estado", style = MaterialTheme.typography.titleSmall)
            DetailChoice(fields, key, IncidentState.entries.map { it.name to it.label() })
        }
        ActivityDetailFields.PRICE_BASIS -> {
            Text("Tarifa (histórica)", style = MaterialTheme.typography.titleSmall)
            DetailChoice(fields, key, IrrigationPricingBasis.entries.map { it.name to it.label() }, errors)
            errors[key]?.let { message ->
                Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("detail-$key-error"))
            }
        }
        else -> DetailField(fields, key, detailInputLabel(key), errors)
    }
}

private fun detailInputLabel(key: String): String = when (key) {
    ActivityDetailFields.DURATION_MINUTES -> "Duración (minutos)"
    ActivityDetailFields.UNIT_PRICE -> "Precio unitario (€)"
    ActivityDetailFields.PRICED_QUANTITY -> "Cantidad facturada"
    ActivityDetailFields.PRICE_DATE -> "Fecha de tarifa (AAAA-MM-DD)"
    ActivityDetailFields.ASSET_TEXT -> "Elemento o equipo"
    ActivityDetailFields.EQUIPMENT_TEXT -> "Equipo"
    ActivityDetailFields.WORKER_COUNT -> "Nº de operarios"
    ActivityDetailFields.ACTION_TAKEN -> "Actuación realizada"
    else -> key.detailFieldLabel()
}

@Composable
private fun DetailField(
    fields: SnapshotStateMap<String, String>,
    key: String,
    label: String,
    errors: SnapshotStateMap<String, String>? = null,
) {
    // #473: the typed text stays on screen with its error; it is never cleared or guessed.
    val error = errors?.get(key)
    MoTextField(
        fields[key].orEmpty(),
        { fields[key] = it; errors?.remove(key) },
        label,
        isError = error != null,
        supportingText = error,
        modifier = Modifier.fillMaxWidth().testTag("detail-$key"),
    )
}

@Composable
private fun DetailChoice(
    fields: SnapshotStateMap<String, String>,
    key: String,
    options: List<Pair<String, String>>,
    errors: SnapshotStateMap<String, String>? = null,
) {
    options.forEach { (value, label) ->
        val checked = fields[key] == value
        val choose = { fields[key] = value; errors?.remove(key); Unit }
        Row(
            Modifier.fillMaxWidth().testTag("detail-$key-option")
                .clickable(role = Role.Checkbox) { choose() }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked, { choose() })
            Text(label)
        }
    }
}

/** Shows the typed block of an Activity that already has one, without a second header. */
@Composable
private fun ActivityDetailSummary(detail: ActivityDetail) {
    MoSectionHeader(detail.type.detailSectionTitle())
    Column(
        Modifier.fillMaxWidth().testTag("activity-detail-summary"),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        detail.toFields().forEach { (key, value) ->
            Text(
                "${key.detailFieldLabel()}: ${value.detailValueLabel()}",
                style = MaterialTheme.typography.bodyMedium,
                color = MoSurfaceTokens.secondaryText,
                modifier = Modifier.testTag("activity-detail-value"),
            )
        }
    }
}

private fun ActivityType.detailSectionTitle() = when (this) {
    ActivityType.PRUNING -> "Datos de poda"
    ActivityType.FERTILIZATION -> "Datos de abonado"
    ActivityType.PHYTOSANITARY -> "Datos del tratamiento"
    ActivityType.SOIL_WORK -> "Datos de la labor"
    ActivityType.IRRIGATION -> "Datos de riego"
    ActivityType.MAINTENANCE -> "Datos de mantenimiento"
    ActivityType.INCIDENT -> "Datos de la incidencia"
    ActivityType.OBSERVATION, ActivityType.OTHER, ActivityType.HARVEST_DAY -> "Datos"
}

private fun String.detailFieldLabel() = when (this) {
    ActivityDetailFields.PRUNING_TYPE -> "Tipo de poda"
    ActivityDetailFields.WORKER_COUNT -> "Operarios"
    ActivityDetailFields.HOURS -> "Horas"
    ActivityDetailFields.RESIDUE_MANAGEMENT -> "Gestión de restos"
    ActivityDetailFields.PRODUCT_NAME -> "Producto"
    ActivityDetailFields.ACTIVE_SUBSTANCE -> "Materia activa"
    ActivityDetailFields.TOTAL_QUANTITY -> "Cantidad total"
    ActivityDetailFields.UNIT -> "Unidad"
    ActivityDetailFields.DOSE_VALUE -> "Dosis"
    ActivityDetailFields.DOSE_UNIT -> "Unidad de dosis"
    ActivityDetailFields.APPLICATION_METHOD -> "Método de aplicación"
    ActivityDetailFields.REASON -> "Motivo"
    ActivityDetailFields.EQUIPMENT_TEXT -> "Equipo"
    ActivityDetailFields.WORK_TYPE -> "Tipo de labor"
    ActivityDetailFields.METHOD -> "Método"
    ActivityDetailFields.DURATION_MINUTES -> "Duración (min)"
    ActivityDetailFields.VOLUME_M3 -> "Volumen (m³)"
    ActivityDetailFields.SECTOR_TEXT -> "Sector"
    ActivityDetailFields.SYSTEM_TEXT -> "Sistema"
    ActivityDetailFields.PRICE_BASIS -> "Base de tarifa"
    ActivityDetailFields.UNIT_PRICE -> "Precio unitario (€)"
    ActivityDetailFields.PRICED_QUANTITY -> "Cantidad facturada"
    ActivityDetailFields.PRICE_DATE -> "Fecha de tarifa"
    ActivityDetailFields.MAINTENANCE_TYPE -> "Tipo de mantenimiento"
    ActivityDetailFields.ASSET_TEXT -> "Elemento"
    ActivityDetailFields.CATEGORY -> "Categoría"
    ActivityDetailFields.SEVERITY -> "Gravedad"
    ActivityDetailFields.INCIDENT_STATE -> "Estado"
    ActivityDetailFields.ACTION_TAKEN -> "Actuación"
    else -> this
}

private fun String.detailValueLabel(): String =
    runCatching { IncidentSeverity.valueOf(this).label() }
        .recoverCatching { IncidentState.valueOf(this).label() }
        .recoverCatching { IrrigationPricingBasis.valueOf(this).label() }
        .getOrDefault(this)

private fun IncidentSeverity.label() = when (this) {
    IncidentSeverity.LOW -> "Baja"
    IncidentSeverity.MEDIUM -> "Media"
    IncidentSeverity.HIGH -> "Alta"
    IncidentSeverity.CRITICAL -> "Crítica"
}

private fun IncidentState.label() = when (this) {
    IncidentState.OPEN -> "Abierta"
    IncidentState.MONITORING -> "En observación"
    IncidentState.RESOLVED -> "Resuelta"
}

private fun IrrigationPricingBasis.label() = when (this) {
    IrrigationPricingBasis.PER_M3 -> "Por m³"
    IrrigationPricingBasis.PER_HOUR -> "Por hora"
    IrrigationPricingBasis.PER_EVENT -> "Por riego"
    IrrigationPricingBasis.PER_HECTARE -> "Por hectárea"
    IrrigationPricingBasis.INVOICE_TOTAL -> "Total factura"
    IrrigationPricingBasis.OTHER -> "Otra"
}

internal fun ActivityStatus.label() = when (this) {
    ActivityStatus.DRAFT -> "Borrador"
    ActivityStatus.PLANNED -> "Planificada"
    ActivityStatus.COMPLETED -> "Completada"
    ActivityStatus.CANCELLED -> "Cancelada"
}

internal fun ActivityStatus.tone() = when (this) {
    ActivityStatus.DRAFT -> MoStatusTone.Neutral
    ActivityStatus.PLANNED -> MoStatusTone.Info
    ActivityStatus.COMPLETED -> MoStatusTone.Success
    ActivityStatus.CANCELLED -> MoStatusTone.Warning
}

/** #414: Observación and Otro say nothing by themselves, so they still ask for a description. */
internal fun ActivityType.needsDescription(): Boolean = this == ActivityType.OBSERVATION || this == ActivityType.OTHER

/** #414: the title a typed work takes when the farmer adds no detail; empty when words are still needed. */
internal fun ActivityType.defaultDescription(fields: Map<String, String> = emptyMap()): String = when {
    needsDescription() -> ""
    // #414: an Incidencia with neither category nor detail says nothing; it is not saved anonymous.
    this == ActivityType.INCIDENT && fields[ActivityDetailFields.CATEGORY].isNullOrBlank() -> ""
    else -> label()
}

internal fun ActivityType.label() = when (this) {
    ActivityType.OBSERVATION -> "Observación"
    ActivityType.PRUNING -> "Poda"
    ActivityType.SOIL_WORK -> "Labores de suelo"
    ActivityType.FERTILIZATION -> "Abonado"
    ActivityType.PHYTOSANITARY -> "Tratamiento"
    ActivityType.IRRIGATION -> "Riego"
    ActivityType.MAINTENANCE -> "Mantenimiento"
    ActivityType.INCIDENT -> "Incidencia"
    ActivityType.OTHER -> "Otro"
    ActivityType.HARVEST_DAY -> "Jornada de recolección"
}

/** "3", "3,5": hours as a farmer writes them. */
internal fun editableHours(hours: Double): String =
    java.math.BigDecimal.valueOf(hours).stripTrailingZeros().toPlainString().replace('.', ',')

/**
 * UI polish v2: the Activity's first card — type, state, date, farm parcels, planned
 * hour/duration and reminder — so the detail reads in one glance.
 */
@Composable
private fun ActivityHeaderCard(activity: Activity) {
    androidx.compose.material3.Surface(
        modifier = Modifier.fillMaxWidth().testTag("activity-header"),
        shape = com.isivoltpro.maginaolivo.ui.theme.MoShape.card,
        color = MoSurfaceTokens.cardSurface,
        border = BorderStroke(1.dp, MoSurfaceTokens.cardStroke),
    ) {
        Column(Modifier.padding(MoSpacing.md), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MoSpacing.xs), verticalAlignment = Alignment.CenterVertically) {
                Text(activity.description, style = MaterialTheme.typography.headlineMedium, color = MoColors.current.primaryText, modifier = Modifier.weight(1f))
                MoStatusChip(activity.status.label(), tone = activity.status.tone())
            }
            HeaderLine(activity.type.icon(), activity.type.label())
            HeaderLine(MoIcons.Calendar, activity.headerDateLabel())
            activity.targets.takeIf { it.isNotEmpty() }?.let { targets ->
                HeaderLine(MoIcons.Parcels, if (targets.size == 1) targets.single().parcelName else "${targets.size} parcelas")
            }
            // Time, people and reminders are told once, in the Planificación block below.
        }
    }
}

@Composable
private fun HeaderLine(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MoSpacing.xs)) {
        androidx.compose.material3.Icon(icon, contentDescription = null, tint = com.isivoltpro.maginaolivo.ui.components.MoIconTone.of(icon).tint, modifier = Modifier.size(18.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MoColors.current.bodyText)
    }
}

private val SPANISH_LOCALE: java.util.Locale = java.util.Locale.forLanguageTag("es-ES")
private val ROW_DATE: java.time.format.DateTimeFormatter = java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", SPANISH_LOCALE)
private val HEADER_DATE: java.time.format.DateTimeFormatter = java.time.format.DateTimeFormatter.ofPattern("EEEE d 'de' MMMM yyyy", SPANISH_LOCALE)
private val RANGE_END_DATE: java.time.format.DateTimeFormatter = java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", SPANISH_LOCALE)

internal fun Activity.compactDateLabel(): String =
    activityEndDate?.let { "${activityDate.format(ROW_DATE)}–${it.format(RANGE_END_DATE)}" }
        ?: activityDate.format(ROW_DATE)

internal fun Activity.headerDateLabel(): String {
    val start = activityDate.format(HEADER_DATE).replaceFirstChar { it.titlecase(SPANISH_LOCALE) }
    return activityEndDate?.let { "$start · hasta ${it.format(HEADER_DATE)}" } ?: start
}

private fun hectaresLabel(areaM2: Double): String =
    "${java.text.NumberFormat.getNumberInstance(SPANISH_LOCALE).apply { maximumFractionDigits = 2 }.format(areaM2 / 10_000)} ha"

private fun editableAreaHa(areaM2: Double): String =
    java.math.BigDecimal.valueOf(areaM2 / 10_000.0).stripTrailingZeros().toPlainString().replace('.', ',')

/** A small label over a group of chips, lighter than a section header. */
@Composable
private fun FormLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MoColors.current.primaryText, modifier = Modifier.padding(top = MoSpacing.xs))
}

/**
 * #437: Gastos of their own still point at this work, so it is not archived: it stays (cancelled
 * work already leaves the Diario) and each Gasto is one tap away. Nothing is unlinked or removed.
 */
@Composable
private fun LinkedExpensesHoldArchive(
    expenses: List<com.isivoltpro.maginaolivo.domain.expense.Expense>,
    onOpenExpense: ((UUID) -> Unit)?,
) {
    if (expenses.isEmpty()) return
    Text(
        LINKED_EXPENSES_TEXT,
        style = MaterialTheme.typography.bodySmall, color = MoSurfaceTokens.secondaryText,
        modifier = Modifier.fillMaxWidth().testTag("activity-linked-expenses-note"),
    )
    LinkedExpensesList(expenses, onOpenExpense)
}

/** #437/#441: each Gasto tied to the work, opened in one tap. */
@Composable
private fun LinkedExpensesList(
    expenses: List<com.isivoltpro.maginaolivo.domain.expense.Expense>,
    onOpenExpense: ((UUID) -> Unit)?,
) {
    if (expenses.isNotEmpty() && onOpenExpense != null) {
        Text("Ver gastos vinculados", style = MaterialTheme.typography.titleSmall, color = MoColors.current.primaryText)
        expenses.forEach { expense ->
            MoTertiaryButton(
                "${expense.concept} · ${Money.format(expense.amountMinor, expense.currency)}",
                { onOpenExpense(expense.id) },
                modifier = Modifier.fillMaxWidth().testTag("activity-linked-expense"),
            )
        }
    }
}

/** #429: why a move is held back, and the way to the Gasto where it is resolved. */
@Composable
private fun CostToReview(text: String, expenseId: UUID?, onOpenExpense: ((UUID) -> Unit)?) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall, color = MoSurfaceTokens.secondaryText,
        modifier = Modifier.fillMaxWidth().testTag("activity-cost-to-review"),
    )
    if (expenseId != null && onOpenExpense != null) {
        MoTertiaryButton(
            "Revisar gasto vinculado",
            { onOpenExpense(expenseId) },
            modifier = Modifier.fillMaxWidth().testTag("activity-review-cost"),
        )
    }
}

private const val RELATED_EXPENSE_NOTICE_MS = 4_000L
