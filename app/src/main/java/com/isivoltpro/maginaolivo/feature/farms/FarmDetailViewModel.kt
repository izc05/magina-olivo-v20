package com.isivoltpro.maginaolivo.feature.farms

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.domain.farm.Farm
import com.isivoltpro.maginaolivo.domain.farm.FarmChanges
import com.isivoltpro.maginaolivo.domain.farm.FarmRepository
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class FarmDetailUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val farm: Farm? = null,
    val nameError: String? = null,
    val error: String? = null,
    val message: String? = null,
)

class FarmDetailViewModel(
    private val farmId: UUID,
    private val farmRepository: FarmRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(FarmDetailUiState())
    val state: StateFlow<FarmDetailUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            farmRepository.observeById(farmId).collect { farm ->
                mutableState.value = mutableState.value.copy(
                    isLoading = false,
                    farm = farm,
                    error = if (farm == null) "La finca no está disponible" else null,
                )
            }
        }
    }

    fun update(draft: FarmDraft) {
        if (draft.name.isBlank()) {
            mutableState.value = mutableState.value.copy(
                nameError = "Escribe un nombre para la finca",
            )
            return
        }
        mutate("Cambios guardados en este dispositivo") {
            farmRepository.update(
                farmId,
                FarmChanges(
                    name = draft.name,
                    description = draft.description,
                    municipality = draft.municipality,
                    province = draft.province,
                    notes = draft.notes,
                    coverDocumentId = draft.coverDocumentId,
                ),
            )
        }
    }

    fun archive() {
        mutate("Finca archivada") { farmRepository.archive(farmId) }
    }

    private fun mutate(
        successMessage: String,
        operation: suspend () -> AppResult<Unit>,
    ) {
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(
                isSaving = true,
                nameError = null,
                error = null,
                message = null,
            )
            when (operation()) {
                is AppResult.Success -> mutableState.value = mutableState.value.copy(
                    isSaving = false,
                    message = successMessage,
                )
                is AppResult.Failure -> mutableState.value = mutableState.value.copy(
                    isSaving = false,
                    error = "No se pudo guardar en este dispositivo",
                )
            }
        }
    }
}
