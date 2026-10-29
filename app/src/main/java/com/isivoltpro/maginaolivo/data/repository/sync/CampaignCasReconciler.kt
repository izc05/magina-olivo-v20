package com.isivoltpro.maginaolivo.data.repository.sync

import androidx.room.withTransaction
import com.isivoltpro.maginaolivo.data.local.MaginaOlivoDatabase
import com.isivoltpro.maginaolivo.data.local.entity.CampaignReconciliationEntity
import com.isivoltpro.maginaolivo.data.local.entity.SyncOutboxEntity
import com.isivoltpro.maginaolivo.data.local.model.OutboxOperation
import com.isivoltpro.maginaolivo.data.local.model.OutboxStatus
import com.isivoltpro.maginaolivo.data.local.model.SyncEntityType
import com.isivoltpro.maginaolivo.data.local.model.SyncStatus
import com.isivoltpro.maginaolivo.data.repository.WorkspaceAuthorizationContract
import com.isivoltpro.maginaolivo.data.repository.buildCampaignCasEnvelope
import com.isivoltpro.maginaolivo.data.repository.parseCampaignCasEnvelope
import com.isivoltpro.maginaolivo.feature.account.AccountRepository
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

internal enum class CampaignReconcileOutcome {
    CAPTURED, RESOLVED, OUTCOME_UNKNOWN, RECEIPT_CONFLICT, NOT_AUTHORIZED, STALE, REJECTED
}

internal data class CampaignConflictComparison(
    val record: CampaignReconciliationEntity,
    val changedFields: Set<String>,
)

/** Explicit QA-only decision path. No scheduler, UI or real transport constructs this. */
internal class CampaignCasReconciler(
    private val database: MaginaOlivoDatabase,
    private val account: AccountRepository,
    private val issuer: String,
    private val rpc: CampaignCasRpcAdapter,
    private val now: () -> Instant = Instant::now,
) {
    suspend fun capture(operationId: UUID): CampaignReconcileOutcome = try {
        account.withServerVerifiedIdentity(remote = { session ->
            val op = database.syncOutboxDao().findById(operationId)
                ?: return@withServerVerifiedIdentity null
            if (op.accountId != session.userId.toString() || op.entityType != SyncEntityType.CAMPAIGN ||
                op.operation != OutboxOperation.UPDATE || op.payloadJson == null) return@withServerVerifiedIdentity null
            val reason = classify(op) ?: return@withServerVerifiedIdentity null
            val campaign = database.campaignDao().findById(op.entityId) ?: return@withServerVerifiedIdentity null
            if (op.status == OutboxStatus.CONFIRMED && campaign.metadata.syncStatus != SyncStatus.CONFLICT)
                return@withServerVerifiedIdentity null
            if (!authorized(session.userId, campaign.workspaceId) ||
                campaign.farmId != op.farmId) return@withServerVerifiedIdentity null
            if (reason == "OUTCOME_UNKNOWN") {
                val receipt = rpc.receipt(session, operationId)
                return@withServerVerifiedIdentity CaptureResult.Unknown(receipt != null)
            }
            if (reason == "RECEIPT_CONFLICT") return@withServerVerifiedIdentity CaptureResult.BadReceipt
            val head = rpc.head(session, op.entityId)
            CaptureResult.Head(op, reason, head)
        }, commit = { userId, result ->
            when (result) {
                null -> CampaignReconcileOutcome.NOT_AUTHORIZED
                is CaptureResult.Unknown -> CampaignReconcileOutcome.OUTCOME_UNKNOWN // receipt must be recovered by CAS dispatch first
                CaptureResult.BadReceipt -> CampaignReconcileOutcome.RECEIPT_CONFLICT
                is CaptureResult.Head -> database.withTransaction {
                    val op = database.syncOutboxDao().findById(operationId)
                        ?: return@withTransaction CampaignReconcileOutcome.STALE
                    val campaign = database.campaignDao().findById(op.entityId)
                        ?: return@withTransaction CampaignReconcileOutcome.STALE
                    val head = result.head
                    val remote = head.campaign
                    if (op != result.op || classify(op) != result.reason ||
                        op.accountId != userId.toString() || !authorized(userId, campaign.workspaceId) ||
                        remote["workspaceId"]?.toString()?.trim('"') != campaign.workspaceId.toString() ||
                        remote["farmId"]?.toString()?.trim('"') != campaign.farmId.toString() ||
                        remote["id"]?.toString()?.trim('"') != campaign.id.toString() ||
                        head.issuer != "$issuer/auth/v1" || head.subject != userId ||
                        head.version <= 0 || head.version < (op.baseRemoteVersion ?: 0L)
                    ) return@withTransaction CampaignReconcileOutcome.NOT_AUTHORIZED
                    val local = buildCampaignCasEnvelope(campaign, issuer, head.version)
                    val existing = database.campaignReconciliationDao().find(op.id)
                    if (existing != null) return@withTransaction CampaignReconcileOutcome.STALE
                    database.campaignReconciliationDao().insert(CampaignReconciliationEntity(
                        op.id, issuer, userId.toString(), campaign.workspaceId, campaign.farmId,
                        campaign.id, result.reason, local, remote.toString(), head.version,
                        now().toEpochMilli(),
                    ))
                    CampaignReconcileOutcome.CAPTURED
                }
            }
        })
    } catch (cancel: CancellationException) { throw cancel
    } catch (_: Exception) { CampaignReconcileOutcome.NOT_AUTHORIZED }

    /** Only the signed-in owner with a fresh grant can inspect both protected revisions. */
    suspend fun comparison(operationId: UUID): CampaignConflictComparison? = try {
        account.withServerVerifiedIdentity(remote = { Unit }) { userId, _ ->
            val row = database.campaignReconciliationDao().find(operationId)
                ?: return@withServerVerifiedIdentity null
            if (row.issuer != issuer || row.accountId != userId.toString() ||
                !authorized(userId, row.workspaceId)) return@withServerVerifiedIdentity null
            val local = parseCampaignCasEnvelope(row.localPayloadJson)?.campaign ?: return@withServerVerifiedIdentity null
            val remote = Json.parseToJsonElement(row.remotePayloadJson) as? JsonObject
                ?: return@withServerVerifiedIdentity null
            CampaignConflictComparison(row, local.keys.filter { local[it] != remote[it] }.toSet())
        }
    } catch (cancel: CancellationException) { throw cancel
    } catch (_: Exception) { null }

    /** KEEP_LOCAL is the only implemented decision. Never silently copies remote data into Room. */
    suspend fun keepLocal(operationId: UUID, newOperationId: UUID): CampaignReconcileOutcome = try {
        account.withServerVerifiedIdentity(remote = { Unit }) { userId, _ -> database.withTransaction {
            val row = database.campaignReconciliationDao().find(operationId)
                ?: return@withTransaction CampaignReconcileOutcome.REJECTED
            val op = database.syncOutboxDao().findById(operationId)
                ?: return@withTransaction CampaignReconcileOutcome.STALE
            val campaign = database.campaignDao().findById(row.campaignId)
                ?: return@withTransaction CampaignReconcileOutcome.STALE
            if (row.issuer != issuer || row.accountId != userId.toString() ||
                !authorized(userId, row.workspaceId)) return@withTransaction CampaignReconcileOutcome.NOT_AUTHORIZED
            if (row.resolvedOperationId != null || database.syncOutboxDao().findById(newOperationId) != null ||
                campaign.workspaceId != row.workspaceId || campaign.farmId != row.farmId ||
                campaign.metadata.syncStatus != SyncStatus.CONFLICT ||
                classify(op) != row.reason || op.accountId != row.accountId ||
                buildCampaignCasEnvelope(campaign, issuer, row.remoteVersion) != row.localPayloadJson
            ) return@withTransaction CampaignReconcileOutcome.STALE
            val farm = database.farmDao().findById(campaign.farmId)
            if (farm?.workspaceId != campaign.workspaceId || farm.metadata.syncStatus != SyncStatus.SYNCED ||
                database.syncOutboxDao().listForEntity(SyncEntityType.FARM, farm.id).any { it.status != OutboxStatus.CONFIRMED } ||
                database.syncOutboxDao().listForEntity(SyncEntityType.CAMPAIGN, campaign.id)
                    .none { it.operation == OutboxOperation.CREATE && it.status == OutboxStatus.CONFIRMED }
            ) return@withTransaction CampaignReconcileOutcome.STALE
            if (database.campaignDao().listSnapshots(campaign.id).any { snapshot ->
                val parcel = database.parcelDao().findById(snapshot.parcelId)
                snapshot.metadata.syncStatus != SyncStatus.SYNCED || snapshot.metadata.deletedAt != null ||
                    parcel?.workspaceId != campaign.workspaceId || parcel.metadata.syncStatus != SyncStatus.SYNCED ||
                    database.syncOutboxDao().listForEntity(SyncEntityType.PARCEL, snapshot.parcelId)
                        .any { it.status != OutboxStatus.CONFIRMED }
            }) return@withTransaction CampaignReconcileOutcome.STALE
            val blockers = database.syncOutboxDao().listForEntity(SyncEntityType.CAMPAIGN, campaign.id)
                .filter { it.status != OutboxStatus.CONFIRMED && it.id != operationId &&
                    it.lastErrorCode?.startsWith("RESOLVED_") != true }
            if (blockers.any { it.status != OutboxStatus.BLOCKED || it.lastErrorCode != "NEEDS_REBASE" ||
                    it.attemptCount != 0 || (it.accountId != row.accountId &&
                    !(it.accountId == null && it.payloadJson == null)) })
                return@withTransaction CampaignReconcileOutcome.STALE
            for (blocked in blockers + listOf(op).filter { it.status == OutboxStatus.BLOCKED }) {
                val updated = if (blocked.accountId == null)
                    database.syncOutboxDao().resolveUnstagedCampaignBlock(blocked.id, now().toEpochMilli())
                else database.syncOutboxDao().resolveCampaignBlock(blocked.id, row.accountId,
                    "RESOLVED_${blocked.lastErrorCode}", now().toEpochMilli())
                if (updated != 1) throw StaleDecision()
            }
            database.syncOutboxDao().insert(SyncOutboxEntity(
                id = newOperationId, entityType = SyncEntityType.CAMPAIGN, entityId = campaign.id,
                operation = OutboxOperation.UPDATE, payloadVersion = 1,
                baseRemoteVersion = row.remoteVersion, status = OutboxStatus.PENDING,
                createdAt = now(), updatedAt = now(), accountId = row.accountId,
                payloadJson = row.localPayloadJson, farmId = row.farmId,
            ))
            if (database.campaignReconciliationDao().markResolved(operationId, row.accountId, newOperationId) != 1)
                throw StaleDecision()
            CampaignReconcileOutcome.RESOLVED
        } }
    } catch (cancel: CancellationException) { throw cancel
    } catch (_: StaleDecision) { CampaignReconcileOutcome.STALE
    } catch (_: Exception) { CampaignReconcileOutcome.REJECTED }

    private class StaleDecision : RuntimeException()

    private fun classify(op: SyncOutboxEntity): String? = when {
        op.status == OutboxStatus.FAILED && op.lastErrorCode == "OUTCOME_UNKNOWN" -> "OUTCOME_UNKNOWN"
        op.status == OutboxStatus.BLOCKED && op.lastErrorCode in setOf("CAS_CONFLICT", "NEEDS_REBASE", "RECEIPT_CONFLICT") -> op.lastErrorCode
        op.status == OutboxStatus.CONFIRMED -> "NEEDS_REBASE"
        else -> null
    }

    private suspend fun authorized(userId: UUID, workspaceId: UUID): Boolean {
        if (database.workspaceSelectionDao().find(issuer, userId.toString())?.workspaceId != workspaceId) return false
        val cache = database.workspaceAuthorizationCacheDao().find(issuer, userId.toString()) ?: return false
        val grant = runCatching { WorkspaceAuthorizationContract.parse(cache.contractJson) }.getOrNull() ?: return false
        return grant.validFor(issuer, userId, now()) &&
            grant.available(now()).any { it.id == workspaceId && "write" in it.permissions }
    }

    private sealed interface CaptureResult {
        data class Head(val op: SyncOutboxEntity, val reason: String, val head: CampaignRemoteHead) : CaptureResult
        data class Unknown(val receiptFound: Boolean) : CaptureResult
        data object BadReceipt : CaptureResult
    }
}
