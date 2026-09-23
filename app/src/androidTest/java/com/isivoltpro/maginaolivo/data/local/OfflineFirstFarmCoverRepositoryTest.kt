package com.isivoltpro.maginaolivo.data.local

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
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
import com.isivoltpro.maginaolivo.data.repository.AndroidAttachmentFileStore
import com.isivoltpro.maginaolivo.domain.farm.NewFarm
import java.io.ByteArrayOutputStream
import java.io.File
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
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfflineFirstFarmCoverRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private val attachments = File(context.filesDir, AndroidAttachmentFileStore.ROOT_DIRECTORY)
    private val sources = File(context.cacheDir, "camera")

    @Before
    fun clearDatabase() {
        context.deleteDatabase(TEST_DATABASE)
        attachments.deleteRecursively()
        sources.deleteRecursively()
    }

    @After
    fun cleanUp() {
        context.deleteDatabase(TEST_DATABASE)
        attachments.deleteRecursively()
        sources.deleteRecursively()
    }

    @Test
    fun copiedCoverAndOutboxSurviveRestart() = runBlocking {
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
                fileStore = AndroidAttachmentFileStore(context),
                clock = FixedClock(now.plusSeconds(30)),
                idGenerator = FixedIds(
                    documentId,
                    uuid("50000000-0000-0000-0000-000000000070"),
                    uuid("50000000-0000-0000-0000-000000000071"),
                ),
                dispatchers = TestDispatchers,
            )

            val bytes = jpeg()
            val source = File(sources.apply { mkdirs() }, "olivar.jpg").apply { writeBytes(bytes) }
            val sourceUri = FileProvider.getUriForFile(context, "${context.packageName}.attachments", source)
            assertEquals(
                AppResult.Success(Unit),
                coverRepository.attachCover(farmId, sourceUri.toString()),
            )
            // The cover is the app's own copy, not the picked file.
            assertTrue(source.delete())
            val coverUri = coverRepository.observeCoverUri(farmId).first()!!
            assertEquals("file", Uri.parse(coverUri).scheme)
            assertArrayEquals(bytes, File(Uri.parse(coverUri).path!!).readBytes())
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
            val coverUri = reopened.documentDao().observeFarmCoverUri(farmId).first()!!
            assertTrue(File(Uri.parse(coverUri).path!!).isFile)
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

    private fun jpeg(): ByteArray {
        val bitmap = Bitmap.createBitmap(64, 48, Bitmap.Config.ARGB_8888)
        return ByteArrayOutputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)
            bitmap.recycle()
            output.toByteArray()
        }
    }

    private fun uuid(value: String): UUID = UUID.fromString(value)

    private companion object {
        const val TEST_DATABASE = "farm-cover-test.db"
    }
}
