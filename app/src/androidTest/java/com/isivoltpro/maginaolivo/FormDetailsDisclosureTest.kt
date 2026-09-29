package com.isivoltpro.maginaolivo

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.isivoltpro.maginaolivo.feature.expenses.ExpenseEditor
import com.isivoltpro.maginaolivo.feature.expenses.ExpenseForm
import com.isivoltpro.maginaolivo.feature.expenses.ExpenseFormErrors
import com.isivoltpro.maginaolivo.feature.expenses.RelationOptions
import com.isivoltpro.maginaolivo.ui.theme.MaginaOlivoTheme
import org.junit.Rule
import org.junit.Test

/**
 * CR-011 §24: a new Gasto shows only what is needed; the rest waits under «Más detalles»,
 * and opens by itself when it already holds something so nothing is ever hidden.
 */
class FormDetailsDisclosureTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun aNewExpenseKeepsTheRarelyUsedFieldsUnderMoreDetails() {
        show(ExpenseForm(date = "2026-11-24"))
        composeRule.onNodeWithText("Notas", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithTag("add-purchase-line").assertDoesNotExist()
        composeRule.onNodeWithTag("expense-more-details").performScrollTo().performClick()
        composeRule.onNodeWithText("Notas", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("add-purchase-line").assertExists()
        composeRule.onNodeWithTag("expense-more-details").assertDoesNotExist()
    }

    @Test fun anExpenseThatAlreadyHasNotesShowsThem() {
        show(ExpenseForm(date = "2026-11-24", notes = "Pagado en efectivo"))
        composeRule.onNodeWithTag("expense-more-details").assertDoesNotExist()
        composeRule.onNodeWithText("Pagado en efectivo", useUnmergedTree = true).assertExists()
    }

    private fun show(form: ExpenseForm) {
        composeRule.setContent {
            MaginaOlivoTheme {
                ExpenseEditor(
                    title = "Nuevo gasto",
                    initial = form,
                    options = RelationOptions(),
                    errors = ExpenseFormErrors(),
                    isSaving = false,
                    saveText = "Guardar gasto",
                    onFarmSelected = {},
                    onSave = {},
                    onCancel = {},
                )
            }
        }
    }
}
