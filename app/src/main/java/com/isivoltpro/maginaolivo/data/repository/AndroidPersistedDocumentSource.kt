package com.isivoltpro.maginaolivo.data.repository

import android.content.ContentResolver
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns

data class PersistedDocument(
    val uri: String,
    val mimeType: String,
    val displayName: String,
    val sizeBytes: Long?,
)

interface PersistedDocumentSource {
    fun retain(uri: String): PersistedDocument
}

class AndroidPersistedDocumentSource(
    private val contentResolver: ContentResolver,
) : PersistedDocumentSource {
    override fun retain(uri: String): PersistedDocument {
        val parsed = Uri.parse(uri)
        require(parsed.scheme == ContentResolver.SCHEME_CONTENT) {
            "Only content URIs can be retained"
        }
        contentResolver.takePersistableUriPermission(
            parsed,
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
        val mimeType = contentResolver.getType(parsed).orEmpty()
        require(mimeType.startsWith("image/")) { "Farm covers must be images" }
        val metadata = contentResolver.query(
            parsed,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null,
        )?.use(::readMetadata)
        return PersistedDocument(
            uri = parsed.toString(),
            mimeType = mimeType,
            displayName = metadata?.first?.takeIf(String::isNotBlank) ?: "Portada de finca",
            sizeBytes = metadata?.second,
        )
    }

    private fun readMetadata(cursor: Cursor): Pair<String, Long?>? {
        if (!cursor.moveToFirst()) return null
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
        val name = if (nameIndex >= 0) cursor.getString(nameIndex).orEmpty() else ""
        val size = if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex) else null
        return name to size
    }
}
