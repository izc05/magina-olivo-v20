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
import com.isivoltpro.maginaolivo.data.local.entity.WorkspaceEntity
import com.isivoltpro.maginaolivo.data.local.model.SyncEntityType
import com.isivoltpro.maginaolivo.data.repository.OfflineFirstOrganizationRepository
import com.isivoltpro.maginaolivo.domain.organization.OrganizationDraft
import com.isivoltpro.maginaolivo.domain.organization.OrganizationRepository
import com.isivoltpro.maginaolivo.domain.organization.OrganizationRole
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OrganizationRepositoryContractTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val workspaceId = UUID.fromString("10000000-0000-0000-0000-000000000550")
    private val now = Instant.parse("2026-10-05T12:00:00Z")

    private lateinit var db: MaginaOlivoDatabase
    private lateinit var repository: OrganizationRepository

    @Before
    fun before() = runBlocking {
        context.deleteDatabase(DB)
        db = MaginaOlivoDatabase.create(context, DB)
        db.workspaceDao().upsert(
            WorkspaceEntity(
                id = workspaceId,
                name = "Olivar",
                ownerUserId = UUID.randomUUID(),
                countryCode = "ES",
                timezone = "Europe/Madrid",
                locale = "es-ES",
                currency = "EUR",
                metadata = LocalMetadata(now, now),
            ),
        )
        repository = repositoryFor(workspaceId)
    }

    @After
    fun after() {
        db.close()
        context.deleteDatabase(DB)
    }

    @Test
    fun updatingAnOrganizationPreservesAddressAndWebsite() = runBlocking {
        val created = repository.create(
            OrganizationDraft(
                name = "Cooperativa Sierra",
                roles = setOf(OrganizationRole.COOPERATIVE),
                taxId = "F12345678",
                municipality = "Bedmar",
                province = "Jaén",
                address = "Calle Olivo 1",
                phone = "953000000",
                website = "https://example.test",
            ),
        )
        assertTrue(created is AppResult.Success)
        val id = (created as AppResult.Success).value

        val before = repository.observeAll().first().single { it.id == id }
        assertEquals("Calle Olivo 1", before.address)
        assertEquals("https://example.test", before.website)

        val updated = repository.update(
            id,
            OrganizationDraft(
                name = "Cooperativa Sierra",
                roles = setOf(OrganizationRole.COOPERATIVE),
                taxId = "F12345678",
                municipality = "Bedmar",
                province = "Jaén",
                address = before.address,
                phone = "953111111",
                website = before.website,
            ),
        )
        assertEquals(AppResult.Success(Unit), updated)

        val after = repository.observeAll().first().single { it.id == id }
        assertEquals("Calle Olivo 1", after.address)
        assertEquals("https://example.test", after.website)
        assertEquals("953111111", after.phone)

        val row = db.organizationDao().findById(id)!!
        assertEquals("Calle Olivo 1", row.address)
        assertEquals("https://example.test", row.website)
    }

    @Test
    fun catalogsRolesAndMutationsNeverCrossWorkspace() = runBlocking {
        val otherWorkspace = UUID.randomUUID()
        db.workspaceDao().upsert(
            WorkspaceEntity(
                id = otherWorkspace,
                name = "Otro olivar",
                ownerUserId = UUID.randomUUID(),
                countryCode = "ES",
                timezone = "Europe/Madrid",
                locale = "es-ES",
                currency = "EUR",
                metadata = LocalMetadata(now, now),
            ),
        )
        val other = repositoryFor(otherWorkspace)
        val sameName = "Cooperativa Compartida"
        val foreign = ok(
            other.create(
                OrganizationDraft(
                    name = sameName,
                    roles = setOf(OrganizationRole.COOPERATIVE, OrganizationRole.SUPPLIER),
                ),
            ),
        )
        val own = ok(
            repository.create(
                OrganizationDraft(
                    name = sameName,
                    roles = setOf(OrganizationRole.COOPERATIVE),
                ),
            ),
        )

        assertEquals(listOf(own), repository.observeAll().first().map { it.id })
        assertEquals(listOf(foreign), other.observeAll().first().map { it.id })
        assertEquals(
            listOf(own),
            repository.observeWithAnyRole(setOf(OrganizationRole.COOPERATIVE)).first().map { it.id },
        )
        assertEquals(
            listOf(foreign),
            other.observeWithAnyRole(setOf(OrganizationRole.SUPPLIER)).first().map { it.id },
        )

        val before = db.organizationDao().findById(foreign)!!
        val rolesBefore = db.organizationDao().listRoles(foreign)
        val outboxBefore = db.syncOutboxDao().listForEntity(SyncEntityType.ORGANIZATION, foreign)

        assertContextMismatch(
            repository.update(
                foreign,
                OrganizationDraft("No tocar", setOf(OrganizationRole.OTHER)),
            ),
        )
        assertContextMismatch(repository.archive(foreign))

        assertEquals(before, db.organizationDao().findById(foreign))
        assertEquals(rolesBefore, db.organizationDao().listRoles(foreign))
        assertEquals(outboxBefore, db.syncOutboxDao().listForEntity(SyncEntityType.ORGANIZATION, foreign))
    }

    private fun repositoryFor(workspace: UUID): OrganizationRepository =
        OfflineFirstOrganizationRepository(
            db,
            object : WorkspaceRepository {
                override suspend fun ensureLocalWorkspace(): AppResult<UUID> = AppResult.Success(workspace)
            },
            FixedClock(now),
            RandomIds,
            TestDispatchers,
        )

    private fun <T> ok(result: AppResult<T>): T = when (result) {
        is AppResult.Success -> result.value
        is AppResult.Failure -> throw AssertionError("Expected success but was ${result.error}")
    }

    private fun assertContextMismatch(result: AppResult<*>) {
        val error = (result as? AppResult.Failure)?.error
        assertEquals(AppError.Validation("organization", "context_mismatch"), error)
    }

    private data class FixedClock(val value: Instant) : AppClock {
        override fun nowInstant() = value
        override fun today(zoneId: ZoneId): LocalDate = LocalDate.ofInstant(value, zoneId)
    }

    private object RandomIds : IdGenerator {
        override fun newId(): UUID = UUID.randomUUID()
    }

    private object TestDispatchers : AppDispatchers {
        override val default: CoroutineDispatcher = Dispatchers.Unconfined
        override val io: CoroutineDispatcher = Dispatchers.Unconfined
        override val main: CoroutineDispatcher = Dispatchers.Unconfined
    }

    companion object {
        private const val DB = "organization-repository-contract-test.db"
    }
}
