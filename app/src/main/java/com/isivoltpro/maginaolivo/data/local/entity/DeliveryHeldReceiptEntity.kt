package com.isivoltpro.maginaolivo.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

/**
 * G2.4: an authenticated server ACK that arrived after the farmer revoked the farm's consent.
 * It is evidence only: it never changes kilos, field data, remoteVersion or syncStatus, and
 * its operation stays BLOCKED until an explicit, re-authorized reconciliation.
 */
@Entity(
    tableName = "delivery_held_receipts",
    indices = [Index(value = ["delivery_id", "state"])],
)
data class DeliveryHeldReceiptEntity(
    @PrimaryKey
    @ColumnInfo(name = "operation_id")
    val operationId: UUID,
    @ColumnInfo(name = "delivery_id")
    val deliveryId: UUID,
    @ColumnInfo(name = "account_id")
    val accountId: String,
    @ColumnInfo(name = "farm_id")
    val farmId: UUID,
    @ColumnInfo(name = "remote_version")
    val remoteVersion: Long,
    @ColumnInfo(name = "payload_hash")
    val payloadHash: String,
    @ColumnInfo(name = "payload_version")
    val payloadVersion: Int,
    /** The ledger revocation that blocked the operation; reconciliation needs a LATER consent. */
    @ColumnInfo(name = "revocation_operation_id")
    val revocationOperationId: UUID,
    val state: String = HELD,
    /** First incompatible receipt seen for the same operation. The original is never replaced. */
    @ColumnInfo(name = "conflict_remote_version")
    val conflictRemoteVersion: Long? = null,
    @ColumnInfo(name = "conflict_payload_hash")
    val conflictPayloadHash: String? = null,
    @ColumnInfo(name = "received_at")
    val receivedAt: Instant,
    @ColumnInfo(name = "resolved_at")
    val resolvedAt: Instant? = null,
) {
    companion object {
        const val HELD = "HELD"
        const val CONFLICT = "CONFLICT"
        const val RECONCILED = "RECONCILED"
    }
}
