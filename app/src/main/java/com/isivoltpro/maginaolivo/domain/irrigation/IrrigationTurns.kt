package com.isivoltpro.maginaolivo.domain.irrigation

import com.isivoltpro.maginaolivo.domain.parcel.IrrigationSystem
import com.isivoltpro.maginaolivo.domain.parcel.Parcel
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.UUID

/**
 * #720 R2 — the farmer's own weekly watering plan, read from the irrigation days each Parcel
 * already carries (CR-004).
 *
 * Three things stay apart and are never confused for one another: a **community notice** (which
 * this is not: nothing here is published by anyone), this **personal plan**, and a **watering
 * that happened**, which exists only once it is recorded in the Cuaderno as an Irrigation
 * Activity. A turn on this list is therefore a reminder of the farmer's own weekly days, never
 * evidence of water delivered and never an official turn of a comunidad de regantes.
 *
 * It needs no connection: the days are local data of the Parcel.
 */
data class IrrigationTurn(
    val parcelId: UUID,
    val parcelName: String,
    val date: LocalDate,
    val sector: String? = null,
    val network: String? = null,
    val system: IrrigationSystem? = null,
) {
    /** "Sector 1 · Red Demo", or null when the farmer gave neither. */
    val where: String? get() = listOfNotNull(sector?.takeIf(String::isNotBlank), network?.takeIf(String::isNotBlank))
        .takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

object IrrigationTurns {
    /** How many turns Inicio shows at once. */
    const val HOME_LIMIT = 3

    /**
     * Each watered Parcel's next day on or after [today], soonest first, then by name.
     *
     * A Parcel with no irrigation days has no turn: nothing is guessed from its system. Dryland
     * is never watered, and an archived Parcel is out of the plan.
     */
    fun of(parcels: List<Parcel>, today: LocalDate, limit: Int = HOME_LIMIT): List<IrrigationTurn> =
        parcels.asSequence()
            .filter { it.archivedAt == null && it.agronomy.irrigationSystem != IrrigationSystem.DRYLAND }
            .mapNotNull { parcel -> next(parcel.agronomy.irrigationDays, today)?.let { parcel to it } }
            .map { (parcel, date) ->
                IrrigationTurn(
                    parcelId = parcel.id,
                    parcelName = parcel.displayName,
                    date = date,
                    sector = parcel.agronomy.irrigationSector,
                    network = parcel.agronomy.irrigationNetwork,
                    system = parcel.agronomy.irrigationSystem,
                )
            }
            .sortedWith(compareBy({ it.date }, { it.parcelName }))
            .take(limit)
            .toList()

    /** Today counts as its own turn: the farmer has not watered yet when the day starts. */
    private fun next(days: Set<DayOfWeek>, today: LocalDate): LocalDate? {
        if (days.isEmpty()) return null
        return (0L..6L).asSequence().map(today::plusDays).firstOrNull { it.dayOfWeek in days }
    }
}
