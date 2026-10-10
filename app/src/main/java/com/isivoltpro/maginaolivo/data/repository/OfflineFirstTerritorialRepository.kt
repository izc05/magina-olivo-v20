package com.isivoltpro.maginaolivo.data.repository

import com.isivoltpro.maginaolivo.core.common.AppError
import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.core.dispatchers.AppDispatchers
import com.isivoltpro.maginaolivo.data.local.MaginaOlivoDatabase
import com.isivoltpro.maginaolivo.data.local.TerritorialCatalogData
import com.isivoltpro.maginaolivo.data.local.entity.PersonalIrrigationPlanEntity
import com.isivoltpro.maginaolivo.domain.territorial.MunicipalPublication
import com.isivoltpro.maginaolivo.domain.territorial.PersonalIrrigationPlanItem
import com.isivoltpro.maginaolivo.domain.territorial.TerritorialCooperativeInfo
import com.isivoltpro.maginaolivo.domain.territorial.TerritorialIrrigationCommunityInfo
import com.isivoltpro.maginaolivo.domain.territorial.TerritorialLocality
import com.isivoltpro.maginaolivo.domain.territorial.TerritorialMunicipality
import com.isivoltpro.maginaolivo.domain.territorial.TerritorialRepository
import com.isivoltpro.maginaolivo.domain.territorial.WaterNotice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class OfflineFirstTerritorialRepository(
    private val database: MaginaOlivoDatabase,
    private val dispatchers: AppDispatchers,
) : TerritorialRepository {

    private val territorialDao = database.territorialDao()

    /**
     * Asegura que el catálogo inicial de municipios y comunidades esté presente en Room.
     * Operación idempotente.
     */
    suspend fun ensureCatalogSeeded() {
        withContext(dispatchers.io) {
            val municipalityEntities = TerritorialCatalogData.toMunicipalityEntities()
            territorialDao.insertMunicipalities(municipalityEntities)

            val communityEntities = TerritorialCatalogData.toCommunityEntities()
            territorialDao.insertCommunities(communityEntities)
        }
    }

    override fun observeMunicipalities(): Flow<List<TerritorialMunicipality>> =
        territorialDao
            .getAllActiveMunicipalities()
            .map { entities ->
                if (entities.isEmpty()) {
                    // Si Room aún no ha sido poblado, devolver los datos canónicos verificados
                    TerritorialCatalogData.MUNICIPALITIES
                } else {
                    entities.map { entity ->
                        val catalogMatch = TerritorialCatalogData.MUNICIPALITIES.find { it.slug == entity.slug }
                        TerritorialMunicipality(
                            slug = entity.slug,
                            name = entity.name,
                            province = entity.province,
                            ineCode = entity.ineCode,
                            aemetCode = entity.aemetCode,
                            comarca = entity.comarca,
                            officialUrl = entity.officialUrl,
                            electronicSeatUrl = entity.electronicSeatUrl,
                            centerLatitude = entity.centerLatitude,
                            centerLongitude = entity.centerLongitude,
                            sourceUrl = entity.sourceUrl,
                            localities = catalogMatch?.localities ?: emptyList(),
                        )
                    }
                }
            }
            .flowOn(dispatchers.io)

    override suspend fun getMunicipalityBySlug(slug: String): TerritorialMunicipality? =
        withContext(dispatchers.io) {
            val entity = territorialDao.getMunicipalityBySlug(slug)
            if (entity != null) {
                val catalogMatch = TerritorialCatalogData.MUNICIPALITIES.find { it.slug == entity.slug }
                TerritorialMunicipality(
                    slug = entity.slug,
                    name = entity.name,
                    province = entity.province,
                    ineCode = entity.ineCode,
                    aemetCode = entity.aemetCode,
                    comarca = entity.comarca,
                    officialUrl = entity.officialUrl,
                    electronicSeatUrl = entity.electronicSeatUrl,
                    centerLatitude = entity.centerLatitude,
                    centerLongitude = entity.centerLongitude,
                    sourceUrl = entity.sourceUrl,
                    localities = catalogMatch?.localities ?: emptyList(),
                )
            } else {
                TerritorialCatalogData.MUNICIPALITIES.find { it.slug == slug }
            }
        }

    override fun observeLocalities(municipalitySlug: String): Flow<List<TerritorialLocality>> =
        flow {
            val municipality = TerritorialCatalogData.MUNICIPALITIES.find { it.slug == municipalitySlug }
            emit(municipality?.localities ?: emptyList())
        }.flowOn(dispatchers.io)

    override fun observeCooperatives(municipalitySlug: String?): Flow<List<TerritorialCooperativeInfo>> =
        flow {
            val all = TerritorialCatalogData.COOPERATIVES
            if (municipalitySlug.isNullOrBlank()) {
                emit(all)
            } else {
                emit(all.filter { it.municipalitySlug == municipalitySlug })
            }
        }.flowOn(dispatchers.io)

    override fun observeIrrigationCommunities(municipalitySlug: String?): Flow<List<TerritorialIrrigationCommunityInfo>> =
        flow {
            val all = TerritorialCatalogData.IRRIGATION_COMMUNITIES
            if (municipalitySlug.isNullOrBlank()) {
                emit(all)
            } else {
                emit(all.filter { it.primaryMunicipalitySlug == municipalitySlug })
            }
        }.flowOn(dispatchers.io)

    override fun observeWaterNotices(communityId: String): Flow<List<WaterNotice>> =
        territorialDao
            .getPublishedNoticesByCommunity(communityId)
            .map { entities ->
                entities.map { entity ->
                    WaterNotice(
                        id = entity.id,
                        communityId = entity.communityId,
                        sectorCode = entity.sectorCode,
                        noticeType = entity.noticeType,
                        title = entity.title,
                        body = entity.body,
                        startsAtEpochMs = entity.startsAtEpochMs,
                        endsAtEpochMs = entity.endsAtEpochMs,
                        sourceType = entity.sourceType,
                        sourceUrl = entity.sourceUrl,
                        status = entity.status,
                        publishedAtEpochMs = entity.publishedAtEpochMs,
                        expiresAtEpochMs = entity.expiresAtEpochMs,
                    )
                }
            }
            .flowOn(dispatchers.io)

    override fun observePersonalPlans(workspaceId: String): Flow<List<PersonalIrrigationPlanItem>> =
        territorialDao
            .getPersonalPlans(workspaceId)
            .map { entities ->
                entities.map { entity ->
                    PersonalIrrigationPlanItem(
                        id = entity.id,
                        workspaceId = entity.workspaceId,
                        plotId = entity.plotId,
                        sectorCode = entity.sectorCode,
                        scheduledAtEpochMs = entity.scheduledAtEpochMs,
                        durationMinutes = entity.durationMinutes,
                        linkedNoticeId = entity.linkedNoticeId,
                        status = entity.status,
                        reminderMinutesBefore = entity.reminderMinutesBefore,
                    )
                }
            }
            .flowOn(dispatchers.io)

    override suspend fun savePersonalPlan(plan: PersonalIrrigationPlanItem): AppResult<Unit> =
        withContext(dispatchers.io) {
            try {
                val entity = PersonalIrrigationPlanEntity(
                    id = plan.id,
                    workspaceId = plan.workspaceId,
                    plotId = plan.plotId,
                    sectorCode = plan.sectorCode,
                    scheduledAtEpochMs = plan.scheduledAtEpochMs,
                    durationMinutes = plan.durationMinutes,
                    linkedNoticeId = plan.linkedNoticeId,
                    status = plan.status,
                    reminderMinutesBefore = plan.reminderMinutesBefore,
                    createdAtEpochMs = System.currentTimeMillis(),
                    updatedAtEpochMs = System.currentTimeMillis(),
                )
                territorialDao.insertPersonalPlan(entity)
                AppResult.Success(Unit)
            } catch (e: Exception) {
                AppResult.Failure(AppError.Storage("save_personal_plan", e))
            }
        }

    override suspend fun deletePersonalPlan(id: String, workspaceId: String): AppResult<Unit> =
        withContext(dispatchers.io) {
            try {
                territorialDao.deletePersonalPlan(id, workspaceId)
                AppResult.Success(Unit)
            } catch (e: Exception) {
                AppResult.Failure(AppError.Storage("delete_personal_plan", e))
            }
        }

    override fun observeMunicipalPublications(municipalitySlug: String): Flow<List<MunicipalPublication>> =
        flow {
            // Contrato preparado para fuentes de ingestión municipal (#532).
            // Devuelve publicaciones oficiales verificadas sin inventar contenido.
            val publications = when (municipalitySlug) {
                "bedmar-y-garciez" -> listOf(
                    MunicipalPublication(
                        id = "pub-bedmar-01",
                        municipalitySlug = "bedmar-y-garciez",
                        category = "aviso_agrario",
                        title = "Información municipal para agricultores de Bedmar y Garcíez",
                        summary = "Trámites, sedes electrónicas y servicios agrarios del Ayuntamiento.",
                        sourceUrl = "https://bedmargarciez.sedelectronica.es/",
                        publishedAtEpochMs = 1775779200000L,
                        isOfficial = true,
                    ),
                )
                "jodar" -> listOf(
                    MunicipalPublication(
                        id = "pub-jodar-01",
                        municipalitySlug = "jodar",
                        category = "aviso_agrario",
                        title = "Información municipal para agricultores de Jódar",
                        summary = "Trámites y servicios del Ayuntamiento de Jódar para el sector agrícola.",
                        sourceUrl = "https://jodar.sedelectronica.es/",
                        publishedAtEpochMs = 1775779200000L,
                        isOfficial = true,
                    ),
                )
                else -> emptyList()
            }
            emit(publications)
        }.flowOn(dispatchers.io)
}
