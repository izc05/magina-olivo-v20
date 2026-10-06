package com.isivoltpro.maginaolivo.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.core.dispatchers.AppDispatchers
import com.isivoltpro.maginaolivo.core.id.IdGenerator
import com.isivoltpro.maginaolivo.core.time.AppClock
import com.isivoltpro.maginaolivo.data.local.entity.LocalMetadata
import com.isivoltpro.maginaolivo.data.local.entity.WorkspaceEntity
import com.isivoltpro.maginaolivo.data.local.model.ActivityStatus
import com.isivoltpro.maginaolivo.data.local.model.CampaignStatus
import com.isivoltpro.maginaolivo.data.repository.OfflineFirstActivityRepository
import com.isivoltpro.maginaolivo.data.repository.OfflineFirstCampaignRepository
import com.isivoltpro.maginaolivo.data.repository.OfflineFirstDeliveryRepository
import com.isivoltpro.maginaolivo.data.repository.OfflineFirstEquipmentRepository
import com.isivoltpro.maginaolivo.data.repository.OfflineFirstExpenseRepository
import com.isivoltpro.maginaolivo.data.repository.OfflineFirstFarmRepository
import com.isivoltpro.maginaolivo.data.repository.OfflineFirstHarvestRepository
import com.isivoltpro.maginaolivo.data.repository.OfflineFirstLabourRepository
import com.isivoltpro.maginaolivo.data.repository.OfflineFirstParcelRepository
import com.isivoltpro.maginaolivo.domain.activity.ActivityDetail
import com.isivoltpro.maginaolivo.domain.activity.ActivityType
import com.isivoltpro.maginaolivo.domain.activity.NewActivity
import com.isivoltpro.maginaolivo.domain.analytics.CampaignComparison
import com.isivoltpro.maginaolivo.domain.analytics.FarmOverview
import com.isivoltpro.maginaolivo.domain.campaign.NewCampaign
import com.isivoltpro.maginaolivo.domain.delivery.DeliveryDraft
import com.isivoltpro.maginaolivo.domain.delivery.DeliveryShareInput
import com.isivoltpro.maginaolivo.domain.delivery.PesadaOrigin
import com.isivoltpro.maginaolivo.domain.equipment.EquipmentDraftLine
import com.isivoltpro.maginaolivo.domain.equipment.EquipmentPriceSnapshot
import com.isivoltpro.maginaolivo.domain.equipment.EquipmentType
import com.isivoltpro.maginaolivo.domain.expense.ExpenseCategory
import com.isivoltpro.maginaolivo.domain.expense.ExpenseDraft
import com.isivoltpro.maginaolivo.domain.expense.ExpenseStatus
import com.isivoltpro.maginaolivo.domain.expense.JornadaExpenseKind
import com.isivoltpro.maginaolivo.domain.farm.NewFarm
import com.isivoltpro.maginaolivo.domain.labour.CrewDraft
import com.isivoltpro.maginaolivo.domain.labour.LabourRateBasis
import com.isivoltpro.maginaolivo.domain.labour.LabourRateSnapshot
import com.isivoltpro.maginaolivo.domain.labour.LabourUnit
import com.isivoltpro.maginaolivo.domain.notebook.CampaignNotebook
import com.isivoltpro.maginaolivo.domain.parcel.NewParcel
import com.isivoltpro.maginaolivo.domain.workspace.WorkspaceRepository
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * #409 «Prueba final: año agrícola completo» — one Farm through a whole olive year, through the
 * same repositories as the app, offline: Finca → Parcela → abonado, tratamiento, riego and their
 * Gastos → Campaña (parcelas, activar) → two días de recolección with Pesadas, priced jornales,
 * machinery and a day cost → cerrar → poda with its jornales and maquinaria → resultados.
 *
 * Every figure is read back from its own ledger: the recollection cost is never mixed with the
 * year's general costs, nothing is counted twice, a closed campaign stays history and all of it
 * survives an app restart.
 */
@RunWith(AndroidJUnit4::class)
class AgriculturalYearContractTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val workspaceId = UUID.fromString("10000000-0000-0000-0000-0000000a9409")
    private val clock = MovableClock(Instant.parse("2026-09-01T08:00:00Z"))

    private lateinit var db: MaginaOlivoDatabase
    private lateinit var farms: OfflineFirstFarmRepository
    private lateinit var parcels: OfflineFirstParcelRepository
    private lateinit var activities: OfflineFirstActivityRepository
    private lateinit var campaigns: OfflineFirstCampaignRepository
    private lateinit var harvests: OfflineFirstHarvestRepository
    private lateinit var deliveries: OfflineFirstDeliveryRepository
    private lateinit var labour: OfflineFirstLabourRepository
    private lateinit var equipment: OfflineFirstEquipmentRepository
    private lateinit var expenses: OfflineFirstExpenseRepository

    @Before
    fun before() = runBlocking {
        context.deleteDatabase(DB)
        open()
        val meta = LocalMetadata(clock.value, clock.value)
        db.workspaceDao().upsert(WorkspaceEntity(workspaceId, "Olivar", UUID.randomUUID(), "ES", "Europe/Madrid", "es-ES", "EUR", meta))
    }

    @After
    fun after() {
        db.close()
        context.deleteDatabase(DB)
    }

    @Test
    fun aWholeOliveYearKeepsEveryRecordWhereTheFarmerExpectsItAndCountsEachEuroOnce() = runBlocking {
        // ---- September: the Farm, its Parcel and the year's work before the harvest.
        at("2026-09-01")
        val farm = ok(farms.create(NewFarm(workspaceId, "El Cortijo", municipality = "Bedmar")))
        val parcel = ok(parcels.create(NewParcel(farm, "Haza Norte", managedAreaM2 = 20_000.0)))

        at("2026-09-05")
        val abonado = work(farm, parcel, ActivityType.FERTILIZATION, "Abonado de fondo",
            ActivityDetail.Fertilization(productName = "NPK 15-15-15", totalQuantity = 400.0, unit = "kg"))
        cost(farm, abonado, "Abono NPK", ExpenseCategory.PRODUCTS, 30_000)

        at("2026-09-12")
        val tratamiento = work(farm, parcel, ActivityType.PHYTOSANITARY, "Tratamiento repilo",
            ActivityDetail.Phytosanitary(productName = "Cobre", reason = "Repilo"))
        cost(farm, tratamiento, "Cobre", ExpenseCategory.PRODUCTS, 12_000)

        at("2026-09-20")
        val riego = work(farm, parcel, ActivityType.IRRIGATION, "Riego por goteo",
            ActivityDetail.Irrigation(durationMinutes = 240, volumeM3 = 80.0, sectorText = "Sector 1"))
        cost(farm, riego, "Agua comunidad de regantes", ExpenseCategory.IRRIGATION, 8_000)

        // ---- October: the Campaign, with its Parcel, activated.
        at("2026-10-01")
        val campaign = ok(campaigns.create(NewCampaign(farm, "2026/27", LocalDate.parse("2026-10-01"), setOf(parcel))))
        ok(campaigns.activate(campaign))

        // ---- November: two días de recolección, each opened by its own Pesada.
        at("2026-11-20")
        val day1 = pesada(farm, parcel, "2026-11-20", 3_000_000, "V-1")
        val crew = listOf("Antonio Ruiz", "Paco Martos", "Mari Cruz").map { ok(labour.addWorker(it)) }
        ok(labour.recordCrew(CrewDraft(day1, crew, LabourUnit.FULL_DAY, appliedRate = jornal("2026-11-20"))))
        ok(equipment.replaceForHarvest(day1, listOf(
            EquipmentDraftLine(EquipmentType.TRACTOR, 1, appliedPrice = EquipmentPriceSnapshot(8_500, "EUR", date("2026-11-20"))),
            EquipmentDraftLine(EquipmentType.SHAKER, 1, appliedPrice = EquipmentPriceSnapshot(6_000, "EUR", date("2026-11-20"))),
        )))
        ok(expenses.create(ExpenseDraft(date("2026-11-20"), JornadaExpenseKind.DIESEL.label, JornadaExpenseKind.DIESEL.category,
            5_000, farmId = farm, harvestId = day1)))

        at("2026-11-27")
        val day2 = pesada(farm, parcel, "2026-11-27", 2_000_000, "V-2")
        ok(labour.recordCrew(CrewDraft(day2, crew.take(2), LabourUnit.FULL_DAY, appliedRate = jornal("2026-11-27"))))
        ok(equipment.replaceForHarvest(day2, listOf(
            EquipmentDraftLine(EquipmentType.TRACTOR, 1, appliedPrice = EquipmentPriceSnapshot(8_500, "EUR", date("2026-11-27"))),
        )))

        // ---- January: the Campaign closes; its days are history from now on.
        at("2027-01-15")
        ok(campaigns.close(campaign, date("2027-01-15")))
        assertTrue("a closed campaign takes no new jornal",
            labour.recordCrew(CrewDraft(day2, crew.drop(2), LabourUnit.FULL_DAY, appliedRate = jornal("2026-11-27"))) is AppResult.Failure)

        // ---- February: pruning after the harvest, with its jornales and machinery as Gastos.
        at("2027-02-10")
        val poda = work(farm, parcel, ActivityType.PRUNING, "Poda de producción", ActivityDetail.Pruning(pruningType = "Producción"))
        cost(farm, poda, "Jornales de poda", ExpenseCategory.LABOR, 40_000)
        cost(farm, poda, "Alquiler motosierras", ExpenseCategory.MACHINERY, 15_000)

        // ---- The phone is switched off and on: nothing lives only in memory.
        db.close()
        open()

        results(farm, parcel, campaign, day1, day2)
    }

    private suspend fun results(farm: UUID, parcel: UUID, campaign: UUID, day1: UUID, day2: UUID) {
        val camp = campaigns.observe(campaign).first()!!
        assertEquals(CampaignStatus.CLOSED, camp.status)
        assertEquals(listOf(parcel), camp.snapshots.map { it.parcelId })

        // The year's work: four done jobs on the Farm, each on its Parcel, none absorbed by the Campaign.
        val works = activities.observeForFarm(farm).first().filter { it.type != ActivityType.HARVEST_DAY }
        assertEquals(setOf(ActivityType.FERTILIZATION, ActivityType.PHYTOSANITARY, ActivityType.IRRIGATION, ActivityType.PRUNING),
            works.map { it.type }.toSet())
        assertTrue(works.all { it.status == ActivityStatus.COMPLETED && it.targets.map { t -> t.parcelId } == listOf(parcel) })

        val all = expenses.observeAll().first()
        val notebook = CampaignNotebook.project(
            camp,
            activities.observeForFarm(farm).first(),
            harvests.observeForCampaign(campaign).first(),
            deliveries.observeForCampaign(campaign).first(),
            all,
            labour.observeForCampaign(campaign).first(),
            equipment.observeForCampaign(campaign).first(),
        )
        assertTrue("general work never appears as campaign work", notebook.works.isEmpty())
        // Two days, each with exactly its Pesada's kilos.
        assertEquals(setOf(day1, day2), notebook.harvests.map { it.id }.toSet())
        assertEquals(5_000_000L, notebook.deliverySummary.deliveredGrams)
        assertEquals(5, notebook.labourSummary.fullDays)
        // Recollection: jornales 300 € + machinery 230 € + diesel 50 € = 580 €, once.
        assertEquals(58_000L, notebook.recollectionByCurrency.single().amount())
        assertEquals(58_000L, notebook.expensesByCurrency.single().amount())
        assertTrue(notebook.costCompleteness.complete)

        // The history: 580 € over 5.000 kg = 0,116 €/kg. #486: a cost per kilo is a ratio and keeps
        // its thousandths; the year test asserts the exact ratio, never a rounding to the cent.
        val year = CampaignComparison.of(listOf(notebook)).single()
        assertEquals(5_000_000L, year.deliveredGrams)
        assertEquals(0, BigDecimal("0.116").compareTo(year.canonicalCost!!.summary!!.costPerKg))
        assertEquals(116L, year.costPerKgMilli)
        assertTrue(year.costComplete)

        // Mi Campo: the same season with the general costs apart, never inside the recollection cost.
        val overview = FarmOverview.of("2026/27", farms.observeActive(workspaceId).first(),
            campaigns.observeForFarm(farm).first(), deliveries.observeAll().first(), all)
        assertEquals(58_000L, overview.costs.single().amountMinor)
        assertEquals(105_000L, overview.generalCosts.single().amountMinor)
        assertEquals(163_000L, overview.totalCosts.single().amountMinor)
        assertEquals(5_000_000L, overview.delivery.deliveredGrams)
        // 1.630 € over 5.000 kg = 0,326 €/kg; recollection 580 € = 0,116 €/kg — #486 exposes the
        // season's ratio in thousandths, so it is asserted exactly, never rounded to the cent.
        assertEquals(0, BigDecimal("0.326").compareTo(ratio(overview.totalCosts.single().amountMinor!!, overview.delivery.deliveredGrams)))
        assertEquals(326L, overview.totalCostPerKgMilli)
        assertEquals(116L, overview.costPerKgMilli)

        // Nothing counted twice: the posted ledger is exactly recollection + general costs.
        assertEquals(163_000L, all.filter { it.status == ExpenseStatus.POSTED }.sumOf { it.amountMinor })
    }

    private suspend fun work(farm: UUID, parcel: UUID, type: ActivityType, description: String, detail: ActivityDetail): UUID =
        ok(activities.create(NewActivity(farmId = farm, type = type, activityDate = clock.today(ZoneOffset.UTC),
            description = description, parcelIds = setOf(parcel), completeImmediately = true, detail = detail)))

    private suspend fun cost(farm: UUID, activity: UUID, concept: String, category: ExpenseCategory, minor: Long) {
        ok(expenses.create(ExpenseDraft(clock.today(ZoneOffset.UTC), concept, category, minor, farmId = farm, activityId = activity)))
    }

    private suspend fun pesada(farm: UUID, parcel: UUID, day: String, grams: Long, ticket: String): UUID {
        val id = ok(deliveries.create(DeliveryDraft(
            farmId = farm, deliveryDate = date(day), destinationOrganizationId = null, destinationName = "Bedmarense",
            netGrams = grams, shares = listOf(DeliveryShareInput(parcel, null)), ticketNumber = ticket,
            deliveryTime = LocalTime.of(12, 0), origin = PesadaOrigin.TREE,
        )))
        return deliveries.observe(id).first()!!.harvestId!!
    }

    /** Euros per kilo from cents and grams, exact (the #486 contract). */
    private fun ratio(cents: Long, grams: Long): BigDecimal =
        BigDecimal.valueOf(cents).movePointLeft(2).multiply(BigDecimal.valueOf(1_000))
            .divide(BigDecimal.valueOf(grams), java.math.MathContext.DECIMAL128)

    private fun jornal(day: String) = LabourRateSnapshot(6_000, "EUR", date(day), LabourRateBasis.DAY)

    private fun date(text: String): LocalDate = LocalDate.parse(text)

    private fun at(day: String) {
        clock.value = LocalDate.parse(day).atTime(18, 0).toInstant(ZoneOffset.UTC)
    }

    private fun open() {
        db = MaginaOlivoDatabase.create(context, DB)
        val workspaces = object : WorkspaceRepository {
            override suspend fun ensureLocalWorkspace(): AppResult<UUID> = AppResult.Success(workspaceId)
        }
        farms = OfflineFirstFarmRepository(db, clock, RandomIds, TestDispatchers)
        parcels = OfflineFirstParcelRepository(db, clock, RandomIds, TestDispatchers)
        activities = OfflineFirstActivityRepository(db, clock, RandomIds, TestDispatchers)
        campaigns = OfflineFirstCampaignRepository(db, clock, RandomIds, TestDispatchers)
        harvests = OfflineFirstHarvestRepository(db, clock, RandomIds, TestDispatchers) { ZoneOffset.UTC }
        deliveries = OfflineFirstDeliveryRepository(db, clock, RandomIds, TestDispatchers) { ZoneOffset.UTC }
        labour = OfflineFirstLabourRepository(db, workspaces, clock, RandomIds, TestDispatchers)
        equipment = OfflineFirstEquipmentRepository(db, clock, RandomIds, TestDispatchers)
        expenses = OfflineFirstExpenseRepository(db, workspaces, clock, RandomIds, TestDispatchers)
    }

    private fun <T> ok(result: AppResult<T>): T = when (result) {
        is AppResult.Success -> result.value
        is AppResult.Failure -> throw AssertionError("Expected success but was ${result.error}")
    }

    private class MovableClock(var value: Instant) : AppClock {
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
        const val DB = "agricultural-year-contract-test.db"
    }
}
