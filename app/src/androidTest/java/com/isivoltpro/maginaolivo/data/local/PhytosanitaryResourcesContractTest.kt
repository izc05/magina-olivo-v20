package com.isivoltpro.maginaolivo.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.isivoltpro.maginaolivo.core.common.AppError
import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.core.dispatchers.AppDispatchers
import com.isivoltpro.maginaolivo.core.id.IdGenerator
import com.isivoltpro.maginaolivo.core.time.AppClock
import com.isivoltpro.maginaolivo.data.local.entity.LocalMetadata
import com.isivoltpro.maginaolivo.data.local.entity.MachineEntity
import com.isivoltpro.maginaolivo.data.local.entity.WorkspaceEntity
import com.isivoltpro.maginaolivo.data.local.model.SyncEntityType
import com.isivoltpro.maginaolivo.data.repository.OfflineFirstPhytosanitaryResourceRepository
import com.isivoltpro.maginaolivo.domain.phytosanitary.AgronomicPersonDraft
import com.isivoltpro.maginaolivo.domain.phytosanitary.PhytosanitaryEquipmentProfileDraft
import com.isivoltpro.maginaolivo.domain.phytosanitary.RegulatoryResourceSource
import com.isivoltpro.maginaolivo.domain.workspace.WorkspaceRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
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

@RunWith(AndroidJUnit4::class)
class PhytosanitaryResourcesContractTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val workspaceId = UUID.fromString("10000000-0000-0000-0000-000000000544")
    private val machineId = UUID.fromString("20000000-0000-0000-0000-000000000544")
    private val now = Instant.parse("2026-10-07T12:00:00Z")

    private lateinit var db: MaginaOlivoDatabase
    private lateinit var repository: OfflineFirstPhytosanitaryResourceRepository

    @Before
    fun before() = runBlocking {
        context.deleteDatabase(DB)
        db = MaginaOlivoDatabase.create(context, DB)
        val meta = LocalMetadata(now, now)
        db.workspaceDao().upsert(
            WorkspaceEntity(workspaceId, "Olivar", UUID.randomUUID(), "ES", "Europe/Madrid", "es-ES", "EUR", meta),
        )
        db.machineDao().upsert(
            MachineEntity(
                id = machineId,
                workspaceId = workspaceId,
                name = "Atomizador principal",
                category = "ATOMIZER",
                registrationOrSerial = "SERIE-99",
                status = "ACTIVE",
                metadata = meta,
            ),
        )
        repository = OfflineFirstPhytosanitaryResourceRepository(
            db, Workspaces(workspaceId), FixedClock(now), RandomIds, TestDispatchers,
        )
    }

    @After
    fun after() {
        db.close()
        context.deleteDatabase(DB)
    }

    @Test
    fun agronomicPersonIsNotAWorkerAndKeepsLegalFieldsOffline() = runBlocking {
        val id = ok(
            repository.createPerson(
                AgronomicPersonDraft(
                    displayName = "Juan Aplicador",
                    taxId = "12345678Z",
                    ropoOrCardNumber = "ROPO-123",
                    cardTypeCode = "QUALIFIED",
                    isAdvisor = true,
                ),
            ),
        )
        val person = repository.observeActivePeople().first().single()
        assertEquals(id, person.id)
        assertEquals("12345678Z", person.taxId)
        assertEquals("ROPO-123", person.ropoOrCardNumber)
        assertEquals("QUALIFIED", person.cardTypeCode)
        assertTrue(person.isAdvisor)
        assertTrue(db.syncOutboxDao().listForEntity(SyncEntityType.AGRONOMIC_PERSON, id).isNotEmpty())

        assertValidation("displayName", repository.createPerson(AgronomicPersonDraft(" ")))
        val duplicate = repository.createPerson(AgronomicPersonDraft("juan aplicador"))
        assertEquals(AppError.Conflict("duplicate_agronomic_person"), (duplicate as AppResult.Failure).error)

        ok(repository.archivePerson(id))
        assertTrue(repository.observeActivePeople().first().isEmpty())
    }

    @Test
    fun invalidPersonValidityRangeIsRejected() = runBlocking {
        val result = repository.createPerson(
            AgronomicPersonDraft(
                displayName = "Asesor",
                validFrom = LocalDate.parse("2026-10-10"),
                validUntil = LocalDate.parse("2026-10-01"),
            ),
        )
        assertValidation("validUntil", result)
    }

    @Test
    fun equipmentProfileExtendsMachineWithoutChangingTheOperationalMachine() = runBlocking {
        val before = db.machineDao().findById(machineId)!!
        ok(
            repository.saveEquipmentProfile(
                machineId,
                PhytosanitaryEquipmentProfileDraft(
                    romaRegistration = "ROMA-JA-001",
                    acquisitionDate = LocalDate.parse("2024-02-01"),
                    lastInspectionDate = LocalDate.parse("2026-03-01"),
                    source = RegulatoryResourceSource.REAFA,
                    externalId = "rea-machine-1",
                    sourceVersion = "2026-10",
                    fetchedAt = now,
                ),
            ),
        )
        val profile = repository.observeEquipmentProfile(machineId).first()!!
        assertEquals("ROMA-JA-001", profile.romaRegistration)
        assertEquals(RegulatoryResourceSource.REAFA, profile.source)
        assertEquals("rea-machine-1", profile.externalId)
        assertEquals(before, db.machineDao().findById(machineId))
        assertTrue(db.syncOutboxDao().listForEntity(SyncEntityType.PHYTO_EQUIPMENT_PROFILE, machineId).isNotEmpty())
    }

    @Test
    fun foreignWorkspaceCannotAttachAProfileToThisMachine() = runBlocking {
        val other = OfflineFirstPhytosanitaryResourceRepository(
            db, Workspaces(UUID.randomUUID()), FixedClock(now), RandomIds, TestDispatchers,
        )
        assertValidation(
            "machine",
            other.saveEquipmentProfile(machineId, PhytosanitaryEquipmentProfileDraft(romaRegistration = "NO")),
        )
        assertNull(repository.observeEquipmentProfile(machineId).first())
    }

    private fun <T> ok(result: AppResult<T>): T = when (result) {
        is AppResult.Success -> result.value
        is AppResult.Failure -> throw AssertionError("Expected success but was ${result.error}")
    }

    private fun assertValidation(field: String, result: AppResult<*>) {
        val error = (result as? AppResult.Failure)?.error
        assertTrue("Expected validation on $field but was $result", error is AppError.Validation && error.field == field)
    }

    private data class Workspaces(val id: UUID) : WorkspaceRepository {
        override suspend fun ensureLocalWorkspace(): AppResult<UUID> = AppResult.Success(id)
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
        const val DB = "phytosanitary-resources-contract-test.db"
    }
}
