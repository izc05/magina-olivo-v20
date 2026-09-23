package com.isivoltpro.maginaolivo.feature.attachments

import com.isivoltpro.maginaolivo.core.common.AppError
import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.domain.attachment.Attachment
import com.isivoltpro.maginaolivo.domain.attachment.AttachmentKind
import com.isivoltpro.maginaolivo.domain.attachment.AttachmentOwner
import com.isivoltpro.maginaolivo.domain.attachment.AttachmentOwnerType
import com.isivoltpro.maginaolivo.domain.attachment.AttachmentRepository
import com.isivoltpro.maginaolivo.domain.attachment.AttachmentUploadState
import com.isivoltpro.maginaolivo.ui.components.MoStatusTone
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AttachmentsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val owner = AttachmentOwner(AttachmentOwnerType.PARCEL, UUID.fromString("30000000-0000-0000-0000-0000000000f1"))

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun listsAttachesAndRemovesThroughTheRepository() = runTest(dispatcher) {
        val repository = FakeAttachmentRepository()
        val viewModel = AttachmentsViewModel(owner, repository)
        advanceUntilIdle()
        assertFalse(viewModel.state.value.isLoading)
        assertEquals(emptyList<Attachment>(), viewModel.state.value.attachments)

        viewModel.attach("content://picker/olivo.jpg")
        advanceUntilIdle()
        assertEquals(listOf("content://picker/olivo.jpg"), repository.attachedSources)
        assertEquals(1, viewModel.state.value.attachments.size)
        assertEquals(AttachmentsViewModel.ATTACHED_MESSAGE, viewModel.state.value.message)
        assertFalse(viewModel.state.value.isSaving)

        viewModel.remove(viewModel.state.value.attachments.single().id)
        advanceUntilIdle()
        assertEquals(emptyList<Attachment>(), viewModel.state.value.attachments)
        assertEquals(AttachmentsViewModel.REMOVED_MESSAGE, viewModel.state.value.message)
    }

    @Test
    fun aRejectedFileExplainsWhyInsteadOfFailingSilently() = runTest(dispatcher) {
        val repository = FakeAttachmentRepository(
            attachResult = AppResult.Failure(AppError.Validation(field = "file", code = "unsupported_type")),
        )
        val viewModel = AttachmentsViewModel(owner, repository)
        advanceUntilIdle()

        viewModel.attach("content://picker/notas.txt")
        advanceUntilIdle()

        assertEquals("Solo se pueden adjuntar fotos, imágenes y PDF", viewModel.state.value.error)
        assertNull(viewModel.state.value.message)
        assertFalse(viewModel.state.value.isSaving)
    }

    @Test
    fun everyFailureHasAFarmerReadableMessage() {
        assertEquals("El archivo supera el máximo de 50 MB", attachmentErrorMessage(AppError.Validation(code = "file_too_large")))
        assertEquals("El archivo está vacío", attachmentErrorMessage(AppError.Validation(code = "empty_file")))
        assertEquals(
            "Este registro está archivado y no admite adjuntos",
            attachmentErrorMessage(AppError.Validation(code = "archived_owner")),
        )
        assertEquals(
            "No hay permiso para leer ese archivo. Vuelve a elegirlo",
            attachmentErrorMessage(AppError.Permission()),
        )
        assertEquals(
            "No se pudo guardar el adjunto en este dispositivo",
            attachmentErrorMessage(AppError.Storage()),
        )
    }

    @Test
    fun theStatusNeverClaimsAnUploadThatDidNotHappen() {
        val pending = attachment(AttachmentUploadState.PENDING)
        assertEquals("Guardado en el dispositivo" to MoStatusTone.Neutral, pending.statusLabel())
        assertEquals(
            "Subida fallida · guardado aquí" to MoStatusTone.Warning,
            attachment(AttachmentUploadState.FAILED).statusLabel(),
        )
        assertEquals("Sincronizado" to MoStatusTone.Success, attachment(AttachmentUploadState.SYNCED).statusLabel())
        assertEquals(
            "Archivo no disponible" to MoStatusTone.Error,
            pending.copy(isAvailableLocally = false).statusLabel(),
        )
    }

    @Test
    fun sizesReadNaturally() {
        assertEquals("512 B", formatSize(512))
        assertEquals("2 KB", formatSize(2_048))
        assertEquals("1,5 MB", formatSize(1_572_864))
    }

    private fun attachment(uploadState: AttachmentUploadState) = Attachment(
        id = UUID.randomUUID(),
        owner = owner,
        kind = AttachmentKind.PHOTO,
        mimeType = "image/jpeg",
        displayName = "olivo.jpg",
        sizeBytes = 2_048,
        sha256 = null,
        localUri = "file:///data/attachments/olivo.jpg",
        thumbnailUri = null,
        uploadState = uploadState,
        isAvailableLocally = true,
        createdAt = Instant.parse("2026-09-23T08:00:00Z"),
    )

    private inner class FakeAttachmentRepository(
        private val attachResult: AppResult<UUID>? = null,
    ) : AttachmentRepository {
        private val rows = MutableStateFlow<List<Attachment>>(emptyList())
        val attachedSources = mutableListOf<String>()

        override fun observeForOwner(owner: AttachmentOwner): Flow<List<Attachment>> = rows

        override fun observe(id: UUID): Flow<Attachment?> = rows.map { list -> list.firstOrNull { it.id == id } }

        override suspend fun attach(
            owner: AttachmentOwner,
            sourceUri: String,
        ): AppResult<UUID> {
            attachedSources += sourceUri
            attachResult?.let { return it }
            val created = attachment(AttachmentUploadState.PENDING)
            rows.value = rows.value + created
            return AppResult.Success(created.id)
        }

        override suspend fun remove(id: UUID): AppResult<Unit> {
            rows.value = rows.value.filterNot { it.id == id }
            return AppResult.Success(Unit)
        }

        override suspend fun recordUploadFailure(
            id: UUID,
            errorCode: String,
            errorMessage: String?,
        ): AppResult<Unit> = AppResult.Success(Unit)
    }
}
