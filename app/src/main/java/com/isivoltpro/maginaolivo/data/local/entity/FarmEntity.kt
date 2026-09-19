package com.isivoltpro.maginaolivo.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.isivoltpro.maginaolivo.data.local.model.FarmStatus
import java.util.UUID

@Entity(
    tableName = "farms",
    foreignKeys = [
        ForeignKey(
            entity = WorkspaceEntity::class,
            parentColumns = ["id"],
            childColumns = ["workspace_id"],
            onDelete = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [
        Index(value = ["workspace_id", "status"]),
        Index(value = ["workspace_id", "name"]),
    ],
)
data class FarmEntity(
    @PrimaryKey
    val id: UUID,
    @ColumnInfo(name = "workspace_id")
    val workspaceId: UUID,
    val name: String,
    val description: String? = null,
    val municipality: String? = null,
    val province: String? = null,
    @ColumnInfo(name = "cover_document_id")
    val coverDocumentId: UUID? = null,
    val notes: String? = null,
    val status: FarmStatus = FarmStatus.ACTIVE,
    @Embedded
    val metadata: LocalMetadata,
)
