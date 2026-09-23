package com.isivoltpro.maginaolivo.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.isivoltpro.maginaolivo.data.local.entity.ActivityPlanningEntity
import com.isivoltpro.maginaolivo.data.local.entity.ReminderEntity
import com.isivoltpro.maginaolivo.data.local.model.ActivityWithTargets
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.Flow

data class FarmNameRow(val id: UUID, val name: String)

@Dao
interface AgendaDao {
    @Transaction
    @Query("SELECT * FROM activities WHERE status = 'PLANNED' AND deleted_at IS NULL ORDER BY activity_date, id")
    fun observePlanned(): Flow<List<ActivityWithTargets>>

    @Query("SELECT id, name FROM farms")
    fun observeFarmNames(): Flow<List<FarmNameRow>>

    @Upsert suspend fun upsertPlanning(planning: ActivityPlanningEntity)

    @Query("SELECT planned_start_time FROM activity_planning_details WHERE activity_id = :activityId LIMIT 1")
    suspend fun findPlannedStartTime(activityId: UUID): String?

    @Query("DELETE FROM activity_planning_details WHERE activity_id = :activityId")
    suspend fun deletePlanning(activityId: UUID)

    @Query("SELECT * FROM reminders WHERE owner_type = :ownerType AND owner_id = :ownerId ORDER BY trigger_at, id")
    suspend fun listForOwner(ownerType: String, ownerId: UUID): List<ReminderEntity>

    @Query("DELETE FROM reminders WHERE owner_type = :ownerType AND owner_id = :ownerId")
    suspend fun deleteForOwner(ownerType: String, ownerId: UUID)

    @Upsert suspend fun upsertReminders(reminders: List<ReminderEntity>)

    @Query("SELECT * FROM reminders WHERE id = :id LIMIT 1")
    suspend fun findReminder(id: UUID): ReminderEntity?

    /** Device state, not an edit: no version bump and no outbox intent. */
    @Query("UPDATE reminders SET fired_at = :firedAt WHERE id = :id")
    suspend fun markFired(id: UUID, firedAt: Instant)

    /**
     * Reminders that should have an alarm: enabled, not yet shown, still ahead (or missed by
     * less than [since], so a phone that was off still warns), on work that is still planned.
     */
    @Query(
        """
        SELECT r.* FROM reminders r
        JOIN activities a ON a.id = r.owner_id
        WHERE r.owner_type = 'ACTIVITY' AND r.enabled = 1 AND r.fired_at IS NULL
          AND r.trigger_at > :since AND a.status = 'PLANNED' AND a.deleted_at IS NULL
        ORDER BY r.trigger_at, r.id
        """,
    )
    suspend fun listDue(since: Instant): List<ReminderEntity>

    /** Every reminder that has not fired: the set a reconcile may need to cancel. */
    @Query("SELECT * FROM reminders WHERE fired_at IS NULL")
    suspend fun listUnfired(): List<ReminderEntity>
}
