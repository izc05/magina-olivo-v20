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

internal enum class ParentReceiveOutcome { IMPORTED, ALREADY_CURRENT, CONFLICT, NOT_AUTHORIZED, OFFLINE, REJECTED }
internal enum class ParentKind { WORKSPACE, FARM, PARCEL, MEMBERSHIP }
internal data class ParentConflict(val kind: ParentKind, val id: UUID, val reason: String)

/** CONFLICT rows were left untouched; every other row of the same graph was applied atomically. */
internal data class ParentReceiveReport(
    val outcome: ParentReceiveOutcome,
    val inserted: Int = 0,
    val updated: Int = 0,
    val unchanged: Int = 0,
    val conflicts: List<ParentConflict> = emptyList(),
)

internal data class ParentGraph(
    val issuer: String,
    val subject: UUID,
    val workspace: WorkspaceEntity,
    val farms: List<FarmEntity>,
    val parcels: List<ParcelEntity>,
    val memberships: List<FarmParcelMembershipEntity>,
)

/**
 * G2.6 receiver for the WORKSPACE -> FARM -> PARCEL parents of one authorized workspace.
 *
 * Explicit QA entry point; no app composition root or scheduler constructs this class. It keeps
 * the remote UUIDs, never creates outbox rows or UUIDs, never overwrites a local pending revision
 * and never rewrites the local workspace owner. Selection is not required: a fresh device must
 * receive the workspace row before the selector can offer it.
 */
internal class ParentReceiveCoordinator(
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
    } catch (failure: AuthFailure) {
        ParentReceiveReport(if (failure.kind == AuthFailure.Kind.NETWORK) ParentReceiveOutcome.OFFLINE
            else ParentReceiveOutcome.NOT_AUTHORIZED)
    } catch (failure: DeliveryRpcFailure) {
        ParentReceiveReport(when (failure.error) {
            DeliveryRpcError.NETWORK -> ParentReceiveOutcome.OFFLINE
            DeliveryRpcError.FORBIDDEN -> ParentReceiveOutcome.NOT_AUTHORIZED
            else -> ParentReceiveOutcome.REJECTED
        })
    } catch (_: Exception) { ParentReceiveReport(ParentReceiveOutcome.REJECTED) }

    private suspend fun read(session: AuthSession, workspaceId: UUID): ParentGraph {
        val response = transport.rpc(RPC, """{"p_workspace_id":"$workspaceId"}""", session.accessToken)
        if (response.status == 401 || response.status == 403)
            throw DeliveryRpcFailure(DeliveryRpcError.FORBIDDEN, response.status)
        if (response.status !in 200..299) throw DeliveryRpcFailure(DeliveryRpcError.PROTOCOL, response.status)
        return parseParentGraph(response.body, workspaceId)
    }

    private suspend fun commit(actor: UUID, workspaceId: UUID, graph: ParentGraph): ParentReceiveReport {
        val at = now()
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
        fun count(kind: ParentKind, id: UUID, decision: Decision) {
            when (decision) {
                Decision.Insert -> inserted++
                Decision.Update -> updated++
                Decision.Same -> unchanged++
                is Decision.Conflict -> conflicts += ParentConflict(kind, id, decision.reason)
            }
        }
        suspend fun openOutbox(type: SyncEntityType, id: UUID) =
            database.syncOutboxDao().listForEntity(type, id).any { it.status != OutboxStatus.CONFIRMED }
        fun synced(meta: LocalMetadata) = meta.copy(lastSyncedAt = at)

        val incomingWorkspace = graph.workspace
        val localWorkspace = database.workspaceDao().findById(workspaceId)
        val workspaceDecision = decide(localWorkspace?.metadata, localWorkspace?.id, workspaceId,
            incomingWorkspace.metadata, openOutbox(SyncEntityType.WORKSPACE, workspaceId)) {
            sameWorkspace(localWorkspace!!, incomingWorkspace)
        }
        count(ParentKind.WORKSPACE, workspaceId, workspaceDecision)
        when (workspaceDecision) {
            // Room never adopts the account as owner of a local row: the principal stays as stored.
            Decision.Insert -> database.workspaceDao().upsert(incomingWorkspace.copy(metadata = synced(incomingWorkspace.metadata)))
            Decision.Update -> database.workspaceDao().upsert(incomingWorkspace.copy(
                ownerUserId = localWorkspace!!.ownerUserId, metadata = synced(incomingWorkspace.metadata)))
            else -> Unit
        }

        for (farm in graph.farms) {
            val local = database.farmDao().findById(farm.id)
            val decision = decide(local?.metadata, local?.workspaceId, workspaceId, farm.metadata,
                openOutbox(SyncEntityType.FARM, farm.id)) { sameFarm(local!!, farm) }
            val row = farm.copy(coverDocumentId = local?.coverDocumentId, metadata = synced(farm.metadata))
            if (decision == Decision.Insert || decision == Decision.Update) database.farmDao().upsert(row)
            count(ParentKind.FARM, farm.id, decision)
        }

        for (parcel in graph.parcels) {
            val local = database.parcelDao().findById(parcel.id)
            val decision = decide(local?.metadata, local?.workspaceId, workspaceId, parcel.metadata,
                openOutbox(SyncEntityType.PARCEL, parcel.id)) { sameParcel(local!!, parcel) }
            if (decision == Decision.Insert || decision == Decision.Update)
                database.parcelDao().upsert(parcel.copy(metadata = synced(parcel.metadata)))
            count(ParentKind.PARCEL, parcel.id, decision)
        }

        for (membership in graph.memberships) {
            val farm = database.farmDao().findById(membership.farmId)
            val parcel = database.parcelDao().findById(membership.parcelId)
            val existing = database.parcelDao().listMemberships(membership.parcelId)
            val local = existing.find { it.id == membership.id }
            val decision = when {
                farm?.workspaceId != workspaceId || parcel?.workspaceId != workspaceId ->
                    Decision.Conflict("parent_unavailable")
                local == null && membershipIdTaken(membership.id) -> Decision.Conflict("foreign_parcel")
                existing.any { it.id != membership.id && it.farmId == membership.farmId &&
                    it.validFrom == membership.validFrom } -> Decision.Conflict("duplicate_period")
                // Memberships travel with the PARCEL aggregate; an open PARCEL outbox owns them.
                else -> decide(local?.metadata, local?.workspaceId, workspaceId, membership.metadata,
                    openOutbox(SyncEntityType.PARCEL, membership.parcelId)) { sameMembership(local!!, membership) }
            }
            if (decision == Decision.Insert || decision == Decision.Update)
                database.parcelDao().upsertMembership(membership.copy(metadata = synced(membership.metadata)))
            count(ParentKind.MEMBERSHIP, membership.id, decision)
        }

        val outcome = when {
            conflicts.isNotEmpty() -> ParentReceiveOutcome.CONFLICT
            inserted + updated == 0 -> ParentReceiveOutcome.ALREADY_CURRENT
            else -> ParentReceiveOutcome.IMPORTED
        }
        return ParentReceiveReport(outcome, inserted, updated, unchanged, conflicts)
    }

    /** A membership UUID already stored for another parcel would be silently re-pointed by an upsert. */
    private fun membershipIdTaken(id: UUID): Boolean =
        database.openHelper.writableDatabase.query("SELECT 1 FROM farm_parcel_memberships WHERE id = ?",
            arrayOf<Any?>(id.toString())).use { it.moveToFirst() }

    private sealed interface Decision {
        data object Insert : Decision
        data object Update : Decision
        data object Same : Decision
        data class Conflict(val reason: String) : Decision
    }

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
        if (local.deletedAt != null) return Decision.Conflict("local_tombstone")
        val remote = local.remoteVersion
        if (outboxOpen || local.syncStatus != SyncStatus.SYNCED || remote == null || local.version != remote)
            return Decision.Conflict("local_pending")
        return when {
            incoming.version < remote -> Decision.Conflict("stale_remote")
            incoming.version == remote -> if (same() && local.updatedAt == incoming.updatedAt) Decision.Same
                else Decision.Conflict("divergent_same_version")
            else -> Decision.Update
        }
    }

    private fun sameWorkspace(a: WorkspaceEntity, b: WorkspaceEntity) = a.name == b.name &&
        a.countryCode == b.countryCode && a.timezone == b.timezone && a.locale == b.locale && a.currency == b.currency
    private fun sameFarm(a: FarmEntity, b: FarmEntity) = a.name == b.name && a.description == b.description &&
        a.municipality == b.municipality && a.province == b.province && a.notes == b.notes && a.status == b.status
    private fun sameParcel(a: ParcelEntity, b: ParcelEntity) =
        a.copy(metadata = b.metadata) == b
    private fun sameMembership(a: FarmParcelMembershipEntity, b: FarmParcelMembershipEntity) =
        a.farmId == b.farmId && a.parcelId == b.parcelId && a.validFrom == b.validFrom && a.validUntil == b.validUntil

    internal companion object {
        const val RPC = "get_own_workspace_parents_v1"
    }
}

/** Validates the whole graph before Room is touched. Instants are truncated to Room's millisecond precision. */
internal fun parseParentGraph(body: String, workspaceId: UUID): ParentGraph {
    require(body.length <= 1_048_576)
    val root = Json.parseToJsonElement(body) as JsonObject
    require(root.keys == setOf("contract_version", "issuer", "subject", "workspace", "farms", "parcels", "memberships"))
    require(root.getValue("contract_version").jsonPrimitive.long == 1L)
    val w = root.getValue("workspace") as JsonObject
    require(w.keys == setOf("id", "name", "ownerUserId", "countryCode", "timezone", "locale", "currency",
        "createdAt", "updatedAt", "version"))
    val workspace = WorkspaceEntity(w.uuid("id"), w.text("name"), w.uuid("ownerUserId"), w.text("countryCode"),
        w.text("timezone"), w.text("locale"), w.text("currency"), w.metadata())
    require(workspace.id == workspaceId)

    val farms = (root.getValue("farms") as JsonArray).map { raw ->
        val f = raw as JsonObject
        require(f.keys == setOf("id", "workspaceId", "name", "description", "municipality", "province", "notes",
            "status", "createdAt", "updatedAt", "version"))
        FarmEntity(f.uuid("id"), f.uuid("workspaceId"), f.text("name"), f.optional("description"),
            f.optional("municipality"), f.optional("province"), null, f.optional("notes"),
            FarmStatus.valueOf(f.text("status")), f.metadata())
    }
    val parcels = (root.getValue("parcels") as JsonArray).map { raw ->
        val p = raw as JsonObject
        require(p.keys == setOf("id", "workspaceId", "displayName", "cadastralReference", "cadastralPolygon",
            "cadastralParcel", "municipality", "province", "source", "geometryGeoJson", "sourceProvider",
            "sourceImportedAt", "cadastralAreaM2", "managedAreaM2", "notes", "status", "oliveTreeCount", "variety",
            "irrigationSystem", "irrigationNetwork", "irrigationSector", "irrigationDays",
            "createdAt", "updatedAt", "version"))
        ParcelEntity(p.uuid("id"), p.uuid("workspaceId"), p.text("displayName"), p.optional("cadastralReference"),
            p.optional("cadastralPolygon"), p.optional("cadastralParcel"), p.optional("municipality"),
            p.optional("province"), p.text("source"), p.optional("geometryGeoJson"), p.optional("sourceProvider"),
            p.optional("sourceImportedAt")?.let(::instant), p.optional("cadastralAreaM2")?.toDouble(),
            p.optional("managedAreaM2")?.toDouble(), p.optional("notes"), RecordStatus.valueOf(p.text("status")),
            p.metadata(), p.optional("oliveTreeCount")?.toInt(), p.optional("variety"),
            p.optional("irrigationSystem"), p.optional("irrigationNetwork"), p.optional("irrigationSector"),
            p.optional("irrigationDays"))
    }
    val memberships = (root.getValue("memberships") as JsonArray).map { raw ->
        val m = raw as JsonObject
        require(m.keys == setOf("id", "workspaceId", "farmId", "parcelId", "validFrom", "validUntil",
            "createdAt", "updatedAt", "version"))
        FarmParcelMembershipEntity(m.uuid("id"), m.uuid("workspaceId"), m.uuid("farmId"), m.uuid("parcelId"),
            instant(m.text("validFrom")), m.optional("validUntil")?.let(::instant), m.metadata())
    }
    require(farms.size <= 100 && parcels.size <= 500 && memberships.size <= 2000)
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
    return ParentGraph(root.text("issuer"), root.uuid("subject"), workspace, farms, parcels, memberships)
}

private fun instant(value: String): Instant = OffsetDateTime.parse(value).toInstant().truncatedTo(ChronoUnit.MILLIS)
private fun JsonObject.text(key: String) = getValue(key).jsonPrimitive.also { require(it.isString) }.content
private fun JsonObject.optional(key: String) = getValue(key).takeUnless { it == JsonNull }?.jsonPrimitive?.content
private fun JsonObject.uuid(key: String) = UUID.fromString(text(key))
private fun JsonObject.metadata(): LocalMetadata {
    val version = getValue("version").jsonPrimitive.long.also { require(it > 0) }
    val created = instant(text("createdAt"))
    val updated = instant(text("updatedAt"))
    require(!created.isAfter(updated))
    return LocalMetadata(created, updated, version = version, syncStatus = SyncStatus.SYNCED, remoteVersion = version)
}
