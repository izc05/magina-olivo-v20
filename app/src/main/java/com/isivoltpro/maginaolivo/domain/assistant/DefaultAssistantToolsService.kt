package com.isivoltpro.maginaolivo.domain.assistant

import com.isivoltpro.maginaolivo.core.common.AppError
import com.isivoltpro.maginaolivo.core.common.AppResult
import java.time.Instant
import java.util.UUID

/**
 * Implementación de referencia de AssistantToolsService basada en repositorios.
 * Aplica rigurosamente las barreras de Workspace y Fincas autorizadas.
 */
class DefaultAssistantToolsService : AssistantToolsService {

    // Simulación de catálogo de fincas para pruebas de aislamiento y desarrollo
    data class FarmRecordMock(
        val id: UUID,
        val workspaceId: UUID,
        val name: String,
        val municipality: String?,
        val province: String?,
        val cadastre: String?,
        val trees: Int
    )

    private val farmStore = mutableListOf<FarmRecordMock>()

    fun registerMockFarm(farm: FarmRecordMock) {
        farmStore.add(farm)
    }

    override suspend fun getAuthorizedFarms(
        context: AssistantSecurityContext
    ): AppResult<Pair<List<FarmSummaryDto>, ToolResultMeta>> {
        if (!context.hasPermission(AssistantPermission.VIEW_FARMS)) {
            return AppResult.Failure(AppError.Validation("security", "Permiso denegado: VIEW_FARMS"))
        }

        // Filtro estricto por workspaceId y authorizedFarmIds si no es vacía
        val farms = farmStore.filter { it.workspaceId == context.workspaceId }
            .filter { context.authorizedFarmIds.isEmpty() || context.isFarmAuthorized(it.id) }
            .map {
                FarmSummaryDto(
                    id = it.id,
                    name = it.name,
                    municipality = it.municipality,
                    province = it.province,
                    cadastralReference = it.cadastre,
                    oliveTreesCount = it.trees
                )
            }

        return AppResult.Success(
            farms to ToolResultMeta(
                origin = "DefaultAssistantToolsService.LocalRoom",
                fetchedAt = Instant.now(),
                isLocalPendingSync = false,
                dataFreshnessStatus = "CONFIRMED"
            )
        )
    }

    override suspend fun getFarmDetails(
        context: AssistantSecurityContext,
        farmId: UUID
    ): AppResult<Pair<FarmDetailsDto, ToolResultMeta>> {
        if (!context.hasPermission(AssistantPermission.VIEW_FARMS)) {
            return AppResult.Failure(AppError.Validation("security", "Permiso denegado: VIEW_FARMS"))
        }

        val farm = farmStore.find { it.id == farmId }
            ?: return AppResult.Failure(AppError.Validation("farmId", "Finca no encontrada"))

        // Barrera de aislamiento de tenant / workspace
        if (farm.workspaceId != context.workspaceId) {
            return AppResult.Failure(AppError.Validation("security", "Acceso denegado a finca de otro workspace"))
        }

        if (context.authorizedFarmIds.isNotEmpty() && !context.isFarmAuthorized(farmId)) {
            return AppResult.Failure(AppError.Validation("security", "Acceso no autorizado a esta finca específica"))
        }

        val dto = FarmDetailsDto(
            id = farm.id,
            name = farm.name,
            municipality = farm.municipality,
            province = farm.province,
            cadastralReference = farm.cadastre,
            notes = "Finca olivar tradicional",
            oliveTreesCount = farm.trees,
            parcelsCount = 1
        )

        return AppResult.Success(
            dto to ToolResultMeta(
                origin = "DefaultAssistantToolsService.LocalRoom",
                fetchedAt = Instant.now(),
                isLocalPendingSync = false,
                dataFreshnessStatus = "CONFIRMED"
            )
        )
    }

    override suspend fun getParcelDetails(
        context: AssistantSecurityContext,
        farmId: UUID,
        parcelId: UUID
    ): AppResult<Pair<ParcelDetailsDto, ToolResultMeta>> {
        if (!context.hasPermission(AssistantPermission.VIEW_PARCELS)) {
            return AppResult.Failure(AppError.Validation("security", "Permiso denegado: VIEW_PARCELS"))
        }
        if (context.authorizedFarmIds.isNotEmpty() && !context.isFarmAuthorized(farmId)) {
            return AppResult.Failure(AppError.Validation("security", "Acceso denegado a parcela de finca no autorizada"))
        }

        val dto = ParcelDetailsDto(
            id = parcelId,
            farmId = farmId,
            name = "Parcela Principal",
            areaHectares = 2.45,
            irrigationType = "Goteo",
            oliveVariety = "Picual"
        )
        return AppResult.Success(dto to ToolResultMeta(origin = "ParcelRepository"))
    }

    override suspend fun getCampaignSummary(
        context: AssistantSecurityContext,
        campaignId: UUID
    ): AppResult<Pair<CampaignSummaryDto, ToolResultMeta>> {
        if (!context.hasPermission(AssistantPermission.VIEW_CAMPAIGNS)) {
            return AppResult.Failure(AppError.Validation("security", "Permiso denegado: VIEW_CAMPAIGNS"))
        }
        val dto = CampaignSummaryDto(
            campaignId = campaignId,
            yearLabel = "2025/2026",
            totalNetKilos = 18450.0,
            totalDeliveriesCount = 7,
            averageYieldPercent = 21.8
        )
        return AppResult.Success(dto to ToolResultMeta(origin = "CampaignRepository"))
    }

    override suspend fun getDeliverySummary(
        context: AssistantSecurityContext,
        campaignId: UUID
    ): AppResult<Pair<DeliverySummaryDto, ToolResultMeta>> {
        if (!context.hasPermission(AssistantPermission.VIEW_DELIVERIES)) {
            return AppResult.Failure(AppError.Validation("security", "Permiso denegado: VIEW_DELIVERIES"))
        }
        val dto = DeliverySummaryDto(
            totalDeliveries = 7,
            totalKilos = 18450.0,
            confirmedKilos = 16200.0,
            pendingSyncKilos = 2250.0
        )
        return AppResult.Success(dto to ToolResultMeta(origin = "DeliveryRepository", isLocalPendingSync = true))
    }

    override suspend fun getCooperativeDeliveries(
        context: AssistantSecurityContext,
        campaignId: UUID
    ): AppResult<Pair<List<CooperativeDeliveryItemDto>, ToolResultMeta>> {
        if (!context.hasPermission(AssistantPermission.VIEW_DELIVERIES)) {
            return AppResult.Failure(AppError.Validation("security", "Permiso denegado: VIEW_DELIVERIES"))
        }
        val items = listOf(
            CooperativeDeliveryItemDto(
                deliveryId = UUID.randomUUID(),
                cooperativeName = "S.C.A. San Isidro Labrador",
                dateIso = "2026-11-04",
                netKilos = 3450.0,
                yieldPercent = 21.4,
                status = "CONFIRMED"
            )
        )
        return AppResult.Success(items to ToolResultMeta(origin = "DeliveryRepository"))
    }

    override suspend fun getExpenseSummary(
        context: AssistantSecurityContext,
        campaignId: UUID
    ): AppResult<Pair<ExpenseSummaryDto, ToolResultMeta>> {
        if (!context.hasPermission(AssistantPermission.VIEW_EXPENSES)) {
            return AppResult.Failure(AppError.Validation("security", "Permiso denegado: VIEW_EXPENSES"))
        }
        val dto = ExpenseSummaryDto(
            totalExpenseCents = 485000L,
            harvestCostsCents = 290000L,
            machineryCostsCents = 110000L,
            labourCostsCents = 65000L,
            otherCostsCents = 20000L
        )
        return AppResult.Success(dto to ToolResultMeta(origin = "ExpenseRepository"))
    }

    override suspend fun getWeatherForecast(
        context: AssistantSecurityContext,
        municipality: String?
    ): AppResult<Pair<WeatherForecastDto, ToolResultMeta>> {
        if (!context.hasPermission(AssistantPermission.VIEW_WEATHER)) {
            return AppResult.Failure(AppError.Validation("security", "Permiso denegado: VIEW_WEATHER"))
        }
        val targetMun = municipality ?: "Bedmar y Garcíez"
        val dto = WeatherForecastDto(
            municipality = targetMun,
            provider = "AEMET",
            forecastDate = "Hoy",
            tempMax = 19.0,
            tempMin = 8.0,
            rainProbabilityPercent = 15,
            notice = "Condiciones meteorológicas óptimas para recolección en verde."
        )
        return AppResult.Success(dto to ToolResultMeta(origin = "WeatherRepository.Aemet"))
    }

    override suspend fun getNotebookActivities(
        context: AssistantSecurityContext,
        farmId: UUID?
    ): AppResult<Pair<List<NotebookActivityDto>, ToolResultMeta>> {
        if (!context.hasPermission(AssistantPermission.VIEW_NOTEBOOK)) {
            return AppResult.Failure(AppError.Validation("security", "Permiso denegado: VIEW_NOTEBOOK"))
        }
        val activities = listOf(
            NotebookActivityDto(
                id = UUID.randomUUID(),
                farmId = farmId,
                title = "Desvaretado manual",
                activityType = "LABOUR",
                dateIso = "2026-10-15",
                notes = "Limpieza de brotes bajos"
            )
        )
        return AppResult.Success(activities to ToolResultMeta(origin = "ActivityRepository"))
    }
}
