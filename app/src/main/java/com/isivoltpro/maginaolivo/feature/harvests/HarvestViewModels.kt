package com.isivoltpro.maginaolivo.feature.harvests

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.isivoltpro.maginaolivo.core.common.AppError
import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.core.time.AppClock
import com.isivoltpro.maginaolivo.domain.delivery.Delivery
import com.isivoltpro.maginaolivo.domain.delivery.DeliveryRepository
import com.isivoltpro.maginaolivo.domain.harvest.Harvest
import com.isivoltpro.maginaolivo.domain.harvest.HarvestContext
import com.isivoltpro.maginaolivo.domain.harvest.HarvestProblem
import com.isivoltpro.maginaolivo.domain.harvest.HarvestRepository
import com.isivoltpro.maginaolivo.domain.harvest.HarvestSummary
import com.isivoltpro.maginaolivo.domain.equipment.EquipmentDraftLine
import com.isivoltpro.maginaolivo.domain.equipment.EquipmentLine
import com.isivoltpro.maginaolivo.domain.equipment.EquipmentRepository
import com.isivoltpro.maginaolivo.domain.harvest.Jornada
import com.isivoltpro.maginaolivo.domain.machinery.Machine
import com.isivoltpro.maginaolivo.domain.machinery.MachineRepository
import com.isivoltpro.maginaolivo.domain.labour.CountDraft
import com.isivoltpro.maginaolivo.domain.labour.CrewDraft
import com.isivoltpro.maginaolivo.domain.labour.LabourChange
import com.isivoltpro.maginaolivo.domain.labour.LabourEntry
import com.isivoltpro.maginaolivo.domain.labour.LabourRepository
import com.isivoltpro.maginaolivo.domain.labour.LabourUnit
import com.isivoltpro.maginaolivo.domain.labour.Worker
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/** The truthful totals of one Campaign, for the S70 header. */
data class CampaignHarvest(
    val campaignId: UUID?,
    val farmName: String?,
    val campaignName: String?,
    val summary: HarvestSummary,
)

data class HarvestsUiState(
    val isLoading: Boolean = true,
    val harvests: List<Harvest> = emptyList(),
    val campaigns: List<CampaignHarvest> = emptyList(),
    val contexts: List<HarvestContext> = emptyList(),
    val formErrors: HarvestFormErrors = HarvestFormErrors(),
    val isSaving: Boolean = false,
    val message: String? = null,
    val error: String? = null,
)

class HarvestsViewModel(
    private val harvests: HarvestRepository,
    private val clock: AppClock,
) : ViewModel() {
    private val mutableState = MutableStateFlow(HarvestsUiState())
    val state: StateFlow<HarvestsUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            harvests.observeAll()
                .catch { mutableState.value = mutableState.value.copy(isLoading = false, error = "No pudimos leer la cosecha") }
                .collect { rows ->
                    mutableState.value = mutableState.value.copy(
                        isLoading = false,
                        harvests = rows,
                        campaigns = rows.groupBy { it.campaignId }.map { (campaignId, group) ->
                            CampaignHarvest(campaignId, group.first().farmName, group.first().campaignName, HarvestSummary.of(group))
                        },
                    )
                }
        }
        viewModelScope.launch {
            harvests.observeContexts().catch { }.collect { mutableState.value = mutableState.value.copy(contexts = it) }
        }
    }

    fun create(form: HarvestForm) {
        val (draft, errors) = form.toDraft(clock.today(ZoneId.systemDefault()))
        mutableState.value = mutableState.value.copy(formErrors = errors, message = null)
        if (draft == null) return
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(isSaving = true, error = null)
            mutableState.value = when (val result = harvests.create(draft)) {
                is AppResult.Success -> mutableState.value.copy(isSaving = false, message = "Cosecha guardada", formErrors = HarvestFormErrors())
                is AppResult.Failure -> mutableState.value.failed(result.error)
            }
        }
    }

    fun clearFormErrors() {
        mutableState.value = mutableState.value.copy(formErrors = HarvestFormErrors())
    }

    private fun HarvestsUiState.failed(error: AppError): HarvestsUiState {
        val problem = error.asProblem()
        return if (problem != null) copy(isSaving = false, formErrors = problem.toFormErrors())
        else copy(isSaving = false, error = harvestErrorMessage(error))
    }
}

data class HarvestDetailUiState(
    val isLoading: Boolean = true,
    val harvest: Harvest? = null,
    val context: HarvestContext? = null,
    val formErrors: HarvestFormErrors = HarvestFormErrors(),
    val isSaving: Boolean = false,
    val message: String? = null,
    val error: String? = null,
    val deleted: Boolean = false,
    /** Phase 19B: the Pesadas linked to this Jornada, oldest first. */
    val pesadas: List<Delivery> = emptyList(),
    /** Phase 19D: the jornales of this Jornada and the people to choose from. */
    val labour: List<LabourEntry> = emptyList(),
    val workers: List<Worker> = emptyList(),
    val previousCrew: List<UUID> = emptyList(),
    val labourMessage: String? = null,
    val labourError: String? = null,
    /** Phase 19E: equipment used on this Jornada, and the registered machines to pick from. */
    val equipment: List<EquipmentLine> = emptyList(),
    val machines: List<Machine> = emptyList(),
    val equipmentSaved: Int = 0,
    val equipmentError: String? = null,
)

class HarvestDetailViewModel(
    private val harvestId: UUID,
    private val harvests: HarvestRepository,
    private val clock: AppClock,
    deliveries: DeliveryRepository? = null,
    private val labour: LabourRepository? = null,
    private val equipment: EquipmentRepository? = null,
    machines: MachineRepository? = null,
) : ViewModel() {
    private val mutableState = MutableStateFlow(HarvestDetailUiState())
    val state: StateFlow<HarvestDetailUiState> = mutableState.asStateFlow()
    private var contexts: List<HarvestContext> = emptyList()

    init {
        viewModelScope.launch {
            harvests.observe(harvestId)
                .catch { mutableState.value = mutableState.value.copy(isLoading = false, error = "No pudimos leer la cosecha") }
                .collect { harvest ->
                    mutableState.value = mutableState.value.copy(
                        isLoading = false,
                        harvest = harvest,
                        context = contexts.firstOrNull { it.campaignId == harvest?.campaignId },
                    )
                }
        }
        viewModelScope.launch {
            harvests.observeContexts().catch { }.collect { rows ->
                contexts = rows
                mutableState.value = mutableState.value.copy(
                    context = rows.firstOrNull { it.campaignId == mutableState.value.harvest?.campaignId },
                )
            }
        }
        deliveries?.let { repository ->
            viewModelScope.launch {
                repository.observeAll().catch { }.collect { rows ->
                    mutableState.value = mutableState.value.copy(pesadas = Jornada.linkedTo(harvestId, rows))
                }
            }
        }
        labour?.let { repository ->
            viewModelScope.launch {
                repository.observeForHarvest(harvestId).catch { }
                    .collect { mutableState.value = mutableState.value.copy(labour = it) }
            }
            viewModelScope.launch {
                repository.observeWorkers().catch { }.collect { mutableState.value = mutableState.value.copy(workers = it) }
            }
            viewModelScope.launch {
                mutableState.value = mutableState.value.copy(previousCrew = repository.previousCrew(harvestId))
            }
        }
        equipment?.let { repository ->
            viewModelScope.launch {
                repository.observeForHarvest(harvestId).catch { }
                    .collect { mutableState.value = mutableState.value.copy(equipment = it) }
            }
        }
        machines?.let { repository ->
            viewModelScope.launch {
                repository.observeActive().catch { }.collect { mutableState.value = mutableState.value.copy(machines = it) }
            }
        }
    }

    /** Phase 19E: the whole equipment sheet in one save. */
    fun saveEquipment(lines: List<EquipmentDraftLine>) {
        val repository = equipment ?: return
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(isSaving = true, equipmentError = null)
            mutableState.value = when (val result = repository.replaceForHarvest(harvestId, lines)) {
                is AppResult.Success -> mutableState.value.copy(isSaving = false, equipmentSaved = mutableState.value.equipmentSaved + 1)
                is AppResult.Failure -> mutableState.value.copy(
                    isSaving = false,
                    equipmentError = when (result.error) {
                        is AppError.Conflict -> "La campaña está cerrada: esta jornada ya es histórico"
                        is AppError.Validation -> "Revisa la maquinaria: cantidades de 1 a 50 y un nombre para «Otra»"
                        else -> "No se pudo guardar en el dispositivo. Inténtalo de nuevo."
                    },
                )
            }
        }
    }

    /** Phase 19D: several people in one save. */
    fun recordCrew(workerIds: List<UUID>, unit: LabourUnit, minutes: Int?) =
        labourCall({ if (it == 1) "1 jornal guardado" else "$it jornales guardados" }) {
            labour!!.recordCrew(CrewDraft(harvestId, workerIds, unit, minutes))
        }

    /** Phase 19D: "N jornales" without names. */
    fun recordCount(count: Int, unit: LabourUnit, minutes: Int?) =
        labourCall({ if (count == 1) "1 jornal guardado" else "$count jornales guardados" }) {
            labour!!.recordCount(CountDraft(harvestId, count, unit, minutes))
        }

    fun updateLabour(entryId: UUID, change: LabourChange) = labourCall({ "Jornal corregido" }) { labour!!.update(entryId, change) }

    fun removeLabour(entryId: UUID) = labourCall({ "Jornal quitado" }) { labour!!.remove(entryId) }

    fun addWorker(name: String) = labourCall({ "Persona añadida" }) { labour!!.addWorker(name) }

    fun clearLabourMessages() {
        mutableState.value = mutableState.value.copy(labourMessage = null, labourError = null)
    }

    private fun <T> labourCall(message: (T) -> String, operation: suspend () -> AppResult<T>) {
        if (labour == null) return
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(isSaving = true, labourError = null, labourMessage = null)
            mutableState.value = when (val result = operation()) {
                is AppResult.Success -> mutableState.value.copy(isSaving = false, labourMessage = message(result.value))
                is AppResult.Failure -> mutableState.value.copy(isSaving = false, labourError = labourErrorMessage(result.error))
            }
        }
    }

    fun update(form: HarvestForm) {
        val (draft, errors) = form.toDraft(clock.today(ZoneId.systemDefault()))
        mutableState.value = mutableState.value.copy(formErrors = errors, message = null)
        if (draft == null) return
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(isSaving = true, error = null)
            mutableState.value = when (val result = harvests.update(harvestId, draft)) {
                is AppResult.Success -> mutableState.value.copy(isSaving = false, message = "Cambios guardados", formErrors = HarvestFormErrors())
                is AppResult.Failure -> {
                    val problem = result.error.asProblem()
                    if (problem != null) mutableState.value.copy(isSaving = false, formErrors = problem.toFormErrors())
                    else mutableState.value.copy(isSaving = false, error = harvestErrorMessage(result.error))
                }
            }
        }
    }

    fun delete() {
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(isSaving = true, error = null)
            mutableState.value = when (val result = harvests.delete(harvestId)) {
                is AppResult.Success -> mutableState.value.copy(isSaving = false, deleted = true)
                is AppResult.Failure -> mutableState.value.copy(isSaving = false, error = harvestErrorMessage(result.error))
            }
        }
    }

    fun clearFormErrors() {
        mutableState.value = mutableState.value.copy(formErrors = HarvestFormErrors())
    }
}

/** A validation the repository refused, shown next to the field it concerns. */
private fun AppError.asProblem(): HarvestProblem? =
    (this as? AppError.Validation)?.let { HarvestProblem(it.field ?: "parcels", it.code) }

internal fun labourErrorMessage(error: AppError): String = when (error) {
    is AppError.Validation -> when (error.code) {
        "empty" -> "Elige al menos una persona"
        "already_recorded" -> "Alguna de esas personas ya tiene su jornal en esta jornada"
        "required" -> if (error.field == "name") "Escribe el nombre o apodo" else "Escribe las horas por persona"
        "too_long" -> if (error.field == "name") "El nombre es demasiado largo" else "No puede pasar de 24 horas por persona"
        "not_positive" -> "El número de personas debe ser mayor que cero"
        "too_many" -> "Son demasiadas personas para un día"
        "one_person" -> "Una persona con nombre cuenta un solo jornal"
        else -> "Revisa los jornales"
    }
    is AppError.Conflict -> "La campaña está cerrada: esta jornada ya es histórico"
    is AppError.NotFound -> "Ese jornal ya no está en este dispositivo"
    else -> "No se pudo guardar en el dispositivo. Inténtalo de nuevo."
}

internal fun harvestErrorMessage(error: AppError): String = when (error) {
    is AppError.Validation -> harvestProblemMessage(HarvestProblem(error.field ?: "parcels", error.code))
    is AppError.NotFound -> "La cosecha o la finca ya no está en este dispositivo"
    is AppError.Conflict -> when (error.resource) {
        "no_running_campaign" -> "Esta finca no tiene una campaña activa o en recolección"
        "closed_campaign" -> "La campaña está cerrada: esta cosecha ya es histórico y no se modifica"
        "archived_farm" -> "La finca está archivada"
        else -> "No se pudo guardar por un conflicto con otros datos"
    }
    is AppError.Storage -> "No se pudo guardar en el dispositivo. Inténtalo de nuevo."
    else -> "Algo no ha ido bien. Inténtalo de nuevo."
}
