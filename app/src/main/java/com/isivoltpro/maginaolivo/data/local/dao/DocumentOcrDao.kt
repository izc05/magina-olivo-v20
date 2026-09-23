package com.isivoltpro.maginaolivo.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.isivoltpro.maginaolivo.data.local.entity.DocumentOcrExtractionEntity
import java.util.UUID
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentOcrDao {
    @Upsert suspend fun upsert(extraction: DocumentOcrExtractionEntity)

    @Query("SELECT * FROM document_ocr_extractions WHERE id = :id LIMIT 1")
    suspend fun findById(id: UUID): DocumentOcrExtractionEntity?

    @Query("SELECT * FROM document_ocr_extractions WHERE id = :id LIMIT 1")
    fun observeById(id: UUID): Flow<DocumentOcrExtractionEntity?>

    /** Documents still waiting for a person: never CONFIRMED, never discarded. */
    @Query(
        """
        SELECT * FROM document_ocr_extractions
        WHERE deleted_at IS NULL AND status != 'CONFIRMED'
        ORDER BY created_at DESC, id
        """,
    )
    fun observeOpen(): Flow<List<DocumentOcrExtractionEntity>>

    @Query(
        """
        SELECT * FROM document_ocr_extractions
        WHERE deleted_at IS NULL
        ORDER BY created_at DESC, id
        LIMIT 30
        """,
    )
    fun observeRecent(): Flow<List<DocumentOcrExtractionEntity>>
}
