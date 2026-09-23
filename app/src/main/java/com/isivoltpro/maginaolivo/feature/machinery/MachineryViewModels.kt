package com.isivoltpro.maginaolivo.feature.machinery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.isivoltpro.maginaolivo.core.common.AppError
import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.domain.machinery.Machine
import com.isivoltpro.maginaolivo.domain.machinery.MachineCategory
import com.isivoltpro.maginaolivo.domain.machinery.MachineDraft
import com.isivoltpro.maginaolivo.domain.machinery.MachineRepository
import com.isivoltpro.maginaolivo.domain.machinery.MachineUse
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

data class MachineForm(
    val name: String = "",
    val category: MachineCategory = MachineCategory.TRACTOR,
    val make: String = "",
    val model: String = "",
    val registration: String = "",
    val hours: String = "",
    val notes: String = "",
)

data class MachineFormErrors(val name: String? = null, val hours: String? = null) {
    val isEmpty: Boolean get() = name == null && hours == null
}

internal fun MachineForm.toDraft(): Pair<MachineDraft?, MachineFormErrors> {
    val hoursValue = hours.replace(',', '.').trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()
    val errors = MachineFormErrors(
        name = if (name.isBlank()) "Ponle un nombre a la máquina" else null,
        hours = when {
            hours.isNotBlank() && hoursValue == null -> "Escribe las horas como 1250 o 1250,5"
            (hoursValue ?: 0.0) < 0 -> "Las horas no pueden ser negativas"
            else -> null
        },
    )
    if (!errors.isEmpty) return null to errors
    return MachineDraft(
        name = name.trim(),
        category = category,
        make = make.trim().ifEmpty { null },
        model = model.trim().ifEmpty { null },
        registrationOrSerial = registration.trim().ifEmpty { null },
        currentHours = hoursValue,
        notes = notes.trim().ifEmpty { null },
    ) to errors
}

internal fun Machine.toForm() = MachineForm(
    name = name,
    category = category,
    make = make.orEmpty(),
    model = model.orEmpty(),
    registration = registrationOrSerial.orEmpty(),
    hours = currentHours?.let { java.math.BigDecimal.valueOf(it).stripTrailingZeros().toPlainString().replace('.', ',') }.orEmpty(),
    notes = notes.orEmpty(),
)

internal fun MachineCategory.label(): String = when (this) {
    MachineCategory.TRACTOR -> "Tractor"
    MachineCategory.ATOMIZER -> "Atomizador / cuba"
    MachineCategory.MOWER -> "Desbrozadora"
    MachineCategory.PRUNER -> "Podadora / motosierra"
    MachineCategory.HARVEST -> "Recolección (vibrador, paraguas…)"
    MachineCategory.TRAILER -> "Remolque"
    MachineCategory.TOOL -> "Herramienta"
    MachineCategory.OTHER -> "Otra"
}

internal fun machineErrorMessage(error: AppError): String = when {
    error is AppError.Conflict && error.resource == "duplicate_machine" -> "Ya tienes una máquina con ese nombre"
    error is AppError.NotFound -> "La máquina ya no está en este dispositivo"
    else -> "No se pudo guardar en el dispositivo. Inténtalo de nuevo."
}

data class MachineryUiState(
    val isLoading: Boolean = true,
    val active: List<Machine> = emptyList(),
    val archived: List<Machine> = emptyList(),
    val formErrors: MachineFormErrors = MachineFormErrors(),
    val isSaving: Boolean = false,
    val message: String? = null,
    val error: String? = null,
)

class MachineryViewModel(private val machines: MachineRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(MachineryUiState())
    val state: StateFlow<MachineryUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            machines.observeActive()
                .catch { mutableState.value = mutableState.value.copy(isLoading = false, error = "No pudimos leer la maquinaria") }
                .collect { mutableState.value = mutableState.value.copy(isLoading = false, active = it) }
        }
        viewModelScope.launch {
            machines.observeArchived().catch { }.collect { mutableState.value = mutableState.value.copy(archived = it) }
        }
    }

    fun create(form: MachineForm) {
        val (draft, errors) = form.toDraft()
        mutableState.value = mutableState.value.copy(formErrors = errors, message = null)
        if (draft == null) return
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(isSaving = true, error = null)
            mutableState.value = when (val result = machines.create(draft)) {
                is AppResult.Success -> mutableState.value.copy(isSaving = false, message = "Máquina guardada", formErrors = MachineFormErrors())
                is AppResult.Failure -> mutableState.value.copy(isSaving = false, error = machineErrorMessage(result.error))
            }
        }
    }

    fun clearFormErrors() {
        mutableState.value = mutableState.value.copy(formErrors = MachineFormErrors())
    }
}

data class MachineDetailUiState(
    val isLoading: Boolean = true,
    val machine: Machine? = null,
    val uses: List<MachineUse> = emptyList(),
    val formErrors: MachineFormErrors = MachineFormErrors(),
    val isSaving: Boolean = false,
    val message: String? = null,
    val error: String? = null,
) {
    /** Only hours someone recorded; activities without hours are counted, not guessed. */
    val recordedHours: Double get() = uses.mapNotNull { it.hoursUsed }.sum()
    val usesWithoutHours: Int get() = uses.count { it.hoursUsed == null }
}

class MachineDetailViewModel(private val machineId: UUID, private val machines: MachineRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(MachineDetailUiState())
    val state: StateFlow<MachineDetailUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            machines.observe(machineId)
                .catch { mutableState.value = mutableState.value.copy(isLoading = false, error = "No pudimos leer la máquina") }
                .collect { mutableState.value = mutableState.value.copy(isLoading = false, machine = it) }
        }
        viewModelScope.launch {
            machines.observeUses(machineId).catch { }.collect { mutableState.value = mutableState.value.copy(uses = it) }
        }
    }

    fun update(form: MachineForm) {
        val (draft, errors) = form.toDraft()
        mutableState.value = mutableState.value.copy(formErrors = errors, message = null)
        if (draft == null) return
        mutate("Cambios guardados") { machines.update(machineId, draft) }
    }

    fun archive() = mutate("Máquina retirada") { machines.archive(machineId) }

    fun restore() = mutate("Máquina recuperada") { machines.restore(machineId) }

    fun clearFormErrors() {
        mutableState.value = mutableState.value.copy(formErrors = MachineFormErrors())
    }

    private fun mutate(message: String, operation: suspend () -> AppResult<Unit>) {
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(isSaving = true, error = null, message = null)
            mutableState.value = when (val result = operation()) {
                is AppResult.Success -> mutableState.value.copy(isSaving = false, message = message, formErrors = MachineFormErrors())
                is AppResult.Failure -> mutableState.value.copy(isSaving = false, error = machineErrorMessage(result.error))
            }
        }
    }
}
