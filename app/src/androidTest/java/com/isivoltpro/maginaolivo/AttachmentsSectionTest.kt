package com.isivoltpro.maginaolivo

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.isivoltpro.maginaolivo.domain.attachment.Attachment
import com.isivoltpro.maginaolivo.domain.attachment.AttachmentKind
import com.isivoltpro.maginaolivo.domain.attachment.AttachmentOwner
import com.isivoltpro.maginaolivo.domain.attachment.AttachmentOwnerType
import com.isivoltpro.maginaolivo.domain.attachment.AttachmentUploadState
import com.isivoltpro.maginaolivo.feature.attachments.AttachmentsSection
import com.isivoltpro.maginaolivo.feature.attachments.AttachmentsUiState
import com.isivoltpro.maginaolivo.ui.theme.MaginaOlivoTheme
import java.time.Instant
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AttachmentsSectionTest {
    @get:Rule val composeRule = createComposeRule()

    private val owner = AttachmentOwner(AttachmentOwnerType.PARCEL, UUID.fromString("30000000-0000-0000-0000-0000000000a1"))

    @Test
    fun anEmptyOwnerSaysSoAndOffersBothWaysToAdd() {
        show(AttachmentsUiState(isLoading = false))

        composeRule.onNodeWithText("Sin documentos ni fotos").assertIsDisplayed()
        composeRule.onNodeWithTag("add-attachment").performClick()
        composeRule.onNodeWithTag("attachment-camera").assertIsDisplayed()
        composeRule.onNodeWithTag("attachment-picker").assertIsDisplayed()
    }

    @Test
    fun aFailedUploadIsShownAsKeptOnTheDeviceNotAsLost() {
        show(
            AttachmentsUiState(
                isLoading = false,
                attachments = listOf(
                    attachment("poda.jpg", AttachmentUploadState.FAILED),
                    attachment("analisis.pdf", AttachmentUploadState.PENDING, AttachmentKind.PDF),
                ),
            ),
        )

        composeRule.onNodeWithText("poda.jpg").assertIsDisplayed()
        composeRule.onNodeWithText("Subida fallida · guardado aquí").assertIsDisplayed()
        composeRule.onNodeWithText("analisis.pdf").assertIsDisplayed()
        composeRule.onNodeWithText("Guardado en el dispositivo").assertIsDisplayed()
        assertEquals(2, composeRule.onAllNodesWithTag("attachment-row").fetchSemanticsNodes().size)
    }

    @Test
    fun removalAsksForConfirmationFirst() {
        val attachment = attachment("borrar.jpg", AttachmentUploadState.PENDING)
        var removed: UUID? = null
        show(AttachmentsUiState(isLoading = false, attachments = listOf(attachment)), onRemove = { removed = it })

        composeRule.onNodeWithTag("remove-attachment").performClick()
        assertNull(removed)
        composeRule.onNodeWithText("Eliminar adjunto").assertIsDisplayed()

        // The row keeps its own "Eliminar"; the confirmation is the one inside the sheet.
        composeRule
            .onNode(hasText("Eliminar") and hasAnyAncestor(hasTestTag("attachment-remove-sheet")))
            .performClick()
        composeRule.waitUntil(5_000) { removed != null }
        assertEquals(attachment.id, removed)
    }

    private fun show(
        state: AttachmentsUiState,
        onRemove: (UUID) -> Unit = {},
    ) {
        composeRule.setContent {
            MaginaOlivoTheme {
                Column {
                    AttachmentsSection(
                        state = state,
                        onPicked = {},
                        onRemove = onRemove,
                        onProblem = {},
                    )
                }
            }
        }
    }

    private fun attachment(
        name: String,
        uploadState: AttachmentUploadState,
        kind: AttachmentKind = AttachmentKind.PHOTO,
    ) = Attachment(
        id = UUID.randomUUID(),
        owner = owner,
        kind = kind,
        mimeType = if (kind == AttachmentKind.PDF) "application/pdf" else "image/jpeg",
        displayName = name,
        sizeBytes = 2_048,
        sha256 = null,
        localUri = "file:///nonexistent/$name",
        thumbnailUri = null,
        uploadState = uploadState,
        isAvailableLocally = true,
        createdAt = Instant.parse("2026-09-23T08:00:00Z"),
    )
}
