package com.isivoltpro.maginaolivo.feature.attachments

import com.isivoltpro.maginaolivo.domain.attachment.*
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class AttachmentPresentationTest {
    private val zone = ZoneId.of("Europe/Madrid")
    private fun photo(id: String = "00000000-0000-0000-0000-000000000001", name: String = "captura-12345678-1234-1234-1234-123456789abc.jpg", type: AttachmentOwnerType = AttachmentOwnerType.DELIVERY) = Attachment(
        UUID.fromString(id), AttachmentOwner(type, UUID(0, 9)), AttachmentKind.PHOTO,
        "image/jpeg", name, null, null, "content://private/photo", null,
        AttachmentUploadState.LOCAL_ONLY, true, Instant.parse("2026-10-05T10:00:00Z"),
    )

    @Test fun internalPhotosUseOwnerContextWithoutChangingStoredNames() {
        val original = photo()
        assertEquals("Foto del vale · 5 oct 2026", attachmentLabels(listOf(original), zone)[original.id])
        val expense = photo(type = AttachmentOwnerType.EXPENSE)
        assertEquals("Foto de factura/ticket · 5 oct 2026", attachmentLabels(listOf(expense), zone)[expense.id])
        assertTrue(original.displayName.startsWith("captura-"))
    }
    @Test fun selectedNamesAndLookalikesRemainUnchanged() {
        for (name in listOf("vale-1234.jpg", "factura.pdf", "captura-vacaciones.jpg")) {
            val selected = photo(name = name)
            assertEquals(name, attachmentLabels(listOf(selected), zone)[selected.id])
        }
    }
    @Test fun sameDayPhotosAreNumberedDeterministicallyRegardlessOfListOrder() {
        val first = photo()
        val second = photo(id = "00000000-0000-0000-0000-000000000002")
        val labels = attachmentLabels(listOf(second, first), zone)
        assertEquals("Foto del vale 1 · 5 oct 2026", labels[first.id])
        assertEquals("Foto del vale 2 · 5 oct 2026", labels[second.id])
        assertEquals(labels, attachmentLabels(listOf(first, second), zone))
    }
    @Test fun calendarDateUsesExplicitDisplayZoneAndPdfIsPreserved() {
        val capture = photo(type = AttachmentOwnerType.PARCEL).copy(createdAt = Instant.parse("2026-10-04T23:30:00Z"))
        assertEquals("Foto · 5 oct 2026", attachmentLabels(listOf(capture), zone)[capture.id])
        val pdf = photo().copy(kind = AttachmentKind.PDF)
        assertEquals(pdf.displayName, attachmentLabels(listOf(pdf), zone)[pdf.id])
    }
}
