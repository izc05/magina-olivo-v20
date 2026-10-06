package com.isivoltpro.maginaolivo.domain.notebook

import com.isivoltpro.maginaolivo.domain.labour.LabourUnit
import com.isivoltpro.maginaolivo.domain.labour.LabourEntry
import com.isivoltpro.maginaolivo.domain.analytics.CampaignDashboard
import com.isivoltpro.maginaolivo.domain.analytics.CampaignComparison
import com.isivoltpro.maginaolivo.data.local.model.ActivityStatus
import com.isivoltpro.maginaolivo.data.local.model.CampaignStatus
import com.isivoltpro.maginaolivo.domain.activity.Activity
import com.isivoltpro.maginaolivo.domain.activity.ActivityDetail
import com.isivoltpro.maginaolivo.domain.activity.ActivityParcelTarget
import com.isivoltpro.maginaolivo.domain.activity.ActivityType
import com.isivoltpro.maginaolivo.domain.campaign.Campaign
import com.isivoltpro.maginaolivo.domain.delivery.Delivery
import com.isivoltpro.maginaolivo.domain.delivery.DeliverySource
import com.isivoltpro.maginaolivo.domain.delivery.YieldAnalysis
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

    /** #478: a work's cost, even a legacy ACTIVITY_COST, is never hidden in the Diario; it is shown, not summed. */
    @Test fun aWorksCostIsItsOwnDiaryRowAndCountsOnceInTheLedger() {
        val irrigation = activity(ActivityType.IRRIGATION, day1)
        val cost = expense(3_000, ExpenseCategory.IRRIGATION, day1)
            .copy(activityId = irrigation.id, origin = ExpenseOrigin.ACTIVITY_COST)
        val notebook = project(listOf(irrigation), expenses = listOf(cost))

        val entries = notebook.diary.single().entries
        assertEquals(2, entries.size)
        assertEquals(irrigation.description, (entries[1] as DiaryEntry.ExpenseEntry).relatedWork)
        assertEquals(3_000L, notebook.costs.ledger.single().amount())
    }

    /** #478 QA 1/3: a manual Gasto of a Tratamiento is shown on its own date, naming the work. */
    @Test fun aManualGastoOfAWorkIsShownOnItsOwnDate() {
        val treatment = activity(ActivityType.PHYTOSANITARY, day1)
        val invoice = expense(8_500, ExpenseCategory.PRODUCTS, day2).copy(activityId = treatment.id)
        val notebook = project(listOf(treatment), expenses = listOf(invoice))

        assertEquals(listOf(day2, day1), notebook.diary.map { it.date })
        val row = notebook.diary.first().entries.single() as DiaryEntry.ExpenseEntry
        assertEquals(invoice.id, row.expense.id)
        assertEquals(treatment.description, row.relatedWork)
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

        assertEquals(27_500L, costs.ledger.single().amount())
        assertEquals(20_000L, costs.labourMoney.single().amount())
        assertEquals(5_500L, costs.machineryMoney.single().amount()) // the draft is never summed
        assertEquals(1, costs.machineUses)
        assertEquals(3.5, costs.machineHours, 0.0)
        assertEquals(2, costs.documents.size)
    }

    @Test fun legacyHandTypedKilosAreShownApartNeverDroppedNorAddedToThePesadasTotal() {
        // CR-010 A2. A legacy Jornada typed by hand with no Pesada, one reconciled with its Pesada,
        // and one opened today still awaiting its first Pesada.
        val legacy = harvest(4_000_000, day1)
        val weighed = harvest(2_000_000, day1)
        val awaiting = harvest(0, day1)
        val pesada = delivery(2_000_000, day1).copy(harvestId = weighed.id)
        val notebook = project(harvests = listOf(legacy, weighed, awaiting), deliveries = listOf(pesada))

        assertEquals(4_000_000L, notebook.legacyUnweighedGrams)
        // The principal total is the Pesadas' only.
        assertEquals(2_000_000L, notebook.deliverySummary.deliveredGrams)
        // Nothing legacy: nothing to show apart.
        assertEquals(0L, project(harvests = listOf(weighed), deliveries = listOf(pesada)).legacyUnweighedGrams)
        // A Jornada awaiting its first Pesada has no kilos: never legacy history.
        assertEquals(0L, legacyUnweighedGrams(listOf(awaiting), emptyList()))
        // The year-over-year comparison carries the same figure apart from the weighed kilos.
        val row = CampaignComparison.of(listOf(notebook)).single()
        assertEquals(4_000_000L, row.legacyUnweighedGrams)
        assertEquals(2_000_000L, row.deliveredGrams)
    }

    @Test fun pendingDeliveryIsKnownOnlyWhenItAddsUp() {
        val picked = listOf(harvest(3_000_000, day1))
        assertEquals(1_000_000L, project(harvests = picked, deliveries = listOf(delivery(2_000_000, day1))).pendingDeliveryGrams)
        // More delivered than picked: part of the harvest is unrecorded, so no difference is shown.
        assertNull(project(harvests = picked, deliveries = listOf(delivery(3_500_000, day1))).pendingDeliveryGrams)
        assertNull(project(deliveries = listOf(delivery(1_000_000, day1))).pendingDeliveryGrams)
    }

    @Test fun aJornadasOwnPesadaAndCostAreReadInsideItNotRepeated() {
        // 254-E: the owner saw "Jornada · 2.390 kg" and "Pesada nº 1 · 2.390 kg" as two rows.
        val jornada = harvest(2_390_000, day2)
        val own = delivery(2_390_000, day2).copy(harvestId = jornada.id)
        val loose = delivery(1_000_000, day2)
        val diesel = expense(5_000, ExpenseCategory.FUEL, day2).copy(harvestId = jornada.id)
        val notebook = project(harvests = listOf(jornada), deliveries = listOf(own, loose), expenses = listOf(diesel))

        val diary = notebook.diary.single().entries
        assertEquals(2, diary.size) // the Jornada and the loose Pesada; not its own Pesada nor its diesel
        assertTrue(diary.none { it is DiaryEntry.DeliveryEntry && it.delivery.id == own.id })
        assertTrue(diary.none { it is DiaryEntry.ExpenseEntry })
        val recollection = notebook.recollectionDays.single().items
        assertTrue(recollection.none { it is RecollectionItem.DeliveryItem && it.delivery.id == own.id })
        // Totals still count everything exactly once.
        assertEquals(3_390_000L, notebook.deliverySummary.deliveredGrams)
        assertEquals(5_000L, notebook.jornadaCost(jornada.id).single().amount())
        assertEquals(5_000L, notebook.expensesByCurrency.single().amount())
        assertEquals("rend. pendiente", notebook.jornadaYieldLabel(jornada.id))
    }

    @Test fun aJornadaYieldIsPartialWhenItCoversOnlySomeOfItsKilos() {
        val jornada = harvest(3_000_000, day2)
        val fat = delivery(2_000_000, day2).let {
            it.copy(harvestId = jornada.id, analysis = YieldAnalysis(UUID.randomUUID(), it.id, day2, 2_100, null, null, 1))
        }
        // Only an industrial yield: "with yield" for the search, but no fat figure for its kilos.
        val industrialOnly = delivery(1_000_000, day2).let {
            it.copy(harvestId = jornada.id, analysis = YieldAnalysis(UUID.randomUUID(), it.id, day2, null, 1_800, null, 1))
        }
        assertEquals("rend. 21 % (parcial)", project(harvests = listOf(jornada), deliveries = listOf(fat, industrialOnly)).jornadaYieldLabel(jornada.id))
        assertEquals("rend. 21 %", project(harvests = listOf(jornada), deliveries = listOf(fat)).jornadaYieldLabel(jornada.id))
    }

    @Test fun theCampaignAtAGlanceCountsDaysAndReadsMoneyOnlyFromTheLedger() {
        // CR-010 §12: days, Pesada/jornal days, first/last Pesada, cost and cost per kilo.
        val jornada = harvest(3_000_000, day1)
        val pesadas = listOf(delivery(2_000_000, day1), delivery(1_000_000, day1), delivery(2_000_000, day2))
        val labour = listOf(LabourEntry(UUID.randomUUID(), jornada.id, null, null, 5, LabourUnit.FULL_DAY, null, 1))
        val calculated = expense(35_000, ExpenseCategory.LABOR, day1).copy(origin = ExpenseOrigin.DAY_LABOUR, harvestId = jornada.id)
        val diesel = expense(5_000, ExpenseCategory.FUEL, day1)
        val draft = expense(99_000, ExpenseCategory.LABOR, day1, ExpenseStatus.DRAFT)
        val notebook = CampaignNotebook.project(campaign, emptyList(), listOf(jornada), pesadas, listOf(calculated, diesel, draft), labour)

        val dashboard = CampaignDashboard.of(notebook, today = LocalDate.of(2026, 11, 21))
        // 1 Sept to 21 Nov, both counted.
        assertEquals(82L, dashboard.calendarDays)
        // The screen names the date it counts from; it never claims an activation date it does not store.
        assertEquals(campaign.startDate, dashboard.countedFrom)
        assertEquals(2, dashboard.pesadaDays)
        assertEquals(1, dashboard.labourDays)
        assertEquals(day1, dashboard.firstPesada)
        assertEquals(day2, dashboard.lastPesada)
        // Posted money once (the calculated jornales are inside it); the draft never counts.
        assertEquals(40_000L, dashboard.costs.single().postedMinor)
        assertEquals("EUR", dashboard.costs.single().currency)
        assertEquals(35_000L, dashboard.costs.single().calculatedLabourMinor)
        // 400 € / 5.000 kg = 0,080 €/kg (#486: thousandths per kilo).
        assertEquals(80L, dashboard.costPerKgMilli)

        // Nothing weighed: no cost per kilo, never a division by zero or a made-up 0.
        val empty = CampaignDashboard.of(CampaignNotebook.project(campaign, emptyList(), emptyList(), emptyList(), listOf(diesel)), LocalDate.of(2026, 11, 21))
        assertEquals(null, empty.costPerKgMilli)
        assertEquals(null, empty.firstPesada)
        // #450: EUR + GBP — both ledgers visible, nothing converted, no global cost per kilo.
        val gbp = expense(30_000, ExpenseCategory.MACHINERY, day1).copy(currency = "GBP", origin = ExpenseOrigin.DAY_EQUIPMENT, harvestId = jornada.id)
        val mixed = CampaignDashboard.of(
            CampaignNotebook.project(campaign, emptyList(), listOf(jornada), pesadas, listOf(calculated, diesel, gbp), labour),
            LocalDate.of(2026, 11, 21),
        )
        assertEquals(listOf("EUR", "GBP"), mixed.costs.map { it.currency })
        assertEquals(40_000L, mixed.costs.first { it.currency == "EUR" }.postedMinor)
        assertEquals(30_000L, mixed.costs.first { it.currency == "GBP" }.postedMinor)
        assertEquals(30_000L, mixed.costs.first { it.currency == "GBP" }.calculatedMachineryMinor)
        assertEquals(null, mixed.costPerKgMilli)
        // Only GBP: GBP is shown, never an empty EUR total.
        val onlyGbp = CampaignDashboard.of(CampaignNotebook.project(campaign, emptyList(), listOf(jornada), pesadas, listOf(gbp), labour), LocalDate.of(2026, 11, 21))
        assertEquals(listOf("GBP"), onlyGbp.costs.map { it.currency })
        // A closed Campaign counts until its close date.
        val closed = campaign.copy(status = CampaignStatus.CLOSED, endDate = LocalDate.of(2026, 9, 10))
        assertEquals(10L, CampaignDashboard.of(CampaignNotebook.project(closed, emptyList(), emptyList(), emptyList(), emptyList()), LocalDate.of(2026, 11, 21)).calendarDays)
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
