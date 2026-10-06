package com.isivoltpro.maginaolivo.data.local

import android.content.Context
import android.graphics.Bitmap
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.isivoltpro.maginaolivo.core.common.AppError
import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.core.dispatchers.AppDispatchers
import com.isivoltpro.maginaolivo.core.id.IdGenerator
import com.isivoltpro.maginaolivo.core.time.AppClock
import com.isivoltpro.maginaolivo.data.local.entity.CampaignEntity
import com.isivoltpro.maginaolivo.data.local.entity.FarmEntity
import com.isivoltpro.maginaolivo.data.local.entity.FarmParcelMembershipEntity
import com.isivoltpro.maginaolivo.data.local.entity.HarvestEntity
import com.isivoltpro.maginaolivo.data.local.entity.LocalMetadata
import com.isivoltpro.maginaolivo.data.local.entity.ParcelEntity
import com.isivoltpro.maginaolivo.data.local.entity.WorkspaceEntity
import com.isivoltpro.maginaolivo.data.local.model.ActivityStatus
import com.isivoltpro.maginaolivo.data.local.model.CampaignStatus
import com.isivoltpro.maginaolivo.data.local.model.OutboxOperation
import com.isivoltpro.maginaolivo.data.local.model.SyncEntityType
import com.isivoltpro.maginaolivo.data.repository.AndroidAttachmentFileStore
import com.isivoltpro.maginaolivo.data.repository.JsonProposalCodec
import com.isivoltpro.maginaolivo.data.repository.OfflineFirstActivityRepository
import com.isivoltpro.maginaolivo.data.repository.OfflineFirstAttachmentRepository
import com.isivoltpro.maginaolivo.data.repository.OfflineFirstDocumentOcrRepository
import com.isivoltpro.maginaolivo.data.repository.OfflineFirstDayCostRepository
import com.isivoltpro.maginaolivo.data.repository.OfflineFirstExpenseRepository
import com.isivoltpro.maginaolivo.data.repository.OfflineFirstOrganizationRepository
import com.isivoltpro.maginaolivo.domain.activity.ActivityChanges
import com.isivoltpro.maginaolivo.domain.activity.ActivityCostRules
import com.isivoltpro.maginaolivo.domain.activity.ActivityType
import com.isivoltpro.maginaolivo.domain.activity.NewActivity
import com.isivoltpro.maginaolivo.domain.attachment.AttachmentOwner
import com.isivoltpro.maginaolivo.domain.attachment.AttachmentOwnerType
import com.isivoltpro.maginaolivo.domain.expense.ExpenseCategory
import com.isivoltpro.maginaolivo.domain.expense.ExpenseDraft
import com.isivoltpro.maginaolivo.domain.expense.ExpenseOrigin
import com.isivoltpro.maginaolivo.domain.expense.ExpenseStatus
import com.isivoltpro.maginaolivo.domain.expense.ExpenseSummary
import com.isivoltpro.maginaolivo.domain.expense.PurchaseLine
import com.isivoltpro.maginaolivo.domain.ocr.DocumentType
import com.isivoltpro.maginaolivo.domain.ocr.OcrEngine
import com.isivoltpro.maginaolivo.domain.ocr.OcrStatus
import com.isivoltpro.maginaolivo.domain.ocr.OcrText
import com.isivoltpro.maginaolivo.domain.organization.OrganizationDraft
import com.isivoltpro.maginaolivo.domain.organization.OrganizationRole
import com.isivoltpro.maginaolivo.domain.workspace.WorkspaceRepository
import com.isivoltpro.maginaolivo.feature.expenses.ExpenseForm
import com.isivoltpro.maginaolivo.feature.expenses.RelationOptions
import com.isivoltpro.maginaolivo.feature.expenses.toDraft
import com.isivoltpro.maginaolivo.feature.expenses.toForm
import com.isivoltpro.maginaolivo.feature.expenses.toReviewForm
import com.isivoltpro.maginaolivo.feature.expenses.withRecollectionPreselected
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Phase 12 — Expense ledger, organizations and generic document OCR.
 *
 * Gate 12: no monetary double counting; organization reuse works across contexts; OCR
 * cannot auto-post money or silently confirm extracted values.
 */
@RunWith(AndroidJUnit4::class)
class ExpenseLedgerContractTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val workspaceId = UUID.fromString("10000000-0000-0000-0000-0000000000f1")
    private val farmId = UUID.fromString("20000000-0000-0000-0000-0000000000f1")
    private val otherFarmId = UUID.fromString("20000000-0000-0000-0000-0000000000f2")
    private val parcelA = UUID.fromString("30000000-0000-0000-0000-0000000000f1")
    private val otherParcel = UUID.fromString("30000000-0000-0000-0000-0000000000f2")
    private val campaignId = UUID.fromString("40000000-0000-0000-0000-0000000000f1")
    private val date = LocalDate.parse("2026-03-10")
    private val now = Instant.parse("2026-09-23T08:00:00Z")
    private val sources = File(context.cacheDir, "camera")
    private val attachmentsRoot = File(context.filesDir, AndroidAttachmentFileStore.ROOT_DIRECTORY)

    private lateinit var db: MaginaOlivoDatabase
    private lateinit var expenses: OfflineFirstExpenseRepository
    private lateinit var activities: OfflineFirstActivityRepository
    private lateinit var organizations: OfflineFirstOrganizationRepository
    private lateinit var attachments: OfflineFirstAttachmentRepository
    private lateinit var documents: OfflineFirstDocumentOcrRepository
    private val engine = FakeEngine()

    @Before
    fun before() = runBlocking {
        context.deleteDatabase(DB)
        sources.deleteRecursively()
        attachmentsRoot.deleteRecursively()
        open()
        seed()
    }

    @After
    fun after() {
        db.close()
        context.deleteDatabase(DB)
        sources.deleteRecursively()
        attachmentsRoot.deleteRecursively()
    }

    // ------------------------------------------------------------ #441 a work's Parcels keep its Gastos

    private suspend fun secondParcel(): UUID {
        val meta = LocalMetadata(now, now)
        val parcelB = UUID.randomUUID()
        db.parcelDao().upsert(ParcelEntity(parcelB, workspaceId, "Parcela B", source = "MANUAL", metadata = meta))
        db.parcelDao().upsertMembership(FarmParcelMembershipEntity(UUID.randomUUID(), workspaceId, farmId, parcelB, now, metadata = meta))
        return parcelB
    }

    private fun assertParcelHasExpenses(result: AppResult<*>) {
        assertTrue("Expected activity_parcel_has_expenses but was $result",
            result is AppResult.Failure && (result.error as? AppError.Conflict)?.resource == ActivityCostRules.PARCEL_HAS_EXPENSES)
    }

    @Test
    fun expenseFeedNeverMixesWorkspaces() = runBlocking {
        val otherWorkspaceId = UUID.fromString("10000000-0000-0000-0000-0000000000f2")
        db.workspaceDao().upsert(
            WorkspaceEntity(otherWorkspaceId, "Otro olivar", UUID.randomUUID(), "ES", "Europe/Madrid", "es-ES", "EUR", LocalMetadata(now, now)),
        )
        val otherWorkspaces = object : WorkspaceRepository {
            override suspend fun ensureLocalWorkspace(): AppResult<UUID> = AppResult.Success(otherWorkspaceId)
        }
        val otherExpenses = OfflineFirstExpenseRepository(db, otherWorkspaces, FixedClock(now), RandomIds, TestDispatchers)

        val mine = ok(expenses.create(draft(500, concept = "Gasto A")))
        val theirs = ok(otherExpenses.create(draft(800, concept = "Gasto B")))

        assertEquals(listOf(mine), expenses.observeAll().first().map { it.id })
        assertEquals(500L, ExpenseSummary.of(expenses.observeAll().first(), "EUR").totalMinor)
        assertEquals(listOf(theirs), otherExpenses.observeAll().first().map { it.id })
        assertEquals(800L, ExpenseSummary.of(otherExpenses.observeAll().first(), "EUR").totalMinor)
    }

    @Test
    fun aParcelAGastoNamesIsNotDroppedFromTheWork() = runBlocking {
        val parcelB = secondParcel()
        val work = activity(costMinor = null)
        val gasto = ok(expenses.create(draft(2_000, farmId = farmId, parcelId = parcelA, activityId = work, concept = "Abono A")))
        val before = expenses.observe(gasto).first()!!

        assertParcelHasExpenses(activities.update(work, ActivityChanges(ActivityType.FERTILIZATION, date, "Abonado de primavera", setOf(parcelB))))
        assertEquals(before, expenses.observe(gasto).first())
        assertEquals(listOf(parcelA), activities.observe(work).first()!!.targets.map { it.parcelId })

        // Adding a Parcel while keeping A leaves the Gasto as it was.
        ok(activities.update(work, ActivityChanges(ActivityType.FERTILIZATION, date, "Abonado de primavera", setOf(parcelA, parcelB))))
        assertEquals(before.parcelId, expenses.observe(gasto).first()!!.parcelId)

        // Once the Gasto no longer names A, A can go.
        ok(expenses.delete(gasto))
        ok(activities.update(work, ActivityChanges(ActivityType.FERTILIZATION, date, "Abonado de primavera", setOf(parcelB))))
    }

    @Test
    fun retypingOrRedatingTheWorkLeavesItsGastosAlone() = runBlocking {
        val work = activity(costMinor = null)
        val gasto = ok(expenses.create(draft(2_000, farmId = farmId, parcelId = parcelA, activityId = work,
            concept = "Abono A", category = ExpenseCategory.PRODUCTS)))
        ok(activities.update(work, ActivityChanges(ActivityType.PRUNING, date.minusDays(2), "Poda", setOf(parcelA))))
        val after = expenses.observe(gasto).first()!!
        assertEquals(ExpenseCategory.PRODUCTS, after.category)
        assertEquals(date, after.expenseDate)
    }

    @Test
    fun aWorkBecomingWholeFarmKeepsAParcelGasto() = runBlocking {
        val draftWork = ok(activities.create(NewActivity(farmId, null, ActivityType.PRUNING, date, "Poda", setOf(parcelA), asDraft = true)))
        ok(expenses.create(draft(500, farmId = farmId, parcelId = parcelA, activityId = draftWork, concept = "Afilado")))
        assertParcelHasExpenses(activities.update(draftWork, ActivityChanges(ActivityType.PRUNING, date, "Poda", emptySet())))
    }

    // ------------------------------------------------------------ #437 archive keeps real money linked

    @Test
    fun workWithAGastoOfItsOwnIsNotArchived() = runBlocking {
        val cancelled = activity(costMinor = null)
        val manual = ok(expenses.create(draft(2_000, farmId = farmId, activityId = cancelled, concept = "Transporte")))
        ok(activities.cancel(cancelled))
        val blocked = activities.archive(cancelled)
        assertTrue(blocked is AppResult.Failure && (blocked.error as? AppError.Conflict)?.resource == ActivityCostRules.LINKED_EXPENSES)
        assertNull(db.activityDao().findById(cancelled)!!.metadata.deletedAt)
        assertEquals(cancelled, expenses.observe(manual).first()!!.activityId)
        assertEquals(2_000, ExpenseSummary.of(expenses.observeAll().first(), "EUR").totalMinor)

        val draftWork = ok(activities.create(NewActivity(farmId, null, ActivityType.PRUNING, date, "Poda", setOf(parcelA), asDraft = true)))
        ok(expenses.create(draft(500, farmId = farmId, activityId = draftWork, concept = "Afilado")))
        val draftBlocked = activities.archive(draftWork)
        assertTrue(draftBlocked is AppResult.Failure && (draftBlocked.error as? AppError.Conflict)?.resource == ActivityCostRules.LINKED_EXPENSES)
    }

    @Test
    fun workWithoutGastosIsArchivedAsBefore() = runBlocking {
        val cancelled = activity(costMinor = null)
        ok(activities.cancel(cancelled))
        ok(activities.archive(cancelled))
        assertNotNull(db.activityDao().findById(cancelled)!!.metadata.deletedAt)
    }

    // ------------------------------------------------------------ #456 posting re-checks the draft

    /** A DRAFT as a reviewed document leaves it: counted nowhere until a person confirms it. */
    private suspend fun draftOf(expense: ExpenseDraft, change: (com.isivoltpro.maginaolivo.data.local.entity.ExpenseEntity) -> com.isivoltpro.maginaolivo.data.local.entity.ExpenseEntity = { it }): UUID {
        val id = ok(expenses.create(expense))
        val row = db.expenseDao().findById(id)!!
        db.expenseDao().upsert(change(row.copy(status = ExpenseStatus.DRAFT.name)))
        return id
    }

    private suspend fun assertStillDraft(id: UUID, before: Long) {
        assertEquals(ExpenseStatus.DRAFT, expenses.observe(id).first()!!.status)
        assertEquals(before, ExpenseSummary.of(expenses.observeAll().first(), "EUR").totalMinor)
    }

    @Test
    fun aValidDraftIsPosted() = runBlocking {
        val id = draftOf(draft(3_000, farmId = farmId, parcelId = parcelA))
        assertEquals(0, ExpenseSummary.of(expenses.observeAll().first(), "EUR").totalMinor)
        ok(expenses.post(id))
        assertEquals(3_000, ExpenseSummary.of(expenses.observeAll().first(), "EUR").totalMinor)
    }

    @Test
    fun aDraftDatedAfterTodayIsNotPosted() = runBlocking {
        val id = draftOf(draft(3_000, farmId = farmId)) { it.copy(expenseDate = LocalDate.parse("2027-01-15")) }
        assertValidation("expenseDate", expenses.post(id))
        assertStillDraft(id, 0)
    }

    @Test
    fun aDraftWhoseParcelNoLongerHoldsIsNotPosted() = runBlocking {
        val id = draftOf(draft(3_000, farmId = farmId, parcelId = parcelA))
        val parcel = db.parcelDao().findById(parcelA)!!
        db.parcelDao().upsert(parcel.copy(status = com.isivoltpro.maginaolivo.data.local.model.RecordStatus.ARCHIVED))
        assertValidation("parcelId", expenses.post(id))
        assertStillDraft(id, 0)
    }

    @Test
    fun aDraftOfGeneralWorkOnACampaignIsNotPosted() = runBlocking {
        runningDay()
        val general = activity(costMinor = null)
        val id = draftOf(draft(3_000, farmId = farmId, activityId = general)) { it.copy(campaignId = campaignId) }
        assertValidation("campaignId", expenses.post(id))
        assertStillDraft(id, 0)
    }

    @Test
    fun aDraftOfAClosedCampaignStaysBlocked() = runBlocking {
        runningDay()
        val id = draftOf(draft(3_000, farmId = farmId)) { it.copy(campaignId = campaignId) }
        closeCampaign()
        assertClosedMutation(expenses.post(id))
        assertStillDraft(id, 0)
    }

    @Test
    fun postingKeepsTheSupplierNameAsCaptured() = runBlocking {
        val supplier = ok(organizations.create(OrganizationDraft("Agro Sur", setOf(OrganizationRole.SUPPLIER))))
        val id = draftOf(draft(3_000, farmId = farmId).copy(supplierOrganizationId = supplier))
        ok(organizations.update(supplier, OrganizationDraft("Agro Sur SL", setOf(OrganizationRole.SUPPLIER))))
        ok(expenses.post(id))
        assertEquals("Agro Sur", db.expenseDao().findById(id)!!.provider)
    }

    /** #451: editing a Gasto keeps the supplier name it was saved with; only a new supplier takes its name. */
    @Test
    fun editingAGastoKeepsTheSupplierNameAsCaptured() = runBlocking {
        val supplier = ok(organizations.create(OrganizationDraft("Agro Sur", setOf(OrganizationRole.SUPPLIER))))
        val saved = draft(3_000, farmId = farmId).copy(supplierOrganizationId = supplier)
        val id = ok(expenses.create(saved))
        ok(organizations.update(supplier, OrganizationDraft("Agro Sur SL", setOf(OrganizationRole.SUPPLIER))))

        ok(expenses.update(id, saved.copy(amountMinor = 3_500, notes = "Segunda factura")))
        assertEquals("Agro Sur", db.expenseDao().findById(id)!!.provider)

        // An archived supplier does not stop correcting the rest of the Gasto.
        ok(organizations.archive(supplier))
        ok(expenses.update(id, saved.copy(concept = "Abono foliar")))
        assertEquals("Agro Sur", db.expenseDao().findById(id)!!.provider)
        assertEquals(supplier, db.expenseDao().findById(id)!!.supplierOrganizationId)

        // Choosing another supplier is an explicit change: it takes that one's name.
        val other = ok(organizations.create(OrganizationDraft("Fitos Mágina", setOf(OrganizationRole.SUPPLIER))))
        ok(expenses.update(id, saved.copy(supplierOrganizationId = other)))
        assertEquals("Fitos Mágina", db.expenseDao().findById(id)!!.provider)
    }

    /** #451 QA 7/9: an archived supplier is never taken by a new Gasto nor by an explicit change. */
    @Test
    fun anArchivedSupplierIsOnlyKeptWhereItAlreadyWas() = runBlocking {
        val archived = ok(organizations.create(OrganizationDraft("Agro Sur", setOf(OrganizationRole.SUPPLIER))))
        val active = ok(organizations.create(OrganizationDraft("Fitos Mágina", setOf(OrganizationRole.SUPPLIER))))
        val kept = draftOf(draft(3_000, farmId = farmId).copy(supplierOrganizationId = archived))
        ok(organizations.archive(archived))

        val created = expenses.create(draft(2_000, farmId = farmId).copy(supplierOrganizationId = archived))
        assertTrue(created is AppResult.Failure && (created.error as? AppError.Validation)?.code == "archived")

        val other = ok(expenses.create(draft(2_000, farmId = farmId).copy(supplierOrganizationId = active)))
        val changed = expenses.update(other, draft(2_000, farmId = farmId).copy(supplierOrganizationId = archived))
        assertTrue(changed is AppResult.Failure && (changed.error as? AppError.Validation)?.code == "archived")
        assertEquals(active, db.expenseDao().findById(other)!!.supplierOrganizationId)

        // The DRAFT that already had it still confirms, with its supplier and name as captured.
        ok(expenses.post(kept))
        assertEquals(archived, db.expenseDao().findById(kept)!!.supplierOrganizationId)
        assertEquals("Agro Sur", db.expenseDao().findById(kept)!!.provider)
    }

    // ------------------------------------------------------------ #429 work not done holds no money

    @Test
    fun plannedWorkWithACostFromALegacyCallerPostsNothing() = runBlocking {
        assertNotDoneWork(activities.create(NewActivity(farmId, null, ActivityType.PRUNING, date, "Poda",
            setOf(parcelA), costMinor = 6_000)))
        // Typed as done but kept planned (a harvest-day appointment): still no money.
        assertNotDoneWork(activities.create(NewActivity(farmId, null, ActivityType.HARVEST_DAY, date, "Recogida",
            setOf(parcelA), completeImmediately = true, costMinor = 3_000)))
        assertNotDoneWork(activities.create(NewActivity(farmId, null, ActivityType.PRUNING, date, "Poda",
            setOf(parcelA), asDraft = true, costMinor = 6_000)))
        assertTrue(expenses.observeAll().first().isEmpty())
    }

    @Test
    fun editingPlannedWorkNeverTouchesMoney() = runBlocking {
        val id = activity(costMinor = null)
        assertNotDoneWork(activities.update(id, changes(5_000)))
        ok(activities.update(id, changes(null)))
        assertTrue(expenses.observeAll().first().isEmpty())
    }

    @Test
    fun completingOrCancellingPlannedWorkLeavesTheLedgerAsItWas() = runBlocking {
        val completed = activity(costMinor = null)
        val cancelled = activity(costMinor = null)
        // A hand-typed Gasto tied to the work is money of its own: it neither blocks nor goes.
        val manual = ok(expenses.create(draft(2_000, farmId = farmId, activityId = cancelled, concept = "Transporte")))

        ok(activities.complete(completed))
        ok(activities.cancel(cancelled))

        assertEquals(listOf(manual), expenses.observeAll().first().map { it.id })
        assertEquals(2_000, ExpenseSummary.of(expenses.observeAll().first(), "EUR").totalMinor)
    }

    @Test
    fun aRealExpenseAddedAfterTheWorkIsOneRow() = runBlocking {
        val id = activity(costMinor = null)
        ok(activities.complete(id))
        ok(expenses.create(draft(4_000, farmId = farmId, activityId = id, concept = "Gasoil")))
        assertEquals(1, expenses.observeForActivity(id).first().size)
        assertEquals(4_000, ExpenseSummary.of(expenses.observeAll().first(), "EUR").totalMinor)
    }

    @Test
    fun reopeningDoneWorkWithACountedCostWaitsUntilItIsKeptOnItsOwn() = runBlocking {
        val id = activity(costMinor = 6_500)
        val cost = expenses.observeForActivity(id).first().single()

        assertCostToReview(activities.reopen(id))
        assertEquals(ActivityStatus.COMPLETED, activities.observe(id).first()!!.status)

        ok(expenses.keepAsIndependent(cost.id))
        val kept = expenses.observe(cost.id).first()!!
        assertEquals(ExpenseOrigin.MANUAL, kept.origin)
        assertNull(kept.activityId)
        assertEquals(farmId, kept.farmId)
        assertEquals(6_500, ExpenseSummary.of(expenses.observeAll().first(), "EUR").totalMinor)

        ok(activities.reopen(id))
        assertEquals(ActivityStatus.PLANNED, activities.observe(id).first()!!.status)
        assertEquals(6_500, ExpenseSummary.of(expenses.observeAll().first(), "EUR").totalMinor)
    }

    @Test
    fun reopeningDoneWorkAfterTheCostStopsCountingIsNormal() = runBlocking {
        val id = activity(costMinor = 6_500)
        ok(expenses.delete(expenses.observeForActivity(id).first().single().id))
        ok(activities.reopen(id))
        assertEquals(0, ExpenseSummary.of(expenses.observeAll().first(), "EUR").totalMinor)

        val plain = activity(costMinor = null, done = true)
        ok(activities.reopen(plain))
    }

    @Test
    fun aLegacyCostOnWorkNotDoneHoldsEveryMoveButCompleting() = runBlocking {
        val planned = legacyCostOn(ActivityStatus.PLANNED)
        assertCostToReview(activities.cancel(planned))
        ok(activities.complete(planned))

        val draft = legacyCostOn(ActivityStatus.DRAFT)
        assertCostToReview(activities.plan(draft))
        assertCostToReview(activities.archive(draft))

        val cancelled = legacyCostOn(ActivityStatus.CANCELLED)
        assertCostToReview(activities.reopen(cancelled))
        assertCostToReview(activities.archive(cancelled))
        // An edit of the work leaves the counted cost exactly as it is.
        assertEquals(3, expenses.observeAll().first().count { it.origin == ExpenseOrigin.ACTIVITY_COST })
        assertEquals(19_500, ExpenseSummary.of(expenses.observeAll().first(), "EUR").totalMinor)
    }

    /** Codex #520: a plain edit never moves a work's cost to another work, nor drops the link. */
    @Test
    fun aWorkCostKeepsItsWorkInAPlainEdit() = runBlocking {
        val id = activity(costMinor = 6_500)
        val other = activity(costMinor = null, done = true)
        val cost = expenses.observeForActivity(id).first().single()
        listOf(null, other).forEach { target ->
            val result = expenses.update(cost.id, draft(6_500, farmId = farmId, activityId = target, concept = "Abonado de primavera"))
            assertTrue(result is AppResult.Failure && (result.error as? AppError.Validation)?.code == "activity_cost_locked")
        }
        ok(expenses.update(cost.id, draft(7_000, farmId = farmId, activityId = id, concept = "Abonado de primavera")))
        assertEquals(id, expenses.observe(cost.id).first()!!.activityId)
        assertEquals(ExpenseOrigin.ACTIVITY_COST, expenses.observe(cost.id).first()!!.origin)
        assertEquals(7_000, expenses.observe(cost.id).first()!!.amountMinor)
    }

    /** #476: a Parcel the Gasto already had stays valid when archived or moved; a new choice must be current. */
    @Test
    fun editingAGastoKeepsItsParcelEvenIfArchivedOrMoved() = runBlocking {
        val saved = draft(4_000, farmId = farmId, parcelId = parcelA)
        val id = ok(expenses.create(saved))

        // Archived: a note can still be corrected, and the Gasto keeps Finca/Parcela.
        val parcel = db.parcelDao().findById(parcelA)!!
        db.parcelDao().upsert(parcel.copy(status = com.isivoltpro.maginaolivo.data.local.model.RecordStatus.ARCHIVED))
        ok(expenses.update(id, saved.copy(notes = "Factura corregida")))
        assertEquals(parcelA, db.expenseDao().findById(id)!!.parcelId)
        assertEquals(farmId, db.expenseDao().findById(id)!!.farmId)
        // A new Gasto cannot pick it.
        assertValidation("parcelId", expenses.create(draft(1_000, farmId = farmId, parcelId = parcelA)))

        // Moved to another Farm: the old Gasto still edits on its own Farm and Parcel.
        db.parcelDao().upsert(parcel)
        val current = db.parcelDao().findCurrentMembership(parcelA)!!
        db.parcelDao().upsertMembership(current.copy(validUntil = now))
        db.parcelDao().upsertMembership(
            FarmParcelMembershipEntity(UUID.randomUUID(), workspaceId, otherFarmId, parcelA, now, metadata = LocalMetadata(now, now)),
        )
        ok(expenses.update(id, saved.copy(amountMinor = 4_500)))
        val kept = db.expenseDao().findById(id)!!
        assertEquals(farmId, kept.farmId)
        assertEquals(parcelA, kept.parcelId)
        assertEquals(4_500L, kept.amountMinor)
        // Choosing it now on its old Farm is a new choice: refused.
        val other = ok(expenses.create(draft(1_000, farmId = farmId)))
        assertValidation("parcelId", expenses.update(other, draft(1_000, farmId = farmId, parcelId = parcelA)))
        // An explicit change to no Parcel is fine.
        ok(expenses.update(id, saved.copy(parcelId = null)))
        assertNull(db.expenseDao().findById(id)!!.parcelId)
    }

    /** #476 vs #456: a DRAFT is not history; its archived Parcel is re-checked, a POSTED one keeps it. */
    @Test
    fun onlyAPostedGastoKeepsAnArchivedParcelADraftIsReChecked() = runBlocking {
        val saved = draft(2_000, farmId = farmId, parcelId = parcelA)
        val pending = draftOf(saved)
        val posted = ok(expenses.create(saved))
        val parcel = db.parcelDao().findById(parcelA)!!
        db.parcelDao().upsert(parcel.copy(status = com.isivoltpro.maginaolivo.data.local.model.RecordStatus.ARCHIVED))

        assertValidation("parcelId", expenses.update(pending, saved.copy(notes = "Revisado")))
        assertValidation("parcelId", expenses.post(pending))
        assertStillDraft(pending, 2_000)
        assertEquals(parcelA, db.expenseDao().findById(pending)!!.parcelId)

        ok(expenses.update(posted, saved.copy(amountMinor = 2_500, notes = "Revisado")))
        val kept = db.expenseDao().findById(posted)!!
        assertEquals(parcelA, kept.parcelId)
        assertEquals(farmId, kept.farmId)
        assertEquals(2_500L, kept.amountMinor)
    }

    /**
     * Owner decision (#429, 5-oct-2026): «Conservar como gasto independiente» only drops the link to
     * the work. Same row, same money, date, concept, supplier, Farm/Parcel and Campaign; no new
     * Expense, nothing duplicated, nothing removed. It only ever runs on the farmer's tap.
     */
    @Test
    fun keepingAWorkCostOnItsOwnOnlyDropsItsWork() = runBlocking {
        val id = activity(costMinor = 6_500)
        val before = db.expenseDao().listForActivity(id).single()
        val rowsBefore = expenses.observeAll().first().size

        ok(expenses.keepAsIndependent(before.id))
        val after = db.expenseDao().findById(before.id)!!
        assertNull(after.activityId)
        assertEquals(ExpenseOrigin.MANUAL.name, after.origin)
        assertEquals(
            before.copy(activityId = null, origin = ExpenseOrigin.MANUAL.name, metadata = after.metadata),
            after,
        )
        assertEquals(before.metadata.version + 1, after.metadata.version)
        assertNull(after.metadata.deletedAt)
        assertEquals(rowsBefore, expenses.observeAll().first().size)
        assertEquals(6_500, ExpenseSummary.of(expenses.observeAll().first(), "EUR").totalMinor)
        assertTrue(expenses.observeForActivity(id).first().isEmpty())
    }

    @Test
    fun onlyAWorkCostCanBeKeptOnItsOwn() = runBlocking {
        val manual = ok(expenses.create(draft(1_000, farmId = farmId)))
        val result = expenses.keepAsIndependent(manual)
        assertTrue(result is AppResult.Failure && (result.error as? AppError.Conflict)?.resource == "not_activity_cost")
    }

    // ------------------------------------------------------------ no double counting

    @Test
    fun aPostedExpenseIsCountedOnceWhereverItIsSeen() = runBlocking {
        val activityId = activity(costMinor = null)
        val id = ok(expenses.create(draft(12_000, farmId = farmId, parcelId = parcelA, activityId = activityId)))

        val all = expenses.observeAll().first()
        assertEquals(12_000, ExpenseSummary.of(all, "EUR").totalMinor)
        assertEquals(listOf(id), expenses.observeForActivity(activityId).first().map { it.id })
        assertEquals(1, all.size)
    }

    @Test
    fun anActivityCostIsOneLinkedExpenseAndNeverASecondNumber() = runBlocking {
        val activityId = activity(costMinor = 6_500)
        val linked = expenses.observeForActivity(activityId).first().single()
        assertEquals(6_500, linked.amountMinor)
        assertEquals(ExpenseOrigin.ACTIVITY_COST, linked.origin)
        assertEquals(ExpenseStatus.POSTED, linked.status)
        assertEquals(6_500L, activities.observe(activityId).first()!!.costMinor)
        // The Activity table never carries its own amount (D2).
        assertNull(db.activityDao().findById(activityId)!!.costMinor)
        // #416/#429: the work never rewrites it; the amount is corrected on its own Gasto.
        assertTrue(activities.update(activityId, changes(costMinor = 8_000)) is AppResult.Failure)
        ok(expenses.update(linked.id, draft(8_000, farmId = farmId, activityId = activityId, concept = "Abonado de primavera")))
        assertEquals(8_000, expenses.observeForActivity(activityId).first().single().amountMinor)
        assertEquals(8_000, ExpenseSummary.of(expenses.observeAll().first(), "EUR").totalMinor)
    }

    @Test
    fun anExtraExpenseOnTheSameActivityIsCountedOnceNextToItsCost() = runBlocking {
        val activityId = activity(costMinor = 6_500)
        val extra = ok(expenses.create(draft(2_000, farmId = farmId, activityId = activityId, concept = "Transporte")))

        assertEquals(8_500, ExpenseSummary.of(expenses.observeAll().first(), "EUR").totalMinor)
        assertEquals(2_000, expenses.observe(extra).first()!!.amountMinor)
        assertEquals(2, expenses.observeForActivity(activityId).first().size)
    }

    @Test
    fun purchaseLinesDescribeTheExpenseAndNeverAddMoney() = runBlocking {
        val id = ok(
            expenses.create(
                draft(12_000, farmId = farmId).copy(
                    invoiceNumber = "F-2026-118",
                    lines = listOf(
                        PurchaseLine("Abono NPK", 10.0, "sacos", lineTotalMinor = 5_000),
                        PurchaseLine("Cobre", 2.0, "kg", lineTotalMinor = 7_000),
                    ),
                ),
            ),
        )

        val expense = expenses.observe(id).first()!!
        assertEquals(2, expense.lines.size)
        assertEquals("F-2026-118", expense.invoiceNumber)
        assertEquals(12_000, ExpenseSummary.of(expenses.observeAll().first(), "EUR").totalMinor)

        ok(expenses.update(id, draft(12_000, farmId = farmId).copy(lines = listOf(PurchaseLine("Abono NPK")))))
        assertEquals(1, expenses.observe(id).first()!!.lines.size)
        assertEquals(12_000, ExpenseSummary.of(expenses.observeAll().first(), "EUR").totalMinor)
    }

    @Test
    fun aDeletedExpenseLeavesTheTotalsAndQueuesOneTombstone() = runBlocking {
        val id = ok(expenses.create(draft(4_000)))
        ok(expenses.delete(id))
        ok(expenses.delete(id))

        assertEquals(0, ExpenseSummary.of(expenses.observeAll().first(), "EUR").totalMinor)
        assertNull(expenses.observe(id).first())
        assertEquals(
            listOf(OutboxOperation.DELETE),
            db.syncOutboxDao().listForEntity(SyncEntityType.EXPENSE, id).map { it.operation },
        )
    }

    @Test
    fun farmExpenseRemainsOutsideCampaignUnlessExplicitlySelected() = runBlocking {
        val meta = LocalMetadata(now, now)
        db.campaignDao().upsert(
            CampaignEntity(campaignId, workspaceId, farmId, "Campaña 2025/26", date.minusMonths(6), status = CampaignStatus.ACTIVE, metadata = meta),
        )
        val id = ok(expenses.create(draft(3_000, farmId = farmId)))
        assertNull(expenses.observe(id).first()!!.campaignId)
        val explicit = ok(expenses.create(draft(3_000, farmId = farmId).copy(campaignId = campaignId)))
        assertEquals(campaignId, expenses.observe(explicit).first()!!.campaignId)
        listOf("JPY" to 1000L, "EUR" to 12345L, "KWD" to 123456L).forEach { (currency, amount) ->
            val contextual = draft(amount, farmId = farmId).copy(campaignId = campaignId, currency = currency,
                lines = listOf(PurchaseLine("Producto", lineTotalMinor = amount)))
            val expense = ok(expenses.create(contextual))
            ok(expenses.update(expense, contextual.copy(concept = "Corregido")))
            val saved = expenses.observe(expense).first()!!
            assertEquals(currency, saved.currency)
            assertEquals(amount, saved.amountMinor)
            assertEquals(amount, saved.lines.single().lineTotalMinor)
            assertEquals(campaignId, saved.campaignId)
        }
    }

    /** #433: the cost of a work follows that work: its Farm, its Campaign (or none), one of its Parcels. */
    @Test
    fun anExpenseNeverMixesAWorkWithAnIncompatibleCampaignParcelOrFarm() = runBlocking {
        val meta = LocalMetadata(now, now)
        db.campaignDao().upsert(
            CampaignEntity(campaignId, workspaceId, farmId, "2025/26", date.minusMonths(6), status = CampaignStatus.HARVEST, metadata = meta),
        )
        val otherCampaign = UUID.randomUUID()
        db.campaignDao().upsert(
            CampaignEntity(otherCampaign, workspaceId, farmId, "2024/25", date.minusMonths(18), status = CampaignStatus.CLOSED, metadata = meta),
        )
        val parcelB = UUID.randomUUID()
        db.parcelDao().upsert(ParcelEntity(parcelB, workspaceId, "Parcela B", source = "MANUAL", metadata = meta))
        db.parcelDao().upsertMembership(FarmParcelMembershipEntity(UUID.randomUUID(), workspaceId, farmId, parcelB, now, metadata = meta))
        val general = activity(costMinor = null) // Parcela A, no Campaign

        // 1. General work + the running recolección → refused; 2. kept general → fine.
        assertValidation("campaignId", expenses.create(draft(1_000, farmId = farmId, activityId = general).copy(campaignId = campaignId)))
        val kept = ok(expenses.create(draft(1_000, farmId = farmId, activityId = general)))
        assertNull(expenses.observe(kept).first()!!.campaignId)

        // 3. Work of Campaign X + Expense X → fine; without a Campaign it takes X; 4. Campaign Y → refused.
        val ofCampaign = ok(
            activities.create(NewActivity(farmId, campaignId, ActivityType.PRUNING, date, "Poda de recogida", setOf(parcelA))),
        )
        ok(expenses.create(draft(1_000, farmId = farmId, activityId = ofCampaign).copy(campaignId = campaignId)))
        val derived = ok(expenses.create(draft(1_000, farmId = farmId, activityId = ofCampaign)))
        assertEquals(campaignId, expenses.observe(derived).first()!!.campaignId)
        assertValidation("campaignId", expenses.create(draft(1_000, farmId = farmId, activityId = ofCampaign).copy(campaignId = otherCampaign)))

        // 5. A Parcel the work was not done on → refused; 6. one it was → fine.
        assertValidation("parcelId", expenses.create(draft(1_000, farmId = farmId, parcelId = parcelB, activityId = general)))
        ok(expenses.create(draft(1_000, farmId = farmId, parcelId = parcelA, activityId = general)))

        // 9./10./12. The Farm comes from the work or the Parcel; a child relation never lacks it.
        val fromWork = ok(expenses.create(draft(1_000, activityId = general)))
        assertEquals(farmId, expenses.observe(fromWork).first()!!.farmId)
        val fromParcel = ok(expenses.create(draft(1_000, parcelId = parcelA)))
        assertEquals(farmId, expenses.observe(fromParcel).first()!!.farmId)
        // 11. Parcel and work of different Farms → refused.
        assertValidation("parcelId", expenses.create(draft(1_000, parcelId = otherParcel, activityId = general)))
        assertValidation("activityId", expenses.create(draft(1_000, farmId = otherFarmId, activityId = general)))
    }

    /**
     * Owner decision 2026-10-03 + CR-012 P1: Cuaderno → Gasto starts on «Gasto de recogida»,
     * «Gasto general» stays outside, a ticket from «Gastos de recogida» keeps Farm + Campaign
     * through OCR review, and editing never re-assigns. Only the explicit choice counts.
     */
    @Test
    fun onlyTheFormsExplicitChoiceCountsInCampaignCostsAndCostPerKg() = runBlocking {
        val meta = LocalMetadata(now, now)
        db.campaignDao().upsert(
            CampaignEntity(campaignId, workspaceId, farmId, "2025/26", date.minusMonths(6), status = CampaignStatus.HARVEST, metadata = meta),
        )
        val running = com.isivoltpro.maginaolivo.domain.campaign.Campaign(
            campaignId, workspaceId, farmId, "2025/26", date.minusMonths(6), null, CampaignStatus.HARVEST, null, emptyList(), 1,
        )
        val options = RelationOptions(campaigns = listOf(running))

        // Cuaderno → Gasto: preselected «Gasto de recogida».
        val cuaderno = ExpenseForm(date.toString(), "40", "Gasoil recogida", farmId = farmId).withRecollectionPreselected(options)
        assertEquals(campaignId, cuaderno.campaignId)
        val inCampaign = ok(expenses.create(cuaderno.toDraft().first!!))
        // Same entry switched expressly to «Gasto general de finca/parcela».
        val general = ok(expenses.create(cuaderno.copy(amount = "25", concept = "Poda", campaignId = null).toDraft().first!!))
        // «Gastos de recogida» → Ticket o factura → OCR review → draft → confirm.
        val documentId = ok(documents.importDocument(DocumentType.PURCHASE_INVOICE, source("recogida.jpg")))
        val reviewed = documents.observe(documentId).first()!!.toReviewForm(farmId, campaignId)
            .copy(date = date.toString(), amount = "12", concept = "Factura sacos")
        val fromDocument = ok(documents.createExpenseDraft(documentId, reviewed.toDraft(requireAmount = false).first!!))
        ok(expenses.post(fromDocument))

        // Editing keeps each saved choice; nothing is re-assigned.
        val savedGeneral = expenses.observe(general).first()!!
        ok(expenses.update(general, savedGeneral.toForm().copy(concept = "Poda corregida").toDraft().first!!))
        val savedCampaign = expenses.observe(inCampaign).first()!!
        ok(expenses.update(inCampaign, savedCampaign.toForm().copy(concept = "Gasoil corregido").toDraft().first!!))
        assertNull(expenses.observe(general).first()!!.campaignId)
        assertEquals(campaignId, expenses.observe(inCampaign).first()!!.campaignId)
        with(expenses.observe(fromDocument).first()!!) {
            assertEquals(campaignId, this.campaignId)
            assertEquals(farmId, this.farmId)
            assertEquals(ExpenseStatus.POSTED, status)
        }

        // 40 + 12 EUR over 400 kg = 0,13 €/kg; the 25 EUR general expense never counts.
        val pesada = com.isivoltpro.maginaolivo.domain.delivery.Delivery(
            id = UUID.randomUUID(), workspaceId = workspaceId, farmId = farmId, campaignId = campaignId,
            deliveryDate = date, destinationOrganizationId = null, destinationName = "Cooperativa",
            netGrams = 400_000, grossGrams = null, tareGrams = null, deliveryNumber = null, ticketNumber = null,
            source = com.isivoltpro.maginaolivo.domain.delivery.DeliverySource.MANUAL, shares = emptyList(), notes = null, version = 1,
        )
        val ledger = com.isivoltpro.maginaolivo.domain.expense.RecollectionLedger
            .of(campaignId, expenses.observeAll().first(), listOf(pesada)).single()
        assertEquals(setOf(inCampaign, fromDocument), ledger.posted.map { it.id }.toSet())
        assertEquals(5_200L, ledger.amount())
        assertEquals(130L, ledger.costPerKgMilli) // #486: 0,130 €/kg
    }

    @Test
    fun relationsOutsideTheFarmAreRejectedWithoutWritingMoney() = runBlocking {
        val result = expenses.create(draft(3_000, farmId = farmId, parcelId = otherParcel))
        assertValidation("parcelId", result)
        assertTrue(expenses.observeAll().first().isEmpty())
    }

    @Test
    fun theLedgerSurvivesARestart() = runBlocking {
        val id = ok(expenses.create(draft(5_500, farmId = farmId)))
        db.close()
        open()
        assertEquals(5_500, expenses.observe(id).first()!!.amountMinor)
        assertEquals(5_500, ExpenseSummary.of(expenses.observeAll().first(), "EUR").totalMinor)
    }

    // CR-012: closing a campaign freezes its authoritative costs even with no payments.

    @Test
    fun closedLinkedManualCostCannotBeRewritten() = runBlocking {
        val dayId = runningDay()
        val original = draft(6_000, farmId = farmId).copy(harvestId = dayId)
        val id = ok(expenses.create(original))
        closeCampaign()
        val before = ledgerState()

        assertClosedMutation(expenses.update(id, original.copy(amountMinor = 4_000)))

        assertEquals(before, ledgerState())
        assertEquals(6_000L, expenses.observe(id).first()!!.amountMinor)
    }

    @Test
    fun closedLinkedManualCostCannotBeDeletedWithItsPurchase() = runBlocking {
        val dayId = runningDay()
        val id = ok(expenses.create(draft(6_000, farmId = farmId).copy(harvestId = dayId,
            invoiceNumber = "H-1", lines = listOf(PurchaseLine("Transporte", lineTotalMinor = 6_000)))))
        closeCampaign()
        val before = ledgerState()

        assertClosedMutation(expenses.delete(id))

        assertEquals(before, ledgerState())
        assertEquals(6_000L, ExpenseSummary.of(expenses.observeAll().first(), "EUR").totalMinor)
        assertEquals("H-1", expenses.observe(id).first()!!.invoiceNumber)
    }

    @Test
    fun closedLinkedDocumentDraftCannotBePosted() = runBlocking {
        val dayId = runningDay()
        val document = ok(documents.importDocument(DocumentType.PURCHASE_INVOICE, source("closed-post.jpg")))
        val id = ok(documents.createExpenseDraft(document, draft(6_000, farmId = farmId).copy(harvestId = dayId)))
        closeCampaign()
        val before = ledgerState()

        assertClosedMutation(expenses.post(id))

        assertEquals(before, ledgerState())
        assertEquals(ExpenseStatus.DRAFT, expenses.observe(id).first()!!.status)
        assertEquals(0L, ExpenseSummary.of(expenses.observeAll().first(), "EUR").totalMinor)
    }

    @Test
    fun closedCostCannotBeReparentedToAnActiveCampaign() = runBlocking {
        val closedDay = runningDay()
        val id = ok(expenses.create(draft(6_000, farmId = farmId).copy(harvestId = closedDay)))
        closeCampaign()
        val activeDay = runningDay(UUID.randomUUID(), otherFarmId)
        val before = ledgerState()

        assertClosedMutation(expenses.update(id, draft(6_000, farmId = otherFarmId).copy(harvestId = activeDay)))

        assertEquals(before, ledgerState())
        assertEquals(campaignId, expenses.observe(id).first()!!.campaignId)
    }

    @Test
    fun activeCostCannotBeReparentedIntoAClosedCampaign() = runBlocking {
        val closedDay = runningDay()
        closeCampaign()
        val activeCampaign = UUID.randomUUID()
        val activeDay = runningDay(activeCampaign, otherFarmId)
        val id = ok(expenses.create(draft(6_000, farmId = otherFarmId).copy(harvestId = activeDay)))
        val before = ledgerState()

        assertClosedMutation(expenses.update(id, draft(6_000, farmId = farmId).copy(harvestId = closedDay)))

        assertEquals(before, ledgerState())
        assertEquals(activeCampaign, expenses.observe(id).first()!!.campaignId)
    }

    @Test
    fun closedDayCannotReceiveANewManualCost() = runBlocking {
        val dayId = runningDay()
        closeCampaign()
        val before = ledgerState()

        assertClosedMutation(expenses.create(draft(6_000, farmId = farmId).copy(harvestId = dayId)))

        assertEquals(before, ledgerState())
    }

    @Test
    fun explicitClosedCampaignCannotReceiveACostWithoutADay() = runBlocking {
        runningDay()
        closeCampaign()
        val before = ledgerState()

        assertClosedMutation(expenses.create(draft(6_000, farmId = farmId).copy(campaignId = campaignId)))

        assertEquals(before, ledgerState())
    }

    @Test
    fun closedUnlinkedCostCannotBeMovedToAnActiveDayByLinking() = runBlocking {
        runningDay()
        val id = ok(expenses.create(draft(6_000, farmId = farmId).copy(campaignId = campaignId)))
        closeCampaign()
        val activeDay = runningDay(UUID.randomUUID())
        val costs = OfflineFirstDayCostRepository(db, FixedClock(now), RandomIds, TestDispatchers)
        val before = ledgerState()

        assertClosedMutation(costs.linkToDay(id, activeDay))

        assertEquals(before, ledgerState())
        assertNull(expenses.observe(id).first()!!.harvestId)
        assertEquals(campaignId, expenses.observe(id).first()!!.campaignId)
    }

    @Test
    fun closedActivityCostRewriteAndRemovalRollBackTheActivityAndIntents() = runBlocking {
        runningDay()
        val id = ok(activities.create(NewActivity(farmId, campaignId, ActivityType.FERTILIZATION,
            date, "Abonado", setOf(parcelA), completeImmediately = true, costMinor = 6_000)))
        closeCampaign()
        val before = ledgerState()

        // #429: done work is protected and never rewrites its cost; neither move touches the ledger.
        assertTrue(activities.update(id, changes(4_000)) is AppResult.Failure)
        assertEquals(before, ledgerState())
        assertTrue(activities.update(id, changes(null)) is AppResult.Failure)
        assertEquals(before, ledgerState())
        assertClosedMutation(expenses.keepAsIndependent(expenses.observeForActivity(id).first().single().id))
        assertEquals(before, ledgerState())
        assertEquals(6_000L, activities.observe(id).first()!!.costMinor)
        assertEquals("Abonado", activities.observe(id).first()!!.description)
    }

    @Test
    fun closedActivityCannotGainANewCostAndNewCostBearingActivityRollsBack() = runBlocking {
        runningDay()
        val id = ok(activities.create(NewActivity(farmId, campaignId, ActivityType.FERTILIZATION,
            date, "Abonado", setOf(parcelA))))
        closeCampaign()
        val before = ledgerState()

        assertNotDoneWork(activities.update(id, changes(6_000)))
        assertEquals(before, ledgerState())
        assertClosedMutation(activities.create(NewActivity(farmId, campaignId, ActivityType.FERTILIZATION,
            date, "Nueva actuación", setOf(parcelA), completeImmediately = true, costMinor = 6_000)))
        assertEquals(before, ledgerState())
    }

    @Test
    fun documentReviewCannotCreateACostInAClosedCampaignOrReownItsAttachment() = runBlocking {
        val dayId = runningDay()
        val document = ok(documents.importDocument(DocumentType.PURCHASE_INVOICE, source("closed-create.jpg")))
        val attachment = documents.observe(document).first()!!.attachmentId
        closeCampaign()
        val before = ledgerState()

        assertClosedMutation(documents.createExpenseDraft(document, draft(6_000, farmId = farmId).copy(harvestId = dayId)))

        assertEquals(before, ledgerState())
        assertEquals(attachment, documents.observe(document).first()!!.attachmentId)
        assertTrue(expenses.observeAll().first().isEmpty())
    }

    @Test
    fun closedCampaignAllowsNonmutatingPostAndDeleteRetries() = runBlocking {
        val dayId = runningDay()
        val posted = ok(expenses.create(draft(6_000, farmId = farmId).copy(harvestId = dayId)))
        val removed = ok(expenses.create(draft(1_000, farmId = farmId).copy(harvestId = dayId)))
        ok(expenses.delete(removed))
        closeCampaign()
        val before = ledgerState()

        ok(expenses.post(posted))
        ok(expenses.delete(removed))

        assertEquals(before, ledgerState())
    }

    // ------------------------------------------------------------ organization reuse

    @Test
    fun anOrganizationWithTwoRolesIsOneRowReusedAcrossContexts() = runBlocking {
        val id = ok(organizations.create(OrganizationDraft("Cooperativa San Isidro", setOf(OrganizationRole.MILL, OrganizationRole.SUPPLIER))))

        assertEquals(listOf(id), organizations.observeWithAnyRole(setOf(OrganizationRole.SUPPLIER)).first().map { it.id })
        assertEquals(
            listOf(id),
            organizations.observeWithAnyRole(setOf(OrganizationRole.MILL, OrganizationRole.COOPERATIVE)).first().map { it.id },
        )
        assertEquals(
            listOf(id),
            organizations.observeWithAnyRole(setOf(OrganizationRole.MILL, OrganizationRole.SUPPLIER)).first().map { it.id },
        )
        val duplicate = organizations.create(OrganizationDraft("cooperativa san isidro", setOf(OrganizationRole.COOPERATIVE)))
        assertTrue(duplicate is AppResult.Failure && duplicate.error is AppError.Conflict)
        assertEquals(1, organizations.observeAll().first().size)

        val expenseId = ok(expenses.create(draft(1_000).copy(supplierOrganizationId = id)))
        val expense = expenses.observe(expenseId).first()!!
        assertEquals(id, expense.supplierOrganizationId)
        assertEquals("Cooperativa San Isidro", expense.supplierName)
    }

    // ---------------------------------------------------------------- OCR safety

    @Test
    fun aReviewedDocumentBecomesADraftThatIsNeverCountedUntilPosted() = runBlocking {
        engine.text = INVOICE
        val documentId = ok(documents.importDocument(DocumentType.FERTILIZER_INVOICE, source("factura.jpg")))
        ok(documents.runExtraction(documentId))

        val extraction = documents.observe(documentId).first()!!
        assertEquals(OcrStatus.EXTRACTED, extraction.status)
        assertEquals(INVOICE, extraction.rawText)
        assertEquals(7_260L, extraction.proposal?.totalMinor)
        // Reading a document writes no money at all.
        assertTrue(expenses.observeAll().first().isEmpty())

        val expenseId = ok(documents.createExpenseDraft(documentId, draft(7_260, category = ExpenseCategory.PRODUCTS)))
        val expense = expenses.observe(expenseId).first()!!
        assertEquals(ExpenseStatus.DRAFT, expense.status)
        assertEquals(ExpenseOrigin.DOCUMENT_OCR, expense.origin)
        assertEquals(0, ExpenseSummary.of(expenses.observeAll().first(), "EUR").totalMinor)
        assertEquals(OcrStatus.CONFIRMED, documents.observe(documentId).first()!!.status)
        assertNotNull(documents.observe(documentId).first()!!.reviewedAt)
        // The original file now belongs to the expense and is still the same file.
        assertEquals(
            listOf(extraction.attachmentId),
            attachments.observeForOwner(AttachmentOwner(AttachmentOwnerType.EXPENSE, expenseId)).first().map { it.id },
        )

        ok(expenses.post(expenseId))
        assertEquals(7_260, ExpenseSummary.of(expenses.observeAll().first(), "EUR").totalMinor)
    }

    @Test
    fun aConfirmedDocumentCannotCreateASecondExpense() = runBlocking {
        engine.text = INVOICE
        val documentId = ok(documents.importDocument(DocumentType.PURCHASE_INVOICE, source("factura.jpg")))
        ok(documents.runExtraction(documentId))
        ok(documents.createExpenseDraft(documentId, draft(7_260)))

        val second = documents.createExpenseDraft(documentId, draft(7_260))
        assertTrue(second is AppResult.Failure && second.error is AppError.Conflict)
        assertEquals(1, expenses.observeAll().first().size)
    }

    @Test
    fun aDraftWithoutAnAmountCannotBePosted() = runBlocking {
        engine.text = "Documento sin importes"
        val documentId = ok(documents.importDocument(DocumentType.PURCHASE_RECEIPT, source("ticket.jpg")))
        ok(documents.runExtraction(documentId))
        assertEquals(OcrStatus.NEEDS_REVIEW, documents.observe(documentId).first()!!.status)

        val expenseId = ok(documents.createExpenseDraft(documentId, draft(0)))
        assertValidation("amountMinor", expenses.post(expenseId))
        assertEquals(ExpenseStatus.DRAFT, expenses.observe(expenseId).first()!!.status)
    }

    @Test
    fun aFailedReadingKeepsTheDocumentAndCanBeRetried() = runBlocking {
        engine.failure = IllegalStateException("model unavailable")
        val documentId = ok(documents.importDocument(DocumentType.IRRIGATION_INVOICE, source("riego.jpg")))
        assertTrue(documents.runExtraction(documentId) is AppResult.Failure)

        val failed = documents.observe(documentId).first()!!
        assertEquals(OcrStatus.FAILED, failed.status)
        assertTrue(attachments.observe(failed.attachmentId).first()!!.isAvailableLocally)
        assertTrue(expenses.observeAll().first().isEmpty())

        engine.failure = null
        engine.text = INVOICE
        ok(documents.runExtraction(documentId))
        assertEquals(OcrStatus.EXTRACTED, documents.observe(documentId).first()!!.status)
    }

    @Test
    fun aDiscardedDocumentLeavesNoMoneyAndNoFile() = runBlocking {
        engine.text = INVOICE
        val documentId = ok(documents.importDocument(DocumentType.PURCHASE_INVOICE, source("factura.jpg")))
        ok(documents.runExtraction(documentId))
        val attachmentId = documents.observe(documentId).first()!!.attachmentId

        ok(documents.discard(documentId))

        assertTrue(documents.observeOpen().first().isEmpty())
        assertNull(attachments.observe(attachmentId).first())
        assertTrue(expenses.observeAll().first().isEmpty())
    }

    // ------------------------------------------------------------------ helpers

    private fun open() {
        db = MaginaOlivoDatabase.create(context, DB)
        val workspaces = object : WorkspaceRepository {
            override suspend fun ensureLocalWorkspace(): AppResult<UUID> = AppResult.Success(workspaceId)
        }
        expenses = OfflineFirstExpenseRepository(db, workspaces, FixedClock(now), RandomIds, TestDispatchers)
        activities = OfflineFirstActivityRepository(db, FixedClock(now), RandomIds, TestDispatchers)
        organizations = OfflineFirstOrganizationRepository(db, workspaces, FixedClock(now), RandomIds, TestDispatchers)
        attachments = OfflineFirstAttachmentRepository(db, AndroidAttachmentFileStore(context), workspaces, FixedClock(now), RandomIds, TestDispatchers)
        documents = OfflineFirstDocumentOcrRepository(
            db, attachments, workspaces, engine, JsonProposalCodec(), FixedClock(now), RandomIds, TestDispatchers,
        )
    }

    /** A cost only goes with work recorded as done (#429). */
    private suspend fun activity(costMinor: Long?, done: Boolean = costMinor != null): UUID = ok(
        activities.create(
            NewActivity(farmId, null, ActivityType.FERTILIZATION, date, "Abonado de primavera", setOf(parcelA),
                completeImmediately = done, costMinor = costMinor),
        ),
    )

    private fun assertNotDoneWork(result: AppResult<*>) {
        val error = (result as? AppResult.Failure)?.error
        assertTrue("Expected costMinor/not_done_work but was $result",
            error is AppError.Validation && error.field == "costMinor" && error.code == ActivityCostRules.NOT_DONE_WORK)
    }

    private fun assertCostToReview(result: AppResult<*>) {
        val error = (result as? AppResult.Failure)?.error
        assertTrue("Expected activity_cost_posted conflict but was $result",
            error is AppError.Conflict && error.resource == ActivityCostRules.COST_TO_REVIEW)
    }

    /** A cost linked before #429 on work that is not done (only reachable through old data). */
    private suspend fun legacyCostOn(status: ActivityStatus): UUID {
        val id = activity(costMinor = 6_500)
        val row = db.activityDao().findById(id)!!
        db.activityDao().upsert(row.copy(status = status))
        return id
    }

    private fun changes(costMinor: Long?) =
        ActivityChanges(ActivityType.FERTILIZATION, date, "Abonado de primavera", setOf(parcelA), costMinor = costMinor)

    private fun draft(
        amountMinor: Long,
        farmId: UUID? = null,
        parcelId: UUID? = null,
        activityId: UUID? = null,
        concept: String = "Compra de abono",
        category: ExpenseCategory = ExpenseCategory.PRODUCTS,
    ) = ExpenseDraft(
        expenseDate = date,
        concept = concept,
        category = category,
        amountMinor = amountMinor,
        farmId = farmId,
        parcelId = parcelId,
        activityId = activityId,
    )

    private fun source(name: String): String {
        sources.mkdirs()
        val bitmap = Bitmap.createBitmap(64, 48, Bitmap.Config.ARGB_8888)
        val bytes = ByteArrayOutputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)
            bitmap.recycle()
            output.toByteArray()
        }
        val file = File(sources, name).apply { writeBytes(bytes) }
        return FileProvider.getUriForFile(context, "${context.packageName}.attachments", file).toString()
    }

    private fun <T> ok(result: AppResult<T>): T = when (result) {
        is AppResult.Success -> result.value
        is AppResult.Failure -> throw AssertionError("Expected success but was ${result.error}")
    }

    private fun assertValidation(field: String, result: AppResult<*>) {
        val error = (result as? AppResult.Failure)?.error
        assertTrue("Expected validation on $field but was $result", error is AppError.Validation && error.field == field)
    }

    private fun assertClosedMutation(result: AppResult<*>) {
        val error = (result as? AppResult.Failure)?.error
        assertTrue("Expected controlled campaign_closed validation but was $result",
            error is AppError.Validation && error.field == "campaignId" && error.code == "campaign_closed")
    }

    private suspend fun runningDay(id: UUID = campaignId, farm: UUID = farmId): UUID {
        val meta = LocalMetadata(now, now)
        db.campaignDao().upsert(CampaignEntity(id, workspaceId, farm, "Recogida", date.minusMonths(6),
            status = CampaignStatus.ACTIVE, metadata = meta))
        val dayId = UUID.randomUUID()
        db.harvestDao().upsert(HarvestEntity(dayId, workspaceId, id, farm, date, 0, metadata = meta))
        return dayId
    }

    private suspend fun closeCampaign() {
        val campaign = db.campaignDao().findById(campaignId)!!
        db.campaignDao().upsert(campaign.copy(status = CampaignStatus.CLOSED, endDate = date))
    }

    /** Include dependent records and all intents to detect partially committed indirect writes. */
    private fun ledgerState(): List<String> {
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM labour_payments").use { cursor ->
            cursor.moveToFirst()
            assertEquals("These closure regressions must not rely on a paid-balance guard", 0, cursor.getInt(0))
        }
        return listOf("campaigns", "harvests", "expenses", "purchases", "purchase_items", "activities",
            "activity_parcels", "documents", "document_ocr_extractions", "sync_outbox").flatMap { table ->
            db.openHelper.readableDatabase.query("SELECT * FROM $table ORDER BY id").use { cursor ->
                buildList {
                    while (cursor.moveToNext()) add(table + ":" + (0 until cursor.columnCount)
                        .joinToString("|") { cursor.getString(it) ?: "NULL" })
                }
            }
        }
    }

    private suspend fun seed() {
        val meta = LocalMetadata(now, now)
        db.workspaceDao().upsert(
            WorkspaceEntity(workspaceId, "Olivar", UUID.randomUUID(), "ES", "Europe/Madrid", "es-ES", "EUR", meta),
        )
        db.farmDao().upsert(FarmEntity(farmId, workspaceId, "Finca principal", metadata = meta))
        db.farmDao().upsert(FarmEntity(otherFarmId, workspaceId, "Finca vecina", metadata = meta))
        db.parcelDao().upsert(ParcelEntity(parcelA, workspaceId, "Parcela A", source = "MANUAL", metadata = meta))
        db.parcelDao().upsert(ParcelEntity(otherParcel, workspaceId, "Parcela vecina", source = "MANUAL", metadata = meta))
        db.parcelDao().upsertMembership(
            FarmParcelMembershipEntity(UUID.randomUUID(), workspaceId, farmId, parcelA, now, metadata = meta),
        )
        db.parcelDao().upsertMembership(
            FarmParcelMembershipEntity(UUID.randomUUID(), workspaceId, otherFarmId, otherParcel, now, metadata = meta),
        )
    }

    private class FakeEngine : OcrEngine {
        var text: String = ""
        var failure: Throwable? = null
        override val name: String = "fake-engine"

        override suspend fun recognize(localUri: String, mimeType: String): OcrText {
            failure?.let { throw it }
            return OcrText(text, name, "test")
        }
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

    companion object {
        private const val DB = "expense-ledger-contract-test.db"

        private val INVOICE = """
            Suministros Agrícolas Mágina S.L.
            CIF: B23456789
            Factura nº F-2026-118
            Fecha: 10/03/2026
            Abono NPK 15-15-15  10 sacos
            Base imponible: 60,00 €
            IVA 21%: 12,60 €
            TOTAL: 72,60 €
        """.trimIndent()
    }
}
