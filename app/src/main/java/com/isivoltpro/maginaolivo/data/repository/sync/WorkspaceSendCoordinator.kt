package com.isivoltpro.maginaolivo.data.repository.sync

import androidx.room.withTransaction
import com.isivoltpro.maginaolivo.data.local.MaginaOlivoDatabase
import com.isivoltpro.maginaolivo.data.local.entity.LocalMetadata
import com.isivoltpro.maginaolivo.data.local.entity.SyncOutboxEntity
import com.isivoltpro.maginaolivo.data.local.entity.WorkspaceEntity
import com.isivoltpro.maginaolivo.data.local.model.OutboxOperation
import com.isivoltpro.maginaolivo.data.local.model.OutboxStatus
import com.isivoltpro.maginaolivo.data.local.model.SyncEntityType
import com.isivoltpro.maginaolivo.data.local.model.SyncStatus
import com.isivoltpro.maginaolivo.data.repository.pgObject
import com.isivoltpro.maginaolivo.data.repository.pgScalar
import com.isivoltpro.maginaolivo.feature.account.AccountRepository
import com.isivoltpro.maginaolivo.feature.account.AuthFailure
import com.isivoltpro.maginaolivo.feature.account.AuthSession
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

internal enum class WorkspaceSendOutcome {
    STAGED,
    SYNCED,
    /** Receipt recorded (remote v1) but the local row changed after staging: kept PENDING, never overwritten. */
    CONFIRMED_LOCAL_PENDING,
    ALREADY_CONFIRMED,
    CONFLICT,
    OUTCOME_UNKNOWN,
    /** Historical or unbound workspace: it is never attributed to the account without explicit consent. */
    CONSENT_REQUIRED,
    NOT_AUTHORIZED,
    OFFLINE,
    REJECTED,
}

internal data class WorkspaceDraft(
    val name: String,
    val countryCode: String,
    val timezone: String,
    val locale: String,
    val currency: String,
)

internal data class WorkspaceStage(
    val outcome: WorkspaceSendOutcome,
    val workspaceId: UUID? = null,
    val operationId: UUID? = null,
)

/**
 * G2.6 sender for WORKSPACE creation only, through the existing `create_own_workspace` and
 * `check_sync_operations` RPCs. Explicit QA entry point; no app composition root or scheduler
 * constructs it, and FARM/PARCEL/CAMPAIGN are out of its scope.
 *
 * Only a workspace created bound to the signed-in account ([createOwned]) can be sent. A historical
 * CREATE from a signed-out session keeps `account_id`/`payload_json` empty and a random local owner,
 * so [send] answers [WorkspaceSendOutcome.CONSENT_REQUIRED] and touches nothing.
 */
internal class WorkspaceSendCoordinator(
    private val database: MaginaOlivoDatabase,
    private val account: AccountRepository,
    private val issuer: String,
    private val transport: DeliveryRpcTransport,
    private val now: () -> Instant = Instant::now,
    private val newId: () -> UUID = UUID::randomUUID,
) {
    /** Local only (works offline): the stored session is the owner; [send] re-verifies it with the server. */
    suspend fun createOwned(draft: WorkspaceDraft): WorkspaceStage {
        if (issuer.isBlank()) return WorkspaceStage(WorkspaceSendOutcome.NOT_AUTHORIZED)
        if (!validDraft(draft)) return WorkspaceStage(WorkspaceSendOutcome.REJECTED)
        return try {
            account.withLocalIdentity { userId ->
                database.withTransaction {
                    val at = now()
                    val row = WorkspaceEntity(newId(), draft.name, userId, draft.countryCode, draft.timezone,
                        draft.locale, draft.currency, LocalMetadata(at, at, syncStatus = SyncStatus.PENDING))
                    val operationId = newId()
                    database.workspaceDao().upsert(row)
                    database.syncOutboxDao().insert(SyncOutboxEntity(operationId, SyncEntityType.WORKSPACE, row.id,
                        OutboxOperation.CREATE, payloadVersion = 1, createdAt = at, updatedAt = at,
                        accountId = userId.toString(), payloadJson = buildWorkspaceCreateEnvelope(row, issuer)))
                    WorkspaceStage(WorkspaceSendOutcome.STAGED, row.id, operationId)
                }
            }
        } catch (cancel: CancellationException) { throw cancel
        } catch (_: AuthFailure) { WorkspaceStage(WorkspaceSendOutcome.NOT_AUTHORIZED) }
    }

    /**
     * Sends or replays one bound CREATE. The same operation id and payload are reused after a
     * timeout or lost response, so the server answers with its idempotent receipt. Failures never
     * delete the outbox row or its payload.
     */
    suspend fun send(operationId: UUID, lease: DeliveryDispatchLease): WorkspaceSendOutcome {
        lease.requireOpen()
        if (issuer.isBlank()) return WorkspaceSendOutcome.NOT_AUTHORIZED
        val op = database.syncOutboxDao().findById(operationId) ?: return WorkspaceSendOutcome.REJECTED
        if (op.entityType != SyncEntityType.WORKSPACE || op.operation != OutboxOperation.CREATE)
            return WorkspaceSendOutcome.REJECTED
        val workspace = database.workspaceDao().findById(op.entityId) ?: return WorkspaceSendOutcome.REJECTED
        val accountId = op.accountId
        val payload = op.payloadJson
        if (accountId == null || payload == null || workspace.ownerUserId.toString() != accountId)
            return WorkspaceSendOutcome.CONSENT_REQUIRED
        if (op.status == OutboxStatus.CONFIRMED) return WorkspaceSendOutcome.ALREADY_CONFIRMED
        val snapshot = parseWorkspaceCreateEnvelope(payload) ?: return WorkspaceSendOutcome.REJECTED
        if (op.status !in setOf(OutboxStatus.PENDING, OutboxStatus.FAILED) || op.payloadVersion != 1 ||
            snapshot.issuer != issuer || snapshot.workspaceId != op.entityId || workspace.metadata.deletedAt != null ||
            database.syncOutboxDao().listForEntity(SyncEntityType.WORKSPACE, op.entityId)
                .any { it.id != op.id && it.status != OutboxStatus.CONFIRMED }
        ) return WorkspaceSendOutcome.REJECTED
        return try {
            lease.withActiveRequest {
                account.withServerVerifiedIdentity(remote = { session ->
                    // Account switch: another session never sends this account's operation.
                    if (session.userId.toString() != accountId) return@withServerVerifiedIdentity Remote.Unauthorized
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
                        // A malformed ack or receipt after the request: the server may have committed.
                        Remote.Unknown
                    }
                }, commit = { userId, result ->
                    if (userId.toString() != accountId) WorkspaceSendOutcome.NOT_AUTHORIZED
                    else when (result) {
                        is Remote.Confirmed -> applyReceipt(op, snapshot, result.receipt, userId)
                        Remote.Conflict -> block(op, "WORKSPACE_CONFLICT", WorkspaceSendOutcome.CONFLICT)
                        Remote.Invalid -> block(op, "WORKSPACE_INVALID", WorkspaceSendOutcome.REJECTED)
                        Remote.Unknown -> {
                            database.syncOutboxDao().markWorkspaceCreateUnknown(op.id, accountId, payload,
                                now().toEpochMilli())
                            WorkspaceSendOutcome.OUTCOME_UNKNOWN
                        }
                        Remote.Unauthorized -> WorkspaceSendOutcome.NOT_AUTHORIZED
                    }
                })
            }
        } catch (cancel: CancellationException) { throw cancel
        } catch (failure: AuthFailure) {
            if (failure.kind == AuthFailure.Kind.NETWORK) WorkspaceSendOutcome.OFFLINE
            else WorkspaceSendOutcome.NOT_AUTHORIZED
        } catch (_: Exception) { WorkspaceSendOutcome.NOT_AUTHORIZED }
    }

    private suspend fun create(session: AuthSession, operationId: UUID, snapshot: WorkspaceCreateSnapshot) {
        val ack = transport.callRpcObject(CREATE_RPC,
            """{"p_operation_id":"$operationId","p_workspace":${snapshot.workspace}}""", session)
        if (ack.keys != setOf("success", "operation_id", "entity_id", "status", "remote_version", "payload_version", "idempotent") ||
            !ack.getValue("success").jsonPrimitive.boolean ||
            ack.getValue("operation_id").jsonPrimitive.content != operationId.toString() ||
            ack.getValue("entity_id").jsonPrimitive.content != snapshot.workspaceId.toString() ||
            ack.getValue("status").jsonPrimitive.content != "APPLIED" ||
            ack.getValue("remote_version").jsonPrimitive.long != 1L ||
            ack.getValue("payload_version").jsonPrimitive.long != 1L ||
            ack.getValue("idempotent").jsonPrimitive.booleanOrNull == null
        ) throw DeliveryRpcFailure(DeliveryRpcError.PROTOCOL)
    }

    private suspend fun receipt(session: AuthSession, operationId: UUID): WorkspaceReceipt? {
        val root = transport.callRpcObject(RECEIPT_RPC, """{"p_operation_ids":["$operationId"]}""", session)
        val rows = root["confirmed_receipts"] as? JsonArray ?: throw DeliveryRpcFailure(DeliveryRpcError.PROTOCOL)
        if (rows.isEmpty()) return null
        val row = rows.singleOrNull() as? JsonObject ?: throw DeliveryRpcFailure(DeliveryRpcError.PROTOCOL)
        return WorkspaceReceipt(
            UUID.fromString(row.getValue("operation_id").jsonPrimitive.content),
            UUID.fromString(row.getValue("entity_id").jsonPrimitive.content),
            row.getValue("entity_type").jsonPrimitive.content,
            row.getValue("payload_hash").jsonPrimitive.content,
            row.getValue("payload_version").jsonPrimitive.long,
            row.getValue("remote_version").jsonPrimitive.long,
        )
    }

    private suspend fun applyReceipt(op: SyncOutboxEntity, snapshot: WorkspaceCreateSnapshot,
        receipt: WorkspaceReceipt, userId: UUID): WorkspaceSendOutcome = database.withTransaction {
        // Correlation: same operation, entity, aggregate type, payload hash and the creation version.
        if (receipt.operationId != op.id || receipt.entityId != op.entityId || receipt.entityType != "WORKSPACE" ||
            receipt.payloadHash != snapshot.hash || receipt.payloadVersion != 1L || receipt.remoteVersion != 1L
        ) return@withTransaction block(op, "RECEIPT_CONFLICT", WorkspaceSendOutcome.CONFLICT)
        val currentOp = database.syncOutboxDao().findById(op.id) ?: return@withTransaction WorkspaceSendOutcome.REJECTED
        val current = database.workspaceDao().findById(op.entityId) ?: return@withTransaction WorkspaceSendOutcome.REJECTED
        if (currentOp.payloadJson != op.payloadJson || currentOp.accountId != userId.toString() ||
            currentOp.status !in setOf(OutboxStatus.PENDING, OutboxStatus.FAILED) || current.ownerUserId != userId
        ) return@withTransaction WorkspaceSendOutcome.REJECTED
        if (database.syncOutboxDao().markConfirmed(op.id, now().toEpochMilli()) != 1)
            return@withTransaction WorkspaceSendOutcome.REJECTED
        val exact = current.metadata.deletedAt == null && current.metadata.version == snapshot.localVersion &&
            buildWorkspaceCreateEnvelope(current, issuer) == op.payloadJson
        database.workspaceDao().upsert(current.copy(metadata = current.metadata.copy(remoteVersion = 1L,
            syncStatus = if (exact) SyncStatus.SYNCED else current.metadata.syncStatus, lastSyncedAt = now())))
        if (exact) WorkspaceSendOutcome.SYNCED else WorkspaceSendOutcome.CONFIRMED_LOCAL_PENDING
    }

    /** Deterministic refusals stay auditable and are never retried automatically. */
    private suspend fun block(op: SyncOutboxEntity, reason: String, outcome: WorkspaceSendOutcome) =
        database.withTransaction {
            if (database.syncOutboxDao().blockWorkspaceCreate(op.id, op.accountId!!, op.payloadJson!!, reason,
                    now().toEpochMilli()) != 1) return@withTransaction WorkspaceSendOutcome.REJECTED
            if (outcome == WorkspaceSendOutcome.CONFLICT) database.workspaceDao().findById(op.entityId)?.let {
                database.workspaceDao().upsert(it.copy(metadata = it.metadata.copy(syncStatus = SyncStatus.CONFLICT)))
            }
            outcome
        }

    private sealed interface Remote {
        data class Confirmed(val receipt: WorkspaceReceipt) : Remote
        data object Conflict : Remote
        data object Invalid : Remote
        data object Unknown : Remote
        data object Unauthorized : Remote
    }

    private data class WorkspaceReceipt(val operationId: UUID, val entityId: UUID, val entityType: String,
        val payloadHash: String, val payloadVersion: Long, val remoteVersion: Long)

    internal companion object {
        const val CREATE_RPC = "create_own_workspace"
        const val RECEIPT_RPC = "check_sync_operations"
        val NAMES = setOf(CREATE_RPC, RECEIPT_RPC)
    }
}

internal data class WorkspaceCreateSnapshot(
    val issuer: String,
    val localVersion: Long,
    val workspaceId: UUID,
    val workspace: JsonObject,
    val hash: String,
)

/** Mirrors the server checks of `create_own_workspace`, so a staged payload is not refused later. */
internal fun validDraft(draft: WorkspaceDraft): Boolean {
    val name = draft.name.codePointCount(0, draft.name.length)
    return name in 1..200 && draft.name.isNotBlank() &&
        Regex("^[A-Z]{2}$").matches(draft.countryCode) && Regex("^[A-Z]{3}$").matches(draft.currency) &&
        draft.locale.length <= 35 && Regex("^[A-Za-z]{2,8}(-[A-Za-z0-9]{1,8})*$").matches(draft.locale) &&
        draft.timezone.length <= 64 && runCatching { ZoneId.of(draft.timezone) }.isSuccess &&
        listOf(draft.name, draft.countryCode, draft.timezone, draft.locale, draft.currency).none { '\u0000' in it }
}

/** Issuer and local revision are durable guards; only `workspace` reaches the RPC. */
internal fun buildWorkspaceCreateEnvelope(row: WorkspaceEntity, issuer: String): String = buildJsonObject {
    put("issuer", issuer)
    put("localVersion", row.metadata.version)
    put("workspace", buildJsonObject {
        put("id", row.id.toString())
        put("name", row.name)
        put("countryCode", row.countryCode)
        put("timezone", row.timezone)
        put("locale", row.locale)
        put("currency", row.currency)
    })
}.toString()

/** Hash matches the server's sha256(p_workspace::text) over PostgreSQL's canonical JSONB text. */
internal fun parseWorkspaceCreateEnvelope(payload: String): WorkspaceCreateSnapshot? = runCatching {
    require(payload.length <= 16_384)
    val root = Json.parseToJsonElement(payload) as JsonObject
    require(root.keys == setOf("issuer", "localVersion", "workspace"))
    val issuer = root.getValue("issuer").jsonPrimitive.content
    val localVersion = root.getValue("localVersion").jsonPrimitive.longOrNull ?: error("localVersion")
    val workspace = root.getValue("workspace") as JsonObject
    require(workspace.keys == setOf("id", "name", "countryCode", "timezone", "locale", "currency"))
    require(workspace.values.all { it is JsonPrimitive && it.isString })
    require(issuer.isNotBlank() && localVersion > 0)
    require(validDraft(WorkspaceDraft(workspace.text("name"), workspace.text("countryCode"),
        workspace.text("timezone"), workspace.text("locale"), workspace.text("currency"))))
    val canonical = pgObject(workspace) { _, value -> pgScalar(value, text = true, nullable = false) }
    val hash = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 255) }
    WorkspaceCreateSnapshot(issuer, localVersion, UUID.fromString(workspace.text("id")), workspace, hash)
}.getOrNull()

private fun JsonObject.text(key: String) = getValue(key).jsonPrimitive.content
