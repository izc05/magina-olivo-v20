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
import com.isivoltpro.maginaolivo.domain.equipment.EquipmentType
import com.isivoltpro.maginaolivo.domain.expense.DayCostRole
import com.isivoltpro.maginaolivo.domain.expense.ExpenseCategory
import com.isivoltpro.maginaolivo.domain.expense.DayCostQuestion
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
import kotlinx.coroutines.async
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
class LabourPaymentContractTest {
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
    fun agreedRatesFreezeAndThreePeoplePost180() = runBlocking {
        ok(costs.saveRates(farmId, RecollectionRates(fullDayMinor = 6_000)))
        val d = ok(harvests.openJornada(farmId, day))
        named(d, 3)
        val rows = labour.observeForHarvest(d).first()
        assertEquals(3, rows.size)
        assertTrue(rows.all { it.appliedRate?.unitPriceMinor == 6_000L })
        assertEquals(18_000L, calculated(d, ExpenseOrigin.DAY_LABOUR)!!.amountMinor)
        ok(costs.saveRates(farmId, RecollectionRates(fullDayMinor = 6_500, currency = "USD")))
        assertEquals(18_000L, calculated(d, ExpenseOrigin.DAY_LABOUR)!!.amountMinor)
        assertEquals("EUR", calculated(d, ExpenseOrigin.DAY_LABOUR)!!.currency)
        ok(labour.update(rows.first().id, LabourChange(1, LabourUnit.FULL_DAY, null)))
        assertEquals(6_000L, labour.observeForHarvest(d).first().first().appliedRate!!.unitPriceMinor)
        val defaultDay = ok(harvests.openJornada(farmId, day.minusDays(2)))
        ok(labour.recordCrew(CrewDraft(defaultDay, listOf(rows.first().workerId!!), LabourUnit.FULL_DAY)))
        assertEquals(6_500L, calculated(defaultDay, ExpenseOrigin.DAY_LABOUR)!!.amountMinor)
        assertEquals("USD", calculated(defaultDay, ExpenseOrigin.DAY_LABOUR)!!.currency)
        val d2 = ok(harvests.openJornada(farmId, day.minusDays(1)))
        ok(labour.recordCrew(CrewDraft(d2, listOf(rows.first().workerId!!), LabourUnit.FULL_DAY,
            appliedRate = rate(7_000))))
        assertEquals(7_000L, calculated(d2, ExpenseOrigin.DAY_LABOUR)!!.amountMinor)
    }

    @Test
    fun partialInitialAndLaterPaymentsSurviveReopenAndSettleAfterCloseWithoutChangingCost() = runBlocking {
        val worker = ok(labour.addWorker("Juan"))
        ok(costs.saveRates(farmId, RecollectionRates(fullDayMinor = 6_000)))
        repeat(3) { index ->
            val d = ok(harvests.openJornada(farmId, day.minusDays(index.toLong() + 1)))
            ok(labour.recordCrew(CrewDraft(d, listOf(worker), LabourUnit.FULL_DAY)))
        }
        val initial = payment(worker, 10_000)
        val d = ok(harvests.openJornada(farmId, day))
        ok(labour.recordCrew(CrewDraft(d, listOf(worker), LabourUnit.FULL_DAY, initialPayments = listOf(initial))))
        val later = payment(worker, 8_000)
        ok(labour.recordPayment(later))
        assertEquals(24_000L, balance(worker).generatedMinor)
        assertEquals(18_000L, balance(worker).paidMinor)
        assertEquals(6_000L, balance(worker).pendingMinor)
        assertEquals(LabourPaymentState.PARTIAL, balance(worker).state)
        val deliveries = com.isivoltpro.maginaolivo.data.repository.OfflineFirstDeliveryRepository(db, FixedClock(now), RandomIds, TestDispatchers)
        ok(deliveries.create(com.isivoltpro.maginaolivo.domain.delivery.DeliveryDraft(farmId, day, null, "Cooperativa", 3_200_000,
            listOf(com.isivoltpro.maginaolivo.domain.delivery.DeliveryShareInput(north, 3_200_000)))))
        val weighedBefore = deliveries.observeForCampaign(campaignId).first()
        val ratioBefore = com.isivoltpro.maginaolivo.domain.expense.RecollectionCostSummary.of(campaignId,
            expenses.observeAll().first(), weighedBefore, "EUR").costPerKg
        assertEquals(0, java.math.BigDecimal("0.075").compareTo(ratioBefore))
        val campaign = db.campaignDao().findById(campaignId)!!.copy(status = CampaignStatus.CLOSED)
        db.campaignDao().upsert(campaign)
        val expenseBefore = expenses.observeAll().first()
        val outboxBefore = outboxExceptPayments()
        val final = payment(worker, 6_000)
        ok(labour.recordPayment(final))
        assertEquals(expenseBefore, expenses.observeAll().first())
        assertEquals(outboxBefore, outboxExceptPayments())
        assertEquals(campaign, db.campaignDao().findById(campaignId))
        assertEquals(weighedBefore.map { it.id to it.netGrams }, deliveries.observeForCampaign(campaignId).first().map { it.id to it.netGrams })
        assertEquals(ratioBefore, com.isivoltpro.maginaolivo.domain.expense.RecollectionCostSummary.of(campaignId,
            expenses.observeAll().first(), deliveries.observeForCampaign(campaignId).first(), "EUR").costPerKg)
        db.close(); open()
        assertEquals(setOf(initial.id, later.id, final.id), labour.observePayments(campaignId).first().map { it.id }.toSet())
        assertEquals(0L, balance(worker).pendingMinor)
        assertEquals(LabourPaymentState.PAID, balance(worker).state)
        assertEquals(expenseBefore, expenses.observeAll().first())
    }

    @Test
    fun aFuturePaymentIsRejectedWithoutChangingBalanceOrOutbox() = runBlocking {
        val (_, worker) = pricedDay()
        val before = dumpFinancialState()
        val result = labour.recordPayment(payment(worker, 1_000).copy(paymentDate = day.plusDays(1)))
        assertEquals(AppResult.Failure(AppError.Validation("paymentDate", "future")), result)
        assertEquals(before, dumpFinancialState())
        assertEquals(0L, balance(worker).paidMinor)
        assertEquals(6_000L, balance(worker).pendingMinor)
    }

    @Test
    fun concurrentPaymentsAndUuidRetriesCannotOverpayOrResurrect() = runBlocking {
        val (d, worker) = pricedDay()
        val attempts = kotlinx.coroutines.coroutineScope {
            listOf(payment(worker, 4_000), payment(worker, 4_000)).map { p ->
                async(kotlinx.coroutines.Dispatchers.IO) { labour.recordPayment(p) }
            }.map { it.await() }
        }
        assertEquals(1, attempts.count { it is AppResult.Success })
        assertEquals(1, attempts.count { it is AppResult.Failure })
        val saved = labour.observePayments(campaignId).first().single()
        val intents = tableCount("sync_outbox")
        assertEquals(saved.id, ok(labour.recordPayment(saved)))
        assertEquals(intents, tableCount("sync_outbox"))
        assertTrue(labour.recordPayment(saved.copy(note = "different")) is AppResult.Failure)
        assertTrue(labour.recordPayment(payment(worker, 2_001)) is AppResult.Failure)
        for (amount in listOf(0L, -1L)) assertTrue(labour.recordPayment(payment(worker, amount)) is AppResult.Failure)
        assertTrue(labour.recordPayment(payment(worker, 1).copy(currency = "USD")) is AppResult.Failure)
        assertTrue(labour.recordPayment(payment(worker, 1).copy(campaignId = UUID.randomUUID())) is AppResult.Failure)
        assertTrue(labour.recordPayment(payment(UUID.randomUUID(), 1)) is AppResult.Failure)
        ok(labour.removePayment(saved.id))
        assertTrue(db.labourPaymentDao().find(saved.id)!!.metadata.deletedAt != null)
        assertTrue(labour.recordPayment(saved) is AppResult.Failure)
        assertEquals(6_000L, balance(worker).pendingMinor)
        assertEquals(6_000L, calculated(d, ExpenseOrigin.DAY_LABOUR)!!.amountMinor)
    }

    /** #475 (owner audit on #583): the Gasto editor asks only what the day really allows. */
    @Test
    fun theDayCostQuestionFollowsTheDayAsItIs() = runBlocking {
        val (d, worker) = pricedDay()
        // A calculation of jornales exists and nobody is paid yet: both answers.
        assertEquals(DayCostQuestion(DayCostKind.LABOUR, canReplace = true), costs.questionFor(d, ExpenseCategory.LABOR))
        // No machinery calculated that day, and products never replace anything: nothing to ask.
        assertNull(costs.questionFor(d, ExpenseCategory.MACHINERY))
        assertNull(costs.questionFor(d, ExpenseCategory.PRODUCTS))
        // With a payment recorded per person, jornales can only add.
        ok(labour.recordPayment(payment(worker, 1_000)))
        assertEquals(DayCostQuestion(DayCostKind.LABOUR, canReplace = false), costs.questionFor(d, ExpenseCategory.LABOR))
    }

    @Test
    fun reductionsHarvestRemovalAndManualCollisionsRollbackRowsAndOutboxUntilPaymentCorrected() = runBlocking {
        val (d, worker) = pricedDay()
        val line = labour.observeForHarvest(d).first().single()
        val p = payment(worker, 5_000)
        ok(labour.recordPayment(p))
        val before = dumpFinancialState()
        assertTrue(labour.update(line.id, LabourChange(1, LabourUnit.FULL_DAY, null, rate(4_000))) is AppResult.Failure)
        assertEquals(before, dumpFinancialState())
        assertTrue(labour.remove(line.id) is AppResult.Failure)
        assertEquals(before, dumpFinancialState())
        assertTrue(harvests.delete(d) is AppResult.Failure)
        assertEquals(before, dumpFinancialState())
        // #475: with payments per person, jornales can never replace the calculation.
        assertEquals(AppError.Validation("dayCostRole", "labour_paid"),
            (expenses.create(cost(d, JornadaExpenseKind.LABOUR, 6_000).copy(dayCostRole = DayCostRole.REPLACEMENT)) as AppResult.Failure).error)
        assertEquals(before, dumpFinancialState())
        val generated = calculated(d, ExpenseOrigin.DAY_LABOUR)!!
        assertTrue(expenses.delete(generated.id) is AppResult.Failure)
        assertTrue(expenses.update(generated.id, cost(d, JornadaExpenseKind.LABOUR, 4_000)) is AppResult.Failure)
        assertEquals(before, dumpFinancialState())
        val manual = ok(expenses.create(cost(d, JornadaExpenseKind.LABOUR, 6_000).copy(harvestId = null, campaignId = campaignId)))
        val beforeLink = dumpFinancialState()
        assertEquals(AppError.Validation("dayCostRole", "labour_paid"),
            (costs.linkToDay(manual, d, DayCostRole.REPLACEMENT) as AppResult.Failure).error)
        assertEquals(beforeLink, dumpFinancialState())
        assertTrue(expenses.update(manual, cost(d, JornadaExpenseKind.LABOUR, 6_000).copy(dayCostRole = DayCostRole.REPLACEMENT)) is AppResult.Failure)
        assertEquals(beforeLink, dumpFinancialState())
        // «Se añade» is always possible: the calculation, its allocation and the payment stay as they were.
        val added = ok(expenses.create(cost(d, JornadaExpenseKind.LABOUR, 1_500)))
        assertEquals(ExpenseStatus.POSTED, calculated(d, ExpenseOrigin.DAY_LABOUR)!!.status)
        assertEquals(generated.amountMinor, calculated(d, ExpenseOrigin.DAY_LABOUR)!!.amountMinor)
        ok(expenses.delete(added))
        ok(labour.removePayment(p.id))
        ok(labour.update(line.id, LabourChange(1, LabourUnit.FULL_DAY, null, rate(4_000))))
        assertEquals(4_000L, calculated(d, ExpenseOrigin.DAY_LABOUR)!!.amountMinor)
        ok(harvests.delete(d))
        assertTrue(db.labourPaymentDao().find(p.id)!!.metadata.deletedAt != null)
    }

    @Test
    fun unknownLegacyStaysUnknownUntilExplicitConfirmationAndZeroIsKnown() = runBlocking {
        val d = ok(harvests.openJornada(farmId, day))
        val legacy = UUID.randomUUID()
        db.labourDao().upsertLabour(listOf(HarvestLabourEntity(legacy, workspaceId, d,
            quantity = 5, unit = LabourUnit.HALF_DAY.name, metadata = LocalMetadata(now, now))))
        val worker = ok(labour.addWorker("Juan"))
        ok(costs.saveRates(farmId, RecollectionRates(fullDayMinor = 6_000)))
        assertNull(labour.observeForHarvest(d).first().single().appliedRate)
        assertNull(calculated(d, ExpenseOrigin.DAY_LABOUR))
        // #449: a priced person may join a day whose legacy line is still unknown; that line stays
        // unknown and nothing is calculated until every price on the day is confirmed.
        val rosa = ok(labour.addWorker("Rosa"))
        ok(labour.recordCrew(CrewDraft(d, listOf(rosa), LabourUnit.FULL_DAY)))
        assertNull(labour.observeForHarvest(d).first().single { it.id == legacy }.appliedRate)
        assertNull(calculated(d, ExpenseOrigin.DAY_LABOUR))
        ok(labour.update(legacy, LabourChange(5, LabourUnit.HALF_DAY, null, rate(6_000))))
        assertEquals(21_000L, calculated(d, ExpenseOrigin.DAY_LABOUR)!!.amountMinor)
        assertTrue(labour.recordPayment(payment(worker, 1)) is AppResult.Failure)
        ok(labour.recordCrew(CrewDraft(d, listOf(worker), LabourUnit.FULL_DAY, appliedRate = rate(0))))
        assertEquals(0L, balance(worker).generatedMinor)
        val zeroDay = ok(harvests.openJornada(farmId, day.minusDays(1)))
        ok(labour.recordCrew(CrewDraft(zeroDay, listOf(worker), LabourUnit.FULL_DAY, appliedRate = rate(0))))
        assertEquals(0L, calculated(zeroDay, ExpenseOrigin.DAY_LABOUR)!!.amountMinor)
    }

    /** #490: Media jornada is recorded today on the full-day rate, costs half and pays only that half. */
    @Test
    fun aNewHalfDayCostsHalfTheAgreedDayAndPaysOnlyThatHalf() = runBlocking {
        val d = ok(harvests.openJornada(farmId, day))
        val ana = ok(labour.addWorker("Ana"))
        ok(labour.recordCrew(CrewDraft(d, listOf(ana), LabourUnit.HALF_DAY, appliedRate = rate(7_001),
            initialPayments = listOf(payment(ana, 3_501)))))
        val line = labour.observeForHarvest(d).first().single()
        assertEquals(LabourUnit.HALF_DAY, line.unit)
        assertEquals(7_001L, line.appliedRate!!.unitPriceMinor)
        assertEquals(LabourRateBasis.DAY, line.appliedRate!!.basis)
        assertEquals(3_501L, calculated(d, ExpenseOrigin.DAY_LABOUR)!!.amountMinor)
        assertEquals(3_501L, balance(ana).generatedMinor)
        assertEquals(LabourPaymentState.PAID, balance(ana).state)
        // Completa <-> Media corrects the same line: one DAY_LABOUR, recalculated each time.
        ok(labour.update(line.id, LabourChange(1, LabourUnit.FULL_DAY, null)))
        assertEquals(7_001L, calculated(d, ExpenseOrigin.DAY_LABOUR)!!.amountMinor)
        ok(labour.update(line.id, LabourChange(1, LabourUnit.HALF_DAY, null)))
        assertEquals(3_501L, calculated(d, ExpenseOrigin.DAY_LABOUR)!!.amountMinor)
        assertEquals(1, expenses.observeForHarvest(d).first().count { it.origin == ExpenseOrigin.DAY_LABOUR })
        db.close(); open()
        assertEquals(LabourUnit.HALF_DAY, labour.observeForHarvest(d).first().single().unit)
        assertEquals(3_501L, balance(ana).generatedMinor)
    }

    @Test
    fun initialPaymentFailureRollsBackCrewExpenseAndIntents() = runBlocking {
        val d = ok(harvests.openJornada(farmId, day))
        val worker = ok(labour.addWorker("Juan"))
        val before = dumpFinancialState()
        assertTrue(labour.recordCrew(CrewDraft(d, listOf(worker), LabourUnit.FULL_DAY, appliedRate = rate(6_000),
            initialPayments = listOf(payment(worker, 6_001)))) is AppResult.Failure)
        assertEquals(before, dumpFinancialState())
        assertTrue(labour.recordCrew(CrewDraft(d, listOf(worker), LabourUnit.FULL_DAY,
            initialPayments = listOf(payment(worker, 1)))) is AppResult.Failure)
        assertEquals(before, dumpFinancialState())
    }

    @Test
    fun invalidCampaignAndForeignContextCannotReceivePayments() = runBlocking {
        val (_, worker) = pricedDay()
        val entity = db.labourDao().findWorker(worker)!!
        val campaign = db.campaignDao().findById(campaignId)!!
        db.campaignDao().upsert(campaign.copy(metadata = campaign.metadata.copy(deletedAt = now)))
        assertTrue(labour.recordPayment(payment(worker, 1)) is AppResult.Failure)
        db.campaignDao().upsert(campaign.copy(status = CampaignStatus.PREPARATION))
        assertTrue(labour.recordPayment(payment(worker, 1)) is AppResult.Failure)
        db.campaignDao().upsert(campaign)
        val otherWorkspace = UUID.randomUUID()
        db.workspaceDao().upsert(WorkspaceEntity(otherWorkspace, "Other", UUID.randomUUID(), "ES", "Europe/Madrid", "es-ES", "EUR", LocalMetadata(now, now)))
        db.labourDao().upsertWorker(entity.copy(workspaceId = otherWorkspace))
        assertTrue(labour.recordPayment(payment(worker, 1)) is AppResult.Failure)
    }

    @Test
    fun archivedWorkerWithoutDebtStillCannotInventAPayment() = runBlocking {
        val worker = ok(labour.addWorker("Sin deuda"))
        val entity = db.labourDao().findWorker(worker)!!
        db.labourDao().upsertWorker(entity.copy(metadata = entity.metadata.copy(deletedAt = now)))
        assertTrue(labour.recordPayment(payment(worker, 1)) is AppResult.Failure)
    }

    /** #449: a person whose price is still unknown is recorded beside priced ones, never as 0 €. */
    @Test
    fun aPersonWithoutPriceJoinsAPricedDayAndCountsOnceConfirmed() = runBlocking {
        val (d, juan) = pricedDay()
        // Juan was already paid in full: adding an unknown price must not erase his debt (Codex #607).
        ok(labour.recordPayment(payment(juan, 6_000)))
        // A usual rate exists, yet «Precio aún sin saber» never fills it in silently.
        ok(costs.saveRates(farmId, RecollectionRates(fullDayMinor = 9_000)))
        val miguel = ok(labour.addWorker("Miguel"))
        ok(labour.recordCrew(CrewDraft(d, listOf(miguel), LabourUnit.FULL_DAY, priceUnknown = true)))
        assertEquals(0L, balance(juan).pendingMinor)
        assertEquals(6_000L, balance(juan).generatedMinor)
        val lines = labour.observeForHarvest(d).first()
        assertEquals(2, lines.size)
        assertNull(lines.single { it.workerId == miguel }.appliedRate)
        // The posted calculation stays the confirmed subtotal; Miguel adds nothing until priced.
        assertEquals(6_000L, calculated(d, ExpenseOrigin.DAY_LABOUR)!!.amountMinor)
        assertEquals(ExpenseStatus.POSTED, calculated(d, ExpenseOrigin.DAY_LABOUR)!!.status)

        // Adding priced money while a price is still missing would make the posted amount stale.
        val pedro = ok(labour.addWorker("Pedro"))
        assertEquals(AppError.Validation("appliedRate", "confirm_missing_prices"),
            (labour.recordCrew(CrewDraft(d, listOf(pedro), LabourUnit.FULL_DAY, appliedRate = rate(5_000))) as AppResult.Failure).error)

        // Confirming Miguel's price recalculates the day once.
        ok(labour.update(lines.single { it.workerId == miguel }.id, LabourChange(1, LabourUnit.FULL_DAY, null, rate(5_000))))
        assertEquals(11_000L, calculated(d, ExpenseOrigin.DAY_LABOUR)!!.amountMinor)
        assertEquals(1, expenses.observeForHarvest(d).first().count { it.origin == ExpenseOrigin.DAY_LABOUR })
    }

    @Test
    fun incompleteLegacyDayPreservesItsLedgerThroughPreferencesAndExplicitPriceConfirmation() = runBlocking {
        val d = ok(harvests.openJornada(farmId, day))
        val workers = listOf("Juan", "Ana").map { ok(labour.addWorker(it)) }
        val legacy = workers.map { worker -> HarvestLabourEntity(UUID.randomUUID(), workspaceId, d,
            workerId = worker, workerName = "Legacy", quantity = 1, unit = LabourUnit.FULL_DAY.name, metadata = LocalMetadata(now, now)) }
        db.labourDao().upsertLabour(legacy)
        val oldCost = com.isivoltpro.maginaolivo.data.local.entity.ExpenseEntity(UUID.randomUUID(), workspaceId,
            campaignId = campaignId, farmId = farmId, harvestId = d, expenseDate = day, concept = "Jornales",
            category = "LABOR", amountMinor = 11_000, currency = "EUR", status = "POSTED", origin = "DAY_LABOUR", metadata = LocalMetadata(now, now))
        db.expenseDao().upsert(oldCost)
        ok(costs.saveRates(farmId, RecollectionRates(fullDayMinor = 9_000)))
        assertEquals(oldCost, db.expenseDao().findById(oldCost.id))
        assertEquals(0L, balance(workers.first()).generatedMinor)
        val beforeCollision = dumpFinancialState()
        assertTrue(expenses.create(cost(d, JornadaExpenseKind.LABOUR, 10_000).copy(dayCostRole = DayCostRole.REPLACEMENT)) is AppResult.Failure)
        assertEquals(beforeCollision, dumpFinancialState())
        assertTrue(costs.preferCalculated(d, DayCostKind.LABOUR) is AppResult.Failure)
        assertEquals(beforeCollision, dumpFinancialState())
        ok(labour.update(legacy.first().id, LabourChange(1, LabourUnit.FULL_DAY, null, rate(6_000))))
        assertEquals(oldCost, db.expenseDao().findById(oldCost.id))
        assertTrue(labour.recordPayment(payment(workers.first(), 1)) is AppResult.Failure)
        ok(labour.update(legacy.last().id, LabourChange(1, LabourUnit.FULL_DAY, null, rate(6_000))))
        assertEquals(12_000L, calculated(d, ExpenseOrigin.DAY_LABOUR)!!.amountMinor)
        assertEquals(oldCost.id, calculated(d, ExpenseOrigin.DAY_LABOUR)!!.id)
        assertEquals(6_000L, balance(workers.first()).generatedMinor)
    }

    @Test
    fun legacyJpyLedgerRejectsEurConfirmationWithoutChangingLabourExpenseOrOutbox() = runBlocking {
        val d = ok(harvests.openJornada(farmId, day))
        val worker = ok(labour.addWorker("Juan"))
        val row = HarvestLabourEntity(UUID.randomUUID(), workspaceId, d, workerId = worker,
            workerName = "Juan", quantity = 1, unit = LabourUnit.FULL_DAY.name,
            metadata = LocalMetadata(now, now))
        db.labourDao().upsertLabour(listOf(row))
        val oldCost = com.isivoltpro.maginaolivo.data.local.entity.ExpenseEntity(UUID.randomUUID(), workspaceId,
            campaignId = campaignId, farmId = farmId, harvestId = d, expenseDate = day, concept = "Jornales",
            category = "LABOR", amountMinor = 1_000, currency = "JPY", status = "POSTED",
            origin = "DAY_LABOUR", metadata = LocalMetadata(now, now))
        db.expenseDao().upsert(oldCost)
        ok(costs.saveRates(farmId, RecollectionRates(fullDayMinor = 6_000, currency = "EUR")))
        val before = dumpFinancialState()
        val rejected = labour.update(row.id, LabourChange(1, LabourUnit.FULL_DAY, null, rate(1_000)))
        assertEquals(AppError.Validation("currency", "currency_mismatch"), (rejected as AppResult.Failure).error)
        assertEquals(before, dumpFinancialState())
        assertNull(labour.observeForHarvest(d).first().single().appliedRate)
        assertEquals(oldCost, db.expenseDao().findById(oldCost.id))

        ok(labour.update(row.id, LabourChange(1, LabourUnit.FULL_DAY, null,
            LabourRateSnapshot(1_000, "JPY", day, LabourRateBasis.DAY))))
        assertEquals(oldCost.id, calculated(d, ExpenseOrigin.DAY_LABOUR)!!.id)
        assertEquals("JPY", calculated(d, ExpenseOrigin.DAY_LABOUR)!!.currency)
        assertEquals(1_000L, calculated(d, ExpenseOrigin.DAY_LABOUR)!!.amountMinor)
    }

    @Test
    fun confirmedJpySnapshotCannotBeReplacedWithEurWhenLedgerIsMissing() = runBlocking {
        val d = ok(harvests.openJornada(farmId, day))
        val worker = ok(labour.addWorker("Ana"))
        val row = HarvestLabourEntity(UUID.randomUUID(), workspaceId, d, workerId = worker,
            workerName = "Ana", quantity = 1, unit = LabourUnit.FULL_DAY.name,
            appliedPriceMinor = 1_000, appliedCurrency = "JPY", appliedPriceDate = day,
            appliedBasis = LabourRateBasis.DAY.name, metadata = LocalMetadata(now, now))
        db.labourDao().upsertLabour(listOf(row))
        val before = dumpFinancialState()
        val rejected = labour.update(row.id, LabourChange(1, LabourUnit.FULL_DAY, null, rate(1_000)))
        assertEquals(AppError.Validation("currency", "currency_mismatch"), (rejected as AppResult.Failure).error)
        assertEquals(before, dumpFinancialState())
        assertEquals("JPY", labour.observeForHarvest(d).first().single().appliedRate?.currency)
    }

    @Test
    fun hoursRequireExplicitCompatibleRateAndCurrenciesNeverMixInSingleDayLedger() = runBlocking {
        val (d, worker) = pricedDay()
        val row = labour.observeForHarvest(d).first().single()
        val before = dumpFinancialState()
        assertTrue(labour.update(row.id, LabourChange(1, LabourUnit.HOURS, 180)) is AppResult.Failure)
        assertEquals(before, dumpFinancialState())
        ok(labour.update(row.id, LabourChange(1, LabourUnit.HOURS, 180, LabourRateSnapshot(1_000, "EUR", day, LabourRateBasis.HOUR))))
        assertEquals(3_000L, balance(worker).generatedMinor)
        val other = ok(labour.addWorker("Ana"))
        val beforeCurrency = dumpFinancialState()
        assertTrue(labour.recordCrew(CrewDraft(d, listOf(other), LabourUnit.FULL_DAY, appliedRate = rate(6_000).copy(currency = "USD"))) is AppResult.Failure)
        assertEquals(beforeCurrency, dumpFinancialState())
    }

    @Test
    fun postingManualCollisionRollsBackAndConfirmedCalculationRemainsPayable() = runBlocking {
        val (d, worker) = pricedDay()
        val generated = db.expenseDao().listForHarvest(d).single()
        val draft = generated.copy(id = UUID.randomUUID(), origin = "DAY_REPLACEMENT", status = "DRAFT")
        db.expenseDao().upsert(draft)
        ok(labour.recordPayment(payment(worker, 1_000)))
        val before = dumpFinancialState()
        assertTrue(expenses.post(draft.id) is AppResult.Failure)
        assertEquals(before, dumpFinancialState())
        ok(costs.preferCalculated(d, DayCostKind.LABOUR))
        assertEquals(6_000L, balance(worker).generatedMinor)
        assertEquals(1_000L, balance(worker).paidMinor)
        assertEquals(ExpenseStatus.DRAFT, expenses.observe(draft.id).first()!!.status)
    }

    /** #481: an archived person with jornales owed can still be paid, never beyond what is owed. */
    @Test
    fun anArchivedPersonWithDebtCanStillBePaid() = runBlocking {
        val (d, worker) = pricedDay()
        val owed = balance(worker).pendingMinor
        assertTrue(owed > 0)
        val entity = db.labourDao().findWorker(worker)!!
        db.labourDao().upsertWorker(entity.copy(metadata = entity.metadata.copy(deletedAt = now)))

        val first = payment(worker, owed - 100)
        ok(labour.recordPayment(first))
        assertEquals(100L, balance(worker).pendingMinor)
        // Archived does not open the door to inventing payments.
        assertTrue(labour.recordPayment(payment(worker, 101)) is AppResult.Failure)
        ok(labour.recordPayment(payment(worker, 100)))
        assertEquals(0L, balance(worker).pendingMinor)
        // A payment of an archived person can still be corrected.
        ok(labour.removePayment(first.id))
        assertEquals(owed - 100, balance(worker).pendingMinor)
        // And they are not offered again for new jornales.
        val otherDay = ok(harvests.openJornada(farmId, day.minusDays(1)))
        assertTrue(labour.recordCrew(CrewDraft(otherDay, listOf(worker), LabourUnit.FULL_DAY, appliedRate = rate(6_000))) is AppResult.Failure)
        assertEquals(d, labour.observeForCampaign(campaignId).first().single().harvestId)
    }

    private fun rate(amount: Long) = LabourRateSnapshot(amount, "EUR", day, LabourRateBasis.DAY)
    private fun payment(worker: UUID, amount: Long) = LabourPayment(UUID.randomUUID(), worker, campaignId, day, amount, "EUR")
    private suspend fun pricedDay(): Pair<UUID, UUID> {
        val d = ok(harvests.openJornada(farmId, day))
        val worker = ok(labour.addWorker("Juan"))
        ok(labour.recordCrew(CrewDraft(d, listOf(worker), LabourUnit.FULL_DAY, appliedRate = rate(6_000))))
        return d to worker
    }
    private suspend fun balance(worker: UUID): LabourSettlement {
        val lines = labour.observeForCampaign(campaignId).first()
        val allocated = expenses.observeAll().first().flatMap { LabourLedgerAllocation.of(it, lines).orEmpty() }
        return LabourSettlement.of(worker, campaignId, "EUR", allocated, labour.observePayments(campaignId).first())
    }
    private fun tableCount(table: String): Int = db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use { it.moveToFirst(); it.getInt(0) }
    private fun dumpFinancialState(): List<String> = listOf("harvests", "harvest_labour", "expenses", "labour_payments", "sync_outbox").flatMap { table ->
        db.openHelper.readableDatabase.query("SELECT * FROM $table ORDER BY id").use { cursor ->
            buildList { while (cursor.moveToNext()) add((0 until cursor.columnCount).joinToString("|") { cursor.getString(it) ?: "NULL" }) }
        }
    }
    private fun outboxExceptPayments(): List<String> = db.openHelper.readableDatabase.query("SELECT * FROM sync_outbox WHERE entity_type != 'LABOUR_PAYMENT' ORDER BY id").use { cursor ->
        buildList { while (cursor.moveToNext()) add((0 until cursor.columnCount).joinToString("|") { cursor.getString(it) ?: "NULL" }) }
    }

    private suspend fun named(dayId: UUID, count: Int) {
        val crew = (1..count).map { ok(labour.addWorker("Persona $it")) }
        ok(labour.recordCrew(CrewDraft(dayId, crew, LabourUnit.FULL_DAY)))
    }

    private suspend fun calculated(dayId: UUID, origin: ExpenseOrigin): Expense? =
        expenses.observeForHarvest(dayId).first().firstOrNull { it.origin == origin }

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
        const val DB = "labour-payment-contract-test.db"
    }
}
