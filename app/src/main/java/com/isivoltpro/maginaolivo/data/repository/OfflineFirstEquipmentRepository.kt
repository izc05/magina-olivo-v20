package com.isivoltpro.maginaolivo.data.repository

import androidx.room.withTransaction
import com.isivoltpro.maginaolivo.core.common.AppError
import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.core.dispatchers.AppDispatchers
import com.isivoltpro.maginaolivo.core.id.IdGenerator
import com.isivoltpro.maginaolivo.core.time.AppClock
import com.isivoltpro.maginaolivo.data.local.MaginaOlivoDatabase
import com.isivoltpro.maginaolivo.data.local.entity.HarvestEquipmentEntity
import com.isivoltpro.maginaolivo.data.local.entity.LocalMetadata
import com.isivoltpro.maginaolivo.data.local.model.CampaignStatus
import com.isivoltpro.maginaolivo.data.local.model.OutboxOperation
import com.isivoltpro.maginaolivo.data.local.model.SyncEntityType
import com.isivoltpro.maginaolivo.data.local.model.SyncStatus
import com.isivoltpro.maginaolivo.domain.equipment.EquipmentDraftLine
import com.isivoltpro.maginaolivo.domain.equipment.EquipmentLine
import com.isivoltpro.maginaolivo.domain.equipment.EquipmentRepository
import com.isivoltpro.maginaolivo.domain.equipment.EquipmentRules
import com.isivoltpro.maginaolivo.domain.equipment.EquipmentType
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

private class EquipmentInvalid(val field: String, val code: String) : RuntimeException("$field:$code")

private class EquipmentConflict(val code: String) : RuntimeException(code)

/**
 * Phase 19E — offline-first Jornada equipment. The sheet is saved in one transaction; each
 * line has its own outbox intent. A registered Machine is only referenced, never created.
 */
class OfflineFirstEquipmentRepository(
    private val database: MaginaOlivoDatabase,
    private val clock: AppClock,
    private val idGenerator: IdGenerator,
    private val dispatchers: AppDispatchers,
) : EquipmentRepository {
    override fun observeForHarvest(harvestId: UUID): Flow<List<EquipmentLine>> =
        database.equipmentDao().observeForHarvest(harvestId).map { rows -> rows.map { it.toDomain() } }.flowOn(dispatchers.io)

    override fun observeForCampaign(campaignId: UUID): Flow<List<EquipmentLine>> =
        database.equipmentDao().observeForCampaign(campaignId).map { rows -> rows.map { it.toDomain() } }.flowOn(dispatchers.io)

    override suspend fun replaceForHarvest(harvestId: UUID, lines: List<EquipmentDraftLine>): AppResult<Unit> {
        EquipmentRules.validate(lines)?.let { return AppResult.Failure(AppError.Validation(it.field, it.code)) }
        return withContext(dispatchers.io) {
            try {
                database.withTransaction { replace(harvestId, lines) }
                AppResult.Success(Unit)
            } catch (error: EquipmentInvalid) {
                AppResult.Failure(AppError.Validation(error.field, error.code))
            } catch (error: EquipmentConflict) {
                AppResult.Failure(AppError.Conflict(error.code))
            } catch (error: Throwable) {
                AppResult.Failure(AppError.Storage("replace_equipment", error))
            }
        }
    }

    private suspend fun replace(harvestId: UUID, lines: List<EquipmentDraftLine>) {
        val harvest = database.harvestDao().findById(harvestId)?.takeIf { it.metadata.deletedAt == null }
            ?: throw EquipmentInvalid("harvestId", "not_found")
        val campaign = harvest.campaignId?.let { database.campaignDao().findById(it) }
        if (campaign == null || campaign.status !in RUNNING) throw EquipmentConflict("closed_campaign")
        val now = clock.nowInstant()
        val current = database.equipmentDao().listForHarvest(harvestId).associateBy { keyOf(it) }
        val wanted = lines.associateBy { EquipmentRules.key(it) }
        val writes = mutableListOf<Pair<HarvestEquipmentEntity, OutboxOperation>>()

        current.forEach { (key, row) ->
            if (key !in wanted) writes += row.copy(metadata = row.metadata.next(now).copy(deletedAt = now)) to OutboxOperation.DELETE
        }
        wanted.forEach { (key, line) ->
            val machineName = line.machineId?.let { id ->
                val machine = database.machineDao().findById(id)
                if (machine == null || machine.metadata.deletedAt != null || machine.workspaceId != harvest.workspaceId) {
                    throw EquipmentInvalid("machineId", "not_found")
                }
                machine.name
            }
            val label = machineName ?: line.label?.trim()?.takeIf { line.type == EquipmentType.OTHER }
            val existing = current[key]
            when {
                existing == null -> writes += HarvestEquipmentEntity(
                    id = idGenerator.newId(),
                    workspaceId = harvest.workspaceId,
                    harvestId = harvestId,
                    type = line.type.name,
                    label = label,
                    quantity = line.quantity,
                    machineId = line.machineId,
                    metadata = LocalMetadata(now, now, syncStatus = SyncStatus.PENDING),
                ) to OutboxOperation.CREATE
                existing.quantity != line.quantity || existing.type != line.type.name ->
                    writes += existing.copy(quantity = line.quantity, type = line.type.name, metadata = existing.metadata.next(now)) to
                        OutboxOperation.UPDATE
            }
        }
        if (writes.isEmpty()) return
        database.equipmentDao().upsert(writes.map { it.first })
        writes.forEach { (row, operation) ->
            database.enqueueCollapsed(idGenerator, SyncEntityType.HARVEST_EQUIPMENT, row.id, operation, now)
        }
    }

    private fun keyOf(row: HarvestEquipmentEntity): String =
        row.machineId?.let { "machine:$it" }
            ?: if (row.type == EquipmentType.OTHER.name) "other:${row.label.orEmpty().trim().lowercase()}" else "type:${row.type}"

    private fun HarvestEquipmentEntity.toDomain() = EquipmentLine(
        id = id,
        harvestId = harvestId,
        type = EquipmentType.entries.firstOrNull { it.name == type } ?: EquipmentType.OTHER,
        label = label,
        quantity = quantity,
        machineId = machineId,
        version = metadata.version,
    )

    private fun LocalMetadata.next(now: Instant) =
        copy(updatedAt = now, version = version + 1, syncStatus = SyncStatus.PENDING)

    private companion object {
        val RUNNING = setOf(CampaignStatus.ACTIVE, CampaignStatus.HARVEST)
    }
}
