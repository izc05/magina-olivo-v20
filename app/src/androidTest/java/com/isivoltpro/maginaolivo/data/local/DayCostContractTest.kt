package com.isivoltpro.maginaolivo.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.isivoltpro.maginaolivo.core.common.AppError
import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.core.dispatchers.AppDispatchers
import com.isivoltpro.maginaolivo.core.id.IdGenerator
import com.isivoltpro.maginaolivo.core.time.AppClock
import com.isivoltpro.maginaolivo.data.local.entity.CampaignEntity
import com.isivoltpro.maginaolivo.data.local.entity.CampaignParcelSnapshotEntity
import com.isivoltpro.maginaolivo.data.local.entity.FarmEntity
import com.isivoltpro.maginaolivo.data.local.entity.ExpenseEntity
import com.isivoltpro.maginaolivo.data.local.entity.LocalMetadata
import com.isivoltpro.maginaolivo.data.local.entity.ParcelEntity
import com.isivoltpro.maginaolivo.data.local.entity.WorkspaceEntity
import com.isivoltpro.maginaolivo.data.local.model.CampaignStatus
import com.isivoltpro.maginaolivo.data.repository.OfflineFirstDayCostRepository
import com.isivoltpro.maginaolivo.data.repository.OfflineFirstEquipmentRepository
import com.isivoltpro.maginaolivo.data.repository.OfflineFirstExpenseRepository
import com.isivoltpro.maginaolivo.data.repository.OfflineFirstHarvestRepository
import com.isivoltpro.maginaolivo.data.repository.OfflineFirstLabourRepository
import com.isivoltpro.maginaolivo.domain.equipment.EquipmentDraftLine
import com.isivoltpro.maginaolivo.domain.equipment.EquipmentPriceSnapshot
import com.isivoltpro.maginaolivo.domain.equipment.EquipmentType
import com.isivoltpro.maginaolivo.domain.harvest.HarvestDraft
import com.isivoltpro.maginaolivo.domain.harvest.HarvestShareInput
import com.isivoltpro.maginaolivo.domain.expense.DayCostRole
import com.isivoltpro.maginaolivo.domain.expense.DayCostKind
import com.isivoltpro.maginaolivo.domain.expense.Expense
import com.isivoltpro.maginaolivo.domain.expense.ExpenseDraft
import com.isivoltpro.maginaolivo.domain.expense.ExpenseOrigin
import com.isivoltpro.maginaolivo.domain.expense.ExpenseStatus
import com.isivoltpro.maginaolivo.domain.expense.JornadaCost
import com.isivoltpro.maginaolivo.domain.expense.JornadaExpenseKind
import com.isivoltpro.maginaolivo.domain.expense.RecollectionRates
import com.isivoltpro.maginaolivo.domain.expense.UnlinkedDayCosts
import com.isivoltpro.maginaolivo.domain.labour.*
import com.isivoltpro.maginaolivo.data.local.entity.HarvestLabourEntity
import com.isivoltpro.maginaolivo.domain.labour.CountDraft
import com.isivoltpro.maginaolivo.domain.labour.LabourChange
import com.isivoltpro.maginaolivo.domain.labour.LabourUnit
import com.isivoltpro.maginaolivo.domain.workspace.WorkspaceRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * CR-010 A3 — one money path for a day's calculated labour and machinery cost: posted once to
 * the Expense ledger, updated in place, never summed together with a hand-typed cost of the same
 * kind on the same day, and never rewritten once the Campaign is closed.
 */
@RunWith(AndroidJUnit4::class)
class DayCostContractTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val workspaceId = UUID.fromString("10000000-0000-0000-0000-0000000c0a31")
    private val farmId = UUID.fromString("20000000-0000-0000-0000-0000000c0a31")
    private val campaignId = UUID.fromString("40000000-0000-0000-0000-0000000c0a31")
    private val north = UUID.fromString("30000000-0000-0000-0000-0000000c0a31")
    private val now = Instant.parse("2026-11-27T19:00:00Z")
    private val day = LocalDate.parse("2026-11-27")

    private lateinit var db: MaginaOlivoDatabase
    private lateinit var harvests: OfflineFirstHarvestRepository
    private lateinit var labour: OfflineFirstLabourRepository
    private lateinit var equipment: OfflineFirstEquipmentRepository
    private lateinit var expenses: OfflineFirstExpenseRepository
    private lateinit var costs: OfflineFirstDayCostRepository

    @Before
    fun before() = runBlocking {
        context.deleteDatabase(DB)
        open()
        seed()
    }

    @After
    fun after() {
        db.close()
        context.deleteDatabase(DB)
    }

    @Test
    fun attendanceAndPricesPostOneCalculatedEntryUpdatedInPlace() = runBlocking {
        ok(costs.saveRates(farmId, RecollectionRates(fullDayMinor = 7_000, hourlyMinor = 900)))
        val dayId = ok(harvests.openJornada(farmId, day))
        named(dayId, 5)
        // Existing historical counts remain editable, including half days; no new anonymous writer.
        val halves = UUID.randomUUID()
        db.labourDao().upsertLabour(listOf(HarvestLabourEntity(halves, workspaceId, dayId,
            quantity = 2, unit = LabourUnit.HALF_DAY.name, metadata = LocalMetadata(now, now))))
        ok(labour.update(halves, LabourChange(2, LabourUnit.HALF_DAY, null,
            LabourRateSnapshot(7_000, "EUR", day, LabourRateBasis.DAY))))

        val first = calculated(dayId, ExpenseOrigin.DAY_LABOUR)!!
        // 5 × 70 € + 2 × 35 € = 420 €, posted once, with the prices used kept on the entry.
        assertEquals(42_000L, first.amountMinor)
        assertEquals(ExpenseStatus.POSTED, first.status)
        assertEquals(day, first.expenseDate)
        assertTrue(first.notes!!.contains("5 jornadas"))
        assertEquals(42_000L, JornadaCost.of(expenses.observeForHarvest(dayId).first()).postedMinor)

        // A change in attendance updates the same entry, never a second one.
        ok(labour.update(halves, LabourChange(3, LabourUnit.HALF_DAY, null)))
        val second = calculated(dayId, ExpenseOrigin.DAY_LABOUR)!!
        assertEquals(first.id, second.id)
        assertEquals(45_500L, second.amountMinor)
        assertEquals(1, expenses.observeForHarvest(dayId).first().count { it.origin == ExpenseOrigin.DAY_LABOUR })

        // The calculated entry is written only by the day: the expense form cannot edit or delete it.
        val edit = expenses.update(second.id, ExpenseDraft(day, "x", second.category, 1, farmId = farmId, harvestId = dayId))
        assertEquals(AppError.Conflict("calculated_cost"), (edit as AppResult.Failure).error)
        assertEquals(AppError.Conflict("calculated_cost"), (expenses.delete(second.id) as AppResult.Failure).error)
    }

    @Test
    fun aHandTypedCostOfTheSameKindStandsUntilTheFarmerPicksTheCalculation() = runBlocking {
        ok(costs.saveRates(farmId, RecollectionRates(fullDayMinor = 7_000)))
        val dayId = ok(harvests.openJornada(farmId, day))
        named(dayId, 5)
        // #475: the farmer says the hand-typed 300 € replace the calculation.
        val manual = ok(expenses.create(cost(dayId, JornadaExpenseKind.LABOUR, 30_000).copy(dayCostRole = DayCostRole.REPLACEMENT)))
        assertEquals(ExpenseOrigin.DAY_REPLACEMENT, expenses.observe(manual).first()!!.origin)

        // Never both: the hand-typed 300 € counts; the calculated 350 € is kept as a draft.
        assertEquals(ExpenseStatus.DRAFT, calculated(dayId, ExpenseOrigin.DAY_LABOUR)!!.status)
        assertEquals(30_000L, JornadaCost.of(expenses.observeForHarvest(dayId).first()).postedMinor)

        ok(costs.preferCalculated(dayId, DayCostKind.LABOUR))
        assertEquals(ExpenseStatus.POSTED, calculated(dayId, ExpenseOrigin.DAY_LABOUR)!!.status)
        // The hand-typed cost is kept, as a draft, never deleted or merged.
        assertEquals(ExpenseStatus.DRAFT, expenses.observe(manual).first()!!.status)
        assertEquals(35_000L, JornadaCost.of(expenses.observeForHarvest(dayId).first()).postedMinor)
    }

    /** #502: an automatic day goes with its last jornal, machine or Gasto; a hand-recorded one never does. */
    @Test
    fun anAutomaticDayGoesWithItsLastChildAndAHandRecordedOneStays() = runBlocking {
        // Last jornal.
        val byLabour = ok(harvests.openJornada(farmId, day))
        named(byLabour, 1)
        ok(labour.remove(labour.observeForHarvest(byLabour).first().single().id))
        assertNull(harvests.observe(byLabour).first())

        // Last Gasto.
        val byExpense = ok(harvests.openJornada(farmId, day.minusDays(1)))
        val diesel = ok(expenses.create(cost(byExpense, JornadaExpenseKind.DIESEL, 3_000).copy(expenseDate = day.minusDays(1))))
        ok(expenses.delete(diesel))
        assertNull(harvests.observe(byExpense).first())

        // A day that still holds something stays.
        val kept = ok(harvests.openJornada(farmId, day.minusDays(2)))
        ok(expenses.create(cost(kept, JornadaExpenseKind.DIESEL, 3_000).copy(expenseDate = day.minusDays(2))))
        named(kept, 1)
        ok(labour.remove(labour.observeForHarvest(kept).first().single().id))
        assertEquals(kept, harvests.observe(kept).first()!!.id)

        // A Jornada recorded by hand keeps its typed kilos when its last Gasto goes.
        val byHand = ok(harvests.create(HarvestDraft(farmId, day.minusDays(3), 5_000_000, listOf(HarvestShareInput(north, null)))))
        val handCost = ok(expenses.create(cost(byHand, JornadaExpenseKind.TRANSPORT, 2_000).copy(expenseDate = day.minusDays(3))))
        ok(expenses.delete(handCost))
        assertEquals(5_000_000L, harvests.observe(byHand).first()!!.totalGrams)
    }

    /** #475: a hand-typed cost of the day adds unless the farmer says it replaces; nothing is deduced. */
    @Test
    fun aHandTypedCostAddsUnlessTheFarmerSaysItReplaces() = runBlocking {
        ok(costs.saveRates(farmId, RecollectionRates(fullDayMinor = 7_000)))
        val dayId = ok(harvests.openJornada(farmId, day))
        named(dayId, 5)
        // Same category as the calculation, chosen «Se añade»: both count.
        val added = ok(expenses.create(cost(dayId, JornadaExpenseKind.LABOUR, 30_000)))
        assertEquals(ExpenseOrigin.MANUAL, expenses.observe(added).first()!!.origin)
        assertEquals(ExpenseStatus.POSTED, calculated(dayId, ExpenseOrigin.DAY_LABOUR)!!.status)
        assertEquals(65_000L, JornadaCost.of(expenses.observeForHarvest(dayId).first()).postedMinor)

        // Changing it to «Sustituye» is the farmer's explicit edit; back to «Se añade», both count again.
        val draft = cost(dayId, JornadaExpenseKind.LABOUR, 30_000)
        ok(expenses.update(added, draft.copy(dayCostRole = DayCostRole.REPLACEMENT)))
        assertEquals(ExpenseOrigin.DAY_REPLACEMENT, expenses.observe(added).first()!!.origin)
        assertEquals(30_000L, JornadaCost.of(expenses.observeForHarvest(dayId).first()).postedMinor)
        ok(expenses.update(added, draft))
        assertEquals(ExpenseOrigin.MANUAL, expenses.observe(added).first()!!.origin)
        assertEquals(65_000L, JornadaCost.of(expenses.observeForHarvest(dayId).first()).postedMinor)

        // A cost typed before the day had a calculation keeps adding when the calculation arrives.
        val otherDay = ok(harvests.openJornada(farmId, day.minusDays(1)))
        val early = ok(expenses.create(cost(otherDay, JornadaExpenseKind.LABOUR, 20_000).copy(expenseDate = day.minusDays(1))))
        named(otherDay, 2)
        assertEquals(ExpenseOrigin.MANUAL, expenses.observe(early).first()!!.origin)
        assertEquals(ExpenseStatus.POSTED, calculated(otherDay, ExpenseOrigin.DAY_LABOUR)!!.status)
        assertEquals(34_000L, JornadaCost.of(expenses.observeForHarvest(otherDay).first()).postedMinor)
    }

    /** #475: «Sustituye» needs a calculation of that kind; diesel, oil and the like never replace. */
    @Test
    fun replacingNeedsACalculationOfThatKind() = runBlocking {
        ok(costs.saveRates(farmId, RecollectionRates(fullDayMinor = 7_000)))
        val dayId = ok(harvests.openJornada(farmId, day))
        val before = outboxCount()
        assertEquals(AppError.Validation("dayCostRole", "nothing_to_replace"),
            (expenses.create(cost(dayId, JornadaExpenseKind.LABOUR, 30_000).copy(dayCostRole = DayCostRole.REPLACEMENT)) as AppResult.Failure).error)
        named(dayId, 1)
        val afterCrew = outboxCount()
        assertEquals(AppError.Validation("dayCostRole", "not_replaceable"),
            (expenses.create(cost(dayId, JornadaExpenseKind.DIESEL, 3_000).copy(dayCostRole = DayCostRole.REPLACEMENT)) as AppResult.Failure).error)
        assertEquals(AppError.Validation("dayCostRole", "no_day"),
            (expenses.create(cost(dayId, JornadaExpenseKind.LABOUR, 3_000).copy(harvestId = null, dayCostRole = DayCostRole.REPLACEMENT)) as AppResult.Failure).error)
        assertEquals(afterCrew, outboxCount())
        assertTrue(before < afterCrew)
        assertEquals(ExpenseStatus.POSTED, calculated(dayId, ExpenseOrigin.DAY_LABOUR)!!.status)
    }

    /** #475 upgrade: what counted before the choice existed keeps counting exactly the same. */
    @Test
    fun existingReplacementsAreMarkedOnceAndNothingElseIsReinterpreted() = runBlocking {
        ok(costs.saveRates(farmId, RecollectionRates(fullDayMinor = 7_000)))
        val dayId = ok(harvests.openJornada(farmId, day))
        named(dayId, 5)
        // As an earlier version left it: a hand-typed 300 € of jornales counted, the calculation a draft.
        val calc = db.expenseDao().listForHarvest(dayId).single { it.origin == ExpenseOrigin.DAY_LABOUR.name }
        db.expenseDao().upsert(calc.copy(status = ExpenseStatus.DRAFT.name))
        val legacy = ExpenseEntity(UUID.randomUUID(), workspaceId, campaignId = campaignId, farmId = farmId, harvestId = dayId,
            expenseDate = day, concept = "Cuadrilla", category = "LABOR", amountMinor = 30_000, currency = "EUR",
            status = ExpenseStatus.POSTED.name, origin = ExpenseOrigin.MANUAL.name, metadata = LocalMetadata(now, now))
        db.expenseDao().upsert(legacy)
        // A day whose calculation counts keeps its hand-typed jornales adding.
        val otherDay = ok(harvests.openJornada(farmId, day.minusDays(1)))
        named(otherDay, 1)
        val adding = ok(expenses.create(cost(otherDay, JornadaExpenseKind.LABOUR, 5_000).copy(expenseDate = day.minusDays(1))))

        ok(costs.markExistingReplacements())
        assertEquals(ExpenseOrigin.DAY_REPLACEMENT, expenses.observe(legacy.id).first()!!.origin)
        assertEquals(ExpenseStatus.DRAFT, calculated(dayId, ExpenseOrigin.DAY_LABOUR)!!.status)
        assertEquals(30_000L, JornadaCost.of(expenses.observeForHarvest(dayId).first()).postedMinor)
        assertEquals(ExpenseOrigin.MANUAL, expenses.observe(adding).first()!!.origin)

        // A cost added afterwards on the same day stays «Se añade»: a second run changes nothing.
        val later = ok(expenses.create(cost(dayId, JornadaExpenseKind.LABOUR, 2_000)))
        val version = db.expenseDao().findById(legacy.id)!!.metadata.version
        ok(costs.markExistingReplacements())
        assertEquals(ExpenseOrigin.MANUAL, expenses.observe(later).first()!!.origin)
        assertEquals(version, db.expenseDao().findById(legacy.id)!!.metadata.version)
        assertEquals(32_000L, JornadaCost.of(expenses.observeForHarvest(dayId).first()).postedMinor)
    }

    @Test
    fun machinerySnapshotsPostOnceAndUsualChangesDoNotRepriceThem() = runBlocking {
        val dayId = ok(harvests.openJornada(farmId, day))
        ok(costs.saveRates(farmId, RecollectionRates(equipmentDayMinor = mapOf(
            EquipmentType.SHAKER to 7_000, EquipmentType.COMB to 2_000, EquipmentType.TRAILER to 3_000))))
        ok(equipment.replaceForHarvest(dayId, listOf(EquipmentDraftLine(EquipmentType.SHAKER, 1),
            EquipmentDraftLine(EquipmentType.COMB, 1), EquipmentDraftLine(EquipmentType.TRAILER, 1))))
        val entry = calculated(dayId, ExpenseOrigin.DAY_EQUIPMENT)!!
        assertEquals(12_000L, entry.amountMinor)
        assertEquals(ExpenseStatus.POSTED, entry.status)
        val outboxBeforeReopen = db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM sync_outbox WHERE entity_type='EXPENSE' AND entity_id='${entry.id}'").use {
            it.moveToFirst(); it.getInt(0)
        }
        db.close()
        open()
        assertEquals(7_000L, equipment.observeForHarvest(dayId).first().first { it.type == EquipmentType.SHAKER }.appliedPrice!!.unitPriceMinor)
        ok(equipment.replaceForHarvest(dayId, listOf(EquipmentDraftLine(EquipmentType.SHAKER, 1),
            EquipmentDraftLine(EquipmentType.COMB, 1), EquipmentDraftLine(EquipmentType.TRAILER, 1))))
        assertEquals(outboxBeforeReopen, db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM sync_outbox WHERE entity_type='EXPENSE' AND entity_id='${entry.id}'").use {
            it.moveToFirst(); it.getInt(0)
        })
        ok(costs.saveRates(farmId, RecollectionRates(equipmentDayMinor = mapOf(EquipmentType.SHAKER to 9_000))))
        assertEquals(12_000L, calculated(dayId, ExpenseOrigin.DAY_EQUIPMENT)!!.amountMinor)
        ok(equipment.replaceForHarvest(dayId, listOf(EquipmentDraftLine(EquipmentType.SHAKER, 2),
            EquipmentDraftLine(EquipmentType.COMB, 1), EquipmentDraftLine(EquipmentType.TRAILER, 1))))
        val changed = calculated(dayId, ExpenseOrigin.DAY_EQUIPMENT)!!
        assertEquals(entry.id, changed.id)
        assertEquals(19_000L, changed.amountMinor)
        assertEquals(7_000L, equipment.observeForHarvest(dayId).first().first { it.type == EquipmentType.SHAKER }.appliedPrice!!.unitPriceMinor)
        assertEquals(1, expenses.observeForHarvest(dayId).first().count { it.origin == ExpenseOrigin.DAY_EQUIPMENT })

        ok(equipment.replaceForHarvest(dayId, listOf(EquipmentDraftLine(EquipmentType.SHAKER, 2, appliedPrice =
            EquipmentPriceSnapshot(8_000, "EUR", day)), EquipmentDraftLine(EquipmentType.COMB, 1), EquipmentDraftLine(EquipmentType.TRAILER, 1))))
        assertEquals(21_000L, calculated(dayId, ExpenseOrigin.DAY_EQUIPMENT)!!.amountMinor)

        // No equipment left: the calculated entry goes.
        ok(equipment.replaceForHarvest(dayId, emptyList()))
        assertNull(calculated(dayId, ExpenseOrigin.DAY_EQUIPMENT))
    }

    @Test
    fun unknownLegacyPriceBlocksPricedAppendAndPreservesExistingLedger() = runBlocking {
        val dayId = ok(harvests.openJornada(farmId, day))
        ok(equipment.replaceForHarvest(dayId, listOf(EquipmentDraftLine(EquipmentType.TRACTOR, 1))))
        val beforeRows = equipment.observeForHarvest(dayId).first()
        ok(costs.saveRates(farmId, RecollectionRates(equipmentDayMinor = mapOf(EquipmentType.SHAKER to 7_000))))
        val rejected = equipment.replaceForHarvest(dayId, listOf(EquipmentDraftLine(EquipmentType.TRACTOR, 1),
            EquipmentDraftLine(EquipmentType.SHAKER, 1)))
        assertEquals(AppError.Validation("appliedPrice", "confirm_missing_prices"), (rejected as AppResult.Failure).error)
        assertEquals(beforeRows, equipment.observeForHarvest(dayId).first())
        assertNull(calculated(dayId, ExpenseOrigin.DAY_EQUIPMENT))
    }

    @Test
    fun explicitUnknownNewLineDoesNotCaptureUsualPriceButDefaultsAndZeroStillDo() = runBlocking {
        val dayId = ok(harvests.openJornada(farmId, day))
        ok(costs.saveRates(farmId, RecollectionRates(equipmentDayMinor = mapOf(EquipmentType.SHAKER to 7_000))))
        ok(equipment.replaceForHarvest(dayId, listOf(EquipmentDraftLine(EquipmentType.SHAKER, 1,
            captureUsualPriceWhenMissing = false))))
        assertNull(equipment.observeForHarvest(dayId).first().single().appliedPrice)
        assertNull(calculated(dayId, ExpenseOrigin.DAY_EQUIPMENT))

        ok(equipment.replaceForHarvest(dayId, emptyList()))
        // #502: with nothing left, the automatic day went with its last machine; the next one opens it again.
        assertNull(harvests.observe(dayId).first())
        val again = ok(harvests.openJornada(farmId, day))
        ok(equipment.replaceForHarvest(again, listOf(EquipmentDraftLine(EquipmentType.SHAKER, 1))))
        assertEquals(7_000L, equipment.observeForHarvest(again).first().single().appliedPrice!!.unitPriceMinor)
        assertEquals(7_000L, calculated(again, ExpenseOrigin.DAY_EQUIPMENT)!!.amountMinor)

        ok(equipment.replaceForHarvest(again, listOf(EquipmentDraftLine(EquipmentType.SHAKER, 1,
            appliedPrice = EquipmentPriceSnapshot(0, "EUR", day), captureUsualPriceWhenMissing = false))))
        assertEquals(0L, equipment.observeForHarvest(again).first().single().appliedPrice!!.unitPriceMinor)
        assertEquals(0L, calculated(again, ExpenseOrigin.DAY_EQUIPMENT)!!.amountMinor)
    }

    @Test
    fun aPostedCostNeverOutlivesAChangeToItsPricedLinesWhileAPriceIsMissing() = runBlocking {
        // #449 (audit 04-10, case B): Tractor 85 + Vibradora 60 = 145 posted.
        val dayId = ok(harvests.openJornada(farmId, day))
        val tractor = EquipmentDraftLine(EquipmentType.TRACTOR, 1, appliedPrice = EquipmentPriceSnapshot(8_500, "EUR", day))
        val shaker = EquipmentDraftLine(EquipmentType.SHAKER, 1, appliedPrice = EquipmentPriceSnapshot(6_000, "EUR", day))
        val trailer = EquipmentDraftLine(EquipmentType.TRAILER, 1, captureUsualPriceWhenMissing = false)
        ok(equipment.replaceForHarvest(dayId, listOf(tractor, shaker)))
        assertEquals(14_500L, calculated(dayId, ExpenseOrigin.DAY_EQUIPMENT)!!.amountMinor)

        // Removing the Tractor and adding a Remolque without price: rejected, nothing written.
        val rows = equipment.observeForHarvest(dayId).first()
        val outbox = outboxCount()
        val swapped = equipment.replaceForHarvest(dayId, listOf(shaker, trailer))
        assertEquals(AppError.Validation("appliedPrice", "confirm_before_recompose"), (swapped as AppResult.Failure).error)
        assertEquals(rows, equipment.observeForHarvest(dayId).first())
        assertEquals(outbox, outboxCount())
        assertEquals(14_500L, calculated(dayId, ExpenseOrigin.DAY_EQUIPMENT)!!.amountMinor)

        // Adding a priced Peine together with the unpriced Remolque would leave 145 short: rejected.
        val comb = EquipmentDraftLine(EquipmentType.COMB, 1, appliedPrice = EquipmentPriceSnapshot(2_000, "EUR", day))
        val both = equipment.replaceForHarvest(dayId, listOf(tractor, shaker, comb, trailer))
        assertEquals(AppError.Validation("appliedPrice", "confirm_before_recompose"), (both as AppResult.Failure).error)
        assertEquals(rows, equipment.observeForHarvest(dayId).first())

        // Adding the Remolque alone keeps 145 as the exact known subtotal.
        ok(equipment.replaceForHarvest(dayId, listOf(tractor, shaker, trailer)))
        assertNull(equipment.observeForHarvest(dayId).first().single { it.type == EquipmentType.TRAILER }.appliedPrice)
        assertEquals(14_500L, calculated(dayId, ExpenseOrigin.DAY_EQUIPMENT)!!.amountMinor)

        // With the Remolque still unpriced, changing a priced line is rejected too.
        val more = equipment.replaceForHarvest(dayId, listOf(tractor, shaker.copy(quantity = 2), trailer))
        assertEquals(AppError.Validation("appliedPrice", "confirm_before_recompose"), (more as AppResult.Failure).error)
        val repriced = equipment.replaceForHarvest(dayId, listOf(tractor,
            shaker.copy(appliedPrice = EquipmentPriceSnapshot(7_000, "EUR", day)), trailer))
        assertEquals(AppError.Validation("appliedPrice", "confirm_before_recompose"), (repriced as AppResult.Failure).error)
        assertEquals(14_500L, calculated(dayId, ExpenseOrigin.DAY_EQUIPMENT)!!.amountMinor)

        // Confirming the last price recalculates once, in place.
        val postedId = calculated(dayId, ExpenseOrigin.DAY_EQUIPMENT)!!.id
        ok(equipment.replaceForHarvest(dayId, listOf(tractor, shaker,
            trailer.copy(appliedPrice = EquipmentPriceSnapshot(2_000, "EUR", day)))))
        val confirmed = calculated(dayId, ExpenseOrigin.DAY_EQUIPMENT)!!
        assertEquals(postedId, confirmed.id)
        assertEquals(16_500L, confirmed.amountMinor)
        assertEquals(1, db.expenseDao().listForHarvest(dayId).count { it.origin == ExpenseOrigin.DAY_EQUIPMENT.name })

        // Once complete, the composition can change freely again.
        ok(equipment.replaceForHarvest(dayId, listOf(shaker.copy(quantity = 2))))
        assertEquals(12_000L, calculated(dayId, ExpenseOrigin.DAY_EQUIPMENT)!!.amountMinor)
    }

    @Test
    fun historicalYenLedgerSurvivesPartialConfirmationAndEuroUsualRate() = runBlocking {
        val dayId = ok(harvests.openJornada(farmId, day))
        ok(equipment.replaceForHarvest(dayId, listOf(EquipmentDraftLine(EquipmentType.SHAKER, 1),
            EquipmentDraftLine(EquipmentType.COMB, 1))))
        val historicalId = UUID.randomUUID()
        db.expenseDao().upsert(ExpenseEntity(historicalId, workspaceId, campaignId = campaignId, farmId = farmId,
            harvestId = dayId, expenseDate = day, concept = "Maquinaria histórica", category = "MACHINERY",
            amountMinor = 9_000, currency = "JPY", status = ExpenseStatus.POSTED.name,
            origin = ExpenseOrigin.DAY_EQUIPMENT.name,
            metadata = LocalMetadata(now, now)))
        ok(costs.saveRates(farmId, RecollectionRates(equipmentDayMinor = mapOf(EquipmentType.TRAILER to 3_000), currency = "EUR")))
        ok(equipment.replaceForHarvest(dayId, listOf(EquipmentDraftLine(EquipmentType.SHAKER, 1,
            appliedPrice = EquipmentPriceSnapshot(7_000, "JPY", day)), EquipmentDraftLine(EquipmentType.COMB, 1))))
        assertEquals(9_000L, calculated(dayId, ExpenseOrigin.DAY_EQUIPMENT)!!.amountMinor)
        val before = db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM sync_outbox").use { it.moveToFirst(); it.getInt(0) }
        val rejected = equipment.replaceForHarvest(dayId, listOf(EquipmentDraftLine(EquipmentType.SHAKER, 1),
            EquipmentDraftLine(EquipmentType.COMB, 1), EquipmentDraftLine(EquipmentType.TRAILER, 1,
                appliedPrice = EquipmentPriceSnapshot(3_000, "JPY", day))))
        assertEquals(AppError.Validation("appliedPrice", "confirm_missing_prices"), (rejected as AppResult.Failure).error)
        assertEquals(before, db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM sync_outbox").use { it.moveToFirst(); it.getInt(0) })
        ok(equipment.replaceForHarvest(dayId, listOf(EquipmentDraftLine(EquipmentType.SHAKER, 1),
            EquipmentDraftLine(EquipmentType.COMB, 1, appliedPrice = EquipmentPriceSnapshot(2_000, "JPY", day)))))
        assertEquals(historicalId, calculated(dayId, ExpenseOrigin.DAY_EQUIPMENT)!!.id)
        assertEquals(9_000L, calculated(dayId, ExpenseOrigin.DAY_EQUIPMENT)!!.amountMinor)
        val wrongCurrency = equipment.replaceForHarvest(dayId, listOf(EquipmentDraftLine(EquipmentType.SHAKER, 1),
            EquipmentDraftLine(EquipmentType.COMB, 1), EquipmentDraftLine(EquipmentType.TRAILER, 1,
                appliedPrice = EquipmentPriceSnapshot(3_000, "EUR", day))))
        assertEquals(AppError.Validation("currency", "currency_mismatch"), (wrongCurrency as AppResult.Failure).error)
    }

    @Test
    fun unknownHistoricalMachineryRejectsRentalCreatePostUpdateAndLinkWithoutWrites() = runBlocking {
        val dayId = ok(harvests.openJornada(farmId, day))
        ok(equipment.replaceForHarvest(dayId, listOf(EquipmentDraftLine(EquipmentType.TRACTOR, 1))))
        val historicalId = UUID.randomUUID()
        db.expenseDao().upsert(ExpenseEntity(historicalId, workspaceId, campaignId = campaignId, farmId = farmId,
            harvestId = dayId, expenseDate = day, concept = "Maquinaria histórica", category = "MACHINERY",
            amountMinor = 9_000, currency = "JPY", status = ExpenseStatus.POSTED.name,
            origin = ExpenseOrigin.DAY_EQUIPMENT.name,
            metadata = LocalMetadata(now, now)))
        val originalLedger = db.expenseDao().findById(historicalId)!!
        val rental = cost(dayId, JornadaExpenseKind.RENTAL, 4_000).copy(currency = "JPY", dayCostRole = DayCostRole.REPLACEMENT)
        val beforeCreateOutbox = outboxCount()
        assertEquals(AppError.Validation("appliedPrice", "confirm_missing_prices"),
            (expenses.create(rental) as AppResult.Failure).error)
        assertEquals(beforeCreateOutbox, outboxCount())
        assertEquals(listOf(historicalId), expenses.observeForHarvest(dayId).first().map { it.id })

        val draftId = UUID.randomUUID()
        db.expenseDao().upsert(ExpenseEntity(draftId, workspaceId, campaignId = campaignId, farmId = farmId,
            harvestId = dayId, expenseDate = day, concept = rental.concept, category = rental.category.name,
            amountMinor = 4_000, currency = "JPY", status = ExpenseStatus.DRAFT.name,
            origin = ExpenseOrigin.DAY_REPLACEMENT.name, metadata = LocalMetadata(now, now)))
        val draftBefore = db.expenseDao().findById(draftId)!!
        val beforePostOutbox = outboxCount()
        assertEquals(AppError.Validation("appliedPrice", "confirm_missing_prices"),
            (expenses.post(draftId) as AppResult.Failure).error)
        assertEquals(draftBefore, db.expenseDao().findById(draftId))
        assertEquals(beforePostOutbox, outboxCount())

        val unlinked = ok(expenses.create(rental.copy(harvestId = null, campaignId = campaignId, dayCostRole = DayCostRole.ADDITIVE)))
        val unlinkedBefore = db.expenseDao().findById(unlinked)!!
        val beforeUpdateOutbox = outboxCount()
        assertEquals(AppError.Validation("appliedPrice", "confirm_missing_prices"),
            (expenses.update(unlinked, rental) as AppResult.Failure).error)
        assertEquals(unlinkedBefore, db.expenseDao().findById(unlinked))
        assertEquals(beforeUpdateOutbox, outboxCount())
        assertEquals(AppError.Validation("appliedPrice", "confirm_missing_prices"),
            (costs.linkToDay(unlinked, dayId, DayCostRole.REPLACEMENT) as AppResult.Failure).error)
        assertEquals(unlinkedBefore, db.expenseDao().findById(unlinked))
        assertEquals(beforeUpdateOutbox, outboxCount())
        assertEquals(originalLedger, db.expenseDao().findById(historicalId))
        assertEquals(9_000L, expenses.observeForHarvest(dayId).first()
            .filter { it.status == ExpenseStatus.POSTED && it.currency == "JPY" }.sumOf { it.amountMinor })

        // Oil is additive, not a replacing rental, even while the historical unit price is unknown.
        ok(expenses.create(cost(dayId, JornadaExpenseKind.LUBRICANT, 1_200).copy(currency = "JPY")))
        assertEquals(10_200L, expenses.observeForHarvest(dayId).first()
            .filter { it.status == ExpenseStatus.POSTED && it.currency == "JPY" }.sumOf { it.amountMinor })
        assertEquals(originalLedger, db.expenseDao().findById(historicalId))
    }

    @Test
    fun partialLegacyConfirmationRejectsKnownSubtotalOverflowBeforeWriting() = runBlocking {
        val dayId = ok(harvests.openJornada(farmId, day))
        val unknown = listOf(EquipmentDraftLine(EquipmentType.SHAKER, 1),
            EquipmentDraftLine(EquipmentType.COMB, 1), EquipmentDraftLine(EquipmentType.TRAILER, 1))
        ok(equipment.replaceForHarvest(dayId, unknown))
        val historicalId = UUID.randomUUID()
        db.expenseDao().upsert(ExpenseEntity(historicalId, workspaceId, campaignId = campaignId, farmId = farmId,
            harvestId = dayId, expenseDate = day, concept = "Maquinaria histórica", category = "MACHINERY",
            amountMinor = 9_000, currency = "EUR", status = ExpenseStatus.POSTED.name,
            origin = ExpenseOrigin.DAY_EQUIPMENT.name,
            metadata = LocalMetadata(now, now)))
        val beforeRows = equipment.observeForHarvest(dayId).first()
        val beforeLedger = db.expenseDao().findById(historicalId)
        val beforeOutbox = outboxCount()
        val huge = EquipmentPriceSnapshot(6_000_000_000_000_000_000L, "EUR", day)
        val result = equipment.replaceForHarvest(dayId, listOf(unknown[0].copy(appliedPrice = huge),
            unknown[1].copy(appliedPrice = huge), unknown[2]))
        assertEquals(AppError.Validation("appliedPrice", "overflow"), (result as AppResult.Failure).error)
        assertEquals(beforeRows, equipment.observeForHarvest(dayId).first())
        assertEquals(beforeLedger, db.expenseDao().findById(historicalId))
        assertEquals(beforeOutbox, outboxCount())
    }

    @Test
    fun explicitZeroIsPostedAndOverflowRollsBackRowsAndOutbox() = runBlocking {
        val dayId = ok(harvests.openJornada(farmId, day))
        ok(equipment.replaceForHarvest(dayId, listOf(EquipmentDraftLine(EquipmentType.SHAKER, 1,
            appliedPrice = EquipmentPriceSnapshot(0, "EUR", day)))))
        val zero = calculated(dayId, ExpenseOrigin.DAY_EQUIPMENT)!!
        assertEquals(0L, zero.amountMinor)
        assertEquals(ExpenseStatus.POSTED, zero.status)
        val beforeRows = equipment.observeForHarvest(dayId).first()
        val beforeOutbox = db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM sync_outbox").use { it.moveToFirst(); it.getInt(0) }
        val failed = equipment.replaceForHarvest(dayId, listOf(EquipmentDraftLine(EquipmentType.SHAKER, 2,
            appliedPrice = EquipmentPriceSnapshot(Long.MAX_VALUE, "EUR", day))))
        assertEquals(AppError.Validation("appliedPrice", "overflow"), (failed as AppResult.Failure).error)
        assertEquals(beforeRows, equipment.observeForHarvest(dayId).first())
        assertEquals(beforeOutbox, db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM sync_outbox").use { it.moveToFirst(); it.getInt(0) })
        assertEquals(zero.id, calculated(dayId, ExpenseOrigin.DAY_EQUIPMENT)!!.id)
        val sumOverflow = equipment.replaceForHarvest(dayId, listOf(
            EquipmentDraftLine(EquipmentType.SHAKER, 1, appliedPrice = EquipmentPriceSnapshot(6_000_000_000_000_000_000L, "EUR", day)),
            EquipmentDraftLine(EquipmentType.COMB, 1, appliedPrice = EquipmentPriceSnapshot(6_000_000_000_000_000_000L, "EUR", day)),
        ))
        assertEquals(AppError.Validation("appliedPrice", "overflow"), (sumOverflow as AppResult.Failure).error)
        assertEquals(beforeRows, equipment.observeForHarvest(dayId).first())
        assertEquals(beforeOutbox, db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM sync_outbox").use { it.moveToFirst(); it.getInt(0) })
    }

    @Test
    fun aClosedCampaignKeepsItsCostsAndARemovedDayTakesItsCalculatedOnes() = runBlocking {
        ok(costs.saveRates(farmId, RecollectionRates(fullDayMinor = 7_000)))
        val dayId = ok(harvests.openJornada(farmId, day))
        named(dayId, 4)
        val calculatedId = calculated(dayId, ExpenseOrigin.DAY_LABOUR)!!.id

        val campaign = db.campaignDao().findById(campaignId)!!
        db.campaignDao().upsert(campaign.copy(status = CampaignStatus.CLOSED))
        ok(costs.saveRates(farmId, RecollectionRates(fullDayMinor = 9_000)))
        // History: the 70 € price stays on the closed Campaign's entry.
        assertEquals(28_000L, expenses.observe(calculatedId).first()!!.amountMinor)

        db.campaignDao().upsert(campaign)
        ok(harvests.delete(dayId))
        assertNull(expenses.observe(calculatedId).first())
    }

    @Test
    fun oilForTheMachinesAddsToTheirCalculatedDayAndNeverReplacesIt() = runBlocking {
        ok(costs.saveRates(farmId, RecollectionRates(equipmentDayMinor = mapOf(EquipmentType.SHAKER to 3_500))))
        val dayId = ok(harvests.openJornada(farmId, day))
        ok(equipment.replaceForHarvest(dayId, listOf(EquipmentDraftLine(EquipmentType.SHAKER, 1))))
        ok(expenses.create(cost(dayId, JornadaExpenseKind.LUBRICANT, 1_200)))
        ok(expenses.create(cost(dayId, JornadaExpenseKind.LUBRICANT, 800).copy(concept = JornadaExpenseKind.LUBRICANT.concept("Aceite hidráulico"))))
        named(dayId, 1)

        // 35 € of shaker plus 12 € and 8 € of oil: both count, nothing is sent to draft.
        assertEquals(ExpenseStatus.POSTED, calculated(dayId, ExpenseOrigin.DAY_EQUIPMENT)!!.status)
        assertEquals(5_500L, JornadaCost.of(expenses.observeForHarvest(dayId).first()).postedMinor)

        // A machinery rental the farmer says replaces the calculation does stand for it (#475).
        ok(expenses.create(cost(dayId, JornadaExpenseKind.RENTAL, 4_000).copy(dayCostRole = DayCostRole.REPLACEMENT)))
        assertEquals(ExpenseStatus.DRAFT, calculated(dayId, ExpenseOrigin.DAY_EQUIPMENT)!!.status)
        assertEquals(6_000L, JornadaCost.of(expenses.observeForHarvest(dayId).first()).postedMinor)
    }

    @Test
    fun aClosedCampaignRefusesToSwapWhichCostCounts() = runBlocking {
        ok(costs.saveRates(farmId, RecollectionRates(fullDayMinor = 7_000)))
        val dayId = ok(harvests.openJornada(farmId, day))
        named(dayId, 5)
        val manual = ok(expenses.create(cost(dayId, JornadaExpenseKind.LABOUR, 30_000).copy(dayCostRole = DayCostRole.REPLACEMENT)))
        val campaign = db.campaignDao().findById(campaignId)!!
        db.campaignDao().upsert(campaign.copy(status = CampaignStatus.CLOSED))

        val refused = costs.preferCalculated(dayId, DayCostKind.LABOUR)
        assertEquals(AppError.Conflict("campaign_closed"), (refused as AppResult.Failure).error)
        // History unchanged: the hand-typed cost still counts, the calculation stays a draft.
        assertEquals(ExpenseStatus.POSTED, expenses.observe(manual).first()!!.status)
        assertEquals(ExpenseStatus.DRAFT, calculated(dayId, ExpenseOrigin.DAY_LABOUR)!!.status)
        assertEquals(30_000L, JornadaCost.of(expenses.observeForHarvest(dayId).first()).postedMinor)
    }

    /** Codex #477 (#433): a same-day cost tied to work joins a Jornada only when that work is of its Campaign. */
    @Test
    fun aCostOfWorkJoinsAJornadaOnlyWhenTheWorkIsOfThatCampaign() = runBlocking {
        val dayId = ok(harvests.openJornada(farmId, day))
        val otherCampaign = UUID.randomUUID()
        db.campaignDao().upsert(
            CampaignEntity(otherCampaign, workspaceId, farmId, "2025/26", LocalDate.parse("2025-10-01"), null, CampaignStatus.ACTIVE, metadata = LocalMetadata(now, now)),
        )
        val general = work(campaign = null)
        val ofThisCampaign = work(campaign = campaignId)
        val ofAnother = work(campaign = otherCampaign)

        // General work and work of another Campaign are refused, and nothing changes.
        listOf(general, ofAnother).forEach { activityId ->
            val id = ok(expenses.create(cost(dayId, JornadaExpenseKind.LABOUR, 12_000).copy(harvestId = null, activityId = activityId)))
            val before = db.expenseDao().findById(id)!!
            val outboxBefore = outboxCount()
            assertEquals(AppError.Validation("activityId", "not_in_day"), (costs.linkToDay(id, dayId) as AppResult.Failure).error)
            assertEquals(before, db.expenseDao().findById(id))
            assertEquals(outboxBefore, outboxCount())
        }
        // Work of this Jornada's Campaign is linked.
        val linked = ok(expenses.create(cost(dayId, JornadaExpenseKind.TRANSPORT, 3_000).copy(harvestId = null, activityId = ofThisCampaign)))
        ok(costs.linkToDay(linked, dayId))
        val after = db.expenseDao().findById(linked)!!
        assertEquals(dayId, after.harvestId)
        assertEquals(campaignId, after.campaignId)
    }

    private suspend fun work(campaign: UUID?): UUID = UUID.randomUUID().also { id ->
        db.activityDao().upsert(
            com.isivoltpro.maginaolivo.data.local.entity.ActivityEntity(
                id, workspaceId, campaignId = campaign, farmId = farmId, activityDate = day, type = "PRUNING",
                status = com.isivoltpro.maginaolivo.data.local.model.ActivityStatus.COMPLETED, description = "Poda",
                metadata = LocalMetadata(now, now),
            ),
        )
    }

    @Test
    fun anUnlinkedHandTypedCostOfTheSameDateIsShownAndOnlyTheFarmerLinksIt() = runBlocking {
        ok(costs.saveRates(farmId, RecollectionRates(fullDayMinor = 7_000)))
        val dayId = ok(harvests.openJornada(farmId, day))
        named(dayId, 5)
        val unlinked = ok(expenses.create(cost(dayId, JornadaExpenseKind.LABOUR, 30_000).copy(harvestId = null, campaignId = campaignId)))
        val otherDate = ok(expenses.create(cost(dayId, JornadaExpenseKind.LABOUR, 9_000).copy(harvestId = null, campaignId = campaignId, expenseDate = day.minusDays(1))))
        // #475: one kept «Fuera de campaña» is never a candidate nor linked.
        val outside = ok(expenses.create(cost(dayId, JornadaExpenseKind.LABOUR, 5_000).copy(harvestId = null)))

        // Ambiguous: listed for the day, never merged or dropped; the calculation still counts.
        val listed = UnlinkedDayCosts.of(dayId, farmId, day, expenses.observeAll().first(), campaignId)
        assertEquals(listOf(unlinked), listed.map { it.id })
        assertEquals(ExpenseStatus.POSTED, calculated(dayId, ExpenseOrigin.DAY_LABOUR)!!.status)

        // Another date is not this day's.
        assertEquals(AppError.Validation("expense", "other_day"), (costs.linkToDay(otherDate, dayId) as AppResult.Failure).error)

        val outsideBefore = db.expenseDao().findById(outside)!!
        assertEquals(AppError.Conflict("outside_campaign"), (costs.linkToDay(outside, dayId) as AppResult.Failure).error)
        assertEquals(outsideBefore, db.expenseDao().findById(outside))

        // Linked by the farmer as replacing the calculation: never both.
        ok(costs.linkToDay(unlinked, dayId, DayCostRole.REPLACEMENT))
        assertEquals(dayId, expenses.observe(unlinked).first()!!.harvestId)
        assertEquals(ExpenseStatus.DRAFT, calculated(dayId, ExpenseOrigin.DAY_LABOUR)!!.status)
        assertEquals(30_000L, JornadaCost.of(expenses.observeForHarvest(dayId).first()).postedMinor)
        assertEquals(emptyList<Expense>(), UnlinkedDayCosts.of(dayId, farmId, day, expenses.observeAll().first(), campaignId))
    }

    @Test
    fun cr012FreezesRateAndRoundsEachPerson() = runBlocking {
        ok(costs.saveRates(farmId, RecollectionRates(hourlyMinor = 100)))
        val dayId = ok(harvests.openJornada(farmId, day))
        val crew = listOf("Juan", "Ana").map { ok(labour.addWorker(it)) }
        ok(labour.recordCrew(com.isivoltpro.maginaolivo.domain.labour.CrewDraft(dayId, crew, LabourUnit.HOURS, 1)))
        assertEquals(4L, calculated(dayId, ExpenseOrigin.DAY_LABOUR)!!.amountMinor)
        ok(costs.saveRates(farmId, RecollectionRates(hourlyMinor = 200)))
        assertEquals(4L, calculated(dayId, ExpenseOrigin.DAY_LABOUR)!!.amountMinor)
    }

    @Test
    fun cr012RejectsNewAnonymousCounts() = runBlocking {
        val dayId = ok(harvests.openJornada(farmId, day))
        assertTrue(labour.recordCount(CountDraft(dayId, 3, LabourUnit.FULL_DAY)) is AppResult.Failure)
    }

    private suspend fun named(dayId: UUID, count: Int) {
        val crew = (1..count).map { ok(labour.addWorker("Persona $it")) }
        ok(labour.recordCrew(CrewDraft(dayId, crew, LabourUnit.FULL_DAY)))
    }

    private suspend fun calculated(dayId: UUID, origin: ExpenseOrigin): Expense? =
        expenses.observeForHarvest(dayId).first().firstOrNull { it.origin == origin }

    private fun outboxCount(): Int = db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM sync_outbox").use {
        it.moveToFirst(); it.getInt(0)
    }

    private fun cost(harvestId: UUID, kind: JornadaExpenseKind, amountMinor: Long) = ExpenseDraft(
        expenseDate = day,
        concept = kind.label,
        category = kind.category,
        amountMinor = amountMinor,
        farmId = farmId,
        harvestId = harvestId,
    )

    private fun open() {
        db = MaginaOlivoDatabase.create(context, DB)
        val clock = FixedClock(now)
        val workspaces = object : WorkspaceRepository {
            override suspend fun ensureLocalWorkspace(): AppResult<UUID> = AppResult.Success(workspaceId)
        }
        harvests = OfflineFirstHarvestRepository(db, clock, RandomIds, TestDispatchers) { ZoneOffset.UTC }
        labour = OfflineFirstLabourRepository(db, workspaces, clock, RandomIds, TestDispatchers)
        equipment = OfflineFirstEquipmentRepository(db, clock, RandomIds, TestDispatchers)
        expenses = OfflineFirstExpenseRepository(db, workspaces, clock, RandomIds, TestDispatchers)
        costs = OfflineFirstDayCostRepository(db, clock, RandomIds, TestDispatchers)
    }

    private suspend fun seed() {
        val meta = LocalMetadata(now, now)
        db.workspaceDao().upsert(WorkspaceEntity(workspaceId, "Olivar", UUID.randomUUID(), "ES", "Europe/Madrid", "es-ES", "EUR", meta))
        db.farmDao().upsert(FarmEntity(farmId, workspaceId, "El Cortijo", metadata = meta))
        db.parcelDao().upsert(ParcelEntity(north, workspaceId, "Norte", source = "MANUAL", metadata = meta))
        db.campaignDao().upsert(
            CampaignEntity(campaignId, workspaceId, farmId, "2026/27", LocalDate.parse("2026-10-01"), null, CampaignStatus.ACTIVE, metadata = meta),
        )
        db.campaignDao().upsertSnapshots(
            listOf(CampaignParcelSnapshotEntity(UUID.randomUUID(), workspaceId, campaignId, north, farmId, "El Cortijo", "Norte", metadata = meta)),
        )
    }

    private fun <T> ok(result: AppResult<T>): T = when (result) {
        is AppResult.Success -> result.value
        is AppResult.Failure -> throw AssertionError("Expected success but was ${result.error}")
    }

    private data class FixedClock(val value: Instant) : AppClock {
        override fun nowInstant() = value
        override fun today(zoneId: ZoneId) = LocalDate.ofInstant(value, zoneId)
    }

    private object RandomIds : IdGenerator {
        override fun newId(): UUID = UUID.randomUUID()
    }

    private object TestDispatchers : AppDispatchers {
        override val default: CoroutineDispatcher = Dispatchers.Unconfined
        override val io: CoroutineDispatcher = Dispatchers.Unconfined
        override val main: CoroutineDispatcher = Dispatchers.Unconfined
    }

    private companion object {
        const val DB = "day-cost-contract-test.db"
    }
}
