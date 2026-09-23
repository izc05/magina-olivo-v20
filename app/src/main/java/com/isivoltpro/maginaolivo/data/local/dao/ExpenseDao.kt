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
}

