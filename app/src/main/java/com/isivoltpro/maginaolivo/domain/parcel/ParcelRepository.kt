package com.isivoltpro.maginaolivo.domain.parcel

import com.isivoltpro.maginaolivo.core.common.AppResult
import java.time.DayOfWeek
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.Flow

enum class ParcelSource { MANUAL, CATASTRO }

/** How the parcel is watered. Null in [ParcelAgronomy] means "not told yet", never dryland. */
enum class IrrigationSystem { DRYLAND, DRIP, SPRINKLER, OTHER }

/**
 * The farmer's own description of the grove (CR-004): every field is optional and shown as
 * "—" until filled in. Irrigation network/sector are what the farmer calls them; linking them
 * to an irrigation provider's alerts belongs to the backend phase.
 */
data class ParcelAgronomy(
    val oliveTreeCount: Int? = null,
    val variety: String? = null,
    val irrigationSystem: IrrigationSystem? = null,
    val irrigationNetwork: String? = null,
    val irrigationSector: String? = null,
    val irrigationDays: Set<DayOfWeek> = emptySet(),
)

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
    val agronomy: ParcelAgronomy = ParcelAgronomy(),
    /** Land registry the geometry came from (e.g. `ES_CATASTRO`); null for manual parcels. */
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
    val agronomy: ParcelAgronomy = ParcelAgronomy(),
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
    val agronomy: ParcelAgronomy = ParcelAgronomy(),
)

/**
 * Land-registry identity and boundary attached to a Parcel the farmer already has (Phase 18
 * "Ubicar en el mapa"). The Parcel keeps its own name, managed area, grove data and history.
 */
data class RegistryLink(
    val cadastralReference: String,
    val cadastralPolygon: String?,
    val cadastralParcel: String?,
    val geometryGeoJson: String,
    val cadastralAreaM2: Double?,
    val sourceProvider: String,
    val sourceImportedAt: Instant,
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
    suspend fun linkToRegistry(parcelId: UUID, link: RegistryLink): AppResult<Unit>
    suspend fun archive(parcelId: UUID): AppResult<Unit>
    suspend fun restore(parcelId: UUID, farmId: UUID): AppResult<Unit>
    suspend fun membershipHistory(parcelId: UUID): List<ParcelMembership>
}
