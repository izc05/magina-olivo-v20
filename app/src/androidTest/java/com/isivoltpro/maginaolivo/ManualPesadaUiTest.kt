package com.isivoltpro.maginaolivo

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.isivoltpro.maginaolivo.data.local.model.CampaignStatus
import com.isivoltpro.maginaolivo.domain.delivery.PesadaOrigin
import com.isivoltpro.maginaolivo.domain.harvest.HarvestContext
import com.isivoltpro.maginaolivo.domain.harvest.HarvestParcelOption
import com.isivoltpro.maginaolivo.feature.deliveries.DeliveriesScreen
import com.isivoltpro.maginaolivo.feature.deliveries.DeliveriesUiState
import com.isivoltpro.maginaolivo.feature.deliveries.DeliveryEditor
import com.isivoltpro.maginaolivo.feature.deliveries.DeliveryForm
import com.isivoltpro.maginaolivo.feature.deliveries.DeliveryFormErrors
import com.isivoltpro.maginaolivo.feature.deliveries.RECEIPT_NOT_SAVED
import com.isivoltpro.maginaolivo.feature.expenses.ExpensesScreen
import com.isivoltpro.maginaolivo.feature.expenses.ExpensesUiState
import com.isivoltpro.maginaolivo.ui.theme.MaginaOlivoTheme
import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/** #342 — Pesada 1.0 is typed by the farmer; the receipt photo is optional evidence; OCR is dormant. */
class ManualPesadaUiTest {
    @get:Rule val rule = createComposeRule()
    private val today = LocalDate.of(2026, 11, 18)
    private val north = HarvestParcelOption(UUID.randomUUID(), "Norte")
    private val context = HarvestContext(
        UUID.randomUUID(), "La Solana", UUID.randomUUID(), "2026/27", CampaignStatus.HARVEST, today.minusMonths(1), listOf(north),
    )
    private val typed = DeliveryForm(
        farmId = context.farmId, date = today.toString(), destinationText = "Cooperativa San Isidro",
        net = "2.850", parcelIds = listOf(north.parcelId), origin = PesadaOrigin.TREE,
    )

    @Test fun pesadasOfferNoTicketReadingOnlyAManualPesada() {
        rule.setContent {
            MaginaOlivoTheme {
                DeliveriesScreen(
                    state = DeliveriesUiState(isLoading = false, contexts = listOf(context)),
                    today = today, onCreate = {}, onProblem = {}, onDeliverySelected = {}, onTicketSelected = {},
                )
            }
        }
        rule.onNodeWithTag("add-delivery").assertExists()
        rule.onNodeWithTag("read-ticket").assertDoesNotExist()
        rule.onNodeWithText("Leer vale").assertDoesNotExist()
    }

    @Test fun failedCampaignReadIsNotShownAsNoCampaignAndCanRetry() {
        var retried = false
        rule.setContent {
            MaginaOlivoTheme {
                DeliveriesScreen(
                    state = DeliveriesUiState(isLoading = false, contextsLoaded = true, contextsReadFailed = true),
                    today = today, onCreate = {}, onProblem = {}, onRetryContexts = { retried = true },
                    onDeliverySelected = {}, onTicketSelected = {},
                )
            }
        }
        rule.onNodeWithTag("delivery-context-error").assertExists()
        rule.onNodeWithTag("delivery-no-campaign").assertDoesNotExist()
        rule.onNodeWithTag("delivery-context-retry").performClick()
        rule.runOnIdle { assertEquals(true, retried) }
    }

    @Test fun aSuccessfulEmptyCampaignReadShowsTheRealNoCampaignState() {
        rule.setContent {
            MaginaOlivoTheme {
                DeliveriesScreen(
                    state = DeliveriesUiState(isLoading = false, contextsLoaded = true),
                    today = today, onCreate = {}, onProblem = {}, onDeliverySelected = {}, onTicketSelected = {},
                )
            }
        }
        rule.onNodeWithTag("delivery-no-campaign").assertExists()
        rule.onNodeWithTag("delivery-context-error").assertDoesNotExist()
    }

    @Test fun nuevaPesadaOffersAnOptionalReceiptPhotoAndNoReading() {
        var asked: DeliveryForm? = null
        editor(typed, onAddReceipt = { asked = it })
        rule.onNodeWithTag("delivery-read-ticket").assertDoesNotExist()
        rule.onNodeWithText("Añadir vale y leer datos").assertDoesNotExist()
        rule.onNodeWithTag("delivery-add-receipt").performScrollTo().performClick()
        // What was typed travels with the request: adding the photo loses nothing.
        rule.runOnIdle { assertEquals(typed, asked) }
    }

    @Test fun aPesadaIsSavedWithoutPhotoOrTicketNumber() {
        var saved: DeliveryForm? = null
        editor(typed, onSave = { saved = it })
        rule.onNodeWithTag("save-delivery").performScrollTo().performClick()
        rule.runOnIdle {
            assertNotNull(saved)
            assertNull(saved?.receiptUri)
            assertEquals("", saved?.ticketNumber)
            assertEquals("2.850", saved?.net)
        }
    }

    @Test fun aChosenReceiptIsShownCanBeRemovedAndNeverChangesTheKilos() {
        var saved: DeliveryForm? = null
        editor(typed.copy(receiptUri = "content://receipt/1"), onSave = { saved = it })
        rule.onNodeWithTag("delivery-receipt-added").performScrollTo().assertExists()
        rule.onNodeWithTag("save-delivery").performScrollTo().performClick()
        rule.runOnIdle { assertEquals("content://receipt/1", saved?.receiptUri); assertEquals("2.850", saved?.net) }
        rule.onNodeWithTag("delivery-remove-receipt").performScrollTo().performClick()
        rule.onNodeWithTag("delivery-add-receipt").performScrollTo().assertExists()
        rule.onNodeWithTag("save-delivery").performScrollTo().performClick()
        rule.runOnIdle { assertNull(saved?.receiptUri); assertEquals("2.850", saved?.net) }
    }

    @Test fun aReceiptThatCouldNotBeKeptIsSaidInsideTheRepeatedEntrySheet() {
        rule.setContent {
            MaginaOlivoTheme {
                DeliveriesScreen(
                    state = DeliveriesUiState(
                        isLoading = false, contexts = listOf(context), nextForm = typed.copy(net = ""), nextFormGeneration = 1,
                        error = RECEIPT_NOT_SAVED,
                    ),
                    today = today, onCreate = {}, onProblem = {}, onDeliverySelected = {}, onTicketSelected = {},
                    onCreateAndAddAnother = {}, presetFarmId = context.farmId,
                )
            }
        }
        rule.onNodeWithTag("delivery-saved-next").assertExists()
        rule.onNodeWithTag("delivery-sheet-warning").assertTextEquals(RECEIPT_NOT_SAVED)
    }

    @Test fun gastosOfferNoInvoiceReading() {
        rule.setContent {
            MaginaOlivoTheme {
                ExpensesScreen(ExpensesUiState(isLoading = false), today, {}, {}, { _, _ -> }, {}, {}, {}, {})
            }
        }
        rule.onNodeWithTag("add-expense").assertExists()
        rule.onNodeWithTag("upload-document").assertDoesNotExist()
        rule.onNodeWithText("Ticket o factura").assertDoesNotExist()
    }

    private fun editor(initial: DeliveryForm, onSave: (DeliveryForm) -> Unit = {}, onAddReceipt: (DeliveryForm) -> Unit = {}) {
        rule.setContent {
            MaginaOlivoTheme {
                DeliveryEditor(
                    title = "Registrar pesada", initial = initial, contexts = listOf(context), destinations = emptyList(),
                    errors = DeliveryFormErrors(), isSaving = false, saveText = "Guardar pesada",
                    onSave = onSave, onCancel = {}, onAddReceipt = onAddReceipt,
                )
            }
        }
    }
}
