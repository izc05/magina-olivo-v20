package com.isivoltpro.maginaolivo.feature.expenses

import com.isivoltpro.maginaolivo.core.common.AppError
import com.isivoltpro.maginaolivo.domain.expense.ExpenseCategory
import com.isivoltpro.maginaolivo.domain.ocr.DocumentExtraction
import com.isivoltpro.maginaolivo.domain.ocr.DocumentType
import com.isivoltpro.maginaolivo.domain.ocr.OcrStatus
import com.isivoltpro.maginaolivo.domain.ocr.PurchaseProposal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ExpenseFormTest {
    @Test
    fun aCompleteFormBecomesADraft() {
        val (draft, errors) = ExpenseForm(
            date = "2026-03-10",
            amount = "72,60",
            concept = "Abono",
            category = ExpenseCategory.PRODUCTS,
            lines = listOf(LineForm("NPK", "10", "sacos", "60"), LineForm()),
        ).toDraft()
        assertNotNull(draft)
        assertEquals(7_260L, draft!!.amountMinor)
        assertEquals(LocalDate.of(2026, 3, 10), draft.expenseDate)
        assertEquals(1, draft.lines.size)
        assertEquals(6_000L, draft.lines.single().lineTotalMinor)
        assertEquals(10.0, draft.lines.single().quantity!!, 0.0)
        assertEquals(true, errors.isEmpty)
    }

    @Test
    fun anUnreadableAmountIsAnErrorNeverZero() {
        val (draft, errors) = ExpenseForm(date = "2026-03-10", amount = "setenta", concept = "Abono").toDraft()
        assertNull(draft)
        assertEquals("Escribe un importe como 65 o 65,50", errors.amount)
    }

    @Test
    fun aPostedExpenseNeedsAnAmountButAReviewedDraftMayNot() {
        val form = ExpenseForm(date = "2026-03-10", amount = "", concept = "Ticket")
        assertEquals("Escribe el importe", form.toDraft().second.amount)
        assertEquals(0L, form.toDraft(requireAmount = false).first!!.amountMinor)
    }

    @Test
    fun missingDateConceptAndBadLinesAreAllReported() {
        val (_, errors) = ExpenseForm(amount = "5", lines = listOf(LineForm("Cobre", quantity = "dos"))).toDraft()
        assertNotNull(errors.date)
        assertNotNull(errors.concept)
        assertNotNull(errors.lines)
    }

    @Test
    fun aReviewFormIsPrefilledFromTheProposalAndStaysEditable() {
        val extraction = DocumentExtraction(
            id = UUID.randomUUID(),
            attachmentId = UUID.randomUUID(),
            documentType = DocumentType.FERTILIZER_INVOICE,
            status = OcrStatus.EXTRACTED,
            engine = "test",
            rawText = "…",
            proposal = PurchaseProposal(
                supplierName = "Suministros Mágina",
                invoiceNumber = "F-1",
                invoiceDate = LocalDate.of(2026, 3, 10),
                totalMinor = 7_260,
            ),
            expenseId = null,
            createdAt = Instant.EPOCH,
            reviewedAt = null,
        )
        val form = extraction.toReviewForm()
        assertEquals("2026-03-10", form.date)
        assertEquals("72,60", form.amount)
        assertEquals("Suministros Mágina", form.supplierText)
        assertEquals("F-1", form.invoiceNumber)
        assertEquals(ExpenseCategory.PRODUCTS, form.category)
    }

    @Test
    fun errorsAreFarmerReadable() {
        assertEquals("La parcela elegida no pertenece a esa finca", expenseErrorMessage(AppError.Validation("parcelId", "not_in_farm")))
        assertEquals("Ya existe una organización con ese nombre", expenseErrorMessage(AppError.Conflict("duplicate_organization")))
        assertEquals("Este documento ya se revisó", expenseErrorMessage(AppError.Conflict("already_confirmed")))
    }
}
