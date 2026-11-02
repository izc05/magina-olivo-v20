package com.isivoltpro.maginaolivo.data.repository.sync

import androidx.room.withTransaction
import com.isivoltpro.maginaolivo.data.local.MaginaOlivoDatabase
import com.isivoltpro.maginaolivo.data.local.entity.FarmParcelMembershipEntity
import com.isivoltpro.maginaolivo.data.local.entity.ParcelEntity
import com.isivoltpro.maginaolivo.data.local.entity.SyncOutboxEntity
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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

internal enum class ParcelMutationKind(val operation: OutboxOperation, val rpc: String, val argument: String) {
    UPDATE(OutboxOperation.UPDATE, "update_own_parcel_v1", "p_parcel"),
    ARCHIVE(OutboxOperation.ARCHIVE, "archive_own_parcel_v1", "p_archive"),
    RESTORE(OutboxOperation.RESTORE, "restore_own_parcel_v1", "p_restore"),
}

internal enum class ParcelMutationOutcome {
    STAGED,
    SYNCED,
    /** Receipt recorded but the parcel changed after staging: kept PENDING with its local edits. */
    CONFIRMED_LOCAL_PENDING,
    ALREADY_CONFIRMED,
    /** Strict per-parcel order: an earlier PARCEL marker of this parcel is not confirmed yet. */
    WAITING_PREVIOUS,
    /** R1: an ARCHIVE waits for every unconfirmed operation that references the parcel. */
    WAITING_DEPENDENCIES,
    /** A legacy marker the local history cannot classify exactly: held for an explicit decision. */
    REVIEW_REQUIRED,
    /** 409 PARCEL_VERSION_CONFLICT: the server moved on; blocked, neither side overwritten. */
    NEEDS_REBASE,
    CONFLICT,
    OUTCOME_UNKNOWN,
    /** The farm involved lacks active G1b consent under this account: nothing is attributed or sent. */
    CONSENT_REQUIRED,
    /** The WORKSPACE, a FARM or the PARCEL itself is not yet confirmed remotely. */
    PARENT_NOT_SYNCED,
    NOT_AUTHORIZED,
    OFFLINE,
    REJECTED,
}

/** What a PARCEL marker means, proven from the immutable membership history, or why it cannot be. */
internal sealed interface ParcelMutationPlan {
    data object Update : ParcelMutationPlan
    data class Archive(val closed: FarmParcelMembershipEntity) : ParcelMutationPlan
    data class Restore(val opened: FarmParcelMembershipEntity) : ParcelMutationPlan
    data class Review(val detail: String) : ParcelMutationPlan
}

/**
 * G2.6 sender for PARCEL UPDATE, ARCHIVE and RESTORE through `update_own_parcel_v1`,
 * `archive_own_parcel_v1`, `restore_own_parcel_v1` and `check_sync_operations`. Explicit QA entry
 * point; no app composition root or scheduler constructs it.
 *
 * Markers of one parcel are sent strictly in creation order, one confirmed before the next is staged,
 * so UPDATE, ARCHIVE and RESTORE are never reordered. Each staged marker carries the remote version it
 * expects; the server applies it only on that version (CAS).
 */
internal class ParcelMutationSendCoordinator(
    private val database: MaginaOlivoDatabase,
    private val account: AccountRepository,
    private val issuer: String,
    private val transport: DeliveryRpcTransport,
    /** Fresh durable G1b ledger for the account, or null when missing, unreadable or another account's. */
    private val ledger: (UUID) -> FarmLinkLedger?,
    private val now: () -> Instant = Instant::now,
) {
    suspend fun stage(operationId: UUID): ParcelMutationOutcome = try {
        if (issuer.isBlank()) ParcelMutationOutcome.NOT_AUTHORIZED
        else account.withServerVerifiedIdentity(remote = { Unit }) { userId, _ ->
            database.withTransaction {
                val dao = database.syncOutboxDao()
                val op = dao.findById(operationId) ?: return@withTransaction ParcelMutationOutcome.REJECTED
                if (op.entityType != SyncEntityType.PARCEL || op.operation !in MUTATIONS ||
                    op.status != OutboxStatus.PENDING || op.accountId != null || op.payloadJson != null ||
                    op.attemptCount != 0) return@withTransaction ParcelMutationOutcome.REJECTED
                val markers = dao.listForEntity(SyncEntityType.PARCEL, op.entityId)
                if (markers.takeWhile { it.id != op.id }.any { it.status != OutboxStatus.CONFIRMED })
                    return@withTransaction ParcelMutationOutcome.WAITING_PREVIOUS
                val parcel = database.parcelDao().findById(op.entityId) ?: return@withTransaction ParcelMutationOutcome.REJECTED
                val expected = parcel.metadata.remoteVersion ?: return@withTransaction ParcelMutationOutcome.PARENT_NOT_SYNCED
                val memberships = database.parcelDao().listMemberships(parcel.id)
                val (kind, mutation, farm) = when (val plan = classifyParcelMarker(op, memberships, markers)) {
                    is ParcelMutationPlan.Review -> {
                        dao.holdParcelMutationForReview(op.id, plan.detail, now().toEpochMilli())
                        return@withTransaction ParcelMutationOutcome.REVIEW_REQUIRED
                    }
                    ParcelMutationPlan.Update -> Triple(ParcelMutationKind.UPDATE,
                        runCatching { parcelFieldsPayload(parcel, memberships) }.getOrNull()
                            ?: return@withTransaction ParcelMutationOutcome.REJECTED,
                        currentFarm(memberships) ?: return@withTransaction ParcelMutationOutcome.REJECTED)
                    is ParcelMutationPlan.Archive -> Triple(ParcelMutationKind.ARCHIVE, archivePayload(plan.closed), plan.closed.farmId)
                    is ParcelMutationPlan.Restore -> Triple(ParcelMutationKind.RESTORE, restorePayload(plan.opened), plan.opened.farmId)
                }
                parentProblem(userId, parcel.workspaceId, farm)?.let { return@withTransaction it }
                if (!consented(userId, parcel.workspaceId, farm)) return@withTransaction ParcelMutationOutcome.CONSENT_REQUIRED
                if (kind == ParcelMutationKind.ARCHIVE) {
                    val dependents = dao.listParcelDependents(parcel.id)
                    if (dependents.isNotEmpty()) {
                        dao.markParcelMutationWaiting(op.id, WAITING_DEPENDENCIES, dependencySummary(dependents),
                            now().toEpochMilli())
                        return@withTransaction ParcelMutationOutcome.WAITING_DEPENDENCIES
                    }
                }
                val envelope = buildJsonObject {
                    put("issuer", issuer); put("kind", kind.name); put("localVersion", parcel.metadata.version)
                    put("parcelId", parcel.id.toString()); put("workspaceId", parcel.workspaceId.toString())
                    put("farmId", farm.toString()); put("expectedVersion", expected); put("mutation", mutation)
                }.toString()
                if (dao.stageParcelMutation(op.id, userId.toString(), parcel.id, farm, expected, envelope,
                        now().toEpochMilli()) != 1) ParcelMutationOutcome.REJECTED else ParcelMutationOutcome.STAGED
            }
        }
    } catch (cancel: CancellationException) { throw cancel
    } catch (failure: AuthFailure) {
        if (failure.kind == AuthFailure.Kind.NETWORK) ParcelMutationOutcome.OFFLINE else ParcelMutationOutcome.NOT_AUTHORIZED
    } catch (_: Exception) { ParcelMutationOutcome.NOT_AUTHORIZED }

    /**
     * Sends or replays one staged marker with the same operation id and payload after a timeout or lost
     * response; the server answers an exact replay with its idempotent receipt. Failures never delete
     * the outbox row or its payload.
     */
    suspend fun send(operationId: UUID, lease: DeliveryDispatchLease): ParcelMutationOutcome {
        lease.requireOpen()
        if (issuer.isBlank()) return ParcelMutationOutcome.NOT_AUTHORIZED
        val dao = database.syncOutboxDao()
        val op = dao.findById(operationId) ?: return ParcelMutationOutcome.REJECTED
        if (op.entityType != SyncEntityType.PARCEL || op.operation !in MUTATIONS) return ParcelMutationOutcome.REJECTED
        val accountId = op.accountId
        val payload = op.payloadJson
        if (accountId == null || payload == null) return ParcelMutationOutcome.REJECTED
        if (op.status == OutboxStatus.CONFIRMED) return ParcelMutationOutcome.ALREADY_CONFIRMED
        val snapshot = parseParcelMutationEnvelope(payload) ?: return ParcelMutationOutcome.REJECTED
        val parcel = database.parcelDao().findById(op.entityId) ?: return ParcelMutationOutcome.REJECTED
        // Consent is checked for the farm in the immutable payload; a revocation wins over BLOCKED.
        if (!consented(UUID.fromString(accountId), snapshot.workspaceId, snapshot.farmId))
            return ParcelMutationOutcome.CONSENT_REQUIRED
        if (op.status !in setOf(OutboxStatus.PENDING, OutboxStatus.FAILED) || snapshot.issuer != issuer ||
            snapshot.parcelId != parcel.id || snapshot.workspaceId != parcel.workspaceId || op.farmId != snapshot.farmId ||
            op.baseRemoteVersion != snapshot.expectedVersion ||
            dao.listForEntity(SyncEntityType.PARCEL, op.entityId).takeWhile { it.id != op.id }
                .any { it.status != OutboxStatus.CONFIRMED }
        ) return ParcelMutationOutcome.REJECTED
        return try {
            lease.withActiveRequest {
                account.withServerVerifiedIdentity(remote = { session ->
                    if (session.userId.toString() != accountId ||
                        parentProblem(session.userId, snapshot.workspaceId, snapshot.farmId) != null)
                        return@withServerVerifiedIdentity Remote.Unauthorized
                    try {
                        val touched = mutate(session, op.id, snapshot)
                        Remote.Confirmed(receipt(session, op.id) ?: return@withServerVerifiedIdentity Remote.Unknown, touched)
                    } catch (cancel: CancellationException) { throw cancel
                    } catch (failure: DeliveryRpcFailure) {
                        when (failure.error) {
                            DeliveryRpcError.CONFLICT -> Remote.Conflict(failure.reason)
                            DeliveryRpcError.FORBIDDEN -> Remote.Unauthorized
                            DeliveryRpcError.INVALID_PAYLOAD -> Remote.Invalid
                            else -> Remote.Unknown
                        }
                    } catch (_: Exception) {
                        // A malformed reply after a request: the server may have committed.
                        Remote.Unknown
                    }
                }, commit = { userId, result ->
                    if (userId.toString() != accountId || parentProblem(userId, snapshot.workspaceId, snapshot.farmId) != null)
                        ParcelMutationOutcome.NOT_AUTHORIZED
                    else when (result) {
                        is Remote.Confirmed -> applyReceipt(op, snapshot, result.receipt, result.touched, userId)
                        is Remote.Conflict -> when (result.reason) {
                            "PARCEL_VERSION_CONFLICT" -> block(op, "NEEDS_REBASE", ParcelMutationOutcome.NEEDS_REBASE)
                            // Typed server causes stay visible to the user: ARCHIVE_BLOCKED_CAMPAIGN, RECEIPT_CONFLICT...
                            else -> block(op, result.reason ?: "PARCEL_CONFLICT", ParcelMutationOutcome.CONFLICT)
                        }
                        Remote.Invalid -> block(op, "PARCEL_INVALID", ParcelMutationOutcome.REJECTED)
                        Remote.Unknown -> {
                            dao.markParcelMutationUnknown(op.id, accountId, payload, now().toEpochMilli())
                            ParcelMutationOutcome.OUTCOME_UNKNOWN
                        }
                        Remote.Unauthorized -> ParcelMutationOutcome.NOT_AUTHORIZED
                    }
                })
            }
        } catch (cancel: CancellationException) { throw cancel
        } catch (failure: AuthFailure) {
            if (failure.kind == AuthFailure.Kind.NETWORK) ParcelMutationOutcome.OFFLINE else ParcelMutationOutcome.NOT_AUTHORIZED
        } catch (_: Exception) { ParcelMutationOutcome.NOT_AUTHORIZED }
    }

    /**
     * The explicit decision for a marker held as REVIEW_REQUIRED. UPDATE is always provable (the current
     * fields); ARCHIVE and RESTORE only when exactly one membership closed or opened at the marker's instant.
     */
    suspend fun resolveReview(operationId: UUID, kind: ParcelMutationKind): Boolean = database.withTransaction {
        val op = database.syncOutboxDao().findById(operationId) ?: return@withTransaction false
        if (op.entityType != SyncEntityType.PARCEL || op.status != OutboxStatus.BLOCKED ||
            op.lastErrorCode != "REVIEW_REQUIRED") return@withTransaction false
        val live = database.parcelDao().listMemberships(op.entityId).filter { it.metadata.deletedAt == null }
        val proven = when (kind) {
            ParcelMutationKind.UPDATE -> true
            ParcelMutationKind.ARCHIVE -> live.count { it.validUntil == op.createdAt } == 1
            ParcelMutationKind.RESTORE -> live.count { it.validFrom == op.createdAt } == 1
        }
        proven && database.syncOutboxDao().resolveParcelMutationReview(op.id, kind.operation, now().toEpochMilli()) == 1
    }

    /** R1 causes for the user: the unconfirmed operations an ARCHIVE of this parcel is waiting for. */
    suspend fun archiveDependencies(parcelId: UUID): List<SyncOutboxEntity> =
        database.syncOutboxDao().listParcelDependents(parcelId)

    private suspend fun mutate(session: AuthSession, operationId: UUID, snapshot: ParcelMutationSnapshot): JsonArray {
        val ack = transport.callRpcObject(snapshot.kind.rpc,
            """{"p_operation_id":"$operationId","p_parcel_id":"${snapshot.parcelId}","p_expected_version":${snapshot.expectedVersion},"${snapshot.kind.argument}":${snapshot.mutation}}""",
            session)
        val touched = ack["memberships"] as? JsonArray ?: throw DeliveryRpcFailure(DeliveryRpcError.PROTOCOL)
        if (ack.keys != setOf("success", "operation_id", "entity_id", "kind", "status", "remote_version", "payload_version",
                "memberships", "idempotent") ||
            !ack.getValue("success").jsonPrimitive.boolean ||
            ack.getValue("operation_id").jsonPrimitive.content != operationId.toString() ||
            ack.getValue("entity_id").jsonPrimitive.content != snapshot.parcelId.toString() ||
            ack.getValue("kind").jsonPrimitive.content != snapshot.kind.name ||
            ack.getValue("status").jsonPrimitive.content != "APPLIED" ||
            ack.getValue("remote_version").jsonPrimitive.long != snapshot.expectedVersion + 1 ||
            ack.getValue("payload_version").jsonPrimitive.long != 1L ||
            ack.getValue("idempotent").jsonPrimitive.booleanOrNull == null ||
            touched.map { (it as JsonObject).getValue("id").jsonPrimitive.content } != listOfNotNull(snapshot.membershipId?.toString()) ||
            touched.any { (it as JsonObject).getValue("version").jsonPrimitive.long < 1 }
        ) throw DeliveryRpcFailure(DeliveryRpcError.PROTOCOL)
        return touched
    }

    private suspend fun receipt(session: AuthSession, operationId: UUID): MutationReceipt? {
        val root = transport.callRpcObject(RECEIPT_RPC, """{"p_operation_ids":["$operationId"]}""", session)
        val rows = root["confirmed_receipts"] as? JsonArray ?: throw DeliveryRpcFailure(DeliveryRpcError.PROTOCOL)
        if (rows.isEmpty()) return null
        val row = rows.singleOrNull() as? JsonObject ?: throw DeliveryRpcFailure(DeliveryRpcError.PROTOCOL)
        return MutationReceipt(
            UUID.fromString(row.getValue("operation_id").jsonPrimitive.content),
            UUID.fromString(row.getValue("entity_id").jsonPrimitive.content),
            row.getValue("entity_type").jsonPrimitive.content,
            row.getValue("payload_hash").jsonPrimitive.content,
            row.getValue("payload_version").jsonPrimitive.long,
            row.getValue("remote_version").jsonPrimitive.long,
        )
    }

    private suspend fun applyReceipt(op: SyncOutboxEntity, snapshot: ParcelMutationSnapshot, receipt: MutationReceipt,
        touched: JsonArray, userId: UUID): ParcelMutationOutcome = database.withTransaction {
        val remote = snapshot.expectedVersion + 1
        if (receipt.operationId != op.id || receipt.entityId != op.entityId ||
            receipt.entityType != "PARCEL_${snapshot.kind.name}" || receipt.payloadHash != snapshot.hash ||
            receipt.payloadVersion != 1L || receipt.remoteVersion != remote
        ) return@withTransaction block(op, "RECEIPT_CONFLICT", ParcelMutationOutcome.CONFLICT)
        val currentOp = database.syncOutboxDao().findById(op.id) ?: return@withTransaction ParcelMutationOutcome.REJECTED
        val current = database.parcelDao().findById(op.entityId) ?: return@withTransaction ParcelMutationOutcome.REJECTED
        if (currentOp.payloadJson != op.payloadJson || currentOp.accountId != userId.toString() ||
            currentOp.status !in setOf(OutboxStatus.PENDING, OutboxStatus.FAILED) ||
            current.workspaceId != snapshot.workspaceId
        ) return@withTransaction ParcelMutationOutcome.REJECTED
        if (database.syncOutboxDao().markConfirmed(op.id, now().toEpochMilli()) != 1)
            return@withTransaction ParcelMutationOutcome.REJECTED
        // Later offline changes (and their outbox markers) stay local and pending; never overwritten.
        val exact = current.metadata.version == snapshot.localVersion &&
            database.syncOutboxDao().listForEntity(SyncEntityType.PARCEL, op.entityId)
                .none { it.id != op.id && it.status != OutboxStatus.CONFIRMED }
        val at = now()
        database.parcelDao().upsert(current.copy(metadata = current.metadata.copy(remoteVersion = remote,
            syncStatus = if (exact) SyncStatus.SYNCED else current.metadata.syncStatus, lastSyncedAt = at)))
        touched.forEach { item ->
            val id = UUID.fromString((item as JsonObject).getValue("id").jsonPrimitive.content)
            database.parcelDao().listMemberships(current.id).firstOrNull { it.id == id }?.let { row ->
                database.parcelDao().upsertMembership(row.copy(metadata = row.metadata.copy(
                    remoteVersion = item.getValue("version").jsonPrimitive.long,
                    syncStatus = if (exact) SyncStatus.SYNCED else row.metadata.syncStatus, lastSyncedAt = at)))
            }
        }
        if (exact) ParcelMutationOutcome.SYNCED else ParcelMutationOutcome.CONFIRMED_LOCAL_PENDING
    }

    /** Deterministic refusals keep the payload, stay auditable and are never retried automatically. */
    private suspend fun block(op: SyncOutboxEntity, reason: String, outcome: ParcelMutationOutcome) =
        database.withTransaction {
            if (database.syncOutboxDao().blockParcelMutation(op.id, op.accountId!!, op.payloadJson!!, reason,
                    now().toEpochMilli()) != 1) return@withTransaction ParcelMutationOutcome.REJECTED
            if (outcome != ParcelMutationOutcome.REJECTED) database.parcelDao().findById(op.entityId)?.let {
                database.parcelDao().upsert(it.copy(metadata = it.metadata.copy(syncStatus = SyncStatus.CONFLICT)))
            }
            outcome
        }

    /**
     * Server rule: only the live workspace owner, and the farm involved live, in the workspace and
     * created/owned by the actor. Locally: WORKSPACE confirmed (#798), selected with `write`, and the
     * farm confirmed by its FARM CREATE (#799).
     */
    private suspend fun parentProblem(userId: UUID, workspaceId: UUID, farmId: UUID): ParcelMutationOutcome? {
        val workspace = database.workspaceDao().findById(workspaceId) ?: return ParcelMutationOutcome.PARENT_NOT_SYNCED
        if (workspace.metadata.deletedAt != null || workspace.ownerUserId != userId) return ParcelMutationOutcome.NOT_AUTHORIZED
        if (workspace.metadata.syncStatus != SyncStatus.SYNCED || workspace.metadata.remoteVersion == null ||
            database.syncOutboxDao().listForEntity(SyncEntityType.WORKSPACE, workspaceId)
                .any { it.status != OutboxStatus.CONFIRMED }) return ParcelMutationOutcome.PARENT_NOT_SYNCED
        val farm = database.farmDao().findById(farmId) ?: return ParcelMutationOutcome.PARENT_NOT_SYNCED
        if (farm.workspaceId != workspaceId || farm.metadata.deletedAt != null) return ParcelMutationOutcome.REJECTED
        if (farm.metadata.remoteVersion == null || database.syncOutboxDao().listForEntity(SyncEntityType.FARM, farmId)
                .any { it.operation == OutboxOperation.CREATE && it.status != OutboxStatus.CONFIRMED })
            return ParcelMutationOutcome.PARENT_NOT_SYNCED
        val selected = database.workspaceSelectionDao().find(issuer, userId.toString())?.workspaceId
        if (selected != workspaceId) return ParcelMutationOutcome.NOT_AUTHORIZED
        val cached = database.workspaceAuthorizationCacheDao().find(issuer, userId.toString())
            ?: return ParcelMutationOutcome.NOT_AUTHORIZED
        val grant = runCatching { WorkspaceAuthorizationContract.parse(cached.contractJson) }.getOrNull()
            ?: return ParcelMutationOutcome.NOT_AUTHORIZED
        return if (grant.validFor(issuer, userId, now()) &&
            grant.available(now()).any { it.id == workspaceId && "write" in it.permissions }) null
        else ParcelMutationOutcome.NOT_AUTHORIZED
    }

    /** The farm involved needs an active G1b consent of this account in this workspace. */
    private fun consented(userId: UUID, workspaceId: UUID, farmId: UUID): Boolean {
        val current = runCatching { ledger(userId) }.getOrNull() ?: return false
        if (current.subject != "$issuer|$userId") return false
        return farmId in current.activeFarmIds && current.consents.any { consent ->
            consent.farms.any { it.id == farmId && it.workspaceId == workspaceId }
        }
    }

    private sealed interface Remote {
        data class Confirmed(val receipt: MutationReceipt, val touched: JsonArray) : Remote
        data class Conflict(val reason: String?) : Remote
        data object Invalid : Remote
        data object Unknown : Remote
        data object Unauthorized : Remote
    }

    private data class MutationReceipt(val operationId: UUID, val entityId: UUID, val entityType: String,
        val payloadHash: String, val payloadVersion: Long, val remoteVersion: Long)

    internal companion object {
        const val RECEIPT_RPC = "check_sync_operations"
        const val WAITING_DEPENDENCIES = "WAITING_DEPENDENCIES"
        val NAMES = ParcelMutationKind.entries.map { it.rpc }.toSet() + RECEIPT_RPC
        private val MUTATIONS = setOf(OutboxOperation.UPDATE, OutboxOperation.DELETE, OutboxOperation.ARCHIVE,
            OutboxOperation.RESTORE)
    }
}

/** Counts by type and state only: no names, amounts or identifiers reach the outbox message. */
internal fun dependencySummary(dependents: List<SyncOutboxEntity>): String = dependents
    .groupingBy { "${it.entityType.name}:${it.status.name}" }.eachCount().toSortedMap()
    .entries.joinToString(",") { (key, count) -> "$key=$count" }

/**
 * Exact classification from the immutable membership history. The producer writes the marker and the
 * membership change with the same instant, so a typed or legacy marker is proven only when exactly one
 * membership closed (ARCHIVE, legacy DELETE) or opened (RESTORE, legacy UPDATE from «Restaurar») at
 * that instant and no other PARCEL marker shares it. Anything else is held for review, never guessed.
 */
internal fun classifyParcelMarker(op: SyncOutboxEntity, memberships: List<FarmParcelMembershipEntity>,
    markers: List<SyncOutboxEntity>): ParcelMutationPlan {
    val live = memberships.filter { it.metadata.deletedAt == null }
    val closed = live.filter { it.validUntil == op.createdAt }
    val opened = live.filter { it.validFrom == op.createdAt }
    val resolved = op.lastErrorCode == "RESOLVED_REVIEW"
    val shared = !resolved && markers.any { it.id != op.id && it.createdAt == op.createdAt }
    fun review(reason: String) = ParcelMutationPlan.Review("$reason:closed=${closed.size},opened=${opened.size},shared=$shared")
    return when (op.operation) {
        OutboxOperation.ARCHIVE, OutboxOperation.DELETE ->
            if (closed.size == 1 && opened.isEmpty() && !shared) ParcelMutationPlan.Archive(closed.single()) else review("archive")
        OutboxOperation.RESTORE ->
            if (opened.size == 1 && closed.isEmpty() && !shared) ParcelMutationPlan.Restore(opened.single()) else review("restore")
        OutboxOperation.UPDATE -> when {
            resolved || (closed.isEmpty() && opened.isEmpty() && !shared) -> ParcelMutationPlan.Update
            opened.size == 1 && closed.isEmpty() && !shared -> ParcelMutationPlan.Restore(opened.single())
            else -> review("update")
        }
        else -> review("operation")
    }
}

internal data class ParcelMutationSnapshot(
    val issuer: String,
    val kind: ParcelMutationKind,
    val localVersion: Long,
    val parcelId: UUID,
    val workspaceId: UUID,
    val farmId: UUID,
    val expectedVersion: Long,
    /** The membership the server must report as touched: closed by ARCHIVE or opened by RESTORE. */
    val membershipId: UUID?,
    val mutation: JsonObject,
    val hash: String,
)

private val FIELD_KEYS = setOf("displayName", "cadastralReference", "cadastralPolygon", "cadastralParcel", "municipality",
    "province", "source", "geometryGeoJson", "sourceProvider", "sourceImportedAt", "cadastralAreaM2", "managedAreaM2",
    "notes", "oliveTreeCount", "variety", "irrigationSystem", "irrigationNetwork", "irrigationSector", "irrigationDays")
private val ENVELOPE_KEYS = setOf("issuer", "kind", "localVersion", "parcelId", "workspaceId", "farmId", "expectedVersion",
    "mutation")

/** The 19 own fields of `update_own_parcel_v1`, validated like `create_own_parcel` (no status, no memberships). */
internal fun parcelFieldsPayload(parcel: ParcelEntity,
    memberships: List<FarmParcelMembershipEntity>): JsonObject =
    JsonObject(parcelPayload(parcel, memberships).filterKeys { it in FIELD_KEYS }).also { require(it.keys == FIELD_KEYS) }

internal fun archivePayload(closed: FarmParcelMembershipEntity): JsonObject = buildJsonObject {
    put("archivedAt", instantText(requireNotNull(closed.validUntil)))
    put("closeMembershipId", closed.id.toString())
}

internal fun restorePayload(opened: FarmParcelMembershipEntity): JsonObject = buildJsonObject {
    put("restoredAt", instantText(opened.validFrom))
    put("openMembership", buildJsonObject {
        put("id", opened.id.toString()); put("farmId", opened.farmId.toString()); put("validFrom", instantText(opened.validFrom))
    })
}

/** sha256 of PostgreSQL's canonical JSONB text of {kind, parcelId, expectedVersion, mutation}, as the server hashes it. */
internal fun parcelMutationHash(kind: ParcelMutationKind, parcelId: UUID, expectedVersion: Long, mutation: JsonObject): String {
    val root = buildJsonObject {
        put("kind", kind.name); put("parcelId", parcelId.toString()); put("expectedVersion", expectedVersion)
        put("mutation", mutation)
    }
    val canonical = pgObject(root) { key, value ->
        when (key) {
            "expectedVersion" -> pgScalar(value, text = false, nullable = false)
            "mutation" -> pgObject(value as JsonObject) { field, element ->
                when {
                    kind == ParcelMutationKind.UPDATE -> parcelFieldScalar(field, element)
                    field == "openMembership" -> pgObject(element as JsonObject) { _, item -> pgScalar(item, text = true, nullable = false) }
                    else -> pgScalar(element, text = true, nullable = false)
                }
            }
            else -> pgScalar(value, text = true, nullable = false)
        }
    }
    return MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 255) }
}

internal fun parseParcelMutationEnvelope(payload: String): ParcelMutationSnapshot? = runCatching {
    require(payload.toByteArray(Charsets.UTF_8).size <= 4 * 1024 * 1024 + 4096)
    val root = Json.parseToJsonElement(payload) as JsonObject
    require(root.keys == ENVELOPE_KEYS)
    fun text(o: JsonObject, key: String) = o.getValue(key).jsonPrimitive.also { require(it.isString) }.content
    fun number(key: String) = root.getValue(key).jsonPrimitive.also { require(!it.isString) }.longOrNull ?: error(key)
    val kind = ParcelMutationKind.valueOf(text(root, "kind"))
    val issuer = text(root, "issuer")
    val localVersion = number("localVersion")
    val expectedVersion = number("expectedVersion")
    require(issuer.isNotBlank() && localVersion > 0 && expectedVersion > 0)
    val mutation = root.getValue("mutation") as JsonObject
    val membershipId = when (kind) {
        ParcelMutationKind.UPDATE -> {
            require(mutation.keys == FIELD_KEYS)
            mutation.forEach { (key, value) -> parcelFieldScalar(key, value) }
            null
        }
        ParcelMutationKind.ARCHIVE -> {
            require(mutation.keys == setOf("archivedAt", "closeMembershipId"))
            Instant.parse(text(mutation, "archivedAt")).also { require(instantText(it) == text(mutation, "archivedAt")) }
            UUID.fromString(text(mutation, "closeMembershipId"))
        }
        ParcelMutationKind.RESTORE -> {
            require(mutation.keys == setOf("restoredAt", "openMembership"))
            val open = mutation.getValue("openMembership") as JsonObject
            require(open.keys == setOf("id", "farmId", "validFrom") && text(open, "validFrom") == text(mutation, "restoredAt"))
            Instant.parse(text(open, "validFrom")).also { require(instantText(it) == text(open, "validFrom")) }
            UUID.fromString(text(open, "farmId"))
            UUID.fromString(text(open, "id"))
        }
    }
    val parcelId = UUID.fromString(text(root, "parcelId"))
    val farmId = UUID.fromString(text(root, "farmId"))
    if (kind == ParcelMutationKind.RESTORE)
        require(text(mutation.getValue("openMembership") as JsonObject, "farmId") == farmId.toString())
    ParcelMutationSnapshot(issuer, kind, localVersion, parcelId, UUID.fromString(text(root, "workspaceId")), farmId,
        expectedVersion, membershipId, mutation, parcelMutationHash(kind, parcelId, expectedVersion, mutation))
}.getOrNull()
