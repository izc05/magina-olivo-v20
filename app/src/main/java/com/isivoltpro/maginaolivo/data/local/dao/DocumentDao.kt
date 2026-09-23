package com.isivoltpro.maginaolivo.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.isivoltpro.maginaolivo.data.local.entity.DocumentEntity
import java.util.UUID
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(document: DocumentEntity)

    @Update
    suspend fun update(document: DocumentEntity)

    @Query("SELECT * FROM documents WHERE id = :id LIMIT 1")
    suspend fun findById(id: UUID): DocumentEntity?

    @Query("SELECT * FROM documents WHERE id = :id AND deleted_at IS NULL LIMIT 1")
    fun observeById(id: UUID): Flow<DocumentEntity?>

    @Query(
        """
        SELECT * FROM documents
        WHERE owner_type = :ownerType AND owner_id = :ownerId AND deleted_at IS NULL
        ORDER BY created_at DESC, id
        """,
    )
    fun observeForOwner(ownerType: String, ownerId: UUID): Flow<List<DocumentEntity>>

    @Query(
        """
        SELECT d.local_uri
        FROM farms f
        LEFT JOIN documents d ON d.id = f.cover_document_id AND d.deleted_at IS NULL
        WHERE f.id = :farmId
        LIMIT 1
        """,
    )
    fun observeFarmCoverUri(farmId: UUID): Flow<String?>
}
