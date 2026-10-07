package com.isivoltpro.maginaolivo.feature.phytosanitary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.isivoltpro.maginaolivo.core.common.AppError
import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.domain.phytosanitary.AgronomicCredential
import com.isivoltpro.maginaolivo.domain.phytosanitary.AgronomicCredentialDraft
import com.isivoltpro.maginaolivo.domain.phytosanitary.AgronomicPerson
import com.isivoltpro.maginaolivo.domain.phytosanitary.AgronomicPersonDraft
import com.isivoltpro.maginaolivo.domain.phytosanitary.PhytosanitaryResourceRepository
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

data class AgronomicPersonForm(
    val displayName: String = "",
    val taxId: String = "",
    val isAdvisor: Boolean = false,
) {
    fun toDraft(): AgronomicPersonDraft? =
        displayName.trim().takeIf { it.isNotEmpty() }?.let {
            AgronomicPersonDraft(
                displayName = it,
                taxId = taxId.trim().ifEmpty { null },
                isAdvisor = isAdvisor,
            )
        }
}

data class CredentialForm(
    val credentialType: String = "ROPO_APPLICATOR",
    val number: String = "",
    val categoryCode: String = "",
    val validFrom: String = "",
    val validUntil: String = "",
)

data class AgronomicPeopleUiState(
    val isLoading: Boolean = true,
    val active: List<AgronomicPerson> = emptyList(),
    val archived: List<AgronomicPerson> = emptyList(),
    val isSaving: Boolean = false,
    val message: String? = null,
    val error: String? = null,
    val saveCount: Int = 0,
)

class AgronomicPeopleViewModel(
    private val repository: PhytosanitaryResourceRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(AgronomicPeopleUiState())
    val state: StateFlow<AgronomicPeopleUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeActivePeople()
                .catch { mutableState.value = mutableState.value.copy(isLoading = false, error = "No pudimos leer los aplicadores") }
                .collect { mutableState.value = mutableState.value.copy(isLoading = false, active = it) }
        }
        viewModelScope.launch {
            repository.observeArchivedPeople()
                .catch { }
                .collect { mutableState.value = mutableState.value.copy(archived = it) }
        }
    }

    fun create(form: AgronomicPersonForm) {
        val draft = form.toDraft()
        if (draft == null) {
            mutableState.value = mutableState.value.copy(error = "Escribe el nombre del aplicador")
            return
        }
        mutate("Aplicador guardado") { repository.createPerson(draft).unit() }
    }

    fun clearMessage() {
        mutableState.value = mutableState.value.copy(message = null, error = null)
    }

    private fun mutate(message: String, operation: suspend () -> AppResult<Unit>) {
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(isSaving = true, error = null, message = null)
            mutableState.value = when (val result = operation()) {
                is AppResult.Success -> mutableState.value.copy(
                    isSaving = false,
                    message = message,
                    saveCount = mutableState.value.saveCount + 1,
                )
                is AppResult.Failure -> mutableState.value.copy(
                    isSaving = false,
                    error = resourceError(result.error),
                )
            }
        }
    }
}

data class AgronomicPersonDetailUiState(
    val isLoading: Boolean = true,
    val person: AgronomicPerson? = null,
    val credentials: List<AgronomicCredential> = emptyList(),
    val isSaving: Boolean = false,
    val message: String? = null,
    val error: String? = null,
    val saveCount: Int = 0,
)

class AgronomicPersonDetailViewModel(
    private val personId: UUID,
    private val repository: PhytosanitaryResourceRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(AgronomicPersonDetailUiState())
    val state: StateFlow<AgronomicPersonDetailUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeActivePeople().catch { }.collect { rows ->
                rows.firstOrNull { it.id == personId }?.let {
                    mutableState.value = mutableState.value.copy(isLoading = false, person = it)
                }
            }
        }
        viewModelScope.launch {
            repository.observeArchivedPeople().catch { }.collect { rows ->
                rows.firstOrNull { it.id == personId }?.let {
                    mutableState.value = mutableState.value.copy(isLoading = false, person = it)
                }
            }
        }
        viewModelScope.launch {
            repository.observeCredentials(personId)
                .catch { mutableState.value = mutableState.value.copy(error = "No pudimos leer las credenciales") }
                .collect { mutableState.value = mutableState.value.copy(credentials = it) }
        }
    }

    fun update(form: AgronomicPersonForm) {
        val draft = form.toDraft()
        if (draft == null) {
            mutableState.value = mutableState.value.copy(error = "Escribe el nombre")
            return
        }
        mutate("Datos guardados") { repository.updatePerson(personId, draft) }
    }

    fun addCredential(draft: AgronomicCredentialDraft) =
        mutate("Credencial añadida") { repository.addCredential(personId, draft).unit() }

    fun archive() = mutate("Aplicador archivado") { repository.archivePerson(personId) }
    fun restore() = mutate("Aplicador recuperado") { repository.restorePerson(personId) }

    private fun mutate(message: String, operation: suspend () -> AppResult<Unit>) {
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(isSaving = true, error = null, message = null)
            mutableState.value = when (val result = operation()) {
                is AppResult.Success -> mutableState.value.copy(
                    isSaving = false,
                    message = message,
                    saveCount = mutableState.value.saveCount + 1,
                )
                is AppResult.Failure -> mutableState.value.copy(
                    isSaving = false,
                    error = resourceError(result.error),
                )
            }
        }
    }
}

private fun resourceError(error: AppError): String = when {
    error is AppError.Conflict && error.resource == "duplicate_agronomic_person" ->
        "Ya existe una persona activa con ese nombre"
    error is AppError.Validation -> "Revisa los datos introducidos"
    error is AppError.NotFound -> "Este registro ya no está disponible"
    else -> "No se pudo guardar en el dispositivo"
}

private fun <T> AppResult<T>.unit(): AppResult<Unit> = when (this) {
    is AppResult.Success -> AppResult.Success(Unit)
    is AppResult.Failure -> this
}
