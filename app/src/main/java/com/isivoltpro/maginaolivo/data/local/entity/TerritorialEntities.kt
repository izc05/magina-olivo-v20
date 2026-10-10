package com.isivoltpro.maginaolivo.data.local.entity

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
    @PrimaryKey val slug: String,
    val name: String,
    val province: String = "Jaén",
    val ineCode: String?,
    val aemetCode: String,
    val comarca: String = "Sierra Mágina",
    val officialUrl: String? = null,
    val electronicSeatUrl: String? = null,
    val centerLatitude: Double? = null,
    val centerLongitude: Double? = null,
    val sourceUrl: String,
    val lastCheckedAtMs: Long = System.currentTimeMillis(),
    val active: Boolean = true,
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
    @PrimaryKey val id: String,
    val officialName: String,
    val shortName: String? = null,
    val entityType: String = "community_of_irrigation", // "community_of_irrigation", "sat_irrigation"
    val primaryMunicipalitySlug: String,
    val address: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val websiteUrl: String? = null,
    val electronicSeatUrl: String? = null,
    val verificationStatus: String = "candidate", // "candidate", "verified", "collaborating"
    val sourceBulletinRef: String? = null,
    val lastCheckedAtMs: Long = System.currentTimeMillis(),
    val active: Boolean = true,
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
    @PrimaryKey val id: String,
    val communityId: String,
    val sectorCode: String? = null,
    val noticeType: String = "shift_schedule", // "shift_schedule", "water_cut", "schedule_change", "general_notice"
    val title: String,
    val body: String,
    val startsAtEpochMs: Long,
    val endsAtEpochMs: Long,
    val sourceType: String = "public_bulletin", // "community_verified", "public_bulletin"
    val sourceUrl: String? = null,
    val status: String = "published", // "draft", "published", "revoked", "expired"
    val publishedAtEpochMs: Long = System.currentTimeMillis(),
    val expiresAtEpochMs: Long? = null,
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
    @PrimaryKey val id: String,
    val workspaceId: String,
    val plotId: String,
    val sectorCode: String? = null,
    val scheduledAtEpochMs: Long,
    val durationMinutes: Int,
    val linkedNoticeId: String? = null,
    val status: String = "planned", // "planned", "reprogrammed", "cancelled", "completed"
    val reminderMinutesBefore: Int = 30,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val updatedAtEpochMs: Long = System.currentTimeMillis(),
)
