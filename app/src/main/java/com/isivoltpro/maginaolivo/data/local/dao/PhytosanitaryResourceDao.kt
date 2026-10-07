package com.isivoltpro.maginaolivo.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.isivoltpro.maginaolivo.data.local.entity.AgronomicPersonEntity
import com.isivoltpro.maginaolivo.data.local.entity.PhytosanitaryEquipmentProfileEntity
import java.util.UUID
import kotlinx.coroutines.flow.Flow

@Dao
interface PhytosanitaryResourceDao {
    @Upsert suspend fun upsertPerson(person: AgronomicPersonEntity)

    @Query("SELECT * FROM agronomic_people WHERE id = :id LIMIT 1")
    suspend fun findPersonById(id: UUID): AgronomicPersonEntity?

    @Query(
        """
        SELECT * FROM agronomic_people
        WHERE workspace_id = :workspaceId AND status = 'ACTIVE' AND deleted_at IS NULL
        ORDER BY display_name COLLATE NOCASE, id
        """,
    )
    fun observeActivePeople(workspaceId: UUID): Flow<List<AgronomicPersonEntity>>

    @Query(
        """
        SELECT * FROM agronomic_people
        WHERE workspace_id = :workspaceId AND status = 'ACTIVE' AND deleted_at IS NULL
          AND display_name = :displayName COLLATE NOCASE
        LIMIT 1
        """,
    )
    suspend fun findActivePersonByName(workspaceId: UUID, displayName: String): AgronomicPersonEntity?

    @Upsert suspend fun upsertEquipmentProfile(profile: PhytosanitaryEquipmentProfileEntity)

    @Query("SELECT * FROM phytosanitary_equipment_profiles WHERE machine_id = :machineId LIMIT 1")
    suspend fun findEquipmentProfile(machineId: UUID): PhytosanitaryEquipmentProfileEntity?

    @Query(
        """
        SELECT * FROM phytosanitary_equipment_profiles
        WHERE workspace_id = :workspaceId AND machine_id = :machineId AND deleted_at IS NULL
        LIMIT 1
        """,
    )
    fun observeEquipmentProfile(workspaceId: UUID, machineId: UUID): Flow<PhytosanitaryEquipmentProfileEntity?>
}
