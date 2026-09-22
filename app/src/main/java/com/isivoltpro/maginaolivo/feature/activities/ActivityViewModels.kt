package com.isivoltpro.maginaolivo.feature.activities

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.data.local.model.ActivityStatus
import com.isivoltpro.maginaolivo.domain.activity.Activity
import com.isivoltpro.maginaolivo.domain.activity.ActivityChanges
import com.isivoltpro.maginaolivo.domain.activity.ActivityParcelOption
import com.isivoltpro.maginaolivo.domain.activity.ActivityRepository
import com.isivoltpro.maginaolivo.domain.activity.ActivityType
import com.isivoltpro.maginaolivo.domain.activity.NewActivity
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
)

data class FarmActivitiesUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val drafts: List<Activity> = emptyList(),
    val planned: List<Activity> = emptyList(),
    val history: List<Activity> = emptyList(),
    val parcels: List<ActivityParcelOption> = emptyList(),
    val descriptionError: String? = null,
    val dateError: String? = null,
    val parcelsError: String? = null,
    val error: String? = null,
    val message: String? = null,
)

class FarmActivitiesViewModel(private val farmId: UUID, private val repository: ActivityRepository) : ViewModel() {
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
    }

    /** A planned activity requires at least one Parcel; a resumable draft does not. */
    fun create(draft: ActivityDraft, asDraft: Boolean = false) {
        if (!validate(draft, asDraft)) return
        mutate("Actuación guardada en este dispositivo") {
            when (
                val result = repository.create(
                    NewActivity(
                        farmId = farmId,
                        type = draft.type,
                        activityDate = draft.activityDate!!,
                        description = draft.description.trim(),
                        parcelIds = draft.parcelIds,
                        notes = draft.notes.nullIfBlank(),
                        asDraft = asDraft,
                    ),
                )
            ) {
                is AppResult.Success -> AppResult.Success(Unit)
                is AppResult.Failure -> result
            }
        }
    }

    fun consumeMessage() { mutableState.value = mutableState.value.copy(message = null) }

    private fun validate(draft: ActivityDraft, asDraft: Boolean): Boolean {
        val descriptionError = if (draft.description.isBlank()) "Describe la actuación" else null
        val dateError = if (draft.activityDate == null) "Selecciona una fecha" else null
        val parcelsError =
            if (!asDraft && draft.parcelIds.isEmpty()) "Selecciona al menos una parcela" else null
        mutableState.value = mutableState.value.copy(
            descriptionError = descriptionError,
            dateError = dateError,
            parcelsError = parcelsError,
        )
        return descriptionError == null && dateError == null && parcelsError == null
    }

    private fun mutate(message: String, operation: suspend () -> AppResult<Unit>) = viewModelScope.launch {
        mutableState.value = mutableState.value.copy(isSaving = true, error = null, message = null)
        mutableState.value = when (operation()) {
            is AppResult.Success -> mutableState.value.copy(isSaving = false, message = message)
            is AppResult.Failure -> mutableState.value.copy(isSaving = false, error = "No se pudo guardar en este dispositivo")
        }
    }
}

data class ActivityDetailUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val activity: Activity? = null,
    val parcels: List<ActivityParcelOption> = emptyList(),
    val error: String? = null,
    val message: String? = null,
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
                    error = if (activity == null) "La actuación no está disponible" else null,
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
    }

    fun update(draft: ActivityDraft) {
        if (draft.description.isBlank() || draft.activityDate == null) {
            mutableState.value = mutableState.value.copy(error = "Revisa la descripción y la fecha")
            return
        }
        mutate("Cambios guardados") {
            repository.update(
                activityId,
                ActivityChanges(draft.type, draft.activityDate, draft.description.trim(), draft.parcelIds, draft.notes.nullIfBlank()),
            )
        }
    }

    fun plan() = mutate("Actuación planificada") { repository.plan(activityId) }

    fun complete() = mutate("Actuación completada") { repository.complete(activityId) }

    fun cancel() = mutate("Actuación cancelada") { repository.cancel(activityId) }

    fun reopen() = mutate("Actuación reabierta") { repository.reopen(activityId) }

    fun archive() = mutate("Actuación archivada") { repository.archive(activityId) }

    fun consumeMessage() { mutableState.value = mutableState.value.copy(message = null) }

    private fun mutate(message: String, operation: suspend () -> AppResult<Unit>) = viewModelScope.launch {
        mutableState.value = mutableState.value.copy(isSaving = true, error = null, message = null)
        mutableState.value = when (operation()) {
            is AppResult.Success -> mutableState.value.copy(isSaving = false, message = message)
            is AppResult.Failure -> mutableState.value.copy(isSaving = false, error = "La operación no se pudo completar")
        }
    }
}

private fun String.nullIfBlank(): String? = trim().takeIf(String::isNotEmpty)
