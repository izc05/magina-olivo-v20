package com.isivoltpro.maginaolivo.feature.farms

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.isivoltpro.maginaolivo.core.common.AppError
import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.domain.farm.Farm
import com.isivoltpro.maginaolivo.domain.farm.FarmChanges
import com.isivoltpro.maginaolivo.domain.farm.FarmRepository
import com.isivoltpro.maginaolivo.domain.farm.NewFarm
import com.isivoltpro.maginaolivo.domain.workspace.WorkspaceRepository
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class FarmDraft(
    val name: String = "",
    val description: String = "",
    val municipality: String = "",
    val province: String = "",
    val notes: String = "",
    val coverDocumentId: UUID? = null,
)

data class FarmListUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val workspaceId: UUID? = null,
    val farms: List<Farm> = emptyList(),
    val archivedFarms: List<Farm> = emptyList(),
    val nameError: String? = null,
    val error: String? = null,
    val message: String? = null,
)

class FarmListViewModel(
    private val farmRepository: FarmRepository,
    private val workspaceRepository: WorkspaceRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(FarmListUiState())
    val state: StateFlow<FarmListUiState> = mutableState.asStateFlow()
    private var observationJob: Job? = null

    init {
        load()
    }

    fun retry() = load()

    fun clearFeedback() {
        mutableState.value = mutableState.value.copy(
            nameError = null,
            error = null,
            message = null,
        )
    }

    fun create(draft: FarmDraft) {
        if (!validateName(draft.name)) return
        val workspaceId = mutableState.value.workspaceId ?: return
        mutate(successMessage = "Finca guardada en este dispositivo") {
            farmRepository.create(
                NewFarm(
                    workspaceId = workspaceId,
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

    fun update(
        farmId: UUID,
        draft: FarmDraft,
    ) {
        if (!validateName(draft.name)) return
        mutate(successMessage = "Cambios guardados en este dispositivo") {
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

    fun archive(farmId: UUID) {
        mutate(successMessage = "Finca archivada") { farmRepository.archive(farmId) }
    }

    fun restore(farmId: UUID) {
        mutate(successMessage = "Finca restaurada") { farmRepository.restore(farmId) }
    }

    private fun load() {
        observationJob?.cancel()
        mutableState.value = mutableState.value.copy(isLoading = true, error = null)
        observationJob = viewModelScope.launch {
            when (val workspace = workspaceRepository.ensureLocalWorkspace()) {
                is AppResult.Failure -> mutableState.value = mutableState.value.copy(
                    isLoading = false,
                    error = workspace.error.userMessage(),
                )
                is AppResult.Success -> {
                    val workspaceId = workspace.value
                    combine(
                        farmRepository.observeActive(workspaceId),
                        farmRepository.observeArchived(workspaceId),
                    ) { active, archived -> active to archived }
                        .collect { (active, archived) ->
                            mutableState.value = mutableState.value.copy(
                                isLoading = false,
                                workspaceId = workspaceId,
                                farms = active,
                                archivedFarms = archived,
                                error = null,
                            )
                        }
                }
            }
        }
    }

    private fun validateName(name: String): Boolean {
        if (name.isBlank()) {
            mutableState.value = mutableState.value.copy(
                nameError = "Escribe un nombre para la finca",
                message = null,
            )
            return false
        }
        mutableState.value = mutableState.value.copy(nameError = null)
        return true
    }

    private fun <T> mutate(
        successMessage: String,
        operation: suspend () -> AppResult<T>,
    ) {
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(
                isSaving = true,
                error = null,
                message = null,
            )
            when (val result = operation()) {
                is AppResult.Success -> mutableState.value = mutableState.value.copy(
                    isSaving = false,
                    message = successMessage,
                )
                is AppResult.Failure -> mutableState.value = mutableState.value.copy(
                    isSaving = false,
                    error = result.error.userMessage(),
                )
            }
        }
    }
}

private fun AppError.userMessage(): String = when (this) {
    is AppError.Validation -> "Revisa los datos indicados"
    is AppError.NotFound -> "La finca ya no está disponible"
    is AppError.Conflict -> "La finca cambió; vuelve a abrirla e inténtalo de nuevo"
    is AppError.Permission -> "No se pudo conservar el permiso necesario"
    is AppError.Offline -> "Sin cobertura: tus datos locales siguen disponibles"
    is AppError.Storage -> "No se pudo guardar en este dispositivo"
    is AppError.Remote -> "El servicio no está disponible ahora"
    is AppError.Unknown -> "Ha ocurrido un error inesperado"
}
