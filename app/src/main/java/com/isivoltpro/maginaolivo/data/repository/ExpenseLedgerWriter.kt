package com.isivoltpro.maginaolivo.data.repository

import com.isivoltpro.maginaolivo.core.id.IdGenerator
import com.isivoltpro.maginaolivo.data.local.MaginaOlivoDatabase
import com.isivoltpro.maginaolivo.data.local.entity.ExpenseEntity
import com.isivoltpro.maginaolivo.data.local.entity.LocalMetadata
import com.isivoltpro.maginaolivo.data.local.entity.PurchaseEntity
import com.isivoltpro.maginaolivo.data.local.entity.PurchaseItemEntity
import com.isivoltpro.maginaolivo.data.local.model.CampaignStatus
import com.isivoltpro.maginaolivo.data.local.model.OutboxOperation
import com.isivoltpro.maginaolivo.data.local.model.RecordStatus
import com.isivoltpro.maginaolivo.data.local.model.SyncEntityType
import com.isivoltpro.maginaolivo.data.local.model.SyncStatus
import com.isivoltpro.maginaolivo.domain.expense.ExpenseCategory
import com.isivoltpro.maginaolivo.domain.expense.ExpenseDraft
import com.isivoltpro.maginaolivo.domain.expense.DayCostRole
import com.isivoltpro.maginaolivo.domain.expense.ExpenseOrigin
import com.isivoltpro.maginaolivo.domain.expense.ExpenseStatus
import java.time.Instant
import java.time.LocalDate
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
        val expense = resolve(id, workspaceId, draft, status, origin, LocalMetadata(now, now, syncStatus = SyncStatus.PENDING))
        requireEditableCampaign(expense)
        database.expenseDao().upsert(expense)
        writePurchase(id, workspaceId, draft, now)
        database.enqueueCollapsed(idGenerator, SyncEntityType.EXPENSE, id, OutboxOperation.CREATE, now)
        return id
    }

    suspend fun rewrite(
        current: ExpenseEntity,
        draft: ExpenseDraft,
        now: Instant,
        origin: ExpenseOrigin = ExpenseOrigin.valueOf(current.origin),
    ) {
        requireEditableCampaign(current)
        val expense = resolve(
            current.id,
            current.workspaceId,
            draft,
            ExpenseStatus.valueOf(current.status),
            origin,
            current.metadata.next(now),
            // #451: the same supplier keeps the name it was recorded with; only choosing another
            // one takes that one's current name.
            keptProvider = current.provider.takeIf {
                current.supplierOrganizationId != null && draft.supplierOrganizationId == current.supplierOrganizationId
            },
            historicalSupplierId = current.supplierOrganizationId,
            recorded = current,
        )
        requireEditableCampaign(expense)
        database.expenseDao().upsert(expense)
        writePurchase(current.id, current.workspaceId, draft, now)
        database.enqueueCollapsed(idGenerator, SyncEntityType.EXPENSE, current.id, OutboxOperation.UPDATE, now)
    }

    suspend fun post(current: ExpenseEntity, now: Instant, today: LocalDate) {
        if (current.status == ExpenseStatus.POSTED.name) return
        requireEditableCampaign(current)
        if (current.amountMinor <= 0) throw InvalidExpense("amountMinor", "not_positive")
        // #456: the moment money starts to count is a domain confirmation, not a status flip. The
        // draft is checked against today's truth — real date, Farm, Parcel, work, day, Campaign —
        // with the same rules as any save; a relation that no longer holds keeps it a DRAFT.
        if (current.expenseDate.isAfter(today)) throw InvalidExpense("expenseDate", "future_real_expense")
        val resolved = resolve(
            current.id,
            current.workspaceId,
            current.asDraftForCheck(),
            ExpenseStatus.POSTED,
            ExpenseOrigin.valueOf(current.origin),
            current.metadata,
            // #451: posting confirms the stored supplier snapshot; it never refreshes its text.
            keptProvider = current.provider.takeIf { current.supplierOrganizationId != null },
            historicalSupplierId = current.supplierOrganizationId,
        )
        requireEditableCampaign(resolved)
        // #529: POST must persist the same structural context it just validated. Keep historical
        // snapshots and unrelated legacy fields exactly as stored; canonicalize only relations.
        database.expenseDao().upsert(
            current.copy(
                campaignId = resolved.campaignId,
                farmId = resolved.farmId,
                parcelId = resolved.parcelId,
                activityId = resolved.activityId,
                harvestId = resolved.harvestId,
                status = ExpenseStatus.POSTED.name,
                metadata = current.metadata.next(now),
            ),
        )
        database.enqueueCollapsed(idGenerator, SyncEntityType.EXPENSE, current.id, OutboxOperation.UPDATE, now)
    }

    /** #429: the money stays exactly as counted; only its tie to a work (and that origin) goes. */
    suspend fun detachFromActivity(current: ExpenseEntity, now: Instant) {
        requireEditableCampaign(current)
        database.expenseDao().upsert(
            current.copy(activityId = null, origin = ExpenseOrigin.MANUAL.name, metadata = current.metadata.next(now)),
        )
        database.enqueueCollapsed(idGenerator, SyncEntityType.EXPENSE, current.id, OutboxOperation.UPDATE, now)
    }

    suspend fun delete(current: ExpenseEntity, now: Instant) {
        if (current.metadata.deletedAt != null) return
        requireEditableCampaign(current)
        database.expenseDao().upsert(current.copy(metadata = current.metadata.next(now).copy(deletedAt = now)))
        database.expenseDao().findPurchaseForExpense(current.id)?.let { purchase ->
            database.expenseDao().upsertPurchase(purchase.copy(metadata = purchase.metadata.next(now).copy(deletedAt = now)))
        }
        database.enqueueCollapsed(idGenerator, SyncEntityType.EXPENSE, current.id, OutboxOperation.DELETE, now)
    }

    private fun ExpenseEntity.asDraftForCheck() = ExpenseDraft(
        dayCostRole = if (origin == ExpenseOrigin.DAY_REPLACEMENT.name) DayCostRole.REPLACEMENT else DayCostRole.ADDITIVE,
        expenseDate = expenseDate,
        concept = concept,
        category = ExpenseCategory.entries.firstOrNull { it.name == category } ?: ExpenseCategory.OTHER,
        amountMinor = amountMinor,
        currency = currency,
        supplierOrganizationId = supplierOrganizationId,
        supplierText = provider,
        farmId = farmId,
        parcelId = parcelId,
        campaignId = campaignId,
        activityId = activityId,
        notes = notes,
        harvestId = harvestId,
    )

    /** Also used by day linking, which changes context without rewriting purchase detail. */
    suspend fun requireEditableCampaign(expense: ExpenseEntity) {
        val dayCampaign = expense.harvestId?.let { database.harvestDao().findById(it)?.campaignId }
        val activityCampaign = expense.activityId?.let { database.activityDao().findById(it)?.campaignId }
        // #528: legacy rows may have campaign_id = NULL even though their Activity already names
        // the Campaign. Every explicit relation that reaches a CLOSED Campaign protects the money.
        listOfNotNull(expense.campaignId, dayCampaign, activityCampaign).distinct().forEach { campaignId ->
            if (database.campaignDao().findById(campaignId)?.status == CampaignStatus.CLOSED) {
                throw InvalidExpense("campaignId", "campaign_closed")
            }
        }
    }

    /**
     * Checks every relation against local truth and fills the ones that follow from it:
     * the supplier's name is copied from the organization so history keeps what was written,
     * and economic campaign context comes only from an explicit Campaign or linked day.
     */
    private suspend fun resolve(
        id: UUID,
        workspaceId: UUID,
        draft: ExpenseDraft,
        status: ExpenseStatus,
        origin: ExpenseOrigin,
        metadata: LocalMetadata,
        keptProvider: String? = null,
        historicalSupplierId: UUID? = null,
        /** The Expense as stored, when rewriting it: once POSTED, its Farm/Parcel pair is history (#476). */
        recorded: ExpenseEntity? = null,
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
        val activity = draft.activityId?.let { activityId ->
            database.activityDao().findById(activityId)
                ?.takeIf { it.workspaceId == workspaceId && it.metadata.deletedAt == null }
                ?: throw InvalidExpense("activityId", "not_found")
        }
        // #476: a Parcel a POSTED Expense already had, on the same Farm, is a historical fact: it
        // stays valid though the Parcel was archived or moved to another Farm since. A DRAFT is not
        // history yet (#456: confirming it re-checks every live relation), and a Parcel chosen now
        // must be active and belong to that Farm today.
        val keptParcel = recorded != null && recorded.status == ExpenseStatus.POSTED.name &&
            draft.parcelId != null && draft.parcelId == recorded.parcelId &&
            (draft.farmId ?: recorded.farmId) == recorded.farmId
        val parcelFarmId = draft.parcelId?.let { parcelId ->
            val parcel = database.parcelDao().findById(parcelId)
            if (parcel == null || parcel.workspaceId != workspaceId) throw InvalidExpense("parcelId", "not_found")
            if (keptParcel) return@let recorded!!.farmId
            if (parcel.status != RecordStatus.ACTIVE) throw InvalidExpense("parcelId", "not_found")
            database.parcelDao().findCurrentMembership(parcelId)?.farmId
        }
        // #433: every relation that knows its Farm must name the same one, and an Expense with a
        // Parcel or an Activity always keeps that Farm (never a child relation without its Farm).
        val farmId = draft.farmId ?: harvest?.farmId ?: activity?.farmId ?: parcelFarmId
        if (harvest != null && farmId != harvest.farmId) throw InvalidExpense("harvestId", "not_in_farm")
        if (activity != null && activity.farmId != null && farmId != activity.farmId) throw InvalidExpense("activityId", "not_in_farm")
        if (draft.parcelId != null && (farmId == null || parcelFarmId != farmId)) throw InvalidExpense("parcelId", "not_in_farm")
        if (activity != null && farmId == null) throw InvalidExpense("activityId", "not_in_farm")
        val farm = farmId?.let {
            database.farmDao().findById(it)?.takeIf { farm -> farm.workspaceId == workspaceId && farm.metadata.deletedAt == null }
                ?: throw InvalidExpense("farmId", "not_found")
        }
        if (activity != null) {
            // #433 C: the Parcel of a work's cost is one the work was done on; a work with no
            // Parcel targets keeps its cost without a Parcel.
            draft.parcelId?.let { parcelId ->
                val targets = database.activityDao().listTargets(activity.id).filter { it.metadata.deletedAt == null }
                if (targets.none { it.parcelId == parcelId }) throw InvalidExpense("parcelId", "not_in_activity")
            }
            // #433 F: a Jornada cost may only name work of that same recolección.
            if (harvest != null && activity.campaignId != harvest.campaignId) throw InvalidExpense("activityId", "not_in_day")
        }
        if (harvest?.campaignId != null && draft.campaignId != null && harvest.campaignId != draft.campaignId) {
            throw InvalidExpense("campaignId", "not_in_day")
        }
        // #433 A/B/E: the cost of a work takes that work's Campaign. General work (no Campaign)
        // never puts its cost on a Campaign, and work of one Campaign never on another.
        if (activity != null && harvest == null && draft.campaignId != null && draft.campaignId != activity.campaignId) {
            throw InvalidExpense("campaignId", if (activity.campaignId == null) "activity_general" else "not_in_activity")
        }
        val campaignId = harvest?.campaignId ?: draft.campaignId ?: activity?.campaignId
        campaignId?.let { campaignId ->
            val campaign = database.campaignDao().findById(campaignId)
                ?.takeIf { it.workspaceId == workspaceId && it.metadata.deletedAt == null }
                ?: throw InvalidExpense("campaignId", "not_found")
            if (farm != null && campaign.farmId != farm.id) throw InvalidExpense("campaignId", "not_in_farm")
        }
        val organization = draft.supplierOrganizationId?.let { organizationId ->
            database.organizationDao().findById(organizationId)?.takeIf { it.workspaceId == workspaceId }
                ?: throw InvalidExpense("supplierOrganizationId", "not_found")
        }
        // #451: an archived supplier stays on the Gastos that already had it (and their snapshot),
        // but a new Gasto, or an explicit change of supplier, only takes an active one.
        if (organization != null && organization.metadata.deletedAt != null && organization.id != historicalSupplierId) {
            throw InvalidExpense("supplierOrganizationId", "archived")
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
            provider = organization?.let { keptProvider ?: it.name } ?: draft.supplierText?.trim()?.ifEmpty { null },
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
