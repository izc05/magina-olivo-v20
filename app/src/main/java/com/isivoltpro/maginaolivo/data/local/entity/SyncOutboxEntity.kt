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
        Index(value = ["account_id", "status"]),
        Index(value = ["farm_id"]),
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
    /**
     * G2.3: Associated user account identifier. Null for legacy operations enqueued prior to G2.3
     * or created offline without an active account session. Never automatically assumed.
     */
    @ColumnInfo(name = "account_id")
    val accountId: String? = null,
    /**
     * G2.3: Canonical immutable JSON payload captured at operation creation time.
     * Guarantees idempotent replays and preserves payload immutability across retries.
     */
    @ColumnInfo(name = "payload_json")
    val payloadJson: String? = null,
    /**
     * G2.3: Optional farm binding for G1b consent gating. If consent for this farm is revoked or missing,
     * the operation cannot be dispatched until consent is restored.
     */
    @ColumnInfo(name = "farm_id")
    val farmId: UUID? = null,
)
