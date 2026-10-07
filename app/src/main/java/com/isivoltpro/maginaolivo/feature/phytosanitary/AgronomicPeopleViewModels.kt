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
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.Job
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
    val credentialSummaries: Map<UUID, String> = emptyMap(),
    val isSaving: Boolean = false,
    val message: String? = null,
    val error: String? = null,
    val saveCount: Int = 0,
)

class AgronomicPeopleViewModel(
    private val repository: PhytosanitaryResourceRepository,
    private val todayProvider: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {
    private val mutableState = MutableStateFlow(AgronomicPeopleUiState())
    private val credentialJobs = mutableMapOf<UUID, Job>()
    val state: StateFlow<AgronomicPeopleUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeActivePeople()
                .catch { mutableState.value = mutableState.value.copy(isLoading = false, error = "No pudimos leer los aplicadores") }
                .collect { people ->
                    mutableState.value = mutableState.value.copy(isLoading = false, active = people)
                    syncCredentialObservers(people.mapTo(mutableSetOf()) { it.id })
                }
        }
        viewModelScope.launch {
            repository.observeArchivedPeople()
                .catch { }
                .collect { mutableState.value = mutableState.value.copy(archived = it) }
        }
    }

    private fun syncCredentialObservers(ids: Set<UUID>) {
        val removed = credentialJobs.keys - ids
        removed.forEach { id -> credentialJobs.remove(id)?.cancel() }
        if (removed.isNotEmpty()) {
            mutableState.value = mutableState.value.copy(
                credentialSummaries = mutableState.value.credentialSummaries - removed,
            )
        }
        (ids - credentialJobs.keys).forEach { id ->
            credentialJobs[id] = viewModelScope.launch {
                repository.observeCredentials(id)
                    .catch {
                        mutableState.value = mutableState.value.copy(
                            credentialSummaries = mutableState.value.credentialSummaries - id,
                        )
                    }
                    .collect { credentials ->
                        val next = mutableState.value.credentialSummaries.toMutableMap()
                        currentCredentialSummary(credentials, todayProvider())?.let { next[id] = it } ?: next.remove(id)
                        mutableState.value = mutableState.value.copy(credentialSummaries = next)
                    }
            }
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

internal fun currentCredentialSummary(
    credentials: List<AgronomicCredential>,
    today: LocalDate,
): String? {
    val current = credentials.firstOrNull { credential ->
        (credential.validFrom == null || !credential.validFrom.isAfter(today)) &&
            (credential.validUntil == null || !credential.validUntil.isBefore(today))
    } ?: return null
    return buildList {
        add(credentialKindLabel(current.credentialType))
        add(maskIdentifier(current.number))
        current.validUntil?.let { add("hasta $it") }
    }.joinToString(" · ")
}

internal fun credentialKindLabel(value: String): String = when (value) {
    "APPLICATOR_CARD", "ROPO_APPLICATOR" -> "Carné / ROPO"
    "ADVISOR" -> "Asesor"
    else -> value
}

internal fun maskIdentifier(value: String): String =
    if (value.length <= 4) "••••" else "••••${value.takeLast(4)}"

private fun <T> AppResult<T>.unit(): AppResult<Unit> = when (this) {
    is AppResult.Success -> AppResult.Success(Unit)
    is AppResult.Failure -> this
}
