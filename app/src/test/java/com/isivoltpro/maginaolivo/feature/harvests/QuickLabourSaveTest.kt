package com.isivoltpro.maginaolivo.feature.harvests

import com.isivoltpro.maginaolivo.core.common.*
import com.isivoltpro.maginaolivo.data.local.model.CampaignStatus
import com.isivoltpro.maginaolivo.domain.harvest.*
import com.isivoltpro.maginaolivo.domain.labour.*
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class QuickLabourSaveTest {
    private val farm = UUID.randomUUID()
    private val campaign = UUID.randomUUID()
    private val day = UUID.randomUUID()
    private val date = LocalDate.of(2026, 10, 13)
    private val context = HarvestContext(farm, "Finca", campaign, "2026/27", CampaignStatus.ACTIVE,
        date.minusDays(2), listOf(HarvestParcelOption(UUID.randomUUID(), "Parcela")))
    private val draft = CrewDraft(UUID.randomUUID(), listOf(UUID.randomUUID()), LabourUnit.HOURS, 180,
        LabourRateSnapshot(1200, "EUR", date, LabourRateBasis.HOUR))
    private fun harvest(campaignId: UUID = campaign, farmId: UUID = farm, dayDate: LocalDate = date) =
        Harvest(day, UUID.randomUUID(), farmId, campaignId, dayDate, 0, emptyList(), null, null, null, null, 1)

    @Test fun selectedCampaignSavesOnlyOneCrewWithActualDayAndSamePrice() = runTest {
        var opened = 0
        var recorded: CrewDraft? = null
        val result = saveCampaignQuickLabour(farm, campaign, date, draft, listOf(context),
            { f, d -> assertEquals(farm, f); assertEquals(date, d); opened++; AppResult.Success(day) },
            { harvest() }, { recorded = it; AppResult.Success(1) })
        assertEquals(AppResult.Success(1), result)
        assertEquals(1, opened)
        assertEquals(draft.copy(harvestId = day), recorded)
    }

    @Test fun changedCampaignDoesNotOpenOrWrite() = runTest {
        val result = saveCampaignQuickLabour(farm, campaign, date, draft,
            listOf(context.copy(campaignId = UUID.randomUUID())),
            { _, _ -> error("Must not open a day") }, { error("Must not read a day") }, { error("Must not save") })
        assertTrue(result is AppResult.Failure)
    }

    @Test fun twoOperativeCampaignsNeverChooseFirstSilently() = runTest {
        val result = saveCampaignQuickLabour(farm, campaign, date, draft,
            listOf(context, context.copy(campaignId = UUID.randomUUID())),
            { _, _ -> error("Ambiguous day") }, { error("Unexpected read") }, { error("Unexpected write") })
        assertTrue(result is AppResult.Failure)
    }

    @Test fun dayOfAnotherCampaignNeverReceivesCrew() = runTest {
        val result = saveCampaignQuickLabour(farm, campaign, date, draft, listOf(context),
            { _, _ -> AppResult.Success(day) }, { harvest(campaignId = UUID.randomUUID()) }, { error("Wrong campaign write") })
        assertTrue(result is AppResult.Failure)
    }

    @Test fun wrongDateOrFarmNeverReceivesCrew() = runTest {
        for (wrong in listOf(harvest(dayDate = date.minusDays(1)), harvest(farmId = UUID.randomUUID()))) {
            val result = saveCampaignQuickLabour(farm, campaign, date, draft, listOf(context),
                { _, _ -> AppResult.Success(day) }, { wrong }, { error("Wrong context write") })
            assertTrue(result is AppResult.Failure)
        }
    }

    @Test fun openingErrorPreservesFailureAndDoesNotWrite() = runTest {
        val failed = AppResult.Failure(AppError.Offline())
        val result = saveCampaignQuickLabour(farm, campaign, date, draft, listOf(context),
            { _, _ -> failed }, { error("Read after failed open") }, { error("Write after failed open") })
        assertEquals(failed, result)
    }
}
