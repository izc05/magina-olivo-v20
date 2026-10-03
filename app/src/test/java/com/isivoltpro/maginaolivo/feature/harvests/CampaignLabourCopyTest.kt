package com.isivoltpro.maginaolivo.feature.harvests

import com.isivoltpro.maginaolivo.domain.expense.Expense
import com.isivoltpro.maginaolivo.domain.expense.ExpenseCategory
import com.isivoltpro.maginaolivo.domain.expense.ExpenseOrigin
import com.isivoltpro.maginaolivo.domain.expense.ExpenseStatus
import com.isivoltpro.maginaolivo.domain.expense.RecollectionLedger
import com.isivoltpro.maginaolivo.domain.labour.LabourEntry
import com.isivoltpro.maginaolivo.domain.labour.LabourUnit
import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** #365: the campaign's Jornales line, read from the labour lines and the posted ledger only. */
class CampaignLabourCopyTest {
    private val campaign = UUID.randomUUID()
    private val day = UUID.randomUUID()
    private val ana = UUID.randomUUID()
    private val luis = UUID.randomUUID()

    private fun named(worker: UUID, name: String, unit: LabourUnit = LabourUnit.FULL_DAY, harvest: UUID = day) =
        LabourEntry(UUID.randomUUID(), harvest, worker, name, 1, unit, null, 1)

    private fun labourCost(minor: Long, status: ExpenseStatus = ExpenseStatus.POSTED) = Expense(
        UUID.randomUUID(), UUID.randomUUID(), LocalDate.of(2026, 11, 27), "Jornales", ExpenseCategory.LABOR, minor, "EUR",
        status, ExpenseOrigin.DAY_LABOUR, campaignId = campaign, harvestId = day,
    )

    @Test fun nothingRecordedIsSaidInWords() {
        assertEquals(CAMPAIGN_NO_LABOUR, campaignLabourLine(emptyList(), emptyList()))
    }

    @Test fun onePersonOneDayAndItsConfirmedCost() {
        val line = campaignLabourLine(listOf(named(ana, "Ana")), RecollectionLedger.of(campaign, listOf(labourCost(6_500)), emptyList()))
        assertTrue(line, line.startsWith("1 persona · 1 jornada · "))
        assertTrue(line, line.contains("65,00"))
    }

    @Test fun peopleAreCountedOncePerPersonAcrossDays() {
        val other = UUID.randomUUID()
        val line = campaignLabourLine(listOf(named(ana, "Ana"), named(ana, "Ana", harvest = other), named(luis, "Luis", LabourUnit.HALF_DAY)), emptyList())
        assertEquals("2 personas · 2 jornadas · 1 media", line)
    }

    @Test fun aDraftCostIsNotShownAsMoney() {
        val line = campaignLabourLine(listOf(named(ana, "Ana")), RecollectionLedger.of(campaign, listOf(labourCost(6_500, ExpenseStatus.DRAFT)), emptyList()))
        assertEquals("1 persona · 1 jornada", line)
        assertFalse(line.contains("€"))
    }

    @Test fun historicalCountsStayApartFromKnownPeople() {
        val count = LabourEntry(UUID.randomUUID(), day, null, null, 5, LabourUnit.FULL_DAY, null, 1)
        assertEquals("1 persona · 6 jornadas · 5 sin nombre (histórico)", campaignLabourLine(listOf(named(ana, "Ana"), count), emptyList()))
    }
}
