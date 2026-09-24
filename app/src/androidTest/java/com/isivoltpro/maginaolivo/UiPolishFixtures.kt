package com.isivoltpro.maginaolivo

import com.isivoltpro.maginaolivo.data.local.model.ActivityStatus
import com.isivoltpro.maginaolivo.data.local.model.CampaignStatus
import com.isivoltpro.maginaolivo.domain.activity.Activity
import com.isivoltpro.maginaolivo.domain.activity.ActivityParcelTarget
import com.isivoltpro.maginaolivo.domain.activity.ActivityType
import com.isivoltpro.maginaolivo.domain.activity.AgendaEntry
import com.isivoltpro.maginaolivo.domain.agenda.ActivityPlanning
import com.isivoltpro.maginaolivo.domain.agenda.Agenda
import com.isivoltpro.maginaolivo.domain.agenda.Reminder
import com.isivoltpro.maginaolivo.domain.agenda.ReminderKind
import com.isivoltpro.maginaolivo.domain.campaign.Campaign
import com.isivoltpro.maginaolivo.domain.campaign.CampaignParcelSnapshot
import com.isivoltpro.maginaolivo.domain.farm.Farm
import com.isivoltpro.maginaolivo.domain.harvest.Harvest
import com.isivoltpro.maginaolivo.domain.harvest.HarvestAllocation
import com.isivoltpro.maginaolivo.domain.harvest.HarvestShare
import com.isivoltpro.maginaolivo.domain.harvest.HarvestSummary
import com.isivoltpro.maginaolivo.feature.agenda.AgendaUiState
import com.isivoltpro.maginaolivo.feature.harvests.CampaignHarvest
import com.isivoltpro.maginaolivo.feature.harvests.HarvestsUiState
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

/**
 * Sample data for the UI-polish screenshots only. It never ships in the app and is
 * named as sample data; no figure here is presented to a farmer.
 */
internal object UiPolishFixtures {
    private val workspace = UUID.fromString("10000000-0000-0000-0000-00000000aa01")
    private val farmA = UUID.fromString("20000000-0000-0000-0000-00000000aa01")
    private val farmB = UUID.fromString("20000000-0000-0000-0000-00000000aa02")
    private val parcel1 = UUID.fromString("30000000-0000-0000-0000-00000000aa01")
    private val parcel2 = UUID.fromString("30000000-0000-0000-0000-00000000aa02")
    val today: LocalDate = LocalDate.now()

    val farms = listOf(
        Farm(farmA, workspace, "Finca de ejemplo", null, "Huelma", "Jaén", null, null, 2, 52_000.0, "Campaña de ejemplo", null, 1),
        Farm(farmB, workspace, "Olivar de muestra", null, null, null, null, null, 1, null, null, null, 1),
    )

    val campaign = Campaign(
        UUID.fromString("40000000-0000-0000-0000-00000000aa01"), workspace, farmA, "Campaña de ejemplo",
        today.minusDays(40), null, CampaignStatus.HARVEST, null,
        listOf(
            CampaignParcelSnapshot(parcel1, "Finca de ejemplo", "Parcela Norte", 30_000.0, null, null),
            CampaignParcelSnapshot(parcel2, "Finca de ejemplo", "Parcela Sur", 22_000.0, null, null),
        ),
        1,
    )

    val activity = Activity(
        id = UUID.fromString("50000000-0000-0000-0000-00000000aa01"),
        workspaceId = workspace,
        farmId = farmA,
        campaignId = null,
        type = ActivityType.IRRIGATION,
        status = ActivityStatus.PLANNED,
        activityDate = today.plusDays(1),
        description = "Riego de ejemplo",
        notes = null,
        targets = listOf(ActivityParcelTarget(parcel1, "Parcela Norte", 30_000.0)),
        version = 1,
        planning = ActivityPlanning(LocalTime.of(7, 0), 240, 2, "Cuadrilla de ejemplo"),
        reminders = listOf(
            Reminder(UUID.randomUUID(), ReminderKind.PREVIOUS_DAY, Instant.now().plusSeconds(3_600), true, null),
        ),
    )

    private val entries = listOf(
        entry("Riego de ejemplo", ActivityType.IRRIGATION, today, LocalTime.of(7, 0), ActivityPlanning(LocalTime.of(7, 0), 240, 2, null)),
        entry("Poda de ejemplo", ActivityType.PRUNING, today.plusDays(2), null, ActivityPlanning(expectedPeopleCount = 4, crewText = "Cuadrilla de ejemplo")),
        entry("Tratamiento de ejemplo", ActivityType.PHYTOSANITARY, today.plusDays(12), LocalTime.of(9, 30), null),
    )

    val agenda = AgendaUiState(
        isLoading = false,
        today = today,
        sections = Agenda.group(entries, today, { it.activityDate }, { it.planning?.startTime }),
    )

    private val harvests = listOf(
        Harvest(UUID.randomUUID(), workspace, farmA, campaign.id, today.minusDays(3), 2_850_000, listOf(HarvestShare(parcel1, "Parcela Norte", HarvestAllocation.EXACT, 2_850_000)), null, 6, null, null, 1, "Finca de ejemplo", "Campaña de ejemplo"),
        Harvest(UUID.randomUUID(), workspace, farmA, campaign.id, today.minusDays(1), 1_920_000, listOf(HarvestShare(parcel2, "Parcela Sur", HarvestAllocation.UNALLOCATED, null)), null, 5, null, null, 1, "Finca de ejemplo", "Campaña de ejemplo"),
    )

    val harvestState = HarvestsUiState(
        isLoading = false,
        harvests = harvests,
        campaigns = listOf(CampaignHarvest(campaign.id, "Finca de ejemplo", "Campaña de ejemplo", HarvestSummary.of(harvests))),
    )

    private fun entry(description: String, type: ActivityType, date: LocalDate, time: LocalTime?, planning: ActivityPlanning?) = AgendaEntry(
        activityId = UUID.randomUUID(),
        farmId = farmA,
        farmName = "Finca de ejemplo",
        type = type,
        activityDate = date,
        description = description,
        parcelNames = listOf("Parcela Norte"),
        planning = planning?.copy(startTime = time),
        reminders = emptyList(),
    )
}
