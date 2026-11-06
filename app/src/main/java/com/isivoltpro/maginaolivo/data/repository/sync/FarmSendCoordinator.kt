package com.isivoltpro.maginaolivo.data.repository.sync

import androidx.room.withTransaction
import com.isivoltpro.maginaolivo.account.reservation.ReservationRequest
import com.isivoltpro.maginaolivo.data.local.MaginaOlivoDatabase
import com.isivoltpro.maginaolivo.data.local.entity.FarmEntity
import com.isivoltpro.maginaolivo.data.local.entity.SyncOutboxEntity
import com.isivoltpro.maginaolivo.data.local.model.FarmStatus
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
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

internal enum class FarmSendOutcome {
    STAGED,
    SYNCED,
    /** Receipt recorded (remote v1) but the farm changed after staging: kept PENDING with its local edits. */
    CONFIRMED_LOCAL_PENDING,
    ALREADY_CONFIRMED,
    CONFLICT,
    OUTCOME_UNKNOWN,
    /** No active G1b consent for this farm under this account: nothing is attributed or sent. */
    CONSENT_REQUIRED,
    /** The farm's WORKSPACE is not yet confirmed remotely (G2.6 WORKSPACE send). */
    PARENT_NOT_SYNCED,
    NOT_AUTHORIZED,
    OFFLINE,
    REJECTED,
}

/**
 * G2.6 sender for FARM creation only, through the existing G1c `reserve_local_farm_g1c`,
 * `create_reserved_farm` and `check_sync_operations` RPCs. Explicit QA entry point; no app
 * composition root or scheduler constructs it. PARCEL and farm-parcel memberships are out of scope.
 *
 * [stage] binds the app's unbound FARM CREATE marker to the server-verified account, but only for a
 * farm whose WORKSPACE is already confirmed, selected with `write`, owned by the account, and that
 * has active G1b consent. The reservation reuses that consent's id, text version and backup manifest.
 */
internal class FarmSendCoordinator(
    private val database: MaginaOlivoDatabase,
    private val account: AccountRepository,
    private val issuer: String,
    private val transport: DeliveryRpcTransport,
    /** Fresh durable G1b ledger for the account, or null when missing, unreadable or another account's. */
    private val ledger: (UUID) -> FarmLinkLedger?,
    private val now: () -> Instant = Instant::now,
    private val newId: () -> UUID = UUID::randomUUID,
) {
    suspend fun stage(operationId: UUID): FarmSendOutcome = try {
        if (issuer.isBlank()) FarmSendOutcome.NOT_AUTHORIZED
        else account.withServerVerifiedIdentity(remote = { Unit }) { userId, _ ->
            database.withTransaction {
                val op = database.syncOutboxDao().findById(operationId)
                    ?: return@withTransaction FarmSendOutcome.REJECTED
                if (op.entityType != SyncEntityType.FARM || op.operation != OutboxOperation.CREATE ||
                    op.status != OutboxStatus.PENDING || op.accountId != null || op.payloadJson != null ||
                    op.attemptCount != 0) return@withTransaction FarmSendOutcome.REJECTED
                val farm = database.farmDao().findById(op.entityId) ?: return@withTransaction FarmSendOutcome.REJECTED
                if (farm.metadata.deletedAt != null || !validFarm(farm)) return@withTransaction FarmSendOutcome.REJECTED
                parentProblem(userId, farm.workspaceId)?.let { return@withTransaction it }
                val consent = consentFor(userId, farm) ?: return@withTransaction FarmSendOutcome.CONSENT_REQUIRED
                if (otherOpenStaged(op)) return@withTransaction FarmSendOutcome.REJECTED
                val reservation = ReservationRequest(newId(), farm.id, farm.workspaceId, consent.id,
                    consent.textVersion, consent.manifest, consent.aggregateVersion)
                val payload = buildFarmCreateEnvelope(farm, issuer, farm.workspaceId, reservation)
                if (database.syncOutboxDao().stageFarmCreate(op.id, userId.toString(), farm.id, payload,
                        now().toEpochMilli()) != 1) FarmSendOutcome.REJECTED else FarmSendOutcome.STAGED
            }
        }
    } catch (cancel: CancellationException) { throw cancel
    } catch (failure: AuthFailure) {
        if (failure.kind == AuthFailure.Kind.NETWORK) FarmSendOutcome.OFFLINE else FarmSendOutcome.NOT_AUTHORIZED
    } catch (_: Exception) { FarmSendOutcome.NOT_AUTHORIZED }

    /**
     * Sends or replays one staged CREATE. Reservation and creation reuse the same operation ids and
     * payload after a timeout or lost response; both RPCs answer exact replays with the same receipt.
     * Failures never delete the outbox row or its payload.
     */
    suspend fun send(operationId: UUID, lease: DeliveryDispatchLease): FarmSendOutcome {
        lease.requireOpen()
        if (issuer.isBlank()) return FarmSendOutcome.NOT_AUTHORIZED
        val op = database.syncOutboxDao().findById(operationId) ?: return FarmSendOutcome.REJECTED
        if (op.entityType != SyncEntityType.FARM || op.operation != OutboxOperation.CREATE)
            return FarmSendOutcome.REJECTED
        val accountId = op.accountId
        val payload = op.payloadJson
        if (accountId == null || payload == null) return FarmSendOutcome.REJECTED
        if (op.status == OutboxStatus.CONFIRMED) return FarmSendOutcome.ALREADY_CONFIRMED
        val snapshot = parseFarmCreateEnvelope(payload) ?: return FarmSendOutcome.REJECTED
        val farm = database.farmDao().findById(op.entityId) ?: return FarmSendOutcome.REJECTED
        // A revoked or missing G1b consent wins over every other state, including BLOCKED by revocation.
        if (!consentActive(UUID.fromString(accountId), farm, snapshot)) return FarmSendOutcome.CONSENT_REQUIRED
        if (op.status !in setOf(OutboxStatus.PENDING, OutboxStatus.FAILED) || op.payloadVersion < 1 ||
            op.farmId != farm.id || snapshot.issuer != issuer || snapshot.farmId != farm.id ||
            snapshot.workspaceId != farm.workspaceId || farm.metadata.deletedAt != null || otherOpenStaged(op)
        ) return FarmSendOutcome.REJECTED
        return try {
            lease.withActiveRequest {
                account.withServerVerifiedIdentity(remote = { session ->
                    if (session.userId.toString() != accountId ||
                        parentProblem(session.userId, farm.workspaceId) != null)
                        return@withServerVerifiedIdentity Remote.Unauthorized
                    try {
                        val receiptId = reserve(session, snapshot)
                        create(session, op.id, receiptId, snapshot)
                        Remote.Confirmed(receiptId,
                            receipt(session, op.id) ?: return@withServerVerifiedIdentity Remote.Unknown)
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
                    if (userId.toString() != accountId || parentProblem(userId, farm.workspaceId) != null)
                        FarmSendOutcome.NOT_AUTHORIZED
                    else when (result) {
                        is Remote.Confirmed -> applyReceipt(op, snapshot, result.reservationReceiptId, result.receipt, userId)
                        Remote.Conflict -> block(op, "FARM_CONFLICT", FarmSendOutcome.CONFLICT)
                        Remote.Invalid -> block(op, "FARM_INVALID", FarmSendOutcome.REJECTED)
                        Remote.Unknown -> {
                            database.syncOutboxDao().markFarmCreateUnknown(op.id, accountId, payload, now().toEpochMilli())
                            FarmSendOutcome.OUTCOME_UNKNOWN
                        }
                        Remote.Unauthorized -> FarmSendOutcome.NOT_AUTHORIZED
                    }
                })
            }
        } catch (cancel: CancellationException) { throw cancel
        } catch (failure: AuthFailure) {
            if (failure.kind == AuthFailure.Kind.NETWORK) FarmSendOutcome.OFFLINE else FarmSendOutcome.NOT_AUTHORIZED
        } catch (_: Exception) { FarmSendOutcome.NOT_AUTHORIZED }
    }

    /** Reservation receipt must echo this actor, operation, farm, request hash and target workspace. */
    private suspend fun reserve(session: AuthSession, snapshot: FarmCreateSnapshot): UUID {
        val request = snapshot.reservation
        val body = buildJsonObject {
            put("p_remote_workspace", snapshot.workspaceId.toString())
            put("p_request", buildJsonObject {
                put("payloadVersion", request.payloadVersion); put("operationId", request.operationId.toString())
                put("farmId", request.farmId.toString()); put("localWorkspaceId", request.localWorkspaceId.toString())
                put("consentId", request.consentId.toString()); put("consentTextVersion", request.consentTextVersion)
                put("manifestSha256", request.manifestSha256); put("aggregateVersion", request.aggregateVersion)
            })
        }.toString()
        val row = transport.callRpcObject(RESERVE_RPC, body, session)
        fun text(key: String) = row.getValue(key).jsonPrimitive.also { check(it.isString) }.content
        if (row.keys != setOf("issuer", "actorId", "operationId", "farmId", "payloadHash", "remoteWorkspaceId", "receiptId") ||
            text("issuer") != issuer || text("actorId") != session.userId.toString() ||
            text("operationId") != request.operationId.toString() || text("farmId") != request.farmId.toString() ||
            text("payloadHash") != request.payloadHash() || text("remoteWorkspaceId") != snapshot.workspaceId.toString()
        ) throw DeliveryRpcFailure(DeliveryRpcError.PROTOCOL)
        return UUID.fromString(text("receiptId"))
    }

    private suspend fun create(session: AuthSession, operationId: UUID, receiptId: UUID, snapshot: FarmCreateSnapshot) {
        val ack = transport.callRpcObject(CREATE_RPC, buildJsonObject {
            put("p_operation_id", operationId.toString())
            put("p_reservation_operation_id", snapshot.reservation.operationId.toString())
            put("p_reservation_receipt_id", receiptId.toString())
            put("p_farm", snapshot.farm)
        }.toString(), session)
        if (ack.keys != setOf("success", "operation_id", "entity_id", "status", "remote_version", "payload_version", "idempotent") ||
            !ack.getValue("success").jsonPrimitive.boolean ||
            ack.getValue("operation_id").jsonPrimitive.content != operationId.toString() ||
            ack.getValue("entity_id").jsonPrimitive.content != snapshot.farmId.toString() ||
            ack.getValue("status").jsonPrimitive.content != "APPLIED" ||
            ack.getValue("remote_version").jsonPrimitive.long != 1L ||
            ack.getValue("payload_version").jsonPrimitive.long != 1L ||
            ack.getValue("idempotent").jsonPrimitive.booleanOrNull == null
        ) throw DeliveryRpcFailure(DeliveryRpcError.PROTOCOL)
    }

    private suspend fun receipt(session: AuthSession, operationId: UUID): FarmReceipt? {
        val root = transport.callRpcObject(RECEIPT_RPC, """{"p_operation_ids":["$operationId"]}""", session)
        val rows = root["confirmed_receipts"] as? JsonArray ?: throw DeliveryRpcFailure(DeliveryRpcError.PROTOCOL)
        if (rows.isEmpty()) return null
        val row = rows.singleOrNull() as? JsonObject ?: throw DeliveryRpcFailure(DeliveryRpcError.PROTOCOL)
        return FarmReceipt(
            UUID.fromString(row.getValue("operation_id").jsonPrimitive.content),
            UUID.fromString(row.getValue("entity_id").jsonPrimitive.content),
            row.getValue("entity_type").jsonPrimitive.content,
            row.getValue("payload_hash").jsonPrimitive.content,
            row.getValue("payload_version").jsonPrimitive.long,
            row.getValue("remote_version").jsonPrimitive.long,
        )
    }

    private suspend fun applyReceipt(op: SyncOutboxEntity, snapshot: FarmCreateSnapshot, reservationReceiptId: UUID,
        receipt: FarmReceipt, userId: UUID): FarmSendOutcome = database.withTransaction {
        if (receipt.operationId != op.id || receipt.entityId != op.entityId || receipt.entityType != "FARM" ||
            receipt.payloadHash != farmCreateHash(snapshot, reservationReceiptId) ||
            receipt.payloadVersion != 1L || receipt.remoteVersion != 1L
        ) return@withTransaction block(op, "RECEIPT_CONFLICT", FarmSendOutcome.CONFLICT)
        val currentOp = database.syncOutboxDao().findById(op.id) ?: return@withTransaction FarmSendOutcome.REJECTED
        val current = database.farmDao().findById(op.entityId) ?: return@withTransaction FarmSendOutcome.REJECTED
        if (currentOp.payloadJson != op.payloadJson || currentOp.accountId != userId.toString() ||
            currentOp.status !in setOf(OutboxStatus.PENDING, OutboxStatus.FAILED) ||
            current.workspaceId != snapshot.workspaceId
        ) return@withTransaction FarmSendOutcome.REJECTED
        if (database.syncOutboxDao().markConfirmed(op.id, now().toEpochMilli()) != 1)
            return@withTransaction FarmSendOutcome.REJECTED
        // Later offline edits (and their UPDATE markers) stay local and pending; nothing is overwritten.
        val exact = current.metadata.deletedAt == null && current.metadata.version == snapshot.localVersion &&
            farmPayload(current) == snapshot.farm &&
            database.syncOutboxDao().listForEntity(SyncEntityType.FARM, op.entityId)
                .none { it.id != op.id && it.status != OutboxStatus.CONFIRMED }
        database.farmDao().upsert(current.copy(metadata = current.metadata.copy(remoteVersion = 1L,
            syncStatus = if (exact) SyncStatus.SYNCED else current.metadata.syncStatus, lastSyncedAt = now())))
        if (exact) FarmSendOutcome.SYNCED else FarmSendOutcome.CONFIRMED_LOCAL_PENDING
    }

    /** Deterministic refusals stay auditable and are never retried automatically. */
    private suspend fun block(op: SyncOutboxEntity, reason: String, outcome: FarmSendOutcome) =
        database.withTransaction {
            if (database.syncOutboxDao().blockFarmCreate(op.id, op.accountId!!, op.payloadJson!!, reason,
                    now().toEpochMilli()) != 1) return@withTransaction FarmSendOutcome.REJECTED
            if (outcome == FarmSendOutcome.CONFLICT) database.farmDao().findById(op.entityId)?.let {
                database.farmDao().upsert(it.copy(metadata = it.metadata.copy(syncStatus = SyncStatus.CONFLICT)))
            }
            outcome
        }

    /**
     * Server rule: only the live workspace owner may reserve and create farms. Locally the WORKSPACE
     * must be confirmed (no open WORKSPACE outbox), selected, and granted `write` by the fresh cache.
     */
    private suspend fun parentProblem(userId: UUID, workspaceId: UUID): FarmSendOutcome? {
        val workspace = database.workspaceDao().findById(workspaceId) ?: return FarmSendOutcome.PARENT_NOT_SYNCED
        if (workspace.metadata.deletedAt != null || workspace.ownerUserId != userId) return FarmSendOutcome.NOT_AUTHORIZED
        if (workspace.metadata.syncStatus != SyncStatus.SYNCED || workspace.metadata.remoteVersion == null ||
            database.syncOutboxDao().listForEntity(SyncEntityType.WORKSPACE, workspaceId)
                .any { it.status != OutboxStatus.CONFIRMED }) return FarmSendOutcome.PARENT_NOT_SYNCED
        val selected = database.workspaceSelectionDao().find(issuer, userId.toString())?.workspaceId
        if (selected != workspaceId) return FarmSendOutcome.NOT_AUTHORIZED
        val cached = database.workspaceAuthorizationCacheDao().find(issuer, userId.toString())
            ?: return FarmSendOutcome.NOT_AUTHORIZED
        val grant = runCatching { WorkspaceAuthorizationContract.parse(cached.contractJson) }.getOrNull()
            ?: return FarmSendOutcome.NOT_AUTHORIZED
        return if (grant.validFor(issuer, userId, now()) &&
            grant.available(now()).any { it.id == workspaceId && "write" in it.permissions }) null
        else FarmSendOutcome.NOT_AUTHORIZED
    }

    private data class Consent(val id: UUID, val textVersion: Int, val manifest: String, val aggregateVersion: Long)

    /** The latest G1b consent entry that selected this farm in this workspace, while the farm is still active. */
    private fun consentFor(userId: UUID, farm: FarmEntity): Consent? {
        val current = runCatching { ledger(userId) }.getOrNull() ?: return null
        if (current.subject != "$issuer|$userId" || farm.id !in current.activeFarmIds) return null
        val entry = current.consents.lastOrNull { consent -> consent.farms.any { it.id == farm.id } } ?: return null
        val choice = entry.farms.single { it.id == farm.id }
        if (choice.workspaceId != farm.workspaceId) return null
        return Consent(entry.operationId, entry.textVersion, entry.backupManifestHash, choice.version)
    }

    private fun consentActive(userId: UUID, farm: FarmEntity, snapshot: FarmCreateSnapshot): Boolean {
        val consent = consentFor(userId, farm) ?: return false
        return consent.id == snapshot.reservation.consentId
    }

    private suspend fun otherOpenStaged(op: SyncOutboxEntity) =
        database.syncOutboxDao().listForEntity(SyncEntityType.FARM, op.entityId).any {
            it.id != op.id && it.operation == OutboxOperation.CREATE && it.status != OutboxStatus.CONFIRMED
        }

    private sealed interface Remote {
        data class Confirmed(val reservationReceiptId: UUID, val receipt: FarmReceipt) : Remote
        data object Conflict : Remote
        data object Invalid : Remote
        data object Unknown : Remote
        data object Unauthorized : Remote
    }

    private data class FarmReceipt(val operationId: UUID, val entityId: UUID, val entityType: String,
        val payloadHash: String, val payloadVersion: Long, val remoteVersion: Long)

    internal companion object {
        const val RESERVE_RPC = "reserve_local_farm_g1c"
        const val CREATE_RPC = "create_reserved_farm"
        const val RECEIPT_RPC = "check_sync_operations"
        val NAMES = setOf(RESERVE_RPC, CREATE_RPC, RECEIPT_RPC)
    }
}

internal data class FarmCreateSnapshot(
    val issuer: String,
    val localVersion: Long,
    val workspaceId: UUID,
    val farmId: UUID,
    val reservation: ReservationRequest,
    val farm: JsonObject,
)

/** Mirrors the server checks of `create_reserved_farm`, so a staged payload is not refused later. */
internal fun validFarm(farm: FarmEntity): Boolean {
    fun chars(value: String?) = value?.let { it.codePointCount(0, it.length) } ?: 0
    return chars(farm.name) in 1..200 && farm.name.isNotBlank() &&
        chars(farm.description) <= 10_000 && chars(farm.notes) <= 10_000 &&
        chars(farm.municipality) <= 200 && chars(farm.province) <= 200 &&
        listOfNotNull(farm.name, farm.description, farm.municipality, farm.province, farm.notes).none { '\u0000' in it }
}

/**
 * The nine-key `p_farm` of `create_reserved_farm`. Covers and documents are out of scope: the opaque
 * local cover id is never sent and the receiver keeps the local one.
 */
internal fun farmPayload(farm: FarmEntity): JsonObject = buildJsonObject {
    fun optional(value: String?) = value?.let(::JsonPrimitive) ?: JsonNull
    put("id", farm.id.toString())
    put("workspaceId", farm.workspaceId.toString())
    put("name", farm.name)
    put("description", optional(farm.description))
    put("municipality", optional(farm.municipality))
    put("province", optional(farm.province))
    put("notes", optional(farm.notes))
    put("status", farm.status.name)
    put("coverDocumentId", JsonNull)
}

/** Issuer, local revision, target workspace and the G1c reservation request are durable guards. */
internal fun buildFarmCreateEnvelope(farm: FarmEntity, issuer: String, workspaceId: UUID,
    reservation: ReservationRequest): String = buildJsonObject {
    put("issuer", issuer)
    put("localVersion", farm.metadata.version)
    put("remoteWorkspaceId", workspaceId.toString())
    put("reservation", buildJsonObject {
        put("operationId", reservation.operationId.toString())
        put("farmId", reservation.farmId.toString())
        put("localWorkspaceId", reservation.localWorkspaceId.toString())
        put("consentId", reservation.consentId.toString())
        put("consentTextVersion", reservation.consentTextVersion)
        put("manifestSha256", reservation.manifestSha256)
        put("aggregateVersion", reservation.aggregateVersion)
    })
    put("farm", farmPayload(farm))
}.toString()

internal fun parseFarmCreateEnvelope(payload: String): FarmCreateSnapshot? = runCatching {
    require(payload.length <= 65_536)
    val root = Json.parseToJsonElement(payload) as JsonObject
    require(root.keys == setOf("issuer", "localVersion", "remoteWorkspaceId", "reservation", "farm"))
    val issuer = root.getValue("issuer").jsonPrimitive.content
    val localVersion = root.getValue("localVersion").jsonPrimitive.longOrNull ?: error("localVersion")
    val workspaceId = UUID.fromString(root.getValue("remoteWorkspaceId").jsonPrimitive.content)
    val r = root.getValue("reservation") as JsonObject
    require(r.keys == setOf("operationId", "farmId", "localWorkspaceId", "consentId", "consentTextVersion",
        "manifestSha256", "aggregateVersion"))
    fun text(o: JsonObject, key: String) = o.getValue(key).jsonPrimitive.also { require(it.isString) }.content
    val reservation = ReservationRequest(UUID.fromString(text(r, "operationId")), UUID.fromString(text(r, "farmId")),
        UUID.fromString(text(r, "localWorkspaceId")), UUID.fromString(text(r, "consentId")),
        r.getValue("consentTextVersion").jsonPrimitive.int, text(r, "manifestSha256"),
        r.getValue("aggregateVersion").jsonPrimitive.long)
    val farm = root.getValue("farm") as JsonObject
    require(farm.keys == setOf("id", "workspaceId", "name", "description", "municipality", "province", "notes",
        "status", "coverDocumentId"))
    val farmId = UUID.fromString(text(farm, "id"))
    require(UUID.fromString(text(farm, "workspaceId")) == workspaceId && farm.getValue("coverDocumentId") == JsonNull)
    fun optional(key: String) = farm.getValue(key).let { if (it == JsonNull) null else text(farm, key) }
    val entity = FarmEntity(farmId, workspaceId, text(farm, "name"), optional("description"), optional("municipality"),
        optional("province"), null, optional("notes"), FarmStatus.valueOf(text(farm, "status")),
        com.isivoltpro.maginaolivo.data.local.entity.LocalMetadata(Instant.EPOCH, Instant.EPOCH))
    require(issuer.isNotBlank() && localVersion > 0 && validFarm(entity) && farmPayload(entity) == farm)
    require(reservation.farmId == farmId && reservation.localWorkspaceId == workspaceId)
    FarmCreateSnapshot(issuer, localVersion, workspaceId, farmId, reservation, farm)
}.getOrNull()

/**
 * Matches the server's sha256(jsonb_build_object('farm', p_farm, 'reservationOperationId', op,
 * 'reservationReceiptId', receipt)::text) over PostgreSQL's canonical JSONB text.
 */
internal fun farmCreateHash(snapshot: FarmCreateSnapshot, reservationReceiptId: UUID): String {
    val nullable = setOf("description", "municipality", "province", "notes", "coverDocumentId")
    val farm = pgObject(snapshot.farm) { key, value -> pgScalar(value, text = true, nullable = key in nullable) }
    val outer = buildJsonObject {
        put("reservationOperationId", snapshot.reservation.operationId.toString())
        put("reservationReceiptId", reservationReceiptId.toString())
    }
    val canonical = pgObject(JsonObject(outer + ("farm" to JsonPrimitive("")))) { key, value ->
        if (key == "farm") farm else pgScalar(value, text = true, nullable = false)
    }
    return MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 255) }
}
