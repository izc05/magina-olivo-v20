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
import com.isivoltpro.maginaolivo.domain.phytosanitary.AgronomicCredentialDraft
import com.isivoltpro.maginaolivo.domain.phytosanitary.AgronomicPersonDraft
import com.isivoltpro.maginaolivo.domain.phytosanitary.PhytosanitaryEquipmentInspectionDraft
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
    fun agronomicPersonIsNotAWorkerAndKeepsIdentityOffline() = runBlocking {
        val id = ok(
            repository.createPerson(
                AgronomicPersonDraft(
                    displayName = "Juan Aplicador",
                    taxId = "12345678Z",
                    isAdvisor = true,
                ),
            ),
        )
        val person = repository.observeActivePeople().first().single()
        assertEquals(id, person.id)
        assertEquals("12345678Z", person.taxId)
        assertTrue(person.isAdvisor)
        assertTrue(db.syncOutboxDao().listForEntity(SyncEntityType.AGRONOMIC_PERSON, id).isNotEmpty())

        assertValidation("displayName", repository.createPerson(AgronomicPersonDraft(" ")))
        val duplicate = repository.createPerson(AgronomicPersonDraft("juan aplicador"))
        assertEquals(AppError.Conflict("duplicate_agronomic_person"), (duplicate as AppResult.Failure).error)

        ok(repository.archivePerson(id))
        assertTrue(repository.observeActivePeople().first().isEmpty())
        ok(repository.restorePerson(id))
        assertEquals(id, repository.observeActivePeople().first().single().id)
    }

    @Test
    fun credentialsAreHistoricalAndAReissueNeverOverwritesThePreviousOne() = runBlocking {
        val personId = ok(repository.createPerson(AgronomicPersonDraft("Juan Aplicador")))
        val firstId = ok(
            repository.addCredential(
                personId,
                AgronomicCredentialDraft(
                    credentialType = "ROPO_APPLICATOR",
                    number = "ROPO-2025",
                    categoryCode = "BASIC",
                    validFrom = LocalDate.parse("2025-01-01"),
                    validUntil = LocalDate.parse("2026-12-31"),
                ),
            ),
        )
        val secondId = ok(
            repository.addCredential(
                personId,
                AgronomicCredentialDraft(
                    credentialType = "ROPO_APPLICATOR",
                    number = "ROPO-2027",
                    categoryCode = "QUALIFIED",
                    validFrom = LocalDate.parse("2027-01-01"),
                ),
            ),
        )
        val credentials = repository.observeCredentials(personId).first()
        assertEquals(setOf(firstId, secondId), credentials.map { it.id }.toSet())
        assertTrue(credentials.any { it.number == "ROPO-2025" && it.categoryCode == "BASIC" })
        assertTrue(credentials.any { it.number == "ROPO-2027" && it.categoryCode == "QUALIFIED" })
        assertTrue(db.syncOutboxDao().listForEntity(SyncEntityType.AGRONOMIC_CREDENTIAL, firstId).isNotEmpty())
        assertTrue(db.syncOutboxDao().listForEntity(SyncEntityType.AGRONOMIC_CREDENTIAL, secondId).isNotEmpty())
    }

    @Test
    fun invalidCredentialValidityRangeIsRejected() = runBlocking {
        val personId = ok(repository.createPerson(AgronomicPersonDraft("Asesor")))
        val result = repository.addCredential(
            personId,
            AgronomicCredentialDraft(
                credentialType = "ADVISOR",
                number = "ADV-1",
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

        val oldInspection = ok(
            repository.addEquipmentInspection(
                machineId,
                PhytosanitaryEquipmentInspectionDraft(
                    inspectionDate = LocalDate.parse("2026-03-01"),
                    resultCode = "PASS",
                    certificateReference = "CERT-2026",
                ),
            ),
        )
        val newInspection = ok(
            repository.addEquipmentInspection(
                machineId,
                PhytosanitaryEquipmentInspectionDraft(
                    inspectionDate = LocalDate.parse("2029-03-01"),
                    resultCode = "PASS",
                    certificateReference = "CERT-2029",
                ),
            ),
        )
        val inspections = repository.observeEquipmentInspections(machineId).first()
        assertEquals(setOf(oldInspection, newInspection), inspections.map { it.id }.toSet())
        assertTrue(inspections.any { it.inspectionDate == LocalDate.parse("2026-03-01") })
        assertTrue(inspections.any { it.inspectionDate == LocalDate.parse("2029-03-01") })
        assertEquals(before, db.machineDao().findById(machineId))
        assertTrue(db.syncOutboxDao().listForEntity(SyncEntityType.PHYTO_EQUIPMENT_INSPECTION, oldInspection).isNotEmpty())
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
