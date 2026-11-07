package com.isivoltpro.maginaolivo.data.local.dao

import androidx.room.*
import com.isivoltpro.maginaolivo.data.local.entity.*
import com.isivoltpro.maginaolivo.domain.cycle.CycleRecord
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow

@Dao
interface AgriculturalCycleDao {
    @Query("SELECT * FROM agricultural_cycles WHERE farm_id=:farmId ORDER BY start_date DESC,id")
    fun observe(farmId: UUID): Flow<List<AgriculturalCycleEntity>>
    @Query("SELECT * FROM agricultural_cycles WHERE farm_id=:farmId ORDER BY start_date DESC,id")
    suspend fun list(farmId: UUID): List<AgriculturalCycleEntity>
    @Query("SELECT * FROM agricultural_cycles WHERE id=:id")
    suspend fun find(id: UUID): AgriculturalCycleEntity?
    @Query("SELECT cycle_id FROM agricultural_cycle_recollections WHERE campaign_id=:id")
    suspend fun recollectionCycle(id: UUID): UUID?
    @Insert suspend fun insert(cycle: AgriculturalCycleEntity)
    @Update suspend fun update(cycle: AgriculturalCycleEntity)
    @Query("SELECT revision FROM agricultural_cycle_farm_state WHERE farm_id=:farmId")
    suspend fun revision(farmId: UUID): Long?
    @Query("SELECT revision FROM agricultural_cycle_farm_state WHERE farm_id=:farmId")
    fun observeRevision(farmId: UUID): Flow<Long?>
    @Upsert suspend fun setRevision(state: AgriculturalCycleFarmStateEntity)
    @Query("SELECT * FROM agricultural_cycle_operations WHERE id=:id")
    suspend fun operation(id: UUID): AgriculturalCycleOperationEntity?
    @Insert suspend fun insert(operation: AgriculturalCycleOperationEntity)
    @Upsert suspend fun link(row: AgriculturalCycleActivityEntity)
    @Upsert suspend fun link(row: AgriculturalCycleExpenseEntity)
    @Upsert suspend fun link(row: AgriculturalCycleRecollectionEntity)

    @Query(RECORDS) suspend fun records(farmId: UUID, workspaceId: UUID): List<CycleRecord>
    @Query(RECORDS) fun observeRecords(farmId: UUID, workspaceId: UUID): Flow<List<CycleRecord>>
    @Query("""SELECT MAX(d.delivery_date) FROM deliveries d
        JOIN agricultural_cycle_recollections r ON r.campaign_id=d.campaign_id
        WHERE r.cycle_id=:id AND d.deleted_at IS NULL""")
    suspend fun lastDeliveryDate(id: UUID): LocalDate?

    companion object {
        const val RECORDS = """
            SELECT a.id AS id,'ACTIVITY' AS kind,a.description AS title,a.activity_date AS date,
                COALESCE(a.activity_end_date,a.activity_date) AS lastDate,
                COALESCE(l.cycle_id,r.cycle_id) AS cycleId,a.status AS status,a.version AS version
            FROM activities a LEFT JOIN agricultural_cycle_activities l ON l.activity_id=a.id
            LEFT JOIN agricultural_cycle_recollections r ON r.campaign_id=a.campaign_id
            WHERE a.farm_id=:farmId AND a.workspace_id=:workspaceId AND a.deleted_at IS NULL
            UNION ALL
            SELECT e.id,'EXPENSE',e.concept,e.expense_date,e.expense_date,
                COALESCE(al.cycle_id,ar.cycle_id,r.cycle_id,l.cycle_id),e.status,e.version
            FROM expenses e LEFT JOIN agricultural_cycle_expenses l ON l.expense_id=e.id
            LEFT JOIN activities a ON a.id=e.activity_id
            LEFT JOIN agricultural_cycle_activities al ON al.activity_id=a.id
            LEFT JOIN agricultural_cycle_recollections ar ON ar.campaign_id=a.campaign_id
            LEFT JOIN agricultural_cycle_recollections r ON r.campaign_id=e.campaign_id
            WHERE e.farm_id=:farmId AND e.workspace_id=:workspaceId AND e.deleted_at IS NULL
            UNION ALL
            SELECT c.id,'RECOLLECTION',c.name,c.start_date,
                MAX(c.start_date,COALESCE(c.end_date,c.start_date),
                    COALESCE((SELECT MAX(h.harvest_date) FROM harvests h WHERE h.campaign_id=c.id AND h.deleted_at IS NULL),c.start_date),
                    COALESCE((SELECT MAX(d.delivery_date) FROM deliveries d WHERE d.campaign_id=c.id AND d.deleted_at IS NULL),c.start_date)),
                r.cycle_id,c.status,c.version
            FROM campaigns c LEFT JOIN agricultural_cycle_recollections r ON r.campaign_id=c.id
            WHERE c.farm_id=:farmId AND c.workspace_id=:workspaceId AND c.deleted_at IS NULL
            ORDER BY kind,id
        """
    }
}
