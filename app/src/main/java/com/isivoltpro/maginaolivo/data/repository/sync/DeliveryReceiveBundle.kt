package com.isivoltpro.maginaolivo.data.repository.sync

import com.isivoltpro.maginaolivo.data.local.model.CampaignStatus
import com.isivoltpro.maginaolivo.data.local.model.FarmStatus
import com.isivoltpro.maginaolivo.data.local.model.RecordStatus
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

class DeliveryReceiveValidationException(message: String) : IllegalArgumentException(message)

data class ReceiveWorkspace(
    val id: UUID,
    val ownerUserId: UUID,
    val name: String,
    val countryCode: String,
    val timezone: String,
    val locale: String,
    val currency: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val version: Long,
)

data class ReceiveFarm(
    val id: UUID,
    val workspaceId: UUID,
    val ownerId: UUID,
    val creatorUserId: UUID,
    val name: String,
    val description: String?,
    val municipality: String?,
    val province: String?,
    val notes: String?,
    val status: FarmStatus,
    val createdAt: Instant,
    val updatedAt: Instant,
    val version: Long,
)

data class ReceiveParcel(
    val id: UUID,
    val workspaceId: UUID,
    val displayName: String,
    val cadastralReference: String?,
    val cadastralPolygon: String?,
    val cadastralParcel: String?,
    val municipality: String?,
    val province: String?,
    val source: String,
    val geometryGeoJson: String?,
    val sourceProvider: String?,
    val sourceImportedAt: Instant?,
    val cadastralAreaM2: Double?,
    val managedAreaM2: Double?,
    val notes: String?,
    val status: RecordStatus,
    val oliveTreeCount: Int?,
    val variety: String?,
    val irrigationSystem: String?,
    val irrigationNetwork: String?,
    val irrigationSector: String?,
    val irrigationDays: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val version: Long,
)

data class ReceiveMembership(
    val id: UUID,
    val workspaceId: UUID,
    val farmId: UUID,
    val parcelId: UUID,
    val validFrom: Instant,
    val validUntil: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val version: Long,
)

data class ReceiveCampaign(
    val id: UUID,
    val workspaceId: UUID,
    val farmId: UUID,
    val name: String,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    val status: CampaignStatus,
    val snapshotFormat: Int,
    val notes: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val version: Long,
)

data class ReceiveCampaignParcel(
    val id: UUID,
    val workspaceId: UUID,
    val campaignId: UUID,
    val parcelId: UUID,
    val farmIdAtStart: UUID,
    val farmNameAtStart: String,
    val parcelNameAtStart: String,
    val managedAreaM2AtStart: Double?,
    val cadastralReferenceAtStart: String?,
    val geometryGeoJsonSnapshot: String?,
    val originCreatedAt: Instant,
    val originUpdatedAt: Instant,
    val originVersion: Long,
    val originEvidenceSha256: String,
)

data class ReceiveHarvest(
    val id: UUID,
    val workspaceId: UUID,
    val farmId: UUID,
    val campaignId: UUID,
    val harvestDate: LocalDate,
    val weightGrams: Long,
    val destination: String?,
    val notes: String?,
    val collectionMethod: String?,
    val workerCount: Int?,
    val machineryText: String?,
    val dayOrigin: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val version: Long,
)

data class ReceiveHarvestParcel(
    val id: UUID,
    val workspaceId: UUID,
    val harvestId: UUID,
    val parcelId: UUID,
    val campaignParcelId: UUID?,
    val parcelNameAtHarvest: String,
    val weightGrams: Long?,
    val allocationMode: String,
    val originCreatedAt: Instant,
    val originUpdatedAt: Instant,
    val originVersion: Long,
    val originEvidenceSha256: String,
)

data class ReceiveDelivery(
    val id: UUID,
    val workspaceId: UUID,
    val farmId: UUID,
    val campaignId: UUID,
    val harvestId: UUID,
    val deliveryDate: LocalDate,
    val deliveryTime: String?,
    val destinationOrganizationId: UUID?,
    val destinationName: String,
    val netGrams: Long,
    val grossGrams: Long?,
    val tareGrams: Long?,
    val deliveryNumber: String?,
    val ticketNumber: String?,
    val source: String,
    val notes: String?,
    val snapshotFormat: Int,
    val harvestOrigin: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val version: Long,
)

data class ReceiveDeliveryParcel(
    val id: UUID,
    val workspaceId: UUID,
    val deliveryId: UUID,
    val parcelId: UUID,
    val campaignParcelId: UUID?,
    val parcelNameAtDelivery: String,
    val weightGrams: Long?,
    val allocationMode: String,
    val originCreatedAt: Instant,
    val originUpdatedAt: Instant,
    val originVersion: Long,
    val originEvidenceSha256: String,
)

data class DeliveryReceiveBundle(
    val receiveContractVersion: Int,
    val issuer: String,
    val accountId: UUID,
    val headPayloadHash: String,
    val headVersion: Long,
    val workspace: ReceiveWorkspace,
    val farm: ReceiveFarm,
    val parcel: ReceiveParcel,
    val membership: ReceiveMembership,
    val campaign: ReceiveCampaign,
    val campaignParcel: ReceiveCampaignParcel,
    val harvest: ReceiveHarvest,
    val harvestParcel: ReceiveHarvestParcel,
    val delivery: ReceiveDelivery,
    val deliveryParcel: ReceiveDeliveryParcel,
)

object DeliveryReceiveBundleParser {
    val SHA256_REGEX = Regex("^[0-9a-f]{64}$")

    fun parse(
        jsonString: String,
        expectedAccountId: UUID? = null,
        expectedIssuer: String? = null,
    ): DeliveryReceiveBundle {
        val root = try {
            val element = Json.parseToJsonElement(jsonString)
            when (element) {
                is JsonObject -> element
                is JsonArray -> element.firstOrNull()?.jsonObject
                    ?: throw DeliveryReceiveValidationException("Empty JSON array")
                else -> throw DeliveryReceiveValidationException("Root must be a JSON object")
            }
        } catch (e: Exception) {
            if (e is DeliveryReceiveValidationException) throw e
            throw DeliveryReceiveValidationException("Invalid JSON: ${e.message}")
        }
        return parse(root, expectedAccountId, expectedIssuer)
    }

    fun parse(
        root: JsonObject,
        expectedAccountId: UUID? = null,
        expectedIssuer: String? = null,
    ): DeliveryReceiveBundle {

        val contractVersion = reqInt(root, "receive_contract_version")
        if (contractVersion != 1) {
            throw DeliveryReceiveValidationException("Unsupported receive_contract_version: $contractVersion (expected 1)")
        }

        val issuer = reqString(root, "issuer")
        if (expectedIssuer != null && issuer != expectedIssuer) {
            throw DeliveryReceiveValidationException("Issuer mismatch: '$issuer' != expected '$expectedIssuer'")
        }

        val accountId = reqUuid(root, "account_id")
        if (expectedAccountId != null && accountId != expectedAccountId) {
            throw DeliveryReceiveValidationException("Account mismatch: '$accountId' != expected '$expectedAccountId'")
        }

        val headPayloadHash = reqString(root, "head_payload_hash")
        if (!SHA256_REGEX.matches(headPayloadHash)) {
            throw DeliveryReceiveValidationException("Invalid head_payload_hash: must be 64 lowercase hex chars")
        }

        val headVersion = reqLong(root, "head_version")
        if (headVersion < 1L) {
            throw DeliveryReceiveValidationException("head_version must be >= 1, was $headVersion")
        }

        val wsObj = reqObject(root, "workspace")
        val farmObj = reqObject(root, "farm")
        val parcelObj = reqObject(root, "parcel")
        val memObj = reqObject(root, "membership")
        val campObj = reqObject(root, "campaign")
        val cpObj = reqObject(root, "campaign_parcel")
        val harvObj = reqObject(root, "harvest")
        val hpObj = reqObject(root, "harvest_parcel")
        val delObj = reqObject(root, "delivery")
        val dpObj = reqObject(root, "delivery_parcel")

        // 1. Workspace
        val workspace = ReceiveWorkspace(
            id = reqUuid(wsObj, "id"),
            ownerUserId = reqUuid(wsObj, "owner_user_id"),
            name = reqString(wsObj, "name"),
            countryCode = optString(wsObj, "country_code") ?: "ES",
            timezone = optString(wsObj, "timezone") ?: "Europe/Madrid",
            locale = optString(wsObj, "locale") ?: "es-ES",
            currency = optString(wsObj, "currency") ?: "EUR",
            createdAt = reqInstant(wsObj, "created_at"),
            updatedAt = reqInstant(wsObj, "updated_at"),
            version = reqLong(wsObj, "version"),
        )
        if (workspace.ownerUserId != accountId) {
            throw DeliveryReceiveValidationException("Workspace owner ${workspace.ownerUserId} != accountId $accountId")
        }

        // 2. Farm
        val farmStatusStr = reqString(farmObj, "status")
        val farmStatus = try {
            FarmStatus.valueOf(farmStatusStr)
        } catch (_: Exception) {
            throw DeliveryReceiveValidationException("Unknown farm status: $farmStatusStr")
        }
        val farm = ReceiveFarm(
            id = reqUuid(farmObj, "id"),
            workspaceId = reqUuid(farmObj, "workspace_id"),
            ownerId = reqUuid(farmObj, "owner_id"),
            creatorUserId = reqUuid(farmObj, "creator_user_id"),
            name = reqString(farmObj, "name"),
            description = optString(farmObj, "description"),
            municipality = optString(farmObj, "municipality"),
            province = optString(farmObj, "province"),
            notes = optString(farmObj, "notes"),
            status = farmStatus,
            createdAt = reqInstant(farmObj, "created_at"),
            updatedAt = reqInstant(farmObj, "updated_at"),
            version = reqLong(farmObj, "version"),
        )
        if (farm.workspaceId != workspace.id) {
            throw DeliveryReceiveValidationException("Farm workspaceId ${farm.workspaceId} != workspace ${workspace.id}")
        }
        if (optString(farmObj, "cover_image_uri") != null) {
            throw DeliveryReceiveValidationException("Unsupported cover_image_uri on farm")
        }

        // 3. Parcel
        val parcelStatusStr = optString(parcelObj, "status") ?: "ACTIVE"
        val parcelStatus = try {
            RecordStatus.valueOf(parcelStatusStr)
        } catch (_: Exception) {
            throw DeliveryReceiveValidationException("Unknown parcel status: $parcelStatusStr")
        }
        val parcel = ReceiveParcel(
            id = reqUuid(parcelObj, "id"),
            workspaceId = reqUuid(parcelObj, "workspace_id"),
            displayName = reqString(parcelObj, "display_name"),
            cadastralReference = optString(parcelObj, "cadastral_reference"),
            cadastralPolygon = optString(parcelObj, "cadastral_polygon"),
            cadastralParcel = optString(parcelObj, "cadastral_parcel"),
            municipality = optString(parcelObj, "municipality"),
            province = optString(parcelObj, "province"),
            source = optString(parcelObj, "source") ?: "MANUAL",
            geometryGeoJson = optString(parcelObj, "geometry_geo_json"),
            sourceProvider = optString(parcelObj, "source_provider"),
            sourceImportedAt = optInstant(parcelObj, "source_imported_at"),
            cadastralAreaM2 = optDouble(parcelObj, "cadastral_area_m2"),
            managedAreaM2 = optDouble(parcelObj, "managed_area_m2"),
            notes = optString(parcelObj, "notes"),
            status = parcelStatus,
            oliveTreeCount = optInt(parcelObj, "olive_tree_count"),
            variety = optString(parcelObj, "variety"),
            irrigationSystem = optString(parcelObj, "irrigation_system"),
            irrigationNetwork = optString(parcelObj, "irrigation_network"),
            irrigationSector = optString(parcelObj, "irrigation_sector"),
            irrigationDays = optString(parcelObj, "irrigation_days"),
            createdAt = reqInstant(parcelObj, "created_at"),
            updatedAt = reqInstant(parcelObj, "updated_at"),
            version = reqLong(parcelObj, "version"),
        )
        if (parcel.workspaceId != workspace.id) {
            throw DeliveryReceiveValidationException("Parcel workspaceId ${parcel.workspaceId} != workspace ${workspace.id}")
        }

        // 4. Membership
        val membership = ReceiveMembership(
            id = reqUuid(memObj, "id"),
            workspaceId = reqUuid(memObj, "workspace_id"),
            farmId = reqUuid(memObj, "farm_id"),
            parcelId = reqUuid(memObj, "parcel_id"),
            validFrom = reqInstant(memObj, "valid_from"),
            validUntil = optInstant(memObj, "valid_until"),
            createdAt = reqInstant(memObj, "created_at"),
            updatedAt = reqInstant(memObj, "updated_at"),
            version = reqLong(memObj, "version"),
        )
        if (membership.workspaceId != workspace.id) {
            throw DeliveryReceiveValidationException("Membership workspaceId ${membership.workspaceId} != workspace ${workspace.id}")
        }
        if (membership.farmId != farm.id) {
            throw DeliveryReceiveValidationException("Membership farmId ${membership.farmId} != farm ${farm.id}")
        }
        if (membership.parcelId != parcel.id) {
            throw DeliveryReceiveValidationException("Membership parcelId ${membership.parcelId} != parcel ${parcel.id}")
        }
        if (membership.validUntil != null) {
            throw DeliveryReceiveValidationException("Membership validUntil must be null for active delivery")
        }

        // 5. Campaign
        val campStatusStr = reqString(campObj, "status")
        val campStatus = try {
            CampaignStatus.valueOf(campStatusStr)
        } catch (_: Exception) {
            throw DeliveryReceiveValidationException("Unknown campaign status: $campStatusStr")
        }
        val campaignFormat = reqInt(campObj, "snapshot_format")
        if (campaignFormat != 1) {
            throw DeliveryReceiveValidationException("Campaign snapshot_format must be 1, was $campaignFormat")
        }
        val campaign = ReceiveCampaign(
            id = reqUuid(campObj, "id"),
            workspaceId = reqUuid(campObj, "workspace_id"),
            farmId = reqUuid(campObj, "farm_id"),
            name = reqString(campObj, "name"),
            startDate = reqLocalDate(campObj, "start_date"),
            endDate = optLocalDate(campObj, "end_date"),
            status = campStatus,
            snapshotFormat = campaignFormat,
            notes = optString(campObj, "notes"),
            createdAt = reqInstant(campObj, "created_at"),
            updatedAt = reqInstant(campObj, "updated_at"),
            version = reqLong(campObj, "version"),
        )
        if (campaign.workspaceId != workspace.id) {
            throw DeliveryReceiveValidationException("Campaign workspaceId ${campaign.workspaceId} != workspace ${workspace.id}")
        }
        if (campaign.farmId != farm.id) {
            throw DeliveryReceiveValidationException("Campaign farmId ${campaign.farmId} != farm ${farm.id}")
        }

        // 6. CampaignParcel
        val cpOriginCreatedAt = reqInstant(cpObj, "origin_created_at")
        val cpOriginUpdatedAt = reqInstant(cpObj, "origin_updated_at")
        val cpOriginVersion = reqLong(cpObj, "origin_version")
        val cpOriginEvidence = reqString(cpObj, "origin_evidence_sha256")
        validateOrigin(cpOriginCreatedAt, cpOriginUpdatedAt, cpOriginVersion, cpOriginEvidence, "campaign_parcel")

        val campaignParcel = ReceiveCampaignParcel(
            id = reqUuid(cpObj, "id"),
            workspaceId = reqUuid(cpObj, "workspace_id"),
            campaignId = reqUuid(cpObj, "campaign_id"),
            parcelId = reqUuid(cpObj, "parcel_id"),
            farmIdAtStart = reqUuid(cpObj, "farm_id_at_start"),
            farmNameAtStart = reqString(cpObj, "farm_name_at_start"),
            parcelNameAtStart = reqString(cpObj, "parcel_name_at_start"),
            managedAreaM2AtStart = optDouble(cpObj, "managed_area_m2_at_start"),
            cadastralReferenceAtStart = optString(cpObj, "cadastral_reference_at_start"),
            geometryGeoJsonSnapshot = optString(cpObj, "geometry_geo_json_snapshot"),
            originCreatedAt = cpOriginCreatedAt,
            originUpdatedAt = cpOriginUpdatedAt,
            originVersion = cpOriginVersion,
            originEvidenceSha256 = cpOriginEvidence,
        )
        if (campaignParcel.workspaceId != workspace.id) {
            throw DeliveryReceiveValidationException("CampaignParcel workspaceId ${campaignParcel.workspaceId} != workspace ${workspace.id}")
        }
        if (campaignParcel.campaignId != campaign.id) {
            throw DeliveryReceiveValidationException("CampaignParcel campaignId ${campaignParcel.campaignId} != campaign ${campaign.id}")
        }
        if (campaignParcel.parcelId != parcel.id) {
            throw DeliveryReceiveValidationException("CampaignParcel parcelId ${campaignParcel.parcelId} != parcel ${parcel.id}")
        }
        if (campaignParcel.farmIdAtStart != farm.id) {
            throw DeliveryReceiveValidationException("CampaignParcel farmIdAtStart ${campaignParcel.farmIdAtStart} != farm ${farm.id}")
        }

        // 7. Harvest
        val harvest = ReceiveHarvest(
            id = reqUuid(harvObj, "id"),
            workspaceId = reqUuid(harvObj, "workspace_id"),
            farmId = reqUuid(harvObj, "farm_id"),
            campaignId = reqUuid(harvObj, "campaign_id"),
            harvestDate = reqLocalDate(harvObj, "harvest_date"),
            weightGrams = reqLong(harvObj, "weight_grams"),
            destination = optString(harvObj, "destination"),
            notes = optString(harvObj, "notes"),
            collectionMethod = optString(harvObj, "collection_method"),
            workerCount = optInt(harvObj, "worker_count"),
            machineryText = optString(harvObj, "machinery_text"),
            dayOrigin = optString(harvObj, "day_origin"),
            createdAt = reqInstant(harvObj, "created_at"),
            updatedAt = reqInstant(harvObj, "updated_at"),
            version = reqLong(harvObj, "version"),
        )
        if (harvest.workspaceId != workspace.id) {
            throw DeliveryReceiveValidationException("Harvest workspaceId ${harvest.workspaceId} != workspace ${workspace.id}")
        }
        if (harvest.farmId != farm.id) {
            throw DeliveryReceiveValidationException("Harvest farmId ${harvest.farmId} != farm ${farm.id}")
        }
        if (harvest.campaignId != campaign.id) {
            throw DeliveryReceiveValidationException("Harvest campaignId ${harvest.campaignId} != campaign ${campaign.id}")
        }
        if (harvest.weightGrams <= 0L) {
            throw DeliveryReceiveValidationException("Harvest weight_grams must be > 0, was ${harvest.weightGrams}")
        }

        // 8. HarvestParcel
        val hpOriginCreatedAt = reqInstant(hpObj, "origin_created_at")
        val hpOriginUpdatedAt = reqInstant(hpObj, "origin_updated_at")
        val hpOriginVersion = reqLong(hpObj, "origin_version")
        val hpOriginEvidence = reqString(hpObj, "origin_evidence_sha256")
        validateOrigin(hpOriginCreatedAt, hpOriginUpdatedAt, hpOriginVersion, hpOriginEvidence, "harvest_parcel")

        val hpMode = reqString(hpObj, "allocation_mode")
        val hpWeight = optLong(hpObj, "weight_grams")
        validateAllocation(hpMode, hpWeight, "harvest_parcel")

        val harvestParcel = ReceiveHarvestParcel(
            id = reqUuid(hpObj, "id"),
            workspaceId = reqUuid(hpObj, "workspace_id"),
            harvestId = reqUuid(hpObj, "harvest_id"),
            parcelId = reqUuid(hpObj, "parcel_id"),
            campaignParcelId = optUuid(hpObj, "campaign_parcel_id"),
            parcelNameAtHarvest = reqString(hpObj, "parcel_name_at_harvest"),
            weightGrams = hpWeight,
            allocationMode = hpMode,
            originCreatedAt = hpOriginCreatedAt,
            originUpdatedAt = hpOriginUpdatedAt,
            originVersion = hpOriginVersion,
            originEvidenceSha256 = hpOriginEvidence,
        )
        if (harvestParcel.workspaceId != workspace.id) {
            throw DeliveryReceiveValidationException("HarvestParcel workspaceId ${harvestParcel.workspaceId} != workspace ${workspace.id}")
        }
        if (harvestParcel.harvestId != harvest.id) {
            throw DeliveryReceiveValidationException("HarvestParcel harvestId ${harvestParcel.harvestId} != harvest ${harvest.id}")
        }
        if (harvestParcel.parcelId != parcel.id) {
            throw DeliveryReceiveValidationException("HarvestParcel parcelId ${harvestParcel.parcelId} != parcel ${parcel.id}")
        }
        if (harvestParcel.campaignParcelId != null && harvestParcel.campaignParcelId != campaignParcel.id) {
            throw DeliveryReceiveValidationException("HarvestParcel campaignParcelId ${harvestParcel.campaignParcelId} != campaignParcel ${campaignParcel.id}")
        }

        // 9. Delivery
        val delFormat = reqInt(delObj, "snapshot_format")
        if (delFormat != 2) {
            throw DeliveryReceiveValidationException("Delivery snapshot_format must be 2, was $delFormat")
        }
        if (optUuid(delObj, "destination_organization_id") != null) {
            throw DeliveryReceiveValidationException("Unsupported destination_organization_id in delivery")
        }
        val netGrams = reqLong(delObj, "net_grams")
        if (netGrams <= 0L) {
            throw DeliveryReceiveValidationException("Delivery net_grams must be > 0, was $netGrams")
        }

        val delivery = ReceiveDelivery(
            id = reqUuid(delObj, "id"),
            workspaceId = reqUuid(delObj, "workspace_id"),
            farmId = reqUuid(delObj, "farm_id"),
            campaignId = reqUuid(delObj, "campaign_id"),
            harvestId = reqUuid(delObj, "harvest_id"),
            deliveryDate = reqLocalDate(delObj, "delivery_date"),
            deliveryTime = optString(delObj, "delivery_time"),
            destinationOrganizationId = null,
            destinationName = reqString(delObj, "destination_name"),
            netGrams = netGrams,
            grossGrams = optLong(delObj, "gross_grams"),
            tareGrams = optLong(delObj, "tare_grams"),
            deliveryNumber = optString(delObj, "delivery_number"),
            ticketNumber = optString(delObj, "ticket_number"),
            source = optString(delObj, "source") ?: "MANUAL",
            notes = optString(delObj, "notes"),
            snapshotFormat = delFormat,
            harvestOrigin = optString(delObj, "harvest_origin") ?: optString(delObj, "origin"),
            createdAt = reqInstant(delObj, "created_at"),
            updatedAt = reqInstant(delObj, "updated_at"),
            version = reqLong(delObj, "version"),
        )
        if (delivery.workspaceId != workspace.id) {
            throw DeliveryReceiveValidationException("Delivery workspaceId ${delivery.workspaceId} != workspace ${workspace.id}")
        }
        if (delivery.farmId != farm.id) {
            throw DeliveryReceiveValidationException("Delivery farmId ${delivery.farmId} != farm ${farm.id}")
        }
        if (delivery.campaignId != campaign.id) {
            throw DeliveryReceiveValidationException("Delivery campaignId ${delivery.campaignId} != campaign ${campaign.id}")
        }
        if (delivery.harvestId != harvest.id) {
            throw DeliveryReceiveValidationException("Delivery harvestId ${delivery.harvestId} != harvest ${harvest.id}")
        }
        if (delivery.version != headVersion) {
            throw DeliveryReceiveValidationException("Delivery version ${delivery.version} != head_version $headVersion")
        }

        // 10. DeliveryParcel
        val dpOriginCreatedAt = reqInstant(dpObj, "origin_created_at")
        val dpOriginUpdatedAt = reqInstant(dpObj, "origin_updated_at")
        val dpOriginVersion = reqLong(dpObj, "origin_version")
        val dpOriginEvidence = reqString(dpObj, "origin_evidence_sha256")
        validateOrigin(dpOriginCreatedAt, dpOriginUpdatedAt, dpOriginVersion, dpOriginEvidence, "delivery_parcel")

        val dpMode = reqString(dpObj, "allocation_mode")
        val dpWeight = optLong(dpObj, "weight_grams")
        validateAllocation(dpMode, dpWeight, "delivery_parcel")

        val deliveryParcel = ReceiveDeliveryParcel(
            id = reqUuid(dpObj, "id"),
            workspaceId = reqUuid(dpObj, "workspace_id"),
            deliveryId = reqUuid(dpObj, "delivery_id"),
            parcelId = reqUuid(dpObj, "parcel_id"),
            campaignParcelId = optUuid(dpObj, "campaign_parcel_id"),
            parcelNameAtDelivery = reqString(dpObj, "parcel_name_at_delivery"),
            weightGrams = dpWeight,
            allocationMode = dpMode,
            originCreatedAt = dpOriginCreatedAt,
            originUpdatedAt = dpOriginUpdatedAt,
            originVersion = dpOriginVersion,
            originEvidenceSha256 = dpOriginEvidence,
        )
        if (deliveryParcel.workspaceId != workspace.id) {
            throw DeliveryReceiveValidationException("DeliveryParcel workspaceId ${deliveryParcel.workspaceId} != workspace ${workspace.id}")
        }
        if (deliveryParcel.deliveryId != delivery.id) {
            throw DeliveryReceiveValidationException("DeliveryParcel deliveryId ${deliveryParcel.deliveryId} != delivery ${delivery.id}")
        }
        if (deliveryParcel.parcelId != parcel.id) {
            throw DeliveryReceiveValidationException("DeliveryParcel parcelId ${deliveryParcel.parcelId} != parcel ${parcel.id}")
        }
        if (deliveryParcel.campaignParcelId != null && deliveryParcel.campaignParcelId != campaignParcel.id) {
            throw DeliveryReceiveValidationException("DeliveryParcel campaignParcelId ${deliveryParcel.campaignParcelId} != campaignParcel ${campaignParcel.id}")
        }

        return DeliveryReceiveBundle(
            receiveContractVersion = contractVersion,
            issuer = issuer,
            accountId = accountId,
            headPayloadHash = headPayloadHash,
            headVersion = headVersion,
            workspace = workspace,
            farm = farm,
            parcel = parcel,
            membership = membership,
            campaign = campaign,
            campaignParcel = campaignParcel,
            harvest = harvest,
            harvestParcel = harvestParcel,
            delivery = delivery,
            deliveryParcel = deliveryParcel,
        )
    }

    private fun validateOrigin(
        createdAt: Instant,
        updatedAt: Instant,
        version: Long,
        evidence: String,
        label: String,
    ) {
        if (version <= 0L) {
            throw DeliveryReceiveValidationException("$label origin_version must be > 0, was $version")
        }
        if (createdAt.isAfter(updatedAt)) {
            throw DeliveryReceiveValidationException("$label origin_created_at ($createdAt) is after origin_updated_at ($updatedAt)")
        }
        if (!SHA256_REGEX.matches(evidence)) {
            throw DeliveryReceiveValidationException("$label origin_evidence_sha256 must be 64 lowercase hex chars")
        }
    }

    private fun validateAllocation(mode: String, weightGrams: Long?, label: String) {
        when (mode) {
            "EXACT" -> if (weightGrams == null || weightGrams <= 0L) {
                throw DeliveryReceiveValidationException("$label EXACT allocation requires weight_grams > 0")
            }
            "UNALLOCATED" -> if (weightGrams != null) {
                throw DeliveryReceiveValidationException("$label UNALLOCATED allocation requires null weight_grams, was $weightGrams")
            }
            else -> throw DeliveryReceiveValidationException("$label unknown allocation_mode: $mode")
        }
    }

    // ------------------------------------------------------------- extraction helpers

    private fun reqObject(parent: JsonObject, key: String): JsonObject =
        (parent[key] as? JsonObject)
            ?: throw DeliveryReceiveValidationException("Missing required object '$key'")

    private fun reqString(parent: JsonObject, key: String): String =
        optString(parent, key)
            ?: throw DeliveryReceiveValidationException("Missing required string '$key'")

    private fun optString(parent: JsonObject, key: String): String? {
        val prim = parent[key] as? JsonPrimitive ?: return null
        if (prim == JsonNull) return null
        return prim.contentOrNull
    }

    private fun reqUuid(parent: JsonObject, key: String): UUID {
        val s = reqString(parent, key)
        return try {
            UUID.fromString(s)
        } catch (_: Exception) {
            throw DeliveryReceiveValidationException("Field '$key' is not a valid UUID: '$s'")
        }
    }

    private fun optUuid(parent: JsonObject, key: String): UUID? {
        val s = optString(parent, key) ?: return null
        return try {
            UUID.fromString(s)
        } catch (_: Exception) {
            throw DeliveryReceiveValidationException("Field '$key' is not a valid UUID: '$s'")
        }
    }

    private fun reqLong(parent: JsonObject, key: String): Long =
        optLong(parent, key) ?: throw DeliveryReceiveValidationException("Missing required long '$key'")

    private fun optLong(parent: JsonObject, key: String): Long? {
        val prim = parent[key] as? JsonPrimitive ?: return null
        if (prim == JsonNull) return null
        return prim.longOrNull ?: prim.contentOrNull?.toLongOrNull()
    }

    private fun reqInt(parent: JsonObject, key: String): Int =
        optInt(parent, key) ?: throw DeliveryReceiveValidationException("Missing required int '$key'")

    private fun optInt(parent: JsonObject, key: String): Int? {
        val prim = parent[key] as? JsonPrimitive ?: return null
        if (prim == JsonNull) return null
        return prim.intOrNull ?: prim.contentOrNull?.toIntOrNull()
    }

    private fun optDouble(parent: JsonObject, key: String): Double? {
        val prim = parent[key] as? JsonPrimitive ?: return null
        if (prim == JsonNull) return null
        return prim.doubleOrNull ?: prim.contentOrNull?.toDoubleOrNull()
    }

    private fun reqInstant(parent: JsonObject, key: String): Instant =
        optInstant(parent, key) ?: throw DeliveryReceiveValidationException("Missing required timestamp '$key'")

    private fun optInstant(parent: JsonObject, key: String): Instant? {
        val s = optString(parent, key) ?: return null
        val normalized = s.trim().replace(' ', 'T')
        return try {
            Instant.parse(normalized)
        } catch (_: DateTimeParseException) {
            // Handle postgres with zone e.g. 2026-10-25T00:00:00+00
            try {
                java.time.OffsetDateTime.parse(normalized).toInstant()
            } catch (e: Exception) {
                throw DeliveryReceiveValidationException("Field '$key' has invalid timestamp format: '$s'")
            }
        }
    }

    private fun reqLocalDate(parent: JsonObject, key: String): LocalDate =
        optLocalDate(parent, key) ?: throw DeliveryReceiveValidationException("Missing required date '$key'")

    private fun optLocalDate(parent: JsonObject, key: String): LocalDate? {
        val s = optString(parent, key) ?: return null
        return try {
            LocalDate.parse(s.trim())
        } catch (e: Exception) {
            throw DeliveryReceiveValidationException("Field '$key' has invalid date format: '$s'")
        }
    }
}
