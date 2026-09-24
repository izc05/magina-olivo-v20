package com.isivoltpro.maginaolivo.feature.notebook

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.isivoltpro.maginaolivo.data.local.model.CampaignStatus
import com.isivoltpro.maginaolivo.domain.activity.ActivityRepository
import com.isivoltpro.maginaolivo.domain.campaign.Campaign
import com.isivoltpro.maginaolivo.domain.campaign.CampaignRepository
import com.isivoltpro.maginaolivo.domain.delivery.DeliveryRepository
import com.isivoltpro.maginaolivo.domain.expense.ExpenseRepository
import com.isivoltpro.maginaolivo.domain.harvest.HarvestRepository
import com.isivoltpro.maginaolivo.domain.equipment.EquipmentLine
import com.isivoltpro.maginaolivo.domain.equipment.EquipmentRepository
import com.isivoltpro.maginaolivo.domain.labour.LabourEntry
import com.isivoltpro.maginaolivo.domain.labour.LabourRepository
import com.isivoltpro.maginaolivo.domain.notebook.CampaignNotebook
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

enum class NotebookTab(val label: String) { WORKS("Trabajos"), RECOLLECTION("Recolección"), SUMMARY("Resumen") }

data class NotebookUiState(
    val isLoading: Boolean = true,
    val campaigns: List<Campaign> = emptyList(),
    val selectedCampaignId: UUID? = null,
    val notebook: CampaignNotebook? = null,
    val error: String? = null,
)

/**
 * Phase 19A: the Cuaderno of a Farm, one Campaign at a time. Everything comes from the local
 * repositories (offline); the Campaign in progress is opened first.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NotebookViewModel(
    private val farmId: UUID,
    campaigns: CampaignRepository,
    private val activities: ActivityRepository,
    private val harvests: HarvestRepository,
    private val deliveries: DeliveryRepository,
    private val expenses: ExpenseRepository,
    private val labour: LabourRepository? = null,
    private val equipment: EquipmentRepository? = null,
) : ViewModel() {
    private val chosen = MutableStateFlow<UUID?>(null)

    val state: StateFlow<NotebookUiState> = combine(campaigns.observeForFarm(farmId), chosen) { list, pick -> list to pick }
        .flatMapLatest { (list, pick) ->
            val campaign = list.firstOrNull { it.id == pick } ?: defaultCampaign(list)
            if (campaign == null) {
                flowOf(NotebookUiState(isLoading = false, campaigns = list))
            } else {
                combine(
                    activities.observeForFarm(farmId),
                    harvests.observeForCampaign(campaign.id),
                    deliveries.observeForCampaign(campaign.id),
                    expenses.observeAll(),
                    combine(
                        labour?.observeForCampaign(campaign.id) ?: flowOf(emptyList<LabourEntry>()),
                        equipment?.observeForCampaign(campaign.id) ?: flowOf(emptyList<EquipmentLine>()),
                    ) { jornales, maquinaria -> jornales to maquinaria },
                ) { acts, crops, weighings, costs, (jornales, maquinaria) ->
                    NotebookUiState(
                        isLoading = false,
                        campaigns = list,
                        selectedCampaignId = campaign.id,
                        notebook = CampaignNotebook.project(campaign, acts, crops, weighings, costs, jornales, maquinaria),
                    )
                }
            }
        }
        .catch { emit(NotebookUiState(isLoading = false, error = "No hemos podido abrir el cuaderno en este dispositivo.")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotebookUiState())

    fun selectCampaign(id: UUID) {
        chosen.value = id
    }

    internal companion object {
        /** The Campaign in progress (harvest first, then active), else the most recent one. */
        fun defaultCampaign(list: List<Campaign>): Campaign? =
            list.filter { it.status == CampaignStatus.HARVEST }.maxByOrNull { it.startDate }
                ?: list.filter { it.status == CampaignStatus.ACTIVE }.maxByOrNull { it.startDate }
                ?: list.maxByOrNull { it.startDate }
    }
}
