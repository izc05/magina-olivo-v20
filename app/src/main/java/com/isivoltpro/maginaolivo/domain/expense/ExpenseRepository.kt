package com.isivoltpro.maginaolivo.domain.expense

import com.isivoltpro.maginaolivo.core.common.AppResult
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow

/**
 * Phase 12 — the authoritative Expense ledger (`RC1-NORMATIVE-ADDENDUM` D2).
 *
 * Every financial total in the app is derived from POSTED expenses only. A DRAFT is money
 * nobody has confirmed yet — typically what a reviewed document proposed — and it is never
 * summed. Purchase lines describe what was bought and never add money of their own.
 */
enum class ExpenseCategory {
    LABOR,
    PRODUCTS,
    MACHINERY,
    FUEL,
    IRRIGATION,
    EXTERNAL_SERVICE,
    REPAIR,
    HARVEST,
    TRANSPORT,
    OTHER,
}

enum class ExpenseStatus { DRAFT, POSTED }

enum class ExpenseOrigin { MANUAL, ACTIVITY_COST, DOCUMENT_OCR }

data class PurchaseLine(
    val productName: String,
    val quantity: Double? = null,
    val unit: String? = null,
    val unitPriceMinor: Long? = null,
    val lineTotalMinor: Long? = null,
)

data class Expense(
    val id: UUID,
    val workspaceId: UUID,
    val expenseDate: LocalDate,
    val concept: String,
    val category: ExpenseCategory,
    val amountMinor: Long,
    val currency: String,
    val status: ExpenseStatus,
    val origin: ExpenseOrigin,
    val supplierName: String? = null,
    val supplierOrganizationId: UUID? = null,
    val farmId: UUID? = null,
    val parcelId: UUID? = null,
    val campaignId: UUID? = null,
    val activityId: UUID? = null,
    val invoiceNumber: String? = null,
    val lines: List<PurchaseLine> = emptyList(),
    val notes: String? = null,
)

data class ExpenseDraft(
    val expenseDate: LocalDate,
    val concept: String,
    val category: ExpenseCategory,
    val amountMinor: Long,
    val currency: String = "EUR",
    val supplierOrganizationId: UUID? = null,
    /** Free text when the supplier is not (yet) a saved organization. */
    val supplierText: String? = null,
    val farmId: UUID? = null,
    val parcelId: UUID? = null,
    val campaignId: UUID? = null,
    val activityId: UUID? = null,
    val invoiceNumber: String? = null,
    val lines: List<PurchaseLine> = emptyList(),
    val notes: String? = null,
)

/** Derived, never stored: posted money only. */
data class ExpenseSummary(
    val totalMinor: Long,
    val currency: String,
    val byCategory: Map<ExpenseCategory, Long>,
    val postedCount: Int,
    val draftCount: Int,
) {
    companion object {
        fun of(expenses: List<Expense>, currency: String = "EUR"): ExpenseSummary {
            val posted = expenses.filter { it.status == ExpenseStatus.POSTED && it.currency == currency }
            return ExpenseSummary(
                totalMinor = posted.sumOf { it.amountMinor },
                currency = currency,
                byCategory = posted.groupBy { it.category }
                    .mapValues { (_, rows) -> rows.sumOf { it.amountMinor } },
                postedCount = posted.size,
                draftCount = expenses.count { it.status == ExpenseStatus.DRAFT },
            )
        }
    }
}

interface ExpenseRepository {
    fun observeAll(): Flow<List<Expense>>

    fun observe(id: UUID): Flow<Expense?>

    fun observeForActivity(activityId: UUID): Flow<List<Expense>>

    /** A person entered this amount: it is posted immediately. */
    suspend fun create(draft: ExpenseDraft): AppResult<UUID>

    suspend fun update(id: UUID, draft: ExpenseDraft): AppResult<Unit>

    /** The explicit human confirmation that turns a DRAFT into counted money. */
    suspend fun post(id: UUID): AppResult<Unit>

    suspend fun delete(id: UUID): AppResult<Unit>
}
