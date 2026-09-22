package com.isivoltpro.maginaolivo.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.isivoltpro.maginaolivo.data.local.entity.ActivityEntity
import com.isivoltpro.maginaolivo.data.local.entity.ActivityParcelTargetEntity
import com.isivoltpro.maginaolivo.data.local.entity.FertilizationDetailEntity
import com.isivoltpro.maginaolivo.data.local.entity.IncidentDetailEntity
import com.isivoltpro.maginaolivo.data.local.entity.IrrigationDetailEntity
import com.isivoltpro.maginaolivo.data.local.entity.IrrigationPriceSnapshotEntity
import com.isivoltpro.maginaolivo.data.local.entity.MaintenanceDetailEntity
import com.isivoltpro.maginaolivo.data.local.entity.PhytosanitaryDetailEntity
import com.isivoltpro.maginaolivo.data.local.entity.PruningDetailEntity
import com.isivoltpro.maginaolivo.data.local.entity.SoilWorkDetailEntity
import com.isivoltpro.maginaolivo.data.local.model.ActivityWithTargets
import java.util.UUID
import kotlinx.coroutines.flow.Flow

@Dao
interface ActivityDao {
    @Upsert suspend fun upsert(activity: ActivityEntity)

    @Upsert suspend fun upsertTargets(targets: List<ActivityParcelTargetEntity>)

    @Query("SELECT * FROM activities WHERE id = :id LIMIT 1")
    suspend fun findById(id: UUID): ActivityEntity?

    @Query("SELECT * FROM activity_parcels WHERE activity_id = :activityId ORDER BY parcel_name_at_target COLLATE NOCASE, parcel_id")
    suspend fun listTargets(activityId: UUID): List<ActivityParcelTargetEntity>

    @Query("DELETE FROM activity_parcels WHERE activity_id = :activityId")
    suspend fun deleteTargets(activityId: UUID)

    @Query("SELECT COUNT(*) FROM activity_parcels WHERE activity_id = :activityId")
    suspend fun countTargets(activityId: UUID): Int


    @Upsert suspend fun upsertPruning(detail: PruningDetailEntity)

    @Upsert suspend fun upsertFertilization(detail: FertilizationDetailEntity)

    @Upsert suspend fun upsertPhytosanitary(detail: PhytosanitaryDetailEntity)

    @Upsert suspend fun upsertSoilWork(detail: SoilWorkDetailEntity)

    @Upsert suspend fun upsertIrrigation(detail: IrrigationDetailEntity)

    @Upsert suspend fun upsertIrrigationPrice(snapshot: IrrigationPriceSnapshotEntity)

    @Upsert suspend fun upsertMaintenance(detail: MaintenanceDetailEntity)

    @Upsert suspend fun upsertIncident(detail: IncidentDetailEntity)

    /**
     * Removes whatever typed detail an Activity currently has.
     *
     * Retyping an Activity is a legitimate correction — a job logged as pruning turns out
     * to have been clearing — and the invariant is one matching detail, so the previous
     * one goes in the same transaction rather than being orphaned under a type that can
     * no longer read it.
     */
    @Query("DELETE FROM pruning_details WHERE activity_id = :activityId")
    suspend fun deletePruning(activityId: UUID)

    @Query("DELETE FROM fertilization_details WHERE activity_id = :activityId")
    suspend fun deleteFertilization(activityId: UUID)

    @Query("DELETE FROM phytosanitary_details WHERE activity_id = :activityId")
    suspend fun deletePhytosanitary(activityId: UUID)

    @Query("DELETE FROM soil_work_details WHERE activity_id = :activityId")
    suspend fun deleteSoilWork(activityId: UUID)

    @Query("DELETE FROM irrigation_details WHERE activity_id = :activityId")
    suspend fun deleteIrrigation(activityId: UUID)

    @Query("DELETE FROM irrigation_price_snapshots WHERE activity_id = :activityId")
    suspend fun deleteIrrigationPrice(activityId: UUID)

    @Query("DELETE FROM maintenance_details WHERE activity_id = :activityId")
    suspend fun deleteMaintenance(activityId: UUID)

    @Query("DELETE FROM incident_details WHERE activity_id = :activityId")
    suspend fun deleteIncident(activityId: UUID)

    @Transaction
    @Query("SELECT * FROM activities WHERE id = :id LIMIT 1")
    suspend fun findWithTargets(id: UUID): ActivityWithTargets?

    @Transaction
    @Query("SELECT * FROM activities WHERE farm_id = :farmId AND deleted_at IS NULL ORDER BY activity_date DESC, id")
    fun observeForFarm(farmId: UUID): Flow<List<ActivityWithTargets>>

    @Transaction
    @Query(
        """
        SELECT a.* FROM activities a
        JOIN activity_parcels t ON t.activity_id = a.id
        WHERE t.parcel_id = :parcelId AND a.deleted_at IS NULL
        ORDER BY a.activity_date DESC, a.id
        """,
    )
    fun observeForParcel(parcelId: UUID): Flow<List<ActivityWithTargets>>

    @Transaction
    @Query("SELECT * FROM activities WHERE id = :id AND deleted_at IS NULL LIMIT 1")
    fun observeWithTargets(id: UUID): Flow<ActivityWithTargets?>
}
