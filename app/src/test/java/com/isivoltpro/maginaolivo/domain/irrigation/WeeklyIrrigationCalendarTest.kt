package com.isivoltpro.maginaolivo.domain.irrigation

import com.isivoltpro.maginaolivo.domain.parcel.IrrigationSystem
import com.isivoltpro.maginaolivo.domain.parcel.Parcel
import com.isivoltpro.maginaolivo.domain.parcel.ParcelAgronomy
import com.isivoltpro.maginaolivo.domain.parcel.ParcelSource
import java.time.DayOfWeek
import java.time.Instant
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeeklyIrrigationCalendarTest {

    private fun sampleParcel(
        id: UUID = UUID.randomUUID(),
        name: String,
        system: IrrigationSystem? = IrrigationSystem.DRIP,
        network: String? = "Comunidad San Marcos",
        sector: String? = "Sector 1",
        days: Set<DayOfWeek> = setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY),
        archivedAt: Instant? = null,
        oliveCount: Int? = 350,
    ) = Parcel(
        id = id,
        workspaceId = UUID.randomUUID(),
        farmId = UUID.randomUUID(),
        displayName = name,
        cadastralReference = null,
        cadastralPolygon = "12",
        cadastralParcel = "45",
        municipality = "Torres",
        province = "Jaén",
        source = ParcelSource.MANUAL,
        geometryGeoJson = null,
        cadastralAreaM2 = 15000.0,
        managedAreaM2 = 14500.0,
        notes = null,
        archivedAt = archivedAt,
        version = 1L,
        agronomy = ParcelAgronomy(
            oliveTreeCount = oliveCount,
            irrigationSystem = system,
            irrigationNetwork = network,
            irrigationSector = sector,
            irrigationDays = days,
        ),
    )

    @Test
    fun buildWeeklyPlan_createsAllSevenDaysMondayToSunday() {
        val plan = WeeklyIrrigationCalendar.buildWeeklyPlan(emptyList())

        assertEquals(7, plan.days.size)
        assertEquals(DayOfWeek.MONDAY, plan.days[0].dayOfWeek)
        assertEquals(DayOfWeek.TUESDAY, plan.days[1].dayOfWeek)
        assertEquals(DayOfWeek.WEDNESDAY, plan.days[2].dayOfWeek)
        assertEquals(DayOfWeek.THURSDAY, plan.days[3].dayOfWeek)
        assertEquals(DayOfWeek.FRIDAY, plan.days[4].dayOfWeek)
        assertEquals(DayOfWeek.SATURDAY, plan.days[5].dayOfWeek)
        assertEquals(DayOfWeek.SUNDAY, plan.days[6].dayOfWeek)
        assertEquals(0, plan.totalScheduledTurns)
        assertEquals(0, plan.participatingParcelsCount)
        assertTrue(plan.communitiesInPlan.isEmpty())
    }

    @Test
    fun buildWeeklyPlan_assignsParcelsToMatchingDays() {
        val parcelA = sampleParcel(
            name = "Las Viñas",
            days = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
        )
        val parcelB = sampleParcel(
            name = "El Salado",
            days = setOf(DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
        )

        val plan = WeeklyIrrigationCalendar.buildWeeklyPlan(listOf(parcelA, parcelB))

        assertEquals(4, plan.totalScheduledTurns) // Lunes(1) + Miércoles(2) + Viernes(1)
        assertEquals(2, plan.participatingParcelsCount)

        val monday = plan.days.first { it.dayOfWeek == DayOfWeek.MONDAY }
        assertTrue(monday.hasTurns)
        assertEquals(1, monday.totalParcels)
        assertEquals("Las Viñas", monday.turns[0].parcelName)

        val wednesday = plan.days.first { it.dayOfWeek == DayOfWeek.WEDNESDAY }
        assertEquals(2, wednesday.totalParcels)
        val namesWednesday = wednesday.turns.map { it.parcelName }
        assertTrue(namesWednesday.contains("Las Viñas"))
        assertTrue(namesWednesday.contains("El Salado"))

        val friday = plan.days.first { it.dayOfWeek == DayOfWeek.FRIDAY }
        assertEquals(1, friday.totalParcels)
        assertEquals("El Salado", friday.turns[0].parcelName)

        val sunday = plan.days.first { it.dayOfWeek == DayOfWeek.SUNDAY }
        assertFalse(sunday.hasTurns)
        assertEquals(0, sunday.totalParcels)
    }

    @Test
    fun buildWeeklyPlan_excludesArchivedDrylandAndEmptyDaysParcels() {
        val archived = sampleParcel(
            name = "Archivada",
            days = setOf(DayOfWeek.MONDAY),
            archivedAt = Instant.now(),
        )
        val dryland = sampleParcel(
            name = "Secano Sierra",
            system = IrrigationSystem.DRYLAND,
            days = setOf(DayOfWeek.MONDAY),
        )
        val noDays = sampleParcel(
            name = "Sin Días Asignados",
            system = IrrigationSystem.DRIP,
            days = emptySet(),
        )
        val valid = sampleParcel(
            name = "Parcela Activa",
            days = setOf(DayOfWeek.MONDAY),
        )

        val plan = WeeklyIrrigationCalendar.buildWeeklyPlan(listOf(archived, dryland, noDays, valid))

        val monday = plan.days.first { it.dayOfWeek == DayOfWeek.MONDAY }
        assertEquals(1, monday.totalParcels)
        assertEquals("Parcela Activa", monday.turns[0].parcelName)
        assertEquals(1, plan.participatingParcelsCount)
    }

    @Test
    fun turnsByCommunity_groupsDayTurnsCorrectly() {
        val parcelComA = sampleParcel(
            name = "Olivar Alto",
            network = "Comunidad San Isidro",
            days = setOf(DayOfWeek.TUESDAY),
        )
        val parcelComB = sampleParcel(
            name = "Olivar Bajo",
            network = "Comunidad San Isidro",
            days = setOf(DayOfWeek.TUESDAY),
        )
        val parcelOther = sampleParcel(
            name = "Pozo Propio",
            network = "Pozo El Hondo",
            days = setOf(DayOfWeek.TUESDAY),
        )
        val parcelNoNet = sampleParcel(
            name = "Sin Comunidad",
            network = null,
            days = setOf(DayOfWeek.TUESDAY),
        )

        val plan = WeeklyIrrigationCalendar.buildWeeklyPlan(listOf(parcelComA, parcelComB, parcelOther, parcelNoNet))
        val tuesday = plan.days.first { it.dayOfWeek == DayOfWeek.TUESDAY }

        val groups = tuesday.turnsByCommunity
        assertEquals(3, groups.size)
        assertEquals(2, groups["Comunidad San Isidro"]?.size)
        assertEquals(1, groups["Pozo El Hondo"]?.size)
        assertEquals(1, groups["Sin comunidad asignada"]?.size)

        assertTrue(plan.communitiesInPlan.contains("Comunidad San Isidro"))
        assertTrue(plan.communitiesInPlan.contains("Pozo El Hondo"))
        assertFalse(plan.communitiesInPlan.contains("Sin comunidad asignada"))
    }
}

