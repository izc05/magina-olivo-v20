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

    // Stable UUID: reuse existing operation ID if pending, ensuring idempotent tracking
    val stableId = pendingExisting?.id ?: idGenerator.newId()

    val operation =
        if (existing.any { it.operation == OutboxOperation.CREATE } && requested != OutboxOperation.DELETE) {
            OutboxOperation.CREATE
        } else {
            requested
        }

    // Preserve original payload or use new one if updated
    val effectivePayload = payloadJson ?: pendingExisting?.payloadJson
    val effectiveAccount = accountId ?: pendingExisting?.accountId
    val effectiveFarm = farmId ?: pendingExisting?.farmId
    val effectiveBaseVersion = baseRemoteVersion ?: pendingExisting?.baseRemoteVersion

    dao.deletePendingForEntity(entityType, entityId)
    dao.insert(
        SyncOutboxEntity(
            id = stableId,
            entityType = entityType,
            entityId = entityId,
            operation = operation,
            payloadVersion = 1,
            baseRemoteVersion = effectiveBaseVersion,
            createdAt = pendingExisting?.createdAt ?: now,
            updatedAt = now,
            accountId = effectiveAccount,
            payloadJson = effectivePayload,
            farmId = effectiveFarm,
        ),
    )
}
