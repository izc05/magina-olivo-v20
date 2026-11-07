package com.isivoltpro.maginaolivo.data.local.entity

import androidx.room.*
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** Local-only CR-015 sidecars. No sync status and no transport outbox records. */
@Entity(tableName="agricultural_cycles", foreignKeys=[
    ForeignKey(entity=WorkspaceEntity::class,parentColumns=["id"],childColumns=["workspace_id"]),
    ForeignKey(entity=FarmEntity::class,parentColumns=["id"],childColumns=["farm_id"]),
],indices=[Index(value=["workspace_id"]),Index(value=["farm_id","start_date"])])
data class AgriculturalCycleEntity(
    @PrimaryKey val id: UUID,
    @ColumnInfo(name="workspace_id") val workspaceId: UUID,
    @ColumnInfo(name="farm_id") val farmId: UUID,
    val label: String,
    @ColumnInfo(name="start_date") val startDate: LocalDate,
    @ColumnInfo(name="effective_end_date") val effectiveEndDate: LocalDate? = null,
    @ColumnInfo(name="closed_at") val closedAt: Instant? = null,
    @ColumnInfo(name="no_recollection") val noRecollection: Boolean = false,
    val version: Long = 1,
)

@Entity(tableName="agricultural_cycle_farm_state",foreignKeys=[
    ForeignKey(entity=FarmEntity::class,parentColumns=["id"],childColumns=["farm_id"]),
])
data class AgriculturalCycleFarmStateEntity(@PrimaryKey @ColumnInfo(name="farm_id") val farmId: UUID, val revision: Long)

@Entity(tableName="agricultural_cycle_activities",foreignKeys=[
    ForeignKey(entity=AgriculturalCycleEntity::class,parentColumns=["id"],childColumns=["cycle_id"]),
    ForeignKey(entity=ActivityEntity::class,parentColumns=["id"],childColumns=["activity_id"]),
],indices=[Index(value=["cycle_id"])])
data class AgriculturalCycleActivityEntity(@PrimaryKey @ColumnInfo(name="activity_id") val activityId: UUID,
    @ColumnInfo(name="cycle_id") val cycleId: UUID)

@Entity(tableName="agricultural_cycle_expenses",foreignKeys=[
    ForeignKey(entity=AgriculturalCycleEntity::class,parentColumns=["id"],childColumns=["cycle_id"]),
    ForeignKey(entity=ExpenseEntity::class,parentColumns=["id"],childColumns=["expense_id"]),
],indices=[Index(value=["cycle_id"])])
data class AgriculturalCycleExpenseEntity(@PrimaryKey @ColumnInfo(name="expense_id") val expenseId: UUID,
    @ColumnInfo(name="cycle_id") val cycleId: UUID)

@Entity(tableName="agricultural_cycle_recollections",foreignKeys=[
    ForeignKey(entity=AgriculturalCycleEntity::class,parentColumns=["id"],childColumns=["cycle_id"]),
    ForeignKey(entity=CampaignEntity::class,parentColumns=["id"],childColumns=["campaign_id"]),
],indices=[Index(value=["cycle_id"])])
data class AgriculturalCycleRecollectionEntity(@PrimaryKey @ColumnInfo(name="campaign_id") val campaignId: UUID,
    @ColumnInfo(name="cycle_id") val cycleId: UUID)

@Entity(tableName="agricultural_cycle_operations",foreignKeys=[
    ForeignKey(entity=AgriculturalCycleEntity::class,parentColumns=["id"],childColumns=["cycle_id"]),
],indices=[Index(value=["cycle_id"])])
data class AgriculturalCycleOperationEntity(
    @PrimaryKey val id: UUID,
    @ColumnInfo(name="cycle_id") val cycleId: UUID,
    @ColumnInfo(name="owner_id") val ownerId: UUID?,
    val fingerprint: String,
    @ColumnInfo(name="recorded_at") val recordedAt: Instant,
    val reason: String,
)
