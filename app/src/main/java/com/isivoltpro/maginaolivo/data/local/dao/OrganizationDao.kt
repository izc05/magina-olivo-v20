package com.isivoltpro.maginaolivo.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.isivoltpro.maginaolivo.data.local.entity.AgriculturalOrganizationEntity
import com.isivoltpro.maginaolivo.data.local.entity.OrganizationRoleEntity
import java.util.UUID
import kotlinx.coroutines.flow.Flow

@Dao
interface OrganizationDao {
    @Upsert suspend fun upsert(organization: AgriculturalOrganizationEntity)

    @Query("SELECT * FROM agricultural_organizations WHERE id = :id LIMIT 1")
    suspend fun findById(id: UUID): AgriculturalOrganizationEntity?

    @Query(
        """
        SELECT * FROM agricultural_organizations
        WHERE deleted_at IS NULL
        ORDER BY name COLLATE NOCASE, id
        """,
    )
    fun observeActive(): Flow<List<AgriculturalOrganizationEntity>>

    @Query(
        """
        SELECT o.* FROM agricultural_organizations o
        JOIN organization_roles r ON r.organization_id = o.id
        WHERE o.deleted_at IS NULL AND r.role IN (:roles)
        GROUP BY o.id
        ORDER BY o.name COLLATE NOCASE, o.id
        """,
    )
    fun observeWithAnyRole(roles: List<String>): Flow<List<AgriculturalOrganizationEntity>>

    @Query("SELECT * FROM organization_roles")
    fun observeRoles(): Flow<List<OrganizationRoleEntity>>

    @Query("SELECT role FROM organization_roles WHERE organization_id = :organizationId ORDER BY role")
    suspend fun listRoles(organizationId: UUID): List<String>

    @Query("DELETE FROM organization_roles WHERE organization_id = :organizationId")
    suspend fun deleteRoles(organizationId: UUID)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRoles(roles: List<OrganizationRoleEntity>)

    @Query(
        """
        SELECT * FROM agricultural_organizations
        WHERE workspace_id = :workspaceId AND deleted_at IS NULL AND name = :name COLLATE NOCASE
        LIMIT 1
        """,
    )
    suspend fun findActiveByName(workspaceId: UUID, name: String): AgriculturalOrganizationEntity?
}
