package com.isivoltpro.maginaolivo.feature.harvests

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.domain.campaign.Campaign
import com.isivoltpro.maginaolivo.domain.campaign.CampaignRepository
import com.isivoltpro.maginaolivo.domain.expense.ExpenseRepository
import com.isivoltpro.maginaolivo.domain.harvest.Harvest
import com.isivoltpro.maginaolivo.domain.harvest.HarvestRepository
import com.isivoltpro.maginaolivo.domain.labour.*
import java.util.UUID
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

internal data class LabourPaymentsUiState(
    val isLoading: Boolean = true,
    val campaign: Campaign? = null,
    val days: List<Harvest> = emptyList(),
    val entries: List<LabourEntry> = emptyList(),
    val payments: List<LabourPayment> = emptyList(),
    val accounts: List<LabourAccount> = emptyList(),
    val readError: String? = null,
    val isSaving: Boolean = false,
    val error: String? = null,
    val saved: Int = 0,
)

internal class LabourPaymentsViewModel(campaignId: UUID, campaigns: CampaignRepository, private val labour: LabourRepository, harvests: HarvestRepository, expenses: ExpenseRepository) : ViewModel() {
    private val reading = combine(campaigns.observe(campaignId), harvests.observeForCampaign(campaignId), labour.observeForCampaign(campaignId), expenses.observeAll(), labour.observePayments(campaignId)) { campaign, days, entries, costs, payments ->
        val accounts = runCatching { labourAccounts(campaignId, entries, costs, payments) }
        LabourPaymentsUiState(isLoading = false, campaign = campaign, days = days, entries = entries, payments = payments, accounts = accounts.getOrDefault(emptyList()), readError = if (accounts.isFailure) "El saldo no está disponible. Los jornales y los pagos se están actualizando; vuelve a abrir el detalle si persiste." else null)
    }.catch { emit(LabourPaymentsUiState(isLoading = false, readError = "No pudimos leer los jornales y sus pagos. Vuelve a abrir el detalle.")) }
    private val writing = MutableStateFlow(LabourPaymentsUiState())
    val state = combine(reading, writing) { read, write -> read.copy(isSaving = write.isSaving, error = write.error, saved = write.saved) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LabourPaymentsUiState())

    fun record(payment: LabourPayment) = write { labour.recordPayment(payment) }
    fun remove(id: UUID) = write { labour.removePayment(id) }
    fun clearError() { writing.update { it.copy(error = null) } }
    private fun write(operation: suspend () -> AppResult<*>) {
        if (writing.value.isSaving) return
        writing.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            when (val result = operation()) {
                is AppResult.Success -> writing.update { it.copy(isSaving = false, saved = it.saved + 1) }
                is AppResult.Failure -> writing.update { it.copy(isSaving = false, error = labourErrorMessage(result.error)) }
            }
        }
    }
}
