package com.isivoltpro.maginaolivo.data.repository.sync

import androidx.room.withTransaction
import com.isivoltpro.maginaolivo.data.local.MaginaOlivoDatabase
import com.isivoltpro.maginaolivo.data.local.entity.FarmParcelMembershipEntity
import com.isivoltpro.maginaolivo.data.local.entity.LocalMetadata
import com.isivoltpro.maginaolivo.data.local.entity.ParcelEntity
import com.isivoltpro.maginaolivo.data.local.entity.SyncOutboxEntity
import com.isivoltpro.maginaolivo.data.local.model.OutboxOperation
import com.isivoltpro.maginaolivo.data.local.model.OutboxStatus
import com.isivoltpro.maginaolivo.data.local.model.RecordStatus
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

internal enum class ParcelSendOutcome {
    STAGED,
    SYNCED,
    /** Receipt recorded (remote v1) but the parcel changed after staging: kept PENDING with its local edits. */
    CONFIRMED_LOCAL_PENDING,
    ALREADY_CONFIRMED,
    CONFLICT,
    OUTCOME_UNKNOWN,
    /** A farm of the parcel lacks active G1b consent under this account: nothing is attributed or sent. */
    CONSENT_REQUIRED,
    /** The WORKSPACE (#798) or a referenced FARM (#799) is not yet confirmed remotely. */
    PARENT_NOT_SYNCED,
    NOT_AUTHORIZED,
    OFFLINE,
    REJECTED,
}

/**
 * G2.6 sender for PARCEL creation only, through the existing `create_own_parcel` and
 * `check_sync_operations` RPCs. Explicit QA entry point; no app composition root or scheduler
 * constructs it.
 *
 * The RPC and the app's producer both treat a parcel and its farm memberships as ONE aggregate: a
 * parcel is created with at least one membership. This sender ships that creation-time history only;
 * later membership changes, parcel UPDATE and DELETE are out of scope and stay local and pending.
 */
internal class ParcelSendCoordinator(
    private val database: MaginaOlivoDatabase,
    private val account: AccountRepository,
    private val issuer: String,
    private val transport: DeliveryRpcTransport,
    /** Fresh durable G1b ledger for the account, or null when missing, unreadable or another account's. */
    private val ledger: (UUID) -> FarmLinkLedger?,
    private val now: () -> Instant = Instant::now,
) {
    suspend fun stage(operationId: UUID): ParcelSendOutcome = try {
        if (issuer.isBlank()) ParcelSendOutcome.NOT_AUTHORIZED
        else account.withServerVerifiedIdentity(remote = { Unit }) { userId, _ ->
            database.withTransaction {
                val op = database.syncOutboxDao().findById(operationId)
                    ?: return@withTransaction ParcelSendOutcome.REJECTED
                if (op.entityType != SyncEntityType.PARCEL || op.operation != OutboxOperation.CREATE ||
                    op.status != OutboxStatus.PENDING || op.accountId != null || op.payloadJson != null ||
                    op.attemptCount != 0) return@withTransaction ParcelSendOutcome.REJECTED
                val parcel = database.parcelDao().findById(op.entityId) ?: return@withTransaction ParcelSendOutcome.REJECTED
                val memberships = database.parcelDao().listMemberships(parcel.id)
                if (parcel.metadata.deletedAt != null) return@withTransaction ParcelSendOutcome.REJECTED
                val payload = runCatching { parcelPayload(parcel, memberships) }.getOrNull()
                    ?: return@withTransaction ParcelSendOutcome.REJECTED
                parentProblem(userId, parcel.workspaceId, memberships)?.let { return@withTransaction it }
                if (!consented(userId, parcel.workspaceId, memberships)) return@withTransaction ParcelSendOutcome.CONSENT_REQUIRED
                if (otherOpenCreate(op)) return@withTransaction ParcelSendOutcome.REJECTED
                val gate = currentFarm(memberships) ?: return@withTransaction ParcelSendOutcome.REJECTED
                val envelope = buildJsonObject {
                    put("issuer", issuer); put("localVersion", parcel.metadata.version); put("parcel", payload)
                }.toString()
                if (database.syncOutboxDao().stageParcelCreate(op.id, userId.toString(), parcel.id, gate, envelope,
                        now().toEpochMilli()) != 1) ParcelSendOutcome.REJECTED else ParcelSendOutcome.STAGED
            }
        }
    } catch (cancel: CancellationException) { throw cancel
    } catch (failure: AuthFailure) {
        if (failure.kind == AuthFailure.Kind.NETWORK) ParcelSendOutcome.OFFLINE else ParcelSendOutcome.NOT_AUTHORIZED
    } catch (_: Exception) { ParcelSendOutcome.NOT_AUTHORIZED }

    /**
     * Sends or replays one staged CREATE with the same operation id and payload after a timeout or
     * lost response; the server answers an exact replay with its idempotent receipt. Failures never
     * delete the outbox row or its payload.
     */
    suspend fun send(operationId: UUID, lease: DeliveryDispatchLease): ParcelSendOutcome {
        lease.requireOpen()
        if (issuer.isBlank()) return ParcelSendOutcome.NOT_AUTHORIZED
        val op = database.syncOutboxDao().findById(operationId) ?: return ParcelSendOutcome.REJECTED
        if (op.entityType != SyncEntityType.PARCEL || op.operation != OutboxOperation.CREATE)
            return ParcelSendOutcome.REJECTED
        val accountId = op.accountId
        val payload = op.payloadJson
        if (accountId == null || payload == null) return ParcelSendOutcome.REJECTED
        if (op.status == OutboxStatus.CONFIRMED) return ParcelSendOutcome.ALREADY_CONFIRMED
        val snapshot = parseParcelCreateEnvelope(payload) ?: return ParcelSendOutcome.REJECTED
        val parcel = database.parcelDao().findById(op.entityId) ?: return ParcelSendOutcome.REJECTED
        // Consent is checked for the farms in the immutable payload; a revocation wins over BLOCKED.
        if (!consented(UUID.fromString(accountId), snapshot.workspaceId, snapshot.farmIds.map { farm ->
                FarmParcelMembershipEntity(farm, snapshot.workspaceId, farm, parcel.id, Instant.EPOCH,
                    metadata = LocalMetadata(Instant.EPOCH, Instant.EPOCH))
            })) return ParcelSendOutcome.CONSENT_REQUIRED
        if (op.status !in setOf(OutboxStatus.PENDING, OutboxStatus.FAILED) || op.payloadVersion < 1 ||
            snapshot.issuer != issuer || snapshot.parcelId != parcel.id || snapshot.workspaceId != parcel.workspaceId ||
            op.farmId !in snapshot.farmIds || parcel.metadata.deletedAt != null || otherOpenCreate(op)
        ) return ParcelSendOutcome.REJECTED
        val parents = snapshot.farmIds.map { farm ->
            FarmParcelMembershipEntity(farm, snapshot.workspaceId, farm, parcel.id, Instant.EPOCH,
                metadata = LocalMetadata(Instant.EPOCH, Instant.EPOCH))
        }
        return try {
            lease.withActiveRequest {
                account.withServerVerifiedIdentity(remote = { session ->
                    if (session.userId.toString() != accountId ||
                        parentProblem(session.userId, snapshot.workspaceId, parents) != null)
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
                    if (userId.toString() != accountId || parentProblem(userId, snapshot.workspaceId, parents) != null)
                        ParcelSendOutcome.NOT_AUTHORIZED
                    else when (result) {
                        is Remote.Confirmed -> applyReceipt(op, snapshot, result.receipt, userId)
                        Remote.Conflict -> block(op, "PARCEL_CONFLICT", ParcelSendOutcome.CONFLICT)
                        Remote.Invalid -> block(op, "PARCEL_INVALID", ParcelSendOutcome.REJECTED)
                        Remote.Unknown -> {
                            database.syncOutboxDao().markParcelCreateUnknown(op.id, accountId, payload, now().toEpochMilli())
                            ParcelSendOutcome.OUTCOME_UNKNOWN
                        }
                        Remote.Unauthorized -> ParcelSendOutcome.NOT_AUTHORIZED
                    }
                })
            }
        } catch (cancel: CancellationException) { throw cancel
        } catch (failure: AuthFailure) {
            if (failure.kind == AuthFailure.Kind.NETWORK) ParcelSendOutcome.OFFLINE else ParcelSendOutcome.NOT_AUTHORIZED
        } catch (_: Exception) { ParcelSendOutcome.NOT_AUTHORIZED }
    }

    private suspend fun create(session: AuthSession, operationId: UUID, snapshot: ParcelCreateSnapshot) {
        val ack = transport.callRpcObject(CREATE_RPC,
            """{"p_operation_id":"$operationId","p_parcel":${snapshot.parcel}}""", session)
        if (ack.keys != setOf("success", "operation_id", "entity_id", "status", "remote_version", "payload_version", "idempotent") ||
            !ack.getValue("success").jsonPrimitive.boolean ||
            ack.getValue("operation_id").jsonPrimitive.content != operationId.toString() ||
            ack.getValue("entity_id").jsonPrimitive.content != snapshot.parcelId.toString() ||
            ack.getValue("status").jsonPrimitive.content != "APPLIED" ||
            ack.getValue("remote_version").jsonPrimitive.long != 1L ||
            ack.getValue("payload_version").jsonPrimitive.long != 1L ||
            ack.getValue("idempotent").jsonPrimitive.booleanOrNull == null
        ) throw DeliveryRpcFailure(DeliveryRpcError.PROTOCOL)
    }

    private suspend fun receipt(session: AuthSession, operationId: UUID): ParcelReceipt? {
        val root = transport.callRpcObject(RECEIPT_RPC, """{"p_operation_ids":["$operationId"]}""", session)
        val rows = root["confirmed_receipts"] as? JsonArray ?: throw DeliveryRpcFailure(DeliveryRpcError.PROTOCOL)
        if (rows.isEmpty()) return null
        val row = rows.singleOrNull() as? JsonObject ?: throw DeliveryRpcFailure(DeliveryRpcError.PROTOCOL)
        return ParcelReceipt(
            UUID.fromString(row.getValue("operation_id").jsonPrimitive.content),
            UUID.fromString(row.getValue("entity_id").jsonPrimitive.content),
            row.getValue("entity_type").jsonPrimitive.content,
            row.getValue("payload_hash").jsonPrimitive.content,
            row.getValue("payload_version").jsonPrimitive.long,
            row.getValue("remote_version").jsonPrimitive.long,
        )
    }

    private suspend fun applyReceipt(op: SyncOutboxEntity, snapshot: ParcelCreateSnapshot, receipt: ParcelReceipt,
        userId: UUID): ParcelSendOutcome = database.withTransaction {
        if (receipt.operationId != op.id || receipt.entityId != op.entityId || receipt.entityType != "PARCEL" ||
            receipt.payloadHash != snapshot.hash || receipt.payloadVersion != 1L || receipt.remoteVersion != 1L
        ) return@withTransaction block(op, "RECEIPT_CONFLICT", ParcelSendOutcome.CONFLICT)
        val currentOp = database.syncOutboxDao().findById(op.id) ?: return@withTransaction ParcelSendOutcome.REJECTED
        val current = database.parcelDao().findById(op.entityId) ?: return@withTransaction ParcelSendOutcome.REJECTED
        if (currentOp.payloadJson != op.payloadJson || currentOp.accountId != userId.toString() ||
            currentOp.status !in setOf(OutboxStatus.PENDING, OutboxStatus.FAILED) ||
            current.workspaceId != snapshot.workspaceId
        ) return@withTransaction ParcelSendOutcome.REJECTED
        if (database.syncOutboxDao().markConfirmed(op.id, now().toEpochMilli()) != 1)
            return@withTransaction ParcelSendOutcome.REJECTED
        val memberships = database.parcelDao().listMemberships(current.id)
        // Later offline edits or membership changes (and their outbox markers) stay local and pending.
        val exact = current.metadata.deletedAt == null && current.metadata.version == snapshot.localVersion &&
            runCatching { parcelPayload(current, memberships) }.getOrNull() == snapshot.parcel &&
            database.syncOutboxDao().listForEntity(SyncEntityType.PARCEL, op.entityId)
                .none { it.id != op.id && it.status != OutboxStatus.CONFIRMED }
        val at = now()
        database.parcelDao().upsert(current.copy(metadata = current.metadata.copy(remoteVersion = 1L,
            syncStatus = if (exact) SyncStatus.SYNCED else current.metadata.syncStatus, lastSyncedAt = at)))
        // Only the memberships that were sent, unchanged, carry the remote revision.
        val sent = snapshot.membershipPayloads
        memberships.filter { membershipPayload(it) == sent[it.id] }.forEach { row ->
            database.parcelDao().upsertMembership(row.copy(metadata = row.metadata.copy(remoteVersion = 1L,
                syncStatus = if (exact) SyncStatus.SYNCED else row.metadata.syncStatus, lastSyncedAt = at)))
        }
        if (exact) ParcelSendOutcome.SYNCED else ParcelSendOutcome.CONFIRMED_LOCAL_PENDING
    }

    /** Deterministic refusals stay auditable and are never retried automatically. */
    private suspend fun block(op: SyncOutboxEntity, reason: String, outcome: ParcelSendOutcome) =
        database.withTransaction {
            if (database.syncOutboxDao().blockParcelCreate(op.id, op.accountId!!, op.payloadJson!!, reason,
                    now().toEpochMilli()) != 1) return@withTransaction ParcelSendOutcome.REJECTED
            if (outcome == ParcelSendOutcome.CONFLICT) database.parcelDao().findById(op.entityId)?.let {
                database.parcelDao().upsert(it.copy(metadata = it.metadata.copy(syncStatus = SyncStatus.CONFLICT)))
            }
            outcome
        }

    /**
     * Server rule: only the live workspace owner, with every referenced farm live, in the workspace and
     * created/owned by the actor. Locally: WORKSPACE confirmed (#798), selected with `write`, and each
     * farm confirmed by its FARM CREATE (#799).
     */
    private suspend fun parentProblem(userId: UUID, workspaceId: UUID,
        memberships: List<FarmParcelMembershipEntity>): ParcelSendOutcome? {
        val workspace = database.workspaceDao().findById(workspaceId) ?: return ParcelSendOutcome.PARENT_NOT_SYNCED
        if (workspace.metadata.deletedAt != null || workspace.ownerUserId != userId) return ParcelSendOutcome.NOT_AUTHORIZED
        if (workspace.metadata.syncStatus != SyncStatus.SYNCED || workspace.metadata.remoteVersion == null ||
            database.syncOutboxDao().listForEntity(SyncEntityType.WORKSPACE, workspaceId)
                .any { it.status != OutboxStatus.CONFIRMED }) return ParcelSendOutcome.PARENT_NOT_SYNCED
        for (farmId in memberships.map { it.farmId }.distinct()) {
            val farm = database.farmDao().findById(farmId) ?: return ParcelSendOutcome.PARENT_NOT_SYNCED
            if (farm.workspaceId != workspaceId || farm.metadata.deletedAt != null) return ParcelSendOutcome.REJECTED
            if (farm.metadata.remoteVersion == null || database.syncOutboxDao().listForEntity(SyncEntityType.FARM, farmId)
                    .any { it.operation == OutboxOperation.CREATE && it.status != OutboxStatus.CONFIRMED })
                return ParcelSendOutcome.PARENT_NOT_SYNCED
        }
        val selected = database.workspaceSelectionDao().find(issuer, userId.toString())?.workspaceId
        if (selected != workspaceId) return ParcelSendOutcome.NOT_AUTHORIZED
        val cached = database.workspaceAuthorizationCacheDao().find(issuer, userId.toString())
            ?: return ParcelSendOutcome.NOT_AUTHORIZED
        val grant = runCatching { WorkspaceAuthorizationContract.parse(cached.contractJson) }.getOrNull()
            ?: return ParcelSendOutcome.NOT_AUTHORIZED
        return if (grant.validFor(issuer, userId, now()) &&
            grant.available(now()).any { it.id == workspaceId && "write" in it.permissions }) null
        else ParcelSendOutcome.NOT_AUTHORIZED
    }

    /** Every farm of the aggregate needs an active G1b consent of this account in this workspace. */
    private fun consented(userId: UUID, workspaceId: UUID, memberships: List<FarmParcelMembershipEntity>): Boolean {
        val current = runCatching { ledger(userId) }.getOrNull() ?: return false
        if (current.subject != "$issuer|$userId") return false
        return memberships.map { it.farmId }.distinct().all { farm ->
            farm in current.activeFarmIds && current.consents.any { consent ->
                consent.farms.any { it.id == farm && it.workspaceId == workspaceId }
            }
        }
    }

    private suspend fun otherOpenCreate(op: SyncOutboxEntity) =
        database.syncOutboxDao().listForEntity(SyncEntityType.PARCEL, op.entityId).any {
            it.id != op.id && it.operation == OutboxOperation.CREATE && it.status != OutboxStatus.CONFIRMED
        }

    private sealed interface Remote {
        data class Confirmed(val receipt: ParcelReceipt) : Remote
        data object Conflict : Remote
        data object Invalid : Remote
        data object Unknown : Remote
        data object Unauthorized : Remote
    }

    private data class ParcelReceipt(val operationId: UUID, val entityId: UUID, val entityType: String,
        val payloadHash: String, val payloadVersion: Long, val remoteVersion: Long)

    internal companion object {
        const val CREATE_RPC = "create_own_parcel"
        const val RECEIPT_RPC = "check_sync_operations"
        val NAMES = setOf(CREATE_RPC, RECEIPT_RPC)
    }
}

internal data class ParcelCreateSnapshot(
    val issuer: String,
    val localVersion: Long,
    val parcelId: UUID,
    val workspaceId: UUID,
    val farmIds: Set<UUID>,
    val membershipPayloads: Map<UUID, JsonObject>,
    val parcel: JsonObject,
    val hash: String,
)

private val TEXT_200 = listOf("cadastralReference", "cadastralPolygon", "cadastralParcel", "municipality", "province",
    "sourceProvider", "sourceImportedAt", "variety", "irrigationSystem", "irrigationNetwork", "irrigationSector",
    "irrigationDays")
private val NULLABLE_TEXT = TEXT_200 + listOf("geometryGeoJson", "notes")
private val NUMBERS = setOf("cadastralAreaM2", "managedAreaM2", "oliveTreeCount")
private val PARCEL_KEYS = setOf("id", "workspaceId", "displayName", "source", "status", "memberships") + NULLABLE_TEXT + NUMBERS
private val INSTANT = Regex("^[0-9]{4}-[0-9]{2}-[0-9]{2}T([01][0-9]|2[0-3]):[0-5][0-9]:[0-5][0-9]([.][0-9]{1,9})?Z$")
private val DECIMAL = Regex("^(0|[1-9][0-9]*)([.][0-9]+)?$")

internal fun instantText(value: Instant): String = value.toString().also { require(INSTANT.matches(it)) }

/** Plain decimal text: PostgreSQL keeps it byte for byte as numeric JSONB, unlike exponent notation. */
@OptIn(ExperimentalSerializationApi::class)
private fun area(value: Double?): JsonElement = value?.let {
    require(it.isFinite() && it >= 0.0)
    JsonUnquotedLiteral(BigDecimal.valueOf(it).toPlainString())
} ?: JsonNull

internal fun membershipPayload(row: FarmParcelMembershipEntity): JsonObject = buildJsonObject {
    put("id", row.id.toString())
    put("farmId", row.farmId.toString())
    put("validFrom", instantText(row.validFrom))
    put("validUntil", row.validUntil?.let { JsonPrimitive(instantText(it)) } ?: JsonNull)
    put("deletedAt", row.metadata.deletedAt?.let { JsonPrimitive(instantText(it)) } ?: JsonNull)
}

/** The 23-key `p_parcel` of `create_own_parcel`, validated like the server so a staged payload is not refused. */
internal fun parcelPayload(parcel: ParcelEntity, memberships: List<FarmParcelMembershipEntity>): JsonObject {
    fun chars(value: String?) = value?.let { it.codePointCount(0, it.length) } ?: 0
    fun optional(value: String?) = value?.let(::JsonPrimitive) ?: JsonNull
    val texts = mapOf("cadastralReference" to parcel.cadastralReference, "cadastralPolygon" to parcel.cadastralPolygon,
        "cadastralParcel" to parcel.cadastralParcel, "municipality" to parcel.municipality, "province" to parcel.province,
        "sourceProvider" to parcel.sourceProvider, "sourceImportedAt" to parcel.sourceImportedAt?.let(::instantText),
        "variety" to parcel.variety, "irrigationSystem" to parcel.irrigationSystem,
        "irrigationNetwork" to parcel.irrigationNetwork, "irrigationSector" to parcel.irrigationSector,
        "irrigationDays" to parcel.irrigationDays)
    require(chars(parcel.displayName) in 1..200 && parcel.displayName.isNotBlank())
    require(texts.values.all { chars(it) <= 200 } && chars(parcel.notes) <= 10_000)
    require((texts.values + listOf(parcel.displayName, parcel.notes, parcel.geometryGeoJson)).none { it?.contains('\u0000') == true })
    require(parcel.source in setOf("MANUAL", "CATASTRO") && parcel.status in setOf(RecordStatus.ACTIVE, RecordStatus.ARCHIVED))
    require(parcel.irrigationSystem == null || parcel.irrigationSystem in setOf("DRYLAND", "DRIP", "SPRINKLER", "OTHER"))
    require(parcel.source != "CATASTRO" || (!parcel.cadastralReference.isNullOrBlank() && !parcel.geometryGeoJson.isNullOrBlank()))
    require((parcel.oliveTreeCount ?: 0) >= 0)
    require(memberships.size in 1..1000 && memberships.map { it.id }.toSet().size == memberships.size)
    require(memberships.all { it.parcelId == parcel.id && it.workspaceId == parcel.workspaceId &&
        (it.validUntil == null || !it.validUntil.isBefore(it.validFrom)) })
    // Same farm and start twice, or two live periods with positive length that overlap, are refused.
    memberships.forEachIndexed { i, a -> memberships.drop(i + 1).forEach { b ->
        require(!(a.farmId == b.farmId && a.validFrom == b.validFrom))
        fun live(m: FarmParcelMembershipEntity) = m.metadata.deletedAt == null && (m.validUntil == null || m.validUntil.isAfter(m.validFrom))
        if (live(a) && live(b)) require(!((a.validUntil == null || b.validFrom.isBefore(a.validUntil)) &&
            (b.validUntil == null || a.validFrom.isBefore(b.validUntil))))
    } }
    return buildJsonObject {
        put("id", parcel.id.toString())
        put("workspaceId", parcel.workspaceId.toString())
        put("displayName", parcel.displayName)
        texts.forEach { (key, value) -> put(key, optional(value)) }
        put("source", parcel.source)
        put("geometryGeoJson", optional(parcel.geometryGeoJson))
        put("cadastralAreaM2", area(parcel.cadastralAreaM2))
        put("managedAreaM2", area(parcel.managedAreaM2))
        put("notes", optional(parcel.notes))
        put("status", parcel.status.name)
        put("oliveTreeCount", parcel.oliveTreeCount?.let(::JsonPrimitive) ?: JsonNull)
        put("memberships", buildJsonArray { memberships.forEach { add(membershipPayload(it)) } })
    }.also { require(it.toString().toByteArray(Charsets.UTF_8).size <= 4 * 1024 * 1024) }
}

/** One parcel field as PostgreSQL's canonical JSONB text renders it: plain decimals, quoted text. */
internal fun parcelFieldScalar(key: String, value: JsonElement): String = when {
    value == JsonNull -> "null"
    key in NUMBERS -> (value as JsonPrimitive).also { require(!it.isString) }.content.also {
        require(DECIMAL.matches(it) && (key != "oliveTreeCount" || !it.contains('.')))
    }
    else -> pgScalar(value, text = true, nullable = false)
}

/** Hash matches the server's sha256(p_parcel::text) over PostgreSQL's canonical JSONB text. */
internal fun parcelCreateHash(parcel: JsonObject): String {
    val canonical = pgObject(parcel) { key, value ->
        if (key != "memberships") parcelFieldScalar(key, value)
        else (value as JsonArray).joinToString(", ", "[", "]") { item ->
            pgObject(item as JsonObject) { field, element ->
                pgScalar(element, text = true, nullable = field in setOf("validUntil", "deletedAt"))
            }
        }
    }
    return MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 255) }
}

internal fun parseParcelCreateEnvelope(payload: String): ParcelCreateSnapshot? = runCatching {
    require(payload.toByteArray(Charsets.UTF_8).size <= 4 * 1024 * 1024 + 4096)
    val root = Json.parseToJsonElement(payload) as JsonObject
    require(root.keys == setOf("issuer", "localVersion", "parcel"))
    val issuer = root.getValue("issuer").jsonPrimitive.content
    val localVersion = root.getValue("localVersion").jsonPrimitive.longOrNull ?: error("localVersion")
    val parcel = root.getValue("parcel") as JsonObject
    require(issuer.isNotBlank() && localVersion > 0 && parcel.keys == PARCEL_KEYS)
    fun text(o: JsonObject, key: String) = o.getValue(key).jsonPrimitive.also { require(it.isString) }.content
    val memberships = (parcel.getValue("memberships") as JsonArray).map { it as JsonObject }
    require(memberships.all { it.keys == setOf("id", "farmId", "validFrom", "validUntil", "deletedAt") })
    val workspaceId = UUID.fromString(text(parcel, "workspaceId"))
    val parcelId = UUID.fromString(text(parcel, "id"))
    // Rebuild the entities and require the canonical payload back: unknown or altered values are refused.
    fun instant(o: JsonObject, key: String) = o.getValue(key).takeUnless { it == JsonNull }?.let { Instant.parse(text(o, key)) }
    fun optional(key: String) = parcel.getValue(key).takeUnless { it == JsonNull }?.let { text(parcel, key) }
    fun number(key: String) = parcel.getValue(key).takeUnless { it == JsonNull }?.jsonPrimitive?.also { require(!it.isString) }?.content
    val rows = memberships.map { m ->
        FarmParcelMembershipEntity(UUID.fromString(text(m, "id")), workspaceId, UUID.fromString(text(m, "farmId")), parcelId,
            Instant.parse(text(m, "validFrom")), instant(m, "validUntil"),
            LocalMetadata(Instant.EPOCH, Instant.EPOCH, deletedAt = instant(m, "deletedAt")))
    }
    val entity = ParcelEntity(parcelId, workspaceId, text(parcel, "displayName"), optional("cadastralReference"),
        optional("cadastralPolygon"), optional("cadastralParcel"), optional("municipality"), optional("province"),
        text(parcel, "source"), optional("geometryGeoJson"), optional("sourceProvider"),
        optional("sourceImportedAt")?.let(Instant::parse), number("cadastralAreaM2")?.toDouble(),
        number("managedAreaM2")?.toDouble(), optional("notes"), RecordStatus.valueOf(text(parcel, "status")),
        LocalMetadata(Instant.EPOCH, Instant.EPOCH), number("oliveTreeCount")?.toInt(), optional("variety"),
        optional("irrigationSystem"), optional("irrigationNetwork"), optional("irrigationSector"), optional("irrigationDays"))
    require(parcelPayload(entity, rows) == parcel)
    ParcelCreateSnapshot(issuer, localVersion, parcelId, workspaceId, rows.map { it.farmId }.toSet(),
        memberships.associateBy { UUID.fromString(text(it, "id")) }, parcel, parcelCreateHash(parcel))
}.getOrNull()

/** The farm the parcel belongs to now (latest live membership): the G1b consent gate for its outbox. */
internal fun currentFarm(memberships: List<FarmParcelMembershipEntity>): UUID? = memberships
    .filter { it.metadata.deletedAt == null }
    .maxWithOrNull(compareBy<FarmParcelMembershipEntity>({ it.validUntil == null }, { it.validFrom }))?.farmId
