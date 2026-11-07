package com.isivoltpro.maginaolivo.feature.campaignhub

import com.isivoltpro.maginaolivo.domain.analytics.CampaignComparison
import com.isivoltpro.maginaolivo.domain.cycle.CycleSnapshot
import com.isivoltpro.maginaolivo.domain.notebook.CampaignNotebook
import com.isivoltpro.maginaolivo.feature.notebook.NotebookUiState
import java.util.UUID

/** Read projection by explicit UUID links, including late invoices. Never classifies by dates. */
internal fun NotebookUiState.forAgriculturalCycle(snapshot: CycleSnapshot,cycleId: UUID): NotebookUiState {
    val cycle=snapshot.cycles.firstOrNull {it.id==cycleId}
    if(cycle==null || cycle.workspaceId!=snapshot.scope.workspaceId || cycle.farmId!=snapshot.scope.farmId ||
        farmNotebook?.farmId?.let {it!=cycle.farmId}==true) return copy(
        isLoading=false,error="No podemos comprobar la finca de esta campaña.",campaigns=emptyList(),
        selectedCampaignId=null,notebook=null,farmNotebook=null,comparison=emptyList(),labourPayments=emptyList())
    val linked=snapshot.records.filter {it.cycleId==cycleId}
    val activities=linked.filter {it.kind=="ACTIVITY"}.map {it.id}.toSet()
    val expenses=linked.filter {it.kind=="EXPENSE"}.map {it.id}.toSet()
    val recollections=linked.filter {it.kind=="RECOLLECTION"}.map {it.id}.toSet()
    val campaigns=campaigns.filter {it.id in recollections && it.farmId==cycle.farmId && it.workspaceId==cycle.workspaceId}
    val farm=farmNotebook?.let {source->
        val days=source.harvests.filter {it.campaignId in recollections}
        val dayIds=days.map {it.id}.toSet()
        source.copy(activities=source.activities.filter {it.id in activities && it.workspaceId==cycle.workspaceId},
            expenses=source.expenses.filter {it.id in expenses && it.workspaceId==cycle.workspaceId},
            harvests=days,deliveries=source.deliveries.filter {it.campaignId in recollections},
            labour=source.labour.filter {it.harvestId in dayIds},equipment=source.equipment.filter {it.harvestId in dayIds})
    }
    val notebooks=if(farm==null) emptyList() else campaigns.map {campaign->CampaignNotebook.project(
        campaign, farm.activities.filter {it.campaignId==campaign.id},
        farm.harvests.filter {it.campaignId==campaign.id},farm.deliveries.filter {it.campaignId==campaign.id},
        farm.expenses.filter {it.campaignId==campaign.id},farm.labour,farm.equipment,candidateCampaigns=campaigns)
    }
    val current=notebooks.firstOrNull {it.campaign.id==selectedCampaignId}
    return copy(campaigns=campaigns,selectedCampaignId=current?.campaign?.id,notebook=current,
        farmNotebook=farm,comparison=CampaignComparison.of(notebooks),
        labourPayments=labourPayments.filter {it.campaignId==current?.campaign?.id})
}
