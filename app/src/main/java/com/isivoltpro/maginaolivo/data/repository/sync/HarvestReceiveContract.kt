package com.isivoltpro.maginaolivo.data.repository.sync

import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * #808 R3-b: strict parser of the R2 receive contract (`get_own_harvest_manifest_v1`, `get_own_harvest_bundle_v1`).
 * All or nothing: any deviation throws [HarvestReceiveProtocol] and the caller writes nothing. It opens no connection
 * and writes nothing; no receiver exists yet (R3-c), and nothing in shipped code calls it.
 *
 * Exact key sets are those of the R2 proposal as PostgreSQL returns them (captured from a disposable local server). A
 * new server column is therefore a PROTOCOL failure until the contract version changes: never silently ignored.
 */
internal class HarvestReceiveProtocol(val reason: String) : Exception("harvest receive protocol: $reason")

internal object HarvestReceiveContract {
    const val MANIFEST_RPC = "get_own_harvest_manifest_v1"
    const val BUNDLE_RPC = "get_own_harvest_bundle_v1"
    val NAMES = setOf(MANIFEST_RPC, BUNDLE_RPC)
    const val MAX_BYTES = 4 * 1024 * 1024
    const val MAX_MANIFEST_HARVESTS = 2_000
    const val MAX_MANIFEST_DELIVERIES = 20_000
    const val MAX_BUNDLE_DELIVERIES = 500

    val MANIFEST_KEYS = setOf("manifest_contract_version", "issuer", "account_id", "workspace_id", "farm_id", "campaign_id",
        "from", "to", "harvests", "deliveries")
    val MANIFEST_HARVEST_KEYS = setOf("id", "version", "deleted", "day_origin", "harvest_date", "recorded_by_self")
    val MANIFEST_DELIVERY_KEYS = setOf("id", "harvest_id", "version", "deleted", "partition_date", "recorded_by_self")
    val BUNDLE_KEYS = setOf("bundle_contract_version", "issuer", "account_id", "harvest", "harvest_head", "server_weight_grams",
        "server_harvest_parcels", "deliveries")
    val HARVEST_ROW_KEYS = setOf("id", "workspace_id", "farm_id", "campaign_id", "harvest_date", "weight_grams", "destination", "notes",
        "collection_method", "worker_count", "machinery_text", "day_origin", "recorded_by_user_id", "created_at", "updated_at",
        "deleted_at", "version")
    val HEAD_V2_KEYS = setOf("contract_version", "fingerprint_version", "harvest_id", "version", "day_origin", "deleted", "weight_grams",
        "head_operation_id", "head_payload_version", "head_payload_hash", "own_fingerprint", "derived_fingerprint")
    val SERVER_PARCEL_KEYS = setOf("parcel_id", "weight_grams", "allocation_mode")
    val ENTRY_KEYS = setOf("delivery", "parcels", "origin_created_at_exact", "recorded_by_self", "head")
    val DELIVERY_ROW_KEYS = setOf("id", "workspace_id", "farm_id", "campaign_id", "delivery_date", "destination_organization_id",
        "destination_name", "net_grams", "gross_grams", "tare_grams", "delivery_number", "ticket_number", "source", "notes", "harvest_id",
        "delivery_time", "harvest_origin", "recorded_by_user_id", "created_at", "updated_at", "deleted_at", "version", "snapshot_format")
    val DELIVERY_PARCEL_KEYS = setOf("id", "workspace_id", "delivery_id", "parcel_id", "campaign_parcel_id", "parcel_name_at_delivery",
        "weight_grams", "allocation_mode", "origin_version", "origin_created_at", "origin_updated_at", "origin_evidence_sha256")
    val DELIVERY_HEAD_KEYS = setOf("operation_id", "version", "payload_version", "payload_hash", "kind")
}

internal data class ManifestRequest(val issuer: String, val accountId: UUID, val workspaceId: UUID, val farmId: UUID,
    val campaignId: UUID, val from: LocalDate? = null, val to: LocalDate? = null)

internal data class ManifestHarvest(val id: UUID, val version: Long, val deleted: Boolean, val dayOrigin: String?,
    val harvestDate: LocalDate, val recordedBySelf: Boolean)

internal data class ManifestDelivery(val id: UUID, val harvestId: UUID?, val version: Long, val deleted: Boolean,
    val partitionDate: LocalDate, val recordedBySelf: Boolean)

internal data class HarvestManifest(val request: ManifestRequest, val harvests: List<ManifestHarvest>,
    val deliveries: List<ManifestDelivery>)

internal data class BundleRequest(val issuer: String, val accountId: UUID, val harvestId: UUID, val workspaceId: UUID,
    val farmId: UUID, val campaignId: UUID)

internal data class ReceivedHarvest(val id: UUID, val workspaceId: UUID, val farmId: UUID, val campaignId: UUID?, val harvestDate: LocalDate,
    val weightGrams: Long, val destination: String?, val notes: String?, val collectionMethod: String?, val workerCount: Int?,
    val machineryText: String?, val dayOrigin: String?, val recordedByUserId: UUID, val createdAt: Instant, val updatedAt: Instant,
    val deletedAt: Instant?, val version: Long)

internal data class HarvestHeadV2(val harvestId: UUID, val version: Long, val dayOrigin: String?, val deleted: Boolean, val weightGrams: Long,
    val headOperationId: UUID?, val headPayloadVersion: Int?, val headPayloadHash: String?, val ownFingerprint: String,
    val derivedFingerprint: String?)

internal data class ServerHarvestParcel(val parcelId: UUID, val weightGrams: Long?, val allocationMode: String)

internal data class ReceivedDeliveryParcel(val id: UUID, val workspaceId: UUID, val deliveryId: UUID, val parcelId: UUID,
    val campaignParcelId: UUID?, val parcelNameAtDelivery: String, val weightGrams: Long?, val allocationMode: String)

internal enum class DeliveryHeadKind { WRITE, DELETE }

internal data class ReceivedDeliveryHead(val operationId: UUID, val version: Long, val payloadVersion: Int, val payloadHash: String,
    val kind: DeliveryHeadKind)

internal data class ReceivedDelivery(val id: UUID, val workspaceId: UUID, val farmId: UUID, val campaignId: UUID, val deliveryDate: LocalDate,
    val destinationOrganizationId: UUID?, val destinationName: String, val netGrams: Long, val grossGrams: Long?, val tareGrams: Long?,
    val deliveryNumber: String?, val ticketNumber: String?, val source: String, val notes: String?, val harvestId: UUID?,
    val deliveryTime: String?, val harvestOrigin: String?, val recordedByUserId: UUID, val createdAt: Instant, val updatedAt: Instant,
    val deletedAt: Instant?, val version: Long, val parcels: List<ReceivedDeliveryParcel>, val originCreatedAtExact: String?,
    val recordedBySelf: Boolean, val head: ReceivedDeliveryHead?)

internal data class HarvestBundle(val request: BundleRequest, val harvest: ReceivedHarvest, val head: HarvestHeadV2,
    val serverWeightGrams: Long, val serverParcels: List<ServerHarvestParcel>, val deliveries: List<ReceivedDelivery>)

internal object HarvestReceiveParser {
    private val UUID_TEXT = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")
    private val DATE_TEXT = Regex("^[0-9]{4}-[0-9]{2}-[0-9]{2}$")
    private val HASH_TEXT = Regex("^[0-9a-f]{64}$")
    private val INTEGER = Regex("^-?(0|[1-9][0-9]*)$")

    fun parseManifest(body: String, request: ManifestRequest): HarvestManifest {
        val root = rootOf(body)
        keys(root, HarvestReceiveContract.MANIFEST_KEYS, "manifest")
        if (long(root, "manifest_contract_version") != 1L) fail("manifest_contract_version")
        identity(root, request.issuer, request.accountId)
        if (uuid(root, "workspace_id") != request.workspaceId || uuid(root, "farm_id") != request.farmId ||
            uuid(root, "campaign_id") != request.campaignId) fail("scope")
        if (dateOrNull(root, "from") != request.from || dateOrNull(root, "to") != request.to) fail("partition")
        val harvests = array(root, "harvests").map { element ->
            val row = obj(element, HarvestReceiveContract.MANIFEST_HARVEST_KEYS, "manifest.harvest")
            ManifestHarvest(uuid(row, "id"), version(row), bool(row, "deleted"), textOrNull(row, "day_origin"), date(row, "harvest_date"),
                bool(row, "recorded_by_self"))
        }
        val deliveries = array(root, "deliveries").map { element ->
            val row = obj(element, HarvestReceiveContract.MANIFEST_DELIVERY_KEYS, "manifest.delivery")
            ManifestDelivery(uuid(row, "id"), uuidOrNull(row, "harvest_id"), version(row), bool(row, "deleted"), date(row, "partition_date"),
                bool(row, "recorded_by_self"))
        }
        if (harvests.size > HarvestReceiveContract.MAX_MANIFEST_HARVESTS ||
            deliveries.size > HarvestReceiveContract.MAX_MANIFEST_DELIVERIES) fail("limit")
        unique(harvests.map { it.id }, "harvest ids"); unique(deliveries.map { it.id }, "delivery ids")
        val harvestIds = harvests.associateBy { it.id }
        for (d in deliveries) {
            val parent = d.harvestId?.let { harvestIds[it] ?: fail("delivery outside the manifest Jornadas") }
            // A Pesada is partitioned by its Jornada's date, or by its own date without one.
            if (parent != null && parent.harvestDate != d.partitionDate) fail("partition_date")
        }
        for (date in harvests.map { it.harvestDate } + deliveries.map { it.partitionDate }) {
            if ((request.from != null && date < request.from) || (request.to != null && date > request.to)) fail("out of partition")
        }
        return HarvestManifest(request, harvests, deliveries)
    }

    fun parseBundle(body: String, request: BundleRequest): HarvestBundle {
        val root = rootOf(body)
        keys(root, HarvestReceiveContract.BUNDLE_KEYS, "bundle")
        if (long(root, "bundle_contract_version") != 1L) fail("bundle_contract_version")
        identity(root, request.issuer, request.accountId)
        val h = obj(root["harvest"], HarvestReceiveContract.HARVEST_ROW_KEYS, "harvest")
        val harvest = ReceivedHarvest(uuid(h, "id"), uuid(h, "workspace_id"), uuid(h, "farm_id"), uuidOrNull(h, "campaign_id"),
            date(h, "harvest_date"), nonNegative(h, "weight_grams"), textOrNull(h, "destination"), textOrNull(h, "notes"),
            textOrNull(h, "collection_method"), intOrNull(h, "worker_count"), textOrNull(h, "machinery_text"), textOrNull(h, "day_origin"),
            uuid(h, "recorded_by_user_id"), instant(h, "created_at"), instant(h, "updated_at"), instantOrNull(h, "deleted_at"), version(h))
        if (harvest.id != request.harvestId || harvest.workspaceId != request.workspaceId || harvest.farmId != request.farmId ||
            harvest.campaignId != request.campaignId) fail("harvest scope")
        val head = parseHead(obj(root["harvest_head"], HarvestReceiveContract.HEAD_V2_KEYS, "harvest_head"), harvest)
        val serverWeight = nonNegative(root, "server_weight_grams")
        if (serverWeight != harvest.weightGrams) fail("server_weight_grams")
        val serverParcels = array(root, "server_harvest_parcels").map { element ->
            val row = obj(element, HarvestReceiveContract.SERVER_PARCEL_KEYS, "server_harvest_parcel")
            ServerHarvestParcel(uuid(row, "parcel_id"), nonNegativeOrNull(row, "weight_grams"), allocation(row))
        }
        unique(serverParcels.map { it.parcelId }, "server parcel ids")
        if (serverParcels.map { it.parcelId.toString() } != serverParcels.map { it.parcelId.toString() }.sorted()) fail("server parcel order")
        val deliveries = array(root, "deliveries").map { parseEntry(it, harvest) }
        if (deliveries.size > HarvestReceiveContract.MAX_BUNDLE_DELIVERIES) fail("limit")
        unique(deliveries.map { it.id }, "delivery ids")
        if (deliveries.map { it.id.toString() } != deliveries.map { it.id.toString() }.sorted()) fail("delivery order")
        return HarvestBundle(request, harvest, head, serverWeight, serverParcels, deliveries)
    }

    private fun parseHead(row: JsonObject, harvest: ReceivedHarvest): HarvestHeadV2 {
        if (long(row, "contract_version") != 2L || long(row, "fingerprint_version") != 1L) fail("harvest_head version")
        val head = HarvestHeadV2(uuid(row, "harvest_id"), version(row), textOrNull(row, "day_origin"), bool(row, "deleted"),
            nonNegative(row, "weight_grams"), uuidOrNull(row, "head_operation_id"), intOrNull(row, "head_payload_version"),
            hashOrNull(row, "head_payload_hash"), hashOrNull(row, "own_fingerprint") ?: fail("own_fingerprint"), hashOrNull(row, "derived_fingerprint"))
        val partial = listOf(head.headOperationId, head.headPayloadVersion, head.headPayloadHash).count { it == null }
        if (partial != 0 && partial != 3) fail("harvest_head partial")
        if (head.harvestId != harvest.id || head.version != harvest.version || head.dayOrigin != harvest.dayOrigin ||
            head.deleted != (harvest.deletedAt != null) || head.weightGrams != harvest.weightGrams) fail("harvest_head row")
        if ((harvest.dayOrigin == "AUTO_DAY") != (head.derivedFingerprint != null)) fail("derived_fingerprint presence")
        return head
    }

    private fun parseEntry(element: JsonElement, harvest: ReceivedHarvest): ReceivedDelivery {
        val entry = obj(element, HarvestReceiveContract.ENTRY_KEYS, "delivery entry")
        val d = obj(entry["delivery"], HarvestReceiveContract.DELIVERY_ROW_KEYS, "delivery")
        val id = uuid(d, "id")
        val workspaceId = uuid(d, "workspace_id")
        val parcels = (entry["parcels"] as? JsonArray ?: fail("parcels")).map { child ->
            val p = obj(child, HarvestReceiveContract.DELIVERY_PARCEL_KEYS, "delivery parcel")
            ReceivedDeliveryParcel(uuid(p, "id"), uuid(p, "workspace_id"), uuid(p, "delivery_id"), uuid(p, "parcel_id"),
                uuidOrNull(p, "campaign_parcel_id"), text(p, "parcel_name_at_delivery"), nonNegativeOrNull(p, "weight_grams"), allocation(p))
        }
        if (parcels.any { it.deliveryId != id || it.workspaceId != workspaceId }) fail("parcel owner")
        unique(parcels.map { it.parcelId }, "delivery parcel ids")
        val delivery = ReceivedDelivery(id, workspaceId, uuid(d, "farm_id"), uuid(d, "campaign_id"), date(d, "delivery_date"),
            uuidOrNull(d, "destination_organization_id"), text(d, "destination_name"), nonNegative(d, "net_grams"),
            nonNegativeOrNull(d, "gross_grams"), nonNegativeOrNull(d, "tare_grams"), textOrNull(d, "delivery_number"),
            textOrNull(d, "ticket_number"), text(d, "source"), textOrNull(d, "notes"), uuidOrNull(d, "harvest_id"),
            textOrNull(d, "delivery_time"), textOrNull(d, "harvest_origin"), uuid(d, "recorded_by_user_id"), instant(d, "created_at"),
            instant(d, "updated_at"), instantOrNull(d, "deleted_at"), version(d), parcels, textOrNull(entry, "origin_created_at_exact"),
            bool(entry, "recorded_by_self"), parseDeliveryHead(entry["head"]))
        if (delivery.harvestId != harvest.id || delivery.workspaceId != harvest.workspaceId || delivery.farmId != harvest.farmId ||
            delivery.campaignId != harvest.campaignId) fail("delivery scope")
        delivery.head?.let { head ->
            // Aclaración 2 (R2): the head is at the row's version; anything else is out of contract.
            if (head.version != delivery.version) fail("head version")
            // `kind` is derived by the server from deleted_at in the same snapshot; it must agree.
            if ((head.kind == DeliveryHeadKind.DELETE) != (delivery.deletedAt != null)) fail("head kind")
        }
        return delivery
    }

    private fun parseDeliveryHead(element: JsonElement?): ReceivedDeliveryHead? {
        if (element == null) fail("head")
        if (element == JsonNull) return null
        val head = obj(element, HarvestReceiveContract.DELIVERY_HEAD_KEYS, "delivery head")
        val kind = when (text(head, "kind")) { "WRITE" -> DeliveryHeadKind.WRITE; "DELETE" -> DeliveryHeadKind.DELETE; else -> fail("kind") }
        val payloadVersion = long(head, "payload_version") ?: fail("payload_version")
        if (payloadVersion !in 1L..9L) fail("payload_version")
        return ReceivedDeliveryHead(uuid(head, "operation_id"), version(head), payloadVersion.toInt(),
            hashOrNull(head, "payload_hash") ?: fail("payload_hash"), kind)
    }

    // ---------------------------------------------------------------------------------------------------- helpers

    private fun rootOf(body: String): JsonObject {
        if (body.toByteArray(Charsets.UTF_8).size > HarvestReceiveContract.MAX_BYTES) fail("bytes")
        return runCatching { Json.parseToJsonElement(body) }.getOrNull() as? JsonObject ?: fail("json")
    }

    private fun identity(root: JsonObject, issuer: String, accountId: UUID) {
        if (text(root, "issuer") != issuer) fail("issuer")
        if (uuid(root, "account_id") != accountId) fail("account")
    }

    private fun fail(reason: String): Nothing = throw HarvestReceiveProtocol(reason)
    private fun keys(row: JsonObject, expected: Set<String>, what: String) { if (row.keys != expected) fail("$what keys") }
    private fun obj(element: JsonElement?, expected: Set<String>, what: String): JsonObject =
        (element as? JsonObject ?: fail(what)).also { keys(it, expected, what) }
    private fun array(row: JsonObject, key: String): JsonArray = row[key] as? JsonArray ?: fail(key)
    private fun unique(ids: List<UUID>, what: String) { if (ids.toSet().size != ids.size) fail("duplicate $what") }
    private fun primitive(row: JsonObject, key: String): JsonPrimitive? {
        val value = row[key] ?: fail("missing $key")
        return if (value == JsonNull) null else value as? JsonPrimitive ?: fail(key)
    }
    private fun textOrNull(row: JsonObject, key: String): String? = primitive(row, key)?.let { if (it.isString) it.content else fail(key) }
    private fun text(row: JsonObject, key: String): String = textOrNull(row, key) ?: fail(key)
    private fun long(row: JsonObject, key: String): Long? = primitive(row, key)?.let {
        if (it.isString || !INTEGER.matches(it.content)) fail(key); it.content.toLongOrNull() ?: fail(key) }
    private fun version(row: JsonObject): Long = long(row, "version")?.takeIf { it >= 1L } ?: fail("version")
    private fun nonNegative(row: JsonObject, key: String): Long = nonNegativeOrNull(row, key) ?: fail(key)
    private fun nonNegativeOrNull(row: JsonObject, key: String): Long? = long(row, key)?.also { if (it < 0L) fail(key) }
    private fun intOrNull(row: JsonObject, key: String): Int? = long(row, key)?.let { if (it !in 0L..Int.MAX_VALUE.toLong()) fail(key); it.toInt() }
    /** Strictly a JSON boolean: a string, a number or null is out of contract (aclaración 4). */
    private fun bool(row: JsonObject, key: String): Boolean {
        val value = row[key] as? JsonPrimitive ?: fail(key)
        if (value == JsonNull || value.isString) fail(key)
        return when (value.content) { "true" -> true; "false" -> false; else -> fail(key) }
    }
    private fun uuidOrNull(row: JsonObject, key: String): UUID? = textOrNull(row, key)?.let {
        if (!UUID_TEXT.matches(it)) fail(key); UUID.fromString(it) }
    private fun uuid(row: JsonObject, key: String): UUID = uuidOrNull(row, key) ?: fail(key)
    private fun dateOrNull(row: JsonObject, key: String): LocalDate? = textOrNull(row, key)?.let {
        if (!DATE_TEXT.matches(it)) fail(key); runCatching { LocalDate.parse(it) }.getOrNull() ?: fail(key) }
    private fun date(row: JsonObject, key: String): LocalDate = dateOrNull(row, key) ?: fail(key)
    private fun instantOrNull(row: JsonObject, key: String): Instant? = textOrNull(row, key)?.let {
        runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull() ?: fail(key) }
    private fun instant(row: JsonObject, key: String): Instant = instantOrNull(row, key) ?: fail(key)
    private fun hashOrNull(row: JsonObject, key: String): String? = textOrNull(row, key)?.also { if (!HASH_TEXT.matches(it)) fail(key) }
    private fun allocation(row: JsonObject): String = text(row, "allocation_mode").also {
        if (it != "EXACT" && it != "UNALLOCATED") fail("allocation_mode") }
}
