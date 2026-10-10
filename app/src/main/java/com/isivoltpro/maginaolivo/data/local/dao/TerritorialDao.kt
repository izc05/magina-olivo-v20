package com.isivoltpro.maginaolivo.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.isivoltpro.maginaolivo.data.local.entity.CommunityWaterNoticeEntity
import com.isivoltpro.maginaolivo.data.local.entity.IrrigationCommunityEntity
import com.isivoltpro.maginaolivo.data.local.entity.PersonalIrrigationPlanEntity
import com.isivoltpro.maginaolivo.data.local.entity.TerritorialMunicipalityEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TerritorialDao {
    // Municipios
    @Query("SELECT * FROM territorial_municipalities WHERE active = 1 ORDER BY name ASC")
    fun getAllActiveMunicipalities(): Flow<List<TerritorialMunicipalityEntity>>

    @Query("SELECT * FROM territorial_municipalities WHERE slug = :slug AND active = 1 LIMIT 1")
    suspend fun getMunicipalityBySlug(slug: String): TerritorialMunicipalityEntity?

    @Query("SELECT * FROM territorial_municipalities WHERE ine_code = :ineCode AND active = 1 LIMIT 1")
    suspend fun getMunicipalityByIneCode(ineCode: String): TerritorialMunicipalityEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMunicipalities(municipalities: List<TerritorialMunicipalityEntity>)

    // Comunidades de Regantes
    @Query("SELECT * FROM irrigation_communities WHERE primary_municipality_slug = :municipalitySlug AND active = 1 ORDER BY officialName ASC")
    fun getCommunitiesByMunicipality(municipalitySlug: String): Flow<List<IrrigationCommunityEntity>>

    @Query("SELECT * FROM irrigation_communities WHERE id = :id AND active = 1 LIMIT 1")
    suspend fun getCommunityById(id: String): IrrigationCommunityEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCommunities(communities: List<IrrigationCommunityEntity>)

    // Avisos Comunitarios de Riego
    @Query("SELECT * FROM community_water_notices WHERE community_id = :communityId AND status = 'published' ORDER BY starts_at_epoch_ms DESC")
    fun getPublishedNoticesByCommunity(communityId: String): Flow<List<CommunityWaterNoticeEntity>>

    @Query("SELECT * FROM community_water_notices WHERE community_id = :communityId AND status = :status ORDER BY starts_at_epoch_ms DESC")
    fun getNoticesByCommunityAndStatus(communityId: String, status: String): Flow<List<CommunityWaterNoticeEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotices(notices: List<CommunityWaterNoticeEntity>)

    // Plan de Riego Personal (Agenda Offline Privada)
    @Query("SELECT * FROM personal_irrigation_plans WHERE workspace_id = :workspaceId ORDER BY scheduled_at_epoch_ms ASC")
    fun getPersonalPlans(workspaceId: String): Flow<List<PersonalIrrigationPlanEntity>>

    @Query("SELECT * FROM personal_irrigation_plans WHERE workspace_id = :workspaceId AND plot_id = :plotId ORDER BY scheduled_at_epoch_ms ASC")
    fun getPersonalPlansForPlot(workspaceId: String, plotId: String): Flow<List<PersonalIrrigationPlanEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPersonalPlan(plan: PersonalIrrigationPlanEntity)

    @Query("DELETE FROM personal_irrigation_plans WHERE id = :id AND workspace_id = :workspaceId")
    suspend fun deletePersonalPlan(id: String, workspaceId: String)
}
