package com.isivoltpro.maginaolivo.domain.notebook

import com.isivoltpro.maginaolivo.data.local.model.ActivityStatus
import com.isivoltpro.maginaolivo.data.local.model.CampaignStatus
import com.isivoltpro.maginaolivo.domain.activity.Activity
import com.isivoltpro.maginaolivo.domain.activity.ActivityType
import com.isivoltpro.maginaolivo.domain.campaign.Campaign
import com.isivoltpro.maginaolivo.domain.delivery.Delivery
import com.isivoltpro.maginaolivo.domain.delivery.DeliverySource
import com.isivoltpro.maginaolivo.domain.delivery.DeliverySummary
import com.isivoltpro.maginaolivo.domain.delivery.YieldAnalysis
import com.isivoltpro.maginaolivo.domain.equipment.EquipmentLine
import com.isivoltpro.maginaolivo.domain.equipment.EquipmentSummary
import com.isivoltpro.maginaolivo.domain.equipment.EquipmentType
import com.isivoltpro.maginaolivo.domain.expense.Expense
import com.isivoltpro.maginaolivo.domain.expense.ExpenseCategory
import com.isivoltpro.maginaolivo.domain.expense.ExpenseOrigin
import com.isivoltpro.maginaolivo.domain.expense.ExpenseStatus
import com.isivoltpro.maginaolivo.domain.harvest.Harvest
import com.isivoltpro.maginaolivo.domain.harvest.HarvestSummary
import com.isivoltpro.maginaolivo.domain.machinery.ActivityMachine
import com.isivoltpro.maginaolivo.domain.machinery.MachineCategory
import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CampaignNotebookTest {
    @Test fun recollectionBucketsAreExclusivePerCurrencyAndHistoricalLabourKeepsItsCost() {
        val rows = listOf(
            expense(30000, ExpenseCategory.HARVEST, ExpenseStatus.POSTED, LocalDate.of(2026, 10, 2)).copy(concept = "Jornales/servicio"),
            expense(18000, ExpenseCategory.MACHINERY, ExpenseStatus.POSTED, LocalDate.of(2026, 10, 2)),
            expense(2000, ExpenseCategory.FUEL, ExpenseStatus.POSTED, LocalDate.of(2026, 10, 2)),
            expense(3000, ExpenseCategory.REPAIR, ExpenseStatus.POSTED, LocalDate.of(2026, 10, 2)),
            expense(99999, ExpenseCategory.MACHINERY, ExpenseStatus.DRAFT, LocalDate.of(2026, 10, 2)).copy(origin = ExpenseOrigin.DAY_EQUIPMENT),
        )
        val ledger = com.isivoltpro.maginaolivo.domain.expense.RecollectionLedger.of(campaign.id, rows, emptyList()).single()
        assertEquals(30000L, ledger.amount(com.isivoltpro.maginaolivo.domain.expense.RecollectionBucket.LABOUR))
        assertEquals(18000L, ledger.amount(com.isivoltpro.maginaolivo.domain.expense.RecollectionBucket.EQUIPMENT))
        assertEquals(5000L, ledger.amount(com.isivoltpro.maginaolivo.domain.expense.RecollectionBucket.OTHER))
        assertEquals(53000L, ledger.amount())
    }
    @Test fun farmAndDateNeverAssignEconomicCampaignContext() {
        val explicit = expense(1000, ExpenseCategory.OTHER, ExpenseStatus.POSTED, LocalDate.of(2026, 10, 2))
        val outside = explicit.copy(id = UUID.randomUUID(), campaignId = null)
        val notebook = CampaignNotebook.project(campaign, emptyList(), emptyList(), emptyList(), listOf(explicit, outside))
        assertEquals(listOf(explicit), notebook.expenses)
        assertEquals(1000L, notebook.recollectionByCurrency.single().amount())
    }
    private val workspace = UUID.randomUUID()
    private val farm = UUID.randomUUID()
    private val otherFarm = UUID.randomUUID()
    private val campaign = Campaign(
        id = UUID.randomUUID(), workspaceId = workspace, farmId = farm, name = "2026/27",
        startDate = LocalDate.of(2026, 9, 1), endDate = null, status = CampaignStatus.HARVEST,
        notes = null, snapshots = emptyList(), version = 1,
    )
    private val otherCampaign = UUID.randomUUID()

    /** #417: a Campaign holds what is linked to it; general work inside its dates stays the Farm's. */
    @Test fun onlyRecordsExplicitlyLinkedToThisCampaign() {
        val notebook = CampaignNotebook.project(
            campaign,
            activities = listOf(
                activity(ActivityType.PRUNING, LocalDate.of(2026, 10, 2), campaign.id),
                activity(ActivityType.PHYTOSANITARY, LocalDate.of(2026, 9, 20), null), // general work inside its dates: not absorbed
                activity(ActivityType.IRRIGATION, LocalDate.of(2026, 8, 1), null), // before the campaign
                activity(ActivityType.PRUNING, LocalDate.of(2026, 10, 3), otherCampaign), // another campaign
                activity(ActivityType.PRUNING, LocalDate.of(2026, 10, 3), null, farmId = otherFarm), // another farm
                activity(ActivityType.HARVEST_DAY, LocalDate.of(2026, 11, 24), campaign.id),
            ),
            harvests = emptyList(), deliveries = emptyList(), expenses = emptyList(),
        )
        assertEquals(listOf(ActivityType.PRUNING), notebook.works.map { it.type })
        assertEquals(1, notebook.harvestDays.size)
        assertEquals(1, notebook.recollectionDays.size)
    }

    /** #417: the Farm's Cuaderno holds all its records, with or without a Campaign; none from another Farm. */
    @Test fun theFarmNotebookListsTheFarmsRecordsWhateverTheCampaign() {
        val general = expense(1000, ExpenseCategory.OTHER, ExpenseStatus.POSTED, LocalDate.of(2026, 10, 2)).copy(campaignId = null)
        val linked = expense(2000, ExpenseCategory.HARVEST, ExpenseStatus.POSTED, LocalDate.of(2026, 10, 2))
        val elsewhere = expense(500, ExpenseCategory.OTHER, ExpenseStatus.POSTED, LocalDate.of(2026, 10, 2)).copy(farmId = otherFarm, campaignId = null)
        val notebook = FarmNotebook.of(
            farm,
            activities = listOf(
                activity(ActivityType.PRUNING, LocalDate.of(2026, 10, 2), campaign.id),
                activity(ActivityType.PHYTOSANITARY, LocalDate.of(2026, 9, 20), null),
                activity(ActivityType.IRRIGATION, LocalDate.of(2026, 8, 1), null),
                activity(ActivityType.PRUNING, LocalDate.of(2026, 10, 3), null, farmId = otherFarm),
            ),
            harvests = emptyList(), deliveries = emptyList(), expenses = listOf(general, linked, elsewhere),
        )
        assertEquals(3, notebook.activities.size)
        assertEquals(listOf(ActivityType.PHYTOSANITARY), notebook.phytoRecords.map { it.activity.type })
        assertEquals(listOf(general), notebook.generalExpenses)
        assertEquals(listOf(linked), notebook.recollectionExpenses)
        // Diario: newest day first, the work and both own expenses, nothing from the other Farm.
        assertEquals(listOf(LocalDate.of(2026, 10, 2), LocalDate.of(2026, 9, 20), LocalDate.of(2026, 8, 1)), notebook.diary.map { it.date })
        assertEquals(3, notebook.diary.first().entries.size)
    }

    @Test fun aFarmWithoutCampaignsStillHasItsNotebook() {
        val notebook = FarmNotebook.of(farm, listOf(activity(ActivityType.FERTILIZATION, LocalDate.of(2026, 3, 1), null)), emptyList(), emptyList(), emptyList())
        assertEquals(1, notebook.diary.size)
        assertTrue(notebook.recollectionExpenses.isEmpty())
    }

    /** #417: legacy recogida saved without a Campaign is still read by date; general work never. */
    @Test fun legacyRecogidaWithoutLinkIsStillReadByDate() {
        val legacyDay = activity(ActivityType.HARVEST_DAY, LocalDate.of(2026, 11, 2), null)
        val legacyJornada = harvest(1_000_000, LocalDate.of(2026, 11, 2)).copy(campaignId = null)
        val notebook = CampaignNotebook.project(
            campaign,
            activities = listOf(legacyDay, activity(ActivityType.IRRIGATION, LocalDate.of(2026, 11, 2), null)),
            harvests = listOf(legacyJornada), deliveries = emptyList(), expenses = emptyList(),
        )
        assertEquals(listOf(legacyDay), notebook.harvestDays)
        assertTrue(notebook.works.isEmpty())
        assertEquals(listOf(legacyJornada), notebook.harvests)
    }

    @Test fun legacyResolverRequiresExactlyOneCandidate() {
        val date = LocalDate.of(2026, 11, 2)
        val bounded = campaign.copy(endDate = LocalDate.of(2026, 12, 31))
        val second = campaign.copy(
            id = UUID.randomUUID(), name = "Solapada",
            startDate = LocalDate.of(2026, 10, 15), endDate = LocalDate.of(2026, 12, 15),
        )
        val third = campaign.copy(
            id = UUID.randomUUID(), name = "Solapada 3",
            startDate = LocalDate.of(2026, 11, 1), endDate = LocalDate.of(2026, 11, 30),
        )

        assertEquals(
            LegacyCampaignResolution.Unassigned,
            LegacyCampaignResolver.resolve(listOf(bounded), otherFarm, date),
        )
        assertEquals(
            LegacyCampaignResolution.Resolved(bounded.id),
            LegacyCampaignResolver.resolve(listOf(bounded), farm, date),
        )
        val two = LegacyCampaignResolver.resolve(listOf(bounded, second), farm, date)
        assertTrue(two is LegacyCampaignResolution.Ambiguous)
        assertEquals(setOf(bounded.id, second.id), (two as LegacyCampaignResolution.Ambiguous).candidateIds.toSet())
        val three = LegacyCampaignResolver.resolve(listOf(bounded, second, third), farm, date)
        assertTrue(three is LegacyCampaignResolution.Ambiguous)
        assertEquals(3, (three as LegacyCampaignResolution.Ambiguous).candidateIds.size)
    }

    @Test fun overlappingCampaignsNeverDuplicateLegacyRecollection() {
        val date = LocalDate.of(2026, 11, 2)
        val first = campaign.copy(endDate = LocalDate.of(2026, 12, 31))
        val second = campaign.copy(
            id = UUID.randomUUID(), name = "2026/27 B",
            startDate = LocalDate.of(2026, 10, 15), endDate = LocalDate.of(2026, 12, 15),
        )
        val candidates = listOf(first, second)
        val legacyDay = activity(ActivityType.HARVEST_DAY, date, null)
        val legacyJornada = harvest(1_000_000, date).copy(campaignId = null)

        fun notebook(c: Campaign) = CampaignNotebook.project(
            c,
            activities = listOf(legacyDay),
            harvests = listOf(legacyJornada),
            deliveries = emptyList(),
            expenses = emptyList(),
            candidateCampaigns = candidates,
        )

        val a = notebook(first)
        val b = notebook(second)
        assertTrue(a.harvestDays.isEmpty() && b.harvestDays.isEmpty())
        assertTrue(a.harvests.isEmpty() && b.harvests.isEmpty())
        // Historical comparison must not count the same hand-entered kilos in both Campaigns.
        val comparison = com.isivoltpro.maginaolivo.domain.analytics.CampaignComparison.of(listOf(a, b))
        assertTrue(comparison.all { it.legacyUnweighedGrams == 0L })

        // Once a legacy row is explicitly assigned, date overlap no longer matters.
        val assigned = legacyJornada.copy(campaignId = second.id)
        val assignedA = CampaignNotebook.project(
            first, emptyList(), listOf(assigned), emptyList(), emptyList(), candidateCampaigns = candidates,
        )
        val assignedB = CampaignNotebook.project(
            second, emptyList(), listOf(assigned), emptyList(), emptyList(), candidateCampaigns = candidates,
        )
        assertTrue(assignedA.harvests.isEmpty())
        assertEquals(listOf(assigned), assignedB.harvests)
    }

    /** #478: the Farm Diario shows a Gasto tied to a work as its own row, with or without a Campaign. */
    @Test fun aGastoOfAWorkIsNeverHiddenFromTheFarmDiario() {
        val treatment = activity(ActivityType.PHYTOSANITARY, LocalDate.of(2026, 3, 10), null)
        val manual = expense(8_500, ExpenseCategory.PRODUCTS, ExpenseStatus.POSTED, LocalDate.of(2026, 3, 12))
            .copy(activityId = treatment.id)
        val ocr = expense(4_000, ExpenseCategory.PRODUCTS, ExpenseStatus.POSTED, LocalDate.of(2026, 3, 10))
            .copy(activityId = treatment.id, origin = ExpenseOrigin.DOCUMENT_OCR)
        val notebook = FarmNotebook.of(farm, listOf(treatment), emptyList(), emptyList(), listOf(manual, ocr))
        val gastos = notebook.diary.flatMap { it.entries }.filterIsInstance<DiaryEntry.ExpenseEntry>()
        assertEquals(setOf(manual.id, ocr.id), gastos.map { it.expense.id }.toSet())
        assertTrue(gastos.all { it.relatedWork == treatment.description })
        assertEquals(LocalDate.of(2026, 3, 12), notebook.diary.first().date)
    }

    @Test fun onlyDoneWorkIsAFactInTheFarmNotebook() {
        val tractor = ActivityMachine(UUID.randomUUID(), "Tractor", MachineCategory.TRACTOR, null, null, 2.0)
        val plannedPruning = activity(ActivityType.PRUNING, LocalDate.of(2026, 10, 5), null).copy(status = ActivityStatus.PLANNED)
        val donePruning = activity(ActivityType.PRUNING, LocalDate.of(2026, 10, 1), null)
        val plannedTreatment = activity(ActivityType.PHYTOSANITARY, LocalDate.of(2026, 10, 6), null)
            .copy(status = ActivityStatus.PLANNED, machines = listOf(tractor))
        val doneTreatment = activity(ActivityType.PHYTOSANITARY, LocalDate.of(2026, 9, 20), null).copy(machines = listOf(tractor))
        val draft = activity(ActivityType.IRRIGATION, LocalDate.of(2026, 9, 1), null).copy(status = ActivityStatus.DRAFT)
        val cancelled = activity(ActivityType.IRRIGATION, LocalDate.of(2026, 9, 2), null).copy(status = ActivityStatus.CANCELLED)
        val notebook = FarmNotebook.of(
            farm, listOf(plannedPruning, donePruning, plannedTreatment, doneTreatment, draft, cancelled),
            emptyList(), emptyList(), emptyList(),
        )
        val diaryWork = notebook.diary.flatMap { it.entries }.filterIsInstance<DiaryEntry.Work>().map { it.activity.id }
        // Diario: only what was done; planned, draft and cancelled work stays out.
        assertEquals(listOf(donePruning.id, doneTreatment.id), diaryWork)
        // Fitosanitario: a planned treatment is never read as applied.
        assertEquals(listOf(doneTreatment.id), notebook.phytoRecords.map { it.activity.id })
        // Machinery: planned machines never count as use.
        assertEquals(listOf(doneTreatment.id), notebook.machineWork.map { it.id })
        // Once confirmed (#438) the same record enters the realised projections exactly once.
        val confirmed = FarmNotebook.of(
            farm, listOf(plannedPruning.copy(status = ActivityStatus.COMPLETED, version = 2), donePruning),
            emptyList(), emptyList(), emptyList(),
        )
        assertEquals(1, confirmed.diary.flatMap { it.entries }.filterIsInstance<DiaryEntry.Work>().count { it.activity.id == plannedPruning.id })
    }

    @Test fun jornadaEquipmentIsPartOfTheFarmsMachineryUse() {
        val jornada = harvest(1_000_000, LocalDate.of(2026, 11, 2))
        val shaker = EquipmentLine(UUID.randomUUID(), jornada.id, EquipmentType.SHAKER, null, 2, null, 1)
        val stray = EquipmentLine(UUID.randomUUID(), UUID.randomUUID(), EquipmentType.TRACTOR, null, 1, null, 1)
        val notebook = FarmNotebook.of(farm, emptyList(), listOf(jornada), emptyList(), emptyList(), equipment = listOf(shaker, stray))
        // No annual work with a machine, yet the Jornada's vibradoras are real use; a line of no listed Jornada is not.
        assertTrue(notebook.machineWork.isEmpty())
        assertEquals(EquipmentSummary.of(listOf(shaker)), notebook.equipmentSummary)
    }

    @Test fun totalsAreTheSameSummariesTheirOwnScreensShow() {
        val harvests = listOf(harvest(2_850_000, LocalDate.of(2026, 11, 20)), harvest(1_920_000, LocalDate.of(2026, 11, 21)))
        val deliveries = listOf(
            delivery(1_840_000, LocalDate.of(2026, 11, 20), "Coop. San Isidro", yieldHundredths = 2_280),
            delivery(2_000_000, LocalDate.of(2026, 11, 21), "Almazara La Loma", yieldHundredths = null),
        )
        val expenses = listOf(
            expense(12_000, ExpenseCategory.HARVEST, ExpenseStatus.POSTED, LocalDate.of(2026, 11, 20)),
            expense(4_500, ExpenseCategory.FUEL, ExpenseStatus.POSTED, LocalDate.of(2026, 10, 2)),
            expense(9_999, ExpenseCategory.TRANSPORT, ExpenseStatus.DRAFT, LocalDate.of(2026, 11, 21)),
        )
        val notebook = CampaignNotebook.project(campaign, emptyList(), harvests, deliveries, expenses)

        assertEquals(HarvestSummary.of(harvests), notebook.harvestSummary)
        assertEquals(DeliverySummary.of(deliveries), notebook.deliverySummary)
        assertEquals(listOf("EUR"), notebook.expensesByCurrency.map { it.currency })
        assertEquals(16_500L, notebook.expensesByCurrency.single().amount()) // the draft is listed, never summed
        assertEquals(16_500L, notebook.recollectionByCurrency.single().amount())
        // Yield only from analysed kilos, with its coverage; the pending delivery never counts as 0 %.
        assertEquals(2_280, notebook.deliverySummary.fatYield!!.hundredths)
        assertEquals(47, notebook.deliverySummary.coveragePercent(notebook.deliverySummary.fatYield))
        // Two recolección days, newest first, each row the canonical record.
        assertEquals(listOf(LocalDate.of(2026, 11, 21), LocalDate.of(2026, 11, 20), LocalDate.of(2026, 10, 2)), notebook.recollectionDays.map { it.date })
        assertTrue(notebook.recollectionDays.first().items.first() is RecollectionItem.HarvestItem)
    }

    private fun activity(type: ActivityType, date: LocalDate, campaignId: UUID?, farmId: UUID = farm) = Activity(
        id = UUID.randomUUID(), workspaceId = workspace, farmId = farmId, campaignId = campaignId, type = type,
        status = ActivityStatus.COMPLETED, activityDate = date, description = type.name, notes = null,
        targets = emptyList(), version = 1,
    )

    private fun harvest(grams: Long, date: LocalDate) = Harvest(
        id = UUID.randomUUID(), workspaceId = workspace, farmId = farm, campaignId = campaign.id, harvestDate = date,
        totalGrams = grams, shares = emptyList(), collectionMethod = null, workerCount = null, machineryText = null,
        notes = null, version = 1,
    )

    private fun delivery(grams: Long, date: LocalDate, destination: String, yieldHundredths: Int?): Delivery {
        val id = UUID.randomUUID()
        return Delivery(
            id = id, workspaceId = workspace, farmId = farm, campaignId = campaign.id, deliveryDate = date,
            destinationOrganizationId = null, destinationName = destination, netGrams = grams, grossGrams = null,
            tareGrams = null, deliveryNumber = null, ticketNumber = "45872", source = DeliverySource.MANUAL,
            shares = emptyList(), notes = null, version = 1,
            analysis = yieldHundredths?.let { YieldAnalysis(UUID.randomUUID(), id, date.plusDays(3), it, null, null, 1) },
        )
    }

    /** #450: the Cuaderno never shows one currency as the campaign's whole ledger. */
    @Test fun theNotebookKeepsEveryCurrencyApart() {
        val day = LocalDate.of(2026, 11, 20)
        val harvestId = UUID.randomUUID()
        val eur = expense(5_000, ExpenseCategory.LABOR, ExpenseStatus.POSTED, day).copy(harvestId = harvestId)
        val usd = expense(3_000, ExpenseCategory.FUEL, ExpenseStatus.POSTED, day).copy(currency = "USD", harvestId = harvestId)
        val notebook = CampaignNotebook.project(campaign, emptyList(), emptyList(), emptyList(), listOf(eur, usd))
        assertEquals(listOf("EUR" to 5_000L, "USD" to 3_000L), notebook.expensesByCurrency.map { it.currency to it.amount() })
        assertEquals(listOf("EUR" to 5_000L, "USD" to 3_000L), notebook.jornadaCost(harvestId).map { it.currency to it.amount() })
        assertEquals(listOf("EUR" to 5_000L, "USD" to 3_000L), notebook.recollectionByCurrency.map { it.currency to it.amount() })
        assertEquals(listOf("EUR" to 5_000L), notebook.costs.labourMoney.map { it.currency to it.amount() })
        assertEquals(listOf("USD" to 3_000L), notebook.costs.machineryMoney.map { it.currency to it.amount() })
    }

    private fun expense(minor: Long, category: ExpenseCategory, status: ExpenseStatus, date: LocalDate) = Expense(
        id = UUID.randomUUID(), workspaceId = workspace, expenseDate = date, concept = category.name, category = category,
        amountMinor = minor, currency = "EUR", status = status, origin = ExpenseOrigin.MANUAL, farmId = farm,
        campaignId = campaign.id,
    )
}
