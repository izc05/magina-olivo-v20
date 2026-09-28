package com.isivoltpro.maginaolivo.feature.notebook

import com.isivoltpro.maginaolivo.data.local.model.CampaignStatus
import com.isivoltpro.maginaolivo.data.local.model.isRunning
import com.isivoltpro.maginaolivo.domain.campaign.Campaign
import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** CR-010: Borrador → Activa → Cerrada; the legacy HARVEST state reads as Activa. */
class CampaignRunningTest {
    @Test fun activeAndLegacyHarvestAreRunningDraftAndClosedAreNot() {
        assertTrue(CampaignStatus.ACTIVE.isRunning)
        assertTrue(CampaignStatus.HARVEST.isRunning)
        assertFalse(CampaignStatus.PREPARATION.isRunning)
        assertFalse(CampaignStatus.CLOSED.isRunning)
    }

    @Test fun theCuadernoOpensOnTheRunningCampaignWhateverItsLegacyState() {
        val closed = campaign("2025/26", 2025, CampaignStatus.CLOSED)
        val active = campaign("2026/27", 2026, CampaignStatus.ACTIVE)
        val draft = campaign("2027/28", 2027, CampaignStatus.PREPARATION)
        assertEquals(active, NotebookViewModel.defaultCampaign(listOf(closed, active, draft)))

        val legacy = active.copy(status = CampaignStatus.HARVEST)
        assertEquals(legacy, NotebookViewModel.defaultCampaign(listOf(closed, legacy, draft)))

        // Nothing running: the most recent one.
        assertEquals(draft, NotebookViewModel.defaultCampaign(listOf(closed, draft)))
    }

    private fun campaign(name: String, year: Int, status: CampaignStatus) = Campaign(
        id = UUID.randomUUID(), workspaceId = WORKSPACE, farmId = FARM, name = name,
        startDate = LocalDate.of(year, 10, 1), endDate = null, status = status,
        notes = null, snapshots = emptyList(), version = 1,
    )

    private companion object {
        val WORKSPACE: UUID = UUID.randomUUID()
        val FARM: UUID = UUID.randomUUID()
    }
}
