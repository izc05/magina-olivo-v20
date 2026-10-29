package com.isivoltpro.maginaolivo.data.repository.sync

import androidx.room.withTransaction
import com.isivoltpro.maginaolivo.data.local.MaginaOlivoDatabase
import com.isivoltpro.maginaolivo.data.local.entity.CampaignEntity
import com.isivoltpro.maginaolivo.data.local.entity.CampaignParcelSnapshotEntity
import com.isivoltpro.maginaolivo.data.local.entity.LocalMetadata
import com.isivoltpro.maginaolivo.data.local.model.CampaignStatus
import com.isivoltpro.maginaolivo.data.local.model.OutboxStatus
import com.isivoltpro.maginaolivo.data.local.model.SyncEntityType
import com.isivoltpro.maginaolivo.data.local.model.SyncStatus
import com.isivoltpro.maginaolivo.data.repository.WorkspaceAuthorizationContract
import com.isivoltpro.maginaolivo.feature.account.AccountRepository
import com.isivoltpro.maginaolivo.feature.account.AuthSession
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

internal enum class CampaignReceiveOutcome { IMPORTED, UPDATED, ALREADY_CURRENT, CONFLICT, NOT_AUTHORIZED, PARENTS_MISSING }

/** Explicit QA entry point; no app composition root or scheduler constructs this class. */
internal class CampaignReceiveCoordinator(
    private val database: MaginaOlivoDatabase,
    private val account: AccountRepository,
    private val issuer: String,
    private val transport: DeliveryRpcTransport,
    private val now: () -> Instant = Instant::now,
) {
    suspend fun receive(id: UUID): CampaignReceiveOutcome = try {
        account.withServerVerifiedIdentity(remote = { read(it, id) }) { actor, incoming ->
            database.withTransaction {
                val at = now()
                val campaign = incoming.campaign
                if (incoming.issuer != "$issuer/auth/v1" || incoming.subject != actor || campaign.id != id)
                    return@withTransaction CampaignReceiveOutcome.NOT_AUTHORIZED
                val selection = database.workspaceSelectionDao().find(issuer, actor.toString())
                val cache = database.workspaceAuthorizationCacheDao().find(issuer, actor.toString())
                val grant = cache?.let { runCatching { WorkspaceAuthorizationContract.parse(it.contractJson) }.getOrNull() }
                if (selection?.workspaceId != campaign.workspaceId || grant == null ||
                    !grant.validFor(issuer, actor, at) ||
                    grant.available(at).none { it.id == campaign.workspaceId && "read" in it.permissions })
                    return@withTransaction CampaignReceiveOutcome.NOT_AUTHORIZED
                val workspace = database.workspaceDao().findById(campaign.workspaceId)
                val farm = database.farmDao().findById(campaign.farmId)
                if (workspace == null || workspace.metadata.deletedAt != null ||
                    farm == null || farm.workspaceId != campaign.workspaceId || farm.metadata.deletedAt != null ||
                    farm.metadata.syncStatus != SyncStatus.SYNCED ||
                    database.syncOutboxDao().listForEntity(SyncEntityType.FARM, farm.id)
                        .any { it.status != OutboxStatus.CONFIRMED })
                    return@withTransaction CampaignReceiveOutcome.PARENTS_MISSING
                for (snapshot in incoming.snapshots) {
                    val parcel = database.parcelDao().findById(snapshot.parcelId)
                    if (parcel == null || parcel.workspaceId != campaign.workspaceId ||
                        parcel.metadata.deletedAt != null || parcel.metadata.syncStatus != SyncStatus.SYNCED ||
                        database.syncOutboxDao().listForEntity(SyncEntityType.PARCEL, parcel.id)
                            .any { it.status != OutboxStatus.CONFIRMED })
                        return@withTransaction CampaignReceiveOutcome.PARENTS_MISSING
                }
                if (database.syncOutboxDao().listForEntity(SyncEntityType.CAMPAIGN, id)
                        .any { it.status != OutboxStatus.CONFIRMED })
                    return@withTransaction CampaignReceiveOutcome.CONFLICT
                val old = database.campaignDao().findById(id)
                val oldSnapshots = database.campaignDao().listSnapshots(id)
                if (old != null) {
                    if (old.workspaceId != campaign.workspaceId || old.farmId != campaign.farmId ||
                        old.metadata.deletedAt != null || old.metadata.syncStatus != SyncStatus.SYNCED ||
                        old.metadata.remoteVersion == null ||
                        old.metadata.version != old.metadata.remoteVersion ||
                        oldSnapshots.any { it.metadata.syncStatus != SyncStatus.SYNCED || it.metadata.deletedAt != null } ||
                        !sameSnapshots(oldSnapshots, incoming.snapshots))
                        return@withTransaction CampaignReceiveOutcome.CONFLICT
                    val oldVersion = old.metadata.remoteVersion ?: return@withTransaction CampaignReceiveOutcome.CONFLICT
                    if (oldVersion >= campaign.metadata.version) {
                        if (oldVersion == campaign.metadata.version && sameHeader(old, campaign))
                            return@withTransaction CampaignReceiveOutcome.ALREADY_CURRENT
                        return@withTransaction CampaignReceiveOutcome.CONFLICT
                    }
                    database.campaignDao().upsert(campaign.copy(metadata = campaign.metadata.copy(lastSyncedAt = at)))
                    return@withTransaction CampaignReceiveOutcome.UPDATED
                }
                if (oldSnapshots.isNotEmpty()) return@withTransaction CampaignReceiveOutcome.CONFLICT
                database.campaignDao().upsert(campaign.copy(metadata = campaign.metadata.copy(lastSyncedAt = at)))
                database.campaignDao().upsertSnapshots(incoming.snapshots.map { it.copy(metadata =
                    LocalMetadata(at, at, version = 1, syncStatus = SyncStatus.SYNCED, lastSyncedAt = at)) })
                CampaignReceiveOutcome.IMPORTED
            }
        }
    } catch (cancel: CancellationException) { throw cancel
    } catch (_: Exception) { CampaignReceiveOutcome.NOT_AUTHORIZED }

    private suspend fun read(session: AuthSession, id: UUID): Incoming {
        val response = transport.rpc("get_own_campaign_receive_v1", """{"p_campaign_id":"$id"}""", session.accessToken)
        if (response.status !in 200..299) throw DeliveryRpcFailure(DeliveryRpcError.FORBIDDEN, response.status)
        require(response.body.length <= 1_048_576)
        val root = Json.parseToJsonElement(response.body) as JsonObject
        require(root.getValue("contract_version").jsonPrimitive.long == 1L)
        val row = root.getValue("campaign") as JsonObject
        require(row.keys == setOf("id","workspaceId","farmId","name","startDate","endDate","status","notes",
            "version","createdAt","updatedAt","snapshotFormat"))
        require(row.getValue("snapshotFormat").jsonPrimitive.long == 1L)
        val version = row.getValue("version").jsonPrimitive.long.also { require(it > 0) }
        val campaign = CampaignEntity(row.uuid("id"), row.uuid("workspaceId"), row.uuid("farmId"),
            row.string("name"), LocalDate.parse(row.string("startDate")), row.optional("endDate")?.let { LocalDate.parse(it) },
            CampaignStatus.valueOf(row.string("status")), row.optional("notes"),
            LocalMetadata(Instant.parse(row.string("createdAt")), Instant.parse(row.string("updatedAt")),
                version = version, syncStatus = SyncStatus.SYNCED, remoteVersion = version))
        val rows = root.getValue("snapshots") as JsonArray
        require(rows.size in 1..1000)
        val snapshots = rows.map { raw ->
            val item = raw as JsonObject
            require(item.keys == setOf("id","workspaceId","campaignId","parcelId","farmIdAtStart",
                "farmNameAtStart","parcelNameAtStart","managedAreaM2AtStart","cadastralReferenceAtStart",
                "geometryGeoJsonSnapshot"))
            CampaignParcelSnapshotEntity(item.uuid("id"), item.uuid("workspaceId"), item.uuid("campaignId"),
                item.uuid("parcelId"), item.uuid("farmIdAtStart"), item.string("farmNameAtStart"),
                item.string("parcelNameAtStart"), item.optional("managedAreaM2AtStart")?.toDouble(),
                item.optional("cadastralReferenceAtStart"), item.optional("geometryGeoJsonSnapshot"),
                LocalMetadata(Instant.EPOCH, Instant.EPOCH))
        }
        require(snapshots.map { it.id }.distinct().size == snapshots.size &&
            snapshots.map { it.parcelId }.distinct().size == snapshots.size &&
            snapshots.all { it.workspaceId == campaign.workspaceId && it.campaignId == campaign.id &&
                it.farmIdAtStart == campaign.farmId })
        return Incoming(root.string("issuer"), root.uuid("subject"), campaign, snapshots)
    }

    private data class Incoming(val issuer: String, val subject: UUID, val campaign: CampaignEntity,
        val snapshots: List<CampaignParcelSnapshotEntity>)
    private fun JsonObject.string(key: String) = getValue(key).jsonPrimitive.content
    private fun JsonObject.optional(key: String) = getValue(key).takeUnless { it == JsonNull }?.jsonPrimitive?.content
    private fun JsonObject.uuid(key: String) = UUID.fromString(string(key))
    private fun sameHeader(a: CampaignEntity, b: CampaignEntity) = a.name == b.name &&
        a.startDate == b.startDate && a.endDate == b.endDate && a.status == b.status && a.notes == b.notes
    private fun sameSnapshots(a: List<CampaignParcelSnapshotEntity>, b: List<CampaignParcelSnapshotEntity>): Boolean =
        a.size == b.size && a.associateBy { it.id }.let { old -> b.all { item ->
            val prior = old[item.id]
            prior != null && prior.workspaceId == item.workspaceId && prior.campaignId == item.campaignId &&
                prior.parcelId == item.parcelId && prior.farmIdAtStart == item.farmIdAtStart &&
                prior.farmNameAtStart == item.farmNameAtStart && prior.parcelNameAtStart == item.parcelNameAtStart &&
                prior.managedAreaM2AtStart == item.managedAreaM2AtStart &&
                prior.cadastralReferenceAtStart == item.cadastralReferenceAtStart &&
                prior.geometryGeoJsonSnapshot == item.geometryGeoJsonSnapshot
        } }
}
