package com.isivoltpro.maginaolivo.data.local.entity

import androidx.room.*
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** CR-015 P5 local economy. No sync status and no outbox: local only until a sync phase is authorised. */
@Entity(tableName="cooperative_settlements",foreignKeys=[
    ForeignKey(entity=WorkspaceEntity::class,parentColumns=["id"],childColumns=["workspace_id"]),
    ForeignKey(entity=FarmEntity::class,parentColumns=["id"],childColumns=["farm_id"]),
    ForeignKey(entity=AgriculturalCycleEntity::class,parentColumns=["id"],childColumns=["cycle_id"]),
    ForeignKey(entity=CooperativeSettlementEntity::class,parentColumns=["id"],childColumns=["replaces_id"]),
],indices=[Index(value=["workspace_id"]),Index(value=["farm_id"]),Index(value=["cycle_id"]),Index(value=["replaces_id"])])
data class CooperativeSettlementEntity(
    @PrimaryKey val id: UUID,
    @ColumnInfo(name="workspace_id") val workspaceId: UUID,
    @ColumnInfo(name="farm_id") val farmId: UUID?,
    @ColumnInfo(name="cycle_id") val cycleId: UUID?,
    val kind: String,
    val cooperative: String,
    @ColumnInfo(name="document_date") val documentDate: LocalDate,
    val currency: String,
    @ColumnInfo(name="gross_minor") val grossMinor: Long,
    @ColumnInfo(name="withholding_minor") val withholdingMinor: Long,
    @ColumnInfo(name="fees_minor") val feesMinor: Long,
    @ColumnInfo(name="net_minor") val netMinor: Long,
    val kilograms: Long?,
    val reference: String?,
    @ColumnInfo(name="replaces_id") val replacesId: UUID?,
    val status: String,
    val version: Long,
    @ColumnInfo(name="created_at") val createdAt: Instant,
    @ColumnInfo(name="updated_at") val updatedAt: Instant,
)

@Entity(tableName="settlement_advance_applications",foreignKeys=[
    ForeignKey(entity=CooperativeSettlementEntity::class,parentColumns=["id"],childColumns=["advance_id"]),
    ForeignKey(entity=CooperativeSettlementEntity::class,parentColumns=["id"],childColumns=["settlement_id"]),
],indices=[Index(value=["advance_id"]),Index(value=["settlement_id"])])
data class SettlementAdvanceApplicationEntity(
    @PrimaryKey val id: UUID,
    @ColumnInfo(name="advance_id") val advanceId: UUID,
    @ColumnInfo(name="settlement_id") val settlementId: UUID,
    @ColumnInfo(name="amount_minor") val amountMinor: Long,
    val active: Boolean,
    @ColumnInfo(name="created_at") val createdAt: Instant,
)

@Entity(tableName="settlement_payments",foreignKeys=[
    ForeignKey(entity=CooperativeSettlementEntity::class,parentColumns=["id"],childColumns=["settlement_id"]),
],indices=[Index(value=["settlement_id"])])
data class SettlementPaymentEntity(
    @PrimaryKey val id: UUID,
    @ColumnInfo(name="settlement_id") val settlementId: UUID,
    @ColumnInfo(name="payment_date") val paymentDate: LocalDate,
    @ColumnInfo(name="amount_minor") val amountMinor: Long,
    val active: Boolean,
    @ColumnInfo(name="created_at") val createdAt: Instant,
)
