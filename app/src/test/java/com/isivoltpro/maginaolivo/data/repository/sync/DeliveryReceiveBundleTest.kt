package com.isivoltpro.maginaolivo.data.repository.sync

import com.isivoltpro.maginaolivo.data.local.model.CampaignStatus
import com.isivoltpro.maginaolivo.data.local.model.FarmStatus
import com.isivoltpro.maginaolivo.data.local.model.RecordStatus
import com.isivoltpro.maginaolivo.feature.account.AuthSession
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Strict contract tests for G2.5 [DeliveryReceiveBundleParser] and [DeliveryRpcAdapter.readOwnDeliveryReceiveBundle].
 * Validates that the full 10-table graph is accepted when valid and that any malformed, crossed,
 * incomplete, foreign or corrupted payload is strictly rejected.
 */
class DeliveryReceiveBundleTest {

    private val accountId = UUID.fromString("008ea900-5f3c-407f-8917-ad72bc9d955b")
    private val foreignAccount = UUID.fromString("49d1dbbb-d974-4eca-8bd7-1e51c83253f5")
    private val deliveryId = UUID.fromString("07db9e31-e48e-4041-8c82-bb875af0c5e4")
    private val farmId = UUID.fromString("72dc9b9e-8814-4d8c-b95d-00b986a3e66e")
    private val campaignId = UUID.fromString("1c053c81-294c-405b-8426-04ca8a18ecf9")
    private val parcelId = UUID.fromString("6243d355-6ce6-4189-a37e-8527fb233abe")
    private val workspaceId = UUID.fromString("7b89b229-4b74-4d30-a6d9-8ea914364495")
    private val harvestId = UUID.fromString("47d2eeff-2330-449d-bf95-9d78666abf69")
    private val membershipId = UUID.fromString("e0110000-0000-0000-0000-000000000001")
    private val campaignParcelId = UUID.fromString("e0110000-0000-0000-0000-000000000002")
    private val harvestParcelId = UUID.fromString("e0110000-0000-0000-0000-000000000003")
    private val deliveryParcelId = UUID.fromString("e0110000-0000-0000-0000-000000000004")

    private val headHash = "e5c3825c96a37a42186b800b7bebc59167fe68d6c738672e20921d2dd5380902"
    private val originEvidence = "1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef"
    private val canonicalIssuer = "https://zubpicdibqbmewxeurhm.auth.test/auth/v1"

    private fun validBundleJson(
        version: Long = 1L,
        contractVersion: Int = 1,
        issuer: String = canonicalIssuer,
        account: UUID = accountId,
        headPayloadHash: String = headHash,
        deliveryGrams: Long = 1540000L,
        harvestGrams: Long = 1540000L,
        overrideWorkspaceId: UUID = workspaceId,
        overrideFarmId: UUID = farmId,
        overrideHarvestId: UUID = harvestId,
        overrideDeliveryId: UUID = deliveryId,
        dpAllocationMode: String = "UNALLOCATED",
        dpWeightGrams: Long? = null,
        hpAllocationMode: String = "UNALLOCATED",
        hpWeightGrams: Long? = null,
        originEvidenceSha256: String = originEvidence,
        originVer: Long = 1L,
        originCreated: String = "2026-10-27T10:00:00Z",
        originUpdated: String = "2026-10-27T10:00:00Z",
        delivVersion: Long = 1L,
    ): String = """
    {
      "receive_contract_version": $contractVersion,
      "issuer": "$issuer",
      "account_id": "$account",
      "head_payload_hash": "$headPayloadHash",
      "head_version": $version,
      "workspace": {
        "id": "$overrideWorkspaceId",
        "owner_user_id": "$account",
        "name": "Finca Principal",
        "country_code": "ES",
        "timezone": "Europe/Madrid",
        "locale": "es-ES",
        "currency": "EUR",
        "created_at": "2026-10-01T00:00:00Z",
        "updated_at": "2026-10-01T00:00:00Z",
        "version": 1
      },
      "farm": {
        "id": "$overrideFarmId",
        "workspace_id": "$overrideWorkspaceId",
        "owner_id": "$account",
        "creator_user_id": "$account",
        "name": "Finca Sol",
        "description": "Finca de ensayo",
        "municipality": "Baeza",
        "province": "Jaén",
        "notes": null,
        "status": "ACTIVE",
        "created_at": "2026-10-01T00:00:00Z",
        "updated_at": "2026-10-01T00:00:00Z",
        "version": 1
      },
      "parcel": {
        "id": "$parcelId",
        "workspace_id": "$overrideWorkspaceId",
        "display_name": "Parcela 1",
        "cadastral_reference": "23009A001000010000AA",
        "cadastral_polygon": "1",
        "cadastral_parcel": "1",
        "municipality": "Baeza",
        "province": "Jaén",
        "source": "MANUAL",
        "geometry_geojson": null,
        "source_provider": null,
        "source_imported_at": null,
        "cadastral_area_m2": 15000.0,
        "managed_area_m2": 15000.0,
        "notes": null,
        "status": "ACTIVE",
        "olive_tree_count": 300,
        "variety": "PICUAL",
        "irrigation_system": "DRIP",
        "irrigation_network": null,
        "irrigation_sector": null,
        "irrigation_days": null,
        "created_at": "2026-10-01T00:00:00Z",
        "updated_at": "2026-10-01T00:00:00Z",
        "version": 1
      },
      "membership": {
        "id": "$membershipId",
        "workspace_id": "$overrideWorkspaceId",
        "farm_id": "$overrideFarmId",
        "parcel_id": "$parcelId",
        "valid_from": "2026-01-01T00:00:00Z",
        "valid_until": null,
        "created_at": "2026-10-01T00:00:00Z",
        "updated_at": "2026-10-01T00:00:00Z",
        "version": 1
      },
      "campaign": {
        "id": "$campaignId",
        "workspace_id": "$overrideWorkspaceId",
        "farm_id": "$overrideFarmId",
        "name": "Campaña 2026/2027",
        "start_date": "2026-10-01",
        "end_date": "2027-09-30",
        "status": "ACTIVE",
        "snapshot_format": 1,
        "notes": null,
        "created_at": "2026-10-01T00:00:00Z",
        "updated_at": "2026-10-01T00:00:00Z",
        "version": 1
      },
      "campaign_parcel": {
        "id": "$campaignParcelId",
        "workspace_id": "$overrideWorkspaceId",
        "campaign_id": "$campaignId",
        "parcel_id": "$parcelId",
        "farm_id_at_start": "$overrideFarmId",
        "farm_name_at_start": "Finca Sol",
        "parcel_name_at_start": "Parcela 1",
        "managed_area_m2_at_start": 15000.0,
        "cadastral_reference_at_start": "23009A001000010000AA",
        "geometry_geo_json_snapshot": null,
        "origin_created_at": "$originCreated",
        "origin_updated_at": "$originUpdated",
        "origin_version": $originVer,
        "origin_evidence_sha256": "$originEvidenceSha256"
      },
      "harvest": {
        "id": "$overrideHarvestId",
        "workspace_id": "$overrideWorkspaceId",
        "farm_id": "$overrideFarmId",
        "campaign_id": "$campaignId",
        "harvest_date": "2026-10-15",
        "weight_grams": $harvestGrams,
        "notes": "Recolección temprana",
        "created_at": "2026-10-15T00:00:00Z",
        "updated_at": "2026-10-15T00:00:00Z",
        "version": 1
      },
      "harvest_parcel": {
        "id": "$harvestParcelId",
        "workspace_id": "$overrideWorkspaceId",
        "harvest_id": "$overrideHarvestId",
        "parcel_id": "$parcelId",
        "campaign_parcel_id": "$campaignParcelId",
        "parcel_name_at_harvest": "Parcela 1",
        "weight_grams": ${hpWeightGrams ?: "null"},
        "allocation_mode": "$hpAllocationMode",
        "origin_created_at": "$originCreated",
        "origin_updated_at": "$originUpdated",
        "origin_version": $originVer,
        "origin_evidence_sha256": "$originEvidenceSha256"
      },
      "delivery": {
        "id": "$overrideDeliveryId",
        "workspace_id": "$overrideWorkspaceId",
        "farm_id": "$overrideFarmId",
        "campaign_id": "$campaignId",
        "harvest_id": "$overrideHarvestId",
        "delivery_date": "2026-10-15",
        "destination_name": "Cooperativa San Isidro",
        "destination_type": "COOPERATIVE",
        "net_grams": $deliveryGrams,
        "status": "DELIVERED",
        "notes": "Pesada QA 1540 kg",
        "snapshot_format": 2,
        "harvest_origin": "MANUAL",
        "created_at": "2026-10-15T14:00:00Z",
        "updated_at": "2026-10-15T14:00:00Z",
        "version": $delivVersion
      },
      "delivery_parcel": {
        "id": "$deliveryParcelId",
        "workspace_id": "$overrideWorkspaceId",
        "delivery_id": "$overrideDeliveryId",
        "parcel_id": "$parcelId",
        "campaign_parcel_id": "$campaignParcelId",
        "parcel_name_at_delivery": "Parcela 1",
        "weight_grams": ${dpWeightGrams ?: "null"},
        "allocation_mode": "$dpAllocationMode",
        "origin_created_at": "$originCreated",
        "origin_updated_at": "$originUpdated",
        "origin_version": $originVer,
        "origin_evidence_sha256": "$originEvidenceSha256"
      }
    }
    """.trimIndent()

    @Test
    fun parseCanonical1540KgBundleSucceedsWithAllFields() {
        val bundle = DeliveryReceiveBundleParser.parse(
            jsonString = validBundleJson(),
            expectedAccountId = accountId,
            expectedIssuer = canonicalIssuer,
        )

        assertEquals(1, bundle.receiveContractVersion)
        assertEquals(canonicalIssuer, bundle.issuer)
        assertEquals(accountId, bundle.accountId)
        assertEquals(headHash, bundle.headPayloadHash)
        assertEquals(1L, bundle.headVersion)

        // Workspace
        assertEquals(workspaceId, bundle.workspace.id)
        assertEquals(accountId, bundle.workspace.ownerUserId)
        assertEquals("Finca Principal", bundle.workspace.name)
        assertEquals("ES", bundle.workspace.countryCode)
        assertEquals("EUR", bundle.workspace.currency)

        // Farm
        assertEquals(farmId, bundle.farm.id)
        assertEquals(workspaceId, bundle.farm.workspaceId)
        assertEquals(FarmStatus.ACTIVE, bundle.farm.status)
        assertEquals("Finca Sol", bundle.farm.name)

        // Parcel
        assertEquals(parcelId, bundle.parcel.id)
        assertEquals("Parcela 1", bundle.parcel.displayName)
        assertEquals("23009A001000010000AA", bundle.parcel.cadastralReference)
        assertEquals(RecordStatus.ACTIVE, bundle.parcel.status)
        assertEquals(300, bundle.parcel.oliveTreeCount)
        assertEquals("PICUAL", bundle.parcel.variety)

        // Membership
        assertEquals(membershipId, bundle.membership.id)
        assertEquals(farmId, bundle.membership.farmId)
        assertEquals(parcelId, bundle.membership.parcelId)

        // Campaign
        assertEquals(campaignId, bundle.campaign.id)
        assertEquals(LocalDate.of(2026, 10, 1), bundle.campaign.startDate)
        assertEquals(CampaignStatus.ACTIVE, bundle.campaign.status)

        // CampaignParcel
        assertEquals(campaignParcelId, bundle.campaignParcel.id)
        assertEquals("Parcela 1", bundle.campaignParcel.parcelNameAtStart)
        assertEquals(originEvidence, bundle.campaignParcel.originEvidenceSha256)
        assertEquals(1L, bundle.campaignParcel.originVersion)

        // Harvest
        assertEquals(harvestId, bundle.harvest.id)
        assertEquals(1540000L, bundle.harvest.weightGrams)
        assertEquals(LocalDate.of(2026, 10, 15), bundle.harvest.harvestDate)

        // HarvestParcel
        assertEquals(harvestParcelId, bundle.harvestParcel.id)
        assertEquals("UNALLOCATED", bundle.harvestParcel.allocationMode)
        assertEquals(null, bundle.harvestParcel.weightGrams)

        // Delivery
        assertEquals(deliveryId, bundle.delivery.id)
        assertEquals(1540000L, bundle.delivery.netGrams)
        assertEquals("Cooperativa San Isidro", bundle.delivery.destinationName)
        assertEquals(1L, bundle.delivery.version)

        // DeliveryParcel
        assertEquals(deliveryParcelId, bundle.deliveryParcel.id)
        assertEquals(deliveryId, bundle.deliveryParcel.deliveryId)
        assertEquals(parcelId, bundle.deliveryParcel.parcelId)
        assertEquals("UNALLOCATED", bundle.deliveryParcel.allocationMode)
        assertEquals(null, bundle.deliveryParcel.weightGrams)
    }

    @Test
    fun rejectsContractVersionNotOne() {
        val json = validBundleJson(contractVersion = 2)
        val ex = assertThrows(DeliveryReceiveValidationException::class.java) {
            DeliveryReceiveBundleParser.parse(json, accountId, canonicalIssuer)
        }
        assertTrue(ex.message!!.contains("Unsupported receive_contract_version"))
    }

    @Test
    fun rejectsIssuerMismatch() {
        val json = validBundleJson(issuer = "https://foreign.auth.test/auth/v1")
        val ex = assertThrows(DeliveryReceiveValidationException::class.java) {
            DeliveryReceiveBundleParser.parse(json, accountId, canonicalIssuer)
        }
        assertTrue(ex.message!!.contains("Issuer mismatch"))
    }

    @Test
    fun rejectsAccountIdMismatch() {
        val json = validBundleJson(account = foreignAccount)
        val ex = assertThrows(DeliveryReceiveValidationException::class.java) {
            DeliveryReceiveBundleParser.parse(json, accountId, canonicalIssuer)
        }
        assertTrue(ex.message!!.contains("Account mismatch"))
    }

    @Test
    fun rejectsInvalidHeadPayloadHash() {
        val jsonShort = validBundleJson(headPayloadHash = "abcd")
        assertThrows(DeliveryReceiveValidationException::class.java) {
            DeliveryReceiveBundleParser.parse(jsonShort, accountId, canonicalIssuer)
        }

        val jsonUpper = validBundleJson(headPayloadHash = headHash.uppercase())
        assertThrows(DeliveryReceiveValidationException::class.java) {
            DeliveryReceiveBundleParser.parse(jsonUpper, accountId, canonicalIssuer)
        }
    }

    @Test
    fun rejectsNonPositiveHeadVersion() {
        val json = validBundleJson(version = 0L)
        val ex = assertThrows(DeliveryReceiveValidationException::class.java) {
            DeliveryReceiveBundleParser.parse(json, accountId, canonicalIssuer)
        }
        assertTrue(ex.message!!.contains("head_version must be >= 1"))
    }

    @Test
    fun rejectsDeliveryVersionMismatchWithHeadVersion() {
        val json = validBundleJson(version = 2L, delivVersion = 1L)
        val ex = assertThrows(DeliveryReceiveValidationException::class.java) {
            DeliveryReceiveBundleParser.parse(json, accountId, canonicalIssuer)
        }
        assertTrue(ex.message!!.contains("Delivery version 1 != head_version 2"))
    }

    @Test
    fun rejectsNegativeOrZeroDeliveryNetGrams() {
        val jsonZero = validBundleJson(deliveryGrams = 0L)
        assertThrows(DeliveryReceiveValidationException::class.java) {
            DeliveryReceiveBundleParser.parse(jsonZero, accountId, canonicalIssuer)
        }

        val jsonNeg = validBundleJson(deliveryGrams = -500L)
        assertThrows(DeliveryReceiveValidationException::class.java) {
            DeliveryReceiveBundleParser.parse(jsonNeg, accountId, canonicalIssuer)
        }
    }

    @Test
    fun rejectsNegativeOrZeroHarvestWeightGrams() {
        val json = validBundleJson(harvestGrams = 0L)
        assertThrows(DeliveryReceiveValidationException::class.java) {
            DeliveryReceiveBundleParser.parse(json, accountId, canonicalIssuer)
        }
    }

    @Test
    fun rejectsInvalidOriginMetadata() {
        // origin version <= 0
        val jsonVer0 = validBundleJson(originVer = 0L)
        assertThrows(DeliveryReceiveValidationException::class.java) {
            DeliveryReceiveBundleParser.parse(jsonVer0, accountId, canonicalIssuer)
        }

        // origin created_at > origin_updated_at
        val jsonInverted = validBundleJson(
            originCreated = "2026-10-27T12:00:00Z",
            originUpdated = "2026-10-27T10:00:00Z",
        )
        assertThrows(DeliveryReceiveValidationException::class.java) {
            DeliveryReceiveBundleParser.parse(jsonInverted, accountId, canonicalIssuer)
        }

        // non-hex origin evidence sha256
        val jsonBadHash = validBundleJson(originEvidenceSha256 = "not-a-valid-sha256")
        assertThrows(DeliveryReceiveValidationException::class.java) {
            DeliveryReceiveBundleParser.parse(jsonBadHash, accountId, canonicalIssuer)
        }
    }

    @Test
    fun rejectsInvalidAllocationModeOrExactWithoutWeight() {
        val jsonBadMode = validBundleJson(dpAllocationMode = "UNKNOWN_MODE")
        assertThrows(DeliveryReceiveValidationException::class.java) {
            DeliveryReceiveBundleParser.parse(jsonBadMode, accountId, canonicalIssuer)
        }

        val jsonExactNoWeight = validBundleJson(dpAllocationMode = "EXACT", dpWeightGrams = null)
        assertThrows(DeliveryReceiveValidationException::class.java) {
            DeliveryReceiveBundleParser.parse(jsonExactNoWeight, accountId, canonicalIssuer)
        }

        val jsonExactZeroWeight = validBundleJson(dpAllocationMode = "EXACT", dpWeightGrams = 0L)
        assertThrows(DeliveryReceiveValidationException::class.java) {
            DeliveryReceiveBundleParser.parse(jsonExactZeroWeight, accountId, canonicalIssuer)
        }
    }

    @Test
    fun acceptsExactAllocationWithPositiveWeight() {
        val json = validBundleJson(
            dpAllocationMode = "EXACT",
            dpWeightGrams = 1540000L,
            hpAllocationMode = "EXACT",
            hpWeightGrams = 1540000L,
        )
        val bundle = DeliveryReceiveBundleParser.parse(json, accountId, canonicalIssuer)
        assertEquals("EXACT", bundle.deliveryParcel.allocationMode)
        assertEquals(1540000L, bundle.deliveryParcel.weightGrams)
        assertEquals("EXACT", bundle.harvestParcel.allocationMode)
        assertEquals(1540000L, bundle.harvestParcel.weightGrams)
    }

    @Test
    fun rejectsCrossedWorkspaceRelations() {
        val foreignWorkspace = UUID.fromString("11111111-2222-3333-4444-555555555555")
        val json = validBundleJson(overrideWorkspaceId = foreignWorkspace)
        val modifiedJson = json.replace(
            """"workspace_id": "$foreignWorkspace"""",
            """"workspace_id": "$workspaceId"""",
        )
        assertThrows(DeliveryReceiveValidationException::class.java) {
            DeliveryReceiveBundleParser.parse(modifiedJson, accountId, canonicalIssuer)
        }
    }

    @Test
    fun rejectsMalformedJson() {
        assertThrows(DeliveryReceiveValidationException::class.java) {
            DeliveryReceiveBundleParser.parse("{not valid json", accountId, canonicalIssuer)
        }
        assertThrows(DeliveryReceiveValidationException::class.java) {
            DeliveryReceiveBundleParser.parse("[]", accountId, canonicalIssuer)
        }
        assertThrows(DeliveryReceiveValidationException::class.java) {
            DeliveryReceiveBundleParser.parse("\"string\"", accountId, canonicalIssuer)
        }
    }

    // ------------------------------------------------ Adapter integration tests

    @Test
    fun adapterReadsAndParsesBundleSuccessfully() = runBlocking {
        val session = AuthSession(
            userId = accountId,
            email = "qa@example.test",
            accessToken = "token",
            refreshToken = "refresh",
            expiresAt = Instant.parse("2027-01-01T00:00:00Z"),
        )
        val transport = DeliveryRpcTransport { name, bodyJson, token ->
            assertEquals(DeliveryRpcAdapter.GET_OWN_DELIVERY_RECEIVE_V1, name)
            assertEquals("""{"p_delivery_id":"$deliveryId"}""", bodyJson)
            assertEquals("token", token)
            DeliveryRpcResponse(200, validBundleJson())
        }

        val adapter = DeliveryRpcAdapter(transport)
        val bundle = adapter.readOwnDeliveryReceiveBundle(session, deliveryId, canonicalIssuer)
        assertEquals(deliveryId, bundle.delivery.id)
        assertEquals(1540000L, bundle.delivery.netGrams)
    }

    @Test
    fun adapterRejectsMalformedBundleWithProtocolError() = runBlocking {
        val session = AuthSession(
            userId = accountId,
            email = "qa@example.test",
            accessToken = "token",
            refreshToken = "refresh",
            expiresAt = Instant.parse("2027-01-01T00:00:00Z"),
        )
        val transport = DeliveryRpcTransport { _, _, _ ->
            // Invalid bundle (missing required farm fields)
            DeliveryRpcResponse(200, """{"receive_contract_version": 1}""")
        }

        val adapter = DeliveryRpcAdapter(transport)
        val ex = assertThrows(DeliveryRpcFailure::class.java) {
            runBlocking {
                adapter.readOwnDeliveryReceiveBundle(session, deliveryId, canonicalIssuer)
            }
        }
        assertEquals(DeliveryRpcError.PROTOCOL, ex.error)
    }

    @Test
    fun adapterPropagatesHttp403AsForbidden() = runBlocking {
        val session = AuthSession(
            userId = foreignAccount,
            email = "c@example.test",
            accessToken = "token_c",
            refreshToken = "refresh_c",
            expiresAt = Instant.parse("2027-01-01T00:00:00Z"),
        )
        val transport = DeliveryRpcTransport { _, _, _ ->
            DeliveryRpcResponse(403, """{"code": "42501", "message": "permission denied"}""")
        }

        val adapter = DeliveryRpcAdapter(transport)
        val ex = assertThrows(DeliveryRpcFailure::class.java) {
            runBlocking {
                adapter.readOwnDeliveryReceiveBundle(session, deliveryId, canonicalIssuer)
            }
        }
        assertEquals(DeliveryRpcError.FORBIDDEN, ex.error)
    }
}
