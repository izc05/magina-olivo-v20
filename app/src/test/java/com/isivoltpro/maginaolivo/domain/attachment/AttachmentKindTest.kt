package com.isivoltpro.maginaolivo.domain.attachment

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AttachmentKindTest {
    @Test
    fun imagesArePhotosWhateverTheirEncoding() {
        listOf("image/jpeg", "image/png", "image/heic", "IMAGE/WEBP", " image/jpeg ").forEach { mimeType ->
            assertEquals(mimeType, AttachmentKind.PHOTO, AttachmentKind.fromMimeType(mimeType))
        }
    }

    @Test
    fun pdfIsTheOnlyDocumentTypeAccepted() {
        assertEquals(AttachmentKind.PDF, AttachmentKind.fromMimeType("application/pdf"))
    }

    @Test
    fun anythingElseIsRejectedRatherThanGuessed() {
        listOf(null, "", "image/", "text/plain", "application/octet-stream", "video/mp4", "application/zip")
            .forEach { mimeType -> assertNull(mimeType, AttachmentKind.fromMimeType(mimeType)) }
    }

    @Test
    fun thePickerOffersExactlyWhatIsAccepted() {
        assertEquals(listOf("image/*", "application/pdf"), AttachmentKind.PICKER_MIME_TYPES.toList())
    }

    @Test
    fun anUnknownStoredUploadStateReadsAsPendingNeverAsSynced() {
        assertEquals(AttachmentUploadState.FAILED, AttachmentUploadState.fromStored("FAILED"))
        assertEquals(AttachmentUploadState.PENDING, AttachmentUploadState.fromStored("SOMETHING_NEW"))
    }
}
