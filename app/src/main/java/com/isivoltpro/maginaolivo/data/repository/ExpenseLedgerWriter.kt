package com.isivoltpro.maginaolivo.data.repository

import com.isivoltpro.maginaolivo.core.id.IdGenerator
import com.isivoltpro.maginaolivo.data.local.MaginaOlivoDatabase
import com.isivoltpro.maginaolivo.data.local.entity.ExpenseEntity
import com.isivoltpro.maginaolivo.data.local.entity.LocalMetadata
import com.isivoltpro.maginaolivo.data.local.entity.PurchaseEntity
import com.isivoltpro.maginaolivo.data.local.entity.PurchaseItemEntity
import com.isivoltpro.maginaolivo.data.local.model.OutboxOperation
import com.isivoltpro.maginaolivo.data.local.model.RecordStatus
import com.isivoltpro.maginaolivo.data.local.model.SyncEntityType
import com.isivoltpro.maginaolivo.data.local.model.SyncStatus
import com.isivoltpro.maginaolivo.domain.expense.ExpenseDraft
import com.isivoltpro.maginaolivo.domain.expense.ExpenseOrigin
import com.isivoltpro.maginaolivo.domain.expense.ExpenseStatus
import java.time.Instant
import java.util.UUID

/** A rejected expense field. Thrown inside a transaction so everything rolls back. */
internal class InvalidExpense(val field: String, val code: String) : RuntimeException("$field:$code")

/**
 * The only code that writes `expenses`, `purchases` and `purchase_items`.
 *
 * Every path that can create money — the Expense form, an Activity's convenience cost
 * (`RC1-NORMATIVE-ADDENDUM` D2) and a reviewed document — goes through here, inside the
 * caller's transaction, so the relation rules and the single outbox intent are the same
 * whichever screen the amount was typed on.
 */
internal class ExpenseLedgerWriter(
    private val database: MaginaOlivoDatabase,
    private val idGenerator: IdGenerator,
) {
    suspend fun insert(
        workspaceId: UUID,
        draft: ExpenseDraft,
        status: ExpenseStatus,
        origin: ExpenseOrigin,
        now: Instant,
    ): UUID {
        val id = idGenerator.newId()
        database.expenseDao().upsert(
            resolve(id, workspaceId, draft, status, origin, LocalMetadata(now, now, syncStatus = SyncStatus.PENDING)),
        )
        writePurchase(id, workspaceId, draft, now)
        database.enqueueCollapsed(idGenerator, SyncEntityType.EXPENSE, id, OutboxOperation.CREATE, now)
        return id
    }

    suspend fun rewrite(current: ExpenseEntity, draft: ExpenseDraft, now: Instant) {
        database.expenseDao().upsert(
            resolve(
                current.id,
                current.workspaceId,
                draft,
                ExpenseStatus.valueOf(current.status),
                ExpenseOrigin.valueOf(current.origin),
                current.metadata.next(now),
            ),
        )
        writePurchase(current.id, current.workspaceId, draft, now)
        database.enqueueCollapsed(idGenerator, SyncEntityType.EXPENSE, current.id, OutboxOperation.UPDATE, now)
    }

    suspend fun post(current: ExpenseEntity, now: Instant) {
        if (current.amountMinor <= 0) throw InvalidExpense("amountMinor", "not_positive")
        database.expenseDao().upsert(current.copy(status = ExpenseStatus.POSTED.name, metadata = current.metadata.next(now)))
        database.enqueueCollapsed(idGenerator, SyncEntityType.EXPENSE, current.id, OutboxOperation.UPDATE, now)
    }

    suspend fun delete(current: ExpenseEntity, now: Instant) {
        database.expenseDao().upsert(current.copy(metadata = current.metadata.next(now).copy(deletedAt = now)))
        database.expenseDao().findPurchaseForExpense(current.id)?.let { purchase ->
            database.expenseDao().upsertPurchase(purchase.copy(metadata = purchase.metadata.next(now).copy(deletedAt = now)))
        }
        database.enqueueCollapsed(idGenerator, SyncEntityType.EXPENSE, current.id, OutboxOperation.DELETE, now)
    }

    /**
     * Checks every relation against local truth and fills the ones that follow from it:
     * the supplier's name is copied from the organization so history keeps what was written,
     * and an expense on a Farm with a running Campaign is counted in that Campaign.
     */
    private suspend fun resolve(
        id: UUID,
        workspaceId: UUID,
        draft: ExpenseDraft,
        status: ExpenseStatus,
        origin: ExpenseOrigin,
        metadata: LocalMetadata,
    ): ExpenseEntity {
        val concept = draft.concept.trim()
        if (concept.isEmpty()) throw InvalidExpense("concept", "blank")
        if (draft.amountMinor < 0) throw InvalidExpense("amountMinor", "negative")
        if (status == ExpenseStatus.POSTED && draft.amountMinor == 0L) throw InvalidExpense("amountMinor", "not_positive")
        if (draft.currency.isBlank()) throw InvalidExpense("currency", "blank")

        // Phase 19F: a Jornada cost belongs to the Jornada's Farm and Campaign.
        val harvest = draft.harvestId?.let { harvestId ->
            database.harvestDao().findById(harvestId)
                ?.takeIf { it.workspaceId == workspaceId && it.metadata.deletedAt == null }
                ?: throw InvalidExpense("harvestId", "not_found")
        }
        if (harvest != null && draft.farmId != null && draft.farmId != harvest.farmId) {
            throw InvalidExpense("harvestId", "not_in_farm")
        }
        val farm = (draft.farmId ?: harvest?.farmId)?.let { farmId ->
            database.farmDao().findById(farmId)?.takeIf { it.workspaceId == workspaceId && it.metadata.deletedAt == null }
                ?: throw InvalidExpense("farmId", "not_found")
        }
        draft.parcelId?.let { parcelId ->
            val parcel = database.parcelDao().findById(parcelId)
            if (parcel == null || parcel.status != RecordStatus.ACTIVE || parcel.workspaceId != workspaceId) {
                throw InvalidExpense("parcelId", "not_found")
            }
            if (farm != null && database.parcelDao().findCurrentMembership(parcelId)?.farmId != farm.id) {
                throw InvalidExpense("parcelId", "not_in_farm")
            }
        }
        draft.activityId?.let { activityId ->
            val activity = database.activityDao().findById(activityId)
            if (activity == null || activity.workspaceId != workspaceId || activity.metadata.deletedAt != null) {
                throw InvalidExpense("activityId", "not_found")
            }
            if (farm != null && activity.farmId != farm.id) throw InvalidExpense("activityId", "not_in_farm")
        }
        val campaignId = harvest?.campaignId ?: draft.campaignId ?: farm?.let { database.campaignDao().findCurrent(it.id)?.id }
        val organization = draft.supplierOrganizationId?.let { organizationId ->
            database.organizationDao().findById(organizationId)?.takeIf { it.workspaceId == workspaceId }
                ?: throw InvalidExpense("supplierOrganizationId", "not_found")
        }
        return ExpenseEntity(
            id = id,
            workspaceId = workspaceId,
            campaignId = campaignId,
            farmId = farm?.id,
            parcelId = draft.parcelId,
            activityId = draft.activityId,
            harvestId = harvest?.id,
            supplierOrganizationId = organization?.id,
            expenseDate = draft.expenseDate,
            concept = concept,
            category = draft.category.name,
            amountMinor = draft.amountMinor,
            currency = draft.currency.trim().uppercase(),
            provider = organization?.name ?: draft.supplierText?.trim()?.ifEmpty { null },
            notes = draft.notes?.trim()?.ifEmpty { null },
            status = status.name,
            origin = origin.name,
            metadata = metadata,
        )
    }

    /**
     * Purchase detail is part of the Expense aggregate: it moves with it and never has an
     * intent or a total of its own. Lines describe; they never add money.
     */
    private suspend fun writePurchase(expenseId: UUID, workspaceId: UUID, draft: ExpenseDraft, now: Instant) {
        val lines = draft.lines.filter { it.productName.isNotBlank() }
        lines.forEach { line ->
            if ((line.quantity ?: 0.0) < 0 || (line.unitPriceMinor ?: 0) < 0 || (line.lineTotalMinor ?: 0) < 0) {
                throw InvalidExpense("lines", "negative")
            }
        }
        val invoiceNumber = draft.invoiceNumber?.trim()?.ifEmpty { null }
        val existing = database.expenseDao().findPurchaseForExpense(expenseId)
        if (lines.isEmpty() && invoiceNumber == null && existing == null) return

        val purchase = PurchaseEntity(
            id = existing?.id ?: idGenerator.newId(),
            workspaceId = workspaceId,
            expenseId = expenseId,
            supplierOrganizationId = draft.supplierOrganizationId,
            purchaseDate = draft.expenseDate,
            invoiceNumber = invoiceNumber,
            metadata = existing?.metadata?.next(now) ?: LocalMetadata(now, now, syncStatus = SyncStatus.PENDING),
        )
        database.expenseDao().upsertPurchase(purchase)
        database.expenseDao().deleteItems(purchase.id)
        if (lines.isNotEmpty()) {
            database.expenseDao().upsertItems(
                lines.mapIndexed { index, line ->
                    PurchaseItemEntity(
                        id = idGenerator.newId(),
                        workspaceId = workspaceId,
                        purchaseId = purchase.id,
                        position = index,
                        productName = line.productName.trim(),
                        quantity = line.quantity,
                        unit = line.unit?.trim()?.ifEmpty { null },
                        unitPriceMinor = line.unitPriceMinor,
                        lineTotalMinor = line.lineTotalMinor,
                        metadata = LocalMetadata(now, now, syncStatus = SyncStatus.PENDING),
                    )
                },
            )
        }
    }

    private fun LocalMetadata.next(now: Instant) =
        copy(updatedAt = now, version = version + 1, syncStatus = SyncStatus.PENDING)
}
