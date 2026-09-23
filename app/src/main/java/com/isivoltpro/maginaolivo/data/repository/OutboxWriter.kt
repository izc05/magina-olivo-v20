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
 */
internal suspend fun MaginaOlivoDatabase.enqueueCollapsed(
    idGenerator: IdGenerator,
    entityType: SyncEntityType,
    entityId: UUID,
    requested: OutboxOperation,
    now: Instant,
) {
    val dao = syncOutboxDao()
    val existing = dao.listForEntity(entityType, entityId)
    val operation =
        if (existing.any { it.operation == OutboxOperation.CREATE } && requested != OutboxOperation.DELETE) {
            OutboxOperation.CREATE
        } else {
            requested
        }
    dao.deletePendingForEntity(entityType, entityId)
    dao.insert(
        SyncOutboxEntity(
            id = idGenerator.newId(),
            entityType = entityType,
            entityId = entityId,
            operation = operation,
            payloadVersion = 1,
            createdAt = now,
            updatedAt = now,
        ),
    )
}
