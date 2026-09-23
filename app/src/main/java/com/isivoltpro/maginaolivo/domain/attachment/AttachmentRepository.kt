package com.isivoltpro.maginaolivo.domain.attachment

import com.isivoltpro.maginaolivo.core.common.AppResult
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.Flow

/**
 * Phase 11 — local attachment lifecycle.
 *
 * An attachment is its own aggregate (`RC1-NORMATIVE-ADDENDUM` D10): it points at an owner
 * but never moves that owner's version, and it queues its own upload intent. The binary is
 * copied into app-private storage before the row exists, so the attachment never depends
 * on the file the user picked still being there, and nothing in the upload lifecycle may
 * remove that copy (`OFFLINE-SYNC-CONTRACT-RC1` §18).
 */
enum class AttachmentOwnerType {
    FARM,
    PARCEL,
    CAMPAIGN,
    ACTIVITY,
}

data class AttachmentOwner(
    val type: AttachmentOwnerType,
    val id: UUID,
)

enum class AttachmentKind {
    PHOTO,
    PDF,
    ;

    companion object {
        /** The file types Phase 11 accepts. Anything else is rejected, never guessed. */
        fun fromMimeType(mimeType: String?): AttachmentKind? {
            val normalized = mimeType?.trim()?.lowercase() ?: return null
            return when {
                normalized.startsWith("image/") && normalized.length > "image/".length -> PHOTO
                normalized == PDF_MIME_TYPE -> PDF
                else -> null
            }
        }

        const val PDF_MIME_TYPE = "application/pdf"
        val PICKER_MIME_TYPES = arrayOf("image/*", PDF_MIME_TYPE)
    }
}

enum class AttachmentUploadState {
    LOCAL_ONLY,
    PENDING,
    UPLOADING,
    SYNCED,
    FAILED,
    ;

    companion object {
        fun fromStored(value: String): AttachmentUploadState =
            entries.firstOrNull { it.name == value } ?: PENDING
    }
}

data class Attachment(
    val id: UUID,
    val owner: AttachmentOwner,
    val kind: AttachmentKind,
    val mimeType: String,
    val displayName: String,
    val sizeBytes: Long?,
    val sha256: String?,
    val localUri: String,
    val thumbnailUri: String?,
    val uploadState: AttachmentUploadState,
    val isAvailableLocally: Boolean,
    val createdAt: Instant,
)

interface AttachmentRepository {
    fun observeForOwner(owner: AttachmentOwner): Flow<List<Attachment>>

    fun observe(id: UUID): Flow<Attachment?>

    /** Copies [sourceUri] into app storage, then records it and queues its upload. */
    suspend fun attach(
        owner: AttachmentOwner,
        sourceUri: String,
    ): AppResult<UUID>

    /** Confirmed user removal: a tombstone is queued and the local copy is released. */
    suspend fun remove(id: UUID): AppResult<Unit>

    /**
     * What a future uploader reports when an attempt fails. It records the failure and
     * keeps the local reference and the local file exactly as they were.
     */
    suspend fun recordUploadFailure(
        id: UUID,
        errorCode: String,
        errorMessage: String? = null,
    ): AppResult<Unit>
}
