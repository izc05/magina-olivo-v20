package com.isivoltpro.maginaolivo.feature.agenda

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.isivoltpro.maginaolivo.core.common.AppError
import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.core.time.AppClock
import com.isivoltpro.maginaolivo.domain.activity.ActivityRepository
import com.isivoltpro.maginaolivo.domain.activity.AgendaEntry
import com.isivoltpro.maginaolivo.domain.agenda.Agenda
import com.isivoltpro.maginaolivo.domain.agenda.AgendaSection
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AgendaUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val today: LocalDate? = null,
    val sections: List<AgendaSection<AgendaEntry>> = emptyList(),
    val message: String? = null,
    val error: String? = null,
) {
    val hasReminders: Boolean get() = sections.any { section -> section.items.any { it.reminders.isNotEmpty() } }
}

/**
 * The Calendar root: planned work across every Farm, read from the local database only.
 * Completing or cancelling here is the same transition as in the Activity detail, on the
 * same record, and it silences that work's reminders.
 */
class AgendaViewModel(
    private val repository: ActivityRepository,
    private val clock: AppClock,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) : ViewModel() {
    private val mutableState = MutableStateFlow(AgendaUiState())
    val state: StateFlow<AgendaUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeAgenda().collect { entries ->
                val today = clock.today(zone())
                mutableState.value = mutableState.value.copy(
                    isLoading = false,
                    today = today,
                    sections = Agenda.group(entries, today, { it.activityDate }, { it.planning?.startTime }),
                )
            }
        }
    }

    fun complete(id: UUID) = mutate("Trabajo marcado como hecho") { repository.complete(id) }

    fun cancel(id: UUID) = mutate("Trabajo cancelado") { repository.cancel(id) }

    fun consumeMessage() {
        mutableState.value = mutableState.value.copy(message = null, error = null)
    }

    private fun mutate(message: String, operation: suspend () -> AppResult<Unit>) = viewModelScope.launch {
        mutableState.value = mutableState.value.copy(isSaving = true, message = null, error = null)
        mutableState.value = when (val result = operation()) {
            is AppResult.Success -> mutableState.value.copy(isSaving = false, message = message)
            is AppResult.Failure -> mutableState.value.copy(
                isSaving = false,
                error = if (result.error is AppError.Validation) {
                    "Para darlo por hecho, elige antes sus parcelas en la actuación."
                } else {
                    "No se pudo guardar en este dispositivo"
                },
            )
        }
    }
}
