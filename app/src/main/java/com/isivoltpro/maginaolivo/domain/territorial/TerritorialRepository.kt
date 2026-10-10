package com.isivoltpro.maginaolivo.domain.territorial

import com.isivoltpro.maginaolivo.core.common.AppResult
import kotlinx.coroutines.flow.Flow

interface TerritorialRepository {
    /**
     * Observa el catálogo de municipios activos (Jaén y Sierra Mágina).
     */
    fun observeMunicipalities(): Flow<List<TerritorialMunicipality>>

    /**
     * Obtiene el detalle de un municipio por su slug.
     */
    suspend fun getMunicipalityBySlug(slug: String): TerritorialMunicipality?

    /**
     * Observa las localidades/pedanías pertenecientes a un municipio.
     */
    fun observeLocalities(municipalitySlug: String): Flow<List<TerritorialLocality>>

    /**
     * Observa las cooperativas y almazaras verificadas, opcionalmente filtradas por municipio.
     */
    fun observeCooperatives(municipalitySlug: String? = null): Flow<List<TerritorialCooperativeInfo>>

    /**
     * Observa las comunidades de regantes y SATs, opcionalmente filtradas por municipio.
     */
    fun observeIrrigationCommunities(municipalitySlug: String? = null): Flow<List<TerritorialIrrigationCommunityInfo>>

    /**
     * Observa los avisos comunitarios públicos vigentes para una comunidad de regantes.
     */
    fun observeWaterNotices(communityId: String): Flow<List<WaterNotice>>

    /**
     * Observa los planes de riego privados (agenda offline) del workspace actual.
     */
    fun observePersonalPlans(workspaceId: String): Flow<List<PersonalIrrigationPlanItem>>

    /**
     * Guarda o actualiza un turno en el plan personal de riego privado.
     */
    suspend fun savePersonalPlan(plan: PersonalIrrigationPlanItem): AppResult<Unit>

    /**
     * Elimina un turno del plan personal privado.
     */
    suspend fun deletePersonalPlan(id: String, workspaceId: String): AppResult<Unit>

    /**
     * Observa noticias, bandos y publicaciones oficiales del municipio (#532).
     */
    fun observeMunicipalPublications(municipalitySlug: String): Flow<List<MunicipalPublication>>
}
