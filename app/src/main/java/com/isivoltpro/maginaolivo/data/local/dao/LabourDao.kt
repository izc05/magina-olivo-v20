package com.isivoltpro.maginaolivo.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.isivoltpro.maginaolivo.data.local.entity.HarvestLabourEntity
import com.isivoltpro.maginaolivo.data.local.entity.WorkerEntity
import java.util.UUID
import kotlinx.coroutines.flow.Flow

@Dao
interface LabourDao {
    @Upsert suspend fun upsertWorker(worker: WorkerEntity)

    @Query("SELECT * FROM workers WHERE id = :id LIMIT 1")
    suspend fun findWorker(id: UUID): WorkerEntity?

    @Query("SELECT * FROM workers WHERE deleted_at IS NULL ORDER BY name COLLATE NOCASE, id")
    fun observeWorkers(): Flow<List<WorkerEntity>>

    @Query(
        """
        SELECT * FROM workers
        WHERE workspace_id = :workspaceId AND name = :name COLLATE NOCASE AND deleted_at IS NULL
        LIMIT 1
        """,
    )
    suspend fun findWorkerByName(workspaceId: UUID, name: String): WorkerEntity?

    @Upsert suspend fun upsertLabour(rows: List<HarvestLabourEntity>)

    @Query("SELECT * FROM harvest_labour WHERE id = :id LIMIT 1")
    suspend fun findLabour(id: UUID): HarvestLabourEntity?

    @Query("SELECT * FROM harvest_labour WHERE harvest_id = :harvestId AND deleted_at IS NULL ORDER BY worker_name COLLATE NOCASE, created_at, id")
    fun observeForHarvest(harvestId: UUID): Flow<List<HarvestLabourEntity>>

    @Query("SELECT * FROM harvest_labour WHERE harvest_id = :harvestId AND deleted_at IS NULL")
    suspend fun listForHarvest(harvestId: UUID): List<HarvestLabourEntity>

    /** The live labour of every live Jornada of one Campaign. */
    @Query(
        """
        SELECT harvest_labour.* FROM harvest_labour
        JOIN harvests ON harvests.id = harvest_labour.harvest_id
        WHERE harvests.campaign_id = :campaignId AND harvests.deleted_at IS NULL AND harvest_labour.deleted_at IS NULL
        """,
    )
    fun observeForCampaign(campaignId: UUID): Flow<List<HarvestLabourEntity>>

    /**
     * The named crew of the Farm's most recent earlier Jornada that had one — for
     * "Repetir cuadrilla anterior".
     */
    @Query(
        """
        SELECT harvest_labour.* FROM harvest_labour
        JOIN harvests ON harvests.id = harvest_labour.harvest_id
        WHERE harvests.farm_id = :farmId AND harvests.id != :harvestId AND harvests.deleted_at IS NULL
          AND harvest_labour.deleted_at IS NULL AND harvest_labour.worker_id IS NOT NULL
          AND harvests.harvest_date <= :onOrBefore
        ORDER BY harvests.harvest_date DESC, harvests.created_at DESC
        """,
    )
    suspend fun listNamedCrewsBefore(farmId: UUID, harvestId: UUID, onOrBefore: java.time.LocalDate): List<HarvestLabourEntity>
}
