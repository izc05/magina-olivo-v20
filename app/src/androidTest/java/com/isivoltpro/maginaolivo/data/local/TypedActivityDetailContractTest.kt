package com.isivoltpro.maginaolivo.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.isivoltpro.maginaolivo.core.common.AppError
import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.core.dispatchers.AppDispatchers
import com.isivoltpro.maginaolivo.core.id.IdGenerator
import com.isivoltpro.maginaolivo.core.time.AppClock
import com.isivoltpro.maginaolivo.data.local.entity.FarmEntity
import com.isivoltpro.maginaolivo.data.local.entity.FarmParcelMembershipEntity
import com.isivoltpro.maginaolivo.data.local.entity.LocalMetadata
import com.isivoltpro.maginaolivo.data.local.entity.ParcelEntity
import com.isivoltpro.maginaolivo.data.local.entity.WorkspaceEntity
import com.isivoltpro.maginaolivo.data.local.model.SyncEntityType
import com.isivoltpro.maginaolivo.data.repository.OfflineFirstActivityRepository
import com.isivoltpro.maginaolivo.domain.activity.Activity
import com.isivoltpro.maginaolivo.domain.activity.ActivityChanges
import com.isivoltpro.maginaolivo.domain.activity.ActivityDetail
import com.isivoltpro.maginaolivo.domain.activity.ActivityRepository
import com.isivoltpro.maginaolivo.domain.activity.ActivityType
import com.isivoltpro.maginaolivo.domain.activity.IncidentSeverity
import com.isivoltpro.maginaolivo.domain.activity.IncidentState
import com.isivoltpro.maginaolivo.domain.activity.IrrigationPrice
import com.isivoltpro.maginaolivo.domain.activity.IrrigationPricingBasis
import com.isivoltpro.maginaolivo.domain.activity.NewActivity
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Phase 10 — typed agronomic details.
 *
 * Proves that each type persists its own structured fields, that a detail can never
 * belong to another type, and that a detail is a child of the Activity aggregate: it is
 * written in the same transaction, moves the Activity's own version and never produces a
 * synchronization intent of its own (RC1-NORMATIVE-ADDENDUM D5 and D10).
 */
@RunWith(AndroidJUnit4::class)
class TypedActivityDetailContractTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val workspaceId = UUID.fromString("10000000-0000-0000-0000-0000000000e1")
    private val farmId = UUID.fromString("20000000-0000-0000-0000-0000000000e1")
    private val parcelA = UUID.fromString("30000000-0000-0000-0000-0000000000e1")
    private val parcelB = UUID.fromString("30000000-0000-0000-0000-0000000000e2")
    private val date = LocalDate.parse("2026-03-04")
    private val now = Instant.parse("2026-09-22T09:00:00Z")

    private lateinit var db: MaginaOlivoDatabase
    private lateinit var repository: ActivityRepository

    @Before
    fun before() = runBlocking {
        context.deleteDatabase(DB)
        db = MaginaOlivoDatabase.create(context, DB)
        repository = OfflineFirstActivityRepository(db, FixedClock(now), RandomIds, TestDispatchers)
        seed()
    }

    @After
    fun after() {
        db.close()
        context.deleteDatabase(DB)
    }

    // ---------------------------------------------- one round trip per type

    @Test
    fun pruningKeepsItsOwnAgronomicFields() = runBlocking {
        val detail = ActivityDetail.Pruning("Formación", 4, 7.5, "Triturado en campo")
        assertEquals(detail, roundTrip(ActivityType.PRUNING, detail).detail)
    }

    @Test
    fun fertilizationKeepsTheProductAsHistoricalTextWithoutAProductsModule() = runBlocking {
        val detail = ActivityDetail.Fertilization(
            productName = "NPK 15-15-15",
            totalQuantity = 400.0,
            unit = "kg",
            doseValue = 2.5,
            doseUnit = "kg/ha",
            applicationMethod = "Fertirrigación",
        )
        val stored = roundTrip(ActivityType.FERTILIZATION, detail)
        assertEquals(detail, stored.detail)
        // product_id stays reserved and empty: RC1 never depends on a Products table.
        val row = db.activityDao().findWithTargets(stored.id)!!.fertilization!!
        assertNull(row.productId)
        assertEquals("NPK 15-15-15", row.productName)
    }

    @Test
    fun phytosanitaryKeepsSubstanceReasonAndEquipment() = runBlocking {
        val detail = ActivityDetail.Phytosanitary(
            productName = "Cobre 50%",
            activeSubstance = "Oxicloruro de cobre",
            totalQuantity = 12.0,
            unit = "l",
            doseValue = 2.0,
            doseUnit = "kg/ha",
            reason = "Repilo",
            equipmentText = "Atomizador arrastrado",
        )
        assertEquals(detail, roundTrip(ActivityType.PHYTOSANITARY, detail).detail)
    }

    @Test
    fun soilWorkKeepsItsWorkTypeAndMethod() = runBlocking {
        val detail = ActivityDetail.SoilWork("Desbroce", "Mecánico")
        assertEquals(detail, roundTrip(ActivityType.SOIL_WORK, detail).detail)
    }

    @Test
    fun irrigationKeepsDurationVolumeSectorAndSystem() = runBlocking {
        val detail = ActivityDetail.Irrigation(180, 240.0, "Sector 3", "Goteo")
        assertEquals(detail, roundTrip(ActivityType.IRRIGATION, detail).detail)
    }

    @Test
    fun maintenanceKeepsTypeAndAsset() = runBlocking {
        val detail = ActivityDetail.Maintenance("Correctivo", "Bomba del pozo")
        assertEquals(detail, roundTrip(ActivityType.MAINTENANCE, detail).detail)
    }

    @Test
    fun incidentKeepsCategorySeverityAndState() = runBlocking {
        val detail = ActivityDetail.Incident(
            category = "Rotura",
            severity = IncidentSeverity.HIGH,
            state = IncidentState.MONITORING,
            actionTaken = "Corte de sector",
        )
        assertEquals(detail, roundTrip(ActivityType.INCIDENT, detail).detail)
    }

    // ------------------------------------------------------- irrigation price

    @Test
    fun theIrrigationTariffIsAHistoricalSnapshotAndNotASecondLedger() = runBlocking {
        val price = IrrigationPrice(
            basis = IrrigationPricingBasis.PER_M3,
            priceDate = LocalDate.parse("2026-03-01"),
            unitPriceMinor = 12,
            quantity = 240.0,
            estimatedAmountMinor = 2880,
        )
        val detail = ActivityDetail.Irrigation(180, 240.0, "Sector 3", "Goteo", price)
        val stored = roundTrip(ActivityType.IRRIGATION, detail)
        assertEquals(price, (stored.detail as ActivityDetail.Irrigation).price)

        // The snapshot never becomes an authoritative cost on the Activity itself.
        val header = db.activityDao().findById(stored.id)!!
        assertNull(header.costMinor)
        assertEquals(0, db.expenseCount())
    }

    // --------------------------------------------------------- type matching

    @Test
    fun aDetailOfAnotherTypeIsRejectedWithoutPersistingAnything() = runBlocking {
        val before = repository.observeForFarm(farmId).first().size
        val result = repository.create(
            NewActivity(
                farmId = farmId,
                type = ActivityType.IRRIGATION,
                activityDate = date,
                description = "Riego",
                parcelIds = setOf(parcelA),
                detail = ActivityDetail.Fertilization(productName = "NPK"),
            ),
        )
        assertValidation("detail", result)
        assertEquals(before, repository.observeForFarm(farmId).first().size)
    }

    @Test
    fun retypingAnActivityReplacesItsDetailInsteadOfKeepingTwo() = runBlocking {
        val id = create(ActivityType.PRUNING, ActivityDetail.Pruning("Formación", 3, 6.0, null))
        assertOk(
            repository.update(
                id,
                ActivityChanges(
                    type = ActivityType.IRRIGATION,
                    activityDate = date,
                    description = "Ahora es un riego",
                    parcelIds = setOf(parcelA),
                    detail = ActivityDetail.Irrigation(90, 60.0, "Sector 1", "Goteo"),
                ),
            ),
        )
        val rows = db.activityDao().findWithTargets(id)!!
        assertNull("the pruning detail must not survive a retype", rows.pruning)
        assertEquals(90, rows.irrigation?.durationMinutes)
    }

    @Test
    fun negativeAgronomicNumbersAreRejected() = runBlocking {
        assertValidation(
            "hours",
            repository.create(
                NewActivity(
                    farmId = farmId,
                    type = ActivityType.PRUNING,
                    activityDate = date,
                    description = "Poda",
                    parcelIds = setOf(parcelA),
                    detail = ActivityDetail.Pruning(hours = -1.0),
                ),
            ),
        )
        assertValidation(
            "volumeM3",
            repository.create(
                NewActivity(
                    farmId = farmId,
                    type = ActivityType.IRRIGATION,
                    activityDate = date,
                    description = "Riego",
                    parcelIds = setOf(parcelA),
                    detail = ActivityDetail.Irrigation(volumeM3 = -5.0),
                ),
            ),
        )
    }

    // ------------------------------------------------------ aggregate rules

    @Test
    fun editingADetailMovesTheActivityAggregateVersion() = runBlocking {
        val id = create(ActivityType.PRUNING, ActivityDetail.Pruning("Formación", 3, 6.0, null))
        val before = db.activityDao().findById(id)!!.metadata.version
        assertOk(
            repository.update(
                id,
                ActivityChanges(
                    type = ActivityType.PRUNING,
                    activityDate = date,
                    description = "Poda de formación",
                    parcelIds = setOf(parcelA),
                    detail = ActivityDetail.Pruning("Formación", 5, 9.0, "Triturado"),
                ),
            ),
        )
        assertTrue(db.activityDao().findById(id)!!.metadata.version > before)
    }

    @Test
    fun aDetailNeverQueuesAnIntentOfItsOwn() = runBlocking {
        val id = create(ActivityType.IRRIGATION, ActivityDetail.Irrigation(120, 100.0, "Sector 1", "Goteo"))
        repeat(3) {
            assertOk(
                repository.update(
                    id,
                    ActivityChanges(
                        type = ActivityType.IRRIGATION,
                        activityDate = date,
                        description = "Riego",
                        parcelIds = setOf(parcelA, parcelB),
                        detail = ActivityDetail.Irrigation(120 + it, 100.0, "Sector 1", "Goteo"),
                    ),
                ),
            )
        }
        // One intent for the whole aggregate, and no entity type of its own exists.
        assertEquals(1, db.syncOutboxDao().listForEntity(SyncEntityType.ACTIVITY, id).size)
        assertEquals(1, db.outboxRowCount())
    }

    @Test
    fun theDetailIsWrittenInTheSameTransactionAsTheHeader() = runBlocking {
        // The header and the detail are written first and the Parcel targets last, so a
        // Parcel that belongs to no Farm fails once both already exist. Nothing may
        // survive that failure.
        val stranger = UUID.fromString("30000000-0000-0000-0000-0000000000ff")
        val result = repository.create(
            NewActivity(
                farmId = farmId,
                type = ActivityType.PRUNING,
                activityDate = date,
                description = "Poda",
                parcelIds = setOf(stranger),
                detail = ActivityDetail.Pruning("Formación", 2, 4.0, null),
            ),
        )
        assertValidation("parcelIds", result)
        assertEquals(0, db.detailRowCount())
        assertEquals(0, repository.observeForFarm(farmId).first().size)
    }

    @Test
    fun multiParcelTargetingIsUntouchedByTypedDetails() = runBlocking {
        val id = create(
            ActivityType.FERTILIZATION,
            ActivityDetail.Fertilization(productName = "NPK", totalQuantity = 300.0, unit = "kg"),
            parcelIds = setOf(parcelA, parcelB),
        )
        assertEquals(1, repository.observeForFarm(farmId).first().size)
        assertEquals(2, db.activityDao().countTargets(id))
        assertEquals(1, db.detailRowCount())
    }

    @Test
    fun typedDetailsSurviveAProcessRestart() = runBlocking {
        val detail = ActivityDetail.Incident(
            category = "Rotura",
            severity = IncidentSeverity.CRITICAL,
            state = IncidentState.OPEN,
            actionTaken = "Corte de agua",
        )
        val id = create(ActivityType.INCIDENT, detail)
        db.close()

        db = MaginaOlivoDatabase.create(context, DB)
        val restored = OfflineFirstActivityRepository(db, FixedClock(now), RandomIds, TestDispatchers)
            .observe(id).first()!!
        assertEquals(detail, restored.detail)
    }

    @Test
    fun observationAndOtherCarryNoDetailTable() = runBlocking {
        val id = create(ActivityType.OBSERVATION, null)
        assertNull(repository.observe(id).first()!!.detail)
        assertEquals(0, db.detailRowCount())
    }

    // --------------------------------------------------------------- helpers

    private suspend fun roundTrip(type: ActivityType, detail: ActivityDetail): Activity {
        val id = create(type, detail)
        return repository.observe(id).first()!!
    }

    private suspend fun create(
        type: ActivityType,
        detail: ActivityDetail?,
        parcelIds: Set<UUID> = setOf(parcelA),
    ): UUID {
        val result = repository.create(
            NewActivity(
                farmId = farmId,
                type = type,
                activityDate = date,
                description = "Trabajo de ${type.name}",
                parcelIds = parcelIds,
                detail = detail,
            ),
        )
        assertTrue("create($type) failed: $result", result is AppResult.Success)
        return (result as AppResult.Success).value
    }

    private fun assertOk(result: AppResult<Unit>) = assertEquals(AppResult.Success(Unit), result)

    private fun assertValidation(field: String, result: AppResult<*>) {
        assertTrue("expected validation failure, got $result", result is AppResult.Failure)
        val error = (result as AppResult.Failure).error
        assertTrue("expected AppError.Validation, got $error", error is AppError.Validation)
        assertEquals(field, (error as AppError.Validation).field)
    }

    private fun MaginaOlivoDatabase.detailRowCount(): Int =
        listOf(
            "pruning_details",
            "fertilization_details",
            "phytosanitary_details",
            "soil_work_details",
            "irrigation_details",
            "irrigation_price_snapshots",
            "maintenance_details",
            "incident_details",
        ).sumOf { table -> countOf("SELECT COUNT(*) FROM $table") }

    private fun MaginaOlivoDatabase.outboxRowCount(): Int = countOf("SELECT COUNT(*) FROM sync_outbox")

    private fun MaginaOlivoDatabase.expenseCount(): Int = countOf("SELECT COUNT(*) FROM expenses")

    private fun MaginaOlivoDatabase.countOf(sql: String): Int =
        openHelper.readableDatabase.query(sql).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }

    private suspend fun seed() {
        val meta = LocalMetadata(now, now)
        db.workspaceDao().upsert(
            WorkspaceEntity(workspaceId, "Olivar", UUID.randomUUID(), "ES", "Europe/Madrid", "es-ES", "EUR", meta),
        )
        db.farmDao().upsert(FarmEntity(farmId, workspaceId, "Finca tipada", metadata = meta))
        db.parcelDao().upsert(
            ParcelEntity(parcelA, workspaceId, "Parcela A", source = "MANUAL", managedAreaM2 = 1000.0, metadata = meta),
        )
        db.parcelDao().upsert(
            ParcelEntity(parcelB, workspaceId, "Parcela B", source = "MANUAL", managedAreaM2 = 900.0, metadata = meta),
        )
        db.parcelDao().upsertMembership(
            FarmParcelMembershipEntity(UUID.randomUUID(), workspaceId, farmId, parcelA, now, metadata = meta),
        )
        db.parcelDao().upsertMembership(
            FarmParcelMembershipEntity(UUID.randomUUID(), workspaceId, farmId, parcelB, now, metadata = meta),
        )
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
        private const val DB = "typed-activity-detail-contract-test.db"
    }
}
