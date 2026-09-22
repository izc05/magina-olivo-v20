package com.isivoltpro.maginaolivo.domain.activity

import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.data.local.model.ActivityStatus
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow

/**
 * RC1 activity types. Harvest is intentionally absent: it owns separate
 * quantity/distribution/delivery semantics (DATA-MODEL-RC1-FUTURE section 8).
 */
enum class ActivityType {
    OBSERVATION,
    PRUNING,
    SOIL_WORK,
    FERTILIZATION,
    PHYTOSANITARY,
    IRRIGATION,
    MAINTENANCE,
    INCIDENT,
    OTHER,
}

/** One Parcel targeted by a single canonical Activity. Never a duplicated Activity. */
data class ActivityParcelTarget(
    val parcelId: UUID,
    val parcelName: String,
    val areaAffectedM2: Double? = null,
    val notes: String? = null,
)

data class ActivityParcelOption(val id: UUID, val name: String, val managedAreaM2: Double?)

data class Activity(
    val id: UUID,
    val workspaceId: UUID,
    val farmId: UUID?,
    val campaignId: UUID?,
    val type: ActivityType,
    val status: ActivityStatus,
    val activityDate: LocalDate,
    val description: String,
    val notes: String?,
    val targets: List<ActivityParcelTarget>,
    val version: Long,
)

data class NewActivity(
    val farmId: UUID,
    val campaignId: UUID? = null,
    val type: ActivityType,
    val activityDate: LocalDate,
    val description: String,
    val parcelIds: Set<UUID> = emptySet(),
    val notes: String? = null,
    /** A draft is resumable and may be saved with no Parcel selected yet. */
    val asDraft: Boolean = false,
)

data class ActivityChanges(
    val type: ActivityType,
    val activityDate: LocalDate,
    val description: String,
    val parcelIds: Set<UUID>,
    val notes: String? = null,
)

/**
 * Common engine for agricultural activities.
 *
 * Aggregate: `activities` is the root, `activity_parcels` are its children
 * (RC1-NORMATIVE-ADDENDUM D5). Every mutation commits locally first, bumps the
 * aggregate version and collapses into a single Activity outbox intent.
 *
 * Phase 9 deliberately exposes no cost field: RC1-NORMATIVE-ADDENDUM D3 supersedes
 * `activities.cost_cents` / `activities.currency` in favour of linked expenses.
 */
interface ActivityRepository {
    fun observeSelectableParcels(farmId: UUID): Flow<List<ActivityParcelOption>>
    fun observeForFarm(farmId: UUID): Flow<List<Activity>>
    fun observeForParcel(parcelId: UUID): Flow<List<Activity>>
    fun observe(id: UUID): Flow<Activity?>
    suspend fun create(command: NewActivity): AppResult<UUID>
    suspend fun update(id: UUID, changes: ActivityChanges): AppResult<Unit>
    /** Promotes a resumable DRAFT to PLANNED once it targets at least one Parcel. */
    suspend fun plan(id: UUID): AppResult<Unit>
    suspend fun complete(id: UUID): AppResult<Unit>
    suspend fun cancel(id: UUID): AppResult<Unit>
    /** Reopens a COMPLETED or CANCELLED activity back to PLANNED, explicitly and audited. */
    suspend fun reopen(id: UUID): AppResult<Unit>
    suspend fun archive(id: UUID): AppResult<Unit>
}
