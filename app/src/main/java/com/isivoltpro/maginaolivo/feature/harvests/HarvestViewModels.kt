package com.isivoltpro.maginaolivo.feature.harvests

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.isivoltpro.maginaolivo.core.common.AppError
import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.core.time.AppClock
import com.isivoltpro.maginaolivo.domain.harvest.Harvest
import com.isivoltpro.maginaolivo.domain.harvest.HarvestContext
import com.isivoltpro.maginaolivo.domain.harvest.HarvestProblem
import com.isivoltpro.maginaolivo.domain.harvest.HarvestRepository
import com.isivoltpro.maginaolivo.domain.harvest.HarvestSummary
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
)

class HarvestDetailViewModel(
    private val harvestId: UUID,
    private val harvests: HarvestRepository,
    private val clock: AppClock,
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
