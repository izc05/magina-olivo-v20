package com.isivoltpro.maginaolivo.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

/**
 * How a planned Activity is expected to go (`DATA-MODEL-RC1.2-ADDENDUM` §6). A child of the
 * Activity aggregate (D5): written in its transaction, sharing its version and its one
 * outbox intent. The day is the Activity's own date; `planned_start_time` is "HH:mm".
 * `provider_organization_id` is reserved for a linked provider; Phase 16 stores free text.
 */
@Entity(
    tableName = "activity_planning_details",
    foreignKeys = [
        ForeignKey(
            entity = ActivityEntity::class,
            parentColumns = ["id"],
            childColumns = ["activity_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["workspace_id"])],
)
data class ActivityPlanningEntity(
    @PrimaryKey @ColumnInfo(name = "activity_id") val activityId: UUID,
    @ColumnInfo(name = "workspace_id") val workspaceId: UUID,
    @ColumnInfo(name = "planned_start_time") val plannedStartTime: String? = null,
    @ColumnInfo(name = "expected_duration_minutes") val expectedDurationMinutes: Int? = null,
    @ColumnInfo(name = "expected_people_count") val expectedPeopleCount: Int? = null,
    @ColumnInfo(name = "provider_organization_id") val providerOrganizationId: UUID? = null,
    @ColumnInfo(name = "crew_text") val crewText: String? = null,
    @Embedded val metadata: LocalMetadata,
)

/**
 * A local reminder (`DATA-MODEL-RC1.1-ADDENDUM` §7). Scheduling is local-first: the row is
 * the truth and the Android alarm is only its projection, rebuilt at start and after a
 * reboot. For an ACTIVITY owner the reminder is a child of that Activity aggregate; marking
 * it fired is device state, not an edit, and queues nothing.
 */
@Entity(
    tableName = "reminders",
    foreignKeys = [
        ForeignKey(
            entity = WorkspaceEntity::class,
            parentColumns = ["id"],
            childColumns = ["workspace_id"],
        ),
    ],
    indices = [
        Index(value = ["workspace_id"]),
        Index(value = ["owner_type", "owner_id"]),
        Index(value = ["enabled", "trigger_at"]),
    ],
)
data class ReminderEntity(
    @PrimaryKey val id: UUID,
    @ColumnInfo(name = "workspace_id") val workspaceId: UUID,
    @ColumnInfo(name = "owner_type") val ownerType: String,
    @ColumnInfo(name = "owner_id") val ownerId: UUID,
    @ColumnInfo(name = "trigger_at") val triggerAt: Instant,
    val kind: String,
    val enabled: Boolean = true,
    @ColumnInfo(name = "local_notification_id") val localNotificationId: Int,
    @ColumnInfo(name = "fired_at") val firedAt: Instant? = null,
    @Embedded val metadata: LocalMetadata,
)
