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
import com.isivoltpro.maginaolivo.data.repository.DayCostLedger.Companion.toDomain
import com.isivoltpro.maginaolivo.data.local.model.CampaignStatus
import com.isivoltpro.maginaolivo.data.local.model.OutboxOperation
import com.isivoltpro.maginaolivo.data.local.model.SyncEntityType
import com.isivoltpro.maginaolivo.data.local.model.SyncStatus
import com.isivoltpro.maginaolivo.domain.equipment.EquipmentDraftLine
import com.isivoltpro.maginaolivo.domain.equipment.EquipmentLine
import com.isivoltpro.maginaolivo.domain.equipment.EquipmentPriceSnapshot
import com.isivoltpro.maginaolivo.domain.equipment.EquipmentRepository
import com.isivoltpro.maginaolivo.domain.equipment.EquipmentRules
import com.isivoltpro.maginaolivo.domain.equipment.EquipmentType
import com.isivoltpro.maginaolivo.domain.expense.ExpenseOrigin
import com.isivoltpro.maginaolivo.domain.expense.ExpenseStatus
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
    private val costs = DayCostLedger(database, idGenerator)

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
            } catch (error: LabourFinanceInvalid) {
                AppResult.Failure(AppError.Validation(error.field, error.code))
            } catch (error: ArithmeticException) {
                AppResult.Failure(AppError.Validation("appliedPrice", "overflow"))
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
        val rates = harvest.farmId?.let { database.recollectionRatesDao().findForFarm(it) }?.toDomain()
        val posted = database.expenseDao().listForHarvest(harvestId).filter {
            it.origin == ExpenseOrigin.DAY_EQUIPMENT.name && it.status == ExpenseStatus.POSTED.name
        }
        val existingCurrencies = current.values.mapNotNull { it.appliedCurrency }.distinct()
        if (posted.size > 1 || existingCurrencies.size > 1 ||
            (posted.isNotEmpty() && existingCurrencies.any { it != posted.single().currency })) {
            throw EquipmentInvalid("currency", "ambiguous_historical_currency")
        }
        val historicalCurrency = posted.singleOrNull()?.currency ?: existingCurrencies.singleOrNull()
        val resolved = wanted.mapValues { (key, line) ->
            line.appliedPrice ?: current[key]?.priceSnapshot() ?: rates?.takeIf { line.captureUsualPriceWhenMissing }?.let { usual ->
                usual.equipmentDayMinor[line.type]
                    ?.takeIf { key !in current && (historicalCurrency == null || historicalCurrency == usual.currency) }
                    ?.let { EquipmentPriceSnapshot(it, usual.currency, harvest.harvestDate) }
            }
        }
        if (current.any { (key, row) -> key in wanted && row.priceSnapshot() == null && resolved[key] == null } &&
            wanted.any { (key, _) -> key !in current && resolved[key] != null }) {
            throw EquipmentInvalid("appliedPrice", "confirm_missing_prices")
        }
        // #449 (audit 04-10, case B): while a price is still missing, a posted cost must never
        // outlive a change to the priced lines (one removed, its quantity or price changed, or a new
        // priced one added) — it would no longer be even the known subtotal. Adding an unpriced line alone, or confirming
        // a missing price, keeps the posted amount; legacy incomplete days keep their ledger.
        val stillUnpriced = wanted.keys.any { resolved[it] == null }
        val pricedChanged = current.any { (key, row) ->
            val price = row.priceSnapshot()
            price != null && (key !in wanted || wanted.getValue(key).quantity != row.quantity || resolved[key] != price)
        } || wanted.keys.any { it !in current && resolved[it] != null }
        if (posted.isNotEmpty() && stillUnpriced && pricedChanged) {
            throw EquipmentInvalid("appliedPrice", "confirm_before_recompose")
        }
        val currencies = resolved.values.mapNotNull { it?.currency }.distinct()
        if (currencies.size > 1 || (historicalCurrency != null && currencies.any { it != historicalCurrency })) {
            throw EquipmentInvalid("currency", "currency_mismatch")
        }
        // Validate the known subtotal even while another legacy line has no confirmed price.
        // No partial amount is posted; this only prevents unrepresentable snapshots being saved.
        resolved.entries.fold(0L) { total, (key, snapshot) ->
            if (snapshot == null) total else Math.addExact(total,
                Math.multiplyExact(wanted.getValue(key).quantity.toLong(), snapshot.unitPriceMinor))
        }

        current.forEach { (key, row) ->
            if (key !in wanted) writes += row.copy(metadata = row.metadata.next(now).copy(deletedAt = now)) to OutboxOperation.DELETE
        }
        wanted.forEach { (key, line) ->
            val existing = current[key]
            val machineName = line.machineId?.let { id ->
                if (existing?.machineId == id) {
                    // #446/#575/#444: once a Jornada named this Machine, later rename/archive of
                    // the live catalogue never refreshes its historical label.
                    existing.label
                } else {
                    val machine = database.machineDao().findById(id)
                    if (machine == null || machine.metadata.deletedAt != null ||
                        machine.workspaceId != harvest.workspaceId
                    ) {
                        throw EquipmentInvalid("machineId", "not_found")
                    }
                    if (machine.status != "ACTIVE") throw EquipmentInvalid("machineId", "archived_machine")
                    machine.name
                }
            }
            val label = machineName ?: line.label?.trim()?.takeIf { line.type == EquipmentType.OTHER }
            val price = resolved[key]
            when {
                existing == null -> writes += HarvestEquipmentEntity(
                    id = idGenerator.newId(),
                    workspaceId = harvest.workspaceId,
                    harvestId = harvestId,
                    type = line.type.name,
                    label = label,
                    quantity = line.quantity,
                    machineId = line.machineId,
                    appliedPriceMinor = price?.unitPriceMinor,
                    appliedCurrency = price?.currency,
                    appliedPriceDate = price?.priceDate,
                    metadata = LocalMetadata(now, now, syncStatus = SyncStatus.PENDING),
                ) to OutboxOperation.CREATE
                existing.quantity != line.quantity || existing.type != line.type.name || existing.priceSnapshot() != price ->
                    writes += existing.copy(quantity = line.quantity, type = line.type.name,
                        appliedPriceMinor = price?.unitPriceMinor, appliedCurrency = price?.currency,
                        appliedPriceDate = price?.priceDate, metadata = existing.metadata.next(now)) to
                        OutboxOperation.UPDATE
            }
        }
        if (writes.isEmpty()) return
        database.equipmentDao().upsert(writes.map { it.first })
        writes.forEach { (row, operation) ->
            database.enqueueCollapsed(idGenerator, SyncEntityType.HARVEST_EQUIPMENT, row.id, operation, now)
        }
        costs.sync(harvestId, now)
        // #502: an automatic day left with nothing goes with its last machine.
        JornadaLedger(database, idGenerator).reconcileAutomatic(harvestId, now)
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
        appliedPrice = priceSnapshot(),
    )

    private fun HarvestEquipmentEntity.priceSnapshot(): EquipmentPriceSnapshot? =
        if (appliedPriceMinor == null || appliedCurrency == null || appliedPriceDate == null) null
        else EquipmentPriceSnapshot(appliedPriceMinor, appliedCurrency, appliedPriceDate)

    private fun LocalMetadata.next(now: Instant) =
        copy(updatedAt = now, version = version + 1, syncStatus = SyncStatus.PENDING)

    private companion object {
        val RUNNING = setOf(CampaignStatus.ACTIVE, CampaignStatus.HARVEST)
    }
}
