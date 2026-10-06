package com.isivoltpro.maginaolivo.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.core.dispatchers.AppDispatchers
import com.isivoltpro.maginaolivo.core.id.IdGenerator
import com.isivoltpro.maginaolivo.core.time.AppClock
import com.isivoltpro.maginaolivo.data.local.entity.CampaignEntity
import com.isivoltpro.maginaolivo.data.local.entity.CampaignParcelSnapshotEntity
import com.isivoltpro.maginaolivo.data.local.entity.FarmEntity
import com.isivoltpro.maginaolivo.data.local.entity.LocalMetadata
import com.isivoltpro.maginaolivo.data.local.entity.ParcelEntity
import com.isivoltpro.maginaolivo.data.local.entity.WorkspaceEntity
import com.isivoltpro.maginaolivo.data.local.model.CampaignStatus
import com.isivoltpro.maginaolivo.data.repository.AndroidAttachmentFileStore
import com.isivoltpro.maginaolivo.data.repository.OfflineFirstAttachmentRepository
import com.isivoltpro.maginaolivo.data.repository.OfflineFirstDeliveryRepository
import com.isivoltpro.maginaolivo.data.repository.OfflineFirstExpenseRepository
import com.isivoltpro.maginaolivo.data.repository.OfflineFirstHarvestRepository
import com.isivoltpro.maginaolivo.data.repository.OfflineFirstLabourRepository
import com.isivoltpro.maginaolivo.domain.attachment.AttachmentOwner
import com.isivoltpro.maginaolivo.domain.attachment.AttachmentOwnerType
import com.isivoltpro.maginaolivo.domain.campaign.Campaign
import com.isivoltpro.maginaolivo.domain.delivery.DeliveryDraft
import com.isivoltpro.maginaolivo.domain.delivery.DeliveryShareInput
import com.isivoltpro.maginaolivo.domain.delivery.PesadaOrigin
import com.isivoltpro.maginaolivo.domain.expense.ExpenseDraft
import com.isivoltpro.maginaolivo.domain.expense.JornadaExpenseKind
import com.isivoltpro.maginaolivo.domain.labour.CrewDraft
import com.isivoltpro.maginaolivo.domain.labour.LabourUnit
import com.isivoltpro.maginaolivo.domain.notebook.CampaignNotebook
import com.isivoltpro.maginaolivo.domain.workspace.WorkspaceRepository
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * #254 (254-E) — the whole recolección of one day, as the owner described it:
 * Campanil · 2.390 kg · Árbol/vuelo · Bedmarense · vale + foto → Jornada → 5 jornales →
 * gasto → totales. Everything local; it survives an app restart and every total is read
 * from its own ledger, never typed twice.
 */
@RunWith(AndroidJUnit4::class)
class RecollectionFlowContractTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val workspaceId = UUID.fromString("10000000-0000-0000-0000-0000000254e1")
    private val farmId = UUID.fromString("20000000-0000-0000-0000-0000000254e1")
    private val campaignId = UUID.fromString("40000000-0000-0000-0000-0000000254e1")
    private val parcelId = UUID.fromString("30000000-0000-0000-0000-0000000254e1")
    private val now = Instant.parse("2026-11-24T19:00:00Z")
    private val day = LocalDate.parse("2026-11-24")
    // Under the FileProvider's cache "camera/" root (res/xml/attachment_paths.xml).
    private val photos = File(File(context.cacheDir, "camera"), "recollection-flow")

    private lateinit var db: MaginaOlivoDatabase
    private lateinit var deliveries: OfflineFirstDeliveryRepository
    private lateinit var harvests: OfflineFirstHarvestRepository
    private lateinit var labour: OfflineFirstLabourRepository
    private lateinit var expenses: OfflineFirstExpenseRepository
    private lateinit var attachments: OfflineFirstAttachmentRepository

    @Before
    fun before() = runBlocking {
        context.deleteDatabase(DB)
        open()
        seed()
    }

    @After
    fun after() {
        db.close()
        context.deleteDatabase(DB)
        photos.deleteRecursively()
    }

    @Test
    fun onePesadaWithItsTicketPhotoOpensTheJornadaAndEveryTotalComesFromItsLedger() = runBlocking {
        // 1. The Pesada: 2.390 kg of tree olives to Bedmarense, ticket V-1; it creates its day (CR-010).
        val pesada = ok(
            deliveries.create(
                DeliveryDraft(
                    farmId = farmId,
                    deliveryDate = day,
                    destinationOrganizationId = null,
                    destinationName = "Bedmarense",
                    netGrams = 2_390_000,
                    shares = listOf(DeliveryShareInput(parcelId, null)),
                    ticketNumber = "V-1",
                    deliveryTime = LocalTime.of(12, 30),
                    origin = PesadaOrigin.TREE,
                ),
            ),
        )
        val jornada = deliveries.observe(pesada).first()!!.harvestId!!
        // 2. The photo of the ticket, attached to the Pesada itself.
        ok(attachments.attach(AttachmentOwner(AttachmentOwnerType.DELIVERY, pesada), photo("vale-V-1.jpg")))
        // 3. Five people, a whole day each.
        val crew = listOf("Antonio Ruiz", "Paco Martos", "Mari Cruz", "Juan Pérez", "Manuel Gómez").map { ok(labour.addWorker(it)) }
        assertEquals(5, ok(labour.recordCrew(CrewDraft(jornada, crew, LabourUnit.FULL_DAY))))
        // 4. The day's diesel, as a cost of this Jornada in the one Expense ledger.
        ok(
            expenses.create(
                ExpenseDraft(
                    expenseDate = day,
                    concept = JornadaExpenseKind.DIESEL.label,
                    category = JornadaExpenseKind.DIESEL.category,
                    amountMinor = 5_000,
                    farmId = farmId,
                    harvestId = jornada,
                ),
            ),
        )

        // The phone is switched off and on: nothing lives only in memory.
        db.close()
        open()

        val saved = deliveries.observe(pesada).first()!!
        assertEquals(PesadaOrigin.TREE, saved.origin)
        assertEquals("Bedmarense", saved.destinationName)
        assertEquals("V-1", saved.ticketNumber)
        assertEquals(1, attachments.observeForOwner(AttachmentOwner(AttachmentOwnerType.DELIVERY, pesada)).first().size)

        val notebook = CampaignNotebook.project(
            campaign(),
            activities = emptyList(),
            harvests = harvests.observeForCampaign(campaignId).first(),
            deliveries = deliveries.observeForCampaign(campaignId).first(),
            expenses = expenses.observeAll().first(),
            labour = labour.observeForCampaign(campaignId).first(),
        )
        // One Jornada, whose kilos are exactly its Pesada's.
        assertEquals(1, notebook.harvests.size)
        assertEquals(2_390_000L, notebook.harvests.single().totalGrams)
        assertEquals(1, notebook.pesadaCount(jornada))
        assertEquals(2_390_000L, notebook.deliverySummary.deliveredGrams)
        // Five whole days of labour, one row per person.
        assertEquals(5, notebook.labourFor(jornada).fullDays)
        assertEquals(5, notebook.labourByWorker.size)
        assertEquals(0, notebook.unnamedLabour.people)
        // The cost is counted once: in the Jornada, the recolección and the campaign.
        assertEquals(5_000L, notebook.jornadaCost(jornada).single().amount())
        assertEquals(5_000L, notebook.recollectionByCurrency.single().amount())
        assertEquals(5_000L, notebook.expensesByCurrency.single().amount())
        // No yield is invented before the analysis arrives.
        assertEquals(null, notebook.deliverySummary.fatYield)
        assertEquals(1, notebook.pendingYieldCount)
    }

    private fun campaign() = Campaign(
        id = campaignId, workspaceId = workspaceId, farmId = farmId, name = "2026/27",
        startDate = LocalDate.parse("2026-10-01"), endDate = null, status = CampaignStatus.HARVEST,
        notes = null, snapshots = emptyList(), version = 1,
    )

    private fun photo(name: String): String {
        photos.mkdirs()
        val bitmap = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(62, 90, 50)) }
        val bytes = ByteArrayOutputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)
            bitmap.recycle()
            output.toByteArray()
        }
        val file = File(photos, name).apply { writeBytes(bytes) }
        return FileProvider.getUriForFile(context, "${context.packageName}.attachments", file).toString()
    }

    private fun open() {
        db = MaginaOlivoDatabase.create(context, DB)
        val clock = FixedClock(now)
        val workspaces = object : WorkspaceRepository {
            override suspend fun ensureLocalWorkspace(): AppResult<UUID> = AppResult.Success(workspaceId)
        }
        deliveries = OfflineFirstDeliveryRepository(db, clock, RandomIds, TestDispatchers) { ZoneOffset.UTC }
        harvests = OfflineFirstHarvestRepository(db, clock, RandomIds, TestDispatchers) { ZoneOffset.UTC }
        labour = OfflineFirstLabourRepository(db, workspaces, clock, RandomIds, TestDispatchers)
        expenses = OfflineFirstExpenseRepository(db, workspaces, clock, RandomIds, TestDispatchers)
        attachments = OfflineFirstAttachmentRepository(db, AndroidAttachmentFileStore(context), workspaces, clock, RandomIds, TestDispatchers)
    }

    private suspend fun seed() {
        val meta = LocalMetadata(now, now)
        db.workspaceDao().upsert(WorkspaceEntity(workspaceId, "Olivar", UUID.randomUUID(), "ES", "Europe/Madrid", "es-ES", "EUR", meta))
        db.farmDao().upsert(FarmEntity(farmId, workspaceId, "Campanil", metadata = meta))
        db.parcelDao().upsert(ParcelEntity(parcelId, workspaceId, "Pol. 14 · Parc. 104", source = "MANUAL", metadata = meta))
        db.campaignDao().upsert(
            CampaignEntity(campaignId, workspaceId, farmId, "2026/27", LocalDate.parse("2026-10-01"), null, CampaignStatus.HARVEST, metadata = meta),
        )
        db.campaignDao().upsertSnapshots(
            listOf(CampaignParcelSnapshotEntity(UUID.randomUUID(), workspaceId, campaignId, parcelId, farmId, "Campanil", "Pol. 14 · Parc. 104", metadata = meta)),
        )
    }

    private fun <T> ok(result: AppResult<T>): T = when (result) {
        is AppResult.Success -> result.value
        is AppResult.Failure -> throw AssertionError("Expected success but was ${result.error}")
    }

    private data class FixedClock(val value: Instant) : AppClock {
        override fun nowInstant() = value
        override fun today(zoneId: ZoneId) = LocalDate.ofInstant(value, zoneId)
    }

    private object RandomIds : IdGenerator {
        override fun newId(): UUID = UUID.randomUUID()
    }

    private object TestDispatchers : AppDispatchers {
        override val default: CoroutineDispatcher = Dispatchers.Unconfined
        override val io: CoroutineDispatcher = Dispatchers.Unconfined
        override val main: CoroutineDispatcher = Dispatchers.Unconfined
    }

    private companion object {
        const val DB = "recollection-flow-contract-test.db"
    }
}
