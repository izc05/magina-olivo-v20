package com.isivoltpro.maginaolivo.domain.parcel

import com.isivoltpro.maginaolivo.core.common.AppResult
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.Flow

enum class ParcelSource { MANUAL, CATASTRO }

data class Parcel(
    val id: UUID,
    val workspaceId: UUID,
    val farmId: UUID?,
    val displayName: String,
    val cadastralReference: String?,
    val cadastralPolygon: String?,
    val cadastralParcel: String?,
    val municipality: String?,
    val province: String?,
    val source: ParcelSource,
    val geometryGeoJson: String?,
    val cadastralAreaM2: Double?,
    val managedAreaM2: Double?,
    val notes: String?,
    val archivedAt: Instant?,
    val version: Long,
    val sourceProvider: String? = null,
    val sourceImportedAt: Instant? = null,
)

data class NewParcel(
    val farmId: UUID,
    val displayName: String,
    val cadastralReference: String? = null,
    val cadastralPolygon: String? = null,
    val cadastralParcel: String? = null,
    val municipality: String? = null,
    val province: String? = null,
    val source: ParcelSource = ParcelSource.MANUAL,
    val geometryGeoJson: String? = null,
    val cadastralAreaM2: Double? = null,
    val managedAreaM2: Double? = null,
    val notes: String? = null,
    val sourceProvider: String? = null,
    val sourceImportedAt: Instant? = null,
)

data class ParcelChanges(
    val displayName: String,
    val cadastralReference: String? = null,
    val cadastralPolygon: String? = null,
    val cadastralParcel: String? = null,
    val municipality: String? = null,
    val province: String? = null,
    val geometryGeoJson: String? = null,
    val cadastralAreaM2: Double? = null,
    val managedAreaM2: Double? = null,
    val notes: String? = null,
)

data class ParcelMembership(
    val id: UUID,
    val farmId: UUID,
    val parcelId: UUID,
    val validFrom: Instant,
    val validUntil: Instant?,
)

interface ParcelRepository {
    fun observeActive(farmId: UUID): Flow<List<Parcel>>
    fun observeArchived(farmId: UUID): Flow<List<Parcel>>
    fun observeById(parcelId: UUID): Flow<Parcel?>
    suspend fun findActiveByCadastralReference(workspaceId: UUID, reference: String): UUID?
    suspend fun create(command: NewParcel): AppResult<UUID>
    suspend fun update(parcelId: UUID, changes: ParcelChanges): AppResult<Unit>
    suspend fun archive(parcelId: UUID): AppResult<Unit>
    suspend fun restore(parcelId: UUID, farmId: UUID): AppResult<Unit>
    suspend fun membershipHistory(parcelId: UUID): List<ParcelMembership>
}
