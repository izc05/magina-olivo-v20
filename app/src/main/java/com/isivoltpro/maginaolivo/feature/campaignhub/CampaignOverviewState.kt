package com.isivoltpro.maginaolivo.feature.campaignhub

import com.isivoltpro.maginaolivo.app.LocalPersistence
import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.domain.analytics.FarmOverview
import com.isivoltpro.maginaolivo.domain.analytics.OliveSeason
import com.isivoltpro.maginaolivo.domain.campaign.Campaign
import com.isivoltpro.maginaolivo.domain.expense.Expense
import com.isivoltpro.maginaolivo.domain.expense.RecollectionCostCompleteness
import com.isivoltpro.maginaolivo.domain.farm.Farm
import com.isivoltpro.maginaolivo.domain.farm.FarmRepository
import com.isivoltpro.maginaolivo.domain.workspace.WorkspaceRepository
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart

internal data class CampaignFarmsState(
    val isLoading: Boolean = true,
    val workspaceId: UUID? = null,
    val farms: List<Farm> = emptyList(),
    val error: String? = null,
)

/** Observe only. Entering this root must not create a workspace, campaign or outbox intent. */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun observeCampaignFarms(workspaces: WorkspaceRepository, farms: FarmRepository): Flow<CampaignFarmsState> =
    workspaces.observeActiveWorkspace().flatMapLatest { result ->
        when (result) {
            is AppResult.Failure -> flowOf(CampaignFarmsState(isLoading = false,
                error = "Elige un espacio de trabajo autorizado en Cuenta para consultar tus fincas."))
            is AppResult.Success -> combine(farms.observeActive(result.value), farms.observeArchived(result.value)) { active, archived ->
                CampaignFarmsState(isLoading = false, workspaceId = result.value,
                    farms = (active + archived).distinctBy { it.id }.filter { it.workspaceId == result.value })
            }.onStart { emit(CampaignFarmsState()) }
        }
    }.catch { emit(CampaignFarmsState(isLoading = false, error = "No pudimos abrir tus fincas. Vuelve a intentarlo.")) }

internal data class CampaignOverviewState(
    val isLoading: Boolean = true,
    val overviews: List<FarmOverview> = emptyList(),
    val campaigns: List<Campaign> = emptyList(),
    val unassigned: List<Expense> = emptyList(),
    val error: String? = null,
)

/** The holding projection previously read in Mi Campo. No new totals or persisted grouping. */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun observeCampaignOverview(persistence: LocalPersistence, workspaceId: UUID, farms: List<Farm>): Flow<CampaignOverviewState> {
    val campaigns = if (farms.isEmpty()) flowOf(emptyList()) else
        combine(farms.map { persistence.campaignRepository.observeForFarm(it.id) }) { lists -> lists.flatMap { it } }
    return campaigns.flatMapLatest { loaded ->
        val scoped = loaded.filter { it.workspaceId == workspaceId }
        // Wait for all crew reads before claiming completeness; money remains the posted ledger.
        val crews = if (scoped.isEmpty()) flowOf(emptyMap<UUID, Pair<List<com.isivoltpro.maginaolivo.domain.labour.LabourEntry>, List<com.isivoltpro.maginaolivo.domain.equipment.EquipmentLine>>>()) else combine(scoped.map { campaign ->
            combine(persistence.labourRepository.observeForCampaign(campaign.id), persistence.equipmentRepository.observeForCampaign(campaign.id)) { labour, equipment ->
                campaign.id to (labour to equipment)
            }
        }) { parts -> parts.toMap() }
        combine(persistence.deliveryRepository.observeAll(), persistence.expenseRepository.observeAll(), crews) { deliveries, expenses, loadedCrews ->
            val scopedExpenses = expenses.filter { it.workspaceId == workspaceId }
            val incomplete = scoped.filter { campaign ->
                val (labour, equipment) = loadedCrews.getValue(campaign.id)
                !RecollectionCostCompleteness.of(labour, equipment, scopedExpenses.filter { it.campaignId == campaign.id }).complete
            }.map { it.id }.toSet()
            CampaignOverviewState(isLoading = false,
                overviews = OliveSeason.available(scoped).map { season ->
                    FarmOverview.of(season, farms, scoped, deliveries.filter { it.workspaceId == workspaceId }, scopedExpenses, incomplete)
                }, campaigns = scoped, unassigned = scopedExpenses.filter { it.farmId == null })
        }
    }.onStart { emit(CampaignOverviewState()) }
        .catch { emit(CampaignOverviewState(isLoading = false, error = "No pudimos consultar el resumen. Los importes no están disponibles.")) }
}
