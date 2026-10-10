package com.isivoltpro.maginaolivo.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Tabla de catálogo público de municipios de Jaén (Sierra Mágina y provincia).
 */
@Entity(
    tableName = "territorial_municipalities",
    indices = [
        Index(value = ["ine_code"], unique = true),
        Index(value = ["aemet_code"], unique = true),
    ],
)
data class TerritorialMunicipalityEntity(
    @PrimaryKey @ColumnInfo(name = "slug") val slug: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "province") val province: String = "Jaén",
    @ColumnInfo(name = "ine_code") val ineCode: String?,
    @ColumnInfo(name = "aemet_code") val aemetCode: String,
    @ColumnInfo(name = "comarca") val comarca: String = "Sierra Mágina",
    @ColumnInfo(name = "official_url") val officialUrl: String? = null,
    @ColumnInfo(name = "electronic_seat_url") val electronicSeatUrl: String? = null,
    @ColumnInfo(name = "center_latitude") val centerLatitude: Double? = null,
    @ColumnInfo(name = "center_longitude") val centerLongitude: Double? = null,
    @ColumnInfo(name = "source_url") val sourceUrl: String,
    @ColumnInfo(name = "last_checked_at_ms") val lastCheckedAtMs: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "active") val active: Boolean = true,
)

/**
 * Tabla de catálogo público de Comunidades de Regantes y SATs de riego.
 */
@Entity(
    tableName = "irrigation_communities",
    foreignKeys = [
        ForeignKey(
            entity = TerritorialMunicipalityEntity::class,
            parentColumns = ["slug"],
            childColumns = ["primary_municipality_slug"],
            onDelete = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [
        Index(value = ["primary_municipality_slug"]),
        Index(value = ["verification_status"]),
    ],
)
data class IrrigationCommunityEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "official_name") val officialName: String,
    @ColumnInfo(name = "short_name") val shortName: String? = null,
    @ColumnInfo(name = "entity_type") val entityType: String = "community_of_irrigation",
    @ColumnInfo(name = "primary_municipality_slug") val primaryMunicipalitySlug: String,
    @ColumnInfo(name = "address") val address: String? = null,
    @ColumnInfo(name = "phone") val phone: String? = null,
    @ColumnInfo(name = "email") val email: String? = null,
    @ColumnInfo(name = "website_url") val websiteUrl: String? = null,
    @ColumnInfo(name = "electronic_seat_url") val electronicSeatUrl: String? = null,
    @ColumnInfo(name = "verification_status") val verificationStatus: String = "candidate",
    @ColumnInfo(name = "source_bulletin_ref") val sourceBulletinRef: String? = null,
    @ColumnInfo(name = "last_checked_at_ms") val lastCheckedAtMs: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "active") val active: Boolean = true,
)

/**
 * Avisos comunitarios públicos de turnos y cortes de riego (red).
 */
@Entity(
    tableName = "community_water_notices",
    foreignKeys = [
        ForeignKey(
            entity = IrrigationCommunityEntity::class,
            parentColumns = ["id"],
            childColumns = ["community_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["community_id", "starts_at_epoch_ms"]),
        Index(value = ["status"]),
    ],
)
data class CommunityWaterNoticeEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "community_id") val communityId: String,
    @ColumnInfo(name = "sector_code") val sectorCode: String? = null,
    @ColumnInfo(name = "notice_type") val noticeType: String = "shift_schedule",
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "body") val body: String,
    @ColumnInfo(name = "starts_at_epoch_ms") val startsAtEpochMs: Long,
    @ColumnInfo(name = "ends_at_epoch_ms") val endsAtEpochMs: Long,
    @ColumnInfo(name = "source_type") val sourceType: String = "public_bulletin",
    @ColumnInfo(name = "source_url") val sourceUrl: String? = null,
    @ColumnInfo(name = "status") val status: String = "published",
    @ColumnInfo(name = "published_at_epoch_ms") val publishedAtEpochMs: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "expires_at_epoch_ms") val expiresAtEpochMs: Long? = null,
)

/**
 * Agenda personal offline del agricultor (Plan de riego privado).
 * NO escribe en las actividades del Cuaderno CUE V9 hasta que el agricultor ejecute el riego.
 */
@Entity(
    tableName = "personal_irrigation_plans",
    foreignKeys = [
        ForeignKey(
            entity = WorkspaceEntity::class,
            parentColumns = ["id"],
            childColumns = ["workspace_id"],
            onDelete = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [
        Index(value = ["workspace_id", "scheduled_at_epoch_ms"]),
        Index(value = ["plot_id"]),
    ],
)
data class PersonalIrrigationPlanEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "workspace_id") val workspaceId: String,
    @ColumnInfo(name = "plot_id") val plotId: String,
    @ColumnInfo(name = "sector_code") val sectorCode: String? = null,
    @ColumnInfo(name = "scheduled_at_epoch_ms") val scheduledAtEpochMs: Long,
    @ColumnInfo(name = "duration_minutes") val durationMinutes: Int,
    @ColumnInfo(name = "linked_notice_id") val linkedNoticeId: String? = null,
    @ColumnInfo(name = "status") val status: String = "planned",
    @ColumnInfo(name = "reminder_minutes_before") val reminderMinutesBefore: Int = 30,
    @ColumnInfo(name = "created_at_epoch_ms") val createdAtEpochMs: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at_epoch_ms") val updatedAtEpochMs: Long = System.currentTimeMillis(),
)
