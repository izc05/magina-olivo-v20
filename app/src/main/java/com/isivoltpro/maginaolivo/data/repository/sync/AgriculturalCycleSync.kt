package com.isivoltpro.maginaolivo.data.repository.sync

import androidx.room.withTransaction
import com.isivoltpro.maginaolivo.data.local.MaginaOlivoDatabase
import com.isivoltpro.maginaolivo.data.local.entity.AgriculturalCycleEntity
import com.isivoltpro.maginaolivo.data.local.entity.AgriculturalCycleFarmStateEntity
import com.isivoltpro.maginaolivo.data.local.entity.AgriculturalCycleOperationEntity
import com.isivoltpro.maginaolivo.data.local.entity.AgriculturalCycleRecollectionEntity
import com.isivoltpro.maginaolivo.data.local.entity.AgriculturalCycleSyncStateEntity
import com.isivoltpro.maginaolivo.data.local.model.OutboxStatus
import com.isivoltpro.maginaolivo.data.local.model.SyncEntityType
import com.isivoltpro.maginaolivo.data.local.model.SyncStatus
import com.isivoltpro.maginaolivo.data.repository.WorkspaceAuthorizationContract
import com.isivoltpro.maginaolivo.feature.account.AccountRepository
import com.isivoltpro.maginaolivo.feature.account.AuthSession
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.Json

/** Persisted per-farm transport state (`agricultural_cycle_sync_state.status`). */
internal enum class CycleSyncStatus { SYNCED, PENDING, WAITING_PARENTS, CONFLICT, NOT_AUTHORIZED, NEEDS_REVIEW }

internal enum class CycleSyncOutcome {
    NOTHING_TO_SEND, STAGED, SYNCED, CONFIRMED_LOCAL_PENDING, OUTCOME_UNKNOWN, WAITING_PARENTS,
    CONFLICT, NOT_AUTHORIZED, NEEDS_REVIEW, REJECTED, IMPORTED, ALREADY_CURRENT,
}

internal data class RemoteCycle(val id: UUID, val label: String, val startDate: LocalDate,
    val effectiveEndDate: LocalDate?, val closedAt: Instant?, val noRecollection: Boolean)
internal data class RemoteFarmCycles(val farmId: UUID, val workspaceId: UUID, val revision: Long,
    val cycles: List<RemoteCycle>, val recollections: Map<UUID, UUID>)

/** Server instants keep microseconds; the same truncated value is always sent for a closed cycle. */
private fun Instant.wire(): Instant = truncatedTo(ChronoUnit.MICROS)

/** Exact `p_state` of `put_own_farm_cycles_v1`: cycle headers and recollection links only. */
internal fun buildCycleState(workspaceId: UUID, cycles: List<AgriculturalCycleEntity>,
    links: List<AgriculturalCycleRecollectionEntity>): JsonObject = buildJsonObject {
    put("workspaceId", workspaceId.toString())
    put("cycles", buildJsonArray {
        cycles.sortedWith(compareBy({ it.startDate }, { it.id.toString() })).forEach { cycle ->
            add(buildJsonObject {
                put("id", cycle.id.toString())
                put("label", cycle.label)
                put("startDate", cycle.startDate.toString())
                put("effectiveEndDate", cycle.effectiveEndDate?.let { JsonPrimitive(it.toString()) } ?: JsonNull)
                put("closedAt", cycle.closedAt?.let { JsonPrimitive(it.wire().toString()) } ?: JsonNull)
                put("noRecollection", cycle.noRecollection)
            })
        }
    })
    put("recollections", buildJsonArray {
        links.sortedBy { it.campaignId.toString() }.forEach { link ->
            add(buildJsonObject { put("campaignId", link.campaignId.toString()); put("cycleId", link.cycleId.toString()) })
        }
    })
}

internal fun buildCyclePutBody(operationId: UUID, farmId: UUID, expectedRevision: Long, state: JsonObject): String =
    buildJsonObject {
        put("p_operation_id", operationId.toString())
        put("p_farm_id", farmId.toString())
        put("p_expected_revision", expectedRevision)
        put("p_state", state)
    }.toString()

/** Strict parser of `get_own_farm_cycles_v1`; anything unexpected is a protocol failure. */
internal fun parseRemoteFarmCycles(root: JsonObject, subject: UUID, farmId: UUID): RemoteFarmCycles? = runCatching {
    require(root.keys == setOf("contract_version","subject","farm_id","workspace_id","revision","cycles","recollections"))
    require(root.getValue("contract_version").jsonPrimitive.long == 1L)
    require(root.getValue("subject").jsonPrimitive.content == subject.toString())
    require(root.getValue("farm_id").jsonPrimitive.content == farmId.toString())
    val revision = root.getValue("revision").jsonPrimitive.longOrNull ?: error("revision")
    require(revision >= 0)
    val cycles = (root.getValue("cycles") as JsonArray).map { element ->
        val row = element as JsonObject
        require(row.keys == setOf("id","label","startDate","effectiveEndDate","closedAt","noRecollection"))
        val end = row.getValue("effectiveEndDate").jsonPrimitive.contentOrNull?.let(LocalDate::parse)
        val closed = row.getValue("closedAt").jsonPrimitive.contentOrNull?.let(Instant::parse)
        val label = row.getValue("label").jsonPrimitive.content
        val start = LocalDate.parse(row.getValue("startDate").jsonPrimitive.content)
        require(label.isNotBlank() && label.length <= 80 && (end == null) == (closed == null) && (end == null || !end.isBefore(start)))
        RemoteCycle(UUID.fromString(row.getValue("id").jsonPrimitive.content), label, start, end, closed,
            row.getValue("noRecollection").jsonPrimitive.booleanOrNull ?: error("noRecollection"))
    }
    require(cycles.map { it.id }.toSet().size == cycles.size && cycles.count { it.effectiveEndDate == null } <= 1)
    val links = (root.getValue("recollections") as JsonArray).associate { element ->
        val row = element as JsonObject
        require(row.keys == setOf("campaignId","cycleId"))
        UUID.fromString(row.getValue("campaignId").jsonPrimitive.content) to UUID.fromString(row.getValue("cycleId").jsonPrimitive.content)
    }
    require(links.values.all { id -> cycles.any { it.id == id } })
    require(revision > 0 || cycles.isEmpty())
    RemoteFarmCycles(farmId, UUID.fromString(root.getValue("workspace_id").jsonPrimitive.content), revision, cycles, links)
}.getOrNull()

/** Reuses the G2.4 transport boundary; this class opens no connection. */
internal class AgriculturalCycleRpcAdapter(private val transport: DeliveryRpcTransport) {
    internal companion object { val NAMES = setOf("put_own_farm_cycles_v1", "get_own_farm_cycles_v1") }

    suspend fun put(session: AuthSession, body: String, operationId: UUID, farmId: UUID, expected: Long): Long {
        val root = transport.callRpcObject("put_own_farm_cycles_v1", body, session)
        val version = root["remote_version"]?.jsonPrimitive?.longOrNull
        if (root["operation_id"]?.jsonPrimitive?.contentOrNull != operationId.toString() ||
            root["entity_id"]?.jsonPrimitive?.contentOrNull != farmId.toString() ||
            root["status"]?.jsonPrimitive?.contentOrNull != "APPLIED" ||
            root["payload_version"]?.jsonPrimitive?.longOrNull != 1L || version != expected + 1
        ) throw DeliveryRpcFailure(DeliveryRpcError.PROTOCOL)
        return version
    }

    suspend fun get(session: AuthSession, farmId: UUID): RemoteFarmCycles =
        parseRemoteFarmCycles(transport.callRpcObject("get_own_farm_cycles_v1", """{"p_farm_id":"$farmId"}""", session),
            session.userId, farmId) ?: throw DeliveryRpcFailure(DeliveryRpcError.PROTOCOL)
}

/**
 * CR-015 P4: sends and receives the agricultural cycles of one farm with a per-farm CAS revision.
 * Only the owner of the selected workspace syncs. A stale revision or a change that would rewrite
 * history becomes a visible CONFLICT; local cycles are never overwritten by the server, and the
 * server never overwrites local ones. Activity and expense links stay local (not synced records).
 */
internal class AgriculturalCycleSyncCoordinator(
    private val database: MaginaOlivoDatabase,
    private val account: AccountRepository,
    private val issuer: String,
    private val rpc: AgriculturalCycleRpcAdapter,
    private val now: () -> Instant = Instant::now,
    private val ids: () -> UUID = UUID::randomUUID,
) {
    private val dao get() = database.agriculturalCycleDao()

    /** Freezes the exact request before any network call. A body already pending is never replaced. */
    suspend fun stage(farmId: UUID): CycleSyncOutcome = guarded {
        account.withServerVerifiedIdentity(remote = { Unit }) { userId, _ ->
            database.withTransaction {
                val farm = database.farmDao().findById(farmId) ?: return@withTransaction CycleSyncOutcome.REJECTED
                if (!authorizedOwner(userId, farm.workspaceId)) return@withTransaction CycleSyncOutcome.NOT_AUTHORIZED
                val state = dao.syncState(farmId)
                if (state != null && state.accountId != userId) return@withTransaction CycleSyncOutcome.NOT_AUTHORIZED
                if (state?.pendingBody != null) return@withTransaction CycleSyncOutcome.STAGED
                if (state?.status == CycleSyncStatus.CONFLICT.name) return@withTransaction CycleSyncOutcome.CONFLICT
                val local = dao.revision(farmId) ?: 0L
                if (local == 0L || state?.syncedLocalRevision == local) return@withTransaction CycleSyncOutcome.NOTHING_TO_SEND
                val links = dao.recollectionLinks(farmId)
                val workspace = database.workspaceDao().findById(farm.workspaceId)
                val parentsReady = workspace?.metadata?.remoteVersion != null &&
                    farm.metadata.syncStatus == SyncStatus.SYNCED && farm.metadata.remoteVersion != null &&
                    links.all { link -> campaignConfirmed(link.campaignId, farmId) }
                if (!parentsReady) {
                    save(farmId, userId, state, CycleSyncStatus.WAITING_PARENTS, "PARENT_NOT_SYNCED")
                    return@withTransaction CycleSyncOutcome.WAITING_PARENTS
                }
                val expected = state?.remoteRevision ?: 0L
                val operation = ids()
                val body = buildCyclePutBody(operation, farmId, expected, buildCycleState(farm.workspaceId, dao.list(farmId), links))
                dao.setSyncState(AgriculturalCycleSyncStateEntity(farmId, userId, expected, state?.syncedLocalRevision ?: 0L,
                    operation, local, body, CycleSyncStatus.PENDING.name, null, now()))
                CycleSyncOutcome.STAGED
            }
        }
    }

    /** Sends the frozen body; a retry after a lost answer reuses the same operation id and body. */
    suspend fun dispatch(farmId: UUID, lease: DeliveryDispatchLease): CycleSyncOutcome = guarded {
        lease.requireOpen()
        val staged = dao.syncState(farmId) ?: return@guarded CycleSyncOutcome.NOTHING_TO_SEND
        val body = staged.pendingBody ?: return@guarded CycleSyncOutcome.NOTHING_TO_SEND
        val operation = staged.pendingOperationId ?: return@guarded CycleSyncOutcome.REJECTED
        val request = runCatching { Json.parseToJsonElement(body) as JsonObject }.getOrNull()
        val expected = request?.get("p_expected_revision")?.jsonPrimitive?.longOrNull
        if (expected != staged.remoteRevision || request["p_operation_id"]?.jsonPrimitive?.contentOrNull != operation.toString() ||
            request["p_farm_id"]?.jsonPrimitive?.contentOrNull != farmId.toString()) return@guarded CycleSyncOutcome.REJECTED
        val farm = database.farmDao().findById(farmId) ?: return@guarded CycleSyncOutcome.REJECTED
        lease.withActiveRequest {
            account.withServerVerifiedIdentity(remote = { session ->
                if (session.userId != staged.accountId || !authorizedOwner(session.userId, farm.workspaceId))
                    return@withServerVerifiedIdentity Remote.Unauthorized
                try { Remote.Ack(rpc.put(session, body, operation, farmId, expected)) }
                catch (failure: DeliveryRpcFailure) {
                    when (failure.error) {
                        DeliveryRpcError.CONFLICT -> Remote.Conflict(failure.reason ?: "CONFLICT")
                        DeliveryRpcError.FORBIDDEN -> Remote.Unauthorized
                        DeliveryRpcError.INVALID_PAYLOAD -> Remote.Invalid
                        else -> Remote.Unknown
                    }
                }
            }, commit = { userId, result ->
                if (result is Remote.Unauthorized || userId != staged.accountId)
                    return@withServerVerifiedIdentity CycleSyncOutcome.NOT_AUTHORIZED
                database.withTransaction {
                    val current = dao.syncState(farmId)
                    if (current?.pendingOperationId != operation || current.pendingBody != body)
                        return@withTransaction CycleSyncOutcome.REJECTED
                    when (result) {
                        is Remote.Ack -> {
                            val pendingLocal = current.pendingLocalRevision ?: return@withTransaction CycleSyncOutcome.REJECTED
                            val later = (dao.revision(farmId) ?: 0L) != pendingLocal
                            dao.setSyncState(current.copy(remoteRevision = result.version, syncedLocalRevision = pendingLocal,
                                pendingOperationId = null, pendingLocalRevision = null, pendingBody = null,
                                status = (if (later) CycleSyncStatus.PENDING else CycleSyncStatus.SYNCED).name,
                                reason = null, updatedAt = now()))
                            if (later) CycleSyncOutcome.CONFIRMED_LOCAL_PENDING else CycleSyncOutcome.SYNCED
                        }
                        // A rejected request committed nothing remotely: the frozen body can be dropped.
                        is Remote.Conflict -> {
                            val waiting = result.reason in setOf("PARENT_NOT_SYNCED", "RECOLLECTION_NOT_CLOSED")
                            dao.setSyncState(current.cleared(if (waiting) CycleSyncStatus.WAITING_PARENTS else CycleSyncStatus.CONFLICT, result.reason))
                            if (waiting) CycleSyncOutcome.WAITING_PARENTS else CycleSyncOutcome.CONFLICT
                        }
                        Remote.Invalid -> { dao.setSyncState(current.cleared(CycleSyncStatus.NEEDS_REVIEW, "INVALID_PAYLOAD")); CycleSyncOutcome.NEEDS_REVIEW }
                        Remote.Unknown -> { dao.setSyncState(current.copy(status = CycleSyncStatus.PENDING.name, reason = "OUTCOME_UNKNOWN", updatedAt = now())); CycleSyncOutcome.OUTCOME_UNKNOWN }
                        Remote.Unauthorized -> CycleSyncOutcome.NOT_AUTHORIZED
                    }
                }
            })
        }
    }

    /** Imports a newer server head only when this phone has no unsent cycle change for the farm. */
    suspend fun receive(farmId: UUID): CycleSyncOutcome = guarded {
        val farm = database.farmDao().findById(farmId) ?: return@guarded CycleSyncOutcome.REJECTED
        account.withServerVerifiedIdentity(remote = { session ->
            if (!authorizedOwner(session.userId, farm.workspaceId)) null
            else try { rpc.get(session, farmId) } catch (_: DeliveryRpcFailure) { null }
        }, commit = { userId, head ->
            if (head == null) return@withServerVerifiedIdentity CycleSyncOutcome.NOT_AUTHORIZED
            database.withTransaction { apply(userId, head) }
        })
    }

    private suspend fun apply(userId: UUID, head: RemoteFarmCycles): CycleSyncOutcome {
        val farm = database.farmDao().findById(head.farmId) ?: return CycleSyncOutcome.REJECTED
        val workspace = database.workspaceDao().findById(farm.workspaceId)
        if (farm.workspaceId != head.workspaceId || workspace?.ownerUserId != userId || !authorizedOwner(userId, farm.workspaceId))
            return CycleSyncOutcome.NOT_AUTHORIZED
        val state = dao.syncState(head.farmId)
        if (state != null && state.accountId != userId) return CycleSyncOutcome.NOT_AUTHORIZED
        val known = state?.remoteRevision ?: 0L
        if (head.revision == known) return CycleSyncOutcome.ALREADY_CURRENT
        if (head.revision < known) return CycleSyncOutcome.REJECTED
        val local = dao.revision(head.farmId) ?: 0L
        val clean = if (state == null) local == 0L else state.pendingBody == null && state.syncedLocalRevision == local
        if (!clean) return conflict(head.farmId, userId, state, "REMOTE_AND_LOCAL_CHANGED")
        // The server only grows history; anything else means two histories diverged and needs review.
        val remote = head.cycles.associateBy { it.id }
        val localCycles = dao.list(head.farmId)
        for (cycle in localCycles) {
            val other = remote[cycle.id] ?: return conflict(head.farmId, userId, state, "CYCLE_REMOVED")
            if (other.label != cycle.label || other.startDate != cycle.startDate) return conflict(head.farmId, userId, state, "CYCLE_CHANGED")
            if (cycle.effectiveEndDate != null && (other.effectiveEndDate != cycle.effectiveEndDate ||
                other.closedAt != cycle.closedAt?.wire() || other.noRecollection != cycle.noRecollection))
                return conflict(head.farmId, userId, state, "CLOSED_CYCLE_CHANGED")
        }
        val closed = localCycles.filter { it.effectiveEndDate != null }.map { it.id }.toSet()
        for (link in dao.recollectionLinks(head.farmId)) {
            val target = head.recollections[link.campaignId] ?: return conflict(head.farmId, userId, state, "LINK_REMOVED")
            if (link.cycleId in closed && target != link.cycleId) return conflict(head.farmId, userId, state, "RECOLLECTION_LEFT_CLOSED_CYCLE")
        }
        for (cycle in head.cycles) {
            val existing = dao.find(cycle.id)
            if (existing != null && existing.farmId != head.farmId) return conflict(head.farmId, userId, state, "CYCLE_ID_TAKEN")
        }
        for (campaign in head.recollections.keys) {
            val row = database.campaignDao().findById(campaign)
            if (row == null || row.farmId != head.farmId || row.metadata.deletedAt != null) {
                save(head.farmId, userId, state, CycleSyncStatus.WAITING_PARENTS, "RECOLLECTION_NOT_RECEIVED")
                return CycleSyncOutcome.WAITING_PARENTS
            }
        }
        for (cycle in head.cycles) {
            val existing = dao.find(cycle.id)
            if (existing == null) dao.insert(AgriculturalCycleEntity(cycle.id, farm.workspaceId, head.farmId, cycle.label,
                cycle.startDate, cycle.effectiveEndDate, cycle.closedAt, cycle.noRecollection))
            else if (existing.effectiveEndDate == null && cycle.effectiveEndDate != null)
                dao.update(existing.copy(effectiveEndDate = cycle.effectiveEndDate, closedAt = cycle.closedAt,
                    noRecollection = cycle.noRecollection, version = Math.addExact(existing.version, 1)))
        }
        head.recollections.forEach { (campaign, cycle) -> dao.link(AgriculturalCycleRecollectionEntity(campaign, cycle)) }
        val revised = Math.addExact(local, 1)
        // Traceability: the received revision is recorded like any other cycle operation.
        head.cycles.maxByOrNull { it.startDate }?.let { latest ->
            dao.insert(AgriculturalCycleOperationEntity(ids(), latest.id, userId, sha256("RECEIVE|${head.farmId}|${head.revision}"),
                now(), "Recibido del servidor: revisión ${head.revision}"))
        }
        dao.setRevision(AgriculturalCycleFarmStateEntity(head.farmId, revised))
        dao.setSyncState(AgriculturalCycleSyncStateEntity(head.farmId, userId, head.revision, revised, null, null, null,
            CycleSyncStatus.SYNCED.name, null, now()))
        return CycleSyncOutcome.IMPORTED
    }

    private suspend fun conflict(farmId: UUID, userId: UUID, state: AgriculturalCycleSyncStateEntity?, reason: String): CycleSyncOutcome {
        save(farmId, userId, state, CycleSyncStatus.CONFLICT, reason)
        return CycleSyncOutcome.CONFLICT
    }

    /** Status only: never touches the frozen pending body or the confirmed revisions. */
    private suspend fun save(farmId: UUID, userId: UUID, state: AgriculturalCycleSyncStateEntity?, status: CycleSyncStatus, reason: String) {
        dao.setSyncState(state?.copy(status = status.name, reason = reason, updatedAt = now())
            ?: AgriculturalCycleSyncStateEntity(farmId, userId, 0, 0, null, null, null, status.name, reason, now()))
    }

    private fun AgriculturalCycleSyncStateEntity.cleared(status: CycleSyncStatus, reason: String) = copy(
        pendingOperationId = null, pendingLocalRevision = null, pendingBody = null,
        status = status.name, reason = reason, updatedAt = now())

    private suspend fun campaignConfirmed(campaignId: UUID, farmId: UUID): Boolean {
        val campaign = database.campaignDao().findById(campaignId) ?: return false
        return campaign.farmId == farmId && campaign.metadata.deletedAt == null &&
            campaign.metadata.syncStatus == SyncStatus.SYNCED && campaign.metadata.remoteVersion != null &&
            database.syncOutboxDao().listForEntity(SyncEntityType.CAMPAIGN, campaignId).all { it.status == OutboxStatus.CONFIRMED }
    }

    /** Owner-only, as the G2.6 receive: the selected workspace with a valid cached OWNER write grant. */
    private suspend fun authorizedOwner(userId: UUID, workspaceId: UUID): Boolean {
        val selected = database.workspaceSelectionDao().find(issuer, userId.toString())?.workspaceId
        if (selected != workspaceId) return false
        val cached = database.workspaceAuthorizationCacheDao().find(issuer, userId.toString()) ?: return false
        val grant = runCatching { WorkspaceAuthorizationContract.parse(cached.contractJson) }.getOrNull() ?: return false
        return grant.validFor(issuer, userId, now()) &&
            grant.available(now()).any { it.id == workspaceId && it.role == "OWNER" && "write" in it.permissions }
    }

    private suspend fun guarded(block: suspend () -> CycleSyncOutcome): CycleSyncOutcome = try { block() }
        catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { CycleSyncOutcome.NOT_AUTHORIZED }

    private fun sha256(text: String) = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private sealed interface Remote {
        data class Ack(val version: Long) : Remote
        data class Conflict(val reason: String) : Remote
        data object Invalid : Remote
        data object Unknown : Remote
        data object Unauthorized : Remote
    }
}
