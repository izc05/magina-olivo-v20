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
import com.isivoltpro.maginaolivo.data.repository.OfflineFirstEquipmentRepository
import com.isivoltpro.maginaolivo.data.repository.OfflineFirstMachineRepository
import com.isivoltpro.maginaolivo.domain.harvest.HarvestDraft
import com.isivoltpro.maginaolivo.domain.harvest.HarvestShareInput
import com.isivoltpro.maginaolivo.domain.equipment.EquipmentDraftLine
import com.isivoltpro.maginaolivo.domain.equipment.EquipmentSummary
import com.isivoltpro.maginaolivo.domain.equipment.EquipmentType
import com.isivoltpro.maginaolivo.domain.machinery.MachineCategory
import com.isivoltpro.maginaolivo.domain.machinery.MachineDraft
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
 * Phase 19E — Jornada equipment.
 *
 * Gate 19E: `2 vibradoras + 1 peine + 1 tractor` is representable without creating fake
 * individual Machine assets.
 */
@RunWith(AndroidJUnit4::class)
class EquipmentContractTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val workspaceId = UUID.fromString("10000000-0000-0000-0000-0000000019e1")
    private val farmId = UUID.fromString("20000000-0000-0000-0000-0000000019e1")
    private val campaignId = UUID.fromString("40000000-0000-0000-0000-0000000019e1")
    private val north = UUID.fromString("30000000-0000-0000-0000-0000000019e1")
    private val now = Instant.parse("2026-11-26T19:00:00Z")
    private val day = LocalDate.parse("2026-11-26")

    private lateinit var db: MaginaOlivoDatabase
    private lateinit var equipment: OfflineFirstEquipmentRepository
    private lateinit var harvests: OfflineFirstHarvestRepository
    private lateinit var machines: OfflineFirstMachineRepository

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
    fun twoShakersACombAndATractorWithoutAnyMachineAsset() = runBlocking {
        val jornada = jornada()
        ok(
            equipment.replaceForHarvest(
                jornada,
                listOf(
                    EquipmentDraftLine(EquipmentType.SHAKER, 2),
                    EquipmentDraftLine(EquipmentType.COMB, 1),
                    EquipmentDraftLine(EquipmentType.TRACTOR, 1),
                ),
            ),
        )
        db.close()
        open()

        val lines = equipment.observeForHarvest(jornada).first()
        assertEquals(3, lines.size)
        assertEquals("1 tractor · 2 vibradoras · 1 peine eléctrico", EquipmentSummary.of(lines).label())
        // No fake Machine was created for any of them.
        assertEquals(0, count("machines"))
        assertTrue(lines.all { it.machineId == null })
    }

    @Test
    fun savingTheSheetAgainReplacesItDeterministically() = runBlocking {
        val jornada = jornada()
        ok(equipment.replaceForHarvest(jornada, listOf(EquipmentDraftLine(EquipmentType.SHAKER, 2), EquipmentDraftLine(EquipmentType.COMB, 1))))
        val first = equipment.observeForHarvest(jornada).first()
        val shakerId = first.first { it.type == EquipmentType.SHAKER }.id

        ok(equipment.replaceForHarvest(jornada, listOf(EquipmentDraftLine(EquipmentType.SHAKER, 3), EquipmentDraftLine(EquipmentType.OTHER, 1, "Paraguas"))))
        val second = equipment.observeForHarvest(jornada).first()
        assertEquals("3 vibradoras · 1 paraguas", EquipmentSummary.of(second).label())
        // The same line was updated, not duplicated.
        assertEquals(shakerId, second.first { it.type == EquipmentType.SHAKER }.id)
        assertEquals(2L, second.first { it.type == EquipmentType.SHAKER }.version)
        // Saving the same sheet again changes nothing.
        ok(equipment.replaceForHarvest(jornada, listOf(EquipmentDraftLine(EquipmentType.SHAKER, 3), EquipmentDraftLine(EquipmentType.OTHER, 1, "paraguas"))))
        assertEquals(second, equipment.observeForHarvest(jornada).first())
        assertEquals(EquipmentSummary.of(second), EquipmentSummary.of(equipment.observeForCampaign(campaignId).first()))
    }

    @Test
    fun aRegisteredMachineIsReferencedOnlyAndAClosedCampaignTakesNothing() = runBlocking {
        val fendt = ok(machines.create(MachineDraft(name = "Fendt 209", category = MachineCategory.TRACTOR)))
        val jornada = jornada()
        ok(equipment.replaceForHarvest(jornada, listOf(EquipmentDraftLine(EquipmentType.TRACTOR, 1, machineId = fendt), EquipmentDraftLine(EquipmentType.SHAKER, 2))))
        val lines = equipment.observeForHarvest(jornada).first()
        assertEquals("Fendt 209", lines.single { it.machineId == fendt }.label)
        assertEquals(1, count("machines"))
        assertValidation("machineId", equipment.replaceForHarvest(jornada, listOf(EquipmentDraftLine(EquipmentType.TRACTOR, 1, machineId = UUID.randomUUID()))))

        ok(harvests.delete(jornada))
        assertTrue(equipment.observeForHarvest(jornada).first().isEmpty())

        val other = jornada()
        val campaign = db.campaignDao().findById(campaignId)!!
        db.campaignDao().upsert(campaign.copy(status = CampaignStatus.CLOSED, endDate = day))
        val result = equipment.replaceForHarvest(other, listOf(EquipmentDraftLine(EquipmentType.SHAKER, 1)))
        assertEquals(AppError.Conflict("closed_campaign"), (result as AppResult.Failure).error)
    }

    private suspend fun jornada(): UUID =
        ok(harvests.create(HarvestDraft(farmId, day, 1_000_000, listOf(HarvestShareInput(north, 1_000_000)))))

    private fun open() {
        db = MaginaOlivoDatabase.create(context, DB)
        val clock = FixedClock(now)
        val workspaces = object : WorkspaceRepository {
            override suspend fun ensureLocalWorkspace(): AppResult<UUID> = AppResult.Success(workspaceId)
        }
        equipment = OfflineFirstEquipmentRepository(db, clock, RandomIds, TestDispatchers)
        machines = OfflineFirstMachineRepository(db, workspaces, clock, RandomIds, TestDispatchers)
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
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table WHERE deleted_at IS NULL").use { it.moveToFirst(); it.getInt(0) }

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
        const val DB = "equipment-contract-test.db"
    }
}
