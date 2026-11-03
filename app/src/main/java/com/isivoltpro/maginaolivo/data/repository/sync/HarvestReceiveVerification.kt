package com.isivoltpro.maginaolivo.data.repository.sync

import com.isivoltpro.maginaolivo.data.local.entity.DeliveryEntity
import com.isivoltpro.maginaolivo.data.local.entity.DeliveryParcelEntity
import com.isivoltpro.maginaolivo.data.local.entity.HarvestEntity
import com.isivoltpro.maginaolivo.data.local.entity.LocalMetadata
import com.isivoltpro.maginaolivo.data.local.model.SyncStatus
import com.isivoltpro.maginaolivo.data.repository.buildDeliveryPayloadJson
import com.isivoltpro.maginaolivo.data.repository.buildDeliveryPayloadJsonV3
import com.isivoltpro.maginaolivo.data.repository.deliveryOriginCreatedAt
import com.isivoltpro.maginaolivo.data.repository.deliveryPayloadHash
import com.isivoltpro.maginaolivo.data.repository.pgObject
import com.isivoltpro.maginaolivo.data.repository.pgScalar
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * #808 R3-b: independent verification of a parsed R2 bundle. Nothing is trusted because the server says it: every
 * Pesada receipt is recomputed from the row with the sender's own builders, and the Jornada fingerprints (R1-c) are
 * recomputed in memory. Pure functions: no Room, no network, no outbox. Not wired in shipped code.
 *
 * Owner decision B1: `derived_fingerprint` is recomputed with the SERVER rule (derive_auto_day) from the verified live
 * Pesadas, covering kilos and the parcel split; the server's split is never copied as a substitute for verification.
 */
internal sealed interface PesadaVerdict {
    data class Verified(val head: ReceivedDeliveryHead) : PesadaVerdict
    data class Unverified(val cause: String) : PesadaVerdict
}

/** Hold causes of the receiver (design §5) that verification alone can decide. */
internal object ReceiveHoldCauses {
    const val UNVERIFIED = "UNVERIFIED"
    const val FOREIGN_RECORDER = "FOREIGN_RECORDER"
    const val WEIGHT_DIVERGENT = "WEIGHT_DIVERGENT"
}

internal data class BundleVerdict(
    /** Empty when the Jornada and every Pesada verified and nothing must be held. */
    val holds: List<Pair<String, String>>,
    val pesadas: Map<UUID, PesadaVerdict>,
) {
    val verified: Boolean get() = holds.isEmpty()
}

internal object HarvestReceiveVerifier {

    fun verify(bundle: HarvestBundle): BundleVerdict {
        val pesadas = bundle.deliveries.associate { it.id to verifyPesada(it) }
        val holds = mutableListOf<Pair<String, String>>()
        val account = bundle.request.accountId
        // D8: owner-only v1. Another recorder anywhere in the unit holds the whole Jornada.
        if (bundle.harvest.recordedByUserId != account) holds += ReceiveHoldCauses.FOREIGN_RECORDER to "harvest"
        bundle.deliveries.filter { !it.recordedBySelf || it.recordedByUserId != account }
            .forEach { holds += ReceiveHoldCauses.FOREIGN_RECORDER to "delivery=${it.id}" }
        // The Jornada HEAD must name an applied receipt (an out-of-protocol write leaves it null).
        if (bundle.head.headOperationId == null) holds += ReceiveHoldCauses.UNVERIFIED to "harvest_head=null"
        val own = runCatching { harvestOwnFingerprint(harvestEntity(bundle.harvest)) }.getOrNull()
        if (own == null || own != bundle.head.ownFingerprint) holds += ReceiveHoldCauses.UNVERIFIED to "own_fingerprint"
        pesadas.filterValues { it is PesadaVerdict.Unverified }
            .forEach { (id, v) -> holds += ReceiveHoldCauses.UNVERIFIED to "delivery=$id;${(v as PesadaVerdict.Unverified).cause}" }
        if (bundle.harvest.dayOrigin == AUTO_DAY_ORIGIN) {
            val live = bundle.deliveries.filter { it.deletedAt == null }
            val derived = serverDerived(live)
            val serverSplit = bundle.serverParcels.map { Triple(it.parcelId, it.weightGrams, it.allocationMode) }
            if (derivedFingerprint(derived) != bundle.head.derivedFingerprint || derived.weightGrams != bundle.serverWeightGrams ||
                derived.parcels.map { Triple(it.parcelId, it.weightGrams, it.allocationMode) } != serverSplit
            ) holds += ReceiveHoldCauses.WEIGHT_DIVERGENT to "server=${bundle.serverWeightGrams};derived=${derived.weightGrams}"
        }
        return BundleVerdict(holds, pesadas)
    }

    /**
     * Aclaración 3 (R2): `kind` is never trusted. DELETE needs a deleted row, payload version 3 and the canonical delete
     * hash of version - 1; WRITE needs a live row and the hash of the payload rebuilt from the row (v3: CREATE only, with
     * originCreatedAt from fact_origins; v2: base null at version 1, else version - 1).
     */
    fun verifyPesada(d: ReceivedDelivery): PesadaVerdict {
        val head = d.head ?: return PesadaVerdict.Unverified("NO_HEAD")
        if (head.version != d.version) return PesadaVerdict.Unverified("HEAD_VERSION")
        return when (head.kind) {
            DeliveryHeadKind.DELETE -> when {
                d.deletedAt == null -> PesadaVerdict.Unverified("KIND_MISMATCH")
                head.payloadVersion != 3 || d.version < 2L -> PesadaVerdict.Unverified("DELETE_CONTRACT")
                head.payloadHash != deliveryDeleteHash(d.id, d.version - 1) -> PesadaVerdict.Unverified("DELETE_HASH")
                else -> PesadaVerdict.Verified(head)
            }
            DeliveryHeadKind.WRITE -> {
                if (d.deletedAt != null) return PesadaVerdict.Unverified("KIND_MISMATCH")
                val rebuilt = runCatching {
                    when (head.payloadVersion) {
                        3 -> {
                            val origin = d.originCreatedAtExact ?: return PesadaVerdict.Unverified("NO_ORIGIN")
                            if (d.version != 1L) return PesadaVerdict.Unverified("V3_NOT_CREATE")
                            val createdAt = Instant.parse(origin)
                            // B3: only an origin text the sender itself produces can be rebuilt byte for byte.
                            if (deliveryOriginCreatedAt(createdAt) != origin) return PesadaVerdict.Unverified("ORIGIN_TEXT")
                            val row = deliveryEntity(d, createdAt)
                            deliveryPayloadHash(buildDeliveryPayloadJsonV3(row, parcelEntities(d)), 3)
                        }
                        2 -> {
                            val row = deliveryEntity(d, d.createdAt)
                            deliveryPayloadHash(buildDeliveryPayloadJson(row, if (d.version == 1L) null else d.version - 1, parcelEntities(d)), 2)
                        }
                        else -> return PesadaVerdict.Unverified("UNSUPPORTED_PAYLOAD_VERSION")
                    }
                }.getOrNull()
                if (rebuilt == null || rebuilt != head.payloadHash) PesadaVerdict.Unverified("HASH_MISMATCH") else PesadaVerdict.Verified(head)
            }
        }
    }

    // ------------------------------------------------------------------------------------- in-memory projections

    private fun meta(createdAt: Instant, updatedAt: Instant, deletedAt: Instant?, version: Long) =
        LocalMetadata(createdAt, updatedAt, deletedAt, version, SyncStatus.SYNCED, version)

    fun deliveryEntity(d: ReceivedDelivery, createdAt: Instant) = DeliveryEntity(
        id = d.id, workspaceId = d.workspaceId, farmId = d.farmId, campaignId = d.campaignId, deliveryDate = d.deliveryDate,
        destinationOrganizationId = d.destinationOrganizationId, destinationName = d.destinationName, netGrams = d.netGrams,
        grossGrams = d.grossGrams, tareGrams = d.tareGrams, deliveryNumber = d.deliveryNumber, ticketNumber = d.ticketNumber,
        source = d.source, notes = d.notes, metadata = meta(createdAt, d.updatedAt, d.deletedAt, d.version), harvestId = d.harvestId,
        deliveryTime = d.deliveryTime, origin = d.harvestOrigin)

    fun parcelEntities(d: ReceivedDelivery) = d.parcels.map { p ->
        DeliveryParcelEntity(id = p.id, workspaceId = p.workspaceId, deliveryId = p.deliveryId, parcelId = p.parcelId,
            campaignParcelId = p.campaignParcelId, parcelNameAtDelivery = p.parcelNameAtDelivery, weightGrams = p.weightGrams,
            allocationMode = p.allocationMode, metadata = meta(d.createdAt, d.updatedAt, null, d.version))
    }

    fun harvestEntity(h: ReceivedHarvest) = HarvestEntity(
        id = h.id, workspaceId = h.workspaceId, campaignId = h.campaignId, farmId = h.farmId, harvestDate = h.harvestDate,
        weightGrams = h.weightGrams, destination = h.destination, notes = h.notes, collectionMethod = h.collectionMethod,
        workerCount = h.workerCount, machineryText = h.machineryText, metadata = meta(h.createdAt, h.updatedAt, h.deletedAt, h.version),
        dayOrigin = h.dayOrigin)

    // --------------------------------------------------------------------------------- derive_auto_day (server rule)

    data class DerivedParcel(val parcelId: UUID, val weightGrams: Long?, val allocationMode: String)
    data class Derived(val weightGrams: Long, val parcels: List<DerivedParcel>)

    /** Sum of live nets; per parcel EXACT with the sum only if every share is EXACT, else UNALLOCATED and null. */
    fun serverDerived(live: List<ReceivedDelivery>): Derived {
        val weight = live.fold(0L) { total, d -> Math.addExact(total, d.netGrams) }
        val parcels = live.flatMap { it.parcels }.groupBy { it.parcelId }.map { (parcelId, shares) ->
            if (shares.all { it.allocationMode == "EXACT" && it.weightGrams != null })
                DerivedParcel(parcelId, shares.fold(0L) { s, p -> Math.addExact(s, p.weightGrams!!) }, "EXACT")
            else DerivedParcel(parcelId, null, "UNALLOCATED")
        }.sortedBy { it.parcelId.toString() }
        return Derived(weight, parcels)
    }

    fun derivedFingerprint(derived: Derived): String {
        val parcels = derived.parcels.joinToString(", ", "[", "]") { p ->
            val value = buildJsonObject {
                put("parcelId", p.parcelId.toString()); put("weightGrams", p.weightGrams?.let(::JsonPrimitive) ?: JsonNull)
                put("allocationMode", p.allocationMode)
            }
            pgObject(value) { key, element -> pgScalar(element, key != "weightGrams", key == "weightGrams") }
        }
        // Top level: "parcels" (7) sorts before "weightGrams" (11), as PostgreSQL orders jsonb keys.
        return sha256("{\"parcels\": $parcels, \"weightGrams\": ${derived.weightGrams}}")
    }

    private const val AUTO_DAY_ORIGIN = "AUTO_DAY"
}

/** R1-c own_fingerprint: the canonical jsonb of p_harvest minus baseVersion (an AUTO_DAY's kilos null). */
internal fun harvestOwnFingerprint(day: HarvestEntity): String {
    val own = JsonObject(harvestPayload(day, null).filterKeys { it != "baseVersion" })
    return sha256(pgObject(own) { key, value -> pgScalar(value, key in HARVEST_TEXT, key in HARVEST_NULLABLE) })
}

private fun sha256(text: String) = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
    .joinToString("") { "%02x".format(it.toInt() and 255) }
