package com.isivoltpro.maginaolivo.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import java.util.UUID

/**
 * #808 R3-a: evidence of what the Jornada receiver (R3-c) has already verified and applied, per account and
 * issuer, so that receiving the same server state again writes nothing. Additive table (migration 33 -> 34),
 * empty on upgrade. Nothing in R3-a writes it: no receiver exists yet.
 */
@Entity(
    tableName = "sync_receive_receipts",
    primaryKeys = ["issuer", "account_id", "entity_type", "entity_id"],
    indices = [Index(value = ["issuer", "account_id", "harvest_id"])],
)
data class SyncReceiveReceiptEntity(
    val issuer: String,
    @ColumnInfo(name = "account_id") val accountId: String,
    /** `HARVEST` or `DELIVERY`. */
    @ColumnInfo(name = "entity_type") val entityType: String,
    @ColumnInfo(name = "entity_id") val entityId: UUID,
    /** The Jornada of the bundle the entity arrived in. */
    @ColumnInfo(name = "harvest_id") val harvestId: UUID?,
    @ColumnInfo(name = "remote_version") val remoteVersion: Long,
    val deleted: Boolean,
    @ColumnInfo(name = "head_operation_id") val headOperationId: UUID?,
    @ColumnInfo(name = "head_payload_version") val headPayloadVersion: Int?,
    @ColumnInfo(name = "head_payload_hash") val headPayloadHash: String?,
    /** R1-c, HARVEST only. */
    @ColumnInfo(name = "own_fingerprint") val ownFingerprint: String?,
    /** R1-c, AUTO_DAY only. */
    @ColumnInfo(name = "derived_fingerprint") val derivedFingerprint: String?,
    @ColumnInfo(name = "contract_version") val contractVersion: Int,
    @ColumnInfo(name = "received_at") val receivedAt: Long,
)

/**
 * #808 R3-a: a persisted receive hold (design D9). Never resolved by itself: R3 has no code path that sets
 * [resolvedAt]; only a future explicit resolution gate may. Additive table (migration 33 -> 34), empty on upgrade.
 */
@Entity(
    tableName = "sync_receive_holds",
    primaryKeys = ["issuer", "account_id", "entity_type", "entity_id", "cause"],
    indices = [Index(value = ["issuer", "account_id", "harvest_id"])],
)
data class SyncReceiveHoldEntity(
    val issuer: String,
    @ColumnInfo(name = "account_id") val accountId: String,
    @ColumnInfo(name = "entity_type") val entityType: String,
    @ColumnInfo(name = "entity_id") val entityId: UUID,
    /** NO_JORNADA, UNVERIFIED, FOREIGN_RECORDER, REMOTE_AHEAD, LOCAL_PENDING, PARENT_MISSING, LOCAL_DEPENDENTS, WEIGHT_DIVERGENT, ... */
    val cause: String,
    @ColumnInfo(name = "harvest_id") val harvestId: UUID?,
    @ColumnInfo(name = "local_version") val localVersion: Long?,
    @ColumnInfo(name = "remote_version") val remoteVersion: Long?,
    @ColumnInfo(name = "remote_hash") val remoteHash: String?,
    /** Bounded (512 chars), never a secret. */
    val detail: String?,
    @ColumnInfo(name = "first_seen_at") val firstSeenAt: Long,
    @ColumnInfo(name = "last_seen_at") val lastSeenAt: Long,
    @ColumnInfo(name = "resolved_at") val resolvedAt: Long? = null,
)
