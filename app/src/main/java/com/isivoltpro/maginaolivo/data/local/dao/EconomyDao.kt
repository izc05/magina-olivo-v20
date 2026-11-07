package com.isivoltpro.maginaolivo.data.local.dao

import androidx.room.*
import com.isivoltpro.maginaolivo.data.local.entity.*
import java.util.UUID
import kotlinx.coroutines.flow.Flow

data class CurrencyAmount(val currency: String, val amountMinor: Long)

@Dao
interface EconomyDao {
    @Query("SELECT * FROM cooperative_settlements WHERE workspace_id=:workspaceId ORDER BY document_date,id")
    suspend fun settlements(workspaceId: UUID): List<CooperativeSettlementEntity>
    @Query("SELECT * FROM cooperative_settlements WHERE workspace_id=:workspaceId")
    fun observeSettlements(workspaceId: UUID): Flow<List<CooperativeSettlementEntity>>
    @Query("""SELECT a.* FROM settlement_advance_applications a JOIN cooperative_settlements s ON s.id=a.advance_id
        WHERE s.workspace_id=:workspaceId ORDER BY a.created_at,a.id""")
    suspend fun applications(workspaceId: UUID): List<SettlementAdvanceApplicationEntity>
    @Query("""SELECT p.* FROM settlement_payments p JOIN cooperative_settlements s ON s.id=p.settlement_id
        WHERE s.workspace_id=:workspaceId ORDER BY p.payment_date,p.id""")
    suspend fun payments(workspaceId: UUID): List<SettlementPaymentEntity>
    /** Invalidation triggers only: any change of these tables re-reads the whole workspace ledger. */
    @Query("SELECT COUNT(*) FROM settlement_advance_applications") fun observeApplications(): Flow<Int>
    @Query("SELECT COUNT(*) FROM settlement_payments") fun observePayments(): Flow<Int>
    @Query("SELECT COUNT(*) FROM expenses WHERE workspace_id=:workspaceId") fun observeExpenses(workspaceId: UUID): Flow<Int>

    @Query("SELECT * FROM cooperative_settlements WHERE id=:id") suspend fun settlement(id: UUID): CooperativeSettlementEntity?
    @Query("SELECT * FROM settlement_advance_applications WHERE id=:id") suspend fun application(id: UUID): SettlementAdvanceApplicationEntity?
    @Query("SELECT * FROM settlement_payments WHERE id=:id") suspend fun payment(id: UUID): SettlementPaymentEntity?
    @Insert suspend fun insert(row: CooperativeSettlementEntity)
    @Update suspend fun update(row: CooperativeSettlementEntity)
    @Insert suspend fun insert(row: SettlementAdvanceApplicationEntity)
    @Insert suspend fun insert(row: SettlementPaymentEntity)
    @Update suspend fun update(row: SettlementPaymentEntity)

    /** Each POSTED, non-deleted canonical Expense once, per currency (P2 rule; no activity/jornal re-add). */
    @Query("""SELECT currency, SUM(amount_minor) AS amountMinor FROM expenses
        WHERE workspace_id=:workspaceId AND status='POSTED' AND deleted_at IS NULL
          AND (:farmId IS NULL OR farm_id=:farmId) GROUP BY currency ORDER BY currency""")
    suspend fun postedCosts(workspaceId: UUID, farmId: UUID?): List<CurrencyAmount>
    @Query("""SELECT currency, SUM(amount_minor) AS amountMinor FROM expenses
        WHERE status='POSTED' AND deleted_at IS NULL AND id IN (:ids) GROUP BY currency ORDER BY currency""")
    suspend fun postedCostsOf(ids: List<UUID>): List<CurrencyAmount>
}
