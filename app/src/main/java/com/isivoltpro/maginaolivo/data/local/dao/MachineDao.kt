package com.isivoltpro.maginaolivo.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import com.isivoltpro.maginaolivo.data.local.entity.ActivityMachineEntity
import com.isivoltpro.maginaolivo.data.local.entity.MachineEntity
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow

/** One Activity that used a machine, as its detail lists it. */
data class MachineUseRow(
    val activityId: UUID,
    val activityDate: LocalDate,
    val description: String,
    val startHours: Double?,
    val endHours: Double?,
    val usageHours: Double?,
)

@Dao
interface MachineDao {
    @Upsert suspend fun upsert(machine: MachineEntity)

    @Query("SELECT * FROM machines WHERE id = :id LIMIT 1")
    suspend fun findById(id: UUID): MachineEntity?

    @Query("SELECT * FROM machines WHERE id = :id AND deleted_at IS NULL LIMIT 1")
    fun observeById(id: UUID): Flow<MachineEntity?>

    @Query("SELECT * FROM machines WHERE status = :status AND deleted_at IS NULL ORDER BY name COLLATE NOCASE, id")
    fun observeByStatus(status: String): Flow<List<MachineEntity>>

    @Query(
        """
        SELECT * FROM machines
        WHERE workspace_id = :workspaceId AND name = :name COLLATE NOCASE AND deleted_at IS NULL
        LIMIT 1
        """,
    )
    suspend fun findByName(workspaceId: UUID, name: String): MachineEntity?

    @Query(
        """
        SELECT a.id AS activityId, a.activity_date AS activityDate, a.description AS description,
               am.start_hours AS startHours, am.end_hours AS endHours, am.usage_hours AS usageHours
        FROM activity_machines am
        JOIN activities a ON a.id = am.activity_id
        WHERE am.machine_id = :machineId AND a.deleted_at IS NULL
        ORDER BY a.activity_date DESC, a.id
        """,
    )
    fun observeUses(machineId: UUID): Flow<List<MachineUseRow>>

    @Query("SELECT * FROM activity_machines WHERE activity_id = :activityId")
    suspend fun listForActivity(activityId: UUID): List<ActivityMachineEntity>

    @Query("SELECT * FROM activity_machines WHERE activity_id = :activityId")
    fun observeForActivity(activityId: UUID): Flow<List<ActivityMachineEntity>>

    @Query("DELETE FROM activity_machines WHERE activity_id = :activityId")
    suspend fun deleteForActivity(activityId: UUID)

    @Insert suspend fun insertUses(rows: List<ActivityMachineEntity>)

    @Query("SELECT * FROM machines WHERE id IN (:ids)")
    suspend fun findAll(ids: List<UUID>): List<MachineEntity>
}
