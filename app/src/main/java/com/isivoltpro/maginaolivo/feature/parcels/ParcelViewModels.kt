package com.isivoltpro.maginaolivo.feature.parcels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.domain.activity.Activity
import com.isivoltpro.maginaolivo.domain.activity.ActivityRepository
import com.isivoltpro.maginaolivo.domain.farm.FarmRepository
import com.isivoltpro.maginaolivo.domain.parcel.IrrigationSystem
import com.isivoltpro.maginaolivo.domain.parcel.NewParcel
import com.isivoltpro.maginaolivo.domain.parcel.Parcel
import com.isivoltpro.maginaolivo.domain.parcel.ParcelAgronomy
import com.isivoltpro.maginaolivo.domain.parcel.ParcelChanges
import com.isivoltpro.maginaolivo.domain.parcel.ParcelRepository
import com.isivoltpro.maginaolivo.domain.parcel.ParcelSource
import java.time.DayOfWeek
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ParcelDraft(
    val displayName: String = "",
    val cadastralReference: String = "",
    val cadastralPolygon: String = "",
    val cadastralParcel: String = "",
    val municipality: String = "",
    val province: String = "Jaén",
    val managedAreaHectares: String = "",
    val notes: String = "",
    val geometryGeoJson: String? = null,
    val cadastralAreaM2: Double? = null,
    // CR-004 grove description, all optional.
    val oliveTreeCount: String = "",
    val variety: String = "",
    val irrigationSystem: IrrigationSystem? = null,
    val irrigationNetwork: String = "",
    val irrigationSector: String = "",
    val irrigationDays: Set<DayOfWeek> = emptySet(),
)

data class FarmParcelsUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val active: List<Parcel> = emptyList(),
    val archived: List<Parcel> = emptyList(),
    val nameError: String? = null,
    val areaError: String? = null,
    val oliveError: String? = null,
    val error: String? = null,
    val message: String? = null,
)

class FarmParcelsViewModel(
    private val farmId: UUID,
    private val repository: ParcelRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(FarmParcelsUiState())
    val state: StateFlow<FarmParcelsUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeActive(farmId).collect {
                mutableState.value = mutableState.value.copy(isLoading = false, active = it)
            }
        }
        viewModelScope.launch {
            repository.observeArchived(farmId).collect {
                mutableState.value = mutableState.value.copy(archived = it)
            }
        }
    }

    fun create(draft: ParcelDraft) {
        val area = validate(draft) ?: return
        mutate("Parcela guardada en este dispositivo") {
            when (val result = repository.create(draft.toNewParcel(farmId, area))) {
                is AppResult.Success -> AppResult.Success(Unit)
                is AppResult.Failure -> result
            }
        }
    }

    fun restore(parcelId: UUID) {
        mutate("Parcela restaurada") { repository.restore(parcelId, farmId) }
    }

    private fun validate(draft: ParcelDraft): Double? {
        val parsedArea = parseArea(draft.managedAreaHectares)
        val nameError = if (draft.displayName.isBlank()) "Escribe un alias para la parcela" else null
        val areaError = if (draft.managedAreaHectares.isNotBlank() && parsedArea == null) {
            "Introduce una superficie válida mayor que cero"
        } else null
        val oliveError = if (draft.oliveTreeCount.isNotBlank() && parseOliveTrees(draft.oliveTreeCount) == null) {
            "Escribe un número de olivos entero mayor que cero"
        } else null
        mutableState.value = mutableState.value.copy(nameError = nameError, areaError = areaError, oliveError = oliveError)
        return if (nameError == null && areaError == null && oliveError == null) parsedArea ?: 0.0 else null
    }

    private fun mutate(message: String, operation: suspend () -> AppResult<Unit>) {
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(isSaving = true, error = null, message = null)
            mutableState.value = when (operation()) {
                is AppResult.Success -> mutableState.value.copy(isSaving = false, message = message)
                is AppResult.Failure -> mutableState.value.copy(
                    isSaving = false,
                    error = "No se pudo guardar en este dispositivo",
                )
            }
        }
    }
}

data class ParcelDetailUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val parcel: Parcel? = null,
    /** Name of the farm the parcel belongs to now, for the header. */
    val farmName: String? = null,
    /** Work recorded on this parcel, newest first (Actividad tab). */
    val activities: List<Activity> = emptyList(),
    val nameError: String? = null,
    val areaError: String? = null,
    val oliveError: String? = null,
    val error: String? = null,
    val message: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class ParcelDetailViewModel(
    private val parcelId: UUID,
    private val repository: ParcelRepository,
    activities: ActivityRepository? = null,
    farms: FarmRepository? = null,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ParcelDetailUiState())
    val state: StateFlow<ParcelDetailUiState> = mutableState.asStateFlow()

    init {
        activities?.let { repository ->
            viewModelScope.launch {
                repository.observeForParcel(parcelId).collect { list ->
                    mutableState.value = mutableState.value.copy(
                        activities = list.sortedByDescending { it.activityDate },
                    )
                }
            }
        }
        farms?.let { farmRepository ->
            viewModelScope.launch {
                repository.observeById(parcelId)
                    .map { it?.farmId }
                    .distinctUntilChanged()
                    .flatMapLatest { farmId -> farmId?.let(farmRepository::observeById) ?: flowOf(null) }
                    .collect { farm -> mutableState.value = mutableState.value.copy(farmName = farm?.name) }
            }
        }
        viewModelScope.launch {
            repository.observeById(parcelId).collect { parcel ->
                mutableState.value = mutableState.value.copy(
                    isLoading = false,
                    parcel = parcel,
                    error = if (parcel == null) "La parcela no está disponible" else null,
                )
            }
        }
    }

    fun update(draft: ParcelDraft) {
        val area = validate(draft) ?: return
        mutate("Cambios guardados en este dispositivo") {
            repository.update(parcelId, draft.toChanges(area))
        }
    }

    fun archive() = mutate("Parcela archivada") { repository.archive(parcelId) }

    fun restore() {
        val farmId = mutableState.value.parcel?.farmId
        if (farmId == null) {
            mutableState.value = mutableState.value.copy(error = "Selecciona una finca para restaurar la parcela")
            return
        }
        mutate("Parcela restaurada") { repository.restore(parcelId, farmId) }
    }

    private fun validate(draft: ParcelDraft): Double? {
        val parsedArea = parseArea(draft.managedAreaHectares)
        val nameError = if (draft.displayName.isBlank()) "Escribe un alias para la parcela" else null
        val areaError = if (draft.managedAreaHectares.isNotBlank() && parsedArea == null) {
            "Introduce una superficie válida mayor que cero"
        } else null
        val oliveError = if (draft.oliveTreeCount.isNotBlank() && parseOliveTrees(draft.oliveTreeCount) == null) {
            "Escribe un número de olivos entero mayor que cero"
        } else null
        mutableState.value = mutableState.value.copy(nameError = nameError, areaError = areaError, oliveError = oliveError)
        return if (nameError == null && areaError == null && oliveError == null) parsedArea ?: 0.0 else null
    }

    private fun mutate(message: String, operation: suspend () -> AppResult<Unit>) {
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(isSaving = true, error = null, message = null)
            mutableState.value = when (operation()) {
                is AppResult.Success -> mutableState.value.copy(isSaving = false, message = message)
                is AppResult.Failure -> mutableState.value.copy(
                    isSaving = false,
                    error = "No se pudo guardar en este dispositivo",
                )
            }
        }
    }
}

private fun ParcelDraft.toNewParcel(farmId: UUID, areaM2: Double) = NewParcel(
    farmId = farmId,
    displayName = displayName.trim(),
    cadastralReference = cadastralReference.nullIfBlank(),
    cadastralPolygon = cadastralPolygon.nullIfBlank(),
    cadastralParcel = cadastralParcel.nullIfBlank(),
    municipality = municipality.nullIfBlank(),
    province = province.nullIfBlank(),
    source = ParcelSource.MANUAL,
    geometryGeoJson = geometryGeoJson,
    cadastralAreaM2 = cadastralAreaM2,
    managedAreaM2 = areaM2.takeIf { it > 0 },
    notes = notes.nullIfBlank(),
    agronomy = toAgronomy(),
)

private fun ParcelDraft.toChanges(areaM2: Double) = ParcelChanges(
    displayName = displayName.trim(),
    cadastralReference = cadastralReference.nullIfBlank(),
    cadastralPolygon = cadastralPolygon.nullIfBlank(),
    cadastralParcel = cadastralParcel.nullIfBlank(),
    municipality = municipality.nullIfBlank(),
    province = province.nullIfBlank(),
    geometryGeoJson = geometryGeoJson,
    cadastralAreaM2 = cadastralAreaM2,
    managedAreaM2 = areaM2.takeIf { it > 0 },
    notes = notes.nullIfBlank(),
    agronomy = toAgronomy(),
)

private fun ParcelDraft.toAgronomy() = ParcelAgronomy(
    oliveTreeCount = parseOliveTrees(oliveTreeCount),
    variety = variety.nullIfBlank(),
    irrigationSystem = irrigationSystem,
    irrigationNetwork = irrigationNetwork.nullIfBlank(),
    irrigationSector = irrigationSector.nullIfBlank(),
    irrigationDays = irrigationDays,
)

internal fun parseOliveTrees(value: String): Int? = value
    .trim()
    .replace(".", "")
    .takeIf(String::isNotEmpty)
    ?.toIntOrNull()
    ?.takeIf { it in 1..1_000_000 }

private fun parseArea(value: String): Double? = value
    .trim()
    .takeIf(String::isNotEmpty)
    ?.replace(',', '.')
    ?.toDoubleOrNull()
    ?.takeIf { it > 0 }
    ?.times(10_000)

private fun String.nullIfBlank(): String? = trim().takeIf(String::isNotEmpty)
