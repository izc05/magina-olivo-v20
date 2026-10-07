package com.isivoltpro.maginaolivo.feature.activities

import com.isivoltpro.maginaolivo.domain.machinery.MachineOption
import com.isivoltpro.maginaolivo.domain.machinery.MachineUseInput
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.isivoltpro.maginaolivo.core.common.AppError
import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.data.local.model.ActivityStatus
import com.isivoltpro.maginaolivo.data.local.model.CampaignStatus
import com.isivoltpro.maginaolivo.domain.activity.Activity
import com.isivoltpro.maginaolivo.domain.activity.ActivityChanges
import com.isivoltpro.maginaolivo.domain.activity.ActivityCostRules
import com.isivoltpro.maginaolivo.domain.activity.ActivityDetail
import com.isivoltpro.maginaolivo.domain.activity.ActivityParcelOption
import com.isivoltpro.maginaolivo.domain.activity.ActivityRepository
import com.isivoltpro.maginaolivo.domain.activity.ActivityType
import com.isivoltpro.maginaolivo.domain.activity.NewActivity
import com.isivoltpro.maginaolivo.domain.campaign.Campaign
import com.isivoltpro.maginaolivo.domain.campaign.CampaignRepository
import com.isivoltpro.maginaolivo.domain.farm.Farm
import com.isivoltpro.maginaolivo.domain.farm.FarmRepository
import com.isivoltpro.maginaolivo.domain.workspace.WorkspaceRepository
import com.isivoltpro.maginaolivo.domain.agenda.ActivityPlanning
import com.isivoltpro.maginaolivo.domain.agenda.ReminderRequest
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ActivityDraft(
    val type: ActivityType = ActivityType.OBSERVATION,
    val activityDate: LocalDate? = null,
    val description: String = "",
    val parcelIds: Set<UUID> = emptySet(),
    val notes: String = "",
    /** The typed agronomic block of [type], built from the form the editor showed. */
    val detail: ActivityDetail? = null,
    /** Optional machines used (Phase 15). */
    val machines: List<MachineUseInput> = emptyList(),
    /** Optional planning: hour, duration, people, crew (Phase 16). */
    val planning: ActivityPlanning? = null,
    /** Optional local reminders (Phase 16). */
    val reminders: List<ReminderRequest> = emptyList(),
    /** Confirmed/suggested affected surface per selected Parcel, in square metres. */
    val parcelAreasM2: Map<UUID, Double?> = emptyMap(),
    /** #482: required for HARVEST_DAY; null for ordinary Farm work. */
    val campaignId: UUID? = null,
    /** Inclusive end of a multi-day agronomic operation; null means one day. */
    val activityEndDate: LocalDate? = null,
)

data class FarmActivitiesUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val drafts: List<Activity> = emptyList(),
    val planned: List<Activity> = emptyList(),
    val history: List<Activity> = emptyList(),
    val parcels: List<ActivityParcelOption> = emptyList(),
    val machines: List<MachineOption> = emptyList(),
    /** Campaigns this Farm may use for a planned harvest appointment. */
    val campaigns: List<Campaign> = emptyList(),
    val campaignError: String? = null,
    val descriptionError: String? = null,
    val dateError: String? = null,
    val parcelsError: String? = null,
    val error: String? = null,
    val message: String? = null,
    /** #380: finished saves; the editor closes when this rises. */
    val saveCount: Int = 0,
)

class FarmActivitiesViewModel(
    private val farmId: UUID,
    private val repository: ActivityRepository,
    private val campaignRepository: CampaignRepository? = null,
    /** #435: canonical day of the active Workspace. Null only in non-writing legacy/test callers. */
    private val today: LocalDate? = null,
) : ViewModel() {
    private val mutableState = MutableStateFlow(FarmActivitiesUiState())
    val state: StateFlow<FarmActivitiesUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeForFarm(farmId).collect { activities ->
                mutableState.value = mutableState.value.copy(
                    isLoading = false,
                    drafts = activities.filter { it.status == ActivityStatus.DRAFT },
                    planned = activities.filter { it.status == ActivityStatus.PLANNED },
                    history = activities.filter {
                        it.status == ActivityStatus.COMPLETED || it.status == ActivityStatus.CANCELLED
                    },
                )
            }
        }
        viewModelScope.launch {
            repository.observeSelectableParcels(farmId).collect {
                mutableState.value = mutableState.value.copy(parcels = it)
            }
        }
        viewModelScope.launch {
            repository.observeSelectableMachines().collect {
                mutableState.value = mutableState.value.copy(machines = it)
            }
        }
        campaignRepository?.let { campaigns ->
            viewModelScope.launch {
                campaigns.observeForFarm(farmId).collect { rows ->
                    mutableState.value = mutableState.value.copy(
                        campaigns = rows.filter { it.status != CampaignStatus.CLOSED },
                    )
                }
            }
        }
    }

    /** A planned activity requires at least one Parcel; a resumable draft does not. */
    fun create(draft: ActivityDraft, asDraft: Boolean = false, completeImmediately: Boolean = false) {
        val harvestCampaignId = if (draft.type == ActivityType.HARVEST_DAY) {
            draft.campaignId ?: mutableState.value.campaigns.singleOrNull()?.id
        } else {
            draft.campaignId
        }
        if (!validate(draft.copy(campaignId = harvestCampaignId), asDraft, completeImmediately)) return
        mutate("Trabajo guardado en este dispositivo") {
            when (
                val result = repository.create(
                    NewActivity(
                        farmId = farmId,
                        campaignId = harvestCampaignId,
                        type = draft.type,
                        activityDate = draft.activityDate!!,
                        description = draft.description.trim(),
                        parcelIds = draft.parcelIds,
                        notes = draft.notes.nullIfBlank(),
                        asDraft = asDraft,
                        completeImmediately = completeImmediately && !asDraft,
                        detail = draft.detail,
                        machines = draft.machines,
                        planning = draft.planning,
                        reminders = draft.reminders,
                        parcelAreasM2 = draft.parcelAreasM2,
                        activityEndDate = draft.activityEndDate,
                    ),
                )
            ) {
                is AppResult.Success -> AppResult.Success(Unit)
                is AppResult.Failure -> result
            }
        }
    }

    fun consumeMessage() { mutableState.value = mutableState.value.copy(message = null) }

    private fun validate(draft: ActivityDraft, asDraft: Boolean, completeImmediately: Boolean = false): Boolean {
        val campaignError = when {
            draft.type != ActivityType.HARVEST_DAY -> null
            draft.campaignId != null -> null
            mutableState.value.campaigns.isEmpty() ->
                "Crea o prepara una campaña antes de planificar una jornada de recolección."
            else -> "Elige la campaña de esta jornada de recolección."
        }
        val descriptionError = when {
            !draft.description.isBlank() -> null
            draft.type == ActivityType.INCIDENT -> "Indica la categoría o un detalle breve"
            else -> "Describe el trabajo"
        }
        val dateError = when {
            draft.activityDate == null -> "Selecciona una fecha"
            // #435/#414: the Cuaderno records facts; a date ahead is planned from Avisos.
            isFutureDoneWork(
                activityDate = draft.activityDate,
                today = today,
                doneWork = completeImmediately && !asDraft,
                type = draft.type,
            ) -> FUTURE_DONE_WORK
            else -> null
        }
        val parcelsError =
            if (!asDraft && draft.parcelIds.isEmpty()) "Selecciona al menos una parcela" else null
        mutableState.value = mutableState.value.copy(
            campaignError = campaignError,
            descriptionError = descriptionError,
            dateError = dateError,
            parcelsError = parcelsError,
        )
        return campaignError == null && descriptionError == null && dateError == null && parcelsError == null
    }

    private fun mutate(message: String, operation: suspend () -> AppResult<Unit>) = viewModelScope.launch {
        mutableState.value = mutableState.value.copy(isSaving = true, error = null, message = null)
        mutableState.value = when (val result = operation()) {
            is AppResult.Success -> mutableState.value.copy(
                isSaving = false,
                message = message,
                saveCount = mutableState.value.saveCount + 1,
                campaignError = null,
            )
            is AppResult.Failure -> mutableState.value.copy(
                isSaving = false,
                error = activitySaveErrorMessage(result.error),
                campaignError = if (
                    result.error is AppError.Validation && result.error.field == "campaignId"
                ) activitySaveErrorMessage(result.error) else mutableState.value.campaignError,
            )
        }
    }
}

internal fun activitySaveErrorMessage(error: AppError): String = when {
    error is AppError.Validation &&
        error.field == "campaignId" && error.code == "required_for_harvest_day" ->
        "Crea o prepara una campaña antes de planificar una jornada de recolección."
    error is AppError.Validation &&
        error.field == "campaignId" && error.code == "closed" ->
        "Esa campaña está cerrada. Reábrela o elige otra campaña para planificar la jornada."
    error is AppError.Validation &&
        error.field == "campaignId" ->
        "La campaña elegida ya no es válida para esta finca."
    error is AppError.Validation &&
        error.field == "activityDate" && error.code == "before_campaign" ->
        "La jornada no puede ser anterior al inicio de la campaña."
    error is AppError.Validation &&
        error.field == "parcelIds" && error.code == "not_in_campaign" ->
        "Alguna parcela elegida no forma parte de esta campaña."
    else -> "No se pudo guardar en este dispositivo"
}

/**
 * Backs the "Registrar actuación" entry point of the Registrar (+) sheet.
 *
 * An Activity always belongs to a Farm, so the global entry point has to resolve one
 * before the real editor can be shown. With a single Farm there is nothing to ask.
 */
data class RegisterActivityUiState(
    val isLoading: Boolean = true,
    val farms: List<Farm> = emptyList(),
    val selectedFarmId: UUID? = null,
    /** #498: a Farm brought by navigation is context, not a free selection. */
    val contextualFarmId: UUID? = null,
    val contextualFarmAccepted: Boolean = false,
    val error: String? = null,
)

class RegisterActivityViewModel(
    private val farmRepository: FarmRepository,
    private val workspaceRepository: WorkspaceRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(RegisterActivityUiState())
    val state: StateFlow<RegisterActivityUiState> = mutableState.asStateFlow()
    private var observationJob: Job? = null

    init {
        load()
    }

    fun retry() = load()

    /** Global entry: the farmer is explicitly choosing a Farm now. */
    fun selectFarm(farmId: UUID) {
        mutableState.value = mutableState.value.copy(
            selectedFarmId = farmId,
            contextualFarmId = null,
            contextualFarmAccepted = false,
            error = null,
        )
    }

    /** Context entry: never substitute another Farm if this one is no longer active. */
    fun preselectFarm(farmId: UUID) {
        val current = mutableState.value
        if (current.isLoading) {
            mutableState.value = current.copy(
                selectedFarmId = farmId,
                contextualFarmId = farmId,
                contextualFarmAccepted = false,
                error = null,
            )
            return
        }
        val accepted = current.farms.any { it.id == farmId }
        mutableState.value = current.copy(
            selectedFarmId = farmId.takeIf { accepted },
            contextualFarmId = farmId,
            contextualFarmAccepted = accepted,
            error = if (accepted) null else CONTEXT_FARM_MISSING,
        )
    }

    fun changeFarm() {
        mutableState.value = mutableState.value.copy(
            selectedFarmId = null,
            contextualFarmId = null,
            contextualFarmAccepted = false,
            error = null,
        )
    }

    private fun load() {
        observationJob?.cancel()
        mutableState.value = mutableState.value.copy(isLoading = true, error = null, contextualFarmAccepted = false)
        observationJob = viewModelScope.launch {
            when (val workspace = workspaceRepository.ensureLocalWorkspace()) {
                is AppResult.Failure -> mutableState.value = mutableState.value.copy(
                    isLoading = false,
                    error = "No se pudo abrir el almacenamiento de este dispositivo",
                )
                is AppResult.Success -> farmRepository.observeActive(workspace.value).collect { farms ->
                    val current = mutableState.value
                    val contextual = current.contextualFarmId
                    val contextAccepted = contextual != null && farms.any { it.id == contextual }
                    val selected = when {
                        contextual != null -> contextual.takeIf { contextAccepted }
                        current.selectedFarmId != null -> current.selectedFarmId.takeIf { id -> farms.any { it.id == id } }
                        else -> farms.singleOrNull()?.id
                    }
                    mutableState.value = current.copy(
                        isLoading = false,
                        farms = farms,
                        selectedFarmId = selected,
                        contextualFarmAccepted = contextAccepted,
                        error = if (contextual != null && !contextAccepted) CONTEXT_FARM_MISSING else null,
                    )
                }
            }
        }
    }

    private companion object {
        const val CONTEXT_FARM_MISSING = "Esta finca ya no está activa. Vuelve a Mi Campo y elige otra."
    }
}

data class ActivityDetailUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val activity: Activity? = null,
    val parcels: List<ActivityParcelOption> = emptyList(),
    val machines: List<MachineOption> = emptyList(),
    val error: String? = null,
    val message: String? = null,
    /** #380: finished saves; the editor closes when this rises. */
    val saveCount: Int = 0,
)

class ActivityDetailViewModel(private val activityId: UUID, private val repository: ActivityRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(ActivityDetailUiState())
    val state: StateFlow<ActivityDetailUiState> = mutableState.asStateFlow()

    private var parcelsJob: Job? = null

    init {
        viewModelScope.launch {
            repository.observe(activityId).collect { activity ->
                mutableState.value = mutableState.value.copy(
                    isLoading = false,
                    activity = activity,
                    error = if (activity == null) "El trabajo no está disponible" else null,
                )
                val farmId = activity?.farmId
                if (farmId != null && parcelsJob == null) {
                    parcelsJob = viewModelScope.launch {
                        repository.observeSelectableParcels(farmId).collect { parcels ->
                            mutableState.value = mutableState.value.copy(parcels = parcels)
                        }
                    }
                }
            }
        }
        viewModelScope.launch {
            repository.observeSelectableMachines().collect {
                mutableState.value = mutableState.value.copy(machines = it)
            }
        }
    }

    fun update(draft: ActivityDraft) {
        if (draft.description.isBlank() || draft.activityDate == null) {
            mutableState.value = mutableState.value.copy(error = "Revisa la descripción y la fecha")
            return
        }
        mutate("Cambios guardados") {
            repository.update(
                activityId,
                ActivityChanges(
                    draft.type,
                    draft.activityDate,
                    draft.description.trim(),
                    draft.parcelIds,
                    draft.notes.nullIfBlank(),
                    draft.detail,
                    // #429: an edit never touches money; a cost linked before 1.0 stays on its Gasto.
                    null,
                    draft.machines,
                    draft.planning,
                    draft.reminders,
                    parcelAreasM2 = draft.parcelAreasM2,
                    activityEndDate = draft.activityEndDate,
                ),
            )
        }
    }

    fun plan() = mutate("Trabajo planificado", "volver a planificarlo") { repository.plan(activityId) }

    fun complete() = mutate("Trabajo completado") { repository.complete(activityId) }

    fun cancel() = mutate("Trabajo cancelado", "cancelarlo") { repository.cancel(activityId) }

    fun reopen() = mutate("Trabajo reabierto", "volver a planificarlo") { repository.reopen(activityId) }

    fun archive() = mutate("Trabajo archivado", "archivarlo") { repository.archive(activityId) }

    fun consumeMessage() { mutableState.value = mutableState.value.copy(message = null) }

    private fun mutate(message: String, move: String? = null, operation: suspend () -> AppResult<Unit>) = viewModelScope.launch {
        mutableState.value = mutableState.value.copy(isSaving = true, error = null, message = null)
        mutableState.value = when (val result = operation()) {
            is AppResult.Success -> mutableState.value.copy(isSaving = false, message = message, saveCount = mutableState.value.saveCount + 1)
            is AppResult.Failure -> mutableState.value.copy(
                isSaving = false,
                error = when ((result.error as? AppError.Conflict)?.resource) {
                    ActivityCostRules.COST_TO_REVIEW -> move?.let(::costToReview)
                    ActivityCostRules.LINKED_EXPENSES -> LINKED_EXPENSES_TEXT
                    ActivityCostRules.PARCEL_HAS_EXPENSES -> PARCEL_HAS_EXPENSES_TEXT
                    else -> null
                } ?: "La operación no se pudo completar",
            )
        }
    }
}

/** #429: said when a counted cost holds a move back; the detail offers «Revisar gasto vinculado». */
internal fun costToReview(move: String) = "Este trabajo tiene un coste contabilizado. Revísalo antes de $move."

/** #441: said when an edit would drop a Parcel a Gasto of the work names. */
internal const val PARCEL_HAS_EXPENSES_TEXT =
    "Hay gastos vinculados a esta parcela dentro del trabajo. Revísalos antes de cambiar las parcelas."

/** #437: said when Gastos of their own still point at the work. */
internal const val LINKED_EXPENSES_TEXT =
    "Este trabajo tiene gastos vinculados. Consérvalo cancelado o revisa esos gastos antes de archivarlo."


private fun String.nullIfBlank(): String? = trim().takeIf(String::isNotEmpty)

internal fun isFutureDoneWork(
    activityDate: LocalDate?,
    today: LocalDate?,
    doneWork: Boolean,
    type: ActivityType,
): Boolean =
    doneWork &&
        type != ActivityType.HARVEST_DAY &&
        activityDate != null &&
        today != null &&
        activityDate.isAfter(today)

/** #435/#414: why the Cuaderno will not save work dated ahead. */
internal const val FUTURE_DONE_WORK = "La fecha es futura. Para trabajos pendientes usa Avisos → Planificar."
