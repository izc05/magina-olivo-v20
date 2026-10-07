package com.isivoltpro.maginaolivo.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * A reusable legal/agronomic person (applicator/advisor). Deliberately separate from Worker,
 * whose meaning is only harvest labour and whose name may be an alias.
 *
 * Regulatory credentials are historical child rows: renewing a card/ROPO never rewrites
 * credentials that were valid for older treatments.
 */
@Entity(
    tableName = "agronomic_people",
    foreignKeys = [
        ForeignKey(
            entity = WorkspaceEntity::class,
            parentColumns = ["id"],
            childColumns = ["workspace_id"],
        ),
    ],
    indices = [
        Index(value = ["workspace_id", "status"]),
        Index(value = ["workspace_id", "tax_id"]),
        Index(value = ["workspace_id", "external_id"]),
    ],
)
data class AgronomicPersonEntity(
    @PrimaryKey val id: UUID,
    @ColumnInfo(name = "workspace_id") val workspaceId: UUID,
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "given_name") val givenName: String? = null,
    @ColumnInfo(name = "family_name") val familyName: String? = null,
    @ColumnInfo(name = "tax_id") val taxId: String? = null,
    @ColumnInfo(name = "is_advisor") val isAdvisor: Boolean = false,
    val source: String = "MANUAL",
    @ColumnInfo(name = "external_id") val externalId: String? = null,
    @ColumnInfo(name = "source_version") val sourceVersion: String? = null,
    @ColumnInfo(name = "fetched_at") val fetchedAt: Instant? = null,
    val status: String = "ACTIVE",
    @Embedded val metadata: LocalMetadata,
)

@Entity(
    tableName = "agronomic_credentials",
    foreignKeys = [
        ForeignKey(
            entity = AgronomicPersonEntity::class,
            parentColumns = ["id"],
            childColumns = ["person_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = WorkspaceEntity::class,
            parentColumns = ["id"],
            childColumns = ["workspace_id"],
        ),
    ],
    indices = [
        Index(value = ["person_id", "valid_from"]),
        Index(value = ["workspace_id"]),
        Index(value = ["workspace_id", "external_id"]),
    ],
)
data class AgronomicCredentialEntity(
    @PrimaryKey val id: UUID,
    @ColumnInfo(name = "workspace_id") val workspaceId: UUID,
    @ColumnInfo(name = "person_id") val personId: UUID,
    /** Dynamic semantic code, e.g. ROPO_APPLICATOR/CARD/ADVISOR; finalized by #536/#558. */
    @ColumnInfo(name = "credential_type") val credentialType: String,
    val number: String,
    @ColumnInfo(name = "category_code") val categoryCode: String? = null,
    @ColumnInfo(name = "valid_from") val validFrom: LocalDate? = null,
    @ColumnInfo(name = "valid_until") val validUntil: LocalDate? = null,
    val source: String = "MANUAL",
    @ColumnInfo(name = "external_id") val externalId: String? = null,
    @ColumnInfo(name = "source_version") val sourceVersion: String? = null,
    @ColumnInfo(name = "fetched_at") val fetchedAt: Instant? = null,
    @Embedded val metadata: LocalMetadata,
)

/**
 * Optional regulatory extension of an existing Machine. The machine remains the operational
 * asset; this profile carries only current registration/provenance facts.
 *
 * Inspections live in their own historical table so a future inspection cannot make a past
 * treatment appear compliant retroactively.
 */
@Entity(
    tableName = "phytosanitary_equipment_profiles",
    foreignKeys = [
        ForeignKey(
            entity = MachineEntity::class,
            parentColumns = ["id"],
            childColumns = ["machine_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = WorkspaceEntity::class,
            parentColumns = ["id"],
            childColumns = ["workspace_id"],
        ),
    ],
    indices = [
        Index(value = ["workspace_id"]),
        Index(value = ["workspace_id", "external_id"]),
    ],
)
data class PhytosanitaryEquipmentProfileEntity(
    @PrimaryKey @ColumnInfo(name = "machine_id") val machineId: UUID,
    @ColumnInfo(name = "workspace_id") val workspaceId: UUID,
    @ColumnInfo(name = "roma_registration") val romaRegistration: String? = null,
    @ColumnInfo(name = "census_reference") val censusReference: String? = null,
    @ColumnInfo(name = "acquisition_date") val acquisitionDate: LocalDate? = null,
    @ColumnInfo(name = "regulatory_type_code") val regulatoryTypeCode: String? = null,
    val source: String = "MANUAL",
    @ColumnInfo(name = "external_id") val externalId: String? = null,
    @ColumnInfo(name = "source_version") val sourceVersion: String? = null,
    @ColumnInfo(name = "fetched_at") val fetchedAt: Instant? = null,
    @Embedded val metadata: LocalMetadata,
)

@Entity(
    tableName = "phytosanitary_equipment_inspections",
    foreignKeys = [
        ForeignKey(
            entity = MachineEntity::class,
            parentColumns = ["id"],
            childColumns = ["machine_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = WorkspaceEntity::class,
            parentColumns = ["id"],
            childColumns = ["workspace_id"],
        ),
    ],
    indices = [
        Index(value = ["machine_id", "inspection_date"]),
        Index(value = ["workspace_id"]),
        Index(value = ["workspace_id", "external_id"]),
    ],
)
data class PhytosanitaryEquipmentInspectionEntity(
    @PrimaryKey val id: UUID,
    @ColumnInfo(name = "workspace_id") val workspaceId: UUID,
    @ColumnInfo(name = "machine_id") val machineId: UUID,
    @ColumnInfo(name = "inspection_date") val inspectionDate: LocalDate,
    /** Dynamic result/status code if the official source supplies one. */
    @ColumnInfo(name = "result_code") val resultCode: String? = null,
    @ColumnInfo(name = "certificate_reference") val certificateReference: String? = null,
    val source: String = "MANUAL",
    @ColumnInfo(name = "external_id") val externalId: String? = null,
    @ColumnInfo(name = "source_version") val sourceVersion: String? = null,
    @ColumnInfo(name = "fetched_at") val fetchedAt: Instant? = null,
    @Embedded val metadata: LocalMetadata,
)
