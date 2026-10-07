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
import com.isivoltpro.maginaolivo.data.local.entity.ExpenseEntity
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
import com.isivoltpro.maginaolivo.domain.delivery.ParcelYield
import com.isivoltpro.maginaolivo.domain.delivery.PesadaOrigin
import com.isivoltpro.maginaolivo.domain.delivery.PesadaQuery
import com.isivoltpro.maginaolivo.domain.delivery.PesadaSearch
import com.isivoltpro.maginaolivo.domain.delivery.YieldDraft
import com.isivoltpro.maginaolivo.domain.delivery.YieldStatus
import com.isivoltpro.maginaolivo.domain.harvest.HARVEST_HAS_DELIVERIES
import com.isivoltpro.maginaolivo.domain.harvest.HarvestAllocation
import com.isivoltpro.maginaolivo.domain.harvest.HarvestDraft
import com.isivoltpro.maginaolivo.domain.harvest.HarvestShareInput
import com.isivoltpro.maginaolivo.domain.harvest.HarvestSummary
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Phase 19B — Jornada + multiple Pesadas; CR-010 — the automatic day (A1, notes 2–3).
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
        // CR-010 §4: nobody opens or picks the day; the three Pesadas of one date share it.
        val first = ok(deliveries.create(pesada(2_100_000, "Coop. San Isidro", "V-101", "09:40")))
        val jornadaId = deliveries.observe(first).first()!!.harvestId!!
        ok(deliveries.create(pesada(1_850_500, "Almazara El Molino", "A-77", "13:05")))
        ok(deliveries.create(pesada(1_479_500, "Coop. San Isidro", "V-102", "17:20")))

        db.close()
        open()

        val harvest = harvests.observe(jornadaId).first()!!
        assertTrue(harvest.automatic)
        val jornada = Jornada.of(harvest, deliveries.observeAll().first())
        assertEquals(3, jornada.pesadas.size)
        // One truthful total: the day's kilos are exactly its Pesadas' kilos.
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
    fun aDaysOriginIsTheUnionOfItsPesadasParcelsWithoutASplit() = runBlocking {
        // CR-010 (note 2): exact kilos stay on each Pesada; the day only knows where they came from.
        val a = ok(deliveries.create(pesada(2_000_000, "Coop. San Isidro", "V-1").copy(shares = listOf(DeliveryShareInput(north, 2_000_000)))))
        val dayId = deliveries.observe(a).first()!!.harvestId!!
        assertEquals(setOf(north), harvests.observe(dayId).first()!!.shares.map { it.parcelId }.toSet())

        ok(deliveries.create(pesada(1_000_000, "Coop. San Isidro", "V-2").copy(shares = listOf(DeliveryShareInput(south, 1_000_000)))))
        val day = harvests.observe(dayId).first()!!
        assertEquals(setOf(north, south), day.shares.map { it.parcelId }.toSet())
        assertTrue(day.shares.all { it.allocation == HarvestAllocation.UNALLOCATED && it.weightGrams == null })
        assertEquals(3_000_000L, day.totalGrams)
        // The single-Parcel Pesadas keep their exact kilos; none is inferred for the day.
        assertEquals(2_000_000L, deliveries.observe(a).first()!!.shares.single().weightGrams)
    }

    @Test
    fun aHandRecordedJornadaIsNeverReusedAndKeepsItsOwnKilos() = runBlocking {
        // CR-010 (note 3): linking a Pesada to it would overwrite the farmer's typed kilos.
        val legacy = ok(harvests.create(HarvestDraft(farmId, day, 9_999_000, listOf(HarvestShareInput(north, null), HarvestShareInput(south, null)))))
        val a = ok(deliveries.create(pesada(2_000_000, "Coop. San Isidro", "V-1")))
        val b = ok(deliveries.create(pesada(3_000_000, "Almazara El Molino", "A-1")))
        val dayId = deliveries.observe(a).first()!!.harvestId!!
        assertTrue(dayId != legacy)
        assertEquals(dayId, deliveries.observe(b).first()!!.harvestId)
        assertEquals(5_000_000L, harvests.observe(dayId).first()!!.totalGrams)

        ok(deliveries.update(a, pesada(2_500_000, "Coop. San Isidro", "V-1")))
        assertEquals(5_500_000L, harvests.observe(dayId).first()!!.totalGrams)
        ok(deliveries.delete(b))
        assertEquals(2_500_000L, harvests.observe(dayId).first()!!.totalGrams)

        // The hand-recorded Jornada of the same date is untouched and still shown.
        val kept = harvests.observe(legacy).first()!!
        assertEquals(9_999_000L, kept.totalGrams)
        assertTrue(!kept.automatic)
        assertEquals(2, db.harvestDao().observeForCampaign(campaignId).first().size)
    }

    @Test
    fun aPesadaLinkedBeforeCr010KeepsItsJornadaUntilItsDateChanges() = runBlocking {
        val yesterday = day.minusDays(1)
        // As the form sends it: a single Parcel carries the whole total.
        val legacy = ok(harvests.create(HarvestDraft(farmId, yesterday, 1_000_000, listOf(HarvestShareInput(north, 1_000_000)))))
        val a = ok(deliveries.create(pesada(2_200_000, "Coop. San Isidro", "V-9").copy(deliveryDate = yesterday)))
        linkAsBeforeCr010(a, legacy)

        // Edited on the same date, it stays in its Jornada, whose kilos follow it (Phase 19B).
        ok(deliveries.update(a, pesada(2_300_000, "Coop. San Isidro", "V-9").copy(deliveryDate = yesterday)))
        assertEquals(legacy, deliveries.observe(a).first()!!.harvestId)
        assertEquals(2_300_000L, harvests.observe(legacy).first()!!.totalGrams)
        assertEquals(2_300_000L, harvests.observe(legacy).first()!!.shares.single().weightGrams)
        // Its kilos never come from the Jornada form while it has Pesadas.
        ok(harvests.update(legacy, HarvestDraft(farmId, yesterday, 7_777_000, listOf(HarvestShareInput(north, 7_777_000)), workerCount = 5)))
        assertEquals(2_300_000L, harvests.observe(legacy).first()!!.totalGrams)
        assertEquals(5, harvests.observe(legacy).first()!!.workerCount)

        // A new date moves it to that date's automatic day; the Jornada it left keeps no kilos of it
        // (they were the Pesada's): back to «Kg pendientes de pesada», never shown as history.
        ok(deliveries.update(a, pesada(2_300_000, "Coop. San Isidro", "V-9")))
        val moved = deliveries.observe(a).first()!!.harvestId!!
        assertTrue(moved != legacy)
        assertTrue(harvests.observe(moved).first()!!.automatic)
        val left = harvests.observe(legacy).first()!!
        assertTrue(left.awaitingPesadas)
        assertEquals(HarvestAllocation.UNALLOCATED, left.shares.single().allocation)

        // #457: a day with Pesadas is never removed from under them; they keep it, intact.
        db.expenseDao().upsert(expenseOn(moved)) // so the day would stay even without its Pesada
        val refused = harvests.delete(moved)
        assertEquals(AppError.Conflict(HARVEST_HAS_DELIVERIES), (refused as AppResult.Failure).error)
        val kept = deliveries.observe(a).first()!!
        assertEquals(moved, kept.harvestId)
        assertEquals(2_300_000L, kept.netGrams)
        assertEquals("V-9", kept.ticketNumber)
        assertNotNull(harvests.observe(moved).first())
    }

    @Test
    fun aJornadaOpenedBeforeAnyPesadaStartsAtZeroAndThenCarriesItsPesadas() = runBlocking {
        // Gate 20 (emulator, build 575): the day can exist before its first Pesada.
        val jornadaId = ok(harvests.openJornada(farmId, day))
        val opened = harvests.observe(jornadaId).first()!!
        assertTrue(opened.automatic)
        // Stored 0 means "not weighed yet": shown as «Kg pendientes de pesada», out of every total.
        assertTrue(opened.awaitingPesadas)
        assertEquals(0, HarvestSummary.of(listOf(opened)).weighedCount)
        // #458: until a Pesada says where its olives came from, the day is attributed to no Parcel.
        assertTrue(opened.shares.isEmpty())
        // Opening it again the same day returns the same day: never a second one.
        assertEquals(jornadaId, ok(harvests.openJornada(farmId, day)))
        assertEquals(1, db.harvestDao().observeForCampaign(campaignId).first().size)
        // No future day, no day before the Campaign.
        assertValidation("harvestDate", harvests.openJornada(farmId, day.plusDays(1)))
        assertValidation("harvestDate", harvests.openJornada(farmId, LocalDate.parse("2026-09-30")))

        // The Pesadas of that date join it by themselves.
        ok(deliveries.create(pesada(2_100_000, "Coop. San Isidro", "V-201")))
        ok(deliveries.create(pesada(1_400_000, "Almazara El Molino", "A-202")))
        db.close()
        open()

        val harvest = harvests.observe(jornadaId).first()!!
        assertEquals(3_500_000L, harvest.totalGrams)
        assertTrue(!harvest.awaitingPesadas)
        assertEquals(2, Jornada.of(harvest, deliveries.observeAll().first()).pesadas.size)
    }

    @Test
    fun anAutomaticDayNeverKeepsKilosItNoLongerHas() = runBlocking {
        // CR-010 A1: delete the only Pesada → nothing is left, and no kilos survive it.
        val only = ok(deliveries.create(pesada(2_000_000, "Coop. San Isidro", "V-1")))
        val first = deliveries.observe(only).first()!!.harvestId!!
        ok(deliveries.delete(only))
        assertNull(harvests.observe(first).first())
        assertTrue(db.harvestDao().observeForCampaign(campaignId).first().isEmpty())

        // A day that still owns something (here an expense) stays, back to «Kg pendientes de pesada».
        val p = ok(deliveries.create(pesada(2_000_000, "Coop. San Isidro", "V-2")))
        val kept = deliveries.observe(p).first()!!.harvestId!!
        db.expenseDao().upsert(expenseOn(kept))
        // Move it to another date: it goes to that date's day; the day it left keeps no kilos.
        ok(deliveries.update(p, pesada(2_000_000, "Coop. San Isidro", "V-2").copy(deliveryDate = day.minusDays(1))))
        val other = deliveries.observe(p).first()!!.harvestId!!
        assertTrue(other != kept)
        val left = harvests.observe(kept).first()!!
        assertTrue(left.awaitingPesadas)
        assertEquals(0, HarvestSummary.of(listOf(left)).weighedCount)
        assertEquals(2_000_000L, harvests.observe(other).first()!!.totalGrams)

        // Move it back: it rejoins the day it left, and the emptied one (nothing else in it) goes.
        ok(deliveries.update(p, pesada(2_000_000, "Coop. San Isidro", "V-2")))
        assertEquals(kept, deliveries.observe(p).first()!!.harvestId)
        assertEquals(2_000_000L, harvests.observe(kept).first()!!.totalGrams)
        assertNull(harvests.observe(other).first())
    }

    @Test
    fun aDayWithWhatTheFarmerTypedOnItStaysAndItsOriginIsNoLongerDetermined() = runBlocking {
        // Codex review on #290: people or machinery typed on the day are the farmer's record.
        val p = ok(deliveries.create(pesada(2_000_000, "Coop. San Isidro", "V-1").copy(shares = listOf(DeliveryShareInput(north, 2_000_000)))))
        val dayId = deliveries.observe(p).first()!!.harvestId!!
        ok(harvests.update(dayId, HarvestDraft(farmId, day, null, emptyList(), workerCount = 4, machineryText = "Vibrador")))
        assertEquals(setOf(north), harvests.observe(dayId).first()!!.shares.map { it.parcelId }.toSet())

        ok(deliveries.delete(p))
        val left = harvests.observe(dayId).first()!!
        assertTrue(left.awaitingPesadas)
        assertEquals(4, left.workerCount)
        assertEquals("Vibrador", left.machineryText)
        // #458: no Pesada says where its olives came from any more: no Parcel is presumed, so the
        // day shows in no Parcel's history.
        assertTrue(left.shares.isEmpty())
        assertTrue(db.harvestDao().listParcels(dayId).isEmpty())
    }

    /** #458 (Codex): an automatic day written before this rule loses its presumed origin on start. */
    @Test
    fun anOlderAutomaticDayWithoutPesadasLosesItsPresumedOriginOnStart() = runBlocking {
        val empty = ok(harvests.openJornada(farmId, day))
        val now = Instant.parse("2026-12-02T08:00:00Z")
        // As the earlier code wrote it: every Parcel of the Campaign, with no Pesada behind it.
        db.harvestDao().upsertParcels(
            listOf(north to "Norte", south to "Sur").map { (parcelId, name) ->
                com.isivoltpro.maginaolivo.data.local.entity.HarvestParcelEntity(
                    id = UUID.randomUUID(), workspaceId = workspaceId, harvestId = empty, parcelId = parcelId,
                    parcelNameAtHarvest = name, allocationMode = HarvestAllocation.UNALLOCATED.name,
                    metadata = LocalMetadata(now, now),
                )
            },
        )
        // A legacy row may even keep kilos with no Pesada behind them.
        val stale = ok(harvests.openJornada(farmId, day.minusDays(2)))
        db.harvestDao().upsert(db.harvestDao().findById(stale)!!.copy(weightGrams = 1_500_000L))
        val weighed = ok(deliveries.create(pesada(1_000_000, "Coop. San Isidro", "V-1").copy(deliveryDate = day.minusDays(1))))
        val weighedDay = deliveries.observe(weighed).first()!!.harvestId!!
        val weighedRows = db.harvestDao().listParcels(weighedDay)
        // The presumption was never history: a closed Campaign is corrected too.
        val campaign = db.campaignDao().findById(campaignId)!!
        db.campaignDao().upsert(campaign.copy(status = CampaignStatus.CLOSED, endDate = day))

        ok(harvests.clearUnfoundedDayOrigins())
        val cleared = harvests.observe(empty).first()!!
        assertTrue(cleared.shares.isEmpty())
        assertTrue(cleared.awaitingPesadas)
        val unweighed = harvests.observe(stale).first()!!
        assertEquals(0L, unweighed.totalGrams)
        assertTrue(unweighed.shares.isEmpty())
        // A day its Pesadas support is left exactly as it was; running it again changes nothing.
        assertEquals(weighedRows, db.harvestDao().listParcels(weighedDay))
        assertEquals(1_000_000L, harvests.observe(weighedDay).first()!!.totalGrams)
        val version = cleared.version
        ok(harvests.clearUnfoundedDayOrigins())
        assertEquals(version, harvests.observe(empty).first()!!.version)
    }

    /** #458 C/D: a day's Parcel rows are kept as recorded; only Parcels joining or leaving change. */
    @Test
    fun aDayKeepsItsParcelRowsAndNamesWhenItsPesadasChange() = runBlocking {
        val a = ok(deliveries.create(pesada(2_000_000, "Coop. San Isidro", "V-1").copy(shares = listOf(DeliveryShareInput(north, 2_000_000)))))
        val dayId = deliveries.observe(a).first()!!.harvestId!!
        val recorded = db.harvestDao().listParcels(dayId).single()
        val parcel = db.parcelDao().findById(north)!!
        db.parcelDao().upsert(parcel.copy(displayName = "Parcela 1"))

        // Another Pesada of the same Parcel: the day's row for it is untouched.
        ok(deliveries.create(pesada(500_000, "Coop. San Isidro", "V-2").copy(shares = listOf(DeliveryShareInput(north, 500_000)))))
        assertEquals(listOf(recorded), db.harvestDao().listParcels(dayId))

        // A Pesada of another Parcel adds only that Parcel's row.
        val b = ok(deliveries.create(pesada(700_000, "Coop. San Isidro", "V-3").copy(shares = listOf(DeliveryShareInput(south, 700_000)))))
        val rows = db.harvestDao().listParcels(dayId).associateBy { it.parcelId }
        assertEquals(setOf(north, south), rows.keys)
        assertEquals(recorded, rows.getValue(north))

        // It leaving drops only its row.
        ok(deliveries.delete(b))
        assertEquals(listOf(recorded), db.harvestDao().listParcels(dayId))
        assertEquals(recorded.parcelNameAtHarvest, harvests.observe(dayId).first()!!.shares.single().parcelName)
    }

    @Test
    fun anAutomaticDayKeepsItsDateKilosAndParcelsWhenItsFormIsSaved() = runBlocking {
        val p = ok(deliveries.create(pesada(2_000_000, "Coop. San Isidro", "V-1")))
        val dayId = deliveries.observe(p).first()!!.harvestId!!
        // Only what describes the day itself is the farmer's to change.
        ok(harvests.update(dayId, HarvestDraft(farmId, day, null, emptyList(), workerCount = 6, notes = "Buen día")))
        val saved = harvests.observe(dayId).first()!!
        assertEquals(2_000_000L, saved.totalGrams)
        assertEquals(6, saved.workerCount)
        assertEquals("Buen día", saved.notes)
        assertEquals(setOf(north, south), saved.shares.map { it.parcelId }.toSet())
        // Its date is its Pesadas': each Pesada changes its own.
        assertValidation("harvestDate", harvests.update(dayId, HarvestDraft(farmId, day.minusDays(1), null, emptyList())))
    }

    @Test
    fun aYieldAddedDaysLaterChangesOnlyTheYieldRecordAndTheDerivedMetrics() = runBlocking {
        // Phase 19C (Gate 19C): the Pesada, its day and their outbox stay as they were.
        val first = ok(deliveries.create(pesada(2_000_000, "Coop. San Isidro", "V-45872")))
        val jornadaId = deliveries.observe(first).first()!!.harvestId!!
        val mixed = ok(deliveries.create(pesada(3_000_000, "Coop. San Isidro", "V-45873")))
        val deliveryBefore = db.deliveryDao().findById(first)
        val harvestBefore = db.harvestDao().findById(jornadaId)
        val outboxBefore = db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM sync_outbox").use { it.moveToFirst(); it.getInt(0) }

        val found = PesadaSearch.filter(deliveries.observeAll().first(), PesadaQuery(text = "45872", status = YieldStatus.PENDING))
        assertEquals(listOf(first), found.map { it.id })
        // The test clock is the weighing day; the analysis date may not be in the future.
        ok(deliveries.recordYield(first, YieldDraft(day, 2_150, null)))

        assertEquals(deliveryBefore, db.deliveryDao().findById(first))
        assertEquals(harvestBefore, db.harvestDao().findById(jornadaId))
        val outboxAfter = db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM sync_outbox").use { it.moveToFirst(); it.getInt(0) }
        assertEquals(outboxBefore + 1, outboxAfter) // one intent, for the yield record only
        val all = deliveries.observeAll().first()
        assertEquals(YieldStatus.WITH_YIELD, PesadaSearch.statusOf(all.first { it.id == first }))
        assertEquals(listOf(mixed), PesadaSearch.filter(all, PesadaQuery(status = YieldStatus.PENDING)).map { it.id })
        // Both Pesadas mix Norte and Sur with an unknown split: no Parcel yield is invented.
        assertTrue(ParcelYield.of(all).isEmpty())
        assertEquals(2_150, DeliverySummary.of(all).fatYield!!.hundredths)
    }

    /** What 0.5.0 wrote when a farmer linked a Pesada to a Jornada by hand: that link, and no automatic day. */
    private suspend fun linkAsBeforeCr010(deliveryId: UUID, jornadaId: UUID) {
        val row = db.deliveryDao().findById(deliveryId)!!
        val automatic = db.harvestDao().findById(row.harvestId!!)!!
        db.deliveryDao().upsert(row.copy(harvestId = jornadaId))
        db.harvestDao().upsert(automatic.copy(metadata = automatic.metadata.copy(deletedAt = now)))
    }

    private fun expenseOn(harvestId: UUID) = ExpenseEntity(
        id = UUID.randomUUID(),
        workspaceId = workspaceId,
        campaignId = campaignId,
        farmId = farmId,
        harvestId = harvestId,
        expenseDate = day,
        concept = "Transporte",
        category = "TRANSPORT",
        amountMinor = 4_000,
        currency = "EUR",
        metadata = LocalMetadata(now, now),
    )

    private fun pesada(net: Long, cooperative: String, ticket: String, time: String? = null) = DeliveryDraft(
        farmId = farmId,
        deliveryDate = day,
        destinationOrganizationId = null,
        destinationName = cooperative,
        netGrams = net,
        shares = listOf(DeliveryShareInput(north, null), DeliveryShareInput(south, null)),
        ticketNumber = ticket,
        deliveryTime = time?.let(LocalTime::parse),
        origin = PesadaOrigin.TREE,
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
