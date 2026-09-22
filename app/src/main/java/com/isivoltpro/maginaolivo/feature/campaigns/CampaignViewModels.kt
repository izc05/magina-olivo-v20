package com.isivoltpro.maginaolivo.feature.campaigns

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.data.local.model.CampaignStatus
import com.isivoltpro.maginaolivo.domain.campaign.Campaign
import com.isivoltpro.maginaolivo.domain.campaign.CampaignPreparationChanges
import com.isivoltpro.maginaolivo.domain.campaign.CampaignRepository
import com.isivoltpro.maginaolivo.domain.campaign.CampaignParcelOption
import com.isivoltpro.maginaolivo.domain.campaign.NewCampaign
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job

data class CampaignDraft(
    val name: String = "",
    val startDate: LocalDate? = null,
    val parcelIds: Set<UUID> = emptySet(),
    val notes: String = "",
)

data class FarmCampaignsUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val current: List<Campaign> = emptyList(),
    val history: List<Campaign> = emptyList(),
    val parcels: List<CampaignParcelOption> = emptyList(),
    val nameError: String? = null,
    val dateError: String? = null,
    val error: String? = null,
    val message: String? = null,
)

class FarmCampaignsViewModel(private val farmId: UUID, private val repository: CampaignRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(FarmCampaignsUiState())
    val state: StateFlow<FarmCampaignsUiState> = mutableState.asStateFlow()

    init { viewModelScope.launch {
        repository.observeForFarm(farmId).collect { campaigns ->
            mutableState.value = mutableState.value.copy(
                isLoading = false,
                current = campaigns.filter { it.status != CampaignStatus.CLOSED },
                history = campaigns.filter { it.status == CampaignStatus.CLOSED },
            )
        }
    }; viewModelScope.launch { repository.observeSelectableParcels(farmId).collect {
        mutableState.value = mutableState.value.copy(parcels = it)
    } } }

    fun create(draft: CampaignDraft) {
        if (!validate(draft)) return
        mutate("Campaña guardada en este dispositivo") {
            when (val result = repository.create(NewCampaign(farmId, draft.name.trim(), draft.startDate!!, draft.parcelIds, draft.notes.nullIfBlank()))) {
                is AppResult.Success -> AppResult.Success(Unit)
                is AppResult.Failure -> result
            }
        }
    }

    private fun validate(draft: CampaignDraft): Boolean {
        val nameError = if (draft.name.isBlank()) "Escribe un nombre para la campaña" else null
        val dateError = if (draft.startDate == null) "Selecciona una fecha de inicio" else null
        mutableState.value = mutableState.value.copy(nameError = nameError, dateError = dateError)
        return nameError == null && dateError == null
    }

    private fun mutate(message: String, operation: suspend () -> AppResult<Unit>) = viewModelScope.launch {
        mutableState.value = mutableState.value.copy(isSaving = true, error = null, message = null)
        mutableState.value = when (operation()) {
            is AppResult.Success -> mutableState.value.copy(isSaving = false, message = message)
            is AppResult.Failure -> mutableState.value.copy(isSaving = false, error = "No se pudo guardar en este dispositivo")
        }
    }
}

data class CampaignDetailUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val campaign: Campaign? = null,
    val parcels: List<CampaignParcelOption> = emptyList(),
    val error: String? = null,
    val message: String? = null,
)

class CampaignDetailViewModel(private val campaignId: UUID, private val repository: CampaignRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(CampaignDetailUiState())
    val state: StateFlow<CampaignDetailUiState> = mutableState.asStateFlow()

    private var parcelsJob: Job? = null
    init { viewModelScope.launch { repository.observe(campaignId).collect { campaign ->
        mutableState.value = mutableState.value.copy(isLoading = false, campaign = campaign,
            error = if (campaign == null) "La campaña no está disponible" else null)
        if (campaign != null && parcelsJob == null) {
            parcelsJob = viewModelScope.launch { repository.observeSelectableParcels(campaign.farmId).collect { parcels ->
                mutableState.value = mutableState.value.copy(parcels = parcels)
            } }
        }
    } } }

    fun update(draft: CampaignDraft) {
        if (draft.name.isBlank() || draft.startDate == null) {
            mutableState.value = mutableState.value.copy(error = "Revisa el nombre y la fecha de inicio")
            return
        }
        mutate("Cambios guardados") { repository.updatePreparation(campaignId,
            CampaignPreparationChanges(draft.name.trim(), draft.startDate, draft.parcelIds, draft.notes.nullIfBlank())) }
    }
    fun activate() = mutate("Campaña activada") { repository.activate(campaignId) }
    fun markHarvest() = mutate("Recolección iniciada") { repository.markHarvest(campaignId) }
    fun close(endDate: LocalDate) = mutate("Campaña cerrada") { repository.close(campaignId, endDate) }
    fun reopen() = mutate("Campaña reabierta") { repository.reopen(campaignId) }
    fun archivePreparation() = mutate("Borrador archivado") { repository.archivePreparation(campaignId) }
    fun consumeMessage() { mutableState.value = mutableState.value.copy(message = null) }

    private fun mutate(message: String, operation: suspend () -> AppResult<Unit>) = viewModelScope.launch {
        mutableState.value = mutableState.value.copy(isSaving = true, error = null, message = null)
        mutableState.value = when (operation()) {
            is AppResult.Success -> mutableState.value.copy(isSaving = false, message = message)
            is AppResult.Failure -> mutableState.value.copy(isSaving = false, error = "La operación no se pudo completar")
        }
    }
}

private fun String.nullIfBlank(): String? = trim().takeIf(String::isNotEmpty)
