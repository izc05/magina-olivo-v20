package com.isivoltpro.maginaolivo.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.isivoltpro.maginaolivo.data.local.entity.DocumentEntity
import java.util.UUID
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(document: DocumentEntity)

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
