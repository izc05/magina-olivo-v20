package com.isivoltpro.maginaolivo.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.isivoltpro.maginaolivo.data.local.entity.ExpenseEntity
import com.isivoltpro.maginaolivo.data.local.entity.PurchaseEntity
import com.isivoltpro.maginaolivo.data.local.entity.PurchaseItemEntity
import java.util.UUID
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {
    @Upsert suspend fun upsert(expense: ExpenseEntity)

    @Query("SELECT * FROM expenses WHERE id = :id LIMIT 1")
    suspend fun findById(id: UUID): ExpenseEntity?

    @Query("SELECT * FROM expenses WHERE id = :id AND deleted_at IS NULL LIMIT 1")
    fun observeById(id: UUID): Flow<ExpenseEntity?>

    @Query(
        """
        SELECT * FROM expenses
        WHERE workspace_id = :workspaceId AND deleted_at IS NULL
        ORDER BY expense_date DESC, created_at DESC, id
        """,
    )
    fun observeForWorkspace(workspaceId: UUID): Flow<List<ExpenseEntity>>

    @Query(
        """
        SELECT * FROM expenses
        WHERE deleted_at IS NULL
        ORDER BY expense_date DESC, created_at DESC, id
        """,
    )
    fun observeAll(): Flow<List<ExpenseEntity>>

    @Query(
        """
        SELECT * FROM expenses
        WHERE activity_id = :activityId AND deleted_at IS NULL
        ORDER BY expense_date DESC, created_at DESC, id
        """,
    )
    fun observeForActivity(activityId: UUID): Flow<List<ExpenseEntity>>

    /** Phase 19F: the Expenses of one Jornada. */
    @Query(
        """
        SELECT * FROM expenses
        WHERE harvest_id = :harvestId AND deleted_at IS NULL
        ORDER BY expense_date DESC, created_at DESC, id
        """,
    )
    fun observeForHarvest(harvestId: UUID): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE harvest_id = :harvestId AND deleted_at IS NULL")
    suspend fun listForHarvest(harvestId: UUID): List<ExpenseEntity>

    /** #427: unresolved money review blocks archiving its current context. */
    @Query("SELECT COUNT(*) FROM expenses WHERE farm_id = :farmId AND status = 'DRAFT' AND deleted_at IS NULL")
    suspend fun countDraftForFarm(farmId: UUID): Int

    /** #427: unresolved money review blocks archiving the Parcel it explicitly references. */
    @Query("SELECT COUNT(*) FROM expenses WHERE parcel_id = :parcelId AND status = 'DRAFT' AND deleted_at IS NULL")
    suspend fun countDraftForParcel(parcelId: UUID): Int

    /** #441: the live Expenses that point at one Activity (drafts included). */
    @Query("SELECT * FROM expenses WHERE activity_id = :activityId AND deleted_at IS NULL")
    suspend fun listForActivity(activityId: UUID): List<ExpenseEntity>

    /** #437: real money tied to an Activity besides its own convenience cost (drafts included). */
    @Query(
        """
        SELECT COUNT(*) FROM expenses
        WHERE activity_id = :activityId AND origin != 'ACTIVITY_COST' AND deleted_at IS NULL
        """,
    )
    suspend fun countLinkedToActivity(activityId: UUID): Int

    /** The single convenience-cost row an Activity form edits (`RC1-NORMATIVE-ADDENDUM` D2). */
    @Query(
        """
        SELECT * FROM expenses
        WHERE activity_id = :activityId AND origin = 'ACTIVITY_COST' AND deleted_at IS NULL
        LIMIT 1
        """,
    )
    suspend fun findActivityCost(activityId: UUID): ExpenseEntity?

    @Upsert suspend fun upsertPurchase(purchase: PurchaseEntity)

    @Query("SELECT * FROM purchases WHERE expense_id = :expenseId LIMIT 1")
    suspend fun findPurchaseForExpense(expenseId: UUID): PurchaseEntity?

    @Query("SELECT * FROM purchases WHERE expense_id = :expenseId AND deleted_at IS NULL LIMIT 1")
    fun observePurchaseForExpense(expenseId: UUID): Flow<PurchaseEntity?>

    @Upsert suspend fun upsertItems(items: List<PurchaseItemEntity>)

    @Query("DELETE FROM purchase_items WHERE purchase_id = :purchaseId")
    suspend fun deleteItems(purchaseId: UUID)

    @Query("SELECT * FROM purchase_items WHERE purchase_id = :purchaseId ORDER BY position, id")
    suspend fun listItems(purchaseId: UUID): List<PurchaseItemEntity>

    @Query(
        """
        SELECT i.* FROM purchase_items i
        JOIN purchases p ON p.id = i.purchase_id
        WHERE p.expense_id = :expenseId AND p.deleted_at IS NULL
        ORDER BY i.position, i.id
        """,
    )
    fun observeItemsForExpense(expenseId: UUID): Flow<List<PurchaseItemEntity>>

    /** #475: live calculated day costs kept as a draft because a hand-typed one stands for them. */
    @Query("SELECT * FROM expenses WHERE origin IN ('DAY_LABOUR', 'DAY_EQUIPMENT') AND status = 'DRAFT' AND deleted_at IS NULL")
    suspend fun listDraftCalculated(): List<ExpenseEntity>

    /** #475: explicit replacements of [category] a day has ever had, deleted or drafted ones included. */
    @Query("SELECT COUNT(*) FROM expenses WHERE harvest_id = :harvestId AND origin = 'DAY_REPLACEMENT' AND category = :category")
    suspend fun countReplacementsEver(harvestId: UUID, category: String): Int
}
