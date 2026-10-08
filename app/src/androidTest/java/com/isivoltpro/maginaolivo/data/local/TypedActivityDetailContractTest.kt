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
import com.isivoltpro.maginaolivo.domain.workspace.WorkspaceRepository
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
        repository = activityRepository()
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
    fun phytosanitaryKeepsCueV25ReferencesAndRegulatorySnapshot() = runBlocking {
        val operatorId = UUID.fromString("40000000-0000-0000-0000-0000000000e1")
        val machineId = UUID.fromString("50000000-0000-0000-0000-0000000000e1")
        val providerId = UUID.fromString("60000000-0000-0000-0000-0000000000e1")
        val fetchedAt = Instant.parse("2026-10-07T12:00:00Z")
        val detail = ActivityDetail.Phytosanitary(
            productName = "Cobre 50%",
            activeSubstance = "Oxicloruro de cobre",
            doseValue = 2.0,
            doseUnit = "kg/ha",
            reason = "Repilo",
            equipmentText = "Atomizador arrastrado",
            operatorPersonId = operatorId,
            applicationMachineId = machineId,
            serviceProviderOrganizationId = providerId,
            productRegistrationNumber = "ES-12345",
            productSource = "MAPA_REGFI",
            productSourceVersion = "2026-W41",
            productFetchedAt = fetchedAt,
            authorizationContextSnapshot = """{"crop":"olivo","use":"repilo"}""",
            pestProblemCode = "REPILO",
            efficacyCode = "GOOD",
            treatmentObservations = "Sin deriva visible",
        )
        val stored = roundTrip(ActivityType.PHYTOSANITARY, detail)
        assertEquals(detail, stored.detail)
        val row = db.activityDao().findWithTargets(stored.id)!!.phytosanitary!!
        assertEquals(operatorId, row.operatorPersonId)
        assertEquals(machineId, row.applicationMachineId)
        assertEquals(providerId, row.serviceProviderOrganizationId)
        assertEquals("ES-12345", row.productRegistrationNumber)
        assertEquals("MAPA_REGFI", row.productSource)
        assertEquals("2026-W41", row.productSourceVersion)
        assertEquals(fetchedAt, row.productFetchedAt)
        assertEquals("""{"crop":"olivo","use":"repilo"}""", row.authorizationContextSnapshot)
        assertEquals("REPILO", row.pestProblemCode)
        assertEquals("GOOD", row.efficacyCode)
        assertEquals("Sin deriva visible", row.treatmentObservations)
    }

    @Test
    fun phytosanitaryRejectsApplicatorFromAnotherWorkspace() = runBlocking {
        val foreignOperator = UUID.fromString("40000000-0000-0000-0000-0000000000ff")
        val result = repository.create(
            NewActivity(
                farmId = farmId,
                type = ActivityType.PHYTOSANITARY,
                activityDate = date,
                description = "Tratamiento con referencia ajena",
                parcelIds = setOf(parcelA),
                detail = ActivityDetail.Phytosanitary(
                    productName = "Cobre 50%",
                    operatorPersonId = foreignOperator,
                ),
            ),
        )
        assertValidation("operatorPersonId", result)
    }

    @Test
    fun legacyTreatmentEditKeepsArchivedResourcesAndSnapshotAfterRestart() = runBlocking {
        val stored = ActivityDetail.Phytosanitary(
            productName = "Cobre 50%", activeSubstance = "Oxicloruro de cobre", reason = "Repilo",
            operatorPersonId = UUID.fromString("40000000-0000-0000-0000-0000000000e1"),
            applicationMachineId = UUID.fromString("50000000-0000-0000-0000-0000000000e1"),
            serviceProviderOrganizationId = UUID.fromString("60000000-0000-0000-0000-0000000000e1"),
            productRegistrationNumber = "ES-12345", productSource = "MAPA_REGFI",
            productSourceVersion = "historical-v1", productFetchedAt = Instant.parse("2026-03-01T12:00:00Z"),
            authorizationContextSnapshot = """{"crop":"olivo","use":"repilo"}""",
            pestProblemCode = "REPILO", efficacyCode = "GOOD", treatmentObservations = "Sin deriva",
        )
        val id = create(ActivityType.PHYTOSANITARY, stored)
        db.openHelper.writableDatabase.execSQL("UPDATE agronomic_people SET status='ARCHIVED' WHERE id='${stored.operatorPersonId}'")
        db.openHelper.writableDatabase.execSQL("UPDATE machines SET status='ARCHIVED' WHERE id='${stored.applicationMachineId}'")
        val legacyForm = ActivityDetail.Phytosanitary(
            productName = stored.productName, activeSubstance = stored.activeSubstance, reason = "Repilo leve",
        )
        assertOk(repository.update(id, ActivityChanges(
            type = ActivityType.PHYTOSANITARY, activityDate = date, description = "Corrección histórica",
            parcelIds = setOf(parcelA), detail = legacyForm,
        )))
        db.close()
        db = MaginaOlivoDatabase.create(context, DB)
        repository = activityRepository()
        assertEquals(stored.copy(reason = "Repilo leve"), repository.observe(id).first()!!.detail)
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

    /** #453: editing the same type never erases what its form does not carry. */
    @Test
    fun editingTheSameTypeKeepsWhatItsFormDoesNotCarry() = runBlocking {
        val stored = IrrigationPrice(
            basis = IrrigationPricingBasis.PER_M3, priceDate = LocalDate.parse("2026-03-01"),
            unitPriceMinor = 12, quantity = 240.0, estimatedAmountMinor = 2880, currency = "USD", notes = "Factura marzo",
        )
        val id = create(ActivityType.IRRIGATION, ActivityDetail.Irrigation(180, 240.0, "Sector 3", "Goteo", stored))
        // The form rebuilds the tariff from its own fields only: no currency, notes or Gasto link.
        val fromForm = IrrigationPrice(basis = IrrigationPricingBasis.PER_M3, priceDate = LocalDate.parse("2026-03-01"),
            unitPriceMinor = 12, quantity = 300.0, estimatedAmountMinor = 3600)
        assertOk(
            repository.update(
                id,
                ActivityChanges(
                    type = ActivityType.IRRIGATION,
                    activityDate = date,
                    description = "Riego corregido",
                    parcelIds = setOf(parcelA),
                    detail = ActivityDetail.Irrigation(200, 300.0, "Sector 3", "Goteo", fromForm),
                ),
            ),
        )
        val price = db.activityDao().findWithTargets(id)!!.irrigationPrice!!
        assertEquals("USD", price.currency)
        assertEquals("Factura marzo", price.notes)
        assertEquals(3600L, price.estimatedAmountMinor)
        assertEquals(0, db.expenseCount())
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
    fun affectedSurfaceIsExplicitAndCanBeSmallerThanTheParcel() = runBlocking {
        val result = repository.create(
            NewActivity(
                farmId = farmId,
                type = ActivityType.PHYTOSANITARY,
                activityDate = date,
                description = "Tratamiento parcial",
                parcelIds = setOf(parcelA),
                parcelAreasM2 = mapOf(parcelA to 500.0),
                detail = ActivityDetail.Phytosanitary(productName = "Cobre"),
            ),
        )
        assertTrue(result is AppResult.Success)
        val id = (result as AppResult.Success).value
        assertEquals(500.0, repository.observe(id).first()!!.targets.single().areaAffectedM2!!, 0.001)
    }

    @Test
    fun selectingAParcelWithoutAnAreaNeverInfersItsWholeManagedSurface() = runBlocking {
        val id = create(ActivityType.PHYTOSANITARY, ActivityDetail.Phytosanitary(productName = "Cobre"))
        assertNull(repository.observe(id).first()!!.targets.single().areaAffectedM2)
    }

    @Test
    fun changingTheParcelSurfaceLaterDoesNotInvalidateHistoricalAffectedArea() = runBlocking {
        val created = repository.create(
            NewActivity(
                farmId = farmId,
                type = ActivityType.PHYTOSANITARY,
                activityDate = date,
                description = "Tratamiento histórico",
                parcelIds = setOf(parcelA),
                parcelAreasM2 = mapOf(parcelA to 900.0),
            ),
        )
        val id = (created as AppResult.Success).value

        val parcel = db.parcelDao().findById(parcelA)!!
        db.parcelDao().upsert(parcel.copy(managedAreaM2 = 800.0))

        assertOk(
            repository.update(
                id,
                ActivityChanges(
                    type = ActivityType.PHYTOSANITARY,
                    activityDate = date,
                    description = "Solo corrijo el texto",
                    parcelIds = setOf(parcelA),
                    // Existing 900 m2 is intentionally preserved although today's parcel says 800 m2.
                ),
            ),
        )
        assertEquals(900.0, repository.observe(id).first()!!.targets.single().areaAffectedM2!!, 0.001)
    }

    @Test
    fun aLegacyUpdateCallerDoesNotEraseAnAlreadyConfirmedAffectedSurface() = runBlocking {
        val created = repository.create(
            NewActivity(
                farmId = farmId,
                type = ActivityType.PHYTOSANITARY,
                activityDate = date,
                description = "Tratamiento parcial",
                parcelIds = setOf(parcelA),
                parcelAreasM2 = mapOf(parcelA to 500.0),
                detail = ActivityDetail.Phytosanitary(productName = "Cobre"),
            ),
        )
        val id = (created as AppResult.Success).value
        assertOk(
            repository.update(
                id,
                ActivityChanges(
                    type = ActivityType.PHYTOSANITARY,
                    activityDate = date,
                    description = "Tratamiento corregido",
                    parcelIds = setOf(parcelA),
                    detail = ActivityDetail.Phytosanitary(productName = "Cobre"),
                    // Intentionally omit parcelAreasM2: simulates an older caller.
                ),
            ),
        )
        assertEquals(500.0, repository.observe(id).first()!!.targets.single().areaAffectedM2!!, 0.001)
    }

    /**
     * #440: correcting a work keeps its targets as they are — same ids, historical names, surface —
     * even when one of its Parcels has since been archived or renamed. Only a Parcel being added
     * must still be an active Parcel of the Farm.
     */
    @Test
    fun correctingAWorkNeverRebuildsItsTargetsEvenWithAnArchivedParcel() = runBlocking {
        val created = repository.create(
            NewActivity(
                farmId = farmId,
                type = ActivityType.PHYTOSANITARY,
                activityDate = date,
                description = "Tratamiento",
                parcelIds = setOf(parcelA, parcelB),
                parcelAreasM2 = mapOf(parcelA to 400.0),
                detail = ActivityDetail.Phytosanitary(productName = "Cobre"),
            ),
        )
        val id = (created as AppResult.Success).value
        val before = db.activityDao().listTargets(id).associateBy { it.parcelId }

        // Later: Parcel A is archived and Parcel B is renamed.
        val a = db.parcelDao().findById(parcelA)!!
        db.parcelDao().upsert(a.copy(status = com.isivoltpro.maginaolivo.data.local.model.RecordStatus.ARCHIVED))
        val b = db.parcelDao().findById(parcelB)!!
        db.parcelDao().upsert(b.copy(displayName = "Parcela B renombrada"))

        assertOk(
            repository.update(
                id,
                ActivityChanges(
                    type = ActivityType.PHYTOSANITARY,
                    activityDate = date,
                    description = "Tratamiento corregido",
                    parcelIds = setOf(parcelA, parcelB),
                    detail = ActivityDetail.Phytosanitary(productName = "Cobre"),
                ),
            ),
        )
        val after = db.activityDao().listTargets(id).associateBy { it.parcelId }
        assertEquals(before.keys, after.keys)
        assertEquals(before.getValue(parcelA).id, after.getValue(parcelA).id)
        assertEquals(before.getValue(parcelB).id, after.getValue(parcelB).id)
        assertEquals("Parcela B", after.getValue(parcelB).parcelNameAtTarget)
        assertEquals(400.0, after.getValue(parcelA).areaAffectedM2!!, 0.001)

        // An archived Parcel cannot be newly added to another work.
        val other = repository.create(
            NewActivity(
                farmId = farmId,
                type = ActivityType.PHYTOSANITARY,
                activityDate = date,
                description = "Otro tratamiento",
                parcelIds = setOf(parcelB),
            ),
        )
        val otherId = (other as AppResult.Success).value
        val adding = repository.update(
            otherId,
            ActivityChanges(
                type = ActivityType.PHYTOSANITARY,
                activityDate = date,
                description = "Otro tratamiento",
                parcelIds = setOf(parcelA, parcelB),
            ),
        )
        assertTrue("adding an archived Parcel is refused", adding is AppResult.Failure)
    }

    @Test
    fun affectedSurfaceCannotSilentlyExceedTheKnownParcelSurface() = runBlocking {
        val result = repository.create(
            NewActivity(
                farmId = farmId,
                type = ActivityType.PHYTOSANITARY,
                activityDate = date,
                description = "Tratamiento imposible",
                parcelIds = setOf(parcelA),
                parcelAreasM2 = mapOf(parcelA to 1_001.0),
            ),
        )
        assertValidation("parcelAreasM2", result)
        assertEquals(0, repository.observeForFarm(farmId).first().size)
    }

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
        val restored = activityRepository()
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

    // This fixture intentionally contains two workspaces. The active one is explicit;
    // selecting the oldest row would choose the foreign workspace used by rejection tests.
    private fun activityRepository() = OfflineFirstActivityRepository(
        db, FixedClock(now), RandomIds, TestDispatchers,
        workspaceRepository = object : WorkspaceRepository {
            override suspend fun ensureLocalWorkspace(): AppResult<UUID> = AppResult.Success(workspaceId)
        },
    )

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

        val sql = db.openHelper.writableDatabase
        sql.execSQL(
            "INSERT INTO agronomic_people (id, workspace_id, display_name, is_advisor, source, status, created_at, updated_at, version, sync_status) " +
                "VALUES ('40000000-0000-0000-0000-0000000000e1','10000000-0000-0000-0000-0000000000e1','Juan Aplicador',0,'MANUAL','ACTIVE',1000,1000,1,'LOCAL_ONLY')",
        )
        sql.execSQL(
            "INSERT INTO machines (id, workspace_id, name, category, status, created_at, updated_at, version, sync_status) " +
                "VALUES ('50000000-0000-0000-0000-0000000000e1','10000000-0000-0000-0000-0000000000e1','Atomizador','ATOMIZER','ACTIVE',1000,1000,1,'LOCAL_ONLY')",
        )
        sql.execSQL(
            "INSERT INTO agricultural_organizations (id, workspace_id, name, created_at, updated_at, version, sync_status) " +
                "VALUES ('60000000-0000-0000-0000-0000000000e1','10000000-0000-0000-0000-0000000000e1','Tratamientos Sierra',1000,1000,1,'LOCAL_ONLY')",
        )
        sql.execSQL(
            "INSERT INTO organization_roles (organization_id, role) " +
                "VALUES ('60000000-0000-0000-0000-0000000000e1','SERVICE_PROVIDER')",
        )
        sql.execSQL(
            "INSERT INTO workspaces (id, name, owner_user_id, country_code, timezone, locale, currency, created_at, updated_at, version, sync_status) " +
                "VALUES ('10000000-0000-0000-0000-0000000000ff','Otro olivar','70000000-0000-0000-0000-0000000000ff','ES','Europe/Madrid','es-ES','EUR',1000,1000,1,'LOCAL_ONLY')",
        )
        sql.execSQL(
            "INSERT INTO agronomic_people (id, workspace_id, display_name, is_advisor, source, status, created_at, updated_at, version, sync_status) " +
                "VALUES ('40000000-0000-0000-0000-0000000000ff','10000000-0000-0000-0000-0000000000ff','Aplicador ajeno',0,'MANUAL','ACTIVE',1000,1000,1,'LOCAL_ONLY')",
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
