package com.isivoltpro.maginaolivo.domain.notebook

import com.isivoltpro.maginaolivo.data.local.model.ActivityStatus
import com.isivoltpro.maginaolivo.data.local.model.CampaignStatus
import com.isivoltpro.maginaolivo.domain.activity.Activity
import com.isivoltpro.maginaolivo.domain.activity.ActivityDetail
import com.isivoltpro.maginaolivo.domain.activity.ActivityParcelTarget
import com.isivoltpro.maginaolivo.domain.activity.ActivityType
import com.isivoltpro.maginaolivo.domain.campaign.Campaign
import com.isivoltpro.maginaolivo.domain.delivery.Delivery
import com.isivoltpro.maginaolivo.domain.delivery.DeliverySource
import com.isivoltpro.maginaolivo.domain.expense.Expense
import com.isivoltpro.maginaolivo.domain.expense.ExpenseCategory
import com.isivoltpro.maginaolivo.domain.expense.ExpenseOrigin
import com.isivoltpro.maginaolivo.domain.expense.ExpenseStatus
import com.isivoltpro.maginaolivo.domain.harvest.Harvest
import com.isivoltpro.maginaolivo.domain.machinery.ActivityMachine
import com.isivoltpro.maginaolivo.domain.machinery.MachineCategory
import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** UX-E (Issue #246 §4) — Diario, Fitosanitario, Gastos and Campaña over the same records. */
class NotebookViewsTest {
    private val workspace = UUID.randomUUID()
    private val farm = UUID.randomUUID()
    private val campaign = Campaign(
        id = UUID.randomUUID(), workspaceId = workspace, farmId = farm, name = "2026/27",
        startDate = LocalDate.of(2026, 9, 1), endDate = null, status = CampaignStatus.HARVEST,
        notes = null, snapshots = emptyList(), version = 1,
    )
    private val day1 = LocalDate.of(2026, 11, 20)
    private val day2 = LocalDate.of(2026, 11, 21)

    @Test fun diaryIsOneTimelineOfEveryRecordNewestDayFirst() {
        val pruning = activity(ActivityType.PRUNING, day1)
        val jornada = harvest(2_000_000, day2)
        val pesada = delivery(1_500_000, day2)
        val fuel = expense(4_500, ExpenseCategory.FUEL, day1)
        val notebook = project(listOf(pruning), listOf(jornada), listOf(pesada), listOf(fuel))

        assertEquals(listOf(day2, day1), notebook.diary.map { it.date })
        val newest = notebook.diary.first().entries
        assertTrue(newest[0] is DiaryEntry.HarvestEntry)
        assertTrue(newest[1] is DiaryEntry.DeliveryEntry)
        val oldest = notebook.diary.last().entries
        assertEquals(pruning.id, (oldest[0] as DiaryEntry.Work).activity.id)
        assertEquals(fuel.id, (oldest[1] as DiaryEntry.ExpenseEntry).expense.id)
    }

    @Test fun anActivitysOwnCostIsNotListedTwiceInTheDiaryButStaysInTheLedger() {
        val irrigation = activity(ActivityType.IRRIGATION, day1)
        val cost = expense(3_000, ExpenseCategory.IRRIGATION, day1)
            .copy(activityId = irrigation.id, origin = ExpenseOrigin.ACTIVITY_COST)
        val notebook = project(listOf(irrigation), expenses = listOf(cost))

        assertEquals(1, notebook.diary.single().entries.size)
        assertEquals(3_000, notebook.costs.ledger.totalMinor)
    }

    @Test fun phytoRecordShowsOnlyWhatWasWrittenAndNamesWhatIsMissing() {
        val complete = activity(ActivityType.PHYTOSANITARY, day1).copy(
            targets = listOf(ActivityParcelTarget(UUID.randomUUID(), "La Loma", areaAffectedM2 = 12_500.0)),
            detail = ActivityDetail.Phytosanitary(
                productName = "Cobre 50", activeSubstance = "Oxicloruro de cobre", totalQuantity = 40.0, unit = "l",
                doseValue = 2.5, doseUnit = "l/ha", reason = "Repilo", equipmentText = "Atomizador",
            ),
            machines = listOf(machine("Tractor John Deere", 2.0)),
        )
        val bare = activity(ActivityType.PHYTOSANITARY, day2)
        val notebook = project(listOf(complete, bare, activity(ActivityType.PRUNING, day1)))

        val records = notebook.phytoRecords
        assertEquals(listOf(bare.id, complete.id), records.map { it.activity.id })
        val full = records.last()
        assertEquals("Cobre 50", full.productName)
        assertEquals("2,5 l/ha", full.dose)
        assertEquals("40 l", full.quantity)
        assertEquals(12_500.0, full.surfaceM2!!, 0.0)
        assertEquals(listOf("Tractor John Deere", "Atomizador"), full.machinery)
        assertTrue(full.gaps.isEmpty())
        // Nothing invented for the bare record: every key field is reported as missing.
        val empty = records.first()
        assertNull(empty.productName)
        assertNull(empty.dose)
        assertEquals(
            listOf(PhytoGap.PRODUCT, PhytoGap.ACTIVE_SUBSTANCE, PhytoGap.DOSE, PhytoGap.PARCEL, PhytoGap.REASON),
            empty.gaps,
        )
    }

    @Test fun surfaceIsUnknownWhenAnyTreatedParcelHasNoArea() {
        val record = PhytoRecord.of(
            activity(ActivityType.PHYTOSANITARY, day1).copy(
                targets = listOf(
                    ActivityParcelTarget(UUID.randomUUID(), "A", areaAffectedM2 = 5_000.0),
                    ActivityParcelTarget(UUID.randomUUID(), "B", areaAffectedM2 = null),
                ),
            ),
        )!!
        assertNull(record.surfaceM2)
        assertTrue(PhytoGap.SURFACE in record.gaps)
    }

    @Test fun costsGroupTheOneLedgerWithoutASecondAmount() {
        val worked = activity(ActivityType.SOIL_WORK, day1).copy(machines = listOf(machine("Tractor", 3.5)))
        val expenses = listOf(
            expense(20_000, ExpenseCategory.LABOR, day1),
            expense(4_500, ExpenseCategory.FUEL, day1),
            expense(1_000, ExpenseCategory.REPAIR, day1).copy(invoiceNumber = "F-12"),
            expense(9_999, ExpenseCategory.MACHINERY, day2, ExpenseStatus.DRAFT),
            expense(2_000, ExpenseCategory.PRODUCTS, day2).copy(origin = ExpenseOrigin.DOCUMENT_OCR),
        )
        val costs = project(listOf(worked), expenses = expenses).costs

        assertEquals(27_500, costs.ledger.totalMinor)
        assertEquals(20_000, costs.labourMoney.totalMinor)
        assertEquals(5_500, costs.machineryMoney.totalMinor) // the draft is never summed
        assertEquals(1, costs.machineUses)
        assertEquals(3.5, costs.machineHours, 0.0)
        assertEquals(2, costs.documents.size)
    }

    @Test fun pendingDeliveryIsKnownOnlyWhenItAddsUp() {
        val picked = listOf(harvest(3_000_000, day1))
        assertEquals(1_000_000L, project(harvests = picked, deliveries = listOf(delivery(2_000_000, day1))).pendingDeliveryGrams)
        // More delivered than picked: part of the harvest is unrecorded, so no difference is shown.
        assertNull(project(harvests = picked, deliveries = listOf(delivery(3_500_000, day1))).pendingDeliveryGrams)
        assertNull(project(deliveries = listOf(delivery(1_000_000, day1))).pendingDeliveryGrams)
    }

    private fun project(
        activities: List<Activity> = emptyList(),
        harvests: List<Harvest> = emptyList(),
        deliveries: List<Delivery> = emptyList(),
        expenses: List<Expense> = emptyList(),
    ) = CampaignNotebook.project(campaign, activities, harvests, deliveries, expenses)

    private fun activity(type: ActivityType, date: LocalDate) = Activity(
        id = UUID.randomUUID(), workspaceId = workspace, farmId = farm, campaignId = campaign.id, type = type,
        status = ActivityStatus.COMPLETED, activityDate = date, description = type.name, notes = null,
        targets = emptyList(), version = 1,
    )

    private fun machine(name: String, hours: Double) = ActivityMachine(
        machineId = UUID.randomUUID(), name = name, category = MachineCategory.TRACTOR,
        startHours = null, endHours = null, usageHours = hours,
    )

    private fun harvest(grams: Long, date: LocalDate) = Harvest(
        id = UUID.randomUUID(), workspaceId = workspace, farmId = farm, campaignId = campaign.id, harvestDate = date,
        totalGrams = grams, shares = emptyList(), collectionMethod = null, workerCount = null, machineryText = null,
        notes = null, version = 1,
    )

    private fun delivery(grams: Long, date: LocalDate) = Delivery(
        id = UUID.randomUUID(), workspaceId = workspace, farmId = farm, campaignId = campaign.id, deliveryDate = date,
        destinationOrganizationId = null, destinationName = "Coop. San Isidro", netGrams = grams, grossGrams = null,
        tareGrams = null, deliveryNumber = null, ticketNumber = null, source = DeliverySource.MANUAL,
        shares = emptyList(), notes = null, version = 1,
    )

    private fun expense(minor: Long, category: ExpenseCategory, date: LocalDate, status: ExpenseStatus = ExpenseStatus.POSTED) = Expense(
        id = UUID.randomUUID(), workspaceId = workspace, expenseDate = date, concept = category.name, category = category,
        amountMinor = minor, currency = "EUR", status = status, origin = ExpenseOrigin.MANUAL, farmId = farm,
        campaignId = campaign.id,
    )
}
