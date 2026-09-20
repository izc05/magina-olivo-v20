package com.isivoltpro.maginaolivo.feature.parcels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.domain.parcel.NewParcel
import com.isivoltpro.maginaolivo.domain.parcel.Parcel
import com.isivoltpro.maginaolivo.domain.parcel.ParcelChanges
import com.isivoltpro.maginaolivo.domain.parcel.ParcelRepository
import com.isivoltpro.maginaolivo.domain.parcel.ParcelSource
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
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
)

data class FarmParcelsUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val active: List<Parcel> = emptyList(),
    val archived: List<Parcel> = emptyList(),
    val nameError: String? = null,
    val areaError: String? = null,
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
        mutableState.value = mutableState.value.copy(nameError = nameError, areaError = areaError)
        return if (nameError == null && areaError == null) parsedArea ?: 0.0 else null
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
    val nameError: String? = null,
    val areaError: String? = null,
    val error: String? = null,
    val message: String? = null,
)

class ParcelDetailViewModel(
    private val parcelId: UUID,
    private val farmId: UUID,
    private val repository: ParcelRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ParcelDetailUiState())
    val state: StateFlow<ParcelDetailUiState> = mutableState.asStateFlow()

    init {
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

    fun restore() = mutate("Parcela restaurada") { repository.restore(parcelId, farmId) }

    private fun validate(draft: ParcelDraft): Double? {
        val parsedArea = parseArea(draft.managedAreaHectares)
        val nameError = if (draft.displayName.isBlank()) "Escribe un alias para la parcela" else null
        val areaError = if (draft.managedAreaHectares.isNotBlank() && parsedArea == null) {
            "Introduce una superficie válida mayor que cero"
        } else null
        mutableState.value = mutableState.value.copy(nameError = nameError, areaError = areaError)
        return if (nameError == null && areaError == null) parsedArea ?: 0.0 else null
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
)

private fun parseArea(value: String): Double? = value
    .trim()
    .takeIf(String::isNotEmpty)
    ?.replace(',', '.')
    ?.toDoubleOrNull()
    ?.takeIf { it > 0 }
    ?.times(10_000)

private fun String.nullIfBlank(): String? = trim().takeIf(String::isNotEmpty)
