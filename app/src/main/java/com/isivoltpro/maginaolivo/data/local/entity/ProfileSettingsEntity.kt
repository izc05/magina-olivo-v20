package com.isivoltpro.maginaolivo.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Phase 21A (Room v19): one profile row per workspace — municipality and the preferred
 * cooperative; Phase 21B (Room v20) adds the reminder preferences. The cooperative is a plain reference: archiving an Organization never deletes or
 * rewrites this row; reads simply stop treating it as the preferred one.
 */
@Entity(
    tableName = "profile_settings",
    foreignKeys = [
        ForeignKey(
            entity = WorkspaceEntity::class,
            parentColumns = ["id"],
            childColumns = ["workspace_id"],
        ),
    ],
    indices = [Index(value = ["workspace_id"], unique = true)],
)
data class ProfileSettingsEntity(
    @PrimaryKey val id: UUID,
    @ColumnInfo(name = "workspace_id") val workspaceId: UUID,
    val municipality: String? = null,
    val province: String? = null,
    @ColumnInfo(name = "preferred_organization_id") val preferredOrganizationId: UUID? = null,
    /** Phase 21B (Room v20): planned-work reminders ring on this phone. */
    @ColumnInfo(name = "reminders_enabled", defaultValue = "1") val remindersEnabled: Boolean = true,
    /** Phase 21B (Room v20): the day-before reminder's hour, in minutes after midnight (480 = 08:00). */
    @ColumnInfo(name = "previous_day_reminder_minute", defaultValue = "480") val previousDayReminderMinute: Int = 480,
    @Embedded val metadata: LocalMetadata,
)
