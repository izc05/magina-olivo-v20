package com.isivoltpro.maginaolivo.feature.notebook

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.isivoltpro.maginaolivo.data.local.model.isRunning
import com.isivoltpro.maginaolivo.domain.activity.ActivityRepository
import com.isivoltpro.maginaolivo.domain.analytics.CampaignComparison
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
import com.isivoltpro.maginaolivo.domain.notebook.FarmNotebook
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

data class NotebookUiState(
    val isLoading: Boolean = true,
    val campaigns: List<Campaign> = emptyList(),
    val selectedCampaignId: UUID? = null,
    val notebook: CampaignNotebook? = null,
    val error: String? = null,
    /** Phase 19G: every Campaign of the Farm, oldest first, for the year-over-year view. */
    val comparison: List<CampaignComparison> = emptyList(),
    val labourPayments: List<com.isivoltpro.maginaolivo.domain.labour.LabourPayment> = emptyList(),
    /** #417: the Farm's own Cuaderno (Diario, Fitosanitario, Gastos), with or without a Campaign. */
    val farmNotebook: FarmNotebook? = null,
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

    private val current = combine(campaigns.observeForFarm(farmId), chosen) { list, pick -> list to pick }
        .flatMapLatest { (list, pick) ->
            val campaign = list.firstOrNull { it.id == pick } ?: defaultCampaign(list)
            if (campaign == null) {
                flowOf(NotebookUiState(isLoading = false, campaigns = list))
            } else {
                combine(
                    activities.observeForFarm(farmId),
                    // #509: campaignId-null legacy rows must be resolved against every
                    // Campaign of the Farm before they can enter one Notebook.
                    harvests.observeAll(),
                    deliveries.observeAll(),
                    expenses.observeAll(),
                    combine(
                        labour?.observeForCampaign(campaign.id) ?: flowOf(emptyList<LabourEntry>()),
                        equipment?.observeForCampaign(campaign.id) ?: flowOf(emptyList<EquipmentLine>()),
                        labour?.observePayments(campaign.id) ?: flowOf(emptyList<com.isivoltpro.maginaolivo.domain.labour.LabourPayment>()),
                    ) { jornales, maquinaria, payments -> Triple(jornales, maquinaria, payments) },
                ) { acts, crops, weighings, costs, (jornales, maquinaria, payments) ->
                    NotebookUiState(
                        isLoading = false,
                        campaigns = list,
                        selectedCampaignId = campaign.id,
                        labourPayments = payments,
                        notebook = CampaignNotebook.project(
                            campaign, acts, crops, weighings, costs, jornales, maquinaria,
                            candidateCampaigns = list,
                        ),
                    )
                }
            }
        }

    /** #417: the jornales and equipment of every Campaign of the Farm, for its Jornada rows. */
    private val farmCrews = campaigns.observeForFarm(farmId).flatMapLatest { list ->
        if (list.isEmpty() || (labour == null && equipment == null)) {
            flowOf(emptyList<LabourEntry>() to emptyList<EquipmentLine>())
        } else {
            combine(list.map { campaign ->
                combine(
                    labour?.observeForCampaign(campaign.id) ?: flowOf(emptyList<LabourEntry>()),
                    equipment?.observeForCampaign(campaign.id) ?: flowOf(emptyList<EquipmentLine>()),
                ) { jornales, maquinaria -> jornales to maquinaria }
            }) { parts -> parts.flatMap { it.first } to parts.flatMap { it.second } }
        }
    }

    /**
     * Phase 19G: the same projection for every Campaign of the Farm (read-only). #449: with each
     * Campaign's jornales and machinery, so an unconfirmed cost is never drawn as complete.
     */
    private val comparison = combine(
        campaigns.observeForFarm(farmId),
        activities.observeForFarm(farmId),
        harvests.observeAll(),
        deliveries.observeAll(),
        combine(expenses.observeAll(), farmCrews) { costs, crews -> costs to crews },
    ) { list, acts, crops, weighings, (costs, crews) ->
        val (jornales, maquinaria) = crews
        CampaignComparison.of(
            list.map {
                CampaignNotebook.project(
                    it, acts, crops, weighings, costs, jornales, maquinaria,
                    candidateCampaigns = list,
                )
            },
        )
    }

    /** #417: the Farm's Cuaderno, built whether or not any Campaign exists. */
    private val farmNotebook = combine(
        activities.observeForFarm(farmId),
        harvests.observeAll(),
        deliveries.observeAll(),
        expenses.observeAll(),
        farmCrews,
    ) { acts, crops, weighings, costs, (jornales, maquinaria) ->
        FarmNotebook.of(farmId, acts, crops, weighings, costs, jornales, maquinaria)
    }

    val state: StateFlow<NotebookUiState> = combine(current, comparison, farmNotebook) { base, years, farm ->
        base.copy(comparison = years, farmNotebook = farm)
    }
        .catch { emit(NotebookUiState(isLoading = false, error = "No hemos podido abrir el cuaderno en este dispositivo.")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotebookUiState())

    fun selectCampaign(id: UUID) {
        chosen.value = id
    }

    internal companion object {
        /** The running Campaign (Activa; legacy HARVEST counts), else the most recent one. */
        fun defaultCampaign(list: List<Campaign>): Campaign? =
            list.filter { it.status.isRunning }.maxByOrNull { it.startDate }
                ?: list.maxByOrNull { it.startDate }
    }
}
