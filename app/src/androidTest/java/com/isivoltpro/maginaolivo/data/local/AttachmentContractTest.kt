package com.isivoltpro.maginaolivo.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.isivoltpro.maginaolivo.core.common.AppError
import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.core.dispatchers.AppDispatchers
import com.isivoltpro.maginaolivo.core.id.IdGenerator
import com.isivoltpro.maginaolivo.core.time.AppClock
import com.isivoltpro.maginaolivo.data.local.entity.FarmEntity
import com.isivoltpro.maginaolivo.data.local.entity.FarmParcelMembershipEntity
import com.isivoltpro.maginaolivo.data.local.entity.LocalMetadata
import com.isivoltpro.maginaolivo.data.local.entity.ParcelEntity
import com.isivoltpro.maginaolivo.data.local.entity.WorkspaceEntity
import com.isivoltpro.maginaolivo.data.local.model.OutboxOperation
import com.isivoltpro.maginaolivo.data.local.model.OutboxStatus
import com.isivoltpro.maginaolivo.data.local.model.SyncEntityType
import com.isivoltpro.maginaolivo.data.repository.AndroidAttachmentFileStore
import com.isivoltpro.maginaolivo.data.repository.OfflineFirstActivityRepository
import com.isivoltpro.maginaolivo.data.repository.OfflineFirstAttachmentRepository
import com.isivoltpro.maginaolivo.data.repository.OfflineFirstFarmCoverRepository
import com.isivoltpro.maginaolivo.domain.activity.ActivityType
import com.isivoltpro.maginaolivo.domain.activity.NewActivity
import com.isivoltpro.maginaolivo.domain.attachment.AttachmentKind
import com.isivoltpro.maginaolivo.domain.attachment.AttachmentOwner
import com.isivoltpro.maginaolivo.domain.attachment.AttachmentOwnerType
import com.isivoltpro.maginaolivo.domain.attachment.AttachmentRepository
import com.isivoltpro.maginaolivo.domain.attachment.AttachmentUploadState
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Phase 11 — attachment contract.
 *
 * Gate 11: an attachment survives an app restart, and a failed upload cannot destroy the
 * local reference. Every source here goes through the app's own FileProvider, which is
 * the same path a camera capture takes.
 */
@RunWith(AndroidJUnit4::class)
class AttachmentContractTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val workspaceId = UUID.fromString("10000000-0000-0000-0000-0000000000e1")
    private val farmId = UUID.fromString("20000000-0000-0000-0000-0000000000e1")
    private val parcelA = UUID.fromString("30000000-0000-0000-0000-0000000000e1")
    private val parcelB = UUID.fromString("30000000-0000-0000-0000-0000000000e2")
    private val now = Instant.parse("2026-09-23T08:00:00Z")
    private val attachmentsDirectory = File(context.filesDir, AndroidAttachmentFileStore.ROOT_DIRECTORY)
    private val sourceDirectory = File(context.cacheDir, "camera")

    private val farm = AttachmentOwner(AttachmentOwnerType.FARM, farmId)
    private val parcel = AttachmentOwner(AttachmentOwnerType.PARCEL, parcelA)

    private lateinit var db: MaginaOlivoDatabase
    private lateinit var repository: AttachmentRepository

    @Before
    fun before() = runBlocking {
        context.deleteDatabase(DB)
        attachmentsDirectory.deleteRecursively()
        sourceDirectory.deleteRecursively()
        db = MaginaOlivoDatabase.create(context, DB)
        repository = newRepository(db)
        seed()
    }

    @After
    fun after() {
        db.close()
        context.deleteDatabase(DB)
        attachmentsDirectory.deleteRecursively()
        sourceDirectory.deleteRecursively()
    }

    // ------------------------------------------------------------- local copy

    @Test
    fun aPhotoIsCopiedIntoAppStorageWithItsHashAndSize() = runBlocking {
        val bytes = jpeg()
        val id = attached(parcel, source("olivo.jpg", bytes))

        val attachment = repository.observe(id).first()!!
        val copy = fileOf(attachment.localUri)
        assertEquals(attachmentsDirectory.canonicalFile, copy.canonicalFile.parentFile)
        assertArrayEquals(bytes, copy.readBytes())
        assertEquals(sha256(bytes), attachment.sha256)
        assertEquals(bytes.size.toLong(), attachment.sizeBytes)
        assertEquals(AttachmentKind.PHOTO, attachment.kind)
        assertEquals("image/jpeg", attachment.mimeType)
        assertEquals("olivo.jpg", attachment.displayName)
        assertEquals(AttachmentUploadState.PENDING, attachment.uploadState)
        assertTrue(attachment.isAvailableLocally)
    }

    @Test
    fun theAttachmentOutlivesTheFileTheUserPicked() = runBlocking {
        val bytes = jpeg()
        val sourceFile = File(sourceDirectory, "temporal.jpg")
        val id = attached(parcel, source(sourceFile.name, bytes))

        assertTrue(sourceFile.delete())

        val attachment = repository.observe(id).first()!!
        assertTrue(attachment.isAvailableLocally)
        assertArrayEquals(bytes, fileOf(attachment.localUri).readBytes())
    }

    @Test
    fun photosAndPdfsGetADerivedThumbnail() = runBlocking {
        val photo = repository.observe(attached(parcel, source("hoja.jpg", jpeg()))).first()!!
        val pdf = repository.observe(attached(parcel, source("analisis.pdf", pdf()))).first()!!

        assertEquals(AttachmentKind.PDF, pdf.kind)
        assertEquals("application/pdf", pdf.mimeType)
        listOf(photo, pdf).forEach { attachment ->
            val thumbnail = fileOf(assertNotNullValue(attachment.thumbnailUri))
            assertTrue(thumbnail.length() > 0)
        }
    }

    // ---------------------------------------------------------------- Gate 11

    @Test
    fun anAttachmentSurvivesAnAppRestart() = runBlocking {
        val bytes = pdf()
        val id = attached(farm, source("escritura.pdf", bytes))
        db.close()

        db = MaginaOlivoDatabase.create(context, DB)
        val restarted = newRepository(db)
        val attachment = restarted.observeForOwner(farm).first().single()
        assertEquals(id, attachment.id)
        assertEquals("escritura.pdf", attachment.displayName)
        assertTrue(attachment.isAvailableLocally)
        assertArrayEquals(bytes, fileOf(attachment.localUri).readBytes())
        assertNotNull(attachment.thumbnailUri)
    }

    @Test
    fun aFailedUploadNeverDestroysTheLocalReference() = runBlocking {
        val bytes = jpeg()
        val id = attached(parcel, source("poda.jpg", bytes))
        val before = repository.observe(id).first()!!

        assertOk(repository.recordUploadFailure(id, "network_timeout", "Tiempo de espera agotado"))
        assertOk(repository.recordUploadFailure(id, "http_503"))

        val after = repository.observe(id).first()!!
        assertEquals(before.localUri, after.localUri)
        assertEquals(before.sha256, after.sha256)
        assertEquals(AttachmentUploadState.FAILED, after.uploadState)
        assertTrue(after.isAvailableLocally)
        assertArrayEquals(bytes, fileOf(after.localUri).readBytes())
        assertEquals(listOf(id), repository.observeForOwner(parcel).first().map { it.id })

        val intent = db.syncOutboxDao().listForEntity(SyncEntityType.DOCUMENT, id).single()
        assertEquals(OutboxOperation.UPLOAD_ATTACHMENT, intent.operation)
        assertEquals(OutboxStatus.FAILED, intent.status)
        assertEquals(2, intent.attemptCount)
        assertEquals("http_503", intent.lastErrorCode)

        // A failure recorded is still a failure after the process dies.
        db.close()
        db = MaginaOlivoDatabase.create(context, DB)
        val restarted = newRepository(db).observe(id).first()!!
        assertEquals(before.localUri, restarted.localUri)
        assertEquals(AttachmentUploadState.FAILED, restarted.uploadState)
        assertTrue(restarted.isAvailableLocally)
    }

    // --------------------------------------------------------------- aggregate

    @Test
    fun eachAttachmentQueuesOneUploadIntentAndNoneForItsOwner() = runBlocking {
        val activityId = activity()
        val activityIntents = db.syncOutboxDao().listForEntity(SyncEntityType.ACTIVITY, activityId)
        val activityVersion = db.activityDao().findById(activityId)!!.metadata.version
        val owner = AttachmentOwner(AttachmentOwnerType.ACTIVITY, activityId)

        val first = attached(owner, source("antes.jpg", jpeg()))
        val second = attached(owner, source("despues.jpg", jpeg()))

        listOf(first, second).forEach { id ->
            assertEquals(
                listOf(OutboxOperation.UPLOAD_ATTACHMENT),
                db.syncOutboxDao().listForEntity(SyncEntityType.DOCUMENT, id).map { it.operation },
            )
        }
        assertEquals(activityIntents, db.syncOutboxDao().listForEntity(SyncEntityType.ACTIVITY, activityId))
        assertEquals(activityVersion, db.activityDao().findById(activityId)!!.metadata.version)
        assertEquals(setOf(first, second), repository.observeForOwner(owner).first().map { it.id }.toSet())
    }

    @Test
    fun attachmentsAreListedOnlyUnderTheirOwner() = runBlocking {
        val onParcelA = attached(parcel, source("a.jpg", jpeg()))
        val onFarm = attached(farm, source("finca.pdf", pdf()))

        assertEquals(listOf(onParcelA), repository.observeForOwner(parcel).first().map { it.id })
        assertEquals(listOf(onFarm), repository.observeForOwner(farm).first().map { it.id })
        assertTrue(
            repository.observeForOwner(AttachmentOwner(AttachmentOwnerType.PARCEL, parcelB)).first().isEmpty(),
        )
    }

    // -------------------------------------------------------------- rejection

    @Test
    fun anUnsupportedFileIsRejectedWithoutARowAFileOrAnIntent() = runBlocking {
        val result = repository.attach(parcel, source("notas.txt", "poda".toByteArray()))

        assertValidation("unsupported_type", result)
        assertTrue(repository.observeForOwner(parcel).first().isEmpty())
        assertTrue(storedFiles().isEmpty())
        assertTrue(db.syncOutboxDao().observeReady(Long.MAX_VALUE).first().none { it.entityType == SyncEntityType.DOCUMENT })
    }

    @Test
    fun anEmptyFileIsRejected() = runBlocking {
        assertValidation("empty_file", repository.attach(parcel, source("vacio.jpg", ByteArray(0))))
        assertTrue(storedFiles().isEmpty())
    }

    @Test
    fun aMissingOwnerIsRejectedBeforeAnythingIsCopied() = runBlocking {
        val result = repository.attach(
            AttachmentOwner(AttachmentOwnerType.ACTIVITY, UUID.randomUUID()),
            source("huerfano.jpg", jpeg()),
        )

        assertTrue(result is AppResult.Failure && result.error is AppError.NotFound)
        assertTrue(storedFiles().isEmpty())
    }

    @Test
    fun aFileSchemeSourceIsRefused() = runBlocking {
        val file = File(sourceDirectory.apply { mkdirs() }, "directo.jpg").apply { writeBytes(jpeg()) }

        assertValidation("unsupported_source", repository.attach(parcel, Uri.fromFile(file).toString()))
        assertTrue(storedFiles().isEmpty())
    }

    // ----------------------------------------------------------------- removal

    @Test
    fun removalQueuesATombstoneAndReleasesTheCopy() = runBlocking {
        val id = attached(parcel, source("borrar.jpg", jpeg()))
        val copy = fileOf(repository.observe(id).first()!!.localUri)

        assertOk(repository.remove(id))
        assertOk(repository.remove(id))

        assertNull(repository.observe(id).first())
        assertTrue(repository.observeForOwner(parcel).first().isEmpty())
        assertNotNull(db.documentDao().findById(id)?.metadata?.deletedAt)
        assertEquals(
            listOf(OutboxOperation.DELETE),
            db.syncOutboxDao().listForEntity(SyncEntityType.DOCUMENT, id).map { it.operation },
        )
        assertFalse(copy.exists())
    }

    @Test
    fun theFarmCoverIsACopiedFarmPhotoAndRemovingItClearsTheCover() = runBlocking {
        val covers = OfflineFirstFarmCoverRepository(db, AndroidAttachmentFileStore(context), FixedClock(now), RandomIds, TestDispatchers)
        val bytes = jpeg()
        val sourceFile = File(sourceDirectory, "portada.jpg")

        assertOk(covers.attachCover(farmId, source(sourceFile.name, bytes)))
        assertTrue(sourceFile.delete())

        val coverId = db.farmDao().findById(farmId)!!.coverDocumentId!!
        val coverUri = covers.observeCoverUri(farmId).first()!!
        assertArrayEquals(bytes, fileOf(coverUri).readBytes())
        assertEquals(listOf(coverId), repository.observeForOwner(farm).first().map { it.id })

        assertOk(repository.remove(coverId))

        assertNull(db.farmDao().findById(farmId)!!.coverDocumentId)
        assertNull(covers.observeCoverUri(farmId).first())
        assertEquals(
            OutboxOperation.UPDATE,
            db.syncOutboxDao().listForEntity(SyncEntityType.FARM, farmId).last().operation,
        )
    }

    @Test
    fun aCoverMustBeAnImage() = runBlocking {
        val covers = OfflineFirstFarmCoverRepository(db, AndroidAttachmentFileStore(context), FixedClock(now), RandomIds, TestDispatchers)

        val result = covers.attachCover(farmId, source("plano.pdf", pdf()))

        assertValidation("unsupported_type", result)
        assertNull(db.farmDao().findById(farmId)!!.coverDocumentId)
        assertTrue(storedFiles().isEmpty())
    }

    // ------------------------------------------------------------------ helpers

    private fun newRepository(database: MaginaOlivoDatabase): AttachmentRepository =
        OfflineFirstAttachmentRepository(database, AndroidAttachmentFileStore(context), FixedClock(now), RandomIds, TestDispatchers)

    private suspend fun attached(
        owner: AttachmentOwner,
        uri: String,
    ): UUID = when (val result = repository.attach(owner, uri)) {
        is AppResult.Success -> result.value
        is AppResult.Failure -> throw AssertionError("Attach failed: ${result.error}")
    }

    private suspend fun activity(): UUID {
        val activities = OfflineFirstActivityRepository(db, FixedClock(now), RandomIds, TestDispatchers)
        val result = activities.create(
            NewActivity(farmId, null, ActivityType.PRUNING, LocalDate.parse("2026-02-10"), "Poda", setOf(parcelA)),
        )
        val id = (result as AppResult.Success).value
        assertOk(activities.complete(id))
        return id
    }

    private fun source(
        name: String,
        bytes: ByteArray,
    ): String {
        sourceDirectory.mkdirs()
        val file = File(sourceDirectory, name).apply { writeBytes(bytes) }
        return FileProvider.getUriForFile(context, "${context.packageName}.attachments", file).toString()
    }

    private fun storedFiles(): List<File> =
        attachmentsDirectory.listFiles()?.filter(File::isFile).orEmpty()

    private fun fileOf(uri: String): File = File(Uri.parse(uri).path!!)

    private fun jpeg(): ByteArray {
        val bitmap = Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.rgb(62, 90, 50))
        return ByteArrayOutputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)
            bitmap.recycle()
            output.toByteArray()
        }
    }

    private fun pdf(): ByteArray {
        val document = PdfDocument()
        val page = document.startPage(PdfDocument.PageInfo.Builder(300, 400, 1).create())
        page.canvas.drawRect(20f, 20f, 280f, 120f, Paint().apply { color = Color.rgb(62, 90, 50) })
        document.finishPage(page)
        return ByteArrayOutputStream().use { output ->
            document.writeTo(output)
            document.close()
            output.toByteArray()
        }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun <T> assertNotNullValue(value: T?): T {
        assertNotNull(value)
        return value!!
    }

    private fun assertOk(result: AppResult<*>) {
        assertTrue("Expected success but was $result", result is AppResult.Success)
    }

    private fun assertValidation(
        code: String,
        result: AppResult<*>,
    ) {
        val error = (result as? AppResult.Failure)?.error
        assertTrue("Expected validation $code but was $result", error is AppError.Validation && error.code == code)
    }

    private suspend fun seed() {
        val meta = LocalMetadata(now, now)
        db.workspaceDao().upsert(
            WorkspaceEntity(workspaceId, "Olivar", UUID.randomUUID(), "ES", "Europe/Madrid", "es-ES", "EUR", meta),
        )
        db.farmDao().upsert(FarmEntity(farmId, workspaceId, "Finca principal", metadata = meta))
        db.parcelDao().upsert(
            ParcelEntity(parcelA, workspaceId, "Parcela A", source = "MANUAL", managedAreaM2 = 1200.0, metadata = meta),
        )
        db.parcelDao().upsert(
            ParcelEntity(parcelB, workspaceId, "Parcela B", source = "MANUAL", managedAreaM2 = 800.0, metadata = meta),
        )
        db.parcelDao().upsertMembership(
            FarmParcelMembershipEntity(UUID.randomUUID(), workspaceId, farmId, parcelA, now, metadata = meta),
        )
        db.parcelDao().upsertMembership(
            FarmParcelMembershipEntity(UUID.randomUUID(), workspaceId, farmId, parcelB, now, metadata = meta),
        )
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

    companion object {
        private const val DB = "attachment-contract-test.db"
    }
}
