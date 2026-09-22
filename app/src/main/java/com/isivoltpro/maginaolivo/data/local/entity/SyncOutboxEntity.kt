package com.isivoltpro.maginaolivo.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.isivoltpro.maginaolivo.data.local.model.OutboxOperation
import com.isivoltpro.maginaolivo.data.local.model.OutboxStatus
import com.isivoltpro.maginaolivo.data.local.model.SyncEntityType
import java.time.Instant
import java.util.UUID

@Entity(
    tableName = "sync_outbox",
    indices = [
        Index(value = ["status", "next_attempt_at", "created_at"]),
        Index(value = ["entity_type", "entity_id"]),
    ],
)
data class SyncOutboxEntity(
    @PrimaryKey
    val id: UUID,
    @ColumnInfo(name = "entity_type")
    val entityType: SyncEntityType,
    @ColumnInfo(name = "entity_id")
    val entityId: UUID,
    val operation: OutboxOperation,
    @ColumnInfo(name = "payload_version")
    val payloadVersion: Int,
    @ColumnInfo(name = "base_remote_version")
    val baseRemoteVersion: Long? = null,
    val status: OutboxStatus = OutboxStatus.PENDING,
    @ColumnInfo(name = "attempt_count")
    val attemptCount: Int = 0,
    @ColumnInfo(name = "next_attempt_at")
    val nextAttemptAt: Instant? = null,
    @ColumnInfo(name = "last_error_code")
    val lastErrorCode: String? = null,
    @ColumnInfo(name = "last_error_message")
    val lastErrorMessage: String? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Instant,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Instant,
)
