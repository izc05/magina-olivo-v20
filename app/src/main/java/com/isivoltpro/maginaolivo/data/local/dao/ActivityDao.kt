package com.isivoltpro.maginaolivo.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.isivoltpro.maginaolivo.data.local.entity.ActivityEntity
import com.isivoltpro.maginaolivo.data.local.entity.ActivityParcelTargetEntity
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
