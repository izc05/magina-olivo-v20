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
import com.isivoltpro.maginaolivo.data.repository.OfflineFirstDeliveryRepository
import com.isivoltpro.maginaolivo.data.repository.OfflineFirstHarvestRepository
import com.isivoltpro.maginaolivo.domain.delivery.DeliveryDraft
import com.isivoltpro.maginaolivo.domain.delivery.DeliveryShareInput
import com.isivoltpro.maginaolivo.domain.delivery.DeliverySummary
import com.isivoltpro.maginaolivo.domain.harvest.HarvestAllocation
import com.isivoltpro.maginaolivo.domain.harvest.HarvestDraft
import com.isivoltpro.maginaolivo.domain.harvest.HarvestShareInput
import com.isivoltpro.maginaolivo.domain.harvest.Jornada
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Phase 19B — Jornada + multiple Pesadas.
 *
 * Gate 19B: three weighings on one date, including different cooperatives, survive restart
 * and produce one truthful Jornada summary. Everything here is local (no network): the same
 * path works in airplane mode.
 */
@RunWith(AndroidJUnit4::class)
class JornadaPesadasContractTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val workspaceId = UUID.fromString("10000000-0000-0000-0000-0000000019b1")
    private val farmId = UUID.fromString("20000000-0000-0000-0000-0000000019b1")
    private val campaignId = UUID.fromString("40000000-0000-0000-0000-0000000019b1")
    private val north = UUID.fromString("30000000-0000-0000-0000-0000000019b1")
    private val south = UUID.fromString("30000000-0000-0000-0000-0000000019b2")
    private val now = Instant.parse("2026-11-24T19:00:00Z")
    private val day = LocalDate.parse("2026-11-24")

    private lateinit var db: MaginaOlivoDatabase
    private lateinit var deliveries: OfflineFirstDeliveryRepository
    private lateinit var harvests: OfflineFirstHarvestRepository

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
    fun threePesadasOfOneDayToTwoCooperativesSurviveRestartAsOneTruthfulJornada() = runBlocking {
        // The first Pesada opens the Jornada; the next two join it explicitly.
        val first = ok(deliveries.create(pesada(2_100_000, "Coop. San Isidro", "V-101", "09:40").copy(newJornada = true)))
        val jornadaId = deliveries.observe(first).first()!!.harvestId!!
        ok(deliveries.create(pesada(1_850_500, "Almazara El Molino", "A-77", "13:05").copy(harvestId = jornadaId)))
        ok(deliveries.create(pesada(1_479_500, "Coop. San Isidro", "V-102", "17:20").copy(harvestId = jornadaId)))

        db.close()
        open()

        val harvest = harvests.observe(jornadaId).first()!!
        val jornada = Jornada.of(harvest, deliveries.observeAll().first())
        assertEquals(3, jornada.pesadas.size)
        // One truthful total: the Jornada's kilos are exactly its Pesadas' kilos.
        assertEquals(5_430_000L, jornada.pesadaGrams)
        assertEquals(5_430_000L, harvest.totalGrams)
        assertEquals(listOf("Coop. San Isidro", "Almazara El Molino"), jornada.destinations)
        assertEquals(listOf("V-101", "A-77", "V-102"), jornada.pesadas.map { it.ticketNumber })
        assertEquals(LocalTime.of(13, 5), jornada.pesadas[1].deliveryTime)
        // The day's Parcels are recorded without inventing a split between them.
        assertEquals(2, harvest.shares.size)
        assertTrue(harvest.shares.all { it.allocation == HarvestAllocation.UNALLOCATED && it.weightGrams == null })
        // Deliveries keep their own summary; nothing is counted twice.
        assertEquals(5_430_000L, DeliverySummary.of(deliveries.observeForCampaign(campaignId).first()).deliveredGrams)
        assertEquals(1, db.harvestDao().observeForCampaign(campaignId).first().size)
    }

    @Test
    fun correctingOrRemovingAPesadaKeepsTheJornadaTotalEqualToItsPesadas() = runBlocking {
        val jornadaId = ok(harvests.create(HarvestDraft(farmId, day, 9_999_000, listOf(HarvestShareInput(north, null), HarvestShareInput(south, null)))))
        val a = ok(deliveries.create(pesada(2_000_000, "Coop. San Isidro", "V-1").copy(harvestId = jornadaId)))
        val b = ok(deliveries.create(pesada(3_000_000, "Almazara El Molino", "A-1").copy(harvestId = jornadaId)))
        // The hand-typed estimate is replaced by the weighed kilos.
        assertEquals(5_000_000L, harvests.observe(jornadaId).first()!!.totalGrams)

        ok(deliveries.update(a, pesada(2_500_000, "Coop. San Isidro", "V-1").copy(harvestId = jornadaId)))
        assertEquals(5_500_000L, harvests.observe(jornadaId).first()!!.totalGrams)

        ok(deliveries.delete(b))
        assertEquals(2_500_000L, harvests.observe(jornadaId).first()!!.totalGrams)

        // Moving the last Pesada out keeps the Jornada's kilos as its own figure: never zero.
        ok(deliveries.update(a, pesada(2_500_000, "Coop. San Isidro", "V-1")))
        assertEquals(2_500_000L, harvests.observe(jornadaId).first()!!.totalGrams)
        assertNull(deliveries.observe(a).first()!!.harvestId)
    }

    @Test
    fun aJornadaWithPesadasNeverTakesKilosFromItsFormAndReleasesThemWhenRemoved() = runBlocking {
        val yesterday = day.minusDays(1)
        // As the form sends it: a single Parcel carries the whole total.
        val jornadaId = ok(harvests.create(HarvestDraft(farmId, yesterday, 1_000_000, listOf(HarvestShareInput(north, 1_000_000)))))
        val a = ok(deliveries.create(pesada(2_200_000, "Coop. San Isidro", "V-9").copy(deliveryDate = yesterday, harvestId = jornadaId)))
        // Single Parcel: it carries the whole total, and follows the Pesadas.
        assertEquals(2_200_000L, harvests.observe(jornadaId).first()!!.shares.single().weightGrams)

        ok(harvests.update(jornadaId, HarvestDraft(farmId, yesterday, 7_777_000, listOf(HarvestShareInput(north, 7_777_000)), workerCount = 5)))
        val edited = harvests.observe(jornadaId).first()!!
        assertEquals(2_200_000L, edited.totalGrams)
        assertEquals(2_200_000L, edited.shares.single().weightGrams)
        assertEquals(5, edited.workerCount)

        // The Jornada cannot move to a day after its own Pesadas.
        assertValidation(
            "harvestDate",
            harvests.update(jornadaId, HarvestDraft(farmId, day, 1, listOf(HarvestShareInput(north, null)))),
        )

        ok(harvests.delete(jornadaId))
        val released = deliveries.observe(a).first()!!
        assertNull(released.harvestId)
        assertEquals(2_200_000L, released.netGrams)
        assertEquals("V-9", released.ticketNumber)
    }

    @Test
    fun aPesadaIsNeverLinkedToAJornadaThatCannotHoldIt() = runBlocking {
        val exact = ok(
            harvests.create(HarvestDraft(farmId, day, 3_000_000, listOf(HarvestShareInput(north, 1_000_000), HarvestShareInput(south, 2_000_000)))),
        )
        assertValidation("harvestId", deliveries.create(pesada(1_000_000, "Coop. San Isidro", "V-3").copy(harvestId = exact)))

        val later = ok(harvests.create(HarvestDraft(farmId, day, 1_000_000, listOf(HarvestShareInput(north, null)))))
        assertValidation(
            "harvestId",
            deliveries.create(pesada(1_000_000, "Coop. San Isidro", "V-4").copy(deliveryDate = day.minusDays(1), harvestId = later)),
        )
        assertValidation("harvestId", deliveries.create(pesada(1_000_000, "Coop. San Isidro", "V-5").copy(harvestId = UUID.randomUUID())))
        // Nothing was written by the refused Pesadas.
        assertTrue(deliveries.observeAll().first().isEmpty())
        assertEquals(3_000_000L, harvests.observe(exact).first()!!.totalGrams)
    }

    private fun pesada(net: Long, cooperative: String, ticket: String, time: String? = null) = DeliveryDraft(
        farmId = farmId,
        deliveryDate = day,
        destinationOrganizationId = null,
        destinationName = cooperative,
        netGrams = net,
        shares = listOf(DeliveryShareInput(north, null), DeliveryShareInput(south, null)),
        ticketNumber = ticket,
        deliveryTime = time?.let(LocalTime::parse),
    )

    private fun open() {
        db = MaginaOlivoDatabase.create(context, DB)
        val clock = FixedClock(now)
        deliveries = OfflineFirstDeliveryRepository(db, clock, RandomIds, TestDispatchers) { ZoneOffset.UTC }
        harvests = OfflineFirstHarvestRepository(db, clock, RandomIds, TestDispatchers) { ZoneOffset.UTC }
    }

    private suspend fun seed() {
        val meta = LocalMetadata(now, now)
        db.workspaceDao().upsert(
            WorkspaceEntity(workspaceId, "Olivar", UUID.randomUUID(), "ES", "Europe/Madrid", "es-ES", "EUR", meta),
        )
        db.farmDao().upsert(FarmEntity(farmId, workspaceId, "El Cortijo", metadata = meta))
        listOf(north to "Norte", south to "Sur").forEach { (id, name) ->
            db.parcelDao().upsert(ParcelEntity(id, workspaceId, name, source = "MANUAL", metadata = meta))
        }
        db.campaignDao().upsert(
            CampaignEntity(campaignId, workspaceId, farmId, "2026/27", LocalDate.parse("2026-10-01"), null, CampaignStatus.HARVEST, metadata = meta),
        )
        db.campaignDao().upsertSnapshots(
            listOf(north to "Norte", south to "Sur").map { (id, name) ->
                CampaignParcelSnapshotEntity(UUID.randomUUID(), workspaceId, campaignId, id, farmId, "El Cortijo", name, metadata = meta)
            },
        )
    }

    private fun <T> ok(result: AppResult<T>): T = when (result) {
        is AppResult.Success -> result.value
        is AppResult.Failure -> throw AssertionError("Expected success but was ${result.error}")
    }

    private fun assertValidation(field: String, result: AppResult<*>) {
        val error = (result as? AppResult.Failure)?.error
        assertTrue("Expected validation on $field but was $result", error is AppError.Validation && error.field == field)
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
        const val DB = "jornada-pesadas-contract-test.db"
    }
}
