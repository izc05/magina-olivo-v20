package com.isivoltpro.maginaolivo.data.repository

import com.isivoltpro.maginaolivo.core.id.IdGenerator
import com.isivoltpro.maginaolivo.data.local.MaginaOlivoDatabase
import com.isivoltpro.maginaolivo.data.local.entity.ExpenseEntity
import com.isivoltpro.maginaolivo.data.local.entity.HarvestEntity
import com.isivoltpro.maginaolivo.data.local.entity.LocalMetadata
import com.isivoltpro.maginaolivo.data.local.entity.RecollectionRatesEntity
import com.isivoltpro.maginaolivo.data.local.model.CampaignStatus
import com.isivoltpro.maginaolivo.data.local.model.OutboxOperation
import com.isivoltpro.maginaolivo.data.local.model.SyncEntityType
import com.isivoltpro.maginaolivo.data.local.model.SyncStatus
import com.isivoltpro.maginaolivo.domain.equipment.EquipmentLine
import com.isivoltpro.maginaolivo.domain.equipment.EquipmentPriceSnapshot
import com.isivoltpro.maginaolivo.domain.equipment.EquipmentType
import com.isivoltpro.maginaolivo.domain.expense.CalculatedCost
import com.isivoltpro.maginaolivo.domain.expense.DayCostCalculator
import com.isivoltpro.maginaolivo.domain.expense.DayCostKind
import com.isivoltpro.maginaolivo.domain.expense.ExpenseCategory
import com.isivoltpro.maginaolivo.domain.expense.ExpenseOrigin
import com.isivoltpro.maginaolivo.domain.expense.ExpenseStatus
import com.isivoltpro.maginaolivo.domain.expense.RecollectionRates
import com.isivoltpro.maginaolivo.domain.labour.LabourPricing
import com.isivoltpro.maginaolivo.domain.labour.LabourSummary
import java.time.Instant
import java.util.UUID
import org.json.JSONObject

/**
 * CR-010 A3: the one money path for a day's calculated labour and machinery cost. Each day has
 * at most one `DAY_LABOUR` and one `DAY_EQUIPMENT` Expense, updated in place (same id, new
 * version) whenever confirmed attendance or equipment prices change, and removed when no lines
 * remain. Day and Campaign costs read only the ledger, so nothing is ever summed twice.
 *
 * A hand-typed cost of the same kind linked to the same day stands: the calculated one is then
 * kept as a draft, never summed, until the farmer picks it ([preferCalculated]). A closed
 * Campaign is history: its entries are never recalculated. Must run inside the caller's
 * transaction.
 */
internal class DayCostLedger(
    private val database: MaginaOlivoDatabase,
    private val idGenerator: IdGenerator,
) {
    suspend fun sync(harvestId: UUID?, now: Instant) {
        if (harvestId == null) return
        val day = database.harvestDao().findById(harvestId)?.takeIf { it.metadata.deletedAt == null } ?: return
        val campaign = day.campaignId?.let { database.campaignDao().findById(it) } ?: return
        if (campaign.status != CampaignStatus.ACTIVE && campaign.status != CampaignStatus.HARVEST) return
        val farmId = day.farmId ?: return
        val rates = database.recollectionRatesDao().findForFarm(farmId)?.toDomain() ?: RecollectionRates()
        val labour = database.labourDao().listForHarvest(harvestId).map { it.toLabourEntry() }
        val equipment = database.equipmentDao().listForHarvest(harvestId).map { it.toLine() }
        // An incomplete historical day keeps its old ledger until all prices are confirmed.
        if (labour.all { it.appliedRate != null }) {
            val currencies = labour.map { it.appliedRate!!.currency }.distinct()
            if (currencies.size > 1) throw LabourFinanceInvalid("currency", "mixed_day_currency")
            val amount = labour.fold(0L) { total, line -> Math.addExact(total, LabourPricing.amountMinor(line)!!) }
            val cost = if (labour.isEmpty()) null else CalculatedCost(amount, LabourSummary.of(labour).label(), null)
            post(day, DayCostKind.LABOUR, cost, currencies.firstOrNull() ?: rates.currency, now)
        } else {
            val postedLegacy = database.expenseDao().listForHarvest(day.id).any {
                it.origin == ExpenseOrigin.DAY_LABOUR.name && it.status == ExpenseStatus.POSTED.name
            }
            if (postedLegacy && manual(day.id, DayCostKind.LABOUR).isNotEmpty()) {
                throw LabourFinanceInvalid("appliedRate", "confirm_missing_prices")
            }
        }
        // Legacy rows with no confirmed price keep the historical Expense intact.
        if (equipment.all { it.appliedPrice != null }) {
            val currencies = equipment.map { it.appliedPrice!!.currency }.distinct()
            val historical = database.expenseDao().listForHarvest(day.id).filter {
                it.origin == ExpenseOrigin.DAY_EQUIPMENT.name && it.status == ExpenseStatus.POSTED.name
            }
            if (currencies.size > 1 || historical.size > 1 ||
                (historical.isNotEmpty() && currencies.any { it != historical.single().currency })) {
                throw LabourFinanceInvalid("currency", "currency_mismatch")
            }
            post(day, DayCostKind.EQUIPMENT, DayCostCalculator.equipment(equipment),
                currencies.firstOrNull() ?: historical.singleOrNull()?.currency ?: rates.currency, now)
        }
        LabourFinance(database).verifyCampaign(campaign.id)
    }

    /** Every live day of the Farm's running Campaign, after its prices changed. */
    suspend fun syncFarm(farmId: UUID, now: Instant) {
        val campaign = database.campaignDao().findCurrent(farmId) ?: return
        database.harvestDao().listLiveForCampaign(campaign.id).forEach { sync(it.id, now) }
    }

    /** The hand-typed costs of [kind] on that day go back to draft; the calculation then stands. */
    suspend fun preferCalculated(harvestId: UUID, kind: DayCostKind, now: Instant) {
        if (kind == DayCostKind.LABOUR && database.labourDao().listForHarvest(harvestId).any { it.toLabourEntry().appliedRate == null }) {
            throw LabourFinanceInvalid("appliedRate", "confirm_missing_prices")
        }
        if (kind == DayCostKind.EQUIPMENT && database.equipmentDao().listForHarvest(harvestId).any { it.toLine().appliedPrice == null }) {
            throw LabourFinanceInvalid("appliedPrice", "confirm_missing_prices")
        }
        manual(harvestId, kind).forEach { expense ->
            database.expenseDao().upsert(expense.copy(status = ExpenseStatus.DRAFT.name, metadata = expense.metadata.next(now)))
            database.enqueueCollapsed(idGenerator, SyncEntityType.EXPENSE, expense.id, OutboxOperation.UPDATE, now)
        }
        sync(harvestId, now)
    }

    /** A removed day's calculated costs go with it: no attendance backs them any more. */
    suspend fun removeFor(harvestId: UUID, now: Instant) {
        database.expenseDao().listForHarvest(harvestId).filter { it.origin in CALCULATED }.forEach { remove(it, now) }
        LabourFinance(database).verifyCampaign(database.harvestDao().findById(harvestId)?.campaignId)
    }

    private suspend fun post(day: HarvestEntity, kind: DayCostKind, cost: CalculatedCost?, currency: String, now: Instant) {
        val current = database.expenseDao().listForHarvest(day.id).firstOrNull { it.origin == kind.origin.name }
        if (cost == null) {
            current?.let { remove(it, now) }
            return
        }
        val status = if (manual(day.id, kind).isEmpty()) ExpenseStatus.POSTED else ExpenseStatus.DRAFT
        val wanted = ExpenseEntity(
            id = current?.id ?: idGenerator.newId(),
            workspaceId = day.workspaceId,
            campaignId = day.campaignId,
            farmId = day.farmId,
            harvestId = day.id,
            expenseDate = day.harvestDate,
            concept = kind.concept,
            category = kind.category.name,
            amountMinor = cost.amountMinor,
            currency = currency,
            notes = cost.note.ifEmpty { null },
            status = status.name,
            origin = kind.origin.name,
            metadata = current?.metadata?.next(now) ?: LocalMetadata(now, now, syncStatus = SyncStatus.PENDING),
        )
        if (current != null && current.copy(metadata = wanted.metadata) == wanted) return
        database.expenseDao().upsert(wanted)
        database.enqueueCollapsed(
            idGenerator, SyncEntityType.EXPENSE, wanted.id,
            if (current == null) OutboxOperation.CREATE else OutboxOperation.UPDATE, now,
        )
    }

    /** Live, posted, hand-typed (or document) costs linked to the same day that stand for [kind]. */
    private suspend fun manual(harvestId: UUID, kind: DayCostKind): List<ExpenseEntity> =
        database.expenseDao().listForHarvest(harvestId).filter { expense ->
            val category = runCatching { ExpenseCategory.valueOf(expense.category) }.getOrNull()
            expense.origin !in CALCULATED && expense.status == ExpenseStatus.POSTED.name &&
                category != null && kind.isReplacedBy(category, expense.concept)
        }

    private suspend fun remove(expense: ExpenseEntity, now: Instant) {
        database.expenseDao().upsert(expense.copy(metadata = expense.metadata.next(now).copy(deletedAt = now)))
        database.enqueueCollapsed(idGenerator, SyncEntityType.EXPENSE, expense.id, OutboxOperation.DELETE, now)
    }

    private fun LocalMetadata.next(now: Instant) =
        copy(updatedAt = now, version = version + 1, syncStatus = SyncStatus.PENDING)

    companion object {
        val CALCULATED = setOf(ExpenseOrigin.DAY_LABOUR.name, ExpenseOrigin.DAY_EQUIPMENT.name)

        fun RecollectionRatesEntity.toDomain(): RecollectionRates {
            val json = runCatching { JSONObject(equipmentDayJson) }.getOrElse { JSONObject() }
            val equipment = EquipmentType.values().mapNotNull { type ->
                if (json.has(type.name) && !json.isNull(type.name)) type to json.getLong(type.name) else null
            }.toMap()
            return RecollectionRates(fullDayMinor, hourlyMinor, equipment, currency)
        }

        fun equipmentJson(rates: RecollectionRates): String =
            JSONObject().apply { rates.equipmentDayMinor.forEach { (type, minor) -> put(type.name, minor) } }.toString()
    }
}

private fun com.isivoltpro.maginaolivo.data.local.entity.HarvestEquipmentEntity.toLine() = EquipmentLine(
    id = id,
    harvestId = harvestId,
    type = runCatching { EquipmentType.valueOf(type) }.getOrDefault(EquipmentType.OTHER),
    label = label,
    quantity = quantity,
    machineId = machineId,
    version = metadata.version,
    appliedPrice = if (appliedPriceMinor != null && appliedCurrency != null && appliedPriceDate != null)
        EquipmentPriceSnapshot(appliedPriceMinor, appliedCurrency, appliedPriceDate) else null,
)
