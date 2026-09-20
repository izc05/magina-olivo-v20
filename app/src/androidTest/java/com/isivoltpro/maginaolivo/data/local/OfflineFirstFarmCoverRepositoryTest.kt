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
import com.isivoltpro.maginaolivo.data.local.model.OutboxOperation
import com.isivoltpro.maginaolivo.data.local.model.SyncEntityType
import com.isivoltpro.maginaolivo.data.repository.OfflineFirstFarmCoverRepository
import com.isivoltpro.maginaolivo.data.repository.OfflineFirstFarmRepository
import com.isivoltpro.maginaolivo.data.repository.PersistedDocument
import com.isivoltpro.maginaolivo.data.repository.PersistedDocumentSource
import com.isivoltpro.maginaolivo.domain.farm.NewFarm
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.ArrayDeque
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfflineFirstFarmCoverRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun clearDatabase() = context.deleteDatabase(TEST_DATABASE)

    @After
    fun cleanUp() = context.deleteDatabase(TEST_DATABASE)

    @Test
    fun retainedCoverAndOutboxSurviveRestart() = runBlocking {
        val workspaceId = uuid("10000000-0000-0000-0000-000000000070")
        val farmId = uuid("20000000-0000-0000-0000-000000000070")
        val documentId = uuid("40000000-0000-0000-0000-000000000070")
        val now = Instant.parse("2026-09-20T08:00:00Z")
        val database = MaginaOlivoDatabase.create(context, TEST_DATABASE)
        try {
            database.workspaceDao().upsert(
                WorkspaceEntity(
                    id = workspaceId,
                    name = "Mi olivar",
                    ownerUserId = uuid("00000000-0000-0000-0000-000000000070"),
                    countryCode = "ES",
                    timezone = "Europe/Madrid",
                    locale = "es-ES",
                    currency = "EUR",
                    metadata = LocalMetadata(now, now),
                ),
            )
            val farmRepository = OfflineFirstFarmRepository(
                database,
                FixedClock(now),
                FixedIds(farmId, uuid("30000000-0000-0000-0000-000000000070")),
                TestDispatchers,
            )
            assertEquals(
                AppResult.Success(farmId),
                farmRepository.create(NewFarm(workspaceId, "La Solana")),
            )
            val coverRepository = OfflineFirstFarmCoverRepository(
                database = database,
                documentSource = FakeDocumentSource,
                clock = FixedClock(now.plusSeconds(30)),
                idGenerator = FixedIds(
                    documentId,
                    uuid("50000000-0000-0000-0000-000000000070"),
                    uuid("50000000-0000-0000-0000-000000000071"),
                ),
                dispatchers = TestDispatchers,
            )

            assertEquals(
                AppResult.Success(Unit),
                coverRepository.attachCover(farmId, COVER_URI),
            )
            assertEquals(COVER_URI, coverRepository.observeCoverUri(farmId).first())
            assertEquals(documentId, database.farmDao().findById(farmId)?.coverDocumentId)
            assertEquals(
                listOf(OutboxOperation.UPLOAD_ATTACHMENT),
                database.syncOutboxDao()
                    .listForEntity(SyncEntityType.DOCUMENT, documentId)
                    .map { it.operation },
            )
            assertEquals(
                listOf(OutboxOperation.CREATE, OutboxOperation.UPDATE),
                database.syncOutboxDao()
                    .listForEntity(SyncEntityType.FARM, farmId)
                    .map { it.operation },
            )
        } finally {
            database.close()
        }

        val reopened = MaginaOlivoDatabase.create(context, TEST_DATABASE)
        try {
            assertNotNull(reopened.farmDao().findById(farmId)?.coverDocumentId)
            assertEquals(COVER_URI, reopened.documentDao().observeFarmCoverUri(farmId).first())
        } finally {
            reopened.close()
        }
    }

    private class FixedIds(vararg ids: UUID) : IdGenerator {
        private val values = ArrayDeque(ids.toList())
        override fun newId(): UUID = values.removeFirst()
    }

    private class FixedClock(private val value: Instant) : AppClock {
        override fun nowInstant(): Instant = value
        override fun today(zoneId: ZoneId): LocalDate = value.atZone(zoneId).toLocalDate()
    }

    private object TestDispatchers : AppDispatchers {
        override val default: CoroutineDispatcher = Dispatchers.Unconfined
        override val io: CoroutineDispatcher = Dispatchers.Unconfined
        override val main: CoroutineDispatcher = Dispatchers.Unconfined
    }

    private object FakeDocumentSource : PersistedDocumentSource {
        override fun retain(uri: String) = PersistedDocument(
            uri = uri,
            mimeType = "image/jpeg",
            displayName = "olivar.jpg",
            sizeBytes = 1_024,
        )
    }

    private fun uuid(value: String): UUID = UUID.fromString(value)

    private companion object {
        const val TEST_DATABASE = "farm-cover-test.db"
        const val COVER_URI = "content://test/olivar.jpg"
    }
}
