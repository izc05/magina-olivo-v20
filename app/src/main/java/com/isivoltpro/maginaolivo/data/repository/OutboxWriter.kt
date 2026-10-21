package com.isivoltpro.maginaolivo.data.repository

import com.isivoltpro.maginaolivo.core.id.IdGenerator
import com.isivoltpro.maginaolivo.data.local.MaginaOlivoDatabase
import com.isivoltpro.maginaolivo.data.local.entity.SyncOutboxEntity
import com.isivoltpro.maginaolivo.data.local.model.OutboxOperation
import com.isivoltpro.maginaolivo.data.local.model.SyncEntityType
import java.time.Instant
import java.util.UUID

/**
 * One pending intent per aggregate. A record created and edited offline is still a single
 * CREATE; a delete replaces whatever was waiting. Must run inside the caller's transaction.
 *
 * G2.3 Hardening:
 * - Preserves stable operation UUIDs across retries and collapsed edits (never generates arbitrary new IDs if an operation is already pending/in-flight).
 * - Binds accountId and farmId when available, respecting G1b consent scopes.
 * - Stores immutable canonical payloadJson when provided.
 */
internal suspend fun MaginaOlivoDatabase.enqueueCollapsed(
    idGenerator: IdGenerator,
    entityType: SyncEntityType,
    entityId: UUID,
    requested: OutboxOperation,
    now: Instant,
    accountId: String? = null,
    payloadJson: String? = null,
    farmId: UUID? = null,
    baseRemoteVersion: Long? = null,
) {
    val dao = syncOutboxDao()
    val existing = dao.listForEntity(entityType, entityId)
    val pendingExisting = existing.firstOrNull { it.status == com.isivoltpro.maginaolivo.data.local.model.OutboxStatus.PENDING }

    val operation =
        if (existing.any { it.operation == OutboxOperation.CREATE } && requested != OutboxOperation.DELETE) {
            OutboxOperation.CREATE
        } else {
            requested
        }

    // G2.3 Hardening:
    // When an operation is pending and its canonical payload, bound account, and baseRemoteVersion
    // are identical, reuse the stable operation UUID for idempotent retries.
    // If payload, account, or baseRemoteVersion change, or if no pending operation exists,
    // allocate a brand-new operation UUID to prevent tampering with a previously prepared or in-flight intent.
    val isIdenticalPending = pendingExisting != null &&
        pendingExisting.operation == operation &&
        pendingExisting.payloadJson == payloadJson &&
        pendingExisting.accountId == accountId &&
        pendingExisting.baseRemoteVersion == baseRemoteVersion &&
        pendingExisting.farmId == farmId

    val operationId = if (isIdenticalPending) pendingExisting!!.id else idGenerator.newId()
    val createdAt = if (isIdenticalPending) pendingExisting!!.createdAt else now

    dao.deletePendingForEntity(entityType, entityId)
    dao.insert(
        SyncOutboxEntity(
            id = operationId,
            entityType = entityType,
            entityId = entityId,
            operation = operation,
            payloadVersion = 1,
            baseRemoteVersion = baseRemoteVersion,
            createdAt = createdAt,
            updatedAt = now,
            accountId = accountId,
            payloadJson = payloadJson,
            farmId = farmId,
        ),
    )
}
