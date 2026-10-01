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
        val manual = ok(expenses.create(cost(dayId, JornadaExpenseKind.LABOUR, 30_000)))
        named(dayId, 5)

        // Never both: the hand-typed 300 € counts; the calculated 350 € is kept as a draft.
        assertEquals(ExpenseStatus.DRAFT, calculated(dayId, ExpenseOrigin.DAY_LABOUR)!!.status)
        assertEquals(30_000L, JornadaCost.of(expenses.observeForHarvest(dayId).first()).postedMinor)

        ok(costs.preferCalculated(dayId, DayCostKind.LABOUR))
        assertEquals(ExpenseStatus.POSTED, calculated(dayId, ExpenseOrigin.DAY_LABOUR)!!.status)
        // The hand-typed cost is kept, as a draft, never deleted or merged.
        assertEquals(ExpenseStatus.DRAFT, expenses.observe(manual).first()!!.status)
        assertEquals(35_000L, JornadaCost.of(expenses.observeForHarvest(dayId).first()).postedMinor)
    }

    @Test
    fun machineryWithoutAPriceCountsNoMoneyAndSaysSo() = runBlocking {
        val dayId = ok(harvests.openJornada(farmId, day))
        ok(equipment.replaceForHarvest(dayId, listOf(EquipmentDraftLine(EquipmentType.SHAKER, 2), EquipmentDraftLine(EquipmentType.TRACTOR, 1))))
        // No prices yet: nothing is posted (never a fabricated 0 € or estimate).
        assertNull(calculated(dayId, ExpenseOrigin.DAY_EQUIPMENT))

        ok(costs.saveRates(farmId, RecollectionRates(equipmentDayMinor = mapOf(EquipmentType.SHAKER to 3_500))))
        val entry = calculated(dayId, ExpenseOrigin.DAY_EQUIPMENT)!!
        assertEquals(7_000L, entry.amountMinor)
        assertTrue(entry.notes!!.contains("1 tractor sin precio"))

        // No equipment left: the calculated entry goes.
        ok(equipment.replaceForHarvest(dayId, emptyList()))
        assertNull(calculated(dayId, ExpenseOrigin.DAY_EQUIPMENT))
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

        // A hand-typed machinery rental of the day does stand for the calculation.
        ok(expenses.create(cost(dayId, JornadaExpenseKind.RENTAL, 4_000)))
        assertEquals(ExpenseStatus.DRAFT, calculated(dayId, ExpenseOrigin.DAY_EQUIPMENT)!!.status)
        assertEquals(6_000L, JornadaCost.of(expenses.observeForHarvest(dayId).first()).postedMinor)
    }

    @Test
    fun aClosedCampaignRefusesToSwapWhichCostCounts() = runBlocking {
        ok(costs.saveRates(farmId, RecollectionRates(fullDayMinor = 7_000)))
        val dayId = ok(harvests.openJornada(farmId, day))
        val manual = ok(expenses.create(cost(dayId, JornadaExpenseKind.LABOUR, 30_000)))
        named(dayId, 5)
        val campaign = db.campaignDao().findById(campaignId)!!
        db.campaignDao().upsert(campaign.copy(status = CampaignStatus.CLOSED))

        val refused = costs.preferCalculated(dayId, DayCostKind.LABOUR)
        assertEquals(AppError.Conflict("campaign_closed"), (refused as AppResult.Failure).error)
        // History unchanged: the hand-typed cost still counts, the calculation stays a draft.
        assertEquals(ExpenseStatus.POSTED, expenses.observe(manual).first()!!.status)
        assertEquals(ExpenseStatus.DRAFT, calculated(dayId, ExpenseOrigin.DAY_LABOUR)!!.status)
        assertEquals(30_000L, JornadaCost.of(expenses.observeForHarvest(dayId).first()).postedMinor)
    }

    @Test
    fun anUnlinkedHandTypedCostOfTheSameDateIsShownAndOnlyTheFarmerLinksIt() = runBlocking {
        ok(costs.saveRates(farmId, RecollectionRates(fullDayMinor = 7_000)))
        val dayId = ok(harvests.openJornada(farmId, day))
        named(dayId, 5)
        val unlinked = ok(expenses.create(cost(dayId, JornadaExpenseKind.LABOUR, 30_000).copy(harvestId = null)))
        val otherDate = ok(expenses.create(cost(dayId, JornadaExpenseKind.LABOUR, 9_000).copy(harvestId = null, expenseDate = day.plusDays(1))))

        // Ambiguous: listed for the day, never merged or dropped; the calculation still counts.
        val listed = UnlinkedDayCosts.of(dayId, farmId, day, expenses.observeAll().first())
        assertEquals(listOf(unlinked), listed.map { it.id })
        assertEquals(ExpenseStatus.POSTED, calculated(dayId, ExpenseOrigin.DAY_LABOUR)!!.status)

        // Another date is not this day's.
        assertEquals(AppError.Validation("expense", "other_day"), (costs.linkToDay(otherDate, dayId) as AppResult.Failure).error)

        // Linked by the farmer: the collision rule applies, never both.
        ok(costs.linkToDay(unlinked, dayId))
        assertEquals(dayId, expenses.observe(unlinked).first()!!.harvestId)
        assertEquals(ExpenseStatus.DRAFT, calculated(dayId, ExpenseOrigin.DAY_LABOUR)!!.status)
        assertEquals(30_000L, JornadaCost.of(expenses.observeForHarvest(dayId).first()).postedMinor)
        assertEquals(emptyList<Expense>(), UnlinkedDayCosts.of(dayId, farmId, day, expenses.observeAll().first()))
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
