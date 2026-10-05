package com.isivoltpro.maginaolivo

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.isivoltpro.maginaolivo.domain.expense.DayCostKind
import com.isivoltpro.maginaolivo.domain.expense.DayCostQuestion
import com.isivoltpro.maginaolivo.domain.expense.DayCostRole
import com.isivoltpro.maginaolivo.domain.expense.ExpenseCategory
import com.isivoltpro.maginaolivo.feature.expenses.ExpenseEditor
import com.isivoltpro.maginaolivo.feature.expenses.ExpenseForm
import com.isivoltpro.maginaolivo.feature.expenses.ExpenseFormErrors
import com.isivoltpro.maginaolivo.feature.expenses.RelationOptions
import com.isivoltpro.maginaolivo.ui.theme.MaginaOlivoTheme
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * #475 (owner audit on #583): the Gasto editor asks «Se añade / Sustituye» only from the day as it
 * is — never an option that would then fail on Guardar.
 */
class ExpenseDayRoleEditorTest {
    @get:Rule val rule = createComposeRule()

    private val day = UUID.randomUUID()

    @Test fun withoutACalculationOfThatKindNothingIsAsked() {
        show(form(ExpenseCategory.MACHINERY)) { _, _ -> null }
        rule.onNodeWithTag("expense-role-ADDITIVE").assertDoesNotExist()
        rule.onNodeWithTag("expense-role-REPLACEMENT").assertDoesNotExist()
    }

    @Test fun withACalculationBothAnswersAreOffered() {
        var saved: ExpenseForm? = null
        show(form(ExpenseCategory.LABOR), onSave = { saved = it }) { _, _ -> DayCostQuestion(DayCostKind.LABOUR, canReplace = true) }
        rule.onNodeWithTag("expense-role-ADDITIVE").assertExists()
        rule.onNodeWithTag("expense-role-REPLACEMENT").performScrollTo().performClick()
        rule.onNodeWithTag("save-expense").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(DayCostRole.REPLACEMENT, saved?.dayCostRole) }
    }

    @Test fun withJornalesPaidItCanOnlyAdd() {
        show(form(ExpenseCategory.LABOR)) { _, _ -> DayCostQuestion(DayCostKind.LABOUR, canReplace = false) }
        rule.onNodeWithTag("expense-role-ADDITIVE").assertExists()
        rule.onNodeWithTag("expense-role-REPLACEMENT").assertDoesNotExist()
        rule.onNodeWithTag("expense-role-labour-paid").assertExists()
    }

    @Test fun aCostThatAlreadyReplacesCanBeTakenBackToAdding() {
        var saved: ExpenseForm? = null
        show(form(ExpenseCategory.MACHINERY, DayCostRole.REPLACEMENT), onSave = { saved = it }) { _, _ ->
            DayCostQuestion(DayCostKind.EQUIPMENT, canReplace = true)
        }
        rule.onNodeWithTag("expense-role-REPLACEMENT").assertExists()
        rule.onNodeWithTag("expense-role-ADDITIVE").performScrollTo().performClick()
        rule.onNodeWithTag("save-expense").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(DayCostRole.ADDITIVE, saved?.dayCostRole) }
    }

    private fun form(category: ExpenseCategory, role: DayCostRole = DayCostRole.ADDITIVE) = ExpenseForm(
        date = "2026-11-24", amount = "60", concept = "Cuadrilla", category = category,
        harvestId = day, dayCostRole = role,
    )

    private fun show(
        initial: ExpenseForm,
        onSave: (ExpenseForm) -> Unit = {},
        question: suspend (UUID, ExpenseCategory) -> DayCostQuestion?,
    ) {
        rule.setContent {
            MaginaOlivoTheme {
                ExpenseEditor(
                    title = "Editar gasto", initial = initial, options = RelationOptions(), errors = ExpenseFormErrors(),
                    isSaving = false, saveText = "Guardar cambios", onFarmSelected = {}, onSave = onSave, onCancel = {},
                    dayCostQuestion = question,
                )
            }
        }
    }
}
