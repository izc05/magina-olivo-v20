package com.isivoltpro.maginaolivo.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

/**
 * G2.5: durable local evidence of a verified delivery bundle imported from the remote server.
 * Preserves the exact head payload hash, head version, receive contract version,
 * account ID, issuer, and receive timestamp.
 */
@Entity(
    tableName = "delivery_import_receipts",
    indices = [Index(value = ["account_id"])],
)
data class DeliveryImportReceiptEntity(
    @PrimaryKey
    @ColumnInfo(name = "delivery_id")
    val deliveryId: UUID,
    @ColumnInfo(name = "account_id")
    val accountId: String,
    val issuer: String,
    @ColumnInfo(name = "head_payload_hash")
    val headPayloadHash: String,
    @ColumnInfo(name = "head_version")
    val headVersion: Long,
    @ColumnInfo(name = "receive_contract_version")
    val receiveContractVersion: Int,
    @ColumnInfo(name = "received_at")
    val receivedAt: Instant,
)
