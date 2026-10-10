package com.isivoltpro.maginaolivo.domain.irrigation

import com.isivoltpro.maginaolivo.domain.parcel.IrrigationSystem
import com.isivoltpro.maginaolivo.domain.parcel.Parcel
import com.isivoltpro.maginaolivo.domain.parcel.ParcelAgronomy
import com.isivoltpro.maginaolivo.domain.parcel.ParcelSource
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** #720 R2: the personal weekly plan, from the Parcel's own irrigation days. Never a community turn. */
class IrrigationTurnsTest {
    private val monday = LocalDate.of(2026, 10, 12) // a Monday

    @Test fun todayCountsAsItsOwnTurn() {
        val turns = IrrigationTurns.of(
            listOf(parcel("Los Llanos", setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY))),
            today = monday,
        )
        assertEquals(listOf(monday), turns.map { it.date })
    }

    @Test fun theNextDayOfTheWeekIsFoundAcrossTheWeekEnd() {
        val turns = IrrigationTurns.of(
            listOf(parcel("El Barranco", setOf(DayOfWeek.SUNDAY))),
            today = monday,
        )
        assertEquals(listOf(monday.plusDays(6)), turns.map { it.date })
    }

    @Test fun soonestFirstAndThenByName() {
        val turns = IrrigationTurns.of(
            listOf(
                parcel("Zarzas", setOf(DayOfWeek.WEDNESDAY), sector = "Sector 2"),
                parcel("Alta", setOf(DayOfWeek.WEDNESDAY), sector = "Sector 3"),
                parcel("Los Llanos", setOf(DayOfWeek.TUESDAY), sector = "Sector 1"),
            ),
            today = monday,
        )
        assertEquals(listOf("Los Llanos", "Alta", "Zarzas"), turns.map { it.parcelName })
        assertEquals("Sector 1", turns.first().sector)
        assertEquals("Sector 1 · Red", turns.first().where)
    }

    @Test fun nothingIsGuessedForDrylandNoDaysOrArchivedParcels() {
        val turns = IrrigationTurns.of(
            listOf(
                parcel("Secano", setOf(DayOfWeek.MONDAY), system = IrrigationSystem.DRYLAND),
                parcel("Sin días", emptySet()),
                parcel("Archivada", setOf(DayOfWeek.MONDAY), archived = true),
            ),
            today = monday,
        )
        assertTrue(turns.isEmpty())
    }

    @Test fun inicioShowsAtMostThreeTurns() {
        val parcels = (1..5).map { parcel("Parcela $it", setOf(DayOfWeek.MONDAY)) }
        assertEquals(3, IrrigationTurns.of(parcels, monday).size)
        assertEquals(5, IrrigationTurns.of(parcels, monday, limit = 10).size)
    }

    private fun parcel(
        name: String,
        days: Set<DayOfWeek>,
        sector: String? = "Sector 1",
        system: IrrigationSystem = IrrigationSystem.DRIP,
        archived: Boolean = false,
    ) = Parcel(
        id = UUID.randomUUID(), workspaceId = UUID.randomUUID(), farmId = UUID.randomUUID(),
        displayName = name, cadastralReference = null, cadastralPolygon = null, cadastralParcel = null,
        municipality = null, province = null, source = ParcelSource.MANUAL, geometryGeoJson = null,
        cadastralAreaM2 = null, managedAreaM2 = null, notes = null,
        archivedAt = if (archived) Instant.EPOCH else null, version = 1,
        agronomy = ParcelAgronomy(
            irrigationSystem = system, irrigationNetwork = "Red", irrigationSector = sector, irrigationDays = days,
        ),
    )
}
