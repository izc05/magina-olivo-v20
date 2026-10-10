package com.isivoltpro.maginaolivo.app

import com.isivoltpro.maginaolivo.core.common.AppError
import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.data.local.model.isRunning
import com.isivoltpro.maginaolivo.domain.activity.ActivityType
import com.isivoltpro.maginaolivo.domain.activity.NewActivity
import com.isivoltpro.maginaolivo.domain.campaign.NewCampaign
import com.isivoltpro.maginaolivo.domain.delivery.DeliveryDraft
import com.isivoltpro.maginaolivo.domain.delivery.DeliveryShareInput
import com.isivoltpro.maginaolivo.domain.delivery.PesadaOrigin
import com.isivoltpro.maginaolivo.domain.delivery.YieldDraft
import com.isivoltpro.maginaolivo.domain.equipment.EquipmentDraftLine
import com.isivoltpro.maginaolivo.domain.equipment.EquipmentPriceSnapshot
import com.isivoltpro.maginaolivo.domain.equipment.EquipmentType
import com.isivoltpro.maginaolivo.domain.expense.ExpenseCategory
import com.isivoltpro.maginaolivo.domain.expense.ExpenseDraft
import com.isivoltpro.maginaolivo.domain.farm.Farm
import com.isivoltpro.maginaolivo.domain.farm.FarmChanges
import com.isivoltpro.maginaolivo.domain.farm.NewFarm
import com.isivoltpro.maginaolivo.domain.labour.CrewDraft
import com.isivoltpro.maginaolivo.domain.labour.LabourPayment
import com.isivoltpro.maginaolivo.domain.labour.LabourRateBasis
import com.isivoltpro.maginaolivo.domain.labour.LabourRateSnapshot
import com.isivoltpro.maginaolivo.domain.labour.LabourUnit
import com.isivoltpro.maginaolivo.domain.parcel.IrrigationSystem
import com.isivoltpro.maginaolivo.domain.parcel.NewParcel
import com.isivoltpro.maginaolivo.domain.parcel.ParcelAgronomy
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.first

/**
 * #399 — the «Finca Demo Mágina» of `docs/DEMO-FARM-SCENARIO.md`, written only through the
 * canonical repositories, so every figure on screen comes from the same aggregates as real data.
 * Nothing here estimates: the expected totals in the document are what these records add up to.
 */
internal class DemoFarmSeeder(private val p: LocalPersistence) : DemoFarmTools {

    override suspend fun load(): AppResult<String> = seeding {
        val workspace = p.workspaceRepository.ensureLocalWorkspace().or("workspace")
        if (demoFarms(workspace).isNotEmpty()) {
            "La Finca Demo ya está cargada. No se ha duplicado nada."
        } else {
            seed(workspace)
            "Finca Demo cargada: 3 parcelas, campañas 2025/26 y 2026/27."
        }
    }

    override suspend fun reset(): AppResult<String> = seeding {
        val workspace = p.workspaceRepository.ensureLocalWorkspace().or("workspace")
        retire(workspace)
        seed(workspace)
        "Finca Demo restablecida: la anterior queda archivada y se ha creado de nuevo."
    }

    /**
     * #696 - «Eliminar datos de demostracion»: retires every demo Farm of the workspace and
     * creates nothing. It only ever touches Farms named [DEMO_FARM], so real Farms, their
     * Parcels, Pesadas, costs and photos are left exactly as they were.
     */
    override suspend fun remove(): AppResult<String> = seeding {
        val workspace = p.workspaceRepository.ensureLocalWorkspace().or("workspace")
        when (val retired = retire(workspace)) {
            0 -> "No hay Finca Demo cargada. No se ha tocado ninguna finca."
            1 -> "Finca Demo retirada y archivada. Tus fincas y datos reales siguen intactos."
            else -> "Retiradas $retired Fincas Demo. Tus fincas y datos reales siguen intactos."
        }
    }

    /**
     * Closes the demo's running campaign - so no Cuaderno context keeps pointing at it - renames
     * it and archives it. There is no physical delete: no repository offers a cascade deletion of
     * a Farm's aggregates, and this slice does not invent one (GAP in `docs/DEMO-FARM-SCENARIO.md`).
     */
    private suspend fun retire(workspace: UUID): Int {
        val demos = demoFarms(workspace)
        demos.forEach { farm ->
            p.campaignRepository.observeForFarm(farm.id).first().filter { it.status.isRunning }.forEach { campaign ->
                p.campaignRepository.close(campaign.id, maxOf(campaign.startDate, LAST_DAY)).or("close")
            }
            p.farmRepository.update(
                farm.id,
                // The mark travels with the rename: dropping `description` here would make the
                // retired demo indistinguishable from a real Farm on the next load or removal.
                FarmChanges(
                    name = "${farm.name.removeSuffix(RETIRED)}$RETIRED", description = DEMO_MARK,
                    municipality = farm.municipality, province = farm.province, notes = DEMO_NOTE,
                ),
            ).or("rename")
            p.farmRepository.archive(farm.id).or("archive")
        }
        return demos.size
    }

    /**
     * #696 — a Farm is the demo because of the mark this seeder wrote in it, never because of its
     * name: the farmer may rename the demo, and a real Farm may happen to be called anything at
     * all. Demos seeded before the mark existed carried `description = "DEMO"` together with the
     * demo note, and that pair is still recognised so they can be retired too.
     */
    private suspend fun demoFarms(workspace: UUID): List<Farm> =
        p.farmRepository.observeActive(workspace).first().filter { isDemo(it) }

    private suspend fun seed(workspace: UUID) {
        val farm = p.farmRepository.create(
            NewFarm(workspace, DEMO_FARM, description = DEMO_MARK, municipality = "Bedmar", province = "Jaén", notes = DEMO_NOTE),
        ).or("farm")
        // #696: each Parcel with its own grove data, watering and municipality (Bedmar and Garciez).
        val llanos = parcel(
            farm, "Los Llanos", 12_500.0, 120, "Picual", "Bedmar", "DEMO-LLANOS-001",
            IrrigationSystem.DRIP, "Sector 1", setOf(DayOfWeek.TUESDAY, DayOfWeek.FRIDAY),
        )
        val loma = parcel(farm, "La Loma", 9_500.0, 90, "Picual", "Bedmar", "DEMO-LOMA-002", IrrigationSystem.DRYLAND, null)
        val barranco = parcel(
            farm, "El Barranco", 10_000.0, 110, "Hojiblanca", "Garcíez", "DEMO-BARRANCO-003",
            IrrigationSystem.DRIP, "Sector 2", setOf(DayOfWeek.WEDNESDAY),
        )
        val all = setOf(llanos, loma, barranco)

        // Work through the year, before and between the campaigns. No money: costs live in the ledger.
        listOf(
            Triple(ActivityType.PHYTOSANITARY, LocalDate.of(2025, 9, 20), "Tratamiento de otoño (DEMO)") to all,
            Triple(ActivityType.PRUNING, LocalDate.of(2026, 2, 10), "Poda (DEMO)") to setOf(llanos),
            Triple(ActivityType.SOIL_WORK, LocalDate.of(2026, 3, 12), "Labores de suelo (DEMO)") to setOf(loma),
            Triple(ActivityType.FERTILIZATION, LocalDate.of(2026, 3, 25), "Abonado (DEMO)") to all,
            Triple(ActivityType.PHYTOSANITARY, LocalDate.of(2026, 4, 20), "Tratamiento fitosanitario primavera (DEMO)") to all,
            Triple(ActivityType.MAINTENANCE, LocalDate.of(2026, 5, 8), "Mantenimiento de goteros (DEMO)") to setOf(barranco),
            Triple(ActivityType.IRRIGATION, LocalDate.of(2026, 6, 15), "Riego (DEMO)") to setOf(llanos),
            Triple(ActivityType.OBSERVATION, LocalDate.of(2026, 7, 1), "Observación del cuajado (DEMO)") to setOf(loma),
            Triple(ActivityType.IRRIGATION, LocalDate.of(2026, 7, 10), "Riego (DEMO)") to setOf(llanos),
            Triple(ActivityType.IRRIGATION, LocalDate.of(2026, 7, 22), "Riego (DEMO)") to setOf(loma),
            Triple(ActivityType.IRRIGATION, LocalDate.of(2026, 8, 18), "Riego (DEMO)") to setOf(barranco),
            // Owner on #413 (#417 QA): general work inside the 2026/27 dates; it never belongs to the campaign.
            Triple(ActivityType.PHYTOSANITARY, LocalDate.of(2026, 9, 30), "Tratamiento general, no de recogida (DEMO)") to setOf(loma),
            Triple(ActivityType.MAINTENANCE, LocalDate.of(2026, 10, 2), "Reparación de valla (DEMO)") to setOf(barranco),
        ).forEach { (what, parcels) ->
            p.activityRepository.create(
                NewActivity(farmId = farm, type = what.first, activityDate = what.second, description = what.third,
                    parcelIds = parcels, notes = DEMO_NOTE, completeImmediately = true),
            ).or("activity ${what.third}")
        }

        val ana = worker("Ana García (DEMO)")
        val miguel = worker("Miguel Torres (DEMO)")
        val jose = worker("José Ruiz (DEMO)")
        val maria = worker("María López (DEMO)")

        // 2025/26 — closed history: 3 Pesadas, 5.100 kg.
        val before = campaign(farm, "Campaña de recogida 2025/26", LocalDate.of(2025, 10, 1), all)
        pesada(farm, LocalDate.of(2025, 10, 20), 1_600_000, 1_950, "DEMO-H01", setOf(llanos))
        pesada(farm, LocalDate.of(2025, 11, 4), 1_900_000, 2_040, "DEMO-H02", setOf(loma, llanos))
        pesada(farm, LocalDate.of(2025, 11, 18), 1_600_000, 1_960, "DEMO-H03", setOf(barranco))
        days(before).forEach { day ->
            crew(day.first, day.second, listOf(ana, miguel, jose), 6_000)
            equipment(day.first, day.second, tractor = 8_000, shaker = 5_500, trailer = 3_500)
        }
        expense(farm, before, LocalDate.of(2025, 11, 5), "Transporte a la cooperativa (DEMO)", ExpenseCategory.TRANSPORT, 7_000)
        listOf(ana, miguel, jose).forEach { pay(it, before, LocalDate.of(2025, 12, 10), 18_000) }
        p.campaignRepository.close(before, LocalDate.of(2025, 12, 15)).or("close 2025/26")

        // General costs of 2026/27 linked to no campaign: 1.185 €.
        listOf(
            Triple(LocalDate.of(2026, 9, 5), "Poda / mano de obra general (DEMO)", ExpenseCategory.LABOR) to 52_000L,
            Triple(LocalDate.of(2026, 9, 8), "Producto de tratamiento (DEMO)", ExpenseCategory.PRODUCTS) to 18_000L,
            Triple(LocalDate.of(2026, 9, 12), "Abonado (DEMO)", ExpenseCategory.PRODUCTS) to 32_000L,
            // Inside the campaign's dates but general: it must stay out of the recollection cost.
            Triple(LocalDate.of(2026, 10, 2), "Gasóleo general (DEMO)", ExpenseCategory.FUEL) to 9_000L,
            Triple(LocalDate.of(2026, 9, 20), "Riego / energía (DEMO)", ExpenseCategory.IRRIGATION) to 7_500L,
        ).forEach { (what, minor) -> expense(farm, null, what.first, what.second, what.third, minor) }

        // 2026/27 — active: 4 Pesadas (two the same day), 6.680 kg, 3 days, 10 jornadas + 1 media + 5 h.
        val now = campaign(farm, "Campaña de recogida 2026/27", LocalDate.of(2026, 9, 28), all)
        pesada(farm, LocalDate.of(2026, 9, 28), 1_850_000, 2_080, "DEMO-001", setOf(llanos))
        pesada(farm, LocalDate.of(2026, 10, 1), 2_120_000, 2_160, "DEMO-002", setOf(loma, llanos))
        // #696: two Pesadas on the same day, each with its own vale and origin suelo/arbol. Both join
        // the same automatic Jornada, so the day's kilos stay the exact sum of its Pesadas.
        pesada(farm, LocalDate.of(2026, 10, 1), 980_000, 1_910, "DEMO-004", setOf(barranco), PesadaOrigin.GROUND)
        pesada(farm, LocalDate.of(2026, 10, 3), 1_730_000, 1_990, "DEMO-003", setOf(barranco))
        val crews = mapOf(
            LocalDate.of(2026, 9, 28) to listOf(ana, miguel, jose),
            LocalDate.of(2026, 10, 1) to listOf(ana, miguel, jose, maria),
            LocalDate.of(2026, 10, 3) to listOf(ana, miguel, maria),
        )
        days(now).forEach { (day, date) ->
            crew(day, date, crews.getValue(date), 6_500)
            equipment(day, date, tractor = 8_500, shaker = 6_000, trailer = 3_500)
        }
        // #696: whole days, a half day and hours in the same campaign, each priced on its own basis.
        val dayOf = days(now).associate { (day, date) -> date to day }
        crew(dayOf.getValue(LocalDate.of(2026, 9, 28)), LocalDate.of(2026, 9, 28), listOf(maria), 1_000,
            LabourUnit.HOURS, minutes = 300, basis = LabourRateBasis.HOUR)
        crew(dayOf.getValue(LocalDate.of(2026, 10, 3)), LocalDate.of(2026, 10, 3), listOf(jose), 6_500, LabourUnit.HALF_DAY)
        expense(farm, now, LocalDate.of(2026, 9, 29), "Gasóleo recogida (DEMO)", ExpenseCategory.FUEL, 14_000)
        expense(farm, now, LocalDate.of(2026, 9, 30), "Aceite y mantenimiento pequeño (DEMO)", ExpenseCategory.REPAIR, 3_500)
        expense(farm, now, LocalDate.of(2026, 10, 2), "Transporte a la cooperativa (DEMO)", ExpenseCategory.TRANSPORT, 7_500)
        pay(ana, now, LAST_DAY, 19_500)   // 195,00 EUR generated, paid in full
        pay(miguel, now, LAST_DAY, 10_000) // 195,00 EUR generated, 100,00 EUR paid: partial
        // 180,00 EUR generated (5 h + 2 jornadas), settled in two payments; José keeps 162,50 EUR pending.
        pay(maria, now, LAST_DAY, 13_000)
        pay(maria, now, LAST_DAY, 5_000)
    }

    private suspend fun parcel(
        farm: UUID,
        name: String,
        areaM2: Double,
        trees: Int,
        variety: String,
        municipality: String,
        reference: String,
        irrigation: IrrigationSystem,
        sector: String?,
        irrigationDays: Set<DayOfWeek> = emptySet(),
    ): UUID =
        p.parcelRepository.create(
            NewParcel(farmId = farm, displayName = name, municipality = municipality, province = "Jaén",
                // A MANUAL parcel with an unmistakably fictitious identifier: it is not a Catastro
                // reference, it does not look like one and nothing here claims CUE compliance.
                cadastralReference = reference,
                managedAreaM2 = areaM2, notes = DEMO_NOTE,
                agronomy = ParcelAgronomy(
                    oliveTreeCount = trees, variety = variety, irrigationSystem = irrigation,
                    irrigationNetwork = sector?.let { "Red Demo" }, irrigationSector = sector,
                    irrigationDays = irrigationDays,
                )),
        ).or("parcel $name")

    private suspend fun worker(name: String): UUID = p.labourRepository.addWorker(name).or("worker $name")

    private suspend fun campaign(farm: UUID, name: String, start: LocalDate, parcels: Set<UUID>): UUID {
        val id = p.campaignRepository.create(NewCampaign(farm, name, start, parcels, DEMO_NOTE)).or("campaign $name")
        p.campaignRepository.activate(id).or("activate $name")
        return id
    }

    private suspend fun pesada(
        farm: UUID,
        date: LocalDate,
        grams: Long,
        fatHundredths: Int,
        ticket: String,
        parcels: Set<UUID>,
        origin: PesadaOrigin = PesadaOrigin.TREE,
    ) {
        // A mixed Pesada keeps its split unknown: no per-parcel kilos are invented.
        val id = p.deliveryRepository.create(
            DeliveryDraft(farmId = farm, deliveryDate = date, destinationOrganizationId = null,
                destinationName = "Organización Demo", netGrams = grams,
                shares = parcels.map { DeliveryShareInput(it, null) }, ticketNumber = ticket,
                notes = DEMO_NOTE, origin = origin),
        ).or("pesada $ticket")
        p.deliveryRepository.recordYield(id, YieldDraft(date, fatHundredths, null, DEMO_NOTE)).or("yield $ticket")
    }

    /** The recogida days the Pesadas opened, oldest first. */
    private suspend fun days(campaign: UUID): List<Pair<UUID, LocalDate>> =
        p.harvestRepository.observeForCampaign(campaign).first().sortedBy { it.harvestDate }.map { it.id to it.harvestDate }

    private suspend fun crew(
        day: UUID,
        date: LocalDate,
        people: List<UUID>,
        rateMinor: Long,
        unit: LabourUnit = LabourUnit.FULL_DAY,
        minutes: Int? = null,
        basis: LabourRateBasis = LabourRateBasis.DAY,
    ) {
        p.labourRepository.recordCrew(
            CrewDraft(day, people, unit, minutes = minutes,
                appliedRate = LabourRateSnapshot(rateMinor, "EUR", date, basis)),
        ).or("crew $date $unit")
    }

    private suspend fun equipment(day: UUID, date: LocalDate, tractor: Long, shaker: Long, trailer: Long) {
        p.equipmentRepository.replaceForHarvest(day, listOf(
            EquipmentDraftLine(EquipmentType.TRACTOR, 1, appliedPrice = EquipmentPriceSnapshot(tractor, "EUR", date)),
            EquipmentDraftLine(EquipmentType.SHAKER, 1, appliedPrice = EquipmentPriceSnapshot(shaker, "EUR", date)),
            EquipmentDraftLine(EquipmentType.TRAILER, 1, appliedPrice = EquipmentPriceSnapshot(trailer, "EUR", date)),
        )).or("equipment $date")
    }

    private suspend fun expense(farm: UUID, campaign: UUID?, date: LocalDate, concept: String, category: ExpenseCategory, minor: Long) {
        p.expenseRepository.create(
            ExpenseDraft(expenseDate = date, concept = concept, category = category, amountMinor = minor,
                farmId = farm, campaignId = campaign, notes = DEMO_NOTE),
        ).or("expense $concept")
    }

    private suspend fun pay(worker: UUID, campaign: UUID, date: LocalDate, minor: Long) {
        p.labourRepository.recordPayment(LabourPayment(UUID.randomUUID(), worker, campaign, date, minor, "EUR", "DEMO")).or("payment")
    }

    private class SeedFailure(val step: String, val error: AppError) : Exception(step)

    private fun <T> AppResult<T>.or(step: String): T = when (this) {
        is AppResult.Success -> value
        is AppResult.Failure -> throw SeedFailure(step, error)
    }

    private suspend fun seeding(block: suspend () -> String): AppResult<String> =
        try {
            AppResult.Success(block())
        } catch (failure: SeedFailure) {
            AppResult.Failure(failure.error).also {
                android.util.Log.w("DemoFarm", "Finca Demo stopped at «${failure.step}»: ${failure.error}")
            }
        }

    companion object {
        const val DEMO_FARM = "Finca Demo Mágina"

        /** The durable mark of a seeded demo Farm; `description` is what carries it. */
        const val DEMO_MARK = "DEMO \u00b7 finca de demostraci\u00f3n (no es real)"

        private const val RETIRED = " (retirada)"

        private const val LEGACY_MARK = "DEMO"

        /**
         * True only for a Farm this seeder created. A real Farm is never matched: the mark is a
         * token no one types by hand, and the legacy pair also requires the demo note.
         */
        fun isDemo(farm: Farm): Boolean = farm.description == DEMO_MARK ||
            (farm.description == LEGACY_MARK && farm.notes == DEMO_NOTE)
        const val DEMO_NOTE = "DEMO · datos ficticios de prueba (solo DEV). No es una recomendación agronómica."
        /** The last recogida day of the 2026/27 demo; Pesadas after «today» are never written. */
        val LAST_DAY: LocalDate = LocalDate.of(2026, 10, 3)
    }
}
