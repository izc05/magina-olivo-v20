package com.isivoltpro.maginaolivo.data.repository

import java.util.UUID

/** The app-owned copy of an attachment binary, as written by [AttachmentFileStore.importFile]. */
data class StoredAttachmentFile(
    val localUri: String,
    val mimeType: String,
    val displayName: String,
    val sizeBytes: Long,
    val sha256: String,
)

/** A file the store refuses on purpose. [code] is the validation code the UI reads. */
class RejectedAttachmentException(val code: String) : IllegalArgumentException(code)

/**
 * Owns attachment binaries on the device.
 *
 * `importFile` copies the picked file into app-private storage under the attachment's own id,
 * hashing it on the way, so the attachment outlives the original file, a revoked picker
 * grant and a failed upload. Thumbnails are derived data: they are regenerated on import
 * and their absence is never an error.
 */
interface AttachmentFileStore {
    fun importFile(
        sourceUri: String,
        attachmentId: UUID,
        accepts: (mimeType: String) -> Boolean,
    ): StoredAttachmentFile

    fun thumbnailUri(attachmentId: UUID): String?

    /** False only when an app-owned copy is known to be missing. */
    fun exists(localUri: String): Boolean

    /** Releases the app-owned copy and its thumbnail. Foreign URIs are never touched. */
    fun delete(
        localUri: String,
        attachmentId: UUID,
    )
}
