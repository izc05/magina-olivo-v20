package com.isivoltpro.maginaolivo.domain.assistant

import com.isivoltpro.maginaolivo.core.common.AppError
import com.isivoltpro.maginaolivo.core.common.AppResult
import java.time.Instant
import java.util.UUID

/**
 * Metadata que acompaña a toda respuesta estructurada de herramientas.
 */
data class ToolResultMeta(
    val origin: String,
    val fetchedAt: Instant = Instant.now(),
    val isLocalPendingSync: Boolean = false,
    val dataFreshnessStatus: String = "CONFIRMED"
)

data class FarmSummaryDto(
    val id: UUID,
    val name: String,
    val municipality: String?,
    val province: String?,
    val cadastralReference: String?,
    val oliveTreesCount: Int?
)

data class FarmDetailsDto(
    val id: UUID,
    val name: String,
    val municipality: String?,
    val province: String?,
    val cadastralReference: String?,
    val notes: String?,
    val oliveTreesCount: Int?,
    val parcelsCount: Int
)

data class ParcelDetailsDto(
    val id: UUID,
    val farmId: UUID,
    val name: String,
    val areaHectares: Double?,
    val irrigationType: String?,
    val oliveVariety: String?
)

data class CampaignSummaryDto(
    val campaignId: UUID,
    val yearLabel: String,
    val totalNetKilos: Double,
    val totalDeliveriesCount: Int,
    val averageYieldPercent: Double?
)

data class DeliverySummaryDto(
    val totalDeliveries: Int,
    val totalKilos: Double,
    val confirmedKilos: Double,
    val pendingSyncKilos: Double
)

data class CooperativeDeliveryItemDto(
    val deliveryId: UUID,
    val cooperativeName: String,
    val dateIso: String,
    val netKilos: Double,
    val yieldPercent: Double?,
    val status: String
)

data class ExpenseSummaryDto(
    val totalExpenseCents: Long,
    val harvestCostsCents: Long,
    val machineryCostsCents: Long,
    val labourCostsCents: Long,
    val otherCostsCents: Long
)

data class WeatherForecastDto(
    val municipality: String,
    val provider: String,
    val forecastDate: String,
    val tempMax: Double,
    val tempMin: Double,
    val rainProbabilityPercent: Int,
    val notice: String
)

data class NotebookActivityDto(
    val id: UUID,
    val farmId: UUID?,
    val title: String,
    val activityType: String,
    val dateIso: String,
    val notes: String?
)

/**
 * Contrato canónico de herramientas internas de solo lectura expuestas al asistente.
 * Cada invocación exige context: AssistantSecurityContext validado.
 */
interface AssistantToolsService {

    suspend fun getAuthorizedFarms(
        context: AssistantSecurityContext
    ): AppResult<Pair<List<FarmSummaryDto>, ToolResultMeta>>

    suspend fun getFarmDetails(
        context: AssistantSecurityContext,
        farmId: UUID
    ): AppResult<Pair<FarmDetailsDto, ToolResultMeta>>

    suspend fun getParcelDetails(
        context: AssistantSecurityContext,
        farmId: UUID,
        parcelId: UUID
    ): AppResult<Pair<ParcelDetailsDto, ToolResultMeta>>

    suspend fun getCampaignSummary(
        context: AssistantSecurityContext,
        campaignId: UUID
    ): AppResult<Pair<CampaignSummaryDto, ToolResultMeta>>

    suspend fun getDeliverySummary(
        context: AssistantSecurityContext,
        campaignId: UUID
    ): AppResult<Pair<DeliverySummaryDto, ToolResultMeta>>

    suspend fun getCooperativeDeliveries(
        context: AssistantSecurityContext,
        campaignId: UUID
    ): AppResult<Pair<List<CooperativeDeliveryItemDto>, ToolResultMeta>>

    suspend fun getExpenseSummary(
        context: AssistantSecurityContext,
        campaignId: UUID
    ): AppResult<Pair<ExpenseSummaryDto, ToolResultMeta>>

    suspend fun getWeatherForecast(
        context: AssistantSecurityContext,
        municipality: String?
    ): AppResult<Pair<WeatherForecastDto, ToolResultMeta>>

    suspend fun getNotebookActivities(
        context: AssistantSecurityContext,
        farmId: UUID?
    ): AppResult<Pair<List<NotebookActivityDto>, ToolResultMeta>>
}
