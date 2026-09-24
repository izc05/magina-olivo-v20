package com.isivoltpro.maginaolivo.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.isivoltpro.maginaolivo.data.local.entity.FarmParcelMembershipEntity
import com.isivoltpro.maginaolivo.data.local.entity.ParcelEntity
import com.isivoltpro.maginaolivo.data.local.model.ParcelRow
import java.util.UUID
import kotlinx.coroutines.flow.Flow

@Dao
interface ParcelDao {
    @Upsert suspend fun upsert(parcel: ParcelEntity)
    @Upsert suspend fun upsertMembership(membership: FarmParcelMembershipEntity)

    @Query("SELECT * FROM parcels WHERE id = :id LIMIT 1")
    suspend fun findById(id: UUID): ParcelEntity?

    @Query("SELECT id FROM parcels WHERE workspace_id = :workspaceId AND cadastral_reference = :reference AND status = 'ACTIVE' AND deleted_at IS NULL LIMIT 1")
    suspend fun findActiveByCadastralReference(workspaceId: UUID, reference: String): UUID?

    @Query("SELECT * FROM farm_parcel_memberships WHERE parcel_id = :parcelId AND valid_until IS NULL AND deleted_at IS NULL LIMIT 1")
    suspend fun findCurrentMembership(parcelId: UUID): FarmParcelMembershipEntity?

    @Query("SELECT * FROM farm_parcel_memberships WHERE parcel_id = :parcelId ORDER BY valid_from, id")
    suspend fun listMemberships(parcelId: UUID): List<FarmParcelMembershipEntity>

    @Query(
        """
        SELECT p.*, m.farm_id AS current_farm_id FROM parcels p
        JOIN farm_parcel_memberships m ON m.parcel_id = p.id
        WHERE m.farm_id = :farmId AND m.valid_until IS NULL AND m.deleted_at IS NULL
          AND p.status = 'ACTIVE' AND p.deleted_at IS NULL
        ORDER BY p.display_name COLLATE NOCASE, p.id
        """,
    )
    fun observeActive(farmId: UUID): Flow<List<ParcelRow>>

    @Query(
        """
        SELECT p.*, NULL AS current_farm_id FROM parcels p
        WHERE p.status = 'ARCHIVED'
          AND EXISTS (SELECT 1 FROM farm_parcel_memberships m WHERE m.parcel_id = p.id AND m.farm_id = :farmId)
        ORDER BY p.display_name COLLATE NOCASE, p.id
        """,
    )
    fun observeArchived(farmId: UUID): Flow<List<ParcelRow>>

    @Query(
        """
        SELECT p.*, m.farm_id AS current_farm_id FROM parcels p
        LEFT JOIN farm_parcel_memberships m ON m.parcel_id = p.id AND m.valid_until IS NULL AND m.deleted_at IS NULL
        WHERE p.id = :parcelId LIMIT 1
        """,
    )
    fun observeById(parcelId: UUID): Flow<ParcelRow?>
}
