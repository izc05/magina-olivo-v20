package com.isivoltpro.maginaolivo.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

/**
 * G2.4: the durable PREPARE record written BEFORE any request may leave the device.
 *
 * It is the correlation anchor for a later authenticated ACK: it freezes who was going to
 * send what, for which farm, under which consent, over which immutable bytes. A receipt that
 * cannot be matched against a row here is never applied, so no transport can confirm an
 * operation this device did not durably prepare.
 *
 * Every column except [settledAt]/[settledOutcome] is immutable once written. The row is
 * evidence: it never carries kilos or field data, and it never authorizes a request by
 * itself — consent and identity are re-checked at correlation time.
 */
@Entity(
    tableName = "delivery_dispatch_prepare",
    indices = [
        Index(value = ["delivery_id"]),
        Index(value = ["account_id", "farm_id"]),
        Index(value = ["settled_at"]),
    ],
)
data class DeliveryDispatchPrepareEntity(
    /** The outbox intent UUID. One PREPARE per intent, for its whole lifetime. */
    @PrimaryKey
    @ColumnInfo(name = "operation_id")
    val operationId: UUID,
    @ColumnInfo(name = "delivery_id")
    val deliveryId: UUID,
    @ColumnInfo(name = "farm_id")
    val farmId: UUID,
    /** Owner the data belongs to. G2.4 is mono-titular: author and owner are the same. */
    @ColumnInfo(name = "account_id")
    val accountId: String,
    /** Token issuer as the authenticated session reported it. */
    val issuer: String,
    /** Ledger subject the identity must own ("issuer|userId"). */
    val subject: String,
    @ColumnInfo(name = "local_workspace_id")
    val localWorkspaceId: UUID,
    /** Null until a remote workspace has actually been reserved; never invented. */
    @ColumnInfo(name = "remote_workspace_id")
    val remoteWorkspaceId: UUID? = null,
    /** The consent event that authorized this farm at PREPARE time. */
    @ColumnInfo(name = "consent_operation_id")
    val consentOperationId: UUID,
    @ColumnInfo(name = "consent_text_version")
    val consentTextVersion: Int,
    @ColumnInfo(name = "payload_version")
    val payloadVersion: Int,
    /** SHA-256 of the exact persisted UTF-8 payload bytes. Immutable. */
    @ColumnInfo(name = "payload_hash")
    val payloadHash: String,
    /** Null for CREATE; the version the edit is based on for UPDATE. */
    @ColumnInfo(name = "base_remote_version")
    val baseRemoteVersion: Long? = null,
    /** Lease claim marker of the sender that prepared it. */
    @ColumnInfo(name = "lease_marker")
    val leaseMarker: String,
    @ColumnInfo(name = "prepared_at")
    val preparedAt: Instant,
    /** Null while the outcome is still unknown: an in-flight or interrupted request. */
    @ColumnInfo(name = "settled_at")
    val settledAt: Instant? = null,
    @ColumnInfo(name = "settled_outcome")
    val settledOutcome: String? = null,
) {
    companion object {
        const val SETTLED_CONFIRMED = "CONFIRMED"
        const val SETTLED_HELD = "HELD"
        const val SETTLED_CONFLICT = "CONFLICT"
        const val SETTLED_UNKNOWN = "OUTCOME_UNKNOWN"
    }
}
