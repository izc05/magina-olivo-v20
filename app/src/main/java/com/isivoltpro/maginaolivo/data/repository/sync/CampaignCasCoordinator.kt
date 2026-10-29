package com.isivoltpro.maginaolivo.data.repository.sync

import androidx.room.withTransaction
import com.isivoltpro.maginaolivo.data.local.MaginaOlivoDatabase
import com.isivoltpro.maginaolivo.data.local.model.OutboxOperation
import com.isivoltpro.maginaolivo.data.local.model.OutboxStatus
import com.isivoltpro.maginaolivo.data.local.model.SyncEntityType
import com.isivoltpro.maginaolivo.data.local.model.SyncStatus
import com.isivoltpro.maginaolivo.data.repository.buildCampaignCasEnvelope
import com.isivoltpro.maginaolivo.data.repository.parseCampaignCasEnvelope
import com.isivoltpro.maginaolivo.data.repository.WorkspaceAuthorizationContract
import com.isivoltpro.maginaolivo.feature.account.AccountRepository
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CancellationException

internal enum class CampaignCasOutcome {
    STAGED, SYNCED, NEEDS_REBASE, CONFLICT, OUTCOME_UNKNOWN, NOT_AUTHORIZED, REJECTED
}

/** Explicit QA entry point. No scheduler or app composition root constructs this coordinator. */
internal class CampaignCasCoordinator(
    private val database: MaginaOlivoDatabase,
    private val account: AccountRepository,
    private val issuer: String,
    private val rpc: CampaignCasRpcAdapter,
    private val now: () -> Instant = Instant::now,
) {
    suspend fun stage(operationId: UUID): CampaignCasOutcome = try {
        account.withServerVerifiedIdentity(remote = { Unit }) { userId, _ ->
            database.withTransaction {
                val op = database.syncOutboxDao().findById(operationId)
                    ?: return@withTransaction CampaignCasOutcome.REJECTED
                val campaign = database.campaignDao().findById(op.entityId)
                    ?: return@withTransaction CampaignCasOutcome.REJECTED
                if (!authorizedWorkspace(userId, campaign.workspaceId))
                    return@withTransaction CampaignCasOutcome.NOT_AUTHORIZED
                val farm = database.farmDao().findById(campaign.farmId)
                val parentOps = database.syncOutboxDao().listForEntity(SyncEntityType.FARM, campaign.farmId)
                val campaignOps = database.syncOutboxDao().listForEntity(SyncEntityType.CAMPAIGN, campaign.id)
                if (op.entityType != SyncEntityType.CAMPAIGN || op.operation != OutboxOperation.UPDATE ||
                    op.status != OutboxStatus.PENDING || op.accountId != null || op.payloadJson != null ||
                    op.attemptCount != 0 || campaign.metadata.deletedAt != null ||
                    campaign.metadata.syncStatus == SyncStatus.CONFLICT ||
                    campaign.metadata.remoteVersion == null || campaign.metadata.remoteVersion < 1 ||
                    farm == null || farm.workspaceId != campaign.workspaceId ||
                    farm.metadata.syncStatus != SyncStatus.SYNCED ||
                    parentOps.any { it.status != OutboxStatus.CONFIRMED } ||
                    campaignOps.none { it.operation == OutboxOperation.CREATE && it.status == OutboxStatus.CONFIRMED } ||
                    campaignOps.any { it.status == OutboxStatus.BLOCKED &&
                        it.lastErrorCode?.startsWith("RESOLVED_") != true } ||
                    campaignOps.any { it.id != op.id && it.status != OutboxStatus.CONFIRMED &&
                        it.payloadJson != null && it.lastErrorCode?.startsWith("RESOLVED_") != true }
                ) return@withTransaction CampaignCasOutcome.REJECTED
                val snapshots = database.campaignDao().listSnapshots(campaign.id)
                if (snapshots.any { snapshot ->
                    val parcel = database.parcelDao().findById(snapshot.parcelId)
                    snapshot.metadata.syncStatus != SyncStatus.SYNCED || snapshot.metadata.deletedAt != null ||
                        parcel?.workspaceId != campaign.workspaceId || parcel.metadata.syncStatus != SyncStatus.SYNCED ||
                        database.syncOutboxDao().listForEntity(SyncEntityType.PARCEL, snapshot.parcelId)
                            .any { it.status != OutboxStatus.CONFIRMED }
                }) return@withTransaction CampaignCasOutcome.REJECTED
                val payload = buildCampaignCasEnvelope(campaign, issuer, campaign.metadata.remoteVersion)
                if (database.syncOutboxDao().stageCampaignCas(op.id, userId.toString(), campaign.farmId,
                        campaign.metadata.remoteVersion, payload, now().toEpochMilli()) != 1
                ) CampaignCasOutcome.REJECTED else CampaignCasOutcome.STAGED
            }
        }
    } catch (cancel: CancellationException) { throw cancel
    } catch (_: Exception) { CampaignCasOutcome.NOT_AUTHORIZED }

    suspend fun dispatch(operationId: UUID, lease: DeliveryDispatchLease): CampaignCasOutcome {
        lease.requireOpen()
        val op = database.syncOutboxDao().findById(operationId) ?: return CampaignCasOutcome.REJECTED
        val payload = op.payloadJson ?: return CampaignCasOutcome.REJECTED
        val snapshot = parseCampaignCasEnvelope(payload) ?: return CampaignCasOutcome.REJECTED
        if (op.entityType != SyncEntityType.CAMPAIGN || op.operation != OutboxOperation.UPDATE ||
            op.status !in setOf(OutboxStatus.PENDING, OutboxStatus.FAILED) ||
            op.accountId.isNullOrBlank() || op.farmId == null ||
            op.baseRemoteVersion != snapshot.expectedVersion || op.payloadVersion != 1 ||
            snapshot.issuer != issuer || snapshot.campaign["id"]?.toString()?.trim('"') != op.entityId.toString() ||
            snapshot.campaign["farmId"]?.toString()?.trim('"') != op.farmId.toString()
        ) return CampaignCasOutcome.REJECTED
        val campaign = database.campaignDao().findById(op.entityId) ?: return CampaignCasOutcome.REJECTED
        if (snapshot.campaign["workspaceId"]?.toString()?.trim('"') != campaign.workspaceId.toString()
        ) return CampaignCasOutcome.NOT_AUTHORIZED
        return try {
            lease.withActiveRequest {
                account.withServerVerifiedIdentity(remote = { session ->
                    if (session.userId.toString() != op.accountId ||
                        !authorizedWorkspace(session.userId, campaign.workspaceId)
                    ) return@withServerVerifiedIdentity RemoteResult.Unauthorized
                    try {
                        var receipt = rpc.receipt(session, operationId)
                        if (receipt == null) {
                            val ack = rpc.update(session, operationId, snapshot.expectedVersion, snapshot.campaign)
                            if (ack.campaignId != op.entityId) return@withServerVerifiedIdentity RemoteResult.Unknown
                            receipt = rpc.receipt(session, operationId)
                            if (receipt?.remoteVersion != ack.remoteVersion) return@withServerVerifiedIdentity RemoteResult.Unknown
                        }
                        RemoteResult.Receipt(receipt)
                    } catch (failure: DeliveryRpcFailure) {
                        when (failure.error) {
                            DeliveryRpcError.CONFLICT -> RemoteResult.Conflict
                            DeliveryRpcError.FORBIDDEN -> RemoteResult.Unauthorized
                            else -> RemoteResult.Unknown
                        }
                    }
                }, commit = { userId, result ->
                    if (userId.toString() != op.accountId ||
                        !authorizedWorkspace(userId, campaign.workspaceId)
                    ) CampaignCasOutcome.NOT_AUTHORIZED
                    else when (result) {
                        is RemoteResult.Receipt -> applyReceipt(op, snapshot, result.value, userId)
                        RemoteResult.Conflict -> quarantine(op, payload, "CAS_CONFLICT")
                        RemoteResult.Unknown -> unknown(op, payload)
                        RemoteResult.Unauthorized -> CampaignCasOutcome.NOT_AUTHORIZED
                    }
                })
            }
        } catch (cancel: CancellationException) { throw cancel
        } catch (_: Exception) { CampaignCasOutcome.NOT_AUTHORIZED }
    }

    private suspend fun applyReceipt(op: com.isivoltpro.maginaolivo.data.local.entity.SyncOutboxEntity,
        snapshot: com.isivoltpro.maginaolivo.data.repository.CampaignCasSnapshot,
        receipt: CampaignCasRemoteReceipt?, userId: UUID): CampaignCasOutcome = database.withTransaction {
        if (receipt == null || receipt.operationId != op.id || receipt.campaignId != op.entityId ||
            receipt.accountId != userId || receipt.entityType != "CAMPAIGN" || receipt.payloadVersion != 1 ||
            receipt.remoteVersion != snapshot.expectedVersion + 1 || receipt.payloadHash != snapshot.hash
        ) return@withTransaction quarantine(op, op.payloadJson!!, "RECEIPT_CONFLICT")
        val currentOp = database.syncOutboxDao().findById(op.id) ?: return@withTransaction CampaignCasOutcome.REJECTED
        val current = database.campaignDao().findById(op.entityId) ?: return@withTransaction CampaignCasOutcome.REJECTED
        if (currentOp.payloadJson != op.payloadJson || currentOp.accountId != userId.toString() ||
            currentOp.status !in setOf(OutboxStatus.PENDING, OutboxStatus.FAILED) ||
            current.workspaceId.toString() != snapshot.campaign.getValue("workspaceId").toString().trim('"')
        ) return@withTransaction CampaignCasOutcome.REJECTED
        val exact = current.metadata.version == snapshot.localVersion &&
            buildCampaignCasEnvelope(current, issuer, snapshot.expectedVersion) == op.payloadJson
        val siblings = database.syncOutboxDao().listForEntity(SyncEntityType.CAMPAIGN, op.entityId)
            .any { it.id != op.id && it.status != OutboxStatus.CONFIRMED &&
                !(it.status == OutboxStatus.BLOCKED && it.lastErrorCode?.startsWith("RESOLVED_") == true) }
        if (database.syncOutboxDao().markConfirmed(op.id, now().toEpochMilli()) != 1)
            return@withTransaction CampaignCasOutcome.REJECTED
        val remoteVersion = maxOf(current.metadata.remoteVersion ?: 0L, receipt.remoteVersion)
        val synced = exact && !siblings && (current.metadata.remoteVersion ?: 0L) <= receipt.remoteVersion
        if (!synced) database.syncOutboxDao().quarantineSiblings(
            SyncEntityType.CAMPAIGN, op.entityId, op.id, now().toEpochMilli())
        database.campaignDao().upsert(current.copy(metadata = current.metadata.copy(
            remoteVersion = remoteVersion, syncStatus = if (synced) SyncStatus.SYNCED else SyncStatus.CONFLICT,
            lastSyncedAt = now(),
        )))
        if (synced) CampaignCasOutcome.SYNCED else CampaignCasOutcome.NEEDS_REBASE
    }

    private suspend fun quarantine(op: com.isivoltpro.maginaolivo.data.local.entity.SyncOutboxEntity,
        payload: String, reason: String): CampaignCasOutcome = database.withTransaction {
        if (database.syncOutboxDao().quarantineCampaignCas(op.id, op.accountId!!, payload,
                reason, now().toEpochMilli()) != 1) return@withTransaction CampaignCasOutcome.REJECTED
        database.campaignDao().findById(op.entityId)?.let { current ->
            database.campaignDao().upsert(current.copy(metadata = current.metadata.copy(syncStatus = SyncStatus.CONFLICT)))
        }
        CampaignCasOutcome.CONFLICT
    }

    private suspend fun unknown(op: com.isivoltpro.maginaolivo.data.local.entity.SyncOutboxEntity,
        payload: String): CampaignCasOutcome {
        database.syncOutboxDao().markCampaignUnknown(op.id, op.accountId!!, payload, now().toEpochMilli())
        return CampaignCasOutcome.OUTCOME_UNKNOWN
    }

    /** Reads durable selection and current cached grant without reacquiring AccountRepository's lock. */
    private suspend fun authorizedWorkspace(userId: UUID, workspaceId: UUID): Boolean {
        val selected = database.workspaceSelectionDao().find(issuer, userId.toString())?.workspaceId
        if (selected != workspaceId) return false
        val cached = database.workspaceAuthorizationCacheDao().find(issuer, userId.toString()) ?: return false
        val grant = runCatching { WorkspaceAuthorizationContract.parse(cached.contractJson) }.getOrNull()
            ?: return false
        return grant.validFor(issuer, userId, now()) &&
            grant.available(now()).any { it.id == workspaceId && "write" in it.permissions }
    }

    private sealed interface RemoteResult {
        data class Receipt(val value: CampaignCasRemoteReceipt?) : RemoteResult
        data object Conflict : RemoteResult
        data object Unknown : RemoteResult
        data object Unauthorized : RemoteResult
    }
}
