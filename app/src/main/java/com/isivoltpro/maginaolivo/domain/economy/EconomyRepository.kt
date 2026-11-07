package com.isivoltpro.maginaolivo.domain.economy

import com.isivoltpro.maginaolivo.core.common.AppResult
import java.util.UUID
import kotlinx.coroutines.flow.Flow

data class EconomySnapshot(val scope: EconomyScope, val ledger: EconomyLedger, val balances: List<EconomyBalance>)

/** CR-015 P5, local only. Every write revalidates scope, authorization and the deterministic rules. */
interface EconomyRepository {
    /** farmId == null: whole workspace. cycleId requires farmId. */
    fun observe(farmId: UUID?, cycleId: UUID? = null): Flow<AppResult<EconomySnapshot>>
    /** New advance or settlement, or a replacement (replacesId). Repeating the same id and content is a no-op. */
    suspend fun record(input: Settlement): AppResult<Unit>
    /** Kept as CANCELLED; refused while it still has active payments or applications. */
    suspend fun cancel(settlementId: UUID): AppResult<Unit>
    suspend fun applyAdvance(application: AdvanceApplication): AppResult<Unit>
    suspend fun recordPayment(payment: SettlementPayment): AppResult<Unit>
    suspend fun cancelPayment(paymentId: UUID): AppResult<Unit>
}
