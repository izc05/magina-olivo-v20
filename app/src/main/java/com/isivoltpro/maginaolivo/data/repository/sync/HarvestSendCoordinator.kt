package com.isivoltpro.maginaolivo.data.repository.sync

import androidx.room.withTransaction
import com.isivoltpro.maginaolivo.core.id.IdGenerator
import com.isivoltpro.maginaolivo.data.local.MaginaOlivoDatabase
import com.isivoltpro.maginaolivo.data.local.entity.HarvestEntity
import com.isivoltpro.maginaolivo.data.local.entity.SyncOutboxEntity
import com.isivoltpro.maginaolivo.data.local.model.OutboxOperation
import com.isivoltpro.maginaolivo.data.local.model.OutboxStatus
import com.isivoltpro.maginaolivo.data.local.model.SyncEntityType
import com.isivoltpro.maginaolivo.data.local.model.SyncStatus
import com.isivoltpro.maginaolivo.data.repository.JornadaLedger
import com.isivoltpro.maginaolivo.data.repository.WorkspaceOwnerVerifier
import com.isivoltpro.maginaolivo.data.repository.buildDeliveryPayloadJson
import com.isivoltpro.maginaolivo.data.repository.buildDeliveryPayloadJsonV3
import com.isivoltpro.maginaolivo.data.repository.pgObject
import com.isivoltpro.maginaolivo.data.repository.pgScalar
import com.isivoltpro.maginaolivo.domain.attachment.AttachmentOwnerType
import com.isivoltpro.maginaolivo.domain.harvest.AUTO_DAY
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
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

internal enum class HarvestSendOutcome {
    STAGED,
    /** The marker needs no request (derived kilos only, a never-sent day, an adopted day): resolved, not sent. */
    RESOLVED_NOOP,
    /** Another operation of this Jornada is staged or in flight: one at a time, never two. */
    WAITING_SIBLING,
    SYNCED,
    /** Confirmed by receipt and HEAD, but the Jornada changed locally after staging: kept PENDING. */
    CONFIRMED_LOCAL_PENDING,
    ALREADY_CONFIRMED,
    /** The server already holds the canonical AUTO_DAY: retained with its id, never created twice. */
    AUTO_DAY_EXISTS,
    /** Unsent dependents moved onto the canonical AUTO_DAY, atomically and audited. */
    REBASED,
    /** A rebase is not safe (prepared, sent or unknown dependents): nothing mutated, kept for review. */
    RETAINED,
    /**
     * #808 R1-b: a hand-recorded (MANUAL) Jornada with a parcel split. The 12-key contract cannot carry
     * the split, so the Jornada is never sent incomplete: retained with zero RPC, bytes and UUID intact.
     */
    MANUAL_PARCELS_UNSUPPORTED,
    CONFLICT,
    REVIEW_HELD,
    OUTCOME_UNKNOWN,
    CONSENT_REQUIRED,
    PARENT_NOT_SYNCED,
    NOT_AUTHORIZED,
    OFFLINE,
    REJECTED,
}

/** Typed `last_error_code` values of HARVEST operations. All are BLOCKED: never re-sent automatically. */
internal object HarvestCodes {
    const val AUTO_DAY_EXISTS = "AUTO_DAY_EXISTS"
    const val AUTO_DAY_REBASED = "AUTO_DAY_REBASED"
    const val MANUAL_PARCELS_UNSUPPORTED = "MANUAL_PARCELS_UNSUPPORTED"
    const val RESOLVED_DERIVED_ONLY = "RESOLVED_DERIVED_ONLY"
    const val RESOLVED_ADOPTED_NOOP = "RESOLVED_ADOPTED_NOOP"
    const val RESOLVED_NEVER_SENT = "RESOLVED_NEVER_SENT"
    const val RECEIPT_CONFLICT = "HARVEST_RECEIPT_CONFLICT"
    const val HEAD_MISMATCH = "HARVEST_HEAD_MISMATCH"
    const val CONFLICT_PREFIX = "HARVEST_CONFLICT:"
    const val INVALID_PREFIX = "HARVEST_INVALID:"
    const val REVIEW_PREFIX = "SYNC_REVIEW:"
}

/**
 * #808 Jornada (HARVEST) sender, through `sync_own_harvest_v1`, `delete_own_harvest_v1`,
 * `check_sync_operations` and `get_own_harvest_head_v1` only (never a direct table write).
 * Explicit QA entry point: no composition root, scheduler or worker constructs it.
 *
 * - [stage] freezes ONCE, into the existing unbound marker, the account, the farm and an envelope with
 *   the closed payload; its canonical hash is the server's. What the marker means is decided by the
 *   Jornada's state, never by the marker label: CREATE until a receipt confirms it, then UPDATE (own
 *   fields only) or DELETE. An AUTO_DAY never carries kilos: the server derives them, and a marker
 *   produced only by a derived-kilos reconciliation is resolved without a request.
 * - [send] needs the fresh server OWNER of the workspace, then the write, this actor's exact receipt
 *   and `get_own_harvest_head_v1` naming this operation at the receipt's version with the same hash.
 *   Only then, in one local transaction, is the Jornada confirmed. A lost response stays unknown and
 *   is retried with the SAME operation and bytes.
 * - [rebaseOntoCanonical] after AUTO_DAY_EXISTS: only when every dependent was never prepared or sent.
 */
internal class HarvestSendCoordinator(
    private val database: MaginaOlivoDatabase,
    private val account: AccountRepository,
    private val issuer: String,
    private val transport: DeliveryRpcTransport,
    private val ledger: (UUID) -> FarmLinkLedger?,
    private val ownerVerifier: WorkspaceOwnerVerifier?,
    private val idGenerator: IdGenerator,
    private val now: () -> Instant = Instant::now,
) {
    suspend fun stage(operationId: UUID): HarvestSendOutcome = try {
        if (issuer.isBlank()) HarvestSendOutcome.NOT_AUTHORIZED
        else account.withServerVerifiedIdentity(remote = { Unit }) { userId, _ ->
            database.withTransaction { stageLocked(operationId, userId) }
        }
    } catch (cancel: CancellationException) { throw cancel
    } catch (failure: AuthFailure) {
        if (failure.kind == AuthFailure.Kind.NETWORK) HarvestSendOutcome.OFFLINE else HarvestSendOutcome.NOT_AUTHORIZED
    }

    private suspend fun stageLocked(operationId: UUID, userId: UUID): HarvestSendOutcome {
        val outbox = database.syncOutboxDao()
        val op = outbox.findById(operationId) ?: return HarvestSendOutcome.REJECTED
        if (op.entityType != SyncEntityType.HARVEST || op.status != OutboxStatus.PENDING || op.accountId != null ||
            op.payloadJson != null || op.attemptCount != 0) return HarvestSendOutcome.REJECTED
        val day = database.harvestDao().findById(op.entityId) ?: return HarvestSendOutcome.REJECTED
        val farmId = day.farmId ?: return HarvestSendOutcome.REJECTED
        val siblings = outbox.listForEntity(SyncEntityType.HARVEST, day.id).filter { it.id != op.id && it.payloadJson != null }
        if (siblings.any { it.status != OutboxStatus.CONFIRMED }) return HarvestSendOutcome.WAITING_SIBLING
        parentProblem(userId, day)?.let { return it }
        if (!consented(userId, day.workspaceId, farmId)) return HarvestSendOutcome.CONSENT_REQUIRED
        val at = now().toEpochMilli()
        // R1-b, after authorization and consent: a MANUAL day with a parcel split is never frozen into an
        // envelope that would drop it. The unbound marker is kept (BLOCKED with its cause, never deleted).
        if (manualSplitWouldBeLost(day, deleting = day.metadata.deletedAt != null)) {
            return if (outbox.resolveUnstagedHarvest(op.id, HarvestCodes.MANUAL_PARCELS_UNSUPPORTED, at) == 1)
                HarvestSendOutcome.MANUAL_PARCELS_UNSUPPORTED else HarvestSendOutcome.REJECTED
        }
        val remote = day.metadata.remoteVersion
        val envelope = when {
            remote == null && day.metadata.deletedAt != null ->
                return resolve(op, HarvestCodes.RESOLVED_NEVER_SENT, at)
            remote == null -> HarvestEnvelope(issuer, day.metadata.version, HarvestKind.CREATE, day.id,
                runCatching { harvestPayload(day, null) }.getOrNull() ?: return HarvestSendOutcome.REJECTED, null)
            day.metadata.deletedAt != null -> HarvestEnvelope(issuer, day.metadata.version, HarvestKind.DELETE, day.id, null, remote)
            else -> {
                val payload = runCatching { harvestPayload(day, remote) }.getOrNull() ?: return HarvestSendOutcome.REJECTED
                // The last payload this device had confirmed. None means the Jornada was adopted (rebase): this
                // device never wrote it and never overwrites it.
                val last = siblings.filter { it.status == OutboxStatus.CONFIRMED }
                    .mapNotNull { parseHarvestEnvelope(it.payloadJson!!) }.filter { it.harvest != null }
                    .maxByOrNull { it.localVersion }
                    ?: return resolve(op, HarvestCodes.RESOLVED_ADOPTED_NOOP, at)
                if (ownFields(last.harvest!!) == ownFields(payload)) return resolve(op, HarvestCodes.RESOLVED_DERIVED_ONLY, at)
                HarvestEnvelope(issuer, day.metadata.version, HarvestKind.UPDATE, day.id, payload, null)
            }
        }
        return if (outbox.stageHarvest(op.id, userId.toString(), farmId, envelope.json(), at) == 1) HarvestSendOutcome.STAGED
        else HarvestSendOutcome.REJECTED
    }

    /**
     * R1-b: true when sending would drop data. Only a non-AUTO_DAY day (MANUAL, or a legacy day without origin,
     * sent as MANUAL) that still holds harvest_parcels rows, and only for CREATE/UPDATE: an AUTO_DAY split is
     * derived by the server from its Pesadas, and a logical DELETE loses nothing.
     */
    private suspend fun manualSplitWouldBeLost(day: HarvestEntity, deleting: Boolean): Boolean =
        !deleting && day.dayOrigin != AUTO_DAY && database.harvestDao().listParcels(day.id).isNotEmpty()

    private suspend fun resolve(op: SyncOutboxEntity, code: String, at: Long): HarvestSendOutcome =
        if (database.syncOutboxDao().resolveUnstagedHarvest(op.id, code, at) == 1) HarvestSendOutcome.RESOLVED_NOOP
        else HarvestSendOutcome.REJECTED

    suspend fun send(operationId: UUID, lease: DeliveryDispatchLease): HarvestSendOutcome {
        lease.requireOpen()
        if (issuer.isBlank()) return HarvestSendOutcome.NOT_AUTHORIZED
        val op = database.syncOutboxDao().findById(operationId) ?: return HarvestSendOutcome.REJECTED
        if (op.entityType != SyncEntityType.HARVEST) return HarvestSendOutcome.REJECTED
        if (op.status == OutboxStatus.CONFIRMED) return HarvestSendOutcome.ALREADY_CONFIRMED
        val accountId = op.accountId ?: return HarvestSendOutcome.REJECTED
        val payload = op.payloadJson ?: return HarvestSendOutcome.REJECTED
        if (op.status !in setOf(OutboxStatus.PENDING, OutboxStatus.FAILED)) return HarvestSendOutcome.REJECTED
        val envelope = parseHarvestEnvelope(payload) ?: return HarvestSendOutcome.REJECTED
        val day = database.harvestDao().findById(op.entityId) ?: return HarvestSendOutcome.REJECTED
        val farmId = day.farmId ?: return HarvestSendOutcome.REJECTED
        if (envelope.issuer != issuer || envelope.harvestId != day.id || op.farmId != farmId) return HarvestSendOutcome.REJECTED
        // R1-b, before any network call: an operation staged (or left UNKNOWN) before the day got a parcel split,
        // or by an older client, is retained as it is. Zero RPC; UUID, frozen bytes and attempt count intact; an
        // UNKNOWN outcome stays recorded in the message. Only the account that froze it may do so.
        if (manualSplitWouldBeLost(day, deleting = envelope.kind == HarvestKind.DELETE)) {
            if (account.state.value.userId?.toString() != accountId) return HarvestSendOutcome.NOT_AUTHORIZED
            val previous = if (op.status == OutboxStatus.FAILED) "previous=${op.lastErrorCode};attempts=${op.attemptCount}" else null
            return if (database.syncOutboxDao().blockHarvest(op.id, accountId, payload, HarvestCodes.MANUAL_PARCELS_UNSUPPORTED,
                    previous, now().toEpochMilli()) == 1) HarvestSendOutcome.MANUAL_PARCELS_UNSUPPORTED
                else HarvestSendOutcome.REJECTED
        }
        if (!consented(UUID.fromString(accountId), day.workspaceId, farmId)) return HarvestSendOutcome.CONSENT_REQUIRED
        return try {
            lease.withActiveRequest {
                account.withServerVerifiedIdentity(remote = { session ->
                    if (session.userId.toString() != accountId) return@withServerVerifiedIdentity Remote.Unauthorized
                    val verifier = ownerVerifier ?: return@withServerVerifiedIdentity Remote.Unauthorized
                    if (!verifier.isCurrentOwner(session, day.workspaceId)) return@withServerVerifiedIdentity Remote.Unauthorized
                    exchange(session, op.id, envelope)
                }, commit = { userId, result ->
                    if (userId.toString() != accountId) return@withServerVerifiedIdentity HarvestSendOutcome.NOT_AUTHORIZED
                    when (result) {
                        is Remote.Confirmed -> applyReceipt(op, envelope, result.version)
                        is Remote.AutoDayExists -> block(op, HarvestCodes.AUTO_DAY_EXISTS,
                            "harvest_id=${result.canonical};version=${result.version ?: "unknown"}", HarvestSendOutcome.AUTO_DAY_EXISTS)
                        is Remote.Review -> block(op, HarvestCodes.REVIEW_PREFIX + result.cause, null, HarvestSendOutcome.REVIEW_HELD)
                        is Remote.Conflict -> block(op, HarvestCodes.CONFLICT_PREFIX + result.cause, null, HarvestSendOutcome.CONFLICT)
                        Remote.ReceiptConflict -> block(op, HarvestCodes.RECEIPT_CONFLICT, null, HarvestSendOutcome.CONFLICT)
                        Remote.HeadMismatch -> block(op, HarvestCodes.HEAD_MISMATCH, null, HarvestSendOutcome.CONFLICT)
                        is Remote.Invalid -> block(op, HarvestCodes.INVALID_PREFIX + result.cause, null, HarvestSendOutcome.REJECTED)
                        Remote.Unknown -> {
                            database.syncOutboxDao().markHarvestUnknown(op.id, accountId, payload, now().toEpochMilli())
                            HarvestSendOutcome.OUTCOME_UNKNOWN
                        }
                        Remote.Unauthorized -> HarvestSendOutcome.NOT_AUTHORIZED
                    }
                })
            }
        } catch (cancel: CancellationException) { throw cancel
        } catch (failure: AuthFailure) {
            if (failure.kind == AuthFailure.Kind.NETWORK) HarvestSendOutcome.OFFLINE else HarvestSendOutcome.NOT_AUTHORIZED
        }
    }

    /** The write, then this actor's receipt and the strict HEAD, all under the one verified session. */
    private suspend fun exchange(session: AuthSession, operationId: UUID, envelope: HarvestEnvelope): Remote {
        val body = when (envelope.kind) {
            HarvestKind.DELETE -> """{"p_operation_id":"$operationId","p_harvest_id":"${envelope.harvestId}","p_expected_version":${envelope.expectedVersion}}"""
            else -> """{"p_operation_id":"$operationId","p_harvest":${envelope.harvest}}"""
        }
        val written = call(session, if (envelope.kind == HarvestKind.DELETE) DELETE_RPC else SYNC_RPC, body)
        when (written) {
            is Answer.Failed -> return written.remote
            is Answer.Ok -> {
                val ack = written.body
                if (ack["operation_id"].text() != operationId.toString() || ack["entity_id"].text() != envelope.harvestId.toString() ||
                    ack["status"].text() != "APPLIED" || ack["payload_version"].number() != 1L ||
                    (ack["remote_version"].number() ?: 0L) < 1L || ack["idempotent"].bool() == null) return Remote.Unknown
            }
        }
        val receipts = call(session, RECEIPT_RPC, """{"p_operation_ids":["$operationId"]}""")
        val rows = ((receipts as? Answer.Ok)?.body?.get("confirmed_receipts") as? JsonArray) ?: return Remote.Unknown
        val row = rows.singleOrNull() as? JsonObject ?: return Remote.Unknown
        val version = row["remote_version"].number() ?: return Remote.ReceiptConflict
        if (row["operation_id"].text() != operationId.toString() || row["entity_id"].text() != envelope.harvestId.toString() ||
            row["entity_type"].text() != "HARVEST" || row["payload_version"].number() != 1L ||
            row["payload_hash"].text() != envelope.hash || version < 1L) return Remote.ReceiptConflict
        val head = (call(session, HEAD_RPC, """{"p_harvest_id":"${envelope.harvestId}"}""") as? Answer.Ok)?.body ?: return Remote.Unknown
        if (head["contract_version"].number() != 1L || head["harvest_id"].text() != envelope.harvestId.toString() ||
            head["head_operation_id"].text() != operationId.toString() || head["version"].number() != version ||
            head["head_payload_version"].number() != 1L || head["head_payload_hash"].text() != envelope.hash ||
            head["deleted"].bool() != (envelope.kind == HarvestKind.DELETE)) return Remote.HeadMismatch
        return Remote.Confirmed(version)
    }

    private sealed interface Answer {
        data class Ok(val body: JsonObject) : Answer
        data class Failed(val remote: Remote) : Answer
    }

    /** Raw call: keeps the server's typed token and, for AUTO_DAY_EXISTS, the canonical id it is allowed to see. */
    private suspend fun call(session: AuthSession, name: String, body: String): Answer {
        val response = try { transport.rpc(name, body, session.accessToken) }
        catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { return Answer.Failed(Remote.Unknown) }
        val parsed = runCatching { Json.parseToJsonElement(response.body) as JsonObject }.getOrNull()
        if (response.status in 200..299) return parsed?.let { Answer.Ok(it) } ?: Answer.Failed(Remote.Unknown)
        val code = parsed?.get("code").text()
        val token = parsed?.get("message").text()?.let { TYPED.matchEntire(it) }
        val kind = token?.groupValues?.get(1)
        val cause = token?.groupValues?.get(2)
        return Answer.Failed(when {
            kind == "conflict" && cause == HarvestCodes.AUTO_DAY_EXISTS && code == "23505" -> {
                val canonical = parsed?.get("details").text()?.let { CANONICAL.matchEntire(it)?.groupValues?.get(1) }
                    ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                if (canonical == null) Remote.Conflict(HarvestCodes.AUTO_DAY_EXISTS)
                else {
                    val head = (call(session, HEAD_RPC, """{"p_harvest_id":"$canonical"}""") as? Answer.Ok)?.body
                    val version = head?.takeIf { it["harvest_id"].text() == canonical.toString() && it["deleted"].bool() == false }
                        ?.get("version").number()
                    Remote.AutoDayExists(canonical, version)
                }
            }
            kind == "review" && cause != null && code in setOf("22023", "42501") -> Remote.Review(cause)
            kind == "conflict" && cause != null && code == "23505" -> Remote.Conflict(cause)
            code == "42501" || response.status in setOf(401, 403) -> Remote.Unauthorized
            kind == "invalid" && cause != null -> Remote.Invalid(cause)
            code == "22023" || response.status == 400 -> Remote.Invalid("PAYLOAD")
            else -> Remote.Unknown
        })
    }

    private suspend fun applyReceipt(op: SyncOutboxEntity, envelope: HarvestEnvelope, version: Long): HarvestSendOutcome =
        database.withTransaction {
            val current = database.syncOutboxDao().findById(op.id) ?: return@withTransaction HarvestSendOutcome.REJECTED
            if (current.payloadJson != op.payloadJson || current.accountId != op.accountId ||
                current.status !in setOf(OutboxStatus.PENDING, OutboxStatus.FAILED)) return@withTransaction HarvestSendOutcome.REJECTED
            val day = database.harvestDao().findById(op.entityId) ?: return@withTransaction HarvestSendOutcome.REJECTED
            if (database.syncOutboxDao().markConfirmed(op.id, now().toEpochMilli()) != 1) return@withTransaction HarvestSendOutcome.REJECTED
            // Exact when the Jornada's own fields are still the ones sent (an AUTO_DAY's derived kilos never count).
            val exact = when (envelope.kind) {
                HarvestKind.DELETE -> day.metadata.deletedAt != null
                else -> day.metadata.deletedAt == null &&
                    runCatching { harvestPayload(day, envelope.harvest!!["baseVersion"].number()) }.getOrNull() == envelope.harvest
            }
            database.harvestDao().upsert(day.copy(metadata = day.metadata.copy(remoteVersion = version,
                syncStatus = if (exact) SyncStatus.SYNCED else day.metadata.syncStatus, lastSyncedAt = now())))
            if (exact) HarvestSendOutcome.SYNCED else HarvestSendOutcome.CONFIRMED_LOCAL_PENDING
        }

    private suspend fun block(op: SyncOutboxEntity, code: String, message: String?, outcome: HarvestSendOutcome) =
        if (database.syncOutboxDao().blockHarvest(op.id, op.accountId!!, op.payloadJson!!, code, message, now().toEpochMilli()) == 1)
            outcome else HarvestSendOutcome.REJECTED

    /**
     * After AUTO_DAY_EXISTS: moves this device's unsent dependents onto the canonical AUTO_DAY, in ONE
     * transaction, only when NOTHING of the losing day was ever prepared, sent, confirmed or unknown
     * (beyond the refused CREATE itself), and the day owns nothing but Pesadas. Every Pesada is moved with
     * its unsent marker rebuilt (same operation id, same kilos and parcels, only the Jornada changes); the
     * losing day is retired locally with an audit code. Anything else: [HarvestSendOutcome.RETAINED],
     * nothing mutated. The canonical day is adopted with the version its HEAD reported.
     *
     * #808 hardening: the rewrite needs, inside the authenticated flow, the server's FRESH confirmation
     * that this session still OWNS the workspace of the losing day (same check as [send]). Without an
     * owner verifier, with another account, or with the ownership revoked: NOT_AUTHORIZED, nothing mutated.
     */
    suspend fun rebaseOntoCanonical(operationId: UUID): HarvestSendOutcome {
        if (issuer.isBlank()) return HarvestSendOutcome.NOT_AUTHORIZED
        val verifier = ownerVerifier ?: return HarvestSendOutcome.NOT_AUTHORIZED
        val op = database.syncOutboxDao().findById(operationId) ?: return HarvestSendOutcome.REJECTED
        val accountId = op.accountId ?: return HarvestSendOutcome.REJECTED
        val workspaceId = database.harvestDao().findById(op.entityId)?.workspaceId ?: return HarvestSendOutcome.REJECTED
        return try { account.withServerVerifiedIdentity(remote = { session ->
            session.userId.toString() == accountId && verifier.isCurrentOwner(session, workspaceId)
        }) { userId, owner ->
            if (!owner || userId.toString() != accountId) HarvestSendOutcome.NOT_AUTHORIZED
            else database.withTransaction { rebaseLocked(operationId, userId, workspaceId) }
        }
        } catch (cancel: CancellationException) { throw cancel
        } catch (failure: AuthFailure) {
            if (failure.kind == AuthFailure.Kind.NETWORK) HarvestSendOutcome.OFFLINE else HarvestSendOutcome.NOT_AUTHORIZED
        }
    }

    private suspend fun rebaseLocked(operationId: UUID, userId: UUID, verifiedWorkspace: UUID): HarvestSendOutcome {
        val outbox = database.syncOutboxDao()
        val op = outbox.findById(operationId) ?: return HarvestSendOutcome.REJECTED
        if (op.entityType != SyncEntityType.HARVEST || op.status != OutboxStatus.BLOCKED ||
            op.lastErrorCode != HarvestCodes.AUTO_DAY_EXISTS || op.accountId != userId.toString()) return HarvestSendOutcome.REJECTED
        val envelope = op.payloadJson?.let(::parseHarvestEnvelope) ?: return HarvestSendOutcome.REJECTED
        if (envelope.issuer != issuer) return HarvestSendOutcome.REJECTED
        val match = AUDIT.matchEntire(op.lastErrorMessage ?: "") ?: return HarvestSendOutcome.RETAINED
        val canonical = UUID.fromString(match.groupValues[1])
        val canonicalVersion = match.groupValues[2].toLongOrNull() ?: return HarvestSendOutcome.RETAINED
        val losing = database.harvestDao().findById(op.entityId) ?: return HarvestSendOutcome.REJECTED
        // The ownership was verified for THIS workspace; a day that moved meanwhile is not covered by it.
        if (losing.workspaceId != verifiedWorkspace) return HarvestSendOutcome.NOT_AUTHORIZED
        if (envelope.kind != HarvestKind.CREATE || losing.dayOrigin != AUTO_DAY || losing.metadata.remoteVersion != null ||
            losing.metadata.deletedAt != null || canonical == losing.id) return HarvestSendOutcome.RETAINED
        database.harvestDao().findById(canonical)?.let { existing ->
            if (existing.farmId != losing.farmId || existing.campaignId != losing.campaignId ||
                existing.harvestDate != losing.harvestDate || existing.dayOrigin != AUTO_DAY) return HarvestSendOutcome.RETAINED
        }
        // The day owns nothing but Pesadas; any other harvest operation of it is unbound.
        if (losing.collectionMethod != null || losing.workerCount != null || losing.machineryText != null || losing.notes != null ||
            database.labourDao().listForHarvest(losing.id).isNotEmpty() || database.equipmentDao().listForHarvest(losing.id).isNotEmpty() ||
            database.expenseDao().listForHarvest(losing.id).isNotEmpty() ||
            database.documentDao().countLiveForOwner(AttachmentOwnerType.HARVEST.name, losing.id) > 0 ||
            outbox.listForEntity(SyncEntityType.HARVEST, losing.id).any { it.id != op.id && it.payloadJson != null }
        ) return HarvestSendOutcome.RETAINED
        val pesadas = database.deliveryDao().listAllForHarvest(losing.id)
        val markers = pesadas.associateWith { outbox.listForEntity(SyncEntityType.DELIVERY, it.id) }
        // #808 hardening: EVERY Pesada keeps a guaranteed way out. One with no outbox operation (`any` over an
        // empty list was vacuously safe) or with any marker that is not an unsent, never-prepared, frozen
        // CREATE/UPDATE of this account is never moved: RETAINED, nothing mutated.
        val unsafe = pesadas.any { pesada ->
            val own = markers.getValue(pesada)
            pesada.metadata.remoteVersion != null || own.isEmpty() || own.any { marker ->
                marker.status != OutboxStatus.PENDING || marker.attemptCount != 0 || marker.payloadVersion !in setOf(2, 3) ||
                    marker.payloadJson == null || marker.operation !in setOf(OutboxOperation.CREATE, OutboxOperation.UPDATE) ||
                    (marker.accountId != null && marker.accountId != userId.toString()) ||
                    database.deliveryDispatchPrepareDao().findByOperation(marker.id) != null
            }
        }
        if (unsafe) return HarvestSendOutcome.RETAINED
        val at = now()
        val millis = at.toEpochMilli()
        if (database.harvestDao().findById(canonical) == null) {
            database.harvestDao().upsert(losing.copy(id = canonical, weightGrams = 0L,
                metadata = losing.metadata.copy(remoteVersion = canonicalVersion, syncStatus = SyncStatus.PENDING, updatedAt = at)))
        }
        for (pesada in pesadas) {
            val moved = pesada.copy(harvestId = canonical)
            database.deliveryDao().upsert(moved)
            val shares = database.deliveryDao().listParcels(moved.id)
            for (marker in markers.getValue(pesada)) {
                val rebuilt = if (marker.payloadVersion == 3) buildDeliveryPayloadJsonV3(moved, shares)
                    else buildDeliveryPayloadJson(moved, marker.baseRemoteVersion, shares)
                check(outbox.rebaseUnsentDeliveryPayload(marker.id, rebuilt, millis) == 1)
            }
        }
        database.harvestDao().deleteParcels(losing.id)
        database.harvestDao().upsert(losing.copy(weightGrams = 0L, metadata = losing.metadata.copy(deletedAt = at, updatedAt = at)))
        check(outbox.relabelHarvestRetention(op.id, HarvestCodes.AUTO_DAY_EXISTS, HarvestCodes.AUTO_DAY_REBASED,
            "rebased_to=$canonical", millis) == 1)
        outbox.listForEntity(SyncEntityType.HARVEST, losing.id).filter { it.id != op.id && it.status == OutboxStatus.PENDING }
            .forEach { outbox.resolveUnstagedHarvest(it.id, HarvestCodes.AUTO_DAY_REBASED, millis) }
        JornadaLedger(database, idGenerator).reconcile(canonical, at)
        return HarvestSendOutcome.REBASED
    }

    /**
     * Locally confirmed parents: WORKSPACE (#798, owned), FARM (#799) and the campaign, each with a remote
     * version and no unconfirmed CREATE. The OWNER itself is checked fresh by the server at [send].
     */
    private suspend fun parentProblem(userId: UUID, day: HarvestEntity): HarvestSendOutcome? {
        val workspace = database.workspaceDao().findById(day.workspaceId) ?: return HarvestSendOutcome.PARENT_NOT_SYNCED
        if (workspace.metadata.deletedAt != null || workspace.ownerUserId != userId) return HarvestSendOutcome.NOT_AUTHORIZED
        if (workspace.metadata.remoteVersion == null || unconfirmedCreate(SyncEntityType.WORKSPACE, workspace.id))
            return HarvestSendOutcome.PARENT_NOT_SYNCED
        val farm = database.farmDao().findById(day.farmId ?: return HarvestSendOutcome.REJECTED) ?: return HarvestSendOutcome.PARENT_NOT_SYNCED
        if (farm.workspaceId != day.workspaceId || farm.metadata.deletedAt != null) return HarvestSendOutcome.REJECTED
        if (farm.metadata.remoteVersion == null || unconfirmedCreate(SyncEntityType.FARM, farm.id)) return HarvestSendOutcome.PARENT_NOT_SYNCED
        day.campaignId?.let { campaignId ->
            val campaign = database.campaignDao().findById(campaignId) ?: return HarvestSendOutcome.PARENT_NOT_SYNCED
            if (campaign.farmId != farm.id || campaign.metadata.deletedAt != null) return HarvestSendOutcome.REJECTED
            if (campaign.metadata.remoteVersion == null || unconfirmedCreate(SyncEntityType.CAMPAIGN, campaign.id))
                return HarvestSendOutcome.PARENT_NOT_SYNCED
        }
        return null
    }

    private suspend fun unconfirmedCreate(type: SyncEntityType, id: UUID) =
        database.syncOutboxDao().listForEntity(type, id).any { it.operation == OutboxOperation.CREATE && it.status != OutboxStatus.CONFIRMED }

    private fun consented(userId: UUID, workspaceId: UUID, farmId: UUID): Boolean {
        val current = runCatching { ledger(userId) }.getOrNull() ?: return false
        if (current.subject != "$issuer|$userId" || farmId !in current.activeFarmIds || current.latestRevocation(farmId) != null) return false
        return current.consents.any { consent -> consent.farms.any { it.id == farmId && it.workspaceId == workspaceId } }
    }

    private sealed interface Remote {
        data class Confirmed(val version: Long) : Remote
        data class AutoDayExists(val canonical: UUID, val version: Long?) : Remote
        data class Review(val cause: String) : Remote
        data class Conflict(val cause: String) : Remote
        data class Invalid(val cause: String) : Remote
        data object ReceiptConflict : Remote
        data object HeadMismatch : Remote
        data object Unknown : Remote
        data object Unauthorized : Remote
    }

    internal companion object {
        const val SYNC_RPC = "sync_own_harvest_v1"
        const val DELETE_RPC = "delete_own_harvest_v1"
        const val RECEIPT_RPC = "check_sync_operations"
        const val HEAD_RPC = "get_own_harvest_head_v1"
        val NAMES = setOf(SYNC_RPC, DELETE_RPC, RECEIPT_RPC, HEAD_RPC)
        private val TYPED = Regex("^(?:sync|harvest) (review|denied|invalid|conflict): ([A-Z][A-Z_]{0,63})$")
        private val CANONICAL = Regex("^harvest_id=([0-9a-f-]{36})$")
        private val AUDIT = Regex("^harvest_id=([0-9a-f-]{36});version=([0-9]+|unknown)$")
    }
}

private fun kotlinx.serialization.json.JsonElement?.text(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
private fun kotlinx.serialization.json.JsonElement?.number(): Long? = (this as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull
private fun kotlinx.serialization.json.JsonElement?.bool(): Boolean? = (this as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull

internal enum class HarvestKind { CREATE, UPDATE, DELETE }

internal val HARVEST_TEXT = setOf("id", "farmId", "campaignId", "harvestDate", "dayOrigin", "destination", "notes",
    "collectionMethod", "machineryText")
internal val HARVEST_NULLABLE = setOf("campaignId", "weightGrams", "destination", "notes", "collectionMethod", "workerCount",
    "machineryText", "baseVersion")
internal val HARVEST_KEYS = HARVEST_TEXT + setOf("weightGrams", "workerCount", "baseVersion")

/**
 * The closed 12-key `p_harvest` of `sync_own_harvest_v1`, validated like the server. An AUTO_DAY never
 * carries kilos (the server derives them from its Pesadas); a MANUAL day carries its typed kilos.
 */
internal fun harvestPayload(day: HarvestEntity, baseVersion: Long?): JsonObject {
    val auto = day.dayOrigin == AUTO_DAY
    fun chars(value: String?) = value?.let { it.codePointCount(0, it.length) } ?: 0
    require(day.farmId != null && (!auto || day.campaignId != null) && day.weightGrams >= 0L)
    require(chars(day.destination) <= 512 && chars(day.notes) <= 10_000 && chars(day.collectionMethod) <= 100 &&
        chars(day.machineryText) <= 2_000 && (day.workerCount ?: 0) >= 0 && (baseVersion == null || baseVersion >= 1L))
    fun optional(value: String?) = value?.let(::JsonPrimitive) ?: JsonNull
    return buildJsonObject {
        put("id", day.id.toString())
        put("farmId", day.farmId.toString())
        put("campaignId", optional(day.campaignId?.toString()))
        put("harvestDate", day.harvestDate.toString())
        put("dayOrigin", if (auto) "AUTO_DAY" else "MANUAL")
        put("weightGrams", if (auto) JsonNull else JsonPrimitive(day.weightGrams))
        put("destination", optional(day.destination))
        put("notes", optional(day.notes))
        put("collectionMethod", optional(day.collectionMethod))
        put("workerCount", day.workerCount?.let(::JsonPrimitive) ?: JsonNull)
        put("machineryText", optional(day.machineryText))
        put("baseVersion", baseVersion?.let(::JsonPrimitive) ?: JsonNull)
    }
}

/** Own fields only: what a Jornada UPDATE could change. A difference only in derived kilos or the base is no change. */
private fun ownFields(payload: JsonObject): JsonObject = JsonObject(payload.filterKeys { it != "baseVersion" })

private fun sha256(text: String) = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
    .joinToString("") { "%02x".format(it.toInt() and 255) }

/** The server's sha256(p_harvest::text) over PostgreSQL's canonical JSONB text. */
internal fun harvestPayloadHash(payload: JsonObject): String {
    require(payload.keys == HARVEST_KEYS)
    return sha256(pgObject(payload) { key, value -> pgScalar(value, key in HARVEST_TEXT, key in HARVEST_NULLABLE) })
}

/** The server's hash of a delete: jsonb_build_object('kind','DELETE','harvestId',id,'expectedVersion',v)::text. */
internal fun harvestDeleteHash(harvestId: UUID, expectedVersion: Long): String {
    val value = buildJsonObject { put("kind", "DELETE"); put("harvestId", harvestId.toString()); put("expectedVersion", expectedVersion) }
    return sha256(pgObject(value) { key, element -> pgScalar(element, key != "expectedVersion", false) })
}

internal data class HarvestEnvelope(
    val issuer: String,
    val localVersion: Long,
    val kind: HarvestKind,
    val harvestId: UUID,
    val harvest: JsonObject?,
    val expectedVersion: Long?,
) {
    val hash: String get() = if (kind == HarvestKind.DELETE) harvestDeleteHash(harvestId, expectedVersion!!) else harvestPayloadHash(harvest!!)

    fun json(): String = buildJsonObject {
        put("issuer", issuer); put("localVersion", localVersion); put("kind", kind.name); put("harvestId", harvestId.toString())
        put("harvest", harvest ?: JsonNull); put("expectedVersion", expectedVersion?.let(::JsonPrimitive) ?: JsonNull)
    }.toString()
}

internal fun parseHarvestEnvelope(payload: String): HarvestEnvelope? = runCatching {
    val root = Json.parseToJsonElement(payload) as JsonObject
    require(root.keys == setOf("issuer", "localVersion", "kind", "harvestId", "harvest", "expectedVersion"))
    val kind = HarvestKind.valueOf(root["kind"].text()!!)
    val harvest = root["harvest"].takeUnless { it == JsonNull } as JsonObject?
    val expected = root["expectedVersion"].number()
    require((kind == HarvestKind.DELETE) == (harvest == null) && (kind == HarvestKind.DELETE) == (expected != null))
    if (harvest != null) require(harvest.keys == HARVEST_KEYS && harvest["id"].text() == root["harvestId"].text())
    HarvestEnvelope(root["issuer"].text()!!, root["localVersion"].number()!!, kind, UUID.fromString(root["harvestId"].text()),
        harvest, expected).also { it.hash }
}.getOrNull()
