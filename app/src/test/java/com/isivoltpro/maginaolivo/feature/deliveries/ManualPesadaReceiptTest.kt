package com.isivoltpro.maginaolivo.feature.deliveries

import com.isivoltpro.maginaolivo.core.common.AppError
import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.core.time.AppClock
import com.isivoltpro.maginaolivo.domain.attachment.AttachmentOwner
import com.isivoltpro.maginaolivo.domain.attachment.AttachmentOwnerType
import com.isivoltpro.maginaolivo.domain.attachment.AttachmentRepository
import com.isivoltpro.maginaolivo.domain.delivery.DeliveryDraft
import com.isivoltpro.maginaolivo.domain.delivery.DeliveryRepository
import com.isivoltpro.maginaolivo.domain.delivery.DeliveryShareInput
import com.isivoltpro.maginaolivo.domain.delivery.PesadaOrigin
import com.isivoltpro.maginaolivo.domain.ocr.DocumentOcrRepository
import com.isivoltpro.maginaolivo.domain.organization.OrganizationRepository
import java.lang.reflect.Proxy
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * #342 — Pesada 1.0: the kilos the farmer types are the canonical record. A receipt photo is
 * an optional attachment added after saving; it never changes or duplicates the Pesada.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ManualPesadaReceiptTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val farm = UUID.randomUUID()
    private val north = UUID.randomUUID()
    private val south = UUID.randomUUID()
    private val form = DeliveryForm(
        farmId = farm, date = "2026-11-18", destinationText = "Cooperativa San Isidro",
        net = "2.850", parcelIds = listOf(north, south), origin = PesadaOrigin.TREE,
    )

    private val created = mutableListOf<DeliveryDraft>()
    private val ids = mutableListOf<UUID>()
    private val attached = mutableListOf<Pair<AttachmentOwner, String>>()
    private var attachResult: AppResult<UUID> = AppResult.Success(UUID.randomUUID())

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun aPesadaIsSavedWithTypedKilosAndNoPhotoOrTicketNumber() = runTest(dispatcher) {
        val model = model()
        model.create(form)
        assertEquals(1, created.size)
        assertEquals(2_850_000L, created.single().netGrams)
        assertNull(created.single().ticketNumber)
        assertTrue(attached.isEmpty())
        assertEquals("Pesada guardada", model.state.value.message)
        assertNull(model.state.value.error)
    }

    @Test fun aReceiptIsAttachedToThatPesadaAndChangesNothingTyped() = runTest(dispatcher) {
        val model = model()
        model.create(form.copy(receiptUri = "content://receipt/1"))
        // Same draft as without a photo: the receipt never feeds kilos, ticket, date or cooperative.
        assertEquals(form.toDraft(TODAY).first, created.single())
        assertEquals(listOf(AttachmentOwner(AttachmentOwnerType.DELIVERY, ids.single()) to "content://receipt/1"), attached)
        assertEquals("Pesada guardada", model.state.value.message)
    }

    @Test fun aFailedPhotoKeepsTheSavedPesadaAndSaysSo() = runTest(dispatcher) {
        attachResult = AppResult.Failure(AppError.Storage("copy_attachment"))
        val model = model()
        model.create(form.copy(receiptUri = "content://receipt/broken"))
        assertEquals(1, created.size)
        assertEquals("Pesada guardada", model.state.value.message)
        assertEquals(RECEIPT_NOT_SAVED, model.state.value.error)
    }

    @Test fun saveAndAddAnotherKeepsOnlySafeContextAndNeverTheReceipt() = runTest(dispatcher) {
        val model = model()
        model.create(form.copy(receiptUri = "content://receipt/1", ticketNumber = "V-1"), again = true)
        val next = model.state.value.nextForm!!
        assertNull(next.receiptUri)
        assertEquals("", next.net)
        assertEquals("", next.ticketNumber)
        assertNull(next.origin)
        assertEquals(farm, next.farmId)
        assertEquals("2026-11-18", next.date)
        assertEquals("Cooperativa San Isidro", next.destinationText)
        assertEquals(listOf(north, south), next.parcelIds)
        // The second Pesada of the same day is its own record, with its own photo or none.
        model.create(next.copy(net = "1.200", origin = PesadaOrigin.GROUND))
        assertEquals(listOf(2_850_000L, 1_200_000L), created.map { it.netGrams })
        assertEquals(listOf(ids.first()), attached.map { it.first.id })
    }

    @Test fun anyCooperativeIsJustItsNameWithNoSpecialFormat() = runTest(dispatcher) {
        val model = model()
        listOf("Cooperativa San Isidro", "Almazara El Molino", "Oleícola de Jaén").forEach { name ->
            model.create(form.copy(destinationText = name))
        }
        assertEquals(listOf("Cooperativa San Isidro", "Almazara El Molino", "Oleícola de Jaén"), created.map { it.destinationName })
        assertTrue(created.all { it.netGrams == 2_850_000L && it.ticketNumber == null })
        assertEquals(listOf(DeliveryShareInput(north, null), DeliveryShareInput(south, null)), created.first().shares)
    }

    private fun model() = DeliveriesViewModel(
        deliveries = object : DeliveryRepository by unused() {
            override fun observeAll() = flowOf(emptyList<com.isivoltpro.maginaolivo.domain.delivery.Delivery>())
            override fun observeContexts() = flowOf(emptyList<com.isivoltpro.maginaolivo.domain.harvest.HarvestContext>())
            override suspend fun create(draft: DeliveryDraft): AppResult<UUID> {
                created += draft
                return AppResult.Success(UUID.randomUUID().also { ids += it })
            }
        },
        documents = object : DocumentOcrRepository by unused() {
            override fun observeOpen() = emptyFlow<List<com.isivoltpro.maginaolivo.domain.ocr.DocumentExtraction>>()
        },
        organizations = object : OrganizationRepository by unused() {
            override fun observeWithAnyRole(roles: Set<com.isivoltpro.maginaolivo.domain.organization.OrganizationRole>) =
                flowOf(emptyList<com.isivoltpro.maginaolivo.domain.organization.Organization>())
        },
        clock = object : AppClock {
            override fun nowInstant(): Instant = Instant.parse("2026-11-18T10:00:00Z")
            override fun today(zoneId: ZoneId): LocalDate = TODAY
        },
        attachments = object : AttachmentRepository by unused() {
            override suspend fun attach(owner: AttachmentOwner, sourceUri: String): AppResult<UUID> {
                attached += owner to sourceUri
                return attachResult
            }
        },
    )

    private inline fun <reified T : Any> unused(): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, _ -> error("Unexpected ${method.name}") } as T

    private companion object {
        val TODAY: LocalDate = LocalDate.of(2026, 11, 18)
    }
}
