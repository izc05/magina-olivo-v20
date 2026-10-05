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
import com.isivoltpro.maginaolivo.data.repository.OfflineFirstProfileRepository
import com.isivoltpro.maginaolivo.domain.feed.FeedLocation
import com.isivoltpro.maginaolivo.domain.organization.OrganizationDraft
import com.isivoltpro.maginaolivo.domain.organization.OrganizationRole
import com.isivoltpro.maginaolivo.domain.profile.ProfileDraft
import com.isivoltpro.maginaolivo.domain.profile.ProfileSettings
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

/**
 * Phase 21A — «Mi perfil»: municipality and preferred cooperative on the phone first.
 *
 * Gate 21 (part): the profile persists across a restart, the cooperative is a live reference
 * (a rename shows, an archived one is no longer preferred) and each save is queued for sync.
 */
@RunWith(AndroidJUnit4::class)
class ProfileSettingsContractTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val workspaceId = UUID.fromString("10000000-0000-0000-0000-0000000021a1")
    private val now = Instant.parse("2026-10-02T08:00:00Z")

    private lateinit var db: MaginaOlivoDatabase
    private lateinit var organizations: OfflineFirstOrganizationRepository
    private lateinit var profile: OfflineFirstProfileRepository

    @Before
    fun before() = runBlocking {
        context.deleteDatabase(DB)
        open()
        db.workspaceDao().upsert(
            WorkspaceEntity(workspaceId, "Olivar", UUID.randomUUID(), "ES", "Europe/Madrid", "es-ES", "EUR", LocalMetadata(now, now)),
        )
    }

    @After
    fun after() {
        db.close()
        context.deleteDatabase(DB)
    }

    @Test
    fun anUnsetProfileClaimsNothing() = runBlocking {
        assertEquals(ProfileSettings(), profile.observe().first())
        assertNull(profile.observe().first().location)
    }

    @Test
    fun theProfilePersistsAcrossARestartAndEachSaveIsQueuedForSync() = runBlocking {
        val cooperative = ok(organizations.create(OrganizationDraft("Coop. Santa Isabel", setOf(OrganizationRole.COOPERATIVE))))
        ok(profile.save(ProfileDraft("  Bedmar ", "Jaén", cooperative)))
        ok(profile.save(ProfileDraft("Bedmar", "Jaén", cooperative)))

        db.close()
        open()

        val saved = profile.observe().first()
        assertEquals("Bedmar", saved.municipality)
        assertEquals("Jaén", saved.province)
        assertEquals(cooperative, saved.preferredCooperative?.id)
        assertEquals(FeedLocation("Bedmar", "Jaén"), saved.location)
        // One row per workspace, updated in place; one pending outbox entry for it.
        assertEquals(1, count("profile_settings"))
        assertEquals(1, count("sync_outbox WHERE entity_type = '${SyncEntityType.PROFILE_SETTINGS.name}'"))
    }

    @Test
    fun theCooperativeIsALiveReferenceRenamedOrArchived() = runBlocking {
        val cooperative = ok(organizations.create(OrganizationDraft("Coop. Santa Isabel", setOf(OrganizationRole.COOPERATIVE))))
        ok(profile.save(ProfileDraft("Bedmar", "Jaén", cooperative)))

        ok(organizations.update(cooperative, OrganizationDraft("S.C.A. Santa Isabel", setOf(OrganizationRole.COOPERATIVE))))
        assertEquals("S.C.A. Santa Isabel", profile.observe().first().preferredCooperative?.name)

        ok(organizations.archive(cooperative))
        val afterArchive = profile.observe().first()
        assertNull(afterArchive.preferredCooperative)
        // The rest of the profile stays as it was.
        assertEquals("Bedmar", afterArchive.municipality)
    }

    @Test
    fun onlyAnActiveCooperativeOrMillCanBeTheFarmersCooperative() = runBlocking {
        val mill = ok(organizations.create(OrganizationDraft("Almazara El Molino", setOf(OrganizationRole.MILL))))
        ok(profile.save(ProfileDraft(null, null, mill)))
        assertEquals(mill, profile.observe().first().preferredCooperative?.id)

        val supplier = ok(organizations.create(OrganizationDraft("Agrosuministros", setOf(OrganizationRole.SUPPLIER))))
        val refused = profile.save(ProfileDraft(null, null, supplier))
        assertEquals(AppError.Validation("organization", "not_cooperative"), (refused as AppResult.Failure).error)

        val unknown = profile.save(ProfileDraft(null, null, UUID.randomUUID()))
        assertEquals(AppError.NotFound("organization"), (unknown as AppResult.Failure).error)

        // A province alone names no place.
        val provinceOnly = profile.save(ProfileDraft(" ", "Jaén", null))
        assertEquals(AppError.Validation("municipality", "blank"), (provinceOnly as AppResult.Failure).error)
        // The refused saves left the profile as it was.
        assertEquals(mill, profile.observe().first().preferredCooperative?.id)
    }

    @Test
    fun preferredCooperativeFromAnotherWorkspaceIsRejectedWithoutChangingProfile() = runBlocking {
        val own = ok(organizations.create(OrganizationDraft("Coop. propia", setOf(OrganizationRole.COOPERATIVE))))
        ok(profile.save(ProfileDraft("Bedmar", "Jaén", own)))
        val profileRowsBefore = count("profile_settings")
        val profileOutboxBefore = count("sync_outbox WHERE entity_type = 'PROFILE_SETTINGS'")

        val otherWorkspaceId = UUID.randomUUID()
        db.workspaceDao().upsert(
            WorkspaceEntity(otherWorkspaceId, "Otro olivar", UUID.randomUUID(), "ES", "Europe/Madrid", "es-ES", "EUR", LocalMetadata(now, now)),
        )
        val otherWorkspaces = object : WorkspaceRepository {
            override suspend fun ensureLocalWorkspace(): AppResult<UUID> = AppResult.Success(otherWorkspaceId)
        }
        val foreignOrganizations = OfflineFirstOrganizationRepository(db, otherWorkspaces, FixedClock(now), RandomIds, TestDispatchers)
        val foreign = ok(foreignOrganizations.create(OrganizationDraft("Coop. ajena", setOf(OrganizationRole.COOPERATIVE))))

        val refused = profile.save(ProfileDraft("Bedmar", "Jaén", foreign))
        assertEquals(AppError.Validation("organization", "context_mismatch"), (refused as AppResult.Failure).error)
        assertEquals(own, profile.observe().first().preferredCooperative?.id)
        assertEquals(profileRowsBefore, count("profile_settings"))
        assertEquals(profileOutboxBefore, count("sync_outbox WHERE entity_type = 'PROFILE_SETTINGS'"))
    }

    @Test
    fun clearingTheProfileLeavesNothingBehind() = runBlocking {
        val cooperative = ok(organizations.create(OrganizationDraft("Coop. Santa Isabel", setOf(OrganizationRole.COOPERATIVE))))
        ok(profile.save(ProfileDraft("Bedmar", "Jaén", cooperative)))
        ok(profile.save(ProfileDraft(" ", null, null)))
        assertEquals(ProfileSettings(), profile.observe().first())
        assertTrue(count("profile_settings") == 1)
    }

    private fun open() {
        db = MaginaOlivoDatabase.create(context, DB)
        val clock = FixedClock(now)
        val workspaces = object : WorkspaceRepository {
            override suspend fun ensureLocalWorkspace(): AppResult<UUID> = AppResult.Success(workspaceId)
        }
        organizations = OfflineFirstOrganizationRepository(db, workspaces, clock, RandomIds, TestDispatchers)
        profile = OfflineFirstProfileRepository(db, workspaces, organizations, clock, RandomIds, TestDispatchers)
    }

    private fun count(tableAndFilter: String): Int =
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $tableAndFilter").use { it.moveToFirst(); it.getInt(0) }

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
        const val DB = "profile-settings-contract-test.db"
    }
}
