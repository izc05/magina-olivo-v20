package com.isivoltpro.maginaolivo.data.repository

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.media.ExifInterface
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

class AndroidAttachmentFileStore(
    context: Context,
    private val maxSizeBytes: Long = MAX_SIZE_BYTES,
) : AttachmentFileStore {
    private val contentResolver: ContentResolver = context.contentResolver
    private val root = File(context.filesDir, ROOT_DIRECTORY)
    private val thumbnails = File(root, THUMBNAIL_DIRECTORY)

    override fun importFile(
        sourceUri: String,
        attachmentId: UUID,
        accepts: (mimeType: String) -> Boolean,
    ): StoredAttachmentFile {
        val source = Uri.parse(sourceUri)
        if (source.scheme != ContentResolver.SCHEME_CONTENT) {
            throw RejectedAttachmentException("unsupported_source")
        }
        val (queriedName, _) = queryMetadata(source)
        val mimeType = resolveMimeType(source, queriedName)
            ?.takeIf(accepts)
            ?: throw RejectedAttachmentException("unsupported_type")

        if (!root.isDirectory && !root.mkdirs()) throw IOException("attachment_directory_unavailable")
        val target = File(root, "$attachmentId.${extensionFor(mimeType)}")
        val partial = File(root, "$attachmentId.partial")
        val digest = MessageDigest.getInstance("SHA-256")
        var size = 0L
        try {
            val input = contentResolver.openInputStream(source) ?: throw IOException("unreadable_source")
            input.use { stream ->
                FileOutputStream(partial).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        val read = stream.read(buffer)
                        if (read < 0) break
                        size += read
                        if (size > maxSizeBytes) throw RejectedAttachmentException("file_too_large")
                        digest.update(buffer, 0, read)
                        output.write(buffer, 0, read)
                    }
                    output.fd.sync()
                }
            }
            if (size == 0L) throw RejectedAttachmentException("empty_file")
            if (!partial.renameTo(target)) throw IOException("attachment_rename_failed")
        } catch (error: Throwable) {
            partial.delete()
            target.delete()
            throw error
        }

        runCatching { writeThumbnail(target, mimeType, attachmentId) }

        return StoredAttachmentFile(
            localUri = Uri.fromFile(target).toString(),
            mimeType = mimeType,
            displayName = queriedName?.takeIf(String::isNotBlank) ?: defaultName(mimeType),
            sizeBytes = size,
            sha256 = digest.digest().joinToString("") { byte -> "%02x".format(byte) },
        )
    }

    override fun thumbnailUri(attachmentId: UUID): String? =
        thumbnailFile(attachmentId).takeIf(File::isFile)?.let { Uri.fromFile(it).toString() }

    override fun exists(localUri: String): Boolean {
        val file = ownedFile(localUri) ?: return true
        return file.isFile
    }

    override fun delete(
        localUri: String,
        attachmentId: UUID,
    ) {
        ownedFile(localUri)?.delete()
        thumbnailFile(attachmentId).delete()
    }

    /** Only files inside the attachment directory are the store's to answer for. */
    private fun ownedFile(localUri: String): File? {
        val uri = Uri.parse(localUri)
        if (uri.scheme != ContentResolver.SCHEME_FILE) return null
        val file = File(uri.path ?: return null).canonicalFile
        return file.takeIf { it.parentFile == root.canonicalFile }
    }

    private fun thumbnailFile(attachmentId: UUID) = File(thumbnails, "$attachmentId.jpg")

    private fun resolveMimeType(
        source: Uri,
        displayName: String?,
    ): String? {
        val reported = contentResolver.getType(source)?.lowercase()
        if (!reported.isNullOrBlank() && reported != GENERIC_BINARY) return reported
        val extension = displayName?.substringAfterLast('.', "")?.lowercase().orEmpty()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
    }

    private fun queryMetadata(source: Uri): Pair<String?, Long?> =
        contentResolver.query(
            source,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null,
        )?.use(::readMetadata) ?: (source.lastPathSegment to null)

    private fun readMetadata(cursor: Cursor): Pair<String?, Long?> {
        if (!cursor.moveToFirst()) return null to null
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
        val name = if (nameIndex >= 0 && !cursor.isNull(nameIndex)) cursor.getString(nameIndex) else null
        val size = if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex) else null
        return name to size
    }

    private fun writeThumbnail(
        file: File,
        mimeType: String,
        attachmentId: UUID,
    ) {
        val bitmap = when {
            mimeType.startsWith("image/") -> decodeImageThumbnail(file)
            mimeType == PDF_MIME_TYPE -> renderPdfThumbnail(file)
            else -> null
        } ?: return
        if (!thumbnails.isDirectory && !thumbnails.mkdirs()) return
        val target = thumbnailFile(attachmentId)
        val partial = File(thumbnails, "$attachmentId.partial")
        try {
            FileOutputStream(partial).use { output -> bitmap.compress(Bitmap.CompressFormat.JPEG, 82, output) }
            if (!partial.renameTo(target)) partial.delete()
        } finally {
            bitmap.recycle()
        }
    }

    private fun decodeImageThumbnail(file: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= THUMBNAIL_EDGE_PX) sample *= 2
        val decoded = BitmapFactory.decodeFile(
            file.path,
            BitmapFactory.Options().apply { inSampleSize = sample },
        ) ?: return null
        return orient(scaleToEdge(decoded), file)
    }

    private fun renderPdfThumbnail(file: File): Bitmap? {
        val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        try {
            val renderer = PdfRenderer(descriptor)
            try {
                if (renderer.pageCount == 0) return null
                val page = renderer.openPage(0)
                try {
                    val scale = THUMBNAIL_EDGE_PX.toFloat() / max(page.width, page.height)
                    val bitmap = Bitmap.createBitmap(
                        (page.width * scale).roundToInt().coerceAtLeast(1),
                        (page.height * scale).roundToInt().coerceAtLeast(1),
                        Bitmap.Config.ARGB_8888,
                    )
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    return bitmap
                } finally {
                    page.close()
                }
            } finally {
                renderer.close()
            }
        } finally {
            descriptor.close()
        }
    }

    private fun scaleToEdge(bitmap: Bitmap): Bitmap {
        val edge = max(bitmap.width, bitmap.height)
        if (edge <= THUMBNAIL_EDGE_PX) return bitmap
        val scale = THUMBNAIL_EDGE_PX.toFloat() / edge
        val scaled = Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).roundToInt().coerceAtLeast(1),
            (bitmap.height * scale).roundToInt().coerceAtLeast(1),
            true,
        )
        if (scaled !== bitmap) bitmap.recycle()
        return scaled
    }

    /** Camera photos are usually stored sideways with an EXIF rotation. */
    private fun orient(
        bitmap: Bitmap,
        file: File,
    ): Bitmap {
        val degrees = when (
            runCatching {
                ExifInterface(file.path).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        ) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> return bitmap
        }
        val rotated = Bitmap.createBitmap(
            bitmap,
            0,
            0,
            bitmap.width,
            bitmap.height,
            Matrix().apply { postRotate(degrees) },
            true,
        )
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }

    private fun extensionFor(mimeType: String): String =
        MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
            ?.takeIf { it.isNotBlank() && it.all(Char::isLetterOrDigit) }
            ?: if (mimeType == PDF_MIME_TYPE) "pdf" else "bin"

    private fun defaultName(mimeType: String): String =
        if (mimeType == PDF_MIME_TYPE) "Documento.pdf" else "Foto.${extensionFor(mimeType)}"

    companion object {
        const val ROOT_DIRECTORY = "attachments"
        const val THUMBNAIL_DIRECTORY = "thumbnails"

        /** Protects the device's storage from an accidental video-sized pick. */
        const val MAX_SIZE_BYTES = 50L * 1024 * 1024

        private const val THUMBNAIL_EDGE_PX = 320
        private const val BUFFER_SIZE = 64 * 1024
        private const val PDF_MIME_TYPE = "application/pdf"
        private const val GENERIC_BINARY = "application/octet-stream"
    }
}
