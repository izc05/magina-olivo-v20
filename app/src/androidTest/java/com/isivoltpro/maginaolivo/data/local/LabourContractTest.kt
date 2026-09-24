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
import com.isivoltpro.maginaolivo.data.repository.OfflineFirstHarvestRepository
import com.isivoltpro.maginaolivo.data.repository.OfflineFirstLabourRepository
import com.isivoltpro.maginaolivo.domain.harvest.HarvestDraft
import com.isivoltpro.maginaolivo.domain.harvest.HarvestShareInput
import com.isivoltpro.maginaolivo.domain.labour.CountDraft
import com.isivoltpro.maginaolivo.domain.labour.CrewDraft
import com.isivoltpro.maginaolivo.domain.labour.LabourChange
import com.isivoltpro.maginaolivo.domain.labour.LabourSummary
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Phase 19D — Jornales.
 *
 * Gate 19D: five workers can be recorded in a few taps (one save) and the daily/campaign total
 * remains deterministic after edits.
 */
@RunWith(AndroidJUnit4::class)
class LabourContractTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val workspaceId = UUID.fromString("10000000-0000-0000-0000-0000000019d1")
    private val farmId = UUID.fromString("20000000-0000-0000-0000-0000000019d1")
    private val campaignId = UUID.fromString("40000000-0000-0000-0000-0000000019d1")
    private val north = UUID.fromString("30000000-0000-0000-0000-0000000019d1")
    private val now = Instant.parse("2026-11-25T19:00:00Z")
    private val day = LocalDate.parse("2026-11-25")

    private lateinit var db: MaginaOlivoDatabase
    private lateinit var labour: OfflineFirstLabourRepository
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
    fun fiveWorkersInOneSaveAndTotalsStayDeterministicAfterEdits() = runBlocking {
        val jornada = jornada(day)
        val crew = listOf("Antonio", "Paco", "Mari", "Juan", "El Rubio").map { ok(labour.addWorker(it)) }
        assertEquals(5, ok(labour.recordCrew(CrewDraft(jornada, crew, LabourUnit.FULL_DAY))))
        ok(labour.recordCount(CountDraft(jornada, 2, LabourUnit.HALF_DAY)))

        db.close()
        open()

        val lines = labour.observeForHarvest(jornada).first()
        assertEquals(6, lines.size)
        assertEquals(LabourSummary(people = 7, fullDays = 5, halfDays = 2, minutes = 0), LabourSummary.of(lines))

        // Edits: one person worked 6 h instead of a day; the quick count becomes 3 halves.
        val paco = lines.first { it.workerName == "Paco" }
        ok(labour.update(paco.id, LabourChange(1, LabourUnit.HOURS, 360)))
        val count = lines.first { it.workerId == null }
        ok(labour.update(count.id, LabourChange(3, LabourUnit.HALF_DAY, null)))
        ok(labour.remove(lines.first { it.workerName == "El Rubio" }.id))

        val edited = LabourSummary.of(labour.observeForHarvest(jornada).first())
        assertEquals(LabourSummary(people = 7, fullDays = 3, halfDays = 3, minutes = 360), edited)
        assertEquals(edited, LabourSummary.of(labour.observeForCampaign(campaignId).first()))
        // No money is ever written by labour.
        assertEquals(0, count("expenses"))
    }

    @Test
    fun theSamePersonIsNotCountedTwiceInOneJornadaAndYesterdaysCrewCanBeRepeated() = runBlocking {
        val yesterday = jornada(day.minusDays(1))
        val crew = listOf("Antonio", "Paco").map { ok(labour.addWorker(it)) }
        ok(labour.recordCrew(CrewDraft(yesterday, crew, LabourUnit.FULL_DAY)))
        // Saving the same name again returns the same person.
        assertEquals(crew[0], ok(labour.addWorker("antonio")))

        val today = jornada(day)
        assertEquals(crew.toSet(), labour.previousCrew(today).toSet())
        ok(labour.recordCrew(CrewDraft(today, labour.previousCrew(today), LabourUnit.FULL_DAY)))
        assertValidation("workers", labour.recordCrew(CrewDraft(today, listOf(crew[0]), LabourUnit.HALF_DAY)))
        assertValidation("quantity", labour.update(labour.observeForHarvest(today).first().first().id, LabourChange(2, LabourUnit.FULL_DAY, null)))
        assertEquals(4, LabourSummary.of(labour.observeForCampaign(campaignId).first()).people)
    }

    @Test
    fun aClosedCampaignTakesNoLabourAndARemovedJornadaTakesItsLabourWithIt() = runBlocking {
        val jornada = jornada(day)
        ok(labour.recordCount(CountDraft(jornada, 5, LabourUnit.FULL_DAY)))
        ok(harvests.delete(jornada))
        assertTrue(labour.observeForHarvest(jornada).first().isEmpty())
        assertTrue(labour.observeForCampaign(campaignId).first().isEmpty())

        val other = jornada(day)
        val campaign = db.campaignDao().findById(campaignId)!!
        db.campaignDao().upsert(campaign.copy(status = CampaignStatus.CLOSED, endDate = day))
        val result = labour.recordCount(CountDraft(other, 1, LabourUnit.FULL_DAY))
        assertEquals(AppError.Conflict("closed_campaign"), (result as AppResult.Failure).error)
    }

    private suspend fun jornada(date: LocalDate): UUID =
        ok(harvests.create(HarvestDraft(farmId, date, 1_000_000, listOf(HarvestShareInput(north, 1_000_000)))))

    private fun open() {
        db = MaginaOlivoDatabase.create(context, DB)
        val clock = FixedClock(now)
        val workspaces = object : WorkspaceRepository {
            override suspend fun ensureLocalWorkspace(): AppResult<UUID> = AppResult.Success(workspaceId)
        }
        labour = OfflineFirstLabourRepository(db, workspaces, clock, RandomIds, TestDispatchers)
        harvests = OfflineFirstHarvestRepository(db, clock, RandomIds, TestDispatchers) { ZoneOffset.UTC }
    }

    private suspend fun seed() {
        val meta = LocalMetadata(now, now)
        db.workspaceDao().upsert(WorkspaceEntity(workspaceId, "Olivar", UUID.randomUUID(), "ES", "Europe/Madrid", "es-ES", "EUR", meta))
        db.farmDao().upsert(FarmEntity(farmId, workspaceId, "El Cortijo", metadata = meta))
        db.parcelDao().upsert(ParcelEntity(north, workspaceId, "Norte", source = "MANUAL", metadata = meta))
        db.campaignDao().upsert(
            CampaignEntity(campaignId, workspaceId, farmId, "2026/27", LocalDate.parse("2026-10-01"), null, CampaignStatus.HARVEST, metadata = meta),
        )
        db.campaignDao().upsertSnapshots(
            listOf(CampaignParcelSnapshotEntity(UUID.randomUUID(), workspaceId, campaignId, north, farmId, "El Cortijo", "Norte", metadata = meta)),
        )
    }

    private fun count(table: String): Int =
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use { it.moveToFirst(); it.getInt(0) }

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
        const val DB = "labour-contract-test.db"
    }
}
