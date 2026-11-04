package com.isivoltpro.maginaolivo.data.repository.sync

import androidx.room.withTransaction
import com.isivoltpro.maginaolivo.data.local.MaginaOlivoDatabase
import com.isivoltpro.maginaolivo.data.local.entity.CampaignEntity
import com.isivoltpro.maginaolivo.data.local.entity.CampaignParcelSnapshotEntity
import com.isivoltpro.maginaolivo.data.local.entity.SyncOutboxEntity
import com.isivoltpro.maginaolivo.data.local.model.CampaignStatus
import com.isivoltpro.maginaolivo.data.local.model.OutboxOperation
import com.isivoltpro.maginaolivo.data.local.model.OutboxStatus
import com.isivoltpro.maginaolivo.data.local.model.SyncEntityType
import com.isivoltpro.maginaolivo.data.local.model.SyncStatus
import com.isivoltpro.maginaolivo.data.repository.WorkspaceAuthorizationContract
import com.isivoltpro.maginaolivo.data.repository.pgObject
import com.isivoltpro.maginaolivo.data.repository.pgScalar
import com.isivoltpro.maginaolivo.feature.account.AccountRepository
import com.isivoltpro.maginaolivo.feature.account.AuthFailure
import com.isivoltpro.maginaolivo.feature.account.AuthSession
import com.isivoltpro.maginaolivo.feature.accountlink.FarmLinkLedger
import java.math.BigDecimal
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonUnquotedLiteral
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

internal enum class CampaignCreateOutcome {
    STAGED,
    SYNCED,
    /** Receipt recorded (remote v1) but the campaign changed after staging: kept PENDING with its local edits. */
    CONFIRMED_LOCAL_PENDING,
    ALREADY_CONFIRMED,
    CONFLICT,
    OUTCOME_UNKNOWN,
    /** The campaign's farm lacks active G1b consent under this account: nothing is attributed or sent. */
    CONSENT_REQUIRED,
    /** The WORKSPACE (#798), its FARM (#799) or a snapshot's PARCEL (#800) is not yet confirmed remotely. */
    PARENT_NOT_SYNCED,
    NOT_AUTHORIZED,
    OFFLINE,
    REJECTED,
}

/**
 * Phase 2: sender for CAMPAIGN creation only, through the existing `create_own_campaign` and `check_sync_operations`
 * RPCs (G2.1 contract). Explicit QA entry point; no app composition root or scheduler constructs it.
 *
 * A campaign and its parcel snapshots are ONE aggregate. [stage] binds the app's unbound CREATE marker once and freezes
 * the exact `p_campaign` bytes; [send] sends or replays them with the same operation id (the server answers an exact
 * replay with its idempotent receipt) and confirms only through the durable server receipt whose hash matches. Later
 * changes go through CAMPAIGN CAS (UPDATE); a CREATE never covers them once confirmed.
 */
internal class CampaignCreateSendCoordinator(
    private val database: MaginaOlivoDatabase,
    private val account: AccountRepository,
    private val issuer: String,
    private val transport: DeliveryRpcTransport,
    /** Fresh durable G1b ledger for the account, or null when missing, unreadable or another account's. */
    private val ledger: (UUID) -> FarmLinkLedger?,
    private val now: () -> Instant = Instant::now,
) {
    suspend fun stage(operationId: UUID): CampaignCreateOutcome = try {
        if (issuer.isBlank()) CampaignCreateOutcome.NOT_AUTHORIZED
        else account.withServerVerifiedIdentity(remote = { Unit }) { userId, _ ->
            database.withTransaction {
                val op = database.syncOutboxDao().findById(operationId)
                    ?: return@withTransaction CampaignCreateOutcome.REJECTED
                if (op.entityType != SyncEntityType.CAMPAIGN || op.operation != OutboxOperation.CREATE ||
                    op.status != OutboxStatus.PENDING || op.accountId != null || op.payloadJson != null ||
                    op.attemptCount != 0) return@withTransaction CampaignCreateOutcome.REJECTED
                val campaign = database.campaignDao().findById(op.entityId) ?: return@withTransaction CampaignCreateOutcome.REJECTED
                if (campaign.metadata.deletedAt != null || campaign.metadata.remoteVersion != null)
                    return@withTransaction CampaignCreateOutcome.REJECTED
                val snapshots = database.campaignDao().listSnapshots(campaign.id).filter { it.metadata.deletedAt == null }
                val payload = runCatching { campaignCreatePayload(campaign, snapshots) }.getOrNull()
                    ?: return@withTransaction CampaignCreateOutcome.REJECTED
                parentProblem(userId, campaign, snapshots)?.let { return@withTransaction it }
                if (!consented(userId, campaign.workspaceId, campaign.farmId)) return@withTransaction CampaignCreateOutcome.CONSENT_REQUIRED
                if (otherOpenCreate(op)) return@withTransaction CampaignCreateOutcome.REJECTED
                val envelope = buildJsonObject {
                    put("issuer", issuer); put("localVersion", campaign.metadata.version); put("campaign", payload)
                }.toString()
                if (database.syncOutboxDao().stageCampaignCreate(op.id, userId.toString(), campaign.id, campaign.farmId, envelope,
                        now().toEpochMilli()) != 1) CampaignCreateOutcome.REJECTED else CampaignCreateOutcome.STAGED
            }
        }
    } catch (cancel: CancellationException) { throw cancel
    } catch (failure: AuthFailure) {
        if (failure.kind == AuthFailure.Kind.NETWORK) CampaignCreateOutcome.OFFLINE else CampaignCreateOutcome.NOT_AUTHORIZED
    } catch (_: Exception) { CampaignCreateOutcome.NOT_AUTHORIZED }

    /**
     * Sends or replays one staged CREATE with the same operation id and payload after a timeout or lost response.
     * Failures never delete the outbox row or its payload.
     */
    suspend fun send(operationId: UUID, lease: DeliveryDispatchLease): CampaignCreateOutcome {
        lease.requireOpen()
        if (issuer.isBlank()) return CampaignCreateOutcome.NOT_AUTHORIZED
        val op = database.syncOutboxDao().findById(operationId) ?: return CampaignCreateOutcome.REJECTED
        if (op.entityType != SyncEntityType.CAMPAIGN || op.operation != OutboxOperation.CREATE) return CampaignCreateOutcome.REJECTED
        val accountId = op.accountId
        val payload = op.payloadJson
        if (accountId == null || payload == null) return CampaignCreateOutcome.REJECTED
        if (op.status == OutboxStatus.CONFIRMED) return CampaignCreateOutcome.ALREADY_CONFIRMED
        val snapshot = parseCampaignCreateEnvelope(payload) ?: return CampaignCreateOutcome.REJECTED
        val campaign = database.campaignDao().findById(op.entityId) ?: return CampaignCreateOutcome.REJECTED
        // Consent is checked for the farm in the immutable payload; a revocation wins over a retry.
        if (!consented(UUID.fromString(accountId), snapshot.workspaceId, snapshot.farmId)) return CampaignCreateOutcome.CONSENT_REQUIRED
        if (op.status !in setOf(OutboxStatus.PENDING, OutboxStatus.FAILED) || op.payloadVersion < 1 ||
            snapshot.issuer != issuer || snapshot.campaignId != campaign.id || snapshot.workspaceId != campaign.workspaceId ||
            snapshot.farmId != campaign.farmId || op.farmId != snapshot.farmId || campaign.metadata.deletedAt != null || otherOpenCreate(op)
        ) return CampaignCreateOutcome.REJECTED
        return try {
            lease.withActiveRequest {
                account.withServerVerifiedIdentity(remote = { session ->
                    if (session.userId.toString() != accountId || parentProblem(session.userId, campaign, null) != null)
                        return@withServerVerifiedIdentity Remote.Unauthorized
                    try {
                        create(session, op.id, snapshot)
                        Remote.Confirmed(receipt(session, op.id) ?: return@withServerVerifiedIdentity Remote.Unknown)
                    } catch (cancel: CancellationException) { throw cancel
                    } catch (failure: DeliveryRpcFailure) {
                        when (failure.error) {
                            DeliveryRpcError.CONFLICT -> Remote.Conflict
                            DeliveryRpcError.FORBIDDEN -> Remote.Unauthorized
                            DeliveryRpcError.INVALID_PAYLOAD -> Remote.Invalid
                            else -> Remote.Unknown
                        }
                    } catch (_: Exception) {
                        // A malformed reply after a request: the server may have committed.
                        Remote.Unknown
                    }
                }, commit = { userId, result ->
                    if (userId.toString() != accountId || parentProblem(userId, campaign, null) != null) CampaignCreateOutcome.NOT_AUTHORIZED
                    else when (result) {
                        is Remote.Confirmed -> applyReceipt(op, snapshot, result.receipt, userId)
                        Remote.Conflict -> block(op, "CAMPAIGN_CONFLICT", CampaignCreateOutcome.CONFLICT)
                        Remote.Invalid -> block(op, "CAMPAIGN_INVALID", CampaignCreateOutcome.REJECTED)
                        Remote.Unknown -> {
                            database.syncOutboxDao().markCampaignCreateUnknown(op.id, accountId, payload, now().toEpochMilli())
                            CampaignCreateOutcome.OUTCOME_UNKNOWN
                        }
                        Remote.Unauthorized -> CampaignCreateOutcome.NOT_AUTHORIZED
                    }
                })
            }
        } catch (cancel: CancellationException) { throw cancel
        } catch (failure: AuthFailure) {
            if (failure.kind == AuthFailure.Kind.NETWORK) CampaignCreateOutcome.OFFLINE else CampaignCreateOutcome.NOT_AUTHORIZED
        } catch (_: Exception) { CampaignCreateOutcome.NOT_AUTHORIZED }
    }

    private suspend fun create(session: AuthSession, operationId: UUID, snapshot: CampaignCreateSnapshot) {
        val ack = transport.callRpcObject(CREATE_RPC,
            """{"p_operation_id":"$operationId","p_campaign":${snapshot.campaign}}""", session)
        if (ack.keys != setOf("success", "operation_id", "entity_id", "status", "remote_version", "payload_version", "idempotent") ||
            !ack.getValue("success").jsonPrimitive.boolean ||
            ack.getValue("operation_id").jsonPrimitive.content != operationId.toString() ||
            ack.getValue("entity_id").jsonPrimitive.content != snapshot.campaignId.toString() ||
            ack.getValue("status").jsonPrimitive.content != "APPLIED" ||
            ack.getValue("remote_version").jsonPrimitive.long != 1L ||
            ack.getValue("payload_version").jsonPrimitive.long != 1L ||
            ack.getValue("idempotent").jsonPrimitive.booleanOrNull == null
        ) throw DeliveryRpcFailure(DeliveryRpcError.PROTOCOL)
    }

    private suspend fun receipt(session: AuthSession, operationId: UUID): CampaignCreateReceipt? {
        val root = transport.callRpcObject(RECEIPT_RPC, """{"p_operation_ids":["$operationId"]}""", session)
        val rows = root["confirmed_receipts"] as? JsonArray ?: throw DeliveryRpcFailure(DeliveryRpcError.PROTOCOL)
        if (rows.isEmpty()) return null
        val row = rows.singleOrNull() as? JsonObject ?: throw DeliveryRpcFailure(DeliveryRpcError.PROTOCOL)
        return CampaignCreateReceipt(
            UUID.fromString(row.getValue("operation_id").jsonPrimitive.content),
            UUID.fromString(row.getValue("entity_id").jsonPrimitive.content),
            row.getValue("entity_type").jsonPrimitive.content,
            row.getValue("payload_hash").jsonPrimitive.content,
            row.getValue("payload_version").jsonPrimitive.long,
            row.getValue("remote_version").jsonPrimitive.long,
        )
    }

    private suspend fun applyReceipt(op: SyncOutboxEntity, snapshot: CampaignCreateSnapshot, receipt: CampaignCreateReceipt,
        userId: UUID): CampaignCreateOutcome = database.withTransaction {
        if (receipt.operationId != op.id || receipt.entityId != op.entityId || receipt.entityType != "CAMPAIGN" ||
            receipt.payloadHash != snapshot.hash || receipt.payloadVersion != 1L || receipt.remoteVersion != 1L
        ) return@withTransaction block(op, "RECEIPT_CONFLICT", CampaignCreateOutcome.CONFLICT)
        val currentOp = database.syncOutboxDao().findById(op.id) ?: return@withTransaction CampaignCreateOutcome.REJECTED
        val current = database.campaignDao().findById(op.entityId) ?: return@withTransaction CampaignCreateOutcome.REJECTED
        if (currentOp.payloadJson != op.payloadJson || currentOp.accountId != userId.toString() ||
            currentOp.status !in setOf(OutboxStatus.PENDING, OutboxStatus.FAILED) || current.workspaceId != snapshot.workspaceId
        ) return@withTransaction CampaignCreateOutcome.REJECTED
        if (database.syncOutboxDao().markConfirmed(op.id, now().toEpochMilli()) != 1) return@withTransaction CampaignCreateOutcome.REJECTED
        // A local edit made while this CREATE was staged was queued as a second CREATE marker (the campaign was still
        // unconfirmed). Once confirmed it can only travel as a CAMPAIGN CAS UPDATE: promote it, never drop it.
        database.syncOutboxDao().promoteLaterCampaignCreates(op.entityId, op.id, now().toEpochMilli())
        val snapshots = database.campaignDao().listSnapshots(current.id).filter { it.metadata.deletedAt == null }
        val exact = current.metadata.deletedAt == null && current.metadata.version == snapshot.localVersion &&
            runCatching { campaignCreatePayload(current, snapshots) }.getOrNull() == snapshot.campaign &&
            database.syncOutboxDao().listForEntity(SyncEntityType.CAMPAIGN, op.entityId)
                .none { it.id != op.id && it.status != OutboxStatus.CONFIRMED }
        val at = now()
        database.campaignDao().upsert(current.copy(metadata = current.metadata.copy(remoteVersion = 1L,
            syncStatus = if (exact) SyncStatus.SYNCED else current.metadata.syncStatus, lastSyncedAt = at)))
        // Only the snapshots that were sent, unchanged, are the server's: they carry its revision.
        val sent = snapshot.snapshotPayloads
        val rows = snapshots.filter { snapshotPayload(it) == sent[it.id] }.map { row ->
            row.copy(metadata = row.metadata.copy(remoteVersion = 1L, syncStatus = SyncStatus.SYNCED, lastSyncedAt = at))
        }
        if (rows.isNotEmpty()) database.campaignDao().upsertSnapshots(rows)
        if (exact) CampaignCreateOutcome.SYNCED else CampaignCreateOutcome.CONFIRMED_LOCAL_PENDING
    }

    /** Deterministic refusals stay auditable and are never retried automatically. */
    private suspend fun block(op: SyncOutboxEntity, reason: String, outcome: CampaignCreateOutcome) =
        database.withTransaction {
            if (database.syncOutboxDao().blockCampaignCreate(op.id, op.accountId!!, op.payloadJson!!, reason,
                    now().toEpochMilli()) != 1) return@withTransaction CampaignCreateOutcome.REJECTED
            if (outcome == CampaignCreateOutcome.CONFLICT) database.campaignDao().findById(op.entityId)?.let {
                database.campaignDao().upsert(it.copy(metadata = it.metadata.copy(syncStatus = SyncStatus.CONFLICT)))
            }
            outcome
        }

    /**
     * Server rule: the live workspace owner, the farm live in it and created/owned by the actor, every snapshot's parcel
     * associated with that farm. Locally: WORKSPACE confirmed (#798), selected with `write`, the FARM confirmed (#799)
     * and each snapshot's PARCEL confirmed (#800).
     */
    private suspend fun parentProblem(userId: UUID, campaign: CampaignEntity,
        snapshots: List<CampaignParcelSnapshotEntity>?): CampaignCreateOutcome? {
        val workspace = database.workspaceDao().findById(campaign.workspaceId) ?: return CampaignCreateOutcome.PARENT_NOT_SYNCED
        if (workspace.metadata.deletedAt != null || workspace.ownerUserId != userId) return CampaignCreateOutcome.NOT_AUTHORIZED
        if (workspace.metadata.syncStatus != SyncStatus.SYNCED || workspace.metadata.remoteVersion == null ||
            database.syncOutboxDao().listForEntity(SyncEntityType.WORKSPACE, workspace.id)
                .any { it.status != OutboxStatus.CONFIRMED }) return CampaignCreateOutcome.PARENT_NOT_SYNCED
        val farm = database.farmDao().findById(campaign.farmId) ?: return CampaignCreateOutcome.PARENT_NOT_SYNCED
        if (farm.workspaceId != campaign.workspaceId || farm.metadata.deletedAt != null) return CampaignCreateOutcome.REJECTED
        if (farm.metadata.remoteVersion == null || database.syncOutboxDao().listForEntity(SyncEntityType.FARM, farm.id)
                .any { it.operation == OutboxOperation.CREATE && it.status != OutboxStatus.CONFIRMED })
            return CampaignCreateOutcome.PARENT_NOT_SYNCED
        for (parcelId in snapshots.orEmpty().map { it.parcelId }.distinct()) {
            val parcel = database.parcelDao().findById(parcelId) ?: return CampaignCreateOutcome.PARENT_NOT_SYNCED
            if (parcel.workspaceId != campaign.workspaceId) return CampaignCreateOutcome.REJECTED
            if (parcel.metadata.remoteVersion == null || database.syncOutboxDao().listForEntity(SyncEntityType.PARCEL, parcelId)
                    .any { it.operation == OutboxOperation.CREATE && it.status != OutboxStatus.CONFIRMED })
                return CampaignCreateOutcome.PARENT_NOT_SYNCED
        }
        val selected = database.workspaceSelectionDao().find(issuer, userId.toString())?.workspaceId
        if (selected != campaign.workspaceId) return CampaignCreateOutcome.NOT_AUTHORIZED
        val cached = database.workspaceAuthorizationCacheDao().find(issuer, userId.toString())
            ?: return CampaignCreateOutcome.NOT_AUTHORIZED
        val grant = runCatching { WorkspaceAuthorizationContract.parse(cached.contractJson) }.getOrNull()
            ?: return CampaignCreateOutcome.NOT_AUTHORIZED
        return if (grant.validFor(issuer, userId, now()) &&
            grant.available(now()).any { it.id == campaign.workspaceId && "write" in it.permissions }) null
        else CampaignCreateOutcome.NOT_AUTHORIZED
    }

    /** The campaign's farm needs an active G1b consent of this account in this workspace. */
    private fun consented(userId: UUID, workspaceId: UUID, farmId: UUID): Boolean {
        val current = runCatching { ledger(userId) }.getOrNull() ?: return false
        if (current.subject != "$issuer|$userId") return false
        return farmId in current.activeFarmIds && current.consents.any { consent ->
            consent.farms.any { it.id == farmId && it.workspaceId == workspaceId }
        }
    }

    private suspend fun otherOpenCreate(op: SyncOutboxEntity) =
        database.syncOutboxDao().listForEntity(SyncEntityType.CAMPAIGN, op.entityId).any {
            it.id != op.id && it.operation == OutboxOperation.CREATE && it.status != OutboxStatus.CONFIRMED && it.payloadJson != null
        }

    private sealed interface Remote {
        data class Confirmed(val receipt: CampaignCreateReceipt) : Remote
        data object Conflict : Remote
        data object Invalid : Remote
        data object Unknown : Remote
        data object Unauthorized : Remote
    }

    private data class CampaignCreateReceipt(val operationId: UUID, val entityId: UUID, val entityType: String,
        val payloadHash: String, val payloadVersion: Long, val remoteVersion: Long)

    internal companion object {
        const val CREATE_RPC = "create_own_campaign"
        const val RECEIPT_RPC = "check_sync_operations"
        val NAMES = setOf(CREATE_RPC, RECEIPT_RPC)
    }
}

internal data class CampaignCreateSnapshot(
    val issuer: String,
    val localVersion: Long,
    val campaignId: UUID,
    val workspaceId: UUID,
    val farmId: UUID,
    val snapshotPayloads: Map<UUID, JsonObject>,
    val campaign: JsonObject,
    val hash: String,
)

private val CAMPAIGN_KEYS = setOf("id", "workspaceId", "farmId", "name", "startDate", "endDate", "status", "notes", "parcels")
private val SNAPSHOT_KEYS = setOf("id", "parcelId", "farmIdAtStart", "farmNameAtStart", "parcelNameAtStart",
    "managedAreaM2AtStart", "cadastralReferenceAtStart", "geometryGeoJsonSnapshot")
private val PLAIN_DECIMAL = Regex("^(0|[1-9][0-9]*)([.][0-9]+)?$")

/** Plain decimal text: PostgreSQL keeps it byte for byte as numeric JSONB, unlike exponent notation. */
@OptIn(ExperimentalSerializationApi::class)
private fun snapshotArea(value: Double?): JsonElement = value?.let {
    require(it.isFinite() && it >= 0.0)
    JsonUnquotedLiteral(BigDecimal.valueOf(it).toPlainString())
} ?: JsonNull

internal fun snapshotPayload(row: CampaignParcelSnapshotEntity): JsonObject = buildJsonObject {
    fun optional(value: String?) = value?.let(::JsonPrimitive) ?: JsonNull
    put("id", row.id.toString())
    put("parcelId", row.parcelId.toString())
    put("farmIdAtStart", row.farmIdAtStart.toString())
    put("farmNameAtStart", row.farmNameAtStart)
    put("parcelNameAtStart", row.parcelNameAtStart)
    put("managedAreaM2AtStart", snapshotArea(row.managedAreaM2AtStart))
    put("cadastralReferenceAtStart", optional(row.cadastralReferenceAtStart))
    put("geometryGeoJsonSnapshot", optional(row.geometryGeoJsonSnapshot))
}

/** The 9-key `p_campaign` of `create_own_campaign`, validated like the server so a staged payload is not refused. */
internal fun campaignCreatePayload(campaign: CampaignEntity, snapshots: List<CampaignParcelSnapshotEntity>): JsonObject {
    fun chars(value: String?) = value?.let { it.codePointCount(0, it.length) } ?: 0
    require(chars(campaign.name) in 1..200 && campaign.name.isNotBlank() && chars(campaign.notes) <= 10_000)
    require(campaign.endDate == null || !campaign.endDate.isBefore(campaign.startDate))
    require(campaign.status in setOf(CampaignStatus.PREPARATION, CampaignStatus.ACTIVE, CampaignStatus.HARVEST, CampaignStatus.CLOSED))
    require(campaign.status == CampaignStatus.PREPARATION || snapshots.isNotEmpty())
    require(snapshots.size <= 1000 && snapshots.map { it.id }.toSet().size == snapshots.size &&
        snapshots.map { it.parcelId }.toSet().size == snapshots.size)
    require(snapshots.all { s -> s.campaignId == campaign.id && s.workspaceId == campaign.workspaceId && s.farmIdAtStart == campaign.farmId &&
        chars(s.farmNameAtStart) in 1..200 && s.farmNameAtStart.isNotBlank() && chars(s.parcelNameAtStart) in 1..200 &&
        s.parcelNameAtStart.isNotBlank() && chars(s.cadastralReferenceAtStart) <= 200 })
    require(listOf(campaign.name, campaign.notes).none { it?.contains('\u0000') == true })
    return buildJsonObject {
        put("id", campaign.id.toString())
        put("workspaceId", campaign.workspaceId.toString())
        put("farmId", campaign.farmId.toString())
        put("name", campaign.name)
        put("startDate", campaign.startDate.toString())
        put("endDate", campaign.endDate?.let { JsonPrimitive(it.toString()) } ?: JsonNull)
        put("status", campaign.status.name)
        put("notes", campaign.notes?.let(::JsonPrimitive) ?: JsonNull)
        put("parcels", buildJsonArray { snapshots.sortedBy { it.parcelId.toString() }.forEach { add(snapshotPayload(it)) } })
    }.also { require(it.toString().toByteArray(Charsets.UTF_8).size <= 4 * 1024 * 1024) }
}

/** Hash matches the server's sha256(p_campaign::text) over PostgreSQL's canonical JSONB text. */
internal fun campaignCreateHash(campaign: JsonObject): String {
    fun scalar(key: String, value: JsonElement, nullable: Set<String>): String = when {
        value == JsonNull -> { require(key in nullable); "null" }
        key == "managedAreaM2AtStart" -> (value as JsonPrimitive).also { require(!it.isString) }.content.also { require(PLAIN_DECIMAL.matches(it)) }
        else -> pgScalar(value, text = true, nullable = false)
    }
    val canonical = pgObject(campaign) { key, value ->
        if (key != "parcels") scalar(key, value, setOf("endDate", "notes"))
        else (value as JsonArray).joinToString(", ", "[", "]") { item ->
            pgObject(item as JsonObject) { field, element ->
                scalar(field, element, setOf("managedAreaM2AtStart", "cadastralReferenceAtStart", "geometryGeoJsonSnapshot"))
            }
        }
    }
    return MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 255) }
}

internal fun parseCampaignCreateEnvelope(payload: String): CampaignCreateSnapshot? = runCatching {
    require(payload.toByteArray(Charsets.UTF_8).size <= 4 * 1024 * 1024 + 4096)
    val root = Json.parseToJsonElement(payload) as JsonObject
    require(root.keys == setOf("issuer", "localVersion", "campaign"))
    val issuer = root.getValue("issuer").jsonPrimitive.content
    val localVersion = root.getValue("localVersion").jsonPrimitive.longOrNull ?: error("localVersion")
    val campaign = root.getValue("campaign") as JsonObject
    require(issuer.isNotBlank() && localVersion > 0 && campaign.keys == CAMPAIGN_KEYS)
    fun text(o: JsonObject, key: String) = o.getValue(key).jsonPrimitive.also { require(it.isString) }.content
    fun optional(o: JsonObject, key: String) = o.getValue(key).takeUnless { it == JsonNull }?.let { text(o, key) }
    val parcels = (campaign.getValue("parcels") as JsonArray).map { it as JsonObject }
    require(parcels.all { it.keys == SNAPSHOT_KEYS })
    val id = UUID.fromString(text(campaign, "id"))
    val workspaceId = UUID.fromString(text(campaign, "workspaceId"))
    val farmId = UUID.fromString(text(campaign, "farmId"))
    // Rebuild the entities and require the canonical payload back: unknown or altered values are refused.
    val meta = com.isivoltpro.maginaolivo.data.local.entity.LocalMetadata(Instant.EPOCH, Instant.EPOCH)
    val rows = parcels.map { p ->
        CampaignParcelSnapshotEntity(UUID.fromString(text(p, "id")), workspaceId, id, UUID.fromString(text(p, "parcelId")),
            UUID.fromString(text(p, "farmIdAtStart")), text(p, "farmNameAtStart"), text(p, "parcelNameAtStart"),
            p.getValue("managedAreaM2AtStart").takeUnless { it == JsonNull }?.jsonPrimitive?.also { require(!it.isString) }?.content?.toDouble(),
            optional(p, "cadastralReferenceAtStart"), optional(p, "geometryGeoJsonSnapshot"), meta)
    }
    val entity = CampaignEntity(id, workspaceId, farmId, text(campaign, "name"), LocalDate.parse(text(campaign, "startDate")),
        optional(campaign, "endDate")?.let(LocalDate::parse), CampaignStatus.valueOf(text(campaign, "status")),
        optional(campaign, "notes"), meta)
    require(campaignCreatePayload(entity, rows) == campaign)
    CampaignCreateSnapshot(issuer, localVersion, id, workspaceId, farmId, parcels.associateBy { UUID.fromString(text(it, "id")) },
        campaign, campaignCreateHash(campaign))
}.getOrNull()
