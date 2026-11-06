package com.isivoltpro.maginaolivo.data.repository.sync

import androidx.room.withTransaction
import com.isivoltpro.maginaolivo.data.local.MaginaOlivoDatabase
import com.isivoltpro.maginaolivo.data.local.entity.FarmEntity
import com.isivoltpro.maginaolivo.data.local.entity.FarmParcelMembershipEntity
import com.isivoltpro.maginaolivo.data.local.entity.LocalMetadata
import com.isivoltpro.maginaolivo.data.local.entity.ParcelEntity
import com.isivoltpro.maginaolivo.data.local.entity.WorkspaceEntity
import com.isivoltpro.maginaolivo.data.local.model.FarmStatus
import com.isivoltpro.maginaolivo.data.local.model.OutboxStatus
import com.isivoltpro.maginaolivo.data.local.model.RecordStatus
import com.isivoltpro.maginaolivo.data.local.model.SyncEntityType
import com.isivoltpro.maginaolivo.data.local.model.SyncStatus
import com.isivoltpro.maginaolivo.data.repository.WorkspaceAuthorizationContract
import com.isivoltpro.maginaolivo.feature.account.AccountRepository
import com.isivoltpro.maginaolivo.feature.account.AuthFailure
import com.isivoltpro.maginaolivo.feature.account.AuthSession
import java.time.Instant
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

/** One byte bound shared with `get_own_workspace_parents_v2` (SQL) and the HTTPS transport (#810 D2). */
internal const val PARENT_GRAPH_V2_MAX_BYTES = 8 * 1024 * 1024

/** The graph is larger than contract v2 allows (rows or bytes): visible, never clipped, nothing written. */
internal class ParentGraphTooLarge : Exception("parent graph v2 too large")

/**
 * G2.6 2c-2 receiver over `get_own_workspace_parents_v2` (plan G2.6-PARCEL-RECEIVE-2C, §3).
 *
 * Explicit QA entry point; no app composition root or scheduler constructs this class. The v1
 * receiver stays as it is. Differences:
 * - ARCHIVED parcels and really archived farms arrive with their `archivedAt`, stored as `deletedAt`.
 * - A clean local tombstone accepts a newer remote state (RESTORE); a pending one never does.
 * - Guard 1: each parcel and **all** its memberships are decided before anything of it is written,
 *   then applied as one unit. One conflicting membership leaves the parcel and all its periods intact.
 * - Local changes are never overwritten: PENDING, FAILED/OUTCOME_UNKNOWN, BLOCKED or CONFLICT rows
 *   and the outbox stay as they are; a newer remote state is reported as `remote_ahead`.
 */
internal class ParentReceiveV2Coordinator(
    private val database: MaginaOlivoDatabase,
    private val account: AccountRepository,
    private val issuer: String,
    private val transport: DeliveryRpcTransport,
    private val now: () -> Instant = Instant::now,
) {
    suspend fun receive(workspaceId: UUID): ParentReceiveReport = try {
        account.withServerVerifiedIdentity(remote = { read(it, workspaceId) }) { actor, graph ->
            database.withTransaction { commit(actor, workspaceId, graph) }
        }
    } catch (cancel: CancellationException) { throw cancel
    } catch (_: ParentGraphTooLarge) { ParentReceiveReport(ParentReceiveOutcome.TOO_LARGE)
    } catch (failure: AuthFailure) {
        ParentReceiveReport(if (failure.kind == AuthFailure.Kind.NETWORK) ParentReceiveOutcome.OFFLINE
            else ParentReceiveOutcome.NOT_AUTHORIZED)
    } catch (failure: DeliveryRpcFailure) {
        ParentReceiveReport(when {
            failure.reason == RESPONSE_TOO_LARGE -> ParentReceiveOutcome.TOO_LARGE
            failure.error == DeliveryRpcError.NETWORK -> ParentReceiveOutcome.OFFLINE
            failure.error == DeliveryRpcError.FORBIDDEN -> ParentReceiveOutcome.NOT_AUTHORIZED
            else -> ParentReceiveOutcome.REJECTED
        })
    } catch (_: Exception) { ParentReceiveReport(ParentReceiveOutcome.REJECTED) }

    private suspend fun read(session: AuthSession, workspaceId: UUID): ParentGraph {
        val response = transport.rpc(RPC, """{"p_workspace_id":"$workspaceId"}""", session.accessToken)
        if (response.status == 401 || response.status == 403)
            throw DeliveryRpcFailure(DeliveryRpcError.FORBIDDEN, response.status)
        if (response.status !in 200..299) {
            // 54000 from the contract: rows or bytes over the limit. Only the fixed prefix is matched.
            if (response.body.length <= 4096 && response.body.contains("too large for contract v2"))
                throw ParentGraphTooLarge()
            throw DeliveryRpcFailure(DeliveryRpcError.PROTOCOL, response.status)
        }
        return parseParentGraphV2(response.body, workspaceId)
    }

    private suspend fun commit(actor: UUID, workspaceId: UUID, graph: ParentGraph): ParentReceiveReport {
        val at = now()
        // Identity and current read grant, checked again inside the commit window.
        if (graph.issuer != "$issuer/auth/v1" || graph.subject != actor)
            return ParentReceiveReport(ParentReceiveOutcome.NOT_AUTHORIZED)
        val cache = database.workspaceAuthorizationCacheDao().find(issuer, actor.toString())
        val grant = cache?.let { runCatching { WorkspaceAuthorizationContract.parse(it.contractJson) }.getOrNull() }
        if (grant == null || !grant.validFor(issuer, actor, at) ||
            grant.available(at).none { it.id == workspaceId && "read" in it.permissions })
            return ParentReceiveReport(ParentReceiveOutcome.NOT_AUTHORIZED)

        val conflicts = mutableListOf<ParentConflict>()
        var inserted = 0
        var updated = 0
        var unchanged = 0
        fun tally(decision: Decision) = when (decision) {
            Decision.Insert -> inserted++
            Decision.Update -> updated++
            Decision.Same -> unchanged++
            is Decision.Conflict -> Unit
        }
        suspend fun outboxOpen(type: SyncEntityType, id: UUID) =
            database.syncOutboxDao().listForEntity(type, id).any { it.status != OutboxStatus.CONFIRMED }
        fun synced(meta: LocalMetadata) = meta.copy(lastSyncedAt = at)

        // WORKSPACE: same rules as v1, including its tombstone rule (workspace deletion is out of 2c).
        val incomingWorkspace = graph.workspace
        val localWorkspace = database.workspaceDao().findById(workspaceId)
        val workspaceDecision = if (localWorkspace?.metadata?.deletedAt != null) Decision.Conflict("local_tombstone")
            else decide(localWorkspace?.metadata, localWorkspace?.id, workspaceId, incomingWorkspace.metadata,
                outboxOpen(SyncEntityType.WORKSPACE, workspaceId)) { sameWorkspace(localWorkspace!!, incomingWorkspace) }
        when (workspaceDecision) {
            Decision.Insert -> database.workspaceDao().upsert(incomingWorkspace.copy(metadata = synced(incomingWorkspace.metadata)))
            // Room never adopts the account as owner of a local row: the principal stays as stored.
            Decision.Update -> database.workspaceDao().upsert(incomingWorkspace.copy(
                ownerUserId = localWorkspace!!.ownerUserId, metadata = synced(incomingWorkspace.metadata)))
            is Decision.Conflict -> conflicts += ParentConflict(ParentKind.WORKSPACE, workspaceId, workspaceDecision.reason)
            Decision.Same -> Unit
        }
        tally(workspaceDecision)

        // FARMS: archivedAt is the farm's deletedAt; a clean local archive accepts a remote restore.
        for (farm in graph.farms) {
            val local = database.farmDao().findById(farm.id)
            val decision = decide(local?.metadata, local?.workspaceId, workspaceId, farm.metadata,
                outboxOpen(SyncEntityType.FARM, farm.id)) { sameFarm(local!!, farm) }
            when (decision) {
                Decision.Insert, Decision.Update ->
                    database.farmDao().upsert(farm.copy(coverDocumentId = local?.coverDocumentId, metadata = synced(farm.metadata)))
                is Decision.Conflict -> conflicts += ParentConflict(ParentKind.FARM, farm.id, decision.reason)
                Decision.Same -> Unit
            }
            tally(decision)
        }
        val incomingFarms = graph.farms.map { it.id }.toSet()
        for (local in database.farmDao().listIncludingDeleted(workspaceId)) {
            if (local.id !in incomingFarms && local.metadata.remoteVersion != null)
                conflicts += ParentConflict(ParentKind.FARM, local.id, "remote_missing")
        }

        // PARCELS: one unit per parcel (guard 1). Decide everything, then write all or nothing of it.
        val membershipsByParcel = graph.memberships.groupBy { it.parcelId }
        for (parcel in graph.parcels) {
            val local = database.parcelDao().findById(parcel.id)
            val parcelDecision = decide(local?.metadata, local?.workspaceId, workspaceId, parcel.metadata,
                outboxOpen(SyncEntityType.PARCEL, parcel.id)) { sameParcel(local!!, parcel) }
            if (parcelDecision is Decision.Conflict) {
                conflicts += ParentConflict(ParentKind.PARCEL, parcel.id, parcelDecision.reason)
                continue
            }
            val incoming = membershipsByParcel[parcel.id].orEmpty()
            val existing = database.parcelDao().listMemberships(parcel.id)
            val unitConflicts = mutableListOf<ParentConflict>()
            val plan = incoming.map { membership ->
                val farm = database.farmDao().findById(membership.farmId)
                val current = existing.find { it.id == membership.id }
                val decision = when {
                    farm?.workspaceId != workspaceId -> Decision.Conflict("parent_unavailable")
                    current == null && membershipIdTaken(membership.id) -> Decision.Conflict("foreign_parcel")
                    current != null && (current.farmId != membership.farmId || current.parcelId != membership.parcelId ||
                        current.validFrom != membership.validFrom) -> Decision.Conflict("immutable_field_changed")
                    existing.any { it.id != membership.id && it.farmId == membership.farmId &&
                        it.validFrom == membership.validFrom } -> Decision.Conflict("duplicate_period")
                    current == null -> Decision.Insert
                    current.workspaceId != workspaceId -> Decision.Conflict("foreign_workspace")
                    current.metadata.syncStatus != SyncStatus.SYNCED || current.metadata.remoteVersion == null ->
                        Decision.Conflict("local_pending")
                    membership.metadata.version < current.metadata.remoteVersion!! -> Decision.Conflict("stale_remote")
                    membership.metadata.version == current.metadata.remoteVersion -> when {
                        current.validUntil != membership.validUntil -> Decision.Conflict("divergent_same_version")
                        current.metadata.updatedAt == membership.metadata.updatedAt &&
                            current.metadata.createdAt == membership.metadata.createdAt -> Decision.Same
                        else -> Decision.Update
                    }
                    else -> Decision.Update
                }
                if (decision is Decision.Conflict) unitConflicts += ParentConflict(ParentKind.MEMBERSHIP, membership.id, decision.reason)
                membership to decision
            }
            // A local period the server does not have is never deleted or closed silently.
            val incomingIds = incoming.map { it.id }.toSet()
            for (row in existing) if (row.id !in incomingIds && row.metadata.deletedAt == null) {
                unitConflicts += ParentConflict(ParentKind.MEMBERSHIP, row.id,
                    if (row.metadata.remoteVersion != null) "remote_missing" else "local_pending")
            }
            if (unitConflicts.isNotEmpty()) {
                conflicts += unitConflicts
                continue
            }
            if (parcelDecision == Decision.Insert || parcelDecision == Decision.Update)
                database.parcelDao().upsert(parcel.copy(metadata = synced(parcel.metadata)))
            tally(parcelDecision)
            for ((membership, decision) in plan) {
                if (decision == Decision.Insert || decision == Decision.Update)
                    database.parcelDao().upsertMembership(membership.copy(metadata = synced(membership.metadata)))
                tally(decision)
            }
        }
        val incomingParcels = graph.parcels.map { it.id }.toSet()
        for (id in syncedParcelIds(workspaceId)) if (id !in incomingParcels)
            conflicts += ParentConflict(ParentKind.PARCEL, id, "remote_missing")

        val outcome = when {
            conflicts.isNotEmpty() -> ParentReceiveOutcome.CONFLICT
            inserted + updated == 0 -> ParentReceiveOutcome.ALREADY_CURRENT
            else -> ParentReceiveOutcome.IMPORTED
        }
        return ParentReceiveReport(outcome, inserted, updated, unchanged, conflicts)
    }

    private fun membershipIdTaken(id: UUID): Boolean =
        database.openHelper.writableDatabase.query("SELECT 1 FROM farm_parcel_memberships WHERE id = ?",
            arrayOf<Any?>(id.toString())).use { it.moveToFirst() }

    private fun syncedParcelIds(workspaceId: UUID): List<UUID> =
        database.openHelper.writableDatabase.query(
            "SELECT id FROM parcels WHERE workspace_id = ? AND remote_version IS NOT NULL", arrayOf<Any?>(workspaceId.toString()),
        ).use { cursor -> buildList { while (cursor.moveToNext()) add(UUID.fromString(cursor.getString(0))) } }

    private sealed interface Decision {
        data object Insert : Decision
        data object Update : Decision
        data object Same : Decision
        data class Conflict(val reason: String) : Decision
    }

    /** §3.1: "clean" = no open outbox, SYNCED and version == remoteVersion. A clean tombstone is not a conflict. */
    private fun decide(
        local: LocalMetadata?,
        localWorkspace: UUID?,
        workspaceId: UUID,
        incoming: LocalMetadata,
        outboxOpen: Boolean,
        same: () -> Boolean,
    ): Decision {
        if (local == null) return Decision.Insert
        if (localWorkspace != workspaceId) return Decision.Conflict("foreign_workspace")
        if (local.syncStatus == SyncStatus.CONFLICT) return Decision.Conflict("local_conflict")
        val remote = local.remoteVersion
        if (outboxOpen || local.syncStatus != SyncStatus.SYNCED || remote == null || local.version != remote)
            return Decision.Conflict(if (remote != null && incoming.version > remote) "remote_ahead" else "local_pending")
        return when {
            incoming.version < remote -> Decision.Conflict("stale_remote")
            incoming.version == remote -> when {
                !same() -> Decision.Conflict("divergent_same_version")
                local.updatedAt == incoming.updatedAt && local.createdAt == incoming.createdAt -> Decision.Same
                else -> Decision.Update
            }
            else -> Decision.Update
        }
    }

    private fun sameWorkspace(a: WorkspaceEntity, b: WorkspaceEntity) = a.name == b.name &&
        a.countryCode == b.countryCode && a.timezone == b.timezone && a.locale == b.locale && a.currency == b.currency
    private fun sameFarm(a: FarmEntity, b: FarmEntity) = a.name == b.name && a.description == b.description &&
        a.municipality == b.municipality && a.province == b.province && a.notes == b.notes && a.status == b.status &&
        a.metadata.deletedAt == b.metadata.deletedAt
    private fun sameParcel(a: ParcelEntity, b: ParcelEntity) =
        a.copy(metadata = b.metadata) == b && a.metadata.deletedAt == b.metadata.deletedAt

    internal companion object {
        const val RPC = "get_own_workspace_parents_v2"
        const val RESPONSE_TOO_LARGE = "RESPONSE_TOO_LARGE"
    }
}

/**
 * Validates the whole v2 graph before Room is touched: exact shape, limits, `archivedAt` against
 * status for farms and parcels, the #805 parcel invariant, and every membership inside the graph.
 * Instants are truncated to Room's millisecond precision.
 */
internal fun parseParentGraphV2(body: String, workspaceId: UUID): ParentGraph {
    if (body.length > PARENT_GRAPH_V2_MAX_BYTES || utf8Length(body) > PARENT_GRAPH_V2_MAX_BYTES) throw ParentGraphTooLarge()
    val root = Json.parseToJsonElement(body) as JsonObject
    require(root.keys == setOf("contract_version", "issuer", "subject", "workspace", "farms", "parcels", "memberships"))
    require(root.getValue("contract_version").jsonPrimitive.long == 2L)
    val farmsJson = root.getValue("farms") as JsonArray
    val parcelsJson = root.getValue("parcels") as JsonArray
    val membershipsJson = root.getValue("memberships") as JsonArray
    if (farmsJson.size > 100 || parcelsJson.size > 1000 || membershipsJson.size > 5000) throw ParentGraphTooLarge()

    val w = root.getValue("workspace") as JsonObject
    require(w.keys == setOf("id", "name", "ownerUserId", "countryCode", "timezone", "locale", "currency",
        "createdAt", "updatedAt", "version"))
    val workspace = WorkspaceEntity(w.uuidV2("id"), w.textV2("name"), w.uuidV2("ownerUserId"), w.textV2("countryCode"),
        w.textV2("timezone"), w.textV2("locale"), w.textV2("currency"), w.metadataV2(null))
    require(workspace.id == workspaceId)

    val farms = farmsJson.map { raw ->
        val f = raw as JsonObject
        require(f.keys == setOf("id", "workspaceId", "name", "description", "municipality", "province", "notes",
            "status", "archivedAt", "createdAt", "updatedAt", "version"))
        val status = FarmStatus.valueOf(f.textV2("status"))
        require(status == FarmStatus.ACTIVE || status == FarmStatus.ARCHIVED)
        val archivedAt = f.optionalV2("archivedAt")?.let(::instantV2)
        require((status == FarmStatus.ARCHIVED) == (archivedAt != null))
        FarmEntity(f.uuidV2("id"), f.uuidV2("workspaceId"), f.textV2("name"), f.optionalV2("description"),
            f.optionalV2("municipality"), f.optionalV2("province"), null, f.optionalV2("notes"), status, f.metadataV2(archivedAt))
    }
    val parcels = parcelsJson.map { raw ->
        val p = raw as JsonObject
        require(p.keys == setOf("id", "workspaceId", "displayName", "cadastralReference", "cadastralPolygon",
            "cadastralParcel", "municipality", "province", "source", "geometryGeoJson", "sourceProvider",
            "sourceImportedAt", "cadastralAreaM2", "managedAreaM2", "notes", "status", "oliveTreeCount", "variety",
            "irrigationSystem", "irrigationNetwork", "irrigationSector", "irrigationDays", "archivedAt",
            "createdAt", "updatedAt", "version"))
        val status = RecordStatus.valueOf(p.textV2("status"))
        require(status == RecordStatus.ACTIVE || status == RecordStatus.ARCHIVED)
        val archivedAt = p.optionalV2("archivedAt")?.let(::instantV2)
        require((status == RecordStatus.ARCHIVED) == (archivedAt != null))
        ParcelEntity(p.uuidV2("id"), p.uuidV2("workspaceId"), p.textV2("displayName"), p.optionalV2("cadastralReference"),
            p.optionalV2("cadastralPolygon"), p.optionalV2("cadastralParcel"), p.optionalV2("municipality"),
            p.optionalV2("province"), p.textV2("source"), p.optionalV2("geometryGeoJson"), p.optionalV2("sourceProvider"),
            p.optionalV2("sourceImportedAt")?.let(::instantV2), p.optionalV2("cadastralAreaM2")?.toDouble(),
            p.optionalV2("managedAreaM2")?.toDouble(), p.optionalV2("notes"), status,
            p.metadataV2(archivedAt), p.optionalV2("oliveTreeCount")?.toInt(), p.optionalV2("variety"),
            p.optionalV2("irrigationSystem"), p.optionalV2("irrigationNetwork"), p.optionalV2("irrigationSector"),
            p.optionalV2("irrigationDays"))
    }
    val memberships = membershipsJson.map { raw ->
        val m = raw as JsonObject
        require(m.keys == setOf("id", "workspaceId", "farmId", "parcelId", "validFrom", "validUntil",
            "createdAt", "updatedAt", "version"))
        FarmParcelMembershipEntity(m.uuidV2("id"), m.uuidV2("workspaceId"), m.uuidV2("farmId"), m.uuidV2("parcelId"),
            instantV2(m.textV2("validFrom")), m.optionalV2("validUntil")?.let(::instantV2), m.metadataV2(null))
    }
    require(farms.map { it.id }.distinct().size == farms.size && parcels.map { it.id }.distinct().size == parcels.size &&
        memberships.map { it.id }.distinct().size == memberships.size)
    require(farms.all { it.workspaceId == workspaceId && it.name.isNotBlank() } &&
        parcels.all { it.workspaceId == workspaceId && it.displayName.isNotBlank() } &&
        memberships.all { it.workspaceId == workspaceId })
    val farmIds = farms.map { it.id }.toSet()
    val parcelIds = parcels.map { it.id }.toSet()
    require(memberships.all { it.farmId in farmIds && it.parcelId in parcelIds &&
        (it.validUntil == null || it.validUntil.isAfter(it.validFrom)) })
    require(memberships.map { Triple(it.farmId, it.parcelId, it.validFrom) }.distinct().size == memberships.size)
    // The #805 invariant, as the contract enforces it: ACTIVE has one open period, ARCHIVED none.
    val open = memberships.filter { it.validUntil == null }.groupingBy { it.parcelId }.eachCount()
    require(parcels.all { (open[it.id] ?: 0) == if (it.status == RecordStatus.ACTIVE) 1 else 0 })
    return ParentGraph(root.textV2("issuer"), root.uuidV2("subject"), workspace, farms, parcels, memberships)
}

/** UTF-8 byte length without allocating a copy of an up-to-8-MiB body. */
internal fun utf8Length(text: String): Int {
    var bytes = 0
    var i = 0
    while (i < text.length) {
        val c = text[i]
        bytes += when {
            c.code < 0x80 -> 1
            c.code < 0x800 -> 2
            Character.isHighSurrogate(c) && i + 1 < text.length && Character.isLowSurrogate(text[i + 1]) -> { i++; 4 }
            else -> 3
        }
        i++
    }
    return bytes
}

private fun instantV2(value: String): Instant = OffsetDateTime.parse(value).toInstant().truncatedTo(ChronoUnit.MILLIS)
private fun JsonObject.textV2(key: String) = getValue(key).jsonPrimitive.also { require(it.isString) }.content
private fun JsonObject.optionalV2(key: String) = getValue(key).takeUnless { it == JsonNull }?.jsonPrimitive?.content
private fun JsonObject.uuidV2(key: String) = UUID.fromString(textV2(key))
private fun JsonObject.metadataV2(deletedAt: Instant?): LocalMetadata {
    val version = getValue("version").jsonPrimitive.long.also { require(it > 0) }
    val created = instantV2(textV2("createdAt"))
    val updated = instantV2(textV2("updatedAt"))
    require(!created.isAfter(updated))
    return LocalMetadata(created, updated, deletedAt = deletedAt, version = version, syncStatus = SyncStatus.SYNCED,
        remoteVersion = version)
}
